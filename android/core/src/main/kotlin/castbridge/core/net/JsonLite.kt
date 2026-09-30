package castbridge.core.net

/**
 * Tiny JSON reader/writer for the server exchanges (no dependency, works on every Android version).
 * Objects become [Map], arrays [List], numbers [Long] or [Double], plus String / Boolean / null.
 */
object JsonLite {
    class ParseError(msg: String) : IllegalArgumentException(msg)

    fun parse(text: String): Any? = Reader(text).run { val v = value(); ws(); if (i != s.length) err("trailing data"); v }

    @Suppress("UNCHECKED_CAST")
    fun obj(text: String): Map<String, Any?> = parse(text) as? Map<String, Any?> ?: throw ParseError("JSON object expected")

    fun quote(s: String): String {
        val b = StringBuilder(s.length + 2).append('"')
        for (c in s) when {
            c == '"' -> b.append("\\\"")
            c == '\\' -> b.append("\\\\")
            c == '\n' -> b.append("\\n")
            c == '\r' -> b.append("\\r")
            c == '\t' -> b.append("\\t")
            c < ' ' -> b.append(String.format("\\u%04x", c.code))
            else -> b.append(c)
        }
        return b.append('"').toString()
    }

    /** Serializes maps, lists, strings, numbers, booleans and null (null map entries are skipped). */
    fun write(v: Any?): String = when (v) {
        null -> "null"
        is String -> quote(v)
        is Boolean, is Int, is Long -> v.toString()
        is Number -> v.toDouble().let { if (it.isFinite()) it.toString() else "null" }
        is Map<*, *> -> v.entries.filter { it.value != null }.joinToString(",", "{", "}") { quote(it.key.toString()) + ":" + write(it.value) }
        is Iterable<*> -> v.joinToString(",", "[", "]") { write(it) }
        else -> quote(v.toString())
    }

    fun Map<String, Any?>.str(k: String): String? = this[k] as? String
    fun Map<String, Any?>.long(k: String): Long? = (this[k] as? Number)?.toLong()
    fun Map<String, Any?>.int(k: String): Int? = (this[k] as? Number)?.toInt()
    fun Map<String, Any?>.bool(k: String): Boolean? = this[k] as? Boolean

    private class Reader(val s: String) {
        var i = 0
        fun err(m: String): Nothing = throw ParseError("$m at $i")
        fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }
        fun value(): Any? {
            ws()
            if (i >= s.length) err("unexpected end")
            return when (s[i]) {
                '{' -> { i++; val m = LinkedHashMap<String, Any?>(); ws()
                    if (s.getOrNull(i) == '}') { i++; return m }
                    while (true) { ws(); if (s.getOrNull(i) != '"') err("key expected"); val k = string(); ws()
                        if (s.getOrNull(i) != ':') err("':' expected"); i++; m[k] = value(); ws()
                        when (s.getOrNull(i)) { ',' -> i++; '}' -> { i++; return m }; else -> err("',' or '}' expected") } } }
                '[' -> { i++; val l = ArrayList<Any?>(); ws()
                    if (s.getOrNull(i) == ']') { i++; return l }
                    while (true) { l += value(); ws()
                        when (s.getOrNull(i)) { ',' -> i++; ']' -> { i++; return l }; else -> err("',' or ']' expected") } } }
                '"' -> string()
                't' -> lit("true", true)
                'f' -> lit("false", false)
                'n' -> lit("null", null)
                else -> number()
            }
        }
        fun lit(w: String, v: Any?): Any? { if (!s.startsWith(w, i)) err("bad literal"); i += w.length; return v }
        fun number(): Any {
            val st = i
            while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
            val t = s.substring(st, i)
            if (t.isEmpty()) err("value expected")
            return t.toLongOrNull() ?: t.toDoubleOrNull() ?: err("bad number")
        }
        fun string(): String {
            i++ // opening quote
            val b = StringBuilder()
            while (true) {
                if (i >= s.length) err("unterminated string")
                val c = s[i++]
                when (c) {
                    '"' -> return b.toString()
                    '\\' -> { if (i >= s.length) err("bad escape")
                        when (val e = s[i++]) {
                            '"', '\\', '/' -> b.append(e); 'b' -> b.append('\b'); 'f' -> b.append('\u000C')
                            'n' -> b.append('\n'); 'r' -> b.append('\r'); 't' -> b.append('\t')
                            'u' -> { if (i + 4 > s.length) err("bad \\u"); b.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4 }
                            else -> err("bad escape") } }
                    else -> b.append(c)
                }
            }
        }
    }
}
