package castbridge.play.chess

import castbridge.core.chess.online.ChessServerRoom
import castbridge.core.owner.ActivationKind
import castbridge.core.quiz.Json
import castbridge.core.quiz.online.ChessOptions
import castbridge.core.quiz.online.ClientMsg
import castbridge.core.quiz.online.PlayCodec
import castbridge.core.quiz.online.PlayProtocol
import castbridge.core.quiz.online.StakeSpec
import castbridge.core.wallet.PlayResult
import castbridge.core.wallet.Verdict
import castbridge.play.HubFixture
import castbridge.play.PlayConfig
import castbridge.play.PlayHub
import castbridge.play.StakeServices
import castbridge.play.TestConn
import castbridge.play.TestKeys
import castbridge.play.TestRights
import castbridge.play.dev
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Le service héberge les échecs en ligne (games-G2) : MÊME chaîne d'entrée que le Quiz (ticket `cbp1`, activation `cbx1`, preuve), puis la salle `game:chess`. Boucle locale du vrai [PlayHub] : deux
 * « TV » (connexions simulées), parties libres et misées, forfait, reprise, arrêt du service. Les résultats `cbr1` sont relus avec la clé publique du service, comme le fait l'API.
 */
class ChessHubTest {
    private var now = 1_000_000L
    private fun cfg(vararg more: Pair<String, Any>) = HubFixture.config("webPlay" to false, *more)
    private fun hub(stakes: StakeServices? = StakeKit.services(), cfg: PlayConfig = cfg()): PlayHub = HubFixture.hub(cfg, stakes = stakes, clock = { now })

    private val tvA = TestRights.tv
    private val tvB = TestRights.otherTv
    private val tvC = TestRights.Tv("C")

    /** Une TV connectée au service : sa connexion simulée, son ticket et son activation. */
    private inner class Peer(val hub: PlayHub, val tv: TestRights.Tv, activation: String? = null, val trial: Boolean = false, ip: String = "198.51.100.${(10..250).random()}") {
        val conn = TestConn(ip)
        val activation: String = activation ?: if (trial) TestRights.activation(ActivationKind.TRIAL, device = tv, rights = TestRights.trialUsage()) else TestRights.activation(device = tv)
        var token: String? = null
        var roomId: String? = null
        init { hub.register(conn) }

        fun hello(ticket: String = TestKeys.ticket(deviceCode = tv.code)) = hub.onText(conn, PlayCodec.encode(ClientMsg.Hello(PlayProtocol.PROTO, PlayProtocol.CAPS, null, ticket)))
        fun send(m: ClientMsg) = hub.onText(conn, PlayCodec.encode(m))
        fun escrow(cur: String = "NDEM", per: Long = 20, id: String = tv.code) = StakeKit.escrow(id, cur, per)

        fun create(stake: StakeSpec? = null, escrow: String? = null, seconds: Int = 30, color: String = "white", game: String = "chess", mode: String = "COMPETITION", activation: String? = this.activation) {
            hello()
            send(ClientMsg.Create("TV ${tv.name}", null, activation, emptyList(), null, game, ChessOptions(seconds, mode, color), stake, escrow))
            remember()
        }

        fun join(code: String, escrow: String? = null, spectate: Boolean = false, activation: String? = this.activation) {
            send(ClientMsg.Join(code, "TV ${tv.name}", null, dev(), spectate, activation, null, escrow))
            remember()
        }

        fun resumeOn(next: Peer, at: Long = 0L) = next.send(ClientMsg.Resume(roomId!!, token!!, 0))

        private fun remember() {
            welcomes().lastOrNull()?.let { token = it["token"] as String; roomId = it["roomId"] as String }
        }

        fun msgs(): List<Map<*, *>> = synchronized(conn.out) { conn.out.map { Json.parse(it) as Map<*, *> } }
        fun welcomes() = msgs().filter { it["t"] == "welcome" }
        fun errors() = msgs().filter { it["t"] == "error" }
        fun lastError() = errors().lastOrNull()
        fun reason() = lastError()?.get("reason")
        fun acks() = msgs().filter { it["t"] == "ack" }
        fun results() = msgs().filter { it["t"] == "result" }.map { it["token"] as String }
        @Suppress("UNCHECKED_CAST") fun states() = msgs().filter { it["t"] == "state" }.map { it["view"] as Map<String, Any?> }
        fun view() = states().last()
        fun code() = welcomes().first()["code"] as String
        fun move(uci: String, ply: Int, seq: Long = ply.toLong() + 1) = send(ClientMsg.GameAct("move", uci, ply, seq))
        fun lastAck() = acks().lastOrNull()?.get("result")
    }

