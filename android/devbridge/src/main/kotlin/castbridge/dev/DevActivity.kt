package castbridge.dev

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** The only screen: what runs, and the two permissions the owner may need to grant once (install unknown apps, notifications). */
class DevActivity : Activity() {
    private lateinit var info: TextView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fun tv(t: String, sp: Float, c: Int = Color.WHITE) = TextView(this).apply { text = t; textSize = sp; setTextColor(c); setPadding(0, 12, 0, 12) }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(90, 60, 90, 60); gravity = Gravity.CENTER_HORIZONTAL; setBackgroundColor(0xFF0A0F1E.toInt()) }
        col.addView(tv("CastBridge Dev", 32f, 0xFFF5B027.toInt()))
        col.addView(tv("Outil de développement : accès SSH (port ${DevService.PORT}, clés seulement, réseau local) et installation d'applications. Jamais distribué.", 18f, 0xFFB8C0D6.toInt()))
        info = tv("", 20f); col.addView(info)
        col.addView(Button(this).apply { text = "Autoriser l'installation d'applications"; textSize = 20f
            setOnClickListener { startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))) } })
        col.addView(Button(this).apply { text = "Autoriser l'affichage par-dessus (confirmations)"; textSize = 20f
            setOnClickListener { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) } })
        col.addView(Button(this).apply { text = "Redémarrer le service SSH"; textSize = 20f; setOnClickListener { DevService.server?.stop(); DevService.start(this@DevActivity); refresh() } })
        setContentView(col)
        if (android.os.Build.VERSION.SDK_INT >= 33) runCatching { requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1) }
        DevService.start(this)
    }
    override fun onResume() { super.onResume(); refresh(); info.postDelayed({ refresh() }, 2_000) }
    private fun refresh() {
        val run = DevService.server?.running == true
        info.text = "SSH : " + (if (run) "actif" else "démarrage…") + "\nInstallation d'apps : " + (if (packageManager.canRequestPackageInstalls()) "autorisée" else "à autoriser (bouton ci-dessous)") +
            "\nAffichage par-dessus : " + (if (Settings.canDrawOverlays(this)) "autorisé" else "à autoriser") + "\nEmpreinte de l'hôte : " + (DevService.server?.hostKeyFingerprint() ?: "—")
    }
}
