package castbridge.sender

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import castbridge.core.remote.smart.AttemptOutcome
import castbridge.core.remote.smart.StrategyStatus
import castbridge.core.remote.smart.Vendor

/** « Ma TV » (docs/REMOTE.md): who the TV is, which remote strategy works, a reversible test, a manual choice, and a diagnostic. */
class MyTvActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SmartRemote.resume(this)
        setContent { CastTheme { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { MyTvScreen(onClose = ::finish) } } }
    }

    companion object { fun open(ctx: Context) = ctx.startActivity(Intent(ctx, MyTvActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private fun outcomeText(o: AttemptOutcome) = when (o) {
    AttemptOutcome.OK -> "réussie"; AttemptOutcome.CONFIRMED -> "réussie (confirmée)"; AttemptOutcome.TO_CONFIRM -> "connectée, à confirmer avec « Tester »"
    AttemptOutcome.FAILED -> "échec"; AttemptOutcome.NEEDS_PAIRING -> "appairage requis"; AttemptOutcome.SKIPPED -> "ignorée"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyTvScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val ui by SmartRemote.ui.collectAsState()
    var host by remember(ui.host) { mutableStateOf(ui.host.orEmpty()) }
    var code by remember { mutableStateOf("") }
    val cs = MaterialTheme.colorScheme
    Scaffold(containerColor = cs.background, topBar = {
        TopAppBar(title = { Text("Ma TV") }, navigationIcon = { IconButton(onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Fermer") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.surface))
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("CastBridge identifie la TV sans lui envoyer de commande, puis essaie plusieurs façons de la piloter. Rien n'est envoyé avant que vous ayez choisi la TV.",
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            OutlinedTextField(host, { host = it }, label = { Text("Adresse de la TV (ex. 192.168.1.20)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(enabled = host.isNotBlank() && ui.busy == null, onClick = { SmartRemote.attach(ctx, host.trim(), null) }) { Text("Identifier cette TV") }
            ui.busy?.let { Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)); Text(it) } }

            ui.fingerprint?.let { fp ->
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(if (fp.vendor == Vendor.UNKNOWN) "Fabricant : non identifié" else "Fabricant : ${fp.vendor.label}", style = MaterialTheme.typography.titleMedium)
                    listOfNotNull(fp.family, fp.model).takeIf { it.isNotEmpty() }?.let { Text(it.joinToString(" · ")) }
                    Text("Confiance : ${(fp.confidence * 100).toInt()} %", style = MaterialTheme.typography.bodySmall)
                    LinearProgressIndicator(progress = { fp.confidence.toFloat() }, Modifier.fillMaxWidth())
                } }
            }

            ui.activeLabel?.let { label ->
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Stratégie active : $label", style = MaterialTheme.typography.titleMedium)
                    if (ui.limits.isNotBlank()) Text("Limites : ${ui.limits}", style = MaterialTheme.typography.bodySmall)
                    ui.warnings.forEach { Text("⚠ $it", style = MaterialTheme.typography.bodySmall, color = Cb.warning) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = ui.busy == null, onClick = { SmartRemote.test() }) { Text("Tester") }
                        if (ui.activeId == castbridge.core.remote.smart.StrategyIds.VENDOR_APP) OutlinedButton(onClick = { SmartRemote.openVendorApp() }) { Text("Ouvrir l'app") }
                        OutlinedButton(enabled = ui.busy == null, onClick = { SmartRemote.retry() }) { Text("Réessayer") }
                    }
                } }
            }
            ui.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

            ui.pairing?.let { id ->
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Appairage demandé (${ui.strategies.firstOrNull { it.id == id }?.label ?: id}) : saisissez le code affiché sur la TV (Sony : la clé PSK réglée sur la TV).", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(code, { code = it }, singleLine = true, label = { Text("Code") }, modifier = Modifier.fillMaxWidth())
                    Button(enabled = code.isNotBlank() && ui.busy == null, onClick = { SmartRemote.pair(id, code.trim()); code = "" }) { Text("Valider") }
                } }
            }

            if (ui.attempts.isNotEmpty()) {
                Text("Stratégies essayées", style = MaterialTheme.typography.titleSmall)
                ui.attempts.forEach { a ->
                    val name = ui.strategies.firstOrNull { it.id == a.strategyId }?.label ?: a.strategyId
                    Text("• $name : ${outcomeText(a.outcome)}${a.reason?.let { " (${castbridge.core.remote.smart.Redact.clean(it)})" } ?: ""}", style = MaterialTheme.typography.bodySmall)
                }
            }

            if (ui.strategies.isNotEmpty()) {
                Text("Choix de la stratégie", style = MaterialTheme.typography.titleSmall)
                Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    RadioButton(ui.forced == null, { SmartRemote.choose(null) }); Text("Automatique (recommandé)")
                }
                ui.strategies.forEach { s ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        RadioButton(ui.forced == s.id, { SmartRemote.choose(s.id) })
                        Column { Text(s.label + if (s.status == StrategyStatus.EXPERIMENTAL) " (expérimentale)" else ""); Text(s.limits, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant) }
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Switch(ui.experimental, { SmartRemote.setExperimental(it) }); Spacer(Modifier.width(8.dp))
                    Text("Essayer aussi les stratégies expérimentales (jamais vérifiées sur du vrai matériel)", style = MaterialTheme.typography.bodySmall)
                }
            }

            OutlinedButton(onClick = {
                (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Diagnostic CastBridge", SmartRemote.diagnostic()))
            }) { Text("Copier le diagnostic (sans secret)") }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (ui.askVolume) AlertDialog(onDismissRequest = {}, title = { Text("Test du volume") },
        text = { Text("CastBridge vient d'envoyer volume + puis volume −. Avez-vous vu le volume changer sur la TV ?") },
        confirmButton = { TextButton({ SmartRemote.confirm(true) }) { Text("Oui") } },
        dismissButton = { TextButton({ SmartRemote.confirm(false) }) { Text("Non") } })
}
