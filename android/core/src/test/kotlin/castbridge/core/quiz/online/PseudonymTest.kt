package castbridge.core.quiz.online

import kotlin.test.*

class PseudonymTest {
    private fun refused(raw: String?, reason: Pseudonym.Reason) {
        val r = Pseudonym.check(raw)
        assertTrue(r is Pseudonym.Result.Refused, "« $raw » aurait dû être refusé : $r")
        assertEquals(reason, (r as Pseudonym.Result.Refused).reason, "motif de « $raw »")
    }
    private fun ok(raw: String, shown: String = raw) = assertEquals(Pseudonym.Result.Ok(shown), Pseudonym.check(raw), "« $raw » aurait dû être accepté")

    @Test fun acceptanceCases() {
        refused("Admin CastBridge", Pseudonym.Reason.IMPERSONATION)
        refused("+237 6 99 00 11 22", Pseudonym.Reason.PHONE)
        refused("http://x", Pseudonym.Reason.URL)
        ok("Amina N.")
    }

    @Test fun ordinaryNamesPass() {
        listOf("Awa", "Jean-Pierre", "O'Neil", "Aïcha", "Dominique", "Monique", "Nazir", "Shittu", "Constance", "Modou", "Ndolo", "Joueur 7", "Élodie", "Amina2010", "Kévin").forEach { ok(it) }
        ok("  Awa   Bello  ", "Awa Bello")
        ok("D’Artagnan", "D'Artagnan")
    }

    @Test fun lengthAndEmptyAndAlphabet() {
        refused(null, Pseudonym.Reason.EMPTY); refused("   ", Pseudonym.Reason.EMPTY)
        refused("A", Pseudonym.Reason.LENGTH); refused("A".repeat(17), Pseudonym.Reason.LENGTH); ok("A".repeat(16)); ok("Al")
        refused("Awa<b>", Pseudonym.Reason.CHARS); refused("Awa😀", Pseudonym.Reason.CHARS); refused("Awa_B", Pseudonym.Reason.CHARS); refused("Awa\u0007", Pseudonym.Reason.CHARS)
    }

    @Test fun urlsEmailsAndDomains() {
        listOf("www.x", "bit.ly/ab", "awa@x.com", "jeu.com", "x.cm", "https://a", "Awa .fr").forEach { refused(it, Pseudonym.Reason.URL) }
    }

    @Test fun phoneNumbersCountDigitsNotSeparators() {
        refused("6 99 00 11 22", Pseudonym.Reason.PHONE); refused("699001122", Pseudonym.Reason.PHONE); refused("Awa-6990011", Pseudonym.Reason.PHONE)
        ok("Awa123456"); refused("Awa1234567", Pseudonym.Reason.PHONE)
    }

    @Test fun impersonation() {
        listOf("CastBridge", "Cast Bridge", "castbridge-tv", "C4stBr1dge", "Admin", "ADMIN", "Modérateur", "M0d3rat3ur", "Support", "Administrateur", "Officiel")
            .forEach { refused(it, Pseudonym.Reason.IMPERSONATION) }
        ok("Profil Awa"); ok("Prof"); ok("Prof Awa")
    }

    @Test fun blocklistWithLeetHomoglyphsAndRepeats() {
        listOf("merde", "M3rd3", "Fuuuck", "f u c k", "putain", "Pu7a1n", "bitch", "b1tch", "sh1thead", "salope", "N1que", "ashawo",
            "ｆｕｃｋ" /* pleine chasse (NFKC) */, "Mumu", "Hitler", "N.a.z.i.s")
            .forEach { refused(it, Pseudonym.Reason.BLOCKED) }
    }

    @Test fun symbolsUsedAsLeetAreRefusedAsCharacters() {
        refused("S@lope", Pseudonym.Reason.CHARS); refused("sh!t", Pseudonym.Reason.CHARS)
    }

    @Test fun shortWordsAreWholeWordOnly() {
        refused("Con", Pseudonym.Reason.BLOCKED); refused("Sale con", Pseudonym.Reason.BLOCKED)
        ok("Constance"); ok("Conan"); ok("Pudding"); ok("Cullen"); ok("Sexton"); ok("Pedro"); ok("Bitel")
    }

    @Test fun refusalTextNeverRepeatsTheWord() {
        val r = Pseudonym.check("merde") as Pseudonym.Result.Refused
        assertFalse("merde" in r.text.lowercase()); assertTrue(r.text.startsWith("Pseudonyme refusé"))
    }

    @Test fun blocklistResourceIsPresentAndLoaded() {
        val txt = Pseudonym::class.java.getResourceAsStream("/castbridge/quiz/online/blocklist.txt")!!.bufferedReader().readText()
        assertTrue(txt.lines().count { it.isNotBlank() && !it.startsWith("#") } >= 50)
    }
}
