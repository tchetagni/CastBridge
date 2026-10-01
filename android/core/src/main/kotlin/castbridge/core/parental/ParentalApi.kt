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
 * - GET  /api/parental/supervision   —                         real state of the whole-TV supervision + what the TV needs to be set up (no secret).
 * - POST /api/parental/apps/list     {pin}                     installed apps of the TV (new ones flagged) + the per-app settings.
 * - POST /api/parental/apps/rules/set {pin, rev, settings}     replaces supervise / newApp / the rules of every profile (409 if [rev] is stale).
 * - POST /api/parental/reports/config/get {pin}                options of the reports, trusted phones and which ones receive them.
 * - POST /api/parental/reports/config/set {pin, rev, config}   hours and per-profile options (409 if [rev] is stale).
 * - POST /api/parental/reports/recipients/add    {pin, phoneId}  designates a TRUSTED phone as a recipient (parental PIN required).
 * - POST /api/parental/reports/recipients/remove {pin, phoneId}  (parental PIN required).
 * - POST /api/parental/reports/now   {pin}                     queues today's summary at once.
 * - POST /api/parental/disable       {}                        administrator only (TV PIN): the control can always be switched off.
 * - POST /api/parental/reset         {confirm:"RESET"}         administrator only (TV PIN): erases the parental PIN and switches off.
 *
 * Wrong PIN: 403 {error, attemptsLeft}; locked (5 wrong PINs: 1 min, then 5, 15, 60 min): 429 {error, retryAfter}.
 */