    private fun foolsMate(a: Peer, b: Peer) {
        a.move("f2f3", 0); b.move("e7e5", 1); a.move("g2g4", 2); b.move("d8h4", 3)
    }

    private fun verified(token: String): PlayResult {
        val v = PlayResult.verify(token, StakeKit.resultRing)
        assertTrue(v is Verdict.Accepted, "le résultat est authentique : $v")
        return (v as Verdict.Accepted).value
    }

    // ------------------------------------------------------------------ parties libres

    @Test fun twoTvsPlayAFreeGameThroughTheHub() {
        val hub = hub(stakes = null)
        val a = Peer(hub, tvA); a.create()
        assertEquals("HOST", a.welcomes().single()["role"]); assertEquals("chess", a.welcomes().single()["game"])
        assertEquals(1, hub.chessRooms().size); assertEquals(1, hub.roomCount())
        val b = Peer(hub, tvB); b.hello(); b.join(a.code())
        assertEquals("PLAYER", b.welcomes().single()["role"])
        assertEquals("PLAYING", a.view()["stage"]); assertEquals("PLAYING", b.view()["stage"])
        foolsMate(a, b)
        assertEquals("OK", b.lastAck()); assertEquals("CHECKMATE", (a.view()["result"] as Map<*, *>)["reason"]); assertEquals("b", (b.view()["result"] as Map<*, *>)["winner"])
        assertTrue(a.results().isEmpty() && b.results().isEmpty(), "partie libre : aucun résultat à régler")
    }

    @Test fun aWrongTurnAStaleMoveAndAnIllegalMoveAreAnsweredByTheRoom() {
        val hub = hub(stakes = null)
        val a = Peer(hub, tvA); a.create(); val b = Peer(hub, tvB); b.hello(); b.join(a.code())
        b.move("e7e5", 0); assertEquals("NOT_YOUR_TURN", b.lastAck())
        a.move("e2e5", 0); assertEquals("ILLEGAL", a.lastAck())
        a.move("e2e4", 0); assertEquals("OK", a.lastAck())
        a.move("e2e4", 0, seq = 9); assertEquals("STALE", a.lastAck(), "le renvoi du même coup (liaison lente) est écarté")
    }

    @Test fun chessRoomsAndQuizRoomsShareOneCodeSpaceAndNeverMix() {
        val hub = hub(stakes = null)
        val q = Peer(hub, tvC)
        q.hello(); q.send(TestRights.create(q.activation))
        val quizCode = q.welcomes().single()["code"] as String
        val a = Peer(hub, tvA); a.create()
        val chessCode = a.code()
        assertTrue(quizCode != chessCode)
        // une TV qui entre par le code d'une salle d'échecs arrive dans les échecs, et la TV du Quiz n'y est pas
        val b = Peer(hub, tvB); b.hello(); b.join(chessCode)
        assertEquals("chess", b.welcomes().single()["game"])
        assertEquals(2, hub.roomCount()); assertEquals(1, hub.rooms().size); assertEquals(1, hub.chessRooms().size)
        // le même code côté Quiz : une autre TV entre dans la salle de Quiz, jamais dans les échecs
        val d = Peer(hub, TestRights.Tv("D")); d.hello(); d.send(ClientMsg.Join(quizCode, "TV", null, dev(), true, d.activation, null))
        assertNull(d.welcomes().single()["game"], "salle de Quiz")
    }

