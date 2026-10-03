package castbridge.play.entitlement

import castbridge.core.lots.ClockDoubt
import castbridge.core.owner.ActivationKind
import castbridge.core.owner.RevocationState
import castbridge.core.quiz.online.HostEdition
import castbridge.core.quiz.online.PlayReason
import castbridge.core.quiz.online.PlayRules
import castbridge.play.TestRights
import castbridge.play.TestRights.DAY
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Ce que le service conclut de `cbx1` + locations, avec SON horloge (DESIGN-W20 § 2.5 : l'API atteste l'appareil, `play` évalue les droits). */
class HostRightsTest {
    private val now = System.currentTimeMillis()
    private var revoked = RevocationState()
    private val ring = TrustedIssuers.parse(TestRights.trustedSpec).ring
    private val eval = HostRightsEvaluator(ring, { revoked })
    private fun rights(vararg tokens: String, code: String? = TestRights.CODE, at: Long = now, doubt: ClockDoubt? = null) = eval.evaluate(code, tokens.toList(), at, doubt)

    @Test fun productionWithoutRentalGetsTheFreeQuestionsOnly() {
        val r = rights(TestRights.activation(now = now))
        assertEquals(HostEdition.PROD, r.edition); assertTrue(r.coveredScopes.isEmpty()); assertTrue(r.publicAllowed)
        assertEquals(PlayRules.Actor.TV_PROD, r.actor)
    }

    @Test fun productionWithACm2RentalCoversCm2() {
        val a = TestRights.activation(now = now, rights = listOf(TestRights.rental("cm2", now - DAY)))
        val r = rights(a)
        assertEquals(HostEdition.PROD, r.edition); assertEquals(setOf("cm2"), r.coveredScopes)
        assertEquals(PlayRules.Actor.TV_PROD_RENTAL, r.actor)
    }

    @Test fun aRentalLineJoinedAsASecondSignedActivationAlsoCounts() {
        val prod = TestRights.activation(now = now)
        val rent = TestRights.activation(now = now, rights = listOf(TestRights.rental("3e", now - DAY)), seq = now + 1)
        assertEquals(setOf("3e"), rights(prod, rent).coveredScopes)
    }

    @Test fun anExpiredRentalFallsBackToFreeQuestionsWithTheFrenchNote() {
        val a = TestRights.activation(now = now, rights = listOf(TestRights.rental("cm2", now - 40 * DAY, days = 30)))
        val r = rights(a)
        assertEquals(HostEdition.PROD, r.edition); assertTrue(r.coveredScopes.isEmpty())
        assertEquals("Location terminée : questions libres", r.note)
    }

    @Test fun trialIsPrivateWithThreeGamesADayAndNoReservedQuestions() {
        val r = rights(TestRights.activation(ActivationKind.TRIAL, now = now, rights = TestRights.trialUsage(now)))
        assertEquals(HostEdition.TRIAL, r.edition); assertFalse(r.publicAllowed); assertEquals(3, r.maxGamesPerDay); assertTrue(r.coveredScopes.isEmpty())
        assertEquals(PlayRules.Actor.TV_TRIAL, r.actor)
    }

    @Test fun productionWinsOverTrialWhenBothAreJoined() {
        val trial = TestRights.activation(ActivationKind.TRIAL, now = now, rights = TestRights.trialUsage(now))
        val prod = TestRights.activation(now = now, seq = now + 5)
        assertEquals(HostEdition.PROD, rights(trial, prod).edition)
        assertEquals(HostEdition.PROD, rights(prod, trial).edition)
    }

    @Test fun anExpiredTrialIsNoRightAtAll() {
        val old = now - 40 * DAY
        val r = rights(TestRights.activation(ActivationKind.TRIAL, now = old, rights = listOf(castbridge.core.lots.Right.Usage(old, old + 30 * DAY)), issuedAt = old))
        assertEquals(HostEdition.NONE, r.edition)
    }

