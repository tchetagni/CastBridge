package castbridge.receiver

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import castbridge.core.crypto.SecretWrapper
import castbridge.core.lots.InstallKeyPolicy
import castbridge.core.lots.InstallKeyPolicy.UnwrapFailure
import castbridge.core.lots.InstallKeyPolicy.Verdict
import java.security.KeyStore
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Wraps the installation key of CastBridge-TV under an AES-256-GCM key that lives in the `AndroidKeyStore` (alias [ALIAS], never exportable): a copy of `install.key` taken off the TV
 * is useless without the TV's secure hardware. Blob = iv(12) + ciphertext + tag.
 *
 * Two instances share the alias: [install] READS (and `wrap` THROWS rather than create anything: an existing key file is never touched by a Keystore reaction); [creator] is used only for the
 * FIRST creation of the key and for an explicit owner reset, and is the only one that may generate the alias (never before it was confirmed absent on a freshly reloaded KeyStore: on
 * Android 6-9 `containsAlias` can answer false during a keystore daemon restart or while the keystore is locked).
 * [unwrap] answers null ONLY for a lost key ([InstallKeyPolicy.classify]: wrong tag, permanently invalidated key, alias confirmed absent); any other error is rethrown (transient).
 */
class KeystoreWrapper private constructor(private val alias: String, private val mayCreate: Boolean) : SecretWrapper {
    override val label = "keystore"

    private fun store(): KeyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }

    /** Absent only if `containsAlias` is false, AND a freshly reloaded KeyStore says false again and returns no key (a lookup that throws is not a confirmation: it is rethrown by the caller). */
    private fun confirmedAbsent(ks: KeyStore): Boolean {
        return InstallKeyPolicy.confirmAbsent(ks.containsAlias(alias)) { val fresh = store(); fresh.containsAlias(alias) to (fresh.getKey(alias, null) != null) }    // ~1.5 s pause between the two checks
    }

    private fun existing(ks: KeyStore): SecretKey = ks.getKey(alias, null) as? SecretKey ?: throw IllegalStateException("clé du coffre Android momentanément illisible")

    /** Creator only, after the alias was confirmed absent or [KeyPermanentlyInvalidatedException] (the dead entry is then deleted first). */
    private fun generate(deleteFirst: Boolean = false): SecretKey {
        check(mayCreate) { "création de clé du coffre non permise ici" }
        if (deleteFirst) store().deleteEntry(alias)
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).setRandomizedEncryptionRequired(true).build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER).apply { init(spec) }.generateKey()
    }

    private fun encrypt(key: SecretKey, plain: ByteArray): ByteArray {
        val c = Cipher.getInstance(TRANSFORM); c.init(Cipher.ENCRYPT_MODE, key)            // the provider picks a fresh random IV (randomized encryption required)
        val iv = c.iv; check(iv.size == IV_BYTES) { "IV inattendu" }
        return iv + c.doFinal(plain)
    }

    @Synchronized override fun wrap(plain: ByteArray): ByteArray {
        val ks = store()
        val key = if (ks.containsAlias(alias)) existing(ks) else if (mayCreate && confirmedAbsent(ks)) generate() else throw IllegalStateException("clé du coffre Android introuvable")
        return try { encrypt(key, plain) } catch (e: Exception) {
            if (mayCreate && classify(e) == UnwrapFailure.INVALIDATED) encrypt(generate(deleteFirst = true), plain) else throw e
        }
    }

    @Synchronized override fun unwrap(blob: ByteArray): ByteArray? {
        if (blob.size < IV_BYTES + 16) return null                                          // truncated: not ours, for good
        val ks = store()                                                                    // a Keystore that does not load: rethrown (transient)
        if (!ks.containsAlias(alias)) {
            if (InstallKeyPolicy.classify(UnwrapFailure.MISSING_ALIAS, confirmedAbsent(ks)) == Verdict.LOST) return null
            throw IllegalStateException("clé du coffre Android momentanément introuvable")
        }
        val key = existing(ks)
        return try {
            val c = Cipher.getInstance(TRANSFORM); c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, blob.copyOf(IV_BYTES)))
            c.doFinal(blob, IV_BYTES, blob.size - IV_BYTES)
        } catch (e: Exception) { if (InstallKeyPolicy.classify(classify(e)) == Verdict.LOST) null else throw e }
    }

    private fun classify(e: Throwable): UnwrapFailure {
        var t: Throwable? = e; var depth = 0
        while (t != null && depth++ < 5) {
            if (t is KeyPermanentlyInvalidatedException) return UnwrapFailure.INVALIDATED
            if (t is AEADBadTagException) return UnwrapFailure.BAD_TAG
            t = t.cause
        }
        return UnwrapFailure.OTHER
    }

    companion object {
        const val ALIAS = "castbridge-install-v1"
        private const val PROVIDER = "AndroidKeyStore"
        private const val TRANSFORM = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12

        /** The reader of a key stored under `keystore`: never creates, never deletes anything. */
        fun install(): SecretWrapper = KeystoreWrapper(ALIAS, mayCreate = false)

        /** The wrapper of a NEW key (first creation or owner reset). Touches the Keystore only when used: call it off the main thread (the first generation can take a few hundred ms). */
        fun creator(): SecretWrapper = KeystoreWrapper(ALIAS, mayCreate = true)
    }
}
