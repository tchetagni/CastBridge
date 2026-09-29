package castbridge.sender

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import castbridge.core.tv.LibraryLogic
import castbridge.core.tv.PlayerParams
import castbridge.core.tv.TvClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * "Réglages de lecture" of the TV player, from the phone: audio track, subtitles (tracks, files next to the video, add one
 * from the phone), delays by 50 ms, subtitle size, speed, picture format, chapters, decoder, equalizer, repeat, technical
 * info. Everything goes through GET /api/player/tracks and POST /api/player/... (PIN). The TV remembers the choices per file.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TvPlayerSettingsSheet(client: TvClient, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var t by remember { mutableStateOf<JSONObject?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableIntStateOf(0) }

    LaunchedEffect(client, tick) {
        while (true) {
            t = withContext(Dispatchers.IO) { runCatching { JSONObject(client.raw("GET", "/api/player/tracks")) }.getOrNull() } ?: t
            delay(3000)
        }
    }
    fun post(path: String) = scope.launch {
        val r = withContext(Dispatchers.IO) { runCatching { client.raw("POST", path) } }
        r.onSuccess { j -> runCatching { JSONObject(j).takeIf { it.has("playing") }?.let { t = it } }; msg = null }
            .onFailure { e -> msg = if ((e as? TvClient.HttpError)?.code == 409) "Impossible maintenant (rien en lecture, ou pas pour ce fichier)" else e.message }
        if (path.startsWith("/api/player/next") || path.startsWith("/api/player/prev") || path.contains("repeat")) tick++
    }
    val subPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            msg = "Envoi du sous-titre…"
            msg = withContext(Dispatchers.IO) {
                runCatching {
                    val name = ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                        if (c.moveToFirst()) c.getString(c.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else null } ?: "sous-titres.srt"
                    if (!castbridge.core.tv.SubtitleFinder.isSubtitle(name)) return@runCatching "Choisissez un fichier .srt, .ass, .ssa, .vtt ou .sub"
                    val bytes = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() }      // subtitle files are small
                    val off = client.part(name).length.let { if (it >= bytes.size) 0L else it }
                    client.upload(name, off, bytes.size.toLong(), bytes.inputStream().also { it.skip(off) }) {}
                    client.raw("POST", "/api/player/subfile?name=${TvClient.enc(name)}")
                    "Sous-titres « $name » ajoutés"
                }.getOrElse { it.message }
            }
            tick++
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        val j = t
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Réglages de lecture", style = MaterialTheme.typography.titleLarge)
            if (j == null || !j.optBoolean("playing")) { Text("Rien en lecture sur la TV."); return@Column }
            fun arr(k: String) = j.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.orEmpty()
            fun strs(k: String) = j.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()

            val audio = arr("audio")
            if (audio.size > 1) {
                Label("Piste audio")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    audio.forEach { a -> FilterChip(j.optInt("audioId") == a.optInt("id"), { post("/api/player/audio?id=${a.optInt("id")}") }, { Text(a.optString("name")) }) }
                }
            }
            Label("Sous-titres")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val cur = j.optInt("subtitleId", -1)
                arr("subtitles").forEach { s -> FilterChip(cur == s.optInt("id"), { post("/api/player/subtitle?id=${s.optInt("id")}") }, { Text(s.optString("name")) }) }
                strs("subtitleFiles").forEach { f -> AssistChip({ post("/api/player/subtitle?file=${TvClient.enc(f)}") }, { Text("Fichier : $f") }) }
                AssistChip({ subPicker.launch(arrayOf("*/*")) }, { Text("Ajouter depuis le téléphone…") }, leadingIcon = { Icon(Icons.Filled.Add, null) })
            }
            DelayRow("Décalage des sous-titres", j.optLong("subDelayMs")) { d -> post("/api/player/subdelay?delta=$d") }
            DelayRow("Décalage audio", j.optLong("audioDelayMs")) { d -> post("/api/player/audiodelay?delta=$d") }
            Label("Taille des sous-titres")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PlayerParams.SUB_SCALES.forEach { s -> FilterChip(j.optInt("subScale") == s, { post("/api/player/subsize?value=$s") }, { Text("$s %") }) }
            }
            Label("Vitesse")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PlayerParams.RATES.forEach { r -> FilterChip(Math.abs(j.optDouble("rate", 1.0) - r) < 0.01, { post("/api/player/rate?value=$r") }, { Text(PlayerParams.rateLabel(r)) }) }
            }
            Label("Format d'image")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PlayerParams.ASPECTS.forEach { a -> FilterChip(j.optString("aspect") == a, { post("/api/player/aspect?value=${TvClient.enc(a)}") }, { Text(PlayerParams.aspectLabel(a)) }) }
            }
            val chapters = arr("chapters")
            if (chapters.size > 1 || strs("queue").size > 1) {
                Label(if (chapters.size > 1) "Chapitre ${j.optInt("chapter") + 1} / ${chapters.size}" else "Liste de lecture ${j.optInt("queueIndex") + 1} / ${strs("queue").size}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ post("/api/player/chapter?delta=-1") }) { Icon(Icons.Filled.SkipPrevious, null); Text("Précédent") }
                    OutlinedButton({ post("/api/player/chapter?delta=1") }) { Text("Suivant"); Icon(Icons.Filled.SkipNext, null) }
                }
                if (chapters.size > 1) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    chapters.forEachIndexed { i, c -> FilterChip(j.optInt("chapter") == i, { post("/api/player/chapter?index=$i") },
                        { Text("${i + 1}. ${c.optString("name").ifEmpty { LibraryLogic.clock(c.optLong("timeMs")) }}") }) }
                }
            }
            if (strs("queue").size > 1) {
                Label("Répétition")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("off" to "Non", "all" to "Toute la liste", "one" to "Ce fichier").forEach { (k, l) ->
                        FilterChip(j.optString("repeat") == k, { post("/api/player/repeat?value=$k") }, { Text(l) })
                    }
                }
            }
            val presets = strs("eqPresets")
            if (presets.isNotEmpty()) {
                Label("Égaliseur")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(j.optInt("eqPreset", -1) < 0, { post("/api/player/eq?preset=-1") }, { Text("Désactivé") })
                    presets.forEachIndexed { i, p -> FilterChip(j.optInt("eqPreset", -1) == i, { post("/api/player/eq?preset=$i") }, { Text(p) }) }
                }
            }
            Label("Décodage vidéo")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PlayerParams.HW_MODES.forEach { h -> FilterChip(j.optString("hw") == h, { post("/api/player/hw?value=$h") }, { Text(PlayerParams.hwLabel(h)) }) }
            }
            j.optJSONObject("video")?.let { v ->
                Label("Informations")
                Text("${v.optString("codec")} ${v.optInt("width")}x${v.optInt("height")}" +
                    (if (v.optDouble("fps") > 0) String.format(java.util.Locale.ROOT, ", %.2f i/s", v.optDouble("fps")) else "") +
                    (if (v.optLong("bitrate") > 0) ", ${v.optLong("bitrate") / 1000} kbit/s" else "") +
                    "\nDécodage : ${v.optString("decoder")}" + (j.optString("audioCodec").takeIf { it.isNotEmpty() && it != "null" }?.let { "\nAudio : $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall)
            }
            msg?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Text("La TV mémorise ces choix pour ce fichier (piste, sous-titres, décalages, vitesse, format).", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun Label(text: String) = Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)

@Composable
private fun DelayRow(label: String, ms: Long, step: (Long) -> Unit) {
    Label("$label : ${PlayerParams.delayLabel(ms)}")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton({ step(-500) }) { Text("-500") }
        OutlinedButton({ step(-50) }) { Text("-50 ms") }
        OutlinedButton({ step(50) }) { Text("+50 ms") }
        OutlinedButton({ step(500) }) { Text("+500") }
        TextButton({ step(-ms) }) { Text("0") }
    }
}
