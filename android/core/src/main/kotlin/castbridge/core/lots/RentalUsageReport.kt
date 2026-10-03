package castbridge.core.lots

import castbridge.core.owner.SafeFile
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The usage statement `castbridge-rental-usage-v1` ([RentalLedger.usageReport]) on the phone's side: parsed line by line (a malformed line is ignored and counted, never fatal), merged
 * monotonically (the minutes of a contract only ever go up, the furthest state wins), and put into words for the screen. It carries an installation id (16 hex) and contracts: no licence,
 * no seat, no person, no profile.
 *
 * Hook (not built at the pilot): the statement will be signed by the installation signer (W6, w6-12) with a `sig=` field at its end; a line starting with `sig=` is skipped today (neither
 * a contract nor counted as ignored), so a signed statement is read by this code unchanged.
 */
object RentalUsageReport {
    const val HEADER = "castbridge-rental-usage-v1"
    private val INSTALL = Regex("[0-9a-f]{16}")
    private val CONTRACT = Regex("[A-Za-z0-9._-]{1,64}@[0-9]{1,18}")
    private val NUMBER = Regex("[0-9]{1,18}")
    private val UNITS = setOf("hours", "days", "trial", "unknown")
    private val STATES = RentalState.values().map { it.name }.toSet() + "ORPHAN"
    private val KEYS = setOf("contract", "unit", "used", "max", "state", "reason", "endsAt", "at")

    /** One contract: [reason] is null when none (`-`), [used] and [max] are minutes (max 0 = no budget), [endsAt] and [at] are the TV's milliseconds. */
    data class Line(val contract: String, val unit: String, val used: Long, val max: Long, val state: String, val reason: String?, val endsAt: Long, val at: Long)
    data class Report(val install: String, val lines: List<Line>, val ignored: Int)

    /** The statement, or null if [text] is not one (wrong header, no valid installation id). */
    fun parse(text: String): Report? {
        val all = text.lines().map { it.trimEnd('\r') }
        if (all.firstOrNull() != HEADER) return null
        val install = all.getOrNull(1)?.takeIf { it.startsWith("install=") }?.substring(8)?.takeIf { INSTALL.matches(it) } ?: return null
        var ignored = 0
        val lines = ArrayList<Line>()
        for (l in all.drop(2)) {
            if (l.isEmpty() || l.startsWith("sig=")) continue
            val parsed = line(l)
            if (parsed == null) ignored++ else lines += parsed
        }
        val merged = lines.groupBy { it.contract }.values.map { g -> g.reduce(::mergeLine) }.sortedBy { it.contract }
        return Report(install, merged, ignored)
    }

    private fun line(l: String): Line? {
        val parts = l.split('|').map { it.split('=', limit = 2) }
        if (parts.any { it.size != 2 } || parts.size != KEYS.size) return null
        val m = parts.associate { it[0] to it[1] }
        if (m.keys != KEYS || parts.map { it[0] }.toSet().size != KEYS.size) return null
        fun num(k: String) = m[k]!!.takeIf { NUMBER.matches(it) }?.toLongOrNull()
        val reason = m["reason"]!!
        if (!CONTRACT.matches(m["contract"]!!) || m["unit"]!! !in UNITS || m["state"]!! !in STATES || reason !in setOf("-", "DATE", "USAGE")) return null
        return Line(m["contract"]!!, m["unit"]!!, num("used") ?: return null, num("max") ?: return null, m["state"]!!, reason.takeIf { it != "-" }, num("endsAt") ?: return null, num("at") ?: return null)
    }

    private fun rank(state: String) = when (state) {
        "NOT_STARTED" -> 0; "ACTIVE", "SUSPENDED", "OVER_LIMIT" -> 1; "GRACE" -> 2; "EXPIRED" -> 3; else -> 4          // ORPHAN: the activation is gone, the furthest state
    }

    /** Two readings of one contract: [Line.used] and [Line.max] never go down, the furthest state wins (the later statement on a tie), the latest [Line.at] is kept. */
    private fun mergeLine(a: Line, b: Line): Line {
        val win = if (rank(b.state) >= rank(a.state) && (rank(b.state) > rank(a.state) || b.at >= a.at)) b else a
        val lose = if (win === a) b else a
        return win.copy(used = maxOf(a.used, b.used), max = maxOf(a.max, b.max), at = maxOf(a.at, b.at), unit = if (win.unit == "unknown") lose.unit else win.unit,
            endsAt = if (win.endsAt == 0L) lose.endsAt else win.endsAt)
    }

