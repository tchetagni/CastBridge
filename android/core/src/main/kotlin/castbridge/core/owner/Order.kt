package castbridge.core.owner

/**
 * A deferred order (docs/agent-briefs/deferred-orders.md): the envelope type `order`. Body = one `action` (an identifier of the closed, documented list of the policy engine) and
 * sorted parameters. This file only gives the order its place in the common envelope and its generic checks; WHICH actions exist and what they do is the policy engine's closed list.
 * An order is signed by a key with the [KeyScope.POLICY] scope (the server has it; it never has `transfer` or `open`), is targeted ([Envelope.Target]: any device, one device, a licence,
 * a group), has a window (`notBefore`, `expiresAt`) and a sequence number that must be STRICTLY greater than the last accepted one for that key (anti-replay, anti-rollback).
 */
data class Order(val action: String, val params: Map<String, String>)

object Orders {
    const val TYPE = "order"
    const val MAX_PARAMS = 16
    const val MAX_VALUE = 512
    private val NAME = Regex("^[a-z][a-z0-9_.-]{0,31}$")
    private val ACTION = Regex("^[a-z][a-z0-9_.-]{0,47}$")

    fun body(action: String, params: Map<String, String>): List<String> = listOf("action=$action") + params.toSortedMap().map { "param=${it.key}|${it.value}" }

    /** Null when the order breaks the generic limits (identifier syntax, number and size of parameters, no line break). */
    fun valid(action: String, params: Map<String, String>): Boolean =
        ACTION.matches(action) && params.size <= MAX_PARAMS && params.all { (k, v) -> NAME.matches(k) && v.length <= MAX_VALUE && '\n' !in v && '\r' !in v }

    fun from(e: Envelope): Order? = runCatching {
        require(e.type == TYPE)
        val action = e.body[0].also { require(it.startsWith("action=")) }.removePrefix("action=")
        val params = LinkedHashMap<String, String>()
        for (l in e.body.drop(1)) { require(l.startsWith("param=")); val r = l.removePrefix("param="); params[r.substringBefore('|')] = r.substringAfter('|', "") }
        require(valid(action, params))
        Order(action, params).also { require(Envelope.payload(TYPE, e.keyId, e.seq, e.nonce, e.issuedAt, e.notBefore, e.expiresAt, e.target, body(action, params)) == e.canonicalPayload()) }
    }.getOrNull()

    /** The signed token of an order (the issuing tools use this, never their own formatting). */
    fun issue(signer: Signer, seq: Long, nonce: String, issuedAt: Long, notBefore: Long, expiresAt: Long, target: Envelope.Target, action: String, params: Map<String, String> = emptyMap()): String {
        require(valid(action, params)) { "ordre invalide" }
        require(Envelope.HEX.matches(nonce) && expiresAt > notBefore)
        val unsigned = Envelope(TYPE, signer.keyId, seq, nonce, issuedAt, notBefore, expiresAt, target, body(action, params), "")
        return unsigned.withSignature(java.util.Base64.getEncoder().encodeToString(signer.sign(unsigned.canonicalPayload().toByteArray(Charsets.UTF_8)))).encode()
    }
}

/** What the device knows about itself, to decide whether a targeted message is for it. */
class DeviceContext(val fingerprints: Fingerprints, val licenses: Set<String> = emptySet(), val groups: Set<String> = emptySet())

sealed class OrderResult {
    data class Accepted(val order: Order, val envelope: Envelope) : OrderResult()
    data class Rejected(val reason: Rejection, val message: String) : OrderResult()
}

/**
 * The generic checks of an order, before the policy engine looks at the action. A refused order does NOT advance the sequence memory (the next valid one is still accepted); an
 * accepted one does. [nowMs] must be [TvClock.now] (the device clock is unreliable offline).
 */
class OrderVerifier(private val keys: KeyRing, private val seqState: SeqState, private val revocations: RevocationState = RevocationState(), private val skewMs: Long = 24L * 3600 * 1000) {
    fun verify(token: String, device: DeviceContext, nowMs: Long): OrderResult {
        val env = Envelope.decode(token) ?: return no(Rejection.MALFORMED, "Ordre illisible")
        if (env.type != Orders.TYPE) return no(Rejection.UNKNOWN_TYPE, "Ce message n'est pas un ordre")
        val key = keys.find(env.keyId) ?: return no(Rejection.UNKNOWN_KEY, "Clé inconnue")
        if (keys.isRevoked(env.keyId) || env.keyId in revocations.keys) return no(Rejection.REVOKED_KEY, "Clé révoquée")
        if (!key.verify(env.canonicalPayload(), env.signature)) return no(Rejection.BAD_SIGNATURE, "Signature invalide")
        if (!key.allows(KeyScope.POLICY)) return no(Rejection.KEY_NOT_ALLOWED, "Cette clé n'a pas la portée policy")
        val order = Orders.from(env) ?: return no(Rejection.BAD_ORDER, "Ordre hors schéma")
        val forMe = when (val t = env.target) {
            Envelope.Target.Any -> true
            is Envelope.Target.Device -> DeviceIdentity.matches(t.factors, t.k, device.fingerprints)
            is Envelope.Target.License -> t.id in device.licenses
            is Envelope.Target.Group -> t.id in device.groups
        }
        if (!forMe) return no(Rejection.WRONG_TARGET, "Ordre destiné à un autre appareil")
        if (env.seq <= seqState.last(env.keyId)) return no(Rejection.STALE_SEQUENCE, "Numéro de séquence déjà vu ou dépassé")
        if (nowMs + skewMs < env.notBefore) return no(Rejection.NOT_YET_VALID, "Ordre pas encore valable")
        if (nowMs > env.expiresAt) return no(Rejection.WINDOW_CLOSED, "Ordre expiré")
        seqState.record(env.keyId, env.seq)
        return OrderResult.Accepted(order, env)
    }

    private fun no(r: Rejection, m: String) = OrderResult.Rejected(r, m)
}
