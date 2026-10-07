package castbridge.core.games

import castbridge.core.owner.Ed25519Signer
import castbridge.core.owner.InstallSigner
import castbridge.core.owner.KeyRing
import castbridge.core.quiz.Json
import castbridge.core.wallet.TestMint
import castbridge.core.wallet.Verdict
import castbridge.core.wallet.WalletRefusal
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Le journal signé d'une partie (`cbg1`) : construction, signature, lecture stricte, rejeu par les règles, bornes. CLÉS DE TEST seulement (graines fixes, sans valeur). */
class GameJournalTest {
    private val signer = Ed25519Signer(ByteArray(32) { 0x77 })
    private val stranger = Ed25519Signer(ByteArray(32) { 0x78 })
    private val ring = KeyRing(listOf(signer.trusted()))
    private val s1 = PlayerId("s1"); private val s2 = PlayerId("s2"); private val s3 = PlayerId("s3")
    private val gameId = "0123456789abcdef0123456789abcdef"
    private val t0 = 1_790_000_000_000L

    /** Une partie de Nim jouée jusqu'au bout (graine 0 : 7 jetons, s1 prend 3, s2 prend 3, s1 prend le dernier). */
    private fun finishedNim(seed: Long = 0): TurnEngine<NimState, Take> {
        val e = TurnEngine(Nim, listOf(s1, s2), seed, 1_000, null)
        var t = 1_000L
        while (!e.over) { val p = e.toMove().single(); val m = Nim.legal(e.state, p).last(); t += 250; check(e.submit(m, e.moveNo, t) == MoveVerdict.OK) }
        return e
    }

    private fun journalOf(e: TurnEngine<NimState, Take> = finishedNim()) = GameJournal.of(gameId, signer.keyId, e, t0, t0 + (e.endedAtMs!! - e.startMs))
    private fun refused(v: Verdict<GameJournal>) = (v as? Verdict.Rejected)?.reason
    private fun verify(t: String?) = GameJournal.verify(t, ring)
    private fun mint(payload: Map<String, Any?>, s: Ed25519Signer = signer) = TestMint.token(GameJournal.PREFIX, GameJournal.DOMAIN, payload, s)

    // ---------------------------------------------------------------- construction

    @Test fun buildsFromAFinishedGameWithoutAnyState() {
        val e = finishedNim(); val j = journalOf(e)
        assertEquals(signer.keyId, j.kid); assertEquals(gameId, j.gameId)
        assertEquals("nim", j.rulesId); assertEquals(1, j.rulesVersion); assertEquals(0L, j.seed)
        assertEquals(listOf("s1", "s2"), j.players); assertEquals(emptyList(), j.ai)
        assertEquals(listOf("take:3", "take:3", "take:1"), j.entries.map { it.move })
        assertEquals(listOf(0, 1, 0), j.entries.map { it.seat })
        assertEquals(listOf(250L, 500L, 750L), j.entries.map { it.atMs })
        assertEquals(GameJournal.End(EndReason.RULES, Outcome.Winners(listOf(s1)), null), j.end)
        assertEquals(t0, j.t0); assertEquals(t0 + 750, j.t1)
    }

    @Test fun refusesAGameThatIsNotOverAndSizesOverTheBound() {
        val running = TurnEngine(Nim, listOf(s1, s2), 0, 0, null)
        assertFailsWith<IllegalArgumentException> { GameJournal.of(gameId, signer.keyId, running, t0, t0 + 1) }
        val e = finishedNim()
        assertFailsWith<IllegalArgumentException> { GameJournal.of("not hex", signer.keyId, e, t0, t0 + 1) }
        assertFailsWith<IllegalArgumentException> { GameJournal.of(gameId, "zz", e, t0, t0 + 1) }
        assertFailsWith<IllegalArgumentException> { GameJournal.of(gameId, signer.keyId, e, t0, t0 - 1) }
    }

    @Test fun theComputerSeatsAndEveryEndReasonAreKept() {
        val a = TurnEngine(Nim, listOf(s1, s2), 0, 0, null, ai = setOf(s2)); a.resign(s1, 5_000)
        val j = GameJournal.of(gameId, signer.keyId, a, t0, t0 + 5_000)
        assertEquals(listOf("s2"), j.ai); assertEquals(GameJournal.End(EndReason.RESIGNATION, Outcome.Winners(listOf(s2)), s1), j.end)
        val b = TurnEngine(Nim, listOf(s1, s2), 0, 0, MoveClock(10_000)); b.tick(10_000)
        assertEquals(GameJournal.End(EndReason.TIMEOUT, Outcome.Winners(listOf(s2)), s1), GameJournal.of(gameId, signer.keyId, b, t0, t0 + 10_000).end)
    }

