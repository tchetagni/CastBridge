package castbridge.sender

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.os.PowerManager
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import castbridge.core.tv.RateMeter
import castbridge.core.tv.ResumableDownload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import kotlin.concurrent.thread

/**
 * TV -> phone download of a stored file (GET /stream/<name> with Range), resumable after any cut, as a foreground service.
 * Saved in Téléchargements/CastBridge (MediaStore, API 29+; the app's own Download folder before) or in a folder the user
 * picked (SAF). The unfinished item is remembered, so a new request for the same file continues where it stopped.
 */
class DownloadService : Service() {
    data class Job(val base: String, val pin: String?, val name: String, val size: Long, val tree: String?)
    data class Progress(val job: Job? = null, val state: ResumableDownload.State? = null, val speed: Long = 0, val average: Long = 0, val savedAs: String? = null)

    @Volatile private var cancelled = false
    private var worker: Thread? = null
    private var wake: PowerManager.WakeLock? = null
    private var wifi: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) { cancelled = true; return START_NOT_STICKY }
        if (worker?.isAlive == true) return START_NOT_STICKY
        val job = Job(intent?.getStringExtra("base") ?: return START_NOT_STICKY, intent.getStringExtra("pin"),
            intent.getStringExtra("name") ?: return START_NOT_STICKY, intent.getLongExtra("size", -1), intent.getStringExtra("tree"))
        try {
            val n = notification("Téléchargement de ${job.name}", 0)
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else startForeground(NOTIF, n)
        } catch (e: Exception) { _progress.value = Progress(job, ResumableDownload.State.Failed("Service refusé : ${e.message}")); stopSelf(); return START_NOT_STICKY }
        cancelled = false
        wake = runCatching { getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "castbridge:download").also { it.acquire(6 * 3600_000L) } }.getOrNull()
        wifi = runCatching {
            @Suppress("DEPRECATION")
            (applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "castbridge-download").also { it.acquire() }
        }.getOrNull()
        worker = thread(name = "download") { run(job) }
        return START_NOT_STICKY
    }

    private fun run(job: Job) {
        val sink = try { sinkFor(job) } catch (e: Exception) {
            finish(Progress(job, ResumableDownload.State.Failed("Impossible de créer le fichier : ${e.message}"))); return
        }
        val meter = RateMeter()
        val t0 = System.nanoTime(); val start = sink.length()
        var last = 0L; var prev = start
        val d = ResumableDownload(job.name, { job.base }, job.pin, { sink.length() }, { at -> sink.open(at) }, { cancelled })
        val res = d.run { s ->
            if (s is ResumableDownload.State.Downloading) { meter.add((s.got - prev).coerceAtLeast(0)); prev = s.got }
            val now = System.currentTimeMillis()
            if (now - last < 500 && s is ResumableDownload.State.Downloading) return@run
            last = now
            val dt = (System.nanoTime() - t0) / 1_000_000
            _progress.value = Progress(job, s, meter.bytesPerSec(), if (dt > 0) (prev - start) * 1000 / dt else 0, sink.label)
            val pct = when (s) { is ResumableDownload.State.Downloading -> if (s.total > 0) (s.got * 100 / s.total).toInt() else 0; else -> 0 }
            runCatching { getSystemService(NotificationManager::class.java).notify(NOTIF, notification("Téléchargement de ${job.name}", pct)) }
        }
        if (res is ResumableDownload.State.Done) runCatching { sink.finish() }.onFailure { Log.w(TAG, "finish: ${it.message}") }
        finish(Progress(job, res, 0, _progress.value.average, sink.label))
    }

    /** Where the file goes; [length] is what is already there (the resume point). */
    private interface Sink { val label: String; fun length(): Long; fun open(at: Long): OutputStream; fun finish() }

    private fun sinkFor(job: Job): Sink {
        val prefs = getSharedPreferences("downloads", MODE_PRIVATE)
        val key = "${job.name}|${job.size}|${job.tree ?: "dl"}"          // not the TV address: it may change between attempts
        job.tree?.let { tree -> return safSink(Uri.parse(tree), job.name, prefs, key) }
        if (Build.VERSION.SDK_INT >= 29) {
            val known = prefs.getString(key, null)?.let(Uri::parse)?.takeIf { u -> runCatching { contentResolver.openFileDescriptor(u, "r")?.use { true } }.getOrNull() == true }
            val uri = known ?: contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, job.name)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/CastBridge")
                put(MediaStore.MediaColumns.IS_PENDING, 1)           // hidden from other apps until complete
            })?.also { prefs.edit().putString(key, it.toString()).apply() } ?: throw IOException("MediaStore refused")
            return object : Sink {
                override val label = "Téléchargements/CastBridge/${job.name}"
                override fun length() = runCatching { contentResolver.openFileDescriptor(uri, "r")!!.use { it.statSize } }.getOrDefault(0)
                override fun open(at: Long): OutputStream = positioned(contentResolver.openFileDescriptor(uri, "rw") ?: throw IOException("cannot open"), at)
                override fun finish() {
                    contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                    prefs.edit().remove(key).apply()
                }
            }
        }
        // API 26-28: the app's own Download folder (no storage permission needed).
        val f = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "CastBridge/${job.name}").also { it.parentFile?.mkdirs() }
        return object : Sink {
            override val label = f.absolutePath
            override fun length() = if (f.exists()) f.length() else 0
            override fun open(at: Long): OutputStream = FileOutputStream(f, true).also { if (it.channel.size() != at) it.channel.truncate(at) }
            override fun finish() {}
        }
    }

    private fun safSink(tree: Uri, name: String, prefs: android.content.SharedPreferences, key: String): Sink {
        val known = prefs.getString(key, null)?.let(Uri::parse)?.takeIf { u -> runCatching { contentResolver.openFileDescriptor(u, "r")?.use { true } }.getOrNull() == true }
        val uri = known ?: run {
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            DocumentsContract.createDocument(contentResolver, parent, "application/octet-stream", name)
        }?.also { prefs.edit().putString(key, it.toString()).apply() } ?: throw IOException("the folder refused the file")
        return object : Sink {
            override val label = "dossier choisi/$name"
            override fun length() = runCatching { contentResolver.openFileDescriptor(uri, "r")!!.use { it.statSize } }.getOrDefault(0)
            override fun open(at: Long): OutputStream {
                // "rw" + position when the provider allows it, else append ("wa"): both continue at the saved size.
                val pfd = runCatching { contentResolver.openFileDescriptor(uri, "rw") }.getOrNull()
                return if (pfd != null) positioned(pfd, at) else contentResolver.openOutputStream(uri, "wa") ?: throw IOException("cannot open")
            }
            override fun finish() { prefs.edit().remove(key).apply() }
        }
    }

    private fun positioned(pfd: android.os.ParcelFileDescriptor, at: Long): OutputStream {
        val out = object : FileOutputStream(pfd.fileDescriptor) { override fun close() { try { super.close() } finally { pfd.close() } } }
        out.channel.position(at)
        return out
    }

    private fun notification(text: String, pct: Int): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Téléchargements depuis la TV", NotificationManager.IMPORTANCE_LOW))
        val cancel = PendingIntent.getService(this, 6, Intent(this, DownloadService::class.java).setAction(ACTION_CANCEL), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL).setContentTitle("CastBridge").setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download).setOngoing(true).setOnlyAlertOnce(true).setProgress(100, pct, false)
            .addAction(Notification.Action.Builder(null, "Annuler", cancel).build()).build()
    }

    private fun finish(p: Progress) { _progress.value = p; stopSelf() }

    override fun onDestroy() {
        cancelled = true
        runCatching { wake?.let { if (it.isHeld) it.release() } }
        runCatching { wifi?.let { if (it.isHeld) it.release() } }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "CastBridgeDownload"
        private const val CHANNEL = "download"
        private const val NOTIF = 6
        private const val ACTION_CANCEL = "castbridge.CANCEL_DOWNLOAD"
        private val _progress = MutableStateFlow(Progress())
        val progress: StateFlow<Progress> = _progress

        /** [tree] = folder chosen with the system picker (SAF), null = Téléchargements/CastBridge. */
        fun start(ctx: Context, base: String, pin: String?, name: String, size: Long, tree: String? = null) {
            _progress.value = Progress(Job(base, pin, name, size, tree))
            ctx.startForegroundService(Intent(ctx, DownloadService::class.java).putExtra("base", base).putExtra("pin", pin)
                .putExtra("name", name).putExtra("size", size).putExtra("tree", tree))
        }

        fun cancel(ctx: Context) { runCatching { ctx.startService(Intent(ctx, DownloadService::class.java).setAction(ACTION_CANCEL)) } }
    }
}
