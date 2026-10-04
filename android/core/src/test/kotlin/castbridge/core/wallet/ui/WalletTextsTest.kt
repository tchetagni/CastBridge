package castbridge.core.wallet.ui

import castbridge.core.wallet.Snapshot
import castbridge.core.wallet.WalletCurrency
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WalletTextsTest {
    private val sp = " "
    private fun snap(n: Long = 3450, nb: Long = 0, m: Long = 12, mb: Long = 0, ed: String = "PROD", frozen: Boolean = false) =
        Snapshot("0123456789abcdef", "7K3M-9PQ2-XH4T-V8RM", ed, n, nb, m, mb, 7, 1_790_000_000_000L, Snapshot.Flags(frozen, true, true))
    private fun policy(bp: Long = 200) = PolicyView(1000, bp, true, true, true, true, true, 5000, 50)

    @Test fun feeTextIsAPercentageInFrench() {
        assertEquals("sans frais", WalletTexts.feeText(0)); assertEquals("2 %", WalletTexts.feeText(200)); assertEquals("1,5 %", WalletTexts.feeText(150)); assertEquals("0,25 %", WalletTexts.feeText(25))
        assertEquals("10 %", WalletTexts.feeText(1000))
    }

    @Test fun directionLabelsShowTheRateAndTheFeeFromThePolicy() {
        assertEquals("NDEM → MBOKO : 1${sp}000 NDEM = 1 MBOKO", WalletTexts.directionLabel(ConvertDir.N2M, policy()))
        assertEquals("MBOKO → NDEM : 1 MBOKO = 1${sp}000 NDEM, frais 2 %", WalletTexts.directionLabel(ConvertDir.M2N, policy()))
        assertEquals("MBOKO → NDEM : 1 MBOKO = 1${sp}000 NDEM, sans frais", WalletTexts.directionLabel(ConvertDir.M2N, policy(bp = 0)))
        assertEquals("NDEM → MBOKO", WalletTexts.directionLabel(ConvertDir.N2M, null)); assertEquals("MBOKO → NDEM", WalletTexts.directionLabel(ConvertDir.M2N, null))
    }

    @Test fun editionLabels() {
        assertEquals("Édition d'essai", WalletTexts.editionLabel("TRIAL")); assertEquals("Édition de production", WalletTexts.editionLabel("PROD"))
        assertEquals("Édition illimitée", WalletTexts.editionLabel("UNLIMITED")); assertEquals("Aucune édition active", WalletTexts.editionLabel("NONE"))
        assertEquals("Édition : AUTRE", WalletTexts.editionLabel("AUTRE"))
    }

    @Test fun notesSayWhatIsPendingOrInGraceOrFrozen() {
        assertEquals(emptyList(), WalletTexts.notes(EditionInfo("PROD", "ACTIVE", false, false), emptyList(), snap()))
        // licence en attente : dit par WalletStatus (bandeau), pas répété ici
        assertEquals(emptyList(), WalletTexts.notes(EditionInfo("PROD", "PENDING", false, false), listOf(Notice("LICENSE_PENDING", "Licence en attente d'enregistrement")), snap()))
        assertEquals(listOf("Licence en période de grâce : renouvelez-la"), WalletTexts.notes(EditionInfo("PROD", "EXPIRED", true, false), emptyList(), snap()))
        assertEquals(listOf("Licence suspendue"), WalletTexts.notes(EditionInfo("NONE", "SUSPENDED", false, false), emptyList(), null))
        assertEquals(listOf("Licence révoquée"), WalletTexts.notes(EditionInfo("NONE", "REVOKED", false, false), emptyList(), null))
        assertEquals(listOf("Ce compte est lié à une autre TV"), WalletTexts.notes(EditionInfo("PROD", "OTHER_TV", false, true), emptyList(), snap()))
        assertEquals(listOf("Compte en vérification : contactez votre point focal"), WalletTexts.notes(null, emptyList(), snap(frozen = true)))
        assertEquals(listOf("Vérifiez l'heure de la TV"), WalletTexts.notes(null, listOf(Notice("CLOCK", "Vérifiez l'heure de la TV")), null))
    }

    @Test fun pocketsAreShownAsSigned() {
        assertEquals(listOf("NDEM : 3${sp}450 disponibles · 0 bloqués en mise", "MBOKO : 12 disponibles · 0 bloqués en mise"), WalletTexts.pocketLines(snap()))
        assertEquals(listOf("NDEM : 100 disponibles · 40 bloqués en mise", "MBOKO : 0 disponibles · 2 bloqués en mise"), WalletTexts.pocketLines(snap(n = 100, nb = 40, m = 0, mb = 2)))
    }

    @Test fun previewLinesShowBeforeAndAfter() {
        val p = ConvertPreview.of(ConvertDir.M2N, 5, policy(), snap(n = 100, m = 8))
        assertEquals(listOf("Vous payez : 5 MBOKO", "Vous recevez : 4${sp}900 NDEM", "Frais : 100 NDEM", "Avant : 100 NDEM · 8 MBOKO", "Après : 5${sp}000 NDEM · 3 MBOKO"), WalletTexts.previewLines(p))
        val n = ConvertPreview.of(ConvertDir.N2M, 12, policy(), snap(n = 15_000, m = 3))
        assertEquals(listOf("Vous payez : 12${sp}000 NDEM", "Vous recevez : 12 MBOKO", "Avant : 15${sp}000 NDEM · 3 MBOKO", "Après : 3${sp}000 NDEM · 15 MBOKO"), WalletTexts.previewLines(n))
        val short = ConvertPreview.of(ConvertDir.N2M, 16, policy(), snap(n = 15_000, m = 3))
        assertEquals("Solde insuffisant : 15${sp}000 NDEM disponibles", WalletTexts.previewLines(short).last())
    }

    @Test fun confirmationsGiveDigitsAndFrenchWords() {
        val m2n = ConvertPreview.of(ConvertDir.M2N, 5, policy(), snap(n = 100, m = 8))
        assertEquals(listOf("Vous payez 5 MBOKO (cinq)", "et recevez 4${sp}900 NDEM (quatre mille neuf cents)", "OK : confirmer"), WalletTexts.convertConfirm(ConvertDir.M2N, 5, m2n))
        val n2m = ConvertPreview.of(ConvertDir.N2M, 12, policy(), snap(n = 15_000, m = 3))
        assertEquals(listOf("Vous payez 12${sp}000 NDEM (douze mille)", "et recevez 12 MBOKO (douze)", "OK : confirmer"), WalletTexts.convertConfirm(ConvertDir.N2M, 12, n2m))
        assertEquals(listOf("Envoyer 50 NDEM (cinquante)", "au code R123-4567-89", "OK : confirmer"), WalletTexts.sendConfirm(WalletCurrency.NDEM, 50, "R123-4567-89"))
        assertEquals(listOf("Envoyer 3${sp}450 MBOKO (trois mille quatre cent cinquante)", "au code R123-4567-89", "OK : confirmer"), WalletTexts.sendConfirm(WalletCurrency.MBOKO, 3450, "R123-4567-89"))
    }

    @Test fun doneMessages() {
        assertEquals("Conversion faite : vous avez payé 5 MBOKO et reçu 4${sp}900 NDEM (frais : 100 NDEM)", WalletTexts.convertDone(ConvertDone("M2N", 5, 1000, 200, 5000, 100, 4900, false, null)))
        assertEquals("Conversion faite : vous avez payé 12${sp}000 NDEM et reçu 12 MBOKO", WalletTexts.convertDone(ConvertDone("N2M", 12, 1000, 200, 12000, 0, 12000, false, null)))
        assertEquals("Déjà fait : conversion de 12 MBOKO déjà enregistrée", WalletTexts.convertDone(ConvertDone("N2M", 12, 1000, 200, 12000, 0, 12000, true, null)))
        assertEquals("Envoyé : 50 NDEM à TV …4F2Q", WalletTexts.transferDone(TransferDone("NDEM", 50, "TV …4F2Q", false, null)))
        assertEquals("Envoyé : 2 MBOKO", WalletTexts.transferDone(TransferDone("MBOKO", 2, null, false, null)))
        assertEquals("Déjà fait : envoi de 50 NDEM déjà enregistré", WalletTexts.transferDone(TransferDone("NDEM", 50, "TV …4F2Q", true, null)))
    }

    @Test fun historyLines() {
        val t = 1_790_000_000_000L
        assertEquals("21/09 14:13 · Conversion de jetons · −5${sp}000 NDEM", WalletTexts.historyLine(HistoryLine(1, "CONVERT", "NDEM", -5000, t, "Conversion de jetons", null), ZoneOffset.UTC))
        assertEquals("21/09 14:13 · Transfert reçu · +2 MBOKO · TV …4F2Q", WalletTexts.historyLine(HistoryLine(2, "TRANSFER", "MBOKO", 2, t, "Transfert reçu", "TV …4F2Q"), ZoneOffset.UTC))
        assertTrue(WalletTexts.historyLine(HistoryLine(3, "X", "NDEM", 0, t, "Opération", null), ZoneOffset.UTC).endsWith("0 NDEM"))
    }

    @Test fun expiryCountdown() {
        val now = 1_790_000_000_000L
        assertEquals("Valable encore 9 min 30 s", WalletTexts.expiry(now + 570_000, now))
        assertEquals("Valable encore 45 s", WalletTexts.expiry(now + 45_000, now))
        assertEquals("Valable encore 1 min", WalletTexts.expiry(now + 60_000, now))
        assertEquals("Code expiré : demandez-en un nouveau", WalletTexts.expiry(now, now))
        assertEquals("Code expiré : demandez-en un nouveau", WalletTexts.expiry(now - 5, now))
    }
}
