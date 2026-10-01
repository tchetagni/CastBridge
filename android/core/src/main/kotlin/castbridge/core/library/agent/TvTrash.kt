package castbridge.core.library.agent

import castbridge.core.tv.ApiExtension
import castbridge.core.tv.ApiReply
import castbridge.core.tv.FileLocks
import castbridge.core.tv.FileStore
import castbridge.core.tv.LibraryMeta
import castbridge.core.tv.ReceiverServer
import castbridge.core.tv.ReceiverServer.Companion.q
import castbridge.core.tv.Storage
import castbridge.core.tv.StorageVolume
import castbridge.core.tv.VolumeRegistry
import java.io.File

/**
 * "Corbeille CastBridge" on the TV: a recoverable bin, never a real delete. A file "put" in the trash is MOVED (an atomic rename on
 * the same volume) into a hidden folder `.castbridge-trash` of its own volume, where the library does not list it, and can be
 * restored for [retentionMs] (30 days). Items older than that are removed the next time the bin is looked at.
 *
 * Routes (behind the PIN like every /api/ route; the phone's assistant only calls them for changes the user ticked and confirmed):
 *
 *  GET  /api/trash                         what is in the bin (id, name, volume, size, when, until when)
 *  POST /api/trash/put?name=[&volume=]     move a finished file into the bin  (409 while it is playing / being moved)
 *  POST /api/trash/restore?id=             take an item back to its volume (another name if the old one is taken: never an overwrite)
 *  POST /api/trash/purge?id=               really delete ONE item (explicit, after the user's confirmation)
 *  POST /api/trash/empty                   really delete everything in the bin (explicit, after the user's confirmation)
 *
 * The existing routes cover the rest: /api/rename (rename), /api/storage/move (move to another volume). The TV library is flat:
 * there is no "create folder" route.
 */
