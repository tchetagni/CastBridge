package castbridge.core.owner

import castbridge.core.lots.Right
import java.security.MessageDigest
import kotlin.test.*

private const val DAY = 24L * 3600 * 1000
private const val NOW = 1_800_000_000_000L

private fun seed(n: String) = MessageDigest.getInstance("SHA-256").digest("licence-test|$n".toByteArray())
private val desk = Ed25519Signer(seed("desk"))
private val phone = Ed25519Signer(seed("phone"))
private val server = Ed25519Signer(seed("server"))
private val serverScopes = setOf(KeyScope.ISSUE_TRIAL, KeyScope.ISSUE_PRODUCTION, KeyScope.REVOKE, KeyScope.REGISTRY)
private val ring = KeyRing(listOf(desk.trusted(), phone.trusted(KeyScope.ALL - KeyScope.REGISTRY), server.trusted(serverScopes)))

private fun tv(name: String, wifi: String = "10:20:30:40:50:60") = DeviceIdentity.fingerprints(RawFactors("FLASH-$name", "cid-$name", "AA:BB:CC:00:11:${name.hashCode().and(0xff).toString(16).padStart(2, '0')}", wifi,
    "/sys/devices/platform/soc/x.sd/mmc_host/mmc1/net/wlan0", "SYS-$name", "11:22:33:44:55:66"))
private val purchase = Right.Purchase("p-classe-cm2", listOf("classe-cm2"), NOW - DAY)

private fun issue(s: Signer, fp: Fingerprints, license: String, at: Long = NOW, seat: String? = null, subject: Subject = Subject.TV, scopes: Set<KeyScope> = KeyScope.ALL, nonce: String = "00".repeat(8) + at.toString(16).padStart(8, '0')): Activation =
    ActivationIssuer(s, scopes).issue(ActivationIssuer.Request(ActivationKind.PRODUCTION, DeviceCode.of(fp), fp, at, subject, listOf(purchase), license, seat, at - 3_600_000L, 48, nonce)).activation

class LicenseTest {
    private fun licence(id: String = "lic-1", seats: Int = 2, cap: Int = 2) = LicenseEvent.license(desk, NOW - 10 * DAY, id, seats, cap)

    @Test fun reactivationOfTheSameHardwareReusesItsSeatWhicheverToolAnswers() {
        val fp = tv("A")
        val a1 = issue(desk, fp, "lic-1")
        var st = LicenseBook.replay(listOf(licence(), LicenseEvent.issue(desk, a1)), ring)
        assertEquals(1, st.usedSeats("lic-1")); assertEquals(1, st.seatsLeft("lic-1"))
        // reinstalled app, same hardware: the seat is reused (also with a replaced module: k of n)
        val again = LicenseBook.plan(st, "lic-1", Subject.TV, fp)
        assertEquals(a1.seat, assertIs<Plan.Reuse>(again).seat.seatId)
        assertIs<Plan.Reuse>(LicenseBook.plan(st, "lic-1", Subject.TV, tv("A", wifi = "99:99:99:99:99:99")))
        // another tool re-issues the SAME seat: no seat consumed, two tools recorded
        val a2 = issue(phone, fp, "lic-1", at = NOW + DAY, seat = a1.seat, nonce = "11".repeat(8))
        st = LicenseBook.replay(listOf(licence(), LicenseEvent.issue(desk, a1), LicenseEvent.issue(phone, a2)), ring)
        assertEquals(1, st.usedSeats("lic-1")); assertEquals(setOf(desk.keyId, phone.keyId), st.seats.values.single().issuedBy)
    }

