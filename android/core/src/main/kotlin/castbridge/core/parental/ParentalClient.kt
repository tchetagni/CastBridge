package castbridge.core.parental

import castbridge.core.net.JsonLite
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** An answer of the TV that is not a success: [code] is the HTTP status, [retryAfter] the lock in seconds when the parental PIN is locked. */
class ParentalError(val code: Int, message: String, val retryAfter: Long? = null) : IOException(message)

/**
 * The phone's side of the parental routes ([ParentalApi]). The TV's own PIN goes in the X-CB-Pin header (like [castbridge.core.tv.TvClient]);
 * the parental PIN goes ONLY in the JSON body. Nothing here logs or stores a PIN, and no URL ever contains one.
 */
class ParentalClient(private val base: String, private val tvPin: String) {
    data class Loaded(val config: ParentalConfig, val learnProfiles: List<Triple<String, String, String?>>, val status: Map<String, Any?>)

    /** An app of the TV as listed for the parent. [never]: cannot be blocked (CastBridge TV, launcher, system). [isNew]: installed after the setup. */
    data class AppRow(val pkg: String, val label: String, val category: AppCategory, val never: Boolean, val isNew: Boolean)
    data class AppsLoaded(val apps: List<AppRow>, val settings: AppSettings, val supervision: Map<String, Any?>)
    data class PhoneRow(val id: String, val name: String, val designated: Boolean)
    data class ReportsLoaded(val config: ReportConfig, val phones: List<PhoneRow>, val waiting: Int)

    fun status(): Map<String, Any?> = JsonLite.obj(call("GET", "", null))

    /** Real state of the whole-TV supervision and the TV's setup hints (older TVs answer 404). */
    fun supervision(): Map<String, Any?> = JsonLite.obj(call("GET", "/supervision", null))

    @Suppress("UNCHECKED_CAST")
    fun apps(pin: String): AppsLoaded {
        val o = post("/apps/list", mapOf("pin" to pin))
        val rows = (o["apps"] as? List<Map<String, Any?>>).orEmpty().mapNotNull { m ->
            AppRow(m["pkg"] as? String ?: return@mapNotNull null, m["label"] as? String ?: (m["pkg"] as String), AppCategory.of(m["category"] as? String) ?: AppCategory.OTHER,
                m["never"] == true, m["new"] == true)
        }
        return AppsLoaded(rows, AppSettings.fromMap(o["settings"] as Map<String, Any?>), o["supervision"] as? Map<String, Any?> ?: emptyMap())
    }

    /** [reviewed]: apps the parent looked at without giving them a rule (they stop being « nouvelles »). */
    @Suppress("UNCHECKED_CAST")
    fun saveApps(pin: String, s: AppSettings, reviewed: List<String> = emptyList()): AppSettings {
        val settings = linkedMapOf("supervise" to s.supervise, "newApp" to s.newApp.code, "reviewed" to reviewed,
            "rules" to s.rules.map { (id, l) -> linkedMapOf("profile" to id, "apps" to l.map { AppSettings.ruleMap(it) }) })
        return AppSettings.fromMap(post("/apps/rules/set", mapOf("pin" to pin, "rev" to s.rev, "settings" to settings))["settings"] as Map<String, Any?>)
    }

    @Suppress("UNCHECKED_CAST")
    private fun reportsLoaded(o: Map<String, Any?>) = ReportsLoaded(ReportConfig.fromMap(o["config"] as Map<String, Any?>),
        (o["phones"] as? List<Map<String, Any?>>).orEmpty().map { PhoneRow(it["id"] as String, it["name"] as? String ?: "Téléphone", it["designated"] == true) },
        (o["waiting"] as? Number)?.toInt() ?: 0)

