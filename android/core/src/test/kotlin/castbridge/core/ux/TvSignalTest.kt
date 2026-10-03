package castbridge.core.ux

import castbridge.core.trust.AbsentKind
import castbridge.core.trust.LinkState
import castbridge.core.trust.RouteKind
import castbridge.core.ux.SignalLevel.BLACK
import castbridge.core.ux.SignalLevel.GREEN
import castbridge.core.ux.SignalLevel.ORANGE
import castbridge.core.ux.SignalLevel.RED
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Signalétique de la TV : la couleur dit ce que l'on peut faire ; Internet absent n'est jamais rouge ni orange sur l'accueil. */
class TvSignalTest {
    private class Row(
        val name: String, val f: TvFacts, val global: SignalLevel, val text: String,
        val net: SignalLevel, val bt: SignalLevel, val internet: SignalLevel, val storage: SignalLevel,
    )

    private val ok = TvFacts(lan = LanKind.WIFI, wifiName = "Maison", bluetooth = BtState.ON, pairedPhones = 1, internet = true)

    private val table = listOf(
        Row("tout va bien (Wi-Fi, Internet)", ok, GREEN, "Prêt à recevoir", GREEN, GREEN, GREEN, GREEN),
        Row("Ethernet sans Internet : vert, Internet noir", ok.copy(lan = LanKind.ETHERNET, internet = false), GREEN, "Prêt à recevoir", GREEN, GREEN, BLACK, GREEN),
        Row("Wi-Fi sans Internet : vert, Internet noir", ok.copy(internet = false), GREEN, "Prêt à recevoir", GREEN, GREEN, BLACK, GREEN),
        Row("Bluetooth coupé par l'usager, LAN actif : vert, Bluetooth noir", ok.copy(bluetooth = BtState.OFF), GREEN, "Prêt à recevoir", GREEN, BLACK, GREEN, GREEN),
        Row("aucun LAN, Bluetooth coupé : rouge", ok.copy(lan = LanKind.NONE, bluetooth = BtState.OFF, internet = false), RED, "Aucun réseau : la TV ne peut rien recevoir", RED, BLACK, BLACK, GREEN),
        Row("aucun LAN, Bluetooth inutilisable : rouge", ok.copy(lan = LanKind.NONE, bluetooth = BtState.UNUSABLE, internet = false), RED, "Aucun réseau : la TV ne peut rien recevoir", RED, ORANGE, BLACK, GREEN),
        Row("aucun LAN, Bluetooth actif : orange", ok.copy(lan = LanKind.NONE, internet = false), ORANGE, "Bluetooth seulement", ORANGE, GREEN, BLACK, GREEN),
        Row("aucun LAN, Bluetooth actif sans téléphone : orange", ok.copy(lan = LanKind.NONE, pairedPhones = 0, internet = false), ORANGE, "Bluetooth seulement", ORANGE, ORANGE, BLACK, GREEN),
        Row("Wi-Fi associé sans adresse, Bluetooth actif : orange", ok.copy(wifiNoAddress = true), ORANGE, "Bluetooth seulement", ORANGE, GREEN, GREEN, GREEN),
        Row("Wi-Fi associé sans adresse, Bluetooth coupé : rouge", ok.copy(wifiNoAddress = true, bluetooth = BtState.OFF), RED, "Aucun réseau : la TV ne peut rien recevoir", RED, BLACK, GREEN, GREEN),
        Row("signal Wi-Fi faible : orange", ok.copy(weakSignal = true), ORANGE, "Signal Wi-Fi faible", ORANGE, GREEN, GREEN, GREEN),
        Row("stockage presque plein : orange", ok.copy(storage = StorageState.LOW, freeText = "800 Mo"), ORANGE, "Stockage presque plein", GREEN, GREEN, GREEN, ORANGE),
        Row("stockage plein : rouge", ok.copy(storage = StorageState.FULL, freeText = "20 Mo"), RED, "Stockage plein : rien ne peut être reçu", GREEN, GREEN, GREEN, RED),
        Row("service qui n'écoute pas : rouge", ok.copy(listening = false), RED, "Service arrêté : la TV n'écoute pas", GREEN, GREEN, GREEN, GREEN),
        Row("contrôle parental bloquant : rouge", ok.copy(parentalLock = true), RED, "Contrôle parental : réception verrouillée", GREEN, GREEN, GREEN, GREEN),
        Row("transfert échoué en attente : orange", ok.copy(transferFailed = true), ORANGE, "Un transfert a échoué et attend", GREEN, GREEN, GREEN, GREEN),
        Row("clé USB lente : orange", ok.copy(usbKey = true, usbSlow = true), ORANGE, "Clé USB lente", GREEN, GREEN, GREEN, ORANGE),
        Row("le code va changer : orange", ok.copy(pinRotating = true), ORANGE, "Le code de la TV va changer", GREEN, GREEN, GREEN, GREEN),
        Row("stockage plein ET aucun réseau : rouge (jamais plus vert)", ok.copy(lan = LanKind.NONE, bluetooth = BtState.OFF, storage = StorageState.FULL), RED, "Stockage plein : rien ne peut être reçu", RED, BLACK, GREEN, RED),
    )

