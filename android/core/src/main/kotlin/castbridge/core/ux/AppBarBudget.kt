package castbridge.core.ux

/**
 * Budget d'une barre d'application sur téléphone (R-23) : le logo (entrée cachée du propriétaire) doit toujours rester
 * visible, et aucune étiquette ne doit se couper. Règle pure, testée en JVM : ajouter une 4e action « à plat » casse
 * un test, pas l'écran.
 *
 *  - au plus [MAX_VISIBLE] actions visibles dans la barre (icônes ou boutons texte), le menu « ⋮ » n'est pas compté ;
 *  - une action visible à étiquette texte tient en [MAX_LABEL] caractères ; au-delà elle va dans le menu ;
 *  - tout le reste va dans le menu de débordement (étiquettes libres, une par ligne).
 */
object AppBarBudget {
    const val MAX_VISIBLE = 2
    const val MAX_LABEL = 14

    /** @param label libellé de l'action ; @param showsText vrai si le texte est affiché dans la barre (faux : icône seule). */
    data class Action(val label: String, val showsText: Boolean = false)

    /** Une barre : ses actions visibles et le contenu de son menu « ⋮ ». */
    data class Bar(val name: String, val visible: List<Action>, val menu: List<String> = emptyList())

    /** Liste des écarts (vide = conforme). */
    fun violations(bar: Bar): List<String> = buildList {
        if (bar.visible.size > MAX_VISIBLE) add("${bar.name} : ${bar.visible.size} actions visibles (max $MAX_VISIBLE), mettre le surplus dans le menu")
        bar.visible.filter { it.showsText && it.label.length > MAX_LABEL }
            .forEach { add("${bar.name} : « ${it.label} » fait ${it.label.length} caractères (max $MAX_LABEL dans une barre), à mettre dans le menu") }
        if (bar.visible.any { it.label.isBlank() } || bar.menu.any { it.isBlank() }) add("${bar.name} : libellé vide")
    }
}

/** Libellés d'actions des barres du module téléphone, utilisés tels quels par les écrans (source unique, vérifiée par AppBarBudgetTest). */
object UxLabels {
    const val ACTIVATE_TV = "Activer la TV"
    const val DEVICE_REQUEST = "Demande d'appareil"
    const val RENTALS = "Locations"
    const val PARENTAL = "Contrôle parental"
    const val SETTINGS = "Réglages"
    const val MORE = "Plus d'actions"
    const val PICK_TV = "Choisir la TV"
    const val OPTIONS = "Options"
    const val REFRESH = "Actualiser"
    const val ASSISTANT = "Ranger ma bibliothèque (Assistant)"
    const val ORGANIZE = "Ranger les fichiers de la TV dans des dossiers"
    const val HISTORY = "Historique et annulation"
    const val ASSISTANT_SETTINGS = "Réglages de l'assistant"
    const val DOWNLOAD_SETTINGS = "Réglages"
    const val ABOUT = "À propos"
    const val LINK = "Liaison"

    val MAIN_MENU = listOf(ACTIVATE_TV, DEVICE_REQUEST, RENTALS, PARENTAL)

    /** Toutes les barres à actions du module (les barres sans action ne sont pas concernées). */
    val BARS: List<AppBarBudget.Bar> = listOf(
        AppBarBudget.Bar("Accueil", listOf(AppBarBudget.Action(SETTINGS), AppBarBudget.Action(MORE)), MAIN_MENU),
        AppBarBudget.Bar("Télécommande", listOf(AppBarBudget.Action(LINK), AppBarBudget.Action(OPTIONS)), listOf(PICK_TV)),
        AppBarBudget.Bar("Bibliothèque de la TV", listOf(AppBarBudget.Action(REFRESH), AppBarBudget.Action(MORE)), listOf(ASSISTANT, ORGANIZE)),
        AppBarBudget.Bar("Téléchargements sur la TV", listOf(AppBarBudget.Action(DOWNLOAD_SETTINGS), AppBarBudget.Action(ABOUT))),
        AppBarBudget.Bar("Assistant de rangement", listOf(AppBarBudget.Action(HISTORY), AppBarBudget.Action(ASSISTANT_SETTINGS))),
    )
}