    // ---------------------------------------------------------------- signature

    @Test fun signAndVerifyRoundTripWithTheInstallationKey() {
        val j = journalOf()
        val token = GameJournal.sign(j, signer)
        assertTrue(token.startsWith("cbg1.")); assertEquals(3, token.split('.').size)
        assertEquals(j, (verify(token) as Verdict.Accepted).value)
        // la clé d'installation de la TV (InstallSigner), par l'adaptateur : mêmes octets que la même graine en Ed25519Signer
        val install = InstallSigner(ByteArray(32) { 0x77 })
        assertEquals(token, GameJournal.sign(j, GameJournal.signerOf(install)), "Ed25519 déterministe : la même clé donne la même signature")
        assertEquals(install.keyId, GameJournal.signerOf(install).keyId)
    }

    @Test fun signRefusesAnotherKeyThanTheOneInTheJournal() {
        val j = journalOf()
        assertFailsWith<IllegalArgumentException> { GameJournal.sign(j, stranger) }
        val foreign = GameJournal.sign(j.copy(kid = stranger.keyId), stranger)
        assertEquals(WalletRefusal.UNKNOWN_KEY, refused(verify(foreign)), "clé inconnue de l'anneau")
        assertEquals(WalletRefusal.REVOKED_KEY, refused(GameJournal.verify(GameJournal.sign(j, signer), ring.withRevoked(setOf(signer.keyId)))))
    }

    @Test fun hasItsOwnDomainNeverTheOnesOfOtherSignedPieces() {
        val p = journalOf().payload()
        for (d in listOf("castbridge-millions-journal-v1", "castbridge-tv-proof-v1", "castbridge-play-result-v1", ""))
            assertEquals(WalletRefusal.BAD_SIGNATURE, refused(verify(TestMint.token(GameJournal.PREFIX, d, p, signer))), "domaine « $d »")
        assertEquals(WalletRefusal.UNREADABLE, refused(verify(TestMint.token("cbm1", GameJournal.DOMAIN, p, signer))), "autre préfixe")
        assertNotEquals("cbm1", GameJournal.PREFIX)
    }

    @Test fun anyAlterationIsRefused() {
        val token = GameJournal.sign(journalOf(), signer)
        val t = token.split('.')
        fun alter(f: (String) -> String) = "${t[0]}.${Base64.getUrlEncoder().withoutPadding().encodeToString(f(String(Base64.getUrlDecoder().decode(t[1]))).toByteArray())}.${t[2]}"
        assertEquals(WalletRefusal.BAD_SIGNATURE, refused(verify(alter { it.replace("take:1", "take:2") })), "un coup changé")
        assertEquals(WalletRefusal.BAD_SIGNATURE, refused(verify(alter { it.replace("\"seed\":0", "\"seed\":1") })), "la graine changée")
        assertEquals(WalletRefusal.UNREADABLE, refused(verify(null)))
        assertEquals(WalletRefusal.UNREADABLE, refused(verify("")))
        assertEquals(WalletRefusal.UNREADABLE, refused(verify(token + ".x")))
        assertEquals(WalletRefusal.UNREADABLE, refused(verify(token.replace("cbg1.", "cbg2."))))
        assertEquals(WalletRefusal.UNREADABLE, refused(verify("x".repeat(GameJournal.MAX_LENGTH + 1))))
    }

    @Test fun theReaderIsStrictEvenForAuthenticPieces() {
        val c = Json.write(journalOf().payload())
        fun raw(s: String) = TestMint.raw(GameJournal.PREFIX, GameJournal.DOMAIN, s, signer)
        assertEquals(WalletRefusal.UNREADABLE, refused(verify(raw(c.dropLast(1) + ",\"extra\":1}"))), "clé en trop")
        assertEquals(WalletRefusal.UNREADABLE, refused(verify(raw(c.replace(",", ", ")))), "espaces : pas la forme canonique")
        assertEquals(WalletRefusal.UNREADABLE, refused(verify(raw(c.replace("\"rv\":1", "\"rv\":1.0")))), "nombre non canonique")
        assertEquals(WalletRefusal.UNREADABLE, refused(verify(raw(c.replace("\"rules\":\"nim\"", "\"rules\":\"NIM\"")))), "identifiant de jeu en majuscules")
        assertEquals(WalletRefusal.UNREADABLE, refused(verify(raw(c.replace(gameId, gameId.uppercase())))), "identifiant de partie en majuscules")
        assertEquals(WalletRefusal.UNREADABLE, refused(verify(mint(journalOf().payload() - "head"))), "clé manquante")
        assertEquals(WalletRefusal.UNREADABLE, refused(verify(mint(journalOf().payload() + ("end" to "RULES")))), "mauvais type")
    }

