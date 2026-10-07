package castbridge.core.chess.online

import castbridge.core.chess.ChessRelayClient
import castbridge.core.chess.ClockMode
import castbridge.core.owner.Ed25519Signer
import castbridge.core.quiz.online.ClientMsg
import castbridge.core.quiz.online.PlayTvSession
import castbridge.core.quiz.online.ScriptedTransport
import castbridge.core.quiz.online.StakeSpec
import castbridge.core.wallet.WalletCurrency
import castbridge.core.wallet.ui.EscrowDone
import castbridge.core.wallet.ui.SettleDone
import castbridge.core.wallet.ui.SettleLine
import castbridge.core.wallet.ui.WalletMessages
import castbridge.core.wallet.ui.WalletResult
import java.security.MessageDigest

/**
 * Un « service » EN MÉMOIRE pour tester les TV sans socket : il tient UNE salle [ChessServerRoom] (la vraie, celle du serveur) et relie à elle les transports de test de chaque TV. Le rôle du hub (identité et
 * blocage vérifiés) est joué ici par des tables ; l'horloge du serveur est [now] (le test l'avance). Aucune vérification de signature de `cbe1` : c'est l'affaire du hub, testée dans `server-play`.
 */
internal class ChessLoopback(var now: Long = 1_000_000L) {
    val signer = Ed25519Signer(MessageDigest.getInstance("SHA-256").digest("castbridge-chess-loopback-result-key".toByteArray()))
    var room: ChessServerRoom? = null; private set
    val spooled = ArrayList<Pair<String, String>>()
    private val conns = HashMap<String, ScriptedTransport>()
    private val identities = HashMap<String, String>()
    private val escrows = HashMap<String, ChessEscrow>()
    /** Les messages reçus par chaque TV, dans l'ordre. */
    val sentTo = HashMap<String, ArrayList<castbridge.core.quiz.online.ServerMsg>>()
    /** Le service refuse la création ou l'entrée de toute connexion avec ce motif (test d'un échec d'ouverture), puis redevient normal. */
    var refuseNext: String? = null

    fun registerEscrow(cbe1: String, e: ChessEscrow) { escrows[cbe1] = e }

    /** Une nouvelle connexion pour la TV [conn] d'identité [identity] (code d'appareil). */
    fun transport(conn: String, identity: String): ScriptedTransport {
        val t = ScriptedTransport()
        conns[conn] = t; identities[conn] = identity
        t.reply = { m -> deliver(conn, m) }
        return t
    }

    fun dropConnection(conn: String) {
        conns.remove(conn)?.state = castbridge.core.quiz.online.PlayTransport.Status.CLOSED
        room?.disconnect(conn, now)?.let(::push)
    }

    fun tick(advanceMs: Long = 0L) { now += advanceMs; room?.tick(now)?.let(::push) }

    private fun push(outs: List<ChessServerRoom.Out>) {
        for (o in outs) { sentTo.getOrPut(o.to) { ArrayList() } += o.msg; conns[o.to]?.push(o.msg) }
    }

    private fun deliver(conn: String, m: ClientMsg) {
        refuseNext?.let { reason ->
            if (m is ClientMsg.Create || m is ClientMsg.Join) {
                refuseNext = null
                push(listOf(ChessServerRoom.Out(conn, castbridge.core.quiz.online.ServerMsg.Error(0, reason, "refus de test", false))))
                return
            }
        }
        val escrow = when (m) { is ClientMsg.Create -> m.escrow; is ClientMsg.Join -> m.escrow; else -> null }?.let { escrows[it] }
        val adm = ChessServerRoom.Admission(identities[conn], escrow)
        if (m is ClientMsg.Create && room == null) {
            val c = m.chess
            val opts = ChessServerRoom.Options(c?.perMoveSeconds ?: 30, ClockMode.values().firstOrNull { it.name == c?.mode } ?: ClockMode.COMPETITION, c?.color ?: "random", m.stake)
            room = ChessServerRoom("0123456789abcdef0123456789abcdef", java.util.Random(7), now, opts, signer = signer, onResult = { rid, t -> spooled += rid to t })
        }
        val r = room ?: return
        push(r.handle(conn, m, now, "198.51.100.${conn.hashCode() and 0x7f}", adm))
    }
}

