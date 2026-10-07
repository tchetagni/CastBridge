package castbridge.play.chess

import castbridge.core.chess.ChessRelayClient
import castbridge.core.chess.ClockMode
import castbridge.core.chess.online.ChessOnlineGame
import castbridge.core.chess.online.ChessServiceCaps
import castbridge.core.chess.online.ChessStakeFlow
import castbridge.core.chess.online.ChessWallet
import castbridge.core.chess.online.InMemoryChessStakeStore
import castbridge.core.chess.online.OnlineChessHost
import castbridge.core.owner.ActivationKind
import castbridge.core.quiz.online.PlayHttpTransport
import castbridge.core.quiz.online.PlayTvSession
import castbridge.core.quiz.online.StakeSpec
import castbridge.core.wallet.PlayResult
import castbridge.core.wallet.Verdict
import castbridge.core.wallet.WalletCurrency
import castbridge.core.wallet.ui.EscrowDone
import castbridge.core.wallet.ui.SettleDone
import castbridge.core.wallet.ui.SettleLine
import castbridge.core.wallet.ui.WalletIdem
import castbridge.core.wallet.ui.WalletResult
import castbridge.play.LOOPBACK
import castbridge.play.PlayConfig
import castbridge.play.PlayServer
import castbridge.play.RevocationsMode
import castbridge.play.TestKeys
import castbridge.play.TestRights
import java.net.URL
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Les échecs en ligne sur de VRAIES sockets (port aléatoire, 127.0.0.1) : le vrai client des TV ([ChessRelayClient] sur [PlayTvSession] et [PlayHttpTransport], SSE + POST), le vrai service
 * ([PlayServer], salle `game:chess`), la config de PRODUCTION (`webPlay = false` : aucun navigateur, ticket + activation pour créer ET rejoindre). Seul le portefeuille est factice : il signe
 * les blocages `cbe1` avec la clé de test du portefeuille et lit les `cbr1` avec la clé publique du service, comme l'API. Le forfait à 60 s a son horloge injectée dans `ChessHubTest`.
 */
class ChessSocketsTest {
    // Les réglages de maintien des connexions HTTP du JDK (http.keepAlive, http.maxConnections) se figent au PREMIER usage de HttpURLConnection dans la JVM des tests : on pose ceux du simulateur du POC
    // (EdgeFluidityTest, qui compte les connexions) AVANT notre premier appel, sinon l'ordre d'exécution des classes changerait leur résultat.
    init { check(castbridge.play.poc.client.KeepAlive.effective) }

    private val servers = ArrayList<PlayServer>()
    private val tvs = ArrayList<Tv>()
    private val resultsDir = StakeKit.tempDir()

    @AfterTest fun stop() { tvs.forEach { runCatching { it.game.close() } }; tvs.clear(); servers.forEach { it.close() }; servers.clear() }

    private fun server(chess: Boolean = true, stakes: Boolean = true): PlayServer = PlayServer(PlayConfig(
        requireProof = false, webPlay = false, revocationsMode = RevocationsMode.OFF, port = 0, trustedProxies = LOOPBACK, ticketPubKeys = listOf(TestKeys.pub), trustedKeys = TestRights.trustedKeys,
        createsPerIpPerHour = 10_000, createsPerIdentityPerDay = 10_000, chess = chess, stakes = stakes, walletPubKeys = listOf(StakeKit.walletPub),
        resultKeyFile = StakeKit.resultKeyFile(), resultsDir = resultsDir)).also { it.start(); servers += it }

    /** Le portefeuille d'une TV : de vrais `cbe1` signés par la clé de test du portefeuille ; il garde et lit (clé publique du service) chaque `cbr1` qu'on lui donne à régler. */
    private class SocketWallet(val tv: TestRights.Tv, val signer: castbridge.core.owner.Ed25519Signer = StakeKit.walletSigner) : ChessWallet {
        val verified = CopyOnWriteArrayList<PlayResult>()
        val tokens = CopyOnWriteArrayList<String>()
        var locks = 0

        override fun lockEscrow(cur: WalletCurrency, per: Long, game: String, idem: String): WalletResult<EscrowDone> {
            locks++
            val eid = StakeKit.eid()
            return WalletResult.Ok(EscrowDone(StakeKit.escrow(tv.code, cur.name, per, eid = eid, signer = signer), eid, System.currentTimeMillis(), System.currentTimeMillis() + 30 * 60_000L, false, null))
        }

        override fun settle(token: String): WalletResult<SettleDone> {
            tokens += token
            val v = PlayResult.verify(token, StakeKit.resultRing)
            val r = (v as? Verdict.Accepted)?.value ?: return WalletResult.Fail(castbridge.core.wallet.ui.WalletMessages.of(403, "RESULT_FORGED"), 403, "RESULT_FORGED", false)
            verified += r
            return WalletResult.Ok(SettleDone(r.rid, r.kind.name, r.cur.name, r.game, 0, r.lines.map { SettleLine(it.eid, it.id, it.used, it.pay, 0) }))
        }
    }

