package castbridge.core.owner

import castbridge.core.lots.Right
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** The desk tool end to end: throw-away vault, tokens issued, then verified exactly as a TV does. */
class OwnerCliTest {
    private class Rec(var pass: String? = "un-code-assez-long-pour-tester") : OwnerCli.Io {
        val out = ArrayList<String>(); val err = ArrayList<String>()
        override fun out(s: String) { out += s }
        override fun err(s: String) { err += s }
        override fun passphrase(prompt: String) = pass?.toCharArray()
    }

    private val now = 1_790_000_000_000L     // 2026-09 or later, after the format epoch
    private fun dir() = Files.createTempDirectory("owner").toFile()
    private fun device(): Fingerprints = Fingerprints(mapOf(FactorKind.FLASH to "a".repeat(32), FactorKind.ETHERNET to "b".repeat(32), FactorKind.SYSTEM_SERIAL to "c".repeat(32)))
    private fun request(d: File, fp: Fingerprints): File = File(d, "demande.txt").also { it.writeText(OwnerFrames.deviceInfo(DeviceCode.of(fp), fp)) }
    private fun trustedFrom(vault: File): KeyRing {
        val kv = vault.readLines().drop(1).associate { it.substringBefore('=') to it.substringAfter('=') }
        return KeyRing(listOf(TrustedKey(kv["kid"]!!, kv["pub"]!!, KeyScope.ALL)))
    }

    @Test fun initCreatesASealedVaultAndRefusesToOverwrite() {
        val d = dir(); val v = File(d, "coffre.txt"); val io = Rec()
        assertEquals(0, OwnerCli.run(listOf("init", "--vault", v.path), io) { now })
        val text = v.readText(); assertTrue("kid=" in text && "pub=" in text && "ct=" in text)
        assertTrue(io.out.any { it.startsWith("kid=") && "scopes=" in it })
        val again = Rec(); assertEquals(2, OwnerCli.run(listOf("init", "--vault", v.path), again) { now }); assertTrue(again.err.single().contains("existe déjà"))
    }

    @Test fun shortPassphraseAndWrongPassphraseAreRefused() {
        val d = dir(); val v = File(d, "c.txt")
        assertEquals(2, OwnerCli.run(listOf("init", "--vault", v.path), Rec("court")) { now }); assertTrue(!v.exists())
        OwnerCli.run(listOf("init", "--vault", v.path), Rec()) { now }
        val bad = Rec("mauvais-code-mauvais-code")
        assertEquals(2, OwnerCli.run(listOf("pubkey", "--vault", v.path), bad) { now }); assertTrue(bad.err.single().contains("Code incorrect"))
        assertEquals(0, OwnerCli.run(listOf("pubkey", "--vault", v.path), Rec()) { now })
    }

    @Test fun compactKeyIsBoundToTheDeviceCodeAndVerifies() {
        val d = dir(); val v = File(d, "c.txt"); OwnerCli.run(listOf("init", "--vault", v.path), Rec()) { now }
        val code = DeviceCode.of(device()); val io = Rec()
        assertEquals(0, OwnerCli.run(listOf("compact", "--vault", v.path, "--device", code, "--kind", "trial", "--days", "30", "--journal", File(d, "j.log").path), io) { now })
        val key = io.out.single(); assertTrue(key.length > 100)
        val ring = trustedFrom(v); val cands = KeyRing.let { listOf(ring.find(ring.let { r -> v.readLines().first { l -> l.startsWith("kid=") }.removePrefix("kid=") })!!) }
        // verified exactly as the TV does: right key, bound to THIS device code, inside its install window
        val start = (now / 86_400_000L * 86_400_000L) + 86_400_000L
        assertTrue(CompactActivation.verify(key, ring, cands, code, now) is ActivationResult.Accepted)
        assertTrue(CompactActivation.verify(key, ring, cands, DeviceCode.of(Fingerprints(mapOf(FactorKind.FLASH to "z".repeat(32)))), now) is ActivationResult.Rejected, "another TV refuses it")
        assertTrue(CompactActivation.verify(key, ring, cands, code, start + 60L * 86_400_000L) is ActivationResult.Rejected, "outside the 30-day window")
        // one mistyped character is ALWAYS caught by the group check (odd weights), whichever key was drawn
        val i = key.indexOf('-', 6) + 2                                   // a data character of the 3rd group
        val wrong = if (key[i] == 'K') 'M' else 'K'
        assertTrue(CompactActivation.verify(key.substring(0, i) + wrong + key.substring(i + 1), ring, cands, code, now) is ActivationResult.Rejected, "a mistyped character is refused")
        assertTrue(File(d, "j.log").readText().contains(code), "the journal records the issue (never the key)")
        assertTrue(!File(d, "j.log").readText().contains(key))
    }

