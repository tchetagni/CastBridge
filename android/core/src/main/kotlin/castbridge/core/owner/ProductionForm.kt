package castbridge.core.owner

import castbridge.core.lots.BundleCatalog
import castbridge.core.lots.RentalDurations
import castbridge.core.lots.Right
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The owner console's « Activer » form as a pure model (options -> rights), so the Compose screen stays thin and the policies are tested:
 *  - key duration is a CHOICE ([UNLIMITED] = no `usage` right, presets, or [OTHER] + a number); a trial always has a duration, SUPER_UNLIMITED none;
 *  - purchases, subscriptions and rentals are chosen from the server's bundle catalogue; the rental duration is the server's, never typed ([RentalDurations]).
 */
data class ProductionForm(
    val production: Boolean = false,
    val superUnlimited: Boolean = false,
    /** null = « Illimitée » (production only); a preset; or [OTHER]. */
    val durationChoice: Int? = ActivationPolicy.TRIAL_DEFAULT_DAYS,
    val otherDays: String = "",
    val license: String = "",
    /** Product id of the purchases and the subscription (their bundles come from the catalogue). */
    val product: String = DEFAULT_PRODUCT,
    val purchaseBundles: Set<String> = emptySet(),
    val subscriptionBundles: Set<String> = emptySet(),
    val subscriptionEnd: String = "",
    val rentalBundles: Set<String> = emptySet(),
    val openProduct: String = "",
    val openDays: String = "30",
    val catalog: BundleCatalog? = null,
) {
    /** What the console must sign: the rights, the rentals (to issue with RentalIssuing) and the usage ceiling in days (null = none). */
    class Plan(val rights: List<Right>, val rentals: List<castbridge.core.owner.RentalSpec>, val usageDays: Int?)

    /** Effective duration of the key in days, or null for none; throws [IssueException] (French). */
    fun usageDays(): Int? {
        if (production && superUnlimited) return null                                     // permanent: the choice is hidden and ignored
        val days: Int? = when {
            durationChoice == null -> if (production) null else throw IssueException("Un essai a toujours une durée (1 à ${ActivationPolicy.TRIAL_MAX_DAYS} jours)")
            durationChoice == OTHER -> otherDays.trim().toIntOrNull() ?: throw IssueException("Durée de la clé : un nombre de jours")
            else -> durationChoice
        }
        if (days != null) {
            val max = if (production) ActivationPolicy.PRODUCTION_MAX_DAYS else ActivationPolicy.TRIAL_MAX_DAYS
            if (days !in 1..max) throw IssueException("Durée de la clé : 1 à $max jours")
        }
        return days
    }

    fun build(now: Long): Plan {
        val day = 24L * 3600 * 1000; val start = now / day * day
        val days = usageDays()
        val rights = ArrayList<Right>(); var rentals = emptyList<castbridge.core.owner.RentalSpec>()
        if (production) {
            if (purchaseBundles.isNotEmpty()) rights += Right.Purchase(checkProduct(), checked(purchaseBundles), now)
            if (subscriptionBundles.isNotEmpty()) {
                val end = runCatching { LocalDate.parse(subscriptionEnd.trim()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull()
                    ?: throw IssueException("Abonnement : date de fin invalide (AAAA-MM-JJ)")
                if (end <= start) throw IssueException("Abonnement : la date de fin doit être dans le futur")
                rights += Right.Subscription(checkProduct(), checked(subscriptionBundles), start, end, 7 * day, false)
            }
            if (rentalBundles.isNotEmpty()) {
                val c = catalog ?: throw IssueException(NO_CATALOG)
                rentals = try { RentalDurations.specsFor(c, rentalBundles) } catch (e: IllegalArgumentException) { throw IssueException(e.message ?: "Location refusée") }
            }
            if (openProduct.isNotBlank()) {
                val d = openDays.toIntOrNull() ?: throw IssueException("« Tout ouvert » : nombre de jours")
                rights += Right.OpenAll(openProduct.trim(), start, start + d * day)
            }
            if (superUnlimited) rights += Right.Super("super-illimite", now)
        }
        return Plan(rights, rentals, days)
    }

    private fun checkProduct(): String = product.trim().also { if (!Activation.ID.matches(it)) throw IssueException("Produit : identifiant invalide") }

    private fun checked(ids: Set<String>): List<String> {
        val c = catalog ?: throw IssueException(NO_CATALOG)
        ids.forEach { if (c.find(it) == null) throw IssueException("Bouquet inconnu du catalogue : $it") }
        return ids.sorted()
    }

    companion object {
        const val OTHER = -1
        const val DEFAULT_PRODUCT = "castbridge"
        val PRESETS = listOf(30, 60, 62, 90, 180, 300, 365)
        const val NO_CATALOG = "Importez le catalogue du serveur"

        /** Label of a duration option for the chips. */
        fun label(choice: Int?): String = when (choice) { null -> "Illimitée"; OTHER -> "Autre…"; else -> "$choice jours" }

        /** Options offered: a trial has no « Illimitée ». */
        fun options(production: Boolean): List<Int?> = (if (production) listOf<Int?>(null) else emptyList()) + PRESETS + OTHER

        /** Switching Essai/Production: production starts « Illimitée », a trial cannot stay so (default 30). */
        fun withProduction(f: ProductionForm, production: Boolean): ProductionForm =
            if (f.production == production) f else f.copy(production = production, durationChoice = if (production) null else (f.durationChoice ?: ActivationPolicy.TRIAL_DEFAULT_DAYS))

        /** Rental days shown/issued for a bundle: the server's, else the owner's default ([RentalDurations.DEFAULT_DAYS]). */
        fun rentalDaysOf(b: castbridge.core.lots.Bundle) = if (b.rentalDays > 0) b.rentalDays else RentalDurations.DEFAULT_DAYS

        /** Line shown for a bundle in a picker; rentals show the fixed duration (never typed). */
        fun bundleLine(b: castbridge.core.lots.Bundle, forRental: Boolean): String =
            (b.title.ifBlank { b.id } + " (${b.id}, ${b.type})") +
                if (forRental) (if (b.rentalDays > 0) " : ${b.rentalDays} jours (fixé par le serveur)" else " : ${RentalDurations.DEFAULT_DAYS} jours (par défaut)") else ""
    }
}
