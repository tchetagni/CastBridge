package castbridge.sender

import android.content.Context
import castbridge.core.trust.CopyStep
import castbridge.core.trust.LinkRefusalTexts
import castbridge.core.tv.QueueItem

/**
 * Le message d'un envoi que la TV a refusé (code 8 : « la TV ne reconnaît plus ce téléphone »…) : la cause et l'action, gardées dans « Dernières copies »
 * et dites par la notification de [CopyReport] (toucher rouvre « Ouvrir avec CastBridge » : le refus mémorisé y met le champ du code PIN). Aucun PIN dans le texte.
 */
object RefusalNotice {
    fun show(ctx: Context, item: QueueItem, code: Int) {
        CopyReport.failed(ctx, item, CopyStep.CONNECT, null, text = LinkRefusalTexts.ticket(code), cause = "TV_REFUSED_$code")
    }
}
