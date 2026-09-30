package castbridge.core.learn

import kotlin.math.*

/**
 * Drawing primitives of an illustration, in the figure's coordinates (w × h, y down). The TV (android.graphics.Canvas)
 * and the phone (Compose Canvas → native canvas) draw exactly these, scaled uniformly to fit: one layout, tested here.
 * Colors are ARGB ints (null = none).
 */
sealed class Op {
    data class Line(val x1: Double, val y1: Double, val x2: Double, val y2: Double, val color: Int, val width: Double, val dash: Boolean = false) : Op()
    data class Circle(val cx: Double, val cy: Double, val r: Double, val fill: Int?, val stroke: Int?, val width: Double) : Op()
    data class Rect(val x: Double, val y: Double, val w: Double, val h: Double, val fill: Int?, val stroke: Int?, val width: Double, val radius: Double = 0.0) : Op()
    /** Path in absolute commands (see [PathCmd]). */
    data class Path(val cmds: List<PathCmd>, val fill: Int?, val stroke: Int?, val width: Double, val dash: Boolean = false) : Op()
    /** Text: [x] is the anchor (start / middle / end), [y] the baseline. */
    data class Text(val x: Double, val y: Double, val text: String, val size: Double, val color: Int, val anchor: String = "middle", val bold: Boolean = false) : Op()
    /** Everything until [Unclip] is clipped to this rectangle. */
    data class Clip(val x: Double, val y: Double, val w: Double, val h: Double) : Op()
    object Unclip : Op()
}

sealed class PathCmd {
    data class M(val x: Double, val y: Double) : PathCmd()
    data class L(val x: Double, val y: Double) : PathCmd()
    data class C(val x1: Double, val y1: Double, val x2: Double, val y2: Double, val x: Double, val y: Double) : PathCmd()
    data class Q(val x1: Double, val y1: Double, val x: Double, val y: Double) : PathCmd()
    object Z : PathCmd()
}

class Scene(val w: Double, val h: Double, val ops: List<Op>) {
    /** Text boxes (approximate: [charWidth] × size per character), for layout tests (no overlapping labels). */
    fun textBoxes(charWidth: Double = CHAR_W): List<DoubleArray> = ops.filterIsInstance<Op.Text>().map { t ->
        val tw = textWidth(t.text, t.size, charWidth)
        val x0 = when (t.anchor) { "start" -> t.x; "end" -> t.x - tw; else -> t.x - tw / 2 }
        doubleArrayOf(x0, t.y - t.size * 0.8, x0 + tw, t.y + t.size * 0.2)
    }

