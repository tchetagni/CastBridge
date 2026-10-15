package castbridge.receiver

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import castbridge.core.tv.NotificationAccessPolicy

/**
 * La moitié Android de R-52 (« les copies ne signalent plus en notification ») : la règle est pure ([NotificationAccessPolicy], testée en JVM), ici on lui donne l'état réel d'Android
 * et on exécute ce qu'elle dit. Même forme que [BatteryExemption] : une ligne du MENU, conditionnelle, dont l'action est décidée à l'appui.
 *
 * Android ne dit pas « le dialogue ne reviendra plus » : `shouldShowRequestPermissionRationale` est faux avant la première demande ET après le refus définitif. D'où [DENIED] : chaque
 * réponse « refusé » d'Android à POST_NOTIFICATIONS est retenue, qu'elle vienne du dialogue GROUPÉ du lancement (Bluetooth + notifications, [PlayerActivity.requestRuntimePermissions]) ou de la ligne.
 */
object NotificationAccess {
    /** Préférence : Android a déjà répondu « refusé » à POST_NOTIFICATIONS pour cette app. Jamais effacée (un refus définitif ne se « défait » que dans le réglage de l'app). */
    private const val DENIED = "notif_denied"

    /** À appeler pour toute réponse à une demande d'autorisations (tous codes) : retient un « refusé » pour POST_NOTIFICATIONS. */
    fun noteAnswer(prefs: TvPrefs, permissions: Array<out String>, grantResults: IntArray) {
        val i = permissions.indexOf(Manifest.permission.POST_NOTIFICATIONS)
        if (i >= 0 && grantResults.getOrNull(i) == PackageManager.PERMISSION_DENIED) prefs.putBool(DENIED, true)
    }

    /** Ce que l'appui sur la ligne « Notifications » doit faire maintenant : la règle pure, nourrie de l'état réel d'Android et de la mémoire du refus. */
    fun action(activity: Activity, prefs: TvPrefs, enabled: Boolean): NotificationAccessPolicy.Action {
        val sdk = Build.VERSION.SDK_INT
        val rationale = sdk >= 33 && activity.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
        return NotificationAccessPolicy.action(sdk, enabled, NotificationAccessPolicy.canAskAgain(rationale, prefs.getBool(DENIED, false)))
    }

    /**
     * Ouvre le réglage des notifications de CastBridge-TV, sinon (boîtiers qui n'ont pas cet écran) les détails de l'app. False = aucun des deux n'existe sur ce boîtier : l'appelant le dit.
     * Jamais d'exception : une TV sans écran de réglage ne doit pas planter.
     */
    fun openSettings(activity: Activity): Boolean {
        val pkg = activity.packageName
        val tries = listOf(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")),
        )
        return tries.any { runCatching { activity.startActivity(it) }.isSuccess }
    }
}
