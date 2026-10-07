package castbridge.core.connect

import castbridge.core.net.NetStateTracker
import castbridge.core.relay.PhoneInfo
import castbridge.core.relay.PipeBroker
import castbridge.core.relay.PipeEnv
import castbridge.core.relay.PipeNeed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * R-41 (audit anti-régression 2026-10-07 b, I-2) : une coupure d'Internet de la box, Wi-Fi relié, était masquée 15 à 20 minutes (avant relay-R1 : une soixantaine de secondes). La sonde
 * directe n'est plus faite que toutes les 5 minutes ; une sonde ratée était remplacée au tour suivant par « validé » dès que le système le disait (Android ne revalide que rarement), et un
 * contact direct de moins de 20 minutes valait preuve. Conséquences : portefeuille « en ligne », chaque opération attendait son délai, aucun tuyau n'était demandé au téléphone.
 */
class DirectLegTest {
    private val min = 60_000L

    /** Un réseau scripté : ce que la sonde répond maintenant, et combien de sondes sont parties. */
    private class Net { var answer: Long? = 40L; var probes = 0 }
    private val net = Net()
    private fun leg(plan: NetProbePlan = NetProbePlan()) = DirectLeg(plan) { net.probes++; net.answer }

    @Test fun aFailedProbeIsNotMaskedBySystemValidation() {
        val leg = leg(); var t = 10_000_000L
        assertEquals(40L, leg.measure(t, probeMode = true, manual = false, systemValidated = true).ms, "premier tour : la sonde répond")
        net.answer = null                                  // la box perd Internet ; le Wi-Fi reste relié et le système continue de dire « validé » (il ne revalide que rarement)
        t += min; leg.networkChanged()
        val failed = leg.measure(t, probeMode = true, manual = false, systemValidated = true)
        assertTrue(failed.probed); assertNull(failed.ms, "la sonde a raté")
        // les tours suivants, avant la sonde suivante : l'avis du système ne remplace JAMAIS une sonde ratée
        repeat(4) { k ->
            t += min
            val n = leg.measure(t, probeMode = true, manual = false, systemValidated = true)
            assertFalse(n.probed, "tour ${k + 1} : pas de nouvelle sonde (cadence de 5 minutes)")
            assertNull(n.ms, "tour ${k + 1} : une sonde ratée reste ratée")
        }
    }

    @Test fun aSuccessfulProbeIsCarriedBetweenProbesWhateverTheSystemSays() {
        val leg = leg(); var t = 10_000_000L
        leg.measure(t, true, false, systemValidated = false)
        t += min
        val r = leg.measure(t, true, false, systemValidated = false)
        assertFalse(r.probed)
        assertEquals(40L, r.ms, "la sonde fait foi en mode sonde : même si le système n'a pas validé le réseau (Google injoignable, serveur CastBridge joignable)")
    }

    @Test fun withoutProbeModeTheSystemIsTheOnlySourceAndNothingIsSent() {
        val leg = leg(); var t = 10_000_000L
        assertEquals(0L, leg.measure(t, probeMode = false, manual = false, systemValidated = true).ms, "validé par le système : joignable, sans mesure")
        t += min
        assertNull(leg.measure(t, probeMode = false, manual = false, systemValidated = false).ms, "pas validé : inconnu")
        assertEquals(0, net.probes, "aucune requête vers un tiers hors mode sonde")
        assertFalse(leg.measure(t, false, false, true).probed)
    }

    @Test fun aManualTestProbesAtOnce() {
        // le mode sonde est « manuel OU réglage OU conditions acceptées » : le test manuel de l'écran « Tests Internet » passe probeMode = true
        val leg = leg(); var t = 10_000_000L
        leg.measure(t, true, false, true)
        t += 1_000
        assertTrue(leg.measure(t, probeMode = true, manual = true, systemValidated = true).probed)
        assertEquals(2, net.probes)
    }

