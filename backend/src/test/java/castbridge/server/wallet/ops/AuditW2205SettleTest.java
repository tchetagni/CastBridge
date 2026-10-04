package castbridge.server.wallet.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import castbridge.server.wallet.core.Currency;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Audit w22-05 : M3 (interrupteur à chaud, plafonds, alerte), F1 (pas de gain sans siège joué), motif précis d'un titulaire faux. */
class AuditW2205SettleTest extends OpsTestBase {

    @AfterEach
    void restore() {
        setPolicy("switch.settle", 1);
        setPolicy("settle.maxPerSettle.NDEM", 20_000);
        setPolicy("settle.maxPerSettle.MBOKO", 1_000);
        setPolicy("settle.dailyMax.NDEM", 5_000_000);
        setPolicy("settle.dailyMax.MBOKO", 50_000);
        setPolicy("settle.alert.MBOKO", 200);
        jdbc.update("DELETE FROM wallet_alert");
    }

    private record Duo(Tv a, Tv b, String eidA, String eidB) {}

    private Duo duo(String idem, long per) throws Exception {
        Tv a = trialTv(), b = trialTv();
        return new Duo(a, b, escrow(a, "NDEM", per, 1, idem).json().get("eid").asText(), escrow(b, "NDEM", per, 1, idem).json().get("eid").asText());
    }

    @Test
    void m3_theSettleSwitchStopsEverythingAtOnceAndComesBack() throws Exception {
        Duo d = duo("m3-0001", 10);
        String t = cbr1(RESULT, rid(101), "NDEM", 10, "END", List.of(new Line(d.eidA(), d.a().code(), 10, 20), new Line(d.eidB(), d.b().code(), 10, 0)));
        setPolicy("switch.settle", 0);
        Reply off = settle(t);
        assertEquals(503, off.status(), off.json().toString());
        assertEquals(0, newTxns("SETTLE"));
        assertEquals(10, locked(d.a(), Currency.NDEM), "rien n'est réglé : le collecteur réessaiera");
        setPolicy("switch.settle", 1);
        assertEquals(200, settle(t).status());
        assertEquals(1, newTxns("SETTLE"));
    }

    /** p5 / F1 : un blocage dont aucun siège n'a joué (used = 0) ne reçoit rien. */
    @Test
    void p5_aStakeWithNoPlayedSeatIsNotPaid() throws Exception {
        Duo d = duo("f1-0001", 10);
        String t = cbr1(RESULT, rid(102), "NDEM", 10, "END", List.of(new Line(d.eidA(), d.a().code(), 0, 10), new Line(d.eidB(), d.b().code(), 10, 0)));
        Reply r = settle(t);
        assertEquals(400, r.status(), "reçu " + r.status() + " " + r.json());
        assertEquals("BAD_TXN", r.reason());
        assertEquals(0, newTxns("SETTLE"));
        assertEquals(10, locked(d.a(), Currency.NDEM));
    }

    @Test
    void m3_aSettlementAbovePerSettleCapIsRefused() throws Exception {
        Duo d = duo("m3-0002", 10);
        setPolicy("settle.maxPerSettle.NDEM", 15);
        String t = cbr1(RESULT, rid(103), "NDEM", 10, "END", List.of(new Line(d.eidA(), d.a().code(), 10, 20), new Line(d.eidB(), d.b().code(), 10, 0)));
        Reply r = settle(t);
        assertEquals(409, r.status(), r.json().toString());
        assertEquals("SETTLE_CAP", r.reason());
        assertEquals(0, newTxns("SETTLE"));
        assertEquals(1, count("SELECT COUNT(*) FROM wallet_alert WHERE kind = 'SETTLE_CAP'"), "le refus laisse une alerte");
        setPolicy("settle.maxPerSettle.NDEM", 20);
        assertEquals(200, settle(t).status(), "à la limite : permis");
    }

