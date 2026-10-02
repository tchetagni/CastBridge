package castbridge.core.owner

import castbridge.core.lots.Right
import java.util.Base64

/** Why a proof is refused. The first six come before the signature is fully trusted, the others after (the TV's code and name are then given for the messages). */
enum class ProofRejection {
    MALFORMED, WRONG_TYPE, NONCE_MISMATCH, REPLAY, BAD_SIGNATURE, WINDOW_CLOSED,
    /** The TV is in trial / grace / reduced mode / locked / suspended: it unlocks nothing on the phone. */
    TRIAL, GRACE, DEGRADED, LOCKED, SUSPENDED,
    /** The embedded activation: unreadable, key unknown, revoked or without the scope, bad signature, wrong subject, seat revoked. */
    ACTIVATION_INVALID, CODE_MISMATCH, USAGE_EXCEEDED,
}

/** What the phone keeps of an accepted proof. [activationToken] is the raw production activation: the phone sends it as `X-CB-TV-Proof` (w6-09). */
data class Proof(val tvCode: String, val tvName: String, val installKeyId: String, val endsAt: Long?, val superUnlimited: Boolean, val verifiedAt: Long, val seq: Long, val activationToken: String)

sealed class ProofResult {
    data class Accepted(val proof: Proof) : ProofResult()
    /** [tvCode] and [tvName] are set only for the refusals made after the signature was checked. */
    data class Rejected(val reason: ProofRejection, val message: String, val tvCode: String? = null, val tvName: String? = null) : ProofResult()
    /** Signature valid under the key carried by the proof, but no key is pinned for this TV yet: the caller decides the TOFU (showing [fingerprint] on both screens), pins, then verifies AGAIN with the pin. */
    data class NeedsPin(val keyId: String, val publicKeyBase64: String, val fingerprint: String, val tvCode: String, val tvName: String) : ProofResult()
    /** A key is pinned and the TV now signs with another one (reinstalled TV): the pin is untouched, the user must confirm [fingerprint] (w6-16). */
    data class IdentityChanged(val keyId: String, val publicKeyBase64: String, val fingerprint: String, val tvCode: String, val tvName: String) : ProofResult()
}

/**
 * The proof a CastBridge-TV gives a phone that it holds a production activation that counts (docs/coordination/DESIGN-W6-PARENTAL-PHONE-GATE.md § 3.3). It is a `cbx1` envelope of type `proof`
 * signed by the installation key ([InstallSigner]) over the phone's challenge. No wall clock is read here: the callers pass the times.
 *
 * Body, one line per field, in this order (`activation=` last and optional):
 * ```
 * code=<device code>   name=<up to 40 chars>   state=production|trial|grace|degraded|locked|suspended   label=<TvAccess.label>
 * endsAt=<ms|0>        super=0|1               uptime=<ms>     installKey=<base64 Ed25519 public key>   activation=<cbx1 token>
 * ```
 * Envelope: `kid` = installation key id, `seq` = TV counter, `nonce` = the phone's challenge, `issuedAt = notBefore` = TV time, `expiresAt` = +10 min, target = the TV's factor set.
 * The public key travels in the proof so a phone can pin it on first link; it is bound to `kid` (a hash), so it cannot be swapped.
 */
object TvProof {
    const val TYPE = "proof"
    const val WINDOW_MS = 10L * 60 * 1000
    const val NAME_MAX = 40
    /** Tolerance between the TV clock and the phone clock for the window check (the challenge nonce is the real freshness guarantee). */
    const val SKEW_MS = 24L * 3600 * 1000

    enum class State { PRODUCTION, TRIAL, GRACE, DEGRADED, LOCKED, SUSPENDED; val wire get() = name.lowercase() }

    /** `state` of a TV: production (super, or a key installed that is not a trial and not suspended), else suspended, trial, [grace] (told by the caller), locked. */
    fun stateOf(access: TvAccess, grace: Boolean = false): State = when {
        access.superUnlimited || (access.keyInstalled && !access.trial && !access.suspended) -> State.PRODUCTION
        access.suspended -> State.SUSPENDED
        access.keyInstalled && access.trial -> State.TRIAL
        grace -> State.GRACE
        else -> State.LOCKED
    }

    /** End of an activation as the proof states it: 0 for a `super` activation or one without `usage` right, else the earliest `usage` end. Both sides use this one rule. */
    fun endsAtOf(a: Activation): Long = if (a.rights.any { it is Right.Super }) 0L else a.rights.filterIsInstance<Right.Usage>().minOfOrNull { it.endsAt } ?: 0L

    private fun line(s: String) = s.replace('\r', ' ').replace('\n', ' ').trim()

