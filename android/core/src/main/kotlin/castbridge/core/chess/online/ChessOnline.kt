package castbridge.core.chess.online

import castbridge.core.net.JsonLite
import castbridge.core.quiz.online.HostEdition
import castbridge.core.quiz.online.PlayRelay
import castbridge.core.quiz.online.PlayRules
import castbridge.core.quiz.online.StakeSpec
import castbridge.core.wallet.WalletCurrency
import castbridge.core.wallet.WalletView
import castbridge.core.wallet.ui.GamePolicyView

/**
 * Ce que le service dit de lui (`GET /play/.well-known/caps`, sans secret) : les échecs sont-ils ouverts, les mises acceptées, le Quiz misé arbitré. Remplace le drapeau `POST /api/chess/config?online=1` de la TV :
 * la capacité vient du service lui-même ; un service plus ancien (sans clé `chess`) dit « pas d'échecs », sans clé `quizStakes` il dit « pas de Quiz misé » (games-G5 : la TV ne propose alors que « Libre »).
 */
data class ChessServiceCaps(val chess: Boolean, val stakes: Boolean, val revocationsOff: Boolean = false, val quizStakes: Boolean = false) {
    companion object {
        /** null = réponse illisible (service injoignable ou page d'un proxy) ; un service sans `chess` rend `chess = false` (TV plus récente que le service). */
        fun parse(body: String?): ChessServiceCaps? {
            val m = runCatching { JsonLite.obj(body.orEmpty()) }.getOrNull() ?: return null
            if (m["name"] != "play-v1") return null
            val caps = (m["caps"] as? List<*>).orEmpty()
            return ChessServiceCaps(chess = m["chess"] == true || "chess" in caps, stakes = m["stakes"] == true || "stakes" in caps, revocationsOff = m["revocations"] == "off",
                quizStakes = m["quizStakes"] == true || "quizStakes" in caps)
        }
    }
}

/** La tuile « En ligne » de l'écran des échecs : rien, une raison (jamais un écran vide), ou ouverte. */
sealed class ChessOnlineTile {
    object Hidden : ChessOnlineTile() { override fun toString() = "Hidden" }
    data class Blocked(val reason: String) : ChessOnlineTile()
    object Available : ChessOnlineTile() { override fun toString() = "Available" }
}

/**
 * La porte de « Échecs › En ligne » : TV activée (essai, grâce ou production), Internet (direct ou par le téléphone), profil adulte, heure fiable, activation vérifiable par le service, service qui annonce
 * les échecs. Une TV qui n'est pas dans cet état n'ouvre AUCUNE connexion. PURE : le câblage Android ne fait que lui donner les faits.
 */
object ChessOnlineGate {
    const val MSG_NO_INTERNET = "Connexion Internet requise"
    const val MSG_SERVICE_DOWN = "Échecs en ligne : service indisponible"
    const val MSG_NOT_OPEN = "Les échecs en ligne ne sont pas encore ouverts sur le service CastBridge. Mettez CastBridge-TV à jour si le message persiste."
    const val MSG_ACTIVATION_FILE = "Échecs en ligne : activez la TV avec le fichier d'activation"
    const val MSG_CHILD = "Profil enfant : les parties sur Internet sont fermées par le contrôle parental."

