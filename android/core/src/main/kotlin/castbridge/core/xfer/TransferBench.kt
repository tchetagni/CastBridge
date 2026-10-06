package castbridge.core.xfer

import castbridge.core.tv.TvClient
import java.io.File
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/**
 * Throughput bench (JVM, command line): `tools/transfer-bench/run.sh --tv http://192.168.1.20:8765 --pin 123456`.
 * Sends a random file of --size to the TV with K = 1, 2, 4, 8 connections (and once the old single-stream way for reference), prints MB/s
 * per setting, the TV's own disk write speed, and what most likely limits the rate. Test files are deleted from the TV afterwards.
 * `--simulate` runs the same engine against a local TV with scripted speeds (per-connection cap, shared Wi-Fi cap, disk cap, Bluetooth).
 */
object TransferBench {
    class Row(val label: String, val seconds: Double, val bytes: Long, val diskBps: Long, val extra: String = "") { val mbps get() = bytes / seconds / 1e6 }

    @JvmStatic fun main(args: Array<String>) {
        val a = parse(args)
        if ("help" in a || (a["tv"] == null && "simulate" !in a)) { println(USAGE); return }
        val size = size(a["size"] ?: "64M")
        val ks = (a["k"] ?: "1,2,4,8").split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..8 }
        val rows = if ("simulate" in a) simulate(a, size, ks) else real(a, size, ks)
        print(table(rows, size))
    }

    // ---- real TV ----
    private fun real(a: Map<String, String>, size: Long, ks: List<Int>): List<Row> {
        val base = a["tv"]!!.trimEnd('/').let { if (it.startsWith("http")) it else "http://$it" }
        val pin = a["pin"] ?: System.getenv("CASTBRIDGE_PIN")
        val hostPort = java.net.URI(base).let { "${it.host}:${if (it.port > 0) it.port else 8765}" }
        val host = hostPort.substringBefore(':'); val port = hostPort.substringAfter(':').toInt()
        val file = File.createTempFile("cbbench", ".bin").apply { deleteOnExit() }
        println("Génération de ${fmt(size)} aléatoires…"); randomFile(file, size)
        val rows = ArrayList<Row>()
        val api = HttpTransferApi(base) { pin }
        val caps = api.caps()
        // reference: the existing single-stream upload
        if ("no-legacy" !in a) rows += legacy(base, pin, file, size)
        if (caps == null) { println("Cette TV n'a pas le transfert multivoie : seul l'envoi classique est mesuré."); return rows }
        // 1) network alone: the TV hashes the bytes and drops them (no disk); 2) network + disk: the real copy. The gap says which one limits.
        for (k in ks) rows += oneRun(api, file, size, "cbbench-${System.currentTimeMillis()}-n$k.bin", k, host, port, pin, caps.maxStreams, discard = true)
        for (k in ks) {
            val name = "cbbench-${System.currentTimeMillis()}-k$k.bin"
            rows += oneRun(api, file, size, name, k, host, port, pin, caps.maxStreams, discard = false)
            runCatching { TvClient(base, pin).delete(name) }
        }
        return rows
    }

    private fun legacy(base: String, pin: String?, f: File, size: Long): Row {
        val name = "cbbench-${System.currentTimeMillis()}-legacy.bin"
        val t0 = System.nanoTime()
        val up = castbridge.core.tv.ResumableUpload(name, size, { base }, { off -> java.io.FileInputStream(f).also { it.channel.position(off) } }, pin = pin)
        val r = up.run { }
        val s = (System.nanoTime() - t0) / 1e9
        runCatching { TvClient(base, pin).delete(name) }
        return Row("classique (1 flux)", s, if (r == castbridge.core.tv.ResumableUpload.State.Done) size else 0, 0)
    }

    private fun oneRun(api: TransferApi, f: File, size: Long, name: String, k: Int, host: String, port: Int, pin: String?, tvMax: Int, discard: Boolean): Row {
        val ch = FileChannel.open(f.toPath(), StandardOpenOption.READ)
        val m = Manifest.of(name, size)
        val disk = AtomicLong()
        val poll = Thread { while (!Thread.currentThread().isInterrupted) { try { Thread.sleep(500); api.state(m.id)?.let { disk.set(maxOf(disk.get(), it.writeBps)) } } catch (_: Exception) { } } }.apply { isDaemon = true; start() }
        val t0 = System.nanoTime()
        val tc = TransferClient(api, FileBlockSource(ch), name, { id, max ->
            listOf(WifiLane("wifi", "$host:$port", id, HttpConn.tcp(host, port), { pin }, maxStreams = max, fixedK = minOf(k, max)))
        }, compress = false, discard = discard)
        val res = try { tc.run() } finally { poll.interrupt(); ch.close() }
        val s = (System.nanoTime() - t0) / 1e9
        val note = if (k > tvMax) " (la TV limite à $tvMax)" else ""
        return Row((if (discard) "réseau seul K=$k" else "réseau + disque K=$k") + note, s, if (res == TransferClient.Result.Done) size else 0, disk.get(), if (res == TransferClient.Result.Done) "" else "échec : $res")
    }

    // ---- simulation: the real engine and server on loopback, speeds scripted on the lanes ----
    private fun simulate(a: Map<String, String>, size: Long, ks: List<Int>): List<Row> {
        val perConn = size(a["conn"] ?: "3M").toDouble(); val wifiTotal = size(a["wifi"] ?: "20M").toDouble()
        val diskBps = size(a["disk"] ?: "6M").toDouble(); val btBps = size(a["bt"] ?: "150K").toDouble()
        println("Simulation (modèle, pas une mesure) : ${fmt(perConn.toLong())}/s par connexion, ${fmt(wifiTotal.toLong())}/s au total sur le Wi-Fi, disque TV ${fmt(diskBps.toLong())}/s, Bluetooth ${fmt(btBps.toLong())}/s")
        val dir = kotlin.io.path.createTempDirectory("cbsim").toFile()
        val reg = castbridge.core.tv.VolumeRegistry.single(File(dir, "tv").apply { mkdirs() })
        val port = java.net.ServerSocket(0).use { it.localPort }
        val server = castbridge.core.tv.ReceiverServer(reg, object : castbridge.core.tv.Player {
            override fun play(file: File, posMs: Long) {}; override fun playStream(url: String, name: String, posMs: Long) {}
            override fun pause() {}; override fun resume() {}; override fun stop() {}; override fun seek(posMs: Long) {}
            override fun state() = castbridge.core.tv.PlayerState()
        }, port, profile = castbridge.core.tv.TvProfile(minFreeBytes = 0, minFreeAfterTransfer = 0)).apply { start(5000, false) }
        val f = File(dir, "src.bin"); randomFile(f, size)
        val rows = ArrayList<Row>()
        try {
            val api = HttpTransferApi("http://127.0.0.1:$port") { null }
            val wifiPipe = Bucket(wifiTotal); val diskPipe = Bucket(diskBps)
            val lowCap = if ("low" in a) 2 else 8      // « ressources faibles » of the TV: maxStreams 2 announced in the caps (R-20)
            fun run(label: String, k: Int?, bt: Boolean, discard: Boolean = false): Row {
                val name = "sim-${label.hashCode()}.bin"
                val ch = FileChannel.open(f.toPath(), StandardOpenOption.READ)
                val inflight = java.util.concurrent.atomic.AtomicInteger(); val peak = java.util.concurrent.atomic.AtomicInteger()
                val stamps = ArrayList<Pair<Long, Long>>()      // (ms since start, bytes done): what the queue service would see every second
                var wifi: WifiLane? = null
                val t0 = System.nanoTime()
                val tc = TransferClient(api, FileBlockSource(ch), name, { id, max ->
                    buildList<Lane> {
                        val w = WifiLane("wifi", "127.0.0.1:$port", id, HttpConn.tcp("127.0.0.1", port), { null }, maxStreams = minOf(8, max, lowCap), fixedK = k?.let { minOf(it, lowCap) }).also { wifi = it }
                        add(Shaped(w, perConn, if (discard) listOf(wifiPipe) else listOf(wifiPipe, diskPipe), inflight = inflight, peak = peak))
                        if (bt) add(Shaped(BluetoothLane("bluetooth", "127.0.0.1:$port", id, HttpConn.tcp("127.0.0.1", port), { null }), btBps, emptyList(), slowLane = true, inflight = inflight, peak = peak))
                    }
                }, compress = false, discard = discard, onProgress = { d, _ -> synchronized(stamps) { stamps += (System.nanoTime() - t0) / 1_000_000 to d } })
                tc.run(); ch.close()
                val s = (System.nanoTime() - t0) / 1e9
                val bs = tc.manifest.blockSize.toLong()
                val (oldN, newN) = notifications(stamps, s, size)
                val extra = tc.perLane.entries.joinToString(" ") { "${it.key}=${fmt(it.value)}" } + " | K final=${wifi?.streams} | en vol max ${peak.get()} bloc(s) = ${fmt(peak.get() * bs)} | notif ancienne ${oldN} / nouvelle ${newN}"
                return Row(label, s, size, diskBps.toLong(), extra)
            }
            for (k in ks) rows += run("réseau seul K=$k", k, false, discard = true)
            for (k in ks) rows += run("réseau + disque K=$k", k, false)
            rows += run("réseau + disque K auto", null, false)
            rows += run("réseau + disque K=${ks.max()} + Bluetooth", ks.max(), true)
        } finally { server.stop(); dir.deleteRecursively() }
        return rows
    }

    /** Shared rate limit (bytes/s) across threads. */
    private class Bucket(val bps: Double) {
        private var free = System.nanoTime()
        @Synchronized fun take(bytes: Long): Long { val now = System.nanoTime(); val start = maxOf(now, free); free = start + (bytes / bps * 1e9).toLong(); return free - now }
    }
    /**
     * Notifications the queue service would post during the run: OLD = one every 2 s whatever happens (the 2 s tick of before R-20);
     * NEW = a 1 s look through [NotificationGate] (text/percentage changed, spaced).
     */
    fun notifications(stamps: List<Pair<Long, Long>>, seconds: Double, size: Long): Pair<Int, Int> {
        val old = (seconds / 2).toInt() + 1
        val gate = NotificationGate()
        var t = 0L; val end = (seconds * 1000).toLong()
        while (t <= end) {
            val done = synchronized(stamps) { stamps.lastOrNull { it.first <= t }?.second ?: 0L }
            gate.shouldPost("« sim.avi »", if (size > 0) (done * 100 / size).toInt() else null, t)
            t += 1000
        }
        return old to gate.posted
    }

    private class Shaped(val inner: Lane, val perConn: Double, val pipes: List<Bucket>, val slowLane: Boolean = false,
                         val inflight: java.util.concurrent.atomic.AtomicInteger = java.util.concurrent.atomic.AtomicInteger(), val peak: java.util.concurrent.atomic.AtomicInteger = java.util.concurrent.atomic.AtomicInteger()) : Lane by inner {
        override val slow get() = slowLane || inner.slow
        override fun send(worker: Int, idx: Int, ctx: SendContext): Outcome {
            val n = inflight.incrementAndGet(); peak.updateAndGet { maxOf(it, n) }
            try { return sendShaped(worker, idx, ctx) } finally { inflight.decrementAndGet() }
        }
        private fun sendShaped(worker: Int, idx: Int, ctx: SendContext): Outcome {
            val t0 = System.nanoTime(); val o = inner.send(worker, idx, ctx)
            if (o is Outcome.Ok) {
                val len = o.bytes
                var wait = (len / perConn * 1e9).toLong() - (System.nanoTime() - t0)
                for (p in pipes) wait = maxOf(wait, p.take(len))
                val end = System.nanoTime() + wait
                while (System.nanoTime() < end && !ctx.cancelled()) Thread.sleep(5)
            }
            return o
        }
    }

    // ---- output ----
    fun table(rows: List<Row>, size: Long): String {
        val sb = StringBuilder("\nFichier de test : ${fmt(size)}\n")
        sb.append(String.format(Locale.ROOT, "%-34s %10s %10s %12s  %s\n", "Réglage", "Durée (s)", "Mo/s", "Disque TV", "Remarque"))
        for (r in rows) sb.append(String.format(Locale.ROOT, "%-34s %10.1f %10.2f %12s  %s\n", r.label, r.seconds, r.mbps, if (r.diskBps > 0) fmt(r.diskBps) + "/s" else "-", r.extra))
        val net = rows.filter { it.bytes > 0 && it.label.startsWith("réseau seul") }
        val both = rows.filter { it.bytes > 0 && it.label.startsWith("réseau + disque") && '+' !in it.label.removePrefix("réseau + disque") }
        if (both.isNotEmpty()) {
            val bestB = both.maxBy { it.mbps }; val disk = both.maxOf { it.diskBps } / 1e6
            sb.append("\nMeilleur réglage réseau + disque : ${bestB.label} à ${String.format(Locale.ROOT, "%.2f", bestB.mbps)} Mo/s.\n")
            if (net.isNotEmpty()) {
                val bestN = net.maxBy { it.mbps }
                sb.append("Réseau seul (sans disque) : jusqu'à ${String.format(Locale.ROOT, "%.2f", bestN.mbps)} Mo/s.\n")
                sb.append(when {
                    bestB.mbps < bestN.mbps * 0.75 -> "Borne : le DISQUE de la TV (${String.format(Locale.ROOT, "%.1f", bestB.mbps)} Mo/s copiés pour ${String.format(Locale.ROOT, "%.1f", bestN.mbps)} Mo/s que le réseau sait livrer" + (if (disk > 0) ", écriture annoncée ≈ ${String.format(Locale.ROOT, "%.1f", disk)} Mo/s" else "") + "). Plus de connexions n'aideront pas : un support plus rapide, si.\n"
                    else -> "Borne : le RÉSEAU (Wi-Fi) : le disque suit. Plus de connexions ou un meilleur signal aident.\n"
                })
            } else sb.append("Sans la mesure « réseau seul » (TV trop ancienne) : ${if (disk > 0 && bestB.mbps >= disk * 0.8) "le disque de la TV est la borne probable" else "borne indéterminée"}.\n")
        }
        return sb.toString()
    }

    private fun randomFile(f: File, size: Long) {
        val r = java.util.Random(42); val b = ByteArray(1 shl 20)
        f.outputStream().buffered(1 shl 20).use { o -> var left = size; while (left > 0) { r.nextBytes(b); val n = minOf(left, b.size.toLong()).toInt(); o.write(b, 0, n); left -= n } }
    }
    fun fmt(b: Long): String = when { b >= 1L shl 30 -> String.format(Locale.ROOT, "%.1f Go", b / 1073741824.0); b >= 1L shl 20 -> String.format(Locale.ROOT, "%.1f Mo", b / 1048576.0); b >= 1024 -> "${b / 1024} Ko"; else -> "$b o" }
    fun size(s: String): Long { val t = s.trim().uppercase(); val mult = when (t.last()) { 'K' -> 1L shl 10; 'M' -> 1L shl 20; 'G' -> 1L shl 30; else -> 1L }; return (t.dropLastWhile { it.isLetter() }.toDouble() * mult).toLong() }
    private fun parse(args: Array<String>): Map<String, String> {
        val m = HashMap<String, String>(); var i = 0
        while (i < args.size) { val k = args[i].removePrefix("--"); if (i + 1 < args.size && !args[i + 1].startsWith("--")) { m[k] = args[i + 1]; i += 2 } else { m[k] = ""; i++ } }
        return m
    }
    const val USAGE = """Banc de mesure du transfert multivoie
  --tv http://IP:8765     la TV à mesurer (code dans --pin ou CASTBRIDGE_PIN)
  --size 64M              taille du fichier de test (K, M, G)
  --k 1,2,4,8             nombres de connexions à essayer
  --no-legacy             ne mesure pas l'envoi classique (1 flux)
  (chaque réglage est mesuré deux fois : « réseau seul » = la TV jette les octets après les avoir vérifiés ; « réseau + disque » = la vraie copie)
  --simulate              sans TV : modèle à vitesses scriptées (--conn 3M --wifi 20M --disk 6M --bt 150K)
  --low                   (avec --simulate) la TV annonce un profil « ressources faibles » : 2 flux au plus (R-20)"""
}
