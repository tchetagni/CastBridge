package castbridge.core.policy

import castbridge.core.lots.MemoryQueueStore
import castbridge.core.lots.Access
import castbridge.core.owner.*
import kotlin.test.*

class PolicyEngineTest {
    private val k = PolicyKit()
    private val e get() = k.engine

    // ---- the happy path and the closed list ----
    @Test fun anOrderIsAppliedAndAcknowledged() {
        val a = e.receive(k.order(PolicyActions.FLAG_SET, mapOf("name" to "learn.beta", "value" to "1"), seq = 1))
        assertEquals(AckReason.APPLIED, a.reason); assertEquals(1, a.policyVersion)
        assertTrue(e.current.flag("learn.beta")); assertEquals(1, e.journal().size)
    }

    @Test fun everyActionOfTheListWorks() {
        var seq = 0L
        fun go(action: String, vararg p: Pair<String, String>) = e.receive(k.order(action, p.toMap(), seq = ++seq)).also { assertEquals(AckReason.APPLIED, it.reason, action) }
        go(PolicyActions.LICENSE_SUSPEND, "license" to "lic-1"); assertEquals(LicenseMode.SUSPENDED, e.current.mode("lic-1"))
        go(PolicyActions.LICENSE_REVOKE, "license" to "lic-1"); assertEquals(LicenseMode.REVOKED, e.current.mode("lic-1"))
        go(PolicyActions.LICENSE_ACTIVATE, "license" to "lic-1"); assertEquals(LicenseMode.ACTIVE, e.current.mode("lic-1"))
        go(PolicyActions.LICENSE_EXTEND, "license" to "lic-1", "until" to (NOW0 + 100 * DAY).toString()); assertEquals(NOW0 + 100 * DAY, e.current.extensions["lic-1"])
        go(PolicyActions.REVOCATION_ADD, "kid" to "0123456789abcdef"); assertTrue("0123456789abcdef" in e.current.revocations.keys)
        go(PolicyActions.REVOCATION_ADD, "license" to "lic-1", "seat" to "00112233aabbccdd", "at" to NOW0.toString()); assertEquals(NOW0, e.current.revocations.seats["lic-1|00112233aabbccdd"])
        go(PolicyActions.RIGHTS_REFRESH, "reason" to "periodic"); assertTrue(PolicyGate.refreshWanted(e.current, 0))
        go(PolicyActions.APP_MIN_VERSION, "version" to "42"); assertEquals(42, e.current.minVersion)
        go(PolicyActions.UPDATE_CHANNEL, "channel" to "beta"); assertEquals("beta", e.current.channel)
        go(PolicyActions.CATALOG_AVAILABLE, "lots" to "learn:cm2,quiz:cm2"); assertEquals(setOf("learn:cm2", "quiz:cm2"), e.current.available)
        go(PolicyActions.CATALOG_RETIRE, "lots" to "quiz:cm2"); assertEquals(setOf("quiz:cm2"), e.current.retired); assertEquals(setOf("learn:cm2"), e.current.available)
        go(PolicyActions.BUDGET_SET, "name" to "lots_mb", "value" to "500"); assertEquals(500L, e.current.budgets["lots_mb"])
        go(PolicyActions.MESSAGE_SHOW, "id" to "hello", "text" to "Une mise à jour est conseillée."); assertEquals(1, e.current.activeMessages(NOW0).size)
        go(PolicyActions.MESSAGE_CLEAR, "id" to "hello"); assertTrue(e.current.messages.isEmpty())
        assertEquals(seq, e.current.version); assertEquals(PolicyActions.ALL.size, seq.toInt())
    }

    @Test fun anActionOutsideTheWhitelistIsRefusedEvenSigned() {
        var seq = 0L
        for (bad in PolicyActions.NEVER) {
            val a = e.receive(k.order(bad, mapOf("path" to "/sdcard"), seq = ++seq))
            assertEquals(AckReason.UNKNOWN_ACTION, a.reason, bad)
        }
        assertEquals(PolicyState().toJson(), e.current.toJson(), "nothing changed")
        // the closed list itself contains no destructive or remote-access verb
        assertTrue(PolicyActions.ALL.none { id -> listOf("delete", "wipe", "exec", "shell", "remote", "file", "ssh", "uninstall", "reset").any { it in id } })
    }

