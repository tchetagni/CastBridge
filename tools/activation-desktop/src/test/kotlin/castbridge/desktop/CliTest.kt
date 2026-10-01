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
import kotlin.test.assertTrue

class CliTest {
    private val now = 1_800_000_000_000L
    private var tick = 0L   // each call is a few seconds later, like the real owner (events are ordered by date)
    private val pass = "un-code-de-test-long"

    private class Run(val code: Int, val out: String, val err: String)

    private val dir = Files.createTempDirectory("activation-desktop").toFile().also { it.deleteOnExit() }
    private val raw = RawFactors("FLASHSERIAL-A1", "cid-a1", "AA:BB:CC:00:11:01", "10:20:30:40:50:01", "/sys/devices/platform/soc/ffe03000.sd/mmc_host/mmc1/net/wlan0", "SYSA0001", "11:22:33:44:55:01")
    private val fp = DeviceIdentity.fingerprints(raw)
    private val request = File(dir, "demande.txt").also { it.writeText(OwnerFrames.deviceInfo(DeviceCode.of(fp), fp)) }

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
        val i = cli("emettre", "--appareil", request.path, "--jours", "90", "--sortie", File(dir, "usb").path, "--qr"); assertEquals(0, i.code, i.err)
        val file = File(dir, "usb/activation"); assertTrue(file.isFile)
        val token = file.readText(); assertTrue(token.startsWith("cba1.") && token.endsWith("\n") && token.trimEnd('\n').lines().size == 1)
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
        val a = cli("emettre", "--appareil", request.path, "--production", "--licence", "lic-0001", "--achat", "p-cm2=classe-cm2", "--jours", "60", "--sortie", File(dir, "u1").path)
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
        val c = cli("cle-saisissable", "--code", DeviceCode.of(fp), "--jours", "30"); assertEquals(0, c.code, c.err)
        assertEquals(165, c.out.trim().replace("-", "").length, "33 groupes de 4 + 1 contrôle : 165 caractères sans les tirets")
    }

    @Test fun badInputsAreRefusedInFrench() {
        assertEquals(0, cli("cle-creer").code)
        val bad = File(dir, "mauvaise.txt").also { it.writeText("code=ABCD-EFGH-JKMN-PQRZ\nk=2\nfactor=FLASH|00") }
        val r = cli("emettre", "--appareil", bad.path); assertEquals(1, r.code); assertTrue(r.err.startsWith("Refusé"), r.err)
        assertEquals(1, cli("emettre", "--appareil", request.path, "--jours", "400").code)
        assertEquals(2, cli("inconnue").code)
    }
}
