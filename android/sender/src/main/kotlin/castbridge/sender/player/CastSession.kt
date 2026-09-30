package castbridge.sender.player

import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import androidx.media3.common.C
import castbridge.core.phone.CastAction
import castbridge.core.phone.CastPlan
import castbridge.core.phone.Handoff
import castbridge.core.phone.MediaKind
import castbridge.core.phone.RemoteClock
import castbridge.core.remote.RemoteBt
import castbridge.core.remote.RemoteReply
import castbridge.core.tv.BtProtocol
import castbridge.core.tv.Mp4Atoms
import castbridge.core.tv.ResumableUpload
import castbridge.core.tv.TvClient
import castbridge.core.tv.TvInfo
import castbridge.core.upnp.Didl
import castbridge.sender.BtUploadService
import castbridge.sender.MediaServer
import castbridge.sender.Renderer
import castbridge.sender.ServerService
import castbridge.sender.Tv
import castbridge.sender.UploadService
import castbridge.sender.Upnp
import castbridge.sender.hasBtPermission
import castbridge.sender.mp4LayoutOf
import java.util.UUID
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
    /** A paired Bluetooth TV (no common Wi-Fi): the file is sent by Bluetooth, then the TV plays it. */
    data class Bt(val address: String, val pin: String, private val btName: String) : CastTarget() { override val name get() = btName }
}

/** Parsed GET /api/remote/player answer (the TV's player state over Bluetooth). */
private data class BtPlayerState(val state: String, val name: String?, val pos: Long, val dur: Long) {
    companion object {
        fun parse(j: String): BtPlayerState? = runCatching {
            val o = castbridge.core.quiz.Json.obj(j)
            fun num(k: String): Long = when (val v = o[k]) { is Long -> v; is Double -> v.toLong(); else -> 0 }
            BtPlayerState(o["state"] as? String ?: "idle", o["name"] as? String, num("pos"), num("dur"))
        }.getOrNull()
    }
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
    val servedByPhone: Boolean = false,
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

    // ---- Bluetooth control link (CBTR) kept open for the length of a Bluetooth cast ----
    @Volatile private var appContext: Context? = null
    @Volatile private var btLink: RemoteBt.Transport? = null
    @Volatile private var btAddr: String? = null
    private val btLock = Any()

    private fun update(f: (Remote) -> Remote) {
        _state.value?.let { old -> val n = f(old); _state.value = n; if (n.phase != old.phase || n.message != old.message) android.util.Log.i("CastSession", "${n.phase} ${n.target.name} ${n.item.name}: ${n.message}") }
    }

