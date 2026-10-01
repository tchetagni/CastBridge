package castbridge.receiver

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.Gravity
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.widget.LinearLayout
import castbridge.core.learn.AnimPlayer
import castbridge.core.learn.AnimSettings
import castbridge.core.learn.AnimatedFigure
import castbridge.core.learn.Block
import castbridge.core.learn.Scene

/** « Réduire les animations » on the TV: the app switch (yellow key) and Android's « remove animations » (animator scale 0). */
object TvAnimPrefs {
    private const val FILE = "learn_anim"
    fun load(ctx: Context) {
        AnimSettings.appReduce = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean("reduce", false)
        AnimSettings.systemReduce = runCatching { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
    }
    fun setApp(ctx: Context, v: Boolean) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean("reduce", v).apply()
        AnimSettings.appReduce = v
    }
}

/**
 * Draws the frames of an [AnimPlayer] on the paper card, like [FigureView] (same Op renderer). The clock is the view's own
 * animation callback (vsync); [AnimPlayer.advance] keeps it to 30 fps and drops frames after a hiccup, and the view only
 * redraws when a new frame is due. Paused as soon as the window is hidden or the view is detached.
 */
class AnimFigureView(ctx: Context, private val player: AnimPlayer, private val onChange: () -> Unit) : View(ctx) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paper = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = LearnStyle.PAPER }
    private val card = RectF()
    private var scene: Scene = player.scene()
    private var running = false
    private var resumeOnShow = false

    private val tick = object : Runnable {
        override fun run() {
            if (!player.playing) { running = false; changed(); return }
            if (player.advance(System.nanoTime())) { scene = player.scene(); invalidate(); onChange() }
            postOnAnimation(this)
        }
    }

    private fun changed() { scene = player.scene(); invalidate(); onChange() }
    private fun start() { if (player.playing && !running) { running = true; postOnAnimation(tick) } }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!player.anim.stepMode && !player.reduced && player.t <= 0.0) player.play()
        changed(); start()
    }

    override fun onDetachedFromWindow() { player.pause(); removeCallbacks(tick); running = false; super.onDetachedFromWindow() }

    override fun onWindowVisibilityChanged(v: Int) {
        super.onWindowVisibilityChanged(v)
        if (v != VISIBLE) { resumeOnShow = player.playing && !player.anim.stepMode; player.pause(); changed() }
        else if (resumeOnShow) { resumeOnShow = false; player.play(); start() }
    }

    /** Remote keys of the animated page. Returns false for the keys that must keep turning pages (before the first / after the last step). */
    fun key(code: Int): Boolean {
        val anim = player.anim
        when (code) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BUTTON_A -> { player.toggle() }
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                if (anim.stops.isEmpty() || anim.nextStopAfter(player.t) == null) return false
                player.next()
            }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                if (anim.stops.isEmpty() || player.t <= 0.0) return false
                player.prev()
            }
            KeyEvent.KEYCODE_PROG_YELLOW -> { toggleReduced() }
            else -> return false
        }
        changed(); start()
        if (anim.stops.isNotEmpty() && !player.playing) announceForAccessibility(player.caption.orEmpty())
        return true
    }

    fun replay() { player.replay(); changed(); start() }

    fun toggleReduced() {
        TvAnimPrefs.setApp(context, !AnimSettings.appReduce)
        player.reduced = AnimSettings.reduce
        if (!player.reduced && !player.anim.stepMode) player.play()
        changed(); start()
    }

    override fun onMeasure(wSpec: Int, hSpec: Int) {
        val w = MeasureSpec.getSize(wSpec); val hMax = MeasureSpec.getSize(hSpec)
        val h = if (MeasureSpec.getMode(hSpec) == MeasureSpec.EXACTLY) hMax
                else minOf((w * scene.h / scene.w).toInt(), if (hMax > 0) hMax else Int.MAX_VALUE)
        setMeasuredDimension(w, h)
    }

    override fun onDraw(c: Canvas) {
        val sc = scene
        val pad = minOf(width, height) * 0.03f
        val s = minOf((width - 2 * pad) / sc.w.toFloat(), (height - 2 * pad) / sc.h.toFloat())
        val dw = (sc.w * s).toFloat(); val dh = (sc.h * s).toFloat()
        val ox = (width - dw) / 2; val oy = (height - dh) / 2
        card.set(ox - pad, oy - pad, ox + dw + pad, oy + dh + pad)
        c.drawRoundRect(card, pad, pad, paper)
        c.save(); c.translate(ox, oy)
        FigureView.draw(c, sc, s, fill, stroke, txt)
        c.restore()
    }
}

