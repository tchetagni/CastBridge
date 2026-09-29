package castbridge.receiver

import android.app.Activity
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.TextView
import castbridge.core.tv.Player
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
class PlayerActivity : Activity(), Player {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var libVlc: LibVLC
    private lateinit var mp: MediaPlayer
    private lateinit var idle: TextView
    private lateinit var osd: TextView
    private var server: ReceiverServer? = null
    private var pin = ""
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
        val dir = getExternalFilesDir("videos") ?: File(filesDir, "videos")
        pin = TvPrefs(this).pin()
        server = ReceiverServer(dir, this, pin = pin).also {
            try { it.start(15_000, false) } catch (e: Exception) { Log.e(TAG, "server", e) }
        }
        register()
        showIdle()
    }

    private fun update(state: String) {
        snapshot = snapshot.copy(state = state, name = current?.name)
    }

    private fun showIdle(msg: String? = null) {
        idle.visibility = View.VISIBLE
        idle.text = (msg?.let { "$it\n\n" } ?: "") + "CastBridge TV\nEn attente du téléphone…\n\n${localIp() ?: "pas de réseau"}:${ReceiverServer.PORT}\nCode PIN : $pin"
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

    // ---- Remote control ----

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
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
        server?.stop()
        mp.stop(); mp.detachViews(); mp.release(); libVlc.release()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "CastBridgeTV"
        private val OSD = Any()
        fun fmt(ms: Long): String { val s = ms / 1000; return "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) }
        fun localIp(): String? = runCatching {
            NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }?.hostAddress
        }.getOrNull()
    }
}
