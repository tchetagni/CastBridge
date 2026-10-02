package castbridge.core.lots

import castbridge.core.owner.*
import java.io.File
import kotlin.test.*

/** The production install path of CastBridge-TV: the installation key is always handed to the ledger, so the v1 sunset is enforced (audit of w4-01). */
class RentalKeyedInstallTest {
    private val t0 = 1_800_000_000_000L          // after RentalKeys.V1_BOX_SUNSET_MS
    private val signer = Ed25519Signer(ByteArray(32) { (it + 7).toByte() })
    private val master = ByteArray(32) { (it * 3 + 1).toByte() }
    private val license = "lic-secret-1"
    private val fp = DeviceIdentity.fingerprints(RawFactors(flashSerial = "FLASHSERIAL1", flashCid = "cid-1", ethernetMac = "AA:BB:CC:00:11:22", systemSerial = "SYS12345"))
    private val ik = InstallKey.fromSeed(ByteArray(32) { (it + 1).toByte() })
    private val otherIk = InstallKey.fromSeed(ByteArray(32) { (it + 100).toByte() })
    private val ephemeral = ByteArray(32) { (it * 5 + 3).toByte() }

    private fun dir() = File.createTempFile("keyed", "").let { it.delete(); it.mkdirs(); it }
    private fun key() = RentalKeys.rentalKey(master, license, SeatIds.of(license, fp), "loc-cm2", t0)

    private fun activation(box: String): Activation {
        val right = Right.Rental("loc-cm2", listOf("classe-cm2"), t0, t0, 30, 0, 0, 0, box)
        val req = ActivationIssuer.Request(ActivationKind.PRODUCTION, DeviceCode.of(fp), fp, t0, rights = listOf(right), license = license, seat = SeatIds.of(license, fp), windowHours = 48)
        val token = ActivationIssuer(signer).issue(req).token
        assertIs<ActivationResult.Accepted>(ActivationVerifier(KeyRing(listOf(signer.trusted()))).verify(token, fp, t0))
        return Activation.decode(token)!!
    }

    private fun ledger(d: File) = RentalLedger(File(d, "rental"), TvClock(), RentalConfig(), { t0 })
    private val contract get() = RentalEngine.contractKey("loc-cm2", t0)

    @Test fun theKeyedPathRefusesAV1BoxPastTheSunsetWhileThePlainPathStillReadsIt() {
        val v1 = RentalKeys.makeBox(fp, DeviceIdentity.kFor(fp.n), key(), "loc-cm2", t0)
        val a = activation(v1)
        val d = dir(); val vault = RentalVault(File(d, "rental"))
        val refused = ledger(d).installKeyed(a, listOf(a), fp, vault, ik)
        assertEquals("enveloppe v1 périmée : refaire la clé avec un outil à jour", refused.getValue(contract))
        assertFalse(vault.hasKey(contract))
        assertEquals(listOf("Location loc-cm2 : enveloppe v1 périmée : refaire la clé avec un outil à jour"), RentalNotes.of(refused))
        // control: the same call WITHOUT the key (what the hub did before) installs the expired v1 box: the sunset was not enforced
        val d2 = dir(); val vault2 = RentalVault(File(d2, "rental"))
        assertEquals("clé installée", ledger(d2).install(a, listOf(a), fp, vault2).getValue(contract))
    }

    @Test fun aV2BoxForThisInstallationOpensAndAnotherInstallationIsToldSo() {
        val box = RentalKeys.makeBoxV2(ik.pub, key(), "loc-cm2", t0, ephemeral)
        val a = activation(box)
        val d = dir(); val vault = RentalVault(File(d, "rental"))
        val ok = ledger(d).installKeyed(a, listOf(a), fp, vault, ik)
        assertEquals("clé installée", ok.getValue(contract)); assertTrue(vault.hasKey(contract)); assertEquals(emptyList(), RentalNotes.of(ok))
        val d2 = dir(); val vault2 = RentalVault(File(d2, "rental"))
        val other = ledger(d2).installKeyed(a, listOf(a), fp, vault2, otherIk)
        assertTrue(other.getValue(contract).startsWith("clé enveloppée pour une autre installation"), other.toString())
        assertFalse(vault2.hasKey(contract)); assertEquals(1, RentalNotes.of(other).size)
    }

    @Test fun routineResultsProduceNoNote() {
        assertEquals(emptyList(), RentalNotes.of(mapOf("a@1" to "clé installée", "b@2" to "clé déjà en place", "c@3" to "terminée")))
    }
}
