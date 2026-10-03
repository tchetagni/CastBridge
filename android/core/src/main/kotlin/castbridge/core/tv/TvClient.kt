package castbridge.core.tv

import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Minimal client for [ReceiverServer]. [base] is like "http://192.168.0.117:8765". Blocking calls. */
class TvClient(val base: String, val pin: String? = null) {
    /** [code] is "NAME_TAKEN" (a finished file of that name but another size is on the TV) or "PART_OTHER" (the partial copy is of another content); null otherwise or on an older TV. */
    data class Part(val length: Long, val done: Boolean, val code: String? = null, val sizeChecked: Boolean = false)

    /**
     * What the TV holds under [name]. With [size] (the size of the file about to be sent) the TV only says "done" for a finished file of that very size;
     * without it an older answer is given (any finished file of that name), which a caller deleting something must never take as proof.
     */
    fun part(name: String, size: Long? = null): Part = parsePart(call("GET", "/api/part?name=${enc(name)}" + (size?.let { "&size=$it" } ?: "")))
    fun reset(name: String) = call("POST", "/api/reset?name=${enc(name)}")
    fun info(): String = call("GET", "/api/info")
    /** GET /api/have (R-12): a finished file of [size] bytes (and of that SHA-256) on the TV? An older TV answers 404 (HttpError): treat as unknown. */
    fun have(size: Long, sha256: String? = null, fresh: Boolean = false): String =
        call("GET", "/api/have?size=$size" + (sha256?.let { "&sha256=$it" } ?: "") + if (fresh) "&fresh=1" else "")
    fun play(name: String, posMs: Long = 0) = call("POST", "/api/play?name=${enc(name)}&pos=$posMs")
    /** Plays an http(s) link on the TV (nothing stored). 404 on a TV older than this route. */
    fun playUrl(url: String, title: String, posMs: Long = 0) = call("POST", "/api/playurl?url=${enc(url)}&title=${enc(title)}&pos=$posMs")
    fun pause() = call("POST", "/api/pause")
    fun resume() = call("POST", "/api/resume")
    fun stop() = call("POST", "/api/stop")
    fun seek(posMs: Long) = call("POST", "/api/seek?pos=$posMs")
    fun delete(name: String, volume: String? = null) = call("POST", "/api/delete?name=${enc(name)}" + (volume?.let { "&volume=${enc(it)}" } ?: ""))
    fun sysinfo(): String = call("GET", "/api/sysinfo")
    /** Stored videos with thumbnail availability, duration, resume position and volume. */
    fun library(): String = call("GET", "/api/library")
    /** Puts a stored file in a virtual folder ("" = the root, "Titre/Saison 01" creates both levels). Moves no byte (docs/LIBRARY-AGENT.md). */
    fun setFolder(name: String, folder: String): String = call("POST", "/api/folders/set?name=${enc(name)}&folder=${enc(folder)}")
    /** « Ranger ma bibliothèque »: what the TV would file, per folder (dry run, nothing moves). 404 on a TV older than this route. */
    fun organizePlan(): String = call("GET", "/api/library/organize")
    /** Files the flat files of the TV into their category folders (recomputed on the TV, renames only, nothing deleted). [max] files per call; the answer says how many remain. */
    fun organizeApply(max: Int = 500): String = call("POST", "/api/library/organize/apply?max=$max")
    /** Marks a stored file as watched (resume position cleared) or not watched. */
    fun setWatched(name: String, watched: Boolean): String = call("POST", "/api/library/watched?name=${enc(name)}&watched=${if (watched) 1 else 0}")
    /** JPEG thumbnail, or null while the TV is still making it (retry in a moment) or if it cannot make one. */
    fun thumb(name: String, volume: String? = null): ByteArray? {
        val c = open("GET", "/api/thumb?name=${enc(name)}" + (volume?.let { "&volume=${enc(it)}" } ?: ""))
        return if (c.responseCode == 200) c.inputStream.use { it.readBytes() } else { runCatching { c.errorStream?.close() }; null }
    }

