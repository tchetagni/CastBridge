package castbridge.core.tv.home

import kotlin.test.*

class HomeDashboardTest {
    private fun ids(entries: List<HomeEntry>) = entries.flatMap { when (it) {
        is HomeEntry.Direct -> listOf(it.id)
        is HomeEntry.Group -> it.tiles.map { t -> t.id }
    } }

    @Test fun allAuthorisedFeaturesRemainReachableExactlyOnce() {
        val source = (HomeGroups.GROUPS.flatMap { it.tileIds } + HomeGroups.QUICK_ACCESS + "future_tool").distinct()
        val entries = HomeDashboard.layout(source.map { HomeTileInfo(it, it) })
        assertEquals(source.toSet(), ids(entries).toSet())
        assertEquals(source.size, ids(entries).size)
        assertEquals(listOf("library", HomeDashboard.RECEIVE, HomeGroups.LEARN, HomeGroups.GAMES, HomeDashboard.MORE), entries.map { it.id })
    }

    @Test fun childModeDoesNotReintroduceHiddenTools() {
        val entries = HomeDashboard.layout(listOf(HomeTileInfo("learn", "Apprendre")))
        assertEquals(listOf("learn"), ids(entries))
        assertTrue(entries.none { it.id == HomeDashboard.RECEIVE || it.id == HomeDashboard.MORE })
        assertTrue(HomeDashboard.layout(emptyList()).isEmpty())
    }

    @Test fun receiveGridReturnsToItsOwnButton() {
        val entries = HomeDashboard.layout(listOf("library", "receive", "pair", "pin").map { HomeTileInfo(it, it) })
        val open = HomeNav.press(HomeNavState.Closed(), entries, HomeKey.OK, HomeDashboard.RECEIVE)
        assertIs<HomeNavState.Open>(open.state)
        assertEquals(HomeAction.FocusHome(HomeDashboard.RECEIVE), HomeNav.press(open.state, entries, HomeKey.BACK).action)
    }
}
