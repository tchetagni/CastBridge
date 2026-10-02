package castbridge.core.owner

import castbridge.core.lots.Right

/**
 * The owner console's « Activer » form as a pure model (options -> rights), so the Compose screen stays thin and the policies are tested:
 *  - a PRODUCTION key only has a DURATION ([UNLIMITED] = no `usage` right, presets, or [OTHER] + a number) and the SUPER_UNLIMITED switch; the licence is generated ([LicenseIds]);
 *    it carries no content right (rentals are the server's job for activated TVs, docs/RENTAL-LOTS.md);
 *  - a trial always has a duration (default 30, no unlimited).
 */
data class ProductionForm(
    val production: Boolean = false,
    val superUnlimited: Boolean = false,
    /** null = « Illimitée » (production only); a preset; or [OTHER]. */
    val durationChoice: Int? = ActivationPolicy.TRIAL_DEFAULT_DAYS,
    val otherDays: String = "",
) {
    /** What the console must sign: the rights and the usage ceiling in days (null = none). */
    class Plan(val rights: List<Right>, val usageDays: Int?)

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
        val days = usageDays()
        val rights = ArrayList<Right>()
        if (production && superUnlimited) rights += Right.Super("super-illimite", now)
        return Plan(rights, days)
    }

    companion object {
        const val OTHER = -1
        val PRESETS = listOf(30, 60, 62, 90, 180, 300, 365)

        /** Label of a duration option for the chips. */
        fun label(choice: Int?): String = when (choice) { null -> "Illimitée"; OTHER -> "Autre…"; else -> "$choice jours" }

        /** Options offered: a trial has no « Illimitée ». */
        fun options(production: Boolean): List<Int?> = (if (production) listOf<Int?>(null) else emptyList()) + PRESETS + OTHER

        /** Switching Essai/Production: production starts « Illimitée », a trial cannot stay so (default 30). */
        fun withProduction(f: ProductionForm, production: Boolean): ProductionForm =
            if (f.production == production) f else f.copy(production = production, durationChoice = if (production) null else (f.durationChoice ?: ActivationPolicy.TRIAL_DEFAULT_DAYS))
    }
}
