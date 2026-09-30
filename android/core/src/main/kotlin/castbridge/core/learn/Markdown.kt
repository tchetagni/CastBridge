package castbridge.core.learn

/**
 * Restricted Markdown of the lessons: paragraphs (blank line), line breaks, « - » bullets, « 1. » numbered items,
 * **bold**, *italic*, and inline formulas between dollars ($\frac{1}{2}$). Nothing else (no links, images, HTML, titles:
 * titles are blocks). Parsed once into spans that the TV (Android spans) and the phone (AnnotatedString) style.
 */
object Markdown {
    data class Span(val text: String, val bold: Boolean = false, val italic: Boolean = false, val tex: Tex? = null)
    enum class Kind { PARA, BULLET, NUMBER }
    data class Para(val kind: Kind, val spans: List<Span>, val number: Int = 0) {
        val plain: String get() = spans.joinToString("") { it.tex?.plain() ?: it.text }
    }

    class Error(msg: String) : IllegalArgumentException(msg)

    fun parse(md: String): List<Para> {
        val out = ArrayList<Para>()
        val buf = StringBuilder()
        fun flush() { if (buf.isNotBlank()) out += Para(Kind.PARA, inline(buf.toString().trim())); buf.clear() }
        for (raw in md.replace("\r", "").split('\n')) {
            val line = raw.trimEnd()
            val t = line.trimStart()
            when {
                t.isEmpty() -> flush()
                t.startsWith("- ") || t.startsWith("• ") -> { flush(); out += Para(Kind.BULLET, inline(t.substring(2).trim())) }
                Regex("^\\d{1,2}[.)] ").containsMatchIn(t) -> {
                    flush(); val n = t.takeWhile { it.isDigit() }.toInt()
                    out += Para(Kind.NUMBER, inline(t.substring(t.indexOf(' ') + 1).trim()), n)
                }
                else -> { if (buf.isNotEmpty()) buf.append('\n'); buf.append(t) }
            }
        }
        flush()
        return out
    }

    /** Inline spans: **bold**, *italic*, $tex$; a lone * or $ (e.g. « 5 $ ») must be escaped as \* or \$. */
    fun inline(s: String): List<Span> {
        val out = ArrayList<Span>()
        var bold = false; var italic = false
        val cur = StringBuilder()
        fun emit() { if (cur.isNotEmpty()) { out += Span(cur.toString(), bold, italic); cur.clear() } }
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c == '\\' && i + 1 < s.length && s[i + 1] in "*$\\" -> { cur.append(s[i + 1]); i += 2 }
                c == '*' && i + 1 < s.length && s[i + 1] == '*' -> { emit(); bold = !bold; i += 2 }
                c == '*' -> { emit(); italic = !italic; i++ }
                c == '$' -> {
                    val end = s.indexOf('$', i + 1)
                    if (end < 0) throw Error("formule non fermée ($) dans « ${s.take(60)} »")
                    emit(); out += Span(s.substring(i + 1, end), bold, italic, Tex.parse(s.substring(i + 1, end))); i = end + 1
                }
                else -> { cur.append(c); i++ }
            }
        }
        emit()
        if (bold) throw Error("gras non fermé (**) dans « ${s.take(60)} »")
        if (italic) throw Error("italique non fermé (*) dans « ${s.take(60)} »")
        return out
    }

    /** Plain text (formulas in their Unicode form), for TextToSpeech, the phone summary and length checks. */
    fun plain(md: String): String = parse(md).joinToString("\n") { p ->
        when (p.kind) { Kind.BULLET -> "• " + p.plain; Kind.NUMBER -> "${p.number}. " + p.plain; else -> p.plain }
    }

    /** Words for TextToSpeech (formulas spoken). */
    fun spoken(md: String, lang: String): String = parse(md).joinToString(". ") { p -> p.spans.joinToString("") { it.tex?.spoken(lang) ?: it.text } }
}
