package castbridge.receiver

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.Build
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
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import castbridge.core.owner.ActivationResult
import castbridge.core.owner.Channel
import castbridge.core.owner.GateState
import castbridge.core.owner.LockedTexts
import castbridge.core.owner.TrialPolicy
import castbridge.core.tunnel.TunnelTerms
import castbridge.core.tv.WdCode
import castbridge.core.tv.activation.ActivationQr
import castbridge.core.tv.activation.ActivationScreenPlan
import castbridge.core.tv.activation.KeyScan
import castbridge.core.tv.activation.LineTone
import castbridge.core.tv.activation.LockedWifiTexts
import castbridge.core.tv.activation.PickInput
import castbridge.core.tv.activation.PickResult
import castbridge.core.tv.activation.PickerPlan
import castbridge.core.tv.activation.UsbActivationBanner
import java.io.File
import java.text.DateFormat
import java.util.Date

/**
 * The start screen of a TV that needs an activation (docs/TRIAL-EDITION.md, docs/TV-ACTIVATION-CLE-USB.md): GUIDED. The ways to activate are numbered in the order the TV detects
 * ([ActivationScreenPlan], pure and tested), one status line each, large type, five keys (up, down, left, right, OK) are enough:
 *  - the phone: the connection code in very large type and a QR of the TV's own Wi-Fi Direct group (the group exists only while this screen is open: [TvService.startActivationGroup]);
 *  - the USB key: a banner at the top « Clé USB : activation trouvée pour cette TV › Activer » (OK installs it) or the exact reason, read at plug-in, at the opening of this screen and on the
 *    « Chercher » button ([UsbActivationWatch]), never silently every 15 s;
 *  - typing or pasting the key with the remote: the LAST RESORT, at the bottom.
 * The terms of use are shown in full until they are accepted (nothing is read before), then fold into one line. Nothing else is reachable while the TV is locked ([castbridge.core.owner.Feature]).
 */
class ActivationActivity : Activity() {
    private val h = Handler(Looper.getMainLooper())
    /** The RESULT line: « En attente de la clé… », « Activée… », a refusal, the outcome of a manual search. */
    private lateinit var status: TextView
    private lateinit var input: EditText
    private lateinit var validate: Button
    private lateinit var termsBox: CheckBox
    private lateinit var termsBody: LinearLayout
    private lateinit var termsReread: Button
    private lateinit var btLine: TextView
    private lateinit var report: TextView
    private lateinit var dropView: TextView
    private lateinit var wifiView: TextView
    private lateinit var bannerButton: Button
    private lateinit var bannerText: TextView
    private lateinit var lanesBox: LinearLayout
    private lateinit var codeView: TextView
    private lateinit var instructionView: TextView
    private lateinit var qrHolder: FrameLayout
    private lateinit var retryGroup: Button
    private lateinit var freeButton: Button
    private lateinit var freeStatus: TextView
    private val laneBoxes = HashMap<ActivationScreenPlan.Lane, LinearLayout>()
    private val laneHeads = HashMap<ActivationScreenPlan.Lane, TextView>()
    private val laneStatuses = HashMap<ActivationScreenPlan.Lane, TextView>()
    private var shownOrder: List<ActivationScreenPlan.Lane> = emptyList()
    private var qrFor: String? = null
    private var done = false
    /** Upgrade mode (trial -> production): nothing is locked, BACK returns to the home, the trial keeps working until a valid production key is accepted. */
    private var upgrade = false
    private var termsExpanded = false
    private var manualSearch = false
    private var groupAsked = false
    private var askedP2p = false
    /** The Wi-Fi Direct permission dialog is up: the group waits for the answer (no « permission » line while the person is answering). */
    private var p2pDialog = false
    private var lastUsbState = UsbActivationBanner.State.IDLE

