package castbridge.core.crypto

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Wraps a small secret (a private key) before it is written to a file. The core knows no Android: the receiver supplies a Keystore-backed implementation, the core brings [MemoryWrapper]
 * (tests) and [PlainWrapper] (the stated fallback when no better wrapper exists).
 */
interface SecretWrapper {
    /** The blob to store for [plain]. */
    fun wrap(plain: ByteArray): ByteArray

    /** The secret, or null if [blob] is not one of ours (wrong key, altered, truncated). Never throws. */
    fun unwrap(blob: ByteArray): ByteArray?

    /** Short name stored next to the blob and shown on the admin page (`plain`, `keystore`, `memory`). */
    val label: String
}

/** No protection: identity. Nothing here restricts the file's permissions: privacy relies on the caller's folder (the app's private directory on Android); the state is visible through [label] = `plain`. */
class PlainWrapper : SecretWrapper {
    override fun wrap(plain: ByteArray) = plain.copyOf()
    override fun unwrap(blob: ByteArray): ByteArray? = blob.copyOf()
    override val label = "plain"
}

/** AES-256-GCM under a key held in memory (tests, and a model of the Keystore wrapper). Blob = iv(12) + ciphertext + tag. */
class MemoryWrapper(key: ByteArray, private val random: SecureRandom = SecureRandom()) : SecretWrapper {
    private val key = SecretKeySpec(key.also { require(it.size == 32) { "clé de 32 octets attendue" } }, "AES")

    override fun wrap(plain: ByteArray): ByteArray {
        val iv = ByteArray(12).also(random::nextBytes)
        val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        return iv + c.doFinal(plain)
    }

    override fun unwrap(blob: ByteArray): ByteArray? {
        if (blob.size < 12 + 16) return null
        return runCatching {
            val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, blob.copyOf(12)))
            c.doFinal(blob, 12, blob.size - 12)
        }.getOrNull()
    }

    override val label = "memory"
}
