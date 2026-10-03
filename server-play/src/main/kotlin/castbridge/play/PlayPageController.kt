package castbridge.play

import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * La page de jeu : `GET /play` et `GET /play/j/{code}` (même page ; le code vient de l'adresse, lu par le script, jamais injecté par le serveur), plus ses deux ressources
 * `play.js` et `play.css`. Servie depuis le jar (`static/play/`), aucune dépendance externe, en-têtes de sécurité stricts.
 */
class PlayPageController {
    private val cache = ConcurrentHashMap<String, ByteArray>()
    private val SAFE_CODE = Regex("^[A-Za-z0-9-]{1,16}$")

    private fun resource(name: String): ByteArray? =
        cache[name] ?: PlayPageController::class.java.getResourceAsStream("/static/play/$name")?.use { it.readBytes() }?.also { cache[name] = it }

    /** Vrai si la requête était pour la page ou l'une de ses ressources (réponse écrite). */
    fun serve(path: String, out: OutputStream, head: Boolean): Boolean {
        val p = path.trimEnd('/').ifEmpty { "/" }
        val (name, type) = when {
            p == "/play" || (p.startsWith("/play/j/") && SAFE_CODE.matches(p.removePrefix("/play/j/"))) -> "play.html" to "text/html; charset=utf-8"
            p == "/play/play.js" -> "play.js" to "text/javascript; charset=utf-8"
            p == "/play/play.css" -> "play.css" to "text/css; charset=utf-8"
            else -> return false
        }
        val body = resource(name) ?: run { MiniHttp.json(out, 404, """{"error":"page absente"}"""); return true }
        val headers = HashMap(SECURITY_HEADERS)
        headers["Cache-Control"] = if (name == "play.html") "no-cache" else "public, max-age=300"
        MiniHttp.respond(out, 200, type, body, headers, includeBody = !head)
        return true
    }

    companion object {
        val SECURITY_HEADERS = mapOf(
            "Content-Security-Policy" to "default-src 'none'; script-src 'self'; style-src 'self'; connect-src 'self' ws: wss:; img-src 'self' data:; base-uri 'none'; form-action 'none'; frame-ancestors 'none'",
            "X-Content-Type-Options" to "nosniff", "Referrer-Policy" to "no-referrer", "X-Frame-Options" to "DENY")
    }
}
