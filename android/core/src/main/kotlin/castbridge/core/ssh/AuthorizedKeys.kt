package castbridge.core.ssh

import java.security.MessageDigest
import java.util.Base64

/** A public key allowed to log in over SSH (one line of an authorized_keys file, options not supported). */
class SshKey(val type: String, val base64: String, val comment: String) {
    val blob: ByteArray get() = Base64.getDecoder().decode(base64)
    val fingerprint: String get() = AuthorizedKeys.fingerprint(blob)
    val line: String get() = if (comment.isEmpty()) "$type $base64" else "$type $base64 $comment"
    override fun equals(other: Any?) = other is SshKey && other.base64 == base64
    override fun hashCode() = base64.hashCode()
}

/**
 * authorized_keys handling. Deliberately strict: only modern key types, no per-key options
 * (command=, from=, ...), size limits, so a key added through the API can never do more than the
 * server's own policy allows.
 */
object AuthorizedKeys {
    val ALLOWED_TYPES = setOf("ssh-ed25519", "ecdsa-sha2-nistp256", "ecdsa-sha2-nistp384", "ecdsa-sha2-nistp521", "ssh-rsa")
    const val MAX_KEYS = 20
    const val MAX_LINE = 8192
    const val MIN_RSA_BITS = 2048

    class Invalid(msg: String) : IllegalArgumentException(msg)

    /** "SHA256:<base64 without padding>", the format printed by `ssh-keygen -lf` and shown by ssh. */
    fun fingerprint(blob: ByteArray): String =
        "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(blob))

    fun parseLine(raw: String): SshKey {
        val line = raw.trim()
        if (line.isEmpty()) throw Invalid("clé vide")
        if (line.length > MAX_LINE) throw Invalid("clé trop longue")
        if (line.any { it.code < 32 && it != '\t' }) throw Invalid("caractères invalides")
        val parts = line.split(Regex("\\s+"), limit = 3)
        if (parts.size < 2) throw Invalid("format attendu : <type> <clé base64> [commentaire]")
        val type = parts[0]
        if (type !in ALLOWED_TYPES) {
            throw Invalid(if (type.startsWith("ssh-") || type.startsWith("ecdsa-") || type.startsWith("sk-"))
                "type de clé non accepté : $type" else "options de clé non supportées")
        }
        val blob = try { Base64.getDecoder().decode(parts[1]) } catch (e: IllegalArgumentException) { throw Invalid("base64 invalide") }
        val r = Reader(blob)
        if (r.string() != type) throw Invalid("le type ne correspond pas au contenu de la clé")
        if (type == "ssh-rsa") {
            r.mpint()                                   // exponent
            val n = r.mpint()
            val bits = (n.size - (if (n.isNotEmpty() && n[0] == 0.toByte()) 1 else 0)) * 8
            if (bits < MIN_RSA_BITS) throw Invalid("clé RSA trop courte (minimum $MIN_RSA_BITS bits)")
        }
        val comment = parts.getOrElse(2) { "" }.filter { !it.isISOControl() }.trim().take(100)
        return SshKey(type, parts[1], comment)
    }

    /** Parses a whole file; lines that are blank, comments or invalid are skipped. */
    fun parse(text: String): List<SshKey> = text.lineSequence()
        .map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        .mapNotNull { runCatching { parseLine(it) }.getOrNull() }.distinct().toList()

    fun render(keys: List<SshKey>): String = keys.joinToString("") { it.line + "\n" }

    /** Adds [key] (no duplicates). Throws [Invalid] beyond [MAX_KEYS]. */
    fun add(keys: List<SshKey>, key: SshKey): List<SshKey> {
        if (key in keys) return keys
        if (keys.size >= MAX_KEYS) throw Invalid("trop de clés (maximum $MAX_KEYS)")
        return keys + key
    }

    fun remove(keys: List<SshKey>, fingerprint: String): List<SshKey> = keys.filterNot { it.fingerprint == fingerprint }

    /** Minimal SSH wire-format reader (RFC 4251 string / mpint). */
    private class Reader(private val b: ByteArray) {
        private var p = 0
        private fun int(): Int {
            if (p + 4 > b.size) throw Invalid("clé tronquée")
            val v = ((b[p].toInt() and 255) shl 24) or ((b[p + 1].toInt() and 255) shl 16) or ((b[p + 2].toInt() and 255) shl 8) or (b[p + 3].toInt() and 255)
            p += 4; return v
        }
        fun mpint(): ByteArray {
            val n = int()
            if (n < 0 || p + n > b.size) throw Invalid("clé tronquée")
            return b.copyOfRange(p, p + n).also { p += n }
        }
        fun string() = String(mpint(), Charsets.US_ASCII)
    }
}
