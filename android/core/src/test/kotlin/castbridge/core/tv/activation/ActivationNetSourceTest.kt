package castbridge.core.tv.activation

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * act-tv-2: the Android side of the group policy (`ActivationNet`) writes words and counters to the journal, never a connection code, an address, a network name or a password
 * (ACT-NF2: nothing secret in `adb logcat`). Source test, in the manner of PlayTvSecretsSourceTest: the Android class cannot run on the JVM.
 */
class ActivationNetSourceTest {
    private fun source(): List<String> {
        val f = listOf("../receiver/src/main/kotlin/castbridge/receiver/ActivationNet.kt", "receiver/src/main/kotlin/castbridge/receiver/ActivationNet.kt").map(::File).firstOrNull { it.exists() }
        assertTrue(f != null, "ActivationNet.kt not found from ${File(".").absolutePath}")
        return f!!.readLines()
    }

    @Test fun `the Wi-Fi reader and the relaunch log fixed words and counters only`() {
        val logs = source().filter { l -> "Log." in l && !l.trimStart().startsWith("*") && !l.trimStart().startsWith("//") }
        assertTrue(logs.size >= 4, "the relaunch says what it does: ${logs.size} journal lines")
        for (l in logs) {
            for (bad in listOf("hostAddress", "linkAddresses", "ssid", "networkName", "passphrase", "wifiUri", "WdCode", "lockedPin", "code")) assertFalse(bad in l, "« $bad » in a journal line: $l")
            // an interpolation is a counter, the answer of reconnect() or an exception's class name: nothing else
            for (m in Regex("""\$\{([^}]*)}""").findAll(l)) {
                val expr = m.groupValues[1]
                assertTrue(listOf("attempts", "RESTORE_ATTEMPTS", "reconnect(app)", "javaClass.simpleName").any { it in expr }, "unexpected interpolation « $expr » in: $l")
            }
        }
    }
}
