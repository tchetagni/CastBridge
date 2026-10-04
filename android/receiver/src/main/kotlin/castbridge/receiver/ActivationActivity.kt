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
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import castbridge.core.owner.ActivationResult
import castbridge.core.owner.Channel
import castbridge.core.owner.FeatureGate
import castbridge.core.owner.GateState
import castbridge.core.owner.LockedTexts
import castbridge.core.owner.TrialPolicy
import castbridge.core.tunnel.TunnelTerms
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
    /** Upgrade mode (trial -> production): nothing is locked, BACK returns to the home, the trial keeps working until a valid production key is accepted. */
    private var upgrade = false
    private val poll = object : Runnable {
        override fun run() {
            if (done) return
            ActivationCenter.pending?.let { k ->
                if (input.text.toString().trim() != k) {
                    input.setText(k)
                    status.setTextColor(0xFF6FE0A0.toInt()); status.text = "Clé reçue du téléphone par Bluetooth. Appuyez sur « Valider la clé » pour activer."
                    validate.requestFocus()
                }
            }
            h.postDelayed(this, 2_000)
        }
    }
    /** USB lookup every 15 s while the screen is open (and at once when a volume is mounted): a key is read only once the terms of use are accepted on this TV. */
    private val scanTick = object : Runnable {
        override fun run() { if (done) return; scanNow(manual = false); h.postDelayed(this, SCAN_EVERY_MS) }
    }
    private val mountReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: android.content.Context, i: Intent) { h.removeCallbacks(scanSoon); h.postDelayed(scanSoon, 1_500) }       // the volume needs a moment to settle
    }
    private val scanSoon = Runnable { if (!done) scanNow(manual = false) }
    private var mountRegistered = false

    /** One lookup in a thread, then the report lines under the button. [manual] = the button: says « Recherche… » and refuses before the terms are accepted. */
    private fun scanNow(manual: Boolean) {
        if (!(if (manual) termsOk() else TunnelHub.termsAccepted(this))) {
            if (manual) { status.setTextColor(0xFFFF8A80.toInt()); status.text = TunnelTerms.MUST_ACCEPT; termsBox.requestFocus() }
            return
        }
        if (manual) { status.setTextColor(0xFFB8C0D6.toInt()); status.text = LockedTexts.SEARCHING; report.text = LockedTexts.SEARCHING }
        Thread {
            val res = ActivationCenter.scanFiles()
            val lines = ActivationCenter.lastReport
            h.post {
                if (isFinishing || done) return@post
                if (res == null) { if (lines.isNotEmpty() || manual) report.text = lines.joinToString("\n"); if (manual) { status.setTextColor(0xFFFF8A80.toInt()); status.text = "Aucune clé trouvée sur la clé USB." } }
                else {
                    report.text = lines.joinToString("\n")
                    status.setTextColor(0xFFB8C0D6.toInt()); status.text = LockedTexts.KEY_FOUND
                    if (res is ActivationResult.Accepted) done = true
                    h.postDelayed({ show(res, "la clé USB") }, 600)
                }
            }
        }.start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ActivationCenter.init(this)
        TvService.start(this)                       // the service (and the owner Bluetooth channel) must run while this screen is up: a locked TV starts nothing else
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        upgrade = intent.getBooleanExtra(EXTRA_UPGRADE, false) && ActivationCenter.trial()
        val state = ActivationCenter.state()
        fun tv(text: String, sp: Float, color: Int = Color.WHITE, bold: Boolean = false, mono: Boolean = false) = TextView(this).apply {
            this.text = text; textSize = sp; setTextColor(color); setPadding(0, 10, 0, 10)
            if (bold) setTypeface(typeface, Typeface.BOLD); if (mono) typeface = Typeface.MONOSPACE
        }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(90, 50, 90, 50); gravity = Gravity.CENTER_HORIZONTAL }
        col.addView(tv("CastBridge TV", 34f, 0xFFF5B027.toInt(), bold = true))
        col.addView(tv("Version ${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE})", 20f, 0xFFF5B027.toInt(), bold = true))
        btLine = tv("Bluetooth d'activation : …", 16f, 0xFF7B849C.toInt()); col.addView(btLine)
        if (upgrade) {
            col.addView(tv(TrialPolicy.UPGRADE_TITLE, 30f, 0xFF6FE0A0.toInt(), bold = true))
            col.addView(tv("Cette TV est en version d'essai.", 22f, 0xFFF5B027.toInt(), bold = true))
            col.addView(tv(TrialPolicy.MESSAGE, 18f, 0xFFB8C0D6.toInt()))
            col.addView(tv(ActivationCenter.badge().lines.firstOrNull().orEmpty(), 18f, 0xFFB8C0D6.toInt()))
            col.addView(tv(TrialPolicy.UPGRADE_EXPLAIN, 18f, 0xFFB8C0D6.toInt()))
            col.addView(Button(this).apply { text = "Retour à l'accueil (garder l'essai)"; textSize = 20f; setOnClickListener { goOn() } })
        } else {
            col.addView(tv(LockedTexts.NOTICE_TITLE, 22f, 0xFFB8C0D6.toInt(), bold = true))
            col.addView(tv(LockedTexts.NOTICE, 18f, 0xFFB8C0D6.toInt()))
        }
        if (state is GateState.Grace) col.addView(tv(LockedTexts.GRACE + "\nJusqu'au " + DateFormat.getDateInstance(DateFormat.LONG).format(Date(state.untilMs)) + ".", 18f, 0xFFF5B027.toInt()))
        // Terms of use (docs/CONDITIONS-ASSISTANCE-A-DISTANCE.md): accepted here, on this TV, before any key is taken (locked AND upgrade modes). Text: TunnelTerms (à valider par le propriétaire).
        col.addView(tv(TunnelTerms.TITLE + " (" + TunnelTerms.VERSION + ")", 22f, 0xFFF5B027.toInt(), bold = true))
        col.addView(tv(TunnelTerms.TEXT, 15f, 0xFFB8C0D6.toInt()))
        termsBox = CheckBox(this).apply {
            text = TunnelTerms.CHECKBOX; textSize = 20f; setTextColor(Color.WHITE)
            isChecked = TunnelHub.termsAccepted(this@ActivationActivity)
            setOnCheckedChangeListener { _, on ->
                if (on) { if (!TunnelHub.acceptTerms(this@ActivationActivity)) { isChecked = false; status.setTextColor(0xFFFF8A80.toInt()); status.text = "Impossible d'enregistrer l'acceptation : réessayez." } }
                else TunnelHub.withdrawTerms(this@ActivationActivity)
            }
        }
        col.addView(termsBox)
        col.addView(tv(LockedTexts.REQUEST, 22f, bold = true))
        col.addView(tv("Code d'appareil", 18f, 0xFFB8C0D6.toInt()))
        col.addView(tv(ActivationCenter.deviceCode, 54f, 0xFFF5B027.toInt(), bold = true, mono = true))
        if (upgrade) { col.addView(tv("Demande d'appareil complète (à donner à CastBridge) :", 16f, 0xFFB8C0D6.toInt())); col.addView(tv(ActivationCenter.requestText(), 13f, 0xFF7B849C.toInt(), mono = true)) }
        col.addView(tv(LockedTexts.WAYS, 18f, 0xFFB8C0D6.toInt()))
        LockedTexts.KEY_WAYS.forEach { col.addView(tv(it, 18f)) }
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
        validate = Button(this).apply { text = "Valider la clé"; textSize = 20f; setOnClickListener { enter() } }
        col.addView(validate)
        col.addView(Button(this).apply { text = "Chercher la clé sur la clé USB"; textSize = 20f; setOnClickListener { scanNow(manual = true) } })
        report = tv("", 15f, 0xFF7B849C.toInt()); col.addView(report)
        col.addView(Button(this).apply { text = "Rendre la TV visible pour le téléphone (Bluetooth)"; textSize = 20f; setOnClickListener { makeVisible() } })
        // Free contents (CC BY-SA): a ZIP written by the TV itself, usable without an activation key, offline (no HTTP route, no PIN)
        col.addView(tv("Archive ZIP des contenus sous licence CC BY-SA, utilisable sans clé d'activation", 16f, 0xFFB8C0D6.toInt()))
        freeButton = Button(this).apply { text = "Télécharger tous les contenus libres"; textSize = 20f; isFocusable = true; setOnClickListener { exportFree() } }
        col.addView(freeButton)
        freeStatus = tv("", 16f, 0xFFB8C0D6.toInt()); col.addView(freeStatus)
        if (state is GateState.Grace) col.addView(Button(this).apply { text = "Continuer sans activer pour l'instant"; textSize = 20f; setOnClickListener { goOn() } })
        setContentView(ScrollView(this).apply { setBackgroundColor(0xFF0A0F1E.toInt()); addView(col) })
    }

    companion object { const val EXTRA_UPGRADE = "upgrade"; const val SCAN_EVERY_MS = 15_000L }
    private lateinit var report: TextView
    private lateinit var freeButton: Button
    private lateinit var freeStatus: TextView
    private fun exportFree() {
        if (FreeContentExport.busy()) { freeStatus.text = "Export déjà en cours…"; return }
        freeButton.isEnabled = false; freeStatus.setTextColor(0xFFB8C0D6.toInt()); freeStatus.text = "Préparation de l'archive…"
        FreeContentExport.start(this, { r -> h.post(r) }, { pct -> freeStatus.text = "Écriture de l'archive : $pct %" }, { o ->
            freeButton.isEnabled = true
            when (o) {
                is FreeContentExport.Outcome.Success -> { freeStatus.setTextColor(0xFF6FE0A0.toInt()); freeStatus.text = o.message }
                is FreeContentExport.Outcome.Failure -> { freeStatus.setTextColor(0xFFFF8A80.toInt()); freeStatus.text = o.message }
            }
        })
    }
    private lateinit var btLine: TextView
    private lateinit var validate: Button
    private lateinit var termsBox: CheckBox
    private fun termsOk(): Boolean = termsBox.isChecked && TunnelHub.termsAccepted(this)
    private var askedVisible = false
    /** Makes the TV discoverable for 5 minutes (the system asks for a confirmation on the TV), so the owner's phone can find it and pair by itself. */
    private fun makeVisible() {
        val ad = (getSystemService(android.content.Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager)?.adapter ?: return
        if (!ad.isEnabled) { status.text = "Activez le Bluetooth de la TV dans ses réglages."; return }
        runCatching { startActivity(Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).putExtra(android.bluetooth.BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)) }
    }

    private fun enter() {
        val text = input.text.toString().trim()
        if (!termsOk()) { status.setTextColor(0xFFFF8A80.toInt()); status.text = TunnelTerms.MUST_ACCEPT; termsBox.requestFocus(); return }
        if (text.isEmpty()) { status.text = "Saisissez d'abord la clé."; return }
        Thread { val r = ActivationCenter.accept(Channel.MANUAL, text.toByteArray(Charsets.UTF_8)); h.post { show(r, "la saisie") } }.start()
    }

    private fun show(r: ActivationResult, from: String) {
        when (r) {
            is ActivationResult.Accepted -> {
                done = true
                status.setTextColor(0xFF6FE0A0.toInt()); status.text = if (upgrade && !ActivationCenter.trial()) "${TrialPolicy.FULL_VERSION} : clé de production acceptée (${ActivationCenter.label()}). Ouverture…" else if (upgrade) "Clé acceptée, mais ce n'est pas une clé de production : l'essai continue (${ActivationCenter.label()}). Ouverture…" else "Activée (${ActivationCenter.label()}). Ouverture…"
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

    private val btTick = object : Runnable { override fun run() { btLine.text = "Bluetooth d'activation : " + (TvService.running?.ownerStatus() ?: "service non démarré"); h.postDelayed(this, 2_000) } }
    override fun onResume() {
        super.onResume(); h.post(poll); h.post(btTick); h.post(scanTick)
        if (!mountRegistered) runCatching {
            val f = android.content.IntentFilter(Intent.ACTION_MEDIA_MOUNTED).apply { addDataScheme("file") }
            if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(mountReceiver, f, android.content.Context.RECEIVER_EXPORTED) else registerReceiver(mountReceiver, f)
            mountRegistered = true
        }
        // Bluetooth permissions: the lock screen comes BEFORE the player screen that normally asks for them, and without BLUETOOTH_ADVERTISE the TV opens none of its
        // Bluetooth services (pairing, remote control, activation). Asked here, then the services are (re)started.
        val wanted = buildList {
            if (android.os.Build.VERSION.SDK_INT >= 31) { add(android.Manifest.permission.BLUETOOTH_CONNECT); add(android.Manifest.permission.BLUETOOTH_ADVERTISE) }
            if (android.os.Build.VERSION.SDK_INT >= 33) add(android.Manifest.permission.POST_NOTIFICATIONS)
        }.filter { checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED }
        if (wanted.isNotEmpty()) runCatching { requestPermissions(wanted.toTypedArray(), 77) }
        else { TvService.running?.onActivationPermissions(); if (!askedVisible) { askedVisible = true; makeVisible() } }
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 77) { TvService.running?.onActivationPermissions(); if (!askedVisible) { askedVisible = true; makeVisible() } }
    }
    override fun onPause() {
        super.onPause(); h.removeCallbacks(poll); h.removeCallbacks(btTick); h.removeCallbacks(scanTick); h.removeCallbacks(scanSoon)
        if (mountRegistered) { runCatching { unregisterReceiver(mountReceiver) }; mountRegistered = false }
    }
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean =
        if (keyCode == KeyEvent.KEYCODE_BACK && ActivationCenter.state() is GateState.Locked) true else super.onKeyDown(keyCode, event)      // no way out while locked
}
