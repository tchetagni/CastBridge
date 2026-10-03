package castbridge.receiver

import android.app.Activity
import android.content.Intent
import android.content.Context
import castbridge.core.connect.QuizPackHook
import castbridge.core.lots.LotBudget
import castbridge.core.lots.LotMeta
import castbridge.core.quiz.CachedQuestionSource
import castbridge.core.quiz.PackedQuestionSource
import castbridge.core.quiz.QuestionFilter
import castbridge.core.quiz.QuizEdition
import castbridge.core.quiz.QuizLotConsumer
import castbridge.core.quiz.QuizLotScopes
import castbridge.core.quiz.QuizPackApi
import castbridge.core.quiz.QuizPackManager
import castbridge.core.quiz.QuizPackStore
import castbridge.core.quiz.ServerPackSource
import castbridge.core.quiz.courseKey
import castbridge.core.quiz.filterOfCourse
import castbridge.core.quiz.QuestionSource
import castbridge.core.quiz.QuizHistoryBook
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
    @Volatile private var source: CachedQuestionSource? = null
    /** Anti-repetition histories (the TV's own + one per phone), in files/quiz/history: see docs/QUIZ.md, Règle des 300 parties. */
    @Volatile private var histories: QuizHistoryBook? = null
    fun historyBook(ctx: Context): QuizHistoryBook = histories ?: synchronized(this) {
        histories ?: QuizHistoryBook(File(ctx.applicationContext.filesDir, "quiz/history")).also { histories = it }
    }

    /**
     * Bundled questions, plus those received from the CastBridge server (QuizSync, daily or « Mettre à jour les questions »)
     * in the app's small cache file; the bundled bank alone when offline or if the cache is unusable.
     */
    fun cachedSource(ctx: Context): CachedQuestionSource = source ?: synchronized(this) {
        source ?: CachedQuestionSource(File(ctx.applicationContext.filesDir, "quiz/questions-cache.json")).also { source = it }
    }
    private fun source(ctx: Context): QuestionSource = questionSource(ctx)

    // ------------------------------------------------------------------ question packs (docs/QUIZ.md, « Packs de questions »)
    @Volatile private var packed: PackedQuestionSource? = null
    @Volatile private var packStore: QuizPackStore? = null
    @Volatile private var packManager: QuizPackManager? = null

    /** `CastBridge/QuizPacks` of the USB drives / volumes: packs found there are played as they are (no download, no size cap). */
    fun driveDirs(): List<File> = LearnHub.packDirs().filter { it.third }.map { File(it.second.parentFile, "QuizPacks") }.distinctBy { it.absolutePath }

    /** Where downloaded packs are kept: the USB drive when there is one, else the app's external storage; never the TV's internal flash if avoidable. */
    private fun packCacheDir(ctx: Context): Pair<File, Long> {
        LearnHub.packDirs().firstOrNull { it.third && !it.first.contains("racine") }?.let { return File(it.second.parentFile, "QuizPacks/cache") to 0L }
        ctx.applicationContext.getExternalFilesDir("quiz-packs")?.let { return it to 0L }
        return File(ctx.applicationContext.filesDir, "quiz/packs") to (200L shl 20)      // internal flash: keep 200 MB free
    }

    @Synchronized fun packStore(ctx: Context): QuizPackStore {
        val (dir, minFree) = packCacheDir(ctx)
        packStore?.takeIf { it.dir.absolutePath == dir.absolutePath }?.let { return it }
        return QuizPackStore(dir, minFreeBytes = minFree, publicKeys = castbridge.core.update.UpdateKeys.PUBLIC_KEYS + extraKeys).also { packStore = it; packManager = null }
    }
    @Volatile private var extraKeys: List<String> = emptyList()

    /** The bank in use: bundled + server cache + installed packs + USB packs. */
    @Synchronized fun questionSource(ctx: Context): PackedQuestionSource {
        val store = packStore(ctx)
        packed?.takeIf { packedFor === store }?.let { return it }
        return PackedQuestionSource(cachedSource(ctx), store, ::driveDirs, lotConsumer(ctx)).also { packed = it; packedFor = store }
    }
    private var packedFor: QuizPackStore? = null

    // ------------------------------------------------------------------ lots (docs/QUIZ.md, « Lots »): offline first, delivered later by the phone
    @Volatile private var lots: QuizLotConsumer? = null
    /** Signature (Ed25519, base64) of a lot's catalog entry, supplied by the lots framework with the lot it delivers; null = unsigned = refused. */
    @Volatile var lotSignatureOf: (LotMeta) -> String? = { null }

    /** The quiz lots held by this TV (files/lots/quiz). Installing one refreshes the question bank by itself: no restart, no network. */
    @Synchronized fun lotConsumer(ctx: Context): QuizLotConsumer = lots ?: QuizLotConsumer(
        File(ctx.applicationContext.filesDir, "lots/quiz"), publicKeys = castbridge.core.update.UpdateKeys.PUBLIC_KEYS + extraKeys,
        signatureOf = { lotSignatureOf(it) }, maxBytes = LotBudget.TV_MAX_BYTES, onChanged = { packed?.refresh() },
    ).also { lots = it }

    /** The order in which the framework's planner should fill this TV with quiz lots: the courses played here first, then general knowledge, then the rest. */
    fun lotPriority(ctx: Context): List<String> = QuizLotScopes.quizPriority(played = historyBook(ctx).host.courses().toList())

    @Synchronized fun packManager(ctx: Context): QuizPackManager {
        val store = packStore(ctx)
        return packManager ?: QuizPackManager(store, combined = { questionSource(ctx).bank().forChannel(TvConnect.channel()) }, history = { historyBook(ctx).host },
            onChanged = { questionSource(ctx).refresh() }, played = { historyBook(ctx).host.courses() }).also { packManager = it }
    }

    private fun watched(ctx: Context): List<QuestionFilter> =
        (listOf(QuestionFilter.GENERAL) + historyBook(ctx).host.courses().mapNotNull { filterOfCourse(it) }).distinctBy { it.courseKey }

    /** PIN routes the phone uses to push packs (docs/QUIZ.md § relais par le téléphone). */
    fun packApi(ctx: Context): QuizPackApi = QuizPackApi(packStore(ctx), packManager(ctx)) { watched(ctx) }.also { it.lots = lotConsumer(ctx) }

    /** Called by the server link: refills the courses that are running low on fresh games, from the server (direct or through the phone). */
    fun packHook(ctx: Context, keys: List<String>): QuizPackHook {
        extraKeys = keys
        return QuizPackHook { baseUrl, http, token, deviceId ->
            val m = packManager(ctx)
            val src = ServerPackSource(baseUrl, http, token, deviceId, castbridge.core.update.UpdateKeys.PUBLIC_KEYS + keys)
            val reports = m.needs(watched(ctx)).sortedBy { it.freshGames }.mapNotNull { n -> filterOfCourse(n.course)?.let { m.refill(it, listOf(src), maxPacks = 2) } }
            reports.firstOrNull { it.installed.isNotEmpty() || it.unreachable } ?: reports.firstOrNull()
                ?: QuizPackManager.Report("-", 0, 0, emptyList(), emptyList(), null, "Assez de questions pour 60 parties sans répétition sur chaque parcours joué")
        }
    }
    /** Public routes (no PIN) for the TV's HTTP server. */
    val http = QuizHttp({ room })

    /** Opens a fresh room (new code), closing the previous one. */
    @Synchronized fun open(ctx: Context): QuizRoom {
        room?.close()
        // review questions are played on the TV (owner decision, see QuizEdition): the server's channel only matters when that is turned off
        return QuizRoom(source(ctx).bank().forChannel(QuizEdition.playChannel(TvConnect.channel())), bankFor = { f -> source(ctx).bankFor(f).forChannel(QuizEdition.playChannel(TvConnect.channel())) }, asked = asked, wallet = wallet, histories = historyBook(ctx))
            .also { it.feedback = TvConnect.feedback; room = it }
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
            TvConnect.feature("quiz", "phone")
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
