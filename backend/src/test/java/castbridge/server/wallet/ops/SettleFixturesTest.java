package castbridge.server.wallet.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import castbridge.server.wallet.core.Currency;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@code POST /api/v1/wallet/settle} : des {@code cbr1} signés (clé de résultat de test, même format que le service de jeu) réglés sur des blocages créés par l'API. Un seul règlement par
 * résultat, tout ou rien, rien n'est créé, rien ne se règle sur la parole d'un résultat non authentique.
 */
class SettleFixturesTest extends OpsTestBase {

    /** Deux TV d'essai qui bloquent chacune {@code per × k} NDEM. */
    private record Duo(Tv a, Tv b, String eidA, String eidB) {}

    private Duo duo(String idem, long per, int kA, int kB) throws Exception {
        Tv a = trialTv(), b = trialTv();
        String ea = escrow(a, "NDEM", per, kA, idem).json().get("eid").asText();
        String eb = escrow(b, "NDEM", per, kB, idem).json().get("eid").asText();
        return new Duo(a, b, ea, eb);
    }

    @Test
    void endSettlesEveryEscrowInOneTransactionAndReturnsTheUnusedPart() throws Exception {
        Duo d = duo("g1-0001", 10, 3, 2);   // A bloque 30, B bloque 20 ; A n'a que 2 sièges présents
        assertEquals(70, bal(d.a(), Currency.NDEM));
        assertEquals(80, bal(d.b(), Currency.NDEM));
        String t = cbr1(RESULT, rid(1), "NDEM", 10, "END", List.of(new Line(d.eidA(), d.a().code(), 20, 30), new Line(d.eidB(), d.b().code(), 20, 10)));
        Reply r = settle(t);
        assertEquals(200, r.status(), r.json().toString());
        assertEquals("SETTLED", r.json().get("status").asText());
        assertEquals(110, bal(d.a(), Currency.NDEM), "70 + 10 non utilisé + 30 gagné");
        assertEquals(90, bal(d.b(), Currency.NDEM), "80 + 10 gagné");
        assertEquals(0, locked(d.a(), Currency.NDEM));
        assertEquals(0, locked(d.b(), Currency.NDEM));
        assertEquals(1, newTxns("SETTLE"));
        assertEquals(0, sys("SYS:POT", "NDEM"), "la cagnotte revient à 0");
        assertEquals("SETTLED", jdbc.queryForObject("SELECT state FROM wallet_escrow WHERE eid = ?", String.class, d.eidA()));
        assertEquals(rid(1), jdbc.queryForObject("SELECT settled_rid FROM wallet_escrow WHERE eid = ?", String.class, d.eidB()));
        assertReconciled();
    }

    @Test
    void repostingFiveTimesChangesNothingAndAnswersTheSame() throws Exception {
        Duo d = duo("g1-0002", 10, 2, 2);
        String t = cbr1(RESULT, rid(2), "NDEM", 10, "END", List.of(new Line(d.eidA(), d.a().code(), 20, 40), new Line(d.eidB(), d.b().code(), 20, 0)));
        Reply first = settle(t);
        assertEquals(200, first.status());
        long a = bal(d.a(), Currency.NDEM), b = bal(d.b(), Currency.NDEM);
        for (int i = 0; i < 5; i++) {
            Reply again = settle(t);
            assertEquals(200, again.status());
            assertEquals(first.json(), again.json(), "même réponse au rejeu");
        }
        assertEquals(a, bal(d.a(), Currency.NDEM));
        assertEquals(b, bal(d.b(), Currency.NDEM));
        assertEquals(1, newTxns("SETTLE"));
        assertEquals(1, count("SELECT COUNT(*) FROM wallet_result WHERE rid = ?", rid(2)));
        // même identifiant de résultat, autre contenu (même clé de service) : refusé, rien ne bouge
        String other = cbr1(RESULT, rid(2), "NDEM", 10, "END", List.of(new Line(d.eidA(), d.a().code(), 20, 0), new Line(d.eidB(), d.b().code(), 20, 40)));
        Reply conflict = settle(other);
        assertEquals(409, conflict.status());
        assertEquals("IDEM_CONFLICT", conflict.reason());
        assertEquals(a, bal(d.a(), Currency.NDEM));
    }

