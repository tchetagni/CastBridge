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
        "sender/src/main/kotlin/castbridge/sender/BtActivationClient.kt",                       // act-bt : le balayage BLE et le canal sans appairage voient le code, la clé et la demande
        "sender/src/main/kotlin/castbridge/sender/ShareRequestIntents.kt",                      // act-fix-2 (R-50) : l'intention de partage porte la demande complète
    )
    private val coreFiles = listOf(
        "core/src/main/kotlin/castbridge/core/owner/ActivationRoutePlan.kt", "core/src/main/kotlin/castbridge/core/owner/KeyAcquisition.kt", "core/src/main/kotlin/castbridge/core/owner/LockedRequestRoute.kt",
        "core/src/main/kotlin/castbridge/core/owner/DeviceRequestText.kt",           // la demande complète et sa forme pour le serveur : jamais dans un journal
        "core/src/main/kotlin/castbridge/core/owner/DeviceRequestInput.kt",          // ce que l'on colle dans la console : la ligne fautive est nommée à l'écran, jamais écrite dans un journal
        "core/src/main/kotlin/castbridge/core/owner/ShareRequestPlan.kt",            // act-fix-2 (R-50) : le plan de partage de la demande (texte seul, QR à part) ; aucun texte d'état ne la recopie
        // act-bt : la PAKE sur le code et le canal chiffré qui porte la demande et la clé ; la porte de tentatives partagée avec la route HTTP
        "core/src/main/kotlin/castbridge/core/owner/BleSearch.kt", "core/src/main/kotlin/castbridge/core/btact/Cpace.kt", "core/src/main/kotlin/castbridge/core/btact/BtActWire.kt",
        "core/src/main/kotlin/castbridge/core/btact/BtActKeys.kt", "core/src/main/kotlin/castbridge/core/btact/BtActChannel.kt", "core/src/main/kotlin/castbridge/core/btact/BtActClient.kt",
        "core/src/main/kotlin/castbridge/core/btact/BtActServer.kt", "core/src/main/kotlin/castbridge/core/btact/BtActAd.kt", "core/src/main/kotlin/castbridge/core/tv/activation/ActivationAttemptGate.kt",
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

    @Test fun theConsoleDoesNotSendTheOwnerRoundByBluetoothForATrialKeyAnyMore() {
        // ACT-F4 amended (2026-10-07): the request read by the code carries `install=` (public key), a trial in a v2 envelope is issued from it; its one sentence lives in the pure core
        val console = source("ownerlib/src/main/kotlin/castbridge/owner/ConsoleActivity.kt")
        assertFalse("La lecture par le code ne donne pas cette clé" in console, "the old sentence is gone")
        assertFalse("lisez la demande complète par Bluetooth" in console)
        assertTrue("ConsoleTrialBox.noKeyMessage" in console, "the sentence of the console comes from the tested core")
    }

    @Test fun theTvHandsItsCompleteRequestToTheLockedRouteNothingStripsTheInstallLineThere() {
        // the receiver module has no JVM test: its one hook is pinned on the source (ACT-F4 amended on 2026-10-07; the pure API rebuilds the text, `DeviceRequestText.complete`)
        val service = source("receiver/src/main/kotlin/castbridge/receiver/TvService.kt")
        assertTrue("deviceRequest = { ActivationCenter.requestText() }" in service, "the locked route is fed with the TV's complete request")
        assertFalse("serverRequestText" in service || "serverRequestText" in source("receiver/src/main/kotlin/castbridge/receiver/ActivationCenter.kt"), "no more server-only form on the TV")
    }

    @Test fun theDriverSubmitsOnlyThroughTheGuardedExecutors() {
        // audit B1 (2026-10-07) : une réponse de la TV qui arrive après release() ne doit jamais atteindre un exécuteur arrêté (RejectedExecutionException dans un fil de pool = processus tué)
        val code = source("sender/src/main/kotlin/castbridge/sender/ActivationDriver.kt").lineSequence().filterNot { it.trim().startsWith("//") || it.trim().startsWith("*") || it.trim().startsWith("/*") }.joinToString("\n")
        assertTrue("ActivationExecutors.standard()" in code, "the pair of executors is the guarded one")
        val raw = Regex("""\bExecutors\b|\.execute\(|\.submit\(|\.schedule\w*\(|\.shutdown\w*\(""").find(code)
        assertNull(raw, "the driver never touches an executor directly (found: ${raw?.value}): everything goes through ActivationExecutors.onMachine / onIo / every / release")
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
