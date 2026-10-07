package castbridge.core.owner

import castbridge.core.lots.BoxResult
import castbridge.core.lots.InstallKey
import castbridge.core.lots.RentalKeys
import castbridge.core.lots.RentalLines
import castbridge.core.lots.Right
import castbridge.core.trust.TvDeviceRequest
import castbridge.core.tv.PinGuard
import castbridge.core.tv.activation.LockedActivationApi
import kotlin.test.*

/**
 * « Un essai en enveloppe v2 s'installe par le code » (docs/test-plans/PARCOURS-CRITIQUES.md P-80 g ; DESIGN-ACTIVATION-SIMPLE ACT-F4, amendée le 2026-10-07 : `install=` est la clé PUBLIQUE X25519
 * de l'installation, rien de secret, et une clé d'essai en enveloppe v2 l'exige). De bout en bout en JVM, sans appareil : la TV verrouillée rend sa demande à qui détient le code
 * ([LockedActivationApi]), le téléphone la lit ([LockedRequestRoute]) et la donne à la console, qui émet l'essai (le même flux que le bureau et le serveur, [LicensedIssuer]), le téléphone l'installe
 * ([KeyAcquisition]), la TV le vérifie et ouvre l'enveloppe des lots avec sa clé privée d'installation. Avant l'amendement la route verrouillée retirait la ligne : l'essai v2 était impossible
 * par le code (la console renvoyait vers la lecture Bluetooth ou l'enveloppe v1).
 */
class TrialKeyByCodeTest {
    private val now = 1_791_400_000_000L
    private val pin = "482915"
    private val fp = DeviceIdentity.fingerprints(RawFactors(flashSerial = "FLASHSERIAL1", flashCid = "cid-1", ethernetMac = "AA:BB:CC:00:11:22", systemSerial = "SYS12345",
        wifiMac = "10:20:30:40:50:60", wifiSysfsPath = "/sys/devices/platform/soc/ffe03000.sd/mmc_host/mmc1/net/wlan0", bluetoothAddress = "11:22:33:44:55:66"))
    private val code = DeviceCode.of(fp)
    private val tvInstall = InstallKey.fromSeed(ByteArray(32) { (it + 5).toByte() })                      // the TV's installation key pair: the private half never leaves the TV
    private val otherInstall = InstallKey.fromSeed(ByteArray(32) { (it + 99).toByte() })
    private val tvSigning = Ed25519Signer(ByteArray(32) { (it + 77).toByte() }).publicKey
    private val owner = Ed25519Signer(ByteArray(32) { (it + 21).toByte() })
    private val ring = KeyRing(listOf(owner.trusted()))

    /** What `ActivationCenter.requestText()` gives on the TV: the complete request. */
    private fun tvRequest(withInstallKey: Boolean = true) = OwnerFrames.deviceInfo(code, fp, if (withInstallKey) tvInstall.pub else null, tvSigning)

    /** The TV's locked route answers the phone that holds the code; the phone reads the answer exactly as `ActivationDriver` does. */
    private fun readByCode(tvText: String): TvDeviceRequest {
        val api = LockedActivationApi(PinGuard(pin, now = { now }), { LockedActivationApi.Install.Rejected("non") }, { true }, "1.0-test", now = { now }, deviceRequest = { tvText })
        val reply = api.handle(LockedActivationApi.Request("GET", LockedActivationApi.DEVICE_REQUEST_PATH, "192.168.49.2", "192.168.49.1:8765", pin, null, null)) { error("a read has no body") }
        assertEquals(200, reply.status, reply.json)
        return assertIs<LockedRequestRoute.Reply.Request>(LockedRequestRoute.interpret(reply.status, reply.json)).request
    }

    /** The console of the phone issues a TRIAL for the request it was opened with (`ActivateTvActivity.req` = the request's complete text). */
    private fun issueTrial(prefill: String, forceV1: Boolean = false): ActivationIssuer.Issued {
        val store = ArrayList<LicenseEvent>()
        val flow = LicensedIssuer(owner, KeyScope.ALL, { ring }, { store.toList() }, { store.clear(); store.addAll(it) }, { now })
        val spec = IssueSpec(ActivationKind.TRIAL, license = Activation.TRIAL_LICENSE, rentalMaster = RentalKeys.masterFrom(owner), usageDays = 30, trialLots = true, boxV1 = forceV1)
        return flow.issue(DeviceRequest.parse(prefill), spec).issued
    }

