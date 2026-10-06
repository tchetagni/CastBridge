package castbridge.sender.player

import android.content.Context
import android.content.Intent
import androidx.media3.common.C
import castbridge.core.phone.CastAction
import castbridge.core.phone.CastPlan
import castbridge.core.phone.CopyHandoff
import castbridge.core.phone.CopyRoute
import castbridge.core.phone.CopyTransport
import castbridge.core.phone.Handoff
import castbridge.core.phone.MediaKind
import castbridge.core.phone.RemoteClock
import castbridge.core.tv.Mp4Atoms
import castbridge.core.tv.TvClient
import castbridge.core.tv.TvInfo
import castbridge.core.upnp.Didl
import castbridge.sender.MediaServer
import castbridge.sender.Renderer
import castbridge.sender.ServerService
import castbridge.sender.Tv
import castbridge.sender.UploadService
import castbridge.sender.Upnp
import castbridge.sender.mp4LayoutOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/** A TV the phone player can send to. */
sealed class CastTarget {
    abstract val name: String
    data class Box(val tv: Tv, val pin: String) : CastTarget() { override val name get() = tv.name }
    data class Dlna(val renderer: Renderer) : CastTarget() { override val name get() = renderer.name }
}

/** What the TV is doing with the phone's file; the phone is its remote control meanwhile. */
data class Remote(
    val target: CastTarget,
    val item: PlayItem,
    val action: CastAction,
    val phase: Phase,
    val clock: RemoteClock = RemoteClock(),
    /** Last position the TV reported while playing (used if it stops before "Revenir sur le téléphone"). */
    val lastKnownPos: Long = 0,
    /** Local duration, to clamp the position when coming back. */
    val localDurMs: Long = 0,
    val volume: Int? = null,
    val message: String? = null,
    /** R-18: colour of [message] while copying (core TransferStatusLine: green copying, orange waiting/slowed); null = the usual text colour. */
    val messageLevel: castbridge.core.ux.SignalLevel? = null,
    val servedByPhone: Boolean = false,
    /** While the file is copied to the TV before it takes over: percentage, time left, time before the hand-over. */
    val copy: castbridge.core.phone.CopyProgress? = null,
    /** The phone's own player plays this file while it is copied (from « Ouvrir avec », nothing plays here: the screen must not say it does). */
    val phonePlays: Boolean = false,
) {
    enum class Phase { STARTING, COPYING, PLAYING, ENDED, FAILED }
    /** The TV has taken over: the phone shows the remote control instead of its own player. */
    val tvHasIt get() = phase == Phase.PLAYING || phase == Phase.ENDED
}

/**
 * The cast in progress (one at a time, process-wide): starts playback on the TV at the phone's position, keeps the remote
 * control state in sync (polling every second), and hands playback back to the phone at the TV's position.
 */
object CastSession {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<Remote?>(null)
    val state: StateFlow<Remote?> = _state
    private val _notices = MutableSharedFlow<String>(extraBufferCapacity = 4)
    /** One-off messages for a toast (e.g. "photo copied to the TV"). */
    val notices: SharedFlow<String> = _notices
    private var job: Job? = null

    private fun update(f: (Remote) -> Remote) {
        _state.value?.let { old -> val n = f(old); _state.value = n; if (n.phase != old.phase || n.message != old.message) android.util.Log.i("CastSession", "${n.phase} ${n.target.name} ${n.item.name}: ${n.message}") }
    }