    @Test fun theChainCommitsToEveryMoveInOrder() {
        val j = journalOf()
        val swapped = j.copy(entries = listOf(j.entries[1], j.entries[0], j.entries[2]))
        val p = swapped.payload().toMutableMap().also { it["head"] = j.payload()["head"] }
        assertEquals(WalletRefusal.UNREADABLE, refused(verify(mint(p))), "deux coups permutés avec l'ancienne empreinte finale")
        val dropped = j.copy(entries = j.entries.dropLast(1)).payload().toMutableMap().also { it["head"] = j.payload()["head"] }
        assertEquals(WalletRefusal.UNREADABLE, refused(verify(mint(dropped))), "dernier coup retiré")
        assertNotEquals(j.payload()["head"], swapped.payload()["head"])
        assertNotEquals(j.payload()["head"], j.copy(seed = 1).payload()["head"], "la graine fait partie de la chaîne")
        assertNotEquals(j.payload()["head"], j.copy(gameId = "f".repeat(32)).payload()["head"])
    }

    @Test fun boundsAreEnforcedOnReading() {
        val j = journalOf()
        val many = j.copy(entries = List(GameJournal.MAX_ENTRIES + 1) { GameJournal.Entry(it % 2, "take:1", it.toLong(), false) })
        assertEquals(WalletRefusal.OUT_OF_BOUNDS, refused(verify(mint(many.payload()))))
        val exact = j.copy(entries = List(GameJournal.MAX_ENTRIES) { GameJournal.Entry(it % 2, "take:1", it.toLong(), false) })
        assertNotNull(GameJournal.sign(exact, signer).also { assertTrue(it.length <= GameJournal.MAX_LENGTH, "${it.length} caractères") })
        assertEquals(exact, (verify(GameJournal.sign(exact, signer)) as Verdict.Accepted).value)
        assertEquals(WalletRefusal.OUT_OF_BOUNDS, refused(verify(mint(j.copy(entries = listOf(GameJournal.Entry(0, "take:1", GameJournal.MAX_MS + 1, false))).payload()))))
        assertEquals(WalletRefusal.OUT_OF_BOUNDS, refused(verify(mint(j.copy(players = List(GameJournal.MAX_PLAYERS + 1) { "s${it + 1}" }).payload()))))
        assertEquals(WalletRefusal.UNREADABLE, refused(verify(mint(j.copy(entries = listOf(GameJournal.Entry(5, "take:1", 1, false))).payload()))), "place hors de la table")
        assertEquals(WalletRefusal.UNREADABLE, refused(verify(mint(j.copy(entries = listOf(GameJournal.Entry(0, "take 1 !", 1, false))).payload()))), "texte de coup invalide")
    }

    @Test fun aGameTooLongForAJournalIsRefusedAtBuildTime() {
        val e = TurnEngine(Endless, listOf(s1, s2), 0, 0, null)
        repeat(GameJournal.MAX_ENTRIES + 1) { assertEquals(MoveVerdict.OK, e.submit(Pass(e.toMove().single()), e.moveNo, it.toLong())) }
        e.abandon(5_000)
        assertFailsWith<IllegalArgumentException> { GameJournal.of(gameId, signer.keyId, e, t0, t0 + 5_000) }
        val ok = TurnEngine(Endless, listOf(s1, s2), 0, 0, null)
        repeat(GameJournal.MAX_ENTRIES) { ok.submit(Pass(ok.toMove().single()), ok.moveNo, it.toLong()) }
        ok.abandon(5_000)
        assertEquals(GameJournal.MAX_ENTRIES, GameJournal.of(gameId, signer.keyId, ok, t0, t0 + 5_000).entries.size, "exactement la borne : accepté")
    }

    // ---------------------------------------------------------------- rejeu par les règles

