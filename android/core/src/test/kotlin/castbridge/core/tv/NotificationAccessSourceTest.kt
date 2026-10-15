package castbridge.core.tv

import castbridge.core.PhoneSources
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Garde de source du branchement Android de R-52 (« les copies ne signalent plus en notification ») côté CastBridge-TV. La règle pure est testée par [NotificationAccessPolicyTest] ;
 * l'assemblage Android n'a aucun test JVM, donc une règle juste peut rester débranchée et la suite rester verte (c'est ce que l'audit du 2026-10-07 a relevé). Ici : les écrans
 * appellent bien la règle. Un fichier absent fait ÉCHOUER le test (pas de vert à vide) ; les commentaires ne comptent pas (une histoire racontée n'est pas un appel).
 */
class NotificationAccessSourceTest {
    private val dir = "receiver/src/main/kotlin/castbridge/receiver"
    private val player by lazy { PhoneSources.code("$dir/PlayerActivity.kt") }
    private val service by lazy { PhoneSources.code("$dir/TvService.kt") }
    private val parental by lazy { PhoneSources.code("$dir/ParentalHub.kt") }
    private val access by lazy { PhoneSources.code("$dir/NotificationAccess.kt") }

    /** Le texte entre le début de [from] et le début de [until] (texte brut, sans compter les accolades). */
    private fun between(code: String, from: String, until: String): String {
        val a = code.indexOf(from); assertTrue(a >= 0, "« $from » introuvable")
        val b = code.indexOf(until, a + from.length); assertTrue(b > a, "« $until » introuvable après « $from »")
        return code.substring(a, b)
    }

    // ---- la ligne du MENU ----

    @Test fun theMenuHasTheNotificationLineOnlyWhileNotificationsAreOff() {
        val menu = between(player, "private fun menuItems()", "fun openDevSettings(")
        assertTrue(Regex("""if \(!s\.notificationsAllowed\(\)\)\s*items \+= (?:castbridge\.core\.tv\.)?NotificationAccessPolicy\.LINE to \{ fixNotifications\(\) \}""").containsMatchIn(menu),
            "MENU : la ligne n'existe que si TvService.notificationsAllowed() est faux, et son action est fixNotifications()")
        assertEquals(1, Regex("NotificationAccessPolicy\\.LINE").findAll(menu).count(), "une seule ligne")
    }

    @Test fun thePressDecidesAtPressTimeThroughThePureRuleAndDoesWhatItSays() {
        val fix = between(player, "private fun fixNotifications()", "private fun openNotificationSettings()")
        assertTrue("NotificationAccess.action(" in fix, "l'action vient de la règle pure, alimentée par l'état réel (rationale, refus déjà reçu), décidée à l'appui et non à la construction de la liste")
        assertTrue(Regex("""Action\.ASK\s*->\s*runCatching \{ requestPermissions\(arrayOf\(Manifest\.permission\.POST_NOTIFICATIONS\), REQ_NOTIF\) \}""").containsMatchIn(fix),
            "ASK : la demande de POST_NOTIFICATIONS SEULE (code REQ_NOTIF), pas groupée avec le Bluetooth")
        assertTrue(Regex("""Action\.OPEN_SETTINGS\s*->\s*openNotificationSettings\(\)""").containsMatchIn(fix), "OPEN_SETTINGS : le réglage de l'app")
        assertTrue("Action.NONE" in fix, "le cas « rien à faire » est traité (jamais d'appui muet)")
        val open = between(player, "private fun openNotificationSettings()", "private fun showMenu()")
        assertTrue("NotificationAccess.openSettings(this)" in open && "NotificationAccessPolicy.SETTINGS_UNAVAILABLE" in open && "flash(" in open,
            "aucun écran de réglage sur la TV : le dit, ne plante pas")
    }

    @Test fun theSettingsAreOpenedByTheAppNotificationScreenThenTheAppDetails() {
        val open = between(access, "fun openSettings(", "\n    }")
        val a = open.indexOf("ACTION_APP_NOTIFICATION_SETTINGS"); val b = open.indexOf("ACTION_APPLICATION_DETAILS_SETTINGS")
        assertTrue(a >= 0 && b > a, "d'abord l'écran des notifications de l'app, puis (GaiaOS) les détails de l'app")
        assertTrue("EXTRA_APP_PACKAGE" in open, "l'écran des notifications de L'APP (pas la liste de toutes)")
        assertTrue("runCatching" in open, "un boîtier sans cet écran ne fait pas planter la TV")
    }

    // ---- le résultat de la demande ----

    @Test fun theAnswerOfTheDialogIsRememberedSaidAndTheMenuRefreshed() {
        val result = between(player, "override fun onRequestPermissionsResult(", "private fun toggleWifiDirect(")
        assertTrue("NotificationAccess.noteAnswer(TvPrefs(this), permissions, grantResults)" in result,
            "toute réponse à POST_NOTIFICATIONS est retenue (dialogue groupé du lancement ou ligne du MENU) : c'est ce qui dit que le dialogue ne reviendra plus")
        val notif = between(result, "REQ_NOTIF", "REQ_WD")
        assertTrue("NotificationAccessPolicy.GRANTED" in notif && "NotificationAccessPolicy.REFUSED" in notif, "« Notifications autorisées » / « Notifications refusées : MENU > Notifications… »")
        assertTrue("settingsPanel?.visible == true" in notif && "showSettings()" in notif, "la ligne disparaît du panneau ouvert quand c'est accordé")
        val codes = Regex("""private const val (REQ_\w+) = (\d+)""").findAll(player).map { it.groupValues[2] }.toList()
        assertEquals(codes.size, codes.toSet().size, "chaque code de demande est distinct : $codes")
        assertTrue(Regex("""private const val REQ_NOTIF = \d+""").containsMatchIn(player), "REQ_NOTIF existe")
    }

    @Test fun theLaunchDialogStillAsksNotificationsWithTheBluetoothGroupAndAnswersAreRecorded() {
        val req = between(player, "private fun requestRuntimePermissions()", "override fun onRequestPermissionsResult(")
        assertTrue("Manifest.permission.POST_NOTIFICATIONS" in req && "REQ_PERMS" in req, "le dialogue groupé du lancement est conservé")
        val helper = between(access, "fun noteAnswer(", "\n    }")
        assertTrue("POST_NOTIFICATIONS" in helper && "PERMISSION_DENIED" in helper && "putBool(" in helper, "un refus d'Android est retenu dans les préférences de la TV")
    }

    @Test fun theRuleIsFedWithTheRealRationaleAndTheRememberedRefusal() {
        val action = between(access, "fun action(", "\n    }")
        assertTrue("shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)" in action, "la vraie réponse d'Android sur « on peut redemander »")
        assertTrue("NotificationAccessPolicy.canAskAgain(" in action && "NotificationAccessPolicy.action(" in action, "les deux décisions viennent de la règle pure")
        assertTrue("Build.VERSION.SDK_INT" in action, "le niveau d'Android vient du système")
    }

    // ---- le contrôle parental ----

    @Test fun theParentalFilterClassesTheLineWithTheSettings() {
        val label = between(parental, "fun categoryOfLabel(", "val lifecycle = object")
        val at = label.indexOf("startsWith(\"notifications\")")
        assertTrue(at >= 0, "categoryOfLabel : un libellé « Notifications… » est reconnu")
        assertTrue(label.indexOf("Category.SETTINGS", at) > at, "…et rangé avec Category.SETTINGS")
        assertTrue(label.indexOf("Category.SETTINGS", at) < label.indexOf("else -> null"), "…avant le cas par défaut")
    }

    // ---- l'écran dit les copies, même sans notification ----

    @Test fun theFirstEventOfATransferIsAnnouncedOnScreenNeverOverAVideoAndTheSilenceIsSaidOnce() {
        val notifier = between(service, "private val receptionNotifier", "private fun showReception(")
        assertTrue("announceOnScreen(item)" in notifier && "showReception(item)" in notifier, "chaque événement de réception : la notification ET l'annonce à l'écran")
        val announce = between(service, "private fun announceOnScreen(", "private fun canUseFullScreen(")
        assertTrue("NotificationAccessPolicy.firstAnnouncement(item.seq, announcedOnScreen)" in announce, "une fois par transfert (seq stable, reprises comprises)")
        assertTrue("notice(item.screenLine())" in announce, "le bandeau « ⬇ Réception de … »")
        assertTrue("playerBridge.state().state" in announce && "\"playing\"" in announce && "\"buffering\"" in announce, "jamais par-dessus une vidéo qui joue")
        assertTrue("notificationsAllowed()" in announce && "NotificationAccessPolicy.OFF_NOTICE" in announce, "notifications coupées : le bandeau d'avertissement")
        assertTrue(announce.indexOf("firstAnnouncement") < announce.indexOf("playerBridge.state()"),
            "le transfert est noté annoncé AVANT de regarder la lecture (un transfert commencé pendant une vidéo ne s'annonce pas plus tard)")
        assertFalse("var notificationsOffWarned" in between(service, "class TvService", "companion object"),
            "l'avertissement vaut une fois par PROCESSUS : son état est dans le companion, pas dans l'instance du service")
        assertTrue(Regex("""@Volatile private var notificationsOffWarned""").containsMatchIn(service.substringAfter("companion object")), "…dans le companion")
    }

    @Test fun theServiceStillTellsTheRealStateOfNotificationsToTheApi() {
        assertTrue(Regex("""\n    fun notificationsAllowed\(\): Boolean""").containsMatchIn(service), "notificationsAllowed() est lisible par l'écran (ligne du MENU)")
        assertTrue("\"notifications\":\${notificationsAllowed()}" in service, "GET /api/background publie toujours l'état réel des notifications")
    }
}