    @Test fun aCodeThatDoesNotExistGetsTheSameAnswerForChessAsForTheQuiz() {
        val hub = hub(stakes = null)
        val a = Peer(hub, tvA); a.create()
        val b = Peer(hub, tvB); b.hello(); b.join("ZZZZZZZZ")
        assertEquals("PLAY_BAD_CODE", b.reason())
    }

    @Test fun aTvThatAlreadyHostsCannotJoinItsOwnRoomAsAPlayer() {
        val hub = hub(stakes = null)
        val a = Peer(hub, tvA); a.create()
        val a2 = Peer(hub, tvA); a2.hello(); a2.join(a.code())
        assertEquals("SAME_TV", a2.reason(), "une TV ne joue pas contre elle-même")
        a2.join(a.code(), spectate = true)
        assertEquals("SPECTATOR", a2.welcomes().single()["role"])
    }

    @Test fun spectatorsGetStatesButNoLegalMovesAndCannotPlay() {
        val hub = hub(stakes = null)
        val a = Peer(hub, tvA); a.create(); val b = Peer(hub, tvB); b.hello(); b.join(a.code())
        val s = Peer(hub, tvC); s.hello(); s.join(a.code(), spectate = true)
        assertEquals("SPECTATOR", s.welcomes().single()["role"])
        s.move("e2e4", 0); assertEquals("FORBIDDEN", s.lastAck())
        a.move("e2e4", 0)
        assertTrue((s.view()["legal"] as List<*>).isEmpty()); assertEquals("e2e4", s.view()["lastMove"])
    }

    // ------------------------------------------------------------------ essai : parties libres seulement

    @Test fun aTrialTvPlaysFreeGamesButNeverStakedOnesAndTheTicketIsNotBurnedByTheRefusal() {
        val hub = hub()
        val t = Peer(hub, tvC, trial = true)
        t.hello()
        t.send(ClientMsg.Create("TV C", null, t.activation, emptyList(), null, "chess", ChessOptions(30, "COMPETITION", "white"), StakeSpec("NDEM", 20), t.escrow()))
        assertEquals("STAKE_TRIAL_FREE_ONLY", t.reason(), "essai = parties libres seulement, même en NDEM")
        assertEquals(0, hub.chessRooms().size)
        // le même ticket sert ensuite pour une partie libre : le refus n'a rien consommé
        t.send(ClientMsg.Create("TV C", null, t.activation, emptyList(), null, "chess", ChessOptions(30, "COMPETITION", "white")))
        assertEquals("HOST", t.welcomes().single()["role"])
        assertEquals(1, hub.chessRooms().size)
    }

    @Test fun aTrialTvCannotEnterAStakedRoomAsAPlayerButMayWatch() {
        val hub = hub()
        val a = Peer(hub, tvA); a.create(StakeSpec("NDEM", 20), a.escrow())
        val t = Peer(hub, tvC, trial = true); t.hello()
        t.join(a.code(), escrow = t.escrow())
        assertEquals("STAKE_TRIAL_FREE_ONLY", t.reason())
        t.join(a.code(), spectate = true)
        assertEquals("SPECTATOR", t.welcomes().single()["role"], "elle peut regarder")
    }

    @Test fun aTrialTvCountsFreeChessGamesInTheSameDailyCounterAsTheQuiz() {
        val hub = hub(stakes = null)
        val a = Peer(hub, tvA); a.create()
        val t = Peer(hub, tvC, trial = true); t.hello(); t.join(a.code())
        assertEquals("PLAYER", t.welcomes().single()["role"])
        // la même TV d'essai ne peut pas tenir deux connexions vivantes
        val a2 = Peer(hub, TestRights.Tv("E")); a2.create()
        val t2 = Peer(hub, tvC, trial = true); t2.hello(); t2.join(a2.code())
        assertEquals("PLAY_BUSY", t2.reason())
    }

    // ------------------------------------------------------------------ mises : bout en bout

