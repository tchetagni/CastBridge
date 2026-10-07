package castbridge.core.quiz.online

import castbridge.core.connect.NetState
import castbridge.core.quiz.online.PlayRelayProfile.Quality
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** relay-R1 § 5 (DESIGN-RELAIS § 2.5) : le jeu en ligne s'adapte quand la TV n'a Internet que par le tuyau d'un téléphone. Règles pures, sans changement du protocole du service. */
class PlayRelayProfileTest {
    private fun relay(rtt: Long? = null, kbps: Long? = null) = PlayRelayProfile.rules(NetState.VIA_RELAY, RelayLink(rtt, kbps))

    @Test fun withoutTheRelayNothingChanges() {
        for (net in listOf(NetState.DIRECT, NetState.NONE)) {
            val r = PlayRelayProfile.rules(net, RelayLink(900, 30))
            assertFalse(r.relay)
            assertEquals(0L, r.answerExtraMs)
            assertEquals(10_000, r.connectTimeoutMs); assertEquals(20_000, r.postReadTimeoutMs)
            assertContentEquals(PlayTvSession.CURVE_SEC, r.curveSec)
            assertNull(r.line); assertFalse(r.warmUp); assertEquals(0, r.rankingPenalty)
        }
    }

    @Test fun theRelayLineIsInformationNotAnAlarm() {
        assertEquals("Partie par relais : liaison lente", relay(150, 800).line)
        assertEquals("Partie par relais : liaison lente", relay().line, "même sans mesure")
        assertEquals("Partie par relais : liaison lente", relay(5_000, 5).line)
        assertTrue(relay().relay && relay().warmUp, "préchargement : la connexion au service est ouverte avant que l'utilisateur choisisse")
    }

    @Test fun qualityFollowsTheMeasure() {
        assertEquals(Quality.UNKNOWN, PlayRelayProfile.quality(RelayLink(null, null)))
        assertEquals(Quality.GOOD, PlayRelayProfile.quality(RelayLink(150, 800)))
        assertEquals(Quality.GOOD, PlayRelayProfile.quality(RelayLink(300, null)))
        assertEquals(Quality.SLOW, PlayRelayProfile.quality(RelayLink(301, 800)))
        assertEquals(Quality.SLOW, PlayRelayProfile.quality(RelayLink(100, 50)), "débit faible")
        assertEquals(Quality.SLOW, PlayRelayProfile.quality(RelayLink(null, 100)))
        assertEquals(Quality.VERY_SLOW, PlayRelayProfile.quality(RelayLink(1_501, 800)))
        assertEquals(Quality.VERY_SLOW, PlayRelayProfile.quality(RelayLink(100, 19)))
    }

    @Test fun theAnswerWindowIsExtendedByTheMeasuredLatencyWithinTheServersOwnGrace() {
        assertEquals(150, relay(150, 800).answerExtraMs)
        assertEquals(700, relay(700, 300).answerExtraMs)
        assertEquals(RttBook.MAX_GRACE_MS, relay(5_000, 10).answerExtraMs, "bornée : jamais plus que la grâce que le service accorde lui-même (1 s)")
        assertEquals(PlayRelayProfile.ASSUMED_RTT_MS, relay().answerExtraMs, "sans mesure : une latence supposée, bornée aussi")
        assertEquals(0L, relay(0, 800).answerExtraMs)
        assertEquals(0L, relay(-5, 800).answerExtraMs, "une mesure négative n'est pas une rallonge")
    }

    @Test fun windowForAddsTheExtensionToTheServersWindow() {
        assertEquals(20_000L, PlayRelayProfile.windowFor(20_000, PlayRelayProfile.rules(NetState.DIRECT, RelayLink(900, 30))))
        assertEquals(20_700L, PlayRelayProfile.windowFor(20_000, relay(700, 300)))
    }

