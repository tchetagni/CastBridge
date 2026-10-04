package castbridge.core.wallet.millions

import castbridge.core.owner.KeyRing
import castbridge.core.owner.TvClock
import castbridge.core.wallet.Verdict
import castbridge.core.wallet.WalletFormats
import castbridge.core.wallet.WalletRefusal
import java.time.LocalDate
import java.time.YearMonth

/** Une question du Défi, telle que le pack la porte. Le 50:50 est DÉSIGNÉ par le pack ([fifty] : les deux mauvaises réponses retirées) pour que le serveur puisse vérifier le journal. */
data class MillionsQuestion(val qid: String, val text: String, val choices: List<String>, val correct: Int, val fifty: List<Int>)

/** Ce qu'est une « partie gagnée » pour les plafonds (conception W22 § 13.9, D-W22-23). */
enum class WinDefinition {
    GAIN_GT_STAKE, GAIN_GT_ZERO;
    fun isWin(gain: Long, stake: Long): Boolean = when (this) { GAIN_GT_STAKE -> gain > stake; GAIN_GT_ZERO -> gain > 0 }
}

/** Plafonds de parties GAGNÉES : par jour, par semaine (lundi-dimanche), par mois civil (heure d'Africa/Douala). */
data class WinLimits(val perDay: Int, val perWeek: Int, val perMonth: Int)

/** Compteurs de parties gagnées tenus par le serveur, avec la période à laquelle ils se rapportent (`AAAA-MM-JJ`, lundi de la semaine `AAAA-MM-JJ`, `AAAA-MM`). */
data class ServerCounts(val dayKey: String, val day: Int, val weekKey: String, val week: Int, val monthKey: String, val month: Int)

/**
 * Pack du Défi `cbk1`, reçu du serveur et SIGNÉ par la clé « portefeuille » de l'API (domaine `castbridge-millions-pack-v1`, même enveloppe que `cbw1` : [WalletFormats]). La TV ne signe JAMAIS un pack :
 * il n'y a pas de `sign` en production (les tests fabriquent les packs). Le pack porte tout ce que le serveur décide sans livrer d'APK : échelle, mise, temps de réponse, plafonds, définition d'une
 * partie gagnée, compteurs du serveur, et les 15 niveaux de 1 à 20 questions avec leurs bonnes réponses et leur 50:50.
 */
