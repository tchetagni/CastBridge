package castbridge.core.learn

import kotlin.math.abs

/**
 * Checks a pack (what the tests, the pack builder and the TV before activating a pack all run). [errors] block the pack;
 * [warnings] are shown to authors. Rules: docs/LEARN.md § Validation.
 */
class LessonValidator(private val knownLessons: Set<String> = emptySet()) {
    class Report(val errors: List<String>, val warnings: List<String>) { val ok get() = errors.isEmpty() }

    companion object {
        /** A page of the TV reader shows one block: longer texts must be split into several blocks. */
        const val MAX_BLOCK_CHARS = 650
        const val MAX_STEP_CHARS = 320
        private val ID = Regex("^[a-z0-9][a-z0-9-]{1,80}$")
    }

    fun validate(p: Pack): Report {
        val e = ArrayList<String>(); val warn = ArrayList<String>()
        if (!ID.matches(p.id)) e += "pack: id « ${p.id} » invalide (minuscules, chiffres, tirets)"
        if (p.version < 1) e += "pack: version < 1"
        val cur = LearnCatalog.cursus(p.cursus)
        if (cur == null) e += "pack: cursus inconnu « ${p.cursus} »"
        val lv = LearnCatalog.level(p.level)
        if (lv == null) e += "pack: niveau inconnu « ${p.level} »" else if (lv.cursus != p.cursus) e += "pack: niveau ${p.level} hors du cursus ${p.cursus}"
        if (cur != null && cur.lang != p.lang) e += "pack: langue ${p.lang} ≠ langue du cursus (${cur.lang})"
        if (LearnCatalog.subject(p.subject) == null) e += "pack: matière inconnue « ${p.subject} »"
        p.exam?.let { x ->
            val ex = LearnCatalog.exam(x)
            if (ex == null) e += "pack: examen inconnu « $x »"
            else {
                if (ex.level != p.level) e += "pack: l'examen $x se passe en ${ex.level}, pas en ${p.level}"
                p.series.filter { it !in ex.series }.forEach { e += "pack: série $it inconnue pour $x" }
            }
        }
        if (p.chapters.isEmpty()) e += "pack: aucun chapitre"
        dup(p.chapters.map { it.id }).forEach { e += "chapitre $it en double" }
        val chapterIds = p.chapters.map { it.id }.toSet()
        dup(p.lessons.map { it.id }).forEach { e += "leçon $it en double" }
        val allExIds = p.exercises.flatMap { listOf(it.id) + it.parts.map { q -> q.id } }
        dup(allExIds).forEach { e += "exercice $it en double" }
        val lessonIds = p.lessons.map { it.id }.toSet()
        var illustrations = 0

        for (l in p.lessons) {
            val w = "leçon ${l.id}"
            if (!l.id.startsWith(p.id + "-")) e += "$w: l'id doit commencer par « ${p.id}- »"
            if (l.chapter !in chapterIds) e += "$w: chapitre ${l.chapter} inconnu"
            if (l.blocks.isEmpty()) e += "$w: aucun bloc"
            if (l.minutes !in 1..240) e += "$w: durée ${l.minutes} min hors 1..240"
            for (pr in l.prerequisites) if (pr !in lessonIds && pr !in knownLessons) e += "$w: prérequis $pr introuvable"
            if (l.id in l.prerequisites) e += "$w: prérequis de lui-même"
            l.level?.let { if (it !in castbridge.core.curriculum.Level.KEYS) e += "$w: niveau « $it » inconnu (N0..N4)" }
            if (l.skill != null && !castbridge.core.curriculum.SkillGraph.ID.matches(l.skill)) e += "$w: identifiant de compétence « ${l.skill} » invalide"
            l.objectives.forEach { md(it, "$w objectif", e) }
            l.blocks.forEachIndexed { k, b ->
                val bw = "$w bloc #$k"
                when (b) {
                    is Block.Heading -> if (b.text.isBlank()) e += "$bw: titre vide"
                    is Block.Text -> { md(b.md, bw, e); len(b.md, MAX_BLOCK_CHARS, bw, e) }
                    is Block.Key -> {
                        md(b.md, bw, e); len(b.md, MAX_BLOCK_CHARS, bw, e); b.tex?.let { tex(it, bw, e) }
                        if (b.style !in setOf("definition", "retenir", "attention", "methode", "objectifs", "pieges", "propriete", "formule"))
                            e += "$bw: style « ${b.style} » inconnu"
                    }
                    is Block.Formula -> tex(b.tex, bw, e)
                    is Block.Example -> {
                        md(b.statement, "$bw énoncé", e); len(b.statement, MAX_BLOCK_CHARS, "$bw énoncé", e)
                        if (b.steps.size < 2) e += "$bw: un exemple résolu a au moins 2 étapes"
                        b.steps.forEachIndexed { j, s -> md(s.md, "$bw étape ${j + 1}", e); len(s.md, MAX_STEP_CHARS, "$bw étape ${j + 1}", e); s.tex?.let { tex(it, "$bw étape ${j + 1}", e) } }
                        b.answer?.let { md(it, "$bw réponse", e) }
                        b.figure?.let { figure(it, "$bw figure", e, warn); illustrations++ }
                    }
                    is Block.Illustration -> { figure(b.figure, bw, e, warn); illustrations++; if (b.alt.isBlank()) e += "$bw: texte alternatif (alt) manquant" }
                    is Block.Video -> if (b.src != null && !(b.src.startsWith("library:") || b.src.startsWith("https://") || b.src.startsWith("http://")))
                        e += "$bw: source vidéo « ${b.src} » : library:<nom> ou URL http(s) seulement"
                    is Block.Audio -> if (b.text.isBlank()) e += "$bw: texte vide"
                    is Block.ExerciseRef -> if (p.exercise(b.id) == null) e += "$bw: exercice ${b.id} introuvable"
                    is Block.More -> b.items.forEach { md(it, bw, e) }
                }
            }
            for (x in l.exercises) if (p.exercise(x) == null) e += "$w: exercice $x introuvable"
            for (x in l.selfCheck) {
                val q = p.exercise(x)
                if (q == null) e += "$w: question d'auto-évaluation $x introuvable"
                else if (q.kind != ExerciseKind.MCQ || q.choices.size != 4) e += "$w: l'auto-évaluation $x doit être un QCM à 4 choix (format du quiz)"
            }
            if (p.exam != null) ficheRules(p, l, w, e, warn)
        }
        if (p.lessons.isNotEmpty() && illustrations < 2) e += "pack: au moins 2 illustrations (il y en a $illustrations)"

        for (x in p.exercises) {
            exercise(x, "exercice ${x.id}", e, warn, false)
            if (!x.id.startsWith(p.id + "-")) e += "exercice ${x.id}: l'id doit commencer par « ${p.id}- »"
            if (x.chapter !in chapterIds) e += "exercice ${x.id}: chapitre ${x.chapter} inconnu"
            x.lesson?.let { if (it !in lessonIds) e += "exercice ${x.id}: fiche de renvoi $it introuvable" }
        }
        dup(p.mockExams.map { it.id }).forEach { e += "épreuve blanche $it en double" }
        for (m in p.mockExams) {
            val w = "épreuve blanche ${m.id}"
            if (m.minutes !in 10..300) e += "$w: durée ${m.minutes} min hors 10..300"
            if (m.sections.isEmpty()) e += "$w: aucune partie"
            val xs = m.exerciseIds.map { it to p.exercise(it) }
            xs.filter { it.second == null }.forEach { e += "$w: exercice ${it.first} introuvable" }
            dup(m.exerciseIds).forEach { e += "$w: exercice $it utilisé deux fois" }
            xs.mapNotNull { it.second }.filter { it.review }.forEach { e += "$w: l'exercice ${it.id} est marqué review (à vérifier) : pas dans une épreuve" }
            val total = xs.mapNotNull { it.second?.totalPoints }.sum()
            if (abs(total - m.outOf) > 0.01) e += "$w: le barème fait ${Scene.fmt(total)} points au lieu de ${Scene.fmt(m.outOf)}"
        }
        return Report(e, warn)
    }

