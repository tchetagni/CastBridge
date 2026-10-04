package castbridge.receiver.wallet

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import castbridge.core.wallet.WalletCurrency
import castbridge.core.wallet.WalletView
import castbridge.core.wallet.ui.AmountEntry
import castbridge.core.wallet.ui.CodeEntry
import castbridge.core.wallet.ui.ConvertDir
import castbridge.core.wallet.ui.ConvertPreview
import castbridge.core.wallet.ui.HistoryPage
import castbridge.core.wallet.ui.OpGate
import castbridge.core.wallet.ui.ServerWallet
import castbridge.core.wallet.ui.WalletFlow
import castbridge.core.wallet.ui.WalletFlow.Event
import castbridge.core.wallet.ui.WalletFlow.State
import castbridge.core.wallet.ui.WalletGate
import castbridge.core.wallet.ui.WalletIdem
import castbridge.core.wallet.ui.WalletMessages
import castbridge.core.wallet.ui.WalletOp
import castbridge.core.wallet.ui.WalletResult
import castbridge.core.wallet.ui.WalletStatus
import castbridge.core.wallet.ui.WalletSyncSchedule.Trigger
import castbridge.core.wallet.ui.WalletTexts
import castbridge.receiver.Dx
import castbridge.receiver.GamesColors
import java.time.ZoneId

/**
 * « Mes jetons » (cahier w22-07a) : soldes signés, convertir, envoyer, recevoir, historique, à la télécommande à 5 touches (haut, bas, gauche, droite, OK) et RETOUR.
 * Toutes les règles (états, textes, refus, hors ligne) sont dans `castbridge.core.wallet.ui` et testées en JVM ; cet écran ne fait que les dessiner. Il ne calcule et ne garde AUCUN solde.
 * Accessibilité : textes de 42 px de conception au moins (28 px sur une dalle 720p), anneau de focus épais ET flèche « ▶ » (jamais la couleur seule), une opération impossible porte sa raison écrite.
 */