    /** State of the app self-update on the TV (versions, whether installing is allowed, last install result). */
    fun updateInfo(): String = call("GET", "/api/update")
    /** APK files present on the TV with their package, label and version. */
    fun apkList(): String = call("GET", "/api/apk")
    /** Installs APK files already uploaded to the TV (several files = one app in split APKs, or several apps). */
    fun installApks(names: List<String>, force: Boolean = false): String =
        call("POST", "/api/apk/install?names=${enc(names.joinToString("/"))}" + if (force) "&force=1" else "")
    /** Asks the TV to install the APK previously uploaded under [name]. Throws [HttpError] with the reason. */
    fun installUpdate(name: String, force: Boolean = false): String =
        call("POST", "/api/update/install?name=${enc(name)}" + if (force) "&force=1" else "")
    fun setVolume(pct: Int) = call("POST", "/api/volume?pct=$pct")
    fun restart() = call("POST", "/api/restart")
    /** [safe]: the TV refuses (409 "playing") instead of stopping the video that is playing (the assistant uses it). */
    fun rename(name: String, to: String, safe: Boolean = false) = call("POST", "/api/rename?name=${enc(name)}&to=${enc(to)}" + if (safe) "&safe=1" else "")
    // ---- storage (see docs/STORAGE.md) ----
    fun storage(): String = call("GET", "/api/storage")
    /** [value] = "auto", "internal" or a volume id from [storage]. */
    fun setTarget(value: String): String = call("POST", "/api/storage/target?value=${enc(value)}")
    fun moveFile(name: String, toVolume: String): String = call("POST", "/api/storage/move?name=${enc(name)}&to=${enc(toVolume)}")
    fun cancelMove(): String = call("POST", "/api/storage/move/cancel")
    fun rescanStorage(measure: Boolean = false): String = call("POST", "/api/storage/rescan" + if (measure) "?measure=1" else "")
    /** Asks the TV to open its system folder picker (someone must then choose on the TV screen). */
    fun pickSafFolder(): String = call("POST", "/api/storage/saf/pick")
    /** Asks the TV to open its own storage settings, if it has any. */
    fun openTvStorageSettings(): String = call("POST", "/api/storage/open-settings")

    /** A destination volume as seen by the pre-flight check: free space now and after the transfer, allowed by the 1 GB rule or not. */
    data class VolumeOption(val id: String, val label: String, val kind: String, val free: Long, val freeAfter: Long, val ok: Boolean)

    data class StorageCheck(val ok: Boolean, val status: Int, val message: String, val warnings: List<String>, val volume: String?, val label: String?, val fs: String?,
                            val freeAfter: Long = -1, val minFreeAfter: Long = 0, val remaining: Long = 0, val options: List<VolumeOption> = emptyList())

    /**
     * Pre-flight: can the TV store this file, where, and with which caveats (FAT32 4 GB limit, slow drive, "1 GB must stay
     * free after the transfer"...). [volume] = "auto", "internal" or a volume id (default: the TV's own target setting).
     * Throws [HttpError] on a TV that predates the route (404): callers ignore that and just upload.
     */
    fun checkStorage(name: String, size: Long, durMs: Long = 0, volume: String? = null): StorageCheck {
        val j = call("GET", "/api/storage/check?name=${enc(name)}&size=$size" + (if (durMs > 0) "&dur=$durMs" else "") + (volume?.let { "&volume=${enc(it)}" } ?: ""))
        return parseCheck(j)
    }

    /** Opens /stream/<name> from byte [from] (Range). The caller closes [Ranged.input]. */
    fun openRange(name: String, from: Long): Ranged {
        val c = open("GET", "/stream/${enc(name)}")
        c.readTimeout = 30_000
        if (from > 0) c.setRequestProperty("Range", "bytes=$from-")
        val code = c.responseCode
        if (code == 416) {
            val total = c.getHeaderField("Content-Range")?.substringAfter('/')?.toLongOrNull() ?: -1
            runCatching { c.errorStream?.close() }
            return Ranged(416, java.io.ByteArrayInputStream(ByteArray(0)), from, total)
        }
        if (code >= 400) { val t = c.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty(); throw HttpError(code, t) }
        val cr = c.getHeaderField("Content-Range")                     // "bytes 100-999/1000"
        val start = if (code == 206) cr?.substringAfter("bytes ")?.substringBefore('-')?.toLongOrNull() ?: from else 0L
        val total = if (code == 206) cr?.substringAfter('/')?.toLongOrNull() ?: -1 else c.contentLengthLong
        return Ranged(code, c.inputStream, start, total)
    }

