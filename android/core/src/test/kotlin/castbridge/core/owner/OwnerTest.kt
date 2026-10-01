package castbridge.core.owner

import castbridge.core.lots.*
import java.io.ByteArrayInputStream
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.Signature
import java.util.Base64
import kotlin.test.*

private val day = 24L * 3600 * 1000
private const val T0 = 1_800_000_000_000L          // an instant in 2027


/** A test console: one signing key, the ring a TV would embed, helpers that build signed artefacts the way the real console will (raw, so tests can build invalid ones too). */
private class Console(seed: ByteArray = ByteArray(32).also { SecureRandom().nextBytes(it) }, val maxPower: Power = Power.OPEN_ALL) {
    val signer = Ed25519Signer(seed)
    val keyId = signer.keyId
    val trusted = signer.trusted(KeyScope.upTo(maxPower))
    fun ring(revoked: Set<String> = emptySet()) = KeyRing(listOf(trusted), revoked)
    private fun sig(text: String) = Base64.getEncoder().encodeToString(signer.sign(text.toByteArray(Charsets.UTF_8)))

    fun activation(fp: Fingerprints, kind: ActivationKind = ActivationKind.PRODUCTION, rights: List<Right> = emptyList(), issued: Long = T0, from: Long = T0 - day,
                   to: Long = T0 + 300 * day, k: Int = DeviceIdentity.kFor(fp.n), nonce: String = "ab".repeat(8), subject: Subject = Subject.TV,
                   license: String = if (kind == ActivationKind.TRIAL) "trial" else "lic-1", seat: String = SeatIds.of(license, fp)): String {
        val payload = Activation.payload(kind, subject, keyId, nonce, issued, from, to, license, seat, k, fp.byKind, rights)
        return Activation(kind, subject, keyId, nonce, issued, from, to, license, seat, k, fp.byKind, rights, sig(payload)).encode()
    }

    fun command(fp: Fingerprints, challenge: String, power: Power = Power.OPEN_ALL, days: Int = 30, action: String = "", bundles: List<String> = emptyList(),
                lots: List<LotId> = emptyList(), k: Int = DeviceIdentity.kFor(fp.n)): String {
        val payload = OwnerCommand.payload(keyId, power, action, challenge, k, fp.byKind, bundles, lots, days)
        return OwnerCommand(keyId, power, action, challenge, k, fp.byKind, bundles, lots, days, sig(payload)).encode()
    }

    fun compact(code: String, kind: ActivationKind = ActivationKind.TRIAL, notBeforeDay: Int = ((T0 - CompactActivation.EPOCH_MS) / day).toInt() - 1, window: Int = 200, setId: Int = 0): String {
        val h = CompactActivation.Header(kind, CompactActivation.keyTag(keyId), notBeforeDay, window, setId, CompactActivation.bindOf(code))
        return CompactActivation.encode(h, signer.sign(CompactActivation.signedText(h.bytes()).toByteArray(Charsets.UTF_8)))
    }
}

private fun tvFactors(wifiPath: String? = "/sys/devices/platform/soc/ffe03000.sd/mmc_host/mmc1/mmc1:0001/net/wlan0", eth: String? = "AA:BB:CC:00:11:22",
                      flash: String? = "FLASHSERIAL1", wifi: String? = "10:20:30:40:50:60", sys: String? = "SYS12345", bt: String? = "11:22:33:44:55:66") =
    RawFactors(flashSerial = flash, flashCid = "cid-1", ethernetMac = eth, wifiMac = wifi, wifiSysfsPath = wifiPath, systemSerial = sys, bluetoothAddress = bt)

class DeviceIdentityTest {
    @Test fun usbWifiIsExcludedAndSolderedWifiIsIncluded() {
        assertFalse(DeviceIdentity.wifiBusIsSoldered("/sys/devices/platform/soc/fe340000.usb/usb1/1-1/1-1.1:1.0/net/wlan0"), "reference TV: replaceable USB module")
        assertTrue(DeviceIdentity.wifiBusIsSoldered("/sys/devices/platform/soc/ffe03000.sd/mmc_host/mmc1/mmc1:0001/net/wlan0"))
        assertTrue(DeviceIdentity.wifiBusIsSoldered("/sys/devices/pci0000:00/0000:00:1c.0/net/wlan0"))
        assertFalse(DeviceIdentity.wifiBusIsSoldered(null)); assertFalse(DeviceIdentity.wifiBusIsSoldered("/sys/devices/virtual/net/wlan0"))
        val usb = DeviceIdentity.fingerprints(tvFactors(wifiPath = "/sys/x/usb1/net/wlan0"))
        assertFalse(FactorKind.WIFI in usb.byKind); assertTrue(FactorKind.WIFI in DeviceIdentity.fingerprints(tvFactors()).byKind)
    }

