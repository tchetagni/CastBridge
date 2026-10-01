package castbridge.core.lots

/**
 * The two families of lots (docs/LANGUES.md § 13): FREE lots (content derived from CC BY-SA sources: not encrypted, downloadable separately, never locked by a technical measure) and
 * RESERVED lots (original or public-domain content, encrypted per TV). Only RESERVED lots can be rented. A lot whose family is unknown is NOT rentable (fail closed).
 */
enum class LotFamily { FREE, RESERVED }

fun interface LotFamilies {
    fun of(id: LotId): LotFamily?

    companion object {
        /** Explicit lists (from the content manifests); anything in neither list is unknown, so refused. */
        fun explicit(free: Set<String>, reserved: Set<String>) = LotFamilies { id ->
            val k = LotNames.key(id)
            when { k in free -> LotFamily.FREE; k in reserved -> LotFamily.RESERVED; else -> null }
        }
    }
}

object RentalPolicy {
    /** Why [meta] cannot be rented (French), or null. Free lots, trial lots and lots of unknown family are refused. */
    fun refusal(meta: LotMeta, families: LotFamilies): String? = when {
        meta.edition == Edition.TRIAL -> "« ${meta.title} » est un échantillon gratuit : il ne se loue pas"
        families.of(meta.id) == LotFamily.FREE -> "« ${meta.title} » est un lot libre (CC BY-SA) : il reste libre et ne peut pas être loué"
        families.of(meta.id) == null -> "« ${meta.title} » : famille de lot inconnue, location refusée par précaution"
        else -> null
    }

    /**
     * The lots of the rented [bundleIds] that a rental really has to carry: the full lots of the bundles that are RESERVED and not already allowed by a purchase, a subscription or an
     * owner grant ([otherwise] = [EditionPolicy.allowedFull] of the access WITHOUT the rental). No double count: a lot already owned is simply not rented.
     */
    fun lotsToRent(bundleIds: Collection<String>, bundles: BundleCatalog, catalog: Collection<LotMeta>, families: LotFamilies, otherwise: Set<LotId>): List<LotMeta> {
        val wanted = bundles.lotsOf(bundleIds)
        return catalog.filter { it.edition == Edition.FULL && it.id in wanted && it.id !in otherwise && refusal(it, families) == null }
            .sortedWith(compareBy({ it.id.feature }, { it.id.scope }))
    }

    /** What a rental request would be refused for, per lot of the bundles (empty list = every lot is rentable). Used by the issuing tools before they sign. */
    fun refusals(bundleIds: Collection<String>, bundles: BundleCatalog, catalog: Collection<LotMeta>, families: LotFamilies): List<String> {
        val unknownBundles = bundleIds.filter { bundles.find(it) == null }.map { "bouquet inconnu : $it" }
        val byId = catalog.filter { it.edition == Edition.FULL }.associateBy { it.id }
        val lotRefusals = bundles.lotsOf(bundleIds).sortedWith(compareBy({ it.feature }, { it.scope })).mapNotNull { id ->
            val meta = byId[id]
            when {
                meta != null -> refusal(meta, families)
                families.of(id) == LotFamily.FREE -> "${LotNames.key(id)} est un lot libre (CC BY-SA) : il ne peut pas être loué"
                families.of(id) == null -> "${LotNames.key(id)} : famille de lot inconnue, location refusée par précaution"
                else -> null
            }
        }
        return unknownBundles + lotRefusals
    }

    /** Rentals only ADD: a lasting right is never reduced by a rental that ended, and a rental never counts for a bundle that is already owned or subscribed. */
    fun mergeAccess(access: Access, rentals: List<RentalStatus>): Access {
        val covered = access.purchased + access.subscribed
        val all = Right.ALL_BUNDLE in covered
        val rented = if (all) emptySet() else rentals.filter { it.usable }.flatMap { it.contract.bundleIds }.filter { it !in covered }.toSortedSet()
        return access.copy(rented = rented, rentals = rentals)
    }
}
