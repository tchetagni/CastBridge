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
import android.os.Build
import android.os.IBinder
import android.util.Log
import castbridge.core.gateway.Exit
import castbridge.core.gateway.Gw
import castbridge.core.gateway.Mux
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID
import kotlin.concurrent.thread

/**
 * Shares the phone's Internet with CastBridge TV over Bluetooth: connects to the TV's gateway service and opens,
 * on the phone's network, the connections the TV app asks for. Reconnects by itself until stopped.
 */
@SuppressLint("MissingPermission")
class BtGatewayService : Service() {
    @Volatile private var stopping = false
    private var worker: Thread? = null
    @Volatile private var sock: android.bluetooth.BluetoothSocket? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopping = true; runCatching { sock?.close() }; _state.value = "Partage arrêté"; stopSelf(); return START_NOT_STICKY }
        val address = intent?.getStringExtra(EXTRA_ADDR) ?: return START_NOT_STICKY
        val pin = intent.getStringExtra(EXTRA_PIN) ?: return START_NOT_STICKY
        if (worker?.isAlive == true) return START_NOT_STICKY
        try {
            val n = notification("Partage d'Internet avec la TV…")
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE) else startForeground(NOTIF, n)
        } catch (e: Exception) { Log.e(TAG, "startForeground", e); stopSelf(); return START_NOT_STICKY }
        stopping = false
        worker = thread(name = "bt-gateway") { loop(address, pin) }
        return START_NOT_STICKY
    }

    private fun loop(address: String, pin: String) {
        val adapter = getSystemService(BluetoothManager::class.java)?.adapter
        var backoff = 1000L
        while (!stopping) {
            if (adapter == null || !adapter.isEnabled) { _state.value = "Bluetooth désactivé"; Thread.sleep(3000); continue }
            try {
                runCatching { adapter.cancelDiscovery() }   // needs BLUETOOTH_SCAN on Android 12+: optional, never fatal
                _state.value = "Connexion à la TV…"
                val s = adapter.getRemoteDevice(address).createRfcommSocketToServiceRecord(UUID.fromString(Gw.SERVICE_UUID))
                sock = s
                s.connect()
                backoff = 1000
                _state.value = "La TV utilise l'Internet du téléphone"
                notify("La TV utilise l'Internet du téléphone")
                Exit(Mux(s.inputStream, s.outputStream), pin, connect = ::openOutbound, log = { Log.i(TAG, it) }, diag = ::runDiag).run()
            } catch (e: IOException) {
                if (e.message?.contains("PIN") == true) { _state.value = "Code PIN refusé par la TV"; stopping = true; break }
                if (!stopping) _state.value = "TV injoignable en Bluetooth, nouvel essai…"
            } finally { runCatching { sock?.close() } }
            if (!stopping) { Thread.sleep(backoff); backoff = minOf(backoff * 2, 15_000) }
        }
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
    }

    /** ping / traceroute from the phone (the TV's way out to the Internet); host already validated by the gateway. */
    private fun runDiag(kind: String, host: String, line: (String) -> Unit) {
        val ip = runCatching { InetAddress.getByName(host).hostAddress }.getOrElse { line("Nom introuvable : $host"); return }
        if (kind == "ping") {
            line("PING $host ($ip) depuis le téléphone")
            val p = ProcessBuilder("/system/bin/ping", "-c", "4", "-W", "2", ip).redirectErrorStream(true).start()
            p.inputStream.bufferedReader().forEachLine { if (it.isNotBlank()) line(it) }
            p.waitFor()
            return
        }
        // traceroute with ping's TTL: each hop answers "Time to live exceeded" from its own address.
        line("TRACEROUTE $host ($ip) depuis le téléphone, 20 sauts max")
        for (ttl in 1..20) {
            val t0 = System.nanoTime()
            val p = ProcessBuilder("/system/bin/ping", "-c", "1", "-W", "2", "-t", ttl.toString(), ip).redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText(); p.waitFor()
            val ms = (System.nanoTime() - t0) / 1_000_000
            val from = Regex("[Ff]rom ([0-9a-fA-F.:]+)").find(out)?.groupValues?.get(1)?.trimEnd(':')
            val time = Regex("time=([0-9.]+) ?ms").find(out)?.groupValues?.get(1)
            when {
                time != null -> { line("%2d  %-39s %s ms".format(ttl, ip, time)); line("Arrivé en $ttl sauts."); return }
                from != null -> line("%2d  %-39s ~%d ms".format(ttl, from, ms))
                else -> line("%2d  *".format(ttl))
            }
        }
        line("Destination non atteinte en 20 sauts.")
    }

    /** Outbound connection for the TV, on the phone's own network; the phone's loopback is off limits. */
    private fun openOutbound(t: castbridge.core.gateway.GwTarget): Socket {
        val addrs = InetAddress.getAllByName(t.host)
        if (addrs.any { it.isLoopbackAddress || it.isAnyLocalAddress }) throw SecurityException("loopback")
        var last: Exception? = null
        for (a in addrs) {
            try { return Socket().apply { tcpNoDelay = true; connect(InetSocketAddress(a, t.port), 15_000) } } catch (e: Exception) { last = e }
        }
        throw last ?: IOException("no address")
    }

    private fun notification(text: String): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CHANNEL, "Internet partagé avec la TV", NotificationManager.IMPORTANCE_LOW))
        val stop = android.app.PendingIntent.getService(this, 1, Intent(this, BtGatewayService::class.java).setAction(ACTION_STOP),
            android.app.PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("CastBridge").setContentText(text).setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Arrêter", stop).build()).build()
    }
    private fun notify(text: String) = getSystemService(NotificationManager::class.java).notify(NOTIF, notification(text))

    companion object {
        private const val TAG = "CastBridgeGW"
        private const val CHANNEL = "gateway"
        private const val NOTIF = 43
        private const val EXTRA_ADDR = "addr"
        private const val EXTRA_PIN = "pin"
        private const val ACTION_STOP = "castbridge.sender.GATEWAY_STOP"
        private val _state = MutableStateFlow("Partage inactif")
        val state: StateFlow<String> = _state

        fun start(ctx: Context, tvAddress: String, pin: String) =
            ctx.startForegroundService(Intent(ctx, BtGatewayService::class.java).putExtra(EXTRA_ADDR, tvAddress).putExtra(EXTRA_PIN, pin))
        fun stop(ctx: Context) = ctx.startService(Intent(ctx, BtGatewayService::class.java).setAction(ACTION_STOP))
    }
}