    @Test fun compactRefusesAMalformedDeviceCode() {
        val d = dir(); val v = File(d, "c.txt"); OwnerCli.run(listOf("init", "--vault", v.path), Rec()) { now }
        val io = Rec(); assertEquals(2, OwnerCli.run(listOf("compact", "--vault", v.path, "--device", "1234"), io) { now }); assertTrue(io.err.single().contains("mal formé"))
    }

    @Test fun fullActivationIsAcceptedByTheTvVerifierAndFileIsWritten() {
        val d = dir(); val v = File(d, "c.txt"); OwnerCli.run(listOf("init", "--vault", v.path), Rec()) { now }
        val fp = device(); val req = request(d, fp); val io = Rec()
        val rc = OwnerCli.run(listOf("activation", "--vault", v.path, "--request", req.path, "--kind", "production", "--license", "lic-0001",
            "--purchase", "prod-math:bundle-6e,bundle-5e", "--days", "90", "--out-file", File(d, "usb").path, "--journal", File(d, "j.log").path), io) { now }
        assertEquals(0, rc, io.err.toString())
        val token = io.out.single()
        val ok = ActivationVerifier(trustedFrom(v)).verify(token, fp, now)
        assertTrue(ok is ActivationResult.Accepted, ok.toString())
        assertEquals("lic-0001", (ok as ActivationResult.Accepted).activation.license)
        assertTrue(ok.activation.rights.single() is Right.Purchase)
        assertEquals(token + "\n", File(File(d, "usb"), Activation.FILE_NAME).readText())
        // another TV refuses it (it is bound to this hardware), and a tampered token is refused
        val other = Fingerprints(mapOf(FactorKind.FLASH to "d".repeat(32), FactorKind.ETHERNET to "e".repeat(32), FactorKind.SYSTEM_SERIAL to "f".repeat(32)))
        assertTrue(ActivationVerifier(trustedFrom(v)).verify(token, other, now) is ActivationResult.Rejected)
        assertTrue(ActivationVerifier(trustedFrom(v)).verify(token.dropLast(3) + "AAA", fp, now) is ActivationResult.Rejected)
    }

    @Test fun trialActivationHasNoRightsAndProductionNeedsNeitherALicenseNorARight() {
        val d = dir(); val v = File(d, "c.txt"); OwnerCli.run(listOf("init", "--vault", v.path), Rec()) { now }
        val fp = device(); val req = request(d, fp)
        val t = Rec(); assertEquals(0, OwnerCli.run(listOf("activation", "--vault", v.path, "--request", req.path, "--journal", File(d, "j").path), t) { now })
        assertTrue(ActivationVerifier(trustedFrom(v)).verify(t.out.single(), fp, now) is ActivationResult.Accepted)
        val noLic = Rec(); assertEquals(0, OwnerCli.run(listOf("activation", "--vault", v.path, "--request", req.path, "--kind", "production"), noLic) { now })
        val gen = ActivationVerifier(trustedFrom(v)).verify(noLic.out.single(), fp, now); assertTrue(gen is ActivationResult.Accepted)
        assertTrue(Regex("^lic-[0-9a-f]{10}$").matches((gen as ActivationResult.Accepted).activation.license)); assertTrue(gen.activation.rights.isEmpty())
        val noRight = Rec(); assertEquals(0, OwnerCli.run(listOf("activation", "--vault", v.path, "--request", req.path, "--kind", "production", "--license", "lic-1"), noRight) { now })
    }

