package castbridge.core.owner

import java.util.Base64

/**
 * THE signed envelope (docs/ACTIVATION-FORMAT.md § Enveloppe). Every signed message of the system uses it: activations, owner commands, revocation lists and deferred orders
 * (docs/agent-briefs/deferred-orders.md). One codec, one canonical text, one signature check; only the BODY differs per [type]. Adding a type adds a body parser and a scope, never a format.
 *
 * ```
 * castbridge-envelope-v1
 * type=<activation|command|revocation|order|…>
 * kid=<16 hex>             seq=<integer>        nonce=<8..64 hex>
 * issuedAt=<ms>            notBefore=<ms>       expiresAt=<ms>
 * target=<any|device|license:<id>|group:<id>>
 * k=<n>  factor=<TYPE>|<32 hex> …        (only when target=device)
 * --
 * <body lines of the type>
 * ```
 */
class Envelope(
    val type: String, val keyId: String, val seq: Long, val nonce: String, val issuedAt: Long, val notBefore: Long, val expiresAt: Long,
    val target: Target, val body: List<String>, val signature: String,
) {
    /** Who the message is for. */
    sealed class Target {
        /** Any device (a revocation list, a global policy). */
        object Any : Target()
        /** One device, by its factor set (k of n must match, DeviceIdentity.matches). */
        data class Device(val k: Int, val factors: Map<FactorKind, String>) : Target()
        /** Every device holding a seat of this licence. */
        data class License(val id: String) : Target()
        /** A named group of devices (orders). */
        data class Group(val id: String) : Target()
    }

    fun canonicalPayload(): String = payload(type, keyId, seq, nonce, issuedAt, notBefore, expiresAt, target, body)

    /** "cbx1.<payload base64url, no padding>.<signature base64 with padding>". */
    fun encode(): String = "$PREFIX." + Base64.getUrlEncoder().withoutPadding().encodeToString(canonicalPayload().toByteArray(Charsets.UTF_8)) + "." + signature

    fun withSignature(sig: String) = Envelope(type, keyId, seq, nonce, issuedAt, notBefore, expiresAt, target, body, sig)

    companion object {
        const val FORMAT = "castbridge-envelope-v1"
        const val PREFIX = "cbx1"
        const val BODY_MARK = "--"
        val HEX = Regex("^[0-9a-f]{8,64}$")
        val ID = Regex("^[a-z0-9][a-z0-9-]{0,63}$")
        private val FP = Regex("^[0-9a-f]{32}$")
        private val TYPE = Regex("^[a-z][a-z0-9-]{0,31}$")

        fun payload(type: String, keyId: String, seq: Long, nonce: String, issuedAt: Long, notBefore: Long, expiresAt: Long, target: Target, body: List<String>): String = buildList {
            add(FORMAT); add("type=$type"); add("kid=$keyId"); add("seq=$seq"); add("nonce=$nonce")
            add("issuedAt=$issuedAt"); add("notBefore=$notBefore"); add("expiresAt=$expiresAt")
            when (target) {
                Target.Any -> add("target=any")
                is Target.Device -> { add("target=device"); add("k=${target.k}"); target.factors.toSortedMap().forEach { (f, fp) -> add("factor=${f.name}|$fp") } }
                is Target.License -> add("target=license:${target.id}")
                is Target.Group -> add("target=group:${target.id}")
            }
            add(BODY_MARK)
            addAll(body)
        }.joinToString("\n")

        /** The envelope in [token], or null. The canonical text is rebuilt from the parsed fields and must equal the received one byte for byte. The signature is NOT checked here. */
        fun decode(token: String): Envelope? = runCatching {
            val parts = token.trim().split('.')
            require(parts.size == 3 && parts[0] == PREFIX)
            val text = String(Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8)
            val lines = text.split('\n')
            require(lines[0] == FORMAT)
            fun field(i: Int, key: String) = lines[i].also { require(it.startsWith("$key=")) }.substringAfter('=')
            val type = field(1, "type").also { require(TYPE.matches(it)) }
            val kid = field(2, "kid").also { require(HEX.matches(it)) }
            val seq = field(3, "seq").toLong().also { require(it >= 0) }
            val nonce = field(4, "nonce").also { require(HEX.matches(it)) }
            val issuedAt = field(5, "issuedAt").toLong(); val notBefore = field(6, "notBefore").toLong(); val expiresAt = field(7, "expiresAt").toLong()
            var i = 9
            val target = when (val t = field(8, "target")) {
                "any" -> Target.Any
                "device" -> {
                    val k = field(i++, "k").toInt()
                    val factors = LinkedHashMap<FactorKind, String>()
                    while (lines[i].startsWith("factor=")) { lines[i].removePrefix("factor=").split('|').let { require(it.size == 2 && FP.matches(it[1])); factors[FactorKind.valueOf(it[0])] = it[1] }; i++ }
                    Target.Device(k, factors)
                }
                else -> when {
                    t.startsWith("license:") -> Target.License(t.removePrefix("license:").also { require(ID.matches(it)) })
                    t.startsWith("group:") -> Target.Group(t.removePrefix("group:").also { require(ID.matches(it)) })
                    else -> error("target")
                }
            }
            require(lines[i] == BODY_MARK)
            val e = Envelope(type, kid, seq, nonce, issuedAt, notBefore, expiresAt, target, lines.drop(i + 1), parts[2])
            require(e.canonicalPayload() == text)
            e
        }.getOrNull()
    }
}

/** Highest sequence number accepted per key (persist it): the anti-replay and anti-rollback memory of a device. */
class SeqState(initial: Map<String, Long> = emptyMap()) {
    private val last = HashMap(initial)
    fun last(kid: String): Long = last[kid] ?: 0L
    fun record(kid: String, seq: Long) { if (seq > last(kid)) last[kid] = seq }
    fun snapshot(): Map<String, Long> = last.toMap()
}
