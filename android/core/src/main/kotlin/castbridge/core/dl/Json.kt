package castbridge.core.dl

/**
 * Small JSON reader/writer for the aria2 JSON-RPC relay (core has no JSON library and must stay light).
 * Values: Map<String, Any?> (insertion order kept), List<Any?>, String, Long, Double, Boolean, null.
 */
object Json {
    class ParseError(msg: String) : IllegalArgumentException(msg)

    fun parse(text: String): Any? {
        val p = Parser(text)
        p.ws()
        val v = p.value()
        p.ws()
        if (p.i != text.length) throw ParseError("trailing data at ${p.i}")
        return v
    }

    fun write(v: Any?): String = StringBuilder().also { write(it, v) }.toString()

    fun write(sb: StringBuilder, v: Any?) {
        when (v) {
            null -> sb.append("null")
            is String -> str(sb, v)
            is Boolean -> sb.append(v)
            is Int, is Long, is Short, is Byte -> sb.append(v.toString())
            is Double -> if (v.isFinite()) sb.append(if (v == Math.floor(v) && Math.abs(v) < 1e15) v.toLong().toString() else v.toString()) else sb.append("null")
            is Float -> write(sb, v.toDouble())
            is Number -> sb.append(v.toString())
            is Map<*, *> -> {
                sb.append('{')
                var first = true
                for ((k, x) in v) { if (!first) sb.append(','); first = false; str(sb, k.toString()); sb.append(':'); write(sb, x) }
                sb.append('}')
            }
            is Iterable<*> -> {
                sb.append('[')
                var first = true
                for (x in v) { if (!first) sb.append(','); first = false; write(sb, x) }
                sb.append(']')
            }
            is Array<*> -> write(sb, v.asList())
            is RawJson -> sb.append(v.json)
            else -> str(sb, v.toString())
        }
    }

    private fun str(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) when {
            c == '"' -> sb.append("\\\"")
            c == '\\' -> sb.append("\\\\")
            c == '\n' -> sb.append("\\n")
            c == '\r' -> sb.append("\\r")
            c == '\t' -> sb.append("\\t")
            c < ' ' || c == ' ' || c == ' ' -> sb.append("\\u%04x".format(c.code))
            else -> sb.append(c)
        }
        sb.append('"')
    }

    private class Parser(val s: String) {
        var i = 0
        var depth = 0
        fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }
        fun value(): Any? {
            if (i >= s.length) throw ParseError("unexpected end")
            return when (val c = s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> string()
                't' -> lit("true", true)
                'f' -> lit("false", false)
                'n' -> lit("null", null)
                else -> if (c == '-' || c.isDigit()) number() else throw ParseError("unexpected '$c' at $i")
            }
        }
        fun lit(w: String, v: Any?): Any? {
            if (!s.startsWith(w, i)) throw ParseError("bad literal at $i")
            i += w.length; return v
        }
        fun number(): Any {
            val st = i
            if (s[i] == '-') i++
            while (i < s.length && (s[i].isDigit() || s[i] in ".eE+-")) i++
            val t = s.substring(st, i)
            return t.toLongOrNull() ?: t.toDoubleOrNull() ?: throw ParseError("bad number '$t'")
        }
        fun string(): String {
            i++                                   // opening quote
            val sb = StringBuilder()
            while (true) {
                if (i >= s.length) throw ParseError("unterminated string")
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (i >= s.length) throw ParseError("bad escape")
                        when (val e = s[i++]) {
                            '"' -> sb.append('"'); '\\' -> sb.append('\\'); '/' -> sb.append('/')
                            'b' -> sb.append('\b'); 'f' -> sb.append('\u000c'); 'n' -> sb.append('\n')
                            'r' -> sb.append('\r'); 't' -> sb.append('\t')
                            'u' -> {
                                if (i + 4 > s.length) throw ParseError("bad unicode escape")
                                sb.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4
                            }
                            else -> throw ParseError("bad escape '\\$e'")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }
        fun obj(): Map<String, Any?> {
            if (++depth > 64) throw ParseError("too deep")
            i++
            val m = LinkedHashMap<String, Any?>()
            ws()
            if (i < s.length && s[i] == '}') { i++; depth--; return m }
            while (true) {
                ws()
                if (i >= s.length || s[i] != '"') throw ParseError("key expected at $i")
                val k = string()
                ws()
                if (i >= s.length || s[i] != ':') throw ParseError("':' expected at $i")
                i++; ws()
                m[k] = value()
                ws()
                if (i >= s.length) throw ParseError("unterminated object")
                when (s[i++]) { ',' -> continue; '}' -> { depth--; return m }; else -> throw ParseError("',' or '}' expected at ${i - 1}") }
            }
        }
        fun arr(): List<Any?> {
            if (++depth > 64) throw ParseError("too deep")
            i++
            val l = ArrayList<Any?>()
            ws()
            if (i < s.length && s[i] == ']') { i++; depth--; return l }
            while (true) {
                ws(); l += value(); ws()
                if (i >= s.length) throw ParseError("unterminated array")
                when (s[i++]) { ',' -> continue; ']' -> { depth--; return l }; else -> throw ParseError("',' or ']' expected at ${i - 1}") }
            }
        }
    }
}

/** Already-serialised JSON embedded as is by [Json.write]. */
class RawJson(val json: String)

// Lenient accessors: aria2 sends every number as a string ("totalLength":"1234").
@Suppress("UNCHECKED_CAST")
fun Any?.obj(): Map<String, Any?> = (this as? Map<String, Any?>) ?: emptyMap()
fun Any?.list(): List<Any?> = (this as? List<Any?>) ?: emptyList()
fun Map<String, Any?>.s(k: String): String? = this[k]?.let { if (it is String) it else it.toString() }
fun Map<String, Any?>.n(k: String): Long = when (val v = this[k]) {
    is Number -> v.toLong()
    is String -> v.toLongOrNull() ?: v.toDoubleOrNull()?.toLong() ?: 0
    else -> 0
}
fun Map<String, Any?>.b(k: String): Boolean = when (val v = this[k]) { is Boolean -> v; is String -> v == "true"; else -> false }
