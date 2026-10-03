package castbridge.core.trust

import castbridge.core.trust.CredentialDecision.Cause
import castbridge.core.trust.CredentialDecision.Choice
import castbridge.core.trust.CredentialDecision.Facts
import castbridge.core.tv.BtProtocol
import kotlin.test.*

/**
 * R-10 « le PIN d'une TV qui contrôle est à ressaisir à chaque ouverture » : le carnet de codes du téléphone (une fiche par TV à identifiant stable,
 * alias, migration des anciennes clés) et la décision « jeton / code gardé / attendre / demander le code (avec la cause) ». Aucun vrai code ici.
 */
class PinBookTest {
    private val A = "AA:BB:CC:DD:EE:01"
    private val B = "AA:BB:CC:DD:EE:02"
    private val ID1 = "0123456789abcdef0123456789abcdef"
    private val ID2 = "fedcba9876543210fedcba9876543210"
    private val tvA = SavedTv(A, "Salon", "CastBridge TV Salon", listOf("192.168.1.5"), installId = ID1)
    private val tvB = SavedTv(B, "Chambre", "CastBridge TV Chambre", listOf("192.168.1.7"), installId = ID2)
    private val kv = MemoryPinKv()
    private val book = PinBook(kv)

    private fun scope(vararg tvs: SavedTv, token: Boolean = false, tunnelTv: String? = null) =
        PinScope(tvs.toList(), tvs.firstOrNull(), tunnelPort = 18765, tunnelTv = tunnelTv, hasToken = { token })

    // ------------------------------------------------------------------------------------------------ changement d'IP (DHCP)

    @Test fun trustedTvWithTokenKeepsTheTypedPinAcrossAnIpChange() {
        // le code tapé sur un écran qui désigne la TV par son IP ; elle avait un jeton : l'ancien code n'écrivait RIEN (PinKeys.writeKeys)
        book.write("192.168.1.5:8765", "111111", scope(tvA, token = true))
        assertEquals("111111", book.read("192.168.1.5:8765", scope(tvA)), "jeton expiré à la session suivante : le code gardé sert")
        val moved = tvA.copy(lastIps = listOf("192.168.1.9"))
        assertEquals("111111", book.read("192.168.1.9:8765", scope(moved)), "nouvelle IP (même TV, même adresse Bluetooth) : même fiche")
        assertEquals("111111", book.read("Salon", scope(moved)))
        assertNull(kv.map["192.168.1.5:8765"], "toujours rien sous une IP pour une TV à jeton (une IP périmée donnerait ce code à une autre TV)")
    }

    @Test fun pinOnlyTvFoundFromEveryScreenKeyOnceItsHostIsLinked() {
        val s = scope()
        book.write("CastBridge TV M1", "222222", s)                                 // accueil : la clé est le nom mDNS
        assertTrue(book.link("CastBridge TV M1", "192.168.1.20:8765", s))          // la découverte a vu ce nom à cette adresse, la TV a accepté le code
        val n = "CastBridge TV M1"
        assertEquals("222222", book.read("192.168.1.20:8765", s, seenName = n), "télécommande / écran avancé (clé hôte:port, nom vu à cette adresse)")
        assertEquals("222222", book.read("192.168.1.20", s, seenName = n), "IP sans port")
        assertEquals("222222", book.read("http://192.168.1.20:8765", s, seenName = n), "URL")
        assertTrue(book.link("CastBridge TV M1", "192.168.1.33:8765", s), "DHCP : nouvelle adresse")
        assertEquals("222222", book.read("192.168.1.33:8765", s, seenName = n))
    }

    @Test fun aPinTypedOnAHostKeyFollowsTheNameWhenLinked() {
        val s = scope()
        book.write("192.168.1.20:8765", "333333", s)
        book.link("CastBridge TV M1", "192.168.1.20:8765", s)
        assertEquals("333333", book.read("CastBridge TV M1", s))
    }

    // ------------------------------------------------------------------------------------------------ deux TV

