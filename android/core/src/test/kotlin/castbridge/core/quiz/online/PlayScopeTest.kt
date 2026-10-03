package castbridge.core.quiz.online

import kotlin.test.*

class PlayScopeTest {
    @Test fun threeScopesWithFrenchLabelsAndIcons() {
        assertEquals(listOf("TV seule", "Réseau local", "Internet"), PlayScope.values().map { it.label })
        assertEquals(listOf("▣", "⌂", "◎"), PlayScope.values().map { it.icon })
        PlayScope.values().forEach { assertTrue(it.promise.isNotBlank()) }
    }

    @Test fun onlyInternetAllowsRemotePlayersAndNetwork() {
        assertFalse(PlayScope.TV_ONLY.allowsRemotePlayers()); assertFalse(PlayScope.LAN.allowsRemotePlayers())
        assertTrue(PlayScope.INTERNET.allowsRemotePlayers())
        assertFalse(PlayScope.TV_ONLY.mayUseNetwork()); assertFalse(PlayScope.LAN.mayUseNetwork())
        assertTrue(PlayScope.INTERNET.mayUseNetwork())
        assertTrue(PlayScope.INTERNET.requiresInternet()); assertFalse(PlayScope.LAN.requiresInternet())
    }
}
