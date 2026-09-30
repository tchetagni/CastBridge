package castbridge.sender

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import castbridge.core.connect.ServerLink
import castbridge.core.update.UpdateManifest
import java.io.File
import java.lang.ref.WeakReference
import java.security.MessageDigest

/**
 * Installs the verified APK that [ServerLink] downloaded (docs/API-SERVER.md § 1):
 * - silently when this app is the owner of its own installation (Android 12+ lets the owner update without asking);
 * - otherwise with Android's confirmation: at once if the user pressed « Installer », else through a notification
 *   « Mise à jour prête — Installer » (once per version) whose tap opens the app and starts it.
 * The APK must carry the same signing key as the installed app (Android refuses anything else: checked first here).
 */
class PhoneUpdater(private val ctx: Context, private val onChanged: () -> Unit) : ServerLink.Hooks {
    @Volatile var message: String? = null; private set
    private val sp = ctx.getSharedPreferences("castbridge_updater", Context.MODE_PRIVATE)

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val st = i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
            when (st) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    @Suppress("DEPRECATION")
                    val confirm = i.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                    val act = foreground?.get()
                    val shown = confirm != null && runCatching {
                        if (act != null) act.startActivity(confirm) else ctx.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }.isSuccess
                    set(if (shown) "Confirmez l'installation à l'écran" else "Ouvrez CastBridge pour confirmer l'installation")
                    if (!shown) PhoneConnect.agent.post { installFailed("no_confirmation") }
                }
                PackageInstaller.STATUS_SUCCESS -> set("Mise à jour installée")
                else -> {
                    val code = when (st) {
                        PackageInstaller.STATUS_FAILURE_ABORTED -> "aborted"
                        PackageInstaller.STATUS_FAILURE_CONFLICT -> "conflict"
                        PackageInstaller.STATUS_FAILURE_STORAGE -> "storage"
                        PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "incompatible"
                        PackageInstaller.STATUS_FAILURE_INVALID -> "invalid"
                        PackageInstaller.STATUS_FAILURE_BLOCKED -> "blocked"
                        else -> "status_$st"
                    }
                    set("Installation non faite ($code)")
                    PhoneConnect.agent.post { installFailed(code) }
                }
            }
        }
    }

    init {
        val f = IntentFilter(ACTION)
        if (Build.VERSION.SDK_INT >= 33) ctx.registerReceiver(receiver, f, Context.RECEIVER_NOT_EXPORTED)
        else @Suppress("UnspecifiedRegisterReceiverFlag") ctx.registerReceiver(receiver, f)
    }

    private fun set(m: String) { message = m; onChanged() }

    override fun changed() { onChanged() }

    override fun downloadDir(size: Long): File? {
        val dir = File(ctx.filesDir, "updates").apply { mkdirs() }
        // old APKs of other versions go
        dir.listFiles().orEmpty().filter { it.name.endsWith(".apk") && System.currentTimeMillis() - it.lastModified() > 7 * 86_400_000L }.forEach { it.delete() }
        return dir.takeIf { it.usableSpace >= size + (50L shl 20) }
    }

    override fun installReady(apk: File, m: UpdateManifest, mandatory: Boolean, userAsked: Boolean): Boolean {
        if (!sameSigner(apk)) {
            set("Mise à jour refusée : l'APK n'est pas signé avec la même clé que l'app installée")
            PhoneConnect.agent.post { installFailed("signature") }
            return false
        }
        val owner = isOwner()
        if (!owner && !userAsked) {
            if (sp.getInt("notified", 0) != m.versionCode) { notifyReady(m); sp.edit().putInt("notified", m.versionCode).apply() }
            set("Mise à jour prête : version ${m.versionName}")
            return false
        }
        if (!ctx.packageManager.canRequestPackageInstalls()) {
            set("Autorisez « Installer des applications inconnues » pour CastBridge, puis touchez « Installer »")
            runCatching {
                val i = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                foreground?.get()?.startActivity(i) ?: ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            return false
        }
        return try {
            val inst = ctx.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            params.setAppPackageName(ctx.packageName)
            if (Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            val id = inst.createSession(params)
            inst.openSession(id).use { s ->
                apk.inputStream().use { inp -> s.openWrite("base.apk", 0, apk.length()).use { o -> inp.copyTo(o, 64 * 1024); s.fsync(o) } }
                val pi = PendingIntent.getBroadcast(ctx, id, Intent(ACTION).setPackage(ctx.packageName),
                    PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0))
                s.commit(pi.intentSender)
            }
            ctx.getSystemService(NotificationManager::class.java).cancel(NOTIF)
            set("Installation de la version ${m.versionName}…")
            true
        } catch (e: Exception) {
            Log.w(TAG, "install", e)
            set("Installation impossible : ${e.javaClass.simpleName}")
            PhoneConnect.agent.post { installFailed("session") }
            false
        }
    }

    /** Android 12+ updates silently only an app whose installer of record (or update owner, 14+) is the app itself. */
    private fun isOwner(): Boolean = runCatching {
        if (Build.VERSION.SDK_INT < 31) return false
        val src = ctx.packageManager.getInstallSourceInfo(ctx.packageName)
        src.installingPackageName == ctx.packageName || (Build.VERSION.SDK_INT >= 34 && src.updateOwnerPackageName == ctx.packageName)
    }.getOrDefault(false)

    private fun notifyReady(m: UpdateManifest) = runCatching {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Mises à jour", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(ctx, 7, Intent(ctx, MainActivity::class.java).putExtra(EXTRA_INSTALL, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        nm.notify(NOTIF, Notification.Builder(ctx, CHANNEL).setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Mise à jour prête — Installer").setContentText("CastBridge ${m.versionName} : touchez pour installer")
            .setAutoCancel(true).setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "Installer", open).build()).build())
    }

    @Suppress("DEPRECATION")
    private fun sameSigner(apk: File): Boolean = runCatching {
        val pm = ctx.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        fun digests(pi: android.content.pm.PackageInfo?): Set<String> {
            val sigs = if (Build.VERSION.SDK_INT >= 28) pi?.signingInfo?.apkContentsSigners else pi?.signatures
            return sigs.orEmpty().map { MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString("") { b -> "%02x".format(b) } }.toSet()
        }
        val archive = pm.getPackageArchiveInfo(apk.absolutePath, flags)
        if (archive?.packageName != ctx.packageName) return false
        val mine = digests(pm.getPackageInfo(ctx.packageName, flags))
        mine.isNotEmpty() && mine == digests(archive)
    }.getOrDefault(false)

    companion object {
        private const val TAG = "CastBridgeUpdate"
        private const val ACTION = "castbridge.sender.UPDATE_RESULT"
        private const val CHANNEL = "updates"
        private const val NOTIF = 71
        const val EXTRA_INSTALL = "castbridge.install_update"
        /** The activity in front (to show Android's install confirmation from it). */
        @Volatile var foreground: WeakReference<Activity>? = null
    }
}
