package castbridge.core.learn

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Validation rules of an animated illustration (called by [LessonValidator]) and the layout linter (labels over time).
 * Budgets are the media policy of the content network: an animation is vector keyframes, never more than [MAX_BYTES].
 */
object AnimationRules {
    const val MAX_BYTES = 40 * 1024
    const val WARN_BYTES = 24 * 1024
    const val MAX_ELEMENTS = 120
    const val MAX_ACTIONS = 400
    const val MAX_STEPS = 40
    const val MIN_DURATION = 0.2
    const val MAX_DURATION = 30.0
    const val MAX_CAPTION = 160
    /** Photosensitivity: at most this many flashes (a bright change and its return) per second. */
    const val MAX_FLASHES_PER_SECOND = 3
    /** An element covering at least this share of the figure can flash. */
    const val FLASH_AREA = 0.10
    const val MIN_ALT = 12

    fun check(a: AnimatedFigure, fallback: Figure, alt: String, w: String, e: MutableList<String>, warn: MutableList<String>) {
        if (a.sizeBytes > MAX_BYTES) e += "$w: animation de ${a.sizeBytes} octets (max $MAX_BYTES)"
        else if (a.sizeBytes > WARN_BYTES) warn += "$w: animation lourde (${a.sizeBytes} octets, ${WARN_BYTES} conseillés)"
        if (a.elements.size > MAX_ELEMENTS) e += "$w: ${a.elements.size} éléments animés (max $MAX_ELEMENTS)"
        if (a.actionCount > MAX_ACTIONS) e += "$w: ${a.actionCount} actions (max $MAX_ACTIONS)"
        if (a.stops.size > MAX_STEPS) e += "$w: ${a.stops.size} étapes (max $MAX_STEPS)"
        if (a.duration < MIN_DURATION || a.duration > MAX_DURATION) e += "$w: durée ${a.duration} s hors $MIN_DURATION..$MAX_DURATION s"
        if (a.w != fallback.w || a.h != fallback.h) e += "$w: l'animation (${a.w}×${a.h}) doit avoir la taille de la figure de repli (${fallback.w}×${fallback.h})"
        if (alt.trim().length < MIN_ALT) e += "$w: texte alternatif (alt) de l'animation trop court (décrire ce qui se passe, ≥ $MIN_ALT caractères)"
        if (a.stepMode && a.loop) e += "$w: une animation par étapes ne peut pas boucler"
        if (a.stepMode && a.stops.isNotEmpty() && abs(a.stops.last().at - a.duration) > 0.01) e += "$w: la dernière étape doit tomber à la fin de l'animation (${a.duration} s)"
        a.stops.forEachIndexed { i, s ->
            if (s.say.isBlank()) e += "$w étape ${i + 1}: légende vide"
            if (s.say.length > MAX_CAPTION) e += "$w étape ${i + 1}: légende trop longue (${s.say.length} > $MAX_CAPTION)"
            if (s.at < MIN_DURATION) e += "$w étape ${i + 1}: arrêt avant ${MIN_DURATION} s"
        }
        if (a.stops.isEmpty() && a.duration > 8) warn += "$w: animation longue sans étapes ni légendes"
        for (el in a.elements) {
            for (p in Prop.values()) {
                val t = el.track(p) ?: continue
                for (k in 0 until t.size) {
                    val d = t.t1[k] - t.t0[k]
                    if (d > 0 && d < MIN_DURATION - 1e-9) e += "$w: ${el.id} ${p.name.lowercase()} dure ${d} s (min $MIN_DURATION s, ou 0 pour un changement instantané)"
                    if (d > MAX_DURATION + 1e-9) e += "$w: ${el.id} ${p.name.lowercase()} dure ${d} s (max $MAX_DURATION s)"
                    if (p == Prop.SCALE && (t.to[k] < 0 || t.to[k] > 10)) e += "$w: ${el.id} échelle ${t.to[k]} hors 0..10"
                    if ((p == Prop.TX || p == Prop.TY) && abs(t.to[k]) > 4 * max(a.w, a.h)) e += "$w: ${el.id} déplacement ${t.to[k]} hors de portée"
                }
            }
            if (el.draw0 < 1.0 || el.track(Prop.DRAW) != null) if (el.flats?.all { it == null } == true) warn += "$w: ${el.id} : « draw » sans trait à tracer"
            if ((el.typed0 < 1.0 || el.track(Prop.TYPED) != null) && (el.shape as? Shape.Text)?.anchor != "start") warn += "$w: ${el.id} : machine à écrire : utiliser anchor \"start\" (sinon le texte se recentre)"
        }
        flashes(a, w, e)
        for (m in AnimationLint.lint(a)) warn += "$w: $m"
    }

