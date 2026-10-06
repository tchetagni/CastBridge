package castbridge.core.tv.activation

import castbridge.core.net.JsonLite
import castbridge.core.ssh.Lan
import castbridge.core.tunnel.TunnelTerms
import castbridge.core.tv.ApiReply
import castbridge.core.tv.HostGuard
import castbridge.core.tv.PinGuard
import fi.iki.elonen.NanoHTTPD
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * « Activer par le Wi-Fi » a LOCKED TV (docs/TV-ACTIVATION-CLE-USB.md; wios-tv-05, decided by the owner on 2026-10-06 with the connection code KEPT).
 * A locked TV starts no API server (docs/TRIAL-EDITION.md): this is its ONLY HTTP surface, [Feature.ACTIVATION_WIFI][castbridge.core.owner.Feature.ACTIVATION_WIFI].
 *
 *  - `GET /api/hello`: who answers (`locked:true`, `pinRequired:true`), nothing else, never the code;
 *  - `POST /api/activation/install`: body = the key (full token, grouped text or compact key), behind the TV's connection code (`X-CB-Pin`, the same [PinGuard] lockout as the
 *    full API; a trusted-phone token never opens it, [castbridge.core.trust.TvAuth.tokenMayCall]), from the local network only (and a local `Host`, anti DNS-rebinding),
 *    [MAX_BODY] bytes at most, [MAX_TRIES] verifications per address per [WINDOW_MS]; the terms of use must have been accepted on the TV; the key goes to [install], i.e. the
 *    SAME verifier as a pasted key (signature by a trusted key, binding to this TV's device code, window). The key is never logged nor echoed.
 *  - anything else: 403 `locked`.
 * Pure but for [LockedActivationServer], the thin NanoHTTPD shell.
 */
class LockedActivationApi(
    private val guard: PinGuard,
    private val install: (String) -> Install,
    private val termsAccepted: () -> Boolean,
    private val version: String,
    private val now: () -> Long = System::currentTimeMillis,
) {
    sealed class Install {
        class Accepted(val label: String) : Install()
        class Rejected(val message: String) : Install()
    }

    /** What the guard needs from an HTTP request (headers already lower-cased by the server). */
    data class Request(val method: String, val path: String, val remoteIp: String?, val host: String?, val pin: String?, val token: String?, val contentLength: Long?)

    private val tries = ConcurrentHashMap<String, ArrayDeque<Long>>()

    /** [readBody] reads exactly n bytes of the body (null if the peer sent fewer); it is called only once the request passed every check. */
    fun handle(r: Request, readBody: (Int) -> ByteArray?): ApiReply {
        val ip = r.remoteIp?.takeIf { Lan.isLocal(it) } ?: return reply(403, """{"error":"réseau local seulement","locked":true}""")
        if (!HostGuard.allowed(r.host)) return reply(403, """{"error":"Hôte non autorisé","locked":true}""")
        if (r.path == "/api/hello" && r.method == "GET") return reply(200, """{"app":"castbridge-tv","v":${JsonLite.quote(version)},"pinRequired":true,"locked":true}""")
        if (r.path != PATH) return reply(403, """{"error":${JsonLite.quote(LOCKED)},"locked":true}""")
        if (r.method != "POST") return reply(405, """{"error":"use POST"}""")
        if (r.pin == null && r.token != null) return reply(403, """{"error":"pin required","message":"Cette action demande le code de la TV."}""")
        when (guard.check(ip, r.pin)) {
            PinGuard.Result.OK -> {}
            PinGuard.Result.BAD -> return reply(401, """{"error":"bad pin"}""")
            PinGuard.Result.LOCKED -> return reply(401, """{"error":"locked","retryAfter":${guard.retryAfterSeconds(ip)}}""")
        }
        val len = r.contentLength ?: return reply(400, """{"error":"body missing"}""")
        if (len <= 0) return reply(400, """{"error":"body missing"}""")
        if (len > MAX_BODY) return reply(413, """{"error":"body too large","max":$MAX_BODY}""")
        if (!take(ip)) return reply(429, """{"error":"trop d'essais : réessayez dans 10 minutes"}""")
        val body = readBody(len.toInt()) ?: return reply(400, """{"error":"body incomplete"}""")
        if (!termsAccepted()) return reply(409, """{"error":${JsonLite.quote(TunnelTerms.MUST_ACCEPT_ON_TV)}}""")
        return when (val res = install(String(body, Charsets.UTF_8).removePrefix("﻿").trim())) {
            is Install.Accepted -> reply(200, """{"installed":true,"label":${JsonLite.quote(res.label)},"notes":[]}""")
            is Install.Rejected -> reply(422, """{"error":${JsonLite.quote(res.message)}}""")
        }
    }

    /** One verification for [ip] if fewer than [MAX_TRIES] were made in the last [WINDOW_MS]. */
    private fun take(ip: String): Boolean {
        val q = tries.getOrPut(ip) { ArrayDeque() }
        synchronized(q) {
            val t = now()
            while (q.isNotEmpty() && t - q.first() > WINDOW_MS) q.removeFirst()
            if (q.size >= MAX_TRIES) return false
            q.addLast(t); return true
        }
    }

    private fun reply(status: Int, json: String) = ApiReply(status, json)

    companion object {
        const val PATH = "/api/activation/install"
        const val MAX_BODY = 16_384
        const val MAX_TRIES = 10
        const val WINDOW_MS = 10 * 60_000L
        const val LOCKED = "Usage soumis à autorisation : seule l'activation est ouverte sur cette TV"
    }
}

/** The line of the activation screen for the Wi-Fi way (large type, read from the sofa): the code the phone asks for, and whether the TV is on a network at all. */
object LockedWifiTexts {
    fun line(pin: String, ips: List<String>): String =
        "Par le Wi-Fi : code de connexion $pin" + if (ips.isEmpty()) " (TV sans réseau Wi-Fi pour le moment : le Bluetooth reste possible)" else " · TV ${ips.first()}"
}

/** The NanoHTTPD shell of [LockedActivationApi] on the TV's usual port (only while the TV is locked; the full server takes the port once it is activated). */
class LockedActivationServer(private val api: LockedActivationApi, port: Int = castbridge.core.tv.ReceiverServer.PORT) : NanoHTTPD(port) {
    init { setAsyncRunner(castbridge.core.tv.BoundedRunner(4)) }

    override fun serve(session: IHTTPSession): Response {
        val h = session.headers
        val req = LockedActivationApi.Request(session.method.name, session.uri, session.remoteIpAddress, h["host"], h["x-cb-pin"], h["x-cb-token"], h["content-length"]?.toLongOrNull())
        val r = try { api.handle(req) { n -> readExactly(session, n) } } catch (e: Exception) { ApiReply(500, """{"error":"erreur interne"}""") }
        val st = Response.Status.lookup(r.status) ?: object : Response.IStatus { override fun getDescription() = "${r.status}"; override fun getRequestStatus() = r.status }
        return newFixedLengthResponse(st, "application/json; charset=utf-8", r.json).also {
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
