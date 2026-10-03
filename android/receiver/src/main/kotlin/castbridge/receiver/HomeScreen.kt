package castbridge.receiver

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextClock
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import castbridge.core.tv.LibraryItem
import castbridge.core.tv.LibraryLogic
import castbridge.core.tv.LibrarySections
import castbridge.core.tv.MediaType
import castbridge.core.tv.VolumeKind
import java.util.concurrent.Executors

/**
 * The TV home, like a media launcher: blurred, slowly moving background taken from the focused (or last watched) video,
 * clock, a small "ready" band with the code partly hidden, and rows "Reprendre", "Récemment ajoutés", "Sur la clé USB",
 * "Toutes les vidéos", "Autres fichiers", then tiles (whole library, connection & settings, help). D-pad only: the focused
 * card zooms with a blue halo, the hero text above the rows describes it. No technical wording here: IP, PIN, SSH,
 * Bluetooth and storage details live in "Connexion & réglages" (MENU).
 *
 * Memory and GPU: the background is the card's own 320 px thumbnail shrunk once to 48x27 pixels on the CPU (a few kB) and
 * stretched by the view with bilinear filtering, which blurs it for free; no per-frame blur effect (the slow zoom only
 * transforms a static image). One row of cards = one RecyclerView (only visible cards exist).
 */
