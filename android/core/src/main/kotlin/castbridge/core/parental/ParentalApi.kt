package castbridge.core.parental

import castbridge.core.net.JsonLite
import castbridge.core.tv.ApiExtension
import castbridge.core.tv.ApiReply

/**
 * Parental routes of the TV's HTTP server, used by the phone (docs/PARENTAL.md). They sit behind the TV's own PIN (checked by
 * the server for every /api route, header X-CB-Pin) and, for every sensitive action, ask the PARENTAL PIN too.
 *
 * - The parental PIN travels ONLY in the JSON body of a POST (`{"pin":"…"}`), never in the URL: a request whose query string
 *   carries anything named like a PIN is refused with 400 (URLs end up in logs). Nothing here is ever logged.
 * - GET /api/parental                status, without any secret: pin set?, enabled?, active profile, lockout, parent session.
 * - POST /api/parental/pin/create    {pin}                     first PIN only (4 to 6 digits, not trivial, never empty).
 * - POST /api/parental/pin/change    {pin, new}
 * - POST /api/parental/unlock        {pin}                     opens a parent session on the TV (rules off for a while).
 * - POST /api/parental/lock          {}                        closes it (no PIN needed: it only restricts).
 * - POST /api/parental/config/get    {pin}                     configuration + the students of « Apprendre » that can be imported.
 * - POST /api/parental/config/set    {pin, rev, config}        replaces the configuration (409 if [rev] is stale).
 * - POST /api/parental/report        {pin}                     usage per day / profile and blocked contents (local, nothing is sent to a server).
 * - POST /api/parental/history/clear {pin}
 * - POST /api/parental/disable       {}                        administrator only (TV PIN): the control can always be switched off.
 * - POST /api/parental/reset         {confirm:"RESET"}         administrator only (TV PIN): erases the parental PIN and switches off.
 *
 * Wrong PIN: 403 {error, attemptsLeft}; locked (5 wrong PINs: 1 min, then 5, 15, 60 min): 429 {error, retryAfter}.
 */
