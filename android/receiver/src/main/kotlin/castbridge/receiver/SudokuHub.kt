package castbridge.receiver

import android.app.Activity
import android.content.Context
import android.content.Intent
import castbridge.core.sudoku.Sudoku
import castbridge.core.sudoku.SudokuGame
import castbridge.core.sudoku.SudokuRecords
import castbridge.core.tv.ApiReply
import castbridge.core.tv.ReceiverServer

/**
 * What the TV keeps of the Sudoku, in the app's private preferences (no network, nothing leaves the TV): the game in
 * progress (one line, [SudokuGame.encode]) and the best times per level ([SudokuRecords.encode]).
 */
class SudokuStore(ctx: Context) {
    private val prefs = ctx.applicationContext.getSharedPreferences("castbridge_sudoku", Context.MODE_PRIVATE)

    fun saved(): SudokuGame? = SudokuGame.decode(prefs.getString("save", null))
    fun save(g: SudokuGame) { if (!g.won) prefs.edit().putString("save", g.encode()).apply() }
    fun clearSave() { prefs.edit().remove("save").apply() }
    fun records(): SudokuRecords = SudokuRecords.decode(prefs.getString("records", null))
    fun saveRecords(r: SudokuRecords) { prefs.edit().putString("records", r.encode()).apply() }

    var level: Sudoku.Difficulty
        get() = runCatching { Sudoku.Difficulty.valueOf(prefs.getString("level", "")!!) }.getOrDefault(Sudoku.Difficulty.EASY)
        set(v) { prefs.edit().putString("level", v.name).apply() }

    /** State line of the game's card in « Jeux ». */
    fun statusLine(): String {
        saved()?.let { return "Partie en cours · ${it.level.label} · ${SudokuRecords.clock(it.elapsedMs)}" }
        val r = records()
        val best = Sudoku.Difficulty.values().firstNotNullOfOrNull { d -> r.best(d)?.let { d to it } }
        return best?.let { (d, ms) -> "Meilleur temps · ${d.label} ${SudokuRecords.clock(ms)}" } ?: "Nouvelle partie"
    }
}

/**
 * The Sudoku the phone can see and drive (docs/GAMES.md): same mechanism as the quiz, PIN-protected routes of the TV's
 * HTTP server, handled by [TvService.extraApi]. The running [SudokuActivity] registers itself here.
 *
 *  - GET  /api/sudoku                   state (open or not, level, time, board...)
 *  - POST /api/sudoku/open[?level=EASY] opens the game on the TV (resumes the saved one unless a level is given)
 *  - POST /api/sudoku/cmd?do=...        left|right|up|down|cell&v=0..80|digit&v=1..9|clear|notes|hint|check|autonotes|menu|new&level=|quit
 */
object SudokuHub {
    @Volatile var active: SudokuActivity? = null

    fun api(activity: Activity?, ctx: Context, path: String, method: String, params: Map<String, String>): ApiReply? = when {
        path == "/api/sudoku" && method == "GET" -> ApiReply(200, stateJson(ctx))
        path == "/api/sudoku/open" && method == "POST" -> open(activity ?: GamesHub.foreground, ctx, params["level"])
        path == "/api/sudoku/cmd" && method == "POST" -> {
            val a = active
            if (a == null) ApiReply(409, "{\"error\":\"Le Sudoku n'est pas ouvert sur la TV\",\"open\":false}")
            else {
                val ok = a.remote(params["do"].orEmpty(), params["v"]?.toIntOrNull(), params["level"])
                if (ok) ApiReply(200, stateJson(ctx)) else ApiReply(400, "{\"error\":\"commande inconnue\"}")
            }
        }
        else -> null
    }

    private fun open(from: Activity?, ctx: Context, level: String?): ApiReply {
        if (active != null) return ApiReply(200, stateJson(ctx))
        if (from == null) return ApiReply(409, "{\"error\":\"Ouvrez CastBridge TV sur la TV, puis réessayez\",\"needsForeground\":true}")
        TvConnect.feature("sudoku", "phone")
        val lv = Sudoku.Difficulty.values().firstOrNull { it.name == level }
        from.runOnUiThread {
            runCatching {
                from.startActivity(Intent(from, SudokuActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).apply { lv?.let { putExtra("level", it.name) } })
            }
        }
        var waited = 0
        while (waited < 3000 && active == null) { Thread.sleep(100); waited += 100 }
        return ApiReply(200, stateJson(ctx))
    }

    fun stateJson(ctx: Context): String {
        val a = active
        if (a != null) runCatching { a.snapshot() }.getOrNull()?.let { return it }
        val s = SudokuStore(ctx)
        val g = s.saved()
        return "{\"open\":false,\"saved\":${g != null}" + (g?.let { ",\"level\":\"${it.level.name}\",\"levelLabel\":${ReceiverServer.q(it.level.label)},\"elapsedMs\":${it.elapsedMs}" } ?: "") + "}"
    }

    /** One finished or abandoned game, for the usage statistics (the telemetry drops it without the user's consent). */
    fun record(g: SudokuGame, result: String) {
        TvConnect.track("sudoku_game", mapOf("level" to g.level.name, "ms" to g.elapsedMs, "result" to result, "hints" to g.hintsUsed))
    }
}
