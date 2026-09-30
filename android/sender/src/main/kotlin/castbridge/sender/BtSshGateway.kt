package castbridge.sender

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import castbridge.core.ssh.ByteRelay
import castbridge.core.tv.BtProtocol
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

/**
 * "Passerelle SSH Bluetooth": the phone listens on 127.0.0.1:2222 (and, only if asked, on its local networks / hotspot) and
 * relays every TCP connection, byte for byte, to the TV's "CastBridge SSH" RFCOMM service. SSH stays end to end between
 * the client (Termux, a computer on the phone's hotspot) and the TV: the phone only sees encrypted bytes.
 */
@SuppressLint("MissingPermission")    // BLUETOOTH_CONNECT is checked by the panel before starting
class BtSshGatewayService : Service() {
    data class State(val running: Boolean = false, val tv: String = "", val listen: String = "", val active: Int = 0,
                     val up: Long = 0, val down: Long = 0, val message: String? = null)

    private var server: ServerSocket? = null
    private val relays = ConcurrentHashMap.newKeySet<ByteRelay>()
    @Volatile private var stopping = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        val addr = intent?.getStringExtra(EXTRA_ADDR) ?: return START_NOT_STICKY
        val name = intent.getStringExtra(EXTRA_NAME) ?: addr
        val lan = intent.getBooleanExtra(EXTRA_LAN, false)
        if (server != null) return START_NOT_STICKY
        try {
            val n = notification("Passerelle SSH vers $name : 127.0.0.1:$PORT")
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE) else startForeground(NOTIF, n)
        } catch (e: Exception) { _state.value = State(message = "Service refusé par le système : ${e.message}"); stopSelf(); return START_NOT_STICKY }
        val ss = try {
            ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(if (lan) InetAddress.getByName("0.0.0.0") else InetAddress.getByName("127.0.0.1"), PORT), 4) }
        } catch (e: IOException) {
            _state.value = State(message = "Port $PORT déjà utilisé sur le téléphone (${e.message})"); stopSelf(); return START_NOT_STICKY
        }
        server = ss
        val listen = if (lan) (listOf("127.0.0.1") + lanAddresses()).joinToString(", ") { "$it:$PORT" } else "127.0.0.1:$PORT"
        _state.value = State(true, name, listen)
        thread(name = "ssh-gw-accept", isDaemon = true) { acceptLoop(ss, addr) }
        return START_NOT_STICKY
    }

    private fun acceptLoop(ss: ServerSocket, addr: String) {
        while (!stopping) {
            val client = try { ss.accept() } catch (e: IOException) { break }
            thread(name = "ssh-gw-link", isDaemon = true) { serve(client, addr) }
        }
    }

    private fun serve(client: Socket, addr: String) {
        if (relays.size >= MAX_LINKS) { runCatching { client.close() }; note("Trop de connexions simultanées (max $MAX_LINKS)"); return }
        val adapter = getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null || !adapter.isEnabled) { runCatching { client.close() }; note("Bluetooth désactivé"); return }
        val bt = try {
            adapter.cancelDiscovery()
            adapter.getRemoteDevice(addr).createRfcommSocketToServiceRecord(UUID.fromString(BtProtocol.SSH_SERVICE_UUID)).also { it.connect() }
        } catch (e: Exception) {
            runCatching { client.close() }
            note("TV injoignable en Bluetooth (SSH activé sur la TV ? appareils appairés ?) : ${e.message}")
            return
        }
        client.tcpNoDelay = true
        val r = ByteRelay(client.getInputStream(), client.getOutputStream(), bt.inputStream, bt.outputStream,
            { client.close() }, { bt.close() }, 32 * 1024, "ssh-gw")
        relays += r
        update()
        r.start()
        val ticker = thread(isDaemon = true) { try { while (!r.isClosed) { Thread.sleep(1000); update() } } catch (_: InterruptedException) {} }
        r.join()
        ticker.interrupt()
        relays -= r
        total[0] += r.bytesAtoB.get(); total[1] += r.bytesBtoA.get()
        update()
    }

    private val total = LongArray(2)

    private fun update() {
        val s = _state.value
        _state.value = s.copy(active = relays.size, up = total[0] + relays.sumOf { it.bytesAtoB.get() }, down = total[1] + relays.sumOf { it.bytesBtoA.get() })
    }

    private fun note(m: String) { Log.i(TAG, m); _state.value = _state.value.copy(message = m) }

    private fun lanAddresses(): List<String> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }.filter { it is Inet4Address && it.isSiteLocalAddress }.mapNotNull { it.hostAddress }
    }.getOrDefault(emptyList())

    private fun notification(text: String): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Passerelle SSH Bluetooth", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this, 4, Intent(this, BtSshGatewayService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL).setContentTitle("CastBridge").setContentText(text)
            .setSmallIcon(R.drawable.ic_stat_castbridge).setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Arrêter", stop).build()).build()
    }

    override fun onDestroy() {
        stopping = true
        runCatching { server?.close() }; server = null
        relays.forEach { it.close() }
        _state.value = _state.value.copy(running = false, active = 0)
        super.onDestroy()
    }

    companion object {
        private const val TAG = "CastBridgeSshGw"
        const val PORT = 2222
        private const val MAX_LINKS = 2
        private const val CHANNEL = "sshgw"
        private const val NOTIF = 5
        private const val ACTION_STOP = "castbridge.STOP_SSH_GATEWAY"
        private const val EXTRA_ADDR = "addr"
        private const val EXTRA_NAME = "name"
        private const val EXTRA_LAN = "lan"
        private val _state = MutableStateFlow(State())
        val state: StateFlow<State> = _state

        fun start(ctx: Context, address: String, name: String, exposeLan: Boolean) {
            _state.value = State()
            ctx.startForegroundService(Intent(ctx, BtSshGatewayService::class.java)
                .putExtra(EXTRA_ADDR, address).putExtra(EXTRA_NAME, name).putExtra(EXTRA_LAN, exposeLan))
        }

        fun stop(ctx: Context) { runCatching { ctx.startService(Intent(ctx, BtSshGatewayService::class.java).setAction(ACTION_STOP)) } }
    }
}

