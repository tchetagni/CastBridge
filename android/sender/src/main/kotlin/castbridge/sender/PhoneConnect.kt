package castbridge.sender

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.provider.Settings
import android.util.Log
import castbridge.core.connect.ConnectAgent
import castbridge.core.connect.ConnectState
import castbridge.core.connect.CrashStore
import castbridge.core.connect.KeyValueStore
import castbridge.core.connect.Routes
import castbridge.core.connect.Scrub
import castbridge.core.connect.ServerLink
import castbridge.core.device.DeviceFacts
import castbridge.core.telemetry.EventQueue
import castbridge.core.telemetry.ScreenClock
import castbridge.core.telemetry.SessionTracker
import castbridge.core.update.UpdateKeys
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/** The phone app process: crash recorder, sessions, and the link with the CastBridge server (started with the app). */
class CastBridgeApp : Application() {
    private val main = Handler(Looper.getMainLooper())
    private val sessionCheck = Runnable { runCatching { PhoneConnect.sessions.check() } }

    override fun onCreate() {
        super.onCreate()
        ThemePrefs.load(this)
        PhoneConnect.init(this)
        // Light crash handler: one small file written at once, sent at the next start (POST /api/v1/devices/crash).
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching { PhoneConnect.crashes.record(e, PhoneConnect.versionCode, PhoneConnect.screens.screen) }
            previous?.uncaughtException(t, e)
        }
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(a: Activity) { main.removeCallbacks(sessionCheck); runCatching { PhoneConnect.sessions.shown() } }
            override fun onActivityStopped(a: Activity) {
                runCatching { PhoneConnect.sessions.hidden() }
                main.removeCallbacks(sessionCheck); main.postDelayed(sessionCheck, 35_000)
            }
            override fun onActivityCreated(a: Activity, b: Bundle?) {}
            override fun onActivityResumed(a: Activity) { PhoneUpdater.foreground = java.lang.ref.WeakReference(a) }
            override fun onActivityPaused(a: Activity) { if (PhoneUpdater.foreground?.get() === a) PhoneUpdater.foreground = null }
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
            override fun onActivityDestroyed(a: Activity) {}
        })
        PhoneConnect.agent.start()
    }
}

/** Private SharedPreferences as the [KeyValueStore] of [ConnectState]. */
class PrefsStore(ctx: Context, name: String) : KeyValueStore {
    private val sp = ctx.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)
    override fun get(key: String): String? = sp.getString(key, null)
    override fun put(key: String, value: String?) { sp.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply() }
}

/**
 * Everything of the phone app that talks to the CastBridge server (docs/API-SERVER.md, docs/TELEMETRY.md): one
 * [ServerLink] run by a [ConnectAgent] while the process lives (tick every minute, heartbeat every 15 min, update check
 * at start then every 12 h). Telemetry helpers never throw.
 */
@SuppressLint("StaticFieldLeak")
object PhoneConnect {
    private lateinit var app: Context
    lateinit var state: ConnectState; private set
    lateinit var link: ServerLink; private set
    lateinit var agent: ConnectAgent; private set
    lateinit var crashes: CrashStore; private set
    lateinit var updater: PhoneUpdater; private set
    var versionCode = 0; private set
    var versionName = ""; private set
    val screens = ScreenClock({ if (::link.isInitialized) link.telemetry else null })
    val sessions = SessionTracker({ if (::link.isInitialized) link.telemetry else null })
    private val _version = MutableStateFlow(0)
    /** Bumped each time something shown on a screen changed (status, progress): Compose screens collect it. */
    val version: StateFlow<Int> = _version

