package castbridge.sender

import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import castbridge.core.learn.*
import castbridge.core.quiz.Json
import castbridge.core.tv.TvClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import castbridge.core.lots.LotId
import castbridge.core.lots.LotMeta
import java.io.File
import java.net.URLEncoder

/**
 * « Apprendre » tab of the phone app (docs/LEARN.md):
 * - Leçons: the packs embedded in the app, read on the phone (individual reading, exercises corrected at once);
 * - Piloter la TV: remote control / teacher mode of « Apprendre » on the TV (PIN routes /api/learn/…);
 * - Parents: dashboard of the students of the TV (time, lessons completed, success rate per subject, mock exams).
 */
@Composable
fun LearnScreen() {
    var sub by rememberSaveable { mutableStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().padding(end = 8.dp), contentAlignment = Alignment.CenterEnd) { LotsEntry() }   // « Données hors ligne » (docs/LOTS.md)
        TabRow(selectedTabIndex = sub) {
            Tab(sub == 0, onClick = { sub = 0 }, text = { Text("Leçons", maxLines = 1) })
            Tab(sub == 1, onClick = { sub = 1 }, text = { Text("Piloter la TV", maxLines = 1) })
            Tab(sub == 2, onClick = { sub = 2 }, text = { Text("Parents", maxLines = 1) })
        }
        Box(Modifier.fillMaxSize()) { when (sub) { 0 -> LessonsTab(); 1 -> RemoteTab(); else -> DashboardTab() } }
    }
}

/**
 * What the lots framework (Données screen, synchronisation with the server, delivery to the TV) plugs into « Apprendre ».
 * The defaults keep the tab fully usable offline with the lots already stored; nothing here needs Internet to be read.
 */
object LearnLotsHooks {
    /** Lots offered by the server at the last synchronisation (kept on the phone, so it can be shown offline). */
    @Volatile var catalog: () -> List<LotMeta> = { emptyList() }
    /** Classes the TV reported holding at the last meeting. */
    @Volatile var onTv: () -> Set<String> = { emptySet() }
    /** Starts the download (or the update) of a class; null until the framework wires it: the screen then points to « Données ». */
    @Volatile var download: ((LotId) -> Unit)? = null
}

private object PhoneLibrary {
    @Volatile private var lots: LearnLotConsumer? = null
    /** The installed lots (filesDir/lots/learn): the lots framework installs the downloaded Apprendre lots with this consumer. */
    @Synchronized fun lots(ctx: android.content.Context): LearnLotConsumer = lots ?: LearnLotConsumer(File(ctx.filesDir, "lots/learn")).also { lots = it }
    /** Installed lots first, then the starter packs of the app: every class already on the phone reads without any network. */
    fun lib(ctx: android.content.Context) = LearnLibrary(listOf(LearnLotSource(lots(ctx)), EmbeddedLessonSource()))
}

// ====================================================================== lessons on the phone