    /** The « fiche type » of exam packs (docs/LEARN.md): objectives, 2 worked examples, graded exercises, 5-question self-check. */
    private fun ficheRules(p: Pack, l: Lesson, w: String, e: MutableList<String>, warn: MutableList<String>) {
        if (l.objectives.isEmpty()) e += "$w: objectifs manquants (fiche type)"
        val examples = l.blocks.count { it is Block.Example }
        if (examples < 2) e += "$w: 2 exemples résolus attendus (fiche type), il y en a $examples"
        if (l.blocks.none { it is Block.Key }) e += "$w: « l'essentiel » (bloc key) manquant"
        if (l.blocks.none { it is Block.Key && (it.style == "pieges" || it.style == "attention") }) warn += "$w: pas de bloc « pièges / erreurs fréquentes »"
        val tiers = l.exercises.mapNotNull { p.exercise(it)?.tier }.groupingBy { it }.eachCount()
        if ((tiers[ExerciseTier.APPLICATION] ?: 0) < 3) e += "$w: 3 exercices d'application attendus (${tiers[ExerciseTier.APPLICATION] ?: 0})"
        if ((tiers[ExerciseTier.DEEPER] ?: 0) < 2) e += "$w: 2 exercices d'approfondissement attendus (${tiers[ExerciseTier.DEEPER] ?: 0})"
        if ((tiers[ExerciseTier.EXAM] ?: 0) < 1) e += "$w: 1 exercice type examen attendu"
        if (l.selfCheck.size != 5) e += "$w: 5 questions d'auto-évaluation attendues (${l.selfCheck.size})"
        if (l.blocks.none { it is Block.Illustration || (it is Block.Example && it.figure != null) }) warn += "$w: aucune illustration"
    }

