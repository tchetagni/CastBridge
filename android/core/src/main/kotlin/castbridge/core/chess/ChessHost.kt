package castbridge.core.chess

/**
 * Ce que les routes publiques de la TV (`/chess/api/...`, [ChessHttp]) demandent à « la salle » : la salle de la maison ([ChessRoom]) ou, pendant une partie EN LIGNE, la vitrine que la TV tient pour ses
 * téléphones ([OnlineChessHost]). Les téléphones du foyer ne parlent qu'à LEUR TV, jamais au service de jeu : c'est la TV qui relaie.
 */
interface ChessHost {
    val maxPlayers: Int
    val stage: ChessRoom.Stage
    /** Compteur de changements : les téléphones (SSE, long-poll) attendent qu'il dépasse celui qu'ils ont vu. */
    val version: Long
    fun players(): List<ChessRoom.Player>
    fun player(token: String?): ChessRoom.Player?
    fun join(code: String?, name: String?, token: String? = null): ChessRoom.JoinResult
    fun leave(token: String?): Boolean
    fun touch(p: ChessRoom.Player)
    fun streamOpened(p: ChessRoom.Player)
    fun streamClosed(p: ChessRoom.Player)
    fun colorOf(p: ChessRoom.Player?): Int?
    fun act(token: String?, action: String, arg: String? = null, ply: Int? = null): ChessRoom.Act
    fun viewJson(token: String?): String
    fun awaitChange(since: Long, timeoutMs: Long): Long
}