    @Test fun aNewHardwareTakesASeatUntilThereIsNoneLeft() {
        val st = LicenseBook.replay(listOf(licence(seats = 2), LicenseEvent.issue(desk, issue(desk, tv("A"), "lic-1"))), ring)
        val p = assertIs<Plan.NewSeat>(LicenseBook.plan(st, "lic-1", Subject.TV, tv("B")))
        assertEquals(0, p.seatsLeftAfter); assertEquals(SeatIds.of("lic-1", tv("B")), p.seatId)
        val full = LicenseBook.replay(listOf(licence(seats = 2), LicenseEvent.issue(desk, issue(desk, tv("A"), "lic-1")), LicenseEvent.issue(desk, issue(desk, tv("B"), "lic-1"))), ring)
        assertEquals(Rejection.NO_SEAT_LEFT, assertIs<Plan.Refused>(LicenseBook.plan(full, "lic-1", Subject.TV, tv("C"))).reason)
        assertEquals(Rejection.UNKNOWN_LICENSE, assertIs<Plan.Refused>(LicenseBook.plan(full, "nope", Subject.TV, tv("C"))).reason)
        assertIs<Plan.Reuse>(LicenseBook.plan(full, "lic-1", Subject.TV, tv("B")))
    }

    @Test fun aPhoneAndATvAreSeparateSeats() {
        val fp = tv("A")
        val st = LicenseBook.replay(listOf(licence(seats = 3), LicenseEvent.issue(desk, issue(desk, fp, "lic-1"))), ring)
        assertIs<Plan.NewSeat>(LicenseBook.plan(st, "lic-1", Subject.PHONE, fp))
    }

    @Test fun trialKeysAreLoggedButNeverCountAsSeats() {
        val fp = tv("T")
        val trial = ActivationIssuer(desk).issue(ActivationIssuer.Request(ActivationKind.TRIAL, DeviceCode.of(fp), fp, NOW, windowHours = 48)).activation
        val st = LicenseBook.replay(listOf(LicenseEvent.issue(desk, trial)), ring)
        assertEquals(0, st.seats.size); assertTrue(st.rejected.isEmpty())
    }

    @Test fun twoToolsIssuingTheSameHardwareUnderDifferentSeatIdsAreDetectedAndCountedOnce() {
        val fp = tv("A"); val swapped = tv("A", wifi = "99:99:99:99:99:99")       // the second tool saw the TV after a Wi-Fi module change: another set, another default seat id
        val a1 = issue(desk, fp, "lic-1"); val a2 = issue(phone, swapped, "lic-1", at = NOW + DAY)
        assertNotEquals(a1.seat, a2.seat)
        val st = LicenseBook.replay(listOf(licence(), LicenseEvent.issue(desk, a1), LicenseEvent.issue(phone, a2)), ring)
        assertEquals(1, st.usedSeats("lic-1")); assertEquals(1, st.duplicates.size)
        assertEquals(a1.seat, st.seats.values.single().seatId, "the earlier seat is kept")
    }

    @Test fun overIssuingIsReportedNotHidden() {
        val st = LicenseBook.replay(listOf(licence(seats = 1), LicenseEvent.issue(desk, issue(desk, tv("A"), "lic-1")), LicenseEvent.issue(phone, issue(phone, tv("B"), "lic-1", at = NOW + DAY))), ring)
        assertEquals(2, st.usedSeats("lic-1")); assertTrue(st.warnings.any { it.contains("plus de postes que prévu") })
    }

    @Test fun transferMovesTheSeatRevokesTheOldActivationAndLetsTheNewOneIn() {
        val oldTv = tv("A"); val newTv = tv("N")
        val a1 = issue(desk, oldTv, "lic-1")
        val at = NOW + 5 * DAY
        val events = listOf(licence(), LicenseEvent.issue(desk, a1), LicenseEvent.transfer(desk, at, "lic-1", a1.seat, newTv, nonce = "ab".repeat(8)))
        val st = LicenseBook.replay(events, ring)
        assertEquals(1, st.transfers.size); assertEquals(1, st.usedSeats("lic-1"))
        assertIs<Plan.Reuse>(LicenseBook.plan(st, "lic-1", Subject.TV, newTv)); assertIs<Plan.NewSeat>(LicenseBook.plan(st, "lic-1", Subject.TV, oldTv))
        val a2 = issue(desk, newTv, "lic-1", at = at + 1, seat = a1.seat, nonce = "cd".repeat(8))
        // the old TV, once it receives the revocation list, refuses its old activation; the new TV accepts the new one
        val v = ActivationVerifier(ring, revocations = st.revocations)
        assertEquals(Rejection.REVOKED_SEAT, assertIs<ActivationResult.Rejected>(v.verify(a1.encode(), oldTv, at + DAY)).reason)
        assertIs<ActivationResult.Accepted>(v.verify(a2.encode(), newTv, at + DAY))
        assertEquals(Rejection.WRONG_DEVICE, assertIs<ActivationResult.Rejected>(v.verify(a2.encode(), oldTv, at + DAY)).reason, "the new activation does not run on the old hardware")
    }

