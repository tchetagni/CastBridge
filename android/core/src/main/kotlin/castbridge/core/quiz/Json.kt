package castbridge.core.quiz

/**
 * Tiny JSON reader/writer for the quiz (question bank, network state). :core has no JSON library and
 * org.json is not available on the JVM test classpath; this is ~100 lines and allocation-light.
 * Objects become LinkedHashMap<String, Any?>, arrays List<Any?>, numbers Long or Double.
 */
object Json {
    class ParseError(msg: String) : IllegalArgumentException(msg)

    fun parse(s: String): Any? {
        val p = Parser(s)
        p.ws()
        val v = p.value()
        p.ws()
        if (p.i != s.length) throw ParseError("trailing data at ${p.i}")
        return v
    }

    @Suppress("UNCHECKED_CAST")
    fun obj(s: String): Map<String, Any?> = parse(s) as? Map<String, Any?> ?: throw ParseError("not an object")

    private class Parser(val s: String) {
        var i = 0
        fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }
        fun value(): Any? {
            if (i >= s.length) throw ParseError("unexpected end")
            return when (val c = s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> lit("true", true)
                'f' -> lit("false", false)
                'n' -> lit("null", null)
                else -> if (c == '-' || c.isDigit()) num() else throw ParseError("unexpected '$c' at $i")
            }
        }
        fun lit(w: String, v: Any?): Any? {
            if (!s.startsWith(w, i)) throw ParseError("bad literal at $i")
            i += w.length; return v
        }
        fun num(): Any {
            val st = i
            if (s[i] == '-') i++
            while (i < s.length && (s[i].isDigit() || s[i] in ".eE+-")) i++
            val t = s.substring(st, i)
            return t.toLongOrNull() ?: t.toDoubleOrNull() ?: throw ParseError("bad number '$t'")
        }
        fun str(): String {
            i++ // opening quote
            val b = StringBuilder()
            while (true) {
                if (i >= s.length) throw ParseError("unterminated string")
                val c = s[i++]
                when (c) {
                    '"' -> return b.toString()
                    '\\' -> {
                        if (i >= s.length) throw ParseError("bad escape")
                        when (val e = s[i++]) {
                            '"' -> b.append('"'); '\\' -> b.append('\\'); '/' -> b.append('/')
                            'b' -> b.append('\b'); 'f' -> b.append('\u000C'); 'n' -> b.append('\n')
                            'r' -> b.append('\r'); 't' -> b.append('\t')
                            'u' -> {
                                if (i + 4 > s.length) throw ParseError("bad unicode escape")
                                b.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4
                            }
                            else -> throw ParseError("bad escape '\\$e'")
                        }
                    }
                    else -> b.append(c)
                }
            }
        }
        fun arr(): List<Any?> {
            i++; val l = ArrayList<Any?>()
            ws(); if (i < s.length && s[i] == ']') { i++; return l }
            while (true) {
                ws(); l += value(); ws()
                if (i >= s.length) throw ParseError("unterminated array")
                when (s[i++]) { ',' -> continue; ']' -> return l; else -> throw ParseError("expected , or ] at ${i - 1}") }
            }
        }
        fun obj(): Map<String, Any?> {
            i++; val m = LinkedHashMap<String, Any?>()
            ws(); if (i < s.length && s[i] == '}') { i++; return m }
            while (true) {
                ws(); if (i >= s.length || s[i] != '"') throw ParseError("expected key at $i")
                val k = str(); ws()
                if (i >= s.length || s[i++] != ':') throw ParseError("expected : at ${i - 1}")
                ws(); m[k] = value(); ws()
                if (i >= s.length) throw ParseError("unterminated object")
                when (s[i++]) { ',' -> continue; '}' -> return m; else -> throw ParseError("expected , or } at ${i - 1}") }
            }
        }
    }

    /** Serializes maps, lists, arrays, strings, numbers, booleans and null. */
    fun write(v: Any?): String = StringBuilder().also { write(it, v) }.toString()

    fun write(b: StringBuilder, v: Any?) {
        when (v) {
            null -> b.append("null")
            is String -> quote(b, v)
            is Boolean, is Int, is Long, is Short, is Byte -> b.append(v.toString())
            is Double -> b.append(if (v.isFinite()) (if (v == Math.floor(v) && Math.abs(v) < 1e15) v.toLong().toString() else v.toString()) else "null")
            is Float -> write(b, v.toDouble())
            is Map<*, *> -> {
                b.append('{'); var first = true
                for ((k, x) in v) { if (!first) b.append(','); first = false; quote(b, k.toString()); b.append(':'); write(b, x) }
                b.append('}')
            }
            is Iterable<*> -> { b.append('['); var first = true; for (x in v) { if (!first) b.append(','); first = false; write(b, x) }; b.append(']') }
            is IntArray -> write(b, v.toList())
            is Array<*> -> write(b, v.toList())
            is Enum<*> -> quote(b, v.name)
            else -> quote(b, v.toString())
        }
    }

    fun quote(s: String): String = StringBuilder().also { quote(it, s) }.toString()

    private fun quote(b: StringBuilder, s: String) {
        b.append('"')
        for (c in s) when {
            c == '"' -> b.append("\\\""); c == '\\' -> b.append("\\\\")
            c == '\n' -> b.append("\\n"); c == '\r' -> b.append("\\r"); c == '\t' -> b.append("\\t")
            // < and > escaped so a JSON string can never close a <script> block of the web page
            c < ' ' || c == '<' || c == '>' || c == ' ' || c == ' ' -> b.append("\\u%04x".format(c.code))
            else -> b.append(c)
        }
        b.append('"')
    }

    // ---- typed accessors for parsed maps ----
    fun Map<String, Any?>.str(k: String): String? = this[k] as? String
    fun Map<String, Any?>.long(k: String): Long? = (this[k] as? Number)?.toLong()
    fun Map<String, Any?>.int(k: String): Int? = (this[k] as? Number)?.toInt()
    fun Map<String, Any?>.bool(k: String): Boolean? = this[k] as? Boolean
    @Suppress("UNCHECKED_CAST")
    fun Map<String, Any?>.map(k: String): Map<String, Any?>? = this[k] as? Map<String, Any?>
    @Suppress("UNCHECKED_CAST")
    fun Map<String, Any?>.list(k: String): List<Any?>? = this[k] as? List<Any?>
}
