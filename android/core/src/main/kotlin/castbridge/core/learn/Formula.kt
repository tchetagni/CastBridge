package castbridge.core.learn

import kotlin.math.max

/**
 * Minimal LaTeX for lessons, drawn with plain text and lines (no math library in the APK):
 * \frac{a}{b}, x^2, x^{n+1}, u_n, u_{n+1}, \sqrt{x}, \sqrt[3]{x}, \vec{AB}, \overline{AB}, \widehat{ABC}, \text{…},
 * \left( \right) (ignored), and symbols (\times \div \cdot \pm \leq \geq \neq \approx \infty \to \Rightarrow \in \mathbb{R}
 * \pi \alpha…\omega \Delta…, \lim \ln \sin…, \sum \int, \quad, \,). Anything else is a parse error, caught by the
 * content tests. [layout] places the pieces with any font metrics; [plain] gives a one-line Unicode form (inline text,
 * phone, accessibility) and [spoken] a French/English reading for TextToSpeech.
 */
sealed class Tex {
    data class Row(val items: List<Tex>) : Tex()
    /** A run of text; [italic] for single-letter variables. */
    data class Sym(val text: String, val italic: Boolean = false) : Tex()
    data class Frac(val num: Tex, val den: Tex) : Tex()
    data class Script(val base: Tex, val sup: Tex?, val sub: Tex?) : Tex()
    data class Sqrt(val body: Tex, val index: Tex?) : Tex()
    /** vec (arrow), bar (overline), hat (wide hat) above [body]. */
    data class Accent(val kind: String, val body: Tex) : Tex()