    @Test fun tableOfSituations() {
        for (r in table) {
            val v = TvSignal.of(r.f)
            assertEquals(r.global, v.level, "${r.name} : niveau global")
            assertEquals(r.text, v.text, "${r.name} : texte de la puce")
            assertEquals(r.global, v.indicator(IndicatorKind.RECEPTION).level, "${r.name} : Réception = puce")
            assertEquals(r.net, v.indicator(IndicatorKind.NETWORK).level, "${r.name} : Réseau")
            assertEquals(r.bt, v.indicator(IndicatorKind.BLUETOOTH).level, "${r.name} : Bluetooth")
            assertEquals(r.internet, v.indicator(IndicatorKind.INTERNET).level, "${r.name} : Internet")
            assertEquals(r.storage, v.indicator(IndicatorKind.STORAGE).level, "${r.name} : Stockage")
        }
    }

    @Test fun fiveIndicatorsInOrder() {
        assertEquals(listOf(IndicatorKind.RECEPTION, IndicatorKind.NETWORK, IndicatorKind.BLUETOOTH, IndicatorKind.INTERNET, IndicatorKind.STORAGE),
            TvSignal.of(ok).indicators.map { it.kind })
    }

    @Test fun internetAbsentIsBlackNeverRedNorOrange() {
        for (r in table) {
            val i = TvSignal.of(r.f.copy(internet = false)).indicator(IndicatorKind.INTERNET)
            assertEquals(BLACK, i.level, r.name)
            assertEquals("Internet : non connecté, inutile pour CastBridge", i.text)
        }
        // Internet seul ne change jamais la couleur globale.
        for (r in table) assertEquals(TvSignal.of(r.f.copy(internet = true)).level, TvSignal.of(r.f.copy(internet = false)).level, r.name)
        // Orange seulement sur la tuile qui en a vraiment besoin.
        assertEquals(ORANGE, TvSignal.internetNeededTile(false).level); assertEquals("Internet requis", TvSignal.internetNeededTile(false).text)
        assertEquals(GREEN, TvSignal.internetNeededTile(true).level)
    }

    @Test fun chipIsNeverGreenerThanItsWorstRequiredIndicator() {
        val lans = LanKind.values(); val bts = BtState.values(); val stores = StorageState.values()
        for (lan in lans) for (noAddr in listOf(false, true)) for (bt in bts) for (st in stores) for (weak in listOf(false, true)) for (listening in listOf(true, false)) for (lock in listOf(false, true)) {
            val f = TvFacts(lan = lan, wifiNoAddress = noAddr, weakSignal = weak, bluetooth = bt, storage = st, listening = listening, parentalLock = lock)
            val v = TvSignal.of(f)
            val net = v.indicator(IndicatorKind.NETWORK).level; val sto = v.indicator(IndicatorKind.STORAGE).level
            val label = "$f"
            assertTrue(v.level.severity >= net.severity, "réseau $net plus grave que la puce ${v.level} : $label")
            assertTrue(v.level.severity >= sto.severity, "stockage $sto plus grave que la puce ${v.level} : $label")
            if (v.level == GREEN) assertTrue(net == GREEN && sto == GREEN && listening && !lock && (f.lanUp), "vert à tort : $label")
            if (!listening || lock || st == StorageState.FULL) assertEquals(RED, v.level, label)
            if (!f.lanUp && bt != BtState.ON) assertEquals(RED, v.level, "rien ne peut joindre la TV : $label")
            // la puce rouge dit toujours une cause et une action
            if (v.level != GREEN) assertNotNull(v.action, label)
        }
    }

