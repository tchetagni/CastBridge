package castbridge.core.tv

import castbridge.core.library.agent.SafeName
import castbridge.core.tv.ReceiverServer.Companion.q
import java.io.File
import java.io.FileOutputStream
import java.net.URLDecoder
import java.net.URLEncoder
import java.text.Normalizer

/**
 * Folders of the TV library ("Séries/Prison Break/Saison 01"), as a VIRTUAL layer (docs/LIBRARY-AGENT.md, "Dossiers sur la TV").
 *
 * The files stay exactly where they are: flat, in the app folder of their volume, under one name space. A folder is an attribute
 * of a file kept here, name -> folder path. So nothing else changes: the names used by every route (`/stream/<name>`, `/api/play`,
 * `/api/rename`, `/api/delete`, `/api/thumb`...), the sidecar files, the "played" marks, the saved positions, the thumbnails,
 * the exFAT / FAT32 limits, the SAF volumes, and the phones / web page that only know the flat list: they keep working and keep
 * seeing every file (`/api/library` only gains a `folder` field they ignore).
 *
 * One small text file, rewritten atomically (temp file, sync, rename): a cut leaves the old or the new version, never half.
 * A folder exists as long as a file is in it (no empty folders). Entries of files that no longer exist are pruned when read.
 * Moving a file between volumes keeps its name, so it keeps its folder. A file in the bin keeps its folder in a "stash" so
 * that restoring puts it back.
 */
class FolderIndex(private val file: File?, private val maxEntries: Int = 20_000, private val maxStash: Int = 2_000) {
    private val map = LinkedHashMap<String, String>()
    private val stash = LinkedHashMap<String, String>()

    init { load() }

    /** Folder of [name], "" = root. */
    @Synchronized fun folderOf(name: String): String = map[name] ?: ""

    /** What a folder path becomes: trimmed parts, Unicode NFC, same case as an existing folder; or a French reason. */
    sealed class Path { data class Ok(val path: String) : Path(); data class Bad(val reason: String) : Path() }

    @Synchronized fun normalize(raw: String): Path {
        if (raw.trim().startsWith("/") || raw.contains('\\')) return Path.Bad("chemin absolu ou séparateur interdit")
        val parts = raw.split('/').map { Normalizer.normalize(it.trim(), Normalizer.Form.NFC) }.filter { it.isNotEmpty() }
        val path = parts.joinToString("/")
        SafeName.checkFolder(path)?.let { return Path.Bad(it) }
        // "séries" typed after "Séries" exists: same folder
        val known = map.values.toSet()
        val canon = known.firstOrNull { it.equals(path, ignoreCase = true) } ?: path
        return Path.Ok(canon)
    }

    /** Puts [name] in [folder] ("" = back to the root). Returns the normalised folder, or the reason it was refused. */
    @Synchronized fun set(name: String, folder: String): Path {
        val p = normalize(folder)
        if (p !is Path.Ok) return p
        if (p.path.isEmpty()) map.remove(name) else {
            if (map[name] != p.path && map.size >= maxEntries && name !in map) return Path.Bad("trop de fichiers rangés dans des dossiers")
            map[name] = p.path
        }
        save()
        return p
    }

    @Synchronized fun renamed(from: String, to: String) {
        val f = map.remove(from) ?: return
        map[to] = f; save()
    }

    @Synchronized fun deleted(name: String) { if (map.remove(name) != null) save() }

    /** The file goes to the bin: its folder is remembered under the bin id. */
    @Synchronized fun stash(id: String, name: String) {
        val f = map.remove(name) ?: return
        stash[id] = f
        while (stash.size > maxStash) stash.remove(stash.keys.first())
        save()
    }

    @Synchronized fun unstash(id: String): String? = stash.remove(id)?.also { save() }

    /** Renames a folder (and everything under it). Merging into an existing folder is fine: it is only a label. */
    @Synchronized fun renameFolder(from: String, to: String): Path {
        val src = normalize(from); val dst = normalize(to)
        if (src !is Path.Ok) return src
        if (dst !is Path.Ok) return dst
        if (src.path.isEmpty()) return Path.Bad("le dossier racine n'a pas de nom")
        fun under(p: String) = p.equals(src.path, true) || p.startsWith(src.path + "/", true)
        var moved = 0
        for ((n, p) in map.entries.toList()) if (under(p)) {
            val rest = p.substring(src.path.length)
            val np = dst.path + rest
            SafeName.checkFolder(np)?.let { return Path.Bad(it) }
            map[n] = np; moved++
        }
        if (moved > 0) save()
        return dst
    }

