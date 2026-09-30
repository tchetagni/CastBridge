package castbridge.receiver

import android.graphics.Color
import android.view.FocusFinder
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import castbridge.core.learn.Answer
import castbridge.core.learn.Block
import castbridge.core.learn.Exercise
import castbridge.core.learn.Lesson
import castbridge.core.learn.LessonDeck
import castbridge.core.learn.Mark
import castbridge.core.learn.Markdown
import castbridge.core.learn.MockExamSession
import castbridge.core.learn.MockExamSpec
import castbridge.core.learn.Pack
import castbridge.core.learn.ReviewStatus
import castbridge.core.learn.Scene

/**
 * The lesson reader: one block (or one worked example) per screen. ◀ ▶ change page, OK reveals the next step of an
 * example, MENU reads the page aloud. Exercises inside the lesson are corrected at once. Progress (page, time,
 * completion, stars) is recorded for the current student; leaving keeps the page for « Reprendre ».
 */
class ReaderScreen(a: LearnActivity, val pack: Pack, val lesson: Lesson, private val start: Int = 0) : LearnActivity.Screen(a) {
    private val deck = LessonDeck(pack, lesson)
    private var page = start.coerceIn(0, deck.pages.size - 1)
    private var revealed = 0
    private var shownAt = System.currentTimeMillis()
    private var ex: ExerciseView? = null
    private val en = deck.lang == "en"
    private fun t(fr: String, e: String) = if (en) e else fr
    override val name = "lesson"

    override fun build(): View {
        ex = null
        val p = deck.pages[page]
        val body: View = when (p) {
            LessonDeck.Page.Intro -> intro()
            is LessonDeck.Page.Content -> content(p.block)
            is LessonDeck.Page.Exercise -> exercise(p.exercise)
            LessonDeck.Page.End -> end()
        }
        val hint = when {
            p is LessonDeck.Page.Content && p.block is Block.Example && revealed < deck.reveals(page) -> t("OK : étape suivante (${revealed}/${deck.reveals(page)})   ·   ◀ ▶ pages", "OK: next step (${revealed}/${deck.reveals(page)})   ·   ◀ ▶ pages")
            p is LessonDeck.Page.Exercise -> t("Réponds avec les flèches et OK   ·   ◀ ▶ pages", "Answer with the arrows and OK   ·   ◀ ▶ pages")
            else -> t("◀ ▶ pages   ·   OK : suite", "◀ ▶ pages   ·   OK: next") + (if (a.ttsReady) t("   ·   MENU : lire à voix haute", "   ·   MENU: read aloud") else "")
        }
        return a.frame(lesson.title, "${pack.subjectInfo?.label(pack.lang) ?: pack.subject} · ${pack.level}", body, hint, (page + 1f) / deck.pages.size, "${page + 1} / ${deck.pages.size}")
    }

    override fun shown() {
        record()
        if (deck.readAloud && a.autoRead) a.speak(deck.spoken(page, revealed), deck.lang)
    }

    private fun record() {
        val prof = a.profile ?: return
        val now = System.currentTimeMillis()
        LearnHub.progress().lessonPage(prof.id, pack, lesson, page, deck.pages.size, now - shownAt, now); shownAt = now
        LearnHub.save()
    }

    private fun intro(): View {
        val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        col.addView(a.st.text(lesson.title, 42f, Color.WHITE, true, 3))
        val status = if (lesson.status == ReviewStatus.VALIDATED) "✔ " + t("Contenu certifié", "Certified content") else "✎ " + t("Brouillon — à relire par un enseignant", "Draft — to be reviewed by a teacher")
        col.addView(a.st.text("⏱ ${lesson.minutes} min   ·   $status", 22f, if (lesson.status == ReviewStatus.VALIDATED) LearnStyle.GOOD else LearnStyle.GOLD), col.lp(top = a.st.px(8), bottom = a.st.px(18)))
        if (lesson.objectives.isNotEmpty()) {
            col.addView(a.st.text(t("Objectifs", "Objectives"), 26f, LearnStyle.ACCENT, true))
            col.addView(a.st.text(a.st.md(lesson.objectives.joinToString("\n") { "- $it" }), 27f), col.lp(bottom = a.st.px(14)))
        }
        val pre = lesson.prerequisites.mapNotNull { pack.lesson(it)?.title }
        if (pre.isNotEmpty()) col.addView(a.st.text(t("À connaître avant : ", "You should know: ") + pre.joinToString(", "), 21f, LearnStyle.MUTED), col.lp(bottom = a.st.px(6)))
        lesson.programRef?.let { col.addView(a.st.text(t("Programme : ", "Syllabus: ") + it, 18f, LearnStyle.MUTED, lines = 2)) }
        return scroll(col)
    }

