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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import castbridge.core.langues.*
import castbridge.core.lots.LotId

/**
 * « Voir les leçons » (Langues): the phone reads the lessons it holds (bundled starter + downloaded lots): units, words with their illustration when available,
 * exercises with the commented correction. Same rules as the TV screen ([LangVisuals], [LangMarking]); without image or explanation nothing more is drawn.
 */
@Composable
fun LanguesViewEntry(modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = modifier) { Text("Voir les leçons de langue") }
    if (open) LanguesViewScreen { open = false }
}

private val starter by lazy { EmbeddedLangSource() }

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
    if (u.vocab.isNotEmpty()) Text("Vocabulaire", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    for (v in u.vocab) {
        Text(v.term + (v.reading?.let { "   $it" } ?: "") + "   —   ${v.gloss}", style = MaterialTheme.typography.bodyLarge)
        val m = LangVisuals.image(p, u, v)
        val bmp = remember(m?.id) { m?.let { e -> starter.media(p.parts.scope, e.file)?.let { b -> runCatching { android.graphics.BitmapFactory.decodeByteArray(b, 0, b.size) }.getOrNull() } } }
        if (m != null && bmp != null) {
            Image(bmp.asImageBitmap(), m.alt ?: v.gloss, Modifier.fillMaxWidth().heightIn(max = 260.dp), contentScale = ContentScale.Fit)
            LangVisuals.notice(m)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
    for (d in u.dialogues) {
        Text("Dialogue : ${d.title}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        for (x in d.lines) {
            Text((if (x.who.isNotBlank()) "${x.who} : " else "") + x.text + (x.reading?.let { "   ($it)" } ?: ""))
            Text(x.translation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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