    /** Pruned to [existing] names: (folder path, number of files), sorted. */
    @Synchronized fun folders(existing: Set<String>): List<Pair<String, Int>> {
        prune(existing)
        return map.values.groupingBy { it }.eachCount().toList().sortedBy { it.first.lowercase() }
    }

    @Synchronized fun prune(existing: Set<String>) {
        val gone = map.keys.filter { it !in existing }
        if (gone.isEmpty()) return
        gone.forEach { map.remove(it) }; save()
    }

    @Synchronized fun size() = map.size

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private fun dec(s: String) = URLDecoder.decode(s, "UTF-8")

    private fun load() {
        val f = file ?: return
        val lines = runCatching { f.readLines() }.getOrNull() ?: return
        for (l in lines) {
            val p = l.split('\t')
            if (p.size < 3) continue
            runCatching { if (p[0] == "f") map[dec(p[1])] = dec(p[2]) else if (p[0] == "t") stash[dec(p[1])] = dec(p[2]) }
        }
    }

    private fun save() {
        val f = file ?: return
        runCatching {
            f.parentFile?.mkdirs()
            val tmp = File(f.parentFile, f.name + ".tmp")
            FileOutputStream(tmp).use { o ->
                o.write((map.entries.joinToString("") { (k, v) -> "f\t${enc(k)}\t${enc(v)}\n" } + stash.entries.joinToString("") { (k, v) -> "t\t${enc(k)}\t${enc(v)}\n" }).toByteArray())
                o.fd.sync()
            }
            if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
        }
    }
}

/**
 * Routes of the folders (behind the PIN like every /api/ route). A change of folder moves no byte: it is always safe, even for a
 * file that is playing or being sent.
 *
 *  GET  /api/folders                          [{"path","count"}] of the folders in use
 *  POST /api/folders/set?name=&folder=        put a file in a folder ("" = the root)
 *  POST /api/folders/rename?from=&to=         rename a folder (and what is under it)
 */
class FoldersApi(private val index: FolderIndex, private val names: () -> Set<String>) : ApiExtension {
    override fun handle(path: String, method: String, params: Map<String, String>): ApiReply? {
        if (path != "/api/folders" && !path.startsWith("/api/folders/")) return null
        return try {
            when (path) {
                "/api/folders" -> if (method == "GET") list() else err(405, "use GET")
                "/api/folders/set" -> if (method != "POST") err(405, "use POST") else set(params["name"].orEmpty(), params["folder"].orEmpty())
                "/api/folders/rename" -> if (method != "POST") err(405, "use POST") else rename(params["from"].orEmpty(), params["to"].orEmpty())
                else -> err(404, "not found")
            }
        } catch (e: Exception) { err(500, e.message ?: e.javaClass.simpleName) }
    }

    private fun err(status: Int, msg: String) = ApiReply(status, """{"error":${q(msg)}}""")

    private fun list(): ApiReply = ApiReply(200, index.folders(names()).joinToString(",", "{\"folders\":[", "]}") { (p, n) -> """{"path":${q(p)},"count":$n}""" })

    private fun set(name: String, folder: String): ApiReply {
        val n = ReceiverServer.safeName(name) ?: return err(400, "bad name")
        if (n !in names()) return err(404, "not found")
        return when (val r = index.set(n, folder)) {
            is FolderIndex.Path.Ok -> ApiReply(200, """{"name":${q(n)},"folder":${q(r.path)}}""")
            is FolderIndex.Path.Bad -> err(400, r.reason)
        }
    }

    private fun rename(from: String, to: String): ApiReply = when (val r = index.renameFolder(from, to)) {
        is FolderIndex.Path.Ok -> ApiReply(200, """{"folder":${q(r.path)}}""")
        is FolderIndex.Path.Bad -> err(400, r.reason)
    }
}
