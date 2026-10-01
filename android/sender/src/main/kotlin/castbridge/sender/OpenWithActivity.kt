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

/**
 * « Ouvrir avec CastBridge » on a video, audio or image: instead of always starting the player, ask what to do.
 * Copy or move to the TV runs in the background (notification, no playback, nothing starts on the TV); playing here is one tap away.
 * Translucent: only the dialog is seen over the app the user came from.
 */
class OpenWithActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = incomingUri(intent)
        if (uri == null) { forwardToPlayer(); finish(); return }
        TvLinkManager.start(this)
        val (name, size) = describe(this, uri)
        val canPlay = true
        setContent {
            CastTheme {
                val link by TvLinkManager.state.collectAsState()
                val session = (link as? LinkUi.Connected)?.session
                val viaBluetoothOnly = session != null && session.base == null
                AlertDialog(
                    onDismissRequest = ::finish,
                    title = { Text(name.substringBeforeLast('.').replace('_', ' ').replace('.', ' '), maxLines = 2) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (size > 0) Text(formatSize(size), style = MaterialTheme.typography.bodyMedium)
                            Text(when {
                                session != null -> "TV : ${session.tv.name}" + if (viaBluetoothOnly) " (par Bluetooth : plus lent)" else ""
                                link is LinkUi.NoTv -> "Aucune TV ajoutée : ouvrez CastBridge pour ajouter votre TV."
                                else -> "Connexion à la TV…"
                            }, style = MaterialTheme.typography.bodyMedium)
                            Text("La copie se fait en arrière-plan, sans lire le fichier. Suivez-la dans la notification.", style = MaterialTheme.typography.bodySmall)
                            androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
                            Button({ send(uri, name, move = false) }, Modifier.fillMaxWidth(), enabled = session != null) { Text("Copier vers la TV") }
                            OutlinedButton({ send(uri, name, move = true) }, Modifier.fillMaxWidth(), enabled = session != null && !viaBluetoothOnly) {
                                Text("Déplacer vers la TV")
                            }
                            if (viaBluetoothOnly) Text("Le déplacement n'est pas disponible par Bluetooth.", style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    confirmButton = { if (canPlay) TextButton({ forwardToPlayer(); finish() }) { Text("Lire ici") } },
                    dismissButton = { TextButton(::finish) { Text("Annuler") } },
                )
            }
        }
    }

    private fun send(uri: Uri, name: String, move: Boolean) {
        // Keep read access beyond this window when the provider allows it (a move deletes the original once the TV holds it).
        runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            .onFailure { runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
        val size = describe(this, uri).second
        runCatching { TransferQueue.enqueue(this, uri, name, size, move) }
            .onSuccess { where ->
                Toast.makeText(this, (if (move) "Déplacement" else "Copie") + " vers la TV en arrière-plan (" + where + ") : voir la notification.", Toast.LENGTH_LONG).show()
                finish()
            }.onFailure { Toast.makeText(this, "Impossible de mettre l'envoi en file : ${it.message}", Toast.LENGTH_LONG).show() }
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