@Composable
private fun LessonsTab() {
    var packId by rememberSaveable { mutableStateOf<String?>(null) }
    var lessonId by rememberSaveable { mutableStateOf<String?>(null) }
    var series by rememberSaveable { mutableStateOf<String?>(null) }   // "graded" | "self" | "exam"
    var scope by rememberSaveable { mutableStateOf<String?>(null) }    // « Mes classes »: the class whose packs are listed
    val ctx = LocalContext.current
    val lib = remember { PhoneLibrary.lib(ctx) }
    val packs = remember { runCatching { lib.packs() }.getOrDefault(emptyList()) }
    val pack = packId?.let { id -> remember(id) { lib.pack(id) } }
    val lesson = lessonId?.let { pack?.lesson(it) }

    if (pack != null && series != null) {
        BackHandler { series = null }
        val items = when (series) {
            "graded" -> lesson?.let { l -> l.exercises.mapNotNull { pack.exercise(it) } }
            "self" -> lesson?.let { l -> l.selfCheck.mapNotNull { pack.exercise(it) } }
            else -> pack.exercises.filter { it.tier == ExerciseTier.EXAM && !it.review }
        }.orEmpty()
        PhoneSeries(pack, items) { series = null }; return
    }
    if (pack != null && lesson != null) {
        BackHandler { lessonId = null }
        PhoneReader(pack, lesson, onExercises = { series = it }); return
    }
    if (pack != null) {
        BackHandler { packId = null }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(pack.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(if (pack.status == ReviewStatus.VALIDATED) "Contenu certifié" else "Brouillon : à relire par un enseignant", color = MaterialTheme.colorScheme.tertiary)
            if (pack.exercises.any { it.tier == ExerciseTier.EXAM }) OutlinedButton(onClick = { series = "exam" }, Modifier.fillMaxWidth()) { Text("Exercices type examen corrigés") }
            for (c in pack.chapters) {
                val ls = pack.lessonsOf(c.id); if (ls.isEmpty()) continue
                Text(c.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                ls.forEach { l -> Card(Modifier.fillMaxWidth().clickable { lessonId = l.id }) { Column(Modifier.padding(12.dp)) {
                    Text(l.title, fontWeight = FontWeight.SemiBold); Text("${l.minutes} min · ${l.exercises.size} exercices · ${l.selfCheck.size} questions d'auto-évaluation", style = MaterialTheme.typography.bodySmall)
                } } }
            }
        }
        return
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Apprendre", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Fiches « l'essentiel », exemples résolus pas à pas et exercices corrigés. Les classes téléchargées se lisent sans Internet ; " +
            "elles se mettent à jour quand le téléphone est en ligne, puis passent sur la TV quand les deux se retrouvent.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        MyClasses(PhoneLibrary.lots(ctx), scope) { scope = it }
        for (r in packs.filter { scope == null || it.scope == scope }.sortedBy { LearnCatalog.level(it.manifest.level)?.order ?: 0 }) {
            val m = r.manifest
            Card(Modifier.fillMaxWidth().clickable { packId = m.id }) { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp, 40.dp).background(Color(LearnCatalog.subject(m.subject)?.color ?: 0xFF1E88E5.toInt()), RoundedCornerShape(4.dp)))
                Spacer(Modifier.width(12.dp))
                Column { Text(m.title, fontWeight = FontWeight.SemiBold); Text("${LearnCatalog.level(m.level)?.label ?: m.level} · ${m.lessons} fiches · ${m.exercises} exercices", style = MaterialTheme.typography.bodySmall) }
            } }
        }
    }
}

