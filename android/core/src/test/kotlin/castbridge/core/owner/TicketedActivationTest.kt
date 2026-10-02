package castbridge.core.owner

import castbridge.core.lots.Right
import castbridge.core.owner.AgentFixtures.DAY
import castbridge.core.owner.AgentFixtures.T0
import castbridge.core.owner.AgentFixtures.kid
import kotlin.test.*

class TicketedActivationTest {
    private val now = T0 + DAY + 3_600_000L
    private val ring = AgentFixtures.tvRing()
    private val tv = AgentFixtures.dev("tvA")
    private val mandate = AgentFixtures.delegation(bundles = listOf("tout"))
    private fun line(activation: String, d: String = mandate) = TicketedActivation.encode(d, activation)
    private fun check(l: String, r: KeyRing = ring, rev: RevocationState = RevocationState(), seq: SeqState = SeqState(), dev: AgentFixtures.Dev = tv) =
        DelegatedVerifier(ring = r, revocations = rev, seqState = seq).verify(l, dev.fp, now)
    private fun rejected(o: DelegatedVerifier.Outcome) = assertIs<ActivationResult.Rejected>(o.result)
    private val full = KeyScope.ALL

    @Test fun ticketedProductionKeyIsAcceptedAndNamesTheAgent() {
        val o = check(line(AgentFixtures.activation()))
        val a = assertIs<ActivationResult.Accepted>(o.result).activation
        assertEquals(kid("agent"), a.keyId); assertEquals("douala-akwa-01", o.delegation!!.name)
    }

    @Test fun ticketedTrialKeyIsAccepted() {
        val trial = AgentFixtures.activation(kind = ActivationKind.TRIAL, days = 30)
        assertIs<ActivationResult.Accepted>(check(line(trial)).result)
    }

    @Test fun anAgentNeverIssuesAnUnlimitedOrTooLongKey() {
        val unlimited = rejected(check(line(AgentFixtures.activation(rights = emptyList()))))
        assertEquals(Rejection.KEY_NOT_ALLOWED, unlimited.reason); assertContains(unlimited.message, "Le point focal n'est pas autorisé à délivrer ceci")
        val tooLong = rejected(check(line(AgentFixtures.activation(days = 366))))
        assertEquals(Rejection.KEY_NOT_ALLOWED, tooLong.reason)
        assertIs<ActivationResult.Accepted>(check(line(AgentFixtures.activation(days = 365))).result)       // the bound is inclusive
    }

    @Test fun anAgentNeverIssuesRentalsSuperOpenAllOrPurchases() {
        val rental = Right.Rental("loc-a", listOf("classe-cm2"), T0, T0, 30, 0L, 0, 0, "AAAA_box-0")
        val usage = Right.Usage(T0 + DAY, T0 + 31 * DAY)
        for ((name, r) in mapOf("rental" to rental, "essai" to Right.Rental("essai", listOf("tout"), T0, T0, 3, 0L, 720, 0, "AAAA_box-0"),
            "super" to Right.Super("super", T0), "openall" to Right.OpenAll("ouvert", T0, T0 + 10 * DAY), "purchase" to Right.Purchase("p-x", listOf("classe-cm2"), T0))) {
            val o = rejected(check(line(AgentFixtures.activation(rights = listOf(usage, r), issuerScopes = full))))
            assertEquals(Rejection.KEY_NOT_ALLOWED, o.reason, name)
        }
        val sub = Right.Subscription("abo", listOf("tout"), T0, T0 + 30 * DAY, 0L, false)
        assertEquals(Rejection.KEY_NOT_ALLOWED, rejected(check(line(AgentFixtures.activation(rights = listOf(usage, sub), issuerScopes = full)))).reason)
    }