    private fun scroll(v: View) = ScrollView(a).apply { isFillViewport = true; clipChildren = true; addView(v) }

    private fun content(b: Block): View = when (b) {
        is Block.Heading -> LinearLayout(a).apply { gravity = Gravity.CENTER; addView(a.st.text(b.text, 46f, Color.WHITE, true).apply { gravity = Gravity.CENTER }) }
        is Block.Text -> a.st.text(a.st.md(b.md), 30f).also { it.fitHeight(a.st.px(18).toFloat()) }.let { wrapReview(it, b.review) }
        is Block.Key -> keyBox(b)
        is Block.Formula -> LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            addView(FormulaView(a, b.tex, a.st.px(64).toFloat()), lp())
            b.caption?.let { addView(a.st.text(a.st.md(it), 24f, LearnStyle.MUTED).apply { gravity = Gravity.CENTER }, lp(top = a.st.px(12))) }
        }
        is Block.Example -> example(b)
        is Block.Illustration -> LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            addView(FigureView(a, Scene.build(b.figure)), LinearLayout.LayoutParams(-1, 0, 1f))
            b.caption?.let { addView(a.st.text(a.st.md(it), 23f, LearnStyle.MUTED, lines = 2).apply { gravity = Gravity.CENTER }, lp(top = a.st.px(8))) }
            if (b.review) addView(a.st.text("⚑ " + t("à vérifier", "to be checked"), 18f, LearnStyle.GOLD))
        }
        is Block.Video -> LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            addView(a.st.text("▶  " + b.title, 34f, Color.WHITE, true))
            b.credit?.let { addView(a.st.text(it, 20f, LearnStyle.MUTED)) }
            addView(a.st.button(t("Regarder la vidéo", "Watch the video"), size = 26f) { b.src?.let { s -> LearnHub.playVideo(s)?.let { m -> a.toast(m) } } }, lp(-2, -2, top = a.st.px(16)))
        }
        is Block.Audio -> LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            addView(a.st.text("♪", 80f).apply { gravity = Gravity.CENTER })
            addView(a.st.text(b.text, 30f).apply { gravity = Gravity.CENTER })
            addView(a.st.button(t("Écouter", "Listen"), size = 26f) { a.speak(b.text, b.lang) }, lp(-2, -2, top = a.st.px(16)))
        }
        is Block.More -> LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            addView(a.st.text(t("Pour aller plus loin", "Going further"), 30f, LearnStyle.ACCENT, true))
            addView(a.st.text(a.st.md(b.items.joinToString("\n") { "- $it" }), 26f).also { it.fitHeight(a.st.px(18).toFloat()) }, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        is Block.ExerciseRef -> View(a)
    }

    private fun wrapReview(v: View, review: Boolean): View = if (!review) v else LinearLayout(a).apply {
        orientation = LinearLayout.VERTICAL; addView(v, LinearLayout.LayoutParams(-1, 0, 1f)); addView(a.st.text("⚑ " + t("passage à vérifier par un enseignant", "to be checked by a teacher"), 18f, LearnStyle.GOLD))
    }

    private fun keyBox(b: Block.Key): View {
        val color = LearnStyle.BOX[b.style] ?: LearnStyle.ACCENT
        val box = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL; setPadding(a.st.px(28), a.st.px(20), a.st.px(28), a.st.px(20))
            background = a.st.rounded(0xFF151C27.toInt(), 18f, color, 3f)
        }
        box.addView(a.st.text((LearnStyle.BOX_LABEL[b.style] ?: "") .let { if (en) enLabel(b.style) else it }.uppercase(), 20f, color, true))
        b.title?.let { box.addView(a.st.text(it, 32f, Color.WHITE, true, 2), box.lp(top = a.st.px(4), bottom = a.st.px(8))) }
        // with a formula: text then formula right under it (wrap); text alone: it takes the box and shrinks to fit
        if (b.md.isNotBlank()) box.addView(a.st.text(a.st.md(b.md), 28f).also { if (b.tex == null) it.fitHeight(a.st.px(18).toFloat()) },
            if (b.tex == null) LinearLayout.LayoutParams(-1, 0, 1f) else box.lp())
        b.tex?.let { box.addView(FormulaView(a, it, a.st.px(54).toFloat()), box.lp(top = a.st.px(18))) }
        if (b.review) box.addView(a.st.text("⚑ " + t("à vérifier par un enseignant", "to be checked by a teacher"), 18f, LearnStyle.GOLD))
        return box
    }

    private fun enLabel(style: String) = mapOf("definition" to "Definition", "propriete" to "Property", "formule" to "Formula", "retenir" to "Remember",
        "methode" to "Method", "attention" to "Watch out", "pieges" to "Common mistakes", "objectifs" to "Objectives")[style] ?: ""

    private fun example(b: Block.Example): View {
        val row = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
        val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        col.addView(a.st.text(b.title, 30f, LearnStyle.GOLD, true, 2))
        col.addView(a.st.text(a.st.md(b.statement), 26f), col.lp(top = a.st.px(6), bottom = a.st.px(10)))
        b.steps.take(revealed).forEachIndexed { i, s ->
            val step = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setPadding(a.st.px(16), a.st.px(8), a.st.px(16), a.st.px(8)); background = a.st.rounded(0xFF182231.toInt(), 12f) }
            step.addView(a.st.text(a.st.md("**" + t("Étape", "Step") + " ${i + 1}.** " + s.md), 24f))
            s.tex?.let { step.addView(FormulaView(a, it, a.st.px(38).toFloat()), step.lp(top = a.st.px(4))) }
            col.addView(step, col.lp(bottom = a.st.px(8)))
        }
        if (revealed > b.steps.size) b.answer?.let { col.addView(a.st.text(a.st.md("➜ " + it), 28f, LearnStyle.GOOD, true), col.lp(top = a.st.px(6))) }
        val sc = ScrollView(a).apply { clipChildren = true; addView(col) }
        val fig = b.figure
        if (fig != null) {
            row.addView(sc, LinearLayout.LayoutParams(0, -1, 1.25f))
            row.addView(FigureView(a, Scene.build(fig)), LinearLayout.LayoutParams(0, -1, 1f).apply { leftMargin = a.st.px(20) })
        } else row.addView(sc, LinearLayout.LayoutParams(-1, -1))
        sc.post { sc.fullScroll(View.FOCUS_DOWN) }
        return row
    }

    private fun exercise(x: Exercise): View {
        val v = ExerciseView(a.st, x, false, deck.lang, onAnswer = { ans, m -> result(x, m) }, onContinue = { go(page + 1) })
        ex = v
        return v.view
    }

    private fun result(x: Exercise, m: Mark) {
        val prof = a.profile ?: return
        LearnHub.progress().exerciseResult(prof.id, pack, x, m, System.currentTimeMillis()); LearnHub.save()
    }

    private fun end(): View {
        val prof = a.profile
        val stars = prof?.let { LearnHub.progress().state.of(it.id).lessons[lesson.id]?.stars } ?: 0
        val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        col.addView(a.st.text(t("Fiche terminée !", "Lesson complete!"), 44f, Color.WHITE, true).apply { gravity = Gravity.CENTER })
        col.addView(a.st.text("★".repeat(stars) + "☆".repeat(3 - stars), 64f, LearnStyle.GOLD).apply { gravity = Gravity.CENTER })
        col.addView(a.st.text(t("Gagne des étoiles avec les exercices et l'auto-évaluation.", "Earn stars with the exercises and the self-check."), 22f, LearnStyle.MUTED).apply { gravity = Gravity.CENTER }, col.lp(bottom = a.st.px(16)))
        val btns = ArrayList<View>()
        deck.graded().takeIf { it.isNotEmpty() }?.let { g -> btns += a.st.button(t("Exercices de la fiche", "Lesson exercises"), "${g.size}", onClick = { a.push(SeriesScreen(a, pack, g, t("Exercices — ", "Exercises — ") + lesson.title)) }) }
        deck.selfCheck().takeIf { it.isNotEmpty() }?.let { q -> btns += a.st.button(t("Auto-évaluation", "Self-check"), "${q.size} " + t("questions", "questions"), onClick = { a.push(SeriesScreen(a, pack, q, t("Auto-évaluation — ", "Self-check — ") + lesson.title)) }) }
        val next = pack.lessons.getOrNull(pack.lessons.indexOf(lesson) + 1)
        next?.let { n -> btns += a.st.button(t("Fiche suivante", "Next lesson"), n.title, onClick = { a.replace(ReaderScreen(a, pack, n)) }) }
        btns += a.st.button(t("Revoir depuis le début", "Start again"), onClick = { go(0) })
        col.addView(a.st.grid(btns, 2, 16))
        return scroll(col)
    }

    private fun go(p: Int) {
        if (p !in deck.pages.indices) return
        record()
        a.stopSpeaking()
        page = p; revealed = 0
        record()                      // the new page counts at once (the last page completes the lesson before « end » shows the stars)
        a.rebuild()
    }

    override fun key(code: Int, e: KeyEvent): Boolean {
        ex?.let { if (it.key(code)) return true }
        when (code) {
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT -> {
                if (code == KeyEvent.KEYCODE_DPAD_RIGHT && canMove(View.FOCUS_RIGHT)) return false
                go(page + 1); return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                if (code == KeyEvent.KEYCODE_DPAD_LEFT && canMove(View.FOCUS_LEFT)) return false
                go(page - 1); return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BUTTON_A -> {
                if (a.currentFocus?.isClickable == true && a.currentFocus?.isFocused == true) return false
                ok(); return true
            }
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_INFO, KeyEvent.KEYCODE_PROG_RED -> { a.speak(deck.spoken(page, revealed), deck.lang); return true }
        }
        return false
    }

    private fun canMove(dir: Int): Boolean {
        val f = a.currentFocus ?: return false
        if (!f.isClickable) return false
        return FocusFinder.getInstance().findNextFocus(a.content, f, dir) != null
    }

    private fun ok() {
        val b = (deck.pages[page] as? LessonDeck.Page.Content)?.block
        if (b is Block.Example && revealed < deck.reveals(page)) {
            revealed++; a.rebuild(focus = false)
            if (deck.readAloud && a.autoRead) a.speak(if (revealed <= b.steps.size) Markdown.spoken(b.steps[revealed - 1].md, deck.lang) else Markdown.spoken(b.answer ?: "", deck.lang), deck.lang)
            return
        }
        if (b is Block.Audio) { a.speak(b.text, b.lang); return }
        if (b is Block.Video && b.src != null) { LearnHub.playVideo(b.src!!)?.let { a.toast(it) }; return }
        go(page + 1)
    }

    override fun leave() { record(); a.stopSpeaking() }

    override fun remote(action: String, p: Map<String, String>): String? = when (action) {
        "next" -> { go(page + 1); null }
        "prev" -> { go(page - 1); null }
        "ok" -> { ok(); null }
        "speak" -> { a.speak(deck.spoken(page, revealed), deck.lang); null }
        "choice" -> ex.let { e -> if (e == null) "pas d'exercice sur cette page" else e.remoteChoice(p["value"]?.toIntOrNull() ?: -1) }
        "bool" -> ex.let { e -> if (e == null) "pas d'exercice sur cette page" else e.remoteBool(p["value"] == "true") }
        "number" -> ex.let { e -> if (e == null) "pas d'exercice sur cette page" else e.remoteNumber(p["value"].orEmpty()) }
        "self" -> ex.let { e -> if (e == null) "pas d'exercice sur cette page" else e.remoteSelf(p["value"]?.toDoubleOrNull() ?: 0.0) }
        else -> super.remote(action, p)
    }

    override fun state(): Map<String, Any?> = linkedMapOf("pack" to pack.id, "lesson" to lesson.id, "title" to lesson.title, "page" to page, "pages" to deck.pages.size,
        "kind" to deck.pages[page].javaClass.simpleName.lowercase(), "revealed" to revealed, "reveals" to deck.reveals(page), "text" to deck.spoken(page, revealed).take(600),
        "exercise" to ex?.stateMap())
}

