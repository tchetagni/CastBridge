package castbridge.desktop

import castbridge.core.owner.Activation
import castbridge.core.owner.DeviceCode
import castbridge.core.owner.DeviceIdentity
import castbridge.core.owner.OwnerFrames
import castbridge.core.owner.RawFactors
import castbridge.core.owner.Subject
import castbridge.core.tv.activation.ActivationLookup
import castbridge.core.tv.activation.ActivationNames
import castbridge.core.tv.activation.Verdict
import castbridge.core.tv.activation.VolumeFact
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Assume.assumeFalse

/**
 * « emettre --cle-usb » and « cle-usb » (docs/ACTIVATION-TOOLS.md § 10): a client's USB key is a temporary folder. A temporary folder is not a mount point, so the tests that are not about
 * that rule give their own ([anyFolder]); the real rule ([SystemVolumes]) has its own tests, which never write anywhere but in a temporary folder.
 */
class UsbKeyTest {
    private val now = 1_800_000_000_000L
    private var tick = 0L
    private var syncs = 0
    private var seq = 0
    private val pass = "un-code-de-test-long"
    private val dir = Files.createTempDirectory("activation-usb").toFile().also { it.deleteOnExit() }
    private val anyFolder = UsbPlatform(volumeRule = VolumeRule { null }, sync = { syncs++ })

    private class Run(val code: Int, val out: String, val err: String)

    // two TVs, each with its installation key (a trial key carries the window of rented lots, which needs it)
    private val rawA = RawFactors("FLASHSERIAL-A1", "cid-a1", "AA:BB:CC:00:11:01", "10:20:30:40:50:01", "/sys/devices/platform/soc/ffe03000.sd/mmc_host/mmc1/net/wlan0", "SYSA0001", "11:22:33:44:55:01")
    private val rawB = rawA.copy(flashSerial = "AUTRE", flashCid = "autre", ethernetMac = "AA:BB:CC:00:99:99", wifiMac = "10:20:30:40:99:99", systemSerial = "SYSB", bluetoothAddress = "11:22:33:44:99:99")
    private val fpA = DeviceIdentity.fingerprints(rawA)
    private val fpB = DeviceIdentity.fingerprints(rawB)
    private val codeA = DeviceCode.of(fpA)
    private val codeB = DeviceCode.of(fpB)
    private val requestA = File(dir, "demande-a.txt").also { it.writeText(OwnerFrames.deviceInfo(codeA, fpA, castbridge.core.lots.InstallKey.fromSeed(ByteArray(32) { 7 }).pub)) }
    private val requestB = File(dir, "demande-b.txt").also { it.writeText(OwnerFrames.deviceInfo(codeB, fpB, castbridge.core.lots.InstallKey.fromSeed(ByteArray(32) { 9 }).pub)) }
    private val journal get() = File(dir, "home/journal.jsonl")
    private val issued get() = if (journal.isFile) journal.readLines().count { it.isNotBlank() } else 0
    private val fourFiles = setOf("Android/data/castbridge.receiver/files/activation", "Download/CastBridge/activation", "activation", "LISEZMOI-CASTBRIDGE.txt")

    @AfterTest fun cleanUp() { dir.walkTopDown().forEach { it.setWritable(true) }; dir.deleteRecursively() }

    private fun cli(vararg args: String, usb: UsbPlatform = anyFolder, clock: (() -> Long)? = null, passphrase: String = pass): Run {
        val o = ByteArrayOutputStream(); val e = ByteArrayOutputStream()
        val env = Env(PrintStream(o, true, "UTF-8"), PrintStream(e, true, "UTF-8"), getenv = { if (it == "CODE_TEST") passphrase else null }, clock = clock ?: { now + 1000 * (tick++) },
            kdf = ScryptKdf(16, 1, 1), usb = usb)
        val code = Cli(env).run(listOf(args[0]) + args.drop(1) + listOf("--dossier", File(dir, "home").path, "--code-env", "CODE_TEST"))
        return Run(code, o.toString("UTF-8"), e.toString("UTF-8"))
    }

    private fun volume(name: String = "CLE"): File = File(dir, "volumes/$name").also { it.mkdirs() }
    private fun makeKey() = assertEquals(0, cli("cle-creer").code)

