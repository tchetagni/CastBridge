package castbridge.core.ux

import castbridge.core.phone.CastAction
import castbridge.core.phone.MediaKind
import castbridge.core.tv.LibraryLogic
import castbridge.core.tv.QueueItem
import castbridge.core.tv.QueueStatus

/*
 * Ergonomie et navigation (2026-10-03, docs/coordination/DESIGN-UX-ERGONOMIE-NAVIGATION-2026-10-03.md).
 * Un seul endroit pour les mots et les petites décisions d'écran qui reviennent partout : les façons d'envoyer un fichier, les messages de
 * l'accueil du téléphone (jamais de blocage muet), l'onglet de départ, le résumé de la file d'envoi, l'aide de la TV et le focus D-pad de
 * l'accueil de la TV. Pur : les écrans ne font que dessiner ce qui sort d'ici (tests `CT/ux/UiTextsTest`).
 */

/**
 * Les façons d'envoyer un fichier à la TV, avec le libellé du bouton et sa ligne d'explication. Les mêmes mots dans « Ouvrir avec CastBridge »,
 * la feuille « Lire sur la TV » du lecteur, la bibliothèque du téléphone et l'accueil. « envoi » reste le nom générique d'un transfert en cours
 * (notifications, file d'attente) ; les boutons disent toujours ce qui arrive au fichier.
 */
enum class SendWay(val label: String, val hint: String) {
    COPY_AND_PLAY("Copier sur la TV et lire", "La TV commence la lecture pendant la copie ; le fichier reste aussi sur le téléphone."),
    COPY("Copier sur la TV", "Gardé sur la TV pour plus tard, sans lecture ; le fichier reste aussi sur le téléphone."),
    MOVE("Déplacer vers la TV", "Copié sur la TV, puis effacé du téléphone une fois la copie vérifiée : libère de la place."),
    LIVE("Lire en direct", "La TV lit depuis le téléphone et ne garde rien : restez sur le même Wi-Fi jusqu'à la fin."),
}

object SendWays {
    /** Accueil du téléphone, tuile « Copier sur la TV » : un seul fichier est lu à la fin de la copie (autoPlay), plusieurs partent en file. */
    const val HOME_COPY_HINT = "Une vidéo seule est lue à la fin de la copie ; reste sur le téléphone"
    const val HOME_MOVE_HINT = "Libère la place du téléphone"
    /** Titre de la feuille du lecteur (ancien « Diffuser sur »). */
    const val SHEET_TITLE = "Lire sur la TV"
    /** Entrée du menu de la bibliothèque du téléphone qui ouvre cette feuille (ancien « Caster… »). */
    const val MENU_PLAY_ON_TV = "Lire sur la TV…"

    fun of(a: CastAction): SendWay = when (a) {
        CastAction.COPY -> SendWay.COPY_AND_PLAY
        CastAction.LIVE -> SendWay.LIVE
        CastAction.MOVE -> SendWay.MOVE
    }

    /** « Copier sur la TV et lire » d'abord (le chemin fiable depuis R-08), puis « Lire en direct », puis « Déplacer ». */
    fun castOrder(actions: List<CastAction>): List<CastAction> {
        val rank = listOf(CastAction.COPY, CastAction.LIVE, CastAction.MOVE)
        return actions.sortedBy { rank.indexOf(it) }
    }

    /** Une photo n'est pas lue : « Copier sur la TV et lire » devient « Copier sur la TV ». */
    fun castLabel(a: CastAction, kind: MediaKind): String =
        if (a == CastAction.COPY && kind == MediaKind.IMAGE) SendWay.COPY.label else of(a).label

    fun castHint(a: CastAction, kind: MediaKind, isWeb: Boolean): String = when {
        a == CastAction.LIVE && isWeb -> "La TV ouvre le lien elle-même ; rien n'est copié."
        a == CastAction.COPY && kind == MediaKind.IMAGE -> "Une copie de la photo est gardée sur la TV ; elle reste aussi sur le téléphone."
        else -> of(a).hint
    }
}

/** Un message de l'accueil : son texte et s'il signale un problème (rouge) ou une information (neutre). */
data class UiNotice(val text: String, val error: Boolean)

