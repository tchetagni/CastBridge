package castbridge.receiver

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * "Télécommande du téléphone" on the TV: what the phone remote can do, and — step by step — how to switch on the optional
 * « toute la TV » mode (accessibility service), with a button to the right settings screen. Opened from the home tile or by
 * the phone (POST /api/remote/system/setup).
 */
class RemoteSetupActivity : Activity() {
    private lateinit var state: TextView
    private lateinit var open: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(56), dp(28), dp(56), dp(28)) }
        box.addView(text(26f, Color.WHITE, "Télécommande du téléphone"))
        box.addView(text(16f, Color.rgb(200, 200, 200),
            "Dans l'app CastBridge du téléphone, onglet « CastBridge TV », touchez « Télécommande » : pavé directionnel, OK, Retour, " +
                "lecture, volume, chiffres, clavier et pavé tactile. Elle pilote toujours les écrans de CastBridge (accueil, bibliothèque, " +
                "lecteur, quiz, échecs, téléchargements), par le Wi-Fi ou, sans réseau commun, par le Bluetooth.").apply { setPadding(0, dp(8), 0, dp(12)) })
        state = text(18f, Color.rgb(51, 181, 229), "").apply { setPadding(0, dp(4), 0, dp(12)) }
        box.addView(state)
        box.addView(text(20f, Color.WHITE, "Piloter toute la TV (facultatif)"))
        box.addView(text(16f, Color.rgb(220, 220, 220),
            "Android ne laisse pas une application appuyer sur les touches des autres applications. Pour que la télécommande du " +
                "téléphone fonctionne aussi hors de CastBridge (Retour, Accueil de la TV, déplacer la sélection et OK dans les autres " +
                "apps, saisir du texte), activez le service d'accessibilité « CastBridge Télécommande » :\n\n" +
                "1. Choisissez « Ouvrir les réglages d'accessibilité » ci-dessous (ou Réglages › Préférences de l'appareil › Accessibilité).\n" +
                "2. Sélectionnez « CastBridge Télécommande » dans la liste des services.\n" +
                "3. Activez-le et confirmez : Android affiche un avertissement général sur les services d'accessibilité.\n" +
                "4. Revenez ici avec Retour : l'état ci-dessus passe à « actif », et le téléphone l'indique aussi.").apply { setPadding(0, dp(8), 0, dp(12)) })
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(4), 0, dp(12)) }
        open = button("Ouvrir les réglages d'accessibilité") { openSettings() }
        row.addView(open)
        row.addView(button("Fermer") { finish() })
        box.addView(row)
        box.addView(text(20f, Color.WHITE, "Ce que fait le service, et ce qu'il ne fait pas"))
        box.addView(text(15f, Color.rgb(200, 200, 200),
            "• Il n'agit que sur ordre d'un téléphone qui a saisi le code de cette TV.\n" +
                "• Il ne lit rien de lui-même, ne garde rien, n'envoie rien sur Internet : il repère seulement l'élément sélectionné " +
                "quand le téléphone appuie sur une flèche, sur OK ou tape du texte.\n" +
                "• Il ne peut pas allumer ni éteindre la TV, changer de source HDMI ni piloter d'autres appareils (HDMI-CEC) : ces " +
                "fonctions sont réservées au système.\n" +
                "• Les touches MENU, chiffres, CH+/CH− et Info ne peuvent pas être envoyées aux autres applications.\n" +
                "• Certains lanceurs ou applications ignorent l'accessibilité : là, seuls Retour et Accueil fonctionnent.\n" +
                "• Vous pouvez le désactiver à tout moment dans les mêmes réglages.").apply { setPadding(0, dp(8), 0, 0) })
        setContentView(ScrollView(this).apply { setBackgroundColor(Color.rgb(18, 18, 18)); addView(box) })
        open.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        val on = RemoteHub.systemEnabled(this)
        val live = RemoteAccessibilityService.instance != null
        state.text = when {
            on && live -> "Mode « toute la TV » : actif ✓"
            on -> "Mode « toute la TV » : activé, en cours de démarrage…"
            else -> "Mode « toute la TV » : inactif (la télécommande pilote CastBridge seulement)"
        }
    }

    private fun openSettings() {
        for (action in listOf(Settings.ACTION_ACCESSIBILITY_SETTINGS, Settings.ACTION_SETTINGS)) {
            if (runCatching { startActivity(Intent(action)) }.isSuccess) return
        }
        state.text = "Cette TV n'a pas d'écran de réglages d'accessibilité accessible : le mode « toute la TV » n'y est pas possible."
    }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; setOnClickListener { onClick() }
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(12) }
    }

    private fun text(sp: Float, color: Int, s: String) = TextView(this).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp); setTextColor(color); gravity = Gravity.START; text = s
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
