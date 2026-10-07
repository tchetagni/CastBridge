package castbridge.core.games

import castbridge.core.owner.InstallSigner
import castbridge.core.owner.KeyRing
import castbridge.core.owner.Signer
import castbridge.core.wallet.Verdict
import castbridge.core.wallet.WalletFormats
import castbridge.core.wallet.WalletRefusal
import java.security.MessageDigest
import java.util.Base64

/**
 * Pourquoi un journal AUTHENTIQUE est jugé IMPOSSIBLE quand on le REJOUE avec les règles du jeu (noms STABLES, comme [castbridge.core.wallet.millions.MillionsImpossible] : un vérificateur
 * Java relira les mêmes vecteurs). L'ordre est celui des contrôles : le premier motif rencontré est rendu.
 */
enum class JournalImpossible {
    /** Autre jeu ou autre version des règles que celles qu'on a. */
    RULES_MISMATCH,
    /** Table impossible : nombre de joueurs hors des règles, place en double, ordinateur qui n'est pas à la table, coup d'une place qui n'existe pas. */
    BAD_TABLE,
    /** Heures qui reculent, ou fin avant le début. */
    BAD_TIMES,
    /** Un coup que les règles du jeu ne savent pas lire. */
    UNDECODABLE_MOVE,
    /** Un coup joué quand la partie était déjà finie par les règles. */
    MOVE_AFTER_END,
    /** Un coup qui n'était pas légal à ce moment (pas à ce joueur de jouer, ou coup interdit). */
    ILLEGAL_MOVE,
    /** Un coup marqué « joué d'office » qui n'est pas celui que les règles auraient joué à la place du joueur. */
    AUTO_MOVE_NOT_FALLBACK,
    /** La fin annoncée n'est pas celle que les coups produisent (autre gagnant, partie encore en cours, abandon sans fautif…). */
    END_MISMATCH,
}

/**
 * Journal de partie `cbg1` : les COUPS, la GRAINE et le RÉSULTAT d'une partie à tour de rôle, signés par l'AUTORITÉ de la partie (la clé d'installation de la TV à la maison, celle du serveur en
 * ligne ; l'interface [Signer] est injectée, la clé privée n'est jamais ici). Même famille et même codec que `cbm1` ([castbridge.core.wallet.millions.MillionsJournal]) mais SON domaine
 * `castbridge-game-journal-v1` : une pièce `cbg1` ne vaut jamais pour un autre format.
 *
 * La signature atteste l'ORIGINE (cette autorité), pas l'honnêteté : celui qui reçoit REJOUE la partie ([impossible]) avec les règles (même code pur que celui de la TV) et refuse tout ce qui
 * n'a pas pu se produire. Contenu : identifiant unique de la partie (128 bits : rejeux refusés), jeu et version des règles, graine, places (`s1`, `s2`…) et celles de l'ordinateur, les coups
 * dans l'ordre (place, coup en texte court, millisecondes depuis le début, joué d'office ou non), la fin, début et fin (heure de la TV), et une CHAÎNE d'empreintes (chaque coup engage tous
 * les précédents, la graine et le jeu) pour qu'un envoi partiel reste vérifiable.
 *
 * **Aucune main** : ni cartes en main, ni pioche, ni état. Les cartes se retrouvent par la graine en rejouant (c'est pourquoi un journal n'est produit QU'À LA FIN de la partie : la graine
 * fixe les mains), et seules les cartes réellement jouées figurent dans les coups. Aucun pseudonyme, aucun jeton de place. Borné : [MAX_ENTRIES] coups, [MAX_PLAYERS] places, [MAX_LENGTH] caractères.
 */
