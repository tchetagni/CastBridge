package castbridge.core.relay

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Les mots du relais : neutres (aucun nom de fichier, de code ni de clé), « CastBridge » et « CastBridge-TV » jamais « sender » ni « receiver », et ceux que le propriétaire a demandés mot pour mot. */
class RelayTextTest {
    @Test fun theNotificationIsNeutralAndNamesOnlyTheTv() {
        assertEquals("CastBridge relaie pour SMART_TV", RelayText.notification("SMART_TV"))
        assertEquals("CastBridge relaie pour la TV", RelayText.notification("   "), "un nom vide : « la TV »")
        assertEquals("CastBridge relaie pour Salon TV", RelayText.notification("Salon\u0000 TV"), "les caractères de contrôle sont ôtés")
        assertEquals("CastBridge relaie pour ab", RelayText.notification("a\nb"), "pas de saut de ligne")
        assertEquals("CastBridge relaie pour ab", RelayText.notification("a\u0007b"))
        assertTrue(RelayText.notification("x".repeat(200)).length <= "CastBridge relaie pour ".length + 40, "le nom d'une TV vient d'un autre appareil : borné")
    }

    @Test fun theLinesTheOwnerAskedForAreExact() {
        assertEquals("Mettez CastBridge à jour pour l'Internet par relais", RelayText.OLD_PHONE)
        assertEquals("Partie par relais : liaison lente", RelayText.PLAY_LINE)
        assertEquals("Internet partagé avec la TV", RelayText.NOTIFICATION_CHANNEL)
        assertEquals("CastBridge", RelayText.NOTIFICATION_PUBLIC, "la version publique de la notification ne dit rien d'autre")
        assertEquals("Arrêter", RelayText.STOP)
    }

    @Test fun everyTextIsInPlainFrenchWithTheRightNamesAndNothingTechnical() {
        val all = listOf(RelayText.NO_PHONE, RelayText.OLD_PHONE, RelayText.ASKING, RelayText.TIMEOUT, RelayText.PAUSED, RelayText.VIA_PHONE, RelayText.PLAY_LINE,
            RelayText.NOTIFICATION_PUBLIC, RelayText.NOTIFICATION_CHANNEL, RelayText.STOP, RelayText.notification("TV")) + RelayReason.values().map { RelayText.refusal(it) }
        val technical = Regex("sender|receiver|socks|rfcomm|uuid|bluetooth_|0x|\\.mp4|\\.apk", RegexOption.IGNORE_CASE)
        val keyLike = Regex("[A-Za-z0-9+/_-]{20,}")
        for (t in all) {
            assertTrue(t.isNotBlank(), t)
            assertFalse(technical.containsMatchIn(t), "mot technique ou nom de fichier : $t")
            assertFalse(keyLike.containsMatchIn(t), "rien qui ressemble à une clé, un jeton ou un code : $t")
        }
    }

    @Test fun everyRefusalHasItsOwnReadableText() {
        val texts = RelayReason.values().map { RelayText.refusal(it) }
        assertEquals(texts.size, texts.toSet().size, "deux motifs ne se disent pas pareil")
        assertTrue(RelayText.refusal(RelayReason.BACKGROUND).contains("Ouvrez CastBridge"), "le seul remède d'Android : ouvrir l'application une fois")
        assertTrue(RelayText.refusal(RelayReason.CAP).contains("plafond"))
        assertTrue(RelayText.refusal(RelayReason.OPTED_OUT).contains("ne relaie plus"))
    }
}