    /** `emettre` for [request] with the key [vol]; the local copy always goes to a temporary folder (never to the working folder). */
    private fun emettre(vol: File, request: File = requestA, vararg extra: String, usb: UsbPlatform = anyFolder, passphrase: String = pass): Run =
        cli("emettre", "--appareil", request.path, "--sortie", File(dir, "sortie-${seq++}").path, "--cle-usb", vol.path, *extra, usb = usb, passphrase = passphrase)

    /** An activation issued without any key (the local copy only). */
    private fun issueLocal(name: String, request: File = requestA, vararg extra: String): File {
        val out = File(dir, name)
        val r = cli("emettre", "--appareil", request.path, "--sortie", out.path, *extra); assertEquals(0, r.code, r.err)
        return File(out, "activation")
    }

    private fun filesOn(vol: File): Set<String> = vol.walkTopDown().filter { it.isFile }.map { it.relativeTo(vol).invariantSeparatorsPath }.toSet()
    private fun codeOn(vol: File, rel: String): String = UsbKey.codeOf(Activation.decode(File(vol, rel).readText().trim())!!)

    /** The command printed by a failure to finish the key without issuing again: its activation file. */
    private fun recoveryFile(err: String): File = File((Regex("cle-usb --activation (.+?) --cle-usb").find(err)?.groupValues?.get(1) ?: error("pas de commande de reprise : $err")).trim('"'))

    // ---- writing and reading back ----

    @Test fun emettreWithCleUsbWritesTheFourFilesAndReadsThemBack() {
        makeKey(); val vol = volume()
        val local = File(dir, "usb")
        val r = cli("emettre", "--appareil", requestA.path, "--sortie", local.path, "--cle-usb", vol.path); assertEquals(0, r.code, r.err)
        assertEquals(fourFiles, filesOn(vol), "exactement les quatre fichiers : ni fichier d'essai, ni clé du bureau")
        val line = File(local, "activation").readText()                                   // what `emettre` always wrote in --sortie: the key line and a newline
        assertTrue(line.startsWith(castbridge.core.owner.Envelope.PREFIX + ".") && line.endsWith("\n") && line.trimEnd('\n').lines().size == 1)
        for (rel in UsbKey.ACTIVATION_FILES) assertContentEquals(line.toByteArray(), File(vol, rel).readBytes(), "mêmes octets que le fichier émis : $rel")
        assertTrue(File(vol, "activation").length() <= 16 * 1024)
        assertTrue(r.out.contains("4 fichiers écrits sur ${vol.path} pour la TV $codeA"), r.out)
        assertEquals(1, syncs, "les écritures sont vidées une fois, avant la relecture")
        assertTrue(r.out.contains("sha256") && r.out.contains("Éjectez la clé"), r.out)
        // the key is a real, valid activation for this TV
        val v = cli("verifier", File(vol, "activation").path, "--appareil", requestA.path, "--maintenant", now.toString()); assertEquals(0, v.code, v.out + v.err); assertTrue(v.out.startsWith("ACCEPTÉ"))
        // nothing secret on the key
        for (f in vol.walkTopDown().filter { it.isFile }) { assertFalse(f.readText().contains(pass), "le code n'est pas sur la clé : ${f.name}"); assertNotEquals("desk.key.json", f.name) }
    }

    @Test fun withoutCleUsbNothingChangesAndNothingIsSynced() {
        makeKey()
        val out = File(dir, "usb")
        val r = cli("emettre", "--appareil", requestA.path, "--sortie", out.path); assertEquals(0, r.code, r.err)
        assertTrue(r.out.contains("Fichier pour la clé USB de la TV : ${File(out, "activation").path}"), r.out)
        assertFalse(r.out.contains("fichiers écrits sur")); assertEquals(0, syncs)
        assertEquals(setOf("activation"), out.walkTopDown().filter { it.isFile }.map { it.name }.toSet())
    }

    @Test fun theLisezmoiHasThreeLinesForTheClient() {
        assertEquals(3, UsbKey.README.lines().count { it.isNotBlank() })
        assertEquals(3, UsbKey.README_LINES.size)
        assertTrue(UsbKey.README.endsWith("\n"))
        assertTrue(UsbKey.README_LINES[0].contains("TV") && UsbKey.README_LINES[0].contains("USB"))
        assertTrue(UsbKey.README_LINES[2].contains("Chercher la clé USB"))
        assertTrue(UsbKey.README.contains("CastBridge-TV"), "l'application de la TV s'appelle CastBridge-TV")
        makeKey(); val vol = volume()
        assertEquals(0, emettre(vol).code)
        assertEquals(UsbKey.README, File(vol, "LISEZMOI-CASTBRIDGE.txt").readText(Charsets.UTF_8))
    }

