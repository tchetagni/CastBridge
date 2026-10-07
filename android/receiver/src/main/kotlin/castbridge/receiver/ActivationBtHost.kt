package castbridge.receiver

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import castbridge.core.btact.BtActAd
import castbridge.core.btact.BtActServer
import castbridge.core.tv.BtProtocol
import java.io.IOException
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/**
 * « Activer par Bluetooth sans appairage » on the TV (act-bt, DESIGN-ACTIVATION-SIMPLE § 7, docs/BT-PLUG-AND-PLAY.md): while the activation screen of a LOCKED TV is open, the TV
 *  - ADVERTISES over BLE the service `…0008` ([BtActAd]): ONE packet with the service UUID, a 2-byte TAG of the connection code (never the code) and the PSM of its L2CAP channel (the record goes to
 *    the scan response only when the controller refuses the 30 bytes);
 *  - LISTENS on an INSECURE RFCOMM socket of that UUID and, from Android 10, on an insecure L2CAP channel (a BLE advertisement carries a random address that cannot be used to open a classic RFCOMM
 *    link, so the L2CAP channel, which runs over the LE link the phone just saw, is the one that works on most boxes; RFCOMM stays for the ones where the two addresses are the same).
 * NO pairing box anywhere: the code, proven by a PAKE, authenticates the phone ([BtActServer]). Every connection runs [BtActServer.serve] on its own thread, two at a time, each closed after
 * [SESSION_MS] by a watchdog (a peer that stays silent never holds a slot).
 *
 * The route exists only when the box CAN advertise (`bluetoothLeAdvertiser != null`), Bluetooth is on and the permissions are granted ([state] READY once the advertisement is up); otherwise the
 * activation screen does not list it and the other ways stay. Started and stopped with the activation screen (the same grace of 20 s as the Wi-Fi Direct group, by [TvService]), never on an
 * activated TV. A connection ends the legacy advertisement (Android stops a connectable advertisement when a link is made): it is started again after each one.
 *
 * Nothing secret reaches the journal: only fixed words and booleans (`ActivationBtSourceTest` guards the source). The connection code, the tag and any Bluetooth address stay out of it.
 */
@SuppressLint("MissingPermission")
class ActivationBtHost(private val ctx: Context, private val server: BtActServer, private val code: () -> String?) {
    enum class State { STOPPED, READY, UNAVAILABLE }

    /** What the activation screen reads: READY = the TV advertises and listens (the line « 2. Bluetooth… » is shown). */
    @Volatile var state: State = State.STOPPED; private set