/** A series of exercises (lesson exercises, self-check, exam-style set, reviews): one per screen, corrected at once. */
class SeriesScreen(a: LearnActivity, val pack: Pack?, private val items: List<Exercise>, private val title: String,
                   private val review: Boolean = false, private val packOf: (Exercise) -> Pack? = { pack }) : LearnActivity.Screen(a) {
    private var i = 0
    private var earned = 0.0; private var max = 0.0; private var right = 0
    private var ex: ExerciseView? = null
    override val name = "exercises"
    private val lang get() = packOf(items.getOrNull(i) ?: items.first())?.lang ?: "fr"
    private fun t(fr: String, e: String) = if (lang == "en") e else fr

    override fun build(): View {
        if (i >= items.size) return summary()
        val x = items[i]
        val v = ExerciseView(a.st, x, false, lang, onAnswer = { ans, m -> result(x, m) }, onContinue = { i++; a.rebuild() })
        ex = v
        val tier = x.tier.label.let { if (lang == "en") mapOf("Application" to "Practice", "Approfondissement" to "Going deeper", "Type examen" to "Exam style", "Auto-évaluation" to "Self-check")[it] ?: it else it }
        return a.frame(title, "$tier  ·  ${Scene.fmt(x.totalPoints)} pt", v.view, t("Flèches et OK pour répondre   ·   RETOUR : quitter", "Arrows and OK to answer   ·   BACK: leave"),
            (i + 1f) / items.size, "${i + 1} / ${items.size}")
    }

    private fun result(x: Exercise, m: Mark) {
        earned += m.earned; max += m.max; if (m.correct) right++
        val prof = a.profile ?: return
        val p = packOf(x) ?: return
        LearnHub.progress().exerciseResult(prof.id, p, x, m, System.currentTimeMillis(), review); LearnHub.save()
    }

    private fun summary(): View {
        ex = null
        val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        val on20 = if (max > 0) Math.round(earned / max * 80) / 4.0 else 0.0
        col.addView(a.st.text(t("Série terminée", "All done"), 44f, Color.WHITE, true).apply { gravity = Gravity.CENTER })
        col.addView(a.st.text("$right / ${items.size} " + t("réussis", "correct") + "   ·   ${Scene.fmt(on20)} / 20", 36f, LearnStyle.GOLD, true).apply { gravity = Gravity.CENTER }, col.lp(top = a.st.px(10)))
        col.addView(a.st.text(t("Les exercices ratés reviendront dans « Révisions » dans quelques jours.", "Missed exercises will come back in « Reviews » in a few days."), 22f, LearnStyle.MUTED).apply { gravity = Gravity.CENTER }, col.lp(top = a.st.px(10), bottom = a.st.px(20)))
        col.addView(a.st.button(t("Recommencer", "Try again"), onClick = { i = 0; earned = 0.0; max = 0.0; right = 0; a.rebuild() }), col.lp(-2, -2, bottom = a.st.px(10)))
        col.addView(a.st.button(t("Retour", "Back"), onClick = { a.pop() }), col.lp(-2, -2))
        return a.frame(title, null, col, t("OK : choisir", "OK: choose"), 1f, "")
    }

    override fun key(code: Int, e: KeyEvent): Boolean = ex?.key(code) == true

    override fun remote(action: String, p: Map<String, String>): String? = when (action) {
        "choice" -> ex.let { e -> if (e == null) "série terminée" else e.remoteChoice(p["value"]?.toIntOrNull() ?: -1) }
        "bool" -> ex.let { e -> if (e == null) "série terminée" else e.remoteBool(p["value"] == "true") }
        "number" -> ex.let { e -> if (e == null) "série terminée" else e.remoteNumber(p["value"].orEmpty()) }
        "self" -> ex.let { e -> if (e == null) "série terminée" else e.remoteSelf(p["value"]?.toDoubleOrNull() ?: 0.0) }
        else -> super.remote(action, p)
    }

    override fun state(): Map<String, Any?> = linkedMapOf("title" to title, "index" to i, "count" to items.size, "exercise" to ex?.stateMap(), "right" to right)
}

