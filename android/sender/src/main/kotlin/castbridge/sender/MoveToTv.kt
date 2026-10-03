package castbridge.sender

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

/**
 * Second half of "move to the TV": the TV confirmed a complete copy (see UploadService.checkMoved), now remove the
 * file from the phone. Documents the app may write are deleted directly; media files go through Android's own
 * deletion confirmation (MediaStore.createDeleteRequest, Android 11+). Shown once in the app root.
 */
@Composable
fun MoveHandler() {
    val ctx = LocalContext.current
    val req by UploadService.moveReady.collectAsState()
    val note by UploadService.moveNote.collectAsState()
    val confirm = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        val name = req?.name
        Toast.makeText(ctx, if (r.resultCode == Activity.RESULT_OK) "« $name » déplacé : il n'est plus sur le téléphone"
            else "Suppression annulée : « $name » reste aussi sur le téléphone", Toast.LENGTH_LONG).show()
        UploadService.moveHandled()
    }
    note?.let { n ->
        AlertDialog(onDismissRequest = { UploadService.noteHandled() },
            confirmButton = { TextButton({ UploadService.noteHandled() }) { Text("OK") } },
            title = { Text("Déplacement") }, text = { Text(n) })
    }
    fun proceed(r: UploadService.MoveRequest) {
        // R-12 (second audit): just before the deletion, the original must still be the file that was checked (same size, same date); else it is kept
        val now = fileStamp(ctx, r.uri)
        if (!castbridge.core.tv.MoveProof.unchanged(r.size, r.stamp, now?.first ?: -1, now?.second ?: -1)) {
            Toast.makeText(ctx, "« ${r.name} » : " + castbridge.core.tv.MoveProof.CHANGED_TEXT, Toast.LENGTH_LONG).show()
            UploadService.moveHandled(); return
        }
        when (val out = deleteFromPhone(ctx, r.uri)) {
            null -> { Toast.makeText(ctx, "« ${r.name} » déplacé vers la TV", Toast.LENGTH_LONG).show(); UploadService.moveHandled() }
            is DeleteNeedsConfirmation -> confirm.launch(IntentSenderRequest.Builder(out.sender).build())
            is DeleteImpossible -> {
                Toast.makeText(ctx, "« ${r.name} » est sur la TV, mais le téléphone ne permet pas de le supprimer d'ici : supprimez-le depuis la Galerie.", Toast.LENGTH_LONG).show()
                UploadService.moveHandled()
            }
        }
    }
    // R-12 audit: a path that deletes WITHOUT Android's own dialog (a document, Android 10 and older) is confirmed in the app first
    var ask by remember { mutableStateOf<UploadService.MoveRequest?>(null) }
    ask?.let { r ->
        AlertDialog(onDismissRequest = { ask = null; Toast.makeText(ctx, "« ${r.name} » reste aussi sur le téléphone", Toast.LENGTH_LONG).show(); UploadService.moveHandled() },
            confirmButton = { TextButton({ ask = null; proceed(r) }) { Text("Supprimer") } },
            dismissButton = { TextButton({ ask = null; Toast.makeText(ctx, "« ${r.name} » reste aussi sur le téléphone", Toast.LENGTH_LONG).show(); UploadService.moveHandled() }) { Text("Garder") } },
            title = { Text("Déplacement") }, text = { Text("Supprimer l'original de « ${r.name} » de ce téléphone ? La TV en a une copie vérifiée.") })
    }
    LaunchedEffect(req) {
        val r = req ?: return@LaunchedEffect
        if (deletesWithoutSystemDialog(ctx, r.uri)) ask = r else proceed(r)
    }
}

/** (size, modification date in ms) of a phone file, from the provider (documents: COLUMN_LAST_MODIFIED; media: DATE_MODIFIED); date -1 if unknown; null if unreadable. */
internal fun fileStamp(ctx: Context, uri: Uri): Pair<Long, Long>? = runCatching {
    val size = ctx.contentResolver.openFileDescriptor(uri, "r")!!.use { it.statSize }
    var date = -1L
    ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
        if (c.moveToFirst()) {
            c.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED).takeIf { it >= 0 && !c.isNull(it) }?.let { date = c.getLong(it) }
            if (date <= 0) c.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED).takeIf { it >= 0 && !c.isNull(it) }?.let { date = c.getLong(it) * 1000 }
        }
    }
    size to date
}.getOrNull()

/** True when [deleteFromPhone] would delete [uri] directly (a document the picker let us write, Android 10 and older): the app must ask first. */
private fun deletesWithoutSystemDialog(ctx: Context, uri: Uri): Boolean {
    if (DocumentsContract.isDocumentUri(ctx, uri)) return true
    if (Build.VERSION.SDK_INT < 30) return true
    return uri.authority != MediaStore.AUTHORITY
}

private sealed interface DeleteOutcome
private class DeleteNeedsConfirmation(val sender: android.content.IntentSender) : DeleteOutcome
private object DeleteImpossible : DeleteOutcome

/** null = deleted. */
private fun deleteFromPhone(ctx: Context, uri: Uri): DeleteOutcome? {
    val cr = ctx.contentResolver
    // 1) A document the picker let us write (Downloads, Files...): delete it directly.
    if (DocumentsContract.isDocumentUri(ctx, uri) && runCatching { DocumentsContract.deleteDocument(cr, uri) }.getOrDefault(false)) return null
    // 2) A photo/video of the gallery: Android asks the user ("Allow CastBridge to delete this video?").
    if (Build.VERSION.SDK_INT >= 30) {
        val media = runCatching { if (DocumentsContract.isDocumentUri(ctx, uri)) MediaStore.getMediaUri(ctx, uri) else uri }.getOrNull()
        if (media != null && media.authority == MediaStore.AUTHORITY)
            return runCatching { DeleteNeedsConfirmation(MediaStore.createDeleteRequest(cr, listOf(media)).intentSender) }.getOrElse { DeleteImpossible }
    }
    // 3) Older Android, own media: plain delete.
    if (runCatching { cr.delete(uri, null, null) > 0 }.getOrDefault(false)) return null
    return DeleteImpossible
}
