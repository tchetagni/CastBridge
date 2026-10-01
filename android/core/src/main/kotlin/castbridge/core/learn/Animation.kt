package castbridge.core.learn

import kotlin.math.*

/**
 * Vector keyframe animations (docs/LEARN.md § Animations): a base list of elements (the same shapes as [Figure.Shapes]) plus a
 * timeline of actions. [AnimatedFigure.frameAt] is a pure function of the time returning a [Scene], so the TV and the phone draw
 * frames with the very same [Op] renderers as the static figures. Nothing here depends on Android.
 */

enum class Ease(val key: String) {
    LINEAR("linear"), IN("in"), OUT("out"), IN_OUT("inout"), CUBIC("cubic");

    /** Monotonic on [0, 1], with at(0) = 0 and at(1) = 1. */
    fun at(p: Double): Double {
        val x = p.coerceIn(0.0, 1.0)
        return when (this) {
            LINEAR -> x
            IN -> x * x
            OUT -> 1 - (1 - x) * (1 - x)
            IN_OUT -> if (x < 0.5) 2 * x * x else 1 - 2 * (1 - x) * (1 - x)
            CUBIC -> if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
        }
    }

    companion object { fun of(k: String?) = values().firstOrNull { it.key == k } }
}

/** Animated property of an element. Colors are ARGB ints stored as exact doubles. */
enum class Prop { TX, TY, SCALE, ROT, ALPHA, FILL, STROKE, DRAW, TYPED, VALUE, WIPE;
    val isColor get() = this == FILL || this == STROKE
}

/** The segments of one property of one element, sorted, never overlapping: from the previous value (or [init]) to [to]. */
class Track(val prop: Prop, val init: Double, val t0: DoubleArray, val t1: DoubleArray, val to: DoubleArray, val ease: Array<Ease>) {
    val size get() = t0.size

    fun at(t: Double): Double {
        var cur = init
        for (k in t0.indices) {
            if (t < t0[k]) return cur
            if (t >= t1[k]) { cur = to[k]; continue }
            return cur + (to[k] - cur) * ease[k].at((t - t0[k]) / (t1[k] - t0[k]))
        }
        return cur
    }

    /** Color at [t], or null while no segment has started (the shape keeps its own colors). */
    fun colorAt(t: Double): Int? {
        if (size == 0 || t < t0[0]) return null
        var cur = init.toLong().toInt()
        for (k in t0.indices) {
            if (t < t0[k]) return cur
            val dst = to[k].toLong().toInt()
            if (t >= t1[k]) { cur = dst; continue }
            return mix(cur, dst, ease[k].at((t - t0[k]) / (t1[k] - t0[k])))
        }
        return cur
    }

    companion object {
        fun mix(a: Int, b: Int, p: Double): Int {
            var out = 0
            for (sh in intArrayOf(24, 16, 8, 0)) {
                val x = (a ushr sh) and 0xFF; val y = (b ushr sh) and 0xFF
                out = out or ((x + (y - x) * p).roundToInt().coerceIn(0, 255) shl sh)
            }
            return out
        }
    }
}

/** A pause of the step mode: the animation plays until [at] seconds, then waits; [say] is the caption of the segment that ends there. */
data class AnimStop(val at: Double, val say: String)

class AnimElement(
    val id: String, val group: String?, val shape: Shape, val ops: List<Op>, val pivotX: Double, val pivotY: Double,
    val alpha0: Double, val draw0: Double, val typed0: Double, val value0: Double, val dec: Int, val tracks: Array<Track?>,
    /** Wipe: share of the element revealed (a clip growing in direction [wipeDir]: right | left | up | down); 1 = all. */
    val wipe0: Double = 1.0, val wipeDir: String = "right",
) {
    /** x0, y0, x1, y1 of the base drawing. */
    val bbox: DoubleArray = AnimGeom.bbox(ops)
    val fmt: String? = (shape as? Shape.Text)?.text?.takeIf { "{v}" in it }
    /** Stroke geometry for the progressive drawing, per op (null when the op has no stroke); built only when the element is drawn progressively. */
    internal val flats: Array<AnimGeom.Flat?>? =
        if (draw0 < 1.0 || tracks[Prop.DRAW.ordinal] != null) Array(ops.size) { AnimGeom.flat(ops[it]) } else null
    fun track(p: Prop) = tracks[p.ordinal]
}