    private inner class Tv(srv: PlayServer, val dev: TestRights.Tv, trial: Boolean = false, signer: castbridge.core.owner.Ed25519Signer = StakeKit.walletSigner) {
        val wallet = SocketWallet(dev, signer)
        val store = InMemoryChessStakeStore()
        private val activation = if (trial) TestRights.activation(ActivationKind.TRIAL, device = dev, rights = TestRights.trialUsage()) else TestRights.activation(device = dev)
        private val mono = { System.nanoTime() / 1_000_000 }
        val client = ChessRelayClient(
            newSession = {
                PlayTvSession(clock = mono, transports = { t -> PlayHttpTransport("http://127.0.0.1:${srv.port}", t, { null }, mono, ticketOnEveryPost = false) },
                    ticket = { TestKeys.ticket(deviceCode = dev.code) })
            },
            activation = { activation }, deviceHash = { "dev-${dev.name}-000001" }, openTimeoutMs = 20_000L, ackTimeoutMs = 15_000L)
        val game = ChessOnlineGame(client, ChessStakeFlow(wallet, store, { WalletIdem.newKey() }), store, OnlineChessHost(), clock = System::currentTimeMillis)
        init { tvs += this }

        fun view() = game.view()
        fun stage() = view()["stage"] as? String
        fun ply() = (view()["ply"] as? Number)?.toInt() ?: -1
    }