    @Test fun aTvWithoutEthernetStillHasAnIdentity() {
        val fp = DeviceIdentity.fingerprints(tvFactors(eth = null))
        assertFalse(FactorKind.ETHERNET in fp.byKind); assertTrue(FactorKind.FLASH in fp.byKind && fp.n >= 3)
        val bare = DeviceIdentity.fingerprints(RawFactors(systemSerial = "ABC123456"))
        assertEquals(setOf(FactorKind.SYSTEM_SERIAL), bare.byKind.keys); assertTrue(DeviceIdentity.isWeak(bare))
        assertEquals(0, DeviceIdentity.fingerprints(RawFactors()).n)
    }

    @Test fun placeholderValuesAreNotFactors() {
        val fp = DeviceIdentity.fingerprints(RawFactors(flashSerial = "0000000000", ethernetMac = "02:00:00:00:00:00", systemSerial = "unknown", bluetoothAddress = "00:00:00:00:00:00"))
        assertEquals(0, fp.n)
    }

    @Test fun fingerprintsAreSaltedHashedAndStable() {
        val a = DeviceIdentity.fingerprints(tvFactors()); val b = DeviceIdentity.fingerprints(tvFactors(eth = "aa-bb-cc-00-11-22"))   // same MAC, other notation
        assertEquals(a, b); assertTrue(a.byKind.values.all { Regex("^[0-9a-f]{32}$").matches(it) })
        assertFalse(a.byKind.values.any { "flashserial" in it.lowercase() })
    }

    @Test fun deviceCodeFormatConfusionsAndChecksum() {
        val code = DeviceCode.of(DeviceIdentity.fingerprints(tvFactors()))
        assertTrue(Regex("^[0-9A-HJKMNP-TV-Z]{4}(-[0-9A-HJKMNP-TV-Z]{4}){3}$").matches(code), code)
        assertEquals(code, DeviceCode.parse(code.lowercase().replace("-", " ")))
        val confused = code.replace('0', 'O').replace('1', 'I')
        assertEquals(code, DeviceCode.parse(confused), "O reads as 0, I as 1")
        val typo = code.replaceFirst(code[6], if (code[6] == 'A') 'B' else 'A')
        assertNull(DeviceCode.parse(typo)); assertNull(DeviceCode.parse("1234")); assertNull(DeviceCode.parse(code + "0"))
        assertEquals(DeviceIdentity.fingerprints(tvFactors()).byKind.keys, DeviceCode.kindsOf(code), "the first char says which factors were used")
        assertNotEquals(code, DeviceCode.of(DeviceIdentity.fingerprints(tvFactors(flash = "OTHER"))))
    }

    @Test fun kOfNToleratesOneChangedFactorOnlyFromThreeFactors() {
        val fp = DeviceIdentity.fingerprints(tvFactors()); assertEquals(5, fp.n); assertEquals(4, DeviceIdentity.kFor(5))
        assertEquals(1, DeviceIdentity.kFor(1)); assertEquals(2, DeviceIdentity.kFor(2)); assertEquals(2, DeviceIdentity.kFor(3))
        val swapped = DeviceIdentity.fingerprints(tvFactors(wifi = "99:99:99:99:99:99"))
        assertTrue(DeviceIdentity.matches(fp.byKind, 4, swapped), "one module replaced")
        val twoChanged = DeviceIdentity.fingerprints(tvFactors(wifi = "99:99:99:99:99:99", bt = "98:98:98:98:98:98"))
        assertFalse(DeviceIdentity.matches(fp.byKind, 4, twoChanged))
        val two = DeviceIdentity.fingerprints(RawFactors(flashSerial = "F1", systemSerial = "SYSTEM12"))
        assertFalse(DeviceIdentity.matches(two.byKind, 2, DeviceIdentity.fingerprints(RawFactors(flashSerial = "F1", systemSerial = "OTHER123"))), "n = 2: all must match")
    }

    @Test fun twoSwappedWeakPartsAreNotTheSameTv() {
        val real = DeviceIdentity.fingerprints(tvFactors())
        val clone = DeviceIdentity.fingerprints(tvFactors(flash = "ANOTHERFLASH", eth = "AA:AA:AA:AA:AA:AA"))   // only the weak factors are shared
        assertFalse(DeviceIdentity.matches(real.byKind, 2, clone), "no strong factor among the matches")
    }
}

class ActivationTest {
    private val console = Console()
    private val fp = DeviceIdentity.fingerprints(tvFactors())
    private val v get() = ActivationVerifier(console.ring())

