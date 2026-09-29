package castbridge.core.dl

import castbridge.core.tv.ApiReply
import castbridge.core.tv.StorageVolume
import castbridge.core.tv.VolumeKind
import castbridge.core.tv.VolumeRegistry
import java.io.File
import java.io.IOException
import java.security.SecureRandom
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * The TV's download manager: keeps its own list of downloads (the source of truth), has aria2 execute them, and applies
 * the TV's rules: where files go (storage policy, 1 GB kept free), pause when space runs out or the USB drive is pulled,
 * resume when it is back, and put finished files in the library. Independent of any Activity: create it once per process
 * and [start] it from whatever component hosts the app (activity or background service).
 *
 * Each download lives in its own hidden folder `<volume>/.cb-downloads/<id>` while it runs (the library lists only files
 * at the top of a volume, so half-written files and aria2's control files never show up); once complete, its videos, music,
 * subtitles and APKs are moved to the top of the same volume, which is instant (same file system).
 */
class DownloadManager(
    registry: VolumeRegistry,
    private val workDir: File,
    /** The live RPC client, null while aria2 is not running. */
    private val rpc: () -> Aria2Rpc?,
    private val engine: () -> EngineStatus,
    /** Current storage target of the app ("auto", "internal" or a volume id). */
    private val target: () -> String = { "auto" },
    private val sizeProbe: (String) -> Long? = { HttpProbe.size(it) },
    private val now: () -> Long = System::currentTimeMillis,
    private val minFree: Long = DownloadSpace.MIN_FREE,
    /** Runs file moves (tests: synchronous). */
    private val worker: (Runnable) -> Unit = defaultWorker(),
) {
    data class EngineStatus(val available: Boolean, val running: Boolean, val state: String, val message: String, val version: String? = null)

    /** A finished download, as kept in the "Terminés" list. [files] are library names on [volumeId]. */
    data class Finished(val id: String, val name: String, val files: List<String>, val volumeId: String, val volumeLabel: String,
                        val size: Long, val at: Long, val extras: Boolean)

    /** One download, as shown to people. */
    data class View(val id: String, val name: String, val kind: String, val state: DlState, val label: String, val total: Long,
                    val done: Long, val down: Long, val up: Long, val eta: Long, val connections: Int, val seeders: Int,
                    val volumeId: String, val volumeLabel: String, val error: String?, val files: Int, val source: String, val addedAt: Long)

    private class Task(
        val id: String, var gid: String?, val kind: String, val uris: List<String>, var name: String, var volumeId: String,
        val addedAt: Long, var pausedBy: PausedBy?, var size: Long?, val options: Map<String, String>, var note: String?,
        var restarts: Int, val infoHash: String?,
    ) {
        fun toMap(): Map<String, Any?> = mapOf("id" to id, "gid" to gid, "kind" to kind, "uris" to uris, "name" to name, "volume" to volumeId,
            "addedAt" to addedAt, "pausedBy" to pausedBy?.name, "size" to size, "options" to options, "note" to note,
            "restarts" to restarts, "infoHash" to infoHash)
        companion object {
            fun from(m: Map<String, Any?>) = Task(m.s("id")!!, m.s("gid"), m.s("kind") ?: "url", m["uris"].list().map { it.toString() },
                m.s("name") ?: "?", m.s("volume") ?: "internal", m.n("addedAt"),
                m.s("pausedBy")?.let { runCatching { PausedBy.valueOf(it) }.getOrNull() }, m["size"]?.let { m.n("size") },
                m["options"].obj().mapValues { it.value.toString() }, m.s("note"), m.n("restarts").toInt(), m.s("infoHash"))
        }
    }

    @Volatile private var volumes: VolumeRegistry = registry
    private val onVolume: (castbridge.core.tv.VolumeEvent) -> Unit = { ev -> if (!ev.present) worker(Runnable { runCatching { tick() } }) }

    /** The host was recreated with a new volume registry (e.g. activity recreated): follow it. */
    fun rebind(registry: VolumeRegistry) {
        if (registry === volumes) return
        volumes = registry
        registry.addListener(onVolume)
    }

    private val lock = Any()
    private val tasks = ArrayList<Task>()
    private val done = ArrayList<Finished>()
    private val moving = HashSet<String>()
    private val purge = HashSet<String>()
    @Volatile private var settings = DlSettings()
    @Volatile private var lastStatus: Map<String, Map<String, Any?>> = emptyMap()
    private var timer: ScheduledExecutorService? = null
    private val finishedListeners = CopyOnWriteArrayList<(Finished) -> Unit>()
    private val stateFile = File(workDir, "downloads.json")
    private val settingsFile = File(workDir, "download-settings.json")
    private val torrentsDir = File(workDir, "torrents")

    fun addFinishedListener(l: (Finished) -> Unit) { finishedListeners += l }
    val currentSettings: DlSettings get() = settings

    init {
        workDir.mkdirs()
        load()
        volumes.addListener(onVolume)
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Life cycle
    // ---------------------------------------------------------------------------------------------------------------

    @Synchronized fun start(periodMs: Long = 2000) {
        if (timer != null) return
        timer = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "cb-downloads").apply { isDaemon = true } }.also {
            it.scheduleWithFixedDelay({ runCatching { tick() } }, 500, periodMs, TimeUnit.MILLISECONDS)
        }
    }

    @Synchronized fun stop() { timer?.shutdownNow(); timer = null; save() }

    /** Called each time aria2 (re)starts: re-applies the settings that are not on its command line. */
    fun onEngineReady(r: Aria2Rpc) { runCatching { r.changeGlobalOption(globalOptions(settings)) } }

    private fun globalOptions(s: DlSettings) = mapOf(
        "max-overall-download-limit" to s.downLimit.toString(), "max-overall-upload-limit" to s.upLimit.toString(),
        "max-concurrent-downloads" to s.maxConcurrent.toString(),
        "seed-ratio" to if (s.seeding) Aria2Config.SEED_RATIO_ON else "0.0",
        "seed-time" to (if (s.seeding) Aria2Config.SEED_TIME_ON_MIN else 0).toString())

    // ---------------------------------------------------------------------------------------------------------------
    // Persistence (work folder: internal storage of the app, never on the USB drive)
    // ---------------------------------------------------------------------------------------------------------------

    private fun load() {
        settings = runCatching { DlSettings.parse(settingsFile.readText()) }.getOrDefault(DlSettings())
        runCatching {
            val m = Json.parse(stateFile.readText()).obj()
            synchronized(lock) {
                tasks.clear(); done.clear()
                m["tasks"].list().forEach { runCatching { tasks += Task.from(it.obj()) } }
                m["done"].list().forEach { e -> runCatching { val x = e.obj()
                    done += Finished(x.s("id")!!, x.s("name") ?: "?", x["files"].list().map { it.toString() }, x.s("volume") ?: "internal",
                        x.s("volumeLabel") ?: "", x.n("size"), x.n("at"), x.b("extras")) } }
            }
        }
    }

    private fun save() {
        val json = synchronized(lock) {
            Json.write(mapOf("tasks" to tasks.map { it.toMap() }, "done" to done.map { f ->
                mapOf("id" to f.id, "name" to f.name, "files" to f.files, "volume" to f.volumeId, "volumeLabel" to f.volumeLabel,
                    "size" to f.size, "at" to f.at, "extras" to f.extras) }))
        }
        atomicWrite(stateFile, json)
    }

    private fun saveSettings() = atomicWrite(settingsFile, settings.toJson())

    private fun atomicWrite(f: File, text: String) = runCatching {
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Periodic work: follow aria2, the space rule, the USB drive, finished downloads
    // ---------------------------------------------------------------------------------------------------------------

    fun tick() {
        val r = rpc() ?: return
        val all = try { r.tellAll() } catch (e: IOException) { return }
        val byGid = all.associateBy { it.s("gid").orEmpty() }
        lastStatus = byGid
        var dirty = false
        synchronized(lock) {
            purge.toList().forEach { g -> if (byGid[g]?.s("status").let { it == null || it == "removed" || it == "complete" || it == "error" }) {
                runCatching { r.removeDownloadResult(g) }; purge -= g } }
            for (t in tasks.toList()) {
                if (t.id in moving) continue
                val vol = volumes[t.volumeId]
                val st = t.gid?.let { byGid[it] }
                val status = st?.s("status")
                if (vol == null) {                                          // the drive holding it is gone
                    if (status == "active" || status == "waiting") runCatching { r.pause(t.gid!!) }
                    if (t.pausedBy != PausedBy.DRIVE) { t.pausedBy = PausedBy.DRIVE; dirty = true }
                    continue
                }
                if (t.pausedBy == PausedBy.DRIVE) {                          // ...and it is back
                    t.pausedBy = null; t.note = null; dirty = true
                    if (status == "paused") runCatching { r.unpause(t.gid!!) } else if (status != "active" && status != "waiting") readd(r, t, vol)
                    continue
                }
                if (st == null) {                                           // aria2 lost it (crash before its session was saved)
                    if (t.restarts < MAX_RESTARTS) { readd(r, t, vol); dirty = true }
                    continue
                }
                if (!StateMapper.isMetadata(st)) {
                    val total = st.n("totalLength")
                    if (total > 0 && t.size != total) { t.size = total; dirty = true }
                    displayName(st)?.let { if (it != t.name) { t.name = it; dirty = true } }
                }
                when (status) {
                    "complete" -> {
                        val next = st["followedBy"].list().map { it.toString() }
                        if (next.isNotEmpty()) {                            // magnet metadata / .torrent / .metalink fetched
                            val old = t.gid!!
                            t.gid = next[0]; t.pausedBy = PausedBy.CHECK; dirty = true
                            runCatching { r.removeDownloadResult(old) }
                            byGid[next[0]]?.takeIf { it.s("status") == "paused" }?.let { checkSpaceAfterMetadata(r, t, it, byGid) }
                        } else { finish(r, t, vol, st); dirty = true }
                    }
                    "paused" -> if (t.pausedBy == PausedBy.CHECK) { checkSpaceAfterMetadata(r, t, st, byGid); dirty = true }
                    "error" -> if (st.n("errorCode") in 14L..18L && !volumes.alive(vol)) { t.pausedBy = PausedBy.DRIVE; dirty = true }
                }
            }
            dirty = spaceCheck(r, byGid) || dirty
        }
        if (dirty) save()
    }

    /** Applies [DownloadSpace.check] to every download that is writing or waiting to write. */
    private fun spaceCheck(r: Aria2Rpc, byGid: Map<String, Map<String, Any?>>): Boolean {
        val order = listOf("active", "waiting", "paused")
        val running = tasks.mapNotNull { t ->
            val st = t.gid?.let { byGid[it] } ?: return@mapNotNull null
            if (volumes[t.volumeId] == null || t.id in moving) return@mapNotNull null
            val s = st.s("status")
            val writing = (s == "active" && !st.b("seeder") && !StateMapper.isMetadata(st)) || s == "waiting" ||
                (s == "paused" && t.pausedBy == PausedBy.SPACE)
            if (!writing) null else Triple(order.indexOf(s), t, DownloadSpace.Running(t.id, t.volumeId, remaining(st), t.pausedBy == PausedBy.SPACE))
        }.sortedWith(compareBy({ it.first }, { it.second.addedAt })).map { it.third }
        val d = DownloadSpace.check(running, { id -> volumes[id]?.let { volumes.free(it) } ?: -1 }, minFree)
        for (id in d.pause) tasks.firstOrNull { it.id == id }?.let { t ->
            runCatching { r.pause(t.gid!!) }
            t.pausedBy = PausedBy.SPACE
            t.note = "Mis en pause : la TV doit garder ${DownloadSpace.human(minFree)} libre. Libérez de la place, il reprendra tout seul."
        }
        for (id in d.resume) tasks.firstOrNull { it.id == id }?.let { t ->
            runCatching { r.unpause(t.gid!!) }
            t.pausedBy = null; t.note = null
        }
        return d.pause.isNotEmpty() || d.resume.isNotEmpty()
    }

    private fun remaining(st: Map<String, Any?>): Long? = st.n("totalLength").takeIf { it > 0 }?.let { (it - st.n("completedLength")).coerceAtLeast(0) }

    /** Bytes still to come on each volume, for downloads other than [except]. */
    private fun reserved(byGid: Map<String, Map<String, Any?>>, except: String? = null): Map<String, Long> =
        tasks.filter { it.id != except }.groupBy { it.volumeId }.mapValues { (_, l) ->
            l.sumOf { t -> t.gid?.let { byGid[it] }?.let { remaining(it) } ?: t.size ?: 0 }
        }

    /** The real size is now known (torrent metadata): keep the volume, move to another one, or wait for room. */
    private fun checkSpaceAfterMetadata(r: Aria2Rpc, t: Task, st: Map<String, Any?>, byGid: Map<String, Map<String, Any?>>) {
        val total = st.n("totalLength")
        val gid = t.gid!!
        if (total <= 0) { runCatching { r.unpause(gid) }; t.pausedBy = null; return }
        when (val c = DownloadSpace.choose(volumes.snapshot(), t.volumeId, total, reserved(byGid, t.id), minFree)) {
            is DownloadSpace.Choice.Ok -> {
                if (c.volume.id != t.volumeId) {
                    runCatching { r.changeOption(gid, mapOf("dir" to dirOf(c.volume, t.id).absolutePath)) }
                        .onSuccess { t.volumeId = c.volume.id; t.note = c.note }
                }
                runCatching { r.unpause(gid) }
                t.pausedBy = null
            }
            is DownloadSpace.Choice.Refused -> { t.pausedBy = PausedBy.SPACE; t.note = c.message }
        }
    }

    private fun displayName(st: Map<String, Any?>): String? {
        st["bittorrent"].obj()["info"].obj().s("name")?.takeIf { it.isNotBlank() }?.let { return it }
        val files = st["files"].list().map { it.obj() }
        if (files.size == 1) files[0].s("path")?.takeIf { it.isNotBlank() }?.let { return File(it).name }
        return null
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Finished: move to the library
    // ---------------------------------------------------------------------------------------------------------------

    private fun finish(r: Aria2Rpc, t: Task, vol: StorageVolume, st: Map<String, Any?>) {
        moving += t.id
        val gid = t.gid
        val files = st["files"].list().map { it.obj() }.filter { it.b("selected") && !it.s("path").isNullOrEmpty() }
        worker(Runnable {
            val moved = ArrayList<String>()
            var size = 0L
            val single = files.size == 1
            for (f in files) {
                val src = File(f.s("path")!!)
                if (!src.isFile || !src.canonicalPath.startsWith(dirOf(vol, t.id).canonicalPath)) continue
                if (!single && !isLibraryFile(src.name)) continue
                val dest = uniqueDest(vol, src.name)
                val ok = src.renameTo(dest) || runCatching { src.copyTo(dest); src.delete(); true }.getOrDefault(false)
                if (ok) { moved += dest.name; size += dest.length() }
            }
            val extras = tidy(dirOf(vol, t.id))
            val f = Finished(t.id, t.name, moved, vol.id, vol.label, size, now(), extras)
            synchronized(lock) {
                tasks.remove(t); moving -= t.id
                done.add(0, f)
                while (done.size > MAX_DONE) done.removeAt(done.size - 1)
                gid?.let { purge += it }
            }
            gid?.let { runCatching { r.removeDownloadResult(it) } }
            save()
            finishedListeners.forEach { l -> runCatching { l(f) } }
        })
    }

    /** Deletes aria2's leftovers in a task folder; returns true if other files (kept on purpose) remain. */
    private fun tidy(dir: File): Boolean {
        if (!dir.exists()) return false
        dir.walkBottomUp().forEach { f ->
            if (f.isFile && (f.name.endsWith(".aria2") || f.name.endsWith(".torrent") || f.name.endsWith(".meta4") || f.name.endsWith(".metalink"))) f.delete()
            if (f.isDirectory) f.delete()                                   // only succeeds when empty
        }
        return dir.exists()
    }

    private fun uniqueDest(vol: StorageVolume, raw: String): File {
        val clean = cleanName(raw)
        val dot = clean.lastIndexOf('.').takeIf { it > 0 } ?: clean.length
        var n = 1
        while (true) {
            val name = vol.storedName(if (n == 1) clean else clean.substring(0, dot) + " ($n)" + clean.substring(dot))
            val f = File(vol.dir, name)
            if (!f.exists() && !File(vol.dir, "$name.part").exists()) return f
            n++
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Actions
    // ---------------------------------------------------------------------------------------------------------------

    sealed class Result {
        data class Ok(val id: String, val note: String? = null) : Result()
        data class Refused(val http: Int, val code: String, val message: String) : Result()
    }

    /** Adds a pasted link (http/https/ftp/sftp, or magnet). [volume] = "auto" or a volume id; [options] are checked by the whitelist. */
    fun addLink(text: String, volume: String? = null, options: Map<String, String> = emptyMap()): Result {
        precheck()?.let { return it }
        val src = when (val p = LinkParser.parse(text)) {
            is LinkParser.Result.Bad -> return Result.Refused(400, "bad link", p.message)
            is LinkParser.Result.Ok -> p.source
        }
        return when (src) {
            is Source.Url -> add("url", src.uris, LinkParser.nameFromUrl(src.uris[0]) ?: LinkParser.display(src.uris[0]),
                size = if (src.uris[0].startsWith("http", true)) sizeProbe(src.uris[0]) else null, volume = volume, options = options)
            is Source.Magnet -> {
                synchronized(lock) { if (tasks.any { it.infoHash == src.infoHash }) return Result.Refused(409, "duplicate", "Ce torrent est déjà dans la liste.") }
                add("magnet", listOf(src.uri), src.name ?: "Torrent ${src.infoHash.take(8)}", src.size, volume, options, infoHash = src.infoHash)
            }
            else -> Result.Refused(400, "bad link", "Lien non pris en charge.")
        }
    }

    /** Adds an uploaded .torrent or .metalink file. */
    fun addFile(kind: String, bytes: ByteArray, volume: String? = null, options: Map<String, String> = emptyMap()): Result {
        precheck()?.let { return it }
        return when (kind) {
            "torrent" -> {
                val info = try { TorrentInfo.parse(bytes) } catch (e: Exception) { return Result.Refused(400, "bad torrent", "Ce fichier .torrent est illisible.") }
                synchronized(lock) { if (tasks.any { it.infoHash == info.infoHash }) return Result.Refused(409, "duplicate", "Ce torrent est déjà dans la liste.") }
                add("torrent", emptyList(), info.name, info.totalLength, volume, options, bytes = bytes, infoHash = info.infoHash)
            }
            "metalink" -> {
                val info = try { MetalinkInfo.parse(bytes) } catch (e: Exception) { return Result.Refused(400, "bad metalink", "Ce fichier Metalink est illisible.") }
                add("metalink", emptyList(), info.names.firstOrNull() ?: "Metalink", info.totalLength, volume, options, bytes = bytes)
            }
            else -> Result.Refused(400, "bad kind", "Type de fichier non pris en charge (.torrent ou .metalink).")
        }
    }

    private fun precheck(): Result? {
        if (!settings.warningAccepted) return Result.Refused(409, "warning", WARNING)
        val e = engine()
        if (!e.available) return Result.Refused(503, "engine", Aria2Supervisor.NOT_SHIPPED)
        if (rpc() == null) return Result.Refused(503, "engine", e.message.ifEmpty { "Le moteur de téléchargement démarre, réessayez dans un instant." })
        return null
    }

    private fun add(kind: String, uris: List<String>, name: String, size: Long?, volume: String?, options: Map<String, String>,
                    bytes: ByteArray? = null, infoHash: String? = null): Result {
        val opts = when (val c = Aria2Config.check(options, Aria2Config.TASK)) {
            is Aria2Config.Check.Bad -> return Result.Refused(400, "bad option", c.message)
            is Aria2Config.Check.Ok -> c.options
        }
        val r = rpc() ?: return Result.Refused(503, "engine", "Le moteur de téléchargement n'est pas démarré.")
        val want = volume?.takeIf { it.isNotEmpty() && it != "auto" } ?: target()
        if (volume != null && volume != "auto" && volume.isNotEmpty() && volumes[volume] == null && volume != "internal")
            return Result.Refused(400, "bad volume", "Stockage inconnu ou absent.")
        val status = runCatching { r.tellAll() }.getOrDefault(emptyList()).associateBy { it.s("gid").orEmpty() }
        val choice = synchronized(lock) { DownloadSpace.choose(volumes.snapshot(), want, size, reserved(status), minFree) }
        val vol = when (choice) {
            is DownloadSpace.Choice.Refused -> return Result.Refused(507, "space", choice.message)
            is DownloadSpace.Choice.Ok -> choice.volume
        }
        val id = newId()
        val dir = dirOf(vol, id)
        val all = opts + ("dir" to dir.absolutePath)
        if (bytes != null) { torrentsDir.mkdirs(); File(torrentsDir, "$id.$kind").writeBytes(bytes) }
        val gid = try {
            when (kind) {
                "url", "magnet" -> r.addUri(uris, all)
                "torrent" -> r.addTorrent(bytes!!, all)
                else -> r.addMetalink(bytes!!, all).first()
            }
        } catch (e: Aria2Rpc.RpcError) {
            File(torrentsDir, "$id.$kind").delete()
            return Result.Refused(400, "aria2", "Téléchargement refusé : ${e.message}")
        } catch (e: IOException) {
            File(torrentsDir, "$id.$kind").delete()
            return Result.Refused(503, "engine", "Le moteur de téléchargement ne répond pas.")
        }
        val t = Task(id, gid, kind, uris, name, vol.id, now(), null, size, opts, (choice as DownloadSpace.Choice.Ok).note, 0, infoHash)
        synchronized(lock) { tasks += t }
        save()
        return Result.Ok(id, t.note)
    }

    private fun readd(r: Aria2Rpc, t: Task, vol: StorageVolume) {
        val opts = t.options + ("dir" to dirOf(vol, t.id).absolutePath)
        t.gid?.let { g -> runCatching { r.removeDownloadResult(g) } }
        t.restarts++
        t.gid = runCatching {
            when (t.kind) {
                "url" -> r.addUri(t.uris, opts)
                "magnet" -> savedMetadata(vol, t)?.let { r.addTorrent(it.readBytes(), opts) } ?: r.addUri(t.uris, opts)
                "torrent" -> r.addTorrent(File(torrentsDir, "${t.id}.torrent").readBytes(), opts)
                else -> r.addMetalink(File(torrentsDir, "${t.id}.metalink").readBytes(), opts).first()
            }
        }.getOrElse { e -> t.note = "Impossible de relancer : ${e.message}"; null }
        t.pausedBy = null
    }

    /** aria2 saved the magnet's metadata as <infohash>.torrent in the task folder (--bt-save-metadata). */
    private fun savedMetadata(vol: StorageVolume, t: Task): File? = t.infoHash?.let { File(dirOf(vol, t.id), "$it.torrent").takeIf { f -> f.isFile } }

    private fun task(id: String): Task? = synchronized(lock) { tasks.firstOrNull { it.id == id } }

    fun pause(id: String): Result {
        val t = task(id) ?: return notFound()
        val r = rpc() ?: return engineDown()
        t.gid?.let { g -> runCatching { r.pause(g) }.onFailure { return Result.Refused(409, "state", "Ce téléchargement ne peut pas être mis en pause maintenant.") } }
        synchronized(lock) { t.pausedBy = PausedBy.USER; t.note = null }
        save(); return Result.Ok(id)
    }

    fun resume(id: String): Result {
        val t = task(id) ?: return notFound()
        val r = rpc() ?: return engineDown()
        val vol = volumes[t.volumeId] ?: return Result.Refused(503, "drive", "La clé USB de ce téléchargement est absente : rebranchez-la.")
        val st = t.gid?.let { g -> runCatching { r.tellStatus(g) }.getOrNull() }
        synchronized(lock) {
            when (st?.s("status")) {
                "paused" -> {
                    if (t.pausedBy == PausedBy.SPACE) {
                        val free = volumes.free(vol)
                        val need = remaining(st) ?: 0
                        if (free >= 0 && free - need < minFree) return Result.Refused(507, "space", t.note ?: "Pas assez de place.")
                    }
                    runCatching { r.unpause(t.gid!!) }
                }
                "error", "removed", null -> { t.restarts = 0; readd(r, t, vol) }
            }
            t.pausedBy = null; t.note = null
        }
        save(); return Result.Ok(id)
    }

    fun pauseAll(): Result { val r = rpc() ?: return engineDown(); runCatching { r.pauseAll() }
        synchronized(lock) { tasks.forEach { if (it.pausedBy == null) it.pausedBy = PausedBy.USER } }; save(); return Result.Ok("") }

    fun resumeAll(): Result {
        val ids = synchronized(lock) { tasks.filter { it.pausedBy == PausedBy.USER }.map { it.id } }
        ids.forEach { resume(it) }
        return Result.Ok("")
    }

    /** Removes a running download, or an entry of the "Terminés" list; with [deleteFiles] its files go too. */
    fun remove(id: String, deleteFiles: Boolean): Result {
        val t = task(id)
        if (t == null) {
            val f = synchronized(lock) { done.firstOrNull { it.id == id }?.also { done.remove(it) } } ?: return notFound()
            if (deleteFiles) volumes[f.volumeId]?.let { v ->
                f.files.forEach { File(v.dir, it).delete() }
                dirOf(v, f.id).deleteRecursively()
            }
            save(); return Result.Ok(id)
        }
        if (t.id in moving) return Result.Refused(409, "moving", "Rangement en cours : réessayez dans un instant.")
        val r = rpc()
        synchronized(lock) {
            tasks.remove(t)
            t.gid?.let { g -> runCatching { r?.remove(g) }; purge += g }
        }
        save()
        if (deleteFiles) worker(Runnable {
            runCatching { Thread.sleep(1500) }                             // let aria2 close the files
            volumes.volumes().filter { it.kind != VolumeKind.SAF }.forEach { dirOf(it, t.id).deleteRecursively() }
            File(torrentsDir, "${t.id}.${t.kind}").delete()
        }) else File(torrentsDir, "${t.id}.${t.kind}").delete()
        return Result.Ok(id)
    }

    fun clearDone(): Result { synchronized(lock) { done.clear() }; save(); return Result.Ok("") }

    /** [how] = top, up, down, bottom. Only downloads waiting for their turn can move. */
    fun priority(id: String, how: String): Result {
        val t = task(id) ?: return notFound()
        val r = rpc() ?: return engineDown()
        val (pos, mode) = when (how) { "top" -> 0 to "POS_SET"; "up" -> -1 to "POS_CUR"; "down" -> 1 to "POS_CUR"; "bottom" -> 0 to "POS_END"
            else -> return Result.Refused(400, "bad move", "move = top, up, down ou bottom") }
        return runCatching { r.changePosition(t.gid!!, pos, mode); Result.Ok(id) as Result }
            .getOrElse { Result.Refused(409, "state", "Seuls les téléchargements en attente peuvent changer de place.") }
    }

    fun setOptions(id: String, options: Map<String, String>): Result {
        val t = task(id) ?: return notFound()
        val r = rpc() ?: return engineDown()
        val opts = when (val c = Aria2Config.check(options, Aria2Config.TASK)) {
            is Aria2Config.Check.Bad -> return Result.Refused(400, "bad option", c.message)
            is Aria2Config.Check.Ok -> c.options
        }
        if (opts.isEmpty()) return Result.Ok(id)
        return runCatching { r.changeOption(t.gid!!, opts); Result.Ok(id) as Result }
            .getOrElse { Result.Refused(409, "aria2", "Option refusée pour ce téléchargement : ${it.message}") }
    }

    /** Chooses which files of a torrent to download (1-based indexes, as aria2 numbers them). */
    fun select(id: String, indexes: String): Result {
        val t = task(id) ?: return notFound()
        val r = rpc() ?: return engineDown()
        if (!Regex("^\\d{1,5}(,\\d{1,5})*$").matches(indexes)) return Result.Refused(400, "bad files", "files = liste de numéros, par exemple 1,3")
        val gid = t.gid ?: return notFound()
        val active = runCatching { r.tellStatus(gid, listOf("status")).s("status") }.getOrNull() == "active"
        return runCatching {
            if (active) r.pause(gid)
            r.changeOption(gid, mapOf("select-file" to indexes))
            if (active) r.unpause(gid)
            Result.Ok(id) as Result
        }.getOrElse { Result.Refused(409, "aria2", "Choix des fichiers refusé : ${it.message}") }
    }

    fun filesJson(id: String): ApiReply {
        val t = task(id) ?: return reply(notFound())
        val r = rpc() ?: return reply(engineDown())
        val base = volumes[t.volumeId]?.let { dirOf(it, t.id).absolutePath + "/" }
        val files = runCatching { r.getFiles(t.gid!!) }.getOrElse { return reply(Result.Refused(409, "state", "Liste des fichiers indisponible.")) }
        return ApiReply(200, Json.write(mapOf("id" to id, "files" to files.map { f ->
            val path = f.s("path").orEmpty()
            mapOf("index" to f.n("index"), "path" to if (base != null) path.removePrefix(base) else File(path).name,
                "length" to f.n("length"), "done" to f.n("completedLength"), "selected" to f.b("selected"))
        })))
    }

    fun peersJson(id: String): ApiReply {
        val t = task(id) ?: return reply(notFound())
        val r = rpc() ?: return reply(engineDown())
        val peers = if (t.kind == "magnet" || t.kind == "torrent") runCatching { r.getPeers(t.gid!!) }.getOrDefault(emptyList()) else emptyList()
        val servers = if (t.kind == "url" || t.kind == "metalink") runCatching { r.getServers(t.gid!!) }.getOrDefault(emptyList()) else emptyList()
        return ApiReply(200, Json.write(mapOf("id" to id,
            "peers" to peers.map { mapOf("ip" to it.s("ip"), "down" to it.n("downloadSpeed"), "up" to it.n("uploadSpeed"), "seeder" to it.b("seeder")) },
            "servers" to servers.flatMap { s -> s["servers"].list().map { x -> x.obj().let { mapOf("uri" to LinkParser.display(it.s("currentUri").orEmpty()), "down" to it.n("downloadSpeed")) } } })))
    }

    fun acceptWarning(): Result { settings = settings.copy(warningAccepted = true); saveSettings(); return Result.Ok("") }

    /** Speeds in bytes/s ("512K", "2M" accepted), seeding on/off, downloads at once. */
    fun changeSettings(p: Map<String, String>): Result {
        var s = settings
        p["downLimit"]?.let { s = s.copy(downLimit = Aria2Config.speedBytes(it) ?: return Result.Refused(400, "bad value", "downLimit invalide")) }
        p["upLimit"]?.let { s = s.copy(upLimit = Aria2Config.speedBytes(it) ?: return Result.Refused(400, "bad value", "upLimit invalide")) }
        p["seeding"]?.let { s = s.copy(seeding = it == "1" || it == "true") }
        p["maxConcurrent"]?.let { v -> s = s.copy(maxConcurrent = v.toIntOrNull()?.takeIf { it in 1..3 } ?: return Result.Refused(400, "bad value", "maxConcurrent = 1 à 3")) }
        settings = s; saveSettings()
        rpc()?.let { r -> runCatching { r.changeGlobalOption(globalOptions(s)) } }
        return Result.Ok("")
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Views
    // ---------------------------------------------------------------------------------------------------------------

    /** Current downloads with live numbers from aria2 (or the last known ones). */
    fun views(): List<View> {
        val r = rpc()
        val live = r?.let { c -> runCatching { c.tellAll().associateBy { it.s("gid").orEmpty() } }.getOrNull() }
        val byGid = live ?: lastStatus
        val running = r != null
        return synchronized(lock) {
            tasks.map { t ->
                val st = t.gid?.let { byGid[it] }
                val vol = volumes[t.volumeId]
                val state = when {
                    t.id in moving -> DlState.MOVING
                    vol == null || t.pausedBy == PausedBy.DRIVE -> DlState.WAITING_DRIVE
                    st == null -> if (running) DlState.QUEUED else DlState.PAUSED
                    else -> StateMapper.map(st, t.pausedBy)
                }
                val total = st?.n("totalLength")?.takeIf { it > 0 && !StateMapper.isMetadata(st) } ?: t.size ?: 0
                val doneBytes = if (st != null && !StateMapper.isMetadata(st)) st.n("completedLength") else 0
                val down = st?.n("downloadSpeed") ?: 0
                val error = when {
                    state == DlState.ERROR && st != null -> StateMapper.error(st.n("errorCode"), st.s("errorMessage"))
                    else -> t.note
                }
                View(t.id, t.name, t.kind, state, if (state == DlState.ERROR) "Échec" else state.label, total, doneBytes, down,
                    st?.n("uploadSpeed") ?: 0, StateMapper.eta(total, doneBytes, down), st?.n("connections")?.toInt() ?: 0,
                    st?.n("numSeeders")?.toInt() ?: 0, t.volumeId, vol?.label ?: (volumes.missingVolumes().firstOrNull { it.id == t.volumeId }?.label ?: t.volumeId),
                    error, st?.get("files").list().size, t.uris.firstOrNull()?.let(LinkParser::display) ?: t.kind, t.addedAt)
            }
        }
    }

    fun finished(): List<Finished> = synchronized(lock) { done.toList() }

    fun listJson(): String {
        val e = engine()
        val r = rpc()
        val stat = r?.let { runCatching { it.getGlobalStat() }.getOrNull() }
        val s = settings
        return Json.write(mapOf(
            "engine" to mapOf("available" to e.available, "running" to e.running, "state" to e.state, "message" to e.message, "version" to e.version),
            "warningAccepted" to s.warningAccepted,
            "warning" to WARNING,
            "global" to mapOf("down" to (stat?.n("downloadSpeed") ?: 0), "up" to (stat?.n("uploadSpeed") ?: 0),
                "downLimit" to s.downLimit, "upLimit" to s.upLimit, "seeding" to s.seeding, "maxConcurrent" to s.maxConcurrent),
            "tasks" to views().map { v ->
                mapOf("id" to v.id, "name" to v.name, "kind" to v.kind, "state" to v.state.name.lowercase(), "label" to v.label,
                    "total" to v.total, "done" to v.done, "down" to v.down, "up" to v.up, "eta" to v.eta,
                    "connections" to v.connections, "seeders" to v.seeders, "volume" to v.volumeId, "volumeLabel" to v.volumeLabel,
                    "error" to v.error, "canPause" to v.state.canPause, "canResume" to v.state.canResume,
                    "files" to v.files, "source" to v.source, "addedAt" to v.addedAt)
            },
            "done" to finished().map { f ->
                mapOf("id" to f.id, "name" to f.name, "files" to f.files, "volume" to f.volumeId, "volumeLabel" to f.volumeLabel,
                    "size" to f.size, "at" to f.at, "extras" to f.extras)
            },
        ))
    }

    // ---------------------------------------------------------------------------------------------------------------
    // HTTP relay: routes under /api/downloads, called by the CastBridge server after the PIN check
    // ---------------------------------------------------------------------------------------------------------------

    /** Handles /api/downloads* routes (null = not ours). Options of a download are passed as `opt.<name>=<value>`. */
    fun api(path: String, method: String, p: Map<String, String>): ApiReply? {
        if (path != "/api/downloads" && !path.startsWith("/api/downloads/")) return null
        val opts = p.filterKeys { it.startsWith("opt.") }.mapKeys { it.key.removePrefix("opt.") }
        val id = p["id"].orEmpty()
        return when {
            path == "/api/downloads" && method == "GET" -> ApiReply(200, listJson())
            path == "/api/downloads/about" && method == "GET" -> ApiReply(200, Json.write(mapOf("text" to ABOUT, "source" to SOURCE_URL)))
            path == "/api/downloads/files" && method == "GET" -> filesJson(id)
            path == "/api/downloads/peers" && method == "GET" -> peersJson(id)
            path == "/api/downloads/settings" && method == "GET" -> ApiReply(200, settings.toJson())
            method != "POST" -> ApiReply(405, """{"error":"use POST"}""")
            path == "/api/downloads/accept" -> reply(acceptWarning())
            path == "/api/downloads/add" -> reply(addLink(p["url"].orEmpty(), p["volume"], opts))
            path == "/api/downloads/pause" -> reply(pause(id))
            path == "/api/downloads/resume" -> reply(resume(id))
            path == "/api/downloads/remove" -> reply(remove(id, p["files"] == "1" || p["files"] == "true"))
            path == "/api/downloads/pauseall" -> reply(pauseAll())
            path == "/api/downloads/resumeall" -> reply(resumeAll())
            path == "/api/downloads/priority" -> reply(priority(id, p["move"].orEmpty()))
            path == "/api/downloads/options" -> reply(setOptions(id, opts))
            path == "/api/downloads/select" -> reply(select(id, p["files"].orEmpty()))
            path == "/api/downloads/settings" -> reply(changeSettings(p))
            path == "/api/downloads/clear" -> reply(clearDone())
            path == "/api/downloads/upload" -> ApiReply(411, """{"error":"body required"}""")
            else -> ApiReply(404, """{"error":"not found"}""")
        }
    }

    /** POST /api/downloads/upload?kind=torrent|metalink: the file is the request body (at most [MAX_UPLOAD] bytes). */
    fun apiBody(path: String, method: String, p: Map<String, String>, body: ByteArray): ApiReply? {
        if (path != "/api/downloads/upload") return null
        if (method != "POST") return ApiReply(405, """{"error":"use POST"}""")
        val kind = p["kind"] ?: when {
            p["name"].orEmpty().endsWith(".torrent", true) -> "torrent"
            p["name"].orEmpty().let { it.endsWith(".metalink", true) || it.endsWith(".meta4", true) } -> "metalink"
            else -> "torrent"
        }
        return reply(addFile(kind, body, p["volume"], p.filterKeys { it.startsWith("opt.") }.mapKeys { it.key.removePrefix("opt.") }))
    }

    fun wantsBody(path: String) = path == "/api/downloads/upload"

    /** Plug for [castbridge.core.tv.ReceiverServer]: `extension = existing.then(downloads.apiExtension)`. */
    val apiExtension: castbridge.core.tv.ApiExtension = object : castbridge.core.tv.ApiExtension {
        override fun handle(path: String, method: String, params: Map<String, String>) = api(path, method, params)
        override fun wantsBody(path: String) = this@DownloadManager.wantsBody(path)
        override fun handleBody(path: String, method: String, params: Map<String, String>, body: ByteArray) = apiBody(path, method, params, body)
    }

    private fun reply(r: Result): ApiReply = when (r) {
        is Result.Ok -> ApiReply(200, Json.write(mapOf("ok" to true, "id" to r.id, "note" to r.note)))
        is Result.Refused -> ApiReply(r.http, Json.write(mapOf("error" to r.code, "message" to r.message)))
    }

    private fun notFound() = Result.Refused(404, "not found", "Téléchargement introuvable.")
    private fun engineDown() = Result.Refused(503, "engine", engine().message.ifEmpty { "Le moteur de téléchargement n'est pas démarré." })

    companion object {
        const val DIR = ".cb-downloads"
        const val MAX_DONE = 100
        const val MAX_RESTARTS = 5
        const val MAX_UPLOAD = 4 shl 20
        const val WARNING = "Téléchargez uniquement des contenus que vous avez le droit de télécharger (œuvres libres, vos propres fichiers, " +
            "contenus achetés ou autorisés). Le partage BitTorrent envoie aussi des morceaux du fichier à d'autres personnes pendant le téléchargement."
        const val SOURCE_URL = "https://github.com/aria2/aria2/releases/tag/release-1.37.0"
        const val ABOUT = "Les téléchargements utilisent aria2 1.37.0 (https://aria2.github.io), logiciel libre sous licence GNU GPL version 2 " +
            "ou ultérieure, © Tatsuhiro Tsujikawa et contributeurs. Il est compilé depuis ses sources officielles par le script " +
            "tools/build-aria2-android.sh du dépôt CastBridge, avec OpenSSL (licence Apache 2.0), c-ares (MIT), libssh2 (BSD), " +
            "expat (MIT) et zlib (licence zlib). Le code source exact correspondant (versions et sommes SHA-256 épinglées) est " +
            "disponible : $SOURCE_URL et dans tools/build-aria2-android.sh. aria2 est fourni SANS AUCUNE GARANTIE."

        private val LIBRARY_EXT = setOf("mp4", "m4v", "mkv", "webm", "avi", "mov", "ts", "m2ts", "mts", "mpg", "mpeg", "wmv", "flv", "3gp", "ogv",
            "mp3", "m4a", "aac", "flac", "ogg", "opus", "wav", "wma", "srt", "ass", "ssa", "sub", "idx", "vtt", "apk", "jpg", "jpeg", "png")

        fun isLibraryFile(name: String) = name.substringAfterLast('.', "").lowercase() in LIBRARY_EXT

        fun dirOf(v: StorageVolume, id: String) = File(File(v.dir, DIR), id)

        /** A name the CastBridge server accepts (see ReceiverServer.safeName): no leading dot, at most 200 characters. */
        fun cleanName(raw: String): String {
            var n = raw.trim().trimStart('.').replace('/', '_').replace('\\', '_').ifEmpty { "fichier" }
            if (n.endsWith(".part") || n.endsWith(".meta")) n += "_"
            if (n.length > 200) {
                val dot = n.lastIndexOf('.')
                val ext = if (dot > 0 && n.length - dot <= 12) n.substring(dot) else ""
                n = n.substring(0, 200 - ext.length) + ext
            }
            return n
        }

        private val rnd = SecureRandom()
        fun newId(): String = "d" + ByteArray(6).also { rnd.nextBytes(it) }.joinToString("") { "%02x".format(it) }

        fun defaultWorker(): (Runnable) -> Unit {
            val ex = Executors.newSingleThreadExecutor { r -> Thread(r, "cb-downloads-io").apply { isDaemon = true } }
            return { ex.execute(it) }
        }
    }
}
