package castbridge.sender

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import castbridge.core.tv.Mp4Atoms
import castbridge.core.tv.Progressive
import castbridge.core.tv.ResumableUpload
import castbridge.core.tv.TvClient
import castbridge.core.xfer.FileBlockSource
import castbridge.core.xfer.HttpConn
import castbridge.core.xfer.HttpTransferApi
import castbridge.core.xfer.TransferClient
import castbridge.core.xfer.WifiLane
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import kotlin.concurrent.thread

/**
 * Sends a whole video to CastBridge TV, resuming after any network loss, then starts playback.
 * Runs as a foreground service so it survives the screen turning off.
 */
class UploadService : Service() {
    data class Job(val fileName: String, val tvName: String, val manualHost: String?, val pin: String? = null,
                   val progressive: Boolean = false, val autoPlay: Boolean = true, val target: String? = null,
                   /** Move instead of copy: once the TV holds the complete file, offer to delete it from the phone. */
                   val move: Boolean = false,
                   /**
                    * Ordered classic path (one connection from byte 0, the TV's visible `.part`), never « Transfert rapide »: « Copier sur la TV et lire » of a
                    * video, so that the TV can start during the copy ([castbridge.core.phone.CopyRoute], R-08). Nothing starts by itself (unlike [progressive]).
                    */
                   val ordered: Boolean = false)

    /** A moved file that the TV now holds completely: the screen deletes it from the phone (with Android's confirmation). */
    data class MoveRequest(val uri: Uri, val name: String, val size: Long)

    /** Thrown by [start] while another upload runs: nothing is started, nothing of the running upload is touched (R-09). */
    class Busy : IllegalStateException(BUSY_TEXT)

    sealed class State {
        object Idle : State()
        data class Uploading(val job: Job, val sent: Long, val total: Long) : State()
        data class Waiting(val job: Job, val sent: Long, val total: Long, val reason: String) : State()
        data class Done(val job: Job) : State()
        data class Failed(val job: Job?, val reason: String) : State()
    }

