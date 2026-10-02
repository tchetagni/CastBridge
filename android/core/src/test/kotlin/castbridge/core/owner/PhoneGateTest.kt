package castbridge.core.owner

import kotlin.test.*

class PhoneGateTest {
    private val day = 24L * 3600 * 1000
    private val now = 1_800_000_000_000L
    private val on = ProofRequirement(required = true, graceDays = 14)
    private fun proof(until: Long, endsAt: Long? = null) = ProofSummary("AAAA-BBBB", "Salon", now - day, until, endsAt)
    private val validProof get() = proof(now + 5 * day)
    private val expiredProof get() = proof(now - 1)
    private fun st(proofs: List<ProofSummary> = emptyList(), mig: FleetMigration? = null, sup: Boolean = false, agent: Boolean = false, req: ProofRequirement = on, t: Long = now) =
        PhoneGate.state(req, proofs, t, mig, sup, agent)

    @Test fun theMinimalSurfaceIsExactlyTheListedOne() {
        val expected = setOf(
            PhoneFeature.USAGE_NOTICE, PhoneFeature.PRIVACY_SCREEN, PhoneFeature.DISPLAY_LANGUAGE, PhoneFeature.TV_PAIRING, PhoneFeature.SHARE_DEVICE_CODE,
            PhoneFeature.CARRY_ACTIVATION_FOR_TV, PhoneFeature.FREE_CONTENT_DOWNLOAD, PhoneFeature.CAST_TO_LINKED_TV, PhoneFeature.LEARN_REMOTE, PhoneFeature.TRIAL_LOTS_SYNC,
            PhoneFeature.INTERNET_GATEWAY_FOR_TV, PhoneFeature.TELEMETRY_CONSENT, PhoneFeature.HELP, PhoneFeature.UPDATES_PHONE, PhoneFeature.SUPER_ADMIN_ENTRY,
            PhoneFeature.FOCAL_ENTRY, PhoneFeature.SHOP_BROWSE
        )
        assertEquals(expected, MINIMAL_WHITELIST)
        assertEquals(17, MINIMAL_WHITELIST.size)
    }

    @Test fun theAgentSurfaceIsExactlyTheListedOne() {
        val expected = MINIMAL_WHITELIST + setOf(PhoneFeature.AGENT_SELL_KEYS, PhoneFeature.AGENT_SELL_VOUCHERS, PhoneFeature.AGENT_CONFIRM_ORDERS, PhoneFeature.AGENT_READ_TV_REQUEST)
        assertEquals(expected, AGENT_WHITELIST)
    }

    @Test fun theClosedSurfaceIsExactlyTheListedOne() {
        val closed = PhoneFeature.values().filter { !it.minimalAllowed && !it.agentAllowed }.toSet()
        assertEquals(setOf(
            PhoneFeature.PHONE_LIBRARY_PLAYER, PhoneFeature.SEND_FILES_TO_TV, PhoneFeature.TV_LIBRARY_BROWSE, PhoneFeature.TV_LIBRARY_MANAGE, PhoneFeature.TV_ADMIN,
            PhoneFeature.LEARN_PHONE, PhoneFeature.QUIZ_PHONE, PhoneFeature.CHESS_PHONE, PhoneFeature.GAMES_PHONE, PhoneFeature.DOWNLOADS, PhoneFeature.LOTS_SYNC_FULL,
            PhoneFeature.SHOP_ORDER, PhoneFeature.TOKENS, PhoneFeature.PARENTAL_DASHBOARD, PhoneFeature.PARENTAL_RULES, PhoneFeature.REMOTE_TUNNEL_GATEWAY,
            PhoneFeature.ASSISTANT_IA, PhoneFeature.TRANSFER_MULTIPATH
        ), closed)
    }