/** Panel of the Bluetooth tab: choose the paired TV, start/stop the SSH gateway, how to connect. */
@SuppressLint("MissingPermission")
@Composable
fun BtSshGatewayPanel() {
    val ctx = LocalContext.current
    val (granted, askUi) = rememberBtPermission()
    val adapter = remember { ctx.getSystemService(BluetoothManager::class.java)?.adapter }
    val devices = remember(granted) {
        if (granted) runCatching { adapter?.bondedDevices.orEmpty().map { (it.name ?: it.address) to it.address } }.getOrDefault(emptyList()) else emptyList()
    }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var lan by rememberSaveable { mutableStateOf(false) }
    val st by BtSshGatewayService.state.collectAsState()

    HorizontalDivider(Modifier.padding(vertical = 8.dp))
    Text("Passerelle SSH Bluetooth", style = MaterialTheme.typography.titleSmall)
    Text("Garder l'accès SSH à la TV sans réseau commun : le téléphone écoute sur 127.0.0.1:${BtSshGatewayService.PORT} et relaie vers la TV " +
        "par Bluetooth. SSH reste chiffré de bout en bout, avec votre clé. Sur la TV : activer SSH (MENU > SSH) ; débit ~100-300 ko/s : " +
        "bien pour un shell, lent pour SFTP.", style = MaterialTheme.typography.bodySmall)
    if (!granted) { askUi(); return }
    if (st.running) {
        Text("Active vers ${st.tv}\nÉcoute : ${st.listen}\nConnexions : ${st.active}   ·   envoyés ${formatSize(st.up)}, reçus ${formatSize(st.down)}",
            style = MaterialTheme.typography.bodyMedium)
        Text("Depuis Termux : ssh -p ${BtSshGatewayService.PORT} tv@127.0.0.1", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
        if (st.listen.contains(",")) Text("Depuis un ordinateur sur le point d'accès du téléphone : ssh -p ${BtSshGatewayService.PORT} tv@<adresse ci-dessus>",
            fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { BtSshGatewayService.stop(ctx) }) { Text("Arrêter la passerelle") }
    } else {
        devices.forEach { (name, addr) ->
            Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(selected == addr, { selected = addr }); Text("$name ($addr)") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(lan, { lan = it })
            Text("Exposer aussi sur le réseau local / point d'accès du téléphone", style = MaterialTheme.typography.bodySmall)
        }
        if (lan) Text("Attention : tout appareil de ces réseaux pourra joindre le SSH de la TV (qui exige toujours une clé autorisée). " +
            "N'activez ceci que sur votre propre point d'accès.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        Button(enabled = selected != null, onClick = {
            val a = selected!!; BtSshGatewayService.start(ctx, a, devices.firstOrNull { it.second == a }?.first ?: a, lan)
        }) { Text("Démarrer la passerelle") }
    }
    st.message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
}
