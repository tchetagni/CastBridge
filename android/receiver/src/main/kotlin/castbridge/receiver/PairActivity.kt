package castbridge.receiver

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import castbridge.core.trust.PairingSession
import castbridge.core.trust.TrustedPhone
import java.text.DateFormat
import java.util.Date

/**
 * « Ajouter un téléphone »: the only place where a phone becomes trusted.
 *
 * Opening it starts the pairing window (2 minutes) and asks Android to make the TV visible for the same time (Android shows its
 * own confirmation once: OK on the remote). The phone then pairs through Android (numeric comparison on both screens); when it
 * asks to be trusted this screen shows « Autoriser <téléphone> à piloter cette TV ? »: only the owner's OK on the remote
 * counts, and "Refuser" is the pre-selected button. Leaving the screen closes the window. The list of trusted phones is here too,
 * each with « Retirer », plus « Oublier tous les téléphones ». Colours and spacing follow branding/design-tokens.json (dark theme);
 * sizes are in dp so 1280x720 and 1920x1080 lay out the same, scrolling rather than overlapping when the list is long.
 */
class PairActivity : Activity() {
    private object C {
        const val BG = 0xFF0A0F1E.toInt(); const val SURFACE = 0xFF151D37.toInt(); const val SURFACE_HIGH = 0xFF1B2542.toInt()
        const val OUTLINE = 0xFF2A3550.toInt(); const val TEXT = 0xFFF4F6FB.toInt(); const val TEXT_MID = 0xFFB7C0D4.toInt()
        const val PRIMARY = 0xFFF5B025.toInt(); const val ON_PRIMARY = 0xFF171204.toInt(); const val FOCUS = 0xFFFFE1A6.toInt()
        const val SUCCESS = 0xFF35C08A.toInt(); const val ERROR = 0xFFFF6B6B.toInt()
    }

    private val main = Handler(Looper.getMainLooper())
    private lateinit var tvName: TextView
    private lateinit var countdown: TextView
    private lateinit var status: TextView
    private lateinit var list: LinearLayout
    private lateinit var listTitle: TextView
    private lateinit var visibleBtn: TextView
    private lateinit var btSettingsBtn: TextView
    private lateinit var forgetAll: TextView
    private lateinit var phonesBtn: TextView
    private var dialog: AlertDialog? = null
    private var message: String? = null
    private var listSig = ""
    private var bound: TvService? = null
    /** The window is opened once per visit, as soon as the service is there. */
    private var opened = false

    private val onPairing: (PairingSession.State) -> Unit = { main.post { refresh() } }
    private val onTrust: () -> Unit = { main.post { refresh() } }
    /** A ninth phone asked while the TV has 8: the owner chooses which one to remove, on the « Téléphones synchronisés » screen in replacement mode. */
    private val onCapacity: (castbridge.core.trust.PairCapacityFlow.Event) -> Unit = { e ->
        if (e.kind == castbridge.core.trust.PairCapacityFlow.Kind.REQUESTED) main.post { PhonesActivity.open(this, replace = true) }
    }
    private val closeWindow = Runnable { bound?.pairing?.close() }
    private val tick = object : Runnable { override fun run() { refresh(); main.postDelayed(this, 500) } }

