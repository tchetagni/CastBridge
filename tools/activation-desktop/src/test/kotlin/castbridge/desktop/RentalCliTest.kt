package castbridge.desktop

import castbridge.core.lots.RentalKeys
import castbridge.core.lots.RentalLines
import castbridge.core.lots.RentalVectors
import castbridge.core.lots.Right
import castbridge.core.owner.Activation
import castbridge.core.owner.DeviceCode
import castbridge.core.owner.DeviceIdentity
import castbridge.core.owner.OwnerFrames
import castbridge.core.owner.RawFactors
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RentalCliTest {
    private val now = 1_800_000_000_000L
    private var tick = 0L
    private val pass = "un-code-de-test-long"
    private class Run(val code: Int, val out: String, val err: String)
    private val dir = Files.createTempDirectory("activation-desktop-rental").toFile().also { it.deleteOnExit() }
    private val fp = DeviceIdentity.fingerprints(RawFactors("FLASHSERIAL-A1", "cid-a1", "AA:BB:CC:00:11:01", "10:20:30:40:50:01", "/sys/devices/platform/soc/ffe03000.sd/mmc_host/mmc1/net/wlan0", "SYSA0001", "11:22:33:44:55:01"))
    private val request = File(dir, "demande.txt").also { it.writeText(OwnerFrames.deviceInfo(DeviceCode.of(fp), fp)) }

    private fun cli(vararg args: String): Run {
        val o = ByteArrayOutputStream(); val e = ByteArrayOutputStream()
        val env = Env(PrintStream(o, true, "UTF-8"), PrintStream(e, true, "UTF-8"), getenv = { if (it == "CODE_TEST") pass else null }, clock = { now + 1000 * (tick++) }, kdf = ScryptKdf(16, 1, 1))
        val code = Cli(env).run(listOf(args[0]) + args.drop(1) + listOf("--dossier", File(dir, "home").path, "--code-env", "CODE_TEST"))
        return Run(code, o.toString("UTF-8"), e.toString("UTF-8"))
    }

    private fun setup() { assertEquals(0, cli("cle-creer").code); assertEquals(0, cli("licence", "lic-loc", "--postes", "2").code) }

    @Test fun locationIsIssuedVerifiedAndItsBoxOpensOnThatTvOnly() {
        setup()
        val r = cli("emettre", "--appareil", request.path, "--production", "--licence", "lic-loc", "--location", "loc-cm2=classe-cm2,quiz-cm2:30:600", "--sans-controle-catalogue", "--sortie", File(dir, "usb").path)
        assertEquals(0, r.code, r.err); assertTrue(r.out.contains("Location loc-cm2") && r.out.contains("usage maximal 600 min"), r.out)
        val a = Activation.decode(File(dir, "usb/activation").readText().trim())!!
        val l = a.rights.filterIsInstance<Right.Rental>().single()
        assertEquals(30, l.durationDays); assertEquals(600, l.maxUsageMinutes); assertEquals(listOf("classe-cm2", "quiz-cm2"), l.bundleIds); assertEquals(l.startsAt, l.period)
        assertNotNull(RentalKeys.openBox(l.box, fp, l.productId, l.period))
        val v = cli("verifier", File(dir, "usb/activation").path, "--appareil", request.path, "--maintenant", (l.startsAt + 1000).toString()); assertEquals(0, v.code, v.out + v.err)
        assertTrue(RentalLines.line(l).startsWith("rental|loc-cm2|classe-cm2,quiz-cm2|"))
    }

    @Test fun renewalKeepsThePeriodAndTheKey() {
        setup()
        assertEquals(0, cli("emettre", "--appareil", request.path, "--production", "--licence", "lic-loc", "--location", "loc-cm2=classe-cm2:30", "--sans-controle-catalogue", "--sortie", File(dir, "a").path).code)
        val first = Activation.decode(File(dir, "a/activation").readText().trim())!!.rights.filterIsInstance<Right.Rental>().single()
        val r = cli("emettre", "--appareil", request.path, "--production", "--licence", "lic-loc", "--location", "loc-cm2=classe-cm2:30", "--sans-controle-catalogue", "--periode", first.period.toString(), "--sortie", File(dir, "b").path)
        assertEquals(0, r.code, r.err)
        val renewal = Activation.decode(File(dir, "b/activation").readText().trim())!!.rights.filterIsInstance<Right.Rental>().single()
        assertEquals(first.period, renewal.period); assertTrue(renewal.startsAt > first.startsAt)
        assertEquals(RentalKeys.fingerprintOf(RentalKeys.openBox(first.box, fp, first.productId, first.period)!!), RentalKeys.fingerprintOf(RentalKeys.openBox(renewal.box, fp, renewal.productId, renewal.period)!!), "same key: delivered lots stay readable")
    }

    @Test fun aFreeLotIsNeverRentedAndOutOfBoundsAreRefused() {
        setup()
        val cat = File(dir, "manifest.json").also { it.writeText("""{"bundles":[{"id":"classe-cm2","type":"classe","rentalDays":30,"lots":["learn:cm2","quiz:cm2"]},{"id":"langue-fr","type":"langue","rentalDays":30,"lots":["langues:fr-a0"]}]}""") }
        val free = File(dir, "libres.txt").also { it.writeText("# lots libres CC BY-SA\nlangues:fr-a0\n") }
        val bad = cli("emettre", "--appareil", request.path, "--production", "--licence", "lic-loc", "--location", "x=langue-fr:30", "--catalogue", cat.path, "--lots-libres", free.path, "--sortie", File(dir, "c").path)
        assertEquals(1, bad.code); assertTrue(bad.err.contains("libre"), bad.err); assertTrue(!File(dir, "c/activation").exists())
        val ok = cli("emettre", "--appareil", request.path, "--production", "--licence", "lic-loc", "--location", "x=classe-cm2:30", "--catalogue", cat.path, "--lots-libres", free.path, "--sortie", File(dir, "d").path)
        assertEquals(0, ok.code, ok.err)
        assertEquals(1, cli("emettre", "--appareil", request.path, "--production", "--licence", "lic-loc", "--location", "x=classe-cm2:367", "--sans-controle-catalogue", "--sortie", File(dir, "e").path).code)
        assertEquals(1, cli("emettre", "--appareil", request.path, "--licence", "lic-loc", "--location", "x=classe-cm2:30", "--sans-controle-catalogue", "--sortie", File(dir, "f").path).code, "no rental in a trial activation")
    }

    private fun durCat() = File(dir, "durees.json").also { it.writeText("""{"bundles":[{"id":"classe-cm2","type":"classe","rentalDays":30,"lots":["learn:cm2","quiz:cm2"]},{"id":"quiz-cm2","type":"quiz","rentalDays":60,"lots":["quiz:cm2"]},{"id":"sans-duree","type":"classe","lots":["learn:x"]}]}""") }
    private fun durFree() = File(dir, "libres-d.txt").also { it.writeText("# aucun\nlangues:fr-a0\n") }
    private fun rent(name: String, vararg extra: String) = cli("emettre", "--appareil", request.path, "--production", "--licence", "lic-loc", "--sortie", File(dir, name).path, *extra)
    private fun withCat(vararg extra: String) = arrayOf("--catalogue", durCat().path, "--lots-libres", durFree().path, *extra)

    @Test fun rentalDurationIsExactlyTheServers() {
        setup()
        val shorter = rent("g1", *withCat("--location", "x=classe-cm2:29")); assertEquals(1, shorter.code); assertTrue(shorter.err.contains("fixée par le catalogue du serveur") && shorter.err.contains("30"), shorter.err)
        val longer = rent("g2", *withCat("--location", "x=classe-cm2:31")); assertEquals(1, longer.code); assertTrue(longer.err.contains("fixée par le serveur"), longer.err)
        assertTrue(!File(dir, "g1/activation").exists() && !File(dir, "g2/activation").exists())
        assertEquals(0, rent("g3", *withCat("--location", "x=classe-cm2:30")).code)
    }

    @Test fun locationBouquetBuildsOneExactRentalPerBundle() {
        setup()
        val r = rent("b1", *withCat("--location-bouquet", "classe-cm2,quiz-cm2")); assertEquals(0, r.code, r.err)
        val rs = Activation.decode(File(dir, "b1/activation").readText().trim())!!.rights.filterIsInstance<Right.Rental>().sortedBy { it.productId }
        assertEquals(listOf("loc-classe-cm2", "loc-quiz-cm2"), rs.map { it.productId }); assertEquals(listOf(30, 60), rs.map { it.durationDays })
    }

    @Test fun aBundleWithoutServerDurationGetsTheDefault30DaysExactAndUnknownIsRefused() {
        setup()
        val r = rent("n1", *withCat("--location-bouquet", "sans-duree")); assertEquals(0, r.code, r.err)
        assertEquals(30, Activation.decode(File(dir, "n1/activation").readText().trim())!!.rights.filterIsInstance<Right.Rental>().single().durationDays)
        val other = rent("n2", *withCat("--location", "x=sans-duree:45")); assertEquals(1, other.code); assertTrue(other.err.contains("fixée par le serveur à 30"), other.err)
        assertEquals(0, rent("n4", *withCat("--location", "x=sans-duree:30")).code)
        assertEquals(1, rent("n3", *withCat("--location-bouquet", "inconnu")).code)
        assertTrue(!File(dir, "n2/activation").exists())
    }

    @Test fun noCatalogueMeansNoRentalUnlessExplicitlyWaived() {
        setup()
        val a = rent("c1", "--location", "x=classe-cm2:30"); assertEquals(1, a.code); assertTrue(a.err.contains("chargez le catalogue du serveur"), a.err)
        val b = rent("c2", "--location-bouquet", "classe-cm2"); assertEquals(1, b.code); assertTrue(b.err.contains("catalogue"), b.err)
        assertTrue(!File(dir, "c1/activation").exists() && !File(dir, "c2/activation").exists())
        val ok = rent("c3", "--location", "x=classe-cm2:30", "--sans-controle-catalogue"); assertEquals(0, ok.code, ok.err); assertTrue(ok.out.contains("NON vérifiées"), ok.out)
    }

    @Test fun commonRentalVectorsGiveTheSameBytes() {
        val f = File(System.getProperty("activation.vectors")).parentFile.resolve("rental-vectors.json")
        assertEquals(emptyList(), RentalVectors.run(f.readText()))
    }
}