    private fun j(vararg moves: Pair<Int, String>, end: GameJournal.End = GameJournal.End(EndReason.RULES, Outcome.Winners(listOf(s1)), null), players: List<String> = listOf("s1", "s2"),
                  rules: String = "nim", v: Int = 1, seed: Long = 0, times: List<Long>? = null, ai: List<String> = emptyList(), auto: Set<Int> = emptySet()) =
        GameJournal(signer.keyId, gameId, rules, v, seed, players, ai, moves.mapIndexed { i, (s, m) -> GameJournal.Entry(s, m, times?.get(i) ?: (i * 100L), i in auto) }, end, t0, t0 + 10_000)
    private val honest = arrayOf(0 to "take:3", 1 to "take:3", 0 to "take:1")

    @Test fun anHonestJournalReplaysToTheSameEnd() {
        assertNull(GameJournal.impossible(journalOf(), Nim))
        assertNull(GameJournal.impossible(j(*honest), Nim))
        // abandon, temps dépassé, déconnexion, abandon de la salle : l'issue est celle de la fin annoncée
        assertNull(GameJournal.impossible(j(0 to "take:2", end = GameJournal.End(EndReason.RESIGNATION, Outcome.Winners(listOf(s2)), s1)), Nim))
        assertNull(GameJournal.impossible(j(end = GameJournal.End(EndReason.TIMEOUT, Outcome.Winners(listOf(s2)), s1)), Nim))
        assertNull(GameJournal.impossible(j(end = GameJournal.End(EndReason.DISCONNECTED, Outcome.Winners(listOf(s2)), s1)), Nim))
        assertNull(GameJournal.impossible(j(end = GameJournal.End(EndReason.ABANDONED, Outcome.Draw, null)), Nim))
        assertNull(GameJournal.impossible(j(0 to "take:1", end = GameJournal.End(EndReason.RESIGNATION, Outcome.Winners(listOf(s1, s3)), s2), players = listOf("s1", "s2", "s3")), Nim), "à trois")
    }

    @Test fun aGameOfCardsReplaysFromTheSeedAloneAndAutoMovesMustBeTheFallback() {
        val e = TurnEngine(Hands, listOf(s1, s2), 77L, 0, MoveClock(10_000, TimeoutPolicy.AUTO_MOVE))
        var t = 0L
        e.tick(10_000)                                         // s1 : coup d'office
        while (!e.over) { val p = e.toMove().single(); t += 50; e.submit(Hands.legal(e.state, p).last(), e.moveNo, 10_000 + t) }
        val journal = GameJournal.of(gameId, signer.keyId, e, t0, t0 + 20_000)
        assertTrue(journal.entries.first().auto); assertNull(GameJournal.impossible(journal, Hands))
        val notTheFallback = Hands.legal(Hands.initial(77L, listOf(s1, s2)), s1).last()      // le coup d'office est le PREMIER coup légal
        val lie = journal.copy(entries = listOf(journal.entries.first().copy(move = Hands.encodeMove(notTheFallback))) + journal.entries.drop(1))
        assertEquals(JournalImpossible.AUTO_MOVE_NOT_FALLBACK, GameJournal.impossible(lie, Hands), "un coup « d'office » qui n'est pas celui que les règles auraient joué")
    }

