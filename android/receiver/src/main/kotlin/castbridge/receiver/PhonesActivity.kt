package castbridge.receiver

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import castbridge.core.trust.PairCapacityFlow
import castbridge.core.trust.PhoneRoster
import castbridge.core.trust.PhonesTexts

/**
 * « Téléphones synchronisés » : TOUS les téléphones de confiance de cette TV (actifs d'abord), « n / 8 », et « Retirer » pour chacun
 * (confirmation, le téléphone reviendra avec le code de la TV ou une nouvelle approbation). Aucun renommage : le registre ne le prévoit pas
 * (le nom vient du téléphone), l'ajouter demanderait un nouveau format de fichier ; non fait, volontairement.
 *
 * Mode « remplacement » (extra [EXTRA_REPLACE]) : ouvert quand un 9e téléphone demande à s'ajouter ([PairCapacityFlow]). Titre « Cette TV a déjà
 * 8 téléphones : choisissez celui à retirer pour ajouter <nom> », le moins récemment vu est SUGGÉRÉ (focus) mais rien n'est retiré sans le
 * choix du propriétaire ; « Retirer » ajoute alors le nouveau téléphone en une seule écriture ; « Annuler l'ajout » ou 2 minutes sans choix
 * annulent la demande, avec la raison affichée ici (et dite au téléphone). Tous les mots viennent de [PhonesTexts]; toutes les décisions de
 * [PhoneRoster] et [PairCapacityFlow] (rien de décisionnel dans cet écran).
 */
class PhonesActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var title: TextView
    private lateinit var counter: TextView
    private lateinit var info: TextView
    private lateinit var list: LinearLayout
    private lateinit var cancelBtn: Button
    private var dialog: AlertDialog? = null
    private var message: String? = null
    private var sig = ""
    private var svc: TvService? = null
    /** The request whose suggested candidate already got the focus: the focus jumps only once per request, never on a refresh. */
    private var focusedFor: String? = null

    private val onChange: () -> Unit = { main.post { refresh() } }
    private val onEvent: (PairCapacityFlow.Event) -> Unit = { e -> main.post { message = PhonesTexts.tvMessage(e); refresh() } }
    private val tick = object : Runnable { override fun run() { refresh(); main.postDelayed(this, 1_000) } }

    private fun dp(v: Int) = TvStyle.dp(this, v)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.setBackgroundColor(TvStyle.BG)
        setContentView(build())
    }

    private fun label(text: String, sp: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        this.text = text; textSize = sp; setTextColor(color); typeface = if (bold) TvFonts.bold else TvFonts.body(this@PhonesActivity)
    }

    private fun button(text: String, danger: Boolean = false, onClick: () -> Unit) = Button(this).apply {
        this.text = text; TvStyle.styleButton(this); if (danger) setTextColor(TvStyle.ERROR); minimumHeight = dp(48); maxLines = 2
        setOnClickListener { onClick() }
    }

    private fun build(): View {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(40), dp(24), dp(40), dp(20)) }
        title = label(PhonesTexts.TITLE, TvStyle.Type.HEADLINE, TvStyle.TEXT, true).apply { maxLines = 3; ellipsize = TextUtils.TruncateAt.END; contentDescription = PhonesTexts.TITLE }
        root.addView(title)
        counter = label("", TvStyle.Type.SUBTITLE, TvStyle.ACCENT, true)
        root.addView(counter, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        info = label("", TvStyle.Type.CAPTION, TvStyle.TEXT2).apply { maxLines = 3 }
        root.addView(info, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4); bottomMargin = dp(8) })
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { isFocusable = false; isFillViewport = false; addView(list, ViewGroup.LayoutParams(-1, -2)) }, LinearLayout.LayoutParams(-1, 0, 1f))
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val lp = { LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(10) } }
        bar.addView(button(PhonesTexts.ADD) { PairActivity.open(this) }, lp())
        cancelBtn = button(PhonesTexts.CANCEL, danger = true) { svc?.capacity?.cancel() }
        bar.addView(cancelBtn, lp())
        bar.addView(button(PhonesTexts.CLOSE) { svc?.capacity?.cancel(); finish() }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(bar, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        return root
    }

    override fun onStart() {
        super.onStart()
        svc = TvService.running ?: run { TvService.start(this); null }
        svc?.let { it.trust.addListener(onChange); it.capacity.addListener(onEvent) }
        sig = ""
        main.post(tick)
    }

    override fun onStop() {
        main.removeCallbacks(tick)
        svc?.let { it.trust.removeListener(onChange); it.capacity.removeListener(onEvent) }
        dialog?.dismiss()
        super.onStop()
    }

    // ------------------------------------------------------------------ rendering

    private fun refresh() {
        val s = svc ?: TvService.running.also { svc = it; it?.let { x -> x.trust.addListener(onChange); x.capacity.addListener(onEvent) } }
        if (s == null) { info.text = "Démarrage de CastBridge-TV…"; return }
        val state = s.capacity.state()
        val awaiting = state as? PairCapacityFlow.State.AwaitingRemoval
        val roster = awaiting?.roster ?: PhoneRoster.build(s.trust.list(), activeAddresses(s), System.currentTimeMillis())
        title.text = awaiting?.let { PhonesTexts.replaceTitle(it.request.name, it.request.address) } ?: PhonesTexts.TITLE
        title.contentDescription = title.text
        counter.text = roster.counter + " téléphones"
        counter.contentDescription = "${roster.count} téléphones sur ${roster.max}"
        val wait = awaiting?.let { "Le téléphone attend : ${((it.request.deadline - System.currentTimeMillis()) / 1000).coerceAtLeast(0)} s avant l'annulation." }
        info.text = listOfNotNull(message, wait, awaiting?.takeIf { it.sameName }?.let { PhonesTexts.SAME_NAME_WARNING }, roster.overBy.takeIf { it > 0 }?.let { PhonesTexts.overText(it) },
            PhonesTexts.HINT.takeIf { message == null && wait == null }).joinToString("\n")
        cancelBtn.visibility = if (awaiting != null) View.VISIBLE else View.GONE
        val newSig = roster.rows.joinToString("|") { "${it.address}:${it.name}:${it.seenText}:${it.state}:${it.suggested}" } + "#" + awaiting?.request?.address + awaiting?.sameName
        if (newSig == sig) return
        sig = newSig
        render(s, roster, awaiting)
    }

    private fun activeAddresses(s: TvService) = s.presence.statuses().filter { it.state != castbridge.core.trust.PhonePresence.State.DISCONNECTED }.map { it.address }.toSet()

    private fun render(s: TvService, roster: PhoneRoster.View, awaiting: PairCapacityFlow.State.AwaitingRemoval?) {
        val hadFocus = list.hasFocus()
        list.removeAllViews()
        if (roster.rows.isEmpty()) list.addView(label(PhonesTexts.EMPTY, TvStyle.Type.BODY, TvStyle.TEXT2))
        var toFocus: View? = null
        roster.rows.forEach { r ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(10), dp(16), dp(10))
                background = TvStyle.rounded(this@PhonesActivity, TvStyle.CARD, TvStyle.R_MD, TvStyle.OUTLINE, 1)
            }
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            col.addView(label(r.label, TvStyle.Type.SUBTITLE, TvStyle.TEXT, true).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
            col.addView(label("${r.state.label} · ${r.seenText.takeIf { r.state != PhoneRoster.PhoneState.ACTIVE } ?: "en ce moment"} · ${r.addedText}", TvStyle.Type.CAPTION,
                if (r.state == PhoneRoster.PhoneState.ACTIVE) TvStyle.GOOD_TEXT else TvStyle.TEXT2).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END })
            if (r.suggested && awaiting != null) col.addView(label(PhonesTexts.SUGGESTION, TvStyle.Type.CAPTION, TvStyle.ACCENT, true))
            row.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
            val remove = button(PhonesTexts.REMOVE, danger = true) { confirmRemove(s, r, awaiting) }.apply { contentDescription = "${PhonesTexts.REMOVE} : ${r.description}" }
            row.addView(remove, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(12) })
            list.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
            if (awaiting != null && r.suggested) toFocus = remove
            if (toFocus == null && roster.rows.first() === r) toFocus = remove
        }
        val req = awaiting?.request?.address
        if (req != null) { if (focusedFor != req) { focusedFor = req; toFocus?.requestFocus() } }       // M1: the suggestion gets the focus once per request
        else { focusedFor = null; if (hadFocus || !list.hasFocus()) toFocus?.requestFocus() }
    }

    private fun confirmRemove(s: TvService, r: PhoneRoster.Row, awaiting: PairCapacityFlow.State.AwaitingRemoval?) {
        val text = if (awaiting != null) PhonesTexts.confirmReplaceText(r.name, r.address, awaiting.request.name, awaiting.request.address) else PhonesTexts.CONFIRM_REMOVE_TEXT
        AlertDialog.Builder(this).setTitle(PhonesTexts.confirmRemoveTitle(r.name, r.address)).setMessage(text)
            .setPositiveButton(PhonesTexts.REMOVE) { _, _ -> remove(s, r, awaiting?.request?.address) }
            .setNegativeButton("Annuler", null).create().also { d ->
                d.setOnShowListener { d.getButton(AlertDialog.BUTTON_NEGATIVE)?.requestFocus() }     // the harmless answer is the selected one
                dialog = d
            }.show()
    }

    private fun remove(s: TvService, r: PhoneRoster.Row, replacingFor: String?) {
        if (replacingFor != null) {
            // the phone the owner SAW in the dialog: a request replaced meanwhile admits nobody (NotPending)
            message = when (val c = s.capacity.choose(r.address, replacingFor)) {
                is PairCapacityFlow.Choice.Replaced -> PhonesTexts.tvMessage(PairCapacityFlow.Event(PairCapacityFlow.Kind.REPLACED, c.added.name, c.removed.name))
                PairCapacityFlow.Choice.WriteFailed -> PhonesTexts.WRITE_FAILED
                PairCapacityFlow.Choice.NotPending -> PhonesTexts.NOT_PENDING
                PairCapacityFlow.Choice.NotFound -> PhonesTexts.ALREADY_GONE
            }
        } else {
            message = if (s.removePhone(r.address)) PhonesTexts.removed(r.name) else PhonesTexts.ALREADY_GONE
        }
        sig = ""; refresh()
    }

    companion object {
        const val EXTRA_REPLACE = "replace"
        fun open(ctx: Context, replace: Boolean = false) {
            ctx.startActivity(Intent(ctx, PhonesActivity::class.java).putExtra(EXTRA_REPLACE, replace).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        }
    }
}
