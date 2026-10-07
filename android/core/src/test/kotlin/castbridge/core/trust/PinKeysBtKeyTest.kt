package castbridge.core.trust

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * R-30 (audit anti-régression 2026-10-07 b, B2) : l'audit a REJOUÉ le défaut contre les classes compilées : `read("bt:MAC")` rend le code, `read(MAC nue, nom)` rend "". Ce test rejoue
 * la même chose avec la fabrique unique de la clé ([PinKeys.btKey]) et garde que toutes les autres clés d'une TV enregistrée en contiennent la même.
 */
class PinKeysBtKeyTest {
    private val mac = "AA:BB:CC:DD:EE:01"
    private val tv = SavedTv(address = mac, name = "SMART_TV", mdns = "CastBridge TV Salon", lastIps = listOf("192.168.0.5"))
    private fun scope(vararg saved: SavedTv) = PinScope(saved.toList(), saved.firstOrNull())

    @Test fun theKeyIsThePrefixAndTheAddressAsTheRegistryWritesIt() {
        assertEquals("bt:AA:BB:CC:DD:EE:01", PinKeys.btKey(mac))
        assertEquals("bt:AA:BB:CC:DD:EE:01", PinKeys.btKey("  aa:bb:cc:dd:ee:01 "), "majuscules, sans espaces : la même clé quelle que soit l'écriture de l'adresse")
        assertEquals(PinKeys.btKey(mac), PinKeys.btKey(PinKeys.btKey(mac).removePrefix(PinKeys.BT_PREFIX)))
        assertEquals("bt:", PinKeys.BT_PREFIX)
    }

    @Test fun theAuditReplayABareAddressFindsNothingAndTheBtKeyFindsTheCode() {
        val book = PinBook(MemoryPinKv())
        val s = scope(tv)
        assertTrue(book.write(PinKeys.btKey(mac), "482913", s))
        assertEquals("482913", book.read(PinKeys.btKey(mac), s, tv.name), "sous « bt:ADRESSE » : le code de la TV")
        assertEquals("", book.read(mac, s, tv.name), "l'adresse NUE (ce que RelayRuntime passait) : ni jeton ni code ⇒ « non synchronisé »")
        assertEquals("host:[aa:bb:cc:dd:ee:01]:8765", PinBook(MemoryPinKv()).formOf(mac), "…parce qu'elle est lue comme un hôte IPv6")
        assertEquals("bt:AA:BB:CC:DD:EE:01", PinBook(MemoryPinKv()).formOf(PinKeys.btKey(mac)))
    }

    @Test fun theCodeTypedForOtherKeysOfTheSameTvIsFoundThroughTheBtKeyToo() {
        // R-10 : un seul enregistrement par TV, quelle que soit la clé d'écran (nom, mDNS, IP) : le tuyau retrouve le code tapé dans l'onglet de la TV
        val book = PinBook(MemoryPinKv())
        val s = scope(tv)
        assertTrue(book.write("SMART_TV", "654321", s))
        assertEquals("654321", book.read(PinKeys.btKey(mac), s, tv.name))
        assertEquals("654321", book.read("bt:" + mac.lowercase(), s, tv.name), "même identifiant, minuscules ou majuscules")
    }

    @Test fun theTokenOfASavedTvIsFoundThroughTheBtKeyOnly() {
        assertEquals(tv, PinKeys.resolve(PinKeys.btKey(mac), listOf(tv), null))
        assertNull(PinKeys.resolve(mac, listOf(tv), null), "une adresse nue ne désigne aucune TV : pas de jeton")
    }

    @Test fun everyKeyListOfASavedTvCarriesTheSameBtKey() {
        assertTrue(PinKeys.btKey(mac) in PinKeys.keysOf(tv))
        assertEquals(listOf(PinKeys.btKey(mac)), PinKeys.keysOf(null, null, mac, emptyList(), null))
        assertEquals(PinKeys.btKey(mac), PinBook(MemoryPinKv()).tvId("bt:" + mac.lowercase(), scope(tv)))
        assertEquals(PinKeys.btKey(mac), PinBook(MemoryPinKv()).tvId("SMART_TV", scope(tv)), "l'identifiant stable d'une TV enregistrée est la clé « bt: »")
    }
}
