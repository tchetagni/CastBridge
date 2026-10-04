package castbridge.receiver

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import castbridge.core.status.IconKind
import castbridge.core.status.IconState
import castbridge.core.status.StatusBar
import castbridge.core.status.StatusIcon
import castbridge.core.status.Tech

/**
 * The permanent status bar: a column at the top right (under the clock, over every screen including a playing video) with one
 * chip per active connection or mode, drawn from [castbridge.core.status.StatusIconModel]. A chip = kind glyph + small technology
 * mark (shape, never colour alone); DEGRADED is dimmed with a dot and says « reconnexion »; ERROR has the red badge and a dot.
 * The French label shows only while the chip is new / just changed (4 s), focused, or the bar is expanded (OK on the home).
 * Focusable only when [interactive] (the home screen): during a video it never takes the remote's focus.
 */
class StatusBarView(private val act: Activity, private val box: LinearLayout, private val onOpen: () -> Unit, private val leaveFocus: () -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private class Chip(val view: LinearLayout, val label: TextView, val glyph: ImageView, val mark: ImageView, val second: ImageView, val dot: View, val holder: View) {
        var sig = ""; var changedAt = 0L; var errBg: Boolean? = null
    }
    private val chips = LinkedHashMap<String, Chip>()
    private val more = TextView(act)
    private var order = emptyList<String>()
    private var last: StatusBar? = null
    /**
     * During a video (owner's rule of 2026-10-04): every chip carries its French label, in 28 sp with a 40 dp glyph on a dark pill, inside the 5 % safe margins, and the whole
     * zone follows the player's control bar ([zoneShown]); an error / reconnection or a change of the last 4 s keeps it on screen (core `PlayerIcons.zoneVisible`).
     */
    var playerMode = false
        set(v) { if (field != v) { field = v; relayout(); last?.let { render(it) } } }
    var zoneShown = true
        set(v) { if (field != v) { field = v; last?.let { render(it) } } }

    private fun relayout() {
        val lp = box.layoutParams as? FrameLayout.LayoutParams ?: return
        val m = act.resources.displayMetrics
        val s = castbridge.core.tv.PlayerIcons.safe(m.widthPixels, m.heightPixels)
        if (playerMode) lp.setMargins(0, s.vertical + dp(48), s.horizontal, 0) else lp.setMargins(dp(24), dp(84), dp(24), 0)
        box.layoutParams = lp
    }

    var interactive = false
        set(v) { field = v; chips.values.forEach { it.view.isFocusable = v }; more.isFocusable = v && more.visibility == View.VISIBLE }

    private fun dp(v: Int) = TvStyle.dp(act, v)

    init {
        more.apply {
            setTextColor(Color.WHITE); textSize = TvStyle.Type.CAPTION; typeface = TvFonts.bold; gravity = Gravity.CENTER; visibility = View.GONE
            setPadding(dp(12), dp(4), dp(12), dp(4)); background = act.getDrawable(R.drawable.badge_bg); setOnClickListener { onOpen() }
            setOnKeyListener(keys(true))
        }
        box.clipChildren = false
    }

    /** Focus the first chip (UP from the home header); false when the bar is empty. */
    fun focusFirst(): Boolean = chips.values.firstOrNull()?.view?.requestFocus() ?: false

    fun hasFocus() = box.findFocus() != null

    fun render(bar: StatusBar) {
        last = bar
        val now = System.currentTimeMillis()
        val ids = bar.icons.map { it.id }
        for (i in bar.icons) {
            val c = chips.getOrPut(i.id) { newChip(i.id).also { it.changedAt = now; it.view.alpha = 0f; it.view.scaleX = 1.25f; it.view.scaleY = 1.25f
                it.view.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(TvStyle.SLOW.toLong()).start() } }   // brief highlight, no banner
            val sig = i.tech.wire + i.secondary?.wire + i.state.wire + i.count + i.label
            if (sig != c.sig) { if (c.sig.isNotEmpty()) c.changedAt = now; c.sig = sig }
            bind(c, i, now)
        }
        for (id in chips.keys.filter { it !in ids }) chips.remove(id)?.let { c -> c.view.animate().alpha(0f).setDuration(300).withEndAction { box.removeView(c.view) }.start() }
        if (ids != order) {                                   // arrival/removal only: the order itself is stable (core)
            order = ids
            box.removeAllViews(); ids.forEach { box.addView(chips[it]!!.view, params()) }
            box.addView(more, params())
        }
        more.visibility = if (bar.hidden > 0) View.VISIBLE else View.GONE
        more.isFocusable = interactive && bar.hidden > 0
        more.text = "+${bar.hidden}"; more.contentDescription = "${bar.hidden} autres connexions"
        val alert = bar.icons.any { it.state == IconState.DEGRADED || it.state == IconState.ERROR }
        val fresh = chips.values.any { now - it.changedAt < SHOW_MS }
        box.visibility = if (bar.icons.isEmpty() || (playerMode && !castbridge.core.tv.PlayerIcons.zoneVisible(zoneShown, alert, fresh))) View.GONE else View.VISIBLE
        main.removeCallbacks(collapse)
        if (chips.values.any { now - it.changedAt < SHOW_MS }) main.postDelayed(collapse, SHOW_MS)
    }

    private val collapse = Runnable { last?.let { render(it) } }

    private fun params() = LinearLayout.LayoutParams(-2, -2).apply { gravity = Gravity.END; bottomMargin = dp(6) }

    private fun newChip(id: String): Chip {
        val label = TextView(act).apply { setTextColor(Color.WHITE); textSize = TvStyle.Type.CAPTION; typeface = TvFonts.bold; maxLines = 1
            setPadding(dp(12), 0, dp(4), 0); visibility = View.GONE }
        val glyph = ImageView(act); val mark = ImageView(act); val second = ImageView(act)
        val dot = View(act).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(TvStyle.ACCENT); setStroke(dp(1), Color.BLACK) }; visibility = View.GONE }
        val g = FrameLayout(act).apply {
            addView(glyph, FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER))
            addView(mark, FrameLayout.LayoutParams(dp(14), dp(14), Gravity.BOTTOM or Gravity.END).apply { setMargins(0, 0, dp(1), dp(1)) })
            addView(second, FrameLayout.LayoutParams(dp(10), dp(10), Gravity.BOTTOM or Gravity.START).apply { setMargins(dp(1), 0, 0, dp(1)) })
            addView(dot, FrameLayout.LayoutParams(dp(10), dp(10), Gravity.TOP or Gravity.END))
        }
        val row = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(8), dp(5), dp(8), dp(5))
            addView(label); addView(g, LinearLayout.LayoutParams(dp(38), dp(34)))
            isFocusable = interactive; isFocusableInTouchMode = false; isClickable = true
        }
        val c = Chip(row, label, glyph, mark, second, dot, g)
        row.setOnClickListener { onOpen() }
        row.setOnKeyListener(keys(false))
        TvStyle.focusZoom(row) { last?.let { render(it) } }
        return c
    }

    /** UP on the first chip stays; DOWN on the last one (or BACK) hands the focus back to the home. */
    private fun keys(isMore: Boolean) = View.OnKeyListener { v, code, e ->
        if (e.action != KeyEvent.ACTION_DOWN) return@OnKeyListener false
        val views = box.let { b -> (0 until b.childCount).map { b.getChildAt(it) }.filter { it.visibility == View.VISIBLE && it.isFocusable } }
        when (code) {
            KeyEvent.KEYCODE_BACK -> { leaveFocus(); true }
            KeyEvent.KEYCODE_DPAD_DOWN -> if (v === views.lastOrNull()) { leaveFocus(); true } else false
            KeyEvent.KEYCODE_DPAD_UP -> v === views.firstOrNull()
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> true
            else -> false
        }
    }

    private fun bind(c: Chip, i: StatusIcon, now: Long) {
        val degraded = i.state == IconState.DEGRADED; val error = i.state == IconState.ERROR
        c.glyph.setImageResource(glyphOf(i))
        tech(c.mark, if (i.kind == IconKind.INTERNET) null else i.tech); tech(c.second, i.secondary)
        c.dot.visibility = if (degraded || error || i.state == IconState.CONNECTING) View.VISIBLE else View.GONE
        (c.dot.background as GradientDrawable).setColor(if (error) TvStyle.ERROR else TvStyle.ACCENT)
        if (c.errBg != error) { c.errBg = error; c.view.background = if (error) act.getDrawable(R.drawable.badge_warn_bg) else TvStyle.focusable(act, 0xCC0B3D5C.toInt(), TvStyle.R_XL) }
        c.view.alpha = if (degraded || i.state == IconState.CONNECTING) 0.65f else 1f
        c.view.contentDescription = i.text()
        c.view.isFocusable = interactive
        val big = playerMode
        c.label.textSize = if (big) castbridge.core.tv.PlayerIcons.TEXT_SP.toFloat() else TvStyle.Type.CAPTION
        (c.holder.layoutParams as? LinearLayout.LayoutParams)?.let { it.width = dp(if (big) 56 else 38); it.height = dp(if (big) 48 else 34); c.holder.layoutParams = it }
        (c.glyph.layoutParams as? FrameLayout.LayoutParams)?.let { it.width = dp(if (big) castbridge.core.tv.PlayerIcons.ICON_DP else 26); it.height = it.width; c.glyph.layoutParams = it }
        val show = big || c.view.hasFocus() || bigBar || now - c.changedAt < SHOW_MS || degraded || error
        c.label.text = i.text(); c.label.visibility = if (show) View.VISIBLE else View.GONE
    }
    /** OK on the home expands every label for a while (see [openPanel]). */
    private var bigBar = false

    private fun tech(v: ImageView, t: Tech?) {
        val res = when (t) {
            Tech.BLUETOOTH, Tech.BLUETOOTH_TUNNEL, Tech.SSH_OVER_BLUETOOTH -> R.drawable.ic_cb_bluetooth
            Tech.WIFI_LAN, Tech.SSH_OVER_LAN -> R.drawable.ic_cb_wifi
            Tech.WIFI_DIRECT -> R.drawable.ic_cb_wifi_direct
            Tech.ETHERNET -> R.drawable.ic_cb_ethernet
            Tech.USB -> R.drawable.ic_cb_cle_usb
            else -> 0
        }
        if (res == 0) { v.visibility = View.GONE; return }
        v.setImageResource(res); v.visibility = View.VISIBLE
        v.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFF0B3D5C.toInt()) }
        v.setPadding(dp(1), dp(1), dp(1), dp(1))
    }

    private fun glyphOf(i: StatusIcon) = statusGlyph(i.kind, i.tech)

    /** The « Connexions » panel: every active connection, readable from the sofa, with the actions that make sense. */
    fun openPanel(svc: TvService) {
        val bar = svc.icons.snapshot(); val now = System.currentTimeMillis()
        val list = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(16), dp(24), dp(8)) }
        var dialog: AlertDialog? = null
        list.addView(action(castbridge.core.trust.PhonesTexts.menuEntry(svc.trust.list().size)) { dialog?.dismiss(); PhonesActivity.open(act) })
        if (bar.all.isEmpty()) list.addView(text("Aucune connexion active.", TvStyle.Type.BODY, TvStyle.TEXT2))
        for (i in bar.all) {
            val g = ImageView(act).apply { setImageResource(glyphOf(i)) }
            val head = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                addView(g, LinearLayout.LayoutParams(dp(30), dp(30)).apply { rightMargin = dp(12) })
                addView(text(if (i.kind == IconKind.INTERNET) i.label else "${i.kind.label} · ${i.label}".takeIf { i.label != i.kind.label } ?: i.kind.label, TvStyle.Type.BODY, TvStyle.TEXT)) }
            list.addView(head, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
            val detail = listOfNotNull(i.tech.label.ifEmpty { null }, i.secondary?.let { "aussi : ${it.label}" }, i.sinceText(now),
                i.latencyMs?.let { "$it ms" }, i.state.label.ifEmpty { null }, if (i.count > 1) "${i.count} sessions" else null).joinToString(" · ")
            list.addView(text(detail, TvStyle.Type.CAPTION, TvStyle.TEXT2), LinearLayout.LayoutParams(-1, -2).apply { leftMargin = dp(42) })
            if (i.kind == IconKind.PHONE && i.ref.isNotEmpty()) list.addView(action("Retirer ce téléphone") {
                confirm("Retirer ${i.label} ?", "Ce téléphone ne pourra plus piloter la TV sans le code. Il pourra être ajouté à nouveau.", "Retirer") {
                    svc.removePhone(i.ref); dialog?.dismiss()
                }
            })
            if (i.kind == IconKind.SSH) list.addView(action("Arrêter SSH") {
                confirm("Arrêter SSH ?", "Les connexions SSH ouvertes seront coupées. Vous pourrez le réactiver depuis le menu.", "Arrêter") {
                    svc.ssh?.disable(); svc.icons.setSsh(0); svc.iconsChanged(); dialog?.dismiss()
                }
            })
        }
        if (bar.hidden > 0) list.addView(text("${bar.hidden} de plus ne tiennent pas dans la barre.", TvStyle.Type.CAPTION, TvStyle.TEXT2), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
        val sv = ScrollView(act).apply { addView(list); setBackgroundColor(TvStyle.BG_ELEVATED) }
        dialog = AlertDialog.Builder(act).setTitle("Connexions").setView(sv).setPositiveButton("Fermer", null).create().also { d ->
            d.setOnShowListener { d.getButton(AlertDialog.BUTTON_POSITIVE)?.requestFocus() }
            d.setOnDismissListener { bigBar = false; last?.let { render(it) } }
        }
        bigBar = true; last?.let { render(it) }
        dialog?.show()
    }

    private fun text(t: String, size: Float, color: Int) = TextView(act).apply { text = t; textSize = size; setTextColor(color) }

    private fun action(label: String, run: () -> Unit) = Button(act).apply { text = label; TvStyle.styleButton(this); setOnClickListener { run() } }.also {
        it.layoutParams = LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(42); topMargin = dp(8) }
    }

    /** Confirmation where the harmless answer (Annuler) is selected, like the phones panel. */
    private fun confirm(title: String, msg: String, yes: String, run: () -> Unit) {
        AlertDialog.Builder(act).setTitle(title).setMessage(msg).setPositiveButton(yes) { _, _ -> run() }.setNegativeButton("Annuler", null).create().also { d ->
            d.setOnShowListener { d.getButton(AlertDialog.BUTTON_NEGATIVE)?.requestFocus() }
        }.show()
    }

    private companion object { const val SHOW_MS = 4_000L }
}

