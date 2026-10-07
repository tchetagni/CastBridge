package castbridge.receiver

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import castbridge.core.games.GameCatalog
import castbridge.core.games.RoomStage
import castbridge.core.quiz.HighScores
import castbridge.core.tv.ApiReply
import castbridge.core.tv.ReceiverServer

/**
 * One game of the « Jeux » category (docs/GAMES.md). The list [Games.all] is declarative: to add a game, add a [GameDef]
 * there (icon, name, one line, state, what OK launches) and declare its activity in the manifest; the hub, the home tile
 * ("3 jeux"), the phone API and the telemetry id all follow from this list.
 */
class GameDef(
    /** Stable id, also the telemetry feature id (castbridge.core.telemetry.EventCatalog.TV_FEATURES) and the `game` of /api/games/open. */
    val id: String,
    val icon: Int,
    val name: String,
    /** "Solo · Multijoueur": the ways to play, in the accent color. */
    val modes: String,
    val blurb: String,
    val accent: Int,
    /** State line ("Meilleur score : 120", "Partie en cours"), read each time the hub is shown. */
    val status: (Context) -> String,
    val launch: (Activity) -> Unit,
    /** « Bientôt » : the rules are still to be given by the owner (docs/GAMES.md). Shown in the hub, never launched, not counted in the home tile. */
    val soon: Boolean = false,
)

object Games {
    /** What the current key opens: every game, or the Sudoku only in the trial. */
    fun visible(): List<GameDef> = if (ActivationCenter.trial()) all.filter { castbridge.core.owner.TrialPolicy.gameAllowed(it.id) } else all

    val all: List<GameDef> = listOf(
        GameDef("quiz", R.drawable.ic_cb_quiz, "Quiz des Millions", "Solo · Multijoueur",
            "Culture générale et niveaux scolaires, en solo ou avec les téléphones.", GamesColors.QUIZ,
            { c -> quizBest(c)?.let { "Meilleur score : $it" } ?: "Pas encore joué" },
            { a -> a.startActivity(Intent(a, QuizActivity::class.java)) }),
        GameDef("chess", R.drawable.ic_cb_echecs, "Échecs", "Solo · À deux · En ligne",
            "Contre l'ordinateur, à deux sur la TV ou en ligne.", GamesColors.CHESS,
            { _ -> if (ChessHub.room?.stage.let { it != null && it != castbridge.core.chess.ChessRoom.Stage.CLOSED }) "Partie en cours" else "Prêt à jouer" },
            { a -> a.startActivity(Intent(a, ChessActivity::class.java)) }),
        GameDef("sudoku", R.drawable.ic_t_sudoku, "Sudoku", "Solo",
            "Grilles à solution unique, de Facile à Expert.", GamesColors.SUDOKU,
            { c -> SudokuStore(c).statusLine() },
            { a -> a.startActivity(Intent(a, SudokuActivity::class.java)) }),
        // Jeux de cartes sur la plateforme commune (docs/GAMES.md) : la Bataille est une démonstration ; Fap-Fap et Agraham Tia attendent leurs règles (rien n'est inventé, rien ne se lance)
        cardGame(GameCatalog.BATAILLE),
        cardGame(GameCatalog.FAP_FAP),
        cardGame(GameCatalog.AGRAHAM_TIA),
    )

    /** The hub card of a game of the shared catalog: playable ones open [GameActivity], the others are « bientôt » (not launchable). */
    private fun cardGame(e: GameCatalog.Entry) = GameDef(e.id, R.drawable.ic_t_cards, e.name, e.modes, e.blurb, GamesColors.CARDS,
        { _ -> if (e.playable && GameRoomHost.room?.takeIf { it.rules.id == e.id }?.roomStage.let { it != null && it != RoomStage.CLOSED }) "Partie en cours" else e.status },
        { a -> if (e.playable) a.startActivity(Intent(a, GameActivity::class.java).putExtra(GameActivity.EXTRA_GAME, e.id)) },
        soon = !e.playable)

    /** How many games the home tile counts (« 4 jeux »): the playable ones; the « bientôt » cards are on the hub but are not yet games. */
    fun playableCount(): Int = visible().count { !it.soon }

