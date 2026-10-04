package castbridge.core.wallet.millions

import castbridge.core.owner.InstallSigner
import castbridge.core.owner.KeyRing
import castbridge.core.wallet.Verdict
import castbridge.core.wallet.WalletFormats
import castbridge.core.wallet.WalletRefusal
import java.security.MessageDigest
import java.util.Base64

/**
 * Pourquoi un journal AUTHENTIQUE est jugé IMPOSSIBLE par rapport à son pack (noms STABLES : le vérificateur Java de w22-16 relit les mêmes vecteurs). L'ordre de cette liste est celui des contrôles
 * de [MillionsJournal.impossible] : le premier motif rencontré est rendu.
 */
enum class MillionsImpossible {
    PACK_MISMATCH, BAD_TIMES, QUESTION_NOT_IN_PACK, LEVELS_OUT_OF_ORDER, ANSWER_AFTER_END, FIFTY_TWICE, ANSWER_TOO_FAST, WITHDRAW_NOT_AT_STOP, END_MISMATCH, GAIN_NOT_LADDER, PLAYS_OVER_DAILY_MAX, STARTED_AT_WIN_CAP,
}

/**
 * Journal de partie `cbm1`, produit par la TV et signé par la clé d'INSTALLATION ([InstallSigner]) avec SON domaine `castbridge-millions-journal-v1` (jamais celui des preuves de TV). La signature
 * atteste l'ORIGINE (cette installation), pas l'honnêteté : la TV connaît les bonnes réponses, donc le serveur recalcule tout ([impossible]) et seul lui crédite. Un journal n'est pas un solde.
 *
 * Contenu : `gameId` (128 bits), pack et version d'échelle, les réponses dans l'ordre (identifiant de question, choix, 50:50, ms depuis l'affichage), la fin, le gain affiché, début et fin (heure de la TV),
 * et une CHAÎNE d'empreintes (chaque entrée engage toutes les précédentes, le jeu, le pack et l'échelle) : on ne peut ni retirer, ni permuter, ni insérer une entrée sans refaire toute la chaîne
 * (et donc toute la signature). Aucun texte de question, aucune donnée personnelle.
 */