    @Test fun transferCapIsPerLicencePerYear_defaultTwo() {
        val a1 = issue(desk, tv("A"), "lic-1")
        fun transfer(n: Int, at: Long) = LicenseEvent.transfer(desk, at, "lic-1", a1.seat, tv("H$n"), nonce = "0$n".repeat(8))
        val ev = listOf(licence(), LicenseEvent.issue(desk, a1), transfer(1, NOW + DAY), transfer(2, NOW + 2 * DAY), transfer(3, NOW + 3 * DAY))
        val st = LicenseBook.replay(ev, ring)
        assertEquals(2, st.transfers.size); assertEquals(Rejection.TRANSFER_LIMIT, st.rejected.single().second)
        assertFalse(LicenseBook.transferAllowed(st, "lic-1", NOW + 4 * DAY)); assertTrue(LicenseBook.transferAllowed(st, "lic-1", NOW + 400 * DAY), "a year later it is allowed again")
        assertEquals(3, LicenseBook.replay(listOf(licence(cap = 3)) + ev.drop(1), ring).transfers.size, "the cap is a setting of the licence")
    }

    @Test fun theServerKeyCanNeverTransfer_andForgedOrUnknownEventsAreRefused() {
        val a1 = issue(desk, tv("A"), "lic-1")
        val byServer = LicenseEvent.transfer(server, NOW + DAY, "lic-1", a1.seat, tv("N"), nonce = "ab".repeat(8))
        var st = LicenseBook.replay(listOf(licence(), LicenseEvent.issue(desk, a1), byServer), ring)
        assertEquals(0, st.transfers.size); assertEquals(Rejection.KEY_NOT_ALLOWED, st.rejected.single().second)
        val rogue = LicenseEvent.license(Ed25519Signer(seed("rogue")), NOW, "lic-evil", 99)
        val tampered = licence().let { LicenseEvent(it.keyId, it.text.replace("seats=2", "seats=200"), it.signature) }
        st = LicenseBook.replay(listOf(rogue, tampered), ring)
        assertEquals(setOf(Rejection.UNKNOWN_KEY, Rejection.BAD_SIGNATURE), st.rejected.map { it.second }.toSet()); assertTrue(st.licenses.isEmpty())
        val revokedRing = KeyRing(listOf(desk.trusted()), setOf(desk.keyId))
        assertEquals(Rejection.REVOKED_KEY, LicenseBook.replay(listOf(licence()), revokedRing).rejected.single().second)
    }

    @Test fun registriesMergeWithoutDuplicatesAndInAnyOrder_theThreeToolsAgree() {
        val a1 = issue(desk, tv("A"), "lic-1"); val a2 = issue(phone, tv("B"), "lic-1", at = NOW + DAY); val a3 = issue(server, tv("C"), "lic-2", at = NOW + 2 * DAY)
        val deskLog = listOf(licence(), LicenseEvent.issue(desk, a1))
        val phoneLog = listOf(licence(), LicenseEvent.issue(phone, a2))                      // the licence event is also known to the phone (same event)
        val serverLog = listOf(LicenseEvent.license(server, NOW - 9 * DAY, "lic-2", 1), LicenseEvent.issue(server, a3))
        val merged = LicenseBook.merge(LicenseBook.merge(deskLog, phoneLog), serverLog)
        assertEquals(5, merged.size, "the shared licence event counts once")
        val other = LicenseBook.merge(serverLog, LicenseBook.merge(phoneLog.reversed(), deskLog.reversed()))
        assertEquals(LicenseBook.replay(merged, ring).digest(), LicenseBook.replay(other, ring).digest())
        assertEquals(LicenseBook.replay(merged, ring).digest(), LicenseBook.replay(merged + merged, ring).digest(), "idempotent")
        // through the signed JSON file: export, import, same state; a tampered entry (id no longer matching) is skipped, never trusted
        val file = LicenseBook.export(merged)
        assertEquals(LicenseBook.replay(merged, ring).digest(), LicenseBook.replay(LicenseBook.import(file), ring).digest())
        val broken = file.replace("seats=2", "seats=9")
        assertTrue(LicenseBook.import(broken).size < merged.size)
        assertFailsWith<castbridge.core.net.JsonLite.ParseError> { LicenseBook.import("{\"format\":\"x\"}") }
    }

