package castbridge.sender

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import castbridge.core.lots.FileQueueStore
import castbridge.core.tv.QueueItem
import castbridge.core.tv.QueueStatus
import castbridge.core.tv.QueueTexts
import castbridge.core.tv.ResumableUpload
import castbridge.core.tv.TransferQueueModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * The files sent to the TV one after the other (copy, move, or « Copier sur la TV et lire »). The order and the bookkeeping live in
 * [TransferQueueModel] (core, tested: [castbridge.core.tv.QueueRules]); this object only starts the right service for the next file and watches it end.
 * [TransferQueueService] keeps the app alive and the read permission of the queued files while some wait.
 *
 * R-09: every path that sends a file to the TV goes through here (trusted link, code path, « Copier et lire », « Échange de fichiers »), so a second file
 * is queued instead of being dropped; the queue is saved on every change and read back when the app (or the service) starts again; credentials are
 * kept in memory only and looked up again after a restart.
 */
object TransferQueue {
    @Volatile private var model = TransferQueueModel()
    @Volatile private var loaded = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _items = MutableStateFlow<List<QueueItem>>(emptyList())
    val items: StateFlow<List<QueueItem>> = _items
    private val _note = MutableStateFlow<String?>(null)
    /** Why the queue is not moving right now (another upload, Android refusing a background start), in French; null = it moves. */
    val note: StateFlow<String?> = _note
    @Volatile private var pumping = false
    @Volatile private var cancelRunning = false
    /** The item whose upload this queue launched last: the upload states belong to it until the next launch. */
    @Volatile private var launchedId = -1L
    /** Set when Android refused to start the upload in the background: the queue waits for the app to come back to the front ([resume]). */
    @Volatile private var paused = false
    /** Credentials of the code-path items of this process (never written to disk; looked up again after a restart). */
    private val credentials = ConcurrentHashMap<Long, String>()

    private fun publish() { _items.value = model.items() }
    fun busy() = model.busy()
    fun waitingText() = model.waitingText()
    fun item(id: Long): QueueItem? = model.item(id)
    fun position(id: Long): Int = model.position(id)
    fun runningName(): String? = model.running()?.name
    /** True while the upload service states describe this item's upload. */
    fun owns(id: Long) = launchedId == id

    /** Reads the saved queue once per process (files whose read permission was lost fail with their cause; a running one waits again). */
    @Synchronized private fun ensure(ctx: Context) {
        if (loaded) return
        val app = ctx.applicationContext
        model = TransferQueueModel(store = FileQueueStore(File(app.filesDir, "transfer-queue.json")))
        loaded = true
        for (q in model.items()) if (q.status == QueueStatus.WAITING && !readable(app, Uri.parse(q.uri))) model.lost(q.id)
        publish()
    }

    private fun readable(app: Context, uri: Uri): Boolean =
        runCatching { app.contentResolver.openFileDescriptor(uri, "r")!!.use { true } }.getOrDefault(false)

    /**
     * Called when CastBridge comes to the front (and by [TransferQueueService] when Android restarts it): reads the saved queue back and starts
     * the files still waiting. Never starts a foreground service from the background by itself.
     */
    fun resume(ctx: Context) {
        val app = ctx.applicationContext
        runCatching { ensure(app) }
        paused = false
        if (model.busy() && !pumping) { model.items().firstOrNull { it.status == QueueStatus.WAITING }?.let { keepAlive(app, Uri.parse(it.uri)) }; pump(app) }
    }

    /** What was queued, its place and the sentence for the user (« Ajouté à la file : n° 2 … » or « envoi en cours »). */
    data class Ticket(val id: Long, val position: Int, val text: String) { val queued get() = position > 1 }

