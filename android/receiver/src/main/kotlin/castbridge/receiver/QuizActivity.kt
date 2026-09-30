package castbridge.receiver

import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import castbridge.core.quiz.HighScores
import castbridge.core.quiz.Ladder
import castbridge.core.quiz.QrCode
import castbridge.core.quiz.QuestionFilter
import castbridge.core.quiz.QuizCatalog
import castbridge.core.quiz.QuizRoom
import castbridge.core.quiz.Track

/** One card of a setup screen. */
private data class Choice(val title: String, val sub: String?, val enabled: Boolean = true, val action: () -> Unit)

/**
 * « Quiz culture générale » on the TV screen, played with the remote (D-pad / OK / BACK) and the players' phones.
 * The TV hosts the room ([QuizHub], [QuizRoom]); this activity only draws the room's state and sends the host's
 * commands. Plain Android views and a few ValueAnimators: no game engine, no image asset (see docs/QUIZ.md).
 */
class QuizActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var root: FrameLayout
    private var room: QuizRoom? = null
    private var screen: Screen? = null
    private var renderPosted = false
    private lateinit var sound: QuizSound
    private lateinit var stage: StageBackground
    private var dialog: AlertDialog? = null
    /** State of the last render, and when it was taken (for the countdowns between renders). */
    private var state: Map<String, Any?> = emptyMap()
    private var stateAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        stage = StageBackground(this)
        root = FrameLayout(this)
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(QuizColors.BG_BOTTOM)
            addView(stage, FrameLayout.LayoutParams(MATCH, MATCH))
            addView(root, FrameLayout.LayoutParams(MATCH, MATCH))
        })
        sound = QuizSound(this)
        openRoom()
        main.post(clock)
    }

    private fun openRoom() {
        val r = QuizHub.open(this)
        r.onChange = { if (!renderPosted) { renderPosted = true; main.post { renderPosted = false; render() } } }
        room = r
        render()
    }

    override fun onNewIntent(intent: android.content.Intent?) { super.onNewIntent(intent); render() }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        dialog?.dismiss()
        room?.onChange = null
        QuizHub.close(room)
        sound.release()
        super.onDestroy()
    }

    private val clock = object : Runnable {
        override fun run() { runCatching { screen?.onTick() }; main.postDelayed(this, 200) }
    }

    private fun play(c: QuizSound.Clip) = sound.play(c)

    // ------------------------------------------------------------------ state access

    private fun Map<String, Any?>.m(k: String): Map<String, Any?>? = @Suppress("UNCHECKED_CAST") (this[k] as? Map<String, Any?>)
    private fun Map<String, Any?>.s(k: String): String? = this[k] as? String
    private fun Map<String, Any?>.i(k: String): Int? = (this[k] as? Number)?.toInt()
    private fun Map<String, Any?>.l(k: String): Long = (this[k] as? Number)?.toLong() ?: 0
    private fun Map<String, Any?>.b(k: String): Boolean = this[k] == true
    @Suppress("UNCHECKED_CAST") private fun Map<String, Any?>.list(k: String): List<Any?> = this[k] as? List<Any?> ?: emptyList()
    @Suppress("UNCHECKED_CAST") private fun Map<String, Any?>.maps(k: String): List<Map<String, Any?>> = list(k).mapNotNull { it as? Map<String, Any?> }

    /** Milliseconds left on a countdown given in the last state. */
    private fun left(ms: Long) = maxOf(0L, ms - (SystemClock.uptimeMillis() - stateAt))

    // ------------------------------------------------------------------ rendering

    private fun render() {
        val r = room ?: return
        state = r.view(null); stateAt = SystemClock.uptimeMillis()
        val s = state
        val stage = s.s("stage")
        val game = s.m("game"); val duel = s.m("duel")
        runCatching { stats(s, game, duel) }
        val key = when {
            stage == "CLOSED" -> "closed"
            steps.isNotEmpty() -> "setup-${steps.last()}"
            stage == "LOBBY" -> "lobby-${s.s("mode")}"
            game != null && game.s("phase") == "FINISHED" -> "m-end"
            game != null -> "m-${game.i("index")}"
            duel != null && duel.s("phase") == "FINISHED" -> "d-final"
            duel != null && duel.s("phase") == "BOARD" -> "d-board-${duel.i("index")}"
            duel != null -> "d-q-${duel.i("index")}"
            else -> "lobby-${s.s("mode")}"
        }
        if (screen?.key != key) {
            val next = when {
                key == "closed" -> ClosedScreen()
                key.startsWith("setup-") -> SetupScreen(key.removePrefix("setup-"))
                key.startsWith("lobby") -> LobbyScreen(key)
                key == "m-end" -> EndScreen()
                key.startsWith("m-") -> MillionaireScreen(key)
                key == "d-final" -> DuelFinalScreen()
                key.startsWith("d-board") -> BoardScreen(key)
                else -> DuelScreen(key)
            }
            screen = next
            root.removeAllViews()
            root.addView(next.view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            next.update(s)
            next.view.alpha = 0f; next.view.animate().alpha(1f).setDuration(250).start()
            next.focusFirst()
        } else screen?.update(s)
    }

    // ---- usage statistics (docs/TELEMETRY.md): quiz_game once per finished game, quiz_answer per revealed answer ----
    private var statGameAt = 0L                   // 0 = no game being followed
    private var statGameDone = false
    private var statQuestionId: String? = null
    private var statQuestionAt = 0L
    private val statAnswered = HashSet<String>()  // "questionId" already counted in this game

    private fun stats(s: Map<String, Any?>, game: Map<String, Any?>?, duel: Map<String, Any?>?) {
        val g = game ?: duel
        if (g == null) { statGameAt = 0; statGameDone = false; statAnswered.clear(); statQuestionId = null; return }
        val now = SystemClock.uptimeMillis()
        // a new game (first time, or « rejouer » straight after a finished one)
        if (statGameAt == 0L || (statGameDone && g.s("phase") != "FINISHED")) { statGameAt = now; statGameDone = false; statAnswered.clear() }
        val q = g.m("question")
        val qid = q?.s("id")
        if (qid != null && qid != statQuestionId) { statQuestionId = qid; statQuestionAt = now }
        val answer = q?.i("answer")
        if (qid != null && answer != null && statAnswered.add(qid)) {
            if (game != null) {
                val sel = game.i("selected")
                TvConnect.track("quiz_answer", mapOf("question" to qid, "correct" to (sel == answer), "ms" to (now - statQuestionAt)))
            } else duel!!.maps("ranking").forEach { p ->
                (p["correct"] as? Boolean)?.let { TvConnect.track("quiz_answer", mapOf("question" to qid, "correct" to it)) }
            }
        }
        if (!statGameDone && g.s("phase") == "FINISHED") {
            statGameDone = true
            val set = s.m("settings")
            val base = mapOf("track" to set?.s("track"), "level" to set?.s("level"), "field" to set?.s("field"), "ms" to (now - statGameAt))
            if (game != null) TvConnect.track("quiz_game", base + mapOf(
                "mode" to if (game.b("practice")) "practice" else "millionaire",
                "players" to maxOf(1, s.maps("players").size), "score" to game.l("winnings"), "jokers" to game.list("jokersUsed").size))
            else TvConnect.track("quiz_game", base + mapOf(
                "mode" to "duel", "duel" to duel!!.s("format")?.lowercase(), "players" to s.maps("players").size,
                "score" to (duel.maps("ranking").maxOfOrNull { (it["score"] as? Number)?.toLong() ?: 0L } ?: 0L)))
        }
    }

    private abstract inner class Screen(val key: String) {
        abstract val view: View
        abstract fun update(s: Map<String, Any?>)
        open fun onTick() {}
        open fun focusFirst() {}
    }

    private fun column(gravity: Int = Gravity.CENTER): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; this.gravity = gravity; clipChildren = false; clipToPadding = false
    }
    private fun row(gravity: Int = Gravity.CENTER_VERTICAL): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; this.gravity = gravity; clipChildren = false; clipToPadding = false
    }
    private fun lp(w: Int = ViewGroup.LayoutParams.WRAP_CONTENT, h: Int = ViewGroup.LayoutParams.WRAP_CONTENT, weight: Float = 0f,
                   l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0) =
        LinearLayout.LayoutParams(w, h, weight).apply { setMargins(dpi(l), dpi(t), dpi(r), dpi(b)) }
    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT

    private fun confirm(title: String, message: String, yes: String, action: () -> Unit) {
        dialog?.dismiss()
        dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(title).setMessage(message)
            .setPositiveButton(yes) { _, _ -> action() }
            .setNegativeButton("Annuler", null)
            .show().also { d -> d.getButton(AlertDialog.BUTTON_NEGATIVE)?.requestFocus() }
    }

    private fun choose(title: String, items: List<String>, action: (Int) -> Unit) {
        dialog?.dismiss()
        dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(title).setItems(items.toTypedArray()) { _, i -> action(i) }
            .setNegativeButton("Annuler", null).show()
    }

    // ------------------------------------------------------------------ setup: Mode → Parcours → Niveau → Filière → Format / Mise

    /** Setup steps shown before the waiting room; empty = waiting room or game. */
    private val steps = arrayListOf("home")
    private var play = QuizRoom.Play.FRIENDS
    private var track = Track.GENERAL
    private var level: String? = null
    private var field: String? = null

    private fun push(step: String) { steps += step; render() }
    private fun goHome() { room?.backToLobby(); steps.clear(); steps += "home"; stage.calm = false; render() }
    private fun count(f: QuestionFilter) = room?.bank?.count(f) ?: 0
    private fun afterFilter() = push(if (play == QuizRoom.Play.STAKE) "stake" else "format")
    private var duelFormat = castbridge.core.quiz.QuizDuel.Format.CLASSIC
    private var stakeChosen = 100L
    private fun launch(mode: QuizRoom.Mode, stake: Long = 100) {
        room?.configure(mode, play, QuestionFilter(track, level, field), stake, duelFormat)
        steps.clear(); render()
    }

    /** Solo at the remote: no lobby, no phone, no network needed; public and friend are simulated. */
    private fun launchSolo() {
        val r = room ?: return
        r.configure(QuizRoom.Mode.MILLIONAIRE, play, QuestionFilter(track, level, field))
        r.setCandidate(null)
        val why = r.startGame()
        steps.clear(); render()
        if (why != null) {
            TvConnect.error("quiz", "start", why)
            android.widget.Toast.makeText(this, why, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    // ---- best scores (solo against oneself), kept in the app's preferences ----
    private val scorePrefs by lazy { getSharedPreferences("castbridge_quiz", MODE_PRIVATE) }
    private val scores by lazy { HighScores.fromJson(scorePrefs.getString("highscores", null)) }
    private fun saveScores() { runCatching { scorePrefs.edit().putString("highscores", scores.toJson()).apply() } }
    private fun board(s: Map<String, Any?>, practice: Boolean): String =
        (if (practice) "Entraînement" else "Millionnaire") + " · " + (s.m("settings")?.s("label") ?: "Culture générale")

    private inner class SetupScreen(val step: String) : Screen("setup-$step") {
        private val footer = quizText(this@QuizActivity, "", 17f, QuizColors.MUTED).apply { gravity = Gravity.CENTER }
        private var first: View? = null
        override val view: View

        init {
            val (title, sub, choices) = content()
            val compact = choices.size > 5
            view = column().apply {
                setPadding(dpi(40), dpi(20), dpi(40), dpi(16))
                if (step == "home") {
                    // the wordmark of the sub-brand (branding/logo/quiz-des-millions-horizontal) replaces the text title
                    addView(TvStyle.logo(context, R.drawable.logo_quiz_des_millions_horizontal, 84).apply { contentDescription = title; scaleType = android.widget.ImageView.ScaleType.FIT_CENTER },
                        lp(h = dpi(84)))
                } else addView(quizText(context, title, 36f, QuizColors.GOLD, true).apply { gravity = Gravity.CENTER }, lp())
                if (sub != null) addView(quizText(context, sub, 21f, QuizColors.TEXT).apply { gravity = Gravity.CENTER }, lp(t = 6, b = 18))
                val perRow = if (compact) 6 else if (step == "home" || choices.size == 4) 2 else 1
                var line: LinearLayout? = null
                choices.forEachIndexed { i, c ->
                    if (i % perRow == 0) line = row(Gravity.CENTER).also { addView(it, lp(t = 6, b = 6)) }
                    val card = choiceCard(context, c.title, c.sub, c.enabled) {
                        if (c.enabled) { play(QuizSound.Clip.SELECT); c.action() }
                        else { play(QuizSound.Clip.WRONG); footer.text = "« ${c.title} » : pas encore de questions. Elles arriveront avec les prochaines mises à jour." }
                    }
                    if (compact) { card.minWidth = dpi(120); card.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 20f) }
                    line!!.addView(card, lp(l = 8, r = 8))
                    if (first == null && c.enabled) first = card
                }
                addView(footer, lp(t = 14))
                // cards slide in one after the other
                for (k in 0 until childCount) getChildAt(k).apply { alpha = 0f; translationY = dp(24f); animate().alpha(1f).translationY(0f).setStartDelay(60L * k).setDuration(300).start() }
            }
        }

        private fun content(): Triple<String, String?, List<Choice>> = when (step) {
            "home" -> Triple("◆  LE QUIZ DES MILLIONS  ◆", "Culture générale, école et université : à vous de jouer !", listOf(
                Choice("Compétition entre amis", "Gratuit, pour le plaisir") { play = QuizRoom.Play.FRIENDS; push("track") },
                Choice("Compétition avec mise", "${QuizRoom.TOKENS_LABEL} : aucun argent réel") { play = QuizRoom.Play.STAKE; push("track") },
                Choice("Entraînement", "Sans enjeu ni chrono, avec les explications") { play = QuizRoom.Play.PRACTICE; push("track") },
                Choice("Meilleurs scores", "Vos records sur cette TV") { push("scores") },
                Choice("Quitter", null) { finish() }))
            "scores" -> Triple("Meilleurs scores", "Sur cette TV, en solo comme à plusieurs", scores.boards().take(4).map { b ->
                val t = scores.top(b, 3)
                Choice(b, t.mapIndexed { i, e -> "${i + 1}. ${e.name}  ${e.detail}" }.joinToString("\n")) { }
            }.ifEmpty { listOf(Choice("Pas encore de score", "Jouez une partie pour inscrire le premier record !") { goHome() }) })
            "track" -> Triple("Quel parcours ?", play.label, listOf(
                Choice(Track.GENERAL.label, "70 % Cameroun · 20 % Afrique · 10 % Monde") { pickTrack(Track.GENERAL) },
                Choice(Track.PRIMARY.label, "Du SIL au CM2 · Class 1 à 6") { pickTrack(Track.PRIMARY) },
                Choice(Track.SECONDARY.label, "De la 6e à la Terminale · Form 1 à Upper Sixth") { pickTrack(Track.SECONDARY) },
                Choice(Track.HIGHER.label, "Licence 1 à 3, par filière") { pickTrack(Track.HIGHER) }))
            "level" -> Triple("Quel niveau ?", track.label, QuizCatalog.levels(track).map { l ->
                val n = count(QuestionFilter(track, l.key))
                Choice(l.label, if (n > 0) "$n questions" else "bientôt", n > 0) { level = l.key; if (track == Track.HIGHER) push("field") else afterFilter() }
            })
            "field" -> Triple("Quelle filière ?", "${track.label} · ${level ?: ""}", QuizCatalog.fields.map { f ->
                val n = count(QuestionFilter(track, level, f.key))
                Choice(f.label, if (n > 0) "$n questions" else "bientôt", n > 0) { field = f.key; afterFilter() }
            })
            "stake" -> Triple("Quelle mise par joueur ?", "${QuizRoom.TOKENS_LABEL} : sans aucune valeur, rien à payer. La cagnotte est partagée selon le classement.",
                listOf(50L, 100L, 200L).map { m -> Choice("$m jetons", if (m == 100L) "conseillé" else null) { stakeChosen = m; push("duel-format") } })
            "duel-format" -> Triple("Quel format de duel ?", "Réponse en 20 secondes au plus", castbridge.core.quiz.QuizDuel.Format.values().map { f ->
                Choice(f.label, f.description) { duelFormat = f; push("duel-time") }
            })
            "duel-time" -> Triple("Temps pour répondre", duelFormat.label, castbridge.core.quiz.QuizDuel.WINDOWS.map { sec ->
                Choice("$sec secondes", when (sec) { 5 -> "Réflexes"; 10 -> "Rapide"; 20 -> "Standard (maximum)"; else -> null }) {
                    room?.configure(QuizRoom.Mode.DUEL, play, QuestionFilter(track, level, field), stakeChosen, duelFormat, sec)
                    steps.clear(); render()
                }
            })
            else -> Triple("Comment jouer ?", QuestionFilter(track, level, field).label, listOf(
                Choice("Seul, à la télécommande", if (play == QuizRoom.Play.PRACTICE) "Question après question, avec les explications"
                    else "15 questions, jokers simulés : battez votre record") { launchSolo() },
                Choice("Millionnaire avec le public", "Un candidat ; les autres aident depuis leur téléphone") { launch(QuizRoom.Mode.MILLIONAIRE) },
                Choice("Duel", "Tout le monde répond sur son téléphone : au plus rapide, 20 s maximum") { push("duel-format") }))
        }

        private fun pickTrack(t: Track) {
            track = t; level = null; field = null
            if (t == Track.GENERAL) afterFilter() else push("level")
        }

        override fun update(s: Map<String, Any?>) {
            val r = room ?: return
            if (footer.text.isNullOrEmpty() || !footer.text.startsWith("«"))
                footer.text = QuizHub.joinUrl(r)?.let { "Les joueurs peuvent déjà rejoindre avec leur téléphone  ·  code ${r.code}" }
                    ?: "Pas de réseau : jeu à la télécommande seulement"
        }
        override fun focusFirst() { first?.requestFocus() }
    }

    // ------------------------------------------------------------------ lobby

    private inner class LobbyScreen(key: String) : Screen(key) {
        private val duel = key.endsWith("DUEL")
        private val qr = QrView(this@QuizActivity)
        private val urlText = quizText(this@QuizActivity, "", 17f, QuizColors.MUTED).apply { gravity = Gravity.CENTER }
        private val codeText = quizText(this@QuizActivity, "", 60f, QuizColors.GOLD, true).apply { gravity = Gravity.CENTER; letterSpacing = 0.3f }
        private val count = quizText(this@QuizActivity, "", 22f, QuizColors.TEXT, true)
        private val list = column(Gravity.TOP or Gravity.START)
        private val candidateBtn = quizButton(this@QuizActivity, "", 19f) { pickCandidate() }
        private val startBtn = quizButton(this@QuizActivity, "Lancer la partie", 22f) { startGame() }
        private val note = quizText(this@QuizActivity, "", 17f, QuizColors.MUTED)
        private val settings = quizText(this@QuizActivity, "", 19f, QuizColors.TEXT, true)
        private val badge = quizText(this@QuizActivity, "", 17f, QuizColors.BG_BOTTOM, true).apply {
            background = panelBackground(this@QuizActivity, QuizColors.GOLD, QuizColors.GOLD); setPadding(dpi(14), dpi(4), dpi(14), dpi(4)); visibility = View.GONE
        }
        private val shown = HashSet<String>()
        private var players: List<Map<String, Any?>> = emptyList()

        override val view: View = row(Gravity.CENTER).apply {
            setPadding(dpi(32), dpi(20), dpi(32), dpi(20))
            addView(column().apply {
                background = panelBackground(context)
                setPadding(dpi(20), dpi(16), dpi(20), dpi(16))
                addView(quizText(context, "Scannez pour jouer", 24f, QuizColors.TEXT, true).apply { gravity = Gravity.CENTER }, lp())
                addView(qr, lp(dpi(230), dpi(230), t = 10, b = 4))
                addView(quizText(context, "avec l'appareil photo du téléphone (même Wi-Fi que la TV), ou ouvrez :", 15f, QuizColors.MUTED).apply { gravity = Gravity.CENTER }, lp())
                addView(urlText, lp())
                addView(quizText(context, "Code de la salle", 18f, QuizColors.MUTED), lp(t = 8))
                addView(codeText, lp())
            }, lp(0, MATCH, 1f, r = 24))
            addView(column(Gravity.TOP or Gravity.START).apply {
                addView(quizText(context, if (duel) "DUEL EN SALLE" else "MILLIONNAIRE", 30f, QuizColors.GOLD, true), lp())
                addView(quizText(context, if (duel) "Tout le monde répond en même temps sur son téléphone. Juste ET rapide = plus de points (jusqu'à 1000)."
                    else "Le candidat répond à 15 questions de plus en plus difficiles. Les autres joueurs forment le public et peuvent être appelés comme ami.",
                    18f, QuizColors.TEXT), lp(t = 6, b = 6))
                addView(settings, lp(b = 4))
                addView(badge, lp(b = 8))
                addView(count, lp(b = 6))
                addView(list, lp(MATCH, 0, 1f))
                if (!duel) addView(candidateBtn, lp(t = 8, b = 8))
                addView(row().apply {
                    addView(startBtn, lp(r = 16))
                    addView(quizButton(context, "Changer de jeu", 19f) { goHome() }, lp())
                }, lp(t = 4))
                addView(note, lp(t = 8))
            }, lp(0, MATCH, 1.25f))
        }

        override fun update(s: Map<String, Any?>) {
            val r = room ?: return
            val url = QuizHub.joinUrl(r)
            if (qr.code == null && url != null) qr.code = runCatching { QrCode.encode(url, QrCode.Ecl.M) }.getOrNull()
            urlText.text = url?.substringBefore("?") ?: "Pas de réseau Wi-Fi : le téléphone ne peut pas rejoindre"
            codeText.text = r.code
            val st = s.m("settings")
            settings.text = listOfNotNull(st?.s("playLabel"), st?.s("label")).joinToString("  ·  ")
            val stake = st?.i("stake") ?: 0
            badge.visibility = if (stake > 0) View.VISIBLE else View.GONE
            badge.text = "Mise : $stake jetons par joueur  —  ${QuizRoom.TOKENS_LABEL}"
            players = s.maps("players")
            count.text = "Joueurs : ${players.size} / ${s.i("maxPlayers")}"
            list.removeAllViews()
            if (players.isEmpty()) list.addView(quizText(this@QuizActivity, "En attente des joueurs…", 20f, QuizColors.MUTED), lp())
            val cand = s.m("candidate")?.s("id")
            for (p in players) {
                val id = p.s("id") ?: continue
                val t = quizText(this@QuizActivity, (if (p.b("connected")) "●  " else "○  ") + p.s("name") + (if (id == cand) "   — candidat" else ""), 22f,
                    if (p.b("connected")) QuizColors.TEXT else QuizColors.MUTED, id == cand)
                list.addView(t, lp(t = 3, b = 3))
                if (shown.add(id)) {                          // arrival animation + a little chime
                    t.alpha = 0f; t.translationX = dp(60f)
                    t.animate().alpha(1f).translationX(0f).setDuration(450).setInterpolator(DecelerateInterpolator()).start()
                    play(QuizSound.Clip.JOIN)
                }
            }
            candidateBtn.text = "Candidat : " + (players.firstOrNull { it.s("id") == cand }?.s("name")?.let { "$it (sur son téléphone)" } ?: "la TV (télécommande)")
            note.text = if (duel && players.isEmpty()) "Il faut au moins un joueur connecté pour lancer le duel." else
                if (duel) "OK sur « Lancer la partie » quand tout le monde est là." else "Sans joueur, le public et l'ami sont simulés."
        }

        override fun focusFirst() { startBtn.requestFocus() }

        private fun pickCandidate() {
            val names = listOf("La TV (télécommande)") + players.map { it.s("name").orEmpty() }
            choose("Qui est le candidat ?", names) { i -> room?.setCandidate(if (i == 0) null else players[i - 1].s("id")) }
        }

        private fun startGame() {
            val r = room ?: return
            r.startGame()?.let { why -> play(QuizSound.Clip.WRONG); note.text = why }
        }
    }

    // ------------------------------------------------------------------ Millionaire

    private inner class MillionaireScreen(key: String) : Screen(key) {
        private val plates = Array(4) { i -> Plate(this@QuizActivity, 22f, true).apply { id = View.generateViewId(); setOnClickListener { onPlate(i) } } }
        private val questionPlate = Plate(this@QuizActivity, 26f, false).apply { gravity = Gravity.CENTER; maxLines = 4 }
        private val info = quizText(this@QuizActivity, "", 19f, QuizColors.TEXT).apply { gravity = Gravity.CENTER; maxLines = 3 }
        private val bars = BarsView(this@QuizActivity).apply { visibility = View.GONE }
        private val ring = RingView(this@QuizActivity)
        private val header = quizText(this@QuizActivity, "", 18f, QuizColors.MUTED).apply { gravity = Gravity.END; maxLines = 2 }
        private val prize = quizText(this@QuizActivity, "", 30f, QuizColors.GOLD, true).apply { gravity = Gravity.CENTER }
        private val jokers = listOf("50:50" to "fifty", "Public" to "audience", "Ami" to "phone").map { (label, action) ->
            quizButton(this@QuizActivity, label, 18f) { onJoker(action) }.apply { id = View.generateViewId(); tag = action }
        }
        private val stop = quizButton(this@QuizActivity, "S'arrêter", 18f) { onWalk() }.apply { id = View.generateViewId() }
        private inner class LadderRow(val box: LinearLayout, val num: TextView, val amount: TextView)
        private val ladderRows = ArrayList<LadderRow>()
        private val confirmBox = column().apply { visibility = View.GONE; background = panelBackground(this@QuizActivity, 0xF00B1850.toInt(), QuizColors.GOLD); setPadding(dpi(28), dpi(18), dpi(28), dpi(18)) }
        private val yes = quizButton(this@QuizActivity, "Oui, dernier mot", 22f) { room?.hostAct("confirm") }
        private val no = quizButton(this@QuizActivity, "Non", 22f) { room?.hostAct("cancel") }
        private val confirmText = quizText(this@QuizActivity, "", 26f, QuizColors.TEXT, true).apply { gravity = Gravity.CENTER }
        private val next = quizButton(this@QuizActivity, "Continuer", 22f) { room?.hostAct("next") }.apply { visibility = View.GONE }
        private var lastPhase: String? = null
        private var remaining = 0L
        private var total = 0L
        private var audienceShown = false
        private var game: Map<String, Any?> = emptyMap()

        override val view: View = FrameLayout(this@QuizActivity).apply {
            addView(row(Gravity.TOP).apply {
                setPadding(dpi(24), dpi(14), dpi(16), dpi(14))
                addView(column(Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                    addView(row().apply {
                        jokers.forEach { addView(it, lp(r = 10)) }
                        addView(stop, lp(r = 10))
                        addView(header, lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f, l = 8, r = 12))
                        addView(ring, lp(dpi(64), dpi(64)))
                    }, lp(MATCH))
                    addView(FrameLayout(context).apply {
                        addView(bars, FrameLayout.LayoutParams(dpi(360), MATCH, Gravity.CENTER))
                        addView(info, FrameLayout.LayoutParams(MATCH, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
                        addView(prize, FrameLayout.LayoutParams(MATCH, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
                    }, lp(MATCH, 0, 1f, t = 6, b = 6))
                    addView(questionPlate, lp(MATCH, dpi(118), b = 8))
                    addView(row().apply {
                        addView(plates[0], lp(0, dpi(78), 1f)); addView(plates[1], lp(0, dpi(78), 1f))
                    }, lp(MATCH))
                    addView(row().apply {
                        addView(plates[2], lp(0, dpi(78), 1f)); addView(plates[3], lp(0, dpi(78), 1f))
                    }, lp(MATCH, t = 4))
                    addView(next, lp(t = 8))
                }, lp(0, MATCH, 1f, r = 16))
                addView(column(Gravity.CENTER).apply {
                    background = panelBackground(context, 0xCC060F3A.toInt())
                    setPadding(dpi(10), dpi(8), dpi(10), dpi(8))
                    for (lvl in 15 downTo 1) {
                        val num = quizText(context, "$lvl", 16f, QuizColors.GOLD).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL }
                        val amount = quizText(context, "", 16f, QuizColors.GOLD).apply { gravity = Gravity.START or Gravity.CENTER_VERTICAL }
                        val box = row().apply {
                            setPadding(dpi(6), 0, dpi(10), 0)
                            addView(num, lp(dpi(30), MATCH)); addView(View(context), lp(dpi(8), 1)); addView(amount, lp(0, MATCH, 1f))
                        }
                        ladderRows += LadderRow(box, num, amount)
                        addView(box, lp(MATCH, 0, 1f))
                    }
                    ladderRows.reverse()                     // ladderRows[0] = level 1
                }, lp(dpi(230), MATCH))
            }, FrameLayout.LayoutParams(MATCH, MATCH))
            confirmBox.addView(confirmText, lp(b = 14))
            confirmBox.addView(row().apply { addView(yes, lp(r = 18)); addView(no, lp()) }, lp())
            addView(confirmBox, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
            wireFocus()
        }

        /** Explicit D-pad paths: jokers row ⇄ answers grid (2×2), nothing lost off-screen. */
        private fun wireFocus() {
            val top = jokers + stop
            top.forEachIndexed { i, v ->
                v.nextFocusDownId = plates[if (i < 2) 0 else 1].id
                if (i > 0) v.nextFocusLeftId = top[i - 1].id
                if (i < top.size - 1) v.nextFocusRightId = top[i + 1].id
            }
            plates[0].nextFocusRightId = plates[1].id; plates[1].nextFocusLeftId = plates[0].id
            plates[2].nextFocusRightId = plates[3].id; plates[3].nextFocusLeftId = plates[2].id
            plates[0].nextFocusDownId = plates[2].id; plates[1].nextFocusDownId = plates[3].id
            plates[2].nextFocusUpId = plates[0].id; plates[3].nextFocusUpId = plates[1].id
            plates[0].nextFocusUpId = jokers[0].id; plates[1].nextFocusUpId = stop.id
        }

        override fun update(s: Map<String, Any?>) {
            val g = s.m("game") ?: return
            game = g
            val q = g.m("question") ?: return
            val phase = g.s("phase")
            val index = g.i("index") ?: 0
            val choices = q.list("choices").map { it.toString() }
            val removed = g.list("removed").mapNotNull { (it as? Number)?.toInt() }.toSet()
            val sel = g.i("selected")
            val answer = q.i("answer")
            val revealed = phase == "REVEALED"
            header.text = (if (g.b("practice")) "Entraînement  ·  ${g.i("correct")} bonne(s) réponse(s)\n" else "") +
                "Question ${index + 1}/${g.i("levels")}  ·  ${q.s("category")}"
            val ladderAmounts = g.list("ladder").mapNotNull { (it as? Number)?.toLong() }
            prize.text = if (g.b("practice")) "" else "Question pour ${Ladder.fcfa(ladderAmounts.getOrElse(index) { 0 })}"
            questionPlate.text = fr(q.s("text"))
            for (i in 0..3) {
                val p = plates[i]
                val gone = i in removed
                val t = SpannableStringBuilder()
                if (!gone) {
                    t.append("◆ ${QuizColors.LETTERS[i]} :  ", ForegroundColorSpan(QuizColors.GOLD), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    t.append(fr(choices.getOrElse(i) { "" }))
                    if (revealed && i == answer) t.append("   ✓")
                    if (revealed && i == sel && i != answer) t.append("   ✗")
                }
                p.text = t
                p.hex.look = when {
                    gone -> HexDrawable.Look.HIDDEN
                    revealed && i == answer -> HexDrawable.Look.RIGHT
                    revealed && i == sel -> HexDrawable.Look.WRONG
                    revealed -> HexDrawable.Look.DIM
                    (phase == "CONFIRM" || phase == "LOCKED") && i == sel -> HexDrawable.Look.SELECTED
                    else -> HexDrawable.Look.NORMAL
                }
                p.isFocusable = !gone && phase == "QUESTION"
            }
            // jokers
            val used = g.list("jokersUsed").map { it.toString() }
            val jokerNames = mapOf("fifty" to "FIFTY", "audience" to "AUDIENCE", "phone" to "PHONE")
            for (j in jokers) {
                val u = jokerNames[j.tag] in used
                val label = when (j.tag) { "fifty" -> "50:50"; "audience" -> "Public"; else -> "Ami" }
                j.text = if (u) SpannableStringBuilder(label).apply { setSpan(StrikethroughSpan(), 0, length, 0) } else label
                j.alpha = if (u) 0.4f else 1f
                j.isFocusable = !u && phase == "QUESTION"
            }
            stop.isFocusable = phase == "QUESTION"
            // ladder
            val ladder = g.list("ladder").mapNotNull { (it as? Number)?.toLong() }
            val safe = g.list("safeLevels").mapNotNull { (it as? Number)?.toInt() }.toSet()
            val reached = g.i("reached") ?: 0
            val practice = g.b("practice")
            ladderRows.forEachIndexed { i, r ->
                val lvl = i + 1
                r.amount.text = if (practice) "question" else Ladder.fcfa(ladder.getOrElse(i) { 0 }).removeSuffix("\u00A0FCFA")
                val current = lvl == index + 1
                val color = when { current -> QuizColors.BG_BOTTOM; lvl in safe && !practice -> QuizColors.TEXT; lvl <= reached -> QuizColors.MUTED; else -> QuizColors.GOLD }
                val face = if ((lvl in safe && !practice) || current) TvFonts.quizBold else TvFonts.quiz
                for (t in listOf(r.num, r.amount)) { t.setTextColor(color); t.typeface = face }
                r.box.background = if (current) panelBackground(this@QuizActivity, if (revealed && g.b("lastCorrect")) QuizColors.RIGHT else QuizColors.SELECTED, QuizColors.GOLD) else null
            }
            // clock
            remaining = g.l("remainingMs")
            if (phase != lastPhase && phase == "QUESTION") total = maxOf(remaining, 1)
            ring.visibility = if (remaining > 0 && (phase == "QUESTION" || phase == "CONFIRM" || phase == "JOKER")) View.VISIBLE else View.INVISIBLE
            // info zone: joker in progress / audience / phone / explanation
            val vote = s.m("vote"); val call = s.m("call")
            val audience = g.list("audience").mapNotNull { (it as? Number)?.toInt() }
            val phone = g.m("phone")
            bars.visibility = if (audience.size == 4 && !revealed) View.VISIBLE else View.GONE
            if (audience.size == 4 && !audienceShown) {
                audienceShown = true
                bars.values = audience.toIntArray()
                ValueAnimator.ofFloat(0f, 1f).apply { duration = 1200; addUpdateListener { bars.progress = it.animatedValue as Float } }.start()
            }
            info.text = when {
                revealed -> (if (g.b("lastCorrect")) "Bonne réponse ! " else "Hélas… ") + fr(q.s("explanation"))
                phase == "JOKER" && vote != null -> "Le public vote sur les téléphones…  ${vote.i("count")}/${vote.i("expected")}"
                phase == "JOKER" && call != null -> "Appel à ${call.s("friend")}… il répond sur son téléphone."
                phone != null -> {
                    val who = phone.s("friend") ?: "L'ami virtuel"
                    val missed = s.s("callMissed")?.let { "$it n'a pas répondu à temps. " } ?: ""
                    missed + "$who : « Je dirais ${QuizColors.LETTERS[phone.i("choice") ?: 0]}" +
                        ((phone.i("confidence") ?: 0).takeIf { it > 0 }?.let { ", j'en suis sûr à $it %" } ?: "") + ". »"
                }
                audience.size == 4 -> ""
                phase == "LOCKED" -> "Réponse verrouillée…"
                else -> ""
            }
            info.setTextColor(if (revealed) (if (g.b("lastCorrect")) 0xFF7CF0A8.toInt() else 0xFFFF9C9C.toInt()) else QuizColors.TEXT)
            if (audience.size == 4 && !revealed && phone == null) info.visibility = View.GONE else info.visibility = View.VISIBLE
            prize.visibility = if (info.text.isNullOrEmpty() && bars.visibility != View.VISIBLE && phase == "QUESTION") View.VISIBLE else View.GONE
            if (audience.size == 4 && phone != null && !revealed) bars.visibility = View.GONE
            // confirm overlay
            confirmBox.visibility = if (phase == "CONFIRM") View.VISIBLE else View.GONE
            if (phase == "CONFIRM" && sel != null) confirmText.text = "C'est votre dernier mot ?\n${QuizColors.LETTERS[sel]} : ${choices.getOrElse(sel) { "" }}"
            next.visibility = if (revealed) View.VISIBLE else View.GONE
            next.text = if (revealed && g.b("lastCorrect") && index + 1 < (g.i("levels") ?: 15)) "Question suivante" else "Continuer"
            // focus + sound on phase changes
            if (phase != lastPhase) {
                when (phase) {
                    "QUESTION" -> { if (lastPhase == null || lastPhase == "CONFIRM" || lastPhase == "JOKER" || !anyFocused()) firstPlate()?.requestFocus() }
                    "CONFIRM" -> { yes.requestFocus(); play(QuizSound.Clip.SELECT) }
                    "LOCKED" -> { play(QuizSound.Clip.LOCK); stage.calm = true }
                    "REVEALED" -> { next.requestFocus(); val ok = g.b("lastCorrect"); play(if (ok) QuizSound.Clip.RIGHT else QuizSound.Clip.WRONG); stage.flash = if (ok) QuizColors.RIGHT else QuizColors.WRONG }
                }
                if (phase == "JOKER") play(QuizSound.Clip.NEXT)
                lastPhase = phase
            }
            if (phase == "QUESTION" && !anyFocused()) firstPlate()?.requestFocus()
            onTick()
        }

        private fun anyFocused() = view.findFocus() != null
        private fun firstPlate() = plates.firstOrNull { it.isFocusable }

        override fun onTick() {
            val left = left(remaining)
            ring.fraction = if (total > 0) left.toFloat() / total else 0f
            ring.label = ((left + 999) / 1000).toString()
            val secs = ((left + 999) / 1000).toInt()
            if (ring.visibility == View.VISIBLE && secs in 1..5 && ring.tag != secs && game.s("phase") == "QUESTION") { ring.tag = secs; play(QuizSound.Clip.TICK) }
        }

        override fun focusFirst() { firstPlate()?.requestFocus() }

        private fun onPlate(i: Int) { if (game.s("phase") == "QUESTION") room?.hostAct("select", i) }

        private fun onJoker(action: String) {
            val r = room ?: return
            if (action != "phone") { r.hostAct(action); return }
            val cand = state.m("candidate")?.s("id")
            val friends = state.maps("players").filter { it.b("connected") && it.s("id") != cand }
            if (friends.isEmpty()) { r.hostAct("phone", arg = ""); return }
            choose("Appeler quel ami ? (30 s pour répondre sur son téléphone)", friends.map { it.s("name").orEmpty() } + "Ami virtuel (simulé)") { i ->
                r.hostAct("phone", arg = friends.getOrNull(i)?.s("id") ?: "")
            }
        }

        private fun onWalk() {
            val index = game.i("index") ?: 0
            val keep = game.list("ladder").getOrNull(index - 1)?.let { (it as Number).toLong() } ?: 0
            confirm("S'arrêter ?", "Vous repartez avec ${Ladder.fcfa(keep)}.", "M'arrêter") { room?.hostAct("walk") }
        }
    }

    // ------------------------------------------------------------------ end of a Millionaire game

    private inner class EndScreen : Screen("m-end") {
        private val title = quizText(this@QuizActivity, "", 34f, QuizColors.TEXT, true).apply { gravity = Gravity.CENTER }
        private val amount = quizText(this@QuizActivity, "", 58f, QuizColors.GOLD, true).apply { gravity = Gravity.CENTER }
        private val detail = quizText(this@QuizActivity, "", 19f, QuizColors.MUTED).apply { gravity = Gravity.CENTER }
        private val again = quizButton(this@QuizActivity, "Rejouer") { replay() }
        private val best = quizText(this@QuizActivity, "", 18f, QuizColors.TEXT).apply { gravity = Gravity.CENTER }
        private var animated = false
        private var recorded = false
        override val view: View = column().apply {
            setPadding(dpi(40), dpi(16), dpi(40), dpi(16))
            addView(title, lp()); addView(amount, lp(t = 10, b = 10)); addView(detail, lp(b = 10)); addView(best, lp(b = 16))
            addView(row().apply {
                addView(again, lp(r = 16))
                addView(quizButton(context, "Salle d'attente") { room?.backToLobby() }, lp(r = 16))
                addView(quizButton(context, "Menu du quiz") { goHome() }, lp())
            }, lp())
        }
        override fun update(s: Map<String, Any?>) {
            val g = s.m("game") ?: return
            val q = g.m("question")
            val who = s.m("candidate")?.s("name")
            title.text = when (g.s("end")) {
                "WON" -> "INCROYABLE ! ${who ?: "Vous"} remporte${if (who == null) "z" else ""} le gros lot"
                "WALKED" -> "${who ?: "Vous"} s'arrête${if (who == null) "z" else ""} avec"
                "TIMEOUT" -> "Temps écoulé ! ${who ?: "Vous"} repart${if (who == null) "ez" else ""} avec"
                else -> "Dommage ! ${who ?: "Vous"} repart${if (who == null) "ez" else ""} avec"
            }
            val win = g.l("winnings")
            if (!recorded) {
                recorded = true
                val practice = g.b("practice")
                val b = board(s, practice)
                val e = HighScores.Entry(b, who ?: "Joueur TV", if (practice) (g.l("correct")) else win,
                    if (practice) "${g.i("correct")}/${g.i("levels")}" else Ladder.fcfa(win), System.currentTimeMillis())
                val rank = if (e.score > 0) scores.add(e).also { saveScores() } else null
                best.text = (if (rank == 1) "★ NOUVEAU RECORD ! ★\n" else rank?.let { "${it}e meilleur score !\n" } ?: "") +
                    "Meilleurs scores · $b\n" + scores.top(b, 3).mapIndexed { i, x -> "${i + 1}. ${x.name} — ${x.detail}" }.joinToString("\n")
                if (rank == 1) { best.setTextColor(QuizColors.GOLD); stage.flash = QuizColors.GOLD }
            }
            if (g.b("practice")) {
                title.text = "Entraînement terminé"
                amount.text = "${g.i("correct")} / ${g.i("levels")} bonnes réponses"
                if (!animated) { animated = true; play(QuizSound.Clip.WIN) }
                detail.text = "Relancez pour de nouvelles questions : celles déjà vues ne reviennent pas tout de suite."
                return
            }
            if (!animated) {
                animated = true
                play(if (g.s("end") == "WON" || g.b("practice")) QuizSound.Clip.WIN else QuizSound.Clip.NEXT)
                ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 1500; interpolator = DecelerateInterpolator()
                    addUpdateListener { amount.text = Ladder.fcfa((win * (it.animatedValue as Float)).toLong()) }
                }.start()
            }
            val ans = q?.i("answer")
            detail.text = if (q != null && ans != null)
                "Gains fictifs, pour le jeu.\nDernière question : ${q.s("text")}\nBonne réponse : ${QuizColors.LETTERS[ans]} — ${q.list("choices").getOrNull(ans)}" else "Gains fictifs, pour le jeu."
        }
        override fun focusFirst() { again.requestFocus() }
    }

    // ------------------------------------------------------------------ Duel: question + reveal

    private inner class DuelScreen(key: String) : Screen(key) {
        private val plates = Array(4) { Plate(this@QuizActivity, 22f, false) }
        private val questionPlate = Plate(this@QuizActivity, 26f, false).apply { gravity = Gravity.CENTER; maxLines = 4 }
        private val ring = RingView(this@QuizActivity)
        private val header = quizText(this@QuizActivity, "", 19f, QuizColors.MUTED)
        private val status = quizText(this@QuizActivity, "", 20f, QuizColors.TEXT).apply { gravity = Gravity.CENTER; maxLines = 2 }
        private val chips = row(Gravity.CENTER)
        private var remaining = 0L
        private var total = 1L
        private var lastPhase: String? = null
        private val hidden = View(this@QuizActivity).apply { isFocusable = true }   // holds the focus: OK = skip the wait

        override val view: View = column(Gravity.TOP).apply {
            setPadding(dpi(28), dpi(16), dpi(28), dpi(16))
            addView(hidden, lp(1, 1))
            addView(row().apply {
                addView(header, lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)); addView(ring, lp(dpi(70), dpi(70)))
            }, lp(MATCH))
            addView(questionPlate, lp(MATCH, dpi(124), t = 8, b = 10))
            addView(row().apply { addView(plates[0], lp(0, dpi(84), 1f)); addView(plates[1], lp(0, dpi(84), 1f)) }, lp(MATCH))
            addView(row().apply { addView(plates[2], lp(0, dpi(84), 1f)); addView(plates[3], lp(0, dpi(84), 1f)) }, lp(MATCH, t = 4))
            addView(status, lp(MATCH, t = 10))
            addView(chips, lp(MATCH, t = 8))
            plates.forEachIndexed { i, p -> p.hex.base = QuizColors.DUEL[i] }
        }

        override fun update(s: Map<String, Any?>) {
            val d = s.m("duel") ?: return
            val q = d.m("question") ?: return
            val phase = d.s("phase")
            val reveal = phase == "REVEAL"
            val answer = q.i("answer")
            val dist = d.list("distribution").mapNotNull { (it as? Number)?.toInt() }
            header.text = "DUEL  ·  Question ${(d.i("index") ?: 0) + 1}/${d.i("count")}  ·  ${q.s("category")}"
            questionPlate.text = fr(q.s("text"))
            val choices = q.list("choices").map { it.toString() }
            for (i in 0..3) {
                val t = SpannableStringBuilder("${QuizColors.SHAPES[i]} ${QuizColors.LETTERS[i]}   ${fr(choices.getOrElse(i) { "" })}")
                if (reveal) { if (i == answer) t.append("   ✓"); t.append("   (${dist.getOrElse(i) { 0 }})") }
                plates[i].text = t
                plates[i].setTextColor(if (i == 2 && !(reveal && i == answer)) QuizColors.BG_BOTTOM else QuizColors.TEXT)
                plates[i].hex.look = when {
                    reveal && i == answer -> HexDrawable.Look.RIGHT
                    reveal -> HexDrawable.Look.DIM
                    else -> HexDrawable.Look.NORMAL
                }
            }
            val players = s.maps("players")
            val ranking = d.maps("ranking").associateBy { it.s("id") }
            chips.removeAllViews()
            for (p in players) {
                val r = ranking[p.s("id")]
                val txt = when {
                    reveal -> (if (r?.b("correct") == true) "✓ " else "✗ ") + p.s("name") + ((r?.i("gained") ?: 0).takeIf { it > 0 }?.let { " +$it" } ?: "")
                    p.b("answered") -> "✓ " + p.s("name")
                    else -> "… " + p.s("name")
                }
                chips.addView(quizText(this@QuizActivity, txt, 18f, if (reveal && r?.b("correct") != true) 0xFFFF9C9C.toInt() else QuizColors.TEXT, p.b("answered") || reveal).apply {
                    background = panelBackground(this@QuizActivity, if (reveal && r?.b("correct") == true) 0xFF12512F.toInt() else QuizColors.PANEL)
                    setPadding(dpi(14), dpi(6), dpi(14), dpi(6))
                }, lp(l = 5, r = 5))
            }
            status.text = if (reveal) fr(q.s("explanation")) else "Répondez sur vos téléphones !  ${d.i("answeredCount")}/${players.size} réponses"
            remaining = d.l("remainingMs")
            if (phase != lastPhase) {
                total = maxOf(1, if (phase == "QUESTION") d.l("questionMs") else remaining)
                if (phase == "REVEAL") { play(QuizSound.Clip.RIGHT); stage.flash = QuizColors.RIGHT } else play(QuizSound.Clip.NEXT)
                lastPhase = phase
            }
            ring.visibility = if (phase == "QUESTION") View.VISIBLE else View.INVISIBLE
            onTick()
        }

        override fun onTick() {
            val left = left(remaining)
            ring.fraction = left.toFloat() / total
            ring.label = ((left + 999) / 1000).toString()
        }

        override fun focusFirst() { hidden.requestFocus() }
    }

    // ------------------------------------------------------------------ Duel: standings between questions

    private inner class BoardScreen(key: String) : Screen(key) {
        private val box = FrameLayout(this@QuizActivity)
        private val title = quizText(this@QuizActivity, "", 30f, QuizColors.GOLD, true)
        private var built = false
        private val hidden = View(this@QuizActivity).apply { isFocusable = true }
        override val view: View = column(Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            setPadding(dpi(60), dpi(20), dpi(60), dpi(20))
            addView(hidden, lp(1, 1))
            addView(title, lp(b = 14))
            addView(box, lp(MATCH, 0, 1f))
            addView(quizText(context, "OK : question suivante", 16f, QuizColors.MUTED), lp(t = 6))
        }
        override fun update(s: Map<String, Any?>) {
            if (built) return
            val d = s.m("duel") ?: return
            built = true
            title.text = "Classement après la question ${(d.i("index") ?: 0) + 1}/${d.i("count")}"
            val ranking = d.maps("ranking")
            box.post { animateRanking(box, ranking) }
        }
        override fun focusFirst() { hidden.requestFocus() }
    }

    /** Rows start at their previous rank and slide to the new one while the scores count up and the bars grow. */
    private fun replay() {
        val r = room ?: return
        r.backToLobby()
        r.startGame()?.let { android.widget.Toast.makeText(this, it, android.widget.Toast.LENGTH_LONG).show() }
    }

    private fun animateRanking(box: FrameLayout, ranking: List<Map<String, Any?>>, payouts: Map<String, Any?>? = null) {
        box.removeAllViews()
        val rowH = minOf(dpi(58), if (ranking.isEmpty()) dpi(58) else box.height / maxOf(ranking.size, 1))
        val max = maxOf(1, ranking.maxOfOrNull { it.i("score") ?: 0 } ?: 1)
        for (r in ranking) {
            val rank = r.i("rank") ?: 1
            val prev = r.i("prevRank") ?: rank
            val score = r.i("score") ?: 0
            val gained = r.i("gained") ?: 0
            val pay = (payouts?.get(r.s("id")) as? Number)?.toLong()
            val line = FrameLayout(this)
            val bar = View(this).apply { background = panelBackground(this@QuizActivity, if (rank == 1) 0xFF8A6A12.toInt() else 0xFF203A9A.toInt(), QuizColors.STROKE) }
            val arrow = when { rank < prev -> "  ▲"; rank > prev -> "  ▼"; else -> "" }
            val name = quizText(this, "$rank.  ${r.s("name")}$arrow", 24f, QuizColors.TEXT, true).apply { setPadding(dpi(16), 0, 0, 0); gravity = Gravity.CENTER_VERTICAL }
            val pts = quizText(this, "", 24f, QuizColors.GOLD, true).apply { gravity = Gravity.CENTER_VERTICAL or Gravity.END; setPadding(0, 0, dpi(28), 0) }
            line.addView(bar, FrameLayout.LayoutParams(0, rowH - dpi(8), Gravity.CENTER_VERTICAL))
            line.addView(name, FrameLayout.LayoutParams(MATCH, MATCH))
            line.addView(pts, FrameLayout.LayoutParams(MATCH, MATCH))
            box.addView(line, FrameLayout.LayoutParams(MATCH, rowH))
            line.translationY = ((prev - 1) * rowH).toFloat()
            val from = score - gained
            val fullW = box.width
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 1100; startDelay = 300; interpolator = DecelerateInterpolator()
                addUpdateListener {
                    val f = it.animatedValue as Float
                    line.translationY = ((prev - 1) + (rank - prev) * f) * rowH
                    val cur = from + ((score - from) * f).toInt()
                    pts.text = "$cur pts" + (if (gained > 0 && pay == null) "  (+$gained)" else "") + (pay?.let { "   ·   +$it jetons" } ?: "")
                    bar.layoutParams = (bar.layoutParams as FrameLayout.LayoutParams).apply { width = maxOf(dpi(8), (fullW * 0.98f * cur / max).toInt()) }
                    bar.requestLayout()
                }
            }.start()
        }
        play(QuizSound.Clip.NEXT)
    }

    // ------------------------------------------------------------------ Duel: final podium

    private inner class DuelFinalScreen : Screen("d-final") {
        private val title = quizText(this@QuizActivity, "", 40f, QuizColors.GOLD, true).apply { gravity = Gravity.CENTER }
        private val box = FrameLayout(this@QuizActivity)
        private val again = quizButton(this@QuizActivity, "Rejouer") { replay() }
        private var built = false
        override val view: View = column(Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            setPadding(dpi(60), dpi(20), dpi(60), dpi(16))
            addView(title, lp(b = 10))
            addView(box, lp(MATCH, 0, 1f))
            addView(row().apply {
                addView(again, lp(r = 16))
                addView(quizButton(context, "Salle d'attente") { room?.backToLobby() }, lp(r = 16))
                addView(quizButton(context, "Menu du quiz") { goHome() }, lp())
            }, lp(t = 10))
        }
        override fun update(s: Map<String, Any?>) {
            if (built) return
            val d = s.m("duel") ?: return
            built = true
            val ranking = d.maps("ranking")
            title.text = ranking.firstOrNull()?.let { "Victoire de ${it.s("name")} !" } ?: "Fin du duel"
            val pot = s.m("pot")
            if (pot != null) title.text = "${title.text}\nCagnotte : ${pot.l("total")} jetons  —  ${QuizRoom.TOKENS_LABEL}"
            box.post { animateRanking(box, ranking, pot?.m("payouts")) }
            play(QuizSound.Clip.WIN)
        }
        override fun focusFirst() { again.requestFocus() }
    }

    // ------------------------------------------------------------------ closed

    private inner class ClosedScreen : Screen("closed") {
        private val btn = quizButton(this@QuizActivity, "Ouvrir une nouvelle salle") { steps.clear(); steps += "home"; openRoom() }
        override val view: View = column().apply {
            addView(quizText(context, "La salle est fermée (inactivité).", 30f, QuizColors.TEXT, true), lp(b = 24))
            addView(btn, lp(b = 12))
            addView(quizButton(context, "Quitter le quiz") { finish() }, lp())
        }
        override fun update(s: Map<String, Any?>) {}
        override fun focusFirst() { btn.requestFocus() }
    }

    // ------------------------------------------------------------------ remote control

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val sc = screen
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_MENU) { pauseMenu(); return true }
        // Duel: OK skips the wait (closes the question now, next standings/question)
        if ((sc is DuelScreen || sc is BoardScreen) && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)) {
            room?.hostSkip(); return true
        }
        return super.onKeyDown(keyCode, event)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { pauseMenu() }

    /** BACK: title → leave; lobby → title; in a game → pause menu with confirmation before anything is lost. */
    private fun pauseMenu() {
        val stage = room?.stage
        when {
            screen is ClosedScreen -> finish()
            screen is SetupScreen -> if (steps.size > 1) { steps.removeAt(steps.size - 1); render() } else finish()
            stage == QuizRoom.Stage.LOBBY -> goHome()
            else -> choose("Pause", listOf("Reprendre la partie", "Abandonner et revenir à la salle d'attente", "Quitter le quiz")) { i ->
                when (i) {
                    1 -> confirm("Abandonner la partie ?", "La partie en cours sera perdue. Les joueurs restent dans la salle.", "Abandonner") { room?.backToLobby() }
                    2 -> confirm("Quitter le quiz ?", "La salle sera fermée et les joueurs déconnectés.", "Quitter") { finish() }
                }
            }
        }
    }
}
