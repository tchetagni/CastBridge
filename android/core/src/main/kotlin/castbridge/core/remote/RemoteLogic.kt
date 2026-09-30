package castbridge.core.remote

/**
 * Drops events the TV already applied. The phone numbers its events per session ([sid], random per remote screen) and resends
 * the ones it has no answer for after a reconnection; a resent event the TV already handled must not act twice (a second
 * "OK" would open the next thing). Events without a sequence number are always accepted. Bounded: [maxSessions] phones.
 */
class SeqFilter(private val maxSessions: Int = 16) {
    private val last = object : LinkedHashMap<String, Long>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?) = size > maxSessions
    }

    /** true = new (apply it), false = already seen. */
    @Synchronized fun accept(sid: String?, seq: Long?): Boolean {
        if (sid == null || seq == null) return true
        val prev = last[sid]
        if (prev != null && seq <= prev) return false
        last[sid] = seq
        return true
    }

    @Synchronized fun lastSeq(sid: String): Long? = last[sid]
}

/**
 * Keys held down from the phone. If the link drops while a key is held, its "up" never comes: after [timeoutMs] without a
 * repeat the TV releases it itself, so an arrow never keeps scrolling forever.
 */
class HoldTracker(private val timeoutMs: Long = 1200) {
    private val held = LinkedHashMap<RemoteKey, Long>()

    @Synchronized fun down(k: RemoteKey, now: Long) { held[k] = now }
    /** true if [k] was held (its release is legitimate). */
    @Synchronized fun up(k: RemoteKey): Boolean = held.remove(k) != null
    @Synchronized fun isHeld(k: RemoteKey) = k in held
    @Synchronized fun any() = held.isNotEmpty()

    /** Keys whose last news is older than the timeout; they are forgotten (the caller sends their "up"). */
    @Synchronized fun expired(now: Long): List<RemoteKey> {
        val out = held.filterValues { now - it > timeoutMs }.keys.toList()
        out.forEach { held.remove(it) }
        return out
    }
}

/**
 * Touchpad of the phone: a finger sliding by [stepPx] along one axis is one arrow; the dominant axis wins, the other one is
 * forgotten at each step (a diagonal slide does not add sideways moves). A tap is OK.
 */
class TouchpadMapper(private val stepPx: Float = 60f, private val maxStepsPerMove: Int = 8) {
    private var ax = 0f
    private var ay = 0f

    fun move(dx: Float, dy: Float): List<RemoteKey> {
        ax += dx; ay += dy
        val out = ArrayList<RemoteKey>()
        while (out.size < maxStepsPerMove) {
            val horizontal = kotlin.math.abs(ax) >= kotlin.math.abs(ay)
            val v = if (horizontal) ax else ay
            if (kotlin.math.abs(v) < stepPx) break
            out += when {
                horizontal && v > 0 -> RemoteKey.DPAD_RIGHT
                horizontal -> RemoteKey.DPAD_LEFT
                v > 0 -> RemoteKey.DPAD_DOWN
                else -> RemoteKey.DPAD_UP
            }
            if (horizontal) { ax -= if (v > 0) stepPx else -stepPx; ay = 0f } else { ay -= if (v > 0) stepPx else -stepPx; ax = 0f }
        }
        if (out.size >= maxStepsPerMove) { ax = 0f; ay = 0f }   // a wild fling: never a flood of arrows
        return out
    }

    fun tap(): RemoteKey = RemoteKey.DPAD_CENTER
    fun reset() { ax = 0f; ay = 0f }

    companion object {
        /** Stateless version for the /api/remote/pointer route: one relative move -> arrows (at most [max]). */
        fun steps(dx: Int, dy: Int, stepPx: Int = 60, max: Int = 10): List<RemoteKey> =
            TouchpadMapper(stepPx.toFloat(), max).move(dx.toFloat(), dy.toFloat())
    }
}

/** Text typed on the phone applied to a field as a live diff: extra characters are inserted, removed ones become DEL keys. */
object TextDiff {
    data class Edit(val deletes: Int, val insert: String)

    /** From what the field held before ([old]) to [new], assuming the cursor is at the end (typing, IME corrections). */
    fun between(old: String, new: String): Edit {
        var common = 0
        val n = minOf(old.length, new.length)
        while (common < n && old[common] == new[common]) common++
        return Edit(old.length - common, new.substring(common))
    }
}