    @Test fun timeoutsGrowWithTheLatencyAndStayBounded() {
        assertEquals(10_000 + 4 * 700, relay(700, 300).connectTimeoutMs)
        assertEquals(20_000 + 8 * 700, relay(700, 300).postReadTimeoutMs)
        assertEquals(25_000, relay(9_000, 5).connectTimeoutMs)
        assertEquals(45_000, relay(9_000, 5).postReadTimeoutMs)
        var prev = relay(0, 800); var rtt = 0L
        while (rtt <= 10_000) {
            val r = relay(rtt, 800)
            assertTrue(r.connectTimeoutMs >= prev.connectTimeoutMs && r.postReadTimeoutMs >= prev.postReadTimeoutMs && r.answerExtraMs >= prev.answerExtraMs, "rtt=$rtt")
            assertTrue(r.connectTimeoutMs in 10_000..25_000 && r.postReadTimeoutMs in 20_000..45_000 && r.answerExtraMs in 0..RttBook.MAX_GRACE_MS, "rtt=$rtt")
            prev = r; rtt += 50
        }
    }

    @Test fun reopeningIsSlowerOverTheRelayButTheLossThresholdIsTheServers() {
        assertContentEquals(intArrayOf(0, 3, 6, 12, 20, 30), relay(150, 800).curveSec)
        assertEquals(6, relay().curveSec.size)
        assertEquals(60_000L, PlayRelayProfile.RESUME_FREE_MS)
        assertEquals(PlayTvSession.LOST_AFTER_MS, PlayRelayProfile.RESUME_FREE_MS, "une seule règle des 60 s")
    }

    @Test fun aCutUnderSixtySecondsCostsNothingAndNeverRanksAnyone() {
        for (outage in listOf(0L, 1_000L, 30_000L, 59_999L)) assertFalse(PlayRelayProfile.gameAbandoned(outage), "$outage ms")
        assertTrue(PlayRelayProfile.gameAbandoned(60_000L))
        assertTrue(PlayRelayProfile.gameAbandoned(120_000L))
        assertEquals(0, relay(5_000, 5).rankingPenalty, "aucune pénalité de classement liée au relais, même très lent")
    }

    // ------------------------------------------------------------------ la mesure

    @Test fun theMeterTakesTheMedianOfTheLastPings() {
        val m = RelayLinkMeter()
        assertEquals(RelayLink(null, null, 0), m.profile())
        for (ms in listOf(120L, 100L, 5_000L, 110L, 130L)) m.pingSample(ms)
        assertEquals(120L, m.profile().rttMs, "un pic isolé ne change pas la latence retenue")
        assertEquals(5, m.profile().samples)
    }

    @Test fun theMeterKeepsOnlyTheRecentSamplesAndBoundsThem() {
        val m = RelayLinkMeter(keep = 4)
        repeat(10) { m.pingSample(2_000) }
        repeat(4) { m.pingSample(100) }
        assertEquals(100L, m.profile().rttMs, "les mesures anciennes sont oubliées")
        m.pingSample(-30); m.pingSample(900_000)
        val p = m.profile()
        assertTrue(p.rttMs!! in 0..RelayLinkMeter.MAX_RTT_MS)
    }

    @Test fun throughputIsTheBestRecentWindowOfRealTraffic() {
        val m = RelayLinkMeter()
        m.throughputSample(bytes = 10, ms = 1_000)                       // bruit : ignoré
        assertNull(m.profile().kbps)
        m.throughputSample(bytes = 50_000, ms = 10_000)                  // 40 kbit/s : la liaison de référence (EDGE)
        assertEquals(40L, m.profile().kbps)
        m.throughputSample(bytes = 200_000, ms = 2_000)                  // 800 kbit/s
        assertEquals(800L, m.profile().kbps, "le meilleur débit récent : la capacité, pas la moyenne d'une pause")
        m.throughputSample(bytes = 5_000, ms = 0)                        // durée nulle : ignorée
        m.throughputSample(bytes = -1, ms = 1_000)
        assertEquals(800L, m.profile().kbps)
    }

    @Test fun theMeterCanBeResetWhenThePipeCloses() {
        val m = RelayLinkMeter()
        m.pingSample(100); m.throughputSample(50_000, 1_000)
        m.reset()
        assertEquals(RelayLink(null, null, 0), m.profile())
    }
}