    private fun accepted(r: ActivationResult) = assertIs<ActivationResult.Accepted>(r).activation
    private fun rejected(r: ActivationResult, why: Rejection) = assertEquals(why, assertIs<ActivationResult.Rejected>(r).reason)

    @Test fun trialAndProductionActivationsAreAccepted() {
        assertEquals(ActivationKind.TRIAL, accepted(v.verify(console.activation(fp, ActivationKind.TRIAL), fp, T0)).kind)
        val prod = accepted(v.verify(console.activation(fp, rights = listOf(Right.Purchase("p1", listOf("classe-cm2"), T0))), fp, T0))
        assertEquals(listOf("classe-cm2"), prod.rights.single().bundleIds)
    }

    @Test fun aReplacedModuleDoesNotInvalidateTheActivation_butAnotherTvDoes() {
        val tok = console.activation(fp)
        accepted(v.verify(tok, DeviceIdentity.fingerprints(tvFactors(wifi = "99:99:99:99:99:99")), T0))
        val other = DeviceIdentity.fingerprints(tvFactors(flash = "X", eth = "AA:AA:AA:AA:AA:01", wifi = "01:01:01:01:01:01", sys = "OTHER999", bt = "10:10:10:10:10:10"))
        val r = v.verify(tok, other, T0)
        rejected(r, Rejection.WRONG_DEVICE); assertTrue((r as ActivationResult.Rejected).suspect, "suspect device: back to the trial with a manual unlock, never a flat refusal")
    }

    @Test fun tamperedForeignRevokedAndUnknownKeysAreRefused() {
        val tok = console.activation(fp, rights = listOf(Right.Purchase("p1", listOf("classe-cm2"), T0)))
        val parts = tok.split('.')
        val forged = parts[0] + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(String(Base64.getUrlDecoder().decode(parts[1])).replace("classe-cm2", "tout").toByteArray()) + "." + parts[2]
        rejected(v.verify(forged, fp, T0), Rejection.BAD_SIGNATURE)
        val rogue = Console()   // another key pair: its id is not in the ring
        rejected(v.verify(rogue.activation(fp), fp, T0), Rejection.UNKNOWN_KEY)
        rejected(ActivationVerifier(console.ring(revoked = setOf(console.keyId))).verify(tok, fp, T0), Rejection.REVOKED_KEY)
        rejected(v.verify("garbage", fp, T0), Rejection.MALFORMED)
        // a key claiming the id of the real one but signing with its own pair
        val liar = Console(); val seat = SeatIds.of("lic-1", fp)
        val liarTok = Activation.payload(ActivationKind.PRODUCTION, Subject.TV, console.keyId, "ab".repeat(8), T0, T0 - day, T0 + day, "lic-1", seat, 4, fp.byKind, emptyList())
        rejected(v.verify(Activation(ActivationKind.PRODUCTION, Subject.TV, console.keyId, "ab".repeat(8), T0, T0 - day, T0 + day, "lic-1", seat, 4, fp.byKind, emptyList(),
            Base64.getEncoder().encodeToString(liar.signer.sign(liarTok.toByteArray()))).encode(), fp, T0), Rejection.BAD_SIGNATURE)
    }

    @Test fun aKeyNeverExceedsItsPower() {
        val support = Console(maxPower = Power.SUPPORT)
        rejected(ActivationVerifier(support.ring()).verify(support.activation(fp), fp, T0), Rejection.KEY_NOT_ALLOWED)
    }

    @Test fun offlineWindowIsAtMostOneYearAndRespectsAWrongClock() {
        rejected(v.verify(console.activation(fp, from = T0, to = T0 + 400 * day), fp, T0), Rejection.WINDOW_TOO_LONG)
        accepted(v.verify(console.activation(fp, from = T0, to = T0 + 365 * day), fp, T0))
        // the TV clock says 1970 (dead battery clock): the activation's own issue date is the floor
        accepted(v.verify(console.activation(fp, issued = T0, from = T0 - day, to = T0 + 30 * day), fp, nowMs = 0L))
        rejected(v.verify(console.activation(fp, issued = T0, from = T0 + 100 * day, to = T0 + 130 * day), fp, T0), Rejection.NOT_YET_VALID)
        rejected(v.verify(console.activation(fp, issued = T0 - 60 * day, from = T0 - 60 * day, to = T0 - 30 * day), fp, T0), Rejection.WINDOW_CLOSED)
    }

    // ---- what a TV may open ----
    @Test fun noKeyMeansNoContentAtAll() {
        val g = TvGate.evaluate(emptyList(), emptyList(), T0)
        assertFalse(g.opensContent); assertTrue(g.access.granted.isEmpty())
    }