/** Les messages de l'accueil « CastBridge TV » du téléphone : toute action qui ne peut pas aboutir le dit, avec la cause et quoi faire. */
object HomeNotices {
    private const val CHECK = "Vérifiez qu'elle est allumée, sur le même Wi-Fi, avec CastBridge-TV ouvert ; la ligne d'état en haut dit où en est la liaison."

    /** Une tâche qui a besoin de la TV (bibliothèque, échange, lecture) touchée alors qu'elle n'est pas jointe (ces touches ne faisaient rien). */
    fun needsTv(task: String) = UiNotice("« $task » attend la TV : elle n'est pas jointe pour l'instant. $CHECK", true)

    /** Les fichiers choisis ont-ils une route ? (avant : ils étaient choisis puis abandonnés en silence). */
    fun canPick(session: Boolean, pinTvName: String?): Boolean = session || pinTvName != null

    /** Pourquoi le sélecteur ne s'ouvre pas. [trustedSaved] : TV de confiance enregistrées. */
    fun pickBlocked(trustedSaved: Int) = if (trustedSaved > 0)
        UiNotice("Votre TV n'est pas encore jointe : rien ne peut partir pour l'instant. $CHECK Réessayez dès qu'elle est connectée.", true)
    else UiNotice("Aucune TV n'est reliée à ce téléphone : touchez « Ajouter ma TV » d'abord.", true)

    /** Résultat d'un choix de fichiers mis en file ; [refused] : la raison d'un refus s'il y en a eu un. */
    fun queued(count: Int, refused: String?): UiNotice {
        val head = if (count == 1) "1 fichier ajouté à la file d'attente." else "$count fichiers ajoutés à la file d'attente : ils partent l'un après l'autre."
        return if (refused != null) UiNotice("$head $refused", true) else UiNotice(head, false)
    }

    fun info(text: String) = UiNotice(text, false)
    fun error(text: String) = UiNotice(text, true)
}

/**
 * Les onglets du téléphone. L'ordre et les ids de télémétrie ne changent pas (mémoire des usagers, ancres des cahiers w11-01 et w17-07) ;
 * seul l'onglet de départ change : l'accueil des tâches « CastBridge TV » et non « TV DLNA » (fonction rare, ouverte à chaque lancement).
 * La mémoire du dernier onglet et la barre du haut restent au cahier w11-01.
 */
object PhoneTabs {
    /** [screen] : id d'écran (`screen_time`) ; [feature] : id envoyé quand on change d'onglet (null = jamais, comme avant). */
    enum class Tab(val label: String, val screen: String?, val feature: String?) {
        DLNA("TV DLNA", "cast", "cast"),
        HOME("CastBridge TV", "home", null),
        GAMES("Jeux", "games", "games"),
        PHONE("Sur le téléphone", "player", "player"),
        LEARN("Apprendre", "learn", "learn"),
        PARENTAL("Parental", null, null),           // jamais rapporté à la télémétrie
    }
    val ORDER: List<Tab> = Tab.values().toList()
    fun index(t: Tab): Int = ORDER.indexOf(t)
    val START: Int = index(Tab.HOME)
    /** Index inconnu (état restauré d'une autre version) : l'accueil. */
    fun at(i: Int): Tab = ORDER.getOrNull(i) ?: Tab.HOME
}

/** Une ligne « où en est ma copie » : [title] le résumé, [detail] la cause d'un arrêt ou d'un échec, [action] le mot du bouton. */
data class QueueGlance(val title: String, val detail: String?, val action: String, val error: Boolean)

object QueueGlances {
    /** Le résumé de la file d'envoi pour la barre visible sur tous les onglets ; null quand rien n'attend, ne tourne ni n'a échoué. */
    fun of(items: List<QueueItem>, note: String?): QueueGlance? {
        val running = items.firstOrNull { it.status == QueueStatus.RUNNING }
        val waiting = items.count { it.status == QueueStatus.WAITING }
        val failed = items.filter { it.status == QueueStatus.FAILED }
        if (running == null && waiting == 0 && failed.isEmpty()) return null
        val what = if (running?.move == true) "Déplacement vers la TV" else "Envoi vers la TV"
        val parts = listOfNotNull(
            running?.let { "« ${LibraryLogic.title(it.name)} »" },
            if (waiting > 0) "$waiting en attente" else null,
            if (failed.isNotEmpty()) (if (failed.size == 1) "1 échec" else "${failed.size} échecs") else null,
        )
        val detail = note ?: failed.firstOrNull()?.error
        return QueueGlance("$what : " + parts.joinToString(" · "), detail, if (failed.isNotEmpty()) "Voir et réessayer" else "Voir", failed.isNotEmpty())
    }