    @Test fun twoTrustedTvsNeverShareACode() {
        val s = scope(tvA, tvB)
        book.write("Salon", "111111", s)
        book.write("192.168.1.7:8765", "444444", s)
        assertEquals("111111", book.read("192.168.1.5", s))
        assertEquals("444444", book.read("Chambre", s))
        assertEquals("444444", book.read("bt:$B", s))
        assertNotEquals(book.tvId("Salon", s), book.tvId("Chambre", s))
    }

    @Test fun twoPinOnlyTvsOfTheSameModelKeepTheirOwnCode() {
        val s = scope()
        book.write("CastBridge TV M1", "111111", s)
        book.write("CastBridge TV M1 (2)", "555555", s)
        assertEquals("111111", book.read("CastBridge TV M1", s))
        assertEquals("555555", book.read("CastBridge TV M1 (2)", s), "« (2) » est une autre TV : jamais confondues")
        assertFalse(book.link("CastBridge TV M1", "CastBridge TV M1 (2)", s), "un nom « … (n) » n'est jamais un alias : c'est peut-être l'autre TV")
        assertEquals("555555", book.read("CastBridge TV M1 (2)", s))
    }

    // ------------------------------------------------------------------------------------------------ audit : alias (mutations « écriture à travers un alias »)

    @Test fun aWriteThroughAnAliasNeverOverwritesTheLinkedTv() {
        val s = scope()
        book.write("CastBridge TV M1", "111111", s)
        book.link("CastBridge TV M1", "192.168.1.20:8765", s)
        // le DHCP a redonné 192.168.1.20 à une autre TV : le code tapé pour elle sur l'écran avancé
        book.write("192.168.1.20:8765", "555555", s)
        assertEquals("111111", book.read("CastBridge TV M1", s), "la fiche de la TV liée n'est jamais écrasée (plus de ping-pong)")
        assertEquals("555555", book.read("192.168.1.20:8765", s), "le code va sous la forme propre de l'adresse")
        assertEquals("555555", book.read("192.168.1.20:8765", s, seenName = "CastBridge TV M1"), "l'alias est délié par cette écriture")
    }

    @Test fun anAddressAliasSendsACodeOnlyWhenTheBaseNameAgrees() {
        val s = scope()
        book.write("CastBridge TV M1", "111111", s)
        book.link("CastBridge TV M1", "192.168.1.20:8765", s)
        assertEquals("", book.read("192.168.1.20:8765", s), "l'adresse seule ne suffit pas à envoyer le code")
        assertEquals("", book.read("192.168.1.20:8765", s, seenName = "CastBridge TV X9"), "autre nom à cette adresse : rien")
        assertEquals("111111", book.read("192.168.1.20:8765", s, seenName = "CastBridge TV M1"))
    }

    // ------------------------------------------------------------------------------------------------ audit : lecture pure, migration à l'initialisation

    @Test fun readNeverWritesAndMigrationRunsOnceAtStartup() {
        val writes = ArrayList<Map<String, String>>()
        val counting = object : PinKv by kv { override fun write(put: Map<String, String>, remove: Collection<String>): Boolean { writes += put; return kv.write(put, remove) } }
        val b2 = PinBook(counting)
        kv.map["CastBridge TV M1"] = "123456"
        kv.map["192.168.1.5"] = "654321"
        assertEquals("123456", b2.read("CastBridge TV M1", scope(tvA)))
        assertEquals(CredentialDecision.Choice.UsePin("123456"), CredentialDecision.decide(Facts(storedPin = b2.read("CastBridge TV M1", scope(tvA)))))
        assertTrue(writes.isEmpty(), "une lecture (donc la décision d'un écran en composition) n'écrit jamais : $writes")
        assertEquals(2, b2.migrateAll(scope(tvA)))
        assertEquals("654321", kv.map["id:bt:$A"]); assertEquals("123456", kv.map["id:name:castbridge tv m1"])
        assertEquals(0, b2.migrateAll(scope(tvA)), "une seule fois")
    }

    @Test fun aLockIsRememberedForThePinField() {
        var now = 1_000L
        val m = LockMemo { now }
        m.locked("name:castbridge tv m1", 42)
        assertEquals(42L, m.left("name:castbridge tv m1"))
        now += 40_000; assertEquals(2L, m.left("name:castbridge tv m1"))
        now += 5_000; assertNull(m.left("name:castbridge tv m1"))
        assertNull(m.left("name:autre"))
    }

