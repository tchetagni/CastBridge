package castbridge.receiver

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.TextView
import castbridge.core.tv.ApiExtension
import castbridge.core.tv.ApiReply
import castbridge.core.tv.Device
import castbridge.core.tv.PinGuard
import castbridge.core.tv.Progressive
import castbridge.core.tv.Storage
import castbridge.core.tv.Player
import castbridge.core.tv.SysInfo
import castbridge.core.tv.PlayerState
import castbridge.core.tv.ReceiverServer
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * CastBridge TV: receives whole videos from the phone (see docs/NEXT-preload.md) and plays them
 * from local storage with libVLC, so playback survives the phone leaving the network.
 */
class PlayerActivity : Activity(), Player, Device {
    private val main = Handler(Looper.getMainLooper())
    // libVLC is created on the first play() and fully released on stop / end of media / low memory:
    // the HTTP server and the waiting screen do not need it (see docs/ADMIN.md, "Mémoire").
    private var libVlc: LibVLC? = null
    private var mp: MediaPlayer? = null
    private lateinit var idle: TextView
    private lateinit var osd: TextView
    private lateinit var lead: TextView
    @Volatile private var streamingName: String? = null    // set while playing a file that may still be arriving
    private var lastLeadUpdate = 0L
    private var server: ReceiverServer? = null
    private var pin = ""
    private lateinit var guard: PinGuard
    private var bt: BtServer? = null
    private var wd: WifiDirectGroup? = null
    private var usb: UsbImporter? = null
    private var ssh: SshControl? = null
    private lateinit var prefs: TvPrefs
    private lateinit var videosDir: File
    private val statuses = java.util.concurrent.ConcurrentHashMap<String, String>()
    private var idleMsg: String? = null
    private var nsd: NsdManager? = null
    private var nsdListener: NsdManager.RegistrationListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    // Snapshot read by HTTP threads; written on the main thread from libVLC events.
    @Volatile private var snapshot = PlayerState()
    private var current: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)
        idle = findViewById(R.id.idle)
        osd = findViewById(R.id.osd)
        lead = findViewById(R.id.lead)
        val dir = (getExternalFilesDir("videos") ?: File(filesDir, "videos")).also { videosDir = it }
        prefs = TvPrefs(this)
        pin = prefs.pin()
        guard = PinGuard(pin)
        val profile = prefs.profile()
        logResources(dir, profile)
        server = ReceiverServer(dir, this, pin = pin, guard = guard, device = this, extension = ApiExtension(::extraApi),
            profile = profile, onSettings = { prefs.saveProfile(it) }).also {
            try { it.start(15_000, false) } catch (e: Exception) { Log.e(TAG, "server", e) }
        }
        register()
        bt = BtServer(this, dir, guard) { setStatus("1-bt", it) }
        wd = WifiDirectGroup(this, prefs) { setStatus("2-wd", it) }
        usb = UsbImporter(this, dir) { setStatus("3-usb", it) }
        ssh = SshControl(this) { setStatus("4-ssh", it) }
        requestRuntimePermissions()
        showIdle()
    }

    /** One line of sizes in logcat (no secrets), to follow RAM/flash use on a small TV. */
    private fun logResources(dir: File, p: castbridge.core.tv.TvProfile) {
        val am = getSystemService(android.app.ActivityManager::class.java)
        Log.i(TAG, "profile: videos used=${Storage.used(dir) shr 20}MB free=${dir.usableSpace shr 20}MB quota=${Storage.quota(dir, p) shr 20}MB " +
            "heap=${Runtime.getRuntime().maxMemory() shr 20}MB memClass=${am.memoryClass}MB lowRam=${am.isLowRamDevice} " +
            "pss=${android.os.Debug.getPss() / 1024}MB")
    }

    private fun update(state: String) {
        snapshot = snapshot.copy(state = state, name = current?.name ?: snapshot.name)
    }

    private fun ensurePlayer(): MediaPlayer {
        mp?.let { return it }
        val lv = LibVLC(this, arrayListOf(
            "--no-drop-late-frames", "--no-skip-frames",
            "--file-caching=400",            // local file: 1500 ms of read-ahead only cost RAM
            "--no-audio-time-stretch",       // no resampling buffers for A/V drift
            "--no-spu", "--no-sub-autodetect-file", "--no-osd", "--no-stats",   // subtitles/OSD/stats unused by this app
        ))
        val p = MediaPlayer(lv)
        p.attachViews(findViewById<VLCVideoLayout>(R.id.video), null, false, false)
        p.setEventListener { ev ->
            when (ev.type) {
                MediaPlayer.Event.Playing -> update("playing")
                MediaPlayer.Event.Paused -> update("paused")
                MediaPlayer.Event.TimeChanged -> { snapshot = snapshot.copy(posMs = ev.timeChanged); main.post { updateLead() } }
                MediaPlayer.Event.Buffering -> {
                    // libVLC pauses by itself when the data runs out (playback caught up with the upload) and resumes alone.
                    val st = snapshot.state
                    if (ev.buffering < 100f && st == "playing") { update("buffering"); main.post { flash("Mise en mémoire tampon… (en attente de l'envoi)") } }
                    else if (ev.buffering >= 100f && st == "buffering") update("playing")
                }
                MediaPlayer.Event.LengthChanged -> snapshot = snapshot.copy(durMs = ev.lengthChanged)
                // Never release from inside libVLC's own event thread: hop to the main thread.
                MediaPlayer.Event.EndReached -> {
                    update("ended"); val n = current?.name
                    main.post { current = null; streamingName = null; releasePlayer(); n?.let { server?.onPlaybackEnded(it) }; showIdle() }
                }
                MediaPlayer.Event.EncounteredError -> {
                    update("error"); val n = current?.name
                    main.post { current = null; streamingName = null; releasePlayer(); showIdle("Lecture impossible : $n") }
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

    private fun releasePlayer() {
        val p = mp; val lv = libVlc
        mp = null; libVlc = null; streamingName = null
        if (::lead.isInitialized) lead.visibility = View.GONE
        runCatching { p?.setEventListener(null) }
        runCatching { p?.stop() }
        runCatching { p?.detachViews() }
        runCatching { p?.release() }
        runCatching { lv?.release() }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Not playing (paused/ended/idle) and the system wants memory back: give the whole player back.
        if (level >= TRIM_MEMORY_UI_HIDDEN && snapshot.state != "playing" && mp != null) {
            current = null; snapshot = PlayerState(); releasePlayer()
        }
    }

    private fun showIdle(msg: String? = idleMsg) {
        idleMsg = msg
        idle.visibility = View.VISIBLE
        idle.text = (msg?.let { "$it\n\n" } ?: "") +
            "CastBridge TV\nEn attente du téléphone…\n\n${localIp() ?: "pas de réseau"}:${ReceiverServer.PORT}\nCode PIN : $pin\n\n" +
            statuses.toSortedMap().values.joinToString("\n") + "\n\nMENU : options (USB, Bluetooth…)"
    }

    /** Status line of a channel (Bluetooth, Wi-Fi Direct, USB, SSH), shown on the waiting screen. Thread-safe. */
    fun setStatus(key: String, text: String?) {
        if (text == null) statuses.remove(key) else statuses[key] = text
        main.post { if (::idle.isInitialized && idle.visibility == View.VISIBLE) showIdle() }
    }

    // ---- Runtime permissions: every feature degrades cleanly when its permission is refused ----

    private fun requestRuntimePermissions() {
        val wanted = buildList {
            if (Build.VERSION.SDK_INT >= 31) { add(Manifest.permission.BLUETOOTH_CONNECT); add(Manifest.permission.BLUETOOTH_ADVERTISE) }
        }.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (wanted.isEmpty()) onPermissionsReady()
        else runCatching { requestPermissions(wanted.toTypedArray(), REQ_PERMS) }.onFailure { onPermissionsReady() }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PERMS) onPermissionsReady()
        if (requestCode == REQ_WD) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) { prefs.putBool("wd_enabled", true); wd?.start() }
            else setStatus("2-wd", "Wi-Fi Direct : permission refusée (désactivé)")
        }
    }

    private fun onPermissionsReady() {
        bt?.start()
        // Wi-Fi Direct is opt-in (MENU): creating a group can disturb the TV's own Wi-Fi connection.
        if (prefs.getBool("wd_enabled", false) && wd?.hasPermission() == true) wd?.start()
    }

    private fun toggleWifiDirect() {
        val g = wd ?: return
        if (prefs.getBool("wd_enabled", false)) {
            prefs.putBool("wd_enabled", false); g.stop(); flash("Wi-Fi Direct désactivé")
        } else if (g.hasPermission()) {
            prefs.putBool("wd_enabled", true); g.start()
        } else runCatching { requestPermissions(arrayOf(g.permission()), REQ_WD) }
    }

    // ---- MENU key: extra options that need a dialog ----

    private fun showMenu() {
        val items = mutableListOf<Pair<String, () -> Unit>>()
        items += "Bluetooth : rendre la TV visible (2 min)" to { makeDiscoverable() }
        items += (if (prefs.getBool("wd_enabled", false)) "Wi-Fi Direct : désactiver" else "Wi-Fi Direct : activer (crée un réseau TV<->téléphone)") to { toggleWifiDirect() }
        items += "USB : importer les vidéos des clés détectées" to { usbMessage(usb?.importFromVolumes()) }
        items += "USB : choisir un dossier de la clé…" to { usbMessage(usb?.launchPicker(REQ_TREE)) }
        if (usb?.isRunning() == true) items += "USB : annuler l'import en cours" to { usb?.cancel() }
        items += (if (ssh?.running == true) "SSH : désactiver" else "SSH : activer (administration à distance, clés autorisées seulement)") to {
            val c = ssh
            if (c?.running == true) { c.disable(); flash("SSH désactivé") }
            else Thread { runCatching { c?.enable() }.onFailure { e -> main.post { flash("SSH impossible : ${e.message}") } } }.start()
        }
        AlertDialog.Builder(this).setTitle("CastBridge TV")
            .setItems(items.map { it.first }.toTypedArray()) { _, i -> items[i].second() }
            .setNegativeButton("Fermer", null).show()
    }

    private fun usbMessage(m: String?) { if (m != null) { flash(m); setStatus("3-usb", "USB : $m") } }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_TREE && resultCode == RESULT_OK) data?.data?.let { usbMessage(usb?.importTree(it)) }
    }

    /** Extra authenticated API routes (USB import status/trigger). */
    private fun extraApi(path: String, method: String, params: Map<String, String>): ApiReply? = when {
        path.startsWith("/api/ssh") -> ssh?.api(path, method, params)
        path == "/api/usb" && method == "GET" -> ApiReply(200, usbJson())
        path == "/api/usb/import" && method == "POST" -> {
            val m = usb?.importFromVolumes() ?: "indisponible"
            ApiReply(200, usbJson(m))
        }
        else -> null
    }

    private fun usbJson(msg: String? = null): String {
        val u = usb
        return "{\"running\":${u?.isRunning() == true},\"message\":${ReceiverServer.q(msg ?: u?.message ?: "")}," +
            "\"volumes\":[${u?.volumeRoots().orEmpty().joinToString(",") { ReceiverServer.q(it.absolutePath) }}]}"
    }

    private fun makeDiscoverable() {
        runCatching {
            startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120))
        }.onFailure { flash("Réglage Bluetooth introuvable sur cette TV : associez le téléphone depuis les réglages de la TV") }
    }

    private fun flash(text: String) {
        osd.text = text
        osd.visibility = View.VISIBLE
        main.removeCallbacksAndMessages(OSD)
        main.postAtTime({ osd.visibility = View.GONE }, OSD, android.os.SystemClock.uptimeMillis() + 2500)
    }

    // ---- Player (called from HTTP threads: hop to the main thread and wait) ----

    private fun <T> onMain(block: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()
        var result: Result<T>? = null
        val latch = CountDownLatch(1)
        main.post { result = runCatching(block); latch.countDown() }
        if (!latch.await(5, TimeUnit.SECONDS)) throw IllegalStateException("player busy")
        return result!!.getOrThrow()
    }

    override fun play(file: File, posMs: Long) = onMain {
        current = file
        streamingName = null
        val p = ensurePlayer()
        val m = Media(libVlc!!, file.absolutePath)
        m.setHWDecoderEnabled(true, false)   // MediaCodec first: software decoding of HD video is what eats RAM/CPU on ARMv7
        if (posMs > 0) m.addOption(":start-time=${posMs / 1000.0}")
        p.media = m
        m.release()
        p.play()
        idle.visibility = View.GONE
        snapshot = PlayerState("playing", file.name, posMs, 0)
        flash("▶ ${file.name}")
    }

    override fun playStream(url: String, name: String, posMs: Long) = onMain {
        current = File(videosDir, name)
        streamingName = name
        val p = ensurePlayer()
        val m = Media(libVlc!!, Uri.parse(url))
        m.setHWDecoderEnabled(true, false)
        m.addOption(":network-caching=1200")   // enough to ride out upload hiccups, still modest in RAM
        m.addOption(":http-reconnect")         // the server cuts the link after 30 s without data: reconnect and wait again
        if (posMs > 0) m.addOption(":start-time=${posMs / 1000.0}")
        p.media = m
        m.release()
        p.play()
        idle.visibility = View.GONE
        snapshot = PlayerState("playing", name, posMs, 0)
        flash("▶ $name (lecture pendant l'envoi)")
    }

    override fun pause() = onMain { mp?.let { if (it.isPlaying) { it.pause(); flash("❚❚ Pause") } }; Unit }
    override fun resume() = onMain { mp?.let { if (!it.isPlaying && current != null) { it.play(); flash("▶ Lecture") } }; Unit }
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
            p.setTime(t); snapshot = snapshot.copy(posMs = t); flash("⇥ ${fmt(t)}")
        }
    }
    override fun stop() = onMain {
        current = null; snapshot = PlayerState(); releasePlayer(); showIdle()
    }
    override fun state(): PlayerState = snapshot

    // ---- Device (called from HTTP threads; only public, permission-free APIs) ----

    override fun sysinfo(): SysInfo {
        val b = registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))  // sticky
        val present = b?.getBooleanExtra(android.os.BatteryManager.EXTRA_PRESENT, false) == true
        val level = b?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = b?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status = b?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1
        val mem = runCatching {
            android.app.ActivityManager.MemoryInfo().also { getSystemService(android.app.ActivityManager::class.java).getMemoryInfo(it) }
        }.getOrNull()
        val ver = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "?"
        return SysInfo(
            model = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}".trim(),
            androidVersion = "${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})",
            ip = localIp(),
            batteryPct = if (present && level >= 0 && scale > 0) level * 100 / scale else null,
            charging = if (present) status == android.os.BatteryManager.BATTERY_STATUS_CHARGING || status == android.os.BatteryManager.BATTERY_STATUS_FULL else null,
            uptimeMs = android.os.SystemClock.elapsedRealtime(),
            appVersion = ver,
            pssMb = (android.os.Debug.getPss() / 1024).toInt(),
            memAvailMb = mem?.let { (it.availMem shr 20).toInt() },
            memTotalMb = mem?.let { (it.totalMem shr 20).toInt() },
            lowMemory = mem?.lowMemory,
        )
    }

    private val audio by lazy { getSystemService(AudioManager::class.java) }

    override fun volume(): Int? = runCatching {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (max > 0) audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max else null
    }.getOrNull()

    override fun setVolume(pct: Int) {
        // Fixed-volume TVs (HDMI-CEC / external amp) may ignore or refuse this: nothing more an app can do.
        runCatching {
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(pct * max / 100f), 0)
        }
        main.post { flash("Volume ${volume() ?: pct} %") }
    }

    /** Restarts the app UI, server and player inside the same process (an app cannot reboot the TV). */
    override fun restartApp() { main.post { recreate() } }

    // ---- Remote control ----

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU) { showMenu(); return true }
        if (current == null) return super.onKeyDown(keyCode, event)
        when (keyCode) {
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

    // ---- mDNS announce: _castbridge._tcp with role=receiver ----

    private fun register() {
        multicastLock = runCatching {
            (applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
                .createMulticastLock("castbridge-tv").apply { setReferenceCounted(false); acquire() }
        }.getOrNull()
        val info = NsdServiceInfo().apply {
            serviceName = "CastBridge TV " + (android.os.Build.MODEL ?: "")
            serviceType = ReceiverServer.SERVICE_TYPE
            port = ReceiverServer.PORT
            setAttribute("role", "receiver")
            setAttribute("v", "0.2")
        }
        val l = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(i: NsdServiceInfo) { Log.i(TAG, "mDNS ${i.serviceName}") }
            override fun onRegistrationFailed(i: NsdServiceInfo, e: Int) { Log.w(TAG, "mDNS failed $e") }
            override fun onServiceUnregistered(i: NsdServiceInfo) {}
            override fun onUnregistrationFailed(i: NsdServiceInfo, e: Int) {}
        }
        nsd = getSystemService(NsdManager::class.java)
        runCatching { nsd?.registerService(info, NsdManager.PROTOCOL_DNS_SD, l); nsdListener = l }
    }

    override fun onDestroy() {
        runCatching { nsdListener?.let { nsd?.unregisterService(it) } }
        runCatching { multicastLock?.release() }
        bt?.stop()
        wd?.stop()
        ssh?.stop()
        server?.stop()
        main.removeCallbacksAndMessages(null)     // no Handler callback may outlive the activity
        releasePlayer()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "CastBridgeTV"
        private const val REQ_PERMS = 10
        private const val REQ_WD = 11
        private const val REQ_TREE = 12
        private val OSD = Any()
        fun fmt(ms: Long): String { val s = ms / 1000; return "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) }
        fun localIp(): String? = runCatching {
            NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }?.hostAddress
        }.getOrNull()
    }
}
