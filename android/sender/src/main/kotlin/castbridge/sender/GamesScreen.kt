package castbridge.sender

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import castbridge.core.quiz.Json
import castbridge.core.tv.TvClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One game of the « Jeux » tab (docs/GAMES.md): add a line to [PHONE_GAMES] and a branch in [GamesScreen] to add a game. */
private class PhoneGame(val id: String, val icon: ImageVector, val name: String, val modes: String, val blurb: String, val accent: Color)

private val PHONE_GAMES = listOf(
    PhoneGame("quiz", Icons.Filled.EmojiEvents, "Quiz des Millions", "Solo · Multijoueur", "Culture générale et niveaux scolaires : jouez sur la TV, répondez sur votre téléphone.", Color(0xFFFF5C39)),
    PhoneGame("chess", Icons.Filled.Extension, "Échecs", "Solo · À deux · En ligne", "Contre l'ordinateur, à deux ou en ligne, avec compte à rebours.", Color(0xFF2FA96B)),
    PhoneGame("sudoku", Icons.Filled.GridOn, "Sudoku", "Solo · sur la TV", "Grilles de Facile à Expert. Jouez sur la TV, pilotez depuis le téléphone.", Color(0xFF6CB6FF)),
)

/**
 * « Jeux » tab of the phone app: one card per game. Quiz and Chess open their existing screens unchanged
 * ([QuizScreen], [ChessScreen]); the Sudoku offers « Jouer sur la TV » and then a remote control ([SudokuRemote]).
 */
@Composable
fun GamesScreen() {
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    val game = PHONE_GAMES.firstOrNull { it.id == open }
    if (game == null) {
        GamesList { open = it; PhoneConnect.feature(it, "tile") }
        return
    }
    BackHandler { open = null }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().clickable { open = null }.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour aux jeux", tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text("Jeux", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text("  ›  ${game.name}", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (game.id) { "quiz" -> QuizScreen(); "chess" -> ChessScreen(); else -> SudokuRemote() }
        }
    }
}