class AnimatedFigure(
    val w: Double, val h: Double, val elements: List<AnimElement>, val stops: List<AnimStop>,
    val stepMode: Boolean, val loop: Boolean, val duration: Double, val sizeBytes: Int, val actionCount: Int,
) {
    /** The scene at [t] seconds (clamped to [0, duration], or wrapped when [loop]). Pure and deterministic. */
    fun frameAt(t: Double): Scene {
        val tt = norm(t)
        val out = ArrayList<Op>(elements.size * 2)
        for (el in elements) render(el, tt, out)
        return Scene(w, h, out)
    }

    fun norm(t: Double): Double = when {
        t.isNaN() || t <= 0.0 -> 0.0
        loop && duration > 0 -> t % duration
        else -> min(t, duration)
    }

    /** First pause strictly after [t], or null. */
    fun nextStopAfter(t: Double): Double? = stops.firstOrNull { it.at > t + EPS }?.at
    /** Last pause strictly before [t]; 0.0 (the start) when only the beginning precedes; null at the very start. */
    fun prevStopBefore(t: Double): Double? = stops.lastOrNull { it.at < t - EPS }?.at ?: if (t > EPS) 0.0 else null
    /** Index of the step whose segment contains [t] (the stop reached counts for its own step); -1 without steps. */
    fun stepIndexAt(t: Double): Int = if (stops.isEmpty()) -1 else stops.indexOfFirst { it.at >= t - EPS }.let { if (it < 0) stops.lastIndex else it }
    fun captionAt(t: Double): String? = stops.getOrNull(stepIndexAt(t))?.say
    /** Captions in order: the step list shown as text (reduced motion, accessibility). */
    val captions: List<String> get() = stops.map { it.say }

    private fun render(el: AnimElement, t: Double, out: MutableList<Op>) {
        fun num(p: Prop, init: Double) = el.track(p)?.at(t) ?: init
        val alpha = num(Prop.ALPHA, el.alpha0)
        if (alpha <= 0.004) return
        val s = num(Prop.SCALE, 1.0); val rot = num(Prop.ROT, 0.0); val tx = num(Prop.TX, 0.0); val ty = num(Prop.TY, 0.0)
        val draw = num(Prop.DRAW, el.draw0).coerceIn(0.0, 1.0); val typed = num(Prop.TYPED, el.typed0).coerceIn(0.0, 1.0)
        val value = num(Prop.VALUE, el.value0)
        val fillOv = el.track(Prop.FILL)?.colorAt(t); val strokeOv = el.track(Prop.STROKE)?.colorAt(t)
        val wipe = if (rot != 0.0) 1.0 else num(Prop.WIPE, el.wipe0).coerceIn(0.0, 1.0)
        if (wipe <= 0.0) return
        val xf = if (s == 1.0 && rot == 0.0 && tx == 0.0 && ty == 0.0) null else Xf(el.pivotX, el.pivotY, tx, ty, s, rot)
        val drawing = draw < 1.0 - 1e-9
        val clipped = wipe < 1.0 - 1e-9
        if (clipped) out += wipeClip(el, wipe, xf)
        for ((i, op) in el.ops.withIndex()) {
            if (drawing) {
                val fl = el.flats?.get(i)
                if (fl != null && op !is Op.Text) {
                    if (draw <= 1e-9) continue
                    val col = strokeColor(op, strokeOv) ?: continue
                    out += place(fl.cut(draw, col, width(op), dash(op)), xf, null, null, el, value, typed, alpha)
                }
                if (op !is Op.Text) continue
            }
            val o = place(op, xf, fillOv, strokeOv, el, value, typed, alpha)
            if (!(o is Op.Text && o.text.isEmpty())) out += o
        }
        if (clipped) out += Op.Unclip
    }

    private fun wipeClip(el: AnimElement, wipe: Double, xf: Xf?): Op.Clip {
        val b = el.bbox; val pad = 4.0
        val x0 = b[0] - pad; val y0 = b[1] - pad; val x1 = b[2] + pad; val y1 = b[3] + pad
        val cx0: Double; val cy0: Double; val cx1: Double; val cy1: Double
        when (el.wipeDir) {
            "left" -> { cx0 = x1 - (x1 - x0) * wipe; cy0 = y0; cx1 = x1; cy1 = y1 }
            "up" -> { cx0 = x0; cy0 = y1 - (y1 - y0) * wipe; cx1 = x1; cy1 = y1 }
            "down" -> { cx0 = x0; cy0 = y0; cx1 = x1; cy1 = y0 + (y1 - y0) * wipe }
            else -> { cx0 = x0; cy0 = y0; cx1 = x0 + (x1 - x0) * wipe; cy1 = y1 }
        }
        val ax = xf?.x(cx0, cy0) ?: cx0; val ay = xf?.y(cx0, cy0) ?: cy0; val bx = xf?.x(cx1, cy1) ?: cx1; val by = xf?.y(cx1, cy1) ?: cy1
        return Op.Clip(ax, ay, bx - ax, by - ay)
    }

    private fun strokeColor(op: Op, ov: Int?): Int? = when (op) {
        is Op.Line -> ov ?: op.color
        is Op.Circle -> op.stroke?.let { ov ?: it }
        is Op.Rect -> op.stroke?.let { ov ?: it }
        is Op.Path -> op.stroke?.let { ov ?: it }
        else -> null
    }
    private fun width(op: Op) = when (op) { is Op.Line -> op.width; is Op.Circle -> op.width; is Op.Rect -> op.width; is Op.Path -> op.width; else -> 2.0 }
    private fun dash(op: Op) = when (op) { is Op.Line -> op.dash; is Op.Path -> op.dash; else -> false }

    /** [op] with the element's transform, colors and opacity applied; text revealed / counted. */
    private fun place(op: Op, xf: Xf?, fillOv: Int?, strokeOv: Int?, el: AnimElement, value: Double, typed: Double, alpha: Double): Op {
        fun f(base: Int?) = base?.let { mulAlpha(fillOv ?: it, alpha) }
        fun st(base: Int?) = base?.let { mulAlpha(strokeOv ?: it, alpha) }
        return when (op) {
            is Op.Line -> {
                val a = mulAlpha(strokeOv ?: op.color, alpha)
                if (xf == null) op.copy(color = a) else op.copy(x1 = xf.x(op.x1, op.y1), y1 = xf.y(op.x1, op.y1), x2 = xf.x(op.x2, op.y2), y2 = xf.y(op.x2, op.y2), color = a)
            }
            is Op.Circle -> if (xf == null) op.copy(fill = f(op.fill), stroke = st(op.stroke))
                else op.copy(cx = xf.x(op.cx, op.cy), cy = xf.y(op.cx, op.cy), r = op.r * xf.s, fill = f(op.fill), stroke = st(op.stroke))
            is Op.Rect -> when {
                xf == null -> op.copy(fill = f(op.fill), stroke = st(op.stroke))
                xf.rot == 0.0 -> op.copy(x = xf.x(op.x, op.y), y = xf.y(op.x, op.y), w = op.w * xf.s, h = op.h * xf.s, fill = f(op.fill), stroke = st(op.stroke), radius = op.radius * xf.s)
                else -> Op.Path(listOf(PathCmd.M(xf.x(op.x, op.y), xf.y(op.x, op.y)), PathCmd.L(xf.x(op.x + op.w, op.y), xf.y(op.x + op.w, op.y)),
                    PathCmd.L(xf.x(op.x + op.w, op.y + op.h), xf.y(op.x + op.w, op.y + op.h)), PathCmd.L(xf.x(op.x, op.y + op.h), xf.y(op.x, op.y + op.h)), PathCmd.Z),
                    f(op.fill), st(op.stroke), op.width)
            }
            is Op.Path -> op.copy(cmds = if (xf == null) op.cmds else op.cmds.map { xf.cmd(it) }, fill = f(op.fill), stroke = st(op.stroke))
            is Op.Text -> {
                var text = if (el.fmt != null) el.fmt.replace("{v}", formatValue(value, el.dec)) else op.text
                if (typed < 1.0 - 1e-9) text = text.substring(0, ceil(text.length * typed - 1e-9).toInt().coerceIn(0, text.length))
                val col = mulAlpha(fillOv ?: op.color, alpha)
                if (xf == null) op.copy(text = text, color = col) else op.copy(x = xf.x(op.x, op.y), y = xf.y(op.x, op.y), size = op.size * xf.s, text = text, color = col)
            }
            is Op.Clip, Op.Unclip -> op
        }
    }

    /** Rotation (degrees, clockwise on screen) and uniform scale about the pivot, then translation. */
    internal class Xf(val px: Double, val py: Double, val tx: Double, val ty: Double, val s: Double, val rot: Double) {
        private val c = cos(Math.toRadians(rot)); private val sn = sin(Math.toRadians(rot))
        fun x(x: Double, y: Double) = px + s * (c * (x - px) - sn * (y - py)) + tx
        fun y(x: Double, y: Double) = py + s * (sn * (x - px) + c * (y - py)) + ty
        fun cmd(k: PathCmd): PathCmd = when (k) {
            is PathCmd.M -> PathCmd.M(x(k.x, k.y), y(k.x, k.y)); is PathCmd.L -> PathCmd.L(x(k.x, k.y), y(k.x, k.y))
            is PathCmd.C -> PathCmd.C(x(k.x1, k.y1), y(k.x1, k.y1), x(k.x2, k.y2), y(k.x2, k.y2), x(k.x, k.y), y(k.x, k.y))
            is PathCmd.Q -> PathCmd.Q(x(k.x1, k.y1), y(k.x1, k.y1), x(k.x, k.y), y(k.x, k.y)); PathCmd.Z -> PathCmd.Z
        }
    }

    companion object {
        const val EPS = 1e-6
        fun mulAlpha(c: Int, a: Double): Int = if (a >= 0.999) c else ((((c ushr 24) * a).roundToInt().coerceIn(0, 255)) shl 24) or (c and 0xFFFFFF)
        fun formatValue(v: Double, dec: Int): String {
            if (dec <= 0) return Math.round(v).toString()
            val k = 10.0.pow(dec); val r = Math.round(v * k) / k
            return String.format(java.util.Locale.ROOT, "%.${dec}f", r).replace('.', ',')
        }
    }
}

