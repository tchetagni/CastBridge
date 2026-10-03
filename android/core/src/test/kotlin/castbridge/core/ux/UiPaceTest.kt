package castbridge.core.ux

import kotlin.test.*

/**
 * Fluidité de CastBridge-TV (docs/agent-reports/tv-perf.md, R-11) : l'accueil ne redessine plus l'écran entier 60 fois par seconde au repos,
 * la ligne de réception n'est repeinte que lorsqu'elle change et au plus une fois par seconde, le fond du Quiz ne dépasse pas ~12 images/s.
 * Horloges factices : aucun temps réel.
 */
class UiPaceTest {
    @Test fun `throttle laisse passer le premier appel puis donne le delai du suivant`() {
        val t = Throttle(1000)
        assertEquals(0, t.admit(10_000))            // premier : tout de suite
        assertEquals(600, t.admit(10_400))          // 400 ms plus tard : attendre 600 ms
        assertEquals(1, t.admit(10_999))
        assertEquals(0, t.admit(11_000))            // l'intervalle est écoulé
        assertEquals(1000, t.admit(11_000))         // deux fois au même instant : la seconde attend
    }

    @Test fun `throttle borne a une execution par intervalle sur une rafale`() {
        val t = Throttle(250)
        var runs = 0
        for (ms in 0L until 1000L step 10) if (t.admit(ms) == 0L) runs++
        assertEquals(4, runs)                       // 100 évènements en 1 s -> 4 repeints (≤ 4 Hz)
    }

    @Test fun `throttle refuse un intervalle negatif`() {
        assertFailsWith<IllegalArgumentException> { Throttle(-1) }
    }

    @Test fun `state gate ne signale que les changements`() {
        val g = StateGate<String>()
        assertTrue(g.changed("● Prêt à recevoir"))
        assertFalse(g.changed("● Prêt à recevoir"))
        assertTrue(g.changed("⬇ Réception de Film : 42 %"))
        assertFalse(g.changed("⬇ Réception de Film : 42 %"))
        assertTrue(g.changed("● Prêt à recevoir"))
        g.reset()
        assertTrue(g.changed("● Prêt à recevoir"))  // après reset (écran reconstruit), on repeint
    }

    @Test fun `zoom lent va de 1 a 1,12 en 30 s puis revient`() {
        assertEquals(1f, SlowZoomCurve.scaleAt(0), 1e-4f)
        assertEquals(1.06f, SlowZoomCurve.scaleAt(15_000), 1e-4f)
        assertEquals(1.12f, SlowZoomCurve.scaleAt(30_000), 1e-4f)
        assertEquals(1.06f, SlowZoomCurve.scaleAt(45_000), 1e-4f)
        assertEquals(1f, SlowZoomCurve.scaleAt(60_000), 1e-4f)
        assertEquals(SlowZoomCurve.scaleAt(7_000), SlowZoomCurve.scaleAt(67_000), 1e-4f)
        assertEquals(1f, SlowZoomCurve.scaleAt(-5), 1e-4f)
    }

    @Test fun `zoom lent avance par pas de 4 images par seconde au plus et chaque pas reste invisible`() {
        assertTrue(SlowZoomCurve.STEP_MS >= 250)
        // un pas = 0,12 / 30 000 ms × STEP_MS : à 1280 px, moins de 2 px de déplacement
        val step = SlowZoomCurve.scaleAt(SlowZoomCurve.STEP_MS) - SlowZoomCurve.scaleAt(0)
        assertTrue(step * 1280 < 2f, "pas trop visible : ${step * 1280} px")
    }

    @Test fun `fond du quiz au plus 12 images par seconde, plus lent en question, flash fluide`() {
        assertTrue(StagePace.delayMs(calm = false, flashing = false) >= 80)
        assertTrue(StagePace.delayMs(calm = true, flashing = false) > StagePace.delayMs(calm = false, flashing = false))
        assertTrue(StagePace.delayMs(calm = false, flashing = true) <= 40)       // l'éclair (bonne / mauvaise réponse) reste fluide 0,9 s
    }
}
