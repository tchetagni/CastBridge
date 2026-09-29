package castbridge.core

import castbridge.core.tv.ReceiverServer
import java.net.ServerSocket
import kotlin.test.*

class AdminPageTest {
    @Test fun rootServesAdminPageWithoutPin() {
        val dir = kotlin.io.path.createTempDirectory("tvw").toFile()
        val port = ServerSocket(0).use { it.localPort }
        val s = ReceiverServer(dir, FakePlayer(), port, pin = "123456").apply { start(5000, false) }
        try {
            val html = java.net.URL("http://127.0.0.1:$port/").readText()
            assertTrue(html.contains("X-CB-Pin") && html.contains("/upload/") && html.contains("/api/part"), "embedded admin page")
            assertFalse(html.contains("123456"))
        } finally { s.stop(); dir.deleteRecursively() }
    }
}
