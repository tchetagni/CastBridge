package castbridge.receiver

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import castbridge.core.remote.RemoteKey
import castbridge.core.remote.TextMode

/**
 * « CastBridge Télécommande » — OPTIONAL, enabled by the user in the TV's accessibility settings (RemoteSetupActivity explains
 * how). It lets the phone remote act outside CastBridge: Back / Home / recent apps (performGlobalAction), moving the focus and
 * pressing OK in other apps (AccessibilityNodeInfo), typing in the focused field (ACTION_SET_TEXT).
 *
 * What it does NOT do: it reads no screen content on its own, keeps nothing, sends nothing anywhere; accessibility events
 * are ignored. It acts only when an order arrives from a phone that gave the TV's PIN (RemoteHub, /api/remote/…).
 * Limits: no power on/off, no HDMI-CEC, no real key codes (MENU, digits…) for other apps; some launchers ignore accessibility.
 */
class RemoteAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() { instance = this }
    override fun onUnbind(intent: android.content.Intent?): Boolean { if (instance === this) instance = null; return super.onUnbind(intent) }
    override fun onDestroy() { if (instance === this) instance = null; super.onDestroy() }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* nothing is read or kept */ }
    override fun onInterrupt() {}

    fun global(action: Int): Boolean = performGlobalAction(action)

    /** Arrow / OK in whatever app is in front. Android 13+: the system's own D-pad actions; before: focus search on nodes. */
    fun dpad(k: RemoteKey, long: Boolean): Boolean {
        if (Build.VERSION.SDK_INT >= 33 && !(long && k == RemoteKey.DPAD_CENTER) && !forceNodes()) {
            val a = when (k) {
                RemoteKey.DPAD_UP -> GLOBAL_ACTION_DPAD_UP; RemoteKey.DPAD_DOWN -> GLOBAL_ACTION_DPAD_DOWN
                RemoteKey.DPAD_LEFT -> GLOBAL_ACTION_DPAD_LEFT; RemoteKey.DPAD_RIGHT -> GLOBAL_ACTION_DPAD_RIGHT
                else -> GLOBAL_ACTION_DPAD_CENTER
            }
            if (performGlobalAction(a)) return true
        }
        val root = rootInActiveWindow ?: return false
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
        if (k == RemoteKey.DPAD_CENTER) {
            var n = focused
            val act = if (long) AccessibilityNodeInfo.ACTION_LONG_CLICK else AccessibilityNodeInfo.ACTION_CLICK
            while (n != null && !(if (long) n.isLongClickable else n.isClickable)) n = n.parent
            return n?.performAction(act) ?: false
        }
        if (focused == null) return firstFocusable(root)?.performAction(AccessibilityNodeInfo.ACTION_FOCUS) ?: false
        val dir = when (k) { RemoteKey.DPAD_UP -> View.FOCUS_UP; RemoteKey.DPAD_DOWN -> View.FOCUS_DOWN; RemoteKey.DPAD_LEFT -> View.FOCUS_LEFT; else -> View.FOCUS_RIGHT }
        val next = focused.focusSearch(dir)
        if (next != null && next != focused) return next.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        // End of a list: scroll it so the next items appear.
        var p: AccessibilityNodeInfo? = focused
        while (p != null && !p.isScrollable) p = p.parent
        return p?.performAction(if (dir == View.FOCUS_UP || dir == View.FOCUS_LEFT) AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD else AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) ?: false
    }

    /** Debug builds only: `adb shell settings put global castbridge_a11y_nodes 1` tests the Android 8–12 path on a newer TV. */
    private fun forceNodes(): Boolean = (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0 &&
        runCatching { android.provider.Settings.Global.getString(contentResolver, "castbridge_a11y_nodes") == "1" }.getOrDefault(false)

    private fun firstFocusable(n: AccessibilityNodeInfo, depth: Int = 0): AccessibilityNodeInfo? {
        if (depth > 30) return null
        if (n.isFocusable && n.isVisibleToUser) return n
        for (i in 0 until n.childCount) n.getChild(i)?.let { c -> firstFocusable(c, depth + 1)?.let { return it } }
        return null
    }

    private fun editable(): AccessibilityNodeInfo? = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.takeIf { it.isEditable }

    private fun current(n: AccessibilityNodeInfo): String = if (Build.VERSION.SDK_INT >= 26 && n.isShowingHintText) "" else n.text?.toString().orEmpty()

    private fun setText(n: AccessibilityNodeInfo, s: String): Boolean =
        n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, s) })

    fun text(value: String, mode: TextMode): Boolean {
        val n = editable() ?: return false
        return when (mode) {
            TextMode.CLEAR -> setText(n, "")
            TextMode.REPLACE -> setText(n, value)
            TextMode.INSERT -> {
                val cur = current(n)
                val a = n.textSelectionStart; val b = n.textSelectionEnd
                if (a in 0..cur.length && b in a..cur.length) setText(n, cur.substring(0, a) + value + cur.substring(b)) else setText(n, cur + value)
            }
        }
    }

    fun deleteChar(): Boolean {
        val n = editable() ?: return false
        val cur = current(n)
        return cur.isNotEmpty() && setText(n, cur.dropLast(1))
    }

    fun enter(): Boolean {
        val n = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        if (Build.VERSION.SDK_INT >= 30 && n.performAction(android.R.id.accessibilityActionImeEnter)) return true
        return n.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    companion object {
        @Volatile var instance: RemoteAccessibilityService? = null; private set
    }
}
