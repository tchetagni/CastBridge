package castbridge.core.lots

import castbridge.core.owner.Activation
import castbridge.core.owner.TvGate

/**
 * The lots a LASTING right covers on the TV (purchase, active subscription, `tout` / open-all / super), rentals left out: what the sweep must not delete at the end of a rental
 * (a lot bought during its rental stays). With a [BundleCatalog] the bundles are resolved to lots ([EditionPolicy.allowedFull]). The TV currently has NO bundle catalogue
 * (the catalogue lives on the phone and the server), so without one only what needs no catalogue is resolvable: the owner grants' single lots, and EVERY held lot when the
 * account owns `tout` (purchased, subscribed or open-all). A lot covered only by a named bundle cannot be resolved here and is NOT kept (safe for the owner: the delivery
 * installs the normal copy of a purchased lot, see [RentalLedger.release]).
 */
object OwnedLots {
    fun of(activations: List<Activation>, nowMs: Long, held: Set<LotId>, catalog: BundleCatalog? = null): Set<LotId> {
        val access = TvGate.evaluate(activations, emptyList(), nowMs).access          // no rentals given: only the lasting rights count
        val resolved = if (catalog != null) EditionPolicy.allowedFull(access, catalog) else access.extraLots
        return if (Right.ALL_BUNDLE in access.granted) held + resolved else resolved
    }
}
