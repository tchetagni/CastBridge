package castbridge.receiver

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.TextView
import castbridge.core.quiz.QrCode

/** Colours of the quiz screens: strong contrasts, readable from the sofa. */
object QuizColors {
    private val Q = castbridge.core.brand.BrandTokens.QuizDesMillions
    private fun mix(a: Int, b: Int, t: Float) = android.graphics.Color.rgb(
        (android.graphics.Color.red(a) + (android.graphics.Color.red(b) - android.graphics.Color.red(a)) * t).toInt(),
        (android.graphics.Color.green(a) + (android.graphics.Color.green(b) - android.graphics.Color.green(a)) * t).toInt(),
        (android.graphics.Color.blue(a) + (android.graphics.Color.blue(b) - android.graphics.Color.blue(a)) * t).toInt())
    val BG_TOP = mix(Q.BACKGROUND, Q.ACCENT, 0.22f)
    val BG_MID = mix(Q.BACKGROUND, Q.ACCENT, 0.08f)
    val BG_BOTTOM = mix(Q.BACKGROUND, 0xFF000000.toInt(), 0.45f)
    val PANEL = mix(Q.BACKGROUND, Q.ACCENT, 0.14f)
    const val STROKE = castbridge.core.brand.BrandTokens.QuizDesMillions.ACCENT
    const val GOLD = castbridge.core.brand.BrandTokens.QuizDesMillions.SECONDARY
    const val TEXT = castbridge.core.brand.BrandTokens.Dark.TEXT_HIGH
    const val MUTED = castbridge.core.brand.BrandTokens.Dark.TEXT_MEDIUM
    /** Selected answer: the coral of the sub-brand, darkened so that the white text keeps AA. */
    val SELECTED = mix(Q.PRIMARY, 0xFF000000.toInt(), 0.27f)
    const val RIGHT = castbridge.core.brand.BrandTokens.Semantic.SUCCESS_LIGHT
    const val WRONG = castbridge.core.brand.BrandTokens.Semantic.ERROR_LIGHT
    const val FOCUS = castbridge.core.brand.BrandTokens.Dark.FOCUS_RING
    /** Duel answer colours (AA with white text), with shapes ▲ ◆ ● ■, never colour alone. */
    val DUEL = intArrayOf(castbridge.core.brand.BrandTokens.Semantic.ERROR_LIGHT, castbridge.core.brand.BrandTokens.Semantic.INFO_LIGHT, castbridge.core.brand.BrandTokens.Semantic.WARNING_LIGHT, castbridge.core.brand.BrandTokens.Semantic.SUCCESS_LIGHT)
    val SHAPES = arrayOf("▲", "◆", "●", "■")
    val LETTERS = arrayOf("A", "B", "C", "D")

    fun background(): Drawable = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(BG_TOP, BG_MID, BG_BOTTOM))
}

fun Context.dp(v: Float): Float = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)
fun Context.dpi(v: Int): Int = dp(v.toFloat()).toInt()

/**
 * The elongated hexagon of the quiz plates (question and answers), stateful: a focused plate gets a thick white
 * outline and a lighter fill so the remote's focus is obvious from 3 m.
 */