    /** ACT-F6: what the tool writes is what CastBridge-TV looks at (its own lookup code, run on the key). */
    @Test fun theKeyIsFoundByTheLookupOfTheTvInEachFolderItReads() {
        makeKey(); val vol = volume()
        assertEquals(0, emettre(vol).code)
        val token = File(vol, "activation").readText().trim()
        fun found(download: List<Pair<File, String?>>, own: List<Pair<File, String?>>): Boolean =
            ActivationLookup.run(ActivationLookup.candidates(download, own), ActivationLookup.dirsToList(download, own), listOf(VolumeFact("CLE", false))) { line -> if (line == token) Verdict.ACCEPTED else Verdict.NOT_VALID }.accepted
        assertTrue(found(listOf(File(vol, "Download") to "CLE"), emptyList()), "Download/CastBridge/activation")
        assertTrue(found(emptyList(), listOf(File(vol, ActivationLookup.OWN_DIR_TEXT) to "CLE")), "le dossier propre de CastBridge-TV, seul lisible partout")
        assertTrue(ActivationNames.isOffered("activation", File(vol, "activation").length()), "la racine : proposée en première ligne par l'explorateur de la TV")
        // and the other way round: a key put by hand where the TV does not look is not found
        val elsewhere = volume("AILLEURS"); File(elsewhere, "Documents").mkdirs(); File(elsewhere, "Documents/activation").writeText(token + "\n")
        assertFalse(found(listOf(File(elsewhere, "Download") to "AILLEURS"), listOf(File(elsewhere, ActivationLookup.OWN_DIR_TEXT) to "AILLEURS")))
    }

    @Test fun theSameTvIsRenewedWithoutForce() {
        makeKey(); val vol = volume()
        assertEquals(0, emettre(vol).code)
        val first = File(vol, "activation").readBytes()
        val again = emettre(vol); assertEquals(0, again.code, again.err)
        assertFalse(again.out.contains("ATTENTION"), again.out)
        assertFalse(first.contentEquals(File(vol, "activation").readBytes()), "la nouvelle activation remplace l'ancienne")
        for (rel in UsbKey.ACTIVATION_FILES) assertEquals(codeA, codeOn(vol, rel))
        assertEquals(fourFiles, filesOn(vol))
    }

    @Test fun missingFoldersAreCreatedAndWhatIsAlreadyThereIsKept() {
        makeKey(); val vol = volume()
        File(vol, "Download").mkdirs(); File(vol, "Download/photo.jpg").writeText("ne pas toucher")
        File(vol, "Android/data/autre.app/files").mkdirs()
        assertEquals(0, emettre(vol).code)
        assertEquals("ne pas toucher", File(vol, "Download/photo.jpg").readText())
        assertTrue(File(vol, "Android/data/autre.app/files").isDirectory)
        assertTrue(filesOn(vol).containsAll(fourFiles))
    }

    // ---- other TVs, --force ----

    @Test fun theActivationOfAnotherTvIsNeverOverwrittenWithoutForceAndItsCodeIsNamed() {
        makeKey(); val vol = volume()
        assertEquals(0, emettre(vol, requestA).code)
        val before = UsbKey.ALL_FILES.associateWith { File(vol, it).readBytes() }
        val journalBefore = issued; val syncsBefore = syncs
        val r = emettre(vol, requestB, passphrase = "un-autre-code-assez-long")
        assertEquals(1, r.code, "un refus de règle, pas « code faux » (3) : la clé est regardée AVANT le code de déverrouillage")
        assertTrue(r.err.startsWith("Refusé") && r.err.contains(codeA) && r.err.contains("AUTRE TV") && r.err.contains("--force"), r.err)
        assertFalse(r.err.contains(codeB))
        for ((rel, bytes) in before) assertContentEquals(bytes, File(vol, rel).readBytes(), "inchangé : $rel")
        assertEquals(journalBefore, issued, "rien n'a été émis : aucun poste consommé, aucune ligne de journal")
        assertEquals(syncsBefore, syncs, "rien n'a été écrit")
    }

