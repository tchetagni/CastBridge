package castbridge.core.tv.home

data class HomeTileInfo(val id: String, val label: String, val status: String? = null, val on: Boolean = false, val warn: Boolean = false)

data class HomeGroupDef(val id: String, val label: String, val tileIds: List<String>)

sealed interface HomeEntry {
    val id: String
    val label: String

    data class Direct(val tile: HomeTileInfo) : HomeEntry {
        override val id get() = tile.id
        override val label get() = tile.label
    }

    data class Group(val def: HomeGroupDef, val tiles: List<HomeTileInfo>, val summary: String) : HomeEntry {
        override val id get() = def.id
        override val label get() = def.label
        val on get() = tiles.any { it.on }
        val warn get() = tiles.any { it.warn }
    }
}

object HomeGroups {
    const val MEDIA = "media"
    const val LEARN = "learn"
    const val GAMES = "games"
    const val NETWORK = "network"
    const val ADMIN = "admin"
    const val PAIR = "pair"
    /** Tuiles toujours directement sur l'accueil, avant les groupes (« Bibliothèque » : la plus utilisée ; la mise à niveau de l'essai). */
    val QUICK_ACCESS = listOf("upgrade", "library")

    val GROUPS = listOf(
        HomeGroupDef(MEDIA, "Médias", listOf("library", "downloads", "usb", "receive")),
        HomeGroupDef(LEARN, "Apprendre", listOf("learn", "langues")),
        HomeGroupDef(GAMES, "Jeux et jetons", listOf("games", "online_game", "wallet")),
        HomeGroupDef(NETWORK, "Téléphones et réseau", listOf(PAIR, "remote", "bluetooth", "wifi_direct", "internet", "phones")),
        HomeGroupDef(ADMIN, "Administration", listOf("admin", "updates", "settings", "dev_options", "parental", "help")),
    )

    fun groupOf(tileId: String): HomeGroupDef? = GROUPS.firstOrNull { tileId in it.tileIds }

    /**
     * L'accueil à partir des tuiles VISIBLES (déjà filtrées). Ordre : accès rapide, puis groupes dans l'ordre de [GROUPS],
     * puis les tuiles inconnues (jamais perdues, dans l'ordre reçu). Un groupe sans tuile visible disparaît ; un groupe
     * à une seule tuile ouvre cette tuile directement ; une tuile déjà en accès rapide n'est pas répétée.
     */
    fun layout(visible: List<HomeTileInfo>): List<HomeEntry> {
        val byId = LinkedHashMap<String, HomeTileInfo>()
        visible.forEach { byId.putIfAbsent(it.id, it) }
        val out = ArrayList<HomeEntry>()
        val shown = HashSet<String>()
        fun direct(t: HomeTileInfo) { if (shown.add(t.id)) out.add(HomeEntry.Direct(t)) }
        QUICK_ACCESS.forEach { id -> byId[id]?.let(::direct) }
        for (def in GROUPS) {
            val tiles = def.tileIds.mapNotNull { byId[it] }
            when {
                tiles.isEmpty() -> Unit
                tiles.size == 1 -> direct(tiles[0])
                else -> out.add(HomeEntry.Group(def, tiles, summary(tiles)))
            }
        }
        byId.values.filter { groupOf(it.id) == null && it.id !in QUICK_ACCESS }.forEach(::direct)
        return out
    }

    /** Résumé court du bouton de groupe : « 4 outils », puis « · 2 actifs » ou « · à vérifier » (un avertissement prime). */
    fun summary(tiles: List<HomeTileInfo>): String {
        val n = tiles.size
        val head = if (n > 1) "$n outils" else "$n outil"
        val on = tiles.count { it.on }
        return when {
            tiles.any { it.warn } -> "$head · à vérifier"
            on == 1 -> "$head · 1 actif"
            on > 1 -> "$head · $on actifs"
            else -> head
        }
    }
}

enum class HomeKey { LEFT, RIGHT, UP, DOWN, OK, BACK }

object HomeGrid {
    const val COLUMNS = 3
    fun rows(count: Int) = if (count <= 0) 0 else (count + COLUMNS - 1) / COLUMNS
    fun row(index: Int) = index / COLUMNS
    fun col(index: Int) = index % COLUMNS

    /** Ramène un indice dans [0, count) ; -1 si la grille est vide (rien à focaliser). */
    fun clamp(index: Int, count: Int) = if (count <= 0) -1 else index.coerceIn(0, count - 1)

