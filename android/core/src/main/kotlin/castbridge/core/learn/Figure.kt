package castbridge.core.learn

/**
 * Structured illustration, drawn with Canvas on the TV and on the phone (no image files, a few hundred bytes each).
 * [Scene.build] turns any figure into drawing primitives in the figure's own coordinates (w × h, y pointing down,
 * like SVG); the apps only scale and draw them. Formats: docs/LEARN.md § Illustrations.
 */
sealed class Figure {
    abstract val w: Double
    abstract val h: Double

    /** Free drawing: geometry figures, schemas, simplified maps. */
    data class Shapes(override val w: Double, override val h: Double, val items: List<Shape>) : Figure()
    /** Function graph / economic curves: axes, grid, curves y = f(x), points, segments. */
    data class Plot(
        val xmin: Double, val xmax: Double, val ymin: Double, val ymax: Double,
        val grid: Double = 1.0, val xlabel: String? = null, val ylabel: String? = null,
        val curves: List<Curve> = emptyList(), val points: List<PlotPoint> = emptyList(), val segments: List<PlotSegment> = emptyList(),
        override val w: Double = 400.0, override val h: Double = 260.0,
    ) : Figure()
    /** Chronological frieze. */
    data class Timeline(val from: Int, val to: Int, val events: List<Event>, val periods: List<Period> = emptyList(),
                        override val w: Double = 480.0, override val h: Double = 240.0) : Figure()
    /** Bar chart. */
    data class Bars(val bars: List<Bar>, val unit: String? = null, override val w: Double = 400.0, override val h: Double = 240.0) : Figure()
    /** [n] identical objects to count (nursery, early primary). */
    data class Count(val n: Int, val shape: String, val color: String, val perRow: Int = 5,
                     override val w: Double = 400.0, override val h: Double = 200.0) : Figure()
    /** Embedded vector drawing: SVG path data only (M L H V C S Q T A Z, absolute or relative). */
    data class Svg(override val w: Double, override val h: Double, val paths: List<SvgItem>) : Figure()

    data class Curve(val expr: String, val color: String = "blue", val label: String? = null, val from: Double? = null, val to: Double? = null)
    data class PlotPoint(val x: Double, val y: Double, val label: String? = null, val color: String = "red")
    data class PlotSegment(val x1: Double, val y1: Double, val x2: Double, val y2: Double, val color: String = "grey", val dash: Boolean = true, val label: String? = null)
    data class Event(val year: Int, val label: String)
    data class Period(val from: Int, val to: Int, val label: String, val color: String = "orange")
    data class Bar(val label: String, val value: Double, val color: String = "blue")
    data class SvgItem(val d: String, val fill: String? = null, val stroke: String? = "ink", val width: Double = 2.0)
}

/** Items of [Figure.Shapes]. Colors: names of [Palette] or #RRGGBB. */
sealed class Shape {
    data class Line(val x1: Double, val y1: Double, val x2: Double, val y2: Double, val color: String = "ink", val width: Double = 2.0,
                    val dash: Boolean = false, val arrow: String = "none") : Shape()
    data class Circle(val cx: Double, val cy: Double, val r: Double, val fill: String? = null, val stroke: String? = "ink", val width: Double = 2.0) : Shape()
    data class Rect(val x: Double, val y: Double, val w: Double, val h: Double, val fill: String? = null, val stroke: String? = "ink",
                    val width: Double = 2.0, val radius: Double = 0.0) : Shape()
    data class Poly(val pts: List<Double>, val fill: String? = null, val stroke: String? = "ink", val width: Double = 2.0, val closed: Boolean = true) : Shape()
    data class Text(val x: Double, val y: Double, val text: String, val size: Double = 16.0, val color: String = "ink",
                    val anchor: String = "middle", val bold: Boolean = false) : Shape()
    /** Angle mark at vertex (x, y) from direction [from] to [to] (degrees, counter-clockwise, 0 = right); [right] = square mark. */
    data class Angle(val x: Double, val y: Double, val from: Double, val to: Double, val r: Double = 22.0, val right: Boolean = false,
                     val label: String? = null, val color: String = "red") : Shape()
    data class Path(val d: String, val fill: String? = null, val stroke: String? = "ink", val width: Double = 2.0) : Shape()
}

/** Named colors of the illustrations (drawn on a light « paper » card). */
object Palette {
    val named: Map<String, Int> = mapOf(
        "ink" to 0xFF1A2330.toInt(), "black" to 0xFF000000.toInt(), "white" to 0xFFFFFFFF.toInt(), "grey" to 0xFF8A94A0.toInt(),
        "lightgrey" to 0xFFD5DAE0.toInt(), "red" to 0xFFE53935.toInt(), "blue" to 0xFF1E6FD9.toInt(), "green" to 0xFF2E9D48.toInt(),
        "yellow" to 0xFFFDD835.toInt(), "orange" to 0xFFFB8C00.toInt(), "purple" to 0xFF8E24AA.toInt(), "brown" to 0xFF795548.toInt(),
        "pink" to 0xFFEC6FA8.toInt(), "cyan" to 0xFF00ACC1.toInt(), "lightblue" to 0xFFBBDEFB.toInt(), "lightgreen" to 0xFFC8E6C9.toInt(),
        "lightyellow" to 0xFFFFF59D.toInt(), "lightorange" to 0xFFFFE0B2.toInt(), "paper" to 0xFFFDFCF7.toInt(),
    )

    /** ARGB, or null for "none"/unknown. */
    fun color(s: String?): Int? {
        if (s == null || s == "none") return null
        named[s]?.let { return it }
        if (s.length == 7 && s[0] == '#') return s.substring(1).toLongOrNull(16)?.let { (0xFF000000 or it).toInt() }
        return null
    }
    fun valid(s: String?) = s == null || s == "none" || color(s) != null
}
