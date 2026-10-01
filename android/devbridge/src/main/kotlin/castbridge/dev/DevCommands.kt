package castbridge.dev

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import java.io.File
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * `ssh -p 2223 tv@<TV> cbdev <commande>`:
 *   status                     Android, ABI, droits, applications CastBridge installées (version, installateur)
 *   install <apk>              installe/met à jour (chemin absolu, ou nom d'un fichier de files/inbox)
 *   uninstall <paquet>         désinstalle
 *   start <paquet>             lance l'application
 * Android demande une confirmation à l'écran de la TV pour certaines opérations (désinstallation toujours ; installation si l'app est nouvelle ou si Android < 12) :
 * la commande l'annonce et attend (3 minutes).
 */
object DevCommands {
    private const val ACTION = "castbridge.dev.RESULT"

    fun run(ctx: Context, line: String, out: OutputStream): Int {
        fun say(s: String) { out.write((s + "\n").toByteArray()); out.flush() }
        val a = line.trim().split(Regex("\\s+")).drop(1)
        return when (a.firstOrNull()) {
            "status", null -> { status(ctx, ::say); 0 }
            "install" -> a.getOrNull(1)?.let { install(ctx, it, ::say) } ?: run { say("usage : cbdev install <apk>"); 2 }
            "uninstall" -> a.getOrNull(1)?.let { uninstall(ctx, it, ::say) } ?: run { say("usage : cbdev uninstall <paquet>"); 2 }
            "start" -> a.getOrNull(1)?.let { launch(ctx, it, ::say) } ?: run { say("usage : cbdev start <paquet>"); 2 }
            else -> { say("commandes : status | install <apk> | uninstall <paquet> | start <paquet>"); 2 }
        }
    }

    private fun status(ctx: Context, say: (String) -> Unit) {
        say("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ABI ${Build.SUPPORTED_ABIS.joinToString(",")}")
        say("installation d'apps inconnues autorisée pour CastBridge Dev : ${if (ctx.packageManager.canRequestPackageInstalls()) "oui" else "NON (ouvrir CastBridge Dev sur la TV)"}")
        say("dossier de dépôt : ${File(ctx.filesDir, "inbox").absolutePath}")
        @Suppress("DEPRECATION")
        ctx.packageManager.getInstalledPackages(0).filter { it.packageName.startsWith("castbridge.") }.sortedBy { it.packageName }.forEach { p ->
            val inst = runCatching { if (Build.VERSION.SDK_INT >= 30) ctx.packageManager.getInstallSourceInfo(p.packageName).installingPackageName else ctx.packageManager.getInstallerPackageName(p.packageName) }.getOrNull()
            say("  ${p.packageName} ${p.versionName} (code ${if (Build.VERSION.SDK_INT >= 28) p.longVersionCode else 0}) installé par ${inst ?: "?"}")
        }
    }

    private fun launch(ctx: Context, pkg: String, say: (String) -> Unit): Int {
        val i = ctx.packageManager.getLeanbackLaunchIntentForPackage(pkg) ?: ctx.packageManager.getLaunchIntentForPackage(pkg)
        if (i == null) { say("$pkg : introuvable ou sans écran de lancement"); return 1 }
        ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); say("lancé : $pkg"); return 0
    }

    private fun install(ctx: Context, arg: String, say: (String) -> Unit): Int {
        val f = if (arg.startsWith("/")) File(arg) else File(File(ctx.filesDir, "inbox"), arg)
        if (!f.isFile) { say("fichier introuvable : ${f.path}"); return 1 }
        if (!ctx.packageManager.canRequestPackageInstalls()) {
            runCatching { ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            say("Autorisez « Installer des apps inconnues » pour CastBridge Dev sur la TV (la page vient de s'ouvrir), puis relancez."); return 3
        }
        val pi = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        if (Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        val id = pi.createSession(params)
        pi.openSession(id).use { s ->
            f.inputStream().use { i -> s.openWrite("app.apk", 0, f.length()).use { o -> i.copyTo(o); s.fsync(o) } }
            return await(ctx, say) { sender -> s.commit(sender) }
        }
    }

    private fun uninstall(ctx: Context, pkg: String, say: (String) -> Unit): Int =
        await(ctx, say) { sender -> ctx.packageManager.packageInstaller.uninstall(pkg, sender) }

    private fun await(ctx: Context, say: (String) -> Unit, begin: (android.content.IntentSender) -> Unit): Int {
        val done = CountDownLatch(1); var code = 1
        val action = "$ACTION.${System.nanoTime()}"
        val rx = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                val st = i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
                val msg = i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
                when (st) {
                    PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                        @Suppress("DEPRECATION") val confirm = i.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                        say("Confirmation demandée à l'écran de la TV : acceptez avec la télécommande (3 minutes).")
                        if (!Settings.canDrawOverlays(c)) say("(astuce : autorisez « Afficher par-dessus les autres apps » pour CastBridge Dev, sinon la confirmation peut ne pas apparaître)")
                        runCatching { c.startActivity(confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.onFailure { say("Impossible d'afficher la confirmation : ${it.message}") }
                    }
                    PackageInstaller.STATUS_SUCCESS -> { say("OK"); code = 0; done.countDown() }
                    else -> { say("ÉCHEC ($st) $msg"); code = 1; done.countDown() }
                }
            }
        }
        // the system answers on this thread, never the main one: writing to the SSH channel from the main thread is forbidden
        val ht = android.os.HandlerThread("cbdev-rx").also { it.start() }; val handler = android.os.Handler(ht.looper)
        if (Build.VERSION.SDK_INT >= 33) ctx.registerReceiver(rx, IntentFilter(action), null, handler, Context.RECEIVER_NOT_EXPORTED) else ctx.registerReceiver(rx, IntentFilter(action), null, handler)
        try {
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            begin(PendingIntent.getBroadcast(ctx, 0, Intent(action).setPackage(ctx.packageName), flags).intentSender)
            if (!done.await(180, TimeUnit.SECONDS)) { say("Délai dépassé (pas de confirmation sur la TV ?)"); return 4 }
            return code
        } finally { runCatching { ctx.unregisterReceiver(rx) }; ht.quitSafely() }
    }
}
