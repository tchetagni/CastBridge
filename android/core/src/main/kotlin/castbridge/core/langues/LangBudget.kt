package castbridge.core.langues

import castbridge.core.lots.LotId
import castbridge.core.lots.LotMeta
import castbridge.core.quiz.Json
import castbridge.core.quiz.Json.int
import castbridge.core.quiz.Json.long
import castbridge.core.quiz.Json.map

/** The « Langues » envelope (6 GB, apart from the 3 GB of the rest of the base) and its split; weights come from `content/langues/budget.json` (single source, also read by tools/content-budget). */
class LangBudget(root: Map<String, Any?>) {
    val envelopeMb = root.int("envelopeMb")!!
    val restOfBaseMb = root.int("restOfBaseMb")!!
    val textLotMax = root.long("textLotMaxBytes")!!
    val mediaLotMax = root.long("mediaLotMaxBytes")!!
    private val langPct = pct(root.map("languageWeightsPercent")!!)
    val transversalPct = root.int("transversalPercent")!!
    val reservePct = root.int("reservePercent")!!
    private val levelPct = root.map("levelWeightsPercent")!!.mapValues { pct(it.value as Map<String, Any?>) }
    private val profile = root.map("profileOf")!!.mapValues { it.value as String }
    private val textKb = pct(root.map("textKbPerLanguageLevel")!!)
    val mediaSplit = pct(root.map("mediaSplitPercent")!!)

    private fun pct(m: Map<String, Any?>) = m.mapValues { (it.value as Number).toInt() }

    /** Budget of one (language, level) cell, in KB. Media = envelope cell − text; [mediaByKind] splits it. */
    data class Cell(val lang: Lang, val level: LangLevel, val totalKb: Long, val textKb: Long) {
        val mediaKb get() = totalKb - textKb
    }

    fun problems(): List<String> {
        val e = ArrayList<String>()
        if (langPct.values.sum() + transversalPct + reservePct != 100) e += "langues + transversal + réserve = ${langPct.values.sum() + transversalPct + reservePct} % (100 attendus)"
        for ((p, m) in levelPct) if (m.values.sum() != 100) e += "profil $p : niveaux = ${m.values.sum()} % (100 attendus)"
        if (mediaSplit.values.sum() != 100) e += "répartition média = ${mediaSplit.values.sum()} % (100 attendus)"
        if (Lang.entries.any { it.code !in langPct }) e += "une langue n'a pas de poids"
        return e
    }

    fun languageKb(l: Lang): Long = envelopeMb * 1024L * langPct.getValue(l.code) / 100
    fun transversalKb(): Long = envelopeMb * 1024L * transversalPct / 100
    fun reserveKb(): Long = envelopeMb * 1024L * reservePct / 100

    fun cell(l: Lang, lv: LangLevel): Cell {
        val p = profile.getValue(l.code)
        return Cell(l, lv, languageKb(l) * levelPct.getValue(p).getValue(lv.name) / 100, textKb.getValue(p).toLong())
    }
    fun cells(): List<Cell> = Lang.entries.flatMap { l -> LangLevel.entries.map { cell(l, it) } }

    /** Number of media lots (≤ 100 MB each) needed for a cell, at least 1 when it has media. */
    fun mediaLots(c: Cell): Int = ((c.mediaKb * 1024 + mediaLotMax - 1) / mediaLotMax).toInt().coerceAtLeast(if (c.mediaKb > 0) 1 else 0)

    /** The budget check over real lots (the language ones; other features are ignored: they belong to the 3 GB of the rest of the base). */
    data class Report(val errors: List<String>, val perLangBytes: Map<Lang, Long>, val totalBytes: Long)

    fun check(lots: List<LotMeta>): Report {
        val e = ArrayList<String>(problems())
        val per = HashMap<Lang, Long>()
        for (m in lots) {
            val parts = LangLots.parse(m.id.scope)?.takeIf { LangLots.isLanguage(m.id) } ?: continue
            val cap = if (LangLots.isMedia(m.id)) mediaLotMax else textLotMax
            if (m.bytes > cap) e += "lot ${m.id.feature}:${m.id.scope} : ${m.bytes} octets > plafond $cap"
            per.merge(parts.target, m.bytes, Long::plus)
        }
        for ((l, b) in per) if (b > languageKb(l) * 1024) e += "${l.code} : ${b / 1048576} Mo > part de ${languageKb(l) / 1024} Mo"
        val total = per.values.sum()
        if (total > envelopeMb.toLong() * 1048576) e += "total langues ${total / 1048576} Mo > enveloppe $envelopeMb Mo"
        return Report(e, per, total)
    }

    companion object { fun parse(text: String) = LangBudget(Json.obj(text)) }
}

/** What a learner follows: languages (target + start), where they are. */
data class LearnerLang(val target: Lang, val source: Lang, val level: LangLevel)

/** Which language lots the phone keeps / sends to the TV. Pure and deterministic, like `LotPlanner`. */
object LangPlanner {
    data class Plan(val selected: List<LotMeta>, val skipped: List<Pair<LotMeta, String>>, val usedBytes: Long)

    /** Priority of a lot for a learner: 0 = current level, then next level, previous level, +2, −1 (closest first); null = not theirs (other target/source). */
    fun rank(l: LearnerLang, id: LotId): Int? {
        val p = LangLots.parse(id.scope) ?: return null
        if (p.target != l.target || p.source != l.source) return null
        val d = p.level.ordinal - l.level.ordinal
        return if (d >= 0) d * 2 else -d * 2 - 1   // 0, next=2, previous=1, +2=4, −2=3 …
    }

    /** Text lots for the TV: [budgetBytes] is what is left of the 10 MB (not per lot). Media lots are NOT planned here, they are copied on request. */
    fun textForTv(learner: LearnerLang, catalog: List<LotMeta>, budgetBytes: Long): Plan = pick(learner, catalog.filter { it.id.feature == LangLots.FEATURE }, budgetBytes)

    /** Media lots to keep in the phone's languages space, or to copy to the TV's library volume when [budgetBytes] is the free space allowed there. */
    fun mediaFor(learner: LearnerLang, catalog: List<LotMeta>, budgetBytes: Long): Plan = pick(learner, catalog.filter { it.id.feature == LangLots.MEDIA_FEATURE }, budgetBytes)

    private fun pick(l: LearnerLang, lots: List<LotMeta>, budget: Long): Plan {
        val mine = lots.mapNotNull { m -> rank(l, m.id)?.let { m to it } }.sortedWith(compareBy({ it.second }, { it.first.bytes }, { it.first.id.scope }))
        val sel = ArrayList<LotMeta>(); val skip = ArrayList<Pair<LotMeta, String>>(); var used = 0L
        for ((m, _) in mine) if (used + m.bytes <= budget) { sel += m; used += m.bytes } else skip += m to "ne tient pas : il manque ${(used + m.bytes - budget + 1023) / 1024} Ko"
        return Plan(sel, skip, used)
    }

    /** A media lot is playable on the TV only when its text twin is installed there too (the text lot says which media it references). */
    fun playableMedia(installedText: Set<LotId>, installedMedia: Set<LotId>): Set<LotId> =
        installedMedia.filter { LangLots.parse(it.scope)?.let { p -> LangLots.textId(p) in installedText } == true }.toSet()
}
