package castbridge.receiver

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import castbridge.core.connect.ConnectAgent
import castbridge.core.connect.ConnectState
import castbridge.core.connect.CrashStore
import castbridge.core.connect.KeyValueStore
import castbridge.core.connect.Routes
import castbridge.core.connect.Scrub
import castbridge.core.connect.ServerLink
import castbridge.core.device.DeviceFacts
import castbridge.core.quiz.QuizSync
import castbridge.core.telemetry.EventQueue
import castbridge.core.telemetry.ScreenClock
import castbridge.core.telemetry.SessionTracker
import castbridge.core.telemetry.Telemetry
import castbridge.core.tv.MediaType
import castbridge.core.tv.StoragePolicy
import castbridge.core.tv.VolumeKind
import castbridge.core.update.UpdateKeys
import castbridge.core.update.UpdateManifest
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/** Private SharedPreferences as the [KeyValueStore] of the server link (the device token lives only here). */
class PrefsStore(ctx: Context, name: String) : KeyValueStore {
    private val sp = ctx.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)
    override fun get(key: String): String? = sp.getString(key, null)
    override fun put(key: String, value: String?) { sp.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply() }
}

/**
 * The TV's link with the CastBridge server (docs/API-SERVER.md, docs/TELEMETRY.md): one per process, created by [TvApp],
 * started by [TvService] (heartbeat every 15 min while the service runs), used by every screen for the usage events.
 * Everything here is safe to call from anywhere: failures never reach the caller.
 */
object TvConnect {
    private const val TAG = "CastBridgeConnect"
    @Volatile var link: ServerLink? = null; private set
    @Volatile var agent: ConnectAgent? = null; private set
    /** Content reports and per item usage totals (docs/CONTENT-VALIDATION.md); set by [init]. */
    @Volatile var feedback: castbridge.core.content.ContentFeedback? = null; private set
    /** Release channel set by the server for this TV ("beta" testers play content that is not validated yet). */
    fun channel(): castbridge.core.content.Channel = castbridge.core.content.Channel.of(link?.state?.channel)
    private lateinit var app: Context
    private val main = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    @Volatile var lastError: String? = null

    /** Which screen is shown (screen_view / screen_time), and the app session. */
    val screens = ScreenClock({ link?.telemetry })
    val sessions = SessionTracker({ link?.telemetry })

    @Synchronized fun init(ctx: Context) {
        if (link != null) return
        app = ctx.applicationContext
        val state = ConnectState(PrefsStore(app, "castbridge_connect"), BuildConfig.DEFAULT_SERVER)
        val pi = runCatching { app.packageManager.getPackageInfo(app.packageName, 0) }.getOrNull()
        @Suppress("DEPRECATION")
        val code = pi?.let { if (Build.VERSION.SDK_INT >= 28) it.longVersionCode.toInt() else it.versionCode } ?: 0
        val keys = (UpdateKeys.PUBLIC_KEYS + BuildConfig.EXTRA_UPDATE_KEY).filter { it.isNotBlank() }
        val quizFile = File(app.filesDir, "quiz/questions-cache.json")
        val fb = castbridge.core.content.ContentFeedback(File(app.filesDir, "content"), { castbridge.core.content.Channel.of(state.channel) }, { link?.telemetry })
        feedback = fb
        val l = ServerLink(
            app = "tv",
            installed = ServerLink.Installed(code, pi?.versionName, Build.SUPPORTED_ABIS.toList(), Build.VERSION.SDK_INT),
            state = state, facts = { TvFacts(app) }, salt = SALT,
            // the TV's own network first, then the Internet of a phone shared over Bluetooth (BtGatewayHost, SOCKS 127.0.0.1:1080). relay-R1: the single truth of the TV (TvNet) says which
            // path goes first; a failed call tells it at once (the TV's own network is then checked now, the pipe is confirmed once: no periodic probe)
            routes = Routes(gateway = { TvNet.gatewayProxy() }, preferred = TvNet::preferredVia, onGatewayFailure = TvNet::relayFailureSeen, onDirectFailure = TvNet::markChanged),
            queue = EventQueue(File(app.filesDir, "telemetry/events.jsonl")),
            crashes = CrashStore(File(app.filesDir, "crashes")),
            keys = keys, hooks = Hooks, quiz = QuizSync(QuizHub.cachedSource(app), quizFile), quizPacks = QuizHub.packHook(app, keys),
            feedback = fb,
        )
        link = l
        agent = ConnectAgent(l)
    }

    /** Called by [TvService] once it runs: start-up work, then the periodic ticks. */
    fun start(ctx: Context) { init(ctx); agent?.start() }

    /** Runs [block] on the link's background thread (network I/O). */
    fun post(block: ServerLink.() -> Unit) { agent?.post(block) }

