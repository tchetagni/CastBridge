package castbridge.receiver

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import castbridge.core.xfer.CopyBadge

/**
 * R-16 (B) : la petite icône « copie en cours » du lecteur plein écran (flèche, pourcentage, pastille de nombre). Vue mince : tout ce qu'elle dit vient de
 * [CopyBadge.of]. Coin haut-gauche (le coin haut-droit porte la barre d'icônes de statut et l'envoi progressif), ~32 dp, translucide ; ne prend JAMAIS
 * le focus ni les touches (les flèches de la télécommande restent au lecteur) ; texte accessible « Copie en cours : 42 % ».
 */
class CopyBadgeView(private val act: Activity, parent: FrameLayout) {
    private val arrow = TextView(act).apply { text = "⬇"; textSize = 14f; setTextColor(Color.WHITE) }
    private val pct = TextView(act).apply { textSize = 14f; typeface = TvFonts.bold; setTextColor(Color.WHITE); setPadding(dp(4), 0, 0, 0) }
    private val count = TextView(act).apply {
        textSize = 11f; typeface = TvFonts.bold; setTextColor(Color.BLACK); gravity = Gravity.CENTER; visibility = View.GONE
        minWidth = dp(16); setPadding(dp(4), 0, dp(4), 0)
    }
    private val box = LinearLayout(act).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; visibility = View.GONE; alpha = 0.78f
        minimumHeight = dp(32); setPadding(dp(10), dp(4), dp(10), dp(4))
        isFocusable = false; isFocusableInTouchMode = false; isClickable = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        addView(arrow); addView(pct); addView(count, LinearLayout.LayoutParams(-2, dp(16)).apply { marginStart = dp(6) })
    }
    private var shownKey: String? = null

    init { parent.addView(box, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply { topMargin = dp(24); marginStart = dp(24) }) }

    /** Draws [m]; does nothing when it is identical to what is already drawn. */
    fun render(m: CopyBadge.Model) {
        if (!m.visible) { if (box.visibility != View.GONE) box.visibility = View.GONE; shownKey = null; return }
        val key = "${m.label}|${m.countLabel}|${m.tone}|${m.description}"
        if (key == shownKey && box.visibility == View.VISIBLE) return
        shownKey = key
        val tone = when {
            m.tone == CopyBadge.Tone.SLOWED -> ORANGE
            m.percent == 100 -> TvStyle.GOOD
            else -> TvStyle.ACCENT
        }
        box.background = GradientDrawable().apply { setColor(0xB0000000.toInt()); cornerRadius = dp(16).toFloat(); setStroke(dp(1), tone) }
        arrow.setTextColor(tone)
        pct.text = m.label; pct.visibility = if (m.label.isEmpty()) View.GONE else View.VISIBLE
        count.text = m.countLabel.orEmpty(); count.visibility = if (m.countLabel == null) View.GONE else View.VISIBLE
        count.background = GradientDrawable().apply { setColor(tone); cornerRadius = dp(8).toFloat() }
        box.contentDescription = m.description
        box.visibility = View.VISIBLE
    }

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), act.resources.displayMetrics).toInt()

    companion object { const val ORANGE = 0xFFFFA726.toInt() }
}
