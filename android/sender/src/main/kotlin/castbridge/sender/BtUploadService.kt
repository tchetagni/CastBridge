package castbridge.sender

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import castbridge.core.tv.BtProtocol
import castbridge.core.tv.Link
import castbridge.core.tv.LinkPlanner
import castbridge.core.tv.ResumableBtUpload
import castbridge.core.tv.ResumableUpload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import kotlin.concurrent.thread

/** Sends a file to CastBridge TV over Bluetooth (RFCOMM), resuming after link loss. Foreground service. */
@SuppressLint("MissingPermission")   // BLUETOOTH_CONNECT is checked by the UI before starting
class BtUploadService : Service() {
    @Volatile private var cancelled = false
    @Volatile private var myToken = 0L
    private var worker: Thread? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) { cancelled = true; return START_NOT_STICKY }
        val uri = intent?.data
        val address = intent?.getStringExtra(EXTRA_ADDR)
        val pin = intent?.getStringExtra(EXTRA_PIN)
        val name = intent?.getStringExtra(EXTRA_NAME)
        val token = intent?.getLongExtra(UploadService.EXTRA_TOKEN, 0L) ?: 0L
        if (uri == null || address == null || pin == null || name == null) { UploadService.slot.release(token); stopSelf(); return START_NOT_STICKY }
        if (worker?.isAlive == true) {
            // R-09: never dropped in silence, never leaves Android waiting for startForeground
            runCatching { val n = notification("Envoi Bluetooth en cours…", 0)
                if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE) else startForeground(NOTIF, n) }
            // the reservation of this start is given back (review A: it used to be kept forever) and the queue starts the file again in a moment
            if (UploadService.slot.release(token)) _state.value = ResumableUpload.State.Failed(castbridge.core.tv.QueueTexts.PREVIOUS_ENDING)
            return START_NOT_STICKY
        }
        try {
            val n = notification("Envoi Bluetooth de $name…", 0)
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            else startForeground(NOTIF, n)
        } catch (e: Exception) {
            Log.e(TAG, "startForeground", e)
            _state.value = ResumableUpload.State.Failed(UploadService.REFUSED)
            UploadService.slot.release(token)
            stopSelf(); return START_NOT_STICKY
        }
        cancelled = false
        myToken = token
        current = this
        val allowWd = intent.getBooleanExtra(EXTRA_WD, true)
        worker = thread(name = "bt-upload") { run(uri, address, pin, name, allowWd) }
        return START_NOT_STICKY
    }

    private fun run(uri: Uri, address: String, pin: String, name: String, allowWd: Boolean) {
        val total = runCatching { contentResolver.openFileDescriptor(uri, "r")!!.use { it.statSize } }.getOrDefault(-1)
        if (total <= 0) { finish(ResumableUpload.State.Failed("Fichier illisible")); return }
        val adapter = getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null || !adapter.isEnabled) { finish(ResumableUpload.State.Failed("Bluetooth désactivé")); return }
        castStart = System.currentTimeMillis(); castTotal = total; castChannel = "bluetooth"
        PhoneConnect.track("cast_start", mapOf("channel" to "bluetooth", "mode" to "copy", "bytes" to total))
        var lastNotif = 0L
        fun connect(): Link {
            runCatching { adapter.cancelDiscovery() }   // needs BLUETOOTH_SCAN on Android 12+: optional, never fatal
            val sock = adapter.getRemoteDevice(address).createRfcommSocketToServiceRecord(UUID.fromString(BtProtocol.SERVICE_UUID))
            // R-32 (audit I-10): one connect() at a time to the same TV (the lock shared with the remote, the pipe, the owner channel and the trusted link)
            try { synchronized(castbridge.core.tunnel.BtConnectLock.of(address)) { sock.connect() } } catch (e: IOException) { runCatching { sock.close() }; throw e }
            return object : Link {
                override val input = sock.inputStream
                override val output = sock.outputStream
                override fun close() { runCatching { sock.close() } }
            }
        }
        // Bluetooth first as the control link: ask the TV for a faster way (same Wi-Fi, or its Wi-Fi Direct group).
        _route.value = "Recherche du lien le plus rapide…"
        val wd = allowWd && Build.VERSION.SDK_INT >= 29
        val info = runCatching { connect().use { l -> BtProtocol.negotiate(l.input, l.output, castbridge.core.trust.TvAuth.btPin(pin), wantWifiDirect = wd) } }
            .onFailure { Log.i(TAG, "negotiate: ${it.javaClass.simpleName} ${it.message}") }.getOrNull()   // older TV (ERR_MAGIC) or no answer: Bluetooth
        val routes = LinkPlanner.plan(info, ::reachable, canJoinWifiDirect = wd)
        for (r in routes) {
            if (cancelled) break
            val res = when (r) {
                is LinkPlanner.Route.Lan -> { _route.value = r.label; castChannel = "wifi"; httpUpload(uri, name, total, r.base, pin, name) }
                is LinkPlanner.Route.Direct -> {
                    _route.value = "${r.label} : connexion au réseau de la TV (validez sur le téléphone)…"
                    val joined = joinWifiDirect(r.ssid, r.pass)
                    if (joined == null) null else try { _route.value = r.label; castChannel = "wifidirect"; httpUpload(uri, name, total, r.base, pin, name) } finally { leaveWifiDirect(joined) }
                }
                LinkPlanner.Route.Bluetooth -> null                // below
                is LinkPlanner.Route.BluetoothTunnel -> null       // not planned here: simple sends keep CBT1 (the tunnel serves the other screens)
            }
            if (res == ResumableUpload.State.Done || (res is ResumableUpload.State.Failed && res.reason != "liaison perdue")) { finish(res); return }
            if (r == LinkPlanner.Route.Bluetooth) break
        }
        _route.value = "Bluetooth"
        castChannel = "bluetooth"
        val up = ResumableBtUpload(name, total, castbridge.core.trust.TvAuth.btPin(pin),
            connect = { connect() },
            openAt = { off -> openAt(uri, off) },
            cancelled = { cancelled })
        val result = up.run { s ->
            pub(s)
            val now = System.currentTimeMillis()
            if (now - lastNotif > 1000) {
                lastNotif = now
                val pct = when (s) {
                    is ResumableUpload.State.Uploading -> (s.sent * 100 / s.total).toInt()
                    is ResumableUpload.State.Waiting -> (s.sent * 100 / s.total).toInt()
                    else -> 0
                }
                runCatching { getSystemService(NotificationManager::class.java).notify(NOTIF, notification("Envoi Bluetooth de $name", pct)) }
            }
        }
        finish(result)
    }

    /** A TV address answers (same network as the phone)? */
    private fun reachable(base: String): Boolean = runCatching {
        val c = java.net.URL("$base/api/hello").openConnection() as java.net.HttpURLConnection
        c.connectTimeout = 1500; c.readTimeout = 1500
        c.responseCode == 200 && c.inputStream.use { it.readBytes() }.decodeToString().contains("castbridge-tv")
    }.getOrDefault(false)

    /** The same resumable HTTP upload as over Wi-Fi, full speed; gives up after a few failures in a row to fall back to Bluetooth. */
    private fun httpUpload(uri: Uri, name: String, total: Long, base: String, pin: String, label: String): ResumableUpload.State {
        var lastNotif = 0L
        return ResumableUpload(name, total, { base }, { off -> openAt(uri, off) }, { cancelled }, pin = pin, giveUpAfter = 6).run { s ->
            pub(s)
            val now = System.currentTimeMillis()
            if (now - lastNotif > 1000 && s is ResumableUpload.State.Uploading) {
                lastNotif = now
                runCatching { getSystemService(NotificationManager::class.java).notify(NOTIF, notification("Envoi de $label (Wi-Fi)", (s.sent * 100 / s.total).toInt())) }
            }
        }
    }

    /**
     * Joins the TV's Wi-Fi Direct group (Android shows its approval dialog); null if refused. Only the sockets towards the group go through its network ([GroupNetworkRoute] /
     * [castbridge.core.net.BoundRoute], one socket at a time): the rest of the app keeps its Internet during the join (R-29; `bindProcessToNetwork` used to cut it).
     */
    private fun joinWifiDirect(ssid: String, pass: String): android.net.ConnectivityManager.NetworkCallback? {
        if (Build.VERSION.SDK_INT < 29) return null
        val cm = getSystemService(android.net.ConnectivityManager::class.java)
        val spec = runCatching { android.net.wifi.WifiNetworkSpecifier.Builder().setSsid(ssid).setWpa2Passphrase(pass).build() }.getOrNull() ?: return null
        val req = android.net.NetworkRequest.Builder().addTransportType(android.net.NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET).setNetworkSpecifier(spec).build()
        val got = java.util.concurrent.CountDownLatch(1)
        var ok = false
        val cb = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) { groupRoute = GroupNetworkRoute.install(network); ok = true; got.countDown() }
            override fun onUnavailable() { got.countDown() }
        }
        return try {
            cm.requestNetwork(req, cb, 45_000)
            got.await(50, java.util.concurrent.TimeUnit.SECONDS)
            if (ok) cb else { leaveWifiDirect(cb); null }
        } catch (e: Exception) { Log.i(TAG, "wifi direct: ${e.javaClass.simpleName}"); null }
    }

    /** The route to the joined group's addresses (null when no group is joined by this service). */
    @Volatile private var groupRoute: castbridge.core.net.BoundRoute.Binding? = null

    private fun leaveWifiDirect(cb: android.net.ConnectivityManager.NetworkCallback) {
        val cm = getSystemService(android.net.ConnectivityManager::class.java)
        GroupNetworkRoute.release(groupRoute); groupRoute = null
        runCatching { cm.unregisterNetworkCallback(cb) }
    }

    private fun openAt(uri: Uri, offset: Long): InputStream {
        val pfd = contentResolver.openFileDescriptor(uri, "r") ?: throw IOException("cannot open")
        val fis = object : FileInputStream(pfd.fileDescriptor) {
            override fun close() { try { super.close() } finally { pfd.close() } }
        }
        fis.channel.position(offset)
        return fis
    }

    private fun notification(text: String, pct: Int): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Envoi Bluetooth", NotificationManager.IMPORTANCE_LOW))
        val cancel = android.app.PendingIntent.getService(this, 2,
            Intent(this, BtUploadService::class.java).setAction(ACTION_CANCEL), android.app.PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL).setContentTitle("CastBridge").setContentText("$text · $pct %").setSubText("$pct %")
            .setSmallIcon(R.drawable.ic_stat_castbridge).setOngoing(true).setOnlyAlertOnce(true)
            .setProgress(100, pct, false)
            .addAction(Notification.Action.Builder(null, "Annuler", cancel).build()).build()
    }

    @Volatile private var castStart = 0L
    @Volatile private var castTotal = 0L
    @Volatile private var castChannel = "bluetooth"

    private fun finish(s: ResumableUpload.State) {
        if (castStart > 0) {
            val ok = s == ResumableUpload.State.Done
            PhoneConnect.castEnd(castChannel, "copy", if (ok) castTotal else 0, System.currentTimeMillis() - castStart, ok,
                when { ok -> null; cancelled -> "cancelled"; else -> "failed" })
            castStart = 0
        }
        if (UploadService.slot.holds(myToken)) _state.value = s
        UploadService.slot.release(myToken); stopSelf()
    }

    /** Only the owner of the current reservation writes the shared state; each write is a sign of life ([castbridge.core.tv.UploadSlot]). */
    private fun pub(s: ResumableUpload.State) { if (UploadService.slot.touch(myToken)) _state.value = s }

    override fun onDestroy() {
        cancelled = true; if (current === this) current = null
        if (worker?.isAlive != true) UploadService.slot.release(myToken)        // else the worker's own end releases it (same generation)
        super.onDestroy()
    }

    companion object {
        private const val TAG = "BtUploadService"
        private const val CHANNEL = "btupload"
        private const val NOTIF = 3
        const val ACTION_CANCEL = "castbridge.CANCEL_BT_UPLOAD"
        private const val EXTRA_ADDR = "addr"
        private const val EXTRA_PIN = "pin"
        private const val EXTRA_NAME = "name"
        private const val EXTRA_WD = "allow_wd"
        private val _state = MutableStateFlow<ResumableUpload.State?>(null)
        val state: StateFlow<ResumableUpload.State?> = _state
        private val _route = MutableStateFlow<String?>(null)
        /** Which link carries the file: "Wi-Fi (réseau commun)", "Wi-Fi Direct" or "Bluetooth". */
        val route: StateFlow<String?> = _route

        @Volatile private var current: BtUploadService? = null
        /** A Bluetooth upload is running in this process. */
        fun active(): Boolean = current?.worker?.isAlive == true || UploadService.slot.held()

        /**
         * [allowWifiDirect] false = the caller (the queue, core BulkRoute + AutoWifiDirect) already chose Bluetooth: no CBTN Wi-Fi Direct request and no
         * system dialog here. True = the manual Bluetooth channel (TvHub), unchanged.
         */
        fun start(ctx: Context, uri: Uri, fileName: String, address: String, pin: String, allowWifiDirect: Boolean = true) {
            // R-09: the same atomic reservation as Wi-Fi (one upload at a time on the phone); the loser is refused, never dropped
            val token = UploadService.slot.tryReserve(fileName) ?: throw UploadService.Busy()
            try {
                _state.value = null
                ctx.startForegroundService(Intent(ctx, BtUploadService::class.java).setData(uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    .putExtra(EXTRA_ADDR, address).putExtra(EXTRA_PIN, pin).putExtra(EXTRA_NAME, fileName).putExtra(UploadService.EXTRA_TOKEN, token)
                    .putExtra(EXTRA_WD, allowWifiDirect))
            } catch (e: Throwable) { UploadService.slot.release(token); throw e }
        }

        fun cancel(ctx: Context) {
            runCatching { ctx.startService(Intent(ctx, BtUploadService::class.java).setAction(ACTION_CANCEL)) }
        }
    }
}
