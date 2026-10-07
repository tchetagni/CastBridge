package castbridge.core.link

import castbridge.core.PhoneSources
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * R-31 (audit anti-régression 2026-10-07 b, I-9) : les récepteurs du manifeste du téléphone réveillés par « appareil Bluetooth connecté » (`ACL_CONNECTED`) étaient
 * `exported="false"`. Cette diffusion est envoyée par l'application Bluetooth d'Android (uid 1002, ni root ni système) : pour un récepteur non exporté, le système la refuse
 * (« is not exported from uid »). `RelayWakeReceiver` (P-83 « CastBridge fermé »), `LinkWakeReceiver` (liaison de confiance en arrière-plan) et `LotsTriggerReceiver` (livraison des lots)
 * ne se réveillaient donc jamais sur ce signal. Les trois sont maintenant exportés ; c'est sans danger parce que ces diffusions sont PROTÉGÉES (aucune application ne peut les
 * envoyer : le système lève une SecurityException) et parce que chaque `onReceive` commence par vérifier l'action attendue.
 *
 * Ce test lit le manifeste et les sources (le JVM ne charge pas le manifeste) : un récepteur n'est exporté que s'il n'écoute QUE des diffusions protégées, et un récepteur qui en écoute
 * l'est obligatoirement.
 */
class BluetoothWakeManifestTest {
    /** Diffusions que seul le système peut envoyer (écrites ici sans passer par le code testé). */
    private val protectedActions = setOf(
        "android.bluetooth.device.action.ACL_CONNECTED", "android.bluetooth.device.action.BOND_STATE_CHANGED",
        "android.bluetooth.adapter.action.STATE_CHANGED", "android.intent.action.BOOT_COMPLETED",
    )
    private val manifest = PhoneSources.text("sender/src/main/AndroidManifest.xml")

    private class Receiver(val name: String, val exported: String?, val actions: List<String>)

    private fun receivers(): List<Receiver> = Regex("""<receiver\b([^>]*)>([\s\S]*?)</receiver>""").findAll(manifest).map { m ->
        val attrs = m.groupValues[1]
        Receiver(
            name = Regex("""android:name="([^"]+)"""").find(attrs)!!.groupValues[1],
            exported = Regex("""android:exported="([^"]+)"""").find(attrs)?.groupValues?.get(1),
            actions = Regex("""<action\s+android:name="([^"]+)"""").findAll(m.groupValues[2]).map { it.groupValues[1] }.toList(),
        )
    }.toList()

    @Test fun theThreeWakeReceiversAreDeclared() {
        assertEquals(setOf(".RelayWakeReceiver", ".LinkWakeReceiver", ".LotsTriggerReceiver"), receivers().map { it.name }.toSet(),
            "un récepteur de plus ou de moins dans le manifeste : relire ce garde (exporté ou non ?)")
    }

    @Test fun everyReceiverThatListensToTheBluetoothConnectionBroadcastIsExported() {
        val acl = receivers().filter { "android.bluetooth.device.action.ACL_CONNECTED" in it.actions }
        assertEquals(setOf(".RelayWakeReceiver", ".LinkWakeReceiver", ".LotsTriggerReceiver"), acl.map { it.name }.toSet(), "les trois écoutent ACL_CONNECTED")
        for (r in acl) assertEquals("true", r.exported, "${r.name} : ACL_CONNECTED vient de l'application Bluetooth (uid 1002) : un récepteur non exporté ne la reçoit jamais")
    }

    @Test fun aReceiverIsExportedOnlyWhenEveryActionItListensToIsProtected() {
        for (r in receivers()) {
            assertTrue(r.actions.isNotEmpty(), "${r.name} n'a aucune action : ce garde ne sait pas le juger")
            if (r.exported == "true") for (a in r.actions) assertTrue(a in protectedActions, "${r.name} est exporté et écoute $a, que n'importe quelle application peut envoyer")
        }
    }

    // ------------------------------------------------------------------ chaque onReceive vérifie l'action avant de faire quoi que ce soit

    private fun onReceiveBody(file: String, cls: String): String {
        val code = PhoneSources.code(file)
        val body = code.substringAfter("class $cls", "").substringAfter("override fun onReceive(", "")
        assertTrue(body.isNotEmpty(), "$cls.onReceive introuvable dans $file")
        return body.substringAfter("{")
    }

    @Test fun everyWakeReceiverChecksTheActionFirst() {
        val src = "sender/src/main/kotlin/castbridge/sender"
        for ((file, cls) in listOf("$src/RelayRuntime.kt" to "RelayWakeReceiver", "$src/LinkAndroid.kt" to "LinkWakeReceiver", "$src/LotsRuntime.kt" to "LotsTriggerReceiver")) {
            val first = onReceiveBody(file, cls).trimStart().lineSequence().first()
            assertTrue(first.trimStart().startsWith("if (") && ".action" in first, "$cls.onReceive : la première instruction doit vérifier l'action de l'intention (exportés, ils peuvent recevoir n'importe quelle intention) : « $first »")
        }
    }
}
