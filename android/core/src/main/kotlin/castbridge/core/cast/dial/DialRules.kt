package castbridge.core.cast.dial

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.security.MessageDigest
import java.util.UUID

/** Règles pures du récepteur DIAL (docs/TV-CAST-DIAL.md) : aucune dépendance Android, tout est testé en JVM. */
object DialRules {
    const val APP_YOUTUBE = "YouTube"
    /** Table des applications : fermée. Tout autre nom répond 404. */
    val APPS: Set<String> = setOf(APP_YOUTUBE)
    const val YOUTUBE_ORIGIN = "package:com.google.android.youtube"
    const val YOUTUBE_TV_URL = "https://www.youtube.com/tv"
    const val YOUTUBE_TV_PACKAGE = "com.google.android.youtube.tv"
    const val MAX_BODY = 4096
    const val MAX_QUERY = 1024
    const val SEARCH_TARGET = "urn:dial-multiscreen-org:service:dial:1"

    /** Source acceptée : boucle locale, RFC1918, lien-local IPv4 (169.254/16), fc00::/7, fe80::/10. Le reste est ignoré sans réponse. */
    fun isLanSource(a: InetAddress?): Boolean {
        if (a == null) return false
        if (a.isLoopbackAddress) return true
        val b = a.address
        return when (a) {
            is Inet4Address -> {
                val x = b[0].toInt() and 0xFF; val y = b[1].toInt() and 0xFF
                x == 10 || (x == 172 && y in 16..31) || (x == 192 && y == 168) || (x == 169 && y == 254)
            }
            is Inet6Address -> {
                val x = b[0].toInt() and 0xFF; val y = b[1].toInt() and 0xFF
                (x and 0xFE) == 0xFC || (x == 0xFE && (y and 0xC0) == 0x80)
            }
            else -> false
        }
    }

    /** Anti DNS-rebinding : l'en-tête Host est exactement « ip:port » d'une adresse de la TV. Un seul en-tête Host. */
    fun hostAllowed(hostHeaders: List<String>, allowed: Set<String>): Boolean =
        hostHeaders.size == 1 && hostHeaders[0] in allowed

    /** Règle CORS de DIAL : pas d'Origin, ou exactement celle de l'application YouTube. Toute autre (navigateur) est refusée. */
    fun originAllowed(originHeaders: List<String>): Boolean =
        originHeaders.isEmpty() || (originHeaders.size == 1 && originHeaders[0] == YOUTUBE_ORIGIN)

    /** Suffixe court et stable tiré de l'identifiant d'installation (4 hexadécimaux). */
    fun suffix(deviceId: String): String {
        val d = MessageDigest.getInstance("SHA-256").digest(deviceId.toByteArray(Charsets.UTF_8))
        return "%02X%02X".format(d[0].toInt() and 0xFF, d[1].toInt() and 0xFF)
    }
    fun friendlyName(deviceId: String) = "CastBridge-TV ${suffix(deviceId)}"
    /** UDN stable par installation. */
    fun udn(deviceId: String): String = "uuid:" + UUID.nameUUIDFromBytes("castbridge-tv-dial:$deviceId".toByteArray(Charsets.UTF_8))

    fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")

    fun deviceDescription(deviceId: String, manufacturer: String, model: String): String =
        "<?xml version=\"1.0\"?>\r\n" +
        "<root xmlns=\"urn:schemas-upnp-org:device-1-0\" xmlns:r=\"urn:restful-tv-org:schemas:upnp-dd\">\r\n" +
        "<specVersion><major>1</major><minor>0</minor></specVersion>\r\n" +
        "<device>\r\n" +
        "<deviceType>urn:schemas-upnp-org:device:tvdevice:1</deviceType>\r\n" +
        "<friendlyName>${esc(friendlyName(deviceId))}</friendlyName>\r\n" +
        "<manufacturer>${esc(manufacturer)}</manufacturer>\r\n" +
        "<modelName>${esc(model)}</modelName>\r\n" +
        "<UDN>${udn(deviceId)}</UDN>\r\n" +
        "</device>\r\n</root>\r\n"

    enum class AppState(val xml: String) { RUNNING("running"), STOPPED("stopped"), HIDDEN("hidden") }

    fun appStatus(name: String, state: AppState, allowStop: Boolean): String =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +
        "<service xmlns=\"urn:dial-multiscreen-org:schemas:dial\" dialVer=\"2.1\">\r\n" +
        "<name>${esc(name)}</name>\r\n" +
        "<options allowStop=\"$allowStop\"/>\r\n" +
        "<state>${state.xml}</state>\r\n" +
        (if (state == AppState.RUNNING) "<link rel=\"run\" href=\"run\"/>\r\n" else "") +
        "</service>\r\n"

    // ---------------------------------------------------------------- lancement

    sealed class Launch {
        class Ok(val query: String, val url: String) : Launch()
        class Refused(val status: Int, val why: String) : Launch()
    }
    private val KEY = Regex("[A-Za-z0-9_.-]{1,64}")
    private val VALUE = Regex("(?:[A-Za-z0-9_.~*,+-]|%[0-9A-Fa-f]{2})*")
    private val FORBIDDEN_ESCAPE = Regex("%(00|0[dD]|0[aA])")

    /** Corps de lancement de l'appli YouTube → URL fixe `https://www.youtube.com/tv?<corps>`. Jeu de caractères strict, 1 Ko, pas de CR/LF. */
    fun buildLaunch(body: ByteArray): Launch {
        if (body.size > MAX_BODY) return Launch.Refused(413, "corps trop grand")
        if (body.any { it < 0 }) return Launch.Refused(400, "caractère non ASCII")
        val s = String(body, Charsets.ISO_8859_1).trimEnd('\r', '\n', ' ')   // seules des fins de ligne finales sont tolérées
        if (s.length > MAX_QUERY) return Launch.Refused(413, "paramètres trop longs")
        if (s.isEmpty()) return Launch.Ok("", YOUTUBE_TV_URL)
        for (pair in s.split('&')) {
            val i = pair.indexOf('=')
            val k = if (i < 0) pair else pair.substring(0, i)
            val v = if (i < 0) "" else pair.substring(i + 1)
            if (!KEY.matches(k) || !VALUE.matches(v)) return Launch.Refused(400, "paramètre invalide")
            if (FORBIDDEN_ESCAPE.containsMatchIn(v)) return Launch.Refused(400, "séquence interdite")
        }
        return Launch.Ok(s, "$YOUTUBE_TV_URL?$s")
    }

    /** Pour le journal : les noms des paramètres seulement, jamais les valeurs (code d'appairage). */
    fun redact(query: String): String =
        if (query.isEmpty()) "(vide)" else query.split('&').joinToString("&") { it.substringBefore('=') + "=***" }
}

/** Au plus [max] lancements par fenêtre de [windowMs] (6 par minute). */
class LaunchRateLimiter(private val max: Int = 6, private val windowMs: Long = 60_000, private val clock: () -> Long = System::currentTimeMillis) {
    private val times = ArrayDeque<Long>()
    @Synchronized fun tryAcquire(): Boolean {
        val now = clock()
        while (times.isNotEmpty() && now - times.first() >= windowMs) times.removeFirst()
        if (times.size >= max) return false
        times.addLast(now); return true
    }
    @Synchronized fun retryAfterSeconds(): Int {
        val now = clock()
        val first = times.firstOrNull() ?: return 1
        return (((first + windowMs - now) + 999) / 1000).toInt().coerceAtLeast(1)
    }
}
