package castbridge.core.connect

import castbridge.core.lots.LotId
import castbridge.core.lots.LotMeta
import castbridge.core.lots.LotRemote
import castbridge.core.lots.SecureHttpLotRemote
import com.sun.net.httpserver.HttpServer
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** relay-R1 : « Mettre à jour les lots Langues » passe par `Routes` comme tout appel de la TV vers le serveur : réseau propre d'abord, puis le tuyau du téléphone. */
class RoutedLotRemoteTest {
    private val gw = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", 1080))
    private val meta = LotMeta(LotId("langues", "zh-a1"), 1, 10, "0".repeat(64), "Chinois A1")

    /** Un faux serveur de lots : ce qu'il fait par chemin. */
    private class Fake(val via: String, val log: MutableList<String>, val onCatalog: (String) -> String) : LotRemote {
        override fun catalogJson(channel: String): String { log += "catalog:$via"; return onCatalog(via) }
        override fun open(m: LotMeta, offset: Long): LotRemote.Stream { log += "open:$via:$offset"; onCatalog(via); return LotRemote.Stream(ByteArrayInputStream(byteArrayOf(1, 2, 3)), offset) }
    }

    private fun routed(routes: Routes, log: MutableList<String>, behaviour: (String) -> String) =
        RoutedLotRemote(routes) { p -> Fake(if (p == null) "direct" else "gw", log, behaviour) }

    @Test fun theOwnNetworkIsTriedFirstAndTheCatalogComesFromIt() {
        val log = ArrayList<String>()
        val r = routed(Routes(gateway = { gw }), log) { "{}" }
        assertEquals("{}", r.catalogJson("stable"))
        assertEquals(listOf("catalog:direct"), log)
    }

    @Test fun whenTheOwnNetworkDoesNotAnswerThePhonesPipeIsUsed() {
        val log = ArrayList<String>()
        val r = routed(Routes(gateway = { gw }), log) { via -> if (via == "direct") throw IOException("délai dépassé") else "{\"ok\":1}" }
        assertEquals("{\"ok\":1}", r.catalogJson("stable"))
        assertEquals(listOf("catalog:direct", "catalog:gw"), log)
    }

    @Test fun theTruthSayingViaRelayGoesStraightToThePipe() {
        val log = ArrayList<String>()
        val r = routed(Routes(gateway = { gw }, preferred = { Routes.Via.GATEWAY }), log) { "{}" }
        r.catalogJson("stable")
        assertEquals(listOf("catalog:gw"), log)
    }

    @Test fun theDownloadKeepsItsOffsetAndItsBytes() {
        val log = ArrayList<String>()
        val r = routed(Routes(gateway = { gw }, preferred = { Routes.Via.GATEWAY }), log) { "{}" }
        r.open(meta, 5).use { s -> assertEquals(5, s.from); assertEquals(listOf<Byte>(1, 2, 3), s.input.readBytes().toList()) }
        assertEquals(listOf("open:gw:5"), log)
    }

    @Test fun anAnswerOfTheServerIsNotRetriedThroughThePhone() {
        for (answer in listOf<(String) -> String>({ throw IOException("HTTP 404") }, { throw IOException("redirection refusée : le serveur doit répondre directement") },
                                                 { throw LotRemote.Gone("lot retiré du serveur") })) {
            val log = ArrayList<String>()
            val r = routed(Routes(gateway = { gw }), log, answer)
            assertFailsWith<IOException> { r.catalogJson("stable") }
            assertEquals(listOf("catalog:direct"), log, "le serveur a répondu : un second chemin n'y changerait rien")
        }
    }

    @Test fun theTypeOfTheErrorSurvivesTheRouting() {
        val log = ArrayList<String>()
        val r = routed(Routes(gateway = { gw }), log) { throw LotRemote.Gone("retiré") }
        assertFailsWith<LotRemote.Gone> { r.open(meta, 0) }
    }

    @Test fun whenNothingAnswersTheLastNetworkErrorComesOut() {
        val log = ArrayList<String>()
        val r = routed(Routes(gateway = { gw }), log) { throw IOException("hors ligne") }
        val e = assertFailsWith<IOException> { r.catalogJson("stable") }
        assertEquals("hors ligne", e.message)
        assertEquals(listOf("catalog:direct", "catalog:gw"), log)
    }

    @Test fun withoutAPipeOnlyTheOwnNetworkIsTried() {
        val log = ArrayList<String>()
        val r = routed(Routes(gateway = { null }), log) { throw IOException("hors ligne") }
        assertFailsWith<IOException> { r.catalogJson("stable") }
        assertEquals(listOf("catalog:direct"), log)
    }

    // ------------------------------------------------------------------ les vrais messages du vrai client (HTTP local)

    @Test fun theRealClientsHttpAnswersAreRecognisedAsAnswers() {
        val srv = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        srv.createContext("/api/v1/lots/catalog") { ex -> val b = "{}".toByteArray(); ex.sendResponseHeaders(404, b.size.toLong()); ex.responseBody.use { it.write(b) } }
        srv.createContext("/api/v1/lots/redirect") { ex -> ex.responseHeaders.add("Location", "http://127.0.0.1:1/"); ex.sendResponseHeaders(302, -1); ex.close() }
        srv.start()
        try {
            val seenProxies = ArrayList<Proxy?>()
            val remote = RoutedLotRemote(Routes(gateway = { gw })) { p -> seenProxies += p; SecureHttpLotRemote("http://127.0.0.1:${srv.address.port}", p, allowLoopbackHttp = true) }
            val e = assertFailsWith<IOException> { remote.catalogJson("stable") }
            assertTrue(e.message!!.startsWith("HTTP 404"), e.message)
            assertEquals(listOf<Proxy?>(null), seenProxies, "une réponse 404 du serveur n'envoie pas la TV chercher un autre chemin")
        } finally { srv.stop(0) }
    }
}
