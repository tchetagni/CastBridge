package castbridge.receiver

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import castbridge.core.parental.SupervisionState

/**
 * « Surveillance de toute la TV » — guided setup (docs/PARENTAL.md). Shows the REAL state (never claims a protection that is not active)
 * and walks the parent through what Android requires:
 *  1. « Accès aux données d'utilisation » (to know which app is in front);
 *  2. « Afficher par-dessus les autres applications » (to bring the lock screen in front of another app);
 *  3. optional: the accessibility service (faster detection, and the only way when the usage-access screen is missing).
 * Some TV boxes hide these Settings screens: each step then shows the exact adb command. The switch on/off asks the parental PIN.
 * Classic views, D-pad only, blocks stacked vertically. Not exported. BACK goes back.
 */
class SupervisionSetupActivity : Activity() {
    private lateinit var content: LinearLayout
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ParentalHub.init(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(TvStyle.BG)
            val p = TvStyle.dp(this@SupervisionSetupActivity, 32); setPadding(p, TvStyle.dp(this@SupervisionSetupActivity, 20), p, TvStyle.dp(this@SupervisionSetupActivity, 12))
        }
        root.addView(ParentalUi.text(this, "Surveillance de toute la TV", ParentalUi.TITLE_SP, TvStyle.ACCENT, true))
        status = ParentalUi.text(this, "", 24f, TvStyle.TEXT, true).apply { setPadding(0, TvStyle.dp(this@SupervisionSetupActivity, 6), 0, TvStyle.dp(this@SupervisionSetupActivity, 6)) }
        root.addView(status)
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply {
            addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        })
        setContentView(root)
    }

    override fun onResume() { super.onResume(); render() }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) { finish(); return true }
        return super.onKeyDown(keyCode, event)
    }

    private fun note(s: String) = content.addView(ParentalUi.text(this, s, ParentalUi.SMALL_SP, TvStyle.MUTED).apply { setPadding(TvStyle.dp(this@SupervisionSetupActivity, 6), TvStyle.dp(this@SupervisionSetupActivity, 4), 0, TvStyle.dp(this@SupervisionSetupActivity, 6)) })
    private fun step(title: String, value: String?, onClick: () -> Unit) = content.addView(ParentalUi.row(this, title, value, onClick))
    private fun adb(cmd: String) = content.addView(ParentalUi.text(this, cmd, 17f, 0xFF8BC34A.toInt()).apply {
        setTextIsSelectable(true); typeface = android.graphics.Typeface.MONOSPACE; setPadding(TvStyle.dp(this@SupervisionSetupActivity, 6), 0, 0, TvStyle.dp(this@SupervisionSetupActivity, 8))
    })

    private fun render() {
        val info = ParentalHub.supervisionInfo()
        val setup = ParentalHub.setupMap()
        status.text = info.state.label + (info.detail?.let { " — $it" } ?: "")
        status.setTextColor(when (info.state) { SupervisionState.ACTIVE -> 0xFF8BC34A.toInt(); SupervisionState.OFF -> TvStyle.MUTED; else -> 0xFFFFB74D.toInt() })
        content.removeAllViews()
        val e = ParentalHub.engine
        val on = e.appSettings().supervise
        note("Sans cette surveillance, le contrôle parental ne voit que ce qui se passe DANS CastBridge-TV. Avec elle, les autres applications (YouTube, Netflix, navigateur, jeux…) sont comptées dans le temps d'écran et peuvent être bloquées. Rien n'est envoyé au serveur.")
        step(if (on) "Surveillance : activée — OK pour désactiver" else "Surveillance : désactivée — OK pour activer",
            "Demande le code parental. Elle ne protège que lorsque l'état ci-dessus indique « active ».") { toggle(on) }

        val usage = setup["usageGranted"] == true
        step("1. Accès aux données d'utilisation : " + if (usage) "accordé" else "à accorder",
            "Réglages > Applications > Accès spécial > Accès aux données d'utilisation > CastBridge-TV > Autoriser.") { openUsage(setup["usageSettingsExists"] == true) }
        if (!usage) { note("Si cet écran n'existe pas sur votre TV, branchez-la en adb et tapez :"); adb(setup["adbUsage"] as? String ?: "") }

        val overlay = setup["overlayGranted"] == true
        step("2. Afficher par-dessus les autres applications : " + if (overlay) "accordé" else "à accorder",
            "Nécessaire pour que l'écran « Accès protégé » apparaisse devant une autre application (Android 10 et plus).") { openOverlay() }
        if (!overlay) { note("Sinon, en adb :"); adb(setup["adbOverlay"] as? String ?: "") }

        step("3. (Facultatif) Détecteur rapide : service d'accessibilité " + if (setup["accessibilityOn"] == true) "activé" else "désactivé",
            "Plus rapide, et la seule solution si l'écran de l'étape 1 n'existe pas. Il ne lit que le nom de l'application au premier plan.") {
            startActivity(Intent(this, RemoteSetupActivity::class.java))
        }
        step("Vérifier maintenant", "Revenez ici après chaque réglage : l'état se met à jour.") { render() }
        note("Limites à connaître : un enfant non protégé peut retirer ces autorisations dans les Réglages de la TV. CastBridge le détecte et prévient le téléphone du parent, mais ne peut pas l'empêcher. Bloquez la catégorie « Réglages » du profil, et protégez l'accès à la TV (voir docs/PARENTAL.md).")
        step("Retour") { finish() }
        content.post { (0 until content.childCount).map { content.getChildAt(it) }.firstOrNull { it.isFocusable }?.requestFocus() }
    }

    private fun step(title: String, onClick: () -> Unit) = content.addView(ParentalUi.row(this, title, null, onClick))

    private fun toggle(on: Boolean) {
        val e = ParentalHub.engine
        if (!e.hasPin()) { ParentalUi.info(this, "Code parental", "Créez d'abord le code parental : tuile « Contrôle parental »."); return }
        if (e.config().profiles.isEmpty()) { ParentalUi.info(this, "Créez un profil", "Ajoutez d'abord un profil d'enfant dans « Contrôle parental »."); return }
        // always asks the PIN, even during a parent session: switching the protection is a parent decision
        ParentalUi.pinDialog(this, "Code parental", "Saisissez votre code pour ${if (on) "désactiver" else "activer"} la surveillance de toute la TV.",
            check = { pin -> ParentalHub.pinError(e.verifyPin(pin)) }, onOk = {
                e.editApps { it.copy(supervise = !on) }
                Toast.makeText(this, if (on) "Surveillance désactivée." else "Surveillance activée. Vérifiez l'état affiché en haut.", Toast.LENGTH_LONG).show()
                render()
            })
    }

    private fun openUsage(exists: Boolean) {
        if (!exists) { ParentalUi.info(this, "Écran introuvable", "Cette TV n'a pas l'écran « Accès aux données d'utilisation ». Utilisez la commande adb affichée sous l'étape 1, ou le détecteur rapide (étape 3)."); return }
        val pkgUri = Uri.parse("package:$packageName")
        // Android 10+ can open the page of this very app; older versions open the list
        if (!tryStart(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, pkgUri))) tryStart(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }

    private fun openOverlay() {
        if (!tryStart(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))))
            ParentalUi.info(this, "Écran introuvable", "Cette TV n'a pas l'écran « Afficher par-dessus les autres applications ». Utilisez la commande adb affichée sous l'étape 2.")
    }

    private fun tryStart(i: Intent): Boolean = runCatching { startActivity(i); true }.getOrDefault(false)
}
