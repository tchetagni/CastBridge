package castbridge.receiver

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Base64
import android.util.Log
import android.widget.Toast
import castbridge.core.dl.Aria2Config
import castbridge.core.dl.Aria2Supervisor
import castbridge.core.dl.DlState
import castbridge.core.dl.DownloadManager
import castbridge.core.tv.VolumeKind
import castbridge.core.tv.VolumeRegistry
import java.io.File
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The TV's download engine (aria2) and manager, one per process, independent of any screen: it keeps running with the
 * screen off as long as the process lives, and holds a Wi-Fi lock, a partial wake lock and a small foreground service only
 * while something is downloading. Start it from whatever hosts the app (activity or background service):
 *
 *     val dl = TvDownloads.start(context, volumeRegistry) { storageTarget }
 *     server extension: existing.then(dl.manager.apiExtension)
 */
class TvDownloads private constructor(private val app: Context, @Volatile private var registry: VolumeRegistry, @Volatile private var target: () -> String) {
    private val work = File(app.filesDir, "aria2").apply { mkdirs() }
    private val binary = File(app.applicationInfo.nativeLibraryDir, "libaria2c.so")
    private val main = Handler(Looper.getMainLooper())
    private val timer = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "cb-dl-host").apply { isDaemon = true } }
    private var wake: PowerManager.WakeLock? = null
    private var wifi: WifiManager.WifiLock? = null
    @Volatile private var busy = false

    val supervisor = Aria2Supervisor(
        binary = binary.takeIf { it.isFile },
        workDir = work,
        argsFor = { conf, port ->
            val session = File(work, "aria2.session").apply { if (!exists()) createNewFile() }
            val defaultDir = File(registry.volumes().firstOrNull()?.dir ?: app.filesDir, DownloadManager.DIR).apply { mkdirs() }
            Aria2Config.args(conf, session, defaultDir, port, android.os.Process.myPid().toLong(), manager.currentSettings,
                caBundle(), emptyList(), File(work, "dht.dat"))
        },
        env = mapOf("HOME" to work.absolutePath, "TMPDIR" to app.cacheDir.absolutePath),
        onLog = { Log.w(TAG, "aria2: $it") },                     // warnings only (console-log-level=warn), secret scrubbed
        onReady = { r -> manager.onEngineReady(r) },
    )

    val manager: DownloadManager = DownloadManager(
        registry = registry, workDir = work, rpc = { supervisor.rpc },
        engine = { DownloadManager.EngineStatus(supervisor.available, supervisor.rpc != null, supervisor.state.name, supervisor.message, supervisor.version) },
        target = { target() },
    )

    init {
        manager.addFinishedListener { f ->
            val what = f.files.firstOrNull() ?: f.name
            main.post { runCatching { Toast.makeText(app, "Téléchargement terminé : $what", Toast.LENGTH_LONG).show() } }
            notifyDone(f.name, f.volumeLabel)
        }
        supervisor.start()
        manager.start()
        timer.scheduleWithFixedDelay({ runCatching { keepAwake() } }, 3, 10, TimeUnit.SECONDS)
    }

    /** Wake/Wi-Fi locks and the foreground service only while there is something to download. */
    @SuppressLint("WakelockTimeout")
    private fun keepAwake() {
        val active = manager.views().any { it.state in ACTIVE }
        if (active == busy) return
        busy = active
        if (active) {
            wake = (app.getSystemService(Context.POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "castbridge:downloads")
                .apply { setReferenceCounted(false); acquire() }
            @Suppress("DEPRECATION")
            wifi = (app.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
                .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "castbridge:downloads").apply { setReferenceCounted(false); acquire() }
            DownloadService.start(app)
        } else {
            runCatching { wake?.release() }; wake = null
            runCatching { wifi?.release() }; wifi = null
            DownloadService.stop(app)
        }
    }

    /** CA certificates the TV trusts (system + user-installed), as the PEM bundle OpenSSL inside aria2 needs. */
    private fun caBundle(): File? = runCatching {
        val ks = KeyStore.getInstance("AndroidCAStore").apply { load(null) }
        val sb = StringBuilder()
        for (alias in ks.aliases()) {
            val c = ks.getCertificate(alias) as? X509Certificate ?: continue
            sb.append("-----BEGIN CERTIFICATE-----\n")
            sb.append(Base64.encodeToString(c.encoded, Base64.NO_WRAP).chunked(64).joinToString("\n"))
            sb.append("\n-----END CERTIFICATE-----\n")
        }
        File(work, "cacert.pem").apply { writeText(sb.toString()) }
    }.onFailure { Log.w(TAG, "CA bundle: ${it.javaClass.simpleName}") }.getOrNull()

    private fun notifyDone(name: String, where: String) = runCatching {
        val nm = app.getSystemService(NotificationManager::class.java)
        ensureChannel(app)
        val n = Notification.Builder(app, CHANNEL).setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Téléchargement terminé").setContentText("$name ($where)").setAutoCancel(true).build()
        nm.notify(name.hashCode(), n)
    }

    fun stop() {
        timer.shutdownNow()
        manager.stop()
        supervisor.stop()
        runCatching { wake?.release() }; runCatching { wifi?.release() }
        DownloadService.stop(app)
    }

    companion object {
        private const val TAG = "CastBridgeDL"
        const val CHANNEL = "downloads"
        private val ACTIVE = setOf(DlState.CONNECTING, DlState.METADATA, DlState.DOWNLOADING, DlState.QUEUED, DlState.SEEDING, DlState.CHECKING, DlState.MOVING)
        @Volatile private var instance: TvDownloads? = null

        /** Starts the engine and the manager once per process; later calls return the same instance. */
        @Synchronized fun start(ctx: Context, registry: VolumeRegistry, target: () -> String): TvDownloads =
            instance?.also { it.registry = registry; it.target = target; it.manager.rebind(registry) }
                ?: TvDownloads(ctx.applicationContext, registry, target).also { instance = it }

        fun get(): TvDownloads? = instance

        fun ensureChannel(ctx: Context) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL) == null)
                nm.createNotificationChannel(NotificationChannel(CHANNEL, "Téléchargements", NotificationManager.IMPORTANCE_LOW))
        }
    }
}

/**
 * Keeps the process alive (foreground service) while downloads run, e.g. when the TV screen is off and the activity is
 * stopped. Android may refuse to start it from the background (Android 12+): the downloads then rely on the wake lock and
 * on whatever else keeps the process alive.
 */
class DownloadService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        TvDownloads.ensureChannel(this)
        val n = Notification.Builder(this, TvDownloads.CHANNEL).setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("CastBridge TV").setContentText("Téléchargements en cours").setOngoing(true).build()
        runCatching {
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            else startForeground(NOTIF_ID, n)
        }.onFailure { stopSelf() }
        return START_NOT_STICKY
    }

    companion object {
        private const val NOTIF_ID = 4242
        fun start(ctx: Context) {
            runCatching { ctx.startForegroundService(Intent(ctx, DownloadService::class.java)) }
                .onFailure { Log.w("CastBridgeDL", "foreground service refused: ${it.javaClass.simpleName}") }
        }
        fun stop(ctx: Context) { runCatching { ctx.stopService(Intent(ctx, DownloadService::class.java)) } }
    }
}
