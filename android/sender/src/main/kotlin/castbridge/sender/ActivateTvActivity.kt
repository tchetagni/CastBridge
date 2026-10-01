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
 * « Activer la TV » (open to everyone, like the rest of the phone app), self-configuring: the phone finds the CastBridge-TV by itself (paired ones are
 * recognised by the services they declare; otherwise it looks around and pairs), the owner only pastes the key and touches « Envoyer ». Nothing secret
 * here: the key is signed and bound to one TV, the TV verifies it itself and refuses anything else.
 */
class ActivateTvActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CastTheme { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { Screen() } } }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable private fun Screen() {
        var key by remember { mutableStateOf("") }
        var tvs by remember { mutableStateOf(emptyList<TvBluetooth.Tv>()) }
        var chosen by remember { mutableStateOf<TvBluetooth.Tv?>(null) }
        var scanning by remember { mutableStateOf(false) }; var scanned by remember { mutableStateOf(false) }; var allPaired by remember { mutableStateOf(false) }
        var busy by remember { mutableStateOf(false) }; var msg by remember { mutableStateOf<String?>(null) }; var ok by remember { mutableStateOf(false) }
        var granted by remember { mutableStateOf(TvBluetooth.permitted(this)) }

        fun merge(found: TvBluetooth.Tv) { runOnUiThread { tvs = (tvs.filter { it.address != found.address } + found).sortedWith(compareByDescending<TvBluetooth.Tv> { it.sure }.thenBy { it.name.lowercase() }); if (chosen == null) chosen = tvs.first() } }
        fun refresh() { tvs = TvBluetooth.pairedTvs(this@ActivateTvActivity); if (chosen == null || tvs.none { it.address == chosen?.address }) chosen = tvs.firstOrNull { it.sure } ?: tvs.singleOrNull() }
        fun search() {
            if (scanning) return; scanning = true; msg = null
            Thread { runCatching { TvBluetooth.scan(this@ActivateTvActivity) { merge(it) } }; runOnUiThread { scanning = false; scanned = true } }.start()
        }
        val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted = TvBluetooth.permitted(this); if (granted) { refresh(); search() } }
        LaunchedEffect(granted) { if (granted) { refresh(); if (tvs.none { it.sure }) search() } }

        fun clean(t: String) = t.replace("\r", "").lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()
        val shown = if (allPaired) TvBluetooth.pairedTvs(this).let { p -> (tvs + p).distinctBy { it.address } } else tvs.filter { it.sure || it.bonded }.let { l -> if (l.any { it.sure }) l.filter { it.sure } else l }
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

            Text("2. Votre TV (allumée, CastBridge-TV ouvert, à proximité) est cherchée automatiquement.", style = MaterialTheme.typography.bodyMedium)
            if (!granted) {
                Text("Autorisez « Appareils à proximité » pour que le téléphone trouve la TV.", style = MaterialTheme.typography.bodySmall)
                Button({ ask.launch(TvBluetooth.missingPermissions(this@ActivateTvActivity)) }) { Text("Autoriser le Bluetooth") }
            } else {
                if (shown.isEmpty()) Text(if (scanning) "Recherche de la TV…" else if (scanned) "Aucune TV CastBridge trouvée. Vérifiez qu'elle est allumée et que CastBridge-TV est ouvert (l'écran d'activation la rend visible), puis relancez la recherche." else "…", style = MaterialTheme.typography.bodySmall)
                else FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    shown.forEach { tv -> FilterChip(chosen?.address == tv.address, { chosen = tv; msg = null }, { Text(tv.name + if (!tv.bonded) " (à appairer)" else "") }) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ search() }, enabled = !scanning) { Text(if (scanning) "Recherche…" else "Chercher à nouveau") }
                    TextButton({ allPaired = !allPaired; refresh() }) { Text(if (allPaired) "Masquer les autres appareils" else "Ma TV n'apparaît pas") }
                }
            }
            Button({
                val tv = chosen ?: return@Button; val k = clean(key)
                busy = true; msg = if (!tv.bonded) "Appairage : validez le code sur la TV et sur le téléphone…" else "Envoi…"
                Thread {
                    val a = runCatching { TvBluetooth.with(this@ActivateTvActivity, tv) { c -> c.sendActivation(k) } }
                    runOnUiThread {
                        busy = false
                        a.onSuccess { r -> ok = r.ok; msg = if (r.ok) "Clé envoyée : ${r.message}" else "Refusée par la TV : ${r.message}"; if (r.ok) refresh() }
                         .onFailure { ok = false; msg = it.message ?: "Échec de l'envoi" }
                    }
                }.start()
            }, enabled = chosen != null && clean(key).length >= 20 && !busy, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "En cours…" else "3. Envoyer à la TV") }
            msg?.let { Text(it, color = if (ok) MaterialTheme.colorScheme.primary else if (busy) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error) }
            Text("Pour recevoir une clé : ouvrez la TV, elle affiche son code d'appareil ; envoyez-le au propriétaire de CastBridge.", style = MaterialTheme.typography.bodySmall)
        }
    }

    companion object { fun open(ctx: Context) = ctx.startActivity(Intent(ctx, ActivateTvActivity::class.java)) }
}
