package castbridge.receiver

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import castbridge.core.tv.UsbSafeRemoval
import castbridge.core.tv.UsbVolumeState

/**
 * « Préparer le retrait de la clé USB » (MENU, et la tuile « Clé USB » de l'accueil ; docs/STORAGE.md § « Retrait sûr »). Plein écran, télécommande à cinq touches : la TV dit quelles copies écrivent
 * sur la clé, propose d'attendre leur fin ou de les mettre en pause, vide la clé, puis dit « Vous pouvez retirer la clé » (et, pour une éjection complète, « Réglages › Stockage › Éjecter » avec son bouton).
 * Rien n'est décidé ici : les étapes et les mots viennent de [UsbSafeRemoval] (pur, testé), le déroulement vit dans [UsbRemoval] (il continue si l'écran est fermé).
 */
class UsbRemovalActivity : Activity() {
    private lateinit var title: TextView
    private lateinit var lines: LinearLayout
    private lateinit var buttons: LinearLayout
    private lateinit var note: TextView
    private var buttonsSig = ""
    /** Plusieurs clés montées : la personne choisit laquelle retirer avant que la préparation s'ouvre. */
    private var choosing: List<UsbVolumeWatch.Entry> = emptyList()
    private var noKey = false

    private val onChange: () -> Unit = { render() }

    private fun dp(v: Int) = TvStyle.dp(this, v)

    private fun label(text: String, sp: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        this.text = text; textSize = sp; setTextColor(color); typeface = if (bold) TvFonts.bold else TvFonts.body(this@UsbRemovalActivity)
    }

    private fun button(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text; TvStyle.styleButton(this); minimumHeight = dp(52); maxLines = 2; isAllCaps = false
        setOnClickListener { onClick() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.setBackgroundColor(TvStyle.BG)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(56), dp(32), dp(56), dp(24)) }
        title = label(UsbVolumeState.PREPARE_LABEL, TvStyle.Type.HEADLINE, TvStyle.TEXT, true).apply { maxLines = 3; ellipsize = TextUtils.TruncateAt.END }
        root.addView(title)
        lines = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { isFocusable = false; addView(lines, ViewGroup.LayoutParams(-1, -2)) }, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(16) })
        note = label("", TvStyle.Type.CAPTION, TvStyle.ACCENT).apply { visibility = View.GONE }
        root.addView(note, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        buttons = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(buttons, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        UsbRemoval.addListener(onChange); UsbVolumeWatch.addListener(onChange)
        begin()
        render()
    }

    override fun onPause() {
        UsbRemoval.removeListener(onChange); UsbVolumeWatch.removeListener(onChange)
        super.onPause()
    }

    /** What to show when the screen opens: a preparation already under way, or the key (the choice among several keys, or none). */
    private fun begin() {
        val v = UsbRemoval.view
        if (v != null && v.step !in FINISHED) return                       // under way (or prepared: « Vous pouvez retirer la clé »)
        if (v != null) UsbRemoval.forget()                                 // an old one, finished: start clean
        val keys = UsbVolumeWatch.mountedKeys()
        choosing = emptyList(); noKey = false
        when (keys.size) {
            0 -> noKey = true
            1 -> UsbRemoval.open(keys[0].id, keys[0].label)
            else -> choosing = keys
        }
    }

    private fun render() {
        runOnUiThread { draw() }
    }

    private fun draw() {
        if (isFinishing) return
        val v = UsbRemoval.view
        lines.removeAllViews()
        val wanted = ArrayList<Pair<String, () -> Unit>>()
        when {
            v != null && choosing.isEmpty() -> {
                title.text = v.title
                v.lines.forEach { lines.addView(label(it, TvStyle.Type.SUBTITLE, if (v.step == UsbSafeRemoval.Step.READY) TvStyle.GOOD_TEXT else TvStyle.TEXT), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }) }
                if (v.busy) lines.addView(label("…", TvStyle.Type.SUBTITLE, TvStyle.TEXT2))
                v.buttons.forEach { b -> wanted += b.label to { onButton(b) } }
            }
            choosing.isNotEmpty() -> {
                title.text = UsbVolumeState.PREPARE_LABEL
                lines.addView(label("Plusieurs clés USB sont branchées. Laquelle retirer ?", TvStyle.Type.SUBTITLE, TvStyle.TEXT))
                choosing.forEach { e -> wanted += UsbVolumeState.subject(e.label) to { choosing = emptyList(); UsbRemoval.open(e.id, e.label); draw() } }
                wanted += "Fermer" to { finish() }
            }
            !noKey -> {                                                    // the preparation is being opened (a moment): nothing to ask yet
                title.text = UsbVolumeState.PREPARE_LABEL
                lines.addView(label("Préparation…", TvStyle.Type.SUBTITLE, TvStyle.TEXT2))
            }
            else -> {
                title.text = UsbVolumeState.PREPARE_LABEL
                val why = UsbVolumeWatch.attention()?.verdict?.line
                lines.addView(label(why ?: "Aucune clé USB montée sur la TV : rien à retirer.", TvStyle.Type.SUBTITLE, TvStyle.TEXT), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
                if (why == null) lines.addView(label("Si la clé vient d'être branchée, attendez qu'Android la vérifie, puis rouvrez cet écran.", TvStyle.Type.CAPTION, TvStyle.TEXT2))
                wanted += "Fermer" to { finish() }
            }
        }
        val sig = wanted.joinToString("|") { it.first }
        if (sig != buttonsSig) {
            buttonsSig = sig
            buttons.removeAllViews()
            wanted.forEach { (text, act) -> buttons.addView(button(text, act), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }) }
            buttons.getChildAt(0)?.requestFocus()
        } else {
            // same buttons: only the actions behind them may have changed (a chosen key): they are replaced in place, the focus stays
            for (i in 0 until buttons.childCount) (buttons.getChildAt(i) as? Button)?.setOnClickListener { wanted.getOrNull(i)?.second?.invoke() }
        }
    }

    private fun onButton(b: UsbSafeRemoval.Button) {
        note.visibility = View.GONE
        when (b) {
            UsbSafeRemoval.Button.SETTINGS -> if (!UsbVolumeWatch.openStorageSettings(this)) {
                note.text = "Cette TV n'a pas d'écran de réglages de stockage : ouvrez ses réglages à la main (Stockage › Éjecter), ou retirez la clé quand la TV dit qu'elle peut l'être."
                note.visibility = View.VISIBLE
            }
            UsbSafeRemoval.Button.CLOSE -> { UsbRemoval.forget(); finish() }
            else -> UsbRemoval.press(b)
        }
    }

    companion object {
        private val FINISHED = setOf(UsbSafeRemoval.Step.GONE, UsbSafeRemoval.Step.EXPIRED, UsbSafeRemoval.Step.CLOSED)

        fun open(ctx: Context) {
            ctx.startActivity(Intent(ctx, UsbRemovalActivity::class.java).apply { if (ctx !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP))
        }
    }
}
