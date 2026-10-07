package castbridge.sender

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import castbridge.core.phone.MediaKind
import castbridge.core.tv.ReshareTexts

/**
 * R-22: « Choisir le fichier ». The files whose provider no longer gives access (shared from Telegram, then the app was restarted) are picked again
 * in the system file chooser (ACTION_OPEN_DOCUMENT: the right is PERSISTABLE, so this never happens again for them). Several files at once;
 * each pick is matched to the waiting item by name (and size when known), then the queue goes on by itself. The chooser cannot be pre-filled with a
 * name (Android offers no such option): the names are said in a toast instead. No UI of its own.
 */
class ReselectActivity : ComponentActivity() {
    private val pick = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        var n = 0
        for (u in uris) {
            runCatching { contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val (name, size) = OpenWithActivity.describe(this, u)
            val item = TransferQueue.toReshare().firstOrNull { it.name == name && (it.size <= 0 || size <= 0 || it.size == size) }
            if (item != null && TransferQueue.reanchor(this, item.id, u)) n++
        }
        val left = TransferQueue.toReshare().size
        Toast.makeText(this, when {
            n > 0 && left == 0 -> "CastBridge : $n fichier(s) repris, l'envoi continue."
            n > 0 -> "CastBridge : $n fichier(s) repris, il en reste $left à choisir."
            uris.isEmpty() -> "CastBridge : aucun fichier choisi."
            else -> "CastBridge : aucun des fichiers choisis ne correspond à la file (même nom attendu)."
        }, Toast.LENGTH_LONG).show()
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val items = TransferQueue.toReshare()
        if (items.isEmpty()) { finish(); return }
        val kinds = items.map { MediaKind.of(null, it.name) }.toSet()
        val mimes = buildList { add("video/*"); if (MediaKind.AUDIO in kinds) add("audio/*"); if (MediaKind.IMAGE in kinds) add("image/*") }
        Toast.makeText(this, "Choisissez : " + items.take(3).joinToString(", ") { "« ${it.name} »" } + if (items.size > 3) ", …" else "", Toast.LENGTH_LONG).show()
        runCatching { pick.launch(mimes.toTypedArray()) }.onFailure { finish() }
    }
}