    @Test fun forceOverwritesTheActivationOfAnotherTvAndSaysSo() {
        makeKey(); val vol = volume()
        assertEquals(0, emettre(vol, requestA).code)
        val r = emettre(vol, requestB, "--force"); assertEquals(0, r.code, r.err)
        for (rel in UsbKey.ACTIVATION_FILES) assertEquals(codeB, codeOn(vol, rel), rel)
        assertTrue(r.out.contains("écrasé") && r.out.contains(codeA), r.out)
        assertTrue(r.out.contains("4 fichiers écrits sur ${vol.path} pour la TV $codeB"), r.out)
        assertEquals(fourFiles, filesOn(vol))
    }

    @Test fun anotherTvInASingleFileIsEnoughToRefuse() {
        makeKey(); val other = volume("AUTRE"); val vol = volume()
        assertEquals(0, emettre(other, requestA).code)
        File(vol, "Download/CastBridge").mkdirs()
        File(other, "Download/CastBridge/activation").copyTo(File(vol, "Download/CastBridge/activation"))       // TV A, in ONE of the three places only
        val r = emettre(vol, requestB); assertEquals(1, r.code); assertTrue(r.err.contains("Download/CastBridge/activation") && r.err.contains(codeA), r.err)
        assertEquals(setOf("Download/CastBridge/activation"), filesOn(vol), "rien d'autre n'a été écrit")
    }

    @Test fun whatIsNotAnActivationOrBlocksTheWayIsNotTouched() {
        makeKey()
        val garbage = volume("PERSO"); File(garbage, "activation").writeText("mon fichier perso\n")
        val r = emettre(garbage); assertEquals(1, r.code); assertTrue(r.err.contains("pas une activation CastBridge") && r.err.contains("--force"), r.err)
        assertEquals("mon fichier perso\n", File(garbage, "activation").readText()); assertEquals(0, issued)
        assertEquals(0, emettre(garbage, requestA, "--force").code, "avec --force, un fichier inconnu est écrasé")
        assertEquals(codeA, codeOn(garbage, "activation"))
        // a folder where the file goes, a file where a folder goes: refused, even with --force (the tool deletes nothing)
        val folder = volume("DOSSIER"); File(folder, "activation").mkdirs()
        val a = emettre(folder, requestA, "--force"); assertEquals(1, a.code); assertTrue(a.err.contains("« activation » est un dossier"), a.err)
        val file = volume("FICHIER"); File(file, "Download").writeText("je suis un fichier")
        val b = emettre(file, requestA, "--force"); assertEquals(1, b.code); assertTrue(b.err.contains("« Download » est un fichier"), b.err)
        assertEquals(1, issued, "seule la première émission (--force sur un fichier inconnu) a eu lieu")
    }

    // ---- the volume ----

    @Test fun aVolumeThatCannotBeWrittenIsRefusedBeforeAnythingIsIssued() {
        makeKey(); val vol = volume("VERROUILLEE")
        try {
            vol.setWritable(false)
            val still = runCatching { File(vol, ".essai").also { it.createNewFile() }.delete() }.getOrDefault(false)
            assumeFalse("exécuté en administrateur : un dossier en lecture seule reste inscriptible, le cas n'existe pas ici", still)
            val r = emettre(vol)
            assertEquals(1, r.code, r.out); assertTrue(r.err.startsWith("Refusé") && r.err.contains("n'est pas inscriptible"), r.err)
            assertTrue(r.err.contains("FAT32") && r.err.contains("verrou"), "le message dit quoi vérifier : ${r.err}")
            assertEquals(0, issued, "rien n'a été émis"); assertEquals(0, syncs)
            assertTrue(vol.listFiles()!!.isEmpty())
        } finally { vol.setWritable(true) }
    }