    /** Runs [block] on the link's thread and waits for its result ([timeoutMs] at most; null on failure or time-out). */
    fun <T> call(timeoutMs: Long, block: ServerLink.() -> T): T? {
        val a = agent ?: return null
        val latch = java.util.concurrent.CountDownLatch(1)
        var r: T? = null
        a.post { r = runCatching { block() }.getOrNull(); latch.countDown() }
        latch.await(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
        return r
    }

    /** State of the link for GET /api/server (no secret: never the device token). */
    fun stateJson(): String {
        val l = link ?: return "{}"
        val s = l.state
        val u = l.update
        val q = runCatching { QuizHub.cachedSource(app).serverCount() }.getOrDefault(0)
        return castbridge.core.net.JsonLite.write(linkedMapOf(
            "baseUrl" to s.baseUrl, "customServer" to s.customServer, "shortId" to s.shortId,
            "consent" to (if (s.needsConsent) null else if (s.consent == castbridge.core.telemetry.Consent.USAGE) "usage" else "essential"),
            "needsConsent" to s.needsConsent, "lastContactAt" to s.lastContactAt, "lastContactOk" to s.lastContactOk,
            "lastContactMessage" to s.lastContactMessage, "via" to s.lastContactVia, "blocked" to s.blocked, "channel" to s.channel,
            "versionCode" to l.installed.versionCode, "versionName" to l.installed.versionName,
            "update" to linkedMapOf("phase" to u.phase.name.lowercase(), "message" to u.message, "versionName" to u.manifest?.versionName,
                "versionCode" to u.manifest?.versionCode, "mandatory" to u.mandatory, "done" to u.done, "total" to u.total,
                "lastCheckAt" to s.updateSchedule.lastCheckAt),
            "quiz" to linkedMapOf("syncedAt" to s.quizSyncedAt, "message" to s.quizMessage, "serverQuestions" to q),
        ))
    }

    fun addListener(l: () -> Unit) { listeners += l }
    fun removeListener(l: () -> Unit) { listeners -= l }

    fun needsConsent(): Boolean = link?.state?.needsConsent == true

    // ------------------------------------------------------------------ usage events (filtered by the consent)

    fun track(name: String, props: Map<String, Any?> = emptyMap()) { runCatching { link?.telemetry?.track(name, props) } }

    /** A home tile / menu entry / phone order used ([castbridge.core.telemetry.EventCatalog.TV_FEATURES]). */
    fun feature(id: String, source: String = "tile") { runCatching { link?.telemetry?.featureUsed(id, source) } }

    /** Non-fatal error worth counting (message scrubbed of paths and file names). */
    fun error(screen: String?, type: String, message: String) {
        val m = Scrub.text(message).take(200)
        lastError = m
        runCatching { link?.telemetry?.error(screen, type, m) }
    }

    /** Distinct things counted without naming them ([Telemetry.hashForCounting], salt never sent). */
    fun hash(value: String): String = Telemetry.hashForCounting(value, link?.state?.countingSalt ?: "")

    /** « Apprendre » event (LearnProgress.EVENTS): content ids only, never the pupil's first name. */
    fun learn(action: String, data: Map<String, Any?>) {
        val keep = setOf("pack", "lesson", "subject", "exercise", "correct", "points", "max", "attempt", "box", "score", "badge", "level", "version")
        val props = LinkedHashMap<String, Any?>()
        props["action"] = action
        for ((k, v) in data) when (k) {
            "outOf" -> props["out_of"] = v
            "timeMs", "durationMs" -> props["ms"] = v
            in keep -> props[k] = v
        }
        track("learn", props)
    }

    // ------------------------------------------------------------------ platform side of the link

    private object Hooks : ServerLink.Hooks {
        override fun downloadDir(size: Long): File? {
            val need = size + (64L shl 20)
            // same policy as the uploads (docs/STORAGE.md): a USB drive first when it can take the file, else internal storage
            val svc = TvService.running
            if (svc != null) runCatching {
                val vols = svc.registry.snapshot().filter { it.kind != VolumeKind.SAF }
                val plan = StoragePolicy.plan(vols, StoragePolicy.AUTO, need, minFree = 64L shl 20)
                plan.candidates.firstOrNull()?.volume?.dir?.let { return File(it, ".castbridge-update").also { d -> d.mkdirs() } }
                return null
            }
            val d = File(app.filesDir, "updates")
            return if (d.apply { mkdirs() }.usableSpace >= need) d else null
        }

        override fun installReady(apk: File, m: UpdateManifest, mandatory: Boolean, userAsked: Boolean): Boolean {
            val svc = TvService.running ?: return false
            // never interrupt a film for an optional update: installed when nothing plays
            val playing = runCatching { svc.playerBridge.state().state in setOf("playing", "buffering", "paused") }.getOrDefault(false)
            if (playing && !userAsked && !mandatory) return false
            val u = svc.updater ?: return false
            return u.installVerified(apk, m.versionCode, m.versionName) { error -> post { installFailed(error) } }
        }

        override fun changed() { main.post { listeners.forEach { runCatching { it() } } } }

        /** relay-R1 (REL-F7): no big background download while the phone's pipe is precious (a game is running, or the phone is on mobile data). */
        override fun backgroundBulkAllowed(): Boolean = TvNet.backgroundBulkAllowed()
    }

    const val SALT = "castbridge-tv"

    // ------------------------------------------------------------------ crashes and sessions (installed by TvApp)

    fun installCrashHandler(ctx: Context) {
        val store = CrashStore(File(ctx.applicationContext.filesDir, "crashes"))
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching { store.record(e, link?.installed?.versionCode ?: 0, screens.screen) }
            if (previous != null) previous.uncaughtException(t, e) else { android.os.Process.killProcess(android.os.Process.myPid()); System.exit(10) }
        }
    }