class WalletActivity : Activity() {
    private lateinit var dx: Dx
    private lateinit var body: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var headerState: TextView
    private val main = Handler(Looper.getMainLooper())
    private var state: State = State.Menu
    private var code = CodeEntry()
    private var amount = AmountEntry()
    private var message: String? = null
    private var historyLines = ArrayList<castbridge.core.wallet.ui.HistoryLine>()
    private val zone: ZoneId = ZoneId.systemDefault()
    private val listener: () -> Unit = { if (!isFinishing) render() }
    private val clockTick = object : Runnable { override fun run() { if (state is State.ReceiveShown) { render(keepFocus = true); main.postDelayed(this, 1000) } } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        dx = Dx(this)
        WalletHub.init(this)
        val root = FrameLayout(this).apply { setBackgroundColor(GamesColors.BG) }
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dx.px(96), dx.px(54), dx.px(96), dx.px(40)) }
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(text("◎ Mes jetons", 64, GamesColors.PRIMARY, true), LinearLayout.LayoutParams(0, -2, 1f))
        headerState = text("", 42, GamesColors.TEXT_HIGH, true); head.addView(headerState)
        column.addView(head)
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll = ScrollView(this).apply { isFillViewport = true; addView(body) }
        column.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dx.px(20) })
        root.addView(column, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        WalletHub.addListener(listener)
        // à l'ouverture : synchronisation (si la règle du calendrier le veut) et lecture de la politique (taux, frais, interrupteurs)
        WalletHub.refresh(Trigger.OPEN)
        if (WalletHub.networkUp()) WalletHub.loadPolicy { render() }
        render()
    }

    override fun onDestroy() { WalletHub.removeListener(listener); main.removeCallbacksAndMessages(null); super.onDestroy() }

    // ---- état ----

    private fun gate(op: WalletOp): OpGate = WalletGate.gate(op, WalletHub.online(), WalletHub.snapshot(), WalletHub.policy, WalletHub.cachedHistory() != null)

    private fun send(e: Event) {
        val before = state
        state = WalletFlow.reduce(before, e, WalletIdem::newKey)
        message = null
        if (before is State.Menu && state is State.Menu && e is Event.Back) { finish(); return }
        if (before == state) { render(); return }                                  // rien n'a changé : on ne relance aucune opération
        when (val s = state) {
            is State.ConvertDirection -> if (before is State.Menu) WalletHub.loadPolicy { render() }
            is State.ConvertAmount, is State.SendAmount -> if (before::class != s::class) amount = AmountEntry()
            is State.SendCode -> code = CodeEntry()
            is State.Sending -> startOp(s.pending)
            is State.ReceiveLoading -> WalletHub.receiveCode { r -> when (r) {
                is WalletResult.Ok -> send(Event.ReceiveReady(r.value.code, r.value.expMs))
                is WalletResult.Fail -> send(Event.FailedWith(r.shown, r.network)) } }
            is State.HistoryLoading -> WalletHub.history(null) { r -> when (r) {
                is WalletResult.Ok -> { historyLines = ArrayList(r.value.lines); send(Event.HistoryReady(r.value, cached = false)) }
                is WalletResult.Fail -> { val c = WalletHub.cachedHistory(); if (r.network && c != null) { historyLines = ArrayList(c.lines); send(Event.HistoryReady(c, cached = true)) } else send(Event.FailedWith(r.shown, r.network)) } } }
            is State.ReceiveShown -> { main.removeCallbacks(clockTick); main.postDelayed(clockTick, 1000) }
            else -> {}
        }
        render()
    }

    private fun startOp(p: WalletFlow.Pending) {
        when (val op = p.op) {
            is WalletFlow.Op.Convert -> WalletHub.convert(op.dir, op.q, p.idem) { r -> onOpResult(r) { WalletTexts.convertDone(it) } }
            is WalletFlow.Op.Send -> WalletHub.transfer(op.cur, op.code, op.amt, p.idem) { r -> onOpResult(r) { WalletTexts.transferDone(it) } }
        }
    }

    private fun <T> onOpResult(r: WalletResult<T>, text: (T) -> String) {
        when (r) {
            is WalletResult.Ok -> send(Event.Succeeded(text(r.value)))
            is WalletResult.Fail -> send(Event.FailedWith(r.shown, retryable = r.network))
        }
    }

    // ---- touches ----

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        if (e.action != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(e)
        val s = state
        val wheel = s is State.SendCode || s is State.SendAmount || s is State.ConvertAmount
        if (e.keyCode == KeyEvent.KEYCODE_BACK || e.keyCode == KeyEvent.KEYCODE_ESCAPE) { send(Event.Back); return true }
        if (!wheel) return super.dispatchKeyEvent(e)
        when (e.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> if (s is State.SendCode) code.up() else amount.up()
            KeyEvent.KEYCODE_DPAD_DOWN -> if (s is State.SendCode) code.down() else amount.down()
            KeyEvent.KEYCODE_DPAD_LEFT -> if (s is State.SendCode) code.left() else amount.left()
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (s is State.SendCode) code.right() else amount.right()
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> { validateEntry(s); return true }
            else -> return super.dispatchKeyEvent(e)
        }
        message = null; render(); return true
    }

    private fun validateEntry(s: State) {
        when (s) {
            is State.SendCode -> if (code.complete()) send(Event.CodeEntered(code.text())) else { message = "Code incomplet ou mal saisi : vérifiez chaque caractère"; render() }
            is State.SendAmount -> {
                val v = amount.value(); val have = WalletHub.snapshot()?.let { if (s.cur == WalletCurrency.NDEM) it.n else it.m }
                when {
                    v < 1 -> { message = "Entrez un montant d'au moins 1"; render() }
                    have != null && v > have -> { message = WalletMessages.of(409, "INSUFFICIENT", available = have, cur = s.cur).text; render() }
                    else -> send(Event.AmountEntered(v))
                }
            }
            is State.ConvertAmount -> {
                val v = amount.value(); val p = WalletHub.policy; val snap = WalletHub.snapshot()
                when {
                    v < 1 -> { message = "Entrez un montant d'au moins 1 MBOKO"; render() }
                    p == null || snap == null -> { message = "Taux de conversion en cours de chargement : réessayez dans un instant"; WalletHub.loadPolicy { render() }; render() }
                    !ConvertPreview.of(s.dir, v, p, snap).enough -> { val pv = ConvertPreview.of(s.dir, v, p, snap); message = WalletTexts.previewLines(pv).last(); render() }
                    else -> send(Event.AmountEntered(v))
                }
            }
            else -> {}
        }
    }

    // ---- dessin ----

    private fun render(keepFocus: Boolean = false) {
        val focusedIndex = if (keepFocus) (0 until body.childCount).firstOrNull { body.getChildAt(it).hasFocus() } else null
        body.removeAllViews()
        val online = WalletHub.online()
        headerState.text = if (online) "● En ligne" else "▲ Hors ligne"
        headerState.setTextColor(if (online) GamesColors.SUCCESS else GamesColors.PRIMARY)
        var first: View? = null
        fun add(v: View, top: Int = 12): View { body.addView(v, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dx.px(top) }); if (first == null && v.isFocusable) first = v; return v }
        fun line(s: String, size: Int = 44, color: Int = GamesColors.TEXT_HIGH, bold: Boolean = false, top: Int = 8) = add(text(s, size, color, bold), top)

        when (val s = state) {
            State.Menu -> menu(::add, ::line)
            State.ConvertDirection -> {
                line("Convertir mes jetons", 54, GamesColors.PRIMARY, true)
                line("Choisissez le sens de la conversion.", 44, GamesColors.TEXT_MEDIUM)
                ConvertDir.values().forEach { d -> add(button(WalletTexts.directionLabel(d, WalletHub.policy), null, true) { send(Event.PickDirection(d)) }, 18) }
                add(button("Retour", null, true) { send(Event.Back) }, 18)
            }
            is State.ConvertAmount -> {
                line("Convertir : " + (if (s.dir == ConvertDir.N2M) "NDEM → MBOKO" else "MBOKO → NDEM"), 54, GamesColors.PRIMARY, true)
                line("Combien de MBOKO ? (le taux et les frais sont ceux du serveur)", 44, GamesColors.TEXT_MEDIUM)
                add(amountWheel(), 20)
                val p = WalletHub.policy; val snap = WalletHub.snapshot()
                if (p != null && snap != null && amount.value() >= 1) WalletTexts.previewLines(ConvertPreview.of(s.dir, amount.value(), p, snap)).forEach { line(it, 44, GamesColors.TEXT_HIGH, false, 4) }
                wheelHint(::line)
            }
            is State.ConvertConfirm -> {
                val p = WalletHub.policy; val snap = WalletHub.snapshot()
                line("Confirmer la conversion", 54, GamesColors.PRIMARY, true)
                if (p != null && snap != null) {
                    val pv = ConvertPreview.of(s.dir, s.q, p, snap)
                    WalletTexts.convertConfirm(s.dir, s.q, pv).dropLast(1).forEach { line(it, 48, GamesColors.TEXT_HIGH, true, 6) }
                    WalletTexts.previewLines(pv).filter { it.startsWith("Frais") || it.startsWith("Avant") || it.startsWith("Après") }.forEach { line(it, 44, GamesColors.TEXT_MEDIUM, false, 4) }
                }
                add(button("Confirmer (OK)", null, true) { send(Event.Confirm) }, 20)
                add(button("Modifier", null, true) { send(Event.Back) }, 14)
            }
            State.SendCurrency -> {
                line("Envoyer des jetons", 54, GamesColors.PRIMARY, true)
                line("Quels jetons envoyer ? Le destinataire vous donne son code de réception (« Recevoir » sur sa TV).", 44, GamesColors.TEXT_MEDIUM)
                WalletCurrency.values().forEach { c -> add(button(c.name, null, true) { send(Event.PickCurrency(c)) }, 18) }
                add(button("Retour", null, true) { send(Event.Back) }, 18)
            }
            is State.SendCode -> {
                line("Code de réception du destinataire", 54, GamesColors.PRIMARY, true)
                line("Saisissez le code affiché sur sa TV (commence par R).", 44, GamesColors.TEXT_MEDIUM)
                add(codeWheel(), 20)
                wheelHint(::line)
            }
            is State.SendAmount -> {
                line("Combien de ${s.cur.name} envoyer à ${s.code} ?", 54, GamesColors.PRIMARY, true)
                WalletHub.snapshot()?.let { line("Disponible : ${WalletView.thousands(if (s.cur == WalletCurrency.NDEM) it.n else it.m)} ${s.cur.name}", 44, GamesColors.TEXT_MEDIUM) }
                add(amountWheel(), 20)
                if (amount.value() >= 1) line(castbridge.core.wallet.ui.AmountWords.confirmation(amount.value(), s.cur), 44, GamesColors.TEXT_HIGH, true, 8)
                wheelHint(::line)
            }
            is State.SendConfirm -> {
                line("Confirmer l'envoi", 54, GamesColors.PRIMARY, true)
                WalletTexts.sendConfirm(s.cur, s.amt, s.code).dropLast(1).forEach { line(it, 52, GamesColors.TEXT_HIGH, true, 6) }
                add(button("Confirmer (OK)", null, true) { send(Event.Confirm) }, 20)
                add(button("Modifier", null, true) { send(Event.Back) }, 14)
            }
            is State.Sending -> line("Envoi en cours… ne coupez pas la TV.", 52, GamesColors.TEXT_HIGH, true)
            is State.Done -> {
                line("✔ " + s.message, 50, GamesColors.SUCCESS, true)
                line("Soldes : " + WalletView.balanceLine(WalletHub.snapshot()), 46, GamesColors.TEXT_HIGH)
                add(button("Retour à Mes jetons", null, true) { send(Event.Back) }, 20)
            }
            is State.Failed -> {
                line("✖ " + s.shown.text, 50, GamesColors.ERROR, true)
                if (s.pending != null) line("Aucun jeton n'a bougé tant que vous n'avez pas vu « ✔ ». Réessayer ne double jamais l'opération.", 42, GamesColors.TEXT_MEDIUM)
                if (s.pending != null) add(button("Réessayer", null, true) { send(Event.Retry) }, 20)
                add(button("Retour", null, true) { send(Event.Back) }, 14)
            }
            State.ReceiveLoading -> line("Demande d'un code de réception au serveur…", 50, GamesColors.TEXT_HIGH, true)
            is State.ReceiveShown -> {
                line("Votre code de réception", 54, GamesColors.PRIMARY, true)
                add(text(s.code, 150, GamesColors.TEXT_HIGH, true).apply { gravity = Gravity.CENTER; letterSpacingCompat(this) }, 24)
                line(WalletTexts.expiry(s.expMs, System.currentTimeMillis()), 48, GamesColors.TEXT_HIGH, true, 16)
                line("Donnez ce code à celui qui vous envoie des jetons. Il ne sert qu'une fois.", 44, GamesColors.TEXT_MEDIUM)
                add(button("Retour", null, true) { send(Event.Back) }, 20)
            }
            State.HistoryLoading -> line("Chargement de l'historique…", 50, GamesColors.TEXT_HIGH, true)
            is State.HistoryShown -> {
                line("Historique" + if (s.cached) " (enregistré : hors ligne)" else "", 54, GamesColors.PRIMARY, true)
                if (s.page.lines.isEmpty()) line("Aucune opération pour l'instant.", 46, GamesColors.TEXT_MEDIUM)
                s.page.lines.forEach { l -> add(focusLine(WalletTexts.historyLine(l, zone)), 6) }
                s.page.next?.takeIf { !s.cached && WalletHub.online() }?.let { next ->
                    add(button("Plus anciens", null, true) { WalletHub.history(next) { r -> if (r is WalletResult.Ok) { historyLines.addAll(r.value.lines); send(Event.HistoryReady(HistoryPage(ArrayList(historyLines), r.value.next), false)) } else if (r is WalletResult.Fail) send(Event.FailedWith(r.shown, r.network)) } }, 14)
                }
                add(button("Retour", null, true) { send(Event.Back) }, 14)
            }
        }
        message?.let { m -> add(text("✖ $m", 46, GamesColors.ERROR, true), 14) }
        val f = first
        if (focusedIndex != null && focusedIndex < body.childCount && body.getChildAt(focusedIndex).isFocusable) body.getChildAt(focusedIndex).requestFocus()
        else f?.requestFocus() ?: run { body.isFocusable = true; body.requestFocus() }
        scroll.scrollTo(0, 0)
    }

    private fun letterSpacingCompat(t: TextView) { runCatching { t.letterSpacing = 0.08f } }

    private fun menu(add: (View, Int) -> View, line: (String, Int, Int, Boolean, Int) -> View) {
        val snap = WalletHub.snapshot()
        if (WalletHub.server == ServerWallet.UNAVAILABLE) {
            line(WalletMessages.UNAVAILABLE_TEXT, 54, GamesColors.ERROR, true, 12)
            line("Le service des jetons n'est pas ouvert pour le moment. Réessayez plus tard.", 44, GamesColors.TEXT_MEDIUM, false, 8)
            add(button("Réessayer", null, true) { WalletHub.refresh(Trigger.AFTER_OPERATION) { render() } }, 20)
            return
        }
        // « toute activation donne lieu à un portefeuille » : avant la première synchronisation il existe déjà, SANS aucun chiffre ; ensuite le dernier instantané signé reste lisible hors ligne
        val v = WalletHub.statusView()
        if (v.showBalances && snap != null) {
            WalletTexts.pocketLines(snap).forEach { line(it, 54, GamesColors.TEXT_HIGH, true, 8) }
            line(v.line, 44, GamesColors.TEXT_MEDIUM, false, 4)
            line(WalletTexts.editionLabel(snap.ed), 44, GamesColors.TEXT_MEDIUM, false, 4)
        } else {
            line("▲ " + v.line, 50, GamesColors.PRIMARY, true, 12)
            // une coupure réseau ou un refus passager est dit en clair (jamais un code technique) sous l'état d'attente
            WalletHub.lastFail?.takeIf { v.state == WalletStatus.State.NEVER_SYNCED }?.let { line(it.text, 44, GamesColors.TEXT_MEDIUM, false, 8) }
        }
        v.banners.forEach { line("▲ $it", 44, GamesColors.PRIMARY, true, 4) }
        val sync = WalletHub.lastSync
        WalletTexts.notes(sync?.edition, sync?.notices.orEmpty(), snap).forEach { line("▲ $it", 44, GamesColors.PRIMARY, true, 4) }
        add(spacer(), 8)
        fun action(label: String, op: WalletOp, start: () -> Unit) {
            val g = gate(op)
            add(button(label, if (g.allowed) null else g.reason, g.allowed) { start() }, 14)
        }
        action("Convertir mes jetons", WalletOp.CONVERT) { send(Event.Open(WalletOp.CONVERT, gate(WalletOp.CONVERT))) }
        action("Envoyer des jetons", WalletOp.SEND) { send(Event.Open(WalletOp.SEND, gate(WalletOp.SEND))) }
        action("Recevoir", WalletOp.RECEIVE) { send(Event.Open(WalletOp.RECEIVE, gate(WalletOp.RECEIVE))) }
        action("Historique", WalletOp.HISTORY) { send(Event.Open(WalletOp.HISTORY, gate(WalletOp.HISTORY))) }
        add(button("Actualiser les soldes", null, true) { WalletHub.loadPolicy { render() }; WalletHub.refresh(Trigger.AFTER_OPERATION) { render() } }, 14)
    }

    private fun wheelHint(line: (String, Int, Int, Boolean, Int) -> View) {
        line("▲ ▼ : changer · ◀ ▶ : se déplacer · OK : valider · RETOUR : annuler", 42, GamesColors.TEXT_MEDIUM, false, 18)
    }

    // ---- composants ----

    private fun text(s: String, designPx: Int, color: Int, bold: Boolean = false): TextView = dx.text(TextView(this), designPx, color, bold).apply { text = s }

    private fun spacer() = View(this)

    private fun focusLine(s: String): View = LinearLayout(this).apply {
        isFocusable = true; isFocusableInTouchMode = true
        background = dx.focusable(GamesColors.SURFACE, GamesColors.SURFACE_HIGH, 14)
        setPadding(dx.px(24), dx.px(10), dx.px(24), dx.px(10))
        val t = text("   $s", 44, GamesColors.TEXT_HIGH); addView(t)
        setOnFocusChangeListener { _, f -> t.text = (if (f) "▶ " else "   ") + s }
    }

    /** Un bouton à la télécommande : anneau de focus épais + flèche ▶ ; désactivé : « ✖ » et la raison écrite dessous (jamais le gris seul). */
    private fun button(label: String, reason: String?, enabled: Boolean, onClick: () -> Unit): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; isFocusable = true; isFocusableInTouchMode = true; isClickable = true
        background = dx.focusable(if (enabled) GamesColors.SURFACE_HIGH else GamesColors.SURFACE, GamesColors.SURFACE_HIGH, 18)
        setPadding(dx.px(28), dx.px(16), dx.px(28), dx.px(16))
        val base = (if (enabled) "" else "✖ ") + label
        val t = text("   $base", 50, if (enabled) GamesColors.TEXT_HIGH else GamesColors.TEXT_MEDIUM, true); addView(t)
        if (reason != null) addView(text("   $reason", 42, GamesColors.PRIMARY, false))
        setOnFocusChangeListener { _, f -> t.text = (if (f) "▶ " else "   ") + base }
        setOnClickListener { if (enabled) onClick() else { message = reason; render(keepFocus = true) } }
    }

    private fun cell(ch: String, active: Boolean): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
        background = dx.rounded(if (active) GamesColors.SURFACE_HIGH else GamesColors.SURFACE, 12, if (active) GamesColors.FOCUS_RING else GamesColors.OUTLINE, if (active) 6 else 2)
        addView(text(if (active) "▲" else " ", 42, GamesColors.FOCUS_RING, true).apply { gravity = Gravity.CENTER })
        addView(text(ch, 84, GamesColors.TEXT_HIGH, true).apply { gravity = Gravity.CENTER })
        addView(text(if (active) "▼" else " ", 42, GamesColors.FOCUS_RING, true).apply { gravity = Gravity.CENTER })
    }

    private fun codeWheel(): View {
        val shown = code.text()   // « R123-4567-89 » : 10 caractères et 2 tirets
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        var idx = 0
        shown.forEach { ch ->
            if (ch == '-') row.addView(text("-", 84, GamesColors.TEXT_MEDIUM, true), LinearLayout.LayoutParams(-2, -2).apply { gravity = Gravity.CENTER_VERTICAL })
            else { row.addView(cell(ch.toString(), idx == code.cursor), LinearLayout.LayoutParams(dx.px(96), -2).apply { setMargins(dx.px(4), 0, dx.px(4), 0) }); idx++ }
        }
        return row
    }

    private fun amountWheel(): View {
        val digits = amount.digits()
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        digits.forEachIndexed { i, ch -> row.addView(cell(ch.toString(), i == amount.cursor), LinearLayout.LayoutParams(dx.px(96), -2).apply { setMargins(dx.px(4), 0, dx.px(4), 0) }) }
        return row
    }
}