    fun tile(flagOn: Boolean, edition: HostEdition, hasInternet: Boolean, childProfile: Boolean, clockDoubt: Boolean, caps: ChessServiceCaps?, serviceReachable: Boolean?, verifiableActivation: Boolean = true,
             relay: PlayRelay = PlayRelay.UNKNOWN): ChessOnlineTile = when {
        !flagOn -> ChessOnlineTile.Hidden
        edition == HostEdition.NONE -> ChessOnlineTile.Blocked(PlayRules.MSG_ACTIVATE)
        !verifiableActivation -> ChessOnlineTile.Blocked(MSG_ACTIVATION_FILE)
        clockDoubt -> ChessOnlineTile.Blocked(castbridge.core.owner.TvAccess.CHECK_CLOCK_LABEL)
        childProfile -> ChessOnlineTile.Blocked(MSG_CHILD)
        // relay-R1 : sans Internet, l'entrée reste proposée si un téléphone synchronisé peut ouvrir un tuyau (la TV le lui demandera à l'appui) ; sinon la raison est dite avec les mots du relais, comme le Quiz
        !hasInternet && relay == PlayRelay.POSSIBLE -> ChessOnlineTile.Available
        !hasInternet && relay == PlayRelay.NO_PHONE -> ChessOnlineTile.Blocked(castbridge.core.relay.RelayText.NO_PHONE)
        !hasInternet && relay == PlayRelay.OLD_PHONE -> ChessOnlineTile.Blocked(castbridge.core.relay.RelayText.OLD_PHONE)
        !hasInternet -> ChessOnlineTile.Blocked(MSG_NO_INTERNET)
        serviceReachable == false -> ChessOnlineTile.Blocked(MSG_SERVICE_DOWN)
        caps != null && !caps.chess -> ChessOnlineTile.Blocked(MSG_NOT_OPEN)
        else -> ChessOnlineTile.Available
    }

    /** Seule une tuile ouverte peut ouvrir une connexion sortante. */
    fun mayOpenNetwork(tile: ChessOnlineTile): Boolean = tile == ChessOnlineTile.Available
}

/** Les mises sont-elles permises à CETTE TV maintenant ? Sinon [FreeOnly] dit pourquoi (la partie libre reste ouverte). */
sealed class StakeAvailability {
    /** [ndem] / [mboko] : les paliers offerts (vide = cette monnaie n'est pas misable maintenant). */
    data class Allowed(val ndem: List<Long>, val mboko: List<Long>) : StakeAvailability()
    data class FreeOnly(val reason: String) : StakeAvailability()

    companion object {
        const val MSG_TRIAL = "Version d'essai : parties libres seulement, sans mise. Passez en version complète pour miser."
        const val MSG_SERVICE_OFF = "Mises suspendues sur le service : parties libres seulement."
        const val MSG_ACTIVATE = "Activez la TV pour miser."
        const val MSG_FROZEN = "Compte en vérification : contactez votre point focal. Parties libres seulement."
        const val MSG_GAME_OFF = "Mises aux échecs suspendues pour maintenance : parties libres seulement."
        const val MSG_NO_WALLET = "Portefeuille indisponible : parties libres seulement."
        /** Quiz misé (games-G5) : service ou serveur sans la capacité `quizStakes`, ou TV ancienne : seule « Libre » est proposée, avec cette ligne. */
        const val MSG_QUIZ_UPDATE = "Mises NDEM/MBOKO : mettez à jour"
        const val MSG_QUIZ_OFF = "Mises au Quiz suspendues pour maintenance : parties libres seulement."

        /**
         * [edition] : celle que la TV connaît (jamais une preuve : l'API et le service la relisent) ; [caps] : ce que le service annonce ; [walletKnown] : un instantané signé existe ; [stakesN] / [stakesM] / [frozen] :
         * les drapeaux de CET instantané ; [policy] : la politique du jeu lue de l'API (null = serveur plus ancien : l'échelle de repli du propriétaire).
         */
        fun of(edition: HostEdition, caps: ChessServiceCaps?, walletKnown: Boolean, stakesN: Boolean, stakesM: Boolean, frozen: Boolean, policy: GamePolicyView?): StakeAvailability =
            decide(edition, if (caps != null && !caps.stakes) MSG_SERVICE_OFF else null, walletKnown, stakesN, stakesM, frozen, policy, MSG_GAME_OFF)

        /**
         * Les mises du QUIZ en ligne (games-G5) : mêmes règles que les échecs, avec la capacité `quizStakes` du service (absente, ou service injoignable à la sonde : « Mises NDEM/MBOKO : mettez à jour », seule
         * « Libre » est proposée) et la politique du jeu `quiz` lue de l'API ([policyLoaded] : la politique a été lue ; si elle ne liste pas `quiz`, le serveur est plus ancien : même ligne).
         */
        fun ofQuiz(edition: HostEdition, caps: ChessServiceCaps?, walletKnown: Boolean, stakesN: Boolean, stakesM: Boolean, frozen: Boolean, policy: GamePolicyView?, policyLoaded: Boolean): StakeAvailability =
            decide(edition, if (caps == null || !caps.quizStakes || (policyLoaded && policy == null)) MSG_QUIZ_UPDATE else null, walletKnown, stakesN, stakesM, frozen, policy, MSG_QUIZ_OFF)

        /** [serviceBlock] : la raison, déjà écrite, pour laquelle le service ou le serveur ne sait pas miser à ce jeu (null = il sait) ; [gameOff] : la phrase quand la politique éteint le jeu. */
        private fun decide(edition: HostEdition, serviceBlock: String?, walletKnown: Boolean, stakesN: Boolean, stakesM: Boolean, frozen: Boolean, policy: GamePolicyView?, gameOff: String): StakeAvailability {
            if (edition == HostEdition.TRIAL) return FreeOnly(MSG_TRIAL)
            if (edition == HostEdition.NONE) return FreeOnly(MSG_ACTIVATE)
            if (serviceBlock != null) return FreeOnly(serviceBlock)
            if (!walletKnown) return FreeOnly(MSG_NO_WALLET)
            if (frozen) return FreeOnly(MSG_FROZEN)
            if (policy != null && !policy.enabled) return FreeOnly(gameOff)
            val ndem = if (stakesN) policy?.stakesNdem ?: ChessStakeScale.NDEM else emptyList()
            val mboko = if (stakesM) policy?.stakesMboko ?: ChessStakeScale.MBOKO else emptyList()
            return if (ndem.isEmpty() && mboko.isEmpty()) FreeOnly(MSG_SERVICE_OFF) else Allowed(ndem, mboko)
        }
    }
}

