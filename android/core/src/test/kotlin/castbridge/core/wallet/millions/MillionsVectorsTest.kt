package castbridge.core.wallet.millions

import castbridge.core.owner.KeyRing
import castbridge.core.owner.TrustedKey
import castbridge.core.owner.TvClock
import castbridge.core.quiz.Json
import castbridge.core.wallet.TestMint
import castbridge.core.wallet.Verdict
import castbridge.core.wallet.WalletTestKeys
import castbridge.core.wallet.millions.MillionsJournal.End
import castbridge.core.wallet.millions.MillionsJournal.Kind
import java.io.File
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Vecteurs communs Kotlin ↔ Java (`tools/wallet/millions-vectors.json`, `castbridge-millions-vectors-v1`) : packs `cbk1` et journaux `cbm1` DORÉS, journaux authentiques mais IMPOSSIBLES avec leur motif
 * ([MillionsImpossible], noms stables). Clés de TEST (graines fixes, publiques, sans valeur). Jamais édité à la main : `MILLIONS_VECTORS_WRITE=1` le régénère, et le test échoue s'il dérive.
 */
class MillionsVectorsTest {
    private val file = File(System.getProperty("millions.vectors") ?: "../../tools/wallet/millions-vectors.json")
    private val pack = MK.pack(levels = MK.levels(2))
    private fun map(vararg p: Pair<String, Any?>) = linkedMapOf(*p)
    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
    private fun tok(j: MillionsJournal) = MillionsJournal.sign(j, MK.install)
    private fun ctxMap(c: MillionsJournal.Context) = map("playsBefore" to c.playsBefore, "winsBefore" to map("day" to c.winsBefore.day, "week" to c.winsBefore.week, "month" to c.winsBefore.month))
    private fun qMap(q: MillionsQuestion) = listOf(q.qid, q.text, q.choices, q.correct, q.fifty)

