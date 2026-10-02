package castbridge.desktop

import castbridge.core.lots.BundleCatalog
import castbridge.core.lots.RentalDurations
import castbridge.core.lots.Right
import castbridge.core.owner.ActivationKind
import castbridge.core.owner.ActivationPolicy
import castbridge.core.owner.IssueException
import castbridge.core.owner.RentalSpec
import castbridge.core.owner.RightsSyntax
import java.text.SimpleDateFormat
import java.util.TimeZone

/** Duration of the activation key chosen in the window (no Swing here: tested). Errors are French sentences for the screen. */
object KeyDuration {
    const val UNLIMITED = "Illimitée"
    const val OTHER = "Autre…"
    val CHOICES = listOf(UNLIMITED, "30", "60", "62", "90", "180", "300", "365", OTHER)

    /** Production: null = unlimited, else 1 to [ActivationPolicy.PRODUCTION_MAX_DAYS]. [other] is read only for « Autre… ». A super key (SUPER_UNLIMITED) never has a duration. */
    fun production(choice: String, other: String, superKey: Boolean = false): Int? {
        if (choice == UNLIMITED) return null
        if (superKey) throw IssueException("SUPER_UNLIMITED est permanent : aucune durée (choisissez « $UNLIMITED »)")
        val d = (if (choice == OTHER) other.trim() else choice).toIntOrNull() ?: throw IssueException("Durée de la clé : un nombre de jours (1 à ${ActivationPolicy.PRODUCTION_MAX_DAYS})")
        if (d !in 1..ActivationPolicy.PRODUCTION_MAX_DAYS) throw IssueException("Durée de la clé : de 1 à ${ActivationPolicy.PRODUCTION_MAX_DAYS} jours")
        return d
    }

    /** Trial: always a duration, 1 to [ActivationPolicy.TRIAL_MAX_DAYS] (never unlimited). */
    fun trial(text: String): Int {
        val t = text.trim()
        if (t.lowercase().startsWith("illimit")) throw IssueException("Un essai a toujours une durée (1 à ${ActivationPolicy.TRIAL_MAX_DAYS} jours)")
        val d = t.toIntOrNull() ?: throw IssueException("Durée de l'essai : un nombre de jours (1 à ${ActivationPolicy.TRIAL_MAX_DAYS})")
        if (d !in 1..ActivationPolicy.TRIAL_MAX_DAYS) throw IssueException("Durée de l'essai : de 1 à ${ActivationPolicy.TRIAL_MAX_DAYS} jours")
        return d
    }
}

/** What the window's checklist and its « avancé » box ask for; becomes the rights and the rentals of the [castbridge.core.owner.IssueSpec]. */
class RightsPlan(val rights: List<Right>, val rentals: List<RentalSpec>)

object GuiRights {
    private const val DAY_MS = 24L * 3600 * 1000

    /** Days from [now] to the end of the day [endDate] (yyyy-MM-dd, UTC), rounded up; at least 1. */
    fun daysUntil(endDate: String, now: Long): Int {
        val end = runCatching { SimpleDateFormat("yyyy-MM-dd").apply { isLenient = false; timeZone = TimeZone.getTimeZone("UTC") }.parse(endDate.trim()).time + DAY_MS }
            .getOrElse { throw IssueException("Abonnement : date de fin « $endDate » invalide (AAAA-MM-JJ)") }
        val days = Math.ceil((end - now).toDouble() / DAY_MS).toInt()
        if (days < 1) throw IssueException("Abonnement : la date de fin « $endDate » est déjà passée")
        return days
    }

    /**
     * [purchases]: bundles bought for good (product `ach-<bundle>`); [subscriptions]: bundle to end date (product `abo-<bundle>`); [rentals]: bundles rented for the duration FIXED BY THE SERVER
     * (product `loc-<bundle>`, [RentalDurations.specsFor]; needs [catalog]); [advanced]: free lines for the other rights (`tout-ouvert`, `droit`, `achat`, `abonnement`); a typed `location` line is checked
     * exactly through [RentalDurations.check] and REFUSED when no catalogue is loaded.
     */
    fun plan(purchases: Collection<String>, subscriptions: Map<String, String>, rentals: Collection<String>, advanced: String, catalog: BundleCatalog?, now: Long): RightsPlan {
        val rights = ArrayList<Right>()
        for (b in purchases.distinct().sorted()) rights += RightsSyntax.purchase("ach-$b=$b", now)
        for ((b, date) in subscriptions.toSortedMap()) rights += RightsSyntax.subscription("abo-$b=$b:${daysUntil(date, now)}", now)
        val specs = ArrayList<RentalSpec>()
        if (rentals.isNotEmpty()) {
            if (catalog == null) throw IssueException("Location : chargez le catalogue du serveur (bouton « Catalogue… ») ; la durée est fixée par le serveur")
            specs += try { RentalDurations.specsFor(catalog, rentals) } catch (e: IllegalArgumentException) { throw IssueException(e.message ?: "Location refusée") }
        }
        val other = ArrayList<String>()
        for (raw in advanced.lines()) {
            val l = raw.trim(); if (l.isEmpty() || l.startsWith("#")) continue
            val kw = l.substringBefore(' ').lowercase()
            when {
                kw == "location" -> {
                    val spec = RightsSyntax.rental(l.substringAfter(' ', "").trim(), null)
                    if (catalog == null) throw IssueException("Location : chargez le catalogue du serveur (bouton « Catalogue… ») ; la durée est fixée par le serveur")
                    RentalDurations.check(spec, catalog)?.let { throw IssueException("Location refusée : $it") }
                    specs += spec
                }
                l.startsWith("rental|") -> throw IssueException("Une location se saisit avec une ligne « location produit=bouquet:JOURS » ou dans la liste du catalogue, jamais en ligne brute")
                else -> other += l
            }
        }
        rights += RightsSyntax.parseBox(other.joinToString("\n"), now)
        val dup = specs.groupBy { it.productId }.filterValues { it.size > 1 }.keys.firstOrNull()
        if (dup != null) throw IssueException("Location : le produit « $dup » est demandé deux fois")
        return RightsPlan(rights, specs)
    }
}