    @Test fun unlistedParametersAndFlagsAreRefused() {
        assertEquals(AckReason.BAD_PARAMS, e.receive(k.order(PolicyActions.FLAG_SET, mapOf("name" to "update.verify", "value" to "0"), seq = 1)).reason, "no flag for the signed update check")
        assertEquals(AckReason.BAD_PARAMS, e.receive(k.order(PolicyActions.FLAG_SET, mapOf("name" to "learn.beta", "value" to "1", "extra" to "x"), seq = 2)).reason)
        assertEquals(AckReason.BAD_PARAMS, e.receive(k.order(PolicyActions.LICENSE_SUSPEND, mapOf("license" to "Lic 1"), seq = 3)).reason)
        assertEquals(AckReason.BAD_PARAMS, e.receive(k.order(PolicyActions.UPDATE_CHANNEL, mapOf("channel" to "http://evil"), seq = 4)).reason)
        assertEquals(AckReason.BAD_PARAMS, e.receive(k.order(PolicyActions.MESSAGE_SHOW, mapOf("id" to "m", "text" to "Allez sur http://evil.example"), seq = 5)).reason, "a message is plain text, never a link")
        assertEquals(AckReason.BAD_PARAMS, e.receive(k.order(PolicyActions.BUDGET_SET, mapOf("name" to "lots_mb", "value" to "999999999"), seq = 6)).reason)
        assertEquals(AckReason.BAD_PARAMS, e.receive(k.order(PolicyActions.LICENSE_EXTEND, mapOf("license" to "lic-1", "until" to (NOW0 + 900 * DAY).toString()), seq = 7)).reason, "no extension to 'forever'")
        assertEquals(0, e.current.version)
    }

    // ---- idempotence, replay, sequence ----
    @Test fun replayIsRefusedAndTheSameTokenGetsTheSameAckWithoutBeingAppliedTwice() {
        val t = k.order(PolicyActions.BUDGET_SET, mapOf("name" to "lots_mb", "value" to "10"), seq = 5)
        val a1 = e.receive(t); val a2 = e.receive(t)
        assertEquals(a1, a2); assertEquals(1, e.current.version, "not applied twice"); assertEquals(1, e.journal().size)
        // a different token with the same sequence number is a replay, not an application
        val other = k.order(PolicyActions.BUDGET_SET, mapOf("name" to "lots_mb", "value" to "20"), seq = 5)
        assertEquals(AckReason.STALE_SEQUENCE, e.receive(other).reason); assertEquals(10L, e.current.budgets["lots_mb"])
    }

    @Test fun lateSequenceRefusedGapAcceptedAndEachKeyHasItsOwnCounter() {
        assertEquals(AckReason.APPLIED, e.receive(k.order(PolicyActions.RIGHTS_REFRESH, seq = 10)).reason)
        assertEquals(AckReason.STALE_SEQUENCE, e.receive(k.order(PolicyActions.APP_MIN_VERSION, mapOf("version" to "1"), seq = 9)).reason, "rollback")
        assertEquals(AckReason.APPLIED, e.receive(k.order(PolicyActions.APP_MIN_VERSION, mapOf("version" to "2"), seq = 500)).reason, "a gap is fine")
        assertEquals(AckReason.APPLIED, e.receive(k.order(PolicyActions.APP_MIN_VERSION, mapOf("version" to "3"), seq = 1, signer = k.desk)).reason, "the desk key has its own counter")
        assertEquals(3, e.current.minVersion)
    }

    @Test fun absoluteActionsAreIdempotent() {
        e.receive(k.order(PolicyActions.LICENSE_SUSPEND, mapOf("license" to "lic-1"), seq = 1)); val s1 = e.current
        e.receive(k.order(PolicyActions.LICENSE_SUSPEND, mapOf("license" to "lic-1"), seq = 2))
        assertEquals(s1.copy(version = 2).toJson(), e.current.toJson())
    }

    // ---- keys, scopes, targets ----
    @Test fun unknownRevokedAndOutOfScopeKeys() {
        assertEquals(AckReason.UNKNOWN_KEY, e.receive(k.order(PolicyActions.RIGHTS_REFRESH, seq = 1, signer = k.rogue)).reason)
        // a key that exists but has no policy scope (a support key)
        val noPolicy = Ed25519Signer(ByteArray(32) { 9 })
        val engine = PolicyEngine(KeyRing(listOf(noPolicy.trusted(setOf(KeyScope.COMMAND_SUPPORT)))), PolicyStorage(MemoryQueueStore()), { k.ctx }, { NOW0 })
        assertEquals(AckReason.KEY_NOT_ALLOWED, engine.receive(k.order(PolicyActions.RIGHTS_REFRESH, seq = 1, signer = noPolicy)).reason)
        // revoked by the ring
        val revoked = PolicyEngine(KeyRing(listOf(k.server.trusted(k.serverScopes)), setOf(k.server.keyId)), PolicyStorage(MemoryQueueStore()), { k.ctx }, { NOW0 })
        assertEquals(AckReason.REVOKED_KEY, revoked.receive(k.order(PolicyActions.RIGHTS_REFRESH, seq = 1)).reason)
    }

