package castbridge.core.lots

import castbridge.core.owner.RentalSpec

/**
 * The duration of a rental is FIXED BY THE SERVER, per bundle (`rentalDays` in the bundle catalogue, [Bundle.rentalDays]); the owner's tools never choose it and never accept another one
 * (policy of the owner: « exacte »). A bundle without `rentalDays` gets the owner's DEFAULT of [DEFAULT_DAYS] days. One rental right is issued per bundle, product `loc-<bundle>`,
 * so every bundle keeps its own duration, its own key and its own end.
 */
object RentalDurations {
    const val PREFIX = "loc-"

    /** The duration of a rental when the server's catalogue fixes none for the bundle (owner's decision: 30 days). */
    const val DEFAULT_DAYS = 30

    /** Product id of the rental of [bundleId] (`loc-<bundle>`), or null when it would not be a valid id. */
    fun productOf(bundleId: String): String? = (PREFIX + bundleId).takeIf { Regex("^[a-z0-9][a-z0-9-]{0,63}$").matches(it) }

    /** The server's duration of [bundleId] ([DEFAULT_DAYS] when it fixes none), or a French reason (unknown bundle, out of bounds). */
    fun daysOf(catalog: BundleCatalog, bundleId: String): Result<Int> {
        val b = catalog.find(bundleId) ?: return Result.failure(IllegalArgumentException("bouquet inconnu : $bundleId"))
        if (b.rentalDays <= 0) return Result.success(DEFAULT_DAYS)
        if (b.rentalDays > RentalLines.MAX_DAYS) return Result.failure(IllegalArgumentException("« ${b.title.ifBlank { b.id }} » : durée du serveur hors bornes (${b.rentalDays} jours)"))
        return Result.success(b.rentalDays)
    }

    /** One rental spec per bundle, with the server's exact duration. Throws IllegalArgumentException (French message) on the first refused bundle. */
    fun specsFor(catalog: BundleCatalog, bundleIds: Collection<String>): List<RentalSpec> {
        require(bundleIds.isNotEmpty()) { "Location : au moins un bouquet" }
        return bundleIds.distinct().sorted().map { id ->
            val days = daysOf(catalog, id).getOrThrow()
            RentalSpec(productOf(id) ?: throw IllegalArgumentException("bouquet « $id » : identifiant trop long pour une location"), listOf(id), days)
        }
    }

    /** French reason when [spec] is not exactly what the server fixed for its bundle(s), or null. A spec on several bundles must agree with every one of them. */
    fun check(spec: RentalSpec, catalog: BundleCatalog): String? {
        for (id in spec.bundleIds) {
            val days = daysOf(catalog, id).getOrElse { return it.message }
            if (spec.days != days) return "« ${catalog.find(id)?.title?.ifBlank { id } ?: id} » : la durée est fixée par le serveur à $days jour(s), pas ${spec.days}"
        }
        return null
    }

    /**
     * Pilot rule (W16), the hook for [castbridge.core.owner.IssueSpec.rentalCheck]: French reason when [spec] is not something the pilot lets a person choose, or null. `userChosen = 0`:
     * the EXACT rule of [check], and no usage budget. Otherwise ONE bundle; days (no budget) from 1 to `min(maxDays, rentalDays of the bundle)`; hours (budget) whole hours up to 96 (the
     * engine's clamp, never above) with 1 to `hourly.validityDays` days of safety; no grace; 1 to `maxConcurrent` simultaneous. State-dependent rules (quota, contracts, window) are [PilotRules].
     */
    fun checkChosen(spec: RentalSpec, catalog: BundleCatalog, params: PilotParams): String? {
        // defence in depth: Langues / free bundles (type, id prefix, `freeBundles`) are refused here too, before any duration is looked at, in BOTH modes
        spec.bundleIds.forEach { id -> catalog.find(id)?.let { PilotRules.languagesRefusal(it, params) }?.let { return it } }
        if (!params.userChosen) return check(spec, catalog) ?: if (spec.maxUsageMinutes != 0) "la durée est fixée par le serveur : pas de plafond d'usage choisi" else null
        if (spec.bundleIds.size != 1) return "une location par bouquet : ${spec.bundleIds.size} bouquets demandés"
        val b = catalog.find(spec.bundleIds.single()) ?: return "bouquet inconnu : ${spec.bundleIds.single()}"
        if (spec.productId != productOf(b.id)) return "le produit d'une location est « loc-${b.id} », pas « ${spec.productId} »"      // adds to RentalPolicy.refusals (lot families), which the caller still runs
        if (spec.graceDays != 0) return "aucune tolérance n'est prévue dans une location du pilote"
        if (spec.maxConcurrent !in 1..params.maxConcurrent) return "locations simultanées : de 1 à ${params.maxConcurrent}"
        if (spec.maxUsageMinutes < 0) return "plafond d'usage invalide"
        if (spec.maxUsageMinutes > 0) {
            val cap = minOf(params.hourlyMaxUseHours, PilotParams.HARD_CAP_HOURS)
            if (spec.maxUsageMinutes > cap * 60) return "une location en heures d'utilisation ne dépasse pas $cap heures"
            if (spec.maxUsageMinutes % 60 != 0) return "les heures d'utilisation se comptent en heures entières"
            if (spec.days !in 1..params.hourlyValidityDays) return "borne de sûreté d'une location en heures : de 1 à ${params.hourlyValidityDays} jours (${spec.days})"
        } else {
            val cap = minOf(params.maxDays, if (b.rentalDays > 0) b.rentalDays else params.maxDays)
            if (spec.days !in 1..cap) return "« ${b.title.ifBlank { b.id }} » : une location en jours va de 1 à $cap jours (${spec.days})"
        }
        return null
    }
}
