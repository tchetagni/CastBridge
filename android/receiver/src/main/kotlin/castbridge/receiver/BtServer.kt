package castbridge.receiver

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import castbridge.core.tv.BtProtocol
import castbridge.core.tv.PinGuard
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Bluetooth (RFCOMM) receiver: a phone paired with the TV can push files into the app's private
 * folder with [BtProtocol]. Needs BLUETOOTH_CONNECT at runtime on API 31+; without it (or without a
 * Bluetooth adapter) [start] reports the reason through [status] and the rest of the app keeps working.
 */
@SuppressLint("MissingPermission")
class BtServer(
    private val ctx: Context,
    private val dir: File,
    private val guard: PinGuard,
    /** Answers "is there a faster link?" (CBTN): addresses of the TV and its Wi-Fi Direct group. */
    private val negotiate: ((Boolean) -> castbridge.core.tv.LinkInfo)? = null,
    private val status: (String?) -> Unit,
) {
    @Volatile private var server: BluetoothServerSocket? = null
    @Volatile private var running = false
    /** A transfer is in progress (the service keeps a wake lock meanwhile). */
    @Volatile var busy = false; private set

    fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < 31 || ctx.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    fun adapter(): BluetoothAdapter? = ctx.getSystemService(BluetoothManager::class.java)?.adapter

    val isRunning get() = running

    /** State for GET /api/bluetooth: lets a phone or an agent see why a transfer cannot happen. */
    fun stateJson(serverStatus: String?): String {
        val q = castbridge.core.tv.ReceiverServer::q
        val ad = adapter()
        val perm = hasPermission()
        val enabled = runCatching { ad?.isEnabled == true }.getOrDefault(false)
        val name = if (perm) runCatching { ad?.name }.getOrNull() else null
        val scan = if (perm) runCatching { ad?.scanMode }.getOrNull() else null
        val bonded = if (perm && enabled) runCatching { ad?.bondedDevices.orEmpty().map { (it.name ?: "?") to it.address } }.getOrDefault(emptyList()) else emptyList()
        return "{\"available\":${ad != null},\"permission\":$perm,\"enabled\":$enabled,\"name\":${name?.let(q) ?: "null"}," +
            "\"discoverable\":${scan == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE},\"listening\":$running," +
            "\"status\":${serverStatus?.let(q) ?: "null"},\"bonded\":[" +
            bonded.joinToString(",") { (n, a) -> "{\"name\":${q(n)},\"address\":${q(a)}}" } + "]}"
    }

    @Synchronized fun start() {
        if (running) return
        val ad = adapter()
        when {
            ad == null -> { status("Bluetooth : non disponible sur cet appareil"); return }
            !hasPermission() -> { status("Bluetooth : permission refusée (réception désactivée)"); return }
            !ad.isEnabled -> { status("Bluetooth : désactivé dans les réglages de la TV"); return }
        }
        val ss = try { ad!!.listenUsingRfcommWithServiceRecord("CastBridge TV", SERVICE_UUID) }
        catch (e: Exception) { Log.w(TAG, "listen failed", e); status("Bluetooth : écoute impossible"); return }
        server = ss; running = true
        status("Bluetooth : prêt (${runCatching { ad.name }.getOrNull() ?: "TV"})")
        Thread({ acceptLoop(ss) }, "bt-accept").apply { isDaemon = true; start() }
    }

    @Synchronized fun stop() {
        running = false
        runCatching { server?.close() }
        server = null
    }

    private fun acceptLoop(ss: BluetoothServerSocket) {
        while (running) {
            val sock = try { ss.accept() } catch (e: IOException) { break }
            handle(sock)   // one transfer at a time: the phone reconnects to resume
        }
        if (running) { running = false; status("Bluetooth : arrêté") }
    }

    private fun handle(sock: BluetoothSocket) {
        val last = java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis())
        val peer = runCatching { sock.remoteDevice.address }.getOrDefault("?")
        // No socket timeout exists for RFCOMM: a watchdog closes a link that stalls for 30 s.
        val watchdog = Thread {
            try { while (true) { Thread.sleep(5000); if (System.currentTimeMillis() - last.get() > 30_000) { sock.close(); break } } }
            catch (_: InterruptedException) {}
        }.apply { isDaemon = true; start() }
        var lastPct = -1
        busy = true
        try {
            val r = BtProtocol.serve(dir, sock.inputStream, sock.outputStream, guard, peer, onProgress = { name, done, total ->
                last.set(System.currentTimeMillis())
                val pct = (done * 100 / total).toInt()
                if (pct != lastPct) { lastPct = pct; status("Bluetooth : réception de $name $pct %") }
            }, negotiate = negotiate)
            status("Bluetooth : prêt" + if (r != BtProtocol.OK) " (refusé : ${BtProtocol.describe(r)})" else " (fichier reçu)")
        } catch (e: Exception) {
            Log.w(TAG, "transfer interrupted: ${e.javaClass.simpleName}")   // never log request contents
            status("Bluetooth : transfert interrompu, reprise possible")
        } finally {
            busy = false
            watchdog.interrupt()
            runCatching { sock.close() }
        }
    }

    companion object {
        private const val TAG = "CastBridgeBT"
        val SERVICE_UUID: UUID = UUID.fromString(BtProtocol.SERVICE_UUID)
    }
}
