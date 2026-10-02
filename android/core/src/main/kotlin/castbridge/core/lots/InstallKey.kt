package castbridge.core.lots

import castbridge.core.crypto.PlainWrapper
import castbridge.core.crypto.SecretWrapper
import castbridge.core.owner.SafeFile
import castbridge.core.owner.X25519
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
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

/** The key file exists but cannot be read FOR NOW (its wrapper threw: Keystore unavailable). Nothing was regenerated, renamed or rewritten: the caller retries later. */
class InstallKeyUnavailableException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)

/**
 * `<dir>/install.key`, written by [SafeFile]:
 * ```
 * castbridge-install-key-v1
 * pub=<64 hex>
 * wrap=<label of the wrapper>
 * priv=<hex of the wrapped blob>
 * createdAt=<ms>
 * ```
 * A stored key is always READ with the wrapper its `wrap=` label names ([readers], [InstallKeyPolicy.readWith]); a key found under `plain` while [wrapper] is better is MIGRATED (same seed,
 * same public key) under [wrapper], the plain copy being kept until the new file reads back ([InstallKeyPolicy.migrateTo]).
 *
 * [loadOrCreate]: a wrapper that THROWS (transient failure, see [SecretWrapper.unwrap]) leaves every file untouched and throws [InstallKeyUnavailableException]. Only a key that is lost for good
 * (missing, truncated, foreign-wrapped, inconsistent, or `unwrap` = null) gives a NEW key and a [loadNote] for the journal (the rentals boxed for the old key then need a reissue, docs § 3). The
 * unreadable files are never overwritten: they are renamed `install.key.unreadable-<ms>` (and `.bak.unreadable-<ms>`) first, and the first [loadOrCreate] of every process tries them again:
 * one that reads now (a key renamed by an older build on a transient error, or set aside by [regenerateAssumingLost]) becomes the key again, the key generated meanwhile being kept aside
 * (`.superseded-<ms>`). If a new key cannot be written it is still returned (it works until the next restart) and the note says so.
 */
