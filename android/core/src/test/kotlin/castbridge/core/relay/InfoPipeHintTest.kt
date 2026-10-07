package castbridge.core.relay

import castbridge.core.FakePlayer
import castbridge.core.trust.TvAuth
import castbridge.core.tv.ReceiverServer
import castbridge.core.tv.VolumeRegistry
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * relay-R1 § 2 : sur le Wi-Fi, la demande de tuyau de la TV rejoint le téléphone par la liaison de confiance existante (`GET /api/info` avec son jeton, déjà appelé par le
 * garde-vivant) sous la forme d'un en-tête `X-CB-Pipe: 1`, sans nouvelle route sur le téléphone ni changement du corps JSON.
 */
class InfoPipeHintTest {
    private val dir = kotlin.io.path.createTempDirectory("pipehint").toFile().also { it.deleteOnExit() }

    private fun get(base: String, path: String, pin: String? = "482913"): Pair<Int, String?> {
        val c = URL(base + path).openConnection() as HttpURLConnection
        pin?.let { c.setRequestProperty(TvAuth.PIN_HEADER, it) }
        return try { c.responseCode to c.getHeaderField("X-CB-Pipe") } finally { runCatching { c.errorStream?.close() }; runCatching { c.inputStream?.close() } }
    }

    private fun withServer(hint: () -> Boolean, body: (String) -> Unit) {
        val port = ServerSocket(0).use { it.localPort }
        val server = ReceiverServer(VolumeRegistry.single(dir), FakePlayer(), port, pin = "482913", pipeHint = hint).apply { start(5000, false) }
        try { body("http://127.0.0.1:$port") } finally { server.stop() }
    }

    @Test fun theHeaderIsPresentOnInfoWhileTheTvWantsAPipeAndOnlyThen() {
        var wanted = false
        withServer({ wanted }) { base ->
            assertEquals(200 to null, get(base, "/api/info"))
            wanted = true
            assertEquals(200 to "1", get(base, "/api/info"))
            wanted = false
            assertEquals(200 to null, get(base, "/api/info"), "dès que le tuyau est ouvert, la demande retombe")
        }
    }

    @Test fun otherRoutesAndUnauthenticatedCallsNeverCarryIt() {
        withServer({ true }) { base ->
            val (code, hint) = get(base, "/api/info", pin = null)
            assertEquals(401, code); assertNull(hint, "pas d'information avant l'authentification")
            assertNull(get(base, "/api/hello", pin = null).second, "/api/hello est public : jamais de demande dessus")
        }
    }

    @Test fun aHintThatThrowsDoesNotBreakTheInfoRoute() {
        withServer({ throw IllegalStateException("boum") }) { base ->
            val (code, hint) = get(base, "/api/info")
            assertEquals(200, code); assertNull(hint)
        }
    }

    @Test fun withoutAHintSupplierTheServerIsExactlyWhatItWas() {
        val port = ServerSocket(0).use { it.localPort }
        val server = ReceiverServer(VolumeRegistry.single(dir), FakePlayer(), port, pin = "482913").apply { start(5000, false) }
        try { assertTrue(get("http://127.0.0.1:$port", "/api/info").second == null) } finally { server.stop() }
    }
}