class HomeScreen(private val act: Activity, private val container: FrameLayout, private val thumbs: TvThumbs, private val api: Api) {
    interface Api {
        fun items(): List<LibraryItem>
        /** (ready text, code, receiving line or null). */
        fun status(): Triple<String, String, String?>
        /** The status signalling (castbridge.core.ux.TvSignal): colour, text and the one action. null = old chip. */
        fun signal(): castbridge.core.ux.TvSignalView? = null
        fun open(i: LibraryItem, row: List<LibraryItem>, index: Int)
        fun actions(i: LibraryItem, row: List<LibraryItem>, index: Int)
        fun openLibrary()
        fun openSettings()
        fun openHelp()
        /** Every feature as an icon, with its live status (the order is the order on screen). */
        fun tools(): List<HomeTool> = emptyList()
    }

    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "cb-home").apply { isDaemon = true } }
    private val dp = { v: Int -> TvStyle.dp(act, v) }
    private val bg = ImageView(act).apply { scaleType = ImageView.ScaleType.CENTER_CROP; alpha = 0.5f }
    private val zoom = SlowZoom(bg)
    private val heroTitle = TextView(act).apply { setTextColor(Color.WHITE); textSize = 34f; typeface = TvFonts.bold; maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
    private val heroSub = TextView(act).apply { setTextColor(TvStyle.TEXT2); textSize = TvStyle.Type.BODY; maxLines = 2 }
    private val chip = TextView(act).apply {
        setTextColor(Color.WHITE); textSize = TvStyle.Type.CAPTION; isFocusable = true; isClickable = true
        maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END      // the live reception line (name · % · speed · transport · phone) never pushes the clock away
        setPadding(dp(14), dp(6), dp(14), dp(6)); background = TvStyle.rounded(act, 0x99000000.toInt(), TvStyle.R_XL)
    }
    private val signalRow = TvSignalViews.Row(act)
    private var signal: castbridge.core.ux.TvSignalView? = null
    // Room inside the scroll area for the focus zoom (+10 %) of the first/last cards, so nothing is cut off.
    private val rowsBox = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(18), dp(8), dp(60)); clipChildren = false; clipToPadding = false }
    // The scroll area clips what scrolls out of it: cards must never slide over the title and description above it
    // (they did on a 1280x720 TV, with texts piled on top of each other).
    private val scroll = ScrollView(act).apply { isFillViewport = true; isVerticalScrollBarEnabled = false; clipChildren = true; clipToPadding = true; addView(rowsBox) }
    private val rows = LinkedHashMap<String, Pair<TextView, RowAdapter>>()
    private var toolsRow: View? = null
    private var signature = 0
    private var revealUntil = 0L
    private var lastFocused: LibraryItem? = null
    // D-pad focus memory (castbridge.core.ux.TvHomeFocus): what was opened last from the home, so BACK lands on the tile it came from
    private var opened = castbridge.core.ux.TvHomeFocus.Opened.NOTHING
    private var openedTool = 0
    val visible get() = container.visibility == View.VISIBLE
    /** The « ready · code » chip of the header: the status bar is reached with UP from it. */
    val headerChip: View get() = chip

    init {
        val root = FrameLayout(act).apply { setBackgroundColor(TvStyle.BG) }
        root.addView(bg, FrameLayout.LayoutParams(-1, -1))
        // Legibility: dark from the left and from the bottom, over the picture.
        root.addView(View(act).apply { background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(0xF00A0F1E.toInt(), 0x800A0F1E.toInt(), 0x300A0F1E)) }, FrameLayout.LayoutParams(-1, -1))
        root.addView(View(act).apply { background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x000A0F1E, 0xC00A0F1E.toInt(), TvStyle.BG)) }, FrameLayout.LayoutParams(-1, -1))
        // clipChildren = true: Android clips each child to ITS OWN bounds only when the PARENT asks for it. Without it the
        // scroll area drew its scrolled-out rows over the title (texts piled up while scrolling on 720p TVs).
        val content = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(56), dp(28), dp(40), 0); clipChildren = true }
        val top = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(TvStyle.logo(act, R.drawable.logo_castbridge_tv_horizontal, 52).apply { contentDescription = "CastBridge TV" })
        top.addView(View(act), LinearLayout.LayoutParams(0, 1, 1f))
        top.addView(chip, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(20) })
        top.addView(TextClock(act).apply { format24Hour = "HH:mm"; format12Hour = "HH:mm"; textSize = 30f; setTextColor(Color.WHITE) })
        content.addView(top)
        content.addView(signalRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8); rightMargin = dp(220) })   // keeps clear of the icon status bar (under the clock)
        content.addView(heroTitle, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(26) })
        // Fixed height: a one- or two-line description never pushes the rows around.
        heroSub.minLines = 2
        content.addView(heroSub, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4); bottomMargin = dp(6) })
        content.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(content, FrameLayout.LayoutParams(-1, -1))
        container.addView(root, FrameLayout.LayoutParams(-1, -1))
        // OK on the chip: the cause and the action are the first lines of « Connexion & réglages »; the code is revealed for 10 s
        chip.setOnClickListener { revealUntil = System.currentTimeMillis() + 10_000; refreshStatus(force = true); if (signal != null) api.openSettings() }
        chip.setOnFocusChangeListener { _, has -> signal?.takeIf { has }?.let { heroTitle.text = it.text; heroSub.text = it.action ?: castbridge.core.ux.TvSignal.LEGEND } }
        TvStyle.focusZoom(chip)
    }

    private val tick = object : Runnable { override fun run() { if (visible && !paused) { reload(); refreshTools(); main.postDelayed(this, 4000) } } }
    private var paused = false

    fun show() {
        container.fadeTo(true)
        reload(focusFirst = true)
        if (!paused) { zoom.start(); main.removeCallbacks(tick); main.postDelayed(tick, 4000) }
    }

    fun hide() { container.fadeTo(false); zoom.stop(); main.removeCallbacks(tick); main.removeCallbacks(chipLater) }

    /**
     * Another screen covers the home (Quiz, Apprendre, Langues… are activities on top: the home stays « visible »): no zoom, no 4 s reload.
     * Before, both kept running on the shared main thread under the Quiz (docs/agent-reports/tv-perf.md, R-11). [resume] restarts them.
     */
    fun pause() { paused = true; zoom.stop(); main.removeCallbacks(tick); main.removeCallbacks(chipLater) }
    fun resume() {
        if (!paused) return
        paused = false
        if (!visible) return
        zoom.start(); main.removeCallbacks(tick); reload(); refreshTools(); main.postDelayed(tick, 4000)
    }

    fun release() { hide(); io.shutdownNow() }

    fun onThumbReady(name: String) = main.post { rows.values.forEach { (_, a) -> a.refresh(name) } }

    // Reception events arrive about once a second per transfer (several at once with lanes): the chip is recomputed at most once a second
    // and repainted only when its text changed (setText lays the header out again). The chip's content is unchanged (castbridge.core.ux.Throttle / StateGate).
    private val chipPace = castbridge.core.ux.Throttle(1000)
    private val chipGate = castbridge.core.ux.StateGate<String>()
    private val chipLater = Runnable { refreshStatus() }

    fun refreshStatus(force: Boolean = false) {
        if (paused && !force) return                                    // covered by another screen: refreshed on resume
        val wait = chipPace.admit(android.os.SystemClock.uptimeMillis())
        if (wait > 0 && !force) { main.removeCallbacks(chipLater); main.postDelayed(chipLater, wait); return }
        val (ready, code, receiving) = api.status()
        val shown = if (System.currentTimeMillis() < revealUntil || code.length < 4) code else code.take(2) + "••••"
        val sig = runCatching { api.signal() }.getOrNull().also { signal = it }
        if (sig == null) { val t = (receiving ?: "● $ready") + "   ·   code $shown"; if (chipGate.changed(t)) chip.text = t; return }
        // never hide a red state behind a transfer line; otherwise a transfer in progress tells more than « Prêt »
        val head = if (sig.level == castbridge.core.ux.SignalLevel.RED || receiving == null) sig.text else receiving
        val text = "$head   ·   code $shown"
        // redraw only when the text or the signal facts really changed (R-11: no 60 Hz repaint of an idle home)
        if (chipGate.changed("$text|$sig")) {
            chip.text = text
            TvSignalViews.style(chip, sig.level, 16)
            signalRow.render(sig)
        }
    }

    private fun reload(focusFirst: Boolean = false) {
        refreshStatus()
        // the list and its signature are computed here, off the main thread (a big library made items.hashCode() a main-thread cost every 4 s)
        runCatching { io.execute { val items = runCatching { api.items() }.getOrDefault(emptyList()); val sig = items.hashCode(); main.post { apply(items, sig, focusFirst) } } }
    }

    private fun apply(items: List<LibraryItem>, sig: Int, focusFirst: Boolean) {
        if (sig == signature && rows.isNotEmpty() && !focusFirst) return
        signature = sig
        val media = items.filter { it.type != MediaType.OTHER }
        val sections = LibrarySections.build(items).associateBy { it.id }
        val wanted = listOf(
            "Reprendre" to sections[LibrarySections.RESUME]?.items.orEmpty(),
            "Récemment ajoutés" to LibraryLogic.sortNewestFirst(media, { it.mtime }, { it.name }).take(15),
            "Sur la clé USB" to LibraryLogic.sortNewestFirst(media.filter { it.volumeKind == VolumeKind.REMOVABLE }, { it.mtime }, { it.name }),
            "Toutes les vidéos" to sections[LibrarySections.ALL]?.items.orEmpty(),
            "Autres fichiers" to sections[LibrarySections.OTHER]?.items.orEmpty(),
        ).filter { it.second.isNotEmpty() }
        // Keep the row views (and the focus) when only their content changed.
        // (also the very first time: an empty TV still needs its tools row, or the home has nothing to focus)
        var rebuilt: castbridge.core.ux.TvHomeFocus.Target = castbridge.core.ux.TvHomeFocus.Target.None
        if (toolsRow == null || wanted.map { it.first } != rows.keys.toList()) {
            val hadFocus = rowsBox.hasFocus()
            val oldTools = toolsRow?.tag as? ViewGroup
            val focusedTool = oldTools?.let { b -> (0 until b.childCount).firstOrNull { b.getChildAt(it).hasFocus() } }
            rebuilt = castbridge.core.ux.TvHomeFocus.afterRebuild(hadFocus, focusedTool, oldTools?.childCount ?: 0, wanted.isNotEmpty())
            rowsBox.removeAllViews(); rows.clear()
            // Tools first: every feature (Internet test, USB, Bluetooth, quiz…) is visible without scrolling.
            toolsRow = tools().also { rowsBox.addView(it) }
            for ((title, _) in wanted) {
                val h = TextView(act).apply { setTextColor(Color.WHITE); textSize = 21f; typeface = TvFonts.bold; setPadding(dp(10), dp(14), 0, 0) }
                val a = RowAdapter(title)
                val rv = RecyclerView(act).apply {
                    layoutManager = LinearLayoutManager(act, LinearLayoutManager.HORIZONTAL, false)
                    adapter = a; itemAnimator = null; clipChildren = false; clipToPadding = false
                    setPadding(dp(4), dp(8), dp(40), dp(8)); descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
                }
                rowsBox.addView(h); rowsBox.addView(rv, LinearLayout.LayoutParams(-1, -2))
                rows[title] = h to a
            }
        }
        for ((title, list) in wanted) rows[title]?.let { (h, a) -> h.text = "$title  ·  ${list.size}"; a.set(list) }
        if (items.isEmpty()) {
            heroTitle.text = "Bienvenue sur CastBridge"
            heroSub.text = "Pour relier votre téléphone sans code : « Ajouter un téléphone », ci-dessous. Ensuite, envoyez une vidéo depuis l'app CastBridge : elle apparaîtra ici, prête à regarder, même sans réseau."
            bg.setImageBitmap(null)
        } else if (lastFocused == null) {
            val first = wanted.firstOrNull()?.second?.firstOrNull()
            if (first != null) describe(first)
        }
        val target = if (focusFirst) castbridge.core.ux.TvHomeFocus.onShow(opened, openedTool, (toolsRow?.tag as? ViewGroup)?.childCount ?: 0, wanted.isNotEmpty())
            .also { opened = castbridge.core.ux.TvHomeFocus.Opened.NOTHING } else rebuilt
        if (target != castbridge.core.ux.TvHomeFocus.Target.None) main.postDelayed({ focus(target) }, 80)
    }

    /** Puts the D-pad focus where [castbridge.core.ux.TvHomeFocus] says (first card of the first row, or a tile; falls back to the other). */
    private fun focus(target: castbridge.core.ux.TvHomeFocus.Target) {
        val firstRow = (0 until rowsBox.childCount).map { rowsBox.getChildAt(it) }.firstOrNull { it is RecyclerView } as? RecyclerView
        val tools = toolsRow?.tag as? ViewGroup
        val v = when (target) {
            is castbridge.core.ux.TvHomeFocus.Target.Tool -> tools?.getChildAt(minOf(target.index, (tools.childCount - 1).coerceAtLeast(0))) ?: firstRow?.getChildAt(0)
            castbridge.core.ux.TvHomeFocus.Target.FirstCard -> firstRow?.getChildAt(0) ?: tools?.getChildAt(0)
            else -> null
        }
        v?.requestFocus()
    }

    private var toolsSig = ""

    private fun tools(): View {
        val w = dp(170)
        val box = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(4), dp(8), 0, 0); clipChildren = false }
        val title = TextView(act).apply { text = "Outils et fonctions"; setTextColor(TvStyle.TEXT2); textSize = 20f; typeface = TvFonts.bold; setPadding(dp(14), dp(4), 0, 0) }
        fillTools(box)
        return LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL; clipChildren = false
            addView(title)
            addView(HorizontalScrollView(act).apply { isHorizontalScrollBarEnabled = false; clipChildren = false; addView(box) })
            tag = box
        }
    }

    private fun fillTools(box: LinearLayout) {
        val w = dp(170)
        val list = api.tools().ifEmpty {
            listOf(HomeTool(R.drawable.ic_cb_bibliotheque, "Bibliothèque", "Toutes vos vidéos et vos fichiers, en grille.", null, false) { api.openLibrary() },
                HomeTool(R.drawable.ic_cb_reglages, "Connexion & réglages", "Code, adresse, Bluetooth, stockage…", null, false) { api.openSettings() },
                HomeTool(R.drawable.ic_cb_aide, "Aide", "Comment envoyer une vidéo depuis le téléphone.", null, false) { api.openHelp() })
        }
        toolsSig = list.joinToString("|") { "${it.label}:${it.status}:${it.on}" }
        val focusedIndex = (0 until box.childCount).firstOrNull { box.getChildAt(it).hasFocus() }
        box.removeAllViews()
        list.forEachIndexed { index, t ->
            box.addView(IconTile(act, t, w).also { v ->
                v.setOnClickListener { opened = castbridge.core.ux.TvHomeFocus.Opened.TOOL; openedTool = index; t.action() }
                v.setOnFocusChangeListener { _, has -> if (has) { heroTitle.text = t.label; heroSub.text = t.description + (t.status?.let { "  —  $it" } ?: "") } }
            })
        }
        focusedIndex?.let { i -> box.getChildAt(minOf(i, box.childCount - 1))?.requestFocus() }
    }

    /** Statuses change (Bluetooth ready, Internet via the phone, SSH on…): refresh the tiles in place. */
    private fun refreshTools() {
        val box = (toolsRow?.tag as? LinearLayout) ?: return
        val sig = api.tools().joinToString("|") { "${it.label}:${it.status}:${it.on}" }
        if (sig != toolsSig) fillTools(box)
    }

    /** The hero text and the background follow the focused card. */
    private fun describe(i: LibraryItem) {
        lastFocused = i
        heroTitle.text = i.title
        val m = i.meta
        heroSub.text = listOfNotNull(
            if (m.resumeMs > 0 && !m.watched) "Reprendre à ${LibraryLogic.clock(m.resumeMs)}" else if (m.watched) "Déjà vu" else null,
            m.durationMs.takeIf { it > 0 }?.let { LibraryLogic.clock(it) },
            when (i.volumeKind) { VolumeKind.INTERNAL -> "sur la TV"; VolumeKind.SAF -> "dossier choisi"; else -> "sur la clé USB" },
        ).joinToString("   ·   ")
        val b = thumbs.cached(i)
        if (b != null) crossfade(b) else thumbs.load(i) { if (lastFocused == i) crossfade(it) }
    }

    private fun crossfade(b: Bitmap) {
        val soft = blurred(b)
        bg.animate().cancel()
        bg.animate().alpha(0.15f).setDuration(150).withEndAction { bg.setImageBitmap(soft); bg.animate().alpha(0.5f).setDuration(350).start() }.start()
    }

    /** Blur computed once: shrink twice with filtering; the full-screen view stretches the tiny result smoothly. */
    private fun blurred(b: Bitmap): Bitmap = runCatching {
        val a = Bitmap.createScaledBitmap(b, 96, 54, true)
        Bitmap.createScaledBitmap(a, 48, 27, true).also { if (it !== a) a.recycle() }
    }.getOrDefault(b)

    private inner class RowAdapter(val title: String) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private var list: List<LibraryItem> = emptyList()
        init { setHasStableIds(true) }
        fun set(l: List<LibraryItem>) { if (l != list) { list = l; notifyDataSetChanged() } }
        fun refresh(name: String) { list.forEachIndexed { i, it -> if (it.name == name) notifyItemChanged(i) } }
        override fun getItemCount() = list.size
        override fun getItemId(position: Int) = "${list[position].volumeId}:${list[position].name}".hashCode().toLong()
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val card = MediaCard(parent.context, dp(220))
            card.layoutParams = RecyclerView.LayoutParams(dp(220), ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(dp(10), dp(10), dp(10), dp(10)) }
            val h = object : RecyclerView.ViewHolder(card) {}
            TvStyle.focusZoom(card) { has -> if (has) card.item?.let { describe(it) } }
            card.setOnClickListener { val p = h.bindingAdapterPosition; if (p >= 0) { opened = castbridge.core.ux.TvHomeFocus.Opened.CARD; api.open(list[p], list, p) } }
            card.setOnLongClickListener { val p = h.bindingAdapterPosition; if (p >= 0) api.actions(list[p], list, p); true }
            card.setOnKeyListener { _, code, ev ->
                if (ev.action == KeyEvent.ACTION_DOWN && (code == KeyEvent.KEYCODE_MENU || code == KeyEvent.KEYCODE_INFO)) {
                    val p = h.bindingAdapterPosition; if (p >= 0) api.actions(list[p], list, p); true
                } else false
            }
            return h
        }
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) { (holder.itemView as MediaCard).bind(list[position], thumbs) }
    }
}

