package castbridge.receiver

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
import castbridge.core.tv.ApiReply
import castbridge.core.tv.ReceiverServer
import java.io.File
import java.security.MessageDigest

/**
 * Installs APKs the phone uploaded over Wi-Fi: updates of this app or any other application.
 *
 * What Android allows (and this class does not try to get around): "install unknown apps" must be allowed
 * for this app once in the TV settings, and the system asks for a confirmation on the TV screen (remote
 * control) for each app. Updates of an installed app must carry its signing key. For this app itself the
 * version may not go down. After installing an update of this very app Android stops it: reopen it from
 * the launcher. Several APK files of one app (split APKs) go into one install session.
 */
/** [dirs] = every folder an upload may land in (internal storage and the USB stick), looked up on each call. */
class UpdateInstaller(
    private val ctx: Context,
    private val dirs: () -> List<File>,
    /** Shows a system screen (install confirmation, settings) from the background as Android allows; null = shown, else why not. */
    private val launch: (Intent, String) -> String? = { i, _ -> runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.exceptionOrNull()?.message },
    private val notify: (String) -> Unit,
) {
    @Volatile private var status = "idle"
    @Volatile private var message = ""
    private val pending = java.util.concurrent.atomic.AtomicInteger(0)

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val st = i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
            val msg = i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
            when (st) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    set("confirm", "Validez l'installation à l'écran de la TV avec la télécommande")
                    @Suppress("DEPRECATION")
                    val confirm = i.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                    val why = confirm?.let { launch(it, "Installation d'une application : validez à l'écran") } ?: "pas d'écran de confirmation"
                    if (why != null) set("confirm", "Validez l'installation sur la TV : $why")
                }
                PackageInstaller.STATUS_SUCCESS ->
                    if (i.getBooleanExtra(EXTRA_AUTO, false)) set("done", "Mise à jour installée")
                    else if (pending.decrementAndGet() <= 0) set("done", "Installée") else set("installing", "Application installée, suivante…")
                else -> {
                    if (i.getBooleanExtra(EXTRA_AUTO, false)) {
                        set("failed", "Mise à jour non installée ($st) : $msg")
                        autoFailed?.invoke(statusCode(st)); autoFailed = null
                    } else { pending.decrementAndGet(); set("failed", "Échec de l'installation de ${i.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME) ?: "l'app"} ($st) : $msg") }
                }
            }
        }
    }

    init {
        val f = IntentFilter(ACTION)
        if (Build.VERSION.SDK_INT >= 33) ctx.registerReceiver(receiver, f, Context.RECEIVER_NOT_EXPORTED)
        else @Suppress("UnspecifiedRegisterReceiverFlag") ctx.registerReceiver(receiver, f)
        // Received APKs are single-use: drop old ones so they do not eat the TV's little storage.
        dirs().flatMap { it.listFiles().orEmpty().toList() }
            .filter { it.isFile && it.name.endsWith(".apk") && System.currentTimeMillis() - it.lastModified() > 3_600_000 }
            .forEach { it.delete() }
    }

    fun stop() { runCatching { ctx.unregisterReceiver(receiver) } }

    private fun set(st: String, msg: String) { status = st; message = msg; notify(msg) }

    @Suppress("DEPRECATION")
    private fun installedCode(): Long = ctx.packageManager.getPackageInfo(ctx.packageName, 0).let {
        if (Build.VERSION.SDK_INT >= 28) it.longVersionCode else it.versionCode.toLong()
    }

    private fun installedName(): String = ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName.orEmpty()

    fun canInstall() = Build.VERSION.SDK_INT < 26 || ctx.packageManager.canRequestPackageInstalls()

    fun infoJson(): String =
        """{"package":${ReceiverServer.q(ctx.packageName)},"versionCode":${installedCode()},"versionName":${ReceiverServer.q(installedName())},""" +
            """"canInstall":${canInstall()},"status":${ReceiverServer.q(status)},"message":${ReceiverServer.q(message)}}"""

    private fun err(code: Int, msg: String) = ApiReply(code, """{"error":${ReceiverServer.q(msg)}}""")

    class Apk(val file: File, val pkg: String, val label: String, val versionName: String, val code: Long, val info: android.content.pm.PackageInfo)

    @Suppress("DEPRECATION")
    private fun inspect(f: File): Apk? {
        val pm = ctx.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val info = pm.getPackageArchiveInfo(f.absolutePath, flags) ?: return null
        val label = runCatching {
            info.applicationInfo?.let { it.sourceDir = f.absolutePath; it.publicSourceDir = f.absolutePath; pm.getApplicationLabel(it).toString() }
        }.getOrNull() ?: info.packageName
        val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        return Apk(f, info.packageName, label, info.versionName.orEmpty(), code, info)
    }

    private fun find(name: String): File? = dirs().map { File(it, name) }.firstOrNull { it.isFile }

    private fun apkFiles() = dirs().flatMap { it.listFiles().orEmpty().toList() }.filter { it.isFile && it.name.endsWith(".apk", ignoreCase = true) }.sortedBy { it.name }

    /** Received APK files with what is inside them, so the phone can show "App X 1.2 (package)" before installing. */
    fun listJson(): String = "[" + apkFiles().joinToString(",") { f ->
        val a = inspect(f)
        """{"name":${ReceiverServer.q(f.name)},"size":${f.length()},"valid":${a != null},"package":${ReceiverServer.q(a?.pkg ?: "")},""" +
            """"label":${ReceiverServer.q(a?.label ?: "")},"versionName":${ReceiverServer.q(a?.versionName ?: "")},"versionCode":${a?.code ?: 0}}"""
    } + "]"

    /** Called on an HTTP thread once the PIN was checked. [names] are files already uploaded to the TV. */
    @Suppress("DEPRECATION")
    fun install(names: List<String>, force: Boolean): ApiReply {
        if (names.isEmpty()) return err(400, "aucun fichier")
        val files = names.map { n ->
            val safe = ReceiverServer.safeName(n)
            val f = safe?.let { find(it) }
            if (safe == null || !safe.endsWith(".apk", ignoreCase = true) || f == null || !f.isFile) return err(404, "APK introuvable : envoyez-le d'abord ($n)")
            f
        }
        val apks = files.map { inspect(it) ?: return err(400, "Fichier APK illisible ou incomplet : ${it.name}") }
        val groups = apks.groupBy { it.pkg }
        for ((pkg, list) in groups) if (pkg == ctx.packageName) {
            val a = list.first()
            val canForce = force && BuildConfig.DEBUG   // release : jamais de rétrogradation
            if (a.code < installedCode() && !canForce) return err(409, "Version plus ancienne : refusée (rétrogradation impossible sur une TV distribuée)")
            if (!sameSigner(a.info)) return err(400, "Signature différente : l'APK doit être signé avec la même clé que l'app installée")
        }
        if (!canInstall()) {
            val why = launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")), "Autorisez l'installation d'applications")
            set("permission", "Autorisez « Installer des apps inconnues » pour CastBridge TV dans les réglages de la TV, puis réessayez" + (why?.let { " ($it)" } ?: ""))
            return ApiReply(403, """{"error":${ReceiverServer.q(message)},"needsPermission":true}""")
        }
        return try {
            pending.set(groups.size)
            val inst = ctx.packageManager.packageInstaller
            for ((pkg, list) in groups) {
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                params.setAppPackageName(pkg)
                // Silent only if the system allows it for this installer; otherwise the TV shows its usual confirmation.
                if (Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                val id = inst.createSession(params)
                inst.openSession(id).use { s ->
                    list.forEachIndexed { i, a ->
                        a.file.inputStream().use { inp ->
                            s.openWrite(if (i == 0) "base.apk" else "split$i.apk", 0, a.file.length()).use { o -> inp.copyTo(o, 64 * 1024); s.fsync(o) }
                        }
                    }
                    val pi = PendingIntent.getBroadcast(ctx, id, Intent(ACTION).setPackage(ctx.packageName),
                        PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0))
                    set("installing", "Installation de ${list.first().label}…")
                    s.commit(pi.intentSender)
                }
            }
            ApiReply(202, """{"started":${groups.size},"apps":[${groups.values.joinToString(",") { l ->
                val a = l.first()
                """{"package":${ReceiverServer.q(a.pkg)},"label":${ReceiverServer.q(a.label)},"versionName":${ReceiverServer.q(a.versionName)}}"""
            }}]}""")
        } catch (e: Exception) {
            Log.w(TAG, "install", e)
            set("failed", "Installation impossible : ${e.message}")
            err(500, message)
        }
    }

    @Volatile private var autoFailed: ((String) -> Unit)? = null

    /**
     * Automatic update from the CastBridge server (TvConnect): [apk] was already checked against the signed manifest
     * (SHA-256, size). Installs it silently when Android allows it for this installer (Android 12+, app installed by
     * itself), else the TV shows its usual confirmation (on screen, or a notification when the app is in the background).
     * Returns false if nothing was started (then [failed] was called); after a start, [failed] is called on refusal.
     * Success = Android replaces the app and restarts it (MY_PACKAGE_REPLACED → BootReceiver → TvService).
     */
    @Suppress("DEPRECATION")
    fun installVerified(apk: File, versionCode: Int, versionName: String, failed: (String) -> Unit): Boolean {
        val a = inspect(apk) ?: run { failed("invalid"); return false }
        if (a.pkg != ctx.packageName) { failed("package"); return false }
        if (a.code < installedCode() || a.code != versionCode.toLong()) { failed("version"); return false }
        // Android refuses an update signed with another key: say it clearly (debug key of the build machine, see docs/API-SERVER.md)
        if (!sameSigner(a.info)) { set("failed", "Mise à jour ignorée : signée avec une autre clé que l'app installée"); failed("signature"); return false }
        if (!canInstall()) {
            val why = launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")), "Autorisez l'installation des mises à jour")
            set("permission", "Mise à jour $versionName prête : autorisez « Installer des apps inconnues » pour CastBridge TV" + (why?.let { " ($it)" } ?: ""))
            failed("permission")
            return false
        }
        return try {
            val inst = ctx.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            params.setAppPackageName(ctx.packageName)
            if (Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            params.setSize(apk.length())
            val id = inst.createSession(params)
            inst.openSession(id).use { s ->
                apk.inputStream().use { inp -> s.openWrite("base.apk", 0, apk.length()).use { o -> inp.copyTo(o, 64 * 1024); s.fsync(o) } }
                autoFailed = failed
                val pi = PendingIntent.getBroadcast(ctx, id, Intent(ACTION).setPackage(ctx.packageName).putExtra(EXTRA_AUTO, true),
                    PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0))
                set("installing", "Installation de la mise à jour $versionName…")
                s.commit(pi.intentSender)
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "auto install", e)
            set("failed", "Installation de la mise à jour impossible : ${e.message}")
            failed("session")
            false
        }
    }

    private fun statusCode(st: Int) = when (st) {
        PackageInstaller.STATUS_FAILURE_ABORTED -> "aborted"
        PackageInstaller.STATUS_FAILURE_BLOCKED -> "blocked"
        PackageInstaller.STATUS_FAILURE_CONFLICT -> "conflict"
        PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "incompatible"
        PackageInstaller.STATUS_FAILURE_INVALID -> "invalid"
        PackageInstaller.STATUS_FAILURE_STORAGE -> "storage"
        else -> "status_$st"
    }

    @Suppress("DEPRECATION")
    private fun sameSigner(archive: android.content.pm.PackageInfo): Boolean {
        fun digests(pi: android.content.pm.PackageInfo): Set<String> {
            val sigs = if (Build.VERSION.SDK_INT >= 28) pi.signingInfo?.apkContentsSigners else pi.signatures
            return sigs.orEmpty().map { MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString("") { b -> "%02x".format(b) } }.toSet()
        }
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val mine = digests(ctx.packageManager.getPackageInfo(ctx.packageName, flags))
        val theirs = digests(archive)
        return mine.isNotEmpty() && mine == theirs
    }

    private companion object {
        const val TAG = "CastBridgeUpdate"
        const val ACTION = "castbridge.receiver.UPDATE_RESULT"
        const val EXTRA_AUTO = "castbridge.auto"
    }
}