    @Test fun aLongReconnectionStopsSayingNoCodeIsNeeded() {
        val d = CredentialDecision.decide(Facts(trustedTv = true, linkPending = true, pendingForMs = 25_000))
        assertTrue(d is Choice.AskPin && d.cause == Cause.TV_OUT_OF_REACH && d.text.contains("allumez-la"), "après 20 s : cause et geste ($d)")
        assertTrue(CredentialDecision.decide(Facts(trustedTv = true, linkPending = true, pendingForMs = 5_000)) is Choice.Wait)
    }

    @Test fun aRefusedCodeUnlinksTheHostAliasInsteadOfServingAnotherTv() {
        val s = scope()
        book.write("CastBridge TV M1", "111111", s)
        book.link("CastBridge TV M1", "192.168.1.20:8765", s)
        // le DHCP a donné 192.168.1.20 à une autre TV, qui refuse ce code
        book.refused("192.168.1.20:8765", "111111", s)
        assertEquals("", book.read("192.168.1.20:8765", s), "l'alias d'hôte est retiré")
        assertEquals("111111", book.read("CastBridge TV M1", s), "le code de la TV par son nom reste bon")
    }

    // ------------------------------------------------------------------------------------------------ jeton expiré, code changé, TV réinitialisée

    @Test fun tokenExpiredButPinStoredUsesThePin() {
        assertEquals(Choice.UsePin("111111"), CredentialDecision.decide(Facts(token = null, trustedTv = true, storedPin = "111111")))
    }

    @Test fun pinRotatedOnTheTvIsAskedOnceWithItsCauseAndNeverResent() {
        val s = scope()
        book.write("CastBridge TV M1", "111111", s)
        book.refused("CastBridge TV M1", "111111", s)
        assertTrue(book.isRefused("CastBridge TV M1", s))
        assertEquals("", book.read("CastBridge TV M1", s), "un code refusé n'est plus présenté (verrouillage progressif)")
        assertTrue(kv.map.values.contains("111111"), "jamais effacé sur un refus (w13-08)")
        val d = CredentialDecision.decide(Facts(storedPin = "111111", pinRefused = true))
        assertEquals(Cause.PIN_REFUSED, (d as Choice.AskPin).cause)
        book.write("CastBridge TV M1", "666666", s)
        assertFalse(book.isRefused("CastBridge TV M1", s), "un nouveau code tapé lève le blocage")
        assertEquals("666666", book.read("CastBridge TV M1", s))
    }

    @Test fun tvResetMakesTheStoredPinStaleAndTheCauseIsSaid() {
        book.write("Salon", "111111", scope(tvA))
        val reset = tvA.copy(installId = ID2)                   // nouvelle installation vue au HELLO : nouveau code sur la TV
        assertTrue(book.tvReset("Salon", scope(reset)))
        assertEquals("", book.read("Salon", scope(reset)))
        assertEquals(Cause.TV_RESET, (CredentialDecision.decide(Facts(trustedTv = true, storedPin = "", tvReset = true)) as Choice.AskPin).cause)
        val lf = CredentialDecision.linkFacts(LinkState.TvForgotMe(BtProtocol.HINT_OTHER_INSTALL))
        assertTrue(lf.tvReset); assertFalse(lf.phoneRemoved)
        assertTrue(CredentialDecision.linkFacts(LinkState.TvForgotMe(BtProtocol.HINT_SAME_INSTALL)).phoneRemoved)
    }

    @Test fun anOlderTvWithoutInstallIdIsNeverCalledReset() {
        book.write("Salon", "111111", scope(tvA.copy(installId = null)))
        assertFalse(book.tvReset("Salon", scope(tvA)))
        assertEquals("111111", book.read("Salon", scope(tvA)))
    }

    // ------------------------------------------------------------------------------------------------ course du premier lancement

