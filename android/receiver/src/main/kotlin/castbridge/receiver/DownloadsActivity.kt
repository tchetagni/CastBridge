package castbridge.receiver

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import castbridge.core.dl.DlState
import castbridge.core.dl.DownloadManager
import castbridge.core.dl.DownloadSpace
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

/**
 * "Téléchargements" on the TV: everything reachable with the arrows and OK of the remote. Lists what is downloading
 * (progress, speed, time left), what is finished ("Regarder"), and lets you paste/type a link with the TV keyboard.
 * Reads the process-wide [TvDownloads]; closing this screen does not stop anything.
 */
class DownloadsActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "cb-dl-ui").apply { isDaemon = true } }
    private lateinit var header: TextView
    private lateinit var banner: TextView
    private lateinit var list: LinearLayout
    private lateinit var doneList: LinearLayout
    private lateinit var doneTitle: TextView
    private lateinit var empty: TextView
    private val rows = LinkedHashMap<String, Row>()
    private val doneRows = LinkedHashMap<String, TextView>()
    private var warned = false
    private var lastViews: List<DownloadManager.View> = emptyList()

    private class Row(val box: LinearLayout, val title: TextView, val bar: ProgressBar, val detail: TextView)

    private val dm: DownloadManager? get() = TvDownloads.get()?.manager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(TvStyle.BG); setPadding(dp(48), dp(24), dp(48), dp(24)) }
        header = text(26f, TvStyle.TEXT).apply { text = "Téléchargements" }
        banner = text(16f, castbridge.core.brand.BrandTokens.Semantic.WARNING_DARK).apply { visibility = View.GONE }
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(12), 0, dp(12)) }
        buttons.addView(button("Ajouter un lien") { askLink() })
        buttons.addView(button("Tout mettre en pause") { act { it.pauseAll() } })
        buttons.addView(button("Tout reprendre") { act { it.resumeAll() } })
        buttons.addView(button("À propos") { about() })
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        empty = text(19f, TvStyle.TEXT2).apply {
            text = "Rien en cours. Ajoutez un lien ici, ou depuis le téléphone : CastBridge > CastBridge TV > Téléchargements (vous pouvez aussi « Partager » un lien vers CastBridge)."
        }
        doneTitle = text(20f, TvStyle.TEXT).apply { text = "Terminés"; setPadding(0, dp(20), 0, dp(8)) }
        doneList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(list); addView(empty); addView(doneTitle); addView(doneList)
        }
        root.addView(header); root.addView(banner); root.addView(buttons)
        root.addView(ScrollView(this).apply { addView(content); isFillViewport = true }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        buttons.getChildAt(0).requestFocus()
    }

    override fun onResume() { super.onResume(); main.post(refresher) }
    override fun onPause() { main.removeCallbacks(refresher); super.onPause() }
    override fun onDestroy() { io.shutdownNow(); super.onDestroy() }

    private val refresher = object : Runnable {
        override fun run() { refresh(); main.postDelayed(this, 2000) }
    }

    private fun refresh() {
        val m = dm
        if (m == null) { banner.text = "Le gestionnaire de téléchargements n'est pas démarré."; banner.visibility = View.VISIBLE; return }
        io.execute {
            val views = runCatching { m.views() }.getOrDefault(emptyList())
            val done = m.finished()
            @Suppress("UNCHECKED_CAST")
            val json = runCatching { castbridge.core.dl.Json.parse(m.listJson()) as? Map<String, Any?> }.getOrNull()
            main.post { if (!isFinishing) render(m, views, done, json) }
        }
    }

    private fun render(m: DownloadManager, views: List<DownloadManager.View>, done: List<DownloadManager.Finished>, json: Map<String, Any?>?) {
        lastViews = views
        val engine = json?.get("engine") as? Map<*, *>
        val msg = (engine?.get("message") as? String).orEmpty()
        banner.text = when {
            engine?.get("available") == false -> "Moteur de téléchargement non inclus dans cette version de l'app."
            msg.isNotEmpty() -> msg
            else -> ""
        }
        banner.visibility = if (banner.text.isEmpty()) View.GONE else View.VISIBLE
        val g = json?.get("global") as? Map<*, *>
        val down = (g?.get("down") as? Number)?.toLong() ?: 0
        header.text = if (down > 0) "Téléchargements · ${speed(down)}" else "Téléchargements"
        if (!warned && !m.currentSettings.warningAccepted) { warned = true; showWarning() }

        // Update rows in place: rebuilding them would lose the remote's focus every 2 seconds.
        val ids = views.map { it.id }.toSet()
        rows.keys.filter { it !in ids }.forEach { id -> list.removeView(rows.remove(id)!!.box) }
        for (v in views) {
            val r = rows.getOrPut(v.id) { newRow().also { row -> list.addView(row.box); row.box.setOnClickListener { actions(v.id) } } }
            r.title.text = v.name
            val pct = if (v.total > 0) (v.done * 1000 / v.total).toInt() else 0
            r.bar.isIndeterminate = v.total <= 0 && v.state in setOf(DlState.CONNECTING, DlState.METADATA, DlState.DOWNLOADING)
            r.bar.progress = pct
            r.detail.text = detail(v)
            r.detail.setTextColor(if (v.state == DlState.ERROR || v.state == DlState.WAITING_SPACE || v.state == DlState.WAITING_DRIVE) TvStyle.ERROR else TvStyle.TEXT2)
        }
        empty.visibility = if (views.isEmpty()) View.VISIBLE else View.GONE
        val dids = done.map { it.id }.toSet()
        doneRows.keys.filter { it !in dids }.forEach { id -> doneList.removeView(doneRows.remove(id)) }
        done.forEachIndexed { i, f ->
            val t = doneRows.getOrPut(f.id) {
                text(19f, TvStyle.TEXT).apply { focusableBox(this); setOnClickListener { doneActions(f.id) } }.also { doneList.addView(it, i) }
            }
            t.text = "✓ ${f.name}\n   ${DownloadSpace.human(f.size)} · ${f.volumeLabel}" + if (f.files.isEmpty()) " · aucun fichier vidéo" else ""
        }
        doneTitle.visibility = if (done.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun detail(v: DownloadManager.View): String = buildString {
        append(v.label)
        if (v.total > 0) append(" · ${DownloadSpace.human(v.done)} sur ${DownloadSpace.human(v.total)} (${v.done * 100 / v.total} %)")
        if (v.down > 0) append(" · ${speed(v.down)}")
        if (v.eta >= 0) append(" · reste ${eta(v.eta)}")
        if (v.kind == "magnet" || v.kind == "torrent") { if (v.connections > 0) append(" · ${v.connections} sources") }
        else if (v.connections > 0) append(" · ${v.connections} connexions")
        append(" · ${v.volumeLabel}")
        v.error?.let { append("\n$it") }
    }

    private fun actions(id: String) {
        val v = lastViews.firstOrNull { it.id == id } ?: return          // no network call on the main thread
        val items = mutableListOf<Pair<String, () -> Unit>>()
        if (v.state.canPause) items += "Mettre en pause" to { act { it.pause(id) } }
        if (v.state.canResume) items += (if (v.state == DlState.ERROR) "Réessayer" else "Reprendre") to { act { it.resume(id) } }
        if (v.state == DlState.QUEUED || v.state == DlState.PAUSED) items += "Passer en premier" to { act { it.priority(id, "top") } }
        items += "Supprimer (garder ce qui est téléchargé)" to { act { it.remove(id, false) } }
        items += "Supprimer avec les fichiers" to { confirm("Supprimer « ${v.name} » et ses fichiers ?") { act { it.remove(id, true) } } }
        AlertDialog.Builder(this).setTitle(v.name).setItems(items.map { it.first }.toTypedArray()) { _, i -> items[i].second() }
            .setNegativeButton("Fermer", null).show()
    }

    private fun doneActions(id: String) {
        val f = dm?.finished()?.firstOrNull { it.id == id } ?: return
        val items = mutableListOf<Pair<String, () -> Unit>>()
        f.files.forEach { name -> items += "Regarder « $name »" to { play(name) } }
        items += "Retirer de la liste (garder le fichier)" to { act { it.remove(id, false) } }
        items += "Supprimer le fichier" to { confirm("Supprimer « ${f.name} » de la TV ?") { act { it.remove(id, true) } } }
        AlertDialog.Builder(this).setTitle(f.name).setItems(items.map { it.first }.toTypedArray()) { _, i -> items[i].second() }
            .setNegativeButton("Fermer", null).show()
    }

    /** Asks the TV's own server to play it (same path as the phone), then shows the player. */
    private fun play(name: String) {
        val pin = TvPrefs(this).pin()
        io.execute {
            val ok = runCatching {
                val c = URL("http://127.0.0.1:8765/api/play?name=" + URLEncoder.encode(name, "UTF-8").replace("+", "%20")).openConnection() as HttpURLConnection
                c.requestMethod = "POST"; castbridge.core.trust.TvCredential.apply(c, pin); c.doOutput = true; c.setFixedLengthStreamingMode(0)
                c.outputStream.close(); val code = c.responseCode; c.disconnect(); code == 200
            }.getOrDefault(false)
            main.post { if (ok) finish() else flash("Lecture impossible pour le moment.") }
        }
    }

    private fun askLink() {
        val m = dm ?: return
        if (!m.currentSettings.warningAccepted) { showWarning(); return }
        val input = EditText(this).apply {
            hint = "https://…  ou  magnet:?…"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_DONE
            setSingleLine()
        }
        AlertDialog.Builder(this).setTitle("Ajouter un lien").setMessage("Tapez ou collez l'adresse du fichier (http, https, ftp, sftp) ou un lien magnet.")
            .setView(input).setPositiveButton("Télécharger") { _, _ ->
                val t = input.text.toString()
                act { it.addLink(t) }
            }.setNegativeButton("Annuler", null).show()
        input.requestFocus()
    }

    private fun showWarning() {
        AlertDialog.Builder(this).setTitle("Avant de télécharger").setMessage(DownloadManager.WARNING)
            .setPositiveButton("J'ai compris") { _, _ -> io.execute { dm?.acceptWarning() } }
            .setNegativeButton("Plus tard", null).show()
    }

    private fun about() {
        AlertDialog.Builder(this).setTitle("À propos des téléchargements").setMessage(DownloadManager.ABOUT)
            .setPositiveButton("Fermer", null).show()
    }

    private fun confirm(q: String, yes: () -> Unit) {
        AlertDialog.Builder(this).setMessage(q).setPositiveButton("Supprimer") { _, _ -> yes() }.setNegativeButton("Annuler", null).show()
    }

    private fun act(f: (DownloadManager) -> DownloadManager.Result) {
        val m = dm ?: return
        io.execute {
            val r = runCatching { f(m) }.getOrElse { DownloadManager.Result.Refused(500, "error", it.message ?: "erreur") }
            main.post {
                when (r) {
                    is DownloadManager.Result.Refused -> flash(r.message)
                    is DownloadManager.Result.Ok -> r.note?.let(::flash)
                }
                refresh()
            }
        }
    }

    private fun flash(s: String) { android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_LONG).show() }

    // ---- views ----

    private fun newRow(): Row {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(10), dp(16), dp(10)) }
        focusableBox(box)
        val title = text(19f, TvStyle.TEXT).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.MIDDLE }
        val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 1000 }
        val detail = text(16f, TvStyle.TEXT2)
        box.addView(title); box.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(10))); box.addView(detail)
        (box.layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin = dp(6)
        return Row(box, title, bar, detail)
    }

    private fun focusableBox(v: View) {
        v.isFocusable = true; v.isClickable = true
        v.background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), GradientDrawable().apply { setColor(TvStyle.CARD_FOCUS); setStroke(dp(3), TvStyle.RING); cornerRadius = dp(TvStyle.R_MD).toFloat() })
            addState(intArrayOf(), GradientDrawable().apply { setColor(TvStyle.CARD); cornerRadius = dp(TvStyle.R_MD).toFloat() })
        }
        if (v is TextView) v.setPadding(dp(16), dp(10), dp(16), dp(10))
    }

    private fun button(label: String, onClick: () -> Unit) = TvStyle.styleButton(Button(this)).apply {
        text = label; setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(12) }
    }

    private fun text(sp: Float, color: Int) = TextView(this).apply { setTextSize(TypedValue.COMPLEX_UNIT_SP, sp); typeface = if (sp >= 24f) TvFonts.display else TvFonts.body; setTextColor(color); gravity = Gravity.START }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        fun speed(bps: Long) = when {
            bps >= 1L shl 20 -> String.format(java.util.Locale.FRANCE, "%.1f Mo/s", bps / (1L shl 20).toDouble())
            else -> "${bps shr 10} ko/s"
        }
        fun eta(s: Long) = when {
            s >= 3600 -> "${s / 3600} h ${s % 3600 / 60} min"
            s >= 60 -> "${s / 60} min"
            else -> "$s s"
        }
    }
}
