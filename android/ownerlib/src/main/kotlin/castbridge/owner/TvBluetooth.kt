package castbridge.owner

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import castbridge.core.owner.OwnerChannelClient
import castbridge.core.owner.OwnerFrames
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The phone's side of the owner Bluetooth channel, self-configuring: it recognises CastBridge-TV among the paired devices (by the services they declare),
 * looks around for one when none is paired, pairs by itself when needed, then reads the device request / pushes the activation.
 * Blocking calls: run them off the main thread.
 */
@SuppressLint("MissingPermission")
object TvBluetooth {
    /** [bonded]: already paired with this phone. [sure]: the device is known to declare a CastBridge service (false = services not known yet). */
    class Tv(val name: String, val address: String, val bonded: Boolean = true, val sure: Boolean = false)

    private const val CB_PREFIX = "7c5e3b9a-4d2f-4c61-9b0e-cb00000000"

    private val needed: Array<String> get() =
        if (Build.VERSION.SDK_INT >= 31) arrayOf(android.Manifest.permission.BLUETOOTH_CONNECT, android.Manifest.permission.BLUETOOTH_SCAN)
        else arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION)

    /** The runtime permissions to ask for (empty when everything is granted). */
    fun missingPermissions(ctx: Context): Array<String> = needed.filter { ctx.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }.toTypedArray()
    fun permitted(ctx: Context): Boolean = missingPermissions(ctx).isEmpty()

    private fun adapter(ctx: Context): BluetoothAdapter? = (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    private fun declaresCastBridge(d: BluetoothDevice): Boolean? {
        val u = runCatching { d.uuids }.getOrNull()
        return if (u.isNullOrEmpty()) null else u.any { it.toString().lowercase().startsWith(CB_PREFIX) }
    }

    /** Paired devices that are, or may be, a CastBridge-TV: a device whose declared services do not include ours (headset, car, laptop…) is left out. */
    fun pairedTvs(ctx: Context): List<Tv> {
        if (!permitted(ctx)) return emptyList()
        return runCatching {
            adapter(ctx)?.bondedDevices.orEmpty().mapNotNull { d ->
                when (declaresCastBridge(d)) { false -> null; true -> Tv(d.name ?: d.address, d.address, true, true); null -> Tv(d.name ?: d.address, d.address, true, false) }
            }.sortedWith(compareByDescending<Tv> { it.sure }.thenBy { it.name.lowercase() })
        }.getOrDefault(emptyList())
    }

    /** Looks around for [seconds] and reports each CastBridge-TV found (paired or not). Returns when the scan is over. */
    fun scan(ctx: Context, seconds: Int = 14, onFound: (Tv) -> Unit) {
        val ad = adapter(ctx) ?: return
        if (!ad.isEnabled || !permitted(ctx)) return
        val seen = HashSet<String>(); val done = CountDownLatch(1)
        fun check(d: BluetoothDevice) {
            if (declaresCastBridge(d) == true && seen.add(d.address)) onFound(Tv(d.name ?: d.address, d.address, d.bondState == BluetoothDevice.BOND_BONDED, true))
        }
        val rx = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                val d = i.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
                when (i.action) {
                    BluetoothDevice.ACTION_FOUND -> { if (declaresCastBridge(d) == null) runCatching { d.fetchUuidsWithSdp() } else check(d) }
                    BluetoothDevice.ACTION_UUID -> check(d)
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> done.countDown()
                }
            }
        }
        ctx.registerReceiver(rx, IntentFilter().apply { addAction(BluetoothDevice.ACTION_FOUND); addAction(BluetoothDevice.ACTION_UUID); addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED) })
        try { if (ad.startDiscovery()) done.await(seconds.toLong(), TimeUnit.SECONDS) } finally { runCatching { ad.cancelDiscovery() }; runCatching { ctx.unregisterReceiver(rx) } }
    }

    /** Pairs with [address] (the system shows its comparison code on both screens), waiting up to [seconds]. */
    fun bond(ctx: Context, address: String, seconds: Int = 90): Boolean {
        val ad = adapter(ctx) ?: return false
        val d = ad.getRemoteDevice(address)
        if (d.bondState == BluetoothDevice.BOND_BONDED) return true
        val done = CountDownLatch(1)
        val rx = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                val x = i.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
                if (x.address != address) return
                if (i.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1).let { it == BluetoothDevice.BOND_BONDED || it == BluetoothDevice.BOND_NONE }) done.countDown()
            }
        }
        ctx.registerReceiver(rx, IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED))
        try { runCatching { ad.cancelDiscovery() }; if (!d.createBond()) return false; done.await(seconds.toLong(), TimeUnit.SECONDS) } finally { runCatching { ctx.unregisterReceiver(rx) } }
        return d.bondState == BluetoothDevice.BOND_BONDED
    }

    /** The services the TV declares now, or null if it did not answer in time. */
    private fun freshServices(ctx: Context, dev: BluetoothDevice): List<String>? {
        val done = CountDownLatch(1); var list: List<String>? = null
        val rx = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                val d = i.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
                if (d.address != dev.address) return
                list = i.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID)?.map { it.toString().lowercase() } ?: runCatching { d.uuids?.map { it.toString().lowercase() } }.getOrNull()
                done.countDown()
            }
        }
        ctx.registerReceiver(rx, IntentFilter(BluetoothDevice.ACTION_UUID))
        try { if (dev.fetchUuidsWithSdp()) done.await(8, TimeUnit.SECONDS) } finally { runCatching { ctx.unregisterReceiver(rx) } }
        return list
    }

    /** Opens the channel to [tv] (pairing first if needed), runs [block] on it, always closes. Throws a readable message on failure. */
    fun <T> with(ctx: Context, tv: Tv, block: (OwnerChannelClient) -> T): T {
        val ad = adapter(ctx) ?: throw IllegalStateException("Bluetooth indisponible")
        if (!ad.isEnabled) throw IllegalStateException("Bluetooth désactivé sur ce téléphone")
        if (!bond(ctx, tv.address)) throw IllegalStateException("Appairage non terminé : validez le code sur la TV et sur le téléphone, puis réessayez")
        val dev = ad.getRemoteDevice(tv.address)
        runCatching { ad.cancelDiscovery() }
        // asks the TV what it offers right now (Android caches this list and can keep an old one): tells "service not started" from "link problem"
        val offered = freshServices(ctx, dev)
        android.util.Log.i("CbOwnerBt", "services de ${tv.name}: ${offered?.joinToString(",") { it.substringAfterLast('-') } ?: "inconnus"}")
        if (offered != null && offered.none { it.equals(OwnerFrames.SERVICE_UUID, true) })
            throw IllegalStateException("La TV est jointe mais n'annonce pas le canal d'activation (${offered.count { it.startsWith(CB_PREFIX, true) }} service(s) CastBridge). Ouvrez CastBridge-TV (0.14.4 ou plus) : l'écran d'activation affiche « Bluetooth d'activation : prêt ».")
        val sock = dev.createRfcommSocketToServiceRecord(UUID.fromString(OwnerFrames.SERVICE_UUID))
        try {
            // R-32 (audit I-10): one connect() at a time to the same TV (the lock of the trusted link, the remote, the pipe…): the activation by Bluetooth no longer collides with them
            try { synchronized(castbridge.core.tunnel.BtConnectLock.of(tv.address)) { sock.connect() } } catch (e: java.io.IOException) {
                android.util.Log.w("CbOwnerBt", "connect: ${e.message}")
                throw IllegalStateException("Connexion impossible : ouvrez CastBridge-TV (version 0.14.2 ou plus) sur la TV, puis réessayez")
            }
            val c = OwnerChannelClient(sock.inputStream, sock.outputStream)
            if (!c.hello()) throw IllegalStateException("Cette TV ne répond pas au canal d'activation (version trop ancienne ?)")
            return block(c)
        } finally { runCatching { sock.close() } }
    }
}