    private fun dp(v: Int) = TvStyle.dp(this, v)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.setBackgroundColor(C.BG)
        setContentView(build())
    }

    private fun label(text: String, sp: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        this.text = text; textSize = sp; setTextColor(color); if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(16), dp(22), dp(16))
        background = GradientDrawable().apply { cornerRadius = dp(16).toFloat(); setColor(C.SURFACE); setStroke(dp(1), C.OUTLINE) }
    }

    /** A D-pad button: clearly different when focused (focus ring colour of the charter), 48 dp high at least. */
    private fun button(text: String, primary: Boolean = false, danger: Boolean = false, onClick: () -> Unit) = TextView(this).apply {
        this.text = text; textSize = 18f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
        setTextColor(if (primary) C.ON_PRIMARY else if (danger) C.ERROR else C.TEXT)
        minimumHeight = dp(48); setPadding(dp(14), dp(8), dp(14), dp(8)); isFocusable = true; isClickable = true; maxLines = 2
        fun shape(fill: Int, stroke: Int, w: Int) = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(fill); setStroke(dp(w), stroke) }
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), shape(if (primary) C.PRIMARY else C.SURFACE_HIGH, C.FOCUS, 3))
            addState(intArrayOf(), shape(if (primary) C.PRIMARY else C.SURFACE_HIGH, if (primary) C.PRIMARY else C.OUTLINE, 1))
        }
        setOnClickListener { onClick() }
    }

    private fun build(): View {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(40), dp(20), dp(40), dp(18)) }
        root.addView(label("Ajouter un téléphone", 30f, C.TEXT, true))

        val cols = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; weightSum = 2f }
        root.addView(cols, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(12) })

        // left: the TV's name, what to do, the countdown (scrolls on a very small screen, never overlaps)
        val left = card()
        val leftBody = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        leftBody.addView(label("Nom de cette TV", 16f, C.TEXT_MID))
        tvName = label("…", 34f, C.PRIMARY, true).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END }
        leftBody.addView(tvName, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2) })
        leftBody.addView(label("Sur votre téléphone, ouvrez CastBridge et touchez « Ajouter ma TV ».", 20f, C.TEXT).apply { setLineSpacing(0f, 1.1f) },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
        countdown = label("", 26f, C.SUCCESS, true)
        leftBody.addView(countdown, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
        status = label("", 17f, C.TEXT_MID)
        leftBody.addView(status, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        left.addView(ScrollView(this).apply { isFillViewport = false; isFocusable = false; addView(leftBody, ViewGroup.LayoutParams(-1, -2)) }, LinearLayout.LayoutParams(-1, 0, 1f))
        cols.addView(left, LinearLayout.LayoutParams(0, -1, 1f).apply { rightMargin = dp(10) })

        // right: trusted phones
        val right = card()
        listTitle = label("Téléphones de confiance", 24f, C.TEXT, true)
        right.addView(listTitle)
        right.addView(label("Ils pilotent la TV sans code. « Retirer » leur enlève l'accès tout de suite.", 15f, C.TEXT_MID),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2); bottomMargin = dp(6) })
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        right.addView(ScrollView(this).apply { isFillViewport = false; isFocusable = false; addView(list, ViewGroup.LayoutParams(-1, -2)) }, LinearLayout.LayoutParams(-1, 0, 1f))
        cols.addView(right, LinearLayout.LayoutParams(0, -1, 1f).apply { leftMargin = dp(10) })

        // bottom bar: the actions, side by side (each at least 48 dp high, focus ring visible)
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        visibleBtn = button("Rendre visible 2 minutes", primary = true) { openWindow() }
        btSettingsBtn = button("Réglages Bluetooth") { runCatching { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) } }
        forgetAll = button("Oublier tous les téléphones", danger = true) { confirmForgetAll() }
        val lp = { LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(10) } }
        bar.addView(visibleBtn, lp()); bar.addView(btSettingsBtn, lp())
        phonesBtn = button(castbridge.core.trust.PhonesTexts.TITLE) { PhonesActivity.open(this) }
        bar.addView(phonesBtn, lp()); bar.addView(forgetAll, lp())
        bar.addView(button("Fermer") { finish() }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(bar, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        return root
    }

    // ------------------------------------------------------------------ lifecycle

    override fun onStart() {
        super.onStart()
        main.removeCallbacks(closeWindow)
        bound = TvService.running ?: run { TvService.start(this); null }
        bound?.let { s -> s.pairing.addListener(onPairing); s.trust.addListener(onTrust); s.capacity.addListener(onCapacity) }
        ensurePermissions()
        opened = false
        main.post(tick)
    }

    override fun onStop() {
        main.removeCallbacks(tick)
        bound?.let { it.pairing.removeListener(onPairing); it.trust.removeListener(onTrust); it.capacity.removeListener(onCapacity) }
        // leaving the screen ends the window (after a short grace: the system's "visible" dialog and pairing dialogs briefly cover this screen)
        main.postDelayed(closeWindow, 8_000)
        super.onStop()
    }

    override fun onDestroy() {
        dialog?.dismiss()
        if (isFinishing) { main.removeCallbacks(closeWindow); bound?.pairing?.close() }
        super.onDestroy()
    }

    // ------------------------------------------------------------------ actions

    private fun hasBt() = Build.VERSION.SDK_INT < 31 || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun ensurePermissions() {
        if (Build.VERSION.SDK_INT < 31) return
        val need = listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE).filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (need.isNotEmpty()) runCatching { requestPermissions(need.toTypedArray(), REQ_PERMS) }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PERMS) { TvService.running?.onPermissionsReady(); if (hasBt()) openWindow() else message = "Le Bluetooth n'est pas autorisé pour CastBridge TV : autorisez-le dans les réglages de la TV (Applications)." }
    }

    @SuppressLint("MissingPermission")
    private fun openWindow() {
        val svc = bound ?: return
        message = null
        svc.pairing.open()
        svc.bt?.takeIf { hasBt() }?.start()
        if (hasBt() && adapter()?.isEnabled == true) runCatching {
            startActivityForResult(Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120), REQ_VISIBLE)
        }.onFailure { message = "Cette TV ne peut pas se rendre visible seule : le téléphone doit déjà être associé dans les réglages Bluetooth de la TV." }
        refresh()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION") super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_VISIBLE && resultCode == RESULT_CANCELED)
            message = "La TV n'est pas visible (refusé). Un téléphone déjà associé peut quand même se connecter."
        refresh()
    }

    @SuppressLint("MissingPermission")
    private fun adapter(): BluetoothAdapter? = getSystemService(BluetoothManager::class.java)?.adapter

    private fun confirmForgetAll() {
        val svc = bound ?: return
        if (svc.trust.list().isEmpty()) return
        confirm("Oublier tous les téléphones ?", "Aucun téléphone ne pourra plus utiliser la TV sans code. Vous pourrez les ajouter à nouveau.", "Tout oublier") {
            svc.trust.revokeAll(); message = "Tous les téléphones ont été retirés."; refresh()
        }
    }

    private fun confirmRemove(p: TrustedPhone) {
        val svc = bound ?: return
        confirm("Retirer ${p.name} ?", "Ce téléphone ne pourra plus piloter la TV sans le code. Il pourra être ajouté à nouveau.", "Retirer") {
            svc.removePhone(p.address); message = "${p.name} a été retiré."; refresh()
        }
    }

    /** Confirmation dialog where the harmless answer (Annuler) is the one selected. */
    private fun confirm(title: String, text: String, yes: String, action: () -> Unit) {
        AlertDialog.Builder(this).setTitle(title).setMessage(text)
            .setPositiveButton(yes) { _, _ -> action() }.setNegativeButton("Annuler", null).create().also { d ->
                d.setOnShowListener { d.getButton(AlertDialog.BUTTON_NEGATIVE)?.requestFocus() }
            }.show()
    }

    // ------------------------------------------------------------------ rendering

    private fun mmss(sec: Long) = "%d:%02d".format(sec / 60, sec % 60)

    private fun refresh() {
        val svc = bound ?: TvService.running.also { bound = it; it?.let { s -> s.pairing.addListener(onPairing); s.trust.addListener(onTrust); s.capacity.addListener(onCapacity) } }
        if (svc == null) { status.text = "Démarrage de CastBridge TV…"; return }
        if (!opened) { opened = true; if (!svc.pairing.isOpen && hasBt()) { openWindow(); return } }
        tvName.text = svc.tvName().also { tvName.textSize = if (it.length > 18) 26f else 34f }
        val ad = adapter()
        val state = svc.pairing.state()
        val btOn = hasBt() && ad?.isEnabled == true
        btSettingsBtn.visibility = if (!btOn && hasBt()) View.VISIBLE else View.GONE
        when {
            ad == null -> { countdown.text = "Bluetooth absent"; countdown.setTextColor(C.ERROR); status.text = "Cette TV n'a pas de Bluetooth : utilisez le code et le Wi-Fi." }
            !hasBt() -> { countdown.text = "Bluetooth non autorisé"; countdown.setTextColor(C.ERROR); status.text = message ?: "Autorisez « Appareils à proximité » pour CastBridge TV." }
            !btOn -> { countdown.text = "Bluetooth éteint"; countdown.setTextColor(C.ERROR); status.text = "Allumez le Bluetooth de la TV, puis revenez ici." }
            state is PairingSession.State.Closed -> { countdown.text = "Non visible"; countdown.setTextColor(C.TEXT_MID); status.text = message ?: ("Appuyez sur « Rendre visible » pour ajouter un téléphone.\n" + btLine(svc, state)) }
            state is PairingSession.State.Asking -> { countdown.text = "Visible encore ${mmss(svc.pairing.secondsLeft())}"; countdown.setTextColor(C.SUCCESS); status.text = "${state.name} demande l'autorisation…" }
            else -> { countdown.text = "Visible encore ${mmss(svc.pairing.secondsLeft())}"; countdown.setTextColor(C.SUCCESS); status.text = message ?: ("En attente d'un téléphone…\n" + btLine(svc, state)) }
        }
        visibleBtn.text = if (state is PairingSession.State.Closed) "Rendre visible 2 minutes" else "Prolonger de 2 minutes"
        showAsk(svc, state)
        renderList(svc.trust.list())
    }

    /** « Bluetooth prêt · 2 téléphones de confiance (1 connecté) » : the TV's short status, matching the phone's diagnostic. */
    private fun btLine(svc: TvService, state: PairingSession.State) = castbridge.core.trust.TvBtStatus.line(true, null, svc.trust.list().size,
        svc.presence.statuses().count { it.state != castbridge.core.trust.PhonePresence.State.DISCONNECTED }, state)

    private fun showAsk(svc: TvService, state: PairingSession.State) {
        if (state !is PairingSession.State.Asking) { dialog?.takeIf { it.isShowing }?.dismiss(); dialog = null; return }
        if (dialog?.isShowing == true) return
        val d = AlertDialog.Builder(this)
            .setTitle("Autoriser ${state.name} à piloter cette TV ?")
            .setMessage("Ce téléphone pourra envoyer des vidéos, utiliser la télécommande et la bibliothèque sans code. Vérifiez que le nom est bien le vôtre. Vous pourrez le retirer à tout moment.")
            .setPositiveButton("Autoriser") { _, _ -> svc.pairing.approve(); message = "${state.name} est maintenant autorisé." }
            .setNegativeButton("Refuser") { _, _ -> svc.pairing.deny(); message = "${state.name} a été refusé." }
            .setOnCancelListener { svc.pairing.deny() }
            .create()
        d.setOnShowListener { d.getButton(AlertDialog.BUTTON_NEGATIVE)?.requestFocus() }     // the safe answer is the pre-selected one
        dialog = d; d.show()
    }

    private fun renderList(phones: List<TrustedPhone>) {
        val presence = bound?.presence?.statuses().orEmpty().associateBy { it.address }
        val sig = phones.joinToString("|") { "${it.address}:${it.name}:${it.lastSeen}:${presence[it.address]?.state}" }
        listTitle.text = "Téléphones de confiance (${castbridge.core.trust.PhonesTexts.counter(phones.size)})"
        phonesBtn.text = castbridge.core.trust.PhonesTexts.menuEntry(phones.size).removeSuffix("…")
        forgetAll.visibility = if (phones.isEmpty()) View.GONE else View.VISIBLE
        if (sig == listSig) return
        listSig = sig
        val hadFocus = list.hasFocus()
        list.removeAllViews()
        if (phones.isEmpty()) list.addView(label("Aucun téléphone pour l'instant.", 20f, C.TEXT_MID))
        val df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        phones.forEach { p ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(6), 0, dp(6)) }
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            col.addView(label(p.name, 22f, C.TEXT, true).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
            val live = when (presence[p.address]?.state) {
                castbridge.core.trust.PhonePresence.State.CONNECTED -> "connecté · "
                castbridge.core.trust.PhonePresence.State.RECONNECTED -> "liaison reprise · "
                castbridge.core.trust.PhonePresence.State.DISCONNECTED -> "téléphone déconnecté · "
                null -> ""
            }
            col.addView(label("$live" + "Ajouté le ${DateFormat.getDateInstance(DateFormat.SHORT).format(Date(p.addedAt))} · vu ${df.format(Date(p.lastSeen))}", 15f, C.TEXT_MID).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END })
            row.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(button("Retirer", danger = true) { confirmRemove(p) }, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(12) })
            list.addView(row, LinearLayout.LayoutParams(-1, -2))
        }
        if (hadFocus) (list.getChildAt(0) as? ViewGroup)?.getChildAt(1)?.requestFocus()
    }

    companion object {
        private const val REQ_PERMS = 41
        private const val REQ_VISIBLE = 42
        fun open(ctx: android.content.Context) { ctx.startActivity(Intent(ctx, PairActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
