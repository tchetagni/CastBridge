package castbridge.core.tv

import castbridge.core.tv.UsbSafeRemoval.Button
import castbridge.core.tv.UsbSafeRemoval.Step
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * « Retrait sûr » (2026-10-07) : une ligne MENU « Préparer le retrait de la clé USB » termine ou met en pause les écritures vers la clé, vide ce qui reste (`fsync` + `sync`), puis dit
 * « Vous pouvez retirer la clé ». Règle pure : le déroulement, les mots et leur ORDRE (rien n'est vidé tant qu'une copie écrit encore, jamais « prête » sans confirmation du vidage).
 */
class UsbSafeRemovalTest {
    private val K = "usb-A379-E209"

    private class FakeHost : RemovalHost {
        val log = ArrayList<String>()
        var writes = mutableListOf<WriteInfo>()
        var present = true
        var flushOk = true
        /** Ce que valait la liste des écritures au moment de chaque vidage (doit toujours être 0). */
        val writesAtFlush = ArrayList<Int>()
        var onFlush: () -> Unit = {}
        override fun writes(volumeId: String): List<WriteInfo> = writes.toList()
        override fun fence(volumeId: String, stop: Boolean) { log += if (stop) "fence:stop" else "fence:drain" }
        override fun unfence(volumeId: String) { log += "unfence" }
        override fun flush(volumeId: String): Boolean { log += "flush"; writesAtFlush += writes.size; onFlush(); return flushOk }
        override fun present(volumeId: String): Boolean = present
    }

    private var t = 5_000_000L
    private val host = FakeHost()
    private fun flow() = UsbSafeRemoval(host) { t }
    private fun copy(name: String, pct: Int = 40) = WriteInfo(name, pct)

    // ---- nothing is being written ----

    @Test fun `with no copy running the key is prepared at once`() {
        val f = flow()
        val v = f.start(K, "Lexar")
        assertEquals(Step.READY, v.step)
        assertEquals(listOf("fence:stop", "flush"), host.log, "nothing may start writing again, THEN everything is flushed")
        assertEquals("Préparer le retrait de la clé « Lexar »", v.title)
        assertTrue("Vous pouvez retirer la clé « Lexar »." in v.lines, v.lines.toString())
        assertTrue("Pour une éjection complète : Réglages › Stockage › Éjecter." in v.lines, v.lines.toString())
        assertFalse(v.lines.any { "en pause" in it }, "no copy was paused: no promise to resume")
        assertEquals(listOf(Button.SETTINGS, Button.RESUME, Button.CLOSE), v.buttons)
        assertFalse(v.busy)
    }

    @Test fun `a key without a name is a key`() {
        assertEquals("Préparer le retrait de la clé USB", flow().start(K, "").title)
        assertTrue("Vous pouvez retirer la clé." in flow().start(K, "  ").lines)
    }

    // ---- copies are running ----

    @Test fun `with copies running the TV says which and offers to wait or to pause, nothing is touched yet`() {
        host.writes = mutableListOf(copy("Film 2024.mkv", 62))
        val v = flow().start(K, "Lexar")
        assertEquals(Step.ASK, v.step)
        assertEquals(emptyList(), host.log, "asking changes nothing")
        assertEquals("Une copie écrit sur la clé : « Film 2024.mkv » (62 %).", v.lines.first())
        assertTrue(v.lines.any { "reprendra quand la clé sera de retour" in it }, v.lines.toString())
        assertEquals(listOf(Button.WAIT, Button.PAUSE, Button.CANCEL), v.buttons)
    }

    @Test fun `several copies are listed, three names at most`() {
        host.writes = mutableListOf(copy("A.mkv", 10), copy("B.mkv", -1), copy("C.mkv", 100), copy("D.mkv"), copy("E.mkv"))
        val l = flow().start(K, "Lexar").lines.first()
        assertEquals("5 copies écrivent sur la clé : « A.mkv » (10 %), « B.mkv », « C.mkv » (100 %) et 2 autre(s).", l)
        host.writes = mutableListOf(copy("A.mkv", 10), copy("B.mkv", 20))
        assertEquals("2 copies écrivent sur la clé : « A.mkv » (10 %), « B.mkv » (20 %).", flow().start(K, "Lexar").lines.first())
        val pl = flow().start(K, "Lexar").lines.joinToString(" ")
        assertTrue("elles reprendront" in pl && "mettez-les en pause" in pl, "plural: $pl")
    }