    /** An open byte range of a file on the TV: [start] is where [input] begins, [total] the file size (-1 unknown). */
    class Ranged(val code: Int, val input: InputStream, val start: Long, val total: Long)

    /** Generic call for extension routes. */
    fun raw(method: String, path: String): String = call(method, path)

    /** Sends bytes [offset, total) read from [src]. Throws [Conflict] if the TV holds a different offset. */
    fun upload(name: String, offset: Long, total: Long, src: InputStream, maxBytesPerSec: Long = 0, target: String? = null,
               bufferBytes: Int = UPLOAD_BUFFER, noFiling: Boolean = false, onBytes: (Long) -> Unit): Part {
        val throttle = if (maxBytesPerSec > 0) Throttle(maxBytesPerSec) else null
        // noFiling: the phone's « classer dans des dossiers » option is off (R-13): the TV keeps this file flat; an older TV ignores the parameter
        val c = open("PUT", "/upload/${enc(name)}?offset=$offset&total=$total" + (target?.let { "&target=${enc(it)}" } ?: "") + if (noFiling) "&filing=0" else "")
        c.doOutput = true
        c.readTimeout = 60_000
        c.setFixedLengthStreamingMode(total - offset)          // one continuous stream, nothing buffered in RAM by HttpURLConnection
        c.setRequestProperty("Content-Type", "application/octet-stream")
        c.outputStream.use { out ->
            val buf = ByteArray(bufferBytes)
            var left = total - offset
            while (left > 0) {
                val r = src.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                if (r < 0) throw IOException("source ended early")
                out.write(buf, 0, r)
                left -= r
                throttle?.onBytes(r.toLong())
                onBytes(r.toLong())
            }
        }
        val body = read(c, allow409 = true)
        if (c.responseCode == 409) {
            val part = parsePart(body)
            when (part.code) {
                "NAME_TAKEN" -> throw NameTaken()
                "PART_OTHER" -> throw PartOther(part.length)
            }
            throw Conflict(part.length)
        }
        return parsePart(body)
    }

    /** 409 NAME_TAKEN: another finished file of that name (other size) is on the TV; nothing was written. */
    class NameTaken : IOException("name taken, different size")
    /** 409 PART_OTHER: the partial copy on the TV belongs to another content; nothing was appended. */
    class PartOther(val serverLength: Long) : IOException("partial copy of another content")
    class Conflict(val serverLength: Long) : IOException("offset conflict, TV has $serverLength")
    class HttpError(val code: Int, body: String) : IOException("HTTP $code: ${body.take(200)}")


    private fun call(method: String, path: String): String = try { callOnce(method, path) } catch (e: java.net.SocketException) {
        // A pooled keep-alive connection the TV had already closed: a GET is safe to send again, once, on a fresh connection.
        if (method != "GET") throw e
        callOnce(method, path)
    }

    private fun callOnce(method: String, path: String): String {
        val c = open(method, path)
        if (method == "POST") { c.doOutput = true; c.setFixedLengthStreamingMode(0); c.outputStream.close() }
        return read(c)
    }

    private fun open(method: String, path: String) = (URL(base + path).openConnection() as HttpURLConnection).apply {
        requestMethod = method; connectTimeout = 4000; readTimeout = 8000
        // A POST/PUT is never replayed by the JDK (it may have been applied) : it must not ride a pooled keep-alive connection the TV already closed
        // (SocketException / « Connection reset » / « Error writing request body », seen under load in MultiVolumeServerTest and FilingServerTest),
        // so it asks for a fresh connection. Only GET (replayed once by [call]) and streams keep the pool.
        if (method != "GET") setRequestProperty("Connection", "close")
        castbridge.core.trust.TvCredential.apply(this, pin)     // PIN or trusted-phone token (never an unusable one)
    }