class HexDrawable(private val ctx: Context) : Drawable() {
    enum class Look { NORMAL, SELECTED, RIGHT, WRONG, DIM, HIDDEN }
    var look = Look.NORMAL; set(v) { field = v; invalidateSelf() }
    /** Fill colour in the normal look (Duel uses the answer colours). */
    var base = QuizColors.PANEL; set(v) { field = v; invalidateSelf() }
    private var focused = false
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = QuizColors.STROKE; strokeWidth = ctx.dp(2f) }
    private val path = Path()

    override fun draw(c: Canvas) {
        if (look == Look.HIDDEN) return
        val b = bounds
        val inset = ctx.dp(4f)
        val l = b.left + inset; val r = b.right - inset; val t = b.top + inset; val bt = b.bottom - inset
        val tip = minOf((bt - t) / 2f, ctx.dp(28f))
        val cy = (t + bt) / 2f
        // the thin line across the screen behind the plate, as on the TV show
        c.drawLine(b.left.toFloat(), cy, l + tip, cy, line); c.drawLine(r - tip, cy, b.right.toFloat(), cy, line)
        path.reset()
        path.moveTo(l, cy); path.lineTo(l + tip, t); path.lineTo(r - tip, t); path.lineTo(r, cy); path.lineTo(r - tip, bt); path.lineTo(l + tip, bt); path.close()
        val color = when (look) {
            Look.SELECTED -> QuizColors.SELECTED
            Look.RIGHT -> QuizColors.RIGHT
            Look.WRONG -> QuizColors.WRONG
            else -> base
        }
        fill.shader = LinearGradient(0f, t, 0f, bt, lighten(color, if (focused) 0.35f else 0.18f), color, Shader.TileMode.CLAMP)
        fill.alpha = if (look == Look.DIM) 90 else 255
        c.drawPath(path, fill)
        stroke.strokeWidth = ctx.dp(if (focused) 5f else 2.5f)
        stroke.color = if (focused) QuizColors.FOCUS else if (look == Look.NORMAL || look == Look.DIM) QuizColors.STROKE else lighten(color, 0.5f)
        stroke.alpha = if (look == Look.DIM) 110 else 255
        c.drawPath(path, stroke)
    }

    override fun isStateful() = true
    override fun onStateChange(state: IntArray): Boolean {
        val f = state.contains(android.R.attr.state_focused)
        if (f == focused) return false
        focused = f; invalidateSelf(); return true
    }
    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(cf: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT

    companion object {
        fun lighten(c: Int, f: Float): Int {
            val r = Color.red(c); val g = Color.green(c); val b = Color.blue(c)
            return Color.rgb((r + (255 - r) * f).toInt(), (g + (255 - g) * f).toInt(), (b + (255 - b) * f).toInt())
        }
    }
}

/** A plate: TextView on a [HexDrawable]. */
class Plate(ctx: Context, sizeSp: Float, focusable: Boolean) : TextView(ctx) {
    val hex = HexDrawable(ctx)
    init {
        background = hex
        setTextColor(QuizColors.TEXT)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        typeface = TvFonts.quizBold
        gravity = Gravity.CENTER_VERTICAL
        val h = ctx.dpi(44); val v = ctx.dpi(10)
        setPadding(h, v, h, v)
        isFocusable = focusable; isFocusableInTouchMode = false
        maxLines = 3
        ellipsize = android.text.TextUtils.TruncateAt.END
    }
}

/** Round countdown: an arc that empties, the seconds in the middle; turns red for the last 5 s. */
class RingView(ctx: Context) : View(ctx) {
    var fraction = 1f; set(v) { field = v.coerceIn(0f, 1f); invalidate() }
    var label = ""; set(v) { if (field != v) { field = v; invalidate() } }
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0x33FFFFFF; strokeWidth = ctx.dp(7f) }
    private val fg = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = ctx.dp(7f); strokeCap = Paint.Cap.ROUND }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = QuizColors.TEXT; textAlign = Paint.Align.CENTER; typeface = TvFonts.quizBold }
    private val oval = RectF()
    override fun onDraw(c: Canvas) {
        val s = minOf(width, height).toFloat(); val pad = bg.strokeWidth
        oval.set((width - s) / 2 + pad, (height - s) / 2 + pad, (width + s) / 2 - pad, (height + s) / 2 - pad)
        c.drawOval(oval, bg)
        val secs = label.toIntOrNull()
        fg.color = if (secs != null && secs <= 5) QuizColors.WRONG else QuizColors.GOLD
        c.drawArc(oval, -90f, 360f * fraction, false, fg)
        txt.textSize = s * 0.34f
        c.drawText(label, width / 2f, height / 2f - (txt.descent() + txt.ascent()) / 2, txt)
    }
}

