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

data class PlayerState(val state: String = "idle", val name: String? = null, val posMs: Long = 0, val durMs: Long = 0,
                       /** R-15: seconds of comfort of the player (PlaybackHealth), null = unknown (open-loop copy pacing). */
                       val comfortSec: Double? = null,
                       /** R-16: 0/1/2, the picture froze or drops frames while the audio may run on (PlaybackHealth + VideoStallDetector). */
                       val videoDistress: Int = 0)

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
    /** 6-digit PIN required (header X-CB-Pin only) on every route but GET / and GET /api/hello. null = open (tests). */
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
    /** JSON of the last re-adoption scan of the drive (see [UsbReadopt]); null = none yet. Additive field `heavy.readopt` of /api/storage. */
    private val readoptJson: () -> String? = { null },
    /** Routes served without the PIN (the quiz at /quiz, with its own room code); asked before the PIN check. */
    private val publicRoutes: PublicRoutes? = null,
    /** Asked first for every route: a French reason refuses it with 403 (the trial edition closes copy and move), null lets it through. */
    private val routeGuard: ((String) -> String?)? = null,
    /**
     * Per-phone tokens of the trusted phones (castbridge.core.trust): header X-CB-Token (or ?token=) accepted instead of the PIN,
     * except on the routes [castbridge.core.trust.TvAuth.tokenMayCall] keeps for the PIN. Returns the phone's address, or null.
     */
    private val tokenAuth: ((String?) -> String?)? = null,
    /** Parental control as seen by the phone's library assistant (`protected` per file, `childActive`); null = the TV does not say. */
    private val contentFlags: ContentFlags? = null,
    /** Readers of `/stream/` right now; shared with the assistant's bin so that nothing is binned under a reader. */
    private val streamUse: StreamUse = StreamUse(),
    /** Virtual folders of the library (see [FolderIndex]); null = no folders (the library stays a flat list). */
    private val folders: FolderIndex? = null,
    /**
     * Identity of clients that arrive through the Bluetooth API tunnel (castbridge.core.tunnel): all of them come from loopback, so
     * PIN failures are counted against "bt:<address>" instead of one shared "127.0.0.1" (one device never locks the others, nor Wi-Fi).
     */
    private val peers: castbridge.core.ssh.PeerRegistry? = null,
    /** Anti DNS-rebinding: refuse (403) a `Host` header that is not a private / local IP or localhost. Off only for tests. */
    private val hostCheck: Boolean = true,
    /** Temporary compatibility for old clients that send the PIN as `?pin=`. Off by default: the PIN travels in the X-CB-Pin header only. */
    private val legacyPinQuery: Boolean = false,
    /**
     * Real filing at reception (docs/STORAGE.md « Rangement à la réception »): the language of the folders ("fr"/"en") while the setting is on, null = off
     * (files stay flat exactly as before). Asked at every reception, so the setting applies at once.
     */
    private val filingLang: () -> String? = { null },
    /** What is being received, from every path. The service owns it (so Bluetooth shows even if this server did not start); tests inject one with a fake clock. */
    val progress: castbridge.core.xfer.TransferProgress = castbridge.core.xfer.TransferProgress(),
    /** The name of a trusted phone from its address (what [tokenAuth] returns), for « depuis … » in the reception progress; null = unknown. */
    private val sourceName: (String) -> String? = { null },
    /** Lowers the receive/hash/write threads while a video plays (the TV app: Process.setThreadPriority); see [castbridge.core.xfer.PlaybackPriority]. */
    private val receivePriority: castbridge.core.xfer.ThreadPriorityPort = castbridge.core.xfer.ThreadPriorityPort.NONE,
    /**
     * Index of the CONTENT of the files the TV holds (size + SHA-256, R-12, docs/agent-reports/copy-dedup.md), read by GET /api/have. true = its background
     * pass runs (the TV app); false = it is only filled on demand by [indexStep] (tests). Either way it never hashes while a video plays or a copy runs.
     */
    private val contentIndexing: Boolean = false,
    /** This TV's own key for the signature of the `.cbhash` caches (the TV app's private storage, never a volume); null = no cache read or written. */
    private val contentIndexKey: ByteArray? = null,
    /** Other disk or network users that must not share the bus with the background hash (downloads, USB import): true = busy. */
    private val indexBusy: () -> Boolean = { false },
    /** One diagnostic line per refused request (route, status, reason code; never a PIN, token or body): the TV app writes it to logcat at INFO (R-17). */
    private val onLog: (String) -> Unit = {},
) : NanoHTTPD(port) {

    /** The socket of the connection this thread serves (NanoHTTPD: one thread per connection), for [hungUp]. */
    private val connectionSocket = ThreadLocal<java.net.Socket?>()

    override fun createClientHandler(finalAccept: java.net.Socket, inputStream: java.io.InputStream): NanoHTTPD.ClientHandler {
        val sock = castbridge.core.tunnel.AttributedSocket.of(finalAccept, peers)
        return object : NanoHTTPD.ClientHandler(inputStream, sock) {
            override fun run() { connectionSocket.set(sock); try { super.run() } finally { connectionSocket.remove() } }
        }
    }

    /**
     * Has the client of this connection closed it? Peeks one byte with a very short timeout and puts it back (mark/reset): called only by a
     * `/stream/` reader that is WAITING for bytes, on the connection's own thread, so no request can be in flight on this input.
     */
    private fun hungUp(sock: java.net.Socket?, input: InputStream): Boolean {
        if (sock == null || !input.markSupported()) return false
        return try {
            if (sock.isClosed || sock.isInputShutdown) return true
            val old = sock.soTimeout
            sock.soTimeout = 30
            try {
                input.mark(1)
                val r = input.read()
                if (r < 0) true else { input.reset(); false }
            } catch (e: java.net.SocketTimeoutException) { false } finally { runCatching { sock.soTimeout = old } }
        } catch (e: IOException) { true }
    }

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
    /** Multi-connection transfers (/api/transfer/..., see docs/TRANSFER.md); old phones never call it. */
    private val transfers = castbridge.core.xfer.TransferHost(maxStreams = maxOf(2, cfg.maxHttpThreads - 2)).also { h ->
        h.finalSizeOf = { n, sz -> findFinalOrOrigin(n, sz)?.size }
    }
    private val chunking = java.util.concurrent.atomic.AtomicInteger()
    /** Chunk requests being served right now (tests). */
    internal val activeChunks: Int get() = chunking.get()
    /** A `finish` of transfer [id] is verifying right now (tests). */
    internal fun finishingNow(id: String): Boolean = transfers.session(id)?.finishing == true
    /** The file the TV plays while it is still arriving ([playIncomplete]), null otherwise. */
    @Volatile private var growingName: String? = null
    /**
     * « La lecture d'abord » (docs/agent-reports/fluid-playback-during-copy.md): ONE policy, asked by every receive path. While a video plays,
     * a copy runs at background priority, capped, on fewer connections, with spaced fsync/progress and a paced (never skipped) final check;
     * the copy that feeds a growing playback is never starved. At rest nothing changes.
     */
    private val playback = castbridge.core.xfer.PlaybackGovernor(::playbackSignal,
        castbridge.core.xfer.PlaybackPriority.Normal(transfers.maxStreams, cfg.removableSyncBytes), receivePriority,
        onChange = { d -> progress.emitEveryMs = d.progressEveryMs }).also { g ->
        transfers.streamLimit = { g.current().maxStreams }
        transfers.persistEveryMs = { g.current().statePersistMs }
        transfers.slowedNote = { if (g.slowedNow()) castbridge.core.xfer.PlaybackAwareCopyPolicy.SLOWED_TEXT else null }   // R-15: the phone shows it
        progress.slowedNow = g::slowedNow                                                                                 // R-15: so does the TV card
    }

    private fun playbackSignal(): castbridge.core.xfer.PlaybackSignal {
        val ps = player.state()
        if (ps.state != "playing" && ps.state != "buffering") return castbridge.core.xfer.PlaybackSignal(ps.state, ps.name)
        val g = growingName?.takeIf { n -> ps.name != null && (ps.name == n || volumes.volumes().any { it.storedName(n) == ps.name }) }
        val part = g?.let { findPart(it) }
        val total = part?.let { h -> (h.st as? FileStore)?.let { Meta.read(it.dir, h.name) } }
        val size = total ?: ps.name?.let { n -> findFinal(n)?.size } ?: 0L
        return castbridge.core.xfer.PlaybackSignal(ps.state, ps.name, growing = part != null, playheadMs = ps.posMs, durMs = ps.durMs, fileBytes = size,
            writeBps = transfers.stats.bytesPerSec(), feedBps = g?.let { meters[it]?.bytesPerSec() } ?: 0L, contiguousBytes = part?.size ?: 0L, copyBytes = transfers.stats.total(),
            playingVolumeId = (ps.name?.let { findFinal(it) } ?: part)?.v?.id, bufferSec = ps.comfortSec, videoDistress = ps.videoDistress)
    }

    /** The TV player changed state (started, paused, stopped): the policy is read again at once, off the caller's thread (it may run deferred fsyncs). */
    fun playbackChanged() {
        Thread({ runCatching { playback.refresh() } }, "cb-playback-policy").apply { isDaemon = true; start() }
        fileDeferredAsync()                                   // R-13: a « Copier et lire » file is filed once it is no longer read
    }
    /** What is being received right now, from every path (PUT /upload, /api/transfer, and Bluetooth through the app): the TV screen and notification read it. */
    /** Address of the trusted phone whose token authenticated the request this thread is serving (NanoHTTPD: one thread per request), else null. */
    private val tokenPhone = ThreadLocal<String?>()
    /** « depuis … » of a reception: the trusted phone's name, else its address on the network. */
    private fun sourceOf(s: IHTTPSession): String? {
        val addr = tokenPhone.get()                    // set by the authentication of this very request: the token is never verified twice
        if (addr != null) return sourceName(addr) ?: "téléphone de confiance"
        return s.remoteIpAddress?.takeIf { it.isNotEmpty() && it != "127.0.0.1" && it != "::1" }
    }

    /** « Déjà sur la TV ? » by content (R-12): the finished files of the real folders, hashed in the background while the TV is idle. */
    private val contentIndex = ContentIndex(::heldFiles, ::indexIdle, contentIndexKey)

    /**
     * Finished files of the real folders (not the system picker's), with the path the index keys them by; with [size], only the files of that size (from the
     * cached listing: a question never stats the whole library). Partial copies are never held files.
     */
    private fun heldFiles(size: Long?): List<HeldFile> = listing().entries.filter { it.complete && (size == null || it.size == size) }.mapNotNull { e ->
        val fs = volumes.store(e.v) as? FileStore ?: return@mapNotNull null
        val f = fs.fileOf(e.name)
        val rel = f.relativeToOrNull(fs.dir)?.invariantSeparatorsPath ?: return@mapNotNull null
        if (rel.startsWith("..")) return@mapNotNull null
        HeldFile(e.v.id, fs.dir, rel, e.name, e.folder, e.size)
    }

    /** The background hash runs only when nothing plays or buffers and nothing is being received or moved (R-06: « la lecture d'abord »). */
    private fun indexIdle(): Boolean {
        val st = player.state().state
        return st != "playing" && st != "buffering" && activeTransfers() == 0 && transfers.active() == 0 && progress.active().isEmpty() &&
            !streamUse.anyBusy() && !runCatching(indexBusy).getOrDefault(true)        // /stream/ readers, downloads, USB import: not on the bus with them
    }

    /** Hashes one file of the index now if the TV is idle (tests; the TV app uses the background pass). */
    internal fun indexStep(): Boolean = contentIndex.step()

    /** GET /api/have?size=N[&sha256=H]: does the TV hold a finished file of that size (and of that content)? Never a name but the matching one. */
    private fun have(p: Map<String, String>): Response {
        val size = p["size"]?.toLongOrNull()?.takeIf { it > 0 } ?: return bad("size required")
        val sha = p["sha256"]?.lowercase()
        if (sha != null && !ContentHash.valid(sha)) return bad("bad sha256")
        // fresh=1 (a « Déplacer »): only a hash this run computed from the file's bytes counts; the file is re-read first in the queue, « indexing » meanwhile
        return when (val a = contentIndex.query(size, sha, fresh = p["fresh"] == "1")) {
            ContentIndex.Answer.Absent -> ok("""{"state":"absent"}""")
            is ContentIndex.Answer.Candidates -> ok("""{"state":"candidates","count":${a.count},"indexing":${a.indexing}}""")
            is ContentIndex.Answer.Indexing -> ok("""{"state":"indexing","pending":${a.pending}}""")
            is ContentIndex.Answer.Present ->
                // a child profile is active: never the name nor the folder of a file (it may be protected); « present » without a place
                if (contentFlags?.childActive() == true) ok("""{"state":"present","masked":true,"size":${a.size},"sha256":${q(a.sha256)},"complete":true,"fresh":${a.fresh}}""")
                else ok("""{"state":"present","name":${q(a.file.name)},"folder":${q(a.file.folder)},"volume":${q(a.file.volumeId)},"size":${a.size},"sha256":${q(a.sha256)},"complete":true,"fresh":${a.fresh}}""")
        }
    }

    override fun start(timeout: Int, daemon: Boolean) {
        super.start(timeout, daemon)
        if (contentIndexing) contentIndex.startWorker()
    }

    /** Uploads being received right now (the TV app keeps a partial wake lock only while this is above zero). */
    fun activeTransfers(): Int = uploading.get() + chunking.get() + (if (moveJob?.state == "running") 1 else 0)
    @Volatile private var playingVolume: String? = null
    /** "Play one after the other" (library section, selection); null = single file. */
    @Volatile private var playlist: Playlist? = null

    fun streamUrl(name: String) = "http://127.0.0.1:$listeningPort/stream/${java.net.URLEncoder.encode(name, "UTF-8").replace("+", "%20")}?t=$streamToken"

    /** The volumes this server uses (the app asks it to re-scan after a mount/unmount broadcast). */
    fun storageChanged(remeasure: Boolean = false) { volumes.refresh(remeasure); invalidate() }

    // ---- locating files across volumes ----

    private class Hit(val v: StorageVolume, val st: VolumeStore, val name: String, val size: Long) {
        val file: File? get() = (st as? FileStore)?.fileOf(name)
    }

    private fun finals(name: String): List<Hit> = volumes.volumes().mapNotNull { v ->
        val st = volumes.store(v); val n = v.storedName(name)
        st.finalSize(n)?.let { Hit(v, st, n, it) }
    }
    private fun findFinal(name: String): Hit? = finals(name).firstOrNull()

    /**
     * The finished file a phone means by [name] after the TV filed it under a clean name: the file of that very name, else the file the index of
     * original names says it became, else (index lost) the file this name would be filed as today. With [size], the last two count only for a file of exactly
     * that size (a different file with a similar name is a different file). Only the resume / "already there" paths use this: every other route is strict.
     */
    private fun findFinalOrOrigin(name: String, size: Long? = null): Hit? {
        findFinal(name)?.let { if (size == null || it.size == size) return it }      // a homonym of another size is another file (see nameTaken)
        val stores = volumes.volumes().mapNotNull { v -> (volumes.store(v) as? FileStore)?.let { v to it } }
        for ((v, fs) in stores) {
            val k = fs.filing.keyOfOrigin(name) ?: continue
            val sz = fs.finalSize(k) ?: continue
            if (size == null || sz == size) return Hit(v, fs, k, sz)
        }
        val lang = filingLang()
        if (size != null && lang != null) {
            val r = FilingPlan.plan(FilingPlan.Input(name, size = size), lang)
            for ((v, fs) in stores) for (n in Filing.candidates(r, if (v.kind == VolumeKind.INTERNAL) Fs.EXT4 else v.fs)) {
                if (!fs.filing.isFiled(n)) continue
                val sz = fs.finalSize(n) ?: continue
                if (sz == size) return Hit(v, fs, n, sz)
            }
        }
        return null
    }

    private fun effectiveFs(v: StorageVolume): Fs = when { v.kind == VolumeKind.INTERNAL -> Fs.EXT4; v.fs == Fs.UNKNOWN -> Fs.EXFAT; else -> v.fs }

    /** A name is taken when ANY volume holds a file or a partial copy of it, in any folder: the library has one name space. */
    private fun nameTaken(n: String): Boolean = volumes.volumes().any { v ->
        val st = volumes.store(v); val sn = v.storedName(n)
        st.finalSize(sn) != null || st.partSize(sn) > 0
    }

    /**
     * Files the file just committed as [diskName] (the phone sent it as [original]) into its category folder under a clean name (docs/STORAGE.md).
     * Returns the placement, or null when the file stays flat: setting off, not a real folder (SAF), kept flat on purpose (installers, packs), being read
     * right now, too big for the file system, any failure. Never fails the upload: a flat file is always valid.
     */
    private fun fileReceived(v: StorageVolume, st: VolumeStore, diskName: String, original: String, size: Long): Filing.Placement? {
        val optedOut = noFiling.remove(original.lowercase())          // the phone said « filing=0 » for this send (its option is off)
        val lang = filingLang() ?: return null
        if (optedOut) return null
        val fs = st as? FileStore ?: return null
        return try {
            // No NameSpace.lock here: the caller holds the lock of this name (the one /api/rename takes AFTER NameSpace.lock): taking it now could deadlock.
            // The final name is checked again, atomically, by FileStore.fileInto (never over an existing file).
            // A reader holds the flat name (« Copier et lire » plays it while it arrives, R-08): filed as soon as nothing reads it any more (R-13; it used to stay flat for ever).
            if (streamUse.busy(diskName) || isPlaying(diskName)) { addPending(diskName, PendingFiling(v.id, original, size)); return null }
            dropPending(diskName)
            // R-13: the category tree of a new copy (Films, Séries/Titre/Saison, Musique, Photos/AAAA-MM, Documents…), docs/agent-reports/filing-tree.md
            val r = FilingPlan.plan(FilingPlan.Input(original, size = size), lang)
            if (r.keepFlat) return null
            val pl = Filing.place(r, effectiveFs(v), size, self = diskName, taken = ::nameTaken) ?: return null
            if (!fs.fileInto(diskName, pl.folder, pl.name, original)) return null
            contentIndex.renamed(fs.dir, fs.diskName(diskName), pl.rel)      // same bytes: the hash follows the file
            folders?.set(pl.name, pl.folder)
            invalidate()
            pl
        } catch (e: Exception) { null }
    }

    /** A file received while the TV read it, to be filed once it is free: volume, the name the phone sent, size. Key = its flat disk name. */
    private class PendingFiling(val volumeId: String, val original: String, val size: Long)
    private val pendingFiling = java.util.concurrent.ConcurrentHashMap<String, PendingFiling>()
    /** Names (lowercase) whose current send asked for no filing (`filing=0`, the phone's option is off): consumed at the commit. */
    private val noFiling: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    // The pending list is kept on its volume (`.cbfiling-pending`, atomic): a restart during « Copier et lire » does not leave the file flat for ever.
    private fun addPending(disk: String, p: PendingFiling) { pendingFiling[disk] = p; savePending(p.volumeId) }
    private fun dropPending(disk: String) { pendingFiling.remove(disk)?.let { savePending(it.volumeId) } }
    @Synchronized private fun savePending(volumeId: String) {
        val v = volumes[volumeId] ?: return
        val fs = volumes.store(v) as? FileStore ?: return
        val f = File(fs.dir, PENDING_FILE)
        val text = pendingFiling.entries.filter { it.value.volumeId == volumeId }
            .joinToString("") { "${java.net.URLEncoder.encode(it.key, "UTF-8")}\t${java.net.URLEncoder.encode(it.value.original, "UTF-8")}\t${it.value.size}\n" }
        runCatching { if (text.isEmpty()) f.delete() else AtomicFile.write(f, text.toByteArray(Charsets.UTF_8)) }
    }
    private fun loadPendingFiling(v: StorageVolume) {
        val fs = volumes.store(v) as? FileStore ?: return
        val f = File(fs.dir, PENDING_FILE)
        if (!f.isFile) return
        runCatching {
            for (l in f.readLines()) {
                val p = l.split('\t'); if (p.size != 3) continue
                val disk = java.net.URLDecoder.decode(p[0], "UTF-8"); val size = p[2].toLongOrNull() ?: continue
                if (disk.isEmpty() || disk.contains('/') || disk.contains('\\')) continue        // a flat name only, never a path
                pendingFiling.putIfAbsent(disk, PendingFiling(v.id, java.net.URLDecoder.decode(p[1], "UTF-8"), size))
            }
        }
    }

    /**
     * Files the receptions that waited for their reader (« Copier et lire »). Off the caller's thread (renames on a USB key are synced); under the per-name lock
     * the uploads take, so nothing races a new send of that name. A file gone meanwhile (deleted after play, moved) is simply forgotten.
     */
    private fun fileDeferredAsync() {
        if (pendingFiling.isEmpty()) return
        Thread({
            for ((disk, p) in pendingFiling.entries.toList()) runCatching {
                val v = volumes[p.volumeId] ?: return@runCatching
                val st = volumes.store(v)
                if (st.finalSize(disk) != p.size) { dropPending(disk); return@runCatching }
                if (streamUse.busy(disk) || isPlaying(disk) || playlist?.contains(disk) == true) return@runCatching          // still read: next playback change
                synchronized(FileLocks.of(LOCK_ROOT, p.original)) { if (fileReceived(v, st, disk, p.original, p.size) != null) invalidate() }
            }
        }, "cb-deferred-filing").apply { isDaemon = true; start() }
    }

    private fun filedJson(v: StorageVolume, name: String): String {
        val fs = volumes.store(v) as? FileStore ?: return ""
        val rel = fs.filing.locate(name) ?: return ""
        return ",\"folder\":${q(rel.substringBeforeLast('/', ""))},\"finalName\":${q(name)}"
    }
    /** The largest partial copy of [name] (there should be one; if a returned drive brings a second, the listing says so). */
    private fun findPart(name: String): Hit? = volumes.volumes().mapNotNull { v ->
        val st = volumes.store(v); val n = v.storedName(name)
        st.partSize(n).takeIf { it > 0 }?.let { Hit(v, st, n, it) }
    }.maxByOrNull { it.size }

    /** A partial copy of [name] is being written now, or was written less than [PART_BUSY_MS] ago (its modification time on a real folder). */
    private fun partBusy(name: String): Boolean {
        if (progress.isRunning("put:" + name.lowercase())) return true
        val now = System.currentTimeMillis()
        return volumes.volumes().any { v ->
            val fs = volumes.store(v) as? FileStore ?: return@any false
            val n = v.storedName(name)
            val f = File(fs.dir, fs.diskName(n) + Storage.PART)
            f.isFile && now - f.lastModified() < PART_BUSY_MS
        }
    }

    private fun isPlaying(name: String): Boolean {
        val ps = player.state()
        val n = ps.name ?: return false
        return ps.state != "idle" && (n == name || volumes.volumes().any { it.storedName(name) == n })
    }
    private fun moving(name: String) = moveJob?.let { it.state == "running" && it.name == name } == true

    /**
     * Why [name] must not be renamed, moved or binned right now, or null: "moving" (copy to another volume running), "uploading"
     * (a partial copy exists: the name is about to be written), "streaming" (a phone is reading it over /stream/), and, with
     * [strictPlaying], "playing" (the TV player has it open).
     */
    fun busyReason(name: String, strictPlaying: Boolean = true): String? = when {
        moving(name) -> "moving"
        findPart(name) != null || transfers.hasName(name) -> "uploading"
        volumes.volumes().any { streamUse.busy(it.storedName(name)) } || streamUse.busy(name) -> "streaming"
        strictPlaying && isPlaying(name) -> "playing"
        else -> null
    }

    /** (received, total) for a file being uploaded or complete; null if unknown. */
    fun progress(name: String): Pair<Long, Long>? {
        findFinal(name)?.let { return it.size to it.size }
        val p = findPart(name) ?: return null
        val total = (p.st as? FileStore)?.let { Meta.read(it.dir, p.name) } ?: return null
        return p.size to total
    }

    init {
        volumes.volumes().forEach { cleanOrphans(it); recoverMove(it); resyncFiling(it); loadPendingFiling(it) }
        volumes.addListener { ev ->
            invalidate()
            contentIndex.poke()                                         // a key came or went: the index reads the list again
            if (ev.present) loadPendingFiling(ev.volume)
            if (!ev.present) {
                if (playingVolume == ev.volume.id && player.state().state != "idle") {
                    runCatching { player.stop() }
                    onNotice("${ev.volume.label} retiré : lecture arrêtée")
                } else onNotice("${ev.volume.label} retirée : les envois en cours reprendront à son retour")
            } else {
                cleanOrphans(ev.volume); recoverMove(ev.volume); resyncFiling(ev.volume)
                val free = runCatching { volumes.free(ev.volume) }.getOrDefault(-1)
                onNotice("${ev.volume.label} branchée" + (if (free >= 0) " : ${StorageLine.size(free)} libres" else ""))
            }
        }
        setAsyncRunner(BoundedRunner(cfg.maxHttpThreads + (publicRoutes?.extraThreads ?: 0)))
        fileDeferredAsync()                                             // receptions left flat by a restart during « Copier et lire »
    }

    /** A move cut after its copy was verified (power, process killed, key pulled): finish removing the source. Never deletes anything unverified. */
    private fun recoverMove(v: StorageVolume) {
        if (moveJob?.state == "running") return
        val st = volumes.store(v)
        runCatching { Mover.recover(st) { id -> volumes[id]?.let { volumes.store(it) } } }.getOrNull()?.let { onNotice(it) }
    }

    /** Files left by a cut between a rename and its index entry (or written by another app) are listed again: the files are the truth. */
    private fun resyncFiling(v: StorageVolume) { runCatching { (volumes.store(v) as? FileStore)?.filing?.resync() } }

    /** Abandoned partial uploads must not eat scarce space; a drive that was away keeps them 7 times longer. */
    private fun cleanOrphans(v: StorageVolume) {
        val st = volumes.store(v) as? FileStore ?: return
        val age = if (v.kind == VolumeKind.REMOVABLE) cfg.orphanPartMaxAgeMs * 7 else cfg.orphanPartMaxAgeMs
        runCatching { Storage.cleanOrphans(st.dir, age) }
        transfers.sweep(cfg.orphanPartMaxAgeMs)
    }

    /**
     * Called by the app when a file played to the end: honours the "delete after play" setting, then starts the next item of
     * the playlist if there is one. Returns true when something new started playing (the app then keeps the video screen).
     */
    fun onPlaybackEnded(name: String): Boolean {
        deleteAfterPlay(name)
        if (playlist == null) fileDeferredAsync()            // never under a running playlist (it holds the flat names it will play next)
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
        if (f != null) { Storage.markPlayed((hit.st as? FileStore)?.dir ?: f.parentFile, f.name); player.play(f, pos) } else player.playSaf(hit.name, hit.size, pos)
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
            folders?.deleted(h.name)
            (h.st as? FileStore)?.let { Storage.forget(it.dir, h.name) }
            invalidate()
        }
    }

    override fun stop() {
        moveJob?.cancelled = true
        contentIndex.stop()
        super.stop()
    }

    override fun serve(session: IHTTPSession): Response {
        val s = CountingSession(session)
        earlyReject.remove(); rejectCode.remove()
        val r = try {
            route(s)
        } catch (e: NeedsForeground) {
            // The TV app runs in the background and could not bring its screen up by itself: someone must open it.
            json(Response.Status.CONFLICT, """{"error":"needs foreground","needsForeground":true,"message":${q(e.message ?: "")}}""")
        } catch (e: Exception) {
            json(Response.Status.INTERNAL_ERROR, """{"error":${q(e.message ?: e.javaClass.simpleName)}}""")
        }
        return try { afterRoute(s, r) } finally { earlyReject.remove(); rejectCode.remove() }
    }

    // ---- R-17: a refused request must never leave its body unread (the phone would see a reset, « Broken pipe », not the status) ----

    /** The session, counting the body bytes the handler really read (so what is left unread is exact). */
    private class CountingSession(private val d: IHTTPSession) : IHTTPSession by d {
        @Volatile var consumed = 0L
        private val counting by lazy {
            object : java.io.FilterInputStream(d.inputStream) {
                override fun read(): Int = super.read().also { if (it >= 0) consumed++ }
                override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) consumed += it }
                override fun skip(n: Long): Long = super.skip(n).also { if (it > 0) consumed += it }
            }
        }
        override fun getInputStream(): InputStream = counting
    }

    /** Set by the guards that answer before any handler runs (host, trial/locked edition, token, PIN). */
    private val earlyReject = ThreadLocal<Boolean?>()
    /** Short code of why the request was refused (no secret): the TV log and `/api/info` say it. */
    private val rejectCode = ThreadLocal<String?>()
    private fun early(code: String, r: Response): Response { earlyReject.set(true); rejectCode.set(code); return r }

    private class Rejection(val atMs: Long, val route: String, val status: Int, val code: String)
    private val recentRejections = ArrayDeque<Rejection>()

    /** Last 5 refusals as JSON (additive `rejections` of `/api/info`): route without its query, status, code. Never a PIN, token or body. */
    private fun rejectionsJson(): String = synchronized(recentRejections) {
        recentRejections.joinToString(",", "[", "]") { """{"t":${it.atMs},"route":${q(it.route)},"status":${it.status},"code":${q(it.code)}}""" }
    }

    private fun afterRoute(s: CountingSession, r: Response): Response {
        val st = r.status.requestStatus
        val early = earlyReject.get() == true
        val xfer = s.uri.startsWith("/api/transfer/")
        val withBody = s.method == Method.PUT || s.method == Method.POST
        // only requests that carry a body count (scanners' GETs must not fill the ring), and the logged route is cut and filtered (no file name, no line injection)
        if (withBody && st !in 200..299 && st != 429 && (early || xfer)) {
            val rej = Rejection(System.currentTimeMillis(), safeRoute(s.method.name, s.uri), st, rejectCode.get() ?: "refused")
            synchronized(recentRejections) { recentRejections.addLast(rej); while (recentRejections.size > 5) recentRejections.removeFirst() }
            runCatching { onLog("refus ${rej.route} ${rej.status} ${rej.code}") }
        }
        if (!withBody || !(early || xfer)) return r
        val rest = (s.headers["content-length"]?.toLongOrNull() ?: 0L) - s.consumed
        if (rest <= 0) return r
        if (st in 200..299) {
            // answered without reading the body (« already »): read it now so the connection stays usable
            if (rest <= maxDrainBytes && drain(s, rest, maxDrainBytes, DRAIN_MS)) return r
            r.addHeader("Connection", "close")
            return r
        }
        r.addHeader("Connection", "close")
        // The status goes out FIRST (NanoHTTPD closes the response data after flushing it); the unread body is then read and dropped, bounded, so that closing does not reset the connection.
        val code = rejectCode.get()
        // A peer that has not proved who it is (host, trial, token, PIN) gets a short read (64 Kio, 500 ms), nothing at all once locked out, and one at a time per address:
        // it must not hold one of the few HTTP threads. A proven peer (unknown session, volume, 413/507/409...) gets up to a whole block (17 Mio, 2 s).
        val anonymous = early && code != "pin-required"
        if (anonymous && code == "pin-locked") return r
        val ip = s.remoteIpAddress ?: "?"
        val cap = if (anonymous) ANON_DRAIN_CAP else maxDrainBytes
        val ms = if (anonymous) ANON_DRAIN_MS else DRAIN_MS
        val orig = r.data ?: return r
        val once = java.util.concurrent.atomic.AtomicBoolean()        // NanoHTTPD closes the data twice (after the send, then with the response)
        r.data = object : java.io.FilterInputStream(orig) {
            override fun close() {
                try { super.close() } finally {
                    if (!once.compareAndSet(false, true)) return
                    if (!anonymous) drain(s, rest, cap, ms)
                    else if (draining.add(ip)) try { drain(s, rest, cap, ms) } finally { draining.remove(ip) }
                }
            }
        }
        return r
    }

    private val draining = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /** `PUT /api/transfer/chunk`: the first two path segments (three under /api/transfer/), letters, digits, `_` and `-` only, 64 characters at most. Never the query. */
    private fun safeRoute(method: String, uri: String): String {
        val segs = uri.split('/').filter { it.isNotEmpty() }.take(if (uri.startsWith("/api/transfer/")) 3 else 2)
        val path = segs.joinToString("/", "/") { seg -> seg.map { c -> if ((c in 'a'..'z') || (c in 'A'..'Z') || (c in '0'..'9') || c == '_' || c == '-') c else '_' }.joinToString("") }
        return "$method ${path.take(64)}"
    }

    /** Reads and drops up to min([n], [cap]) body bytes within [ms]; true = all [n] bytes were read. */
    private fun drain(s: IHTTPSession, n: Long, cap: Long, ms: Long): Boolean {
        val sock = connectionSocket.get()
        val old = runCatching { sock?.soTimeout }.getOrNull()
        val end = System.nanoTime() + ms * 1_000_000
        var left = minOf(n, cap)
        try {
            val buf = ByteArray(64 * 1024)
            while (left > 0) {
                val rem = (end - System.nanoTime()) / 1_000_000
                if (rem <= 0) break
                runCatching { sock?.soTimeout = rem.toInt() }
                val r = s.inputStream.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                if (r < 0) break
                left -= r
            }
        } catch (e: IOException) { /* the phone went away or stopped sending: nothing more to answer to */ }
        finally { if (old != null) runCatching { sock?.soTimeout = old } }
        return left == 0L && n <= cap
    }

    private fun route(s: IHTTPSession): Response {
        tokenPhone.remove()                              // a keep-alive thread serves several requests: never inherit the previous one's phone
        bodyDrained.remove()
        val p = s.parameters.mapValues { it.value.firstOrNull().orEmpty() }
        val path = s.uri
        if (hostCheck && !HostGuard.allowed(s.headers["host"])) return early("host", json(Response.Status.FORBIDDEN, """{"error":"Hôte non autorisé"}""")).also { it.addHeader("Connection", "close") }
        if (s.method == Method.GET && path == "/") return page()
        if (s.method == Method.GET && path == "/api/hello")
            return ok("""{"app":"castbridge-tv","v":${q(VERSION)},"pinRequired":${guard != null}}""")
        val isStream = (s.method == Method.GET || s.method == Method.HEAD) && path.startsWith("/stream/")
        val loopbackStream = isStream && p["t"] == streamToken && s.remoteIpAddress.let { it == "127.0.0.1" || it == "::1" || it == "0:0:0:0:0:0:0:1" }
        // The guard (trial allowlist) sees every route; only the TV's own player (loopback + run token) is let through on /stream/.
        if (!loopbackStream) routeGuard?.invoke(path)?.let { return early("trial", json(Response.Status.FORBIDDEN, """{"error":${q(it)},"trial":true}""")).also { r -> if (s.method != Method.GET) r.addHeader("Connection", "close") } }   // an unread upload body must not corrupt the next request
        publicRoutes?.serve(s)?.let { return it }
        if (!loopbackStream) denied(s, p)?.let { return it }
        if (isStream) return stream(s, path.removePrefix("/stream/"))
        val ext = if (!path.startsWith("/api/")) null
            else if (s.method == Method.POST && extension?.wantsBody(path) == true) extBody(s)?.let { extension.handleBody(path, s.method.name, p, it) }
                ?: ApiReply(413, """{"error":"body missing or too large"}""")
            else extension?.handle(path, s.method.name, p)
        return when {
            // NanoHTTPD already percent-decoded the URI. A rejected upload leaves its body unread on the
            // socket, which would corrupt the next keep-alive request: close the connection instead.
            s.method == Method.PUT && path.startsWith("/upload/") -> upload(s, path.removePrefix("/upload/"), p).also {
                if (it.status != Response.Status.OK) it.addHeader("Connection", "close")
            }
            path.startsWith("/api/transfer/") -> transfer(s, path.removePrefix("/api/transfer/"), p).also {
                if (it.status != Response.Status.OK && !(it.status.requestStatus == 429 && bodyDrained.get() == true)) it.addHeader("Connection", "close")
            }
            path == "/api/info" -> ok(info())
            path == "/api/storage" -> storage(s.method, p)
            path == "/api/storage/check" -> if (s.method == Method.GET) check(p) else json(Response.Status.METHOD_NOT_ALLOWED, """{"error":"use GET"}""")
            path == "/api/have" -> if (s.method == Method.GET) have(p) else json(Response.Status.METHOD_NOT_ALLOWED, """{"error":"use GET"}""")
            path == "/api/part" -> named(p) { name ->
                val size = p["size"]?.toLongOrNull()?.takeIf { it > 0 }
                if (findFinalOrOrigin(name, size) == null && findPart(name) == null) volumes.missingOwner(name)?.let { return@named removed() }
                ok(if (size != null) sizeChecked(partJsonFor(name, size)) else partJson(name))
            }
            path == "/api/sysinfo" -> sysinfo()
            path == "/api/library" && s.method == Method.GET -> ok(libraryJson())
            path == "/api/library/organize" -> if (s.method == Method.GET) ok(organizeJson(organizePlan(), null)) else json(Response.Status.METHOD_NOT_ALLOWED, """{"error":"use GET"}""")
            path == "/api/library/organize/apply" -> if (s.method == Method.POST) organizeApply(p["max"]?.toIntOrNull()) else json(Response.Status.METHOD_NOT_ALLOWED, """{"error":"use POST"}""")
            path == "/api/player/tracks" && s.method == Method.GET -> ok(tracksJson())
            path == "/api/thumb" && s.method == Method.GET -> named(p) { thumb(it, p["volume"]) }
            ext != null && ext.bytes != null -> newFixedLengthResponse(status(ext.status), ext.mime, java.io.ByteArrayInputStream(ext.bytes), ext.bytes.size.toLong())
                .also { it.addHeader("Cache-Control", "no-store") }
            ext != null -> json(status(ext.status), ext.json).also { if (ext.status == 413) it.addHeader("Connection", "close") }
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
                // safe=1 (the phone's assistant): never stop a playing video to rename it, refuse instead.
                val strict = p["safe"] == "1"
                busyReason(from, strict)?.let { return@named json(Response.Status.CONFLICT, """{"error":${q(it)}}""") }
                synchronized(NameSpace.lock) {
                    synchronized(FileLocks.of(LOCK_ROOT, from)) {
                        val sameEntry = src.v.fs.caseInsensitive && from.equals(to, ignoreCase = true) && from != to
                        if (!sameEntry && exists(to)) return@named json(Response.Status.CONFLICT, """{"error":"target exists"}""")
                        busyReason(from, strict)?.let { return@named json(Response.Status.CONFLICT, """{"error":${q(it)}}""") }
                        if (!strict && isPlaying(from)) player.stop()
                        val toStored = src.v.storedName(to)
                        if (!src.st.rename(src.name, toStored)) throw IOException("rename failed")
                        library?.renamed(src.name, toStored, src.size)
                        folders?.renamed(src.name, toStored)
                        (src.st as? FileStore)?.let { fs ->
                            if (src.name in Storage.playedNames(fs.dir)) { Storage.forget(fs.dir, src.name); Storage.markPlayed(fs.dir, toStored) }
                        }
                        invalidate()
                        ok(info())
                    }
                }
            }
            path == "/api/reset" -> named(p) { name ->
                // Never under a live upload of that name (another phone, or this one on another connection): a partial copy that is being written, or that was
                // written a moment ago, is not ours to drop (two phones sending the same name would erase each other for ever).
                if (partBusy(name)) return@named json(Response.Status.CONFLICT, """{"error":"busy","code":"BUSY","name":${q(name)}}""")
                synchronized(FileLocks.of(LOCK_ROOT, name)) {
                    if (partBusy(name)) return@named json(Response.Status.CONFLICT, """{"error":"busy","code":"BUSY","name":${q(name)}}""")
                    volumes.volumes().forEach { v ->
                        val st = volumes.store(v); val n = v.storedName(name)
                        st.deletePart(n); (st as? FileStore)?.let { Meta.delete(it.dir, n) }; volumes.forgetPart(v, n)
                    }
                    volumes.forgetMissing(name); meters.remove(name); invalidate()
                }
                ok(partJson(name))
            }
            path == "/api/delete" -> named(p) { name ->
                if (moving(name)) return@named json(Response.Status.CONFLICT, """{"error":"moving"}""")
                val only = p["volume"]?.takeIf { it.isNotEmpty() }
                if (isPlaying(name)) player.stop()
                synchronized(FileLocks.of(LOCK_ROOT, name)) {
                    volumes.volumes().filter { only == null || it.id == only }.forEach { v ->
                        val st = volumes.store(v); val n = v.storedName(name)
                        st.finalSize(n)?.let { sz -> library?.deleted(n, sz); folders?.deleted(n) }
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
                    growingName = null
                    val f = hit.file
                    if (f != null) { Storage.markPlayed((hit.st as? FileStore)?.dir ?: f.parentFile, f.name); player.play(f, pos) }
                    else player.playSaf(hit.name, hit.size, pos)
                    ok(info())
                } else playIncomplete(name, pos)
            }
            // "Lire en direct" from the phone's player: the TV opens a link (the phone's own little server, or a web video)
            // without storing anything. PIN-guarded like every other route; only http(s) links.
            path == "/api/playurl" -> {
                val url = p["url"].orEmpty()
                val scheme = url.substringBefore("://", "").lowercase()
                if ((scheme != "http" && scheme != "https") || url.length > 4096 || url.any { it.isWhitespace() }) return bad("http(s) url required")
                val title = safeName(p["title"].orEmpty()) ?: "Depuis le téléphone"
                playlist = null; growingName = null
                player.playStream(url, title, (p["pos"]?.toLongOrNull() ?: 0).coerceAtLeast(0))
                ok(info())
            }
            path == "/api/pause" -> { player.pause(); ok(info()) }
            path == "/api/resume" -> { player.resume(); ok(info()) }
            path == "/api/stop" -> { playlist = null; player.stop(); growingName = null; playback.refresh(); ok(info()) }
            path == "/api/seek" -> { player.seek(p["pos"]?.toLongOrNull() ?: 0); ok(info()) }
            else -> json(Response.Status.NOT_FOUND, """{"error":"not found"}""")
        }
    }

    /** Body of a POST for an extension route (a .torrent...), null if absent or larger than [MAX_EXT_BODY]. */
    private fun extBody(s: IHTTPSession): ByteArray? {
        val len = s.headers["content-length"]?.toLongOrNull() ?: return null
        if (len <= 0 || len > MAX_EXT_BODY) return null
        val b = ByteArray(len.toInt()); var off = 0
        while (off < b.size) { val r = s.inputStream.read(b, off, b.size - off); if (r < 0) return null; off += r }
        return b
    }

    private fun exists(name: String): Boolean = volumes.volumes().any { v ->
        val st = volumes.store(v); val n = v.storedName(name)
        st.finalSize(n) != null || st.partSize(n) > 0
    }

    /** Null when the request may proceed, else 401 (with Connection: close: an unread PUT body would corrupt keep-alive). */
    private fun denied(s: IHTTPSession, p: Map<String, String>): Response? {
        val g = guard ?: return null
        val ip = peers?.keyOfAddress(s.remoteIpAddress) ?: s.remoteIpAddress ?: "?"
        val tok = s.headers["x-cb-token"] ?: p["token"]
        if (tok != null && tokenAuth != null) {
            val phone = tokenAuth.invoke(tok)
            if (phone != null) {
                if (castbridge.core.trust.TvAuth.tokenMayCall(s.uri)) { tokenPhone.set(phone); return null }
                return early("pin-required", json(Response.Status.FORBIDDEN, """{"error":"pin required","message":"Cette action demande le code de la TV."}""")).also { it.addHeader("Connection", "close") }
            }
            // expired or revoked: the phone asks the TV again over Bluetooth (no PIN is tried, so no lockout is counted)
            return early("bad-token", json(Response.Status.UNAUTHORIZED, """{"error":"bad token"}""")).also { it.addHeader("Connection", "close") }
        }
        val given = s.headers["x-cb-pin"] ?: (if (legacyPinQuery) p["pin"] else null)
        return when (g.check(ip, given)) {
            PinGuard.Result.OK -> null
            PinGuard.Result.BAD -> early("bad-pin", json(Response.Status.UNAUTHORIZED, """{"error":"bad pin"}"""))
            PinGuard.Result.LOCKED -> early("pin-locked", json(Response.Status.UNAUTHORIZED,
                """{"error":"locked","retryAfter":${g.retryAfterSeconds(ip)}}"""))
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
        if (p["filing"] == "0") noFiling += name.lowercase() else noFiling -= name.lowercase()     // R-13: the phone's « classer dans des dossiers » option, per send
        findFinalOrOrigin(name, total)?.let { if (it.size == total) return ok(sizeChecked(partJson(name, total))) }
        // A finished file of that very name but another size is another file: never "done", never replaced silently (the phone says so to the user).
        if (findFinal(name)?.let { it.size != total } == true) return nameTaken(name, total)
        synchronized(FileLocks.of(LOCK_ROOT, name)) {
            if (moving(name)) return json(Response.Status.CONFLICT, """{"error":"moving"}""")
            // Same test again under the lock (a final may have appeared since): a partial copy never goes on next to a finished homonym of another size.
            if (finals(name).any { it.size != total }) return nameTaken(name, total)
            var owner = findPart(name)
            if (owner == null) volumes.missingOwner(name)?.let { return removed() }
            val cur = owner?.size ?: 0L
            // The partial copy is of the content announced when it was started: another total is another content, nothing is ever appended to it.
            owner?.let { o0 -> (o0.st as? FileStore)?.let { fs0 -> Meta.read(fs0.dir, o0.name) } }?.let { m0 ->
                if (m0 != total) return json(Response.Status.CONFLICT, partOtherJson(name, cur))
            }
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
            val pid = "put:" + name.lowercase()
            progress.begin(pid, name, total, castbridge.core.xfer.TransferProgress.Transport.WIFI, if (progress.isRunning(pid)) null else sourceOf(s), cur)
            synchronized(lock) {
                // Append as bytes arrive: whatever reached the disk before a network cut or a pulled drive is kept for resume.
                try {
                    fileStore?.let { Meta.write(it.dir, o.name, total) }   // lets /stream/ announce the final size before the last byte arrives
                    val meter = meters.getOrPut(name) { RateMeter() }
                    val input = s.inputStream
                    val out = try { st.openPart(o.name) } catch (e: IOException) { throw DiskError(e) }
                    playback.receive(name, v.id).use { rx -> out.use {
                        // Full speed: fill a large block from the socket, then one disk write (no fsync per block; a removable
                        // drive is flushed every removableSyncBytes and at the end, see commit()).
                        val buf = ByteArray(cfg.uploadBufferBytes)
                        var fill = 0
                        var left = len
                        var sinceSync = 0L
                        fun flush() {
                            if (fill == 0) return
                            val t0 = System.nanoTime()
                            try { out.write(buf, 0, fill) } catch (e: IOException) { throw DiskError(e) }
                            transfers.stats.record(fill, System.nanoTime() - t0)      // the disk speed the policy caps against, measured on this path too
                            sinceSync += fill; fill = 0
                            progress.advance(pid, len - left + cur)
                            // while a video plays the periodic fsync is spaced out (bounded: SYNC_DEFER_FACTOR x); the fsync before the commit never changes
                            val syncEvery = if (rx.decision.on) maxOf(rx.decision.syncEveryBytes, cfg.removableSyncBytes) else cfg.removableSyncBytes
                            if (v.kind == VolumeKind.REMOVABLE && sinceSync >= syncEvery) {
                                runCatching { (out as? java.io.FileOutputStream)?.fd?.sync() }; sinceSync = 0
                            }
                        }
                        try {
                            while (left > 0) {
                                if (!volumes.alive(v)) throw DiskError(IOException("volume removed"))
                                val r = input.read(buf, fill, minOf((buf.size - fill).toLong(), left, rx.readCap().toLong()).toInt())
                                if (r < 0) break
                                fill += r; left -= r
                                meter.add(r.toLong())
                                rx.onBytes(r)                        // priority and rate of « la lecture d'abord » (no-op at rest)
                                if (fill == buf.size) flush()
                            }
                        } catch (e: DiskError) { throw e } catch (e: IOException) {
                            flush()                                  // the network dropped: keep what arrived, the phone resumes from there
                            // an fsync spaced out because of playback is owed to what arrived: done as soon as playback stops
                            if (v.kind == VolumeKind.REMOVABLE && sinceSync > 0 && rx.decision.on)
                                playback.deferred.defer("sync:${v.id}:${o.name}") { if (st.partSize(o.name) > 0) st.syncPart(o.name) }
                            throw e
                        }
                        flush()
                    } }
                    if (st.partSize(o.name) == total) {
                        // Last look before the commit (which replaces a final of the same name): a finished homonym of another size is another file. Decision: the
                        // partial copy is KEPT and the phone gets 409 NAME_TAKEN (nothing is overwritten, nothing is deleted, no duplicate name is invented).
                        if (finals(name).any { it.size != total }) { progress.interrupted(pid); return nameTaken(name, total) }
                        try { st.commit(o.name) } catch (e: IOException) { throw DiskError(e) }
                        fileStore?.let { Storage.forget(it.dir, o.name); Meta.delete(it.dir, o.name) }
                        meters.remove(name); volumes.forgetPart(v, o.name)
                        // The upload replaced any older file of that name: no silent duplicate on another volume.
                        finals(name).filter { it.v.id != v.id && it.size == total && !isPlaying(name) }.forEach {
                            it.st.deleteFinal(it.name); (it.st as? FileStore)?.let { f -> Storage.forget(f.dir, it.name) }
                        }
                        // committed: the copy IS received, whatever filing then does (an IOException there must not turn it into « reprise en attente »)
                        val filed = try { fileReceived(v, st, o.name, name, total) } catch (e: IOException) { null }
                        progress.finish(pid, filed?.name)
                        onNotice(receivedNotice(name, filed)); contentIndex.poke()
                    } else {
                        progress.interrupted(pid)             // the stream ended short (clean close): the phone resumes, the sweep does not have to wait for it
                    }
                } catch (e: DiskError) {
                    progress.fail(pid, diskReason(v, e))
                    return diskFailure(v, e)
                } catch (e: IOException) {
                    progress.interrupted(pid)                 // the network dropped: the phone resumes from what arrived
                    throw e
                }
            }
            invalidate()
            return ok(partJson(name))
        }
    }

    // ---- multi-connection transfer: /api/transfer/{caps,begin,chunk,state,finish,abort} ----

    private fun transfer(s: IHTTPSession, op: String, p: Map<String, String>): Response = when {
        op == "caps" && s.method == Method.GET ->
            ok("""{"version":${castbridge.core.xfer.TransferHost.API_VERSION},"maxStreams":${transfers.allowedStreams()},"slice":${castbridge.core.xfer.Manifest.SLICE}}""")
        op == "begin" && s.method == Method.POST -> transferBegin(s, p)
        op == "chunk" && s.method == Method.PUT -> transferChunk(s, p)
        op == "state" && s.method == Method.GET -> transfers.session(p["id"].orEmpty())?.let { ok(transfers.stateJson(it, p["hashes"] == "1")) }
            ?: json(Response.Status.NOT_FOUND, """{"error":"unknown transfer"}""")
        op == "finish" && s.method == Method.POST -> transferFinish(p)
        op == "abort" && s.method == Method.POST -> { transfers.discard(p["id"].orEmpty()); progress.abort("x:" + p["id"].orEmpty(), "annulée par le téléphone"); ok("""{"ok":true}""") }
        else -> json(Response.Status.NOT_FOUND, """{"error":"not found"}""")
    }

    private fun transferBegin(s: IHTTPSession, p: Map<String, String>): Response {
        val name = safeName(p["name"].orEmpty()) ?: return bad("bad name")
        val size = p["size"]?.toLongOrNull()?.takeIf { it >= 0 } ?: return bad("size required")
        val bs = p["blockSize"]?.toIntOrNull() ?: return bad("blockSize required")
        val m = try { castbridge.core.xfer.Manifest(name, size, bs).also { require(bs <= 16 shl 20) } } catch (e: IllegalArgumentException) { return bad("bad blockSize") }
        val target = p["target"]?.takeIf { it.isNotEmpty() } ?: cfg.target
        if (!StoragePolicy.isValidTarget(target, volumes.volumes() + volumes.missingVolumes())) return bad("bad target")
        if (p["discard"] != "1") { if (p["filing"] == "0") noFiling += name.lowercase() else noFiling -= name.lowercase() }      // R-13, per send
        // A finish of this name is reading the file back under the per-name lock (minutes for a big file): never park an HTTP thread behind it (8 of
        // them and /stream/ and /api/info starve). The phone treats 5xx of begin as « resume » and waits retryMs. Looked up by id AND by name.
        if (transfers.session(m.id)?.finishing == true || transfers.finishingName(name))
            return json(SERVICE_UNAVAILABLE, """{"error":"verifying","retryMs":2000}""")
        uploading.incrementAndGet()
        try {
            synchronized(FileLocks.of(LOCK_ROOT, name)) {
                if (moving(name)) return json(Response.Status.CONFLICT, """{"error":"moving"}""")
                // discard=1: the bench's "network alone" run (bytes are hashed and dropped, nothing is stored)
                val r = if (p["discard"] == "1") transfers.beginDiscard(m) else transfers.begin(m) { mf -> allocateTransfer(mf, target) }
                return when (r) {
                    is castbridge.core.xfer.TransferHost.Begin.AlreadyThere -> ok("""{"done":true,"name":${q(name)}}""")
                    is castbridge.core.xfer.TransferHost.Begin.Refused ->
                        json(status(r.http), """{"error":${q(r.message)},"message":${q(humanRefusal(r.message))}}""")
                    is castbridge.core.xfer.TransferHost.Begin.Ok -> {
                        if (!r.s.assembler.discard) {
                            val pid = "x:" + m.id
                            progress.begin(pid, name, size, castbridge.core.xfer.TransferProgress.Transport.WIFI_MULTI, if (progress.isRunning(pid)) null else sourceOf(s), transfers.receivedBytes(r.s))
                        }
                        ok(transfers.stateJson(r.s))
                    }
                }
            }
        } finally { uploading.decrementAndGet() }
    }

    /** Picks the volume like an upload does (policy, quota, "free space after the transfer" rule); folders only (not the system picker's). */
    private fun allocateTransfer(m: castbridge.core.xfer.Manifest, target: String): castbridge.core.xfer.Allocation {
        findPart(m.name)?.let { return castbridge.core.xfer.Allocation.Refused(409, "upload in progress") }
        if (volumes.missingOwner(m.name) != null) return castbridge.core.xfer.Allocation.Refused(503, "volume removed")
        val plan = StoragePolicy.plan(volumes.snapshot(), target, m.size, TransferRule.minFree(cfg), reclaimable = ::reclaimable)
        plan.refusal?.let { r -> return castbridge.core.xfer.Allocation.Refused(r.http, if (r.http == 507) spaceMessage(r.message, target, m.size).second else r.message) }
        var why: String? = null
        for (c in plan.candidates) {
            val st = volumes.store(c.volume) as? FileStore
            if (st == null) { why = "not supported on this volume"; continue }
            val w = ensureRoom(c.volume, m.size, xferCfg)
            if (w != null) { why = w; continue }
            return castbridge.core.xfer.Allocation.At(st.dir, c.volume.storedName(m.name), c.volume.id,
                preallocate = castbridge.core.xfer.PartAssembler.preallocates(effectiveFs(c.volume)))
        }
        val reason = why ?: "no storage available"
        return if (reason == "not supported on this volume") castbridge.core.xfer.Allocation.Refused(501, reason)
        else castbridge.core.xfer.Allocation.Refused(507, spaceMessage(reason, target, m.size).second)
    }

    private fun transferChunk(s: IHTTPSession, p: Map<String, String>): Response {
        val sess = transfers.session(p["id"].orEmpty()) ?: run { rejectCode.set("session-unknown"); return json(Response.Status.NOT_FOUND, """{"error":"unknown transfer"}""") }
        val v = if (sess.assembler.discard) null else volumes[sess.volumeId]?.takeIf { volumes.alive(it) }
        if (v == null && !sess.assembler.discard) { transfers.remove(sess.manifest.id); sess.assembler.close(); progress.fail("x:" + sess.manifest.id, "support de stockage retiré"); rejectCode.set("volume-removed"); return removed() }
        val idx = p["idx"]?.toIntOrNull() ?: return bad("idx required")
        val len = s.headers["content-length"]?.toLongOrNull() ?: return bad("content-length required")
        val sha = s.headers["x-cb-sha256"].orEmpty().lowercase()
        if (idx !in 0 until sess.manifest.blocks) return bad("bad block index")
        // fewer connections while a video plays (PlaybackPriority): the extra ones are told « busy », the phone's controller backs off.
        // A block beyond the head window of a file that is not preallocated waits too (the kernel would zero-fill the gap): same answer.
        if (chunking.get() >= transfers.allowedStreams() || !transfers.mayAccept(sess.manifest.length(idx).toLong()) || !transfers.admitAhead(sess, idx))
            return busy(s, len)
        chunking.incrementAndGet()
        try {
            val a = sess.assembler
            val r = playback.receive(sess.manifest.name, v?.id).use { rx ->
                p["slice"]?.let { k -> a.writeSlice(idx, k.toIntOrNull() ?: return bad("bad slice"), sha, s.inputStream, len) }
                    ?: a.writeBlock(idx, sha, s.inputStream, len, s.headers["x-cb-enc"].equals("gzip", true), pace = rx::onBytes, readCap = rx::readCap)
            }
            return when (r) {
                is castbridge.core.xfer.PartAssembler.Block.Ok -> {
                    if (!a.discard) {
                        val pid = "x:" + sess.manifest.id
                        // the 90 s silence closed it (ABORTED) but the phone went on with chunks only: it is a live copy again (same seq, same notification)
                        if (!progress.isRunning(pid)) progress.begin(pid, sess.manifest.name, sess.manifest.size, castbridge.core.xfer.TransferProgress.Transport.WIFI_MULTI, null, transfers.receivedBytes(sess))
                        else progress.advance(pid, transfers.receivedBytes(sess))
                    }
                    ok("""{"ok":true,"done":${a.map.count()},"writeBps":${transfers.stats.bytesPerSec()}}""")
                }
                is castbridge.core.xfer.PartAssembler.Block.Already -> ok("""{"already":true}""")
                is castbridge.core.xfer.PartAssembler.Block.Corrupt -> json(status(422), """{"error":"corrupt block","idx":$idx}""")
                is castbridge.core.xfer.PartAssembler.Block.Bad -> bad(r.reason)
                is castbridge.core.xfer.PartAssembler.Block.Interrupted -> { rejectCode.set("interrupted"); json(SERVICE_UNAVAILABLE, """{"error":"interrupted","retry":true,"retryMs":1000}""") }   // a stalled read is a retry, never a refusal (a 400 stopped the copy)
                is castbridge.core.xfer.PartAssembler.Block.DiskFail -> try { progress.fail("x:" + sess.manifest.id, diskReason(v!!, DiskError(r.cause))); diskFailure(v, DiskError(r.cause)) } finally { if (!volumes.alive(v!!)) { transfers.remove(sess.manifest.id); a.close() } }
            }
        } finally { chunking.decrementAndGet() }
    }

    /** Largest body read and dropped before a 429 (a gzip block of 16 MiB at most, see the manifest limit). */
    private val maxDrainBytes = 17L shl 20
    /** R-17: unread body read and dropped after a refusal other than 429 (the status is already sent), and the time it may take. */
    private val ANON_DRAIN_CAP = 64L shl 10
    private val ANON_DRAIN_MS = 500L
    private val DRAIN_MS = 2000L
    /** The request body of this thread's request was read to the end: the answer may keep the connection alive. */
    private val bodyDrained = ThreadLocal<Boolean?>()

    /**
     * 429 « busy ». The phone sends the WHOLE block before it reads the answer: answering and closing with that body unread resets the connection
     * (the phone books a failure, not « busy »; its lanes get benched). So the body is read and dropped first (no disk, no hash, no pacing), and the
     * connection stays usable.
     */
    private fun busy(s: IHTTPSession, bodyLen: Long): Response {
        if (bodyLen in 0..maxDrainBytes) {
            try {
                val buf = ByteArray(64 * 1024); var left = bodyLen
                while (left > 0) { val r = s.inputStream.read(buf, 0, minOf(buf.size.toLong(), left).toInt()); if (r < 0) break; left -= r }
                if (left == 0L) bodyDrained.set(true)
            } catch (e: IOException) { /* the phone went away: nothing to answer to */ }
        }
        return json(status(429), """{"error":"busy","retryMs":${playback.current().busyRetryMs},"writeBps":${transfers.stats.bytesPerSec()}}""")
    }

    private fun transferFinish(p: Map<String, String>): Response {
        // The session is gone (restart, sweep, or a first finish concluded and removed it): NOT a 404, which the phone takes for a refusal and
        // gives up on. 503 makes it resume by begin, which answers « done » if the file is there, or resends what is missing.
        val sess = transfers.session(p["id"].orEmpty()) ?: return json(SERVICE_UNAVAILABLE, """{"error":"unknown transfer","retry":true}""")
        val root = p["root"].orEmpty()
        val name = sess.manifest.name
        if (sess.assembler.discard) {
            val r = sess.assembler.finish(root, "")
            if (r is castbridge.core.xfer.PartAssembler.Finish.Done) transfers.remove(sess.manifest.id)
            return if (r is castbridge.core.xfer.PartAssembler.Finish.Done) ok("""{"done":true,"discarded":true}""") else bad("incomplete")
        }
        val v = volumes[sess.volumeId]?.takeIf { volumes.alive(it) } ?: return removed()
        // A first finish is reading the file back (2 GB paced at 12 MB/s is minutes): never queue a second HTTP thread behind its lock; the phone
        // gave up after 60 s and asks again, it is told at once that the check is under way.
        if (!sess.tryBeginFinish()) return json(SERVICE_UNAVAILABLE, """{"error":"verifying","retryMs":2000}""")
        uploading.incrementAndGet()
        try {
            synchronized(FileLocks.of(LOCK_ROOT, name)) {
                // the phone gave up waiting for a long read-back and asked again: the first call may have concluded meanwhile (never a 500 on a closed file)
                if (transfers.session(sess.manifest.id) !== sess)
                    return if (findFinalOrOrigin(name, sess.manifest.size)?.size == sess.manifest.size) ok("""{"done":true,"name":${q(name)}}""")
                        else json(SERVICE_UNAVAILABLE, """{"error":"unknown transfer","retry":true}""")
                // the read-back is paced and runs at background priority while a video plays: slower, NEVER skipped (nothing is complete before it)
                val verified = playback.verify(name, v.id).use { rx -> sess.assembler.finish(root, sess.diskName, rx::onBytes) }
                return when (val r = verified) {
                    is castbridge.core.xfer.PartAssembler.Finish.Missing -> json(Response.Status.CONFLICT, transfers.stateJson(sess))
                    is castbridge.core.xfer.PartAssembler.Finish.Corrupt -> json(status(422), transfers.stateJson(sess))
                    castbridge.core.xfer.PartAssembler.Finish.RootMismatch -> bad("root mismatch")
                    is castbridge.core.xfer.PartAssembler.Finish.DiskFail -> { progress.fail("x:" + sess.manifest.id, diskReason(v, DiskError(r.cause))); diskFailure(v, DiskError(r.cause)) }
                    is castbridge.core.xfer.PartAssembler.Finish.Done -> {
                        val st = volumes.store(v); val fileStore = st as? FileStore
                        if (finals(name).any { it.size != sess.manifest.size }) return nameTaken(name, sess.manifest.size)      // never replace a homonym of another size (the parts stay)
                        try { st.commit(sess.diskName) } catch (e: IOException) { progress.fail("x:" + sess.manifest.id, diskReason(v, DiskError(e))); return diskFailure(v, DiskError(e)) }
                        fileStore?.let { Storage.forget(it.dir, sess.diskName); Meta.delete(it.dir, sess.diskName) }
                        transfers.remove(sess.manifest.id)
                        finals(name).filter { it.v.id != v.id && it.size == sess.manifest.size && !isPlaying(name) }.forEach {
                            it.st.deleteFinal(it.name); (it.st as? FileStore)?.let { f -> Storage.forget(f.dir, it.name) }
                        }
                        val filed = try { fileReceived(v, st, sess.diskName, name, sess.manifest.size) } catch (e: IOException) { null }
                        progress.finish("x:" + sess.manifest.id, filed?.name)
                        onNotice(receivedNotice(name, filed)); contentIndex.poke()
                        invalidate()
                        ok("""{"done":true,"name":${q(name)},"volume":${q(v.id)}${if (filed != null) ",\"folder\":${q(filed.folder)},\"finalName\":${q(filed.name)}" else ""}}""")
                    }
                }
            }
        } finally { sess.finishing = false; uploading.decrementAndGet() }
    }

    /** The same diagnosis as [diskFailure], in words for the TV screen (no side effect). */
    private fun diskReason(v: StorageVolume, e: DiskError): String {
        val msg = e.message.orEmpty()
        return when {
            msg == "volume removed" || !volumes.alive(v) -> "support de stockage retiré"
            msg.contains("ENOSPC") || msg.contains("No space", ignoreCase = true) -> "plus de place sur ${v.label}"
            else -> "erreur d'écriture sur ${v.label}"
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

    private fun receivedNotice(name: String, filed: Filing.Placement?): String =
        when (MediaType.of(name)) { MediaType.VIDEO -> "Vidéo reçue"; MediaType.AUDIO -> "Musique reçue"; MediaType.OTHER -> "Fichier reçu" } +
            " ✓  ${LibraryLogic.title(filed?.name ?: name)}" + (filed?.folder?.takeIf { it.isNotEmpty() }?.let { " → $it" } ?: "")

    // ---- « Ranger ma bibliothèque »: the one-off filing of the files that are still flat (docs/STORAGE.md) ----

    private val organizing = java.util.concurrent.atomic.AtomicBoolean(false)

    private class OrgPlan(val plan: Filing.Plan, val childActive: Boolean, val protectedCount: Int, val unsupported: Int)

    /**
     * Dry run: what each flat finished file would become. Nothing moves. A file the parental control protects is neither planned nor named (only counted),
     * a file in use is listed as skipped, a SAF volume (no real folders) is counted apart. Files already filed are not looked at again (idempotent).
     */
    private fun organizePlan(): OrgPlan {
        val lang = filingLang() ?: "fr"
        val flags = contentFlags
        val prot = flags?.protectedNames(libraryItems()).orEmpty()
        val files = ArrayList<Filing.PlanFile>()
        var protectedCount = 0; var unsupported = 0
        for (e in listing().entries) {
            if (!e.complete || e.folder.isNotEmpty()) continue
            val fs = volumes.store(e.v) as? FileStore
            if (fs == null) { unsupported++; continue }
            if (fs.filing.isFiled(e.name)) continue
            if (e.name in prot) { protectedCount++; continue }
            val busy = busyReason(e.name)
            files += Filing.PlanFile(e.v.id, e.name, e.size, effectiveFs(e.v), busy?.let { "occupé ($it)" })
        }
        // the same tree as a new copy (R-13): FilingPlan, so « Ranger ma bibliothèque » and the reception never disagree
        return OrgPlan(Filing.plan(files, lang, classifier = { f -> FilingPlan.plan(FilingPlan.Input(f.name, size = f.size, date = f.meta.mtimeDate, durationMs = f.meta.durationMs), lang) }, taken = ::nameTaken),
            flags?.childActive() == true, protectedCount, unsupported)
    }

    private fun organizeJson(op: OrgPlan, result: String?): String {
        val moves = op.plan.moves
        val head = "{\"canApply\":${!op.childActive},\"childActive\":${op.childActive},\"count\":${moves.size},\"summary\":${q(Filing.summary(op.plan))}," +
            "\"protected\":${op.protectedCount},\"unsupported\":${op.unsupported},"
        val by = op.plan.byCategory.entries.joinToString(",", "{", "}") { "${q(it.key)}:${it.value}" }
        val mv = moves.take(300).joinToString(",", "[", "]") {
            "{\"volume\":${q(it.volumeId)},\"from\":${q(it.from)},\"folder\":${q(it.folder)},\"to\":${q(it.to)},\"category\":${q(it.category.name.lowercase())},\"note\":${q(it.note)}}"
        }
        val sk = op.plan.skipped.take(100).joinToString(",", "[", "]") { "{\"name\":${q(it.first)},\"reason\":${q(it.second)}}" }
        return head + "\"byFolder\":$by,\"moves\":$mv,\"truncated\":${moves.size > 300},\"skipped\":$sk,\"skippedCount\":${op.plan.skipped.size}" + (result?.let { ",\"result\":$it" } ?: "") + "}"
    }

    /**
     * Applies the plan, recomputed now (nothing the phone sends decides what moves). Only renames, on the same volume, never over a file, nothing is
     * deleted (so the bin is not needed); at most [max] files per call (default 500), the answer says how many remain. Refused while a child profile is active.
     */
    private fun organizeApply(max: Int?): Response {
        val op = organizePlan()
        if (op.childActive) return json(Response.Status.FORBIDDEN, """{"error":"child active","message":"Un profil enfant est actif : le rangement est refusé."}""")
        if (!organizing.compareAndSet(false, true)) return json(Response.Status.CONFLICT, """{"error":"already running"}""")
        try {
            val limit = (max ?: 500).coerceIn(1, 2000)
            var moved = 0; var failed = 0
            val done = ArrayList<String>()
            for (m in op.plan.moves.take(limit)) {
                val v = volumes[m.volumeId]; val fs = v?.let { volumes.store(it) as? FileStore }
                if (v == null || fs == null) { failed++; continue }
                synchronized(FileLocks.of(LOCK_ROOT, m.from)) {
                    val size = fs.finalSize(m.from)
                    if (size == null || busyReason(m.from) != null) { failed++; return@synchronized }
                    // names may have been taken since the plan was made: place again from the same destination, never over anything
                    val pl = Filing.place(Filing.Result(m.category, m.folder, m.to, m.rule), effectiveFs(v), size, self = m.from, taken = ::nameTaken)
                    if (pl == null || !fs.fileInto(m.from, pl.folder, pl.name, if (pl.name.equals(m.from, ignoreCase = true)) null else m.from)) { failed++; return@synchronized }
                    folders?.set(pl.name, pl.folder)
                    if (pl.name != m.from) {
                        library?.renamed(m.from, pl.name, size)
                        if (m.from in Storage.playedNames(fs.dir)) { Storage.forget(fs.dir, m.from); Storage.markPlayed(fs.dir, pl.name) }
                    }
                    moved++
                    if (done.size < 100) done += pl.rel
                }
            }
            invalidate()
            if (moved > 0) onNotice("Bibliothèque rangée : $moved fichier${if (moved > 1) "s" else ""}")
            val remaining = (op.plan.moves.size - limit).coerceAtLeast(0)
            return ok("""{"moved":$moved,"failed":$failed,"remaining":$remaining,"files":${strs(done)}}""")
        } finally { organizing.set(false) }
    }

    private fun nameTaken(name: String, total: Long): Response {
        val have = findFinal(name)?.size ?: 0
        return json(Response.Status.CONFLICT, """{"error":"name taken, different size","code":"NAME_TAKEN","name":${q(name)},"length":0,"done":false,"existing":$have,"total":$total,""" +
            """"message":"Un autre fichier du même nom est déjà sur la TV."}""")
    }

    /** Marks an answer as given with the size taken into account: a phone only takes "done" as proof of the same file when it sees this (an older TV ignores `size`). */
    private fun sizeChecked(json: String): String = json.dropLast(1) + ""","sizeChecked":true}"""

    private fun partOtherJson(name: String, length: Long): String =
        """{"error":"part of another content","code":"PART_OTHER","name":${q(name)},"length":$length,"done":false}"""

    /**
     * The answer of GET /api/part?size=: "done" only for a finished file of exactly [size] (the file the phone is about to send); a finished homonym of another
     * size is announced as NAME_TAKEN, a partial copy started for another total as PART_OTHER (never resumed).
     */
    private fun partJsonFor(name: String, size: Long): String {
        if (findFinalOrOrigin(name, size) != null) return partJson(name, size)
        val part = findPart(name)
        if (part == null && findFinal(name) != null) return """{"name":${q(name)},"length":0,"done":false,"volume":null,"code":"NAME_TAKEN"}"""
        val meta = part?.let { h -> (h.st as? FileStore)?.let { Meta.read(it.dir, h.name) } }
        if (part != null && meta != null && meta != size) return partOtherJson(name, part.size)
        // A partial copy of a real folder without its Meta (sidecar lost): nobody can say which content it is, so it is never resumed for a known size.
        if (part != null && meta == null && part.st is FileStore) return partOtherJson(name, part.size)
        return partJson(name)
    }

    private fun partJson(name: String, size: Long? = null): String {
        findFinalOrOrigin(name, size)?.let { return """{"name":${q(name)},"length":${it.size},"done":true,"volume":${q(it.v.id)}${filedJson(it.v, it.name)}}""" }
        val part = findPart(name)
        return """{"name":${q(name)},"length":${part?.size ?: 0},"done":false,"volume":${part?.let { q(it.v.id) } ?: "null"}}"""
    }

    // /api/info is polled every second by phones and the web page: list the folders at most once per infoCacheMs.
    private class Entry(val v: StorageVolume, val name: String, val size: Long, val received: Long, val complete: Boolean, val dup: Boolean, val folder: String = "", val origin: String? = null)
    private class Listing(val at: Long, val entries: List<Entry>, val used: Map<String, Long>, val partials: List<Triple<String, Long, Long>> = emptyList()) {
        val filesJson: String by lazy {
            entries.joinToString(",", "[", "]") { e ->
                "{\"name\":${q(e.name)},\"size\":${e.size},\"received\":${e.received},\"complete\":${e.complete}," +
                    "\"volume\":${q(e.v.id)},\"duplicate\":${e.dup}" + (if (e.folder.isNotEmpty()) ",\"folder\":${q(e.folder)}" else "") + (e.origin?.let { ",\"origin\":${q(it)}" } ?: "") + "}"
            }
        }
    }
    @Volatile private var listing: Listing? = null
    private fun invalidate() { listing = null }

    /** The library changed behind the server's back (the bin, a returned drive...): the next listing is read again. */
    fun changed() = invalidate()

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
            for (e in l) if (!e.part) { raw += Entry(v, e.name, e.size, e.size, true, false, e.folder, e.origin); finalKeys += e.name.lowercase() }
        }
        // what is arriving right now, for the TV screen: EVERY partial copy with a known size, even when the same name already exists complete (an episode sent again,
        // a move target): the library listing hides those (it would show the name twice), the progress line must not
        val arriving = ArrayList<Triple<String, Long, Long>>()
        for ((v, l) in listed) for (e in l) if (e.part) {
            val total = (volumes.store(v) as? FileStore)?.let { Meta.read(it.dir, e.name) } ?: continue     // no size known: not listed
            arriving += Triple(e.name, e.size, total)
            if (e.name.lowercase() !in finalKeys) raw += Entry(v, e.name, total, e.size, false, false)
        }
        val count = raw.groupingBy { it.name.lowercase() to it.complete }.eachCount()
        val entries = raw.map { Entry(it.v, it.name, it.size, it.received, it.complete, (count[it.name.lowercase() to it.complete] ?: 1) > 1, it.folder, it.origin) }
            .sortedWith(compareBy({ it.name }, { it.v.id }))
        return Listing(now, entries, used, arriving).also { listing = it }
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
    fun receiving(): List<Triple<String, Long, Long>> = listing().partials

    /** Finished files (newest first) with what the library screens need: thumbnail, duration, resume position, volume. */
    fun libraryItems(): List<LibraryItem> {
        val l = listing()
        val lib = library
        val ps = player.state()
        val files = l.entries.filter { it.complete }.map { e ->
            val f = (volumes.store(e.v) as? FileStore)?.fileOf(e.name)
            val m = lib?.meta(e.name, e.size, f) ?: FileMeta()
            LibraryItem(e.name, e.size, f?.lastModified() ?: 0L, e.v.id, e.v.label, e.v.kind, m, e.dup,
                ps.state != "idle" && ps.name == e.name, folders?.folderOf(e.name).orEmpty().ifEmpty { e.folder })
        }
        return LibraryLogic.sortNewestFirst(files, { it.mtime }, { it.name })
    }

    fun libraryJson(): String {
        val sorted = libraryItems()
        val flags = contentFlags
        val prot = flags?.protectedNames(sorted).orEmpty()
        val head = "{" + (if (folders != null) "\"folders\":true," else "") + (if (flags == null) "" else "\"guard\":true,\"childActive\":${flags.childActive()},") + "\"files\":["
        return sorted.joinToString(",", head, "") { i ->
            val m = i.meta
            "{\"name\":${q(i.name)},\"title\":${q(i.title)},\"size\":${i.size},\"mtime\":${i.mtime}," +
                "\"volume\":${q(i.volumeId)},\"volumeLabel\":${q(i.volumeLabel)},\"kind\":${q(i.volumeKind.name.lowercase())}," +
                "\"type\":${q(i.type.name.lowercase())}," +
                "\"durationMs\":${m.durationMs},\"resumeMs\":${m.resumeMs},\"watched\":${m.watched},\"playedAt\":${m.playedAtMs}," +
                "\"hasThumb\":${m.hasThumb},\"duplicate\":${i.duplicate},\"playing\":${i.playing}" +
                (if (flags != null) ",\"protected\":${i.name in prot}" else "") + (if (folders != null) ",\"folder\":${q(i.folder)}" else "") + "}"
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
            """"player":{"state":${q(ps.state)},"name":${ps.name?.let(::q) ?: "null"},"pos":${ps.posMs},"dur":${ps.durMs}},"playlist":${playlistJson()},""" +
            """"playbackPriority":${playback.json()},"rejections":${rejectionsJson()}}"""
    }

    private fun volumesJson(l: Listing = listing()): String {
        val present = volumes.snapshot().joinToString(",") { v ->
            val used = l.used[v.id] ?: 0
            val dups = l.entries.count { it.v.id == v.id && it.dup }
            "{\"id\":${q(v.id)},\"label\":${q(v.label)},\"kind\":${q(v.kind.name.lowercase())},\"fs\":${q(v.fs.label)}," +
                "\"removable\":${v.removable},\"writable\":${v.writable},\"present\":true,\"free\":${v.free},\"total\":${v.total}," +
                "\"used\":$used,\"quota\":${quotaOf(v, used)},\"writeBps\":${v.writeBps}," +
                "\"maxFileBytes\":${if (v.maxFileBytes == Long.MAX_VALUE) -1 else v.maxFileBytes}," +
                "\"warnings\":${strs(warningsOf(v, dups) + (v.usb?.warnings().orEmpty()))},\"formatAdvice\":${formatAdvice(v)?.let(::q) ?: "null"}," +
                "\"usb\":${usbJson(v.usb)}}"
        }
        val absent = volumes.missingVolumes().joinToString(",") { v ->
            "{\"id\":${q(v.id)},\"label\":${q(v.label)},\"kind\":${q(v.kind.name.lowercase())},\"fs\":${q(v.fs.label)},\"present\":false," +
                "\"warnings\":${strs(listOf("volume absent : les envois qui y étaient en cours reprendront à son retour"))}}"
        }
        return "[" + listOf(present, absent).filter { it.isNotEmpty() }.joinToString(",") + "]"
    }

    private fun strs(l: List<String>) = l.joinToString(",", "[", "]") { q(it) }

    /** What the drive says about itself (serial in full: this API is behind the PIN / trusted-phone token). */
    private fun usbJson(u: UsbKeyInfo?): String = if (u == null) "null" else
        "{\"vendorId\":${q(u.vendorId)},\"productId\":${q(u.productId)},\"manufacturer\":${u.manufacturer?.let(::q) ?: "null"},\"product\":${u.product?.let(::q) ?: "null"}," +
            "\"serial\":${u.serial?.let(::q) ?: "null"},\"speedMbps\":${u.speedMbps},\"speed\":${q(u.speedLabel)},\"controller\":${u.controller?.let(::q) ?: "null"}," +
            "\"usbVersion\":${u.usbVersion?.let(::q) ?: "null"},\"maxPower\":${u.maxPowerMa?.let(::q) ?: "null"},\"sharesBusWithWifi\":${u.sharesBusWithWifi}," +
            "\"sharesBusWith\":${strs(u.sharesBusWith)},\"info\":${q(u.info())},\"details\":${u.details().joinToString(",", "[", "]") { (k, v) -> "{\"k\":${q(k)},\"v\":${q(v)}}" }}}"

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
            """"move":${moveJob?.json() ?: "null"},"warnings":${strs(warnings)},"heavy":${heavyJson()}}"""
    }

    /** Additive: where heavy content goes (no absolute path is ever disclosed), why, and the state of the last re-adoption. */
    private fun heavyJson(): String {
        val d = HeavyStorage.decide(volumes.snapshot(), cfg.heavyOnUsb, cfg.heavyDriveId)
        val v = d.volume
        return """{"enabled":${cfg.heavyOnUsb},"where":${q(d.where.name.lowercase())},"drive":${if (d.where == HeavyStorage.Where.USB && v != null) q(v.id) else "null"},""" +
            """"label":${v?.let { q(it.label) } ?: "null"},"root":${if (v?.heavyRoot != null) q("Download/CastBridge") else "null"},"free":${v?.let { volumes.free(it) } ?: -1},""" +
            """"writeBps":${v?.writeBps ?: 0},"warnings":${strs(d.warnings)},"readopt":${readoptJson() ?: "null"}}"""
    }

    private fun storage(method: Method, p: Map<String, String>): Response {
        if (method == Method.POST) {
            var c = cfg
            p["deleteAfterPlay"]?.let { c = c.copy(deleteAfterPlay = it == "true" || it == "1") }
            p["evictPlayed"]?.let { c = c.copy(evictPlayed = it == "true" || it == "1") }
            p["quotaMb"]?.let { v -> c = c.copy(quotaBytes = (v.toLongOrNull() ?: return bad("quotaMb must be a number")).coerceAtLeast(0) shl 20) }
            p["minFreeAfterMb"]?.let { v -> c = c.copy(minFreeAfterTransfer = (v.toLongOrNull() ?: return bad("minFreeAfterMb must be a number")).coerceIn(0, 1L shl 20) shl 20) }
            p["heavyOnUsb"]?.let { c = c.copy(heavyOnUsb = it == "true" || it == "1") }
            p["heavyDrive"]?.let { v -> if (v.isNotEmpty() && volumes.volumes().none { it.id == v && it.kind == VolumeKind.REMOVABLE }) return bad("heavyDrive must be empty or a drive id"); c = c.copy(heavyDriveId = v) }
            cfg = c; onSettings(c); invalidate()
            if (p.containsKey("heavyOnUsb") || p.containsKey("heavyDrive")) volumes.refresh()
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
            if (streamUse.busy(src.name) || streamUse.busy(name)) return json(Response.Status.CONFLICT, """{"error":"streaming"}""")
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
            // a move keeps the same "free space after the transfer" rule as an upload (1 GB by default), not only the small reserve
            roomWithoutDeleting(to, src.size - partial, false, xferCfg)?.let { return json(INSUFFICIENT_STORAGE, spaceJson(it, to)) }
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
            // a byte not there yet: wait politely while the copy is alive (up to 2 min) instead of cutting the player after 30 s
            val sock = connectionSocket.get()
            val raw = if (fs != null) fs.fileOf(src.name).let { loc -> GrowingStream(loc.parentFile ?: fs.dir, loc.name, from, to, alive = { volumes.alive(src.v) },
                    stillComing = { partBusy(name) }, maxWaitMs = STREAM_MAX_WAIT_MS, deadWaitMs = STREAM_DEAD_WAIT_MS,
                    clientGone = { hungUp(sock, s.inputStream) }) }
                else BoundedStream(src.st.open(src.name, from), to - from + 1)
            // a reader pins the file: no rename / move / bin under it. On a file still growing, at most two readers per file: a player that jumps
            // leaves its previous connection waiting; it is closed as soon as the next one opens (its thread must not sit there for minutes).
            return streamUse.track(src.name, raw, maxOpen = if (fin == null) MAX_GROWING_READERS else Int.MAX_VALUE)
        }
        fun reply(st: Response.IStatus, from: Long, to: Long): Response {
            // bytes that are not there and a copy that is dead (nobody wrote for PART_BUSY_MS): an error at once, not an endless buffering
            if (!head && fin == null && from >= src.size && !partBusy(name))
                return json(SERVICE_UNAVAILABLE, """{"error":"copy stopped","message":"La copie de ce fichier est arrêtée : reprenez l'envoi depuis le téléphone."}""")
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
        growingName = name
        player.playStream(streamUrl(name), name, pos)
        playback.refresh()
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
        /** Receptions waiting for their reader before being filed (R-13), one per volume folder. */
        const val PENDING_FILE = ".cbfiling-pending"
        const val VERSION = "0.7"
        const val MAX_EXT_BODY = 4 shl 20
        /** A partial copy written less than this long ago (or being written) is busy: /api/reset refuses to drop it. */
        const val PART_BUSY_MS = 60_000L
        /** Longest a /stream/ reader waits for a byte while the copy is still alive (then the player's http-reconnect takes over). */
        const val STREAM_MAX_WAIT_MS = 120_000L
        /** Once the copy is dead (see [PART_BUSY_MS]) a waiting reader gets its error after this long. */
        const val STREAM_DEAD_WAIT_MS = 5_000L
        /** Readers of one growing file at the same time (a seek opens a new connection while the old one closes). */
        const val MAX_GROWING_READERS = 2
        private val ADMIN_HTML: String by lazy {
            ReceiverServer::class.java.getResourceAsStream("/castbridge/admin.html")?.use { String(it.readBytes(), Charsets.UTF_8) }
                ?: "<!doctype html><meta charset=utf-8><title>CastBridge TV</title><h1>CastBridge TV</h1><p>Page d'administration indisponible.</p>"
        }
        const val SERVICE_TYPE = "_castbridge._tcp."
        private val PLAYER_ROUTES = setOf("audio", "subtitle", "subdelay", "audiodelay", "subsize", "rate", "aspect", "chapter", "title", "hw", "eq", "sleep", "loop", "mark", "picture", "night", "gain", "pitch", "substyle", "autonext", "skipstep")
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
