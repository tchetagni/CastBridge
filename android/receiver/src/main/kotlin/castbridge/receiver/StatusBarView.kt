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
import android.view.ViewGroup
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
import castbridge.core.tv.PlayerIcons
import castbridge.core.tv.status.Badge
import castbridge.core.tv.status.StatusBadges
import castbridge.core.tv.status.StatusFold
import castbridge.core.tv.status.StatusFoldState
import castbridge.core.tv.status.StatusLevel
import castbridge.core.tv.status.StatusPalette
import castbridge.core.tv.status.StatusRules

/**
 * The status bar, FOLDABLE (owner's request of 2026-10-27: the column over the cards hid their titles). COLLAPSED by default (never remembered): ONE [pill] in the header band beside the clock,
 * with the worst level colour + a shape sign + the count (core [StatusFold.pill]); a change pulses it and lends it a label of 4 s that stays inside the header band (never over the rows).
 * EXPANDED (OK on the pill, the home only): [box] is a panel anchored at the top right, above the content, with one chip per active connection or mode drawn from
 * [castbridge.core.status.StatusIconModel], all labelled, 3 per row; the D-pad moves among the chips and never reaches the cards behind; BACK, OK on the pill or 10 s without a key roll it up
 * ([StatusFoldState]). The pill itself follows the owner's rule of 2026-10-18 (R-53: see [place]): always on the home, never over the MENU panel, over a playing video only with the player's
 * control bar or for 4 s after a change. A chip = kind glyph + small technology mark (shape, never colour alone); DEGRADED is dimmed with a dot; ERROR has the red badge and a dot.
 * Focusable only when [interactive] (the home screen): during a video it never takes the remote's focus; the pill and the chips take it only on purpose (UP from the header chip, then OK).
 */