    @Test fun firstLaunchRaceWaitsForTheLinkInsteadOfAskingThePin() {
        val lf = CredentialDecision.linkFacts(null)               // le processus vient de démarrer : aucun HELLO encore
        assertTrue(lf.pending)
        assertTrue(CredentialDecision.linkFacts(LinkState.Connecting).pending)
        val d = CredentialDecision.decide(Facts(trustedTv = true, linkPending = lf.pending))
        assertTrue(d is Choice.Wait, "la TV de confiance se reconnecte seule : aucun code demandé ($d)")
        assertEquals(Cause.TOKEN_EXPIRED, (CredentialDecision.decide(Facts(trustedTv = true, linkPending = false)) as Choice.AskPin).cause)
    }

    @Test fun r01SecondHalfIsKept() {
        // jeton refusé par une TV de confiance : aucun code présenté, on attend le nouveau HELLO (PinFallback.choose)
        assertEquals(Choice.Wait(PinFallback.REFUSED), CredentialDecision.decide(Facts(trustedTv = true, tokenRefused = true, storedPin = "111111")))
        assertEquals(Choice.UseToken("cbk_x"), CredentialDecision.decide(Facts(token = "cbk_x", storedPin = "111111")))
    }

    @Test fun lockedIsNotAChangedPin() {
        val k = TvAuthReply.of(401, "HTTP 401: {\"error\":\"locked\",\"retryAfter\":42}")
        assertEquals(TvAuthReply.Kind.Locked(42), k)
        assertEquals(TvAuthReply.Kind.BadPin, TvAuthReply.of(401, "HTTP 401: {\"error\":\"bad pin\"}"))
        assertEquals(TvAuthReply.Kind.BadToken, TvAuthReply.of(401, "HTTP 401: {\"error\":\"bad token\"}"))
        assertEquals(TvAuthReply.Kind.Other, TvAuthReply.of(500, "HTTP 500: x"))
        val d = CredentialDecision.decide(Facts(storedPin = "111111", lockedSec = 42))
        assertEquals(42L, (d as Choice.Wait).retryAfterSec, "trop d'essais : on attend, on ne redemande pas le code")
    }

    // ------------------------------------------------------------------------------------------------ relais (boucle locale du tunnel)

    @Test fun relayLoopbackNeverHandsAStoredCodeToAnUnknownTv() {
        kv.map["127.0.0.1:18765"] = "777777"                    // écrit par une ancienne version sous la boucle locale
        assertEquals("", book.read("127.0.0.1:18765", scope(tvA)), "passerelle arrêtée : la boucle ne désigne aucune TV")
        assertNull(book.tvId("http://127.0.0.1:18765", scope(tvA)))
        assertTrue(book.unknownRelay("http://127.0.0.1:18765", scope(tvA))); assertFalse(book.unknownRelay("Salon", scope(tvA)))
        book.write("127.0.0.1:18765", "888888", scope(tvA))
        assertEquals("777777", kv.map["127.0.0.1:18765"], "rien de neuf écrit sous la boucle")
        book.write("Salon", "111111", scope(tvA))
        assertEquals("111111", book.read("http://127.0.0.1:18765", scope(tvA, tunnelTv = A)), "passerelle connectée à la TV A : la fiche de A")
        assertEquals(Cause.RELAY_UNKNOWN, (CredentialDecision.decide(Facts(relayUnknown = true, storedPin = "777777")) as Choice.AskPin).cause)
    }

    @Test fun relayToAnUnsavedBluetoothTvUsesTheCodeTypedForThatAddress() {
        val x = "AA:BB:CC:DD:EE:42"
        book.write("bt:${x.lowercase()}", "999999", scope())                            // écran Bluetooth : TV appairée mais pas « de confiance »
        assertEquals("bt:$x", book.tvId("bt:$x", scope()), "une adresse Bluetooth est un identifiant stable, quelle que soit la casse")
        assertEquals("999999", book.read("http://127.0.0.1:18765", scope(tunnelTv = x)), "la passerelle atteint cette adresse : même fiche")
        assertEquals("", book.read("http://127.0.0.1:18765", scope(tunnelTv = B)), "passerelle vers une autre TV : pas ce code")
        assertFalse(book.link("bt:$x", "192.168.49.1:8765", scope()), "l'adresse Wi-Fi Direct est la même sur toutes les TV : jamais un alias")
    }

    // ------------------------------------------------------------------------------------------------ migration des anciennes clés

