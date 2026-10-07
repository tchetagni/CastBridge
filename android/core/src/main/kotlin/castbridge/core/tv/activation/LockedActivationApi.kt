package castbridge.core.tv.activation

import castbridge.core.net.JsonLite
import castbridge.core.owner.DeviceRequestText
import castbridge.core.owner.Feature
import castbridge.core.owner.FeatureGate
import castbridge.core.owner.GateState
import castbridge.core.ssh.Lan
import castbridge.core.tunnel.TunnelTerms
import castbridge.core.tv.ApiReply
import castbridge.core.tv.HostGuard
import castbridge.core.tv.PinGuard
import fi.iki.elonen.NanoHTTPD
import java.io.IOException

/**
 * « Activer par le Wi-Fi » a LOCKED TV (docs/TV-ACTIVATION-CLE-USB.md; wios-tv-05, decided by the owner on 2026-10-06 with the connection code KEPT).
 * A locked TV starts no API server (docs/TRIAL-EDITION.md): this is its ONLY HTTP surface, [Feature.ACTIVATION_WIFI][castbridge.core.owner.Feature.ACTIVATION_WIFI].
 *
 *  - `GET /api/hello`: who answers (`locked:true`, `pinRequired:true`), nothing else, never the code;
 *  - `POST /api/activation/install`: body = the key (full token, grouped text or compact key), behind the TV's connection code (`X-CB-Pin`, the same [PinGuard] lockout as the
 *    full API; a trusted-phone token never opens it, [castbridge.core.trust.TvAuth.tokenMayCall]), from the local network only (and a local `Host`, anti DNS-rebinding),
 *    [MAX_BODY] bytes at most, [MAX_TRIES] verifications per address per [WINDOW_MS]; the terms of use must have been accepted on the TV (checked BEFORE the code, audit M1 b);
 *    at most [GLOBAL_MAX_WRONG] wrong codes per [WINDOW_MS] from every address together, then 429 for everyone (audit M1 a); the key goes to [install], i.e. the
 *    SAME verifier as a pasted key (signature by a trusted key, binding to this TV's device code, window). The key is never logged nor echoed.
 *  - `GET /api/activation/device-request` (docs/coordination/DESIGN-ACTIVATION-SIMPLE-2026-10-07.md, F2): the phone that holds the connection code reads the TV's device
 *    request instead of having it recopied by hand. EXACTLY the same guards as the installation (local address and `Host`, terms before the code, global cap, [PinGuard]
 *    shared with it, a trusted-phone token never opens it); the answer is the COMPLETE request ([DeviceRequestText.complete], `text/plain`: `code=`, `k=`, `factor=`, `install=` and `install_sig=`
 *    lines, nothing else): `install=` is the installation's PUBLIC X25519 key, nothing secret, and a trial key in a v2 envelope needs it (ACT-F4 amended on 2026-10-07); the line is ABSENT when the TV has
 *    no key yet. The text is rebuilt from the parsed request, never copied raw. At most [MAX_READS] per address and per [WINDOW_MS], counted apart from the key verifications;
 *  - anything else: 403 `locked`.
 * Pure but for [LockedActivationServer], the thin NanoHTTPD shell.
 */