    companion object {
        /** Average glyph width / font size for layout estimates (sans-serif, mixed case). */
        const val CHAR_W = 0.56
        private val INK = Palette.named.getValue("ink")
        private val GRID = 0xFFE3E7EC.toInt()
        private val AXIS = 0xFF4A5563.toInt()

        fun textWidth(s: String, size: Double, cw: Double = CHAR_W) = s.length * size * cw

        fun build(f: Figure): Scene = when (f) {
            is Figure.Shapes -> Scene(f.w, f.h, f.items.flatMap { shape(it) })
            is Figure.Svg -> Scene(f.w, f.h, f.paths.map { Op.Path(SvgPath.parse(it.d), Palette.color(it.fill), Palette.color(it.stroke), it.width) })
            is Figure.Plot -> plot(f)
            is Figure.Timeline -> timeline(f)
            is Figure.Bars -> bars(f)
            is Figure.Count -> count(f)
        }

        private fun c(s: String?, def: Int? = INK) = if (s == null) def else Palette.color(s)

        fun shape(s: Shape): List<Op> = when (s) {
            is Shape.Line -> {
                val col = c(s.color) ?: INK
                val out = mutableListOf<Op>(Op.Line(s.x1, s.y1, s.x2, s.y2, col, s.width, s.dash))
                if (s.arrow == "end" || s.arrow == "both") out += arrowHead(s.x1, s.y1, s.x2, s.y2, col, s.width)
                if (s.arrow == "start" || s.arrow == "both") out += arrowHead(s.x2, s.y2, s.x1, s.y1, col, s.width)
                out
            }
            is Shape.Circle -> listOf(Op.Circle(s.cx, s.cy, s.r, c(s.fill, null), c(s.stroke, null), s.width))
            is Shape.Rect -> listOf(Op.Rect(s.x, s.y, s.w, s.h, c(s.fill, null), c(s.stroke, null), s.width, s.radius))
            is Shape.Poly -> {
                val cmds = ArrayList<PathCmd>()
                for (k in s.pts.indices step 2) cmds += if (k == 0) PathCmd.M(s.pts[0], s.pts[1]) else PathCmd.L(s.pts[k], s.pts[k + 1])
                if (s.closed) cmds += PathCmd.Z
                listOf(Op.Path(cmds, c(s.fill, null), c(s.stroke, null), s.width))
            }
            is Shape.Text -> listOf(Op.Text(s.x, s.y, s.text, s.size, c(s.color) ?: INK, s.anchor, s.bold))
            is Shape.Angle -> angle(s)
            is Shape.Path -> listOf(Op.Path(SvgPath.parse(s.d), c(s.fill, null), c(s.stroke, null), s.width))
        }

        private fun arrowHead(x1: Double, y1: Double, x2: Double, y2: Double, col: Int, width: Double): Op {
            val a = atan2(y2 - y1, x2 - x1); val len = 6 + width * 3
            val p1x = x2 - len * cos(a - 0.45); val p1y = y2 - len * sin(a - 0.45)
            val p2x = x2 - len * cos(a + 0.45); val p2y = y2 - len * sin(a + 0.45)
            return Op.Path(listOf(PathCmd.M(x2, y2), PathCmd.L(p1x, p1y), PathCmd.L(p2x, p2y), PathCmd.Z), col, null, 0.0)
        }

        /** Angle in math orientation (counter-clockwise, y up) drawn in y-down coordinates. */
        private fun angle(s: Shape.Angle): List<Op> {
            val col = c(s.color) ?: INK
            fun px(deg: Double, r: Double) = s.x + r * cos(Math.toRadians(deg))
            fun py(deg: Double, r: Double) = s.y - r * sin(Math.toRadians(deg))
            var sweep = s.to - s.from
            while (sweep < 0) sweep += 360.0
            val out = ArrayList<Op>()
            if (s.right) {
                val r = s.r * 0.7
                out += Op.Path(listOf(PathCmd.M(px(s.from, r), py(s.from, r)),
                    PathCmd.L(px(s.from, r) + (px(s.to, r) - s.x), py(s.from, r) + (py(s.to, r) - s.y)), PathCmd.L(px(s.to, r), py(s.to, r))), null, col, 2.0)
            } else {
                val cmds = ArrayList<PathCmd>(); val n = max(6, (sweep / 10).toInt())
                for (k in 0..n) { val d = s.from + sweep * k / n; cmds += if (k == 0) PathCmd.M(px(d, s.r), py(d, s.r)) else PathCmd.L(px(d, s.r), py(d, s.r)) }
                out += Op.Path(cmds, null, col, 2.0)
            }
            s.label?.let { l ->
                val mid = s.from + sweep / 2; val r = s.r + 14
                out += Op.Text(px(mid, r), py(mid, r) + 5, l, 14.0, col)
            }
            return out
        }

        /** Axis step: [grid] enlarged (×2, ×5, ×10…) until there are at most [maxTicks] ticks. */
        fun niceStep(span: Double, grid: Double, maxTicks: Int = 10): Double {
            var st = if (grid > 0) grid else 1.0
            val mults = doubleArrayOf(2.0, 2.5, 2.0)
            var k = 0
            while (span / st > maxTicks) { st *= mults[k % 3]; k++ }
            return st
        }

        fun fmt(v: Double): String {
            val r = Math.round(v * 1000) / 1000.0
            return if (r == floor(r) && abs(r) < 1e9) r.toLong().toString() else r.toString().replace('.', ',')
        }

        private fun plot(f: Figure.Plot): Scene {
            val ml = 40.0; val mr = 14.0; val mt = 14.0; val mb = 30.0
            val pw = f.w - ml - mr; val ph = f.h - mt - mb
            fun X(x: Double) = ml + (x - f.xmin) / (f.xmax - f.xmin) * pw
            fun Y(y: Double) = mt + (f.ymax - y) / (f.ymax - f.ymin) * ph
            val ops = ArrayList<Op>()
            val sx = niceStep(f.xmax - f.xmin, f.grid); val sy = niceStep(f.ymax - f.ymin, f.grid)
            var gx = ceil(f.xmin / sx) * sx
            while (gx <= f.xmax + 1e-9) { ops += Op.Line(X(gx), mt, X(gx), mt + ph, GRID, 1.0); gx += sx }
            var gy = ceil(f.ymin / sy) * sy
            while (gy <= f.ymax + 1e-9) { ops += Op.Line(ml, Y(gy), ml + pw, Y(gy), GRID, 1.0); gy += sy }
            val ax = if (0.0 in f.ymin..f.ymax) Y(0.0) else mt + ph      // x axis
            val ay = if (0.0 in f.xmin..f.xmax) X(0.0) else ml           // y axis
            ops += Op.Line(ml, ax, ml + pw, ax, AXIS, 1.6); ops += arrowHead(ml, ax, ml + pw + 6, ax, AXIS, 1.2)
            ops += Op.Line(ay, mt + ph, ay, mt, AXIS, 1.6); ops += arrowHead(ay, mt + ph, ay, mt - 6, AXIS, 1.2)
            // Every label of the graph goes through place(): it is moved (or dropped, for tick labels) when it would
            // cover another label, so no text is ever drawn over another one (tested on every figure of the content).
            val boxes = ArrayList<DoubleArray>()
            fun boxOf(t: Op.Text): DoubleArray { val tw = textWidth(t.text, t.size); val x0 = when (t.anchor) { "start" -> t.x; "end" -> t.x - tw; else -> t.x - tw / 2 }
                return doubleArrayOf(x0, t.y - t.size * 0.8, x0 + tw, t.y + t.size * 0.2) }
            fun free(t: Op.Text) = boxOf(t).let { b -> boxes.none { overlap(it, b, 1.0) } }
            fun place(t: Op.Text): Boolean { if (!free(t)) return false; boxes += boxOf(t); ops += t; return true }
            val originShown = 0.0 in f.xmin..f.xmax && 0.0 in f.ymin..f.ymax
            // axis names first (they matter most), at the arrow ends
            f.xlabel?.let { place(Op.Text(ml + pw, ax - 7, it, 13.0, AXIS, "end", true)) || place(Op.Text(ml + pw, ax + 28, it, 13.0, AXIS, "end", true)) }
            f.ylabel?.let { place(Op.Text(ay + 7, mt + 10, it, 13.0, AXIS, "start", true)) || place(Op.Text(ay - 7, mt + 10, it, 13.0, AXIS, "end", true)) }
            if (originShown) place(Op.Text(ay - 5, ax + 14, "0", 11.0, AXIS, "end"))
            // tick labels: under the x axis, left of the y axis; 0 only once (the origin)
            gx = ceil(f.xmin / sx) * sx
            while (gx <= f.xmax + 1e-9) {
                if (abs(gx) > 1e-9 || !originShown) place(Op.Text(X(gx), min(ax + 15, f.h - 3), fmt(gx), 11.0, AXIS))
                gx += sx
            }
            gy = ceil(f.ymin / sy) * sy
            while (gy <= f.ymax + 1e-9) {
                if (abs(gy) > 1e-9 || !originShown) place(Op.Text(max(ay - 5, 30.0), Y(gy) + 4, fmt(gy), 11.0, AXIS, "end"))
                gy += sy
            }
            ops += Op.Clip(ml, mt, pw, ph)
            for (s in f.segments) ops += Op.Line(X(s.x1), Y(s.y1), X(s.x2), Y(s.y2), c(s.color) ?: AXIS, 1.6, s.dash)
            for (cv in f.curves) {
                val e = Expr.parse(cv.expr)
                val a = max(f.xmin, cv.from ?: f.xmin); val b = min(f.xmax, cv.to ?: f.xmax)
                val n = 240; val span = f.ymax - f.ymin
                val cmds = ArrayList<PathCmd>(); var pen = false
                val col = c(cv.color) ?: INK
                for (k in 0..n) {
                    val x = a + (b - a) * k / n
                    val y = e.eval(x)
                    if (!y.isFinite() || y > f.ymax + span * 2 || y < f.ymin - span * 2) { pen = false; continue }
                    cmds += if (pen) PathCmd.L(X(x), Y(y)) else PathCmd.M(X(x), Y(y)); pen = true
                }
                if (cmds.isNotEmpty()) ops += Op.Path(cmds, null, col, 2.6)
            }
            ops += Op.Unclip
            for (p in f.points) {
                val col = c(p.color) ?: INK
                ops += Op.Circle(X(p.x), Y(p.y), 4.0, col, null, 0.0)
                boxes += doubleArrayOf(X(p.x) - 4, Y(p.y) - 4, X(p.x) + 4, Y(p.y) + 4)
            }
            for (p in f.points) {
                val l = p.label ?: continue
                val col = c(p.color) ?: INK; val px = X(p.x); val py = Y(p.y)
                // north-east, then north-west, south-east, south-west of the point
                listOf(Op.Text(px + 7, py - 7, l, 13.0, col, "start", true), Op.Text(px - 7, py - 7, l, 13.0, col, "end", true),
                    Op.Text(px + 7, py + 17, l, 13.0, col, "start", true), Op.Text(px - 7, py + 17, l, 13.0, col, "end", true))
                    .firstOrNull { free(it) && boxOf(it).let { b -> b[0] >= 0 && b[2] <= f.w && b[1] >= 0 && b[3] <= f.h } }?.let { place(it) }
            }
            // curve labels: along the visible part of the curve, from its right end, at the first free place
            for (cv in f.curves) {
                val l = cv.label ?: continue
                val e = Expr.parse(cv.expr); val col = c(cv.color) ?: INK
                val b = min(f.xmax, cv.to ?: f.xmax); val a = max(f.xmin, cv.from ?: f.xmin)
                for (k in 0..60) {
                    val x = b - (b - a) * k / 60; val y = e.eval(x)
                    if (!y.isFinite() || y !in f.ymin..f.ymax) continue
                    val cand = listOf(Op.Text(min(X(x) - 4, ml + pw - 4), max(Y(y) - 8, mt + 12), l, 14.0, col, "end", true),
                        Op.Text(min(X(x) - 4, ml + pw - 4), min(Y(y) + 20, mt + ph - 2), l, 14.0, col, "end", true))
                    val ok = cand.firstOrNull { t -> free(t) && boxOf(t).let { bx -> bx[0] >= ml && bx[2] <= ml + pw && bx[1] >= mt && bx[3] <= mt + ph } }
                    if (ok != null) { place(ok); break }
                }
            }
            return Scene(f.w, f.h, ops)
        }

        /** Wraps [s] into lines of at most [maxW] units. */
        fun wrap(s: String, size: Double, maxW: Double): List<String> {
            val words = s.split(' ').filter { it.isNotEmpty() }
            val lines = ArrayList<String>(); var cur = ""
            for (w in words) {
                val t = if (cur.isEmpty()) w else "$cur $w"
                if (textWidth(t, size) <= maxW || cur.isEmpty()) cur = t else { lines += cur; cur = w }
            }
            if (cur.isNotEmpty()) lines += cur
            return lines
        }

        /**
         * Frieze: the axis in the middle, periods as coloured bands on it, events alternately above and below; a label
         * moves one row further away from the axis while it would overlap an earlier one (tested: no overlapping text).
         */
        private fun timeline(f: Figure.Timeline): Scene {
            val ml = 24.0; val mr = 24.0; val axisY = f.h / 2
            val span = (f.to - f.from).toDouble().coerceAtLeast(1.0)
            fun X(y: Double) = ml + (y - f.from) / span * (f.w - ml - mr)
            val ops = ArrayList<Op>()
            val bandH = 16.0
            for (p in f.periods) {
                val col = c(p.color) ?: INK
                ops += Op.Rect(X(p.from.toDouble()), axisY - bandH / 2, X(p.to.toDouble()) - X(p.from.toDouble()), bandH, (col and 0x00FFFFFF) or 0x66000000, col, 1.0, 4.0)
            }
            ops += Op.Line(ml - 8, axisY, f.w - mr + 8, axisY, INK, 2.5)
            ops += arrowHead(ml, axisY, f.w - mr + 14, axisY, INK, 2.0)
            ops += Op.Text(ml, axisY + bandH / 2 + 14, f.from.toString(), 11.0, AXIS, "start")
            ops += Op.Text(f.w - mr, axisY + bandH / 2 + 14, f.to.toString(), 11.0, AXIS, "end")
            val size = 12.0; val lineH = size * 1.2; val maxW = (f.w - ml - mr) / 3.2
            val placed = ArrayList<DoubleArray>()        // boxes already used (x0, y0, x1, y1)
            // period labels: inside the band row just above the axis area
            val periodRow = ArrayList<DoubleArray>()
            for (p in f.periods) {
                val cx = (X(p.from.toDouble()) + X(p.to.toDouble())) / 2
                val lines = wrap(p.label, 11.0, max(60.0, X(p.to.toDouble()) - X(p.from.toDouble())))
                val tw = lines.maxOf { textWidth(it, 11.0) }
                var y = axisY + bandH / 2 + 30
                var box = doubleArrayOf(cx - tw / 2, y - 11, cx + tw / 2, y - 11 + lines.size * 13.0)
                while (periodRow.any { overlap(it, box) }) { y += 14; box = doubleArrayOf(box[0], y - 11, box[2], y - 11 + lines.size * 13.0) }
                periodRow += box; placed += box
                lines.forEachIndexed { k, l -> ops += Op.Text(cx.coerceIn(ml + tw / 2, f.w - mr - tw / 2), y + k * 13.0, l, 11.0, c(p.color) ?: INK, "middle", true) }
            }
            f.events.sortedBy { it.year }.forEachIndexed { i, e ->
                val x = X(e.year.toDouble())
                val up = i % 2 == 0
                val lines = listOf(e.year.toString()) + wrap(e.label, size, maxW)
                val tw = lines.maxOf { textWidth(it, size) }
                val bh = lines.size * lineH
                val cx = x.coerceIn(ml + tw / 2 - 16, f.w - mr - tw / 2 + 16).coerceIn(tw / 2 + 2, f.w - tw / 2 - 2)
                var dist = 22.0
                fun boxAt(d: Double) = if (up) doubleArrayOf(cx - tw / 2, axisY - d - bh, cx + tw / 2, axisY - d)
                                       else doubleArrayOf(cx - tw / 2, axisY + d, cx + tw / 2, axisY + d + bh)
                var box = boxAt(dist)
                while (placed.any { overlap(it, box) }) { dist += lineH; box = boxAt(dist) }
                placed += box
                ops += Op.Line(x, axisY, x, if (up) box[3] + 2 else box[1] - 2, AXIS, 1.2)
                ops += Op.Circle(x, axisY, 4.5, 0xFFE53935.toInt(), 0xFFFFFFFF.toInt(), 1.5)
                lines.forEachIndexed { k, l ->
                    ops += Op.Text(cx, box[1] + (k + 1) * lineH - 3, l, size, if (k == 0) 0xFFC62828.toInt() else INK, "middle", k == 0)
                }
            }
            // Grow the figure if labels went beyond it (many events): nothing is ever cut off.
            val minY = placed.minOfOrNull { it[1] } ?: 0.0; val maxY = placed.maxOfOrNull { it[3] } ?: f.h
            val top = min(0.0, minY - 4); val bottom = max(f.h, maxY + 4)
            return if (top < 0 || bottom > f.h) Scene(f.w, bottom - top, ops.map { shiftY(it, -top) }) else Scene(f.w, f.h, ops)
        }

        fun overlap(a: DoubleArray, b: DoubleArray, pad: Double = 2.0) =
            a[0] < b[2] + pad && b[0] < a[2] + pad && a[1] < b[3] + pad && b[1] < a[3] + pad

        private fun shiftY(o: Op, d: Double): Op = when (o) {
            is Op.Line -> o.copy(y1 = o.y1 + d, y2 = o.y2 + d)
            is Op.Circle -> o.copy(cy = o.cy + d)
            is Op.Rect -> o.copy(y = o.y + d)
            is Op.Text -> o.copy(y = o.y + d)
            is Op.Clip -> o.copy(y = o.y + d)
            is Op.Path -> o.copy(cmds = o.cmds.map { when (it) {
                is PathCmd.M -> it.copy(y = it.y + d); is PathCmd.L -> it.copy(y = it.y + d)
                is PathCmd.C -> it.copy(y1 = it.y1 + d, y2 = it.y2 + d, y = it.y + d); is PathCmd.Q -> it.copy(y1 = it.y1 + d, y = it.y + d)
                PathCmd.Z -> it } })
            Op.Unclip -> o
        }

        private fun bars(f: Figure.Bars): Scene {
            val ml = 40.0; val mb = 34.0; val mt = 22.0; val mr = 10.0
            val maxV = (f.bars.maxOfOrNull { it.value } ?: 1.0).coerceAtLeast(1e-9)
            val step = niceStep(maxV, 1.0, 6)
            val top = ceil(maxV / step) * step
            val pw = f.w - ml - mr; val ph = f.h - mt - mb
            val ops = ArrayList<Op>()
            var g = 0.0
            while (g <= top + 1e-9) {
                val y = mt + ph - g / top * ph
                ops += Op.Line(ml, y, ml + pw, y, GRID, 1.0); ops += Op.Text(ml - 5, y + 4, fmt(g), 11.0, AXIS, "end"); g += step
            }
            val n = f.bars.size.coerceAtLeast(1); val slot = pw / n; val bw = slot * 0.6
            val labelSize = min(12.0, slot / (Scene.CHAR_W * (f.bars.maxOfOrNull { it.label.length } ?: 1)).coerceAtLeast(1.0)).coerceAtLeast(8.0)
            f.bars.forEachIndexed { i, b ->
                val x = ml + slot * i + (slot - bw) / 2; val hgt = b.value / top * ph
                ops += Op.Rect(x, mt + ph - hgt, bw, hgt, c(b.color) ?: INK, null, 0.0, 3.0)
                ops += Op.Text(x + bw / 2, mt + ph - hgt - 5, fmt(b.value), 12.0, INK, "middle", true)
                ops += Op.Text(x + bw / 2, mt + ph + 16, b.label, labelSize, INK)
            }
            ops += Op.Line(ml, mt + ph, ml + pw, mt + ph, AXIS, 1.6)
            f.unit?.let { ops += Op.Text(ml, mt - 8, it, 11.0, AXIS, "start") }
            return Scene(f.w, f.h, ops)
        }

        private fun count(f: Figure.Count): Scene {
            val per = f.perRow.coerceIn(1, 10); val rows = (f.n + per - 1) / per
            val cell = min(f.w / per, f.h / rows.coerceAtLeast(1)); val r = cell * 0.38
            val col = c(f.color) ?: INK
            val ops = ArrayList<Op>()
            val offX = (f.w - cell * min(per, f.n)) / 2; val offY = (f.h - cell * rows) / 2
            for (i in 0 until f.n) {
                val cx = offX + cell * (i % per) + cell / 2; val cy = offY + cell * (i / per) + cell / 2
                ops += when (f.shape) {
                    "square" -> Op.Rect(cx - r, cy - r, 2 * r, 2 * r, col, INK, 2.0, r * 0.2)
                    "triangle" -> Op.Path(listOf(PathCmd.M(cx, cy - r), PathCmd.L(cx + r, cy + r * 0.8), PathCmd.L(cx - r, cy + r * 0.8), PathCmd.Z), col, INK, 2.0)
                    "star" -> Op.Path(star(cx, cy, r), col, INK, 2.0)
                    "heart" -> Op.Path(heart(cx, cy, r), col, INK, 2.0)
                    else -> Op.Circle(cx, cy, r, col, INK, 2.0)
                }
            }
            return Scene(f.w, f.h, ops)
        }

        private fun star(cx: Double, cy: Double, r: Double): List<PathCmd> {
            val out = ArrayList<PathCmd>()
            for (k in 0 until 10) {
                val a = -PI / 2 + k * PI / 5; val rr = if (k % 2 == 0) r else r * 0.45
                out += if (k == 0) PathCmd.M(cx + rr * cos(a), cy + rr * sin(a)) else PathCmd.L(cx + rr * cos(a), cy + rr * sin(a))
            }
            out += PathCmd.Z; return out
        }

        private fun heart(cx: Double, cy: Double, r: Double): List<PathCmd> = listOf(
            PathCmd.M(cx, cy + r),
            PathCmd.C(cx - r * 1.6, cy - r * 0.1, cx - r * 0.6, cy - r * 1.3, cx, cy - r * 0.45),
            PathCmd.C(cx + r * 0.6, cy - r * 1.3, cx + r * 1.6, cy - r * 0.1, cx, cy + r), PathCmd.Z)
    }
}

