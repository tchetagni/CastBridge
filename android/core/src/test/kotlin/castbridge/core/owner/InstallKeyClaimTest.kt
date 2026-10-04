package castbridge.core.owner

import castbridge.core.lots.Right
import kotlin.test.*

private const val T = 1_800_000_000_000L

/**
 * Audit Opus w23-05, HIGH-1: a PRODUCTION activation issued for a TV that sent its signing key (`install_sig=ed25519|<64 hex>` in the device request) carries that key SIGNED, as the right
 * `ik|<64 hex>`; the server then registers and pays automatically ONLY for the TV that holds the key (its `bind` proof). The claim grants nothing and is additive: a TV (any version) keeps
 * verifying activations with and without it. TEST KEYS only, derived from fixed bytes.
 */
class InstallKeyClaimTest {
    private val fp = DeviceIdentity.fingerprints(RawFactors(flashSerial = "FLASHSERIAL1", flashCid = "cid-1", ethernetMac = "AA:BB:CC:00:11:22", systemSerial = "SYS12345",
        wifiMac = "10:20:30:40:50:60", wifiSysfsPath = "/sys/devices/platform/soc/ffe03000.sd/mmc_host/mmc1/net/wlan0", bluetoothAddress = "11:22:33:44:55:66"))
    private val signer = Ed25519Signer(ByteArray(32) { (it + 21).toByte() })
    private val ring = KeyRing(listOf(signer.trusted()))
    private val tvKey = Ed25519Signer(ByteArray(32) { (it + 77).toByte() }).publicKey          // the TV's signing key: a test key
    private val tvKeyHex = tvKey.joinToString("") { "%02x".format(it) }
    private val code = DeviceCode.of(fp)

    private fun flow() = ArrayList<LicenseEvent>().let { store -> LicensedIssuer(signer, KeyScope.ALL, { ring }, { store.toList() }, { store.clear(); store.addAll(it) }, { T }) }

    private fun requestText(withSig: Boolean) = OwnerFrames.deviceInfo(code, fp) + (if (withSig) "\ninstall_sig=ed25519|$tvKeyHex" else "")

    @Test fun aProductionActivationForARequestWithTheSigningKeyCarriesItSigned() {
        val a = flow().issue(DeviceRequest.parse(requestText(true)), IssueSpec(ActivationKind.PRODUCTION, license = "")).issued
        val act = assertIs<ActivationResult.Accepted>(ActivationVerifier(ring).verify(a.token, fp, T + 1000)).activation
        val ik = act.rights.filterIsInstance<Right.Unknown>().filter { it.raw.startsWith("ik|") }
        assertEquals(listOf("ik|$tvKeyHex"), ik.map { it.raw }, "la clé d'installation de la TV est signée dans l'activation : ${act.rights}")
        // the signature covers it: changing the claim breaks the token
        val tampered = a.token.replace(a.token.split('.')[1], java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(String(java.util.Base64.getUrlDecoder().decode(a.token.split('.')[1])).replace("ik|$tvKeyHex", "ik|" + "0".repeat(64)).toByteArray()))
        assertFalse(ActivationVerifier(ring).verify(tampered, fp, T + 1000) is ActivationResult.Accepted, "une clé d'installation modifiée casse la signature")
    }

    @Test fun aRequestWithoutTheSigningKeyGivesAnActivationWithoutTheClaimAndAnOldTvStillVerifiesBoth() {
        val f = flow()
        val without = f.issue(DeviceRequest.parse(requestText(false)), IssueSpec(ActivationKind.PRODUCTION, license = "")).issued
        assertTrue(assertIs<ActivationResult.Accepted>(ActivationVerifier(ring).verify(without.token, fp, T + 1000)).activation.rights.none { it is Right.Unknown && it.raw.startsWith("ik|") })
        val with = f.issue(DeviceRequest.parse(requestText(true)), IssueSpec(ActivationKind.PRODUCTION, license = "")).issued
        // « the receiver keeps working with tokens with and without ik »: the verification ignores the claim (it is a right of an unknown kind, kept verbatim, granting nothing)
        val a = assertIs<ActivationResult.Accepted>(ActivationVerifier(ring).verify(with.token, fp, T + 1000)).activation
        val access = TvGate.evaluate(listOf(a), emptyList(), T + 1000)
        assertTrue(access.keyInstalled); assertFalse(access.trial); assertEquals("Version complète", access.label)
    }

    @Test fun aTrialActivationNeverCarriesTheClaim() {
        val a = flow().issue(DeviceRequest.parse(requestText(true)), IssueSpec(ActivationKind.TRIAL, license = Activation.TRIAL_LICENSE, rentalMaster = ByteArray(32), usageDays = 30)).issued.activation
        assertTrue(a.rights.none { it is Right.Unknown && it.raw.startsWith("ik|") }, a.rights.toString())
    }

    @Test fun theDeviceRequestTextRoundTripsTheSigningKeyAndAMalformedLineIsUnreadable() {
        val text = requestText(true)
        val info = assertNotNull(OwnerFrames.parseDeviceInfo(text))
        assertTrue(info.unknown.isEmpty(), "ce n'est plus une ligne inconnue : ${info.unknown}")
        assertNull(OwnerFrames.parseDeviceInfo(OwnerFrames.deviceInfo(code, fp) + "\ninstall_sig=ed25519|zz"), "une clé de signature mal formée rend la demande illisible")
        assertNull(OwnerFrames.parseDeviceInfo(text + "\ninstall_sig=ed25519|$tvKeyHex"), "deux lignes install_sig : illisible")
    }

