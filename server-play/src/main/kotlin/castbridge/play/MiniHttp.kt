package castbridge.play

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.URLDecoder

/** Une requête HTTP/1.1 lue sur la socket (tête seulement ; le corps se lit avec [body]). */
class HttpReq(val method: String, val path: String, val query: Map<String, String>, private val headers: Map<String, String>, val peer: InetAddress,
              val input: InputStream) {
    fun header(name: String): String? = headers[name.lowercase()]

    /** Corps (Content-Length obligatoire) ; null si trop gros ou absent. */
    fun body(max: Int): ByteArray? {
        val n = header("content-length")?.trim()?.toIntOrNull() ?: return null
        if (n < 0 || n > max) return null
        return ByteArray(n).also { var o = 0; while (o < n) { val r = input.read(it, o, n - o); if (r < 0) return null; o += r } }
    }
}

/** Erreur de lecture de la tête : le service répond avec ce statut. */
class HttpError(val status: Int, message: String) : Exception(message)

/** HTTP/1.1 minimal : une requête par connexion (`Connection: close`), tête ≤ 8 Ko, ≤ 64 en-têtes. Pas de pipeline, pas de keep-alive. */
object MiniHttp {
    const val MAX_HEAD = 8 * 1024
    private val REASONS = mapOf(200 to "OK", 101 to "Switching Protocols", 400 to "Bad Request", 403 to "Forbidden", 404 to "Not Found", 405 to "Method Not Allowed",
        410 to "Gone", 411 to "Length Required", 413 to "Payload Too Large", 429 to "Too Many Requests", 431 to "Request Header Fields Too Large", 500 to "Internal Server Error",
        503 to "Service Unavailable")

    fun readRequest(input: InputStream, peer: InetAddress): HttpReq {
        val head = ByteArrayOutputStream()
        var last4 = 0
        while (true) {
            val b = input.read()
            if (b < 0) throw HttpError(400, "tête incomplète")
            head.write(b)
            last4 = (last4 shl 8) or b
            if (last4 == 0x0d0a0d0a) break
            if (head.size() > MAX_HEAD) throw HttpError(431, "tête trop grosse")
        }
        val lines = head.toString(Charsets.ISO_8859_1).split("\r\n").filter { it.isNotEmpty() }
        val rl = lines.firstOrNull()?.split(' ') ?: throw HttpError(400, "requête vide")
        if (rl.size != 3 || !rl[2].startsWith("HTTP/1.")) throw HttpError(400, "ligne de requête invalide")
        val headers = HashMap<String, String>()
        if (lines.size > 65) throw HttpError(431, "trop d'en-têtes")
        for (l in lines.drop(1)) {
            val i = l.indexOf(':'); if (i <= 0) throw HttpError(400, "en-tête invalide")
            val k = l.substring(0, i).trim().lowercase(); val v = l.substring(i + 1).trim()
            headers[k] = headers[k]?.let { "$it,$v" } ?: v   // plusieurs lignes : fusionnées (X-Forwarded-For)
        }
        val target = rl[1]
        if (!target.startsWith("/")) throw HttpError(400, "cible invalide")
        val path = target.substringBefore('?')
        val query = HashMap<String, String>()
        target.substringAfter('?', "").split('&').filter { it.isNotEmpty() }.forEach { kv ->
            val k = kv.substringBefore('='); val v = kv.substringAfter('=', "")
            runCatching { query[URLDecoder.decode(k, Charsets.UTF_8)] = URLDecoder.decode(v, Charsets.UTF_8) }
        }
        return HttpReq(rl[0], path, query, headers, peer, input)
    }

    fun respond(out: OutputStream, status: Int, contentType: String, body: ByteArray, headers: Map<String, String> = emptyMap(), includeBody: Boolean = true) {
        val sb = StringBuilder("HTTP/1.1 $status ${REASONS[status] ?: "Status"}\r\n")
        sb.append("Content-Type: $contentType\r\nContent-Length: ${body.size}\r\nConnection: close\r\n")
        headers.forEach { (k, v) -> sb.append(k).append(": ").append(v.replace("\r", "").replace("\n", "")).append("\r\n") }
        sb.append("\r\n")
        out.write(sb.toString().toByteArray(Charsets.ISO_8859_1))
        if (includeBody) out.write(body)
        out.flush()
    }

    fun json(out: OutputStream, status: Int, json: String, headers: Map<String, String> = emptyMap()) =
        respond(out, status, "application/json; charset=utf-8", json.toByteArray(Charsets.UTF_8), NO_STORE + headers)

    val NO_STORE = mapOf("Cache-Control" to "no-store", "X-Content-Type-Options" to "nosniff", "Referrer-Policy" to "no-referrer")
}
