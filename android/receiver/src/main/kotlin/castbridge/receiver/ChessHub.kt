package castbridge.receiver

import android.app.Activity
import android.content.Context
import android.content.Intent
import castbridge.core.chess.ChessHttp
import castbridge.core.chess.ChessRoom
import castbridge.core.games.RoomHttp
import castbridge.core.tv.ApiReply
import castbridge.core.tv.PublicRoutes
import castbridge.core.tv.ReceiverServer
import fi.iki.elonen.NanoHTTPD

/**
 * The chess room of this TV (at most one), shared by [ChessActivity] (which opens and closes it) and the HTTP server
 * (public routes /chess/..., see [ChessHttp]). Internet games (« Échecs › En ligne », [ChessOnlineHub]) are played by the TV itself on the
 * online service `castbridge-play`; during one, the same public routes serve a read-only showcase of the game to the phones of the house
 * ([ChessOnlineHub.activeHost]), so a phone never talks to the service. There is no « online » switch any more: the service says what it can do.
 */
object ChessHub {
    @Volatile var room: ChessRoom? = null
        private set

    /**
     * Public routes (no PIN) for the TV's HTTP server: the online showcase while an Internet game is on, else the room of the house. Both are the games platform's [RoomHttp] (the home room through
     * [ChessHttp], which only says what is chess); the showcase is served on the same `/chess` prefix with the same `ply` move parameter, and its `hello` says `"online":true`.
     */
    val http: PublicRoutes = object : PublicRoutes {
        private val home = ChessHttp({ room })
        private val online = RoomHttp("/chess", { ChessOnlineHub.activeHost() }, { ChessHttp.PAGE }, ChessRoom.PROTOCOL, "Aucune partie d'échecs ouverte sur la TV.",
            helloExtra = ",\"online\":true", seqParam = "ply")
        override val extraThreads: Int get() = home.extraThreads + online.extraThreads
        override fun serve(s: NanoHTTPD.IHTTPSession): NanoHTTPD.Response? = if (ChessOnlineHub.activeHost() != null) online.serve(s) else home.serve(s)
    }

    @Synchronized fun open(): ChessRoom {
        room?.close()
        return ChessRoom().also { room = it }
    }

    @Synchronized fun close(r: ChessRoom?) {
        r?.close()
        if (room === r) room = null
    }

    fun joinUrl(r: ChessRoom): String? = joinUrl(r.code)
    fun joinUrl(code: String): String? = TvService.localIp()?.let { "http://$it:${ReceiverServer.PORT}/chess?code=$code" }

    /**
     * PIN-protected routes for the phone app: GET /api/chess (is a room open, its code), POST /api/chess/open (opens the chess screen on the TV).
     * POST /api/chess/config is kept as a harmless no-op for phone apps that still send it (the old Internet switch: `online=0|1`, `relay=`): it only answers the status.
     */
    @Suppress("UNUSED_PARAMETER")
    fun api(activity: Activity?, ctx: Context, path: String, method: String, params: Map<String, String>): ApiReply? = when {
        path == "/api/chess" && method == "GET" -> ApiReply(200, statusJson())
        path == "/api/chess/open" && method == "POST" && activity == null ->
            ApiReply(409, "{\"error\":\"Ouvrez CastBridge TV sur la TV, puis réessayez\",\"needsForeground\":true}")
        path == "/api/chess/open" && method == "POST" -> {
            TvConnect.feature("chess", "phone")
            activity!!.runOnUiThread {
                runCatching { activity.startActivity(Intent(activity, ChessActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            var waited = 0
            while (waited < 3000 && room?.stage.let { it == null || it == ChessRoom.Stage.CLOSED }) { Thread.sleep(100); waited += 100 }
            ApiReply(200, statusJson())
        }
        path == "/api/chess/config" && method == "POST" -> ApiReply(200, statusJson())
        else -> null
    }

    /**
     * `online` stays false for the phone apps that read it (they used it to show a phone-side Internet chip: phones never play online); `onlineGame` says that this TV is playing an Internet game whose
     * showcase the phones can watch with the 4-digit [code] (never the Internet room code).
     */
    private fun statusJson(): String {
        val host = ChessOnlineHub.activeHost()
        if (host != null) return """{"open":true,"code":"${host.code}","stage":"${host.roomStage}","players":${host.players().size},"online":false,"onlineGame":true,""" +
            """"url":${ReceiverServer.q(joinUrl(host.code) ?: "")}}"""
        val r = room?.takeIf { it.stage != ChessRoom.Stage.CLOSED } ?: return """{"open":false,"online":false,"onlineGame":false}"""
        return """{"open":true,"code":"${r.code}","stage":"${r.stage}","players":${r.players().size},"online":false,"onlineGame":false,""" +
            """"url":${ReceiverServer.q(joinUrl(r) ?: "")}}"""
    }
}