    @Test fun trialKeyOpensTheTrialOnly_productionAddsRights_expiryFallsBack() {
        val trial = accepted(v.verify(console.activation(fp, ActivationKind.TRIAL), fp, T0))
        val t = TvGate.evaluate(listOf(trial), emptyList(), T0)
        assertTrue(t.opensContent); assertTrue(t.access.granted.isEmpty()); assertEquals("Version d'essai", t.label)
        val sub = Right.Subscription("abo", listOf("tout"), T0 - day, T0 + day, day, false)
        val prod = accepted(v.verify(console.activation(fp, rights = listOf(Right.Purchase("p1", listOf("quiz-cm2"), T0), sub)), fp, T0))
        assertEquals(setOf("quiz-cm2", "tout"), TvGate.evaluate(listOf(trial, prod), emptyList(), T0).access.granted)
        assertEquals(setOf("quiz-cm2"), TvGate.evaluate(listOf(trial, prod), emptyList(), T0 + 5 * day).access.granted, "subscription over: the purchase stays")
    }

    // ---- compact activation, typed by hand ----
    @Test fun compactActivationRoundTripsAndIsLongButTypable() {
        val code = DeviceCode.of(fp)
        val typed = console.compact(code)
        assertEquals(33, typed.split('-').size); assertTrue(typed.split('-').all { it.length == 5 })
        val r = CompactActivation.verify(typed, console.ring(), listOf(console.trusted), code, T0)
        assertEquals(ActivationKind.TRIAL, accepted(r).kind)
        // as typed on a remote: lower case, spaces, O for 0, I for 1
        val sloppy = typed.lowercase().replace('0', 'o').replace('1', 'i').replace("-", " ")
        accepted(CompactActivation.verify(sloppy, console.ring(), listOf(console.trusted), code, T0))
    }

    @Test fun compactActivationPointsAtTheMistypedGroup() {
        val code = DeviceCode.of(fp); val typed = console.compact(code)
        val groups = typed.split('-').toMutableList()
        groups[11] = groups[11].replaceRange(1, 2, if (groups[11][1] == 'A') "B" else "A")
        val r = CompactActivation.verify(groups.joinToString("-"), console.ring(), listOf(console.trusted), code, T0)
        assertContains(assertIs<ActivationResult.Rejected>(r).message, "Groupe 12")
        assertEquals(CompactActivation.Parsed.Malformed, CompactActivation.parse("ABCD"))
    }

    @Test fun compactActivationIsStrictlyBoundToTheDeviceAndSigned() {
        val code = DeviceCode.of(fp); val other = DeviceCode.of(DeviceIdentity.fingerprints(tvFactors(flash = "ELSE")))
        val typed = console.compact(code)
        assertEquals(Rejection.WRONG_DEVICE, assertIs<ActivationResult.Rejected>(CompactActivation.verify(typed, console.ring(), listOf(console.trusted), other, T0)).reason)
        assertEquals(Rejection.UNKNOWN_KEY, assertIs<ActivationResult.Rejected>(CompactActivation.verify(Console().compact(code), console.ring(), listOf(console.trusted), code, T0)).reason)
        assertEquals(Rejection.REVOKED_KEY, assertIs<ActivationResult.Rejected>(CompactActivation.verify(typed, console.ring(setOf(console.keyId)), listOf(console.trusted), code, T0)).reason)
        assertEquals(Rejection.WINDOW_CLOSED, assertIs<ActivationResult.Rejected>(CompactActivation.verify(typed, console.ring(), listOf(console.trusted), code, T0 + 900 * day)).reason)
    }
}

class OwnerCommandTest {
    private val console = Console()
    private val fp = DeviceIdentity.fingerprints(tvFactors())
    private var uptime = 1000L
    private val book = ChallengeBook({ uptime }, SecureRandom())
    private val clock = TvClock()
    private fun verifier(ring: KeyRing = console.ring()) = OwnerCommandVerifier(ring, book)
    private fun ok(r: CommandResult) = assertIs<CommandResult.Accepted>(r).grant
    private fun no(r: CommandResult, why: Rejection) = assertEquals(why, assertIs<CommandResult.Rejected>(r).reason)

    @Test fun openAllIsAcceptedAndLimitedToThirtyDays() {
        val ch = book.issue()
        val g = ok(verifier().verify(console.command(fp, ch, days = 90), fp, clock, T0))
        assertEquals(Power.OPEN_ALL, g.power); assertTrue(g.clamped); assertEquals(T0 + 30 * day, g.untilMs, "the TV clamps to 30 days whatever the command says")
        val g2 = ok(verifier().verify(console.command(fp, book.issue(), days = 7), fp, clock, T0))
        assertFalse(g2.clamped); assertEquals(T0 + 7 * day, g2.untilMs)
    }

