package castbridge.core.remote

import castbridge.core.PhoneSources
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Gardes de source de « Ouvrir sur la TV » côté téléphone (audit anti-régression 2026-10-07 b) :
 *  - R-34 (I-15) : le raccourci d'icône `castbridge://open-tv` ne rejoue pas l'ouverture, sans geste, quand on revient par les récents après la mort du processus : Android recrée alors
 *    l'activité avec l'intention d'ORIGINE (le lien y est encore : `intent.data = null` ne vaut qu'en mémoire), `savedInstanceState` est rempli et/ou le drapeau
 *    `FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY` est posé. La règle pure est [OpenTvLaunch.fires].
 *  - R-35 (I-14) : la touche « TV » de la télécommande en Bluetooth ouvrait une SECONDE liaison RFCOMM vers le service que la télécommande tient déjà (même canal, refusé : « La TV ne répond
 *    pas » alors que la télécommande marche). Quand la liaison CBTR de la télécommande est vivante, l'ouverture passe dessus ([OpenTvSessionLink]).
 */
class OpenTvPhoneSourceTest {
    private val main = PhoneSources.code("sender/src/main/kotlin/castbridge/sender/MainActivity.kt")
    private val openTv = PhoneSources.code("sender/src/main/kotlin/castbridge/sender/OpenTv.kt")
    private val remote = PhoneSources.code("sender/src/main/kotlin/castbridge/sender/RemoteController.kt")

    // ------------------------------------------------------------------ I-15

    @Test fun theShortcutLinkFiresOnlyForAFreshGestureNotForARestoredOrRecentsLaunch() {
        val onCreate = main.substringAfter("override fun onCreate(").substringBefore("override fun onResume(")
        assertTrue(onCreate.contains("installFrom("), "le test ne voit plus onCreate")
        assertTrue(Regex("""OpenTvLaunch\.fires\(""").containsMatchIn(main), "MainActivity décide par OpenTvLaunch.fires(restored, fromHistory)")
        assertTrue(onCreate.contains("savedInstanceState != null"), "l'activité recréée (état sauvegardé) ne rejoue pas le lien")
        assertTrue(main.contains("FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY"), "un retour par les récents ne rejoue pas le lien")
        assertFalse(Regex("""installFrom\(\s*intent\s*\)""").containsMatchIn(main), "installFrom reçoit toujours le geste décidé (jamais installFrom(intent) seul)")
    }

    @Test fun aLinkThatDoesNotFireIsStillConsumedFromTheIntent() {
        assertTrue(Regex("""fun discardLink\(""").containsMatchIn(openTv), "OpenTv sait retirer le lien d'une intention rejouée")
        assertTrue(main.contains("OpenTv.discardLink("), "MainActivity consomme l'intention même quand elle ne déclenche rien")
    }

    // ------------------------------------------------------------------ I-14

    @Test fun theBluetoothRouteReusesTheLiveLinkOfTheRemote() {
        assertTrue(Regex("""OpenTvRoute\.BLUETOOTH\s*->\s*OpenTvSessionLink\(""").containsMatchIn(openTv), "la voie Bluetooth essaie d'abord la liaison de la télécommande")
        assertTrue(openTv.contains("RemoteController.liveBluetooth("), "la liaison vient de la session de la télécommande")
        assertTrue(Regex("""fun liveBluetooth\(""").containsMatchIn(remote), "RemoteController rend la liaison Bluetooth vivante de la TV demandée")
    }
}
