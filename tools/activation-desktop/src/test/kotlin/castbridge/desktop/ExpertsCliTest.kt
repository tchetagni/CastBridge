package castbridge.desktop

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** « experts-* »: throwaway desk key (fast KDF), throwaway SSH public keys. */
class ExpertsCliTest {
    private val now = 1_800_000_000_000L
    private val pass = "un-code-de-test-long"
    private val dir = Files.createTempDirectory("activation-desktop-experts").toFile().also { it.deleteOnExit() }
    private val k1 = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8g alice@laptop"
    private val k2 = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAICEiIyQlJicoKSorLC0uLzAxMjM0NTY3ODk6Ozw9Pj9A"
    private class Run(val code: Int, val out: String, val err: String)

    private fun cli(vararg args: String, scopes: Boolean = true): Run {
        val o = ByteArrayOutputStream(); val e = ByteArrayOutputStream()
        val env = Env(PrintStream(o, true, "UTF-8"), PrintStream(e, true, "UTF-8"), getenv = { if (it == "CODE_TEST") pass else null }, clock = { now }, kdf = ScryptKdf(16, 1, 1))
        val code = Cli(env).run(args.toList() + listOf("--dossier", File(dir, "home").path, "--code-env", "CODE_TEST"))
        return Run(code, o.toString("UTF-8"), e.toString("UTF-8"))
    }

    @Test fun addListRemoveSignVerify() {
        assertEquals(0, cli("cle-creer").code)
        assertEquals(0, cli("experts-ajouter", "alice", "--cle-ssh", k1, "--jusqu-au", "2027-12-31").code)
        assertEquals(0, cli("experts-ajouter", "bob", "--cle-ssh", k2).code)
        val l = cli("experts-lister"); assertTrue(l.out.contains("alice") && l.out.contains("2027-12-31") && l.out.contains("sans date de fin"), l.out)
        assertEquals(1, cli("experts-ajouter", "alice", "--cle-ssh", k2).code)                           // duplicate id
        assertEquals(1, cli("experts-ajouter", "carl", "--cle-ssh", k1).code)                            // same key
        assertEquals(1, cli("experts-ajouter", "dan", "--cle-ssh", "ssh-rsa AAAAB3NzaC1yc2E").code)
        assertEquals(1, cli("experts-ajouter", "Bad_Id", "--cle-ssh", k2).code)
        val out = File(dir, "pub/experts.json")
        val s = cli("experts-signer", "--sortie", out.path); assertEquals(0, s.code, s.err)
        val v = cli("experts-verifier", out.path); assertEquals(0, v.code, v.err); assertTrue(v.out.contains("2 expert(s), 2 actif(s)"), v.out)
        val json = out.readText()
        assertTrue(json.contains("\"experts\"") && json.contains("\"signature\""))
        File(dir, "tampered.json").writeText(json.replace("alice", "mallo"))
        val t = cli("experts-verifier", File(dir, "tampered.json").path); assertEquals(1, t.code); assertTrue(t.err.contains("Signature"), t.err)
        assertEquals(0, cli("experts-retirer", "alice").code)
        assertEquals(1, cli("experts-retirer", "alice").code)
        assertEquals(0, cli("experts-signer", "--sortie", out.path).code)
        assertFalse(out.readText().contains("alice")); assertTrue(cli("experts-verifier", out.path).out.contains("1 expert(s)"))
        val journal = File(dir, "home/journal.jsonl").readText()
        assertTrue(journal.contains("experts-ajouter") && journal.contains("experts-signer") && !journal.contains("AAAAC3Nza"), journal)
        assertEquals(0, cli("journal").code)
    }

    @Test fun keyWithoutRegistryScopeCannotSign() {
        assertEquals(0, cli("cle-creer", "--portees", "ISSUE_TRIAL,COMMAND_SUPPORT").code)
        assertEquals(0, cli("experts-ajouter", "bob", "--cle-ssh", k2).code)
        val r = cli("experts-signer", "--sortie", File(dir, "x.json").path)
        assertEquals(1, r.code); assertTrue(r.err.contains("REGISTRY"), r.err); assertFalse(File(dir, "x.json").exists())
    }
}
