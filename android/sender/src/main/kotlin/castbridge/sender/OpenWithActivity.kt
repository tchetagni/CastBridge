package castbridge.sender

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import castbridge.core.trust.PinEntry
import castbridge.core.trust.PinEntryState
import castbridge.core.trust.PinVerdict
import castbridge.core.trust.TvAuthReply
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import castbridge.core.phone.MediaKind
import castbridge.core.trust.CopyAndPlay
import castbridge.core.trust.PinCheck
import castbridge.core.trust.SendAction
import castbridge.core.trust.SendChoice
import castbridge.core.trust.SendChoices
import castbridge.core.trust.SendFacts
import castbridge.core.trust.SendRoute
import castbridge.core.trust.TvAuth
import castbridge.core.tv.TvClient
import castbridge.sender.player.CastSession
import castbridge.sender.player.CastTarget
import castbridge.sender.player.Media
import castbridge.sender.player.PlayItem
import castbridge.sender.player.PlayerActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * « Ouvrir avec CastBridge » on a video, audio or image: instead of always starting the player, ask what to do.
 * Copy or move to the TV runs in the background (notification, no playback, nothing starts on the TV); playing here is one tap away.
 * Translucent: only the dialog is seen over the app the user came from.
 */
class OpenWithActivity : ComponentActivity() {
    /** The TV of the code (PIN) path as found on the network by the single check (its address, for the streaming part of « Copier et lire »). */
    @Volatile private var pinTvFound: Tv? = null
    /** Bumped when a code was just kept (the dialog recomputes its choice) / when the window leaves the screen (the typed digits are wiped). */
    private val pinRev = MutableStateFlow(0)
    private val wipeTick = MutableStateFlow(0)

