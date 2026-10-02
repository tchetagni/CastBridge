package castbridge.core.owner

import castbridge.core.lots.Right

/**
 * Verifies a ticketed line (`<mandat>|<activation>`, [TicketedActivation]) on the TV without touching [ActivationVerifier]: (1) the delegation against [ring]; (2) the activation with a ring
 * that also holds the agent key (scopes and window of the mandate); (3) the limits of the mandate on what the activation carries. An agent never issues an unlimited key, a rental
 * (rentals are online, decision P1/P2), `super`, `openall`, a subscription or a purchase. [seqState] is the per-key sequence memory of the activations; [delegationSeq] (optional) the one of the delegations.
 */
class DelegatedVerifier(private val ring: KeyRing, private val revocations: RevocationState = RevocationState(), private val expect: Subject = Subject.TV,
                        private val seqState: SeqState = SeqState(), private val delegationSeq: SeqState? = null) {
    /** [delegation] is the accepted mandate whenever the mandate itself passed, even if the activation was then refused (screens say « Point focal : <name> »). */
    class Outcome(val result: ActivationResult, val delegation: Delegation?)

    fun verify(line: String, device: Fingerprints, nowMs: Long): Outcome {
        val (dToken, aToken) = TicketedActivation.split(line) ?: return Outcome(rejected(Rejection.MALFORMED, "Activation avec ticket illisible"), null)
        val d = when (val r = Delegation.verify(dToken, ring, revocations, nowMs, delegationSeq)) {
            is DelegationResult.Refused -> return Outcome(rejected(map(r.reason), r.message), null)
            is DelegationResult.Accepted -> r.delegation
        }
        val env = Envelope.decode(aToken) ?: return Outcome(rejected(Rejection.MALFORMED, "Activation illisible"), d)
        if (env.keyId != d.agent) return Outcome(rejected(Rejection.KEY_NOT_ALLOWED, "L'activation n'est pas signée par le point focal de ce mandat"), d)
        val scratch = SeqState(seqState.snapshot())          // the sequence is recorded only when the limits of the mandate are respected too
        val result = ActivationVerifier(ring.withDelegated(listOf(d.agentKey())), revocations = revocations, expect = expect, seqState = scratch).verify(aToken, device, nowMs)
        if (result !is ActivationResult.Accepted) return Outcome(result, d)
        refusal(d, result.activation)?.let { return Outcome(rejected(Rejection.KEY_NOT_ALLOWED, "Le point focal n'est pas autorisé à délivrer ceci : $it"), d) }
        seqState.record(result.activation.keyId, result.activation.seq)
        return Outcome(result, d)
    }

    /** What the activation carries that the mandate does not allow (French, short), or null. */
    private fun refusal(d: Delegation, a: Activation): String? {
        val day = 24L * 3600 * 1000
        for (r in a.rights) when (r) {
            is Right.Usage -> {
                val days = (r.endsAt - r.startsAt + day - 1) / day
                if (a.kind == ActivationKind.PRODUCTION && days > d.maxKeyDays) return "une clé de plus de ${d.maxKeyDays} jours"
                if (a.kind == ActivationKind.TRIAL && days > ActivationPolicy.TRIAL_MAX_DAYS) return "un essai de plus de ${ActivationPolicy.TRIAL_MAX_DAYS} jours"
            }
            is Right.Rental -> return "une location (les locations se font en ligne)"
            is Right.Super -> return "le droit super"
            is Right.OpenAll -> return "« tout ouvert »"
            is Right.Subscription -> return "un abonnement"
            is Right.Purchase -> return "un achat définitif"
            is Right.Unknown -> return "un droit inconnu"
        }
        if (a.kind == ActivationKind.PRODUCTION && a.rights.none { it is Right.Usage }) return "une clé de production sans durée (illimitée)"
        return null
    }

    private fun rejected(r: Rejection, m: String) = ActivationResult.Rejected(r, m)

    private fun map(r: DelegationRefusal) = when (r) {
        DelegationRefusal.MALFORMED -> Rejection.MALFORMED
        DelegationRefusal.UNKNOWN_TYPE -> Rejection.UNKNOWN_TYPE
        DelegationRefusal.UNKNOWN_KEY -> Rejection.UNKNOWN_KEY
        DelegationRefusal.REVOKED_KEY -> Rejection.REVOKED_KEY
        DelegationRefusal.BAD_SIGNATURE -> Rejection.BAD_SIGNATURE
        DelegationRefusal.KEY_NOT_ALLOWED -> Rejection.KEY_NOT_ALLOWED
        DelegationRefusal.BAD_DELEGATION -> Rejection.BAD_RIGHTS
        DelegationRefusal.STALE_SEQUENCE -> Rejection.STALE_SEQUENCE
        DelegationRefusal.NOT_YET_VALID -> Rejection.NOT_YET_VALID
        DelegationRefusal.WINDOW_CLOSED -> Rejection.WINDOW_CLOSED
    }
}
