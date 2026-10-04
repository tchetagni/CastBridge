package castbridge.receiver

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import castbridge.core.tv.AutoHide
import castbridge.core.tv.PlayerIcons
import castbridge.core.tv.PlayerRemote

/**
 * Barre de commandes à l'écran du lecteur (télécommande de base) : « Pause », « Audio », « Sous-titres », « Affichage », « Infos », chacune avec son icône ET son libellé.
 * Elle apparaît sur OK, se cache seule après 5 s sans touche (jamais en pause) ; la ligne de diagnostic « Affichage : … » occupe le haut à gauche 6 s. Les règles (durées, marges,
 * tailles) viennent de `core/tv` (testées) ; ici seulement les vues, que seule la TV réelle peut juger (P-54).
 */
class PlayerControls(private val act: Activity, parent: FrameLayout, private val api: Api) {
    interface Api {
        fun playing(): Boolean
        fun togglePause()
        fun audio()
        fun subtitles()
        fun display()
        fun infos()
        /** Redessine la ligne de progression et le temps (appelé chaque seconde tant que la barre est visible). */
        fun refreshProgress()
        /** La barre apparaît ou disparaît : la zone d'icônes de statut la suit. */
        fun zoneChanged(visible: Boolean)
    }

    private val main = Handler(Looper.getMainLooper())
    private val hold = AutoHide(PlayerRemote.BAR_MS)
    private val diagHold = AutoHide(PlayerRemote.DIAG_MS)
    private val safe = PlayerIcons.safe(act.resources.displayMetrics.widthPixels, act.resources.displayMetrics.heightPixels)
    var visible = false; private set

    private fun dp(v: Int) = TvStyle.dp(act, v)

    private fun button(label: String, icon: Int, run: () -> Unit) = Button(act).apply {
        TvStyle.styleButton(this)
        text = label; textSize = PlayerIcons.TEXT_SP.toFloat(); setSingleLine(true)
        setIcon(this, icon)
        compoundDrawablePadding = dp(4)
        setOnClickListener { touch(); run() }
    }

    private fun setIcon(b: Button, icon: Int) {
        val d = act.getDrawable(icon)?.mutate() ?: return
        d.setBounds(0, 0, dp(PlayerIcons.ICON_DP), dp(PlayerIcons.ICON_DP))
        b.setCompoundDrawables(null, d, null, null)
    }

    private val pause = button("Pause", R.drawable.ic_cb_pause) { api.togglePause(); refreshPause() }
    private val row = LinearLayout(act).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; visibility = View.GONE
        // fermer la barre avant d'ouvrir un choix : le dialogue prend la main, la barre ne reste pas derrière
        listOf(pause,
            button("Audio", R.drawable.ic_cb_pistes_audio) { hide(); api.audio() },
            button("Sous-titres", R.drawable.ic_cb_sous_titres) { hide(); api.subtitles() },
            button("Affichage", R.drawable.ic_cb_reglages) { hide(); api.display() },
            button("Infos", R.drawable.ic_cb_aide) { hide(); api.infos() },
        ).forEach { addView(it, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(6), 0, dp(6), 0) }) }
    }
    private val diag = TextView(act).apply {
        setTextColor(Color.WHITE); textSize = 22f; typeface = TvFonts.bold; visibility = View.GONE
        isFocusable = false; isClickable = false; setPadding(dp(14), dp(8), dp(14), dp(8))
        background = GradientDrawable().apply { setColor((PlayerIcons.PILL_ALPHA shl 24)); cornerRadius = dp(12).toFloat() }
    }

    init {
        // Marges de sécurité de 5 % (surbalayage) ; la barre se pose au-dessus de la ligne de progression du bas.
        parent.addView(row, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply { setMargins(safe.horizontal, 0, safe.horizontal, safe.vertical + dp(110)) })
        parent.addView(diag, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply { setMargins(safe.horizontal, safe.vertical + dp(52), 0, 0) })
    }

    private fun now() = SystemClock.elapsedRealtime()

    private fun refreshPause() {
        val playing = api.playing()
        pause.text = if (playing) "Pause" else "Lecture"
        setIcon(pause, if (playing) R.drawable.ic_cb_pause else R.drawable.ic_cb_lecture)
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!visible) return
            if (!hold.visible(now(), paused = !api.playing())) { hide(); return }
            refreshPause(); api.refreshProgress()
            main.postDelayed(this, 1000)
        }
    }

    fun show() {
        visible = true
        hold.touch(now()); refreshPause()
        row.visibility = View.VISIBLE; pause.requestFocus()
        api.zoneChanged(true)
        main.removeCallbacks(tick); main.post(tick)
    }

    /** Une touche pressée pendant que la barre est visible repousse la disparition. */
    fun touch() { if (visible) hold.touch(now()) }

    fun hide() {
        if (!visible) return
        visible = false; main.removeCallbacks(tick)
        row.visibility = View.GONE
        api.zoneChanged(false)
    }

    /** La ligne du diagnostic pendant 6 s (au début de la lecture, à chaque changement d'affichage, et par « Infos »). */
    fun showDiag(text: String) {
        diag.text = text; diag.visibility = View.VISIBLE; diagHold.touch(now())
        main.removeCallbacks(hideDiag); main.postDelayed(hideDiag, PlayerRemote.DIAG_MS)
    }

    /** Met le texte à jour (la dalle ou l'image se précisent après le début) sans prolonger l'affichage. */
    fun refreshDiag(text: String) { if (diag.visibility == View.VISIBLE && diag.text.toString() != text) diag.text = text }

    private val hideDiag = Runnable { if (!diagHold.visible(now(), paused = false)) diag.visibility = View.GONE }

    fun release() { main.removeCallbacksAndMessages(null); visible = false; row.visibility = View.GONE; diag.visibility = View.GONE }
}

