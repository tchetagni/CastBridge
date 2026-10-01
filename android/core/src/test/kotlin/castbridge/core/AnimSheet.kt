package castbridge.core

import castbridge.core.learn.*
import java.awt.*
import java.awt.geom.Path2D
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Headless contact sheets of animations (Java2D): the frames come from the real [AnimatedFigure.frameAt], so a reviewer can
 * eyeball what the apps will draw without a device. Written to android/core/build/anim-sheets/<name>.png by the tests.
 */
object AnimSheet {
    private val PAPER = Color(0xFDFCF7)

    fun times(a: AnimatedFigure, max: Int = 8): List<Double> {
        val ts = if (a.stops.isNotEmpty() && !a.loop) (listOf(0.0) + a.stops.map { it.at }) else (0 until max).map { a.duration * it / max }
        return if (ts.size <= max + 1) ts else ts.filterIndexed { i, _ -> i % ((ts.size + max) / max) == 0 || i == ts.lastIndex }
    }

    fun render(name: String, a: AnimatedFigure, cols: Int = 3, scale: Double = 0.78): BufferedImage {
        val ts = times(a)
        val cw = (a.w * scale).toInt(); val ch = (a.h * scale).toInt(); val cap = 34; val pad = 10; val head = 30
        val rows = (ts.size + cols - 1) / cols
        val img = BufferedImage(cols * (cw + pad) + pad, head + rows * (ch + cap + pad) + pad, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.color = Color(0x1B2430); g.fillRect(0, 0, img.width, img.height)
        g.color = Color.WHITE; g.font = Font(Font.SANS_SERIF, Font.BOLD, 15)
        g.drawString("$name — ${"%.1f".format(a.duration)} s, ${a.sizeBytes} octets, ${a.elements.size} éléments, ${a.actionCount} actions" + if (a.loop) " (boucle)" else if (a.stepMode) " (par étapes)" else "", pad, 20)
        for ((k, t) in ts.withIndex()) {
            val x = pad + (k % cols) * (cw + pad); val y = head + (k / cols) * (ch + cap + pad)
            g.color = PAPER; g.fillRoundRect(x, y, cw, ch, 8, 8)
            val old = g.transform; val oc = g.clip
            g.clip = Rectangle(x, y, cw, ch)
            g.translate(x, y); draw(g, a.frameAt(t), scale); g.transform = old; g.clip = oc
            g.color = Color(0xC8D0DA); g.font = Font(Font.SANS_SERIF, Font.PLAIN, 11)
            val c = a.captionAt(t)?.takeIf { a.stops.isNotEmpty() && !a.loop }
            g.drawString("t = ${"%.1f".format(t)} s" + (c?.let { "  " + it.take(52) } ?: ""), x, y + ch + 14)
            if (c != null && c.length > 52) g.drawString(c.drop(52).take(62), x, y + ch + 27)
        }
        g.dispose()
        return img
    }

    fun write(dir: File, name: String, a: AnimatedFigure) {
        dir.mkdirs(); ImageIO.write(render(name, a), "png", File(dir, "$name.png"))
    }

    private fun col(c: Int) = Color((c shr 16) and 0xFF, (c shr 8) and 0xFF, c and 0xFF, (c ushr 24) and 0xFF)

    fun draw(g: Graphics2D, sc: Scene, s: Double) {
        val clips = ArrayDeque<java.awt.Shape?>()
        fun stroke(w: Double, dash: Boolean) = if (dash) BasicStroke((w * s).toFloat().coerceAtLeast(1f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f, floatArrayOf(6f * s.toFloat(), 5f * s.toFloat()), 0f)
            else BasicStroke((w * s).toFloat().coerceAtLeast(1f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        for (op in sc.ops) when (op) {
            is Op.Line -> { g.color = col(op.color); g.stroke = stroke(op.width, op.dash); g.drawLine((op.x1 * s).toInt(), (op.y1 * s).toInt(), (op.x2 * s).toInt(), (op.y2 * s).toInt()) }
            is Op.Circle -> { val r = op.r * s; val e = java.awt.geom.Ellipse2D.Double((op.cx - op.r) * s, (op.cy - op.r) * s, 2 * r, 2 * r)
                op.fill?.let { g.color = col(it); g.fill(e) }; op.stroke?.let { g.color = col(it); g.stroke = stroke(op.width, false); g.draw(e) } }
            is Op.Rect -> { val e = java.awt.geom.RoundRectangle2D.Double(op.x * s, op.y * s, op.w * s, op.h * s, op.radius * 2 * s, op.radius * 2 * s)
                op.fill?.let { g.color = col(it); g.fill(e) }; op.stroke?.let { g.color = col(it); g.stroke = stroke(op.width, false); g.draw(e) } }
            is Op.Path -> { val p = Path2D.Double()
                for (c in op.cmds) when (c) { is PathCmd.M -> p.moveTo(c.x * s, c.y * s); is PathCmd.L -> p.lineTo(c.x * s, c.y * s)
                    is PathCmd.C -> p.curveTo(c.x1 * s, c.y1 * s, c.x2 * s, c.y2 * s, c.x * s, c.y * s); is PathCmd.Q -> p.quadTo(c.x1 * s, c.y1 * s, c.x * s, c.y * s); PathCmd.Z -> p.closePath() }
                op.fill?.let { g.color = col(it); g.fill(p) }; op.stroke?.let { g.color = col(it); g.stroke = stroke(op.width, op.dash); g.draw(p) } }
            is Op.Text -> { g.font = Font(Font.SANS_SERIF, if (op.bold) Font.BOLD else Font.PLAIN, (op.size * s).toInt().coerceAtLeast(6))
                val w = g.fontMetrics.stringWidth(op.text); val x = when (op.anchor) { "start" -> op.x * s; "end" -> op.x * s - w; else -> op.x * s - w / 2 }
                g.color = col(op.color); g.drawString(op.text, x.toFloat(), (op.y * s).toFloat()) }
            is Op.Clip -> { clips.addLast(g.clip); g.clipRect((op.x * s).toInt(), (op.y * s).toInt(), (op.w * s).toInt() + 1, (op.h * s).toInt() + 1) }
            Op.Unclip -> g.clip = clips.removeLastOrNull()
        }
        g.stroke = BasicStroke(1f)
    }
}