class LockedActivationApi(
    private val guard: PinGuard,
    private val install: (String) -> Install,
    private val termsAccepted: () -> Boolean,
    private val version: String,
    private val now: () -> Long = System::currentTimeMillis,
    /** The TV's own device request, full text (`ActivationCenter.requestText()`); only its rebuilt [DeviceRequestText.complete] form ever leaves. null = unavailable (503). Called only for a peer that passed every guard. */
    private val deviceRequest: () -> String? = { null },
    /** Told the ADDRESS (never the code) of every peer that presented the right code, on either route: the activation screen shows « téléphone relié ». A failing listener changes nothing. */
    private val onAuthorized: (String) -> Unit = {},
) {
    sealed class Install {
        class Accepted(val label: String) : Install()
        class Rejected(val message: String) : Install()
    }

    /** What the guard needs from an HTTP request (headers already lower-cased by the server). */
    data class Request(val method: String, val path: String, val remoteIp: String?, val host: String?, val pin: String?, val token: String?, val contentLength: Long?)

    /** Per-address counters (access-ordered, at most [MAX_ADDRESSES] addresses: the least recently seen is forgotten first, audit M1 c). */
    private fun addressTable() = object : LinkedHashMap<String, ArrayDeque<Long>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ArrayDeque<Long>>?) = size > MAX_ADDRESSES
    }
    /** Key verifications per address. */
    private val tries = addressTable()
    /** Reads of the device request per address: counted apart, so reading it never uses up the key verifications of the same address. */
    private val reads = addressTable()
    /** Times of the wrong codes, every address together (audit M1 a): at most [GLOBAL_MAX_WRONG] per [WINDOW_MS], then 429 for everyone. */
    private val wrong = ArrayDeque<Long>()

    fun trackedAddresses(): Int = synchronized(tries) { tries.size }
    fun trackedReaders(): Int = synchronized(reads) { reads.size }

    /** [readBody] reads exactly n bytes of the body (null if the peer sent fewer); it is called only once the request passed every check. */
    fun handle(r: Request, readBody: (Int) -> ByteArray?): ApiReply {
        val ip = r.remoteIp?.takeIf { Lan.isLocal(it) } ?: return reply(403, """{"error":"réseau local seulement","locked":true}""")
        if (!HostGuard.allowed(r.host)) return reply(403, """{"error":"Hôte non autorisé","locked":true}""")
        if (r.path == "/api/hello" && r.method == "GET") return reply(200, """{"app":"castbridge-tv","v":${JsonLite.quote(version)},"pinRequired":true,"locked":true}""")
        val readsRequest = r.path == DEVICE_REQUEST_PATH
        if (r.path != PATH && !readsRequest) return reply(403, """{"error":${JsonLite.quote(LOCKED)},"locked":true}""")
        if (readsRequest) { if (r.method != "GET") return reply(405, """{"error":"use GET"}""") }
        else if (r.method != "POST") return reply(405, """{"error":"use POST"}""")
        if (r.pin == null && r.token != null) return reply(403, """{"error":"pin required","message":"Cette action demande le code de la TV."}""")
        // the terms BEFORE the code (audit M1 b): a TV whose terms are not accepted never says whether a code is right
        if (!termsAccepted()) return reply(409, """{"error":${JsonLite.quote(TunnelTerms.MUST_ACCEPT_ON_TV)}}""")
        // the global cap BEFORE the code (audit M1 a): once reached, no code is compared any more, from any address
        if (globalClosed()) return reply(429, """{"error":${JsonLite.quote(GLOBAL_CLOSED)}}""")
        when (guard.check(ip, r.pin)) {
            PinGuard.Result.OK -> {}
            PinGuard.Result.BAD -> { countWrong(); return reply(401, """{"error":"bad pin"}""") }
            PinGuard.Result.LOCKED -> { countWrong(); return reply(401, """{"error":"locked","retryAfter":${guard.retryAfterSeconds(ip)}}""") }
        }
        runCatching { onAuthorized(ip) }
        if (readsRequest) return deviceRequestReply(ip)
        val len = r.contentLength ?: return reply(400, """{"error":"body missing"}""")
        if (len <= 0) return reply(400, """{"error":"body missing"}""")
        if (len > MAX_BODY) return reply(413, """{"error":"body too large","max":$MAX_BODY}""")
        if (!take(tries, ip, MAX_TRIES)) return reply(429, """{"error":"trop d'essais : réessayez dans 10 minutes"}""")
        val body = readBody(len.toInt()) ?: return reply(400, """{"error":"body incomplete"}""")
        return when (val res = install(String(body, Charsets.UTF_8).removePrefix("﻿").trim())) {
            is Install.Accepted -> reply(200, """{"installed":true,"label":${JsonLite.quote(res.label)},"notes":[]}""")
            is Install.Rejected -> reply(422, """{"error":${JsonLite.quote(res.message)}}""")
        }
    }

    /** The complete device request as plain text; nothing is built for an address that already read it [MAX_READS] times in the window. */
    private fun deviceRequestReply(ip: String): ApiReply {
        if (!take(reads, ip, MAX_READS)) return reply(429, """{"error":"trop de lectures : réessayez dans 10 minutes"}""")
        val text = runCatching { deviceRequest()?.let(DeviceRequestText::complete) }.getOrNull() ?: return reply(503, """{"error":"demande d'appareil indisponible"}""")
        return ApiReply(200, text, mime = TEXT_PLAIN)
    }

    /** One use of [table] for [ip] if fewer than [max] were made in the last [WINDOW_MS]. */
    private fun take(table: MutableMap<String, ArrayDeque<Long>>, ip: String, max: Int): Boolean = synchronized(table) {
        val q = table.getOrPut(ip) { ArrayDeque() }
        val t = now()
        prune(q, t)
        if (q.size >= max) return false
        q.addLast(t); true
    }

    private fun prune(q: ArrayDeque<Long>, t: Long) { while (q.isNotEmpty() && t - q.first() > WINDOW_MS) q.removeFirst() }
    private fun globalClosed(): Boolean = synchronized(wrong) { prune(wrong, now()); wrong.size >= GLOBAL_MAX_WRONG }
    private fun countWrong() { synchronized(wrong) { val t = now(); prune(wrong, t); wrong.addLast(t) } }

    private fun reply(status: Int, json: String) = ApiReply(status, json)

    companion object {
        const val PATH = "/api/activation/install"
        /** F2: the phone reads the TV's device request here (GET, same guards as [PATH]). */
        const val DEVICE_REQUEST_PATH = "/api/activation/device-request"
        const val TEXT_PLAIN = "text/plain; charset=utf-8"
        const val MAX_BODY = 16_384
        const val MAX_TRIES = 10
        /** Reads of the device request per address and per [WINDOW_MS] (the phone reads it once or twice); apart from [MAX_TRIES]. */
        const val MAX_READS = 20
        const val WINDOW_MS = 10 * 60_000L
        /** Wrong codes accepted per [WINDOW_MS] from every address together (audit M1 a), then the route answers 429 to everyone until the window slides. */
        const val GLOBAL_MAX_WRONG = 20
        /** Addresses tracked by the per-address limiter (audit M1 c). */
        const val MAX_ADDRESSES = 1_000
        const val GLOBAL_CLOSED = "Trop de codes faux reçus par cette TV : l'activation par le Wi-Fi est fermée pour 10 minutes. Utilisez le Bluetooth (CastBridge > « Activer la TV »), le fichier d'activation ou collez la clé sur la TV."

        /** L3: the locked route opens only on a LOCKED TV and only while the gate lets [Feature.ACTIVATION_WIFI] through (an activated TV runs its full server instead). */
        fun mayOpen(state: GateState): Boolean = state is GateState.Locked && FeatureGate.canUse(Feature.ACTIVATION_WIFI, state)
        const val LOCKED = "Usage soumis à autorisation : seule l'activation est ouverte sur cette TV"
    }
}