    @Test fun theActivationMustBeSignedByTheAgentOfTheMandate() {
        val byOwner = ActivationIssuer(AgentFixtures.key("desk").signer).issue(ActivationIssuer.Request(ActivationKind.PRODUCTION, tv.code, tv.fp, T0 + DAY, license = "lic-0001", rights = listOf(Right.Usage(T0, T0 + 30 * DAY)))).token
        assertEquals(Rejection.KEY_NOT_ALLOWED, rejected(check(line(byOwner))).reason)
        val otherAgent = AgentFixtures.activation(agent = "agent2")
        assertEquals(Rejection.KEY_NOT_ALLOWED, rejected(check(line(otherAgent))).reason)
    }

    @Test fun mandateScopesBoundTheKindOfActivation() {
        val trialOnly = Delegation.issue(AgentFixtures.key("desk").signer, T0, T0, "a1a1a1a1", AgentFixtures.key("agent").signer.publicKeyBase64, "essais", 30, 5, listOf("tout"), scopes = setOf(KeyScope.ISSUE_TRIAL))
        assertEquals(Rejection.KEY_NOT_ALLOWED, rejected(check(line(AgentFixtures.activation(), trialOnly))).reason)
        assertIs<ActivationResult.Accepted>(check(line(AgentFixtures.activation(kind = ActivationKind.TRIAL, days = 30), trialOnly)).result)
    }

    @Test fun badMandatesAreRefusedWithTheirOwnReason() {
        val a = AgentFixtures.activation()
        assertEquals(Rejection.REVOKED_KEY, rejected(check(line(a), rev = RevocationState(keys = setOf(kid("agent"))))).reason)
        assertEquals(Rejection.UNKNOWN_KEY, rejected(check(line(a, AgentFixtures.delegation("rogue")))).reason)
        assertEquals(Rejection.KEY_NOT_ALLOWED, rejected(check(line(a, AgentFixtures.delegation("phone")))).reason)
        val expired = DelegatedVerifier(ring).verify(line(a), tv.fp, T0 + 200 * DAY)
        assertEquals(Rejection.WINDOW_CLOSED, rejected(expired).reason)
    }

    @Test fun theActivationKeepsItsOwnChecks() {
        val other = AgentFixtures.dev("tvN1")
        val o = rejected(check(line(AgentFixtures.activation()), dev = other))
        assertEquals(Rejection.WRONG_DEVICE, o.reason); assertTrue(o.suspect)
        assertNotNull(check(line(AgentFixtures.activation()), dev = other).delegation)                  // the mandate itself was fine
    }

    @Test fun aRefusedActivationDoesNotConsumeItsSequenceNumber() {
        val seq = SeqState()
        rejected(check(line(AgentFixtures.activation(rights = emptyList(), seq = 50)), seq = seq))
        assertEquals(0, seq.last(kid("agent")))
        assertIs<ActivationResult.Accepted>(check(line(AgentFixtures.activation(seq = 40)), seq = seq).result)
        assertEquals(40, seq.last(kid("agent")))
        assertEquals(Rejection.STALE_SEQUENCE, rejected(check(line(AgentFixtures.activation(seq = 39)), seq = seq)).reason)
    }

    @Test fun lineFormatIsExactlyTwoEnvelopesSeparatedByOneBar() {
        val a = AgentFixtures.activation()
        val l = line(a)
        assertEquals(mandate to a, TicketedActivation.split(l)); assertEquals(mandate to a, TicketedActivation.split("  $l \n"))
        assertTrue(TicketedActivation.isTicketed(l)); assertFalse(TicketedActivation.isTicketed(a)); assertFalse(TicketedActivation.isTicketed("abc|def"))
        assertNull(TicketedActivation.split(a)); assertNull(TicketedActivation.split("$l|$a")); assertNull(TicketedActivation.split("$mandate|texte")); assertNull(TicketedActivation.split("|$a"))
        assertEquals(Rejection.MALFORMED, rejected(check("$mandate|texte")).reason)
        assertEquals(Rejection.MALFORMED, rejected(check(a)).reason)
    }

    @Test fun anOldTvWithThePlainVerifierReadsATicketAsMalformed() {
        val old = ActivationVerifier(ring).verify(line(AgentFixtures.activation()), tv.fp, now)
        assertEquals(Rejection.MALFORMED, assertIs<ActivationResult.Rejected>(old).reason)
    }
}