/** SVG path data → absolute [PathCmd]s (M L H V C S Q T A Z, upper = absolute, lower = relative; arcs become cubics). */
object SvgPath {
    class Error(msg: String) : IllegalArgumentException(msg)

    fun parse(d: String): List<PathCmd> {
        val toks = Regex("[MmLlHhVvCcSsQqTtAaZz]|-?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][-+]?\\d+)?").findAll(d).map { it.value }.toList()
        val out = ArrayList<PathCmd>()
        var i = 0; var cmd = ' '
        var x = 0.0; var y = 0.0; var sx = 0.0; var sy = 0.0
        var lcx = 0.0; var lcy = 0.0; var lastC = ' '
        fun num(): Double = toks.getOrNull(i++)?.toDoubleOrNull() ?: throw Error("nombre attendu dans « ${d.take(40)} »")
        while (i < toks.size) {
            if (toks[i][0].isLetter()) { cmd = toks[i][0]; i++ } else if (cmd == ' ') throw Error("commande attendue")
            val rel = cmd.isLowerCase()
            val ox = if (rel) x else 0.0; val oy = if (rel) y else 0.0
            when (cmd.uppercaseChar()) {
                'M' -> { x = ox + num(); y = oy + num(); sx = x; sy = y; out += PathCmd.M(x, y); cmd = if (rel) 'l' else 'L' }
                'L' -> { x = ox + num(); y = oy + num(); out += PathCmd.L(x, y) }
                'H' -> { x = (if (rel) x else 0.0) + num(); out += PathCmd.L(x, y) }
                'V' -> { y = (if (rel) y else 0.0) + num(); out += PathCmd.L(x, y) }
                'C' -> { val a = ox + num(); val b = oy + num(); val c2 = ox + num(); val d2 = oy + num(); x = ox + num(); y = oy + num()
                    out += PathCmd.C(a, b, c2, d2, x, y); lcx = c2; lcy = d2 }
                'S' -> { val a = if (lastC in "CcSs") 2 * x - lcx else x; val b = if (lastC in "CcSs") 2 * y - lcy else y
                    val c2 = ox + num(); val d2 = oy + num(); x = ox + num(); y = oy + num(); out += PathCmd.C(a, b, c2, d2, x, y); lcx = c2; lcy = d2 }
                'Q' -> { val a = ox + num(); val b = oy + num(); x = ox + num(); y = oy + num(); out += PathCmd.Q(a, b, x, y); lcx = a; lcy = b }
                'T' -> { val a = if (lastC in "QqTt") 2 * x - lcx else x; val b = if (lastC in "QqTt") 2 * y - lcy else y
                    x = ox + num(); y = oy + num(); out += PathCmd.Q(a, b, x, y); lcx = a; lcy = b }
                'A' -> { val rx = num(); val ry = num(); val rot = num(); val large = num() != 0.0; val sweep = num() != 0.0
                    val nx = ox + num(); val ny = oy + num(); out += arc(x, y, rx, ry, rot, large, sweep, nx, ny); x = nx; y = ny }
                'Z' -> { out += PathCmd.Z; x = sx; y = sy }
                else -> throw Error("commande « $cmd » non prise en charge")
            }
            lastC = cmd
        }
        return out
    }