    @Test
    void aResultWhoseTotalsDisagreeIsRefusedAndNothingMoves() throws Exception {
        Duo d = duo("g1-0003", 10, 2, 2);
        String t = cbr1(RESULT, rid(3), "NDEM", 10, "END", List.of(new Line(d.eidA(), d.a().code(), 20, 41), new Line(d.eidB(), d.b().code(), 20, 0)));
        Reply r = settle(t);
        assertEquals(400, r.status());
        assertEquals("UNBALANCED", r.reason());
        assertEquals(80, bal(d.a(), Currency.NDEM));
        assertEquals(20, locked(d.a(), Currency.NDEM));
        assertEquals(0, newTxns("SETTLE"));
        assertEquals("OPEN", jdbc.queryForObject("SELECT state FROM wallet_escrow WHERE eid = ?", String.class, d.eidA()));
        // un résultat refusé ne « brûle » pas son rid : le bon résultat avec le même rid passe ensuite
        assertEquals(200, settle(cbr1(RESULT, rid(3), "NDEM", 10, "END", List.of(new Line(d.eidA(), d.a().code(), 20, 20), new Line(d.eidB(), d.b().code(), 20, 20)))).status());
    }

    @Test
    void aResultSignedByAnUnknownKeyIsRefusedButTheSecondRotationKeyIsAccepted() throws Exception {
        Duo d = duo("g1-0004", 10, 1, 1);
        List<Line> lines = List.of(new Line(d.eidA(), d.a().code(), 10, 20), new Line(d.eidB(), d.b().code(), 10, 0));
        Reply bad = settle(cbr1(STRANGER, rid(4), "NDEM", 10, "END", lines));
        assertEquals(403, bad.status());
        assertEquals(90, bal(d.a(), Currency.NDEM));
        assertEquals(0, newTxns("SETTLE"));
        // la clé du portefeuille ne signe jamais un résultat
        assertEquals(403, settle(cbr1(WALLET, rid(4), "NDEM", 10, "END", lines)).status());
        assertEquals(200, settle(cbr1(RESULT_2, rid(4), "NDEM", 10, "END", lines)).status());
        assertEquals(110, bal(d.a(), Currency.NDEM));
    }

    @Test
    void anUnknownEidRefusesTheWholeResultNoPartialSettlement() throws Exception {
        Duo d = duo("g1-0005", 10, 2, 2);
        String t = cbr1(RESULT, rid(5), "NDEM", 10, "END", List.of(new Line(d.eidA(), d.a().code(), 20, 20), new Line("ZZZZZZZZZZZZZZZZZZZZZZ", d.b().code(), 20, 20)));
        Reply r = settle(t);
        assertEquals(409, r.status());
        assertEquals("ESCROW_UNKNOWN", r.reason());
        assertEquals(80, bal(d.a(), Currency.NDEM));
        assertEquals(20, locked(d.a(), Currency.NDEM));
        assertEquals("OPEN", jdbc.queryForObject("SELECT state FROM wallet_escrow WHERE eid = ?", String.class, d.eidA()));
        assertEquals(0, newTxns("SETTLE"));
    }

    @Test
    void currencyPerAndHolderMustMatchTheEscrow() throws Exception {
        Duo d = duo("g1-0006", 10, 2, 2);
        List<Line> ok = List.of(new Line(d.eidA(), d.a().code(), 20, 20), new Line(d.eidB(), d.b().code(), 20, 20));
        assertEquals(400, settle(cbr1(RESULT, rid(6), "MBOKO", 10, "END", ok)).status(), "autre monnaie");
        assertEquals(400, settle(cbr1(RESULT, rid(7), "NDEM", 20, "END", ok)).status(), "autre mise par siège : 20 ≠ 10 × 2");
        assertEquals(400, settle(cbr1(RESULT, rid(8), "NDEM", 10, "END", List.of(new Line(d.eidA(), d.b().code(), 20, 20), new Line(d.eidB(), d.a().code(), 20, 20)))).status(), "autre titulaire");
        assertEquals(400, settle(cbr1(RESULT, rid(9), "NDEM", 10, "END", List.of(new Line(d.eidA(), d.a().code(), 25, 25), new Line(d.eidB(), d.b().code(), 20, 20)))).status(), "utilisé > bloqué");
        assertEquals(400, settle(cbr1(RESULT, rid(10), "NDEM", 10, "END", List.of(new Line(d.eidA(), d.a().code(), 15, 15), new Line(d.eidB(), d.b().code(), 20, 20)))).status(), "utilisé pas multiple de la mise");
        assertEquals(400, settle(cbr1(RESULT, rid(11), "NDEM", 10, "END", List.of(new Line(d.eidA(), d.a().code(), 20, 20), new Line(d.eidA(), d.a().code(), 20, 20)))).status(), "blocage en double");
        assertEquals(0, newTxns("SETTLE"));
        assertEquals(200, settle(cbr1(RESULT, rid(12), "NDEM", 10, "END", ok)).status());
    }

