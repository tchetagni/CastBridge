package castbridge.sender

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import castbridge.core.langues.*
import castbridge.core.lots.LotId

/**
 * « Voir les leçons » (Langues): the phone reads the lessons it holds (bundled starter + downloaded lots): units, words with their illustration and their sound when available,
 * exercises with the commented correction and their sound. Same rules as the TV screen ([LangVisuals], [LangMarking]); without image or explanation nothing more is drawn.
 */
@Composable
fun LanguesViewEntry(modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = modifier) { Text("Voir les leçons de langue") }
    if (open) LanguesViewScreen { open = false }
}

private val starter by lazy { EmbeddedLangSource() }

/** The sound player of the lesson screen (one sound at a time). The bundled copy is written once to the cache, MediaPlayer wants a file. */
private object Voice {
    private var player: android.media.MediaPlayer? = null
    fun stop() { runCatching { player?.release() }; player = null }
    /** True when the sound could be started. */
    fun play(ctx: android.content.Context, p: LangPack, m: MediaEntry): Boolean {
        stop()
        return runCatching {
            val bytes = starter.media(p.parts.scope, m.file) ?: return false
            val dir = java.io.File(ctx.cacheDir, "langues-audio/${p.parts.scope}").apply { mkdirs() }
            val f = java.io.File(dir, LangVisuals.fileName(m.file) ?: return false).also { if (it.length() != bytes.size.toLong()) it.writeBytes(bytes) }
            player = android.media.MediaPlayer().apply { setDataSource(f.path); prepare(); start(); setOnCompletionListener { stop() } }
            true
        }.onFailure { android.util.Log.w("Langues", "lecture audio impossible : ${m.file}", it); stop() }.getOrDefault(false)
    }
    fun available(p: LangPack, m: MediaEntry) = starter.media(p.parts.scope, m.file) != null
}

/** What was already said on the current page: the synthetic-voice mention and the « audio not available » line appear once per page. */
private class VoiceNotes { var voice = false; var missing = false }

