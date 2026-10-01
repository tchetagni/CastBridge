package castbridge.core.library.agent

import castbridge.core.net.JsonLite
import castbridge.core.net.JsonLite.bool
import castbridge.core.net.JsonLite.long
import castbridge.core.net.JsonLite.str
import castbridge.core.tv.TvClient
import java.security.MessageDigest

/** Reads the TV library (GET /api/library, GET /api/storage): every volume, the USB key included. Metadata only. */
object TvSnapshot {
    @Suppress("UNCHECKED_CAST")
    fun read(client: TvClient, now: Long = System.currentTimeMillis()): LibrarySnapshot {
        val lib = JsonLite.obj(client.library())
        val files = (lib["files"] as? List<Map<String, Any?>>).orEmpty().map { o ->
            FileRef(Origin.TV, o.str("name").orEmpty(), o.long("size") ?: 0, o.long("mtime") ?: 0, volumeId = o.str("volume").orEmpty(), folder = "",
                durationMs = o.long("durationMs") ?: 0, watched = o.bool("watched") ?: false, playedAtMs = o.long("playedAt") ?: 0,
                resumeMs = o.long("resumeMs") ?: 0, playing = o.bool("playing") ?: false)
        }.filter { it.name.isNotEmpty() }
        return LibrarySnapshot(Origin.TV, files, volumes(client), now)
    }

    @Suppress("UNCHECKED_CAST")
    fun volumes(client: TvClient): List<VolumeInfo> {
        val st = JsonLite.obj(client.storage())
        return (st["volumes"] as? List<Map<String, Any?>>).orEmpty().filter { it.bool("present") != false }.map { o ->
            val kind = when (o.str("kind")) { "internal" -> "internal"; "removable" -> "usb"; else -> "saf" }
            val max = o.long("maxFileBytes") ?: -1
            VolumeInfo(o.str("id").orEmpty(), o.str("label").orEmpty(), kind, o.long("free") ?: -1, o.long("total") ?: 0, o.bool("writable") ?: true,
                o.str("fs").orEmpty().takeIf { it != "?" }.orEmpty(), if (max < 0) Long.MAX_VALUE else max)
        }
    }
}

/** Reads the first and last 64 kB of a TV file over the local network to compare two files of the same size. Nothing is stored or sent anywhere else. */
class TvFingerprinter(private val client: TvClient, private val window: Int = 64 * 1024) : Fingerprinter {
    override fun fingerprint(file: FileRef): String? = runCatching {
        if (file.size <= 0) return null
        val md = MessageDigest.getInstance("SHA-256")
        md.update(file.size.toString().toByteArray())
        md.update(read(file.name, 0))
        if (file.size > window) md.update(read(file.name, maxOf(0, file.size - window)))
        md.digest().joinToString("") { "%02x".format(it) }.take(32)
    }.getOrNull()

    private fun read(name: String, from: Long): ByteArray {
        val r = client.openRange(name, from)
        r.input.use { inp ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(8192)
            var left = window
            while (left > 0) {
                val n = inp.read(buf, 0, minOf(buf.size, left))
                if (n < 0) break
                out.write(buf, 0, n); left -= n
            }
            return out.toByteArray()
        }
    }
}

/**
 * [LibraryOps] over the TV's HTTP routes (PIN protected, flat library): /api/rename, /api/storage/move, and the bin of
 * [TrashApi] (/api/trash/…). A TV too old to have the bin refuses the change: the agent NEVER falls back to /api/delete.
 */
