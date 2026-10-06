package castbridge.core.tv.activation

import castbridge.core.lots.Right
import castbridge.core.owner.*
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** « Chercher la clé dans le fichier » (docs/TV-ACTIVATION-CLE-USB.md): the key may be anywhere in the chosen text file, not only on its first line. */
class KeyScanTest {
    private val grouped = "ABCDE-FGHJK-MNPQR-STVWX-YZ012"                       // shape of a grouped text / compact key (5 groups of 5)

    @Test fun `a token anywhere in the text is a candidate, in reading order`() {
        val text = "Bonjour,\nvoici votre clé :\n\n   cbx1.AAAA.BBBB==  \nMerci\ncbx1.CCCC.DDDD\n"
        assertEquals(listOf("cbx1.AAAA.BBBB==", "cbx1.CCCC.DDDD"), KeyScan.candidates(text))
    }

    @Test fun `a token inside a sentence or quotes is cut out of it`() {
        assertEquals(listOf("cbx1.AAAA.BB+/=="), KeyScan.candidates("Clé : « cbx1.AAAA.BB+/== »."))
        assertEquals(listOf("cbx1.X.Y"), KeyScan.candidates("(cbx1.X.Y), merci"))
        assertEquals(listOf("cbx1.X.Y"), KeyScan.candidates("\"cbx1.X.Y\";"))
    }

    @Test fun `a grouped key line is a candidate, with or without a label`() {
        assertEquals(listOf(grouped), KeyScan.candidates("Bonjour\n$grouped\n"))
        assertEquals(listOf(grouped), KeyScan.candidates("Clé : $grouped"))
        assertEquals(listOf("ABCDE FGHJK MNPQR STVWX YZ012"), KeyScan.candidates("ABCDE FGHJK MNPQR STVWX YZ012"))
    }

    @Test fun `prose and short codes are never candidates`() {
        for (t in listOf("", "\n\n", "Bonjour, voici la clé", "ABCDE-FGHJK", "1234-5678-9012-3456", "code=ABCD-EFGH-JKMN-PQRS", "cbx2.AAAA.BBBB", "xcbx1.AAAA.BBBB", "cbx1.", "cbx1.AAAA", "cbx1.A B", "Merci votre carte reste ferme demain", "A1CDE-FGHJK-MNPQR-STVWX", "A1CDE FGHJK MNPQR STVWX"))
            assertEquals(emptyList(), KeyScan.candidates(t), t)
    }

    @Test fun `a grouped key wrapped over several lines is also tried joined`() {
        val text = "Votre clé :\nABCDE-FGHJK-MNPQR-\nSTVWX-YZ012-34567\nFin"
        val c = KeyScan.candidates(text)
        assertTrue("ABCDE-FGHJK-MNPQR-STVWX-YZ012-34567" in c, c.toString())
        // the joined block comes after its own lines (reading order of the first line)
        assertEquals("ABCDE-FGHJK-MNPQR-STVWX-YZ012-34567", c.last())
    }

    @Test fun `duplicates are tried once and the number of candidates is capped`() {
        assertEquals(listOf("cbx1.A.B"), KeyScan.candidates("cbx1.A.B\ncbx1.A.B\n cbx1.A.B"))
        val many = (1..500).joinToString("\n") { "cbx1.K$it.S" }
        val c = KeyScan.candidates(many)
        assertEquals(KeyScan.MAX_CANDIDATES, c.size)
        assertEquals("cbx1.K1.S", c.first())
    }

    @Test fun `bom and crlf are tolerated`() {
        assertEquals(listOf("cbx1.A.B"), KeyScan.candidates("﻿cbx1.A.B\r\nsuite\r\n"))
    }

    @Test fun `the first key valid for this TV wins even after refused ones`() {
        val seen = ArrayList<String>()
        val o = KeyScan.pick(listOf("a", "b", "c", "d")) { seen += it; when (it) { "a" -> Verdict.NOT_VALID; "b" -> Verdict.WRONG_DEVICE; "c" -> Verdict.ACCEPTED; else -> error("not tried after an accepted one") } }
        assertEquals(Verdict.ACCEPTED, o.verdict); assertEquals(2, o.index); assertEquals(listOf("a", "b", "c"), seen)
    }

