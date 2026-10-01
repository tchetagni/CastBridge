package castbridge.sender

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.hardware.ConsumerIrManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import castbridge.core.remote.smart.AppLauncher
import castbridge.core.remote.smart.HidPort
import castbridge.core.remote.smart.HidReports
import castbridge.core.remote.smart.IrEmitter
import castbridge.core.remote.smart.MdnsRecord
import castbridge.core.remote.smart.Remembered
import castbridge.core.remote.smart.SecretStore
import castbridge.core.remote.smart.StrategyException
import castbridge.core.remote.smart.StrategyMemory
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Smart remote, phone side (docs/REMOTE.md): private storage of what worked and of the pairing tokens, and the three phone-only
 * ways of driving a TV (infrared emitter, Bluetooth keyboard, the maker's app). Plain preferences of the app's private space:
 * not readable by other apps, never logged, never sent anywhere.
 */
class SmartPrefs(ctx: Context) : StrategyMemory, SecretStore {
    private val sp = ctx.applicationContext.getSharedPreferences("castbridge_smart", Context.MODE_PRIVATE)

    override fun recall(tvId: String): Remembered? = sp.getString("mem:$tvId", null)?.split('|')?.let { p ->
        val at = p.getOrNull(1)?.toLongOrNull(); if (p.size == 2 && at != null) Remembered(p[0], at) else null
    }
    override fun put(tvId: String, r: Remembered) { sp.edit().putString("mem:$tvId", "${r.strategyId}|${r.at}").apply() }
    override fun forget(tvId: String) { sp.edit().remove("mem:$tvId").apply() }

    // SecretStore: pairing tokens (Samsung, LG, Vizio), Sony key. Never shown in the diagnostic.
    override fun get(key: String): String? = sp.getString("sec:$key", null)
    override fun put(key: String, value: String) { sp.edit().putString("sec:$key", value).apply() }
    override fun remove(key: String) { sp.edit().remove("sec:$key").apply() }

    /** The TV chosen in « Ma TV » (address, name); the user picks it, nothing is scanned for. */
    var tvHost: String? get() = sp.getString("tv.host", null); set(v) { sp.edit().putString("tv.host", v).apply() }
    var tvName: String? get() = sp.getString("tv.name", null); set(v) { sp.edit().putString("tv.name", v).apply() }
    /** Off by default: strategies that were not verified on real hardware. */
    var experimental: Boolean get() = sp.getBoolean("experimental", false); set(v) { sp.edit().putBoolean("experimental", v).apply() }
    fun forced(tvId: String): String? = sp.getString("forced:$tvId", null)
    fun setForced(tvId: String, id: String?) { sp.edit().apply { if (id == null) remove("forced:$tvId") else putString("forced:$tvId", id) }.apply() }
}

/** Infrared emitter of the phone (ConsumerIrManager), if it has one. */
class AndroidIrEmitter(ctx: Context) : IrEmitter {
    private val ir = ctx.applicationContext.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
    override val available: Boolean get() = ir?.hasIrEmitter() == true
    override fun transmit(carrierHz: Int, pattern: IntArray) { ir?.transmit(carrierHz, pattern) }
}

/** « Ouvrir l'app du fabricant » (needs the <queries> entries of the manifest to see other apps). */
class AndroidAppLauncher(private val ctx: Context) : AppLauncher {
    override fun isInstalled(pkg: String) = ctx.packageManager.getLaunchIntentForPackage(pkg) != null
    override fun open(pkg: String): Boolean {
        val i = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        return runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
    }
}

/** The phone as a Bluetooth keyboard / remote (BluetoothHidDevice, Android 9+). The TV must already be paired with the phone. */
@SuppressLint("MissingPermission")
class AndroidHidPort(private val ctx: Context, private val btAddress: String?) : HidPort {
    private var hid: BluetoothHidDevice? = null
    private var host: BluetoothDevice? = null

    override val available: Boolean get() = Build.VERSION.SDK_INT >= 28 && btAddress != null && hasBtPermission(ctx) &&
        ctx.getSystemService(BluetoothManager::class.java)?.adapter != null

    override fun connect() {
        if (Build.VERSION.SDK_INT < 28) throw IOException("Bluetooth clavier : Android 9 minimum")
        val addr = btAddress ?: throw StrategyException("Appairez d'abord la TV dans les réglages Bluetooth du téléphone.", needsPairing = true)
        val adapter = ctx.getSystemService(BluetoothManager::class.java)?.adapter ?: throw IOException("pas de Bluetooth")
        if (!adapter.isEnabled) throw IOException("Bluetooth éteint")
        val device = adapter.getRemoteDevice(addr)
        val gotProxy = CountDownLatch(1); var proxy: BluetoothHidDevice? = null
        adapter.getProfileProxy(ctx, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, p: BluetoothProfile) { proxy = p as? BluetoothHidDevice; gotProxy.countDown() }
            override fun onServiceDisconnected(profile: Int) {}
        }, BluetoothProfile.HID_DEVICE)
        if (!gotProxy.await(3, TimeUnit.SECONDS) || proxy == null) throw IOException("service Bluetooth clavier indisponible")
        val h = proxy!!
        val registered = CountDownLatch(1); val connected = CountDownLatch(1)
        val cb = object : BluetoothHidDevice.Callback() {
            override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, isRegistered: Boolean) { if (isRegistered) registered.countDown() }
            override fun onConnectionStateChanged(d: BluetoothDevice, state: Int) { if (state == BluetoothProfile.STATE_CONNECTED) connected.countDown() }
        }
        val sdp = BluetoothHidDeviceAppSdpSettings("CastBridge", "Télécommande", "CastBridge", BluetoothHidDevice.SUBCLASS1_COMBO, HidReports.DESCRIPTOR)
        if (!h.registerApp(sdp, null, null, ctx.mainExecutor, cb) || !registered.await(3, TimeUnit.SECONDS)) throw IOException("enregistrement Bluetooth refusé")
        if (!h.connect(device) || !connected.await(10, TimeUnit.SECONDS)) {
            runCatching { h.unregisterApp() }
            throw StrategyException("Acceptez la connexion « clavier Bluetooth » de CastBridge sur la TV (ou appairez-la d'abord).", needsPairing = true)
        }
        hid = h; host = device
    }

    override fun sendReport(id: Int, data: ByteArray) {
        val h = hid; val d = host
        if (h == null || d == null || !h.sendReport(d, id, data)) throw IOException("envoi Bluetooth échoué")
    }

    override fun close() { runCatching { hid?.unregisterApp() }; hid = null; host = null }
}

