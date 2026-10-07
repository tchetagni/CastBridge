package castbridge.core.quiz.online

import castbridge.core.connect.Routes
import castbridge.core.net.JsonLite
import castbridge.core.owner.TvAccess
import castbridge.core.ux.SignalLevel

/*
 * w20-05 (POC « TV à TV avec relais », DESIGN-W20-AMENDEMENT § 2.6) : les règles PURES des écrans « Partie Internet » de CastBridge-TV.
 * Le dessin (vues Android) n'a aucune décision : il lit ces modèles. Aucun accès réseau, fichier ni horloge ici (OnlinePurityTest).
 */

/** Le drapeau `quiz.online` : l'interrupteur de l'utilisateur (réglage de la TV), puis l'ordre signé `flag.set`, puis le défaut compilé. */
object QuizOnlineFlag {
    const val NAME = "quiz.online"
    /** Décision du propriétaire du 2026-10-04 (« lève tous les verrous ») : allumé par défaut dans tous les builds ; l'interrupteur de l'utilisateur reste. */
    const val COMPILED_DEFAULT = true
    fun enabled(settingsValue: Boolean?, flagOrder: Boolean?, compiledDefault: Boolean): Boolean = settingsValue ?: flagOrder ?: compiledDefault
}

/** relay-R1 : un téléphone synchronisé peut-il donner Internet à la TV qui n'en a pas ? [UNKNOWN] = l'appelant ne sait pas (comportement d'avant). */
enum class PlayRelay { UNKNOWN, NO_PHONE, OLD_PHONE, POSSIBLE }

/** Ce que le Quiz montre de la partie Internet : rien, une raison (jamais un écran vide), ou la tuile. */
sealed class PlayTile {
    object Hidden : PlayTile() { override fun toString() = "Hidden" }
    data class Blocked(val reason: String) : PlayTile()
    object Available : PlayTile() { override fun toString() = "Available" }
}

/**
 * La porte de la tuile « Partie Internet » : drapeau + TV activée (essai, grâce ou production) + Internet + profil adulte + service qui répond.
 * Une TV qui n'est pas dans cet état n'ouvre AUCUNE connexion ([mayOpenNetwork]). [serviceUp] : null = pas encore sondé (on essaie), false = ne répond pas.
 */
object PlayGate {
    const val MSG_NO_INTERNET = "Connexion Internet requise"
    const val MSG_SERVICE_DOWN = "Quiz en ligne : service indisponible"
    const val MSG_ACTIVATION_FILE = "Partie Internet : activez la TV avec le fichier d'activation"
    /** Dite au menu quand le service tourne avec `CASTBRIDGE_PLAY_REVOCATIONS=off` (POC) : une TV révoquée y joue encore. */
    const val NOTE_REVOCATIONS_OFF = "Service d'essai : les activations révoquées ne sont pas encore vérifiées."

    fun tile(flagOn: Boolean, edition: HostEdition, hasInternet: Boolean, childProfile: Boolean, clockDoubt: Boolean = false, serviceUp: Boolean? = null, verifiableActivation: Boolean = true,
             relay: PlayRelay = PlayRelay.UNKNOWN): PlayTile = when {
        !flagOn -> PlayTile.Hidden
        edition == HostEdition.NONE -> PlayTile.Blocked(PlayRules.MSG_ACTIVATE)
        !verifiableActivation -> PlayTile.Blocked(MSG_ACTIVATION_FILE)   // M-4 : même règle que le service (clé courte invérifiable)
        clockDoubt -> PlayTile.Blocked(TvAccess.CHECK_CLOCK_LABEL)
        childProfile -> PlayTile.Blocked(PlayRules.MSG_CHILD)
        // relay-R1 : sans Internet, la tuile reste proposée si un téléphone synchronisé peut ouvrir un tuyau (la TV le lui demandera à l'appui) ; sinon la raison est dite avec les mots du relais
        !hasInternet && relay == PlayRelay.POSSIBLE -> PlayTile.Available     // le service ne peut pas être sondé sans Internet : on essaiera à l'appui, par le tuyau
        !hasInternet && relay == PlayRelay.NO_PHONE -> PlayTile.Blocked(castbridge.core.relay.RelayText.NO_PHONE)
        !hasInternet && relay == PlayRelay.OLD_PHONE -> PlayTile.Blocked(castbridge.core.relay.RelayText.OLD_PHONE)
        !hasInternet -> PlayTile.Blocked(MSG_NO_INTERNET)
        serviceUp == false -> PlayTile.Blocked(MSG_SERVICE_DOWN)
        else -> PlayTile.Available
    }

