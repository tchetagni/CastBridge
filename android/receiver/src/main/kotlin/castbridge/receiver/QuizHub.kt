package castbridge.receiver

import android.app.Activity
import android.content.Intent
import android.content.Context
import castbridge.core.quiz.CachedQuestionSource
import castbridge.core.quiz.QuestionSource
import castbridge.core.quiz.VirtualWallet
import castbridge.core.quiz.QuizHttp
import castbridge.core.quiz.QuizRoom
import castbridge.core.tv.ApiReply
import castbridge.core.tv.ReceiverServer
import java.io.File

/**
 * The quiz room of this TV (at most one), shared by [QuizActivity] (which opens and closes it) and the HTTP server
 * of [PlayerActivity] (routes /quiz/..., see [QuizHttp]). Questions already asked stay excluded for the whole app
 * session, across rooms.
 */
object QuizHub {
    @Volatile var room: QuizRoom? = null
        private set
    private val asked = LinkedHashSet<String>()
    /** Demo tokens only (no real money): see docs/QUIZ.md, « Mise payante ». */
    private val wallet = VirtualWallet()
    @Volatile private var source: QuestionSource? = null

    /** Bundled questions, plus those a future question server leaves in the app's small cache file (offline fallback). */
    private fun source(ctx: Context): QuestionSource = source ?: synchronized(this) {
        source ?: CachedQuestionSource(File(ctx.filesDir, "quiz/questions-cache.json")).also { source = it }
    }
    /** Public routes (no PIN) for the TV's HTTP server. */
    val http = QuizHttp({ room })

    /** Opens a fresh room (new code), closing the previous one. */
    @Synchronized fun open(ctx: Context): QuizRoom {
        room?.close()
        return QuizRoom(source(ctx).bank(), asked = asked, wallet = wallet).also { room = it }
    }

    @Synchronized fun close(r: QuizRoom?) {
        r?.close()
        if (room === r) room = null
    }

    /**
     * PIN-protected routes for the CastBridge phone app: GET /api/quiz (is a room open, its code) and
     * POST /api/quiz/open (starts the quiz on the TV screen).
     */
    /** [activity] = the visible TV screen, or null when it is closed (opening the quiz then needs the screen). */
    fun api(activity: Activity?, path: String, method: String): ApiReply? = when {
        path == "/api/quiz" && method == "GET" -> ApiReply(200, statusJson())
        path == "/api/quiz/open" && method == "POST" && activity == null ->
            ApiReply(409, "{\"error\":\"Ouvrez CastBridge TV sur la TV, puis réessayez\",\"needsForeground\":true}")
        path == "/api/quiz/open" && method == "POST" -> {
            activity!!.runOnUiThread {
                runCatching { activity.startActivity(Intent(activity, QuizActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            // the activity opens the room in onCreate: wait a little so the phone gets the code in the same answer
            var waited = 0
            while (waited < 3000 && room?.stage.let { it == null || it == QuizRoom.Stage.CLOSED }) { Thread.sleep(100); waited += 100 }
            ApiReply(200, statusJson())
        }
        else -> null
    }

    private fun statusJson(): String {
        val r = room?.takeIf { it.stage != QuizRoom.Stage.CLOSED }
            ?: return """{"open":false}"""
        return """{"open":true,"code":"${r.code}","stage":"${r.stage}","mode":"${r.mode}","players":${r.players().size},""" +
            """"max":${r.maxPlayers},"url":${ReceiverServer.q(joinUrl(r) ?: "")}}"""
    }

    fun joinUrl(r: QuizRoom): String? = TvService.localIp()?.let { "http://$it:${ReceiverServer.PORT}/quiz?code=${r.code}" }
}
