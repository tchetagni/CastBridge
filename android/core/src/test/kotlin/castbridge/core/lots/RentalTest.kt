package castbridge.core.lots

import castbridge.core.owner.*
import java.io.File
import kotlin.test.*

private const val DAY = 24L * 3600 * 1000
private const val T0 = 1_800_000_000_000L
private const val MIN = 60_000L

/** A TV in a temp folder with its rental ledger, safe and a controllable wall clock. */
private class RentalRig(val fp: Fingerprints = tvFp(), startWall: Long = T0) {
    var wall = startWall
    val dir: File = Kit.tmp()
    val signer = Ed25519Signer(ByteArray(32) { (it + 7).toByte() })
    val issuer = ActivationIssuer(signer)
    val ring = KeyRing(listOf(signer.trusted()))
    val master = ByteArray(32) { (it * 3 + 1).toByte() }
    val license = "lic-secret-1"
    val vault = RentalVault(File(dir, "rental"))
    val ledger = RentalLedger(File(dir, "rental"), TvClock(), RentalConfig(), { wall })
    val installed = ArrayList<Activation>()
    val steps = ArrayList<String>()
    val progressFile = File(dir, "progress/learner.json").also { it.parentFile.mkdirs(); it.writeText("""{"score":42}""") }

    fun seat() = SeatIds.of(license, fp)
    fun rental(product: String = "loc-cm2", bundles: List<String> = listOf("classe-cm2"), start: Long = T0, days: Int = 30, grace: Long = 0, usage: Int = 0, conc: Int = 0,
               period: Long = start, device: Fingerprints = fp): Right.Rental {
        val key = RentalKeys.rentalKey(master, license, SeatIds.of(license, device), product, period)
        return Right.Rental(product, bundles, start, period, days, grace, usage, conc, RentalKeys.makeBox(device, DeviceIdentity.kFor(device.n), key, product, period))
    }
    fun key(product: String = "loc-cm2", period: Long = T0) = RentalKeys.rentalKey(master, license, seat(), product, period)
    fun issue(vararg rights: Right, at: Long = T0, device: Fingerprints = fp, license: String = this.license): Activation {
        val r = ActivationIssuer.Request(ActivationKind.PRODUCTION, DeviceCode.of(device), device, at, rights = rights.toList(), license = license, seat = SeatIds.of(license, device), windowHours = 48)
        val tok = issuer.issue(r).token
        val res = ActivationVerifier(ring).verify(tok, device, at)
        assertIs<ActivationResult.Accepted>(res, "issued activation must verify: $res")
        return Activation.decode(tok)!!
    }
    fun install(a: Activation) = run { installed += a; ledger.install(a, installed.toList(), fp, vault) }
    fun status(): List<RentalStatus> = ledger.status(installed.toList())
    fun sweeper(lots: RentedLots, owned: Set<LotId> = emptySet()) = RentalSweeper(ledger, vault, lots, { installed.toList() }, { owned }, { wall }, { steps += it })
}

private fun tvFp() = DeviceIdentity.fingerprints(RawFactors(flashSerial = "FLASHSERIAL1", flashCid = "cid-1", ethernetMac = "AA:BB:CC:00:11:22", systemSerial = "SYS12345",
    wifiMac = "10:20:30:40:50:60", wifiSysfsPath = "/sys/devices/platform/soc/ffe03000.sd/mmc_host/mmc1/net/wlan0", bluetoothAddress = "11:22:33:44:55:66"))
private fun otherFp() = DeviceIdentity.fingerprints(RawFactors(flashSerial = "OTHERFLASH", flashCid = "cid-9", ethernetMac = "AA:BB:CC:99:99:99", systemSerial = "SYS99999"))

private class FakeLots : RentedLots {
    val held = LinkedHashMap<LotId, Edition>()
    val removed = ArrayList<LotId>()
    override fun heldLots() = held.keys.toSet()
    override fun lotEdition(id: LotId) = held[id]
    override fun removeLot(id: LotId) { held.remove(id); removed += id }
}

private val CM2 = LotId("learn", "cm2")
private val CM2Q = LotId("quiz", "cm2")

class RentalRightTest {
    @Test fun lineRoundTripsAndNewKindDoesNotChangeOtherLines() {
        val rig = RentalRig()
        val a = rig.issue(Right.Purchase("p1", listOf("quiz-a"), T0), rig.rental())
        val lines = a.canonicalPayload().lines().filter { it.startsWith("right=") }
        assertEquals(2, lines.size); assertTrue(lines[0].startsWith("right=purchase|p1|quiz-a|")); assertTrue(lines[1].startsWith("right=rental|loc-cm2|classe-cm2|"))
        val back = Activation.decode(a.encode())!!
        assertEquals(a.rights, back.rights)
        assertEquals(a.rights.filterIsInstance<Right.Rental>().single().endsAt, T0 + 30 * DAY)
    }

    @Test fun anOldDeviceThatDoesNotKnowTheRightGrantsNothingNeverEverything() {
        val rig = RentalRig()
        // (1) a build that has the right but is not given any rental evaluation (the old call): the line grants nothing
        val a = rig.issue(rig.rental())
        val acc = TvGate.evaluate(listOf(a), emptyList(), T0 + DAY).access
        assertTrue(acc.rented.isEmpty() && acc.granted.isEmpty() && acc.purchased.isEmpty(), "no rental evaluation: nothing granted")
        // (2) a right of a kind nobody knows yet is kept verbatim, signed, accepted, and grants nothing
        val future = Right.Unknown("hologram|prod|a,b|1|2")
        val raw = Activation.payload(ActivationKind.PRODUCTION, Subject.TV, rig.signer.keyId, T0, "ab".repeat(8), T0, T0, T0 + 48 * 3_600_000L, "lic-1", rig.seat(), DeviceIdentity.kFor(rig.fp.n), rig.fp.byKind,
            listOf(Right.Purchase("p1", listOf("quiz-a"), T0), future))
        val sig = java.util.Base64.getEncoder().encodeToString(rig.signer.sign(raw.toByteArray()))
        val act = Activation(ActivationKind.PRODUCTION, Subject.TV, rig.signer.keyId, T0, "ab".repeat(8), T0, T0, T0 + 48 * 3_600_000L, "lic-1", rig.seat(), DeviceIdentity.kFor(rig.fp.n), rig.fp.byKind,
            listOf(Right.Purchase("p1", listOf("quiz-a"), T0), future), sig)
        assertTrue(future in Activation.decode(act.encode())!!.rights, "unknown right survives the canonical rebuild")
        assertIs<ActivationResult.Accepted>(ActivationVerifier(rig.ring).verify(act.encode(), rig.fp, T0))
        val access = TvGate.evaluate(listOf(act), emptyList(), T0).access
        assertEquals(setOf("quiz-a"), access.granted, "only the purchase counts")
    }