    /** Seule une tuile disponible peut ouvrir une connexion sortante. */
    fun mayOpenNetwork(tile: PlayTile): Boolean = tile == PlayTile.Available

    /** L'édition de la TV pour la porte : verrouillée ⇒ aucune ; essai ; grâce ; sinon production (le service refait son propre calcul sur l'activation `cbx1`). */
    fun editionOf(locked: Boolean, trial: Boolean, grace: Boolean): HostEdition = when {
        locked -> HostEdition.NONE
        trial -> HostEdition.TRIAL
        grace -> HostEdition.GRACE
        else -> HostEdition.PROD
    }
}

/** Saisie du code de salle au D-pad : une grille des 32 symboles, huit emplacements `XXXX-XXXX`. Immuable ; seuls les symboles du code entrent. */
data class RoomCodeEntry(val chars: String = "") {
    val complete: Boolean get() = chars.length == RoomCode.LENGTH

    fun append(c: Char): RoomCodeEntry {
        if (complete) return this
        val n = when (val u = c.uppercaseChar()) { 'I', 'L' -> '1'; 'O' -> '0'; else -> u }
        return if (n in RoomCode.ALPHABET) RoomCodeEntry(chars + n) else this
    }

    fun backspace(): RoomCodeEntry = if (chars.isEmpty()) this else RoomCodeEntry(chars.dropLast(1))

    /** `K7M2-Q___` : les emplacements vides sont des soulignés. */
    fun display(): String = chars.padEnd(RoomCode.LENGTH, '_').let { it.substring(0, 4) + "-" + it.substring(4) }

    /** Le code normalisé, ou null tant qu'il n'est pas complet. */
    fun code(): String? = if (complete) RoomCode.normalize(chars) else null

    companion object {
        /** Quatre rangées de huit : exactement l'alphabet du code (sans I, L, O, U). */
        fun keypad(): List<List<Char>> = RoomCode.ALPHABET.toList().chunked(8)
    }
}

/**
 * Les écrans de la « Partie Internet ». Quiz misé (games-G5) : [STAKE_CHOICE] (Libre / NDEM / MBOKO, montant, joueurs d'ici qui misent), [STAKE_CONFIRM] (confirmation avant de bloquer une mise),
 * [JOIN_STAKE] (la salle qu'on rejoint est misée : la TV bloque la sienne ou renonce).
 */
enum class PlayScreen { MENU, OPENING, ENTER_CODE, ROOM, CONFIRM_LEAVE, LOST, FAILED, BLOCKED, CLOSED, STAKE_CHOICE, STAKE_CONFIRM, JOIN_STAKE }