    private val main = Handler(Looper.getMainLooper())
    private val slots = Semaphore(MAX_SESSIONS)
    private val watchdog = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "cb-act-bt-watchdog").apply { isDaemon = true } }
    private var rfcomm: BluetoothServerSocket? = null
    private var l2cap: BluetoothServerSocket? = null
    private var psm = 0
    private var advertiser: BluetoothLeAdvertiser? = null
    private var wanted = false
    private var running = false
    private var advertising = false
    private var lastAttemptMs = 0L
    private var restartPending = false
    /** The last reason said in the journal (said once per change, not at every retry), the failures of the advertisement since the screen asked, and whether this box will never advertise. */
    private var lastWhy = ""
    private var failures = 0
    private var permanentFailure = false
    /** The record rides in the advertisement packet (30 bytes, [BtActAd]); set when the controller refused that size: it then rides in the scan response. */
    private var recordInScanResponse = false

    /** The activation screen is in front: start (or keep) the advertisement and the listeners. Main thread; idempotent. */
    @Synchronized fun want() { wanted = true; failures = 0; permanentFailure = false; recordInScanResponse = false; startNow() }

    /** The 2 s tick of the screen: a box whose Bluetooth came on late, or whose permission was just granted, joins in; a listener that died is made again. */
    @Synchronized fun tick() {
        if (!wanted || permanentFailure) return                                     // a box that cannot advertise is not asked again until the screen asks anew
        val now = android.os.SystemClock.elapsedRealtime()
        if (running && state == State.READY) return
        if (now - lastAttemptMs < RETRY_MS) return
        if (running) teardown()
        startNow()
    }

    /** The screen left for good, or the TV was activated: everything is given back. */
    @Synchronized fun stop() { wanted = false; teardown() }

    /** The host is dropped (the locked route stops): [stop], and the watchdog thread goes with it. A connection that arrives at this very instant is closed, never scheduled (no exception in a pool thread). */
    @Synchronized fun release() { stop(); watchdog.shutdownNow() }

    // ------------------------------------------------------------------ start

    private fun hasPermissions(): Boolean = Build.VERSION.SDK_INT < 31 ||
        (ctx.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED && ctx.checkSelfPermission(android.Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED)

    private fun startNow() {
        if (running) return
        lastAttemptMs = android.os.SystemClock.elapsedRealtime()
        val adapter: BluetoothAdapter? = (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        if (adapter == null) return unavailable("pas de Bluetooth")
        if (!adapter.isEnabled) return unavailable("Bluetooth éteint")
        if (!hasPermissions()) return unavailable("permission Bluetooth manquante")
        val adv = adapter.bluetoothLeAdvertiser ?: return unavailable("cette TV ne sait pas annoncer en BLE")
        if (code() == null) return unavailable("pas de code de connexion")
        val uuid = UUID.fromString(BtProtocol.ACTIVATION_SERVICE_UUID)
        rfcomm = try { adapter.listenUsingInsecureRfcommWithServiceRecord(SDP_NAME, uuid) } catch (e: Exception) { Log.w(TAG, "écoute RFCOMM impossible : ${e.javaClass.simpleName}"); null }
        l2cap = if (Build.VERSION.SDK_INT >= 29) try { adapter.listenUsingInsecureL2capChannel() } catch (e: Exception) { Log.w(TAG, "écoute L2CAP impossible : ${e.javaClass.simpleName}"); null } else null
        psm = if (Build.VERSION.SDK_INT >= 29) l2cap?.psm ?: 0 else 0
        if (rfcomm == null && l2cap == null) return unavailable("aucune écoute possible")
        advertiser = adv; running = true
        rfcomm?.let { acceptLoop(it, false) }; l2cap?.let { acceptLoop(it, true) }
        startAdvertising()
    }

    private fun unavailable(why: String) {
        if (why != lastWhy) { lastWhy = why; Log.i(TAG, "activation Bluetooth sans appairage absente : $why") }
        state = State.UNAVAILABLE
    }

    // ------------------------------------------------------------------ the BLE advertisement

    private val callback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            synchronized(this@ActivationBtHost) { if (!running) return; advertising = true; state = State.READY; failures = 0; lastWhy = "" }
            Log.i(TAG, "activation Bluetooth sans appairage : annonce prête (L2CAP ${psm != 0}, RFCOMM ${rfcomm != null})")
        }

        override fun onStartFailure(errorCode: Int) {
            // 3 = already started: the advertisement is up
            if (errorCode == ADVERTISE_FAILED_ALREADY_STARTED) { synchronized(this@ActivationBtHost) { if (running) { advertising = true; state = State.READY } }; return }
            Log.w(TAG, "annonce BLE refusée (code $errorCode)")
            // 1 = data too large: once, the record goes to the scan response instead (the UUID alone is 21 bytes)
            if (errorCode == ADVERTISE_FAILED_DATA_TOO_LARGE && !recordInScanResponse) {
                synchronized(this@ActivationBtHost) { recordInScanResponse = true }
                main.post { synchronized(this@ActivationBtHost) { if (running) startAdvertising() } }
                return
            }
            // 1 (again) or 5 = feature unsupported: this box will not advertise, whatever is tried; the others (internal error, too many advertisers) are retried a few times
            synchronized(this@ActivationBtHost) {
                advertising = false
                if (errorCode == ADVERTISE_FAILED_DATA_TOO_LARGE || errorCode == ADVERTISE_FAILED_FEATURE_UNSUPPORTED || ++failures >= MAX_FAILURES) permanentFailure = true
                if (running) teardown(); state = State.UNAVAILABLE
            }
        }
    }

    private fun startAdvertising() {
        val adv = advertiser ?: return
        val c = code() ?: return
        val settings = AdvertiseSettings.Builder().setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED).setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(psm != 0).setTimeout(0).build()
        // the UUID and the record in ONE packet: a hardware scan filter on the UUID then sees both; the scan response only carries the record when the controller refused the 30 bytes
        val record = BtActAd.payload(c, psm)
        val data = AdvertiseData.Builder().setIncludeDeviceName(false).setIncludeTxPowerLevel(false).addServiceUuid(ParcelUuid(UUID.fromString(BtActAd.SERVICE_UUID)))
            .apply { if (!recordInScanResponse) addManufacturerData(BtActAd.COMPANY_ID, record) }.build()
        val scanResponse = if (recordInScanResponse) AdvertiseData.Builder().setIncludeDeviceName(false).setIncludeTxPowerLevel(false).addManufacturerData(BtActAd.COMPANY_ID, record).build() else null
        try {
            if (scanResponse == null) adv.startAdvertising(settings, data, callback) else adv.startAdvertising(settings, data, scanResponse, callback)
        } catch (e: Exception) { Log.w(TAG, "annonce BLE impossible : ${e.javaClass.simpleName}"); teardown(); state = State.UNAVAILABLE }
    }

    private fun stopAdvertising() { advertising = false; runCatching { advertiser?.stopAdvertising(callback) } }

    /** A link ends a connectable advertisement: say it again, a moment after (stop and start in the same instant is refused by some stacks). */
    private fun restartAdvertisingSoon() {
        synchronized(this) { if (!running || restartPending) return; restartPending = true }
        main.postDelayed({
            synchronized(this) { restartPending = false; if (!running) return@postDelayed; stopAdvertising() }
            main.postDelayed({ synchronized(this) { if (running) startAdvertising() } }, 200)
        }, 300)
    }

    // ------------------------------------------------------------------ the connections

    private fun acceptLoop(ss: BluetoothServerSocket, isL2cap: Boolean) {
        Thread({
            while (true) {
                val sock = try { ss.accept() } catch (e: IOException) { break }
                if (isL2cap) restartAdvertisingSoon()
                serveOne(sock, isL2cap)
            }
            // the listener ended: closed by [teardown], or Bluetooth went away (the tick of the screen starts again when it is back)
            synchronized(this) { if (running && (if (isL2cap) l2cap else rfcomm) === ss) { teardown(); state = State.UNAVAILABLE } }
        }, if (isL2cap) "cb-act-bt-l2cap" else "cb-act-bt-rfcomm").apply { isDaemon = true; start() }
    }

    private fun serveOne(sock: BluetoothSocket, isL2cap: Boolean) {
        if (!slots.tryAcquire()) { runCatching { sock.close() }; return }
        val peer = runCatching { sock.remoteDevice.address }.getOrNull()            // the device of the socket, never something the peer wrote
        val guard = try { watchdog.schedule(Runnable { runCatching { sock.close() } }, SESSION_MS, TimeUnit.MILLISECONDS) } catch (e: RejectedExecutionException) { slots.release(); runCatching { sock.close() }; return }
        Thread({
            try {
                val end = sock.use { server.serve(it.inputStream, it.outputStream, peer) }
                Log.i(TAG, "session de l'activation Bluetooth : $end")
            } catch (e: Exception) {
                Log.w(TAG, "session de l'activation Bluetooth : ${e.javaClass.simpleName}")
            } finally {
                guard.cancel(false); slots.release()
                if (isL2cap) restartAdvertisingSoon()
            }
        }, "cb-act-bt-session").apply { isDaemon = true; start() }
    }

    // ------------------------------------------------------------------ stop

    private fun teardown() {
        running = false
        stopAdvertising()
        runCatching { rfcomm?.close() }; rfcomm = null
        runCatching { l2cap?.close() }; l2cap = null
        advertiser = null; psm = 0
        state = State.STOPPED
    }

    private companion object {
        const val TAG = "CastBridgeTV"
        /** The SDP name of the table's service `…0008` (`BtProtocol.SERVICES`; `BtServicesTest` checks it is registered here). */
        const val SDP_NAME = "CastBridge Activation"
        const val MAX_SESSIONS = 2
        /** A connection lives at most this long (the PAKE and two or three frames take well under a second). */
        const val SESSION_MS = 30_000L
        const val RETRY_MS = 4_000L
        /** Refusals of the advertisement (other than the permanent ones) before the box is given up for this opening of the screen. */
        const val MAX_FAILURES = 5
    }
}
