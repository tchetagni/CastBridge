package castbridge.core.quiz.online

import castbridge.core.quiz.online.PlayRules.Actor
import kotlin.test.*

/** La matrice des droits du quiz en ligne (DESIGN-W20 § 2.5, w20-04) : 9 acteurs × créer / rejoindre / réservées / classé. */
class PlayRulesTest {
    private fun row(a: Actor) = listOf(PlayRules.canCreate(a).allowed, PlayRules.canJoin(a), PlayRules.reservedAllowed(a), PlayRules.isRanked(a, publicRoom = false))

    @Test fun theNineRowMatrixIsExactlyTheTableOfSection25() {
        // créer · rejoindre · réservées · classé
        val expected = mapOf(
            Actor.TV_PROD to listOf(true, false, false, true),
            Actor.TV_PROD_RENTAL to listOf(true, false, true, true),
            Actor.TV_TRIAL to listOf(true, false, false, false),
            Actor.TV_GRACE to listOf(true, false, false, true),
            Actor.TV_CLOCK_DOUBT to listOf(false, false, false, false),
            Actor.TV_LOCKED to listOf(false, false, false, false),
            Actor.PHONE_APP to listOf(false, true, false, true),
            Actor.PHONE_WEB to listOf(false, true, false, true),
            Actor.PUBLIC_LOBBY to listOf(false, true, false, true),
        )
        assertEquals(9, Actor.values().size)
        for (a in Actor.values()) assertEquals(expected.getValue(a), row(a), "ligne $a")
    }

    @Test fun joiningIsFreeForEveryPlayerAndNeverForATv() {
        assertTrue(listOf(Actor.PHONE_APP, Actor.PHONE_WEB, Actor.PUBLIC_LOBBY).all { PlayRules.canJoin(it) })
        assertTrue(Actor.values().filter { it.name.startsWith("TV_") }.none { PlayRules.canJoin(it) }, "la TV n'est pas un joueur")
    }

    @Test fun trialIsPrivateOnlyWithThreeGamesADay() {
        assertTrue(PlayRules.canCreate(Actor.TV_TRIAL, publicRoom = false, gamesToday = 2).allowed)
        val third = PlayRules.canCreate(Actor.TV_TRIAL, publicRoom = false, gamesToday = 3)
        assertFalse(third.allowed); assertEquals(PlayReason.PLAY_SCOPE_FORBIDDEN, third.reason)
        assertTrue("3 parties" in third.message, third.message)
        val pub = PlayRules.canCreate(Actor.TV_TRIAL, publicRoom = true, gamesToday = 0)
        assertFalse(pub.allowed); assertTrue("privé" in pub.message, pub.message)
        assertFalse(PlayRules.isRanked(Actor.TV_TRIAL, publicRoom = false), "parties d'essai non classées")
        assertEquals(PlayRules.TRIAL_GAMES_PER_DAY, 3)
    }

    @Test fun productionMayCreatePublicRoomsAndTrialNever() {
        assertTrue(PlayRules.canCreate(Actor.TV_PROD, publicRoom = true).allowed)
        assertTrue(PlayRules.canCreate(Actor.TV_PROD_RENTAL, publicRoom = true).allowed)
        assertFalse(PlayRules.canCreate(Actor.TV_TRIAL, publicRoom = true).allowed)
    }

    @Test fun unactivatedAndDoubtfulClockTvsGetAFrenchReasonNotASilence() {
        val none = PlayRules.canCreate(Actor.TV_LOCKED)
        assertEquals(PlayReason.PLAY_SCOPE_FORBIDDEN, none.reason); assertEquals("Activez la TV pour créer une partie Internet", none.message)
        val clock = PlayRules.canCreate(Actor.TV_CLOCK_DOUBT)
        assertEquals(PlayReason.PLAY_SCOPE_FORBIDDEN, clock.reason); assertTrue("Vérifiez l'heure de la TV" in clock.message)
        assertFalse(PlayRules.canCreate(Actor.PHONE_WEB).allowed)
        assertTrue(PlayRules.canCreate(Actor.PHONE_WEB).message.isNotBlank())
    }

    @Test fun reservedQuestionsOnlyForTheEntitledHostAndOnlyForItsCoveredScopes() {
        assertTrue(PlayRules.reservedScopeAllowed(Actor.TV_PROD_RENTAL, setOf("cm2"), "cm2"))
        assertFalse(PlayRules.reservedScopeAllowed(Actor.TV_PROD_RENTAL, setOf("cm2"), "3e"))
        assertTrue(PlayRules.reservedScopeAllowed(Actor.TV_PROD_RENTAL, setOf("tout"), "3e"), "« tout » couvre tous les lots")
        assertFalse(PlayRules.reservedScopeAllowed(Actor.TV_PROD, setOf("cm2"), "cm2"), "une production sans location n'a que les libres, même si la liste n'est pas vide")
        assertFalse(PlayRules.reservedScopeAllowed(Actor.TV_TRIAL, setOf("tout"), "cm2"))
        assertFalse(PlayRules.reservedScopeAllowed(Actor.PUBLIC_LOBBY, setOf("tout"), "cm2"), "un salon public ne sert jamais de réservée")
    }

    @Test fun actorFollowsTheEditionTheServerEvaluated() {
        assertEquals(Actor.TV_PROD, PlayRules.actorOf(HostEdition.PROD, hasCoveredScopes = false))
        assertEquals(Actor.TV_PROD_RENTAL, PlayRules.actorOf(HostEdition.PROD, hasCoveredScopes = true))
        assertEquals(Actor.TV_TRIAL, PlayRules.actorOf(HostEdition.TRIAL, hasCoveredScopes = false))
        assertEquals(Actor.TV_GRACE, PlayRules.actorOf(HostEdition.GRACE, hasCoveredScopes = false))
        assertEquals(Actor.TV_LOCKED, PlayRules.actorOf(HostEdition.NONE, hasCoveredScopes = true))
        assertEquals(Actor.TV_CLOCK_DOUBT, PlayRules.actorOf(HostEdition.PROD, hasCoveredScopes = true, clockDoubt = true))
    }

    @Test fun childProfilesNeverGoToTheInternet() {
        val v = PlayRules.internetForProfile(childProfile = true)
        assertFalse(v.allowed); assertEquals(PlayReason.PLAY_SCOPE_FORBIDDEN, v.reason); assertTrue("contrôle parental" in v.message)
        assertTrue(PlayRules.internetForProfile(childProfile = false).allowed)
    }
}
