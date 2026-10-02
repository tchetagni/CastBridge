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
import androidx.compose.material3.OutlinedButton
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
import castbridge.core.phone.CastAction
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = incomingUri(intent)
        if (uri == null) { forwardToPlayer(); finish(); return }
        TvLinkManager.start(this)
        val (name, size) = describe(this, uri)
        val canPlay = true
        val kind = MediaKind.of(intent.type, name)
        // The TV of the code (PIN) path, as the home screen knows it: a phone may use its TV this way without any trusted (Bluetooth) TV saved.
        val pins = PinStore(this)
        val pinTv = HomeTv(this).name
        val pinCheck = MutableStateFlow(PinCheck.UNKNOWN)
        if (pinTv != null && TvLinkManager.saved.list().isEmpty() && TvAuth.isUsable(pins.get(pinTv))) checkPinTv(pinTv, pins.get(pinTv), pinCheck)
        setContent {
            CastTheme {
                val link by TvLinkManager.state.collectAsState()
                val check by pinCheck.collectAsState()
                val session = (link as? LinkUi.Connected)?.session
                val facts = SendFacts(
                    savedCount = TvLinkManager.saved.list().size, defaultName = TvLinkManager.saved.default()?.name,
                    stepView = when (val l = link) { is LinkUi.Connected -> l.view; is LinkUi.Status -> l.view; else -> null },
                    session = session != null, sessionName = session?.tv?.name, btOnly = session != null && session.base == null,
                    pinTvName = pinTv, pinStored = pinTv != null && TvAuth.isUsable(pins.get(pinTv)), pinCheck = check)
                val choice = SendChoices.decide(facts)
                // « Copier et lire sur la TV »: every state decision is CopyAndPlay's (this build does not learn the TV edition: UNKNOWN, the TV judges the copy)
                val both = CopyAndPlay.decide(CopyAndPlay.Facts(CopyAndPlay.linkOf(facts, choice), ipRoute = session == null || session.base != null,
                    edition = CopyAndPlay.Edition.UNKNOWN, kind = kind))
                AlertDialog(
                    onDismissRequest = ::finish,
                    title = { Text(name.substringBeforeLast('.').replace('_', ' ').replace('.', ' '), maxLines = 2) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (size > 0) Text(formatSize(size), style = MaterialTheme.typography.bodyMedium)
                            Text(choice.status, style = MaterialTheme.typography.bodyMedium)
                            Text("La copie se fait en arrière-plan, sans lire le fichier. Suivez-la dans la notification.", style = MaterialTheme.typography.bodySmall)
                            choice.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
                            if (choice.action != SendAction.NONE)
                                Button({ openApp(choice.action) }, Modifier.fillMaxWidth()) { Text(choice.action.label) }
                            Button({ send(uri, name, move = false, choice, pinTv, pins) }, Modifier.fillMaxWidth(), enabled = choice.copyEnabled) { Text("Copier vers la TV") }
                            Button({ copyAndPlay(uri, name, size, both, choice, session, pinTv, pins) }, Modifier.fillMaxWidth(), enabled = both !is CopyAndPlay.Decision.Disabled) { Text(both.label) }
                            both.reason?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            OutlinedButton({ send(uri, name, move = true, choice, pinTv, pins) }, Modifier.fillMaxWidth(), enabled = choice.moveEnabled) {
                                Text("Déplacer vers la TV")
                            }
                        }
                    },
                    confirmButton = { if (canPlay) TextButton({ forwardToPlayer(); finish() }) { Text("Lire ici") } },
                    dismissButton = { TextButton(::finish) { Text("Annuler") } },
                )
            }
        }
    }

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
            SendRoute.QUEUE -> runCatching { TransferQueue.enqueue(this, uri, name, size, move) }
                .onSuccess { where -> Toast.makeText(this, "$what vers la TV en arrière-plan ($where) : voir la notification.", Toast.LENGTH_LONG).show(); finish() }
                .onFailure { Toast.makeText(this, "Impossible de mettre l'envoi en file : ${it.message}", Toast.LENGTH_LONG).show() }
            // the code path: the same upload as the home screen for one file (finds the TV by its name, waits for it), nothing played on the TV
            SendRoute.PIN_UPLOAD -> runCatching { UploadService.start(this, uri, name, pinTv!!, null, pins.get(pinTv), autoPlay = false, move = move) }
                .onSuccess { Toast.makeText(this, "$what vers la TV en arrière-plan : voir la notification.", Toast.LENGTH_LONG).show(); finish() }
                .onFailure { Toast.makeText(this, "Impossible de démarrer l'envoi : ${it.message}", Toast.LENGTH_LONG).show() }
            SendRoute.NONE -> Unit
        }
    }

    /**
     * « Copier et lire sur la TV »: (1) playback starts at once by streaming from the phone (the cast flow's LIVE action, [CastSession]),
     * (2) the normal background copy is enqueued as « Copier vers la TV » does ([send]'s routes, move = false) unless [how] says the copy is closed
     * (trial TV). Each part fails alone: a failed start leaves the copy running, a failed enqueue leaves the playback running; the user is told in one sentence.
     * The phone serves the stream while the copy runs: [CastSession] and the upload service keep it alive until the copy ends.
     */
    private fun copyAndPlay(uri: Uri, name: String, size: Long, how: CopyAndPlay.Decision, choice: SendChoice, session: castbridge.core.trust.LinkSession?, pinTv: String?, pins: PinStore) {
        lifecycleScope.launch {
            val item = withContext(Dispatchers.IO) { Media.describe(this@OpenWithActivity, uri, intent.type) } ?: PlayItem(uri, name, intent.type, MediaKind.of(intent.type, name), size)
            val target = castTarget(session, pinTv, pins)
            val playError: String? = if (target == null) "la TV n'est pas encore jointe en Wi-Fi" else runCatching {
                CastSession.start(applicationContext, target, CastAction.LIVE, item)
            }.exceptionOrNull()?.let { it.message ?: "erreur inattendue" }
            var copyError: String? = null
            if (how.copies) {
                withContext(Dispatchers.IO) { runCatching {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } }
                when (choice.route) {
                    SendRoute.QUEUE -> runCatching { TransferQueue.enqueue(this@OpenWithActivity, uri, name, size, false) }.onFailure { copyError = it.message ?: "erreur inattendue" }
                    SendRoute.PIN_UPLOAD -> runCatching { UploadService.start(this@OpenWithActivity, uri, name, pinTv!!, null, pins.get(pinTv), autoPlay = false, move = false) }
                        .onFailure { copyError = it.message ?: "erreur inattendue" }
                    SendRoute.NONE -> copyError = "aucune TV prête"
                }
            }
            val said = when {
                playError != null && copyError != null -> "Rien n'a pu démarrer : lecture ($playError), copie ($copyError)."
                playError != null -> "La lecture n'a pas pu démarrer ($playError) ; la copie continue en arrière-plan : voir la notification."
                copyError != null -> "La lecture est lancée sur la TV, mais la copie n'a pas pu être mise en file ($copyError)."
                how.copies -> "Lecture lancée sur la TV ; la copie continue en arrière-plan : gardez le téléphone connecté jusqu'à la notification de fin."
                else -> "Lecture lancée sur la TV (sans copie)."
            }
            Toast.makeText(this@OpenWithActivity, said, Toast.LENGTH_LONG).show()
            if (playError == null) runCatching {
                startActivity(Intent(this@OpenWithActivity, PlayerActivity::class.java).setAction(PlayerActivity.ACTION_REMOTE)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            }
            if (playError == null || copyError == null) finish()
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
