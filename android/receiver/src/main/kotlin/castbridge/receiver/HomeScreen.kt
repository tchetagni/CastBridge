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
import android.widget.GridLayout
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
        /** « Reprendre » lue dans la mémoire de la base seule (au plus 10, regardés en partie, derniers lus d'abord), sans lire les volumes. */
        fun quickResume(): List<LibraryItem> = emptyList()
        /** Empreinte de ce qui change [items] ; la même valeur = rien à recalculer. null = inconnue. */
        fun stamp(): Long? = null
        /** Le fichier d'une carte lue avant la liste est-il toujours là ? (un seul accès au volume, hors du fil principal). */
        fun stillThere(i: LibraryItem): Boolean = true
        /** Il n'est plus là : le message court. */
        fun gone(i: LibraryItem) {}
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
    // « Reprendre » instantanée : son propre fil, pour ne jamais attendre la lecture de la bibliothèque (qui occupe [io])
    private val quickIo = Executors.newSingleThreadExecutor { r -> Thread(r, "cb-home-quick").apply { isDaemon = true } }
    /** Vrai tant que la liste complète n'est pas arrivée : l'accueil montre les outils et « Reprendre » (lue dans la base seule). */
    private var loadingShown = false
    private var fullApplied = false
    private var quickList: List<LibraryItem> = emptyList()
    // dernière liste complète et l'empreinte qui l'a produite (lues et écrites seulement sur le fil [io])
    private var lastStamp: Long? = null
    private var lastItems: List<LibraryItem>? = null
    private val dp = { v: Int -> TvStyle.dp(act, v) }
    private val bg = ImageView(act).apply { scaleType = ImageView.ScaleType.CENTER_CROP; alpha = 0.5f }
    // A quiet static background keeps the focus and media thumbnails legible, with no idle animation.
    private val zoom = SlowZoom(bg)
    private val reception = TextView(act).apply {
        textSize = TvStyle.Type.BODY; setTextColor(TvStyle.INFO); maxLines = 3
        setPadding(TvStyle.dp(act, 18), TvStyle.dp(act, 12), TvStyle.dp(act, 18), TvStyle.dp(act, 12))
        background = TvStyle.rounded(act, TvStyle.CARD, TvStyle.R_LG)
        visibility = View.GONE
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }
    private val heroTitle = TextView(act).apply { setTextColor(Color.WHITE); textSize = 34f; typeface = TvFonts.bold; maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
    private val heroSub = TextView(act).apply { setTextColor(TvStyle.TEXT2); textSize = TvStyle.Type.BODY; maxLines = 2 }
    private val keyHint = TextView(act).apply {
        text = "← → Choisir   ·   OK Ouvrir   ·   ↓ Vos vidéos"
        textSize = TvStyle.Type.CAPTION; setTextColor(TvStyle.TEXT2)
        setPadding(dp(8), dp(6), dp(8), dp(12))
    }
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
    // Accueil en groupes (castbridge.core.tv.home) : les entrées affichées, l'état ouvert/fermé de la grille, et la grille elle-même
    private var entries: List<castbridge.core.tv.home.HomeEntry> = emptyList()
    private var toolsById: Map<String, HomeTool> = emptyMap()
    private var nav: castbridge.core.tv.home.HomeNavState = castbridge.core.tv.home.HomeNavState.Closed()
    private val gridBox = FrameLayout(act).apply { visibility = View.GONE }
    private val grid = GridPanel(act, gridBox)
    private lateinit var contentBox: LinearLayout
    val visible get() = container.visibility == View.VISIBLE
    /** The « ready · code » chip of the header: the status pill, just to its right, is reached with RIGHT (or UP) from it. */
    val headerChip: View get() = chip

    init {
        val root = FrameLayout(act).apply { setBackgroundColor(TvStyle.BG) }
        bg.visibility = View.GONE
        // Legibility: dark from the left and from the bottom, over the picture.
        root.addView(View(act).apply { background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(0xF00A0F1E.toInt(), 0x800A0F1E.toInt(), 0x300A0F1E)) }, FrameLayout.LayoutParams(-1, -1))
        root.addView(View(act).apply { background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x000A0F1E, 0xC00A0F1E.toInt(), TvStyle.BG)) }, FrameLayout.LayoutParams(-1, -1))
        // clipChildren = true: Android clips each child to ITS OWN bounds only when the PARENT asks for it. Without it the
        // scroll area drew its scrolled-out rows over the title (texts piled up while scrolling on 720p TVs).
        val content = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(56), dp(28), dp(40), 0); clipChildren = true }
        val top = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(TvStyle.logo(act, R.drawable.logo_castbridge_tv_horizontal_compact, 56).apply { contentDescription = "CastBridge TV" })
        top.addView(View(act), LinearLayout.LayoutParams(0, 1, 1f))
        top.addView(chip, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(20 + 100) })   // 100 dp kept for the status pill (collapsed status bar, StatusBarView) beside the clock
        top.addView(TextClock(act).apply { format24Hour = "HH:mm"; format12Hour = "HH:mm"; textSize = 30f; setTextColor(Color.WHITE) })
        content.addView(top)
        content.addView(signalRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })   // the status bar is a pill in the header now: the full width is free
        content.addView(heroTitle, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        // Fixed height: a one- or two-line description never pushes the rows around.
        heroSub.minLines = 2
        content.addView(heroSub, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4); bottomMargin = dp(6) })
        content.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        content.addView(keyHint, LinearLayout.LayoutParams(-1, -2))
        root.addView(content, FrameLayout.LayoutParams(-1, -1))
        contentBox = content
        root.addView(gridBox, FrameLayout.LayoutParams(-1, -1))                       // la grille d'un groupe recouvre l'accueil
        container.addView(root, FrameLayout.LayoutParams(-1, -1))
        // OK on the chip: the cause and the action are the first lines of « Connexion & réglages »; the code is revealed for 10 s
        chip.setOnClickListener { revealUntil = System.currentTimeMillis() + 10_000; refreshStatus(force = true); if (signal != null) api.openSettings() }
        TvStyle.focusZoom(chip) { has -> if (has) {
            keyHint.text = "OK Connexion et code   ·   → État de la TV   ·   ↓ Accueil"
            signal?.let { heroTitle.text = it.text; heroSub.text = it.action ?: castbridge.core.ux.TvSignal.LEGEND }
        } }
    }

    private val tick = object : Runnable { override fun run() { if (visible && !paused) { reload(); refreshTools(); main.postDelayed(this, 4000) } } }
    private var paused = false

    fun show() {
        container.fadeTo(true)
        // jamais d'accueil vide qui attend la bibliothèque : outils et texte d'attente tout de suite, « Reprendre » dès qu'elle est lue dans la base
        val early = toolsRow == null
        if (early) { apply(emptyList(), 0, true, loading = true); loadQuick() }
        reload(focusFirst = !early)
        if (!paused) { main.removeCallbacks(tick); main.postDelayed(tick, 4000) }
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
        main.removeCallbacks(tick); reload(); refreshTools(); main.postDelayed(tick, 4000)
    }

    fun release() { hide(); io.shutdownNow(); quickIo.shutdownNow() }

    private fun loadQuick() {
        runCatching { quickIo.execute {
            val q = runCatching { api.quickResume() }.getOrDefault(emptyList())
            main.post { if (!fullApplied && q.isNotEmpty()) { quickList = q; apply(q, 1, false, loading = true) } }
        } }
    }

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
        reception.visibility = if (receiving.isNullOrBlank()) View.GONE else View.VISIBLE
        if (reception.text.toString() != receiving.orEmpty()) reception.text = receiving.orEmpty()
        val shown = if (System.currentTimeMillis() < revealUntil || code.length < 4) code else code.take(2) + "••••"
        val sig = runCatching { api.signal() }.getOrNull().also { signal = it }
        if (sig == null) { val t = (receiving ?: "● $ready") + "   ·   code $shown"; if (chipGate.changed(t)) chip.text = t; return }
        // never hide a red state behind a transfer line; otherwise a transfer in progress tells more than « Prêt »
        val head = sig.text
        val text = castbridge.core.ux.NetworkNames.chip(head, sig.network, shown)       // « Prêt à recevoir   ·   Wi-Fi Maison   ·   code 12•••• »
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
        runCatching { io.execute {
            val stamp = runCatching { api.stamp() }.getOrNull()
            val same = stamp != null && stamp == lastStamp && lastItems != null
            if (same && !focusFirst && fullApplied) return@execute                  // rien n'a changé depuis la dernière liste : rien à recalculer (ni lecture, ni calcul de la liste)
            val items = if (same) lastItems!! else runCatching { api.items() }.getOrNull()
            if (items == null) { main.post { if (!fullApplied) apply(emptyList(), 0, focusFirst, loading = true) }; return@execute }
            lastStamp = stamp; lastItems = items
            val sig = items.hashCode()
            main.post { fullApplied = true; apply(items, sig, focusFirst) }
        } }
    }

    private fun apply(items: List<LibraryItem>, sig: Int, focusFirst: Boolean, loading: Boolean = false) {
        if (!loading && !loadingShown && sig == signature && rows.isNotEmpty() && !focusFirst) return
        if (loading && fullApplied) return
        loadingShown = loading
        signature = if (loading) 0 else sig
        val media = items.filter { it.type != MediaType.OTHER }
        val sections = if (loading) emptyMap() else LibrarySections.build(items).associateBy { it.id }
        val wanted = if (loading) listOf("Reprendre" to items).filter { it.second.isNotEmpty() } else listOf(
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
            // La rangée des outils reste en place (et son focus) : seules les rangées de cartes sont refaites, quand la liste complète arrive.
            val oldTools = toolsRow?.tag as? ViewGroup
            val focusInTools = oldTools?.let { b -> (0 until b.childCount).any { b.getChildAt(it).hasFocus() } } == true
            if (rowsBox.hasFocus() && !focusInTools) rebuilt = castbridge.core.ux.TvHomeFocus.afterRebuild(true, null, oldTools?.childCount ?: 0, wanted.isNotEmpty())
            if (toolsRow == null) {
                // Tools first: every feature (Internet test, USB, Bluetooth, quiz…) is visible without scrolling.
                toolsRow = tools().also { rowsBox.addView(it) }
            } else while (rowsBox.childCount > 1) rowsBox.removeViewAt(1)
            rows.clear()
            for ((title, _) in wanted) {
                val h = TextView(act).apply { setTextColor(Color.WHITE); textSize = 21f; typeface = TvFonts.bold; setPadding(dp(10), dp(14), 0, 0) }
                val a = RowAdapter(title)
                val rv = RecyclerView(act).apply {
                    layoutManager = LinearLayoutManager(act, LinearLayoutManager.HORIZONTAL, false)
                    adapter = a; itemAnimator = null; clipChildren = false; clipToPadding = false
                    setPadding(dp(4), dp(8), dp(40), dp(8)); descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
                }
                a.rv = rv
                rowsBox.addView(h); rowsBox.addView(rv, LinearLayout.LayoutParams(-1, -2))
                rows[title] = h to a
            }
        }
        var deferred = false
        for ((title, list) in wanted) rows[title]?.let { (h, a) -> h.text = "$title  ·  ${list.size}"; if (!a.set(list)) deferred = true }
        if (deferred) signature = 0                                                     // une rangée tient le focus : sa nouvelle liste est appliquée au prochain passage
        if (loading && wanted.isEmpty()) {
            heroTitle.text = "Votre TV, simplement."
            heroSub.text = "Choisissez une activité. Vos vidéos se chargent en arrière-plan."
        } else if (items.isEmpty() && !loading) {
            heroTitle.text = "Bienvenue sur CastBridge"
            heroSub.text = "Choisissez « Recevoir » pour connecter votre téléphone et envoyer votre première vidéo."
            bg.setImageBitmap(null)
        } else if (lastFocused == null) {
            val first = wanted.firstOrNull()?.second?.firstOrNull()
            if (first != null) describe(first)
        }
        val target = if (focusFirst) castbridge.core.ux.TvHomeFocus.onShow(opened, openedTool, (toolsRow?.tag as? ViewGroup)?.childCount ?: 0, wanted.isNotEmpty())
            .also { opened = castbridge.core.ux.TvHomeFocus.Opened.NOTHING } else rebuilt
        if (target != castbridge.core.ux.TvHomeFocus.Target.None) main.postDelayed({ focus(target) }, 80)
    }

    /**
     * Ouvre une carte. Tant que la liste complète n'est pas là, « Reprendre » vient de la base seule : le fichier n'est cherché qu'à l'ouverture (un seul accès,
     * hors du fil principal) ; absent, un message court et la carte disparaît.
     */
    private fun openCard(i: LibraryItem, row: List<LibraryItem>, index: Int) {
        if (fullApplied || i !in quickList) { api.open(i, row, index); return }
        runCatching { quickIo.execute {
            val there = runCatching { api.stillThere(i) }.getOrDefault(true)
            main.post {
                if (there) api.open(i, row, index)
                else { api.gone(i); quickList = quickList - i; if (!fullApplied) apply(quickList, 1, false, loading = true) }
            }
        } }
    }

    /** Puts the D-pad focus where [castbridge.core.ux.TvHomeFocus] says (first card of the first row, or a tile; falls back to the other). */
    private fun focus(target: castbridge.core.ux.TvHomeFocus.Target) {
        val firstRow = (0 until rowsBox.childCount).map { rowsBox.getChildAt(it) }.firstOrNull { it is RecyclerView } as? RecyclerView
        val tools = toolsRow?.tag as? ViewGroup
        if (nav is castbridge.core.tv.home.HomeNavState.Open) return                      // a grid is open: the focus stays on its tile
        val v = when (target) {
            is castbridge.core.ux.TvHomeFocus.Target.Tool -> tools?.getChildAt(minOf(target.index, (tools.childCount - 1).coerceAtLeast(0))) ?: firstRow?.getChildAt(0)
            castbridge.core.ux.TvHomeFocus.Target.FirstCard -> firstRow?.getChildAt(0) ?: tools?.getChildAt(0)
            else -> null
        }
        v?.requestFocus()
    }

    private var toolsSig = ""

    private fun tools(): View {
        val box = GridLayout(act).apply { columnCount = 4; clipChildren = false; setPadding(dp(8), dp(8), dp(8), dp(8)) }
        fillTools(box)
        return LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL; clipChildren = false
            addView(box, LinearLayout.LayoutParams(-1, -2))
            addView(reception, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12); bottomMargin = dp(8) })
            tag = box
        }
    }

    private fun fillTools(box: GridLayout) {
        val available = act.resources.displayMetrics.widthPixels - dp(136)
        val w = ((available - dp(48)) / 4).coerceAtLeast(dp(140))
        val list = api.tools().ifEmpty {
            listOf(HomeTool(R.drawable.ic_cb_bibliotheque, "Bibliothèque", "Toutes vos vidéos et vos fichiers, en grille.", null, false, id = "library") { api.openLibrary() },
                HomeTool(R.drawable.ic_cb_reglages, "Connexion & réglages", "Code, adresse, Bluetooth, stockage…", null, false, id = "settings") { api.openSettings() },
                HomeTool(R.drawable.ic_cb_aide, "Aide", "Comment envoyer une vidéo depuis le téléphone.", null, false, id = "help") { api.openHelp() })
        }
        toolsSig = list.joinToString("|") { "${it.label}:${it.status}:${it.on}" }
        val focusedId = (0 until box.childCount).firstOrNull { box.getChildAt(it).hasFocus() }?.let { entries.getOrNull(it)?.id }
        // the tools are grouped (castbridge.core.tv.home.HomeGroups): one big button per group, the quick-access tiles stay direct
        toolsById = list.associateBy { it.id.ifEmpty { it.label } }
        entries = castbridge.core.tv.home.HomeDashboard.layout(list.map { castbridge.core.tv.home.HomeTileInfo(it.id.ifEmpty { it.label }, it.label, it.status, it.on, it.warn) })
        box.removeAllViews()
        entries.forEachIndexed { index, e ->
            val isMore = e.id == castbridge.core.tv.home.HomeDashboard.MORE
            val v: View = if (isMore) TextView(act).apply {
                text = "Connexion et réglages  ›"; textSize = TvStyle.Type.BODY; setTextColor(TvStyle.TEXT2)
                gravity = Gravity.CENTER_VERTICAL or Gravity.END; setPadding(dp(16), 0, dp(16), 0)
                isFocusable = true; isClickable = true
                background = android.graphics.drawable.StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_focused), TvStyle.rounded(act, TvStyle.CARD_FOCUS, TvStyle.R_MD, TvStyle.RING, 3))
                    addState(intArrayOf(), TvStyle.rounded(act, TvStyle.BG, TvStyle.R_MD))
                }
                setOnClickListener { opened = castbridge.core.ux.TvHomeFocus.Opened.TOOL; openedTool = index; openGrid(e.id) }
                TvStyle.focusZoom(this) { has -> if (has) { heroSub.text = "Téléphones, stockage, téléchargements, aide et réglages"; keyHint.text = "OK Ouvrir   ·   ↑ Activités   ·   ↓ Vos vidéos" } }
            } else when (e) {
                is castbridge.core.tv.home.HomeEntry.Direct -> {
                    val t = toolsById.getValue(e.id)
                    IconTile(act, t, w, HomeGroupStyle.HOME_LABEL_SP, TvStyle.Type.CAPTION, 40, dp(HomeGroupStyle.HOME_HEIGHT_DP)).also { v ->
                        v.setOnClickListener { opened = castbridge.core.ux.TvHomeFocus.Opened.TOOL; openedTool = index; t.action() }
                        TvStyle.focusZoom(v) { has -> if (has) { heroTitle.text = "Votre TV, simplement."; heroSub.text = t.description + (t.status?.let { "  —  $it" } ?: ""); keyHint.text = "← → Choisir   ·   OK Ouvrir   ·   ↓ Vos vidéos" } }
                    }
                }
                is castbridge.core.tv.home.HomeEntry.Group -> GroupTile(act, e, if (isMore) available else w, dp(if (isMore) 88 else HomeGroupStyle.HOME_HEIGHT_DP)).also { v ->
                    v.setOnClickListener { opened = castbridge.core.ux.TvHomeFocus.Opened.TOOL; openedTool = index; openGrid(e.id) }
                    TvStyle.focusZoom(v) { has -> if (has) { heroTitle.text = "Votre TV, simplement."; heroSub.text = e.summary; keyHint.text = "← → Choisir   ·   OK Voir le groupe   ·   ↓ Vos vidéos" } }
                }
            }
            val ordinary = entries.take(index).count { it.id != castbridge.core.tv.home.HomeDashboard.MORE }
            box.addView(v, GridLayout.LayoutParams().apply {
                width = if (isMore) available else w
                height = dp(if (isMore) 52 else HomeGroupStyle.HOME_HEIGHT_DP)
                columnSpec = GridLayout.spec(if (isMore) 0 else ordinary % 4, if (isMore) 4 else 1)
                rowSpec = GridLayout.spec(if (isMore) (ordinary + 3) / 4 else ordinary / 4)
                setMargins(dp(4), dp(8), dp(4), dp(8))
            })
        }
        focusedId?.let { id -> box.getChildAt(entries.indexOfFirst { it.id == id }.coerceAtLeast(0))?.requestFocus() }
        // statuses changed under an open grid: redraw it on the same tile; a group that vanished closes it
        nav = castbridge.core.tv.home.HomeNav.reconcile(nav, entries, grid.focusedTileId)
        if (nav is castbridge.core.tv.home.HomeNavState.Open) showGrid() else if (grid.visible) hideGrid()
    }

    private fun toolOf(id: String): HomeTool? = toolsById[id]

    private fun openGrid(groupId: String) {
        val s = castbridge.core.tv.home.HomeNav.press(castbridge.core.tv.home.HomeNavState.Closed(groupId), entries, castbridge.core.tv.home.HomeKey.OK, groupId)
        nav = s.state
        if (nav is castbridge.core.tv.home.HomeNavState.Open) showGrid()
    }

    private fun showGrid() {
        val st = nav as? castbridge.core.tv.home.HomeNavState.Open ?: return
        val g = entries.firstOrNull { it.id == st.groupId } as? castbridge.core.tv.home.HomeEntry.Group ?: return
        contentBox.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS               // behind the grid, nothing can take the D-pad focus
        grid.show(g.label, g.tiles.mapNotNull { toolOf(it.id) }, st.focusIndex, { _, key -> press(key) }, { index ->
            nav = (nav as? castbridge.core.tv.home.HomeNavState.Open)?.copy(focusIndex = index) ?: nav
            press(castbridge.core.tv.home.HomeKey.OK)
        })
    }

    private fun hideGrid() { grid.hide(); contentBox.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS }

    /** One key through the core state machine; the screen only applies the action it returns. */
    private fun press(key: castbridge.core.tv.home.HomeKey): Boolean {
        val s = castbridge.core.tv.home.HomeNav.press(nav, entries, key)
        nav = s.state
        when (val a = s.action) {
            is castbridge.core.tv.home.HomeAction.FocusGrid -> grid.focus(a.index)
            is castbridge.core.tv.home.HomeAction.FocusHome -> { hideGrid(); focusEntry(a.id) }
            is castbridge.core.tv.home.HomeAction.Launch -> toolOf(a.tileId)?.let { opened = castbridge.core.ux.TvHomeFocus.Opened.TOOL; it.action() }
            null -> Unit
        }
        return s.consumed
    }

    private fun focusEntry(id: String) {
        val box = toolsRow?.tag as? ViewGroup ?: return
        entries.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let { box.getChildAt(minOf(it, box.childCount - 1))?.requestFocus() }
    }

    /** RETOUR : ferme la grille ouverte et rend le focus à son bouton de groupe. false = aucune grille (l'activité traite RETOUR comme avant). */
    fun closeGrid(): Boolean = nav is castbridge.core.tv.home.HomeNavState.Open && press(castbridge.core.tv.home.HomeKey.BACK)

    /** Statuses change (Bluetooth ready, Internet via the phone, SSH on…): refresh the tiles in place. */
    private fun refreshTools() {
        val box = (toolsRow?.tag as? GridLayout) ?: return
        val sig = api.tools().joinToString("|") { "${it.label}:${it.status}:${it.on}" }
        if (sig != toolsSig) fillTools(box)
    }

    /** The hero text and the background follow the focused card. */
    private fun describe(i: LibraryItem) {
        lastFocused = i
        heroTitle.text = i.title
        keyHint.text = "← → Choisir   ·   OK Lire   ·   Appui long OK / MENU Options"
        val m = i.meta
        heroSub.text = listOfNotNull(
            if (m.resumeMs > 0 && !m.watched) "Reprendre à ${LibraryLogic.clock(m.resumeMs)}" else if (m.watched) "Déjà vu" else null,
            m.durationMs.takeIf { it > 0 }?.let { LibraryLogic.clock(it) },
            when (i.volumeKind) { VolumeKind.INTERNAL -> "sur la TV"; VolumeKind.SAF -> "dossier choisi"; else -> "sur la clé USB" },
        ).joinToString("   ·   ")

    }

    private fun crossfade(b: Bitmap) {
        val soft = blurred(b)
        bg.animate().cancel()
        if (!ResourceProfiles.peek().homeAnimations) { bg.setImageBitmap(soft); bg.alpha = 0.5f; return }   // économe : pas de fondu enchaîné
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
        var rv: RecyclerView? = null
        private fun keys(l: List<LibraryItem>) = l.map { "${it.volumeId}:${it.name}" }
        /**
         * La nouvelle liste. Mêmes fichiers dans le même ordre : les cartes se mettent à jour en place (le focus reste). Autre ordre ou autres fichiers
         * pendant que la rangée tient le focus : rien ne bouge (false = à reprendre au prochain passage). Rangée sans focus : refaite.
         */
        fun set(l: List<LibraryItem>): Boolean {
            if (l == list) return true
            if (keys(l) == keys(list)) { list = l; notifyItemRangeChanged(0, l.size, Unit); return true }
            if (rv?.hasFocus() == true) return false
            list = l; notifyDataSetChanged(); return true
        }
        fun refresh(name: String) { list.forEachIndexed { i, it -> if (it.name == name) notifyItemChanged(i) } }
        override fun getItemCount() = list.size
        override fun getItemId(position: Int) = "${list[position].volumeId}:${list[position].name}".hashCode().toLong()
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val card = MediaCard(parent.context, dp(220))
            card.layoutParams = RecyclerView.LayoutParams(dp(220), ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(dp(10), dp(10), dp(10), dp(10)) }
            val h = object : RecyclerView.ViewHolder(card) {}
            TvStyle.focusZoom(card) { has -> if (has) card.item?.let { describe(it) } }
            card.setOnClickListener { val p = h.bindingAdapterPosition; if (p >= 0) { opened = castbridge.core.ux.TvHomeFocus.Opened.CARD; openCard(list[p], list, p) } }
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
        left.addView(TvStyle.logo(act, R.drawable.logo_castbridge_tv_horizontal_compact, 56).apply { contentDescription = "CastBridge TV"
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
