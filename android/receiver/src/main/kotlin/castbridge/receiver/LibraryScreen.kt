package castbridge.receiver

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import castbridge.core.tv.LibraryItem
import castbridge.core.tv.LibraryLogic
import castbridge.core.tv.LibrarySections
import castbridge.core.tv.MediaType
import castbridge.core.tv.StorageLine
import java.util.concurrent.Executors

/**
 * The whole TV library as a grid (from the home's "Toute la bibliothèque" tile): sections "Reprendre", "Récemment ajoutés",
 * "Toutes", "Autres fichiers", cards shared with the home ([MediaCard]). D-pad: OK = play (offers "Reprendre à 12:34" /
 * "Depuis le début"), MENU or a long OK = actions. Only the visible cards exist (RecyclerView); thumbnails come from [TvThumbs].
 * Actions go through the app's own HTTP API on loopback, so they follow exactly the same rules (PIN, volumes, locks) as the
 * phone and the web page. Also used by the home for the play prompt and the actions menu.
 */
class LibraryScreen(
    private val act: Activity,
    private val container: FrameLayout,
    private val thumbs: TvThumbs,
    private val api: Api,
) {
    /** What the screen needs from the app. */
    interface Api {
        fun items(): List<LibraryItem>
        fun volumes(): List<Pair<String, String>>          // (id, label) of the volumes a file may be moved to
        fun header(): String
        /** Runs an HTTP call on the TV's own API (loopback) off the main thread; returns an error message or null. */
        fun call(block: (castbridge.core.tv.TvClient) -> Unit): String?
        fun flash(msg: String)
        /** Plays [names] one after the other from [start] (a section of the library). */
        fun playAll(names: List<String>, start: Int)
        /** Something changed (played, renamed...): screens showing the library reload. */
        fun changed() {}
    }

    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "cb-library").apply { isDaemon = true } }
    private val rows = ArrayList<Row>()
    private var signature = 0
    private val header: TextView
    private val list: RecyclerView
    private val adapter = Adapter()
    val visible get() = container.visibility == View.VISIBLE

    private sealed class Row {
        abstract val id: Long
        data class Header(val title: String, val count: Int) : Row() { override val id = ("h:$title").hashCode().toLong() }
        data class Card(val section: String, val item: LibraryItem, val index: Int, val sectionItems: List<LibraryItem>) : Row() {
            override val id = ("c:$section:${item.volumeId}:${item.name}").hashCode().toLong() shl 1 or 1
        }
        data class Empty(val text: String) : Row() { override val id = 7L }
    }

    init {
        val root = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(TvStyle.BG)
            setPadding(TvStyle.dp(act, 48), TvStyle.dp(act, 28), TvStyle.dp(act, 48), 0)
        }
        root.addView(TextView(act).apply { text = "Bibliothèque"; setTextColor(Color.WHITE); textSize = 30f; typeface = TvFonts.bold })
        header = TextView(act).apply { setTextColor(TvStyle.MUTED); textSize = TvStyle.Type.CAPTION; setPadding(0, TvStyle.dp(act, 4), 0, TvStyle.dp(act, 8)) }
        root.addView(header)
        list = RecyclerView(act).apply {
            layoutManager = GridLayoutManager(act, COLS).apply {
                spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int) = if (rows.getOrNull(position) is Row.Card) 1 else COLS
                }
            }
            adapter = this@LibraryScreen.adapter
            clipToPadding = false; clipChildren = false
            setPadding(0, TvStyle.dp(act, 4), 0, TvStyle.dp(act, 48))
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
        container.fadeTo(true)
        header.text = api.header()
        reload(focusFirst = true)
        main.removeCallbacks(tick); main.postDelayed(tick, 5000)
    }

    fun hide() {
        container.fadeTo(false)
        main.removeCallbacks(tick)
    }

    fun release() { hide(); io.shutdownNow() }

    /** The TV made a thumbnail: show it if its card is on screen. */
    fun onThumbReady(name: String) = main.post {
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
        if (sections.isEmpty()) rows += Row.Empty("Aucun fichier sur la TV.\nEnvoyez des vidéos depuis l'app CastBridge du téléphone : elles apparaîtront ici.")
        for (s in sections) {
            rows += Row.Header(s.title, s.items.size)
            s.items.forEachIndexed { i, it -> rows += Row.Card(s.id, it, i, s.items) }
        }
        adapter.notifyDataSetChanged()
        if (focusFirst) main.post {
            val first = rows.indexOfFirst { it is Row.Card }
            if (first >= 0) { list.scrollToPosition(first); main.postDelayed({ list.findViewHolderForAdapterPosition(first)?.itemView?.requestFocus() }, 60) }
        }
    }

    // ---------------------------------------------------------------- actions (also used by the home)

    /** OK on a card: play, offering to resume where the viewer stopped; other files open their actions. */
    fun open(i: LibraryItem, section: List<LibraryItem>, index: Int) {
        if (i.type == MediaType.OTHER) { actions(i, section, index); return }
        if (i.meta.resumeMs > 0 && !i.meta.watched) {
            AlertDialog.Builder(act).setTitle(i.title)
                .setItems(arrayOf("Reprendre à ${LibraryLogic.clock(i.meta.resumeMs)}", "Depuis le début")) { _, w ->
                    play(i, if (w == 0) i.meta.resumeMs else 0)
                }.setNegativeButton("Annuler", null).show()
        } else play(i, 0)
    }

    private fun play(i: LibraryItem, pos: Long) = run("Lecture") { it.play(i.name, pos) }

    fun actions(i: LibraryItem, section: List<LibraryItem>, index: Int) {
        val items = ArrayList<Pair<String, () -> Unit>>()
        val names = section.filter { it.type != MediaType.OTHER }.map { it.name }
        if (i.type != MediaType.OTHER) {
            if (i.meta.resumeMs > 0) items += "Reprendre à ${LibraryLogic.clock(i.meta.resumeMs)}" to { play(i, i.meta.resumeMs) }
            items += "Lire depuis le début" to { play(i, 0) }
            val from = names.indexOf(i.name)
            if (names.size > 1 && from >= 0) items += "Lire la suite de cette rangée (${names.size - from} fichiers)" to { api.playAll(names, from) }
            items += (if (i.meta.watched) "Marquer comme non vu" else "Marquer comme vu") to { run("Marquage") { it.setWatched(i.name, !i.meta.watched) } }
        } else if (i.name.endsWith(".apk", true)) {
            items += "Installer cette application" to { run("Installation") { it.installApks(listOf(i.name)) }; api.flash("Installation demandée : validez à l'écran") }
        }
        api.volumes().filter { it.first != i.volumeId }.forEach { (id, label) ->
            items += "Déplacer vers $label" to { run("Déplacement") { it.moveFile(i.name, id) }; api.flash("Déplacement vers $label en cours…") }
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
            .setMessage("${StorageLine.size(i.size)}, ${if (i.volumeKind == castbridge.core.tv.VolumeKind.INTERNAL) "sur la TV" else "sur ${i.volumeLabel}"}. Cette action est définitive.")
            .setPositiveButton("Supprimer") { _, _ -> run("Suppression") { it.delete(i.name, i.volumeId) } }
            .setNegativeButton("Annuler", null).show()
            .getButton(AlertDialog.BUTTON_NEGATIVE)?.requestFocus()       // the safe choice has the focus
    }

    private fun run(what: String, block: (castbridge.core.tv.TvClient) -> Unit) {
        runCatching {
            io.execute {
                val err = api.call(block)
                main.post { if (err != null) api.flash("$what impossible : $err"); reload(); api.changed() }
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
            1 -> {
                val card = MediaCard(parent.context)
                val m = TvStyle.dp(act, 10)
                card.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(m, m, m, m) }
                TvStyle.focusZoom(card)
                object : RecyclerView.ViewHolder(card) {}.also { h ->
                    fun row() = rows.getOrNull(h.bindingAdapterPosition) as? Row.Card
                    card.setOnClickListener { row()?.let { open(it.item, it.sectionItems, it.index) } }
                    card.setOnLongClickListener { row()?.let { actions(it.item, it.sectionItems, it.index) }; true }
                    card.setOnKeyListener { _, code, ev ->
                        if (ev.action == KeyEvent.ACTION_DOWN && (code == KeyEvent.KEYCODE_MENU || code == KeyEvent.KEYCODE_INFO)) {
                            row()?.let { actions(it.item, it.sectionItems, it.index) }; true
                        } else false
                    }
                }
            }
            else -> object : RecyclerView.ViewHolder(TextView(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                setTextColor(if (viewType == 0) TvStyle.ACCENT else TvStyle.MUTED); textSize = 20f
                if (viewType == 0) typeface = TvFonts.bold
                setPadding(TvStyle.dp(act, 4), TvStyle.dp(act, 18), 0, TvStyle.dp(act, 8)); isFocusable = false
            }) {}
        }

        override fun onBindViewHolder(h: RecyclerView.ViewHolder, position: Int) {
            when (val r = rows[position]) {
                is Row.Header -> (h.itemView as TextView).text = "${r.title}  ·  ${r.count}"
                is Row.Empty -> (h.itemView as TextView).text = r.text
                is Row.Card -> (h.itemView as MediaCard).bind(r.item, thumbs)
            }
        }
    }

    companion object { const val COLS = 4 }
}
