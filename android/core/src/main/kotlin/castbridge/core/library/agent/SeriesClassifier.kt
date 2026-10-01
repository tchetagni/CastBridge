package castbridge.core.library.agent

/**
 * Puts the episodes of a series in a folder per title and a sub-folder per season on the TV (« Prison Break / Saison 01 »), from the file NAMES only
 * (the same [NameParser] as the library assistant; nothing is read from the content). The TV's folders are virtual (an index: no file is moved or copied,
 * a name stays unique), so classifying is instantaneous and reversible: [undo] puts the files back at the root.
 * Only what is sure is classified: a clear episode marker, a non-empty title, enough confidence. Anything else stays where it is.
 */
object SeriesClassifier {
    const val MIN_CONFIDENCE = 0.7

    data class Move(val name: String, val folder: String)

    /** « Prison Break/Saison 01 », or « Titre » alone when the season is unknown (anime numbered 1045…), or null when the file is not a sure episode. */
    fun folderFor(fileName: String, folder: String = "", fr: Boolean = true): String? {
        val p = NameParser.parse(fileName, folder)
        return folderOf(p, p.title, fr)
    }

    private fun folderOf(p: Parsed, title: String, fr: Boolean): String? {
        if (p.kind != Kind.SERIES || p.media != Media.VIDEO && p.media != Media.SUBTITLE) return null
        if (p.episode == null && p.date == null || p.confidence < MIN_CONFIDENCE) return null
        val t = clean(title).ifBlank { return null }
        if (p.date != null) return "$t/${if (fr) "Saison" else "Season"} ${p.date.take(4)}"
        val s = p.season ?: return t
        return "$t/${if (fr) "Saison" else "Season"} ${"%02d".format(s)}"
    }

    /**
     * What to do for a list of (name, current folder): only the files still at the root, grouped by series so that « Prison Break », « Prison.Break »,
     * « PrisonBreak » and « prison break (2005) » are ONE folder spelled like the most frequent spelling. Returns nothing for a name already classified
     * (never re-shuffles what the user organised).
     *
     * Two series of the same name are never merged: when the library holds two different years (« The Flash (1990) », « The Flash (2014) », « Doctor Who »
     * 1963 / 2005), or a year-less file whose season and episode already exist under a year, each year gets its own folder « Titre (année) ».
     * With [aliases] (optional, [SeriesAliases.builtin]) a translated title found in the same library joins the original one.
     */
    fun plan(files: List<Pair<String, String>>, fr: Boolean = true, aliases: SeriesAliases = SeriesAliases.NONE): List<Move> {
        val parsed = files.map { (n, f) -> Triple(n, f, NameParser.parse(n, f)) }
        val series = parsed.filter { it.third.kind == Kind.SERIES && it.third.title.isNotBlank() }
        val byName = series.groupBy { aliases.groupKey(it.third.title) }
        // a folder key per file: the series key, plus the year when the same title exists under several years
        val keyOf = HashMap<String, String>()   // file name + folder -> folder key
        val spellingOf = HashMap<String, String>()
        for ((gk, members) in byName) {
            val years = members.mapNotNull { it.third.year }.distinct()
            val yearless = members.filter { it.third.year == null }
            val overlap = years.size == 1 && yearless.any { y -> members.any { m -> m.third.year != null && m.third.season == y.third.season && m.third.episode == y.third.episode && y.third.episode != null } }
            val split = years.size >= 2 || overlap
            for (m in members) keyOf[m.first + "\u0000" + m.second] = if (split) gk + "#" + (m.third.year ?: "") else gk
        }
        val bySubgroup = series.groupBy { keyOf[it.first + "\u0000" + it.second]!! }
        for ((k, v) in bySubgroup) spellingOf[k] = v.groupingBy { it.third.title }.eachCount().maxByOrNull { it.value }!!.key
        return parsed.mapNotNull { (n, f, p) ->
            if (f.isNotEmpty()) return@mapNotNull null
            val k = keyOf[n + "\u0000" + f]
            val split = k != null && k.contains('#')
            val base = k?.let { spellingOf[it] } ?: p.title
            val title = if (split && p.year != null) "$base (${p.year})" else base
            val folder = folderOf(p, title, fr) ?: return@mapNotNull null
            Move(n, folder)
        }
    }

    /** The files put back at the root (the user changed their mind). */
    fun undo(moves: List<Move>): List<Move> = moves.map { Move(it.name, "") }

    private fun clean(s: String) = s.map { if (it in "\\/:*?\"<>|" || it.code < 0x20) ' ' else it }.joinToString("").replace(Regex("\\s+"), " ").trim().trim('.').take(80)
}
