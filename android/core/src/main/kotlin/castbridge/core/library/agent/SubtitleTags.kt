package castbridge.core.library.agent

/**
 * The language and flags written between the name and the extension of a subtitle file: `Title.fr.srt`, `Title.eng.forced.srt`, `Title.fr.sdh.srt`,
 * `Title.pt-BR.ass`. Only dot-separated tokens at the very end of the name count (« Stranger Than It.srt » keeps its « It »), and only a known
 * language code, so that a title word is never taken for a language. The canonical suffix is `fr`, `en.forced`, `fr.sdh`, `pt-BR`: two letters
 * (ISO 639-1), an optional region, then at most one flag.
 */
internal object SubtitleTags {
    private val CODES: Map<String, String> = buildMap {
        fun add(code: String, vararg aliases: String) { put(code, code); aliases.forEach { put(it, code) } }
        add("fr", "fre", "fra", "french", "francais")
        add("en", "eng", "english")
        add("es", "spa", "spanish", "esp")
        add("pt", "por", "portuguese")
        add("de", "ger", "deu", "german")
        add("it", "ita", "italian")
        add("nl", "dut", "nld", "dutch")
        add("sv", "swe", "swedish")
        add("pl", "pol", "polish")
        add("tr", "tur", "turkish")
        add("ru", "rus", "russian")
        add("ar", "ara", "arabic")
        add("zh", "chi", "zho", "chinese")
        add("ja", "jpn", "japanese")
        add("ko", "kor", "korean")
    }
    private val FLAGS = mapOf("forced" to "forced", "sdh" to "sdh", "hi" to "sdh", "cc" to "sdh", "default" to "default")
    private val REGION = Regex("^([a-z]{2,3})[-_]([a-z]{2}|hans|hant)$", RegexOption.IGNORE_CASE)

    /** The canonical `lang` / `lang-REGION` of one token, or null. */
    private fun lang(tok: String): String? {
        val l = tok.lowercase()
        CODES[l]?.let { return it }
        REGION.matchEntire(tok)?.let { m ->
            val base = CODES[m.groupValues[1].lowercase()] ?: return null
            val reg = m.groupValues[2]
            return base + "-" + (if (reg.length == 2) reg.uppercase() else reg.replaceFirstChar { it.uppercase() })
        }
        return null
    }

    /** ("Title", "fr.forced") from "Title.fr.forced"; (stem, null) when there is no such suffix or nothing would remain of the name. */
    fun peel(stem: String): Pair<String, String?> {
        val toks = stem.split('.')
        if (toks.size < 2) return stem to null
        var end = toks.size
        var lang: String? = null
        var flag: String? = null
        while (end > 1 && toks.size - end < 3) {
            val t = toks[end - 1]
            val f = FLAGS[t.lowercase()]
            val lg = if (f == null) lang(t) else null
            when {
                f != null && flag == null -> flag = f
                lg != null && lang == null -> lang = lg
                else -> break
            }
            end--
        }
        if (lang == null && flag == null) return stem to null
        val rest = toks.subList(0, end).joinToString(".")
        if (rest.isBlank()) return stem to null
        return rest to listOfNotNull(lang, flag).joinToString(".")
    }
}
