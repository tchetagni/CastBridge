package castbridge.receiver

import android.app.Activity
import android.app.Application
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView

/**
 * The edition and the properties of the activation key (essai / production / super illimité, validity, trial window), PERMANENTLY on every screen of CastBridge TV: a small label at the top
 * centre of each activity, refreshed every 30 s. Only while the activation requirement is on (a development build has no edition to show).
 */
object KeyBadgeOverlay : Application.ActivityLifecycleCallbacks {
    private const val TAG = "cb_key_badge"
    private val handler = Handler(Looper.getMainLooper())
    private val ticks = HashMap<Activity, Runnable>()

    private fun attach(a: Activity) {
        if (!BuildConfig.REQUIRE_ACTIVATION) return
        val root = a.window?.decorView as? ViewGroup ?: return
        val view = (root.findViewWithTag<TextView>(TAG)) ?: TextView(a).apply {
            tag = TAG; textSize = 14f; typeface = Typeface.DEFAULT_BOLD; setPadding(26, 8, 26, 8); isFocusable = false; isClickable = false; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            root.addView(this, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = 6 })
        }
        refresh(view)
        val tick = object : Runnable { override fun run() { refresh(view); handler.postDelayed(this, 30_000L) } }
        ticks.remove(a)?.let(handler::removeCallbacks); ticks[a] = tick; handler.postDelayed(tick, 30_000L)
        view.bringToFront()
    }

    private fun refresh(v: TextView) {
        val b = runCatching { ActivationCenter.badge() }.getOrNull() ?: return
        v.text = b.text
        v.setTextColor(Color.WHITE)
        v.background = GradientDrawable().apply { cornerRadius = 40f; setColor(when { b.ended -> 0xDDB3261E.toInt(); b.title == "ESSAI" -> 0xDD8A6200.toInt(); else -> 0xDD1B6B3A.toInt() }) }
    }

    override fun onActivityResumed(a: Activity) = attach(a)
    override fun onActivityPaused(a: Activity) { ticks.remove(a)?.let(handler::removeCallbacks) }
    override fun onActivityCreated(a: Activity, s: Bundle?) {}
    override fun onActivityStarted(a: Activity) {}
    override fun onActivityStopped(a: Activity) {}
    override fun onActivitySaveInstanceState(a: Activity, s: Bundle) {}
    override fun onActivityDestroyed(a: Activity) { ticks.remove(a)?.let(handler::removeCallbacks) }
}
