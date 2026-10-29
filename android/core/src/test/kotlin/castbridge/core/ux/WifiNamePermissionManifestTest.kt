package castbridge.core.ux

import castbridge.core.PhoneSources
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Défaut « je ne vois pas le nom du Wi-Fi » (2026-10-29) : Android ne donne le SSID qu'avec ACCESS_FINE_LOCATION. Le téléphone la déclarait avec `maxSdkVersion="30"` (reporté par la
 * fusion avec ownerlib) et la TV avec `maxSdkVersion="32"` : sur Android 14 elle n'était jamais accordable, le nom restait « <unknown ssid> ». Ce garde lit les manifestes sources
 * (le JVM ne charge pas le manifeste) : FINE ne doit porter aucune limite inférieure à 34, et dans `sender` il faut `tools:remove` pour écarter celle d'ownerlib.
 */
class WifiNamePermissionManifestTest {
    private fun fineLine(rel: String): String =
        Regex("""<uses-permission\b[^>]*ACCESS_FINE_LOCATION[^>]*/>""").find(PhoneSources.text(rel))?.value
            ?: error("$rel ne déclare plus ACCESS_FINE_LOCATION : le nom du Wi-Fi ne peut plus être lu")

    private fun maxSdk(line: String): Int? = Regex("""android:maxSdkVersion="(\d+)"""").find(line)?.groupValues?.get(1)?.toInt()

    @Test fun receiverFineLocationHasNoLowMaxSdk() {
        val max = maxSdk(fineLine("receiver/src/main/AndroidManifest.xml"))
        assertTrue(max == null || max >= 34, "receiver : ACCESS_FINE_LOCATION limitée à l'API $max, la TV Android 14 ne verrait plus le nom du Wi-Fi")
    }

    @Test fun senderFineLocationDropsOwnerlibMaxSdk() {
        val line = fineLine("sender/src/main/AndroidManifest.xml")
        val max = maxSdk(line)
        assertTrue(max == null || max >= 34, "sender : ACCESS_FINE_LOCATION limitée à l'API $max")
        assertTrue(line.contains("""tools:remove="android:maxSdkVersion""""), "sender : sans tools:remove la limite de ownerlib (30) revient par la fusion des manifestes")
    }
}
