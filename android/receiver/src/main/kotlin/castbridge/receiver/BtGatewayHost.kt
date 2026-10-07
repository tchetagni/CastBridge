package castbridge.receiver

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.content.Context
import android.util.Log
import castbridge.core.gateway.Entry
import castbridge.core.gateway.GatewayService
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
 *
 * The service has its own UUID ([Gw.SERVICE_UUID], …0007). R-28-LEGACY-TV, for two TV versions: an OLD phone still asks for the UUID the gateway used to share with the SSH tunnel
 * (…0002, [Gw.LEGACY_SERVICE_UUID]); the TV listens there too, but only while its SSH over Bluetooth is not wanted ([sshBluetoothWanted], [sshBluetoothChanged]), so that UUID never
 * has two owners (castbridge.core.gateway.GatewayService).
 */
@SuppressLint("MissingPermission")
class BtGatewayHost(private val ctx: Context, private val guard: PinGuard, private val trusted: (String) -> Boolean = { false }, private val status: (String?) -> Unit,
                    /** Is the TV's SSH over Bluetooth wanted (it owns …0002 from before its listener opens until after it closes)? */
                    private val sshBluetoothWanted: () -> Boolean = { false }) {
    /** Address of the phone whose link is being attached on this thread (the paired device of the socket: what "trusted" is checked on). */
    private val peerAddress = ThreadLocal<String?>()
    private val entry: Entry = Entry({ pin ->
        // a trusted phone sends "no PIN"; anybody else (or a PIN sent on purpose) goes through the usual PIN check and lockout
        if (pin == castbridge.core.trust.TvAuth.NO_PIN) peerAddress.get()?.let(trusted) == true
        else guard.check("bt-gateway", pin) == PinGuard.Result.OK
    }, PORT) { m -> Log.i(TAG, m); refresh() }
    /** One server socket per UUID the gateway listens on (see [castbridge.core.gateway.GatewayService.tvListens]); guarded by `this`. */
    private val servers = HashMap<String, BluetoothServerSocket>()
    @Volatile private var running = false

    @Synchronized fun start() {
        if (running) return
        if (ctx.getSystemService(BluetoothManager::class.java)?.adapter == null) return
        try {
            entry.startSocks()
            listen(Gw.SERVICE_UUID)                  // the gateway's own service: if it cannot open there is no gateway, and nothing is marked as running (a later start() retries)
            refresh()
        } catch (e: Exception) { Log.w(TAG, "gateway start", e); entry.stop(); return }
        sshBluetoothChanged(sshBluetoothWanted())    // R-28-LEGACY-TV: the old UUID too, unless the SSH over Bluetooth owns it
    }

    /** Opens the RFCOMM service [uuid] and accepts the phones that connect to it, until that server socket is closed. Throws if the service cannot be opened. */
    private fun listen(uuid: String) {
        val ad = ctx.getSystemService(BluetoothManager::class.java)?.adapter ?: throw IOException("Bluetooth indisponible")
        val name = if (uuid == Gw.SERVICE_UUID) "CastBridge Internet" else "CastBridge Internet (ancien)"
        val ss = ad.listenUsingRfcommWithServiceRecord(name, UUID.fromString(uuid))
        servers[uuid] = ss; running = true
        Thread({
            while (running) {
                val sock = try { ss.accept() } catch (e: IOException) { break }
                val peer = runCatching { sock.remoteDevice.name ?: sock.remoteDevice.address }.getOrDefault("téléphone")
                val peerAddr = runCatching { sock.remoteDevice.address }.getOrNull()
                Thread({
                    val startedAt = System.currentTimeMillis()
                    var mux: Mux? = null
                    try { mux = Mux(sock.inputStream, sock.outputStream); peerAddress.set(peerAddr); entry.attach(mux, peer) }
                    catch (e: Exception) { Log.w(TAG, "gateway link: ${e.javaClass.simpleName}") }
                    finally { runCatching { sock.close() }; refresh(); mux?.let { m -> runCatching { sessionEnded(startedAt, m) } } }
                }, "gw-link").apply { isDaemon = true; start() }
            }
        }, "gw-accept-${uuid.takeLast(4)}").apply { isDaemon = true; start() }
    }

    /**
     * R-28-LEGACY-TV: the TV's SSH over Bluetooth is now [wanted] or no longer wanted. The old UUID (…0002) is the SSH tunnel's while it is, so the gateway gives it up first
     * (called BEFORE the SSH listener opens) and takes it back once the SSH is off. A link already accepted on it is not cut.
     */
    @Synchronized fun sshBluetoothChanged(wanted: Boolean) {
        if (!running) return                                    // start() reads the state itself
        val legacy = Gw.LEGACY_SERVICE_UUID in GatewayService.tvListens(wanted)
        val open = servers[Gw.LEGACY_SERVICE_UUID]
        if (!legacy && open != null) { servers.remove(Gw.LEGACY_SERVICE_UUID); runCatching { open.close() }; Log.i(TAG, "old gateway service closed: the SSH over Bluetooth owns it") }
        else if (legacy && open == null) try { listen(Gw.LEGACY_SERVICE_UUID); Log.i(TAG, "old gateway service open again") } catch (e: Exception) { Log.w(TAG, "old gateway service: ${e.javaClass.simpleName}") }
    }

    val connected get() = entry.connected
    val phoneName get() = entry.peerName
    @Volatile var connectedSince = 0L; private set

    /** Plain-language state of the gateway for the TV screen. */
    fun statusLines(): List<String> {
        if (!running) return listOf("Passerelle Bluetooth : arrêtée (Bluetooth indisponible ou non autorisé sur la TV)")
        if (!entry.connected) return listOf("Passerelle Bluetooth : prête, aucun téléphone connecté.",
            "Sur le téléphone : CastBridge › CastBridge TV › Bluetooth › choisir la TV › « Partager l'Internet du téléphone ».")
        val (rx, tx) = entry.stats()
        val mins = if (connectedSince > 0) (System.currentTimeMillis() - connectedSince) / 60000 else 0
        return listOf("Passerelle Bluetooth : ACTIVE via ${entry.peerName}", "Connectée depuis $mins min · ${entry.openStreams} connexion(s) en cours",
            "Reçu du téléphone : ${rx / 1024} ko · envoyé : ${tx / 1024} ko")
    }

    @Synchronized fun stop() { running = false; servers.values.forEach { s -> runCatching { s.close() } }; servers.clear(); entry.stop() }

    private fun refresh(): Unit = run {
        if (entry.connected && connectedSince == 0L) connectedSince = System.currentTimeMillis()
        if (!entry.connected) connectedSince = 0L
    }.let { status(if (entry.connected) "Internet via le téléphone (${entry.peerName})" else null) }

    /** gateway_session (docs/TELEMETRY.md): duration and volume of one phone link, never the phone's name. */
    private fun sessionEnded(startedAt: Long, mux: Mux) {
        TvConnect.track("gateway_session", mapOf("ms" to (System.currentTimeMillis() - startedAt).coerceAtLeast(0),
            "bytes" to mux.received.get() + mux.sent.get()))
    }

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
        out("══ Réseau de la TV ══")
        TvNetDiag.run(host, null, out)
        if (!entry.connected) { out("══ Passerelle du téléphone : aucun téléphone ne partage sa connexion en Bluetooth ══"); out("— Terminé —"); return }
        out("══ Via le téléphone ${entry.peerName ?: ""} (Bluetooth) ══")
        TvNetDiag.run(host, proxy(), out)
        repeat(3) { i -> out("connexion TCP ${i + 1} vers $host:443 : " + (entry.tcpPing(host)?.let { "$it ms" } ?: "échec")) }
        entry.diag("ping", host) { out(it) }
        entry.diag("trace", host, timeoutS = 120) { out(it) }
        out("— Terminé —")
    }

    /** Ping and traceroute run by the phone (the gateway's way out). */
    fun diagnosePhoneSide(host: String, out: (String) -> Unit) {
        if (!entry.connected) { out("aucun téléphone connecté"); return }
        entry.diag("ping", host) { out(it) }
        entry.diag("trace", host, timeoutS = 120) { out(it) }
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
