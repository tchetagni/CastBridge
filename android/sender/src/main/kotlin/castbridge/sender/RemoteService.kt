package castbridge.sender

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.VolumeProvider
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import castbridge.core.remote.KeyAction
import castbridge.core.remote.RemoteKey
import castbridge.core.remote.RemoteSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps the phone remote usable while the app is not in front (docs/REMOTE.md): the link of [RemoteController] stays up, a
 * notification carries volume / mute / play-pause keys, and a media session routes the phone's volume buttons to the TV
 * (even with the screen off) unless « Boutons de volume du téléphone → TV » is off. Stopped by « Arrêter » or the app's menu.
 */
class RemoteService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var session: MediaSession? = null
    private var text = "Télécommande TV"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { quit(); return START_NOT_STICKY }
            ACTION_KEY -> { RemoteKey.entries.firstOrNull { it.wire == intent.getStringExtra(EXTRA_KEY) }?.let { RemoteController.key(it, KeyAction.PRESS) }; return START_NOT_STICKY }
        }
        if (!RemoteController.hasSession) { stopSelf(); return START_NOT_STICKY }     // nothing to keep alive (process restarted)
        if (running) return START_NOT_STICKY
        try {
            val n = notification()
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE) else startForeground(NOTIF, n)
        } catch (e: Exception) { stopSelf(); return START_NOT_STICKY }
        running = true
        setupVolumeKeys()
        scope.launch {
            RemoteController.status.collect { st ->
                text = when (st.link) {
                    RemoteSession.Link.CONNECTED -> "Télécommande connectée"
                    else -> st.message?.takeIf { it.isNotBlank() } ?: "Télécommande : reconnexion…"
                }
                getSystemService(NotificationManager::class.java).notify(NOTIF, notification())
                session?.isActive = RemoteController.connected && RemotePrefs(this@RemoteService).volumeKeys
            }
        }
        return START_NOT_STICKY
    }

    /** A remote-volume session receives the hardware volume keys wherever the phone is (screen off, another app in front). */
    private fun setupVolumeKeys() {
        val s = MediaSession(this, "CastBridgeRemote")
        s.setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING, 0, 1f).build())
        s.setPlaybackToRemote(object : VolumeProvider(VOLUME_CONTROL_RELATIVE, 100, 50) {
            override fun onAdjustVolume(direction: Int) {
                if (!RemotePrefs(this@RemoteService).volumeKeys) return
                when { direction > 0 -> RemoteController.key(RemoteKey.VOLUME_UP, KeyAction.PRESS); direction < 0 -> RemoteController.key(RemoteKey.VOLUME_DOWN, KeyAction.PRESS) }
            }
        })
        s.isActive = false
        session = s
    }

    private fun quit() {
        RemoteController.disconnect()
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
    }

    override fun onDestroy() {
        running = false
        scope.cancel()
        session?.release(); session = null
        super.onDestroy()
    }

    private fun action(label: String, request: Int, intent: Intent) =
        Notification.Action.Builder(null, label, PendingIntent.getService(this, request, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)).build()

    private fun key(k: RemoteKey, label: String, n: Int) =
        action(label, n, Intent(this, RemoteService::class.java).setAction(ACTION_KEY).putExtra(EXTRA_KEY, k.wire))

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CHANNEL, "Télécommande TV", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, RemoteActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_stat_castbridge)
            .setContentTitle("CastBridge").setContentText(text).setOngoing(true).setContentIntent(open)
            .addAction(key(RemoteKey.VOLUME_DOWN, "Vol −", 1))
            .addAction(key(RemoteKey.VOLUME_MUTE, "Muet", 2))
            .addAction(key(RemoteKey.VOLUME_UP, "Vol +", 3))
            .addAction(key(RemoteKey.PLAY_PAUSE, "Lecture/pause", 4))
            .addAction(action("Arrêter", 5, Intent(this, RemoteService::class.java).setAction(ACTION_STOP)))
            .build()
    }

    companion object {
        private const val CHANNEL = "remote"
        private const val NOTIF = 44
        private const val ACTION_STOP = "castbridge.sender.REMOTE_STOP"
        private const val ACTION_KEY = "castbridge.sender.REMOTE_KEY"
        private const val EXTRA_KEY = "key"
        @Volatile var running = false; private set

        fun start(ctx: Context) {
            runCatching { ctx.startForegroundService(Intent(ctx, RemoteService::class.java)) }
        }
        fun stop(ctx: Context) { runCatching { ctx.startService(Intent(ctx, RemoteService::class.java).setAction(ACTION_STOP)) } }
    }
}