class TrashApi(
    private val volumes: VolumeRegistry,
    /** Name of the file the TV player is playing now (null = nothing). */
    private val playing: () -> String? = { null },
    private val library: LibraryMeta? = null,
    private val now: () -> Long = System::currentTimeMillis,
    private val retentionMs: Long = RETENTION_MS,
    /** True while [name] is being copied to another volume. */
    private val busy: (String) -> Boolean = { false },
) : ApiExtension {

    private data class Item(val id: String, val name: String, val volume: StorageVolume, val file: File, val at: Long, val size: Long)

    override fun handle(path: String, method: String, params: Map<String, String>): ApiReply? {
        if (path != "/api/trash" && !path.startsWith("/api/trash/")) return null
        return try {
            when (path) {
                "/api/trash" -> if (method == "GET") list() else err(405, "use GET")
                "/api/trash/put" -> post(method) { put(params["name"].orEmpty(), params["volume"]) }
                "/api/trash/restore" -> post(method) { restore(params["id"].orEmpty()) }
                "/api/trash/purge" -> post(method) { purge(params["id"].orEmpty()) }
                "/api/trash/empty" -> post(method) { empty() }
                else -> err(404, "not found")
            }
        } catch (e: Exception) { err(500, e.message ?: e.javaClass.simpleName) }
    }

    private inline fun post(method: String, f: () -> ApiReply) = if (method == "POST") f() else err(405, "use POST")
    private fun err(status: Int, msg: String) = ApiReply(status, """{"error":${q(msg)}}""")

    private fun dirOf(v: StorageVolume): File? = (volumes.store(v) as? FileStore)?.dir?.takeIf { it.isDirectory }
    private fun binOf(v: StorageVolume): File? = dirOf(v)?.let { File(it, BIN) }

    private fun items(purgeExpired: Boolean): List<Item> {
        val out = ArrayList<Item>()
        for (v in volumes.volumes()) {
            val bin = binOf(v) ?: continue
            for (f in bin.listFiles().orEmpty()) {
                if (!f.isFile) continue
                val parsed = parseStored(f.name) ?: continue
                if (purgeExpired && now() - parsed.second > retentionMs) { f.delete(); continue }
                out += Item(parsed.first, parsed.third, v, f, parsed.second, f.length())
            }
        }
        return out.sortedByDescending { it.at }
    }

    private fun list(): ApiReply {
        val l = items(purgeExpired = true)
        return ApiReply(200, "{\"retentionDays\":${retentionMs / 86_400_000L},\"count\":${l.size},\"bytes\":${l.sumOf { it.size }},\"items\":" + l.joinToString(",", "[", "]") {
            "{\"id\":${q(it.id)},\"name\":${q(it.name)},\"volume\":${q(it.volume.id)},\"size\":${it.size},\"trashedAt\":${it.at},\"expiresAt\":${it.at + retentionMs}}"
        } + "}")
    }

    private fun put(name: String, volume: String?): ApiReply {
        val n = ReceiverServer.safeName(name) ?: return err(400, "bad name")
        if (playing()?.let { p -> p == n || volumes.volumes().any { it.storedName(n) == p } } == true) return err(409, "playing")
        if (busy(n)) return err(409, "moving")
        items(purgeExpired = true)                                                   // keeps the bin tidy
        synchronized(FileLocks.of(File("/castbridge-locks"), n)) {
            val hits = volumes.volumes().filter { volume.isNullOrEmpty() || it.id == volume }.mapNotNull { v ->
                val st = volumes.store(v); val stored = v.storedName(n)
                st.finalSize(stored)?.let { Triple(v, stored, it) }
            }
            val (v, stored, size) = hits.firstOrNull() ?: return err(404, "no such file")
            val dir = dirOf(v) ?: return err(501, "this volume cannot hold a bin")
            val bin = File(dir, BIN)
            if (!bin.isDirectory && !bin.mkdirs()) return err(500, "cannot create the bin")
            val src = File(dir, (volumes.store(v) as FileStore).diskName(stored))
            val id = now().toString() + "-" + java.util.UUID.randomUUID().toString().take(4)
            val target = File(bin, "${id}__$stored")
            if (target.name.toByteArray(Charsets.UTF_8).size > 250) return err(400, "name too long for the bin")
            if (!src.renameTo(target)) return err(500, "rename failed")
            library?.deleted(stored, size)
            Storage.forget(dir, stored)
            return ApiReply(200, """{"id":${q(id)},"name":${q(stored)},"volume":${q(v.id)},"size":$size}""")
        }
    }

    private fun restore(id: String): ApiReply {
        if (!ID.matches(id)) return err(400, "bad id")
        val it = items(purgeExpired = true).firstOrNull { it.id == id } ?: return err(404, "not in the bin (expired?)")
        val dir = dirOf(it.volume) ?: return err(503, "volume unavailable")
        // the TV library has one name space: a name taken on ANY volume is taken
        fun taken(n: String) = volumes.volumes().any { v -> volumes.store(v).let { s -> s.finalSize(v.storedName(n)) != null || s.partSize(v.storedName(n)) > 0 } }
        var name = it.name
        if (taken(name)) {
            val dot = name.lastIndexOf('.').takeIf { d -> d > 0 && name.length - d <= 6 } ?: name.length
            val base = name.substring(0, dot); val ext = name.substring(dot)
            var i = 1
            name = "$base (restauré)$ext"
            while (taken(name)) { i++; name = "$base (restauré $i)$ext" }
        }
        val stored = it.volume.storedName(name)
        val target = File(dir, stored)
        if (target.exists()) return err(409, "target exists")
        if (!it.file.renameTo(target)) return err(500, "rename failed")
        return ApiReply(200, """{"name":${q(stored)},"volume":${q(it.volume.id)},"size":${it.size}}""")
    }

    private fun purge(id: String): ApiReply {
        if (!ID.matches(id)) return err(400, "bad id")
        val it = items(purgeExpired = false).firstOrNull { x -> x.id == id } ?: return err(404, "not in the bin")
        return if (it.file.delete()) ApiReply(200, """{"purged":1}""") else err(500, "delete failed")
    }

    private fun empty(): ApiReply {
        val all = items(purgeExpired = false)
        val n = all.count { it.file.delete() }
        return ApiReply(200, """{"purged":$n}""")
    }

    companion object {
        const val BIN = ".castbridge-trash"
        const val RETENTION_MS = 30L * 86_400_000L
        private val ID = Regex("^\\d{10,14}-[0-9a-f]{4}$")

        /** "<id>__<name>" -> (id, trashedAt, name) */
        internal fun parseStored(stored: String): Triple<String, Long, String>? {
            val i = stored.indexOf("__")
            if (i <= 0) return null
            val id = stored.substring(0, i)
            if (!ID.matches(id)) return null
            return Triple(id, id.substringBefore('-').toLongOrNull() ?: return null, stored.substring(i + 2))
        }
    }
}
