package castbridge.core.lots

import castbridge.core.net.JsonLite
import castbridge.core.net.JsonLite.long
import castbridge.core.tv.ApiExtension
import castbridge.core.tv.ApiReply
import castbridge.core.tv.BtProtocol
import castbridge.core.tv.Link
import castbridge.core.tv.ResumableBtUpload
import castbridge.core.tv.ResumableUpload
import castbridge.core.tv.TvClient
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL

/** Outcome of pushing one lot to the TV. */
sealed class SendResult {
    /** The TV verified and installed it (HTTP answers it directly). */
    object Installed : SendResult()
    /** All bytes (and the proof) reached the TV; it installs on its own and will report it in its manifest (Bluetooth, CBT1). */
    object Delivered : SendResult()
    /** The TV refused (budget, signature, corrupted…): [reason] in French; retrying the same lot is useless. */
    data class Refused(val reason: String) : SendResult()
    /** The link dropped or the TV is gone: retry later, resuming from [confirmed] bytes held by the TV. */
    data class LinkDown(val confirmed: Long, val reason: String) : SendResult()
}

/**
 * How the phone talks to ONE TV for lots. Implemented over HTTP (Wi-Fi LAN / Wi-Fi Direct) and CBT1 (Bluetooth) now; the
 * API-over-Bluetooth tunnel (claude/bt-everything) only needs another implementation: HTTP calls through the tunnel base URL
 * already work with [HttpLotTransport].
 */
interface LotTransport {
    val label: String
    /** Can this link read the TV manifest (false for plain Bluetooth file transfer: deliveries are then blind until the next HTTP contact). */
    val canReadManifest: Boolean
    /** The TV manifest, or null if the TV is not reachable. */
    fun manifest(): TvManifest?
    fun setPriority(ids: List<LotId>): Boolean
    /** Sends [file] (the whole lot of [meta]) resuming at what the TV already holds; [onConfirmed] gets the bytes the TV confirmed. */
    fun send(meta: LotMeta, file: File, proofJson: String, onConfirmed: (Long) -> Unit, cancelled: () -> Boolean = { false }): SendResult
    fun remove(id: LotId): Boolean
}

/**
 * TV side routes (an [ApiExtension] behind the TV's existing authentication: PIN or trusted-phone token), additive:
 * - GET  /api/lots                                  the TV manifest ([TvManifest])
 * - POST /api/lots/priority?ids=learn:cm2,quiz:cm2   the phone's plan: these are never evicted
 * - GET  /api/lots/part?name=                        {"received":n}: where to resume
 * - POST /api/lots/upload?name=&offset=&total=       one chunk (≤ [CHUNK] bytes) at exactly the TV's offset; 409 + {"received":n} otherwise
 * - POST /api/lots/install?name=                     body = the signed catalog; the TV verifies and installs, 422 + French reason otherwise
 * - POST /api/lots/remove?id=learn:cm2
 * Chunked POST (not a PUT stream) because the TV's extension routes take bounded bodies; each answer confirms the offset,
 * which is exactly what a resume needs. There is deliberately NO route that fetches anything from the Internet.
 */
class TvLotApi(private val store: TvLotStore) : ApiExtension {
    override fun wantsBody(path: String) = path == "/api/lots/upload" || path == "/api/lots/install"

    override fun handleBody(path: String, method: String, params: Map<String, String>, body: ByteArray): ApiReply? {
        if (method != "POST") return null
        val name = params["name"] ?: return err(400, "name manquant")
        return when (path) {
            "/api/lots/upload" -> {
                val offset = params["offset"]?.toLongOrNull() ?: return err(400, "offset manquant")
                val total = params["total"]?.toLongOrNull() ?: return err(400, "total manquant")
                when (val r = store.receive(name, offset, total, body)) {
                    is TvLotStore.Chunk.Received -> ApiReply(200, """{"received":${r.bytes}}""")
                    is TvLotStore.Chunk.Conflict -> ApiReply(409, """{"received":${r.bytes}}""")
                    is TvLotStore.Chunk.Refused -> err(422, r.reason)
                }
            }
            "/api/lots/install" -> when (val r = store.installReceived(name, String(body, Charsets.UTF_8))) {
                is TvLotStore.Result.Ok -> ApiReply(200, """{"installed":true}""")
                is TvLotStore.Result.Refused -> err(422, r.reason)
            }
            else -> null
        }
    }

    override fun handle(path: String, method: String, params: Map<String, String>): ApiReply? {
        if (!path.startsWith("/api/lots")) return null
        return when {
            path == "/api/lots" && method == "GET" -> ApiReply(200, store.manifest().toJson())
            path == "/api/lots/part" && method == "GET" -> params["name"]?.let { ApiReply(200, """{"received":${store.received(it)}}""") } ?: err(400, "name manquant")
            path == "/api/lots/priority" && method == "POST" -> {
                store.setPriority(params["ids"].orEmpty().split(',').mapNotNull(LotNames::parseKey)); ApiReply(200, store.manifest().toJson())
            }
            path == "/api/lots/remove" && method == "POST" -> params["id"]?.let(LotNames::parseKey)?.let { store.remove(it); ApiReply(200, store.manifest().toJson()) } ?: err(400, "id invalide")
            path == "/api/lots/upload" || path == "/api/lots/install" -> err(405, "POST avec un corps attendu")
            else -> err(404, "route inconnue")
        }
    }

    private fun err(code: Int, msg: String) = ApiReply(code, "{\"error\":${JsonLite.quote(msg)}}")

    companion object { const val CHUNK = 512 * 1024 }
}

