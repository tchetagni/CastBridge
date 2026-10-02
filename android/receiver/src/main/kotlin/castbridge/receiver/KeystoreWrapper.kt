package castbridge.receiver

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import castbridge.core.crypto.PlainWrapper
import castbridge.core.crypto.SecretWrapper
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Wraps the installation key of CastBridge-TV under an AES-256-GCM key that lives in the `AndroidKeyStore` (alias [ALIAS], never exportable): a copy of `install.key` taken off the TV
 * is useless without the TV's secure hardware. Blob = iv(12) + ciphertext + tag. [unwrap] never throws (null = not ours / key gone), so [castbridge.core.lots.InstallKeyStore] regenerates
 * a key rather than staying stuck; a failing or invalidated alias is recreated by [wrap].
 */
class KeystoreWrapper private constructor() : SecretWrapper {
    override val label = "keystore"

    private fun store(): KeyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }

    private fun existing(): SecretKey? = store().getKey(ALIAS, null) as? SecretKey

    private fun generate(): SecretKey {
        runCatching { store().deleteEntry(ALIAS) }
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
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
        val key = runCatching { existing() }.getOrNull() ?: generate()
        return try { encrypt(key, plain) } catch (e: Exception) { encrypt(generate(), plain) }      // an invalidated key: one new alias, then the failure is the caller's
    }

    @Synchronized override fun unwrap(blob: ByteArray): ByteArray? {
        if (blob.size < IV_BYTES + 16) return null
        return try {
            val key = existing() ?: return null
            val c = Cipher.getInstance(TRANSFORM); c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, blob.copyOf(IV_BYTES)))
            c.doFinal(blob, IV_BYTES, blob.size - IV_BYTES)
        } catch (e: Exception) { null }        // AEADBadTagException (wrong or recreated key, altered blob), KeyStoreException, UnrecoverableKeyException, ProviderException…
    }

    companion object {
        const val ALIAS = "castbridge-install-v1"
        private const val PROVIDER = "AndroidKeyStore"
        private const val TRANSFORM = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val TAG = "KeystoreWrapper"

        /**
         * The Keystore wrapper if it works HERE (one generation or reuse and one 32-byte round trip), otherwise a [PlainWrapper] and a warning: the install key is then stored without an
         * envelope, and the admin page says « non protégée ». Call it off the main thread when possible (the first generation can take a few hundred ms on a small TV).
         */
        fun orPlain(log: (String, Throwable?) -> Unit = { m, t -> Log.w(TAG, m, t) }): SecretWrapper = try {
            val w = KeystoreWrapper()
            val probe = ByteArray(32) { it.toByte() }
            check(w.unwrap(w.wrap(probe))?.contentEquals(probe) == true) { "aller-retour du coffre Android incohérent" }
            w
        } catch (e: Exception) {
            log("coffre Android indisponible : clé d'installation stockée sans enveloppe", e)
            PlainWrapper()
        }
    }
}