sealed class PlayEvent {
    object ChooseCreate : PlayEvent()
    object ChooseJoin : PlayEvent()
    data class CodeSubmitted(val raw: String?) : PlayEvent()
    object Seated : PlayEvent()
    data class Failed(val text: String) : PlayEvent()
    object LinkLost : PlayEvent()
    object Back : PlayEvent()
    object ConfirmLeave : PlayEvent()
    object FlagOff : PlayEvent()
    /** Écran de mise : la TV a choisi sa mise ([plan] non nul : mise à confirmer) ou « Libre » (null : la partie s'ouvre tout de suite). */
    data class StakeChosen(val plan: PlayStakePlan?) : PlayEvent()
    /** « Bloquer ma mise et créer » : confirmé. */
    object StakeConfirmed : PlayEvent()
    /** Le service dit que la salle qu'on rejoint est misée de [spec] : la TV doit bloquer sa mise (ou renoncer). */
    data class NeedsStake(val spec: StakeSpec) : PlayEvent()
    /** La TV rejoint la salle misée avec [seats] sièges qui misent (le blocage se prend alors, sur la MÊME liaison). */
    data class JoinWithSeats(val seats: Int) : PlayEvent()
}

/** Ce que la TV a décidé de miser : la mise par siège (monnaie, montant) et le nombre de sièges de CETTE TV qui misent (1 à 8) ; le blocage vaut `mise × sièges`. */
data class PlayStakePlan(val spec: StakeSpec, val seats: Int) {
    val blocked: Long get() = spec.per * seats
}

sealed class PlayIntent {
    object Create : PlayIntent() { override fun toString() = "Create" }
    /** Créer une salle MISÉE (Quiz misé, games-G5) : la TV bloque d'abord sa mise, puis crée avec son blocage. */
    data class CreateStaked(val plan: PlayStakePlan) : PlayIntent()
    data class Join(val code: String) : PlayIntent()
}

/**
 * L'état des écrans. [stakeOffer] : le menu propose-t-il une partie MISÉE (mises permises à cette TV, service et serveur à jour) ? Faux : « Créer une partie » ouvre tout de suite une partie libre, comme avant.
 * [plan] : la mise choisie (écran de confirmation, ouverture) ; [joinStake] : la mise de la salle qu'on rejoint (écran [PlayScreen.JOIN_STAKE]).
 */
data class PlayFlowState(val screen: PlayScreen, val message: String? = null, val intent: PlayIntent? = null, val stakeOffer: Boolean = false, val plan: PlayStakePlan? = null, val joinStake: StakeSpec? = null)

/** L'automate des écrans (pur) : un évènement qui n'a pas de sens dans l'écran courant ne change rien. `CLOSED` = retour au menu du Quiz. */
object PlayFlow {
    const val MSG_BAD_CODE = "Le code a 8 symboles : XXXX-XXXX."

    fun start(tile: PlayTile, stakeOffer: Boolean = false): PlayFlowState = when (tile) {
        PlayTile.Hidden -> PlayFlowState(PlayScreen.CLOSED)
        is PlayTile.Blocked -> PlayFlowState(PlayScreen.BLOCKED, tile.reason)
        PlayTile.Available -> PlayFlowState(PlayScreen.MENU, stakeOffer = stakeOffer)
    }

