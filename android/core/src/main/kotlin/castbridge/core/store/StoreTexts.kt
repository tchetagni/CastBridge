package castbridge.core.store

import castbridge.core.lots.LotStore

/**
 * Toutes les phrases françaises de la Boutique (w17-02, conception § 2.5) : le téléphone (CastBridge) et la TV (CastBridge-TV) lisent les
 * mêmes textes, aucun écran n'en écrit. Aucun prix, aucune mention technique. Trois familles : [BUTTONS] (étiquettes de bouton),
 * [SHORT] (phrases courtes de la TV, sans point final) et [SENTENCES] (phrases complètes, avec ponctuation finale).
 */
object StoreTexts {
    // Boutons
    const val RENT_FREE = "Louer gratuitement"
    const val RENT_TV = "Louer"
    const val EXTEND = "Prolonger"
    const val RERENT = "Relouer"

    // Étiquettes et lignes courtes
    const val BADGE_FREE = "Gratuit"
    const val SAMPLE_PHONE = "Sur la TV : échantillon"
    const val SAMPLE_TV = "Échantillon"
    const val NOT_ON_TV_PHONE = "Pas sur la TV"
    const val NOT_ON_TV_TV = "À envoyer depuis le téléphone"
    const val ON_TV_PHONE = "Sur la TV"
    const val ON_TV_TV = "Sur cette TV"
    const val FREE_ON_TV = "Gratuit · sur cette TV"
    const val FREE_ASK_PHONE = "Gratuit · demandez-le au téléphone"
    const val SENDING_PHONE = "Envoi en cours"
    const val WAITING_PHONE = "En attente de la TV"
    const val AVAILABLE_TO_RENT = "À louer"
    const val UNLIMITED = "Droit illimité"

    // Phrases courtes de la TV (états bloqués)
    const val KID_TV = "Demandez à un parent"
    const val FREE_TV = "Gratuit"
    const val TRIAL_TV = "Version complète nécessaire · Passer en production"
    const val CATALOG_ABSENT_TV = "Boutique vide : rapprochez le téléphone"

    // Phrases complètes (états bloqués du téléphone, bandeaux)
    const val NO_TV_PHONE = "Ajoutez d'abord votre TV (Accueil › Ajouter ma TV)."
    const val TV_UNREACHABLE_PHONE = "La TV n'est pas à portée : la demande partira dès qu'elle sera allumée à côté du téléphone."
    const val TRIAL_PHONE = "Version d'essai : les locations demandent une clé de production. Voyez votre point focal."
    const val KID_PHONE = "Un profil enfant est actif sur la TV : demandez à un parent."
    const val FREE_NOTHING_TO_RENT = "Gratuit : rien à louer."
    const val UNKNOWN_FAMILY = "Cet article n'est pas louable pour le moment."
    const val PILOT_ENDED = "Le test gratuit est terminé ; tarifs bientôt disponibles."
    const val CATALOG_ABSENT_PHONE = "Connectez le téléphone à Internet puis actualisez la Boutique."
    const val OLD_CATALOG = "Ce catalogue date de plus d'un mois : connectez le téléphone à Internet pour l'actualiser."
    const val ENDED_NO_DATE = "Location terminée · Relouer ?"

    val BUTTONS = listOf(RENT_FREE, RENT_TV, EXTEND, RERENT)
    val SHORT = listOf(BADGE_FREE, SAMPLE_PHONE, SAMPLE_TV, NOT_ON_TV_PHONE, NOT_ON_TV_TV, ON_TV_PHONE, ON_TV_TV, FREE_ON_TV, FREE_ASK_PHONE, SENDING_PHONE,
        WAITING_PHONE, AVAILABLE_TO_RENT, UNLIMITED, KID_TV, FREE_TV, TRIAL_TV, CATALOG_ABSENT_TV)
    val SENTENCES = listOf(NO_TV_PHONE, TV_UNREACHABLE_PHONE, TRIAL_PHONE, KID_PHONE, FREE_NOTHING_TO_RENT, UNKNOWN_FAMILY, PILOT_ENDED, CATALOG_ABSENT_PHONE,
        OLD_CATALOG)

    // Phrases à paramètres
    fun quotaPhone(n: Int) = "$n locations en cours sur cette TV : attendez la fin de l'une d'elles."
    fun quotaTv(n: Int) = "$n locations en cours"
    fun alreadyRented(title: String, left: String) = "$title est déjà loué ($left) : prolongez-le, ou attendez la fin pour changer d'unité."
    fun spacePhone(missingBytes: Long) = "Place insuffisante : il manque ${LotStore.mo(missingBytes)} sur la TV."
    fun spaceTv(missingBytes: Long) = "Place insuffisante sur la TV (${LotStore.mo(missingBytes)})"
    fun pilotBanner(dayMonth: String) = "Test gratuit jusqu'au $dayMonth : toutes les locations sont offertes pendant le test."
    fun endedOn(dayMonth: String) = "Location terminée le $dayMonth · Relouer ?"
    fun pending(dayMonth: String) = "Demande en cours ($dayMonth)"

    /** « Il vous reste 5 h 20 d'utilisation » (minutes d'utilisation restantes). */
    fun usageLeft(minutes: Long): String {
        val m = maxOf(0L, minutes)
        val left = if (m >= 60) "${m / 60} h" + (if (m % 60 != 0L) " %02d".format(m % 60) else "") else "$m min"
        return "Il vous reste $left d'utilisation"
    }

    /** Durée courte pour « est déjà loué (7 jours) » : jours, sinon heures, sinon minutes. */
    fun shortLeft(ms: Long): String {
        val d = ms / 86_400_000L; val h = ms / 3_600_000L; val m = maxOf(1L, ms / 60_000L)
        return when { d >= 2 -> "$d jours"; d == 1L -> "1 jour"; h >= 1 -> "$h h"; else -> "$m min" }
    }

    /** « Leçons + exercices · 2 lots · 4,2 Mo ». */
    fun content(features: List<String>, lots: Int, bytes: Long): String {
        val names = features.map { when (it) { "learn" -> "leçons"; "quiz" -> "exercices"; "langues" -> "cours de langue"; "oeuvres" -> "œuvres"; else -> it } }
            .joinToString(" + ").replaceFirstChar { it.uppercase() }
        return listOf(names, if (lots == 1) "1 lot" else "$lots lots", LotStore.mo(bytes)).filter { it.isNotEmpty() }.joinToString(" · ")
    }
}
