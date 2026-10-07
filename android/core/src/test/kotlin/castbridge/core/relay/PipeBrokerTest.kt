package castbridge.core.relay

import castbridge.core.connect.NetState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** relay-R1 § 2 : la TV demande le tuyau au téléphone synchronisé quand une opération vivante a besoin d'Internet et que NetState = none. */
class PipeBrokerTest {
    private class Env(var phones: List<PhoneInfo>) : PipeEnv {
        var t = 1_000_000L
        var net = NetState.NONE
        val knocks = ArrayList<Pair<Long, String>>()
        /** Appelé à chaque attente : permet à un test de « brancher le téléphone » au bon moment. */
        var onSleep: (Long) -> Unit = {}
        override fun now() = t
        override fun sleep(ms: Long) { t += ms; onSleep(t) }
        override fun net() = net
        override fun phones() = phones
        override fun knock(address: String) { knocks += t to address }
    }

    private val A = "AA:BB:CC:DD:EE:01"
    private val B = "AA:BB:CC:DD:EE:02"
    private fun phone(a: String, capable: Boolean? = true, seen: Long = 900_000L) = PhoneInfo(a, capable, seen)
    private fun broker(e: Env, maxAsks: Int = 6) = PipeBroker(e, leaseMs = 120_000, askEveryMs = 15_000, maxAsks = maxAsks, pauseMs = 300_000, pollMs = 500)

    @Test fun withInternetNothingIsAskedAndTheAnswerIsImmediate() {
        val e = Env(listOf(phone(A))).apply { net = NetState.DIRECT }
        val b = broker(e)
        assertEquals(PipeOutcome.Up(NetState.DIRECT), b.ensure(PipeNeed.PLAY, 10_000))
        assertTrue(e.knocks.isEmpty())
        assertFalse(b.wanted())
    }

    @Test fun withoutAnySynchronizedPhoneItSaysSoAtOnce() {
        val e = Env(emptyList())
        val out = broker(e).ensure(PipeNeed.PLAY, 10_000)
        assertEquals(PipeOutcome.Failed(PipeWhy.NO_PHONE, RelayText.NO_PHONE), out)
        assertTrue(e.knocks.isEmpty())
        assertEquals(1_000_000L, e.t, "aucune attente")
    }

    @Test fun anOldPhoneIsToldToUpdate() {
        val e = Env(listOf(phone(A, capable = false)))
        val out = broker(e).ensure(PipeNeed.PLAY, 10_000)
        assertEquals(PipeOutcome.Failed(PipeWhy.OLD_PHONE, "Mettez CastBridge à jour pour l'Internet par relais"), out)
        assertTrue(e.knocks.isEmpty(), "pas de demande à un téléphone qui ne sait pas la comprendre : comportement actuel (manuel)")
    }

    @Test fun anUnknownCapabilityIsTriedAndOneCapablePhoneIsEnough() {
        val e = Env(listOf(phone(A, capable = false), phone(B, capable = null)))
        broker(e).need(PipeNeed.PLAY)
        assertEquals(listOf(B), e.knocks.map { it.second }, "seul le téléphone dont la version est inconnue est sollicité")
    }

    @Test fun theFirstAskIsImmediateThenEveryFifteenSecondsThenItGivesUp() {
        val e = Env(listOf(phone(A)))
        val b = broker(e)
        val t0 = e.t
        b.need(PipeNeed.PLAY)
        assertEquals(listOf(t0), e.knocks.map { it.first })
        // la partie rappelle `need` tant qu'elle vit : une demande tous les 15 s, jamais plus
        var guard = 0
        while (guard++ < 200) { e.t += 1_000; b.need(PipeNeed.PLAY) }   // 200 s : la pause de 5 minutes n'est pas finie
        val times = e.knocks.map { it.first - t0 }
        assertEquals(listOf(0L, 15_000L, 30_000L, 45_000L, 60_000L, 75_000L), times, "six demandes au plus par épisode")
        assertFalse(b.wanted(), "épisode épuisé : la TV cesse de demander et le dit")
        assertEquals(PipeWhy.PAUSED, (b.ensure(PipeNeed.PLAY, 1_000) as PipeOutcome.Failed).why)
    }

    @Test fun anExplicitUserActionRestartsAnExhaustedEpisode() {
        val e = Env(listOf(phone(A)))
        val b = broker(e, maxAsks = 2)
        b.need(PipeNeed.PLAY)
        e.t += 15_000; b.need(PipeNeed.PLAY)
        e.t += 15_001; b.need(PipeNeed.PLAY)                       // épuisé
        val before = e.knocks.size
        e.t += 20_000; b.need(PipeNeed.PLAY)
        assertEquals(before, e.knocks.size, "pas de nouvelle demande pendant la pause")
        b.need(PipeNeed.PLAY, force = true)
        assertEquals(before + 1, e.knocks.size, "l'utilisateur appuie de nouveau : on redemande")
    }

    @Test fun thePauseEndsByItself() {
        val e = Env(listOf(phone(A)))
        val b = broker(e, maxAsks = 1)
        b.need(PipeNeed.PLAY)
        e.t += 15_001; b.need(PipeNeed.PLAY)                       // épuisé
        val n = e.knocks.size
        e.t += 299_000; b.need(PipeNeed.PLAY)
        assertEquals(n, e.knocks.size)
        e.t += 2_000; b.need(PipeNeed.PLAY)
        assertEquals(n + 1, e.knocks.size, "cinq minutes plus tard on peut redemander")
    }

