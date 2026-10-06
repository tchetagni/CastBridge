package castbridge.sender

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import castbridge.core.lots.FileQueueStore
import castbridge.core.trust.CopyStep
import castbridge.core.tv.ContentHash
import castbridge.core.tv.DedupDecision
import castbridge.core.tv.DedupTexts
import castbridge.core.tv.QueueCancel
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
    /** The item whose upload this queue launched last: the upload states belong to it until the next launch. */
    @Volatile private var launchedId = -1L
    /** The attempt ([QueueItem.attempts]) launched: a retried file waiting its turn is another generation ([QueueCancel.isLaunched]). */
    @Volatile private var launchedAttempt = -1
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
    /** The queue waits for the app to come back (Android refused a background start, or its time budget is used up). */
    fun paused() = paused

    /**
     * Reads the saved queue once per process (a running file waits again). Only one small file is read here; whether each queued file is still
     * readable is checked by the runner, off the main thread, just before it is sent (a lost one fails with its cause).
     */
    @Synchronized private fun ensure(ctx: Context) {
        if (loaded) return
        val app = ctx.applicationContext
        // a store that cannot be opened never breaks the queue: it goes on in memory (review, minor 8)
        runCatching { model = TransferQueueModel(store = FileQueueStore(File(app.filesDir, "transfer-queue.json"))) }
            .onFailure { android.util.Log.w("TransferQueue", "file d'attente non relue", it) }
        loaded = true
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
        paused = false
        scope.launch(Dispatchers.IO) {                      // disk read off the main thread (audit, minor 7)
            runCatching { ensure(app) }
            if (model.busy() && !pumping) {
                _note.value = null
                withContext(Dispatchers.Main) { model.items().firstOrNull { it.status == QueueStatus.WAITING }?.let { keepAlive(app, Uri.parse(it.uri)) } }
                pump(app)
            }
        }
    }

    /** What was queued, its place and the sentence for the user (« Ajouté à la file : n° 2 … » or « envoi en cours »). */
    data class Ticket(val id: Long, val position: Int, val text: String) { val queued get() = position > 1 }

    /**
     * Adds a file; the runner starts at once when nothing else is being sent. [tvName] null = the trusted link; else the TV of that name
     * (code path or cast target) with [credential] (kept in memory only) and its manual [host] when known.
     */
    fun add(ctx: Context, uri: Uri, name: String, size: Long, move: Boolean, autoPlay: Boolean = false, progressive: Boolean = false,
            playOnTv: Boolean = false, ordered: Boolean = false, tvName: String? = null, credential: String? = null, host: String? = null,
            target: String? = null, force: Boolean = false): Ticket {
        val app = ctx.applicationContext
        ensure(app)
        // the trusted link: remember WHICH TV (its address, never a credential), so that a file is never sent to another TV after a reconnection
        val linkTv = if (tvName == null) (TvLinkManager.state.value as? LinkUi.Connected)?.session?.tv?.address else null
        // throws QueueRefused (French reason) when the same file is already queued for this TV with the other action
        val it = model.enqueue(uri.toString(), name, size, move, autoPlay, progressive, playOnTv, ordered, tvName, host, target, linkTv, force)
        credential?.takeIf { c -> c.isNotEmpty() }?.let { c -> credentials[it.id] = c }
        if (paused) { paused = false; _note.value = null }           // added from the app in front: the paused queue may go on (audit, minor 9)
        publish()
        keepAlive(app, uri)
        pump(app)
        return Ticket(it.id, model.position(it.id), model.admitted(it.id))
    }

    /** « Annuler » ([QueueCancel]): a waiting file leaves; a running one not launched yet is marked (the runner never launches it); a launched one stops. */
    fun cancel(ctx: Context, id: Long) {
        val it = model.item(id) ?: return
        val before = it.status
        val launched = QueueCancel.isLaunched(launchedId, launchedAttempt, it)
        model.cancel(id)
        // only the upload this queue launched for THIS file (never another screen's upload the file is still waiting for)
        if (QueueCancel.onCancel(before, launched) == QueueCancel.Action.STOP_UPLOAD) { UploadService.cancel(ctx); BtUploadService.cancel(ctx) }
        publish()
    }

    /** « Réessayer » a failed file: it waits again at the end of the queue (its cause is cleared, the runner restarts if idle). */
    fun retry(ctx: Context, id: Long) {
        val app = ctx.applicationContext
        if (model.retry(id)) { publish(); model.item(id)?.let { keepAlive(app, Uri.parse(it.uri)) }; paused = false; pump(app) }
    }

    /**
     * « Copier quand même » (R-12), offered once for a file that was not copied because the TV holds the same content: the same file again, forced
     * (no content check), as a NEW item of the queue; the line of the old one goes. A MOVE stays a move (its original goes only after the usual verified copy).
     */
    fun copyAnyway(ctx: Context, id: Long): Ticket? {
        val it = model.item(id) ?: return null
        model.dropNote(id)
        val t = runCatching {
            add(ctx, Uri.parse(it.uri), it.name, it.size, it.move, it.autoPlay, it.progressive, playOnTv = false, ordered = it.ordered, tvName = it.tvName,
                credential = credentials[id], host = it.host, target = it.target, force = true)
        }.getOrNull()
        publish()
        return t
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
            if (model.cancelAsked(item.id)) { _note.value = null; model.finishCancelled(item.id); publish(); return false }
            delay(1000)
        }
        _note.value = null
        return true
    }

    /** One file; the record of what was launched is cleared before and after, so « Annuler » never targets another screen's upload. */
    private suspend fun runOne(app: Context, item: QueueItem) {
        launchedId = -1; launchedAttempt = -1
        try { runOneInner(app, item) }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Throwable) {
            // never a file left RUNNING for ever (nothing else would start behind it, without a word): it fails with its cause
            android.util.Log.e("TransferQueue", "l'envoi a planté", e)
            model.abandonRunning(CopyReport.failed(app, item, lastStep, lastPercent.takeIf { it > 0 }, e = e)); publish()
        }
        finally { launchedId = -1; launchedAttempt = -1 }
    }

    private suspend fun runOneInner(app: Context, item: QueueItem) {
        if (!model.start(item.id)) { publish(); return }            // cancelled between next() and here: never started
        publish()
        if (!waitForFreeTv(item)) return
        val uri = Uri.parse(item.uri)
        if (!readable(app, uri)) { model.finish(item.id, false, CopyReport.failed(app, item, CopyStep.START, null, reason = QueueTexts.SOURCE_LOST)); publish(); return }
        val launch: () -> Unit
        var viaBt = false
        var viaWd = false; var wdSince = 0L
        // the name sent to the TV: the file's own, or a unique one when the TV holds another content under that very name (R-12, second audit)
        var sendName = item.name
        var dedupe: (suspend () -> Boolean)? = null
        var afterBase: String? = null; var afterCred: String? = null
        // R-12: where to ask « is this content already on the TV? » (Wi-Fi only; a code-path TV found by discovery only is not asked: the copy goes)
        var dedupBase: String? = null; var dedupCred: String? = null
        if (item.tvName == null) {
            // the trusted link: its session, waiting up to a minute for it to be (re)established
            CopyReport.step(app, CopyStep.SEARCH, "recherche de la TV de confiance")
            val (session, refused) = waitForTv()
            if (session == null) {
                // the TV said no (code 8 « je ne vous reconnais plus »…): fail at once, with its cause and the PIN to type, never a silent minute
                val why = refused?.second ?: CopyReport.failed(app, item, CopyStep.SEARCH, null, reason = QueueTexts.NO_TV)
                model.finish(item.id, false, why); publish()
                if (refused != null) RefusalNotice.show(app, item, refused.first)
                return
            }
            if (item.linkTv != null && session.tv.address != item.linkTv) { model.finish(item.id, false, QueueTexts.OTHER_TV); publish(); return }
            // « Seul le Bluetooth » (R-14): no common network that answers ⇒ the phone and the TV set up Wi-Fi Direct by themselves (core BulkRoute decides,
            // AutoWifiDirect joins); the same upload then runs over it, ordered path and « Copier et lire » included. Never instead of a working LAN.
            val wdBase = if (!castbridge.core.link.BulkRoute.lanRoute(session.route))
                runCatching { AutoWifiDirect.bulkBase(app, session, item.size) }.onFailure { android.util.Log.w("TransferQueue", "Wi-Fi Direct", it) }.getOrNull() else null
            if (wdBase != null) { viaWd = true; wdSince = AutoWifiDirect.clock() }     // the same monotonic clock as lostSince
            val base = wdBase ?: session.base
            viaBt = base == null
            afterBase = base; afterCred = session.credential
            dedupBase = base; dedupCred = session.credential
            // never copy the same file twice: same name, complete, same size already on the TV
            if (base != null) dedupe = { withContext(Dispatchers.IO) { runCatching { castbridge.core.tv.TvDedupe.alreadyThere(castbridge.core.tv.TvInfo.parse(castbridge.core.tv.TvClient(base, session.credential).info()).file(item.name), item.size) }.getOrDefault(false) } }
            launch = {
                // the queue already chose the route (BulkRoute): the Bluetooth service never sets up Wi-Fi Direct on its own (no system dialog)
                if (viaBt) BtUploadService.start(app, uri, sendName, session.tv.address, session.credential, allowWifiDirect = false)
                else UploadService.start(app, uri, sendName, session.tv.mdns ?: session.tv.name, base!!.removePrefix("http://"), session.credential,
                    progressive = item.progressive, autoPlay = item.autoPlay, move = item.move, ordered = item.ordered, target = item.target)
            }
        } else {
            // the TV of that name (code path, cast target): found by discovery (or its manual address) inside the upload service
            val cred = credentials[item.id] ?: PinStore(app).get(item.tvName).takeIf { it.isNotEmpty() }
            if (cred == null) { model.finish(item.id, false, CopyReport.failed(app, item, CopyStep.CONNECT, null, reason = QueueTexts.NO_CREDENTIAL, text = "Copie impossible : le code PIN de la TV n'est pas gardé sur ce téléphone. Touchez pour saisir le code PIN")); publish(); return }
            dedupCred = cred
            dedupBase = item.host?.let { h -> if (':' in h) "http://$h" else "http://$h:8765" }
            launch = {
                UploadService.start(app, uri, sendName, item.tvName!!, item.host, cred, progressive = item.progressive, autoPlay = item.autoPlay,
                    target = item.target, move = item.move, ordered = item.ordered)
            }
        }
        // same name, complete, same size already on the TV: no longer « already there » by itself (its CONTENT may differ, R-12 second audit)
        val sameName = dedupe?.invoke() == true
        // R-12: the same CONTENT already on the TV (any name, any folder), or already sent by this queue, is not copied again
        val base0 = dedupBase
        if (!item.force && !viaBt && base0 != null) {
            val d = runCatching { contentDedupe(app, item, base0, dedupCred, sameName) }.getOrElse { DedupRun(if (sameName) Dedup.SAME_NAME_UNKNOWN else Dedup.COPY) }
            _note.value = null
            when (d.kind) {
                Dedup.SKIPPED -> { credentials.remove(item.id); CopyReport.succeeded(app, item, QueueTexts.ALREADY_THERE); publish(); return }
                Dedup.CANCELLED -> { model.finishCancelled(item.id); publish(); return }
                // same name and size, content not verifiable: nothing sent; a MOVE keeps the original (the TV's « done » would prove nothing)
                Dedup.SAME_NAME_UNKNOWN -> {
                    if (item.move) model.finishSkipped(item.id, item.name, castbridge.core.tv.MoveProof.SAME_NAME_UNVERIFIED_TEXT) else model.finish(item.id, true)
                    publish(); return
                }
                Dedup.COPY -> d.sendAs?.let { sendName = it }
            }
        } else if (sameName && !item.move) { model.finish(item.id, true); _note.value = null; publish(); return }
        while (true) {
            // « Annuler » landed before the launch: nothing leaves the phone
            if (!QueueCancel.mayLaunch(model.cancelAsked(item.id))) { model.finishCancelled(item.id); publish(); return }
            val e = runCatching { launch() }.exceptionOrNull() ?: break
            when {
                e is UploadService.Busy -> if (!waitForFreeTv(item)) return       // another screen started an upload meanwhile: it goes first, then this one
                isBackgroundRefusal(e) -> { requeuePaused(item, QueueTexts.PAUSED_BACKGROUND); return }
                else -> { model.finish(item.id, false, CopyReport.failed(app, item, CopyStep.START, null, e = e)); publish(); return }
            }
        }
        launchedAttempt = item.attempts; launchedId = item.id
        lastPercent = 0; lastStep = CopyStep.CONNECT; lastWaitReason = null
        appForJournal = app
        CopyReport.step(app, CopyStep.SEND, "envoi lancé (${if (viaBt) "Bluetooth" else "Wi-Fi"})")
        // « Annuler » landed between the launch and its record (cancel() could not know it was launched): stop it now
        if (QueueCancel.afterLaunch(model.cancelAsked(item.id)) == QueueCancel.Action.STOP_UPLOAD) { UploadService.cancel(app); BtUploadService.cancel(app) }
        val outcome = watch(viaBt, item.id)
        when (castbridge.core.tv.QueueOutcome.of(outcome, model.cancelAsked(item.id), isBackgroundRefusal(outcome?.let { Exception(it) }))) {
            castbridge.core.tv.QueueOutcome.Kind.CANCELLED -> model.finishCancelled(item.id)
            castbridge.core.tv.QueueOutcome.Kind.DONE -> {
                model.finish(item.id, true)
                CopyReport.succeeded(app, item)
                credentials.remove(item.id)
                // the name the TV holds (the assistant may have renamed it on the way), then « Titre / Saison » for a series
                val held = (UploadService.state.value as? UploadService.State.Done)?.job?.fileName ?: item.name
                val tvBase = afterBase
                if (tvBase != null) runCatching { SeriesClassifying.afterSend(app, castbridge.core.tv.TvClient(tvBase, afterCred), held) }
            }
            castbridge.core.tv.QueueOutcome.Kind.PAUSE_TIME_LIMIT -> { requeuePaused(item, QueueTexts.PAUSED_TIME_LIMIT); return }
            castbridge.core.tv.QueueOutcome.Kind.PAUSE_BACKGROUND -> { requeuePaused(item, QueueTexts.PAUSED_BACKGROUND); return }
            // R-19: the upload stopped because the TV did not answer: never a frozen 0 %, never a dead end; the file keeps its place and is relaunched
            // (it resumes where the TV's copy stopped) as soon as an address of the TV answers again, tries spaced 1 s, 2 s, 5 s, 10 s, then every 15 s
            castbridge.core.tv.QueueOutcome.Kind.WAIT_FOR_TV -> {
                _note.value = QueueTexts.WAITING_TV; publish()
                val r = withContext(Dispatchers.IO) {
                    castbridge.core.tv.ResumeWait.until({ tvAnswers(item) }, { paused || model.cancelAsked(item.id) }, { Thread.sleep(it) }, { System.currentTimeMillis() })
                }
                _note.value = null
                when {
                    model.cancelAsked(item.id) -> model.finishCancelled(item.id)
                    r == castbridge.core.tv.ResumeWait.Result.GAVE_UP ->
                        model.finish(item.id, false, CopyReport.failed(app, item, lastStep, lastPercent.takeIf { it > 0 }, reason = QueueTexts.TV_GONE))
                    else -> { model.release(item.id); publish(); return }           // reachable again (or paused): back to its place, the runner relaunches it
                }
            }
            // the previous upload was still ending: back to its place, again in a moment (never a failure)
            castbridge.core.tv.QueueOutcome.Kind.RETRY_SOON -> { model.release(item.id); publish(); delay(2_000); return }
            castbridge.core.tv.QueueOutcome.Kind.FAILED -> {
                // the Wi-Fi Direct group fell during the copy: back to its place for a new decision (re-join, or Bluetooth after 3 failures), at most twice
                val n = wdReroutes[item.id] ?: 0
                if (castbridge.core.link.BulkRoute.rerouteAfterLoss(viaWd, AutoWifiDirect.lostSince(wdSince), n)) {
                    wdReroutes[item.id] = n + 1
                    model.release(item.id); publish(); delay(2_000); return
                }
                wdReroutes.remove(item.id)
                model.finish(item.id, false, CopyReport.failed(app, item, lastStep, lastPercent.takeIf { it > 0 }, reason = outcome))
            }
        }
        if (!model.busy()) AutoWifiDirect.queueIdle()
        publish()
    }

    @Volatile private var lastPercent = 0
    @Volatile private var lastStep = CopyStep.CONNECT
    @Volatile private var lastWaitReason: String? = null

    /** Reroutes of a file after the loss of the Wi-Fi Direct group ([castbridge.core.link.BulkRoute.MAX_REROUTES]), in memory. */
    private val wdReroutes = ConcurrentHashMap<Long, Int>()

    private enum class Dedup { COPY, SKIPPED, CANCELLED, SAME_NAME_UNKNOWN }
    /** [sendAs] = a unique name to send under (same name and size on the TV, different content). */
    private data class DedupRun(val kind: Dedup, val sendAs: String? = null)
    private const val INDEX_WAIT_TRIES = 4
    private const val INDEX_WAIT_MS = 3_000L
    /** A MOVE waits up to 2 minutes for the TV to re-read its file (fresh hash); then the usual copy goes. */
    private const val MOVE_WAIT_TRIES = 40

    /**
     * R-12 (docs/agent-reports/copy-dedup.md): asks the TV by SIZE first (GET /api/have?size=), hashes the phone's file (off the main thread, « Vérification… n % »,
     * cancellable by « Annuler ») only when a same-size file exists on the TV or in this queue, then asks by SHA-256. The decision is [DedupDecision]: identical
     * ⇒ nothing is copied (« Copier et lire » plays the TV's file; a MOVE offers the deletion of the original only when [castbridge.core.tv.MoveProof.byContentHash]
     * holds, and Android asks the user). Any doubt (old TV, index not ready, error) ⇒ COPY.
     */
    private suspend fun contentDedupe(app: Context, item: QueueItem, base: String, cred: String?, sameName: Boolean = false): DedupRun = withContext(Dispatchers.IO) {
        val client = castbridge.core.tv.TvClient(base, cred)
        val uri = Uri.parse(item.uri)
        val size = runCatching { app.contentResolver.openFileDescriptor(uri, "r")!!.use { it.statSize } }.getOrNull()?.takeIf { it > 0 } ?: item.size
        if (size <= 0) return@withContext DedupRun(if (sameName) Dedup.SAME_NAME_UNKNOWN else Dedup.COPY)
        val action = when {
            item.move -> DedupDecision.Action.MOVE
            item.playOnTv || item.autoPlay -> DedupDecision.Action.COPY_AND_PLAY
            else -> DedupDecision.Action.COPY
        }
        val move = action == DedupDecision.Action.MOVE
        // a MOVE asks for a FRESH hash (computed by the TV from its bytes in this run, never its cache): MoveProof.byContentHash requires it
        fun ask(sha: String?): DedupDecision.Tv = runCatching { DedupDecision.parse(client.have(size, sha, fresh = move && sha != null)) }.getOrDefault(DedupDecision.Tv.Unsupported)
        val bySize = ask(null)
        val siblings = model.sameSize(item.id)
        // the original as it is now (size, date): checked again just before a deletion is offered (TOCTOU)
        val stamp0 = if (move) fileStamp(app, uri) else null
        if (!DedupDecision.mustHash(bySize, siblings.isNotEmpty() || sameName, item.force)) return@withContext DedupRun(if (sameName) Dedup.SAME_NAME_UNKNOWN else Dedup.COPY)
        val cancelled = { model.cancelAsked(item.id) }
        // a MOVE never reuses a hash kept from earlier (the file may have changed since: a photo rotated in the Gallery keeps its size)
        val sha = item.sha256?.takeIf { DedupDecision.mayReuseHash(action, it) } ?: hashUri(app, uri, item.name, size, cancelled)
        if (sha == null) return@withContext DedupRun(if (cancelled()) Dedup.CANCELLED else if (sameName) Dedup.SAME_NAME_UNKNOWN else Dedup.COPY)
        model.setHash(item.id, sha)
        // the same-size files this queue already SENT: their hash too (once, kept with the queue), to recognise the same content under another name
        for (s in siblings) if (s.sha256 == null && s.status == QueueStatus.DONE && !cancelled())
            hashUri(app, Uri.parse(s.uri), s.name, s.size, cancelled)?.let { model.setHash(s.id, it) }
        if (cancelled()) return@withContext DedupRun(Dedup.CANCELLED)
        var tv = if (bySize is DedupDecision.Tv.Unsupported) bySize else ask(sha)
        // the TV hashes a same-size candidate first when it is asked about it: a short wait (longer for a MOVE: the TV re-reads its file), never a block
        var tries = 0
        while (tv is DedupDecision.Tv.Indexing && tries++ < (if (move) MOVE_WAIT_TRIES else INDEX_WAIT_TRIES) && !cancelled()) {
            _note.value = DedupTexts.checking(item.name, 100) + " · la TV vérifie un fichier de même taille…"
            delay(INDEX_WAIT_MS)
            tv = ask(sha)
        }
        if (cancelled()) return@withContext DedupRun(Dedup.CANCELLED)
        val twin = model.twinOf(item.id)?.let { t ->
            val f = runCatching { castbridge.core.tv.TvInfo.parse(client.info()).file(t.heldAs ?: t.name) }.getOrNull()
            DedupDecision.Twin(t.name, f?.name ?: t.heldAs ?: t.name, t.size, t.sha256!!, castbridge.core.tv.TvDedupe.alreadyThere(f, size))
        }
        when (val first = DedupDecision.decide(DedupDecision.Facts(action, size, sha, tv, twin, item.force))) {
            is DedupDecision.Outcome.Copy -> {
                android.util.Log.i("TransferQueue", "copie de ${item.name} : ${first.why}")
                when {
                    !sameName -> DedupRun(Dedup.COPY)
                    // same name and size, the TV's answer says the content DIFFERS (every same-size file hashed, none equal): sent under a unique name
                    tv is DedupDecision.Tv.Absent -> {
                        val names = runCatching { castbridge.core.tv.TvInfo.parse(client.info()).files.flatMap { listOfNotNull(it.name, it.origin) }.map { it.lowercase() }.toSet() }.getOrNull()
                        if (names == null) DedupRun(Dedup.SAME_NAME_UNKNOWN) else DedupRun(Dedup.COPY, DedupDecision.uniqueName(item.name) { it.lowercase() in names })
                    }
                    else -> DedupRun(Dedup.SAME_NAME_UNKNOWN)
                }
            }
            is DedupDecision.Outcome.Skip -> {
                // MOVE: second proof, read by the phone itself (the whole TV file up to 64 MiB, else edges + 8 blocks at positions the TV cannot guess)
                var out = if (first.deleteSource) DedupDecision.afterEdges(first, independentProof(app, client, uri, first.tvName, size, sha)) else first
                // the original must still be the file that was hashed (size and date), right now
                val now = if (out.deleteSource) fileStamp(app, uri) else null
                if (out.deleteSource && (stamp0 == null || now == null || !castbridge.core.tv.MoveProof.unchanged(stamp0.first, stamp0.second, now.first, now.second)))
                    out = out.copy(deleteSource = false, text = castbridge.core.tv.MoveProof.CHANGED_TEXT)
                // « Copier sur la TV et lire » from the phone's player (playOnTv): CastSession starts the TV's file at the phone's position (heldAs);
                // a plain « Copier et lire » (autoPlay) is started here
                if (out.play && !item.playOnTv && out.tvName.isNotEmpty()) runCatching { client.play(out.tvName) }
                // « Annuler » landed during the checks: nothing is offered for deletion (R-09)
                if (cancelled()) return@withContext DedupRun(Dedup.CANCELLED)
                // MOVE: the original goes ONLY with both proofs (fresh hash + the phone's own reading), unchanged since, and the user confirms (MoveHandler)
                if (out.deleteSource) UploadService.offerVerifiedMove(UploadService.MoveRequest(uri, out.tvName, size, now!!.second))
                model.finishSkipped(item.id, out.tvName, out.text)
                DedupRun(Dedup.SKIPPED)
            }
        }
    }

    /**
     * The phone's own proof, independent of what the TV says ([castbridge.core.tv.MoveProof.samplePlan]): up to 64 MiB it reads the WHOLE file of the TV (/stream/)
     * and hashes it itself; above, the head, the tail and 8 blocks at positions drawn by a SecureRandom. Every range must come back as asked: 206 (or 200 for
     * the whole file from 0), the start asked for, and the total size expected (a longer TV file fails). Any error = false.
     */
    private fun independentProof(app: Context, client: castbridge.core.tv.TvClient, uri: Uri, tvName: String, size: Long, sha: String): Boolean = runCatching {
        if (tvName.isEmpty()) return false
        val plan = castbridge.core.tv.MoveProof.samplePlan(size, java.security.SecureRandom())
        fun ranged(at: Long): castbridge.core.tv.TvClient.Ranged {
            val r = client.openRange(tvName, at)
            if (!((r.code == 206 || (r.code == 200 && at == 0L)) && r.start == at && r.total == size)) { r.input.close(); throw java.io.IOException("plage refusée") }
            return r
        }
        if (plan.size == 1 && plan[0].first == 0L && plan[0].second.toLong() == size)
            return ranged(0).input.use { ContentHash.sha256(it, size) } == sha
        val tvParts = plan.map { (at, n) -> ranged(at).input.use { readN(it, n) } }
        val localParts = plan.map { (at, n) -> app.contentResolver.openFileDescriptor(uri, "r")!!.use { pfd ->
            java.io.FileInputStream(pfd.fileDescriptor).use { s -> s.channel.position(at); readN(s, n) } } }
        castbridge.core.tv.MoveProof.bySamples(plan, localParts, tvParts)
    }.getOrDefault(false)

    private fun readN(s: java.io.InputStream, n: Int): ByteArray {
        val b = ByteArray(n); var off = 0
        while (off < n) { val r = s.read(b, off, n - off); if (r < 0) break; off += r }
        return if (off == n) b else b.copyOf(off)
    }

    /** SHA-256 of a phone file, streamed (never in memory), progress in the queue's note; null = cancelled or unreadable. */
    private fun hashUri(app: Context, uri: Uri, name: String, size: Long, cancelled: () -> Boolean): String? = runCatching {
        app.contentResolver.openInputStream(uri)!!.use { inp ->
            var last = -1
            ContentHash.sha256(inp, size, cancelled = cancelled) { r, t ->
                val pct = if (t > 0) (r * 100 / t).toInt() else 0
                if (pct != last) { last = pct; _note.value = DedupTexts.checking(name, pct) }
            }
        }
    }.getOrNull()

    /**
     * R-19: does an address of the file's TV answer again? The trusted link: its session (Bluetooth only counts: the relaunch goes over it) or a Wi-Fi
     * address that answers a « hello ». A TV of that name: its typed address, the address a screen reaches it at, the last address that answered (10 min);
     * none known: true (the upload's own discovery searches again).
     */
    private fun tvAnswers(item: QueueItem): Boolean {
        val tvName = item.tvName
        if (tvName == null) {
            val s = (TvLinkManager.state.value as? LinkUi.Connected)?.session ?: return false
            if (item.linkTv != null && s.tv.address != item.linkTv) return false
            return s.base == null || UploadService.helloOk(s.base!!) || UploadService.trustedBases(s.tv.mdns ?: s.tv.name).any { UploadService.helloOk(it) }
        }
        val app = appForJournal ?: return true
        val bases = (listOfNotNull(item.host?.let { h -> if (':' in h) "http://$h" else "http://$h:8765" }, UploadService.addressMemory(app).recall(tvName), UploadService.hintFor(tvName)) +
            UploadService.trustedBases(tvName)).distinct()
        return bases.isEmpty() || bases.any { UploadService.helloOk(it) }
    }

    /** Android refused the upload service in the background: the file waits, the queue resumes when CastBridge is opened (said in French). */
    private fun requeuePaused(item: QueueItem, why: String) {
        model.release(item.id)
        paused = true
        _note.value = why
        publish()
    }

    /** Pauses the queue with [why] (the running file, if any, ends by itself; the next one waits for the app). */
    internal fun pauseWith(why: String) { paused = true; _note.value = why; publish() }

    /**
     * The TV link, waiting up to a minute for it to be (re)established. Leaves at once, with the explained refusal (code, text), when the TV
     * refuses this phone ([castbridge.core.trust.LinkRefusalTexts.failureFor]): no point waiting for a link that will not come by itself.
     */
    private suspend fun waitForTv(): Pair<castbridge.core.trust.LinkSession?, Pair<Int, String>?> {
        repeat(120) {
            val st = TvLinkManager.state.value
            (st as? LinkUi.Connected)?.session?.let { return it to null }
            val state = when (st) { is LinkUi.Status -> st.view.state; else -> null }
            val code = state?.let { castbridge.core.trust.LinkRefusalTexts.codeOf(it) }
            if (code != null) return null to (code to castbridge.core.trust.LinkRefusalTexts.ticket(code))
            delay(500)
        }
        return null to null
    }

    /** Null when the file arrived, else the reason. Waits for the service to start (90 s at most), then for it to end. */
    /** Where the copy is (percent, step), and each new reason it waits for, in the internal journal (« où en est la copie » when it stops later). */
    private fun progress(sent: Long, total: Long, waitReason: String?) {
        if (total > 0) lastPercent = (sent * 100 / total).toInt()
        lastStep = if (sent > 0) CopyStep.SEND else CopyStep.CONNECT
        if (waitReason != null && waitReason != lastWaitReason) {
            lastWaitReason = waitReason
            runCatching { appForJournal?.let { CopyReport.step(it, lastStep, "attente à $lastPercent % : $waitReason") } }
        } else if (waitReason == null) lastWaitReason = null
    }
    @Volatile private var appForJournal: Context? = null

    private suspend fun watch(viaBt: Boolean, id: Long): String? {
        var started = false; var waited = 0
        while (true) {
            delay(500)
            if (viaBt) {
                when (val s = BtUploadService.state.value) {
                    is ResumableUpload.State.Done -> return null
                    is ResumableUpload.State.Failed -> return s.reason
                    is ResumableUpload.State.Uploading -> { started = true; progress(s.sent, s.total, null) }
                    is ResumableUpload.State.Waiting -> { started = true; progress(s.sent, s.total, s.reason) }
                    // no state yet during the Bluetooth negotiation: interrupted only once the upload no longer holds the slot
                    null -> if (started && !BtUploadService.active()) return if (model.cancelAsked(id)) null else "Envoi interrompu"
                }
                if (!started && BtUploadService.active()) started = true
            } else {
                when (val s = UploadService.state.value) {
                    is UploadService.State.Done -> return null
                    is UploadService.State.Failed -> return s.reason
                    is UploadService.State.Uploading -> { started = true; progress(s.sent, s.total, null) }
                    is UploadService.State.Waiting -> { started = true; progress(s.sent, s.total, s.reason) }
                    UploadService.State.Idle -> if (started && !UploadService.active()) return if (model.cancelAsked(id)) null else "Envoi interrompu"
                }
                if (!started && UploadService.active()) started = true
            }
            if (!started && ++waited > 180) return "L'envoi n'a pas démarré (aucune réponse du service d'envoi en 90 s)"
        }
    }
}

/** Android's time budget for background transfers is used up ([TransferQueueService.onTimeout]): the queue pauses with that cause. */
internal fun TransferQueue.pauseForTimeLimit() = pauseWith(castbridge.core.tv.QueueTexts.PAUSED_TIME_LIMIT)