    private fun build(): String {
        val goldenPacks = listOf(
            map("name" to "pack-b-2-per-level", "token" to MK.packToken(pack), "nowMs" to MK.NOW, "expect" to map("packId" to pack.packId, "id" to MK.TV, "ladderVersion" to 1, "stake" to 500, "timeSec" to 30, "levelSizes" to pack.levels.map { it.size }, "ladder" to pack.ladder.values)),
            map("name" to "pack-gain-gt-zero", "token" to MK.packToken(pack.copy(winDefinition = WinDefinition.GAIN_GT_ZERO)), "nowMs" to MK.NOW, "expect" to map("packId" to pack.packId, "id" to MK.TV, "ladderVersion" to 1, "stake" to 500, "timeSec" to 30, "levelSizes" to pack.levels.map { it.size }, "ladder" to pack.ladder.values)),
        )
        val t = MK.packToken(pack).split('.')
        val forged = String(Base64.getUrlDecoder().decode(t[1])).replace("\"stake\":500", "\"stake\":501")
        val refusedPacks = listOf(
            map("name" to "tampered-stake", "token" to "${t[0]}.${Base64.getUrlEncoder().withoutPadding().encodeToString(forged.toByteArray())}.${t[2]}", "nowMs" to MK.NOW, "expect" to "BAD_SIGNATURE"),
            map("name" to "wrong-domain", "token" to MK.packToken(pack, domain = "castbridge-wallet-snapshot-v1"), "nowMs" to MK.NOW, "expect" to "BAD_SIGNATURE"),
            map("name" to "other-tv", "token" to MK.packToken(pack.copy(id = "tv-autre")), "nowMs" to MK.NOW, "expect" to "OTHER_TV"),
            map("name" to "expired", "token" to MK.packToken(pack), "nowMs" to MK.NOW + 7 * MK.DAY, "expect" to "EXPIRED"),
            map("name" to "life-over-14-days", "token" to MK.packToken(pack.copy(until = pack.from + 14 * MK.DAY + 1)), "nowMs" to MK.NOW, "expect" to "TOO_LONG_LIFE"),
            map("name" to "ladder-not-increasing", "token" to MK.packToken(pack.copy(ladder = MillionsLadder(MillionsLadder.B.toMutableList().also { it[3] = it[2] }, 500))), "nowMs" to MK.NOW, "expect" to "OUT_OF_BOUNDS"),
            map("name" to "fifty-removes-correct-answer", "token" to MK.packToken(pack.copy(levels = pack.levels.toMutableList().also { l -> val q = l[0][0]; l[0] = listOf(q.copy(fifty = listOf(q.correct, (q.correct + 1) % 4))) + l[0].drop(1) })), "nowMs" to MK.NOW, "expect" to "OUT_OF_BOUNDS"),
            map("name" to "unknown-key", "token" to MK.packToken(pack.copy(kid = WalletTestKeys.stranger.keyId), WalletTestKeys.stranger), "nowMs" to MK.NOW, "expect" to "UNKNOWN_KEY"),
        )
        // journaux dorés : les mêmes que ceux d'une vraie partie, signés par la clé d'installation de test
        fun real(play: (MillionsGame) -> Unit): MillionsJournal { val g = MK.game(pack); g.start(); play(g); return g.toJournal(MK.install.keyId) }
        fun golden(name: String, j: MillionsJournal) = map("name" to name, "token" to tok(j), "pack" to "pack-b-2-per-level", "expect" to map("end" to j.end.text, "gain" to j.gain, "entries" to j.entries.size, "gameId" to j.gameId, "t0" to j.t0, "t1" to j.t1))
        val goldenJournals = listOf(
            golden("won-q15", real { MK.correct(it, 1, 15) }),
            golden("withdraw-8", real { MK.correct(it, 1, 8); it.withdraw() }),
            golden("withdraw-5", real { MK.correct(it, 1, 5); it.withdraw() }),
            golden("wrong-at-3", real { MK.correct(it, 1, 2); val q = MK.question(3, 0); it.show(q); it.answer((q.correct + 1) % 4, 2500) }),
            golden("timeout-at-2", real { MK.correct(it, 1, 1); val q = MK.question(2, 0); it.show(q); it.answer(q.correct, 30_001) }),
            golden("forfeit-at-4", real { MK.correct(it, 1, 3); it.forfeit() }),
            golden("fifty-used-then-wrong", real { val q = MK.question(1, 0); it.show(q); it.fifty(); it.answer(q.correct, 1800); val q2 = MK.question(2, 0); it.show(q2); it.answer((q2.correct + 1) % 4, 900) }),
        )
        val jt = tok(real { MK.correct(it, 1, 8); it.withdraw() }).split('.')
        val tamperedGain = String(Base64.getUrlDecoder().decode(jt[1])).replace("\"gain\":1200", "\"gain\":9999")
        val compact = Json.write(real { MK.correct(it, 1, 8); it.withdraw() }.payload())
        val refusedJournals = listOf(
            map("name" to "tampered-gain", "token" to "${jt[0]}.${Base64.getUrlEncoder().withoutPadding().encodeToString(tamperedGain.toByteArray())}.${jt[2]}", "expect" to "BAD_SIGNATURE"),
            map("name" to "wrong-domain", "token" to TestMint.token(MillionsJournal.PREFIX, "castbridge-play-result-v1", real { MK.correct(it, 1, 8); it.withdraw() }.payload(), MK.installEd), "expect" to "BAD_SIGNATURE"),
            map("name" to "unknown-install-key", "token" to MillionsJournal.sign(real { MK.correct(it, 1, 8); it.withdraw() }.copy(kid = MK.stranger.keyId), MK.stranger), "expect" to "UNKNOWN_KEY"),
            map("name" to "broken-hash-chain", "token" to TestMint.raw(MillionsJournal.PREFIX, MillionsJournal.DOMAIN, compact.substring(0, compact.indexOf("\"entries\"")) + compact.substring(compact.indexOf("\"entries\"")).replaceFirst(Regex("\"[0-9a-f]{16}\""), "\"0000000000000000\""), MK.installEd), "expect" to "UNREADABLE"),
            map("name" to "extra-field", "token" to TestMint.raw(MillionsJournal.PREFIX, MillionsJournal.DOMAIN, compact.dropLast(1) + ",\"extra\":1}", MK.installEd), "expect" to "UNREADABLE"),
            map("name" to "sixteen-entries", "token" to TestMint.token(MillionsJournal.PREFIX, MillionsJournal.DOMAIN, MK.journal(MK.correctEntries(15) + MK.entry(15), End(Kind.WON, 0), 10_000).payload(), MK.installEd), "expect" to "OUT_OF_BOUNDS"),
        )
        fun imp(name: String, j: MillionsJournal, why: MillionsImpossible, ctx: MillionsJournal.Context = MillionsJournal.Context()) =
            map("name" to name, "token" to tok(j), "pack" to "pack-b-2-per-level", "context" to ctxMap(ctx), "expect" to why.name)
        val swapped = MK.correctEntries(4).toMutableList().also { val a = it[1]; it[1] = it[2]; it[2] = a }
        val impossible = listOf(
            imp("other-pack", MK.journal(MK.correctEntries(8), End(Kind.WITHDRAW, 8), 1200, packId = "f".repeat(32)), MillionsImpossible.PACK_MISMATCH),
            imp("other-ladder-version", MK.journal(MK.correctEntries(8), End(Kind.WITHDRAW, 8), 1200, lv = 2), MillionsImpossible.PACK_MISMATCH),
            imp("end-before-start", MK.journal(MK.correctEntries(5), End(Kind.WITHDRAW, 5), 500, t0 = 10, t1 = 9), MillionsImpossible.BAD_TIMES),
            imp("question-not-in-pack", MK.correctEntries(4).toMutableList().also { it[2] = it[2].copy(qid = "q-3-99") }.let { MK.journal(it, End(Kind.FORFEIT, 5), 0) }, MillionsImpossible.QUESTION_NOT_IN_PACK),
            imp("levels-swapped", MK.journal(swapped, End(Kind.FORFEIT, 5), 0), MillionsImpossible.LEVELS_OUT_OF_ORDER),
            imp("answer-after-wrong", MK.journal(listOf(MK.entry(1), MK.entry(2, ok = false), MK.entry(3)), End(Kind.WRONG, 2), 0), MillionsImpossible.ANSWER_AFTER_END),
            imp("fifty-twice", MK.journal(MK.correctEntries(3).toMutableList().also { it[0] = it[0].copy(fifty = true); it[2] = it[2].copy(fifty = true) }, End(Kind.FORFEIT, 4), 0), MillionsImpossible.FIFTY_TWICE),
            imp("answer-in-299-ms", MK.journal(listOf(MK.entry(1, ms = 299)), End(Kind.FORFEIT, 2), 0), MillionsImpossible.ANSWER_TOO_FAST),
            imp("withdraw-at-6", MK.journal(MK.correctEntries(6), End(Kind.WITHDRAW, 6), 700), MillionsImpossible.WITHDRAW_NOT_AT_STOP),
            imp("won-with-14-answers", MK.journal(MK.correctEntries(14), End(Kind.WON, 0), 10_000), MillionsImpossible.END_MISMATCH),
            imp("wrong-claimed-after-correct-answer", MK.journal(MK.correctEntries(3), End(Kind.WRONG, 3), 0), MillionsImpossible.END_MISMATCH),
            imp("gain-not-on-ladder", MK.journal(MK.correctEntries(8), End(Kind.WITHDRAW, 8), 1600), MillionsImpossible.GAIN_NOT_LADDER),
            imp("gain-after-error", MK.journal(MK.correctEntries(2) + MK.entry(3, ok = false), End(Kind.WRONG, 3), 200), MillionsImpossible.GAIN_NOT_LADDER),
            imp("21st-play-of-the-day", MK.journal(MK.correctEntries(5), End(Kind.WITHDRAW, 5), 500), MillionsImpossible.PLAYS_OVER_DAILY_MAX, MillionsJournal.Context(playsBefore = 20)),
            imp("started-at-daily-win-cap", MK.journal(MK.correctEntries(8), End(Kind.WITHDRAW, 8), 1200), MillionsImpossible.STARTED_AT_WIN_CAP, MillionsJournal.Context(winsBefore = MillionsJournal.WinCounts(3, 3, 3))),
            imp("started-at-weekly-win-cap", MK.journal(MK.correctEntries(8), End(Kind.WITHDRAW, 8), 1200), MillionsImpossible.STARTED_AT_WIN_CAP, MillionsJournal.Context(winsBefore = MillionsJournal.WinCounts(0, 10, 10))),
            imp("started-at-monthly-win-cap", MK.journal(MK.correctEntries(8), End(Kind.WITHDRAW, 8), 1200), MillionsImpossible.STARTED_AT_WIN_CAP, MillionsJournal.Context(winsBefore = MillionsJournal.WinCounts(0, 0, 15))),
        )
        val chainEntries = MK.correctEntries(3)
        val root = map(
            "format" to "castbridge-millions-vectors-v1",
            "note" to "CLÉS DE TEST SEULEMENT : graines fixes et publiques, sans aucune valeur. Fichier généré par MillionsVectorsTest (MILLIONS_VECTORS_WRITE=1), ne pas éditer à la main.",
            "encoding" to map(
                "token" to "PREFIX.PAYLOAD.SIGNATURE ; PAYLOAD = base64url sans bourrage de la charge JSON compacte ; SIGNATURE = base64url sans bourrage de la signature Ed25519 (64 octets) sur ASCII(DOMAINE + LF + PREFIX + point + PAYLOAD) ; même enveloppe que cbw1/cbe1/cbr1 (tools/wallet/wallet-vectors.json)",
                "cbk1" to "pack signé par la clé « portefeuille » de l'API (domaine castbridge-millions-pack-v1) ; fenêtre [from, until[ d'au plus 14 jours ; 15 niveaux de 1 à 20 questions",
                "cbm1" to "journal signé par la clé d'INSTALLATION de la TV (domaine castbridge-millions-journal-v1, jamais celui des preuves de TV) ; entries = [qid, choix (-1 = aucune réponse), joker 50:50 (0/1), ms depuis l'affichage, empreinte] ; end = WRONG@k | TIMEOUT@k | WITHDRAW@k | WON | FORFEIT@k",
                "chain" to "h0 = hex16(SHA-256(\"cbm1-chain|<gameId>|<packId>|<lv>\")) ; h(i) = hex16(SHA-256(h(i-1) + \"|\" + qid + \"|\" + choix + \"|\" + joker(0/1) + \"|\" + ms)) ; hex16 = 8 premiers octets en minuscules ; head = dernière empreinte (h0 si aucune entrée)",
                "impossibleOrder" to MillionsImpossible.values().map { it.name },
            ),
            "keys" to listOf(
                map("role" to "wallet", "seedHex" to hex(WalletTestKeys.walletSeed), "publicKeyBase64" to WalletTestKeys.wallet.publicKeyBase64, "kid" to WalletTestKeys.wallet.keyId),
                map("role" to "install", "seedHex" to hex(MK.installSeed), "publicKeyBase64" to MK.install.publicKeyBase64, "kid" to MK.install.keyId),
                map("role" to "stranger-not-trusted", "seedHex" to hex(WalletTestKeys.strangerSeed), "publicKeyBase64" to WalletTestKeys.stranger.publicKeyBase64, "kid" to WalletTestKeys.stranger.keyId),
            ),
            "domains" to map("cbk1" to MillionsPack.DOMAIN, "cbm1" to MillionsJournal.DOMAIN),
            "context" to map("expectedId" to MK.TV, "gameId" to MK.GAME_ID, "packId" to MK.PACK_ID),
            "packs" to map(
                "pack-b-2-per-level" to map("levels" to pack.levels.map { l -> l.map(::qMap) }, "ladder" to pack.ladder.values, "stake" to 500, "timeSec" to 30, "maxPlaysPerDay" to 20, "limits" to listOf(3, 10, 15), "winDefinition" to "GAIN_GT_STAKE"),
            ),
            "chainVector" to map("gameId" to MK.GAME_ID, "packId" to MK.PACK_ID, "lv" to 1, "entries" to chainEntries.map { listOf(it.qid, it.choice, if (it.fifty) 1 else 0, it.ms) }, "hashes" to MillionsJournal.chain(MK.GAME_ID, MK.PACK_ID, 1, chainEntries)),
            "golden" to map("cbk1" to goldenPacks, "cbm1" to goldenJournals),
            "refused" to map("cbk1" to refusedPacks, "cbm1" to refusedJournals),
            "impossible" to impossible,
        )
        return Json.write(root).replace("},{", "},\n{").replace("],\"", "],\n\"").replace("}],", "}],\n") + "\n"
    }

