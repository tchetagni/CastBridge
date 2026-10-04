package castbridge.core.wallet.millions

import castbridge.core.quiz.Json
import castbridge.core.wallet.TestMint
import castbridge.core.wallet.Verdict
import castbridge.core.wallet.WalletRefusal
import castbridge.core.wallet.WalletTestKeys
import castbridge.core.owner.KeyRing
import castbridge.core.owner.TrustedKey
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import castbridge.core.wallet.millions.MillionsJournal.End
import castbridge.core.wallet.millions.MillionsJournal.Kind

class MillionsJournalTest {
    private val pack = MK.pack()
    private fun wonJournal(): MillionsJournal { val g = MK.game(); g.start(); MK.correct(g, 1, 15); return g.toJournal(MK.install.keyId) }
    private fun refused(v: Verdict<MillionsJournal>) = (v as? Verdict.Rejected)?.reason
    private fun verify(t: String?) = MillionsJournal.verify(t, MK.installRing)
    private fun why(j: MillionsJournal, ctx: MillionsJournal.Context = MillionsJournal.Context()) = MillionsJournal.impossible(j, pack, ctx)

    // ---------- format ----------

    @Test fun roundTrip() {
        val j = wonJournal()
        val token = MillionsJournal.sign(j, MK.install)
        assertTrue(token.startsWith("cbm1."))
        assertEquals(j, (verify(token) as Verdict.Accepted).value)
    }

    @Test fun signsWithItsOwnDomainNeverTheTvProofOnes() {
        val j = wonJournal()
        assertEquals("castbridge-millions-journal-v1", MillionsJournal.DOMAIN)
        for (d in listOf("castbridge-wallet-snapshot-v1", "castbridge-play-result-v1", "", "castbridge-tv-proof-v1"))
            assertEquals(WalletRefusal.BAD_SIGNATURE, refused(verify(TestMint.token(MillionsJournal.PREFIX, d, j.payload(), MK.installEd))), d)
    }

    @Test fun tvCannotSignForAnotherKey() {
        val j = wonJournal()
        assertFailsWith<IllegalArgumentException> { MillionsJournal.sign(j, MK.stranger) }
        val foreign = MillionsJournal.sign(j.copy(kid = MK.stranger.keyId), MK.stranger)
        assertEquals(WalletRefusal.UNKNOWN_KEY, refused(verify(foreign)))
    }

    @Test fun revokedInstallKeyRefused() {
        val ring = KeyRing(listOf(TrustedKey(MK.install.keyId, MK.install.publicKeyBase64)), revoked = setOf(MK.install.keyId))
        assertEquals(WalletRefusal.REVOKED_KEY, refused(MillionsJournal.verify(MillionsJournal.sign(wonJournal(), MK.install), ring)))
    }

    @Test fun tamperedPayloadRefused() {
        val t = MillionsJournal.sign(wonJournal(), MK.install).split('.')
        val text = String(Base64.getUrlDecoder().decode(t[1])).replace("\"gain\":10000", "\"gain\":20000")
        assertEquals(WalletRefusal.BAD_SIGNATURE, refused(verify("${t[0]}.${Base64.getUrlEncoder().withoutPadding().encodeToString(text.toByteArray())}.${t[2]}")))
    }

    private fun raw(text: String) = TestMint.raw(MillionsJournal.PREFIX, MillionsJournal.DOMAIN, text, MK.installEd)
    private fun compact(j: MillionsJournal = wonJournal()) = Json.write(j.payload())

    @Test fun strictParsing() {
        val c = compact()
        assertEquals(WalletRefusal.UNREADABLE, refused(verify(raw(c.dropLast(1) + ",\"extra\":1}"))))
        assertEquals(WalletRefusal.UNREADABLE, refused(verify(raw(c.replace(",", ", ")))))
        assertEquals(WalletRefusal.UNREADABLE, refused(verify(raw(c.dropLast(1) + ",\"gain\":1}"))))
        assertEquals(WalletRefusal.UNREADABLE, refused(verify(raw(c.replace("\"lv\":1", "\"lv\":1.0")))))
        assertEquals(WalletRefusal.UNREADABLE, refused(verify(raw(c.replace("\"end\":\"WON\"", "\"end\":\"WIN\"")))))
        assertEquals(WalletRefusal.UNREADABLE, refused(verify(raw(c.replace("\"gameId\":\"" + MK.GAME_ID, "\"gameId\":\"" + MK.GAME_ID.uppercase())))))
        assertEquals(WalletRefusal.UNREADABLE, refused(verify("cbw1.x.y")))
        assertEquals(WalletRefusal.UNREADABLE, refused(verify(null)))
        assertEquals(WalletRefusal.UNREADABLE, refused(verify("x".repeat(MillionsJournal.MAX_LENGTH + 1))))
    }

