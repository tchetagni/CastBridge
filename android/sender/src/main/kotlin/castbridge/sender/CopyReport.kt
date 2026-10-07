package castbridge.sender

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import castbridge.core.trust.CopyEntry
import castbridge.core.trust.CopyJournal
import castbridge.core.trust.CopyStep
import castbridge.core.trust.UploadFailure
import castbridge.core.tv.QueueItem

/**
 * Le résultat de chaque copie vers la TV, dit et gardé : le texte de la cause et de l'action ([UploadFailure]), la notification (un toucher rouvre
 * « Ouvrir avec CastBridge » sur le même fichier), la fiche de la file, le petit journal interne et « Dernières copies » ([CopyJournal]).
 * Jamais de code PIN, de jeton ni de chemin dans ce qui est gardé ou affiché.
 */
object CopyReport {
    private const val CHANNEL = "queue-refused"
    private const val NOTIF = 12
    @Volatile private var journal0: CopyJournal? = null

    fun journal(ctx: Context): CopyJournal = journal0 ?: synchronized(this) {
        journal0 ?: run {
            val sp = ctx.applicationContext.getSharedPreferences("castbridge_copy_journal", Context.MODE_PRIVATE)
            CopyJournal(PrefsPersistence(sp, "ring"), PrefsPersistence(sp, "history")).also { journal0 = it }
        }
    }

    /** Une ligne de l'anneau : où en est la copie (étape, avancement). */
    fun step(ctx: Context, step: CopyStep, event: String) { runCatching { journal(ctx).log(step, event) } }

    /** Une copie a échoué : texte de la cause (gardé dans la fiche), notification, journal. Rend le texte. [e] ou [reason] disent pourquoi. */
    fun failed(ctx: Context, item: QueueItem, step: CopyStep, percent: Int?, e: Throwable? = null, reason: String? = null, text: String? = null, cause: String? = null, notify: Boolean = true): String {
        val info = e?.let { UploadFailure.ofException(it) } ?: UploadFailure.ofReason(reason ?: "")
        val shown = text ?: UploadFailure.message(info, percent)
        runCatching {
            val j = journal(ctx.applicationContext)
            j.log(step, "échec ${cause ?: info.cause} : ${info.technical}" + if (percent != null) " à $percent %" else "")
            j.record(CopyEntry(System.currentTimeMillis(), item.name, false, cause ?: info.cause.name, step.label, percent ?: 0, shown))
        }
        // R-22: a file that can no longer be read joins ONE grouped notification (« 5 fichiers à repartager »), never one per file
        if (notify) { if (cause == "FILE_UNREADABLE" || info.cause == UploadFailure.Cause.FILE_UNREADABLE) reshareNotice(ctx, item) else notify(ctx, item, shown) }
        return shown
    }

    /** Une copie est partie ou n'a pas eu à partir (déjà sur la TV) : gardée dans « Dernières copies ». */
    fun succeeded(ctx: Context, item: QueueItem, note: String? = null) {
        runCatching {
            val j = journal(ctx.applicationContext)
            j.log(CopyStep.VERIFY, "terminée" + if (note != null) " : $note" else "")
            j.record(CopyEntry(System.currentTimeMillis(), item.name, true, "OK", CopyStep.VERIFY.label, 100, note ?: "Copie terminée"))
        }
        runCatching { ctx.getSystemService(NotificationManager::class.java)?.cancel(NOTIF) }
    }

    private const val NOTIF_RESHARE = 13

    /**
     * R-22: UNE seule notification pour tous les fichiers à repartager (même identifiant : elle se met à jour au lieu de se multiplier). [extra] = un fichier
     * qui vient de l'être et que la file ne marque pas encore. Toucher ouvre le choix des fichiers ([ReselectActivity], droit persistant).
     */
    fun reshareNotice(ctx: Context, extra: QueueItem?) {
        runCatching {
            val names = (TransferQueue.toReshare() + listOfNotNull(extra)).distinctBy { it.id }.map { it.name }
            if (names.isEmpty()) return
            val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Envois refusés par la TV", NotificationManager.IMPORTANCE_DEFAULT))
            val tap = PendingIntent.getActivity(ctx, NOTIF_RESHARE, Intent(ctx, ReselectActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val text = castbridge.core.tv.ReshareTexts.groupText(names)
            nm.notify(NOTIF_RESHARE, Notification.Builder(ctx, CHANNEL).setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle(castbridge.core.tv.ReshareTexts.groupTitle(names)).setContentText(text).setStyle(Notification.BigTextStyle().bigText(text))
                .setContentIntent(tap).setAutoCancel(true).build())
        }
    }

    /** La notification d'échec : la cause et l'action ; toucher rouvre « Ouvrir avec CastBridge » sur le même fichier. */
    fun notify(ctx: Context, item: QueueItem, text: String) {
        runCatching {
            val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Envois refusés par la TV", NotificationManager.IMPORTANCE_DEFAULT))
            val uri = Uri.parse(item.source)
            val reopen = Intent(ctx, OpenWithActivity::class.java).setAction(Intent.ACTION_VIEW).setDataAndType(uri, ctx.contentResolver.getType(uri))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val tap = PendingIntent.getActivity(ctx, NOTIF, reopen, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            nm.notify(NOTIF, Notification.Builder(ctx, CHANNEL).setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle("CastBridge : « ${item.name} » n'est pas parti")
                .setContentText(text).setStyle(Notification.BigTextStyle().bigText(text))
                .setContentIntent(tap).setAutoCancel(true).build())
        }
    }
}
