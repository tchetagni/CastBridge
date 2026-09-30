package castbridge.core.learn

import castbridge.core.quiz.Json

/**
 * Reads a pack from its JSON files: `pack.json` (metadata, chapters, mock exams) and `lessons/<name>.json` (lessons and
 * exercises; usually one file per chapter). Format version [FORMAT]; readers accept this version and older ones.
 * Errors name the file and the element (« lessons/ch2.json: exercice #4 (bepc-maths-ex-004): "choices" manquant »).
 */
object LessonJson {
    const val FORMAT = 1

    class ParseError(msg: String) : IllegalArgumentException(msg)

    @Suppress("UNCHECKED_CAST")
    private fun Any?.obj(where: String): Map<String, Any?> = this as? Map<String, Any?> ?: throw ParseError("$where : objet attendu")
    private fun Map<String, Any?>.s(k: String): String? = this[k] as? String
    private fun Map<String, Any?>.req(k: String, where: String): String = s(k)?.takeIf { it.isNotBlank() } ?: throw ParseError("$where : \"$k\" manquant")
    private fun Map<String, Any?>.d(k: String): Double? = (this[k] as? Number)?.toDouble()
    private fun Map<String, Any?>.i(k: String): Int? = (this[k] as? Number)?.toInt()
    private fun Map<String, Any?>.b(k: String): Boolean? = this[k] as? Boolean
    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.l(k: String): List<Any?> = this[k] as? List<Any?> ?: emptyList()
    private fun Map<String, Any?>.strs(k: String): List<String> = l(k).mapNotNull { it as? String }

    /** [files]: path inside the pack → text. Needs "pack.json"; every "lessons/…json" is read, in name order. */
    fun parsePack(files: Map<String, String>): Pack {
        val root = try { Json.obj(files["pack.json"] ?: throw ParseError("pack.json manquant")) } catch (e: Json.ParseError) { throw ParseError("pack.json : ${e.message}") }
        val fmt = root.i("format") ?: 1
        if (fmt > FORMAT) throw ParseError("pack.json : format $fmt non pris en charge (max $FORMAT)")
        val id = root.req("id", "pack.json")
        val lang = root.s("lang") ?: "fr"
        val chapters = root.l("chapters").mapIndexed { k, o ->
            val m = o.obj("pack.json : chapitre #$k")
            Chapter(m.req("id", "pack.json : chapitre #$k"), m.req("title", "pack.json : chapitre #$k"), m.i("order") ?: k, m.s("programRef"), m.s("summary"))
        }
        val lessons = ArrayList<Lesson>(); val exercises = ArrayList<Exercise>()
        for ((path, text) in files.toSortedMap()) {
            if (!path.startsWith("lessons/") || !path.endsWith(".json")) continue
            val m = try { Json.obj(text) } catch (e: Json.ParseError) { throw ParseError("$path : JSON invalide (${e.message})") }
            val defChapter = m.s("chapter")
            m.l("lessons").forEachIndexed { k, o -> lessons += lesson(o.obj("$path : leçon #$k"), "$path : leçon #$k", defChapter) }
            m.l("exercises").forEachIndexed { k, o -> exercises += exercise(o.obj("$path : exercice #$k"), "$path : exercice #$k", defChapter) }
        }
        val mocks = root.l("mockExams").mapIndexed { k, o ->
            val w = "pack.json : épreuve blanche #$k"; val m = o.obj(w)
            MockExamSpec(m.req("id", w), m.req("title", w), m.i("minutes") ?: throw ParseError("$w : \"minutes\" manquant"),
                m.l("sections").mapIndexed { j, so -> val sm = so.obj("$w section #$j"); MockExamSpec.Section(sm.req("title", "$w section #$j"), sm.strs("exercises")) },
                m.d("outOf") ?: 20.0, m.s("instructions"))
        }
        return Pack(
            id = id, version = root.i("version") ?: throw ParseError("pack.json : \"version\" manquant"),
            title = root.req("title", "pack.json"), lang = lang,
            cursus = root.req("cursus", "pack.json"), level = root.req("level", "pack.json"), subject = root.req("subject", "pack.json"),
            exam = root.s("exam"), series = root.strs("series"), programRef = root.s("programRef"), authors = root.strs("authors"),
            status = ReviewStatus.of(root.s("status")) ?: ReviewStatus.DRAFT, updatedAt = root.s("updatedAt"), description = root.s("description"),
            chapters = chapters.sortedBy { it.order }, lessons = lessons, exercises = exercises, mockExams = mocks,
        )
    }