    @Test fun theTvFacingFunctionsAreExactlyTheListedOnes() {
        assertEquals(setOf(
            PhoneFeature.CAST_TO_LINKED_TV, PhoneFeature.LEARN_REMOTE, PhoneFeature.TRIAL_LOTS_SYNC, PhoneFeature.INTERNET_GATEWAY_FOR_TV, PhoneFeature.SEND_FILES_TO_TV,
            PhoneFeature.TV_LIBRARY_BROWSE, PhoneFeature.TV_LIBRARY_MANAGE, PhoneFeature.TV_ADMIN, PhoneFeature.LOTS_SYNC_FULL, PhoneFeature.PARENTAL_RULES,
            PhoneFeature.REMOTE_TUNNEL_GATEWAY, PhoneFeature.TRANSFER_MULTIPATH
        ), PhoneFeature.values().filter { it.tvFacing }.toSet())
    }

    @Test fun nothingIsRequiredWhenTheSwitchIsOff() {
        assertEquals(PhoneGateState.NotRequired, st(req = ProofRequirement()))
        PhoneFeature.values().forEach { assertTrue(PhoneGate.canUse(it, PhoneGateState.NotRequired)) }
    }

    @Test fun stateOrderIsSuperThenLinkedThenAgentThenGraceThenMinimal() {
        val mig = FleetMigration(true, now - day)
        assertEquals(PhoneGateState.Super, st(listOf(validProof), mig, sup = true, agent = true))
        assertIs<PhoneGateState.Linked>(st(listOf(validProof), mig, agent = true))
        assertEquals(PhoneGateState.Agent, st(emptyList(), mig, agent = true))
        assertIs<PhoneGateState.Grace>(st(emptyList(), mig))
        assertEquals(PhoneGateState.Minimal(MinimalReason.NO_PROOF), st(emptyList(), null))
    }

    @Test fun agentWithoutAnyProofIsAgentNotMinimal() = assertEquals(PhoneGateState.Agent, st(emptyList(), null, agent = true))

    @Test fun superWithoutProofIsSuper() = assertEquals(PhoneGateState.Super, st(emptyList(), null, sup = true))

    @Test fun linkedKeepsOnlyTheValidProofs() {
        val s = st(listOf(validProof, expiredProof))
        assertEquals(listOf(validProof), (s as PhoneGateState.Linked).proofs)
    }

    @Test fun graceIsAbsoluteAndEndsAtGraceStartPlusDays() {
        val start = now - 10 * day
        val mig = FleetMigration.of(firstInstallTimeMs = start - day, lockGraceStartMs = start)
        assertEquals(PhoneGateState.Grace(start + 14 * day), st(mig = mig))
        assertEquals(PhoneGateState.Minimal(MinimalReason.NO_PROOF), st(mig = mig, t = start + 14 * day)) // the end instant is already locked
    }

    @Test fun reinstallDoesNotRestartTheGrace() {
        val start = now - 20 * day
        // a reinstall after the lock: first-install time later than the grace start => no grace
        val mig = FleetMigration.of(firstInstallTimeMs = now - day, lockGraceStartMs = start)
        assertEquals(PhoneGateState.Minimal(MinimalReason.NO_PROOF), st(mig = mig))
        // an install whose clock file is gone (date wound back) gets none either
        assertEquals(PhoneGateState.Minimal(MinimalReason.NO_PROOF), st(mig = FleetMigration.of(start - day, start, clockFileExistedAtStart = false), t = start + day))
    }

    @Test fun graceDaysZeroIsNeverGrace() {
        val mig = FleetMigration(true, now - day)
        assertEquals(PhoneGateState.Minimal(MinimalReason.NO_PROOF), st(mig = mig, req = ProofRequirement(true, 0)))
    }

    @Test fun anExpiredProofGivesMinimalWithTheExpiredReason() {
        assertEquals(PhoneGateState.Minimal(MinimalReason.PROOF_EXPIRED), st(listOf(expiredProof)))
    }

    @Test fun aProofStopsAtEndsAtEvenWhenItsCacheIsStillValid() {
        val p = proof(until = now + 10 * day, endsAt = now + day)
        assertTrue(p.validAt(now + day - 1))
        assertFalse(p.validAt(now + day))
        assertFalse(p.validAt(now + 2 * day))
        assertEquals(PhoneGateState.Minimal(MinimalReason.PROOF_EXPIRED), st(listOf(p), t = now + 2 * day))
    }