/**
 * The line of the activation screen for the Wi-Fi way (read from the sofa): the code the phone asks for, and whether the TV is on a network at all. [ips] are the TV's addresses on a
 * LOCAL NETWORK (never its own group's 192.168.49.x: an older phone compares the address shown with the one it found); [groupReady] = the TV's own Wi-Fi Direct group is up, so a TV with
 * no network of its own is not called « sans réseau ».
 */
object LockedWifiTexts {
    fun line(pin: String, ips: List<String>, groupReady: Boolean = false): String =
        "Par le Wi-Fi : code de connexion $pin" + when {
            ips.isNotEmpty() -> " · TV ${ips.first()}"
            groupReady -> " · réseau direct de la TV ${castbridge.core.tv.WifiDirect.GROUP_OWNER_IP}"
            else -> " (TV sans réseau Wi-Fi pour le moment : le Bluetooth reste possible)"
        }
}

/** The NanoHTTPD shell of [LockedActivationApi] on the TV's usual port (only while the TV is locked; the full server takes the port once it is activated). */
class LockedActivationServer(private val api: LockedActivationApi, port: Int = castbridge.core.tv.ReceiverServer.PORT) : NanoHTTPD(port) {
    private val gate = ConnectionGate()
    init { setAsyncRunner(LocalOnlyRunner(gate, 4)) }

    /** Carries the peer's address to [LocalOnlyRunner], which decides BEFORE the first header byte is read (audit L1). */
    inner class Tagged(val ip: String?, input: java.io.InputStream, sock: java.net.Socket) : ClientHandler(input, sock)

    override fun createClientHandler(finalAccept: java.net.Socket, inputStream: java.io.InputStream): ClientHandler {
        val ip = finalAccept.inetAddress?.hostAddress
        if (ip == null || !Lan.isLocal(ip)) runCatching { finalAccept.close() }           // never read a byte from a non-local peer
        return Tagged(ip, inputStream, finalAccept)
    }

    override fun serve(session: IHTTPSession): Response {
        val h = session.headers
        val req = LockedActivationApi.Request(session.method.name, session.uri, session.remoteIpAddress, h["host"], h["x-cb-pin"], h["x-cb-token"], h["content-length"]?.toLongOrNull())
        val r = try { api.handle(req) { n -> readExactly(session, n) } } catch (e: Exception) { ApiReply(500, """{"error":"erreur interne"}""") }
        val st = Response.Status.lookup(r.status) ?: object : Response.IStatus { override fun getDescription() = "${r.status}"; override fun getRequestStatus() = r.status }
        // JSON everywhere except the device request, which is plain text (ApiReply.mime)
        val mime = if (r.mime == "application/json") "application/json; charset=utf-8" else r.mime
        return newFixedLengthResponse(st, mime, r.json).also {
            it.addHeader("Cache-Control", "no-store")
            it.addHeader("Connection", "close")            // a refused request may leave its body unread: never reuse the connection
        }
    }

    private fun readExactly(s: IHTTPSession, n: Int): ByteArray? = try {
        val buf = ByteArray(n); var off = 0
        while (off < n) { val k = s.inputStream.read(buf, off, n - off); if (k < 0) break; off += k }
        if (off == n) buf else null
    } catch (e: IOException) { null }
}
