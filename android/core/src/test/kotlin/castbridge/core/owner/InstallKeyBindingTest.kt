package castbridge.core.owner

import castbridge.core.net.JsonLite
import java.io.File
import java.nio.file.Files
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val T = 1_800_000_000_000L

/**
 * Second audit Opus w23-05, MEDIUM-C and LOW-E. The device request is unsigned, so a man in the middle can swap `install_sig`: (i) the TV refuses an activation whose `ik` is not ITS OWN installation
 * key, with a clear French reason ([ActivationBinding]); (ii) every issuer shows the FINGERPRINT of the key it signs, to compare with the TV screen; the fingerprint is the same text everywhere
 * (shared vectors with Java and Python). TEST KEYS only.
 */
class InstallKeyBindingTest {
    private val fp = DeviceIdentity.fingerprints(RawFactors(flashSerial = "FLASHSERIAL1", flashCid = "cid-1", ethernetMac = "AA:BB:CC:00:11:22", systemSerial = "SYS12345",
        wifiMac = "10:20:30:40:50:60", wifiSysfsPath = "/sys/devices/platform/soc/ffe03000.sd/mmc_host/mmc1/net/wlan0", bluetoothAddress = "11:22:33:44:55:66"))
    private val signer = Ed25519Signer(ByteArray(32) { (it + 21).toByte() })
    private val ring = KeyRing(listOf(signer.trusted()))
    private val tvKey = Ed25519Signer(ByteArray(32) { (it + 77).toByte() }).publicKey
    private val middleman = Ed25519Signer(ByteArray(32) { (it + 140).toByte() }).publicKey
    private val code = DeviceCode.of(fp)
    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
    private fun fpOf(raw: ByteArray) = InstallSigner.fingerprintOf(Base64.getEncoder().encodeToString(raw))

    private fun flow() = ArrayList<LicenseEvent>().let { store -> LicensedIssuer(signer, KeyScope.ALL, { ring }, { store.toList() }, { store.clear(); store.addAll(it) }, { T }) }
    private fun issued(key: ByteArray?) = flow().issue(DeviceRequest.parse(OwnerFrames.deviceInfo(code, fp, null, key)), IssueSpec(ActivationKind.PRODUCTION, license = "")).issued

    private fun receiver(own: ByteArray?) = ActivationReceiver(ring, emptyList(), fp, Subject.TV, ownInstallKey = own)

    // ------------------------------------------------------------------ (i) la TV vérifie que ik est SA clé

    @Test fun aTvAcceptsAnActivationWhoseIkIsItsOwnKeyOnEveryChannel() {
        val a = issued(tvKey)
        assertIs<ActivationResult.Accepted>(receiver(tvKey).receive(Channel.MANUAL, a.token.toByteArray(), T + 1000))
        assertIs<ActivationResult.Accepted>(receiver(tvKey).receive(Channel.FILE, a.fileContent.toByteArray(), T + 1000))
        assertIs<ActivationResult.Accepted>(receiver(tvKey).receive(Channel.BLUETOOTH, a.bluetoothFrame, T + 1000))
    }

    @Test fun aTvRefusesAnActivationPreparedForAnotherInstallationKeyWithAClearReason() {
        val a = issued(middleman)         // la demande d'appareil a été altérée en route : l'activation porte la clé de l'intermédiaire
        for ((channel, payload) in listOf(Channel.MANUAL to a.token.toByteArray(), Channel.FILE to a.fileContent.toByteArray(), Channel.BLUETOOTH to a.bluetoothFrame)) {
            val r = assertIs<ActivationResult.Rejected>(receiver(tvKey).receive(channel, payload, T + 1000), "canal $channel")
            assertEquals(Rejection.WRONG_DEVICE, r.reason)
            assertFalse(r.suspect, "ce n'est pas un doute sur le matériel : jamais de déblocage manuel proposé")
            assertTrue(r.message.startsWith("Cette activation a été préparée pour une autre clé d'installation"), r.message)
            assertTrue(fpOf(middleman) in r.message, "l'empreinte attendue est dite : ${r.message}")
            assertTrue(fpOf(tvKey) in r.message, "l'empreinte de cette TV aussi : ${r.message}")
        }
    }

    @Test fun anActivationWithoutIkAndATvThatCannotReadItsKeyKeepWorkingAsBefore() {
        assertIs<ActivationResult.Accepted>(receiver(tvKey).receive(Channel.MANUAL, issued(null).token.toByteArray(), T + 1000), "activation d'avant le correctif : acceptée")
        assertIs<ActivationResult.Accepted>(receiver(null).receive(Channel.MANUAL, issued(middleman).token.toByteArray(), T + 1000), "sans clé locale lisible, rien n'est inventé : comme avant")
    }

    @Test fun theBindingCheckIsAPureFunctionOfTheRightsAndTheOwnKey() {
        val act = assertIs<ActivationResult.Accepted>(ActivationVerifier(ring).verify(issued(tvKey).token, fp, T + 1000)).activation
        assertNull(ActivationBinding.check(act, tvKey))
        assertNull(ActivationBinding.check(act, null))
        assertContentEquals(tvKey, ActivationBinding.installKeyOf(act))
        assertNotNull(ActivationBinding.check(act, middleman))
        assertNull(ActivationBinding.installKeyOf(assertIs<ActivationResult.Accepted>(ActivationVerifier(ring).verify(issued(null).token, fp, T + 1000)).activation))
    }

    // ------------------------------------------------------------------ (ii) l'empreinte est montrée par chaque émetteur