/**
 * L'écran « Créer une partie en ligne » : libre / mise, monnaie, montant (parmi l'échelle), solde, avertissement. Un modèle PUR piloté par ‹ › : l'écran Canvas ne décide rien. Le choix libre est TOUJOURS
 * permis ; une mise exige que la monnaie ait des paliers et que le solde connu les couvre.
 *
 * Partagé avec le QUIZ en ligne (games-G5, alias [StakeChoice]) : [maxSeats] > 1 ajoute le choix du nombre de sièges de CETTE TV qui misent (une mise par siège, payée par le compte de la TV ; le blocage vaut
 * `mise × sièges`), [shared] dit que la cagnotte se partage selon le classement (au lieu d'aller au gagnant d'un duel), [warning] est l'avertissement d'abandon propre au jeu. Aux échecs rien ne change
 * (un siège, cagnotte du gagnant, avertissement des échecs).
 */
class ChessStakeChoice(val availability: StakeAvailability, private val balanceNdem: Long?, private val balanceMboko: Long?, private val maxSeats: Int = 1, private val shared: Boolean = false,
                       private val abandonWarning: String = ChessOnlineTexts.ABANDON_WARNING) {
    enum class Mode(val label: String) { FREE("Libre (sans mise)"), NDEM("Mise en NDEM"), MBOKO("Mise en MBOKO") }

    var mode: Mode = Mode.FREE; private set
    private var index = 0

    /** Les modes proposés : « libre » d'abord, puis seulement les monnaies misables. */
    val modes: List<Mode> = buildList {
        add(Mode.FREE)
        (availability as? StakeAvailability.Allowed)?.let { a -> if (a.ndem.isNotEmpty()) add(Mode.NDEM); if (a.mboko.isNotEmpty()) add(Mode.MBOKO) }
    }

    fun cycleMode(d: Int) { mode = modes[(modes.indexOf(mode) + d + modes.size) % modes.size]; index = 0 }

    private fun scale(): List<Long> = (availability as? StakeAvailability.Allowed)?.let { if (mode == Mode.NDEM) it.ndem else if (mode == Mode.MBOKO) it.mboko else emptyList() }.orEmpty()

    fun cycleAmount(d: Int) { val s = scale(); if (s.isNotEmpty()) index = (index + d + s.size) % s.size }

    /** La mise choisie (null = partie libre). */
    fun stake(): StakeSpec? = scale().getOrNull(index)?.let { StakeSpec(if (mode == Mode.NDEM) "NDEM" else "MBOKO", it) }

    private fun cur(): WalletCurrency? = when (mode) { Mode.NDEM -> WalletCurrency.NDEM; Mode.MBOKO -> WalletCurrency.MBOKO; Mode.FREE -> null }

    private fun balance(): Long? = when (mode) { Mode.NDEM -> balanceNdem; Mode.MBOKO -> balanceMboko; Mode.FREE -> null }

    /** Sièges de CETTE TV qui misent (1 aux échecs ; 1 à [maxSeats] au Quiz) : une mise par siège. */
    var seats: Int = 1; private set

    /** Plusieurs sièges sont-ils possibles (Quiz) ? Sinon la ligne « Joueurs ici qui misent » n'existe pas. */
    val multiSeat: Boolean get() = maxSeats > 1

    fun cycleSeats(d: Int) { if (maxSeats > 1) seats = (seats - 1 + d + maxSeats) % maxSeats + 1 }

    /** Ce que le blocage met de côté : mise × sièges (null = partie libre). */
    fun blocked(): Long? = stake()?.let { it.per * seats }

    /** Le solde connu couvre-t-il ce qui sera bloqué (mise × sièges) ? Sans solde connu on laisse l'API juger (elle dit « Solde insuffisant : N disponibles »). */
    fun affordable(): Boolean { val s = stake() ?: return true; val b = balance() ?: return true; return b >= s.per * seats }

    fun modeText(): String = mode.label
    fun amountText(): String = stake()?.let { "${WalletView.thousands(it.per)} ${it.cur} par joueur" } ?: "—"
    fun seatsText(): String = if (seats == 1) "1 joueur de cette TV mise" else "$seats joueurs de cette TV misent"
    fun balanceLine(): String? = cur()?.let { c -> balance()?.let { "Votre solde : ${WalletView.thousands(it)} ${c.name}" } }
    /** Aux échecs : la cagnotte du gagnant ; au Quiz (cagnotte partagée) : ce qui est bloqué pour cette TV et la règle du partage. */
    fun potLine(): String? = stake()?.let {
        if (shared) "Mise bloquée : ${WalletView.thousands(it.per * seats)} ${it.cur} · cagnotte partagée selon le classement"
        else "Cagnotte : ${WalletView.thousands(2 * it.per)} ${it.cur} pour le gagnant"
    }
    /** Pourquoi les mises ne sont pas proposées, ou null. */
    fun freeOnlyReason(): String? = (availability as? StakeAvailability.FreeOnly)?.reason
    fun warning(): String? = if (stake() != null) abandonWarning else null
}