    @Test fun aStakedGameFromEscrowToSignedResultAndSpool() {
        val dir = StakeKit.tempDir()
        val services = StakeKit.services(dir)
        val hub = hub(stakes = services)
        val a = Peer(hub, tvA)
        val escA = a.escrow("NDEM", 20)
        a.create(StakeSpec("NDEM", 20), escA)
        assertEquals("HOST", a.welcomes().single()["role"])
        assertEquals(20L, ((a.view()["stake"] as Map<*, *>)["per"]))
        // B arrive sans blocage : la salle lui dit la mise ; son ticket n'est pas brûlé
        val b = Peer(hub, tvB); b.hello(); b.join(a.code())
        assertEquals("STAKE_ESCROW_REQUIRED", b.reason())
        @Suppress("UNCHECKED_CAST") val data = b.lastError()!!["data"] as Map<String, Any?>
        assertEquals(mapOf("game" to "chess", "cur" to "NDEM", "per" to 20), data.mapValues { (it.value as? Number)?.toInt() ?: it.value })
        assertEquals(1, hub.chessRooms().single().escrowIds().size, "seul l'hôte est assis")
        // la TV bloque sa mise puis revient avec le même ticket
        val escB = b.escrow("NDEM", 20)
        b.join(a.code(), escrow = escB)
        assertEquals("PLAYER", b.welcomes().single()["role"])
        assertEquals(setOf(StakeKit.eidOf(escA), StakeKit.eidOf(escB)), hub.chessRooms().single().escrowIds().toSet())
        foolsMate(a, b)
        // les deux TV reçoivent le MÊME résultat signé
        val ra = verified(a.results().single()); val rb = verified(b.results().single())
        assertEquals(ra, rb)
        assertEquals("chess", ra.game); assertEquals(PlayResult.Kind.END, ra.kind); assertEquals(20L, ra.per)
        assertEquals(setOf(tvA.code, tvB.code), ra.lines.map { it.id }.toSet())
        assertEquals(40L, ra.lines.single { it.id == tvB.code }.pay, "les Noirs ont maté : ils gagnent les deux mises")
        assertEquals(0L, ra.lines.single { it.id == tvA.code }.pay)
        // copie déposée pour le collecteur de l'hôte, sous l'identifiant du résultat
        assertEquals(listOf(ra.rid), services.spool.list())
        assertEquals(a.results().single(), services.spool.read(ra.rid))
        assertEquals(hub.chessRooms().single().resultToken, a.results().single())
    }

    @Test fun aBlockedEscrowServesOneRoomOnlyAndAnotherTvsEscrowIsRefused() {
        val hub = hub()
        val a = Peer(hub, tvA)
        val esc = a.escrow("NDEM", 20)
        a.create(StakeSpec("NDEM", 20), esc)
        assertEquals(1, hub.chessRooms().size)
        // le même blocage pour une SECONDE salle : refusé (sinon la partie ne se règlerait pas)
        val a2 = Peer(hub, tvA)
        a2.create(StakeSpec("NDEM", 20), esc)
        assertEquals("STAKE_ESCROW_INVALID", a2.reason())
        assertEquals(1, hub.chessRooms().size)
        // le blocage d'une AUTRE TV, présenté à celle-ci
        val c = Peer(hub, tvC)
        c.create(StakeSpec("NDEM", 20), StakeKit.escrow(tvA.code, "NDEM", 20))
        assertEquals("STAKE_ESCROW_INVALID", c.reason())
    }