    @Test fun `waiting refuses new copies at once, lets the running ones end, then stops everything and flushes`() {
        host.writes = mutableListOf(copy("Film.mkv", 62))
        val f = flow(); f.start(K, "Lexar")
        var v = f.press(Button.WAIT)
        assertEquals(Step.WAITING, v.step)
        assertEquals(listOf("fence:drain"), host.log, "no new copy, the running one is not cut")
        assertTrue(v.lines.any { "« Film.mkv »" in it && "62 %" in it }, v.lines.toString())
        assertEquals(listOf(Button.PAUSE_NOW, Button.CANCEL), v.buttons)
        // still writing a second later: still waiting, nothing flushed
        t += 1_000; host.writes = mutableListOf(copy("Film.mkv", 80))
        v = f.tick()
        assertEquals(Step.WAITING, v.step)
        assertTrue(v.lines.any { "80 %" in it }, "the progress follows")
        assertEquals(listOf("fence:drain"), host.log)
        // the copy ends
        t += 1_000; host.writes = mutableListOf()
        v = f.tick()
        assertEquals(Step.READY, v.step)
        assertEquals(listOf("fence:drain", "fence:stop", "flush"), host.log, "hard stop BEFORE the flush: a late copy cannot slip in after it")
        assertEquals(listOf(0), host.writesAtFlush)
        assertFalse(v.lines.any { "en pause" in it }, "nothing was paused: the copy had ended")
    }

    @Test fun `pausing stops the copies, waits until they let go, flushes, and says they will resume`() {
        host.writes = mutableListOf(copy("Film.mkv", 62))
        val f = flow(); f.start(K, "Lexar")
        var v = f.press(Button.PAUSE)
        assertEquals(Step.STOPPING, v.step)
        assertEquals(listOf("fence:stop"), host.log)
        assertTrue(v.busy)
        assertEquals(emptyList(), v.buttons)
        t += 500
        v = f.tick()
        assertEquals(Step.STOPPING, v.step, "the copy still holds the key: no flush")
        assertFalse("flush" in host.log)
        t += 500; host.writes = mutableListOf()
        v = f.tick()
        assertEquals(Step.READY, v.step)
        assertEquals(listOf("fence:stop", "flush"), host.log)
        assertEquals(listOf(0), host.writesAtFlush)
        assertTrue(v.lines.any { "Les copies en pause reprendront quand vous remettrez la clé." == it }, v.lines.toString())
    }

    @Test fun `waiting can become pausing`() {
        host.writes = mutableListOf(copy("Film.mkv"))
        val f = flow(); f.start(K, "Lexar"); f.press(Button.WAIT)
        val v = f.press(Button.PAUSE_NOW)
        assertEquals(Step.STOPPING, v.step)
        assertEquals(listOf("fence:drain", "fence:stop"), host.log)
        host.writes = mutableListOf()
        val done = f.tick()
        assertEquals(Step.READY, done.step)
        assertTrue(done.lines.any { "en pause" in it }, "it was paused by the owner")
    }

    @Test fun `a copy that will not stop makes the preparation fail instead of lying`() {
        host.writes = mutableListOf(copy("Film.mkv"))
        val f = flow(); f.start(K, "Lexar"); f.press(Button.PAUSE)
        t += UsbSafeRemoval.STOP_TIMEOUT_MS - 1
        assertEquals(Step.STOPPING, f.tick().step)
        t += 1
        val v = f.tick()
        assertEquals(Step.FAILED, v.step)
        assertTrue(v.lines.first().startsWith("Une copie ne s'arrête pas") && "« Film.mkv »" in v.lines.first(), v.lines.toString())
        assertTrue(v.lines.any { "Ne retirez pas la clé" in it }, v.lines.toString())
        assertFalse("flush" in host.log, "nothing is flushed under a running copy, and « prête » is never said")
        assertEquals(listOf(Button.RETRY, Button.SETTINGS, Button.RESUME), v.buttons)
    }

    @Test fun `a copy that finishes its verification is waited for, not accused of not stopping`() {
        host.writes = mutableListOf(copy("Film.mkv", 100))
        val f = flow(); f.start(K, "Lexar"); f.press(Button.PAUSE)
        t += UsbSafeRemoval.STOP_TIMEOUT_MS
        val v = f.tick()
        assertEquals(Step.FAILED, v.step)
        assertTrue("finit sa vérification" in v.lines.first() && "« Film.mkv » (100 %)" in v.lines.first(), v.lines.toString())
        assertFalse("ne s'arrête pas" in v.lines.first(), v.lines.toString())
        host.writes = mutableListOf(copy("A.mkv", 100), copy("B.mkv", 100))
        val g = flow(); g.start(K, "Lexar"); g.press(Button.PAUSE)
        t += UsbSafeRemoval.STOP_TIMEOUT_MS
        assertTrue("finissent leur vérification" in g.tick().lines.first())
        // one copy still running among them: that one does not stop
        host.writes = mutableListOf(copy("A.mkv", 100), copy("B.mkv", 40))
        val h = flow(); h.start(K, "Lexar"); h.press(Button.PAUSE)
        t += UsbSafeRemoval.STOP_TIMEOUT_MS
        assertTrue("ne s'arrête pas" in h.tick().lines.first())
    }