    @Test fun fileIsExactlyWhatTheCodeProduces() {
        val expected = build()
        if (System.getenv("MILLIONS_VECTORS_WRITE") == "1") { file.parentFile.mkdirs(); file.writeText(expected) }
        assertTrue(file.isFile, "vecteurs absents : ${file.absolutePath} (MILLIONS_VECTORS_WRITE=1 pour les générer)")
        assertEquals(expected, file.readText(), "dérive : regénérer avec MILLIONS_VECTORS_WRITE=1")
    }

    @Suppress("UNCHECKED_CAST")
    @Test fun everyGoldenIsAcceptedEveryFakeRefusedAndEveryImpossibleHasItsReason() {
        val root = Json.obj(file.readText())
        assertEquals("castbridge-millions-vectors-v1", root["format"])
        val ctx = root["context"] as Map<String, Any?>
        val id = ctx["expectedId"] as String
        val keys = (root["keys"] as List<Map<String, Any?>>).associateBy { it["role"] as String }
        fun ring(role: String) = KeyRing(listOf(TrustedKey(keys[role]!!["kid"] as String, keys[role]!!["publicKeyBase64"] as String)))   // reconstruits DU FICHIER (clés publiques)
        val walletRing = ring("wallet"); val installRing = ring("install")
        val golden = root["golden"] as Map<String, List<Map<String, Any?>>>; val refused = root["refused"] as Map<String, List<Map<String, Any?>>>
        val packs = HashMap<String, MillionsPack>()
        var checked = 0
        for (g in golden["cbk1"]!!) {
            val p = (MillionsPack.verify(g["token"] as String, walletRing, id, TvClock(), g["nowMs"] as Long) as? Verdict.Accepted)?.value ?: fail("cbk1 doré refusé : ${g["name"]}")
            val e = g["expect"] as Map<String, Any?>
            assertEquals(listOf(e["packId"], e["id"], e["ladderVersion"], e["stake"], e["timeSec"].let { (it as Long).toInt() }, e["levelSizes"], e["ladder"]),
                listOf(p.packId, p.id, p.ladderVersion, p.ladder.stake, p.timeSec, p.levels.map { it.size.toLong() }, p.ladder.values), g["name"] as String)
            packs[g["name"] as String] = p; checked++
        }
        for (r in refused["cbk1"]!!) {
            val v = MillionsPack.verify(r["token"] as String, walletRing, id, TvClock(), r["nowMs"] as Long)
            assertEquals(r["expect"], (v as? Verdict.Rejected)?.reason?.name ?: "ACCEPTED", "cbk1 faux : ${r["name"]}"); checked++
        }
        for (g in golden["cbm1"]!!) {
            val j = (MillionsJournal.verify(g["token"] as String, installRing) as? Verdict.Accepted)?.value ?: fail("cbm1 doré refusé : ${g["name"]}")
            val e = g["expect"] as Map<String, Any?>
            assertEquals(listOf(e["end"], e["gain"], e["entries"], e["gameId"], e["t0"], e["t1"]), listOf(j.end.text, j.gain, j.entries.size.toLong(), j.gameId, j.t0, j.t1), g["name"] as String)
            assertEquals(null, MillionsJournal.impossible(j, packs[g["pack"]]!!), "cbm1 doré impossible : ${g["name"]}"); checked++
        }
        for (r in refused["cbm1"]!!) {
            assertEquals(r["expect"], (MillionsJournal.verify(r["token"] as String, installRing) as? Verdict.Rejected)?.reason?.name ?: "ACCEPTED", "cbm1 faux : ${r["name"]}"); checked++
        }
        for (r in root["impossible"] as List<Map<String, Any?>>) {
            val j = (MillionsJournal.verify(r["token"] as String, installRing) as? Verdict.Accepted)?.value ?: fail("journal impossible mais authentique refusé trop tôt : ${r["name"]}")
            val c = r["context"] as Map<String, Any?>; val w = c["winsBefore"] as Map<String, Any?>
            val context = MillionsJournal.Context((c["playsBefore"] as Long).toInt(), MillionsJournal.WinCounts((w["day"] as Long).toInt(), (w["week"] as Long).toInt(), (w["month"] as Long).toInt()))
            assertEquals(r["expect"], MillionsJournal.impossible(j, packs[r["pack"]]!!, context)?.name ?: "POSSIBLE", "journal impossible : ${r["name"]}"); checked++
        }
        val reasons = (root["impossible"] as List<Map<String, Any?>>).map { it["expect"] }.toSet()
        assertEquals(MillionsImpossible.values().map { it.name }.toSet(), reasons, "chaque motif a au moins un vecteur")
        val cv = root["chainVector"] as Map<String, Any?>
        val rows = (cv["entries"] as List<List<Any?>>).map { MillionsJournal.Entry(it[0] as String, (it[1] as Long).toInt(), it[2] == 1L, it[3] as Long) }
        assertEquals(cv["hashes"], MillionsJournal.chain(cv["gameId"] as String, cv["packId"] as String, cv["lv"] as Long, rows)); checked++
        assertTrue(checked >= 35, "trop peu de vecteurs : $checked")
    }
}