    @Test fun everyBadEscrowIsRefusedBeforeAnythingIsConsumed() {
        val hub = hub()
        val spec = StakeSpec("NDEM", 20)
        val now = System.currentTimeMillis()
        val cases = linkedMapOf(
            "clé inconnue" to StakeKit.escrow(tvA.code, signer = StakeKit.otherWalletSigner),
            "échu" to StakeKit.escrow(tvA.code, now = now - 3_600_000L, lifeMs = 60_000L),
            "autre monnaie" to StakeKit.escrow(tvA.code, cur = "MBOKO", per = 20),
            "autre mise" to StakeKit.escrow(tvA.code, per = 50),
            "deux sièges" to StakeKit.escrow(tvA.code, k = 2),
            "mauvaise audience" to StakeKit.escrow(tvA.code, aud = "autre-service"),
            "montant incohérent" to StakeKit.escrow(tvA.code, amt = 999),
            "illisible" to "cbe1.pas-un-blocage.xx",
        )
        val a = Peer(hub, tvA)
        a.hello()
        for ((why, token) in cases) {
            a.send(ClientMsg.Create("TV A", null, a.activation, emptyList(), null, "chess", ChessOptions(30, "COMPETITION", "white"), spec, token))
            assertEquals("STAKE_ESCROW_INVALID", a.reason(), why)
        }
        assertEquals(0, hub.chessRooms().size)
        assertEquals(0, hub.usedTicketCount(), "aucun refus n'a brûlé le ticket : la TV corrige et recommence")
        a.send(ClientMsg.Create("TV A", null, a.activation, emptyList(), null, "chess", ChessOptions(30, "COMPETITION", "white"), spec, StakeKit.escrow(tvA.code)))
        assertEquals("HOST", a.welcomes().single()["role"])
    }

    @Test fun stakeInputsAreCheckedForShapeBoundsAndPresence() {
        val hub = hub(stakes = StakeKit.services(maxNdem = 200))
        val a = Peer(hub, tvA); a.hello()
        fun create(stake: StakeSpec?, escrow: String?) = a.send(ClientMsg.Create("TV A", null, a.activation, emptyList(), null, "chess", ChessOptions(30, "COMPETITION", "white"), stake, escrow))
        create(StakeSpec("NDEM", 20), null); assertEquals("STAKE_ESCROW_REQUIRED", a.reason(), "une partie misée exige le blocage")
        @Suppress("UNCHECKED_CAST") assertEquals("NDEM", (a.lastError()!!["data"] as Map<String, Any?>)["cur"])
        create(null, StakeKit.escrow(tvA.code)); assertEquals("STAKE_BAD", a.reason(), "un blocage sans mise bloquerait l'argent pour rien")
        create(StakeSpec("NDEM", 201), StakeKit.escrow(tvA.code, per = 201)); assertEquals("STAKE_BAD", a.reason(), "au-dessus des bornes du service")
        assertEquals(0, hub.chessRooms().size); assertEquals(0, hub.usedTicketCount())
    }

    @Test fun mbokoStakesWorkLikeNdemForAProductionTv() {
        val hub = hub()
        val a = Peer(hub, tvA); a.create(StakeSpec("MBOKO", 5), a.escrow("MBOKO", 5))
        val b = Peer(hub, tvB); b.hello(); b.join(a.code(), escrow = b.escrow("MBOKO", 5))
        a.send(ClientMsg.GameAct("resign", null, null, 3))
        val r = verified(b.results().single())
        assertEquals("MBOKO", r.cur.name); assertEquals(10L, r.lines.single { it.id == tvB.code }.pay, "l'adversaire d'un joueur qui abandonne gagne les deux mises")
        assertEquals(0L, r.lines.single { it.id == tvA.code }.pay, "abandon = mise perdue")
    }

    @Test fun withoutTheKeysStakesAreSuspendedButFreeGamesStayOpen() {
        val hub = hub(stakes = null)
        assertFalse(hub.stakesOn())
        val a = Peer(hub, tvA); a.hello()
        a.send(ClientMsg.Create("TV A", null, a.activation, emptyList(), null, "chess", ChessOptions(30, "COMPETITION", "white"), StakeSpec("NDEM", 20), StakeKit.escrow(tvA.code)))
        assertEquals("STAKES_SUSPENDED", a.reason())
        a.send(ClientMsg.Create("TV A", null, a.activation, emptyList(), null, "chess", ChessOptions(30, "COMPETITION", "white")))
        assertEquals("HOST", a.welcomes().single()["role"])
    }

