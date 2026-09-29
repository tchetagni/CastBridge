package castbridge.core.tv

import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.IOException
import java.io.InputStream

/** Playback side of the receiver, implemented with libVLC on the TV. Called from HTTP threads. */
interface Player {
    fun play(file: File, posMs: Long)
    /** Plays [name] from [url] (the server's own /stream/ route) while its upload is still in progress. */
    fun playStream(url: String, name: String, posMs: Long)
    /** Plays a finished file that lives in a folder chosen through the system picker (no java.io.File path). */
    fun playSaf(name: String, size: Long, posMs: Long): Unit = throw UnsupportedOperationException("SAF playback not supported")
    fun pause()
    fun resume()
    fun seek(posMs: Long)
    fun stop()
    fun state(): PlayerState
    /** Tracks, delays, chapters... of what is playing; null when nothing plays or the player cannot tell. */
    fun tracks(): PlayerTracks? = null
    /** Applies a setting (audio track, subtitles, delays, speed...); false = not possible now (nothing playing, no such track). */
    fun command(c: PlayerCommand): Boolean = false
    /** Plays an external subtitle file (a real file next to the video) with the current video. */
    fun subtitleFile(file: File): Boolean = false
}

data class PlayerState(val state: String = "idle", val name: String? = null, val posMs: Long = 0, val durMs: Long = 0)

/**
 * CastBridge TV HTTP API (port 8765). Videos are uploaded whole, resumably, into one of the storage volumes of [volumes]
 * (internal storage, a USB drive's app folder, a folder chosen through the system picker) so playback no longer depends on
 * the phone or the network. See docs/NEXT-preload.md and docs/STORAGE.md.
 */