    @Test fun aFailedRealCallMakesTheNextRoundProbeAndRemembersWhen() {
        val plan = NetProbePlan(); val leg = leg(plan); var t = 10_000_000L
        leg.measure(t, true, false, true)                                    // sonde de 5 minutes faite à t
        t += 20_000
        assertFalse(leg.measure(t, true, false, true).probed, "20 s après : pas de sonde")
        assertEquals(0L, leg.failedAt())
        leg.callFailed(t + 5_000)                                            // un appel réel (portefeuille, télémétrie…) échoue sur le réseau propre
        assertEquals(t + 5_000, leg.failedAt())
        assertTrue(leg.measure(t + 40_000, true, false, true).probed, "le tour suivant sonde, sans attendre les 5 minutes")
        assertFalse(leg.measure(t + 100_000, true, false, true).probed, "une seule sonde : la cadence reprend")
    }

    @Test fun aRecoveredNetworkIsSeenAtTheNextEvent() {
        val leg = leg(); var t = 10_000_000L
        leg.measure(t, true, false, true)
        net.answer = null; t += min; leg.networkChanged()
        assertNull(leg.measure(t, true, false, true).ms)
        net.answer = 55L; t += min; leg.networkChanged()                     // le système revalide (onCapabilitiesChanged) : la TV sonde tout de suite
        assertEquals(55L, leg.measure(t, true, false, true).ms)
    }

    @Test fun theRecheckAfterAFailedCallStaysUnder90Seconds() {
        // un tour de boucle (60 s) + une sonde au pire cas (connexion 6 s + lecture 6 s)
        assertEquals(90_000L, DirectLeg.RECHECK_BUDGET_MS)
        assertTrue(NetStateTracker.STEADY_MS + 2 * DirectLeg.PROBE_TIMEOUT_MS <= DirectLeg.RECHECK_BUDGET_MS, "la boucle réseau ne doit pas être plus lente que la promesse")
    }

    // ------------------------------------------------------------------ le parcours : la box perd Internet, la TV le voit et demande un tuyau

    @Test fun aBoxOutageIsSeenWithin90SecondsAndThePipeIsAsked() {
        // La TV, Wi-Fi relié, sondée il y a 2 minutes, dernier battement de cœur direct il y a 5 minutes. La box perd Internet ; le système dit toujours « validé ».
        var now = 50_000_000L
        val plan = NetProbePlan(); val leg = leg(plan)
        val lastContactAt = now - 5 * min; val lastContactOk = true
        val failedAfter = { leg.failedAt() }
        var directMs: Long? = null
        fun facts() = NetFacts(checked = true, linkUp = true, directValidated = directMs != null,
            directContactRecent = NetStates.contactRecent(lastContactOk, Routes.Via.DIRECT.key, lastContactAt, now, failedAfter()), relayConnected = false, relayConfirmed = false)
        val knocked = ArrayList<String>()
        val env = object : PipeEnv {
            override fun now() = now
            override fun sleep(ms: Long) { now += ms }
            override fun net() = NetStates.of(facts())
            override fun phones() = listOf(PhoneInfo("AA:BB:CC:DD:EE:FF", true, now - 1_000))
            override fun knock(address: String) { knocked += address }
        }
        val broker = PipeBroker(env)
        fun tick() { directMs = leg.measure(now, probeMode = true, manual = false, systemValidated = true).ms }

        // avant la panne : tout va bien
        tick(); assertEquals(NetState.DIRECT, env.net())
        net.answer = null                                                   // la box perd Internet (Wi-Fi toujours relié, validation du système inchangée)
        now += 30_000
        assertEquals(NetState.DIRECT, env.net(), "la TV ne le sait pas encore : le dernier tour disait joignable")

        // un appel réel échoue (opération du portefeuille) : la TV se sait en cause et revérifie
        val failedAt = now
        leg.callFailed(failedAt)
        now += NetStateTracker.STEADY_MS                                    // au plus un tour de boucle plus tard
        tick()
        assertTrue(now - failedAt <= DirectLeg.RECHECK_BUDGET_MS)
        assertEquals(NetState.NONE, env.net(), "plus joignable : ni le « validé » du système, ni le battement de cœur d'avant la panne ne le masquent")
        assertFalse(NetStates.reachable(facts()), "le portefeuille ne se dit plus en ligne")
        assertTrue(broker.need(PipeNeed.WALLET), "une opération a besoin d'Internet : le tuyau est demandé")
        assertEquals(listOf("AA:BB:CC:DD:EE:FF"), knocked, "la TV frappe à la porte du téléphone synchronisé")
    }
}