/** « Écouter » button of a sound referenced by [ref] (nothing when there is none); a sound the phone does not hold gives one discreet line instead. */
@Composable
private fun ListenButton(p: LangPack, ref: String?, text: String, notes: VoiceNotes) {
    val m = LangVisuals.audio(p, ref) ?: return
    val ctx = LocalContext.current
    if (!Voice.available(p, m)) {
        if (!notes.missing) { notes.missing = true; Text("Audio non disponible.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        return
    }
    var failed by remember(m.id) { mutableStateOf(false) }
    OutlinedButton({ failed = !Voice.play(ctx, p, m) }, Modifier.fillMaxWidth()) { Text(text) }
    if (failed) Text("Lecture impossible sur ce téléphone.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    if (!notes.voice) LangVisuals.voiceNotice(m)?.let { notes.voice = true; Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

/** The bundled copy of a video, written once to the cache (VideoView wants a file); null when the phone does not hold it. */
private fun videoFile(ctx: android.content.Context, p: LangPack, m: MediaEntry): java.io.File? = runCatching {
    val bytes = starter.media(p.parts.scope, m.file) ?: return@runCatching null
    val dir = java.io.File(ctx.cacheDir, "langues-video/${p.parts.scope}").apply { mkdirs() }
    java.io.File(dir, LangVisuals.fileName(m.file) ?: return@runCatching null).also { if (it.length() != bytes.size.toLong()) it.writeBytes(bytes) }
}.getOrNull()

/** « Voir la vidéo (muette) » : opens a full-screen player (pause / resume, close), with the synthetic mention; no file or a player error = one line « Vidéo non disponible ». */
@Composable
private fun VideoButton(p: LangPack, c: LangVisuals.UnitClip) {
    val ctx = LocalContext.current
    val file = remember(c.media.id) { videoFile(ctx, p, c.media) }
    if (file == null) { Text("Vidéo non disponible.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); return }
    var open by remember(c.media.id) { mutableStateOf(false) }
    var failed by remember(c.media.id) { mutableStateOf(false) }
    OutlinedButton({ failed = false; open = true }, Modifier.fillMaxWidth()) { Text(LangVisuals.videoButton(c)) }
    LangVisuals.videoNotice(c.media)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    if (failed) Text("Vidéo non disponible.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    if (open) VideoDialog(file, c.media, onError = { open = false; failed = true }, onClose = { open = false })
}

@Composable
private fun VideoDialog(file: java.io.File, m: MediaEntry, onError: () -> Unit, onClose: () -> Unit) {
    var view by remember { mutableStateOf<android.widget.VideoView?>(null) }
    var paused by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = androidx.compose.ui.graphics.Color.Black) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                AndroidView(factory = { c ->
                    android.widget.VideoView(c).apply {
                        setOnCompletionListener { onClose() }          // the video stops at its end and the lesson comes back
                        setOnErrorListener { _, w, x -> android.util.Log.w("Langues", "lecture vidéo impossible : ${file.name} ($w, $x)"); onError(); true }
                        setOnClickListener { if (isPlaying) { pause(); paused = true } else { start(); paused = false } }
                        setVideoPath(file.path); start(); view = this
                    }
                }, modifier = Modifier.weight(1f).fillMaxWidth(), onRelease = { runCatching { it.stopPlayback() } })
                LangVisuals.videoNotice(m)?.let { Text(it, color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.bodySmall) }
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton({ view?.let { v -> if (v.isPlaying) { v.pause(); paused = true } else { v.start(); paused = false } } }, Modifier.weight(1f)) { Text(if (paused) "Reprendre" else "Pause") }
                    Button(onClose, Modifier.weight(1f)) { Text("Fermer") }
                }
            }
        }
    }
}

/** Starter packs + packs of the lots stored on the phone (a stored lot of the same name and at least as recent replaces the starter). */
private fun heldPacks(): List<LangPack> {
    val stored = runCatching { LotsRuntime.store.list().filter { it.meta.id.feature == LangLots.FEATURE }.mapNotNull { e ->
        LotsRuntime.store.file(e.meta.id, e.meta.version)?.let { runCatching { LangLotConsumer.readPack(it) }.getOrNull() }
    } }.getOrDefault(emptyList())
    return EmbeddedLangSource.merge(starter.packs, stored)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguesViewScreen(onClose: () -> Unit) {
    val packs = remember { heldPacks() }
    var pack by remember { mutableStateOf<LangPack?>(null) }
    var unit by remember { mutableStateOf<LangUnit?>(null) }
    var running by remember { mutableStateOf(false) }
    val back = { when { running -> running = false; unit != null -> unit = null; pack != null -> pack = null; else -> onClose() } }
    BackHandler { back() }
    DisposableEffect(Unit) { onDispose { Voice.stop() } }
    Dialog(onDismissRequest = { back() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(title = { Text(unit?.title ?: pack?.title ?: "Leçons de langue", maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                    navigationIcon = { IconButton({ back() }) { Icon(Icons.Filled.ArrowBack, "Retour") } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface))
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val p = pack; val u = unit
                    when {
                        p == null -> {
                            if (packs.isEmpty()) Text(LangCatalog.EMPTY_MESSAGE)
                            packs.forEach { OutlinedButton({ pack = it }, Modifier.fillMaxWidth()) { Text("${it.title}  ·  ${it.units.size} unité(s)") } }
                        }
                        u == null -> p.units.forEach { OutlinedButton({ unit = it }, Modifier.fillMaxWidth()) { Text("${it.title}  ·  ${it.minutes} min") } }
                        running -> Exercises(p, u) { running = false }
                        else -> UnitPage(p, u) { running = true }
                    }
                }
            }
        }
    }
}

@Composable
private fun UnitPage(p: LangPack, u: LangUnit, onExercises: () -> Unit) {
    DisposableEffect(u.id) { onDispose { Voice.stop() } }
    val notes = remember(u.id) { VoiceNotes() }.also { it.voice = false; it.missing = false }
    if (u.vocab.isNotEmpty()) Text("Vocabulaire", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    for (v in u.vocab) {
        Text(v.term + (v.reading?.let { "   $it" } ?: "") + "   —   ${v.gloss}", style = MaterialTheme.typography.bodyLarge)
        ListenButton(p, v.audio, "Écouter : ${v.term}", notes)
        val m = LangVisuals.image(p, u, v)
        val bmp = remember(m?.id) { m?.let { e -> starter.media(p.parts.scope, e.file)?.let { b -> runCatching { android.graphics.BitmapFactory.decodeByteArray(b, 0, b.size) }.getOrNull() } } }
        if (m != null && bmp != null) {
            Image(bmp.asImageBitmap(), m.alt ?: v.gloss, Modifier.fillMaxWidth().heightIn(max = 260.dp), contentScale = ContentScale.Fit)
            LangVisuals.notice(m)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        LangVisuals.videosAfter(p, u, v.id).forEach { VideoButton(p, it) }
    }
    LangVisuals.videosAtEnd(p, u).forEach { VideoButton(p, it) }
    for (d in u.dialogues) {
        Text("Dialogue : ${d.title}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        for (x in d.lines) {
            Text((if (x.who.isNotBlank()) "${x.who} : " else "") + x.text + (x.reading?.let { "   ($it)" } ?: ""))
            Text(x.translation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ListenButton(p, x.audio, "Écouter cette phrase", notes)
        }
        ListenButton(p, d.audio, "Écouter le dialogue", notes)
    }
    for (g in u.grammar) {
        Text("Grammaire : ${g.title}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text(g.md.replace("**", "").replace("*", "").replace("`", ""))
        g.examples.forEach { (t, tr) -> Text("$t   —   $tr", style = MaterialTheme.typography.bodySmall) }
    }
    if (u.exercises.isNotEmpty()) Button(onExercises, Modifier.fillMaxWidth()) { Text("Faire les exercices (${u.exercises.size})") }
}

@Composable
private fun Exercises(p: LangPack, u: LangUnit, onDone: () -> Unit) {
    DisposableEffect(u.id) { onDispose { Voice.stop() } }
    var i by remember { mutableIntStateOf(0) }
    var right by remember { mutableIntStateOf(0) }
    var graded by remember { mutableIntStateOf(0) }
    var verdict by remember { mutableStateOf<Boolean?>(null) }
    if (i >= u.exercises.size) {
        Text(if (graded == 0) "Terminé." else "$right bonne(s) réponse(s) sur $graded", style = MaterialTheme.typography.headlineSmall)
        Button({ i = 0; right = 0; graded = 0; verdict = null }, Modifier.fillMaxWidth()) { Text("Refaire") }
        OutlinedButton(onDone, Modifier.fillMaxWidth()) { Text("Retour à l'unité") }
        return
    }
    val x = u.exercises[i]
    fun answer(ok: Boolean) { graded++; if (ok) right++; verdict = ok }
    fun next() { verdict = null; i++ }
    Text("Exercice ${i + 1} / ${u.exercises.size}  ·  ${LangCatalog.kindLabel(x.kind)}", style = MaterialTheme.typography.labelLarge)
    Text(x.prompt, style = MaterialTheme.typography.titleMedium)
    ListenButton(p, x.audio, "Écouter", remember(i) { VoiceNotes() })
    val v = verdict
    if (v != null) {
        Text(if (v) "Bonne réponse" else "Pas tout à fait", style = MaterialTheme.typography.titleLarge, color = if (v) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
        if (!v) (when (x.kind) { LangExerciseKind.MCQ, LangExerciseKind.TRUEFALSE -> x.choices.getOrNull(x.correct ?: -1); else -> x.answers.firstOrNull() })?.let { Text("Réponse attendue : $it") }
        LangVisuals.explanation(x)?.let { Text("Explication : $it", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Button(::next, Modifier.fillMaxWidth()) { Text("Suivant") }
        return
    }
    when (x.kind) {
        LangExerciseKind.MCQ, LangExerciseKind.TRUEFALSE -> {
            val choices = if (x.choices.size >= 2) x.choices else listOf("Faux", "Vrai")
            choices.forEachIndexed { k, c -> OutlinedButton({ answer(k == x.correct) }, Modifier.fillMaxWidth()) { Text(c) } }
        }
        LangExerciseKind.SPEAK, LangExerciseKind.WRITE, LangExerciseKind.MATCH -> {
            var shown by remember(i) { mutableStateOf(false) }
            if (!shown) Button({ shown = true }, Modifier.fillMaxWidth()) { Text(if (x.kind == LangExerciseKind.MATCH) "Voir les paires" else "Voir la réponse modèle") }
            else {
                if (x.kind == LangExerciseKind.MATCH) x.pairs.forEach { (l, r) -> Text("$l   =   $r") } else Text("Modèle : ${x.model}")
                LangVisuals.explanation(x)?.let { Text("Explication : $it", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Text("Votre réponse était-elle juste ?")
                Button({ graded++; right++; next() }, Modifier.fillMaxWidth()) { Text("Oui") }
                OutlinedButton({ graded++; next() }, Modifier.fillMaxWidth()) { Text("Pas encore") }
            }
        }
        else -> {
            var text by remember(i) { mutableStateOf("") }
            if (x.kind == LangExerciseKind.ORDER) Text("Pièces : " + x.words.joinToString("  /  "))
            OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), label = { Text("Votre réponse") }, singleLine = true)
            Button({ answer(LangMarking.matches(text, x)) }, Modifier.fillMaxWidth()) { Text("Vérifier") }
        }
    }
}