    /** SVG elliptical arc (endpoint form) → cubic Béziers (≤ 90° each). */
    private fun arc(x1: Double, y1: Double, rx0: Double, ry0: Double, rotDeg: Double, large: Boolean, sweep: Boolean, x2: Double, y2: Double): List<PathCmd> {
        if (rx0 == 0.0 || ry0 == 0.0) return listOf(PathCmd.L(x2, y2))
        val phi = Math.toRadians(rotDeg); val cp = cos(phi); val sp = sin(phi)
        val dx = (x1 - x2) / 2; val dy = (y1 - y2) / 2
        val x1p = cp * dx + sp * dy; val y1p = -sp * dx + cp * dy
        var rx = abs(rx0); var ry = abs(ry0)
        val lam = x1p * x1p / (rx * rx) + y1p * y1p / (ry * ry)
        if (lam > 1) { rx *= sqrt(lam); ry *= sqrt(lam) }
        val num = rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p
        val den = rx * rx * y1p * y1p + ry * ry * x1p * x1p
        var co = sqrt(max(0.0, num / den)); if (large == sweep) co = -co
        val cxp = co * rx * y1p / ry; val cyp = -co * ry * x1p / rx
        val cx = cp * cxp - sp * cyp + (x1 + x2) / 2; val cy = sp * cxp + cp * cyp + (y1 + y2) / 2
        fun ang(ux: Double, uy: Double, vx: Double, vy: Double): Double {
            val a = atan2(ux * vy - uy * vx, ux * vx + uy * vy); return a
        }
        val t1 = ang(1.0, 0.0, (x1p - cxp) / rx, (y1p - cyp) / ry)
        var dt = ang((x1p - cxp) / rx, (y1p - cyp) / ry, (-x1p - cxp) / rx, (-y1p - cyp) / ry)
        if (!sweep && dt > 0) dt -= 2 * PI else if (sweep && dt < 0) dt += 2 * PI
        val segs = ceil(abs(dt) / (PI / 2)).toInt().coerceAtLeast(1)
        val out = ArrayList<PathCmd>(); val d = dt / segs; val k = 4.0 / 3 * tan(d / 4)
        var t = t1
        fun pt(a: Double) = doubleArrayOf(cx + rx * cos(a) * cp - ry * sin(a) * sp, cy + rx * cos(a) * sp + ry * sin(a) * cp)
        fun der(a: Double) = doubleArrayOf(-rx * sin(a) * cp - ry * cos(a) * sp, -rx * sin(a) * sp + ry * cos(a) * cp)
        repeat(segs) {
            val p0 = pt(t); val p3 = pt(t + d); val d0 = der(t); val d3 = der(t + d)
            out += PathCmd.C(p0[0] + k * d0[0], p0[1] + k * d0[1], p3[0] - k * d3[0], p3[1] - k * d3[1], p3[0], p3[1])
            t += d
        }
        return out
    }
}
