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

    fun status(): Map<String, Any?> = JsonLite.obj(call("GET", "", null))
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
            c.setRequestProperty("X-CB-Pin", tvPin)
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
