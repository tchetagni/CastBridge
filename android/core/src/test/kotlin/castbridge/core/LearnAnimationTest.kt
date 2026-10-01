package castbridge.core

import castbridge.core.learn.*
import castbridge.core.quiz.Json
import kotlin.test.*

/** Timeline maths, parser, validator rules and playback state of the « Apprendre » animations (docs/LEARN.md § Animations). */
class LearnAnimationTest {
    private val fallback = Figure.Shapes(400.0, 240.0, listOf(Shape.Circle(350.0, 120.0, 20.0, "red")))

    private val demo = """{"w":400,"h":240,"mode":"steps",
      "items":[{"id":"dot","t":"circle","cx":50,"cy":120,"r":20,"fill":"blue"},
        {"id":"lbl","t":"text","x":20,"y":30,"text":"Valeur : {v}","anchor":"start","dec":1},
        {"id":"ln","t":"line","x1":50,"y1":200,"x2":350,"y2":200,"draw":0},
        {"id":"msg","t":"text","x":20,"y":60,"text":"Bonjour","anchor":"start","typed":0}],
      "do":[{"at":0,"d":2,"op":"move","on":"dot","dx":300,"ease":"linear"},
        {"at":0,"d":2,"op":"count","on":"lbl","v":10,"ease":"linear"},
        {"at":2,"d":1,"op":"draw","on":"ln","ease":"linear"},
        {"at":3,"d":1,"op":"type","on":"msg","ease":"linear"},
        {"at":2,"d":1,"op":"color","on":"dot","fill":"red","ease":"linear"}],
      "steps":[{"at":2,"say":"Le point avance."},{"at":4,"say":"La ligne et le texte apparaissent."}]}"""

    private fun parse(json: String, fb: Figure = fallback) = AnimationJson.parse(Json.obj(json), "test", fb)
    private fun anim() = parse(demo)
    private fun errors(a: AnimatedFigure, alt: String = "Un point bleu avance puis devient rouge.", fb: Figure = fallback): List<String> {
        val e = ArrayList<String>(); AnimationRules.check(a, fb, alt, "t", e, ArrayList()); return e
    }
    private inline fun <reified T : Op> Scene.all() = ops.filterIsInstance<T>()

    @Test fun framesAtBoundaries() {
        val a = anim()
        assertEquals(4.0, a.duration)
        assertEquals(50.0, a.frameAt(0.0).all<Op.Circle>().single().cx)
        assertEquals(200.0, a.frameAt(1.0).all<Op.Circle>().single().cx, 1e-9)
        assertEquals(350.0, a.frameAt(2.0).all<Op.Circle>().single().cx, 1e-9)
        assertEquals(a.frameAt(0.0).ops, a.frameAt(-5.0).ops, "before the start")
        assertEquals(a.frameAt(4.0).ops, a.frameAt(99.0).ops, "after the end")
        assertEquals(a.frameAt(0.0).ops, a.frameAt(Double.NaN).ops)
    }

    @Test fun counterLineAndTypewriter() {
        val a = anim()
        assertEquals("Valeur : 5,0", a.frameAt(1.0).all<Op.Text>().first().text)
        assertEquals("Valeur : 10,0", a.frameAt(2.0).all<Op.Text>().first().text)
        assertTrue(a.frameAt(1.0).all<Op.Path>().isEmpty(), "line not started: nothing drawn")
        val half = a.frameAt(2.5).all<Op.Path>().single().cmds
        assertEquals(PathCmd.M(50.0, 200.0), half.first()); assertEquals(PathCmd.L(200.0, 200.0), half.last())
        assertEquals(1, a.frameAt(2.0).all<Op.Text>().size, "typewriter at 0: no text yet")
        assertEquals("Bonj", a.frameAt(3.5).all<Op.Text>().last().text)
        assertEquals("Bonjour", a.frameAt(4.0).all<Op.Text>().last().text)
        assertEquals(1, a.frameAt(4.0).all<Op.Line>().size, "complete line is the real line")
    }

    @Test fun colorTweenAndFade() {
        val a = anim()
        val blue = Palette.color("blue")!!; val red = Palette.color("red")!!
        assertEquals(blue, a.frameAt(1.9).all<Op.Circle>().single().fill)
        assertEquals(Track.mix(blue, red, 0.5), a.frameAt(2.5).all<Op.Circle>().single().fill)
        assertEquals(red, a.frameAt(3.0).all<Op.Circle>().single().fill)
        val f = parse("""{"items":[{"id":"r","t":"rect","x":10,"y":10,"w":50,"h":50,"fill":"green","alpha":0}],"do":[{"at":0,"d":1,"op":"fade","on":"r","ease":"linear"}]}""")
        assertTrue(f.frameAt(0.0).ops.isEmpty(), "invisible element not emitted")
        assertEquals(0x80, f.frameAt(0.5).all<Op.Rect>().single().fill!! ushr 24)
        assertEquals(0xFF, f.frameAt(1.0).all<Op.Rect>().single().fill!! ushr 24)
    }

