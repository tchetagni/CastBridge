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
    LaunchedEffect(req) {
        val r = req ?: return@LaunchedEffect
        when (val out = deleteFromPhone(ctx, r.uri)) {
            null -> { Toast.makeText(ctx, "« ${r.name} » déplacé vers la TV", Toast.LENGTH_LONG).show(); UploadService.moveHandled() }
            is DeleteNeedsConfirmation -> confirm.launch(IntentSenderRequest.Builder(out.sender).build())
            is DeleteImpossible -> {
                Toast.makeText(ctx, "« ${r.name} » est sur la TV, mais le téléphone ne permet pas de le supprimer d'ici : supprimez-le depuis la Galerie.", Toast.LENGTH_LONG).show()
                UploadService.moveHandled()
            }
        }
    }
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
