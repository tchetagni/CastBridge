package castbridge.sender

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import castbridge.core.trust.LinkRefusalTexts
import castbridge.core.tv.QueueItem

/**
 * Le message d'un envoi que la TV a refusé (code 8 : « la TV ne reconnaît plus ce téléphone »…) : une notification qui dit la cause, jamais seulement un Toast.
 * Toucher la notification rouvre « Ouvrir avec CastBridge » sur le même fichier ; le refus mémorisé ([TvLinkManager.refusals]) y met le champ du code PIN
 * (PinEntryBox) à la place de la file d'attente. Aucun code PIN ni jeton dans le texte.
 */
object RefusalNotice {
    private const val CHANNEL = "queue-refused"
    private const val NOTIF = 12

    fun show(ctx: Context, item: QueueItem, code: Int) {
        runCatching {
            val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Envois refusés par la TV", NotificationManager.IMPORTANCE_DEFAULT))
            val uri = Uri.parse(item.uri)
            val reopen = Intent(ctx, OpenWithActivity::class.java).setAction(Intent.ACTION_VIEW).setDataAndType(uri, ctx.contentResolver.getType(uri))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val tap = PendingIntent.getActivity(ctx, NOTIF, reopen, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val text = LinkRefusalTexts.ticket(code)
            nm.notify(NOTIF, Notification.Builder(ctx, CHANNEL).setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle("CastBridge : « ${item.name} » n'est pas parti")
                .setContentText(text).setStyle(Notification.BigTextStyle().bigText("$text\n${LinkRefusalTexts.banner(code)}"))
                .setContentIntent(tap).setAutoCancel(true).build())
        }
    }
}