    fun start(ctx: Context, target: CastTarget, action: CastAction, item: PlayItem, fallbackPosMs: Long = 0, fallbackDurMs: Long = 0, dest: String? = null) {
        val app = ctx.applicationContext
        appContext = app
        val previous = _state.value
        job?.cancel()
        _state.value = Remote(target, item, action, Remote.Phase.STARTING, message = "Connexion à ${target.name}…")
        job = scope.launch {
            if (previous != null && previous.target != target) runCatching { stopTv(previous) }
            try {
                val (pos, dur) = phonePosition(item, fallbackPosMs, fallbackDurMs)
                update { it.copy(localDurMs = dur) }
                when (action) {
                    CastAction.LIVE -> live(app, target, item, pos, dur)
                    CastAction.COPY, CastAction.MOVE -> when (target) {
                        is CastTarget.Box -> copy(app, target, item, action == CastAction.MOVE, dur, fallbackPosMs, dest)
                        is CastTarget.Bt -> copyBt(app, target, item, action == CastAction.MOVE, dur, fallbackPosMs)
                        is CastTarget.Dlna -> throw CastFailure("Copie impossible vers une TV DLNA (lecture en direct seulement).")
                    }
                }
            } catch (e: CastFailure) {
                update { it.copy(phase = Remote.Phase.FAILED, message = e.message) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                update { it.copy(phase = Remote.Phase.FAILED, message = "Échec : ${e.message ?: e.javaClass.simpleName}") }
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
                    e.code == 401 -> "PIN refusé par la TV."
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
            is CastTarget.Bt -> throw CastFailure("Lecture en direct impossible par Bluetooth : choisissez « ${CastAction.COPY.label} ».")
        }
        pauseLocal(item)
        update { it.copy(phase = Remote.Phase.PLAYING, message = null, clock = RemoteClock(start, dur, true, now()), lastKnownPos = start) }
        poll(ctx)
    }

    private suspend fun copy(ctx: Context, target: CastTarget.Box, item: PlayItem, move: Boolean, dur: Long, fallbackPos: Long, dest: String?) {
        val up = UploadService.state.value
        if (up is UploadService.State.Uploading || up is UploadService.State.Waiting)
            throw CastFailure("Un envoi vers la TV est déjà en cours : attendez qu'il se termine.")
        withContext(Dispatchers.Main) {
            // Wi-Fi Direct has a fixed address (192.168.49.1): pin the upload to it instead of mDNS, which may not
            // find the TV over that interface. A regular TV keeps mDNS re-resolution for its (possibly changing) IP.
            val manualHost = target.tv.host.takeIf { it == castbridge.core.tv.WifiDirect.GROUP_OWNER_IP }
            UploadService.start(ctx, item.uri, item.name, target.tv.name, manualHost, target.pin, progressive = false, autoPlay = false, target = dest, move = move)
        }
        if (!CastPlan.playsOnTv(if (move) CastAction.MOVE else CastAction.COPY, item.castSource)) {
            _notices.tryEmit(if (move) "Déplacement de « ${item.name} » vers ${target.name} : suivez l'envoi dans la notification."
                else "Copie de « ${item.name} » vers ${target.name} : suivez l'envoi dans la notification.")
            _state.value = null
            return
        }
        val client = TvClient(target.tv.base, target.pin)
        var total = 0L
        var layout: Mp4Atoms.Layout? = null
        update { it.copy(phase = Remote.Phase.COPYING, message = "Copie vers ${target.name}… La lecture continue ici en attendant.") }
        while (currentCoroutineContextActive()) {
            when (val u = UploadService.state.value) {
                is UploadService.State.Failed -> throw CastFailure("Échec de l'envoi : ${u.reason}")
                is UploadService.State.Uploading -> total = u.total
                is UploadService.State.Waiting -> { total = u.total; update { it.copy(message = "En attente du réseau : ${u.reason}") } }
                else -> {}
            }
            if (total > 0 && layout == null)
                layout = if (Mp4Atoms.isIsoName(item.name)) mp4LayoutOf(ctx, item.uri, total) else Mp4Atoms.Layout.NOT_ISO
            val info = runCatching { TvInfo.parse(client.info()) }.getOrNull()
            val f = info?.file(item.name)
            // A small file can be sent completely before the first look: the TV's own size then says it all.
            if (total <= 0 && f != null && f.complete) total = f.size
            if (total > 0 && layout == null)
                layout = if (Mp4Atoms.isIsoName(item.name)) mp4LayoutOf(ctx, item.uri, total) else Mp4Atoms.Layout.NOT_ISO
            if (f != null && total > 0) {
                val (pos, _) = phonePosition(item, fallbackPos, dur)
                val moovAtEnd = layout == Mp4Atoms.Layout.MOOV_AT_END
                val pct = f.received * 100 / total
                update { it.copy(message = if (moovAtEnd) "Copie vers ${it.target.name} : $pct %. Ce MP4 doit être copié en entier avant de passer sur la TV ; la lecture continue ici."
                    else "Copie vers ${it.target.name} : $pct %. La TV prendra le relais dès qu'elle aura assez d'avance ; la lecture continue ici.") }
                if (Handoff.copyReady(f.received, total, dur, pos, moovAtEnd, UploadService.speed.value)) {
                    val start = Handoff.phoneToTv(pos, dur)
                    val ok = try { client.play(item.name, start); true } catch (e: TvClient.HttpError) {
                        if (e.code == 409 && "needsForeground" in e.message.orEmpty())
                            update { it.copy(message = TvClient.str(e.message.orEmpty().substringAfter(": "), "message") ?: "Ouvrez CastBridge sur la TV") }
                        false
                    }
                    if (ok) {
                        pauseLocal(item)
                        update { it.copy(phase = Remote.Phase.PLAYING, message = null, clock = RemoteClock(start, dur, true, now()), lastKnownPos = start) }
                        poll(ctx); return
                    }
                }
            }
            delay(1000)
        }
    }

    private suspend fun currentCoroutineContextActive() = kotlin.coroutines.coroutineContext.isActive

    /**
     * "Cast" over Bluetooth: send the file to the paired TV (Bluetooth first, upgraded to Wi-Fi / Wi-Fi Direct when possible),
     * then tell it to play over the Bluetooth control link and become its remote control. COPY or MOVE (MOVE verifies the
     * TV's complete copy before the phone deletes it). LIVE needs a Wi-Fi stream and is not offered here.
     */
    private suspend fun copyBt(ctx: Context, target: CastTarget.Bt, item: PlayItem, move: Boolean, dur: Long, fallbackPos: Long) {
        val st = BtUploadService.state.value
        if (st is ResumableUpload.State.Uploading || st is ResumableUpload.State.Waiting)
            throw CastFailure("Un envoi Bluetooth est déjà en cours : attendez qu'il se termine.")
        withContext(Dispatchers.Main) { BtUploadService.start(ctx, item.uri, item.name, target.address, target.pin) }
        val action = if (move) CastAction.MOVE else CastAction.COPY
        if (!CastPlan.playsOnTv(action, item.castSource)) {
            _notices.tryEmit(if (move) "Déplacement de « ${item.name} » vers ${target.name} : suivez l'envoi dans la notification."
                else "Copie de « ${item.name} » vers ${target.name} : suivez l'envoi dans la notification.")
            _state.value = null
            return
        }
        update { it.copy(phase = Remote.Phase.COPYING, message = "Envoi vers ${target.name} (Bluetooth)… La lecture continue ici en attendant.") }
        while (currentCoroutineContextActive()) {
            when (val u = BtUploadService.state.value) {
                is ResumableUpload.State.Failed -> throw CastFailure("Échec de l'envoi : ${u.reason}")
                is ResumableUpload.State.Uploading ->
                    update { it.copy(message = "Envoi vers ${it.target.name} : ${u.sent * 100 / u.total} % (${BtUploadService.route.value ?: "Bluetooth"})…") }
                is ResumableUpload.State.Waiting -> update { it.copy(message = "En attente du lien Bluetooth : ${u.reason}") }
                ResumableUpload.State.Done -> break
                null -> {}
            }
            delay(1000)
        }
        if (move) verifyMove(ctx, target, item)
        val (pos, _) = phonePosition(item, fallbackPos, dur)
        val start = Handoff.phoneToTv(pos, dur)
        val r = btSend(ctx, target, "POST", "play", "name=${TvClient.enc(item.name)}&pos=$start")
        if (r.status !in 200..299) throw CastFailure(castbridge.core.remote.RemoteSession.message(r))
        pauseLocal(item)
        update { it.copy(phase = Remote.Phase.PLAYING, message = null, clock = RemoteClock(start, dur, true, now()), lastKnownPos = start) }
        poll(ctx)
    }

    /** Move over Bluetooth: never delete on faith — the TV must confirm a complete copy of exactly the local size. */
    private suspend fun verifyMove(ctx: Context, target: CastTarget.Bt, item: PlayItem) {
        val local = localSize(ctx, item.uri)
        val reply = btSend(ctx, target, "GET", "file", "name=${TvClient.enc(item.name)}")
        val tvSize = runCatching {
            val o = castbridge.core.quiz.Json.obj(reply.body)
            when (val v = o["size"]) { is Long -> v; is Double -> v.toLong(); else -> -1L }
        }.getOrDefault(-1L)
        if (local > 0 && tvSize == local) UploadService.offerMove(UploadService.MoveRequest(item.uri, item.name, local))
        else UploadService.noteMove("« ${item.name} » est envoyé mais la TV n'a pas confirmé une copie complète : il reste sur le téléphone.")
    }

    private fun localSize(ctx: Context, uri: android.net.Uri): Long = runCatching {
        ctx.contentResolver.openFileDescriptor(uri, "r")!!.use { it.statSize }
    }.getOrDefault(-1)


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

    private suspend fun poll(ctx: Context) {
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
                    is CastTarget.Bt -> {
                        val reply = btSend(ctx, t, "GET", "player", "")
                        if (reply.status == 401) { update { it.copy(phase = Remote.Phase.FAILED, message = "PIN refusé par la TV.") }; return }
                        val st = BtPlayerState.parse(reply.body)
                        if (st != null) {
                            val mine = st.name == null || st.name == r.item.name
                            val playing = st.state == "playing" || st.state == "buffering"
                            if (playing) sawPlaying = true
                            when {
                                !mine -> update { it.copy(phase = Remote.Phase.ENDED, message = "La TV lit maintenant autre chose.") }
                                (st.state == "idle" || st.state == "ended" || st.state == "error") && (sawPlaying || n > 8) -> update {
                                    it.copy(phase = Remote.Phase.ENDED, clock = it.clock.copy(playing = false),
                                        message = when (st.state) { "ended" -> "Lecture terminée sur ${t.name}."; "error" -> "La TV n'a pas pu lire ce fichier."; else -> "Lecture arrêtée sur ${t.name}." })
                                }
                                else -> update {
                                    val pos = if (st.pos > 0 || playing) st.pos else it.clock.posMs
                                    it.copy(clock = RemoteClock(pos, if (st.dur > 0) st.dur else it.clock.durMs, playing, now()),
                                        lastKnownPos = if (st.pos > 0) st.pos else it.lastKnownPos, message = if (st.state == "buffering") "Mise en mémoire tampon sur la TV…" else null)
                                }
                            }
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

    // ---------- Bluetooth control link ----------

    private fun btOpen(ctx: Context, t: CastTarget.Bt): RemoteBt.Transport {
        if (!hasBtPermission(ctx)) throw CastFailure("Autorisation Bluetooth « Appareils à proximité » refusée.")
        val adapter = ctx.getSystemService(BluetoothManager::class.java)?.adapter
            ?: throw CastFailure("Ce téléphone n'a pas de Bluetooth.")
        if (!adapter.isEnabled) throw CastFailure("Le Bluetooth du téléphone est éteint.")
        runCatching { adapter.cancelDiscovery() }
        val sock = adapter.getRemoteDevice(t.address).createRfcommSocketToServiceRecord(UUID.fromString(BtProtocol.SERVICE_UUID))
        try {
            sock.connect()
            RemoteBt.handshake(sock.inputStream, sock.outputStream, t.pin)
            return RemoteBt.Transport(sock.inputStream, sock.outputStream, sock)
        } catch (e: BtProtocol.Refused) {
            runCatching { sock.close() }
            throw CastFailure("Bluetooth : ${e.message}" + if (e.code == BtProtocol.ERR_MAGIC) " (mettez à jour CastBridge TV)" else "")
        } catch (e: IOException) {
            runCatching { sock.close() }
            throw CastFailure("TV injoignable en Bluetooth (appairée ? CastBridge TV ouverte ?)")
        }
    }

    /** One request on the Bluetooth control link, reconnecting once if the link dropped or targets another TV. */
    private fun btSend(ctx: Context, t: CastTarget.Bt, method: String, route: String, query: String): RemoteReply = synchronized(btLock) {
        if (btLink == null || btAddr != t.address) {
            runCatching { btLink?.close() }; btLink = btOpen(ctx, t); btAddr = t.address
        }
        try {
            btLink!!.send(method, route, query)
        } catch (e: IOException) {
            runCatching { btLink?.close() }; btLink = btOpen(ctx, t); btAddr = t.address
            btLink!!.send(method, route, query)
        }
    }

    private fun closeBt() = synchronized(btLock) { runCatching { btLink?.close() }; btLink = null; btAddr = null }

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
                    is CastTarget.Bt -> btSend(appContext ?: return@launch, t, "POST", if (play) "resume" else "pause", "")
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
                    is CastTarget.Bt -> btSend(appContext ?: return@launch, t, "POST", "seek", "pos=$target")
                }
            }.onFailure { e -> update { it.copy(message = "Déplacement refusé : ${e.message}") } }
        }
    }

    fun skip(deltaMs: Long) { _state.value?.let { seekTo(it.clock.now(now()) + deltaMs) } }

    fun setVolume(pct: Int) {
        val r = _state.value ?: return
        update { it.copy(volume = pct) }
        val p = pct.coerceIn(0, 100)
        scope.launch {
            runCatching {
                when (val t = r.target) {
                    is CastTarget.Box -> TvClient(t.tv.base, t.pin).setVolume(p)
                    is CastTarget.Bt -> btSend(appContext ?: return@launch, t, "POST", "volume", "pct=$p")
                    is CastTarget.Dlna -> {}
                }
            }.onFailure { e -> update { it.copy(message = "Volume refusé : ${e.message}") } }
        }
    }

    /** Stops playback on the TV and ends the session. */
    fun stop(ctx: Context) {
        val r = _state.value ?: return
        job?.cancel(); _state.value = null
        closeBt()
        scope.launch { runCatching { stopTv(r) }; if (r.servedByPhone) stopServer(ctx) }
    }

    /** Forget a finished or failed session (nothing is sent to the TV). */
    fun dismiss(ctx: Context) {
        val r = _state.value ?: return
        job?.cancel(); _state.value = null
        closeBt()
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
            closeBt()
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
            is CastTarget.Bt -> btSend(appContext ?: return, t, "POST", "stop", "")
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
