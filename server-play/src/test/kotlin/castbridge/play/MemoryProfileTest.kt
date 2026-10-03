package castbridge.play

import castbridge.play.entitlement.TicketVerifier
import castbridge.core.quiz.EmbeddedQuestionSource
import castbridge.core.quiz.online.ClientMsg
import castbridge.core.quiz.online.PlayCodec
import castbridge.core.quiz.online.PlayProtocol
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Mesure de mémoire (ordre de grandeur, pas une garantie) : 100 salles × (1 hôte + 8 joueurs) simulés, Duel lancé, sans socket ; la banque de questions est chargée AVANT la mesure.
 * Résultat écrit dans build/memory-profile.txt et repris dans le rapport.
 */
class MemoryProfileTest {
    private class Sink(id: String) : PlayConn(id, "198.51.100.1", 1_000_000, 1_000_000) {
        @Volatile var code: String? = null
        override fun offer(text: String): Boolean {
            sent.addAndGet(text.length.toLong())
            if (text.startsWith("{\"t\":\"welcome\"")) code = Regex("\"code\":\"([0-9A-Z]{8})\"").find(text)?.groupValues?.get(1)
            return true
        }
        override fun close(code: Int, reason: String) {}
        companion object { val sent = AtomicLong() }
    }

    /** Tas utilisé après plusieurs ramasse-miettes : la plus petite de trois lectures (le bruit ne peut que l'augmenter). */
    private fun used(): Long = (1..3).minOf { System.gc(); Thread.sleep(150); System.gc(); val r = Runtime.getRuntime(); r.totalMemory() - r.freeMemory() }

    @Test fun hundredRoomsOfEightClientsFitInMemory() {
        val bank = EmbeddedQuestionSource(levels = null).bank()
        val limits = ConnectionLimits(100_000, 100_000)
        val hub = PlayHub(PlayConfig(maxRooms = 400, maxRoomsPerSubject = 1_000, createsPerIdentityPerDay = 100_000, ticketPubKeys = listOf(TestKeys.pub), trustedKeys = TestRights.trustedKeys, createsPerIpPerHour = 10_000), { 1_000L }, bank, TicketVerifier(listOf(TestKeys.pub)), limits = limits)
        val keep = ArrayList<PlayConn>()
        // échauffement : une salle jouée puis fermée charge les classes et remplit les caches avant la mesure
        val warm = PlayHub(PlayConfig(ticketPubKeys = listOf(TestKeys.pub), trustedKeys = TestRights.trustedKeys, createsPerIpPerHour = 10_000), { 1_000L }, bank, TicketVerifier(listOf(TestKeys.pub)), limits = limits)
        Sink("warm").also { it.ticket = TestKeys.ticket(); warm.register(it); warm.onText(it, PlayCodec.encode(TestRights.create())); warm.onText(it, PlayCodec.encode(ClientMsg.Act(null, "start", null, "7", 1))); warm.closeAll() }
        val before = used()
        repeat(100) { r ->
            val tv: Sink = Sink("tv$r").also { it.ticket = TestKeys.ticket(); hub.register(it); keep += it }
            hub.onText(tv, PlayCodec.encode(TestRights.create()))
            val code = tv.code!!
            repeat(8) { p ->
                val c = Sink("c$r-$p").also { hub.register(it); keep += it }
                hub.onText(c, PlayCodec.encode(ClientMsg.Join(code, "J$p", null, "dev-$r-$p-aaaaaaaa", false)))
            }
            hub.onText(tv, PlayCodec.encode(ClientMsg.Act(null, "start", null, "7", 1)))
        }
        hub.tick()
        val after = used()
        assertEquals(100, hub.roomCount()); assertEquals(900, hub.connectionCount())
        val mb = (after - before) / 1_048_576.0
        val line = "100 salles x 8 joueurs + hote simules (Duel lance, 900 connexions factices) : %.1f Mo de tas en plus (%.0f Ko par salle) ; octets serveur->client emis pendant la mesure : %d"
            .format(mb, (after - before) / 1024.0 / 100, Sink.sent.get())
        println(line)
        File("build").also { it.mkdirs() }.resolve("memory-profile.txt").writeText(line + "\n")
        assertTrue(mb < 200, line)
        assertEquals(900, keep.size, "les connexions factices restent vivantes jusqu'ici")
    }
}
