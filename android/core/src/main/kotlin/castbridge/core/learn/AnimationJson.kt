package castbridge.core.learn

import castbridge.core.learn.LessonJson.ParseError
import kotlin.math.max

/**
 * Reads the `"animation"` object of an illustration block (format: docs/LEARN.md § Animations) and measures its size.
 * Structural mistakes (unknown op or target, overlapping segments…) are [ParseError]s; budgets, durations, flashing and
 * accessibility are checked by [AnimationRules].
 */
object AnimationJson {
    @Suppress("UNCHECKED_CAST")
    private fun Any?.obj(w: String): Map<String, Any?> = this as? Map<String, Any?> ?: throw ParseError("$w : objet attendu")
    private fun Map<String, Any?>.s(k: String) = this[k] as? String
    private fun Map<String, Any?>.d(k: String) = (this[k] as? Number)?.toDouble()
    private fun Map<String, Any?>.b(k: String) = this[k] as? Boolean
    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.l(k: String) = this[k] as? List<Any?> ?: emptyList()

    const val MAX_PARSE_ELEMENTS = 5000

    private class Seg(val prop: Prop, val t0: Double, val t1: Double, val to: Double, val ease: Ease)

    /** [fallback]: the static figure of the block; the animation takes its size when it gives none. */
    fun parse(m: Map<String, Any?>, w: String, fallback: Figure): AnimatedFigure {
        val aw = m.d("w") ?: fallback.w; val ah = m.d("h") ?: fallback.h
        val items = m.l("items")
        if (items.size > MAX_PARSE_ELEMENTS) throw ParseError("$w : trop d'éléments (${items.size})")
        val groupDefs = (m["groups"] as? Map<*, *>)?.mapNotNull { (k, v) -> (k as? String)?.let { it to v.obj("$w groupe $it") } }?.toMap() ?: emptyMap()
        val shapes = items.mapIndexed { k, o -> o.obj("$w élément #$k").let { it to LessonJson.shape(it, "$w élément #$k") } }
        val baseOps = shapes.map { (_, s) -> Scene.shape(s) }
        // explicit ids, else _<index>
        val ids = shapes.mapIndexed { k, (mm, _) -> mm.s("id")?.takeIf { it.isNotBlank() } ?: "_$k" }
        if (ids.toSet().size != ids.size) throw ParseError("$w : id d'élément en double « ${ids.groupingBy { it }.eachCount().filter { it.value > 1 }.keys.first()} »")
        val groupOf = shapes.map { (mm, _) -> mm.s("g") }
        for (g in groupOf) if (g != null && g in ids) throw ParseError("$w : le groupe « $g » porte le même nom qu'un élément")

        // group pivots: explicit, else centre of the union of the members' boxes
        val groupPivot = HashMap<String, DoubleArray>()
        for (g in groupOf.filterNotNull().toSet()) {
            val pv = groupDefs[g]?.l("pivot")?.mapNotNull { (it as? Number)?.toDouble() }
            if (pv != null && pv.size == 2) groupPivot[g] = doubleArrayOf(pv[0], pv[1])
            else {
                val bs = baseOps.indices.filter { groupOf[it] == g }.map { AnimGeom.bbox(baseOps[it]) }
                groupPivot[g] = doubleArrayOf((bs.minOf { it[0] } + bs.maxOf { it[2] }) / 2, (bs.minOf { it[1] } + bs.maxOf { it[3] }) / 2)
            }
        }

        // timeline → segments per element
        val segs = Array(items.size) { ArrayList<Seg>() }
        var actions = 0; var end = 0.0
        val acts = m.l("do")
        for ((n, o) in acts.withIndex()) {
            val a = o.obj("$w action #$n"); val aw0 = "$w action #$n"
            actions++
            val op = a.s("op") ?: throw ParseError("$aw0 : \"op\" manquant")
            val at = a.d("at") ?: throw ParseError("$aw0 : \"at\" manquant")
            val dur = a.d("d") ?: 0.5
            if (at < 0 || dur < 0) throw ParseError("$aw0 : temps négatif")
            val ease = if (a.containsKey("ease")) Ease.of(a.s("ease")) ?: throw ParseError("$aw0 : easing inconnu « ${a["ease"]} »") else Ease.IN_OUT
            val on = a.s("on") ?: throw ParseError("$aw0 : \"on\" manquant")
            val targets = ids.indices.filter { ids[it] == on || groupOf[it] == on }
            if (targets.isEmpty()) throw ParseError("$aw0 : cible « $on » inconnue")
            val vals = ArrayList<Pair<Prop, Double>>()
            fun need(k: String) = a.d(k) ?: throw ParseError("$aw0 : \"$k\" manquant pour $op")
            when (op) {
                "move" -> { if (a.d("dx") == null && a.d("dy") == null) throw ParseError("$aw0 : move sans dx ni dy")
                    a.d("dx")?.let { vals += Prop.TX to it }; a.d("dy")?.let { vals += Prop.TY to it } }
                "scale" -> vals += Prop.SCALE to need("s")
                "rotate" -> vals += Prop.ROT to need("deg")
                "fade" -> vals += Prop.ALPHA to (a.d("a") ?: 1.0).also { if (it !in 0.0..1.0) throw ParseError("$aw0 : a hors 0..1") }
                "color" -> {
                    if (a.s("fill") == null && a.s("stroke") == null) throw ParseError("$aw0 : color sans fill ni stroke")
                    for ((k, p) in listOf("fill" to Prop.FILL, "stroke" to Prop.STROKE)) a.s(k)?.let { cs ->
                        vals += p to (Palette.color(cs)?.toLong()?.and(0xFFFFFFFFL)?.toDouble() ?: throw ParseError("$aw0 : couleur « $cs » inconnue")) }
                }
                "draw" -> vals += Prop.DRAW to (a.d("p") ?: 1.0).also { if (it !in 0.0..1.0) throw ParseError("$aw0 : p hors 0..1") }
                "wipe" -> vals += Prop.WIPE to (a.d("p") ?: 1.0).also { if (it !in 0.0..1.0) throw ParseError("$aw0 : p hors 0..1") }
                "type" -> vals += Prop.TYPED to (a.d("p") ?: 1.0).also { if (it !in 0.0..1.0) throw ParseError("$aw0 : p hors 0..1") }
                "count" -> vals += Prop.VALUE to need("v")
                else -> throw ParseError("$aw0 : opération inconnue « $op »")
            }
            for (t in targets) for ((p, v) in vals) {
                if (p == Prop.TYPED && baseOps[t].none { it is Op.Text }) throw ParseError("$aw0 : type (machine à écrire) sur un élément sans texte")
                if (p == Prop.VALUE && !((shapes[t].second as? Shape.Text)?.text?.contains("{v}") ?: false)) throw ParseError("$aw0 : count sur un texte sans « {v} »")
                segs[t] += Seg(p, at, at + dur, v, ease)
            }
            end = max(end, at + dur)
        }

        val elements = ArrayList<AnimElement>()
        for (k in items.indices) {
            val (mm, shape) = shapes[k]
            val ops = baseOps[k]
            val gp = groupOf[k]?.let { groupPivot[it] }
            val own = mm.l("pivot").mapNotNull { (it as? Number)?.toDouble() }
            val bb = AnimGeom.bbox(ops)
            val piv = if (own.size == 2) doubleArrayOf(own[0], own[1]) else gp ?: doubleArrayOf((bb[0] + bb[2]) / 2, (bb[1] + bb[3]) / 2)
            val alpha0 = mm.d("alpha") ?: 1.0; val draw0 = mm.d("draw") ?: 1.0; val typed0 = mm.d("typed") ?: 1.0; val v0 = mm.d("v") ?: 0.0
            val wipe0 = mm.d("wipe") ?: 1.0; val dir = mm.s("dir") ?: "right"
            if (dir !in listOf("right", "left", "up", "down")) throw ParseError("$w élément ${ids[k]} : dir inconnu « $dir »")
            for ((nm, v) in listOf("alpha" to alpha0, "draw" to draw0, "typed" to typed0, "wipe" to wipe0)) if (v !in 0.0..1.0) throw ParseError("$w élément ${ids[k]} : $nm hors 0..1")
            val tracks = arrayOfNulls<Track>(Prop.values().size)
            for (p in Prop.values()) {
                val list = segs[k].filter { it.prop == p }.sortedBy { it.t0 }
                if (list.isEmpty()) continue
                for (i in 1 until list.size) if (list[i].t0 < list[i - 1].t1 - 1e-9)
                    throw ParseError("$w élément ${ids[k]} : deux actions ${p.name.lowercase()} se chevauchent (à ${list[i].t0} s)")
                val init = when (p) {
                    Prop.TX, Prop.TY, Prop.ROT -> 0.0; Prop.VALUE -> v0
                    Prop.SCALE -> 1.0; Prop.ALPHA -> alpha0; Prop.DRAW -> draw0; Prop.TYPED -> typed0; Prop.WIPE -> wipe0
                    Prop.FILL -> baseColor(ops, true) ?: throw ParseError("$w élément ${ids[k]} : color fill sans remplissage")
                    Prop.STROKE -> baseColor(ops, false) ?: throw ParseError("$w élément ${ids[k]} : color stroke sans trait")
                }
                tracks[p.ordinal] = Track(p, init, DoubleArray(list.size) { list[it].t0 }, DoubleArray(list.size) { list[it].t1 },
                    DoubleArray(list.size) { list[it].to }, Array(list.size) { list[it].ease })
            }
            elements += AnimElement(ids[k], groupOf[k], shape, ops, piv[0], piv[1], alpha0, draw0, typed0, v0, (mm.d("dec") ?: 0.0).toInt().coerceIn(0, 4), tracks, wipe0, dir)
        }

        val stops = m.l("steps").mapIndexed { n, o ->
            val s = o.obj("$w étape #$n")
            AnimStop(s.d("at") ?: throw ParseError("$w étape #$n : \"at\" manquant"), s.s("say") ?: throw ParseError("$w étape #$n : \"say\" manquant"))
        }
        for (i in 1 until stops.size) if (stops[i].at <= stops[i - 1].at) throw ParseError("$w : les étapes doivent être strictement croissantes")
        val mode = m.s("mode") ?: "auto"
        if (mode != "auto" && mode != "steps") throw ParseError("$w : mode inconnu « $mode » (auto | steps)")
        if (mode == "steps" && stops.isEmpty()) throw ParseError("$w : mode steps sans étapes")
        stops.lastOrNull()?.let { end = max(end, it.at) }
        return AnimatedFigure(aw, ah, elements, stops, mode == "steps", m.b("loop") ?: false, end, sizeBytes(m), actions)
    }

