package castbridge.core.tv

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Garde de source du branchement Android des correctifs `fix-tv` (audit anti-régression 2026-10-07 b : I-1, I-2, I-7, I-11, I-12, I-16 et R-46). L'audit l'a constaté : l'assemblage Android
 * n'a aucun test JVM, donc une règle pure juste peut rester branchée à l'ancien comportement et la suite reste verte. Ici : les points de branchement sont bien ceux qui appellent les règles
 * pures testées ailleurs. Un fichier absent fait ÉCHOUER le test (pas de vert à vide). Les commentaires ne comptent pas (une histoire racontée n'est pas un appel).
 */
class FixTvSourceTest {
    private fun src(rel: String): File = File("../receiver/src/main/kotlin/castbridge/receiver/$rel")

    /** Le code d'un fichier source : commentaires de bloc et de ligne retirés. */
    private fun code(rel: String): String {
        val f = src(rel)
        assertTrue(f.isFile, "source introuvable : ${f.absolutePath}")
        return f.readText().replace(Regex("/\\*[\\s\\S]*?\\*/"), " ").replace(Regex("//[^\\n]*"), "")
    }

    /** Le corps de la fonction qui commence par [signature] jusqu'au début de [until] (texte brut, sans accolades à compter). */
    private fun between(code: String, signature: String, until: String): String {
        val a = code.indexOf(signature); assertTrue(a >= 0, "« $signature » introuvable")
        val b = code.indexOf(until, a + signature.length); assertTrue(b > a, "« $until » introuvable après « $signature »")
        return code.substring(a, b)
    }

    // ---- R-40 (I-1) : « Ouvrir sur la TV » ne vide plus la tâche ----
    @Test fun openOnTheTvBringsTheTaskBackWithoutClearingIt() {
        val c = code("TvForeground.kt")
        assertTrue("OpenTvPlan.target(" in c, "le geste doit demander à la règle pure ce qui revient à l'écran")
        assertTrue("moveToFront()" in c && "FLAG_ACTIVITY_REORDER_TO_FRONT" in c, "la tâche revient telle quelle : moveToFront ou REORDER_TO_FRONT")
        val resume = between(c, "internal fun intentFor(", "private fun screenIntent(")
        assertFalse("CLEAR_TOP" in resume || "SINGLE_TOP" in resume, "revenir à CastBridge-TV sans écran demandé ne doit jamais vider la tâche (PlayerActivity est singleTask)")
        assertFalse("PlayerActivity" in resume.substringBefore("OpenTarget.Screen") && "top?.let" !in resume, "PlayerActivity n'est démarré sans écran que faute d'écran du sommet")
        val explicit = between(c, "private fun screenIntent(", "private class AndroidActions")
        assertTrue("FLAG_ACTIVITY_CLEAR_TOP" in explicit, "un écran DEMANDÉ s'ouvre comme avant")
        assertEquals(1, Regex("FLAG_ACTIVITY_CLEAR_TOP").findAll(c).count(), "CLEAR_TOP seulement pour un écran demandé")
        assertTrue("top = a" in code("ScreenCapture.kt"), "ScreenCapture garde le sommet de la tâche (le dernier écran repris et encore vivant)")
    }

    // ---- R-41 (I-2) : la vérité réseau passe par DirectLeg, un appel échoué annule la preuve, l'avis du système est écouté ----
    @Test fun theNetworkTruthUsesTheDirectLegAndListensToTheSystemVerdict() {
        val net = code("TvNet.kt")
        assertTrue("DirectLeg(" in net && "direct.measure(" in net, "TvNet.measure délègue à DirectLeg (une sonde ratée n'est plus remplacée par l'avis du système)")
        assertFalse("lastDirectMs" in net, "plus de mémoire de sonde hors de DirectLeg")
        assertTrue("direct.failedAt()" in net, "la preuve d'un contact direct plus ancien qu'un appel échoué ne compte plus")
        assertTrue("onDirectFailure = TvNet::directFailed" in code("TvConnect.kt"), "un appel direct échoué est dit à la vérité réseau")
        val service = code("TvService.kt")
        assertTrue("onCapabilitiesChanged" in service && "ValidationWatch" in service && "NET_CAPABILITY_VALIDATED" in service, "TvService écoute le verdict VALIDATED du système")
        assertTrue("DirectLeg.PROBE_TIMEOUT_MS" in code("TvNetDiag.kt"), "les délais de la sonde sont ceux du budget de 90 s")
    }

    // ---- R-42 (I-12) : la passerelle réécoute ----
    @Test fun theGatewayListensAgainAtBluetoothOnAndItsLoopSurvivesAFailedAccept() {
        val host = code("BtGatewayHost.kt")
        assertTrue("AcceptLoop<" in host, "la boucle d'acceptation est celle du cœur (testée avec de fausses sockets)")
        assertFalse(Regex("while \\(running\\)\\s*\\{\\s*val sock = try").containsMatchIn(host), "l'ancienne boucle qui sortait au premier accept() échoué est retirée")
        assertTrue("fun restart()" in host && "gaveUp" in host, "un `start()` redevient possible quand la boucle renonce")
        val service = code("TvService.kt")
        assertTrue("ACTION_STATE_CHANGED" in service && "BtAdapterWatch.listenAgain(" in service && "gateway?.restart()" in service, "au retour du Bluetooth (STATE_ON) la passerelle réécoute")
    }

    // ---- R-43 (I-11) : le lien est servi avec l'identité du téléphone ----
    @Test fun theLinkIsServedWithThePhonesAddress() {
        assertTrue(Regex("entry\\.attach\\(mux, peer, peerAddr\\)").containsMatchIn(code("BtGatewayHost.kt")), "Entry.attach reçoit l'adresse du téléphone : seul le MÊME appareil remplace son lien")
    }

    // ---- R-44 (I-16) : une clé USB déjà installée n'est ni trouvée ni périmée ----
    @Test fun aKeyAlreadyInstalledIsNotVerifiedNorAnnounced() {
        val c = code("ActivationCenter.kt")
        assertTrue("UsbKeyJudge.installed(" in c && "Verdict.INSTALLED" in c, "la recherche sur la clé USB compare à ce qui est installé")
        assertTrue("State.INSTALLED" in code("ActivationActivity.kt"), "le bandeau reste silencieux pour une clé déjà installée")
    }

    // ---- R-45 (I-7) : le tunnel n'emprunte le tuyau que pour une assistance demandée ----
    @Test fun theTunnelRidesThePhonesPipeOnlyForAnAskedAssistance() {
        assertTrue("TunnelConnectivity.path(TvNet.state(), TvNet.assistPipeAllowed())" in code("TunnelHub.kt"), "le chemin du tunnel dépend de l'assistance demandée")
        assertTrue("TvNet.requestAssistance()" in code("PlayerActivity.kt"), "« Se connecter maintenant » ouvre la fenêtre d'assistance")
        val net = code("TvNet.kt")
        assertTrue("assist.attached(" in net && "assist.detached()" in net, "l'origine du tuyau (demandé par la TV ou partage manuel) est retenue à chaque liaison")
    }

    // ---- R-46 : le portefeuille se règle sur la dernière tentative ----
    @Test fun theWalletScheduleReadsTheLastAttemptNotOnlyTheLastSuccess() {
        val c = code("wallet/WalletHub.kt")
        assertTrue("shouldSyncAfter(" in c && "lastAttempt" in c && "schedule.after(" in c, "le tick se règle sur la dernière TENTATIVE")
        assertFalse(Regex("schedule\\.shouldSync\\(").containsMatchIn(c), "plus d'appel qui ne connaît que la dernière réussite")
    }
}
