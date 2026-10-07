package castbridge.core.ux

import castbridge.core.ux.SignalLevel.GREEN
import castbridge.core.ux.SignalLevel.ORANGE
import castbridge.core.ux.SignalLevel.RED
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * La pastille de l'accueil et l'état d'une clé USB (docs/STORAGE.md « Clé USB mal éjectée ») : une clé qu'Android vérifie, qu'il ne sait pas lire ou qui a été retirée sans éjection met la
 * pastille en orange avec ce qu'il faut faire ; « prête à être retirée » est une bonne nouvelle (jamais d'alerte) ; rien ne cache un état rouge.
 */
class TvSignalUsbNoteTest {
    private val ok = TvFacts(lan = LanKind.WIFI, wifiName = "Maison", bluetooth = BtState.ON, pairedPhones = 1, internet = true)
    private val checking = UsbNote(ORANGE, "Clé USB en vérification par Android", "Patientez : la clé sera prête dans un instant")
    private val unreadable = UsbNote(ORANGE, "Clé USB illisible", "Réparez-la sur un ordinateur, ou ouvrez les réglages de stockage : MENU > Clé USB")
    private val pull = UsbNote(GREEN, "Clé prête à être retirée", null)

    @Test fun `a key under check turns the pastille orange and says to wait`() {
        val v = TvSignal.of(ok.copy(usbNote = checking))
        assertEquals(ORANGE, v.level)
        assertEquals("Clé USB en vérification par Android", v.text)
        assertEquals("Patientez : la clé sera prête dans un instant", v.action)
        val st = v.indicator(IndicatorKind.STORAGE)
        assertEquals(ORANGE, st.level)
        assertEquals("Stockage : Clé USB en vérification par Android", st.text)
        assertEquals(checking.action, st.action)
    }

    @Test fun `an unreadable key says what to do`() {
        val v = TvSignal.of(ok.copy(usbNote = unreadable))
        assertEquals(ORANGE, v.level)
        assertEquals(unreadable.action, v.action)
        assertEquals("Stockage : Clé USB illisible", v.indicator(IndicatorKind.STORAGE).text)
    }

    @Test fun `a key ready to be pulled is good news, the pastille stays green`() {
        val v = TvSignal.of(ok.copy(usbNote = pull))
        assertEquals(GREEN, v.level)
        assertEquals("Prêt à recevoir", v.text)
        assertNull(v.action)
        val st = v.indicator(IndicatorKind.STORAGE)
        assertEquals(GREEN, st.level)
        assertEquals("Stockage : Clé prête à être retirée", st.text)
    }

    @Test fun `no note, nothing changes`() {
        assertEquals(TvSignal.of(ok), TvSignal.of(ok.copy(usbNote = null)))
        assertEquals(GREEN, TvSignal.of(ok).indicator(IndicatorKind.STORAGE).level)
    }

    @Test fun `a red state is never hidden behind a key note`() {
        val full = TvSignal.of(ok.copy(storage = StorageState.FULL, freeText = "20 Mo", usbNote = checking))
        assertEquals(RED, full.level)
        assertEquals("Stockage plein : rien ne peut être reçu", full.text)
        assertEquals(RED, full.indicator(IndicatorKind.STORAGE).level, "the storage indicator keeps its own cause")
        val noNet = TvSignal.of(ok.copy(lan = LanKind.NONE, bluetooth = BtState.OFF, internet = false, usbNote = unreadable))
        assertEquals(RED, noNet.level)
        assertEquals("Aucun réseau : la TV ne peut rien recevoir", noNet.text)
    }

    @Test fun `the first cause stays when two things need attention`() {
        val low = TvSignal.of(ok.copy(storage = StorageState.LOW, freeText = "800 Mo", usbNote = checking))
        assertEquals("Stockage presque plein", low.text, "the cause found first is said")
        val weak = TvSignal.of(ok.copy(weakSignal = true, usbNote = checking))
        assertEquals("Signal Wi-Fi faible", weak.text)
        val failed = TvSignal.of(ok.copy(transferFailed = true, usbNote = checking))
        assertEquals("Clé USB en vérification par Android", failed.text, "a key to wait for comes before a transfer that waits")
    }

    @Test fun `a slow key keeps its own storage line`() {
        val st = TvSignal.of(ok.copy(usbKey = true, usbSlow = true, freeText = "20 Go", usbNote = checking)).indicator(IndicatorKind.STORAGE)
        assertEquals("Stockage : clé lente · 20 Go libres", st.text)
    }

    @Test fun `the free space stays on the storage line`() {
        val st = TvSignal.of(ok.copy(freeText = "20 Go", usbNote = checking)).indicator(IndicatorKind.STORAGE)
        assertEquals("Stockage : Clé USB en vérification par Android · 20 Go libres", st.text)
    }
}
