package castbridge.core.owner

import castbridge.core.crypto.SecretWrapper
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * The SIGNING key of one CastBridge-TV installation (Ed25519), distinct from the X25519 key of the rental box (w4-01): one key, one job. It signs the TV proofs ([TvProof]) and nothing
 * else. [keyId] = [KeyRing.idOf] of the public key. The seed is never exposed: the object only signs; the store keeps the seed sealed by a [SecretWrapper].
 */
class InstallSigner(seed: ByteArray) {
    private val key: Ed25519Signer = Ed25519Signer(seed.also { require(it.size == 32) { "graine de 32 octets attendue" } })

    val keyId: String get() = key.keyId
    val publicKeyBase64: String get() = key.publicKeyBase64

    /** Signature of [text] (UTF-8), base64 with padding. */
    fun sign(text: String): String = Base64.getEncoder().encodeToString(key.sign(text.toByteArray(Charsets.UTF_8)))

    /** Readable fingerprint to compare on both screens (see [fingerprintOf]). */
    fun fingerprintText(): String = fingerprintOf(publicKeyBase64)

    /** What [loadOrCreate] found: [regenerated] = a stored seed existed but could not be opened (lost wrapper key), so the installation changed identity and phones must re-confirm it. */
    class Loaded(val signer: InstallSigner, val regenerated: Boolean)

    companion object {
        /** 8 groups of 4 lowercase hex characters, `xxxx-xxxx-…`: the first 16 bytes of SHA-256 of the raw public key. */
        fun fingerprintOf(publicKeyBase64: String): String =
            MessageDigest.getInstance("SHA-256").digest(Base64.getDecoder().decode(publicKeyBase64.trim())).take(16).joinToString("") { "%02x".format(it) }.chunked(4).joinToString("-")

        /** A fresh key from [random] (nothing is stored). */
        fun create(random: SecureRandom = SecureRandom()): InstallSigner { val s = ByteArray(32).also(random::nextBytes); try { return InstallSigner(s) } finally { s.fill(0) } }

        /**
         * The installation key kept in [store] under [wrapper] (REQUIRED: the receiver passes its Keystore wrapper, or states [castbridge.core.crypto.PlainWrapper] as the file fallback; there is no silent
         * default), created and stored on first use. Same rule as the rental install key (InstallKeyPolicy): an unwrap that fails is retried once (a transient Keystore hiccup must not change the
         * identity); only a blob that STILL does not open (wrong or lost key, altered, not 32 bytes) counts as lost, and an exception from [wrapper] is rethrown, nothing is replaced. Before a new identity is
         * written the old blob is kept aside by [InstallSignerStore.keepUnreadable] (a failure there throws: the old blob is never overwritten unsaved). The new state is written BEFORE the signer is
         * returned, so a failed write throws and no unsaved identity is ever used.
         */
        fun loadOrCreate(store: InstallSignerStore, wrapper: SecretWrapper, random: SecureRandom = SecureRandom(), nowMs: () -> Long = System::currentTimeMillis): Loaded {
            val blob = store.load()
            if (blob != null) repeat(2) { wrapper.unwrap(blob)?.takeIf { it.size == 32 }?.let { seed -> try { return Loaded(InstallSigner(seed), false) } finally { seed.fill(0) } } }
            if (blob != null) store.keepUnreadable(blob, nowMs())
            val seed = ByteArray(32).also(random::nextBytes)
            try { store.save(wrapper.wrap(seed)); return Loaded(InstallSigner(seed), blob != null) } finally { seed.fill(0) }
        }
    }
}

/** Where the sealed seed lives: an opaque blob (the app's private folder on Android). */
interface InstallSignerStore {
    fun load(): ByteArray?
    /** Persists [blob]; throws when the write fails. */
    fun save(blob: ByteArray)
    /** Keeps [blob] (a stored seed nobody could open) aside, as `.unreadable-<ms>`, before it is replaced; throws when that fails. */
    fun keepUnreadable(blob: ByteArray, nowMs: Long)
}

/** [InstallSignerStore] in one file (base64 text), written atomically with [SafeFile] (the stated fallback when the receiver has nothing better). */
class FileInstallSignerStore(private val file: File) : InstallSignerStore {
    private fun valid(text: String) = runCatching { Base64.getDecoder().decode(text.trim()).isNotEmpty() }.getOrDefault(false)
    override fun load(): ByteArray? = SafeFile.read(file, ::valid)?.let { Base64.getDecoder().decode(it.text.trim()) }
    override fun save(blob: ByteArray) = SafeFile.write(file, Base64.getEncoder().encodeToString(blob), ::valid)
    override fun keepUnreadable(blob: ByteArray, nowMs: Long) { file.parentFile?.mkdirs(); java.io.FileOutputStream(File(file.parentFile, file.name + ".unreadable-$nowMs")).use { it.write(Base64.getEncoder().encode(blob)); it.flush(); it.fd.sync() } }
}