    @Test fun `retrying after a failure starts again, and succeeds once the copy lets go`() {
        host.writes = mutableListOf(copy("Film.mkv"))
        val f = flow(); f.start(K, "Lexar"); f.press(Button.PAUSE)
        t += UsbSafeRemoval.STOP_TIMEOUT_MS
        assertEquals(Step.FAILED, f.tick().step)
        host.writes = mutableListOf()
        val v = f.press(Button.RETRY)
        assertEquals(Step.READY, v.step)
        assertEquals(listOf("fence:stop", "fence:stop", "flush"), host.log)
    }

    // ---- the flush ----

    @Test fun `a flush that is not confirmed is a failure, never a ready key`() {
        host.flushOk = false
        val f = flow()
        val v = f.start(K, "Lexar")
        assertEquals(Step.FAILED, v.step)
        assertTrue(v.lines.first().contains("n'a pas pu confirmer"), v.lines.toString())
        assertTrue(v.lines.any { "Réglages › Stockage › Éjecter" in it }, v.lines.toString())
        assertFalse(v.lines.any { "Vous pouvez retirer" in it })
        assertEquals(listOf(Button.RETRY, Button.SETTINGS, Button.RESUME), v.buttons)
        host.flushOk = true
        assertEquals(Step.READY, f.press(Button.RETRY).step)
        assertEquals(listOf("fence:stop", "flush", "fence:stop", "flush"), host.log)
    }

    @Test fun `the flush never runs while a copy still writes`() {
        host.writes = mutableListOf(copy("A.mkv"))
        val f = flow(); f.start(K, "Lexar"); f.press(Button.WAIT)
        for (i in 1..3) { t += 1_000; f.tick() }
        host.writes = mutableListOf(); f.tick()
        host.writes = mutableListOf(copy("B.mkv")); f.tick()
        assertTrue(host.writesAtFlush.all { it == 0 }, host.writesAtFlush.toString())
    }

    // ---- the key goes away, or stays ----

    @Test fun `a key that is gone is said gone and the fence is lifted, at every step`() {
        host.writes = mutableListOf(copy("A.mkv"))
        val f = flow(); f.start(K, "Lexar"); f.press(Button.WAIT)
        host.present = false
        val v = f.tick()
        assertEquals(Step.GONE, v.step)
        assertEquals("La clé a été retirée.", v.lines.first())
        assertEquals("unfence", host.log.last(), "when the key comes back, copies are not refused for ever")
        assertEquals(listOf(Button.CLOSE), v.buttons)
        // at the question
        host.log.clear(); host.present = true
        val g = flow(); g.start(K, "Lexar")                    // writes still there: ASK
        host.present = false
        assertEquals(Step.GONE, g.tick().step)
        // already prepared
        host.writes = mutableListOf(); host.present = true; host.log.clear()
        val h = flow(); assertEquals(Step.READY, h.start(K, "Lexar").step)
        host.present = false
        assertEquals(Step.GONE, h.tick().step)
        assertEquals("unfence", host.log.last())
        // a prepared key that is gone is not flushed again
        assertEquals(1, host.log.count { it == "flush" })
    }

    @Test fun `no key at all is said at once, nothing is touched`() {
        host.present = false
        val v = flow().start(K, "Lexar")
        assertEquals(Step.GONE, v.step)
        assertEquals(emptyList(), host.log)
    }

    @Test fun `a prepared key that stays plugged gets its copies back after ten minutes`() {
        val f = flow(); f.start(K, "Lexar")
        t += UsbSafeRemoval.READY_HOLD_MS - 1
        assertEquals(Step.READY, f.tick().step)
        t += 1
        val v = f.tick()
        assertEquals(Step.EXPIRED, v.step)
        assertEquals("unfence", host.log.last())
        assertTrue(v.lines.first().contains("n'a pas été retirée") && "reprennent" in v.lines.first(), v.lines.toString())
    }

    @Test fun `resuming gives the key back at once`() {
        val f = flow(); f.start(K, "Lexar")
        val v = f.press(Button.RESUME)
        assertEquals(Step.CLOSED, v.step)
        assertEquals("unfence", host.log.last())
        assertTrue(v.lines.first().contains("reprennent"), v.lines.toString())
        // also after a failure
        host.flushOk = false; host.log.clear()
        val g = flow(); g.start(K, "Lexar")
        assertEquals(Step.CLOSED, g.press(Button.RESUME).step)
        assertEquals("unfence", host.log.last())
    }