    @Test fun boundsAreEnforcedByTheIssuerAndByTheDevice() {
        val rig = RentalRig()
        assertFailsWith<IssueException> { rig.issue(rig.rental(days = 367)) }
        assertFailsWith<IssueException> { rig.issue(rig.rental(days = 0)) }
        assertFailsWith<IssueException> { rig.issue(rig.rental(grace = 31 * DAY)) }
        assertFailsWith<IssueException> { rig.issue(rig.rental(conc = 21)) }
        assertFailsWith<IssueException> { rig.issue(Right.Unknown("hologram|x")) }
        // a hand-made token that the issuer would refuse is refused by the device (BAD_RIGHTS)
        val bad = rig.rental().copy(durationDays = 400)
        val payload = Activation.payload(ActivationKind.PRODUCTION, Subject.TV, rig.signer.keyId, T0, "ab".repeat(8), T0, T0, T0 + 48 * 3_600_000L, "lic-1", rig.seat(), DeviceIdentity.kFor(rig.fp.n), rig.fp.byKind, listOf(bad))
        val sig = java.util.Base64.getEncoder().encodeToString(rig.signer.sign(payload.toByteArray()))
        val tok = Activation(ActivationKind.PRODUCTION, Subject.TV, rig.signer.keyId, T0, "ab".repeat(8), T0, T0, T0 + 48 * 3_600_000L, "lic-1", rig.seat(), DeviceIdentity.kFor(rig.fp.n), rig.fp.byKind, listOf(bad), sig).encode()
        val r = ActivationVerifier(rig.ring).verify(tok, rig.fp, T0)
        assertIs<ActivationResult.Rejected>(r); assertEquals(Rejection.BAD_RIGHTS, r.reason)
    }
}

class RentalClockTest {
    @Test fun durationAndExpiryAreExactToTheMillisecond() {
        val rig = RentalRig(); rig.install(rig.issue(rig.rental(days = 30)))
        val end = T0 + 30 * DAY
        rig.wall = end - 1; assertEquals(RentalState.ACTIVE, rig.status().single().state)
        rig.ledger.observe()
        rig.wall = end; assertEquals(RentalState.EXPIRED, rig.status().single().state); assertEquals(ExpiryReason.DATE, rig.status().single().reason)
    }

    @Test fun graceKeepsItUsableThenExpires() {
        val rig = RentalRig(); rig.install(rig.issue(rig.rental(days = 10, grace = 2 * DAY)))
        rig.wall = T0 + 10 * DAY; assertEquals(RentalState.GRACE, rig.status().single().state); assertTrue(rig.status().single().usable)
        rig.wall = T0 + 12 * DAY - 1; assertEquals(RentalState.GRACE, rig.status().single().state)
        rig.wall = T0 + 12 * DAY; assertEquals(RentalState.EXPIRED, rig.status().single().state)
    }

    @Test fun anAccountActivatedBySuperUnlimitedKeepsEveryRentalForGood() {
        val rig = RentalRig()
        // two rentals with a short date, a usage ceiling and a limit of ONE at a time: for an ordinary account all of that bites
        rig.install(rig.issue(rig.rental(days = 10, grace = 2 * DAY, usage = 600, conc = 1), rig.rental("loc-b", listOf("quiz-cm2"), days = 10, conc = 1), Right.Super("super-illimite", T0)))
        rig.wall = T0 + 400 * DAY                                                       // far past the end, the grace and the 45-day clock doubt
        val all = rig.status()
        assertEquals(2, all.size)
        all.forEach { assertEquals(RentalState.ACTIVE, it.state); assertTrue(it.usable); assertEquals(RentalEngine.PERMANENT, it.message); assertNull(it.remainingMs); assertEquals(RentalWarning.NONE, it.warning) }
        assertTrue(RentalEngine.superUnlimited(rig.installed))
    }

    @Test fun anOrdinaryAccountSeesTheSameRentalsEnd() {
        val rig = RentalRig(); rig.install(rig.issue(rig.rental(days = 10, grace = 2 * DAY)))
        rig.wall = T0 + 400 * DAY
        assertNotEquals(RentalState.ACTIVE, rig.status().single().state)
        assertFalse(RentalEngine.superUnlimited(rig.installed))
    }

    @Test fun superUnlimitedCannotBringBackARentalAlreadySweptAway() {
        val rig = RentalRig(); val a = rig.issue(rig.rental(days = 10), Right.Super("super-illimite", T0))
        val c = RentalEngine.contracts(listOf(a)).single()
        // the key of a swept rental is destroyed and its files removed: nothing can resurrect it
        val st = RentalEngine.evaluate(listOf(c), RentalInputs(JudgedTime(T0, null), expired = mapOf(c.key to ExpiryReason.DATE), superUnlimited = true)).single()
        assertEquals(RentalState.EXPIRED, st.state)
    }

    @Test fun aClockSetBackNeverExtendsAndIsSuspendedVisibly() {
        val rig = RentalRig(); rig.install(rig.issue(rig.rental(days = 30)))
        rig.wall = T0 + 20 * DAY; rig.ledger.observe()
        rig.wall = T0 + 2 * DAY                                       // someone sets the clock back
        val s = rig.status().single()
        assertEquals(RentalState.SUSPENDED, s.state); assertEquals(ClockDoubt.BEHIND, s.doubt); assertTrue(s.message.contains("Vérifiez l'heure de la TV"))
        assertFalse(s.usable)
        rig.wall = T0 + 29 * DAY; assertEquals(RentalState.ACTIVE, rig.status().single().state)
        assertEquals(T0 + 30 * DAY - rig.ledger.nowMs(), rig.status().single().remainingMs)
        assertTrue(rig.status().single().remainingMs!! <= 10 * DAY + 1, "remaining time was never lengthened by the rollback")
    }

    @Test fun aRolledBackClockCannotRevivePastTheHighestTimeSeen() {
        val rig = RentalRig(); rig.install(rig.issue(rig.rental(days = 5)))
        rig.wall = T0 + 6 * DAY; rig.ledger.observe()
        rig.wall = T0 + DAY                                           // back to day 1
        val s = rig.status().single()
        assertEquals(RentalState.EXPIRED, s.state, "the highest time seen already proves it ended: never revived by a rollback")
    }

    @Test fun aFarAheadClockSuspendsInsteadOfDeletingAndAUserConfirmationReleasesIt() {
        val rig = RentalRig(); rig.install(rig.issue(rig.rental(days = 30)))
        rig.wall = T0 + 200 * DAY                                      // glitch: the TV thinks 200 days passed
        val s = rig.status().single()
        assertEquals(RentalState.SUSPENDED, s.state); assertEquals(ClockDoubt.AHEAD, s.doubt)
        assertEquals(emptyList(), rig.ledger.markEnding(rig.status()), "nothing is ended because of a doubtful clock")
        // the user confirms the time is right (TV really was off for 200 days): the rental is then judged ended
        assertTrue(rig.ledger.confirmClockAhead())
        assertEquals(RentalState.EXPIRED, rig.status().single().state)
    }

    @Test fun aClockBehindCannotBeConfirmed() {
        val rig = RentalRig(); rig.install(rig.issue(rig.rental(days = 30)))
        rig.wall = T0 + 10 * DAY; rig.ledger.observe(); rig.wall = T0
        assertFalse(rig.ledger.confirmClockAhead())
    }

    @Test fun anUnreasonableJumpIsNeverBelievedByConfirmation() {
        val rig = RentalRig(); rig.install(rig.issue(rig.rental(days = 30)))
        rig.wall = T0 + 500 * DAY
        assertFalse(rig.ledger.confirmClockAhead())
        assertEquals(RentalState.SUSPENDED, rig.status().single().state)
    }

