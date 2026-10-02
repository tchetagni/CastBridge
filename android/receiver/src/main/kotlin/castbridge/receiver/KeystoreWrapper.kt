package castbridge.receiver

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import castbridge.core.lots.InstallKeyPolicy
import android.util.Log
import castbridge.core.crypto.PlainWrapper
import castbridge.core.crypto.SecretWrapper
import java.security.KeyStore
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Wraps the installation key of CastBridge-TV under an AES-256-GCM key that lives in the `AndroidKeyStore` (alias [ALIAS], never exportable): a copy of `install.key` taken off the TV
 * is useless without the TV's secure hardware. Blob = iv(12) + ciphertext + tag. [unwrap] answers null ONLY when the key is lost for good (wrong tag, alias absent, key permanently
 * invalidated: [InstallKeyPolicy.unwrapIsLoss]); any other error is RETHROWN, so [castbridge.core.lots.InstallKeyStore] keeps the file and the caller retries later. Only an absent or
 * permanently invalidated alias is (re)created by [wrap].
 */
class KeystoreWrapper private constructor(private val alias: String) : SecretWrapper {
    override val label = "keystore"

    private fun store(): KeyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }

    private fun existing(): SecretKey? = store().getKey(alias, null) as? SecretKey

    /** Only called when the alias is absent, or after [KeyPermanentlyInvalidatedException] (which deletes the dead entry first): never as a reaction to a transient failure. */
    private fun generate(deleteFirst: Boolean = false): SecretKey {
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
        // A transient Keystore error (lookup or encryption) is RETHROWN, never answered by a new alias: that would destroy the key that protects install.key (audit w4-03).
        val present = try { store().containsAlias(alias) } catch (e: Exception) { null }
        val key = when (InstallKeyPolicy.forLookup(present)) {
            InstallKeyPolicy.Step.GENERATE -> generate()
            InstallKeyPolicy.Step.USE -> existing() ?: throw IllegalStateException("clé du coffre Android introuvable")
            else -> throw IllegalStateException("coffre Android indisponible")
        }
        return try { encrypt(key, plain) } catch (e: Exception) {
            if (InstallKeyPolicy.forEncryptFailure(e is KeyPermanentlyInvalidatedException) == InstallKeyPolicy.Step.RECREATE) encrypt(generate(deleteFirst = true), plain) else throw e
        }
    }

    @Synchronized override fun unwrap(blob: ByteArray): ByteArray? {
        if (blob.size < IV_BYTES + 16) return null                                          // truncated: not ours, for good
        val ks = store()                                                                    // a Keystore that does not load: rethrown (transient)
        if (!ks.containsAlias(alias)) return lost(InstallKeyPolicy.UnwrapFailure.MISSING_ALIAS, null)
        val key = ks.getKey(alias, null) as? SecretKey ?: throw IllegalStateException("clé du coffre Android momentanément illisible")
        return try {
            val c = Cipher.getInstance(TRANSFORM); c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, blob.copyOf(IV_BYTES)))
            c.doFinal(blob, IV_BYTES, blob.size - IV_BYTES)
        } catch (e: Exception) { lost(classify(e), e) }
    }

    /** Null for a lost key, otherwise the error is rethrown (KeyStoreException, UnrecoverableKeyException, ProviderException… are transient: retried later). */
    private fun lost(f: InstallKeyPolicy.UnwrapFailure, e: Exception?): ByteArray? = if (InstallKeyPolicy.unwrapIsLoss(f)) null else throw (e ?: IllegalStateException("coffre Android indisponible"))

    private fun classify(e: Throwable): InstallKeyPolicy.UnwrapFailure {
        var t: Throwable? = e; var depth = 0
        while (t != null && depth++ < 5) {
            if (t is KeyPermanentlyInvalidatedException) return InstallKeyPolicy.UnwrapFailure.INVALIDATED
            if (t is AEADBadTagException) return InstallKeyPolicy.UnwrapFailure.BAD_TAG
            t = t.cause
        }
        return InstallKeyPolicy.UnwrapFailure.OTHER
    }

    companion object {
        const val ALIAS = "castbridge-install-v1"
        /** The probe of [orPlain] uses its own alias: it can never touch the real one. */
        const val PROBE_ALIAS = "castbridge-probe"
        private const val PROVIDER = "AndroidKeyStore"
        private const val TRANSFORM = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val TAG = "KeystoreWrapper"

        /** The wrapper of the real alias WITHOUT probe: the reader of a key stored under `keystore` (its failures are thrown, never answered by a plain fallback). */
        fun install(): SecretWrapper = KeystoreWrapper(ALIAS)

        /**
         * The Keystore wrapper if it works HERE (one generation or reuse and one 32-byte round trip), otherwise a [PlainWrapper] and a warning: the install key is then stored without an
         * envelope, and the admin page says « non protégée ». Call it off the main thread when possible (the first generation can take a few hundred ms on a small TV).
         */
        fun orPlain(log: (String, Throwable?) -> Unit = { m, t -> Log.w(TAG, m, t) }): SecretWrapper = try {
            val probeWrapper = KeystoreWrapper(PROBE_ALIAS)
            val probe = ByteArray(32) { it.toByte() }
            check(probeWrapper.unwrap(probeWrapper.wrap(probe))?.contentEquals(probe) == true) { "aller-retour du coffre Android incohérent" }
            KeystoreWrapper(ALIAS)
        } catch (e: Exception) {
            log("coffre Android indisponible : une nouvelle clé d'installation serait stockée sans enveloppe (une clé existante est toujours lue avec sa propre enveloppe)", e)
            PlainWrapper()
        }
    }
}
