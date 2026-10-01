package castbridge.receiver

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import castbridge.core.owner.ActivationResult
import castbridge.core.owner.Channel
import castbridge.core.owner.FeatureGate
import castbridge.core.owner.GateState
import castbridge.core.owner.LockedTexts
import java.text.DateFormat
import java.util.Date

/**
 * The start screen of a TV that needs an activation (docs/TRIAL-EDITION.md): the usage notice, the device code in large type, how to send it, and the ways to enter the key
 * (USB file read every 2 seconds, or typed with the remote). Nothing else is reachable while the TV is locked ([castbridge.core.owner.Feature]).
 */
class ActivationActivity : Activity() {
    private val h = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private lateinit var input: EditText
    private var done = false
    private val poll = object : Runnable {
        override fun run() {
            if (done) return
            val r = Thread { val res = ActivationCenter.scanFiles(); h.post { if (res != null) show(res, "la clé USB") } }
            r.start(); h.postDelayed(this, 2_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ActivationCenter.init(this)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val state = ActivationCenter.state()
        fun tv(text: String, sp: Float, color: Int = Color.WHITE, bold: Boolean = false, mono: Boolean = false) = TextView(this).apply {
            this.text = text; textSize = sp; setTextColor(color); setPadding(0, 10, 0, 10)
            if (bold) setTypeface(typeface, Typeface.BOLD); if (mono) typeface = Typeface.MONOSPACE
        }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(90, 50, 90, 50); gravity = Gravity.CENTER_HORIZONTAL }
        col.addView(tv("CastBridge TV", 34f, 0xFFF5B027.toInt(), bold = true))
        col.addView(tv(LockedTexts.NOTICE_TITLE, 22f, 0xFFB8C0D6.toInt(), bold = true))
        col.addView(tv(LockedTexts.NOTICE, 18f, 0xFFB8C0D6.toInt()))
        if (state is GateState.Grace) col.addView(tv(LockedTexts.GRACE + "\nJusqu'au " + DateFormat.getDateInstance(DateFormat.LONG).format(Date(state.untilMs)) + ".", 18f, 0xFFF5B027.toInt()))
        col.addView(tv(LockedTexts.REQUEST, 22f, bold = true))
        col.addView(tv("Code d'appareil", 18f, 0xFFB8C0D6.toInt()))
        col.addView(tv(ActivationCenter.deviceCode, 54f, 0xFFF5B027.toInt(), bold = true, mono = true))
        col.addView(tv(LockedTexts.WAYS, 18f, 0xFFB8C0D6.toInt()))
        col.addView(tv("Recevoir la clé : copiez le fichier « activation » reçu dans le dossier Download/CastBridge d'une clé USB branchée sur la TV (lu automatiquement), ou saisissez la clé ci-dessous.", 18f))
        val where = tv("", 15f, 0xFF7B849C.toInt())
        col.addView(where)
        Thread { val w = ActivationCenter.exportRequest(); h.post { if (w.isNotEmpty()) where.text = "Demande d'appareil complète écrite dans : " + w.joinToString(" · ") { it.substringAfter("/storage/").substringAfter("emulated/0/").take(70) } } }.start()
        status = tv("En attente de la clé…", 20f, 0xFFB8C0D6.toInt(), bold = true)
        col.addView(status)
        input = EditText(this).apply {
            hint = "Saisissez ou collez la clé d'activation"; setHintTextColor(0xFF7B849C.toInt()); setTextColor(Color.WHITE); textSize = 18f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS; minLines = 2
        }
        col.addView(input, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        col.addView(Button(this).apply { text = "Valider la clé"; textSize = 20f; setOnClickListener { enter() } })
        col.addView(Button(this).apply { text = "Chercher la clé sur la clé USB"; textSize = 20f; setOnClickListener { Thread { val r = ActivationCenter.scanFiles(); h.post { if (r != null) show(r, "la clé USB") else status.text = "Aucun fichier « activation » trouvé sur la clé USB." } }.start() } })
        col.addView(tv("Plus simple : sur le téléphone, ouvrez CastBridge > « Activer la TV », collez la clé : le téléphone trouve cette TV par Bluetooth et l'envoie.", 18f, 0xFFB8C0D6.toInt()))
        col.addView(Button(this).apply { text = "Rendre la TV visible pour le téléphone (Bluetooth)"; textSize = 20f; setOnClickListener { makeVisible() } })
        if (state is GateState.Grace) col.addView(Button(this).apply { text = "Continuer sans activer pour l'instant"; textSize = 20f; setOnClickListener { goOn() } })
        setContentView(ScrollView(this).apply { setBackgroundColor(0xFF0A0F1E.toInt()); addView(col) })
    }

    private var askedVisible = false
    /** Makes the TV discoverable for 5 minutes (the system asks for a confirmation on the TV), so the owner's phone can find it and pair by itself. */
    private fun makeVisible() {
        val ad = (getSystemService(android.content.Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager)?.adapter ?: return
        if (!ad.isEnabled) { status.text = "Activez le Bluetooth de la TV dans ses réglages."; return }
        runCatching { startActivity(Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).putExtra(android.bluetooth.BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)) }
    }

    private fun enter() {
        val text = input.text.toString().trim()
        if (text.isEmpty()) { status.text = "Saisissez d'abord la clé."; return }
        Thread { val r = ActivationCenter.accept(Channel.MANUAL, text.toByteArray(Charsets.UTF_8)); h.post { show(r, "la saisie") } }.start()
    }

    private fun show(r: ActivationResult, from: String) {
        when (r) {
            is ActivationResult.Accepted -> {
                done = true
                status.setTextColor(0xFF6FE0A0.toInt()); status.text = "Activée (${ActivationCenter.label()}). Ouverture…"
                h.postDelayed({ goOn() }, 1_200)
            }
            is ActivationResult.Rejected -> { status.setTextColor(0xFFFF8A80.toInt()); status.text = "Clé refusée par $from : ${r.message}" + if (r.suspect) "\nElle est valide mais pas pour cette TV : vérifiez le code d'appareil donné." else "" }
        }
    }

    private fun goOn() {
        done = true
        TvService.start(this)                                                                    // the core starts now that the TV is allowed
        startActivity(Intent(this, PlayerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        finish()
    }

    override fun onResume() {
        super.onResume(); h.post(poll)
        // the owner's phone pushes the activation by Bluetooth: on recent Android that needs the permission, asked here because a locked TV asks nothing else
        if (android.os.Build.VERSION.SDK_INT >= 31 && checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            runCatching { requestPermissions(arrayOf(android.Manifest.permission.BLUETOOTH_CONNECT), 77) }
        else if (!askedVisible) { askedVisible = true; makeVisible() }
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 77) { TvService.running?.startOwnerChannel(); if (!askedVisible) { askedVisible = true; makeVisible() } }
    }
    override fun onPause() { super.onPause(); h.removeCallbacks(poll) }
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean =
        if (keyCode == KeyEvent.KEYCODE_BACK && ActivationCenter.state() is GateState.Locked) true else super.onKeyDown(keyCode, event)      // no way out while locked
}