    fun byId(id: String?) = all.firstOrNull { it.id == id }

    /** Best solo score of the quiz, or null when nothing was played yet (same preferences as [QuizActivity]). */
    private fun quizBest(c: Context): Long? = runCatching {
        val hs = HighScores.fromJson(c.getSharedPreferences("castbridge_quiz", Context.MODE_PRIVATE).getString("highscores", null))
        hs.boards().mapNotNull { hs.top(it, 1).firstOrNull()?.score }.maxOrNull()
    }.getOrNull()

    /** Launches [game] from a foreground [activity], counting it in the usage statistics (only with the user's consent). */
    fun open(activity: Activity, game: GameDef, source: String) {
        if (game.soon) return                       // « bientôt » : pas de règles, rien à lancer (et rien à compter)
        TvConnect.feature(game.id, source)
        game.launch(activity)
    }
}

/** Phone side of the games: GET /api/games (the list and their states), POST /api/games/open[?game=id] (PIN protected). */
object GamesHub {
    /** The games screen or a game on top, if any: the phone may open a game from there too (Android 14 forbids it from the background). */
    @Volatile var foreground: Activity? = null

    fun api(activity: Activity?, ctx: Context, path: String, method: String, params: Map<String, String>): ApiReply? = when {
        path == "/api/games" && method == "GET" -> ApiReply(200, json(ctx))
        // the room of a card game (code + the address of its web page), for the phone app: PIN protected like the rest of /api
        path == "/api/games/room" && method == "GET" -> ApiReply(200, GameRoomHost.roomJson(params["game"]))
        // the last signed game journals (bounded, in memory, no hand in them): docs/GAMES.md
        path == "/api/games/journals" && method == "GET" -> ApiReply(200, GameRoomHost.journalsJson(params["id"]))
        path == "/api/games/open" && method == "POST" -> {
            val a = activity ?: foreground
            if (a == null) ApiReply(409, "{\"error\":\"Ouvrez CastBridge TV sur la TV, puis réessayez\",\"needsForeground\":true}")
            else {
                val g = Games.byId(params["game"])?.takeIf { it in Games.visible() && !it.soon }   // the trial opens the Sudoku only; « bientôt » games open nothing
                TvConnect.feature(g?.id ?: "games", "phone")
                a.runOnUiThread {
                    runCatching { if (g != null) g.launch(a) else a.startActivity(Intent(a, GamesActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
                ApiReply(200, json(ctx))
            }
        }
        else -> null
    }

    private fun json(ctx: Context): String = "{\"games\":[" + Games.visible().joinToString(",") { g ->
        "{\"id\":${ReceiverServer.q(g.id)},\"name\":${ReceiverServer.q(g.name)},\"status\":${ReceiverServer.q(g.status(ctx))},\"soon\":${g.soon}}"
    } + "]}"
}

/**
 * « Jeux » : one card per game of [Games.all], big and readable at 3 m, D-pad only (left/right to choose, OK to play, BACK to
 * return to the home). Plain views in a fixed-proportion layout (see [Dx]): nothing is drawn over anything else, whatever the
 * screen size.
 */
class GamesActivity : Activity() {
    private lateinit var dx: Dx
    private val statusViews = LinkedHashMap<String, TextView>()
    private val cards = LinkedHashMap<String, View>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        dx = Dx(this)
        val root = FrameLayout(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(GamesColors.BG_TOP, GamesColors.BG))
            clipChildren = false
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false
            setPadding(dx.px(96), dx.px(64), dx.px(96), dx.px(48))
        }
        col.addView(dx.text(TextView(this).apply { text = "Jeux"; maxLines = 1 }, 72, GamesColors.TEXT_HIGH, true))
        col.addView(dx.text(TextView(this).apply { text = "Choisissez un jeu"; maxLines = 1 }, 32, GamesColors.TEXT_MEDIUM),
            LinearLayout.LayoutParams(-2, -2).apply { topMargin = dx.px(10) })

        val gap = dx.px(40)
        val avail = dx.width - 2 * dx.px(96)
        val n = Games.visible().size
        val cardW = if (n <= 3) (avail - gap * (n - 1)) / n else (avail - gap * 2) / 3
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false; clipToPadding = false; setPadding(dx.px(16), dx.px(24), dx.px(16), dx.px(24)) }
        Games.visible().forEachIndexed { i, g ->
            row.addView(card(g, cardW), LinearLayout.LayoutParams(cardW, dx.px(600)).apply { if (i > 0) leftMargin = gap })
        }
        col.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false; clipChildren = false; clipToPadding = false; addView(row)
        }, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dx.px(32) })
        col.addView(dx.text(TextView(this).apply { text = "OK : jouer     ·     Gauche / droite : choisir     ·     Retour : accueil"; maxLines = 1 }, 28, GamesColors.TEXT_MEDIUM))
        root.addView(col, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        val last = getSharedPreferences("castbridge_games", MODE_PRIVATE).getString("last", null)
        (cards[last] ?: cards.values.firstOrNull())?.requestFocus()
    }

    private fun card(g: GameDef, w: Int): View {
        val pad = dx.px(40)
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; isFocusable = true; isFocusableInTouchMode = true; isClickable = true
            setPadding(pad, pad, pad, pad)
            background = dx.focusable(GamesColors.SURFACE, GamesColors.SURFACE_HIGH, 32)
            gravity = Gravity.START
            if (g.soon) alpha = 0.7f                       // « bientôt » : visible mais pas jouable
            setOnClickListener {
                if (g.soon) { android.widget.Toast.makeText(this@GamesActivity, GameCatalog.SOON, android.widget.Toast.LENGTH_LONG).show(); return@setOnClickListener }
                getSharedPreferences("castbridge_games", MODE_PRIVATE).edit().putString("last", g.id).apply()
                Games.open(this@GamesActivity, g, "menu")
            }
            setOnFocusChangeListener { v, has ->
                v.animate().scaleX(if (has) 1.04f else 1f).scaleY(if (has) 1.04f else 1f).translationZ(if (has) dx.px(16).toFloat() else 0f).setDuration(150).start()
            }
        }
        val tile = FrameLayout(this).apply { background = dx.rounded((g.accent and 0xFFFFFF) or 0x33000000, 28) }
        tile.addView(ImageView(this).apply {
            setImageResource(g.icon); imageTintList = android.content.res.ColorStateList.valueOf(g.accent); scaleType = ImageView.ScaleType.FIT_CENTER
        }, FrameLayout.LayoutParams(dx.px(84), dx.px(84), Gravity.CENTER))
        c.addView(tile, LinearLayout.LayoutParams(dx.px(132), dx.px(132)))
        c.addView(dx.text(TextView(this).apply { text = g.name; maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END }, 44, GamesColors.TEXT_HIGH, true),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dx.px(28) })
        c.addView(dx.text(TextView(this).apply { text = g.modes; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }, 26, g.accent, true),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dx.px(10) })
        c.addView(dx.text(TextView(this).apply { text = g.blurb; maxLines = 3; ellipsize = android.text.TextUtils.TruncateAt.END; setLineSpacing(0f, 1.25f) }, 28, GamesColors.TEXT_MEDIUM),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dx.px(16) })
        c.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))
        val st = dx.text(TextView(this).apply {
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; gravity = Gravity.CENTER_VERTICAL
            setPadding(dx.px(24), dx.px(10), dx.px(24), dx.px(10)); background = dx.rounded(GamesColors.BG, 40, GamesColors.OUTLINE, 2)
        }, 26, GamesColors.TEXT_HIGH, true)
        statusViews[g.id] = st; cards[g.id] = c
        c.addView(st, LinearLayout.LayoutParams(-2, -2))
        return c
    }

    override fun onResume() {
        super.onResume()
        GamesHub.foreground = this
        Games.visible().forEach { g -> statusViews[g.id]?.text = "●  " + runCatching { g.status(this) }.getOrDefault("") }
    }

    override fun onPause() { if (GamesHub.foreground === this) GamesHub.foreground = null; super.onPause() }
}
