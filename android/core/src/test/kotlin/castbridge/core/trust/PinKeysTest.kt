package castbridge.core.trust

import kotlin.test.*

class PinKeysTest {
    private data class Row(val label: String, val name: String?, val mdns: String?, val bt: String?, val host: List<String>, val port: Int?, val keys: List<String>)
    private val rows = listOf(
        Row("tout", "Salon", "CastBridge TV Salon", "AA:BB:CC", listOf("192.168.0.5"), 8765, listOf("Salon", "CastBridge TV Salon", "bt:AA:BB:CC", "192.168.0.5:8765")),
        Row("hôte sans port : 8765", "Salon", null, null, listOf("192.168.0.5"), null, listOf("Salon", "192.168.0.5:8765")),
        Row("port propre", null, null, null, listOf("10.0.0.2"), 9000, listOf("10.0.0.2:9000")),
        Row("rien", null, null, null, emptyList(), null, emptyList()),
        Row("doublon nom = mdns", "Salon", "Salon", null, emptyList(), null, listOf("Salon")),
        Row("vides ignorés", " ", "", "", listOf(""), null, emptyList()),
        Row("Bluetooth seul", null, null, "11:22", emptyList(), null, listOf("bt:11:22")),
        Row("nom avec espaces", " Salon ", null, null, emptyList(), null, listOf("Salon")),
    )

    @Test fun keysOf() { for (r in rows) assertEquals(r.keys, PinKeys.keysOf(r.name, r.mdns, r.bt, r.host, r.port), r.label) }

    @Test fun severalIps() {
        assertEquals(listOf("Salon", "192.168.0.5:8765", "10.0.0.9:8765"), PinKeys.keysOf("Salon", null, null, listOf("192.168.0.5", "10.0.0.9", "192.168.0.5"), null))
    }

    @Test fun hostnameAndIpv6() {
        assertEquals(listOf("tv.local:8765"), PinKeys.keysOf(null, null, null, listOf("tv.local"), null))
        assertEquals("tv.local", PinKeys.normalize("tv.local"))
        assertEquals(listOf("[fe80::1]:9000"), PinKeys.keysOf(null, null, null, listOf("fe80::1"), 9000))
        assertEquals("fe80::1", PinKeys.normalize("fe80::1"))
    }

    @Test fun lookupFindsLegacyBareHostEntry() {
        val k = PinKeys.lookupKeys("Salon", null, null, listOf("192.168.0.5"), 8765)
        assertTrue("192.168.0.5" in k)           // legacy: TvScreen.kt:77 stored under the bare host
        assertTrue("192.168.0.5:8765" in k)
        assertEquals(k.distinct(), k)
        assertEquals(PinKeys.keysOf("Salon", null, null, listOf("192.168.0.5"), 8765), k.take(2))   // normalized first
    }

    @Test fun lookupWithoutHostsEqualsKeys() {
        assertEquals(PinKeys.keysOf("Salon", "CastBridge TV Salon", "AA", emptyList(), null), PinKeys.lookupKeys("Salon", "CastBridge TV Salon", "AA", emptyList(), null))
    }

    @Test fun normalize() {
        assertEquals("192.168.0.5:8765", PinKeys.normalize("192.168.0.5"))
        assertEquals("192.168.0.5:8765", PinKeys.normalize(" 192.168.0.5 "))
        assertEquals("192.168.0.5:9000", PinKeys.normalize("192.168.0.5:9000"))
        assertEquals("bt:AA:BB", PinKeys.normalize("bt:AA:BB"))
        assertEquals("CastBridge TV Salon", PinKeys.normalize("CastBridge TV Salon"))
        assertEquals("Salon", PinKeys.normalize("Salon"))
        assertEquals("", PinKeys.normalize("  "))
    }

    @Test fun normalizeIsIdempotent() { for (k in listOf("1.2.3.4", "1.2.3.4:1", "Salon", "bt:X")) assertEquals(PinKeys.normalize(k), PinKeys.normalize(PinKeys.normalize(k))) }