    /** One abrupt change of a large element: |Δ opacity| ≥ 0.5, or a luminance jump ≥ 0.5, within 0.34 s. */
    private class Event(val t: Double)

    fun flashes(a: AnimatedFigure, w: String, e: MutableList<String>) {
        val area = a.w * a.h
        val ev = ArrayList<Event>()
        for (el in a.elements) {
            val bb = el.bbox
            if ((bb[2] - bb[0]) * (bb[3] - bb[1]) < FLASH_AREA * area) continue
            for (p in listOf(Prop.ALPHA, Prop.FILL)) {
                val t = el.track(p) ?: continue
                var cur = t.init
                for (k in 0 until t.size) {
                    val big = if (p == Prop.ALPHA) abs(t.to[k] - cur) >= 0.5 else abs(lum(t.to[k]) - lum(cur)) >= 0.5
                    if (big && t.t1[k] - t.t0[k] < 0.34) ev += Event(t.t0[k])
                    cur = t.to[k]
                }
            }
        }
        if (ev.isEmpty()) return
        val times = ev.map { it.t }.sorted().toMutableList()
        // a loop plays its first second again right after the last one
        if (a.loop) ev.forEach { times += it.t + a.duration }
        times.sort()
        // two events = one flash: more than 2 × MAX events in any 1-second window is too many
        for (i in times.indices) {
            val inWin = times.count { it >= times[i] - 1e-9 && it < times[i] + 1.0 }
            if (inWin > 2 * MAX_FLASHES_PER_SECOND) { e += "$w: plus de $MAX_FLASHES_PER_SECOND flashs par seconde vers ${"%.1f".format(times[i])} s"; return }
        }
    }

    private fun lum(c: Double): Double {
        val v = c.toLong().toInt()
        return (0.2126 * ((v shr 16) and 0xFF) + 0.7152 * ((v shr 8) and 0xFF) + 0.0722 * (v and 0xFF)) / 255.0
    }
}

/** Overlapping or overflowing labels at several timestamps of an animation (same idea as the static `figureTextsDoNotOverlap` test). */
object AnimationLint {
    fun times(a: AnimatedFigure, samples: Int = 16): List<Double> {
        val ts = sortedSetOf(0.0, a.duration)
        for (s in a.stops) ts += s.at
        // step mode: what the learner reads is the frame at each pause; labels crossing during a move (a swap) are not a fault
        if (a.stepMode) return ts.toList()
        val bounds = ts.toList()
        for (i in 1 until bounds.size) ts += (bounds[i - 1] + bounds[i]) / 2
        for (k in 0..samples) ts += a.duration * k / samples
        return ts.toList()
    }

    /** Messages like « t = 2.5 s : « a » / « b » se chevauchent »; each pair or label is reported once. */
    fun lint(a: AnimatedFigure, samples: Int = 16): List<String> {
        val out = ArrayList<String>(); val seen = HashSet<String>()
        for (t in times(a, samples)) {
            val sc = a.frameAt(t)
            val texts = sc.ops.filterIsInstance<Op.Text>().filter { (it.color ushr 24) >= 0xC0 && it.text.isNotEmpty() }
            val boxes = texts.map { tx -> Scene(sc.w, sc.h, listOf(tx)).textBoxes(0.5)[0] }
            for (i in boxes.indices) {
                val b = boxes[i]
                if ((b[0] < -sc.w * 0.05 || b[2] > sc.w * 1.05 || b[1] < -sc.h * 0.05 || b[3] > sc.h * 1.05) && seen.add("out:" + texts[i].text))
                    out += "t = ${"%.1f".format(t)} s : « ${texts[i].text} » dépasse du cadre"
                for (j in i + 1 until boxes.size) {
                    val c = boxes[j]
                    val ow = min(b[2], c[2]) - max(b[0], c[0]); val oh = min(b[3], c[3]) - max(b[1], c[1])
                    if (ow <= 0 || oh <= 0) continue
                    val smaller = min((b[2] - b[0]) * (b[3] - b[1]), (c[2] - c[0]) * (c[3] - c[1]))
                    if (ow * oh > 0.2 * smaller && seen.add(texts[i].text + "|" + texts[j].text))
                        out += "t = ${"%.1f".format(t)} s : « ${texts[i].text} » / « ${texts[j].text} » se chevauchent"
                }
            }
        }
        return out
    }
}
