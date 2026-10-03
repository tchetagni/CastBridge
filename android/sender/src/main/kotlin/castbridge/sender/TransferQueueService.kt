package castbridge.sender

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Foreground while the transfer queue is not empty: keeps the process alive between two files and holds the read permission the file
 * manager gave to the queued files (a content:// grant of « Ouvrir avec » only lasts as long as a component of ours holds it).
 * Each new file is announced with a new start (its ClipData adds the grant); the service ends itself when the queue is empty.
 * R-09: sticky (Android restarts it after killing the process: the saved queue is read back and goes on), the end is tied to the last start
 * (a file added while it was ending keeps it alive), and a refused foreground start is logged instead of crashing.
 */
class TransferQueueService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var watcher: Job? = null
    @Volatile private var lastStartId = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        if (!startForegroundNow()) { stopSelfResult(startId); return START_NOT_STICKY }
        // restarted by Android after the process died (null intent): read the saved queue back and go on (unless it was paused: then it stops)
        if (intent == null && !TransferQueue.paused()) TransferQueue.resume(this)
        if (watcher?.isActive != true) {
            watcher = scope.launch {
                while (true) {
                    delay(2_000)
                    // a paused queue (Android refused, or its time budget is used up) does not hold the foreground: resumed when the app is opened
                    while (TransferQueue.busy() && !TransferQueue.paused()) {
                        runCatching { getSystemService(NotificationManager::class.java)?.notify(NOTIF, notification()) }
                        delay(2_000)
                    }
                    // ends only when no file was announced since the last look (stopSelfResult ignores an older start id); else keep watching
                    val id = lastStartId
                    if ((!TransferQueue.busy() || TransferQueue.paused()) && stopSelfResult(id)) break
                }
            }
        }
        return START_STICKY
    }

    /** Android 15: the dataSync time budget is used up: pause the queue with that cause (not the background-start text) and stop. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        TransferQueue.pauseForTimeLimit()
        stopSelf()
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    private fun startForegroundNow(): Boolean = try {
        val nm = getSystemService(NotificationManager::class.java)
        nm?.createNotificationChannel(NotificationChannel(CHANNEL, "File d'attente des envois", NotificationManager.IMPORTANCE_LOW))
        if (android.os.Build.VERSION.SDK_INT >= 29) startForeground(NOTIF, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else startForeground(NOTIF, notification())
        true
    } catch (e: Exception) { Log.w("TransferQueueService", "startForeground refused", e); false }

    private fun notification(): Notification {
        // the percentage of the file being sent (Wi-Fi upload state; over Bluetooth its own notification carries it)
        val pct = (UploadService.state.value as? UploadService.State.Uploading)?.let { if (it.total > 0) (it.sent * 100 / it.total).toInt() else null }
        val running = TransferQueue.runningName()
        val text = TransferQueue.note.value ?: listOfNotNull(running?.let { "« $it »" }, TransferQueue.waitingText()).joinToString(" · ").ifEmpty { "Envoi en cours" }
        // touching the notification opens the « CastBridge TV » tab, where the queue card is (it used to open whatever tab was last shown)
        val open = PendingIntent.getActivity(this, 9, Intent(this, MainActivity::class.java).putExtra(TvHomeRequest.EXTRA, TvHomeRequest.TV)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("CastBridge : file d'envoi vers la TV")
            .setContentText(if (pct != null) "$text · $pct %" else text)
            .setContentIntent(open)
            .apply { if (pct != null) { setSubText("$pct %"); setProgress(100, pct, false) } }
            .setOngoing(true).setOnlyAlertOnce(true).build()
    }

    companion object { private const val CHANNEL = "queue"; private const val NOTIF = 9 }
}