/** The drawable of a status icon kind (shared with the player's « Légende des icônes »). */
internal fun statusGlyph(kind: IconKind, tech: Tech): Int = when (kind) {
        IconKind.INTERNET -> when (tech) { Tech.ETHERNET -> R.drawable.ic_cb_ethernet; Tech.NONE -> R.drawable.ic_cb_sans_internet
            Tech.BLUETOOTH -> R.drawable.ic_cb_passerelle_bluetooth; else -> R.drawable.ic_cb_wifi }
        IconKind.PHONE -> R.drawable.ic_cb_sur_le_telephone
        IconKind.REMOTE_CONTROL -> R.drawable.ic_cb_telecommande
        IconKind.SSH -> R.drawable.ic_cb_administration
        IconKind.GATEWAY -> R.drawable.ic_cb_passerelle_bluetooth
        IconKind.CAST -> R.drawable.ic_cb_caster
        IconKind.USB_DRIVE -> R.drawable.ic_cb_cle_usb
        IconKind.WIFI_DIRECT_GROUP -> R.drawable.ic_cb_wifi_direct
        IconKind.QUIZ_PLAYER -> R.drawable.ic_cb_quiz
        IconKind.CHESS_PLAYER -> R.drawable.ic_cb_echecs
        IconKind.DOWNLOAD -> R.drawable.ic_cb_telechargements
        IconKind.PARENTAL_MODE -> R.drawable.ic_t_parental
        IconKind.UPDATE -> R.drawable.ic_cb_mises_a_jour
    }