    @Test fun anActivationFromTheFutureLiftsAFarAheadDoubt() {
        val rig = RentalRig(); rig.install(rig.issue(rig.rental(days = 30)))
        rig.wall = T0 + 100 * DAY
        assertEquals(RentalState.SUSPENDED, rig.status().single().state)
        // a signed activation issued at that real time proves the time (the floor): the TV believes it
        rig.install(rig.issue(rig.rental(product = "loc-other", start = T0 + 100 * DAY, days = 10), at = T0 + 100 * DAY))
        assertEquals(RentalState.EXPIRED, rig.status().first { it.contract.productId == "loc-cm2" }.state)
        assertEquals(RentalState.ACTIVE, rig.status().first { it.contract.productId == "loc-other" }.state)
    }

    @Test fun usageCeilingEndsItBeforeTheDateAndWorksWhateverTheClockSays() {
        val rig = RentalRig(); rig.install(rig.issue(rig.rental(days = 30, usage = 120)))
        rig.ledger.markRented("loc-cm2@$T0", CM2, LotMeta(CM2, 1, 10, "a".repeat(64), "CM2"), LotFamilies.explicit(emptySet(), setOf("learn:cm2")))
        rig.wall = T0 + DAY
        var s = rig.ledger.recordUsage(CM2, 100, rig.installed).single()
        assertEquals(RentalState.ACTIVE, s.state); assertEquals(20L, s.remainingUsageMinutes)
        assertEquals(RentalWarning.HOUR_1, s.warning)
        rig.wall = T0 - 10 * DAY                                       // clock set far back: usage still counts
        s = rig.ledger.recordUsage(CM2, 20, rig.installed).single()
        assertEquals(RentalState.EXPIRED, s.state); assertEquals(ExpiryReason.USAGE, s.reason)
    }

    @Test fun theDateEndsItWhenUsageIsNotReached() {
        val rig = RentalRig(); rig.install(rig.issue(rig.rental(days = 3, usage = 100000)))
        rig.wall = T0 + 3 * DAY
        assertEquals(ExpiryReason.DATE, rig.status().single().reason)
    }

    @Test fun warningsAndCountdownInFrench() {
        val rig = RentalRig(); rig.install(rig.issue(rig.rental(days = 30)))
        fun at(ms: Long): RentalStatus { rig.wall = T0 + 30 * DAY - ms; return rig.status().single() }
        assertEquals(RentalWarning.NONE, at(8 * DAY).warning)
        assertEquals(RentalWarning.DAYS_7, at(7 * DAY).warning)
        assertEquals(RentalWarning.HOURS_24, at(24 * 3600_000L).warning)
        assertEquals(RentalWarning.HOUR_1, at(3600_000L).warning)
        rig.wall = T0 + 18 * DAY; assertEquals("Il vous reste 12 jours", rig.status().single().message)
        rig.wall = T0 + 29 * DAY + 19 * 3600_000L; assertEquals("Il vous reste 5 h", rig.status().single().message)
        rig.wall = T0 + 30 * DAY - 40 * MIN; assertEquals("Il vous reste 40 min", rig.status().single().message)
    }

    @Test fun anAcquiredRentalCountsFromActivationNotFromFirstOpening() {
        val rig = RentalRig(); rig.install(rig.issue(rig.rental(days = 10)))
        rig.wall = T0 + 9 * DAY                                        // never opened, still ticking
        assertEquals(RentalState.ACTIVE, rig.status().single().state)
        rig.wall = T0 + 10 * DAY; assertEquals(RentalState.EXPIRED, rig.status().single().state)
    }

    @Test fun aPostDatedRentalIsNotStartedYet() {
        val rig = RentalRig(); rig.install(rig.issue(rig.rental(start = T0 + 5 * DAY, days = 10)))
        assertEquals(RentalState.NOT_STARTED, rig.status().single().state)
        rig.wall = T0 + 5 * DAY; assertEquals(RentalState.ACTIVE, rig.status().single().state)
    }

    @Test fun clockWithNoReferenceYetTrustsTheWallClock() {
        val j = RentalEngine.judge(TvClock(), 123L); assertEquals(123L, j.now); assertNull(j.doubt)
    }

    @Test fun ledgerSurvivesARestartWithTheHighWaterMark() {
        val rig = RentalRig(); rig.install(rig.issue(rig.rental(days = 30)))
        rig.wall = T0 + 25 * DAY; rig.ledger.observe(); rig.ledger.recordUsage(CM2, 5, rig.installed)
        val again = RentalLedger(File(rig.dir, "rental"), TvClock(), RentalConfig(), { T0 + DAY })
        assertEquals(T0 + 25 * DAY, again.clock.lastSeen, "high-water mark persisted: a reboot with a wrong clock cannot go back")
        assertEquals(RentalState.SUSPENDED, again.status(rig.installed).single().state)
    }
}

class RentalRenewalTest {
    @Test fun renewalExtendsTheSameRentalWithoutDuplicate() {
        val rig = RentalRig()
        val first = rig.issue(rig.rental(days = 30), at = T0)
        rig.install(first)
        rig.wall = T0 + 20 * DAY
        val renewal = rig.issue(rig.rental(start = T0 + 20 * DAY, period = T0, days = 30), at = T0 + 20 * DAY)
        rig.install(renewal); rig.install(renewal)                           // the same file read twice
        val all = RentalEngine.contracts(rig.installed)
        assertEquals(1, all.size, "one contract, not two")
        assertEquals(T0 + 60 * DAY, all.single().endsAt, "extends from the previous end: 30 + 30 days")
        assertEquals(1, rig.status().size)
        assertEquals("loc-cm2@$T0", rig.status().single().key)
        assertTrue(rig.vault.getKey("loc-cm2@$T0")!!.contentEquals(rig.key()), "same key: the already delivered lots keep working")
    }

    @Test fun readingTheSameActivationTwiceDoesNotExtend() {
        val rig = RentalRig(); val a = rig.issue(rig.rental(days = 30)); rig.install(a); rig.install(a)
        assertEquals(T0 + 30 * DAY, RentalEngine.contracts(rig.installed).single().endsAt)
    }

    @Test fun aLateRenewalCannotReviveAnEndedRental() {
        val rig = RentalRig(); rig.install(rig.issue(rig.rental(days = 10)))
        rig.wall = T0 + 12 * DAY
        val late = rig.issue(rig.rental(start = T0 + 12 * DAY, period = T0, days = 30), at = T0 + 12 * DAY)
        val out = rig.install(late)
        assertEquals(T0 + 10 * DAY, RentalEngine.contracts(rig.installed).single().endsAt, "late line ignored")
        assertEquals(RentalState.EXPIRED, rig.status().single().state); assertTrue(out.values.single().contains("terminée") || out.values.single().isNotEmpty())
        // taking the rental again is a NEW rental (new period, new key)
        val fresh = rig.issue(rig.rental(start = T0 + 12 * DAY, days = 30), at = T0 + 12 * DAY); rig.install(fresh)
        assertEquals(2, rig.status().size); assertEquals(RentalState.ACTIVE, rig.status().last().state)
    }