    @Test fun groupTransformsRotateAndScale() {
        val a = parse("""{"items":[{"id":"a","g":"G","t":"circle","cx":100,"cy":100,"r":10,"fill":"red"},{"id":"b","g":"G","t":"circle","cx":200,"cy":100,"r":10,"fill":"red"}],
            "do":[{"at":0,"d":1,"op":"rotate","on":"G","deg":90,"ease":"linear"},{"at":1,"d":1,"op":"scale","on":"G","s":2}]}""")
        val c = a.frameAt(1.0).all<Op.Circle>()    // pivot = (150,100); +90° clockwise on screen: (x,y) → centre + (-dy, dx)
        assertEquals(150.0, c[0].cx, 1e-9); assertEquals(50.0, c[0].cy, 1e-9)
        assertEquals(150.0, c[1].cx, 1e-9); assertEquals(150.0, c[1].cy, 1e-9)
        val d = a.frameAt(2.0).all<Op.Circle>()
        assertEquals(20.0, d[0].r, 1e-9); assertEquals(0.0, d[0].cy, 1e-9); assertEquals(200.0, d[1].cy, 1e-9)
    }

    @Test fun easingsAreMonotonicWithExactEnds() {
        for (e in Ease.values()) {
            assertEquals(0.0, e.at(0.0), 1e-12, e.key); assertEquals(1.0, e.at(1.0), 1e-12, e.key)
            var prev = 0.0
            for (k in 1..1000) { val v = e.at(k / 1000.0); assertTrue(v >= prev - 1e-12, "${e.key} not monotonic at $k"); prev = v }
            assertEquals(0.0, e.at(-3.0)); assertEquals(1.0, e.at(7.0))
        }
        assertTrue(Ease.IN.at(0.5) < 0.5 && Ease.OUT.at(0.5) > 0.5 && Ease.IN_OUT.at(0.5) == 0.5 && Ease.CUBIC.at(0.5) == 0.5)
    }

    @Test fun stepStops() {
        val a = anim()
        assertEquals(2.0, a.nextStopAfter(0.0)); assertEquals(4.0, a.nextStopAfter(2.0)); assertNull(a.nextStopAfter(4.0))
        assertEquals(4.0, a.nextStopAfter(3.99))
        assertEquals(2.0, a.prevStopBefore(4.0)); assertEquals(0.0, a.prevStopBefore(2.0)); assertEquals(0.0, a.prevStopBefore(1.0)); assertNull(a.prevStopBefore(0.0))
        assertEquals("Le point avance.", a.captionAt(0.0)); assertEquals("Le point avance.", a.captionAt(2.0))
        assertEquals("La ligne et le texte apparaissent.", a.captionAt(2.1)); assertEquals("La ligne et le texte apparaissent.", a.captionAt(4.0))
        assertEquals(listOf("Le point avance.", "La ligne et le texte apparaissent."), a.captions)
    }

    @Test fun loopingWrapsAndOneShotClamps() {
        val src = """{"items":[{"id":"d","t":"circle","cx":0,"cy":50,"r":5,"fill":"red"}],"do":[{"at":0,"d":2,"op":"move","on":"d","dx":100,"ease":"linear"}],"loop":LOOP}"""
        val loop = parse(src.replace("LOOP", "true")); val once = parse(src.replace("LOOP", "false"))
        assertEquals(loop.frameAt(0.5).ops, loop.frameAt(2.5).ops); assertEquals(loop.frameAt(0.5).ops, loop.frameAt(10.5).ops)
        assertEquals(once.frameAt(2.0).ops, once.frameAt(10.5).ops)
        assertTrue(0.0 <= loop.norm(1e9) && loop.norm(1e9) < 2.0)
    }

    @Test fun framesAreFiniteAndDeterministic() {
        val a = anim(); val b = anim()
        for (k in 0..80) {
            val t = k * 0.05
            val s = a.frameAt(t)
            assertEquals(s.ops, b.frameAt(t).ops, "same input, same frame at $t")
            assertEquals(s.ops, a.frameAt(t).ops)
            for (op in s.ops) when (op) {
                is Op.Circle -> assertTrue(op.cx.isFinite() && op.cy.isFinite() && op.r >= 0)
                is Op.Path -> op.cmds.forEach { c -> if (c is PathCmd.L) assertTrue(c.x.isFinite() && c.y.isFinite()) }
                else -> {}
            }
        }
    }

