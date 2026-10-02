package castbridge.core.owner

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Single-file state written so that a power cut or a full disk never loses the last good copy: the text goes to a temporary file in the SAME folder, is fsync'ed, the previous
 * good file is kept as `<name>.bak`, then the temporary file is renamed over the main one (atomic). [read] falls back to the `.bak` when the main file is missing, truncated or
 * refused by [valid]. A failed write THROWS (the caller keeps its in-memory state, logs and retries): nothing is swallowed here.
 */
object SafeFile {
    fun bak(f: File) = File(f.parentFile, f.name + ".bak")

    fun write(f: File, text: String, valid: (String) -> Boolean = { true }) {
        f.parentFile?.mkdirs()
        val tmp = File(f.parentFile, f.name + ".tmp")
        FileOutputStream(tmp).use { it.write(text.toByteArray(Charsets.UTF_8)); it.flush(); it.fd.sync() }
        // keep the last GOOD file (never overwrite a good .bak with a corrupt main)
        if (f.isFile) runCatching { if (valid(f.readText())) Files.copy(f.toPath(), bak(f).toPath(), StandardCopyOption.REPLACE_EXISTING) }
        try { Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
        catch (e: java.io.IOException) { Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING) }
    }

    /** The text of the main file if [valid], else of the `.bak` if [valid], else null. [fromBackup] says which one answered. */
    class Read(val text: String, val fromBackup: Boolean)
    fun read(f: File, valid: (String) -> Boolean = { true }): Read? {
        runCatching { f.readText() }.getOrNull()?.takeIf { runCatching { valid(it) }.getOrDefault(false) }?.let { return Read(it, false) }
        runCatching { bak(f).readText() }.getOrNull()?.takeIf { runCatching { valid(it) }.getOrDefault(false) }?.let { return Read(it, true) }
        return null
    }
}
