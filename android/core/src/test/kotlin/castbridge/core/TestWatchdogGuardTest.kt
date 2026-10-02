package castbridge.core

import java.io.File
import kotlin.test.*

/**
 * Garde « aucun test réseau ne peut bloquer la suite » (w15-07). Un test qui attend un socket, un thread ou un verrou sans borne a déjà tenu le verrou
 * Gradle 52 minutes (ByteRelayTest.noServerMeansRefused). Trois protections, vérifiées ici :
 *  1. le chien de garde de `android/core/build.gradle.kts` (tâche limitée à 40 min ; un test de plus de 60 s tue le processus de test en nommant le test
 *     dans le journal « TEST TROP LONG » : Gradle fait alors ÉCHOUER la tâche) ne doit être ni retiré ni assoupli ;
 *  2. le pool keep-alive du JDK reste désactivé dans la JVM de test (un port réutilisé par un autre serveur de test faisait échouer la première requête) ;
 *  3. cliquet : une classe de test qui ouvre de vrais sockets n'ajoute aucune attente sans borne (`Thread.join()` / `latch.await()` sans délai) au-delà de
 *     l'existant (liste ci-dessous). Une nouvelle attente doit porter un délai (`join(10_000)`, `await(5, SECONDS)`).
 */
class TestWatchdogGuardTest {
    private val root = File(System.getProperty("branding.dir") ?: error("branding.dir")).parentFile
    private val core = File(root, "android/core")
    private val gradle get() = File(core, "build.gradle.kts").readText()

    @Test fun theGradlePerTestWatchdogIsWiredAndAtMostSixtySeconds() {
        val g = gradle
        val minutes = Regex("""timeout\.set\(Duration\.ofMinutes\((\d+)\)""").find(g)?.groupValues?.get(1)?.toInt()
        assertNotNull(minutes, "la tâche test doit avoir une limite globale")
        assertTrue(minutes in 1..60, "limite globale ($minutes min) trop large")
        val perTest = Regex("""\?:\s*(\d[\d_]*)L""").findAll(g).map { it.groupValues[1].replace("_", "").toLong() }.toList()
        assertTrue(perTest.any { it in 1_000..60_000 }, "le délai par test doit être au plus de 60 s : $perTest")
        for (needed in listOf("addTestListener", "beforeTest", "afterTest", "destroyForcibly", "TEST TROP LONG"))
            assertTrue(needed in g, "chien de garde par test incomplet : « $needed » absent de build.gradle.kts")
    }

    @Test fun theTestJvmHasNoJdkKeepAlivePool() {
        assertTrue("systemProperty(\"http.keepAlive\", \"false\")" in gradle, "http.keepAlive=false retiré de build.gradle.kts : le pool JDK rend la première requête d'un serveur de test instable (port réutilisé)")
    }

    @Test fun networkTestClassesAddNoUnboundedWait() {
        val net = Regex("""ServerSocket|java\.net\.Socket|\bSocket\(|ReceiverServer\(|openConnection\(|NanoHTTPD""")
        val unbounded = Regex("""\.join\(\)|[A-Za-z_)\]]\.await\(\)""")
        // Existant au moment de w15-07 (attentes dans des fils de test ou sur des verrous locaux) : ne peut que diminuer.
        val allowed = mapOf("TvHardeningTest.kt" to 3, "ProgressiveTest.kt" to 1, "TunnelTest.kt" to 2, "ChessRoomTest.kt" to 1,
            "RemoteTest.kt" to 1, "HarnessSmokeTest.kt" to 1, "DownloadManagerTest.kt" to 1)
        val offenders = ArrayList<String>()
        File(core, "src/test/kotlin").walkTopDown().filter { it.isFile && it.extension == "kt" && it.name != "TestWatchdogGuardTest.kt" }.forEach { f ->
            val text = f.readText()
            if (!net.containsMatchIn(text)) return@forEach
            val n = unbounded.findAll(text).count()
            val max = allowed[f.name] ?: 0
            if (n > max) offenders += "${f.name} : $n attente(s) sans borne (au plus $max autorisée(s)) : ajouter un délai à join()/await()"
        }
        assertTrue(offenders.isEmpty(), offenders.joinToString("\n"))
    }
}
