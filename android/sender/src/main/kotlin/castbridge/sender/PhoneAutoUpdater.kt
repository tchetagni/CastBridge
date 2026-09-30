package castbridge.sender

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import castbridge.core.update.UpdateClient
import castbridge.core.update.UpdateKeys
import castbridge.core.update.UpdateManifest
import castbridge.core.update.UpdateSchedule
import java.io.File

/**
 * Automatic updates of the phone app: at start-up and then periodically ([UpdateSchedule]) the phone asks the CastBridge
 * server for a newer version, verifies its signed manifest and the APK's SHA-256, downloads it in the background
 * (resumable), then asks Android to install it (the system shows its usual confirmation).
 */
object PhoneAutoUpdater {
    private const val TAG = "PhoneAutoUpdate"
    private const val CHANNEL = "updates"
    private const val NOTIF = 9
    private const val DEFAULT_URL = "https://bridge.sti-cm.com"
    private const val TIMER_MS = 10 * 60_000L

    private var ctx: Context? = null
    private var client: UpdateClient? = null
    private var schedule: UpdateSchedule? = null
    private var state = UpdateSchedule.State()
    @Volatile private var running = false
    @Volatile private var checking = false
    private val main = Handler(Looper.getMainLooper())

    fun start(context: Context) {
        val app = context.applicationContext
        if (running) return
        running = true
        ctx = app
        val prefs = app.getSharedPreferences("castbridge_update", Context.MODE_PRIVATE)
        state = UpdateSchedule.State.decode(prefs.getString("schedule", null))
        client = UpdateClient(prefs.getString("server_url", null) ?: DEFAULT_URL, "phone", UpdateKeys.PUBLIC_KEYS)
        val id = prefs.getString("install_id", null) ?: java.util.UUID.randomUUID().toString().also { prefs.edit().putString("install_id", it).apply() }
        schedule = UpdateSchedule(deviceSeed = id.hashCode().toLong())
        Thread { if (running) check(UpdateSchedule.Trigger.STARTUP) }.start()
        main.postDelayed(tick, TIMER_MS)
    }

    fun checkNow() { Thread { if (running) check(UpdateSchedule.Trigger.FORCED) }.start() }

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            if (schedule!!.isDue(state, System.currentTimeMillis(), UpdateSchedule.Trigger.TIMER))
                Thread { if (running) check(UpdateSchedule.Trigger.TIMER) }.start()
            main.postDelayed(this, TIMER_MS)
        }
    }

    @Suppress("DEPRECATION")
    private fun installedCode(): Int = runCatching {
        val pi = ctx!!.packageManager.getPackageInfo(ctx!!.packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode.toInt() else pi.versionCode
    }.getOrDefault(0)

    private fun installed(): UpdateClient.Installed = UpdateClient.Installed(
        versionCode = installedCode(), supportedAbis = Build.SUPPORTED_ABIS.toList(), sdk = Build.VERSION.SDK_INT, channel = "stable")

    private fun check(trigger: UpdateSchedule.Trigger) {
        if (checking) return
        val now = System.currentTimeMillis()
        if (!schedule!!.isDue(state, now, trigger)) return
        checking = true
        try {
            when (val c = client!!.check(installed())) {
                is UpdateClient.Check.Available -> {
                    state = schedule!!.onSuccess(state, now)
                    notify("Mise à jour ${c.manifest.versionName} disponible", "Téléchargement…")
                    downloadAndInstall(c.manifest)
                }
                is UpdateClient.Check.Failed -> state = schedule!!.onFailure(state, now)
                else -> state = schedule!!.onSuccess(state, now)
            }
        } catch (e: Exception) {
            Log.w(TAG, "check", e)
            state = schedule!!.onFailure(state, now)
        } finally {
            ctx!!.getSharedPreferences("castbridge_update", Context.MODE_PRIVATE).edit().putString("schedule", state.encode()).apply()
            checking = false
        }
    }

    private fun downloadAndInstall(m: UpdateManifest) {
        Thread {
            try {
                val f = client!!.download(m, File(ctx!!.cacheDir, "updates"), progress = { done, total ->
                    if (total > 0) notify("Mise à jour", "${done * 100 / total} %")
                })
                install(f)
            } catch (e: Exception) {
                Log.w(TAG, "download", e)
                notify("Mise à jour", "Échec : ${e.message ?: e.javaClass.simpleName}")
            }
        }.start()
    }

    @Suppress("DEPRECATION")
    private fun install(f: File) {
        val pm = ctx!!.packageManager
        val inst = pm.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        if (Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        val id = inst.createSession(params)
        inst.openSession(id).use { s ->
            f.inputStream().use { input ->
                s.openWrite("base.apk", 0, f.length()).use { o -> input.copyTo(o); s.fsync(o) }
            }
            val pi = PendingIntent.getBroadcast(ctx!!, id, Intent(ACTION).setPackage(ctx!!.packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0))
            s.commit(pi.intentSender)
        }
        notify("Mise à jour", "Installation demandée — validez si Android le demande.")
    }

    private fun notify(title: String, text: String) {
        val nm = ctx!!.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Mises à jour", NotificationManager.IMPORTANCE_LOW))
        nm.notify(NOTIF, Notification.Builder(ctx!!, CHANNEL).setContentTitle(title).setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download_done).build())
    }

    private const val ACTION = "castbridge.sender.UPDATE_RESULT"
}