/** « Mes classes »: one card per class (lot) with its status, and the call to action when a class is not on the phone yet. */
@Composable
private fun MyClasses(lots: LearnLotConsumer, scope: String?, onScope: (String?) -> Unit) {
    val installed = remember { lots.installedAll() }
    val starter = remember { LearnLotCatalog(lots, EmbeddedLessonSource()).classes().filter { it.fromStarter }.associateBy { it.scope } }
    val statuses = remember { LearnClassStatus.of(installed, LearnLotsHooks.catalog(), LearnLotsHooks.onTv()) }
    val all = statuses + starter.values.filter { c -> statuses.none { it.scope == c.scope } }.map { LearnClassStatus(it.scope, it.title, null, null, null, null, null, false) }
    Text("Mes classes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    if (scope != null) OutlinedButton(onClick = { onScope(null) }) { Text("Toutes les classes") }
    for (c in all.sortedBy { LearnScopes.ladders.values.flatMap { l -> l.flatten() }.indexOf(it.scope).let { k -> if (k < 0) 99 else k } }) {
        val action = c.action()
        Card(Modifier.fillMaxWidth().clickable { onScope(c.scope) }) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (c.onTv) Text("sur la TV", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            Text(
                when {
                    c.downloaded -> "v${c.installedVersion} · ${LearnFormat.size(c.installedBytes ?: 0)} · ${LearnFormat.dataDate(c.dataDate)}" + if (c.updateAvailable) " · mise à jour disponible" else ""
                    starter.containsKey(c.scope) -> "Contenu de base inclus dans l'app — leçons complètes à télécharger"
                    else -> "Pas encore sur ce téléphone"
                }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (action != null) {
                val dl = LearnLotsHooks.download
                if (dl != null) Button(onClick = { dl(LotId("learn", c.scope)) }) { Text(action) }
                else Text("$action (écran Données)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
            }
        } }
    }
}

@Composable
private fun md(s: String): AnnotatedString = remember(s) {
    val paras = runCatching { Markdown.parse(s) }.getOrNull() ?: return@remember AnnotatedString(s)
    buildAnnotatedString {
        paras.forEachIndexed { i, p ->
            if (i > 0) append("\n")
            when (p.kind) { Markdown.Kind.BULLET -> append("•  "); Markdown.Kind.NUMBER -> append("${p.number}. "); else -> {} }
            for (sp in p.spans) withStyle(SpanStyle(fontWeight = if (sp.bold) FontWeight.Bold else null, fontStyle = if (sp.italic || sp.tex != null) FontStyle.Italic else null)) { append(sp.tex?.plain() ?: sp.text) }
        }
    }
}

@Composable
private fun PhoneReader(pack: Pack, lesson: Lesson, onExercises: (String) -> Unit) {
    val deck = remember(lesson.id) { LessonDeck(pack, lesson) }
    var page by rememberSaveable(lesson.id) { mutableStateOf(0) }
    var revealed by rememberSaveable(lesson.id, page) { mutableStateOf(0) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(lesson.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        LinearProgressIndicator(progress = { (page + 1f) / deck.pages.size }, Modifier.fillMaxWidth().padding(vertical = 8.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (val p = deck.pages[page]) {
                LessonDeck.Page.Intro -> {
                    Text("${lesson.minutes} min", color = MaterialTheme.colorScheme.primary)
                    castbridge.core.content.PlayPolicy.mark(lesson, PhoneConnect.channel())?.let { Text("⚑ $it", color = MaterialTheme.colorScheme.tertiary) }
                    lesson.objectives.forEach { Text(md("- $it")) }
                    ReportErrorButton { r, n -> PhoneReports.lesson(pack, lesson, r, n) }
                }
                is LessonDeck.Page.Content -> PhoneBlock(p.block, revealed)
                is LessonDeck.Page.Exercise -> PhoneExercise(p.exercise, pack.lang, pack = pack) {}
                LessonDeck.Page.End -> {
                    Text("Fiche terminée !", style = MaterialTheme.typography.headlineSmall)
                    if (lesson.exercises.isNotEmpty()) Button(onClick = { onExercises("graded") }, Modifier.fillMaxWidth()) { Text("Exercices de la fiche (${lesson.exercises.size})") }
                    if (lesson.selfCheck.isNotEmpty()) OutlinedButton(onClick = { onExercises("self") }, Modifier.fillMaxWidth()) { Text("Auto-évaluation (${lesson.selfCheck.size})") }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { if (page > 0) { page--; revealed = 0 } }, Modifier.weight(1f), enabled = page > 0) { Text("◀") }
            val canReveal = revealed < deck.reveals(page)
            Button(onClick = { if (canReveal) revealed++ else if (page < deck.pages.size - 1) { page++; revealed = 0 } }, Modifier.weight(2f)) {
                Text(if (canReveal) "Étape suivante" else "Suivant  ▶")
            }
        }
    }
}

@Composable
private fun PhoneBlock(b: Block, revealed: Int) {
    when (b) {
        is Block.Heading -> Text(b.text, style = MaterialTheme.typography.headlineSmall)
        is Block.Text -> Text(md(b.md))
        is Block.Key -> Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) { Column(Modifier.padding(12.dp)) {
            b.title?.let { Text(it, fontWeight = FontWeight.Bold) }
            Text(md(b.md)); b.tex?.let { PhoneFormula(it) }
        } }
        is Block.Formula -> { PhoneFormula(b.tex); b.caption?.let { Text(md(it)) } }
        is Block.Example -> {
            Text(b.title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
            Text(md(b.statement))
            b.figure?.let { PhoneFigure(it) }
            b.steps.take(revealed).forEachIndexed { i, s -> Card { Column(Modifier.padding(10.dp)) { Text(md("**Étape ${i + 1}.** " + s.md)); s.tex?.let { PhoneFormula(it) } } } }
            if (revealed > b.steps.size) b.answer?.let { Text(md("➜ $it"), color = MaterialTheme.colorScheme.primary) }
        }
        is Block.Illustration -> { b.animation?.let { PhoneAnimation(b, it) } ?: PhoneFigure(b.figure); b.caption?.let { Text(md(it), style = MaterialTheme.typography.bodySmall) } }
        is Block.Audio -> Text("🔊 " + b.text)
        is Block.More -> { Text("Pour aller plus loin", fontWeight = FontWeight.Bold); b.items.forEach { Text(md("- $it")) } }
        is Block.Video -> Text("▶ ${b.title} (vidéo lue sur la TV)")
        is Block.ExerciseRef -> {}
    }
}

/** Same drawing as the TV (core Scene primitives) on the phone's native canvas. */
@Composable
private fun PhoneFigure(f: Figure) {
    val scene = remember(f) { Scene.build(f) }
    Canvas(Modifier.fillMaxWidth().aspectRatio((scene.w / scene.h).toFloat().coerceIn(0.4f, 4f)).background(Color(0xFFFDFCF7), RoundedCornerShape(10.dp))) {
        val s = minOf(size.width / scene.w.toFloat(), size.height / scene.h.toFloat())
        drawIntoCanvas { c -> val nc = c.nativeCanvas; nc.save(); nc.translate((size.width - scene.w.toFloat() * s) / 2, (size.height - scene.h.toFloat() * s) / 2); SceneDraw.draw(nc, scene, s); nc.restore() }
    }
}

@Composable
private fun PhoneFormula(tex: String) {
    val parsed = remember(tex) { runCatching { Tex.parse(tex) }.getOrNull() }
    val color = MaterialTheme.colorScheme.onSurface.let { android.graphics.Color.argb((it.alpha * 255).toInt(), (it.red * 255).toInt(), (it.green * 255).toInt(), (it.blue * 255).toInt()) }
    if (parsed == null) { Text(tex); return }
    Canvas(Modifier.fillMaxWidth().height(64.dp)) {
        drawIntoCanvas { c -> SceneDraw.formula(c.nativeCanvas, parsed, size.width, size.height, 30.dp.toPx(), color) }
    }
}

internal object SceneDraw {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG)
    /** One reusable Path (UI thread only): no allocation per drawn path, frames of animations are drawn 30 times a second. */
    private val scratch = Path()

    fun draw(c: android.graphics.Canvas, scene: Scene, s: Float) {
        fun f(v: Double) = (v * s).toFloat()
        for (op in scene.ops) when (op) {
            is Op.Line -> { stroke.color = op.color; stroke.strokeWidth = f(op.width).coerceAtLeast(1f); stroke.pathEffect = if (op.dash) DashPathEffect(floatArrayOf(f(6.0), f(5.0)), 0f) else null
                c.drawLine(f(op.x1), f(op.y1), f(op.x2), f(op.y2), stroke); stroke.pathEffect = null }
            is Op.Circle -> { op.fill?.let { fill.color = it; c.drawCircle(f(op.cx), f(op.cy), f(op.r), fill) }; op.stroke?.let { stroke.color = it; stroke.strokeWidth = f(op.width).coerceAtLeast(1f); c.drawCircle(f(op.cx), f(op.cy), f(op.r), stroke) } }
            is Op.Rect -> { val r = RectF(f(op.x), f(op.y), f(op.x + op.w), f(op.y + op.h)); op.fill?.let { fill.color = it; c.drawRoundRect(r, f(op.radius), f(op.radius), fill) }
                op.stroke?.let { stroke.color = it; stroke.strokeWidth = f(op.width).coerceAtLeast(1f); c.drawRoundRect(r, f(op.radius), f(op.radius), stroke) } }
            is Op.Path -> { val p = scratch.also { it.rewind() }
                for (cmd in op.cmds) when (cmd) { is PathCmd.M -> p.moveTo(f(cmd.x), f(cmd.y)); is PathCmd.L -> p.lineTo(f(cmd.x), f(cmd.y))
                    is PathCmd.C -> p.cubicTo(f(cmd.x1), f(cmd.y1), f(cmd.x2), f(cmd.y2), f(cmd.x), f(cmd.y)); is PathCmd.Q -> p.quadTo(f(cmd.x1), f(cmd.y1), f(cmd.x), f(cmd.y)); PathCmd.Z -> p.close() }
                op.fill?.let { fill.color = it; c.drawPath(p, fill) }; op.stroke?.let { stroke.color = it; stroke.strokeWidth = f(op.width).coerceAtLeast(1f); c.drawPath(p, stroke) } }
            is Op.Text -> { txt.color = op.color; txt.textSize = f(op.size); txt.typeface = if (op.bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                txt.textAlign = when (op.anchor) { "start" -> Paint.Align.LEFT; "end" -> Paint.Align.RIGHT; else -> Paint.Align.CENTER }
                txt.style = Paint.Style.STROKE; txt.strokeWidth = f(op.size) * 0.22f; txt.color = (0xFDFCF7 or (op.color and 0xFF000000.toInt())); c.drawText(op.text, f(op.x), f(op.y), txt)
                txt.style = Paint.Style.FILL; txt.color = op.color; c.drawText(op.text, f(op.x), f(op.y), txt) }
            is Op.Clip -> { c.save(); c.clipRect(f(op.x), f(op.y), f(op.x + op.w), f(op.y + op.h)) }
            Op.Unclip -> c.restore()
        }
    }

    fun formula(c: android.graphics.Canvas, t: Tex, w: Float, h: Float, size0: Float, color: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        val m = object : TexLayout.Metrics {
            override fun width(text: String, size: Double, italic: Boolean): Double { p.textSize = size.toFloat(); p.typeface = if (italic) Typeface.create(Typeface.SERIF, Typeface.ITALIC) else Typeface.SERIF; return p.measureText(text).toDouble() }
        }
        var size = size0; var b = TexLayout.layout(t, size.toDouble(), m)
        while ((b.width > w * 0.96 || b.height > h) && size > size0 * 0.35f) { size *= 0.9f; b = TexLayout.layout(t, size.toDouble(), m) }
        val x0 = ((w - b.width) / 2).toFloat(); val y0 = ((h - b.height) / 2 + b.ascent).toFloat()
        for (it in b.items) when (it) {
            is TexLayout.Item.Run -> { p.style = Paint.Style.FILL; p.textSize = it.size.toFloat(); p.typeface = if (it.italic) Typeface.create(Typeface.SERIF, Typeface.ITALIC) else Typeface.SERIF; c.drawText(it.text, x0 + it.x.toFloat(), y0 + it.y.toFloat(), p) }
            is TexLayout.Item.Rule -> { p.style = Paint.Style.STROKE; p.strokeWidth = it.thickness.toFloat(); c.drawLine(x0 + it.x1.toFloat(), y0 + it.y1.toFloat(), x0 + it.x2.toFloat(), y0 + it.y2.toFloat(), p) }
        }
    }
}

@Composable
private fun PhoneSeries(pack: Pack, items: List<Exercise>, done: () -> Unit) {
    var i by rememberSaveable { mutableStateOf(0) }
    var right by rememberSaveable { mutableStateOf(0) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (i >= items.size) {
            Text("Série terminée : $right / ${items.size}", style = MaterialTheme.typography.headlineSmall)
            Button(onClick = done) { Text("Retour") }
            return@Column
        }
        Text("${i + 1} / ${items.size}", color = MaterialTheme.colorScheme.primary)
        key(items[i].id) { PhoneExercise(items[i], pack.lang, onNext = { i++ }, pack = pack) { if (it.correct) right++ } }
    }
}

/** One exercise on the phone, corrected at once (problems: parts one after the other). */
@Composable
private fun PhoneExercise(x: Exercise, lang: String, onNext: (() -> Unit)? = null, pack: Pack? = null, onMark: (Mark) -> Unit) {
    var part by remember(x.id) { mutableStateOf(0) }
    var mark by remember(x.id, part) { mutableStateOf<Mark?>(null) }
    var showModel by remember(x.id, part) { mutableStateOf(false) }
    var num by remember(x.id, part) { mutableStateOf("") }
    val q = if (x.kind == ExerciseKind.PROBLEM) x.parts[part] else x
    if (x.kind == ExerciseKind.PROBLEM) { Text(md(x.prompt)); Text("Question ${part + 1} / ${x.parts.size}", color = MaterialTheme.colorScheme.primary) }
    castbridge.core.content.PlayPolicy.mark(x, PhoneConnect.channel())?.let { Text("⚑ $it", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodySmall) }
    Text(md(q.prompt), fontWeight = FontWeight.SemiBold)
    (q.figure ?: x.figure)?.let { PhoneFigure(it) }
    q.tex?.let { PhoneFormula(it) }
    fun submit(a: Answer) { val m = Marking.mark(q, a); mark = m; onMark(m) }
    val m = mark
    if (m == null) when (q.kind) {
        ExerciseKind.MCQ -> Shuffle.order(q.choices.size, Shuffle.seed(q.id)).forEach { k -> OutlinedButton(onClick = { submit(Answer.Choice(k)) }, Modifier.fillMaxWidth()) { Text(md(q.choices[k])) } }
        ExerciseKind.TRUE_FALSE -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { submit(Answer.Bool(true)) }, Modifier.weight(1f)) { Text(if (lang == "en") "True" else "Vrai") }
            Button(onClick = { submit(Answer.Bool(false)) }, Modifier.weight(1f)) { Text(if (lang == "en") "False" else "Faux") }
        }
        ExerciseKind.NUMERIC -> Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(num, { num = it }, Modifier.weight(1f), singleLine = true, label = { Text(q.unit ?: "réponse") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text))
            Spacer(Modifier.width(8.dp)); Button(onClick = { if (Marking.parseNumber(num) != null) submit(Answer.Number(num)) }) { Text("OK") }
        }
        ExerciseKind.MATCHING -> { q.pairs.forEach { (l, r) -> Text("$l  →  ?") }; Button(onClick = { showModel = true; submit(Answer.Pairs(emptyMap())) }) { Text("Voir la correction") } }
        ExerciseKind.OPEN -> if (!showModel) Button(onClick = { showModel = true }) { Text("Voir le corrigé") } else {
            Text(md(q.model ?: "")); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Pas réussi" to 0.0, "À moitié" to 0.5, "Réussi" to 1.0).forEach { (l, f) -> OutlinedButton(onClick = { submit(Answer.Self(f)) }) { Text(l) } }
            }
        }
        ExerciseKind.PROBLEM -> {}
    } else {
        Text(if (m.correct) "✓ Bonne réponse" else "✗ Réponse : " + Markdown.plain(Marking.rightAnswer(q, lang)), color = if (m.correct) Cb.success else Cb.error, fontWeight = FontWeight.Bold)
        if (q.explanation.isNotBlank()) Text(md(q.explanation))
        (if (x.kind == ExerciseKind.PROBLEM) x else q).method?.let { Text(md("**Méthode :** $it"), style = MaterialTheme.typography.bodySmall) }
        val last = x.kind != ExerciseKind.PROBLEM || part >= x.parts.size - 1
        if (!last) Button(onClick = { part++ }) { Text("Question suivante") } else onNext?.let { Button(onClick = it) { Text("Continuer") } }
        if (pack != null) ReportErrorButton { r, n -> PhoneReports.exercise(pack, x, r, n) }
    }
}

// ====================================================================== remote control of the TV

@Composable
private fun TvPicker(onTv: @Composable (TvClient) -> Unit) {
    val ctx = LocalContext.current
    val discovery = remember { TvDiscovery(ctx) }
    DisposableEffect(Unit) { discovery.start(); onDispose { discovery.stop() } }
    val tvs by discovery.tvs.collectAsState()
    val pins = remember { PinStore(ctx) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(tvs) { if (selected == null || tvs.none { it.name == selected }) selected = tvs.firstOrNull()?.name }
    val tv = tvs.firstOrNull { it.name == selected }
    if (tvs.isEmpty()) Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); Spacer(Modifier.width(12.dp)); Text("Recherche de la TV sur le Wi-Fi…") }
    else if (tvs.size > 1) tvs.forEach { t -> DeviceRow(t.name, t.host, t.name == selected) { selected = t.name } }
    val pin = pins.get(selected)
    if (tv != null && pin.isEmpty()) Text("Entrez d'abord le code (PIN) de la TV dans l'onglet « CastBridge TV ».", color = MaterialTheme.colorScheme.error)
    if (tv != null && pin.isNotEmpty()) onTv(remember(tv.base, pin) { TvClient(tv.base, pin) })
}

