package castbridge.receiver

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
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
import castbridge.core.tv.Device
import castbridge.core.tv.PinGuard
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
    private lateinit var libVlc: LibVLC
    private lateinit var mp: MediaPlayer
    private lateinit var idle: TextView
    private lateinit var osd: TextView
    private var server: ReceiverServer? = null
    private var pin = ""
    private lateinit var guard: PinGuard
    private var bt: BtServer? = null
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
        libVlc = LibVLC(this, arrayListOf("--no-drop-late-frames", "--no-skip-frames", "--file-caching=1500"))
        mp = MediaPlayer(libVlc)
        mp.attachViews(findViewById<VLCVideoLayout>(R.id.video), null, false, false)
        mp.setEventListener { ev ->
            when (ev.type) {
                MediaPlayer.Event.Playing -> update("playing")
                MediaPlayer.Event.Paused -> update("paused")
                MediaPlayer.Event.TimeChanged -> snapshot = snapshot.copy(posMs = ev.timeChanged)
                MediaPlayer.Event.LengthChanged -> snapshot = snapshot.copy(durMs = ev.lengthChanged)
                MediaPlayer.Event.EndReached -> { update("ended"); showIdle() }
                MediaPlayer.Event.EncounteredError -> { update("error"); showIdle("Lecture impossible : ${current?.name}") }
            }
        }
        val dir = (getExternalFilesDir("videos") ?: File(filesDir, "videos")).also { videosDir = it }
        pin = TvPrefs(this).pin()
        guard = PinGuard(pin)
        server = ReceiverServer(dir, this, pin = pin, guard = guard, device = this).also {
            try { it.start(15_000, false) } catch (e: Exception) { Log.e(TAG, "server", e) }
        }
        register()
        bt = BtServer(this, dir, guard) { setStatus("1-bt", it) }
        requestRuntimePermissions()
        showIdle()
    }

    private fun update(state: String) {
        snapshot = snapshot.copy(state = state, name = current?.name)
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
    }

    private fun onPermissionsReady() { bt?.start() }

    // ---- MENU key: extra options that need a dialog ----

    private fun showMenu() {
        val items = mutableListOf<Pair<String, () -> Unit>>()
        items += "Bluetooth : rendre la TV visible (2 min)" to { makeDiscoverable() }
        AlertDialog.Builder(this).setTitle("CastBridge TV")
            .setItems(items.map { it.first }.toTypedArray()) { _, i -> items[i].second() }
            .setNegativeButton("Fermer", null).show()
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
        val m = Media(libVlc, file.absolutePath)
        if (posMs > 0) m.addOption(":start-time=${posMs / 1000.0}")
        mp.media = m
        m.release()
        mp.play()
        idle.visibility = View.GONE
        snapshot = PlayerState("playing", file.name, posMs, 0)
        flash("▶ ${file.name}")
    }

    override fun pause() = onMain { if (mp.isPlaying) { mp.pause(); flash("❚❚ Pause") } }
    override fun resume() = onMain { if (!mp.isPlaying && current != null) { mp.play(); flash("▶ Lecture") } }
    override fun seek(posMs: Long) = onMain {
        if (current != null) { mp.setTime(posMs); snapshot = snapshot.copy(posMs = posMs); flash("⇥ ${fmt(posMs)}") }
    }
    override fun stop() = onMain {
        mp.stop(); current = null; snapshot = PlayerState(); showIdle()
    }
    override fun state(): PlayerState = snapshot

    // ---- Device (called from HTTP threads; only public, permission-free APIs) ----

    override fun sysinfo(): SysInfo {
        val b = registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))  // sticky
        val present = b?.getBooleanExtra(android.os.BatteryManager.EXTRA_PRESENT, false) == true
        val level = b?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = b?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status = b?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1
        val ver = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "?"
        return SysInfo(
            model = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}".trim(),
            androidVersion = "${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})",
            ip = localIp(),
            batteryPct = if (present && level >= 0 && scale > 0) level * 100 / scale else null,
            charging = if (present) status == android.os.BatteryManager.BATTERY_STATUS_CHARGING || status == android.os.BatteryManager.BATTERY_STATUS_FULL else null,
            uptimeMs = android.os.SystemClock.elapsedRealtime(),
            appVersion = ver,
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
                if (mp.isPlaying) pause() else resume()
            KeyEvent.KEYCODE_MEDIA_PLAY -> resume()
            KeyEvent.KEYCODE_MEDIA_PAUSE -> pause()
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> seek(mp.time + 10_000)
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> seek(maxOf(0, mp.time - 10_000))
            KeyEvent.KEYCODE_DPAD_UP -> seek(mp.time + 60_000)
            KeyEvent.KEYCODE_DPAD_DOWN -> seek(maxOf(0, mp.time - 60_000))
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
        server?.stop()
        mp.stop(); mp.detachViews(); mp.release(); libVlc.release()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "CastBridgeTV"
        private const val REQ_PERMS = 10
        private val OSD = Any()
        fun fmt(ms: Long): String { val s = ms / 1000; return "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) }
        fun localIp(): String? = runCatching {
            NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }?.hostAddress
        }.getOrNull()
    }
}