    @Test fun theRealRuleRefusesAFolderThatIsNotTheRootOfAMountedVolume() {
        makeKey(); val folder = volume("DOSSIER-ORDINAIRE")
        val r = emettre(folder, usb = UsbPlatform(sync = { syncs++ }))                  // default rule = the real one
        assertEquals(1, r.code, r.out); assertTrue(r.err.startsWith("Refusé") && r.err.contains("n'est pas la racine d'un volume"), r.err)
        if (!isWindows()) assertTrue(r.err.contains("/Volumes/NOM") && r.err.contains("E:"), "le message dit où se trouve une clé : ${r.err}")
        assertTrue(folder.listFiles()!!.isEmpty(), "pas même un fichier d'essai")
        assertEquals(0, issued); assertEquals(0, syncs)
    }

    @Test fun theSystemDiskIsNotAUsbKeyAndNothingIsWrittenToFindOut() {
        val root = if (isWindows()) File((System.getenv("SystemDrive") ?: "C:") + "\\") else File("/")
        val why = SystemVolumes.refusal(root)
        assertNotNull(why); assertTrue(why.contains("disque du système"), why)
        // a folder of that disk is refused as well
        assertTrue(SystemVolumes.refusal(dir)!!.contains("n'est pas la racine"), "un dossier temporaire n'est pas un volume monté")
    }

    @Test fun windowsDrivesAreJudgedOnTheirText() {
        assertNull(SystemVolumes.windowsRefusal("E:\\", "C:"))
        assertNull(SystemVolumes.windowsRefusal("e:\\", null))
        assertTrue(SystemVolumes.windowsRefusal("C:\\", "C:")!!.contains("disque du système"))
        assertTrue(SystemVolumes.windowsRefusal("c:\\", "C:")!!.contains("disque du système"))
        assertTrue(SystemVolumes.windowsRefusal("E:\\Download", "C:")!!.contains("n'est pas la racine d'un volume"))
        assertTrue(SystemVolumes.windowsRefusal("\\\\serveur\\partage", "C:")!!.contains("volume local"))
        assertTrue(SystemVolumes.windowsRefusal("/Volumes/CLE", "C:")!!.contains("volume local"))
    }

    @Test fun volumeArgumentsAreReadTheWayTheOwnerMeansThem() {
        assertEquals(File("E:\\"), UsbKey.volumeArg("E:", windows = true))
        assertEquals(File("E:\\"), UsbKey.volumeArg("E:\"", windows = true), "sous cmd.exe, \"E:\\\" arrive au programme sous la forme E:\"")
        assertEquals(File("/Volumes/MA CLE"), UsbKey.volumeArg(" /Volumes/MA CLE ", windows = false))
        assertEquals(File("E:"), UsbKey.volumeArg("E:", windows = false), "ailleurs que sous Windows, rien n'est réinterprété")
    }

    @Test fun aMissingVolumeOrAFileIsRefusedWithWhatToDo() {
        makeKey()
        val absent = emettre(File(dir, "volumes/absente")); assertEquals(1, absent.code); assertTrue(absent.err.contains("introuvable") && absent.err.contains("Branchez la clé"), absent.err)
        val file = File(dir, "pas-un-dossier.txt").also { it.writeText("x") }
        val notDir = emettre(file); assertEquals(1, notDir.code); assertTrue(notDir.err.contains("n'est pas un dossier"), notDir.err)
        assertEquals(0, issued)
    }

    // ---- names: FAT, exFAT, NTFS ----

    @Test fun everyNameWrittenIsValidOnFatExfatAndNtfs() {
        UsbKey.ALL_FILES.forEach(FatNames::checkPath)
        assertEquals(4, UsbKey.ALL_FILES.size)
        for (bad in listOf("a:b", "x?", "n*m", "q\"r", "p|q", "a<b", "a>b", "a\\b", "fin.", "fin ", "CON", "nul.txt", "COM1", "lpt9", "", ".", "..", "tab\tx", "a".repeat(256))) assertNotNull(FatNames.problem(bad), "refusé : « $bad »")
        for (good in listOf("activation", "Download", "CastBridge", "Android", "data", "castbridge.receiver", "files", "LISEZMOI-CASTBRIDGE.txt", "caractères accentués.txt", "COMPTE", "console")) assertNull(FatNames.problem(good), "accepté : « $good »")
        assertFailsWith<IllegalStateException> { FatNames.checkPath("Download/CastBridge/ac:tivation") }
        assertFailsWith<IllegalStateException> { FatNames.checkPath("Download//activation") }
    }

    // ---- the sub-command « cle-usb » ----