/** A QR code drawn with its quiet zone on white (phones need contrast and margin to read it off a TV). */
class QrView(ctx: Context) : View(ctx) {
    var code: QrCode? = null; set(v) { field = v; invalidate() }
    private val dark = Paint().apply { color = Color.BLACK; isAntiAlias = false }
    override fun onDraw(c: Canvas) {
        val q = code ?: return
        val n = q.size + 8                                  // 4 modules of quiet zone on each side
        val side = minOf(width, height)
        val cell = side / n                                 // whole pixels per module: crisp edges
        if (cell <= 0) return
        val total = cell * n
        val ox = (width - total) / 2; val oy = (height - total) / 2
        c.drawRect(ox.toFloat(), oy.toFloat(), (ox + total).toFloat(), (oy + total).toFloat(), Paint().apply { color = Color.WHITE })
        for (y in 0 until q.size) for (x in 0 until q.size) if (q[x, y]) {
            val l = ox + (x + 4) * cell; val t = oy + (y + 4) * cell
            c.drawRect(l.toFloat(), t.toFloat(), (l + cell).toFloat(), (t + cell).toFloat(), dark)
        }
    }
}

/** Audience result: four bars with the letter under each and the percentage above. */
class BarsView(ctx: Context) : View(ctx) {
    var values: IntArray? = null; set(v) { field = v; invalidate() }
    var progress = 1f; set(v) { field = v; invalidate() }
    private val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = QuizColors.GOLD }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = QuizColors.TEXT; textAlign = Paint.Align.CENTER; typeface = TvFonts.quizBold; textSize = ctx.dp(18f) }
    override fun onDraw(c: Canvas) {
        val v = values ?: return
        val w = width / 4f; val top = txt.textSize * 1.4f; val bottom = height - txt.textSize * 1.4f
        for (i in 0 until 4) {
            val cx = w * i + w / 2
            val h = (bottom - top) * v[i] / 100f * progress
            c.drawRect(cx - w * 0.25f, bottom - h, cx + w * 0.25f, bottom, bar)
            c.drawText("${(v[i] * progress).toInt()} %", cx, bottom - h - txt.textSize * 0.4f, txt)
            c.drawText(QuizColors.LETTERS[i], cx, height - txt.textSize * 0.3f, txt)
        }
    }
}

/**
 * The lit stage behind every quiz screen: a deep blue gradient with a few slow, soft spotlights sweeping across it
 * (redrawn at ~20 images/s only, a few gradient circles: cheap enough for a small TV chip). [calm] slows them during
 * questions so the eye stays on the text.
 */
class StageBackground(ctx: Context) : View(ctx) {
    var calm = false
    var flash = 0; set(v) { field = v; flashUntil = android.os.SystemClock.uptimeMillis() + 900 }
    private var flashUntil = 0L
    private val base = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(QuizColors.BG_TOP, QuizColors.BG_MID, QuizColors.BG_BOTTOM))
    private val spot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val floor = Paint(Paint.ANTI_ALIAS_FLAG)
    private val start = android.os.SystemClock.uptimeMillis()
    private val colors = intArrayOf(0x5527C7B0, 0x40FFB020, 0x35FF5C39)

    override fun onDraw(c: Canvas) {
        base.setBounds(0, 0, width, height); base.draw(c)
        val w = width.toFloat(); val h = height.toFloat()
        val t = (android.os.SystemClock.uptimeMillis() - start) / (if (calm) 9000.0 else 4000.0)
        for (i in colors.indices) {
            val phase = t + i * 2.1
            val x = w * (0.5f + 0.42f * Math.sin(phase).toFloat())
            val y = h * (0.18f + 0.1f * Math.cos(phase * 1.3).toFloat())
            val r = h * 0.55f
            spot.shader = android.graphics.RadialGradient(x, y, r, colors[i], 0x00000000, Shader.TileMode.CLAMP)
            c.drawCircle(x, y, r, spot)
        }
        // a glowing « floor » at the bottom, as on a TV set
        floor.shader = LinearGradient(0f, h * 0.78f, 0f, h, 0x00000000, 0x3327C7B0, Shader.TileMode.CLAMP)
        c.drawRect(0f, h * 0.78f, w, h, floor)
        val now = android.os.SystemClock.uptimeMillis()
        if (now < flashUntil) {
            val a = ((flashUntil - now) / 900f * 110).toInt().coerceIn(0, 255)
            c.drawColor((a shl 24) or (flash and 0xFFFFFF))
        }
        postInvalidateDelayed(if (now < flashUntil) 30 else 50)
    }
}

