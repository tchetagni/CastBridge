package castbridge.core.chess

import castbridge.core.games.RoomHttp
import castbridge.core.games.RoomLimits
import castbridge.core.tv.PublicRoutes
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Response

/**
 * The chess routes, served by the TV on port 8765 without the admin PIN (room code + player token instead):
 *
 * - GET  /chess                        the mobile web page
 * - GET  /chess/api/hello              is a room open? (no code, no names)
 * - POST /chess/api/join               code, name [, token to take one's seat back] → token, id, colour
 * - GET  /chess/api/events?token=      Server-Sent Events: the state at every change (+ a ping every 15 s)
 * - GET  /chess/api/state?token=&since=&wait=   long-poll fallback (≤ 25 s)
 * - POST /chess/api/act?token=&action=&arg=&ply=   move / resign / draw / sit / stand (see [ChessRoom.act])
 * - POST /chess/api/leave?token=
 *
 * Same limits as the quiz: requests per IP, wrong codes per IP, event streams per player and in total. Since the games platform (docs/GAMES.md) the implementation is the generic
 * [RoomHttp] shared with every game of `/jeux/<id>`: this class only says what is chess (the prefix, the page, the `ply` parameter, the messages).
 */
class ChessHttp(
    room: () -> ChessRoom?,
    clock: () -> Long = { System.nanoTime() / 1_000_000 },
    streamMaxMs: Long = 10 * 60_000L,
    pingMs: Long = 15_000,
) : PublicRoutes {
    private val http = RoomHttp("/chess", room, { PAGE }, ChessRoom.PROTOCOL, "Aucune partie d'échecs ouverte sur la TV.", seqParam = "ply",
        limits = RoomLimits(clock), clock = clock, streamMaxMs = streamMaxMs, pingMs = pingMs)

    override val extraThreads: Int get() = http.extraThreads
    override fun serve(s: NanoHTTPD.IHTTPSession): Response? = http.serve(s)

    companion object {
        const val MAX_STREAMS = RoomHttp.MAX_STREAMS
        const val BURST = RoomHttp.BURST
        const val RATE_PER_MS = RoomHttp.RATE_PER_MS
        const val MAX_BAD_CODES = RoomHttp.MAX_BAD_CODES
        const val CODE_WINDOW_MS = RoomHttp.CODE_WINDOW_MS

        /** The page, with the piece shapes put in (one design for the TV, the app and the web). */
        val PAGE: String by lazy {
            (ChessHttp::class.java.getResourceAsStream("/castbridge/chess/play.html")?.use { String(it.readBytes(), Charsets.UTF_8) }
                ?: "<!doctype html><meta charset=utf-8><title>Échecs</title><p>Page des échecs indisponible.")
                .replace("/*PIECES*/null", ChessPieces.json())
        }
    }
}