    @Test fun usageCeilingsAddUpAcrossARenewal() {
        val rig = RentalRig(); rig.install(rig.issue(rig.rental(days = 30, usage = 100)))
        rig.install(rig.issue(rig.rental(start = T0 + DAY, period = T0, days = 30, usage = 50), at = T0 + DAY))
        assertEquals(150L, RentalEngine.contracts(rig.installed).single().maxUsageMinutes)
    }

    @Test fun simultaneousLimitKeepsTheOldestRentalsUsable() {
        val rig = RentalRig()
        rig.install(rig.issue(rig.rental("loc-a", start = T0, days = 30, conc = 1), rig.rental("loc-b", start = T0 + DAY, days = 30, conc = 1)))
        val st = rig.status().associateBy { it.contract.productId }
        assertEquals(RentalState.ACTIVE, st.getValue("loc-a").state)
        assertEquals(RentalState.OVER_LIMIT, st.getValue("loc-b").state)
    }
}

class RentalKeysTest {
    @Test fun boxOpensOnTheRightDeviceAndToleratesAReplacedModuleButNotAnotherTv() {
        val rig = RentalRig(); val key = rig.key(); val box = RentalKeys.makeBox(rig.fp, DeviceIdentity.kFor(rig.fp.n), key, "loc-cm2", T0)
        assertTrue(key.contentEquals(RentalKeys.openBox(box, rig.fp, "loc-cm2", T0)!!))
        val swapped = Fingerprints(rig.fp.byKind.toMutableMap().also { it[FactorKind.WIFI] = "different" })
        assertNotNull(RentalKeys.openBox(box, swapped, "loc-cm2", T0), "k of n tolerance")
        assertNull(RentalKeys.openBox(box, otherFp(), "loc-cm2", T0))
        assertNull(RentalKeys.openBox(box, rig.fp, "loc-other", T0), "the box is bound to its product and period")
        assertEquals(box, RentalKeys.makeBox(rig.fp, DeviceIdentity.kFor(rig.fp.n), key, "loc-cm2", T0), "deterministic: same inputs, same bytes")
    }

    @Test fun lotFileIsBoundToItsLotVersionAndKey() {
        val rig = RentalRig(); val plain = Kit.bytes(1, 2000); val sealed = RentalKeys.seal(rig.key(), CM2, 3, plain)
        assertContentEquals(plain, RentalKeys.open(rig.key(), CM2, 3, sealed))
        assertNull(RentalKeys.open(rig.key(), CM2, 4, sealed)); assertNull(RentalKeys.open(rig.key(), CM2Q, 3, sealed))
        assertNull(RentalKeys.open(rig.key("loc-x"), CM2, 3, sealed)); assertNull(RentalKeys.open(null, CM2, 3, sealed))
        assertNull(RentalKeys.open(rig.key(), CM2, 3, sealed.copyOf().also { it[20] = (it[20] + 1).toByte() }))
        assertFalse(sealed.toList().windowed(16).any { w -> plain.toList().windowed(16).first() == w }, "no plaintext in the file")
    }
}

class RentalSweepTest {
    private fun setup(rig: RentalRig, lots: FakeLots, days: Int = 10, usage: Int = 0): String {
        val a = rig.issue(rig.rental(days = days, usage = usage)); rig.install(a)
        val key = "loc-cm2@$T0"
        for ((id, v) in listOf(CM2 to 1, CM2Q to 1)) {
            lots.held[id] = Edition.FULL
            rig.vault.putLot(id, v, RentalKeys.seal(rig.key(), id, v, Kit.bytes(id.scope.length + v, 500)))
            rig.ledger.markRented(key, id, LotMeta(id, v, 500, "a".repeat(64), id.scope), LotFamilies.explicit(emptySet(), setOf("learn:cm2", "quiz:cm2")))
        }
        return key
    }

    @Test fun keyIsDestroyedBeforeTheFilesAndEverythingIsRemoved() {
        val rig = RentalRig(); val lots = FakeLots(); val key = setup(rig, lots)
        rig.wall = T0 + 10 * DAY
        val rep = rig.sweeper(lots).sweep(SweepTrigger.APP_START)
        assertEquals(listOf("key:$key", "lot:learn:cm2", "lot:quiz:cm2"), rig.steps, "key first, then the lots")
        assertFalse(rig.vault.hasKey(key)); assertEquals(setOf(CM2, CM2Q), lots.removed.toSet()); assertTrue(rig.vault.heldLotFolders().isEmpty())
        assertEquals(listOf(RentalEngine.ENDED), rep.notices); assertEquals(RentalPhase.DONE, rig.ledger.phase(key))
        assertEquals("Location terminée : ce contenu n'est plus disponible. Reprendre la location ?", RentalEngine.ENDED)
    }

    @Test fun anUnlimitedAccountThatIsNotSuperAdministratorStillSeesItsRentalsEndAndBeDeleted() {
        // "illimité" = a permanent usage account (bought bundle "tout"): it opens everything for good, but it is NOT SUPER_UNLIMITED, so a rental still expires and is swept
        val rig = RentalRig(); val lots = FakeLots(); val key = setup(rig, lots)
        rig.install(rig.issue(Right.Purchase("illimite", listOf(Right.ALL_BUNDLE), T0), license = "lic-illimite-1"))
        val access = TvGate.evaluate(rig.installed.toList(), emptyList(), T0 + 5_000 * DAY)
        assertEquals("Illimité", access.label); assertFalse(access.superUnlimited); assertTrue(Right.ALL_BUNDLE in access.access.granted)
        assertFalse(RentalEngine.superUnlimited(rig.installed))
        rig.wall = T0 + 10 * DAY; rig.sweeper(lots).sweep(SweepTrigger.APP_START)
        assertEquals(setOf(CM2, CM2Q), lots.removed.toSet(), "the ended rental is deleted in an unlimited account"); assertEquals(RentalPhase.DONE, rig.ledger.phase(key))
    }

    @Test fun theSweepNeverDeletesTheRentalsOfASuperUnlimitedAccount_butDeletesThoseOfAnyOther() {
        val ordinary = RentalRig(); val lots1 = FakeLots(); val key1 = setup(ordinary, lots1)
        ordinary.wall = T0 + 10 * DAY; ordinary.sweeper(lots1).sweep(SweepTrigger.APP_START)
        assertEquals(setOf(CM2, CM2Q), lots1.removed.toSet(), "an ordinary account: the ended rental is deleted automatically"); assertEquals(RentalPhase.DONE, ordinary.ledger.phase(key1))
        val sup = RentalRig(); val lots2 = FakeLots(); val key2 = setup(sup, lots2)
        sup.install(sup.issue(Right.Super("super-illimite", T0), license = "lic-super-1"))            // activated by the super administrator code
        sup.wall = T0 + 10 * DAY; sup.sweeper(lots2).sweep(SweepTrigger.APP_START)
        assertTrue(lots2.removed.isEmpty(), "super administrator account: nothing is deleted"); assertTrue(sup.vault.hasKey(key2)); assertTrue(sup.steps.isEmpty())
    }