    @Test fun twoToolsReissuingTheSameLicenceBytewiseAgreeOnTheSeat() {
        val fp = tv("A")
        assertEquals(issue(desk, fp, "lic-1").seat, issue(phone, fp, "lic-1").seat, "the default seat id is a function of licence and hardware only")
    }

    @Test fun revocationNoticeIsSignedAndScoped() {
        val state = RevocationState(setOf("00112233445566ff"), mapOf("lic-1|aabbccddeeff0011" to NOW))
        val tok = RevocationNotice.issue(server, NOW, state)
        val back = assertNotNull(RevocationNotice.verify(tok, ring))
        assertEquals(state.keys, back.keys); assertEquals(state.seats, back.seats)
        assertNull(RevocationNotice.verify(tok, KeyRing(listOf(desk.trusted()))), "unknown key")
        assertNull(RevocationNotice.verify(RevocationNotice.issue(server, NOW, state), KeyRing(listOf(server.trusted(setOf(KeyScope.ISSUE_TRIAL))))), "no REVOKE scope")
        assertNull(RevocationNotice.verify(tok.replace(tok.split('.')[1], tok.split('.')[1].dropLast(2) + "AA"), ring), "tampered")
        assertNull(RevocationNotice.verify(tok, KeyRing(listOf(server.trusted(serverScopes)), setOf(server.keyId))), "revoked signer")
        // applied by a device: the key is refused, then a revoked-seat activation
        val fp = tv("A"); val act = issue(desk, fp, "lic-1")
        val rev = back.merge(RevocationState(setOf(desk.keyId), emptyMap()))
        assertEquals(Rejection.REVOKED_KEY, assertIs<ActivationResult.Rejected>(ActivationVerifier(ring, revocations = rev).verify(act.encode(), fp, NOW)).reason)
    }
}

class FeatureGateTest {
    private val fp = tv("TV")
    private val code = DeviceCode.of(fp)
    private val trusted = listOf(desk.trusted(), phone.trusted())
    private val required = ActivationRequirement(required = true)
    private fun receiver(subject: Subject = Subject.TV, device: Fingerprints = fp) = ActivationReceiver(ring, trusted, device, subject)
    private fun access(vararg a: Activation) = TvGate.evaluate(a.toList(), emptyList(), NOW)
    private val tvActivation get() = issue(desk, fp, "lic-1")

    @Test fun theLockedSurfaceIsExactlyTheActivationSurface_andANewFeatureIsLockedByDefault() {
        val expected = setOf(Feature.USAGE_NOTICE, Feature.DEVICE_CODE, Feature.ACTIVATION_BLUETOOTH, Feature.ACTIVATION_FILE, Feature.ACTIVATION_MANUAL_ENTRY, Feature.OWNER_CHANNEL,
            Feature.DISPLAY_LANGUAGE, Feature.MINIMAL_BLUETOOTH_LINK, Feature.SHARE_DEVICE_CODE, Feature.CARRY_ACTIVATION_FOR_TV,
            // added ON PURPOSE (owner's decision 2026-10-06, « activer la clé en exploitant le WIFI »): one PIN-guarded route, see LockedActivationApiTest
            Feature.ACTIVATION_WIFI)
        val locked = FeatureGate.state(required, access(), NOW)
        assertIs<GateState.Locked>(locked)
        assertEquals(expected, Feature.values().filter { FeatureGate.canUse(it, locked) }.toSet(), "the whitelist is pinned: a feature reachable while locked must be added here on purpose")
        for (f in listOf(Feature.PLAYER, Feature.LIBRARY, Feature.REMOTE_CONTROL, Feature.LEARN, Feature.QUIZ, Feature.GAMES, Feature.FILE_TRANSFER, Feature.UPDATES, Feature.SSH, Feature.ADMIN_API))
            assertFalse(FeatureGate.canUse(f, locked), f.name)
        assertEquals(expected, LOCKED_WHITELIST)
    }

