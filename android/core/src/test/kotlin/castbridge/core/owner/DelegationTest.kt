package castbridge.core.owner

import castbridge.core.owner.AgentFixtures.DAY
import castbridge.core.owner.AgentFixtures.T0
import castbridge.core.owner.AgentFixtures.key
import castbridge.core.owner.AgentFixtures.kid
import kotlin.test.*

class DelegationTest {
    private val ring = AgentFixtures.tvRing()
    private fun verify(token: String, now: Long = T0 + DAY, r: KeyRing = ring, rev: RevocationState = RevocationState(), seq: SeqState? = null) = Delegation.verify(token, r, rev, now, seq)
    private fun refusal(r: DelegationResult) = assertIs<DelegationResult.Refused>(r).reason
    private fun unchecked(owner: String = "desk", mutate: (Delegation) -> Delegation): String {
        val base = Delegation.decode(AgentFixtures.delegation(owner))!!
        return Delegation.sign(key(owner).signer, mutate(base))
    }

    @Test fun issuedDelegationVerifiesAndRoundTrips() {
        val token = AgentFixtures.delegation(bundles = listOf("tout"))
        val d = assertIs<DelegationResult.Accepted>(verify(token)).delegation
        assertEquals(kid("agent"), d.agent); assertEquals("douala-akwa-01", d.name); assertEquals(0, d.maxRentalDays); assertEquals(Delegation.ALLOWED_SCOPES, d.scopes)
        assertEquals(T0, d.notBefore); assertEquals(T0 + 90 * DAY, d.expiresAt)
        assertEquals(token, d.encode())
        assertEquals(d.agentKey().keyId, kid("agent")); assertEquals(T0..(T0 + 90 * DAY), d.agentKey().validity)
    }

    @Test fun canonicalBodyHasFixedOrderAndOptionalLinesOnlyWhenSet() {
        val plain = Envelope.decode(AgentFixtures.delegation())!!.body
        assertEquals(listOf("agent", "pub", "name", "scopes", "maxKeyDays", "maxRentalDays", "maxSales", "bundles"), plain.map { it.substringBefore('=') })
        assertEquals("scopes=ISSUE_PRODUCTION,ISSUE_TRIAL", plain[3]); assertEquals("maxRentalDays=0", plain[5])
        assertFalse(plain.any { it.startsWith("master=") || it.startsWith("agentx=") })
        val full = Envelope.decode(AgentFixtures.delegation(confirmOrders = true, sellVouchers = true, maxConfirm = 50_000))!!.body
        assertEquals(listOf("confirmOrders=1", "sellVouchers=1", "maxConfirmXafPerDay=50000"), full.drop(8))
        val d = assertIs<DelegationResult.Accepted>(verify(AgentFixtures.delegation(confirmOrders = true, sellVouchers = true, maxConfirm = 50_000))).delegation
        assertTrue(d.confirmOrders && d.sellVouchers); assertEquals(50_000, d.maxConfirmXafPerDay)
        assertEquals(listOf("classe-cm2", "maths"), Delegation.decode(AgentFixtures.delegation(bundles = listOf("maths", "classe-cm2")))!!.bundles)
    }

    @Test fun issueRefusesOutOfBoundsInput() {
        val pub = key("agent").signer.publicKeyBase64; val s = key("desk").signer
        fun bad(block: () -> Unit) { assertFailsWith<IssueException> { block() } }
        bad { Delegation.issue(s, T0, 1, "a1a1a1a1", pub, "Douala Akwa", 365, 10, listOf("tout")) }
        bad { Delegation.issue(s, T0, 1, "a1a1a1a1", pub, "ok", 0, 10, listOf("tout")) }
        bad { Delegation.issue(s, T0, 1, "a1a1a1a1", pub, "ok", 3661, 10, listOf("tout")) }
        bad { Delegation.issue(s, T0, 1, "a1a1a1a1", pub, "ok", 365, 0, listOf("tout")) }
        bad { Delegation.issue(s, T0, 1, "a1a1a1a1", pub, "ok", 365, 10001, listOf("tout")) }
        bad { Delegation.issue(s, T0, 1, "a1a1a1a1", pub, "ok", 365, 10, listOf("tout", "maths")) }
        bad { Delegation.issue(s, T0, 1, "a1a1a1a1", pub, "ok", 365, 10, listOf("tout"), validityDays = 181) }
        bad { Delegation.issue(s, T0, 1, "a1a1a1a1", pub, "ok", 365, 10, listOf("tout"), scopes = setOf(KeyScope.TRANSFER)) }
        bad { Delegation.issue(s, T0, 1, "a1a1a1a1", pub, "ok", 365, 10, listOf("tout"), maxConfirmXafPerDay = 1000) }
        bad { Delegation.issue(s, T0, 1, "zz", pub, "ok", 365, 10, listOf("tout")) }
        bad { Delegation.issue(s, T0, 1, "a1a1a1a1", "pas-base64!", "ok", 365, 10, listOf("tout")) }
        bad { Delegation.issue(s, T0, 1, "a1a1a1a1", s.publicKeyBase64, "ok", 365, 10, listOf("tout")) }       // the owner key cannot be its own agent
        Delegation.issue(s, T0, 1, "a1a1a1a1", pub, "a-0", 3660, 10000, listOf("tout"), validityDays = 180)   // bounds are inclusive
    }