    @Test fun aPowerCutAtEveryStepResumesAndFinishes() {
        for (cutAt in 0..2) {
            val rig = RentalRig(); val lots = FakeLots(); val key = setup(rig, lots); rig.wall = T0 + 10 * DAY
            var n = 0
            val cutting = RentalSweeper(rig.ledger, rig.vault, lots, { rig.installed.toList() }, { emptySet() }, { rig.wall }, { if (n++ == cutAt) throw IllegalStateException("coupure") })
            assertTrue(cutting.sweep(SweepTrigger.APP_START).failures.isNotEmpty(), "a failing contract is reported, not thrown")
            // reboot: a new ledger from disk, a new sweeper, no cut this time
            val ledger2 = RentalLedger(File(rig.dir, "rental"), TvClock(), RentalConfig(), { rig.wall })
            RentalSweeper(ledger2, rig.vault, lots, { rig.installed.toList() }, { emptySet() }, { rig.wall }).sweep(SweepTrigger.APP_START)
            assertFalse(rig.vault.hasKey(key), "cut at $cutAt"); assertTrue(lots.held.isEmpty(), "cut at $cutAt"); assertEquals(RentalPhase.DONE, ledger2.phase(key))
        }
    }

    @Test fun afterTheKeyIsGoneACopiedOrRestoredFileIsNoise() {
        val rig = RentalRig(); val lots = FakeLots(); val key = setup(rig, lots)
        val copy = rig.vault.lotFile(CM2, 1)!!.readBytes()
        assertNotNull(rig.vault.readLot(key, CM2, 1), "usable during the rental")
        rig.wall = T0 + 10 * DAY; rig.sweeper(lots).sweep(SweepTrigger.PERIODIC)
        // the user restores the file from a backup, even rewrites the vault key from the activation: still useless
        rig.vault.putLot(CM2, 1, copy)
        assertNull(rig.vault.readLot(key, CM2, 1))
        val re = rig.install(rig.installed.first())
        assertFalse(rig.vault.hasKey(key), "the ended rental key is never reopened from the activation: $re")
        // and from another TV's rig: the key cannot even be derived without the box for that TV
        assertNull(RentalKeys.open(RentalKeys.openBox(rig.rental().box, otherFp(), "loc-cm2", T0), CM2, 1, copy))
    }

    @Test fun aWipedTvWithTheOldActivationStillCannotReopenAnEndedRental() {
        val rig = RentalRig(); val lots = FakeLots(); setup(rig, lots)
        rig.wall = T0 + 10 * DAY
        val fresh = RentalLedger(File(rig.dir, "fresh"), TvClock(), RentalConfig(), { rig.wall })    // app data wiped: no tombstone
        val vault2 = RentalVault(File(rig.dir, "fresh"))
        val out = fresh.install(rig.installed.first(), rig.installed, rig.fp, vault2)
        assertFalse(vault2.hasKey("loc-cm2@$T0"), "ended by the dates in the signed line itself: $out")
    }

    @Test fun onlyRentedExpiredLotsAreTouched() {
        val rig = RentalRig(); val lots = FakeLots(); val key = setup(rig, lots)
        val purchased = LotId("learn", "6e"); val free = LotId("langues", "fr-a0"); val trial = LotId("learn", "cm2-trial"); val otherRent = LotId("quiz", "cp")
        lots.held[purchased] = Edition.FULL; lots.held[free] = Edition.FULL; lots.held[trial] = Edition.TRIAL; lots.held[otherRent] = Edition.FULL
        // a second rental, still running, with its own lot
        rig.install(rig.issue(rig.rental("loc-cp", listOf("classe-cp"), start = T0, days = 100), at = T0))
        rig.ledger.markRented("loc-cp@$T0", otherRent, LotMeta(otherRent, 1, 1, "b".repeat(64), "CP"), LotFamilies.explicit(emptySet(), setOf("quiz:cp")))
        // other people's files next to the safe
        val strayInLots = File(rig.dir, "rental/lots/learn_cm2/notes-perso.txt").also { it.parentFile.mkdirs(); it.writeText("mine") }
        val sibling = File(rig.dir, "rental/lots-user/photo.jpg").also { it.parentFile.mkdirs(); it.writeText("photo") }
        val outside = File(rig.dir, "Download/CastBridge/activation").also { it.parentFile.mkdirs(); it.writeText("tok") }
        rig.wall = T0 + 10 * DAY
        rig.sweeper(lots).sweep(SweepTrigger.LEARN_SCREEN)
        assertEquals(setOf(CM2, CM2Q), lots.removed.toSet(), "ONLY the two lots of the ended rental")
        assertEquals(setOf(purchased, free, trial, otherRent), lots.held.keys)
        assertTrue(strayInLots.isFile && sibling.isFile && outside.isFile && rig.progressFile.isFile, "no foreign file touched")
        assertTrue(rig.vault.hasKey("loc-cp@$T0"), "the running rental keeps its key"); assertEquals(RentalPhase.DONE, rig.ledger.phase(key))
    }

    @Test fun aLotAlreadyCoveredByAPurchaseIsNeverRemoved() {
        val rig = RentalRig(); val lots = FakeLots(); setup(rig, lots)
        rig.wall = T0 + 10 * DAY
        val rep = rig.sweeper(lots, owned = setOf(CM2)).sweep(SweepTrigger.APP_START)
        assertEquals(listOf(CM2), rep.keptBecauseOwned); assertTrue(CM2 in lots.held); assertTrue(CM2Q !in lots.held)
    }

    @Test fun markRentedRefusesWhatIsAlreadyOwnedAndWhatIsFree() {
        val rig = RentalRig(); rig.install(rig.issue(rig.rental()))
        val key = "loc-cm2@$T0"; val fam = LotFamilies.explicit(setOf("langues:fr-a0"), setOf("learn:cm2"))
        assertNotNull(rig.ledger.markRented(key, CM2, LotMeta(CM2, 1, 1, "a".repeat(64), "CM2"), fam, otherwiseAllowed = setOf(CM2)))
        val free = LotId("langues", "fr-a0")
        assertTrue(rig.ledger.markRented(key, free, LotMeta(free, 1, 1, "a".repeat(64), "Français A0"), fam)!!.contains("libre"))
        assertNull(rig.ledger.markRented(key, CM2, LotMeta(CM2, 1, 1, "a".repeat(64), "CM2"), fam))
    }

    @Test fun releasedLotIsNeverTouchedByTheSweep() {
        val rig = RentalRig(); val lots = FakeLots(); val key = setup(rig, lots)
        rig.ledger.release(key, CM2)                                  // a purchase arrived and its normal copy was installed
        rig.wall = T0 + 10 * DAY; rig.sweeper(lots).sweep(SweepTrigger.APP_START)
        assertTrue(CM2 in lots.held); assertTrue(CM2Q !in lots.held)
    }

    @Test fun progressIsKeptAndTheJournalHasNoPersonalData() {
        val rig = RentalRig(); val lots = FakeLots(); setup(rig, lots)
        rig.wall = T0 + 10 * DAY; rig.sweeper(lots).sweep(SweepTrigger.APP_START)
        assertEquals("""{"score":42}""", rig.progressFile.readText(), "the score stays, only the content goes")
        val j = rig.ledger.journal(); assertEquals(3, j.size)
        assertEquals(listOf("cle", "fichiers", "fichiers"), j.map { it.step }); assertTrue(j.all { it.reason == "date" && it.at == T0 + 10 * DAY })
        val disk = File(rig.dir, "rental/rentals.json").readText()
        val secrets = listOf(rig.license, rig.seat(), DeviceCode.of(rig.fp)) + rig.fp.byKind.values + listOf("SYS12345", "FLASHSERIAL1", "AA:BB:CC")
        secrets.forEach { assertFalse(disk.contains(it), "journal/ledger must not contain $it") }
        assertTrue(File(rig.dir, "rental").walkTopDown().filter { it.isFile }.none { f -> secrets.any { f.readText().contains(it) } || f.extension == "key" })
    }

