package castbridge.core.wallet.ui

import castbridge.core.wallet.WalletCurrency
import castbridge.core.wallet.WalletReason
import castbridge.core.wallet.ui.WalletMessages.Kind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WalletMessagesTest {
    @Test fun knownReasonsUseTheSharedFrenchText() {
        WalletReason.values().filter { it != WalletReason.INSUFFICIENT }.forEach {
            val s = WalletMessages.of(409, it.name)
            assertEquals(it.text(), s.text, it.name)
            assertEquals(it.name, s.code)
        }
        assertEquals("Solde insuffisant : 120 NDEM disponibles", WalletMessages.of(409, "INSUFFICIENT", available = 120, cur = WalletCurrency.NDEM).text)
        assertEquals("Solde insuffisant : 3 450 MBOKO disponibles", WalletMessages.of(409, "INSUFFICIENT", available = 3450, cur = WalletCurrency.MBOKO).text)
        assertEquals("Solde insuffisant", WalletMessages.of(409, "INSUFFICIENT").text)
    }

    @Test fun reasonsOnlyTheServerEmitsHaveTheirOwnText() {
        val expected = mapOf(
            "LICENSE_PENDING" to "Licence en attente d'enregistrement",
            "CONVERT_SUSPENDED" to "Conversion suspendue pour maintenance : réessayez plus tard",
            "TRANSFER_SUSPENDED" to "Transferts suspendus pour maintenance : réessayez plus tard",
            "TRIAL_LIMIT" to "Limite de la version d'essai atteinte : le transfert s'ouvrira plus tard",
            "RATE_LIMIT" to "Trop d'opérations à la suite : réessayez dans un instant",
            "SETTLE_CAP" to "Plafond de gains atteint : le règlement est reporté, contactez votre point focal",
            "RESULT_AFTER_REFUND" to "Cette partie a été remboursée : aucun gain à régler",
            "CODE_LIMIT" to "Trois codes de réception sont déjà actifs : attendez l'expiration de l'un d'eux",
            "LOOKUP_LIMIT" to "Trop de codes essayés cette heure : réessayez plus tard",
            "IDEM_CONFLICT" to "Cette opération a déjà été envoyée avec d'autres valeurs : recommencez",
        )
        expected.forEach { (reason, text) ->
            val s = WalletMessages.of(409, reason)
            assertEquals(text, s.text, reason); assertEquals(reason, s.code); assertTrue(s.kind != Kind.UNKNOWN, reason)
        }
    }

    @Test fun activateAndBindProofAreHandledWithClearTexts() {
        val a = WalletMessages.of(409, "ACTIVATE")
        assertEquals(Kind.ACTIVATE, a.kind); assertEquals("Activez la TV pour recevoir des jetons", a.text)
        val b = WalletMessages.of(409, "BIND_PROOF")
        assertEquals(Kind.BIND_PROOF, b.kind)
        assertEquals("Cette TV doit prouver qu'elle est bien la sienne : vérifiez l'heure de la TV, puis réessayez", b.text)
    }

    @Test fun unknownOrNewReasonsNeverCrashAndAreShownWithTheirCode() {
        val s = WalletMessages.of(409, "NEW_THING")
        assertEquals("Opération refusée (code NEW_THING)", s.text); assertEquals("NEW_THING", s.code); assertEquals(Kind.UNKNOWN, s.kind)
        // motif étrange : jamais de plantage, code assaini ou absent
        val weird = listOf("", " ", "x".repeat(500), "a\nb", "<script>", "é", "lower_case", "ESCROW_UNKNOWN2", null)
        weird.forEach { r ->
            val x = WalletMessages.of(409, r)
            assertTrue(x.text.isNotBlank(), "reason=$r")
            assertTrue(x.code == null || (x.code.length <= 40 && x.code.all { c -> c in 'A'..'Z' || c in '0'..'9' || c == '_' }), "reason=$r code=${x.code}")
        }
        assertNull(WalletMessages.of(409, "<script>").code)
        assertEquals("Opération refusée", WalletMessages.of(409, null).text)
    }

    @Test fun statusAloneIsMapped() {
        assertEquals(WalletMessages.UNAVAILABLE_TEXT, WalletMessages.of(404, null).text)
        assertEquals(Kind.UNAVAILABLE, WalletMessages.of(404, null).kind)
        assertEquals(Kind.UNAVAILABLE, WalletMessages.of(503, null).kind)
        assertEquals("Jetons : service indisponible", WalletMessages.of(503, null).text)
        assertEquals(Kind.NETWORK, WalletMessages.of(503, "OFFLINE").kind)       // le motif du serveur l'emporte sur le statut
        assertEquals("Trop d'opérations à la suite : réessayez dans un instant", WalletMessages.of(429, null).text)
        assertEquals(Kind.RETRY_LATER, WalletMessages.of(401, null).kind)
        assertEquals(Kind.RETRY_LATER, WalletMessages.of(500, null).kind)
        assertEquals("Le serveur ne répond pas correctement : réessayez plus tard", WalletMessages.of(500, null).text)
        assertEquals("Code d'appareil invalide", WalletMessages.of(400, null, "Code d'appareil invalide").text)
        assertEquals("Demande refusée", WalletMessages.of(400, null).text)
    }

    @Test fun offlineText() {
        val o = WalletMessages.offline()
        assertEquals("Connexion Internet nécessaire", o.text); assertEquals(Kind.NETWORK, o.kind)
        assertFalse(o.text.contains("code"))
    }
}
