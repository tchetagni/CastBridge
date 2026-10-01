package castbridge.sender

import android.annotation.SuppressLint
import android.content.Context
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import castbridge.core.net.HttpLite
import castbridge.core.quiz.HttpTvPackEndpoint
import castbridge.core.quiz.Json
import castbridge.core.quiz.QuizPackRelay
import castbridge.core.quiz.ServerPackSource
import castbridge.core.update.UpdateKeys
import castbridge.core.tv.TvClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * « Quiz » tab of the phone app: finds the TV on the Wi-Fi, opens the quiz on its screen if needed (with the TV's PIN,
 * already known to the app), fetches the room code by itself and joins. The game itself is the TV's mobile page
 * (/quiz) shown full screen: the same page players without the app open by scanning the QR code.
 */
@Composable
fun QuizScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val discovery = remember { TvDiscovery(ctx) }
    DisposableEffect(Unit) { discovery.start(); onDispose { discovery.stop() } }
    val tvs by discovery.tvs.collectAsState()
    val prefs = remember { ctx.getSharedPreferences("castbridge_quiz", Context.MODE_PRIVATE) }
    val pins = remember { PinStore(ctx) }

    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf(prefs.getString("name", "").orEmpty()) }
    var code by rememberSaveable { mutableStateOf("") }
    var playing by rememberSaveable { mutableStateOf<String?>(null) }     // URL of the game page when joined
    var roomOpen by remember { mutableStateOf<Boolean?>(null) }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(tvs) { if (selected == null || tvs.none { it.name == selected }) selected = tvs.firstOrNull()?.name }
    val tv = tvs.firstOrNull { it.name == selected }
    val pin = pins.get(selected)

    // Is a game open on the TV? With the PIN the app also learns the room code: nothing to type.
    LaunchedEffect(tv?.base, pin, playing) {
        while (tv != null && playing == null) {
            val st = withContext(Dispatchers.IO) { roomStatus(tv.base, pin) }
            roomOpen = st?.open
            st?.code?.let { if (code != it) code = it }
            delay(3000)
        }
    }

    // The TV running low on questions without a route of its own gets them from the phone (Internet here): docs/QUIZ.md, « Packs de questions ».
    var packMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(tv?.base, pin) {
        if (tv == null || pin.isEmpty()) return@LaunchedEffect
        packMessage = withContext(Dispatchers.IO) {
            runCatching {
                val keys = UpdateKeys.PUBLIC_KEYS + listOf(BuildConfig.EXTRA_UPDATE_KEY).filter { it.isNotBlank() }
                val server = ServerPackSource(PhoneConnect.state.baseUrl, HttpLite(), PhoneConnect.state.deviceToken, PhoneConnect.state.deviceId, keys, "serveur")
                QuizPackRelay(server, HttpTvPackEndpoint(tv.base, pin), File(ctx.cacheDir, "quiz-packs")).sync().takeIf { it.pushed.isNotEmpty() }?.message
            }.getOrNull()
        }
    }

    // What CastBridge-TV holds (« Mes thèmes »): asked while the TV answers, unknown (null) otherwise: the list never waits for the network.
    var tvLots by remember { mutableStateOf<List<castbridge.core.lots.LotMeta>?>(null) }
    LaunchedEffect(tv?.base, pin) {
        tvLots = if (tv == null || pin.isEmpty()) null else withContext(Dispatchers.IO) { runCatching { HttpTvPackEndpoint(tv.base, pin).status()?.lots }.getOrNull() }
    }

    playing?.let { url ->
        BackHandler { playing = null }
        GamePage(url)
        return
    }

    fun join() {
        val t = tv ?: return
        prefs.edit().putString("name", name.trim()).apply()
        playing = "${t.base}/quiz?code=${enc(code)}&name=${enc(name.trim())}&auto=1"
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Quiz culture générale", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Jouez sur la TV avec vos amis : chacun répond sur son téléphone. Les amis sans l'application scannent simplement le QR code affiché sur la TV.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        if (tvs.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); Spacer(Modifier.width(12.dp))
                Text("Recherche de la TV sur le Wi-Fi…")
            }
        } else tvs.forEach { t ->
            DeviceRow(t.name, t.host, t.name == selected) { selected = t.name }
        }

        val status = when {
            tv == null -> ""
            roomOpen == true -> "Une partie est ouverte sur la TV."
            roomOpen == false -> "Pas de partie ouverte sur la TV pour l'instant."
            else -> ""
        }
        if (status.isNotEmpty()) Text(status, color = MaterialTheme.colorScheme.primary)
        packMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

        if (tv != null && roomOpen != true) {
            Button(enabled = pin.isNotEmpty() && !busy, onClick = {
                busy = true; message = ""
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { runCatching { TvClient(tv.base, pin).raw("POST", "/api/quiz/open") }.getOrNull() }
                    busy = false
                    if (ok == null) message = "La TV n'a pas pu ouvrir le quiz (PIN à vérifier dans l'onglet CastBridge TV)."
                    else Json.obj(ok)["code"]?.let { code = it.toString(); roomOpen = true }
                }
            }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Tv, null); Spacer(Modifier.width(8.dp)); Text("Ouvrir le quiz sur la TV")
            }
            if (pin.isEmpty()) Text("Pour ouvrir le quiz depuis le téléphone, entrez d'abord le PIN de la TV dans l'onglet « CastBridge TV ». " +
                "Sinon, ouvrez-le avec la télécommande (touche MENU).", style = MaterialTheme.typography.bodySmall)
        }

        OutlinedTextField(name, { name = it.take(16) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Votre pseudo") })
        OutlinedTextField(code, { code = it.filter(Char::isDigit).take(4) }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Code de la salle (affiché sur la TV)") },
            textStyle = LocalTextStyle.current.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
        Button(enabled = tv != null && code.length == 4 && name.isNotBlank(), onClick = ::join,
            modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Icon(cbv(R.drawable.ic_cb_lecture), null); Spacer(Modifier.width(8.dp)); Text("Rejoindre la partie", fontSize = 18.sp)
        }
        if (message.isNotEmpty()) Text(message, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(8.dp))
        QuizThemesSection(tvLots)
    }
}

/** The TV's mobile quiz page, full screen. localStorage keeps the player's seat across reconnections. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun GamePage(url: String) {
    AndroidView(factory = { c ->
        WebView(c).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = WebViewClient()                     // stay inside the app
            setBackgroundColor(castbridge.core.brand.BrandTokens.QuizDesMillions.BACKGROUND)
            loadUrl(url)
        }
    }, onRelease = { it.destroy() }, modifier = Modifier.fillMaxSize())
}

private data class RoomStatus(val open: Boolean, val code: String?)

/** GET /api/quiz with the PIN (gives the code), else the public /quiz/api/hello (open or not, no code). */
private fun roomStatus(base: String, pin: String): RoomStatus? {
    if (pin.isNotEmpty()) runCatching {
        val j = Json.obj(TvClient(base, pin).raw("GET", "/api/quiz"))
        return RoomStatus(j["open"] == true, j["code"] as? String)
    }
    return runCatching {
        val c = URL("$base/quiz/api/hello").openConnection() as HttpURLConnection
        c.connectTimeout = 3000; c.readTimeout = 3000
        val j = Json.obj(c.inputStream.use { String(it.readBytes()) })
        RoomStatus(j["open"] == true, null)
    }.getOrNull()
}

private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
