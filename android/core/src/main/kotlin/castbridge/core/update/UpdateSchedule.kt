package castbridge.core.update

import kotlin.random.Random

/**
 * When to ask the server for an update: at start-up, then every [periodMs] (12 h) with a per-device jitter (so that all
 * the TVs do not call at the same minute), exponential backoff after errors, and never more than once per
 * [minIntervalMs] (1 h) whatever triggers it (start-up loop, server asking for a check, network coming back).
 *
 * Pure logic: the app stores [State] (e.g. in its preferences via [State.encode]) and asks [isDue] from its timer.
 */
class UpdateSchedule(
    val periodMs: Long = 12 * HOUR,
    val jitterMs: Long = HOUR,
    val minIntervalMs: Long = HOUR,
    val maxBackoffMs: Long = 12 * HOUR,
    /** Stable per device (e.g. the hash of the install id): spreads the checks of different devices. */
    private val deviceSeed: Long = 0,
) {
    data class State(val lastCheckAt: Long = 0, val lastSuccessAt: Long = 0, val failures: Int = 0) {
        fun encode() = "$lastCheckAt,$lastSuccessAt,$failures"

        companion object {
            fun decode(s: String?): State = runCatching {
                val p = s!!.split(",")
                State(p[0].toLong(), p[1].toLong(), p[2].toInt())
            }.getOrDefault(State())
        }
    }

    enum class Trigger {
        /** App (re)started. */
        STARTUP,
        /** Periodic timer. */
        TIMER,
        /** The server asked for a check in a heartbeat answer (admin action). */
        FORCED,
        /** The user pressed "Vérifier maintenant": always allowed (a person is waiting), except twice within [USER_MIN_MS]. */
        USER,
    }

    /** Earliest time of the next regular check. */
    fun nextCheckAt(s: State): Long {
        if (s.lastCheckAt == 0L) return 0
        val next = if (s.failures > 0) {
            val backoff = minOf(maxBackoffMs, minIntervalMs shl minOf(20, s.failures - 1))
            s.lastCheckAt + backoff + jitter(s) / 4
        } else {
            s.lastCheckAt + periodMs + jitter(s)
        }
        return maxOf(next, s.lastCheckAt + minIntervalMs)
    }

    fun isDue(s: State, now: Long, trigger: Trigger): Boolean {
        if (trigger == Trigger.USER) return s.lastCheckAt == 0L || now < s.lastCheckAt || now - s.lastCheckAt >= USER_MIN_MS
        if (s.lastCheckAt != 0L && now - s.lastCheckAt < minIntervalMs && now >= s.lastCheckAt) return false
        return when (trigger) {
            Trigger.STARTUP, Trigger.FORCED, Trigger.USER -> true
            Trigger.TIMER -> now >= nextCheckAt(s)
        }
    }

    fun onSuccess(s: State, now: Long) = State(lastCheckAt = now, lastSuccessAt = now, failures = 0)

    fun onFailure(s: State, now: Long) = State(lastCheckAt = now, lastSuccessAt = s.lastSuccessAt, failures = s.failures + 1)

    /** -jitterMs..+jitterMs, the same for a given device and check (no drift at each call). */
    private fun jitter(s: State): Long {
        if (jitterMs <= 0) return 0
        val r = Random(deviceSeed xor s.lastCheckAt)
        return r.nextLong(-jitterMs, jitterMs + 1)
    }

    companion object {
        const val HOUR = 3_600_000L
        const val USER_MIN_MS = 10_000L
    }
}