private suspend fun call(c: TvClient, method: String, path: String): Result<Map<String, Any?>> = withContext(Dispatchers.IO) {
    runCatching { Json.obj(c.raw(method, path)) }.recoverCatching { e ->
        // HttpError's message is « HTTP 409: {"error":"…"} »: show the TV's own words
        val body = (e as? TvClient.HttpError)?.message?.substringAfter(": ", "")
        val why: String? = body?.let { b -> runCatching { Json.obj(b)["error"] as? String }.getOrNull() }
        throw RuntimeException(why ?: e.message ?: "TV injoignable")
    }
}

private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

@Suppress("UNCHECKED_CAST")
@Composable
private fun RemoteTab() {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Piloter « Apprendre » sur la TV", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Mode classe : la leçon s'affiche en grand sur la TV, vous tournez les pages et faites répondre la classe d'ici.", style = MaterialTheme.typography.bodySmall)
        TvPicker { c ->
            var state by remember { mutableStateOf<Map<String, Any?>?>(null) }
            var msg by remember { mutableStateOf("") }
            var num by remember { mutableStateOf("") }
            LaunchedEffect(c) { while (true) { call(c, "GET", "/api/learn").onSuccess { state = it }; delay(1500) } }
            fun cmd(q: String) = scope.launch { call(c, "POST", "/api/learn/cmd?$q").onSuccess { state = it; msg = "" }.onFailure { msg = it.message ?: "" } }
            val open = state?.get("open") == true
            if (!open) Button(onClick = { scope.launch { call(c, "POST", "/api/learn/open").onSuccess { state = it }.onFailure { msg = it.message ?: "" } } }, Modifier.fillMaxWidth()) { Text("Ouvrir « Apprendre » sur la TV") }
            else {
                val screen = state?.get("screen") as? Map<String, Any?>
                val st = screen?.get("state") as? Map<String, Any?>
                val teacher = screen?.get("teacher") == true
                Text("Sur la TV : " + (st?.get("title") ?: screen?.get("screen") ?: "") + (st?.get("page")?.let { p -> "  ·  page ${(p as Number).toInt() + 1} / ${st["pages"]}" } ?: ""), fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) { Switch(teacher, { cmd("action=teacher&value=" + if (it) "on" else "off") }); Spacer(Modifier.width(8.dp)); Text("Mode classe (texte agrandi)") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { cmd("action=prev") }, Modifier.weight(1f)) { Text("◀") }
                    Button(onClick = { cmd("action=ok") }, Modifier.weight(1.4f)) { Text("OK / révéler") }
                    OutlinedButton(onClick = { cmd("action=next") }, Modifier.weight(1f)) { Text("▶") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { cmd("action=back") }, Modifier.weight(1f)) { Text("Retour") }
                    OutlinedButton(onClick = { cmd("action=speak") }, Modifier.weight(1f)) { Text("Lire à voix haute") }
                }
                val ex = st?.get("exercise") as? Map<String, Any?>
                if (ex != null) Card { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(ex["prompt"] as? String ?: "", fontWeight = FontWeight.SemiBold)
                    if (ex["answered"] == true) Text(if (ex["correct"] == true) "✓ Bonne réponse" else "✗ " + (ex["answer"] ?: ""))
                    else when (ex["kind"]) {
                        "mcq" -> (ex["choices"] as? List<Any?>).orEmpty().forEachIndexed { k, ch -> OutlinedButton(onClick = { cmd("action=choice&value=$k") }, Modifier.fillMaxWidth()) { Text("${"ABCDEF"[k]}. $ch") } }
                        "truefalse" -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { cmd("action=bool&value=true") }, Modifier.weight(1f)) { Text("Vrai") }; Button(onClick = { cmd("action=bool&value=false") }, Modifier.weight(1f)) { Text("Faux") }
                        }
                        "numeric" -> Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(num, { num = it }, Modifier.weight(1f), singleLine = true, label = { Text((ex["unit"] as? String) ?: "nombre") })
                            Spacer(Modifier.width(8.dp)); Button(onClick = { cmd("action=number&value=" + enc(num)); num = "" }) { Text("Envoyer") }
                        }
                        "open" -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("0" to "0", "½" to "0.5", "Tout" to "1").forEach { (l, v) -> OutlinedButton(onClick = { cmd("action=self&value=$v") }) { Text(l) } } }
                        else -> Text("Répondez avec la télécommande sur la TV.")
                    }
                } }
                Text("Ouvrir une fiche sur la TV", style = MaterialTheme.typography.titleMedium)
                val lib = remember { PhoneLibrary.lib(ctx) }
                val packs = remember { runCatching { lib.packs() }.getOrDefault(emptyList()) }
                var pick by remember { mutableStateOf<String?>(null) }
                packs.forEach { r -> TextButton(onClick = { pick = if (pick == r.id) null else r.id }) { Text(r.manifest.title) }
                    if (pick == r.id) lib.pack(r.id)?.lessons?.forEach { l -> Text("   • " + l.title, Modifier.fillMaxWidth().clickable { cmd("action=lesson&pack=${enc(r.id)}&lesson=${enc(l.id)}") }.padding(6.dp)) } }
            }
            if (msg.isNotEmpty()) Text(msg, color = MaterialTheme.colorScheme.error)
        }
    }
}