    @Test fun theRequirementIsACompileSwitchOffByDefault() {
        assertFalse(ActivationRequirement().required)
        val open = FeatureGate.state(ActivationRequirement(), access(), NOW)
        assertIs<GateState.NotRequired>(open); assertTrue(Feature.values().all { FeatureGate.canUse(it, open) }, "development builds and the owner's own devices stay open")
    }

    @Test fun everyChannelUnlocks_bluetooth() {
        val r = receiver().receive(Channel.BLUETOOTH, ActivationIssuer.Issued(tvActivation).bluetoothFrame, NOW)
        val a = assertIs<ActivationResult.Accepted>(r).activation
        val st = FeatureGate.state(required, access(a), NOW)
        assertIs<GateState.Activated>(st); assertTrue(Feature.values().all { FeatureGate.canUse(it, st) })
        assertEquals(Rejection.MALFORMED, assertIs<ActivationResult.Rejected>(receiver().receive(Channel.BLUETOOTH, OwnerFrames.encode(OwnerFrames.CHALLENGE, "ab".repeat(16)), NOW)).reason)
    }

    @Test fun everyChannelUnlocks_usbFileWithCrlfBomAndNewline() {
        val issued = ActivationIssuer.Issued(tvActivation)
        for (content in listOf(issued.fileContent, "﻿" + issued.token + "\r\n", "\n" + issued.token + "\n\n"))
            assertIs<ActivationResult.Accepted>(receiver().receive(Channel.FILE, content.toByteArray(), NOW), content.take(8))
        assertIs<ActivationResult.Rejected>(receiver().receive(Channel.FILE, "rien".toByteArray(), NOW))
    }

    @Test fun everyChannelUnlocks_manualEntry() {
        val issued = ActivationIssuer.Issued(tvActivation)
        assertIs<ActivationResult.Accepted>(receiver().receive(Channel.MANUAL, issued.token.toByteArray(), NOW), "pasted token")
        assertIs<ActivationResult.Accepted>(receiver().receive(Channel.MANUAL, issued.groupedText.lowercase().replace('-', ' ').toByteArray(), NOW), "grouped text")
        val day0 = ((NOW - CompactActivation.EPOCH_MS) / 3_600_000L).toInt() - 1
        val compact = ActivationIssuer(desk).issueCompact(ActivationKind.TRIAL, code, day0, 48)
        assertEquals(ActivationKind.TRIAL, assertIs<ActivationResult.Accepted>(receiver().receive(Channel.MANUAL, compact.toByteArray(), NOW)).activation.kind)
        assertEquals(Rejection.MALFORMED, assertIs<ActivationResult.Rejected>(receiver().receive(Channel.MANUAL, "n'importe quoi".toByteArray(), NOW)).reason)
    }

    @Test fun reinstallingTheSameFileTwiceChangesNothing_aNewerOneReplaces() {
        val a1 = tvActivation; val a2 = issue(phone, fp, "lic-1", at = NOW + DAY, seat = a1.seat)
        val inst = InstalledActivations(); inst.install(a1); inst.install(a1)
        assertEquals(1, inst.all().size); inst.install(a2); assertEquals(listOf(a2), inst.all())
        inst.install(a1); assertEquals(listOf(a2), inst.all(), "an older copy never replaces a newer one")
    }