@Composable
private fun GamesList(onOpen: (String) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Jeux", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Jouez sur la TV, avec vos amis ou en solo.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        PHONE_GAMES.forEach { g ->
            Card(Modifier.fillMaxWidth().clickable { onOpen(g.id) }, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(56.dp).background(g.accent.copy(alpha = 0.2f), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                        Icon(g.icon, null, tint = g.accent, modifier = Modifier.size(32.dp))
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(g.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(g.modes, color = g.accent, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(g.blurb, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private class SudokuState(val open: Boolean, val busy: Boolean, val level: String?, val elapsedMs: Long, val filled: Int, val hintsLeft: Int,
                          val won: Boolean, val notesMode: Boolean, val cursor: Int, val puzzle: String, val board: String, val saved: Boolean, val menu: Boolean)

private fun parseSudoku(json: String): SudokuState? = runCatching {
    val j = Json.obj(json)
    fun n(k: String) = (j[k] as? Number)?.toLong() ?: 0L
    SudokuState(j["open"] == true, j["busy"] == true, j["levelLabel"] as? String, n("elapsedMs"), n("filled").toInt(), n("hintsLeft").toInt(),
        j["won"] == true, j["notesMode"] == true, n("cursor").toInt(), (j["puzzle"] as? String).orEmpty(), (j["board"] as? String).orEmpty(), j["saved"] == true, j["menu"] == true)
}.getOrNull()

/** Sudoku of the TV: open it (« Jouer sur la TV »), then a remote control with a mirror of the grid, arrows, digits, notes, hint, check. */
@Composable
private fun SudokuRemote() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val discovery = remember { TvDiscovery(ctx) }
    DisposableEffect(Unit) { discovery.start(); onDispose { discovery.stop() } }
    val tvs by discovery.tvs.collectAsState()
    val pins = remember { PinStore(ctx) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var st by remember { mutableStateOf<SudokuState?>(null) }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(tvs) { if (selected == null || tvs.none { it.name == selected }) selected = tvs.firstOrNull()?.name }
    val tv = tvs.firstOrNull { it.name == selected }
    val pin = pins.get(selected)
    val client = remember(tv?.base, pin) { tv?.let { TvClient(it.base, pin) } }

    suspend fun call(method: String, path: String): SudokuState? = withContext(Dispatchers.IO) {
        runCatching { client?.raw(method, path)?.let { parseSudoku(it) } }.onFailure { e -> message = e.message.orEmpty().take(120) }.getOrNull()
    }
    fun cmd(what: String) { scope.launch { call("POST", "/api/sudoku/cmd?do=$what")?.let { st = it } } }

    LaunchedEffect(client) {
        while (client != null && pin.isNotEmpty()) {
            call("GET", "/api/sudoku")?.let { st = it }
            delay(1500)
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Sudoku sur la TV", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (tvs.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); Spacer(Modifier.width(12.dp)); Text("Recherche de la TV sur le Wi-Fi…")
            }
        } else tvs.forEach { t -> DeviceRow(t.name, t.host, t.name == selected) { selected = t.name } }
        if (tv != null && pin.isEmpty()) Text("Entrez d'abord le PIN de la TV dans l'onglet « CastBridge TV ».", color = MaterialTheme.colorScheme.error)

        val s = st
        if (tv != null && pin.isNotEmpty() && (s == null || !s.open)) {
            Text("Le Sudoku se joue sur l'écran de la TV, à la télécommande ou avec ce téléphone." +
                if (s?.saved == true) " Une partie est en cours : elle sera reprise." else "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(enabled = !busy, modifier = Modifier.fillMaxWidth().height(56.dp), onClick = {
                busy = true; message = ""
                scope.launch {
                    val r = withContext(Dispatchers.IO) { runCatching { client?.raw("POST", "/api/sudoku/open") }.getOrNull() }
                    busy = false
                    if (r == null) message = "La TV n'a pas pu ouvrir le Sudoku : ouvrez CastBridge TV sur la TV (écran d'accueil ou « Jeux »), puis réessayez."
                    else parseSudoku(r)?.let { st = it }
                }
            }) { Icon(Icons.Filled.Tv, null); Spacer(Modifier.width(8.dp)); Text("Jouer sur la TV", fontSize = 18.sp) }
        }
        if (s != null && s.open) SudokuPad(s, ::cmd, onNew = { lv -> cmd("new&level=$lv") })
        if (message.isNotEmpty()) Text(message, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun SudokuPad(s: SudokuState, cmd: (String) -> Unit, onNew: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val mins = s.elapsedMs / 1000
    if (s.busy) Text("La TV prépare la grille…", color = cs.primary)
    else if (s.level == null) Text("Choisissez le niveau sur la TV, ou ici :", color = cs.primary)
    else Text("${s.level}  ·  ${mins / 60}:${"%02d".format(mins % 60)}  ·  ${s.filled}/81 cases  ·  ${s.hintsLeft} indice(s)" + if (s.won) "  ·  Gagné !" else "",
        fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
    if (s.puzzle.length == 81 && s.board.length == 81) {
        Column(Modifier.fillMaxWidth().border(2.dp, cs.outline)) {
            for (r in 0 until 9) Row(Modifier.fillMaxWidth()) {
                for (c in 0 until 9) {
                    val i = r * 9 + c
                    val v = s.board[i]
                    val given = s.puzzle[i] != '0'
                    Box(Modifier.weight(1f).aspectRatio(1f)
                        .background(when { i == s.cursor -> cs.primaryContainer; (r / 3 + c / 3) % 2 == 0 -> cs.surface; else -> cs.surfaceVariant })
                        .border(0.5.dp, cs.outlineVariant).clickable { cmd("cell&v=$i") }, contentAlignment = Alignment.Center) {
                        if (v != '0') Text(v.toString(), fontSize = 18.sp, fontWeight = if (given) FontWeight.Bold else FontWeight.Normal,
                            color = if (given) cs.onSurface else cs.primary, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
    // arrows
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton({ cmd("left") }) { Icon(Icons.Filled.KeyboardArrowLeft, "Gauche") }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            FilledTonalIconButton({ cmd("up") }) { Icon(Icons.Filled.KeyboardArrowUp, "Haut") }
            FilledTonalIconButton({ cmd("down") }) { Icon(Icons.Filled.KeyboardArrowDown, "Bas") }
        }
        FilledTonalIconButton({ cmd("right") }) { Icon(Icons.Filled.KeyboardArrowRight, "Droite") }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (d in 1..9) FilledTonalButton({ cmd("digit&v=$d") }, Modifier.weight(1f).height(48.dp), contentPadding = PaddingValues(0.dp)) { Text("$d", fontSize = 18.sp) }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton({ cmd("clear") }, Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) { Text("Effacer", maxLines = 1) }
        if (s.notesMode) Button({ cmd("notes") }, Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) { Text("Notes", maxLines = 1) }
        else OutlinedButton({ cmd("notes") }, Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) { Text("Notes", maxLines = 1) }
        OutlinedButton({ cmd("hint") }, Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) { Text("Indice", maxLines = 1) }
        OutlinedButton({ cmd("check") }, Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) { Text("Vérifier", maxLines = 1) }
    }
    Text("Nouvelle partie :", style = MaterialTheme.typography.labelLarge)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("EASY" to "Facile", "MEDIUM" to "Moyen", "HARD" to "Difficile", "EXPERT" to "Expert").forEach { (k, label) ->
            OutlinedButton({ onNew(k) }, Modifier.weight(1f), contentPadding = PaddingValues(2.dp)) { Text(label, fontSize = 12.sp, maxLines = 1) }
        }
    }
    OutlinedButton({ cmd("quit") }, Modifier.fillMaxWidth()) { Text("Quitter le Sudoku sur la TV") }
}
