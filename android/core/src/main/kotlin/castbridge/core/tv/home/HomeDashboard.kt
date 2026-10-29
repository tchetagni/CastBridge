package castbridge.core.tv.home

/** Four everyday destinations, then every remaining authorised tool. Never invents a permission. */
object HomeDashboard {
    const val RECEIVE = "dashboard_receive"
    const val MORE = "dashboard_more"

    fun layout(visible: List<HomeTileInfo>): List<HomeEntry> {
        val remaining = visible.distinctBy { it.id }.associateByTo(linkedMapOf()) { it.id }
        val entries = mutableListOf<HomeEntry>()
        remaining.remove("library")?.let { entries += HomeEntry.Direct(it) }
        fun group(id: String, title: String, ids: List<String>) {
            val tiles = ids.mapNotNull { remaining.remove(it) }
            if (tiles.isNotEmpty()) entries += HomeEntry.Group(HomeGroupDef(id, title, tiles.map { it.id }), tiles, when (id) {
                RECEIVE -> "Depuis votre téléphone"
                HomeGroups.LEARN -> "Formations et langues"
                HomeGroups.GAMES -> "Quiz et jeux en famille"
                else -> "Connexion, stockage et aide"
            })
        }
        group(RECEIVE, "Recevoir", listOf("receive", "pair", "pin"))
        group(HomeGroups.LEARN, "Apprendre", listOf("learn", "langues"))
        group(HomeGroups.GAMES, "Jouer", listOf("games", "online_game", "wallet"))
        group(MORE, "Connexion et réglages", remaining.keys.toList())
        return entries
    }
}