    @Volatile private var cancelled = false
    /** This upload's reservation of the phone's single upload slot ([castbridge.core.tv.UploadSlot], taken by [start]). */
    @Volatile private var myToken = 0L
    /** A cause set by this upload's owner (Android's time limit): never overwritten by what its worker returns afterwards. */
    @Volatile private var endCause: String? = null
    @Volatile private var moveUri: Uri? = null
    @Volatile private var progressiveNow = false     // play as soon as enough has arrived (see switchToFullPreload)
    @Volatile private var started = false            // playback already launched during the upload
    private val meter = castbridge.core.tv.RateMeter()
    private var worker: Thread? = null
    private var discovery: TvDiscovery? = null
    private var netCallback: ConnectivityManager.NetworkCallback? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var wifiLock2: WifiManager.WifiLock? = null
    private var lastNotified = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) { cancelled = true; stopSelf(); return START_NOT_STICKY }
        val uri = intent?.data
        val tvName = intent?.getStringExtra(EXTRA_TV)
        val token = intent?.getLongExtra(EXTRA_TOKEN, 0L) ?: 0L
        if (uri == null || tvName == null) { slot.release(token); stopSelf(); return START_NOT_STICKY }
        if (worker?.isAlive == true) {
            // R-09: a start that slipped past [start]'s check is never dropped in silence (and never leaves Android waiting for startForeground)
            runCatching { val n = notification("Envoi en cours…", 0)
                if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else startForeground(NOTIF, n) }
            Log.w(TAG, "start refused while the previous upload ends: ${intent.getStringExtra(EXTRA_NAME)}")
            // the reservation of this start is given back and the refusal is said (the queue keeps the file and shows the cause)
            // the queue puts the file back at its place and starts it again in a moment (never a failure)
            if (slot.release(token)) _state.value = State.Failed(null, castbridge.core.tv.QueueTexts.PREVIOUS_ENDING)
            return START_NOT_STICKY
        }
        val name = intent.getStringExtra(EXTRA_NAME) ?: uri.lastPathSegment ?: "video"
        val job = Job(name, tvName, intent.getStringExtra(EXTRA_HOST), intent.getStringExtra(EXTRA_PIN),
            intent.getBooleanExtra(EXTRA_PROGRESSIVE, false), intent.getBooleanExtra(EXTRA_AUTOPLAY, true), intent.getStringExtra(EXTRA_TARGET),
            intent.getBooleanExtra(EXTRA_MOVE, false), intent.getBooleanExtra(EXTRA_ORDERED, false))
        moveUri = if (job.move) uri else null
        myToken = token
        try {
            val n = notification("Envoi de $name…", 0)
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            else startForeground(NOTIF, n)
        } catch (e: Exception) {
            Log.e(TAG, "startForeground", e)
            _state.value = State.Failed(job, "$REFUSED : ${e.message}")
            slot.release(token)
            stopSelf(); return START_NOT_STICKY
        }
        acquireLocks()
        val disc = if (job.manualHost == null) TvDiscovery(this).also { it.start(); discovery = it } else null
        watchNetwork(disc)
        cancelled = false
        instance = this
        _notice.value = null; _speed.value = 0
        worker = thread(name = "upload") { runJob(uri, job, disc) }
        return START_NOT_STICKY
    }

    private fun runJob(uri: Uri, job0: Job, disc: TvDiscovery?) {
        val total = runCatching { contentResolver.openFileDescriptor(uri, "r")!!.use { it.statSize } }.getOrDefault(-1)
        if (total <= 0) { finish(State.Failed(job0, "Fichier illisible")); return }
        val resolve: () -> String? = {
            job0.manualHost?.let { h -> if (':' in h) "http://$h" else "http://$h:8765" } ?: disc?.find(job0.tvName)?.base
        }
        // « Rangement automatique » : the clean name is only kept if the TV is reachable and has no file of that name (never resume into another file)
        val job = castbridge.sender.agent.AgentAuto.settle(job0, resolve)
        progressiveNow = job.progressive
        started = false
        val castMode = if (job.move) "move" else "copy"
        val castStart = System.currentTimeMillis()
        PhoneConnect.track("cast_start", mapOf("channel" to "wifi", "mode" to castMode, "bytes" to total))
        if (progressiveNow && Mp4Atoms.isIsoName(job.fileName)) {
            // An MP4 whose index (moov) is at the end cannot start before the last byte arrives.
            val layout = mp4LayoutOf(this, uri, total)
            if (layout == Mp4Atoms.Layout.MOOV_AT_END) {
                progressiveNow = false
                _notice.value = "Ce fichier ne peut pas être lu pendant l'envoi (index MP4 en fin de fichier) : " +
                    "préchargement complet, la lecture démarrera à la fin de l'envoi."
            }
        }
        var lastAttempt = 0L
        var prevSent = -1L
        // a token renewed while the transfer waits is picked up; one the TV refused is never sent again (core/.../TvClient.kt)
        val credential: () -> String? = { if (castbridge.core.trust.TvAuth.isToken(job.pin)) (TvLinkManager.credentialFor(job.tvName) ?: job.pin) else job.pin }
        val t0 = System.nanoTime(); var first = -1L
        val onState: (ResumableUpload.State) -> Unit = { s ->
            if (s is ResumableUpload.State.Uploading) {
                if (first < 0) first = s.sent
                val dt = (System.nanoTime() - t0) / 1_000_000
                if (dt > 500) _average.value = (s.sent - first) * 1000 / dt
                // Full speed by default: the bigger the lead over playback, the longer the video survives a lost network.
                if (prevSent >= 0 && s.sent - prevSent in 1..(16L shl 20)) meter.add(s.sent - prevSent)
                prevSent = s.sent
                _speed.value = meter.bytesPerSec()
                if (progressiveNow && !started && System.currentTimeMillis() - lastAttempt > 1000 &&
                    s.sent >= Progressive.bootstrapBytes(s.total, _speed.value)) {
                    lastAttempt = System.currentTimeMillis()
                    val base = resolve()
                    if (base != null) thread(name = "progressive-start") {
                        // 409 "buffering" just means: not enough yet, the next attempt in a second will do.
                        if (runCatching { TvClient(base, job.pin).play(job.fileName) }.isSuccess) started = true
                    }
                }
            }
            // a late write of an upload whose slot was released (cancelled, service destroyed) never overwrites the next upload's state
            if (mine()) slot.touch(myToken)                      // sign of life: a silent owner's reservation becomes stale (UploadSlot)
            if (mine()) _state.value = when (s) {
                is ResumableUpload.State.Uploading -> State.Uploading(job, s.sent, s.total)
                is ResumableUpload.State.Waiting -> State.Waiting(job, s.sent, s.total, s.reason)
                ResumableUpload.State.Done -> State.Done(job)
                is ResumableUpload.State.Failed -> State.Failed(job, s.reason)
            }
            notifyProgress(s)
        }
        // « Transfert rapide » : plusieurs connexions en parallèle, fichier découpé en blocs (docs/TRANSFER.md). Jamais pendant « lire pendant l'envoi »
        // ni pour « Copier sur la TV et lire » d'une vidéo (job.ordered, R-08) : les blocs n'arrivent pas dans l'ordre et restent invisibles au lecteur de
        // la TV (.cbx) jusqu'à la fin ; une TV qui ne connaît pas le protocole (null) reçoit l'envoi classique.
        val fast = if (!progressiveNow && !job.ordered && FastTransfer.enabled(this)) runFast(uri, job, total, resolve, credential, onState) else null
        val result = fast ?: ResumableUpload(job.fileName, total, resolve, { off -> openAt(uri, off) }, { cancelled }, pin = job.pin,
            target = job.target, onCheck = { _check.value = it }, credential = credential).run(onState)
        run {
            val ms = System.currentTimeMillis() - castStart
            val ok = result == ResumableUpload.State.Done
            val err = when { ok -> null; cancelled -> "cancelled"; else -> "failed" }
            PhoneConnect.castEnd("wifi", castMode, if (ok) total else maxOf(0L, prevSent), ms, ok, err)
            if (!ok && !cancelled) PhoneConnect.error("send", "upload", (result as? ResumableUpload.State.Failed)?.reason)
        }
        if (result == ResumableUpload.State.Done) castbridge.sender.agent.AgentAuto.completed(this, job.fileName)
        if (result == ResumableUpload.State.Done && !cancelled) moveUri?.let { u -> checkMoved(job, u, resolve()) }
        if (result == ResumableUpload.State.Done) {
            if (started || !job.autoPlay) { finish(State.Done(job)); return }      // already playing (or the caller starts it)
            val base = resolve()
            val r = if (base != null) runCatching { TvClient(base, job.pin).play(job.fileName) } else Result.failure(IllegalStateException())
            val why = (r.exceptionOrNull() as? TvClient.HttpError)?.message?.takeIf { "needsForeground" in it }
                ?.let { TvClient.str(it.substringAfter(": "), "message") }
            finish(if (r.isSuccess) State.Done(job) else State.Failed(job, why?.let { "Fichier envoyé. $it" } ?: "Fichier envoyé, mais lancement impossible : réessayez « Lire »"))
        } else finish(_state.value.takeIf { it is State.Failed } ?: State.Failed(job, "annulé"))
    }

    /**
     * Transfert rapide. null = la TV n'a pas le protocole multivoie (ou ne peut pas l'utiliser) : l'appelant envoie à l'ancienne.
     * Le débit alimente la même progression (pourcentage, durée) que l'envoi classique.
     */
    private fun runFast(uri: Uri, job: Job, total: Long, resolve: () -> String?, credential: () -> String?,
                        onState: (ResumableUpload.State) -> Unit): ResumableUpload.State? {
        var sent = 0L
        // the TV may need a moment to be (re)discovered; the classic path waits the same way
        while (!cancelled && resolve() == null) { onState(ResumableUpload.State.Waiting(sent, total, "TV introuvable")); Thread.sleep(1000) }
        if (cancelled) return ResumableUpload.State.Failed("annulé")
        val pfd = runCatching { contentResolver.openFileDescriptor(uri, "r") }.getOrNull() ?: return null
        pfd.use {
            val ch = FileInputStream(pfd.fileDescriptor).channel
            val connect: () -> java.nio.channels.SocketChannel = {
                val base = resolve() ?: throw IOException("TV introuvable")
                val u = Uri.parse(base)
                HttpConn.tcp(u.host ?: throw IOException("adresse de la TV illisible"), if (u.port > 0) u.port else 8765)()
            }
            val tc = TransferClient(HttpTransferApi(resolve, credential), FileBlockSource(ch, total), job.fileName,
                lanes = { id, max -> listOf(WifiLane("wifi", resolve()?.removePrefix("http://") ?: "tv", id, connect, credential, maxStreams = max)) },
                target = job.target, cancelled = { cancelled },
                onProgress = { s, t -> sent = s; onState(ResumableUpload.State.Uploading(s, t)) },
                onWaiting = { why -> onState(ResumableUpload.State.Waiting(sent, total, why)) },
                onEvent = { Log.i(TAG, it) },
                // the TV's measured disk speed: said once, in French, when the disk (not the Wi-Fi) is what limits the copy
                onDisk = { _, note -> if (note != null && _notice.value != note) _notice.value = note })
            return when (val r = tc.run()) {
                TransferClient.Result.Done -> ResumableUpload.State.Done.also(onState)
                TransferClient.Result.Unsupported -> null
                TransferClient.Result.Cancelled -> ResumableUpload.State.Failed("annulé").also(onState)
                is TransferClient.Result.Failed -> ResumableUpload.State.Failed(r.reason).also(onState)
            }
        }
    }

    /**
     * Move: never delete on faith. Ask the TV for its file list and require the complete file with exactly the local size;
     * only then hand the deletion to the screen (Android shows its own "delete?" confirmation).
     */
    private fun checkMoved(job: Job, uri: Uri, base: String?) {
        val local = runCatching { contentResolver.openFileDescriptor(uri, "r")!!.use { it.statSize } }.getOrDefault(-1L)
        val tvFile = base?.let { b ->
            runCatching { castbridge.core.tv.TvInfo.parse(TvClient(b, job.pin).info()) }.getOrNull()
                ?.files?.firstOrNull { it.name.equals(job.fileName, ignoreCase = true) }
        }
        // a cancelled move never asks for the deletion (R-09, audit 3)
        if (castbridge.core.tv.QueueCancel.mayDeleteMoved(cancelled, local > 0 && tvFile != null && tvFile.complete && tvFile.size == local)) {
            offerMove(MoveRequest(uri, job.fileName, local))
            runCatching {
                val open = android.app.PendingIntent.getActivity(this, 2, Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP), android.app.PendingIntent.FLAG_IMMUTABLE)
                getSystemService(NotificationManager::class.java).notify(NOTIF_MOVE, Notification.Builder(this, CHANNEL)
                    .setSmallIcon(R.drawable.ic_stat_castbridge).setContentTitle("« ${job.fileName} » est sur la TV")
                    .setContentText("Touchez pour le retirer du téléphone").setAutoCancel(true).setContentIntent(open).build())
            }
        } else _moveNote.value = "« ${job.fileName} » est envoyé mais la TV n'a pas confirmé une copie complète : il reste sur le téléphone."
    }

    private fun openAt(uri: Uri, offset: Long): InputStream {
        val pfd = contentResolver.openFileDescriptor(uri, "r") ?: throw IOException("cannot open")
        val fis = ParcelFileInputStream(pfd)
        fis.channel.position(offset)
        return fis
    }

    /** FileInputStream that also closes its ParcelFileDescriptor. */
    private class ParcelFileInputStream(private val pfd: android.os.ParcelFileDescriptor) : FileInputStream(pfd.fileDescriptor) {
        override fun close() { try { super.close() } finally { pfd.close() } }
    }

    /** On any network change, rediscover the TV (it may have a new address) so the upload resumes fast. */
    private fun watchNetwork(disc: TvDiscovery?) {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { disc?.restart() }
        }
        runCatching { cm.registerDefaultNetworkCallback(cb); netCallback = cb }
    }

    private fun notifyProgress(s: ResumableUpload.State) {
        val now = System.currentTimeMillis()
        if (now - lastNotified < 1000 && s is ResumableUpload.State.Uploading) return
        lastNotified = now
        val (text, pct) = when (s) {
            is ResumableUpload.State.Uploading -> "Envoi vers la TV" to (s.sent * 100 / s.total).toInt()
            is ResumableUpload.State.Waiting -> "En attente du réseau (${s.reason})" to (s.sent * 100 / s.total).toInt()
            else -> return
        }
        runCatching { getSystemService(NotificationManager::class.java).notify(NOTIF, notification(text, pct)) }
    }

    private fun notification(text: String, pct: Int): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Envoi vers la TV", NotificationManager.IMPORTANCE_LOW))
        val cancel = android.app.PendingIntent.getService(this, 1,
            Intent(this, UploadService::class.java).setAction(ACTION_CANCEL), android.app.PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL).setContentTitle("CastBridge").setContentText("$text · $pct %").setSubText("$pct %")
            .setSmallIcon(R.drawable.ic_stat_castbridge).setOngoing(true).setOnlyAlertOnce(true)
            .setProgress(100, pct, false)
            .addAction(Notification.Action.Builder(null, "Annuler", cancel).build()).build()
    }

    /** Android 15: the dataSync time budget is used up. The upload stops (the TV keeps its partial copy) and the queue pauses with this cause. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        // the owner of the current generation sets the cause; the worker (still running) keeps the reservation until its own end,
        // and its late « annulé » cannot overwrite this cause ([finish], QueueOutcome.finalCause); the service stops at once as Android requires
        endCause = castbridge.core.tv.QueueTexts.TIME_LIMIT
        cancelled = true
        if (mine()) _state.value = State.Failed((_state.value as? State.Uploading)?.job ?: (_state.value as? State.Waiting)?.job, castbridge.core.tv.QueueTexts.TIME_LIMIT)
        stopSelf()
    }

    private fun acquireLocks() {
        wakeLock = runCatching {
            getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "castbridge:upload")
                .also { it.acquire(6 * 3600_000L) }
        }.getOrNull()
        // Full speed: no Wi-Fi power save while sending (LOW_LATENCY on API 29+, effective while the app is in front; HIGH_PERF otherwise).
        wifiLock = runCatching {
            @Suppress("DEPRECATION")
            val mode = if (Build.VERSION.SDK_INT >= 29) WifiManager.WIFI_MODE_FULL_LOW_LATENCY else WifiManager.WIFI_MODE_FULL_HIGH_PERF
            (applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).createWifiLock(mode, "castbridge-upload").also { it.acquire() }
        }.getOrNull()
        if (Build.VERSION.SDK_INT >= 29) wifiLock2 = runCatching {
            @Suppress("DEPRECATION")
            (applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "castbridge-upload-hp").also { it.acquire() }
        }.getOrNull()
    }

    /** Only the owner of the current reservation writes the shared state (a released or taken-over generation never does). */
    private fun mine() = slot.holds(myToken)

    private fun finish(s0: State) {
        val s = endCause?.let { c -> (s0 as? State.Failed)?.copy(reason = castbridge.core.tv.QueueOutcome.finalCause(c, s0.reason)) } ?: s0
        if (mine()) _state.value = s
        slot.release(myToken)
        stopSelf()
    }

    override fun onDestroy() {
        cancelled = true
        if (instance === this) instance = null
        // released by the worker's own end ([finish]) when it still runs (cancelled): never before it, so no next upload overlaps it
        if (worker?.isAlive != true) slot.release(myToken)
        discovery?.stop()
        netCallback?.let { cb -> runCatching { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(cb) } }
        runCatching { wakeLock?.let { if (it.isHeld) it.release() } }
        runCatching { wifiLock?.let { if (it.isHeld) it.release() } }
        runCatching { wifiLock2?.let { if (it.isHeld) it.release() } }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "UploadService"
        private const val CHANNEL = "upload"
        private const val NOTIF = 2
        const val ACTION_CANCEL = "castbridge.CANCEL_UPLOAD"
        const val EXTRA_TV = "tv"
        const val EXTRA_NAME = "name"
        const val EXTRA_HOST = "host"
        const val EXTRA_PIN = "pin"
        const val EXTRA_PROGRESSIVE = "progressive"
        const val EXTRA_AUTOPLAY = "autoplay"
        const val EXTRA_TARGET = "target"
        private val _check = MutableStateFlow<castbridge.core.tv.TvClient.StorageCheck?>(null)
        /** The TV's pre-flight answer for the current upload (destination, free space left after it). */
        val check: StateFlow<castbridge.core.tv.TvClient.StorageCheck?> = _check
        private val _average = MutableStateFlow(0L)
        /** Average speed of the current upload since it (re)started, bytes per second. */
        val average: StateFlow<Long> = _average
        @Volatile private var instance: UploadService? = null
        private val _notice = MutableStateFlow<String?>(null)
        /** Explains automatic fallbacks (e.g. MP4 index at the end of the file). */
        val notice: StateFlow<String?> = _notice
        private val _speed = MutableStateFlow(0L)
        /** Current upload speed in bytes per second. */
        val speed: StateFlow<Long> = _speed

        /** The user gave up on playing while uploading: keep uploading at full speed, play when complete. */
        fun switchToFullPreload() { instance?.let { it.progressiveNow = false; it.started = false } }
        private val _moveReady = MutableStateFlow<MoveRequest?>(null)
        /** Set when a moved file is safely on the TV; the screen deletes it and calls [moveHandled]. */
        val moveReady: StateFlow<MoveRequest?> = _moveReady
        private val _moveNote = MutableStateFlow<String?>(null)
        val moveNote: StateFlow<String?> = _moveNote
        private val moves = castbridge.core.tv.MoveInbox<MoveRequest>()
        /** Several moves ending close together wait in turn (never overwritten). */
        private fun offerMove(r: MoveRequest) { moves.offer(r); _moveReady.value = moves.head() }
        /**
         * R-12: a MOVE whose content the TV ALREADY holds, proven by hash ([castbridge.core.tv.MoveProof.byContentHash], nothing was copied): the screen asks
         * Android to delete the original exactly like after a verified copy ([MoveHandler], with Android's own confirmation). Never called on the TV's word alone.
         */
        fun offerVerifiedMove(r: MoveRequest) = offerMove(r)
        /** The current deletion request is handled: the next one (if any) is shown. */
        fun moveHandled() { _moveReady.value = moves.done() }
        fun noteHandled() { _moveNote.value = null }
        const val EXTRA_TOKEN = "slot"
        /** The phone's single upload slot, shared with [BtUploadService]: reserved atomically by [start] before any service starts (R-09, audit 1). */
        val slot = castbridge.core.tv.UploadSlot()
        private const val NOTIF_MOVE = 7
        const val EXTRA_MOVE = "move"
        const val EXTRA_ORDERED = "ordered"
        /** Prefix of the failure when Android refuses the foreground service (in the background since Android 12): the queue waits for the app instead. */
        const val REFUSED = "Service refusé par le système"
        const val BUSY_TEXT = "Un envoi vers la TV est déjà en cours : celui-ci n'a pas été lancé. « Copier sur la TV » le met dans la file d'attente."
        /** An upload is running in this process (its worker thread is alive). */
        fun active(): Boolean = slot.held()
        private val _state = MutableStateFlow<State>(State.Idle)
        val state: StateFlow<State> = _state

        fun start(ctx: Context, uri: Uri, fileName: String, tvName: String, manualHost: String?, pin: String? = null,
                  progressive: Boolean = false, autoPlay: Boolean = true, target: String? = null, move: Boolean = false, ordered: Boolean = false) {
            // library assistant, option « Rangement automatique des nouveaux envois » (off by default, docs/LIBRARY-AGENT.md)
            // R-09: one upload at a time, reserved ATOMICALLY here (before the service exists): the loser of a race is refused, never dropped in silence
            val token = slot.tryReserve(fileName) ?: throw Busy()
            try { startReserved(ctx, token, uri, fileName, tvName, manualHost, pin, progressive, autoPlay, target, move, ordered) }
            catch (e: Throwable) { slot.release(token); throw e }
        }

        private fun startReserved(ctx: Context, token: Long, uri: Uri, fileName: String, tvName: String, manualHost: String?, pin: String?,
                                  progressive: Boolean, autoPlay: Boolean, target: String?, move: Boolean, ordered: Boolean) {
            val fileName = castbridge.sender.agent.AgentAuto.nameFor(ctx, fileName)       // a CANDIDATE: confirmed in the service once the TV has been asked
            val i = Intent(ctx, UploadService::class.java).setData(uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .putExtra(EXTRA_TV, tvName).putExtra(EXTRA_NAME, fileName).putExtra(EXTRA_HOST, manualHost).putExtra(EXTRA_PIN, pin?.takeIf { it.isNotEmpty() })
                .putExtra(EXTRA_PROGRESSIVE, progressive).putExtra(EXTRA_AUTOPLAY, autoPlay).putExtra(EXTRA_TARGET, target).putExtra(EXTRA_MOVE, move)
                .putExtra(EXTRA_ORDERED, ordered).putExtra(EXTRA_TOKEN, token)
            _state.value = State.Idle; _check.value = null; _average.value = 0
            ctx.startForegroundService(i)
        }

        fun cancel(ctx: Context) {
            runCatching { ctx.startService(Intent(ctx, UploadService::class.java).setAction(ACTION_CANCEL)) }
        }
    }
}
