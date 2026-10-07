package castbridge.play.stake

import castbridge.core.owner.Ed25519Signer
import java.io.File
import java.util.Base64

/**
 * La clé dédiée « résultat » du service (`CASTBRIDGE_PLAY_RESULT_KEY_FILE`) : la SEULE clé privée du service, et pas un secret du grand livre. Elle ne signe que les résultats de partie `cbr1`
 * (domaine `castbridge-play-result-v1`) ; l'API n'en connaît que la clé PUBLIQUE (`castbridge.wallet.play-result-pubkeys`). Un service compromis ne peut que redistribuer les mises DÉJÀ bloquées des
 * parties qu'il arbitre (conception W22 § 3.3) : ni créer, ni débiter, ni régler deux fois.
 *
 * Formats lus : graine de 32 octets en Base64, PKCS#8 Ed25519 en Base64 (48 octets) ou en PEM (`openssl genpkey -algorithm ed25519`). Fichier absent, illisible ou invalide : null (le service
 * refuse alors les salles misées, `STAKES_SUSPENDED`, et le reste marche) ; la matière de la clé n'est JAMAIS journalisée.
 */
object ResultKey {
    fun load(file: File?): Ed25519Signer? {
        if (file == null || !file.isFile) return null
        return runCatching { parse(file.readBytes()) }.getOrNull()
    }

    internal fun parse(content: ByteArray): Ed25519Signer? {
        val text = String(content, Charsets.US_ASCII).trim()
        val der = Base64.getDecoder().decode(text.replace(Regex("-----[A-Z ]+-----"), "").replace(Regex("\\s"), ""))
        return when {
            der.size == 32 -> Ed25519Signer(der)
            // PKCS#8 d'une clé Ed25519 : 16 octets d'en-tête fixe puis la graine de 32 octets
            der.size == 48 && der[0] == 0x30.toByte() && der[1] == 0x2e.toByte() -> Ed25519Signer(der.copyOfRange(16, 48))
            else -> null
        }
    }
}