    private val poll = object : Runnable {
        override fun run() {
            if (done) return
            // a key sent by the phone over the Wi-Fi was verified and installed by the locked TV's only route: open the TV
            ActivationCenter.takeWifiAccepted()?.let { r -> show(r, "le Wi-Fi"); return }
            // the service may not have been up yet when the screen opened: ask for the activation group as soon as it is
            if (!groupAsked && !upgrade && !p2pDialog) TvService.running?.let { it.activationScreenResumed(); groupAsked = true }
            ActivationCenter.pending?.let { k ->
                if (input.text.toString().trim() != k) {
                    input.setText(k)
                    status.setTextColor(GREEN); status.text = "Clé reçue du téléphone par Bluetooth. Appuyez sur « Valider la clé » pour activer."
                    validate.requestFocus()
                }
            }
            refresh()
            h.postDelayed(this, 2_000)
        }
    }

    /** The USB lookup answered (plug-in, opening, button): the banner, the report and, for a manual search, the result line. */
    private val usbListener: () -> Unit = { if (!isFinishing && !done) onUsbChanged() }

    private fun onUsbChanged() {
        val v = UsbActivationWatch.view
        if (v.state != UsbActivationBanner.State.SEARCHING) report.text = ActivationCenter.lastReport.joinToString("\n")
        if (manualSearch && v.state != UsbActivationBanner.State.SEARCHING && v.state != UsbActivationBanner.State.IDLE) {
            manualSearch = false
            status.setTextColor(color(v.tone))
            status.text = if (v.canActivate) "Clé trouvée : appuyez sur « Activer » dans le bandeau en haut." else v.text
        }
        refresh()
        // a key that verifies just appeared: the button takes the focus (unless someone is typing), so OK is enough
        if (v.canActivate && lastUsbState != UsbActivationBanner.State.FOUND && currentFocus !is EditText) bannerButton.requestFocus()
        lastUsbState = v.state
    }

    private fun tv(text: String, sp: Float, color: Int = Color.WHITE, bold: Boolean = false, mono: Boolean = false) = TextView(this).apply {
        this.text = text; textSize = sp; setTextColor(color); setPadding(0, 8, 0, 8)
        if (bold) setTypeface(typeface, Typeface.BOLD); if (mono) typeface = Typeface.MONOSPACE
    }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply { text = label; textSize = 22f; isAllCaps = false; setOnClickListener { onClick() } }

    private fun color(t: LineTone) = when (t) { LineTone.GOOD -> GREEN; LineTone.INFO -> GREY; LineTone.WARN -> AMBER }

