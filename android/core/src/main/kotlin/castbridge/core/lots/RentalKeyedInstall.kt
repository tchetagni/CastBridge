package castbridge.core.lots

import castbridge.core.owner.Activation
import castbridge.core.owner.Fingerprints

/**
 * The production path of an installation: [RentalLedger.install] with the installation key as a NON-NULL parameter, so a caller (CastBridge-TV's rental hub) cannot forget it by leaving the
 * default `null`: the v1 box sunset ([RentalKeys.V1_BOX_SUNSET_MS]) and the v2 boxes both depend on it.
 */
fun RentalLedger.installKeyed(activation: Activation, all: List<Activation>, device: Fingerprints, vault: RentalVault, key: InstallKey): Map<String, String> =
    install(activation, all, device, vault, key)

/** The French lines of an install result that deserve the owner's attention (the routine ones, « clé installée » and the like, are not repeated). */
object RentalNotes {
    private val ROUTINE = setOf("clé installée", "clé déjà en place", "terminée")

    fun of(result: Map<String, String>): List<String> =
        result.filterValues { it !in ROUTINE }.map { (contract, what) -> "Location ${contract.substringBefore('@')} : $what" }
}
