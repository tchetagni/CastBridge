package castbridge.core.net

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * R-29 (relay-R4, inventory I-6): `ConnectivityManager.bindProcessToNetwork` ties EVERY socket of the app to the Wi-Fi Direct group (a network without Internet) while the
 * phone is joined: the Internet jobs of the app (orders, lots, telemetry, the gateway's own exit) all fail meanwhile. Only the sockets towards the group may be bound,
 * one by one ([BoundRoute]). A test of the sources: no Android module calls it any more (a comment may still tell the story).
 */
class NoProcessBindingTest {
    private val modules = listOf("sender", "receiver", "owner", "ownerlib", "devbridge", "sshd")

    private fun kotlinFiles(module: String): List<File> =
        File("../$module/src/main").takeIf { it.isDirectory }?.walkTopDown()?.filter { it.isFile && it.extension in setOf("kt", "java") }?.toList().orEmpty()

    /** The code of a source file: block comments and line comments removed (the story told by a comment is not a call). */
    private fun code(f: File) = f.readText().replace(Regex("/\\*[\\s\\S]*?\\*/"), " ").replace(Regex("//[^\\n]*"), "")

    @Test fun noAndroidModuleBindsTheWholeProcessToANetwork() {
        val scanned = modules.flatMap { kotlinFiles(it) }
        assertTrue(scanned.any { it.name == "BtUploadService.kt" } && scanned.any { it.name == "WifiDirectScreen.kt" } && scanned.any { it.name == "TvService.kt" },
            "sources not found from ${File(".").absolutePath}")
        val offenders = scanned.filter { Regex("\\bbindProcessToNetwork\\b").containsMatchIn(code(it)) }.map { it.name }
        assertEquals(emptyList(), offenders, "bindProcessToNetwork cuts the Internet of the whole app while the Wi-Fi Direct group is joined: use castbridge.core.net.BoundRoute (one socket at a time)")
    }
}