    private fun until(what: String, ms: Long = 20_000L, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) { if (cond()) return; Thread.sleep(25) }
        throw AssertionError("délai dépassé : $what")
    }

    private fun seated(o: ChessOnlineGame.Opened): ChessOnlineGame.Opened.Seated { assertTrue(o is ChessOnlineGame.Opened.Seated, o.toString()); return o as ChessOnlineGame.Opened.Seated }

    /** Joue [uci] quand la TV a le trait à la demi-coup [ply] (la position arrive par le flux, pas à l'instant du coup de l'autre). */
    private fun move(tv: Tv, uci: String, ply: Int) {
        until("la TV ${tv.dev.name} a le trait à la demi-coup $ply") { tv.ply() == ply && (tv.view()["legal"] as? List<*>).orEmpty().isNotEmpty() }
        assertEquals("OK", tv.game.move(uci).result, uci)
    }

    private fun foolsMate(a: Tv, b: Tv) { move(a, "f2f3", 0); move(b, "e7e5", 1); move(a, "g2g4", 2); move(b, "d8h4", 3) }

    private val NDEM20 = StakeSpec("NDEM", 20)

    // ------------------------------------------------------------------ capacités

    @Test fun theCapsPageTellsWhatTheServiceHostsAndWhatTheTvReads() {
        val full = ChessServiceCaps.parse(URL("http://127.0.0.1:${server().port}/play/.well-known/caps").readText())!!
        assertTrue(full.chess && full.stakes)
        val noStakes = ChessServiceCaps.parse(URL("http://127.0.0.1:${server(stakes = false).port}/play/.well-known/caps").readText())!!
        assertTrue(noStakes.chess); assertFalse(noStakes.stakes)
        val off = ChessServiceCaps.parse(URL("http://127.0.0.1:${server(chess = false).port}/play/.well-known/caps").readText())!!
        assertFalse(off.chess); assertFalse(off.stakes)
    }

    @Test fun aServiceWithChessOffRefusesWithTheFrenchText() {
        val a = Tv(server(chess = false), TestRights.tv)
        val r = a.game.create("TV A", 30, "white", ClockMode.COMPETITION, null) as ChessOnlineGame.Opened.Failed
        assertTrue(r.text.contains("échecs en ligne"), r.text)
    }

    // ------------------------------------------------------------------ partie libre

    @Test fun twoTvsPlayAFreeGameAndATvWatchesIt() {
        val srv = server()
        val a = Tv(srv, TestRights.tv); val b = Tv(srv, TestRights.otherTv); val c = Tv(srv, TestRights.Tv("C"))
        val s = seated(a.game.create("TV A", 30, "white", ClockMode.COMPETITION, null)).session
        assertEquals(1, srv.hub.chessRooms().size)
        seated(c.game.join(s.code, "TV C", spectate = true))
        seated(b.game.join(s.code, "TV B"))
        until("la partie commence") { a.stage() == "PLAYING" && b.stage() == "PLAYING" }
        foolsMate(a, b)
        until("la partie est finie pour les trois TV") { listOf(a, b, c).all { it.stage() == "FINISHED" } }
        assertEquals("b", ((b.view()["result"] as Map<*, *>)["winner"]))
        assertEquals("FINISHED", a.game.host!!.view(null)["stage"], "la vitrine des téléphones suit la TV")
        assertTrue(a.wallet.tokens.isEmpty() && b.wallet.tokens.isEmpty() && srv.hub.chessRooms().all { it.resultToken == null }, "aucun résultat signé pour une partie libre")
        assertEquals(0, a.wallet.locks + b.wallet.locks)
    }

    // ------------------------------------------------------------------ partie misée

    @Test fun aStakedGameIsSignedByTheServiceCopiedForTheCollectorAndSettledByBothTvs() {
        val srv = server()
        val a = Tv(srv, TestRights.tv); val b = Tv(srv, TestRights.otherTv)
        val s = seated(a.game.create("TV A", 30, "white", ClockMode.COMPETITION, NDEM20)).session
        assertEquals(NDEM20, a.game.stake)
        assertEquals(ChessOnlineGame.Opened.NeedsStake(NDEM20), b.game.join(s.code, "TV B"), "la salle dit sa mise avant tout blocage")
        assertEquals(0, b.wallet.locks)
        seated(b.game.joinWithStake(s.code, "TV B", NDEM20))
        until("la partie commence") { a.stage() == "PLAYING" && b.stage() == "PLAYING" }
        foolsMate(a, b)
        until("les deux règlements") { a.game.settlement is ChessOnlineGame.Settlement.Done && b.game.settlement is ChessOnlineGame.Settlement.Done }
        assertEquals(-20L, (a.game.settlement as ChessOnlineGame.Settlement.Done).net)
        assertEquals(20L, (b.game.settlement as ChessOnlineGame.Settlement.Done).net)
        // le résultat relu avec la CLÉ PUBLIQUE du service, comme le fait l'API : deux blocages, somme versée = somme utilisée, le gagnant reçoit les deux mises
        val r = b.wallet.verified.single()
        assertEquals(PlayResult.Kind.END, r.kind); assertEquals("chess", r.game); assertEquals(20L, r.per)
        assertEquals(setOf(TestRights.tv.code, TestRights.otherTv.code), r.lines.map { it.id }.toSet())
        assertEquals(40L, r.lines.single { it.id == TestRights.otherTv.code }.pay); assertEquals(0L, r.lines.single { it.id == TestRights.tv.code }.pay)
        assertEquals(a.wallet.tokens.single(), b.wallet.tokens.single(), "les deux TV reçoivent le même résultat signé")
        // la copie du service pour le collecteur de l'hôte : le même jeton, sous le nom de l'identifiant de résultat
        val file = java.io.File(resultsDir, r.rid + ".cbr1")
        assertTrue(file.isFile, "copie déposée"); assertEquals(b.wallet.tokens.single(), file.readText().trim())
    }

    @Test fun aTvThatClosedItsApplicationMidGameComesBackToItsSeatAndSettles() {
        val srv = server()
        val a = Tv(srv, TestRights.tv); val b = Tv(srv, TestRights.otherTv)
        val s = seated(a.game.create("TV A", 30, "white", ClockMode.COMPETITION, NDEM20)).session
        b.game.join(s.code, "TV B"); seated(b.game.joinWithStake(s.code, "TV B", NDEM20))
        move(a, "e2e4", 0); move(b, "e7e5", 1)
        val saved = a.store.savedSeat()!!
        a.client.close()                                           // l'application de A est fermée : la liaison tombe, la place est gardée
        val a2 = Tv(srv, TestRights.tv); a2.store.saveSeat(saved)
        val back = a2.game.resume()
        assertTrue(back is ChessOnlineGame.Opened.Seated && back.resumed, back.toString())
        until("la position revient") { a2.ply() == 2 && a2.stage() == "PLAYING" }
        move(a2, "g1f3", 2)
        assertEquals("OK", b.game.resign().result)
        until("le règlement de A, revenue") { a2.game.settlement is ChessOnlineGame.Settlement.Done }
        assertEquals(20L, (a2.game.settlement as ChessOnlineGame.Settlement.Done).net)
    }

    // ------------------------------------------------------------------ refus

    @Test fun aTrialTvCanPlayFreeButNeverStake() {
        val srv = server()
        val a = Tv(srv, TestRights.tv, trial = true)
        val r = a.game.create("TV A", 30, "white", ClockMode.COMPETITION, NDEM20) as ChessOnlineGame.Opened.Failed
        assertTrue(r.text.contains("libres seulement"), r.text)
        seated(a.game.create("TV A", 30, "white", ClockMode.COMPETITION, null))
    }

    @Test fun aForgedEscrowIsRefusedAndNotKept() {
        val srv = server()
        val a = Tv(srv, TestRights.tv, signer = StakeKit.otherWalletSigner)
        val r = a.game.create("TV A", 30, "white", ClockMode.COMPETITION, NDEM20) as ChessOnlineGame.Opened.Failed
        assertTrue(r.text.contains("n'est pas valable"), r.text)
        assertEquals(null, a.store.pendingEscrow(), "un blocage que le service dit invalide est oublié")
        assertTrue(srv.hub.chessRooms().isEmpty())
    }

    @Test fun aTvDoesNotPlayAgainstItself() {
        val srv = server()
        val a = Tv(srv, TestRights.tv)
        val s = seated(a.game.create("TV A", 30, "white", ClockMode.COMPETITION, null)).session
        val a2 = Tv(srv, TestRights.tv)
        val r = a2.game.join(s.code, "TV A bis") as ChessOnlineGame.Opened.Failed
        assertTrue(r.text.contains("contre elle-même"), r.text)
        assertNotNull(a.stage())
    }

}