    @Test fun hashChainIsChecked() {
        val c = compact()
        val at = c.indexOf("\"entries\"")
        val first = Regex("\"[0-9a-f]{16}\"").find(c, at)!!.value                                 // empreinte de la 1re entrée
        val forged = c.substring(0, at) + c.substring(at).replaceFirst(first, "\"0000000000000000\"")
        assertEquals(WalletRefusal.UNREADABLE, refused(verify(raw(forged))))
        // supprimer une entrée en gardant les autres empreintes : refusé
        val j = wonJournal()
        val shorter = j.copy(entries = j.entries.drop(1))
        val p = shorter.payload().toMutableMap().also { it["entries"] = (j.payload()["entries"] as List<*>).drop(1) }
        assertEquals(WalletRefusal.UNREADABLE, refused(verify(raw(Json.write(p)))))
        // permuter deux entrées
        val rows = (j.payload()["entries"] as List<*>).toMutableList().also { val a = it[0]; it[0] = it[1]; it[1] = a }
        assertEquals(WalletRefusal.UNREADABLE, refused(verify(raw(Json.write(j.payload().toMutableMap().also { it["entries"] = rows })))))
    }

    @Test fun chainDependsOnGamePackAndLadder() {
        val e = MK.correctEntries(2)
        val base = MillionsJournal.chain(MK.GAME_ID, MK.PACK_ID, 1, e)
        assertEquals(2, base.size)
        assertTrue(base != MillionsJournal.chain("0".repeat(32), MK.PACK_ID, 1, e))
        assertTrue(base != MillionsJournal.chain(MK.GAME_ID, "0".repeat(32), 1, e))
        assertTrue(base != MillionsJournal.chain(MK.GAME_ID, MK.PACK_ID, 2, e))
        assertTrue(base != MillionsJournal.chain(MK.GAME_ID, MK.PACK_ID, 1, e.map { it.copy(ms = it.ms + 1) }))
        assertTrue(base != MillionsJournal.chain(MK.GAME_ID, MK.PACK_ID, 1, e.map { it.copy(fifty = true) }))
        assertTrue(base[1] != MillionsJournal.chain(MK.GAME_ID, MK.PACK_ID, 1, listOf(e[0].copy(choice = 3), e[1]))[1], "une entrée change toutes les suivantes")
    }

    @Test fun boundsAndSize() {
        val token = MillionsJournal.sign(wonJournal(), MK.install)
        assertTrue(token.length <= MillionsJournal.MAX_LENGTH && token.length < 3_000, "${token.length}")
        val j = wonJournal()
        fun oob(m: MillionsJournal) = assertEquals(WalletRefusal.OUT_OF_BOUNDS, refused(verify(TestMint.token(MillionsJournal.PREFIX, MillionsJournal.DOMAIN, m.payload(), MK.installEd))))
        oob(j.copy(entries = j.entries + MK.entry(15)))                                       // 16 entrées
        oob(j.copy(entries = listOf(j.entries[0].copy(ms = 3_600_001)) + j.entries.drop(1)))
        oob(j.copy(entries = listOf(j.entries[0].copy(choice = 4)) + j.entries.drop(1)))
        oob(j.copy(gain = -1)); oob(j.copy(t0 = -1))
    }

    @Test fun carriesNoPersonalDataNorQuestionText() {
        val token = MillionsJournal.sign(wonJournal(), MK.install)
        val text = String(Base64.getUrlDecoder().decode(token.split('.')[1]))
        assertFalse(text.contains("Question")); assertFalse(text.contains("\"A\""))
        assertEquals(setOf("kid", "gameId", "packId", "lv", "entries", "end", "gain", "t0", "t1", "head"), Json.obj(text).keys)
    }

    @Test fun endTextForms() {
        assertEquals("WRONG@3", End(Kind.WRONG, 3).text); assertEquals("WON", End(Kind.WON, 0).text); assertEquals(End(Kind.TIMEOUT, 7), End.parse("TIMEOUT@7"))
        for (bad in listOf("WRONG", "WRONG@0", "WRONG@16", "WON@1", "WITHDRAW@x", "WRONG@03", "wrong@3", "")) assertNull(End.parse(bad), bad)
    }

    // ---------- journaux impossibles : un motif chacun, dans l'ordre documenté ----------