class InstallKeyStore(
    private val dir: File, private val wrapper: SecretWrapper, private val random: SecureRandom = SecureRandom(), private val now: () -> Long = System::currentTimeMillis,
    /** The wrapper able to read a blob stored under a label (null = foreign envelope, unreadable here). */
    private val readers: (String) -> SecretWrapper? = { label -> if (label == wrapper.label) wrapper else if (label == InstallKeyPolicy.PLAIN) PlainWrapper() else null },
) {
    private val file = File(dir, FILE)

    /** What happened at the last [loadOrCreate] when it was not a plain load (null otherwise): a French sentence for the journal. */
    @Volatile var loadNote: String? = null; private set

    @Volatile private var inUse: String? = null

    /** The label of the envelope of the key in use (`plain`, `keystore`, …), [wrapper]'s before any load: shown by the admin page, never a secret. */
    val protection: String get() = inUse ?: wrapper.label

    private var cached: InstallKey? = null

    private class Loaded(val key: InstallKey, val wrap: String, val text: String, val file: File)

    @Synchronized fun loadOrCreate(): InstallKey {
        cached?.let { return it }
        loadNote = null
        // main file first, then the .bak: a main file that reads but does not decrypt (or does not match its public key) falls back to the .bak
        var transient: Throwable? = null
        var current: Loaded? = null
        for ((f, fromBackup) in listOf(file to false, SafeFile.bak(file) to true)) {
            val l = try { readFile(f) } catch (e: Exception) { if (transient == null) transient = e; null } ?: continue
            if (fromBackup) loadNote = "clé d'installation relue depuis la copie de sécurité"
            current = l; break
        }
        if (current == null && transient != null)
            throw InstallKeyUnavailableException("clé d'installation momentanément illisible (coffre de clés indisponible) : conservée telle quelle, nouvel essai plus tard", transient)
        recoverable(current?.key)?.let { return restore(it, current) }
        if (current != null) {
            inUse = current.wrap; cached = current.key
            if (InstallKeyPolicy.migrateTo(current.wrap, wrapper.label)) migrate(current)
            return current.key
        }
        return regenerate(null)
    }

    /**
     * The Keystore has stayed unusable over several process starts ([InstallKeyPolicy.mustRegenerate]): the key is assumed lost, set aside (`.unreadable-<ms>`, tried again at every start)
     * and replaced. The note says « illisible » so the owner asks for the rentals' reissue.
     */
    @Synchronized fun regenerateAssumingLost(): InstallKey { cached = null; return regenerate(ASSUMED_NOTE) }

    private fun regenerate(note: String?): InstallKey {
        val existed = runCatching { file.exists() || SafeFile.bak(file).exists() }.getOrDefault(false)
        if (existed) { val stamp = now(); for (f in listOf(file, SafeFile.bak(file))) aside(f, "unreadable", stamp) }
        val fresh = InstallKey.generate(random)
        val saved = writeKey(fresh)
        loadNote = (note ?: if (existed) "clé d'installation illisible : nouvelle clé générée (les locations en cours devront être réémises)" else "nouvelle clé d'installation générée") +
            if (saved) "" else " ; écriture impossible, la clé ne survivra pas à un redémarrage"
        cached = fresh
        return fresh
    }

    /** Reads one key file with the wrapper ITS label names. Null = lost for good here; THROWS when the wrapper failed twice (transient). */
    private fun readFile(f: File): Loaded? {
        val text = runCatching { f.readText() }.getOrNull() ?: return null
        val p = parse(text) ?: return null
        val reader = readers(InstallKeyPolicy.readWith(p.wrap)) ?: return null
        val seed = try { reader.unwrap(p.blob) } catch (e: Exception) { reader.unwrap(p.blob) }      // one retry: the Keystore can fail once; a second failure is thrown
        val k = seed?.takeIf { it.size == 32 }?.let { InstallKey.fromSeed(it) }?.takeIf { it.pub.contentEquals(p.pub) } ?: return null
        return Loaded(k, p.wrap, text, f)
    }

    /** The oldest `.unreadable-*` file that reads NOW and holds another key than [current] (copies of [current] are removed). Wrapper failures are ignored here: tried again next start. */
    private fun recoverable(current: InstallKey?): Loaded? {
        val names = runCatching { dir.list()?.toList() }.getOrNull().orEmpty().filter { it.startsWith("$FILE.unreadable-") || it.startsWith("$FILE.bak.unreadable-") }
        for (n in names.sortedBy { it.substringAfter(".unreadable-").substringBefore('-').toLongOrNull() ?: Long.MAX_VALUE }.take(MAX_RECOVERY_TRIES)) {
            val l = runCatching { readFile(File(dir, n)) }.getOrNull() ?: continue
            if (current != null && l.key.pub.contentEquals(current.pub)) { runCatching { l.file.delete() }; continue }
            return l
        }
        return null
    }

    private fun restore(old: Loaded, current: Loaded?): InstallKey {
        val stamp = now()
        for (f in listOf(file, SafeFile.bak(file))) aside(f, if (current != null) "superseded" else "unreadable", stamp)
        val saved = writeKey(old.key)
        if (saved && runCatching { readFile(file)?.key?.pub?.contentEquals(old.key.pub) }.getOrNull() == true) {
            runCatching { old.file.delete() }
            recoverable(old.key)                                   // removes the other kept copies of the same key (e.g. its `.bak.unreadable-*`)
        }
        loadNote = "ancienne clé d'installation retrouvée et rétablie" +
            (if (current != null) " (la clé générée entre-temps est gardée à part : les locations émises pour elle devront être réémises)" else "") +
            if (saved) "" else " ; écriture impossible, nouvel essai au prochain démarrage"
        cached = old.key
        return old.key
    }

    /** Plain → better envelope, same seed. The plain file stays the main one until the new file reads back; then the plain `.bak` is replaced by a copy of the new file. */
    private fun migrate(l: Loaded) {
        val text = runCatching { render(l.key, wrapper) }.getOrNull() ?: return               // the wrapper fails now: the plain key stays, unchanged, migration at the next start
        if (runCatching { SafeFile.write(file, text) { parse(it) != null } }.isFailure) { putBack(l.text); return }
        val ok = runCatching { readFile(file)?.key?.pub?.contentEquals(l.key.pub) }.getOrNull() == true
        if (!ok) { putBack(l.text); return }
        runCatching { Files.copy(file.toPath(), SafeFile.bak(file).toPath(), StandardCopyOption.REPLACE_EXISTING) }.onFailure { runCatching { SafeFile.bak(file).delete() } }
        inUse = wrapper.label
        loadNote = (loadNote?.let { "$it ; " } ?: "") + "clé d'installation placée sous l'enveloppe « ${wrapper.label} » (même clé)"
    }

    /** Writes [k] under [wrapper], else (the wrapper fails) under the stated plain fallback, which a later start migrates. False if nothing could be written. */
    private fun writeKey(k: InstallKey): Boolean {
        for (w in listOfNotNull(wrapper, PlainWrapper().takeIf { wrapper.label != InstallKeyPolicy.PLAIN })) {
            val text = runCatching { render(k, w) }.getOrNull() ?: continue
            if (runCatching { SafeFile.write(file, text) { parse(it) != null } }.isSuccess) { inUse = w.label; return true }
        }
        return false
    }

    /** Atomically puts [text] back as the main file (a failed migration), without touching the `.bak`. */
    private fun putBack(text: String) = runCatching {
        val tmp = File(dir, "$FILE.tmp"); tmp.writeText(text)
        try { Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
        catch (e: java.io.IOException) { Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING) }
    }

    /** Renames [f] to `<name>.<kind>-<stamp>` (a free name: never over another kept file). */
    private fun aside(f: File, kind: String, stamp: Long) {
        if (!runCatching { f.exists() }.getOrDefault(false)) return
        var target = File(dir, "${f.name}.$kind-$stamp"); var i = 1
        while (target.exists()) target = File(dir, "${f.name}.$kind-$stamp-${i++}")
        runCatching { f.renameTo(target) }
    }

    private class Parsed(val pub: ByteArray, val wrap: String, val blob: ByteArray)

    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
    private fun unhex(s: String): ByteArray? = if (s.length % 2 == 0 && s.all { it in '0'..'9' || it in 'a'..'f' }) s.chunked(2).map { it.toInt(16).toByte() }.toByteArray() else null

    private fun render(k: InstallKey, w: SecretWrapper) = listOf(HEADER, "pub=${hex(k.pub)}", "wrap=${w.label}", "priv=${hex(w.wrap(k.priv))}", "createdAt=${now()}").joinToString("\n") + "\n"

    private fun parse(text: String): Parsed? {
        val lines = text.replace("\r", "").lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.firstOrNull() != HEADER) return null
        val kv = lines.drop(1).mapNotNull { l -> l.indexOf('=').takeIf { it > 0 }?.let { l.substring(0, it) to l.substring(it + 1) } }.toMap()
        val pub = kv["pub"]?.let(::unhex)?.takeIf { it.size == 32 } ?: return null
        val blob = kv["priv"]?.let(::unhex)?.takeIf { it.isNotEmpty() } ?: return null
        return Parsed(pub, kv["wrap"].orEmpty(), blob)
    }

    companion object {
        const val FILE = "install.key"; private const val HEADER = "castbridge-install-key-v1"
        private const val MAX_RECOVERY_TRIES = 10
        const val ASSUMED_NOTE = "clé d'installation illisible (coffre de clés indisponible depuis plusieurs démarrages) : nouvelle clé générée (les locations en cours devront être réémises)"

        /** The `wrap=` label of the stored key (main file, then `.bak`), or null if there is no readable key file. Diagnostics only: the store reads every file with its own label. */
        fun storedWrap(dir: File): String? = listOf(File(dir, FILE), SafeFile.bak(File(dir, FILE))).firstNotNullOfOrNull { f ->
            runCatching { f.readText() }.getOrNull()?.replace("\r", "")?.lines()?.map { it.trim() }
                ?.takeIf { it.firstOrNull() == HEADER }?.firstNotNullOfOrNull { l -> l.takeIf { it.startsWith("wrap=") }?.substring(5)?.takeIf { it.isNotEmpty() } }
        }
    }
}