    @Test fun lockedPhoneCarriesAnActivationForATvAndUnlocksNothingForItself() {
        val phoneFp = DeviceIdentity.fingerprints(RawFactors(systemSerial = "androidid:9774d56d682e549c"))
        val phoneAccess = TvGate.evaluate(emptyList(), emptyList(), NOW)
        val tvToken = tvActivation.encode()
        val item = assertNotNull(CarrierMode.accept(tvToken))
        assertEquals(code, item.forDeviceCode); assertNotNull(CarrierMode.frame(item))
        assertNotNull(CarrierMode.accept(ActivationIssuer.Issued(tvActivation).groupedText)); assertNotNull(CarrierMode.accept(ActivationIssuer(desk).issueCompact(ActivationKind.TRIAL, code, 100, 48)))
        assertNull(CarrierMode.frame(CarrierMode.accept(ActivationIssuer(desk).issueCompact(ActivationKind.TRIAL, code, 100, 48))!!), "a compact key is typed on the TV, no frame")
        assertNull(CarrierMode.accept("n'importe quoi")); assertNull(CarrierMode.accept("cba1.xx.yy"))
        assertNull(CarrierMode.accept(issue(desk, phoneFp, "lic-1", subject = Subject.PHONE).encode()), "a phone activation is not carried to a TV")
        // the phone itself stays locked: carrying never installs anything, and its own receiver refuses a TV activation
        assertIs<GateState.Locked>(FeatureGate.state(required, phoneAccess, NOW))
        assertEquals(Rejection.WRONG_SUBJECT, assertIs<ActivationResult.Rejected>(receiver(Subject.PHONE, phoneFp).receive(Channel.MANUAL, tvToken.toByteArray(), NOW)).reason)
        assertTrue(FeatureGate.canUse(Feature.CARRY_ACTIVATION_FOR_TV, GateState.Locked) && FeatureGate.canUse(Feature.SHARE_DEVICE_CODE, GateState.Locked))
    }

    @Test fun anExistingInstallGetsAGraceThenLocks_aFreshOneLocksAtOnce() {
        val grace = FleetMigration(existingInstall = true, graceStartMs = NOW)
        val inGrace = FeatureGate.state(required, access(), NOW + 5 * DAY, grace)
        assertEquals(NOW + 14 * DAY, assertIs<GateState.Grace>(inGrace).untilMs); assertTrue(Feature.values().all { FeatureGate.canUse(it, inGrace) })
        assertIs<GateState.Locked>(FeatureGate.state(required, access(), NOW + 15 * DAY, grace))
        assertIs<GateState.Locked>(FeatureGate.state(required, access(), NOW, FleetMigration(existingInstall = false, graceStartMs = NOW)))
        assertEquals(NOW + 30 * DAY, FleetMigration(true, NOW).graceUntil(ActivationRequirement(true, graceDays = 30)), "the grace period is adjustable")
        assertNull(FleetMigration(true, NOW).graceUntil(ActivationRequirement(true, graceDays = 0)))
        assertIs<GateState.Activated>(FeatureGate.state(required, access(tvActivation), NOW + 20 * DAY, grace), "activation always wins")
    }

    @Test fun trialIsIssuedManuallyByDefault() {
        assertEquals(TrialIssuancePolicy.MANUAL, TrialIssuancePolicy.DEFAULT)
        assertEquals(TrialIssuance.Decision.AskOwner, TrialIssuance.decide(TrialIssuancePolicy.MANUAL, phonePaired = true, serverReachable = true))
        assertEquals(TrialIssuance.Decision.AskOwner, TrialIssuance.decide(TrialIssuancePolicy.AUTOMATIC, phonePaired = false, serverReachable = true))
        assertEquals(TrialIssuance.Decision.FetchFromServerViaPhone, TrialIssuance.decide(TrialIssuancePolicy.AUTOMATIC, phonePaired = true, serverReachable = true))
    }

    @Test fun lockedScreenTexts() {
        assertContains(LockedTexts.shareMessage(code, Subject.TV), code); assertContains(LockedTexts.NOTICE, "à valider par le propriétaire")
    }

