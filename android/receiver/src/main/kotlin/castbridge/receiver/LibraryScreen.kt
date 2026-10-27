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
import castbridge.core.tv.BulkDelete
import castbridge.core.tv.BulkDelete.bulk
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
        /** A USB key Android is checking (« patientez ») or cannot read (the guide): said in the header and in the empty library, never silently. null = nothing to say. */
        fun volumeNote(): String? = null
        /** The button that goes with [volumeNote] when the key is unreadable (« Ouvrir les réglages de stockage »); null = no button. */
        fun volumeAction(): Pair<String, () -> Unit>? = null
    }

    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "cb-library").apply { isDaemon = true } }
    private val rows = ArrayList<Row>()
    private var signature = 0
    private val header: TextView
    /** « Ouvrir les réglages de stockage », only while a key is unreadable: a focusable line, OK opens the storage settings of the TV. */
    private val keyAction = TextView(act).apply {
        setTextColor(TvStyle.ACCENT); textSize = TvStyle.Type.BODY; typeface = TvFonts.bold; isFocusable = true; isClickable = true; visibility = View.GONE
        setPadding(TvStyle.dp(act, 12), TvStyle.dp(act, 8), TvStyle.dp(act, 12), TvStyle.dp(act, 8))
        background = android.graphics.drawable.StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), TvStyle.rounded(act, TvStyle.CARD_FOCUS, TvStyle.R_MD, TvStyle.RING, 3))
            addState(intArrayOf(), TvStyle.rounded(act, 0x00000000, TvStyle.R_MD))
        }
    }
    // ---- suppression en masse (décision du 2026-10-27) : sélection à la télécommande, puis UNE confirmation, suppression définitive
    private var selecting = false
    /** « Les plus gros d'abord » : pendant la sélection, une seule rangée triée par taille (au lieu des sections). */
    private var bySize = false
    private val selected = LinkedHashSet<String>()                    // clés [BulkDelete.key]
    private var allItems: List<LibraryItem> = emptyList()
    private var pendingFocusKey: String? = null
    @Volatile private var deleting = false
    private val bulkIo = Executors.newSingleThreadExecutor { r -> Thread(r, "cb-library-bulk").apply { isDaemon = true } }
    private fun LibraryItem.k() = BulkDelete.key(volumeId, name)

    private fun chip(text: String, color: Int = TvStyle.ACCENT, onClick: () -> Unit) = TextView(act).apply {
        this.text = text; setTextColor(color); textSize = TvStyle.Type.BODY; typeface = TvFonts.bold; isFocusable = true; isClickable = true
        setPadding(TvStyle.dp(act, 12), TvStyle.dp(act, 8), TvStyle.dp(act, 12), TvStyle.dp(act, 8))
        background = android.graphics.drawable.StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), TvStyle.rounded(act, TvStyle.CARD_FOCUS, TvStyle.R_MD, TvStyle.RING, 3))
            addState(intArrayOf(), TvStyle.rounded(act, 0x00000000, TvStyle.R_MD))
        }
        setOnClickListener { onClick() }
    }
    private val bulkEntry = chip("Supprimer en masse…") { bulkMenu() }.apply { visibility = View.GONE }
    private val selTitle = TextView(act).apply { setTextColor(Color.WHITE); textSize = TvStyle.Type.SUBTITLE; typeface = TvFonts.bold; setPadding(0, 0, TvStyle.dp(act, 24), 0) }
    private val selDelete = chip("Supprimer définitivement", TvStyle.ERROR) { confirmBulk() }
    private val selAll = chip("Tout sélectionner") { toggleAll() }
    private val selCancel = chip("Annuler") { leaveSelection() }
    private val selBar = LinearLayout(act).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL; visibility = View.GONE
        addView(selTitle); addView(selDelete); addView(selAll); addView(selCancel)
    }
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
        root.addView(keyAction)
        root.addView(bulkEntry)
        root.addView(selBar)
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

    fun release() { hide(); io.shutdownNow(); bulkIo.shutdownNow() }

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
        // a key under check or unreadable: the button of the guide (the header already says why the videos of the key are missing)
        val act0 = runCatching { api.volumeAction() }.getOrNull()
        keyAction.visibility = if (act0 == null) View.GONE else View.VISIBLE
        keyAction.text = act0?.first.orEmpty()
        keyAction.setOnClickListener { act0?.second?.invoke() }
        val note = runCatching { api.volumeNote() }.getOrNull()
        allItems = items
        if (selecting) selected.retainAll(items.map { it.k() }.toSet())              // un fichier disparu ne reste pas coché
        val sig = items.hashCode() * 31 + (note?.hashCode() ?: 0)
        if (sig == signature && rows.isNotEmpty() && !focusFirst) return
        signature = sig
        rows.clear()
        if (selecting && bySize) {
            val sorted = items.sortedWith(compareBy<LibraryItem>({ -it.size }, { it.name.lowercase() }))
            rows += Row.Header("Les plus gros d'abord", sorted.size)
            sorted.forEachIndexed { i, it -> rows += Row.Card("size", it, i, sorted) }
        } else {
            val sections = LibrarySections.build(items)
            if (sections.isEmpty()) rows += Row.Empty((note?.let { "$it\n\n" }.orEmpty()) + "Aucun fichier sur la TV.\nEnvoyez des vidéos depuis l'app CastBridge du téléphone : elles apparaîtront ici.")
            for (s in sections) {
                rows += Row.Header(s.title, s.items.size)
                s.items.forEachIndexed { i, it -> rows += Row.Card(s.id, it, i, s.items) }
            }
        }
        bulkEntry.visibility = if (!selecting && items.isNotEmpty()) View.VISIBLE else View.GONE
        refreshBar()
        adapter.notifyDataSetChanged()
        val want = pendingFocusKey; pendingFocusKey = null
        if (focusFirst) main.post {
            val first = want?.let { k -> rows.indexOfFirst { it is Row.Card && it.item.k() == k } }?.takeIf { it >= 0 } ?: rows.indexOfFirst { it is Row.Card }
            if (first >= 0) { list.scrollToPosition(first); main.postDelayed({ list.findViewHolderForAdapterPosition(first)?.itemView?.requestFocus() }, 60) }
        }
    }

    /** RETOUR dans la bibliothèque : quitte la sélection (sans rien supprimer) ; true = la touche est consommée. Pendant une suppression, elle ne fait rien. */
    fun onBack(): Boolean {
        if (deleting) return true
        if (!selecting) return false
        leaveSelection(); return true
    }

    // ---------------------------------------------------------------- suppression en masse

    private fun refuseIfChild(): Boolean {
        if (!ParentalHub.childActive()) return false
        api.flash("Un profil enfant est actif : la suppression en masse est refusée.")
        return true
    }

    /** MENU sur un fichier > « Sélectionner plusieurs… » : la sélection s'ouvre avec ce fichier coché. */
    private fun startSelection(first: LibraryItem) {
        if (refuseIfChild()) return
        enterSelection(setOf(first.k()), false, first.k())
    }

    private fun enterSelection(pre: Set<String>, sizeOrder: Boolean, focusKey: String?) {
        selecting = true; bySize = sizeOrder
        selected.clear(); selected.addAll(pre)
        pendingFocusKey = focusKey
        apply(allItems, header.text.toString(), true)
    }

    private fun leaveSelection() {
        if (!selecting) return
        val focused = (list.focusedChild as? MediaCard)?.item?.k()
        selecting = false; bySize = false; selected.clear()
        pendingFocusKey = focused
        apply(allItems, header.text.toString(), true)
    }

    private fun refreshBar() {
        selBar.visibility = if (selecting) View.VISIBLE else View.GONE
        if (!selecting) return
        val e = allItems.map { it.bulk() }
        selTitle.text = BulkDelete.headline(BulkDelete.count(e, selected), BulkDelete.totalSize(e, selected))
        selAll.text = if (selected.size >= allItems.size) "Tout désélectionner" else "Tout sélectionner"
        selDelete.alpha = if (selected.isEmpty()) 0.4f else 1f
    }

    private fun toggle(i: LibraryItem, card: MediaCard) {
        if (deleting) return
        val k = i.k()
        if (!selected.remove(k)) selected.add(k)
        card.setSelection(k in selected)
        refreshBar()
    }

    private fun toggleAll() {
        if (deleting) return
        if (selected.size >= allItems.size) selected.clear() else selected.addAll(allItems.map { it.k() })
        for (c in 0 until list.childCount) (list.getChildAt(c) as? MediaCard)?.let { m -> m.item?.let { m.setSelection(it.k() in selected) } }
        refreshBar()
    }

    /** « Supprimer en masse… » : les préréglages ; chacun ouvre la sélection DÉJÀ cochée, que l'on peut encore corriger. */
    private fun bulkMenu() {
        if (refuseIfChild()) return
        val e = allItems.map { it.bulk() }
        fun go(pre: Set<String>, sizeOrder: Boolean = false) {
            if (pre.isEmpty() && !sizeOrder) { api.flash("Aucun fichier ne correspond."); return }
            enterSelection(pre, sizeOrder, null)
        }
        val entries = listOf<Pair<String, () -> Unit>>(
            "Un dossier…" to { pickFolder(e) { f -> go(BulkDelete.inFolder(e, f)) } },
            "Vidéos déjà vues" to { go(BulkDelete.watched(e)) },
            "Non lues depuis…" to {
                val months = listOf(1, 3, 6, 12)
                AlertDialog.Builder(act).setTitle("Non lues depuis…")
                    .setItems(months.map { if (it == 12) "12 mois (1 an)" else "$it mois" }.toTypedArray()) { _, w ->
                        go(BulkDelete.notPlayedSince(e, BulkDelete.monthsToDays(months[w]), System.currentTimeMillis()))
                    }.setNegativeButton("Annuler", null).show()
            },
            "Les plus gros d'abord" to { go(emptySet(), sizeOrder = true) },
        )
        AlertDialog.Builder(act).setTitle("Supprimer en masse")
            .setItems(entries.map { it.first }.toTypedArray()) { _, w -> entries[w].second() }
            .setNegativeButton("Fermer", null).show()
    }

    private fun pickFolder(e: List<BulkDelete.Entry>, then: (String) -> Unit) {
        val fs = BulkDelete.folders(e)
        if (fs.isEmpty()) { api.flash("Aucun dossier dans la bibliothèque."); return }
        AlertDialog.Builder(act).setTitle("Quel dossier ?")
            .setItems(fs.map { "${it.folder}  ·  ${BulkDelete.files(it.count)}  ·  ${BulkDelete.size(it.bytes)}" }.toTypedArray()) { _, w -> then(fs[w].folder) }
            .setNegativeButton("Annuler", null).show()
    }

    /** UNE confirmation (nombre et taille totale), le choix sûr a le focus ; puis le lot part sur le serveur de la TV et la progression s'affiche. */
    private fun confirmBulk() {
        if (deleting) return
        val e = allItems.map { it.bulk() }
        val n = BulkDelete.count(e, selected)
        if (n == 0) { api.flash("Rien n'est sélectionné."); return }
        val chosen = allItems.filter { it.k() in selected }
        AlertDialog.Builder(act).setTitle("Suppression définitive")
            .setMessage(BulkDelete.confirmText(n, BulkDelete.totalSize(e, selected)))
            .setPositiveButton("Supprimer définitivement") { _, _ -> runBulk(chosen) }
            .setNegativeButton("Annuler", null).show()
            .getButton(AlertDialog.BUTTON_NEGATIVE)?.requestFocus()
    }

    private fun runBulk(files: List<LibraryItem>) {
        deleting = true
        val total = files.size
        val dlg = AlertDialog.Builder(act).setTitle("Suppression définitive").setMessage(BulkDelete.progressText(0, total)).setCancelable(false).show()
        var result: String? = null
        runCatching {
            bulkIo.execute {
                val err = api.call { c ->
                    val job = castbridge.core.net.JsonLite.obj(c.deleteBatch(files.map { BulkDelete.Target(it.name, it.volumeId) }))["job"] as String
                    while (true) {
                        val st = castbridge.core.net.JsonLite.obj(c.deleteBatchStatus(job))
                        val done = (st["done"] as? Number)?.toInt() ?: 0
                        main.post { dlg.setMessage(BulkDelete.progressText(done, total)) }
                        if (st["finished"] == true) {
                            val skipped = (st["skipped"] as? List<*>).orEmpty().mapNotNull { m -> (m as? Map<*, *>)?.let { BulkDelete.Skip(it["name"] as? String ?: "", it["reason"] as? String ?: "") } }
                            result = BulkDelete.resultText(done - skipped.size, (st["freed"] as? Number)?.toLong() ?: 0L, skipped)
                            break
                        }
                        Thread.sleep(400)
                    }
                }
                main.post {
                    runCatching { dlg.dismiss() }
                    deleting = false
                    val text = result ?: if (err == "busy") "Une suppression est déjà en cours." else "Suppression impossible : $err"
                    selecting = false; bySize = false; selected.clear()
                    reload(focusFirst = true); api.changed()
                    AlertDialog.Builder(act).setTitle("Suppression").setMessage(text).setPositiveButton("OK", null).show()
                        .getButton(AlertDialog.BUTTON_POSITIVE)?.requestFocus()
                }
            }
        }.onFailure { deleting = false; runCatching { dlg.dismiss() } }
    }

    // ---------------------------------------------------------------- actions (also used by the home)

    /** OK on a card: play, offering to resume where the viewer stopped; other files open their actions. */
    fun open(i: LibraryItem, section: List<LibraryItem>, index: Int) {
        if (i.type == MediaType.OTHER) { actions(i, section, index); return }
        if (castbridge.core.tv.ResumePolicy.offer(i.meta.resumeMs, i.meta.durationMs) && !i.meta.watched) {
            AlertDialog.Builder(act).setTitle(i.title)
                .setItems(arrayOf(castbridge.core.tv.ResumePolicy.label(i.meta.resumeMs), castbridge.core.tv.ResumePolicy.RESTART_LABEL)) { _, w ->
                    play(i, if (w == 0) i.meta.resumeMs else 0)
                }.setNegativeButton("Annuler", null).show()
        } else play(i, 0)
    }

    private fun play(i: LibraryItem, pos: Long) {
        // Parental control: a video above the profile's age, in "lock" mode, asks the parental PIN first (docs/PARENTAL.md)
        if (ParentalHub.needsPin(i)) ParentalHub.authorize(act, "Vidéo verrouillée : « ${i.title} »") { ParentalHub.grantPlay(i.name); run("Lecture") { it.play(i.name, pos) } }
        else run("Lecture") { it.play(i.name, pos) }
    }

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
        items += "Sélectionner plusieurs…" to { startSelection(i) }
        if (i.type != MediaType.OTHER) items += "Classer (contrôle parental)…" to { ParentalHub.rateDialog(act, i.name, i.title) }
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
                    card.setOnClickListener { row()?.let { if (selecting) toggle(it.item, card) else open(it.item, it.sectionItems, it.index) } }
                    card.setOnLongClickListener { row()?.let { if (selecting) toggle(it.item, card) else actions(it.item, it.sectionItems, it.index) }; true }
                    card.setOnKeyListener { _, code, ev ->
                        if (ev.action == KeyEvent.ACTION_DOWN && (code == KeyEvent.KEYCODE_MENU || code == KeyEvent.KEYCODE_INFO)) {
                            row()?.let { if (selecting) toggle(it.item, card) else actions(it.item, it.sectionItems, it.index) }; true
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
                is Row.Card -> (h.itemView as MediaCard).apply { bind(r.item, thumbs); setSelection(if (selecting) r.item.k() in selected else null) }
            }
        }
    }

    companion object { const val COLS = 4 }
}