    companion object {
        class Error(msg: String) : IllegalArgumentException(msg)

        private val SYMBOLS: Map<String, String> = mapOf(
            "times" to "×", "div" to "÷", "cdot" to "·", "pm" to "±", "mp" to "∓", "leq" to "≤", "le" to "≤", "geq" to "≥", "ge" to "≥",
            "neq" to "≠", "ne" to "≠", "approx" to "≈", "infty" to "∞", "to" to "→", "rightarrow" to "→", "leftarrow" to "←",
            "Rightarrow" to "⇒", "Leftrightarrow" to "⇔", "iff" to "⇔", "implies" to "⇒", "in" to "∈", "notin" to "∉", "subset" to "⊂",
            "cup" to "∪", "cap" to "∩", "emptyset" to "∅", "varnothing" to "∅", "forall" to "∀", "exists" to "∃", "perp" to "⊥",
            "parallel" to "∥", "angle" to "∠", "ldots" to "…", "cdots" to "⋯", "dots" to "…", "degree" to "°", "circ" to "°",
            "%" to "%", "{" to "{", "}" to "}", "," to " ", ";" to " ", ":" to " ", "!" to "", "quad" to "  ", "qquad" to "    ",
            " " to " ", "sum" to "∑", "int" to "∫", "prod" to "∏", "partial" to "∂", "nabla" to "∇", "sim" to "∼", "equiv" to "≡",
            "mid" to "|", "vert" to "|", "lbrace" to "{", "rbrace" to "}", "langle" to "⟨", "rangle" to "⟩", "star" to "⋆",
            "alpha" to "α", "beta" to "β", "gamma" to "γ", "delta" to "δ", "epsilon" to "ε", "varepsilon" to "ε", "zeta" to "ζ",
            "eta" to "η", "theta" to "θ", "lambda" to "λ", "mu" to "μ", "nu" to "ν", "xi" to "ξ", "pi" to "π", "rho" to "ρ",
            "sigma" to "σ", "tau" to "τ", "phi" to "φ", "varphi" to "φ", "chi" to "χ", "psi" to "ψ", "omega" to "ω",
            "Gamma" to "Γ", "Delta" to "Δ", "Theta" to "Θ", "Lambda" to "Λ", "Pi" to "Π", "Sigma" to "Σ", "Phi" to "Φ", "Omega" to "Ω",
        )
        private val FUNCTIONS = setOf("lim", "ln", "log", "exp", "sin", "cos", "tan", "max", "min", "det", "arctan", "cotan")
        private val SETS = mapOf("R" to "ℝ", "N" to "ℕ", "Z" to "ℤ", "Q" to "ℚ", "C" to "ℂ")

        fun parse(s: String): Tex { val p = P(s); val r = p.row(null); if (p.i < s.length) throw Error("« ${s[p.i]} » inattendu dans « $s »"); return r }

        private class P(val s: String) {
            var i = 0
            fun row(end: Char?): Tex {
                val items = ArrayList<Tex>()
                while (i < s.length) {
                    val c = s[i]
                    if (end != null && c == end) break
                    if (c == '}') { if (end == null) throw Error("« } » sans ouverture dans « $s »"); break }
                    if (c == '^' || c == '_') {
                        i++
                        val arg = arg()
                        val prev = items.removeLastOrNull() ?: Sym("")
                        val sc = prev as? Script
                        items += if (sc != null && ((c == '^' && sc.sup == null) || (c == '_' && sc.sub == null)))
                            if (c == '^') sc.copy(sup = arg) else sc.copy(sub = arg)
                        else if (c == '^') Script(prev, arg, null) else Script(prev, null, arg)
                        continue
                    }
                    items += atom() ?: continue
                }
                return if (items.size == 1) items[0] else Row(items)
            }
            /** One argument: {group}, a command, or a single character. */
            fun arg(): Tex {
                skipSpaces()
                if (i >= s.length) throw Error("argument manquant dans « $s »")
                if (s[i] == '{') { i++; val r = row('}'); expect('}'); return r }
                return atom() ?: throw Error("argument manquant dans « $s »")
            }
            fun group(): Tex { skipSpaces(); if (i < s.length && s[i] == '{') { i++; val r = row('}'); expect('}'); return r }; return arg() }
            fun rawGroup(): String {
                skipSpaces(); expect('{'); val st = i; var depth = 1
                while (i < s.length) { if (s[i] == '{') depth++; if (s[i] == '}') { depth--; if (depth == 0) break }; i++ }
                val t = s.substring(st, i); expect('}'); return t
            }
            fun expect(c: Char) { if (i >= s.length || s[i] != c) throw Error("« $c » attendu dans « $s »"); i++ }
            fun skipSpaces() { while (i < s.length && s[i] == ' ') i++ }
            fun atom(): Tex? {
                val c = s[i]
                when {
                    c == ' ' -> { i++; return null }
                    c == '{' -> { i++; val r = row('}'); expect('}'); return r }
                    c == '\\' -> return command()
                    c.isLetter() -> { i++; return Sym(c.toString(), italic = true) }
                    c.isDigit() || c == '.' || c == ',' -> {
                        val st = i
                        while (i < s.length && (s[i].isDigit() || ((s[i] == '.' || s[i] == ',') && i + 1 < s.length && s[i + 1].isDigit()))) i++
                        if (i == st) i++
                        return Sym(s.substring(st, i))
                    }
                    c == '-' -> { i++; return Sym("−") }
                    c == '*' -> { i++; return Sym("×") }
                    c == '\'' -> { i++; return Sym("′") }
                    c == '<' -> { i++; return Sym("<") }
                    c == '>' -> { i++; return Sym(">") }
                    else -> { i++; return Sym(c.toString()) }
                }
            }
            fun command(): Tex? {
                i++ // backslash
                if (i >= s.length) throw Error("commande vide dans « $s »")
                val st = i
                if (s[i].isLetter()) while (i < s.length && s[i].isLetter()) i++ else i++
                val name = s.substring(st, i)
                return when (name) {
                    "frac", "dfrac", "tfrac" -> Frac(group(), group())
                    "sqrt" -> {
                        skipSpaces()
                        val idx = if (i < s.length && s[i] == '[') { i++; val r = row(']'); expect(']'); r } else null
                        Sqrt(group(), idx)
                    }
                    "vec", "overrightarrow" -> Accent("vec", group())
                    "overline", "bar" -> Accent("bar", group())
                    "widehat", "hat" -> Accent("hat", group())
                    "text", "mathrm", "textrm", "operatorname" -> Sym(rawGroup())
                    "mathbb" -> { val t = rawGroup().trim(); Sym(SETS[t] ?: throw Error("\\mathbb{$t} inconnu")) }
                    "left", "right", "big", "Big", "displaystyle" -> {
                        if (name == "left" || name == "right") { skipSpaces(); if (i < s.length && s[i] == '.') { i++; return null } }
                        null
                    }
                    in FUNCTIONS -> Sym(name)
                    else -> SYMBOLS[name]?.let { Sym(it) } ?: throw Error("commande \\$name non prise en charge dans « $s »")
                }
            }
        }

        private const val SUP = "⁰¹²³⁴⁵⁶⁷⁸⁹⁺⁻⁼⁽⁾ⁿⁱ"
        private const val SUP_SRC = "0123456789+-=()ni"
        private const val SUB = "₀₁₂₃₄₅₆₇₈₉₊₋₌₍₎ₙᵢₚ"
        private const val SUB_SRC = "0123456789+-=()nip"
    }

