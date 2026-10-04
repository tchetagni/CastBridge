package castbridge.receiver

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.TextView
import castbridge.core.tv.LibraryProvider
import castbridge.core.tv.PlayerCommand
import castbridge.core.tv.PlayerParams
import castbridge.core.tv.PlayerState
import castbridge.core.tv.PlayerTracks
import castbridge.core.tv.Progressive
import castbridge.core.tv.ReceiverServer
import castbridge.core.tv.VolumeKind
import castbridge.core.tv.VolumeRegistry
import castbridge.core.tv.then
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import castbridge.core.xfer.CopyBadge
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The CastBridge TV screen: waiting screen, library and playback (libVLC). Everything else (HTTP server, transfers,
 * Bluetooth, SSH, storage...) runs in [TvService], which starts with the TV and keeps running when this screen is closed;
 * this activity binds to it and registers itself as the player while it exists.
 */
class PlayerActivity : Activity(), TvService.Screen {
    private val main = Handler(Looper.getMainLooper())
    // libVLC is created on the first play() and fully released on stop / end of media / low memory / leaving the screen:
    // the service and the waiting screen do not need it (see docs/ADMIN.md, "Mémoire").
    private var libVlc: LibVLC? = null
    private var mp: MediaPlayer? = null
    private var home: HomeScreen? = null                    // the launcher-like home (docs/ADMIN.md, "Écran d'accueil")
    private var libScreen: LibraryScreen? = null            // the whole library as a grid
    private var settingsPanel: SettingsPanel? = null        // "Connexion & réglages" (MENU)
    private var thumbs: TvThumbs? = null
    private lateinit var banner: Banner
    @Volatile private var currentSize = 0L                  // size of the file playing: with its name, the key of its saved position
    private lateinit var extras: PlayerExtras               // per-file player settings, tracks, decoder (docs/ADMIN.md, "Lecteur")
    private lateinit var panel: PlayerPanel
    private lateinit var bar: ProgressOverlay
    private var playerSpu = false                           // libVLC was created with its subtitle engine
    private var reopen: ((Long) -> Unit)? = null             // re-opens the current file at a position (subtitle engine, decoder change)
    private var swRetry = false                             // the software-decoding retry was already made for this file
    private lateinit var lead: TextView
    @Volatile private var streamingName: String? = null    // set while playing a file that may still be arriving
    private var lastLeadUpdate = 0L
    private var safPfd: android.os.ParcelFileDescriptor? = null   // descriptor of the SAF file being played (libVLC reads it)
    private var idleMsg: String? = null
    @Volatile private var resumed = false