    @Test fun `without a valid key the most useful refusal is reported`() {
        val o = KeyScan.pick(listOf("a", "b", "c")) { when (it) { "a" -> Verdict.NOT_VALID; "b" -> Verdict.EXPIRED; else -> Verdict.WRONG_DEVICE } }
        assertEquals(Verdict.WRONG_DEVICE, o.verdict); assertEquals(2, o.index); assertEquals(3, o.tried)
        val e = KeyScan.pick(listOf("a", "b", "c")) { when (it) { "a" -> Verdict.NOT_VALID; "b" -> Verdict.EXPIRED; else -> Verdict.EXPIRED } }
        assertEquals(Verdict.EXPIRED, e.verdict); assertEquals(1, e.index, "the FIRST of the best refusals")
        val n = KeyScan.pick(listOf("a", "b")) { Verdict.NOT_VALID }
        assertEquals(Verdict.NOT_VALID, n.verdict); assertEquals(0, n.index)
    }

    @Test fun `no candidate at all is said in plain French`() {
        val o = KeyScan.pick(emptyList()) { error("never called") }
        assertNull(o.verdict); assertEquals(0, o.tried)
        assertTrue("Aucune clé d'activation" in KeyScan.summary(o)!!)
        assertTrue("2 clés" in KeyScan.summary(KeyScan.Outcome(Verdict.WRONG_DEVICE, 1, 2))!!)
        assertNull(KeyScan.summary(KeyScan.Outcome(Verdict.WRONG_DEVICE, 0, 1)), "one key: the verifier's own message is enough")
        assertNull(KeyScan.summary(KeyScan.Outcome(Verdict.ACCEPTED, 3, 4)))
    }

    // ---- end to end with the real verifier (the same path as a pasted key) ----
    private fun seed(n: String) = MessageDigest.getInstance("SHA-256").digest("keyscan-test|$n".toByteArray())
    private val desk = Ed25519Signer(seed("desk"))
    private val ring = KeyRing(listOf(desk.trusted()))
    private fun tv(name: String) = DeviceIdentity.fingerprints(RawFactors("FLASH-$name", "cid-$name", "AA:BB:CC:00:11:22", "10:20:30:40:50:60",
        "/sys/devices/platform/soc/x.sd/mmc_host/mmc1/net/wlan0", "SYS-$name", "11:22:33:44:55:66"))
    private val now = 1_800_000_000_000L
    private fun token(fp: Fingerprints) = ActivationIssuer(desk, KeyScope.ALL).issue(ActivationIssuer.Request(ActivationKind.PRODUCTION, DeviceCode.of(fp), fp, now, Subject.TV,
        listOf(Right.Purchase("p", listOf("classe-cm2"), now - 1)), "lic-1", null, now - 3_600_000L, 48, "00".repeat(12))).activation.encode()

    @Test fun `a real key in the middle of a mail is found and a key of another TV before it does not hide it`() {
        val mine = tv("MINE"); val other = tv("OTHER")
        val text = "Bonjour,\nAncienne clé (autre TV) : ${token(other)}\n\nVoici la clé de votre TV :\n${token(mine)}\n-- \nCastBridge\n"
        val receiver = ActivationReceiver(ring, listOf(desk.trusted()), mine, Subject.TV)
        val results = ArrayList<ActivationResult>()
        val o = KeyScan.pick(KeyScan.candidates(text)) { c ->
            val r = receiver.receive(Channel.MANUAL, c.toByteArray(), now).also { results += it }
            if (r is ActivationResult.Accepted) Verdict.ACCEPTED else if ((r as ActivationResult.Rejected).reason == Rejection.WRONG_DEVICE) Verdict.WRONG_DEVICE else Verdict.NOT_VALID
        }
        assertEquals(Verdict.ACCEPTED, o.verdict); assertEquals(2, o.tried)
        assertIs<ActivationResult.Accepted>(results.last())
        assertEquals(Rejection.WRONG_DEVICE, assertIs<ActivationResult.Rejected>(results.first()).reason)
    }
}
