package castbridge.receiver

import android.app.Activity
import android.app.AlertDialog
import android.widget.Toast
import castbridge.core.content.ContentFeedback
import castbridge.core.content.ReportQueue
import castbridge.core.content.ReportReason
import castbridge.core.learn.Exercise
import castbridge.core.learn.Lesson
import castbridge.core.learn.Pack

/**
 * « Signaler une erreur » on the TV (docs/CONTENT-VALIDATION.md): the list of reasons, usable with the remote, then [send] runs with the
 * chosen reason and the sentence it returns is shown. Reports are queued on the TV (offline first) and leave with the next server contact.
 */
fun Activity.askReportReason(send: (ReportReason) -> String) {
    val reasons = ContentFeedback.REASONS
    AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
        .setTitle("Signaler une erreur : pourquoi ?")
        .setItems(reasons.map { it.label }.toTypedArray()) { _, i -> Toast.makeText(this, send(reasons[i]), Toast.LENGTH_LONG).show() }
        .setNegativeButton("Annuler", null).show()
}

/** Reports of « Apprendre » content from the TV screens (the quiz reports go through QuizRoom). */
object ContentReports {
    private fun say(r: ReportQueue.Add?): String = TvConnect.feedback?.message(r ?: ReportQueue.Add.INVALID) ?: "Signalement impossible."
    fun exercise(pack: Pack, x: Exercise, r: ReportReason) = say(TvConnect.feedback?.reportExercise(pack, x, r, null))
    fun lesson(pack: Pack, l: Lesson, r: ReportReason) = say(TvConnect.feedback?.reportLesson(pack, l, r, null))
}
