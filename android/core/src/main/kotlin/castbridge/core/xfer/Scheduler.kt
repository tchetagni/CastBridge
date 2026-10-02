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
) {
    open class Listener {
        open fun progress(doneBytes: Long, total: Long) {}
        open fun waiting(reason: String) {}
        open fun laneEvent(lane: String, what: String) {}
    }
    sealed class Result {
        object Done : Result()
        object Cancelled : Result()
        object SessionLost : Result()
        class Failed(val reason: String) : Result()
    }

    private class Run(val lane: Lane, val startedNs: Long, val abort: AtomicBoolean = AtomicBoolean(false))
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
        }
        if (done.complete()) return Result.Done
        val threads = ArrayList<Thread>()
        for (lane in lanes) for (w in 0 until lane.maxWorkers)
            threads += Thread({ worker(lane, w, cancelled) }, "xfer-${lane.id}-$w").apply { isDaemon = true; start() }
        synchronized(lock) {
            while (!done.complete() && failure == null && !sessionLost && !cancelled()) lock.wait(100)
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

    private fun finished(cancelled: () -> Boolean) = done.complete() || failure != null || sessionLost || cancelled()

    private fun worker(lane: Lane, w: Int, cancelled: () -> Boolean) {
        while (true) {
            synchronized(lock) { if (finished(cancelled)) return }
            val benched = synchronized(lock) { (benchedUntil[lane.id] ?: 0L) > clock() / 1_000_000 }
            if (w >= lane.allowedWorkers() || benched) { sleepMs(50); continue }
            val t = take(lane)
            if (t == null) { sleepMs(30); continue }
            val (idx, run) = t
            val c = ctx { cancelled() || run.abort.get() }
            val out = try { lane.send(w, idx, c) } catch (e: Exception) { Outcome.Failed(e.message ?: e.javaClass.simpleName) }
            report(lane, idx, run, out, cancelled)
            if (out is Outcome.Busy) sleepMs(out.retryMs.coerceIn(20, 2000))
            else if (out is Outcome.Failed && !out.fatal) sleepMs(100)
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
                    strikes[lane.id] = 0; benchCount[lane.id] = 0
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
                is Outcome.Busy -> requeue()
                Outcome.SessionLost -> { sessionLost = true }
                Outcome.Cancelled -> { if (!run.abort.get() && !cancelled()) requeue() }
                is Outcome.Failed -> {
                    if (run.abort.get()) { /* another lane finished it: not this lane's fault */ }
                    else if (out.fatal) failure = out.reason
                    else {
                        requeue()
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