    /**
     * Adds a file; the runner starts at once when nothing else is being sent. [tvName] null = the trusted link; else the TV of that name
     * (code path or cast target) with [credential] (kept in memory only) and its manual [host] when known.
     */
    fun add(ctx: Context, uri: Uri, name: String, size: Long, move: Boolean, autoPlay: Boolean = false, progressive: Boolean = false,
            playOnTv: Boolean = false, ordered: Boolean = false, tvName: String? = null, credential: String? = null, host: String? = null,
            target: String? = null): Ticket {
        val app = ctx.applicationContext
        ensure(app)
        val it = model.enqueue(uri.toString(), name, size, move, autoPlay, progressive, playOnTv, ordered, tvName, host, target)
        credential?.takeIf { c -> c.isNotEmpty() }?.let { c -> credentials[it.id] = c }
        publish()
        keepAlive(app, uri)
        pump(app)
        return Ticket(it.id, model.position(it.id), model.admitted(it.id))
    }

    fun cancel(ctx: Context, id: Long) {
        if (model.cancel(id)) {                       // the running one: stop the transfer, the runner records it
            cancelRunning = true
            // only the upload this queue launched (never another screen's upload the file is still waiting for)
            if (launchedId == id) { UploadService.cancel(ctx); BtUploadService.cancel(ctx) }
        }
        publish()
    }

    /** « Réessayer » a failed file: it waits again at the end of the queue (its cause is cleared, the runner restarts if idle). */
    fun retry(ctx: Context, id: Long) {
        val app = ctx.applicationContext
        if (model.retry(id)) { publish(); model.item(id)?.let { keepAlive(app, Uri.parse(it.uri)) }; paused = false; pump(app) }
    }

    fun cancelWaiting() { model.cancelWaiting(); publish() }
    fun clearFinished() { model.clearFinished(); publish() }

    /** Holds the read permission of the queued files and keeps the process alive while the queue is not empty. */
    private fun keepAlive(app: Context, uri: Uri) {
        runCatching {
            val i = Intent(app, TransferQueueService::class.java).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            i.clipData = ClipData.newRawUri("", uri)
            app.startForegroundService(i)
        }
    }

    @Synchronized private fun claimPump(): Boolean { if (pumping) return false; pumping = true; return true }

    private fun pump(app: Context) {
        if (!claimPump()) return
        scope.launch {
            try {
                while (isActive && !paused) {
                    val item = model.next() ?: break
                    runOne(app, item)
                }
            } finally { pumping = false; publish() }
            // a file added (or retried) between the last next() and the end of this runner is not left waiting
            if (!paused && model.next() != null) pump(app)
        }
    }

    private fun isBackgroundRefusal(e: Throwable?) = e != null &&
        (e.javaClass.simpleName == "ForegroundServiceStartNotAllowedException" || e.message.orEmpty().startsWith(UploadService.REFUSED))

    /** Never two uploads at once: another screen's upload (or the previous file's service still closing) finishes first. False = cancelled meanwhile. */
    private suspend fun waitForFreeTv(item: QueueItem): Boolean {
        while (UploadService.active() || BtUploadService.active()) {
            _note.value = QueueTexts.TV_BUSY
            if (cancelRunning) { _note.value = null; model.finishCancelled(item.id); publish(); return false }
            delay(1000)
        }
        _note.value = null
        return true
    }