/** Le portefeuille d'une TV de test : bloque des mises (enregistrées auprès du service) et règle des résultats comme l'API (frais en points de base sur la cagnotte d'une partie décidée). */
internal class FakeChessWallet(private val identity: String, private val service: ChessLoopback, private val feeBp: Int = 0) : ChessWallet {
    var locks = 0; private set
    var settles = 0; private set
    var refuseLock: WalletResult.Fail? = null
    var refuseSettle: WalletResult.Fail? = null
    var offline = false
    val lockKeys = ArrayList<String>()
    val settledRids = HashSet<String>()
    private var n = 0

    override fun lockEscrow(cur: WalletCurrency, per: Long, game: String, idem: String): WalletResult<EscrowDone> {
        locks++; lockKeys += idem
        if (offline) return WalletResult.Fail(WalletMessages.offline(), null, null, true)
        refuseLock?.let { return it }
        val eid = "Eid${identity.first()}${(n++).toString().padStart(18, '0')}"
        val cbe1 = "cbe1.test.$eid"
        service.registerEscrow(cbe1, ChessEscrow(eid, identity, per))
        return WalletResult.Ok(EscrowDone(cbe1, eid, 0L, Long.MAX_VALUE / 4, false, null))
    }

    override fun settle(token: String): WalletResult<SettleDone> {
        settles++
        if (offline) return WalletResult.Fail(WalletMessages.offline(), null, null, true)
        refuseSettle?.let { return it }
        val s = ChessResultSummary.of(token) ?: return WalletResult.Fail(WalletMessages.of(400, "RESULT_BAD"), 400, "RESULT_BAD", false)
        settledRids += s.rid
        val pot = s.lines.sumOf { it.used }
        val decisive = s.kind == "END" && s.lines.any { it.pay > it.used }
        val fee = if (decisive) pot * feeBp / 10_000 else 0L
        val lines = s.lines.map { l -> SettleLine(l.eid, l.id, l.used, l.pay, if (decisive && l.pay > l.used) fee else 0L) }
        return WalletResult.Ok(SettleDone(s.rid, s.kind, s.cur, "chess", fee, lines))
    }
}

/** Une TV de test : client du service, portefeuille factice, mémoire, vitrine des téléphones et partie orchestrée. */
internal class TestTv(val conn: String, val identity: String, val service: ChessLoopback, feeBp: Int = 0, val wall: () -> Long = { 5_000_000L }) {
    val store = InMemoryChessStakeStore()
    val wallet = FakeChessWallet(identity, service, feeBp)
    private var keys = 0
    val flow = ChessStakeFlow(wallet, store, newKey = { "idem-$conn-${keys++}" })
    val host = OnlineChessHost(random = java.util.Random(3))
    var transports = 0; private set
    val client = ChessRelayClient(
        newSession = { PlayTvSession(clock = { service.now }, transports = { transports++; service.transport(conn, identity) }, ticket = { "cbp1.test" }) },
        activation = { "cbx1.test" }, deviceHash = { "dev-$conn-000001" }, openTimeoutMs = 2_000L, ackTimeoutMs = 2_000L,
    )
    val game = ChessOnlineGame(client, flow, store, host, clock = wall, async = { it() })
    val results = ArrayList<String>()

    init { val prior = client.onResult; client.onResult = { t -> results += t; prior?.invoke(t) } }

    fun create(stake: StakeSpec? = null, color: String = "white", seconds: Int = 30, mode: ClockMode = ClockMode.COMPETITION) = game.create("TV $conn", seconds, color, mode, stake)
}