class TvLibraryOps(private val client: TvClient, private val sleep: (Long) -> Unit = Thread::sleep, private val now: () -> Long = System::currentTimeMillis,
                   private val moveTimeoutMs: Long = 6 * 3_600_000L) : LibraryOps {
    override val folders = false

    private data class Row(val volume: String, val name: String, val size: Long, val playing: Boolean)
    @Volatile private var cache: Pair<Long, List<Row>>? = null

    @Suppress("UNCHECKED_CAST")
    private fun rows(): List<Row> {
        cache?.let { if (now() - it.first < 1500) return it.second }
        val j = JsonLite.obj(client.library())
        val l = (j["files"] as? List<Map<String, Any?>>).orEmpty().map { Row(it.str("volume").orEmpty(), it.str("name").orEmpty(), it.long("size") ?: 0, it.bool("playing") ?: false) }
        cache = now() to l
        return l
    }

    private fun changed() { cache = null }

    override fun volumes() = TvSnapshot.volumes(client)

    override fun stat(loc: Loc): Stat? = rows().firstOrNull { it.volume == loc.volume && it.name.equals(loc.name, ignoreCase = true) }?.let { Stat(it.size) }

    override fun nameTaken(loc: Loc): Boolean = rows().any { it.name.equals(loc.name, ignoreCase = true) }

    override fun isPlaying(loc: Loc): Boolean = rows().any { it.name == loc.name && it.volume == loc.volume && it.playing }

    override fun mkdirs(volume: String, folder: String): OpResult = OpResult.Fail("la bibliothèque de la TV n'a pas de dossiers")
    override fun moveToFolder(loc: Loc, folder: String): OpResult = OpResult.Fail("la bibliothèque de la TV n'a pas de dossiers")

    private fun httpFail(e: TvClient.HttpError): OpResult.Fail {
        val body = e.message.orEmpty().substringAfter(": ", "")
        val msg = runCatching { JsonLite.obj(body) }.getOrNull()?.let { it.str("message") ?: it.str("error") } ?: e.message.orEmpty()
        return OpResult.Fail(when {
            e.code == 401 -> "code PIN refusé"
            msg == "target exists" -> "ce nom existe déjà sur la TV"
            msg == "moving" || msg == "playing" -> "le fichier est en cours d'utilisation"
            msg == "not found" || msg == "no such file" -> "fichier introuvable sur la TV"
            msg == "not in the bin" || msg.startsWith("not in the bin") -> "n'est plus dans la corbeille (expiré ?)"
            else -> msg
        })
    }

    override fun rename(loc: Loc, newName: String): OpResult = try {
        client.rename(loc.name, newName); changed(); OpResult.Ok(loc.copy(name = newName))
    } catch (e: TvClient.HttpError) { httpFail(e) } catch (e: java.io.IOException) { OpResult.Fail("TV injoignable : ${e.message}") }

    @Suppress("UNCHECKED_CAST")
    override fun moveToVolume(loc: Loc, toVolume: String, onProgress: (Long, Long) -> Unit, cancelled: () -> Boolean): OpResult {
        val deadline = now() + moveTimeoutMs
        try {
            // the TV runs one move at a time: wait for ours turn
            while (true) {
                try { client.moveFile(loc.name, toVolume); break }
                catch (e: TvClient.HttpError) {
                    if (e.code == 409 && e.message.orEmpty().contains("another move")) { if (cancelled() || now() > deadline) return OpResult.Fail("annulé"); sleep(1000); continue }
                    return httpFail(e)
                }
            }
            changed()
            var cancelSent = false
            while (now() < deadline) {
                sleep(500)
                val mv = (JsonLite.obj(client.storage())["move"] as? Map<String, Any?>) ?: continue
                if (mv.str("name") != loc.name) continue
                onProgress(mv.long("done") ?: 0, mv.long("total") ?: 0)
                when (mv.str("state")) {
                    "done" -> { changed(); return OpResult.Ok(loc.copy(volume = toVolume)) }
                    "failed" -> return OpResult.Fail(mv.str("error") ?: "déplacement échoué")
                    "cancelled" -> return OpResult.Fail("annulé")
                }
                if (cancelled() && !cancelSent) { cancelSent = true; client.cancelMove() }
            }
            return OpResult.Fail("le déplacement dure trop longtemps")
        } catch (e: java.io.IOException) { return OpResult.Fail("TV injoignable : ${e.message}") }
    }

    override fun trash(loc: Loc): OpResult = try {
        val j = JsonLite.obj(client.raw("POST", "/api/trash/put?name=${TvClient.enc(loc.name)}&volume=${TvClient.enc(loc.volume)}"))
        changed()
        OpResult.Ok(loc, j.str("id"))
    } catch (e: TvClient.HttpError) {
        // an old TV answers {"error":"not found"} to an unknown route; the bin itself answers "no such file" / "not in the bin"
        if (e.code == 404 && runCatching { JsonLite.obj(e.message.orEmpty().substringAfter(": ", "")).str("error") }.getOrNull() == "not found") OpResult.Fail("Cette TV ne gère pas encore la corbeille : mettez à jour CastBridge TV. Rien n'a été supprimé.")
        else httpFail(e)
    } catch (e: java.io.IOException) { OpResult.Fail("TV injoignable : ${e.message}") }

    override fun restore(trashId: String, original: Loc): OpResult = try {
        val j = JsonLite.obj(client.raw("POST", "/api/trash/restore?id=${TvClient.enc(trashId)}"))
        changed()
        OpResult.Ok(Loc(j.str("volume") ?: original.volume, "", j.str("name") ?: original.name))
    } catch (e: TvClient.HttpError) { httpFail(e) } catch (e: java.io.IOException) { OpResult.Fail("TV injoignable : ${e.message}") }

    @Suppress("UNCHECKED_CAST")
    override fun findInTrash(original: Loc): String? = runCatching {
        (JsonLite.obj(client.raw("GET", "/api/trash"))["items"] as? List<Map<String, Any?>>).orEmpty()
            .firstOrNull { it.str("name") == original.name && it.str("volume") == original.volume }?.str("id")
    }.getOrNull()
}
