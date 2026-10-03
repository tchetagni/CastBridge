package castbridge.core.owner

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Classement des routes de la Boutique en essai (w17-04) : la vitrine se voit, les demandes sont fermées. */
class TrialRoutesStoreTest {
    private fun repoFile(rel: String): java.io.File {
        var d: java.io.File? = java.io.File("").absoluteFile
        while (d != null && !java.io.File(d, rel).exists()) d = d.parentFile
        return java.io.File(d ?: error("repo root not found"), rel)
    }

    @Test fun theShowcaseAndTheCatalogReceptionAreOpenInTrial() {
        assertTrue(TrialPolicy.routeAllowed("/api/store")); assertTrue(TrialPolicy.routeAllowed("/api/store/catalog"))
    }

    @Test fun requestsAndAcknowledgementsAreClosedInTrial() {
        for (p in listOf("/api/store/requests", "/api/store/requests/ack", "/api/store/request")) assertFalse(TrialPolicy.routeAllowed(p), p)
    }

    @Test fun traversalAndLookalikesStayClosed() {
        for (p in listOf("/api/store/../ssh", "/api/store//catalog", "/api/storex", "/api/storage", "/api/store/%72equests", "/api/storexyz/catalog")) assertFalse(TrialPolicy.routeAllowed(p), p)
    }

    @Test fun exactlyFiveStoreRoutesAreClassifiedInTheRouteTable() {
        val lines = repoFile("tools/routes/routes.txt").readLines().map { it.trim() }.filter { it.startsWith("/api/store") }
        assertEquals(listOf("/api/store", "/api/store/catalog", "/api/store/request", "/api/store/requests", "/api/store/requests/ack"), lines)
    }
}
