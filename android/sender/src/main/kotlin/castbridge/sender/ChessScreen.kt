package castbridge.sender

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import castbridge.core.chess.ChessPieces
import castbridge.core.chess.ChessSession
import castbridge.core.chess.ChessTransport
import castbridge.core.chess.LanChessClient
import castbridge.core.quiz.Json
import castbridge.core.tv.TvClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * « Échecs » tab of the phone app: finds the TV on the Wi-Fi, opens the chess screen on it if needed (with the TV's PIN),
 * fetches the room code, joins and plays on a native board (tap a piece, then its destination). The TV hosts the game
 * and checks every move ([LanChessClient]). Internet games are played BY the TV (« Échecs › En ligne », docs/CHESS.md § 6); a phone watches its
 * TV's game through the TV and never talks to the online service.
 */
@Composable
fun ChessScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val discovery = remember { TvDiscovery(ctx) }
    DisposableEffect(Unit) { discovery.start(); onDispose { discovery.stop() } }
    val tvs by discovery.tvs.collectAsState()
    val prefs = remember { ctx.getSharedPreferences("castbridge_chess", Context.MODE_PRIVATE) }
    val pins = remember { PinStore(ctx) }

    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf(prefs.getString("name", "").orEmpty()) }
    var code by rememberSaveable { mutableStateOf("") }
    var roomOpen by remember { mutableStateOf<Boolean?>(null) }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var session by remember { mutableStateOf<Pair<ChessTransport, ChessSession>?>(null) }

    LaunchedEffect(tvs) { if (selected == null || tvs.none { it.name == selected }) selected = tvs.firstOrNull()?.name }
    val tv = tvs.firstOrNull { it.name == selected }
    val pin = pins.get(selected)

    LaunchedEffect(tv?.base, pin, session) {
        while (tv != null && session == null) {
            val st = withContext(Dispatchers.IO) { chessStatus(tv.base, pin) }
            roomOpen = st?.open
            st?.code?.let { if (code != it) code = it }
            delay(3000)
        }
    }

    session?.let { (transport, s) ->
        ChessGame(transport, s) { session = null }
        return
    }

    fun join() {
        val n = name.trim()
        prefs.edit().putString("name", n).apply()
        busy = true; message = ""
        val transport: ChessTransport = LanChessClient(tv?.base ?: return)    // a phone only ever talks to ITS TV, never to the online service
        val c = code
        scope.launch {
            val res = withContext(Dispatchers.IO) { runCatching { transport.join(c, n, prefs.getString("token_$c", null)) } }
            busy = false
            res.onSuccess { s -> prefs.edit().putString("token_$c", s.token).apply(); session = transport to s }
                .onFailure { message = it.message ?: "Impossible de rejoindre" }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Échecs", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Jouez contre un ami sur la TV, ou regardez la partie. Chaque coup est vérifié par la TV, qui tient aussi le compte à rebours.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Les parties sur Internet (libres ou avec mise) se jouent sur la TV : « Échecs › En ligne ». Pendant l'une d'elles, saisissez ici le code à 4 chiffres de la TV pour la regarder : " +
            "le téléphone ne se connecte jamais au service, c'est la TV qui vous montre la partie.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        run {
            if (tvs.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); Spacer(Modifier.width(12.dp))
                    Text("Recherche de la TV sur le Wi-Fi…")
                }
            } else tvs.forEach { t -> DeviceRow(t.name, t.host, t.name == selected) { selected = t.name } }
            when (roomOpen) {
                true -> Text("Les échecs sont ouverts sur la TV.", color = MaterialTheme.colorScheme.primary)
                false -> Text("Les échecs ne sont pas ouverts sur la TV.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                null -> {}
            }
            if (tv != null && roomOpen != true) {
                Button(enabled = pin.isNotEmpty() && !busy, onClick = {
                    busy = true; message = ""
                    scope.launch {
                        val r = withContext(Dispatchers.IO) { runCatching { TvClient(tv.base, pin).raw("POST", "/api/chess/open") }.getOrNull() }
                        busy = false
                        if (r == null) message = "La TV n'a pas pu ouvrir les échecs (PIN à vérifier dans l'onglet CastBridge TV)."
                        else runCatching { Json.obj(r)["code"] }.getOrNull()?.let { code = it.toString(); roomOpen = true }
                    }
                }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Filled.Tv, null); Spacer(Modifier.width(8.dp)); Text("Ouvrir les échecs sur la TV") }
                if (pin.isEmpty()) Text("Pour ouvrir les échecs depuis le téléphone, entrez d'abord le PIN de la TV dans l'onglet « CastBridge TV ». " +
                    "Sinon, ouvrez la tuile « Échecs » avec la télécommande.", style = MaterialTheme.typography.bodySmall)
            }
        }
        OutlinedTextField(name, { name = it.take(16) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Votre pseudo") })
        OutlinedTextField(code, { v -> code = v.filter(Char::isDigit).take(4) },
            Modifier.fillMaxWidth(), singleLine = true, label = { Text("Code de la partie (affiché sur la TV)") },
            textStyle = LocalTextStyle.current.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
        val ready = name.isNotBlank() && !busy && tv != null && code.length == 4
        Button(enabled = ready, onClick = ::join, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Icon(cbv(R.drawable.ic_cb_lecture), null); Spacer(Modifier.width(8.dp)); Text("Rejoindre la partie", fontSize = 18.sp)
        }
        if (message.isNotEmpty()) Text(message, color = MaterialTheme.colorScheme.error)
    }
}

private data class ChessStatus(val open: Boolean, val code: String?)

/** GET /api/chess with the PIN (gives the code of the room, or of the TV's Internet game to watch), else the public /chess/api/hello. */
private fun chessStatus(base: String, pin: String): ChessStatus? {
    if (pin.isNotEmpty()) runCatching {
        val j = Json.obj(TvClient(base, pin).raw("GET", "/api/chess"))
        return ChessStatus(j["open"] == true, j["code"] as? String)
    }
    return runCatching {
        val c = java.net.URL("$base/chess/api/hello").openConnection() as java.net.HttpURLConnection
        c.connectTimeout = 3000; c.readTimeout = 3000
        val j = Json.obj(c.inputStream.use { String(it.readBytes()) })
        ChessStatus(j["open"] == true, null)
    }.getOrNull()
}

// ---------------------------------------------------------------------------------------------- the game

private val pieceShapes: Map<Int, List<Pair<Path, Boolean>>> by lazy {
    ChessPieces.SHAPES.mapValues { (_, layers) -> layers.map { PathParser().parsePathString(it.path).toPath() to it.body } }
}

private fun DrawScope.piece(ch: Char, x: Float, y: Float, size: Float) {
    val layers = pieceShapes[" pnbrqk".indexOf(ch.lowercaseChar())] ?: return
    val white = ch.isUpperCase()
    val fill = Color(if (white) ChessPieces.WHITE_FILL else ChessPieces.BLACK_FILL)
    val line = Color(if (white) ChessPieces.WHITE_LINE else ChessPieces.BLACK_LINE)
    translate(x, y) {
        scale(size / 100f, size / 100f, pivot = Offset.Zero) {
            for ((p, body) in layers) {
                if (body) drawPath(p, fill)
                drawPath(p, line, style = Stroke(width = 3.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

private fun parseFen(fen: String): Map<String, Char> {
    val b = HashMap<String, Char>()
    fen.substringBefore(' ').split('/').forEachIndexed { i, row ->
        var f = 0
        for (ch in row) if (ch.isDigit()) f += ch - '0' else { if (f < 8) b["${'a' + f}${8 - i}"] = ch; f++ }
    }
    return b
}

@Suppress("UNCHECKED_CAST")
@Composable
private fun ChessGame(transport: ChessTransport, session: ChessSession, onLeave: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<Map<String, Any?>>(emptyMap()) }
    var stateAt by remember { mutableStateOf(0L) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var selected by remember { mutableStateOf<String?>(null) }
    var promo by remember { mutableStateOf<List<String>>(emptyList()) }
    var confirmResign by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var endShown by remember { mutableStateOf<String?>(null) }
    var flash by remember { mutableStateOf("") }
    var connection by remember { mutableStateOf("") }

    fun apply(s: Map<String, Any?>?) { if (s != null) { state = s; stateAt = System.currentTimeMillis() } }

    LaunchedEffect(session) {
        var since = 0L; var errors = 0
        while (true) {
            try {
                val s = withContext(Dispatchers.IO) { transport.state(session, since, 20) }
                since = (s["v"] as? Number)?.toLong() ?: since
                apply(s); errors = 0; connection = ""
                if (s["stage"] == "CLOSED") { connection = "La TV a fermé la partie."; break }
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                errors++; connection = "Connexion perdue, nouvel essai…"
                if ((e as? castbridge.core.chess.ChessTransportException)?.status in setOf(401, 410)) { connection = "La partie n'existe plus."; break }
                delay(minOf(10_000L, 1_000L * errors))
            }
        }
    }
    LaunchedEffect(Unit) { while (true) { delay(200); now = System.currentTimeMillis() } }

    fun act(block: () -> castbridge.core.chess.ChessAct) {
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching(block) }
            r.onSuccess { a -> apply(a.state); if (a.result == "ILLEGAL") flash = "Coup illégal" else if (a.result == "NOT_YOUR_TURN") flash = "Ce n'est pas votre tour" }
                .onFailure { flash = it.message ?: "Envoi impossible" }
        }
    }

    BackHandler { confirmLeave = true }

    val s = state
    val me = (s["me"] as? Map<String, Any?>)?.get("color") as? String
    val flipped = me == "b"
    val legal = (s["legal"] as? List<String>).orEmpty()
    if (legal.isEmpty() && selected != null) selected = null
    val stage = s["stage"] as? String
    val turn = s["turn"] as? String ?: "w"
    val result = s["result"] as? Map<String, Any?>

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PlayerCard(s, if (flipped) "w" else "b", stateAt, now)
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val side = minOf(maxWidth, 520.dp)
            val board = parseFen(s["fen"] as? String ?: castbridge.core.chess.Position.START_FEN)
            val last = s["lastMove"] as? String
            fun sqXY(sq: String): Pair<Int, Int> { val f = sq[0] - 'a'; val r = sq[1] - '1'; return if (flipped) (7 - f) to r else f to (7 - r) }
            Canvas(Modifier.size(side).pointerInput(legal, selected, flipped) {
                detectTapGestures { o ->
                    val q = size.width / 8f
                    val x = (o.x / q).toInt().coerceIn(0, 7); val y = (o.y / q).toInt().coerceIn(0, 7)
                    val f = if (flipped) 7 - x else x; val r = if (flipped) y else 7 - y
                    val sq = "${'a' + f}${'1' + r}"
                    val sel = selected
                    if (sel != null) {
                        val c = legal.filter { it.startsWith(sel) && it.substring(2, 4) == sq }
                        if (c.size == 1) { selected = null; val ply = (s["ply"] as? Number)?.toInt() ?: 0; act { transport.move(session, c[0], ply) }; return@detectTapGestures }
                        if (c.size > 1) { promo = c; return@detectTapGestures }
                    }
                    selected = if (legal.any { it.startsWith(sq) } && sq != sel) sq else null
                }
            }) {
                val q = size.width / 8f
                for (y in 0 until 8) for (x in 0 until 8)
                    drawRect(Color(if ((x + y) % 2 == 0) ChessPieces.LIGHT else ChessPieces.DARK), Offset(x * q, y * q), Size(q, q))
                last?.takeIf { it.length >= 4 }?.let { m -> for (sq in listOf(m.substring(0, 2), m.substring(2, 4))) { val (x, y) = sqXY(sq); drawRect(Color(ChessPieces.LAST_MOVE), Offset(x * q, y * q), Size(q, q)) } }
                if (s["check"] == true) board.entries.firstOrNull { it.value == (if (turn == "w") 'K' else 'k') }?.let { (sq, _) ->
                    val (x, y) = sqXY(sq)
                    drawRect(Brush.radialGradient(listOf(Color(ChessPieces.CHECK), Color.Transparent), Offset((x + .5f) * q, (y + .5f) * q), q * .7f), Offset(x * q, y * q), Size(q, q))
                }
                selected?.let { val (x, y) = sqXY(it); drawRect(Color(ChessPieces.SELECTED), Offset(x * q, y * q), Size(q, q)) }
                for ((sq, ch) in board) { val (x, y) = sqXY(sq); piece(ch, x * q + q * .05f, y * q + q * .04f, q * .9f) }
                selected?.let { from ->
                    for (to in legal.filter { it.startsWith(from) }.map { it.substring(2, 4) }.distinct()) {
                        val (x, y) = sqXY(to); val c = Offset((x + .5f) * q, (y + .5f) * q)
                        if (board.containsKey(to)) drawCircle(Color(ChessPieces.HINT), q * .44f, c, style = Stroke(q * .08f))
                        else drawCircle(Color(ChessPieces.HINT), q * .15f, c)
                    }
                }
            }
        }
        PlayerCard(s, if (flipped) "b" else "w", stateAt, now)
        val status = when {
            connection.isNotEmpty() -> connection
            stage == "LOBBY" -> "En attente du lancement sur la TV…"
            result != null -> result["text"] as? String ?: "Partie terminée"
            me != null && turn == me -> "À vous de jouer" + (if (s["check"] == true) " — échec !" else "")
            s["thinking"] == true -> "L'ordinateur réfléchit…"
            else -> "Trait aux ${if (turn == "w") "Blancs" else "Noirs"}" + (if (s["check"] == true) " — échec !" else "")
        }
        Text(status, Modifier.fillMaxWidth(), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val note = flash.ifEmpty { s["notice"] as? String ?: "" }
        if (note.isNotEmpty()) Text(note, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodyMedium)
        LaunchedEffect(flash) { if (flash.isNotEmpty()) { delay(2500); flash = "" } }

        val offer = s["drawOffer"] as? String
        if (stage == "PLAYING" && me != null) {
            if (offer != null && offer != me) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ act { transport.draw(session, "accept") } }, Modifier.weight(1f)) { Text("Accepter la nulle") }
                OutlinedButton({ act { transport.draw(session, "decline") } }, Modifier.weight(1f)) { Text("Refuser") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ act { transport.draw(session, "offer") } }, Modifier.weight(1f), enabled = offer == null) {
                    Text(if (offer == me) "Nulle proposée…" else "Proposer nulle", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton({ confirmResign = true }, Modifier.weight(1f)) { Text("Abandonner") }
            }
        }
        if (stage == "LOBBY" && transport is LanChessClient) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val w = s["white"] as? Map<String, Any?>; val b = s["black"] as? Map<String, Any?>
            OutlinedButton({ act { lanSit(transport, session, "w") } }, Modifier.weight(1f), enabled = w?.get("kind") == "PHONE" && me != "w") { Text("Blancs") }
            OutlinedButton({ act { lanSit(transport, session, "b") } }, Modifier.weight(1f), enabled = b?.get("kind") == "PHONE" && me != "b") { Text("Noirs") }
            OutlinedButton({ act { lanSit(transport, session, null) } }, Modifier.weight(1f), enabled = me != null) { Text("Regarder") }
        }
        MoveList((s["san"] as? List<String>).orEmpty())
        OutlinedButton({ confirmLeave = true }, Modifier.fillMaxWidth()) { Text("Quitter la partie") }
    }

    if (promo.isNotEmpty()) AlertDialog(onDismissRequest = { promo = emptyList() }, title = { Text("Promotion") },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (p in listOf("q", "r", "b", "n")) promo.firstOrNull { it.endsWith(p) }?.let { m ->
                    val ch = if (turn == "w") p.uppercase()[0] else p[0]
                    Canvas(Modifier.size(56.dp).background(Color(ChessPieces.LIGHT), RoundedCornerShape(8.dp)).pointerInput(m) {
                        detectTapGestures { promo = emptyList(); selected = null; val ply = (s["ply"] as? Number)?.toInt() ?: 0; act { transport.move(session, m, ply) } }
                    }) { piece(ch, size.width * .05f, size.width * .04f, size.width * .9f) }
                }
            }
        }, confirmButton = {}, dismissButton = { TextButton({ promo = emptyList() }) { Text("Annuler") } })

    if (confirmResign) AlertDialog(onDismissRequest = { confirmResign = false }, title = { Text("Abandonner ?") }, text = { Text("La partie sera perdue.") },
        confirmButton = { TextButton({ confirmResign = false; act { transport.resign(session) } }) { Text("Abandonner") } },
        dismissButton = { TextButton({ confirmResign = false }) { Text("Continuer") } })

    if (confirmLeave) AlertDialog(onDismissRequest = { confirmLeave = false }, title = { Text("Quitter la partie ?") },
        text = { Text("Vous pourrez revenir avec le même code tant que la partie est ouverte. Votre compte à rebours continue pendant l'absence.") },
        confirmButton = { TextButton({ confirmLeave = false; scope.launch(Dispatchers.IO) { transport.leave(session) }; onLeave() }) { Text("Quitter") } },
        dismissButton = { TextButton({ confirmLeave = false }) { Text("Rester") } })

    val endKey = if (stage == "FINISHED" && result != null) "${s["ply"]}-${result["reason"]}" else null
    if (endKey != null && endShown != endKey) {
        val w = result!!["winner"] as? String
        AlertDialog(onDismissRequest = { endShown = endKey },
            title = { Text(if (w == null) "Partie nulle" else if (me != null) (if (w == me) "Vous avez gagné !" else "Vous avez perdu") else if (w == "w") "Les Blancs gagnent" else "Les Noirs gagnent") },
            text = { Text(result["text"] as? String ?: "") },
            confirmButton = { TextButton({ endShown = endKey }) { Text("Fermer") } },
            dismissButton = {
                (s["pgn"] as? String)?.let { pgn -> TextButton({
                    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("PGN", pgn))
                    endShown = endKey
                }) { Text("Copier la partie (PGN)") } }
            })
    }
}