    @Test fun anOrderNeverGrantsMoreThanItsKeyScope() {
        // policy-only key: may flip a flag, may NOT extend a right (issue scope) nor revoke (revoke scope)
        assertEquals(AckReason.APPLIED, e.receive(k.order(PolicyActions.FLAG_SET, mapOf("name" to "bt.tunnel", "value" to "1"), seq = 1, signer = k.weak)).reason)
        assertEquals(AckReason.SCOPE_EXCEEDED, e.receive(k.order(PolicyActions.LICENSE_EXTEND, mapOf("license" to "lic-1", "until" to (NOW0 + DAY).toString()), seq = 2, signer = k.weak)).reason)
        assertEquals(AckReason.SCOPE_EXCEEDED, e.receive(k.order(PolicyActions.REVOCATION_ADD, mapOf("kid" to k.desk.keyId), seq = 3, signer = k.weak)).reason)
        assertTrue(e.current.extensions.isEmpty() && e.current.revocations.keys.isEmpty())
    }

    @Test fun theServerKeyCannotRevokeTheOwnersMasterKey() {
        assertEquals(AckReason.SCOPE_EXCEEDED, e.receive(k.order(PolicyActions.REVOCATION_ADD, mapOf("kid" to k.desk.keyId), seq = 1)).reason, "the desk key is more powerful than the server key")
        assertEquals(AckReason.APPLIED, e.receive(k.order(PolicyActions.REVOCATION_ADD, mapOf("kid" to k.weak.keyId), seq = 2)).reason, "a less powerful key can be revoked")
        // and once revoked its orders stop working
        assertEquals(AckReason.REVOKED_KEY, e.receive(k.order(PolicyActions.RIGHTS_REFRESH, seq = 1, signer = k.weak)).reason)
        assertEquals(AckReason.APPLIED, e.receive(k.order(PolicyActions.REVOCATION_ADD, mapOf("kid" to k.desk.keyId), seq = 1, signer = k.desk)).reason, "the desk can revoke itself (equal power)")
    }

    @Test fun targets() {
        assertEquals(AckReason.WRONG_TARGET, e.receive(k.order(PolicyActions.RIGHTS_REFRESH, seq = 1, target = k.otherTarget)).reason, "another TV")
        assertEquals(AckReason.WRONG_TARGET, e.receive(k.order(PolicyActions.RIGHTS_REFRESH, seq = 2, target = Envelope.Target.License("lic-9"))).reason)
        assertEquals(AckReason.WRONG_TARGET, e.receive(k.order(PolicyActions.RIGHTS_REFRESH, seq = 3, target = Envelope.Target.Group("alpha"))).reason)
        assertEquals(AckReason.APPLIED, e.receive(k.order(PolicyActions.RIGHTS_REFRESH, seq = 4, target = Envelope.Target.License("lic-1"))).reason)
        assertEquals(AckReason.APPLIED, e.receive(k.order(PolicyActions.RIGHTS_REFRESH, seq = 5, target = Envelope.Target.Group("beta"))).reason)
        assertEquals(AckReason.APPLIED, e.receive(k.order(PolicyActions.RIGHTS_REFRESH, seq = 6, target = Envelope.Target.Any)).reason)
        assertEquals(3, e.current.version, "a refused order did not move the state; the following ones were applied")
    }

    // ---- time ----
    @Test fun expiredAndNotYetValid() {
        assertEquals(AckReason.WINDOW_CLOSED, e.receive(k.order(PolicyActions.RIGHTS_REFRESH, seq = 1, notBefore = NOW0 - 40 * DAY, expiresAt = NOW0 - DAY)).reason)
        assertEquals(AckReason.NOT_YET_VALID, e.receive(k.order(PolicyActions.RIGHTS_REFRESH, seq = 2, notBefore = NOW0 + 10 * DAY, expiresAt = NOW0 + 40 * DAY)).reason)
        // a refused order did not burn the sequence number: the same number is still usable
        assertEquals(AckReason.APPLIED, e.receive(k.order(PolicyActions.RIGHTS_REFRESH, seq = 2)).reason)
    }

