package castbridge.play.guard

import castbridge.core.quiz.EmbeddedQuestionSource
import castbridge.core.quiz.online.ClientMsg
import castbridge.core.quiz.online.PlayCodec
import castbridge.core.quiz.online.ServerRoom
import castbridge.play.Cidr
import castbridge.play.ConnectionLimits
import castbridge.play.PlayConfig
import castbridge.play.PlayConn
import castbridge.play.PlayHub
import castbridge.play.TestKeys
import castbridge.play.TestRights
import castbridge.play.entitlement.TicketVerifier
import java.util.concurrent.atomic.AtomicInteger

/** Connexion factice : garde tout ce que le service lui envoie et le code de fermeture demandé. */
class FakeConn(id: String, ip: String = "203.0.113.1") : PlayConn(id, ip, 1_000_000, 1_000_000) {
    val got = ArrayList<String>()
    @Volatile var closedWith: Int? = null
    override fun offer(text: String): Boolean { got += text; return true }
    override fun close(code: Int, reason: String) { if (closedWith == null) closedWith = code }
    fun errors(): List<String> = got.filter { it.startsWith("{\"t\":\"error\"") }
    fun welcomed(): Boolean = got.any { it.startsWith("{\"t\":\"welcome\"") }
}

object GuardHarness {
    private val devs = AtomicInteger()
    fun dev(): String = "guard-dev-%08d".format(devs.incrementAndGet())

    val bank = EmbeddedQuestionSource(levels = null).bank()

    fun hub(settings: ServerRoom.Settings = ServerRoom.Settings(), clock: () -> Long = { 1_000L }, limits: ConnectionLimits = ConnectionLimits(1_000_000, 1_000_000, 1_000_000)): PlayHub =
        PlayHub(PlayConfig(requireProof = false, webPlay = true, revocationsMode = castbridge.play.RevocationsMode.OFF, trustedProxies = listOf(Cidr.parse("127.0.0.1/32")!!), ticketPubKeys = listOf(TestKeys.pub), trustedKeys = TestRights.trustedKeys, createsPerIpPerHour = 100_000), clock, bank, TicketVerifier(listOf(TestKeys.pub)), settings = settings, limits = limits)

    /** Une TV qui crée la salle : (connexion, code de la salle). */
    fun host(hub: PlayHub, id: String = "tv", ip: String = "198.51.100.9"): Pair<FakeConn, String> {
        val tv = FakeConn(id, ip).also { it.ticket = TestKeys.ticket(); hub.register(it) }
        hub.onText(tv, PlayCodec.encode(TestRights.create()))
        val code = Regex("\"code\":\"([0-9A-Z]{8})\"").find(tv.got.first { it.startsWith("{\"t\":\"welcome\"") })!!.groupValues[1]
        return tv to code
    }

    fun join(hub: PlayHub, code: String, name: String, id: String, ip: String = "203.0.113.1", device: String = dev(), spectate: Boolean = false): FakeConn =
        FakeConn(id, ip).also { hub.register(it); hub.onText(it, PlayCodec.encode(ClientMsg.Join(code, name, null, device, spectate))) }
}