    @Test fun parserRejectsStructuralMistakes() {
        fun bad(extra: String, msg: String) {
            val ex = assertFailsWith<LessonJson.ParseError>(msg) { parse("""{"items":[{"id":"a","t":"circle","cx":1,"cy":1,"r":1,"fill":"red"},{"id":"s","t":"text","x":1,"y":1,"text":"hi","anchor":"start"}],"do":[$extra]}""") }
            assertTrue(ex.message!!.isNotBlank())
        }
        bad("""{"at":0,"op":"explode","on":"a"}""", "unknown op")
        bad("""{"at":0,"op":"fade","on":"zzz"}""", "unknown target")
        bad("""{"at":0,"op":"fade","on":"a","ease":"wobble"}""", "unknown easing")
        bad("""{"at":0,"d":2,"op":"move","on":"a","dx":5},{"at":1,"d":2,"op":"move","on":"a","dx":9}""", "overlapping same property")
        bad("""{"at":0,"op":"type","on":"a"}""", "typewriter on a circle")
        bad("""{"at":0,"op":"count","on":"s","v":3}""", "count without {v}")
        bad("""{"at":0,"op":"color","on":"a","fill":"nonsense"}""", "bad color")
        bad("""{"at":0,"op":"scale","on":"a"}""", "missing s")
        // same property on different elements, or different properties at once, is fine
        parse("""{"items":[{"id":"a","t":"circle","cx":1,"cy":1,"r":1,"fill":"red"}],"do":[{"at":0,"d":2,"op":"move","on":"a","dx":5},{"at":0,"d":2,"op":"scale","on":"a","s":2}]}""")
    }

    @Test fun validatorBudgetsDurationsAndAccessibility() {
        assertEquals(emptyList(), errors(anim()))
        assertTrue(errors(anim(), alt = "court").any { "alt" in it })
        assertTrue(errors(anim(), fb = Figure.Shapes(300.0, 200.0, fallback.items)).any { "taille" in it })
        // too long
        val long = parse("""{"items":[{"t":"circle","cx":1,"cy":1,"r":1,"fill":"red"}],"do":[{"at":0,"d":31,"op":"move","on":"_0","dx":5}]}""")
        assertTrue(errors(long).any { "durée" in it }, errors(long).toString())
        val tooFast = parse("""{"items":[{"t":"circle","cx":1,"cy":1,"r":1,"fill":"red"}],"do":[{"at":0,"d":0.05,"op":"move","on":"_0","dx":5}]}""")
        assertTrue(errors(tooFast).any { "min" in it })
        // size budget: 40 KB
        val items = (0 until 100).joinToString(",") { """{"t":"text","x":10,"y":10,"text":"${"x".repeat(450)}","anchor":"start"}""" }
        val big = parse("""{"items":[$items],"do":[{"at":0,"d":1,"op":"fade","on":"_0","a":0.5}]}""")
        assertTrue(big.sizeBytes > AnimationRules.MAX_BYTES)
        assertTrue(errors(big).any { "octets" in it })
        // too many objects
        val many = (0 until 130).joinToString(",") { """{"t":"circle","cx":$it,"cy":5,"r":1,"fill":"red"}""" }
        assertTrue(errors(parse("""{"items":[$many],"do":[{"at":0,"d":1,"op":"fade","on":"_0","a":0.5}]}""")).any { "éléments" in it })
        // step mode: last stop at the end, no looping, captions
        val noEnd = parse(demo.replace("""{"at":4,"say""", """{"at":3.5,"say""").replace("\"mode\":\"steps\"", "\"mode\":\"steps\""))
        assertTrue(errors(noEnd).any { "dernière étape" in it })
        val loopSteps = parse(demo.replace("\"mode\":\"steps\"", "\"mode\":\"steps\",\"loop\":true"))
        assertTrue(errors(loopSteps).any { "boucler" in it })
        assertTrue(errors(parse(demo.replace("Le point avance.", " "))).any { "légende vide" in it })
        assertFailsWith<LessonJson.ParseError> { parse(demo.replace("""{"at":2,"say":"Le point avance."},""", "")); parse(demo.replace("\"steps\":[", "\"steps\":[],\"zz\":[")); parse("""{"mode":"steps","items":[],"do":[]}""") }
    }

