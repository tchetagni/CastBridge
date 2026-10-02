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
}
