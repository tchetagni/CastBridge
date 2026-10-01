package castbridge.sender

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import castbridge.core.remote.hid.HidRemote
import castbridge.core.remote.hid.HidReport
import castbridge.core.remote.hid.HidTransport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.Executors

/**
 * Route C (docs/REMOTE.md): the phone presents itself to the TV as a Bluetooth keyboard + consumer-control remote
 * (BluetoothHidDevice, Android 9+). No app is needed on the TV; the reports are built by the tested core ([HidRemote]).
 * Needs BLUETOOTH_CONNECT (Android 12+), already asked for the Bluetooth fallback ([hasBtPermission]).
 */
@SuppressLint("MissingPermission", "NewApi")
object BtHidRemote : HidTransport {
    enum class State(val label: String) {
        OFF("arrêté"), STARTING("démarrage…"), READY("prêt : choisissez la TV"), CONNECTING("connexion…"), CONNECTED("connecté"),
        UNSUPPORTED("non pris en charge par cette TV ou ce téléphone"), NO_PERMISSION("autorisation Bluetooth refusée"),
    }

    private val _state = MutableStateFlow(State.OFF)
    val state: StateFlow<State> = _state
    private var adapter: BluetoothAdapter? = null
    private var hid: BluetoothHidDevice? = null
    @Volatile private var host: BluetoothDevice? = null
    private val executor = Executors.newSingleThreadExecutor()

    val supported: Boolean get() = Build.VERSION.SDK_INT >= 28
    /** True once a connection attempt ended without ever connecting: the TV does not take a phone as a keyboard. */
    val hostRefused: Boolean get() = _state.value == State.UNSUPPORTED
    override val connected: Boolean get() = _state.value == State.CONNECTED && host != null

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onConnectionStateChanged(device: BluetoothDevice?, s: Int) {
            when (s) {
                BluetoothProfile.STATE_CONNECTED -> { host = device; _state.value = State.CONNECTED }
                BluetoothProfile.STATE_CONNECTING -> _state.value = State.CONNECTING
                BluetoothProfile.STATE_DISCONNECTED -> {
                    // Dropped while still connecting: the host refused the keyboard. Dropped after connecting: just a cut.
                    _state.value = if (_state.value == State.CONNECTING) State.UNSUPPORTED else State.READY
                    host = null
                }
            }
        }
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            if (!registered && _state.value == State.STARTING) _state.value = State.UNSUPPORTED
        }
    }

    fun start(ctx: Context) {
        if (!supported) { _state.value = State.UNSUPPORTED; return }
        if (!hasBtPermission(ctx)) { _state.value = State.NO_PERMISSION; return }
        if (hid != null) return
        val ad = ctx.getSystemService(BluetoothManager::class.java)?.adapter
        if (ad == null || !ad.isEnabled) { _state.value = State.UNSUPPORTED; return }
        adapter = ad
        _state.value = State.STARTING
        val ok = ad.getProfileProxy(ctx.applicationContext, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                val h = proxy as BluetoothHidDevice
                hid = h
                val sdp = BluetoothHidDeviceAppSdpSettings("CastBridge", "Télécommande", "CastBridge",
                    BluetoothHidDevice.SUBCLASS1_COMBO, HidRemote.DESCRIPTOR)
                if (h.registerApp(sdp, null, null, executor, callback)) _state.value = State.READY else _state.value = State.UNSUPPORTED
            }
            override fun onServiceDisconnected(profile: Int) { hid = null; host = null; _state.value = State.OFF }
        }, BluetoothProfile.HID_DEVICE)
        if (!ok) _state.value = State.UNSUPPORTED
    }

    /** Connects to a TV already paired in Android's Bluetooth settings (or pair from the TV's Bluetooth menu, then call this). */
    fun connect(device: BluetoothDevice): Boolean {
        val h = hid ?: return false
        _state.value = State.CONNECTING
        return h.connect(device).also { if (!it) _state.value = State.UNSUPPORTED }
    }

    override fun send(report: HidReport): Boolean {
        val h = hid ?: return false
        val d = host ?: return false
        return h.sendReport(d, report.id, report.data)
    }

    fun stop() {
        runCatching { host?.let { hid?.disconnect(it) } }
        runCatching { hid?.unregisterApp() }
        runCatching { adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, hid) }
        hid = null; host = null; _state.value = State.OFF
    }
}