// ====================================================================== parent dashboard

@Suppress("UNCHECKED_CAST")
@Composable
private fun DashboardTab() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Tableau de bord des parents", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Les progrès sont gardés sur la TV (prénom et classe seulement). Ils ne quittent pas la maison.", style = MaterialTheme.typography.bodySmall)
        TvPicker { c ->
            var data by remember { mutableStateOf<Map<String, Any?>?>(null) }
            var packs by remember { mutableStateOf<Map<String, Any?>?>(null) }
            var err by remember { mutableStateOf("") }
            LaunchedEffect(c) {
                call(c, "GET", "/api/learn/dashboard").onSuccess { data = it }.onFailure { err = it.message ?: "" }
                call(c, "GET", "/api/learn/packs").onSuccess { packs = it }
            }
            if (err.isNotEmpty()) Text(err, color = MaterialTheme.colorScheme.error)
            val students = (data?.get("students") as? List<Any?>).orEmpty().mapNotNull { it as? Map<String, Any?> }
            if (data != null && students.isEmpty()) Text("Aucun élève sur la TV : créez un profil dans « Apprendre » sur la TV.")
            students.forEach { s -> StudentCard(s) }
            val list = (packs?.get("packs") as? List<Any?>).orEmpty().mapNotNull { it as? Map<String, Any?> }
            if (list.isNotEmpty()) {
                Text("Contenus sur la TV", style = MaterialTheme.typography.titleMedium)
                list.forEach { p -> Text("• ${p["title"]}  v${p["version"]}  (${p["where"]})", style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

private fun dur(ms: Long): String { val m = ms / 60_000; return if (m >= 60) "${m / 60} h ${m % 60} min" else "$m min" }

@Suppress("UNCHECKED_CAST")
@Composable
private fun StudentCard(s: Map<String, Any?>) {
    val p = s["profile"] as? Map<String, Any?>
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val avatar = (p?.get("avatar") as? Number)?.toInt() ?: 0
        Text("${LearnProgress.AVATARS.getOrElse(avatar) { "🙂" }}  ${p?.get("name")}  ·  ${LearnCatalog.level(p?.get("level") as? String)?.label ?: ""}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        (p?.get("daysToExam") as? Number)?.let { d -> if (d.toLong() >= 0) Text("${LearnCatalog.exam(p["exam"] as? String)?.label ?: "Examen"} dans ${d} jour(s)", color = MaterialTheme.colorScheme.primary) }
        Text("Temps passé : ${dur((s["timeMs"] as? Number)?.toLong() ?: 0)}  ·  fiches terminées : ${s["lessonsCompleted"]}  ·  ${s["stars"]} ★  ·  série : ${s["streak"]} j")
        Text("Révisions en attente : ${s["reviewsDue"]}")
        (s["subjects"] as? List<Any?>).orEmpty().mapNotNull { it as? Map<String, Any?> }.forEach { sub ->
            val rate = (sub["successRate"] as? Number)?.toInt()
            Column {
                Text("${sub["label"]} : ${dur((sub["timeMs"] as? Number)?.toLong() ?: 0)} · ${sub["lessonsCompleted"]} fiche(s) · " + (rate?.let { "$it % de réussite" } ?: "pas encore d'exercice"), style = MaterialTheme.typography.bodyMedium)
                if (rate != null) LinearProgressIndicator(progress = { rate / 100f }, Modifier.fillMaxWidth())
            }
        }
        (s["mocks"] as? List<Any?>).orEmpty().mapNotNull { it as? Map<String, Any?> }.takeIf { it.isNotEmpty() }?.let { ms ->
            Text("Épreuves blanches : " + ms.joinToString(" · ") { "${LearnCatalog.subject(it["subject"] as? String)?.fr ?: it["subject"]} ${Scene.fmt((it["score"] as? Number)?.toDouble() ?: 0.0)}/20" }, style = MaterialTheme.typography.bodySmall)
        }
        (s["badges"] as? List<Any?>).orEmpty().mapNotNull { (it as? Map<String, Any?>)?.get("label") as? String }.takeIf { it.isNotEmpty() }?.let { Text("🏅 " + it.joinToString(" · "), style = MaterialTheme.typography.bodySmall) }
    } }
}