    @Test fun cleUsbWritesAnActivationThatWasAlreadyIssuedWithoutAnyUnlockCode() {
        makeKey()
        val act = issueLocal("usb")
        File(dir, "home").deleteRecursively()                                                           // no desk key at all: nothing is signed by « cle-usb »
        val vol = volume()
        val r = cli("cle-usb", "--activation", act.path, "--cle-usb", vol.path, passphrase = "n'importe quoi"); assertEquals(0, r.code, r.err)
        assertEquals(fourFiles, filesOn(vol))
        assertTrue(r.out.contains("4 fichiers écrits sur ${vol.path} pour la TV $codeA"), r.out)
        assertEquals(act.readText(), File(vol, "Download/CastBridge/activation").readText())
        assertEquals(1, syncs)
        // the folder written by « emettre --sortie » is accepted as well, and the standard input too
        val vol2 = volume("CLE2"); assertEquals(0, cli("cle-usb", "--activation", act.parentFile.path, "--cle-usb", vol2.path).code)
        assertEquals(fourFiles, filesOn(vol2))
        val vol3 = volume("CLE3")
        val env = Env(PrintStream(ByteArrayOutputStream(), true, "UTF-8"), PrintStream(ByteArrayOutputStream(), true, "UTF-8"), ByteArrayInputStream(act.readBytes()), clock = { now + 5000 }, usb = anyFolder)
        assertEquals(0, Cli(env).run(listOf("cle-usb", "--activation", "-", "--cle-usb", vol3.path)))
        assertEquals(fourFiles, filesOn(vol3))
        assertEquals(0, cli("cle-usb", "--activation", act.path, "--cle-usb", vol.path).code, "même TV : renouvelée sans --force")
    }

    @Test fun cleUsbRefusesAnotherTvLikeEmettreAndForceOverrides() {
        makeKey()
        val a = issueLocal("ua", requestA); val b = issueLocal("ub", requestB)
        val vol = volume()
        assertEquals(0, cli("cle-usb", "--activation", a.path, "--cle-usb", vol.path).code)
        val r = cli("cle-usb", "--activation", b.path, "--cle-usb", vol.path); assertEquals(1, r.code); assertTrue(r.err.contains(codeA) && r.err.contains("--force"), r.err)
        assertEquals(codeA, codeOn(vol, "activation"))
        assertEquals(0, cli("cle-usb", "--activation", b.path, "--cle-usb", vol.path, "--force").code)
        assertEquals(codeB, codeOn(vol, "activation"))
    }

    @Test fun cleUsbRefusesWhatTheTvWouldNotReadOrCouldNotInstall() {
        makeKey(); val vol = volume()
        val act = issueLocal("usb")
        // too late: a key can be installed during 48 h from its creation
        val late = cli("cle-usb", "--activation", act.path, "--cle-usb", vol.path, clock = { now + 49L * 3600 * 1000 }); assertEquals(1, late.code); assertTrue(late.err.contains("n'est plus installable"), late.err)
        // not an activation, empty, too big, missing
        val junk = File(dir, "bonjour.txt").also { it.writeText("bonjour\n") }
        val j = cli("cle-usb", "--activation", junk.path, "--cle-usb", vol.path); assertEquals(1, j.code); assertTrue(j.err.contains("pas d'activation CastBridge"), j.err)
        val empty = File(dir, "vide.txt").also { it.writeText("\n\n") }
        assertTrue(cli("cle-usb", "--activation", empty.path, "--cle-usb", vol.path).err.contains("vide"))
        val big = File(dir, "gros.txt").also { it.writeText("x".repeat(20_000)) }
        assertTrue(cli("cle-usb", "--activation", big.path, "--cle-usb", vol.path).err.contains("16 Kio"))
        val missing = cli("cle-usb", "--activation", File(dir, "absent").path, "--cle-usb", vol.path); assertEquals(2, missing.code); assertTrue(missing.err.contains("introuvable"), missing.err)
        assertEquals(2, cli("cle-usb", "--activation", act.path).code, "--cle-usb est obligatoire")
        assertEquals(2, cli("cle-usb", "--cle-usb", vol.path).code, "--activation est obligatoire")
        assertTrue(vol.listFiles()!!.isEmpty(), "rien n'a été écrit"); assertEquals(0, syncs)
    }