data class MillionsPack(
    val kid: String, val packId: String, val id: String, val at: Long, val from: Long, val until: Long, val ladderVersion: Long, val ladder: MillionsLadder, val timeSec: Int,
    val maxPlaysPerDay: Int, val limits: WinLimits, val winDefinition: WinDefinition, val mw: ServerCounts, val levels: List<List<MillionsQuestion>>,
) {
    private val index: Map<String, Pair<Int, MillionsQuestion>> by lazy { buildMap { levels.forEachIndexed { l, qs -> qs.forEach { put(it.qid, (l + 1) to it) } } } }

    /** Niveau (1..15) et question d'identifiant [qid], ou null. */
    fun locate(qid: String): Pair<Int, MillionsQuestion>? = index[qid]

    /** Tirage déterministe (vérifiable par le serveur) : la première question du [level] (1..15) qui n'est pas dans [played], dans l'ordre du pack ; null si le niveau est épuisé. */
    fun next(level: Int, played: Set<String>): MillionsQuestion? = levels.getOrNull(level - 1)?.firstOrNull { it.qid !in played }

    internal fun payload(): Map<String, Any?> = linkedMapOf(
        "kid" to kid, "packId" to packId, "id" to id, "at" to at, "from" to from, "until" to until, "lv" to ladderVersion,
        "ladder" to ladder.values, "stake" to ladder.stake, "timeSec" to timeSec, "maxPlays" to maxPlaysPerDay,
        "limits" to linkedMapOf("d" to limits.perDay, "w" to limits.perWeek, "m" to limits.perMonth),
        "winDef" to winDefinition.name,
        "mw" to linkedMapOf("kd" to mw.dayKey, "d" to mw.day, "kw" to mw.weekKey, "w" to mw.week, "km" to mw.monthKey, "m" to mw.month),
        "levels" to levels.map { qs -> qs.map { listOf(it.qid, it.text, it.choices, it.correct, it.fifty) } },
    )

    companion object {
        const val PREFIX = "cbk1"
        const val DOMAIN = "castbridge-millions-pack-v1"
        /** Au plus 20 questions × 15 niveaux × (texte 400 + 4 choix de 120 + enveloppe) en base64 : borne dure ; un pack réel fait ≈ 75 Ko. */
        const val MAX_LENGTH = 450_000
        const val MAX_LIFE_MS = 14L * 24 * 3600 * 1000
        const val MAX_PER_LEVEL = 20
        private val KEYS = setOf("kid", "packId", "id", "at", "from", "until", "lv", "ladder", "stake", "timeSec", "maxPlays", "limits", "winDef", "mw", "levels")
        private val ASCII16 = Regex("^[ -~]{1,16}$")

        /**
         * Vérifie [token] : lecture stricte, clé de [ring], signature, puis champs et bornes, puis identité ([identity] : la TV ne reçoit que SON pack) et validité selon l'heure de la TV
         * ([clock].now([wallNowMs]) : jamais l'heure murale seule ; un retour en arrière de l'horloge ne rouvre rien).
         */
        fun verify(token: String?, ring: KeyRing, identity: String, clock: TvClock, wallNowMs: Long): Verdict<MillionsPack> {
            val o = when (val v = WalletFormats.open(token, PREFIX, DOMAIN, ring, MAX_LENGTH)) { is Verdict.Rejected -> return v; is Verdict.Accepted -> v.value }
            val v = WalletFormats.guard { parse(o) }
            if (v !is Verdict.Accepted) return v
            val p = v.value
            if (p.id != identity) return Verdict.Rejected(WalletRefusal.OTHER_TV)
            if (p.until - p.from > MAX_LIFE_MS) return Verdict.Rejected(WalletRefusal.TOO_LONG_LIFE)
            val now = clock.now(wallNowMs)
            if (p.from - WalletFormats.SKEW_MS > now) return Verdict.Rejected(WalletRefusal.NOT_YET_VALID)
            if (now >= p.until) return Verdict.Rejected(WalletRefusal.EXPIRED)
            return v
        }

        private fun bad(r: WalletRefusal = WalletRefusal.OUT_OF_BOUNDS): Nothing = throw WalletFormats.Bad(r)

        private fun date(s: String): LocalDate = runCatching { LocalDate.parse(s) }.getOrNull()?.takeIf { it.toString() == s } ?: bad()
        private fun month(s: String): YearMonth = runCatching { YearMonth.parse(s) }.getOrNull()?.takeIf { it.toString() == s } ?: bad()

        private fun question(row: Any?, used: MutableSet<String>): MillionsQuestion {
            val r = row as? List<*> ?: bad(WalletRefusal.UNREADABLE)
            if (r.size != 5) bad(WalletRefusal.UNREADABLE)
            val qid = (r[0] as? String)?.takeIf { WalletFormats.ID.matches(it) } ?: bad(WalletRefusal.UNREADABLE)
            val text = r[1] as? String ?: bad(WalletRefusal.UNREADABLE)
            val choices = (r[2] as? List<*>)?.map { it as? String ?: bad(WalletRefusal.UNREADABLE) } ?: bad(WalletRefusal.UNREADABLE)
            val correct = r[3] as? Long ?: bad(WalletRefusal.UNREADABLE)
            val fifty = (r[4] as? List<*>)?.map { it as? Long ?: bad(WalletRefusal.UNREADABLE) } ?: bad(WalletRefusal.UNREADABLE)
            if (text.isBlank() || text.length > 400) bad()
            if (choices.size != 4 || choices.any { it.isBlank() || it.length > 120 } || choices.toSet().size != 4) bad()
            if (correct !in 0..3) bad()
            if (fifty.size != 2 || fifty[0] == fifty[1] || fifty.any { it !in 0..3 || it == correct }) bad()
            if (!used.add(qid)) bad()
            return MillionsQuestion(qid, text, choices, correct.toInt(), fifty.map { it.toInt() })
        }

        private fun parse(o: WalletFormats.Opened): MillionsPack {
            val f = WalletFormats.Fields(o.body, KEYS)
            val id = f.str("id", WalletFormats.ID)
            val packId = f.str("packId", WalletFormats.HEX32)
            val at = f.nonNeg("at"); val from = f.nonNeg("from"); val until = f.nonNeg("until"); val lv = f.nonNeg("lv")
            if (until <= from) bad()
            val values = f.list("ladder").map { it as? Long ?: bad(WalletRefusal.UNREADABLE) }
            val ladder = MillionsLadder(values, f.amount("stake", min = 1))
            if (ladder.validate() != null) bad()
            val timeSec = f.long("timeSec").also { if (it !in 5..120) bad() }.toInt()
            val maxPlays = f.long("maxPlays").also { if (it !in 1..100) bad() }.toInt()
            val lim = f.obj("limits", setOf("d", "w", "m"))
            val limits = WinLimits(lim.long("d").toInt(), lim.long("w").toInt(), lim.long("m").toInt())
            if (limits.perDay !in 1..1000 || limits.perWeek !in 1..1000 || limits.perMonth !in 1..1000 || limits.perDay > limits.perWeek || limits.perWeek > limits.perMonth) bad()
            val def = WinDefinition.values().firstOrNull { it.name == o.body["winDef"] } ?: bad(WalletRefusal.UNREADABLE)
            val m = f.obj("mw", setOf("kd", "d", "kw", "w", "km", "m"))
            val mw = ServerCounts(m.str("kd", ASCII16), m.long("d").toInt(), m.str("kw", ASCII16), m.long("w").toInt(), m.str("km", ASCII16), m.long("m").toInt())
            if (mw.day !in 0..100_000 || mw.week !in 0..100_000 || mw.month !in 0..100_000) bad()
            date(mw.dayKey); date(mw.weekKey); month(mw.monthKey)
            val raw = f.list("levels")
            if (raw.size != MillionsLadder.QUESTIONS) bad()
            val used = HashSet<String>()
            val levels = raw.map { lvl ->
                val rows = lvl as? List<*> ?: bad(WalletRefusal.UNREADABLE)
                if (rows.isEmpty() || rows.size > MAX_PER_LEVEL) bad()
                rows.map { question(it, used) }
            }
            return MillionsPack(o.kid, packId, id, at, from, until, lv, ladder, timeSec, maxPlays, limits, def, mw, levels)
        }
    }
}
