package castbridge.desktop

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
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CliTest {
    private val now = 1_800_000_000_000L
    private var tick = 0L   // each call is a few seconds later, like the real owner (events are ordered by date)
    private val pass = "un-code-de-test-long"

    private class Run(val code: Int, val out: String, val err: String)

    private val dir = Files.createTempDirectory("activation-desktop").toFile().also { it.deleteOnExit() }
    private val raw = RawFactors("FLASHSERIAL-A1", "cid-a1", "AA:BB:CC:00:11:01", "10:20:30:40:50:01", "/sys/devices/platform/soc/ffe03000.sd/mmc_host/mmc1/net/wlan0", "SYSA0001", "11:22:33:44:55:01")
    private val fp = DeviceIdentity.fingerprints(raw)
    private val request = File(dir, "demande.txt").also { it.writeText(OwnerFrames.deviceInfo(DeviceCode.of(fp), fp, castbridge.core.lots.InstallKey.fromSeed(ByteArray(32) { 7 }).pub)) }

    private fun cli(vararg args: String, passphrase: String = pass): Run {
        val o = ByteArrayOutputStream(); val e = ByteArrayOutputStream()
        val env = Env(PrintStream(o, true, "UTF-8"), PrintStream(e, true, "UTF-8"), getenv = { if (it == "CODE_TEST") passphrase else null }, clock = { now + 1000 * (tick++) }, kdf = ScryptKdf(16, 1, 1))
        val code = Cli(env).run(listOf("--dossier", File(dir, "home").path, "--code-env", "CODE_TEST").let { common ->
            // global options go after the command
            listOf(args[0]) + args.drop(1) + common
        })
        return Run(code, o.toString("UTF-8"), e.toString("UTF-8"))
    }

    @Test fun trialEndToEndWritesTheFileTheTvReadsAndNeverTheKey() {
        val k = cli("cle-creer"); assertEquals(0, k.code, k.err)
        assertTrue(File(dir, "home/desk.key.json").isFile)
        val i = cli("emettre", "--appareil", request.path, "--sortie", File(dir, "usb").path, "--qr"); assertEquals(0, i.code, i.err)
        val file = File(dir, "usb/activation"); assertTrue(file.isFile)
        val token = file.readText(); assertTrue(token.startsWith(castbridge.core.owner.Envelope.PREFIX + ".") && token.endsWith("\n") && token.trimEnd('\n').lines().size == 1)
        assertTrue(File(dir, "usb/activation.png").length() > 100)
        val v = cli("verifier", file.path, "--appareil", request.path, "--maintenant", now.toString()); assertEquals(0, v.code, v.out + v.err); assertTrue(v.out.startsWith("ACCEPTÉ"))
        // nothing secret anywhere: neither the code nor the seed in any file of the home folder
        for (f in File(dir, "home").walkTopDown().filter { it.isFile }) assertFalse(f.readText().contains(pass), "le code n'est écrit nulle part : ${f.name}")
        val j = cli("journal"); assertTrue(j.out.contains("trial") && j.out.contains(DeviceCode.of(fp)), j.out)
    }

    @Test fun wrongUnlockCodeSignsNothing() {
        assertEquals(0, cli("cle-creer").code)
        val r = cli("emettre", "--appareil", request.path, passphrase = "un-autre-code-assez-long")
        assertEquals(3, r.code); assertFalse(File(dir, "activation").exists())
        assertFalse(File(dir, "home/journal.jsonl").exists())
    }

    @Test fun shortUnlockCodeIsRefused() {
        val r = cli("cle-creer", passphrase = "court"); assertEquals(1, r.code); assertTrue(r.err.contains("10 caractères"))
    }

    @Test fun anExistingKeyIsNeverOverwritten() {
        assertEquals(0, cli("cle-creer").code)
        val before = File(dir, "home/desk.key.json").readText()
        assertEquals(1, cli("cle-creer").code)
        assertEquals(before, File(dir, "home/desk.key.json").readText())
    }

    @Test fun productionConsumesSeatsOnlyForNewHardwareAndReactivationIsFree() {
        assertEquals(0, cli("cle-creer").code)
        assertEquals(1, cli("emettre", "--appareil", request.path, "--production", "--licence", "lic-0001", "--achat", "p-cm2=classe-cm2").code, "licence inconnue : refusée")
        assertEquals(0, cli("licence", "lic-0001", "--postes", "1").code)
        val a = cli("emettre", "--appareil", request.path, "--production", "--licence", "lic-0001", "--achat", "p-cm2=classe-cm2", "--sortie", File(dir, "u1").path)
        assertEquals(0, a.code, a.err); assertTrue(a.out.contains("postes restants : 0"), a.out)
        val b = cli("emettre", "--appareil", request.path, "--production", "--licence", "lic-0001", "--achat", "p-cm2=classe-cm2", "--sortie", File(dir, "u2").path)
        assertEquals(0, b.code, b.err); assertTrue(b.out.contains("ré-activation"), b.out)
        // another TV: no seat left
        val raw2 = raw.copy(flashSerial = "AUTRE", flashCid = "autre", ethernetMac = "AA:BB:CC:00:99:99", wifiMac = "10:20:30:40:99:99", systemSerial = "SYSB", bluetoothAddress = "11:22:33:44:99:99")
        val fp2 = DeviceIdentity.fingerprints(raw2); val other = File(dir, "autre.txt").also { it.writeText(OwnerFrames.deviceInfo(DeviceCode.of(fp2), fp2)) }
        val c = cli("emettre", "--appareil", other.path, "--production", "--licence", "lic-0001", "--achat", "p-cm2=classe-cm2", "--sortie", File(dir, "u3").path)
        assertEquals(1, c.code); assertFalse(File(dir, "u3/activation").exists())
        val st = cli("registre", "etat"); assertTrue(st.out.contains("lic-0001 : 1/1"), st.out)
    }

    @Test fun registryExportImportMergesWithoutDuplicates() {
        assertEquals(0, cli("cle-creer").code); assertEquals(0, cli("licence", "lic-0002", "--postes", "2").code)
        val exp = File(dir, "reg.json"); assertEquals(0, cli("registre", "exporter", exp.path).code)
        val r = cli("registre", "importer", exp.path); assertEquals(0, r.code); assertTrue(r.out.startsWith("0 "), r.out)
    }

    @Test fun compactKeyIs165CharactersAndBoundToTheCode() {
        assertEquals(0, cli("cle-creer").code)
        val c = cli("cle-saisissable", "--code", DeviceCode.of(fp)); assertEquals(0, c.code, c.err)
        assertEquals(165, c.out.trim().replace("-", "").length, "33 groupes de 4 + 1 contrôle : 165 caractères sans les tirets")
    }

    @Test fun badInputsAreRefusedInFrench() {
        assertEquals(0, cli("cle-creer").code)
        val bad = File(dir, "mauvaise.txt").also { it.writeText("code=ABCD-EFGH-JKMN-PQRZ\nk=2\nfactor=FLASH|00") }
        val r = cli("emettre", "--appareil", bad.path); assertEquals(1, r.code); assertTrue(r.err.startsWith("Refusé"), r.err)
        val old = cli("emettre", "--appareil", request.path, "--jours", "400"); assertEquals(2, old.code); assertTrue("48 h" in old.err, old.err)    // the old option is refused loudly, never ignored
        assertEquals(2, cli("inconnue").code)
    }

    // ---- usage ceiling and trial window of rented lots ----
    private fun setup() { assertEquals(0, cli("cle-creer").code); assertEquals(0, cli("licence", "lic-u", "--postes", "5").code) }
    private fun issue(name: String, vararg extra: String): Run = cli("emettre", "--appareil", request.path, "--sortie", File(dir, name).path, *(if ("--production" in extra) arrayOf("--licence", "lic-u", "--achat", "p-cm2=classe-cm2") else emptyArray()), *extra)
    private fun act(name: String) = castbridge.core.owner.Activation.decode(File(dir, "$name/activation").readText().trim())!!
    private fun usage(name: String) = act(name).rights.filterIsInstance<castbridge.core.lots.Right.Usage>().firstOrNull()
    private fun windows(name: String) = act(name).rights.filterIsInstance<castbridge.core.lots.Right.Rental>().filter { it.productId == "essai" }
    private val dayMs = 24L * 3600 * 1000

    @Test fun trialByDefaultHas30DaysOfUsageAndTheTrialLotWindow() {
        setup()
        val r = issue("t1"); assertEquals(0, r.code, r.err)
        val u = usage("t1")!!; assertEquals(30 * dayMs, u.endsAt - u.startsAt)
        val w = windows("t1").single()
        assertEquals(listOf("tout"), w.bundleIds); assertEquals(3, w.durationDays); assertEquals(720, w.maxUsageMinutes)
        assertTrue(r.out.contains("Fenêtre de lots d'essai (usage unique) : 720 min d'usage, dans les 3 jours"), r.out)
        assertFalse(r.out.contains("Location essai"), r.out)
    }

    @Test fun productionWithoutLicenceGeneratesOneAndNeedsNoRight() {
        assertEquals(0, cli("cle-creer").code)
        val r = cli("emettre", "--appareil", request.path, "--production", "--usage-jours", "62", "--sortie", File(dir, "g1").path); assertEquals(0, r.code, r.err)
        val a = act("g1")
        assertTrue(Regex("^lic-[0-9a-f]{10}$").matches(a.license), a.license)
        assertTrue(r.out.contains("Licence ${a.license} (générée)"), r.out)
        assertEquals(listOf(62 * dayMs), a.rights.map { (it as castbridge.core.lots.Right.Usage).let { u -> u.endsAt - u.startsAt } })      // the duration is the only right
        val state = cli("registre", "etat"); assertTrue(state.out.contains("${a.license} : 1/1 postes"), state.out)
        // unlimited by default: no right at all
        val u = cli("emettre", "--appareil", request.path, "--production", "--sortie", File(dir, "g2").path); assertEquals(0, u.code, u.err)
        assertTrue(act("g2").rights.isEmpty()); assertNotEquals(a.license, act("g2").license)
        // a typed licence keeps the old behaviour (unknown: refused)
        assertEquals(1, cli("emettre", "--appareil", request.path, "--production", "--licence", "lic-nope", "--sortie", File(dir, "g3").path).code)
    }

    @Test fun trialWithoutTrialLotsHasNoWindow() {
        setup()
        val r = issue("t2", "--sans-lots-essai"); assertEquals(0, r.code, r.err)
        assertTrue(windows("t2").isEmpty()); assertFalse(r.out.contains("Fenêtre de lots"), r.out)
        assertNotNull(usage("t2"))
    }

    @Test fun trialUsageDaysAreHonouredAndBounded() {
        setup()
        assertEquals(0, issue("t3", "--usage-jours", "7").code)
        val u = usage("t3")!!; assertEquals(7 * dayMs, u.endsAt - u.startsAt)
        val r = issue("t4", "--usage-jours", "illimitee"); assertEquals(2, r.code); assertTrue(r.err.contains("Un essai a toujours une durée"), r.err)
        assertFalse(File(dir, "t4/activation").exists())
        assertEquals(1, issue("t5", "--usage-jours", "366").code)
        assertEquals(2, issue("t6", "--usage-jours", "abc").code)
    }

    @Test fun productionUsageIsUnlimitedByDefaultOrBounded() {
        setup()
        assertEquals(0, issue("p1", "--production").code); assertNull(usage("p1")); assertTrue(windows("p1").isEmpty())
        assertEquals(0, issue("p2", "--production", "--usage-jours", "62").code)
        val u = usage("p2")!!; assertEquals(62 * dayMs, u.endsAt - u.startsAt)
        assertEquals(0, issue("p3", "--production", "--usage-jours", "illimitee").code); assertNull(usage("p3"))
        val r = issue("p4", "--production", "--usage-jours", "4000"); assertEquals(1, r.code); assertTrue(r.err.contains("Refusé"), r.err)
        assertFalse(File(dir, "p4/activation").exists())
    }

    @Test fun superIsPermanentAndRefusesAUsageCeiling() {
        setup()
        val r = issue("s1", "--production", "--super", "--usage-jours", "30"); assertEquals(1, r.code); assertTrue(r.err.contains("SUPER_UNLIMITED est permanent"), r.err)
        assertFalse(File(dir, "s1/activation").exists())
    }

    @Test fun rentalLongerThanTheCatalogueIsRefusedAndWithinIsAccepted() {
        setup()
        val cat = File(dir, "manifest-rd.json").also { it.writeText("""{"bundles":[{"id":"classe-cm2","type":"classe","rentalDays":14,"lots":["learn:cm2","quiz:cm2"]}]}""") }
        val free = File(dir, "libres-rd.txt").also { it.writeText("langues:fr-a0\n") }
        val common = arrayOf("--production", "--catalogue", cat.path, "--lots-libres", free.path)
        val bad = issue("r1", *common, "--location", "x=classe-cm2:30"); assertEquals(1, bad.code); assertTrue(bad.err.contains("fixée par le catalogue du serveur"), bad.err)
        assertFalse(File(dir, "r1/activation").exists())
        val ok = issue("r2", *common, "--location", "x=classe-cm2:14"); assertEquals(0, ok.code, ok.err)
        assertTrue(ok.out.contains("Location x (classe-cm2) : 14 jour(s)"), ok.out)
    }

    /** Second audit w23-05, MEDIUM-C : le bureau montre l'empreinte de la clé de signature qu'il lie, pour la comparer avec l'écran de la TV. */
    @Test fun theDeskShowsTheFingerprintOfTheInstallationKeyItBinds() {
        val tvKey = castbridge.core.owner.Ed25519Signer(ByteArray(32) { (it + 77).toByte() }).publicKey
        val withKey = File(dir, "demande-ik.txt").also { it.writeText(OwnerFrames.deviceInfo(DeviceCode.of(fp), fp, null, tvKey)) }
        val expected = castbridge.core.owner.ActivationBinding.fingerprint(tvKey)
        assertEquals(0, cli("cle-creer").code)
        val seen = cli("appareil", withKey.path); assertEquals(0, seen.code, seen.err)
        assertTrue(seen.out.contains(expected), "l'inspection affiche l'empreinte : ${seen.out}")
        assertEquals(0, cli("licence", "lic-0009", "--postes", "1").code)
        val issued = cli("emettre", "--appareil", withKey.path, "--production", "--licence", "lic-0009", "--sortie", File(dir, "usb-ik").path)
        assertEquals(0, issued.code, issued.err)
        assertTrue(issued.out.contains(expected), "l'émission rappelle l'empreinte de la clé liée : ${issued.out}")
        val without = cli("appareil", request.path)
        assertTrue(without.out.contains("absente (activation sans clé liée"), without.out)
    }
}
