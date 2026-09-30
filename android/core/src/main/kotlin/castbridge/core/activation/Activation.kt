package castbridge.core.activation

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Offline activation of the TV (no network needed): the app derives a short "machine code" from a stable device
 * fingerprint (the MAC address when readable, else ANDROID_ID), the seller computes an activation token from that
 * code with a shared secret, and the app verifies it. Pure logic, testable.
 *
 * Security note: the token is HMAC-SHA256(keyed by [secret], which must also live in the seller's tool). The secret
 * embedded in the APK can be extracted by a determined attacker; if that matters, switch to an Ed25519 signature
 * (public key in the app, private key held by the seller only) — the math below is the simple, offline scheme asked for.
 */
object Activation {
    /** Crockford base32: 32 symbols, no look-alike characters (I, L, O, U) so codes are easy to read and type. */
    const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

    /** The Caesar shift applied to the code the TV shows (see [caesar]); the seller reverses it. */
    const val CAESAR_SHIFT = 3

    /**
     * Caesar substitution over [ALPHABET]: each symbol is shifted by [shift] positions (mod 32); separators stay.
     * Positive [shift] encodes, negative decodes. The TV shows [caesar] of the machine code so the raw code is not
     * trivially readable on screen.
     */
    fun caesar(s: String, shift: Int): String = buildString {
        for (c in s.uppercase()) {
            val i = ALPHABET.indexOf(c)
            if (i < 0) append(c) else append(ALPHABET[(i + shift + ALPHABET.length) % ALPHABET.length])
        }
    }

    /** Keeps letters and digits, uppercased; separators are ignored. */
    fun normalize(fingerprint: String): String = fingerprint.filter { it.isLetterOrDigit() }.uppercase()

    /** "Machine code" shown on the TV: first 8 base32 chars of SHA-256(fingerprint), grouped XXXX-XXXX. */
    fun requestCode(fingerprint: String): String {
        val h = MessageDigest.getInstance("SHA-256").digest(normalize(fingerprint).toByteArray(Charsets.UTF_8))
        return base32(h, 8).chunked(4).joinToString("-")
    }

    /** Activation token: HMAC-SHA256(requestCode, secret), 12 base32 chars, grouped XXXX-XXXX-XXXX. */
    fun token(requestCode: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val h = mac.doFinal(normalize(requestCode).toByteArray(Charsets.UTF_8))
        return base32(h, 12).chunked(4).joinToString("-")
    }

    /** Constant-time comparison so a wrong token is not guessable by timing. */
    fun verify(requestCode: String, token: String, secret: String): Boolean =
        MessageDigest.isEqual(normalize(token(requestCode, secret)).toByteArray(Charsets.UTF_8), normalize(token).toByteArray(Charsets.UTF_8))

    private fun base32(bytes: ByteArray, chars: Int): String {
        val sb = StringBuilder()
        var acc = 0
        var bits = 0
        for (b in bytes) {
            acc = (acc shl 8) or (b.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) { bits -= 5; sb.append(ALPHABET[(acc shr bits) and 0x1F]) }
        }
        if (bits > 0) sb.append(ALPHABET[(acc shl (5 - bits)) and 0x1F])
        return sb.toString().take(chars)
    }
}