    /** [old] and [new] of ONE installation: every contract of either, each merged by [mergeLine]. */
    fun merge(old: Report, new: Report): Report {
        require(old.install == new.install) { "two statements of different installations are never merged" }
        val lines = (old.lines + new.lines).groupBy { it.contract }.values.map { g -> g.reduce(::mergeLine) }.sortedBy { it.contract }
        return Report(old.install, lines, new.ignored)
    }

    /** The statement as text (the very format the TV wrote). */
    fun toText(r: Report): String = (listOf(HEADER, "install=${r.install}") + r.lines.map {
        "contract=${it.contract}|unit=${it.unit}|used=${it.used}|max=${it.max}|state=${it.state}|reason=${it.reason ?: "-"}|endsAt=${it.endsAt}|at=${it.at}"
    }).joinToString("\n", postfix = "\n")

    /** One French line per contract for the screen: the unit tells the nature (hours of use, or days that pass anyway), never a conversion. */
    fun format(r: Report, zone: ZoneId = ZoneId.systemDefault()): List<String> {
        if (r.lines.isEmpty()) return listOf("Aucune location sur cette TV.")
        val day = DateTimeFormatter.ofPattern("dd/MM").withZone(zone)
        return r.lines.map { l ->
            val p = l.contract.substringBefore('@'); val end = day.format(Instant.ofEpochMilli(l.endsAt))
            when {
                l.state == "ORPHAN" -> "$p : activation retirée · ${span(l.used)} d'utilisation comptée(s)"
                l.state == "NOT_STARTED" -> "$p : pas encore commencée" + if (l.unit == "hours") " · ${span(l.max)} d'utilisation" else ""
                l.unit == "hours" && l.state == "EXPIRED" ->
                    if (l.reason == "USAGE") "$p : vos ${span(l.max)} d'utilisation sont épuisées" else "$p : vos heures non utilisées ont expiré le $end (fin du test gratuit)"
                l.unit == "hours" -> "$p : ${span(maxOf(0L, l.max - l.used))} d'utilisation restante(s) sur ${span(l.max)} · à utiliser avant le $end"
                l.unit == "days" && l.state == "EXPIRED" -> "$p : location en jours terminée le $end"
                l.unit == "days" -> "$p : location en jours, jusqu'au $end · ${span(l.used)} d'utilisation mesurée"
                else -> "$p : ${span(l.used)} d'utilisation mesurée"
            }
        }
    }

    internal fun span(minutes: Long): String = when {
        minutes < 60 -> "$minutes min"
        minutes % 60 == 0L -> "${minutes / 60} h"
        else -> "${minutes / 60} h %02d".format(minutes % 60)
    }
}

/**
 * The statements kept on the phone, one file per installation (`usage-<install>.txt`, written by [SafeFile] so a power cut keeps the last good copy as `.bak`): [put] merges into what the
 * file already holds, so an older statement never lowers a count. [export] is the text to share: every statement, one after the other.
 */
class RentalReportStore(private val dir: File) {
    private fun file(install: String) = File(dir, "usage-$install.txt")
    private fun valid(t: String) = RentalUsageReport.parse(t) != null
    private fun read(f: File): RentalUsageReport.Report? = SafeFile.read(f, ::valid)?.let { RentalUsageReport.parse(it.text) }

    @Synchronized fun put(r: RentalUsageReport.Report) {
        val f = file(r.install)
        val merged = read(f)?.let { RentalUsageReport.merge(it, r) } ?: r
        SafeFile.write(f, RentalUsageReport.toText(merged), ::valid)
    }

    @Synchronized fun all(): List<RentalUsageReport.Report> =
        (dir.listFiles().orEmpty().filter { it.isFile && Regex("usage-[0-9a-f]{16}\\.txt").matches(it.name) }.sortedBy { it.name }).mapNotNull { read(it) }

    fun export(): String = all().joinToString("") { RentalUsageReport.toText(it) }
}
