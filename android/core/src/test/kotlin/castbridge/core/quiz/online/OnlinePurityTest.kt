package castbridge.core.quiz.online

import java.io.File
import kotlin.test.*

/** The online core is pure: no network, no file, no real clock (it runs identically on the TV, the phone and the server). */
class OnlinePurityTest {
    @Test fun noNetworkOrFileImportInTheOnlinePackage() {
        val dir = File("src/main/kotlin/castbridge/core/quiz/online")
        val files = dir.listFiles { f -> f.extension == "kt" }!!.toList()
        assertTrue(files.size >= 5, "sources found from ${File(".").absolutePath}")
        files.forEach { f ->
            f.readLines().forEachIndexed { i, line ->
                if (line.trimStart().startsWith("import ")) {
                    assertFalse(line.contains("java.net") || line.contains("java.io") || line.contains("java.nio") || line.contains("okhttp"), "${f.name}:${i + 1} $line")
                }
            }
            val body = f.readText()
            assertFalse(body.contains("System.currentTimeMillis") || body.contains("nanoTime"), "${f.name}: clock must be injected")
        }
    }
}