    /** A banner that stays visible under the D-pad: a white frame when focused. */
    private fun bannerBackground(fill: Int) = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_focused), GradientDrawable().apply { setColor(fill); setStroke(8, Color.WHITE); cornerRadius = 16f })
        addState(intArrayOf(), GradientDrawable().apply { setColor(fill); setStroke(2, 0x66FFFFFF); cornerRadius = 16f })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ActivationCenter.init(this)
        TvService.start(this)                       // the service (and the owner Bluetooth channel) must run while this screen is up: a locked TV starts nothing else
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        upgrade = intent.getBooleanExtra(EXTRA_UPGRADE, false) && ActivationCenter.trial()
        val state = ActivationCenter.state()
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(90, 40, 90, 50); gravity = Gravity.CENTER_HORIZONTAL }
        col.addView(tv("CastBridge-TV", 34f, AMBER, bold = true))
        col.addView(tv("Version ${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE})", 18f, AMBER, bold = true))
        // the device code: what a key is made for (the phone reads it by itself on the locked route; an agent can still read it here)
        col.addView(tv("Code d'appareil : ${ActivationCenter.deviceCode}", 28f, AMBER, bold = true, mono = true))
        col.addView(tv(LockedTexts.REQUEST, 16f, GREY))
        if (upgrade) {
            col.addView(tv(TrialPolicy.UPGRADE_TITLE, 30f, GREEN, bold = true))
            col.addView(tv("Cette TV est en version d'essai.", 22f, AMBER, bold = true))
            col.addView(tv(TrialPolicy.MESSAGE, 18f, GREY))
            col.addView(tv(ActivationCenter.badge().lines.firstOrNull().orEmpty(), 18f, GREY))
            col.addView(tv(TrialPolicy.UPGRADE_EXPLAIN, 18f, GREY))
            col.addView(button("Retour à l'accueil (garder l'essai)") { goOn() })
        }
        if (state is GateState.Grace) col.addView(tv(LockedTexts.GRACE + "\nJusqu'au " + DateFormat.getDateInstance(DateFormat.LONG).format(Date(state.untilMs)) + ".", 18f, AMBER))
        // The USB banner (F5): a focusable « Activer » button when a key verifies for this TV, else the exact reason; hidden when there is nothing to say
        bannerButton = Button(this).apply {
            textSize = 24f; isAllCaps = false; setTextColor(Color.WHITE); background = bannerBackground(0xFF1F7A4C.toInt()); setPadding(30, 22, 30, 22)
            visibility = View.GONE; setOnClickListener { installFromUsb() }
        }
        col.addView(bannerButton, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = 14; bottomMargin = 6 })
        bannerText = tv("", 22f, AMBER, bold = true).apply { visibility = View.GONE }
        col.addView(bannerText)
        status = tv("En attente de la clé…", 22f, GREY, bold = true)
        col.addView(status)
        // Terms of use (docs/CONDITIONS-ASSISTANCE-A-DISTANCE.md): accepted here, on this TV, before any key is taken (locked AND upgrade modes). Shown in full until accepted, then one line. Text: TunnelTerms (à valider par le propriétaire).
        termsBody = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (!upgrade) { termsBody.addView(tv(LockedTexts.NOTICE_TITLE, 22f, GREY, bold = true)); termsBody.addView(tv(LockedTexts.NOTICE, 18f, GREY)) }
        termsBody.addView(tv(TunnelTerms.TITLE + " (" + TunnelTerms.VERSION + ")", 22f, AMBER, bold = true))
        termsBody.addView(tv(TunnelTerms.TEXT, 15f, GREY))
        col.addView(termsBody)
        termsBox = CheckBox(this).apply {
            text = TunnelTerms.CHECKBOX; textSize = 22f; setTextColor(Color.WHITE)
            isChecked = TunnelHub.termsAccepted(this@ActivationActivity)
            setOnCheckedChangeListener { _, on ->
                if (on) {
                    if (!TunnelHub.acceptTerms(this@ActivationActivity)) { isChecked = false; status.setTextColor(RED); status.text = "Impossible d'enregistrer l'acceptation : réessayez."; return@setOnCheckedChangeListener }
                    termsExpanded = false
                    status.setTextColor(GREY); status.text = "En attente de la clé…"
                } else TunnelHub.withdrawTerms(this@ActivationActivity)
                applyTerms()
                UsbActivationWatch.search(this@ActivationActivity, UsbActivationWatch.Trigger.TERMS)       // a key may already be plugged in: read it now (or say why it is not read)
            }
        }
        termsReread = button("Relire les conditions") { termsExpanded = !termsExpanded; applyTerms() }
        col.addView(LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; addView(termsBox); addView(termsReread) })
        applyTerms()
        // The numbered ways, in the order the TV detects (ActivationScreenPlan)
        lanesBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        for (lane in ActivationScreenPlan.Lane.values()) {
            val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 22, 0, 22) }
            val head = tv("", 28f, Color.WHITE, bold = true)
            val st = tv("", 20f, GREY)
            laneHeads[lane] = head; laneStatuses[lane] = st
            box.addView(head)
            when (lane) {
                ActivationScreenPlan.Lane.PHONE -> buildPhoneLane(box, st)
                ActivationScreenPlan.Lane.USB -> buildUsbLane(box, st)
                ActivationScreenPlan.Lane.TYPED -> buildTypedLane(box, st)
            }
            laneBoxes[lane] = box
        }
        col.addView(lanesBox)
        refresh()
        // What the agent needs, and what is free
        runCatching { castbridge.receiver.wallet.WalletHub.installSigner()?.fingerprintText() }.getOrNull()?.let { col.addView(tv("Empreinte de la clé d'installation : " + it, 16f, DIM, mono = true)) }
        if (upgrade) { col.addView(tv("Demande d'appareil complète (à donner à CastBridge) :", 16f, GREY)); col.addView(tv(ActivationCenter.requestText(), 13f, DIM, mono = true)) }
        col.addView(tv(LockedTexts.WAYS, 16f, GREY))
        val where = tv("", 15f, DIM)
        col.addView(where)
        Thread { val w = ActivationCenter.exportRequest(); h.post { if (w.isNotEmpty()) where.text = "Demande d'appareil complète écrite dans : " + w.joinToString(" · ") { it.substringAfter("/storage/").substringAfter("emulated/0/").take(70) } } }.start()
        // Free contents (CC BY-SA): a ZIP written by the TV itself, usable without an activation key, offline (no HTTP route, no PIN)
        col.addView(tv("Archive ZIP des contenus sous licence CC BY-SA, utilisable sans clé d'activation", 16f, GREY))
        freeButton = button("Télécharger tous les contenus libres") { exportFree() }.apply { isFocusable = true }
        col.addView(freeButton)
        freeStatus = tv("", 16f, GREY); col.addView(freeStatus)
        if (state is GateState.Grace) col.addView(button("Continuer sans activer pour l'instant") { goOn() })
        setContentView(ScrollView(this).apply { setBackgroundColor(BG); addView(col) })
    }

    // ---- the terms: full until accepted, then one line (and « Relire ») ----
    private fun termsOk(): Boolean = termsBox.isChecked && TunnelHub.termsAccepted(this)

    private fun applyTerms() {
        val ok = termsOk()
        termsBody.visibility = if (!ok || termsExpanded) View.VISIBLE else View.GONE
        termsReread.visibility = if (ok) View.VISIBLE else View.GONE
        termsReread.text = if (termsExpanded) "Masquer les conditions" else "Relire les conditions"
    }

    // ---- way 1: the phone (connection code, QR of the TV's group, Bluetooth) ----
    private fun buildPhoneLane(box: LinearLayout, st: TextView) {
        codeView = tv("", 72f, AMBER, bold = true, mono = true).apply { visibility = View.GONE }
        instructionView = tv("", 24f, Color.WHITE)
        qrHolder = FrameLayout(this).apply { visibility = View.GONE }
        val text = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; addView(codeView); addView(instructionView) }
        box.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(qrHolder, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { rightMargin = 40 })
            addView(text, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        })
        box.addView(st)
        // the line older phones read (« Activer la TV » asks for this code and compares this address)
        wifiView = tv("", 16f, DIM).apply { visibility = View.GONE }; box.addView(wifiView)
        btLine = tv("Bluetooth d'activation : …", 16f, DIM); box.addView(btLine)
        box.addView(button("Rendre la TV visible pour le téléphone (Bluetooth)") { makeVisible() })
        retryGroup = button("Réessayer le réseau direct") { askP2pPermission(); TvService.running?.startActivationGroup(); refresh() }.apply { visibility = View.GONE }
        box.addView(retryGroup)
    }

    private fun refreshPhoneLane(info: Pair<String, List<String>>?, group: ActivationScreenPlan.Group) {
        if (info == null) {
            codeView.visibility = View.GONE; wifiView.visibility = View.GONE; qrHolder.visibility = View.GONE; retryGroup.visibility = View.GONE
            instructionView.text = "Sur votre téléphone, ouvrez CastBridge › Activer la TV."
            return
        }
        val code = info.first
        val qrOk = group is ActivationScreenPlan.Group.Ready && WdCode.isValid(code)
        codeView.visibility = View.VISIBLE; codeView.text = ActivationScreenPlan.groupedCode(code)
        instructionView.text = ActivationScreenPlan.phoneInstruction(code, qrShown = qrOk)
        if (qrOk) {
            if (qrFor != code) { qrHolder.removeAllViews(); qrHolder.addView(ActivationQrView(this, ActivationQr.encode(code))); qrFor = code }
            qrHolder.visibility = View.VISIBLE
        } else qrHolder.visibility = View.GONE
        wifiView.visibility = View.VISIBLE; wifiView.text = LockedWifiTexts.line(code, info.second, groupReady = group is ActivationScreenPlan.Group.Ready)
        retryGroup.visibility = if (group is ActivationScreenPlan.Group.Failed) View.VISIBLE else View.GONE
    }

    // ---- way 2: the USB key ----
    private fun buildUsbLane(box: LinearLayout, st: TextView) {
        box.addView(st)
        box.addView(button("Chercher la clé sur la clé USB") { searchUsbByButton() })
        report = tv("", 15f, DIM); box.addView(report)
        dropView = tv("", 16f, AMBER); box.addView(dropView); refreshDrop()
        box.addView(button("Choisir le fichier d'activation (explorateur)") { pickBuiltIn() })
        if (PickerPlan.Chooser.SYSTEM in PickerPlan.choosers(systemPickerIntent().resolveActivity(packageManager) != null))
            box.addView(button("Explorateur du système") { pickSystem() })
    }

    private fun searchUsbByButton() {
        if (!termsOk()) { status.setTextColor(RED); status.text = TunnelTerms.MUST_ACCEPT; termsBox.requestFocus(); return }
        manualSearch = true
        status.setTextColor(GREY); status.text = LockedTexts.SEARCHING; report.text = LockedTexts.SEARCHING
        UsbActivationWatch.search(this, UsbActivationWatch.Trigger.BUTTON)
    }

    /** The banner at the top: the « Activer » button when a key verifies for this TV, else the exact reason; nothing while there is no key (the key's way says so) or the terms are pending (so does the key's way). */
    private fun refreshBanner(v: UsbActivationBanner.View) {
        val quiet = v.state == UsbActivationBanner.State.IDLE || v.state == UsbActivationBanner.State.NO_KEY || v.state == UsbActivationBanner.State.TERMS_PENDING
        bannerButton.visibility = if (v.canActivate) View.VISIBLE else View.GONE
        if (v.canActivate) bannerButton.text = v.text
        bannerText.visibility = if (!quiet && !v.canActivate) View.VISIBLE else View.GONE
        if (bannerText.visibility == View.VISIBLE) { bannerText.text = v.text; bannerText.setTextColor(color(v.tone)) }
    }

    /** The « Activer » button: looks again and installs the first key that verifies for this TV (the same verifier as a pasted key). */
    private fun installFromUsb() {
        if (!termsOk()) { status.setTextColor(RED); status.text = TunnelTerms.MUST_ACCEPT; termsBox.requestFocus(); return }
        status.setTextColor(GREY); status.text = LockedTexts.KEY_FOUND
        UsbActivationWatch.install(this) { r ->
            if (isFinishing || done) return@install
            when (r) {
                null -> { status.setTextColor(RED); status.text = "Une recherche est déjà en cours : appuyez de nouveau sur « Activer »." }
                else -> { show(r, "la clé USB"); if (r is ActivationResult.Rejected) UsbActivationWatch.search(this, UsbActivationWatch.Trigger.BUTTON) }
            }
        }
    }

    // ---- way 3: typing or pasting the key (the last resort) ----
    private fun buildTypedLane(box: LinearLayout, st: TextView) {
        box.addView(st)
        input = EditText(this).apply {
            hint = "Saisissez ou collez la clé d'activation"; setHintTextColor(DIM); setTextColor(Color.WHITE); textSize = 20f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS; minLines = 2
        }
        box.addView(input, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        validate = button("Valider la clé") { enter() }
        box.addView(validate)
    }

    // ---- the guided order: recomputed from what the TV detects ----
    private fun refresh() {
        if (!::lanesBox.isInitialized) return
        val svc = TvService.running
        val info = svc?.lockedWifiInfo()
        val group = if (svc != null && info != null && !upgrade) svc.activationGroupState() else ActivationScreenPlan.Group.NotTried
        val lan = info?.second ?: listOfNotNull(TvService.localIp())
        val facts = ActivationScreenPlan.Facts(phoneLinked = svc != null && info != null && svc.activationPhoneLinked(), usb = UsbActivationWatch.view, group = group, lanIps = lan)
        val plan = ActivationScreenPlan.plan(facts)
        val order = plan.map { it.lane }
        if (order != shownOrder) placeLanes(order)
        for (lv in plan) {
            laneHeads[lv.lane]?.text = "${lv.number} · ${lv.title}"
            laneStatuses[lv.lane]?.apply { text = lv.status; setTextColor(color(lv.tone)) }
        }
        refreshBanner(facts.usb)
        refreshPhoneLane(info, group)
        btLine.text = "Bluetooth d'activation : " + (svc?.ownerStatus() ?: "service non démarré")
    }

    private fun placeLanes(order: List<ActivationScreenPlan.Lane>) {
        val focused = currentFocus
        lanesBox.removeAllViews()
        for (lane in order) laneBoxes[lane]?.let { lanesBox.addView(it) }
        shownOrder = order
        focused?.takeIf { it.isFocusable }?.requestFocus()                 // the same button keeps the focus when the ways change places
    }

    private fun askP2pPermission() {
        val p = WifiDirectGroup.permissionFor(Build.VERSION.SDK_INT) ?: return
        if (checkSelfPermission(p) != android.content.pm.PackageManager.PERMISSION_GRANTED) runCatching { requestPermissions(arrayOf(p), 78) }
    }

    /** The exact readable drop folder of every volume and the file names the app sees there (thread: lists folders). */
    private fun refreshDrop() {
        Thread { val l = castbridge.core.tv.activation.DropFolders.lines(ActivationCenter.dropFolders()); h.post { if (!isFinishing) dropView.text = l.joinToString("\n") } }.start()
    }

    // ---- choosing the file by hand: the built-in explorer first (a poor box has no system picker), the system one if it exists; the file name does not matter ----
    private fun systemPickerIntent() = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*").also { i ->
        PickerPlan.initialUri(ActivationCenter.volumeIds().firstOrNull())?.let { i.putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI, Uri.parse(it)) }
    }
    private fun pickBuiltIn() {
        if (!termsOk()) { status.setTextColor(RED); status.text = TunnelTerms.MUST_ACCEPT; termsBox.requestFocus(); return }
        startActivityForResult(Intent(this, FilePickActivity::class.java).putExtra(FilePickActivity.EXTRA_SYSTEM_AVAILABLE, systemPickerIntent().resolveActivity(packageManager) != null), REQ_BUILT_IN)
    }
    private fun pickSystem() {
        if (!termsOk()) { status.setTextColor(RED); status.text = TunnelTerms.MUST_ACCEPT; termsBox.requestFocus(); return }
        try { startActivityForResult(systemPickerIntent(), REQ_SYSTEM) }
        catch (e: android.content.ActivityNotFoundException) { status.setTextColor(RED); status.text = PickerPlan.systemPickerMissing(); UsbActivationWatch.search(this, UsbActivationWatch.Trigger.BUTTON) }       // no silent failure: say it, then the lookup
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_BUILT_IN && requestCode != REQ_SYSTEM) return
        if (resultCode != RESULT_OK || data == null) { handlePick(PickInput.Cancelled); return }
        // the built-in explorer's « Ouvrir l'explorateur du système » row (Android hides files from the app): open it now
        if (requestCode == REQ_BUILT_IN && data.getBooleanExtra(FilePickActivity.EXTRA_SYSTEM, false)) { pickSystem(); return }
        status.setTextColor(GREY); status.text = LockedTexts.SEARCHING
        Thread {
            val input: PickInput = try {
                val stream = if (requestCode == REQ_BUILT_IN) data.getStringExtra(FilePickActivity.EXTRA_PATH)?.let { java.io.FileInputStream(File(it)) }
                             else data.data?.let { contentResolver.openInputStream(it) }              // read once, the URI is not kept (no persistable permission)
                // MAX + 1 bytes at most: a bigger file is said « trop gros » (never read whole)
                if (stream == null) PickInput.Nothing else stream.use { PickInput.Bytes(castbridge.core.util.BoundedRead.readUpTo(it, PickerPlan.MAX_BYTES + 1)) }
            } catch (e: java.io.IOException) { PickInput.Unreadable } catch (e: SecurityException) { PickInput.Unreadable }
            h.post { handlePick(input) }
        }.start()
    }
    private fun handlePick(input: PickInput) {
        when (val d = PickerPlan.decide(input)) {
            is PickResult.Keys -> {
                status.setTextColor(GREY); status.text = d.message
                // every key-shaped piece of the file, in reading order, through the same verification as a pasted key; the first one valid for THIS TV is installed
                Thread {
                    val results = ArrayList<ActivationResult>()
                    val o = KeyScan.pick(d.candidates) { c -> ActivationCenter.accept(Channel.MANUAL, c.toByteArray(Charsets.UTF_8)).also { results += it }.let(ActivationCenter::verdictOf) }
                    h.post {
                        val r = results.getOrNull(o.index)
                        if (r == null) { status.setTextColor(RED); status.text = KeyScan.NO_KEY; return@post }
                        show(r, "le fichier choisi")
                        KeyScan.summary(o)?.let { if (r !is ActivationResult.Accepted) status.text = it + "\n" + status.text }
                    }
                }.start()
            }
            else -> { status.setTextColor(if (d is PickResult.Cancelled || d is PickResult.Nothing) GREY else RED); status.text = d.message }
        }
    }
    private fun exportFree() {
        if (FreeContentExport.busy()) { freeStatus.text = "Export déjà en cours…"; return }
        freeButton.isEnabled = false; freeStatus.setTextColor(GREY); freeStatus.text = "Préparation de l'archive…"
        FreeContentExport.start(this, { r -> h.post(r) }, { pct -> freeStatus.text = "Écriture de l'archive : $pct %" }, { o ->
            freeButton.isEnabled = true
            when (o) {
                is FreeContentExport.Outcome.Success -> { freeStatus.setTextColor(GREEN); freeStatus.text = o.message }
                is FreeContentExport.Outcome.Failure -> { freeStatus.setTextColor(RED); freeStatus.text = o.message }
            }
        })
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
        if (!termsOk()) { status.setTextColor(RED); status.text = TunnelTerms.MUST_ACCEPT; termsBox.requestFocus(); return }
        if (text.isEmpty()) { status.text = "Saisissez d'abord la clé."; return }
        Thread { val r = ActivationCenter.accept(Channel.MANUAL, text.toByteArray(Charsets.UTF_8)); h.post { show(r, "la saisie") } }.start()
    }

    private fun show(r: ActivationResult, from: String) {
        when (r) {
            is ActivationResult.Accepted -> {
                done = true
                // the TV is activated: the activation group is given back at once and the USB banner is cleared
                TvService.running?.stopActivationGroup()
                UsbActivationWatch.reset()
                status.setTextColor(GREEN); status.text = if (upgrade && !ActivationCenter.trial()) "${TrialPolicy.FULL_VERSION} : clé de production acceptée (${ActivationCenter.label()}). Ouverture…" else if (upgrade) "Clé acceptée, mais ce n'est pas une clé de production : l'essai continue (${ActivationCenter.label()}). Ouverture…" else "Activée (${ActivationCenter.label()}). Ouverture…"
                h.postDelayed({ goOn() }, 1_200)
            }
            is ActivationResult.Rejected -> { status.setTextColor(RED); status.text = "Clé refusée par $from : ${r.message}" + if (r.suspect) "\nElle est valide mais pas pour cette TV : vérifiez le code d'appareil donné." else "" }
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
        super.onResume(); h.post(poll); h.post(btTick)
        UsbActivationWatch.addListener(usbListener)
        // the screen opens (or comes back): the USB key is looked at once (the terms must be accepted first: then the key's way says so)
        if (!done) UsbActivationWatch.search(this, UsbActivationWatch.Trigger.OPEN)
        if (!termsOk()) termsBox.requestFocus()
        // The Wi-Fi Direct permission (« Appareils à proximité », or the location below Android 13) is asked ONCE per opening: without it the TV cannot host its network, and says so.
        val p2p = if (upgrade) null else WifiDirectGroup.permissionFor(Build.VERSION.SDK_INT)
        val askP2p = p2p != null && !askedP2p && checkSelfPermission(p2p) != android.content.pm.PackageManager.PERMISSION_GRANTED
        // the TV hosts its own Wi-Fi Direct network while this screen is up (a locked TV only: an upgrading trial TV runs its full server); after the permission dialog when one is needed
        if (!upgrade && !askP2p) TvService.running?.let { it.activationScreenResumed(); groupAsked = true }
        // Bluetooth permissions: the lock screen comes BEFORE the player screen that normally asks for them, and without BLUETOOTH_ADVERTISE the TV opens none of its
        // Bluetooth services (pairing, remote control, activation). Asked here, then the services are (re)started.
        val wanted = buildList {
            if (Build.VERSION.SDK_INT >= 31) { add(android.Manifest.permission.BLUETOOTH_CONNECT); add(android.Manifest.permission.BLUETOOTH_ADVERTISE) }
            if (Build.VERSION.SDK_INT >= 33) add(android.Manifest.permission.POST_NOTIFICATIONS)
            if (askP2p && p2p != null) add(p2p)
        }.filter { checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED }
        if (!upgrade) askedP2p = true
        p2pDialog = askP2p && wanted.isNotEmpty()
        if (wanted.isNotEmpty()) runCatching { requestPermissions(wanted.toTypedArray(), 77) }.onFailure { p2pDialog = false }
        else { TvService.running?.onActivationPermissions(); if (!askedVisible) { askedVisible = true; makeVisible() } }
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 77) {
            p2pDialog = false
            TvService.running?.onActivationPermissions()
            if (!upgrade) TvService.running?.let { it.activationScreenResumed(); groupAsked = true }            // the group, now that the permission is answered (granted or not: the line says which)
            if (!askedVisible) { askedVisible = true; makeVisible() }
        } else if (requestCode == 78 && !upgrade) TvService.running?.startActivationGroup()
    }
    override fun onPause() {
        super.onPause(); h.removeCallbacks(poll); h.removeCallbacks(btTick)
        UsbActivationWatch.removeListener(usbListener)
        groupAsked = false
        // given back at once when the screen closes for good, else after a short delay (a picker of its own, the Bluetooth dialog, a rotation)
        if (!upgrade) TvService.running?.activationScreenPaused(isFinishing || done)
    }
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean =
        if (keyCode == KeyEvent.KEYCODE_BACK && ActivationCenter.state() is GateState.Locked) true else super.onKeyDown(keyCode, event)      // no way out while locked

    companion object {
        const val EXTRA_UPGRADE = "upgrade"
        private const val REQ_BUILT_IN = 81
        private const val REQ_SYSTEM = 82
        private const val AMBER = 0xFFF5B027.toInt()
        private const val GREEN = 0xFF6FE0A0.toInt()
        private const val GREY = 0xFFB8C0D6.toInt()
        private const val DIM = 0xFF7B849C.toInt()
        private const val RED = 0xFFFF8A80.toInt()
        private const val BG = 0xFF0A0F1E.toInt()
    }
}
