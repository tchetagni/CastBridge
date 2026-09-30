package castbridge.receiver

import android.graphics.Color
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import castbridge.core.learn.Answer
import castbridge.core.learn.Exercise
import castbridge.core.learn.ExerciseKind
import castbridge.core.learn.Mark
import castbridge.core.learn.Markdown
import castbridge.core.learn.Marking
import castbridge.core.learn.Scene
import castbridge.core.learn.Shuffle

/**
 * One exercise on the TV, answered with the remote (or the phone, see [remoteChoice]…). PRACTICE shows the corrected
 * answer at once (✓ / ✗ never by colour alone, the right answer, the commented correction, the method, frequent
 * mistakes); EXAM (mock exam) only records the answer. A PROBLEM is answered part after part.
 */
class ExerciseView(
    private val st: LearnStyle,
    private val x: Exercise,
    private val exam: Boolean,
    private val lang: String,
    private val round: Int = 0,
    private val onAnswer: (Answer, Mark) -> Unit,
    private val onContinue: () -> Unit,
) {
    val view: LinearLayout = LinearLayout(st.act).apply { orientation = LinearLayout.VERTICAL }
    private val en = lang == "en"
    private var part = 0
    private val partAnswers = ArrayList<Answer?>()
    /** Current sub-exercise (the exercise itself, or the current part of a problem). */
    private val cur: Exercise get() = if (x.kind == ExerciseKind.PROBLEM) x.parts[part] else x
    /** Shown order of the current choices (MCQ) / right items (MATCHING). */
    var shown: List<Int> = emptyList(); private set
    var answered = false; private set
    var lastMark: Mark? = null; private set
    private var numberText = ""
    private var numberField: TextView? = null
    private var firstFocus: View? = null
    private val matchLeft = LinkedHashMap<String, String>()
    private var pickedLeft: String? = null

    init { render() }

    fun t(fr: String, enS: String) = if (en) enS else fr

    fun focus() { (firstFocus ?: view).requestFocus() }

    private fun render() {
        view.removeAllViews(); firstFocus = null
        val c = cur
        val scroll = ScrollView(st.act).apply { isVerticalScrollBarEnabled = true; isFillViewport = false; clipChildren = true }
        val col = LinearLayout(st.act).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(col)
        // statement: the problem's shared text, then the part
        if (x.kind == ExerciseKind.PROBLEM) {
            col.addView(st.text(st.md(x.prompt), 24f, Color.WHITE), view.lp(bottom = st.px(8)))
            col.addView(st.text(t("Question ${part + 1} sur ${x.parts.size}", "Question ${part + 1} of ${x.parts.size}") + "  ·  " + pts(c.points), 20f, LearnStyle.ACCENT, true))
        }
        col.addView(st.text(st.md(c.prompt), 28f, Color.WHITE, x.kind != ExerciseKind.PROBLEM), view.lp(bottom = st.px(8)))
        (c.tex ?: x.tex.takeIf { part == 0 && x.kind == ExerciseKind.PROBLEM })?.let { col.addView(FormulaView(st.act, it, st.px(34).toFloat()), view.lp()) }
        val fig = c.figure ?: x.figure
        val body = LinearLayout(st.act).apply { orientation = LinearLayout.HORIZONTAL }
        val answerCol = LinearLayout(st.act).apply { orientation = LinearLayout.VERTICAL }
        if (fig != null) {
            body.addView(answerCol, LinearLayout.LayoutParams(0, -2, 1.1f))
            body.addView(FigureView(st.act, Scene.build(fig)), LinearLayout.LayoutParams(0, st.px(300), 1f).apply { leftMargin = st.px(20) })
        } else body.addView(answerCol, LinearLayout.LayoutParams(-1, -2))
        col.addView(body, view.lp(top = st.px(6)))
        if (!answered) answerControls(c, answerCol) else correction(c, answerCol)
        view.addView(scroll, LinearLayout.LayoutParams(-1, -1))
        view.post { focus() }
    }

    private fun pts(p: Double) = Scene.fmt(p) + if (en) (if (p == 1.0) " mark" else " marks") else " pt" + if (p > 1) "s" else ""

    private fun answerControls(c: Exercise, box: LinearLayout) {
        when (c.kind) {
            ExerciseKind.MCQ -> {
                shown = Shuffle.order(c.choices.size, Shuffle.seed(c.id, round))
                val letters = "ABCDEF"
                val btns = shown.mapIndexed { i, orig -> st.button("${letters[i]}.  " + Markdown.plain(c.choices[orig]), size = 25f) { submit(Answer.Choice(orig)) } }
                if (btns.size == 4 && c.choices.all { Markdown.plain(it).length <= 28 }) box.addView(st.grid(btns, 2, 14))
                else btns.forEach { box.addView(it, box.lp(bottom = st.px(10))) }
                firstFocus = btns.firstOrNull()
            }
            ExerciseKind.TRUE_FALSE -> {
                val a = st.button(t("Vrai", "True"), size = 30f) { submit(Answer.Bool(true)) }
                val b = st.button(t("Faux", "False"), size = 30f) { submit(Answer.Bool(false)) }
                box.addView(st.grid(listOf(a, b), 2, 20)); firstFocus = a
            }
            ExerciseKind.NUMERIC -> numericPad(c, box)
            ExerciseKind.MATCHING -> matching(c, box)
            ExerciseKind.OPEN -> {
                box.addView(st.text(t("Réponds sur ton cahier (ou à voix haute), puis compare avec le corrigé.", "Answer in your exercise book (or aloud), then compare with the model answer."), 22f, LearnStyle.MUTED), box.lp(bottom = st.px(10)))
                val b = st.button(if (exam) t("J'ai répondu : question suivante", "I have answered: next question") else t("Voir le corrigé", "Show the model answer"), size = 26f) {
                    if (exam) submit(Answer.Self(0.0)) else { answered = true; render() }
                }
                box.addView(b); firstFocus = b
            }
            ExerciseKind.PROBLEM -> {}
        }
    }

    private fun numericPad(c: Exercise, box: LinearLayout) {
        val field = st.text("", 34f, Color.WHITE, true).apply {
            background = st.rounded(0xFF0B0F14.toInt(), 10f, LearnStyle.ACCENT, 2f); setPadding(st.px(18), st.px(8), st.px(18), st.px(8)); minWidth = st.px(300)
        }
        numberField = field; refreshNumber(c)
        box.addView(field, box.lp(-2, -2, bottom = st.px(10)))
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0", if (en) "." else ",", "−", "/", "⌫", "OK")
        val btns = keys.map { k ->
            st.button(k, size = 28f) {
                when (k) {
                    "⌫" -> numberText = numberText.dropLast(1)
                    "OK" -> { if (Marking.parseNumber(numberText) != null) submit(Answer.Number(numberText)); return@button }
                    "−" -> numberText = if (numberText.startsWith("-")) numberText.drop(1) else "-$numberText"
                    else -> if (numberText.length < 14) numberText += k
                }
                refreshNumber(c)
            }.apply { gravity = Gravity.CENTER }
        }
        val grid = st.grid(btns, 5, 10)
        box.addView(grid, box.lp(-1, -2))
        firstFocus = btns[0]
        box.addView(st.text(t("Touches chiffres de la télécommande acceptées · OK pour valider", "Remote number keys work too · OK to submit"), 18f, LearnStyle.MUTED), box.lp(top = st.px(4)))
    }

    private fun refreshNumber(c: Exercise) {
        numberField?.text = (numberText.ifEmpty { "…" }) + (c.unit?.let { "  $it" } ?: "")
    }

    /** Digits typed on the remote while a numeric question is shown. */
    fun key(code: Int): Boolean {
        if (answered || cur.kind != ExerciseKind.NUMERIC) return false
        val d = when (code) { in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> ('0' + (code - KeyEvent.KEYCODE_0)).toString(); KeyEvent.KEYCODE_DEL -> "⌫"; else -> return false }
        if (d == "⌫") numberText = numberText.dropLast(1) else if (numberText.length < 14) numberText += d
        refreshNumber(cur); return true
    }

    private fun matching(c: Exercise, box: LinearLayout) {
        shown = Shuffle.order(c.pairs.size, Shuffle.seed(c.id, round))
        val rights = shown.map { c.pairs[it].second }
        val row = LinearLayout(st.act).apply { orientation = LinearLayout.HORIZONTAL }
        val left = LinearLayout(st.act).apply { orientation = LinearLayout.VERTICAL }
        val right = LinearLayout(st.act).apply { orientation = LinearLayout.VERTICAL }
        val status = st.text(t("Choisis un élément à gauche, puis son partenaire à droite.", "Pick an item on the left, then its partner on the right."), 20f, LearnStyle.MUTED)
        fun label(l: String) = matchLeft[l]?.let { "$l  →  $it" } ?: l
        val leftBtns = HashMap<String, LinearLayout>()
        for ((l, _) in c.pairs) {
            val b = st.button(label(l), size = 22f) { pickedLeft = l; status.text = t("« $l » : choisis maintenant à droite.", "« $l »: now pick on the right.") }
            leftBtns[l] = b; left.addView(b, left.lp(bottom = st.px(8)))
        }
        for (r in rights) right.addView(st.button(r, size = 22f) {
            val l = pickedLeft ?: return@button
            matchLeft.entries.removeAll { it.value == r }
            matchLeft[l] = r; pickedLeft = null
            leftBtns.forEach { (k, v) -> ((v.getChildAt(0)) as TextView).text = label(k) }
            status.text = t("${matchLeft.size} / ${c.pairs.size} associés", "${matchLeft.size} / ${c.pairs.size} matched")
            leftBtns.values.firstOrNull { b -> matchLeft.keys.none { leftBtns[it] == b } }?.requestFocus()
        }, right.lp(bottom = st.px(8)))
        row.addView(left, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = st.px(20) })
        row.addView(right, LinearLayout.LayoutParams(0, -2, 1f))
        box.addView(row); box.addView(status, box.lp(top = st.px(6)))
        val done = st.button(t("Valider", "Submit"), size = 24f) { submit(Answer.Pairs(HashMap(matchLeft))) }
        box.addView(done, box.lp(-2, -2, top = st.px(8)))
        firstFocus = leftBtns[c.pairs.first().first]
    }

    private fun submit(a: Answer) {
        if (answered) return
        val c = cur
        val m = Marking.mark(c, a)
        if (x.kind == ExerciseKind.PROBLEM) {
            while (partAnswers.size <= part) partAnswers += null
            partAnswers[part] = a
            if (exam) {
                // saved after every part: finishing the paper in the middle of a problem keeps the parts already answered
                val padded = List(x.parts.size) { partAnswers.getOrNull(it) }
                onAnswer(Answer.Parts(padded), Marking.mark(x, Answer.Parts(padded)))
                if (part < x.parts.size - 1) { part++; numberText = ""; matchLeft.clear(); render() } else { answered = true; onContinue() }
                return
            }
            answered = true; lastMark = m; render(); return
        }
        answered = true; lastMark = m
        onAnswer(a, m)
        if (exam) { onContinue(); return }
        render()
    }

    private fun finishProblem() {
        val a = Answer.Parts(partAnswers.toList()); val m = Marking.mark(x, a)
        lastMark = m; answered = true; onAnswer(a, m)
        if (exam) onContinue() else render()
    }

    private fun correction(c: Exercise, box: LinearLayout) {
        if (c.kind == ExerciseKind.OPEN && !exam && (lastMark == null || (x.kind == ExerciseKind.PROBLEM && partAnswers.getOrNull(part) == null))) {
            // model answer, then self-marking
            box.addView(st.text(t("Corrigé", "Model answer"), 22f, LearnStyle.ACCENT, true))
            box.addView(st.text(st.md(c.model ?: ""), 24f, Color.WHITE), box.lp(bottom = st.px(8)))
            if (c.rubric.isNotEmpty()) box.addView(st.text(st.md(c.rubric.joinToString("\n") { "- $it" }), 21f, LearnStyle.MUTED), box.lp(bottom = st.px(8)))
            box.addView(st.text(t("Comment t'es-tu noté(e) ?", "How did you do?"), 22f, Color.WHITE, true))
            val bs = listOf(t("Pas réussi", "Not yet") to 0.0, t("À moitié", "Half") to 0.5, t("Réussi", "Got it") to 1.0).map { (l, f) ->
                st.button(l, size = 24f) { answered = false; selfMark(f) }
            }
            box.addView(st.grid(bs, 3, 12)); firstFocus = bs[1]
            return
        }
        val m = lastMark ?: return
        val ok = m.correct
        val mark = if (m.max > 0 && !ok && m.earned > 0) "  (${Scene.fmt(m.earned)} / ${pts(m.max)})" else ""
        box.addView(st.text((if (ok) "✓  " + t("Bonne réponse !", "Correct!") else "✗  " + t("Pas tout à fait…", "Not quite…")) + mark,
            30f, if (ok) LearnStyle.GOOD else LearnStyle.BAD, true), box.lp(bottom = st.px(6)))
        if (!ok && c.kind != ExerciseKind.OPEN) box.addView(st.text(t("Réponse : ", "Answer: ") + Markdown.plain(Marking.rightAnswer(c, lang)), 25f, Color.WHITE, true), box.lp(bottom = st.px(6)))
        if (c.explanation.isNotBlank()) box.addView(st.text(st.md(c.explanation), 23f, 0xFFE3E8EE.toInt()), box.lp(bottom = st.px(6)))
        val top = if (x.kind == ExerciseKind.PROBLEM && part == x.parts.size - 1) x else c
        top.method?.let { box.addView(st.text(st.md("**" + t("Méthode", "Method") + " :** " + it), 21f, LearnStyle.MUTED), box.lp(bottom = st.px(4))) }
        if (top.mistakes.isNotEmpty()) box.addView(st.text(st.md("**" + t("Erreurs fréquentes", "Frequent mistakes") + " :**\n" + top.mistakes.joinToString("\n") { "- $it" }), 21f, LearnStyle.MUTED), box.lp(bottom = st.px(4)))
        val last = x.kind != ExerciseKind.PROBLEM || part >= x.parts.size - 1
        val next = st.button(if (last) t("Continuer", "Continue") else t("Question suivante", "Next question"), size = 25f) {
            if (!last) { part++; answered = false; lastMark = null; numberText = ""; matchLeft.clear(); render() }
            else { if (x.kind == ExerciseKind.PROBLEM && partAnswers.size >= x.parts.size) finishProblemAfterCorrection() else onContinue() }
        }
        box.addView(next, box.lp(-2, -2, top = st.px(8))); firstFocus = next
    }

    private var problemReported = false
    private fun finishProblemAfterCorrection() {
        if (!problemReported) { problemReported = true; val a = Answer.Parts(partAnswers.toList()); onAnswer(a, Marking.mark(x, a)) }
        onContinue()
    }

    private fun selfMark(f: Double) {
        val a = Answer.Self(f)
        if (x.kind == ExerciseKind.PROBLEM) { while (partAnswers.size <= part) partAnswers += null; partAnswers[part] = a; lastMark = Marking.mark(cur, a); answered = true; render(); return }
        lastMark = Marking.mark(x, a); answered = true; onAnswer(a, lastMark!!); render()
    }

    // ---- phone remote ----
    fun remoteChoice(shownIndex: Int): String? {
        if (answered) return "déjà répondu"
        if (cur.kind != ExerciseKind.MCQ) return "ce n'est pas un QCM"
        val orig = shown.getOrNull(shownIndex) ?: return "choix inconnu"
        submit(Answer.Choice(orig)); return null
    }
    fun remoteBool(v: Boolean): String? = if (answered || cur.kind != ExerciseKind.TRUE_FALSE) "impossible ici" else { submit(Answer.Bool(v)); null }
    fun remoteNumber(s: String): String? = if (answered || cur.kind != ExerciseKind.NUMERIC) "impossible ici"
        else if (Marking.parseNumber(s) == null) "nombre invalide" else { numberText = s; submit(Answer.Number(s)); null }
    fun remoteSelf(f: Double): String? = if (cur.kind != ExerciseKind.OPEN) "impossible ici" else { if (!answered && !exam) { answered = true }; selfMark(f); null }

    /** What the phone needs to show the matching controls. */
    fun stateMap(): Map<String, Any?> = linkedMapOf(
        "id" to x.id, "kind" to cur.kind.key, "part" to if (x.kind == ExerciseKind.PROBLEM) part + 1 else null, "parts" to x.parts.size.takeIf { it > 0 },
        "prompt" to Markdown.plain(cur.prompt), "choices" to if (cur.kind == ExerciseKind.MCQ) shown.map { Markdown.plain(cur.choices[it]) } else null,
        "unit" to cur.unit, "answered" to answered, "correct" to lastMark?.correct, "points" to lastMark?.earned, "max" to lastMark?.max,
        "answer" to if (answered && !exam) Markdown.plain(Marking.rightAnswer(cur, lang)) else null, "lang" to lang,
    )
}
