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
    private lateinit var access: Button
    private val adapter by lazy { ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, ArrayList()) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(60, 30, 60, 30) }
        col.addView(TextView(this).apply { text = "Choisir le fichier d'activation"; textSize = 26f; setTextColor(0xFFF5B027.toInt()); setTypeface(typeface, Typeface.BOLD) })
        title = TextView(this).apply { textSize = 20f; setTextColor(Color.WHITE); setPadding(0, 8, 0, 8) }; col.addView(title)
        notice = TextView(this).apply { textSize = 18f; setTextColor(0xFFFF8A80.toInt()); visibility = View.GONE }; col.addView(notice)
        access = Button(this).apply { text = "Autoriser l'accès aux fichiers (facultatif)"; textSize = 18f; visibility = View.GONE; setOnClickListener { askAccess() } }; col.addView(access)
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
        browser = FileBrowser(roots)
        browser.startAt(places)
        draw()
    }

    private fun draw() {
        val v = browser.view()
        title.text = v.title
        adapter.clear()
        adapter.addAll(v.rows.map { r -> if (r.isDir) r.label else if (r.selectable) "${r.label}   (${r.size} o)" else "${r.label}   (${r.size / 1024} Kio : trop gros)" })
        adapter.notifyDataSetChanged()
        notice.visibility = if (v.notice == null) View.GONE else View.VISIBLE; notice.text = v.notice.orEmpty()
        access.visibility = if (v.notice != null && v.notice!!.contains("permission") && canAskAll()) View.VISIBLE else View.GONE
        list.requestFocus(); if (adapter.count > 0) list.setSelection(0)
    }

    private fun onAction(a: BrowseAction) {
        when (a) {
            is BrowseAction.Redraw -> draw()
            is BrowseAction.Picked -> { setResult(RESULT_OK, Intent().putExtra(EXTRA_PATH, a.file.path)); finish() }
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
    private fun canAskAll(): Boolean = Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager() && allFilesIntent().resolveActivity(packageManager) != null
    private fun askAccess() { if (canAskAll()) runCatching { startActivity(allFilesIntent()) } }
    override fun onResume() { super.onResume(); if (::browser.isInitialized) build() }

    companion object { const val EXTRA_PATH = "path" }
}