    @Test fun aChallengeWorksOnce_noReplay() {
        val ch = book.issue(); val cmd = console.command(fp, ch)
        ok(verifier().verify(cmd, fp, clock, T0)); no(verifier().verify(cmd, fp, clock, T0), Rejection.REPLAY)
        no(verifier().verify(console.command(fp, "ff".repeat(16)), fp, clock, T0), Rejection.REPLAY)                 // never issued by this TV
    }

    @Test fun aChallengeExpiresOnTheMonotonicClock_notTheWallClock() {
        val ch = book.issue(); uptime += 121_000
        no(verifier().verify(console.command(fp, ch), fp, clock, T0), Rejection.REPLAY)
        val ch2 = book.issue(); uptime += 60_000
        ok(verifier().verify(console.command(fp, ch2), fp, clock, wallClockMs = 0L))   // a wall clock stuck in 1970 changes nothing
    }

    @Test fun aWrongTvIsRefused_andARefusedCommandBurnsNothing() {
        val other = DeviceIdentity.fingerprints(tvFactors(flash = "X", eth = "AA:AA:AA:AA:AA:01", wifi = "01:01:01:01:01:01", sys = "OTHER999", bt = "10:10:10:10:10:10"))
        val ch = book.issue()
        no(verifier().verify(console.command(other, ch), fp, clock, T0), Rejection.WRONG_DEVICE)
        ok(verifier().verify(console.command(fp, ch), fp, clock, T0))          // the legitimate command with the same challenge still works
    }

    @Test fun badSignaturesRevokedKeysAndPowerAboveTheKeyAreRefused() {
        val ch = book.issue(); val tok = console.command(fp, ch)
        val parts = tok.split('.')
        val forged = parts[0] + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(String(Base64.getUrlDecoder().decode(parts[1])).replace("days=30", "days=3000").toByteArray()) + "." + parts[2]
        no(verifier().verify(forged, fp, clock, T0), Rejection.BAD_SIGNATURE)
        no(verifier(console.ring(setOf(console.keyId))).verify(tok, fp, clock, T0), Rejection.REVOKED_KEY)
        no(verifier().verify(Console().command(fp, ch), fp, clock, T0), Rejection.UNKNOWN_KEY)
        val support = Console(maxPower = Power.SUPPORT)
        no(verifier(support.ring()).verify(support.command(fp, ch), fp, clock, T0), Rejection.KEY_NOT_ALLOWED)
        no(verifier().verify("nope", fp, clock, T0), Rejection.MALFORMED)
    }

    @Test fun gradedPowers() {
        val support = Console(maxPower = Power.SUPPORT)
        val g = ok(verifier(support.ring()).verify(support.command(fp, book.issue(), Power.SUPPORT, days = 0, action = "reset-trial"), fp, clock, T0))
        assertEquals(0L, g.untilMs); assertEquals("reset-trial", g.action)
        no(verifier(support.ring()).verify(support.command(fp, book.issue(), Power.SUPPORT, days = 0, action = "format-everything"), fp, clock, T0), Rejection.BAD_COMMAND)
        val unlockKey = Console(maxPower = Power.UNLOCK)
        val u = ok(verifier(unlockKey.ring()).verify(unlockKey.command(fp, book.issue(), Power.UNLOCK, days = 10, lots = listOf(LotId("learn", "cm2"))), fp, clock, T0))
        assertEquals(listOf(LotId("learn", "cm2")), u.lots)
        no(verifier(unlockKey.ring()).verify(unlockKey.command(fp, book.issue(), Power.OPEN_ALL), fp, clock, T0), Rejection.KEY_NOT_ALLOWED)
        no(verifier(unlockKey.ring()).verify(unlockKey.command(fp, book.issue(), Power.UNLOCK, days = 10), fp, clock, T0), Rejection.BAD_COMMAND)
    }

    @Test fun rotationAndSpareKey_manyPublicKeysAtOnce() {
        val spare = Console(); val ring = KeyRing(listOf(console.trusted, spare.trusted))
        ok(OwnerCommandVerifier(ring, book).verify(console.command(fp, book.issue()), fp, clock, T0))
        ok(OwnerCommandVerifier(ring, book).verify(spare.command(fp, book.issue()), fp, clock, T0))
        val rotated = ring.withRevoked(setOf(console.keyId))
        no(OwnerCommandVerifier(rotated, book).verify(console.command(fp, book.issue()), fp, clock, T0), Rejection.REVOKED_KEY)
        ok(OwnerCommandVerifier(rotated, book).verify(spare.command(fp, book.issue()), fp, clock, T0))
    }

