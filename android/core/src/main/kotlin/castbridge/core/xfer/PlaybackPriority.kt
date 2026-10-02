package castbridge.core.xfer

import java.io.Closeable

/**
 * Lowers or restores the priority of the CALLING thread. The TV app maps it to `Process.setThreadPriority(THREAD_PRIORITY_BACKGROUND / DEFAULT)`;
 * core stays free of Android. Both calls must be cheap and must never throw.
 */
interface ThreadPriorityPort {
    fun background()
    fun normal()
    companion object { val NONE: ThreadPriorityPort = object : ThreadPriorityPort { override fun background() {}; override fun normal() {} } }
}

/** What the TV is doing right now, as far as « playback first » is concerned (sampled by [PlaybackGovernor]). */
data class PlaybackSignal(
    /** [castbridge.core.tv.PlayerState.state]: "playing", "buffering", "paused", "ended", "error", "idle". */
    val playerState: String = "idle",
    val playingName: String? = null,
    /** The file being played is still arriving (« lire pendant l'envoi »: the player reads a growing `.part`). */
    val growing: Boolean = false,
    val playheadMs: Long = 0,
    val durMs: Long = 0,
    /** Final size of the file being played (0 = unknown): with [durMs], its average bitrate. */
    val fileBytes: Long = 0,
    /** How fast the TV's disk absorbs the copy (bytes/s, time inside `write` only), 0 = not measured yet. */
    val writeBps: Long = 0,
    /** Receive rate of the copy that feeds a growing playback (bytes/s), 0 = unknown. */
    val feedBps: Long = 0,
    /** Bytes of the growing file present from its first byte without a hole: the most the player may read. */
    val contiguousBytes: Long = 0,
)

/** One decision of the policy. Every field has its « TV at rest » value when [on] is false, so the receive paths behave exactly as before. */
data class PlaybackDecision(
    val on: Boolean,
    /** Short machine codes, shown read-only in /api/info `playbackPriority.reasons` for the next field measurement. */
    val reasons: List<String>,
    /** Receive, hash and write threads run at background priority (the decoder and the UI come first). */
    val backgroundThreads: Boolean,
    /** Concurrent chunk requests of the multi-lane transfer the TV accepts (more are answered 429 busy: the phone backs off). */
    val maxStreams: Int,
    /** Receive rate cap shared by every receive thread (bytes/s), 0 = none. */
    val receiveCapBps: Long,
    /** Interval between two repaints of the reception progress (notification + home chip). */
    val progressEveryMs: Long,
    /** Periodic fsync of a partial copy on a removable drive (the final fsync before the commit never changes). */
    val syncEveryBytes: Long,
    /** Pace of the final read-back verification of a multi-lane copy (bytes/s), 0 = none. It is slowed, NEVER skipped. */
    val verifyReadBps: Long,
    /** Interval between two saves of a multi-lane block map (resume after a restart; an older map only means blocks sent again). */
    val statePersistMs: Long,
    /** `retryMs` of a 429 busy answer. */
    val busyRetryMs: Long,
)

/**
 * « La lecture reste fluide pendant une copie » (docs/agent-reports/fluid-playback-during-copy.md): ONE pure table that every receive path asks.
 *
 * While the TV plays a video (state playing or buffering):
 *  - a copy that does NOT feed that video runs at background thread priority, capped to half of what the disk absorbs (the other half is the
 *    player's: the same eMMC or USB key), on at most [PLAYING_MAX_STREAMS] connections, its fsync batches deferred (bounded) and its final
 *    read-back verification paced (never skipped);
 *  - the copy that FEEDS a growing playback (« Copier sur la TV et lire » on the classic path) is the playback's lifeline: no cap, normal
 *    priority; only its fsync batches and its progress repaints are spaced out;
 *  - progress repaints are spaced to [PLAYING_PROGRESS_MS] (notification + home chip cost CPU on a 4×A53).
 * Paused, ended, idle: everything is back to full speed at once.
 */
object PlaybackPriority {
    data class Normal(val maxStreams: Int, val syncEveryBytes: Long)

    const val IDLE_PROGRESS_MS = 1_000L
    const val PLAYING_PROGRESS_MS = 2_500L
    const val IDLE_PERSIST_MS = 1_000L
    const val PLAYING_PERSIST_MS = 10_000L
    const val IDLE_RETRY_MS = 250L
    const val PLAYING_RETRY_MS = 1_000L
    const val PLAYING_MAX_STREAMS = 2
    /** fsync at most every 4× the usual amount while playing (64 MiB → 256 MiB on a removable drive): the loss on a pulled key stays bounded. */
    const val SYNC_DEFER_FACTOR = 4
    /** Final read-back while playing: 12 MB/s leaves the player its share of a USB 2 key (≈ 20-30 MB/s read) and keeps a 2 GB check under 3 min. */
    const val VERIFY_CAP_BPS = 12_000_000L
    const val UNKNOWN_DISK_CAP_BPS = 3_000_000L
    const val MIN_CAP_BPS = 1_000_000L
    const val MAX_CAP_BPS = 6_000_000L
    /** Head-first window: a block more than this beyond the contiguous prefix waits (copy-and-play on blocks). */
    const val HEAD_WINDOW_BYTES = 32L shl 20