    @Test fun openAllIsBoundedToThirtyDays() {
        val d = dir(); val v = File(d, "c.txt"); OwnerCli.run(listOf("init", "--vault", v.path), Rec()) { now }
        val req = request(d, device())
        val io = Rec(); val rc = OwnerCli.run(listOf("activation", "--vault", v.path, "--request", req.path, "--kind", "production", "--license", "lic-2",
            "--open-all", "tout-prod", "--open-days", "45", "--journal", File(d, "j").path), io) { now }
        assertEquals(3, rc, "45 days is refused: the issuer caps « tout ouvert » at 30"); assertTrue(io.err.single().contains("30 jours"))
        val ok = Rec(); assertEquals(0, OwnerCli.run(listOf("activation", "--vault", v.path, "--request", req.path, "--kind", "production", "--license", "lic-2",
            "--open-all", "tout-prod", "--open-days", "30", "--journal", File(d, "j").path), ok) { now })
    }

    @Test fun tamperedRequestIsRefused() {
        val d = dir(); val v = File(d, "c.txt"); OwnerCli.run(listOf("init", "--vault", v.path), Rec()) { now }
        val fp = device(); val req = request(d, fp)
        req.writeText(req.readText().replace("factor=FLASH|" + "a".repeat(32), "factor=FLASH|" + "9".repeat(32)))
        val io = Rec(); assertEquals(2, OwnerCli.run(listOf("activation", "--vault", v.path, "--request", req.path), io) { now }); assertTrue(io.err.single().contains("ne correspond pas"))
    }

    @Test fun inspectShowsTheFactors() {
        val d = dir(); val io = Rec(); val req = request(d, device())
        assertEquals(0, OwnerCli.run(listOf("inspect", "--request", req.path), io) { now })
        assertTrue(io.out.first().contains("k=2 sur n=3") && io.out.any { it.contains("FLASH") })
        assertNotEquals(0, OwnerCli.run(listOf("nimportequoi"), Rec()) { now })
    }

    private val installPub = castbridge.core.lots.InstallKey.fromSeed(ByteArray(32) { (it + 5).toByte() }).pub      // test seed, never a real key
    private fun requestV2(d: File, fp: Fingerprints): File = File(d, "demande-v2.txt").also { it.writeText(OwnerFrames.deviceInfo(DeviceCode.of(fp), fp, installPub).replace("\n", "\r\n\r\n")) }

    @Test fun aV2RequestIsReadByInspectAndActivationAndTheInstallKeyIsReported() {
        val d = dir(); val v = File(d, "c.txt"); OwnerCli.run(listOf("init", "--vault", v.path), Rec()) { now }
        val fp = device(); val v2 = requestV2(d, fp)
        val io = Rec(); assertEquals(0, OwnerCli.run(listOf("inspect", "--request", v2.path), io) { now })
        assertTrue(io.out.first().contains("clé d'installation : présente"), io.out.first())
        val old = Rec(); OwnerCli.run(listOf("inspect", "--request", request(d, fp).path), old) { now }; assertTrue(old.out.first().contains("clé d'installation : absente"))
        val act = Rec(); assertEquals(0, OwnerCli.run(listOf("activation", "--vault", v.path, "--request", v2.path, "--kind", "trial", "--journal", File(d, "j").path), act) { now }, act.err.toString())
    }

    @Test fun aMalformedInstallLineIsRefusedNotTreatedAsAV1Request() {
        val d = dir(); val req = requestV2(d, device())
        req.writeText(req.readText().replace("install=x25519|", "install=x25519|a"))
        val io = Rec(); assertEquals(2, OwnerCli.run(listOf("inspect", "--request", req.path), io) { now })
        assertTrue(io.err.single().contains("clé d'installation"), io.err.toString())
    }
}