    @Test fun honestJournalsAreNotImpossible() {
        assertNull(why(wonJournal()))
        assertNull(why(MK.journal(MK.correctEntries(8), End(Kind.WITHDRAW, 8), 1200)))
        assertNull(why(MK.journal(MK.correctEntries(5), End(Kind.WITHDRAW, 5), 500)))
        assertNull(why(MK.journal(MK.correctEntries(2) + MK.entry(3, ok = false), End(Kind.WRONG, 3), 0)))
        assertNull(why(MK.journal(MK.correctEntries(1) + MK.entry(2, ms = 30_001), End(Kind.TIMEOUT, 2), 0)))
        assertNull(why(MK.journal(MK.correctEntries(3), End(Kind.FORFEIT, 4), 0)))
        assertNull(why(MK.journal(emptyList(), End(Kind.FORFEIT, 1), 0)))
        assertNull(why(MK.journal(listOf(MK.entry(1, ms = 300)), End(Kind.FORFEIT, 2), 0)), "300 ms est permis")
    }

    @Test fun packMismatch() {
        assertEquals(MillionsImpossible.PACK_MISMATCH, why(MK.journal(MK.correctEntries(8), End(Kind.WITHDRAW, 8), 1200, packId = "f".repeat(32))))
        assertEquals(MillionsImpossible.PACK_MISMATCH, why(MK.journal(MK.correctEntries(8), End(Kind.WITHDRAW, 8), 1200, lv = 2)))
    }

    @Test fun badTimes() { assertEquals(MillionsImpossible.BAD_TIMES, why(MK.journal(MK.correctEntries(5), End(Kind.WITHDRAW, 5), 500, t0 = 10, t1 = 9))) }

    @Test fun questionNotInPack() {
        val e = MK.correctEntries(4).toMutableList().also { it[2] = it[2].copy(qid = "q-3-99") }
        assertEquals(MillionsImpossible.QUESTION_NOT_IN_PACK, why(MK.journal(e, End(Kind.FORFEIT, 5), 0)))
    }

    @Test fun levelsOutOfOrder() {
        val e = MK.correctEntries(4).toMutableList().also { val a = it[1]; it[1] = it[2]; it[2] = a }
        assertEquals(MillionsImpossible.LEVELS_OUT_OF_ORDER, why(MK.journal(e, End(Kind.FORFEIT, 5), 0)))
        val skipped = listOf(MK.entry(1), MK.entry(3))
        assertEquals(MillionsImpossible.LEVELS_OUT_OF_ORDER, why(MK.journal(skipped, End(Kind.FORFEIT, 3), 0)))
    }

    @Test fun withdrawOffStop() {
        for (k in listOf(1, 4, 6, 7, 9, 11, 12, 14))
            assertEquals(MillionsImpossible.WITHDRAW_NOT_AT_STOP, why(MK.journal(MK.correctEntries(k), End(Kind.WITHDRAW, k), MillionsLadder.DEFAULT.gainAfter(k))), "k=$k")
    }

    @Test fun gainNotLadder() {
        assertEquals(MillionsImpossible.GAIN_NOT_LADDER, why(MK.journal(MK.correctEntries(8), End(Kind.WITHDRAW, 8), 1201)))
        assertEquals(MillionsImpossible.GAIN_NOT_LADDER, why(MK.journal(MK.correctEntries(8), End(Kind.WITHDRAW, 8), 1600)))
        assertEquals(MillionsImpossible.GAIN_NOT_LADDER, why(MK.journal(MK.correctEntries(15), End(Kind.WON, 0), 9_999)))
        assertEquals(MillionsImpossible.GAIN_NOT_LADDER, why(MK.journal(MK.correctEntries(2) + MK.entry(3, ok = false), End(Kind.WRONG, 3), 200)), "une erreur vaut 0")
        assertEquals(MillionsImpossible.GAIN_NOT_LADDER, why(MK.journal(MK.correctEntries(3), End(Kind.FORFEIT, 4), 100)))
    }

    @Test fun fiftyTwice() {
        val e = MK.correctEntries(3).toMutableList().also { it[0] = it[0].copy(fifty = true); it[2] = it[2].copy(fifty = true) }
        assertEquals(MillionsImpossible.FIFTY_TWICE, why(MK.journal(e, End(Kind.FORFEIT, 4), 0)))
        assertNull(why(MK.journal(MK.correctEntries(3).toMutableList().also { it[1] = it[1].copy(fifty = true) }, End(Kind.FORFEIT, 4), 0)))
    }