/** Take (or leave) a seat before the game: LAN only (the relay seats players when they join). */
private fun lanSit(t: LanChessClient, s: ChessSession, color: String?): castbridge.core.chess.ChessAct =
    t.seat(s, color)

@Suppress("UNCHECKED_CAST")
@Composable
private fun PlayerCard(s: Map<String, Any?>, color: String, stateAt: Long, now: Long) {
    val side = s[if (color == "w") "white" else "black"] as? Map<String, Any?> ?: emptyMap()
    val clock = s["clock"] as? Map<String, Any?> ?: emptyMap()
    val per = (clock["perMoveMs"] as? Number)?.toLong() ?: 30_000L
    val active = clock["running"] == true && s["turn"] == color && s["stage"] == "PLAYING"
    val left = if (active) (((clock["remainingMs"] as? Number)?.toLong() ?: per) - (now - stateAt)).coerceIn(0, per) else per
    val low = active && left < 10_000
    val blinkOff = low && (now / 400) % 2 == 1L
    val mine = (s["me"] as? Map<String, Any?>)?.get("color") == color
    val accent = MaterialTheme.colorScheme.primary
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
        .border(if (active) 2.dp else 1.dp, if (active) accent else MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(34.dp).background(Color(if (color == "w") 0xFF3A4452.toInt() else 0xFFCFC6B4.toInt()), RoundedCornerShape(8.dp))) {
            piece(if (color == "w") 'K' else 'k', size.width * .06f, size.width * .04f, size.width * .88f)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(side["name"] as? String ?: "", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = (if (color == "w") "Blancs" else "Noirs") + (if (mine) " · vous" else "") +
                (if (side["kind"] == "PHONE" && side["connected"] != true) " · déconnecté" else "") + (if (s["drawOffer"] == color) " · propose la nulle" else "")
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(Modifier.width(84.dp), horizontalAlignment = Alignment.End) {
            Text(if (blinkOff) "" else "${(left + 999) / 1000} s", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold,
                color = if (low) MaterialTheme.colorScheme.error else if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            LinearProgressIndicator(progress = { left.toFloat() / per }, Modifier.fillMaxWidth().height(5.dp),
                color = if (low) MaterialTheme.colorScheme.error else accent, trackColor = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun MoveList(san: List<String>) {
    if (san.isEmpty()) return
    val text = san.chunked(2).mapIndexed { i, p -> "${i + 1}. ${p.joinToString(" ")}" }.joinToString("   ")
    Text(text, Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)).padding(10.dp),
        style = MaterialTheme.typography.bodyMedium)
}
