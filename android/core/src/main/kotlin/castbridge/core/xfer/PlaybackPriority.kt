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
    /** Cumulative bytes the TV's copies have written to disk (any copy): « a copy is writing » is a change of this number. */
    val copyBytes: Long = 0,
    val durMs: Long = 0,
    /** Final size of the file being played (0 = unknown): with [durMs], its average bitrate. */
    val fileBytes: Long = 0,
    /** How fast the TV's disk absorbs the copy (bytes/s, time inside `write` only), 0 = not measured yet. */
    val writeBps: Long = 0,
    /** Receive rate of the copy that feeds a growing playback (bytes/s), 0 = unknown. */
    val feedBps: Long = 0,
    /** Bytes of the growing file present from its first byte without a hole: the most the player may read. */
    val contiguousBytes: Long = 0,
    /** Volume the played file lives on (null = unknown): a copy is « on the same disk » when it writes to this one. */
    val playingVolumeId: String? = null,
    /** Seconds of comfort the player has in hand ([PlaybackHealth]); null = the player gives no measure (open-loop fallback). */
    val bufferSec: Double? = null,
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

    fun decide(s: PlaybackSignal, normal: Normal, transferName: String? = null, targetVolumeId: String? = null): PlaybackDecision {
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
        // R-15: same disk as the player => paced disk; another disk => no disk pacing (CPU relief stays); unknown => R-06 values
        val same = PlaybackAwareCopyPolicy.sameVolume(s.playingVolumeId, targetVolumeId)
        reasons += when (same) { true -> "same-volume"; false -> "other-volume"; null -> "volume:unknown" }
        return spaced.copy(reasons = reasons, backgroundThreads = true, maxStreams = minOf(PLAYING_MAX_STREAMS, normal.maxStreams).coerceAtLeast(1),
            receiveCapBps = PlaybackAwareCopyPolicy.receiveCap(s.writeBps, same), verifyReadBps = PlaybackAwareCopyPolicy.verifyCap(same),
            syncEveryBytes = if (PlaybackAwareCopyPolicy.deferFsync(same)) spaced.syncEveryBytes else normal.syncEveryBytes, busyRetryMs = PLAYING_RETRY_MS)
    }

    /** Half of what the disk absorbs (the player reads the same medium), within [MIN_CAP_BPS]..[MAX_CAP_BPS]; [UNKNOWN_DISK_CAP_BPS] until measured. */
    fun receiveCap(writeBps: Long): Long = if (writeBps <= 0) UNKNOWN_DISK_CAP_BPS else (writeBps / 2).coerceIn(MIN_CAP_BPS, MAX_CAP_BPS)

    /** Average bitrate of the video (bytes/s), 0 = unknown. */
    fun videoBps(fileBytes: Long, durMs: Long): Long = if (fileBytes <= 0 || durMs <= 0) 0 else fileBytes * 1000 / durMs

    /** A `buffering` player whose playhead has not moved for this long no longer counts as playing (crashed or stuck player, dead copy). */
    const val BUFFERING_GRACE_MS = 30_000L

    /** Head first: a block is taken now only if it starts within [window] bytes of the contiguous prefix (blocks behind it are resends: always). */
    fun admitAhead(blockOffset: Long, contiguousBytes: Long, window: Long = HEAD_WINDOW_BYTES): Boolean = blockOffset < contiguousBytes + window

    /** Head window of a copy that is not preallocated: never less than [HEAD_WINDOW_BYTES], never less than what the connections legitimately hold in flight. */
    fun headWindow(blockSize: Int, maxStreams: Int): Long = maxOf(HEAD_WINDOW_BYTES, (maxStreams + 2).toLong() * blockSize)

    /** libVLC `:file-caching` of a local file: 400 ms at rest (RAM), 2 s while a copy writes to the disk the player reads. */
    fun fileCachingMs(receiving: Boolean): Long = if (receiving) 2_000 else 400

    /**
     * R-15: read-ahead of a local file while a copy writes to the disk it is read from, bounded by the memory of the TV: at most 1/16 of the app's heap
     * ([maxMemoryBytes] = `Runtime.maxMemory()`, an upper estimate of what a 32-bit 1 GB TV spares; libVLC's cache is native memory, so this is a
     * declared prudence, not a measure) and 32 MiB, for an assumed 2 MB/s video (720p to 1080p): 2 s to 4 s, 400 ms at rest. Applied when a file starts.
     */
    fun fileCachingMs(receiving: Boolean, maxMemoryBytes: Long): Long {
        if (!receiving) return 400
        val budget = minOf(maxMemoryBytes / 16, 32L shl 20).coerceAtLeast(0)
        return (budget * 1000 / ASSUMED_VIDEO_BPS).coerceIn(2_000, 4_000)
    }
    const val ASSUMED_VIDEO_BPS = 2_000_000L

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
    /** Runs a deferred drain: by default on a thread of its own (`cb-deferred-sync`), never on a request thread (an fsync takes seconds on a USB key). */
    private val drainer: (() -> Unit) -> Unit = { job -> Thread({ runCatching { job() } }, "cb-deferred-sync").apply { isDaemon = true; start() } },
) {
    val deferred = DeferredWork()
    /** R-15: closed-loop regulator fed with the player's comfort (seconds); open loop whenever the player gives no measure. */
    val buffer = BufferGovernor(clock)
    @Volatile private var lastPacedAt = Long.MIN_VALUE / 2
    private val receivePacer = RatePacer({ clock() * 1_000_000 }, sleep)
    private val verifyPacer = RatePacer({ clock() * 1_000_000 }, sleep)
    @Volatile private var sig = PlaybackSignal()
    @Volatile private var cached: PlaybackDecision? = null
    @Volatile private var at = 0L
    private val notifyLock = Any()
    // « buffering without progress »: see [settle]
    private var lastHead = -1L
    private var lastAdvanceAt = 0L
    private var stuck = false
    private var lastCopyBytes = -1L
    private var lastCopyAt = 0L

    fun current(): PlaybackDecision {
        val c = cached
        if (c != null && clock() - at < refreshMs) return c
        return refresh()
    }

    /** Reads the signal now (the player just changed state). The deferred work (an fsync) runs on the `cb-deferred-sync` thread once playback is over. */
    fun refresh(): PlaybackDecision = update()

    private val draining = java.util.concurrent.atomic.AtomicBoolean(false)
    /** Every drain goes through here: one drainer at a time, off the caller's thread. */
    private fun drainAsync() {
        if (deferred.pending().isEmpty() || !draining.compareAndSet(false, true)) return
        try { drainer { try { deferred.drain() } finally { draining.set(false) } } } catch (e: Throwable) { draining.set(false) }
    }

    private fun update(): PlaybackDecision {
        val s = settle(runCatching(signal).getOrDefault(PlaybackSignal()))
        val d = PlaybackPriority.decide(s, normal)
        val prev = synchronized(this) { cached.also { sig = s; cached = d; at = clock() } }
        // delivered one at a time and always with the LATEST decision: two racing refreshes must not leave a stale one last
        if (d.on) buffer.sample(s.bufferSec) else buffer.reset()
        if (prev?.on != d.on) synchronized(notifyLock) { runCatching { onChange(cached ?: d) } }
        if (!d.on) drainAsync()
        return d
    }

    /**
     * `buffering` counts as playing for at most [PlaybackPriority.BUFFERING_GRACE_MS] without playhead advance: a crashed or stuck player (or a
     * dead copy that a reader keeps reconnecting to) must not keep every other copy throttled, nor the deferred fsync waiting, for ever. Once
     * latched off it stays off, whatever flapping the player reports, until the playhead really moves again.
     */
    private fun settle(raw: PlaybackSignal): PlaybackSignal = synchronized(this) {
        val now = clock()
        if (raw.copyBytes != lastCopyBytes) { lastCopyBytes = raw.copyBytes; lastCopyAt = now }
        if (raw.playerState != "playing" && raw.playerState != "buffering") { stuck = false; lastHead = -1L; lastAdvanceAt = now; return raw }   // paused/idle: the counters restart
        if (raw.playheadMs != lastHead) { lastHead = raw.playheadMs; lastAdvanceAt = now; stuck = false }
        else if (raw.playerState == "buffering" && now - lastAdvanceAt >= PlaybackPriority.BUFFERING_GRACE_MS &&
            // a player IO-starved by a copy of ITS OWN file on the same flash is exactly what the policy is for: lock off only for a dead copy
            // (no byte written for the whole window) or when the file played is not the one being copied
            (!raw.growing || now - lastCopyAt >= PlaybackPriority.BUFFERING_GRACE_MS)) stuck = true
        if (stuck) raw.copy(playerState = "stalled") else raw
    }

    /** The decision for one transfer (the copy that feeds a growing playback is treated apart). */
    fun forTransfer(name: String?, volumeId: String? = null): PlaybackDecision { val d = current(); return if (!d.on) d else PlaybackPriority.decide(sig, normal, name, volumeId) }

    /** « Copie ralentie pour ne pas gêner la lecture » is true only while a copy byte was really held back in the last [SLOWED_LINGER_MS] and a video plays. */
    fun slowedNow(): Boolean = current().on && clock() - lastPacedAt < SLOWED_LINGER_MS

    /** Read-only state for /api/info `playbackPriority` (no secret, no address). */
    fun json(): String {
        val c = cached
        val d = if (c != null && clock() - at < refreshMs) c else update()
        if (!d.on) drainAsync()
        val q = { x: String -> "\"" + x.replace("\\", "\\\\").replace("\"", "\\\"") + "\"" }
        return "{\"on\":${d.on},\"reasons\":${d.reasons.joinToString(",", "[", "]") { q(it) }},\"backgroundThreads\":${d.backgroundThreads}," +
            "\"maxStreams\":${d.maxStreams},\"receiveCapBps\":${d.receiveCapBps},\"progressEveryMs\":${d.progressEveryMs}," +
            "\"syncEveryBytes\":${d.syncEveryBytes},\"verifyReadBps\":${d.verifyReadBps},\"deferred\":${deferred.pending().size}," +
            "\"slowed\":${slowedNow()},\"bufferBand\":\"${buffer.band.name.lowercase()}\",\"bufferSec\":${buffer.smoothedSec()?.let { String.format(java.util.Locale.ROOT, "%.1f", it) } ?: "null"}}"
    }

    /**
     * One receive request (or one long upload) on the calling thread: lowers its priority while the policy says so, paces the bytes, re-reads
     * the decision every [refreshMs] (playback may start or stop in the middle of a copy), and ALWAYS restores the priority on [close].
     */
    inner class Receive internal constructor(private val name: String?, private val verify: Boolean, private val volumeId: String?) : Closeable {
        private var lowered = false
        private var checked = clock()
        var decision: PlaybackDecision = forTransfer(name, volumeId).also { apply(it) }; private set

        private fun recheck() { val t = clock(); if (t - checked >= refreshMs) { checked = t; decision = forTransfer(name, volumeId); apply(decision) } }

        fun onBytes(n: Int) {
            recheck()
            val governed = decision.backgroundThreads
            // R-15 closed loop: the player's buffer is nearly empty => wait (bounded by MAX_HOLD_MS, a heartbeat slice then passes)
            while (governed && decision.backgroundThreads) {
                val h = buffer.holdMs()
                if (h <= 0) break
                lastPacedAt = clock(); sleep(minOf(h, HOLD_SLICE_MS)); recheck()
            }
            val slept = if (verify) verifyPacer.pace(n, decision.verifyReadBps)
                else receivePacer.pace(n, if (decision.backgroundThreads) buffer.allowedBps(decision.receiveCapBps) else decision.receiveCapBps)
            if (slept >= MIN_SLOW_MS) lastPacedAt = clock()
            if (governed) buffer.passed()
        }

        private fun apply(d: PlaybackDecision) {
            if (d.backgroundThreads && !lowered) { runCatching { port.background() }; lowered = true }
            else if (!d.backgroundThreads && lowered) { runCatching { port.normal() }; lowered = false }
        }

        override fun close() { if (lowered) { runCatching { port.normal() }; lowered = false } }
    }

    /** [volumeId] = the volume the copy writes to (R-15: same disk as the player or not). */
    fun receive(name: String?, volumeId: String? = null): Receive = Receive(name, false, volumeId)
    /** The final read-back of a copy: same priority rule, paced to [PlaybackDecision.verifyReadBps], held (never skipped) while the player's buffer is nearly empty. */
    fun verify(name: String?, volumeId: String? = null): Receive = Receive(name, true, volumeId)

    companion object {
        const val SLOWED_LINGER_MS = 4_000L
        const val MIN_SLOW_MS = 20L
        const val HOLD_SLICE_MS = 250L
    }
}
