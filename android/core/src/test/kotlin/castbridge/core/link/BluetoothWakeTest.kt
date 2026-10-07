package castbridge.core.link

import castbridge.core.PhoneSources
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * R-31 (audit anti-régression 2026-10-07 b, I-9) : les trois récepteurs « appareil Bluetooth connecté » sont exportés, donc n'importe quelle application peut leur envoyer une intention
 * EXPLICITE : seule l'action attendue, une diffusion protégée (que seul le système peut envoyer), déclenche quoi que ce soit.
 */
class BluetoothWakeTest {
    private val acl = "android.bluetooth.device.action.ACL_CONNECTED"

    @Test fun eachReceiverActsOnItsOwnActionsOnly() {
        for (r in BluetoothWake.Receiver.values()) {
            for (a in r.actions) assertTrue(BluetoothWake.accepts(r, a), "$r : $a")
            for (a in BluetoothWake.PROTECTED - r.actions) assertFalse(BluetoothWake.accepts(r, a), "$r ne traite pas $a")
        }
        assertTrue(BluetoothWake.accepts(BluetoothWake.Receiver.RELAY, acl))
        assertFalse(BluetoothWake.accepts(BluetoothWake.Receiver.RELAY, "android.intent.action.BOOT_COMPLETED"))
        assertTrue(BluetoothWake.accepts(BluetoothWake.Receiver.LOTS, "android.intent.action.BOOT_COMPLETED"))
    }

    @Test fun aForgedOrMissingActionDoesNothing() {
        for (r in BluetoothWake.Receiver.values()) for (a in listOf(null, "", " ", "com.evil.FORGED", "android.intent.action.MAIN", "android.bluetooth.device.action.ACL_CONNECTED ", acl.lowercase(), "ACL_CONNECTED"))
            assertFalse(BluetoothWake.accepts(r, a), "$r doit ignorer « $a »")
    }

    @Test fun everyActionOfAnExportedReceiverIsProtected() {
        for (r in BluetoothWake.Receiver.values()) assertTrue(r.mayBeExported, "$r écoute une action que n'importe quelle application peut envoyer")
        assertEquals(setOf(acl, "android.bluetooth.device.action.BOND_STATE_CHANGED", "android.bluetooth.adapter.action.STATE_CHANGED", "android.intent.action.BOOT_COMPLETED"), BluetoothWake.PROTECTED)
    }

    @Test fun theManifestDeclaresExactlyTheActionsTheCodeAccepts() {
        // le manifeste et le code d'un récepteur disent la même chose : une action de plus dans le manifeste serait reçue puis ignorée (ou l'inverse : jamais reçue)
        val manifest = PhoneSources.text("sender/src/main/AndroidManifest.xml")
        val byClass = mapOf(".RelayWakeReceiver" to BluetoothWake.Receiver.RELAY, ".LinkWakeReceiver" to BluetoothWake.Receiver.LINK, ".LotsTriggerReceiver" to BluetoothWake.Receiver.LOTS)
        for ((name, r) in byClass) {
            val block = manifest.substringAfter("""<receiver android:name="$name"""", "").substringBefore("</receiver>")
            assertTrue(block.isNotEmpty(), "$name absent du manifeste")
            val declared = Regex("""<action\s+android:name="([^"]+)"""").findAll(block).map { it.groupValues[1] }.toSet()
            assertEquals(r.actions, declared, "$name : actions du manifeste et du code")
        }
    }

    @Test fun theThreeReceiversCallTheRuleBeforeAnythingElse() {
        val src = "sender/src/main/kotlin/castbridge/sender"
        for ((file, cls, r) in listOf(Triple("$src/RelayRuntime.kt", "RelayWakeReceiver", "RELAY"), Triple("$src/LinkAndroid.kt", "LinkWakeReceiver", "LINK"), Triple("$src/LotsRuntime.kt", "LotsTriggerReceiver", "LOTS"))) {
            val body = PhoneSources.code(file).substringAfter("class $cls").substringAfter("override fun onReceive(").substringAfter("{").trimStart()
            val first = body.lineSequence().first()
            assertTrue(Regex("""if \(!(?:castbridge\.core\.link\.)?BluetoothWake\.accepts\((?:castbridge\.core\.link\.)?BluetoothWake\.Receiver\.$r, i\.action\)\) return""").containsMatchIn(first), "$cls : « $first »")
        }
    }
}
