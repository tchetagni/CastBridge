package castbridge.core.tv

import java.security.SecureRandom

/** Pure helpers for the Wi-Fi Direct channel (the Android parts live in the apps). */
object WifiDirect {
    /** Address of the group owner (the TV) on a Wi-Fi Direct group: fixed by Android. */
    const val GROUP_OWNER_IP = "192.168.49.1"
    const val BASE_URL = "http://$GROUP_OWNER_IP:${ReceiverServer.PORT}"

    // No look-alike characters (0/O, 1/l/I): the password is read off a TV screen and typed on a phone.
    private const val ALPHABET = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    fun generatePassphrase(random: java.util.Random = SecureRandom(), length: Int = 10): String {
        require(length in 8..63)
        return (1..length).joinToString("") { ALPHABET[random.nextInt(ALPHABET.length)].toString() }
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
