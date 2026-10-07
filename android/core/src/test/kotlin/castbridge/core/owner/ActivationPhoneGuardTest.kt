package castbridge.core.owner

import java.io.File
import kotlin.test.*

/**
 * Les interdits du chantier « Activer avec le code » côté téléphone (DESIGN-ACTIVATION-SIMPLE, ACT-NF2) gardés sur les sources : aucune ligne de journal dans les fichiers qui voient le code, la clé ou la
 * demande ; aucune caméra, aucune permission de caméra ; le fournisseur de fichiers du QR n'est pas exporté ; les textes visibles disent « CastBridge » et « CastBridge-TV ».
 */
class ActivationPhoneGuardTest {
    private fun source(relative: String): String {
        val f = File("../$relative").takeIf { it.exists() } ?: File(relative)
        assertTrue(f.exists(), "source introuvable : $relative (le garde ne doit jamais passer en silence)")
        return f.readText()
    }

    private val phoneFiles = listOf(
        "sender/src/main/kotlin/castbridge/sender/ActivationDriver.kt", "sender/src/main/kotlin/castbridge/sender/ActivationGroupJoin.kt",
        "sender/src/main/kotlin/castbridge/sender/ActivationNotice.kt", "sender/src/main/kotlin/castbridge/sender/ActivateTvActivity.kt",
    )
    private val coreFiles = listOf(
        "core/src/main/kotlin/castbridge/core/owner/ActivationRoutePlan.kt", "core/src/main/kotlin/castbridge/core/owner/KeyAcquisition.kt", "core/src/main/kotlin/castbridge/core/owner/LockedRequestRoute.kt",
    )

    @Test fun noFileThatSeesTheCodeTheKeyOrTheRequestWritesToALog() {
        val logging = Regex("""\bLog\.[a-z]\(|\bprintln\(|\bprint\(|printStackTrace|System\.(out|err)|\bTimber\.|android\.util\.Log""")
        for (f in phoneFiles + coreFiles) {
            val code = source(f).lineSequence().filterNot { it.trim().startsWith("//") || it.trim().startsWith("*") || it.trim().startsWith("/*") }.joinToString("\n")
            assertNull(logging.find(code), "$f écrit dans un journal : ${logging.find(code)?.value}")
        }
    }

    @Test fun theConsoleKeyIsHandedBackByTheResultOfTheActivityNeverLogged() {
        val console = source("ownerlib/src/main/kotlin/castbridge/owner/ConsoleActivity.kt")
        val added = console.lines().filter { "RESULT_KEY" in it || "EXTRA_RETURN_KEY" in it || "EXTRA_DEVICE_REQUEST" in it }
        assertTrue(added.isNotEmpty())
        assertTrue(added.none { "Log." in it || "println" in it }, added.toString())
    }

    @Test fun noCameraNeitherAScannerNorAPermissionForIt() {
        val manifest = source("sender/src/main/AndroidManifest.xml")
        assertFalse("android.permission.CAMERA" in manifest, "pas de lecteur QR dans l'app : la caméra du système lit le QR Wi-Fi de la TV")
        for (f in phoneFiles) {
            val s = source(f)
            assertFalse(Regex("""\bMediaStore\.ACTION_IMAGE_CAPTURE|\bCameraX|\bandroidx\.camera|\bBarcodeScanner|\bZXing|com\.google\.zxing|android\.hardware\.camera""").containsMatchIn(s), f)
        }
    }

    @Test fun theQrFileProviderIsPrivateAndHasNoPermissionOfItsOwn() {
        val manifest = source("sender/src/main/AndroidManifest.xml")
        val provider = Regex("""<provider[^>]*activationshare[^>]*>""").find(manifest)?.value ?: fail("fournisseur du QR absent du manifeste")
        assertTrue("""android:exported="false"""" in provider, provider)
        assertTrue("grantUriPermissions" in provider)
        val paths = source("sender/src/main/res/xml/activation_share_paths.xml")
        assertTrue("cache-path" in paths && "external" !in paths, "le QR n'est écrit que dans le cache privé")
    }

    @Test fun visibleTextsSayCastBridgeAndCastBridgeTv() {
        val visible = Regex(""""[^"\n]*(\bsender\b|\breceiver\b|[ée]metteur|r[ée]cepteur)[^"\n]*"""", RegexOption.IGNORE_CASE)
        for (f in phoneFiles + coreFiles) {
            val hits = source(f).lineSequence().filterNot { it.trim().startsWith("import ") || it.trim().startsWith("package ") || it.trim().startsWith("//") || it.trim().startsWith("*") || it.trim().startsWith("/*") }
                .flatMap { l -> visible.findAll(l).map { it.value } }.filterNot { "castbridge.sender" in it || "castbridge.receiver" in it || "castbridge.owner" in it }.toList()
            assertTrue(hits.isEmpty(), "$f : mot interdit dans un texte visible : $hits")
        }
    }
}