/** Geometry helpers: bounding boxes and flattened strokes (for the progressive drawing). */
object AnimGeom {
    class Flat(val sub: List<DoubleArray>, val total: Double) {
        /** The first [p] (0..1) of the stroke's length as a path op. */
        fun cut(p: Double, color: Int, width: Double, dash: Boolean): Op {
            var left = total * p
            val cmds = ArrayList<PathCmd>()
            for (s in sub) {
                if (left <= 0) break
                cmds += PathCmd.M(s[0], s[1])
                var k = 2
                while (k < s.size) {
                    val d = hypot(s[k] - s[k - 2], s[k + 1] - s[k - 1])
                    if (d <= left) { cmds += PathCmd.L(s[k], s[k + 1]); left -= d }
                    else { val q = if (d > 0) left / d else 0.0
                        cmds += PathCmd.L(s[k - 2] + (s[k] - s[k - 2]) * q, s[k - 1] + (s[k + 1] - s[k - 1]) * q); left = 0.0; break }
                    k += 2
                }
            }
            return Op.Path(cmds, null, color, width, dash)
        }
    }

    private fun length(sub: List<DoubleArray>): Double = sub.sumOf { s -> (2 until s.size step 2).sumOf { hypot(s[it] - s[it - 2], s[it + 1] - s[it - 1]) } }

