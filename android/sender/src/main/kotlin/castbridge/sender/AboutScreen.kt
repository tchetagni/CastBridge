package castbridge.sender

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import castbridge.core.brand.Brand

/**
 * Onglet « À propos » d'une marque blanche (docs/WHITE-LABEL.md § À propos) : logo, nom et version, slogan, support (touchable : appel ou WhatsApp),
 * entrée « Conditions générales d'utilisation » si le client a fourni legal/cgu.md, et en bas « Commercialisé par … » (clé brand.marketedBy).
 * Pour CastBridge l'onglet n'existe pas (l'interface reste identique).
 */
@Composable
fun AboutScreen() {
    val ctx = LocalContext.current
    var cgu by rememberSaveable { mutableStateOf(false) }
    val cguId = remember { ctx.resources.getIdentifier("brand_cgu", "raw", ctx.packageName) }
    if (cgu && cguId != 0) {
        BackHandler { cgu = false }
        CguScreen(runCatching { ctx.resources.openRawResource(cguId).bufferedReader(Charsets.UTF_8).use { it.readText() } }.getOrDefault("")) { cgu = false }
        return
    }
    val version = remember { PhoneConnect.versionName.ifEmpty { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull().orEmpty() } }
    val phone = Brand.supportPhone
    fun open(i: Intent) { runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            CbLogo(R.drawable.logo_castbridge_logo_horizontal, 140.dp, description = Brand.name)
            Spacer(Modifier.height(16.dp))
            Text(Brand.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            if (version.isNotEmpty()) Text("Version $version", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (Brand.tagline.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(Brand.tagline, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface)
            }
            if (phone.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                Text("Support", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(phone, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                val digits = phone.filter { it.isDigit() }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ open(Intent(Intent.ACTION_DIAL, Uri.parse("tel:+$digits"))) }) { Icon(Icons.Filled.Call, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Appeler") }
                    OutlinedButton({ open(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits"))) }) { Text("WhatsApp") }
                }
            }
            if (cguId != 0) {
                Spacer(Modifier.height(24.dp))
                HorizontalDivider()
                Row(Modifier.fillMaxWidth().clickable { cgu = true }.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Description, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text("Conditions générales d'utilisation (CGU)", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                    Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider()
            }
        }
        Brand.marketedByLine()?.let { Text(it, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/** Texte des CGU : rendu markdown minimal (titres #, ##, ###, puces « - », paragraphes, **gras** retiré), défilant. */
@Composable
private fun CguScreen(markdown: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") }
            Text("Conditions générales d'utilisation", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)) {
            val para = StringBuilder()
            @Composable fun flush() { if (para.isNotEmpty()) { Text(para.toString().trim(), Modifier.padding(bottom = 10.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface); para.clear() } }
            for (raw in markdown.lines()) {
                val line = raw.trimEnd().replace("**", "").replace("`", "")
                when {
                    line.isBlank() -> flush()
                    line.startsWith("#") -> { flush(); val lvl = line.takeWhile { it == '#' }.length
                        Text(line.trimStart('#').trim(), Modifier.padding(top = if (lvl == 1) 4.dp else 12.dp, bottom = 6.dp), style = if (lvl == 1) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) }
                    line.startsWith("> ") -> { flush(); Text(line.removePrefix("> "), Modifier.padding(bottom = 10.dp), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary) }
                    line.trimStart().startsWith("- ") -> { flush(); Row(Modifier.padding(start = 8.dp, bottom = 4.dp)) { Text("•  "); Text(line.trimStart().removePrefix("- "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface) } }
                    else -> para.append(line).append(' ')
                }
            }
            flush()
        }
    }
}