    // ---- W15-02 : résolution d'une clé d'écran vers la TV enregistrée (R-01 : jeton retrouvé, PIN jamais redemandé à un téléphone de confiance)
    private val salon = SavedTv("AA:BB:CC:DD:EE:01", "Salon", mdns = "CastBridge TV Salon", lastIps = listOf("192.168.0.5"), port = 8765)
    private val chambre = SavedTv("AA:BB:CC:DD:EE:02", "Chambre", mdns = null, lastIps = listOf("192.168.0.9"), port = 9000)
    private val btOnly = SavedTv("AA:BB:CC:DD:EE:03", "Cave", mdns = null, lastIps = emptyList())
    private val all = listOf(salon, chambre, btOnly)
    private fun resolve(key: String, saved: List<SavedTv> = all, default: SavedTv? = salon, tunnel: Int? = 18765, tunnelTv: String? = salon.address) =
        PinKeys.resolve(key, saved, default, 8765, tunnel, tunnelTv)

    private data class Res(val label: String, val key: String, val expect: SavedTv?)
    @Test fun resolveTable() {
        val rows = listOf(
            Res("TvHome : nom affiché", "Salon", salon),
            Res("nom insensible à la casse", "salon", salon),
            Res("TvScreen : IP sans port", "192.168.0.5", salon),
            Res("TvScreen : ip:port", "192.168.0.5:8765", salon),
            Res("WifiDirect.BASE_URL / URL complète", "http://192.168.0.5:8765", salon),
            Res("URL avec chemin", "http://192.168.0.5:8765/api/info", salon),
            Res("TvDiscovery : « (Bluetooth) »", "Salon (Bluetooth)", salon),
            Res("NSD : suffixe (2)", "CastBridge TV Salon (2)", salon),
            Res("nom mDNS", "CastBridge TV Salon", salon),
            Res("bt: majuscules", "bt:AA:BB:CC:DD:EE:03", btOnly),
            Res("bt: minuscules", "bt:aa:bb:cc:dd:ee:03", btOnly),
            Res("BT seul par nom", "Cave", btOnly),
            Res("BT seul « (Bluetooth) »", "Cave (Bluetooth)", btOnly),
            Res("port non standard", "192.168.0.9:9000", chambre),
            Res("tunnel 127.0.0.1:18765 = TV de la passerelle", "127.0.0.1:18765", salon),
            Res("tunnel en URL", "http://127.0.0.1:18765", salon),
            Res("clé vide", "", null),
            Res("clé blanche", "   ", null),
            Res("clé inconnue", "Garage", null),
            Res("IP inconnue", "10.9.9.9:8765", null),
        )
        for (r in rows) assertEquals(r.expect?.address, resolve(r.key)?.address, r.label)
    }

    @Test fun tunnelToNonDefaultTv() {
        assertEquals(chambre.address, resolve("127.0.0.1:18765", all, default = salon, tunnelTv = chambre.address)?.address, "passerelle vers Chambre, défaut Salon")
        assertEquals(chambre.address, resolve("http://127.0.0.1:18765", all, default = null, tunnelTv = chambre.address.lowercase())?.address, "sans défaut, adresse en minuscules")
        assertEquals(btOnly.address, resolve("localhost:18765", all, default = salon, tunnelTv = btOnly.address)?.address)
        assertEquals(salon.address, resolve("127.0.0.1:18765", listOf(salon, chambre), salon, tunnelTv = salon.address)?.address, "deux TV, passerelle vers le défaut")
    }

    @Test fun tunnelToAPairedButNotSavedTvIsNull() {
        assertNull(resolve("127.0.0.1:18765", all, default = salon, tunnelTv = "AA:BB:CC:DD:EE:99"), "ne jamais prendre le jeton du défaut")
        assertNull(resolve("127.0.0.1:18765", emptyList(), default = null))
    }

    @Test fun tunnelNeedsAPortAndARunningGateway() {
        assertNull(resolve("127.0.0.1:18765", tunnel = null), "port du tunnel inconnu : rien")
        assertNull(resolve("127.0.0.1:18765", tunnelTv = null), "passerelle arrêtée : pas de jeton pour la boucle locale")
        assertNull(resolve("127.0.0.1:18765", default = null, tunnelTv = null), "ni passerelle ni défaut")
        assertNull(resolve("localhost:9999"), "localhost, autre port")
        assertNull(resolve("127.0.0.1:9999"), "autre port local : pas le tunnel")
    }

    @Test fun realNameContainingBluetoothWinsOverStripping() {
        val odd = SavedTv("AA:BB:CC:DD:EE:04", "Salon (Bluetooth)", lastIps = emptyList())
        assertEquals(odd.address, resolve("Salon (Bluetooth)", listOf(salon, odd))?.address)
        assertEquals(salon.address, resolve("Salon", listOf(salon, odd))?.address)
    }