    @Test fun grantsOpenEverythingThenFallBackToAcquiredRightsWithoutDeletingAnything() {
        val trial = (ActivationVerifier(console.ring()).verify(console.activation(fp, rights = listOf(Right.Purchase("p", listOf("quiz-cm2"), T0))), fp, T0) as ActivationResult.Accepted).activation
        val g = ok(verifier().verify(console.command(fp, book.issue(), days = 10), fp, clock, T0))
        val open = TvGate.evaluate(listOf(trial), listOf(g), T0 + day)
        assertTrue("tout" in open.access.granted); assertEquals(g.untilMs, open.openAllUntil)
        val after = TvGate.evaluate(listOf(trial), listOf(g), T0 + 11 * day)
        assertEquals(setOf("quiz-cm2"), after.access.granted); assertNull(after.openAllUntil)
        val unlock = OwnerGrant(Power.UNLOCK, emptyList(), listOf(LotId("learn", "cm2")), T0 + day, false, "")
        assertEquals(setOf(LotId("learn", "cm2")), TvGate.evaluate(listOf(trial), listOf(unlock), T0).access.extraLots)
        assertTrue(TvGate.evaluate(emptyList(), listOf(g), T0 + day).opensContent, "an owner grant alone also opens the TV")
    }

    @Test fun editionPolicyHonoursOwnerOpenAndLotGrants() {
        val bundles = BundleCatalog(listOf(Bundle("tout", "tout", setOf("learn:cm2", "learn:6e"))))
        val g = OwnerGrant(Power.OPEN_ALL, emptyList(), emptyList(), T0 + day, false, "")
        val all = TvGate.evaluate(emptyList(), listOf(g), T0).access
        assertEquals(setOf(LotId("learn", "cm2"), LotId("learn", "6e")), EditionPolicy.allowedFull(all, bundles))
        val lot = TvGate.evaluate(emptyList(), listOf(OwnerGrant(Power.UNLOCK, emptyList(), listOf(LotId("learn", "6e")), T0 + day, false, "")), T0).access
        assertEquals(setOf(LotId("learn", "6e")), EditionPolicy.allowedFull(lot, bundles))
    }
}

class TvClockTest {
    @Test fun rollbackIsDetectedAndNeverExtendsAnything() {
        val c = TvClock(); c.observe(T0)
        assertEquals(T0, c.now(T0 - 100 * day)); assertTrue(c.rolledBack(T0 - 100 * day)); assertFalse(c.rolledBack(T0 - 1000))
        c.observe(T0 + 2 * day); assertEquals(T0 + 2 * day, c.now(T0))
    }

    @Test fun aWildForwardJumpIsNotBelieved_untilASignedMessageConfirmsIt() {
        val c = TvClock(); c.observe(T0)
        val glitch = T0 + 5000 * day
        assertEquals(T0, c.now(glitch)); c.observe(glitch); assertEquals(T0, c.lastSeen)
        c.observe(glitch, signedIssuedAt = glitch - day)         // a signed message proves the time is really there
        assertEquals(glitch, c.now(glitch))
    }

    @Test fun aSignedMessageIsAFloorForAClockStuckInThePast() {
        val c = TvClock(); c.observe(0L, signedIssuedAt = T0)
        assertEquals(T0, c.now(0L))
    }
}

class SecretSequenceTest {
    private var t = 0L
    private val seq = SecretSequence({ t })
    private val good = SecretSequence.DEFAULT
    private fun feed(s: SecretSequence, keys: List<RemoteKey>, step: Long = 200): Boolean { var done = false; for (k in keys) { t += step; done = s.onKey(k) || done }; return done }

    @Test fun defaultSequenceOpensInUnderEightSeconds() { assertTrue(feed(seq, good)) }

    @Test fun tooSlowDoesNotOpen() { assertFalse(feed(seq, good, step = 1000)) }

    @Test fun firstErrorResetsWithoutAnyVisibleEffect() {
        assertFalse(feed(seq, good.take(5) + RemoteKey.DOWN + good.drop(5)))
        assertTrue(feed(seq, good), "and a clean retry works")
    }

    @Test fun aTypoThatIsTheFirstKeyStartsANewAttempt() {
        assertTrue(feed(seq, good.take(6) + good), "…← → then the whole sequence again: the UP that breaks the first attempt starts the second")
    }

