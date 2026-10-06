package castbridge.receiver

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import castbridge.core.tv.activation.BrowseAction
import castbridge.core.tv.activation.ExplorerStart
import castbridge.core.tv.activation.BrowseRoot
import castbridge.core.tv.activation.FileBrowser
import java.io.File

/**
 * Built-in file explorer of CastBridge-TV (poor boxes have no system file picker): full screen, 5-key remote (UP/DOWN = list, OK = open a folder or choose a file, BACK = parent folder then
 * volume list then quit). Read only: it never writes, deletes or renames. The navigation is the pure [FileBrowser] (JVM-tested); this class only draws it and asks for the storage permissions.
 * The app's own folder of every volume (Android/data/castbridge.receiver/files) is readable with no permission, and it is where the explorer starts when nothing else can be listed.
 */
class FilePickActivity : Activity() {
    private lateinit var browser: FileBrowser
    private lateinit var title: TextView
    private lateinit var notice: TextView
    private lateinit var list: ListView
    /** Rows as drawn: large type for a 720p TV seen from the sofa; a file that cannot be chosen stays listed, greyed, with its reason. */
    private var shown: List<castbridge.core.tv.activation.BrowseRow> = emptyList()
    private val adapter by lazy {
        object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, ArrayList()) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View = (super.getView(position, convertView, parent) as TextView).apply {
                val r = shown.getOrNull(position)
                textSize = 22f; setPadding(16, 14, 16, 14)
                setTextColor(when { r == null -> Color.WHITE; r.action -> 0xFFF5B027.toInt(); r.info || !r.selectable -> 0xFF7B849C.toInt(); else -> Color.WHITE })
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(60, 30, 60, 30) }
        col.addView(TextView(this).apply { text = "Choisir le fichier d'activation"; textSize = 26f; setTextColor(0xFFF5B027.toInt()); setTypeface(typeface, Typeface.BOLD) })
        title = TextView(this).apply { textSize = 20f; setTextColor(Color.WHITE); setPadding(0, 8, 0, 8) }; col.addView(title)
        notice = TextView(this).apply { textSize = 20f; setTextColor(0xFFFF8A80.toInt()); visibility = View.GONE }; col.addView(notice)
        list = ListView(this).apply {
            this.adapter = this@FilePickActivity.adapter; isFocusable = true; isFocusableInTouchMode = true; divider = null
            setSelector(android.R.drawable.list_selector_background)
            setOnItemClickListener { _, _, i, _ -> onAction(browser.open(i)) }
        }
        col.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        col.addView(TextView(this).apply { text = "Haut/bas : choisir · OK : ouvrir ou sélectionner · Retour : dossier parent"; textSize = 15f; setTextColor(0xFF7B849C.toInt()); gravity = Gravity.CENTER_HORIZONTAL })
        setContentView(col.also { it.setBackgroundColor(0xFF0A0F1E.toInt()) })
        askReadPermissionOnce()
        build()
    }

    private fun build() {
        val own = getExternalFilesDirs(null).filterNotNull()
        val roots = ArrayList<BrowseRoot>(); val places = ArrayList<File>()
        for (d in own) {
            val base = d.path.substringBefore("/Android/", "").takeIf { it.isNotEmpty() } ?: continue
            val id = Regex("^/storage/([^/]+)$").find(base)?.groupValues?.get(1)?.takeIf { it != "emulated" && it != "self" }
            if (roots.none { it.dir.path == base }) roots += if (id == null) BrowseRoot("Stockage interne", File(base), null) else BrowseRoot("Clé USB", File(base), id)
        }
        if (roots.none { it.id == null }) runCatching { roots += BrowseRoot("Stockage interne", Environment.getExternalStorageDirectory(), null) }
        roots.sortBy { it.id == null }                                       // keys first
        for (r in roots) { places += File(r.dir, "Download/CastBridge"); places += File(r.dir, "Download") }
        places += own                                                         // always readable: the app's own folder of each volume
        val access = ActivationCenter.storageAccess()
        // without the permission Download looks empty to the app: its own folder (the only readable one) comes first
        browser = FileBrowser(roots, access = access, settingsScreen = canAskAll(), systemPicker = intent.getBooleanExtra(EXTRA_SYSTEM_AVAILABLE, false), ownPath = own.firstOrNull { it.path.contains("/storage/") && !it.path.contains("/emulated/") }?.path ?: own.firstOrNull()?.path)
        browser.startAt(ExplorerStart.places(roots, own, access))
        draw()
    }

    private fun draw() {
        val v = browser.view()
        title.text = v.title
        adapter.clear(); shown = v.rows
        adapter.addAll(v.rows.map { r -> if (r.action) "▶ " + r.label else if (r.info) r.label else if (r.isDir) r.label else if (r.reason == null) "${r.label}   (${size(r.size)})" else "${r.label}   (${size(r.size)} : ${r.reason})" })
        adapter.notifyDataSetChanged()
        notice.visibility = if (v.notice == null) View.GONE else View.VISIBLE; notice.text = v.notice.orEmpty()
        list.requestFocus(); if (adapter.count > 0) list.setSelection(0)
    }

    private fun onAction(a: BrowseAction) {
        when (a) {
            is BrowseAction.Redraw -> draw()
            is BrowseAction.Picked -> { setResult(RESULT_OK, Intent().putExtra(EXTRA_PATH, a.file.path)); finish() }
            BrowseAction.AskAccess -> askAccess()
            BrowseAction.SystemPicker -> { setResult(RESULT_OK, Intent().putExtra(EXTRA_SYSTEM, true)); finish() }
            is BrowseAction.Refused -> { notice.visibility = View.VISIBLE; notice.text = a.message }
            BrowseAction.Quit -> { setResult(RESULT_CANCELED); finish() }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) { onAction(browser.back()); return true }
        return super.onKeyDown(keyCode, event)
    }

    // ---- storage permissions: never blocking, the own folder of each volume needs none ----
    private fun askReadPermissionOnce() {
        if (Build.VERSION.SDK_INT <= 32 && checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            runCatching { requestPermissions(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE), 91) }
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 91 && ::browser.isInitialized) build()
    }

    private fun allFilesIntent() = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName"))
    /** True when the settings screen exists on this box (no dead button otherwise). */
    private fun canAskAll(): Boolean = Build.VERSION.SDK_INT >= 30 && allFilesIntent().resolveActivity(packageManager) != null
    private fun askAccess() { if (canAskAll()) runCatching { startActivity(allFilesIntent()) } }
    override fun onResume() { super.onResume(); if (::browser.isInitialized) build() }

    private fun size(b: Long) = when { b < 1024 -> "$b o"; b < 1024 * 1024 -> "${b / 1024} Kio"; else -> "${b / (1024 * 1024)} Mio" }

    companion object {
        const val EXTRA_PATH = "path"
        /** Result: the owner chose « Ouvrir l'explorateur du système » (the activation screen opens it). */
        const val EXTRA_SYSTEM = "system"
        /** Input: an app answers ACTION_OPEN_DOCUMENT on this box. */
        const val EXTRA_SYSTEM_AVAILABLE = "systemAvailable"
    }
}
