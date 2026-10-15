package castbridge.core.ux

import castbridge.core.tv.NotificationAccessPolicy.Action
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * R-52 (2026-10-14, « les copies ne signalent plus en notification »), côté téléphone : sur le S21+ (Android 14) la permission POST_NOTIFICATIONS n'était pas accordée
 * (`importance=NONE`) et CastBridge ne disait rien : la progression des copies disparaissait de la barre sans une ligne d'explication. Ces tests figent le texte
 * affiché au-dessus de la carte d'envoi et de la file d'attente (avec le bouton « Autoriser ») : rien quand tout va bien, la cause dite quand ce n'est pas le cas,
 * et ce que fait le bouton (le dialogue d'Android pour un téléphone jamais interrogé, le réglage de l'app sinon).
 * L'écran Android lit l'état et dessine ce qui sort d'ici (garde de source : `NotificationHintSourceTest`).
 */
class NotificationHintTest {
    private val appOff = "Notifications désactivées pour CastBridge : la progression des copies n'apparaît pas dans la barre."

    @Test fun nothingIsSaidWhenEverythingWorks() {
        assertNull(NotificationHint.text(enabled = true, blockedChannels = emptyList()))
    }

    @Test fun theWholeAppSwitchedOffIsSaidInOneSentenceWhateverTheChannels() {
        assertEquals(appOff, NotificationHint.text(enabled = false, blockedChannels = emptyList()))
        assertEquals(appOff, NotificationHint.text(enabled = false, blockedChannels = listOf("Envoi vers la TV")),
            "l'app entière coupée dit tout : pas de liste de canaux par-dessus")
    }

    @Test fun aBlockedChannelIsNamed() {
        assertEquals("Canal « Envoi vers la TV » désactivé : la progression de ces copies n'apparaît pas dans la barre.",
            NotificationHint.text(enabled = true, blockedChannels = listOf("Envoi vers la TV")))
        assertEquals("Canal « Envoi Bluetooth » désactivé : la progression de ces copies n'apparaît pas dans la barre.",
            NotificationHint.text(enabled = true, blockedChannels = listOf("Envoi Bluetooth")))
    }

    @Test fun severalBlockedChannelsAreNamedInOneSentence() {
        assertEquals("Canaux « Envoi vers la TV » et « File d'attente des envois » désactivés : la progression de ces copies n'apparaît pas dans la barre.",
            NotificationHint.text(true, listOf("Envoi vers la TV", "File d'attente des envois")))
        assertEquals("Canaux « Envoi vers la TV », « File d'attente des envois » et « Envoi Bluetooth » désactivés : la progression de ces copies n'apparaît pas dans la barre.",
            NotificationHint.text(true, listOf("Envoi vers la TV", "File d'attente des envois", "Envoi Bluetooth")))
    }

    // ---- ce que fait « Autoriser » ----

    @Test fun aPhoneNeverAskedIsAskedNotSentToTheSettings() {
        // depuis l'ergonomie du 2026-10-03 l'app s'ouvre sur l'accueil « CastBridge TV » ; la seule demande d'autorisation vit dans l'onglet « TV DLNA » : un téléphone qui n'ouvre pas
        // cet onglet n'a JAMAIS vu le dialogue (rationale faux, aucun refus retenu) : « Autoriser » doit le lui montrer
        for (sdk in listOf(33, 34, 35))
            assertEquals(Action.ASK, NotificationHint.buttonAction(sdk, appEnabled = false, rationale = false, deniedBefore = false), "sdk=$sdk")
    }

    @Test fun refusedOnceAndroidAsksAgainRefusedTwiceTheAppSettingsOpen() {
        assertEquals(Action.ASK, NotificationHint.buttonAction(34, appEnabled = false, rationale = true, deniedBefore = true), "refusé une fois : dernière chance du dialogue")
        assertEquals(Action.OPEN_SETTINGS, NotificationHint.buttonAction(34, appEnabled = false, rationale = false, deniedBefore = true), "refusé deux fois : le dialogue est mort")
    }

    @Test fun onlyBlockedChannelsAlwaysOpenTheAppSettings() {
        // l'autorisation est accordée : la redemander ne ferait rien et le texte resterait ; les canaux se rallument dans le réglage de l'app
        for (sdk in listOf(26, 31, 33, 34)) for (rationale in listOf(false, true)) for (denied in listOf(false, true))
            assertEquals(Action.OPEN_SETTINGS, NotificationHint.buttonAction(sdk, appEnabled = true, rationale = rationale, deniedBefore = denied), "sdk=$sdk rationale=$rationale denied=$denied")
    }

    @Test fun beforeAndroidThirteenTheSettingsOnly() {
        for (sdk in listOf(26, 29, 32)) assertEquals(Action.OPEN_SETTINGS, NotificationHint.buttonAction(sdk, appEnabled = false, rationale = false, deniedBefore = false), "sdk=$sdk")
    }

    @Test fun theButtonAndTheCopyChannelsAreTheOnesTheServicesCreate() {
        assertEquals("Autoriser", NotificationHint.BUTTON)
        assertEquals(listOf("upload" to "Envoi vers la TV", "queue" to "File d'attente des envois", "btupload" to "Envoi Bluetooth"), NotificationHint.COPY_CHANNELS)
    }

    @Test fun noTextUsesTheInternalNamesOfTheApps() {
        val all = listOf(appOff, NotificationHint.BUTTON, NotificationHint.text(true, listOf("Envoi vers la TV", "Envoi Bluetooth")).orEmpty())
        for (t in all) assertFalse(Regex("(?i)sender|receiver").containsMatchIn(t), "les apps s'appellent CastBridge et CastBridge-TV : $t")
        assertTrue(all.none { it.isBlank() })
    }
}