    fun reportsConfig(pin: String): ReportsLoaded = reportsLoaded(post("/reports/config/get", mapOf("pin" to pin)))
    fun saveReportsConfig(pin: String, c: ReportConfig): ReportsLoaded = reportsLoaded(post("/reports/config/set", mapOf("pin" to pin, "rev" to c.rev, "config" to c.toMap())))
    fun designate(pin: String, phoneId: String): ReportsLoaded = reportsLoaded(post("/reports/recipients/add", mapOf("pin" to pin, "phoneId" to phoneId)))
    fun removeRecipient(pin: String, phoneId: String): ReportsLoaded = reportsLoaded(post("/reports/recipients/remove", mapOf("pin" to pin, "phoneId" to phoneId)))
    fun reportNow(pin: String): Int = (post("/reports/now", mapOf("pin" to pin))["queued"] as? Number)?.toInt() ?: 0

    fun createPin(pin: String) = post("/pin/create", mapOf("pin" to pin))
    fun changePin(old: String, new: String) = post("/pin/change", mapOf("pin" to old, "new" to new))
    fun unlock(pin: String) = post("/unlock", mapOf("pin" to pin))
    fun lock() = post("/lock", emptyMap())
    fun disable() = post("/disable", emptyMap())
    fun reset() = post("/reset", mapOf("confirm" to "RESET"))
    fun clearHistory(pin: String) = post("/history/clear", mapOf("pin" to pin))

    @Suppress("UNCHECKED_CAST")
    fun load(pin: String): Loaded {
        val o = post("/config/get", mapOf("pin" to pin))
        val learn = (o["learnProfiles"] as? List<Map<String, Any?>>).orEmpty().map { Triple(it["id"] as String, it["name"] as String, it["level"] as? String) }
        return Loaded(ParentalConfig.fromMap(o["config"] as Map<String, Any?>), learn, o["status"] as? Map<String, Any?> ?: emptyMap())
    }

    @Suppress("UNCHECKED_CAST")
    fun save(pin: String, cfg: ParentalConfig): ParentalConfig {
        val o = post("/config/set", mapOf("pin" to pin, "rev" to cfg.rev, "config" to cfg.toMap()))
        return ParentalConfig.fromMap(o["config"] as Map<String, Any?>)
    }

    fun report(pin: String, days: Int = 7): Map<String, Any?> = post("/report", mapOf("pin" to pin, "days" to days))

    /** The videos stored on the TV: (file name, title, storage label), to classify them. */
    @Suppress("UNCHECKED_CAST")
    fun videos(): List<Triple<String, String, String?>> =
        (JsonLite.obj(callPath("GET", "/api/library", null))["files"] as? List<Map<String, Any?>>).orEmpty()
            .filter { it["type"] == "video" }.map { Triple(it["name"] as String, it["title"] as? String ?: (it["name"] as String), it["volumeLabel"] as? String) }

    private fun post(route: String, body: Map<String, Any?>): Map<String, Any?> = JsonLite.obj(call("POST", route, JsonLite.write(body)))

    private fun call(method: String, route: String, body: String?): String = callPath(method, ParentalApi.ROOT + route, body)

    private fun callPath(method: String, path: String, body: String?): String {
        val c = URL(base + path).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method; c.connectTimeout = 4000; c.readTimeout = 15_000
            // a trusted phone presents its token (X-CB-Token), anybody else the TV's PIN (X-CB-Pin): TvAuth says which header
            castbridge.core.trust.TvAuth.header(tvPin).let { (h, v) -> c.setRequestProperty(h, v) }
            if (body != null) {
                val bytes = body.toByteArray(Charsets.UTF_8)
                c.doOutput = true; c.setRequestProperty("Content-Type", "application/json; charset=utf-8"); c.setFixedLengthStreamingMode(bytes.size)
                c.outputStream.use { it.write(bytes) }
            }
            val code = c.responseCode
            val text = (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code >= 400) {
                val o = runCatching { JsonLite.obj(text) }.getOrNull()
                val msg = when {
                    code == 401 && o?.get("error") == "bad pin" -> "Le code de connexion de la TV n'est plus accepté : reconnectez la TV."
                    code == 401 -> "La TV est verrouillée après trop d'essais : réessayez dans ${(o?.get("retryAfter") as? Number)?.toLong() ?: 60} s."
                    else -> (o?.get("error") as? String) ?: "Erreur $code"
                }
                throw ParentalError(code, msg, (o?.get("retryAfter") as? Number)?.toLong())
            }
            return text
        } finally { c.disconnect() }
    }
}