class StatusBarView(private val act: Activity, private val box: LinearLayout, private val pill: TextView, private val onOpen: () -> Unit, private val leaveFocus: () -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private class Chip(val view: LinearLayout, val label: TextView, val glyph: ImageView, val mark: ImageView, val second: ImageView, val dot: View, val holder: View) {
        var sig = ""; var changedAt = 0L; var errBg: Boolean? = null; var level: StatusLevel? = null
    }
    private val chips = LinkedHashMap<String, Chip>()
    private var order = emptyList<String>()
    private val fold = StatusFoldState()
    private var pulsedAt = 0L
    private var last: StatusBar? = null
    /**
     * Where the bar is drawn, and whether the player's control bar is on screen; the decision is the core `PlayerIcons.barVisible` (owner's rule of 2026-10-18, R-53, which replaces
     * « an alert keeps the zone » of 2026-10-04 for the video): HOME always, PANEL (the MENU is open, with or without a video behind) never, VIDEO only with the control bar
     * ([controlsVisible]: it comes with a short OK) or for 4 s after a chip changes (its passing to an alert included; a standing alert no longer pins the zone), OTHER (the library...) only for those 4 s.
     * An alert still forces the label of its chip ([bind]), not the presence of the zone. During a video (owner's rule of 2026-10-04) every chip carries its French label, in 28 sp
     * with a 40 dp glyph on a dark pill, inside the 5 % safe margins.
     */
    var place = PlayerIcons.Place.HOME
        set(v) { if (field != v) { val was = playerMode; field = v; fold.placeChanged(v); if (was != playerMode) relayout(); last?.let { render(it) } } }
    var controlsVisible = false
        set(v) { if (field != v) { field = v; last?.let { render(it) } } }

    /** The player presentation (labels 28 sp, glyph 40 dp, 5 % margins) is the VIDEO one. */
    private val playerMode get() = place == PlayerIcons.Place.VIDEO

    private fun relayout() {
        val m = act.resources.displayMetrics
        val sf = PlayerIcons.safe(m.widthPixels, m.heightPixels)
        // the pill sits in the header band beside the clock on the home (the clock is ~100 dp wide behind a 40 dp margin); during a video it keeps the safe margins
        (pill.layoutParams as? FrameLayout.LayoutParams)?.let { lp ->
            if (playerMode) lp.setMargins(0, sf.vertical + dp(8), sf.horizontal, 0) else lp.setMargins(0, dp(39), dp(144), 0)
            pill.layoutParams = lp
        }
        (box.layoutParams as? FrameLayout.LayoutParams)?.let { lp -> lp.setMargins(dp(24), dp(88), dp(40), 0); box.layoutParams = lp }
    }

    var interactive = false
        set(v) { field = v; applyFocusable() }

    private fun dp(v: Int) = TvStyle.dp(act, v)

    init {
        box.clipChildren = false; box.orientation = LinearLayout.VERTICAL; box.gravity = Gravity.END
        box.setPadding(dp(10), dp(8), dp(10), dp(4)); box.background = TvStyle.rounded(act, 0xF008121C.toInt(), TvStyle.R_XL)
        pill.apply {
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; maxWidth = dp(440); gravity = Gravity.CENTER
            setTextColor(Color.WHITE); typeface = TvFonts.bold; setPadding(dp(14), dp(5), dp(14), dp(5))
            isFocusable = false; isFocusableInTouchMode = false; isClickable = true
            setOnClickListener { toggle() }
            setOnKeyListener(pillKeys())
        }
        TvStyle.focusZoom(pill) { has -> if (!has) pill.isFocusable = false; last?.let { render(it) } }
        relayout()
    }

    private fun now() = System.currentTimeMillis()

    /** Chips take the focus only when the panel is unrolled on the home; the pill only on purpose ([focusFirst]). */
    private fun applyFocusable() { val f = interactive && fold.expanded; chips.values.forEach { it.view.isFocusable = f } }

    /** Focus the pill (UP from the home header); false when it is not there or not reachable. */
    fun focusFirst(): Boolean {
        if (!interactive || pill.visibility != View.VISIBLE) return false
        pill.isFocusable = true; return pill.requestFocus()
    }

    fun hasFocus() = pill.hasFocus() || box.findFocus() != null

    /** OK on the pill: unroll (first chip gets the focus) or roll up (the pill keeps it). */
    private fun toggle() {
        if (!interactive) return
        if (fold.expanded) rollUp() else if (fold.expand(place, now())) { applyFocusable(); schedule(); last?.let { render(it) }; box.post { chips[order.firstOrNull()]?.view?.requestFocus() } }
    }

    private fun rollUp() {
        val hadFocus = box.findFocus() != null
        fold.collapse(); main.removeCallbacks(autoRoll)
        if (hadFocus) { pill.isFocusable = true; pill.requestFocus() }
        applyFocusable(); last?.let { render(it) }
    }

    private val autoRoll = Runnable { if (fold.tick(now())) rollUp() else schedule() }
    private fun schedule() { main.removeCallbacks(autoRoll); fold.remainingMs(now())?.let { main.postDelayed(autoRoll, it + 20) } }

    /** Every key in the panel pushes the automatic roll-up back (10 s without a key). */
    private fun touched() { fold.key(now()); schedule() }

    private fun pillKeys() = View.OnKeyListener { _, code, e ->
        if (e.action != KeyEvent.ACTION_DOWN) return@OnKeyListener false
        touched()
        when (code) {
            KeyEvent.KEYCODE_BACK -> { if (fold.expanded) rollUp() else leaveFocus(); true }
            KeyEvent.KEYCODE_DPAD_DOWN -> { if (fold.expanded) chips[order.firstOrNull()]?.view?.requestFocus() else leaveFocus(); true }
            KeyEvent.KEYCODE_DPAD_LEFT -> { if (!fold.expanded) leaveFocus(); true }
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_RIGHT -> true
            else -> false
        }
    }

    private fun inBox(v: View?): Boolean { var p: View? = v; while (p != null) { if (p === box) return true; p = p.parent as? View }; return false }

    /** In the panel the D-pad moves among the chips only: a direction with no chip is eaten (the cards behind never take the focus); UP from the first row goes to the pill; BACK rolls up. */
    private fun keys() = View.OnKeyListener { v, code, e ->
        if (e.action != KeyEvent.ACTION_DOWN) return@OnKeyListener false
        touched()
        val dir = when (code) { KeyEvent.KEYCODE_DPAD_UP -> View.FOCUS_UP; KeyEvent.KEYCODE_DPAD_DOWN -> View.FOCUS_DOWN
            KeyEvent.KEYCODE_DPAD_LEFT -> View.FOCUS_LEFT; KeyEvent.KEYCODE_DPAD_RIGHT -> View.FOCUS_RIGHT; else -> 0 }
        when {
            code == KeyEvent.KEYCODE_BACK -> { rollUp(); true }
            dir == 0 -> false
            else -> { val n = v.focusSearch(dir)
                if (n != null && n !== v && inBox(n)) false
                else { if (dir == View.FOCUS_UP) { pill.isFocusable = true; pill.requestFocus() }; true } }
        }
    }

    /**
     * Draws the badges: the measured ones of [TvService.statusBoard] (Wi-Fi, Bluetooth, Internet, Stockage, Téléphones, Licence...) then the connections of [bar].
     * Every badge has its chip in the panel (no « +n »); the pill summarises them ([StatusFold.pill]) and borrows the label of the one that just changed ([StatusFold.transientLabel]).
     */
    fun render(bar: StatusBar) {
        last = bar
        val now = now()
        val first = chips.isEmpty()                              // the very first draw is not a « change »: no label at start-up
        val measured = TvService.running?.statusBoard?.current().orEmpty()
        val iconsById = bar.all.associateBy { it.id }
        val all = StatusBadges.merge(measured, bar.all)
        allBadges = all
        val ids = all.map { it.id }
        for (b in all) {
            val i = iconsById[b.id]
            val c = chips.getOrPut(b.id) { newChip(b.id).also { it.changedAt = if (first) 0L else now } }
            val sig = b.level.wire + b.stateWord + b.label + i?.tech?.wire + i?.secondary?.wire + i?.count
            if (sig != c.sig) { if (c.sig.isNotEmpty()) c.changedAt = now; c.sig = sig }
            bind(c, b, i, now)
        }
        for (id in chips.keys.filter { it !in ids }) chips.remove(id)?.let { c -> (c.view.parent as? ViewGroup)?.removeView(c.view) }
        if (ids != order) {                                   // arrival/removal only: the order itself is stable (core)
            order = ids
            chips.values.forEach { (it.view.parent as? ViewGroup)?.removeView(it.view) }
            box.removeAllViews()
            var row: LinearLayout? = null
            ids.forEachIndexed { n, id ->
                if (n % PER_ROW == 0) { row = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END }; box.addView(row, LinearLayout.LayoutParams(-2, -2).apply { gravity = Gravity.END; bottomMargin = dp(4) }) }
                row!!.addView(chips[id]!!.view, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(6) })
            }
        }
        val fresh = chips.values.any { now - it.changedAt < StatusFold.LABEL_MS }
        val show = all.isNotEmpty() && PlayerIcons.barVisible(place, controlsVisible, fresh)      // no alert in the decision (R-53)
        pill.visibility = if (show) View.VISIBLE else View.GONE
        box.visibility = if (show && fold.expanded && StatusFold.canExpand(place)) View.VISIBLE else View.GONE
        applyFocusable()
        renderPill(all, now, show)
        main.removeCallbacks(collapse)
        if (fresh) main.postDelayed(collapse, StatusFold.LABEL_MS + 50)
    }

    /** The collapsed item: level colour + shape sign + count, or for 4 s after a change the label of the chip that changed (inside the header band). */
    private fun renderPill(all: List<Badge>, now: Long, show: Boolean) {
        if (!show) return
        val p = StatusFold.pill(all)
        val tr = if (fold.expanded) null else StatusFold.transientLabel(all, chips.mapValues { it.value.changedAt }, now)
        val level = tr?.level ?: p.level
        pill.text = if (tr != null) "${StatusFold.glyph(tr.level)} ${tr.text}" else p.text
        pill.textSize = if (playerMode) PlayerIcons.TEXT_SP.toFloat() else TvStyle.Type.CAPTION
        pill.contentDescription = tr?.description ?: p.description
        pill.background = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; cornerRadius = dp(24).toFloat(); setColor(StatusPalette.fill(level))
            setStroke(dp(if (pill.hasFocus()) 5 else 3), if (pill.hasFocus()) Color.WHITE else StatusPalette.ring(level)) }
        pill.alpha = if (level == StatusLevel.OFF) 0.85f else 1f
        val at = chips.values.maxOfOrNull { it.changedAt } ?: 0L
        if (tr != null && at != pulsedAt) { pulsedAt = at; pill.animate().cancel(); pill.scaleX = 1.3f; pill.scaleY = 1.3f; pill.animate().scaleX(1f).scaleY(1f).setDuration(TvStyle.SLOW.toLong()).start() }   // brief pulse, no banner
    }

    /** Every badge of the last draw (visible or behind « +n »), for the grid of the « Connexions » panel. */
    private var allBadges = emptyList<Badge>()

    private val collapse = Runnable { last?.let { render(it) } }

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
            isFocusable = interactive && fold.expanded; isFocusableInTouchMode = false; isClickable = true
        }
        val c = Chip(row, label, glyph, mark, second, dot, g)
        row.setOnClickListener { onOpen() }
        row.setOnKeyListener(keys())
        TvStyle.focusZoom(row) { last?.let { render(it) } }
        return c
    }

    private fun bind(c: Chip, b: Badge, i: StatusIcon?, now: Long) {
        val degraded = i?.state == IconState.DEGRADED; val error = b.level == StatusLevel.ERROR
        c.glyph.setImageResource(if (i != null) glyphOf(i) else badgeGlyph(b))
        if (i != null) { tech(c.mark, if (i.kind == IconKind.INTERNET) null else i.tech); tech(c.second, i.secondary) } else { c.mark.visibility = View.GONE; c.second.visibility = View.GONE }
        // signal colour (core StatusRules): ring + dot carry the level, the glyph keeps its shape and the French text carries the state word
        val level = b.level
        if (c.level != level) {
            c.level = level
            c.holder.background = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; cornerRadius = dp(24).toFloat()
                setColor(StatusPalette.fill(level)); setStroke(dp(3), StatusPalette.ring(level)) }
            (c.dot.background as GradientDrawable).apply { setColor(StatusPalette.dot(level))
                setStroke(dp(1), if (level == StatusLevel.OFF) StatusPalette.ring(level) else Color.BLACK) }
            c.glyph.alpha = StatusPalette.glyphAlpha(level)
        }
        c.dot.visibility = View.VISIBLE
        if (c.errBg != error) { c.errBg = error; c.view.background = if (error) act.getDrawable(R.drawable.badge_warn_bg) else TvStyle.focusable(act, 0xCC0B3D5C.toInt(), TvStyle.R_XL) }
        c.view.alpha = 1f
        c.view.contentDescription = "${b.description} (${level.colour.lowercase()})"
        c.view.isFocusable = interactive && fold.expanded
        val big = playerMode
        c.label.textSize = if (big) castbridge.core.tv.PlayerIcons.TEXT_SP.toFloat() else TvStyle.Type.CAPTION
        (c.holder.layoutParams as? LinearLayout.LayoutParams)?.let { it.width = dp(if (big) 56 else 38); it.height = dp(if (big) 48 else 34); c.holder.layoutParams = it }
        (c.glyph.layoutParams as? FrameLayout.LayoutParams)?.let { it.width = dp(if (big) castbridge.core.tv.PlayerIcons.ICON_DP else 26); it.height = it.width; c.glyph.layoutParams = it }
        // the panel only exists unrolled: every chip is labelled there
        c.label.text = b.text; c.label.visibility = View.VISIBLE
    }

    private fun badgeGlyph(b: Badge): Int = badgeDrawable(b.id, b.stateWord)

    /** One cell of the grid: ring + dot of the level colour and the full text (label · state word), then the detail line when the badge has one (« 48 Go libres sur 128 Go »). */
    private fun badgeCell(b: Badge): View {
        val disc = ImageView(act).apply {
            setImageResource(badgeGlyph(b)); alpha = StatusPalette.glyphAlpha(b.level); setPadding(dp(6), dp(6), dp(6), dp(6))
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(StatusPalette.fill(b.level)); setStroke(dp(3), StatusPalette.ring(b.level)) }
        }
        return LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(4), dp(6), dp(12), dp(6))
            addView(disc, LinearLayout.LayoutParams(dp(44), dp(44)).apply { rightMargin = dp(10) })
            addView(LinearLayout(act).apply {
                orientation = LinearLayout.VERTICAL
                addView(text(b.text, TvStyle.Type.BODY, TvStyle.TEXT))
                if (b.detail.isNotEmpty()) addView(text(b.detail, TvStyle.Type.CAPTION, TvStyle.TEXT2))
            })
            contentDescription = "${b.description} (${b.level.colour.lowercase()})"
        }
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
        if (allBadges.isNotEmpty()) {                       // every badge, readable from the sofa, two columns (urgent ones first)
            val grid = android.widget.GridLayout(act).apply { columnCount = 2 }
            for (b in allBadges.sortedBy { if (it.urgent) 0 else 1 }) grid.addView(badgeCell(b), android.widget.GridLayout.LayoutParams().apply { width = 0; columnSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f) })
            list.addView(grid, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        }
        if (bar.all.isEmpty()) list.addView(text("Aucune connexion active.", TvStyle.Type.BODY, TvStyle.TEXT2))
        for (i in bar.all) {
            val g = ImageView(act).apply { setImageResource(glyphOf(i)) }
            val head = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                addView(g, LinearLayout.LayoutParams(dp(30), dp(30)).apply { rightMargin = dp(12) })
                addView(text(if (i.kind == IconKind.INTERNET) i.label else "${i.kind.label} · ${i.label}".takeIf { i.label != i.kind.label } ?: i.kind.label, TvStyle.Type.BODY, TvStyle.TEXT)) }
            list.addView(head, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
            val detail = listOfNotNull(i.tech.label.ifEmpty { null }, i.secondary?.let { "aussi : ${it.label}" }, i.sinceText(now),
                i.latencyMs?.let { "$it ms" }, StatusRules.icon(i).let { "${it.level.colour.lowercase()} : ${it.word}" }, if (i.count > 1) "${i.count} sessions" else null).joinToString(" · ")
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

    private companion object { const val PER_ROW = 3 }
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

/** The drawable of a measured badge by id (shared with the « Légende des icônes »); the connection icons keep [statusGlyph]. */
internal fun badgeDrawable(id: String, stateWord: String = ""): Int = when (id) {
    "internet" -> when {
        stateWord == "aucun accès" -> R.drawable.ic_cb_sans_internet
        TvService.running?.netState == castbridge.core.net.NetState.INTERNET_ETHERNET -> R.drawable.ic_cb_ethernet
        TvService.running?.netState == castbridge.core.net.NetState.INTERNET_VIA_PHONE -> R.drawable.ic_cb_passerelle_bluetooth
        else -> R.drawable.ic_cb_test_internet
    }
    "wifi" -> R.drawable.ic_cb_wifi
    "wifi_direct" -> R.drawable.ic_cb_wifi_direct
    "bluetooth" -> R.drawable.ic_cb_bluetooth
    "storage" -> R.drawable.ic_cb_bibliotheque
    "phones" -> R.drawable.ic_cb_sur_le_telephone
    "copy" -> R.drawable.ic_cb_copier
    "licence" -> R.drawable.ic_cb_aide
    "quiz" -> R.drawable.ic_cb_quiz
    "tokens" -> R.drawable.ic_cb_jetons
    else -> R.drawable.ic_cb_reglages
}
