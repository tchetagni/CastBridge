package castbridge.core.dl

import fi.iki.elonen.NanoHTTPD
import java.io.File

/**
 * In-memory stand-in for aria2's JSON-RPC (only what CastBridge uses), usable as an [Aria2Rpc] transport or behind a real
 * HTTP port. Checks the "token:<secret>" parameter like aria2 does.
 */
class FakeAria2(val secret: String = "s3cret") {
    class Dl(val gid: String, var status: String, var total: Long, var completed: Long, var dir: String,
             var files: List<Triple<String, Long, Boolean>>, var followedBy: List<String> = emptyList(),
             var metadata: Boolean = false, var infoHash: String? = null, var btName: String? = null,
             val options: MutableMap<String, String> = mutableMapOf(), var errorCode: Int = 0, var speed: Long = 0, var seeder: Boolean = false)

    val dls = LinkedHashMap<String, Dl>()
    val calls = ArrayList<String>()
    val globalOptions = LinkedHashMap<String, String>()
    var added = ArrayList<Pair<String, Any?>>()
    private var next = 1
    var shutdownCalled = false
    var onShutdown: () -> Unit = {}
    /** What addUri of a magnet creates: a metadata download (default) as aria2 does. */
    var magnetTotal = 0L

    fun gid() = "%016x".format(next++)

    @Synchronized fun handle(body: String): String {
        val req = Json.parse(body).obj()
        val id = req["id"]
        val method = req.s("method").orEmpty()
        val params = req["params"].list()
        calls += method
        if (params.firstOrNull() != "token:$secret") return err(id, 1, "Unauthorized")
        val p = params.drop(1)
        return try { ok(id, dispatch(method, p)) } catch (e: IllegalStateException) { err(id, 1, e.message ?: "error") }
    }

    private fun dispatch(method: String, p: List<Any?>): Any? = when (method) {
        "aria2.getVersion" -> mapOf("version" to "1.37.0", "enabledFeatures" to listOf("BitTorrent", "SFTP"))
        "aria2.addUri" -> {
            val uris = p[0].list().map { it.toString() }
            val opts = p.getOrNull(1).obj().mapValues { it.value.toString() }
            val g = gid()
            val u = uris[0]
            val d = if (u.startsWith("magnet:")) Dl(g, "active", 0, 0, opts["dir"]!!, listOf(Triple("[METADATA]hash", 0L, true)), metadata = true)
                else Dl(g, "active", 0, 0, opts["dir"]!!, listOf(Triple(opts["dir"] + "/" + (opts["out"] ?: u.substringAfterLast('/')), 0L, true)))
            d.options.putAll(opts)
            dls[g] = d; added += "addUri" to uris
            g
        }
        "aria2.addTorrent" -> {
            val opts = p.getOrNull(2).obj().mapValues { it.value.toString() }
            val info = TorrentInfo.parse(java.util.Base64.getDecoder().decode(p[0].toString()))
            val g = gid()
            dls[g] = Dl(g, "active", info.totalLength, 0, opts["dir"]!!, info.files.map { Triple(opts["dir"] + "/" + info.name + (if (info.files.size > 1) "/" + it.path else ""), it.length, true) },
                infoHash = info.infoHash, btName = info.name).also { it.options.putAll(opts) }
            added += "addTorrent" to info.name
            g
        }
        "aria2.addMetalink" -> {
            val opts = p.getOrNull(1).obj().mapValues { it.value.toString() }
            val g = gid(); dls[g] = Dl(g, "active", 0, 0, opts["dir"]!!, emptyList()); listOf(g)
        }
        "aria2.tellStatus" -> view(dl(p[0]))
        "aria2.tellActive" -> dls.values.filter { it.status == "active" }.map(::view)
        "aria2.tellWaiting" -> dls.values.filter { it.status == "waiting" || it.status == "paused" }.map(::view)
        "aria2.tellStopped" -> dls.values.filter { it.status in setOf("complete", "error", "removed") }.map(::view)
        "aria2.forcePause" -> { val d = dl(p[0]); check(d.status == "active" || d.status == "waiting") { "cannot pause" }; d.status = "paused"; d.gid }
        "aria2.unpause" -> { val d = dl(p[0]); check(d.status == "paused") { "not paused" }; d.status = "active"; d.gid }
        "aria2.forceRemove" -> { dl(p[0]).status = "removed"; p[0] }
        "aria2.removeDownloadResult" -> { val d = dl(p[0]); check(d.status in setOf("complete", "error", "removed")) { "active" }; dls.remove(d.gid); "OK" }
        "aria2.changeOption" -> { dl(p[0]).options.putAll(p[1].obj().mapValues { it.value.toString() }); p[1].obj()["dir"]?.let { dl(p[0]).dir = it.toString() }; "OK" }
        "aria2.changeGlobalOption" -> { globalOptions.putAll(p[0].obj().mapValues { it.value.toString() }); "OK" }
        "aria2.getGlobalStat" -> mapOf("downloadSpeed" to dls.values.sumOf { it.speed }.toString(), "uploadSpeed" to "0", "numActive" to "1")
        "aria2.getFiles" -> view(dl(p[0]))["files"]
        "aria2.getPeers" -> listOf(mapOf("ip" to "10.0.0.2", "downloadSpeed" to "100", "uploadSpeed" to "0", "seeder" to "true"))
        "aria2.getServers" -> emptyList<Any>()
        "aria2.changePosition" -> { val d = dl(p[0]); check(d.status != "active") { "active" }; 0L }
        "aria2.forcePauseAll" -> { dls.values.filter { it.status == "active" || it.status == "waiting" }.forEach { it.status = "paused" }; "OK" }
        "aria2.unpauseAll" -> { dls.values.filter { it.status == "paused" }.forEach { it.status = "active" }; "OK" }
        "aria2.saveSession", "aria2.purgeDownloadResult" -> "OK"
        "aria2.shutdown", "aria2.forceShutdown" -> { shutdownCalled = true; Thread { Thread.sleep(50); onShutdown() }.start(); "OK" }
        else -> throw IllegalStateException("No such method: $method")
    }

