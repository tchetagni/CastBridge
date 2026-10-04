package castbridge.core.wallet

import java.io.File
import java.time.ZoneOffset
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WalletViewTest {
    private fun snap(n: Long, m: Long, at: Long = WalletTestKeys.NOW) = Snapshot("k", "tv", "PROD", n, 0, m, 0, 1, at, Snapshot.Flags(false, true, true))

    @Test fun thousandsUseNarrowNoBreakSpaces() {
        assertEquals("0", WalletView.thousands(0)); assertEquals("999", WalletView.thousands(999))
        assertEquals("1 000", WalletView.thousands(1000)); assertEquals("3 450", WalletView.thousands(3450))
        assertEquals("1 234 567", WalletView.thousands(1_234_567))
        assertFalse(WalletView.thousands(1_234_567).contains(' '))
    }

    @Test fun cardTextsMatchTheDesign() {
        val s = snap(3450, 12)
        assertEquals("3 450 NDEM · 12 MBOKO", WalletView.balanceLine(s))
        assertEquals("au 21/09 14:13", WalletView.asOf(s.at, ZoneOffset.UTC))
        assertEquals("au 21/09 15:13", WalletView.asOf(s.at, ZoneId.of("Africa/Douala")))
        assertEquals("+500 en attente", WalletView.pendingLine(500, 0))
        assertEquals("+5 MBOKO en attente", WalletView.pendingLine(0, 5))
        assertEquals("+500 NDEM · +5 MBOKO en attente", WalletView.pendingLine(500, 5))
        assertNull(WalletView.pendingLine(0, 0))
        assertEquals(listOf("3 450 NDEM · 12 MBOKO", "au 21/09 14:13 · +500 en attente"), WalletView.cardLines(s, 500, 0, ZoneOffset.UTC))
        assertEquals(listOf("3 450 NDEM · 12 MBOKO", "au 21/09 14:13"), WalletView.cardLines(s, 0, 0, ZoneOffset.UTC))
        assertEquals(listOf("Soldes inconnus", "+500 en attente"), WalletView.cardLines(null, 500, 0, ZoneOffset.UTC))
        assertEquals("Hors ligne : soldes au 21/09 14:13", WalletView.offlineLine(s, ZoneOffset.UTC))
    }

    @Test fun amountsInWordsUpToTenThousand() {
        assertEquals("3 450 NDEM (trois mille quatre cent cinquante)", WalletView.confirmation(3450, WalletCurrency.NDEM))
        assertEquals("1 MBOKO (un)", WalletView.confirmation(1, WalletCurrency.MBOKO))
        assertEquals("10 000 NDEM (dix mille)", WalletView.confirmation(10_000, WalletCurrency.NDEM))
        assertEquals("10 001 NDEM", WalletView.confirmation(10_001, WalletCurrency.NDEM))                 // au-delà : chiffres seuls
        assertNull(WalletView.words(10_001))
    }

    @Test fun reasonsCarryTheFrenchTextsOfTheDesign() {
        assertEquals(
            listOf("INSUFFICIENT", "OFFLINE", "BOUND_OTHER_TV", "TRIAL_NO_MBOKO", "STAKES_SUSPENDED", "DAILY_CAP", "CODE_UNKNOWN", "CODE_EXPIRED", "VOUCHER_USED", "VOUCHER_OTHER_TV",
                "VOUCHER_EXPIRED", "VOUCHER_BAD", "FROZEN", "ACTIVATE", "CLOCK"), WalletReason.values().map { it.name })
        assertEquals("Solde insuffisant : 120 NDEM disponibles", WalletReason.INSUFFICIENT.text(120))
        assertEquals("Solde insuffisant : 1 200 MBOKO disponibles", WalletReason.INSUFFICIENT.text(1200, WalletCurrency.MBOKO))
        assertEquals("Solde insuffisant", WalletReason.INSUFFICIENT.text())
        assertEquals("Connexion Internet nécessaire", WalletReason.OFFLINE.text())
        assertEquals("Ce bon est destiné à une autre TV", WalletReason.VOUCHER_OTHER_TV.text())
        assertEquals("Vérifiez l'heure de la TV", WalletReason.CLOCK.text())
        for (r in WalletReason.values()) {
            val t = r.text(1)
            assertTrue(t.isNotBlank() && !t.contains("sender", true) && !t.contains("receiver", true), t)
        }
        // chaque refus de pièce qui a un écran pointe vers un motif existant
        assertEquals(WalletReason.VOUCHER_BAD, WalletRefusal.VOUCHER_BAD.shown)
        assertEquals(WalletReason.BOUND_OTHER_TV, WalletRefusal.OTHER_TV.shown)
    }

    /** La TV ne crée jamais de valeur : aucune opération du paquet n'augmente un solde à partir d'une entrée locale (test de source). */
    @Test fun noPublicOperationRaisesABalance() {
        val dir = File("src/main/kotlin/castbridge/core/wallet")
        val files = dir.listFiles { f -> f.name.endsWith(".kt") }!!
        assertTrue(files.size >= 9, "sources du paquet introuvables : ${dir.absolutePath}")
        val forbiddenFun = Regex("""fun\s+(credit|add|mint|deposit|increase|topUp|refill|grant|award|setBalance|setN|setM)\w*\s*\(""", RegexOption.IGNORE_CASE)
        val mutableBalance = Regex("""\bvar\s+(n|nb|m|mb|ndem|mboko|balance|available)\b""")
        for (f in files) {
            val text = f.readText().lines().filterNot { it.trim().startsWith("*") || it.trim().startsWith("//") || it.trim().startsWith("/*") }.joinToString("\n")
            assertFalse(forbiddenFun.containsMatchIn(text), "${f.name} : opération qui crédite")
            assertFalse(mutableBalance.containsMatchIn(text), "${f.name} : solde modifiable")
            assertFalse(Regex("""\bjava\.net\.|\bjava\.io\.File\b|HttpURLConnection|nanohttpd""").containsMatchIn(text), "${f.name} : réseau ou fichier dans le cœur pur")
        }
        // le paquet n'embarque aucune clé privée : seul PlayResult.sign signe, pour le service de jeu
        assertFalse(files.filter { it.name != "PlayResult.kt" }.any { Regex("""\.sign\(|Signer""").containsMatchIn(it.readText().lines().filterNot { l -> l.trim().startsWith("*") }.joinToString("\n")) && it.name != "WalletFormats.kt" })
    }
}