    @Test fun legacyPinsByNameOrIpAreMigratedWithoutLoss() {
        kv.map["CastBridge TV M1"] = "123456"
        kv.map["192.168.1.5"] = "654321"                         // ancienne clé brute (TvScreen.kt) d'une TV sauvegardée
        book.migrateAll(scope(tvA))                              // au démarrage, hors du fil principal
        assertEquals("123456", book.read("CastBridge TV M1", scope()))
        assertEquals("654321", book.read("192.168.1.5:8765", scope(tvA)))
        assertEquals("123456", kv.map["CastBridge TV M1"], "l'ancienne entrée reste (retour arrière possible)")
        // la TV change d'IP : l'ancienne clé ne la désigne plus, la fiche migrée oui
        val moved = tvA.copy(lastIps = listOf("192.168.1.9"))
        assertEquals("654321", book.read("192.168.1.9", scope(moved)))
        assertEquals("654321", book.read("bt:$A", scope(moved)))
    }

    @Test fun aWriteFailureIsReported() {
        val failing = PinBook(object : PinKv { override fun get(key: String): String? = null; override fun write(put: Map<String, String>, remove: Collection<String>) = false })
        assertFalse(failing.write("Salon", "111111", scope(tvA)))
    }

    // ------------------------------------------------------------------------------------------------ retrouver la TV de l'accueil (nom mDNS instable)

    @Test fun homeTvIsFoundAfterItsMdnsNameGotASuffix() {
        val seen = listOf(HomeTvMatch.Seen("CastBridge TV M1 (2)", "192.168.1.20", 8765), HomeTvMatch.Seen("CastBridge TV X9", "192.168.1.21", 8765))
        assertEquals(HomeTvMatch.How.NAME_AND_HOST, HomeTvMatch.pick("CastBridge TV X9", "192.168.1.21", seen)?.how)
        val m = HomeTvMatch.pick("CastBridge TV M1", "192.168.1.20", seen)
        assertEquals("CastBridge TV M1 (2)", m?.tv?.name, "même adresse, même nom de base : la même TV renommée par le mDNS")
        assertEquals(HomeTvMatch.How.HOST, m?.how)
        assertNull(HomeTvMatch.pick("CastBridge TV M1", "192.168.1.21", seen), "une TV d'un autre modèle à l'ancienne adresse n'est pas la nôtre")
        assertNull(HomeTvMatch.pick("CastBridge TV M1", null, seen), "sans adresse connue, un « (2) » peut être l'autre TV du même modèle")
    }

    @Test fun exactNameAtAnotherAddressIsNotMatchedWhenTheAddressIsKnown() {
        // deux TV du même modèle : TV2 s'annonce maintenant « M1 » ailleurs ; notre TV (à 192.168.1.20) est éteinte
        val seen = listOf(HomeTvMatch.Seen("CastBridge TV M1", "192.168.1.30", 8765))
        assertNull(HomeTvMatch.pick("CastBridge TV M1", "192.168.1.20", seen), "adresse connue et différente : ne rien apparier (l'utilisateur choisit)")
        val both = listOf(HomeTvMatch.Seen("CastBridge TV M1", "192.168.1.30", 8765), HomeTvMatch.Seen("CastBridge TV M1 (2)", "192.168.1.20", 8765))
        assertEquals("192.168.1.20", HomeTvMatch.pick("CastBridge TV M1", "192.168.1.20", both)?.tv?.host, "adresse + nom de base d'abord")
        val same = HomeTvMatch.pick("CastBridge TV M1", "192.168.1.30", seen)
        assertEquals(HomeTvMatch.How.NAME_AND_HOST, same?.how)
        assertTrue(HomeTvMatch.refuseOn401(same), "nom et adresse concordent : le refus vient bien de notre TV")
        assertFalse(HomeTvMatch.refuseOn401(HomeTvMatch.pick("CastBridge TV M1", null, seen)), "nom seul : un 401 peut venir d'une autre TV, le code n'est jamais marqué refusé")
        assertFalse(HomeTvMatch.refuseOn401(HomeTvMatch.pick("CastBridge TV M1", "192.168.1.20", both)), "adresse seule (nom « (2) ») : jamais marqué")
        assertFalse(HomeTvMatch.refuseOn401(null))
    }
}
