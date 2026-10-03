package castbridge.core.quiz.online

import castbridge.core.quiz.EmbeddedQuestionSource
import kotlin.test.*

/**
 * Correctifs de l'audit Opus de w20-03 qui touchent le cœur : B3 (mémoire du compteur de codes faux), I2 (compensation RTT non gonflable),
 * I3 (graine tirée par le serveur), I4 (reprise : blocage d'IP, jamais de rotation du code), I5 (sièges, bannissement, appareil obligatoire).
 */
class AuditCoreTest {
    private val bank = EmbeddedQuestionSource().bank()
    private fun room(scope: PlayScope = PlayScope.INTERNET, seed: Long = 42) = ServerRoom("r1", scope, bank, java.util.Random(seed), createdAt = 0, settings = ServerRoom.Settings(duelCount = 10))
    private inline fun <reified T : ServerMsg> List<ServerRoom.Out>.one(): T = map { it.msg }.filterIsInstance<T>().first()
    private fun List<ServerRoom.Out>.error(): String? = map { it.msg }.filterIsInstance<ServerMsg.Error>().firstOrNull()?.reason
    private fun ServerRoom.host() = handle("tv", ClientMsg.Create(null, "DUEL"), 0).one<ServerMsg.Welcome>()
    private fun ServerRoom.join(conn: String, name: String, device: String? = "dev-$conn-0000", spectate: Boolean = false, ip: String? = null, now: Long = 0) =
        handle(conn, ClientMsg.Join(code, name, null, device, spectate), now, ip)

    // ---- B3 : le compteur de codes faux ne grandit pas sans borne ----

    @Test fun badCodeCounterStaysUnderItsHardCapWhateverTheNumberOfAddresses() {
        val c = BadCodeCounter()
        repeat(1_000_000) { c.ipFail("ip-$it", it.toLong()) }
        assertTrue(c.size() <= 50_000, "taille ${c.size()} au-dessus du plafond")
        assertFalse(c.ipBlocked("ip-999999", 1_000_000), "une seule faute : pas bloqué")
    }

    @Test fun expiredEntriesAreSweptAndLookupsNeverCreateEntries() {
        val c = BadCodeCounter()
        repeat(100) { c.ipFail("a$it", 0) }
        repeat(1_000) { assertFalse(c.ipBlocked("b$it", 10)) }
        assertEquals(100, c.size(), "interroger ne crée rien")
        c.sweep(PlayProtocol.BAD_CODE_WINDOW_MS + 1)
        assertEquals(0, c.size(), "après la fenêtre de 5 min, le balayage retire les files vides")
        repeat(PlayProtocol.MAX_BAD_CODES_PER_IP) { c.ipFail("x", 0) }
        assertTrue(c.ipBlocked("x", 1)); assertFalse(c.ipBlocked("x", PlayProtocol.BAD_CODE_WINDOW_MS + 1)); assertEquals(0, c.size(), "une file vidée par le temps est retirée")
    }

    // ---- I2 : le RTT sert au minimum des 8 derniers échantillons, plafonné ----

    @Test fun delayedPongsCannotInflateTheCompensation() {
        val b = RttBook()
        repeat(8) { b.sample("slow", 1_900) }
        assertEquals(1_900, b.rtt("slow"), "le RTT lui-même reste la plus petite des 8 dernières mesures")
        assertEquals(100, b.compensationMs("slow"), "un client qui retarde ses pongs ne gagne au plus que 100 ms (plafond 200 ms de RTT)")
        assertEquals(9_900, RttBook.elapsed(10_000, 1_900))
        assertEquals(9_900, RttBook.elapsed(10_000, 600), "RTT 600 : plafonné à 200 ⇒ 100 ms rendus")
        assertEquals(9_950, RttBook.elapsed(10_000, 100))
    }

    @Test fun oneGoodSampleAmongTheLastEightGivesTheMinimumAndOldSamplesLeave() {
        val b = RttBook()
        repeat(7) { b.sample("c", 1_500) }; b.sample("c", 40)
        assertEquals(40, b.rtt("c"))
        repeat(8) { b.sample("c", 1_500) }
        assertEquals(1_500, b.rtt("c"), "le bon échantillon est sorti de la fenêtre de 8")
        assertEquals(0, b.rtt("inconnu"))
    }

    @Test fun relayTermIsBoundedByFourHundredMilliseconds() {
        assertEquals(9_600, RttBook.relayed(9_000, 10_000, 1_900), "la TV ne peut pas annuler plus de 400 ms : max(9000, 10000 − 400)")
        assertEquals(9_900, RttBook.relayed(9_900, 10_000, 300))
        assertEquals(9_700, RttBook.relayed(9_000, 10_000, 300))
    }

    // ---- I3 : le serveur tire la graine en Internet ----

    @Test fun internetIgnoresTheClientSeedButLanKeepsIt() {
        fun ids(scope: PlayScope, seed: String): List<String> {
            val r = room(scope); r.host(); r.join("a", "Awa"); r.join("b", "Bello")
            r.handle("tv", ClientMsg.Act(null, "start", null, seed, 1), 0)
            return r.table(0).room.duel!!.questions.map { it.id }
        }
        assertEquals(ids(PlayScope.INTERNET, "5"), ids(PlayScope.INTERNET, "999"), "graine du client ignorée : seul le générateur du serveur compte")
        assertNotEquals(ids(PlayScope.LAN, "5"), ids(PlayScope.LAN, "999"), "LAN : comportement inchangé")
        assertEquals(ids(PlayScope.LAN, "5"), ids(PlayScope.LAN, "5"))
    }

    // ---- I4 : la reprise est protégée comme l'entrée ----