    fun next(s: PlayFlowState, e: PlayEvent): PlayFlowState {
        if (e is PlayEvent.FlagOff) return PlayFlowState(PlayScreen.CLOSED)
        val lost = PlayFlowState(PlayScreen.LOST, LinkCause.LOST_TEXT)
        val menu = PlayFlowState(PlayScreen.MENU, stakeOffer = s.stakeOffer)
        return when (s.screen) {
            PlayScreen.MENU -> when (e) {
                // sans mise possible : une partie libre s'ouvre tout de suite (comme avant) ; avec : l'écran de mise (« Libre » en fait partie)
                PlayEvent.ChooseCreate -> if (s.stakeOffer) PlayFlowState(PlayScreen.STAKE_CHOICE, stakeOffer = true) else PlayFlowState(PlayScreen.OPENING, intent = PlayIntent.Create)
                PlayEvent.ChooseJoin -> PlayFlowState(PlayScreen.ENTER_CODE, stakeOffer = s.stakeOffer)
                PlayEvent.Back -> PlayFlowState(PlayScreen.CLOSED)
                else -> s
            }
            PlayScreen.STAKE_CHOICE -> when (e) {
                is PlayEvent.StakeChosen -> if (e.plan == null) PlayFlowState(PlayScreen.OPENING, intent = PlayIntent.Create, stakeOffer = s.stakeOffer)
                    else if (e.plan.seats !in 1..PlayProtocol.MAX_STAKE_SEATS || e.plan.spec.per < 1) s
                    else PlayFlowState(PlayScreen.STAKE_CONFIRM, stakeOffer = s.stakeOffer, plan = e.plan)
                PlayEvent.Back -> menu
                else -> s
            }
            PlayScreen.STAKE_CONFIRM -> when (e) {
                PlayEvent.StakeConfirmed -> s.plan?.let { PlayFlowState(PlayScreen.OPENING, intent = PlayIntent.CreateStaked(it), stakeOffer = s.stakeOffer, plan = it) } ?: s
                PlayEvent.Back -> PlayFlowState(PlayScreen.STAKE_CHOICE, stakeOffer = s.stakeOffer)
                else -> s
            }
            PlayScreen.ENTER_CODE -> when (e) {
                is PlayEvent.CodeSubmitted -> RoomCode.normalize(e.raw)?.let { PlayFlowState(PlayScreen.OPENING, intent = PlayIntent.Join(it), stakeOffer = s.stakeOffer) } ?: s.copy(message = MSG_BAD_CODE)
                PlayEvent.Back -> menu
                else -> s
            }
            PlayScreen.OPENING -> when (e) {
                PlayEvent.Seated -> s.copy(screen = PlayScreen.ROOM)
                is PlayEvent.Failed -> PlayFlowState(PlayScreen.FAILED, e.text, stakeOffer = s.stakeOffer)
                PlayEvent.LinkLost -> lost
                // la salle qu'on rejoint est misée : la liaison reste ouverte, rien n'est consommé ; la TV choisit (une seule fois par ouverture)
                is PlayEvent.NeedsStake -> if (s.intent is PlayIntent.Join && s.plan == null) PlayFlowState(PlayScreen.JOIN_STAKE, intent = s.intent, stakeOffer = s.stakeOffer, joinStake = e.spec) else s
                PlayEvent.Back -> menu
                else -> s
            }
            PlayScreen.JOIN_STAKE -> when (e) {
                is PlayEvent.JoinWithSeats -> {
                    val spec = s.joinStake
                    if (spec == null || e.seats !in 1..PlayProtocol.MAX_STAKE_SEATS) s
                    else PlayFlowState(PlayScreen.OPENING, intent = s.intent, stakeOffer = s.stakeOffer, plan = PlayStakePlan(spec, e.seats))
                }
                is PlayEvent.Failed -> PlayFlowState(PlayScreen.FAILED, e.text, stakeOffer = s.stakeOffer)
                PlayEvent.LinkLost -> lost
                PlayEvent.Back -> menu
                else -> s
            }
            PlayScreen.ROOM -> when (e) {
                PlayEvent.Back -> s.copy(screen = PlayScreen.CONFIRM_LEAVE)
                PlayEvent.LinkLost -> lost
                is PlayEvent.Failed -> PlayFlowState(PlayScreen.FAILED, e.text, stakeOffer = s.stakeOffer)
                else -> s
            }
            PlayScreen.CONFIRM_LEAVE -> when (e) {
                PlayEvent.Back -> s.copy(screen = PlayScreen.ROOM)         // « Annuler » est présélectionné : Retour = Annuler
                PlayEvent.ConfirmLeave -> menu
                PlayEvent.LinkLost -> lost
                is PlayEvent.Failed -> PlayFlowState(PlayScreen.FAILED, e.text, stakeOffer = s.stakeOffer)
                else -> s
            }
            PlayScreen.LOST -> if (e == PlayEvent.Back) PlayFlowState(PlayScreen.CLOSED) else s
            PlayScreen.FAILED -> if (e == PlayEvent.Back) menu else s
            PlayScreen.BLOCKED -> if (e == PlayEvent.Back) PlayFlowState(PlayScreen.CLOSED) else s
            PlayScreen.CLOSED -> s
        }
    }
}