    @Test fun strayKeysAreIgnoredButNavigationNoiseIsNot() {
        val withVolume = good.take(4) + RemoteKey.OTHER + RemoteKey.OTHER + good.drop(4)
        assertTrue(feed(seq, withVolume))
        assertFalse(feed(seq, good.take(4) + RemoteKey.MENU + good.drop(4)))
    }

    @Test fun repetitionAndOverlapWork() {
        assertTrue(feed(seq, good)); assertTrue(feed(seq, good)); assertFalse(feed(seq, good.dropLast(1)))
        assertTrue(feed(seq, good.take(3) + good), "an incomplete attempt followed by a full one")
    }

    @Test fun panelIsRateLimitedClosesByItselfAndOnBack() {
        val gate = OwnerPanelGate({ t }, SecureRandom(), SecretSequence({ t }))
        assertTrue(feed2(gate, good)); assertTrue(gate.visible); val code = gate.pairingCode!!
        assertTrue(Regex("^\\d{6}$").matches(code))
        t += 59_000; assertTrue(gate.visible)
        t += 2_000; gate.tick(); assertFalse(gate.visible, "closes after 60 s")
        assertTrue(feed2(gate, good)); gate.onKey(RemoteKey.BACK); assertFalse(gate.visible, "and on Back")
    }

    @Test fun onlyFiveRevealsPerMinute_silently() {
        val gate = OwnerPanelGate({ t }, SecureRandom(), SecretSequence({ t }))
        val results = (1..7).map { feed2(gate, good, step = 100).also { gate.close(); t += 100 } }
        assertEquals(listOf(true, true, true, true, true, false, false), results)
        t += 61_000; assertTrue(feed2(gate, good))
    }

    @Test fun pairingCodeIsSingleUseAndOnlyWhileOpen() {
        val gate = OwnerPanelGate({ t }, SecureRandom(), SecretSequence({ t }))
        feed2(gate, good); val c = gate.pairingCode!!
        assertFalse(gate.consumePairing("000000".takeIf { it != c } ?: "000001")); assertTrue(gate.consumePairing(c)); assertFalse(gate.consumePairing(c))
    }

    private fun feed2(g: OwnerPanelGate, keys: List<RemoteKey>, step: Long = 200): Boolean { var done = false; for (k in keys) { t += step; done = g.onKey(k) || done }; return done }
}

class LotKeysTest {
    private val fp = DeviceIdentity.fingerprints(tvFactors())

    @Test fun wrappedLotKeyOpensOnlyForItsLotAndVersion() {
        val dk = LotKeys.newKey(); val lk = LotKeys.newKey(); val lot = LotId("learn", "cm2")
        val w = LotKeys.wrapLotKey(dk, lk, lot, 3)
        assertContentEquals(lk, LotKeys.unwrapLotKey(dk, w, lot, 3))
        assertNull(LotKeys.unwrapLotKey(dk, w, LotId("learn", "6e"), 3), "cannot be moved to another lot")
        assertNull(LotKeys.unwrapLotKey(dk, w, lot, 4)); assertNull(LotKeys.unwrapLotKey(LotKeys.newKey(), w, lot, 3))
        val flipped = w.copyOf().also { it[20] = (it[20].toInt() xor 1).toByte() }
        assertNull(LotKeys.unwrapLotKey(dk, flipped, lot, 3), "tampering is detected")
    }

    @Test fun dataKeyFollowsTheKOfNIdentity() {
        val dk = LotKeys.newKey(); val box = LotKeys.DeviceKeyBox.create(fp, DeviceIdentity.kFor(fp.n), dk)
        assertContentEquals(dk, box.open(fp))
        assertContentEquals(dk, box.open(DeviceIdentity.fingerprints(tvFactors(wifi = "99:99:99:99:99:99"))), "one replaced module: the content still opens")
        assertNull(box.open(DeviceIdentity.fingerprints(tvFactors(wifi = "99:99:99:99:99:99", bt = "98:98:98:98:98:98"))))
        assertNull(box.open(DeviceIdentity.fingerprints(tvFactors(flash = "ELSE", eth = "AA:AA:AA:AA:AA:01", wifi = "01:01:01:01:01:01", sys = "OTHER999", bt = "10:10:10:10:10:10"))))
        val two = DeviceIdentity.fingerprints(RawFactors(flashSerial = "F1", systemSerial = "SYSTEM12"))
        val box2 = LotKeys.DeviceKeyBox.create(two, 2, dk)
        assertContentEquals(dk, box2.open(two)); assertNull(box2.open(DeviceIdentity.fingerprints(RawFactors(flashSerial = "F1", systemSerial = "OTHER123"))))
    }

