package castbridge.core.parental.tab

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class LineStyle { TITLE, HEADING, BODY, SMALL }
data class DocLine(val text: String, val style: LineStyle = LineStyle.BODY)

/**
 * The content of a shared summary, as lines, BEFORE any format: the text share and the PDF use the same lines (so that the PDF, drawn by Android's
 * PdfDocument, only paginates and paints what is tested here). Nothing is uploaded: the phone hands the result to the share sheet on the parent's action.
 */
object ReportDocument {
    fun build(summaries: List<Pair<String, PeriodSummary>>, previous: Map<String, PeriodSummary>, tvName: String?, generatedAt: Long, zone: ZoneId, supervisionLine: String): List<DocLine> {
        val out = ArrayList<DocLine>()
        val p = summaries.firstOrNull()?.second?.period
        out += DocLine("CastBridge — Rapport parental", LineStyle.TITLE)
        out += DocLine((tvName?.let { "TV : $it · " } ?: "") + "Période : ${p?.label() ?: "—"} · généré le ${stamp(generatedAt, zone)}", LineStyle.SMALL)
        out += DocLine(supervisionLine, LineStyle.BODY)
        out += DocLine("MESURÉ = compté par CastBridge-TV. MEILLEUR EFFORT = autres applications, estimé. INDISPONIBLE = non mesuré (jamais affiché comme 0).", LineStyle.SMALL)
        for ((name, s) in summaries) {
            out += DocLine(name, LineStyle.HEADING)
            out += DocLine(s.coverageText(), LineStyle.SMALL)
            out += DocLine("CastBridge-TV (MESURÉ) : ${s.measured.text()}  — vidéos ${s.play.text()}, jeux et Apprendre ${s.games.text()}, téléchargements ${s.downloads.text()}")
            out += DocLine("Autres applications (${s.otherApps.quality.label}) : ${if (s.otherApps.value != null) "~" + Fmt.min(s.otherApps.value) else "indisponible"}" + (s.otherApps.note?.let { " — $it" } ?: ""))
            out += DocLine("Blocages : ${s.blocks.text(Long::toString)} · tentatives de déverrouillage : ${s.unlockAttempts.text(Long::toString)} · alertes de manipulation : ${s.tamper.text(Long::toString)}")
            previous[name]?.let { out += DocLine(Trends.measuredDelta(s, it).text()) }
            for (a in s.apps.take(5)) out += DocLine("  • ${a.label} : ~${Fmt.min(a.min)} (meilleur effort)")
            if (s.missingDays.isNotEmpty()) out += DocLine("Jours sans rapport : ${s.missingDays.joinToString(", ") { it.toString() }}", LineStyle.SMALL)
        }
        out += DocLine("Données locales : elles restent sur ce téléphone et sur la TV. Rien n'est envoyé sur Internet.", LineStyle.SMALL)
        return out
    }

    fun toText(lines: List<DocLine>): String = lines.joinToString("\n") { l -> when (l.style) { LineStyle.TITLE -> l.text.uppercase() + "\n"; LineStyle.HEADING -> "\n## " + l.text; else -> l.text } }

    fun stamp(ts: Long, z: ZoneId): String = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(Instant.ofEpochMilli(ts).atZone(z))
}

/** Pages of a PDF from lines: width in characters decides the wrapping, [linesPerPage] the page break. Android only draws the result. */
object PdfLayout {
    data class Page(val lines: List<DocLine>)

    fun paginate(lines: List<DocLine>, charsPerLine: Int = 78, linesPerPage: Int = 46): List<Page> {
        val wrapped = ArrayList<DocLine>()
        for (l in lines) {
            val w = when (l.style) { LineStyle.TITLE -> charsPerLine / 2; LineStyle.HEADING -> charsPerLine * 3 / 4; LineStyle.SMALL -> charsPerLine + 12; else -> charsPerLine }
            if (l.style == LineStyle.HEADING) wrapped += DocLine("", LineStyle.SMALL)
            wrapText(l.text, w).forEach { wrapped += DocLine(it, l.style) }
        }
        return wrapped.chunked(linesPerPage).map(::Page).ifEmpty { listOf(Page(emptyList())) }
    }

    fun wrapText(t: String, width: Int): List<String> {
        if (t.length <= width) return listOf(t)
        val out = ArrayList<String>(); var cur = StringBuilder()
        for (w in t.split(' ')) {
            if (cur.isNotEmpty() && cur.length + 1 + w.length > width) { out += cur.toString(); cur = StringBuilder() }
            if (w.length > width) { w.chunked(width).forEach { c -> if (cur.isNotEmpty()) { out += cur.toString(); cur = StringBuilder() }; cur.append(c) }; continue }
            if (cur.isNotEmpty()) cur.append(' ')
            cur.append(w)
        }
        if (cur.isNotEmpty()) out += cur.toString()
        return out
    }
}

/** CSV of the detailed events (RFC 4180, UTF-8 with « ; » for the French spreadsheets). Cells starting with = + - @ are neutralised (formula injection). */
object CsvExport {
    const val SEP = ';'
    val HEADER = listOf("date", "heure", "tv", "profil", "type", "titre", "duree_min", "score", "qualite", "detail")

    fun build(events: List<ActivityEvent>, zone: ZoneId, maxRows: Int = 50_000, profileName: (ActivityEvent) -> String): String {
        val sb = StringBuilder(HEADER.joinToString(SEP.toString())).append("\r\n")
        val df = DateTimeFormatter.ofPattern("yyyy-MM-dd"); val tf = DateTimeFormatter.ofPattern("HH:mm:ss")
        for (e in events.sortedBy { it.ts }.take(maxRows)) {
            val z = Instant.ofEpochMilli(e.ts).atZone(zone)
            sb.append(listOf(df.format(z), tf.format(z), e.tv, profileName(e), e.type.label, e.title, e.durMin?.toString() ?: "", e.score ?: "", e.quality.label, e.detail ?: "")
                .joinToString(SEP.toString()) { cell(it) }).append("\r\n")
        }
        return sb.toString()
    }

    fun cell(s: String): String {
        var v = s.replace("\r", " ").replace("\n", " ")
        if (v.isNotEmpty() && v[0] in "=+-@\t") v = "'$v"
        return if (v.any { it == SEP || it == '"' }) "\"" + v.replace("\"", "\"\"") + "\"" else v
    }
}