/**
 * Passive DNS-SD listening (a few seconds) for the types the strategies know, keeping only the records of the chosen TV's address.
 * Same pattern as TvDiscovery: a multicast lock, resolutions one at a time.
 */
class MdnsHints(ctx: Context) {
    private val app = ctx.applicationContext
    private val nsd = app.getSystemService(NsdManager::class.java)

    fun collect(host: String, listenMs: Long = 3500): List<MdnsRecord> {
        val found = LinkedBlockingQueue<NsdServiceInfo>(); val out = CopyOnWriteArrayList<MdnsRecord>()
        val lock = runCatching { (app.getSystemService(Context.WIFI_SERVICE) as WifiManager).createMulticastLock("castbridge-hints").apply { setReferenceCounted(false); acquire() } }.getOrNull()
        val listeners = TYPES.map { type ->
            object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(t: String) {}
                override fun onDiscoveryStopped(t: String) {}
                override fun onStartDiscoveryFailed(t: String, e: Int) {}
                override fun onStopDiscoveryFailed(t: String, e: Int) {}
                override fun onServiceFound(i: NsdServiceInfo) { found.offer(i) }
                override fun onServiceLost(i: NsdServiceInfo) {}
            }.also { l -> runCatching { nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, l) } }
        }
        try {
            val until = System.currentTimeMillis() + listenMs
            while (System.currentTimeMillis() < until) {
                val i = found.poll(200, TimeUnit.MILLISECONDS) ?: continue
                val done = CountDownLatch(1)
                @Suppress("DEPRECATION")
                runCatching {
                    nsd.resolveService(i, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(s: NsdServiceInfo, e: Int) { done.countDown() }
                        override fun onServiceResolved(s: NsdServiceInfo) {
                            if (s.host?.hostAddress == host)
                                out += MdnsRecord(s.serviceType.trim('.'), s.serviceName, s.attributes.mapValues { (_, v) -> v?.let { String(it) } ?: "" }, s.port)
                            done.countDown()
                        }
                    })
                }.onFailure { done.countDown() }
                done.await(1500, TimeUnit.MILLISECONDS)
            }
        } finally {
            listeners.forEach { runCatching { nsd.stopServiceDiscovery(it) } }
            runCatching { lock?.release() }
        }
        return out.toList()
    }

    companion object {
        /** DNS-SD types of the known strategies (docs/REMOTE.md). */
        val TYPES = listOf("_castbridge._tcp.", "_share._tcp.", "_maxhubmobile._tcp.", "_androidtvremote2._tcp.", "_samsungmsf._tcp.")
    }
}
