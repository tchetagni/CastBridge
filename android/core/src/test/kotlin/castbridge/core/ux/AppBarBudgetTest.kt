package castbridge.core.ux

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppBarBudgetTest {
    @Test fun everyBarOfTheModuleRespectsTheBudget() {
        val all = UxLabels.BARS.flatMap { AppBarBudget.violations(it) }
        assertTrue(all.isEmpty(), all.joinToString("\n"))
    }

    @Test fun theMainBarKeepsRoomForTheLogo() {
        val main = UxLabels.BARS.first { it.name == "Accueil" }
        assertTrue(main.visible.size <= AppBarBudget.MAX_VISIBLE)
        assertEquals(listOf("Activer la TV", "Demande d'appareil", "Locations", "Contrôle parental"), main.menu)
    }

    @Test fun aFourthFlatActionBreaksTheTest() {
        val bar = AppBarBudget.Bar("X", listOf("a", "b", "c", "d").map { AppBarBudget.Action(it) })
        assertEquals(1, AppBarBudget.violations(bar).size)
    }

    @Test fun aLongTextLabelInTheBarBreaksTheTest() {
        val bar = AppBarBudget.Bar("X", listOf(AppBarBudget.Action(UxLabels.DEVICE_REQUEST, showsText = true)))
        assertEquals(1, AppBarBudget.violations(bar).size)
        // the same label is fine in the menu, or as an icon (its text is not drawn)
        assertTrue(AppBarBudget.violations(AppBarBudget.Bar("X", listOf(AppBarBudget.Action(UxLabels.DEVICE_REQUEST)), listOf(UxLabels.DEVICE_REQUEST))).isEmpty())
    }
}
