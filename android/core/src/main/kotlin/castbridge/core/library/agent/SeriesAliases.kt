package castbridge.core.library.agent

/**
 * Optional table of series known under several titles (« La Casa de Papel » = « Money Heist »). It only tells [SeriesClassifier.plan] that two spellings found
 * in the SAME library are one series, so that they share a folder; it never renames a lone file. Local and verifiable: the file
 * `castbridge/library/series-aliases.tsv` is a resource, and `SeriesAliasesTest` checks its rules (no year, no title in two entries, no merge of distinct series).
 */
class SeriesAliases private constructor(private val canonicalOf: Map<String, String>) {
    /** Group key of [title]: the canonical entry's key when [title] is a listed alias, else its own key. */
    fun groupKey(title: String): String { val k = Text.groupKey(title); return canonicalOf[k] ?: k }

    val size get() = canonicalOf.size

    companion object {
        val NONE = SeriesAliases(emptyMap())

        /** One entry per line: `Canonical<TAB>Alias | Alias`. Lines starting with `#` and blank lines are ignored. Throws on a malformed table (a title listed twice, a year in a title). */
        fun parse(text: String): SeriesAliases {
            val map = HashMap<String, String>()
            val seen = HashSet<String>()
            for ((i, raw) in text.lines().withIndex()) {
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) continue
                val cols = line.split('\t')
                require(cols.size == 2) { "series-aliases line ${i + 1}: expected 'Canonical<TAB>Alias | Alias'" }
                val titles = listOf(cols[0].trim()) + cols[1].split('|').map { it.trim() }
                val canonical = Text.groupKey(titles[0])
                for (t in titles) {
                    require(t.isNotEmpty() && Text.groupKey(t).isNotEmpty()) { "series-aliases line ${i + 1}: empty title" }
                    require(!Regex("(?:19|20)\\d\\d").containsMatchIn(t)) { "series-aliases line ${i + 1}: no year in a title ('$t')" }
                    require(seen.add(Text.groupKey(t))) { "series-aliases line ${i + 1}: '$t' is listed twice" }
                    map[Text.groupKey(t)] = canonical
                }
            }
            return SeriesAliases(map)
        }

        /** The table shipped with the app (empty if the resource is missing: the feature is optional). */
        fun builtin(): SeriesAliases = BUILTIN
        private val BUILTIN: SeriesAliases by lazy {
            runCatching { SeriesAliases::class.java.getResourceAsStream("/castbridge/library/series-aliases.tsv")?.use { parse(it.readBytes().toString(Charsets.UTF_8)) } }.getOrNull() ?: NONE
        }
    }
}