/** Le choix de mise est le même aux échecs et au Quiz (games-G5) : un seul modèle, deux jeux. */
typealias StakeChoice = ChessStakeChoice

/** Les textes des échecs en ligne, identiques sur la TV et dans les tests (jamais un code brut). */
object ChessOnlineTexts {
    /** Dit avant chaque partie misée (créer, rejoindre) ; assez court pour tenir sur deux lignes de la TV. */
    const val ABANDON_WARNING = "Abandonner ou quitter la partie fait perdre la mise ; plus de 60 s hors ligne = abandon."
    const val CREATE_STAKE_TITLE = "Créer une partie avec mise ?"
    const val JOIN_STAKE_TITLE = "Cette partie se joue avec une mise"
    const val SETTLING = "Règlement de la mise en cours…"
    const val SETTLED = "Règlement fait : votre solde est à jour dans « Mes jetons »."
    const val SETTLE_LATER = "Règlement en attente : la TV le renverra dès que la connexion revient (le service en garde aussi une copie)."
    const val SETTLE_REFUSED = "Règlement refusé par le serveur : contactez votre point focal."

    fun stakeLine(s: StakeSpec) = "Mise : ${WalletView.thousands(s.per)} ${s.cur} par joueur · cagnotte ${WalletView.thousands(2 * s.per)} ${s.cur}"

