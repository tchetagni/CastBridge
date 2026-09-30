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
 * Joins the Wi-Fi Direct group created by the TV (WifiNetworkSpecifier, Android 10+), then routes this
 * app's traffic through it so the normal HTTP API works at 192.168.49.1:8765. The system shows its own
 * approval dialog; the phone's other apps keep their usual connection.
 *
 * A singleton: the connection is meant to survive leaving this screen (so the user can connect here, then
 * cast from the player, which sees the TV at 192.168.49.1). Disconnect with [disconnect] or the "Quitter"
 * button.
 */
object DirectLink {
    sealed class State {
        object Idle : State()
        object Connecting : State()
        object Connected : State()
        data class Failed(val reason: String) : State()
    }

    var state by mutableStateOf<State>(State.Idle)
        private set
    private var cm: ConnectivityManager? = null
    private var callback: ConnectivityManager.NetworkCallback? = null

    fun connect(ctx: Context, ssid: String, pass: String) {
        if (Build.VERSION.SDK_INT < 29) { state = State.Failed("Wi-Fi Direct : Android 10 minimum sur le téléphone"); return }
        disconnect()
        val c = ctx.applicationContext.getSystemService(ConnectivityManager::class.java)
        cm = c
        val spec = try { WifiNetworkSpecifier.Builder().setSsid(ssid).setWpa2Passphrase(pass).build() }
        catch (e: Exception) { state = State.Failed("Nom ou mot de passe invalide"); return }
        val req = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).setNetworkSpecifier(spec).build()
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { c.bindProcessToNetwork(network); state = State.Connected }
            override fun onUnavailable() { state = State.Failed("Connexion refusée ou TV introuvable") }
            override fun onLost(network: Network) { c.bindProcessToNetwork(null); state = State.Failed("Liaison perdue") }
        }
        callback = cb
        state = State.Connecting
        try { c.requestNetwork(req, cb, 30_000) }
        catch (e: Exception) { state = State.Failed(e.message ?: "requête refusée"); callback = null }
    }

    fun disconnect() {
        cm?.let { c ->
            callback?.let { runCatching { c.unregisterNetworkCallback(it) } }
            callback = null
            runCatching { c.bindProcessToNetwork(null) }
        }
        cm = null
        if (state !is State.Failed) state = State.Idle
    }
}

@Composable
fun WifiDirectScreen() {
    val ctx = LocalContext.current
    val sp = remember { ctx.getSharedPreferences("castbridge_wd", Context.MODE_PRIVATE) }
    var ssid by remember { mutableStateOf(sp.getString("ssid", WifiDirect.networkName()) ?: "") }
    var pass by remember { mutableStateOf(sp.getString("pass", "") ?: "") }

    if (DirectLink.state == DirectLink.State.Connected) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Connecté à la TV en Wi-Fi Direct.", Modifier.weight(1f).padding(top = 10.dp))
                OutlinedButton(onClick = { DirectLink.disconnect() }) { Text("Quitter") }
            }
            TvScreen(fixedBase = WifiDirect.BASE_URL, extra = { AdminPanel(it) })
        }
        return
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Wi-Fi Direct : sur la TV, appuyez sur MENU > « Wi-Fi Direct : activer ». La TV affiche le nom du réseau, " +
            "le mot de passe et le PIN. Aucun routeur n'est nécessaire ; pendant la connexion, ce téléphone n'a pas Internet " +
            "pour cette app.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(ssid, { ssid = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Nom du réseau (SSID)") })
        OutlinedTextField(pass, { pass = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Mot de passe") })
        val ok = WifiDirect.isValidNetworkName(ssid.trim()) && WifiDirect.isValidPassphrase(pass)
        Button(enabled = ok && DirectLink.state != DirectLink.State.Connecting, onClick = {
            sp.edit().putString("ssid", ssid.trim()).putString("pass", pass).apply()
            DirectLink.connect(ctx, ssid.trim(), pass)
        }) { Text(if (DirectLink.state == DirectLink.State.Connecting) "Connexion…" else "Se connecter à la TV") }
        (DirectLink.state as? DirectLink.State.Failed)?.let { Text(it.reason, color = MaterialTheme.colorScheme.error) }
    }
}