    @Test
    void m3_theDailyCapCountsWhatWasPaidInTheLast24Hours() throws Exception {
        long before = count("SELECT COALESCE(SUM(paid), 0) FROM wallet_result WHERE cur = 'NDEM' AND received_at >= ?", java.sql.Timestamp.from(T0.minusSeconds(86_400)));
        setPolicy("settle.dailyMax.NDEM", before + 30);
        Duo d1 = duo("m3-0003", 10), d2 = duo("m3-0004", 10);
        assertEquals(200, settle(cbr1(RESULT, rid(104), "NDEM", 10, "END", List.of(new Line(d1.eidA(), d1.a().code(), 10, 20), new Line(d1.eidB(), d1.b().code(), 10, 0)))).status());
        Reply r = settle(cbr1(RESULT, rid(105), "NDEM", 10, "END", List.of(new Line(d2.eidA(), d2.a().code(), 10, 20), new Line(d2.eidB(), d2.b().code(), 10, 0))));
        assertEquals(409, r.status(), r.json().toString());
        assertEquals("SETTLE_CAP", r.reason());
        clock.freezeAt(T0.plusSeconds(86_400 + 60));
        assertEquals(200, settle(cbr1(RESULT, rid(105), "NDEM", 10, "END", List.of(new Line(d2.eidA(), d2.a().code(), 10, 20), new Line(d2.eidB(), d2.b().code(), 10, 0)))).status(),
                "la fenêtre est glissante : 24 h plus tard le premier règlement n'est plus compté");
    }

    @Test
    void m3_aLineThatWinsMoreThanTwiceItsStakeAndTheAlertThresholdRaisesAnAlert() throws Exception {
        List<Tv> tvs = new ArrayList<>();
        List<String> eids = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Tv tv = productionTv();
            adminGrant(tv.code(), "MBOKO", 100);
            tvs.add(tv);
            eids.add(escrow(tv, "MBOKO", 100, 1, "m3-al-" + i).json().get("eid").asText());
        }
        List<Line> lines = new ArrayList<>();
        lines.add(new Line(eids.get(0), tvs.get(0).code(), 100, 400));
        for (int i = 1; i < 4; i++) lines.add(new Line(eids.get(i), tvs.get(i).code(), 100, 0));
        setPolicy("settle.maxPerSettle.MBOKO", 1_000);
        Reply r = settle(cbr1(RESULT, rid(106), "MBOKO", 100, "END", lines));
        assertEquals(200, r.status(), r.json().toString());
        assertEquals(1, count("SELECT COUNT(*) FROM wallet_alert WHERE kind = 'SETTLE_GAIN'"), "gain de 400 MBOKO pour une mise de 100 : alerte");
        assertEquals(400L, count("SELECT paid FROM wallet_result WHERE rid = ?", rid(106)));
    }

    @Test
    void anOrdinarySettlementRaisesNoAlert() throws Exception {
        Duo d = duo("m3-0005", 10);
        assertEquals(200, settle(cbr1(RESULT, rid(107), "NDEM", 10, "END", List.of(new Line(d.eidA(), d.a().code(), 10, 20), new Line(d.eidB(), d.b().code(), 10, 0)))).status());
        assertEquals(0, count("SELECT COUNT(*) FROM wallet_alert"));
    }

    /** Mutation survivante : le titulaire du blocage est revérifié avec un motif précis. */
    @Test
    void aLineNamingAnotherHolderIsRefusedWithAPreciseReason() throws Exception {
        Duo d = duo("m3-0006", 10);
        Reply r = settle(cbr1(RESULT, rid(108), "NDEM", 10, "END", List.of(new Line(d.eidA(), d.b().code(), 10, 20), new Line(d.eidB(), d.b().code(), 10, 0))));
        assertEquals(400, r.status());
        assertEquals("BAD_TXN", r.reason());
        assertTrue(r.message().startsWith("Titulaire différent"), r.message());
        assertEquals(0, newTxns("SETTLE"));
    }
}
