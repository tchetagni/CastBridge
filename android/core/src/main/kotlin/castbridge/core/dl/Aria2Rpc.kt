package castbridge.core.dl

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import java.util.concurrent.atomic.AtomicLong

/**
 * JSON-RPC client for the aria2 daemon run by the TV app. It only ever talks to 127.0.0.1: aria2's RPC is never exposed
 * on the network (the CastBridge API relays a safe subset, behind the PIN). The secret is sent as "token:<secret>" in
 * every call and is never logged or put in an exception message.
 */
class Aria2Rpc(
    private val port: Int,
    private val secret: String,
    private val connectTimeoutMs: Int = 2000,
    private val readTimeoutMs: Int = 10_000,
    /** Replaces the HTTP POST (tests). Takes the request body, returns the response body. */
    private val transport: ((String) -> String)? = null,
) {
    /** aria2 answered with a JSON-RPC error (bad GID, refused option...). [code] is aria2's error code. */
    class RpcError(val code: Long, message: String) : IOException(message)

    private val ids = AtomicLong()

    fun call(method: String, vararg params: Any?): Any? {
        val body = Json.write(mapOf(
            "jsonrpc" to "2.0", "id" to ids.incrementAndGet().toString(), "method" to method,
            "params" to listOf("token:$secret") + params.toList()))
        val reply = transport?.invoke(body) ?: post(body)
        val m = try { Json.parse(reply).obj() } catch (e: Json.ParseError) { throw IOException("bad aria2 reply") }
        m["error"]?.obj()?.let { e -> throw RpcError(e.n("code"), e.s("message").orEmpty().take(300)) }
        return m["result"]
    }

    private fun post(body: String): String {
        val c = URL("http://127.0.0.1:$port/jsonrpc").openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"; c.connectTimeout = connectTimeoutMs; c.readTimeout = readTimeoutMs
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            val bytes = body.toByteArray(Charsets.UTF_8)
            c.setFixedLengthStreamingMode(bytes.size)
            c.outputStream.use { it.write(bytes) }
            val code = c.responseCode
            val text = (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            // aria2 answers JSON-RPC errors with HTTP 400/500 and a JSON body: let the caller parse it.
            if (text.isEmpty()) throw IOException("aria2 HTTP $code")
            return text
        } finally { c.disconnect() }
    }

    // ---- typed helpers (method names from the aria2 manual, "RPC interface") ----

    fun getVersion(): String = call("aria2.getVersion").obj().s("version").orEmpty()
    fun addUri(uris: List<String>, options: Map<String, String> = emptyMap(), position: Int? = null): String =
        call("aria2.addUri", uris, options, *listOfNotNull(position).toTypedArray()) as String
    fun addTorrent(torrent: ByteArray, options: Map<String, String> = emptyMap()): String =
        call("aria2.addTorrent", Base64.getEncoder().encodeToString(torrent), emptyList<String>(), options) as String
    /** A metalink may describe several downloads: one GID each. */
    fun addMetalink(metalink: ByteArray, options: Map<String, String> = emptyMap()): List<String> =
        call("aria2.addMetalink", Base64.getEncoder().encodeToString(metalink), options).list().map { it.toString() }

    fun tellStatus(gid: String, keys: List<String> = STATUS_KEYS): Map<String, Any?> = call("aria2.tellStatus", gid, keys).obj()
    fun tellActive(keys: List<String> = STATUS_KEYS): List<Map<String, Any?>> = call("aria2.tellActive", keys).list().map { it.obj() }
    fun tellWaiting(offset: Int = 0, num: Int = 1000, keys: List<String> = STATUS_KEYS): List<Map<String, Any?>> =
        call("aria2.tellWaiting", offset, num, keys).list().map { it.obj() }
    fun tellStopped(offset: Int = 0, num: Int = 1000, keys: List<String> = STATUS_KEYS): List<Map<String, Any?>> =
        call("aria2.tellStopped", offset, num, keys).list().map { it.obj() }
    /** Every download aria2 knows (active, waiting/paused, stopped). */
    fun tellAll(keys: List<String> = STATUS_KEYS): List<Map<String, Any?>> = tellActive(keys) + tellWaiting(keys = keys) + tellStopped(keys = keys)

    fun pause(gid: String) = call("aria2.forcePause", gid)       // force: no wait for tracker "stopped" announces
    fun unpause(gid: String) = call("aria2.unpause", gid)
    fun remove(gid: String) = call("aria2.forceRemove", gid)
    fun pauseAll() = call("aria2.forcePauseAll")
    fun unpauseAll() = call("aria2.unpauseAll")
    /** [how] = POS_SET, POS_CUR or POS_END. Returns the new position. */
    fun changePosition(gid: String, pos: Int, how: String): Long = (call("aria2.changePosition", gid, pos, how) as? Number)?.toLong() ?: 0
    fun changeOption(gid: String, options: Map<String, String>) = call("aria2.changeOption", gid, options)
    fun changeGlobalOption(options: Map<String, String>) = call("aria2.changeGlobalOption", options)
    fun getOption(gid: String): Map<String, Any?> = call("aria2.getOption", gid).obj()
    fun getGlobalOption(): Map<String, Any?> = call("aria2.getGlobalOption").obj()
    fun getGlobalStat(): Map<String, Any?> = call("aria2.getGlobalStat").obj()
    fun getFiles(gid: String): List<Map<String, Any?>> = call("aria2.getFiles", gid).list().map { it.obj() }
    fun getPeers(gid: String): List<Map<String, Any?>> = call("aria2.getPeers", gid).list().map { it.obj() }
    fun getServers(gid: String): List<Map<String, Any?>> = call("aria2.getServers", gid).list().map { it.obj() }
    fun purgeDownloadResult() = call("aria2.purgeDownloadResult")
    fun removeDownloadResult(gid: String) = call("aria2.removeDownloadResult", gid)
    fun saveSession() = call("aria2.saveSession")
    fun shutdown() = call("aria2.shutdown")
    fun forceShutdown() = call("aria2.forceShutdown")

    companion object {
        val STATUS_KEYS = listOf("gid", "status", "totalLength", "completedLength", "uploadLength", "downloadSpeed", "uploadSpeed",
            "connections", "numSeeders", "seeder", "errorCode", "errorMessage", "followedBy", "following", "belongsTo", "dir",
            "files", "bittorrent", "infoHash", "numPieces", "pieceLength", "verifiedLength", "verifyIntegrityPending")
    }
}