class ParentalApi(
    private val engine: ParentalEngine,
    /** Students of « Apprendre »: (id, name, level). */
    private val learnProfiles: () -> List<Triple<String, String, String?>> = { emptyList() },
    /** Launcher-visible apps of the TV (the leanback launcher included) and what the TV knows of the system (launcher, Settings). */
    private val installed: () -> List<InstalledApp> = { emptyList() },
    private val appEnv: () -> AppEnv = { AppEnv("castbridge.receiver") },
    /** What the TV needs for the whole-TV supervision (usage access granted?, settings screen present?, adb command). No secret. */
    private val supervisionSetup: () -> Map<String, Any?> = { emptyMap() },
    /** Reports for the parent's phone, and the trusted phones of the TV as (Bluetooth address, name): the address never leaves the TV. */
    private val reports: ParentalReports? = null,
    private val trustedPhones: () -> List<Pair<String, String>> = { emptyList() },
    private val onChanged: () -> Unit = {},
) : ApiExtension {

    override fun wantsBody(path: String) = path.startsWith(ROOT + "/") && path != ROOT

    override fun handle(path: String, method: String, params: Map<String, String>): ApiReply? {
        if (path != ROOT && !path.startsWith("$ROOT/")) return null
        pinInUrl(params)?.let { return it }
        if (path == ROOT && method == "GET") return ApiReply(200, JsonLite.write(engine.statusMap()))
        if (path == "$ROOT/supervision" && method == "GET") return ApiReply(200, JsonLite.write(supervisionMap()))
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
            "/apps/list" -> auth(pin) { appsList() }
            "/apps/rules/set" -> auth(pin) { setApps(o) }
            "/reports/config/get" -> auth(pin) { reportsConfig() }
            "/reports/config/set" -> auth(pin) { setReportConfig(o) }
            "/reports/recipients/add" -> auth(pin) { recipient(o, add = true) }
            "/reports/recipients/remove" -> auth(pin) { recipient(o, add = false) }
            "/reports/now" -> auth(pin) { ApiReply(200, JsonLite.write(linkedMapOf("queued" to (reports?.sendNow() ?: 0)))) }
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


    private fun supervisionMap(): Map<String, Any?> = linkedMapOf("supervision" to engine.supervision().toMap(), "setup" to supervisionSetup(), "rev" to engine.appSettings().rev)

    private fun appsList(): ApiReply {
        val apps = installed()
        engine.syncInstalled(apps)                               // first call = baseline; later calls flag the new apps
        val s = engine.appSettings(); val env = appEnv()
        val rows = apps.filter { AppSettings.validPkg(it.pkg) }.sortedBy { it.label.lowercase() }.map {
            linkedMapOf("pkg" to it.pkg, "label" to AppSettings.cleanLabel(it.label, it.pkg), "category" to it.category.code,
                "never" to AppRules.neverBlockable(it.pkg, env), "new" to s.isNew(it.pkg))
        }
        return ApiReply(200, JsonLite.write(linkedMapOf("apps" to rows, "settings" to s.toMap().filterKeys { it != "known" }, "supervision" to engine.supervision().toMap(),
            "states" to AppState.values().map { linkedMapOf("code" to it.code, "label" to it.label) })))
    }

    @Suppress("UNCHECKED_CAST")
    private fun setApps(o: Map<String, Any?>): ApiReply {
        val raw = o["settings"] as? Map<String, Any?> ?: return ApiReply(400, err("settings manquants."))
        val rules = LinkedHashMap<String, List<AppRule>>()
        try {
            for (e in (raw["rules"] as? List<Any?>).orEmpty()) {
                val m = e as? Map<String, Any?> ?: throw IllegalArgumentException("Réglages des applications invalides.")
                val id = (m["profile"] as? String)?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{1,16}")) } ?: throw IllegalArgumentException("Identifiant de profil invalide.")
                rules[id] = AppSettings.parseRules(m["apps"] as? List<Any?>)
            }
        } catch (e: IllegalArgumentException) { return ApiReply(400, err(e.message ?: "Réglages invalides.")) }
        val newApp = NewAppDefault.of(raw["newApp"] as? String) ?: return ApiReply(400, err("Choix « nouvelle application » inconnu."))
        val reviewed = (raw["reviewed"] as? List<Any?>).orEmpty().mapNotNull { (it as? String)?.takeIf(AppSettings::validPkg) }
        if (raw["supervise"] == true && engine.config().profiles.isEmpty()) return ApiReply(400, err("Créez d'abord un profil enfant avant d'activer la surveillance de toute la TV."))
        val saved = engine.editApps((o["rev"] as? Number)?.toInt()) { cur ->
            cur.copy(supervise = raw["supervise"] as? Boolean ?: cur.supervise, newApp = newApp, rules = rules,
                known = cur.known + rules.values.flatten().map { it.pkg } + reviewed)
        } ?: return ApiReply(409, err("Les réglages des applications ont changé sur la TV : rechargez-les."))
        onChanged()
        return ApiReply(200, JsonLite.write(linkedMapOf("settings" to saved.toMap().filterKeys { it != "known" }, "supervision" to engine.supervision().toMap())))
    }

    private fun reportsConfig(): ApiReply {
        val rp = reports ?: return ApiReply(404, err("Cette TV n'envoie pas de rapports."))
        val cfg = rp.config(); val profiles = engine.config().profiles
        val full = cfg.copy(profiles = profiles.associate { it.id to cfg.options(it.id) })
        val active = rp.recipients.active()
        val phones = trustedPhones().map { (addr, name) ->
            linkedMapOf("id" to rp.recipients.phoneId(addr), "name" to castbridge.core.trust.PhoneName.sanitize(name), "designated" to active.any { it.address == castbridge.core.trust.TrustRegistry.norm(addr) })
        }
        return ApiReply(200, JsonLite.write(linkedMapOf("config" to full.toMap(), "phones" to phones, "waiting" to rp.outbox.count())))
    }

    @Suppress("UNCHECKED_CAST")
    private fun setReportConfig(o: Map<String, Any?>): ApiReply {
        val rp = reports ?: return ApiReply(404, err("Cette TV n'envoie pas de rapports."))
        val parsed = try { ReportConfig.fromMap(o["config"] as? Map<String, Any?> ?: return ApiReply(400, err("config manquante."))) }
        catch (e: IllegalArgumentException) { return ApiReply(400, err(e.message ?: "Configuration invalide.")) }
        rp.saveConfig(parsed, (o["rev"] as? Number)?.toInt()) ?: return ApiReply(409, err("Les options de rapport ont changé sur la TV : rechargez-les."))
        return reportsConfig()
    }

    /** The PIN was checked by [auth]: this is the only way (with the TV screen, which asks it too) to designate or remove a recipient. */
    private fun recipient(o: Map<String, Any?>, add: Boolean): ApiReply {
        val rp = reports ?: return ApiReply(404, err("Cette TV n'envoie pas de rapports."))
        val id = o["phoneId"] as? String ?: return ApiReply(400, err("phoneId manquant."))
        val addr = rp.recipients.byPhoneId(id, trustedPhones().map { it.first } + rp.recipients.list().map { it.address }) ?: return ApiReply(404, err("Téléphone inconnu de la TV."))
        val r = if (add) rp.recipients.designate(addr, pinVerified = true) else rp.recipients.remove(addr, pinVerified = true).also { rp.outbox.dropRecipient(addr) }
        if (r != null) return ApiReply(400, err(r))
        return reportsConfig()
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
