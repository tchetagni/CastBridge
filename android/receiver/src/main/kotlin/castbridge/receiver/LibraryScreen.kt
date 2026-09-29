package castbridge.receiver

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.text.TextUtils
import android.util.LruCache
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import castbridge.core.tv.LibraryItem
import castbridge.core.tv.LibraryLogic
import castbridge.core.tv.LibrarySections
import castbridge.core.tv.MediaType
import castbridge.core.tv.StorageLine
import castbridge.core.tv.VolumeKind
import java.util.concurrent.Executors

/**
 * The TV library: a grid of cards (thumbnail, readable title, duration, resume bar, "vu" badge, drive/internal badge) in
 * sections "Reprendre", "Récemment ajoutés", "Toutes", "Autres fichiers". Made for the remote: D-pad focus with a big
 * visible frame, OK = play (offers "Reprendre à 12:34" / "Depuis le début"), MENU or a long OK = actions.
 *
 * Plain Android views and one RecyclerView (only the visible cards exist); thumbnails are ~320 px RGB_565 bitmaps kept in a
 * 3 MB LRU, loaded one at a time. Actions go through the app's own HTTP API on loopback, so they follow exactly the same
 * rules (PIN, volumes, locks) as the phone and the web page.
 */
class LibraryScreen(
    private val act: Activity,
    private val container: FrameLayout,
    private val api: Api,
) {
    /** What the screen needs from the app. */
    interface Api {
        fun items(): List<LibraryItem>
        fun thumbnail(name: String, volume: String): ByteArray?
        fun volumes(): List<Pair<String, String>>          // (id, label) of the volumes a file may be moved to
        fun header(): String
        /** Runs an HTTP call on the TV's own API (loopback) off the main thread; returns an error message or null. */
        fun call(block: (castbridge.core.tv.TvClient) -> Unit): String?
        fun flash(msg: String)
        /** Plays [names] one after the other from [start] (a section of the library). */
        fun playAll(names: List<String>, start: Int)
    }

    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "cb-library").apply { isDaemon = true } }
    private val thumbs = object : LruCache<String, Bitmap>(3 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val asked = HashSet<String>()                  // thumbnails already requested (retried when the TV says one is ready)
    private val rows = ArrayList<Row>()
    private var signature = 0
    private val root: LinearLayout
    private val header: TextView
    private val list: RecyclerView
    private val adapter = Adapter()
    val visible get() = container.visibility == View.VISIBLE

    private sealed class Row {
        abstract val id: Long
        data class Header(val title: String, val count: Int) : Row() { override val id = ("h:$title").hashCode().toLong() }
        data class Card(val section: String, val item: LibraryItem, val index: Int, val sectionNames: List<String>) : Row() {
            override val id = ("c:$section:${item.volumeId}:${item.name}").hashCode().toLong() shl 1 or 1
        }
        data class Empty(val text: String) : Row() { override val id = 7L }
    }

    init {
        root = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
            setPadding(dp(40), dp(24), dp(40), 0)
        }
        root.addView(TextView(act).apply {
            text = "Bibliothèque"; setTextColor(Color.WHITE); textSize = 28f; typeface = Typeface.DEFAULT_BOLD
        })
        header = TextView(act).apply { setTextColor(MUTED); textSize = 15f; setPadding(0, dp(4), 0, dp(8)) }
        root.addView(header)
        list = RecyclerView(act).apply {
            layoutManager = GridLayoutManager(act, COLS).apply {
                spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int) = if (rows.getOrNull(position) is Row.Card) 1 else COLS
                }
            }
            adapter = this@LibraryScreen.adapter
            clipToPadding = false
            clipChildren = false
            setPadding(0, dp(4), 0, dp(48))
            itemAnimator = null                             // no fades when a thumbnail arrives: focus stays put
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        }
        root.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        container.addView(root, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private val tick = object : Runnable {
        override fun run() { if (visible) { reload(); main.postDelayed(this, 5000) } }
    }

    fun show() {
        container.visibility = View.VISIBLE
        header.text = api.header()
        reload(focusFirst = true)
        main.removeCallbacks(tick); main.postDelayed(tick, 5000)
    }

    fun hide() {
        container.visibility = View.GONE
        main.removeCallbacks(tick)
        thumbs.evictAll()                                   // the player needs the memory more than the cards
    }

    fun release() { hide(); io.shutdownNow() }

    /** The TV made a thumbnail: show it if its card is on screen. */
    fun onThumbReady(name: String) = main.post {
        asked.removeAll { it.contains("\u0000$name\u0000") }
        rows.forEachIndexed { i, r -> if (r is Row.Card && r.item.name == name) adapter.notifyItemChanged(i) }
    }

    private fun reload(focusFirst: Boolean = false) {
        runCatching {
            io.execute {
                val items = runCatching { api.items() }.getOrDefault(emptyList())
                val h = api.header()
                main.post { apply(items, h, focusFirst) }
            }
        }
    }

    private fun apply(items: List<LibraryItem>, h: String, focusFirst: Boolean) {
        header.text = h
        val sig = items.hashCode()
        if (sig == signature && rows.isNotEmpty() && !focusFirst) return
        signature = sig
        rows.clear()
        val sections = LibrarySections.build(items)
        if (sections.isEmpty()) rows += Row.Empty("Aucun fichier sur la TV.\nEnvoyez des vidéos depuis l'app CastBridge du téléphone, la page web, le Bluetooth ou une clé USB.")
        for (s in sections) {
            rows += Row.Header(s.title, s.items.size)
            val names = s.items.map { it.name }
            s.items.forEachIndexed { i, it -> rows += Row.Card(s.id, it, i, names) }
        }
        adapter.notifyDataSetChanged()
        if (focusFirst) main.post {
            val first = rows.indexOfFirst { it is Row.Card }
            if (first >= 0) { list.scrollToPosition(first); main.postDelayed({ list.findViewHolderForAdapterPosition(first)?.itemView?.requestFocus() }, 50) }
        }
    }

    // ---------------------------------------------------------------- actions

    private fun playChoice(r: Row.Card) {
        val i = r.item
        if (i.type == MediaType.OTHER) { actions(r); return }
        if (i.meta.resumeMs > 0) {
            AlertDialog.Builder(act).setTitle(i.title)
                .setItems(arrayOf("Reprendre à ${LibraryLogic.clock(i.meta.resumeMs)}", "Depuis le début")) { _, w ->
                    play(i, if (w == 0) i.meta.resumeMs else 0)
                }.setNegativeButton("Annuler", null).show()
        } else play(i, 0)
    }

    private fun play(i: LibraryItem, pos: Long) = run("Lecture") { it.play(i.name, pos) }

    private fun actions(r: Row.Card) {
        val i = r.item
        val items = ArrayList<Pair<String, () -> Unit>>()
        if (i.type != MediaType.OTHER) {
            if (i.meta.resumeMs > 0) items += "Reprendre à ${LibraryLogic.clock(i.meta.resumeMs)}" to { play(i, i.meta.resumeMs) }
            items += "Lire depuis le début" to { play(i, 0) }
            if (r.sectionNames.size > 1) items += "Lire la section à la suite (${r.sectionNames.size - r.index} fichiers)" to { api.playAll(r.sectionNames, r.index) }
            items += (if (i.meta.watched) "Marquer comme non vu" else "Marquer comme vu") to { run("Marquage") { it.setWatched(i.name, !i.meta.watched) } }
        } else if (i.name.endsWith(".apk", true)) {
            items += "Installer cette application" to { run("Installation") { it.installApks(listOf(i.name)) }; api.flash("Installation demandée : validez à l'écran") }
        }
        api.volumes().filter { it.first != i.volumeId }.forEach { (id, label) ->
            items += "Déplacer vers $label" to { run("Déplacement") { it.moveFile(i.name, id) }; api.flash("Déplacement vers $label lancé") }
        }
        items += "Renommer…" to { rename(i) }
        items += "Supprimer…" to { confirmDelete(i) }
        AlertDialog.Builder(act).setTitle(i.title)
            .setItems(items.map { it.first }.toTypedArray()) { _, w -> items[w].second() }
            .setNegativeButton("Fermer", null).show()
    }

    private fun rename(i: LibraryItem) {
        val e = EditText(act).apply { setText(i.name); inputType = InputType.TYPE_CLASS_TEXT; setSingleLine(); setSelection(0, i.name.substringBeforeLast('.').length) }
        AlertDialog.Builder(act).setTitle("Renommer").setView(e)
            .setPositiveButton("Renommer") { _, _ ->
                val to = e.text.toString().trim()
                if (to.isNotEmpty() && to != i.name) run("Renommage") { it.rename(i.name, to) }
            }.setNegativeButton("Annuler", null).show()
    }

    private fun confirmDelete(i: LibraryItem) {
        AlertDialog.Builder(act).setTitle("Supprimer « ${i.title} » ?")
            .setMessage("${i.name}\n${StorageLine.size(i.size)} sur ${i.volumeLabel}. Cette action est définitive.")
            .setPositiveButton("Supprimer") { _, _ -> run("Suppression") { it.delete(i.name, i.volumeId) } }
            .setNegativeButton("Annuler", null).show()
            .getButton(AlertDialog.BUTTON_NEGATIVE)?.requestFocus()       // the safe choice has the focus
    }

    private fun run(what: String, block: (castbridge.core.tv.TvClient) -> Unit) {
        runCatching {
            io.execute {
                val err = api.call(block)
                main.post { if (err != null) api.flash("$what impossible : $err"); reload() }
            }
        }
    }

    // ---------------------------------------------------------------- views

    private inner class Adapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        init { setHasStableIds(true) }
        override fun getItemCount() = rows.size
        override fun getItemId(position: Int) = rows[position].id
        override fun getItemViewType(position: Int) = when (rows[position]) { is Row.Header -> 0; is Row.Card -> 1; is Row.Empty -> 2 }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder = when (viewType) {
            1 -> CardHolder(CardView(parent.context))
            else -> object : RecyclerView.ViewHolder(TextView(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                setTextColor(if (viewType == 0) ACCENT else MUTED); textSize = if (viewType == 0) 20f else 20f
                if (viewType == 0) typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(4), dp(18), 0, dp(8)); isFocusable = false
            }) {}
        }

        override fun onBindViewHolder(h: RecyclerView.ViewHolder, position: Int) {
            when (val r = rows[position]) {
                is Row.Header -> (h.itemView as TextView).text = "${r.title}  ·  ${r.count}"
                is Row.Empty -> (h.itemView as TextView).text = r.text
                is Row.Card -> (h as CardHolder).bind(r)
            }
        }
    }

    private inner class CardHolder(val card: CardView) : RecyclerView.ViewHolder(card) {
        var row: Row.Card? = null
        init {
            card.setOnClickListener { row?.let(::playChoice) }
            card.setOnLongClickListener { row?.let(::actions); true }
            card.setOnKeyListener { _, code, ev ->
                if (ev.action == KeyEvent.ACTION_DOWN && (code == KeyEvent.KEYCODE_MENU || code == KeyEvent.KEYCODE_INFO)) { row?.let(::actions); true }
                else false
            }
        }

        fun bind(r: Row.Card) {
            row = r
            val i = r.item
            val m = i.meta
            card.title.text = i.title
            card.sub.text = buildString {
                if (m.durationMs > 0) append(LibraryLogic.clock(m.durationMs)).append("  ·  ")
                append(StorageLine.size(i.size))
            }
            card.volume.text = if (i.volumeKind == VolumeKind.INTERNAL) "Interne" else if (i.volumeKind == VolumeKind.SAF) "Dossier" else "Clé"
            card.watched.visibility = if (m.watched) View.VISIBLE else View.GONE
            card.duration.visibility = if (m.durationMs > 0) View.VISIBLE else View.GONE
            card.duration.text = LibraryLogic.clock(m.durationMs)
            val p = if (m.resumeMs > 0 && !m.watched) LibraryLogic.progress(m.resumeMs, m.durationMs) else 0f
            card.progress.visibility = if (p > 0f) View.VISIBLE else View.GONE
            card.progress.progress = (p * 1000).toInt()
            card.placeholder.text = when (i.type) { MediaType.VIDEO -> "▶"; MediaType.AUDIO -> "♪"; MediaType.OTHER -> i.name.substringAfterLast('.', "?").uppercase().take(4) }
            val key = "${i.volumeId}\u0000${i.name}\u0000${i.size}"
            val cached = thumbs.get(key)
            card.image.setImageBitmap(cached)
            card.placeholder.visibility = if (cached == null) View.VISIBLE else View.GONE
            if (cached == null && i.type != MediaType.OTHER && asked.add(key)) loadThumb(key, i)
        }
    }

    private fun loadThumb(key: String, i: LibraryItem) {
        runCatching {
            io.execute {
                val bytes = runCatching { api.thumbnail(i.name, i.volumeId) }.getOrNull() ?: return@execute   // not ready: onThumbReady will retry
                val bmp = runCatching {
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 })
                }.getOrNull() ?: return@execute
                main.post {
                    thumbs.put(key, bmp)
                    rows.forEachIndexed { idx, r -> if (r is Row.Card && r.item.name == i.name && r.item.volumeId == i.volumeId) adapter.notifyItemChanged(idx) }
                }
            }
        }
    }

    /** One card: 16:9 thumbnail with badges and resume bar, then a two-line title and a detail line. */
    private inner class CardView(ctx: Context) : LinearLayout(ctx) {
        val image = ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        val placeholder = TextView(ctx).apply { setTextColor(0x88FFFFFF.toInt()); textSize = 34f; gravity = Gravity.CENTER }
        val volume = badge(0xCC12384A.toInt())
        val watched = badge(0xCC1B5E20.toInt()).apply { text = "VU" }
        val duration = badge(0xCC000000.toInt())
        val progress = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000; progressTintList = android.content.res.ColorStateList.valueOf(ACCENT)
        }
        val title = TextView(ctx).apply {
            setTextColor(Color.WHITE); textSize = 17f; maxLines = 2; ellipsize = TextUtils.TruncateAt.END; setPadding(dp(8), dp(6), dp(8), 0)
        }
        val sub = TextView(ctx).apply { setTextColor(MUTED); textSize = 13f; maxLines = 1; setPadding(dp(8), dp(2), dp(8), dp(8)) }

        init {
            orientation = VERTICAL
            isFocusable = true; isFocusableInTouchMode = true; isClickable = true; isLongClickable = true
            layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(dp(8), dp(8), dp(8), dp(8)) }
            background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused), rounded(0xFF263238.toInt(), ACCENT, dp(4)))
                addState(intArrayOf(), rounded(CARD, 0, 0))
            }
            val pic = Aspect169(ctx)
            pic.addView(placeholder, FrameLayout.LayoutParams(-1, -1))
            pic.addView(image, FrameLayout.LayoutParams(-1, -1))
            pic.addView(volume, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply { setMargins(dp(6), dp(6), 0, 0) })
            pic.addView(watched, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply { setMargins(0, dp(6), dp(6), 0) })
            pic.addView(duration, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END).apply { setMargins(0, 0, dp(6), dp(10)) })
            pic.addView(progress, FrameLayout.LayoutParams(-1, dp(6), Gravity.BOTTOM))
            pic.setPadding(dp(4), dp(4), dp(4), 0)
            addView(pic, LayoutParams(-1, -2))
            addView(title, LayoutParams(-1, -2))
            addView(sub, LayoutParams(-1, -2))
            setOnFocusChangeListener { v, has ->
                v.animate().scaleX(if (has) 1.07f else 1f).scaleY(if (has) 1.07f else 1f).setDuration(120).start()
                v.elevation = if (has) dp(8).toFloat() else 0f
                title.setTypeface(null, if (has) Typeface.BOLD else Typeface.NORMAL)
            }
        }

        private fun badge(color: Int) = TextView(context).apply {
            setTextColor(Color.WHITE); textSize = 12f; typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(6), dp(2), dp(6), dp(2)); background = rounded(color, 0, 0)
        }
    }

    /** Keeps the thumbnail area at 16:9 of the card width. */
    private class Aspect169(ctx: Context) : FrameLayout(ctx) {
        override fun onMeasure(w: Int, h: Int) {
            val width = MeasureSpec.getSize(w)
            super.onMeasure(w, MeasureSpec.makeMeasureSpec(width * 9 / 16, MeasureSpec.EXACTLY))
        }
    }

    private fun rounded(fill: Int, stroke: Int, strokeW: Int) = GradientDrawable().apply {
        cornerRadius = dp(8).toFloat(); setColor(fill); if (strokeW > 0) setStroke(strokeW, stroke)
    }

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), act.resources.displayMetrics).toInt()

    companion object {
        const val COLS = 4
        private const val BG = 0xF2121212.toInt()
        private const val CARD = 0xFF1C1C1C.toInt()
        private const val ACCENT = 0xFF33B5E5.toInt()
        private const val MUTED = 0xFFA8A8A8.toInt()
    }
}