    @Test fun ensureReturnsAsSoonAsThePipeIsUpAndClearsTheDemand() {
        val e = Env(listOf(phone(A)))
        val b = broker(e)
        e.onSleep = { t -> if (t >= 1_000_000L + 3_000L) e.net = NetState.VIA_RELAY }
        val out = b.ensure(PipeNeed.PLAY, 25_000)
        assertEquals(PipeOutcome.Up(NetState.VIA_RELAY), out)
        assertEquals(1, e.knocks.size, "une seule demande suffit")
        assertFalse(b.wanted(), "le tuyau est ouvert : le drapeau pipeWanted retombe")
    }

    @Test fun ensureGivesUpAtTheDeadlineWithoutMoreAsksThanTheCadenceAllows() {
        val e = Env(listOf(phone(A)))
        val out = broker(e).ensure(PipeNeed.PLAY, 40_000)
        assertEquals(PipeWhy.TIMEOUT, (out as PipeOutcome.Failed).why)
        assertEquals(RelayText.TIMEOUT, out.text)
        assertEquals(3, e.knocks.size, "0 s, 15 s, 30 s")
        assertTrue(e.t - 1_000_000L in 40_000L..41_000L, "ne dépasse pas l'échéance de plus d'un pas")
    }

    @Test fun aPhoneThatRefusesIsLeftAloneAndItsReasonIsGiven() {
        val e = Env(listOf(phone(A)))
        val b = broker(e)
        b.report(A, RelayReason.OPTED_OUT)
        val out = b.ensure(PipeNeed.PLAY, 20_000)
        assertEquals(PipeOutcome.Failed(PipeWhy.REFUSED, RelayText.refusal(RelayReason.OPTED_OUT), RelayReason.OPTED_OUT), out)
        assertTrue(e.knocks.isEmpty())
        assertEquals(1_000_000L, e.t, "pas d'attente inutile : tous les téléphones ont dit non")
    }

    @Test fun otherPhonesAreStillAskedWhenOneRefuses() {
        val e = Env(listOf(phone(A), phone(B)))
        val b = broker(e)
        b.report(A, RelayReason.CAP)
        b.need(PipeNeed.PLAY)
        assertEquals(listOf(B), e.knocks.map { it.second })
    }

    @Test fun aRefusalExpiresAccordingToItsReason() {
        val e = Env(listOf(phone(A)))
        val b = broker(e)
        b.report(A, RelayReason.BACKGROUND)                         // « ouvrez CastBridge » : l'utilisateur peut le faire dans la minute
        e.t += 10_000; b.need(PipeNeed.PLAY)
        assertTrue(e.knocks.isEmpty())
        e.t += 25_000; b.need(PipeNeed.PLAY, force = true)
        assertEquals(listOf(A), e.knocks.map { it.second }, "30 s après, on redemande")
        // un retrait volontaire dure bien plus longtemps
        val e2 = Env(listOf(phone(A))); val b2 = broker(e2)
        b2.report(A, RelayReason.OPTED_OUT)
        e2.t += 30 * 60_000; b2.need(PipeNeed.PLAY, force = true)
        assertTrue(e2.knocks.isEmpty(), "30 minutes plus tard le propriétaire a toujours retiré le relais")
    }

    @Test fun aCleanReportOrAnOpenPipeForgetsTheRefusal() {
        val e = Env(listOf(phone(A)))
        val b = broker(e)
        b.report(A, RelayReason.OPTED_OUT)
        b.report(A, null)
        b.need(PipeNeed.PLAY)
        assertEquals(1, e.knocks.size)
    }

    @Test fun theLeaseExpiresWhenNobodyRefreshesIt() {
        val e = Env(listOf(phone(A)))
        val b = broker(e)
        b.need(PipeNeed.PLAY)
        assertTrue(b.wanted())
        e.t += 119_999; assertTrue(b.wanted())
        e.t += 2; assertFalse(b.wanted(), "la partie a fini : plus personne ne rafraîchit, le drapeau retombe")
    }

    @Test fun wantedFollowsTheNetwork() {
        val e = Env(listOf(phone(A)))
        val b = broker(e)
        b.need(PipeNeed.PLAY)
        assertTrue(b.wanted())
        e.net = NetState.DIRECT
        assertFalse(b.wanted(), "le Wi-Fi est revenu : plus besoin du téléphone")
        b.need(PipeNeed.PLAY)
        assertEquals(1, e.knocks.size, "et aucune demande inutile")
    }

    @Test fun phonesAreAskedMostRecentlySeenFirstAndAtMostThreeAtATime() {
        val phones = (1..8).map { phone("AA:BB:CC:DD:EE:0$it", true, seen = 100L * it) }
        val e = Env(phones)
        broker(e).need(PipeNeed.WALLET)
        assertEquals(listOf("AA:BB:CC:DD:EE:08", "AA:BB:CC:DD:EE:07", "AA:BB:CC:DD:EE:06"), e.knocks.map { it.second })
    }

    @Test fun theStatusLineSaysWhatIsHappening() {
        val e = Env(listOf(phone(A)))
        val b = broker(e)
        assertNull(b.text(), "aucune demande : aucune ligne")
        b.need(PipeNeed.PLAY)
        assertEquals(RelayText.ASKING, b.text())
        b.report(A, RelayReason.METERED_BULK)
        assertEquals(RelayText.refusal(RelayReason.METERED_BULK), b.text())
        e.net = NetState.VIA_RELAY
        assertNull(b.text())
    }

    @Test fun bulkNeedsAreKnownByTheBrokerToo() {
        val e = Env(listOf(phone(A)))
        val b = broker(e)
        b.need(PipeNeed.UPDATE_NOW)
        assertEquals(listOf(PipeNeed.UPDATE_NOW), b.currentNeeds(), "la demande dit pourquoi : le téléphone refuse un « bulk » sur données mobiles")
    }
}
