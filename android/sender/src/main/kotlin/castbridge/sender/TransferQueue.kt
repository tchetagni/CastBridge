package castbridge.sender

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import castbridge.core.tv.QueueItem
import castbridge.core.tv.QueueStatus
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

/**
 * The files sent to the TV one after the other (copy or move, in the background, nothing played). The order and the bookkeeping live in
 * [TransferQueueModel] (core, tested); this object only starts the right service for the next file and watches it end.
 * [TransferQueueService] keeps the app alive and the read permission of the queued files while some wait.
 */
object TransferQueue {
    private val model = TransferQueueModel()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _items = MutableStateFlow<List<QueueItem>>(emptyList())
    val items: StateFlow<List<QueueItem>> = _items
    @Volatile private var pumping = false
    @Volatile private var cancelRunning = false

    private fun publish() { _items.value = model.items() }
    fun busy() = model.busy()
    fun waitingText() = model.waitingText()

    /** Adds a file; the runner starts at once when nothing else is being sent. Returns the position text for the user. */
    fun enqueue(ctx: Context, uri: Uri, name: String, size: Long, move: Boolean, autoPlay: Boolean = false, progressive: Boolean = false): String {
        val app = ctx.applicationContext
        model.enqueue(uri.toString(), name, size, move, autoPlay, progressive); publish()
        keepAlive(app, uri)
        pump(app)
        return model.waitingText() ?: "envoi en cours"
    }

    fun cancel(ctx: Context, id: Long) {
        if (model.cancel(id)) {                       // the running one: stop the transfer, the runner records it
            cancelRunning = true
            UploadService.cancel(ctx); BtUploadService.cancel(ctx)
        }
        publish()
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

    private fun pump(app: Context) {
        if (pumping) return
        pumping = true
        scope.launch {
            try {
                while (isActive) {
                    val item = model.next() ?: break
                    runOne(app, item)
                }
            } finally { pumping = false; publish() }
        }
    }

    private suspend fun runOne(app: Context, item: QueueItem) {
        model.start(item.id); publish(); cancelRunning = false
        val session = waitForTv()
        if (session == null) { model.finish(item.id, false, "TV non connectée : l'envoi n'a pas pu démarrer"); publish(); return }
        val uri = Uri.parse(item.uri)
        val base = session.base
        val viaBt = base == null
        // never copy the same file twice: same name, complete, same size already on the TV
        val there = base?.let { b -> withContext(Dispatchers.IO) { runCatching { castbridge.core.tv.TvDedupe.alreadyThere(castbridge.core.tv.TvInfo.parse(castbridge.core.tv.TvClient(b, session.credential).info()).file(item.name), item.size) }.getOrDefault(false) } } ?: false
        if (there) { model.finish(item.id, true, "Déjà sur la TV : non recopié"); publish(); return }
        val ok = runCatching {
            if (viaBt) BtUploadService.start(app, uri, item.name, session.tv.address, session.credential)
            else UploadService.start(app, uri, item.name, session.tv.mdns ?: session.tv.name, base!!.removePrefix("http://"), session.credential,
                progressive = item.progressive, autoPlay = item.autoPlay, move = item.move)
        }
        if (ok.isFailure) { model.finish(item.id, false, ok.exceptionOrNull()?.message); publish(); return }
        val outcome = watch(viaBt)
        when {
            cancelRunning -> model.finishCancelled(item.id)
            outcome == null -> {
                model.finish(item.id, true)
                // the name the TV holds (the assistant may have renamed it on the way), then « Titre / Saison » for a series
                val held = (UploadService.state.value as? UploadService.State.Done)?.job?.fileName ?: item.name
                val tvBase = session.base
                if (tvBase != null) runCatching { SeriesClassifying.afterSend(app, castbridge.core.tv.TvClient(tvBase, session.credential), held) }
            }
            else -> model.finish(item.id, false, outcome)
        }
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

    /** Null when the file arrived, else the reason. Waits for the service to start, then for it to end. */
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
            } else {
                when (val s = UploadService.state.value) {
                    is UploadService.State.Done -> return null
                    is UploadService.State.Failed -> return s.reason
                    is UploadService.State.Uploading, is UploadService.State.Waiting -> started = true
                    UploadService.State.Idle -> if (started) return if (cancelRunning) null else "Envoi interrompu"
                }
            }
            if (!started && ++waited > 40) return "L'envoi n'a pas démarré"
        }
    }
}