class ParentalApi(
    private val engine: ParentalEngine,
    /** Students of « Apprendre »: (id, name, level). */
    private val learnProfiles: () -> List<Triple<String, String, String?>> = { emptyList() },
    private val onChanged: () -> Unit = {},
) : ApiExtension {

    override fun wantsBody(path: String) = path.startsWith(ROOT + "/") && path != ROOT

    override fun handle(path: String, method: String, params: Map<String, String>): ApiReply? {
        if (path != ROOT && !path.startsWith("$ROOT/")) return null
        pinInUrl(params)?.let { return it }
        if (path == ROOT && method == "GET") return ApiReply(200, JsonLite.write(engine.statusMap()))
        if (method != "POST" || !wantsBody(path)) return ApiReply(405, err("Méthode non permise."))
        // the routes below normally arrive through handleBody; a POST without body is an empty body
        return handleBody(path, method, params, "{}".toByteArray())
    }

    override fun handleBody(path: String, method: String, params: Map<String, String>, body: ByteArray): ApiReply? {
        if (path != ROOT && !path.startsWith("$ROOT/")) return null
        pinInUrl(params)?.let { return it }
        if (method != "POST") return ApiReply(405, err("Utilisez POST."))
        if (body.size > MAX_BODY) return ApiReply(413, err("Requête trop grande."))
        val o = runCatching { JsonLite.obj(String(body, Charsets.UTF_8)) }.getOrElse { return ApiReply(400, err("Corps JSON invalide.")) }
        val pin = o["pin"] as? String
        return when (path.removePrefix(ROOT)) {
            "/disable" -> { engine.adminDisable(); onChanged(); ok() }
            "/reset" ->
                if (o["confirm"] == "RESET") { engine.resetNow(); onChanged(); ok() }
                else ApiReply(400, err("Confirmez avec {\"confirm\":\"RESET\"} : le code parental sera effacé et le contrôle désactivé."))
            "/lock" -> { engine.endSession(); onChanged(); ok() }
            "/pin/create" -> engine.createPin(pin)?.let { ApiReply(400, err(it)) } ?: run { onChanged(); ok() }
            "/pin/change" -> {
                ParentalPins.validateNew(o["new"] as? String)?.let { return ApiReply(400, err(it)) }
                val r = engine.changePin(pin.orEmpty(), o["new"] as? String)
                if (r == null) { onChanged(); ok() } else pinProblem(r)
            }
            "/unlock" -> auth(pin) { engine.startSession(); onChanged(); ok() }
            "/config/get" -> auth(pin) {
                ApiReply(200, JsonLite.write(linkedMapOf("config" to engine.config().toMap(),
                    "learnProfiles" to learnProfiles().map { linkedMapOf("id" to it.first, "name" to it.second, "level" to it.third, "age" to AgeBand.guessFromLevel(it.third).code) },
                    "status" to engine.statusMap())))
            }
            "/config/set" -> auth(pin) { setConfig(o) }
            "/report" -> auth(pin) { ApiReply(200, JsonLite.write(engine.report((o["days"] as? Number)?.toInt() ?: 7))) }
            "/history/clear" -> auth(pin) { engine.clearHistory(); ok() }
            else -> ApiReply(404, err("Route parentale inconnue."))
        }
    }

    private fun setConfig(o: Map<String, Any?>): ApiReply {
        @Suppress("UNCHECKED_CAST") val raw = o["config"] as? Map<String, Any?> ?: return ApiReply(400, err("config manquante."))
        val parsed = try { ParentalConfig.fromMap(raw) } catch (e: IllegalArgumentException) { return ApiReply(400, err(e.message ?: "Configuration invalide.")) }
        if (parsed.enabled && parsed.activeProfile == null && parsed.profiles.isNotEmpty())
            return ApiReply(400, err("Choisissez le profil actif avant d'activer le contrôle."))
        val saved = engine.edit(expectedRev = (o["rev"] as? Number)?.toInt()) { parsed } ?: return ApiReply(409, err("La configuration a changé sur la TV : rechargez-la."))
        onChanged()
        return ApiReply(200, JsonLite.write(linkedMapOf("config" to saved.toMap(), "status" to engine.statusMap())))
    }

    private inline fun auth(pin: String?, then: () -> ApiReply): ApiReply = when (val r = engine.verifyPin(pin)) {
        PinResult.Ok -> then()
        else -> pinProblem(r)
    }

    private fun pinProblem(r: Any): ApiReply = when (r) {
        is PinResult.Locked -> ApiReply(429, JsonLite.write(linkedMapOf("error" to "Trop d'essais : code parental bloqué.", "retryAfter" to r.retryAfterSec)))
        is PinResult.Wrong -> ApiReply(403, JsonLite.write(linkedMapOf("error" to "Code parental incorrect.", "attemptsLeft" to r.attemptsLeft)))
        PinResult.NoPin -> ApiReply(409, err("Aucun code parental : créez-le d'abord."))
        is String -> ApiReply(if (r.contains("Trop d'essais")) 429 else 403, err(r))
        else -> ApiReply(400, err("Refusé."))
    }

    /** Anything named like a PIN in the query string is refused, even with a correct value: it would be logged. */
    private fun pinInUrl(params: Map<String, String>): ApiReply? =
        if (params.keys.any { it.lowercase().contains("pin") || it.lowercase() in setOf("new", "old", "code") })
            ApiReply(400, err("Ne mettez jamais un code dans l'adresse : envoyez-le dans le corps de la requête."))
        else null

    private fun ok() = ApiReply(200, JsonLite.write(engine.statusMap()))
    private fun err(m: String) = JsonLite.write(linkedMapOf("error" to m))

    companion object {
        const val ROOT = "/api/parental"
        const val MAX_BODY = 64 * 1024
    }
}
