package castbridge.core.tv

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Activation « sans réseau à configurer » (décision du propriétaire, 2026-10-07) : la TV verrouillée affiche un code de connexion à 6 chiffres
 * et crée un groupe Wi-Fi Direct dont le nom et le mot de passe DÉRIVENT de ce code. Le téléphone qui a lu le code sur l'écran rejoint le groupe
 * seul (WifiNetworkSpecifier, aucune configuration Wi-Fi, aucun appairage Bluetooth) et parle à la TV sur 192.168.49.1. Pur et déterministe :
 * les deux applications calculent la même chose.
 *
 * Portée du secret : 6 chiffres lus sur un écran = présence physique. Le groupe n'existe que pendant l'écran d'activation ; y entrer permet au plus
 * de lire la demande d'appareil et d'installer une clé SIGNÉE pour cette TV (vérifiée comme une clé collée). Le nom du réseau ne contient pas le
 * code (il est diffusé par radio) : il en dérive.
 */
object WdCode {
    const val DIGITS = 6
    private const val KEY = "castbridge-wd-code-v1"
    const val SSID_PREFIX = "DIRECT-CB-"

    fun isValid(code: String): Boolean = code.length == DIGITS && code.all { it in '0'..'9' }

    /** Nom du groupe, « DIRECT-CB- » + 6 caractères dérivés (jamais le code lui-même). */
    fun networkName(code: String): String = SSID_PREFIX + WifiDirect.charsFromBytes(mac("ssid:" + norm(code)), 6)

    /** Mot de passe WPA2 du groupe, 16 caractères de l'alphabet sans sosies, dérivés du code. */
    fun passphrase(code: String): String = WifiDirect.charsFromBytes(mac("pass:" + norm(code)), WifiDirect.PASSPHRASE_LENGTH)

    /** URI « WIFI: » à mettre dans le QR de l'écran d'activation (la caméra du téléphone propose de rejoindre le réseau). */
    fun wifiUri(code: String): String = WifiDirect.wifiUri(networkName(code), passphrase(code))

    /** Le téléphone reconnaît un groupe d'activation à son nom, sans connaître le code : il demande alors le code à l'utilisateur. */
    fun looksLikeActivationGroup(ssid: String): Boolean = ssid.startsWith(SSID_PREFIX) && ssid.length == SSID_PREFIX.length + 6

    private fun norm(code: String): String {
        require(isValid(code)) { "code de connexion : 6 chiffres attendus" }
        return code
    }

    private fun mac(msg: String): ByteArray {
        val m = Mac.getInstance("HmacSHA256")
        m.init(SecretKeySpec(KEY.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return m.doFinal(msg.toByteArray(Charsets.UTF_8))
    }
}
