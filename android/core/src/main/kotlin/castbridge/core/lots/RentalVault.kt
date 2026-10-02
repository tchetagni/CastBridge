package castbridge.core.lots

import castbridge.core.tv.AtomicFile
import java.io.File
import java.io.RandomAccessFile
import java.security.SecureRandom

/**
 * The TV's rental safe: `<dir>/keys/<product>_<period>.key` (the rental key, raw) and `<dir>/lots/<feature>_<scope>/…` (the encrypted lot files of rented lots).
 * It is the ONLY place the sweep deletes files from, and every path is checked to stay inside it ("préfixe du dossier de lots"): a name that is not a plain lot or key name is refused.
 * Destroying a key = overwrite with zeros, then with random bytes (each flushed to the device), then delete; on flash storage with wear levelling an old physical copy can survive
 * (limit stated in docs/RENTAL-LOTS.md), which is why the key is ALSO tombstoned in the ledger and never re-opened from the activation.
 */
class RentalVault(val dir: File, private val random: SecureRandom = SecureRandom()) {
    private val keysDir get() = File(dir, "keys")
    private val lotsDir get() = File(dir, "lots")
    private val NAME = Regex("^[a-z0-9][a-z0-9-]{0,63}_[0-9]{1,19}$")
    private val LOT_DIR = Regex("^[a-z0-9]{1,32}_[a-z0-9][a-z0-9-]{0,31}$")

    private fun keyFile(contractKey: String): File? {
        val n = contractKey.replace('@', '_'); if (!NAME.matches(n)) return null
        return File(keysDir, "$n.key").takeIf { inside(keysDir, it) }
    }

    private fun lotDir(lot: LotId): File? {
        if (!LotNames.valid(lot)) return null
        val n = "${lot.feature}_${lot.scope}"; if (!LOT_DIR.matches(n)) return null
        return File(lotsDir, n).takeIf { inside(lotsDir, it) }
    }

    private fun inside(root: File, f: File): Boolean {
        val r = root.canonicalFile; val c = f.canonicalFile
        return c.path.startsWith(r.path + File.separator) && c.parentFile != null
    }

    fun putKey(contractKey: String, key: ByteArray): Boolean {
        val f = keyFile(contractKey) ?: return false
        f.parentFile.mkdirs()
        return try { AtomicFile.write(f, key); true } catch (_: java.io.IOException) { false }
    }

    fun getKey(contractKey: String): ByteArray? = keyFile(contractKey)?.takeIf { it.isFile }?.readBytes()?.takeIf { it.size == 32 }
    fun hasKey(contractKey: String) = keyFile(contractKey)?.isFile == true

    /** Rewrites then deletes. True when no key file of that contract remains (also when there was none: the step is idempotent, a crash can replay it). */
    fun destroyKey(contractKey: String): Boolean {
        val f = keyFile(contractKey) ?: return false
        File(f.parentFile, f.name + ".tmp").takeIf { it.exists() }?.let { shred(it) }
        if (f.exists()) shred(f)
        return !f.exists()
    }

    private fun shred(f: File) {
        runCatching {
            RandomAccessFile(f, "rws").use { raf ->
                val n = raf.length().toInt().coerceAtLeast(32)
                raf.seek(0); raf.write(ByteArray(n)); raf.fd.sync()
                raf.seek(0); raf.write(ByteArray(n).also(random::nextBytes)); raf.fd.sync()
            }
        }
        f.delete()
    }

    // ---- encrypted lot files of rented lots ----
    fun lotFile(lot: LotId, version: Int): File? = lotDir(lot)?.let { File(it, "v$version.lot") }
    fun putLot(lot: LotId, version: Int, sealed: ByteArray): Boolean {
        val f = lotFile(lot, version) ?: return false
        return try { AtomicFile.write(f, sealed); true } catch (_: java.io.IOException) { false }
    }

    /** Decrypts a rented lot file with the contract's key: null when the key is gone, the file is missing, or it was altered or moved to another lot or version. */
    fun readLot(contractKey: String, lot: LotId, version: Int): ByteArray? {
        val blob = lotFile(lot, version)?.takeIf { it.isFile }?.readBytes() ?: return null
        return RentalKeys.open(getKey(contractKey), lot, version, blob)
    }

    /** Deletes the encrypted files of [lot] (and only them). True when none of them remains. */
    fun deleteLotFiles(lot: LotId): Boolean {
        val d = lotDir(lot) ?: return false
        val ours = Regex("^v[0-9]{1,9}\\.lot(\\.tmp)?$")
        d.listFiles()?.forEach { if (it.isFile && ours.matches(it.name)) it.delete() }
        // the folder goes only if it is now empty: a stray file of someone else is never swept away with it
        if (d.isDirectory && d.list().isNullOrEmpty()) d.delete()
        return d.listFiles()?.none { ours.matches(it.name) } ?: true
    }

    fun heldLotFolders(): List<String> = lotsDir.list()?.sorted() ?: emptyList()
}
