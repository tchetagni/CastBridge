package castbridge.core.tv

import java.security.SecureRandom

/** Pure helpers for the Wi-Fi Direct channel (the Android parts live in the apps). */
object WifiDirect {
    /** Address of the group owner (the TV) on a Wi-Fi Direct group: fixed by Android. */
    const val GROUP_OWNER_IP = "192.168.49.1"
    const val BASE_URL = "http://$GROUP_OWNER_IP:${ReceiverServer.PORT}"

    // No look-alike characters (0/O, 1/l/I): the password is read off a TV screen and typed on a phone.
    private const val ALPHABET = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    /** Length of a group's WPA2-PSK passphrase: 16 random characters of [ALPHABET] (about 93 bits), fresh for every group. */
    const val PASSPHRASE_LENGTH = 16

    fun generatePassphrase(random: java.util.Random = SecureRandom(), length: Int = PASSPHRASE_LENGTH): String {
        require(length in 8..63)
        return (1..length).joinToString("") { ALPHABET[random.nextInt(ALPHABET.length)].toString() }
    }

    /** A new group's passphrase (never stored: it lives as long as the group, docs/agent-reports/auto-wifi-direct.md). */
    fun groupPassphrase(random: java.util.Random = SecureRandom()): String = generatePassphrase(random, PASSPHRASE_LENGTH)

    /** A new group's network name, "DIRECT-CB-" + 6 random characters: two TVs side by side never share a name, so a phone never tries the wrong one. */
    fun groupNetworkName(random: java.util.Random = SecureRandom()): String =
        "DIRECT-CB-" + (1..6).joinToString("") { ALPHABET[random.nextInt(ALPHABET.length)].toString() }

    /** Why the TV gave no group (CBTN answer `wd.err=`): one known word, never free text. */
    object Err {
        const val WIFI_OFF = "wifi_off"
        const val UNSUPPORTED = "unsupported"
        const val PERMISSION = "permission"
        const val TRIAL = "trial"
        const val POLICY = "policy"
        const val FAILED = "failed"
        val ALL = setOf(WIFI_OFF, UNSUPPORTED, PERMISSION, TRIAL, POLICY, FAILED)
    }

    /** A Wi-Fi Direct group name must be "DIRECT-xy" (x, y alphanumeric) plus an optional suffix, 9..32 bytes. */
    fun networkName(suffix: String = "CastBridge"): String {
        val clean = suffix.filter { it.isLetterOrDigit() || it == '-' }.take(20)
        return "DIRECT-CB-$clean"
    }

    fun isValidNetworkName(n: String) =
        Regex("^DIRECT-[A-Za-z0-9]{2}.*$").matches(n) && n.toByteArray(Charsets.UTF_8).size in 9..32

    fun isValidPassphrase(p: String) = p.length in 8..63 && p.all { it.code in 32..126 }

    /** "WIFI:" URI understood by phone cameras/Wi-Fi settings (WPA, escaped per the ZXing convention). */
    fun wifiUri(ssid: String, pass: String): String {
        fun esc(s: String) = s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace(":", "\\:").replace("\"", "\\\"")
        return "WIFI:T:WPA;S:${esc(ssid)};P:${esc(pass)};;"
    }
}