    @Test fun chessCanBeSwitchedOffAndUnknownGamesAreNamed() {
        val off = hub(cfg = cfg().let { PlayConfig(webPlay = false, revocationsMode = castbridge.play.RevocationsMode.OFF, port = 0, ticketPubKeys = listOf(TestKeys.pub), trustedKeys = TestRights.trustedKeys, requireProof = false, chess = false) })
        val a = Peer(off, tvA); a.create()
        assertEquals("GAME_UNAVAILABLE", a.reason())
        val on = hub(stakes = null)
        val b = Peer(on, tvB); b.create(game = "cards")
        assertEquals("GAME_UNKNOWN", b.reason())
        assertEquals(0, on.chessRooms().size)
    }

    @Test fun aStakedRoomRequiresTheActivationOfTheTvThatPlays() {
        val hub = hub()
        val a = Peer(hub, tvA, activation = TestRights.activation(device = tvB))   // l'activation d'une AUTRE TV
        a.create(StakeSpec("NDEM", 20), a.escrow())
        assertEquals("PLAY_SCOPE_FORBIDDEN", a.reason())
        assertEquals(0, hub.chessRooms().size)
    }

    // ------------------------------------------------------------------ forfait, reprise, arrêt

    private fun stakedPair(hub: PlayHub, per: Long = 20): Pair<Peer, Peer> {
        val a = Peer(hub, tvA); a.create(StakeSpec("NDEM", per), a.escrow("NDEM", per), seconds = 60)
        val b = Peer(hub, tvB); b.hello(); b.join(a.code(), escrow = b.escrow("NDEM", per))
        return a to b
    }

    @Test fun aTvThatResumesWithinSixtySecondsKeepsItsSeat() {
        val hub = hub()
        val (a, b) = stakedPair(hub)
        a.move("e2e4", 0)
        now += 1_000
        hub.onClosed(b.conn)                                        // la liaison de B tombe (aux Noirs de jouer, 60 s de pendule)
        now += 20_000; hub.tick()
        val b2 = Peer(hub, tvB); b.resumeOn(b2)                     // 20 s plus tard, nouvelle connexion, même jeton : AUCUN ticket demandé
        assertEquals(b.token, b2.welcomes().single()["token"])
        assertEquals(listOf("e4"), b2.view()["san"]); assertEquals("b", (b2.view()["me"] as Map<*, *>)["color"])
        b2.move("e7e5", 1); assertEquals("OK", b2.lastAck())
        now += 30_000; hub.tick()
        assertEquals(ChessServerRoom.State.PLAYING, hub.chessRooms().single().phase())
        assertTrue(b2.results().isEmpty() && a.results().isEmpty())
    }

    @Test fun aTvThatStaysAwayLosesByForfeitAndTheStakeIsLost() {
        val hub = hub()
        val (a, b) = stakedPair(hub)
        a.move("e2e4", 0)
        val t0 = now
        hub.onClosed(a.conn)                                        // les Blancs jouent puis leur TV tombe
        now = t0 + 5_000
        b.move("e7e5", 1)                                           // les Noirs répondent : la pendule des Blancs (absents) court jusqu'à t0 + 65 s
        now = t0 + 59_999; hub.tick()
        assertTrue(b.results().isEmpty(), "59,999 s : pas encore de forfait")
        now = t0 + 60_000; hub.tick()
        val r = verified(b.results().single())
        assertEquals("FORFEIT", (b.view()["result"] as Map<*, *>)["reason"])
        assertEquals(0L, r.lines.single { it.id == tvA.code }.pay, "forfait = mise perdue")
        assertEquals(40L, r.lines.single { it.id == tvB.code }.pay)
        // la TV absente retrouve son résultat à la reprise (elle peut le poster elle-même)
        val a2 = Peer(hub, tvA); a.resumeOn(a2)
        assertEquals(b.results().single(), a2.results().single())
    }