    @Test fun aLessonInProgressDefersTheEndByAtMostFifteenMinutes() {
        val rig = RentalRig(); val lots = FakeLots(); val key = setup(rig, lots)
        rig.wall = T0 + 10 * DAY + 5 * MIN
        var rep = rig.sweeper(lots).sweep(SweepTrigger.PERIODIC, lessonActive = true)
        assertEquals(listOf(key), rep.deferred); assertTrue(rig.vault.hasKey(key)); assertEquals(RentalState.EXPIRED, rig.status().single().state, "no NEW lesson can open")
        rig.wall = T0 + 10 * DAY + 14 * MIN + 59_000; rep = rig.sweeper(lots).sweep(SweepTrigger.PERIODIC, lessonActive = true); assertEquals(listOf(key), rep.deferred)
        rig.wall = T0 + 10 * DAY + 15 * MIN; rep = rig.sweeper(lots).sweep(SweepTrigger.PERIODIC, lessonActive = true)
        assertTrue(rep.deferred.isEmpty()); assertFalse(rig.vault.hasKey(key))
        // and the end of the lesson triggers the sweep right away
        val rig2 = RentalRig(); val lots2 = FakeLots(); val key2 = setup(rig2, lots2); rig2.wall = T0 + 10 * DAY + MIN
        rig2.sweeper(lots2).sweep(SweepTrigger.PERIODIC, lessonActive = true); rig2.sweeper(lots2).sweep(SweepTrigger.LESSON_END)
        assertFalse(rig2.vault.hasKey(key2))
    }

    @Test fun usageEndedRentalIsSweptToo() {
        val rig = RentalRig(); val lots = FakeLots(); val key = setup(rig, lots, days = 30, usage = 60)
        rig.wall = T0 + DAY; rig.ledger.recordUsage(CM2, 60, rig.installed)
        rig.sweeper(lots).sweep(SweepTrigger.PHONE_RECONNECT)
        assertFalse(rig.vault.hasKey(key)); assertEquals("usage", rig.ledger.journal().first().reason); assertTrue(lots.held.isEmpty())
    }

    @Test fun periodicTickRunsEverySixHoursOnly() {
        val rig = RentalRig(); val lots = FakeLots(); setup(rig, lots)
        val sw = rig.sweeper(lots)
        assertNotNull(sw.tick()); rig.wall += 5 * 3600_000L; assertNull(sw.tick()); rig.wall += 3600_000L; assertNotNull(sw.tick())
    }

    @Test fun aSweepWithNothingEndedChangesNothing() {
        val rig = RentalRig(); val lots = FakeLots(); val key = setup(rig, lots)
        rig.wall = T0 + 5 * DAY; val rep = rig.sweeper(lots).sweep(SweepTrigger.APP_START)
        assertTrue(rep.endedNow.isEmpty() && rep.lotsRemoved.isEmpty() && rig.vault.hasKey(key) && lots.removed.isEmpty())
    }

    @Test fun vaultRefusesPathTricks() {
        val v = RentalVault(Kit.tmp()); assertFalse(v.putKey("../../etc@1", ByteArray(32))); assertFalse(v.destroyKey("a/b@1"))
        assertNull(v.lotFile(LotId("learn", "../x"), 1)); assertFalse(v.deleteLotFiles(LotId("..", "x")))
    }
}

class RentalPolicyTest {
    private val fam = LotFamilies.explicit(free = setOf("langues:fr-a0"), reserved = setOf("learn:cm2", "quiz:cm2"))
    private fun meta(f: String, s: String, ed: Edition = Edition.FULL) = LotMeta(LotId(f, if (ed == Edition.TRIAL) "$s-trial" else s), 1, 100, "a".repeat(64), "$f $s", 0, ed)

    @Test fun aFreeLotCanNeverBeRented() {
        assertTrue(RentalPolicy.refusal(meta("langues", "fr-a0"), fam)!!.contains("libre"))
        assertNull(RentalPolicy.refusal(meta("learn", "cm2"), fam))
        assertNotNull(RentalPolicy.refusal(meta("learn", "inconnu"), fam), "unknown family: refused (fail closed)")
        assertNotNull(RentalPolicy.refusal(meta("learn", "cm2", Edition.TRIAL), fam), "a trial sample is not rented")
        val bundles = BundleCatalog(listOf(Bundle("classe-cm2", "classe", setOf("learn:cm2", "quiz:cm2")), Bundle("langue-fr", "langue", setOf("langues:fr-a0"))))
        val cat = listOf(meta("learn", "cm2"), meta("quiz", "cm2"), meta("langues", "fr-a0"))
        assertEquals(emptyList(), RentalPolicy.refusals(listOf("classe-cm2"), bundles, cat, fam))
        assertEquals(1, RentalPolicy.refusals(listOf("langue-fr"), bundles, cat, fam).size)
        assertEquals(listOf("bouquet inconnu : zzz"), RentalPolicy.refusals(listOf("zzz"), bundles, cat, fam))
    }

    @Test fun purchaseAndSubscriptionComeFirstAndAnEndedRentalRemovesNothingAcquired() {
        val rig = RentalRig()
        val a = rig.issue(Right.Purchase("p-cm2", listOf("classe-cm2"), T0), rig.rental(bundles = listOf("classe-cm2", "quiz-cm2")))
        rig.install(a)
        val bundles = BundleCatalog(listOf(Bundle("classe-cm2", "classe", setOf("learn:cm2", "quiz:cm2")), Bundle("quiz-cm2", "quiz", setOf("quiz:cm2"))))
        rig.wall = T0 + DAY
        var access = TvGate.evaluate(rig.installed, emptyList(), rig.ledger.nowMs(), rig.status()).access
        assertEquals(setOf("classe-cm2"), access.purchased); assertEquals(emptySet(), access.rented.intersect(access.purchased), "no double count of an owned bundle")
        assertEquals(setOf("quiz-cm2"), access.rented)
        // the lots the rental still has to carry: nothing the purchase already gives
        val cat = listOf(meta("learn", "cm2"), meta("quiz", "cm2"))
        assertEquals(emptyList(), RentalPolicy.lotsToRent(listOf("quiz-cm2"), bundles, cat, fam, EditionPolicy.allowedFull(access.copy(rented = emptySet()), bundles)))
        // rental ends: the purchase stays
        rig.wall = T0 + 31 * DAY
        access = TvGate.evaluate(rig.installed, emptyList(), rig.ledger.nowMs(), rig.status()).access
        assertEquals(setOf("classe-cm2"), access.granted); assertTrue(EditionPolicy.isAllowed(meta("learn", "cm2"), access, bundles))
        assertFalse(EditionPolicy.isAllowed(meta("learn", "autre"), access, bundles))
    }