    /** Comment ce fichier part (ligne de la carte de file) : la lecture sur la TV est dite, qu'elle soit pendant ou après la copie. */
    fun kind(item: QueueItem): String = when {
        item.playOnTv -> "copie et lecture"
        item.move -> if (item.autoPlay) "déplacement puis lecture" else "déplacement"
        item.autoPlay -> "copie puis lecture"
        else -> "copie"
    }

    /** La barre est visible sur tous les onglets sauf l'accueil, qui porte déjà la carte complète de la file. */
    fun stripVisible(onHome: Boolean, glance: QueueGlance?): Boolean = glance != null && !onHome
}

/** L'aide « Recevoir du téléphone » / « Aide » de CastBridge-TV : elle nomme le bouton du téléphone par son libellé actuel. */
object TvHelpTexts {
    const val SEND_TITLE = "Copier une vidéo sur la TV"

    fun send(shownPin: String, webAddress: String): String =
        "1. Sur le téléphone, ouvrez l'app CastBridge, onglet « CastBridge TV ».\n" +
            "2. Touchez « ${SendWay.COPY.label} » et choisissez la vidéo.\n" +
            "3. La première fois, saisissez le code de la TV : $shownPin.\n\n" +
            "La vidéo est copiée sur la TV (ou sur sa clé USB) : elle continue même si le téléphone s'en va. " +
            "Depuis un ordinateur : ouvrez $webAddress dans un navigateur."
}

/**
 * Focus D-pad de l'accueil de CastBridge-TV. Règles : au lancement et après une vidéo, la première carte (la vidéo à reprendre ou la plus
 * récente : OK la lit sans déplacement) ; au retour d'un écran ouvert par une tuile (Bibliothèque, Connexion & réglages), cette tuile ;
 * quand les rangées sont reconstruites (premier fichier reçu, « Reprendre » qui apparaît, clé USB) avec le focus dedans, le focus est remis
 * au même endroit au lieu d'être perdu ; un focus ailleurs (puce d'état, panneau) n'est jamais volé.
 */
object TvHomeFocus {
    sealed class Target {
        object FirstCard : Target() { override fun toString() = "FirstCard" }
        data class Tool(val index: Int) : Target()
        object None : Target() { override fun toString() = "None" }
    }
    /** Ce que l'usager a ouvert en dernier depuis l'accueil. */
    enum class Opened { NOTHING, CARD, TOOL }

    private fun fallback(toolCount: Int, hasMediaRow: Boolean): Target = when {
        hasMediaRow -> Target.FirstCard
        toolCount > 0 -> Target.Tool(0)
        else -> Target.None
    }

    fun onShow(opened: Opened, toolIndex: Int, toolCount: Int, hasMediaRow: Boolean): Target =
        if (opened == Opened.TOOL && toolCount > 0) Target.Tool(toolIndex.coerceIn(0, toolCount - 1)) else fallback(toolCount, hasMediaRow)

    fun afterRebuild(hadFocusInRows: Boolean, focusedToolIndex: Int?, toolCount: Int, hasMediaRow: Boolean): Target = when {
        !hadFocusInRows -> Target.None
        focusedToolIndex != null && toolCount > 0 -> Target.Tool(focusedToolIndex.coerceIn(0, toolCount - 1))
        else -> fallback(toolCount, hasMediaRow)
    }
}

/** R-16 : mots de la petite icône de copie du lecteur de la TV (CopyBadge). Le texte accessible dit la même chose que l'icône. */
object CopyBadgeTexts {
    const val SLOWED = "Copie ralentie"
    fun description(count: Int, percent: Int?, slowed: Boolean): String {
        val lead = if (count >= 2) "$count copies en cours" else "Copie en cours"
        return lead + (percent?.let { " : $it %" } ?: "") + if (slowed) " · $SLOWED" else ""
    }
}