    fun flat(op: Op): Flat? {
        val sub: List<DoubleArray> = when (op) {
            is Op.Line -> listOf(doubleArrayOf(op.x1, op.y1, op.x2, op.y2))
            is Op.Circle -> if (op.stroke == null) return null else listOf(DoubleArray(2 * 65).also { a -> for (k in 0..64) {
                val ang = -PI / 2 + 2 * PI * k / 64; a[2 * k] = op.cx + op.r * cos(ang); a[2 * k + 1] = op.cy + op.r * sin(ang) } })
            is Op.Rect -> if (op.stroke == null) return null else listOf(doubleArrayOf(op.x, op.y, op.x + op.w, op.y, op.x + op.w, op.y + op.h, op.x, op.y + op.h, op.x, op.y))
            is Op.Path -> if (op.stroke == null) return null else flatten(op.cmds)
            else -> return null
        }
        return Flat(sub, length(sub))
    }

    fun flatten(cmds: List<PathCmd>): List<DoubleArray> {
        val subs = ArrayList<DoubleArray>(); var cur = ArrayList<Double>(); var cx = 0.0; var cy = 0.0; var sx = 0.0; var sy = 0.0
        fun flush() { if (cur.size >= 4) subs += cur.toDoubleArray(); cur = ArrayList() }
        for (c in cmds) when (c) {
            is PathCmd.M -> { flush(); cx = c.x; cy = c.y; sx = cx; sy = cy; cur += cx; cur += cy }
            is PathCmd.L -> { cx = c.x; cy = c.y; cur += cx; cur += cy }
            is PathCmd.C -> { for (k in 1..12) { val u = k / 12.0; val v = 1 - u
                cur += v * v * v * cx + 3 * v * v * u * c.x1 + 3 * v * u * u * c.x2 + u * u * u * c.x
                cur += v * v * v * cy + 3 * v * v * u * c.y1 + 3 * v * u * u * c.y2 + u * u * u * c.y }; cx = c.x; cy = c.y }
            is PathCmd.Q -> { for (k in 1..10) { val u = k / 10.0; val v = 1 - u
                cur += v * v * cx + 2 * v * u * c.x1 + u * u * c.x; cur += v * v * cy + 2 * v * u * c.y1 + u * u * c.y }; cx = c.x; cy = c.y }
            PathCmd.Z -> { cur += sx; cur += sy; cx = sx; cy = sy }
        }
        flush()
        return subs
    }