    @Test fun rentedBundleOpensItsLotsOnlyWhileUsableAndSubscriptionWinsTheLabel() {
        val rig = RentalRig(); rig.install(rig.issue(rig.rental()))
        val bundles = BundleCatalog(listOf(Bundle("classe-cm2", "classe", setOf("learn:cm2"))))
        rig.wall = T0 + DAY
        val tv = TvGate.evaluate(rig.installed, emptyList(), rig.ledger.nowMs(), rig.status())
        assertEquals("Location en cours", tv.label); assertTrue(EditionPolicy.isAllowed(meta("learn", "cm2"), tv.access, bundles))
        rig.wall = T0 + 40 * DAY
        val ended = TvGate.evaluate(rig.installed, emptyList(), rig.ledger.nowMs(), rig.status())
        assertFalse(EditionPolicy.isAllowed(meta("learn", "cm2"), ended.access, bundles))
        // a subscription covering the bundle: the rental is not counted
        val sub = rig.issue(rig.rental(), Right.Subscription("abo", listOf("classe-cm2"), T0, T0 + 90 * DAY, 0, false), at = T0 + 1)
        val both = TvGate.evaluate(listOf(sub), emptyList(), T0 + DAY, RentalEngine.evaluate(RentalEngine.contracts(listOf(sub)), RentalInputs(JudgedTime(T0 + DAY, null)))).access
        assertTrue(both.rented.isEmpty() && both.subscribed == setOf("classe-cm2"))
    }

    @Test fun reconcileLeavesRentedLotsToTheSweep() {
        val access = Access.TRIAL_ONLY
        val bundles = BundleCatalog(emptyList())
        val rented = meta("learn", "cm2")
        val r = EditionPolicy.reconcile(listOf(rented), access, bundles, rentedLots = setOf(rented.id))
        assertTrue(r.demotions.isEmpty())
        assertEquals(1, EditionPolicy.reconcile(listOf(rented), access, bundles).demotions.size, "without the rental registry the old behaviour is unchanged")
    }

    @Test fun rentedLotsAreNotInTheTrialBudgetButUseTheTvBudgetAndFreeItWhenRemoved() {
        // the trial manifest and its 100 Mo check depend on the content only: an access with a rental changes neither
        val trialLot = TrialLot("learn", "cm2-trial", "learn", "cm2", 2L shl 20, listOf(TrialFile("a", 2L shl 20)), listOf("lesson/1"), listOf("classe-cm2"))
        val bundles = BundleCatalog(listOf(Bundle("classe-cm2", "classe", setOf("learn:cm2"))))
        val manifest = TrialManifest(true, TrialBudget.CAP_BYTES, 2L shl 20, "inv", listOf(trialLot), listOf(TrialSub("learn/cm2/maths", "learn", 1, 10, 2L shl 20, 1L shl 30)), bundles)
        assertEquals(emptyList(), TrialBudget.verify(manifest)); val before = TrialBudget.fingerprint(manifest)
        val rig = RentalRig(); rig.install(rig.issue(rig.rental())); rig.wall = T0 + DAY
        val access = RentalPolicy.mergeAccess(Access.TRIAL_ONLY, rig.status())
        assertEquals(setOf("classe-cm2"), access.rented)
        assertEquals(emptyList(), TrialBudget.verify(manifest)); assertEquals(before, TrialBudget.fingerprint(manifest))
        // the plan: the rented FULL lot takes the place of its trial twin (never both), counted in the TV budget (10 Mo here: 1000 bytes)
        val trial = LotMeta(LotId("learn", "cm2-trial"), 1, 300, "b".repeat(64), "CM2 essai", 0, Edition.TRIAL)
        val full = LotMeta(CM2, 1, 600, "a".repeat(64), "CM2")
        val plan = EditionPolicy.planForTv(listOf(Need(CM2, 1)), listOf(trial, full), listOf(trial), 0, access, bundles, budget = 1000)
        assertEquals(listOf(CM2), plan.plan.wanted.map { it.id }, "the rented lot replaces the trial twin"); assertEquals(600, plan.plan.wanted.sumOf { it.bytes })
        val noRental = EditionPolicy.planForTv(listOf(Need(CM2, 1)), listOf(trial, full), emptyList(), 0, Access.TRIAL_ONLY, bundles, budget = 1000)
        assertEquals(listOf(trial.id), noRental.plan.wanted.map { it.id }, "without the rental the trial stays")
        // TV budget: the rented lot counts, and its removal frees the room
        val tv = FakeTv(starter = 0, max = 1000)
        tv.learn.held[CM2] = full
        assertEquals(600, tv.store.usedBytes()); assertEquals(400, tv.store.remainingBytes())
        rig.ledger.markRented("loc-cm2@$T0", CM2, full, LotFamilies.explicit(emptySet(), setOf("learn:cm2")))
        rig.wall = T0 + 31 * DAY
        rig.sweeper(TvRentedLots(tv.store)).sweep(SweepTrigger.APP_START)
        assertEquals(0, tv.store.usedBytes(), "the expired lot frees its room"); assertTrue(tv.learn.held.isEmpty())
    }
}

class RentalRobustnessTest {
    private fun setupOne(rig: RentalRig, lots: FakeLots, product: String, bundle: String, lot: LotId, days: Int = 10): String {
        val a = rig.issue(rig.rental(product = product, bundles = listOf(bundle), days = days)); rig.install(a)
        val key = "$product@$T0"
        lots.held[lot] = Edition.FULL
        rig.vault.putKey(key, rig.key(product))
        rig.vault.putLot(lot, 1, RentalKeys.seal(rig.key(product), lot, 1, Kit.bytes(lot.scope.length + 1, 500)))
        rig.ledger.markRented(key, lot, LotMeta(lot, 1, 500, "a".repeat(64), lot.scope), LotFamilies.explicit(emptySet(), setOf(LotNames.key(lot))))
        return key
    }
    private fun reopen(rig: RentalRig) = RentalLedger(File(rig.dir, "rental"), TvClock(), RentalConfig(), { rig.wall })

    @Test fun corruptLedgerWithoutBackupIsKeptAsEvidenceAndSuspendsEveryRental() {
        val rig = RentalRig(); val lots = FakeLots(); val key = setupOne(rig, lots, "loc-cm2", "classe-cm2", CM2)
        val file = File(rig.dir, "rental/rentals.json"); File(rig.dir, "rental/rentals.json.bak").delete()
        file.writeText("""{"clock":{"lastSe""")                          // truncated
        val ledger2 = reopen(rig)
        assertTrue(ledger2.degraded); assertNotNull(ledger2.loadNote)
        assertTrue(File(rig.dir, "rental").list()!!.any { it.startsWith("rentals.json.corrupt-") }, "the corrupt file is kept")
        // within the first day of a 10-day rental: the unknown state is NOT trusted, the rental is treated as ended
        assertEquals(RentalState.EXPIRED, ledger2.status(rig.installed.toList()).single().state)
        val rig2 = RentalSweeperRig(ledger2, rig, lots).sweep()
        assertFalse(rig.vault.hasKey(key), "key destroyed"); assertTrue(rig2.failures.isEmpty())
        assertFalse(ledger2.degraded, "quarantine over once every contract is tombstoned")
        assertFalse(reopen(rig).degraded, "and it stays over after a reboot")
        // the contract can never be reopened from its activation
        assertNotEquals("clé installée", rig.ledger.let { reopen(rig).install(rig.installed.first(), rig.installed.toList(), rig.fp, rig.vault) }.values.first())
    }
    private fun RentalSweeperRig(ledger: RentalLedger, rig: RentalRig, lots: FakeLots) = object { fun sweep() = RentalSweeper(ledger, rig.vault, lots, { rig.installed.toList() }, { emptySet() }, { rig.wall }).sweep(SweepTrigger.APP_START) }