class ReceiverServer(
    private val volumes: VolumeRegistry,
    private val player: Player,
    port: Int = PORT,
    profile: TvProfile = TvProfile(),
    /** Called when settings change through the API, so the app can persist them. */
    private val onSettings: (TvProfile) -> Unit = {},
    /** 6-digit PIN required (header X-CB-Pin or ?pin=) on every route but GET / and GET /api/hello. null = open (tests). */
    pin: String? = null,
    private val guard: PinGuard? = pin?.let { PinGuard(it) },
    private val device: Device? = null,
    /** Extra authenticated routes (SSH, USB import, ...). Return null if the route is not handled. */
    private val extension: ApiExtension? = null,
    /** One-line messages for the TV screen (drive removed / back, ...). */
    private val onNotice: (String) -> Unit = {},
    /** Opens the system folder picker on the TV; returns a message for the user (why it did not open, or what to do). */
    private val safPicker: (() -> String)? = null,
    /** Opens the TV's own storage settings; null = opened, else why not. */
    private val settingsOpener: (() -> String?)? = null,
    /** Thumbnails, durations and saved positions for the library screens; null = plain file list. */
    private val library: LibraryMeta? = null,
) : NanoHTTPD(port) {

    /** Single internal folder (tests, simple setups). */
    constructor(
        dir: File, player: Player, port: Int = PORT, profile: TvProfile = TvProfile(),
        onSettings: (TvProfile) -> Unit = {}, pin: String? = null, guard: PinGuard? = pin?.let { PinGuard(it) },
        device: Device? = null, extension: ApiExtension? = null,
    ) : this(VolumeRegistry.single(dir), player, port, profile, onSettings, pin, guard, device, extension)

    @Volatile private var cfg = profile
    /** Profile for phone <-> TV transfers: the "free space after the transfer" rule instead of the plain reserve. */
    private val xferCfg: TvProfile get() = cfg.let { it.copy(minFreeBytes = TransferRule.minFree(it)) }
    /** Random per run: lets the TV's own player read /stream/ on loopback without the PIN (never leaves the process). */
    private val streamToken: String = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
    private val meters = java.util.concurrent.ConcurrentHashMap<String, RateMeter>()
    @Volatile private var moveJob: MoveJob? = null
    private val uploading = java.util.concurrent.atomic.AtomicInteger()

    /** Uploads being received right now (the TV app keeps a partial wake lock only while this is above zero). */
    fun activeTransfers(): Int = uploading.get() + (if (moveJob?.state == "running") 1 else 0)
    @Volatile private var playingVolume: String? = null
    /** "Play one after the other" (library section, selection); null = single file. */
    @Volatile private var playlist: Playlist? = null

    fun streamUrl(name: String) = "http://127.0.0.1:$listeningPort/stream/${java.net.URLEncoder.encode(name, "UTF-8").replace("+", "%20")}?t=$streamToken"

    /** The volumes this server uses (the app asks it to re-scan after a mount/unmount broadcast). */
    fun storageChanged(remeasure: Boolean = false) { volumes.refresh(remeasure); invalidate() }

    // ---- locating files across volumes ----

    private class Hit(val v: StorageVolume, val st: VolumeStore, val name: String, val size: Long) {
        val file: File? get() = (st as? FileStore)?.let { File(it.dir, it.diskName(name)) }
    }

    private fun finals(name: String): List<Hit> = volumes.volumes().mapNotNull { v ->
        val st = volumes.store(v); val n = v.storedName(name)
        st.finalSize(n)?.let { Hit(v, st, n, it) }
    }
    private fun findFinal(name: String): Hit? = finals(name).firstOrNull()
    /** The largest partial copy of [name] (there should be one; if a returned drive brings a second, the listing says so). */
    private fun findPart(name: String): Hit? = volumes.volumes().mapNotNull { v ->
        val st = volumes.store(v); val n = v.storedName(name)
        st.partSize(n).takeIf { it > 0 }?.let { Hit(v, st, n, it) }
    }.maxByOrNull { it.size }

    private fun isPlaying(name: String): Boolean {
        val ps = player.state()
        val n = ps.name ?: return false
        return ps.state != "idle" && (n == name || volumes.volumes().any { it.storedName(name) == n })
    }
    private fun moving(name: String) = moveJob?.let { it.state == "running" && it.name == name } == true

    /** (received, total) for a file being uploaded or complete; null if unknown. */
    fun progress(name: String): Pair<Long, Long>? {
        findFinal(name)?.let { return it.size to it.size }
        val p = findPart(name) ?: return null
        val total = (p.st as? FileStore)?.let { Meta.read(it.dir, p.name) } ?: return null
        return p.size to total
    }

    init {
        volumes.volumes().forEach { cleanOrphans(it) }
        volumes.addListener { ev ->
            invalidate()
            if (!ev.present) {
                if (playingVolume == ev.volume.id && player.state().state != "idle") {
                    runCatching { player.stop() }
                    onNotice("${ev.volume.label} retiré : lecture arrêtée")
                } else onNotice("${ev.volume.label} retirée : les envois en cours reprendront à son retour")
            } else {
                cleanOrphans(ev.volume)
                val free = runCatching { volumes.free(ev.volume) }.getOrDefault(-1)
                onNotice("${ev.volume.label} branchée" + (if (free >= 0) " : ${StorageLine.size(free)} libres" else ""))
            }
        }
        setAsyncRunner(BoundedRunner(cfg.maxHttpThreads))
    }

    /** Abandoned partial uploads must not eat scarce space; a drive that was away keeps them 7 times longer. */
    private fun cleanOrphans(v: StorageVolume) {
        val st = volumes.store(v) as? FileStore ?: return
        val age = if (v.kind == VolumeKind.REMOVABLE) cfg.orphanPartMaxAgeMs * 7 else cfg.orphanPartMaxAgeMs
        runCatching { Storage.cleanOrphans(st.dir, age) }
    }

    /**
     * Called by the app when a file played to the end: honours the "delete after play" setting, then starts the next item of
     * the playlist if there is one. Returns true when something new started playing (the app then keeps the video screen).
     */
    fun onPlaybackEnded(name: String): Boolean {
        deleteAfterPlay(name)
        val pl = playlist ?: return false
        if (pl.current != name && !pl.contains(name)) { playlist = null; return false }
        val next = pl.next(auto = true) ?: run { playlist = null; return false }
        return runCatching { startPlaying(next, 0) }.getOrDefault(false)
    }

    /** Plays [name] (finished file, or one still arriving) from [pos]; false if it does not exist. */
    private fun startPlaying(name: String, pos: Long): Boolean {
        val hit = findFinal(name) ?: return false
        playingVolume = hit.v.id
        val f = hit.file
        if (f != null) { Storage.markPlayed(f.parentFile, f.name); player.play(f, pos) } else player.playSaf(hit.name, hit.size, pos)
        return true
    }

    /** Next / previous item of the playlist (remote keys, phone); false if there is none. */
    fun playlistStep(delta: Int): Boolean {
        val pl = playlist ?: return false
        val n = (if (delta >= 0) pl.next(auto = false) else pl.previous()) ?: return false
        return startPlaying(n, 0)
    }

    /** Starts "one after the other" over [names] (stored files) from [start]. The TV's library uses it for a section. */
    fun playAll(names: List<String>, start: Int = 0, repeat: Playlist.Repeat = Playlist.Repeat.OFF): Boolean {
        val ok = names.mapNotNull { safeName(it) }.filter { findFinal(it) != null }.distinct()
        if (ok.isEmpty()) return false
        val first = names.getOrNull(start)?.let { ok.indexOf(it) }?.takeIf { it >= 0 } ?: 0
        val pl = Playlist(ok, first, repeat)
        playlist = pl
        return startPlaying(pl.current!!, 0)
    }

    /** Repeat mode of the running playlist; false when there is none. */
    fun setRepeat(r: Playlist.Repeat): Boolean { val pl = playlist ?: return false; pl.repeat = r; return true }

    private fun playlistJson(): String {
        val pl = playlist ?: return "null"
        return """{"items":${strs(pl.items)},"index":${pl.index},"repeat":${q(pl.repeat.label)}}"""
    }

    private fun deleteAfterPlay(name: String) {
        if (!cfg.deleteAfterPlay) return
        val n = safeName(name) ?: return
        val hits = finals(n)
        val h = hits.firstOrNull { it.v.id == playingVolume } ?: hits.firstOrNull() ?: return
        synchronized(FileLocks.of(LOCK_ROOT, n)) {
            h.st.deleteFinal(h.name)
            (h.st as? FileStore)?.let { Storage.forget(it.dir, h.name) }
            invalidate()
        }
    }

    override fun stop() {
        moveJob?.cancelled = true
        super.stop()
    }

    override fun serve(s: IHTTPSession): Response = try {
        route(s)
    } catch (e: NeedsForeground) {
        // The TV app runs in the background and could not bring its screen up by itself: someone must open it.
        json(Response.Status.CONFLICT, """{"error":"needs foreground","needsForeground":true,"message":${q(e.message ?: "")}}""")
    } catch (e: Exception) {
        json(Response.Status.INTERNAL_ERROR, """{"error":${q(e.message ?: e.javaClass.simpleName)}}""")
    }

    private fun route(s: IHTTPSession): Response {
        val p = s.parameters.mapValues { it.value.firstOrNull().orEmpty() }
        val path = s.uri
        if (s.method == Method.GET && path == "/") return page()
        if (s.method == Method.GET && path == "/api/hello")
            return ok("""{"app":"castbridge-tv","v":${q(VERSION)},"pinRequired":${guard != null}}""")
        val isStream = (s.method == Method.GET || s.method == Method.HEAD) && path.startsWith("/stream/")
        val loopbackStream = isStream && p["t"] == streamToken && s.remoteIpAddress.let { it == "127.0.0.1" || it == "::1" || it == "0:0:0:0:0:0:0:1" }
        if (!loopbackStream) denied(s, p)?.let { return it }
        if (isStream) return stream(s, path.removePrefix("/stream/"))
        val ext = if (path.startsWith("/api/")) extension?.handle(path, s.method.name, p) else null
        return when {
            // NanoHTTPD already percent-decoded the URI. A rejected upload leaves its body unread on the
            // socket, which would corrupt the next keep-alive request: close the connection instead.
            s.method == Method.PUT && path.startsWith("/upload/") -> upload(s, path.removePrefix("/upload/"), p).also {
                if (it.status != Response.Status.OK) it.addHeader("Connection", "close")
            }
            path == "/api/info" -> ok(info())
            path == "/api/storage" -> storage(s.method, p)
            path == "/api/storage/check" -> if (s.method == Method.GET) check(p) else json(Response.Status.METHOD_NOT_ALLOWED, """{"error":"use GET"}""")
            path == "/api/part" -> named(p) { name ->
                if (findFinal(name) == null && findPart(name) == null) volumes.missingOwner(name)?.let { return@named removed() }
                ok(partJson(name))
            }
            path == "/api/sysinfo" -> sysinfo()
            path == "/api/library" && s.method == Method.GET -> ok(libraryJson())
            path == "/api/player/tracks" && s.method == Method.GET -> ok(tracksJson())
            path == "/api/thumb" && s.method == Method.GET -> named(p) { thumb(it, p["volume"]) }
            ext != null -> json(status(ext.status), ext.json)
            s.method != Method.POST -> json(Response.Status.METHOD_NOT_ALLOWED, """{"error":"use POST"}""")
            path == "/api/library/watched" -> named(p) { setWatched(it, p["watched"] != "0" && p["watched"] != "false") }
            path == "/api/playlist" -> {
                // names separated by "/" (a character file names cannot contain), like /api/apk/install
                val names = p["names"].orEmpty().split('/').filter { it.isNotEmpty() }
                val rep = Playlist.Repeat.of(p["repeat"] ?: "off") ?: return bad("repeat must be off, all or one")
                if (names.isEmpty() || names.any { safeName(it) == null }) return bad("names required")
                if (!playAll(names, p["start"]?.toIntOrNull() ?: 0, rep)) return json(Response.Status.NOT_FOUND, """{"error":"not found"}""")
                ok(info())
            }
            path == "/api/player/next" || path == "/api/player/prev" ->
                if (playlistStep(if (path.endsWith("next")) 1 else -1)) ok(info()) else json(Response.Status.CONFLICT, """{"error":"no playlist"}""")
            path == "/api/player/repeat" -> {
                val rep = Playlist.Repeat.of(p["value"]) ?: return bad("value must be off, all or one")
                if (!setRepeat(rep)) return json(Response.Status.CONFLICT, """{"error":"no playlist"}""")
                ok(info())
            }
            path == "/api/player/subfile" -> named(p) { sub ->
                if (!SubtitleFinder.isSubtitle(sub)) return@named bad("not a subtitle file")
                val f = findFinal(sub)?.file ?: return@named json(Response.Status.NOT_FOUND, """{"error":"not found"}""")
                if (player.subtitleFile(f)) ok(tracksJson()) else json(Response.Status.CONFLICT, """{"error":"not playing"}""")
            }
            path.startsWith("/api/player/") -> {
                val cur = player.tracks() ?: return json(Response.Status.CONFLICT, """{"error":"not playing"}""")
                val cmd = PlayerParams.command(path.removePrefix("/api/player/"), p, cur)
                    ?: return if (path.removePrefix("/api/player/") in PLAYER_ROUTES) bad("bad value") else json(Response.Status.NOT_FOUND, """{"error":"not found"}""")
                if (player.command(cmd)) ok(tracksJson()) else json(Response.Status.CONFLICT, """{"error":"not possible now"}""")
            }
            path == "/api/storage/target" -> setTarget(p["value"].orEmpty())
            path == "/api/storage/move" -> named(p) { startMove(it, p["to"].orEmpty()) }
            path == "/api/storage/move/cancel" -> { moveJob?.let { if (it.state == "running") it.cancelled = true }; ok(storageJson()) }
            path == "/api/storage/rescan" -> { volumes.refresh(remeasure = p["measure"] == "1"); invalidate(); ok(storageJson()) }
            path == "/api/storage/saf/pick" -> safPicker?.let { ok("""{"message":${q(it())}}""") }
                ?: json(NOT_IMPLEMENTED, """{"error":"not supported on this device"}""")
            path == "/api/storage/open-settings" -> settingsOpener?.let { f ->
                val why = f()
                ok("""{"opened":${why == null},"message":${why?.let(::q) ?: "null"}}""")
            } ?: json(NOT_IMPLEMENTED, """{"error":"not supported on this device"}""")
            path == "/api/volume" -> withDevice { d ->
                val pct = p["pct"]?.toIntOrNull()?.coerceIn(0, 100) ?: return@withDevice bad("pct required")
                d.setVolume(pct); sysinfo()
            }
            path == "/api/restart" -> withDevice { d ->
                Thread { Thread.sleep(400); runCatching { d.restartApp() } }.apply { isDaemon = true }.start()
                ok("""{"restarting":true}""")
            }
            path == "/api/rename" -> named(p) { from ->
                val to = safeName(p["to"].orEmpty()) ?: return@named bad("bad target name")
                val src = findFinal(from) ?: return@named json(Response.Status.NOT_FOUND, """{"error":"not found"}""")
                when {
                    moving(from) -> json(Response.Status.CONFLICT, """{"error":"moving"}""")
                    exists(to) -> json(Response.Status.CONFLICT, """{"error":"target exists"}""")
                    else -> synchronized(FileLocks.of(LOCK_ROOT, from)) {
                        if (isPlaying(from)) player.stop()
                        val toStored = src.v.storedName(to)
                        if (!src.st.rename(src.name, toStored)) throw IOException("rename failed")
                        library?.renamed(src.name, toStored, src.size)
                        (src.st as? FileStore)?.let { fs ->
                            if (src.name in Storage.playedNames(fs.dir)) { Storage.forget(fs.dir, src.name); Storage.markPlayed(fs.dir, toStored) }
                        }
                        invalidate()
                        ok(info())
                    }
                }
            }
            path == "/api/reset" -> named(p) { name ->
                volumes.volumes().forEach { v ->
                    val st = volumes.store(v); val n = v.storedName(name)
                    st.deletePart(n); (st as? FileStore)?.let { Meta.delete(it.dir, n) }; volumes.forgetPart(v, n)
                }
                volumes.forgetMissing(name); meters.remove(name); invalidate(); ok(partJson(name))
            }
            path == "/api/delete" -> named(p) { name ->
                if (moving(name)) return@named json(Response.Status.CONFLICT, """{"error":"moving"}""")
                val only = p["volume"]?.takeIf { it.isNotEmpty() }
                if (isPlaying(name)) player.stop()
                synchronized(FileLocks.of(LOCK_ROOT, name)) {
                    volumes.volumes().filter { only == null || it.id == only }.forEach { v ->
                        val st = volumes.store(v); val n = v.storedName(name)
                        st.finalSize(n)?.let { sz -> library?.deleted(n, sz) }
                        st.deleteFinal(n); st.deletePart(n)
                        (st as? FileStore)?.let { Meta.delete(it.dir, n); Storage.forget(it.dir, n) }
                        volumes.forgetPart(v, n)
                    }
                    if (only == null) volumes.forgetMissing(name)
                    meters.remove(name); invalidate()
                }
                ok(info())
            }
            path == "/api/play" -> named(p) { name ->
                if (moving(name)) return@named json(Response.Status.CONFLICT, """{"error":"moving"}""")
                playlist = null
                val pos = p["pos"]?.toLongOrNull() ?: 0
                val hit = findFinal(name)
                if (hit != null) {
                    playingVolume = hit.v.id
                    val f = hit.file
                    if (f != null) { Storage.markPlayed(f.parentFile, f.name); player.play(f, pos) }
                    else player.playSaf(hit.name, hit.size, pos)
                    ok(info())
                } else playIncomplete(name, pos)
            }
            path == "/api/pause" -> { player.pause(); ok(info()) }
            path == "/api/resume" -> { player.resume(); ok(info()) }
            path == "/api/stop" -> { playlist = null; player.stop(); ok(info()) }
            path == "/api/seek" -> { player.seek(p["pos"]?.toLongOrNull() ?: 0); ok(info()) }
            else -> json(Response.Status.NOT_FOUND, """{"error":"not found"}""")
        }
    }

    private fun exists(name: String): Boolean = volumes.volumes().any { v ->
        val st = volumes.store(v); val n = v.storedName(name)
        st.finalSize(n) != null || st.partSize(n) > 0
    }

    /** Null when the request may proceed, else 401 (with Connection: close: an unread PUT body would corrupt keep-alive). */
    private fun denied(s: IHTTPSession, p: Map<String, String>): Response? {
        val g = guard ?: return null
        val ip = s.remoteIpAddress ?: "?"
        val given = s.headers["x-cb-pin"] ?: p["pin"]
        return when (g.check(ip, given)) {
            PinGuard.Result.OK -> null
            PinGuard.Result.BAD -> json(Response.Status.UNAUTHORIZED, """{"error":"bad pin"}""")
            PinGuard.Result.LOCKED -> json(Response.Status.UNAUTHORIZED,
                """{"error":"locked","retryAfter":${g.retryAfterSeconds(ip)}}""")
        }?.also { it.addHeader("Connection", "close") }
    }

    /** The admin web page: static, contains no data; every API call it makes carries the PIN. */
    private fun page(): Response = newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", ADMIN_HTML)
        .also { it.addHeader("Cache-Control", "no-store") }

    /** A write failed because of the disk, not the network. */
    private class DiskError(cause: IOException) : IOException(cause.message, cause)

    /** 503: the drive holding this upload is gone; the phone retries like after a network cut and resumes when it is back. */
    private fun removed(): Response = json(SERVICE_UNAVAILABLE, """{"error":"volume removed"}""")

    private fun refusal(r: StoragePolicy.Refusal): Response = when (r.http) {
        503 -> json(SERVICE_UNAVAILABLE, """{"error":${q(r.message)}}""")
        413 -> json(PAYLOAD_TOO_LARGE, """{"error":${q(r.message)},"message":${q(humanRefusal(r.message))}}""")
        else -> json(INSUFFICIENT_STORAGE, """{"error":${q(r.message)}}""")
    }

    private fun upload(s: IHTTPSession, rawName: String, p: Map<String, String>): Response {
        uploading.incrementAndGet()
        try { return uploadCounted(s, rawName, p) } finally { uploading.decrementAndGet() }
    }

    private fun uploadCounted(s: IHTTPSession, rawName: String, p: Map<String, String>): Response {
        val name = safeName(rawName) ?: return bad("bad name")
        val offset = p["offset"]?.toLongOrNull() ?: return bad("offset required")
        val total = p["total"]?.toLongOrNull()?.takeIf { it > 0 } ?: return bad("total required")
        val len = s.headers["content-length"]?.toLongOrNull() ?: return bad("content-length required")
        val target = p["target"]?.takeIf { it.isNotEmpty() } ?: cfg.target
        if (!StoragePolicy.isValidTarget(target, volumes.volumes() + volumes.missingVolumes())) return bad("bad target")
        findFinal(name)?.let { if (it.size == total) return ok(partJson(name)) }
        synchronized(FileLocks.of(LOCK_ROOT, name)) {
            if (moving(name)) return json(Response.Status.CONFLICT, """{"error":"moving"}""")
            var owner = findPart(name)
            if (owner == null) volumes.missingOwner(name)?.let { return removed() }
            val cur = owner?.size ?: 0L
            if (offset != cur || offset + len > total) return json(Response.Status.CONFLICT, partJson(name))
            if (owner == null) {
                // A new upload: pick the volume (policy), then check quota/space there (evicting old played files if allowed).
                val plan = StoragePolicy.plan(volumes.snapshot(), target, total, TransferRule.minFree(cfg), reclaimable = ::reclaimable)
                plan.refusal?.let { r -> return if (r.http == 507) spaceRefusal(r.message, target, total) else refusal(r) }
                var why: String? = null
                for (c in plan.candidates) {
                    val w = ensureRoom(c.volume, total, xferCfg)
                    if (w != null) { why = w; continue }
                    val st = volumes.store(c.volume)
                    owner = Hit(c.volume, st, c.volume.storedName(name), 0)
                    break
                }
                val o = owner ?: return spaceRefusal(why ?: "no storage available", target, total)
                // No stale empty partial copy elsewhere (a failed earlier attempt).
                volumes.volumes().filter { it.id != o.v.id }.forEach { v ->
                    val st = volumes.store(v); val n = v.storedName(name)
                    if (st.partSize(n) == 0L) { st.deletePart(n); (st as? FileStore)?.let { Meta.delete(it.dir, n) } }
                }
                (o.st as? FileStore)?.let { Meta.delete(it.dir, o.name) }      // a fresh upload: forget any stale size
            } else {
                ensureRoom(owner.v, total - cur, xferCfg)?.let { why -> return json(INSUFFICIENT_STORAGE, spaceJson(why, owner.v, total - cur)) }
            }
            val o = owner!!
            val v = o.v; val st = o.st
            if (!volumes.alive(v)) return removed()
            volumes.notePart(v, o.name)
            val fileStore = st as? FileStore
            val lock = if (fileStore != null) FileLocks.of(fileStore.dir, o.name) else Any()
            synchronized(lock) {
                // Append as bytes arrive: whatever reached the disk before a network cut or a pulled drive is kept for resume.
                try {
                    fileStore?.let { Meta.write(it.dir, o.name, total) }   // lets /stream/ announce the final size before the last byte arrives
                    val meter = meters.getOrPut(name) { RateMeter() }
                    val input = s.inputStream
                    val out = try { st.openPart(o.name) } catch (e: IOException) { throw DiskError(e) }
                    out.use {
                        // Full speed: fill a large block from the socket, then one disk write (no fsync per block; a removable
                        // drive is flushed every removableSyncBytes and at the end, see commit()).
                        val buf = ByteArray(cfg.uploadBufferBytes)
                        var fill = 0
                        var left = len
                        var sinceSync = 0L
                        fun flush() {
                            if (fill == 0) return
                            try { out.write(buf, 0, fill) } catch (e: IOException) { throw DiskError(e) }
                            sinceSync += fill; fill = 0
                            if (v.kind == VolumeKind.REMOVABLE && sinceSync >= cfg.removableSyncBytes) {
                                runCatching { (out as? java.io.FileOutputStream)?.fd?.sync() }; sinceSync = 0
                            }
                        }
                        try {
                            while (left > 0) {
                                if (!volumes.alive(v)) throw DiskError(IOException("volume removed"))
                                val r = input.read(buf, fill, minOf((buf.size - fill).toLong(), left).toInt())
                                if (r < 0) break
                                fill += r; left -= r
                                meter.add(r.toLong())
                                if (fill == buf.size) flush()
                            }
                        } catch (e: DiskError) { throw e } catch (e: IOException) {
                            flush()                                  // the network dropped: keep what arrived, the phone resumes from there
                            throw e
                        }
                        flush()
                    }
                    if (st.partSize(o.name) == total) {
                        try { st.commit(o.name) } catch (e: IOException) { throw DiskError(e) }
                        fileStore?.let { Storage.forget(it.dir, o.name); Meta.delete(it.dir, o.name) }
                        meters.remove(name); volumes.forgetPart(v, o.name)
                        onNotice(when (MediaType.of(name)) { MediaType.VIDEO -> "Vidéo reçue"; MediaType.AUDIO -> "Musique reçue"; MediaType.OTHER -> "Fichier reçu" } + " ✓  ${LibraryLogic.title(name)}")
                        // The upload replaced any older file of that name: no silent duplicate on another volume.
                        finals(name).filter { it.v.id != v.id && !isPlaying(name) }.forEach {
                            it.st.deleteFinal(it.name); (it.st as? FileStore)?.let { f -> Storage.forget(f.dir, it.name) }
                        }
                    }
                } catch (e: DiskError) {
                    return diskFailure(v, e)
                }
            }
            invalidate()
            return ok(partJson(name))
        }
    }

    /** Why a disk write failed: drive pulled (503, retry), read-only (503), file too big for the FS (413), no space (507). */
    private fun diskFailure(v: StorageVolume, e: DiskError): Response {
        val msg = e.message.orEmpty()
        if (!volumes.store(v).reachable() || msg == "volume removed" || !volumes.alive(v)) {
            volumes.markRemoved(v.id); return removed()
        }
        return when {
            v.kind != VolumeKind.SAF && !v.dir.canWrite() -> json(SERVICE_UNAVAILABLE, """{"error":"volume read-only"}""")
            msg.contains("too large", true) || msg.contains("EFBIG") ->
                json(PAYLOAD_TOO_LARGE, """{"error":"file too large for this volume","message":${q(humanRefusal("file too large for ${v.fs.label}"))}}""")
            msg.contains("No space", true) || msg.contains("ENOSPC") -> json(INSUFFICIENT_STORAGE, spaceJson("not enough space", v))
            else -> throw e
        }
    }

    private fun partJson(name: String): String {
        findFinal(name)?.let { return """{"name":${q(name)},"length":${it.size},"done":true,"volume":${q(it.v.id)}}""" }
        val part = findPart(name)
        return """{"name":${q(name)},"length":${part?.size ?: 0},"done":false,"volume":${part?.let { q(it.v.id) } ?: "null"}}"""
    }

    // /api/info is polled every second by phones and the web page: list the folders at most once per infoCacheMs.
    private class Entry(val v: StorageVolume, val name: String, val size: Long, val received: Long, val complete: Boolean, val dup: Boolean)
    private class Listing(val at: Long, val entries: List<Entry>, val used: Map<String, Long>) {
        val filesJson: String by lazy {
            entries.joinToString(",", "[", "]") { e ->
                "{\"name\":${q(e.name)},\"size\":${e.size},\"received\":${e.received},\"complete\":${e.complete}," +
                    "\"volume\":${q(e.v.id)},\"duplicate\":${e.dup}}"
            }
        }
    }
    @Volatile private var listing: Listing? = null
    private fun invalidate() { listing = null }

    private fun listing(): Listing {
        val now = System.nanoTime() / 1_000_000
        listing?.let { if (now - it.at < cfg.infoCacheMs) return it }
        val vols = volumes.volumes()
        val raw = ArrayList<Entry>()
        val used = HashMap<String, Long>()
        val finalKeys = HashSet<String>()
        // Finished files first, so a partial copy (e.g. the target of a move) of a file that exists is not listed as an upload.
        val listed = vols.map { it to runCatching { volumes.store(it).list() }.getOrDefault(emptyList()) }
        for ((v, l) in listed) {
            used[v.id] = l.sumOf { it.size }
            for (e in l) if (!e.part) { raw += Entry(v, e.name, e.size, e.size, true, false); finalKeys += e.name.lowercase() }
        }
        for ((v, l) in listed) for (e in l) if (e.part && e.name.lowercase() !in finalKeys) {
            val total = (volumes.store(v) as? FileStore)?.let { Meta.read(it.dir, e.name) } ?: continue     // no size known: not listed
            raw += Entry(v, e.name, total, e.size, false, false)
        }
        val count = raw.groupingBy { it.name.lowercase() to it.complete }.eachCount()
        val entries = raw.map { Entry(it.v, it.name, it.size, it.received, it.complete, (count[it.name.lowercase() to it.complete] ?: 1) > 1) }
            .sortedWith(compareBy({ it.name }, { it.v.id }))
        return Listing(now, entries, used).also { listing = it }
    }

    /** The volume the next upload would go to (for the top-level free/used/quota numbers older clients read). */
    private fun primary(): StorageVolume? {
        val snap = volumes.snapshot()
        return StoragePolicy.plan(snap, cfg.target, 0, 0).candidates.firstOrNull()?.volume
            ?: snap.firstOrNull { it.kind == VolumeKind.INTERNAL } ?: snap.firstOrNull()
    }

    private fun quotaOf(v: StorageVolume, used: Long): Long {
        val st = volumes.store(v) as? FileStore ?: return -1
        return Storage.quota(st.dir, cfg, used, volumes.free(v), v.kind)
    }

    /** Files being received right now: (name, received bytes, final size). */
    fun receiving(): List<Triple<String, Long, Long>> = listing().entries.filter { !it.complete }.map { Triple(it.name, it.received, it.size) }

    /** Finished files (newest first) with what the library screens need: thumbnail, duration, resume position, volume. */
    fun libraryItems(): List<LibraryItem> {
        val l = listing()
        val lib = library
        val ps = player.state()
        val files = l.entries.filter { it.complete }.map { e ->
            val f = (volumes.store(e.v) as? FileStore)?.let { File(it.dir, it.diskName(e.name)) }
            val m = lib?.meta(e.name, e.size, f) ?: FileMeta()
            LibraryItem(e.name, e.size, f?.lastModified() ?: 0L, e.v.id, e.v.label, e.v.kind, m, e.dup,
                ps.state != "idle" && ps.name == e.name)
        }
        return LibraryLogic.sortNewestFirst(files, { it.mtime }, { it.name })
    }

    fun libraryJson(): String {
        val sorted = libraryItems()
        return sorted.joinToString(",", "{\"files\":[", "]") { i ->
            val m = i.meta
            "{\"name\":${q(i.name)},\"title\":${q(i.title)},\"size\":${i.size},\"mtime\":${i.mtime}," +
                "\"volume\":${q(i.volumeId)},\"volumeLabel\":${q(i.volumeLabel)},\"kind\":${q(i.volumeKind.name.lowercase())}," +
                "\"type\":${q(i.type.name.lowercase())}," +
                "\"durationMs\":${m.durationMs},\"resumeMs\":${m.resumeMs},\"watched\":${m.watched},\"playedAt\":${m.playedAtMs}," +
                "\"hasThumb\":${m.hasThumb},\"duplicate\":${i.duplicate},\"playing\":${i.playing}}"
        } + "],\"count\":${sorted.size}}"
    }

    /** JPEG thumbnail of a stored file for the TV's own screen (null = not ready or impossible; generation is queued). */
    fun thumbnail(name: String, volume: String? = null): ByteArray? {
        val lib = library ?: return null
        val hit = finals(name).let { hs -> hs.firstOrNull { it.v.id == volume } ?: hs.firstOrNull() } ?: return null
        return lib.thumb(hit.name, hit.size, hit.file)
    }

    private fun setWatched(name: String, watched: Boolean): Response {
        val lib = library ?: return json(NOT_IMPLEMENTED, """{"error":"no library"}""")
        val hits = finals(name)
        if (hits.isEmpty()) return json(Response.Status.NOT_FOUND, """{"error":"not found"}""")
        hits.forEach { lib.setWatched(it.name, it.size, watched) }
        return ok(libraryJson())
    }

    private fun thumb(name: String, volume: String?): Response {
        val lib = library ?: return json(Response.Status.NOT_FOUND, """{"error":"no library"}""")
        val hit = finals(name).let { hs -> hs.firstOrNull { it.v.id == volume } ?: hs.firstOrNull() }
            ?: return json(Response.Status.NOT_FOUND, """{"error":"not found"}""")
        val bytes = lib.thumb(hit.name, hit.size, hit.file)
            ?: return if (lib.thumbFailed(hit.name, hit.size, hit.file)) json(Response.Status.NOT_FOUND, """{"error":"no thumbnail"}""")
                else json(NO_CONTENT_YET, """{"error":"thumbnail not ready"}""")
        return newFixedLengthResponse(Response.Status.OK, "image/jpeg", java.io.ByteArrayInputStream(bytes), bytes.size.toLong())
            .also { it.addHeader("Cache-Control", "private, max-age=86400") }
    }

    private fun info(): String {
        val l = listing()
        val ps = player.state()
        val pv = primary()
        val free = pv?.let { volumes.free(it) } ?: 0
        val used = pv?.let { l.used[it.id] } ?: 0
        val quota = pv?.let { quotaOf(it, used) } ?: 0
        return """{"files":${l.filesJson},"free":$free,"used":$used,"quota":$quota,"target":${q(cfg.target)},"volumes":${volumesJson(l)},""" +
            """"player":{"state":${q(ps.state)},"name":${ps.name?.let(::q) ?: "null"},"pos":${ps.posMs},"dur":${ps.durMs}},"playlist":${playlistJson()}}"""
    }

    private fun volumesJson(l: Listing = listing()): String {
        val present = volumes.snapshot().joinToString(",") { v ->
            val used = l.used[v.id] ?: 0
            val dups = l.entries.count { it.v.id == v.id && it.dup }
            "{\"id\":${q(v.id)},\"label\":${q(v.label)},\"kind\":${q(v.kind.name.lowercase())},\"fs\":${q(v.fs.label)}," +
                "\"removable\":${v.removable},\"writable\":${v.writable},\"present\":true,\"free\":${v.free},\"total\":${v.total}," +
                "\"used\":$used,\"quota\":${quotaOf(v, used)},\"writeBps\":${v.writeBps}," +
                "\"maxFileBytes\":${if (v.maxFileBytes == Long.MAX_VALUE) -1 else v.maxFileBytes}," +
                "\"warnings\":${strs(warningsOf(v, dups))},\"formatAdvice\":${formatAdvice(v)?.let(::q) ?: "null"}}"
        }
        val absent = volumes.missingVolumes().joinToString(",") { v ->
            "{\"id\":${q(v.id)},\"label\":${q(v.label)},\"kind\":${q(v.kind.name.lowercase())},\"fs\":${q(v.fs.label)},\"present\":false," +
                "\"warnings\":${strs(listOf("volume absent : les envois qui y étaient en cours reprendront à son retour"))}}"
        }
        return "[" + listOf(present, absent).filter { it.isNotEmpty() }.joinToString(",") + "]"
    }

    private fun strs(l: List<String>) = l.joinToString(",", "[", "]") { q(it) }

    private fun warningsOf(v: StorageVolume, dups: Int): List<String> = buildList {
        if (!v.writable) add("Non inscriptible" + (v.note?.let { " ($it)" } ?: "") + " : la clé est ignorée pour les envois.")
        if (v.kind == VolumeKind.REMOVABLE && v.fs == Fs.FAT32) add("FAT32 : un fichier de 4 Go ou plus ne peut pas y être stocké.")
        if (v.kind == VolumeKind.REMOVABLE && v.fs == Fs.UNKNOWN) add("Système de fichiers non détecté : limite de 4 Go non vérifiable, noms de fichiers restreints par précaution.")
        if (v.kind == VolumeKind.SAF) add("Dossier choisi par le sélecteur système : pas de lecture pendant l'envoi, envoi complet avant lecture.")
        if (v.writeBps in 1 until StoragePolicy.SLOW_BPS) add("Écriture lente (${v.writeBps / 1000} ko/s mesurés) : la lecture pendant l'envoi risque de saccader.")
        else if (v.writeBps in 1 until StoragePolicy.COMFORT_BPS) add("Écriture modeste (${v.writeBps / 1000} ko/s) : lecture pendant l'envoi possible seulement pour des vidéos à faible débit.")
        if (v.free in 0 until (cfg.minFreeBytes * 2)) add("Peu d'espace libre.")
        if (dups > 0) add("$dups fichier(s) en double sur plusieurs volumes (voir la liste).")
    }

    /** What would help, honestly: an app cannot format a drive (only the user can, from a computer or the TV's settings). */
    private fun formatAdvice(v: StorageVolume): String? {
        if (v.kind != VolumeKind.REMOVABLE) return null
        return when {
            !v.writable && v.fs in setOf(Fs.NTFS, Fs.EXT4, Fs.F2FS, Fs.UNKNOWN) ->
                "Écriture impossible : ce système de fichiers (${v.fs.label}) n'est souvent pas inscriptible par une app Android, ou la clé est protégée en écriture. " +
                    "Sauvegardez son contenu puis reformatez-la en exFAT (ou FAT32 si tous les fichiers font moins de 4 Go) depuis un ordinateur. " +
                    "Si les réglages de la TV le proposent, la clé peut aussi être « adoptée » comme stockage interne (efface tout, la clé reste liée à cette TV). L'app ne formate jamais."
            !v.writable ->
                "Écriture impossible sur cette clé ${v.fs.label} : interrupteur de protection, ou système de fichiers à réparer après un retrait brutal " +
                    "(branchez-la sur un ordinateur pour la vérifier) ; sinon reformatez-la en exFAT. L'app ne formate jamais."
            v.fs == Fs.FAT32 ->
                "FAT32 ne stocke pas les fichiers de 4 Go ou plus. Pour de gros films, reformatez la clé en exFAT depuis un ordinateur (efface tout). L'app ne formate jamais."
            else -> null
        }
    }

    /** Bytes that eviction could free on [v] right now: played files, except the one in use. */
    private fun reclaimable(v: StorageVolume): Long {
        if (!cfg.evictPlayed) return 0
        val st = volumes.store(v) as? FileStore ?: return 0
        val played = Storage.playedNames(st.dir); val playing = player.state().name
        return Storage.files(st.dir).filter { it.name in played && it.name != playing }.sumOf { it.length() }
    }

    /** Null if [incoming] bytes can be stored on [v], else the reason. May delete old *played* files when eviction is on. */
    private fun ensureRoom(v: StorageVolume, incoming: Long, p: TvProfile = cfg): String? {
        val st = volumes.store(v)
        val free = volumes.free(v)
        if (st !is FileStore) return if (free >= 0 && free - incoming < (if (p === cfg) 0 else p.minFreeBytes)) "not enough space" else null
        val why = Storage.refusal(st.dir, p, incoming, free, v.kind) ?: return null
        if (!p.evictPlayed) return why
        val used = Storage.used(st.dir)
        val needed = maxOf(used + incoming - Storage.quota(st.dir, p, used, free, v.kind), incoming + p.minFreeBytes - free, 1L)
        val plan = Storage.evictionPlan(st.dir, needed, player.state().name) ?: return why
        plan.forEach { it.delete(); Storage.forget(st.dir, it.name) }
        invalidate()
        return Storage.refusal(st.dir, p, incoming, volumes.free(v), v.kind)
    }

    /** Like [ensureRoom] but deletes nothing (pre-flight check, move). */
    private fun roomWithoutDeleting(v: StorageVolume, incoming: Long, allowEviction: Boolean, p: TvProfile = cfg): String? {
        val st = volumes.store(v)
        val free = volumes.free(v)
        if (st !is FileStore) return if (free >= 0 && free - incoming < (if (p === cfg) 0 else p.minFreeBytes)) "not enough space" else null
        val why = Storage.refusal(st.dir, p, incoming, free, v.kind) ?: return null
        if (!allowEviction || !p.evictPlayed) return why
        val used = Storage.used(st.dir)
        val needed = maxOf(used + incoming - Storage.quota(st.dir, p, used, free, v.kind), incoming + p.minFreeBytes - free, 1L)
        return if (Storage.evictionPlan(st.dir, needed, player.state().name) != null) null else why
    }

    private fun spaceJson(why: String, v: StorageVolume, remaining: Long = 0): String {
        val st = volumes.store(v) as? FileStore
        val free = volumes.free(v)
        val msg = if (why == "not enough space" && remaining > 0) TransferRule.message(v.label, free, remaining, TransferRule.minFree(cfg), !drivepresent()) else humanRefusal(why)
        return """{"error":${q(why)},"message":${q(msg)},"free":$free,"quota":${st?.let { Storage.quota(it.dir, cfg, Storage.used(it.dir), free, v.kind) } ?: -1},"volume":${q(v.id)}}"""
    }

    private fun drivepresent() = volumes.volumes().any { it.kind == VolumeKind.REMOVABLE }

    /**
     * 507 with the precise reason of the "free space after the transfer" rule, about the volume that was the best chance
     * (the explicit target, or in auto the writable volume with the most free space).
     */
    private fun spaceRefusal(why: String, target: String, size: Long): Response {
        val (v, msg) = spaceMessage(why, target, size)
        return json(INSUFFICIENT_STORAGE, """{"error":${q(why)},"message":${q(msg)}${v?.let { ""","volume":${q(it.id)},"free":${it.free}""" } ?: ""}}""")
    }

    private fun spaceMessage(why: String, target: String, size: Long): Pair<StorageVolume?, String> {
        val snap = volumes.snapshot().filter { it.writable }
        val v = snap.firstOrNull { it.id == target } ?: (if (target == StoragePolicy.INTERNAL) snap.firstOrNull { it.kind == VolumeKind.INTERNAL } else null)
            ?: snap.maxByOrNull { it.free }
        val minFree = TransferRule.minFree(cfg)
        val msg = if (v != null && v.free >= 0 && (why == "not enough space" || !TransferRule.ok(v.free, size, minFree)))
            TransferRule.message(v.label, v.free, size, minFree, !drivepresent()) else humanRefusal(why)
        return v to msg
    }

    // ---- storage API ----

    private fun storageJson(): String {
        val l = listing()
        val pv = primary()
        val used = pv?.let { l.used[it.id] } ?: 0
        val warnings = volumes.snapshot().flatMap { v -> warningsOf(v, 0).filter { v.kind != VolumeKind.INTERNAL }.map { "${v.label} : $it" } }
        return """{"used":$used,"free":${pv?.let { volumes.free(it) } ?: 0},"quota":${pv?.let { quotaOf(it, used) } ?: 0},"quotaMb":${cfg.quotaBytes shr 20},""" +
            """"deleteAfterPlay":${cfg.deleteAfterPlay},"evictPlayed":${cfg.evictPlayed},"minFreeMb":${cfg.minFreeBytes shr 20},"minFreeAfterMb":${cfg.minFreeAfterTransfer shr 20},""" +
            """"target":${q(cfg.target)},"primary":${pv?.let { q(it.id) } ?: "null"},"volumes":${volumesJson(l)},""" +
            """"move":${moveJob?.json() ?: "null"},"warnings":${strs(warnings)}}"""
    }

    private fun storage(method: Method, p: Map<String, String>): Response {
        if (method == Method.POST) {
            var c = cfg
            p["deleteAfterPlay"]?.let { c = c.copy(deleteAfterPlay = it == "true" || it == "1") }
            p["evictPlayed"]?.let { c = c.copy(evictPlayed = it == "true" || it == "1") }
            p["quotaMb"]?.let { v -> c = c.copy(quotaBytes = (v.toLongOrNull() ?: return bad("quotaMb must be a number")).coerceAtLeast(0) shl 20) }
            p["minFreeAfterMb"]?.let { v -> c = c.copy(minFreeAfterTransfer = (v.toLongOrNull() ?: return bad("minFreeAfterMb must be a number")).coerceIn(0, 1L shl 20) shl 20) }
            cfg = c; onSettings(c); invalidate()
        } else if (method != Method.GET) return json(Response.Status.METHOD_NOT_ALLOWED, """{"error":"use GET or POST"}""")
        return ok(storageJson())
    }

    private fun setTarget(value: String): Response =
        if (setTargetValue(value)) ok(storageJson()) else bad("value must be auto, internal or a volume id")

    /** Sets the upload target ("auto", "internal" or a volume id); false if [value] is not one of those. */
    fun setTargetValue(value: String): Boolean {
        val known = volumes.volumes() + volumes.missingVolumes()
        if (value.isEmpty() || !StoragePolicy.isValidTarget(value, known)) return false
        cfg = cfg.copy(target = value); onSettings(cfg); invalidate()
        return true
    }

    val target: String get() = cfg.target

    /** "Stockage : clé USB 28 Go libres | ..." for the TV screen. */
    fun storageLine(): String = StorageLine.render(volumes.snapshot(), primary()?.id, volumes.missingVolumes())

    /** Pre-flight: where would this file go, with which warnings, or why it cannot be stored. The phone calls it before sending. */
    private fun check(p: Map<String, String>): Response {
        val name = safeName(p["name"].orEmpty()) ?: return bad("bad name")
        val size = p["size"]?.toLongOrNull()?.takeIf { it > 0 } ?: return bad("size required")
        val dur = p["dur"]?.toLongOrNull() ?: 0
        val target = p["volume"]?.takeIf { it.isNotEmpty() } ?: cfg.target
        if (!StoragePolicy.isValidTarget(target, volumes.volumes() + volumes.missingVolumes())) return bad("volume must be auto, internal or a volume id")
        val minFree = TransferRule.minFree(cfg)
        val options = optionsJson(size, minFree)
        findFinal(name)?.let { if (it.size == size) return ok(checkOk(it.v, it.name, emptyList(), emptyList(), "already stored", 0, options)) }
        findPart(name)?.let { part ->
            // A resume goes back to the volume holding the partial copy: the rule applies to what is still to come.
            val remaining = size - part.size
            val why = roomWithoutDeleting(part.v, remaining, true, xferCfg)
            return if (why == null) ok(checkOk(part.v, part.name, emptyList(), emptyList(), "resumes on the volume holding the partial copy", remaining, options))
                else ok("""{"ok":false,"status":507,"error":${q(why)},"message":${q(TransferRule.message(part.v.label, volumes.free(part.v), remaining, minFree, !drivepresent()))},""" +
                    """"volume":${q(part.v.id)},"remaining":$remaining,"minFreeAfter":$minFree,"options":$options}""")
        }
        volumes.missingOwner(name)?.let {
            return ok("""{"ok":false,"status":503,"error":"volume removed","message":${q("La clé « ${it.label} » qui contient cet envoi est retirée : remettez-la pour reprendre.")}}""")
        }
        val plan = StoragePolicy.plan(volumes.snapshot(), target, size, minFree, dur, ::reclaimable)
        val skipped = plan.skipped.joinToString(",", "[", "]") { """{"volume":${q(it.volumeId)},"reason":${q(it.reason)}}""" }
        fun refuse(status: Int, error: String): Response {
            val msg = if (status == 507) spaceMessage(error, target, size).second else humanRefusal(error)
            return ok("""{"ok":false,"status":$status,"error":${q(error)},"message":${q(msg)},"remaining":$size,"minFreeAfter":$minFree,"skipped":$skipped,"options":$options}""")
        }
        plan.refusal?.let { r -> return refuse(r.http, r.message) }
        val pick = plan.candidates.firstOrNull { roomWithoutDeleting(it.volume, size, true, xferCfg) == null }
            ?: return refuse(507, "not enough space")
        val notes = plan.skipped.map { s -> "${volumes[s.volumeId]?.label ?: s.volumeId} ignoré : " +
            if (s.reason == "not enough space") volumes[s.volumeId]?.let { v -> volumes.free(v).takeIf { it >= 0 }?.let { f ->
                val after = TransferRule.freeAfter(f, size)
                if (after >= 0) "il resterait ${TransferRule.size(after)} après le transfert (il en faut ${TransferRule.size(minFree)})"
                else "le fichier ne tient pas (il manque ${TransferRule.size(-after)})" } } ?: humanRefusal(s.reason)
            else humanRefusal(s.reason) }
        return ok(checkOk(pick.volume, pick.volume.storedName(name), pick.warnings, notes, null, size, options))
    }

    /** Every present volume with the free space it would have after receiving [size] bytes, and whether the rule allows it. */
    private fun optionsJson(size: Long, minFree: Long): String = volumes.snapshot().joinToString(",", "[", "]") { v ->
        val after = if (v.free >= 0) TransferRule.freeAfter(v.free, size) else -1
        val ok = v.writable && size <= v.maxFileBytes && TransferRule.ok(v.free, size, minFree)
        """{"id":${q(v.id)},"label":${q(v.label)},"kind":${q(v.kind.name.lowercase())},"free":${v.free},"freeAfter":$after,"ok":$ok}"""
    }

    private fun checkOk(v: StorageVolume, stored: String, warnings: List<String>, notes: List<String>, why: String?, remaining: Long = 0, options: String = "[]"): String {
        val free = volumes.free(v)
        return """{"ok":true,"volume":${q(v.id)},"label":${q(v.label)},"kind":${q(v.kind.name.lowercase())},"fs":${q(v.fs.label)},"as":${q(stored)},""" +
            """"warnings":${strs(warnings.map(::humanWarning) + notes)},"reason":${why?.let(::q) ?: "null"},""" +
            """"free":$free,"remaining":$remaining,"freeAfter":${if (free >= 0) TransferRule.freeAfter(free, remaining) else -1},""" +
            """"minFreeAfter":${TransferRule.minFree(cfg)},"options":$options}"""
    }

    private fun startMove(name: String, toId: String): Response {
        val to = volumes[toId] ?: return if (volumes.missingVolumes().any { it.id == toId }) json(SERVICE_UNAVAILABLE, """{"error":"volume unavailable"}""")
            else json(Response.Status.NOT_FOUND, """{"error":"unknown volume"}""")
        synchronized(FileLocks.of(LOCK_ROOT, name)) {
            val all = finals(name)
            if (all.isEmpty()) return json(Response.Status.NOT_FOUND, """{"error":"not found"}""")
            val src = all.firstOrNull { it.v.id != to.id } ?: return bad("already on that volume")
            if (all.any { it.v.id == to.id }) return json(Response.Status.CONFLICT, """{"error":"target exists"}""")
            if (isPlaying(name)) return json(Response.Status.CONFLICT, """{"error":"playing"}""")
            if (moveJob?.state == "running") return json(Response.Status.CONFLICT, """{"error":"another move is running"}""")
            if (findPart(name) != null && findPart(name)!!.v.id != to.id) return json(Response.Status.CONFLICT, """{"error":"upload in progress"}""")
            val toName = to.storedName(name)
            val dst = volumes.store(to)
            if (dst.finalSize(toName) != null) return json(Response.Status.CONFLICT, """{"error":"target exists"}""")
            if (!to.writable) return json(SERVICE_UNAVAILABLE, """{"error":"target not writable"}""")
            if (src.size > to.maxFileBytes)
                return json(PAYLOAD_TOO_LARGE, """{"error":${q("file too large for ${to.fs.label}")},"message":${q(humanRefusal("file too large for ${to.fs.label}"))}}""")
            val partial = dst.partSize(toName)
            (dst as? FileStore)?.let { fs ->
                if (partial > 0 && Meta.read(fs.dir, toName) != src.size)
                    return json(Response.Status.CONFLICT, """{"error":"target holds a different partial file"}""")
            }
            roomWithoutDeleting(to, src.size - partial, false)?.let { return json(INSUFFICIENT_STORAGE, spaceJson(it, to)) }
            val job = MoveJob(name, src.v, to, toName, src.size)
            moveJob = job
            val srcStore = src.st; val srcName = src.name
            Thread({
                Mover.run(job, srcStore, dst, { volumes.alive(it) })
                if (job.state == "done") {
                    (srcStore as? FileStore)?.let { f ->
                        // played mark travels with the file
                        val wasPlayed = srcName in Storage.playedNames(f.dir)
                        Storage.forget(f.dir, srcName)
                        if (wasPlayed) (dst as? FileStore)?.let { Storage.markPlayed(it.dir, toName) }
                    }
                    (srcStore as? FileStore)?.let { Meta.delete(it.dir, srcName) }
                    volumes.forgetPart(to, toName)
                }
                invalidate()
            }, "cb-move").apply { isDaemon = true; start() }
            invalidate()
            return ok("""{"moving":true,"move":${job.json()}}""")
        }
    }

    // ---- streaming ----

    /** GET/HEAD /stream/<name>: the finished file, or the .part still growing, with Range support (206/416). */
    private fun stream(s: IHTTPSession, rawName: String): Response {
        val name = safeName(rawName) ?: return bad("bad name")
        val fin = findFinal(name)
        val part = if (fin == null) findPart(name) else null
        val src = fin ?: part
        val total = when {
            fin != null -> fin.size
            part != null -> (part.st as? FileStore)?.let { Meta.read(it.dir, part.name) }
            else -> null
        } ?: return if (src == null && volumes.missingOwner(name) != null) removed() else json(Response.Status.NOT_FOUND, """{"error":"not found"}""")
        src!!
        val mime = mimeOf(name)
        // NanoHTTPD 2.3.1 would send the body of a HEAD answer: give HEAD an empty body but the real Content-Length.
        val head = s.method == Method.HEAD
        fun body(from: Long, to: Long): InputStream {
            val fs = src.st as? FileStore
            return if (fs != null) GrowingStream(fs.dir, fs.diskName(src.name), from, to, alive = { volumes.alive(src.v) })
            else BoundedStream(src.st.open(src.name, from), to - from + 1)
        }
        fun reply(st: Response.IStatus, from: Long, to: Long): Response {
            val len = to - from + 1
            val r = if (head) newFixedLengthResponse(st, mime, java.io.ByteArrayInputStream(ByteArray(0)), 0)
                    else newFixedLengthResponse(st, mime, body(from, to), len)
            if (head) r.addHeader("Content-Length", len.toString())
            return r.also { it.addHeader("Accept-Ranges", "bytes") }
        }
        return when (val r = HttpRange.parse(s.headers["range"], total)) {
            HttpRange.R.Unsatisfiable -> newFixedLengthResponse(status(416), MIME_PLAINTEXT, "")
                .also { it.addHeader("Content-Range", "bytes */$total"); it.addHeader("Accept-Ranges", "bytes") }
            HttpRange.R.Full -> reply(Response.Status.OK, 0, total - 1)
            is HttpRange.R.Part -> reply(Response.Status.PARTIAL_CONTENT, r.start, r.end)
                .also { it.addHeader("Content-Range", "bytes ${r.start}-${r.end}/$total") }
        }
    }

    /** At most [limit] bytes of [inner]; closes it. */
    private class BoundedStream(private val inner: InputStream, private var left: Long) : InputStream() {
        override fun read(): Int { if (left <= 0) return -1; val b = inner.read(); if (b >= 0) left--; return b }
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (left <= 0) return -1
            val n = inner.read(b, off, minOf(len.toLong(), left).toInt())
            if (n > 0) left -= n
            return n
        }
        override fun close() = inner.close()
    }

    /** Plays a file that is still arriving, once enough is there to start (else 409 buffering). */
    private fun playIncomplete(name: String, pos: Long): Response {
        val part = findPart(name)
        if (part == null) return volumes.missingOwner(name)?.let { removed() } ?: json(Response.Status.NOT_FOUND, """{"error":"not uploaded"}""")
        val fs = part.st as? FileStore
            ?: return json(Response.Status.CONFLICT, """{"error":"preload only","message":"Ce dossier ne permet pas la lecture pendant l'envoi : attendez la fin de l'envoi."}""")
        val total = Meta.read(fs.dir, part.name) ?: return json(Response.Status.NOT_FOUND, """{"error":"not uploaded"}""")
        val received = part.size
        val needed = Progressive.bootstrapBytes(total, meters[name]?.bytesPerSec() ?: 0)
        if (received < needed)
            return json(Response.Status.CONFLICT, """{"error":"buffering","received":$received,"needed":$needed,"size":$total}""")
        Storage.markPlayed(fs.dir, part.name)
        playingVolume = part.v.id
        player.playStream(streamUrl(name), name, pos)
        return ok(info())
    }

    private fun mimeOf(name: String) = when (name.substringAfterLast('.', "").lowercase()) {
        "mp4", "m4v" -> "video/mp4"; "mkv" -> "video/x-matroska"; "webm" -> "video/webm"; "avi" -> "video/x-msvideo"
        "mov" -> "video/quicktime"; "ts", "m2ts", "mts" -> "video/mp2t"; "mpg", "mpeg" -> "video/mpeg"
        "mp3" -> "audio/mpeg"; "m4a" -> "audio/mp4"; "flac" -> "audio/flac"; "ogg" -> "audio/ogg"
        else -> "application/octet-stream"
    }

    private fun sysinfo(): Response = withDevice { d -> ok(d.sysinfo().toJson(d.volume())) }

    /** GET /api/player/tracks: the player's own state plus the playlist the server keeps. */
    private fun tracksJson(): String {
        val t = player.tracks() ?: return """{"playing":false}"""
        val pl = playlist
        return t.copy(repeat = pl?.repeat?.label ?: "off", queue = pl?.items.orEmpty(), queueIndex = pl?.index ?: -1).toJson()
            .replaceFirst("{", "{\"playing\":true,")
    }

    private inline fun withDevice(f: (Device) -> Response): Response =
        device?.let(f) ?: json(NOT_IMPLEMENTED, """{"error":"device actions not supported on this device"}""")

    private fun status(code: Int): Response.IStatus = Response.Status.lookup(code) ?: object : Response.IStatus {
        override fun getDescription() = "$code"
        override fun getRequestStatus() = code
    }

    private inline fun named(p: Map<String, String>, f: (String) -> Response): Response =
        safeName(p["name"].orEmpty())?.let(f) ?: bad("bad name")

    private fun ok(body: String) = json(Response.Status.OK, body)
    private fun bad(msg: String) = json(Response.Status.BAD_REQUEST, """{"error":${q(msg)}}""")
    private fun json(st: Response.IStatus, body: String) = newFixedLengthResponse(st, "application/json; charset=utf-8", body)  // default would be US-ASCII

    companion object {
        const val PORT = 8765
        const val VERSION = "0.5"
        private val ADMIN_HTML: String by lazy {
            ReceiverServer::class.java.getResourceAsStream("/castbridge/admin.html")?.use { String(it.readBytes(), Charsets.UTF_8) }
                ?: "<!doctype html><meta charset=utf-8><title>CastBridge TV</title><h1>CastBridge TV</h1><p>Page d'administration indisponible.</p>"
        }
        const val SERVICE_TYPE = "_castbridge._tcp."
        private val PLAYER_ROUTES = setOf("audio", "subtitle", "subdelay", "audiodelay", "subsize", "rate", "aspect", "chapter", "title", "hw", "eq")
        private val LOCK_ROOT = File("/castbridge-locks")      // never touched on disk: only a namespace for per-name locks
        private val NOT_IMPLEMENTED = Response.Status.NOT_IMPLEMENTED
        private val SERVICE_UNAVAILABLE = object : Response.IStatus {
            override fun getDescription() = "503 Service Unavailable"
            override fun getRequestStatus() = 503
        }
        private val PAYLOAD_TOO_LARGE = object : Response.IStatus {
            override fun getDescription() = "413 Payload Too Large"
            override fun getRequestStatus() = 413
        }
        /** 202: the thumbnail is being made, ask again in a moment. */
        private val NO_CONTENT_YET = object : Response.IStatus {
            override fun getDescription() = "202 Accepted"
            override fun getRequestStatus() = 202
        }
        private val INSUFFICIENT_STORAGE = object : Response.IStatus {
            override fun getDescription() = "507 Insufficient Storage"
            override fun getRequestStatus() = 507
        }

        /** Human (French) text for the refusal codes of [StoragePolicy], shown on the phone and the web page. */
        fun humanRefusal(code: String): String = when {
            code.startsWith("file too large") ->
                "Fichier trop gros pour ce volume (${code.removePrefix("file too large for ").substringBefore(" (")} : 4 Go - 1 octet maximum). " +
                    "Reformatez la clé en exFAT depuis un ordinateur, ou envoyez vers la mémoire interne (cible « internal »)."
            code == "not enough space" -> "Espace insuffisant sur la TV."
            code == "quota exceeded" -> "Quota de stockage atteint."
            code == "volume unavailable" -> "Le volume choisi comme cible est absent : remettez la clé ou choisissez la cible « auto »."
            code.startsWith("not writable") -> "La clé n'est pas inscriptible (protection en écriture, ou système de fichiers non pris en charge)."
            else -> code
        }

        private fun humanWarning(w: String): String = when {
            w.startsWith("slow drive") -> "Clé lente (" + w.substringAfter("(").substringBefore(")") + ") : la lecture pendant l'envoi risque de saccader."
            w.startsWith("file system unknown") -> "Système de fichiers de la clé non détecté : un fichier de plus de 4 Go peut échouer si elle est en FAT32."
            w.startsWith("no play-while") -> "Dossier choisi par le sélecteur système : la lecture ne pourra démarrer qu'à la fin de l'envoi."
            else -> w
        }

        /**
         * Accepts what a client may name a file. Characters that a *volume* cannot store (`: * ? " < > |` on FAT/exFAT/NTFS)
         * are accepted here and mapped per volume by [NameRules]; separators, dot-files and our own suffixes are refused.
         */
        fun safeName(n: String): String? = n.trim().takeIf {
            it.isNotEmpty() && it.length <= 200 && !it.startsWith(".") && !it.endsWith(Storage.PART) && !it.endsWith(Meta.SUFFIX) &&
                it.none { c -> c == '/' || c == '\\' || c == '\u0000' }
        }

        fun q(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\""
    }
}
