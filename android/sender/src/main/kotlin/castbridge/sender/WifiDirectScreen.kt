package castbridge.sender

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import castbridge.core.tv.WifiDirect

/**
 * Joins the Wi-Fi Direct group created by the TV (WifiNetworkSpecifier, Android 10+), then routes the sockets towards the group
 * through it ([GroupNetworkRoute] / [castbridge.core.net.BoundRoute], one socket at a time) so the normal HTTP API works at
 * 192.168.49.1:8765. The system shows its own approval dialog; the phone's other apps keep their usual connection, and so does the
 * rest of this app (R-29: `bindProcessToNetwork` used to cut the Internet of the whole app while the group was joined).
 */
class DirectLink(ctx: Context) {
    sealed class State {
        object Idle : State()
        object Connecting : State()
        object Connected : State()
        data class Failed(val reason: String) : State()
    }

    private val cm = ctx.applicationContext.getSystemService(ConnectivityManager::class.java)
    private var callback: ConnectivityManager.NetworkCallback? = null
    /** The route to the joined group's addresses, null while no group is joined. */
    @Volatile private var route: castbridge.core.net.BoundRoute.Binding? = null
    var state by mutableStateOf<State>(State.Idle)
        private set

    fun connect(ssid: String, pass: String) {
        if (Build.VERSION.SDK_INT < 29) { state = State.Failed("Wi-Fi Direct : Android 10 minimum sur le téléphone"); return }
        disconnect()
        val spec = try { WifiNetworkSpecifier.Builder().setSsid(ssid).setWpa2Passphrase(pass).build() }
        catch (e: Exception) { state = State.Failed("Nom ou mot de passe invalide"); return }
        val req = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).setNetworkSpecifier(spec).build()
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { route = GroupNetworkRoute.install(network); state = State.Connected }
            override fun onUnavailable() { state = State.Failed("Connexion refusée ou TV introuvable") }
            override fun onLost(network: Network) { GroupNetworkRoute.release(route); route = null; state = State.Failed("Liaison perdue") }
        }
        callback = cb
        state = State.Connecting
        try { cm.requestNetwork(req, cb, 30_000) }
        catch (e: Exception) { state = State.Failed(e.message ?: "requête refusée"); callback = null }
    }

    fun disconnect() {
        callback?.let { runCatching { cm.unregisterNetworkCallback(it) } }
        callback = null
        GroupNetworkRoute.release(route); route = null
        if (state !is State.Failed) state = State.Idle
    }
}

@Composable
fun WifiDirectScreen() {
    val ctx = LocalContext.current
    val sp = remember { ctx.getSharedPreferences("castbridge_wd", Context.MODE_PRIVATE) }
    val link = remember { DirectLink(ctx) }
    DisposableEffect(Unit) { onDispose { link.disconnect() } }
    var ssid by remember { mutableStateOf(sp.getString("ssid", WifiDirect.networkName()) ?: "") }
    var pass by remember { mutableStateOf(sp.getString("pass", "") ?: "") }

    if (link.state == DirectLink.State.Connected) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Connecté à la TV en Wi-Fi Direct.", Modifier.weight(1f).padding(top = 10.dp))
                OutlinedButton(onClick = { link.disconnect() }) { Text("Quitter") }
            }
            TvScreen(fixedBase = WifiDirect.BASE_URL, extra = { AdminPanel(it) })
        }
        return
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Wi-Fi Direct : sur la TV, appuyez sur MENU > « Wi-Fi Direct : activer ». La TV affiche le nom du réseau, " +
            "le mot de passe et le PIN. Aucun routeur n'est nécessaire ; seuls les échanges avec la TV passent par ce réseau, " +
            "le reste de l'app garde son Internet.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(ssid, { ssid = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Nom du réseau (SSID)") })
        OutlinedTextField(pass, { pass = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Mot de passe") })
        val ok = WifiDirect.isValidNetworkName(ssid.trim()) && WifiDirect.isValidPassphrase(pass)
        Button(enabled = ok && link.state != DirectLink.State.Connecting, onClick = {
            sp.edit().putString("ssid", ssid.trim()).putString("pass", pass).apply()
            link.connect(ssid.trim(), pass)
        }) { Text(if (link.state == DirectLink.State.Connecting) "Connexion…" else "Se connecter à la TV") }
        (link.state as? DirectLink.State.Failed)?.let { Text(it.reason, color = MaterialTheme.colorScheme.error) }
    }
}
