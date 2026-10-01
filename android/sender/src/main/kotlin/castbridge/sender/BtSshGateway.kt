package castbridge.sender

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import castbridge.core.tunnel.BtDialException
import castbridge.core.tunnel.TunnelGateway
import castbridge.core.tv.BtProtocol
import castbridge.core.tv.Link
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.io.IOException
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * "Passerelle Bluetooth": the phone listens on 127.0.0.1 and relays every TCP connection, byte for byte, to the TV over a secure
 * RFCOMM link, for two services of CastBridge-TV:
 *  - SSH (127.0.0.1:2222 -> "CastBridge SSH"): SSH stays end to end between the client (Termux, a computer on the phone's hotspot)
 *    and the TV; the phone only sees encrypted bytes. May also listen on the phone's networks if asked.
 *  - API (127.0.0.1:18765 -> "CastBridge API"): the TV's HTTP API (library, uploads, install, remote, parental...) exactly as on
 *    Wi-Fi; the TV still checks the PIN / token. Never exposed on the network.
 * The logic (timeouts, cleanup, messages) is castbridge.core.tunnel.TunnelGateway, tested on the JVM; this service only supplies the
 * secure RFCOMM sockets and the notification.
 */
@SuppressLint("MissingPermission")    // BLUETOOTH_CONNECT is checked by the panel (and again in dial) before connecting
class BtSshGatewayService : Service() {
    data class State(val running: Boolean = false, val tv: String = "",
                     val ssh: TunnelGateway.State = TunnelGateway.State(), val api: TunnelGateway.State = TunnelGateway.State(),
                     val message: String? = null)

    private var ssh: TunnelGateway? = null
    private var api: TunnelGateway? = null
    private var tvAddress: String? = null
    private val timers = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "bt-gw-timer").apply { isDaemon = true } }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        val addr = intent?.getStringExtra(EXTRA_ADDR) ?: return START_NOT_STICKY
        val name = intent.getStringExtra(EXTRA_NAME) ?: addr
        if (tvAddress != null && tvAddress != addr) { note("Une passerelle vers une autre TV est déjà active : arrêtez-la d'abord"); return START_NOT_STICKY }
        val wantSsh = intent.getBooleanExtra(EXTRA_SSH, true)
        val wantApi = intent.getBooleanExtra(EXTRA_API, true)
        val lan = intent.getBooleanExtra(EXTRA_LAN, false)
        try {
            val n = notification("Passerelle Bluetooth vers $name : 127.0.0.1")
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE) else startForeground(NOTIF, n)
        } catch (e: Exception) { note("Service refusé par le système : ${e.message}"); if (tvAddress == null) stopSelf(); return START_NOT_STICKY }
        tvAddress = addr
        _state.update { it.copy(running = true, tv = name) }
        if (wantSsh && ssh == null) ssh = open("ssh", false, addr, BtProtocol.SSH_SERVICE_UUID, if (lan) "0.0.0.0" else "127.0.0.1", PORT) { s -> _state.update { it.copy(ssh = s) } }
        if (wantApi && api == null) api = open("api", true, addr, BtProtocol.API_SERVICE_UUID, "127.0.0.1", API_PORT) { s -> _state.update { it.copy(api = s) } }
        if (ssh == null && api == null) stopSelf()
        return START_NOT_STICKY
    }

    private fun open(label: String, handshake: Boolean, addr: String, uuid: String, host: String, port: Int, onState: (TunnelGateway.State) -> Unit): TunnelGateway? {
        lateinit var gw: TunnelGateway
        gw = TunnelGateway(label, handshake, { dial(addr, uuid, label) }, maxLinks = if (handshake) 4 else 2,
            log = { Log.i(TAG, it) }, onChange = { onState(gw.state) })
        return try { gw.start(host, port); gw } catch (e: IOException) {
            note("Port $port déjà utilisé sur le téléphone (${e.message})"); null
        }
    }

    /** One secure RFCOMM link to the TV's service [uuid]; BluetoothSocket.connect() has no timeout, so a timer closes the socket if it hangs. */
    private fun dial(addr: String, uuid: String, label: String): Link {
        val adapter = getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null || !adapter.isEnabled) throw BtDialException("Bluetooth désactivé sur le téléphone")
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            throw BtDialException("Permission Bluetooth refusée à CastBridge (réglages de l'application)")
        runCatching { adapter.cancelDiscovery() }            // needs BLUETOOTH_SCAN on Android 12+: optional, never fatal
        val dev = adapter.getRemoteDevice(addr)
        if (dev.bondState != BluetoothDevice.BOND_BONDED) throw BtDialException("La TV n'est plus appairée avec ce téléphone (réglages Bluetooth)")
        var last: IOException? = null
        repeat(2) { attempt ->       // a second try with a fresh socket also refreshes a stale SDP answer (the TV re-published the service)
            val sock = dev.createRfcommSocketToServiceRecord(UUID.fromString(uuid))     // secure: authenticated and encrypted by the pairing
            val guard = timers.schedule(Runnable { runCatching { sock.close() } }, DIAL_LIMIT_S, TimeUnit.SECONDS)
            try {
                sock.connect(); guard.cancel(false)
                return object : Link {
                    override val input = sock.inputStream
                    override val output = sock.outputStream
                    override fun close() { runCatching { sock.close() } }
                }
            } catch (e: IOException) {
                guard.cancel(false); runCatching { sock.close() }; last = e
                Log.i(TAG, "$label: connect attempt ${attempt + 1} failed: ${e.message}")
                if (attempt == 0) Thread.sleep(600)
            }
        }
        throw BtDialException("Service introuvable ou fermé par la TV (${last?.message}). Sur la TV : MENU > Administration " +
            (if (label == "ssh") "> activer SSH" else "> API par Bluetooth") + " ; CastBridge-TV à jour ; TV allumée et à portée.")
    }

    private fun note(m: String) { Log.i(TAG, m); _state.update { it.copy(message = m) } }

    private fun notification(text: String): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Passerelle Bluetooth", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this, 4, Intent(this, BtSshGatewayService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL).setContentTitle("CastBridge").setContentText(text)
            .setSmallIcon(R.drawable.ic_stat_castbridge).setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Arrêter", stop).build()).build()
    }

    override fun onDestroy() {
        ssh?.stop(); api?.stop(); ssh = null; api = null; tvAddress = null
        timers.shutdownNow()
        _state.update { it.copy(running = false, ssh = TunnelGateway.State(), api = TunnelGateway.State()) }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "CastBridgeSshGw"
        const val PORT = 2222
        /** Local port of the API tunnel: http://127.0.0.1:18765 is the TV's HTTP API (the same requests as http://<tv>:8765). */
        const val API_PORT = 18765
        private const val DIAL_LIMIT_S = 20L
        private const val CHANNEL = "sshgw"
        private const val NOTIF = 5
        private const val ACTION_STOP = "castbridge.STOP_SSH_GATEWAY"
        private const val EXTRA_ADDR = "addr"
        private const val EXTRA_NAME = "name"
        private const val EXTRA_LAN = "lan"
        private const val EXTRA_SSH = "ssh"
        private const val EXTRA_API = "api"
        private val _state = MutableStateFlow(State())
        val state: StateFlow<State> = _state

        /** The local end of the API tunnel for the TV at [address], when it runs: the phone's TV clients use it as a last-resort route. */
        fun apiBase(address: String?): String? =
            if (_state.value.api.running && address != null && address.equals(lastAddress, true)) "http://127.0.0.1:$API_PORT" else null

        @Volatile private var lastAddress: String? = null

        fun start(ctx: Context, address: String, name: String, exposeLan: Boolean, ssh: Boolean = true, api: Boolean = true) {
            lastAddress = address
            _state.value = _state.value.copy(message = null)
            ctx.startForegroundService(Intent(ctx, BtSshGatewayService::class.java)
                .putExtra(EXTRA_ADDR, address).putExtra(EXTRA_NAME, name).putExtra(EXTRA_LAN, exposeLan).putExtra(EXTRA_SSH, ssh).putExtra(EXTRA_API, api))
        }

        /** Starts the API tunnel on its own when the app has no IP route to the TV; false if it already runs (nothing to wait for). */
        fun ensureApi(ctx: Context, address: String, name: String): Boolean {
            if (_state.value.api.running) return false
            start(ctx, address, name, exposeLan = false, ssh = false, api = true)
            return true
        }

        fun stop(ctx: Context) { runCatching { ctx.startService(Intent(ctx, BtSshGatewayService::class.java).setAction(ACTION_STOP)) } }
    }
}