    private val ACTIVE = setOf("playing", "buffering")

    fun decide(s: PlaybackSignal, normal: Normal, transferName: String? = null): PlaybackDecision {
        val rest = PlaybackDecision(false, emptyList(), false, normal.maxStreams.coerceAtLeast(1), 0, IDLE_PROGRESS_MS, normal.syncEveryBytes,
            0, IDLE_PERSIST_MS, IDLE_RETRY_MS)
        if (s.playerState !in ACTIVE) return rest
        val reasons = arrayListOf("player:${s.playerState}")
        val feeds = s.growing && transferName != null && s.playingName != null && transferName.equals(s.playingName, ignoreCase = true)
        if (s.growing) reasons += "growing"
        val video = videoBps(s.fileBytes, s.durMs)
        if (s.growing && video > 0 && s.feedBps in 1 until video) reasons += "underrun-risk"
        val spaced = rest.copy(on = true, reasons = reasons, progressEveryMs = PLAYING_PROGRESS_MS,
            syncEveryBytes = normal.syncEveryBytes * SYNC_DEFER_FACTOR, statePersistMs = PLAYING_PERSIST_MS)
        if (feeds) return spaced.copy(reasons = reasons + "feeds-playback")
        reasons += "copy:background"
        return spaced.copy(reasons = reasons, backgroundThreads = true, maxStreams = minOf(PLAYING_MAX_STREAMS, normal.maxStreams).coerceAtLeast(1),
            receiveCapBps = receiveCap(s.writeBps), verifyReadBps = VERIFY_CAP_BPS, busyRetryMs = PLAYING_RETRY_MS)
    }

    /** Half of what the disk absorbs (the player reads the same medium), within [MIN_CAP_BPS]..[MAX_CAP_BPS]; [UNKNOWN_DISK_CAP_BPS] until measured. */
    fun receiveCap(writeBps: Long): Long = if (writeBps <= 0) UNKNOWN_DISK_CAP_BPS else (writeBps / 2).coerceIn(MIN_CAP_BPS, MAX_CAP_BPS)

    /** Average bitrate of the video (bytes/s), 0 = unknown. */
    fun videoBps(fileBytes: Long, durMs: Long): Long = if (fileBytes <= 0 || durMs <= 0) 0 else fileBytes * 1000 / durMs

    /** Playback time held ahead of the playhead in the contiguous prefix (constant-bitrate estimate), null = unknown; never negative. */
    fun leadMs(contiguousBytes: Long, playheadMs: Long, fileBytes: Long, durMs: Long): Long? {
        val bps = videoBps(fileBytes, durMs).takeIf { it > 0 } ?: return null
        val playheadByte = fileBytes * playheadMs.coerceIn(0, durMs) / durMs
        return ((contiguousBytes - playheadByte).coerceAtLeast(0) * 1000 / bps)
    }

    /** Head first: a block is taken now only if it starts within [window] bytes of the contiguous prefix (blocks behind it are resends: always). */
    fun admitAhead(blockOffset: Long, contiguousBytes: Long, window: Long = HEAD_WINDOW_BYTES): Boolean = blockOffset < contiguousBytes + window

    /** libVLC `:file-caching` of a local file: 400 ms at rest (RAM), 2 s while a copy writes to the disk the player reads. */
    fun fileCachingMs(receiving: Boolean): Long = if (receiving) 2_000 else 400

    /** libVLC `:network-caching`: 1.2 s for a link, 3 s for a file still arriving (rides out a write burst or a Wi-Fi dip). */
    fun networkCachingMs(growing: Boolean): Long = if (growing) 3_000 else 1_200
}

/** Paces bytes to a rate shared by every caller (virtual-time token bucket, at most [burstMs] of credit). [clock] in nanoseconds. */
class RatePacer(private val clock: () -> Long = System::nanoTime, private val sleep: (Long) -> Unit = Thread::sleep, private val burstMs: Long = 250) {
    private var next = Long.MIN_VALUE

    /** Accounts [bytes] at [bps] (0 = no cap) and sleeps as long as needed; returns the milliseconds slept. */
    fun pace(bytes: Int, bps: Long): Long {
        if (bps <= 0 || bytes <= 0) return 0
        val wait = synchronized(this) {
            val now = clock()
            val floor = now - burstMs * 1_000_000
            if (next < floor) next = floor
            next += bytes * 1_000_000_000L / bps
            (next - now) / 1_000_000
        }
        if (wait > 0) sleep(wait)
        return maxOf(0, wait)
    }
}