    fun bbox(ops: List<Op>): DoubleArray {
        var x0 = Double.MAX_VALUE; var y0 = Double.MAX_VALUE; var x1 = -Double.MAX_VALUE; var y1 = -Double.MAX_VALUE
        fun pt(x: Double, y: Double) { x0 = min(x0, x); y0 = min(y0, y); x1 = max(x1, x); y1 = max(y1, y) }
        for (op in ops) when (op) {
            is Op.Line -> { pt(op.x1, op.y1); pt(op.x2, op.y2) }
            is Op.Circle -> { pt(op.cx - op.r, op.cy - op.r); pt(op.cx + op.r, op.cy + op.r) }
            is Op.Rect -> { pt(op.x, op.y); pt(op.x + op.w, op.y + op.h) }
            is Op.Path -> for (c in op.cmds) when (c) { is PathCmd.M -> pt(c.x, c.y); is PathCmd.L -> pt(c.x, c.y)
                is PathCmd.C -> { pt(c.x1, c.y1); pt(c.x2, c.y2); pt(c.x, c.y) }; is PathCmd.Q -> { pt(c.x1, c.y1); pt(c.x, c.y) }; PathCmd.Z -> {} }
            is Op.Text -> { val tw = Scene.textWidth(op.text, op.size); val a = when (op.anchor) { "start" -> op.x; "end" -> op.x - tw; else -> op.x - tw / 2 }
                pt(a, op.y - op.size * 0.8); pt(a + tw, op.y + op.size * 0.2) }
            else -> {}
        }
        return if (x0 > x1) doubleArrayOf(0.0, 0.0, 0.0, 0.0) else doubleArrayOf(x0, y0, x1, y1)
    }
}