    @Test fun corruptMainFileFallsBackToTheBackup() {
        val rig = RentalRig(); val lots = FakeLots(); val key = setupOne(rig, lots, "loc-cm2", "classe-cm2", CM2)
        rig.wall = T0 + 10 * DAY; rig.sweeper(lots).sweep(SweepTrigger.APP_START)            // saves several times: a .bak of the previous good state exists
        val dir = File(rig.dir, "rental"); assertTrue(File(dir, "rentals.json.bak").isFile)
        File(dir, "rentals.json").writeText("not json at all")
        val ledger2 = reopen(rig)
        assertFalse(ledger2.degraded); assertNotNull(ledger2.rec(key), "contract state restored from the backup")
        assertTrue(File(dir, "rentals.json").let { true } && dir.list()!!.any { it.startsWith("rentals.json.corrupt-") })
    }

    @Test fun missingLedgerWhileKeysAreInTheSafeIsDoubtful_aFreshTvIsNot() {
        val rig = RentalRig(); val lots = FakeLots(); setupOne(rig, lots, "loc-cm2", "classe-cm2", CM2)
        val dir = File(rig.dir, "rental"); File(dir, "rentals.json").delete(); File(dir, "rentals.json.bak").delete()
        assertTrue(reopen(rig).degraded)
        assertFalse(RentalLedger(File(Kit.tmp(), "rental")).degraded, "nothing at all = a fresh TV")
    }

    @Test fun oneFailingContractDoesNotStopTheNextOnes() {
        val rig = RentalRig(); val lots = FakeLots()
        val k1 = setupOne(rig, lots, "loc-cm2", "classe-cm2", CM2)
        val cm1 = LotId("learn", "cm1"); val k2 = setupOne(rig, lots, "loc-cm1", "classe-cm1", cm1)
        rig.wall = T0 + 10 * DAY
        val errors = ArrayList<String>()
        var failOnce = true
        val sw = RentalSweeper(rig.ledger, rig.vault, lots, { rig.installed.toList() }, { emptySet() }, { rig.wall }, { if (it == "key:$k1" && failOnce) { failOnce = false; throw IllegalStateException("boom") } }, { k, _ -> errors += k })
        val rep = sw.sweep(SweepTrigger.APP_START)
        assertEquals(listOf(k1), rep.failures); assertEquals(listOf(k1), errors)
        assertEquals(RentalPhase.DONE, rig.ledger.phase(k2), "the later contract was swept"); assertTrue(cm1 !in lots.held)
        assertEquals(RentalPhase.EXPIRING, rig.ledger.phase(k1), "the failed one waits at its last persisted step")
        val rep2 = sw.sweep(SweepTrigger.PERIODIC)                                          // retried at the next sweep
        assertTrue(rep2.failures.isEmpty()); assertEquals(RentalPhase.DONE, rig.ledger.phase(k1)); assertTrue(CM2 !in lots.held)
    }

    @Test fun aLotBoughtDuringItsRentalIsKeptAtExpiry_viaOwnedLots() {
        val rig = RentalRig(); val lots = FakeLots(); val key = setupOne(rig, lots, "loc-cm2", "classe-cm2", CM2)
        // a permanent « tout » purchase arrives during the rental
        rig.installed += rig.issue(Right.Purchase("p-all", listOf(Right.ALL_BUNDLE), T0 + DAY), at = T0 + DAY)
        rig.wall = T0 + 10 * DAY
        val owned = OwnedLots.of(rig.installed.toList(), rig.wall, lots.heldLots())
        assertEquals(setOf(CM2), owned)
        val rep = RentalSweeper(rig.ledger, rig.vault, lots, { rig.installed.toList() }, { owned }, { rig.wall }).sweep(SweepTrigger.APP_START)
        assertEquals(listOf(CM2), rep.keptBecauseOwned); assertTrue(CM2 in lots.held); assertFalse(rig.vault.hasKey(key))
    }

    @Test fun ownedLotsResolveBundlesWithACatalogueAndNothingWithoutRights() {
        val rig = RentalRig()
        val cat = BundleCatalog(listOf(Bundle("classe-cm2", "classe", setOf("learn:cm2", "quiz:cm2"))))
        val held = setOf(CM2, CM2Q, LotId("learn", "cm1"))
        val buy = rig.issue(Right.Purchase("p1", listOf("classe-cm2"), T0))
        assertEquals(setOf(CM2, CM2Q), OwnedLots.of(listOf(buy), T0 + DAY, held, cat))
        assertTrue(OwnedLots.of(listOf(buy), T0 + DAY, held, null).isEmpty(), "no catalogue on the TV: a named bundle cannot be resolved")
        assertTrue(OwnedLots.of(emptyList(), T0, held, cat).isEmpty())
        val rental = rig.issue(rig.rental())
        assertTrue(OwnedLots.of(listOf(rental), T0 + DAY, held, cat).isEmpty(), "a rental never counts as owned")
    }
}

/** w1-02: the rental safe writes with fsync + rename (AtomicFile): a reader never sees an empty or half key, nothing is lost. */
class RentalVaultDurabilityTest {
    private val dirs = ArrayList<File>()
    private fun vault() = RentalVault(File(Kit.tmp().also { dirs += it }, "rental"))
    @AfterTest fun tearDown() { dirs.forEach { it.deleteRecursively() } }

    @Test fun keyAndLotSurviveAReopenAndLeaveNoTemporaryFile() {
        val v = vault(); val key = ByteArray(32) { (it + 1).toByte() }
        assertTrue(v.putKey("loc-cm2@1", key)); assertContentEquals(key, RentalVault(v.dir).getKey("loc-cm2@1"))
        val lot = LotId("learn", "a"); val sealed = ByteArray(500) { it.toByte() }
        assertTrue(v.putLot(lot, 1, sealed)); assertContentEquals(sealed, v.lotFile(lot, 1)!!.readBytes())
        assertTrue(v.dir.walkTopDown().none { it.name.endsWith(".tmp") })
    }

    @Test fun putKeyOverAnExistingKeyIsNeverVisiblyEmptyOrPartial() {
        val v = vault(); val a = ByteArray(32) { 1 }; val b = ByteArray(32) { 2 }
        assertTrue(v.putKey("loc-cm2@1", a))
        val stop = java.util.concurrent.atomic.AtomicBoolean(false); val bad = java.util.concurrent.atomic.AtomicReference<String?>(null)
        val reader = Thread { while (!stop.get()) { val k = v.getKey("loc-cm2@1"); if (k == null || !(k.contentEquals(a) || k.contentEquals(b))) { bad.set("vu: ${k?.size}"); break } } }
        reader.start()
        repeat(300) { assertTrue(v.putKey("loc-cm2@1", if (it % 2 == 0) b else a)) }
        stop.set(true); reader.join(5000)
        assertNull(bad.get(), "the visible key was the old one or the new one, never empty or partial")
    }
}