data class MillionsJournal(
    val kid: String, val gameId: String, val packId: String, val ladderVersion: Long, val entries: List<Entry>, val end: End, val gain: Long, val t0: Long, val t1: Long,
) {
    /** Une réponse : [choice] 0..3 (ou -1 : aucune réponse, seulement hors délai), [fifty] : le 50:50 a été utilisé sur CETTE question, [ms] : millisecondes depuis l'affichage. */
    data class Entry(val qid: String, val choice: Int, val fifty: Boolean, val ms: Long)

    enum class Kind { WRONG, TIMEOUT, WITHDRAW, WON, FORFEIT }

    /** Fin de partie : `WRONG@k`, `TIMEOUT@k`, `WITHDRAW@k`, `WON` (k = 0), `FORFEIT@k` (k = question à laquelle on a abandonné). */
    data class End(val kind: Kind, val k: Int) {
        val text: String get() = if (kind == Kind.WON) "WON" else "${kind.name}@$k"
        companion object {
            private val RE = Regex("^(WRONG|TIMEOUT|WITHDRAW|FORFEIT)@([1-9][0-9]?)$")
            fun parse(s: String): End? {
                if (s == "WON") return End(Kind.WON, 0)
                val m = RE.matchEntire(s) ?: return null
                val k = m.groupValues[2].toInt()
                return if (k in 1..MillionsLadder.QUESTIONS) End(Kind.valueOf(m.groupValues[1]), k) else null
            }
        }
    }

    /** Parties gagnées déjà comptées (jour, semaine, mois) avant cette partie. */
    data class WinCounts(val day: Int, val week: Int, val month: Int)

    /** Ce que le journal ne dit pas : le nombre de parties déjà commencées ce jour-là et les parties gagnées déjà comptées au début de celle-ci (le serveur les connaît). */
    data class Context(val playsBefore: Int = 0, val winsBefore: WinCounts = WinCounts(0, 0, 0))

    internal fun payload(): Map<String, Any?> {
        val hashes = chain(gameId, packId, ladderVersion, entries)
        return linkedMapOf(
            "kid" to kid, "gameId" to gameId, "packId" to packId, "lv" to ladderVersion,
            "entries" to entries.mapIndexed { i, e -> listOf(e.qid, e.choice.toLong(), if (e.fifty) 1L else 0L, e.ms, hashes[i]) },
            "end" to end.text, "gain" to gain, "t0" to t0, "t1" to t1, "head" to (hashes.lastOrNull() ?: start(gameId, packId, ladderVersion)),
        )
    }

    companion object {
        const val PREFIX = "cbm1"
        const val DOMAIN = "castbridge-millions-journal-v1"
        const val MAX_LENGTH = 4_000
        const val MAX_ENTRIES = MillionsLadder.QUESTIONS
        const val MAX_MS = 3_600_000L
        /** Une réponse plus rapide que cela (ms) est impossible pour un humain (conception § 13.4). */
        const val MIN_MS = 300L
        private val KEYS = setOf("kid", "gameId", "packId", "lv", "entries", "end", "gain", "t0", "t1", "head")
        private val HEX16 = Regex("^[0-9a-f]{16}$")

        private fun hex16(s: String): String = MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8)).take(8).joinToString("") { "%02x".format(it) }

        /** h0 de la chaîne : engage le jeu, le pack et la version d'échelle. */
        fun start(gameId: String, packId: String, ladderVersion: Long): String = hex16("cbm1-chain|$gameId|$packId|$ladderVersion")

        /** Empreinte de chaque entrée : `h(i) = hex16(SHA-256(h(i-1) | qid | choix | joker | ms))`. */
        fun chain(gameId: String, packId: String, ladderVersion: Long, entries: List<Entry>): List<String> {
            var prev = start(gameId, packId, ladderVersion)
            return entries.map { e -> hex16("$prev|${e.qid}|${e.choice}|${if (e.fifty) 1 else 0}|${e.ms}").also { prev = it } }
        }

        /** Signe [journal] avec la clé d'installation : refuse un `kid` qui n'est pas celui de [signer]. La TV ne signe QUE ceci (jamais un pack, un instantané ni un bon). */
        fun sign(journal: MillionsJournal, signer: InstallSigner): String {
            require(journal.kid == signer.keyId) { "kid ≠ clé d'installation" }
            return WalletFormats.seal(PREFIX, DOMAIN, journal.payload()) { Base64.getDecoder().decode(signer.sign(String(it, Charsets.US_ASCII))) }
        }

        /** Vérifie [token] avec l'anneau des clés d'installation (côté serveur : la clé publique enregistrée à l'activation) : lecture stricte, signature, champs, bornes, chaîne. */
        fun verify(token: String?, ring: KeyRing): Verdict<MillionsJournal> {
            val o = when (val v = WalletFormats.open(token, PREFIX, DOMAIN, ring, MAX_LENGTH)) { is Verdict.Rejected -> return v; is Verdict.Accepted -> v.value }
            return WalletFormats.guard {
                val f = WalletFormats.Fields(o.body, KEYS)
                val gameId = f.str("gameId", WalletFormats.HEX32); val packId = f.str("packId", WalletFormats.HEX32)
                val lv = f.nonNeg("lv")
                val raw = f.list("entries")
                if (raw.size > MAX_ENTRIES) throw WalletFormats.Bad(WalletRefusal.OUT_OF_BOUNDS)
                val given = ArrayList<String>(); val entries = raw.map { row ->
                    val r = row as? List<*> ?: throw WalletFormats.Bad(WalletRefusal.UNREADABLE)
                    if (r.size != 5) throw WalletFormats.Bad(WalletRefusal.UNREADABLE)
                    val qid = (r[0] as? String)?.takeIf { WalletFormats.ID.matches(it) }; val choice = r[1] as? Long; val fifty = r[2] as? Long; val ms = r[3] as? Long; val h = (r[4] as? String)?.takeIf { HEX16.matches(it) }
                    if (qid == null || choice == null || fifty == null || ms == null || h == null || fifty !in 0..1) throw WalletFormats.Bad(WalletRefusal.UNREADABLE)
                    if (choice !in -1..3 || ms !in 0..MAX_MS) throw WalletFormats.Bad(WalletRefusal.OUT_OF_BOUNDS)
                    given += h
                    Entry(qid, choice.toInt(), fifty == 1L, ms)
                }
                val hashes = chain(gameId, packId, lv, entries)
                if (hashes != given || f.str("head", HEX16) != (hashes.lastOrNull() ?: start(gameId, packId, lv))) throw WalletFormats.Bad(WalletRefusal.UNREADABLE)
                val end = End.parse((o.body["end"] as? String) ?: throw WalletFormats.Bad(WalletRefusal.UNREADABLE)) ?: throw WalletFormats.Bad(WalletRefusal.UNREADABLE)
                MillionsJournal(o.kid, gameId, packId, lv, entries, end, f.amount("gain"), f.nonNeg("t0"), f.nonNeg("t1"))
            }
        }

        /**
         * Le journal est-il IMPOSSIBLE par rapport à [pack] ? Rend le premier motif, dans l'ordre de [MillionsImpossible], ou null. Contrôles : pack et version d'échelle ; fin ≥ début ; chaque réponse
         * (question dans le pack, niveau = rang, pas de réponse après une erreur ou un délai dépassé, 50:50 au plus une fois, ≥ [MIN_MS] ms) ; la fin annoncée est celle que les réponses produisent
         * (retrait seulement à un palier) ; le gain est celui de l'échelle du pack (0 sauf retrait ou victoire) ; puis, avec [context], le plafond de parties jouées par jour et les plafonds de parties
         * gagnées (au DÉBUT de la partie seulement : une partie commencée sous le plafond peut toujours être gagnée).
         */
        fun impossible(journal: MillionsJournal, pack: MillionsPack, context: Context = Context()): MillionsImpossible? {
            if (journal.packId != pack.packId || journal.ladderVersion != pack.ladderVersion) return MillionsImpossible.PACK_MISMATCH
            if (journal.t1 < journal.t0) return MillionsImpossible.BAD_TIMES
            var failed: Kind? = null
            var fifties = 0
            for ((i, e) in journal.entries.withIndex()) {
                val (level, q) = pack.locate(e.qid) ?: return MillionsImpossible.QUESTION_NOT_IN_PACK
                if (level != i + 1) return MillionsImpossible.LEVELS_OUT_OF_ORDER
                if (failed != null) return MillionsImpossible.ANSWER_AFTER_END
                if (e.fifty && ++fifties > 1) return MillionsImpossible.FIFTY_TWICE
                if (e.ms < MIN_MS) return MillionsImpossible.ANSWER_TOO_FAST
                failed = when {
                    e.ms > pack.timeSec * 1000L || e.choice == -1 -> Kind.TIMEOUT
                    e.choice != q.correct -> Kind.WRONG
                    else -> null
                }
            }
            val n = journal.entries.size; val end = journal.end
            when (end.kind) {
                Kind.WITHDRAW -> { if (end.k !in pack.ladder.stops) return MillionsImpossible.WITHDRAW_NOT_AT_STOP; if (failed != null || n != end.k) return MillionsImpossible.END_MISMATCH }
                Kind.WON -> if (failed != null || n != MillionsLadder.QUESTIONS) return MillionsImpossible.END_MISMATCH
                Kind.FORFEIT -> if (failed != null || n != end.k - 1) return MillionsImpossible.END_MISMATCH
                Kind.WRONG, Kind.TIMEOUT -> if (failed != end.kind || n != end.k) return MillionsImpossible.END_MISMATCH
            }
            val expected = when (end.kind) { Kind.WITHDRAW -> pack.ladder.gainAfter(end.k); Kind.WON -> pack.ladder.gainAfter(MillionsLadder.QUESTIONS); else -> 0L }
            if (journal.gain != expected) return MillionsImpossible.GAIN_NOT_LADDER
            if (context.playsBefore >= pack.maxPlaysPerDay) return MillionsImpossible.PLAYS_OVER_DAILY_MAX
            val w = context.winsBefore
            if (w.day >= pack.limits.perDay || w.week >= pack.limits.perWeek || w.month >= pack.limits.perMonth) return MillionsImpossible.STARTED_AT_WIN_CAP
            return null
        }
    }
}