    /** One-line Unicode form: « a/b », « x² », « uₙ₊₁ », « √(x+1) », « AB→ ». */
    fun plain(): String = when (this) {
        is Row -> items.joinToString("") { it.plain() }
        is Sym -> text
        is Frac -> { val n = num.plain(); val d = den.plain(); "${wrapIf(n)}/${wrapIf(d)}" }
        is Script -> base.plain() + (sub?.let { script(it.plain(), SUB_SRC, SUB, "_") } ?: "") +
            (sup?.let { it.plain().let { p -> if (p == "°" || p == "′" || p == "′′") p else script(p, SUP_SRC, SUP, "^") } } ?: "")
        is Sqrt -> (index?.let { script(it.plain(), SUP_SRC, SUP, "^") } ?: "") + "√" + body.plain().let { if (it.length > 1) "($it)" else it }
        is Accent -> when (kind) { "vec" -> "vec(${body.plain()})"; "hat" -> "angle ${body.plain()}"; else -> body.plain() }
    }

    private fun wrapIf(s: String) = if (s.any { it in "+−-×÷ =" }) "($s)" else s
    private fun script(s: String, src: String, dst: String, fallback: String): String {
        val t = s.replace('−', '-')
        return if (t.all { src.indexOf(it) >= 0 }) t.map { dst[src.indexOf(it)] }.joinToString("") else "$fallback($s)"
    }

    /** Words for TextToSpeech. */
    fun spoken(lang: String = "fr"): String {
        val fr = lang != "en"
        return when (this) {
            is Row -> items.joinToString(" ") { it.spoken(lang) }
            is Sym -> when (text) {
                "×" -> if (fr) "fois" else "times"; "÷" -> if (fr) "divisé par" else "divided by"; "−" -> if (fr) "moins" else "minus"
                "+" -> if (fr) "plus" else "plus"; "=" -> if (fr) "égale" else "equals"; "≤" -> if (fr) "inférieur ou égal à" else "less than or equal to"
                "≥" -> if (fr) "supérieur ou égal à" else "greater than or equal to"; "π" -> "pi"; "°" -> if (fr) "degrés" else "degrees"
                else -> text
            }
            is Frac -> if (fr) "${num.spoken(lang)} sur ${den.spoken(lang)}" else "${num.spoken(lang)} over ${den.spoken(lang)}"
            is Script -> base.spoken(lang) + (sub?.let { (if (fr) " indice " else " sub ") + it.spoken(lang) } ?: "") + (sup?.let {
                when (it.plain()) { "2" -> if (fr) " au carré" else " squared"; "3" -> if (fr) " au cube" else " cubed"
                    else -> (if (fr) " puissance " else " to the power ") + it.spoken(lang) } } ?: "")
            is Sqrt -> (if (fr) "racine de " else "square root of ") + body.spoken(lang)
            is Accent -> (if (kind == "vec") (if (fr) "vecteur " else "vector ") else if (kind == "hat") (if (fr) "angle " else "angle ") else "") + body.spoken(lang)
        }
    }
}

/**
 * Formula layout with any font metrics (Android Paint on the apps, a fixed-width fake in tests). The result is a list
 * of text runs and lines placed around a baseline at y = 0 (negative = above).
 */
object TexLayout {
    interface Metrics {
        fun width(text: String, size: Double, italic: Boolean): Double
        fun ascent(size: Double): Double = size * 0.78
        fun descent(size: Double): Double = size * 0.22
    }

    sealed class Item {
        data class Run(val x: Double, val y: Double, val text: String, val size: Double, val italic: Boolean) : Item()
        data class Rule(val x1: Double, val y1: Double, val x2: Double, val y2: Double, val thickness: Double) : Item()
    }

    class Box(val width: Double, val ascent: Double, val descent: Double, val items: List<Item>) {
        val height get() = ascent + descent
        fun shifted(dx: Double, dy: Double) = items.map {
            when (it) { is Item.Run -> it.copy(x = it.x + dx, y = it.y + dy); is Item.Rule -> it.copy(x1 = it.x1 + dx, x2 = it.x2 + dx, y1 = it.y1 + dy, y2 = it.y2 + dy) }
        }
    }

