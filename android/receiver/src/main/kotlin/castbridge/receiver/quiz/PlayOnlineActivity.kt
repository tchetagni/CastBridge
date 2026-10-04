package castbridge.receiver.quiz

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import castbridge.core.quiz.online.LinkAction
import castbridge.core.quiz.online.PlayBanner
import castbridge.core.quiz.online.PlayBannerModel
import castbridge.core.quiz.online.PlayErrors
import castbridge.core.quiz.online.PlayEvent
import castbridge.core.quiz.online.PlayFlow
import castbridge.core.quiz.online.PlayFlowState
import castbridge.core.quiz.online.PlayLinkScreen
import castbridge.core.quiz.online.PlayScreen
import castbridge.core.quiz.online.RelaySeatsText
import castbridge.core.quiz.online.RoomCode
import castbridge.core.quiz.online.RoomCodeEntry
import castbridge.core.tv.PlayerIcons
import castbridge.core.ux.SignalColors
import castbridge.receiver.QuizColors
import castbridge.receiver.TvSignalViews
import castbridge.receiver.dp
import castbridge.receiver.dpi
import castbridge.receiver.fr
import castbridge.receiver.panelBackground
import castbridge.receiver.quizButton
import castbridge.receiver.quizText

/**
 * « Partie Internet » de CastBridge-TV (w20-05 POC). Cinq touches suffisent (haut, bas, gauche, droite, OK ; Retour = revenir) : tout est un bouton focalisable.
 * Aucune décision ici : l'écran affiche l'état de l'automate du cœur ([PlayFlow]), le bandeau reçu ([PlayBannerModel]) et les textes du cœur ([PlayErrors]).
 * Textes ≥ 28 sp ([PlayerIcons.TEXT_SP]), marge de sécurité de 5 % ([PlayerIcons.safe]), le bandeau porte toujours la forme ET le mot, jamais la couleur seule.
 * Vérifié par compilation seulement : le rendu et le réseau se jugent sur une vraie TV et le service déployé (docs/test-plans/H-PLAY-POC.md).
 */
class PlayOnlineActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var banner: TextView
    private lateinit var here: TextView
    private lateinit var body: FrameLayout
    private var flow = PlayFlowState(PlayScreen.CLOSED)
    private var entry = RoomCodeEntry()
    private var started = false
    private var shownKey: String? = null
    private var roomInfo: TextView? = null
    private var roomTitle: TextView? = null
    private var roomLines: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val dm = resources.displayMetrics
        val safe = PlayerIcons.safe(dm.widthPixels, dm.heightPixels)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(safe.horizontal, safe.vertical, safe.horizontal, safe.vertical)
        }
        banner = quizText(this, "", PlayerIcons.TEXT_SP.toFloat(), QuizColors.TEXT, true).apply {
            maxLines = 2; setPadding(dpi(16), dpi(8), dpi(16), dpi(8)); background = panelBackground(this@PlayOnlineActivity); compoundDrawablePadding = dpi(12)
        }
        here = quizText(this, "", PlayerIcons.TEXT_SP.toFloat(), QuizColors.MUTED).apply { visibility = View.GONE }
        body = FrameLayout(this)
        col.addView(banner, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        col.addView(here, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dpi(6) })
        col.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = dpi(10) })
        setContentView(FrameLayout(this).apply { setBackgroundColor(QuizColors.BG_BOTTOM); addView(col, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)) })

        if (PlayHub.active()) { flow = PlayFlowState(PlayScreen.ROOM); started = true; show() }
        else {
            showMessage("Vérification du service de jeu…", null)
            PlayHub.probe(force = true) { main.post { if (!isFinishing) { flow = PlayFlow.start(PlayHub.tile(this)); started = true; afterChange(PlayFlowState(PlayScreen.CLOSED)) } } }
        }
        main.post(poll)
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        if (isFinishing) PlayHub.stop()
        super.onDestroy()
    }

    override fun onBackPressed() { if (started) send(PlayEvent.Back) else finish() }

    // ------------------------------------------------------------------ automate

    private fun send(e: PlayEvent) {
        val prev = flow
        flow = PlayFlow.next(flow, e)
        if (flow != prev) afterChange(prev)
    }

    private fun afterChange(prev: PlayFlowState) {
        when (flow.screen) {
            PlayScreen.CLOSED -> { PlayHub.stop(); finish(); return }
            PlayScreen.OPENING -> if (prev.screen != PlayScreen.OPENING) {
                val intent = flow.intent!!
                PlayHub.start(this, intent) { err -> main.post { if (err != null) send(PlayEvent.Failed(err)) } }
            }
            PlayScreen.MENU, PlayScreen.FAILED, PlayScreen.LOST -> if (prev.screen == PlayScreen.OPENING || prev.screen == PlayScreen.ROOM || prev.screen == PlayScreen.CONFIRM_LEAVE) PlayHub.stop()
            else -> {}
        }
        if (flow.screen == PlayScreen.ENTER_CODE && prev.screen != PlayScreen.ENTER_CODE) entry = RoomCodeEntry()
        show()
    }

    /** Suit la session : ouverture réussie ou refusée, perte de liaison, salle fermée, bandeau et salle à jour. */
    private val poll = object : Runnable {
        override fun run() {
            runCatching { tick() }
            main.postDelayed(this, 300)
        }
    }

    private fun tick() {
        if (!started) return
        val s = PlayHub.session
        when (flow.screen) {
            PlayScreen.OPENING -> if (s != null && s.started) {
                val err = s.authority.lastErrorOrNull()
                when {
                    s.certificateInvalid -> send(PlayEvent.Failed(PlayErrors.text(castbridge.core.quiz.online.PlayReason.PLAY_TLS_INVALID.name)))
                    s.seated -> send(PlayEvent.Seated)
                    err != null -> send(PlayEvent.Failed(PlayErrors.text(err.reason, err.retryAfterMs)))
                    PlayLinkScreen.of(s.link, null, false).action == LinkAction.LEAVE -> send(PlayEvent.LinkLost)
                }
            }
            PlayScreen.ROOM, PlayScreen.CONFIRM_LEAVE -> if (s != null) {
                val link = PlayLinkScreen.of(s.link, castbridge.receiver.TvConnect.link?.routes?.lastVia, s.certificateInvalid, PlayHub.hasInternet(this))
                val gone = s.authority.lastGone
                when {
                    gone != null -> send(PlayEvent.Failed(PlayErrors.text(gone.reason)))
                    link.action == LinkAction.LEAVE -> send(PlayEvent.LinkLost)
                }
            }
            else -> {}
        }
        drawBanner()
        if (flow.screen == PlayScreen.ROOM) updateRoom()
    }

    // ------------------------------------------------------------------ bandeau

    private fun drawBanner() {
        val s = PlayHub.session
        val view = PlayBanner.sign(flow.screen, s?.safety())
        if (view == null) { banner.visibility = View.GONE; here.visibility = View.GONE; return }
        val online = flow.screen in setOf(PlayScreen.OPENING, PlayScreen.ROOM, PlayScreen.CONFIRM_LEAVE)
        val m = PlayBannerModel.of(view, if (online) PlayHub.relay?.phoneCount() ?: 0 else null)
        banner.visibility = View.VISIBLE
        banner.text = fr(m.scopeLine + " · " + m.word + " · " + m.text + (m.action?.let { "  ›  $it" } ?: ""))
        banner.contentDescription = m.description
        banner.setCompoundDrawablesRelativeWithIntrinsicBounds(TvSignalViews.drawable(this, m.level, PlayerIcons.ICON_DP), null, null, null)
        banner.setTextColor(SignalColors.TEXT)
        here.visibility = if (m.hereLine != null) View.VISIBLE else View.GONE
        here.text = m.hereLine.orEmpty()
    }

    // ------------------------------------------------------------------ écrans

    private fun column(): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
    private fun row(): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
    private fun text(s: CharSequence, sp: Float = PlayerIcons.TEXT_SP.toFloat(), color: Int = QuizColors.TEXT, bold: Boolean = false) = quizText(this, fr(s.toString()), sp, color, bold).apply { gravity = Gravity.CENTER }
    private fun button(label: String, onClick: () -> Unit) = quizButton(this, label, PlayerIcons.TEXT_SP.toFloat(), onClick)
    private fun lp(top: Int = 8) = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dpi(top); leftMargin = dpi(8); rightMargin = dpi(8) }

    private fun setBody(v: View, focus: View?) {
        body.removeAllViews()
        body.addView(v, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        focus?.requestFocus()
    }

    private fun showMessage(message: String, backLabel: String?) {
        val c = column().apply { gravity = Gravity.CENTER }
        c.addView(text(message), lp())
        val b = backLabel?.let { l -> button(l) { send(PlayEvent.Back) }.also { c.addView(it, lp(24)) } }
        setBody(c, b)
    }

    private fun show() {
        drawBanner()
        shownKey = null; roomInfo = null; roomTitle = null; roomLines = null
        when (flow.screen) {
            PlayScreen.MENU -> {
                val c = column().apply { gravity = Gravity.CENTER }
                c.addView(text("Partie Internet", 40f, QuizColors.GOLD, true), lp())
                c.addView(text("Votre TV joue en ligne ; les téléphones de la maison jouent par la TV."), lp(4))
                if (PlayHub.revocationsOff) c.addView(text(castbridge.core.quiz.online.PlayGate.NOTE_REVOCATIONS_OFF, 28f, QuizColors.GOLD, false), lp(4))
                val create = button("Créer une partie") { send(PlayEvent.ChooseCreate) }
                c.addView(create, lp(24))
                c.addView(button("Rejoindre avec un code") { send(PlayEvent.ChooseJoin) }, lp())
                c.addView(button("Retour") { send(PlayEvent.Back) }, lp())
                setBody(c, create)
            }
            PlayScreen.ENTER_CODE -> showCodeEntry()
            PlayScreen.OPENING -> showMessage("Ouverture de la partie…\nPar une liaison lente, cela peut prendre quelques secondes.", null)
            PlayScreen.ROOM -> { roomBodyBuilt = null; updateRoom() }
            PlayScreen.CONFIRM_LEAVE -> {
                val c = column().apply { gravity = Gravity.CENTER }
                c.addView(text("Quitter la partie Internet ?", 36f, QuizColors.GOLD, true), lp())
                c.addView(text("Les joueurs de cette TV seront retirés de la partie."), lp(4))
                val cancel = button("Annuler") { send(PlayEvent.Back) }          // présélectionné
                val r = row().apply { addView(cancel, lp()); addView(button("Quitter") { send(PlayEvent.ConfirmLeave) }, lp()) }
                c.addView(r, lp(24))
                setBody(c, cancel)
            }
            PlayScreen.LOST -> showMessage(flow.message ?: "Partie Internet perdue", "Retour au Quiz")
            PlayScreen.FAILED -> showMessage(flow.message ?: PlayErrors.GENERIC, "Retour")
            PlayScreen.BLOCKED -> showMessage(flow.message ?: PlayErrors.GENERIC, "Retour au Quiz")
            PlayScreen.CLOSED -> {}
        }
    }

    private lateinit var codeText: TextView
    private lateinit var codeMessage: TextView

    private fun showCodeEntry() {
        val c = column().apply { gravity = Gravity.CENTER_HORIZONTAL }
        c.addView(text("Code de la salle", 32f, QuizColors.GOLD, true), lp(0))
        codeText = text(entry.display(), 48f, QuizColors.TEXT, true).apply { typeface = android.graphics.Typeface.MONOSPACE }
        c.addView(codeText, lp(4))
        codeMessage = text(flow.message.orEmpty(), PlayerIcons.TEXT_SP.toFloat(), QuizColors.WRONG)
        c.addView(codeMessage, lp(2))
        var firstKey: View? = null
        for (keys in RoomCodeEntry.keypad()) {
            val r = row()
            for (k in keys) {
                val b = button(k.toString()) { entry = entry.append(k); codeText.text = entry.display(); codeMessage.text = "" }
                b.minWidth = 0; b.setPadding(dpi(4), dpi(6), dpi(4), dpi(6))
                r.addView(b, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dpi(4); rightMargin = dpi(4) })
                if (firstKey == null) firstKey = b
            }
            c.addView(r, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dpi(4) })
        }
        val actions = row().apply {
            addView(button("Effacer") { entry = entry.backspace(); codeText.text = entry.display() }, lp())
            addView(button("Valider") {
                val before = flow
                send(PlayEvent.CodeSubmitted(entry.code()))
                if (flow == before) { codeMessage.text = flow.message.orEmpty() }
            }, lp())
            addView(button("Retour") { send(PlayEvent.Back) }, lp())
        }
        c.addView(actions, lp(8))
        val sv = ScrollView(this).apply { isFillViewport = true; isVerticalScrollBarEnabled = false; addView(c, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)) }
        setBody(sv, firstKey)
    }

    // ------------------------------------------------------------------ la salle

    private var roomBodyBuilt: String? = null

    @Suppress("UNCHECKED_CAST")
    private fun updateRoom() {
        val s = PlayHub.session ?: return
        val v: Map<String, Any?> = PlayHub.relay?.view(null) ?: s.authority.view(null)
        val room = v["room"] as? Map<String, Any?>
        val duel = v["duel"] as? Map<String, Any?>
        val isHost = room?.get("role") == "HOST"
        val roomState = room?.get("state") as? String
        val phase = duel?.get("phase") as? String
        val kind = when {
            duel == null || roomState == "OPEN" -> "lobby"
            phase == "FINISHED" || roomState == "FINISHED" -> "end"
            else -> "game"
        } + if (isHost) "-host" else ""
        if (roomBodyBuilt != kind) {
            roomBodyBuilt = kind
            val c = column().apply { gravity = Gravity.CENTER }
            val title = text("", 40f, QuizColors.GOLD, true).also { roomTitle = it }
            val info = text("", PlayerIcons.TEXT_SP.toFloat()).also { roomInfo = it }
            val lines = text("", PlayerIcons.TEXT_SP.toFloat()).also { roomLines = it }
            c.addView(title, lp(0)); c.addView(info, lp(6)); c.addView(lines, lp(6))
            var focus: View? = null
            val r = row()
            if (kind == "lobby-host") r.addView(button("Commencer") { PlayHub.hostStart() }.also { focus = it }, lp())
            if (kind == "end-host") r.addView(button("Nouvelle partie") { PlayHub.session?.authority?.act(null, "lobby", null, null, null) }.also { focus = it }, lp())
            val leave = button("Quitter") { send(PlayEvent.Back) }
            r.addView(leave, lp())
            if (focus == null) focus = leave
            c.addView(r, lp(16))
            setBody(c, focus)
        }
        val code = (room?.get("code") as? String) ?: s.authority.code?.let { RoomCode.display(it) } ?: ""
        val phones = PlayHub.relay?.phoneCount() ?: 0
        when {
            kind.startsWith("lobby") -> {
                roomTitle?.text = fr("Code de la salle : $code")
                roomTitle?.textSize = 44f
                roomInfo?.text = fr(if (isHost) "Les autres TV rejoignent avec ce code. Les téléphones de la maison ouvrent le Quiz de CastBridge." else "Vous avez rejoint la salle. L'hôte lance la partie.")
                roomLines?.text = RelaySeatsText.here(phones)
            }
            kind.startsWith("end") -> {
                roomTitle?.text = "Partie terminée"
                roomInfo?.text = ""
                roomLines?.text = ranking(duel)
            }
            else -> {
                val q = duel?.get("question") as? Map<String, Any?>
                val choices = (q?.get("choices") as? List<*>).orEmpty().map { it.toString() }
                val shapes = arrayOf("▲", "◆", "●", "■"); val letters = arrayOf("A", "B", "C", "D")
                val idx = (duel?.get("index") as? Number)?.toInt() ?: 0; val count = (duel?.get("count") as? Number)?.toInt() ?: 0
                roomTitle?.text = "Question ${idx + 1} / $count"
                roomTitle?.textSize = 34f
                roomInfo?.text = fr(q?.get("text") as? String ?: "")
                val answer = (q?.get("answer") as? Number)?.toInt()
                val sb = StringBuilder()
                choices.forEachIndexed { i, ch -> sb.append(shapes.getOrElse(i) { "" }).append(' ').append(letters.getOrElse(i) { "" }).append("   ").append(ch).append(if (answer == i) "   ✓" else "").append('\n') }
                when (phase) {
                    "QUESTION" -> {
                        val qc = s.questionClock
                        val left = qc?.let { (it.windowMs - (SystemClock.elapsedRealtime() - it.opensAtLocalMono)).coerceIn(0L, it.windowMs) / 1000 }
                        sb.append(if (left != null && SystemClock.elapsedRealtime() < qc.opensAtLocalMono) "Question suivante dans un instant…" else "Répondez sur vos téléphones" + (left?.let { " · $it s" } ?: "")).append("\n")
                    }
                    "REVEAL" -> (q?.get("explanation") as? String)?.let { sb.append(it).append('\n') }
                    "BOARD" -> sb.append(ranking(duel)).append('\n')
                }
                sb.append(RelaySeatsText.here(phones))
                roomLines?.text = fr(sb.toString())
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun ranking(duel: Map<String, Any?>?): String =
        (duel?.get("ranking") as? List<Map<String, Any?>>).orEmpty().take(5).mapIndexed { i, r -> "${i + 1}. ${r["name"]}  ${r["score"]}" }.joinToString("\n")
}
