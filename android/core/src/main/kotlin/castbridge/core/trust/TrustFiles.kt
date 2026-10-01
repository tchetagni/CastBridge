package castbridge.core.trust

import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * The trust registry in a private file that survives a power cut or a crash in the middle of a write:
 * write `<file>.tmp` and fsync it, keep the previous good file as `<file>.bak`, then rename the new one into place.
 * [load] returns the main file; [loadBackup] the previous copy (the registry uses it when the checksum of the main file does not match).
 * The folder must be excluded from backups (Android Auto Backup rules), see docs/BT-PLUG-AND-PLAY.md.
 */
class FileTrustPersistence(private val file: File) : TrustPersistence {
    private val tmp = File(file.path + ".tmp")
    private val bak = File(file.path + ".bak")

    @Synchronized override fun load(): String? = read(file)
    @Synchronized override fun loadBackup(): String? = read(bak)

    @Synchronized override fun save(text: String) {
        file.absoluteFile.parentFile?.mkdirs()
        FileOutputStream(tmp).use { it.write(text.toByteArray(Charsets.UTF_8)); it.flush(); runCatching { it.fd.sync() } }
        if (file.exists()) {
            // the old file becomes the backup only if it is itself intact: a damaged main file must not replace a good backup
            val old = read(file)
            if (old != null && TrustRegistry.intact(old)) { bak.delete(); if (!file.renameTo(bak)) throw IOException("cannot keep a backup copy") }
        }
        if (!tmp.renameTo(file)) throw IOException("cannot replace the registry file")
    }

    private fun read(f: File): String? = try { if (f.isFile) f.readText(Charsets.UTF_8) else null } catch (e: IOException) { null }
}
