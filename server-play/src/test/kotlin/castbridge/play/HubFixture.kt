package castbridge.play

import castbridge.core.quiz.EmbeddedQuestionSource
import castbridge.core.quiz.Json
import castbridge.core.quiz.QuizBank
import castbridge.core.quiz.online.ClientMsg
import castbridge.core.quiz.online.PlayCodec
import castbridge.core.quiz.online.PlayProtocol
import castbridge.play.entitlement.ReservedBank
import castbridge.play.entitlement.TicketVerifier
import java.util.concurrent.atomic.AtomicInteger

/** Une connexion simulée (sans socket) : garde tout ce que le serveur lui envoie. */
class TestConn(ip: String = "198.51.100.1") : PlayConn("tc-" + n.incrementAndGet(), ip, 1_000_000, 1_000_000) {
    val out = ArrayList<String>()
    @Volatile var closedWith: Int? = null
    override fun offer(text: String): Boolean { synchronized(out) { out += text }; return true }
    override fun close(code: Int, reason: String) { closedWith = code }

    private fun parsed() = synchronized(out) { out.map { Json.parse(it) as Map<*, *> } }
    fun welcomed() = parsed().any { it["t"] == "welcome" }
    fun errors() = parsed().filter { it["t"] == "error" }
    fun lastError() = errors().lastOrNull()
    fun errorReason() = lastError()?.get("reason") as String?
    fun errorMessage() = lastError()?.get("message") as String?
    companion object { private val n = AtomicInteger() }
}

object HubFixture {
    val bank: QuizBank by lazy { EmbeddedQuestionSource(levels = null).bank() }

    fun config(vararg more: Pair<String, Any>): PlayConfig {
        val m = more.toMap()
        return PlayConfig(port = 0, ticketPubKeys = listOf(TestKeys.pub), trustedKeys = TestRights.trustedKeys,
            maxRooms = m["maxRooms"] as Int? ?: 400, maxRoomsPerSubject = m["perSubject"] as Int? ?: 1000, roomIdleMs = m["idle"] as Long? ?: 10 * 60_000L, createsPerIpPerHour = m["perIp"] as Int? ?: 100_000,
            maxUsedTickets = m["used"] as Int? ?: 20_000,
            createsPerIdentityPerDay = m["perIdentityDay"] as Int? ?: 100_000, createsPer48PerHour = m["per48"] as Int? ?: 1_000_000)
    }

    fun hub(cfg: PlayConfig = config(), verifier: TicketVerifier = TicketVerifier(listOf(TestKeys.pub)), reserved: ReservedBank? = null, bank: QuizBank = this.bank,
            settings: castbridge.core.quiz.online.ServerRoom.Settings = castbridge.core.quiz.online.ServerRoom.Settings(), ready: () -> Boolean = { true }): PlayHub =
        PlayHub(cfg, System::currentTimeMillis, reserved?.freeBank ?: bank, verifier, settings = settings, limits = ConnectionLimits(100_000, 100_000), reserved = reserved, revocationsReady = ready)

    /** `hello` (ticket) puis `create` (activation) sur [c] ; rend la connexion. */
    fun open(hub: PlayHub, ticket: String?, create: ClientMsg.Create = TestRights.create(), c: TestConn = TestConn()): TestConn {
        hub.register(c)
        hub.onText(c, PlayCodec.encode(ClientMsg.Hello(PlayProtocol.PROTO, PlayProtocol.CAPS, null, ticket)))
        hub.onText(c, PlayCodec.encode(create))
        return c
    }
}
