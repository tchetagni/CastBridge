package castbridge.core.xfer

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.GZIPOutputStream

/**
 * How many Wi-Fi connections to run at once: starts low, adds one while the aggregate rate keeps improving (by 8 % or more), settles
 * on the best count, backs off when the TV says it is busy, and probes upwards again now and then. Pure: the lane feeds it one rate
 * sample per window.
 */
class KController(val min: Int = 1, var max: Int = 8, start: Int = 2, private val holdWindows: Int = 8, private val gain: Double = 1.08) {
    @Volatile var k: Int = start.coerceIn(min, max); private set
    private var bestK = k
    private var bestRate = 0.0
    private var probing = true
    private var held = 0

    @Synchronized fun onSample(bytesPerSec: Double): Int {
        if (probing) {
            if (bestRate == 0.0 || bytesPerSec > bestRate * gain) { bestRate = bytesPerSec; bestK = k; if (k < max) k++ else probing = false }
            else { k = bestK; probing = false; held = 0 }
        } else {
            held++
            if (bytesPerSec < bestRate * 0.6 && bestK > min) { bestK--; k = bestK; bestRate = bytesPerSec }
            else if (held >= holdWindows) { held = 0; bestRate = maxOf(bytesPerSec, bestRate * 0.9); if (k < max) { k = bestK + 1; probing = true } }
        }
        k = k.coerceIn(min, max)
        return k
    }
    @Synchronized fun onBusy() { k = maxOf(min, k - 1); bestK = k; probing = false; held = 0 }
    @Synchronized fun limit(m: Int) { max = m.coerceAtLeast(min); if (k > max) k = max; if (bestK > max) bestK = max }
}

/** Shared by the lanes that talk HTTP to the TV's `/api/transfer`. */
internal class ChunkClient(val host: String, val id: String, private val credential: () -> String?) {
    fun auth(): String? = castbridge.core.trust.TvCredential.headerLine(credential())

    /** Maps the TV's reply to an [Outcome]. */
    fun outcome(r: HttpConn.Reply, bytes: Long): Outcome = statusOutcome(r.status, r.body, bytes)
}

/** The one table that maps a status of the TV's `/api/transfer` to an [Outcome] (also used for a reply salvaged after a failed write, see [WriteFailureClassifier]). */
internal fun statusOutcome(status: Int, body: String, bytes: Long): Outcome = when {
    status == 200 -> if ("\"already\":true" in body) Outcome.Already else Outcome.Ok(bytes)
    status == 422 -> Outcome.Corrupt("hash")
    status == 429 -> Outcome.Busy(Regex("\"retryMs\":(\\d+)").find(body)?.groupValues?.get(1)?.toLongOrNull() ?: 200)
    status == 404 -> Outcome.SessionLost
    status == 401 -> Outcome.Failed("Autorisation de la TV expirée : reconnectez le téléphone à la TV (401)", fatal = true)
    status == 403 -> Outcome.Failed("autorisation refusée par la TV (403)", fatal = true)
    status == 507 || status == 413 -> Outcome.Failed("la TV n'a plus de place ($status)", fatal = true)
    // a 400 that says « interrupted » / retry (TV 0.14.25-26 answered it for a stalled read) is a link problem, not a refusal
    status == 400 -> Outcome.Failed("requête refusée : ${body.take(120)}", fatal = !("interrupted" in body || "\"retry\":true" in body))
    else -> Outcome.Failed("TV : $status ${body.take(80)}")
}

/**
 * Wi-Fi: [maxWorkers] persistent connections in parallel, the number in use adapting to the measured rate ([KController]).
 * [connect] decides which network path the connections take (the Wi-Fi LAN, or Wi-Fi Direct, see [WifiDirectLane]).
 */
