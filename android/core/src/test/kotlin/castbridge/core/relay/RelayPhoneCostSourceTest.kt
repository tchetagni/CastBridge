package castbridge.core.relay

import castbridge.core.PhoneSources
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * R-33 (audit anti-régression 2026-10-07 b, I-13), moitié téléphone : la garde de coût REL-F7 (« 0 octet gros volume sur réseau facturé, plafond du petit volume respecté ») était percée :
 *  - la TV remet « réseau du téléphone facturé ? » à inconnu à chaque coupure du lien, et le téléphone qui se reconnecte ne le redisait pas ([MeteredAnnouncer] : il le redit après
 *    chaque poignée de main, et à chaque changement) ;
 *  - la surveillance du tuyau (toutes les 5 s) rejugeait avec `need = null` : un tuyau ouvert pour du gros volume sur Wi-Fi continuait sur les données mobiles et mangeait les 5 Mo du jour
 *    réservés au jeu et au portefeuille ; elle garde maintenant le besoin de l'ouverture (`Run.need`, repris après une mort du service par `RelaySettings.Session.need`).
 *  - les octets d'un réseau qui n'est pas SÛREMENT gratuit comptent contre le plafond ([RelayCost.countsAgainstCap] : inconnu = facturé).
 * (La moitié TV, `TvNet.backgroundBulkAllowed` : inconnu = facturé, est la règle pure [RelayCost.tvBulkAllowed], à brancher par le chantier fix-tv.)
 */
class RelayPhoneCostSourceTest {
    private val svc = PhoneSources.code("sender/src/main/kotlin/castbridge/sender/BtGatewayService.kt")
    private val runtime = PhoneSources.code("sender/src/main/kotlin/castbridge/sender/RelayRuntime.kt")
    private val settings = PhoneSources.code("sender/src/main/kotlin/castbridge/sender/RelaySettings.kt")

    @Test fun theWatchdogJudgesTheCostPolicyWithTheNeedTheTunnelWasOpenedFor() {
        assertTrue(Regex("""private class Run\([^)]*\bneed:\s*PipeNeed\?""").containsMatchIn(svc), "Run garde le besoin de l'ouverture")
        val watchdog = svc.substringAfter("private fun watchdog(").substringBefore("private fun runDiag(")
        assertTrue(watchdog.contains("RelayPolicy.decide("), "le test ne voit plus la surveillance")
        assertTrue(Regex("""RelayInput\([\s\S]{0,400}?\bneed\s*=\s*r\.need\b""").containsMatchIn(watchdog), "la surveillance rejuge avec need = r.need, plus avec need = null")
    }

    @Test fun theNeedTravelsFromTheDecisionToTheServiceAndSurvivesAKillOfTheService() {
        assertTrue(Regex("""fun startAuto\([^)]*\bneed:\s*PipeNeed\?""").containsMatchIn(svc), "startAuto reçoit le besoin")
        assertTrue(Regex("""startAuto\([^)]*\bneed\b""").containsMatchIn(runtime.substringAfter("private fun decideAndStart(")), "RelayRuntime donne le besoin à startAuto")
        assertTrue(Regex("""class Session\([^)]*\bneed:\s*PipeNeed\?""").containsMatchIn(settings), "la session sauvegardée garde le besoin (reprise par le système)")
        assertTrue(svc.contains("EXTRA_NEED"), "le besoin passe par l'Intent du démarrage automatique (jamais un identifiant)")
    }

    @Test fun thePhoneTellsTheTvItsNetworkAfterEveryHandshakeAndWhenItChanges() {
        assertTrue(svc.contains("MeteredAnnouncer("), "le service utilise l'annonceur")
        assertTrue(Regex("""Exit\([\s\S]{0,400}?\bonHello\s*=""").containsMatchIn(svc), "la poignée de main de la passerelle déclenche l'annonce (la TV oublie à chaque coupure)")
        val watchdog = svc.substringAfter("private fun watchdog(").substringBefore("private fun runDiag(")
        assertTrue(watchdog.contains("announce("), "la surveillance annonce un changement de réseau (Wi-Fi puis données mobiles)")
        assertTrue(svc.contains("RelayRuntime.report("), "l'annonce passe par le canal propriétaire (RELAY_STATE)")
    }

    @Test fun theWatchdogCountsEveryNetworkThatIsNotSurelyFree() {
        val watchdog = svc.substringAfter("private fun watchdog(").substringBefore("private fun runDiag(")
        assertTrue(watchdog.contains("meter.add("), "le test ne voit plus le compteur d'octets")
        assertTrue(watchdog.contains("RelayCost.countsAgainstCap("), "le compteur du jour suit la règle commune (inconnu = facturé)")
        assertFalse(Regex("""net\s*==\s*PhoneNet\.METERED\s*&&\s*!settings\.allowMobile""").containsMatchIn(watchdog), "plus de « seulement quand le réseau est SÛREMENT facturé »")
    }

    @Test fun theMeteredFlagOfThePhoneIsBuiltInOnePlace() {
        // « réseau inconnu » (PhoneNet.NONE) n'est ni gratuit ni facturé : le drapeau est absent (null), jamais « non facturé » ; une seule définition (meteredFlag), pas trois copies
        for (code in listOf(svc, runtime)) assertFalse(Regex("""PhoneNet\.NONE\)\s*null\s*else""").containsMatchIn(code), "le drapeau « facturé » se calcule par PhoneNet.meteredFlag()")
        assertTrue(runtime.contains(".meteredFlag()"), "RelayRuntime utilise la définition unique")
    }
}