    @Test fun everyImpossibilityIsNamed() {
        assertEquals(JournalImpossible.RULES_MISMATCH, GameJournal.impossible(j(*honest, rules = "autre"), Nim))
        assertEquals(JournalImpossible.RULES_MISMATCH, GameJournal.impossible(j(*honest, v = 2), Nim), "une autre version des règles")
        assertEquals(JournalImpossible.BAD_TABLE, GameJournal.impossible(j(players = listOf("s1")), Nim))
        assertEquals(JournalImpossible.BAD_TABLE, GameJournal.impossible(j(players = listOf("s1", "s1")), Nim))
        assertEquals(JournalImpossible.BAD_TABLE, GameJournal.impossible(j(*honest, ai = listOf("s9")), Nim), "ordinateur hors de la table")
        assertEquals(JournalImpossible.UNDECODABLE_MOVE, GameJournal.impossible(j(0 to "take:9"), Nim))
        assertEquals(JournalImpossible.BAD_TABLE, GameJournal.impossible(j(7 to "take:1"), Nim), "place inexistante")
        assertEquals(JournalImpossible.ILLEGAL_MOVE, GameJournal.impossible(j(1 to "take:1"), Nim), "ce n'est pas à s2")
        assertEquals(JournalImpossible.MOVE_AFTER_END, GameJournal.impossible(j(*honest, 1 to "take:1"), Nim))
        assertEquals(JournalImpossible.BAD_TIMES, GameJournal.impossible(j(*honest, times = listOf(300L, 200L, 400L)), Nim), "heures qui reculent")
        assertEquals(JournalImpossible.BAD_TIMES, GameJournal.impossible(j(*honest).copy(t1 = t0 - 1), Nim))
        assertEquals(JournalImpossible.END_MISMATCH, GameJournal.impossible(j(*honest, end = GameJournal.End(EndReason.RULES, Outcome.Winners(listOf(s2)), null)), Nim), "mauvais gagnant")
        assertEquals(JournalImpossible.END_MISMATCH, GameJournal.impossible(j(0 to "take:3"), Nim), "fin par les règles annoncée alors que la partie continue")
        assertEquals(JournalImpossible.END_MISMATCH, GameJournal.impossible(j(*honest, end = GameJournal.End(EndReason.RESIGNATION, Outcome.Winners(listOf(s2)), s1)), Nim), "abandon après la victoire")
        assertEquals(JournalImpossible.END_MISMATCH, GameJournal.impossible(j(end = GameJournal.End(EndReason.RESIGNATION, Outcome.Winners(listOf(s1)), s1)), Nim), "celui qui abandonne ne gagne pas")
        assertEquals(JournalImpossible.END_MISMATCH, GameJournal.impossible(j(end = GameJournal.End(EndReason.DISCONNECTED, Outcome.Winners(listOf(s2)), null)), Nim), "forfait sans fautif")
        assertEquals(JournalImpossible.END_MISMATCH, GameJournal.impossible(j(end = GameJournal.End(EndReason.ABANDONED, Outcome.Winners(listOf(s1)), null)), Nim))
        assertEquals(JournalImpossible.END_MISMATCH, GameJournal.impossible(j(end = GameJournal.End(EndReason.ABANDONED, Outcome.Draw, s1)), Nim))
    }

    // ---------------------------------------------------------------- aucune main dans le journal

    @Test fun noHandEverAppearsInTheJournalOnlyTheCardsActuallyPlayed() {
        val e = TurnEngine(Hands, listOf(s1, s2), 5L, 0, null)
        val hands = Hands.initial(5L, listOf(s1, s2)).hands.values.flatten().map { it.code }.toSet()
        e.submit(Hands.legal(e.state, s1).first(), 0, 100)
        e.resign(s2, 200)                                       // la partie s'arrête avec cinq cartes encore en main
        val journal = GameJournal.of(gameId, signer.keyId, e, t0, t0 + 200)
        val text = String(Base64.getUrlDecoder().decode(GameJournal.sign(journal, signer).split('.')[1]))
        val played = hands.filter { text.contains("\"play:$it\"") }
        assertEquals(1, played.size, "une seule carte jouée, donc une seule dans le journal : $played")
        for (c in hands - played.toSet()) assertFalse(text.contains(c), "la carte « $c » (encore en main) ne doit pas figurer dans le journal")
        assertFalse(text.contains("hand", ignoreCase = true)); assertFalse(text.contains("pioche")); assertFalse(text.contains("deck", ignoreCase = true))
        assertNull(GameJournal.impossible(journal, Hands), "et pourtant il se rejoue : la graine et les coups suffisent")
    }

    // ---------------------------------------------------------------- journaux gardés par la TV

    @Test fun theLogKeepsTheLastJournalsOnly() {
        val log = GameJournalLog(max = 3)
        val tokens = (1..5).map { i -> GameJournal.sign(journalOf().copy(gameId = "%032x".format(i)), signer) }
        tokens.forEachIndexed { i, t -> log.add(GameJournalLog.Item("%032x".format(i + 1), "nim", t0 + i, t)) }
        assertEquals(listOf(3, 4, 5), log.items().map { it.gameId.toInt(16) }, "les trois derniers, du plus ancien au plus récent")
        assertEquals(tokens[4], log.items().last().token)
        log.add(GameJournalLog.Item("%032x".format(5), "nim", t0, tokens[4]))
        assertEquals(3, log.items().size, "un journal déjà gardé n'est pas gardé deux fois")
        assertFailsWith<IllegalArgumentException> { log.add(GameJournalLog.Item("zz", "nim", 0, tokens[0])) }
        assertFailsWith<IllegalArgumentException> { log.add(GameJournalLog.Item("%032x".format(9), "nim", 0, "x".repeat(GameJournal.MAX_LENGTH + 1))) }
        assertFailsWith<IllegalArgumentException> { GameJournalLog(max = 0) }
    }
}