    @Test fun theDeviceRequestAndTheDeliveredActivationShowTheFingerprintOfTheKeyTheyBind() {
        val request = DeviceRequest.parse(OwnerFrames.deviceInfo(code, fp, null, tvKey))
        assertEquals(fpOf(tvKey), request.installFingerprint)
        assertNull(DeviceRequest.parse(OwnerFrames.deviceInfo(code, fp)).installFingerprint)
        val delivered = flow().issue(request, IssueSpec(ActivationKind.PRODUCTION, license = ""))
        assertEquals(fpOf(tvKey), delivered.installKeyFingerprint)
        assertNull(flow().issue(DeviceRequest.parse(OwnerFrames.deviceInfo(code, fp)), IssueSpec(ActivationKind.PRODUCTION, license = "")).installKeyFingerprint)
    }

    @Test fun thePhoneConsoleAnswersWithTheFingerprintToCompareBeforeHandingOverTheActivation() {
        val kdf = Pbkdf2Kdf(10)
        val seed = ByteArray(32) { (it + 21).toByte() }
        val vault = OwnerVault.seal(seed, "un-code-assez-long".toCharArray(), kdf)
        var g = 1_800_000_000_000L
        val console = PhoneConsole(vault, kdf, UnlockGuard({ g }), KeyScope.ALL - KeyScope.REGISTRY, Ed25519Signer(seed).trusted(KeyScope.ALL - KeyScope.REGISTRY), clock = { T })
        val session = assertIs<PhoneUnlock.Unlocked>(console.unlock("un-code-assez-long".toCharArray())).session
        val out = session.issue(OwnerFrames.deviceInfo(code, fp, null, tvKey), IssueSpec(ActivationKind.PRODUCTION, license = ""))
        assertEquals(fpOf(tvKey), out.installKeyFingerprint)
    }

    private class Rec : OwnerCli.Io {
        val out = ArrayList<String>(); val err = ArrayList<String>()
        override fun out(s: String) { out += s }
        override fun err(s: String) { err += s }
        override fun passphrase(prompt: String) = "un-code-assez-long-pour-tester".toCharArray()
    }

    @Test fun theCommandLineShowsTheFingerprintInInspectAndWhenItIssues() {
        val d = Files.createTempDirectory("owner").toFile()
        val vault = File(d, "coffre.txt")
        assertEquals(0, OwnerCli.run(listOf("init", "--vault", vault.path), Rec()) { T })
        val request = File(d, "demande.txt").also { it.writeText(OwnerFrames.deviceInfo(code, fp, null, tvKey)) }
        val inspect = Rec()
        assertEquals(0, OwnerCli.run(listOf("inspect", "--request", request.path), inspect) { T })
        assertTrue(inspect.out.joinToString("\n").contains(fpOf(tvKey)), "inspect affiche l'empreinte à comparer avec l'écran de la TV : ${inspect.out}")
        val issue = Rec()
        assertEquals(0, OwnerCli.run(listOf("activation", "--vault", vault.path, "--request", request.path, "--kind", "production", "--license", "lic-1", "--journal", File(d, "j.log").path), issue) { T })
        assertTrue(issue.err.joinToString("\n").contains(fpOf(tvKey)), "l'émission rappelle l'empreinte de la clé liée : ${issue.err}")
        val other = File(d, "sans.txt").also { it.writeText(OwnerFrames.deviceInfo(code, fp)) }
        val none = Rec()
        assertEquals(0, OwnerCli.run(listOf("inspect", "--request", other.path), none) { T })
        assertFalse(none.out.joinToString("\n").lowercase().contains("empreinte de la clé de signature"), "sans clé, aucune empreinte")
    }

    @Test fun theOptionalFingerprintLineRoundTripsAndAnAlteredRequestIsUnreadable() {
        val plain = OwnerFrames.deviceInfo(code, fp, null, tvKey)
        assertFalse("install_fp=" in plain, "par défaut le texte de la TV ne change pas (un serveur qui ne connaît pas la ligne la refuserait)")
        val withFp = OwnerFrames.deviceInfo(code, fp, null, tvKey, withFingerprint = true)
        assertTrue("install_fp=${fpOf(tvKey)}" in withFp)
        val info = assertNotNull(OwnerFrames.parseDeviceInfo(withFp))
        assertContentEquals(tvKey, info.installSig)
        assertTrue(info.unknown.isEmpty())
        assertNull(OwnerFrames.parseDeviceInfo(plain + "\ninstall_fp=${fpOf(middleman)}"), "une empreinte qui n'est pas celle de install_sig : demande altérée")
        assertNull(OwnerFrames.parseDeviceInfo(withFp + "\ninstall_fp=${fpOf(tvKey)}"), "deux lignes install_fp")
        assertNull(OwnerFrames.parseDeviceInfo(plain + "\ninstall_fp=zz"))
        assertEquals(fpOf(tvKey), DeviceRequest.parse(withFp).installFingerprint)
    }

    // ------------------------------------------------------------------ parité des empreintes (vecteurs partagés avec Java et Python)

    @Test fun theFingerprintMatchesTheSharedVectors() {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, "tools/activation/install-key-fingerprint-vectors.json").isFile) dir = dir.parentFile
        val v = JsonLite.obj(File(dir ?: File("."), "tools/activation/install-key-fingerprint-vectors.json").readText())
        @Suppress("UNCHECKED_CAST") val cases = v["cases"] as List<Map<String, Any?>>
        assertTrue(cases.size >= 4)
        for (c in cases) {
            val raw = (c["publicKeyHex"] as String).chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            assertEquals(c["fingerprint"], fpOf(raw), c["id"] as String)
            assertEquals(c["fingerprint"], ActivationBinding.fingerprint(raw), c["id"] as String)
        }
        @Suppress("UNCHECKED_CAST") for (n in v["normalize"] as List<Map<String, Any?>>) assertEquals(n["canonical"], ActivationBinding.normalizeFingerprint(n["typed"] as String), n["typed"] as String)
    }
}
