package castbridge.core.tv

import castbridge.core.tv.NotificationAccessPolicy.Action
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * R-52 (2026-10-14, « les copies ne signalent plus en notification ») : depuis Android 13 la permission POST_NOTIFICATIONS est à accorder, et refusée elle fait jeter EN SILENCE
 * toute notification de l'app (constat ADB sur le S21+ : `importance=NONE`, `POST_NOTIFICATIONS granted=false`). Android ne remontre le dialogue qu'une dernière fois après un
 * refus, puis plus jamais : seul le réglage de l'app peut alors la rendre. Ces tests figent la règle pure qui dit quoi proposer sur la TV (la ligne du MENU), quand redemander et
 * quand ouvrir le réglage, et la règle « un transfert n'est annoncé à l'écran qu'une fois ». L'écran Android ne fait que l'exécuter (garde de source : `NotificationAccessSourceTest`).
 */
class NotificationAccessPolicyTest {
    private val androidThirteenAndAfter = listOf(33, 34, 35)
    private val androidEightToTwelve = listOf(26, 28, 29, 31, 32)
    private val beforeAndroidEight = listOf(19, 21, 24, 25)

    // ---- les mots ----

    @Test fun theMenuLineAndTheTexts() {
        assertEquals("Notifications : désactivées sur cette TV — autoriser (copies reçues, demandes du téléphone)", NotificationAccessPolicy.LINE)
        assertEquals("Notifications autorisées", NotificationAccessPolicy.GRANTED)
        assertEquals("Notifications refusées : MENU > Notifications pour les autoriser plus tard", NotificationAccessPolicy.REFUSED)
        assertEquals("Réglage indisponible sur cette TV", NotificationAccessPolicy.SETTINGS_UNAVAILABLE)
        assertEquals("Notifications désactivées : les copies ne s'affichent qu'à l'écran (MENU > Notifications)", NotificationAccessPolicy.OFF_NOTICE)
        // la ligne commence par le mot que le contrôle parental lit (ParentalHub.categoryOfLabel) et que le bandeau cite (« MENU > Notifications »)
        assertTrue(NotificationAccessPolicy.LINE.lowercase().startsWith("notifications"))
        assertTrue("MENU > Notifications" in NotificationAccessPolicy.OFF_NOTICE && "MENU > Notifications" in NotificationAccessPolicy.REFUSED)
        for (t in listOf(NotificationAccessPolicy.LINE, NotificationAccessPolicy.GRANTED, NotificationAccessPolicy.REFUSED,
            NotificationAccessPolicy.SETTINGS_UNAVAILABLE, NotificationAccessPolicy.OFF_NOTICE))
            assertFalse(Regex("(?i)sender|receiver").containsMatchIn(t), "les apps s'appellent CastBridge et CastBridge-TV : $t")
    }

    // ---- quoi faire quand les notifications sont coupées ----

    @Test fun nothingToDoWhileNotificationsAreOn() {
        for (sdk in beforeAndroidEight + androidEightToTwelve + androidThirteenAndAfter) for (canAsk in listOf(false, true))
            assertEquals(Action.NONE, NotificationAccessPolicy.action(sdk, enabled = true, canAskAgain = canAsk), "sdk=$sdk canAskAgain=$canAsk")
    }

    @Test fun androidThirteenAsksAgainWhileAndroidStillShowsTheDialog() {
        for (sdk in androidThirteenAndAfter)
            assertEquals(Action.ASK, NotificationAccessPolicy.action(sdk, enabled = false, canAskAgain = true), "sdk=$sdk")
    }

    @Test fun androidThirteenOpensTheAppSettingsOnceAndroidStoppedAsking() {
        for (sdk in androidThirteenAndAfter)
            assertEquals(Action.OPEN_SETTINGS, NotificationAccessPolicy.action(sdk, enabled = false, canAskAgain = false), "sdk=$sdk")
    }

    @Test fun beforeAndroidThirteenThereIsNoDialogOnlyTheSettings() {
        for (sdk in androidEightToTwelve) for (canAsk in listOf(false, true))
            assertEquals(Action.OPEN_SETTINGS, NotificationAccessPolicy.action(sdk, enabled = false, canAskAgain = canAsk), "sdk=$sdk canAskAgain=$canAsk")
    }

