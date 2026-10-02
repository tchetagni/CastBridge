package castbridge.core.owner

/**
 * What a TRIAL key opens ([TvAccess.trial]): cast streaming (receiving what the phone casts, pairing, remote control), the Sudoku, and « Apprendre » for the lots of the trial window only. Copying and
 * moving media (file transfers, storage moves, deleting and renaming, downloads, the library) is switched off. A production key, or an owner grant, lifts every restriction.
 */
object TrialPolicy {
    const val MESSAGE = "Version d'essai : seuls le streaming, le Sudoku et les lots d'essai sont disponibles. Entrez un code de production pour tout débloquer."

    /** Home tiles closed in the trial (ids of the TV home): the library, receiving files from the phone, the USB drive, the downloads and « Langues » (paid content, not part of the trial). */
    val CLOSED_TILES: Set<String> = setOf("library", "receive", "usb", "downloads", "langues")
    /** The home tile that upgrades a trial to production (shown FIRST, in the trial only): id, exact label and the title of its screen. */
    const val UPGRADE_TILE = "upgrade"
    const val UPGRADE_LABEL = "Passer en production"
    const val UPGRADE_TITLE = "Passer en version complète"
    const val UPGRADE_EXPLAIN = "Pour passer en version complète, donnez le code d'appareil ci-dessous (ou la demande complète) à CastBridge : une clé de production vous sera remise. L'essai continue de fonctionner jusqu'à l'activation de cette clé."
    const val FULL_VERSION = "Version complète"
    /** Games of the « Jeux » hub reachable in the trial. */
    val GAMES: Set<String> = setOf("sudoku")

    /** TV API routes (and their sub-paths) a trial key does not open: the copy / move / delete of media and the downloads. Streaming (/api/play, /api/hello, the player) stays. */
    private val BLOCKED = listOf("/api/transfer", "/api/part", "/api/upload", "/api/storage/move", "/api/storage/target", "/api/delete", "/api/rename", "/api/folders", "/api/downloads", "/api/dl", "/api/trash",
        "/upload", "/quiz", "/chess")

    fun routeBlocked(path: String): Boolean = BLOCKED.any { path == it || path.startsWith("$it/") }
    fun tileAllowed(id: String) = id !in CLOSED_TILES
    fun gameAllowed(id: String) = id in GAMES
}
