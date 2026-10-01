package castbridge.core.library.agent

/**
 * A folder of the phone reached through the Android file picker (Storage Access Framework), seen as a plain tree of documents.
 *
 * The Android part ([castbridge.sender.agent.SafLibrary]) is a thin adapter over `DocumentsContract`; everything that decides
 * (walking, name collisions, trash, restore, bookkeeping of ids that change at every rename) lives HERE, in pure Kotlin, so that it is
 * tested on the JVM against a simulated provider ([castbridge.core.library.agent.FakeDocProvider] in the tests), including the awkward
 * providers: no "move" support, a provider that silently renames, case-insensitive names, an SD card volume that disappears.
 */
data class DocEntry(val id: String, val name: String, val isDir: Boolean, val size: Long = 0, val mtime: Long = 0)

interface DocProvider {
    /** Id of the picked folder. */
    val rootId: String

    /** Children of a folder. Empty when the folder cannot be read (volume removed, permission revoked). */
    fun children(parentId: String): List<DocEntry>

    /** One document, read fresh from the provider (ids change after a rename or a move on most providers). Null when it is gone. */
    fun info(id: String): DocEntry?

    fun createDir(parentId: String, name: String): DocEntry?

    /** Renames in place. Returns the entry as it is AFTER (the provider may have changed the name or the id); null when refused. */
    fun rename(id: String, newName: String): DocEntry?

    /** Moves to another folder of the same tree. Returns the entry after; null when the provider cannot or will not (nothing is lost then). */
    fun move(id: String, fromParentId: String, toParentId: String): DocEntry?

    /** Real deletion. Only called after an explicit confirmation of the user (emptying the bin). */
    fun delete(id: String): Boolean

    /** Duration of a video or audio file, read from its header; 0 when unknown. */
    fun durationMs(id: String): Long

    /** Label, free space and size of the volume the folder lives on (internal memory, SD card, USB drive). */
    fun volume(): VolumeInfo
}

/** Keeps durations (slow to read: one header per file) between two analyses: the second analysis of a folder only reads what is new. */
interface DurationCache {
    fun get(key: String): Long?
    fun put(key: String, durationMs: Long)
    companion object { val NONE = object : DurationCache { override fun get(key: String): Long? = null; override fun put(key: String, durationMs: Long) {} } }
}

/** What a reader reports while it walks the folder: the files found so far (for the "au fil de l'eau" preview). */
class WalkProgress(val filesFound: Int, val foldersRead: Int, val latest: List<FileRef>)

