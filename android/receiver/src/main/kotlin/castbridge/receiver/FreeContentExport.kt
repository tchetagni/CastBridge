package castbridge.receiver

import android.content.Context
import android.os.Environment
import castbridge.core.free.EmbeddedFreeSource
import castbridge.core.free.FreeExportException
import castbridge.core.free.FreeExportFiles
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * « Télécharger tous les contenus libres » (docs/FREE-CONTENT.md): writes the ZIP of the embedded CC BY-SA contents into Download/CastBridge/ of the USB drive when
 * one is plugged, else of the internal storage. Needs no activation, no server, no PIN, no HTTP route: it works on a locked TV and offline.
 * One export at a time, on its own background thread; results are posted on the main thread.
 */
object FreeContentExport {
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "cb-free-export").apply { isDaemon = true } }
    private val running = java.util.concurrent.atomic.AtomicBoolean(false)
    @Volatile private var cancel = false

    sealed class Outcome {
        class Success(val message: String) : Outcome()
        class Failure(val message: String) : Outcome()
    }

    fun busy(): Boolean = running.get()
    fun cancel() { cancel = true }

    /** Folders to try, best first: USB drive (removable volume) then internal. Always « Download/CastBridge ». */
    fun targetDirs(ctx: Context): List<File> {
        val dirs = LinkedHashSet<File>()
        val ext = ctx.getExternalFilesDirs(null).filterNotNull()
        ext.forEach { d ->
            val removable = runCatching { Environment.isExternalStorageRemovable(d) }.getOrDefault(false)
            val root = d.path.substringBefore("/Android/", "")
            if (removable && root.isNotEmpty()) dirs += File(root, "Download/CastBridge")
        }
        runCatching { File("/storage").listFiles()?.filter { it.name != "emulated" && it.name != "self" && it.isDirectory && it.canWrite() }?.forEach { dirs += File(it, "Download/CastBridge") } }
        dirs += File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "CastBridge")
        ext.firstOrNull()?.let { dirs += File(it, "Download/CastBridge") }
        return dirs.toList()
    }

    /** @param onProgress percent 0..100 (main thread); @param onDone the final outcome (main thread). */
    fun start(ctx: Context, post: (() -> Unit) -> Unit, onProgress: (Int) -> Unit, onDone: (Outcome) -> Unit) {
        if (!running.compareAndSet(false, true)) return
        cancel = false
        val app = ctx.applicationContext
        io.execute {
            val outcome = try { run(app, post, onProgress) } catch (e: Throwable) { Outcome.Failure("Export impossible : ${e.message ?: e.javaClass.simpleName}.") }
            running.set(false)
            post { onDone(outcome) }
        }
    }

    private fun run(app: Context, post: (() -> Unit) -> Unit, onProgress: (Int) -> Unit): Outcome {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
        val source = EmbeddedFreeSource()
        var last = -1
        var lastError: String? = null
        for (dir in targetDirs(app)) {
            try {
                val done = FreeExportFiles.export(source, dir, date, source.families(), progress = { d, t ->
                    val pct = if (t <= 0) 100 else (d * 100 / t).toInt()
                    if (pct != last) { last = pct; post { onProgress(pct) } }
                }, cancelled = { cancel })
                return Outcome.Success(done.message())
            } catch (e: FreeExportException) {
                if (e.message == "Export annulé." || e.javaClass.simpleName == "FreeExportEmpty") return Outcome.Failure(e.message ?: "Export impossible.")
                lastError = e.message                                             // no space / not writable here: try the next folder
            }
        }
        return Outcome.Failure(lastError ?: "Aucun dossier d'écriture disponible.")
    }
}