/** The animated « illustration » page of the TV reader: the figure, the caption of the current step, the list of steps when motion is reduced. */
class AnimBlock(private val a: LearnActivity, private val b: Block.Illustration, private val anim: AnimatedFigure, private val en: Boolean) {
    private fun t(fr: String, e: String) = if (en) e else fr
    private val player = run { TvAnimPrefs.load(a); AnimPlayer(anim, AnimSettings.reduce) }
    private val captionView = a.st.text("", 25f, Color.WHITE, true, 2).apply { gravity = Gravity.CENTER }
    private val stepsView = a.st.text("", 20f, LearnStyle.MUTED)
    private val fig = AnimFigureView(a, player) { refreshText() }.also { it.contentDescription = b.alt }
    val view: View = LinearLayout(a).apply {
        orientation = LinearLayout.VERTICAL
        addView(fig, LinearLayout.LayoutParams(-1, 0, 1f))
        addView(captionView, LinearLayout.LayoutParams(-1, -2).apply { topMargin = a.st.px(8) })
        addView(stepsView, LinearLayout.LayoutParams(-1, -2))
        b.caption?.let { addView(a.st.text(a.st.md(it), 21f, LearnStyle.MUTED, lines = 1).apply { gravity = Gravity.CENTER }) }
    }

    init { refreshText() }

    private fun refreshText() {
        val stops = anim.stops
        val i = player.stepIndex
        captionView.text = if (stops.isEmpty()) "" else (if (stops.size > 1) "${i + 1}/${stops.size} · " else "") + stops[i].say
        // reduced motion: the whole step list as text, the current step marked
        stepsView.text = if (player.reduced && stops.isNotEmpty()) stops.mapIndexed { k, s -> (if (k == i) "▶ " else "   ") + "${k + 1}. ${s.say}" }.joinToString("\n") else ""
        stepsView.visibility = if (stepsView.text.isEmpty()) View.GONE else View.VISIBLE
    }

    fun key(code: Int): Boolean = fig.key(code)
    fun pause() { player.pause() }

    /** Phone remote: play | next | prev | replay | reduce. */
    fun control(action: String): Boolean = when (action) {
        "play" -> fig.key(KeyEvent.KEYCODE_DPAD_CENTER)
        "next" -> fig.key(KeyEvent.KEYCODE_DPAD_RIGHT)
        "prev" -> fig.key(KeyEvent.KEYCODE_DPAD_LEFT)
        "replay" -> { fig.replay(); true }
        "reduce" -> { fig.toggleReduced(); true }
        else -> false
    }

    fun hint(): String = when {
        player.reduced -> t("OK / ▶ : étape suivante   ·   ◀ : étape précédente   ·   JAUNE : rétablir les animations", "OK / ▶: next step   ·   ◀: previous step   ·   YELLOW: turn animations back on")
        anim.stepMode -> t("OK : étape suivante   ·   ◀ ▶ : étapes   ·   JAUNE : réduire les animations", "OK: next step   ·   ◀ ▶: steps   ·   YELLOW: reduce animations")
        else -> t("OK : lecture / pause   ·   ◀ ▶ : étapes   ·   JAUNE : réduire les animations", "OK: play / pause   ·   ◀ ▶: steps   ·   YELLOW: reduce animations")
    }
}
