package castbridge.sender

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Foreground while the transfer queue is not empty: keeps the process alive between two files and holds the read permission the file
 * manager gave to the queued files (a content:// grant of « Ouvrir avec » only lasts as long as a component of ours holds it).
 * Each new file is announced with a new start (its ClipData adds the grant); the service ends itself when the queue is empty.
 */
class TransferQueueService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var watching = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundNow()
        if (!watching) {
            watching = true
            scope.launch {
                delay(2_000)
                while (TransferQueue.busy()) {
                    getSystemService(NotificationManager::class.java)?.notify(NOTIF, notification())
                    delay(2_000)
                }
                stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    private fun startForegroundNow() {
        val nm = getSystemService(NotificationManager::class.java)
        nm?.createNotificationChannel(NotificationChannel(CHANNEL, "File d'attente des envois", NotificationManager.IMPORTANCE_LOW))
        if (android.os.Build.VERSION.SDK_INT >= 29) startForeground(NOTIF, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else startForeground(NOTIF, notification())
    }

    private fun notification(): Notification =
        Notification.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("CastBridge : envois vers la TV")
            .setContentText(TransferQueue.waitingText() ?: "Envoi en cours")
            .setOngoing(true).build()

    companion object { private const val CHANNEL = "queue"; private const val NOTIF = 9 }
}