    @Test fun anActivationOfAPhoneHasNoPlaceOnTheKeyOfATv() {
        makeKey(); val vol = volume()
        val r = emettre(vol, requestA, "--sujet", "phone"); assertEquals(2, r.code, r.out); assertTrue(r.err.contains("téléphone"), r.err)
        assertEquals(0, issued); assertTrue(vol.listFiles()!!.isEmpty())
        // a phone activation that already exists is refused by « cle-usb » as well
        val phone = issueLocal("phone", requestA, "--sujet", "phone")
        assertEquals(Subject.PHONE, Activation.decode(phone.readText().trim())!!.subject)
        val q = cli("cle-usb", "--activation", phone.path, "--cle-usb", vol.path); assertEquals(1, q.code); assertTrue(q.err.contains("téléphone"), q.err)
        assertTrue(vol.listFiles()!!.isEmpty())
    }

    // ---- failures of the media ----

    private class Damage(val name: String, val needsForce: Boolean, val apply: (File) -> Unit)

    /**
     * A key that does not hold what was written (a worn key, pulled too early: a cut file, a file of zeros): never a success, and the message says how to finish without issuing again. What this tool
     * itself leaves (cut or empty) is replaced by that command; a foreign text needs --force.
     */
    @Test fun aKeyThatDoesNotReadBackIdenticalIsAFailureAndTheMessageSaysHowToFinish() {
        makeKey()
        val damages = listOf(
            Damage("jeton coupé (taille différente)", false) { f -> f.writeText(f.readText().take(200)) },
            Damage("même taille, que des zéros", false) { f -> f.writeBytes(ByteArray(f.length().toInt())) },
            Damage("texte étranger", true) { f -> f.writeText("abîmé") },
        )
        for ((i, d) in damages.withIndex()) {
            val vol = volume("MA CLE USEE $i")                                                     // a volume name with spaces, as a Mac or a Windows key often has
            val flaky = UsbPlatform(volumeRule = VolumeRule { null }, sync = { d.apply(File(vol, "activation")) })
            val before = issued
            val r = emettre(vol, usb = flaky)
            assertEquals(1, r.code, d.name); assertTrue(r.err.startsWith("Échec") && r.err.contains("Vérification échouée"), "${d.name} : ${r.err}")
            assertTrue(r.err.contains("--cle-usb \"${vol.path}\""), "la commande de reprise se copie telle quelle : ${r.err}")
            assertFalse(r.out.contains("fichiers écrits sur"), "${d.name} : jamais d'annonce de succès")
            assertEquals(before + 1, issued, "l'activation, elle, est émise : le journal la compte")
            val copy = recoveryFile(r.err); assertTrue(copy.isFile, copy.path)
            if (d.needsForce) {
                val no = cli("cle-usb", "--activation", copy.path, "--cle-usb", vol.path); assertEquals(1, no.code, d.name); assertTrue(no.err.contains("--force"), no.err)
            }
            val fixed = cli("cle-usb", "--activation", copy.path, "--cle-usb", vol.path, *(if (d.needsForce) arrayOf("--force") else emptyArray())); assertEquals(0, fixed.code, "${d.name} : ${fixed.err}")
            assertEquals(codeA, codeOn(vol, "activation")); assertEquals(fourFiles, filesOn(vol))
        }
    }

