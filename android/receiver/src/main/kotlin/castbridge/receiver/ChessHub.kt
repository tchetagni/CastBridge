package castbridge.receiver

import android.app.Activity
import android.content.Context
import android.content.Intent
import castbridge.core.chess.ChessHttp
import castbridge.core.chess.ChessRelayClient
import castbridge.core.chess.ChessRoom
import castbridge.core.tv.ApiReply
import castbridge.core.tv.ReceiverServer

/**
 * The chess room of this TV (at most one), shared by [ChessActivity] (which opens and closes it) and the HTTP server
 * (public routes /chess/..., see [ChessHttp]). Also the Internet settings: the relay stays off until the central
 * server has the routes (docs/CHESS.md), then « online » is switched on here or through POST /api/chess/config.
 */
object ChessHub {
    @Volatile var room: ChessRoom? = null
        private set

    /** Public routes (no PIN) for the TV's HTTP server. */
    val http = ChessHttp({ room })

    @Synchronized fun open(): ChessRoom {
        room?.close()
        return ChessRoom().also { room = it }
    }

    @Synchronized fun close(r: ChessRoom?) {
        r?.close()
        if (room === r) room = null
    }

    fun joinUrl(r: ChessRoom): String? = TvService.localIp()?.let { "http://$it:${ReceiverServer.PORT}/chess?code=${r.code}" }

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("castbridge_chess", Context.MODE_PRIVATE)

    /** The Internet relay client with the saved settings (off by default). */
    fun relay(ctx: Context): ChessRelayClient {
        val p = prefs(ctx)
        return ChessRelayClient(p.getString("relay_url", null) ?: ChessRelayClient.DEFAULT_URL, p.getBoolean("online", false))
    }

    /**
     * PIN-protected routes for the phone app: GET /api/chess (is a room open, its code), POST /api/chess/open (opens
     * the chess screen on the TV), POST /api/chess/config?online=0|1[&relay=https://…] (Internet play on/off).
     */
    fun api(activity: Activity?, ctx: Context, path: String, method: String, params: Map<String, String>): ApiReply? = when {
        path == "/api/chess" && method == "GET" -> ApiReply(200, statusJson(ctx))
        path == "/api/chess/open" && method == "POST" && activity == null ->
            ApiReply(409, "{\"error\":\"Ouvrez CastBridge TV sur la TV, puis réessayez\",\"needsForeground\":true}")
        path == "/api/chess/open" && method == "POST" -> {
            TvConnect.feature("chess", "phone")
            activity!!.runOnUiThread {
                runCatching { activity.startActivity(Intent(activity, ChessActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            var waited = 0
            while (waited < 3000 && room?.stage.let { it == null || it == ChessRoom.Stage.CLOSED }) { Thread.sleep(100); waited += 100 }
            ApiReply(200, statusJson(ctx))
        }
        path == "/api/chess/config" && method == "POST" -> {
            val e = prefs(ctx).edit()
            params["online"]?.let { e.putBoolean("online", it == "1" || it == "true") }
            params["relay"]?.takeIf { it.startsWith("https://") || it.startsWith("http://") }?.let { e.putString("relay_url", it) }
            e.apply()
            ApiReply(200, statusJson(ctx))
        }
        else -> null
    }

    private fun statusJson(ctx: Context): String {
        val relay = relay(ctx)
        val online = """"online":${relay.enabled}"""
        val r = room?.takeIf { it.stage != ChessRoom.Stage.CLOSED } ?: return """{"open":false,$online}"""
        return """{"open":true,"code":"${r.code}","stage":"${r.stage}","players":${r.players().size},$online,""" +
            """"url":${ReceiverServer.q(joinUrl(r) ?: "")}}"""
    }
}