/**
 * « Légende des icônes » (bouton « Infos ») : la ligne de diagnostic, puis chaque icône du lecteur avec son libellé et sa signification (texte de `core/tv/PlayerIcons.legend`).
 * Le bouton « Infos techniques » ouvre l'ancienne fenêtre (codec, décodage, mémoire).
 */
object IconLegend {
    fun show(act: Activity, diagLine: String, technical: () -> Unit) {
        fun dp(v: Int) = TvStyle.dp(act, v)
        val list = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(16), dp(24), dp(8)) }
        list.addView(TextView(act).apply { text = diagLine; textSize = 20f; setTextColor(TvStyle.TEXT) })
        for (e in PlayerIcons.legend()) {
            val glyph = android.widget.ImageView(act)
            val kind = e.kind
            val level = if (e.id.startsWith("level-")) castbridge.core.tv.status.StatusLevel.fromWire(e.id.removePrefix("level-")) else null
            if (level != null) glyph.setImageDrawable(android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(castbridge.core.tv.status.StatusPalette.fill(level)); setStroke(dp(4), castbridge.core.tv.status.StatusPalette.ring(level)) })
            else if (kind != null) glyph.setImageResource(statusGlyph(kind, castbridge.core.status.Tech.WIFI_LAN))
            else when {
                e.id.startsWith("tech-") -> glyph.setImageResource(when (e.id) {
                    "tech-bluetooth" -> R.drawable.ic_cb_bluetooth; "tech-ethernet" -> R.drawable.ic_cb_ethernet
                    "tech-wifi_direct" -> R.drawable.ic_cb_wifi_direct; "tech-usb" -> R.drawable.ic_cb_cle_usb; else -> R.drawable.ic_cb_wifi })
                e.id == "copy" -> glyph.setImageResource(R.drawable.ic_cb_copier)
                e.id == "edition" -> glyph.setImageResource(R.drawable.ic_cb_aide)
                else -> glyph.setImageResource(R.drawable.ic_cb_reglages)
            }
            val head = LinearLayout(act).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                addView(glyph, LinearLayout.LayoutParams(dp(PlayerIcons.ICON_DP), dp(PlayerIcons.ICON_DP)).apply { rightMargin = dp(14) })
                addView(TextView(act).apply { text = e.label; textSize = PlayerIcons.TEXT_SP.toFloat() * 0.75f; typeface = TvFonts.bold; setTextColor(TvStyle.TEXT) })
            }
            list.addView(head, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
            list.addView(TextView(act).apply { text = e.meaning; textSize = 18f; setTextColor(TvStyle.TEXT2) }, LinearLayout.LayoutParams(-1, -2).apply { leftMargin = dp(PlayerIcons.ICON_DP + 14) })
        }
        val sv = android.widget.ScrollView(act).apply { addView(list); setBackgroundColor(TvStyle.BG_ELEVATED) }
        android.app.AlertDialog.Builder(act).setTitle("Légende des icônes").setView(sv)
            .setPositiveButton("Fermer", null).setNeutralButton("Infos techniques") { _, _ -> technical() }.show()
    }
}
