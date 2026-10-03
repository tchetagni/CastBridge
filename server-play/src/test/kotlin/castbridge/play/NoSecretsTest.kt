package castbridge.play

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Le service démarre sans base, sans jeton d'administration, sans clé privée : il ne lit aucune variable `*_KEY` / `*_TOKEN` sauf `CASTBRIDGE_PLAY_TICKET_PUBKEY*`. */
class NoSecretsTest {
    private val secretLike = Regex("(_KEY|_TOKEN|_SECRET|_PASSWORD|_PASS|_PRIVATE.*)$", RegexOption.IGNORE_CASE)

    @Test fun configurationReadsOnlyTheDocumentedVariables() {
        val read = ArrayList<String>()
        val cfg = PlayConfig.fromEnv({ k -> read += k; null })
        assertTrue(read.isNotEmpty())
        assertEquals(PlayConfig.ENV_NAMES.toSet(), read.toSet(), "exactement les variables documentées (README)")
        for (n in read) {
            assertTrue(n.startsWith("CASTBRIDGE_PLAY_"), "variable hors espace de noms : $n")
            if (n.startsWith("CASTBRIDGE_PLAY_TICKET_PUBKEY")) continue
            assertFalse(secretLike.containsMatchIn(n), "variable de secret lue : $n")
        }
        assertEquals(8080, cfg.port); assertTrue(cfg.ticketPubKeys.isEmpty())
    }

    @Test fun serviceStartsWithAnEmptyEnvironmentAndRefusesToCreateRooms() {
        val srv = PlayServer(PlayConfig.fromEnv({ null }, arrayOf("--server.port=0"))).start()
        try {
            val r = SseWire.post(srv.port, """{"t":"create","mode":"DUEL"}""", null, "https://bridge.sti-cm.com", null)
            assertEquals(200, r.statusCode())
            assertTrue(srv.rooms().isEmpty(), "aucune clé publique configurée : aucun ticket n'est valide, aucune salle")
        } finally { srv.close() }
    }

    @Test fun onlyPlayConfigTouchesTheEnvironment() {
        val dir = File("src/main/kotlin/castbridge/play")
        assertTrue(dir.isDirectory, "lancé depuis ${File(".").absolutePath}")
        val users = dir.listFiles { f -> f.name.endsWith(".kt") }!!.filter { Regex("getenv|System\\.getProperty|ProcessBuilder|Runtime\\.getRuntime\\(\\)\\.exec").containsMatchIn(it.readText()) }.map { it.name }
        assertEquals(listOf("PlayConfig.kt"), users.sorted(), "seul PlayConfig lit l'environnement")
        val all = dir.listFiles { f -> f.name.endsWith(".kt") }!!.joinToString("\n") { it.readText() }
        assertFalse(Regex("ADMIN_TOKEN|PRIVATE_KEY|jdbc:|mysql", RegexOption.IGNORE_CASE).containsMatchIn(all), "aucune trace de secret ni de base dans les sources")
    }

    @Test fun ticketPublicKeysAcceptRawAndSpkiAndRejectGarbage() {
        val raw = java.util.Base64.getEncoder().encodeToString(TestKeys.pair.public.encoded.copyOfRange(12, 44))
        for (k in listOf(TestKeys.pub, raw)) assertTrue(TicketVerifier(listOf(k)).verify(TestKeys.ticket(), System.currentTimeMillis()), "clé $k")
        assertFalse(TicketVerifier(listOf("pas une clé", "")).configured)
        assertFalse(TicketVerifier(emptyList()).verify(TestKeys.ticket(), System.currentTimeMillis()))
        val v = TicketVerifier(listOf(TestKeys.pub)); val now = System.currentTimeMillis()
        assertFalse(v.verify(TestKeys.ticket(now = now - 200_000, lifeMs = 60_000), now), "périmé")
        assertFalse(v.verify(TestKeys.ticket(now = now, lifeMs = 3_600_000), now), "durée de vie > 15 min")
        assertFalse(v.verify(TestKeys.ticket(now = now + 600_000), now), "émis dans le futur")
        assertFalse(v.verify(TestKeys.ticket().dropLast(3) + "AAA", now), "signature altérée")
        assertFalse(v.verify(null, now)); assertFalse(v.verify("v1.a.b", now)); assertFalse(v.verify("x".repeat(1_300), now))
    }
}
