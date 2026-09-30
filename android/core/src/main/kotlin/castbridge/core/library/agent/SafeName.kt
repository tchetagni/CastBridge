package castbridge.core.library.agent

/**
 * File and folder names the agent is allowed to produce (and the executor to accept).
 * The rules are the strictest of the volumes the TV can have (exFAT / FAT32 / NTFS): a name that is valid here is valid
 * everywhere, and moving a file from the internal memory to a USB key never renames it behind the user's back.
 */
object SafeName {
    const val MAX_CHARS = 180
    const val MAX_BYTES = 230
    const val MAX_DEPTH = 4
    private const val FORBIDDEN = "\\/:*?\"<>|"
    private val RESERVED = setOf("con", "prn", "aux", "nul", "com1", "com2", "com3", "com4", "com5", "com6", "com7", "com8", "com9", "lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9")

    private fun bad(c: Char) = c in FORBIDDEN || c.code < 0x20 || c.code == 0x7f

    /** Makes [text] usable as the base of a file name: forbidden characters replaced or dropped, no trailing dot/space, bounded length. */
    fun clean(text: String, maxChars: Int = 150): String {
        val sb = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == ':' -> sb.append(if (text.getOrNull(i + 1) == ' ' || text.getOrNull(i - 1) == ' ') " –" else "-")
                c == '|' -> sb.append(" – ")
                c == '/' || c == '\\' -> sb.append('-')
                c == '"' -> sb.append('\'')
                c == '?' || c == '*' || c == '<' || c == '>' -> {}
                bad(c) -> {}
                else -> sb.append(c)
            }
            i++
        }
        var s = sb.toString().replace(Regex("\\s{2,}"), " ").replace(Regex("(?: –){2,}"), " –").trim().trimEnd('.', ' ')
        s = s.trimStart('.', ' ', '–', '-')
        if (s.length > maxChars) s = s.substring(0, s.offsetByCodePoints(0, minOf(maxChars, s.codePointCount(0, s.length)))).trimEnd('.', ' ', '-', '–')
        if (s.lowercase() in RESERVED) s += "_"
        return s
    }

    fun fileName(base: String, ext: String): String {
        var b = clean(base)
        val suffix = if (ext.isEmpty()) "" else ".$ext"
        while ((b + suffix).toByteArray(Charsets.UTF_8).size > MAX_BYTES && b.isNotEmpty()) b = b.substring(0, b.offsetByCodePoints(b.length, -1)).trimEnd('.', ' ')
        return b + suffix
    }

    /** Null if [name] is acceptable as a file name, else the reason (French, shown to the user). */
    fun checkName(name: String): String? {
        if (name.isBlank()) return "nom vide"
        if (name == "." || name == "..") return "nom interdit"
        if (name.startsWith(".")) return "un nom ne peut pas commencer par un point"
        if (name.length > 200 || name.toByteArray(Charsets.UTF_8).size > 250) return "nom trop long"
        if (name.any { bad(it) }) return "caractère interdit (\\ / : * ? \" < > |)"
        if (name.endsWith(".") || name.endsWith(" ")) return "un nom ne peut pas finir par un point ou une espace"
        if (name.substringBefore('.').lowercase() in RESERVED) return "nom réservé par le système"
        return null
    }

    /** Null if [path] is an acceptable relative folder ("Séries/Prison Break/Saison 01"), else the reason. "" is the library root. */
    fun checkFolder(path: String): String? {
        if (path.isEmpty()) return null
        if (path.startsWith("/") || path.startsWith("\\") || path.contains(':') && path.indexOf(':') == 1) return "chemin absolu refusé"
        if (path.contains('\\')) return "séparateur interdit"
        val segs = path.split('/')
        if (segs.size > MAX_DEPTH) return "dossiers trop imbriqués"
        for (s in segs) {
            if (s.isEmpty()) return "dossier vide dans le chemin"
            if (s == "." || s == "..") return "« .. » refusé : on ne sort jamais de la bibliothèque"
            checkName(s)?.let { return "dossier « $s » : $it" }
        }
        if (path.length > 200) return "chemin trop long"
        return null
    }

    /** A folder path from its parts, each part cleaned; empty parts dropped. */
    fun folder(parts: List<String>): String = parts.map { clean(it, 80) }.filter { it.isNotEmpty() }.joinToString("/")
}