open class WifiLane(
    override val id: String,
    private val host: String,
    private val transferId: String,
    private val connect: () -> java.nio.channels.SocketChannel,
    private val credential: () -> String?,
    maxStreams: Int = 8,
    startStreams: Int = 2,
    /** Pin K (benchmark, tests): disables the adaptation. */
    private val fixedK: Int? = null,
    private val clock: () -> Long = System::nanoTime,
    private val onBusy: () -> Unit = {},
) : Lane {
    override val maxWorkers: Int = (fixedK ?: maxStreams).coerceIn(1, 8)
    override val sent = AtomicLong()
    /** Bytes that really crossed the link (after compression) for confirmed blocks. */
    val wire = AtomicLong()
    override fun bytesMoved(): Long = wire.get()
    private val ctl = KController(1, maxWorkers, startStreams.coerceAtMost(maxWorkers))
    private val cc = ChunkClient(host, transferId, credential)
    private val conns = arrayOfNulls<HttpConn>(8)
    private var winStart = 0L; private var winBytes = 0L
    private val gzSlots = java.util.concurrent.Semaphore(2)

    override fun allowedWorkers(): Int = fixedK ?: ctl.k
    val streams: Int get() = allowedWorkers()

    private fun sample(bytes: Long) = synchronized(this) {
        val now = clock()
        if (winStart == 0L) winStart = now
        winBytes += bytes
        val dt = now - winStart
        if (dt >= 1_500_000_000L) { if (fixedK == null) ctl.onSample(winBytes * 1e9 / dt); winStart = now; winBytes = 0 }
    }

    override fun send(worker: Int, idx: Int, ctx: SendContext): Outcome {
        val m = ctx.manifest
        val conn = conns[worker] ?: HttpConn(connect).also { conns[worker] = it }
        val len = m.length(idx).toLong()
        val sha = ctx.hashes.get(idx)
        var gz: ByteArray? = null
        // R-20: a compressed block lives in the heap twice (raw + gzip); at most 2 blocks at a time, whatever the number of streams
        if (ctx.compress && Compression.worthTrying(m.name)) { gzSlots.acquire(); try { gz = compressed(ctx, idx, len.toInt()) } finally { gzSlots.release() } }
        val headers = ArrayList<String>().apply {
            add("Content-Type: application/octet-stream"); add("X-CB-Sha256: $sha")
            if (gz != null) add("X-CB-Enc: gzip")
            cc.auth()?.let { add(it) }
        }
        val wire = gz?.size?.toLong() ?: len
        val r = try {
            conn.request("PUT", "/api/transfer/chunk?id=$transferId&idx=$idx", host, headers, wire) { out ->
                if (gz != null) {
                    var o = 0; while (o < gz.size) { if (ctx.cancelled()) throw IOException("cancelled"); val n = minOf(256 * 1024, gz.size - o); val bb = java.nio.ByteBuffer.wrap(gz, o, n); while (bb.hasRemaining()) out.write(bb); o += n; conn.progress(n.toLong()) }
                } else {
                    var pos = m.offset(idx); var left = len
                    while (left > 0) {
                        if (ctx.cancelled()) throw IOException("cancelled")
                        val n = ctx.source.transferTo(pos, minOf(left, 1L shl 20), out)
                        if (n <= 0) throw IOException("source ended early")
                        pos += n; left -= n; conn.progress(n)
                    }
                }
            }
        } catch (e: IOException) {
            return if (ctx.cancelled()) Outcome.Cancelled else WriteFailureClassifier.classify(e, null)
        }
        val o = cc.outcome(r, len)
        if (o is Outcome.Ok) { sample(wire); this.wire.addAndGet(wire) }
        if (o is Outcome.Busy) { ctl.onBusy(); onBusy() }
        if (!r.keepAlive) { conn.close(); conns[worker] = null }
        return o
    }

    /** gzip of the block, or null when it would not shrink by at least 10 % (then the block is sent as it is). */
    private fun compressed(ctx: SendContext, idx: Int, len: Int): ByteArray? {
        val raw = ByteArray(len); var n = 0
        while (n < len) { val r = ctx.source.read(ctx.manifest.offset(idx) + n, raw, n, len - n); if (r <= 0) throw IOException("source ended early"); n += r }
        val bos = ByteArrayOutputStream(len / 2 + 64)
        GZIPOutputStream(bos, 64 * 1024).use { it.write(raw) }
        return bos.toByteArray().takeIf { it.size < len * 0.9 }
    }

    override fun close() { conns.forEach { it?.close() } }
}

/** Wi-Fi Direct: the same lane over the group-owner address. Experimental: no worker runs unless [LaneSwitches.wifiDirect] is on. */
class WifiDirectLane(host: String, transferId: String, connect: () -> java.nio.channels.SocketChannel, credential: () -> String?) :
    WifiLane("wifidirect", host, transferId, connect, credential, maxStreams = 4, startStreams = 1) {
    override fun allowedWorkers(): Int = if (LaneSwitches.wifiDirect) super.allowedWorkers() else 0
}

/**
 * A slow link (Bluetooth RFCOMM, either the CBT1 channel or the API tunnel): one worker, 256 KiB slices, concurrent with the others.
 * The TV assembles the slices and checks the whole block's hash. [connect] reaches the TV's `/api/transfer` through that link.
 */
class BluetoothLane(
    override val id: String = "bluetooth",
    private val host: String,
    private val transferId: String,
    private val connect: () -> java.nio.channels.SocketChannel,
    private val credential: () -> String?,
) : Lane {
    override val slow = true
    override val maxWorkers = 1
    override val sent = AtomicLong()
    private val cc = ChunkClient(host, transferId, credential)
    private var conn: HttpConn? = null
    /** Slice bytes the TV acknowledged: a slow link moves bytes long before a whole block is confirmed (R-17: not « stuck »). */
    private val moved = AtomicLong()
    override fun bytesMoved(): Long = moved.get()

    override fun send(worker: Int, idx: Int, ctx: SendContext): Outcome {
        val m = ctx.manifest
        val c = conn ?: HttpConn(connect, stallMs = 60_000, bufferBytes = 64 * 1024).also { conn = it }
        val sha = ctx.hashes.get(idx)
        val buf = ByteArray(Manifest.SLICE)
        var last: Outcome = Outcome.Ok(0)
        var total = 0L
        for (k in 0 until m.slices(idx)) {
            if (ctx.cancelled()) return Outcome.Cancelled
            val n = m.sliceLength(idx, k)
            var o = 0
            while (o < n) { val r = ctx.source.read(m.offset(idx) + k.toLong() * Manifest.SLICE + o, buf, o, n - o); if (r <= 0) return Outcome.Failed("source ended early"); o += r }
            val headers = ArrayList<String>().apply { add("Content-Type: application/octet-stream"); add("X-CB-Sha256: $sha"); cc.auth()?.let { add(it) } }
            val r = try {
                c.request("PUT", "/api/transfer/chunk?id=$transferId&idx=$idx&slice=$k", host, headers, n.toLong()) { out ->
                    val bb = java.nio.ByteBuffer.wrap(buf, 0, n); while (bb.hasRemaining()) { if (ctx.cancelled()) throw IOException("cancelled"); out.write(bb); c.progress(n.toLong()) }
                }
            } catch (e: IOException) { return if (ctx.cancelled()) Outcome.Cancelled else WriteFailureClassifier.classify(e, null) }
            last = cc.outcome(r, n.toLong())
            if (!r.keepAlive) { c.close(); conn = null }
            when (last) { is Outcome.Ok -> { total += n; moved.addAndGet(n.toLong()) }; Outcome.Already -> return Outcome.Already; else -> return last }
        }
        return Outcome.Ok(total)
    }

    override fun close() { conn?.close() }
}