    private fun read(c: HttpURLConnection, allow409: Boolean = false): String {
        val code = c.responseCode
        val text = (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code >= 400 && !(allow409 && code == 409)) throw HttpError(code, text)
        return text
    }

    companion object {
        /** 401 because the phone's TOKEN is expired or revoked (not a wrong PIN: no lockout counted, a new HELLO fixes it). */
        fun isBadToken(e: HttpError) = e.code == 401 && "bad token" in e.message.orEmpty()
        /** Phone side of an upload: large reads from the file, one continuous HTTP body (the TV writes 256 kB blocks). */
        const val UPLOAD_BUFFER = 512 * 1024
        fun enc(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
        fun parseCheck(j: String): StorageCheck {
            val opts = Regex("\\{\"id\":\"((?:[^\"\\\\]|\\\\.)*)\",\"label\":\"((?:[^\"\\\\]|\\\\.)*)\",\"kind\":\"(\\w+)\",\"free\":(-?\\d+),\"freeAfter\":(-?\\d+),\"ok\":(true|false)\\}")
                .findAll(j).map { m -> VolumeOption(m.groupValues[1], m.groupValues[2].replace("\\\"", "\""), m.groupValues[3], m.groupValues[4].toLong(), m.groupValues[5].toLong(), m.groupValues[6] == "true") }.toList()
            // Top-level fields are read with the options array removed (its objects also have "free", "freeAfter"...).
            val top = j.replace(Regex("\"options\":\\[[^\\]]*\\]"), "")
            val ok = top.contains("\"ok\":true")
            return StorageCheck(ok, num(top, "status")?.toInt() ?: 200, str(top, "message") ?: str(top, "error") ?: "", strList(j, "warnings"),
                str(top, "volume"), str(top, "label"), str(top, "fs"), num(top, "freeAfter") ?: -1, num(top, "minFreeAfter") ?: 0, num(top, "remaining") ?: 0, opts)
        }
        fun parsePart(json: String) = Part(
            Regex("\"length\":(\\d+)").find(json)?.groupValues?.get(1)?.toLong() ?: 0,
            json.contains("\"done\":true"), str(json, "code"), json.contains("\"sizeChecked\":true"))
        /** Strings of a JSON array of strings under [key] (flat, as produced by the TV). */
        fun strList(json: String, key: String): List<String> {
            val arr = Regex("\"$key\":\\[((?:[^\\]\"]|\"(?:[^\"\\\\]|\\\\.)*\")*)\\]").find(json)?.groupValues?.get(1) ?: return emptyList()
            return Regex("\"((?:[^\"\\\\]|\\\\.)*)\"").findAll(arr).map { it.groupValues[1].replace("\\\"", "\"").replace("\\\\", "\\") }.toList()
        }
        fun num(json: String, key: String): Long? = Regex("\"$key\":(-?\\d+)").find(json)?.groupValues?.get(1)?.toLong()
        fun str(json: String, key: String): String? = Regex("\"$key\":\"((?:[^\"\\\\]|\\\\.)*)\"").find(json)
            ?.groupValues?.get(1)?.replace("\\\"", "\"")?.replace("\\\\", "\\")
    }
}

/**
 * Resumable upload: on any network failure it waits (backoff up to 5 s), re-resolves the TV
 * (its IP may have changed), asks how much it already has, and continues from there.
 */
class ResumableUpload(
    private val name: String,
    private val total: Long,
    private val resolve: () -> String?,          // current base URL of the TV, null if not found yet
    private val openAt: (Long) -> InputStream,   // source stream positioned at the given offset
    private val cancelled: () -> Boolean = { false },
    private val sleep: (Long) -> Unit = Thread::sleep,
    private val pin: String? = null,
    /** 0 = full speed (the default: the bigger the lead over playback, the better). Set to spare a weak TV. */
    private val maxBytesPerSec: Long = 0,
    /** Video duration if known: lets the TV judge whether a slow drive can keep up with playback during the upload. */
    private val durMs: Long = 0,
    /** Caveats reported by the TV before the first byte is sent (FAT32 target, slow drive...). */
    private val onWarnings: (List<String>) -> Unit = {},
    /** Destination on the TV for this transfer: "auto", "internal" or a volume id; null = the TV's own setting. */
    private val target: String? = null,
    /** The TV's pre-flight answer (destination volume, free space left after the transfer). */
    private val onCheck: (TvClient.StorageCheck) -> Unit = {},
    /** Consecutive failures without progress before giving up (to try another link); default: never. */
    private val giveUpAfter: Int = Int.MAX_VALUE,
    /** The credential to use NOW (a trusted phone's token is renewed while a long transfer waits); falls back to [pin]. A token the TV refused is never sent again. */
    private val credential: (() -> String?)? = null,
    /** The phone's « classer dans des dossiers » option is off (R-13): the TV keeps this file flat (`filing=0`). */
    private val noFiling: Boolean = false,
) {
    companion object {
        const val NAME_TAKEN_TEXT = "Un autre fichier du même nom est déjà sur la TV."
        const val PART_OTHER_TEXT = "Un autre envoi du même nom est déjà en cours sur la TV : réessayez plus tard."
        /** The partial copy on the TV is of another content this many times in a row: another sender is using that name, stop instead of fighting over it. */
        const val PART_OTHER_MAX = 3
    }

    sealed class State {
        data class Uploading(val sent: Long, val total: Long) : State()
        data class Waiting(val sent: Long, val total: Long, val reason: String) : State()
        object Done : State()
        data class Failed(val reason: String) : State()
    }

    /** Offset of the first byte THIS job sent (-1 = it sent none: the TV said « done » for a file it already had). */
    @Volatile var firstOffset = -1L; private set
    /** This job's own bytes reached the last byte of the file. */
    @Volatile var reachedEnd = false; private set
    /**
     * [MoveProof.byUpload]: every byte of the file was sent by THIS job (from 0 to the end) and the TV then said « done » for that size. A « done » for a file
     * the TV already held (same name, same size, no byte sent) is NOT a proof: a « Déplacer » keeps the original (R-12, second audit).
     */
    fun sentWholeFile(done: Boolean): Boolean = MoveProof.byUpload(if (firstOffset == 0L && reachedEnd) total else 0L, total, tvCheckedSize = true, tvDone = done)

    /** Runs until done, cancelled or a non-retryable error. Returns the final state. */
    fun run(onState: (State) -> Unit): State {
        var sent = 0L
        var backoff = 500L
        var checked = false
        var failures = 0
        var lastSent = -1L
        var refusedToken: String? = null
        var partOthers = 0
        while (!cancelled()) {
            if (sent != lastSent) { lastSent = sent; failures = 0 } else if (++failures > giveUpAfter) return State.Failed("liaison perdue").also(onState)
            val base = resolve()
            if (base == null) {
                onState(State.Waiting(sent, total, "TV introuvable"))
                sleep(backoff); backoff = minOf(backoff * 2, 5000); continue
            }
            val cred = credential?.invoke() ?: pin
            if (cred != null && cred == refusedToken) {
                // the TV said this token is expired or revoked: ask for no more until the phone holds a new one (nothing is sent meanwhile)
                onState(State.Waiting(sent, total, "Autorisation de la TV à renouveler : reprise dès qu'elle l'est")); sleep(backoff); backoff = minOf(backoff * 2, 5000); continue
            }
            val tv = TvClient(base, cred)
            if (!checked) {
                // Pre-flight, before any byte moves: a file that cannot be stored (FAT32 4 GB, no room) is announced now.
                try {
                    val c = tv.checkStorage(name, total, durMs, target)
                    onCheck(c)
                    if (!c.ok && c.status == 503) { onState(State.Waiting(sent, total, c.message)); sleep(backoff); backoff = minOf(backoff * 2, 5000); continue }
                    if (!c.ok) return State.Failed(c.message.ifEmpty { "refusé par la TV" }).also(onState)
                    if (c.warnings.isNotEmpty()) onWarnings(c.warnings)
                    checked = true
                } catch (e: castbridge.core.trust.TvCredential.Missing) {
                    return State.Failed(castbridge.core.trust.LinkText.failure(e)).also(onState)
                } catch (e: TvClient.HttpError) {
                    if (e.code == 401 && TvClient.isBadToken(e) && credential != null) { refusedToken = cred; continue }
                    if (e.code == 401) return State.Failed(castbridge.core.trust.LinkText.http(401, e.message.orEmpty())).also(onState)
                    checked = true                       // a TV without this route (404): the upload itself will say
                } catch (e: IOException) {
                    if (cancelled()) break
                    onState(State.Waiting(sent, total, castbridge.core.trust.LinkText.failure(e)))
                    sleep(backoff); backoff = minOf(backoff * 2, 5000); continue
                }
            }
            try {
                val p = tv.part(name, total)
                sent = p.length
                if (p.done) return State.Done.also(onState)
                if (p.code == "NAME_TAKEN") return State.Failed(NAME_TAKEN_TEXT).also(onState)
                if (sent > total || p.code == "PART_OTHER") {
                    if (++partOthers >= PART_OTHER_MAX) return State.Failed(PART_OTHER_TEXT).also(onState)
                    tv.reset(name); sent = 0          // refused with 409 "busy" when that partial copy is alive: handled below (wait, never a hot loop)
                }
                onState(State.Uploading(sent, total))
                openAt(sent).use { src ->
                    if (firstOffset < 0) firstOffset = sent
                    val r = tv.upload(name, sent, total, src, maxBytesPerSec, target, noFiling = noFiling) { n ->
                        sent += n; backoff = 500
                        if (sent >= total) reachedEnd = true
                        onState(State.Uploading(sent, total))
                        if (cancelled()) throw java.io.InterruptedIOException("cancelled")
                    }
                    if (r.done) return State.Done.also(onState)
                }
            } catch (e: castbridge.core.trust.TvCredential.Missing) {
                return State.Failed(castbridge.core.trust.LinkText.failure(e)).also(onState)
            } catch (e: TvClient.NameTaken) {
                return State.Failed(NAME_TAKEN_TEXT).also(onState)
            } catch (e: TvClient.PartOther) {
                if (++partOthers >= PART_OTHER_MAX) return State.Failed(PART_OTHER_TEXT).also(onState)
                onState(State.Waiting(sent, total, PART_OTHER_TEXT))
                sleep(backoff); backoff = minOf(backoff * 2, 5000)          // never a hot loop, even when the reset is refused (busy, read-only volume)
                runCatching { tv.reset(name) }; sent = 0                      // the partial copy is of another content: dropped when the TV agrees, never appended to
            } catch (e: TvClient.HttpError) {
                if (e.code == 401 && TvClient.isBadToken(e) && credential != null) { refusedToken = cred; continue }
                if (e.code == 507 || e.code == 413 || e.code == 400 || e.code == 401)
                    return State.Failed(TvClient.str(e.message.orEmpty().substringAfter(": "), "message") ?: e.message ?: "erreur").also(onState)
                // 503 "volume removed": the drive was pulled; wait like after a network cut, the upload resumes when it is back.
                onState(State.Waiting(sent, total,
                    if (e.code == 503 && e.message.orEmpty().contains("volume")) "Clé USB retirée ou indisponible : remettez-la, l'envoi reprendra" else e.message ?: "erreur"))
                sleep(backoff); backoff = minOf(backoff * 2, 5000)
            } catch (e: IOException) {
                if (cancelled()) break
                onState(State.Waiting(sent, total, castbridge.core.trust.LinkText.failure(e)))
                sleep(backoff); backoff = minOf(backoff * 2, 5000)
            }
        }
        return State.Failed("annulé").also(onState)
    }
}

/** Optional upload rate cap (off by default). Sleeps just enough to keep the average at [maxBytesPerSec]. */
class Throttle(
    private val maxBytesPerSec: Long,
    private val now: () -> Long = System::nanoTime,
    private val sleep: (Long) -> Unit = Thread::sleep,
) {
    private var start = 0L
    private var bytes = 0L
    fun onBytes(n: Long) {
        if (start == 0L) start = now()
        bytes += n
        val expectedNs = bytes * 1_000_000_000L / maxBytesPerSec
        val aheadMs = (expectedNs - (now() - start)) / 1_000_000
        if (aheadMs > 0) sleep(aheadMs)
    }
}

/**
 * TV -> phone download of a stored file, resumable: asks /stream/<name> from the size already saved (HTTP Range), appends,
 * and after any failure waits (backoff up to 5 s), re-resolves the TV and continues. Never keeps the file in RAM.
 */
class ResumableDownload(
    private val name: String,
    private val resolve: () -> String?,
    private val pin: String?,
    /** Bytes already saved on the phone (the resume point). */
    private val saved: () -> Long,
    /** Output positioned at [saved] (appending). */
    private val openOut: (Long) -> java.io.OutputStream,
    private val cancelled: () -> Boolean = { false },
    private val sleep: (Long) -> Unit = Thread::sleep,
    private val bufferBytes: Int = 256 * 1024,
    private val maxFailures: Int = 60,
    /** As in [ResumableUpload]: the live credential, and a refused token is never sent twice. */
    private val credential: (() -> String?)? = null,
) {
    sealed class State {
        data class Downloading(val got: Long, val total: Long) : State()
        data class Waiting(val got: Long, val total: Long, val reason: String) : State()
        data class Done(val total: Long) : State()
        data class Failed(val reason: String) : State()
    }

    fun run(onState: (State) -> Unit): State {
        var backoff = 500L
        var failures = 0
        var total = -1L
        var refusedToken: String? = null
        while (!cancelled()) {
            val have = saved()
            val base = resolve()
            val cred = credential?.invoke() ?: pin
            if (base == null) {
                onState(State.Waiting(have, total, "TV introuvable")); failures++
            } else if (cred != null && cred == refusedToken) {
                onState(State.Waiting(have, total, "Autorisation de la TV à renouveler : reprise dès qu'elle l'est"))
            } else try {
                val r = TvClient(base, cred).openRange(name, have)
                if (r.total >= 0) total = r.total
                if (r.code == 416 || (total >= 0 && have >= total)) {
                    r.input.close()
                    return if (total >= 0 && have == total) State.Done(total).also(onState)
                    else State.Failed("Le fichier du téléphone est plus grand que celui de la TV ($have > $total) : supprimez-le et recommencez").also(onState)
                }
                if (r.start != have) { r.input.close(); throw java.io.IOException("the TV answered from ${r.start}, expected $have") }
                var got = have
                r.input.use { input ->
                    openOut(have).use { out ->
                        val buf = ByteArray(bufferBytes)
                        while (true) {
                            if (cancelled()) throw java.io.InterruptedIOException("cancelled")
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            got += n; backoff = 500; failures = 0
                            onState(State.Downloading(got, total))
                        }
                    }
                }
                if (total < 0 || got >= total) return State.Done(got).also(onState)
                throw java.io.IOException("connection closed at $got/$total")
            } catch (e: castbridge.core.trust.TvCredential.Missing) {
                return State.Failed(castbridge.core.trust.LinkText.failure(e)).also(onState)
            } catch (e: TvClient.HttpError) {
                if (e.code == 401 && TvClient.isBadToken(e) && credential != null) { refusedToken = cred; onState(State.Waiting(saved(), total, castbridge.core.trust.LinkText.http(401, e.message.orEmpty()))) }
                else {
                    if (e.code == 401 || e.code == 404 || e.code == 400) return State.Failed(
                        when (e.code) { 404 -> "Fichier introuvable sur la TV"; 401 -> castbridge.core.trust.LinkText.http(401, e.message.orEmpty()); else -> castbridge.core.trust.LinkText.http(e.code, e.message.orEmpty()) }).also(onState)
                    failures++; onState(State.Waiting(saved(), total, castbridge.core.trust.LinkText.http(e.code, e.message.orEmpty())))
                }
            } catch (e: java.io.IOException) {
                if (cancelled()) break
                failures++; onState(State.Waiting(saved(), total, castbridge.core.trust.LinkText.failure(e)))
            }
            if (failures >= maxFailures) return State.Failed("TV injoignable").also(onState)
            sleep(backoff); backoff = minOf(backoff * 2, 5000)
        }
        return State.Failed("annulé").also(onState)
    }
}
