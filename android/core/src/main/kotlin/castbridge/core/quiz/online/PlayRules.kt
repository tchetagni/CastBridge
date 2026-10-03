package castbridge.core.quiz.online

import castbridge.core.owner.TvAccess

/** Édition d'un hôte, telle que le SERVICE l'a évaluée (jamais lue dans un ticket). */
enum class HostEdition { PROD, TRIAL, GRACE, NONE }

/**
 * Le tableau commercial du Quiz en ligne en code (DESIGN-W20 § 2.5, w20-04), pur : sans horloge, sans réseau, sans argent réel. Une seule vérité pour le service
 * (qui l'applique à la création d'une salle) et pour les deux applications (qui expliquent le refus). Règles :
 * créer une salle Internet = TV de production (ou en grâce) ; rejoindre est gratuit pour tout téléphone ; l'essai ne crée que des salles privées, 3 parties par jour,
 * non classées, avec les questions libres ; les questions réservées ne sont servies que dans la salle d'un hôte qui y a droit, et seulement pour les lots qu'il couvre ;
 * un salon public ne sert jamais de réservée.
 */
object PlayRules {
    const val TRIAL_GAMES_PER_DAY = 3
    const val TRIAL_MAX_PLAYERS = 8
    /** Un droit « tout » (achat, abonnement, tout ouvert, super) couvre tous les lots. */
    const val ALL = "tout"

    /** Les neuf lignes du tableau. */
    enum class Actor { TV_PROD, TV_PROD_RENTAL, TV_TRIAL, TV_GRACE, TV_CLOCK_DOUBT, TV_LOCKED, PHONE_APP, PHONE_WEB, PUBLIC_LOBBY }

    /** [allowed] ; sinon [reason] (code stable) et [message] (français, pour l'écran). */
    class Verdict(val allowed: Boolean, val reason: PlayReason? = null, val message: String = "") {
        companion object {
            val OK = Verdict(true)
            fun no(message: String) = Verdict(false, PlayReason.PLAY_SCOPE_FORBIDDEN, message)
        }
    }

    const val MSG_ACTIVATE = "Activez la TV pour créer une partie Internet"
    const val MSG_TRIAL_PRIVATE = "Version d'essai : les parties Internet sont privées. Passez en version complète pour ouvrir un salon public."
    const val MSG_TRIAL_DAILY = "Version d'essai : 3 parties Internet par jour. Revenez demain ou passez en version complète."
    const val MSG_PHONE_CANNOT_CREATE = "Seule une TV activée crée une partie Internet : rejoignez-en une avec son code."
    const val MSG_LOBBY_NO_HOST = "Les tables des salons publics sont ouvertes par CastBridge."
    const val MSG_CHILD = "Profil enfant : les parties sur Internet sont fermées par le contrôle parental."
    const val MSG_RENTAL_ENDED = "Location terminée : questions libres"

    fun actorOf(edition: HostEdition, hasCoveredScopes: Boolean, clockDoubt: Boolean = false): Actor = when {
        clockDoubt -> Actor.TV_CLOCK_DOUBT
        edition == HostEdition.NONE -> Actor.TV_LOCKED
        edition == HostEdition.TRIAL -> Actor.TV_TRIAL
        hasCoveredScopes -> Actor.TV_PROD_RENTAL           // production ou grâce AVEC droits : « selon droits »
        edition == HostEdition.GRACE -> Actor.TV_GRACE
        else -> Actor.TV_PROD
    }

    /** Peut-il créer une salle Internet ? [gamesToday] = parties déjà ouvertes aujourd'hui avec cette activation (essai seulement). */
    fun canCreate(a: Actor, publicRoom: Boolean = false, gamesToday: Int = 0): Verdict = when (a) {
        Actor.TV_PROD, Actor.TV_PROD_RENTAL, Actor.TV_GRACE -> Verdict.OK
        Actor.TV_TRIAL -> when {
            publicRoom -> Verdict.no(MSG_TRIAL_PRIVATE)
            gamesToday >= TRIAL_GAMES_PER_DAY -> Verdict.no(MSG_TRIAL_DAILY)
            else -> Verdict.OK
        }
        Actor.TV_CLOCK_DOUBT -> Verdict.no(TvAccess.CHECK_CLOCK_LABEL)
        Actor.TV_LOCKED -> Verdict.no(MSG_ACTIVATE)
        Actor.PHONE_APP, Actor.PHONE_WEB -> Verdict.no(MSG_PHONE_CANNOT_CREATE)
        Actor.PUBLIC_LOBBY -> Verdict.no(MSG_LOBBY_NO_HOST)
    }

    /** Rejoindre est gratuit pour tout joueur ; la TV n'est pas un joueur. */
    fun canJoin(a: Actor): Boolean = a == Actor.PHONE_APP || a == Actor.PHONE_WEB || a == Actor.PUBLIC_LOBBY

    /** Les questions réservées ne sont possibles que dans la salle d'un hôte titulaire d'une location ou d'un achat. */
    fun reservedAllowed(a: Actor): Boolean = a == Actor.TV_PROD_RENTAL

    /** Lot [scope] couvert par [covered] ? (« tout » couvre tous les lots). */
    fun covers(covered: Set<String>, scope: String): Boolean = ALL in covered || scope in covered

    /** Les réservées du lot [scope] sont-elles servies dans cette salle ? */
    fun reservedScopeAllowed(a: Actor, covered: Set<String>, scope: String): Boolean = reservedAllowed(a) && covers(covered, scope)

    /** Une partie compte-t-elle pour les classements publics ? L'essai : jamais. */
    fun isRanked(a: Actor, @Suppress("UNUSED_PARAMETER") publicRoom: Boolean): Boolean = when (a) {
        Actor.TV_PROD, Actor.TV_PROD_RENTAL, Actor.TV_GRACE, Actor.PHONE_APP, Actor.PHONE_WEB, Actor.PUBLIC_LOBBY -> true
        Actor.TV_TRIAL, Actor.TV_CLOCK_DOUBT, Actor.TV_LOCKED -> false
    }

    /** Le profil actif de la TV peut-il ouvrir Internet ? Un profil enfant : jamais (la TV le vérifie avant de demander un ticket ; le serveur ne connaît pas les profils). */
    fun internetForProfile(childProfile: Boolean): Verdict = if (childProfile) Verdict.no(MSG_CHILD) else Verdict.OK
}