    @Test fun `cancelling at any question or wait gives the key back`() {
        host.writes = mutableListOf(copy("A.mkv"))
        val a = flow(); a.start(K, "Lexar")
        assertEquals(Step.CLOSED, a.press(Button.CANCEL).step)
        assertFalse("flush" in host.log)
        host.log.clear()
        val b = flow(); b.start(K, "Lexar"); b.press(Button.WAIT)
        assertEquals(Step.CLOSED, b.press(Button.CANCEL).step)
        assertEquals("unfence", host.log.last())
        host.log.clear()
        val c = flow(); c.start(K, "Lexar"); c.press(Button.PAUSE)
        assertEquals(Step.STOPPING, c.view.step)
        assertEquals(Step.STOPPING, c.press(Button.CANCEL).step, "cancel is not offered while the copies are being stopped: the stop ends first")
    }

    // ---- hygiene ----

    @Test fun `a button the step does not offer does nothing`() {
        host.writes = mutableListOf(copy("A.mkv"))
        val f = flow(); f.start(K, "Lexar")
        for (b in listOf(Button.RETRY, Button.RESUME, Button.PAUSE_NOW, Button.CLOSE, Button.SETTINGS)) assertEquals(Step.ASK, f.press(b).step, "$b")
        assertEquals(emptyList(), host.log)
        host.writes = mutableListOf()
        val g = flow(); g.start(K, "Lexar")                    // READY
        for (b in listOf(Button.WAIT, Button.PAUSE, Button.PAUSE_NOW, Button.RETRY, Button.CANCEL)) assertEquals(Step.READY, g.press(b).step, "$b")
        assertEquals(listOf("fence:stop", "flush"), host.log, "no second flush, no second fence")
    }

    @Test fun `finished flows stay finished and ask nothing of the TV`() {
        val f = flow(); f.start(K, "Lexar"); f.press(Button.RESUME)
        val n = host.log.size
        repeat(3) { t += 1_000; assertEquals(Step.CLOSED, f.tick().step) }
        assertEquals(n, host.log.size)
        // a key that left stays left: the fence is lifted once, not at every second (the key is still absent)
        host.present = false
        val g = flow(); g.start(K, "Lexar")
        assertEquals(Step.GONE, g.view.step)
        val m = host.log.size
        repeat(3) { t += 1_000; assertEquals(Step.GONE, g.tick().step) }
        assertEquals(m, host.log.size)
        // and so does an expired one, whatever happens to the key afterwards
        host.present = true; host.log.clear()
        val h = flow(); h.start(K, "Lexar"); t += UsbSafeRemoval.READY_HOLD_MS; assertEquals(Step.EXPIRED, h.tick().step)
        host.present = false
        val e = host.log.size
        repeat(3) { t += 1_000; assertEquals(Step.EXPIRED, h.tick().step) }
        assertEquals(e, host.log.size)
        // and a closed one
        host.present = true; host.log.clear()
        val c = flow(); c.start(K, "Lexar"); c.press(Button.RESUME)
        host.present = false
        val n2 = host.log.size
        repeat(3) { t += 1_000; assertEquals(Step.CLOSED, c.tick().step) }
        assertEquals(n2, host.log.size)
    }

    @Test fun `the delays are the documented ones`() {
        assertEquals(20_000L, UsbSafeRemoval.STOP_TIMEOUT_MS, "a copy that has not let go of the key 20 s after the stop order")
        assertEquals(10 * 60_000L, UsbSafeRemoval.READY_HOLD_MS, "a prepared key that stays plugged gets its copies back after 10 minutes")
    }

    @Test fun `the words are French, name no path and no internal word`() {
        host.writes = mutableListOf(copy("Film.mkv"))
        val f = flow()
        val all = ArrayList<UsbSafeRemoval.View>()
        all += f.start(K, "Lexar"); all += f.press(Button.PAUSE); host.writes = mutableListOf(); all += f.tick()
        host.present = false; all += f.tick()
        for (v in all) for (l in v.lines + v.title + v.buttons.map { it.label }) {
            assertFalse("/storage" in l || "usb-" in l || "A379" in l, l)
            assertFalse("sender" in l.lowercase() || "receiver" in l.lowercase() || "fence" in l.lowercase(), l)
        }
    }

    @Test fun `the button labels are the ones of the screen`() {
        assertEquals("Attendre la fin de la copie", Button.WAIT.label)
        assertEquals("Mettre en pause et préparer le retrait", Button.PAUSE.label)
        assertEquals("Mettre en pause maintenant", Button.PAUSE_NOW.label)
        assertEquals("Ouvrir les réglages de stockage", Button.SETTINGS.label)
        assertEquals("Reprendre l'utilisation de la clé", Button.RESUME.label)
        assertEquals("Réessayer", Button.RETRY.label)
        assertEquals("Annuler", Button.CANCEL.label)
        assertEquals("Fermer", Button.CLOSE.label)
    }
}