    private fun exercise(x: Exercise, w: String, e: MutableList<String>, warn: MutableList<String>, part: Boolean) {
        md(x.prompt, "$w énoncé", e); len(x.prompt, MAX_BLOCK_CHARS, "$w énoncé", e)
        x.tex?.let { tex(it, w, e) }
        x.figure?.let { figure(it, "$w figure", e, warn) }
        if (x.difficulty !in 1..5) e += "$w: difficulté ${x.difficulty} hors 1..5"
        x.level?.let { if (it !in castbridge.core.curriculum.Level.KEYS) e += "$w: niveau « $it » inconnu (N0..N4)" }
        if (x.tier.excellence && x.difficulty < 3) e += "$w: un exercice ${x.tier.key} a une difficulté ≥ 3 (${x.difficulty})"
        x.calibration.forEach { (k, v) -> if (k !in castbridge.core.curriculum.Level.KEYS || v !in 0.0..1.0) e += "$w: calibration « $k » = $v invalide (niveau N0..N4, taux 0..1)" }
        if (x.kind != ExerciseKind.PROBLEM && x.points <= 0) e += "$w: points ≤ 0"
        if (x.kind != ExerciseKind.PROBLEM && x.explanation.isBlank()) e += "$w: explication (corrigé) manquante"
        md(x.explanation, "$w explication", e)
        x.steps.forEach { md(it, "$w étape", e) }
        x.method?.let { md(it, "$w méthode", e) }
        x.mistakes.forEach { md(it, "$w erreur fréquente", e) }
        when (x.kind) {
            ExerciseKind.MCQ -> {
                if (x.choices.size !in 2..6) e += "$w: 2 à 6 choix"
                if (x.choices.map { it.trim().lowercase() }.toSet().size != x.choices.size) e += "$w: choix en double"
                if (x.choices.any { it.isBlank() }) e += "$w: choix vide"
                if (x.answerIndex !in x.choices.indices) e += "$w: index de réponse ${x.answerIndex} hors des choix"
                x.choices.forEach { md(it, "$w choix", e) }
                if (x.choices.any { it.lowercase().startsWith("toutes ces") || it.lowercase().startsWith("aucune de") || it.lowercase().startsWith("all of") || it.lowercase().startsWith("none of") })
                    e += "$w: pas de choix dépendant de l'ordre (les choix sont mélangés)"
            }
            ExerciseKind.TRUE_FALSE -> if (x.answerBool == null) e += "$w: réponse vrai/faux manquante"
            ExerciseKind.NUMERIC -> {
                val a = x.answerNumber
                if (a == null || !a.isFinite()) e += "$w: réponse numérique invalide"
                if (x.tolerance < 0) e += "$w: tolérance négative"
                if (a != null && a != 0.0 && x.tolerance > abs(a) * 0.2) warn += "$w: tolérance large (${x.tolerance})"
            }
            ExerciseKind.MATCHING -> {
                if (x.pairs.size !in 2..6) e += "$w: 2 à 6 paires"
                if (x.pairs.map { it.first.trim().lowercase() }.toSet().size != x.pairs.size) e += "$w: éléments de gauche en double"
                if (x.pairs.map { it.second.trim().lowercase() }.toSet().size != x.pairs.size) e += "$w: éléments de droite en double"
                if (x.pairs.any { it.first.isBlank() || it.second.isBlank() }) e += "$w: paire incomplète"
            }
            ExerciseKind.OPEN -> {
                if (x.model.isNullOrBlank()) e += "$w: corrigé modèle (model) manquant"
                x.model?.let { md(it, "$w corrigé", e) }
                x.rubric.forEach { md(it, "$w barème", e) }
            }
            ExerciseKind.PROBLEM -> {
                if (part) e += "$w: un problème ne peut pas contenir de problème"
                if (x.parts.size < 2) e += "$w: un problème a au moins 2 questions"
                x.parts.forEachIndexed { k, q -> exercise(q, "$w question ${k + 1} (${q.id})", e, warn, true) }
                if (x.parts.any { !it.id.startsWith(x.id + "-") }) e += "$w: les questions ont des id « ${x.id}-… »"
            }
        }
    }