    @Test fun aWoundBackClockDoesNotResurrectAProofPastItsValidity() {
        val p = proof(until = now - 1)
        assertFalse(p.validAt(now))
        assertFalse(p.validAt(now + 5)) // a later reading is just as dead
        assertTrue(p.validAt(now - 2))   // the comparison is plain: monotonic time is the caller's job (w6-03)
    }

    @Test fun canUseFollowsTheState() {
        for (f in PhoneFeature.values()) {
            assertEquals(f.minimalAllowed, PhoneGate.canUse(f, PhoneGateState.Minimal(MinimalReason.NO_PROOF)), "minimal $f")
            assertEquals(f.agentAllowed, PhoneGate.canUse(f, PhoneGateState.Agent), "agent $f")
            assertTrue(PhoneGate.canUse(f, PhoneGateState.Linked(listOf(validProof))), "linked $f")
            assertTrue(PhoneGate.canUse(f, PhoneGateState.Super), "super $f")
            assertTrue(PhoneGate.canUse(f, PhoneGateState.Grace(now)), "grace $f")
        }
    }

    @Test fun minimalClosesTheValueFunctionsAndOpensTheActivationSurface() {
        val m = PhoneGateState.Minimal(MinimalReason.NO_PROOF)
        assertFalse(PhoneGate.canUse(PhoneFeature.PHONE_LIBRARY_PLAYER, m))
        assertFalse(PhoneGate.canUse(PhoneFeature.SEND_FILES_TO_TV, m))
        assertFalse(PhoneGate.canUse(PhoneFeature.PARENTAL_DASHBOARD, m))
        assertTrue(PhoneGate.canUse(PhoneFeature.FREE_CONTENT_DOWNLOAD, m))
        assertTrue(PhoneGate.canUse(PhoneFeature.CARRY_ACTIVATION_FOR_TV, m))
        assertFalse(PhoneGate.canUse(PhoneFeature.AGENT_SELL_KEYS, m))
    }

    @Test fun agentNeverGetsThePlayerNorTheParental() {
        assertFalse(PhoneGate.canUse(PhoneFeature.PHONE_LIBRARY_PLAYER, PhoneGateState.Agent))
        assertFalse(PhoneGate.canUse(PhoneFeature.PARENTAL_DASHBOARD, PhoneGateState.Agent))
        assertTrue(PhoneGate.canUse(PhoneFeature.AGENT_SELL_KEYS, PhoneGateState.Agent))
    }

    @Test fun proofRequirementRejectsAbsurdGraceDays() {
        assertFailsWith<IllegalArgumentException> { ProofRequirement(true, -1) }
        assertFailsWith<IllegalArgumentException> { ProofRequirement(true, 366) }
    }

    @Test fun messageIdsAreUniqueAndStable() {
        val ids = listOf(PhoneMessages.NO_TV, PhoneMessages.SYNC_FIRST, PhoneMessages.SYNCING, PhoneMessages.TV_TRIAL, PhoneMessages.TV_GRACE, PhoneMessages.TV_LOCKED,
            PhoneMessages.TV_ENDED, PhoneMessages.TV_SUSPENDED, PhoneMessages.PROOF_EXPIRED, PhoneMessages.PROOF_STALE, PhoneMessages.TV_UNREACHABLE, PhoneMessages.BT_OFF,
            PhoneMessages.WRONG_NETWORK, PhoneMessages.CLOCK_DOUBT, PhoneMessages.PROOF_REJECTED, PhoneMessages.IDENTITY_CHANGED, PhoneMessages.TV_OLD_VERSION,
            PhoneMessages.TV_REFUSED, PhoneMessages.PARENTAL_BLOCKED, PhoneMessages.TRIAL_LOTS_ONLY, PhoneMessages.SHOP_READ_ONLY)
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.all { it.startsWith("M-") })
        assertEquals("M-TV-TRIAL", PhoneMessages.TV_TRIAL)
    }
}
