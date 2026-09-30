package castbridge.core.learn

import kotlin.math.*

/**
 * Tiny math expression evaluator for plots (« x^2 - 3*x + 1 », « 2x+1 », « ln(x) », « 100 - 2*x »...).
 * Operators + - * / ^ (right-associative), unary minus, parentheses, implicit multiplication (2x, 3(x+1), x ln(x)),
 * variable x, constants pi and e, functions sin cos tan sqrt ln log exp abs. Compiled once, evaluated many times.
 */
class Expr private constructor(private val root: Node) {
    fun eval(x: Double): Double = root.v(x)

    private interface Node { fun v(x: Double): Double }

    companion object {
        class Error(msg: String) : IllegalArgumentException(msg)

        private val FUNS: Map<String, (Double) -> Double> = mapOf(
            "sin" to ::sin, "cos" to ::cos, "tan" to ::tan, "sqrt" to ::sqrt, "ln" to ::ln, "log" to ::log10,
            "exp" to ::exp, "abs" to { v: Double -> abs(v) },
        )

        fun parse(s: String): Expr {
            val p = P(s.replace(" ", ""))
            val n = p.sum()
            if (p.i != p.s.length) throw Error("caractère inattendu '${p.s[p.i]}' dans « $s »")
            return Expr(n)
        }

        private class P(val s: String) {
            var i = 0
            fun sum(): Node {
                var l = prod()
                while (i < s.length && (s[i] == '+' || s[i] == '-')) {
                    val op = s[i++]; val a = l; val b = prod()
                    l = if (op == '+') object : Node { override fun v(x: Double) = a.v(x) + b.v(x) } else object : Node { override fun v(x: Double) = a.v(x) - b.v(x) }
                }
                return l
            }
            fun prod(): Node {
                var l = unary()
                while (i < s.length) {
                    val c = s[i]
                    if (c == '*' || c == '/') {
                        i++; val a = l; val b = unary()
                        l = if (c == '*') object : Node { override fun v(x: Double) = a.v(x) * b.v(x) } else object : Node { override fun v(x: Double) = a.v(x) / b.v(x) }
                    } else if (c == '(' || c.isLetter() || c.isDigit() || c == '.') {
                        val a = l; val b = power()   // implicit multiplication
                        l = object : Node { override fun v(x: Double) = a.v(x) * b.v(x) }
                    } else break
                }
                return l
            }
            fun unary(): Node {
                if (i < s.length && s[i] == '-') { i++; val a = unary(); return object : Node { override fun v(x: Double) = -a.v(x) } }
                if (i < s.length && s[i] == '+') { i++; return unary() }
                return power()
            }
            fun power(): Node {
                val b = atom()
                if (i < s.length && s[i] == '^') { i++; val e = unary(); return object : Node { override fun v(x: Double) = b.v(x).pow(e.v(x)) } }
                return b
            }
            fun atom(): Node {
                if (i >= s.length) throw Error("expression incomplète")
                val c = s[i]
                if (c == '(') {
                    i++; val n = sum()
                    if (i >= s.length || s[i] != ')') throw Error("parenthèse non fermée")
                    i++; return n
                }
                if (c.isDigit() || c == '.') {
                    val st = i
                    while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
                    val v = s.substring(st, i).toDoubleOrNull() ?: throw Error("nombre invalide")
                    return object : Node { override fun v(x: Double) = v }
                }
                if (c.isLetter()) {
                    val st = i
                    while (i < s.length && s[i].isLetter()) i++
                    val w = s.substring(st, i)
                    FUNS[w]?.let { f ->
                        val a = atomArg()
                        return object : Node { override fun v(x: Double) = f(a.v(x)) }
                    }
                    // Words made of x / pi / e glued together (« xe », « 2pix ») are not accepted: only single names.
                    return when (w) {
                        "x" -> object : Node { override fun v(x: Double) = x }
                        "pi" -> object : Node { override fun v(x: Double) = PI }
                        "e" -> object : Node { override fun v(x: Double) = E }
                        else -> throw Error("nom inconnu « $w »")
                    }
                }
                throw Error("caractère inattendu '$c'")
            }
            private fun atomArg(): Node {
                if (i < s.length && s[i] == '(') return atom()
                return power()
            }
        }
    }
}
