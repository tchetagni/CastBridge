package castbridge.receiver

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import castbridge.core.parental.FgEvent
import castbridge.core.parental.ForegroundTracker

/**
 * Which app is in front of the whole TV (docs/PARENTAL.md, « Surveillance de toute la TV »).
 *
 * - Primary source: UsageStatsManager (special access PACKAGE_USAGE_STATS), polled every [POLL_MS] on its own low-priority thread, and
 *   ONLY while the screen is on and the parent switched the supervision on. One small query of the events since the last poll: no loop, no
 *   screenshot, nothing kept. Built for a 32-bit TV box with little CPU and RAM.
 * - Optional faster source: the CastBridge accessibility service ([RemoteAccessibilityService]) reports the package of the window that
 *   just opened (nothing else). It is used when it is connected and more recent than the last change seen by the usage log; it is the
 *   only way on a TV where the usage-access screen does not exist.
 * The watcher only reports the package that changed; [ParentalHub] decides and enforces.
 */
object ForegroundWatcher {
    private const val POLL_MS = 4_000L
    private const val BOOT_WINDOW_MS = 2 * 3600_000L

    private var app: Context? = null
    private var handler: Handler? = null
    private val tracker = ForegroundTracker()
    @Volatile var current: String? = null; private set
    @Volatile private var lastPollAt = 0L
    private var lastQuery = 0L
    @Volatile private var a11yPkg: String? = null
    @Volatile private var a11yAt = 0L
    private var usageChangedAt = 0L
    private var lastUsagePkg: String? = null
    @Volatile private var ime: Set<String> = emptySet()
    @Volatile var onChange: (String?) -> Unit = {}

    /** Windows that say nothing about what the child is using: they never replace the app underneath. */
    private val IGNORED = setOf("com.android.systemui", "com.android.permissioncontroller", "com.google.android.permissioncontroller")

    @Synchronized fun start(ctx: Context) {
        if (handler != null) return
        app = ctx.applicationContext
        val t = HandlerThread("cb-fg", Process.THREAD_PRIORITY_BACKGROUND).apply { start() }
        handler = Handler(t.looper).also { it.postDelayed(poll, POLL_MS) }
    }

    fun usageExists(ctx: Context): Boolean = ctx.getSystemService(Context.USAGE_STATS_SERVICE) != null

    /** The special access « Accès aux données d'utilisation » is granted to CastBridge-TV. */
    @Suppress("DEPRECATION")
    fun usageGranted(ctx: Context): Boolean = runCatching {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (android.os.Build.VERSION.SDK_INT >= 29) ops.unsafeCheckOpNoThrow("android:get_usage_stats", Process.myUid(), ctx.packageName)
        else ops.checkOpNoThrow("android:get_usage_stats", Process.myUid(), ctx.packageName)
        mode == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    fun accessibilityOn(): Boolean = RemoteAccessibilityService.instance != null

    /** May the lock screen be brought in front of another app? (« Afficher par-dessus les autres apps » or the accessibility service.) */
    fun canEnforce(ctx: Context): Boolean = accessibilityOn() || runCatching { Settings.canDrawOverlays(ctx) }.getOrDefault(false)

    fun screenOn(ctx: Context): Boolean = runCatching { (ctx.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager).isInteractive }.getOrDefault(true)

    /** The loop ran recently, or the screen is off (then it is meant to sleep). A dead loop must not leave « active » on the screen. */
    fun fresh(ctx: Context): Boolean = !screenOn(ctx) || SystemClock.elapsedRealtime() - lastPollAt < 3 * POLL_MS + 5_000

    /** From [RemoteAccessibilityService]: a window of [pkg] just came to the front. */
    fun onWindowChanged(pkg: String) {
        val ctx = app ?: return
        if (!ParentalHub.superviseWanted() || pkg in IGNORED || pkg in ime(ctx)) return
        a11yPkg = pkg; a11yAt = SystemClock.elapsedRealtime()
        handler?.post { decide(pkg) }
    }

    private fun ime(ctx: Context): Set<String> {
        if (ime.isEmpty()) ime = runCatching {
            (ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).inputMethodList.map { it.packageName }.toSet()
        }.getOrDefault(emptySet())
        return ime
    }

    private val poll = object : Runnable {
        override fun run() {
            try { pollOnce() } catch (_: Throwable) { /* never kill the loop */ }
            lastPollAt = SystemClock.elapsedRealtime()
            handler?.postDelayed(this, POLL_MS)
        }
    }

    private fun pollOnce() {
        val ctx = app ?: return
        if (!ParentalHub.superviseWanted() || !screenOn(ctx)) {
            tracker.reset(); lastQuery = 0; lastUsagePkg = null; a11yPkg = null
            decide(null)
            return
        }
        var pkg: String? = null
        if (usageGranted(ctx)) {
            val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            if (usm != null) {
                val now = System.currentTimeMillis()
                val begin = if (lastQuery == 0L) now - BOOT_WINDOW_MS else lastQuery - 1_000
                val events = ArrayList<FgEvent>()
                val ue = usm.queryEvents(begin, now)
                val ev = UsageEvents.Event()
                while (ue.hasNextEvent()) {
                    ue.getNextEvent(ev)
                    val p = ev.packageName ?: continue
                    @Suppress("DEPRECATION")
                    when (ev.eventType) {
                        UsageEvents.Event.MOVE_TO_FOREGROUND -> events += FgEvent(p, true, ev.timeStamp)
                        UsageEvents.Event.MOVE_TO_BACKGROUND -> events += FgEvent(p, false, ev.timeStamp)
                    }
                }
                lastQuery = now
                val u = tracker.feed(events)
                if (u != lastUsagePkg) { lastUsagePkg = u; usageChangedAt = SystemClock.elapsedRealtime() }
                pkg = u
            }
        }
        // the accessibility signal wins when it is connected and newer than the last change of the usage log
        val a = a11yPkg
        if (accessibilityOn() && a != null && (pkg == null || a11yAt >= usageChangedAt)) pkg = a
        decide(pkg)
    }

    private fun decide(pkg: String?) {
        if (pkg == current) return
        current = pkg
        runCatching { onChange(pkg) }
    }
}