    /**
     * Bords : GAUCHE/DROITE s'arrêtent aux bords de la ligne (pas de bouclage : on ne change pas de ligne sans le vouloir) ;
     * HAUT sur la première ligne reste sur place ; BAS vers une dernière ligne incomplète tombe sur la dernière tuile ;
     * BAS sur la dernière ligne reste sur place.
     */
    fun move(index: Int, key: HomeKey, count: Int): Int {
        if (count <= 0) return -1
        val i = clamp(index, count)
        return when (key) {
            HomeKey.LEFT -> if (col(i) == 0) i else i - 1
            HomeKey.RIGHT -> if (col(i) == COLUMNS - 1 || i == count - 1) i else i + 1
            HomeKey.UP -> if (i < COLUMNS) i else i - COLUMNS
            HomeKey.DOWN -> if (i + COLUMNS < count) i + COLUMNS else if (row(i) < rows(count) - 1) count - 1 else i
            else -> i
        }
    }
}

sealed interface HomeAction {
    data class Launch(val tileId: String) : HomeAction
    data class FocusHome(val id: String) : HomeAction
    data class FocusGrid(val index: Int) : HomeAction
}

sealed interface HomeNavState {
    data class Closed(val focusId: String? = null) : HomeNavState
    data class Open(val groupId: String, val focusIndex: Int, val returnTo: String) : HomeNavState
}

data class HomeStep(val state: HomeNavState, val action: HomeAction? = null, val consumed: Boolean = false)

object HomeNav {
    private fun group(entries: List<HomeEntry>, id: String) = entries.firstOrNull { it is HomeEntry.Group && it.id == id } as HomeEntry.Group?

    /**
     * Une touche dans l'état [state]. [focusId] = bouton d'accueil actuellement focalisé (état fermé). Fermé : OK sur un groupe
     * ouvre la grille (focus sur la première tuile), OK sur une tuile directe la lance ; les flèches restent à Android (non consommées) ;
     * RETOUR n'est pas consommé (l'activité quitte l'accueil comme avant). Ouvert : flèches = [HomeGrid.move], OK lance la tuile,
     * RETOUR ferme et rend le focus au bouton du groupe.
     */
    fun press(state: HomeNavState, entries: List<HomeEntry>, key: HomeKey, focusId: String? = null): HomeStep = when (state) {
        is HomeNavState.Closed -> {
            val id = focusId ?: state.focusId
            val e = entries.firstOrNull { it.id == id }
            when {
                key == HomeKey.OK && e is HomeEntry.Group -> HomeStep(HomeNavState.Open(e.id, 0, e.id), HomeAction.FocusGrid(0), true)
                key == HomeKey.OK && e is HomeEntry.Direct -> HomeStep(HomeNavState.Closed(e.id), HomeAction.Launch(e.tile.id), true)
                else -> HomeStep(HomeNavState.Closed(id))
            }
        }
        is HomeNavState.Open -> {
            val g = group(entries, state.groupId)
            if (g == null) closed(state, entries)                      // le groupe a disparu (réglage éteint, profil enfant…) : retour à l'accueil
            else when (key) {
                HomeKey.BACK -> HomeStep(HomeNavState.Closed(state.returnTo), HomeAction.FocusHome(state.returnTo), true)
                HomeKey.OK -> HomeStep(state, g.tiles.getOrNull(HomeGrid.clamp(state.focusIndex, g.tiles.size))?.let { HomeAction.Launch(it.id) }, true)
                else -> {
                    val i = HomeGrid.move(state.focusIndex, key, g.tiles.size)
                    HomeStep(state.copy(focusIndex = i), HomeAction.FocusGrid(i), true)
                }
            }
        }
    }

    /**
     * L'accueil est reconstruit (statuts, réglages, profil) : si la grille ouverte n'existe plus, retour à l'accueil sur son bouton
     * (ou le premier restant) ; sinon le focus reste sur la MÊME tuile quand elle existe encore (suivie par identifiant [focusedTileId]).
     */
    fun reconcile(state: HomeNavState, entries: List<HomeEntry>, focusedTileId: String? = null): HomeNavState = when (state) {
        is HomeNavState.Closed -> if (state.focusId == null || entries.any { it.id == state.focusId }) state else HomeNavState.Closed(entries.firstOrNull()?.id)
        is HomeNavState.Open -> {
            val g = group(entries, state.groupId)
            if (g == null) closed(state, entries).state
            else {
                val byId = focusedTileId?.let { id -> g.tiles.indexOfFirst { it.id == id } }?.takeIf { it >= 0 }
                state.copy(focusIndex = byId ?: HomeGrid.clamp(state.focusIndex, g.tiles.size))
            }
        }
    }

    private fun closed(state: HomeNavState.Open, entries: List<HomeEntry>): HomeStep {
        val to = entries.firstOrNull { it.id == state.returnTo }?.id ?: entries.firstOrNull()?.id
        return HomeStep(HomeNavState.Closed(to), to?.let { HomeAction.FocusHome(it) }, true)
    }
}
