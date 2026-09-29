package castbridge.sender

import android.app.*
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.IBinder
import android.os.PowerManager

/** Foreground service keeping the HTTP server (and Wi-Fi) alive with the screen off. */
class ServerService : Service() {
    private var wifiLock: WifiManager.WifiLock? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("cast", "Diffusion", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "cast").setContentTitle("CastBridge")
            .setContentText("Diffusion en cours").setSmallIcon(android.R.drawable.ic_media_play).build()
        startForeground(1, n)
        if (server == null) {
            server = MediaServer(contentResolver).also { it.start() }
            wifiLock = (applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
                .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "castbridge").also { it.acquire() }
            wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "castbridge:cast").also { it.acquire(4 * 3600_000L) }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        server?.stop(); server = null
        wifiLock?.release(); wakeLock?.let { if (it.isHeld) it.release() }
    }

    companion object { @Volatile var server: MediaServer? = null }
}
