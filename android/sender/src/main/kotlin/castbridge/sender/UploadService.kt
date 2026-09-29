package castbridge.sender

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import castbridge.core.tv.ResumableUpload
import castbridge.core.tv.TvClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import kotlin.concurrent.thread

/**
 * Sends a whole video to CastBridge TV, resuming after any network loss, then starts playback.
 * Runs as a foreground service so it survives the screen turning off.
 */
class UploadService : Service() {
    data class Job(val fileName: String, val tvName: String, val manualHost: String?, val pin: String? = null)

    sealed class State {
        object Idle : State()
        data class Uploading(val job: Job, val sent: Long, val total: Long) : State()
        data class Waiting(val job: Job, val sent: Long, val total: Long, val reason: String) : State()
        data class Done(val job: Job) : State()
        data class Failed(val job: Job?, val reason: String) : State()
    }

    @Volatile private var cancelled = false
    private var worker: Thread? = null
    private var discovery: TvDiscovery? = null
    private var netCallback: ConnectivityManager.NetworkCallback? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var lastNotified = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) { cancelled = true; stopSelf(); return START_NOT_STICKY }
        val uri = intent?.data
        val tvName = intent?.getStringExtra(EXTRA_TV)
        if (uri == null || tvName == null || worker?.isAlive == true) return START_NOT_STICKY
        val name = intent.getStringExtra(EXTRA_NAME) ?: uri.lastPathSegment ?: "video"
        val job = Job(name, tvName, intent.getStringExtra(EXTRA_HOST), intent.getStringExtra(EXTRA_PIN))
        try {
            val n = notification("Envoi de $name…", 0)
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            else startForeground(NOTIF, n)
        } catch (e: Exception) {
            Log.e(TAG, "startForeground", e)
            _state.value = State.Failed(job, "Service refusé par le système : ${e.message}")
            stopSelf(); return START_NOT_STICKY
        }
        acquireLocks()
        val disc = if (job.manualHost == null) TvDiscovery(this).also { it.start(); discovery = it } else null
        watchNetwork(disc)
        cancelled = false
        worker = thread(name = "upload") { runJob(uri, job, disc) }
        return START_NOT_STICKY
    }

    private fun runJob(uri: Uri, job: Job, disc: TvDiscovery?) {
        val total = runCatching { contentResolver.openFileDescriptor(uri, "r")!!.use { it.statSize } }.getOrDefault(-1)
        if (total <= 0) { finish(State.Failed(job, "Fichier illisible")); return }
        val resolve: () -> String? = {
            job.manualHost?.let { h -> if (':' in h) "http://$h" else "http://$h:8765" } ?: disc?.find(job.tvName)?.base
        }
        val up = ResumableUpload(job.fileName, total, resolve, { off -> openAt(uri, off) }, { cancelled }, pin = job.pin)
        val result = up.run { s ->
            _state.value = when (s) {
                is ResumableUpload.State.Uploading -> State.Uploading(job, s.sent, s.total)
                is ResumableUpload.State.Waiting -> State.Waiting(job, s.sent, s.total, s.reason)
                ResumableUpload.State.Done -> State.Done(job)
                is ResumableUpload.State.Failed -> State.Failed(job, s.reason)
            }
            notifyProgress(s)
        }
        if (result == ResumableUpload.State.Done) {
            val base = resolve()
            val played = base != null && runCatching { TvClient(base, job.pin).play(job.fileName) }.isSuccess
            finish(if (played) State.Done(job) else State.Failed(job, "Fichier envoyé, mais lancement impossible : réessayez « Lire »"))
        } else finish(_state.value.takeIf { it is State.Failed } ?: State.Failed(job, "annulé"))
    }

    private fun openAt(uri: Uri, offset: Long): InputStream {
        val pfd = contentResolver.openFileDescriptor(uri, "r") ?: throw IOException("cannot open")
        val fis = ParcelFileInputStream(pfd)
        fis.channel.position(offset)
        return fis
    }

    /** FileInputStream that also closes its ParcelFileDescriptor. */
    private class ParcelFileInputStream(private val pfd: android.os.ParcelFileDescriptor) : FileInputStream(pfd.fileDescriptor) {
        override fun close() { try { super.close() } finally { pfd.close() } }
    }

    /** On any network change, rediscover the TV (it may have a new address) so the upload resumes fast. */
    private fun watchNetwork(disc: TvDiscovery?) {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { disc?.restart() }
        }
        runCatching { cm.registerDefaultNetworkCallback(cb); netCallback = cb }
    }

    private fun notifyProgress(s: ResumableUpload.State) {
        val now = System.currentTimeMillis()
        if (now - lastNotified < 1000 && s is ResumableUpload.State.Uploading) return
        lastNotified = now
        val (text, pct) = when (s) {
            is ResumableUpload.State.Uploading -> "Envoi vers la TV" to (s.sent * 100 / s.total).toInt()
            is ResumableUpload.State.Waiting -> "En attente du réseau (${s.reason})" to (s.sent * 100 / s.total).toInt()
            else -> return
        }
        runCatching { getSystemService(NotificationManager::class.java).notify(NOTIF, notification(text, pct)) }
    }

    private fun notification(text: String, pct: Int): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Envoi vers la TV", NotificationManager.IMPORTANCE_LOW))
        val cancel = android.app.PendingIntent.getService(this, 1,
            Intent(this, UploadService::class.java).setAction(ACTION_CANCEL), android.app.PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL).setContentTitle("CastBridge").setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_upload).setOngoing(true).setOnlyAlertOnce(true)
            .setProgress(100, pct, false)
            .addAction(Notification.Action.Builder(null, "Annuler", cancel).build()).build()
    }

    private fun acquireLocks() {
        wakeLock = runCatching {
            getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "castbridge:upload")
                .also { it.acquire(6 * 3600_000L) }
        }.getOrNull()
        wifiLock = runCatching {
            (applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
                .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "castbridge-upload").also { it.acquire() }
        }.getOrNull()
    }

    private fun finish(s: State) {
        _state.value = s
        stopSelf()
    }

    override fun onDestroy() {
        cancelled = true
        discovery?.stop()
        netCallback?.let { cb -> runCatching { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(cb) } }
        runCatching { wakeLock?.let { if (it.isHeld) it.release() } }
        runCatching { wifiLock?.let { if (it.isHeld) it.release() } }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "UploadService"
        private const val CHANNEL = "upload"
        private const val NOTIF = 2
        const val ACTION_CANCEL = "castbridge.CANCEL_UPLOAD"
        const val EXTRA_TV = "tv"
        const val EXTRA_NAME = "name"
        const val EXTRA_HOST = "host"
        const val EXTRA_PIN = "pin"
        private val _state = MutableStateFlow<State>(State.Idle)
        val state: StateFlow<State> = _state

        fun start(ctx: Context, uri: Uri, fileName: String, tvName: String, manualHost: String?, pin: String? = null) {
            val i = Intent(ctx, UploadService::class.java).setData(uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .putExtra(EXTRA_TV, tvName).putExtra(EXTRA_NAME, fileName).putExtra(EXTRA_HOST, manualHost).putExtra(EXTRA_PIN, pin?.takeIf { it.isNotEmpty() })
            _state.value = State.Idle
            ctx.startForegroundService(i)
        }

        fun cancel(ctx: Context) {
            runCatching { ctx.startService(Intent(ctx, UploadService::class.java).setAction(ACTION_CANCEL)) }
        }
    }
}