    fun layout(t: Tex, size: Double, m: Metrics): Box = when (t) {
        is Tex.Sym -> {
            val pad = if (t.text in setOf("=", "+", "−", "×", "÷", "≤", "≥", "≠", "≈", "→", "⇒", "⇔", "<", ">", "±", "∈")) size * 0.18 else 0.0
            val w = m.width(t.text, size, t.italic)
            Box(w + 2 * pad, m.ascent(size), m.descent(size), listOf(Item.Run(pad, 0.0, t.text, size, t.italic)))
        }
        is Tex.Row -> {
            var x = 0.0; var asc = m.ascent(size) * 0.7; var desc = 0.0; val items = ArrayList<Item>()
            for (c in t.items) {
                val b = layout(c, size, m)
                items += b.shifted(x, 0.0); x += b.width; asc = max(asc, b.ascent); desc = max(desc, b.descent)
            }
            Box(x, asc, desc, items)
        }
        is Tex.Frac -> {
            val s2 = max(size * 0.8, 10.0).coerceAtMost(size)
            val n = layout(t.num, s2, m); val d = layout(t.den, s2, m)
            val w = max(n.width, d.width) + size * 0.3
            val axis = -size * 0.3; val gap = size * 0.12; val th = max(1.0, size * 0.06)
            val items = ArrayList<Item>()
            items += n.shifted((w - n.width) / 2, axis - gap - n.descent)
            items += d.shifted((w - d.width) / 2, axis + gap + d.ascent)
            items += Item.Rule(size * 0.05, axis, w - size * 0.05, axis, th)
            Box(w, -axis + gap + n.height, axis + gap + d.height, items)
        }
        is Tex.Script -> {
            val b = layout(t.base, size, m); val ss = max(size * 0.68, 8.0)
            val items = ArrayList(b.items); var w = b.width; var asc = b.ascent; var desc = b.descent
            var extra = 0.0
            t.sup?.let { sup ->
                val sb = layout(sup, ss, m); val dy = -(b.ascent * 0.55) - sb.descent * 0.3
                items += sb.shifted(b.width + size * 0.04, dy); extra = max(extra, sb.width + size * 0.04); asc = max(asc, -dy + sb.ascent)
            }
            t.sub?.let { sub ->
                val sb = layout(sub, ss, m); val dy = size * 0.25
                items += sb.shifted(b.width + size * 0.02, dy); extra = max(extra, sb.width + size * 0.02); desc = max(desc, dy + sb.descent)
            }
            w += extra
            Box(w, asc, desc, items)
        }
        is Tex.Sqrt -> {
            val b = layout(t.body, size, m)
            val th = max(1.2, size * 0.07); val top = b.ascent + size * 0.14
            val idx = t.index?.let { layout(it, max(size * 0.55, 8.0), m) }
            val lead = (idx?.width ?: 0.0).coerceAtLeast(size * 0.2)
            val hook = size * 0.55
            val items = ArrayList<Item>()
            idx?.let { items += it.shifted(0.0, -b.ascent * 0.35) }
            // √ sign: small tick, down stroke to the baseline area, long up stroke, then the bar over the body
            val x0 = lead - size * 0.2; val xv = x0 + hook * 0.35; val xt = x0 + hook
            items += Item.Rule(x0, -size * 0.25, xv - size * 0.05, -size * 0.3, th)
            items += Item.Rule(xv - size * 0.05, -size * 0.3, xv + size * 0.1, b.descent, th)
            items += Item.Rule(xv + size * 0.1, b.descent, xt, -top, th)
            items += Item.Rule(xt, -top, xt + b.width + size * 0.12, -top, th)
            items += b.shifted(xt + size * 0.06, 0.0)
            Box(xt + b.width + size * 0.16, top + th, b.descent + th, items)
        }
        is Tex.Accent -> {
            val b = layout(t.body, size, m); val th = max(1.0, size * 0.06)
            val y = -b.ascent - size * 0.1; val items = ArrayList(b.items)
            when (t.kind) {
                "vec" -> { items += Item.Rule(0.0, y, b.width, y, th); items += Item.Rule(b.width - size * 0.18, y - size * 0.12, b.width, y, th); items += Item.Rule(b.width - size * 0.18, y + size * 0.12, b.width, y, th) }
                "hat" -> { items += Item.Rule(0.0, y + size * 0.1, b.width / 2, y - size * 0.12, th); items += Item.Rule(b.width / 2, y - size * 0.12, b.width, y + size * 0.1, th) }
                else -> items += Item.Rule(0.0, y, b.width, y, th)
            }
            Box(b.width, b.ascent + size * 0.26, b.descent, items)
        }
    }
}
