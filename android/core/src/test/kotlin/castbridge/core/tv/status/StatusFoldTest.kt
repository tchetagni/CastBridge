package castbridge.core.tv.status

import castbridge.core.tv.PlayerIcons.Place
import kotlin.test.*

/** Barre d'état enroulable (demande du propriétaire du 2026-10-27) : enroulée au départ, pastille = pire niveau + nombre, libellé passager de 4 s, déroulé sur l'accueil seulement, enroulement après 10 s. */
class StatusFoldTest {
    private fun b(id: String, l: StatusLevel) = Badge(id, l, id, "x")
    private val nine = listOf(b("internet", StatusLevel.OK), b("wifi", StatusLevel.OK), b("bluetooth", StatusLevel.OFF), b("storage", StatusLevel.OK), b("phones", StatusLevel.OK),
        b("licence", StatusLevel.WARN), b("quiz", StatusLevel.BUSY), b("copy", StatusLevel.OK), b("tokens", StatusLevel.WARN))

    @Test fun pillShowsTheWorstLevelTheCountAndAShapeSign() {
        val p = StatusFold.pill(nine)
        assertEquals(StatusLevel.WARN, p.level); assertEquals(9, p.count); assertEquals(2, p.urgent); assertEquals("! 9", p.text)
        assertEquals(StatusLevel.ERROR, StatusFold.pill(nine + b("x", StatusLevel.ERROR)).level)
        assertTrue(p.description.contains("2 en alerte"))
    }
    @Test fun severityOrderIsRedOrangeBlueGreyGreenBlack() {
        val order = listOf(StatusLevel.ERROR, StatusLevel.WARN, StatusLevel.BUSY, StatusLevel.UNKNOWN, StatusLevel.OK, StatusLevel.OFF)
        assertEquals(order, order.sortedByDescending { StatusFold.severity(it) })
        assertEquals(StatusLevel.values().size, StatusLevel.values().map { StatusFold.glyph(it) }.toSet().size, "chaque niveau a son signe")
    }
    @Test fun emptyBarIsABlackPillWithoutCount() {
        val p = StatusFold.pill(emptyList()); assertEquals(StatusLevel.OFF, p.level); assertEquals("–", p.text)
    }
    @Test fun transientLabelIsTheMostSevereChangedBadgeForFourSecondsOnly() {
        val ch = mapOf("wifi" to 1_000L, "licence" to 1_500L, "storage" to -10_000L)
        assertEquals("licence", StatusFold.transientLabel(nine, ch, 2_000)?.id)             // l'orange prime sur le vert
        assertEquals("licence", StatusFold.transientLabel(nine, ch, 5_499)?.id)
        assertNull(StatusFold.transientLabel(nine, ch, 5_500))                                 // 4 s après le dernier changement : plus de libellé
        assertNull(StatusFold.transientLabel(nine, emptyMap(), 0), "une alerte qui dure n'a pas de libellé")
        assertEquals(5_500L, StatusFold.labelEndsAt(ch, 2_000)); assertNull(StatusFold.labelEndsAt(ch, 9_000))
    }
    @Test fun startsCollapsedAndOnlyTheHomeCanUnroll() {
        val s = StatusFoldState(); assertFalse(s.expanded)
        for (p in listOf(Place.VIDEO, Place.PANEL, Place.OTHER)) { assertFalse(s.expand(p, 0), p.name); assertFalse(s.expanded) }
        assertTrue(s.expand(Place.HOME, 0)); assertTrue(s.expanded)
    }
    @Test fun okOnThePillTogglesAndLeavingTheHomeRollsUp() {
        val s = StatusFoldState()
        assertTrue(s.toggle(Place.HOME, 0)); assertFalse(s.toggle(Place.HOME, 1))
        s.toggle(Place.HOME, 2); s.placeChanged(Place.HOME); assertTrue(s.expanded)
        s.placeChanged(Place.VIDEO); assertFalse(s.expanded)
        assertFalse(StatusFoldState().expanded, "jamais mémorisé : un nouvel automate est enroulé")
    }
    @Test fun rollsUpByItselfAfterTenSecondsWithoutAKey() {
        val s = StatusFoldState(); s.expand(Place.HOME, 1_000)
        assertFalse(s.tick(10_999)); assertEquals(1L, s.remainingMs(10_999))
        s.key(6_000)                                                                        // une touche repousse
        assertFalse(s.tick(15_999)); assertTrue(s.tick(16_000)); assertFalse(s.expanded); assertNull(s.remainingMs(16_000))
        assertFalse(s.tick(99_000))
    }
}
