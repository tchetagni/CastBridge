@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package castbridge.sender

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import castbridge.core.dl.DownloadsClient
import castbridge.core.dl.LinkParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "Partager > CastBridge" from the browser (a link), a magnet: link opened anywhere, or a .torrent file: sends it to the
 * TV the user last managed (found again by name through mDNS if its address changed). Shows the "only what you have the
 * right to" notice first when the TV has not seen it acknowledged yet.
 */
class ShareToTvActivity : ComponentActivity() {
    private sealed class What { data class Link(val url: String) : What(); data class File(val name: String, val uri: Uri) : What() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val what = parse(intent)
        setContent { CastTheme { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { Screen(what) } } }
    }

    private fun parse(i: Intent): What? = when (i.action) {
        Intent.ACTION_SEND -> {
            val text = i.getStringExtra(Intent.EXTRA_TEXT).orEmpty() + " " + i.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty()
            DownloadsClient.findLink(text)?.let { What.Link(it) }
                ?: (if (android.os.Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) else @Suppress("DEPRECATION") i.getParcelableExtra(Intent.EXTRA_STREAM))
                    ?.let { What.File(displayName(it), it) }
        }
        Intent.ACTION_VIEW -> i.data?.let { u ->
            if (u.scheme.equals("magnet", true)) What.Link(u.toString()) else What.File(displayName(u), u)
        }
        else -> null
    }

    private fun displayName(u: Uri): String = runCatching {
        contentResolver.query(u, null, null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(c.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else null }
    }.getOrNull() ?: u.lastPathSegment ?: "fichier.torrent"

    /** The remembered TV, or the same TV found again on the network (the PIN is remembered per TV name). */
    private suspend fun findTv(): DownloadsClient? {
        val prefs = DlPrefs(this)
        prefs.base?.let { b ->
            val c = DownloadsClient(b, prefs.pin)
            if (withContext(Dispatchers.IO) { runCatching { c.state() }.isSuccess }) return c
        }
        val disc = TvDiscovery(this)
        disc.start()
        try {
            repeat(12) {
                delay(500)
                for (tv in disc.tvs.value) {
                    val pin = PinStore(this).get(tv.name).ifEmpty { prefs.pin.orEmpty() }
                    val c = DownloadsClient(tv.base, pin.ifEmpty { null })
                    if (withContext(Dispatchers.IO) { runCatching { c.state() }.isSuccess }) { prefs.remember(tv.base, pin); return c }
                }
            }
        } finally { disc.stop() }
        return null
    }

    @Composable
    private fun Screen(what: What?) {
        val scope = rememberCoroutineScope()
        var tv by remember { mutableStateOf<DownloadsClient?>(null) }
        var looking by remember { mutableStateOf(true) }
        var status by remember { mutableStateOf<DownloadsClient.State?>(null) }
        var result by remember { mutableStateOf<String?>(null) }
        var sending by remember { mutableStateOf(false) }
        var showList by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            tv = findTv(); looking = false
            tv?.let { c -> status = withContext(Dispatchers.IO) { runCatching { c.state() }.getOrNull() } }
        }
        val c = tv
        if (showList && c != null) { DownloadsScreen(c, onClose = { finish() }); return }

        fun send() {
            val client = c ?: return
            sending = true
            scope.launch {
                result = try {
                    withContext(Dispatchers.IO) {
                        when (what) {
                            is What.Link -> client.add(what.url)
                            is What.File -> client.upload(what.name, contentResolver.openInputStream(what.uri)!!.use { s ->
                                val b = s.readBytes(); if (b.size > 4 shl 20) throw DownloadsClient.Refused(413, "size", "Fichier trop gros pour un .torrent."); b })
                            null -> null
                        }
                    } ?: "C'est parti : la TV télécharge."
                } catch (e: DownloadsClient.Refused) { e.message } catch (e: Exception) { "TV injoignable : ${e.message}" }
                sending = false
            }
        }

        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Télécharger sur la TV", style = MaterialTheme.typography.headlineSmall)
            when (what) {
                is What.Link -> {
                    Text(LinkParser.display(what.url), style = MaterialTheme.typography.bodySmall, maxLines = 4)
                    val (ok, desc) = describeLink(what.url)
                    Text(desc, color = if (ok) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
                }
                is What.File -> Text(what.name)
                null -> Text("Aucun lien ni fichier .torrent reconnu dans ce partage.", color = MaterialTheme.colorScheme.error)
            }
            when {
                looking -> Row { CircularProgressIndicator(Modifier.size(20.dp)); Spacer(Modifier.width(12.dp)); Text("Recherche de la TV…") }
                c == null -> Text("TV introuvable. Ouvrez CastBridge > CastBridge TV, connectez-vous à la TV (PIN), ouvrez « Téléchargements sur la TV » une fois, puis partagez à nouveau.")
                status?.warningAccepted == false -> {
                    Text("Avant de commencer", fontWeight = FontWeight.Bold)
                    Text(status!!.warning)
                    Button(onClick = { scope.launch { withContext(Dispatchers.IO) { runCatching { c.accept() } }; status = status?.copy(warningAccepted = true) } }) { Text("J'ai compris") }
                }
                result == null -> Button(enabled = !sending && what != null && (what !is What.Link || describeLink(what.url).first), onClick = ::send) {
                    Text(if (sending) "Envoi…" else "Télécharger sur la TV")
                }
                else -> {
                    Text(result!!)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Button(onClick = { showList = true }) { Text("Voir les téléchargements") }
                        OutlinedButton(onClick = { finish() }) { Text("Fermer") }
                    }
                }
            }
            if (result == null) TextButton(onClick = { finish() }) { Text("Annuler") }
        }
    }
}