    @Test
    void abortGivesEveryEscrowBackInFullAndCannotMoveValue() throws Exception {
        Duo d = duo("g1-0007", 10, 2, 2);
        Reply moves = settle(cbr1(RESULT, rid(20), "NDEM", 10, "ABORT", List.of(new Line(d.eidA(), d.a().code(), 20, 30), new Line(d.eidB(), d.b().code(), 20, 10))));
        assertEquals(400, moves.status(), "un ABORT qui déplace de la valeur");
        assertEquals(20, locked(d.a(), Currency.NDEM));
        Reply r = settle(cbr1(RESULT, rid(21), "NDEM", 10, "ABORT", List.of(new Line(d.eidA(), d.a().code(), 0, 0), new Line(d.eidB(), d.b().code(), 0, 0))));
        assertEquals(200, r.status(), r.json().toString());
        assertEquals(100, bal(d.a(), Currency.NDEM));
        assertEquals(100, bal(d.b(), Currency.NDEM));
        assertEquals(0, locked(d.a(), Currency.NDEM));
        assertReconciled();
    }

    @Test
    void anEscrowAlreadySettledByAnotherResultIsClosed() throws Exception {
        Duo d = duo("g1-0008", 10, 1, 1);
        assertEquals(200, settle(cbr1(RESULT, rid(30), "NDEM", 10, "END", List.of(new Line(d.eidA(), d.a().code(), 10, 20), new Line(d.eidB(), d.b().code(), 10, 0)))).status());
        Reply second = settle(cbr1(RESULT, rid(31), "NDEM", 10, "END", List.of(new Line(d.eidA(), d.a().code(), 10, 0), new Line(d.eidB(), d.b().code(), 10, 20))));
        assertEquals(409, second.status());
        assertEquals("ESCROW_CLOSED", second.reason());
        assertEquals(110, bal(d.a(), Currency.NDEM));
        assertEquals(90, bal(d.b(), Currency.NDEM));
        assertEquals(1, newTxns("SETTLE"));
    }

    @Test
    void mboko_escrows_settle_the_same_way() throws Exception {
        Tv a = productionTv(), b = productionTv();
        String ea = escrow(a, "MBOKO", 2, 2, "g1-0009").json().get("eid").asText();
        String eb = escrow(b, "MBOKO", 2, 2, "g1-0009").json().get("eid").asText();
        assertEquals(200, settle(cbr1(RESULT, rid(40), "MBOKO", 2, "END", List.of(new Line(ea, a.code(), 4, 5), new Line(eb, b.code(), 4, 3)))).status());
        assertEquals(11, bal(a, Currency.MBOKO));
        assertEquals(9, bal(b, Currency.MBOKO));
        assertReconciled();
    }

    @Test
    void garbageTamperedAndWrappedTokens() throws Exception {
        Duo d = duo("g1-0010", 10, 1, 1);
        String t = cbr1(RESULT, rid(50), "NDEM", 10, "END", List.of(new Line(d.eidA(), d.a().code(), 10, 10), new Line(d.eidB(), d.b().code(), 10, 10)));
        assertEquals(400, settle("").status());
        assertEquals(400, settle("pas un résultat").status());
        assertEquals(400, settle("cbr1.AAAA.BBBB").status());
        String[] p = t.split("\\.");
        char c = p[1].charAt(20) == 'A' ? 'B' : 'A';
        int forged = settle(p[0] + "." + p[1].substring(0, 20) + c + p[1].substring(21) + "." + p[2]).status();
        assertTrue(forged == 400 || forged == 403, "charge modifiée : " + forged);
        assertEquals(0, newTxns("SETTLE"));
        // le jeton peut aussi arriver enveloppé {"cbr1": "..."}
        Reply r = reply(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/wallet/settle").contentType("application/json")
                .content("{\"cbr1\":\"" + t + "\"}")).andReturn());
        assertEquals(200, r.status(), r.json().toString());
        // charge anormale mais bien signée : clé en plus
        String extra = sign(RESULT, "{\"kid\":\"x\",\"rid\":\"" + rid(51) + "\",\"extra\":1}");
        assertEquals(400, settle(extra).status());
    }

    @Test
    void moreThanSixteenLinesAreRefused() throws Exception {
        java.util.ArrayList<Line> lines = new java.util.ArrayList<>();
        for (int i = 0; i < 17; i++) lines.add(new Line(String.format("%022d", i), "AAAA-BBBB-CCCC-DDDD", 1, 1));
        assertEquals(400, settle(cbr1(RESULT, rid(60), "NDEM", 1, "END", lines)).status());
    }
}
