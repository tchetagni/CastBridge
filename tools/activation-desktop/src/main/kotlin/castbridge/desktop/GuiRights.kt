package castbridge.desktop

import castbridge.core.owner.ActivationPolicy
import castbridge.core.owner.IssueException

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
