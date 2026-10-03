package castbridge.play.entitlement

import castbridge.core.owner.ActivationKind
import castbridge.core.quiz.Question
import castbridge.play.HubFixture
import castbridge.play.HubFixture.config
import castbridge.play.HubFixture.hub
import castbridge.play.HubFixture.open
import castbridge.play.TestKeys
import castbridge.play.TestRights
import castbridge.play.TestRights.DAY
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Les règles commerciales appliquées par le service à la création d'une salle (matrice du cœur `PlayRules`, DESIGN-W20 § 2.5) : l'activation `cbx1` jointe fait foi, jamais le ticket. */
class PlayRulesTest {
    private val now = System.currentTimeMillis()
    private fun t(code: String = TestRights.CODE) = TestKeys.ticket(now = now, deviceCode = code)

    @Test fun productionHostCreatesARoom() {
        val h = hub()
        assertTrue(open(h, t(), TestRights.create(TestRights.PROD)).welcomed())
    }

    @Test fun aValidTicketWithoutActivationIsHostNoneAndGetsTheActivationText() {
        val h = hub()
        val c = open(h, t(), TestRights.create(activation = null))
        assertFalse(c.welcomed()); assertEquals("PLAY_SCOPE_FORBIDDEN", c.errorReason())
        assertEquals("Activez la TV pour créer une partie Internet", c.errorMessage())
        assertEquals(0, h.roomCount())
    }

    @Test fun anActivationOfAnotherTvIsRefusedEvenWithAValidTicket() {
        val h = hub()
        val c = open(h, t(), TestRights.create(TestRights.activation(device = TestRights.otherTv)))
        assertEquals("PLAY_SCOPE_FORBIDDEN", c.errorReason()); assertEquals(0, h.roomCount())
    }

    @Test fun aTicketIsNotAnEditionTheActivationIsSoAForgedOrRogueActivationOpensNothing() {
        val h = hub()
        assertEquals("PLAY_SCOPE_FORBIDDEN", open(h, t(), TestRights.create(TestRights.activation(signer = TestRights.rogue))).errorReason())
        assertEquals(0, h.roomCount())
    }

    @Test fun trialHostPlaysPrivateWithThreeGamesADayThenIsRefusedInFrench() {
        val h = hub(config("idle" to 0L))
        val trial = TestRights.activation(ActivationKind.TRIAL, rights = TestRights.trialUsage(now))
        // l'essai tient UNE salle ouverte à la fois : chaque partie est fermée (hôte parti, salle vide) avant la suivante
        val results = (0 until 4).map { open(h, t(), TestRights.create(trial)).also { c -> h.onClosed(c); h.tick() } }
        assertEquals(listOf(true, true, true, false), results.map { it.welcomed() })
        assertEquals("PLAY_SCOPE_FORBIDDEN", results[3].errorReason()); assertTrue("3 parties" in results[3].errorMessage()!!, results[3].errorMessage())
        assertEquals(0, h.roomCount(), "salles fermées")
    }

    @Test fun theTrialDailyCountFollowsTheActivationIdentityNotTheAttestedDevice() {
        val h = hub(config("idle" to 0L))
        val trial = TestRights.activation(ActivationKind.TRIAL, rights = TestRights.trialUsage(now))
        // six appareils API frais avec LA MÊME activation d'essai : le compte du jour suit l'activation (signée), donc la 4e salle est refusée quand même
        val results = (0 until 6).map { open(h, TestKeys.ticket(now = now, deviceId = "dev-fresh-$it"), TestRights.create(trial)).also { c -> h.onClosed(c); h.tick() } }
        assertEquals(3, results.count { it.welcomed() })
    }

    @Test fun productionWithAToutRightAndTheFreeBankStillServesAllRoomsTheSameFreeBank() {
        val free = HubFixture.bank
        val src = object : ReservedSource { var loads = 0; override fun scopes() = setOf("cm2"); override fun load(scope: String): List<Question> { loads++; return emptyList() } }
        val rb = ReservedBank(free, src, setOf("r-1"))
        val h = hub(reserved = rb)
        val prod = TestRights.activation()
        val rent = TestRights.activation(rights = listOf(TestRights.rental("cm2", now - DAY)), seq = now + 3)
        assertTrue(open(h, t(), TestRights.create(prod)).welcomed())
        assertEquals(0, src.loads, "sans location : aucun paquet réservé n'est ouvert")
        assertTrue(open(h, t(), TestRights.create(prod, listOf(rent))).welcomed())
        assertEquals(1, src.loads, "avec location cm2 : le lot cm2 est lu, à la demande, une fois")
        assertTrue(open(h, t(), TestRights.create(prod, listOf(rent))).welcomed())
        assertEquals(1, src.loads, "pas de relecture (cache)")
    }

    @Test fun anExpiredRentalOpensAFreeRoomWithoutLoadingReservedQuestions() {
        val src = object : ReservedSource { var loads = 0; override fun scopes() = setOf("cm2"); override fun load(scope: String): List<Question> { loads++; return emptyList() } }
        val h = hub(reserved = ReservedBank(HubFixture.bank, src, setOf("r-1")))
        val old = TestRights.activation(rights = listOf(TestRights.rental("cm2", now - 40 * DAY, days = 30)))
        assertTrue(open(h, t(), TestRights.create(old)).welcomed())
        assertEquals(0, src.loads)
    }
}