    private suspend fun runOne(app: Context, item: QueueItem) {
        model.start(item.id); publish(); cancelRunning = false
        if (!waitForFreeTv(item)) return
        val uri = Uri.parse(item.uri)
        if (!readable(app, uri)) { model.lost(item.id); publish(); return }
        val launch: () -> Unit
        var viaBt = false
        var dedupe: (suspend () -> Boolean)? = null
        var afterBase: String? = null; var afterCred: String? = null
        if (item.tvName == null) {
            // the trusted link: its session, waiting up to a minute for it to be (re)established
            val session = waitForTv()
            if (session == null) { model.finish(item.id, false, QueueTexts.NO_TV); publish(); return }
            val base = session.base
            viaBt = base == null
            afterBase = base; afterCred = session.credential
            // never copy the same file twice: same name, complete, same size already on the TV
            if (base != null) dedupe = { withContext(Dispatchers.IO) { runCatching { castbridge.core.tv.TvDedupe.alreadyThere(castbridge.core.tv.TvInfo.parse(castbridge.core.tv.TvClient(base, session.credential).info()).file(item.name), item.size) }.getOrDefault(false) } }
            launch = {
                if (viaBt) BtUploadService.start(app, uri, item.name, session.tv.address, session.credential)
                else UploadService.start(app, uri, item.name, session.tv.mdns ?: session.tv.name, base!!.removePrefix("http://"), session.credential,
                    progressive = item.progressive, autoPlay = item.autoPlay, move = item.move, ordered = item.ordered, target = item.target)
            }
        } else {
            // the TV of that name (code path, cast target): found by discovery (or its manual address) inside the upload service
            val cred = credentials[item.id] ?: PinStore(app).get(item.tvName).takeIf { it.isNotEmpty() }
            if (cred == null) { model.finish(item.id, false, QueueTexts.NO_CREDENTIAL); publish(); return }
            launch = {
                UploadService.start(app, uri, item.name, item.tvName!!, item.host, cred, progressive = item.progressive, autoPlay = item.autoPlay,
                    target = item.target, move = item.move, ordered = item.ordered)
            }
        }
        if (dedupe?.invoke() == true) { model.finish(item.id, true); _note.value = null; publish(); return }
        while (true) {
            val e = runCatching { launch() }.exceptionOrNull() ?: break
            when {
                e is UploadService.Busy -> if (!waitForFreeTv(item)) return       // another screen started an upload meanwhile: it goes first, then this one
                isBackgroundRefusal(e) -> { requeuePaused(item); return }
                else -> { model.finish(item.id, false, e.message ?: "L'envoi n'a pas pu démarrer"); publish(); return }
            }
        }
        launchedId = item.id
        val outcome = watch(viaBt)
        when {
            cancelRunning -> model.finishCancelled(item.id)
            outcome == null -> {
                model.finish(item.id, true)
                credentials.remove(item.id)
                // the name the TV holds (the assistant may have renamed it on the way), then « Titre / Saison » for a series
                val held = (UploadService.state.value as? UploadService.State.Done)?.job?.fileName ?: item.name
                val tvBase = afterBase
                if (tvBase != null) runCatching { SeriesClassifying.afterSend(app, castbridge.core.tv.TvClient(tvBase, afterCred), held) }
            }
            isBackgroundRefusal(Exception(outcome)) -> { requeuePaused(item); return }
            else -> model.finish(item.id, false, outcome)
        }
        publish()
    }

    /** Android refused the upload service in the background: the file waits, the queue resumes when CastBridge is opened (said in French). */
    private fun requeuePaused(item: QueueItem) {
        model.release(item.id)
        paused = true
        _note.value = "En pause : Android bloque les envois en arrière-plan. Ouvrez CastBridge pour continuer la file."
        publish()
    }

    /** The TV link, waiting up to a minute for it to be (re)established. */
    private suspend fun waitForTv(): castbridge.core.trust.LinkSession? {
        repeat(120) {
            (TvLinkManager.state.value as? LinkUi.Connected)?.session?.let { return it }
            delay(500)
        }
        return null
    }

    /** Null when the file arrived, else the reason. Waits for the service to start (90 s at most), then for it to end. */
    private suspend fun watch(viaBt: Boolean): String? {
        var started = false; var waited = 0
        while (true) {
            delay(500)
            if (viaBt) {
                when (val s = BtUploadService.state.value) {
                    is ResumableUpload.State.Done -> return null
                    is ResumableUpload.State.Failed -> return s.reason
                    is ResumableUpload.State.Uploading, is ResumableUpload.State.Waiting -> started = true
                    null -> if (started) return if (cancelRunning) null else "Envoi interrompu"
                }
                if (!started && BtUploadService.active()) started = true
            } else {
                when (val s = UploadService.state.value) {
                    is UploadService.State.Done -> return null
                    is UploadService.State.Failed -> return s.reason
                    is UploadService.State.Uploading, is UploadService.State.Waiting -> started = true
                    UploadService.State.Idle -> if (started && !UploadService.active()) return if (cancelRunning) null else "Envoi interrompu"
                }
                if (!started && UploadService.active()) started = true
            }
            if (!started && ++waited > 180) return "L'envoi n'a pas démarré (aucune réponse du service d'envoi en 90 s)"
        }
    }
}
