package castbridge.receiver

import android.app.Activity
import android.app.Application
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import castbridge.core.owner.KeyBadgeDisplay

/**
 * The edition and the properties of the activation key (essai / production / super illimité, validity, trial window): a small label at the top centre of each activity, refreshed every 30 s.
 * Only while the activation requirement is on (a development build has no edition to show).
 *
 * NOT permanent (decision of the owner, 2026-10-18; the rule is `castbridge.core.owner.KeyBadgeDisplay`): the green (production) and orange (essai) label shows for 15 s each time the home of
 * CastBridge-TV is (re)shown (launch, back from a video, MENU or library closed, back from Quiz / Apprendre / Jeux…) and is never on a video, in the MENU, in the library or in another activity.
 * PlayerActivity says when its home appears and when it is left ([homeShown], [homeLeft]). The red label (no key, key over: `Badge.ended`) stays on every screen (assumption, the owner may reverse it).
 */
object KeyBadgeOverlay : Application.ActivityLifecycleCallbacks {
    private const val TAG = "cb_key_badge"
    private val handler = Handler(Looper.getMainLooper())
    private val ticks = HashMap<Activity, Runnable>()
    private val views = HashMap<Activity, TextView>()               // the labels of the activities in front (resumed)
    private var onHome = false                                      // PlayerActivity shows its home: no video, no MENU, no library
    private var homeShownAt: Long? = null                           // SystemClock.elapsedRealtime() of the last (re)show of the home; null = never, or the 15 s are over
    private var ended = false                                       // the last badge read is the red one (no key / key over)
    private val endOfWindow = Runnable { homeShownAt = null; applyAll() }

    /** PlayerActivity (re)shows its home (launch, back from a video, MENU or library closed, back from another activity): the green / orange label shows for 15 s. */
    fun homeShown() {
        onHome = true; homeShownAt = SystemClock.elapsedRealtime()
        handler.removeCallbacks(endOfWindow); handler.postDelayed(endOfWindow, KeyBadgeDisplay.HOME_SHOW_MS)
        for ((a, v) in views) refresh(a, v)                         // fresh text and colour at the moment it appears
    }

    /** PlayerActivity leaves its home (a video starts, the MENU or the library opens): the label goes away at once and the 15 s are cancelled. */
    fun homeLeft() {
        onHome = false; homeShownAt = null
        handler.removeCallbacks(endOfWindow)
        applyAll()
    }

    private fun attach(a: Activity) {
        if (!BuildConfig.REQUIRE_ACTIVATION) return
        val root = a.window?.decorView as? ViewGroup ?: return
        val view = (root.findViewWithTag<TextView>(TAG)) ?: TextView(a).apply {
            tag = TAG; visibility = View.GONE; textSize = 20f; typeface = Typeface.DEFAULT_BOLD; setPadding(26, 8, 26, 8); isFocusable = false; isClickable = false; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            root.addView(this, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = castbridge.core.tv.PlayerIcons.safe(a.resources.displayMetrics.widthPixels, a.resources.displayMetrics.heightPixels).vertical })
        }
        views[a] = view
        refresh(a, view)
        val tick = object : Runnable { override fun run() { refresh(a, view); handler.postDelayed(this, 30_000L) } }
        ticks.remove(a)?.let(handler::removeCallbacks); ticks[a] = tick; handler.postDelayed(tick, 30_000L)
        view.bringToFront()
    }

    private fun detach(a: Activity) { ticks.remove(a)?.let(handler::removeCallbacks); views.remove(a) }

    private fun refresh(a: Activity, v: TextView) {
        runCatching { ActivationCenter.badge() }.getOrNull()?.let { b ->
            ended = b.ended
            v.text = b.text
            v.setTextColor(Color.WHITE)
            v.background = GradientDrawable().apply { cornerRadius = 40f; setColor(when { b.ended -> 0xDDB3261E.toInt(); b.title == "ESSAI" -> 0xDD8A6200.toInt(); else -> 0xDD1B6B3A.toInt() }) }
        }
        applyRule(a, v)
    }

    /** The rule of [KeyBadgeDisplay] on one label: only PlayerActivity can be « on the home »; the red label shows everywhere. */
    private fun applyRule(a: Activity, v: TextView) {
        val since = homeShownAt?.let { SystemClock.elapsedRealtime() - it }
        if (!KeyBadgeDisplay.visible(ended, onHome && a is PlayerActivity, since)) v.visibility = View.GONE
        else if (v.visibility != View.VISIBLE) { v.visibility = View.VISIBLE; v.bringToFront() }
    }

    private fun applyAll() { for ((a, v) in views) applyRule(a, v) }

    override fun onActivityResumed(a: Activity) = attach(a)
    override fun onActivityPaused(a: Activity) = detach(a)
    override fun onActivityCreated(a: Activity, s: Bundle?) {}
    override fun onActivityStarted(a: Activity) {}
    override fun onActivityStopped(a: Activity) {}
    override fun onActivitySaveInstanceState(a: Activity, s: Bundle) {}
    override fun onActivityDestroyed(a: Activity) = detach(a)
}
