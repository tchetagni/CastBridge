package castbridge.sender

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

/**
 * L'UNE notification de l'activation (ACT-NF2) : « TV activée ». Identifiant fixe : une seconde publication remplace la première, il n'y en a jamais deux. Le texte est celui de
 * `KeyAcquisition.noticeText` (le nom de la TV, jamais le code, la clé ni la demande). Sans l'autorisation d'Android 13+ de publier, rien n'est publié : l'écran dit déjà le résultat.
 */
object ActivationNotice {
    private const val CHANNEL = "activation"
    private const val NOTIF = 77

    fun post(ctx: Context, title: String, text: String) {
        runCatching {
            if (Build.VERSION.SDK_INT >= 33 && ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Activation de la TV", NotificationManager.IMPORTANCE_DEFAULT))
            val open = PendingIntent.getActivity(ctx, 77, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            nm.notify(NOTIF, Notification.Builder(ctx, CHANNEL).setSmallIcon(R.drawable.ic_stat_castbridge).setContentTitle(title).setContentText(text).setAutoCancel(true).setContentIntent(open).build())
        }
    }
}
