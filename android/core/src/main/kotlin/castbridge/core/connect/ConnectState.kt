package castbridge.core.connect

import castbridge.core.device.DeviceIdentity
import castbridge.core.device.DeviceStore
import castbridge.core.telemetry.Consent
import castbridge.core.update.UpdateSchedule
import java.net.URI
import java.util.UUID

/** Address of the CastBridge server (docs/API-SERVER.md): production by default, changeable in a hidden setting. */
object ServerUrl {
    const val DEFAULT = "https://bridge.sti-cm.com"

    /** Plain http is accepted only for a test server on the developer's machine (emulator: 10.0.2.2, adb reverse: 127.0.0.1). */
    private val LOCAL_HOSTS = setOf("127.0.0.1", "localhost", "10.0.2.2", "[::1]", "::1")

    /** The normalized address ("https://host[:port][/prefix]", no trailing slash, no /api/v1), or null if refused. */
    fun normalize(input: String?): String? = check(input).first

    /** Why [input] is refused (French, for the screen), or null if it is accepted. */
    fun problem(input: String?): String? = check(input).second

    private fun check(input: String?): Pair<String?, String?> {
        var t = input?.trim().orEmpty()
        if (t.isEmpty()) return null to "Adresse vide"
        if (!t.contains("://")) t = "https://$t"
        val u = runCatching { URI(t) }.getOrNull() ?: return null to "Adresse invalide"
        val scheme = u.scheme?.lowercase()
        val host = u.host?.lowercase() ?: return null to "Adresse invalide (nom du serveur manquant)"
        if (u.rawUserInfo != null || u.rawQuery != null || u.rawFragment != null) return null to "Adresse invalide"
        when (scheme) {
            "https" -> {}
            "http" -> if (host !in LOCAL_HOSTS) return null to "HTTPS obligatoire (http seulement pour un serveur de test sur cette machine)"
            else -> return null to "L'adresse doit commencer par https://"
        }
        var path = (u.rawPath ?: "").trimEnd('/')
        for (suffix in listOf("/api/v1", "/api", "/admin")) if (path.endsWith(suffix)) path = path.removeSuffix(suffix)
        val port = if (u.port > 0) ":${u.port}" else ""
        return "$scheme://$host$port$path" to null
    }

    /** A download link announced by a signed manifest: HTTPS (or a local test server), nothing else. */
    fun allowedDownload(url: String): Boolean = runCatching {
        val u = URI(url)
        u.scheme == "https" || (u.scheme == "http" && u.host?.lowercase() in LOCAL_HOSTS)
    }.getOrDefault(false)
}

/** Persistent key/value storage: private SharedPreferences on Android, a map in the tests. */
interface KeyValueStore {
    fun get(key: String): String?
    fun put(key: String, value: String?)
}

class MemoryKeyValueStore : KeyValueStore {
    private val m = java.util.concurrent.ConcurrentHashMap<String, String>()
    override fun get(key: String): String? = m[key]
    override fun put(key: String, value: String?) { if (value == null) m.remove(key) else m[key] = value }
}

/**
 * Everything the app keeps about its link with the server (identity, consent, token, last contact, update schedule…).
 * The device token is a secret: it only lives here (private app storage) and is never shown or logged.
 */
class ConnectState(private val kv: KeyValueStore) : DeviceStore {
    private fun long(k: String) = kv.get(k)?.toLongOrNull() ?: 0L
    private fun setLong(k: String, v: Long) = kv.put(k, if (v == 0L) null else v.toString())

    /** Server address in use (production unless changed in the advanced setting). */
    var baseUrl: String
        get() = kv.get("base_url")?.let { ServerUrl.normalize(it) } ?: ServerUrl.DEFAULT
        set(v) {
            val n = ServerUrl.normalize(v) ?: throw IllegalArgumentException(ServerUrl.problem(v) ?: "adresse invalide")
            if (n == baseUrl) return
            kv.put("base_url", n.takeIf { it != ServerUrl.DEFAULT })
            // another server does not know this device: register again there, forget what the old one said
            deviceToken = null; deviceId = null; blocked = false; serverChannel = null
            lastContactAt = 0; lastContactOk = false; lastContactMessage = null
            kv.put("quiz_reset", "1")
        }
    val customServer: Boolean get() = kv.get("base_url") != null

    /** Choice made on the information screen; null = not asked yet (nothing is sent before). */
    var consent: Consent?
        get() = when (kv.get("consent")) { "usage" -> Consent.USAGE; "essential" -> Consent.ESSENTIAL; else -> null }
        private set(v) = kv.put("consent", when (v) { Consent.USAGE -> "usage"; Consent.ESSENTIAL -> "essential"; null -> null })
    val consentVersion: String? get() = kv.get("consent_version")
    val consentAt: Long get() = long("consent_at")