    /** Activities → sessions and screen time (PlayerActivity sets its own sub-screens: home, library, player…). */
    val lifecycle = object : Application.ActivityLifecycleCallbacks {
        private val check = Runnable { sessions.check() }
        override fun onActivityStarted(a: Activity) {
            sessions.shown()
            screenOf(a)?.let { screens.enter(it) }
        }
        override fun onActivityStopped(a: Activity) {
            sessions.hidden()
            // the screen of this activity ends (unless another one already took over)
            val mine = screenOf(a) ?: (a as? PlayerActivity)?.screenId
            if (mine != null && screens.screen == mine) screens.leave()
            main.removeCallbacks(check); main.postDelayed(check, 35_000)
        }
        override fun onActivityResumed(a: Activity) {}
        override fun onActivityPaused(a: Activity) {}
        override fun onActivityCreated(a: Activity, b: Bundle?) {}
        override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
        override fun onActivityDestroyed(a: Activity) {}
    }

    private fun screenOf(a: Activity): String? = when (a) {
        is QuizActivity -> "quiz"
        is ChessActivity -> "chess"
        is LearnActivity -> "learn"
        is DownloadsActivity -> "downloads"
        is RemoteSetupActivity -> "remote"
        is ServerActivity -> a.screenId()
        else -> null                              // PlayerActivity: home / library / player / settings, set by itself
    }

    fun logw(m: String) { Log.w(TAG, m) }
}

/** What the server is told about this TV (DeviceReport): generic Android facts, no personal content. */
private class TvFacts(private val ctx: Context) : DeviceFacts {
    private val pm = ctx.packageManager
    private val pi = runCatching { pm.getPackageInfo(ctx.packageName, 0) }.getOrNull()
    private val svc = TvService.running
    private val metrics = DisplayMetrics().also {
        @Suppress("DEPRECATION")
        runCatching { (ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(it) }
    }
    override val app = "tv"
    override val installId = ""                                   // replaced by ServerLink with the stored one
    override val androidId: String? = runCatching { Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID) }.getOrNull()
    @Suppress("DEPRECATION")
    override val versionCode = pi?.let { if (Build.VERSION.SDK_INT >= 28) it.longVersionCode.toInt() else it.versionCode } ?: 0
    override val versionName = pi?.versionName
    override val supportedAbis: List<String> = Build.SUPPORTED_ABIS.toList()
    override val sdkInt = Build.VERSION.SDK_INT
    override val manufacturer: String? = Build.MANUFACTURER
    override val model: String? = Build.MODEL
    // free text typed by the user (often a first name): never sent (docs/TELEMETRY.md § 1); the server ignores it anyway
    override val deviceName: String? = null
    override val buildDisplay: String? = Build.DISPLAY
    override val fingerprint: String? = Build.FINGERPRINT
    override val hasLeanback = pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    override val hasGoogleTv = pm.hasSystemFeature("com.google.android.feature.GOOGLE_EXPERIENCE") && hasLeanback
    override val isTelevisionUi = runCatching { ctx.getSystemService(UiModeManager::class.java).currentModeType == Configuration.UI_MODE_TYPE_TELEVISION }.getOrNull()
    override val hasTouchscreen = pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
    override val hasTelephony = pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
    override val smallestWidthDp = ctx.resources.configuration.smallestScreenWidthDp
    override val screenWidthPx = metrics.widthPixels.takeIf { it > 0 }
    override val screenHeightPx = metrics.heightPixels.takeIf { it > 0 }
    override val densityDpi = metrics.densityDpi.takeIf { it > 0 }
    override val ramTotalBytes = runCatching { ActivityManager.MemoryInfo().also { ctx.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }.totalMem }.getOrNull()
    private val stat = runCatching { StatFs(ctx.filesDir.absolutePath) }.getOrNull()
    override val storageFreeBytes = stat?.availableBytes
    override val storageTotalBytes = stat?.totalBytes
    private val drives = runCatching { svc?.registry?.snapshot()?.filter { it.kind == VolumeKind.REMOVABLE } }.getOrNull()
    override val usbPresent = drives?.isNotEmpty()
    override val usbFreeBytes = drives?.sumOf { maxOf(0L, it.free) }
    override val btGatewayActive = svc?.gateway?.connected
    override val sshEnabled = svc?.ssh?.running
    override val wifiDirectActive = svc?.wd?.let { it.active != null }
    override val videoCount = runCatching { svc?.server?.libraryItems()?.count { it.type != MediaType.OTHER } }.getOrNull()
    override val lastError = TvConnect.lastError
}