    @Test fun theTvProducesExactlyTheTextTheIssuersRead() {
        val text = OwnerFrames.deviceInfo(code, fp, null, tvKey)
        assertEquals(requestText(true), text)
        val info = assertNotNull(OwnerFrames.parseDeviceInfo(text))
        assertContentEquals(tvKey, info.installSig)
        assertNull(info.installPub)
        val both = OwnerFrames.deviceInfo(code, fp, ByteArray(32) { (it * 3 + 1).toByte() }, tvKey)
        assertContentEquals(tvKey, assertNotNull(OwnerFrames.parseDeviceInfo(both)).installSig)
        assertEquals(32, assertNotNull(OwnerFrames.parseDeviceInfo(both)).installPub?.size)
        assertFailsWith<IllegalArgumentException> { OwnerFrames.deviceInfo(code, fp, null, ByteArray(31)) }
        assertContentEquals(tvKey, DeviceRequest.parse(both.replace("\n", "\r\n\r\n")).installSig)
    }

    @Test fun theBareIssuerSignsTheClaimOnlyIntoAProductionActivationForATv() {
        val issuer = ActivationIssuer(signer)
        val req = ActivationIssuer.Request(ActivationKind.PRODUCTION, code, fp, T, license = "lic-1", installKey = tvKey)
        val act = issuer.issue(req).activation
        assertEquals(listOf(Right.Unknown("ik|$tvKeyHex")), act.rights)
        // a same issue without the key gives different bytes (the claim is signed), and the same inputs always give the same bytes
        assertEquals(issuer.issue(req.copy(nonce = "00112233445566778899aabbccddeeff")).token, issuer.issue(req.copy(nonce = "00112233445566778899aabbccddeeff")).token)
        assertNotEquals(issuer.issue(req.copy(nonce = "00112233445566778899aabbccddeeff")).token, issuer.issue(req.copy(nonce = "00112233445566778899aabbccddeeff", installKey = null)).token)
        assertFailsWith<IssueException> { issuer.issue(req.copy(kind = ActivationKind.TRIAL, license = Activation.TRIAL_LICENSE)) }
        assertFailsWith<IssueException> { issuer.issue(req.copy(subject = Subject.PHONE)) }
        assertFailsWith<IssueException> { issuer.issue(req.copy(installKey = ByteArray(31))) }
        assertFailsWith<IssueException> { issuer.issue(req.copy(rights = listOf(Right.Unknown("ik|" + "ab".repeat(32))))) }   // an unknown right is never emitted by hand
        // it sorts with the other rights (canonical body): usage < ik by the bytes of the line
        val withUsage = issuer.issue(req.copy(rights = listOf(Right.Usage(T, T + 30 * 86_400_000L)))).activation
        assertEquals(setOf("usage|duree|$T|${T + 30 * 86_400_000L}", "ik|$tvKeyHex"), withUsage.rights.map { Activation.rightLine(it) }.toSet())
        assertIs<ActivationResult.Accepted>(ActivationVerifier(ring).verify(issuer.issue(req.copy(rights = listOf(Right.Usage(T, T + 30 * 86_400_000L)))).token, fp, T + 1000))
    }

    @Test fun theCommandLineToolEmbedsTheClaimWheneverTheRequestFileHasIt() {
        val dir = java.nio.file.Files.createTempDirectory("ik-cli").toFile()
        try {
            val vault = java.io.File(dir, "coffre.txt")
            val request = java.io.File(dir, "demande.txt").also { it.writeText(requestText(true).replace("\n", "\r\n\r\n")) }
            val noSig = java.io.File(dir, "demande-ancienne.txt").also { it.writeText(requestText(false)) }
            class Rec : OwnerCli.Io {
                val out = ArrayList<String>(); val err = ArrayList<String>()
                override fun out(s: String) { out += s }
                override fun err(s: String) { err += s }
                override fun passphrase(prompt: String) = "un-code-assez-long-pour-tester".toCharArray()
            }
            assertEquals(0, OwnerCli.run(listOf("init", "--vault", vault.path), Rec()) { T })
            val io = Rec()
            assertEquals(0, OwnerCli.run(listOf("activation", "--vault", vault.path, "--request", request.path, "--kind", "production", "--license", "lic-cli1", "--journal", java.io.File(dir, "j.log").path), io) { T }, io.err.toString())
            val act = assertNotNull(Activation.decode(io.out.first { it.startsWith("cbx1.") }))
            assertEquals(listOf("ik|$tvKeyHex"), act.rights.map { Activation.rightLine(it) })
            val old = Rec()
            assertEquals(0, OwnerCli.run(listOf("activation", "--vault", vault.path, "--request", noSig.path, "--kind", "production", "--license", "lic-cli2", "--journal", java.io.File(dir, "j.log").path), old) { T }, old.err.toString())
            assertTrue(assertNotNull(Activation.decode(old.out.first { it.startsWith("cbx1.") })).rights.isEmpty(), "une TV qui n'envoie pas sa clé : aucune clé liée")
        } finally { dir.deleteRecursively() }
    }
}
