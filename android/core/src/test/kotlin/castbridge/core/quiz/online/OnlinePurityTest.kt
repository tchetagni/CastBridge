package castbridge.core.quiz.online

import java.io.File
import kotlin.test.*

/**
 * The online core is pure: no network, no file, no real clock (it runs identically on the TV, the phone and the server).
 * w20-05a: ONE file is the declared exception, the TV's outgoing transport (HttpURLConnection only, no OkHttp): [NETWORK_EXCEPTIONS]; it still gets no real clock.
 */
class OnlinePurityTest {
    private val NETWORK_EXCEPTIONS = setOf("PlayHttpTransport.kt")

    @Test fun theNetworkExceptionIsExactlyTheTvTransportAndUsesOnlyJdkUrlConnections() {
        val src = File("src/main/kotlin/castbridge/core/quiz/online/PlayHttpTransport.kt").readText()
        assertFalse(src.contains("okhttp") || src.contains("TrustManager") || src.contains("HostnameVerifier") || src.contains("SSLContext") || src.contains("setDefaultSSLSocketFactory"), "aucune confiance TLS ajoutée, aucun OkHttp")
        assertTrue(src.contains("instanceFollowRedirects = false"))
    }

    @Test fun noNetworkOrFileImportInTheOnlinePackage() {
        val dir = File("src/main/kotlin/castbridge/core/quiz/online")
        val files = dir.listFiles { f -> f.extension == "kt" }!!.toList()
        assertTrue(files.size >= 5, "sources found from ${File(".").absolutePath}")
        files.forEach { f ->
            f.readLines().forEachIndexed { i, line ->
                if (f.name !in NETWORK_EXCEPTIONS && line.trimStart().startsWith("import ")) {
                    assertFalse(line.contains("java.net") || line.contains("java.io") || line.contains("java.nio") || line.contains("okhttp"), "${f.name}:${i + 1} $line")
                }
            }
            val body = f.readText()
            assertFalse(body.contains("System.currentTimeMillis") || body.contains("nanoTime"), "${f.name}: clock must be injected")
        }
    }
}
