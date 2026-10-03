package castbridge.play

import castbridge.core.quiz.Json
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Vérifie les tickets d'ouverture de salle (`v1.<charge utile>.<signature>`, Base64 URL) : signature Ed25519 sur `v1.<charge utile>`,
 * `exp` (ms) pas dépassé, `iat` pas dans le futur (60 s de tolérance), durée de vie ≤ 15 minutes. Le service n'a QUE les clés PUBLIQUES
 * (`CASTBRIDGE_PLAY_TICKET_PUBKEY*`) : l'émetteur (w20-04) est ailleurs. Sans clé configurée, aucun ticket n'est valide.
 */
class TicketVerifier(pubKeys: List<String>) {
    private val keys: List<PublicKey> = pubKeys.mapNotNull { parse(it) }
    val configured: Boolean get() = keys.isNotEmpty()

    fun verify(ticket: String?, nowMs: Long): Boolean {
        if (ticket == null || ticket.length > 1_200 || keys.isEmpty()) return false
        val parts = ticket.split('.')
        if (parts.size != 3 || parts[0] != "v1") return false
        val dec = Base64.getUrlDecoder()
        val sig = runCatching { dec.decode(parts[2]) }.getOrNull() ?: return false
        val head = (parts[0] + "." + parts[1]).toByteArray(Charsets.US_ASCII)
        if (keys.none { k -> runCatching { Signature.getInstance("Ed25519").run { initVerify(k); update(head); verify(sig) } }.getOrDefault(false) }) return false
        val body = runCatching { Json.parse(String(dec.decode(parts[1]), Charsets.UTF_8)) as? Map<*, *> }.getOrNull() ?: return false
        val exp = (body["exp"] as? Number)?.toLong() ?: return false
        val iat = (body["iat"] as? Number)?.toLong() ?: return false
        return nowMs < exp && iat <= nowMs + SKEW_MS && exp - iat <= MAX_LIFE_MS
    }

    private fun parse(text: String): PublicKey? = runCatching {
        val raw = Base64.getMimeDecoder().decode(text.trim().replace('-', '+').replace('_', '/'))
        val spki = if (raw.size == 32) SPKI_PREFIX + raw else raw
        KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(spki))
    }.getOrNull()

    companion object {
        const val SKEW_MS = 60_000L
        const val MAX_LIFE_MS = 15 * 60_000L
        private val SPKI_PREFIX = byteArrayOf(0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00)
    }
}