class DocTreeLibrary(
    private val p: DocProvider,
    private val durations: DurationCache = DurationCache.NONE,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val dirCache = HashMap<String, MutableList<DocEntry>>()
    private val dirStamp = HashMap<String, Long>()
    private val folderIds = HashMap<String, String>()          // relative folder path, lower-case -> id ("" = root)

    @Synchronized private fun list(id: String, fresh: Boolean = false): MutableList<DocEntry> {
        val t = now()
        if (!fresh) dirCache[id]?.let { if (t - (dirStamp[id] ?: 0) < MAX_AGE_MS) return it }
        val l = p.children(id).toMutableList()
        dirCache[id] = l; dirStamp[id] = t
        return l
    }

    @Synchronized private fun forgetAll() { dirCache.clear(); dirStamp.clear(); folderIds.clear() }

    @Synchronized private fun folderId(folder: String): String? {
        if (folder.isEmpty()) return p.rootId
        folderIds[folder.lowercase()]?.let { return it }
        var id = p.rootId
        var path = ""
        for (seg in folder.split('/')) {
            path = if (path.isEmpty()) seg else "$path/$seg"
            val known = folderIds[path.lowercase()]
            id = known ?: (list(id).firstOrNull { it.isDir && it.name.equals(seg, ignoreCase = true) }?.id ?: return null)
            folderIds[path.lowercase()] = id
        }
        return id
    }

    @Synchronized private fun folderIdOrNull(path: String): String? = folderId(path)

    private fun child(parentId: String, name: String): DocEntry? = list(parentId).firstOrNull { it.name.equals(name, ignoreCase = true) }

    private fun find(loc: Loc): DocEntry? = folderId(loc.folder)?.let { f -> list(f).firstOrNull { !it.isDir && it.name.equals(loc.name, ignoreCase = true) } }

    // ------------------------------------------------------------------ reading

    /**
     * Walks the folder (metadata only). [onProgress] is called after each folder with the files found so far, so that the screen can show
     * results while the walk goes on. Durations come from [DurationCache] when known; at most [maxNewDurations] new ones are read per
     * walk, so that a second analysis continues where the first stopped (incremental) instead of starting again.
     */
    fun snapshot(maxFiles: Int = 20_000, maxNewDurations: Int = 300, withDurations: Boolean = true, onProgress: (WalkProgress) -> Unit = {}, cancelled: () -> Boolean = { false }): LibrarySnapshot {
        forgetAll()
        val files = ArrayList<FileRef>()
        var folders = 0
        var reportedAt = 0
        fun walk(id: String, path: String, depth: Int) {
            if (depth > 6 || files.size >= maxFiles || cancelled()) return
            val kids = list(id, fresh = true)
            folders++
            val before = files.size
            for (d in kids) {
                if (d.name.startsWith(".")) continue
                if (d.isDir) continue
                if (files.size < maxFiles) files += FileRef(Origin.PHONE, d.name, d.size, d.mtime, "phone", path)
            }
            if (files.size - reportedAt >= 25 || folders == 1) { reportedAt = files.size; onProgress(WalkProgress(files.size, folders, files.subList(before, files.size).toList())) }
            for (d in kids) if (d.isDir && !d.name.startsWith(".") && !(depth == 0 && d.name == TRASH_NAME)) walk(d.id, if (path.isEmpty()) d.name else "$path/${d.name}", depth + 1)
        }
        walk(p.rootId, "", 0)
        onProgress(WalkProgress(files.size, folders, emptyList()))
        var fresh = 0
        val withDur = if (!withDurations) files else files.map { f ->
            if (cancelled() || f.size < MIN_DURATION_BYTES || !isMedia(f.name)) return@map f
            val key = durationKey(f)
            val known = durations.get(key)
            when {
                known != null -> if (known > 0) f.copy(durationMs = known) else f
                fresh >= maxNewDurations -> f
                else -> {
                    fresh++
                    val d = find(f.loc)?.let { p.durationMs(it.id) } ?: 0L
                    durations.put(key, d)
                    if (d > 0) f.copy(durationMs = d) else f
                }
            }
        }
        return LibrarySnapshot(Origin.PHONE, withDur, listOf(p.volume()), now())
    }

    /** True while some media files still have an unknown duration (a following analysis will read more). */
    fun durationKey(f: FileRef) = "${f.folder}/${f.name}|${f.size}|${f.mtime}"

    private fun isMedia(n: String) = NameParser.mediaOf(n.substringAfterLast('.', "")).let { it == Media.VIDEO || it == Media.AUDIO }

    // ------------------------------------------------------------------ operations (the executor's view of the phone)

    inner class Ops : LibraryOps {
        override val folders = true
        override fun volumes() = listOf(p.volume())
        override fun stat(loc: Loc): Stat? {
            val e = find(loc) ?: return null
            // the size is read fresh from the provider (one row), not from the listing, so that "the file changed since the analysis" is exact
            val live = p.info(e.id) ?: run { synchronized(this@DocTreeLibrary) { folderId(loc.folder)?.let { dirCache.remove(it) } }; return null }
            return Stat(live.size, live.mtime)
        }
        override fun isPlaying(loc: Loc) = false

        override fun mkdirs(volume: String, folder: String): OpResult {
            var id = p.rootId
            var path = ""
            for (seg in folder.split('/').filter { it.isNotEmpty() }) {
                path = if (path.isEmpty()) seg else "$path/$seg"
                val ex = child(id, seg)
                id = if (ex != null && ex.isDir) ex.id else {
                    if (ex != null) return OpResult.Fail("« $seg » existe déjà et n'est pas un dossier")
                    val made = p.createDir(id, seg) ?: return OpResult.Fail("création du dossier « $seg » refusée")
                    synchronized(this@DocTreeLibrary) { list(id).add(made) }
                    made.id
                }
                synchronized(this@DocTreeLibrary) { folderIds[path.lowercase()] = id }
            }
            return OpResult.Ok(Loc(volume, folder, ""))
        }

        override fun rename(loc: Loc, newName: String): OpResult {
            val e = find(loc) ?: return OpResult.Fail("introuvable")
            val parent = folderId(loc.folder) ?: return OpResult.Fail("dossier introuvable")
            val taken = child(parent, newName)
            if (taken != null && taken.id != e.id) return OpResult.Fail("le nom existe déjà")
            val after = p.rename(e.id, newName) ?: return OpResult.Fail("renommage refusé par le système")
            synchronized(this@DocTreeLibrary) { val l = list(parent); l.removeAll { it.id == e.id }; l.add(after) }
            // some providers silently change the name (a number appended…): report what really happened, the journal must know it
            return OpResult.Ok(loc.copy(name = after.name))
        }

        override fun moveToFolder(loc: Loc, folder: String): OpResult {
            val e = find(loc) ?: return OpResult.Fail("introuvable")
            val src = folderId(loc.folder) ?: return OpResult.Fail("dossier d'origine introuvable")
            val dst = folderId(folder) ?: return OpResult.Fail("dossier de destination introuvable")
            if (child(dst, loc.name) != null) return OpResult.Fail("le nom existe déjà dans le dossier")
            val after = p.move(e.id, src, dst) ?: return OpResult.Fail("ce dossier ne permet pas de déplacer des fichiers : rien n'a changé")
            synchronized(this@DocTreeLibrary) { list(src).removeAll { it.id == e.id }; dirCache[dst]?.add(after) }
            return OpResult.Ok(Loc(loc.volume, folder, after.name))
        }

        override fun moveToVolume(loc: Loc, toVolume: String, onProgress: (Long, Long) -> Unit, cancelled: () -> Boolean): OpResult =
            OpResult.Fail("un dossier du téléphone est sur un seul volume : choisissez le dossier de la carte SD (ou de la clé) pour la ranger à part")

        private fun trashId(create: Boolean = true): String? {
            val ex = child(p.rootId, TRASH_NAME)
            if (ex != null) return if (ex.isDir) ex.id else null
            if (!create) return null
            val made = p.createDir(p.rootId, TRASH_NAME) ?: return null
            synchronized(this@DocTreeLibrary) { list(p.rootId).add(made) }
            return made.id
        }

        private fun splitExt(n: String): Pair<String, String> {
            val dot = n.lastIndexOf('.')
            return if (dot > 0 && n.length - dot <= 6) n.substring(0, dot) to n.substring(dot) else n to ""
        }

        override fun trash(loc: Loc): OpResult {
            var e = find(loc) ?: return OpResult.Fail("introuvable")
            val src = folderId(loc.folder) ?: return OpResult.Fail("dossier d'origine introuvable")
            val bin = trashId() ?: return OpResult.Fail("impossible de créer « $TRASH_NAME »")
            // a name already in the bin: make ours unique first (the journal keeps the original name and the bin name)
            var tn = loc.name
            if (child(bin, tn) != null) {
                val (b, x) = splitExt(tn)
                var i = 2
                while (child(bin, "$b ($i)$x") != null) i++
                tn = "$b ($i)$x"
                val r = rename(loc, tn)
                if (r is OpResult.Fail) return r
                tn = (r as OpResult.Ok).loc.name
                e = find(loc.copy(name = tn)) ?: return OpResult.Fail("introuvable")
            }
            val after = p.move(e.id, src, bin)
            if (after == null) {
                // undo our own renaming: nothing may be left half done
                if (tn != loc.name) rename(loc.copy(name = tn), loc.name)
                return OpResult.Fail("ce dossier ne permet pas de déplacer des fichiers : rien n'a été supprimé")
            }
            synchronized(this@DocTreeLibrary) { list(src).removeAll { it.id == e.id }; dirCache[bin]?.add(after) }
            return OpResult.Ok(loc, after.name)
        }

        override fun restore(trashId: String, original: Loc): OpResult {
            val bin = trashId() ?: return OpResult.Fail("corbeille introuvable")
            val d = child(bin, trashId) ?: return OpResult.Fail("n'est plus dans la corbeille")
            val dst = folderId(original.folder) ?: (mkdirs(original.volume, original.folder).let { folderId(original.folder) }) ?: return OpResult.Fail("dossier d'origine introuvable")
            synchronized(this@DocTreeLibrary) { list(dst, fresh = true) }          // a restoration is rare: look at the real content of the folder
            val (b, x) = splitExt(original.name)
            var name = original.name
            var n = 1
            while (child(dst, name) != null) { n++; name = if (n == 2) "$b (restauré)$x" else "$b (restauré $n)$x" }
            // moved back under its bin name when that is free, then given its own name; if the bin name is taken there, rename first inside the bin
            var cur = d
            if (child(dst, cur.name) != null) {
                val tmp = "$b (restauré ${System.nanoTime() % 100000})$x"
                cur = p.rename(cur.id, tmp) ?: return OpResult.Fail("restauration refusée par le système")
                synchronized(this@DocTreeLibrary) { val l = list(bin); l.removeAll { it.id == d.id }; l.add(cur) }
            }
            val moved = p.move(cur.id, bin, dst) ?: return OpResult.Fail("restauration refusée par le système")
            synchronized(this@DocTreeLibrary) { list(bin).removeAll { it.id == cur.id }; dirCache[dst]?.add(moved) }
            var finalName = moved.name
            if (finalName != name) {
                val after = p.rename(moved.id, name)
                if (after != null) { finalName = after.name; synchronized(this@DocTreeLibrary) { val l = list(dst); l.removeAll { it.id == moved.id }; l.add(after) } }
            }
            return OpResult.Ok(Loc(original.volume, original.folder, finalName))
        }

        override fun findInTrash(original: Loc): String? = trashId(create = false)?.let { child(it, original.name)?.name }

        /** What is in the bin (for the "Corbeille" screen): name and size. */
        fun binItems(): List<Pair<String, Long>> = trashId(create = false)?.let { b -> list(b, fresh = true).filter { !it.isDir }.map { it.name to it.size } }.orEmpty()

        /**
         * Removes folders that are EMPTY (deepest first, never the picked folder, never one with anything inside): the folders a rangement created
         * and an undo emptied ("Séries/Narcos/Saison 01"), and the bin when nothing is left in it. Returns how many were removed.
         */
        fun pruneEmpty(folders: Collection<String>, includeBin: Boolean = true): Int {
            var n = 0
            val paths = LinkedHashSet<String>()
            for (f in folders) { var cur = f; while (cur.isNotEmpty()) { paths += cur; cur = if ('/' in cur) cur.substringBeforeLast('/') else "" } }
            if (includeBin) paths += TRASH_NAME
            for (path in paths.sortedByDescending { it.count { c -> c == '/' } }) {
                val id = synchronized(this@DocTreeLibrary) { folderIdOrNull(path) } ?: continue
                if (list(id, fresh = true).isEmpty() && p.delete(id)) {
                    n++
                    synchronized(this@DocTreeLibrary) { dirCache.remove(id); folderIds.remove(path.lowercase()); val parent = folderIdOrNull(path.substringBeforeLast('/', "")); parent?.let { dirCache.remove(it) } }
                }
            }
            return n
        }

        /** Really deletes one item of the bin: only ever called after the user's explicit confirmation. */
        fun purge(name: String): Boolean {
            val b = trashId(create = false) ?: return false
            val d = child(b, name) ?: return false
            val ok = p.delete(d.id)
            if (ok) synchronized(this@DocTreeLibrary) { list(b).removeAll { it.id == d.id } }
            return ok
        }
    }

    companion object {
        const val TRASH_NAME = "Corbeille CastBridge"
        const val MAX_AGE_MS = 5_000L
        /** Reading a header costs a system call per file: small files are not worth it. */
        const val MIN_DURATION_BYTES = 5L shl 20
    }
}