    private fun lesson(m: Map<String, Any?>, w0: String, defChapter: String?): Lesson {
        val id = m.req("id", w0); val w = "$w0 ($id)"
        return Lesson(
            id = id, chapter = m.s("chapter") ?: defChapter ?: throw ParseError("$w : \"chapter\" manquant"),
            title = m.req("title", w),
            blocks = m.l("blocks").mapIndexed { k, o -> block(o.obj("$w bloc #$k"), "$w bloc #$k") },
            minutes = m.i("minutes") ?: 10, objectives = m.strs("objectives"), prerequisites = m.strs("prerequisites"),
            programRef = m.s("programRef"), status = ReviewStatus.of(m.s("status")) ?: ReviewStatus.DRAFT,
            author = m.s("author"), source = m.s("source"), lang = m.s("lang"), readAloud = m.b("readAloud"),
            exercises = m.strs("exercises"), selfCheck = m.strs("selfCheck"), reviewNotes = m.strs("reviewNotes"),
        )
    }

    private fun block(m: Map<String, Any?>, w: String): Block {
        val rv = m.b("review") ?: false
        return when (val t = m.req("type", w)) {
            "heading" -> Block.Heading(m.req("text", w))
            "text" -> Block.Text(m.req("md", w), rv)
            "key" -> Block.Key(m.s("style") ?: "retenir", m.s("title"), m.s("md") ?: "", m.s("tex"), rv)
            "formula" -> Block.Formula(m.req("tex", w), m.s("caption"), rv)
            "example" -> Block.Example(m.req("title", w), m.req("statement", w),
                m.l("steps").mapIndexed { k, o -> when (o) { is String -> Step(o); else -> o.obj("$w étape #$k").let { s -> Step(s.s("md") ?: "", s.s("tex")) } } },
                m.s("answer"), m["figure"]?.let { figure(it.obj("$w figure"), "$w figure") }, rv)
            "illustration" -> Block.Illustration(figure((m["figure"] ?: throw ParseError("$w : \"figure\" manquant")).obj("$w figure"), "$w figure"),
                m.s("caption"), m.s("alt") ?: m.s("caption") ?: "", rv)
            "video" -> Block.Video(m.req("title", w), m.s("src"), m.s("credit"), m.s("license"))
            "audio" -> Block.Audio(m.req("text", w), m.s("lang") ?: "fr-FR")
            "exercise" -> Block.ExerciseRef(m.req("ref", w))
            "more" -> Block.More(m.strs("items"))
            else -> throw ParseError("$w : type de bloc inconnu « $t »")
        }
    }

    fun exercise(m: Map<String, Any?>, w0: String, defChapter: String?, isPart: Boolean = false): Exercise {
        val id = m.req("id", w0); val w = "$w0 ($id)"
        val kind = ExerciseKind.of(m.s("kind")) ?: throw ParseError("$w : \"kind\" inconnu « ${m.s("kind")} »")
        val ans = m["answer"]
        return Exercise(
            id = id, chapter = m.s("chapter") ?: defChapter ?: if (isPart) "" else throw ParseError("$w : \"chapter\" manquant"),
            kind = kind, prompt = m.req("prompt", w), points = m.d("points") ?: 1.0,
            tier = m.s("tier")?.let { ExerciseTier.of(it) ?: throw ParseError("$w : \"tier\" inconnu « $it »") } ?: ExerciseTier.APPLICATION,
            difficulty = m.i("difficulty") ?: 1, tex = m.s("tex"),
            figure = m["figure"]?.let { figure(it.obj("$w figure"), "$w figure") },
            choices = m.strs("choices"),
            answerIndex = if (kind == ExerciseKind.MCQ) (ans as? Number)?.toInt() ?: throw ParseError("$w : \"answer\" (index) manquant") else -1,
            answerBool = if (kind == ExerciseKind.TRUE_FALSE) ans as? Boolean ?: throw ParseError("$w : \"answer\" (true/false) manquant") else null,
            answerNumber = if (kind == ExerciseKind.NUMERIC) (ans as? Number)?.toDouble() ?: throw ParseError("$w : \"answer\" (nombre) manquant") else null,
            tolerance = m.d("tolerance") ?: 0.0, unit = m.s("unit"),
            pairs = m.l("pairs").mapIndexed { k, o ->
                @Suppress("UNCHECKED_CAST") val p = o as? List<Any?> ?: throw ParseError("$w : paire #$k invalide")
                (p.getOrNull(0) as? String ?: "") to (p.getOrNull(1) as? String ?: "")
            },
            model = m.s("model"), rubric = m.strs("rubric"),
            parts = m.l("parts").mapIndexed { k, o -> exercise(o.obj("$w partie #$k"), "$w partie #$k", m.s("chapter") ?: defChapter, true) },
            explanation = m.s("explanation") ?: "", steps = m.strs("steps"), method = m.s("method"), mistakes = m.strs("mistakes"),
            review = m.b("review") ?: false, reviewNote = m.s("reviewNote"), source = m.s("source"), lesson = m.s("lesson"),
        )
    }

