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
import castbridge.core.lots.HttpTvTransport
import castbridge.core.owner.ActivationScreenState
import castbridge.core.owner.ActivationSend
import castbridge.core.tv.TvClient
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

        // What the TV itself says about its activation (read through the link the phone already has with it): the phone must notice an activation done any way (Bluetooth, USB file, typed)
        val link by TvLinkManager.state.collectAsState()
        var tvView by remember { mutableStateOf<ActivationScreenState.View?>(null) }
        var lanBase by remember { mutableStateOf<Pair<String, String?>?>(null) }
        LaunchedEffect(link, busy) {
            val s = (link as? LinkUi.Connected)?.session
            val base = s?.base
            if (base == null) {
                val why = if (s == null) (if (link is LinkUi.NoTv) "Aucune TV n'est ajoutée dans CastBridge (onglet « CastBridge TV » > « Ajouter ma TV »), donc l'état d'activation ne peut pas être lu. L'envoi d'une clé, lui, n'en dépend pas." else "TV non jointe pour le moment : l'état d'activation n'est pas lisible.") else "TV jointe par Bluetooth seulement : l'état d'activation n'est pas lisible."
                tvView = ActivationScreenState.view(null, why, System.currentTimeMillis()); lanBase = null; return@LaunchedEffect
            }
            lanBase = base to s.credential
            while (true) {
                val r = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { org.json.JSONObject(TvClient(base, s.credential).raw("GET", "/api/activation")) } }
                r.onSuccess { j ->
                    val info = ActivationScreenState.Info(j.optBoolean("required"), j.optBoolean("locked"), j.optBoolean("trial"), j.optString("label"), if (j.isNull("usageEndsAt")) null else j.optLong("usageEndsAt"))
                    tvView = ActivationScreenState.view(info, null, System.currentTimeMillis())
                }.onFailure { tvView = ActivationScreenState.view(null, "L'état d'activation de la TV est illisible (version de CastBridge-TV trop ancienne ?).", System.currentTimeMillis()) }
                kotlinx.coroutines.delay(4_000)
            }
        }
        fun clean(t: String) = t.replace("\r", "").lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()
        var pinText by remember { mutableStateOf("") }; var askPin by remember { mutableStateOf(false) }
        fun sendBluetooth(tv: TvBluetooth.Tv, k: String, prefix: String) {
            busy = true; msg = prefix + if (!tv.bonded) "Appairage : validez le code sur la TV et sur le téléphone…" else "Envoi par Bluetooth…"
            Thread {
                val a = runCatching { TvBluetooth.with(this@ActivateTvActivity, tv) { c -> c.sendActivation(k) } }
                runOnUiThread {
                    busy = false
                    a.onSuccess { r -> val o = ActivationScreenState.sendOutcome(r.ok, r.message); ok = o.ok; msg = o.text; if (r.ok) refresh() }
                     .onFailure { ok = false; msg = it.message ?: "Échec de l'envoi" }
                }
            }.start()
        }
        fun send(forceBluetooth: Boolean) {
            val k = clean(key); val tv = chosen
            if (forceBluetooth) { if (tv != null) { askPin = false; sendBluetooth(tv, k, "") }; return }
            val lan = lanBase
            val choice = ActivationSend.choose(lan?.first, lan?.second, pinText)
            when (choice.channel) {
                ActivationSend.Channel.BLUETOOTH -> if (tv != null) sendBluetooth(tv, k, choice.explanation?.plus(" ").orEmpty()) else { ok = false; msg = "Aucune TV trouvée : allumez la TV et ouvrez CastBridge-TV." }
                ActivationSend.Channel.LAN_ASKS_PIN -> { askPin = true; ok = false; msg = choice.explanation }
                ActivationSend.Channel.LAN_WITH_PIN -> {
                    busy = true; ok = false; msg = "Envoi par le Wi-Fi…"
                    val t = HttpTvTransport(lan!!.first, choice.pin)
                    Thread {
                        val r = runCatching { ActivationSend.sendLan(t, k) }
                        runOnUiThread {
                            busy = false
                            val res = r.getOrNull()
                            when {
                                res == null -> { ok = false; msg = r.exceptionOrNull()?.message ?: "Échec de l'envoi" }
                                res.pinRefused -> { askPin = true; pinText = ""; ok = false; msg = res.message }
                                res.linkDown && tv != null -> sendBluetooth(tv, k, res.message + " Essai par Bluetooth. ")
                                else -> { val o = ActivationScreenState.sendOutcome(res.ok, res.message); ok = o.ok; msg = o.text + if (res.linkDown) " Ouvrez CastBridge-TV et réessayez." else ""; if (res.ok) { askPin = false; pinText = "" } }
                            }
                        }
                    }.start()
                }
            }
        }
        val shown = if (allPaired) TvBluetooth.pairedTvs(this).let { p -> (tvs + p).distinctBy { it.address } } else tvs.filter { it.sure || it.bonded }.let { l -> if (l.any { it.sure }) l.filter { it.sure } else l }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Activer la TV", style = MaterialTheme.typography.headlineSmall)
            tvView?.let { v ->
                Text(v.headline, color = if (v.good) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
                v.detail?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
            Text("1. Copiez la clé d'activation (ou de production) reçue (message, e-mail…), puis collez-la ici.", style = MaterialTheme.typography.bodyMedium)
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
            if (askPin) {
                OutlinedTextField(pinText, { pinText = it.filter(Char::isDigit).take(6); msg = null }, label = { Text("Code de connexion de la TV (6 chiffres)") }, singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            }
            Button({ send(false) }, enabled = (lanBase != null || chosen != null) && clean(key).length >= 20 && !busy, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "En cours…" else if (askPin) "3. Envoyer par le Wi-Fi avec ce code" else "3. Envoyer à la TV") }
            if (askPin && chosen != null) OutlinedButton({ send(true) }, enabled = !busy && clean(key).length >= 20, modifier = Modifier.fillMaxWidth()) { Text("Envoyer par Bluetooth à la place") }
            msg?.let { Text(it, color = if (ok) MaterialTheme.colorScheme.primary else if (busy) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error) }
            Text("Pour recevoir une clé (ou passer de l'essai à la production) : touchez « Demander la clé de production » et envoyez la demande au propriétaire de CastBridge.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton({
                busy = true; ok = false; msg = "Lecture de la demande de la TV…"
                val lan = lanBase; val tv = chosen
                Thread {
                    val req = runCatching {
                        if (lan != null) org.json.JSONObject(TvClient(lan.first, lan.second).raw("GET", "/api/activation/request")).getString("request")
                        else if (tv != null) TvBluetooth.with(this@ActivateTvActivity, tv) { c -> c.deviceInfo() } ?: error("La TV n'a pas répondu")
                        else error("Aucune TV trouvée : allumez la TV et ouvrez CastBridge-TV.")
                    }
                    runOnUiThread {
                        busy = false
                        req.onSuccess { t -> msg = "Demande prête : choisissez comment l'envoyer."; ok = true
                            val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, "Demande de passage en production").putExtra(Intent.EXTRA_TEXT, ActivationScreenState.requestShare(t))
                            startActivity(Intent.createChooser(i, "Envoyer la demande"))
                        }.onFailure { msg = it.message ?: "Impossible de lire la demande de la TV" }
                    }
                }.start()
            }, enabled = !busy && (lanBase != null || chosen != null), modifier = Modifier.fillMaxWidth()) { Text("Demander la clé de production") }
        }
    }

    companion object { fun open(ctx: Context) = ctx.startActivity(Intent(ctx, ActivateTvActivity::class.java)) }
}
