package castbridge.core.xfer

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Work stealing over the lanes. Each worker of each lane takes the next missing block as soon as it is free, so the fastest lane does
 * the most. Slow lanes take from the end of the queue, the others from the front (they meet in the middle) - except when the TV does not
 * preallocate its file ([slowFromHead]): a block written far ahead would make a FAT/exFAT kernel zero-fill the whole gap, so everyone goes in order. When nothing is left to take
 * but blocks are still in flight, a fast worker sends a copy of the oldest one (the last blocks are never held hostage by a slow lane);
 * the first copy to arrive wins and the others are told to stop. A lane that fails repeatedly is set aside for a while, then tried again.
 */
class Scheduler(
    private val manifest: Manifest,
    private val done: BlockMap,
    private val lanes: List<Lane>,
    private val ctx: (cancelled: () -> Boolean) -> SendContext,
    private val listener: Listener = Listener(),
    private val clock: () -> Long = System::nanoTime,
    private val sleepMs: (Long) -> Unit = Thread::sleep,
    private val maxCorrupt: Int = 4,
    private val strikesToBench: Int = 3,
    private val benchBaseMs: Long = 1500,
    /** The TV does not preallocate (FAT/exFAT): the slow lane takes from the front too (see [PlaybackPriority.headWindow]). */
    private val slowFromHead: Boolean = false,
    /** R-17: the same transient reason this many times in a row on a lane, over [stuckSpanMs], with no progress anywhere = a visible failure (see [StuckDetector]). */
    stuckRepeats: Int = StuckDetector.DEFAULT_REPEATS,
    stuckSpanMs: Long = StuckDetector.DEFAULT_SPAN_MS,
    /** R-21 : période (horloge du planificateur) entre deux publications « envoyé / confirmé » quand quelque chose a bougé. */
    private val publishEveryMs: Long = 1_000,
    /** R-21 : un bloc renvoyé [resendsToStop] fois sans qu'aucun octet ne soit confirmé nulle part pendant [noConfirmMs] = arrêt avec la cause (jamais de renvoi sans fin). */
    private val noConfirmMs: Long = 90_000,
    private val resendsToStop: Int = 3,
) {
    private val stuck = StuckDetector(stuckRepeats, stuckSpanMs)
    open class Listener {
        open fun progress(doneBytes: Long, total: Long) {}
        /** R-21 : [sentBytes] = écrits sur le socket (blocs confirmés + morceaux des blocs en vol), [confirmedBytes] = confirmés par la TV (blocs vérifiés + tranches acquittées). Jamais en recul. */
        open fun sent(sentBytes: Long, confirmedBytes: Long, total: Long) {}
        open fun waiting(reason: String) {}
        open fun laneEvent(lane: String, what: String) {}
    }
    sealed class Result {
        object Done : Result()
        object Cancelled : Result()
        object SessionLost : Result()
        class Failed(val reason: String) : Result()
    }

    private class Run(val lane: Lane, val startedNs: Long, val abort: AtomicBoolean = AtomicBoolean(false)) {
        val written = AtomicLong(); val acked = AtomicLong()
    }
    private val resends = HashMap<Int, Int>()
    private var lastCause: String? = null
    private var lastProgressMs = 0L
    private var lastMoved = -1L
    private var hiSent = 0L
    private var hiConfirmed = 0L
    private var lastPublishMs = Long.MIN_VALUE / 2
    private var lastPublished = -1L to -1L
    private val lock = Object()
    private val pending = ArrayDeque<Int>()
    private val running = HashMap<Int, MutableList<Run>>()
    private val corrupt = HashMap<Int, Int>()
    private val strikes = HashMap<String, Int>()
    private val benchedUntil = HashMap<String, Long>()
    private val benchCount = HashMap<String, Int>()
    private val avgNs = HashMap<String, Long>()
    private var failure: String? = null
    private var sessionLost = false
    private var doneBytes = 0L
    val duplicates = AtomicLong()

    /** Blocks confirmed per lane (the fairness report); see also [Lane.sent]. */
    fun blocksBy(lane: String): Int = synchronized(lock) { counts[lane] ?: 0 }
    private val counts = HashMap<String, Int>()

    fun run(cancelled: () -> Boolean): Result {
        synchronized(lock) {
            pending.clear(); pending.addAll(done.missing())
            doneBytes = (0 until manifest.blocks).filter { done.has(it) }.sumOf { manifest.length(it).toLong() }
            lastProgressMs = clock() / 1_000_000; hiSent = doneBytes; hiConfirmed = doneBytes
        }
        if (done.complete()) return Result.Done
        val threads = ArrayList<Thread>()
        for (lane in lanes) for (w in 0 until lane.maxWorkers)
            threads += Thread({ worker(lane, w, cancelled) }, "xfer-${lane.id}-$w").apply { isDaemon = true; start() }
        synchronized(lock) {
            while (!done.complete() && failure == null && !sessionLost && !cancelled()) { lock.wait(100); tick() }
            lock.notifyAll()
        }
        threads.forEach { it.join(300) }      // daemons: one stuck in a slow write is released when the engine closes its lane
        synchronized(lock) {
            return when {
                done.complete() -> Result.Done
                sessionLost -> Result.SessionLost
                failure != null -> Result.Failed(failure!!)
                else -> Result.Cancelled
            }
        }
    }

    /** Sous [lock] : publie « envoyé / confirmé » au plus toutes les [publishEveryMs] quand ça a changé, et arrête une copie qui renvoie sans que rien ne soit confirmé. */
    private fun tick() {
        val nowMs = clock() / 1_000_000
        val moved = lanes.sumOf { it.bytesMoved() } + running.values.sumOf { rs -> rs.maxOfOrNull { it.acked.get() } ?: 0L }
        if (moved != lastMoved) { lastMoved = moved; lastProgressMs = nowMs }
        if (failure == null && nowMs - lastProgressMs >= noConfirmMs && resends.values.any { it >= resendsToStop }) {
            failure = CopyCauses.noConfirm(lastCause); lock.notifyAll(); return
        }
        var sent = doneBytes; var conf = doneBytes
        for ((idx, rs) in running) if (!done.has(idx)) {
            val len = manifest.length(idx).toLong()
            sent += minOf(len, rs.maxOf { it.written.get() }); conf += minOf(len, rs.maxOf { it.acked.get() })
        }
        hiSent = maxOf(hiSent, sent, conf); hiConfirmed = maxOf(hiConfirmed, conf)
        if (nowMs - lastPublishMs >= publishEveryMs && (hiSent to hiConfirmed) != lastPublished) {
            lastPublishMs = nowMs; lastPublished = hiSent to hiConfirmed
            listener.sent(hiSent, hiConfirmed, manifest.size)
        }
    }

    private fun finished(cancelled: () -> Boolean) = done.complete() || failure != null || sessionLost || cancelled()

    private fun worker(lane: Lane, w: Int, cancelled: () -> Boolean) {
        var failsInARow = 0
        while (true) {
            synchronized(lock) { if (finished(cancelled)) return }
            val benched = synchronized(lock) { (benchedUntil[lane.id] ?: 0L) > clock() / 1_000_000 }
            if (w >= lane.allowedWorkers() || benched) { sleepMs(50); continue }
            val t = take(lane)
            if (t == null) { sleepMs(30); continue }
            val (idx, run) = t
            val c = ctx { cancelled() || run.abort.get() }.withCounters({ n -> run.written.addAndGet(n) }, { n -> run.acked.addAndGet(n) })
            val out = try { lane.send(w, idx, c) } catch (e: Exception) { Outcome.Failed(e.message ?: e.javaClass.simpleName) }
            report(lane, idx, run, out, cancelled)
            if (out is Outcome.Busy) sleepMs(out.retryMs.coerceIn(20, 2000))
            // R-20: a block that fails again and again on this worker waits 100 ms, 200 ms, ... up to 3 s (was a fixed 100 ms: ten requests a second at a dead TV)
            else if (out is Outcome.Failed && !out.fatal) { sleepMs(Backoff.delayMs(failsInARow, 100, 3_000)); failsInARow++ }
            else failsInARow = 0
        }
    }

    private fun take(lane: Lane): Pair<Int, Run>? {
        synchronized(lock) {
            val now = clock()
            var idx: Int? = if (lane.slow && !slowFromHead) pending.removeLastOrNull() else pending.removeFirstOrNull()
            if (idx == null && !lane.slow) idx = duplicateCandidate(lane, now)?.also { duplicates.incrementAndGet() }
            if (idx == null) return null
            val r = Run(lane, now)
            running.getOrPut(idx) { ArrayList() }.add(r)
            return idx to r
        }
    }

    /** The oldest block in flight that may be copied: not held by this lane, unless it looks stalled; at most two copies. */
    private fun duplicateCandidate(lane: Lane, now: Long): Int? {
        val typical = avgNs[lane.id] ?: 0L
        return running.entries.filter { (idx, runs) ->
            !done.has(idx) && runs.size < 2 && runs.none { it.lane === lane && now - it.startedNs < maxOf(2_000_000_000L, typical * 3) }
        }.minByOrNull { e -> e.value.minOf { it.startedNs } }?.key
    }

    private fun report(lane: Lane, idx: Int, run: Run, out: Outcome, cancelled: () -> Boolean) {
        synchronized(lock) {
            running[idx]?.let { it.remove(run); if (it.isEmpty()) running.remove(idx) }
            val stillRunning = running.containsKey(idx)
            fun requeue() { if (!done.has(idx) && !stillRunning && idx !in pending) pending.addFirst(idx) }
            when (out) {
                is Outcome.Ok, Outcome.Already -> {
                    strikes[lane.id] = 0; benchCount[lane.id] = 0; stuck.onProgress(); lastProgressMs = clock() / 1_000_000; resends.remove(idx)
                    if (out is Outcome.Ok) {
                        lane.sent.addAndGet(out.bytes)
                        val t = clock() - run.startedNs; avgNs[lane.id] = avgNs[lane.id]?.let { (it * 3 + t) / 4 } ?: t
                    }
                    if (done.set(idx)) {
                        doneBytes += manifest.length(idx); counts[lane.id] = (counts[lane.id] ?: 0) + 1
                        running.remove(idx)?.forEach { it.abort.set(true) }
                        listener.progress(doneBytes, manifest.size)      // under the lock: never out of order
                    }
                }
                is Outcome.Corrupt -> {
                    val n = (corrupt[idx] ?: 0) + 1; corrupt[idx] = n
                    listener.laneEvent(lane.id, "bloc $idx corrompu (${out.reason})")
                    if (n > maxCorrupt) failure = "bloc $idx refusé ${n} fois par la TV (${out.reason})" else requeue()
                }
                is Outcome.Busy -> { if (!run.abort.get()) { resends[idx] = (resends[idx] ?: 0) + 1; lastCause = out.cause ?: lastCause }; requeue() }
                Outcome.SessionLost -> { sessionLost = true }
                Outcome.Cancelled -> { if (!run.abort.get() && !cancelled()) requeue() }
                is Outcome.Failed -> {
                    if (run.abort.get()) { /* another lane finished it: not this lane's fault */ }
                    else if (out.fatal) failure = out.reason
                    else {
                        requeue()
                        val nowMs = clock() / 1_000_000; val moved = lanes.sumOf { it.bytesMoved() }       // clock first: a lane that moves bytes then advances time is seen moving
                        if (stuck.onFailure(lane.id, out.reason, nowMs, moved)) { failure = StuckDetector.message(out.reason); lock.notifyAll(); return }
                        val s = (strikes[lane.id] ?: 0) + 1; strikes[lane.id] = s
                        if (s >= strikesToBench) {
                            val n = (benchCount[lane.id] ?: 0) + 1; benchCount[lane.id] = n; strikes[lane.id] = 0
                            benchedUntil[lane.id] = clock() / 1_000_000 + minOf(benchBaseMs shl minOf(n - 1, 4), 30_000)
                            listener.laneEvent(lane.id, "voie mise à l'écart (${out.reason})")
                            if (lanes.all { (benchedUntil[it.id] ?: 0L) > clock() / 1_000_000 }) listener.waiting(out.reason)
                        }
                    }
                }
            }
            lock.notifyAll()
        }
    }
}