    private fun baseColor(ops: List<Op>, fill: Boolean): Double? {
        for (op in ops) {
            val c: Int? = when (op) {
                is Op.Circle -> if (fill) op.fill else op.stroke; is Op.Rect -> if (fill) op.fill else op.stroke
                is Op.Path -> if (fill) op.fill else op.stroke; is Op.Line -> if (fill) null else op.color
                is Op.Text -> if (fill) op.color else null; else -> null
            }
            if (c != null) return (c.toLong() and 0xFFFFFFFFL).toDouble()
        }
        return null
    }

    /** UTF-8 size of the compact JSON of [v] (what the budget counts). */
    fun sizeBytes(v: Any?): Int = StringBuilder().also { write(v, it) }.toString().toByteArray(Charsets.UTF_8).size

    fun write(v: Any?, sb: StringBuilder) {
        when (v) {
            null -> sb.append("null")
            is Map<*, *> -> { sb.append('{'); var first = true
                for ((k, x) in v) { if (!first) sb.append(','); first = false; str(k.toString(), sb); sb.append(':'); write(x, sb) }; sb.append('}') }
            is List<*> -> { sb.append('['); v.forEachIndexed { i, x -> if (i > 0) sb.append(','); write(x, sb) }; sb.append(']') }
            is String -> str(v, sb)
            is Boolean -> sb.append(v)
            is Double -> sb.append(if (v == Math.floor(v) && Math.abs(v) < 1e15) v.toLong().toString() else v.toString())
            is Number -> sb.append(v.toString())
            else -> str(v.toString(), sb)
        }
    }

    private fun str(s: String, sb: StringBuilder) {
        sb.append('"')
        for (c in s) when (c) { '"' -> sb.append("\\\""); '\\' -> sb.append("\\\\"); '\n' -> sb.append("\\n"); else -> if (c < ' ') sb.append(String.format("\\u%04x", c.code)) else sb.append(c) }
        sb.append('"')
    }
}
