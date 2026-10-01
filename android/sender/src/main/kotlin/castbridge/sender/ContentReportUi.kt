package castbridge.sender

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import castbridge.core.content.ContentReport
import castbridge.core.content.ContentFeedback
import castbridge.core.content.ReportQueue
import castbridge.core.content.ReportReason
import castbridge.core.learn.Exercise
import castbridge.core.learn.Lesson
import castbridge.core.learn.Pack

/**
 * « Signaler une erreur » (docs/CONTENT-VALIDATION.md): a reason from the list and an optional short text; [send] stores the report on
 * this phone (offline first, uploaded later) and returns the sentence shown to the user. No personal data: ids, reason and the text.
 */
@Composable
fun ReportErrorButton(modifier: Modifier = Modifier, send: (ReportReason, String) -> String) {
    var open by remember { mutableStateOf(false) }
    var reason by remember { mutableStateOf<ReportReason?>(null) }
    var note by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    TextButton(onClick = { open = true; reason = null; note = "" }, modifier) { Text("Signaler une erreur") }
    if (open) AlertDialog(
        onDismissRequest = { open = false },
        title = { Text("Signaler une erreur : pourquoi ?") },
        text = {
            Column {
                ContentFeedback.REASONS.forEach { r -> TextButton(onClick = { reason = r }) { Text((if (reason == r) "● " else "○ ") + r.label) } }
                OutlinedTextField(note, { note = it.take(ContentReport.MAX_NOTE) }, label = { Text("Précision (facultatif)") }, supportingText = { Text(ContentFeedback.HINT) })
            }
        },
        confirmButton = { TextButton(enabled = reason != null, onClick = { message = send(reason!!, note); open = false }) { Text("Envoyer") } },
        dismissButton = { TextButton(onClick = { open = false }) { Text("Annuler") } },
    )
    message?.let { m -> AlertDialog(onDismissRequest = { message = null }, confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } }, text = { Text(m, style = MaterialTheme.typography.bodyMedium) }) }
}

/** Reports of « Apprendre » content from the phone screens. */
object PhoneReports {
    private fun say(r: ReportQueue.Add?): String = PhoneConnect.feedback?.message(r ?: ReportQueue.Add.INVALID) ?: "Signalement impossible."
    fun exercise(pack: Pack, x: Exercise, r: ReportReason, note: String) = say(PhoneConnect.feedback?.reportExercise(pack, x, r, note))
    fun lesson(pack: Pack, l: Lesson, r: ReportReason, note: String) = say(PhoneConnect.feedback?.reportLesson(pack, l, r, note))
}