    /** Ce que le joueur voit en fin de partie, d'après le RÉSULTAT et ce que l'API a réglé : [net] = gain net (+) ou perte (−) APRÈS frais ; [aborted] = mise rendue. */
    fun outcome(cur: String, net: Long, aborted: Boolean): String = when {
        aborted -> "Partie interrompue : mise rendue"
        net > 0 -> "Vous gagnez ${WalletView.thousands(net)} $cur"
        net < 0 -> "Vous perdez ${WalletView.thousands(-net)} $cur"
        else -> "Partie nulle : mise rendue"
    }

    /** Le décompte du forfait d'un adversaire absent (secondes restantes avant sa défaite). */
    fun opponentAway(leftSec: Int) = "Adversaire déconnecté : forfait dans ${leftSec.coerceAtLeast(0)} s"

    /**
     * La ligne « Adversaire déconnecté : forfait dans N s » d'après la vue du service (`room.away` = depuis combien de ms chaque couleur est absente, `room.forfeitMs`), ou null si l'adversaire est là ou
     * si cette TV regarde seulement. [sinceViewMs] = le temps écoulé depuis la réception de la vue (le décompte tourne en local, la vue ne change pas à chaque seconde).
     */
    fun awayLine(view: Map<String, Any?>, sinceViewMs: Long): String? {
        if (view["stage"] != "PLAYING") return null
        val room = view["room"] as? Map<*, *> ?: return null
        val mine = (view["me"] as? Map<*, *>)?.get("color") as? String ?: return null
        val opp = if (mine == "w") "b" else "w"
        val away = ((room["away"] as? Map<*, *>)?.get(opp) as? Number)?.toLong() ?: return null
        val forfeit = (room["forfeitMs"] as? Number)?.toLong() ?: 60_000L
        return opponentAway(((forfeit - away - sinceViewMs + 999) / 1000).toInt())
    }

    fun cannotAfford(cur: String, available: Long) = "Solde insuffisant : ${WalletView.thousands(available)} $cur disponibles"

    fun joinStakeQuestion(s: StakeSpec) = "Cette partie se joue avec une mise de ${WalletView.thousands(s.per)} ${s.cur} par joueur. Bloquer votre mise et jouer ?"

    /** La ligne sous le titre de la confirmation d'une mise (une seule ligne à l'écran) : montant, et le solde quand il est connu. */
    fun stakeSubtitle(s: StakeSpec, balance: Long?): String =
        "${WalletView.thousands(s.per)} ${s.cur} par joueur" + (balance?.let { " · votre solde ${WalletView.thousands(it)} ${s.cur}" } ?: "")

    /** Ce que le joueur lit après une action refusée par le service ; null quand il n'y a rien à dire (accepté, ou sans effet). */
    fun ackText(result: String): String? = when (result) {
        "OK", "IGNORED", "SAME" -> null
        "ILLEGAL" -> "Coup illégal"
        "NOT_YOUR_TURN" -> "Ce n'est pas votre tour"
        "STALE" -> "Coup trop tardif : la partie a avancé"
        "OVER", "CLOSED" -> "La partie est terminée"
        "FORBIDDEN" -> "Action impossible pour l'instant"
        "UNKNOWN_PLAYER" -> "Vous n'êtes plus dans cette partie"
        "BAD_REQUEST" -> "Le service n'a pas compris cette action : mettez CastBridge-TV à jour"
        else -> "Action refusée"
    }
}