    @Test fun textsAreFrenchAndNonEmptyAndNeverSenderReceiver() {
        val all = table.flatMap { r -> val v = TvSignal.of(r.f); listOf(v.text, v.action.orEmpty()) + v.indicators.flatMap { listOf(it.text, it.action.orEmpty()) } } + TvSignal.LEGEND
        for (t in all) assertTrue(!t.contains("sender", true) && !t.contains("receiver", true), t)
        for (r in table) {
            val v = TvSignal.of(r.f)
            assertTrue(v.text.isNotBlank()); v.indicators.forEach { assertTrue(it.text.isNotBlank(), "${r.name} ${it.kind}") }
            if (v.level != GREEN) assertTrue(!v.action.isNullOrBlank(), r.name)
        }
        assertEquals("Branchez le câble réseau ou connectez le Wi-Fi : MENU > Connexion & réglages", TvSignal.of(table[4].f).action)
        assertEquals("Activez le Bluetooth pour recevoir sans réseau", TvSignal.of(table[3].f).indicator(IndicatorKind.BLUETOOTH).action)
        assertTrue(TvSignal.LEGEND.contains("Vert") && TvSignal.LEGEND.contains("Orange") && TvSignal.LEGEND.contains("Rouge") && TvSignal.LEGEND.contains("Noir"))
    }

    @Test fun eachLevelHasItsOwnShapeAndLegibleColour() {
        assertEquals(4, SignalLevel.values().map { it.shape }.toSet().size, "quatre formes distinctes (daltonisme)")
        assertEquals(4, SignalLevel.values().map { it.word }.toSet().size)
        for (l in SignalLevel.values()) assertTrue(SignalColors.contrast(SignalColors.of(l), SignalColors.BACKGROUND) >= 4.5, "contraste ${l}")
        assertTrue(SignalColors.contrast(SignalColors.TEXT, SignalColors.BACKGROUND) >= 4.5)
        // le noir n'est jamais invisible : le contour se détache du fond
        assertTrue(SignalColors.contrast(SignalColors.BLACK_OUTLINE, SignalColors.BACKGROUND) >= 4.5)
        assertTrue(SignalColors.BLACK_FILL != SignalColors.BACKGROUND)
        // l'ambre de la marque n'est pas une couleur d'état
        val brand = 0xFFF5B025.toInt()
        for (l in SignalLevel.values()) assertTrue(SignalColors.of(l) != brand)
    }

    @Test fun storageThresholds() {
        assertEquals(StorageState.OK, TvSignal.storageOf(-1)); assertEquals(StorageState.OK, TvSignal.storageOf(5L shl 30))
        assertEquals(StorageState.LOW, TvSignal.storageOf(500L shl 20)); assertEquals(StorageState.FULL, TvSignal.storageOf(50L shl 20))
    }

    @Test fun phoneSpeaksTheSameLanguage() {
        assertEquals(RED, TvSignal.phoneLevel(LinkState.TvUnreachable(AbsentKind.NO_ANSWER)))
        assertEquals(ORANGE, TvSignal.phoneLevel(LinkState.Degraded("Salon")))
        assertEquals(GREEN, TvSignal.phoneLevel(LinkState.Connected(RouteKind.LAN, "Salon")))
        assertEquals(BLACK, TvSignal.phoneLevel(LinkState.NoTv))
        assertEquals(RED, TvSignal.phoneLevel(LinkState.Denied))
    }
}
