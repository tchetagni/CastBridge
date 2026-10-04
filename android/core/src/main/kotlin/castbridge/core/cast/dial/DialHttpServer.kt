package castbridge.core.cast.dial

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Petit serveur HTTP/1.1 pour DIAL (sockets JDK : fonctionne aussi sur Android, contrairement à com.sun.net.httpserver).
 * Une requête par connexion, en-têtes ≤ 8 Ko, corps ≤ 4 Ko (413 avant lecture), délai de lecture 5 s, 4 fils au plus.
 */
class DialHttpServer(private val router: DialRouter, private val port: Int = PORT, private val bindAddress: InetAddress? = null, private val log: (String) -> Unit = {}) {
    companion object {
        /** Ni 8008/8009 (Chromecast), ni 8765 (CastBridge-TV). */
        const val PORT = 30765
        const val MAX_HEADERS = 8192
        private val REASONS = mapOf(200 to "OK", 201 to "Created", 400 to "Bad Request", 403 to "Forbidden", 404 to "Not Found", 405 to "Method Not Allowed",
            411 to "Length Required", 413 to "Payload Too Large", 415 to "Unsupported Media Type", 429 to "Too Many Requests", 431 to "Request Header Fields Too Large",
            501 to "Not Implemented", 503 to "Service Unavailable")
    }

    private var server: ServerSocket? = null
    private var pool: ThreadPoolExecutor? = null
    val localPort: Int get() = server?.localPort ?: -1

    /** Écoute sur [port] ; si pris, sur un port libre. Renvoie le port, ou -1. */
    @Synchronized fun start(): Int {
        server?.let { return it.localPort }
        val s = try { ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(bindAddress, port)) } }
        catch (e: Exception) {
            try { ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(bindAddress, 0)) } } catch (e2: Exception) { log("DIAL : serveur HTTP impossible"); return -1 }
        }
        server = s
        val p = ThreadPoolExecutor(1, 4, 10, TimeUnit.SECONDS, ArrayBlockingQueue(16)) { r -> Thread(r, "cb-dial-http").apply { isDaemon = true } }
        pool = p
        Thread({ accept(s, p) }, "cb-dial-accept").apply { isDaemon = true }.start()
        return s.localPort
    }

    @Synchronized fun stop() {
        runCatching { server?.close() }; server = null
        pool?.shutdownNow(); pool = null
    }

    private fun accept(s: ServerSocket, p: ThreadPoolExecutor) {
        while (!s.isClosed) {
            val c = try { s.accept() } catch (e: Exception) { if (s.isClosed) return else continue }
            try { p.execute { serve(c) } } catch (e: RejectedExecutionException) { runCatching { c.close() } }
        }
    }

    private fun serve(c: Socket) {
        c.use {
            try {
                c.soTimeout = 5_000
                val input = c.getInputStream()
                val head = readHead(input)
                val resp: DialResponse? = when {
                    head == null -> DialResponse(400, body = "Requête invalide", contentType = "text/plain; charset=utf-8")
                    head == TOO_BIG -> DialResponse(431, body = "En-têtes trop grands", contentType = "text/plain; charset=utf-8")
                    else -> dispatch(c, head, input)
                }
                if (resp != null) { write(c, resp); drain(c, input) }
            } catch (_: Exception) { /* délai ou coupure : on ferme */ }
        }
    }

    private val TOO_BIG = "!"

    /** Après une réponse d'erreur donnée avant la fin du corps : on lit (peu, brièvement) ce qui reste, sinon la fermeture réinitialise la connexion et le client perd la réponse. */
    private fun drain(c: Socket, input: InputStream) {
        runCatching {
            c.shutdownOutput(); c.soTimeout = 300
            val buf = ByteArray(4096); var total = 0
            while (total < 65_536) { val n = input.read(buf); if (n < 0) break; total += n }
        }
    }

    private fun dispatch(c: Socket, head: String, input: InputStream): DialResponse? {
        val lines = head.split("\r\n")
        val parts = lines[0].split(' ')
        if (parts.size != 3 || !parts[2].startsWith("HTTP/1.")) return DialResponse(400, body = "Requête invalide", contentType = "text/plain; charset=utf-8")
        val headers = HashMap<String, MutableList<String>>()
        for (l in lines.drop(1)) {
            if (l.isEmpty()) continue
            val i = l.indexOf(':'); if (i <= 0) return DialResponse(400, body = "En-tête invalide", contentType = "text/plain; charset=utf-8")
            headers.getOrPut(l.substring(0, i).trim().lowercase()) { ArrayList() }.add(l.substring(i + 1).trim())
        }
        // La source est contrôlée avant toute lecture du corps : hors réseau local, on se tait.
        val remote = c.inetAddress
        if (!DialRules.isLanSource(remote)) return null
        if (headers.containsKey("transfer-encoding")) return DialResponse(501, body = "Transfer-Encoding non pris en charge", contentType = "text/plain; charset=utf-8")
        val cl = headers["content-length"]
        var len = 0
        if (cl != null) {
            len = (if (cl.size == 1) cl[0].toIntOrNull() else null) ?: return DialResponse(400, body = "Content-Length invalide", contentType = "text/plain; charset=utf-8")
            if (len < 0) return DialResponse(400, body = "Content-Length invalide", contentType = "text/plain; charset=utf-8")
            if (len > DialRules.MAX_BODY) return DialResponse(413, body = "Corps trop grand", contentType = "text/plain; charset=utf-8")
        }
        val body = ByteArray(len)
        var off = 0
        while (off < len) { val n = input.read(body, off, len - off); if (n < 0) return DialResponse(400, body = "Corps incomplet", contentType = "text/plain; charset=utf-8"); off += n }
        return router.handle(DialRequest(parts[0], parts[1], headers, body, remote))
    }

    /** Lit jusqu'à la ligne vide ; null si flux vide ; [TOO_BIG] si > 8 Ko. */
    private fun readHead(input: InputStream): String? {
        val out = ByteArrayOutputStream()
        var tail = 0
        while (true) {
            val b = input.read()
            if (b < 0) return null
            out.write(b)
            if (out.size() > MAX_HEADERS) return TOO_BIG
            tail = (tail shl 8) or b   // les 4 derniers octets, en glissant
            if (tail == 0x0D0A0D0A) return String(out.toByteArray(), 0, out.size() - 4, Charsets.ISO_8859_1)
        }
    }

    private fun write(c: Socket, r: DialResponse) {
        val body = r.body.toByteArray(Charsets.UTF_8)
        val sb = StringBuilder("HTTP/1.1 ${r.status} ${REASONS[r.status] ?: "Status"}\r\n")
        r.headers.forEach { (k, v) -> sb.append(k).append(": ").append(v.replace("\r", "").replace("\n", "")).append("\r\n") }
        if (r.contentType != null) sb.append("Content-Type: ").append(r.contentType).append("\r\n")
        sb.append("Content-Length: ").append(body.size).append("\r\nConnection: close\r\n\r\n")
        val o = c.getOutputStream()
        o.write(sb.toString().toByteArray(Charsets.ISO_8859_1)); o.write(body); o.flush()
    }
}