    /** What an interrupted delivery leaves is replaced like nothing; what a person wrote, or an intact message of CastBridge that is not an activation, is protected. */
    @Test fun whatAnInterruptedDeliveryLeavesIsReplacedButAPersonalFileOrAnotherMessageIsNot() {
        makeKey()
        val token = issueLocal("usb").readText().trim(); val base = issued
        val command = castbridge.core.owner.Envelope("command", "0123456789abcdef", 1, "00112233", 1, 1, 2, castbridge.core.owner.Envelope.Target.Any, listOf("x"), "sig").encode()
        assertNotNull(castbridge.core.owner.Envelope.decode(command), "le message d'un autre type est un jeton intact")
        val leftovers = listOf("vide" to ByteArray(0), "blancs" to " \r\n\t\n".toByteArray(), "zéros" to ByteArray(300), "jeton coupé" to token.take(120).toByteArray())
        for ((i, l) in leftovers.withIndex()) {
            val vol = volume("RESTE-$i"); File(vol, "activation").writeBytes(l.second)
            val r = emettre(vol); assertEquals(0, r.code, "${l.first} : ${r.err}")
            assertFalse(r.out.contains("ATTENTION"), "${l.first} : ${r.out}")
            assertEquals(codeA, codeOn(vol, "activation"), l.first)
        }
        val protected = listOf("texte" to "mon fichier\n".toByteArray(), "message d'un autre type" to (command + "\n").toByteArray(), "trop gros pour une clé" to ByteArray(UsbKey.MAX_BYTES + 5) { 'a'.code.toByte() })
        for ((i, p) in protected.withIndex()) {
            val vol = volume("PROTEGE-$i"); File(vol, "activation").writeBytes(p.second)
            val r = emettre(vol); assertEquals(1, r.code, p.first); assertTrue(r.err.contains("--force"), "${p.first} : ${r.err}")
            assertContentEquals(p.second, File(vol, "activation").readBytes(), "${p.first} : inchangé")
        }
        assertEquals(base + leftovers.size, issued, "seules les émissions sur des restes ont eu lieu")
    }

    /** A folder that refuses us half-way: the failure says how many files are already on the key, and « cle-usb » finishes the job without issuing again. */
    @Test fun aWriteThatFailsHalfWayIsAFailureThatCountsTheFilesAlreadyOnTheKey() {
        makeKey(); val vol = volume()
        val shared = File(vol, "Download").also { it.mkdirs() }
        try {
            shared.setWritable(false)
            val still = runCatching { File(shared, ".essai").also { it.createNewFile() }.delete() }.getOrDefault(false)
            assumeFalse("exécuté en administrateur : un dossier en lecture seule reste inscriptible, le cas n'existe pas ici", still)
            val r = emettre(vol)
            assertEquals(1, r.code, r.out)
            assertTrue(r.err.startsWith("Échec") && r.err.contains("Écriture impossible") && r.err.contains("Download/CastBridge/activation") && r.err.contains("accès refusé"), r.err)
            assertTrue(r.err.contains("1 fichier(s) sur 4"), "le dossier de l'application est écrit en premier : ${r.err}")
            assertEquals(1, issued); assertTrue(File(vol, UsbKey.OWN_FILE).isFile)
            shared.setWritable(true)
            val fixed = cli("cle-usb", "--activation", recoveryFile(r.err).path, "--cle-usb", vol.path); assertEquals(0, fixed.code, fixed.err)
            assertEquals(fourFiles, filesOn(vol))
        } finally { shared.setWritable(true) }
    }

    /** The key is looked at again just before writing: what appeared since the first look is refused, nothing is written. */
    @Test fun theKeyIsLookedAtAgainJustBeforeWriting() {
        makeKey(); val vol = volume()
        val parsed = UsbKey.parse(issueLocal("usb").readBytes())
        val key = UsbKey.open(vol, anyFolder)
        File(vol, "Download").writeText("je suis un fichier, pas un dossier")                  // appears after the volume was opened
        val e = assertFailsWith<UsbKeyException> { key.write(parsed, force = false) }
        assertTrue(e.refusal && e.message!!.contains("« Download » est un fichier"), e.message)
        assertEquals(setOf("Download"), filesOn(vol), "rien n'a été écrit"); assertEquals(0, syncs)
    }

    // ---- the activation text ----

    @Test fun theFirstLineIsReadLikeTheTvReadsIt() {
        makeKey()
        val token = issueLocal("usb").readText().trim()
        val p = UsbKey.parse(("\uFEFF\r\n$token\r\nune autre ligne\r\n").toByteArray(Charsets.UTF_8))
        assertEquals(token, p.line); assertEquals(token + "\n", p.content); assertEquals(codeA, p.deviceCode)
        assertEquals(Subject.TV, p.activation.subject)
        assertFailsWith<UsbKeyException> { UsbKey.parse(ByteArray(0)) }
        assertFailsWith<UsbKeyException> { UsbKey.parse("cbx1.abc.def\n".toByteArray()) }
        assertFailsWith<UsbKeyException> { UsbKey.parse(ByteArray(UsbKey.MAX_BYTES + 1) { 'a'.code.toByte() }) }
        assertEquals(16 * 1024, UsbKey.MAX_BYTES, "la limite de la TV")
    }
}