    @Synchronized fun init(ctx: Context) {
        if (::link.isInitialized) return
        app = ctx.applicationContext
        val pi = app.packageManager.getPackageInfo(app.packageName, 0)
        @Suppress("DEPRECATION")
        versionCode = if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode.toInt() else pi.versionCode
        versionName = pi.versionName.orEmpty()
        state = ConnectState(PrefsStore(app, "castbridge_connect"), BuildConfig.DEFAULT_SERVER)
        crashes = CrashStore(File(app.filesDir, "crashes"))
        updater = PhoneUpdater(app) { _version.value++ }
        val keys = UpdateKeys.PUBLIC_KEYS + listOf(BuildConfig.EXTRA_UPDATE_KEY).filter { it.isNotBlank() }
        link = ServerLink(
            app = "phone",
            installed = ServerLink.Installed(versionCode, versionName, Build.SUPPORTED_ABIS.toList(), Build.VERSION.SDK_INT),
            state = state, facts = { PhoneFacts(app, state) }, salt = "castbridge-phone", routes = Routes(),
            queue = EventQueue(File(app.filesDir, "telemetry/events.jsonl")), crashes = crashes,
            keys = keys, hooks = updater,
        )
        agent = ConnectAgent(link)
    }

    fun changed() { _version.value++ }

    /** feature_used {feature, source}: [id] from EventCatalog.PHONE_FEATURES. */
    fun feature(id: String, source: String = "tile") { track("feature_used", mapOf("feature" to id, "source" to source)) }

    fun track(name: String, props: Map<String, Any?> = emptyMap()) {
        if (!::link.isInitialized) return
        runCatching { link.telemetry.track(name, props) }.onFailure { Log.w("PhoneConnect", "track $name: ${it.javaClass.simpleName}") }
    }

    /** error {screen, type, message}: the message is scrubbed of paths, URLs and file names. */
    fun error(screen: String?, type: String, message: String?) =
        track("error", mapOf("screen" to screen, "type" to type, "message" to Scrub.text(message ?: type).take(200)))

    /** cast_end with the derived average rate; [error] is a short code. */
    fun castEnd(channel: String, mode: String, bytes: Long, ms: Long, ok: Boolean, error: String? = null) =
        track("cast_end", mapOf("channel" to channel, "mode" to mode, "bytes" to bytes.coerceAtLeast(0), "ms" to ms.coerceAtLeast(0),
            "kbps" to (if (ms > 0) bytes * 8 / ms else 0L), "ok" to ok, "error" to error))
}

/** What the phone tells the server about itself (no personal content). */
private class PhoneFacts(private val ctx: Context, private val state: ConnectState) : DeviceFacts {
    private val pm = ctx.packageManager
    override val app = "phone"
    override val installId: String get() = state.installId
    @SuppressLint("HardwareIds")
    override val androidId: String? = runCatching { Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID) }.getOrNull()
    override val versionCode = PhoneConnect.versionCode
    override val versionName: String? = PhoneConnect.versionName
    override val supportedAbis: List<String> = Build.SUPPORTED_ABIS.toList()
    override val sdkInt: Int = Build.VERSION.SDK_INT
    override val manufacturer: String? = Build.MANUFACTURER
    override val model: String? = Build.MODEL
    override val buildDisplay: String? = Build.DISPLAY
    override val fingerprint: String? = Build.FINGERPRINT
    override val hasLeanback: Boolean = pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    override val hasTouchscreen: Boolean = pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
    override val hasTelephony: Boolean = pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
    override val smallestWidthDp: Int = ctx.resources.configuration.smallestScreenWidthDp
    private val dm = ctx.resources.displayMetrics
    override val screenWidthPx: Int = dm.widthPixels
    override val screenHeightPx: Int = dm.heightPixels
    override val densityDpi: Int = dm.densityDpi
    override val ramTotalBytes: Long? = runCatching {
        ActivityManager.MemoryInfo().also { ctx.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }.totalMem
    }.getOrNull()
    private val stat = runCatching { StatFs(ctx.filesDir.absolutePath) }.getOrNull()
    override val storageFreeBytes: Long? = stat?.availableBytes
    override val storageTotalBytes: Long? = stat?.totalBytes
    override val btGatewayActive: Boolean = BtGatewayService.active
}