    /** TV side. [seq] is the TV's own counter, [tvClockNow] = [TvClock.now], [activationToken] = the production activation that counts (null: none). [grace] and [uptimeMs] are told by the caller. */
    fun build(signer: InstallSigner, device: Fingerprints, nonceHex: String, seq: Long, tvClockNow: Long, access: TvAccess, activationToken: String?, name: String,
              grace: Boolean = false, uptimeMs: Long = 0L): String {
        val act = activationToken?.let { Activation.decode(it) }
        val body = listOfNotNull(
            "code=${DeviceCode.of(device)}", "name=${line(name).take(NAME_MAX).trim()}", "state=${stateOf(access, grace).wire}", "label=${line(access.label)}",
            "endsAt=${act?.let(::endsAtOf) ?: 0L}", "super=${if (act?.rights?.any { it is Right.Super } ?: access.superUnlimited) 1 else 0}", "uptime=${maxOf(0L, uptimeMs)}",
            "installKey=${signer.publicKeyBase64}", activationToken?.let { "activation=${it.trim()}" },
        )
        val target = Envelope.Target.Device(DeviceIdentity.kFor(device.n), device.byKind)
        val payload = Envelope.payload(TYPE, signer.keyId, seq, nonceHex, tvClockNow, tvClockNow, tvClockNow + WINDOW_MS, target, body)
        return Envelope(TYPE, signer.keyId, seq, nonceHex, tvClockNow, tvClockNow, tvClockNow + WINDOW_MS, target, body, signer.sign(payload)).encode()
    }

    private class Body(val code: String, val name: String, val state: State, val endsAt: Long, val superFlag: Boolean, val installKey: String, val activation: String?)

    private fun parseBody(e: Envelope): Body? = runCatching {
        val b = e.body
        require(b.size == 8 || b.size == 9)
        fun f(i: Int, key: String) = b[i].also { require(it.startsWith("$key=")) }.substringAfter('=')
        val name = f(1, "name").also { require(it.length <= NAME_MAX) }
        val state = State.values().first { it.wire == f(2, "state") }
        f(3, "label")
        val endsAt = f(4, "endsAt").toLong().also { require(it >= 0) }
        val sup = f(5, "super").also { require(it == "0" || it == "1") }
        f(6, "uptime").toLong().also { require(it >= 0) }
        Body(DeviceCode.parse(f(0, "code")) ?: error("code"), name, state, endsAt, sup == "1", f(7, "installKey"), if (b.size == 9) f(8, "activation") else null)
    }.getOrNull()

    private fun bad(r: ProofRejection, m: String, b: Body? = null) = ProofResult.Rejected(r, m, b?.code, b?.name)

