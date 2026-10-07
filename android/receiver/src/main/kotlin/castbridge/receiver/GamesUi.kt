package castbridge.receiver

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.util.TypedValue
import android.widget.TextView
import kotlin.math.roundToInt

/**
 * Colors of the games screens, taken from branding/design-tokens.json (dark theme) so that the games keep the charter even
 * before the rest of the TV app is migrated. Brand colors of each game come from the same file (quizDesMillions, echecs).
 */
object GamesColors {
    const val BG = 0xFF0A0F1E.toInt()            // color.dark.background
    const val BG_TOP = 0xFF10182E.toInt()        // backgroundElevated
    const val SURFACE = 0xFF151D37.toInt()
    const val SURFACE_HIGH = 0xFF1B2542.toInt()
    const val OUTLINE = 0xFF2A3550.toInt()
    const val TEXT_HIGH = 0xFFF4F6FB.toInt()
    const val TEXT_MEDIUM = 0xFFB7C0D4.toInt()
    const val PRIMARY = 0xFFF5B025.toInt()
    const val ON_PRIMARY = 0xFF171204.toInt()
    const val FOCUS_RING = 0xFFFFE1A6.toInt()
    const val SUCCESS = 0xFF35C08A.toInt()
    const val ERROR = 0xFFFF6B6B.toInt()
    const val INFO = 0xFF6CB6FF.toInt()
    const val QUIZ = 0xFFFF5C39.toInt()          // brand.quizDesMillions.primary
    const val CHESS = 0xFF2FA96B.toInt()         // brand.echecs.primary
    const val SUDOKU = 0xFF6CB6FF.toInt()        // no brand entry yet: semantic.info
    const val CARDS = 0xFFB48CFF.toInt()         // card games (Bataille, Fap-Fap, Agraham Tia): no brand entry yet, a violet apart from the three above
}

/**
 * Sizes in "design pixels" of a 1920x1080 screen, scaled to the real screen: the same layout on a 1280x720 @160 dpi TV and on a
 * 1920x1080 @320 dpi one (their dp heights differ, 720 vs 540, so dp would not do). Text never goes under the charter's
 * 24 px TV minimum at 1080p.
 */
class Dx(ctx: Context) {
    private val dm = ctx.resources.displayMetrics
    val k: Float = minOf(dm.widthPixels / 1920f, dm.heightPixels / 1080f)
    val width get() = dm.widthPixels
    val height get() = dm.heightPixels
    fun px(v: Int): Int = (v * k).roundToInt().coerceAtLeast(1)
    fun pxf(v: Float): Float = v * k
    fun text(t: TextView, designPx: Int, color: Int, bold: Boolean = false): TextView = t.apply {
        setTextSize(TypedValue.COMPLEX_UNIT_PX, designPx * k); setTextColor(color)
        typeface = if (bold) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
        includeFontPadding = false
    }

    fun rounded(fill: Int, radius: Int, stroke: Int = 0, strokePx: Int = 0) = GradientDrawable().apply {
        cornerRadius = pxf(radius.toFloat()); setColor(fill); if (strokePx > 0) setStroke(px(strokePx).coerceAtLeast(1), stroke)
    }

    /** Card background: a focus ring (charter focusRing) when focused, a thin outline otherwise. */
    fun focusable(fill: Int, focusFill: Int, radius: Int) = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_focused), rounded(focusFill, radius, GamesColors.FOCUS_RING, 6))
        addState(intArrayOf(), rounded(fill, radius, GamesColors.OUTLINE, 2))
    }
}