/** Rounded focusable button of the menus (title, lobby, dialogs). */
fun quizButton(ctx: Context, text: String, sizeSp: Float = 22f, onClick: () -> Unit): TextView = TextView(ctx).apply {
    this.text = text
    setTextColor(QuizColors.TEXT)
    setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
    typeface = TvFonts.quizBold
    gravity = Gravity.CENTER
    val h = ctx.dpi(28); val v = ctx.dpi(12)
    setPadding(h, v, h, v)
    isFocusable = true; isClickable = true
    background = android.graphics.drawable.StateListDrawable().apply {
        val r = ctx.dp(30f)
        addState(intArrayOf(android.R.attr.state_focused), GradientDrawable().apply {
            cornerRadius = r; setColor(QuizColors.GOLD); setStroke(ctx.dpi(4), QuizColors.FOCUS) })
        addState(intArrayOf(), GradientDrawable().apply { cornerRadius = r; setColor(QuizColors.PANEL); setStroke(ctx.dpi(2), QuizColors.STROKE) })
    }
    setOnFocusChangeListener { v, f ->
        (v as TextView).setTextColor(if (f) QuizColors.BG_BOTTOM else QuizColors.TEXT)
        v.animate().scaleX(if (f) 1.06f else 1f).scaleY(if (f) 1.06f else 1f).setDuration(120).start()
    }
    setOnClickListener { onClick() }
}

/** A big two-line choice card for the setup screens (title in bold, explanation below); dimmed when not available yet. */
fun choiceCard(ctx: Context, title: String, sub: String?, enabled: Boolean, onClick: () -> Unit): TextView = quizButton(ctx, "", 24f, onClick).apply {
    val t = android.text.SpannableStringBuilder(title)
    if (sub != null) {
        val s = t.length
        t.append("\n").append(sub)
        t.setSpan(android.text.style.RelativeSizeSpan(0.68f), s, t.length, 0)
        t.setSpan(android.text.style.StyleSpan(Typeface.NORMAL), s, t.length, 0)
    }
    text = t
    minWidth = ctx.dpi(300)
    alpha = if (enabled) 1f else 0.45f
}

/** French typography: no line break inside « … » nor before ? ! : (non-breaking spaces). */
fun fr(s: String?): String = (s ?: "").replace("« ", "«\u00A0").replace(" »", "\u00A0»")
    .replace(" ?", "\u00A0?").replace(" !", "\u00A0!").replace(" :", "\u00A0:")

fun quizText(ctx: Context, text: CharSequence, sizeSp: Float, color: Int = QuizColors.TEXT, bold: Boolean = false): TextView = TextView(ctx).apply {
    this.text = text
    setTextColor(color)
    setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
    if (bold) typeface = TvFonts.quizBold
}

fun panelBackground(ctx: Context, color: Int = QuizColors.PANEL, stroke: Int = QuizColors.STROKE): Drawable =
    GradientDrawable().apply { cornerRadius = ctx.dp(18f); setColor(color); setStroke(ctx.dpi(2), stroke) }
