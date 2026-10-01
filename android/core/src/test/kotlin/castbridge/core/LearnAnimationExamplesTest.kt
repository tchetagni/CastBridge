package castbridge.core

import castbridge.core.learn.*
import castbridge.core.quiz.Json
import java.io.File
import kotlin.math.abs
import kotlin.test.*

/**
 * The generated examples of tools/anim (one per template): each must parse, validate (budget, durations, flashing, alt text),
 * have a faithful static fallback, a clean label layout over time, deterministic frames, and be rendered to a PNG contact sheet.
 */
class LearnAnimationExamplesTest {
    private val dir = File(System.getProperty("anim.dir") ?: "../../tools/anim", "examples")
    private val sheets = File(System.getProperty("anim.sheets") ?: "build/anim-sheets")
    private val files get() = (dir.listFiles { f -> f.name.endsWith(".json") } ?: emptyArray()).sortedBy { it.name }

    private fun load(f: File) = LessonJson.block(Json.obj(f.readText()), f.name) as Block.Illustration

    @Test fun atLeastFifteenTemplatesWithExamples() {
        assertTrue(files.size >= 15, "found ${files.size} examples in $dir")
    }

    @Test fun everyExampleIsValidAndWithinBudget() {
        for (f in files) {
            val b = load(f); val a = assertNotNull(b.animation, f.name)
            val e = ArrayList<String>(); val w = ArrayList<String>()
            AnimationRules.check(a, b.figure, b.alt, f.name, e, w)
            assertEquals(emptyList(), e, "${f.name} errors"); assertEquals(emptyList(), w, "${f.name} warnings (layout, size)")
            assertTrue(a.sizeBytes in 200..AnimationRules.MAX_BYTES, "${f.name}: ${a.sizeBytes} bytes")
            assertTrue(a.sizeBytes <= 12 * 1024, "${f.name} is a typical example, well under the budget: ${a.sizeBytes}")
            assertFalse(b.alt.isBlank()); assertTrue(a.duration in 0.2..30.0)
            if (!a.loop) assertTrue(a.stops.isNotEmpty() || a.duration <= 8, "${f.name}: captions")
        }
    }

    @Test fun staticFallbackMatchesTheLastFrame() {
        for (f in files) {
            val b = load(f); val a = b.animation!!
            val last = a.frameAt(a.duration).ops; val still = Scene.build(b.figure).ops
            assertEquals(still.size, last.size, "${f.name}: op count of the fallback")
            for ((x, y) in still.zip(last)) {
                assertEquals(x::class, y::class, f.name)
                when {
                    x is Op.Text && y is Op.Text -> { assertEquals(x.text, y.text, f.name); assertEquals(x.x, y.x, 0.2, f.name); assertEquals(x.y, y.y, 0.2, f.name) }
                    x is Op.Circle && y is Op.Circle -> { assertEquals(x.cx, y.cx, 0.2, f.name); assertEquals(x.cy, y.cy, 0.2, f.name); assertEquals(x.fill, y.fill, f.name) }
                    x is Op.Rect && y is Op.Rect -> { assertEquals(x.x, y.x, 0.2, f.name); assertEquals(x.fill, y.fill, f.name) }
                    x is Op.Line && y is Op.Line -> { assertEquals(x.x2, y.x2, 0.2, f.name); assertEquals(x.color, y.color, f.name) }
                }
            }
        }
    }

    @Test fun framesAreDeterministicAndFinite() {
        for (f in files) {
            val a = load(f).animation!!; val b = load(f).animation!!
            for (k in 0..24) {
                val t = a.duration * k / 24
                assertEquals(a.frameAt(t).ops, b.frameAt(t).ops, "${f.name} @ $t")
            }
            val big = a.frameAt(a.duration / 2).ops
            for (op in big) if (op is Op.Circle) assertTrue(op.cx.isFinite() && op.cy.isFinite() && op.r.isFinite(), f.name)
            // everything stays inside the figure (with the usual 5 % margin) at the step stops
            for (t in listOf(0.0) + a.stops.map { it.at }) for (op in a.frameAt(t).ops) if (op is Op.Circle && op.fill != null && (op.fill!! ushr 24) > 0xC0)
                assertTrue(op.cx in -a.w * 0.1..a.w * 1.1 && op.cy in -a.h * 0.1..a.h * 1.1, "${f.name}: a dot leaves the figure at t=$t")
        }
    }

    @Test fun renderContactSheets() {
        for (f in files) {
            val a = load(f).animation!!
            AnimSheet.write(sheets, f.name.removeSuffix(".json"), a)
            val png = File(sheets, f.name.removeSuffix(".json") + ".png")
            assertTrue(png.length() > 5_000, "${png.name} rendered")
        }
    }

    @Test fun stepModeExamplesStopAtEveryCaption() {
        for (f in files) {
            val a = load(f).animation!!
            if (!a.stepMode) continue
            var t = 0.0; var n = 0
            while (true) { t = a.nextStopAfter(t) ?: break; n++; assertNotNull(a.captionAt(t)) }
            assertEquals(a.stops.size, n, f.name)
            assertTrue(abs(a.stops.last().at - a.duration) < 0.01, f.name)
        }
    }
}
