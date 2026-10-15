package castbridge.core.ux

import castbridge.core.PhoneSources
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Garde de source du branchement Android de R-52 côté CastBridge (téléphone) : la règle pure ([NotificationHint], [castbridge.core.tv.NotificationAccessPolicy]) est testée ailleurs ;
 * l'assemblage Android n'a aucun test JVM. Ici : le texte et le bouton « Autoriser » sont bien posés EN TÊTE de la carte d'envoi en cours et de la file d'attente de l'accueil,
 * l'état est relu au retour des réglages, et les canaux surveillés sont ceux que les services créent (même identifiant, même nom). Un fichier absent fait ÉCHOUER le test.
 */
class NotificationHintSourceTest {
    private val dir = "sender/src/main/kotlin/castbridge/sender"
    private val home by lazy { PhoneSources.code("$dir/TvHome.kt") }
    private val ui by lazy { PhoneSources.code("$dir/NotificationHintUi.kt") }

    private fun between(code: String, from: String, until: String): String {
        val a = code.indexOf(from); assertTrue(a >= 0, "« $from » introuvable")
        val b = code.indexOf(until, a + from.length); assertTrue(b > a, "« $until » introuvable après « $from »")
        return code.substring(a, b)
    }

    @Test fun theHintHeadsTheRunningSendCardAndTheQueueCardNeverTwiceOnTheSameScreen() {
        val send = between(home, "AnimatedVisibility(sendCardShown", "(u as? UploadService.State.Done)")
        val firstChild = send.substringAfter("Column(Modifier.padding(18.dp)").substringAfter("\n").trimStart()
        assertTrue(firstChild.startsWith("NotificationHintRow()"), "carte « Envoi vers la TV » : le texte et « Autoriser » sont le PREMIER élément, avant le titre")
        assertTrue(Regex("""val sendCardShown = u is UploadService\.State\.Uploading \|\| u is UploadService\.State\.Waiting \|\| btActive""").containsMatchIn(home),
            "la carte d'envoi garde sa condition d'affichage, nommée une fois")
        val queue = home.substring(home.indexOf("fun TransferQueueCard(").also { assertTrue(it >= 0, "TransferQueueCard introuvable") })
        assertTrue("fun TransferQueueCard(showNotificationHint: Boolean = true)" in queue,
            "la file peut céder la place à la carte d'envoi (même texte deux fois sur un écran) et reste complète ailleurs (TvTransferScreen)")
        val at = queue.indexOf("if (showNotificationHint) NotificationHintRow()")
        assertTrue(at >= 0, "file d'attente : le texte y est posé")
        assertTrue(at < queue.indexOf("File d'attente des envois"), "…en tête, avant le titre de la carte")
        assertTrue("TransferQueueCard(showNotificationHint = !sendCardShown)" in home, "l'accueil n'affiche le texte qu'une fois : sur la carte d'envoi quand elle est là, sinon sur la file")
        assertEquals(2, Regex("NotificationHintRow\\(\\)").findAll(home).count(), "deux emplacements dans TvHome : la carte d'envoi et la file")
    }

    @Test fun theHintIsReadFromTheRealStateAndDrawnAsTheRulesays() {
        assertTrue("NotificationManagerCompat.from(ctx)" in ui && "nm.areNotificationsEnabled()" in ui, "l'état de l'app vient du système (NotificationManagerCompat.areNotificationsEnabled)")
        assertTrue("NotificationHint.COPY_CHANNELS" in ui && "IMPORTANCE_NONE" in ui && "getNotificationChannel(" in ui, "les canaux de copie coupés un à un sont lus")
        assertTrue("NotificationHint.text(" in ui, "le texte vient de la règle pure")
        assertTrue("NotificationHint.BUTTON" in ui, "le bouton est « Autoriser »")
        assertTrue("MaterialTheme.colorScheme.error" in ui && "MaterialTheme.typography.bodySmall" in ui, "texte rouge (error), bodySmall")
        assertTrue("TextButton(" in ui, "un TextButton")
    }

    @Test fun theButtonAsksWhileAndroidWillShowTheDialogElseOpensTheAppSettings() {
        assertTrue("NotificationHint.buttonAction(" in ui, "la décision du bouton vient de la règle pure (la même que sur la TV)")
        assertTrue("shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)" in ui && "Build.VERSION.SDK_INT" in ui, "l'état réel d'Android nourrit la règle")
        assertTrue("rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission())" in ui, "la demande passe par le contrat Android")
        assertTrue("noteDenied(" in ui && "if (!granted)" in ui, "un refus du dialogue est retenu : c'est ce qui dit qu'il ne reviendra plus")
        val settings = ui.indexOf("ACTION_APP_NOTIFICATION_SETTINGS"); val details = ui.indexOf("ACTION_APPLICATION_DETAILS_SETTINGS")
        assertTrue(settings >= 0 && details > settings && "EXTRA_APP_PACKAGE" in ui, "réglage des notifications de l'app (EXTRA_APP_PACKAGE), repli : détails de l'app")
    }

    @Test fun theDlnaTabRequestRecordsItsRefusalToo() {
        val main = PhoneSources.code("$dir/MainActivity.kt")
        assertTrue("Manifest.permission.POST_NOTIFICATIONS" in main, "la demande de l'onglet « TV DLNA » existe toujours")
        assertTrue(Regex("""rememberLauncherForActivityResult\(ActivityResultContracts\.RequestPermission\(\)\) \{ granted -> if \(!granted\) PhoneNotificationState\.noteDenied\(this@MainActivity\) \}""").containsMatchIn(main),
            "son refus est retenu (sinon « Autoriser » croirait le dialogue encore possible et le premier appui serait perdu)")
    }

    @Test fun theStateIsReadAgainWhenTheUserComesBackFromTheSettings() {
        assertTrue("LifecycleEventObserver" in ui && "Lifecycle.Event.ON_RESUME" in ui, "recomposition au retour (ON_RESUME)")
        assertTrue("remember(tick)" in ui || "remember(" in ui, "l'état est relu à chaque retour")
        assertTrue("removeObserver" in ui, "l'observateur est retiré")
    }

    @Test fun theWatchedChannelsAreTheOnesTheServicesCreate() {
        val services = mapOf("upload" to "UploadService.kt", "queue" to "TransferQueueService.kt", "btupload" to "BtUploadService.kt")
        assertEquals(services.keys, NotificationHint.COPY_CHANNELS.map { it.first }.toSet(), "un service par canal surveillé")
        for ((id, name) in NotificationHint.COPY_CHANNELS) {
            val code = PhoneSources.code("$dir/${services.getValue(id)}")
            assertTrue("""private const val CHANNEL = "$id"""" in code, "${services[id]} crée le canal « $id »")
            assertTrue("""NotificationChannel(CHANNEL, "$name",""" in code, "${services[id]} nomme le canal « $name » comme le texte du téléphone")
        }
    }
}