enum class LinkAction { CONTINUE, DEGRADED, LEAVE }
data class LinkView(val action: LinkAction, val message: String?)

/**
 * Ce que l'écran fait de la liaison de CETTE TV (cause locale de [LinkCause]) : continuer, continuer en orange (dégradé ≠ panne : passerelle, reprise, réseau absent),
 * ou quitter (partie perdue à 60 s, certificat non valide : aucun contournement, aucun « continuer quand même »).
 */
object PlayLinkScreen {
    fun of(link: TvLink, via: Routes.Via?, certificateInvalid: Boolean, tvHasNetwork: Boolean = true): LinkView {
        if (certificateInvalid) return LinkView(LinkAction.LEAVE, LinkCause.TLS_TEXT)
        if (link == TvLink.Lost) return LinkView(LinkAction.LEAVE, LinkCause.LOST_TEXT)
        val lowered = LinkCause.lower(SafetySign.of(SafetyFacts(PlayScope.INTERNET)), via, link, tvHasNetwork, false)
        return when (lowered.level) {
            SignalLevel.GREEN -> LinkView(LinkAction.CONTINUE, null)
            SignalLevel.ORANGE, SignalLevel.BLACK -> LinkView(LinkAction.DEGRADED, lowered.text)
            SignalLevel.RED -> LinkView(LinkAction.LEAVE, lowered.text)
        }
    }
}

/** Le bandeau du haut, 1 ligne : il ne montre QUE la [SafetyView] reçue (déjà abaissée par la cause locale) ; la forme et le mot accompagnent toujours la couleur. */
data class PlayBannerModel(val scopeLine: String, val level: SignalLevel, val word: String, val text: String, val action: String?, val hereLine: String?, val description: String) {
    companion object {
        /** [localPlayers] = téléphones relayés par cette TV (null : pas de ligne « Ici »). */
        fun of(view: SafetyView, localPlayers: Int?, maxLocal: Int = RelayAuthority.MAX_PHONES): PlayBannerModel {
            val here = localPlayers?.let { RelaySeatsText.here(it, maxLocal) }
            val scope = "${view.scope.icon} ${view.scope.label}"
            val description = listOfNotNull(view.scope.label, view.word, view.text, view.action, here).joinToString(". ")
            return PlayBannerModel(scope, view.level, view.word, view.text, view.action, here, description)
        }
    }
}

/** Quel signe « TV seule / réseau local / Internet » chaque écran montre (via [SafetySign]). */
object PlayBanner {
    fun sign(screen: PlayScreen, session: SafetyView?): SafetyView? = when (screen) {
        // avant toute ouverture rien ne sort de la maison : « TV seule » (les écrans de mise aussi : le blocage se prend à l'API, rien n'est ouvert vers le service de jeu)
        PlayScreen.MENU, PlayScreen.ENTER_CODE, PlayScreen.STAKE_CHOICE, PlayScreen.STAKE_CONFIRM -> SafetySign.of(SafetyFacts(PlayScope.TV_ONLY))
        // la salle qu'on rejoint est misée : la liaison au service est ouverte, en attente du choix de la TV
        PlayScreen.OPENING, PlayScreen.JOIN_STAKE, PlayScreen.ROOM, PlayScreen.CONFIRM_LEAVE, PlayScreen.LOST -> session ?: SafetySign.of(SafetyFacts(PlayScope.INTERNET))
        PlayScreen.FAILED, PlayScreen.BLOCKED, PlayScreen.CLOSED -> null
    }
}

