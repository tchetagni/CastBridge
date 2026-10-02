package castbridge.core.xfer

import castbridge.core.tv.TvClient
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** The small calls of the protocol (begin, state, finish). Chunks travel on the lanes. */
interface TransferApi {
    class Caps(val version: Int, val maxStreams: Int)
    class Begin(val done: Boolean, val id: String, val map: BlockMap, val hashes: List<String>, val writeBps: Long, val maxStreams: Int, val volume: String, val note: String? = null, val ordered: Boolean = false)
    sealed class Finish { object Done : Finish(); class Missing(val map: BlockMap) : Finish(); class Corrupt(val map: BlockMap) : Finish(); class Refused(val message: String) : Finish() }
    /** A refusal that no retry fixes (no room, name refused, bad credential...); [message] is for the user. */
    class Refused(val http: Int, override val message: String) : IOException(message)
    /** The TV is reading the file back (503 `verifying`): not a failure, ask again after [retryMs] (by `begin`, which answers at once meanwhile). */
    class Verifying(val retryMs: Long) : IOException("la TV vérifie encore le fichier")

    /** null = this TV does not know the protocol: send the old way. */
    fun caps(): Caps?
    fun begin(m: Manifest, target: String?, discard: Boolean = false): Begin
    fun state(id: String, withHashes: Boolean = false): Begin?
    fun finish(id: String, root: String): Finish
    fun abort(id: String)
}

class HttpTransferApi(private val baseOf: () -> String?, private val credential: () -> String?) : TransferApi {
    constructor(base: String, credential: () -> String?) : this({ base }, credential)