    @Test fun noActivationMeansNoRoomAndTheActivationText() {
        for (r in listOf(rights(), eval.evaluate(TestRights.CODE, emptyList(), now), eval.evaluate(null, listOf(TestRights.PROD), now))) {
            assertEquals(HostEdition.NONE, r.edition); assertEquals(PlayReason.PLAY_SCOPE_FORBIDDEN, r.reason)
            assertEquals("Activez la TV pour créer une partie Internet", PlayRules.canCreate(r.actor).message)
        }
    }

    @Test fun anActivationOfAnotherTvIsRefusedWhateverItsSignature() {
        val r = rights(TestRights.activation(device = TestRights.otherTv, now = now))
        assertEquals(HostEdition.NONE, r.edition); assertTrue("n'est pas celle de cette TV" in r.note, r.note)
        assertEquals(HostEdition.NONE, rights(TestRights.PROD, code = TestRights.otherTv.code).edition, "ticket d'un autre appareil, activation de la TV A")
    }

    @Test fun wrongKeyTamperedAndGarbageTokensGiveNothing() {
        assertEquals(HostEdition.NONE, rights(TestRights.activation(now = now, signer = TestRights.rogue)).edition, "clé inconnue du service")
        val t = TestRights.activation(now = now).split('.')
        assertEquals(HostEdition.NONE, rights(t[0] + "." + t[1].dropLast(2) + "AA" + "." + t[2]).edition, "charge altérée")
        for (g in listOf("", "cbx1.", "n'importe quoi", "x".repeat(20_000))) assertEquals(HostEdition.NONE, rights(g).edition)
    }

    @Test fun aRevokedKeyOrSeatBringsTheHostBackToNone() {
        val a = TestRights.activation(now = now)
        assertEquals(HostEdition.PROD, rights(a).edition)
        revoked = RevocationState(keys = setOf(TestRights.issuer.keyId))
        assertEquals(HostEdition.NONE, rights(a).edition, "clé révoquée")
        val act = castbridge.core.owner.Activation.decode(a)!!
        revoked = RevocationState(seats = mapOf("${act.license}|${act.seat}" to now))
        assertEquals(HostEdition.NONE, rights(a).edition, "poste révoqué")
    }

    @Test fun aDoubtfulClockRefusesWithTheCheckTheClockText() {
        val r = rights(TestRights.PROD, doubt = ClockDoubt.AHEAD)
        assertEquals(HostEdition.NONE, r.edition); assertEquals(PlayRules.Actor.TV_CLOCK_DOUBT, r.actor)
        assertTrue("Vérifiez l'heure de la TV" in r.note, r.note)
        val future = rights(TestRights.activation(now = now, issuedAt = now + 3 * DAY))
        assertEquals(HostEdition.NONE, future.edition, "une activation émise dans le futur du serveur : horloge douteuse (celle du serveur ou une activation forgée)")
        assertTrue("Vérifiez l'heure de la TV" in future.note)
    }

    @Test fun theNumberOfJoinedTokensIsBounded() {
        val many = List(40) { TestRights.activation(now = now, seq = now + it) }
        assertEquals(HostEdition.NONE, rights(*many.toTypedArray()).edition, "plus de ${HostRightsEvaluator.MAX_TOKENS} jetons : refus")
    }

    @Test fun trustedKeysParseFailClosed() {
        val ok = TrustedIssuers.parse(TestRights.trustedSpec)
        assertEquals(1, ok.ring.let { listOf(it.find(TestRights.issuer.keyId)).filterNotNull().size })
        val noScopes = TrustedIssuers.parse("desk:${TestRights.issuer.publicKeyBase64}")
        assertEquals(null, noScopes.ring.find(TestRights.issuer.keyId), "une entrée sans portées est ignorée")
        assertTrue(noScopes.warnings.isNotEmpty())
        val unknownScope = TrustedIssuers.parse("desk:${TestRights.issuer.publicKeyBase64}:NOPE")
        assertEquals(null, unknownScope.ring.find(TestRights.issuer.keyId))
        assertEquals(null, TrustedIssuers.parse("").ring.find(TestRights.issuer.keyId))
    }
}