    @Test fun fiftyWrongResumesNeverRotateTheRoomCode() {
        val r = room(); val h = r.host(); r.join("s0", "Voyeur", spectate = true)
        val before = r.code
        repeat(60) { i -> r.handle("x$i", ClientMsg.Resume(h.roomId, "token-faux-%020d".format(i), 0), 100L + i, "10.0.$i.1") }
        assertEquals(before, r.code, "seuls des `join` ratés font tourner le code, jamais des `resume` ratés")
    }

    @Test fun aPlayerAlreadyInGameResumesWithItsTokenEvenIfItsAddressHasManyBadCodes() {
        val r = room(); val h = r.host()
        val w = r.join("a", "Awa", ip = "198.51.100.9").one<ServerMsg.Welcome>()
        assertEquals(30, PlayProtocol.MAX_BAD_CODES_PER_IP, "seuil derrière un NAT collectif : ≈ 30 codes faux par adresse et par 5 min")
        repeat(30) { r.handle("z$it", ClientMsg.Join("ZZZZZZZZ", "X", null, "dev-z-$it-aaaa", false), 10, "198.51.100.9") }
        assertEquals(PlayReason.PLAY_BAD_CODE.name, r.join("late", "Neuf", ip = "198.51.100.9", now = 20).error(), "les CODES faux de join bloquent l'adresse")
        assertNotNull(r.handle("n", ClientMsg.Resume(h.roomId, w.token, 0), 20, "198.51.100.9").map { it.msg }.filterIsInstance<ServerMsg.Welcome>().firstOrNull(), "mais un joueur assis reprend avec son jeton : jamais de blocage collectif d'un resume")
    }

    @Test fun wrongResumesAreNeverCountedAgainstTheAddress() {
        val r = room(); val h = r.host()
        repeat(200) { r.handle("x$it", ClientMsg.Resume(h.roomId, "token-faux-%020d".format(it), 0), 10, "198.51.100.7") }
        assertNotNull(r.join("ok", "Awa", ip = "198.51.100.7", now = 20).map { it.msg }.filterIsInstance<ServerMsg.Welcome>().firstOrNull(), "200 resume faux n'empêchent pas un join avec le bon code")
    }

    @Test fun aSeatedConnectionSendingWrongJoinsNeverRotatesTheCode() {
        val r = room(); r.host(); r.join("s", "Voyeur", spectate = true)
        val before = r.code
        repeat(60) { i -> assertEquals(PlayProtocol.FORBIDDEN, r.handle("s", ClientMsg.Join("ZZZZ%04d".format(i), "X", null, dv(), false), 100L + i, "10.1.1.1").error(), "déjà assis : refus sans compter") }
        assertEquals(before, r.code, "le code de salle ne tourne pas")
        r.join("p", "Awa").one<ServerMsg.Welcome>()
        repeat(60) { i -> r.handle("p", ClientMsg.Join("ZZZZ%04d".format(i), "X", null, dv(), false), 200L + i, "10.1.1.2") }
        assertEquals(before, r.code, "un joueur assis non plus")
        assertNotNull(r.handle("tv", ClientMsg.Join(r.code, "Voisin", null, dv(), false), 300).map { it.msg }.filterIsInstance<ServerMsg.Welcome>().firstOrNull(), "l'hôte enregistre toujours un joueur local relayé")
    }

    // ---- I5 : appareil obligatoire, bannissement par appareil ET adresse, purge des spectateurs ----

    @Test fun internetRefusesAJoinWithoutADeviceButLanDoesNot() {
        val r = room(); r.host()
        assertEquals(PlayProtocol.BAD_REQUEST, r.join("a", "Awa", device = null).error())
        assertEquals(PlayProtocol.BAD_REQUEST, r.join("b", "Bello", device = "court").error(), "identifiant trop court")
        assertNotNull(r.join("c", "Carine", device = "device-carine-01").one<ServerMsg.Welcome>())
        val lan = room(PlayScope.LAN); lan.host()
        assertEquals(PlayRole.PLAYER, lan.join("a", "Awa", device = null).one<ServerMsg.Welcome>().role)
    }

    @Test fun kickBansTheDeviceAndTheAddress() {
        val r = room(); r.host()
        val w = r.join("a", "Awa", device = "device-awa-0001", ip = "203.0.113.5").one<ServerMsg.Welcome>()
        r.handle("tv", ClientMsg.Kick(w.playerId!!), 5)
        assertEquals(PlayReason.PLAY_BANNED.name, r.join("b", "Autre", device = "device-neuf-0002", ip = "203.0.113.5").error(), "même adresse, autre appareil : banni")
        assertEquals(PlayReason.PLAY_BANNED.name, r.join("c", "Awa2", device = "device-awa-0001", ip = "203.0.113.77").error(), "même appareil, autre adresse : banni")
        assertNotNull(r.join("d", "Dora", device = "device-dora-0003", ip = "203.0.113.78").one<ServerMsg.Welcome>(), "un autre joueur entre")
    }

    @Test fun disconnectedSpectatorsAreEventuallyPurged() {
        val r = room(); val h = r.host()
        val s = r.join("s", "Voyeur", spectate = true, now = 1_000).one<ServerMsg.Welcome>()
        assertEquals(1, r.spectatorCount())
        r.disconnect("s", 2_000)
        r.tick(2_000 + 60_000); assertEquals(1, r.spectatorCount(), "encore gardé une minute après (reprise possible)")
        r.tick(2_000 + ServerRoom.SPECTATOR_PURGE_MS + 1)
        assertEquals(0, r.spectatorCount(), "purgé après le délai")
        assertEquals(PlayReason.PLAY_BAD_CODE.name, r.handle("s2", ClientMsg.Resume(h.roomId, s.token, 0), 3_000_000).error())
    }
}
