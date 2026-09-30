package castbridge.sender

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.os.PowerManager
import android.provider.MediaStore
import android.util.Log
import castbridge.core.tv.BtProtocol
import castbridge.core.tv.Link
import castbridge.core.tv.ResumableDownload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.UUID
import kotlin.concurrent.thread

/**
 * TV -> phone download over Bluetooth (CBTD, the reverse of the Bluetooth upload): asks the paired TV for a stored file
 * and streams it into Téléchargements/CastBridge. No common Wi-Fi needed. Not resumable (a cut restarts the file).
 */
class BtDownloadService : Service() {
    @Volatile private var cancelled = false
    private var worker: Thread? = null
    private var wake: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) { cancelled = true; return START_NOT_STICKY }
        val address = intent?.getStringExtra("addr") ?: return START_NOT_STICKY
        val pin = intent.getStringExtra("pin") ?: return START_NOT_STICKY
        val name = intent.getStringExtra("name") ?: return START_NOT_STICKY
        val size = intent.getLongExtra("size", -1)
        if (worker?.isAlive == true) return START_NOT_STICKY
        try {
            val n = notification("Téléchargement Bluetooth de $name", 0)
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE) else startForeground(NOTIF, n)
        } catch (e: Exception) { _state.value = ResumableDownload.State.Failed("Service refusé"); stopSelf(); return START_NOT_STICKY }
        cancelled = false
        wake = runCatching { getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "castbridge:btdownload").also { it.acquire(6 * 3600_000L) } }.getOrNull()
        worker = thread(name = "bt-download") { run(address, pin, name, size) }
        return START_NOT_STICKY
    }

    private fun run(address: String, pin: String, name: String, size: Long) {
        BtCastPrefs(this).lastAddress = address          // any Bluetooth link memorizes the TV for auto-detection
        val adapter = getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null || !adapter.isEnabled) { finish(ResumableDownload.State.Failed("Bluetooth désactivé")); return }
        val sink = try { sinkFor(name) } catch (e: Exception) { finish(ResumableDownload.State.Failed("Impossible de créer le fichier : ${e.message}")); return }
        var last = 0L
        var done = 0L
        fun connect(): Link {
            runCatching { adapter.cancelDiscovery() }
            val sock = adapter.getRemoteDevice(address).createRfcommSocketToServiceRecord(UUID.fromString(BtProtocol.SERVICE_UUID))
            try { sock.connect() } catch (e: IOException) { runCatching { sock.close() }; throw e }
            return object : Link {
                override val input = sock.inputStream
                override val output = sock.outputStream
                override fun close() { runCatching { sock.close() } }
            }
        }
        try {
            connect().use { link ->
                BtProtocol.download(link.input, link.output, name, pin, { sink.open() }) { n ->
                    done += n
                    val now = System.currentTimeMillis()
                    if (now - last > 1000) {
                        last = now
                        _state.value = ResumableDownload.State.Downloading(done, if (size > 0) size else done)
                        runCatching { getSystemService(NotificationManager::class.java).notify(NOTIF, notification("Téléchargement Bluetooth de $name", if (size > 0) (done * 100 / size).toInt() else 0)) }
                    }
                }
                sink.finish()
                finish(ResumableDownload.State.Done(done))
            }
        } catch (e: BtProtocol.Refused) {
            finish(ResumableDownload.State.Failed(e.message ?: "refusé par la TV"))
        } catch (e: Exception) {
            Log.w(TAG, "download", e)
            finish(ResumableDownload.State.Failed(e.message ?: e.javaClass.simpleName))
        }
    }

    private fun sinkFor(name: String): Sink {
        if (Build.VERSION.SDK_INT >= 29) {
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/CastBridge")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }) ?: throw IOException("MediaStore refused")
            return object : Sink {
                override fun open(): OutputStream = contentResolver.openOutputStream(uri, "w") ?: throw IOException("cannot open")
                override fun finish() { contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null) }
            }
        }
        val f = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "CastBridge/$name").also { it.parentFile?.mkdirs() }
        return object : Sink {
            override fun open(): OutputStream = FileOutputStream(f)
            override fun finish() {}
        }
    }

    private interface Sink { fun open(): OutputStream; fun finish() }

    private fun notification(text: String, pct: Int): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Téléchargements Bluetooth", NotificationManager.IMPORTANCE_LOW))
        val cancel = PendingIntent.getService(this, 8, Intent(this, BtDownloadService::class.java).setAction(ACTION_CANCEL), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL).setContentTitle("CastBridge").setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download).setOngoing(true).setOnlyAlertOnce(true).setProgress(100, pct, false)
            .addAction(Notification.Action.Builder(null, "Annuler", cancel).build()).build()
    }

    private fun finish(s: ResumableDownload.State) { _state.value = s; stopSelf() }

    override fun onDestroy() { cancelled = true; runCatching { wake?.let { if (it.isHeld) it.release() } }; super.onDestroy() }

    companion object {
        private const val TAG = "CastBridgeBtDl"
        private const val CHANNEL = "btdownload"
        private const val NOTIF = 8
        private const val ACTION_CANCEL = "castbridge.CANCEL_BT_DOWNLOAD"
        private val _state = MutableStateFlow<ResumableDownload.State?>(null)
        val state: StateFlow<ResumableDownload.State?> = _state

        fun start(ctx: Context, address: String, pin: String, name: String, size: Long) {
            _state.value = null
            ctx.startForegroundService(Intent(ctx, BtDownloadService::class.java)
                .putExtra("addr", address).putExtra("pin", pin).putExtra("name", name).putExtra("size", size))
        }

        fun cancel(ctx: Context) { runCatching { ctx.startService(Intent(ctx, BtDownloadService::class.java).setAction(ACTION_CANCEL)) } }
    }
}