/** [LotTransport] over the TV's HTTP API ([base] = "http://192.168.1.20:8765", or the Bluetooth tunnel's local base URL). */
class HttpLotTransport(private val base: String, private val pin: String?, override val label: String = "Wi-Fi",
                       private val timeoutMs: Int = 8000) : LotTransport {
    override val canReadManifest = true

    private class Answer(val code: Int, val body: String)

    private fun call(method: String, path: String, body: ByteArray? = null): Answer {
        val c = URL(base.trimEnd('/') + path).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method; c.connectTimeout = timeoutMs; c.readTimeout = timeoutMs * 4
            castbridge.core.trust.TvCredential.apply(c, pin)   // the one place that builds the header; never logged, never in the URL
            if (method == "POST") {
                c.doOutput = true
                c.setFixedLengthStreamingMode(body?.size ?: 0)
                c.outputStream.use { o -> body?.let { o.write(it) } }
            }
            val code = c.responseCode
            val text = (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            return Answer(code, text)
        } finally { c.disconnect() }
    }

    private fun reason(a: Answer) = runCatching { JsonLite.obj(a.body)["error"] as? String }.getOrNull() ?: "HTTP ${a.code}"
    private fun received(a: Answer) = runCatching { JsonLite.obj(a.body).long("received") }.getOrNull()

    override fun manifest(): TvManifest? = try { call("GET", "/api/lots").takeIf { it.code == 200 }?.let { TvManifest.parse(it.body) } } catch (e: IOException) { null }

    override fun setPriority(ids: List<LotId>): Boolean = try {
        call("POST", "/api/lots/priority?ids=" + TvClient.enc(ids.joinToString(",") { LotNames.key(it) })).code == 200
    } catch (e: IOException) { false }

    override fun remove(id: LotId): Boolean = try { call("POST", "/api/lots/remove?id=" + TvClient.enc(LotNames.key(id))).code == 200 } catch (e: IOException) { false }

    override fun send(meta: LotMeta, file: File, proofJson: String, onConfirmed: (Long) -> Unit, cancelled: () -> Boolean): SendResult {
        val name = LotNames.fileName(meta)
        val q = "name=" + TvClient.enc(name)
        try {
            var off = call("GET", "/api/lots/part?$q").let { received(it) } ?: 0L
            if (off > meta.bytes) off = 0L
            RandomAccessFile(file, "r").use { raf ->
                while (off < meta.bytes) {
                    if (cancelled()) return SendResult.LinkDown(off, "interrompu")
                    val n = minOf(TvLotApi.CHUNK.toLong(), meta.bytes - off).toInt()
                    val buf = ByteArray(n); raf.seek(off); raf.readFully(buf)
                    val a = call("POST", "/api/lots/upload?$q&offset=$off&total=${meta.bytes}", buf)
                    when (a.code) {
                        200 -> { off = received(a) ?: (off + n); onConfirmed(off) }
                        409 -> { off = received(a) ?: 0L; onConfirmed(off) }        // the TV stands elsewhere (restart, other transport): resume there
                        else -> return SendResult.Refused(reason(a))
                    }
                }
            }
            val a = call("POST", "/api/lots/install?$q", proofJson.toByteArray(Charsets.UTF_8))
            return if (a.code == 200) SendResult.Installed else SendResult.Refused(reason(a))
        } catch (e: IOException) {
            return SendResult.LinkDown(0L, e.message ?: e.javaClass.simpleName)
        }
    }
}

/**
 * [LotTransport] over Bluetooth file transfer (CBT1, castbridge.core.tv.BtProtocol): the lot, then its signed proof
 * ("<lot file>.json", sent last), land in the TV's reception folder; the TV adopts the pair ([TvLotStore.adoptFrom]). CBT1 has
 * no answer channel, so the install result and the TV state are only learnt at the next contact that can read the manifest
 * ([canReadManifest] = false): the delivery stays SENT until then.
 * @param connect opens a Bluetooth link to the TV (throws IOException if it is out of range)
 */
class Cbt1LotTransport(private val pin: String, private val connect: () -> Link, private val sleep: (Long) -> Unit = Thread::sleep) : LotTransport {
    override val label = "Bluetooth"
    override val canReadManifest = false
    override fun manifest(): TvManifest? = null
    override fun setPriority(ids: List<LotId>) = false
    override fun remove(id: LotId) = false

    override fun send(meta: LotMeta, file: File, proofJson: String, onConfirmed: (Long) -> Unit, cancelled: () -> Boolean): SendResult {
        val name = LotNames.fileName(meta)
        val proof = proofJson.toByteArray(Charsets.UTF_8)
        var last = 0L
        val r1 = ResumableBtUpload(name, meta.bytes, pin, connect, { off -> file.inputStream().also { it.skip(off) } }, cancelled, sleep, maxFailures = 2)
            .run { s -> if (s is ResumableUpload.State.Uploading) { last = s.sent; onConfirmed(s.sent) } }
        when (r1) {
            is ResumableUpload.State.Done -> {}
            is ResumableUpload.State.Failed -> return if (r1.reason == "annulé" || r1.reason.contains("injoignable")) SendResult.LinkDown(last, r1.reason) else SendResult.Refused(r1.reason)
            else -> return SendResult.LinkDown(last, "Bluetooth interrompu")
        }
        val r2 = ResumableBtUpload(name + LotNames.PROOF_SUFFIX, proof.size.toLong(), pin, connect, { off -> proof.inputStream().also { it.skip(off) } }, cancelled, sleep, maxFailures = 2).run { }
        return if (r2 is ResumableUpload.State.Done) SendResult.Delivered else SendResult.LinkDown(meta.bytes, "Bluetooth interrompu avant la fin de la signature")
    }
}