    fun start(ctx: Context, target: CastTarget, action: CastAction, item: PlayItem, fallbackPosMs: Long = 0, fallbackDurMs: Long = 0) {
        val app = ctx.applicationContext
        val previous = _state.value
        job?.cancel()
        _state.value = Remote(target, item, action, Remote.Phase.STARTING, message = "Connexion à ${target.name}…")
        job = scope.launch {
            if (previous != null && previous.target != target) runCatching { stopTv(previous) }
            // cast_start / cast_end for a live cast (a copy is counted by UploadService)
            val channel = if (target is CastTarget.Dlna) "dlna" else "wifi"
            val t0 = System.currentTimeMillis()
            if (action == CastAction.LIVE) castbridge.sender.PhoneConnect.track("cast_start", mapOf("channel" to channel, "mode" to "direct"))
            fun ended(ok: Boolean, error: String?) {
                if (action == CastAction.LIVE) castbridge.sender.PhoneConnect.castEnd(channel, "direct", 0, System.currentTimeMillis() - t0, ok, error)
            }
            try {
                val (pos, dur) = phonePosition(item, fallbackPosMs, fallbackDurMs)
                update { it.copy(localDurMs = dur) }
                when (action) {
                    CastAction.LIVE -> live(app, target, item, pos, dur)
                    CastAction.COPY, CastAction.MOVE -> copy(app, target as CastTarget.Box, item, action == CastAction.MOVE, dur, fallbackPosMs)
                }
                ended(true, null)
            } catch (e: CastFailure) {
                ended(false, "refused")
                update { it.copy(phase = Remote.Phase.FAILED, message = e.message) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                ended(false, e.javaClass.simpleName)
                update { it.copy(phase = Remote.Phase.FAILED, message = "Échec : " + castbridge.core.trust.LinkText.failure(e)) }
            }
        }
    }

    private class CastFailure(msg: String) : Exception(msg)

    // ---------- start ----------

    private suspend fun live(ctx: Context, target: CastTarget, item: PlayItem, phonePos: Long, dur: Long) {
        val served = CastPlan.needsPhoneServer(CastAction.LIVE, item.castSource)
        val url = if (served) serve(ctx, item) else item.uri.toString()
        update { it.copy(servedByPhone = served) }
        val start = if (item.kind == MediaKind.IMAGE) 0 else Handoff.phoneToTv(phonePos, dur)
        when (target) {
            is CastTarget.Box -> try {
                TvClient(target.tv.base, target.pin).playUrl(url, item.name, start)
            } catch (e: TvClient.HttpError) {
                throw CastFailure(when {
                    e.code == 404 -> "Cette TV CastBridge est trop ancienne pour la lecture en direct : mettez-la à jour (0.6.5), ou choisissez « Copier sur la TV et lire »."
                    e.code == 401 -> castbridge.core.trust.LinkText.http(401, e.message.orEmpty())
                    e.code == 409 -> TvClient.str(e.message.orEmpty().substringAfter(": "), "message") ?: "La TV doit être au premier plan : ouvrez CastBridge sur la TV."
                    else -> e.message ?: "Refusé par la TV"
                })
            }
            is CastTarget.Dlna -> {
                val mime = item.mime ?: when (item.kind) { MediaKind.AUDIO -> "audio/mpeg"; MediaKind.IMAGE -> "image/jpeg"; else -> "video/mp4" }
                val didl = Didl.item(url, item.name, mime, Didl.protocolInfo(mime, MediaServer.FEATURES))
                Upnp.play(target.renderer, url, didl)
                if (start >= 1000) {
                    // Most renderers refuse Seek until they really play: a few attempts.
                    for (i in 0 until 12) {
                        delay(700)
                        if (runCatching { Upnp.seek(target.renderer, Handoff.toDlnaSeconds(start)) }.isSuccess) break
                    }
                }
            }
        }
        pauseLocal(item)
        update { it.copy(phase = Remote.Phase.PLAYING, message = null, clock = RemoteClock(start, dur, true, now()), lastKnownPos = start) }
        poll()
    }

    /**
     * « Copier sur la TV et lire » / « Déplacer » (R-08): the path is chosen before the first byte ([CopyRoute]): a video that plays on the TV goes in order
     * from byte 0 (visible `.part`), so that `/api/info` shows what arrived and the TV starts as soon as it holds [Handoff.bytesNeeded]; an MP4 indexed at the
     * end, or a photo, takes « Transfert rapide » (the TV waits for the whole file anyway). The phone keeps playing (if it was) until the TV takes over.
     * R-09: the file goes through the transfer queue ([castbridge.sender.TransferQueue]); while another copy runs it is queued (« Ajouté à la file : n° 2 »),
     * never refused, and the hand-off starts once its own copy runs (a « Copier et lire » goes before the plain copies waiting, [castbridge.core.tv.QueueRules]).
     */
    private suspend fun copy(ctx: Context, target: CastTarget.Box, item: PlayItem, move: Boolean, dur0: Long, fallbackPos: Long) {
        val action = if (move) CastAction.MOVE else CastAction.COPY
        // facts known before the first byte: the size and the MP4 layout decide the path
        val size = withContext(Dispatchers.IO) {
            runCatching { ctx.contentResolver.openFileDescriptor(item.uri, "r")!!.use { it.statSize } }.getOrNull()?.takeIf { it > 0 } ?: item.size
        }
        var layout: Mp4Atoms.Layout? = if (size <= 0) null else if (!Mp4Atoms.isIsoName(item.name)) Mp4Atoms.Layout.NOT_ISO
            else withContext(Dispatchers.IO) { mp4LayoutOf(ctx, item.uri, size) }
        val route = CopyRoute.decide(CopyRoute.Facts(action, item.castSource, layout, fastEnabled = castbridge.sender.FastTransfer.enabled(ctx)))
        android.util.Log.i("CastSession", "copy ${item.name}: ${route.transport} (${route.why})")
        val plays = CastPlan.playsOnTv(action, item.castSource)
        UploadService.hintBase(target.tv.name, target.tv.base)       // R-18: the address this screen already reaches the TV at is a source for the upload too
        val ticket = try {
            withContext(Dispatchers.Main) {
                castbridge.sender.TransferQueue.add(ctx, item.uri, item.name, size, move, playOnTv = plays, ordered = route.transport == CopyTransport.ORDERED,
                    tvName = target.tv.name, credential = target.pin)
            }
        } catch (e: castbridge.core.tv.QueueRefused) { throw CastFailure(e.message ?: "Déjà dans la file d'attente") }
        if (!plays) {
            _notices.tryEmit(if (ticket.queued) "${ticket.text} (« ${item.name} »)."
                else if (move) "Déplacement de « ${item.name} » vers ${target.name} : suivez l'envoi dans la notification."
                else "Copie de « ${item.name} » vers ${target.name} : suivez l'envoi dans la notification.")
            _state.value = null
            return
        }
        if (ticket.queued) _notices.tryEmit("${ticket.text}.")
        awaitTurn(ticket.id, item)
        // « Ouvrir avec » starts here without the phone's player: read the duration from the file, else the hand-off can only start from 0 on a byte guess
        val dur = if (dur0 > 0) dur0 else withContext(Dispatchers.IO) { probeDurationMs(ctx, item) }
        if (dur > 0) update { it.copy(localDurMs = dur) }
        val client = TvClient(target.tv.base, target.pin)
        var total = 0L
        val playsAtStart = phonePlays(item)
        update { it.copy(phase = Remote.Phase.COPYING, message = CopyHandoff.line(layout == Mp4Atoms.Layout.MOOV_AT_END, null), phonePlays = playsAtStart,
            copy = castbridge.core.phone.CopyProgress(0, 0, 0, null, moovAtEnd = layout == Mp4Atoms.Layout.MOOV_AT_END)) }
        // the name the TV receives (« Rangement automatique » may have given a clean one), remembered once the upload states stop being this file's
        var sentName = item.name
        var toldHeld = false
        var lastReceived = -1L; var lastGrowthAt = 0L           // R-18: what the TV itself says (bytes received) is the second witness of « the copy goes on »
        while (currentCoroutineContextActive()) {
            var waitingNow = false
            // the upload states describe this file only while the queue's last launch is this file (the next file of the queue may follow it)
            val own = castbridge.sender.TransferQueue.owns(ticket.id)
            val q = castbridge.sender.TransferQueue.item(ticket.id)
            if (q?.status == castbridge.core.tv.QueueStatus.FAILED) throw CastFailure("Échec de l'envoi : ${q.error ?: "envoi interrompu"}")
            if (q?.status == castbridge.core.tv.QueueStatus.CANCELLED) throw CastFailure("Envoi annulé : « ${item.name} » a été retiré de la file d'attente.")
            // R-12: the TV already held the same content (nothing was copied): its own file is the one to play
            q?.heldAs?.let { held ->
                if (q.status == castbridge.core.tv.QueueStatus.DONE) {
                    sentName = held
                    if (!toldHeld) { toldHeld = true; q.note?.let { n -> _notices.tryEmit(n) } }
                }
            }
            val u0 = if (own) UploadService.state.value else null
            when (val u = u0) {
                is UploadService.State.Failed -> throw CastFailure("Échec de l'envoi : ${u.reason}")
                is UploadService.State.Uploading -> { total = u.total; sentName = u.job.fileName }
                is UploadService.State.Waiting -> { total = u.total; sentName = u.job.fileName; waitingNow = true }
                is UploadService.State.Done -> sentName = u.job.fileName
                else -> {}
            }
            val info = runCatching { TvInfo.parse(client.info()) }.getOrNull()
            val f = info?.file(sentName) ?: info?.file(item.name)
            // A small file can be sent completely before the first look: the TV's own size then says it all.
            if (total <= 0 && f != null && f.complete) total = f.size
            if (total > 0 && layout == null)
                layout = if (Mp4Atoms.isIsoName(item.name)) mp4LayoutOf(ctx, item.uri, total) else Mp4Atoms.Layout.NOT_ISO
            val moovAtEnd = layout == Mp4Atoms.Layout.MOOV_AT_END
            // R-18: ONE line (core TransferStatusLine), the same as the notification's: bytes that grew on the TV in the last 10 s, or an upload in progress, mean « copying »
            val nowMs = System.currentTimeMillis()
            if (f != null && f.received > lastReceived) { if (lastReceived >= 0) lastGrowthAt = nowMs; lastReceived = f.received }
            val copying = (own && u0 is UploadService.State.Uploading) || (nowMs - lastGrowthAt <= 10_000 && lastGrowthAt > 0)
            val statusLine = castbridge.core.ux.TransferStatusLine.of(castbridge.core.ux.TransferFacts(
                if (f != null && total > 0) (f.received * 100 / total).toInt() else null, if (own) UploadService.route.value else null,
                copying, UploadService.notice.value == castbridge.core.xfer.PlaybackAwareCopyPolicy.SLOWED_TEXT, waitingNow, null, link = if (own) UploadService.link.value else null))
            val waiting: String? = if (waitingNow || copying && statusLine.level != castbridge.core.ux.SignalLevel.GREEN) statusLine.text else null
            val waitingLevel = if (waiting != null) statusLine.level else null
            if (waiting != null) update { it.copy(message = waiting, messageLevel = waitingLevel) }
            if (f != null && total > 0) {
                val (pos, _) = phonePosition(item, fallbackPos, dur)
                val speed = if (!own) 0L else UploadService.speed.value.takeIf { it > 0 } ?: UploadService.average.value
                val progress = castbridge.core.phone.CopyProgress(f.received, total, speed,
                    Handoff.waitMs(f.received, total, dur, pos, moovAtEnd, speed), moovAtEnd = moovAtEnd)
                val playsHere = phonePlays(item)
                update { it.copy(copy = progress, phonePlays = playsHere, messageLevel = waitingLevel ?: if (copying) castbridge.core.ux.SignalLevel.GREEN else null,
                    message = waiting ?: (CopyHandoff.line(moovAtEnd, progress.fullInMs) + (if (own) UploadService.route.value?.let { " · ${it.label}" }.orEmpty() else ""))) }
                if (Handoff.copyReady(f.received, total, dur, pos, moovAtEnd, if (own) UploadService.speed.value else 0L)) {
                    val start = Handoff.phoneToTv(pos, dur)
                    // f.name: the TV's own name of the file (the clean one it filed it under once complete)
                    val ok = try { client.play(f.name, start); true } catch (e: TvClient.HttpError) {
                        if (e.code == 409 && "needsForeground" in e.message.orEmpty())
                            update { it.copy(message = TvClient.str(e.message.orEmpty().substringAfter(": "), "message") ?: "Ouvrez CastBridge sur la TV") }
                        false
                    }
                    if (ok) {
                        pauseLocal(item)
                        update { it.copy(phase = Remote.Phase.PLAYING, message = null, clock = RemoteClock(start, dur, true, now()), lastKnownPos = start) }
                        poll(); return
                    }
                }
            }
            delay(1000)
        }
    }

    /**
     * Waits for this file's turn in the transfer queue (the copy running before it ends first; it is never interrupted), saying its place;
     * a file removed or failed in the queue ends the cast with its cause. Returns once its own upload was launched (or it was already on the TV).
     */
    private suspend fun awaitTurn(id: Long, item: PlayItem) {
        while (currentCoroutineContextActive()) {
            val q = castbridge.sender.TransferQueue.item(id)
            when {
                castbridge.sender.TransferQueue.owns(id) -> return
                q == null || q.status == castbridge.core.tv.QueueStatus.CANCELLED -> throw CastFailure("Envoi annulé : « ${item.name} » a été retiré de la file d'attente.")
                q.status == castbridge.core.tv.QueueStatus.FAILED -> throw CastFailure("Échec de l'envoi : ${q.error ?: "envoi interrompu"}")
                q.status == castbridge.core.tv.QueueStatus.DONE -> return
            }
            val n = castbridge.sender.TransferQueue.position(id)
            val ahead = castbridge.sender.TransferQueue.runningName()?.takeIf { it != item.name }
            val why = castbridge.sender.TransferQueue.note.value
            update { it.copy(message = why ?: if (n > 1) "En file d'attente : n° $n" + (ahead?.let { a -> " — la copie et la lecture commenceront après « $a »" } ?: "") else "Démarrage de la copie…") }
            delay(1000)
        }
    }

    /** Duration of a local video/audio file read from its own header (0 = unknown). */
    private fun probeDurationMs(ctx: Context, item: PlayItem): Long {
        if (item.kind != MediaKind.VIDEO && item.kind != MediaKind.AUDIO) return 0
        val r = android.media.MediaMetadataRetriever()
        return try {
            r.setDataSource(ctx, item.uri)
            r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
        } catch (e: Exception) { 0 } finally { runCatching { r.release() } }
    }

    /** The phone's own player is playing [item] right now. */
    private suspend fun phonePlays(item: PlayItem): Boolean = withContext(Dispatchers.Main) {
        PlaybackService.player.value?.let { it.currentMediaItem?.mediaId == item.uri.toString() && it.isPlaying } == true
    }

    private suspend fun currentCoroutineContextActive() = kotlin.coroutines.coroutineContext.isActive

    /** The phone's own little HTTP server (the same as the "TV DLNA" tab) serves the file to the TV. */
    private suspend fun serve(ctx: Context, item: PlayItem): String {
        val ip = Upnp.localIp() ?: throw CastFailure("Pas d'adresse Wi-Fi : le téléphone et la TV doivent être sur le même réseau.")
        withContext(Dispatchers.Main) { runCatching { ctx.startForegroundService(Intent(ctx, ServerService::class.java)) } }
        var srv = ServerService.server
        repeat(30) { if (srv == null) { delay(100); srv = ServerService.server } }
        val s = srv ?: throw CastFailure("Le serveur du téléphone n'a pas démarré.")
        val mime = item.mime ?: "video/mp4"
        val ext = item.name.substringAfterLast('.', "bin").take(5)
        return "http://$ip:8089/media/${s.register(item.uri, mime)}.$ext"
    }

    // ---------- follow ----------

    private suspend fun poll() {
        var n = 0
        var sawPlaying = false
        while (currentCoroutineContextActive()) {
            val r = _state.value ?: return
            if (r.phase != Remote.Phase.PLAYING) { delay(1000); continue }
            try {
                when (val t = r.target) {
                    is CastTarget.Box -> {
                        val c = TvClient(t.tv.base, t.pin)
                        val i = TvInfo.parse(c.info())
                        val mine = i.playing == null || i.playing == r.item.name
                        val playing = i.state == "playing" || i.state == "buffering"
                        if (playing) sawPlaying = true
                        when {
                            !mine -> update { it.copy(phase = Remote.Phase.ENDED, message = "La TV lit maintenant autre chose.") }
                            (i.state == "idle" || i.state == "ended" || i.state == "error") && (sawPlaying || n > 8) -> update {
                                it.copy(phase = Remote.Phase.ENDED, clock = it.clock.copy(playing = false),
                                    message = when (i.state) { "ended" -> "Lecture terminée sur ${t.name}."; "error" -> "La TV n'a pas pu lire ce fichier."; else -> "Lecture arrêtée sur ${t.name}." })
                            }
                            else -> update {
                                val pos = if (i.pos > 0 || playing) i.pos else it.clock.posMs
                                it.copy(clock = RemoteClock(pos, if (i.dur > 0) i.dur else it.clock.durMs, playing, now()),
                                    lastKnownPos = if (i.pos > 0) i.pos else it.lastKnownPos, message = if (i.state == "buffering") "Mise en mémoire tampon sur la TV…" else null)
                            }
                        }
                        if (n % 5 == 0) runCatching { TvClient.num(c.sysinfo(), "volume")?.toInt() }.getOrNull()?.let { v -> update { it.copy(volume = v) } }
                    }
                    is CastTarget.Dlna -> {
                        val st = runCatching { Upnp.transportState(t.renderer) }.getOrNull()
                        val (p, d) = Upnp.position(t.renderer)
                        val playing = st == null || st == "PLAYING" || st == "TRANSITIONING"
                        if (st == "PLAYING") sawPlaying = true
                        if ((st == "STOPPED" || st == "NO_MEDIA_PRESENT") && (sawPlaying || n > 8))
                            update { it.copy(phase = Remote.Phase.ENDED, clock = it.clock.copy(playing = false), message = "Lecture arrêtée sur ${t.name}.") }
                        else update {
                            val pos = p * 1000
                            it.copy(clock = RemoteClock(pos, if (d > 0) d * 1000 else it.clock.durMs, playing, now()),
                                lastKnownPos = if (pos > 0) pos else it.lastKnownPos, message = null)
                        }
                    }
                }
            } catch (e: TvClient.HttpError) {
                if (e.code == 401) { update { it.copy(phase = Remote.Phase.FAILED, message = "PIN refusé par la TV.") }; return }
            } catch (e: IOException) {
                update { it.copy(message = "TV injoignable, nouvel essai…") }
            }
            n++
            delay(1000)
        }
    }

    // ---------- remote control ----------

    fun toggle() {
        val r = _state.value ?: return
        val play = !r.clock.playing
        update { it.copy(clock = RemoteClock(it.clock.now(now()), it.clock.durMs, play, now())) }
        scope.launch {
            runCatching {
                when (val t = r.target) {
                    is CastTarget.Box -> TvClient(t.tv.base, t.pin).let { if (play) it.resume() else it.pause() }
                    is CastTarget.Dlna -> if (play) Upnp.resume(t.renderer) else Upnp.pause(t.renderer)
                }
            }.onFailure { e -> update { it.copy(message = "Commande refusée : ${e.message}") } }
        }
    }

    fun seekTo(ms: Long) {
        val r = _state.value ?: return
        val target = if (r.clock.durMs > 0) ms.coerceIn(0, r.clock.durMs) else ms.coerceAtLeast(0)
        update { it.copy(clock = it.clock.copy(posMs = target, atMs = now())) }
        scope.launch {
            runCatching {
                when (val t = r.target) {
                    is CastTarget.Box -> TvClient(t.tv.base, t.pin).seek(target)
                    is CastTarget.Dlna -> Upnp.seek(t.renderer, Handoff.toDlnaSeconds(target))
                }
            }.onFailure { e -> update { it.copy(message = "Déplacement refusé : ${e.message}") } }
        }
    }

    fun skip(deltaMs: Long) { _state.value?.let { seekTo(it.clock.now(now()) + deltaMs) } }

    fun setVolume(pct: Int) {
        val r = _state.value ?: return
        val t = r.target as? CastTarget.Box ?: return
        update { it.copy(volume = pct) }
        scope.launch { runCatching { TvClient(t.tv.base, t.pin).setVolume(pct.coerceIn(0, 100)) } }
    }

    /** Stops playback on the TV and ends the session. */
    fun stop(ctx: Context) {
        val r = _state.value ?: return
        job?.cancel(); _state.value = null
        scope.launch { runCatching { stopTv(r) }; if (r.servedByPhone) stopServer(ctx) }
    }

    /** Forget a finished or failed session (nothing is sent to the TV). */
    fun dismiss(ctx: Context) {
        val r = _state.value ?: return
        job?.cancel(); _state.value = null
        if (r.servedByPhone) scope.launch { stopServer(ctx) }
    }

    /**
     * "Revenir sur le téléphone": stop the TV, then play here from where the TV was. If the phone no longer has the file
     * (moved), the TV's own copy is streamed back instead.
     */
    fun backToPhone(ctx: Context) {
        val r = _state.value ?: return
        job?.cancel(); _state.value = null
        val app = ctx.applicationContext
        scope.launch {
            val tvPos = if (r.phase == Remote.Phase.PLAYING) r.clock.now(now()) else 0
            runCatching { stopTv(r) }
            if (r.servedByPhone) stopServer(app)
            val pos = Handoff.tvToPhone(tvPos, r.lastKnownPos, r.localDurMs)
            val readable = isReadable(app, r.item.uri)
            withContext(Dispatchers.Main) {
                val p = PlaybackService.player.value
                if (readable && p != null && p.currentMediaItem?.mediaId == r.item.uri.toString()) {
                    p.seekTo(pos); p.play()
                    return@withContext
                }
                val i = Intent(app, PlayerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(Media.EXTRA_POS, pos)
                val t = r.target
                when {
                    readable -> i.setAction(Intent.ACTION_VIEW).setDataAndType(r.item.uri, r.item.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    t is CastTarget.Box && r.action != CastAction.LIVE -> {
                        TvStreamAuth.pins[t.tv.host] = t.pin
                        i.setAction(Intent.ACTION_VIEW).setData(android.net.Uri.parse("${t.tv.base}/stream/${TvClient.enc(r.item.name)}"))
                            .putExtra(Media.EXTRA_TITLE, r.item.name).putExtra(PlayerActivity.EXTRA_FROM_TV, true)
                    }
                    else -> { _notices.tryEmit("« ${r.item.name} » n'est plus lisible sur le téléphone."); return@withContext }
                }
                app.startActivity(i)
            }
        }
    }

    private suspend fun stopTv(r: Remote) {
        when (val t = r.target) {
            is CastTarget.Box -> TvClient(t.tv.base, t.pin).stop()
            is CastTarget.Dlna -> Upnp.stop(t.renderer)
        }
    }

    private suspend fun stopServer(ctx: Context) = withContext(Dispatchers.Main) {
        runCatching { ctx.stopService(Intent(ctx, ServerService::class.java)) }
    }

    // ---------- the phone's own player ----------

    /** Position/duration of [item] in the phone's player (if it is the one loaded there), else the fallback. */
    private suspend fun phonePosition(item: PlayItem, fallbackPos: Long, fallbackDur: Long): Pair<Long, Long> = withContext(Dispatchers.Main) {
        val p = PlaybackService.player.value
        if (p != null && p.currentMediaItem?.mediaId == item.uri.toString())
            p.currentPosition to (p.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: fallbackDur)
        else fallbackPos to fallbackDur
    }

    private suspend fun pauseLocal(item: PlayItem) = withContext(Dispatchers.Main) {
        PlaybackService.player.value?.let { if (it.currentMediaItem?.mediaId == item.uri.toString()) it.pause() }
    }

    private fun now() = System.currentTimeMillis()
}