    @Test fun refusalsFollowTheDocumentedOrder() {
        assertEquals(DelegationRefusal.MALFORMED, refusal(verify("cbx1.xx.yy")))
        assertEquals(DelegationRefusal.UNKNOWN_TYPE, refusal(verify(AgentFixtures.activation())))
        assertEquals(DelegationRefusal.UNKNOWN_KEY, refusal(verify(AgentFixtures.delegation("rogue"))))
        assertEquals(DelegationRefusal.REVOKED_KEY, refusal(verify(AgentFixtures.delegation(), r = AgentFixtures.tvRing(setOf(kid("desk"))))))
        assertEquals(DelegationRefusal.REVOKED_KEY, refusal(verify(AgentFixtures.delegation(), rev = RevocationState(keys = setOf(kid("agent"))))))
        val tampered = AgentFixtures.delegation().split('.').let { (a, b, c) -> "$a.$b.${c.reversed()}" }
        assertEquals(DelegationRefusal.BAD_SIGNATURE, refusal(verify(tampered)))
        assertEquals(DelegationRefusal.KEY_NOT_ALLOWED, refusal(verify(AgentFixtures.delegation("phone"))))                   // phone key: no DELEGATE scope
        assertEquals(DelegationRefusal.KEY_NOT_ALLOWED, refusal(verify(unchecked { it.copy(scopes = setOf(KeyScope.ISSUE_PRODUCTION, KeyScope.TRANSFER)) })))
        assertEquals(DelegationRefusal.BAD_DELEGATION, refusal(verify(unchecked { it.copy(maxRentalDays = 30) })))             // rentals are online: always 0
        assertEquals(DelegationRefusal.BAD_DELEGATION, refusal(verify(unchecked { it.copy(expiresAt = it.notBefore + 181 * DAY) })))
        assertEquals(DelegationRefusal.BAD_DELEGATION, refusal(verify(unchecked { it.copy(name = "Pas Bon") })))
        assertEquals(DelegationRefusal.BAD_DELEGATION, refusal(verify(unchecked { it.copy(maxConfirmXafPerDay = 100) })))      // cap without confirmOrders
        val seq = SeqState(mapOf("${kid("desk")}/${kid("agent")}" to T0 + 10))
        assertEquals(DelegationRefusal.STALE_SEQUENCE, refusal(verify(AgentFixtures.delegation(seq = T0), seq = seq)))
        assertEquals(DelegationRefusal.NOT_YET_VALID, refusal(verify(unchecked { d -> d.copy(issuedAt = T0, notBefore = T0 + 5 * DAY, expiresAt = T0 + 50 * DAY) }, now = T0)))
        assertEquals(DelegationRefusal.WINDOW_CLOSED, refusal(verify(AgentFixtures.delegation(), now = T0 + 91 * DAY)))
    }

    @Test fun agentKeyThatIsAlreadyACompiledKeyIsRefusedAndNeverOverridesIt() {
        val token = Delegation.issue(key("desk").signer, T0, T0, "a1a1a1a1", key("phone").signer.publicKeyBase64, "phone-agent", 30, 5, listOf("tout"))
        assertEquals(DelegationRefusal.BAD_DELEGATION, refusal(verify(token)))
        val fakeSameKid = TrustedKey(kid("phone"), key("phone").signer.publicKeyBase64, setOf(KeyScope.ISSUE_TRIAL))
        assertEquals(key("phone").scopes, ring.withDelegated(listOf(fakeSameKid)).find(kid("phone"))!!.scopes)
    }

    @Test fun acceptedSequenceIsRecordedPerOwnerKey() {
        val seq = SeqState()
        assertIs<DelegationResult.Accepted>(verify(AgentFixtures.delegation(seq = 5), seq = seq))
        assertEquals(5, seq.last("${kid("desk")}/${kid("agent")}"))
        assertEquals(DelegationRefusal.STALE_SEQUENCE, refusal(verify(AgentFixtures.delegation(seq = 4), seq = seq)))
        assertIs<DelegationResult.Accepted>(verify(AgentFixtures.delegation(seq = 5), seq = seq))                       // the same seq again: a renewal of the same content, not stale
    }

