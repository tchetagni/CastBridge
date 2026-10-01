package castbridge.owner

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import castbridge.core.owner.OwnerChannelClient
import castbridge.core.owner.OwnerFrames
import java.util.UUID

/** The console's side of the owner Bluetooth channel: the paired TVs, reading a TV's device request, pushing an activation to it. Blocking calls: run them off the main thread. */
@SuppressLint("MissingPermission")
object TvBluetooth {
    class Tv(val name: String, val address: String)

    fun permitted(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || ctx.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun adapter(ctx: Context): BluetoothAdapter? = (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    /** Paired devices (the TV must already be paired with this phone, as for the remote control). */
    fun paired(ctx: Context): List<Tv> =
        if (!permitted(ctx)) emptyList() else runCatching { adapter(ctx)?.bondedDevices.orEmpty().map { Tv(it.name ?: it.address, it.address) }.sortedBy { it.name.lowercase() } }.getOrDefault(emptyList())

    /** Opens the channel to [address], runs [block] on it, always closes. Throws a readable message on failure. */
    fun <T> with(ctx: Context, address: String, block: (OwnerChannelClient) -> T): T {
        val ad = adapter(ctx) ?: throw IllegalStateException("Bluetooth indisponible")
        if (!ad.isEnabled) throw IllegalStateException("Bluetooth désactivé sur ce téléphone")
        val dev = ad.getRemoteDevice(address)
        runCatching { ad.cancelDiscovery() }
        val sock = dev.createRfcommSocketToServiceRecord(UUID.fromString(OwnerFrames.SERVICE_UUID))
        try {
            try { sock.connect() } catch (e: java.io.IOException) {
                throw IllegalStateException("Connexion impossible : la TV est-elle allumée, appairée, avec CastBridge-TV version 0.14 ou plus ?")
            }
            val c = OwnerChannelClient(sock.inputStream, sock.outputStream)
            if (!c.hello()) throw IllegalStateException("Cette TV ne répond pas au canal propriétaire (version trop ancienne ?)")
            return block(c)
        } finally { runCatching { sock.close() } }
    }
}