    /** The information screen must be shown (first launch, or its text changed since the choice). */
    val needsConsent: Boolean get() = consent == null || consentVersion != ConsentText.VERSION

    /** Consent that applies now: only "essential" as long as the screen was not answered. */
    val effectiveConsent: Consent get() = if (needsConsent) Consent.ESSENTIAL else consent ?: Consent.ESSENTIAL

    fun setConsent(c: Consent, now: Long) {
        consent = c; kv.put("consent_version", ConsentText.VERSION); setLong("consent_at", now)
    }

    /** UUID drawn at the first launch and kept ([DeviceIdentity.newInstallId]). */
    val installId: String
        @Synchronized get() = kv.get("install_id") ?: DeviceIdentity.newInstallId().also { kv.put("install_id", it) }

    /** Random, never sent: salt of [castbridge.core.telemetry.Telemetry.hashForCounting]. */
    val countingSalt: String
        @Synchronized get() = kv.get("counting_salt") ?: UUID.randomUUID().toString().also { kv.put("counting_salt", it) }

    override var deviceId: String?
        get() = kv.get("device_id")
        set(v) = kv.put("device_id", v)
    override var deviceToken: String?
        get() = kv.get("device_token")
        set(v) = kv.put("device_token", v)

    /** Short id shown in the settings, to find the device in /admin (search box): first 8 characters of the device id. */
    val shortId: String? get() = deviceId?.take(8)

    var lastContactAt: Long get() = long("contact_at"); set(v) = setLong("contact_at", v)
    var lastAttemptAt: Long get() = long("attempt_at"); set(v) = setLong("attempt_at", v)
    var lastContactOk: Boolean get() = kv.get("contact_ok") == "1"; set(v) = kv.put("contact_ok", if (v) "1" else null)
    var lastContactMessage: String? get() = kv.get("contact_msg"); set(v) = kv.put("contact_msg", v)
    /** "direct" or "passerelle" (Bluetooth gateway of the phone). */
    var lastContactVia: String? get() = kv.get("contact_via"); set(v) = kv.put("contact_via", v)

    /** Directives of the server (last heartbeat). */
    var blocked: Boolean get() = kv.get("blocked") == "1"; set(v) = kv.put("blocked", if (v) "1" else null)
    var serverChannel: String? get() = kv.get("channel"); set(v) = kv.put("channel", v)
    var heartbeatSeconds: Int
        get() = kv.get("heartbeat_s")?.toIntOrNull()?.coerceIn(60, 86_400) ?: 900
        set(v) = kv.put("heartbeat_s", v.coerceIn(60, 86_400).toString())
    val channel: String get() = serverChannel?.takeIf { it == "beta" || it == "stable" } ?: "stable"

    var updateSchedule: UpdateSchedule.State
        get() = UpdateSchedule.State.decode(kv.get("update_schedule"))
        set(v) = kv.put("update_schedule", v.encode())
    var lastUpdateMessage: String? get() = kv.get("update_msg"); set(v) = kv.put("update_msg", v)

    /** "from,to" while an update is being installed: checked at the next start ([ServerLink.onStartup]). */
    var pendingInstall: Pair<Int, Int>?
        get() = kv.get("pending_install")?.split(",")?.let { p -> runCatching { p[0].toInt() to p[1].toInt() }.getOrNull() }
        set(v) = kv.put("pending_install", v?.let { "${it.first},${it.second}" })

    var lastFlushAt: Long get() = long("flush_at"); set(v) = setLong("flush_at", v)

    var quizSyncedAt: Long get() = long("quiz_at"); set(v) = setLong("quiz_at", v)
    var quizAttemptAt: Long get() = long("quiz_attempt_at"); set(v) = setLong("quiz_attempt_at", v)
    var quizMessage: String? get() = kv.get("quiz_msg"); set(v) = kv.put("quiz_msg", v)
    /** Set when the server changed: the question cache of the old server is dropped at the next sync. */
    fun takeQuizReset(): Boolean = (kv.get("quiz_reset") == "1").also { if (it) kv.put("quiz_reset", null) }

    /**
     * After "Effacer mes données": a new identity (new install id, no token, no counting salt) and the information screen
     * again before anything is sent.
     */
    fun resetIdentity() {
        for (k in listOf("install_id", "counting_salt", "device_id", "device_token", "consent", "consent_version", "consent_at", "contact_at",
            "attempt_at", "contact_ok", "contact_msg", "contact_via", "blocked", "channel", "flush_at", "pending_install")) kv.put(k, null)
    }
}
