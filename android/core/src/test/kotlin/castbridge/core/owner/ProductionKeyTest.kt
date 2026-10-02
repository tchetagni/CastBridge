package castbridge.core.owner

import castbridge.core.lots.Right
import java.time.ZoneId
import kotlin.test.*

private const val DAY = 24L * 3600 * 1000
private const val T = 1_800_000_000_000L

/** Production key policy (owner decision 2026-10-02): licence generated automatically, only a duration (and SUPER) matters, zero content right is valid and means « version complète ». */
class ProductionKeyTest {
    private val fp = DeviceIdentity.fingerprints(RawFactors(flashSerial = "FLASHSERIAL1", flashCid = "cid-1", ethernetMac = "AA:BB:CC:00:11:22", systemSerial = "SYS12345",
        wifiMac = "10:20:30:40:50:60", wifiSysfsPath = "/sys/devices/platform/soc/ffe03000.sd/mmc_host/mmc1/net/wlan0", bluetoothAddress = "11:22:33:44:55:66"))
    private val signer = Ed25519Signer(ByteArray(32) { (it + 21).toByte() })
    private val ring = KeyRing(listOf(signer.trusted()))
    private val device = DeviceRequest(DeviceCode.of(fp), DeviceIdentity.kFor(fp.n), fp)

    private fun flow(store: MutableList<LicenseEvent> = ArrayList()) = LicensedIssuer(signer, KeyScope.ALL, { ring }, { store.toList() }, { store.clear(); store.addAll(it) }, { T }) to store

    @Test fun licenseIdsMatchTheFormatAndDoNotCollide() {
        val seen = HashSet<String>()
        repeat(10_000) {
            val id = LicenseIds.generate()
            assertTrue(Regex("^lic-[0-9a-f]{10}$").matches(id), id); assertTrue(Activation.ID.matches(id), id); assertNotEquals(Activation.TRIAL_LICENSE, id)
            assertTrue(seen.add(id), "duplicate $id")
        }
        assertTrue(LicenseIds.wantsAuto(null) && LicenseIds.wantsAuto("") && LicenseIds.wantsAuto("  ") && LicenseIds.wantsAuto("auto") && LicenseIds.wantsAuto("AUTO"))
        assertFalse(LicenseIds.wantsAuto("lic-1"))
    }

    @Test fun aBlankLicenceMakesTheDeskCreateANewOneSeatAndIssue() {
        val (f, store) = flow()
        val r = f.issue(device, IssueSpec(ActivationKind.PRODUCTION, license = ""))
        val lic = r.issued.activation.license
        assertTrue(Regex("^lic-[0-9a-f]{10}$").matches(lic), lic)
        val state = LicenseBook.replay(store, ring)
        assertEquals(1, state.licenses.getValue(lic).seats); assertEquals(LicenseBook.DEFAULT_TRANSFERS_PER_YEAR, state.licenses.getValue(lic).maxTransfersPerYear)
        assertEquals(1, state.usedSeats(lic)); assertTrue(r.issued.activation.rights.isEmpty())
        // a second blank request is a NEW licence, never an existing one
        val other = f.issue(device, IssueSpec(ActivationKind.PRODUCTION, license = "auto")).issued.activation.license
        assertNotEquals(lic, other); assertEquals(2, LicenseBook.replay(store, ring).licenses.size)
        // a given id keeps today's behaviour: unknown = refused, known = re-activation of the same hardware
        assertFailsWith<IssueException> { f.issue(device, IssueSpec(ActivationKind.PRODUCTION, license = "lic-zzz")) }
        val again = f.issue(device, IssueSpec(ActivationKind.PRODUCTION, license = lic, issuedAt = T + 1000))
        assertTrue(again.reused); assertEquals(lic, again.issued.activation.license)
    }

    @Test fun productionWithoutAnyRightVerifiesAndLiftsTheTrial() {
        val (f, _) = flow()
        val a = f.issue(device, IssueSpec(ActivationKind.PRODUCTION, license = "")).issued
        val v = ActivationVerifier(ring).verify(a.token, fp, T + 1000)
        val act = assertIs<ActivationResult.Accepted>(v).activation
        assertTrue(act.rights.isEmpty())
        val access = TvGate.evaluate(listOf(act), emptyList(), T + 1000)
        assertTrue(access.keyInstalled); assertFalse(access.trial); assertEquals("Version complète", access.label)
        // on top of a trial the production key lifts every restriction
        val trial = f.issue(device, IssueSpec(ActivationKind.TRIAL, license = Activation.TRIAL_LICENSE, rentalMaster = ByteArray(32), usageDays = 30)).issued.activation
        assertTrue(TvGate.evaluate(listOf(trial), emptyList(), T + 1000).trial)
        assertFalse(TvGate.evaluate(listOf(trial, act), emptyList(), T + 1000).trial)
        assertEquals("PRODUCTION", KeyBadge.of(listOf(act), T + 1000, zone = ZoneId.of("UTC")).title)
        assertEquals("Clé illimitée", KeyBadge.of(listOf(act), T + 900 * DAY, zone = ZoneId.of("UTC")).lines[0])
    }

    @Test fun usageOnlyProductionIsValidAndEndsAtItsCeiling() {
        val (f, _) = flow()
        val a = f.issue(device, IssueSpec(ActivationKind.PRODUCTION, license = "", usageDays = 90)).issued
        val act = assertIs<ActivationResult.Accepted>(ActivationVerifier(ring).verify(a.token, fp, T + 1000)).activation
        assertEquals(listOf(Right.Usage(T, T + 90 * DAY)), act.rights)
        val ok = TvGate.evaluate(listOf(act), emptyList(), T + 89 * DAY)
        assertTrue(ok.keyInstalled); assertFalse(ok.trial)
        assertFalse(TvGate.evaluate(listOf(act), emptyList(), T + 91 * DAY).keyInstalled)
    }

    @Test fun theBareIssuerAcceptsProductionWithNoRightButStillNeedsAValidLicence() {
        val issuer = ActivationIssuer(signer)
        val req = ActivationIssuer.Request(ActivationKind.PRODUCTION, device.code, fp, T, license = "lic-1")
        assertTrue(issuer.issue(req).activation.rights.isEmpty())
        assertFailsWith<IssueException> { issuer.issue(req.copy(license = "trial")) }
        assertFailsWith<IssueException> { issuer.issue(req.copy(license = "")) }
    }
}
