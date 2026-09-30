package castbridge.receiver

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.text.style.BulletSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import castbridge.core.learn.Markdown
import castbridge.core.learn.Op
import castbridge.core.learn.PathCmd
import castbridge.core.learn.Scene
import castbridge.core.learn.Tex
import castbridge.core.learn.TexLayout

/**
 * Sizes of « Apprendre » on the TV. Everything is laid out on a 1280 × 720 design grid scaled to the real screen
 * ([u] pixels per design unit), so a 1280x720 mdpi TV and a 1920x1080 xhdpi TV show exactly the same page (the density
 * is ignored on purpose: text readable at 3 m whatever the panel). [scale] enlarges the text in « mode classe ».
 */
class LearnStyle(val act: Activity) {
    val u: Float = act.resources.displayMetrics.let { minOf(it.widthPixels / 1280f, it.heightPixels / 720f) }
    var scale = 1f
    fun px(v: Float): Int = Math.round(v * u)
    fun px(v: Int): Int = px(v.toFloat())
    fun size(tv: TextView, v: Float) = tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, v * u * scale)

    fun text(s: CharSequence, size: Float = 26f, color: Int = Color.WHITE, bold: Boolean = false, lines: Int = 0): TextView = TextView(act).apply {
        text = s; setTextColor(color); size(this, size); if (bold) typeface = Typeface.DEFAULT_BOLD
        if (lines > 0) { maxLines = lines; ellipsize = TextUtils.TruncateAt.END }
        setLineSpacing(0f, 1.12f); includeFontPadding = true
    }

    fun rounded(fill: Int, radius: Float = 14f, stroke: Int = 0, strokeW: Float = 0f) = GradientDrawable().apply {
        cornerRadius = px(radius).toFloat(); setColor(fill); if (strokeW > 0) setStroke(px(strokeW).coerceAtLeast(1), stroke)
    }

    /** Focus look shared by every button and tile: accent frame + light fill, never a size change that could cover a neighbour. */
    fun focusable(v: View, fill: Int = CARD, radius: Float = 14f) {
        v.isFocusable = true; v.isFocusableInTouchMode = true; v.isClickable = true
        v.background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), rounded(FOCUS, radius, ACCENT, 4f))
            addState(intArrayOf(android.R.attr.state_pressed), rounded(FOCUS, radius, ACCENT, 4f))
            addState(intArrayOf(), rounded(fill, radius))
        }
    }

    /** A wide focusable button with a label (and an optional second line). */
    fun button(label: CharSequence, sub: CharSequence? = null, fill: Int = CARD, size: Float = 26f, onClick: () -> Unit): LinearLayout =
        LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(px(22), px(12), px(22), px(12))
            addView(text(label, size, Color.WHITE, true, 2))
            sub?.let { addView(text(it, size * 0.72f, MUTED, false, 2)) }
            focusable(this, fill)
            setOnClickListener { onClick() }
        }

    /** A coloured tile of a grid (subjects, chapters, exams…): a colour band, a title and a status line. */
    fun tile(title: CharSequence, sub: CharSequence?, color: Int, big: String? = null, onClick: () -> Unit): LinearLayout =
        LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL; setPadding(px(18), px(14), px(18), px(14))
            addView(View(act).apply { background = rounded(color, 4f) }, LinearLayout.LayoutParams(-1, px(8)).apply { bottomMargin = px(10) })
            big?.let { addView(text(it, 44f, Color.WHITE, true, 1)) }
            addView(text(title, 25f, Color.WHITE, true, 2))
            sub?.let { addView(text(it, 19f, MUTED, false, 2)) }
            focusable(this)
            setOnClickListener { onClick() }
        }

    /**
     * Rows of [cols] equal tiles (plain LinearLayouts: the D-pad moves naturally between them, and a tile can never be
     * drawn over another one).
     */
    fun grid(views: List<View>, cols: Int, gap: Int = 18, height: Int = 0): LinearLayout = LinearLayout(act).apply {
        orientation = LinearLayout.VERTICAL
        views.chunked(cols).forEach { row ->
            val r = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
            row.forEach { v -> r.addView(v, LinearLayout.LayoutParams(0, if (height > 0) px(height) else -2, 1f).apply { setMargins(px(gap / 2), px(gap / 2), px(gap / 2), px(gap / 2)) }) }
            repeat(cols - row.size) { r.addView(View(act), LinearLayout.LayoutParams(0, 1, 1f).apply { setMargins(px(gap / 2), 0, px(gap / 2), 0) }) }
            addView(r, LinearLayout.LayoutParams(-1, -2))
        }
    }

    /** Restricted Markdown → styled text (bold, italic, bullets; inline formulas in their one-line Unicode form). */
    fun md(s: String): CharSequence {
        val paras = runCatching { Markdown.parse(s) }.getOrElse { return s }
        val b = SpannableStringBuilder()
        paras.forEachIndexed { i, p ->
            if (i > 0) b.append(if (p.kind == Markdown.Kind.PARA || paras[i - 1].kind == Markdown.Kind.PARA) "\n\n" else "\n")
            val start = b.length
            if (p.kind == Markdown.Kind.NUMBER) b.append("${p.number}. ")
            for (sp in p.spans) {
                val st = b.length
                b.append(sp.tex?.plain() ?: sp.text)
                val style = when { sp.bold && sp.italic -> Typeface.BOLD_ITALIC; sp.bold -> Typeface.BOLD; sp.italic || sp.tex != null -> Typeface.ITALIC; else -> -1 }
                if (style >= 0) b.setSpan(StyleSpan(style), st, b.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (p.kind == Markdown.Kind.BULLET) b.setSpan(BulletSpan(px(14), ACCENT), start, b.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return b
    }

    companion object {
        const val BG = 0xFF0E1116.toInt()
        const val CARD = 0xFF1B2230.toInt()
        const val FOCUS = 0xFF2B3A52.toInt()
        const val ACCENT = 0xFF33B5E5.toInt()
        const val MUTED = 0xFFB4BCC8.toInt()
        const val GOOD = 0xFF43A047.toInt()
        const val BAD = 0xFFE53935.toInt()
        const val GOLD = 0xFFFFC107.toInt()
        const val PAPER = 0xFFFDFCF7.toInt()
        val BOX: Map<String, Int> = mapOf(
            "definition" to 0xFF1E88E5.toInt(), "propriete" to 0xFF1E88E5.toInt(), "formule" to 0xFF1E88E5.toInt(),
            "retenir" to 0xFF43A047.toInt(), "methode" to 0xFF8E24AA.toInt(), "attention" to 0xFFF4511E.toInt(),
            "pieges" to 0xFFF4511E.toInt(), "objectifs" to 0xFF00ACC1.toInt(),
        )
        val BOX_LABEL: Map<String, String> = mapOf(
            "definition" to "Définition", "propriete" to "Propriété", "formule" to "Formule", "retenir" to "À retenir",
            "methode" to "Méthode", "attention" to "Attention", "pieges" to "Pièges et erreurs fréquentes", "objectifs" to "Objectifs",
        )
    }
}

/** Shrinks a text until it fits its height (long blocks on a 720p screen), never under [minPx]. */
fun TextView.fitHeight(minPx: Float) {
    post {
        val h = height - paddingTop - paddingBottom; val w = width - paddingLeft - paddingRight
        if (h <= 0 || w <= 0) return@post
        var size = textSize
        val tp = TextPaint(paint)
        while (size > minPx) {
            tp.textSize = size
            val sl = StaticLayout.Builder.obtain(text, 0, text.length, tp, w).setLineSpacing(0f, lineSpacingMultiplier).build()
            if (sl.height <= h) break
            size -= 1f
        }
        if (size != textSize) setTextSize(TypedValue.COMPLEX_UNIT_PX, size)
    }
}

/** Draws a core [Scene] (illustration) on a light card, scaled uniformly and centred. */
class FigureView(ctx: Context, private val scene: Scene) : View(ctx) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paper = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = LearnStyle.PAPER }

    override fun onMeasure(wSpec: Int, hSpec: Int) {
        val w = MeasureSpec.getSize(wSpec); val hMax = MeasureSpec.getSize(hSpec)
        val h = if (MeasureSpec.getMode(hSpec) == MeasureSpec.EXACTLY) hMax
                else minOf((w * scene.h / scene.w).toInt(), if (hMax > 0) hMax else Int.MAX_VALUE)
        setMeasuredDimension(w, h)
    }

    override fun onDraw(c: Canvas) {
        val pad = minOf(width, height) * 0.03f
        val s = minOf((width - 2 * pad) / scene.w.toFloat(), (height - 2 * pad) / scene.h.toFloat())
        val dw = (scene.w * s).toFloat(); val dh = (scene.h * s).toFloat()
        val ox = (width - dw) / 2; val oy = (height - dh) / 2
        c.drawRoundRect(RectF(ox - pad, oy - pad, ox + dw + pad, oy + dh + pad), pad, pad, paper)
        c.save(); c.translate(ox, oy)
        draw(c, scene, s, fill, stroke, txt)
        c.restore()
    }

    companion object {
        fun draw(c: Canvas, scene: Scene, s: Float, fill: Paint, stroke: Paint, txt: Paint) {
            fun f(v: Double) = (v * s).toFloat()
            for (op in scene.ops) when (op) {
                is Op.Line -> { stroke.color = op.color; stroke.strokeWidth = f(op.width).coerceAtLeast(1f)
                    stroke.pathEffect = if (op.dash) DashPathEffect(floatArrayOf(f(6.0), f(5.0)), 0f) else null
                    c.drawLine(f(op.x1), f(op.y1), f(op.x2), f(op.y2), stroke); stroke.pathEffect = null }
                is Op.Circle -> { op.fill?.let { fill.color = it; c.drawCircle(f(op.cx), f(op.cy), f(op.r), fill) }
                    op.stroke?.let { stroke.color = it; stroke.strokeWidth = f(op.width).coerceAtLeast(1f); c.drawCircle(f(op.cx), f(op.cy), f(op.r), stroke) } }
                is Op.Rect -> { val r = RectF(f(op.x), f(op.y), f(op.x + op.w), f(op.y + op.h))
                    op.fill?.let { fill.color = it; c.drawRoundRect(r, f(op.radius), f(op.radius), fill) }
                    op.stroke?.let { stroke.color = it; stroke.strokeWidth = f(op.width).coerceAtLeast(1f); c.drawRoundRect(r, f(op.radius), f(op.radius), stroke) } }
                is Op.Path -> {
                    val p = Path()
                    for (cmd in op.cmds) when (cmd) {
                        is PathCmd.M -> p.moveTo(f(cmd.x), f(cmd.y)); is PathCmd.L -> p.lineTo(f(cmd.x), f(cmd.y))
                        is PathCmd.C -> p.cubicTo(f(cmd.x1), f(cmd.y1), f(cmd.x2), f(cmd.y2), f(cmd.x), f(cmd.y))
                        is PathCmd.Q -> p.quadTo(f(cmd.x1), f(cmd.y1), f(cmd.x), f(cmd.y)); PathCmd.Z -> p.close()
                    }
                    op.fill?.let { fill.color = it; c.drawPath(p, fill) }
                    op.stroke?.let { stroke.color = it; stroke.strokeWidth = f(op.width).coerceAtLeast(1f)
                        stroke.pathEffect = if (op.dash) DashPathEffect(floatArrayOf(f(6.0), f(5.0)), 0f) else null
                        c.drawPath(p, stroke); stroke.pathEffect = null }
                }
                is Op.Text -> { txt.color = op.color; txt.textSize = f(op.size); txt.typeface = if (op.bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                    txt.textAlign = when (op.anchor) { "start" -> Paint.Align.LEFT; "end" -> Paint.Align.RIGHT; else -> Paint.Align.CENTER }
                    // a thin paper-coloured halo first: a label stays readable where a curve or a line crosses it
                    txt.style = Paint.Style.STROKE; txt.strokeWidth = f(op.size) * 0.22f; val col = txt.color; txt.color = LearnStyle.PAPER
                    c.drawText(op.text, f(op.x), f(op.y), txt)
                    txt.style = Paint.Style.FILL; txt.color = col
                    c.drawText(op.text, f(op.x), f(op.y), txt) }
                is Op.Clip -> { c.save(); c.clipRect(f(op.x), f(op.y), f(op.x + op.w), f(op.y + op.h)) }
                Op.Unclip -> c.restore()
            }
        }
    }
}

/** A display formula (minimal LaTeX, core [TexLayout]) drawn with the platform font; centred, shrunk to fit its width. */
class FormulaView(ctx: Context, tex: String, private val sizePx: Float, private val color: Int = Color.WHITE) : View(ctx) {
    private val parsed: Tex? = runCatching { Tex.parse(tex) }.getOrNull()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = this@FormulaView.color }
    private val plain = tex
    private val metrics = object : TexLayout.Metrics {
        override fun width(text: String, size: Double, italic: Boolean): Double {
            paint.textSize = size.toFloat(); paint.typeface = if (italic) Typeface.create(Typeface.SERIF, Typeface.ITALIC) else Typeface.SERIF
            return paint.measureText(text).toDouble()
        }
        override fun ascent(size: Double) = size * 0.8
        override fun descent(size: Double) = size * 0.25
    }
    private var box: TexLayout.Box? = null

    private fun layoutFor(size: Float) = parsed?.let { TexLayout.layout(it, size.toDouble(), metrics) }

    override fun onMeasure(wSpec: Int, hSpec: Int) {
        val w = MeasureSpec.getSize(wSpec)
        var size = sizePx; var b = layoutFor(size)
        while (b != null && b.width > w * 0.96 && size > sizePx * 0.4f) { size *= 0.9f; b = layoutFor(size) }
        box = b
        setMeasuredDimension(w, ((b?.height ?: sizePx.toDouble() * 1.2) + sizePx * 0.3).toInt())
    }

    override fun onDraw(c: Canvas) {
        val b = box
        if (b == null) { paint.textSize = sizePx; paint.textAlign = Paint.Align.CENTER; c.drawText(plain, width / 2f, height * 0.65f, paint); return }
        val x0 = ((width - b.width) / 2).toFloat(); val y0 = ((height - b.height) / 2 + b.ascent).toFloat()
        paint.textAlign = Paint.Align.LEFT
        for (it in b.items) when (it) {
            is TexLayout.Item.Run -> { paint.textSize = it.size.toFloat(); paint.typeface = if (it.italic) Typeface.create(Typeface.SERIF, Typeface.ITALIC) else Typeface.SERIF
                paint.style = Paint.Style.FILL; c.drawText(it.text, x0 + it.x.toFloat(), y0 + it.y.toFloat(), paint) }
            is TexLayout.Item.Rule -> { paint.strokeWidth = it.thickness.toFloat(); paint.style = Paint.Style.STROKE
                c.drawLine(x0 + it.x1.toFloat(), y0 + it.y1.toFloat(), x0 + it.x2.toFloat(), y0 + it.y2.toFloat(), paint); paint.style = Paint.Style.FILL }
        }
    }
}

/** A thin progress bar (pages of a lesson, time of a mock exam). */
class BarView(ctx: Context, private val color: Int) : View(ctx) {
    var fraction = 0f; set(v) { field = v.coerceIn(0f, 1f); invalidate() }
    private val bg = Paint().apply { this.color = 0x33FFFFFF }
    private val fg = Paint().apply { this.color = color }
    override fun onDraw(c: Canvas) { c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bg); c.drawRect(0f, 0f, width * fraction, height.toFloat(), fg) }
}

fun ViewGroup.lp(w: Int = -1, h: Int = -2, weight: Float = 0f, top: Int = 0, bottom: Int = 0): LinearLayout.LayoutParams =
    LinearLayout.LayoutParams(w, h, weight).apply { topMargin = top; bottomMargin = bottom }