    private fun dl(g: Any?): Dl = dls[g.toString()] ?: throw IllegalStateException("GID $g is not found")

    private fun view(d: Dl): Map<String, Any?> = buildMap {
        put("gid", d.gid); put("status", d.status); put("totalLength", d.total.toString()); put("completedLength", d.completed.toString())
        put("downloadSpeed", d.speed.toString()); put("uploadSpeed", "0"); put("connections", "3"); put("dir", d.dir)
        put("errorCode", d.errorCode.toString()); put("seeder", d.seeder.toString())
        if (d.followedBy.isNotEmpty()) put("followedBy", d.followedBy)
        d.infoHash?.let { put("infoHash", it); put("numSeeders", "2") }
        if (d.btName != null) put("bittorrent", mapOf("info" to mapOf("name" to d.btName)))
        put("files", d.files.mapIndexed { i, (path, len, sel) ->
            mapOf("index" to (i + 1).toString(), "path" to path, "length" to len.toString(),
                "completedLength" to (if (d.status == "complete") len else 0).toString(), "selected" to sel.toString(), "uris" to emptyList<Any>())
        })
    }

    private fun ok(id: Any?, result: Any?) = Json.write(mapOf("jsonrpc" to "2.0", "id" to id, "result" to result))
    private fun err(id: Any?, code: Int, msg: String) = Json.write(mapOf("jsonrpc" to "2.0", "id" to id, "error" to mapOf("code" to code, "message" to msg)))

    // ---- test helpers ----

    /** Writes the files of [gid] on disk and marks it complete, as aria2 does at the end. */
    @Synchronized fun complete(gid: String, content: (String) -> ByteArray = { "x".repeat(100).toByteArray() }) {
        val d = dls[gid]!!
        d.files.forEach { (path, _, sel) -> if (sel) File(path).also { it.parentFile.mkdirs(); it.writeBytes(content(path)) } }
        d.files = d.files.map { (p, _, s) -> Triple(p, File(p).takeIf { it.exists() }?.length() ?: 0, s) }
        d.total = d.files.sumOf { it.second }; d.completed = d.total; d.status = "complete"
    }

    /** A real HTTP endpoint, like aria2's /jsonrpc. */
    fun serve(port: Int): NanoHTTPD = object : NanoHTTPD("127.0.0.1", port) {
        override fun serve(s: IHTTPSession): Response {
            val len = s.headers["content-length"]?.toInt() ?: 0
            val b = ByteArray(len); var o = 0
            while (o < len) { val r = s.inputStream.read(b, o, len - o); if (r < 0) break; o += r }
            val reply = handle(String(b, Charsets.UTF_8))
            return newFixedLengthResponse(Response.Status.OK, "application/json", reply)
        }
    }.also { it.start(5000, true) }
}