    private fun flashJson(n: Int, gap: Double) = """{"items":[{"id":"bg","t":"rect","x":0,"y":0,"w":400,"h":240,"fill":"white"}],"do":[${
        (0 until n).joinToString(",") { """{"at":${it * gap},"d":0,"op":"fade","on":"bg","a":${if (it % 2 == 0) 1 else 0}}""" }}],"steps":[],"loop":false}"""

    @Test fun flashingRule() {
        // 8 abrupt on/off changes of a full-screen rectangle in 1.4 s = 2.9 flashes/s → ok; every 0.1 s = 5 flashes/s → refused
        val ok = parse(flashJson(6, 0.34).replace("\"items\":[{\"id\":\"bg\"", "\"items\":[{\"id\":\"bg\",\"alpha\":0"))
        assertEquals(emptyList(), errors(ok).filter { "flash" in it })
        val bad = parse(flashJson(14, 0.1))
        assertTrue(errors(bad).any { "flash" in it }, errors(bad).toString())
        // small elements may blink freely (below the area threshold)
        val small = parse(flashJson(14, 0.1).replace("\"w\":400,\"h\":240", "\"w\":20,\"h\":20"))
        assertEquals(emptyList(), errors(small).filter { "flash" in it })
        // a loop repeats: 3 changes per second, 3 s of silence is ok, but a tight loop is not
        val tight = parse(flashJson(4, 0.15).replace("\"loop\":false", "\"loop\":true").replace("\"items\":[{\"id\":\"bg\"", "\"items\":[{\"id\":\"bg\",\"alpha\":0"))
        assertTrue(errors(tight).any { "flash" in it })
    }

    @Test fun linterFindsOverlappingLabelsOverTime() {
        val a = parse("""{"items":[{"id":"a","t":"text","x":50,"y":50,"text":"Premier","anchor":"start"},
            {"id":"b","t":"text","x":300,"y":50,"text":"Second","anchor":"start"}],"do":[{"at":0,"d":2,"op":"move","on":"b","dx":-240,"ease":"linear"}]}""")
        val msgs = AnimationLint.lint(a)
        assertTrue(msgs.any { "Premier" in it && "Second" in it && "chevauchent" in it }, msgs.toString())
        val ok = parse("""{"items":[{"id":"a","t":"text","x":50,"y":50,"text":"Premier","anchor":"start"},
            {"id":"b","t":"text","x":300,"y":150,"text":"Second","anchor":"start"}],"do":[{"at":0,"d":2,"op":"move","on":"b","dx":-240,"ease":"linear"}]}""")
        assertEquals(emptyList(), AnimationLint.lint(ok))
        val out = parse("""{"items":[{"id":"a","t":"text","x":380,"y":50,"text":"Trop long pour la ligne","anchor":"start"}],"do":[{"at":0,"d":1,"op":"fade","on":"a","a":1}]}""")
        assertTrue(AnimationLint.lint(out).any { "cadre" in it })
    }

    // ------------------------------------------------------------------ integration in packs: older readers keep the static figure
    private val packJson = """{"format":1,"id":"t-anim","version":1,"title":"Test","lang":"fr","cursus":"secondaire","level":"3e","subject":"maths","chapters":[{"id":"t-anim-c1","title":"C1"}]}"""
    private fun lesson(anim: String?, alt: String = "Un point bleu avance puis devient rouge.") = """{"chapter":"t-anim-c1","lessons":[{"id":"t-anim-l1","title":"L","blocks":[
        {"type":"illustration","alt":"$alt","caption":"Légende","figure":{"kind":"shapes","w":400,"h":240,"items":[{"t":"circle","cx":350,"cy":120,"r":20,"fill":"red"}]}${if (anim != null) ",\"animation\":$anim" else ""}}]}],"exercises":[]}"""
    private fun pack(anim: String?) = LessonJson.parsePack(mapOf("pack.json" to packJson, "lessons/c1.json" to lesson(anim)))

    @Test fun animatedIllustrationInAPack() {
        val b = pack(demo).lessons.single().blocks.single() as Block.Illustration
        assertNotNull(b.animation); assertEquals(2, b.animation!!.stops.size)
        assertTrue(LessonValidator().validate(pack(demo)).errors.none { "animation" in it }, LessonValidator().validate(pack(demo)).errors.toString())
        val plain = pack(null).lessons.single().blocks.single() as Block.Illustration
        assertNull(plain.animation); assertEquals(plain.figure, b.figure)
        // an invalid animation blocks the pack, with a readable message
        val r = LessonValidator().validate(pack(demo.replace("\"w\":400", "\"w\":300")))
        assertTrue(r.errors.any { "animation" in it && "taille" in it }, r.errors.toString())
        assertTrue(LessonValidator().validate(LessonJson.parsePack(mapOf("pack.json" to packJson, "lessons/c1.json" to lesson(demo, alt = "")))).errors.any { "alt" in it })
        assertEquals(listOf("Le point avance.", "La ligne et le texte apparaissent."), b.animation!!.captions)
    }