    @Test fun hkdfMatchesTheRfc5869Vector() {
        val ikm = ByteArray(22) { 0x0b }; val salt = ByteArray(13) { it.toByte() }; val info = ByteArray(10) { (0xf0 + it).toByte() }
        val okm = Hkdf.expand(Hkdf.extract(salt, ikm), info, 42)
        assertEquals("3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865", okm.joinToString("") { "%02x".format(it) })
    }
}

class VaultAndAuditTest {
    private val kdf = Pbkdf2Kdf(1000)

    @Test fun vaultOpensWithThePassphraseOnly_andStoresNoPassphrase() {
        val seed = ByteArray(32) { it.toByte() }
        val blob = OwnerVault.seal(seed, "correct horse".toCharArray(), kdf)
        assertContentEquals(seed, OwnerVault.open(blob, "correct horse".toCharArray(), kdf))
        assertNull(OwnerVault.open(blob, "wrong".toCharArray(), kdf)); assertNull(OwnerVault.open(blob, "correct horse".toCharArray(), Pbkdf2Kdf(2000)))
        assertFalse(String(blob.ciphertext, Charsets.ISO_8859_1).contains("correct"))
    }

    @Test fun unlockGuardSlowsDownThenLocksAndResetsOnSuccess() {
        var t = 0L; val g = UnlockGuard({ t })
        repeat(3) { assertEquals(0, g.waitMs()); g.failure() }
        assertEquals(0, g.waitMs(), "three free attempts")
        g.failure(); assertEquals(1000, g.waitMs()); t += 1000; g.failure(); assertEquals(2000, g.waitMs()); t += 2000; g.failure(); assertEquals(4000, g.waitMs())
        repeat(5) { t += g.waitMs(); g.failure() }
        assertTrue(g.waitMs() >= UnlockGuard.LOCK_MS, "temporary lock after ${UnlockGuard.LOCK_AFTER} failures")
        t += g.waitMs(); g.success(); assertEquals(0, g.waitMs()); assertEquals(0, g.state.failures)
    }

    @Test fun auditChainDetectsEditsAndRemovals() {
        val a = AuditChain(); a.append(1, "openall", "ABCD", "ok"); a.append(2, "unlock", "ABCD", "ok"); a.append(3, "reset-trial", "EFGH", "refused")
        assertTrue(a.verify())
        val edited = AuditChain(a.entries.mapIndexed { i, e -> if (i == 1) e.copy(outcome = "refused") else e })
        assertFalse(edited.verify()); assertFalse(AuditChain(a.entries.filterIndexed { i, _ -> i != 1 }).verify())
    }
}

class OwnerFramesTest {
    @Test fun framesRoundTripAndRejectGarbage() {
        val out = OwnerFrames.hello() + OwnerFrames.encode(OwnerFrames.CHALLENGE, "ab".repeat(16)) + OwnerFrames.encode(OwnerFrames.CHALLENGE_REQUEST)
        val input = ByteArrayInputStream(out)
        assertTrue(OwnerFrames.readHello(input))
        assertEquals("ab".repeat(16), OwnerFrames.read(input)!!.text)
        assertEquals(OwnerFrames.CHALLENGE_REQUEST, OwnerFrames.read(input)!!.type); assertNull(OwnerFrames.read(input))
        assertFalse(OwnerFrames.readHello(ByteArrayInputStream("CBT1".toByteArray())), "other channels are not mistaken for this one")
        assertNull(OwnerFrames.read(ByteArrayInputStream(byteArrayOf(3, 0x7f, 0x00))), "too big / truncated")
        assertFailsWith<IllegalArgumentException> { OwnerFrames.encode(1, ByteArray(OwnerFrames.MAX_PAYLOAD + 1)) }
    }

    @Test fun deviceInfoCarriesTheCodeAndTheFactorSet() {
        val fp = DeviceIdentity.fingerprints(tvFactors())
        val (code, k, back) = OwnerFrames.parseDeviceInfo(OwnerFrames.deviceInfo(DeviceCode.of(fp), fp))!!
        assertEquals(DeviceCode.of(fp), code); assertEquals(DeviceIdentity.kFor(fp.n), k); assertEquals(fp, back)
        assertNull(OwnerFrames.parseDeviceInfo("code=1234\nk=1"))
    }

    @Test fun ownerServiceIsSeparateFromTheExistingOnes() {
        assertNotEquals(castbridge.core.tv.BtProtocol.API_SERVICE_UUID, OwnerFrames.SERVICE_UUID); assertNotEquals(castbridge.core.tv.BtProtocol.SERVICE_UUID, OwnerFrames.SERVICE_UUID)
    }
}
