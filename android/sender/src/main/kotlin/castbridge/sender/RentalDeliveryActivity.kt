package castbridge.sender

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
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
import androidx.compose.ui.unit.dp
import castbridge.core.lots.HttpTvTransport
import castbridge.core.lots.RentalDelivery
import castbridge.core.lots.SealedLot
import castbridge.core.lots.TvTransport
import java.io.FileInputStream

/**
 * « Locations sur la TV » : the phone carries rented lots to CastBridge-TV (which stays offline). The user picks the files the owner gave (sealed .lot files, catalog.json, and the
 * contract line "produit@période", in a small text file or typed), the phone sends what the TV does not hold yet and shows what the TV holds. All logic is in castbridge.core.lots.RentalDelivery.
 */
class RentalDeliveryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CastTheme { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { Screen() } } }
    }

    private fun nameOf(u: Uri): String = contentResolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } ?: (u.lastPathSegment ?: "")
    private fun sizeOf(u: Uri): Long = contentResolver.query(u, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { if (it.moveToFirst()) it.getLong(0) else -1L } ?: -1L

    @Composable private fun Screen() {
        var picked by remember { mutableStateOf(emptyList<Uri>()) }
        var contract by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        var msg by remember { mutableStateOf<String?>(null) }
        var progress by remember { mutableStateOf<String?>(null) }
        var held by remember { mutableStateOf(emptyList<String>()) }
        val link by TvLinkManager.state.collectAsState()
        val session = (link as? LinkUi.Connected)?.session

        fun transport(): TvTransport? = session?.base?.let { HttpTvTransport(it, session.credential) }
        fun refresh() {
            val t = transport() ?: run { held = listOf("TV non jointe pour le moment (Wi-Fi nécessaire)."); return }
            Thread { val v = RentalDelivery(t).status().lines(); runOnUiThread { held = v } }.start()
        }
        LaunchedEffect(session?.base) { refresh() }

        val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            picked = uris
            uris.firstOrNull { nameOf(it).let { n -> n.startsWith("contract", true) || n.endsWith(".contract", true) } }?.let { u ->
                runCatching { contentResolver.openInputStream(u)?.bufferedReader()?.use { it.readText() }?.lineSequence()?.map { it.trim() }?.firstOrNull { it.isNotEmpty() } }.getOrNull()?.let { contract = it }
            }
            msg = null
        }

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Locations sur la TV", style = MaterialTheme.typography.headlineSmall)
            Text("Ce que la TV contient", style = MaterialTheme.typography.titleSmall)
            if (held.isEmpty()) Text("…", style = MaterialTheme.typography.bodySmall) else held.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
            OutlinedButton({ refresh() }, enabled = !busy) { Text("Actualiser") }
            HorizontalDivider()
            Text("1. Choisissez les fichiers reçus : les lots (.lot), catalog.json et, si vous l'avez, le fichier « contract ».", style = MaterialTheme.typography.bodyMedium)
            Button({ pick.launch(arrayOf("*/*")) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Choisir les fichiers") }
            if (picked.isNotEmpty()) Text("${picked.count { nameOf(it).endsWith(".lot") }} lot(s)" + if (picked.any { nameOf(it) == "catalog.json" }) ", catalogue signé trouvé" else ", catalog.json manquant", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(contract, { contract = it.trim(); msg = null }, label = { Text("Location (produit@période)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Button({
                val t = transport() ?: run { msg = "La TV n'est pas jointe : allumez-la, ouvrez CastBridge-TV, même Wi-Fi."; return@Button }
                val cat = picked.firstOrNull { nameOf(it) == "catalog.json" }
                val lotUris = picked.filter { nameOf(it).endsWith(".lot") }
                if (cat == null || lotUris.isEmpty() || contract.isBlank()) { msg = "Il faut au moins un lot, catalog.json et la ligne de location."; return@Button }
                busy = true; msg = "Envoi…"
                Thread {
                    val text = runCatching {
                        val catalog = contentResolver.openInputStream(cat)!!.bufferedReader().use { it.readText() }
                        val lots = lotUris.map { u -> SealedLot(nameOf(u), sizeOf(u)) { off, n ->
                            contentResolver.openFileDescriptor(u, "r")!!.use { pfd -> FileInputStream(pfd.fileDescriptor).use { fis -> ByteArray(n).also { b -> fis.channel.position(off); var r = 0; while (r < n) { val k = fis.read(b, r, n - r); if (k < 0) break; r += k } } } }
                        } }
                        RentalDelivery(t).deliver(contract, catalog, lots, progress = { name, sent, total -> runOnUiThread { progress = "$name : ${if (total > 0) sent * 100 / total else 100} %" } }).summary
                    }.getOrElse { "Échec de l'envoi : ${it.message ?: it.javaClass.simpleName}" }
                    runOnUiThread { busy = false; progress = null; msg = text; refresh() }
                }.start()
            }, enabled = !busy && picked.isNotEmpty() && contract.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(if (busy) "En cours…" else "2. Envoyer à la TV") }
            progress?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            msg?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            Text("La TV reste hors ligne : tout vient du téléphone. Un lot déjà présent n'est pas renvoyé, et un envoi interrompu reprend là où il s'est arrêté.", style = MaterialTheme.typography.bodySmall)
        }
    }

    companion object { fun open(ctx: Context) = ctx.startActivity(Intent(ctx, RentalDeliveryActivity::class.java)) }
}