    // ------------------------------------------------------------------ playback
    @Test fun playerStepMode() {
        val p = AnimPlayer(anim(), reduced = false)
        assertEquals(0.0, p.t); assertFalse(p.playing)
        p.toggle(); assertTrue(p.playing)                 // OK → play to the first stop
        var now = 1_000_000_000L; var frames = 0; var guard = 0
        while (p.playing && guard++ < 1000) { if (p.advance(now)) frames++; now += 16_000_000 }   // 60 Hz clock
        assertEquals(2.0, p.t); assertFalse(p.playing)
        assertTrue(frames in 55..70, "capped to ~30 fps over 2 s: $frames")
        p.toggle(); while (p.playing) { p.advance(now); now += 33_000_000 }
        assertEquals(4.0, p.t); assertTrue(p.finished)
        p.prev(); assertEquals(2.0, p.t); p.prev(); assertEquals(0.0, p.t)
        p.next(); assertTrue(p.playing); p.next(); /* second OK while playing: ignored target change */
        p.toggle(); assertEquals(2.0, p.t); assertFalse(p.playing)   // OK while playing = skip to the stop
        p.toggle(); p.toggle(); assertEquals(4.0, p.t)
        p.toggle(); assertEquals(0.0, p.t)                           // at the end, OK replays from the start (paused at step 0)
    }

    @Test fun playerDropsFramesAfterAHiccup() {
        val p = AnimPlayer(parse("""{"items":[{"t":"circle","cx":0,"cy":50,"r":5,"fill":"red"}],"do":[{"at":0,"d":10,"op":"move","on":"_0","dx":100}]}"""), reduced = false)
        p.play(); p.advance(1_000_000_000L)
        assertTrue(p.advance(1_034_000_000L)); assertEquals(0.034, p.t, 1e-9)
        assertFalse(p.advance(1_040_000_000L), "too soon for 30 fps")
        assertTrue(p.advance(5_000_000_000L)); assertEquals(0.134, p.t, 1e-9)    // a 4 s stall advances by 0.1 s only
        p.pause(); assertFalse(p.playing)
    }

    @Test fun playerAutoModeAndLoop() {
        val one = AnimPlayer(parse("""{"items":[{"t":"circle","cx":0,"cy":50,"r":5,"fill":"red"}],"do":[{"at":0,"d":1,"op":"move","on":"_0","dx":100}]}"""), reduced = false)
        one.toggle(); var n = 1_000_000_000L; while (one.playing) { one.advance(n); n += 40_000_000 }
        assertEquals(1.0, one.t); assertTrue(one.finished); one.toggle(); assertTrue(one.playing); assertEquals(0.0, one.t)
        val lp = AnimPlayer(parse("""{"loop":true,"items":[{"t":"circle","cx":0,"cy":50,"r":5,"fill":"red"}],"do":[{"at":0,"d":1,"op":"move","on":"_0","dx":100}]}"""), reduced = false)
        lp.play(); n = 1_000_000_000L; repeat(100) { lp.advance(n); n += 40_000_000 }
        assertTrue(lp.playing && lp.t in 0.0..1.0)
    }

    @Test fun reducedMotionJumpsBetweenStepFrames() {
        val p = AnimPlayer(anim(), reduced = true)
        assertEquals(0.0, p.t)
        p.toggle(); assertEquals(2.0, p.t); assertFalse(p.playing)
        p.toggle(); assertEquals(4.0, p.t); p.prev(); assertEquals(2.0, p.t)
        assertEquals(2, p.stepCount); assertEquals("Le point avance.", p.caption)
        val auto = AnimPlayer(parse("""{"items":[{"t":"circle","cx":0,"cy":50,"r":5,"fill":"red"}],"do":[{"at":0,"d":1,"op":"move","on":"_0","dx":100}]}"""), reduced = true)
        assertEquals(1.0, auto.t, 0.0); auto.play(); assertFalse(auto.playing)
        AnimSettings.appReduce = true; try { assertTrue(AnimSettings.reduce) } finally { AnimSettings.appReduce = false }
        assertFalse(AnimSettings.reduce)
    }
}
