package castbridge.sender

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import castbridge.core.tv.TvClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** One volume as reported by the TV (GET /api/storage). */
data class TvVolume(
    val id: String, val label: String, val kind: String, val fs: String, val present: Boolean, val writable: Boolean,
    val free: Long, val writeBps: Long, val warnings: List<String>, val advice: String?,
    /** Maker, model, serial, USB speed... of the drive as the TV reads them (label to value). */
    val usbDetails: List<Pair<String, String>> = emptyList(),
)

data class TvStorage(val target: String, val volumes: List<TvVolume>, val moveJson: JSONObject?, val warnings: List<String>)

object TvStorageParser {
    fun parse(json: String): TvStorage {
        val o = JSONObject(json)
        val vs = o.optJSONArray("volumes")
        val volumes = (0 until (vs?.length() ?: 0)).map { i ->
            val v = vs!!.getJSONObject(i)
            val w = v.optJSONArray("warnings")
            TvVolume(v.getString("id"), v.optString("label"), v.optString("kind"), v.optString("fs"), v.optBoolean("present", true),
                v.optBoolean("writable", true), v.optLong("free", -1), v.optLong("writeBps", 0),
                (0 until (w?.length() ?: 0)).map { w!!.getString(it) }, if (v.isNull("formatAdvice")) null else v.optString("formatAdvice"),
                v.optJSONObject("usb")?.optJSONArray("details")?.let { d -> (0 until d.length()).map { j -> d.getJSONObject(j).let { it.optString("k") to it.optString("v") } } }.orEmpty())
        }
        val ws = o.optJSONArray("warnings")
        return TvStorage(o.optString("target", "auto"), volumes, o.optJSONObject("move"), (0 until (ws?.length() ?: 0)).map { ws!!.getString(it) })
    }
}

/**
 * Before-send check for callers that want to warn earlier than the upload itself does (the upload already runs this check and
 * stops with the same message): null = fine, else what to tell the user (FAT32 4 GB limit, no room, drive removed).
 * Warnings that do not block are returned by [TvClient.checkStorage] in `warnings`.
 */
suspend fun storagePreflight(client: TvClient, name: String, size: Long, durMs: Long = 0): String? = withContext(Dispatchers.IO) {
    runCatching { client.checkStorage(name, size, durMs) }.getOrNull()?.let { if (it.ok) null else it.message }
}

/** "Stockage de la TV": drives, where new files go, warnings, moving files between volumes. Shown under the admin panel. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StoragePanel(client: TvClient) {
    var st by remember(client.base) { mutableStateOf<TvStorage?>(null) }
    var files by remember(client.base) { mutableStateOf<List<Pair<String, String>>>(emptyList()) }     // name to volume id (finished files)
    var msg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    /** Reads the TV's storage state; throws on error (a 401 stops the polling loop below). */
    fun load() {
        st = TvStorageParser.parse(client.storage())
        val fs = JSONObject(client.info()).optJSONArray("files")
        files = (0 until (fs?.length() ?: 0)).map { fs!!.getJSONObject(it) }
            .filter { it.optBoolean("complete") }.map { it.getString("name") to it.optString("volume") }
    }
    fun refresh() = scope.launch { withContext(Dispatchers.IO) { runCatching { load() } } }
    LaunchedEffect(client) {
        while (true) {
            val err = withContext(Dispatchers.IO) { runCatching { load() }.exceptionOrNull() }
            if (err is TvClient.HttpError && err.code == 401) break     // refused PIN: retrying would trigger the TV's lockout
            delay(5000)
        }
    }
    fun act(f: () -> String?) = scope.launch {
        msg = withContext(Dispatchers.IO) { runCatching { f() }.getOrElse { it.message } }
        refresh()
    }

    val s = st ?: return
    HorizontalDivider()
    Text("Stockage de la TV", style = MaterialTheme.typography.titleSmall)
    Text("Nouveaux fichiers : " + when (s.target) { "auto" -> "automatique (clé USB si utilisable, sinon mémoire interne)"; "internal" -> "mémoire interne"
        else -> s.volumes.firstOrNull { it.id == s.target }?.label ?: s.target }, style = MaterialTheme.typography.bodyMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(s.target == "auto", { act { client.setTarget("auto"); null } }, { Text("Auto") })
        FilterChip(s.target == "internal", { act { client.setTarget("internal"); null } }, { Text("Interne") })
        s.volumes.filter { it.kind != "internal" && it.present }.forEach { v ->
            FilterChip(s.target == v.id, { act { client.setTarget(v.id); null } }, { Text(v.label) })
        }
    }
    s.volumes.forEach { v ->
        val free = if (v.free >= 0) "${formatSize(v.free)} libres" else "espace inconnu"
        val speed = if (v.writeBps > 0) ", écriture ~${v.writeBps / 1_000_000} Mo/s" else ""
        Text("${v.label}${if (v.fs != "?" && v.fs.isNotEmpty()) " (${v.fs})" else ""} : " +
            if (!v.present) "retirée" else if (!v.writable) "lecture seule" else "$free$speed", style = MaterialTheme.typography.bodyMedium)
        if (v.usbDetails.isNotEmpty()) Column(Modifier.padding(start = 8.dp, top = 2.dp, bottom = 2.dp)) {
            v.usbDetails.forEach { (k, d) -> Text("$k : $d", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        v.warnings.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        v.advice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
    s.moveJson?.let { m ->
        val state = m.optString("state")
        Text("Déplacement de ${m.optString("name")} : $state" + if (state == "running") " ${m.optLong("done") * 100 / m.optLong("total").coerceAtLeast(1)} %" else
            if (state == "failed") " (${m.optString("error")})" else "", style = MaterialTheme.typography.bodySmall)
        if (state == "running") TextButton(onClick = { act { client.cancelMove(); null } }) { Text("Annuler le déplacement") }
    }
    if (files.isNotEmpty() && s.volumes.count { it.present } > 1) {
        Text("Déplacer un fichier", style = MaterialTheme.typography.labelLarge)
        files.forEach { (name, vol) ->
            Text(name, style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                s.volumes.filter { it.present && it.id != vol && it.writable }.forEach { to ->
                    OutlinedButton(onClick = { act { client.moveFile(name, to.id); null } }) { Text("vers ${to.label}") }
                }
            }
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { act { client.rescanStorage(true); "Détection relancée" } }) { Text("Re-détecter la clé") }
        OutlinedButton(onClick = { act { TvClient.str(client.pickSafFolder(), "message") } }) { Text("Choisir un dossier sur la TV") }
        OutlinedButton(onClick = { act { TvClient.str(client.openTvStorageSettings(), "message") ?: "Réglages ouverts sur la TV" } }) { Text("Réglages de stockage de la TV") }
    }
    msg?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
}
