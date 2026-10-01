package castbridge.dev

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import castbridge.core.ssh.SshPolicy
import castbridge.sshd.TvSshServer
import java.io.File

/** Keeps the development SSH server up (port [PORT]); restarts it if its idle watchdog stopped it. */
class DevService : Service() {
    companion object {
        const val PORT = 2223
        @Volatile var server: TvSshServer? = null
        fun start(ctx: Context) { runCatching { if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(Intent(ctx, DevService::class.java)) else ctx.startService(Intent(ctx, DevService::class.java)) } }
    }
    private val h = Handler(Looper.getMainLooper())
    private val tick = object : Runnable { override fun run() { ensure(); h.postDelayed(this, 30_000) } }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("dev", "CastBridge Dev", NotificationManager.IMPORTANCE_MIN))
        val n = Notification.Builder(this, "dev").setContentTitle("CastBridge Dev").setContentText("SSH de développement actif (port $PORT)").setSmallIcon(android.R.drawable.stat_sys_data_bluetooth).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(1, n)
        val s = TvSshServer(File(filesDir, "ssh"), File(filesDir, "inbox"), SshPolicy(), PORT, builtin = { line, out -> try { DevCommands.run(this, line, out) } catch (e: Throwable) { android.util.Log.e("CbDev", "commande: $line", e); out.write("erreur interne : $e\n".toByteArray()); 1 } })
        runCatching { BuildConfig.DEV_SSH_KEYS.lines().filter { it.isNotBlank() }.forEach { s.addKey(it) } }
        server = s
        h.post(tick)
    }

    private fun ensure() { val s = server ?: return; if (!s.running) runCatching { s.start(SshPolicy.MAX_IDLE_MINUTES) } else s.policy.enable(SshPolicy.MAX_IDLE_MINUTES) }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int { ensure(); return START_STICKY }
    override fun onBind(i: Intent?): IBinder? = null
    override fun onDestroy() { h.removeCallbacks(tick); server?.stop(); server = null; super.onDestroy() }
}
