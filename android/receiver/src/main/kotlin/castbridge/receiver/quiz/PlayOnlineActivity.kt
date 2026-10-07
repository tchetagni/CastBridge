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
import castbridge.core.chess.online.ChessOnlineGame
import castbridge.core.chess.online.StakeAvailability
import castbridge.core.chess.online.StakeChoice
import castbridge.core.quiz.online.LinkAction
import castbridge.core.quiz.online.PlayBanner
import castbridge.core.quiz.online.PlayBannerModel
import castbridge.core.quiz.online.PlayErrors
import castbridge.core.quiz.online.PlayEvent
import castbridge.core.quiz.online.PlayFlow
import castbridge.core.quiz.online.PlayFlowState
import castbridge.core.quiz.online.PlayIntent
import castbridge.core.quiz.online.PlayLinkScreen
import castbridge.core.quiz.online.PlayScreen
import castbridge.core.quiz.online.PlayStakePlan
import castbridge.core.quiz.online.QuizStakeTexts
import castbridge.core.quiz.online.RelaySeatsText
import castbridge.core.quiz.online.RoomCode
import castbridge.core.quiz.online.RoomCodeEntry
import castbridge.core.quiz.online.StakeSpec
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
    /** Quiz misé (games-G5) : le choix de mise en cours (écran « Créer »), les sièges choisis pour rejoindre une salle misée, et le refus du service déjà traité (jamais rejoué à chaque battement). */
    private var choice: StakeChoice? = null
    private var joinSeats = 1
    private var handledError: Any? = null
    private var choiceNote: TextView? = null

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

        PlayHub.prepareStakes(this)   // le portefeuille et la politique du Quiz, relus (l'API reste l'autorité de chaque blocage)
        if (PlayHub.active()) { flow = PlayFlowState(PlayScreen.ROOM); started = true; show() }
        else {
            showMessage("Vérification du service de jeu…", null)
            PlayHub.probe(force = true) { main.post { if (!isFinishing) { flow = PlayFlow.start(PlayHub.tile(this), stakeOffer()); started = true; afterChange(PlayFlowState(PlayScreen.CLOSED)) } } }
        }
        main.post(poll)
        // des règlements restés en attente (hors ligne) d'une partie précédente repartent à l'ouverture de l'écran
        Thread({ runCatching { PlayHub.retryPendingSettlements(this) } }, "quiz-settle-retry").apply { isDaemon = true }.start()
    }

    /** Le menu propose-t-il de miser ? Oui seulement si cette TV peut miser au Quiz maintenant (sinon « Créer » ouvre tout de suite une partie libre, et la raison est dite au menu). */
    private fun stakeOffer(): Boolean = PlayHub.stakeAvailability() is StakeAvailability.Allowed

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
        val leaving = setOf(PlayScreen.OPENING, PlayScreen.ROOM, PlayScreen.CONFIRM_LEAVE, PlayScreen.JOIN_STAKE)
        when (flow.screen) {
            PlayScreen.CLOSED -> { PlayHub.stop(); finish(); return }
            PlayScreen.OPENING -> if (prev.screen != PlayScreen.OPENING) {
                val intent = flow.intent!!
                val report: (String?) -> Unit = { err -> main.post { if (err != null) send(PlayEvent.Failed(err)) } }
                // la salle qu'on rejoint est misée et la TV vient de choisir ses sièges : on bloque sa mise puis on revient sur la MÊME liaison (aucun nouveau ticket) ; sinon, une ouverture neuve
                if (prev.screen == PlayScreen.JOIN_STAKE && intent is PlayIntent.Join && flow.plan != null) PlayHub.joinWithStake(this, intent.code, flow.plan!!, report)
                else PlayHub.start(this, intent, report)
            }
            // l'utilisateur s'en va : une salle misée encore en attente est ANNULÉE par son hôte (mise rendue tout de suite) ; une liaison perdue ou refusée se ferme simplement
            PlayScreen.MENU -> if (prev.screen in leaving) PlayHub.leave()
            PlayScreen.FAILED, PlayScreen.LOST -> if (prev.screen in leaving) PlayHub.stop()
            else -> {}
        }
        if (flow.screen == PlayScreen.ENTER_CODE && prev.screen != PlayScreen.ENTER_CODE) entry = RoomCodeEntry()
        if (flow.screen == PlayScreen.STAKE_CHOICE && prev.screen != PlayScreen.STAKE_CHOICE && prev.screen != PlayScreen.STAKE_CONFIRM) choice = newChoice()
        if (flow.screen == PlayScreen.JOIN_STAKE && prev.screen != PlayScreen.JOIN_STAKE) joinSeats = 1
        show()
    }

    /** Le modèle du choix de mise : mises permises à cette TV (jamais une preuve), solde connu de l'instantané signé, au plus autant de sièges que le serveur en permet, cagnotte partagée. */
    private fun newChoice() = StakeChoice(PlayHub.stakeAvailability(), PlayHub.balanceNdem(), PlayHub.balanceMboko(), maxSeats = PlayHub.maxStakeSeats(), shared = true, abandonWarning = QuizStakeTexts.LEAVE_WARNING)

    /** Suit la session : ouverture réussie ou refusée, perte de liaison, salle fermée, bandeau et salle à jour. */
    private val poll = object : Runnable {
        override fun run() {
            runCatching { tick() }
            main.postDelayed(this, 300)
        }
    }

    private var lastWaitLine: String? = null

    private fun tick() {
        if (!started) {
            // relay-R1 : sans Internet, la TV demande un tuyau au téléphone avant de sonder le service : l'utilisateur voit où l'on en est
            PlayHub.requestLine()?.let { l -> if (l != lastWaitLine) { lastWaitLine = l; showMessage("Vérification du service de jeu…\n$l", null) } }
            return
        }
        val s = PlayHub.session
        when (flow.screen) {
            PlayScreen.OPENING -> if (s != null && s.started) {
                val err = s.authority.lastErrorOrNull()
                when {
                    s.certificateInvalid -> send(PlayEvent.Failed(PlayErrors.text(castbridge.core.quiz.online.PlayReason.PLAY_TLS_INVALID.name)))
                    s.seated -> send(PlayEvent.Seated)
                    // un refus déjà traité (la salle qu'on rejoint est misée : la TV choisit sa mise) reste en mémoire du service jusqu'à la nouvelle entrée : on ne le rejoue pas
                    err != null && err === handledError -> {}
                    // la salle est MISÉE : la liaison reste ouverte, rien n'est consommé ; la TV bloque sa mise (ou renonce)
                    err != null && err.reason == "STAKE_ESCROW_REQUIRED" && flow.intent is PlayIntent.Join -> {
                        handledError = err
                        val spec = QuizStakeTexts.specOf(err.data)
                        send(if (spec != null) PlayEvent.NeedsStake(spec) else PlayEvent.Failed(PlayErrors.text(err.reason, err.retryAfterMs)))
                    }
                    err != null -> send(PlayEvent.Failed(PlayErrors.text(err.reason, err.retryAfterMs)))
                    PlayLinkScreen.of(s.link, null, false).action == LinkAction.LEAVE -> send(PlayEvent.LinkLost)
                }
            }
            PlayScreen.JOIN_STAKE -> if (s != null && PlayLinkScreen.of(s.link, null, s.certificateInvalid).action == LinkAction.LEAVE) send(PlayEvent.LinkLost)
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
        // une partie misée : « Ici : 1 joueur · 1 place libre » compte les mises bloquées, pas huit places
        val m = PlayBannerModel.of(view, if (online) PlayHub.relay?.phoneCount() ?: 0 else null, PlayHub.stake?.plan?.seats ?: castbridge.core.quiz.online.RelayAuthority.MAX_PHONES)
        banner.visibility = View.VISIBLE
        banner.text = fr(m.scopeLine + " · " + m.word + " · " + m.text + (m.action?.let { "  ›  $it" } ?: ""))
        banner.contentDescription = m.description
        banner.setCompoundDrawablesRelativeWithIntrinsicBounds(TvSignalViews.drawable(this, m.level, PlayerIcons.ICON_DP), null, null, null)
        banner.setTextColor(SignalColors.TEXT)
        // relay-R1 : la demande de tuyau en cours, puis « Partie par relais : liaison lente » (information, jamais une alarme) ; rien hors partie
        val hereText = (listOfNotNull(m.hereLine) + (if (online) listOfNotNull(PlayHub.requestLine() ?: PlayHub.relayLine()) else emptyList())).joinToString("   ·   ")
        here.visibility = if (hereText.isNotEmpty()) View.VISIBLE else View.GONE
        here.text = hereText
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
                val offer = stakeOffer()
                if (offer != flow.stakeOffer) flow = flow.copy(stakeOffer = offer)   // les mises deviennent permises (portefeuille lu, politique chargée) ou ne le sont plus : le menu suit
                val c = column().apply { gravity = Gravity.CENTER }
                c.addView(text("Partie Internet", 40f, QuizColors.GOLD, true), lp())
                c.addView(text("Votre TV joue en ligne ; les téléphones de la maison jouent par la TV."), lp(4))
                // quand cette TV ne peut pas miser (essai, service ou serveur à mettre à jour, portefeuille pas lu…), seule « Libre » existe : la raison est dite, jamais un menu muet
                (PlayHub.stakeAvailability() as? StakeAvailability.FreeOnly)?.let { c.addView(text(it.reason, 28f, QuizColors.MUTED, false), lp(4)) }
                if (PlayHub.revocationsOff) c.addView(text(castbridge.core.quiz.online.PlayGate.NOTE_REVOCATIONS_OFF, 28f, QuizColors.GOLD, false), lp(4))
                val create = button("Créer une partie") { send(PlayEvent.ChooseCreate) }
                c.addView(create, lp(24))
                c.addView(button("Rejoindre avec un code") { send(PlayEvent.ChooseJoin) }, lp())
                c.addView(button("Retour") { send(PlayEvent.Back) }, lp())
                setBody(c, create)
            }
            PlayScreen.ENTER_CODE -> showCodeEntry()
            PlayScreen.STAKE_CHOICE -> showStakeChoice()
            PlayScreen.STAKE_CONFIRM -> showStakeConfirm()
            PlayScreen.JOIN_STAKE -> showJoinStake()
            PlayScreen.OPENING -> showMessage(
                if (flow.plan != null) "Blocage de la mise puis ouverture de la partie…\nPar une liaison lente, cela peut prendre quelques secondes." else "Ouverture de la partie…\nPar une liaison lente, cela peut prendre quelques secondes.", null)
            PlayScreen.ROOM -> { roomBodyBuilt = null; updateRoom() }
            PlayScreen.CONFIRM_LEAVE -> {
                val c = column().apply { gravity = Gravity.CENTER }
                c.addView(text("Quitter la partie Internet ?", 36f, QuizColors.GOLD, true), lp())
                c.addView(text(leaveText()), lp(4))
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

    // ------------------------------------------------------------------ la mise (Quiz misé, games-G5) : le modèle est celui du cœur, l'écran ne décide rien

    /** Un réglage à la télécommande : OK ou droite = suivant, gauche = précédent (les « ‹ › » des échecs). */
    private fun cycler(change: (Int) -> Unit): TextView = button("") { change(1) }.also { b ->
        b.setOnKeyListener { _, code, ev ->
            when {
                ev.action != android.view.KeyEvent.ACTION_DOWN -> false
                code == android.view.KeyEvent.KEYCODE_DPAD_LEFT -> { change(-1); true }
                code == android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> { change(1); true }
                else -> false
            }
        }
    }

    private fun balanceOf(cur: String): Long? = if (cur == "NDEM") PlayHub.balanceNdem() else PlayHub.balanceMboko()

    /** « Créer une partie » avec mise : Libre / NDEM / MBOKO, puis le montant (échelle du serveur), puis le nombre de joueurs de CETTE TV qui misent ; solde, mise bloquée et avertissement sous les réglages. */
    private fun showStakeChoice() {
        val m = choice ?: newChoice().also { choice = it }
        val c = column().apply { gravity = Gravity.CENTER }
        c.addView(text("Créer une partie", 40f, QuizColors.GOLD, true), lp(0))
        c.addView(text("Libre, ou avec une mise en NDEM ou MBOKO : chaque joueur qui mise paie la même mise, la cagnotte est partagée selon le classement.", 28f, QuizColors.MUTED), lp(2))
        lateinit var modeBtn: TextView; lateinit var amountBtn: TextView; lateinit var seatsBtn: TextView
        val note = text("", 28f, QuizColors.MUTED).also { choiceNote = it }
        fun refresh() {
            val staked = m.stake() != null
            modeBtn.text = fr("‹  ${m.modeText()}  ›")
            amountBtn.visibility = if (staked) View.VISIBLE else View.GONE
            seatsBtn.visibility = if (staked && m.multiSeat) View.VISIBLE else View.GONE
            amountBtn.text = fr("‹  ${m.amountText()}  ›")
            seatsBtn.text = fr("‹  ${m.seatsText()}  ›")
            val unaffordable = staked && !m.affordable()
            // seule « Libre » existe quand cette TV ne peut pas miser (le menu l'a dit, la liste peut avoir changé depuis) : la raison reste sous les yeux, jamais un choix muet
            note.text = fr(listOfNotNull(m.freeOnlyReason(), m.balanceLine(), m.potLine(), m.warning(), if (unaffordable) "Solde insuffisant pour cette mise : choisissez moins de joueurs ici ou une mise plus petite." else null).joinToString("\n"))
            note.setTextColor(if (unaffordable) QuizColors.WRONG else QuizColors.MUTED)
        }
        modeBtn = cycler { m.cycleMode(it); refresh() }
        amountBtn = cycler { m.cycleAmount(it); refresh() }
        seatsBtn = cycler { m.cycleSeats(it); refresh() }
        c.addView(modeBtn, lp(12)); c.addView(amountBtn, lp(4)); c.addView(seatsBtn, lp(4)); c.addView(note, lp(8))
        val go = button("Créer la partie") {
            val spec = m.stake()
            when {
                spec == null -> send(PlayEvent.StakeChosen(null))          // Libre : la partie s'ouvre tout de suite
                !m.affordable() -> refresh()                               // le texte dit déjà pourquoi
                else -> send(PlayEvent.StakeChosen(PlayStakePlan(spec, m.seats)))
            }
        }
        c.addView(row().apply { addView(go, lp()); addView(button("Retour") { send(PlayEvent.Back) }, lp()) }, lp(16))
        refresh()
        setBody(ScrollView(this).apply { isFillViewport = true; isVerticalScrollBarEnabled = false; addView(c, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)) }, modeBtn)
    }

    /** Une mise demande une confirmation : montant, joueurs d'ici, ce qui est bloqué, solde, et ce qui se passe si on quitte. « Annuler » est présélectionné. */
    private fun showStakeConfirm() {
        val plan = flow.plan ?: return
        val c = column().apply { gravity = Gravity.CENTER }
        c.addView(text(QuizStakeTexts.CREATE_TITLE, 36f, QuizColors.GOLD, true), lp())
        c.addView(text(QuizStakeTexts.confirmSubtitle(plan, balanceOf(plan.spec.cur))), lp(4))
        c.addView(text(QuizStakeTexts.LEAVE_WARNING, 28f, QuizColors.MUTED), lp(6))
        val cancel = button("Annuler") { send(PlayEvent.Back) }
        c.addView(row().apply { addView(cancel, lp()); addView(button("Bloquer ma mise et créer") { send(PlayEvent.StakeConfirmed) }, lp()) }, lp(24))
        setBody(c, cancel)
    }

    /**
     * La salle qu'on rejoint se joue avec une mise : le service l'a dite avant que la TV ne bloque quoi que ce soit. La TV choisit combien de ses joueurs misent (une mise par siège, bloquée de son compte), ou renonce ;
     * si elle ne peut pas miser cette monnaie (essai, service à mettre à jour, solde pas lu), la raison est dite et seul « Retour » reste.
     */
    private fun showJoinStake() {
        val spec = flow.joinStake ?: return
        val avail = PlayHub.stakeAvailability()
        val canStake = (avail as? StakeAvailability.Allowed)?.let { if (spec.cur == "NDEM") it.ndem.isNotEmpty() else it.mboko.isNotEmpty() } == true
        val c = column().apply { gravity = Gravity.CENTER }
        c.addView(text(QuizStakeTexts.JOIN_TITLE, 36f, QuizColors.GOLD, true), lp())
        if (!canStake) {
            c.addView(text((avail as? StakeAvailability.FreeOnly)?.reason ?: "Votre TV ne peut pas miser en ${spec.cur} pour le moment.", 30f, QuizColors.WRONG), lp(4))
            c.addView(text(QuizStakeTexts.NO_WATCH, 28f, QuizColors.MUTED), lp(4))
            val back = button("Retour") { send(PlayEvent.Back) }
            c.addView(back, lp(24)); setBody(c, back); return
        }
        c.addView(text(QuizStakeTexts.joinQuestion(spec)), lp(4))
        val max = PlayHub.maxStakeSeats()
        val note = text("", 28f, QuizColors.MUTED)
        lateinit var seatsBtn: TextView
        fun plan() = PlayStakePlan(spec, joinSeats)
        fun affordable() = balanceOf(spec.cur)?.let { it >= plan().blocked } ?: true      // sans solde connu, l'API juge
        fun refresh() {
            seatsBtn.text = fr("‹  ${if (joinSeats == 1) "1 joueur de cette TV mise" else "$joinSeats joueurs de cette TV misent"}  ›")
            note.text = fr(listOfNotNull(balanceOf(spec.cur)?.let { "Votre solde : ${castbridge.core.wallet.WalletView.thousands(it)} ${spec.cur}" },
                "Mise bloquée : ${castbridge.core.wallet.WalletView.thousands(plan().blocked)} ${spec.cur}", QuizStakeTexts.LEAVE_WARNING,
                if (!affordable()) "Solde insuffisant pour cette mise : choisissez moins de joueurs ici." else null).joinToString("\n"))
            note.setTextColor(if (!affordable()) QuizColors.WRONG else QuizColors.MUTED)
        }
        seatsBtn = cycler { d -> joinSeats = (joinSeats - 1 + d + max) % max + 1; refresh() }
        c.addView(seatsBtn, lp(12)); c.addView(note, lp(8))
        val go = button("Bloquer ma mise et rejoindre") { if (affordable()) send(PlayEvent.JoinWithSeats(joinSeats)) else refresh() }
        c.addView(row().apply { addView(go, lp()); addView(button("Annuler") { send(PlayEvent.Back) }, lp()) }, lp(16))
        refresh()
        setBody(c, seatsBtn)
    }

    /** Ce que le joueur lit avant de quitter la partie : sans mise, comme avant ; avec une mise, ce que cela lui coûte (la partie commencée fait perdre la mise, avant le départ elle est rendue). */
    @Suppress("UNCHECKED_CAST")
    private fun leaveText(): String {
        val plan = PlayHub.stake?.plan ?: return "Les joueurs de cette TV seront retirés de la partie."
        val view = (PlayHub.session?.authority?.view(null)?.get("stake") as? Map<String, Any?>)
        val started = view?.get("started") == true
        val settled = view?.get("settled") == true
        val host = PlayHub.session?.authority?.role == castbridge.core.quiz.online.PlayRole.HOST
        return when {
            settled -> "La partie est terminée : sa mise est réglée. Les joueurs de cette TV seront retirés de la salle."
            started -> QuizStakeTexts.LEAVE_WARNING + " (${QuizStakeTexts.stakeLine(plan.spec)})"
            host -> "Quitter annule la partie : votre mise de ${castbridge.core.wallet.WalletView.thousands(plan.blocked)} ${plan.spec.cur} est rendue."
            else -> "La partie n'a pas commencé : votre mise de ${castbridge.core.wallet.WalletView.thousands(plan.blocked)} ${plan.spec.cur} sera rendue."
        }
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
        // Quiz misé (games-G5) : le bloc `stake` de la vue du service dit la mise, la cagnotte, l'issue ; une salle libre n'en a pas
        val stakeView = v["stake"] as? Map<String, Any?>
        val staked = stakeView != null
        val base = when {
            duel == null || roomState == "OPEN" -> "lobby"
            phase == "FINISHED" || roomState == "FINISHED" -> "end"
            else -> "game"
        }
        val kind = base + (if (isHost) "-host" else "") + (if (staked) "-stake" else "")
        if (roomBodyBuilt != kind) {
            roomBodyBuilt = kind
            val c = column().apply { gravity = Gravity.CENTER }
            val title = text("", 40f, QuizColors.GOLD, true).also { roomTitle = it }
            val info = text("", PlayerIcons.TEXT_SP.toFloat()).also { roomInfo = it }
            val lines = text("", PlayerIcons.TEXT_SP.toFloat()).also { roomLines = it }
            c.addView(title, lp(0)); c.addView(info, lp(6)); c.addView(lines, lp(6))
            var focus: View? = null
            val r = row()
            if (base == "lobby" && isHost) r.addView(button("Commencer") { PlayHub.hostStart() }.also { focus = it }, lp())
            // une salle misée ne joue qu'UNE partie (les mises sont réglées) : pas de « Nouvelle partie » ; le règlement se lit dans « Mes jetons »
            if (base == "end" && isHost && !staked) r.addView(button("Nouvelle partie") { PlayHub.session?.authority?.act(null, "lobby", null, null, null) }.also { focus = it }, lp())
            if (base == "end" && staked && castbridge.receiver.wallet.WalletHub.screenOpenable())
                r.addView(button("Voir « Mes jetons »") { runCatching { startActivity(android.content.Intent(this, castbridge.receiver.wallet.WalletActivity::class.java)) } }.also { if (focus == null) focus = it }, lp())
            val leave = button("Quitter") { send(PlayEvent.Back) }
            r.addView(leave, lp())
            if (focus == null) focus = leave
            c.addView(r, lp(16))
            setBody(c, focus)
        }
        val code = (room?.get("code") as? String) ?: s.authority.code?.let { RoomCode.display(it) } ?: ""
        val phones = PlayHub.relay?.phoneCount() ?: 0
        val seatsHere = PlayHub.stake?.plan?.seats
        val hereLine = if (staked && seatsHere != null) QuizStakeTexts.hereLine(phones, seatsHere) else RelaySeatsText.here(phones)
        when {
            kind.startsWith("lobby") -> {
                roomTitle?.text = fr("Code de la salle : $code")
                roomTitle?.textSize = 44f
                roomInfo?.text = fr(if (isHost) "Les autres TV rejoignent avec ce code. Les téléphones de la maison ouvrent le Quiz de CastBridge." else "Vous avez rejoint la salle. L'hôte lance la partie.")
                val tvs = (stakeView?.get("tvs") as? Number)?.toInt() ?: 0
                roomLines?.text = fr(listOfNotNull(hereLine, QuizStakeTexts.roomLine(stakeView), if (staked) QuizStakeTexts.LEAVE_WARNING else null,
                    if (staked && isHost && tvs < 2) QuizStakeTexts.ONE_TV_ONLY else null, if (isHost) PlayHub.startRefusal else null).joinToString("\n"))
            }
            kind.startsWith("end") -> {
                roomTitle?.text = "Partie terminée"
                roomInfo?.text = ""
                roomLines?.text = fr(listOfNotNull(ranking(duel), if (staked) settlementText() else null).joinToString("\n\n"))
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
                sb.append(hereLine)
                QuizStakeTexts.roomLine(stakeView)?.let { sb.append('\n').append(it) }   // une partie misée : « Mise : 20 NDEM par joueur · cagnotte 80 NDEM » reste sous les yeux
                roomLines?.text = fr(sb.toString())
            }
        }
    }

    /** Où en est le règlement de la mise de CETTE TV (d'après le résultat signé du service, puis la réponse de l'API : jamais un solde calculé ici), ou null tant que rien n'est arrivé. */
    private fun settlementText(): String? = when (val st = PlayHub.stake?.settlement) {
        is ChessOnlineGame.Settlement.Pending -> st.text
        is ChessOnlineGame.Settlement.Done -> st.text
        is ChessOnlineGame.Settlement.Later -> st.text
        is ChessOnlineGame.Settlement.Refused -> st.text
        else -> null
    }

    @Suppress("UNCHECKED_CAST")
    private fun ranking(duel: Map<String, Any?>?): String =
        (duel?.get("ranking") as? List<Map<String, Any?>>).orEmpty().take(5).mapIndexed { i, r -> "${i + 1}. ${r["name"]}  ${r["score"]}" }.joinToString("\n")
}