    @Test fun homonymsAreToldApartByTheirAddress() {
        val a = SavedTv("AA:BB:CC:DD:EE:11", "TV", lastIps = listOf("10.0.0.1"))
        val b = SavedTv("AA:BB:CC:DD:EE:12", "TV", lastIps = listOf("10.0.0.2"))
        assertEquals(b.address, resolve("10.0.0.2:8765", listOf(a, b), a)?.address)
        assertEquals(b.address, resolve("bt:AA:BB:CC:DD:EE:12", listOf(a, b), a)?.address)
        assertEquals(a.address, resolve("10.0.0.1", listOf(a, b), b)?.address)
    }

    @Test fun ambiguousHomonymKeyGoesToTheDefaultOrNothing() {
        val a = SavedTv("AA:BB:CC:DD:EE:11", "TV"); val b = SavedTv("AA:BB:CC:DD:EE:12", "TV")
        assertEquals(b.address, resolve("TV", listOf(a, b), b)?.address)
        assertEquals(b.address, resolve("TV (Bluetooth)", listOf(a, b), b)?.address)
        assertNull(resolve("TV", listOf(a, b), null), "jamais le jeton d'une TV au hasard")
        assertNull(resolve("TV", listOf(a, b), salon), "le défaut doit être l'un des candidats")
    }

    @Test fun ipv6() {
        val v6 = SavedTv("AA:BB:CC:DD:EE:21", "V6", lastIps = listOf("fe80::1"), port = 8765)
        assertEquals(v6.address, resolve("[fe80::1]:8765", listOf(v6), null)?.address)
        assertEquals(v6.address, resolve("http://[fe80::1]:8765/x", listOf(v6), null)?.address)
        assertEquals(v6.address, resolve("fe80::1", listOf(v6), null)?.address)
    }

    // ---- W15-02-fix
    @Test fun staleSharedIpIsNull() {
        val a = SavedTv("AA:BB:CC:DD:EE:31", "A", lastIps = listOf("10.0.0.7")); val b = SavedTv("AA:BB:CC:DD:EE:32", "B", lastIps = listOf("10.0.0.7"))
        assertNull(resolve("10.0.0.7:8765", listOf(a, b), a), "IP périmée partagée : jamais le défaut")
        assertEquals(a.address, resolve("A", listOf(a, b), a)?.address, "le nom reste fiable")
    }
    @Test fun wifiDirectAddressIsNeverMatched() {
        val a = SavedTv("AA:BB:CC:DD:EE:31", "A", lastIps = listOf("192.168.49.1"))
        assertNull(resolve("192.168.49.1:8765", listOf(a), a))
        assertEquals(a.address, resolve("A", listOf(a), a)?.address)
    }

    @Test fun ipv6ZoneAndLocalHostname() {
        val v6 = SavedTv("AA:BB:CC:DD:EE:21", "V6", lastIps = listOf("fe80::1%wlan0", "tv-salon.local"), port = 8765)
        assertEquals(v6.address, resolve("fe80::1%wlan0", listOf(v6), null)?.address)
        assertEquals(v6.address, resolve("[fe80::1%wlan0]:8765", listOf(v6), null)?.address)
        assertEquals(v6.address, resolve("tv-salon.local", listOf(v6), null)?.address)
        assertEquals(v6.address, resolve("http://TV-Salon.local:8765/x", listOf(v6), null)?.address)
        assertEquals(listOf("[fe80::1%wlan0]:8765", "tv-salon.local:8765"), PinKeys.keysOf(null, null, null, v6.lastIps, 8765))
        assertNull(resolve("tv-autre.local", listOf(v6), null))
    }

    @Test fun writeKeysDecision() {
        assertEquals(listOf("Salon"), PinKeys.writeKeys("Salon", null, false), "TV inconnue : la clé seule")
        assertEquals(listOf("Salon"), PinKeys.writeKeys("Salon", salon, hasToken = true), "TV à jeton : pas de clé IP")
        assertTrue(PinKeys.writeKeys("192.168.0.5:8765", salon, hasToken = true).isEmpty(), "clé IP d'une TV à jeton : rien")
        assertEquals(PinKeys.keysOf(btOnly).toSet(), PinKeys.writeKeys("Cave", btOnly, hasToken = false).toSet())
        assertTrue(PinKeys.writeKeys("Salon", salon, hasToken = false).containsAll(listOf("192.168.0.5:8765", "bt:AA:BB:CC:DD:EE:01")))
    }

