package castbridge.core.library.agent

import java.text.Normalizer

/** Small text helpers: matching keys, title case (French / English), date formatting. */
object Text {
    private val DIACRITICS = Regex("\\p{InCombiningDiacriticalMarks}+")
    private val NON_ALNUM = Regex("[^a-z0-9]+")

    /** Matching key: lower case, no accents, letters and digits only, single spaces. "Prison Break" = "prison.break" = "PRISON_BREAK". */
    fun key(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(DIACRITICS, "").lowercase().replace('œ', 'o').replace(NON_ALNUM, " ").trim()

    fun nfc(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFC)

    private val SMALL_FR = setOf("de", "du", "des", "la", "le", "les", "un", "une", "et", "à", "au", "aux", "en", "sur", "sous", "dans", "pour", "par", "ou", "d", "l", "sans", "chez", "vs", "by", "ft", "feat")
    private val SMALL_EN = setOf("a", "an", "the", "of", "and", "in", "on", "at", "to", "for", "or", "but", "vs", "via", "with", "by", "from", "nor", "as")
    private val ACRONYMS = setOf("ncis", "csi", "fbi", "swat", "ufc", "nba", "wwe", "bbc", "cnn", "tv", "dj", "usa", "uk", "ovni", "tmz", "mtv", "hbo", "npr", "rnb", "r&b", "atl", "mc", "ft", "cia", "nypd", "la", "nyc", "dc", "bts", "ac", "dvd", "cd", "hd", "vip", "ok", "xxx", "uefa", "fifa", "can", "caf")
    private val COUNTRY_SUFFIX = setOf("us", "uk", "au", "nz")
    /** "S.W.A.T.", "S.H.I.E.L.D.", "E.T.": letters and dots only, at least two letters. */
    private val INITIALS = Regex("^(?:\\p{L}\\.){2,}\\p{L}?\\.?$")
    /** "LA CASA DE PAPEL": in a French title "LA" is the article, not Los Angeles. */
    private val FRENCH_WORDS_LIKE_ACRONYMS = setOf("la", "can")
    private val ROMAN = Regex("^(ii|iii|iv|vi|vii|viii|ix|xi|xii|xiii|xiv|xv)$")
    private val ELISION = setOf("l", "d", "j", "n", "s", "c", "m", "t", "qu", "jusqu", "lorsqu", "puisqu")

    /** True when every letter of [s] has the same case (and there is at least one letter): "prison break", "PRISON BREAK". */
    fun isSingleCase(s: String): Boolean {
        val letters = s.filter { it.isLetter() }
        return letters.isNotEmpty() && (letters.all { it.isLowerCase() } || letters.all { it.isUpperCase() })
    }

    /**
     * Title case for [lang] ("fr" / "en"). Applied only to names written in a single case (what download sites produce);
     * a name that already mixes cases is the user's own and is left alone.
     */
    fun titleCaseIfNeeded(s: String, lang: String): String = if (isSingleCase(s)) titleCase(s, lang) else s

    fun titleCase(s: String, lang: String): String {
        val small = if (lang == "en") SMALL_EN else SMALL_FR
        val allUpper = s.filter { it.isLetter() }.let { l -> l.isNotEmpty() && l.all { it.isUpperCase() } }
        val words = s.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val sb = ArrayList<String>(words.size)
        words.forEachIndexed { i, w ->
            val lw = w.lowercase()
            sb += when {
                allUpper && lw in ACRONYMS && !(lang == "fr" && lw in FRENCH_WORDS_LIKE_ACRONYMS) && w.length <= 5 && w.any { it.isLetter() } -> w.uppercase()
                ROMAN.matches(lw) -> w.uppercase()
                i > 0 && i == words.lastIndex && lw in COUNTRY_SUFFIX && words.size > 2 && words[i - 1].lowercase() !in small && words[i - 1].lowercase() !in SMALL_EN -> w.uppercase()   // "The Office US", not "The Last of Us"
                lw == "dj" || lw == "tv" -> w.uppercase()
                INITIALS.matches(w) -> w.uppercase()
                i > 0 && lw in small -> lw
                else -> capitalizeWord(lw, i == 0)
            }
        }
        return sb.joinToString(" ")
    }

    private fun capitalizeWord(w: String, first: Boolean): String {
        val ap = w.indexOfFirst { it == '\'' || it == '’' }
        if (ap in 1 until w.length - 1 && w.substring(0, ap) in ELISION) {
            val pre = w.substring(0, ap)
            return (if (first) cap(pre) else pre) + w[ap] + capitalizeWord(w.substring(ap + 1), false)
        }
        if (w.contains('-')) return w.split('-').joinToString("-") { cap(it) }
        return cap(w)
    }

    private fun cap(w: String): String = if (w.isEmpty()) w else w.substring(0, w.offsetByCodePoints(0, 1)).uppercase() + w.substring(w.offsetByCodePoints(0, 1))

    fun pad2(n: Int) = n.toString().padStart(2, '0')

    /** Human size: "4,2 Go", "850 Mo". */
    fun size(b: Long): String = when {
        b >= 1L shl 30 -> String.format(java.util.Locale.FRANCE, "%.1f Go", b / (1L shl 30).toDouble())
        b >= 1L shl 20 -> String.format(java.util.Locale.FRANCE, "%.0f Mo", b / (1L shl 20).toDouble())
        else -> "${b / 1024} ko"
    }
}
