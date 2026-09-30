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
    private val heroTitle = TextView(act).apply { setTextColor(Color.WHITE); textSize = 34f; typeface = Typeface.DEFAULT_BOLD; maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
    private val heroSub = TextView(act).apply { setTextColor(0xFFDDE3EA.toInt()); textSize = 17f; maxLines = 2 }
    private val chip = TextView(act).apply {
        setTextColor(Color.WHITE); textSize = 15f; isFocusable = true; isClickable = true
        setPadding(dp(14), dp(6), dp(14), dp(6)); background = TvStyle.rounded(act, 0x99000000.toInt(), 20)
    }
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
    val visible get() = container.visibility == View.VISIBLE

    init {
        val root = FrameLayout(act).apply { setBackgroundColor(TvStyle.BG) }
        root.addView(bg, FrameLayout.LayoutParams(-1, -1))
        // Legibility: dark from the left and from the bottom, over the picture.
        root.addView(View(act).apply { background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(0xF00E1116.toInt(), 0x800E1116.toInt(), 0x300E1116)) }, FrameLayout.LayoutParams(-1, -1))
        root.addView(View(act).apply { background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x000E1116, 0xC00E1116.toInt(), 0xFF0E1116.toInt())) }, FrameLayout.LayoutParams(-1, -1))
        // clipChildren = true: Android clips each child to ITS OWN bounds only when the PARENT asks for it. Without it the
        // scroll area drew its scrolled-out rows over the title (texts piled up while scrolling on 720p TVs).
        val content = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(56), dp(28), dp(40), 0); clipChildren = true }
        val top = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(TextView(act).apply { text = "CastBridge"; textSize = 26f; typeface = Typeface.DEFAULT_BOLD; setTextColor(TvStyle.ACCENT) })
        top.addView(View(act), LinearLayout.LayoutParams(0, 1, 1f))
        top.addView(chip, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(20) })
        top.addView(TextClock(act).apply { format24Hour = "HH:mm"; format12Hour = "HH:mm"; textSize = 30f; setTextColor(Color.WHITE) })
        content.addView(top)
        content.addView(heroTitle, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(26) })
        // Fixed height: a one- or two-line description never pushes the rows around.
        heroSub.minLines = 2
        content.addView(heroSub, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4); bottomMargin = dp(6) })
        content.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(content, FrameLayout.LayoutParams(-1, -1))
        container.addView(root, FrameLayout.LayoutParams(-1, -1))
        chip.setOnClickListener { revealUntil = System.currentTimeMillis() + 10_000; refreshStatus() }
        TvStyle.focusZoom(chip, 1.05f)
    }

    private val tick = object : Runnable { override fun run() { if (visible) { reload(); refreshTools(); main.postDelayed(this, 4000) } } }

    fun show() {
        container.fadeTo(true)
        zoom.start()
        reload(focusFirst = true)
        main.removeCallbacks(tick); main.postDelayed(tick, 4000)
    }

    fun hide() { container.fadeTo(false); zoom.stop(); main.removeCallbacks(tick) }

    fun release() { hide(); io.shutdownNow() }

    fun onThumbReady(name: String) = main.post { rows.values.forEach { (_, a) -> a.refresh(name) } }

    private fun refreshStatus() {
        val (ready, code, receiving) = api.status()
        val shown = if (System.currentTimeMillis() < revealUntil || code.length < 4) code else code.take(2) + "••••"
        chip.text = (receiving ?: "● $ready") + "   ·   code $shown"
    }

    private fun reload(focusFirst: Boolean = false) {
        refreshStatus()
        runCatching { io.execute { val items = runCatching { api.items() }.getOrDefault(emptyList()); main.post { apply(items, focusFirst) } } }
    }

    private fun apply(items: List<LibraryItem>, focusFirst: Boolean) {
        val sig = items.hashCode()
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
        if (toolsRow == null || wanted.map { it.first } != rows.keys.toList()) {
            rowsBox.removeAllViews(); rows.clear()
            // Tools first: every feature (Internet test, USB, Bluetooth, quiz…) is visible without scrolling.
            toolsRow = tools().also { rowsBox.addView(it) }
            for ((title, _) in wanted) {
                val h = TextView(act).apply { setTextColor(Color.WHITE); textSize = 21f; typeface = Typeface.DEFAULT_BOLD; setPadding(dp(10), dp(14), 0, 0) }
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
            heroSub.text = "Envoyez une vidéo depuis l'app CastBridge de votre téléphone : elle apparaîtra ici, prête à regarder, même sans réseau."
            bg.setImageBitmap(null)
        } else if (lastFocused == null) {
            val first = wanted.firstOrNull()?.second?.firstOrNull()
            if (first != null) describe(first)
        }
        if (focusFirst) main.postDelayed({
            val firstRow = (0 until rowsBox.childCount).map { rowsBox.getChildAt(it) }.firstOrNull { it is RecyclerView } as? RecyclerView
            val v = firstRow?.getChildAt(0) ?: ((toolsRow?.tag as? ViewGroup)?.getChildAt(0))
            v?.requestFocus()
        }, 80)
    }

    private var toolsSig = ""

    private fun tools(): View {
        val w = dp(170)
        val box = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(4), dp(8), 0, 0); clipChildren = false }
        val title = TextView(act).apply { text = "Outils et fonctions"; setTextColor(0xFFDDE3EA.toInt()); textSize = 20f; typeface = Typeface.DEFAULT_BOLD; setPadding(dp(14), dp(4), 0, 0) }
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
            listOf(HomeTool(R.drawable.ic_t_library, "Bibliothèque", "Toutes vos vidéos et vos fichiers, en grille.", null, false) { api.openLibrary() },
                HomeTool(R.drawable.ic_t_settings, "Connexion & réglages", "Code, adresse, Bluetooth, stockage…", null, false) { api.openSettings() },
                HomeTool(R.drawable.ic_t_help, "Aide", "Comment envoyer une vidéo depuis le téléphone.", null, false) { api.openHelp() })
        }
        toolsSig = list.joinToString("|") { "${it.label}:${it.status}:${it.on}" }
        val focusedIndex = (0 until box.childCount).firstOrNull { box.getChildAt(it).hasFocus() }
        box.removeAllViews()
        list.forEach { t ->
            box.addView(IconTile(act, t, w).also { v ->
                v.setOnClickListener { t.action() }
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
            TvStyle.focusZoom(card, 1.1f) { has -> if (has) card.item?.let { describe(it) } }
            card.setOnClickListener { val p = h.bindingAdapterPosition; if (p >= 0) api.open(list[p], list, p) }
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
            orientation = LinearLayout.HORIZONTAL; setBackgroundColor(0xF20E1116.toInt()); setPadding(dp(56), dp(40), dp(56), dp(30))
        }
        val left = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        left.addView(TextView(act).apply { text = "Connexion & réglages"; textSize = 30f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE) })
        for ((k, v) in info) {
            left.addView(TextView(act).apply { text = k; textSize = 14f; setTextColor(TvStyle.MUTED); setPadding(0, dp(14), 0, 0) })
            left.addView(TextView(act).apply { text = v; textSize = 19f; setTextColor(Color.WHITE) })
        }
        root.addView(ScrollView(act).apply { addView(left) }, LinearLayout.LayoutParams(0, -1, 1f))
        val right = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(30), dp(50), 0, 0) }
        var first: View? = null
        for ((label, f) in actions) {
            val b = TextView(act).apply {
                text = label; textSize = 18f; setTextColor(Color.WHITE); isFocusable = true; isClickable = true
                setPadding(dp(18), dp(12), dp(18), dp(12))
                background = android.graphics.drawable.StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_focused), TvStyle.rounded(act, TvStyle.CARD_FOCUS, 10, TvStyle.ACCENT, 2))
                    addState(intArrayOf(), TvStyle.rounded(act, 0x00000000, 10))
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