    @Test fun credentialReadOrderTokenThenPinUnderAnyKeyThenEmpty() {
        val store = mapOf("192.168.0.5" to "1111", "Chambre" to "2222")           // legacy bare-host entry for Salon
        val read = { k: String -> store[k] }
        assertEquals("cbt_x", PinKeys.credential("cbt_x", "Salon", salon, read), "jeton d'abord")
        assertEquals("2222", PinKeys.credential(null, "Chambre", chambre, read), "clé directe")
        assertEquals("1111", PinKeys.credential(null, "Salon", salon, read), "PIN sous une autre clé (IP nue) de la même TV")
        assertEquals("", PinKeys.credential(null, "Cave", btOnly, read))
        assertEquals("", PinKeys.credential(null, "Inconnue", null, read))
        assertEquals("", PinKeys.credential(null, null, salon, read))
        assertEquals("1111", PinKeys.credential("  ", "Salon", salon, read), "jeton blanc ignoré")
    }

    @Test fun noSavedTvMeansNull() { assertNull(resolve("Salon", emptyList(), null)) }

    @Test fun keysOfTvHoldsEveryScreenForm() {
        val k = PinKeys.keysOf(salon, 8765)
        for (form in listOf("Salon", "Salon (Bluetooth)", "CastBridge TV Salon", "bt:AA:BB:CC:DD:EE:01", "192.168.0.5:8765", "192.168.0.5", "http://192.168.0.5:8765"))
            assertTrue(form in k, "manque $form dans $k")
        assertEquals(k.distinct(), k); assertTrue(k.none { it.isBlank() })
        assertTrue(PinKeys.keysOf(btOnly).containsAll(listOf("Cave", "Cave (Bluetooth)", "bt:AA:BB:CC:DD:EE:03")))
        assertTrue("192.168.0.9:9000" in PinKeys.keysOf(chambre))
    }

    @Test fun everyKeyOfATvResolvesBackToIt() {
        for (tv in all) for (k in PinKeys.keysOf(tv)) assertEquals(tv.address, resolve(k, all, null)?.address, "clé « $k » de ${tv.name}")
    }

    @Test fun lookupKeysOfTvIncludeLegacyBareHost() {
        val k = PinKeys.lookupKeys(salon)
        assertTrue("192.168.0.5" in k && "192.168.0.5:8765" in k && "Salon" in k)
    }

    // ---- PinFallback (R-01, second half)
    @Test fun tokenIsUsedWhenLive() {
        val c = PinFallback.choose("cbt_x", "1234", trustedTv = true, tokenRefused = false)
        assertEquals(PinFallback.Source.TOKEN, c.source); assertEquals("cbt_x", c.credential); assertNull(c.reason)
    }

    @Test fun refusedTokenOfTrustedTvNeverFallsBackToOldPin() {
        for (token in listOf(null, "cbt_x")) {
            val c = PinFallback.choose(token, "1234", trustedTv = true, tokenRefused = true)
            assertEquals(PinFallback.Source.NONE, c.source); assertEquals("", c.credential); assertEquals(PinFallback.REFUSED, c.reason)
        }
    }

    @Test fun pinStillUsedWhenNothingTrusts() {
        assertEquals("1234", PinFallback.choose(null, "1234", trustedTv = false, tokenRefused = false).credential)
        assertEquals("1234", PinFallback.choose(null, "1234", trustedTv = true, tokenRefused = false).credential)   // session not up yet, nothing refused
        assertEquals("1234", PinFallback.choose(null, "1234", trustedTv = false, tokenRefused = true).credential)
    }

    @Test fun nothingAtAllAsksForTheCode() {
        val c = PinFallback.choose(null, "", trustedTv = false, tokenRefused = false)
        assertEquals(PinFallback.Source.NONE, c.source); assertEquals(PinFallback.NEEDS_CODE, c.reason)
        assertEquals(PinFallback.NEEDS_CODE, PinFallback.choose("", "  ", trustedTv = true, tokenRefused = false).reason)
    }
}
