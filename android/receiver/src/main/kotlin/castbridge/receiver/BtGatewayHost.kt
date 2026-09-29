package castbridge.receiver

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.content.Context
import android.util.Log
import castbridge.core.gateway.Entry
import castbridge.core.gateway.Gw
import castbridge.core.gateway.Mux
import castbridge.core.tv.ApiReply
import castbridge.core.tv.PinGuard
import castbridge.core.tv.ReceiverServer
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.util.UUID

/**
 * Internet through the phone over Bluetooth: the phone app connects to this RFCOMM service and becomes the TV
 * app's gateway; the TV app's own network calls (quiz questions, updates, downloads) go through the local SOCKS5
 * proxy [PORT] while a phone is attached. It does not change the TV's system-wide connection.
 */
@SuppressLint("MissingPermission")
class BtGatewayHost(private val ctx: Context, private val guard: PinGuard, private val status: (String?) -> Unit) {
    private val entry: Entry = Entry({ pin -> guard.check("bt-gateway", pin) == PinGuard.Result.OK }, PORT) { m -> Log.i(TAG, m); refresh() }
    @Volatile private var server: BluetoothServerSocket? = null
    @Volatile private var running = false

    fun start() {
        if (running) return
        val ad = ctx.getSystemService(BluetoothManager::class.java)?.adapter ?: return
        try {
            entry.startSocks()
            val ss = ad.listenUsingRfcommWithServiceRecord("CastBridge Internet", UUID.fromString(Gw.SERVICE_UUID))
            server = ss; running = true
            Thread({
                while (running) {
                    val sock = try { ss.accept() } catch (e: IOException) { break }
                    val peer = runCatching { sock.remoteDevice.name ?: sock.remoteDevice.address }.getOrDefault("téléphone")
                    Thread({
                        try { entry.attach(Mux(sock.inputStream, sock.outputStream), peer) }
                        catch (e: Exception) { Log.w(TAG, "gateway link: ${e.javaClass.simpleName}") }
                        finally { runCatching { sock.close() }; refresh() }
                    }, "gw-link").apply { isDaemon = true; start() }
                }
            }, "gw-accept").apply { isDaemon = true; start() }
            refresh()
        } catch (e: Exception) { Log.w(TAG, "gateway start", e); entry.stop() }
    }

    fun stop() { running = false; runCatching { server?.close() }; entry.stop() }

    private fun refresh(): Unit = status(if (entry.connected) "Internet via le téléphone (${entry.peerName})" else null)

    /** Proxy for the app's own connections when a phone shares its Internet, else null (use the TV's own network). */
    fun proxy(): Proxy? = if (entry.connected) Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", PORT)) else null

    fun json(): String {
        val (rx, tx) = entry.stats()
        return """{"listening":$running,"connected":${entry.connected},"phone":${entry.peerName?.let(ReceiverServer::q) ?: "null"},""" +
            """"socksPort":$PORT,"streams":${entry.openStreams},"bytesFromPhone":$rx,"bytesToPhone":$tx}"""
    }

    /** Fetches small known pages through the phone and reports status and time: proves the path works end to end. */
    fun test(): ApiReply {
        val p = proxy() ?: return ApiReply(409, """{"error":"aucun téléphone ne partage sa connexion"}""")
        val urls = listOf("http://connectivitycheck.gstatic.com/generate_204", "https://www.google.com/generate_204", "https://example.com/")
        val out = urls.joinToString(",", "[", "]") { u ->
            val t0 = System.nanoTime()
            val r = runCatching {
                (URL(u).openConnection(p) as HttpURLConnection).run {
                    connectTimeout = 20_000; readTimeout = 20_000; instanceFollowRedirects = false
                    val code = responseCode
                    val n = (if (code < 400) inputStream else errorStream)?.use { it.readBytes().size } ?: 0
                    code to n
                }
            }
            val ms = (System.nanoTime() - t0) / 1_000_000
            r.fold({ (c, n) -> """{"url":${ReceiverServer.q(u)},"status":$c,"bytes":$n,"ms":$ms}""" },
                { e -> """{"url":${ReceiverServer.q(u)},"error":${ReceiverServer.q(e.javaClass.simpleName + ": " + (e.message ?: ""))},"ms":$ms}""" })
        }
        return ApiReply(200, """{"results":$out,"gateway":${json()}}""")
    }

    /**
     * Internet diagnostics: TCP connect time through the phone, then ping and traceroute run by the phone (ICMP
     * cannot cross the TCP tunnel), and the TV's own network ping for comparison. Lines go to [out] as they arrive.
     */
    fun diagnose(host: String, out: (String) -> Unit) {
        if (!castbridge.core.gateway.Gw.validHost(host)) { out("Adresse invalide"); return }
        out("— Réseau de la TV —")
        runCatching {
            val p = ProcessBuilder("/system/bin/ping", "-c", "3", "-W", "2", host).redirectErrorStream(true).start()
            p.inputStream.bufferedReader().forEachLine { if (it.isNotBlank()) out(it) }; p.waitFor()
        }.onFailure { out("ping indisponible sur la TV : ${it.message}") }
        if (!entry.connected) { out("— Aucun téléphone ne partage sa connexion en Bluetooth —"); return }
        out("— Via le téléphone ${entry.peerName ?: ""} (Bluetooth) —")
        repeat(3) { i -> out("connexion TCP ${i + 1} vers $host:443 : " + (entry.tcpPing(host)?.let { "$it ms" } ?: "échec")) }
        entry.diag("ping", host) { out(it) }
        entry.diag("trace", host, timeoutS = 120) { out(it) }
        out("— Terminé —")
    }

    /** Download speed test through the phone: [bytes] from a public speed-test endpoint. */
    fun speed(bytes: Long): ApiReply {
        val p = proxy() ?: return ApiReply(409, """{"error":"aucun téléphone ne partage sa connexion"}""")
        val n = bytes.coerceIn(100_000, 20_000_000)
        val u = "https://speed.cloudflare.com/__down?bytes=$n"
        val t0 = System.nanoTime()
        return runCatching {
            (URL(u).openConnection(p) as HttpURLConnection).run {
                connectTimeout = 20_000; readTimeout = 30_000
                var got = 0L; val buf = ByteArray(32 * 1024)
                inputStream.use { while (true) { val r = it.read(buf); if (r < 0) break; got += r } }
                val s = (System.nanoTime() - t0) / 1e9
                ApiReply(200, """{"bytes":$got,"seconds":${"%.2f".format(java.util.Locale.ROOT, s)},"kBps":${(got / 1024 / s).toLong()}}""")
            }
        }.getOrElse { ApiReply(502, """{"error":${ReceiverServer.q(it.javaClass.simpleName + ": " + (it.message ?: ""))}}""") }
    }

    companion object {
        private const val TAG = "CastBridgeGW"
        const val PORT = 1080
    }
}
