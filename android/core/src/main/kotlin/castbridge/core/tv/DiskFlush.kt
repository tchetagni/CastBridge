package castbridge.core.tv

import java.io.File
import java.io.FileInputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Best-effort flush of EVERYTHING the system still holds in memory (the `sync` command): the data of every file, and also the directory entries and the file-allocation tables that a per-file
 * `fsync` leaves to the kernel's own pace (a rename just before a pulled key is the classic loss). Needs no permission and no `su`: it is the `sync` binary of Android, which any app may run.
 * Not available (an unusual system) means false, never an exception.
 *
 * Where it is used (docs/STORAGE.md § « Clé USB mal éjectée »): once at the END of a file written to a removable volume ([FileStore.commit]: [soon], coalesced and spaced, never per block: R-20 removed
 * the fsync per block on purpose), and by « Préparer le retrait de la clé USB » ([now], waited for).
 */
interface SystemSync {
    /** Flushes now and waits up to [timeoutMs]. False = the command is missing, failed or did not end in time. */
    fun now(timeoutMs: Long = DEFAULT_TIMEOUT_MS): Boolean

    /** Asks for a flush soon, off the caller's thread; requests close together are served by ONE flush. Never blocks. */
    fun soon()

    companion object {
        const val DEFAULT_TIMEOUT_MS = 15_000L
        /** Does nothing (tests, stores without a removable medium). */
        val NONE: SystemSync = object : SystemSync {
            override fun now(timeoutMs: Long) = false
            override fun soon() {}
        }
    }
}

/**
 * [SystemSync] by the `sync` command. [soon] is coalesced (one worker, however many requests) and spaced ([minGapMs] since the END of the last flush): a burst of small files costs one flush, and a
 * video playing from the same bus is not hit by a flush per file. A request made WHILE a flush runs is served by another one (what was written meanwhile may not be covered). The side effects
 * ([exec], the clock, sleeping, the thread) are injected for the tests.
 */
class ShellSync(
    private val exec: (Long) -> Boolean = { runSync(it) },
    private val minGapMs: Long = 3_000L,
    private val clock: () -> Long = System::currentTimeMillis,
    private val sleep: (Long) -> Unit = { Thread.sleep(it) },
    private val spawn: (Runnable) -> Unit = { r -> Thread(r, "cb-sync").apply { isDaemon = true }.start() },
) : SystemSync {
    private val pending = AtomicBoolean(false)
    private val running = AtomicBoolean(false)
    @Volatile private var lastEnd = Long.MIN_VALUE / 2

    override fun now(timeoutMs: Long): Boolean {
        val ok = runCatching { exec(timeoutMs) }.getOrDefault(false)
        lastEnd = clock()
        return ok
    }

    override fun soon() {
        pending.set(true)
        if (running.compareAndSet(false, true)) spawn(Runnable { loop() })
    }

    private fun loop() {
        try {
            while (pending.get()) {
                val wait = minGapMs - (clock() - lastEnd)
                if (wait > 0) runCatching { sleep(wait) }
                pending.set(false)                      // everything requested up to here is covered by the flush below
                now(SystemSync.DEFAULT_TIMEOUT_MS)
            }
        } finally {
            running.set(false)
            if (pending.get() && running.compareAndSet(false, true)) spawn(Runnable { loop() })      // a request slipped in while the worker was ending
        }
    }

    companion object {
        /** `sync`, waited for up to [timeoutMs] (then killed). False when the command does not exist or fails. */
        fun runSync(timeoutMs: Long): Boolean = try {
            val p = ProcessBuilder("sync").redirectErrorStream(true).start()
            runCatching { p.outputStream.close() }
            if (p.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) p.exitValue() == 0 else { p.destroyForcibly(); false }
        } catch (e: Exception) { false }

        /** The one of the TV app: shared by every store, so that the requests of all the volumes are coalesced together. */
        val shared: SystemSync by lazy { ShellSync() }
    }
}

/** `fsync` of files by path (opened read-only: the data of a file is flushed whoever wrote it, the same way [FileStore.syncPart] does it). */
object DiskFlush {
    /** True = the file reached the medium; false = it is missing or the system refused. */
    fun file(f: File): Boolean = try { FileInputStream(f).use { it.fd.sync() }; true } catch (e: Exception) { false }

    /** [count] files looked at, [allOk] = every one reached the medium. */
    class Flushed(val count: Int, val allOk: Boolean)

    /**
     * Every partial file a volume folder holds: the `.part` files (single-stream copies, and multi-connection copies that are complete but not committed yet) and the files of the unfinished
     * multi-connection transfers (`.cbx/`). Partial copies always live flat in the folder (docs/STORAGE.md §10): finished and filed files were synced when they were committed.
     */
    fun partials(dir: File): Flushed {
        val files = dir.listFiles().orEmpty().filter { it.isFile && it.name.endsWith(Storage.PART) } +
            File(dir, castbridge.core.xfer.PartAssembler.SUB).listFiles().orEmpty().filter { it.isFile }
        var ok = true
        for (f in files) if (!file(f)) ok = false
        return Flushed(files.size, ok)
    }
}