/** « Épreuve blanche »: timed, no correction before the end; then the mark out of 20, the lost points and the fiches to revise. */
class MockExamScreen(a: LearnActivity, val pack: Pack, val spec: MockExamSpec) : LearnActivity.Screen(a) {
    /** Created when the student presses « Commencer » after reading the instructions (the clock starts then). */
    private var started: MockExamSession? = null
    private val session: MockExamSession get() = started ?: MockExamSession(pack, spec, System.currentTimeMillis()).also { started = it }
    private var i = 0
    private var result: MockExamSession.Result? = null
    private var ex: ExerciseView? = null
    private var clock: TextView? = null
    override val name = "mock"
    private fun t(fr: String, e: String) = if (pack.lang == "en") e else fr
    private val tick = object : Runnable {
        override fun run() {
            if (result != null || a.top() !== this@MockExamScreen) return
            clock?.text = remaining()
            if (session.timeUp) { a.toast(t("Temps écoulé : l'épreuve est terminée.", "Time is up.")); finish(); return }
            a.main.postDelayed(this, 1000)
        }
    }

    private fun remaining(): String { val s = session.remainingMs / 1000; return "⏱ %d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) }

    override fun build(): View {
        result?.let { return results(it) }
        if (started == null) return intro()
        val x = session.exercises[i]
        val section = spec.sections.firstOrNull { x.id in it.exercises }?.title ?: ""
        val v = ExerciseView(a.st, x, true, pack.lang, onAnswer = { ans, _ -> session.answer(x.id, ans) }, onContinue = { if (i < session.exercises.size - 1) { i++; a.rebuild() } else confirmFinish() })
        ex = v
        val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        val head = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(a.st.text(section, 22f, LearnStyle.ACCENT, true, 1), LinearLayout.LayoutParams(0, -2, 1f))
        clock = a.st.text(remaining(), 26f, LearnStyle.GOLD, true).also { head.addView(it) }
        col.addView(head)
        val answered = session.answerOf(x.id) != null
        if (answered) col.addView(a.st.text("✓ " + t("Réponse enregistrée (tu peux la changer)", "Answer saved (you may change it)"), 20f, LearnStyle.GOOD))
        col.addView(v.view, LinearLayout.LayoutParams(-1, 0, 1f))
        a.main.removeCallbacks(tick); a.main.postDelayed(tick, 1000)
        return a.frame(spec.title, "${session.answered()} / ${session.exercises.size} " + t("répondus", "answered"), col,
            t("◀ ▶ exercices   ·   MENU : terminer l'épreuve   ·   correction à la fin", "◀ ▶ questions   ·   MENU: finish   ·   marked at the end"),
            (i + 1f) / session.exercises.size, "${i + 1} / ${session.exercises.size}")
    }

    /** Instructions first (never as a pop-up over the page): duration, parts and marks, then « Commencer ». */
    private fun intro(): View {
        val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        col.addView(a.st.text(spec.title, 38f, Color.WHITE, true))
        col.addView(a.st.text("⏱ ${spec.minutes} min   ·   /20   ·   ${spec.exerciseIds.size} " + t("exercice(s)", "question(s)"), 26f, LearnStyle.GOLD, true), col.lp(top = a.st.px(8), bottom = a.st.px(12)))
        spec.sections.forEach { s -> col.addView(a.st.text("• " + s.title, 24f), col.lp(bottom = a.st.px(4))) }
        spec.instructions?.let { col.addView(a.st.text(a.st.md(it), 21f, LearnStyle.MUTED), col.lp(top = a.st.px(10), bottom = a.st.px(12))) }
        col.addView(a.st.text(t("Sujet original d'entraînement, écrit « dans le style » de l'examen : ce n'est pas un sujet officiel.", "Original practice paper in the style of the exam: not an official paper."), 19f, LearnStyle.MUTED), col.lp(bottom = a.st.px(14)))
        col.addView(a.st.button(t("Commencer l'épreuve", "Start the paper"), fill = 0xFF1F5A2E.toInt()) { session; a.rebuild() }, col.lp(-2, -2))
        return a.frame(t("Épreuve blanche", "Mock exam"), pack.title, ScrollView(a).apply { clipChildren = true; addView(col) }, t("OK : commencer   ·   RETOUR : plus tard", "OK: start   ·   BACK: later"))
    }

    private fun confirmFinish() {
        a.confirm(t("Terminer l'épreuve ?", "Finish the paper?"), t("${session.answered()} exercice(s) sur ${session.exercises.size} répondu(s).", "${session.answered()} of ${session.exercises.size} answered.")) { finish() }
    }

    private fun finish() {
        val r = session.finish(); result = r
        a.profile?.let { p -> LearnHub.progress().mockResult(p.id, pack, spec, r, (session.finishedAt ?: 0) - session.startedAt, System.currentTimeMillis()); LearnHub.save() }
        a.rebuild()
    }

    private fun results(r: MockExamSession.Result): View {
        ex = null
        val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        col.addView(a.st.text("${Scene.fmt(r.score)} / 20", 64f, if (r.score >= 10) LearnStyle.GOOD else LearnStyle.BAD, true).apply { gravity = Gravity.CENTER }, col.lp())
        if (r.pendingSelfMarks > 0) col.addView(a.st.text(t("Les questions à rédiger sont comptées 0 : ouvre-les pour te noter avec le corrigé.", "Written answers count 0 until you mark them with the model answer."), 20f, LearnStyle.GOLD).apply { gravity = Gravity.CENTER })
        val lessons = r.revise.mapNotNull { pack.lesson(it) }
        if (lessons.isNotEmpty()) col.addView(a.st.text(t("Fiches à revoir : ", "Lessons to revise: ") + lessons.joinToString(" · ") { it.title }, 22f, Color.WHITE), col.lp(top = a.st.px(8), bottom = a.st.px(8)))
        for (l in r.lines) {
            val lost = l.mark.max - l.mark.earned
            col.addView(a.st.button((if (lost <= 0) "✓ " else "✗ ") + Markdown.plain(l.exercise.prompt).take(90),
                "${l.section} · ${Scene.fmt(l.mark.earned)} / ${Scene.fmt(l.mark.max)}" + if (lost > 0) " (−${Scene.fmt(lost)})" else "", size = 22f) {
                a.push(CorrectionScreen(a, pack, l.exercise, session) { a.rebuild() })
            }, col.lp(bottom = a.st.px(8)))
        }
        lessons.firstOrNull()?.let { l -> col.addView(a.st.button(t("Ouvrir la fiche « ${l.title} »", "Open « ${l.title} »"), onClick = { a.push(ReaderScreen(a, pack, l)) }), col.lp(top = a.st.px(8))) }
        return a.frame(t("Résultat — ", "Result — ") + spec.title, null, ScrollView(a).apply { clipChildren = true; addView(col) }, t("OK : voir la correction d'un exercice", "OK: see the correction"), 1f, "")
    }

    override fun key(code: Int, e: KeyEvent): Boolean {
        if (result != null || started == null) return false
        ex?.let { if (it.key(code)) return true }
        when (code) {
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_INFO -> { confirmFinish(); return true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { if (canMove(View.FOCUS_RIGHT)) return false; if (i < session.exercises.size - 1) { i++; a.rebuild() }; return true }
            KeyEvent.KEYCODE_DPAD_LEFT -> { if (canMove(View.FOCUS_LEFT)) return false; if (i > 0) { i--; a.rebuild() }; return true }
        }
        return false
    }

    private fun canMove(dir: Int): Boolean { val f = a.currentFocus ?: return false; return FocusFinder.getInstance().findNextFocus(a.content, f, dir) != null }

    override fun back(): Boolean {
        if (result != null || started == null) return false
        a.confirm(t("Quitter l'épreuve ?", "Leave the paper?"), t("Tes réponses seront perdues.", "Your answers will be lost.")) { result = MockExamSession.Result(emptyList(), 0.0, 0.0, 20.0); a.main.removeCallbacks(tick); a.pop() }
        return true
    }

    override fun leave() { a.main.removeCallbacks(tick) }

    override fun remote(action: String, p: Map<String, String>): String? = when (action) {
        "choice" -> ex.let { e -> if (e == null) "épreuve terminée" else e.remoteChoice(p["value"]?.toIntOrNull() ?: -1) }
        "bool" -> ex.let { e -> if (e == null) "épreuve terminée" else e.remoteBool(p["value"] == "true") }
        "number" -> ex.let { e -> if (e == null) "épreuve terminée" else e.remoteNumber(p["value"].orEmpty()) }
        "next" -> { if (result == null && i < session.exercises.size - 1) { i++; a.rebuild() }; null }
        "prev" -> { if (result == null && i > 0) { i--; a.rebuild() }; null }
        else -> super.remote(action, p)
    }

    override fun state(): Map<String, Any?> = linkedMapOf("pack" to pack.id, "mock" to spec.id, "started" to (started != null), "index" to i, "count" to spec.exerciseIds.size,
        "remainingMs" to started?.remainingMs, "score" to result?.score, "exercise" to ex?.stateMap())
}

/** Correction of one exercise of a finished mock exam, with self-marking of written answers. */
class CorrectionScreen(a: LearnActivity, val pack: Pack, val x: Exercise, private val session: MockExamSession, private val changed: () -> Unit) : LearnActivity.Screen(a) {
    override val name = "correction"
    private fun t(fr: String, e: String) = if (pack.lang == "en") e else fr
    override fun build(): View {
        val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        col.addView(a.st.text(a.st.md(x.prompt), 24f), col.lp(bottom = a.st.px(8)))
        x.figure?.let { col.addView(FigureView(a, Scene.build(it)), LinearLayout.LayoutParams(-1, a.st.px(260))) }
        val parts = if (x.parts.isEmpty()) listOf(x) else x.parts
        parts.forEachIndexed { k, q ->
            val box = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setPadding(a.st.px(16), a.st.px(10), a.st.px(16), a.st.px(10)); background = a.st.rounded(0xFF182231.toInt(), 12f) }
            if (x.parts.isNotEmpty()) box.addView(a.st.text(a.st.md("**${k + 1}.** " + q.prompt), 22f))
            val ans = session.answerOf(x.id).let { if (x.parts.isEmpty()) it else (it as? Answer.Parts)?.parts?.getOrNull(k) }
            val m = castbridge.core.learn.Marking.mark(q, ans)
            box.addView(a.st.text((if (m.correct) "✓ " else "✗ ") + "${Scene.fmt(m.earned)} / ${Scene.fmt(m.max)}", 22f, if (m.correct) LearnStyle.GOOD else LearnStyle.BAD, true))
            if (q.kind == castbridge.core.learn.ExerciseKind.OPEN) {
                box.addView(a.st.text(a.st.md("**" + t("Corrigé", "Model answer") + " :** " + (q.model ?: "")), 21f))
                val row = listOf(t("0", "0") to 0.0, t("½", "½") to 0.5, t("Tout", "All") to 1.0).map { (l, f) ->
                    a.st.button(t("Je me note : ", "My mark: ") + l, size = 20f) { session.selfMark(x.id, f, if (x.parts.isEmpty()) null else k); changed(); a.rebuild() }
                }
                box.addView(a.st.grid(row, 3, 10))
            } else box.addView(a.st.text(t("Réponse : ", "Answer: ") + Markdown.plain(castbridge.core.learn.Marking.rightAnswer(q, pack.lang)), 21f, Color.WHITE, true))
            if (q.explanation.isNotBlank()) box.addView(a.st.text(a.st.md(q.explanation), 20f, LearnStyle.MUTED))
            col.addView(box, col.lp(bottom = a.st.px(8)))
        }
        x.method?.let { col.addView(a.st.text(a.st.md("**" + t("Méthode", "Method") + " :** " + it), 20f, LearnStyle.MUTED)) }
        if (x.mistakes.isNotEmpty()) col.addView(a.st.text(a.st.md("**" + t("Erreurs fréquentes", "Frequent mistakes") + " :**\n" + x.mistakes.joinToString("\n") { "- $it" }), 20f, LearnStyle.MUTED))
        val focusAnchor = a.st.button(t("Retour aux résultats", "Back to the results"), onClick = { a.pop() })
        col.addView(focusAnchor, col.lp(-2, -2, top = a.st.px(10)))
        return a.frame(t("Correction", "Correction"), null, ScrollView(a).apply { clipChildren = true; addView(col) }, t("RETOUR : résultats", "BACK: results"), 1f, "")
    }
}