    private fun call(method: String, path: String): Pair<Int, String> {
        val base = baseOf() ?: throw IOException("TV introuvable")
        val c = URL(base + path).openConnection() as HttpURLConnection
        c.requestMethod = method; c.connectTimeout = 4000; c.readTimeout = 60_000   // finish re-reads the file on the TV
        castbridge.core.trust.TvCredential.apply(c, credential())
        if (method == "POST") { c.doOutput = true; c.setFixedLengthStreamingMode(0); c.outputStream.close() }
        val code = c.responseCode
        val text = (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        return code to text
    }

    override fun caps(): TransferApi.Caps? {
        val (code, body) = try { call("GET", "/api/transfer/caps") } catch (e: IOException) { throw e }
        if (code == 401 || code == 403) throw TransferApi.Refused(code, TvClient.str(body, "message") ?: "autorisation refusée par la TV")
        if (code != 200 || TvClient.num(body, "version") == null) return null
        return TransferApi.Caps(TvClient.num(body, "version")!!.toInt(), (TvClient.num(body, "maxStreams") ?: 4).toInt())
    }

    override fun begin(m: Manifest, target: String?, discard: Boolean): TransferApi.Begin {
        val (code, body) = call("POST", "/api/transfer/begin?name=${TvClient.enc(m.name)}&size=${m.size}&blockSize=${m.blockSize}" + (target?.let { "&target=${TvClient.enc(it)}" } ?: "") + if (discard) "&discard=1" else "")
        if (code == 503 && "verifying" in body) throw TransferApi.Verifying(TvClient.num(body, "retryMs") ?: 2000)
        if (code in 500..599 && code != 507) throw IOException("TV : $code ${body.take(80)}")
        if (code != 200) throw TransferApi.Refused(code, TvClient.str(body, "message") ?: TvClient.str(body, "error") ?: "refusé par la TV ($code)")
        return parse(m, body)!!
    }

    override fun state(id: String, withHashes: Boolean): TransferApi.Begin? {
        val (code, body) = call("GET", "/api/transfer/state?id=$id" + if (withHashes) "&hashes=1" else "")
        if (code == 404) return null
        if (code != 200) throw IOException("TV : $code ${body.take(80)}")
        return parseState(body)
    }

    override fun finish(id: String, root: String): TransferApi.Finish {
        val (code, body) = call("POST", "/api/transfer/finish?id=$id&root=$root")
        return when {
            code == 200 -> TransferApi.Finish.Done
            // the TV is still verifying (a first call is reading the file back) or lost the session: neither is a refusal, the phone resumes by begin
            code == 503 || code == 404 -> { if ("verifying" in body) throw TransferApi.Verifying(TvClient.num(body, "retryMs") ?: 2000) else throw IOException("TV : transfert à reprendre ($code)") }
            code == 409 -> TransferApi.Finish.Missing(parseState(body)?.map ?: BlockMap(0))
            code == 422 -> TransferApi.Finish.Corrupt(parseState(body)?.map ?: BlockMap(0))
            code in 400..499 -> TransferApi.Finish.Refused(TvClient.str(body, "message") ?: TvClient.str(body, "error") ?: "refusé ($code)")
            else -> throw IOException("TV : $code ${body.take(80)}")
        }
    }

    override fun abort(id: String) { runCatching { call("POST", "/api/transfer/abort?id=$id") } }

    private fun parse(m: Manifest, body: String): TransferApi.Begin? =
        if (body.contains("\"done\":true") && !body.contains("\"map\"")) TransferApi.Begin(true, m.id, BlockMap(m.blocks), emptyList(), 0, 4, "")
        else parseState(body)

    private fun parseState(body: String): TransferApi.Begin? {
        val blocks = TvClient.num(body, "blocks")?.toInt() ?: return null
        val map = BlockMap.fromHex(blocks, TvClient.str(body, "map") ?: "")
        val hashes = (TvClient.str(body, "hashes") ?: "").let { if (it.isEmpty()) emptyList() else it.split(',') }
        return TransferApi.Begin(false, TvClient.str(body, "id") ?: "", map, hashes, TvClient.num(body, "writeBps") ?: 0, (TvClient.num(body, "maxStreams") ?: 4).toInt(), TvClient.str(body, "volume") ?: "", TvClient.str(body, "note"), body.contains("\"ordered\":true"))
    }
}

/**
 * Sends one file over every lane at once: begin → work-stealing over the missing blocks → finish (the TV reads the file back and compares
 * hashes) → rename. A resumed transfer (same file, same name) continues from the TV's block map, even after the phone restarted.
 */
class TransferClient(
    private val api: TransferApi,
    private val source: BlockSource,
    private val name: String,
    /** Builds the lanes once the TV's id is known; [maxStreams] is what the TV allows. */
    private val lanes: (id: String, maxStreams: Int) -> List<Lane>,
    private val target: String? = null,
    private val compress: Boolean = true,
    private val cancelled: () -> Boolean = { false },
    private val onProgress: (sent: Long, total: Long) -> Unit = { _, _ -> },
    private val onWaiting: (String) -> Unit = {},
    private val onEvent: (String) -> Unit = {},
    private val sleep: (Long) -> Unit = Thread::sleep,
    private val retryDelayMs: Long = 2000,
    /** Bench: "network alone" (the TV drops the bytes after hashing them). */
    private val discard: Boolean = false,
    /** The TV's measured disk speed (bytes/s, re-measured all along: a drive slows down when its cache is full) and its French hint when the disk is the limit. */
    private val onDisk: (bps: Long, note: String?) -> Unit = { _, _ -> },
) {
    sealed class Result {
        object Done : Result()
        /** The TV has no multi-lane protocol: use [castbridge.core.tv.ResumableUpload]. */
        object Unsupported : Result()
        class Failed(val reason: String) : Result()
        object Cancelled : Result()
    }
    @Volatile var manifest: Manifest = Manifest.of(name, source.size); private set
    /** Bytes per lane id after the run (report, fairness). */
    val perLane = LinkedHashMap<String, Long>()
    @Volatile var duplicates = 0L; private set
    @Volatile var rounds = 0; private set

    fun run(): Result {
        val m = manifest
        val caps = try { api.caps() } catch (e: TransferApi.Refused) { return Result.Failed(e.message ?: "refusé") } catch (e: IOException) { return waitThen { run() } } ?: return Result.Unsupported
        var attempts = 0
        while (!cancelled()) {
            attempts++
            val b = try { api.begin(m, target, discard) }
                catch (e: TransferApi.Refused) { return if (e.http == 501) Result.Unsupported else Result.Failed(e.message ?: "refusé") }
                catch (e: IOException) { onWaiting(castbridge.core.trust.LinkText.failure(e)); sleep(retryWait(e)); continue }
            if (b.done) { onProgress(m.size, m.size); return Result.Done }
            val hashes = HashBook(m, source)
            val full = try { api.state(b.id, withHashes = true) } catch (e: IOException) { null } ?: b
            full.hashes.forEachIndexed { i, h -> if (i < m.blocks && full.map.has(i)) hashes.preload(i, h) }
            val ls = lanes(b.id, minOf(caps.maxStreams, b.maxStreams))
            val sched = Scheduler(m, full.map, ls, { c -> SendContext(m, source, hashes, c, compress) }, slowFromHead = full.ordered || b.ordered, listener = object : Scheduler.Listener() {
                override fun progress(doneBytes: Long, total: Long) { onProgress(doneBytes, total) }
                override fun waiting(reason: String) { onWaiting(reason) }
                override fun laneEvent(lane: String, what: String) { onEvent("$lane : $what") }
            })
            if (full.map.count() > 0) onProgress((0 until m.blocks).filter { full.map.has(it) }.sumOf { m.length(it).toLong() }, m.size)
            rounds++
            val poll = Thread { while (!Thread.currentThread().isInterrupted) { try { Thread.sleep(2000); api.state(b.id)?.let { onDisk(it.writeBps, it.note) } } catch (_: InterruptedException) { return@Thread } catch (_: Exception) { } } }.apply { isDaemon = true; start() }
            val r = try { sched.run(cancelled) } finally { poll.interrupt(); ls.forEach { runCatching { it.close() } } }
            ls.forEach { perLane[it.id] = (perLane[it.id] ?: 0) + it.sent.get() }
            duplicates += sched.duplicates.get()
            when (r) {
                Scheduler.Result.Cancelled -> return Result.Cancelled
                is Scheduler.Result.Failed -> return Result.Failed(r.reason)
                Scheduler.Result.SessionLost -> { onEvent("la TV a perdu le transfert : reprise"); if (attempts > 6) return Result.Failed("la TV ne garde pas le transfert"); continue }
                Scheduler.Result.Done -> {
                    val fin = try { api.finish(b.id, Manifest.root(hashes.all())) } catch (e: IOException) { onWaiting(castbridge.core.trust.LinkText.failure(e)); sleep(retryWait(e)); continue }
                    when (fin) {
                        TransferApi.Finish.Done -> { onProgress(m.size, m.size); return Result.Done }
                        is TransferApi.Finish.Refused -> return Result.Failed(fin.message)
                        is TransferApi.Finish.Missing, is TransferApi.Finish.Corrupt -> { onEvent("vérification finale : des blocs sont renvoyés"); if (attempts > 6) return Result.Failed("la TV refuse la copie après ${attempts} essais"); continue }
                    }
                }
            }
        }
        return Result.Cancelled
    }

    /** A 503 `verifying` says when to come back (the TV is reading the file back): wait that long, never hammer. */
    private fun retryWait(e: IOException): Long = (e as? TransferApi.Verifying)?.retryMs?.coerceIn(200, 30_000) ?: retryDelayMs

    private fun waitThen(again: () -> Result): Result { onWaiting("TV injoignable"); if (cancelled()) return Result.Cancelled; sleep(retryDelayMs); return again() }
}