    @Test fun theServiceStoppingAbortsEveryOngoingStakedGameAndRefundsTheEscrows() {
        val dir = StakeKit.tempDir()
        val services = StakeKit.services(dir)
        val hub = hub(stakes = services)
        val (a, b) = stakedPair(hub)
        a.move("e2e4", 0)
        val aborted = hub.finishGames("DRAIN")
        assertEquals(1, aborted)
        val r = verified(a.results().single())
        assertEquals(PlayResult.Kind.ABORT, r.kind)
        assertTrue(r.lines.all { it.used == 0L && it.pay == 0L }); assertEquals(2, r.lines.size)
        assertEquals(a.results().single(), b.results().single())
        assertEquals(listOf(r.rid), services.spool.list(), "déposé pour le collecteur")
        assertEquals(0, hub.chessRooms().size)
        assertEquals("ABANDONED", (b.view()["result"] as Map<*, *>)["reason"])
    }

    @Test fun theHostCancellingBeforeAnyoneJoinsReleasesItsEscrowThroughAnAbort() {
        val services = StakeKit.services()
        val hub = hub(stakes = services)
        val a = Peer(hub, tvA); a.create(StakeSpec("NDEM", 10), a.escrow("NDEM", 10))
        a.send(ClientMsg.GameAct("cancel", null, null, 4))
        val r = verified(a.results().single())
        assertEquals(PlayResult.Kind.ABORT, r.kind); assertEquals(1, r.lines.size)
        assertEquals(listOf(r.rid), services.spool.list())
    }

    @Test fun aFinishedRoomIsPurgedAfterItsGraceAndANewDrainDoesNotAbortIt() {
        val hub = hub(stakes = null)
        val a = Peer(hub, tvA); a.create(); val b = Peer(hub, tvB); b.hello(); b.join(a.code())
        foolsMate(a, b)
        assertEquals(0, hub.finishGames("DRAIN"), "une partie finie n'est pas interrompue")
    }

    @Test fun closingAConnectionDoesNotLeakTheSeatAndTheRoomGoesWhenEveryoneIsGone() {
        val cfg = cfg("idle" to 1L)
        val hub = hub(stakes = null, cfg = cfg)
        val a = Peer(hub, tvA); a.create()
        hub.onClosed(a.conn)
        Thread.sleep(5)
        hub.tick()
        assertEquals(0, hub.chessRooms().size, "sans connexion depuis `roomIdleMs` : fermée")
    }

    @Test fun aGameMessageNeverReachesAQuizRoomAndAQuizMessageNeverChangesAChessRoom() {
        val hub = hub(stakes = null)
        val q = Peer(hub, tvC); q.hello(); q.send(TestRights.create(q.activation))
        q.send(ClientMsg.GameAct("move", "e2e4", 0, 1))
        assertEquals("FORBIDDEN", q.reason())
        val a = Peer(hub, tvA); a.create()
        a.send(ClientMsg.Act("q1", "answer", 1, null, 1))
        assertEquals("FORBIDDEN", a.reason(), "une réponse de Quiz n'a pas cours dans une partie d'échecs")
    }

    @Test fun theRoomCapCountsChessAndQuizRoomsTogether() {
        val hub = hub(stakes = null, cfg = cfg("maxRooms" to 2))
        val q = Peer(hub, tvC); q.hello(); q.send(TestRights.create(q.activation))
        val a = Peer(hub, tvA); a.create()
        val b = Peer(hub, tvB); b.create()
        assertEquals("PLAY_BUSY", b.reason(), "deux salles ouvertes (Quiz + échecs) : la troisième est refusée")
    }

    @Test fun theEscrowOfAFailedEntryCanBeUsedAgain() {
        val hub = hub()
        val a = Peer(hub, tvA); a.create(StakeSpec("NDEM", 20), a.escrow())
        val b = Peer(hub, tvB); b.hello()
        val esc = b.escrow()
        b.join("ZZZZZZZZ", escrow = esc)                           // faute de frappe sur le code
        assertEquals("PLAY_BAD_CODE", b.reason())
        b.join(a.code(), escrow = esc)                             // le MÊME blocage sert à la bonne salle
        assertEquals("PLAYER", b.welcomes().single()["role"])
        assertNotNull(hub.chessRooms().single().escrowIds().singleOrNull { it == StakeKit.eidOf(esc) })
    }
}
