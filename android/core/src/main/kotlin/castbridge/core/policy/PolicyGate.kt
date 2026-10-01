package castbridge.core.policy

import castbridge.core.owner.GateState

/**
 * How the policy state touches the app gate. A suspended or revoked licence brings the app back to the LOCKED state ([GateState.Locked]: the activation surface only, see
 * `FeatureGate`) and NOTHING else: no file, no library entry, no setting is deleted (the engine has no deletion at all), and `license.activate` (or a fresh activation) brings
 * everything back as it was. When the activation requirement is off ([GateState.NotRequired]) or an existing install is in its grace period, a policy suspension changes nothing.
 */
object PolicyGate {
    /** [licenses] = the licence ids of the activations installed on this device (`trial` for a trial key). Locked only if EVERY one of them is suspended/revoked. */
    fun effective(base: GateState, policy: PolicyState, licenses: Set<String>): GateState =
        if (base is GateState.Activated && licenses.isNotEmpty() && licenses.all { policy.mode(it) != LicenseMode.ACTIVE }) GateState.Locked else base

    /** An extension only ever LATE the end of a right, never shortens one and never grants a right that does not exist. */
    fun effectiveEnd(license: String, endMs: Long, policy: PolicyState): Long = maxOf(endMs, policy.extensions[license] ?: 0L).takeIf { endMs > 0 } ?: endMs

    /** Should the app ask for fresh rights now (an order said so and it has not done it yet)? Compare with the version at which the app last refreshed. */
    fun refreshWanted(policy: PolicyState, lastRefreshVersion: Long) = policy.refreshRequestedAtVersion > lastRefreshVersion

    /** Lots that must be offered/kept, never the user's own files: a retired lot is removed from the LOT STORE by the existing lot logic. */
    fun lotRetired(policy: PolicyState, lotKey: String) = lotKey in policy.retired
}
