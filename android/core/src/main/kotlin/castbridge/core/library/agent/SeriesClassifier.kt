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
        if (p.episode == null || p.confidence < MIN_CONFIDENCE) return null
        val t = clean(title).ifBlank { return null }
        val s = p.season ?: return t
        return "$t/${if (fr) "Saison" else "Season"} ${"%02d".format(s)}"
    }

    /**
     * What to do for a list of (name, current folder): only the files still at the root, grouped by title so that « Prison Break » is spelled the same in
     * every file (the most frequent spelling wins). Returns nothing for a name already classified (never re-shuffles what the user organised).
     */
    fun plan(files: List<Pair<String, String>>, fr: Boolean = true): List<Move> {
        val parsed = files.map { (n, f) -> Triple(n, f, NameParser.parse(n, f)) }
        val bySeries = parsed.filter { it.third.kind == Kind.SERIES && it.third.title.isNotBlank() }.groupBy { it.third.titleKey }
        val spelling = bySeries.mapValues { (_, v) -> v.groupingBy { it.third.title }.eachCount().maxByOrNull { it.value }!!.key }
        return parsed.mapNotNull { (n, f, p) ->
            if (f.isNotEmpty()) return@mapNotNull null
            val folder = folderOf(p, spelling[p.titleKey] ?: p.title, fr) ?: return@mapNotNull null
            Move(n, folder)
        }
    }

    /** The files put back at the root (the user changed their mind). */
    fun undo(moves: List<Move>): List<Move> = moves.map { Move(it.name, "") }

    private fun clean(s: String) = s.map { if (it in "\\/:*?\"<>|" || it.code < 0x20) ' ' else it }.joinToString("").replace(Regex("\\s+"), " ").trim().trim('.').take(80)
}
