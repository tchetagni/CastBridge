package castbridge.sender

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import castbridge.core.tv.BtProtocol
import castbridge.core.tv.Link
import castbridge.core.tv.ResumableBtUpload
import castbridge.core.tv.ResumableUpload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import kotlin.concurrent.thread

/** Sends a file to CastBridge TV over Bluetooth (RFCOMM), resuming after link loss. Foreground service. */
@SuppressLint("MissingPermission")   // BLUETOOTH_CONNECT is checked by the UI before starting
class BtUploadService : Service() {
    @Volatile private var cancelled = false
    private var worker: Thread? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) { cancelled = true; return START_NOT_STICKY }
        val uri = intent?.data
        val address = intent?.getStringExtra(EXTRA_ADDR)
        val pin = intent?.getStringExtra(EXTRA_PIN)
        val name = intent?.getStringExtra(EXTRA_NAME)
        if (uri == null || address == null || pin == null || name == null || worker?.isAlive == true) return START_NOT_STICKY
        try {
            val n = notification("Envoi Bluetooth de $name…", 0)
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            else startForeground(NOTIF, n)
        } catch (e: Exception) {
            Log.e(TAG, "startForeground", e)
            _state.value = ResumableUpload.State.Failed("Service refusé par le système")
            stopSelf(); return START_NOT_STICKY
        }
        cancelled = false
        worker = thread(name = "bt-upload") { run(uri, address, pin, name) }
        return START_NOT_STICKY
    }

    private fun run(uri: Uri, address: String, pin: String, name: String) {
        val total = runCatching { contentResolver.openFileDescriptor(uri, "r")!!.use { it.statSize } }.getOrDefault(-1)
        if (total <= 0) { finish(ResumableUpload.State.Failed("Fichier illisible")); return }
        val adapter = getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null || !adapter.isEnabled) { finish(ResumableUpload.State.Failed("Bluetooth désactivé")); return }
        var lastNotif = 0L
        val up = ResumableBtUpload(name, total, pin,
            connect = {
                runCatching { adapter.cancelDiscovery() }   // needs BLUETOOTH_SCAN on Android 12+: optional, never fatal
                val sock = adapter.getRemoteDevice(address).createRfcommSocketToServiceRecord(UUID.fromString(BtProtocol.SERVICE_UUID))
                try { sock.connect() } catch (e: IOException) { runCatching { sock.close() }; throw e }
                object : Link {
                    override val input = sock.inputStream
                    override val output = sock.outputStream
                    override fun close() { runCatching { sock.close() } }
                }
            },
            openAt = { off -> openAt(uri, off) },
            cancelled = { cancelled })
        val result = up.run { s ->
            _state.value = s
            val now = System.currentTimeMillis()
            if (now - lastNotif > 1000) {
                lastNotif = now
                val pct = when (s) {
                    is ResumableUpload.State.Uploading -> (s.sent * 100 / s.total).toInt()
                    is ResumableUpload.State.Waiting -> (s.sent * 100 / s.total).toInt()
                    else -> 0
                }
                runCatching { getSystemService(NotificationManager::class.java).notify(NOTIF, notification("Envoi Bluetooth de $name", pct)) }
            }
        }
        finish(result)
    }

    private fun openAt(uri: Uri, offset: Long): InputStream {
        val pfd = contentResolver.openFileDescriptor(uri, "r") ?: throw IOException("cannot open")
        val fis = object : FileInputStream(pfd.fileDescriptor) {
            override fun close() { try { super.close() } finally { pfd.close() } }
        }
        fis.channel.position(offset)
        return fis
    }

    private fun notification(text: String, pct: Int): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Envoi Bluetooth", NotificationManager.IMPORTANCE_LOW))
        val cancel = android.app.PendingIntent.getService(this, 2,
            Intent(this, BtUploadService::class.java).setAction(ACTION_CANCEL), android.app.PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL).setContentTitle("CastBridge").setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_upload).setOngoing(true).setOnlyAlertOnce(true)
            .setProgress(100, pct, false)
            .addAction(Notification.Action.Builder(null, "Annuler", cancel).build()).build()
    }

    private fun finish(s: ResumableUpload.State) { _state.value = s; stopSelf() }

    override fun onDestroy() { cancelled = true; super.onDestroy() }

    companion object {
        private const val TAG = "BtUploadService"
        private const val CHANNEL = "btupload"
        private const val NOTIF = 3
        const val ACTION_CANCEL = "castbridge.CANCEL_BT_UPLOAD"
        private const val EXTRA_ADDR = "addr"
        private const val EXTRA_PIN = "pin"
        private const val EXTRA_NAME = "name"
        private val _state = MutableStateFlow<ResumableUpload.State?>(null)
        val state: StateFlow<ResumableUpload.State?> = _state

        fun start(ctx: Context, uri: Uri, fileName: String, address: String, pin: String) {
            _state.value = null
            ctx.startForegroundService(Intent(ctx, BtUploadService::class.java).setData(uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .putExtra(EXTRA_ADDR, address).putExtra(EXTRA_PIN, pin).putExtra(EXTRA_NAME, fileName))
        }

        fun cancel(ctx: Context) {
            runCatching { ctx.startService(Intent(ctx, BtUploadService::class.java).setAction(ACTION_CANCEL)) }
        }
    }
}