data class GameJournal(
    val kid: String, val gameId: String, val rulesId: String, val rulesVersion: Int, val seed: Long,
    val players: List<String>, val ai: List<String>, val entries: List<Entry>, val end: End, val t0: Long, val t1: Long,
) {
    /** Un coup : la place (index dans [players]), le coup en texte court, le temps depuis le début de la partie (ms) et s'il a été joué d'office. */
    data class Entry(val seat: Int, val move: String, val atMs: Long, val auto: Boolean)

    /** La fin : le motif, l'issue, et le fautif d'un abandon, d'un temps dépassé ou d'une déconnexion. */
    data class End(val reason: EndReason, val outcome: Outcome, val by: PlayerId?)

    private fun hex16(s: String): String = MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8)).take(8).joinToString("") { "%02x".format(it) }

    /** h0 de la chaîne : engage la partie, le jeu, la graine et la table. */
    private fun start(): String = hex16("cbg1-chain|$gameId|$rulesId|$rulesVersion|$seed|${players.joinToString(",")}|${ai.joinToString(",")}")

    /** `h(i) = hex16(SHA-256(h(i-1) | place | coup | ms | d'office))` pour chaque coup ; la dernière est la tête signée. */
    internal fun head(): String {
        var prev = start()
        for (e in entries) prev = hex16("$prev|${e.seat}|${e.move}|${e.atMs}|${if (e.auto) 1 else 0}")
        return prev
    }

    internal fun payload(): Map<String, Any?> = linkedMapOf(
        "kid" to kid, "gameId" to gameId, "rules" to rulesId, "rv" to rulesVersion.toLong(), "seed" to seed,
        "players" to players, "ai" to ai,
        "entries" to entries.map { listOf(it.seat.toLong(), it.move, it.atMs, if (it.auto) 1L else 0L) },
        "end" to linkedMapOf("reason" to end.reason.name, "outcome" to end.outcome.toJson(), "by" to end.by?.id),
        "t0" to t0, "t1" to t1, "head" to head(),
    )

    companion object {
        const val PREFIX = "cbg1"
        const val DOMAIN = "castbridge-game-journal-v1"
        const val MAX_ENTRIES = 1_000
        const val MAX_PLAYERS = 8
        const val MAX_LENGTH = 64 * 1024
        /** Un coup ne peut pas dater de plus de trois jours après le début de la partie. */
        const val MAX_MS = 3L * 24 * 3600 * 1000
        private val KEYS = setOf("kid", "gameId", "rules", "rv", "seed", "players", "ai", "entries", "end", "t0", "t1", "head")
        private val END_KEYS = setOf("reason", "outcome", "by")
        private val RULES_ID = Regex("^[a-z0-9-]{1,32}$")
        private val MOVE = Regex("^[A-Za-z0-9:_,.-]{1,48}$")
        private val SEAT = Regex("^[A-Za-z0-9_-]{1,16}$")
        private val HEX16 = Regex("^[0-9a-f]{16}$")

        /** Le journal d'une partie FINIE du moteur [engine] ; [t0] et [t1] sont l'heure de la TV au début et à la fin. Refuse une partie en cours ou trop longue pour un journal. */
        fun <S, M : GameMove> of(gameId: String, kid: String, engine: TurnEngine<S, M>, t0: Long, t1: Long): GameJournal {
            val r = engine.result ?: throw IllegalArgumentException("la partie n'est pas finie : pas de journal")
            require(WalletFormats.HEX32.matches(gameId)) { "identifiant de partie : 32 chiffres hexadécimaux en minuscules" }
            require(HEX16.matches(kid)) { "identifiant de clé : 16 chiffres hexadécimaux en minuscules" }
            require(t1 >= t0) { "la fin ne précède pas le début" }
            require(engine.players.size <= MAX_PLAYERS) { "plus de $MAX_PLAYERS places" }
            require(engine.log.size <= MAX_ENTRIES) { "${engine.log.size} coups : plus que les $MAX_ENTRIES d'un journal" }
            val entries = engine.log.map { Entry(engine.players.indexOf(it.move.player), engine.rules.encodeMove(it.move), it.atMs, it.auto) }
            return GameJournal(kid, gameId, engine.rules.id, engine.rules.version, engine.seed, engine.players.map { it.id },
                engine.players.filter { it in engine.ai }.map { it.id }, entries, End(r.reason, r.outcome, r.by), t0, t1)
        }

        /** Le [Signer] de la clé d'INSTALLATION de la TV (la même que celle des preuves de TV, mais avec un autre domaine de signature). */
        fun signerOf(install: InstallSigner): Signer = object : Signer {
            override val keyId: String = install.keyId
            override fun sign(message: ByteArray): ByteArray = Base64.getDecoder().decode(install.sign(String(message, Charsets.US_ASCII)))
        }

        /** Signe [journal] : refuse un `kid` qui n'est pas celui de [signer] et tout journal que [verify] refuserait pour sa taille. */
        fun sign(journal: GameJournal, signer: Signer): String {
            require(journal.kid == signer.keyId) { "kid ≠ clé de l'autorité" }
            require(journal.entries.size <= MAX_ENTRIES) { "trop de coups pour un journal" }
            val token = WalletFormats.seal(PREFIX, DOMAIN, journal.payload()) { signer.sign(it) }
            require(token.length <= MAX_LENGTH) { "journal trop long : ${token.length} caractères" }
            return token
        }

        /** Vérifie [token] avec l'anneau des clés d'autorité : lecture stricte, signature, champs, bornes, chaîne. Ne rejoue PAS la partie (voir [impossible]). */
        fun verify(token: String?, ring: KeyRing): Verdict<GameJournal> {
            val o = when (val v = WalletFormats.open(token, PREFIX, DOMAIN, ring, MAX_LENGTH)) { is Verdict.Rejected -> return v; is Verdict.Accepted -> v.value }
            return WalletFormats.guard {
                fun bad(r: WalletRefusal): Nothing = throw WalletFormats.Bad(r)
                val f = WalletFormats.Fields(o.body, KEYS)
                val gameId = f.str("gameId", WalletFormats.HEX32)
                val rules = f.str("rules", RULES_ID)
                val rv = f.long("rv").also { if (it !in 0..1_000_000) bad(WalletRefusal.OUT_OF_BOUNDS) }.toInt()
                val seed = f.long("seed")
                fun seats(k: String): List<String> = f.list(k).map { (it as? String)?.takeIf { s -> SEAT.matches(s) } ?: bad(WalletRefusal.UNREADABLE) }
                val players = seats("players")
                if (players.size > MAX_PLAYERS) bad(WalletRefusal.OUT_OF_BOUNDS)
                if (players.isEmpty() || players.toSet().size != players.size) bad(WalletRefusal.UNREADABLE)
                val ai = seats("ai")
                if (ai.size > players.size || !players.containsAll(ai) || ai.toSet().size != ai.size) bad(WalletRefusal.UNREADABLE)
                val raw = f.list("entries")
                if (raw.size > MAX_ENTRIES) bad(WalletRefusal.OUT_OF_BOUNDS)
                val entries = raw.map { row ->
                    val r = row as? List<*> ?: bad(WalletRefusal.UNREADABLE)
                    if (r.size != 4) bad(WalletRefusal.UNREADABLE)
                    val seat = r[0] as? Long; val move = (r[1] as? String)?.takeIf { MOVE.matches(it) }; val at = r[2] as? Long; val auto = r[3] as? Long
                    if (seat == null || move == null || at == null || auto == null || auto !in 0..1 || seat !in players.indices.map { it.toLong() }) bad(WalletRefusal.UNREADABLE)
                    if (at !in 0..MAX_MS) bad(WalletRefusal.OUT_OF_BOUNDS)
                    Entry(seat.toInt(), move, at, auto == 1L)
                }
                @Suppress("UNCHECKED_CAST") val endMap = o.body["end"] as? Map<String, Any?> ?: bad(WalletRefusal.UNREADABLE)
                if (endMap.keys != END_KEYS) bad(WalletRefusal.UNREADABLE)
                val reason = EndReason.values().firstOrNull { it.name == endMap["reason"] } ?: bad(WalletRefusal.UNREADABLE)
                val by = endMap["by"]?.let { b -> (b as? String)?.takeIf { it in players }?.let(::PlayerId) ?: bad(WalletRefusal.UNREADABLE) }
                @Suppress("UNCHECKED_CAST") val outcomeMap = endMap["outcome"] as? Map<String, Any?> ?: bad(WalletRefusal.UNREADABLE)
                val outcome = strictOutcome(outcomeMap, players) ?: bad(WalletRefusal.UNREADABLE)
                val journal = GameJournal(o.kid, gameId, rules, rv, seed, players, ai, entries, End(reason, outcome, by), f.nonNeg("t0"), f.nonNeg("t1"))
                if (f.str("head", HEX16) != journal.head()) bad(WalletRefusal.UNREADABLE)
                journal
            }
        }

        /** [Outcome] relu SANS tolérance : clés exactes de chaque cas, joueurs de la table, et une partie finie (pas « en cours »). */
        private fun strictOutcome(m: Map<String, Any?>, players: List<String>): Outcome? {
            val keys = when (m["kind"]) { "WINNERS" -> setOf("kind", "winners"); "DRAW" -> setOf("kind"); "SCORES" -> setOf("kind", "scores"); else -> return null }
            if (m.keys != keys) return null
            val o = Outcome.fromJson(m) ?: return null
            return when (o) {
                is Outcome.Winners -> o.takeIf { w -> w.winners.all { it.id in players } && w.winners.toSet().size == w.winners.size }
                is Outcome.Scores -> o.takeIf { s -> s.scores.keys.all { it.id in players } }
                is Outcome.Draw -> o
                is Outcome.InProgress -> null
            }
        }

        /**
         * Le journal est-il IMPOSSIBLE avec [rules] ? On REJOUE la partie : même jeu et même version, table possible, heures qui avancent, chaque coup lisible et légal au moment où il est joué (un coup
         * « d'office » est exactement le coup de repli des règles), aucun coup après la fin, puis la fin annoncée est celle que les coups produisent. Rend le premier motif, dans l'ordre de
         * [JournalImpossible], ou null.
         */
        fun <S, M : GameMove> impossible(journal: GameJournal, rules: GameRules<S, M>): JournalImpossible? {
            if (journal.rulesId != rules.id || journal.rulesVersion != rules.version) return JournalImpossible.RULES_MISMATCH
            val players = runCatching { journal.players.map(::PlayerId) }.getOrNull() ?: return JournalImpossible.BAD_TABLE
            if (players.size !in rules.minPlayers..rules.maxPlayers || players.toSet().size != players.size) return JournalImpossible.BAD_TABLE
            if (journal.ai.any { it !in journal.players }) return JournalImpossible.BAD_TABLE
            if (journal.entries.any { it.seat !in players.indices }) return JournalImpossible.BAD_TABLE
            if (journal.t1 < journal.t0 || journal.entries.zipWithNext().any { (a, b) -> b.atMs < a.atMs }) return JournalImpossible.BAD_TIMES
            var state = rules.initial(journal.seed, players)
            for (e in journal.entries) {
                val player = players[e.seat]
                if (rules.outcome(state).over) return JournalImpossible.MOVE_AFTER_END
                val move = rules.decodeMove(e.move, player) ?: return JournalImpossible.UNDECODABLE_MOVE
                if (move !in rules.legal(state, player)) return JournalImpossible.ILLEGAL_MOVE
                if (e.auto && move != rules.fallbackMove(state, player)) return JournalImpossible.AUTO_MOVE_NOT_FALLBACK
                state = rules.apply(state, move)
            }
            val actual = rules.outcome(state)
            val end = journal.end
            val ok = when (end.reason) {
                EndReason.RULES -> actual.over && end.by == null && end.outcome == actual
                EndReason.ABANDONED -> !actual.over && end.by == null && end.outcome == Outcome.Draw
                EndReason.RESIGNATION, EndReason.DISCONNECTED, EndReason.TIMEOUT -> {
                    val by = end.by
                    val others = players.filter { it != by }
                    !actual.over && by != null && by in players && end.outcome == (if (others.isEmpty()) Outcome.Draw else Outcome.Winners(others)) &&
                        // seul le joueur qui avait la main peut dépasser son temps
                        (end.reason != EndReason.TIMEOUT || by in rules.toMove(state, players))
                }
            }
            return if (ok) null else JournalImpossible.END_MISMATCH
        }
    }
}

/**
 * Les derniers journaux signés gardés par l'autorité (la TV), BORNÉS : au plus [max], le plus ancien est oublié, un journal déjà gardé ne l'est pas deux fois, un jeton trop long est refusé.
 * Garde le jeton signé tel quel (la TV ne le relit pas) et de quoi l'afficher. Thread-safe.
 */
class GameJournalLog(val max: Int = 20) {
    init { require(max >= 1) { "au moins un journal gardé : $max" } }

    data class Item(val gameId: String, val rulesId: String, val endedAtMs: Long, val token: String)

    private val kept = ArrayList<Item>()

    @Synchronized fun add(item: Item) {
        require(WalletFormats.HEX32.matches(item.gameId)) { "identifiant de partie invalide" }
        require(item.token.length <= GameJournal.MAX_LENGTH) { "journal trop long : ${item.token.length} caractères" }
        if (kept.any { it.gameId == item.gameId }) return
        kept += item
        while (kept.size > max) kept.removeAt(0)
    }

    /** Du plus ancien au plus récent. */
    @Synchronized fun items(): List<Item> = kept.toList()
}