    @Test fun beforeAndroidEightNothingIsProposed() {
        for (sdk in beforeAndroidEight) for (canAsk in listOf(false, true))
            assertEquals(Action.NONE, NotificationAccessPolicy.action(sdk, enabled = false, canAskAgain = canAsk), "sdk=$sdk canAskAgain=$canAsk")
    }

    @Test fun androidCanBeAskedAgainWhenItSaysSoOrWhenItNeverAnsweredRefused() {
        assertTrue(NotificationAccessPolicy.canAskAgain(rationale = false, deniedBefore = false), "jamais refusé : le dialogue peut encore venir")
        assertTrue(NotificationAccessPolicy.canAskAgain(rationale = true, deniedBefore = true), "refusé une fois : Android le remontre une dernière fois")
        assertTrue(NotificationAccessPolicy.canAskAgain(rationale = true, deniedBefore = false))
        assertFalse(NotificationAccessPolicy.canAskAgain(rationale = false, deniedBefore = true),
            "refusé deux fois (ou RETOUR deux fois) : Android ne montre plus rien, une demande ne servirait à rien, seul le réglage de l'app peut les rendre")
    }

    @Test fun theWholeStoryOfARefusedPermissionEndsInTheAppSettings() {
        // jamais demandé → on demande ; refusé une fois → on redemande ; refusé deux fois → le réglage de l'app (le « dialogue mort » n'est plus proposé)
        fun next(rationale: Boolean, denied: Boolean) = NotificationAccessPolicy.action(34, enabled = false, canAskAgain = NotificationAccessPolicy.canAskAgain(rationale, denied))
        assertEquals(Action.ASK, next(rationale = false, denied = false))
        assertEquals(Action.ASK, next(rationale = true, denied = true))
        assertEquals(Action.OPEN_SETTINGS, next(rationale = false, denied = true))
    }

    // ---- un transfert n'est annoncé à l'écran qu'une fois ----

    @Test fun aTransferIsAnnouncedOnceWhateverTheNumberOfItsEvents() {
        val seen = mutableSetOf<Int>()
        assertTrue(NotificationAccessPolicy.firstAnnouncement(7, seen), "premier événement du transfert 7")
        repeat(20) { assertFalse(NotificationAccessPolicy.firstAnnouncement(7, seen), "événement ${it + 2} du transfert 7 : déjà annoncé") }
        assertTrue(NotificationAccessPolicy.firstAnnouncement(8, seen), "un autre transfert est annoncé à son tour")
        assertFalse(NotificationAccessPolicy.firstAnnouncement(8, seen))
        assertFalse(NotificationAccessPolicy.firstAnnouncement(7, seen), "le 7 reprend après une coupure (même numéro) : pas de nouvelle annonce")
    }

    @Test fun theMemoryOfAnnouncementsIsBoundedAndKeepsTheRecentTransfers() {
        val seen = LinkedHashSet<Int>()
        for (seq in 1..1_000) assertTrue(NotificationAccessPolicy.firstAnnouncement(seq, seen), "transfert $seq")
        assertTrue(seen.size <= NotificationAccessPolicy.MAX_REMEMBERED, "mémoire bornée : ${seen.size}")
        assertFalse(NotificationAccessPolicy.firstAnnouncement(1_000, seen), "le dernier transfert reste connu")
        assertFalse(NotificationAccessPolicy.firstAnnouncement(1_000 - NotificationAccessPolicy.MAX_REMEMBERED + 1, seen), "le plus ancien gardé aussi")
    }

    @Test fun theMemoryNeverForgetsTheTransferJustAnnouncedEvenInAnUnorderedSet() {
        // un HashSet d'entiers s'itère par valeur croissante : en annonçant des numéros DÉCROISSANTS, le dernier noté est le premier de l'itération (le piège d'un « on oublie le premier »)
        val seen = HashSet<Int>()
        for (seq in (NotificationAccessPolicy.MAX_REMEMBERED + 50) downTo 1) {
            assertTrue(NotificationAccessPolicy.firstAnnouncement(seq, seen), "transfert $seq")
            assertFalse(NotificationAccessPolicy.firstAnnouncement(seq, seen), "transfert $seq annoncé à l'instant : jamais oublié aussitôt")
        }
    }
}