    @Test fun newerDelegationWinsByOwnerSeqThenByIssueDate() {
        fun d(owner: String, at: Long, seq: Long) = Delegation.decode(AgentFixtures.delegation(owner, at = at, seq = seq))!!
        val old = d("desk", T0, 1); val renewed = d("desk", T0 + DAY, 2)
        assertSame(renewed, Delegation.newer(old, renewed)); assertSame(renewed, Delegation.newer(renewed, old))
        val other = d("phone", T0 + 2 * DAY, 1)           // another owner key: the most recent issuedAt wins, whatever the seq
        assertSame(other, Delegation.newer(renewed, other)); assertSame(other, Delegation.newer(other, renewed))
        assertSame(old, Delegation.newer(old, old))
    }

    @Test fun replayKeysKeepAnExpiredMandateWithItsWindow() {
        val expired = AgentFixtures.delegation(at = T0 - 200 * DAY, validityDays = 90)
        val keys = Delegation.replayKeys(listOf(expired, "cbx1.garbage.x", AgentFixtures.delegation("rogue")), ring)
        assertEquals(1, keys.size); assertEquals((T0 - 200 * DAY)..(T0 - 110 * DAY), keys.single().validity)
        assertEquals(Delegation.ALLOWED_SCOPES, keys.single().scopes)
    }

    @Test fun sequenceIsKeptPerAgentNotPerOwnerKey() {
        val seq = SeqState()
        val a10 = AgentFixtures.delegation(agent = "agent", seq = 10); val b11 = AgentFixtures.delegation(agent = "agent2", seq = 11, name = "autre-agent", nonce = "c1c1c1c1c1c1c1c1")
        assertIs<DelegationResult.Accepted>(verify(a10, seq = seq))
        assertIs<DelegationResult.Accepted>(verify(b11, seq = seq))
        assertIs<DelegationResult.Accepted>(verify(AgentFixtures.delegation(agent = "agent", seq = 10), seq = seq))        // A is not stale because B went to 11
        assertEquals(DelegationRefusal.STALE_SEQUENCE, refusal(verify(AgentFixtures.delegation(agent = "agent", seq = 9), seq = seq)))
    }

    @Test fun notBeforeMustBePositive() {
        assertEquals(DelegationRefusal.BAD_DELEGATION, refusal(verify(unchecked { it.copy(notBefore = 0, expiresAt = DAY) })))
        assertEquals(DelegationRefusal.BAD_DELEGATION, refusal(verify(unchecked { it.copy(notBefore = -DAY, expiresAt = DAY) })))
    }

    @Test fun twoSuccessiveMandatesOfTheSameAgentBothReplayTheirOwnEvents() {
        val m1 = AgentFixtures.delegation(at = T0 - 200 * DAY, validityDays = 90, nonce = "a1a1a1a1a1a1a1a1")          // T0-200d .. T0-110d
        val m2 = AgentFixtures.delegation(at = T0, validityDays = 90, nonce = "a2a2a2a2a2a2a2a2")                      // T0 .. T0+90d
        val keys = Delegation.replayKeys(listOf(m1, m2), ring)
        assertEquals(2, keys.size)
        val r = ring.withDelegated(keys)
        val k = r.find(kid("agent"))!!
        assertTrue(k.validAt(T0 - 150 * DAY)); assertTrue(k.validAt(T0 + DAY)); assertFalse(k.validAt(T0 - 50 * DAY)); assertFalse(k.validAt(T0 + 100 * DAY))
        val signer = key("agent").signer
        val events = listOf(LicenseEvent.license(signer, T0 - 150 * DAY, "lic-old", 1), LicenseEvent.license(signer, T0 + DAY, "lic-new", 1), LicenseEvent.license(signer, T0 - 50 * DAY, "lic-gap", 1))
        val state = LicenseBook.replay(events, r)
        assertEquals(setOf("lic-old", "lic-new"), state.licenses.keys); assertEquals(1, state.rejected.size)
        assertEquals(Rejection.KEY_NOT_ALLOWED, state.rejected.single().second)
    }

    @Test fun replayKeepsAnAgentRevokedByACbr1ListRevoked() {
        val m = AgentFixtures.delegation(at = T0 - 10 * DAY)
        assertEquals(1, Delegation.replayKeys(listOf(m), ring).size)
        assertEquals(emptyList(), Delegation.replayKeys(listOf(m), ring, RevocationState(keys = setOf(kid("agent")))))
        val events = listOf(LicenseEvent.license(key("agent").signer, T0 - 5 * DAY, "lic-x", 1))
        val replayRing = ring.withDelegated(Delegation.replayKeys(listOf(m), ring, RevocationState(keys = setOf(kid("agent")))))
        assertTrue(LicenseBook.replay(events, replayRing).licenses.isEmpty())
    }
}