/** Panel of the Bluetooth tab: choose the paired TV, start/stop the Bluetooth gateway (SSH and API), how to use each tunnel. */
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
    var wantSsh by rememberSaveable { mutableStateOf(true) }
    var wantApi by rememberSaveable { mutableStateOf(true) }
    val st by BtSshGatewayService.state.collectAsState()

    HorizontalDivider(Modifier.padding(vertical = 8.dp))
    Text("Passerelle Bluetooth", style = MaterialTheme.typography.titleSmall)
    Text("Tout faire avec la TV sans réseau commun : le téléphone écoute sur 127.0.0.1 et relaie vers la TV par Bluetooth. " +
        "« API » = les mêmes requêtes qu'en Wi-Fi (bibliothèque, envoi, installation, télécommande, contrôle parental…), la TV vérifie toujours le code. " +
        "« SSH » reste chiffré de bout en bout avec votre clé. Sur la TV : MENU > Administration (activer SSH ; « API par Bluetooth » est active par défaut). " +
        "Débit ~100-300 ko/s : bien pour commander, lent pour de gros fichiers.", style = MaterialTheme.typography.bodySmall)
    if (!granted) { askUi(); return }
    if (st.running) {
        Text("Active vers ${st.tv}", style = MaterialTheme.typography.bodyMedium)
        if (st.api.running) {
            Text("API : http://${st.api.listen}   ·   ${st.api.active} liaison(s)   ·   envoyés ${formatSize(st.api.up)}, reçus ${formatSize(st.api.down)}", style = MaterialTheme.typography.bodyMedium)
            Text("Depuis ce téléphone (Termux) : curl -H 'X-CB-Pin: <code>' http://${st.api.listen}/api/hello", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            st.api.message?.let { Text("API — $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        }
        if (st.ssh.running) {
            Text("SSH : ${st.ssh.listen}   ·   ${st.ssh.active} connexion(s)   ·   envoyés ${formatSize(st.ssh.up)}, reçus ${formatSize(st.ssh.down)}", style = MaterialTheme.typography.bodyMedium)
            Text("Depuis Termux : ssh -p ${BtSshGatewayService.PORT} tv@127.0.0.1", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            if (st.ssh.listen.contains("0.0.0.0")) Text("Exposée aussi sur le réseau local / point d'accès du téléphone : ssh -p ${BtSshGatewayService.PORT} tv@<adresse du téléphone>",
                fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            st.ssh.message?.let { Text("SSH — $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        }
        OutlinedButton(onClick = { BtSshGatewayService.stop(ctx) }) { Text("Arrêter la passerelle") }
    } else {
        devices.forEach { (name, addr) ->
            Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(selected == addr, { selected = addr }); Text("$name ($addr)") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(wantApi, { wantApi = it }); Text("API de la TV (127.0.0.1:${BtSshGatewayService.API_PORT})", style = MaterialTheme.typography.bodySmall) }
        Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(wantSsh, { wantSsh = it }); Text("SSH (127.0.0.1:${BtSshGatewayService.PORT})", style = MaterialTheme.typography.bodySmall) }
        if (wantSsh) Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(lan, { lan = it })
            Text("Exposer aussi le SSH sur le réseau local / point d'accès du téléphone", style = MaterialTheme.typography.bodySmall)
        }
        if (wantSsh && lan) Text("Attention : tout appareil de ces réseaux pourra joindre le SSH de la TV (qui exige toujours une clé autorisée). " +
            "N'activez ceci que sur votre propre point d'accès. L'API n'est jamais exposée sur le réseau.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        Button(enabled = selected != null && (wantSsh || wantApi), onClick = {
            val a = selected!!; BtSshGatewayService.start(ctx, a, devices.firstOrNull { it.second == a }?.first ?: a, lan && wantSsh, wantSsh, wantApi)
        }) { Text("Démarrer la passerelle") }
    }
    st.message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
}