/** Work put off while a video plays (an fsync of a partial copy left by a cut...), keyed so it is never queued twice; run by [drain]. */
class DeferredWork {
    private val tasks = LinkedHashMap<String, () -> Unit>()
    @Synchronized fun defer(key: String, task: () -> Unit) { if (key !in tasks) tasks[key] = task }
    /** Runs every queued task once (outside the lock; a failing one does not stop the others); returns how many ran. */
    fun drain(): Int {
        val list = synchronized(this) { tasks.values.toList().also { tasks.clear() } }
        list.forEach { runCatching { it() } }
        return list.size
    }
    @Synchronized fun pending(): List<String> = tasks.keys.toList()
}

/**
 * Holds the live decision of [PlaybackPriority] for the TV's receive paths: re-reads [signal] at most every [refreshMs] (a chunk must not pay
 * for it), runs the [deferred] work as soon as playback stops, tells [onChange] when the decision flips, and applies it to the calling thread
 * through [receive] / [verify]. [clock] in milliseconds.
 */
class PlaybackGovernor(
    private val signal: () -> PlaybackSignal,
    private val normal: PlaybackPriority.Normal,
    private val port: ThreadPriorityPort = ThreadPriorityPort.NONE,
    private val clock: () -> Long = System::currentTimeMillis,
    private val refreshMs: Long = 500,
    private val sleep: (Long) -> Unit = Thread::sleep,
    private val onChange: (PlaybackDecision) -> Unit = {},
) {
    val deferred = DeferredWork()
    private val receivePacer = RatePacer({ clock() * 1_000_000 }, sleep)
    private val verifyPacer = RatePacer({ clock() * 1_000_000 }, sleep)
    @Volatile private var sig = PlaybackSignal()
    @Volatile private var cached: PlaybackDecision? = null
    @Volatile private var at = 0L

    fun current(): PlaybackDecision {
        val c = cached
        if (c != null && clock() - at < refreshMs) return c
        return refresh()
    }

    /** Reads the signal now (the player just changed state). */
    fun refresh(): PlaybackDecision {
        val s = runCatching(signal).getOrDefault(PlaybackSignal())
        val d = PlaybackPriority.decide(s, normal)
        val prev = synchronized(this) { cached.also { sig = s; cached = d; at = clock() } }
        if (prev?.on != d.on) runCatching { onChange(d) }
        if (!d.on) deferred.drain()
        return d
    }

    /** The decision for one transfer (the copy that feeds a growing playback is treated apart). */
    fun forTransfer(name: String?): PlaybackDecision { val d = current(); return if (!d.on) d else PlaybackPriority.decide(sig, normal, name) }

    /** Read-only state for /api/info `playbackPriority` (no secret, no address). */
    fun json(): String {
        val d = current()
        val q = { x: String -> "\"" + x.replace("\\", "\\\\").replace("\"", "\\\"") + "\"" }
        return "{\"on\":${d.on},\"reasons\":${d.reasons.joinToString(",", "[", "]") { q(it) }},\"backgroundThreads\":${d.backgroundThreads}," +
            "\"maxStreams\":${d.maxStreams},\"receiveCapBps\":${d.receiveCapBps},\"progressEveryMs\":${d.progressEveryMs}," +
            "\"syncEveryBytes\":${d.syncEveryBytes},\"verifyReadBps\":${d.verifyReadBps},\"deferred\":${deferred.pending().size}}"
    }

    /**
     * One receive request (or one long upload) on the calling thread: lowers its priority while the policy says so, paces the bytes, re-reads
     * the decision every [refreshMs] (playback may start or stop in the middle of a copy), and ALWAYS restores the priority on [close].
     */
    inner class Receive internal constructor(private val name: String?, private val verify: Boolean) : Closeable {
        private var lowered = false
        private var checked = clock()
        var decision: PlaybackDecision = forTransfer(name).also { apply(it) }; private set

        fun onBytes(n: Int) {
            val t = clock()
            if (t - checked >= refreshMs) { checked = t; decision = forTransfer(name); apply(decision) }
            if (verify) verifyPacer.pace(n, decision.verifyReadBps) else receivePacer.pace(n, decision.receiveCapBps)
        }

        private fun apply(d: PlaybackDecision) {
            if (d.backgroundThreads && !lowered) { runCatching { port.background() }; lowered = true }
            else if (!d.backgroundThreads && lowered) { runCatching { port.normal() }; lowered = false }
        }

        override fun close() { if (lowered) { runCatching { port.normal() }; lowered = false } }
    }

    fun receive(name: String?): Receive = Receive(name, false)
    /** The final read-back of a copy: same priority rule, paced to [PlaybackDecision.verifyReadBps]. */
    fun verify(name: String?): Receive = Receive(name, true)
}