/**
 * "Connexion & réglages": everything technical, off the home screen. Left: plain-language connection facts (code, address,
 * Bluetooth, storage, remote access...); right: the actions (the former MENU list), as big D-pad buttons. BACK closes it.
 */
class SettingsPanel(private val act: Activity, private val container: FrameLayout) {
    private val dp = { v: Int -> TvStyle.dp(act, v) }
    val visible get() = container.visibility == View.VISIBLE

    fun show(info: List<Pair<String, String>>, actions: List<Pair<String, () -> Unit>>) {
        container.removeAllViews()
        val root = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL; setBackgroundColor(TvStyle.BG); setPadding(dp(56), dp(40), dp(56), dp(30))
        }
        val left = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        left.addView(TvStyle.logo(act, R.drawable.logo_castbridge_tv_horizontal, 48).apply { contentDescription = "CastBridge TV"
            (layoutParams as LinearLayout.LayoutParams).bottomMargin = dp(4) })
        left.addView(TextView(act).apply { text = "À propos · version ${BuildConfig.VERSION_NAME} · code ${BuildConfig.VERSION_CODE}"; textSize = TvStyle.Type.CAPTION; setTextColor(TvStyle.MUTED); setPadding(0, 0, 0, dp(8)) })
        left.addView(TextView(act).apply { text = "Connexion & réglages"; textSize = 30f; typeface = TvFonts.bold; setTextColor(Color.WHITE) })
        for ((k, v) in info) {
            left.addView(TextView(act).apply { text = k; textSize = TvStyle.Type.CAPTION; setTextColor(TvStyle.MUTED); setPadding(0, dp(14), 0, 0) })
            left.addView(TextView(act).apply { text = v; textSize = 19f; setTextColor(Color.WHITE) })
        }
        root.addView(ScrollView(act).apply { addView(left) }, LinearLayout.LayoutParams(0, -1, 1f))
        val right = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(30), dp(50), 0, 0) }
        var first: View? = null
        for ((label, f) in actions) {
            val b = TextView(act).apply {
                text = label; textSize = TvStyle.Type.BODY; setTextColor(Color.WHITE); isFocusable = true; isClickable = true
                setPadding(dp(18), dp(12), dp(18), dp(12))
                background = android.graphics.drawable.StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_focused), TvStyle.rounded(act, TvStyle.CARD_FOCUS, TvStyle.R_MD, TvStyle.RING, 3))
                    addState(intArrayOf(), TvStyle.rounded(act, 0x00000000, TvStyle.R_MD))
                }
                setOnClickListener { f() }
            }
            right.addView(b, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
            if (first == null) first = b
        }
        root.addView(ScrollView(act).apply { addView(right) }, LinearLayout.LayoutParams(0, -1, 1.2f))
        container.addView(root, FrameLayout.LayoutParams(-1, -1))
        container.fadeTo(true)
        first?.requestFocus()
    }

    fun hide() { container.fadeTo(false) }
}
