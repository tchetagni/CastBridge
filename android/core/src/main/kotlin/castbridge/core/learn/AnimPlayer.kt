package castbridge.core.learn

import kotlin.math.min

/** Global « Réduire les animations » setting: the app switch, plus Android's remove-animations (set by the apps). */
object AnimSettings {
    @Volatile var appReduce = false
    @Volatile var systemReduce = false
    val reduce get() = appReduce || systemReduce
}

/**
 * Playback state of one [AnimatedFigure], independent of Android: the TV view and the phone composable feed it clock
 * ticks and key presses and draw [scene]. Step mode: [next] plays up to the next stop and waits there; reduced motion:
 * no tweening at all, [next] / [prev] jump from one stop frame to the next.
 */
class AnimPlayer(val anim: AnimatedFigure, reduced: Boolean = AnimSettings.reduce) {
    var reduced = reduced
        set(v) { if (v != field) { field = v; playing = false; target = null; if (v) t = if (anim.stepMode) 0.0 else anim.duration } }
    /** Current time, in seconds. */
    var t = if (reduced && !anim.stepMode) anim.duration else 0.0
        private set
    var playing = false
        private set
    /** True once a one-shot animation reached its end. */
    val finished get() = !anim.loop && t >= anim.duration - AnimatedFigure.EPS
    private var target: Double? = null
    private var lastNanos = 0L
    private var shownKey = -1L

    val stepIndex get() = anim.stepIndexAt(t)
    val stepCount get() = anim.stops.size
    val caption get() = anim.captionAt(t)
    val canStep get() = anim.stops.isNotEmpty()

    /** Frame at the current time. */
    fun scene(): Scene = anim.frameAt(t)

    /** Starts (or resumes) from the point where it stopped; restarts at the end. Auto mode: plays to the end (or loops). */
    fun play() {
        if (reduced) { next(); return }
        if (finished) t = 0.0
        if (anim.stepMode) { if (target == null) target = anim.nextStopAfter(t) ?: anim.duration }
        playing = true; lastNanos = 0L
    }
    fun pause() { playing = false }
    /** OK button: play / pause (auto mode); next step (step mode). */
    fun toggle() { if (anim.stepMode || reduced) { if (playing) skipToTarget() else if (finished) replay() else next() } else if (playing) pause() else play() }
    fun replay() { t = 0.0; target = null; playing = false; if (!anim.stepMode && !reduced) play() }

    /** Step mode: play to the next stop. Otherwise (auto mode with captions): jump to the next stop. */
    fun next() {
        val nx = anim.nextStopAfter(t)
        when {
            nx == null -> { if (finished) replay() }
            reduced || !anim.stepMode -> { t = nx; target = null; playing = false }
            else -> { target = nx; playing = true; lastNanos = 0L }
        }
    }
    /** Previous stop frame (no tweening), paused. Without stops: a tenth of the duration back. */
    fun prev() {
        playing = false; target = null
        t = if (anim.stops.isNotEmpty()) anim.prevStopBefore(t) ?: 0.0 else maxOf(0.0, t - anim.duration / 10)
    }
    fun seek(sec: Double) { t = anim.norm(sec); target = null; if (anim.stepMode) playing = false }
    private fun skipToTarget() { target?.let { t = it }; target = null; playing = false }

    /**
     * Advances the clock to [nowNanos] (monotonic) and tells whether a new frame must be drawn. At most [fps] frames per
     * second: ticks that come sooner are ignored; after a hiccup the time jumps by at most [maxStepSec] (frames are dropped,
     * not played in slow motion catch-up). The view calls this from its choreographer / frame callback.
     */
    fun advance(nowNanos: Long, fps: Int = 30, maxStepSec: Double = 0.1): Boolean {
        if (!playing) { lastNanos = 0L; return consumeDirty() }
        if (lastNanos == 0L) { lastNanos = nowNanos; return true }
        val minGap = 1_000_000_000L / fps - 2_000_000L
        if (nowNanos - lastNanos < minGap) return false
        val dt = min((nowNanos - lastNanos) / 1e9, maxStepSec); lastNanos = nowNanos
        t += dt
        val stop = target
        if (stop != null && t >= stop) { t = stop; target = null; playing = false }
        else if (anim.loop) t %= anim.duration
        else if (t >= anim.duration) { t = anim.duration; playing = false }
        return true
    }

    private fun consumeDirty(): Boolean { val k = (t * 1000).toLong(); val d = k != shownKey; shownKey = k; return d }
}
