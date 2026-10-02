package castbridge.core.lots

import castbridge.core.crypto.SecretWrapper
import castbridge.core.owner.SafeFile
import castbridge.core.owner.X25519
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * The X25519 key pair of ONE installation of CastBridge-TV (docs/coordination/DESIGN-W4-ENVELOPPE-LOCATIONS.md § 3). The private part never leaves the TV and never enters a token;
 * the public part travels in the device request (`install=x25519|<hex>`) and rental keys are boxed for it. [priv] is the 32-byte seed (the clamp is applied by [X25519]).
 */
class InstallKey(val priv: ByteArray, val pub: ByteArray) {
    init { require(priv.size == 32 && pub.size == 32) }

    /** 16 hex = the first 8 bytes of SHA-256(pub): for logs, no personal data. */
    val installId: String get() = MessageDigest.getInstance("SHA-256").digest(pub).take(8).joinToString("") { "%02x".format(it) }

    companion object {
        fun fromSeed(seed: ByteArray) = InstallKey(seed.copyOf(), X25519.publicKey(seed))
        fun generate(random: SecureRandom = SecureRandom()) = fromSeed(ByteArray(32).also(random::nextBytes))
    }
}

/**
 * `<dir>/install.key`, written by [SafeFile]:
 * ```
 * castbridge-install-key-v1
 * pub=<64 hex>
 * wrap=<label of the wrapper>
 * priv=<hex of the wrapped blob>
 * createdAt=<ms>
 * ```
 * [loadOrCreate] never throws: a missing, truncated, foreign-wrapped or inconsistent file gives a NEW key and a [loadNote] for the journal (the rentals boxed for the old key then need a
 * reissue, docs § 3). If the new key cannot be written it is still returned (it works until the next restart) and the note says so.
 */
class InstallKeyStore(private val dir: File, private val wrapper: SecretWrapper, private val random: SecureRandom = SecureRandom(), private val now: () -> Long = System::currentTimeMillis) {
    private val file = File(dir, FILE)

    /** What happened at the last [loadOrCreate] when it was not a plain load (null otherwise): a French sentence for the journal. */
    @Volatile var loadNote: String? = null; private set

    /** The wrapper's label (`plain`, `keystore`, …): shown by the admin page, never a secret. */
    val protection: String get() = wrapper.label

    private var cached: InstallKey? = null

    @Synchronized fun loadOrCreate(): InstallKey {
        cached?.let { return it }
        loadNote = null
        val read = runCatching { SafeFile.read(file) { parse(it) != null } }.getOrNull()
        val loaded = read?.let { r ->
            parse(r.text)?.let { p -> runCatching { wrapper.unwrap(p.blob) }.getOrNull()?.takeIf { it.size == 32 }?.let { InstallKey.fromSeed(it) }?.takeIf { k -> k.pub.contentEquals(p.pub) } }
        }
        if (loaded != null) {
            if (read.fromBackup) loadNote = "clé d'installation relue depuis la copie de sécurité"
            cached = loaded; return loaded
        }
        val existed = runCatching { file.exists() }.getOrDefault(false)
        val fresh = InstallKey.generate(random)
        val saved = runCatching { SafeFile.write(file, render(fresh)) { parse(it) != null } }.isSuccess
        loadNote = (if (existed) "clé d'installation illisible : nouvelle clé générée (les locations en cours devront être réémises)" else "nouvelle clé d'installation générée") +
            if (saved) "" else " ; écriture impossible, la clé ne survivra pas à un redémarrage"
        cached = fresh
        return fresh
    }

    private class Parsed(val pub: ByteArray, val blob: ByteArray)

    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
    private fun unhex(s: String): ByteArray? = if (s.length % 2 == 0 && s.all { it in '0'..'9' || it in 'a'..'f' }) s.chunked(2).map { it.toInt(16).toByte() }.toByteArray() else null

    private fun render(k: InstallKey) = listOf(HEADER, "pub=${hex(k.pub)}", "wrap=${wrapper.label}", "priv=${hex(wrapper.wrap(k.priv))}", "createdAt=${now()}").joinToString("\n") + "\n"

    private fun parse(text: String): Parsed? {
        val lines = text.replace("\r", "").lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.firstOrNull() != HEADER) return null
        val kv = lines.drop(1).mapNotNull { l -> l.indexOf('=').takeIf { it > 0 }?.let { l.substring(0, it) to l.substring(it + 1) } }.toMap()
        val pub = kv["pub"]?.let(::unhex)?.takeIf { it.size == 32 } ?: return null
        val blob = kv["priv"]?.let(::unhex)?.takeIf { it.isNotEmpty() } ?: return null
        return Parsed(pub, blob)
    }

    companion object { const val FILE = "install.key"; private const val HEADER = "castbridge-install-key-v1" }
}
