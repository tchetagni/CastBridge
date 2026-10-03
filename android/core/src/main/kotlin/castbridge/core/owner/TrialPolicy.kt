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

    /** Shown when the trial is refused a Bluetooth file (a lot delivery stays accepted). */
    const val BT_MESSAGE = "Version d'essai : la réception de fichiers par Bluetooth est désactivée."
    const val USB_MESSAGE = "Version d'essai : l'import depuis une clé USB est désactivé. Entrez un code de production pour le débloquer."
    const val SSH_MESSAGE = "Version d'essai : l'accès SSH à la TV est désactivé. Entrez un code de production pour le débloquer."

    /**
     * ALLOWLIST of what the trial opens (default deny: a route added tomorrow is closed until it is listed here). Exact paths, and prefixes (a path under "<prefix>/").
     * Streaming with no copy (the phone's « Lire en direct » = /api/playurl, control of the cast), pairing / remote control, activation, rental, lots and « Apprendre »,
     * the Sudoku, and the connection helpers. Never: files, library, storage, folders, trash, downloads, uploads, transfers, USB, SSH, APK installs, screenshots, quiz, chess.
     */
    private val EXACT = setOf("/", "/api/hello", "/api/info", "/api/sysinfo", "/api/playurl", "/api/pause", "/api/resume", "/api/stop", "/api/seek", "/api/volume", "/api/restart",
        "/api/connections", "/api/net", "/api/background", "/api/autostart", "/api/overlay-permission", "/api/bluetooth", "/api/bluetooth/discoverable",
        "/api/activation", "/api/rental", "/api/lots", "/api/store", "/api/store/catalog", "/api/learn", "/api/sudoku", "/api/games", "/api/games/open", "/api/parental",
        "/api/server", "/api/server/me", "/api/server/url", "/api/server/contact")
    private val PREFIXES = listOf("/api/activation", "/api/rental", "/api/lots", "/api/store", "/api/learn", "/api/sudoku", "/api/player", "/api/remote", "/api/bluetooth/tunnel", "/api/gateway",
        "/api/parental", "/api/content/reports")
    /** Under an allowed prefix but still closed: the subtitle of a stored file, the owner's reset of the installation key, and « Apprendre » pack installs/imports (an import reads a pack from the TV's storage; the trial gets its lots through /api/lots). */
    private val DENIED_UNDER_ALLOWED = setOf("/api/player/subfile", "/api/learn/packs/import", "/api/learn/packs/install", "/api/activation/install-key/reset",
        "/api/store/requests", "/api/store/requests/ack", "/api/store/request")

    fun routeAllowed(path: String): Boolean {
        if (path.contains("..") || path.contains("//") || path.contains('\\') || path.contains('%')) return false
        if (path in DENIED_UNDER_ALLOWED) return false
        return path in EXACT || PREFIXES.any { path == it || path.startsWith("$it/") }
    }
    fun routeBlocked(path: String): Boolean = !routeAllowed(path)

    /** `/stream/<name>` reads the TV's stored media: in the trial only the TV's own player (loopback + its run token) may. */
    fun streamAllowed(loopbackToken: Boolean): Boolean = loopbackToken

    /** Bluetooth files: only a lot (and its signed proof) is accepted in the trial; any other file is refused. */
    fun btFileAllowed(name: String): Boolean =
        castbridge.core.lots.LotNames.parseFileName(name.removeSuffix(castbridge.core.lots.LotNames.PROOF_SUFFIX)) != null
    fun tileAllowed(id: String) = id !in CLOSED_TILES
    fun gameAllowed(id: String) = id in GAMES
}