    // The background service and what this screen uses of it.
    @Volatile private var svc: TvService? = null
    private val server: ReceiverServer? get() = svc?.server
    private val library: LibraryProvider? get() = svc?.library
    private val prefs: TvPrefs get() = svc!!.prefs
    private val pin: String get() = svc?.pin.orEmpty()
    private val statuses: Map<String, String> get() = svc?.statuses.orEmpty()
    private fun bgRun(r: () -> Unit) { runCatching { svc?.bg?.execute(r) } }

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val s = (binder as? TvService.Local)?.service ?: return
            svc = s
            onBound(s)
        }
        override fun onServiceDisconnected(name: ComponentName?) { svc = null }
    }

    // Snapshot read by HTTP threads; written on the main thread from libVLC events.
    @Volatile private var snapshot = PlayerState()
    /** R-15: stutter detector from libVLC's playhead and Buffering events (libVLC gives no read-ahead level); read by the copy's closed-loop pacing. */
    private val health = castbridge.core.xfer.PlaybackHealth()
    private var current: File? = null

    // R-16: the picture's health (libVLC counters), the decoder tuning of this file and the copy badge
    private var badge: CopyBadgeView? = null
    private var lastBadgeAt = 0L
    private var tuneStage = 0                               // distress level the player was last lightened for (0 = not at all)
    private var tuneReopens = 0                             // at most 2 re-openings per file
    private var appliedTuning: castbridge.core.xfer.PlayerTuning.Tuning? = null
    private var statsTickOn = false
    private var cpuBaseMs = -1L; private var shownBase = -1  // CPU time of the app per displayed picture: a HINT of hardware vs software decoding
    private var lastShown = 0; private var lastLost = 0
    private var cpuPerFrame: Double? = null
    private var decoderLogged = false

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_CastBridge_Tv) // leaves the launch theme (splash) for the normal one
        super.onCreate(savedInstanceState)
        // Activation (docs/TRIAL-EDITION.md): a locked TV (no key, no grace) shows nothing but the activation screen; in the grace period it is offered once a day
        ActivationCenter.init(this)
        val gate = ActivationCenter.state()
        if (gate is castbridge.core.owner.GateState.Locked || (gate is castbridge.core.owner.GateState.Grace && ActivationCenter.dailyPrompt())) {
            startActivity(Intent(this, ActivationActivity::class.java)); finish(); return
        }
        setContentView(R.layout.activity_player)
        lead = findViewById(R.id.lead)
        banner = Banner(findViewById(android.R.id.content))
        TvService.start(this)                                // runs on its own afterwards (and starts with the TV)
        bindService(Intent(this, TvService::class.java), conn, BIND_AUTO_CREATE)
    }

    private fun onBound(s: TvService) {
        extras = PlayerExtras({ library?.db }, s.prefs) { r -> runCatching { s.bg.execute(r) } }
        val t = thumbs ?: TvThumbs { n, v -> server?.thumbnail(n, v) }.also { thumbs = it }
        if (libScreen == null) libScreen = LibraryScreen(this, findViewById(R.id.library), t, libraryApi())
        if (home == null) home = HomeScreen(this, findViewById(R.id.home), t, homeApi())
        if (settingsPanel == null) settingsPanel = SettingsPanel(this, findViewById(R.id.settings))
        panel = PlayerPanel(this, panelApi())
        if (!::bar.isInitialized) bar = ProgressOverlay(this, findViewById(android.R.id.content)).also { b -> b.onChange = { refreshBadge(true) } }
        if (badge == null) badge = CopyBadgeView(this, findViewById(android.R.id.content))
        refreshStatusBar()                                   // connections already known when the screen (re)opens
        s.attach(this)                                       // may run a play request that arrived while the screen was closed
        requestRuntimePermissions()
        if (current == null && libScreen?.visible != true) showHome()
    }

    /** « Accueil » of the phone remote (RemoteHub): leave the video / library / other screen for the home. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(RemoteHub.EXTRA_HOME, false)) { if (current != null) stop() else showHome() }
    }

    override fun onResume() {
        super.onResume(); resumed = true
        TvConnect.screens.enter(screenId)
        if (::extras.isInitialized) svc?.takePending()?.let { runPending(it) }
        serverScreens()
    }

    override fun onStart() { super.onStart(); TvConnect.addListener(serverListener); home?.resume() }

    private val serverListener: () -> Unit = { if (resumed) serverScreens() }

    /**
     * The server link asks for a screen: the information screen at the first launch (nothing is sent before it is
     * answered), or the blocking screen of a mandatory update.
     */
    private fun serverScreens() {
        val link = TvConnect.link ?: return
        if (link.state.needsConsent) { if (!consentShown) { consentShown = true; ServerActivity.open(this, ServerActivity.MODE_CONSENT) }; return }
        if (current == null && !TunnelHub.askedThisRun && !ActivationCenter.locked() && !TunnelHub.termsAccepted(this) &&
            castbridge.core.tunnel.TunnelEnroll.pickActivation(ActivationCenter.allActivations(), ActivationCenter.now()) != null) { TunnelHub.askedThisRun = true; showTermsDialog() }
        val u = link.update
        if (u.mandatory && u.manifest != null && u.phase != castbridge.core.connect.ServerLink.Phase.UP_TO_DATE && current == null && !mandatoryShown) {
            mandatoryShown = true
            ServerActivity.open(this, ServerActivity.MODE_MANDATORY)
        }
    }
    private var consentShown = false
    private var mandatoryShown = false

    /**
     * The terms of use of this version (docs/CONDITIONS-ASSISTANCE-A-DISTANCE.md), asked ONCE per start on the home of a TV activated before they existed, and from « À propos > Assistance à distance ».
     * Until they are accepted the TV works as before but the remote-assistance tunnel does not start.
     */
    private fun showTermsDialog() {
        val tv = TextView(this).apply { text = castbridge.core.tunnel.TunnelTerms.TEXT; textSize = TvStyle.Type.CAPTION; setTextColor(TvStyle.TEXT); setPadding(32, 24, 32, 24) }
        val sv = android.widget.ScrollView(this).apply { addView(tv); setBackgroundColor(TvStyle.BG_ELEVATED) }
        AlertDialog.Builder(this).setTitle(castbridge.core.tunnel.TunnelTerms.TITLE + " (" + castbridge.core.tunnel.TunnelTerms.VERSION + ")").setView(sv)
            .setPositiveButton(castbridge.core.tunnel.TunnelTerms.ACCEPT_BUTTON) { _, _ ->
                flash(if (TunnelHub.acceptTerms(this)) "Conditions acceptées." else "Impossible d'enregistrer l'acceptation : réessayez depuis le menu.")
            }
            .setNegativeButton(castbridge.core.tunnel.TunnelTerms.LATER_BUTTON, null).show()
    }

    /** « À propos > Assistance à distance »: the state line, the detail and the local journal of connections and of the experts that logged in. */
    fun showAssistance() {
        val lines = TunnelHub.journalLines(this, 40)
        val text = buildString {
            append(TunnelHub.statusLine(this@PlayerActivity)); TunnelHub.detail(this@PlayerActivity)?.let { append('\n').append(it) }
            append("\n\n").append(castbridge.core.tunnel.TunnelTerms.VERSION).append(" : ").append(if (TunnelHub.termsAccepted(this@PlayerActivity)) "acceptées" else "non acceptées")
            append("\n\nJournal local (connexions, déconnexions, experts connectés) :\n")
            append(if (lines.isEmpty()) castbridge.core.tunnel.TunnelText.EMPTY_JOURNAL else lines.asReversed().joinToString("\n"))
        }
        val tv = TextView(this).apply { typeface = android.graphics.Typeface.MONOSPACE; textSize = TvStyle.Type.CAPTION; setTextColor(TvStyle.TEXT); setPadding(32, 24, 32, 24); this.text = text }
        val b = AlertDialog.Builder(this).setTitle("À propos : " + castbridge.core.tunnel.TunnelText.TITLE)
            .setView(android.widget.ScrollView(this).apply { addView(tv); setBackgroundColor(TvStyle.BG_ELEVATED) })
            .setPositiveButton("Fermer", null)
            .setNeutralButton("Lire les conditions…") { _, _ -> showTermsDialog() }
        b.show()
    }

    override fun onPause() {
        super.onPause()
        resumed = false
        savePosition()                                      // HOME, another app on top: keep the resume point
    }

    override fun onStop() {
        super.onStop()
        TvConnect.removeListener(serverListener)
        home?.pause()                                       // Quiz, Apprendre… on top: the hidden home stops its zoom and its 4 s reload (R-11)
        consentShown = false; mandatoryShown = false
        // The screen is gone: give libVLC back (the service keeps serving). Back in front = the library.
        if (mp != null && !isChangingConfigurations) {
            playbackEnd(abandoned = true)
            current = null; snapshot = PlayerState(); releasePlayer(); reopen = null; policyChanged()
            if (::extras.isInitialized) extras.forget()
            main.post { if (home != null) showHome() }
        }
    }

    // ---- TvService.Screen ----

    override val shown: Boolean get() = resumed && !isFinishing
    override val activity: Activity get() = this
    override fun notice(msg: String) { flash(msg) }
    override fun statusesChanged() { if (settingsPanel?.visible == true) showSettings(); refreshStatusBar() }
    override fun transfersChanged() { home?.takeIf { it.visible }?.refreshStatus(); refreshBadge() }
    override fun iconsChanged() { refreshStatusBar() }
    override fun thumbReady(name: String) { thumbs?.ready(name); libScreen?.onThumbReady(name); home?.onThumbReady(name) }
    override fun runPending(r: TvService.Pending) {
        runCatching {
            when (r) {
                is TvService.Pending.Local -> play(r.file, r.pos)
                is TvService.Pending.Stream -> playStream(r.url, r.name, r.pos)
                is TvService.Pending.Saf -> playSaf(r.name, r.size, r.pos)
            }
            svc?.pendingPlayed()
        }.onFailure { flash("Lecture impossible : ${it.message}") }
    }

    // ---- Storage actions that need this screen (docs/STORAGE.md) ----

    /** Opens the system folder picker; returns what to tell the user. */
    fun pickSafFolder(): String {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
        return try {
            startActivityForResult(i, REQ_STORAGE_TREE)
            "Sélecteur de dossier ouvert sur l'écran de la TV : choisissez le dossier (par exemple sur la clé USB) avec la télécommande, puis validez."
        } catch (e: ActivityNotFoundException) {
            "Cette TV n'a pas de sélecteur de fichiers Android : impossible de choisir un dossier. Utilisez le dossier de l'app sur la clé " +
                "(getExternalFilesDirs, rempli depuis un ordinateur) ou la mémoire interne."
        } catch (e: Exception) { "Sélecteur indisponible : ${e.message}" }
    }

    private fun chooseTarget() {
        val s = svc ?: return
        val vols = s.registry.volumes()
        val ids = mutableListOf("auto", "internal") + vols.filter { it.kind != VolumeKind.INTERNAL }.map { it.id }
        val names = mutableListOf("Automatique (clé USB si utilisable, sinon interne)", "Mémoire interne") +
            vols.filter { it.kind != VolumeKind.INTERNAL }.map { it.label + if (it.kind == VolumeKind.SAF) " (dossier choisi, sans lecture pendant l'envoi)" else "" }
        AlertDialog.Builder(this).setTitle("Où ranger les nouveaux fichiers ?")
            .setItems(names.toTypedArray()) { _, i ->
                val ok = server?.setTargetValue(ids[i]) == true
                flash(if (ok) "Cible : ${names[i]}" else "Cible refusée"); s.updateStorageStatus()
            }.setNegativeButton("Fermer", null).show()
    }

    private fun forgetSafFolder() {
        prefs.getString("saf_tree")?.let { u -> runCatching {
            contentResolver.releasePersistableUriPermission(Uri.parse(u), Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } }
        prefs.putString("saf_tree", null)
        if (server?.target == "saf") server?.setTargetValue("auto")
        svc?.rescanAsync(); flash("Dossier choisi oublié (les fichiers qu'il contient ne sont pas effacés)")
    }

    private fun update(state: String) {
        val was = snapshot.state
        snapshot = snapshot.copy(state = state, name = current?.name ?: snapshot.name)
        if (active(was) != active(state)) policyChanged()
    }

    private fun active(state: String) = state == "playing" || state == "buffering"
    /** « La lecture d'abord » (castbridge.core.xfer.PlaybackPriority): the server re-reads the player's state at once, on its own thread. */
    private fun policyChanged() { runCatching { server?.playbackChanged() } }

    /** The player, created on demand; re-created when the subtitle engine must be switched on or off ([spu]). */
    private fun ensurePlayer(spu: Boolean = false): MediaPlayer {
        mp?.let { if (playerSpu == spu) return it; releasePlayer() }
        val opts = arrayListOf(
            // R-16: libVLC drops late frames (its default) instead of waiting for each one: with the old --no-drop-late-frames --no-skip-frames a decoder
            // short of CPU froze the picture while the audio went on. Per-file knobs (threads, loop filter...) come from PlayerTuning, see configure().
            "--file-caching=400",            // local file: 1500 ms of read-ahead only cost RAM
            "--no-audio-time-stretch",       // no resampling buffers for A/V drift
            "--no-sub-autodetect-file",      // subtitle files next to the video are found by the app (SubtitleFinder), not by scanning
            "--no-osd",                      // R-16: statistics stay ON (Media.getStats: displayed / lost pictures feed VideoStallDetector)
        )
        // The subtitle engine (freetype, fonts, blending) costs memory on this TV: only for files that need it.
        if (spu) opts += "--sub-text-scale=${extras.p.subScale}" else opts += "--no-spu"
        val lv = LibVLC(this, opts)
        playerSpu = spu
        val p = MediaPlayer(lv)
        p.attachViews(findViewById<VLCVideoLayout>(R.id.video), null, false, false)
        // Affichage (VideoFit): the real size of the video surface, re-applied when it changes (display mode switch, rotation) and on each Vout event.
        extras.panel = { panelSize() }
        findViewById<View>(R.id.video).addOnLayoutChangeListener { _, l, t, r, b, ol, ot, orr, ob ->
            if (r - l != orr - ol || b - t != ob - ot) mp?.let { extras.applyFit(it) }
        }
        p.setEventListener { ev ->
            when (ev.type) {
                MediaPlayer.Event.Vout, MediaPlayer.Event.ESAdded, MediaPlayer.Event.ESSelected -> main.post { mp?.let { extras.applyFit(it) } }
                MediaPlayer.Event.Playing -> { health.onPlaying(android.os.SystemClock.elapsedRealtime()); update("playing"); main.post { mp?.let { extras.onPlaying(it, playerSpu) }; playbackPlaying(); startStats() } }
                MediaPlayer.Event.Paused -> { health.onStopped(); update("paused") }
                MediaPlayer.Event.TimeChanged -> { health.onTime(android.os.SystemClock.elapsedRealtime(), ev.timeChanged); snapshot = snapshot.copy(posMs = ev.timeChanged); main.post { updateLead(); if (extras.fitPending) mp?.let { extras.applyFit(it) } } }
                MediaPlayer.Event.Buffering -> {
                    // libVLC pauses by itself when the data runs out (playback caught up with the upload) and resumes alone.
                    val st = snapshot.state
                    health.onBuffering(android.os.SystemClock.elapsedRealtime(), ev.buffering)
                    if (ev.buffering < 100f && st == "playing") { update("buffering"); main.post { flash("Mise en mémoire tampon… (en attente de l'envoi)") } }
                    else if (ev.buffering >= 100f && st == "buffering") update("playing")
                }
                MediaPlayer.Event.LengthChanged -> snapshot = snapshot.copy(durMs = ev.lengthChanged)
                // Never release from inside libVLC's own event thread: hop to the main thread.
                MediaPlayer.Event.EndReached -> {
                    health.onStopped(); update("ended"); val n = current?.name; val size = currentSize; val dur = snapshot.durMs
                    main.post {
                        playbackEnd(abandoned = false, complete = true)
                        current = null; streamingName = null; releasePlayer()
                        n?.let { name -> bgRun { library?.db?.onEnded(name, size, dur) } }
                        val next = n?.let { name -> runCatching { server?.onPlaybackEnded(name) == true }.getOrDefault(false) } == true
                        if (!next) afterPlayback()                  // a playlist goes on with its next file
                    }
                }
                MediaPlayer.Event.EncounteredError -> {
                    update("error"); val n = current?.name; val pos = snapshot.posMs
                    main.post {
                        val r = reopen
                        if (!swRetry && r != null && extras.hwMode() != "off") {
                            // Hardware decoding refused this file: one retry in software at the same position.
                            swRetry = true; extras.hwOverride = "off"; releasePlayer(); pbRetry = true
                            flash("Décodage matériel impossible : nouvel essai en décodage logiciel")
                            runCatching { r(pos) }.onFailure { current = null; afterPlayback("Lecture impossible : $n") }
                        } else {
                            playbackEnd(abandoned = false, ok = false, error = "decode")
                            TvConnect.error("player", "playback", "Lecture impossible (décodage)")
                            current = null; streamingName = null; releasePlayer(); afterPlayback("Lecture impossible : $n")
                        }
                    }
                }
            }
        }
        libVlc = lv; mp = p
        return p
    }

    /** "Encore X min de lecture sans réseau" while the file is still arriving. */
    private fun updateLead() {
        val n = streamingName
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastLeadUpdate < 2000) return
        lastLeadUpdate = now
        val (rec, tot) = n?.let { server?.progress(it) } ?: run { lead.visibility = View.GONE; return }
        if (rec >= tot) { lead.visibility = View.GONE; return }
        val s = snapshot
        val ahead = (Progressive.reachableMs(s.durMs, rec, tot, 0) - s.posMs).coerceAtLeast(0)
        lead.text = "Envoi ${rec * 100 / tot} %  -  encore ${leadText(ahead)} de lecture sans réseau"
        lead.visibility = View.VISIBLE
    }

    private fun leadText(ms: Long) = if (ms >= 90_000) "${ms / 60_000} min" else "${ms / 1000} s"

    private fun closeSafFd() { runCatching { safPfd?.close() }; safPfd = null }

    private fun releasePlayer() {
        val p = mp; val lv = libVlc
        mp = null; libVlc = null; streamingName = null
        main.removeCallbacks(statsTick); statsTickOn = false; main.removeCallbacks(badgeTick); badge?.render(CopyBadge.of(emptyList()))
        decoderLogged = false; cpuBaseMs = -1; shownBase = -1; cpuPerFrame = null; lastShown = 0; lastLost = 0
        if (::lead.isInitialized) lead.visibility = View.GONE
        runCatching { p?.setEventListener(null) }
        runCatching { p?.stop() }
        runCatching { p?.detachViews() }
        runCatching { p?.release() }
        runCatching { lv?.release() }
        closeSafFd()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Not playing (paused/ended/idle) and the system wants memory back: give the whole player back.
        if (level >= TRIM_MEMORY_UI_HIDDEN && snapshot.state != "playing" && mp != null) {
            current = null; snapshot = PlayerState(); releasePlayer(); policyChanged()
        }
    }

    // ---- playback statistics (docs/TELEMETRY.md: playback_start / playback_end; codec, resolution, never the file name) ----
    private var pbSource: String? = null
    private var pbStartedAt = 0L
    private var pbSent = false
    private var pbRetry = false
    private var pbCodec: String? = null
    private var pbRes: String? = null
    private var pbHw: Boolean? = null

    /** A file starts ([source]: internal | usb | stream | phone); the previous one, if any, was left before its end. */
    private fun playbackBegin(source: String) {
        if (pbRetry && pbSource != null) { pbRetry = false; return }     // same file again in software decoding
        pbRetry = false
        playbackEnd()
        pbSource = source; pbStartedAt = android.os.SystemClock.elapsedRealtime(); pbSent = false; pbCodec = null; pbRes = null
        pbHw = runCatching { extras.hwFlags().first }.getOrNull()
    }

    private fun playbackPlaying() {
        if (pbSource == null || pbSent) return
        pbSent = true
        val t = runCatching { mp?.currentVideoTrack }.getOrNull()
        pbCodec = t?.codec?.trim()?.takeIf { it.isNotEmpty() }
        pbRes = t?.takeIf { it.width > 0 && it.height > 0 }?.let { "${it.width}x${it.height}" }
        TvConnect.track("playback_start", mapOf("codec" to pbCodec, "resolution" to pbRes, "hw" to pbHw, "source" to pbSource))
    }

    /** End of the current file: [abandoned] null = judged from the position (less than 95 % seen). */
    private fun playbackEnd(abandoned: Boolean? = null, ok: Boolean = true, error: String? = null, complete: Boolean = false) {
        val src = pbSource ?: return
        pbSource = null
        val s = snapshot
        val pct = if (complete) 100.0 else if (s.durMs > 0) (s.posMs * 100.0 / s.durMs).coerceIn(0.0, 100.0) else null
        TvConnect.track("playback_end", mapOf("ms" to (android.os.SystemClock.elapsedRealtime() - pbStartedAt), "pct" to pct,
            "codec" to pbCodec, "resolution" to pbRes, "hw" to pbHw, "source" to src,
            "abandoned" to (abandoned ?: ((pct ?: 0.0) < 95.0)), "ok" to ok, "error" to error))
    }

    /** Sub-screen shown by this activity, for the screen-time statistics (docs/TELEMETRY.md): home, library, player, settings. */
    @Volatile var screenId: String = "home"; private set
    private fun enterScreen(id: String) { screenId = id; if (resumed || hasWindowFocus()) TvConnect.screens.enter(id) }

    /** The home (launcher): rows of videos, clock, "ready" band. Technical details are in "Connexion & réglages". */
    private fun showHome() {
        libScreen?.hide(); settingsPanel?.hide()
        home?.show()
        enterScreen("home")
        refreshStatusBar()
    }

    /** Hides every screen (a video starts). */
    private fun hideScreens() {
        home?.hide(); libScreen?.hide(); settingsPanel?.hide()
        refreshStatusBar()
    }

    private fun showLibrary() {
        home?.hide(); settingsPanel?.hide()
        libScreen?.show()
        enterScreen("library")
        refreshStatusBar()
    }

    /** Back from a video (BACK, end of file, error): the home with its "Reprendre" row, not an empty screen. */
    private fun afterPlayback(msg: String? = null) {
        if (msg != null) flash(msg)
        showHome()
    }

    /** Count of the home's last listing (computed on its own thread): the « Bibliothèque » tile reads it instead of listing the library on the main thread every 4 s (R-11). */
    @Volatile private var homeItemCount = -1
    private fun homeApi() = object : HomeScreen.Api {
        override fun items() = ParentalHub.filterItems(server?.libraryItems().orEmpty()).also { homeItemCount = it.size }
        override fun status(): Triple<String, String, String?> {
            val s = server
            // ONE source for every path (Wi-Fi, Wi-Fi multivoie, Bluetooth), held by the service (independent of the HTTP server): castbridge.core.xfer.ReceiveCards
            val cards = castbridge.core.xfer.ReceiveCards.of(svc?.reception?.shown().orEmpty(), s?.receiving().orEmpty(), s != null)
            return Triple(castbridge.core.xfer.ReceiveCards.ready(s != null), ParentalHub.shownPin(pin), castbridge.core.xfer.ReceiveCards.headline(cards))
        }
        override fun signal() = castbridge.core.ux.TvSignal.of(TvSignalViews.facts(this@PlayerActivity, svc, server != null))
        override fun open(i: castbridge.core.tv.LibraryItem, row: List<castbridge.core.tv.LibraryItem>, index: Int) { libScreen?.open(i, row, index) }
        override fun actions(i: castbridge.core.tv.LibraryItem, row: List<castbridge.core.tv.LibraryItem>, index: Int) { libScreen?.actions(i, row, index) }
        override fun openLibrary() = showLibrary()
        override fun openSettings() = showSettings()
        override fun tools(): List<HomeTool> = homeTools()
        override fun openHelp() {
            // the phone button is named by its current label (castbridge.core.ux.TvHelpTexts ← SendWay.COPY), never a stale one
            AlertDialog.Builder(this@PlayerActivity).setTitle(castbridge.core.ux.TvHelpTexts.SEND_TITLE)
                .setMessage(castbridge.core.ux.TvHelpTexts.send(ParentalHub.shownPin(pin), "http://${TvService.localIp() ?: "adresse-de-la-TV"}:${ReceiverServer.PORT}") + "\n\nPastilles : " + castbridge.core.ux.TvSignal.LEGEND)
                .setPositiveButton("Compris", null).show()
        }
    }

    /**
     * PNG of this app's window (what the viewer sees, minus the video surface which PixelCopy of the window does include
     * on most devices). Called from an HTTP thread; waits for the main-thread copy. null if it fails.
     */
    fun capture(): ByteArray? {
        val v = window?.decorView ?: return null
        if (v.width == 0 || v.height == 0) return null
        val bmp = android.graphics.Bitmap.createBitmap(v.width, v.height, android.graphics.Bitmap.Config.ARGB_8888)
        val done = CountDownLatch(1); var ok = false
        main.post {
            runCatching {
                android.view.PixelCopy.request(window, bmp, { r -> ok = r == android.view.PixelCopy.SUCCESS; done.countDown() }, main)
            }.onFailure { runCatching { v.draw(android.graphics.Canvas(bmp)); ok = true }; done.countDown() }
        }
        if (!done.await(5, TimeUnit.SECONDS) || !ok) { bmp.recycle(); return null }
        return java.io.ByteArrayOutputStream().use { o -> bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, o); bmp.recycle(); o.toByteArray() }
    }

    /** Internet: gateway state and connectivity tests, each path on its own. */
    private fun internetMenu() {
        val s = svc
        val g = s?.gateway
        fun test(title: String, block: ((String) -> Unit) -> Unit) {
            showDiag(title)
            Thread { block { l -> main.post { appendDiag(l) } }; s?.checkNetNow() }.start()
        }
        choose("Internet  —  ${s?.netSummary() ?: ""}", listOf<Pair<String, () -> Unit>>(
            "État de la passerelle Bluetooth" to {
                showDiag("Passerelle Bluetooth")
                (g?.statusLines() ?: listOf("Passerelle non démarrée (Bluetooth non autorisé sur la TV)")).forEach { appendDiag(it) }
                appendDiag(""); appendDiag("Internet de la TV : ${s?.netSummary() ?: "?"}")
            },
            "Tester le réseau de la TV (${TvNetDiag.localLink(this)})" to { test("Réseau de la TV") { out -> TvNetDiag.run("8.8.8.8", null, out) } },
            "Tester via la passerelle du téléphone" to {
                test("Via la passerelle du téléphone") { out ->
                    val p = g?.proxy()
                    if (p == null) (g?.statusLines() ?: listOf("Passerelle non démarrée")).forEach(out)
                    else { out("Téléphone : ${g.phoneName}"); TvNetDiag.run("8.8.8.8", p, out); out("Ping et traceroute exécutés par le téléphone :"); g.diagnosePhoneSide("8.8.8.8", out) }
                }
            },
            "Tout tester" to { test("Test Internet complet") { out -> g?.diagnose("8.8.8.8", out) ?: TvNetDiag.run("8.8.8.8", null, out) } },
        ))
    }

    /** Pick one of several actions with the remote (sub-menu of a home icon). */
    private fun choose(title: String, items: List<Pair<String, () -> Unit>>) {
        if (items.isEmpty()) return
        val items = ParentalHub.wrapMenu(this, items)
        AlertDialog.Builder(this).setTitle(title).setItems(items.map { it.first }.toTypedArray()) { _, i -> items[i].second() }
            .setNegativeButton("Fermer", null).show()
    }

    /** Every feature of the app as a home icon, with its live state. */
    /** A home tile that also counts its use (feature_used, docs/TELEMETRY.md: closed list of ids). */
    private fun tile(feature: String, icon: Int, label: String, description: String, status: String?, on: Boolean, warn: Boolean = false, action: () -> Unit) =
        HomeTool(icon, label, description, status, on, warn) { TvConnect.feature(feature, "tile"); ParentalHub.guardTile(this, feature, action) }

    /** Status line of the "Mises à jour" tile: what the server link knows right now. */
    private fun updateStatus(): String {
        val link = TvConnect.link ?: return "Version ?"
        val u = link.update
        val mine = "Version ${link.installed.versionName ?: link.installed.versionCode}"
        return when (u.phase) {
            castbridge.core.connect.ServerLink.Phase.DOWNLOADING -> "Téléchargement ${if (u.total > 0) u.done * 100 / u.total else 0} %"
            castbridge.core.connect.ServerLink.Phase.READY -> "Nouvelle version ${u.manifest?.versionName} prête"
            castbridge.core.connect.ServerLink.Phase.INSTALLING -> "Installation ${u.manifest?.versionName}…"
            castbridge.core.connect.ServerLink.Phase.UP_TO_DATE -> "$mine · à jour"
            castbridge.core.connect.ServerLink.Phase.BLOCKED -> "$mine · bloquée"
            else -> mine
        }
    }

    private fun homeTools(): List<HomeTool> {
        val s = svc
        val st = statuses
        val usb = s?.usb; val ssh = s?.ssh
        val drives = s?.registry?.let { r -> runCatching { r.volumes().filter { it.kind == castbridge.core.tv.VolumeKind.REMOVABLE } }.getOrNull() }.orEmpty()
        val btOk = st["1-bt"]?.contains("prêt") == true || st["1-bt"]?.contains("réception") == true
        val net = st["6-gw"]
        val internetUp = s?.let { it.netDirectMs != null || it.netGatewayMs != null || it.netCheckedAt == 0L } != false
        val wdOn = prefs.getBool("wd_enabled", false)
        val sshOn = ssh?.running == true
        val upgrade = if (ActivationCenter.trial()) listOf(
            tile(castbridge.core.owner.TrialPolicy.UPGRADE_TILE, R.drawable.ic_cb_cle_usb, castbridge.core.owner.TrialPolicy.UPGRADE_LABEL, "Version d'essai : demandez la clé de production avec le code de cette TV.", "Essai", true) {
                startActivity(Intent(this, ActivationActivity::class.java).putExtra(ActivationActivity.EXTRA_UPGRADE, true))
            }) else emptyList()
        return ParentalHub.filterHome(upgrade + listOf(
            tile("library", R.drawable.ic_cb_bibliotheque, "Bibliothèque", "Toutes vos vidéos et vos fichiers, en grille.", "${homeItemCount.takeIf { it >= 0 } ?: ParentalHub.filterItems(server?.libraryItems().orEmpty()).size} fichier(s)", false) { showLibrary() },
            tile("bluetooth", R.drawable.ic_cb_bluetooth, "Ajouter un téléphone", "Le téléphone trouve et pilote la TV par Bluetooth, sans code à saisir : une seule validation ici.",
                (svc?.trust?.list()?.size ?: 0).let { if (it == 0) "Aucun" else "$it de confiance" }, (svc?.trust?.list()?.size ?: 0) > 0) { PairActivity.open(this) },
            tile("learn", R.drawable.ic_cb_apprendre, "Apprendre", "Leçons de la maternelle à la licence, exercices corrigés, préparer le CEP, le BEPC, le GCE, le Bac.", "Élèves", true) {
                startActivity(Intent(this, LearnActivity::class.java))
            },
            tile("langues", R.drawable.ic_cb_apprendre, "Langues", "Chinois, anglais, allemand, français, italien, espagnol, japonais : leçons et exercices reçus du téléphone.",
                LanguesHub.status(this), LanguesHub.hasContent(this)) {
                startActivity(Intent(this, LanguesActivity::class.java))
            },
            // Quiz, Échecs and Sudoku live in the « Jeux » hub (docs/GAMES.md); their public URLs (/quiz, /chess) are unchanged.
            tile("games", R.drawable.ic_t_games, "Jeux", "Quiz des Millions, Échecs et Sudoku, en solo ou avec les téléphones.", Games.visible().size.let { n -> if (n > 1) "$n jeux" else "$n jeu" }, true) {
                startActivity(Intent(this, GamesActivity::class.java))
            },
            tile("downloads", R.drawable.ic_cb_telechargements, "Téléchargements", "Télécharger sur la TV (liens, magnet, torrent) : les fichiers rejoignent la bibliothèque.",
                if (internetUp) "aria2" else castbridge.core.ux.TvSignal.INTERNET_REQUIRED, false, warn = !internetUp) {
                startActivity(Intent(this, DownloadsActivity::class.java))
            },
            tile("remote", R.drawable.ic_cb_telecommande, "Télécommande", "Piloter la TV avec le téléphone ; option « toute la TV » (accessibilité).",
                if (RemoteAccessibilityService.instance != null) "Toute la TV" else "CastBridge", RemoteAccessibilityService.instance != null) {
                startActivity(Intent(this, RemoteSetupActivity::class.java))
            },
            tile("receive", R.drawable.ic_cb_recevoir_du_telephone, "Recevoir du téléphone", "Envoyer une vidéo depuis l'app CastBridge du téléphone.", "Code ${ParentalHub.shownPin(pin)}", true) { homeApi().openHelp() },
            tile("usb", R.drawable.ic_cb_cle_usb, "Clé USB", "Importer des vidéos d'une clé, ou y ranger les nouvelles.",
                if (drives.isEmpty()) "Aucune clé" else drives.joinToString { "${it.label} · ${it.free / (1L shl 30)} Go libres" }, drives.isNotEmpty()) {
                choose("Clé USB", listOf<Pair<String, () -> Unit>>(
                    "Importer les vidéos des clés détectées" to { usbMessage(usb?.importFromVolumes()) },
                    "Choisir un dossier de la clé…" to { usbMessage(usb?.launchPicker(this, REQ_TREE)) },
                    "Où ranger les nouveaux fichiers (${server?.target ?: "auto"})…" to { chooseTarget() },
                    "Re-détecter la clé (test de vitesse)" to { s?.rescanAsync(remeasure = true); flash("Détection de la clé en cours…") },
                    "Réglages de stockage de la TV" to { s?.openStorageSettings()?.let { flash(it) } },
                ) + (if (usb?.isRunning() == true) listOf<Pair<String, () -> Unit>>("Annuler l'import en cours" to { usb.cancel() }) else emptyList()))
            },
            tile("bluetooth", R.drawable.ic_cb_bluetooth, "Bluetooth", "Recevoir des fichiers et partager l'Internet du téléphone sans réseau commun.",
                st["1-bt"]?.substringAfter(": ")?.take(28) ?: "Désactivé", btOk) {
                choose("Bluetooth", listOf<Pair<String, () -> Unit>>(
                    "Ajouter un téléphone (recommandé)" to { PairActivity.open(this) },
                    "Rendre la TV visible (2 min) pour l'appairer" to { makeDiscoverable() }))
            },
            tile("internet", R.drawable.ic_cb_test_internet, "Internet", "Connectivité de la TV (Wi-Fi/Ethernet) et de la passerelle Bluetooth du téléphone : état et tests.",
                s?.takeIf { it.netCheckedAt > 0 }?.netSummary()?.take(34) ?: "Vérification…",
                s?.netDirectMs != null || s?.netGatewayMs != null) { internetMenu() },
            tile("wifi_direct", R.drawable.ic_cb_wifi_direct, "Wi-Fi Direct", "Un réseau direct TV ↔ téléphone, sans box.", if (wdOn) "Activé" else "Désactivé", wdOn) { toggleWifiDirect() },
            tile("admin", R.drawable.ic_cb_administration, "Administration", "Page web et SSH pour gérer la TV à distance.",
                if (sshOn) "SSH actif" else "SSH arrêté", sshOn) {
                choose("Administration à distance", listOf<Pair<String, () -> Unit>>(
                    (if (sshOn) "Désactiver SSH" else "Activer SSH (clés autorisées seulement)") to {
                        if (sshOn) { ssh?.disable(); flash("SSH désactivé") }
                        else Thread { runCatching { ssh?.enable() }.onFailure { e -> main.post { flash("SSH impossible : ${e.message}") } } }.start()
                    },
                    ((if (s?.btApi?.enabled != false) "Couper" else "Activer") + " l'API par Bluetooth (tout par Bluetooth, sans Wi-Fi)") to {
                        s?.btApi?.let { it.enable(!it.enabled); flash("API par Bluetooth : " + if (it.enabled) "activée" else "coupée (téléphones de confiance seulement)") }
                    },
                    "Adresse de la page web" to { flash("Ouvrez http://${TvService.localIp() ?: "?"}:${ReceiverServer.PORT} — code $pin") },
                ))
            },
            tile("updates", R.drawable.ic_cb_mises_a_jour, "Mises à jour", "Mises à jour automatiques depuis le serveur CastBridge : dernière vérification, version disponible, installer.",
                updateStatus(), TvConnect.link?.update?.manifest != null) { ServerActivity.open(this, ServerActivity.MODE_UPDATES) },
            tile("settings", R.drawable.ic_cb_reglages, "Connexion & réglages", "Code, adresse, démarrage avec la TV, lecture à distance…", null, false) { showSettings() },
            tile("dev_options", R.drawable.ic_cb_options_developpeur, "Options développeur", "Débogage USB / Wi-Fi de la TV.", null, false) { flash(openDevSettings()) },
            // Parental control (docs/PARENTAL.md): always reachable, even in kid mode; no usage event is sent for it
            HomeTool(R.drawable.ic_t_parental, "Contrôle parental", "Code parental, profils des enfants, horaires, vidéos adaptées à l'âge.",
                ParentalHub.tileStatus(), ParentalHub.engine.config().enabled) { startActivity(Intent(this, ParentalActivity::class.java)) },
            tile("help", R.drawable.ic_cb_aide, "Aide", "Comment envoyer une vidéo depuis le téléphone.", null, false) { homeApi().openHelp() },
        ))
    }

    /** "Connexion & réglages": connection facts in plain words, and every option (the former MENU list). */
    private fun showSettings() {
        if (!ParentalHub.allow(this, castbridge.core.parental.Category.SETTINGS)) return
        val s = svc ?: return
        val ip = TvService.localIp()
        val labels = mapOf("0-storage" to "Stockage", "1-bt" to "Bluetooth", "2-wd" to "Wi-Fi Direct (sans box)", "3-usb" to "Import depuis une clé",
            "4-ssh" to "Administration à distance (SSH)", "4-ssh-bt" to "SSH par Bluetooth", "4-api-bt" to "API par Bluetooth", "5-update" to "Installation d'applications",
            "5-notice" to "Dernier événement", "9-server" to "Serveur", "1-phone" to "Téléphone connecté")
        val sig = castbridge.core.ux.TvSignal.of(TvSignalViews.facts(this, s, server != null))
        val info = buildList {
            add("Signalétique : " + sig.text to (sig.action ?: castbridge.core.ux.TvSignal.LEGEND))
            sig.indicators.filter { it.kind != castbridge.core.ux.IndicatorKind.RECEPTION }.forEach { add(it.kind.label to (it.text + (it.action?.let { a -> " — $a" } ?: ""))) }
            add("Code de connexion (à saisir une fois sur le téléphone)" to ParentalHub.shownPin(pin))
            add("Téléphones de confiance (Bluetooth, sans code)" to s.trust.list().let { l -> if (l.isEmpty()) "aucun : menu « Ajouter un téléphone »" else l.joinToString(", ") { it.name } })
            add("Adresse de la TV" to (ip?.let { "$it:${ReceiverServer.PORT}   ·   page web : http://$it:${ReceiverServer.PORT}" } ?: "pas de réseau (Bluetooth ou Wi-Fi Direct possibles)"))
            add("Démarrage avec la TV" to if (prefs.getBool("autostart", true)) "oui" else "non")
            add("Lecture lancée depuis le téléphone" to if (s.overlayAllowed()) "s'ouvre toute seule" else "demande d'ouvrir l'app (autorisation « afficher par-dessus » non accordée)")
            statuses.toSortedMap().forEach { (k, v) -> add((labels[k] ?: k) to v.substringAfter(" : ", v)) }
            // « Assistance à distance : connectée / hors ligne / en attente d'acceptation des conditions » (silent in daily use, readable here; journal under the menu « À propos »)
            add(castbridge.core.tunnel.TunnelText.TITLE to (TunnelHub.statusLine(this@PlayerActivity).substringAfter(" : ") + (TunnelHub.detail(this@PlayerActivity)?.let { "\n$it" } ?: "")))
            TvConnect.link?.state?.let { st ->
                add("Identifiant de la TV (serveur CastBridge)" to (st.shortId ?: "pas encore enregistrée"))
                add("Serveur CastBridge" to (if (st.lastContactOk) "connecté" else st.lastContactMessage ?: "pas encore contacté") +
                    (if (st.lastContactAt > 0) " · dernier contact ${ServerActivity.date(st.lastContactAt)}" else ""))
            }
        }
        settingsPanel?.show(info, menuItems()); refreshStatusBar()
        enterScreen("settings")
    }

    /** Remembers where the current video stopped (normalised: see LibraryLogic.resumeFrom). */
    private fun savePosition() {
        val name = current?.name ?: return
        val s = snapshot; val size = currentSize
        if (s.posMs <= 0 && s.durMs <= 0) return
        bgRun { library?.db?.onStopped(name, size, s.posMs, s.durMs) }
    }

    private fun startedPlaying(name: String, size: Long) {
        currentSize = size
        bgRun { library?.db?.onPlayStarted(name, size) }
    }

    /** The library's view of the app: listing, thumbnails, and the app's own API on loopback for actions. */
    private fun libraryApi() = object : LibraryScreen.Api {
        override fun items() = ParentalHub.filterItems(server?.libraryItems().orEmpty())
        override fun volumes() = svc?.registry?.volumes().orEmpty().filter { it.writable }.map { it.id to it.label }
        override fun header() = "OK : lire   ·   MENU (ou OK maintenu) : actions   ·   RETOUR : accueil"
        override fun call(block: (castbridge.core.tv.TvClient) -> Unit): String? = try {
            block(castbridge.core.tv.TvClient("http://127.0.0.1:${ReceiverServer.PORT}", pin)); null
        } catch (e: castbridge.core.tv.TvClient.HttpError) {
            castbridge.core.tv.TvClient.str(e.message.orEmpty().substringAfter(": "), "message")
                ?: castbridge.core.tv.TvClient.str(e.message.orEmpty().substringAfter(": "), "error") ?: e.message
        } catch (e: Exception) { e.message ?: e.javaClass.simpleName }
        override fun flash(msg: String) { main.post { this@PlayerActivity.flash(msg) } }
        override fun playAll(names: List<String>, start: Int) { bgRun { runCatching { server?.playAll(names, start) } } }
    }

    // ---- Runtime permissions: every feature degrades cleanly when its permission is refused ----

    private fun requestRuntimePermissions() {
        val wanted = buildList {
            if (Build.VERSION.SDK_INT >= 31) { add(Manifest.permission.BLUETOOTH_CONNECT); add(Manifest.permission.BLUETOOTH_ADVERTISE) }
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)   // "ouvrez CastBridge TV" when asked from the phone
            // « Seul le Bluetooth » (docs/agent-reports/auto-wifi-direct.md): the automatic Wi-Fi Direct group needs it; same « Appareils à proximité » group as
            // BLUETOOTH_CONNECT (one answer for both), neverForLocation. Nothing starts a group until a trusted phone asks.
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (wanted.isEmpty()) svc?.onPermissionsReady()
        else runCatching { requestPermissions(wanted.toTypedArray(), REQ_PERMS) }.onFailure { svc?.onPermissionsReady() }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PERMS) svc?.onPermissionsReady()
        if (requestCode == REQ_WD) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) { prefs.putBool("wd_enabled", true); svc?.wd?.start() }
            else svc?.setStatus("2-wd", "Wi-Fi Direct : permission refusée (désactivé)")
        }
    }

    private fun toggleWifiDirect() {
        val g = svc?.wd ?: return
        if (prefs.getBool("wd_enabled", false)) {
            prefs.putBool("wd_enabled", false); g.stop(); flash("Wi-Fi Direct désactivé")
        } else if (g.hasPermission()) {
            prefs.putBool("wd_enabled", true); g.start()
        } else runCatching { requestPermissions(arrayOf(g.permission()), REQ_WD) }
    }

    // ---- MENU key: extra options that need a dialog ----

    private fun showMenu() {
        if (current == null) { showSettings(); return }
        if (!ParentalHub.allow(this, castbridge.core.parental.Category.SETTINGS)) return
        AlertDialog.Builder(this).setTitle("CastBridge TV")
            .setItems(menuItems().map { it.first }.toTypedArray()) { _, i -> menuItems()[i].second() }
            .setNegativeButton("Fermer", null).show()
    }

    private fun menuItems(): List<Pair<String, () -> Unit>> {
        val s = svc ?: return emptyList()
        val usb = s.usb; val ssh = s.ssh
        val items = mutableListOf<Pair<String, () -> Unit>>()
        if (current == null) items += "Toute la bibliothèque" to { showLibrary() }
        items += "Quiz culture générale (jouer avec les téléphones)" to { startActivity(Intent(this, QuizActivity::class.java)) }
        items += "Téléchargements" to { startActivity(Intent(this, DownloadsActivity::class.java)) }
        items += "Ajouter un téléphone / téléphones de confiance (${s.trust.list().size})…" to { PairActivity.open(this) }
        items += castbridge.core.trust.PhonesTexts.menuEntry(s.trust.list().size) to { PhonesActivity.open(this) }
        items += "Bluetooth : rendre la TV visible (2 min)" to { makeDiscoverable() }
        items += (if (prefs.getBool("wd_enabled", false)) "Wi-Fi Direct : désactiver" else "Wi-Fi Direct : activer (crée un réseau TV<->téléphone)") to { toggleWifiDirect() }
        items += "USB : importer les vidéos des clés détectées" to { usbMessage(usb?.importFromVolumes()) }
        items += "USB : choisir un dossier de la clé…" to { usbMessage(usb?.launchPicker(this, REQ_TREE)) }
        if (usb?.isRunning() == true) items += "USB : annuler l'import en cours" to { usb.cancel() }
        items += "Stockage : où ranger les nouveaux fichiers (${server?.target ?: "auto"})…" to { chooseTarget() }
        items += "Stockage : re-détecter la clé (test de vitesse)" to { s.rescanAsync(remeasure = true); flash("Détection de la clé en cours…") }
        items += "Stockage : choisir un dossier (sélecteur système)…" to { flash(pickSafFolder()) }
        if (prefs.getString("saf_tree") != null) items += "Stockage : oublier le dossier choisi" to { forgetSafFolder() }
        items += "Stockage : ouvrir les réglages de stockage de la TV" to { s.openStorageSettings()?.let { flash(it) } }
        val auto = prefs.getBool("autostart", true)
        items += (if (auto) "Démarrer avec la TV : oui (désactiver)" else "Démarrer avec la TV : non (activer)") to {
            prefs.putBool("autostart", !auto); flash(if (!auto) "CastBridge TV démarrera avec la TV" else "Démarrage automatique désactivé")
        }
        items += "Lecture à distance : autoriser l'affichage par-dessus les autres apps" + (if (s.overlayAllowed()) " (autorisé)" else "") to {
            s.openOverlaySettings(this)?.let { flash(it) }
        }
        items += "Tester Internet (ping et traceroute)" to {
            val host = "8.8.8.8"
            showDiag(host)
            Thread { svc?.gateway?.diagnose(host) { l -> main.post { appendDiag(l) } } ?: TvNetDiag.run(host, null) { l -> main.post { appendDiag(l) } } }.start()
        }
        items += "Mises à jour (serveur CastBridge)…" to { TvConnect.feature("updates", "menu"); ServerActivity.open(this, ServerActivity.MODE_UPDATES) }
        items += "Confidentialité : mes données, statistiques d'usage…" to { ServerActivity.open(this, ServerActivity.MODE_PRIVACY) }
        items += "Connexion au serveur (identifiant de la TV)…" to { ServerActivity.open(this, ServerActivity.MODE_CONNECTION) }
        items += "À propos : Assistance à distance (état et journal)…" to { showAssistance() }
        items += "Options développeur (débogage USB / Wi-Fi)" to { flash(openDevSettings()) }
        items += "Tester le relais Bluetooth (volume + puis −)" to {
            showDiag("Relais du service du fabricant")
            Thread { RemoteHub.testVendorRelay { l -> main.post { appendDiag(l) } } }.start()
        }
        items += (if (ssh?.running == true) "SSH : désactiver" else "SSH : activer (administration à distance, clés autorisées seulement)") to {
            if (ssh?.running == true) { ssh.disable(); flash("SSH désactivé") }
            else Thread { runCatching { ssh?.enable() }.onFailure { e -> main.post { flash("SSH impossible : ${e.message}") } } }.start()
        }
        return ParentalHub.wrapMenu(this, items)
    }

    /**
     * Opens the TV's developer options, or the screen where they are unlocked (tap "Build number" 7 times).
     * An app cannot switch developer mode on itself (that needs a system-level permission): it can only take
     * you to the right screen. Returns what to do next, for the on-screen message.
     */
    fun openDevSettings(): String {
        val enabled = runCatching { android.provider.Settings.Global.getInt(contentResolver, android.provider.Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1 }.getOrDefault(false)
        val adb = runCatching { android.provider.Settings.Global.getInt(contentResolver, android.provider.Settings.Global.ADB_ENABLED, 0) == 1 }.getOrDefault(false)
        val tries = buildList {
            if (enabled) add(Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS) to
                "Options développeur ouvertes. Débogage USB : ${if (adb) "activé" else "désactivé"}. Activez « Débogage USB » et, si présent, « Débogage sans fil ».")
            add(Intent(android.provider.Settings.ACTION_DEVICE_INFO_SETTINGS) to
                "Mode développeur inactif : appuyez 7 fois sur « Numéro de build » (ou « Version »), puis rouvrez ce menu.")
            add(Intent(android.provider.Settings.ACTION_SETTINGS) to
                "Réglages : cherchez « À propos » puis « Numéro de build » (7 appuis) pour débloquer les options développeur.")
        }
        for ((intent, msg) in tries) if (runCatching { startActivity(intent) }.isSuccess) return msg
        return "Réglages inaccessibles sur cette TV : ouvrez-les avec la télécommande de la TV."
    }

    // ---- Permanent status bar (core StatusIconModel, drawn by StatusBarView): Internet, phones, remote, SSH, gateway, USB, Wi-Fi Direct... ----
    private var statusBar: StatusBarView? = null

    private fun ensureStatusBar(): StatusBarView? {
        statusBar?.let { return it }
        val box = findViewById<android.widget.LinearLayout>(R.id.statusBar) ?: return null
        return StatusBarView(this, box, onOpen = { svc?.let { statusBar?.openPanel(it) } }, leaveFocus = { home?.takeIf { it.visible }?.headerChip?.requestFocus() }).also { statusBar = it }
    }

    /** Redraws the bar from the service's model; it takes the remote's focus only on the home screen (never over a video or another screen). */
    private fun refreshStatusBar() {
        val bar = ensureStatusBar() ?: return
        val s = svc ?: return
        bar.interactive = current == null && home?.visible == true && libScreen?.visible != true && settingsPanel?.visible != true
        bar.render(s.icons.snapshot())
    }

    // ---- Internet diagnostics panel (ping / traceroute), readable from the sofa ----
    private var diagView: TextView? = null
    private var diagDialog: AlertDialog? = null

    fun showDiag(host: String) {
        diagDialog?.dismiss()
        val tv = TextView(this).apply {
            typeface = android.graphics.Typeface.MONOSPACE; textSize = TvStyle.Type.CAPTION; setTextColor(TvStyle.TEXT)
            setPadding(32, 24, 32, 24); text = ""
        }
        val sv = android.widget.ScrollView(this).apply { addView(tv); setBackgroundColor(TvStyle.BG_ELEVATED) }
        diagView = tv
        diagDialog = AlertDialog.Builder(this).setTitle("Test Internet : $host").setView(sv)
            .setPositiveButton("Fermer", null).show()
    }

    fun appendDiag(line: String) {
        val tv = diagView ?: return
        tv.append(line + "\n")
        (tv.parent as? android.widget.ScrollView)?.post { (tv.parent as android.widget.ScrollView).fullScroll(View.FOCUS_DOWN) }
    }

    private fun usbMessage(m: String?) { if (m != null) { flash(m); svc?.setStatus("3-usb", "USB : $m") } }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_TREE && resultCode == RESULT_OK) data?.data?.let { usbMessage(svc?.usb?.importTree(it)) }
        if (requestCode == REQ_STORAGE_TREE) {
            val uri = data?.data
            if (resultCode != RESULT_OK || uri == null) { flash("Aucun dossier choisi"); return }
            val ok = runCatching {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }.isSuccess
            if (!ok) { flash("Ce dossier n'accorde pas d'autorisation durable : choisissez-en un autre"); return }
            prefs.putString("saf_tree", uri.toString())
            svc?.rescanAsync()
            flash("Dossier enregistré. Choisissez-le comme cible dans MENU > Stockage (envoi complet avant lecture).")
        }
    }

    fun makeDiscoverable() {
        runCatching {
            startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120))
        }.onFailure { flash("Réglage Bluetooth introuvable sur cette TV : associez le téléphone depuis les réglages de la TV") }
    }

    private fun flash(text: String) { if (::banner.isInitialized) banner.show(text) }

    // ---- Player (called from HTTP threads through the service: hop to the main thread and wait) ----

    private fun <T> onMain(block: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()
        var result: Result<T>? = null
        val latch = CountDownLatch(1)
        main.post { result = runCatching(block); latch.countDown() }
        if (!latch.await(5, TimeUnit.SECONDS)) throw IllegalStateException("player busy")
        return result!!.getOrThrow()
    }

    /** Decoder (MediaCodec first: software decoding of HD video is what eats RAM/CPU on ARMv7) and start position. */
    private fun configure(m: Media, posMs: Long) {
        val (hw, force) = extras.hwFlags()
        // R-16: the decision is pure (PlayerTuning.decide); hardware is forced only when the system's own probe says this TV can, and the app's software retry stays
        val t = castbridge.core.xfer.PlayerTuning.decide(tuningFacts(tuneStage))
        appliedTuning = t
        m.setHWDecoderEnabled(hw && t.hw, force || (hw && t.forceHw))
        if (t.dropLateFrames) m.addOption(":drop-late-frames")
        if (t.skipFrames) m.addOption(":skip-frames")
        if (t.threads > 0) m.addOption(":avcodec-threads=${t.threads}")
        if (t.skipLoopFilter > 0) m.addOption(":avcodec-skiploopfilter=${t.skipLoopFilter}")
        if (t.skipFrame > 0) m.addOption(":avcodec-skip-frame=${t.skipFrame}")
        if (t.skipIdct > 0) m.addOption(":avcodec-skip-idct=${t.skipIdct}")
        if (t.fileCachingMs > 0) m.addOption(":file-caching=${t.fileCachingMs}")
        // Picture quality (PictureQuality, pure): composed AFTER the tuning, distress wins; Natif adds no filter at all.
        pictureQuality = castbridge.core.tv.PictureQuality.decide(qualityFacts(tuneStage)).also { q -> q.options.forEach { m.addOption(it) } }
        if (posMs > 0) m.addOption(":start-time=${posMs / 1000.0}")
    }

    private var pictureQuality: castbridge.core.tv.PictureQuality.Quality? = null

    /** What libVLC 3.6 does not tell us (interlaced flag, bit depth) stays null: nothing is guessed. */
    private fun qualityFacts(distress: Int): castbridge.core.tv.PictureQuality.Facts {
        val codec = pbCodec
        val (w, h) = videoSize()
        val capable = castbridge.core.xfer.CodecMime.hwCapable(codec) { mime -> CodecCapabilityProbe.hardware(mime, w, h) }
        val hwOn = extras.hwMode() != "off"
        val (pw, ph) = panelSize()
        return castbridge.core.tv.PictureQuality.Facts(extras.fitMode(), codec, w, h, null, if (hwOn && capable == true) true else if (!hwOn || capable == false) false else null,
            if (!hwOn || capable == false) true else if (capable == true) false else null,
            Runtime.getRuntime().availableProcessors(), (server?.activeTransfers() ?: 0) > 0, distress, pw, ph)
    }

    /** The real size of the video surface (the panel as the app sees it); the display size while the layout is not measured yet. */
    private fun panelSize(): Pair<Int, Int> {
        val v = findViewById<View>(R.id.video)
        if (v != null && v.width > 0 && v.height > 0) return v.width to v.height
        val m = resources.displayMetrics
        return m.widthPixels to m.heightPixels
    }

    /** Facts of PlayerTuning for the file being opened ([distress]: 0 none, 1 frozen / lost frames, 2 lasting). */
    private fun tuningFacts(distress: Int): castbridge.core.xfer.PlayerTuning.Facts {
        val codec = pbCodec
        val (w, h) = videoSize()
        return castbridge.core.xfer.PlayerTuning.Facts(codec, w, h, extras.hwMode() != "off", (server?.activeTransfers() ?: 0) > 0, distress,
            Runtime.getRuntime().availableProcessors(), castbridge.core.xfer.CodecMime.hwCapable(codec) { mime -> CodecCapabilityProbe.hardware(mime, w, h) },
            Runtime.getRuntime().maxMemory(), android.os.Build.SUPPORTED_64_BIT_ABIS.isNotEmpty())
    }

    private fun videoSize(): Pair<Int, Int> =
        pbRes?.split('x')?.let { (it.getOrNull(0)?.toIntOrNull() ?: 0) to (it.getOrNull(1)?.toIntOrNull() ?: 0) } ?: (0 to 0)

    // ---- R-16: the picture's health (libVLC statistics, 1 Hz) and the copy badge ----

    private val statsTick = object : Runnable {
        override fun run() {
            if (mp == null || current == null) { statsTickOn = false; return }
            pollVideoStats()
            refreshBadge()
            main.postDelayed(this, 1000)
        }
    }
    private val badgeTick = Runnable { refreshBadge(true) }

    private fun startStats() {
        refreshBadge(true)
        if (statsTickOn) return
        statsTickOn = true; main.postDelayed(statsTick, 1000)
    }

    /** Feeds [health] with what libVLC counts (displayed / lost pictures), measures the CPU cost per picture and lightens the decoder once per distress level. */
    private fun pollVideoStats() {
        val p = mp ?: return
        val now = android.os.SystemClock.elapsedRealtime()
        val st = runCatching { p.media?.let { m -> try { m.stats } finally { runCatching { m.release() } } } }.getOrNull() ?: return
        val hasVideo = runCatching { p.videoTracksCount > 0 }.getOrDefault(true)
        if (p.isPlaying) health.onVideoStats(now, st.displayedPictures, st.lostPictures, hasVideo)
        lastShown = st.displayedPictures; lastLost = st.lostPictures
        val cpu = android.os.Process.getElapsedCpuTime()
        if (cpuBaseMs < 0 || st.displayedPictures < shownBase) { cpuBaseMs = cpu; shownBase = st.displayedPictures }
        else if (st.displayedPictures - shownBase >= 100) cpuPerFrame = (cpu - cpuBaseMs).toDouble() / (st.displayedPictures - shownBase)
        if (!decoderLogged && cpuPerFrame != null) { decoderLogged = true; logDecoder("playing") }
        val d = health.videoDistress(now)
        if (d > tuneStage) { tuneStage = d; retune(d) }
    }

    /** The picture froze or drops frames (or keeps doing so): re-open the file at the same position with lighter decoder options, at most twice per file. */
    private fun retune(distress: Int) {
        val p = mp ?: return; val r = reopen ?: return
        if (tuneReopens >= 2 || snapshot.state != "playing") return
        val next = castbridge.core.xfer.PlayerTuning.decide(tuningFacts(distress))
        if (next == appliedTuning) { logDecoder("distress=$distress, tuning unchanged"); return }
        tuneReopens++
        logDecoder("distress=$distress, reopening lighter")
        flash("Lecture allégée pour rester fluide")
        pbRetry = true
        runCatching { r(p.time.coerceAtLeast(0)) }
    }

    private fun decoderLines(): List<String> {
        val codec = pbCodec
        val (w, h) = videoSize()
        val capable = castbridge.core.xfer.CodecMime.hwCapable(codec) { mime -> CodecCapabilityProbe.hardware(mime, w, h) }
        val lines = castbridge.core.xfer.DecoderReport.lines(codec, w, h, appliedTuning, capable, cpuPerFrame, lastShown, lastLost, tuneStage).toMutableList()
        castbridge.core.xfer.CodecMime.mimeOf(codec)?.let { lines += CodecCapabilityProbe.describe(it) }
        return lines
    }

    private fun logDecoder(why: String) { android.util.Log.i("TvPlayer", "décodeur ($why) : " + decoderLines().joinToString(" | ")) }

    /** At most once a second (unless [force]d by a visible change): the badge shows the aggregate of every copy in progress, only over a playing video. */
    private fun refreshBadge(force: Boolean = false) {
        val b = badge ?: return
        val now = android.os.SystemClock.uptimeMillis()
        if (!force && now - lastBadgeAt < 1000) { main.removeCallbacks(badgeTick); main.postDelayed(badgeTick, 1000 - (now - lastBadgeAt)); return }
        lastBadgeAt = now
        val items = if (current != null && mp != null) svc?.reception?.active().orEmpty() else emptyList()
        b.render(CopyBadge.of(items, overlayVisible = ::bar.isInitialized && bar.shown))
    }

    private fun newFile(name: String, size: Long) {
        if (current?.name != name || currentSize != size) { swRetry = false; tuneStage = 0; tuneReopens = 0; appliedTuning = null }
    }

    override fun play(file: File, posMs: Long): Unit = onMain {
        savePosition()
        newFile(file.name, file.length())
        val onUsb = svc?.registry?.volumes()?.firstOrNull { file.absolutePath.startsWith(it.dir.absolutePath + "/") }?.kind == VolumeKind.REMOVABLE
        playbackBegin(if (onUsb) "usb" else "internal")
        current = file
        startedPlaying(file.name, file.length())
        extras.load(file.name, file.length(), file.parentFile)
        reopen = { pos -> play(file, pos) }
        val p = ensurePlayer(extras.wantsSpu())
        streamingName = null
        val m = Media(libVlc!!, file.absolutePath)
        // a copy writes to the disk this file is read from: more read-ahead rides out its write bursts (400 ms at rest, see PlaybackPriority)
        m.addOption(":file-caching=${castbridge.core.xfer.PlaybackPriority.fileCachingMs((server?.activeTransfers() ?: 0) > 0, Runtime.getRuntime().maxMemory())}")
        configure(m, posMs)
        p.media = m
        m.release()
        p.play()
        closeSafFd()
        hideScreens()
        enterScreen("player")
        snapshot = PlayerState("playing", file.name, posMs, 0); policyChanged()
        flash("▶ ${file.name}")
    }

    override fun playSaf(name: String, size: Long, posMs: Long): Unit = onMain {
        val store = svc?.volProvider?.saf ?: throw IllegalStateException("no folder chosen")
        val fd = store.openFd(name)                        // the file lives behind a ContentResolver: libVLC reads the descriptor
        savePosition()
        newFile(name, size)
        playbackBegin("usb")                               // a folder chosen through the system picker: in practice a drive
        current = File(svc!!.videosDir, name)              // only the name is used
        startedPlaying(name, size)
        extras.load(name, size, null)
        reopen = { pos -> playSaf(name, size, pos) }
        val p = ensurePlayer(extras.wantsSpu())
        streamingName = null
        val old = safPfd; safPfd = fd
        val m = Media(libVlc!!, fd.fileDescriptor)
        configure(m, posMs)
        p.media = m
        m.release()
        p.play()
        runCatching { old?.close() }
        hideScreens()
        enterScreen("player")
        snapshot = PlayerState("playing", name, posMs, 0); policyChanged()
        flash("▶ $name")
    }

    override fun playStream(url: String, name: String, posMs: Long): Unit = onMain {
        savePosition()
        val total = server?.progress(name)?.second ?: 0L
        newFile(name, total)
        playbackBegin(if (total > 0) "phone" else "stream")    // being uploaded by the phone, or a link played from it
        current = File(svc!!.videosDir, name)
        startedPlaying(name, total)
        extras.load(name, total, null)
        reopen = { pos -> playStream(url, name, pos) }
        val p = ensurePlayer(extras.wantsSpu())
        streamingName = name
        val m = Media(libVlc!!, Uri.parse(url))
        // a file still arriving: 3 s of cache rides out a write burst or a Wi-Fi dip; a plain link keeps 1.2 s (RAM)
        m.addOption(":network-caching=${castbridge.core.xfer.PlaybackPriority.networkCachingMs(growing = total > 0)}")
        m.addOption(":http-reconnect")         // the server cuts the link after 30 s without data: reconnect and wait again
        configure(m, posMs)
        p.media = m
        m.release()
        p.play()
        closeSafFd()
        hideScreens()
        enterScreen("player")
        snapshot = PlayerState("playing", name, posMs, 0); policyChanged()
        flash(if (total > 0) "▶ $name (lecture pendant l'envoi)" else "▶ $name")   // total 0: a link played from the phone
    }

    override fun pause() = onMain { mp?.let { if (it.isPlaying) { it.pause(); showBar("Pause"); savePosition() } }; Unit }
    override fun resume() = onMain { mp?.let { if (!it.isPlaying && current != null) { it.play(); showBar() } }; Unit }
    override fun seek(posMs: Long) = onMain {
        val p = mp
        if (p != null && current != null) {
            var t = posMs
            // Progressive playback: nothing exists beyond what has been received. Clamp instead of freezing.
            val prog = streamingName?.let { server?.progress(it) }
            if (prog != null && prog.first < prog.second) {
                val max = Progressive.reachableMs(snapshot.durMs, prog.first, prog.second)
                if (t > max) { t = maxOf(max, 0); flash("En attente de l'envoi… (atteignable : ${fmt(t)})") }
            }
            p.setTime(t); snapshot = snapshot.copy(posMs = t); showBar()
        }
    }
    override fun stop() = onMain {
        val wasPlaying = current != null
        playbackEnd()
        savePosition()
        current = null; snapshot = PlayerState(); releasePlayer(); reopen = null
        if (::extras.isInitialized) extras.forget()
        if (::bar.isInitialized) bar.hideNow()
        if (wasPlaying || libScreen?.visible == true) afterPlayback() else showHome()
    }
    override fun state(): PlayerState = snapshot.let { s ->
        if (s.state == "playing" || s.state == "buffering") s.copy(comfortSec = health.bufferSec(android.os.SystemClock.elapsedRealtime()), videoDistress = health.videoDistress(android.os.SystemClock.elapsedRealtime())) else s
    }

    override fun tracks(): PlayerTracks? = runCatching {
        onMain { val p = mp; if (p == null || current == null) null else extras.read(p, playerSpu, currentSize, snapshot.durMs) }
    }.getOrNull()

    override fun command(c: PlayerCommand): Boolean = onMain {
        val p = mp
        if (p == null || current == null) false else outcome(extras.apply(p, playerSpu, c) { d -> server?.playlistStep(d) == true }, c)
    }

    override fun subtitleFile(file: File): Boolean = onMain {
        val p = mp
        if (p == null || current == null) false else outcome(extras.subtitleFile(p, playerSpu, file), PlayerCommand.SubFile(file.name))
    }

    /** Applies what a setting change needs: nothing more, or re-opening the file at the same position (subtitles, decoder). */
    private fun outcome(r: PlayerExtras.Result, c: PlayerCommand): Boolean = when (r) {
        PlayerExtras.Result.Failed -> false
        PlayerExtras.Result.Done -> { flash(describe(c)); true }
        PlayerExtras.Result.Reopen -> {
            val pos = mp?.time ?: snapshot.posMs
            val again = reopen
            if (again == null) false else { releasePlayer(); again(pos); flash(describe(c)); true }
        }
    }

    private fun describe(c: PlayerCommand): String = when (c) {
        is PlayerCommand.Audio -> "Piste audio changée"
        is PlayerCommand.Subtitle -> if (c.id < 0) "Sous-titres désactivés" else "Sous-titres activés"
        is PlayerCommand.SubFile -> "Sous-titres : ${c.name}"
        is PlayerCommand.SubDelay -> "Décalage des sous-titres : ${PlayerParams.delayLabel(c.ms)}"
        is PlayerCommand.AudioDelay -> "Décalage audio : ${PlayerParams.delayLabel(c.ms)}"
        is PlayerCommand.SubScale -> "Taille des sous-titres : ${c.percent} %"
        is PlayerCommand.Rate -> "Vitesse : ${PlayerParams.rateLabel(c.rate)}"
        is PlayerCommand.Aspect -> "Format d'image : ${PlayerParams.aspectLabel(c.mode)}"
        is PlayerCommand.Chapter, is PlayerCommand.ChapterStep -> "Chapitre ${(mp?.chapter ?: 0) + 1}"
        is PlayerCommand.Title -> "Titre ${c.index + 1}"
        is PlayerCommand.Hw -> "Décodage : ${PlayerParams.hwLabel(c.mode)}"
        is PlayerCommand.Fit, is PlayerCommand.FitDefault -> {
            val m = extras.fitMode()
            "Affichage : ${castbridge.core.ux.DisplayTexts.label(m)}" + if (m == castbridge.core.tv.VideoFit.Mode.STRETCH) " (déforme l'image)" else ""
        }
        is PlayerCommand.Eq -> if (c.preset < 0) "Égaliseur désactivé" else "Égaliseur : ${runCatching { MediaPlayer.Equalizer.getPresetName(c.preset) }.getOrDefault("")}"
    }

    private fun showBar(extra: String = "") {
        val n = current?.name ?: return
        val s = snapshot
        val prog = streamingName?.let { server?.progress(it) }
        val reach = if (prog != null && prog.first < prog.second) Progressive.reachableMs(s.durMs, prog.first, prog.second) else -1
        bar.show(n, mp?.time ?: s.posMs, s.durMs, extra, reach)
    }

    private fun infoText(): String {
        val t = tracks() ?: return "Rien en lecture."
        val v = t.video
        return buildString {
            append("Fichier : ${current?.name}\n")
            v?.let {
                append("Vidéo : ${it.codec}, ${it.width}x${it.height}")
                if (it.fps > 0) append(String.format(java.util.Locale.ROOT, ", %.2f i/s", it.fps))
                if (it.bitrateBps > 0) append(", ${it.bitrateBps / 1000} kbit/s")
                append("\nDécodage : ${it.decoder}\n")
            }
            decoderLines().forEach { append(it).append('\n') }       // R-16: what was asked, what the TV can, what is only a hint
            append(extras.fitInfo()).append('\n')
            pictureQuality?.notes?.forEach { append(it).append('\n') }
            t.audioCodec?.let { append("Audio : $it\n") }
            append("Pistes audio : ${t.audio.size}, sous-titres : ${t.subtitles.count { it.id >= 0 } + t.subtitleFiles.size}\n")
            if (t.chapters.isNotEmpty()) append("Chapitres : ${t.chapters.size}\n")
            append("Vitesse : ${PlayerParams.rateLabel(t.rate)}   Format : ${PlayerParams.aspectLabel(t.aspect)}\n")
            append("Mémoire de l'app : ${android.os.Debug.getPss() / 1024} Mo")
        }
    }

    private fun panelApi() = object : PlayerPanel.Api {
        override fun tracks() = this@PlayerActivity.tracks()
        override fun command(c: PlayerCommand) = this@PlayerActivity.command(c)
        override fun general() = showMenu()
        override fun repeat(mode: String) = castbridge.core.tv.Playlist.Repeat.of(mode)?.let { server?.setRepeat(it) } == true
        override fun info() = infoText()
        override fun flash(msg: String) = this@PlayerActivity.flash(msg)
    }

    /** Cycles through the tracks of one kind (remote keys AUDIO / SUBTITLE). */
    private fun cycle(audio: Boolean) {
        val t = tracks() ?: return
        if (audio) {
            if (t.audio.size < 2) { flash("Une seule piste audio"); return }
            val i = (t.audio.indexOfFirst { it.id == t.audioId } + 1) % t.audio.size
            command(PlayerCommand.Audio(t.audio[i].id)); flash("Audio : ${t.audio[i].name}")
        } else {
            val all: List<Pair<String, PlayerCommand>> = t.subtitles.map { it.name to PlayerCommand.Subtitle(it.id) } +
                t.subtitleFiles.map { "Fichier : $it" to PlayerCommand.SubFile(it) }
            if (all.size < 2) { flash("Aucun sous-titre"); return }
            val cur = t.subtitles.indexOfFirst { it.id == t.subtitleId }.coerceAtLeast(0)
            val (label, cmd) = all[(cur + 1) % all.size]
            command(cmd); flash("Sous-titres : $label")
        }
    }

    // ---- Remote control ----

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (current == null) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                when {
                    settingsPanel?.visible == true -> { settingsPanel?.hide(); if (libScreen?.visible != true) showHome() }
                    libScreen?.visible == true -> showHome()
                    else -> moveTaskToBack(true)              // leave the home: the service keeps running
                }
                return true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP && home?.visible == true && currentFocus === home?.headerChip && statusBar?.focusFirst() == true) return true
            if (libScreen?.visible != true && keyCode in LIBRARY_KEYS) { showLibrary(); return true }
        }
        if (keyCode == KeyEvent.KEYCODE_MENU) { if (current != null && mp != null) panel.show() else showMenu(); return true }
        if (current == null) return super.onKeyDown(keyCode, event)
        when (keyCode) {
            KeyEvent.KEYCODE_INFO -> { showBar(); AlertDialog.Builder(this).setTitle("Informations").setMessage(infoText()).setPositiveButton("Fermer", null).show() }
            KeyEvent.KEYCODE_CAPTIONS -> cycle(audio = false)
            KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK -> cycle(audio = true)
            KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_CHANNEL_UP -> if (!command(PlayerCommand.ChapterStep(1))) flash("Pas de chapitre ni de fichier suivant")
            KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_CHANNEL_DOWN -> if (!command(PlayerCommand.ChapterStep(-1))) flash("Pas de chapitre ni de fichier précédent")
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ->
                if (mp?.isPlaying == true) pause() else resume()
            KeyEvent.KEYCODE_MEDIA_PLAY -> resume()
            KeyEvent.KEYCODE_MEDIA_PAUSE -> pause()
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> seek((mp?.time ?: 0) + 10_000)
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> seek(maxOf(0, (mp?.time ?: 0) - 10_000))
            KeyEvent.KEYCODE_DPAD_UP -> seek((mp?.time ?: 0) + 60_000)
            KeyEvent.KEYCODE_DPAD_DOWN -> seek(maxOf(0, (mp?.time ?: 0) - 60_000))
            KeyEvent.KEYCODE_MEDIA_STOP -> stop()
            KeyEvent.KEYCODE_BACK -> { stop(); return true }
            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }

    override fun onDestroy() {
        libScreen?.release(); home?.release(); thumbs?.release()
        svc?.detach(this)
        runCatching { unbindService(conn) }                  // the service keeps running (started, foreground)
        main.removeCallbacksAndMessages(null)                 // no Handler callback may outlive the activity
        releasePlayer()
        super.onDestroy()
    }

    companion object {
        private const val REQ_PERMS = 10
        private const val REQ_WD = 11
        private const val REQ_TREE = 12
        private const val REQ_STORAGE_TREE = 13
        /** Keys that open the library from the waiting screen (remotes differ: any of these). */
        private val LIBRARY_KEYS = setOf(KeyEvent.KEYCODE_GUIDE, KeyEvent.KEYCODE_BOOKMARK, KeyEvent.KEYCODE_PROG_BLUE,
            KeyEvent.KEYCODE_MEDIA_TOP_MENU, KeyEvent.KEYCODE_TV_CONTENTS_MENU, KeyEvent.KEYCODE_ALL_APPS)
        fun fmt(ms: Long): String { val s = ms / 1000; return "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) }
    }
}