    @Test fun aWrongClockSetBackNeverRevivesAnExpiredOrderAndIsJournalled() {
        k.wall.v = NOW0 + 60 * DAY; e.observeClock()
        val expired = k.order(PolicyActions.APP_MIN_VERSION, mapOf("version" to "7"), seq = 1, expiresAt = NOW0 + 30 * DAY)
        k.wall.v = NOW0                                              // the battery clock fell back 60 days
        val a = e.receive(expired)
        assertEquals(AckReason.WINDOW_CLOSED, a.reason, "the TV trusts the highest instant it has seen")
        assertTrue(e.journal().first().clockRolledBack)
        assertTrue(e.trustedNow() in (NOW0 + 60 * DAY)..(NOW0 + 60 * DAY + 3_600_000L), "time never goes back (it now advances with the monotonic time instead of freezing)")
    }

    @Test fun aWildlyAdvancedClockIsNotBelieved() {
        k.wall.v = NOW0; e.observeClock()
        k.wall.v = NOW0 + 3000 * DAY                                  // 8 years ahead: a glitch
        assertEquals(AckReason.APPLIED, e.receive(k.order(PolicyActions.RIGHTS_REFRESH, seq = 1, expiresAt = NOW0 + 30 * DAY)).reason)
    }

    @Test fun anEmptyClockOfTheTvStillSeesNotYetValidOrdersRefused() {
        k.wall.v = 0
        assertEquals(AckReason.NOT_YET_VALID, e.receive(k.order(PolicyActions.RIGHTS_REFRESH, seq = 1)).reason, "no signed floor for an order: refused until the clock is sane; nothing breaks")
    }

    // ---- junk, tampering, size ----
    @Test fun junkTamperedAndOversizedTokens() {
        assertEquals(AckReason.MALFORMED, e.receive("nope").reason)
        assertEquals(AckReason.TOO_LARGE, e.receive("cbx1." + "A".repeat(PolicyEngine.MAX_TOKEN)).reason)
        val t = k.order(PolicyActions.BUDGET_SET, mapOf("name" to "lots_mb", "value" to "10"), seq = 3)
        val p = t.split('.'); val body = String(java.util.Base64.getUrlDecoder().decode(p[1])).replace("value|10", "value|99")
        val forged = p[0] + "." + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(body.toByteArray()) + "." + p[2]
        assertEquals(AckReason.BAD_SIGNATURE, e.receive(forged).reason)
        // a forged copy must not poison the real order (same kid, seq and nonce)
        assertEquals(AckReason.APPLIED, e.receive(t).reason)
        assertEquals(10L, e.current.budgets["lots_mb"])
    }

    @Test fun anActivationPresentedAsAnOrderIsRefused() {
        val raw = Envelope("activation", k.server.keyId, 1, "00000000000000aa", NOW0, NOW0 - DAY, NOW0 + DAY, k.myTarget, listOf("kind=trial"), "")
        val act = raw.withSignature(java.util.Base64.getEncoder().encodeToString(k.server.sign(raw.canonicalPayload().toByteArray()))).encode()
        assertEquals(AckReason.UNKNOWN_TYPE, e.receive(act).reason)
    }

    // ---- an order refused does not stop the next ----
    @Test fun refusedThenFollowingOnesApplied() {
        val a1 = e.receive(k.order("format.disk", seq = 1))
        val a2 = e.receive(k.order(PolicyActions.RIGHTS_REFRESH, seq = 2, signer = k.rogue))
        val a3 = e.receive(k.order(PolicyActions.APP_MIN_VERSION, mapOf("version" to "5"), seq = 3))
        val a4 = e.receive(k.order(PolicyActions.FLAG_SET, mapOf("name" to "ui.new-home", "value" to "1"), seq = 4))
        assertEquals(listOf(AckReason.UNKNOWN_ACTION, AckReason.UNKNOWN_KEY, AckReason.APPLIED, AckReason.APPLIED), listOf(a1, a2, a3, a4).map { it.reason })
        assertEquals(5, e.current.minVersion); assertTrue(e.current.flag("ui.new-home"))
        assertEquals(4, e.journal().size)
    }