    @Test fun answerAfterEnd() {
        val afterWrong = listOf(MK.entry(1), MK.entry(2, ok = false), MK.entry(3))
        assertEquals(MillionsImpossible.ANSWER_AFTER_END, why(MK.journal(afterWrong, End(Kind.WRONG, 2), 0)))
        val afterTimeout = listOf(MK.entry(1, ms = 31_000), MK.entry(2))
        assertEquals(MillionsImpossible.ANSWER_AFTER_END, why(MK.journal(afterTimeout, End(Kind.TIMEOUT, 1), 0)))
    }

    @Test fun answerTooFast() {
        assertEquals(MillionsImpossible.ANSWER_TOO_FAST, why(MK.journal(listOf(MK.entry(1, ms = 299)), End(Kind.FORFEIT, 2), 0)))
        assertEquals(MillionsImpossible.ANSWER_TOO_FAST, why(MK.journal(listOf(MK.entry(1, ok = false, ms = 0)), End(Kind.WRONG, 1), 0)))
    }

    @Test fun endMismatch() {
        // « erreur » annoncée alors que la dernière réponse était bonne
        assertEquals(MillionsImpossible.END_MISMATCH, why(MK.journal(MK.correctEntries(3), End(Kind.WRONG, 3), 0)))
        // « gagné » sans 15 réponses
        assertEquals(MillionsImpossible.END_MISMATCH, why(MK.journal(MK.correctEntries(14), End(Kind.WON, 0), 10_000)))
        // retrait annoncé au palier 8 avec 7 réponses
        assertEquals(MillionsImpossible.END_MISMATCH, why(MK.journal(MK.correctEntries(7), End(Kind.WITHDRAW, 8), 1200)))
        // retrait après une mauvaise réponse
        assertEquals(MillionsImpossible.END_MISMATCH, why(MK.journal(MK.correctEntries(4) + MK.entry(5, ok = false), End(Kind.WITHDRAW, 5), 500)))
        // délai dépassé annoncé comme mauvaise réponse ; mauvaise réponse annoncée comme délai
        assertEquals(MillionsImpossible.END_MISMATCH, why(MK.journal(listOf(MK.entry(1, ms = 31_000)), End(Kind.WRONG, 1), 0)))
        assertEquals(MillionsImpossible.END_MISMATCH, why(MK.journal(listOf(MK.entry(1, ok = false)), End(Kind.TIMEOUT, 1), 0)))
        // abandon à un rang qui ne correspond pas
        assertEquals(MillionsImpossible.END_MISMATCH, why(MK.journal(MK.correctEntries(3), End(Kind.FORFEIT, 6), 0)))
    }

    @Test fun playsOverDailyMax() {
        val j = MK.journal(MK.correctEntries(5), End(Kind.WITHDRAW, 5), 500)
        assertNull(why(j, MillionsJournal.Context(playsBefore = 19)))
        assertEquals(MillionsImpossible.PLAYS_OVER_DAILY_MAX, why(j, MillionsJournal.Context(playsBefore = 20)))
    }

    @Test fun startedWhileWinCapReached() {
        val j = MK.journal(MK.correctEntries(8), End(Kind.WITHDRAW, 8), 1200)
        assertNull(why(j, MillionsJournal.Context(winsBefore = MillionsJournal.WinCounts(2, 9, 14))), "une partie commencée sous le plafond peut être gagnée")
        assertEquals(MillionsImpossible.STARTED_AT_WIN_CAP, why(j, MillionsJournal.Context(winsBefore = MillionsJournal.WinCounts(3, 0, 0))))
        assertEquals(MillionsImpossible.STARTED_AT_WIN_CAP, why(j, MillionsJournal.Context(winsBefore = MillionsJournal.WinCounts(0, 10, 0))))
        assertEquals(MillionsImpossible.STARTED_AT_WIN_CAP, why(j, MillionsJournal.Context(winsBefore = MillionsJournal.WinCounts(0, 0, 15))))
    }

    @Test fun reasonNamesAreStableForTheJavaVerifier() {
        assertEquals(
            listOf("PACK_MISMATCH", "BAD_TIMES", "QUESTION_NOT_IN_PACK", "LEVELS_OUT_OF_ORDER", "ANSWER_AFTER_END", "FIFTY_TWICE", "ANSWER_TOO_FAST", "WITHDRAW_NOT_AT_STOP", "END_MISMATCH", "GAIN_NOT_LADDER", "PLAYS_OVER_DAILY_MAX", "STARTED_AT_WIN_CAP"),
            MillionsImpossible.values().map { it.name })
    }
}