    @Test fun aTrialInAV2EnvelopeIsIssuedFromTheRequestReadByTheCodeAndInstalledOnTheTv() {
        val request = readByCode(tvRequest())
        assertNotNull(request.installHex, "the request read by the code carries the TV's public installation key")
        val issued = issueTrial(request.fullText())                                                      // no Bluetooth read, no « enveloppe v1 » switch

        // the phone: the key is recognised as a TRIAL made for this TV, and the console's key is installed at once (the owner already touched « Installer sur la TV »)
        val joined = KeyAcquisition.reduce(KeyAcquisition.Model(), KeyAcquisition.Event.TvFound(KeyAcquisition.Tv("CastBridge-TV", ActivationRoutePlan.Route.GROUP), request, now)).model
        val step = KeyAcquisition.reduce(joined, KeyAcquisition.Event.ConsoleKey(issued.token, now))
        assertEquals(listOf<KeyAcquisition.Effect>(KeyAcquisition.Effect.Install(issued.token, KeyAcquisition.Source.CONSOLE)), step.effects)
        val check = assertIs<KeyAcquisition.Check.Valid>(step.model.pick!!.check)
        assertEquals(ActivationKind.TRIAL, check.kind); assertEquals(30L, check.usageDays); assertFalse(check.maybeExpired)
        assertEquals("Clé d'essai de 30 jours, faite pour cette TV.", KeyAcquisition.describe(check, request))

        // the TV: verified by the same verifier as a pasted key; the rented lots' box is v2 and only THIS installation opens it
        val a = assertIs<ActivationResult.Accepted>(ActivationVerifier(ring).verify(issued.token, fp, now + 1000)).activation
        val rental = a.rights.filterIsInstance<Right.Rental>().single { it.productId == RentalLines.TRIAL_PRODUCT }
        assertTrue(rental.box.startsWith("v2:"), "enveloppe v2 : ${rental.box.take(8)}")
        assertIs<BoxResult.Key>(RentalKeys.openBox(rental.box, fp, rental.productId, rental.period, tvInstall, a.issuedAt))
        assertEquals(BoxResult.OtherInstall, RentalKeys.openBox(rental.box, fp, rental.productId, rental.period, otherInstall, a.issuedAt), "another installation opens nothing")
        assertEquals(BoxResult.NeedsInstallKey, RentalKeys.openBox(rental.box, fp, rental.productId, rental.period, null, a.issuedAt), "the public fingerprints alone open nothing")
    }

    @Test fun theSameTrialStillWorksAfterTheV1SunsetBecauseItIsNotV1() {
        val after = RentalKeys.V1_BOX_SUNSET_MS + 86_400_000L
        val request = readByCode(tvRequest())
        val store = ArrayList<LicenseEvent>()
        val issued = LicensedIssuer(owner, KeyScope.ALL, { ring }, { store.toList() }, { store.clear(); store.addAll(it) }, { after })
            .issue(DeviceRequest.parse(request.fullText()), IssueSpec(ActivationKind.TRIAL, license = Activation.TRIAL_LICENSE, rentalMaster = RentalKeys.masterFrom(owner), usageDays = 30, trialLots = true)).issued
        val a = assertIs<ActivationResult.Accepted>(ActivationVerifier(ring).verify(issued.token, fp, after + 1000)).activation
        assertTrue(a.rights.filterIsInstance<Right.Rental>().single().box.startsWith("v2:"))
    }

    @Test fun aTvThatHasNoInstallationKeyYetGivesNoV2EnvelopeAndNothingIsInvented() {
        val request = readByCode(tvRequest(withInstallKey = false))
        assertNull(request.installHex)
        assertTrue(request.fullText().lines().none { it.startsWith("install=") })
        val e = assertFailsWith<IssueException> { issueTrial(request.fullText()) }
        assertTrue("clé d'installation" in e.message.orEmpty(), e.message)
    }

    @Test fun theRequestTheAgentReceivesFromTheSharedTextGivesTheSameTrial() {
        // « Partager la demande » : the agent's tool reads the text the phone shared (the complete request) and issues the same v2 trial
        val shared = LockedRequestRoute.shareText(readByCode(tvRequest()))
        assertTrue(shared.lines().any { it.startsWith("install=x25519|") })
        val a = issueTrial(shared).activation
        assertTrue(a.rights.filterIsInstance<Right.Rental>().single().box.startsWith("v2:"))
    }
}