    /**
     * Phone side, fixed order, first reason wins: readable → type → nonce (equal to [expectedNonce], not in [consumed]) → signature (by the key the proof carries; then [pinnedKey]:
     * none = [ProofResult.NeedsPin], another = [ProofResult.IdentityChanged]) → sequence not older than [lastSeq] → window (±[skewMs]) → `state == production` → the embedded activation
     * (key known, not revoked, scope, signature, subject TV, seat not revoked; NO hardware check: the phone has no factors) → `kind == PRODUCTION` → device code = hash of the activation's
     * factors = the proof's target factors → no `usage` ceiling passed at `max(nowMs, issuedAt)`. [ActivationVerifier] is not used (it checks the 48 h install window and the hardware).
     * The caller marks the nonce consumed after ANY answer other than [ProofResult.NeedsPin].
     */
    fun verify(token: String, expectedNonce: String, pinnedKey: TrustedKey?, trusted: KeyRing, nowMs: Long, consumed: Set<String> = emptySet(), lastSeq: Long? = null,
               revocations: RevocationState = RevocationState(), skewMs: Long = SKEW_MS): ProofResult {
        val env = Envelope.decode(token) ?: return bad(ProofRejection.MALFORMED, "Preuve illisible")
        if (env.type != TYPE) return bad(ProofRejection.WRONG_TYPE, "Ce message n'est pas une preuve de TV")
        val body = parseBody(env) ?: return bad(ProofRejection.MALFORMED, "Preuve illisible")
        val target = env.target as? Envelope.Target.Device ?: return bad(ProofRejection.MALFORMED, "Preuve illisible")
        if (env.nonce != expectedNonce) return bad(ProofRejection.NONCE_MISMATCH, "Cette réponse ne correspond pas à la demande")
        if (expectedNonce in consumed) return bad(ProofRejection.REPLAY, "Cette réponse a déjà été utilisée")
        val pub = runCatching { Base64.getDecoder().decode(body.installKey) }.getOrNull()
        if (pub == null || pub.size != 32 || KeyRing.idOf(body.installKey) != env.keyId) return bad(ProofRejection.MALFORMED, "Preuve illisible")
        if (!runCatching { TrustedKey(env.keyId, body.installKey).verify(env.canonicalPayload(), env.signature) }.getOrDefault(false)) return bad(ProofRejection.BAD_SIGNATURE, "Signature de la preuve invalide")
        if (pinnedKey == null) return ProofResult.NeedsPin(env.keyId, body.installKey, InstallSigner.fingerprintOf(body.installKey), body.code, body.name)
        if (pinnedKey.keyId != env.keyId || pinnedKey.publicKeyBase64 != body.installKey)
            return ProofResult.IdentityChanged(env.keyId, body.installKey, InstallSigner.fingerprintOf(body.installKey), body.code, body.name)
        if (lastSeq != null && env.seq < lastSeq) return bad(ProofRejection.REPLAY, "Preuve plus ancienne que la dernière reçue", body)
        if (nowMs > env.expiresAt + skewMs || nowMs + skewMs < env.notBefore) return bad(ProofRejection.WINDOW_CLOSED, "Preuve périmée : à redemander", body)
        when (body.state) {
            State.PRODUCTION -> {}
            State.TRIAL -> return bad(ProofRejection.TRIAL, "La TV est en version d'essai", body)
            State.GRACE -> return bad(ProofRejection.GRACE, "La TV est en période de grâce", body)
            State.DEGRADED -> return bad(ProofRejection.DEGRADED, "La clé de la TV est terminée", body)
            State.SUSPENDED -> return bad(ProofRejection.SUSPENDED, "Vérifiez l'heure de la TV", body)
            State.LOCKED -> return bad(ProofRejection.LOCKED, "La TV n'a pas de clé de production", body)
        }
        val token0 = body.activation ?: return bad(ProofRejection.LOCKED, "La TV n'a pas de clé de production", body)
        val a = Activation.decode(token0) ?: return bad(ProofRejection.ACTIVATION_INVALID, "Activation de la TV illisible", body)
        val key = trusted.find(a.keyId)
        when {
            key == null -> return bad(ProofRejection.ACTIVATION_INVALID, "Activation signée par une clé inconnue", body)
            trusted.isRevoked(a.keyId) || a.keyId in revocations.keys -> return bad(ProofRejection.ACTIVATION_INVALID, "Activation signée par une clé révoquée", body)
            !key.verify(a.canonicalPayload(), a.signature) -> return bad(ProofRejection.ACTIVATION_INVALID, "Signature de l'activation invalide", body)
            a.subject != Subject.TV -> return bad(ProofRejection.ACTIVATION_INVALID, "Cette activation n'est pas celle d'une TV", body)
            a.kind == ActivationKind.TRIAL -> return bad(ProofRejection.TRIAL, "La TV est en version d'essai", body)
            !(key.allows(KeyScope.ISSUE_PRODUCTION) || key.allows(KeyScope.REACTIVATE)) -> return bad(ProofRejection.ACTIVATION_INVALID, "Cette clé ne délivre pas de production", body)
            a.rights.any { it is Right.Super } && !key.allows(KeyScope.SUPER_UNLIMITED) -> return bad(ProofRejection.ACTIVATION_INVALID, "Cette clé n'est pas celle du super administrateur", body)
            a.rights.any { it is Right.OpenAll } && !key.allows(KeyScope.COMMAND_OPEN_ALL) -> return bad(ProofRejection.ACTIVATION_INVALID, "Cette clé ne peut pas délivrer « tout ouvert »", body)
            revocations.seatRevoked(a) -> return bad(ProofRejection.ACTIVATION_INVALID, "Ce poste de la licence a été transféré ou révoqué", body)
        }
        if (DeviceCode.of(Fingerprints(a.factors)) != body.code || target.factors != a.factors || target.k != a.k) return bad(ProofRejection.CODE_MISMATCH, "L'activation n'est pas celle de cette TV", body)
        val isSuper = a.rights.any { it is Right.Super }
        if (isSuper != body.superFlag || endsAtOf(a) != body.endsAt) return bad(ProofRejection.MALFORMED, "Preuve incohérente avec l'activation", body)
        val now = maxOf(nowMs, env.issuedAt)
        if (a.rights.filterIsInstance<Right.Usage>().any { now >= it.endsAt || now < it.startsAt - ActivationPolicy.USAGE_SKEW_MS })
            return bad(ProofRejection.USAGE_EXCEEDED, "La durée d'usage de la clé de la TV est terminée", body)
        return ProofResult.Accepted(Proof(body.code, body.name, env.keyId, body.endsAt.takeIf { it > 0 }, isSuper, nowMs, env.seq, token0))
    }
}