/** « Ici : N joueurs » : les téléphones de CETTE TV (au plus [RelayAuthority.MAX_PHONES]), avec les places restantes. */
object RelaySeatsText {
    fun here(count: Int, max: Int = RelayAuthority.MAX_PHONES): String {
        val c = count.coerceIn(0, max)
        val who = when (c) { 0 -> "aucun joueur"; 1 -> "1 joueur"; else -> "$c joueurs" }
        val free = max - c
        val room = when { free <= 0 -> "complet"; free == 1 -> "1 place libre"; else -> "$free places libres" }
        return "Ici : $who · $room"
    }
}

/** Les textes de refus, en français, jamais un code brut ni le texte du serveur : un motif inconnu (version plus récente du service) reste lisible. */
object PlayErrors {
    const val NO_INTERNET = "Internet injoignable : vérifiez la connexion de la TV."
    const val GENERIC = "La partie Internet n'a pas pu s'ouvrir. Réessayez dans un instant."
    const val UNEXPECTED_REPLY = "Réponse inattendue du service de jeu. Réessayez dans un instant."
    private val STABLE_CODE = Regex("^[A-Z][A-Z0-9_]{2,31}$")

    /** [reason] = le motif du message `error` ; [retryAfterMs] = l'attente structurée du service (0 : celle du motif). */
    fun text(reason: String?, retryAfterMs: Long = 0L): String {
        val r = reason?.trim()
        if (r.isNullOrEmpty()) return GENERIC
        PlayReason.of(r)?.let { known ->
            val wait = if (retryAfterMs > 0) retryAfterMs else known.retryAfterMs
            return if (known.retryable && wait > 0) known.message + " Nouvel essai possible dans ${(wait + 999) / 1000} s." else known.message
        }
        GameReason.of(r)?.let { return it.message }   // salles de jeu à tour de rôle et mises (échecs en ligne)
        return when (r) {
            PlayProtocol.UNSUPPORTED -> "Cette version de CastBridge-TV ne sait pas jouer en ligne : mettez-la à jour."
            PlayProtocol.BAD_REQUEST -> "Le service a refusé la demande : mettez CastBridge-TV à jour."
            PlayProtocol.FORBIDDEN -> "Action refusée par le service de jeu."
            PlayProtocol.UNKNOWN_PLAYER -> "Joueur inconnu dans cette partie."
            else -> if (STABLE_CODE.matches(r)) "Le service de jeu a refusé la demande ($r)." else GENERIC
        }
    }

    /** Refus de l'émetteur de tickets (`POST /api/v1/play/ticket`). */
    fun ticketHttp(code: Int): String = when (code) {
        401 -> "Cette TV n'est pas reconnue par le service. Ouvrez CastBridge-TV une fois connectée à Internet, puis réessayez."
        403 -> PlayReason.PLAY_TICKET_REFUSED.message
        429 -> "Trop de demandes : réessayez dans quelques minutes."
        503 -> "Le Quiz en ligne n'est pas disponible sur ce serveur pour le moment."
        else -> "Le service ne répond pas correctement (HTTP $code). Réessayez dans un instant."
    }
}

/** La réponse de l'émetteur de tickets, jugée sans réseau : tolérante aux champs inconnus, stricte sur le ticket. */
sealed class PlayTicketReply {
    data class Ok(val ticket: String, val expiresAt: Long) : PlayTicketReply()
    data class Refused(val text: String) : PlayTicketReply()

    companion object {
        fun parse(code: Int, body: String?): PlayTicketReply {
            if (code != 200) return Refused(PlayErrors.ticketHttp(code))
            val m = runCatching { JsonLite.obj(body.orEmpty()) }.getOrNull() ?: return Refused(PlayErrors.UNEXPECTED_REPLY)
            val t = m["ticket"] as? String
            if (t.isNullOrBlank() || t.length > PlayProtocol.MAX_TICKET || t.any { it.isWhitespace() }) return Refused(PlayErrors.UNEXPECTED_REPLY)
            return Ok(t, (m["expiresAt"] as? Number)?.toLong() ?: 0L)
        }
    }
}
