package castbridge.core.free

import castbridge.core.lots.LotFamilies
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Writes the archive in a folder: free-space check first, `.part` file, rename at the end; a failure or a cancellation never leaves a partial file.
 * No network, no activation, no PIN.
 */
object FreeExportFiles {
    fun fileName(date: String) = "castbridge-contenus-libres-$date.zip"

    class Done(val file: File, val result: FreeExportResult) {
        /** The French message shown at the end. */
        fun message(): String = buildString {
            append("Archive écrite : ${file.path}\nTaille : ${size(file.length())}\n")
            append("${result.exported.size} contenu${if (result.exported.size > 1) "s" else ""} libre${if (result.exported.size > 1) "s" else ""} exporté${if (result.exported.size > 1) "s" else ""}.\n")
            result.skippedLine().takeIf { it.isNotEmpty() }?.let { append(it).append(".\n") }
            append("Copiez ce fichier sur une clé USB ou envoyez-le à votre téléphone.")
        }
    }

    fun size(b: Long): String = when { b >= 1_048_576 -> "%.1f Mo".format(java.util.Locale.FRANCE, b / 1_048_576.0); b >= 1024 -> "${b / 1024} Ko"; else -> "$b octets" }

    /** @throws FreeExportException (French message) */
    fun export(
        source: FreeContentSource, dir: File, date: String, families: LotFamilies = LotFamilies { null },
        progress: (Long, Long) -> Unit = { _, _ -> }, cancelled: () -> Boolean = { false },
    ): Done {
        val need = FreeContentExporter.contentBytes(source, families)
        if (!(dir.isDirectory || dir.mkdirs())) throw FreeExportException("Impossible de créer le dossier ${dir.path}.")
        val free = dir.usableSpace
        if (free in 1 until need) throw FreeExportException("Espace insuffisant dans ${dir.path} : ${size(need)} nécessaires, ${size(free)} libres.")
        val target = File(dir, fileName(date)); val part = File(dir, target.name + ".part")
        try {
            val result = FileOutputStream(part).use { fos ->
                val r = FreeContentExporter.export(source, fos.buffered(64 * 1024), families, date = date, progress = progress, cancelled = cancelled)
                fos.flush(); runCatching { fos.fd.sync() }
                r
            }
            if (target.exists() && !target.delete()) throw IOException("fichier existant non remplaçable")
            if (!part.renameTo(target)) throw IOException("renommage impossible")
            return Done(target, result)
        } catch (e: FreeExportException) {
            part.delete(); throw e
        } catch (e: IOException) {
            part.delete()
            val full = e.message?.contains("space", true) == true || e.message?.contains("ENOSPC") == true
            throw FreeExportException(if (full) "Espace insuffisant dans ${dir.path}." else "Écriture impossible dans ${dir.path} : ${e.message ?: "erreur"}.", e)
        } catch (e: Throwable) {
            part.delete(); throw e
        }
    }
}