    fun figure(m: Map<String, Any?>, w: String): Figure {
        fun num(k: String) = m.d(k) ?: throw ParseError("$w : \"$k\" manquant")
        return when (val k = m.req("kind", w)) {
            "shapes" -> Figure.Shapes(num("w"), num("h"), m.l("items").mapIndexed { j, o -> shape(o.obj("$w élément #$j"), "$w élément #$j") })
            "svg" -> Figure.Svg(num("w"), num("h"), m.l("paths").mapIndexed { j, o -> val p = o.obj("$w chemin #$j")
                Figure.SvgItem(p.req("d", "$w chemin #$j"), p.s("fill"), if (p.containsKey("stroke")) p.s("stroke") else "ink", p.d("width") ?: 2.0) })
            "plot" -> Figure.Plot(num("xmin"), num("xmax"), num("ymin"), num("ymax"), m.d("grid") ?: 1.0, m.s("xlabel"), m.s("ylabel"),
                m.l("curves").map { o -> val c = o.obj("$w courbe"); Figure.Curve(c.req("expr", "$w courbe"), c.s("color") ?: "blue", c.s("label"), c.d("from"), c.d("to")) },
                m.l("points").map { o -> val p = o.obj("$w point"); Figure.PlotPoint(p.d("x") ?: 0.0, p.d("y") ?: 0.0, p.s("label"), p.s("color") ?: "red") },
                m.l("segments").map { o -> val p = o.obj("$w segment")
                    Figure.PlotSegment(p.d("x1") ?: 0.0, p.d("y1") ?: 0.0, p.d("x2") ?: 0.0, p.d("y2") ?: 0.0, p.s("color") ?: "grey", p.b("dash") ?: true, p.s("label")) },
                m.d("w") ?: 400.0, m.d("h") ?: 260.0)
            "timeline" -> Figure.Timeline(m.i("from") ?: throw ParseError("$w : \"from\" manquant"), m.i("to") ?: throw ParseError("$w : \"to\" manquant"),
                m.l("events").map { o -> val e = o.obj("$w événement"); Figure.Event(e.i("year") ?: throw ParseError("$w : année manquante"), e.req("label", "$w événement")) },
                m.l("periods").map { o -> val p = o.obj("$w période"); Figure.Period(p.i("from") ?: 0, p.i("to") ?: 0, p.req("label", "$w période"), p.s("color") ?: "orange") },
                m.d("w") ?: 480.0, m.d("h") ?: 240.0)
            "bars" -> Figure.Bars(m.l("bars").map { o -> val b = o.obj("$w barre"); Figure.Bar(b.req("label", "$w barre"), b.d("value") ?: 0.0, b.s("color") ?: "blue") },
                m.s("unit"), m.d("w") ?: 400.0, m.d("h") ?: 240.0)
            "count" -> Figure.Count(m.i("n") ?: throw ParseError("$w : \"n\" manquant"), m.s("shape") ?: "circle", m.s("color") ?: "orange",
                m.i("perRow") ?: 5, m.d("w") ?: 400.0, m.d("h") ?: 200.0)
            else -> throw ParseError("$w : type de figure inconnu « $k »")
        }
    }

    private fun shape(m: Map<String, Any?>, w: String): Shape {
        fun n(k: String) = m.d(k) ?: throw ParseError("$w : \"$k\" manquant")
        fun stroke() = if (m.containsKey("stroke")) m.s("stroke") else "ink"
        val width = m.d("width") ?: 2.0
        return when (val t = m.req("t", w)) {
            "line" -> Shape.Line(n("x1"), n("y1"), n("x2"), n("y2"), m.s("color") ?: "ink", width, m.b("dash") ?: false, m.s("arrow") ?: "none")
            "circle" -> Shape.Circle(n("cx"), n("cy"), n("r"), m.s("fill"), stroke(), width)
            "rect" -> Shape.Rect(n("x"), n("y"), n("w"), n("h"), m.s("fill"), stroke(), width, m.d("radius") ?: 0.0)
            "poly" -> Shape.Poly(m.l("pts").map { (it as? Number)?.toDouble() ?: throw ParseError("$w : pts invalides") }, m.s("fill"), stroke(), width, m.b("closed") ?: true)
            "text" -> Shape.Text(n("x"), n("y"), m.req("text", w), m.d("size") ?: 16.0, m.s("color") ?: "ink", m.s("anchor") ?: "middle", m.b("bold") ?: false)
            "angle" -> Shape.Angle(n("x"), n("y"), n("from"), n("to"), m.d("r") ?: 22.0, m.b("right") ?: false, m.s("label"), m.s("color") ?: "red")
            "path" -> Shape.Path(m.req("d", w), m.s("fill"), stroke(), width)
            else -> throw ParseError("$w : forme inconnue « $t »")
        }
    }
}
