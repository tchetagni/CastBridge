package castbridge.core.trust

/**
 * Storm control, so that several phones (or all of them after a TV reboot) never hammer a TV nor synchronise:
 * at most [perPeer] attempts per [windowMs] for one peer and [global] for everybody. Pure and clock-driven (tests use a fake clock).
 * [tryAcquire] returns 0 when the attempt may go ahead, else the milliseconds to wait.
 */
class AttemptLimiter(
    private val global: Int = 30,
    private val perPeer: Int = 6,
    private val windowMs: Long = 60_000,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val all = ArrayDeque<Long>()
    private val peers = HashMap<String, ArrayDeque<Long>>()

    @Synchronized fun tryAcquire(peer: String): Long {
        val t = now()
        prune(all, t)
        val mine = peers.getOrPut(peer) { ArrayDeque() }
        prune(mine, t)
        if (peers.size > 256) peers.entries.removeAll { prune(it.value, t); it.value.isEmpty() && it.key != peer }
        val wait = maxOf(if (all.size >= global) all.first() + windowMs - t else 0L, if (mine.size >= perPeer) mine.first() + windowMs - t else 0L)
        if (wait > 0) return wait
        all.addLast(t); mine.addLast(t)
        return 0
    }

    private fun prune(q: ArrayDeque<Long>, t: Long) { while (q.isNotEmpty() && q.first() + windowMs <= t) q.removeFirst() }
}

/** Delay with random spread: two phones that lost the TV at the same moment do not come back at the same moment. */
object Jitter {
    /** [ms] ± [spread] (0.25 = ±25 %); [random] gives a value in [0, 1). */
    fun around(ms: Long, spread: Double = 0.25, random: () -> Double = Math::random): Long =
        (ms * (1.0 - spread + 2 * spread * random().coerceIn(0.0, 1.0))).toLong().coerceAtLeast(0)
}