    @Test fun theCodeUnderTheDeviceCodeSaysWhatItIsAndHowTheKeyIsAskedFor() {
        // propriétaire bloqué : le texte disait « fournissez ce code d'appareil à CastBridge pour obtenir votre clé », FAUX : l'émetteur exige les empreintes de facteurs (le code n'en est que le haché,
        // ActivationIssuer l. 104-105) ; la clé se demande avec CastBridge › Activer la TV sur le téléphone, qui lit la demande complète de la TV
        assertEquals("Code d'appareil (il identifie cette TV ; la clé se demande avec CastBridge › Activer la TV sur le téléphone, qui lit la demande complète de la TV)", LockedTexts.REQUEST)
        for (t in listOf(LockedTexts.REQUEST, LockedTexts.WAYS, LockedTexts.GRACE)) {
            val low = t.lowercase()
            for (false_claim in listOf("fournissez", "envoyer le code", "envoyez le code", "communiquez le code", "donnez le code", "obtenir votre clé")) assertFalse(false_claim in low, "« $false_claim » dans « $t »")
        }
        assertContains(LockedTexts.WAYS, "demande d'appareil"); assertContains(LockedTexts.GRACE, "Activer la TV")
    }
}

/** Registry events signed by a delegated agent key (docs/coordination/DESIGN-W4-VENTE-TERRAIN.md § 3): replayed with the ring that holds the mandates, inside their window only. */
class DelegatedReplayTest {
    private val t0 = AgentFixtures.T0
    private val agent = AgentFixtures.key("agent").signer
    private val tvRing = AgentFixtures.tvRing()
    private fun replayRing(vararg mandates: String) = tvRing.withDelegated(Delegation.replayKeys(mandates.toList(), tvRing))
    private fun events(at: Long): List<LicenseEvent> {
        val d = AgentFixtures.dev("tvA")
        val a = ActivationIssuer(agent, Delegation.ALLOWED_SCOPES).issue(ActivationIssuer.Request(ActivationKind.PRODUCTION, d.code, d.fp, at, rights = listOf(castbridge.core.lots.Right.Usage(at, at + 30 * AgentFixtures.DAY)), license = "lic-0001", nonce = "c3c3c3c3c3c3c3c3")).activation
        return listOf(LicenseEvent.license(agent, at - 1, "lic-0001", 1), LicenseEvent.issue(agent, a))
    }

    @Test fun eventsInsideTheMandateWindowAreReplayed() {
        val st = LicenseBook.replay(events(t0 + AgentFixtures.DAY), replayRing(AgentFixtures.delegation()))
        assertEquals(setOf("lic-0001"), st.licenses.keys); assertEquals(1, st.usedSeats("lic-0001")); assertTrue(st.rejected.isEmpty())
    }

    @Test fun eventsOutsideTheWindowAreRejectedAsKeyNotAllowed() {
        val late = LicenseBook.replay(events(t0 + 91 * AgentFixtures.DAY), replayRing(AgentFixtures.delegation()))
        assertTrue(late.licenses.isEmpty()); assertEquals(setOf(Rejection.KEY_NOT_ALLOWED), late.rejected.map { it.second }.toSet())
        val early = LicenseBook.replay(events(t0 - 10 * AgentFixtures.DAY), replayRing(AgentFixtures.delegation()))
        assertEquals(2, early.rejected.size)
    }

    @Test fun withoutAMandateTheAgentKeyIsUnknownAndAnExpiredMandateKeepsPastEvents() {
        assertEquals(setOf(Rejection.UNKNOWN_KEY), LicenseBook.replay(events(t0 + AgentFixtures.DAY), tvRing).rejected.map { it.second }.toSet())
        assertEquals(1, LicenseBook.replay(events(t0 + AgentFixtures.DAY), replayRing(AgentFixtures.delegation(validityDays = 10))).licenses.size)       // mandate over since, past event still counts
    }

    @Test fun aRevokedAgentStaysRevokedInTheReplay() {
        val ring = replayRing(AgentFixtures.delegation()).withRevoked(setOf(agent.keyId))
        assertEquals(setOf(Rejection.REVOKED_KEY), LicenseBook.replay(events(t0 + AgentFixtures.DAY), ring).rejected.map { it.second }.toSet())
    }
}
