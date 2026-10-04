package castbridge.core.quiz.online

import castbridge.core.quiz.Json
import castbridge.core.update.Ed25519
import java.security.MessageDigest
import java.util.Base64

/**
 * Preuve de possession de la TV (audit Opus H-3) : l'entrée au service ne prouve plus seulement « une activation `cbx1` » (copiable : téléphones appariés, clé USB) mais « la TV qui
 * détient la clé d'installation » ([castbridge.core.owner.InstallSigner], jamais remise aux téléphones). Même schéma que la preuve de liaison du portefeuille
 * ([castbridge.core.wallet.ui.WalletBind]) avec SON domaine : la TV signe `castbridge-play-bind-v1 \n jti \n code d'appareil \n SHA-256(activation)`, où `jti` et le code viennent du
 * ticket `cbp1` (la preuve ne vaut que pour CE ticket, qui sert une fois, et CETTE activation). Le ticket porte `ik` = SHA-256 (64 hex) de la clé d'installation, épinglée par l'API
 * à la première demande ; le service vérifie que la clé de la preuve a cette empreinte, puis la signature. Forme : `<clé publique brute base64>.<signature base64>`.
 */
object PlayProof {
    const val DOMAIN = "castbridge-play-bind-v1"
    private val JTI = Regex("^[0-9a-f]{32}$")

    private fun sha256Hex(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    fun message(jti: String, deviceCode: String, activation: String): String = "$DOMAIN\n$jti\n$deviceCode\n${sha256Hex(activation.toByteArray(Charsets.UTF_8))}"

    /** Le champ [field] (texte) de la charge utile d'un ticket `cbp1.<charge>.<signature>`, SANS vérifier la signature (le service la vérifie) ; null si illisible. */
    private fun fieldOf(ticket: String?, field: String): String? = try {
        val parts = ticket?.split('.') ?: return null
        if (parts.size != 3 || parts[0] != "cbp1") null
        else (Json.parse(String(Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8)) as? Map<*, *>)?.get(field) as? String
    } catch (e: Exception) { null }

    fun jtiOf(ticket: String?): String? = fieldOf(ticket, "jti")?.takeIf { JTI.matches(it) }
    fun deviceCodeOf(ticket: String?): String? = fieldOf(ticket, "deviceCode")?.takeIf { it.length in 1..24 }

    /** L'empreinte (SHA-256, 64 hex) d'une clé publique brute de 32 octets en base64 ; null si ce n'en est pas une. */
    fun installHash(rawKeyBase64: String): String? = try {
        Base64.getDecoder().decode(rawKeyBase64.trim()).takeIf { it.size == 32 }?.let { sha256Hex(it) }
    } catch (e: IllegalArgumentException) { null }

    /** Côté TV : la preuve pour ce ticket et cette activation ; null si le ticket est illisible ou s'il n'y a pas d'activation. [sign] signe un texte UTF-8 (base64 avec bourrage). */
    fun build(sign: (String) -> String, publicKeyBase64: String, ticket: String?, activation: String?): String? {
        val jti = jtiOf(ticket) ?: return null
        val code = deviceCodeOf(ticket) ?: return null
        if (activation == null) return null
        return publicKeyBase64.trim() + "." + sign(message(jti, code, activation))
    }

    /** Côté service : vrai si la clé de la preuve a l'empreinte [installHash] ET si sa signature couvre exactement ce `jti`, ce code et cette activation. Jamais d'exception. */
    fun verify(proof: String?, jti: String, deviceCode: String, activation: String, installHash: String): Boolean = try {
        if (proof == null || proof.length > PlayProtocol.MAX_PROOF) false
        else {
            val parts = proof.split('.')
            if (parts.size != 2) false
            else {
                val pub = Base64.getDecoder().decode(parts[0]); val sig = Base64.getDecoder().decode(parts[1])
                pub.size == 32 && sig.size == 64 && sha256Hex(pub) == installHash &&
                    Ed25519.verify(pub, message(jti, deviceCode, activation).toByteArray(Charsets.UTF_8), sig)
            }
        }
    } catch (e: Exception) { false }
}
