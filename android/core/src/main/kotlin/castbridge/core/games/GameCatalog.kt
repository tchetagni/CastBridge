package castbridge.core.games

import castbridge.core.games.bataille.Bataille
import java.security.SecureRandom

/**
 * Les jeux de la catégorie « Jeux » qui se jouent sur la plateforme commune (docs/GAMES.md), pour la TV (cartes du hub, état de la page `/jeux/<id>`) et le téléphone. Un jeu est ici
 * soit JOUABLE (ses règles exécutables existent : [newRoom] rend une salle), soit « BIENTÔT » : ses règles sont attendues du propriétaire, RIEN n'est inventé, il n'a pas de salle et ne
 * peut pas être lancé. Ajouter un jeu = ajouter une [Entry] et une branche de [newRoom] (voir « Ajouter un jeu à règles » dans docs/GAMES.md).
 */
object GameCatalog {
    /** Ce que disent l'écran de la TV, le téléphone et la page web d'un jeu dont les règles ne sont pas encore là. */
    const val SOON = "Bientôt : règles en attente du propriétaire"

    /** Un jeu du catalogue : l'identifiant de l'URL `/jeux/<id>` et des journaux, son nom d'écran, ses modes, une phrase, et s'il est jouable. */
    class Entry(val id: String, val name: String, val modes: String, val blurb: String, val playable: Boolean) {
        /** La ligne d'état de la carte du hub (« Prêt à jouer », « Bientôt : règles en attente du propriétaire »). */
        val status: String get() = if (playable) "Prêt à jouer" else SOON
    }

    val BATAILLE = Entry(Bataille.id, Bataille.NAME, "Solo · À deux (téléphones)",
        "Démonstration de la plateforme de jeux : la carte la plus haute gagne la levée, égalité = bataille.", playable = true)
    val FAP_FAP = Entry("fap-fap", "Fap-Fap", "Solo · Multijoueur", "Jeu de cartes, en solo ou à plusieurs. Les règles sont à fournir par le propriétaire.", playable = false)
    val AGRAHAM_TIA = Entry("agraham-tia", "Agraham Tia", "Solo · Multijoueur", "Jeu de cartes, en solo ou à plusieurs. Les règles sont à fournir par le propriétaire.", playable = false)

    val all: List<Entry> = listOf(BATAILLE, FAP_FAP, AGRAHAM_TIA)

    fun entry(id: String?): Entry? = all.firstOrNull { it.id == id }

    /**
     * Une salle neuve pour le jeu [id], ou null s'il n'a pas de règles (« bientôt » : rien n'est lancé) ou n'existe pas. Les paramètres servent aux tests (horloge fausse, aléa à graine,
     * pas de fil de fond, ordinateur sans délai) ; la TV prend les valeurs par défaut.
     */
    fun newRoom(
        id: String?,
        clock: () -> Long = { System.nanoTime() / 1_000_000 },
        random: java.util.Random = SecureRandom(),
        autoTick: Boolean = true,
        aiDelayMs: Long = RulesRoom.DEFAULT_AI_DELAY_MS,
        wallClock: () -> Long = { System.currentTimeMillis() },
    ): RulesRoom<*, *>? = when (id) {
        Bataille.id -> RulesRoom(Bataille, clock = clock, random = random, autoTick = autoTick, aiDelayMs = aiDelayMs, wallClock = wallClock)
        else -> null
    }
}
