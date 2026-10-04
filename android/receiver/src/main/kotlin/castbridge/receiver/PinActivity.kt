package castbridge.receiver

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import castbridge.core.trust.PhonesTexts
import castbridge.core.tv.pin.PinContext
import castbridge.core.tv.pin.PinDisplay
import castbridge.core.tv.pin.PinEvent
import castbridge.core.tv.pin.PinFlow
import castbridge.core.tv.pin.PinFlowState
import castbridge.core.tv.pin.PinOutcome
import castbridge.core.tv.pin.PinPhase
import castbridge.core.tv.pin.PinRules
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * « Code PIN de la TV » : le code en très grand (masqué sous un profil enfant), une ligne d'instruction, les téléphones de confiance
 * (non touchés) et « Générer un nouveau PIN » (confirmation d'abord, « Annuler » présélectionné). Toutes les décisions viennent de
 * castbridge.core.tv.pin (PinRules, PinFlow, PinRegenerator) ; cet écran ne fait que les afficher. Le code n'est montré qu'ici et sur l'accueil :
 * jamais journalisé, notifié ni copié.
 */
class PinActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private var svc: TvService? = null
    private var flow = PinFlowState()
    /** Heure de la dernière régénération réussie de cette ouverture, pour « Nouveau code actif à … ». */
    private var activeSince = 0L
    private lateinit var pinView: TextView
    private lateinit var activeView: TextView
    private lateinit var noticeView: TextView
    private lateinit var phonesView: TextView
    private lateinit var generateBtn: Button
    private val tick = object : Runnable { override fun run() { refresh(); main.postDelayed(this, 1_000) } }

    private fun dp(v: Int) = TvStyle.dp(this, v)

    private fun label(text: String, sp: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        this.text = text; textSize = sp; setTextColor(color); typeface = if (bold) TvFonts.bold else TvFonts.body(this@PinActivity); gravity = Gravity.CENTER_HORIZONTAL
    }

    private fun button(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text; TvStyle.styleButton(this); minimumHeight = dp(56); setOnClickListener { onClick() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.setBackgroundColor(TvStyle.BG)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(dp(40), dp(24), dp(40), dp(20)) }
        root.addView(label("Code PIN de la TV", TvStyle.Type.HEADLINE, TvStyle.TEXT, true))
        pinView = label("", 84f, TvStyle.ACCENT, true).apply { letterSpacing = 0.08f }
        root.addView(pinView, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        activeView = label("", TvStyle.Type.SUBTITLE, TvStyle.TEXT, true)
        root.addView(activeView)
        root.addView(label("Saisissez ce code sur le téléphone, dans CastBridge, une seule fois", TvStyle.Type.SUBTITLE, TvStyle.TEXT2),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        phonesView = label("", TvStyle.Type.CAPTION, TvStyle.TEXT2)
        root.addView(phonesView, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
        noticeView = label("", TvStyle.Type.CAPTION, TvStyle.ERROR, true)
        root.addView(noticeView, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        generateBtn = button("Générer un nouveau PIN") { onGenerate() }
        bar.addView(generateBtn, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(12) })
        bar.addView(button("Retour") { finish() }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(bar, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(20) })
        setContentView(root)
    }

    override fun onStart() {
        super.onStart()
        svc = TvService.running ?: run { TvService.start(this); null }
        refresh(); main.postDelayed(tick, 1_000)
    }

    override fun onStop() { main.removeCallbacks(tick); super.onStop() }

    private fun context() = PinContext(childProfileActive = ParentalHub.pinMasked(), transferInProgress = svc?.transferInProgress() == true)
    private fun decision() = PinRules.decide(context(), svc?.prefs?.pinStore()?.times().orEmpty(), System.currentTimeMillis())

    private fun refresh() {
        val s = svc
        if (s == null || s.pin.isEmpty()) { pinView.text = ""; return }
        val masked = ParentalHub.pinMasked()
        pinView.text = PinDisplay.shown(s.pin, masked)
        activeView.text = if (activeSince > 0 && flow.phase == PinPhase.DONE) "Nouveau code actif · ${SimpleDateFormat("HH:mm:ss", Locale.FRANCE).format(Date(activeSince))}" else ""
        val phones = s.trust.list()
        phonesView.text = "Téléphones de confiance (Bluetooth) : ${PhonesTexts.counter(phones.size)}" + (if (phones.isEmpty()) "" else " · " + phones.joinToString(", ") { it.name }) +
            "\nIls ne sont PAS concernés par un changement de code."
        noticeView.text = flow.notice.orEmpty()
        generateBtn.isEnabled = flow.phase != PinPhase.SAVING
    }

    private fun onGenerate() {
        if (ParentalHub.pinMasked()) { flow = PinFlow.step(flow, PinEvent.GENERATE, decision()); refresh(); return }
        // the parental code first when one exists (no session open): same rule as the other protected actions
        ParentalHub.authorize(this, "Code du parent pour changer le code PIN de la TV") { startFlow() }
    }

    private fun startFlow() {
        flow = PinFlow.step(flow, PinEvent.GENERATE, decision())
        refresh()
        if (flow.phase != PinPhase.CONFIRMING) return
        val d = AlertDialog.Builder(this).setTitle("Générer un nouveau PIN ?").setMessage(PinFlow.CONFIRM_TEXT)
            .setNegativeButton("Annuler") { _, _ -> flow = PinFlow.step(flow, PinEvent.CANCEL, decision()); refresh() }
            .setPositiveButton("Générer") { _, _ -> confirm() }
            .setOnCancelListener { flow = PinFlow.step(flow, PinEvent.CANCEL, decision()); refresh() }
            .create()
        d.setOnShowListener { d.getButton(AlertDialog.BUTTON_NEGATIVE)?.requestFocus() }      // « Annuler » preselected
        d.show()
    }

    private fun confirm() {
        flow = PinFlow.step(flow, PinEvent.CONFIRM, decision())
        refresh()
        if (!flow.write) return
        val s = svc ?: return
        Thread {
            val out = runCatching { s.pinRegenerator.regenerate(context()) }.getOrDefault(PinOutcome.WriteFailed)
            main.post {
                when (out) {
                    is PinOutcome.Done -> { activeSince = out.at; flow = PinFlow.step(flow, PinEvent.SAVED, PinRules.decide(PinContext(), emptyList(), 0)) }
                    is PinOutcome.Refused -> flow = PinFlowState(PinPhase.SHOWING, out.decision.text)
                    PinOutcome.WriteFailed -> flow = PinFlow.step(flow, PinEvent.SAVE_FAILED, PinRules.decide(PinContext(), emptyList(), 0))
                }
                refresh()
            }
        }.start()
    }

    companion object {
        fun open(ctx: Context) { ctx.startActivity(Intent(ctx, PinActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