    override fun onStop() { wipeTick.value++; super.onStop() }       // the typed code never survives the background

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = incomingUri(intent)
        if (uri == null) { forwardToPlayer(); finish(); return }
        TvLinkManager.start(this)
        TransferQueue.resume(this)                 // R-09: a queue saved before the app was killed goes on
        val (name, size) = describe(this, uri)
        val canPlay = true
        val kind = MediaKind.of(intent.type, name)
        // The TV of the code (PIN) path, as the home screen knows it: a phone may use its TV this way without any trusted (Bluetooth) TV saved.
        val pins = PinStore(this)
        val pinTv = HomeTv(this).name
        val pinCheck = MutableStateFlow(PinCheck.UNKNOWN)
        // the TV said « je ne vous reconnais plus » a moment ago: the code path is verified too (it is what can still work)
        if (pinTv != null && (TvLinkManager.saved.list().isEmpty() || SendChoices.untrusted(refusalFacts())) && TvAuth.isUsable(pins.get(pinTv))) checkPinTv(pinTv, pins.get(pinTv), pinCheck)
        // the last attempt to copy THIS file failed a moment ago: its cause and what to do, again on the next opening (never lost with the notification)
        val lastFailure = runCatching { CopyReport.journal(this).lastFailureFor(name, 30 * 60_000L)?.text }.getOrNull()
        setContent {
            CastTheme {
                val link by TvLinkManager.state.collectAsState()
                val check by pinCheck.collectAsState()
                val rev by pinRev.collectAsState()
                val wipe by wipeTick.collectAsState()
                val session = (link as? LinkUi.Connected)?.session
                val facts = SendFacts(
                    savedCount = TvLinkManager.saved.list().size, defaultName = TvLinkManager.saved.default()?.name,
                    stepView = when (val l = link) { is LinkUi.Connected -> l.view; is LinkUi.Status -> l.view; else -> null },
                    session = session != null, sessionName = session?.tv?.name, btOnly = session != null && session.base == null,
                    pinTvName = pinTv, pinStored = rev >= 0 && pinTv != null && TvAuth.isUsable(pins.get(pinTv)), pinCheck = check,
                    refusal = rev.let { latestRefusal() }, nowMs = System.currentTimeMillis())
                val choice = SendChoices.decide(facts)
                // « Copier sur la TV et lire »: every state decision is CopyAndPlay's (this build does not learn the TV edition: UNKNOWN, the TV judges the copy)
                val both = CopyAndPlay.decide(CopyAndPlay.Facts(CopyAndPlay.linkOf(facts, choice), ipRoute = session == null || session.base != null,
                    edition = CopyAndPlay.Edition.UNKNOWN, kind = kind))
                AlertDialog(
                    onDismissRequest = ::finish,
                    title = { Text(name.substringBeforeLast('.').replace('_', ' ').replace('.', ' '), maxLines = 2) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (size > 0) Text(formatSize(size), style = MaterialTheme.typography.bodyMedium)
                            // the TV refused this phone: the cause and what to do, ON the phone (never only a Toast)
                            choice.banner?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
                            if (choice.banner == null) lastFailure?.let { Text("Dernière tentative : $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
                            Text(choice.status, style = MaterialTheme.typography.bodyMedium)
                            choice.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
                            // the code is typed right here (never an extra step); the button of the old path stays for the other actions
                            if (PinEntry.showsField(choice.action) && pinTv != null)
                                PinEntryBox(pinTv, pins, wipe, verify = { code -> verifyPin(pinTv, code) }, onAccepted = { code ->
                                    if (pins.put(pinTv, code)) { TvLinkManager.refusals.clearAll(); pinCheck.value = PinCheck.OK; pinRev.value++ }
                                    else Toast.makeText(this@OpenWithActivity, "Le code n'a pas pu être gardé sur ce téléphone.", Toast.LENGTH_LONG).show()
                                })
                            else if (choice.action != SendAction.NONE)
                                Button({ openApp(choice.action) }, Modifier.fillMaxWidth()) { Text(choice.action.label) }
                            // the three ways, most wanted first, each with its one-line explanation (castbridge.core.ux.SendWay: same words everywhere)
                            Button({ copyAndPlay(uri, name, size, both, session, pinTv, pins) }, Modifier.fillMaxWidth(), enabled = both !is CopyAndPlay.Decision.Disabled) { Text(both.label) }
                            // the reason it is limited, else what it does: the TV starts as soon as it has enough lead, this phone becomes its remote (R-08)
                            Text(both.reason ?: castbridge.core.phone.CopyHandoff.BUTTON_HINT, style = MaterialTheme.typography.bodySmall)
                            OutlinedButton({ send(uri, name, move = false, choice, pinTv, pins) }, Modifier.fillMaxWidth(), enabled = choice.copyEnabled) { Text(castbridge.core.ux.SendWay.COPY.label) }
                            Text(castbridge.core.ux.SendWay.COPY.hint + " Suivez la copie dans la notification.", style = MaterialTheme.typography.bodySmall)
                            OutlinedButton({ send(uri, name, move = true, choice, pinTv, pins) }, Modifier.fillMaxWidth(), enabled = choice.moveEnabled) {
                                Text(castbridge.core.ux.SendWay.MOVE.label)
                            }
                            Text(castbridge.core.ux.SendWay.MOVE.hint, style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    confirmButton = { if (canPlay) TextButton({ forwardToPlayer(); finish() }) { Text("Lire ici") } },
                    dismissButton = { TextButton(::finish) { Text("Annuler") } },
                )
            }
        }
    }

    /** The last refusal of the default TV (else of any TV when none is saved): what « Ouvrir avec » must not contradict. */
    private fun latestRefusal(): castbridge.core.trust.RefusalRecord? =
        TvLinkManager.saved.default()?.let { TvLinkManager.refusals.latest(it.address) } ?: if (TvLinkManager.saved.list().isEmpty()) TvLinkManager.refusals.latestAny() else null

    private fun refusalFacts() = SendFacts(refusal = latestRefusal(), nowMs = System.currentTimeMillis())

    /**
     * The same check as the home screen's green dot ([TvHome]: `/api/info` with the code), made ONCE per address found: a refusal is never
     * repeated (five wrong codes lock the TV for a minute). Not found within a few seconds = UNREACHABLE (the upload itself keeps looking).
     */
    private fun checkPinTv(tvName: String, credential: String, out: MutableStateFlow<PinCheck>) {
        val discovery = TvDiscovery(this)
        discovery.start()
        lifecycleScope.launch {
            try {
                var tried: String? = null
                var waited = 0
                while (isActive) {
                    // (tvs is shared WhileSubscribed: read it by subscribing, never through .value)
                    val tv = withTimeoutOrNull(1000) { discovery.tvs.first { l -> l.any { it.name == tvName && it.base != tried } } }?.firstOrNull { it.name == tvName }
                    if (tv != null) {
                        pinTvFound = tv
                        tried = tv.base
                        val r = withContext(Dispatchers.IO) { runCatching { TvClient(tv.base, credential).info() } }
                        val e = r.exceptionOrNull() as? TvClient.HttpError
                        out.value = when {
                            r.isSuccess -> PinCheck.OK
                            e?.code == 401 && "locked" in e.message.orEmpty() -> PinCheck.LOCKED
                            e?.code == 401 && "bad token" in e.message.orEmpty() -> { TvLinkManager.poke(); PinCheck.UNREACHABLE }
                            e?.code == 401 -> PinCheck.REJECTED
                            else -> PinCheck.UNREACHABLE
                        }
                        if (out.value != PinCheck.UNREACHABLE) break
                        delay(2000)
                    } else if (++waited == 8 && out.value == PinCheck.UNKNOWN) out.value = PinCheck.UNREACHABLE
                }
            } finally { discovery.stop() }
        }
    }

    /**
     * The one check of a typed code: the TV of the code path is found by its name, then the same authenticated request as the home screen
     * ([TvClient.info]); sent once per call, the lock-out and the counters stay the TV's ([TvAuthReply]). Never logs the code.
     */
    private suspend fun verifyPin(tvName: String, code: String): PinVerdict {
        val discovery = TvDiscovery(this)
        discovery.start()
        try {
            val tv = withTimeoutOrNull(8000) { discovery.tvs.first { l -> l.any { it.name == tvName } } }?.firstOrNull { it.name == tvName } ?: return PinVerdict.Unreachable
            pinTvFound = tv
            val r = withContext(Dispatchers.IO) { runCatching { TvClient(tv.base, code).info() } }
            if (r.isSuccess) return PinVerdict.Ok
            val e = r.exceptionOrNull() as? TvClient.HttpError ?: return PinVerdict.Unreachable
            return when (val k = TvAuthReply.of(e.code, e.message)) {
                is TvAuthReply.Kind.Locked -> PinVerdict.Locked(k.retryAfterSec)
                TvAuthReply.Kind.BadPin -> PinVerdict.BadPin
                else -> PinVerdict.Unreachable
            }
        } finally { discovery.stop() }
    }

    /** Opens CastBridge on the CastBridge TV tab, on « Ajouter ma TV » or on the code entry (existing assistant). */
    private fun openApp(action: SendAction) {
        val what = when (action) { SendAction.ADD_TV -> TvHomeRequest.ADD_TV; SendAction.ENTER_PIN -> TvHomeRequest.ENTER_PIN; else -> TvHomeRequest.TV }
        startActivity(Intent(this, MainActivity::class.java).putExtra(TvHomeRequest.EXTRA, what)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        finish()
    }

    private fun send(uri: Uri, name: String, move: Boolean, choice: SendChoice, pinTv: String?, pins: PinStore) {
        // Keep read access beyond this window when the provider allows it (a move deletes the original once the TV holds it).
        runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            .onFailure { runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
        val size = describe(this, uri).second
        val what = if (move) "Déplacement" else "Copie"
        when (choice.route) {
            // the trusted link: the queue waits (≤ 1 min) for the link being (re)established
            SendRoute.QUEUE -> runCatching { TransferQueue.add(this, uri, name, size, move) }
                .onSuccess { t -> Toast.makeText(this, sent(what, t), Toast.LENGTH_LONG).show(); finish() }
                .onFailure { Toast.makeText(this, "Impossible de mettre l'envoi en file : ${it.message}", Toast.LENGTH_LONG).show() }
            // the code path: the same upload as the home screen (finds the TV by its name, waits for it), nothing played on the TV;
            // R-09: through the queue too, so a second « Copier » while one runs is queued instead of being dropped in silence
            SendRoute.PIN_UPLOAD -> runCatching { TransferQueue.add(this, uri, name, size, move, tvName = pinTv!!, credential = pins.get(pinTv)) }
                .onSuccess { t -> Toast.makeText(this, sent(what, t), Toast.LENGTH_LONG).show(); finish() }
                .onFailure { Toast.makeText(this, "Impossible de mettre l'envoi en file : ${it.message}", Toast.LENGTH_LONG).show() }
            SendRoute.NONE -> Unit
        }
    }

    private fun sent(what: String, t: TransferQueue.Ticket) =
        if (t.queued) "$what vers la TV : ${t.text}. Voir la notification." else "$what vers la TV en arrière-plan : voir la notification."

    /**
     * « Copier sur la TV et lire »: the existing cast action ([CastAction.COPY], or LIVE when [how] degrades it for a trial TV), started through
     * [CastSession] exactly like the library menu and the cast sheet (upload, TV plays once it holds enough, notifications, failures are CastSession's).
     */
    private fun copyAndPlay(uri: Uri, name: String, size: Long, how: CopyAndPlay.Decision, session: castbridge.core.trust.LinkSession?, pinTv: String?, pins: PinStore) {
        lifecycleScope.launch {
            val item = withContext(Dispatchers.IO) { Media.describe(this@OpenWithActivity, uri, intent.type) } ?: PlayItem(uri, name, intent.type, MediaKind.of(intent.type, name), size)
            val target = castTarget(session, pinTv, pins)
            val error: String? = if (target == null) "la TV n'est pas encore jointe en Wi-Fi" else runCatching {
                CastSession.start(applicationContext, target, how.action, item)
            }.exceptionOrNull()?.let { it.message ?: "erreur inattendue" }
            if (error != null) {
                Toast.makeText(this@OpenWithActivity, "Impossible de démarrer : $error", Toast.LENGTH_LONG).show()
                return@launch
            }
            runCatching {
                startActivity(Intent(this@OpenWithActivity, PlayerActivity::class.java).setAction(PlayerActivity.ACTION_REMOTE)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            }
            finish()
        }
    }

    /** Where to stream: the trusted session's IP base, else the TV of the code path found by the check; null = no IP address known yet. */
    private fun castTarget(session: castbridge.core.trust.LinkSession?, pinTv: String?, pins: PinStore): CastTarget? {
        val base = session?.base
        if (session != null && base != null) {
            val u = runCatching { java.net.URI(base) }.getOrNull() ?: return null
            val host = u.host ?: return null
            return CastTarget.Box(Tv(session.tv.name, host, if (u.port > 0) u.port else 80), session.credential)
        }
        val tv = pinTvFound ?: return null
        return CastTarget.Box(tv, pins.get(pinTv))
    }

    /** Hands the very same intent to the player (grants included). */
    private fun forwardToPlayer() {
        startActivity(Intent(intent).setClass(this, castbridge.sender.player.PlayerActivity::class.java)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }

    private fun incomingUri(i: Intent): Uri? = when (i.action) {
        Intent.ACTION_VIEW -> i.data
        Intent.ACTION_SEND -> @Suppress("DEPRECATION") (i.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri)
        else -> null
    }

    companion object {
        /** File name and size from the provider (size 0 = unknown). */
        fun describe(ctx: Context, uri: Uri): Pair<String, Long> {
            var name = uri.lastPathSegment?.substringAfterLast('/') ?: "video"; var size = 0L
            runCatching { ctx.contentResolver.query(uri, null, null, null, null)?.use { c -> if (c.moveToFirst()) {
                c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { name = c.getString(it) ?: name }
                c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 }?.let { size = c.getLong(it) } } } }
            return name to size
        }
    }
}

/**
 * The code (PIN) field of « Ouvrir avec CastBridge »: masked, numeric keyboard at once, checked on the last digit or « Valider ».
 * Every decision is [PinEntry]'s (pure, tested); the typed digits live only in this composition (not saved, wiped on [wipe] = the window left the screen).
 */
@Composable
private fun PinEntryBox(tv: String, pins: PinStore, wipe: Int, verify: suspend (String) -> PinVerdict, onAccepted: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(PinEntry.start(pins.lockLeft(tv))) }
    var typed by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val cs = MaterialTheme.colorScheme
    val busy = state is PinEntryState.Checking || state is PinEntryState.Accepted
    val locked = state is PinEntryState.Locked
    LaunchedEffect(wipe) { typed = ""; state = PinEntry.clear(state) }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus(); keyboard?.show() } }
    // a lock counts down with the TV's own memo; it ends when the TV says so
    LaunchedEffect(locked) { while (locked) { delay(1000); state = PinEntry.tick(state, pins.lockLeft(tv)) } }
    fun go() {
        val next = PinEntry.submit(state)
        if (next !is PinEntryState.Checking) return
        state = next
        val code = typed
        scope.launch {
            val v = verify(code)
            typed = ""
            state = PinEntry.result(state, v)
            (state as? PinEntryState.Locked)?.let { pins.locked(tv, it.seconds) }
            if (state is PinEntryState.Accepted) onAccepted(code)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(typed, { v ->
            val next = PinEntry.input(state, v)
            typed = PinEntry.digitsOf(next); state = next
            if (PinEntry.readyToCheck(next)) go()
        }, Modifier.fillMaxWidth().focusRequester(focus).semantics { contentDescription = PinEntry.FIELD_DESCRIPTION },
            singleLine = true, enabled = !busy && !locked, label = { Text(PinEntry.FIELD_LABEL) },
            isError = state is PinEntryState.Wrong, visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
        PinEntry.message(state)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = if (state is PinEntryState.Checking) cs.onSurfaceVariant else cs.error) }
        Button({ go() }, Modifier.fillMaxWidth(), enabled = PinEntry.readyToCheck(state)) { Text(PinEntry.SUBMIT_LABEL) }
    }
}