    // ---- persistence ----
    @Test fun stateSequencesClockAndJournalSurviveARestart() {
        k.wall.v = NOW0 + 5 * DAY
        e.receive(k.order(PolicyActions.LICENSE_SUSPEND, mapOf("license" to "lic-1"), seq = 7))
        val t = k.order(PolicyActions.RIGHTS_REFRESH, seq = 8); val a = e.receive(t)
        val again = k.engine(k.store)
        assertEquals(e.current.toJson(), again.current.toJson()); assertEquals(8, again.lastSeq(k.server.keyId)); assertEquals(2, again.journal().size)
        assertEquals(AckReason.STALE_SEQUENCE, again.receive(k.order(PolicyActions.RIGHTS_REFRESH, seq = 7)).reason, "anti-rollback survives the restart")
        assertEquals(a, again.receive(t), "the lost acknowledgement is given again after a restart")
        assertTrue(again.trustedNow() >= NOW0 + 5 * DAY)
        // a damaged file does not crash: the engine starts empty
        val broken = MemoryQueueStore("{not json"); assertEquals(0, k.engine(broken).current.version)
    }

    // ---- no brick, no data loss ----
    @Test fun suspensionOnlyLocksAndNothingIsDeleted() {
        val library = mutableMapOf("film.mkv" to 1, "photos" to 2)           // the user's data: the engine has no access to it at all
        val before = library.toMap()
        val access = TvAccess(true, Access.TRIAL_ONLY, null, "Activée")
        val base = GateState.Activated(access)
        val licenses = setOf("lic-1")
        assertEquals(base, PolicyGate.effective(base, e.current, licenses))
        e.receive(k.order(PolicyActions.LICENSE_SUSPEND, mapOf("license" to "lic-1"), seq = 1))
        val locked = PolicyGate.effective(base, e.current, licenses)
        assertEquals(GateState.Locked, locked)
        assertTrue(Feature.values().filter { FeatureGate.canUse(it, locked) }.toSet() == LOCKED_WHITELIST, "locked = the activation surface only")
        assertEquals(before, library, "no data touched"); assertTrue(FeatureGate.canUse(Feature.ACTIVATION_BLUETOOTH, locked), "activation stays possible")
        // a second licence still active keeps the app open; lifting the suspension restores everything
        assertEquals(base, PolicyGate.effective(base, e.current, setOf("lic-1", "lic-2")))
        e.receive(k.order(PolicyActions.LICENSE_ACTIVATE, mapOf("license" to "lic-1"), seq = 2))
        assertEquals(base, PolicyGate.effective(base, e.current, licenses))
        // when the requirement is off a policy suspension changes nothing
        e.receive(k.order(PolicyActions.LICENSE_SUSPEND, mapOf("license" to "lic-1"), seq = 3))
        assertEquals(GateState.NotRequired, PolicyGate.effective(GateState.NotRequired, e.current, licenses))
    }

    @Test fun noOrdersForAYearChangesNothing() {
        e.receive(k.order(PolicyActions.FLAG_SET, mapOf("name" to "learn.beta", "value" to "1"), seq = 1))
        val s = e.current
        k.wall.v = NOW0 + 365 * DAY; e.observeClock()
        assertEquals(s.toJson(), e.current.toJson(), "the last valid state is kept: a server outage locks nobody")
        assertEquals(s.toJson(), k.engine(k.store).current.toJson())
    }

    @Test fun extensionOnlyLengthensARightAndNeverCreatesOne() {
        e.receive(k.order(PolicyActions.LICENSE_EXTEND, mapOf("license" to "lic-1", "until" to (NOW0 + 50 * DAY).toString()), seq = 1))
        assertEquals(NOW0 + 50 * DAY, PolicyGate.effectiveEnd("lic-1", NOW0 + 10 * DAY, e.current))
        assertEquals(NOW0 + 90 * DAY, PolicyGate.effectiveEnd("lic-1", NOW0 + 90 * DAY, e.current), "never shortens")
        assertEquals(0L, PolicyGate.effectiveEnd("lic-1", 0L, e.current), "no right = nothing to extend")
        assertEquals(NOW0 + 10 * DAY, PolicyGate.effectiveEnd("lic-2", NOW0 + 10 * DAY, e.current))
    }

    @Test fun ackWireTextRoundTrips() {
        val a = e.receive(k.order(PolicyActions.RIGHTS_REFRESH, seq = 1))
        assertEquals(a, OrderAck.parse(a.toText())); assertNull(OrderAck.parse("kid=zz")); assertFalse(a.toText().contains("lic-"), "technical fields only")
    }
}
