package castbridge.core.tunnel

import castbridge.core.net.JsonLite
import castbridge.core.owner.Activation
import castbridge.core.owner.ActivationKind
import castbridge.core.lots.Right
import castbridge.core.owner.SafeFile
import castbridge.core.owner.TvGate
import java.io.File
import java.security.MessageDigest

/** `POST /api/v1/tunnel/enroll` body (docs/REMOTE-TUNNEL.md § 2.1). */
data class EnrollRequest(val activation: String, val sshPublicKey: String, val deviceCode: String) {
    fun toJson(): String = JsonLite.write(linkedMapOf("activation" to activation, "sshPublicKey" to sshPublicKey, "deviceCode" to deviceCode))
}

/** What the server answered, kept on the TV: where to connect and which remote port is this TV's own. [activationId] / [keyId] say what it was enrolled with (re-enroll when either changes). */
data class Enrollment(val host: String, val sshPort: Int, val user: String, val port: Int, val hostKeyFingerprint: String?, val activationId: String, val keyId: String) {
    fun toJson(): String = JsonLite.write(linkedMapOf("host" to host, "sshPort" to sshPort, "user" to user, "port" to port, "hostKeyFingerprint" to hostKeyFingerprint,
        "activationId" to activationId, "keyId" to keyId))

    companion object {
        private val HOST = Regex("^[A-Za-z0-9]([A-Za-z0-9.-]{0,251}[A-Za-z0-9])?$")
        private val USER = Regex("^[a-z_][a-z0-9_-]{0,31}$")
        private val FP = Regex("^SHA256:[A-Za-z0-9+/]{43}$")

        /** Parses and validates a 200 reply `{"host","sshPort","user","port","hostKeyFingerprint"?}`; null = unusable. */
        fun parseReply(body: String, activationId: String, keyId: String): Enrollment? = runCatching {
            val m = JsonLite.obj(body)
            val host = m["host"] as String; val sshPort = (m["sshPort"] as Number).toInt(); val user = m["user"] as String; val port = (m["port"] as Number).toInt()
            val fp = (m["hostKeyFingerprint"] as? String)?.takeIf { it.isNotBlank() }
            if (!HOST.matches(host) || ".." in host || sshPort !in 1..65535 || !USER.matches(user) || port !in 1024..65535) return null
            if (fp != null && !FP.matches(fp)) return null
            Enrollment(host, sshPort, user, port, fp, activationId, keyId)
        }.getOrNull()

        fun fromJson(text: String): Enrollment? = runCatching {
            val m = JsonLite.obj(text)
            parseReply(text, m["activationId"] as String, m["keyId"] as String)
        }.getOrNull()
    }
}

/** Result of one enrollment attempt. */
sealed class EnrollOutcome {
    class Ok(val enrollment: Enrollment) : EnrollOutcome()
    /** 403: the owner switched this TV's tunnel off. Stop and wait [TunnelEnroll.REVOKED_MS]. */
    class Revoked(val message: String) : EnrollOutcome()
    /** 429 / 503 / network failure / any other refusal: back off, at least [minDelayMs]. */
    class Retry(val message: String, val minDelayMs: Long = 0L) : EnrollOutcome()
}

object TunnelEnroll {
    const val REVOKED_MS = 24 * 3_600_000L
    const val RATE_LIMITED_MIN_MS = 10 * 60_000L
    const val REFUSED_MIN_MS = 60 * 60_000L        // 400 / 401 / 404 ...: the request itself is refused, hammering the server would not help

    fun activationId(activation: String): String = MessageDigest.getInstance("SHA-256").digest(activation.toByteArray(Charsets.UTF_8)).take(8).joinToString("") { "%02x".format(it) }

    /** Maps an HTTP answer to what the machine does next. The body is the server's French message on errors (never logged with a token). */
    fun outcome(code: Int, body: String, activationId: String, keyId: String): EnrollOutcome = when {
        code == 200 -> Enrollment.parseReply(body, activationId, keyId)?.let { EnrollOutcome.Ok(it) } ?: EnrollOutcome.Retry("Réponse d'enrôlement illisible", REFUSED_MIN_MS)
        code == 403 -> EnrollOutcome.Revoked(message(body, "Assistance à distance désactivée pour cette TV par l'éditeur"))
        code == 429 -> EnrollOutcome.Retry(message(body, "Trop de demandes"), RATE_LIMITED_MIN_MS)
        code == 503 || code in 500..599 -> EnrollOutcome.Retry(message(body, "Serveur indisponible"), 0L)
        else -> EnrollOutcome.Retry(message(body, "Enrôlement refusé ($code)"), REFUSED_MIN_MS)
    }

    private fun message(body: String, fallback: String): String =
        runCatching { (JsonLite.obj(body)["message"] ?: JsonLite.obj(body)["error"]) as? String }.getOrNull()?.takeIf { it.isNotBlank() }?.take(200) ?: fallback

    /**
     * The newest activation that still counts (same filter as the key badge: no ended usage ceiling, no spent implicit trial ceiling), trial or production, or null.
     * Newest = latest issue time, then the sequence number.
     */
    fun pickActivation(all: List<Activation>, nowMs: Long): Activation? = all
        .filter { a -> a.rights.filterIsInstance<Right.Usage>().none { nowMs >= it.endsAt } && TvGate.implicitUsageEnd(a).let { it == null || nowMs < it } }
        .maxWithOrNull(compareBy<Activation>({ it.issuedAt }, { it.seq }, { if (it.kind == ActivationKind.PRODUCTION) 1 else 0 }))
}

/** What the TV keeps between runs: the enrollment and the end of the 24 h pause after a 403. Written through [SafeFile]. */
class EnrollmentStore(private val file: File) {
    fun load(): Pair<Enrollment?, Long> {
        val t = SafeFile.read(file) { parse(it) != null }?.text ?: return null to 0L
        return parse(t) ?: (null to 0L)
    }

    private fun parse(text: String): Pair<Enrollment?, Long>? = runCatching {
        val m = JsonLite.obj(text)
        val e = (m["enrollment"] as? Map<*, *>)?.let { Enrollment.fromJson(JsonLite.write(it)) }
        e to ((m["revokedUntil"] as? Number)?.toLong() ?: 0L)
    }.getOrNull()

    fun save(e: Enrollment?, revokedUntil: Long) {
        val json = JsonLite.write(linkedMapOf("enrollment" to e?.let { JsonLite.obj(it.toJson()) }, "revokedUntil" to revokedUntil)) + "\n"
        SafeFile.write(file, json) { parse(it) != null }
    }
}