    private fun md(s: String, w: String, e: MutableList<String>) {
        try { Markdown.parse(s) } catch (ex: IllegalArgumentException) { e += "$w: ${ex.message}" }
    }

    private fun tex(s: String, w: String, e: MutableList<String>) {
        try { Tex.parse(s) } catch (ex: IllegalArgumentException) { e += "$w: formule : ${ex.message}" }
    }

    private fun len(s: String, max: Int, w: String, e: MutableList<String>) {
        val n = runCatching { Markdown.plain(s).length }.getOrDefault(s.length)
        if (n > max) e += "$w: texte trop long pour un écran ($n caractères, max $max) : découpez-le en plusieurs blocs"
    }

    private fun figure(f: Figure, w: String, e: MutableList<String>, warn: MutableList<String>) {
        val colors = ArrayList<String?>()
        when (f) {
            is Figure.Shapes -> {
                if (f.items.isEmpty()) e += "$w: figure vide"
                for (s in f.items) when (s) {
                    is Shape.Line -> colors += s.color; is Shape.Circle -> { colors += s.fill; colors += s.stroke }
                    is Shape.Rect -> { colors += s.fill; colors += s.stroke }; is Shape.Poly -> { colors += s.fill; colors += s.stroke; if (s.pts.size < 4 || s.pts.size % 2 != 0) e += "$w: polygone : pts pairs x,y (≥ 2 points)" }
                    is Shape.Text -> { colors += s.color; if (s.size < 10) warn += "$w: texte « ${s.text} » très petit (${s.size})" }
                    is Shape.Angle -> colors += s.color; is Shape.Path -> { colors += s.fill; colors += s.stroke }
                }
            }
            is Figure.Plot -> {
                if (f.xmin >= f.xmax || f.ymin >= f.ymax) e += "$w: bornes du graphique invalides"
                f.curves.forEach { c ->
                    colors += c.color
                    try {
                        val ex = Expr.parse(c.expr)
                        val finite = (0..20).count { k -> ex.eval(f.xmin + (f.xmax - f.xmin) * k / 20).isFinite() }
                        if (finite < 3) e += "$w: la courbe « ${c.expr} » n'a presque aucun point défini"
                    } catch (ex: IllegalArgumentException) { e += "$w: courbe « ${c.expr} » : ${ex.message}" }
                }
                f.points.forEach { colors += it.color; if (it.x !in f.xmin..f.xmax || it.y !in f.ymin..f.ymax) e += "$w: point ${it.label ?: ""} hors du graphique" }
            }
            is Figure.Timeline -> {
                if (f.from >= f.to) e += "$w: frise : from ≥ to"
                f.events.forEach { if (it.year !in f.from..f.to) e += "$w: événement ${it.year} hors de la frise" }
                f.periods.forEach { colors += it.color; if (it.from < f.from || it.to > f.to || it.from >= it.to) e += "$w: période « ${it.label} » invalide" }
            }
            is Figure.Bars -> { if (f.bars.isEmpty()) e += "$w: aucune barre"; f.bars.forEach { colors += it.color; if (it.value < 0) e += "$w: valeur négative" } }
            is Figure.Count -> { colors += f.color; if (f.n !in 1..30) e += "$w: n hors 1..30" }
            is Figure.Svg -> f.paths.forEach { colors += it.fill; colors += it.stroke }
        }
        colors.filter { !Palette.valid(it) }.forEach { e += "$w: couleur « $it » inconnue" }
        if (f.w <= 0 || f.h <= 0 || f.w / f.h > 4 || f.h / f.w > 3) e += "$w: dimensions ${f.w}×${f.h} invalides (rapport largeur/hauteur entre 1/3 et 4)"
        try {
            val sc = Scene.build(f)
            // Labels must stay inside the figure (5 % margin).
            for (b in sc.textBoxes()) if (b[0] < -sc.w * 0.05 || b[2] > sc.w * 1.05 || b[1] < -sc.h * 0.05 || b[3] > sc.h * 1.05)
                warn += "$w: un texte dépasse du cadre de la figure"
        } catch (ex: IllegalArgumentException) { e += "$w: ${ex.message}" }
    }

    private fun dup(l: List<String>) = l.groupingBy { it }.eachCount().filter { it.value > 1 }.keys
}
