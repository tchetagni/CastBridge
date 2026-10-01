package castbridge.sender

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import castbridge.owner.TvBluetooth

/**
 * « Activer la TV » (open to everyone, like the rest of the phone app): paste the activation key received from the owner, pick the paired TV, send it by
 * Bluetooth. Nothing secret here: the key is signed and bound to one TV, the TV verifies it itself and refuses anything else. Nothing to type on the remote.
 */
class ActivateTvActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CastTheme { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { Screen() } } }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable private fun Screen() {
        var key by remember { mutableStateOf("") }
        var tvs by remember { mutableStateOf(TvBluetooth.paired(this)) }
        var chosen by remember { mutableStateOf<TvBluetooth.Tv?>(null) }
        var busy by remember { mutableStateOf(false) }; var msg by remember { mutableStateOf<String?>(null) }; var ok by remember { mutableStateOf(false) }
        val askBt = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tvs = TvBluetooth.paired(this) }
        LaunchedEffect(tvs) { if (chosen == null && tvs.size == 1) chosen = tvs[0] }
        fun clean(t: String) = t.replace("\r", "").lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Activer la TV", style = MaterialTheme.typography.headlineSmall)
            Text("1. Copiez la clé d'activation reçue (message, e-mail…), puis collez-la ici.", style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(key, { key = it; msg = null }, label = { Text("Clé d'activation") }, textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp))
            Button({
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val t = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this@ActivateTvActivity)?.toString().orEmpty()
                if (t.isBlank()) msg = "Le presse-papiers est vide : copiez d'abord la clé." else { key = clean(t); msg = null }
            }, modifier = Modifier.fillMaxWidth()) { Text("Coller la clé") }
            Text("2. Choisissez votre TV (appairée en Bluetooth avec ce téléphone).", style = MaterialTheme.typography.bodyMedium)
            if (!TvBluetooth.permitted(this@ActivateTvActivity)) {
                Button({ if (android.os.Build.VERSION.SDK_INT >= 31) askBt.launch(android.Manifest.permission.BLUETOOTH_CONNECT) }) { Text("Autoriser le Bluetooth") }
            } else if (tvs.isEmpty()) Text("Aucune TV appairée : appairez-la d'abord dans les réglages Bluetooth du téléphone.", style = MaterialTheme.typography.bodySmall)
            else FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { tvs.forEach { tv -> FilterChip(chosen?.address == tv.address, { chosen = tv; msg = null }, { Text(tv.name) }) } }
            Button({
                val tv = chosen ?: return@Button; val k = clean(key)
                busy = true; msg = null
                Thread {
                    val a = runCatching { TvBluetooth.with(this@ActivateTvActivity, tv.address) { c -> c.sendActivation(k) } }
                    runOnUiThread {
                        busy = false
                        a.onSuccess { r -> ok = r.ok; msg = if (r.ok) "TV activée. Elle se déverrouille dans quelques secondes." else "Refusée par la TV : ${r.message}" }
                         .onFailure { ok = false; msg = it.message ?: "Échec de l'envoi" }
                    }
                }.start()
            }, enabled = chosen != null && clean(key).length >= 20 && !busy, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Envoi…" else "3. Envoyer à la TV") }
            msg?.let { Text(it, color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) }
            Text("Pour recevoir une clé : ouvrez la TV, elle affiche son code d'appareil ; envoyez-le au propriétaire de CastBridge.", style = MaterialTheme.typography.bodySmall)
        }
    }

    companion object { fun open(ctx: Context) = ctx.startActivity(Intent(ctx, ActivateTvActivity::class.java)) }
}
