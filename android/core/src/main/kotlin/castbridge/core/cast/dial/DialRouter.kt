package castbridge.core.cast.dial

import java.net.InetAddress

/** Ce que le câblage Android fournit : l'application YouTube TV installée sur la TV. */
interface DialLauncher {
    fun installed(): Boolean
    /** Vrai tant que le lancement n'a pas été arrêté (l'état réel d'une autre appli n'est pas lisible sans droit spécial). */
    fun running(): Boolean
    fun launch(url: String): Boolean
    /** Retour à l'accueil de CastBridge-TV ; ne tue pas YouTube TV (limite documentée). */
    fun stop(): Boolean
}

class DialRequest(val method: String, val target: String, val headers: Map<String, List<String>>, val body: ByteArray, val remote: InetAddress) {
    fun header(n: String): List<String> = headers[n.lowercase()] ?: emptyList()
}

class DialResponse(val status: Int, val headers: List<Pair<String, String>> = emptyList(), val body: String = "", val contentType: String? = null)

/** Routage HTTP de DIAL : pur, sans socket. `null` = ignorer la requête (source hors réseau local). */
class DialRouter(
    private val deviceId: String,
    private val manufacturer: String,
    private val model: String,
    private val allowedHosts: () -> Set<String>,
    private val launcher: DialLauncher,
    private val limiter: LaunchRateLimiter = LaunchRateLimiter(),
    private val log: (String) -> Unit = {},
) {
    private val xml = "application/xml; charset=utf-8"

    fun handle(r: DialRequest): DialResponse? {
        if (!DialRules.isLanSource(r.remote)) return null
        if (!DialRules.hostAllowed(r.header("host"), allowedHosts())) return err(403, "Host refusé")
        if (!DialRules.originAllowed(r.header("origin"))) { log("DIAL : requête refusée (origine non autorisée)"); return err(403, "Origine refusée") }
        val host = r.header("host")[0]
        val path = r.target.substringBefore('?')
        return when {
            path == "/dd.xml" -> if (r.method == "GET") DialResponse(200, listOf("Application-URL" to "http://$host/apps/"), DialRules.deviceDescription(deviceId, manufacturer, model), xml) else notAllowed("GET")
            path.startsWith("/apps/") -> app(r, host, path.removePrefix("/apps/"))
            else -> err(404, "Introuvable")
        }
    }

    private fun app(r: DialRequest, host: String, rest: String): DialResponse {
        val name = rest.substringBefore('/')
        val sub = rest.substringAfter('/', "")
        if (name !in DialRules.APPS) return err(404, "Application inconnue")
        if (!launcher.installed()) { log("Application YouTube TV introuvable sur cette TV"); return err(404, "Application introuvable") }
        val state = if (launcher.running()) DialRules.AppState.RUNNING else DialRules.AppState.STOPPED
        return when {
            sub.isEmpty() -> when (r.method) {
                "GET" -> DialResponse(200, emptyList(), DialRules.appStatus(name, state, true), xml)
                "POST" -> launch(r, host, name)
                else -> notAllowed("GET, POST")
            }
            sub == "run" -> when (r.method) {
                "GET" -> if (state == DialRules.AppState.RUNNING) DialResponse(200, emptyList(), DialRules.appStatus(name, state, true), xml) else err(404, "Pas d'instance")
                "DELETE" -> if (state == DialRules.AppState.RUNNING) { launcher.stop(); DialResponse(200) } else err(404, "Pas d'instance")
                else -> notAllowed("GET, DELETE")
            }
            else -> err(404, "Introuvable")
        }
    }

    private fun launch(r: DialRequest, host: String, name: String): DialResponse {
        if (r.body.size > DialRules.MAX_BODY) return err(413, "Corps trop grand")
        val ct = r.header("content-type").firstOrNull()?.substringBefore(';')?.trim()?.lowercase()
        if (r.body.isNotEmpty() && ct != "text/plain" && ct != "application/x-www-form-urlencoded") return err(415, "Type non pris en charge")
        val l = DialRules.buildLaunch(r.body)
        if (l is DialRules.Launch.Refused) return err(l.status, l.why)
        l as DialRules.Launch.Ok
        if (!limiter.tryAcquire()) return DialResponse(429, listOf("Retry-After" to limiter.retryAfterSeconds().toString()), "Trop de lancements", "text/plain; charset=utf-8")
        log("DIAL : lancement de YouTube TV (${DialRules.redact(l.query)})")
        if (!launcher.launch(l.url)) return err(503, "Lancement impossible")
        return DialResponse(201, listOf("Location" to "http://$host/apps/$name/run"))
    }

    private fun err(code: Int, msg: String) = DialResponse(code, emptyList(), msg, "text/plain; charset=utf-8")
    private fun notAllowed(allow: String) = DialResponse(405, listOf("Allow" to allow), "Méthode non permise", "text/plain; charset=utf-8")
}
