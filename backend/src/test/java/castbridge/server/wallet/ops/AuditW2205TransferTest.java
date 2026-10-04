package castbridge.server.wallet.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import castbridge.server.wallet.core.Currency;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Audit w22-05 : M2 (code de réception et plafond dans la transaction du transfert), M5 (limites propres à l'essai), F6 (index, purge), F8 (rejeu avant le gel). */
class AuditW2205TransferTest extends OpsTestBase {

    @AfterEach
    void restorePolicy() {
        setPolicy("switch.transfer", 1);
        setPolicy("transfer.dailyCap.NDEM", 10_000);
        setPolicy("transfer.dailyCap.MBOKO", 100);
        setPolicy("transfer.trial.dailyCap.NDEM", 1_000);
        setPolicy("transfer.trial.dailyCap.MBOKO", 0);
        setPolicy("transfer.trial.minAgeHours", 72);
        setPolicy("transfer.trial.maxDonors", 3);
        setPolicy("transfer.trial.pairCap.NDEM", 5_000);
        setPolicy("transfer.trial.pairCap.MBOKO", 50);
    }

    private String canonical(String code) { return code.replace("-", ""); }

    private String codeOf(Tv tv) throws Exception { return receiveCode(tv).json().get("code").asText(); }

    /** Une TV d'essai dont le compte a l'âge voulu. */
    private Tv agedTrial(int hours) throws Exception {
        Tv tv = trialTv();
        jdbc.update("UPDATE wallet_identity SET created_at = ? WHERE holder = ?", Timestamp.from(T0.minus(Duration.ofHours(hours))), tv.code());
        return tv;
    }

    // ---- M2 ----

    /** p4 : le code n'est jamais marqué sans transfert ; un refus du grand livre laisse le code libre. */
    @Test
    void p4_aCodeIsNeverBurnedWithoutATransfer() throws Exception {
        Tv to = productionTv(), poor = productionTv();
        String code = codeOf(to);
        Reply r = transfer(poor, code, "NDEM", 5_000, "m2-0001");
        assertEquals("INSUFFICIENT", r.reason());
        assertEquals(0, count("SELECT COUNT(*) FROM wallet_recv_code WHERE code = ? AND used_at IS NOT NULL", canonical(code)));
        assertEquals(0, newTxns("TRANSFER"));
        // le réessai avec la même clé après un vrai refus n'est pas « CODE_UNKNOWN »
        adminGrant(poor.code(), "NDEM", 5_000);
        Reply again = transfer(poor, code, "NDEM", 5_000, "m2-0001");
        assertEquals(200, again.status(), again.json().toString());
    }

    @Test
    void theCodeIsMarkedWithTheTransferKeyInTheSameTransaction() throws Exception {
        Tv to = productionTv(), from = productionTv();
        String code = codeOf(to);
        assertEquals(200, transfer(from, code, "NDEM", 10, "m2-0002").status());
        assertEquals("xfer:" + from.code() + ":m2-0002", jdbc.queryForObject("SELECT used_key FROM wallet_recv_code WHERE code = ?", String.class, canonical(code)));
        Timestamp usedAt = jdbc.queryForObject("SELECT used_at FROM wallet_recv_code WHERE code = ?", Timestamp.class, canonical(code));
        Reply replay = transfer(from, code, "NDEM", 10, "m2-0002");
        assertTrue(replay.json().get("replayed").asBoolean());
        assertEquals(usedAt, jdbc.queryForObject("SELECT used_at FROM wallet_recv_code WHERE code = ?", Timestamp.class, canonical(code)), "le rejeu ne touche pas au code");
        assertEquals(1, newTxns("TRANSFER"));
    }

    @Test
    void eightSendersRacingOnOneCodeMakeOneTransfer() throws Exception {
        Tv to = productionTv();
        List<Tv> senders = new ArrayList<>();
        for (int i = 0; i < 8; i++) senders.add(productionTv());
        String code = codeOf(to);
        CyclicBarrier go = new CyclicBarrier(8);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Future<Reply>> fs = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            Tv s = senders.get(i);
            String idem = "race8-" + i;
            fs.add(pool.submit(() -> { go.await(); return transfer(s, code, "NDEM", 7, idem); }));
        }
        int ok = 0;
        for (Future<Reply> f : fs) {
            Reply r = f.get();
            if (r.status() == 200) ok++;
            else assertEquals("CODE_UNKNOWN", r.reason(), r.json().toString());
        }
        pool.shutdown();
        assertEquals(1, ok);
        assertEquals(1, newTxns("TRANSFER"));
        assertReconciled();
    }

    @Test
    void eightParallelTransfersOfOneSenderStayUnderTheDailyCap() throws Exception {
        Tv from = productionTv();
        adminGrant(from.code(), "NDEM", 30_000);
        setPolicy("transfer.dailyCap.NDEM", 10_000);
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < 8; i++) codes.add(codeOf(productionTv()));
        CyclicBarrier go = new CyclicBarrier(8);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Future<Reply>> fs = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            String code = codes.get(i), idem = "cap8-" + i;
            fs.add(pool.submit(() -> { go.await(); return transfer(from, code, "NDEM", 2_500, idem); }));
        }
        int ok = 0;
        for (Future<Reply> f : fs) {
            Reply r = f.get();
            if (r.status() == 200) ok++;
            else assertEquals("DAILY_CAP", r.reason(), r.json().toString());
        }
        pool.shutdown();
        assertEquals(4, ok, "10 000 au plafond : exactement quatre fois 2 500");
        assertEquals(4, count("SELECT COUNT(*) FROM wallet_txn WHERE kind = 'TRANSFER' AND idem_key LIKE ?", "xfer:" + from.code() + ":cap8-%"));
        assertEquals(4, count("SELECT COUNT(*) FROM wallet_recv_code WHERE used_key LIKE ?", "xfer:" + from.code() + ":cap8-%"), "un code consommé par transfert, pas un de plus");
    }

    // ---- M5 ----

    @Test
    void m5_aTrialSenderIsLimitedTo1000NdemAndNoMboko() throws Exception {
        Tv to = productionTv(), trial = agedTrial(100);
        adminGrant(trial.code(), "NDEM", 5_000);
        adminGrant(trial.code(), "MBOKO", 20);
        Reply over = transfer(trial, codeOf(to), "NDEM", 1_001, "m5-0001");
        assertEquals(429, over.status(), over.json().toString());
        assertEquals("DAILY_CAP", over.reason());
        assertEquals(200, transfer(trial, codeOf(to), "NDEM", 1_000, "m5-0002").status(), "pile au plafond d'essai");
        assertEquals("DAILY_CAP", transfer(trial, codeOf(to), "NDEM", 1, "m5-0003").reason());
        Reply mboko = transfer(trial, codeOf(to), "MBOKO", 1, "m5-0004");
        assertEquals(429, mboko.status(), "un essai n'envoie aucun MBOKO");
        assertEquals("DAILY_CAP", mboko.reason());
        assertEquals(1, newTxns("TRANSFER"));
    }

    @Test
    void m5_aTrialAccountYoungerThan72HoursCannotSend() throws Exception {
        Tv to = productionTv(), young = agedTrial(71), old = agedTrial(72);
        Reply r = transfer(young, codeOf(to), "NDEM", 10, "m5-0010");
        assertEquals(409, r.status(), r.json().toString());
        assertEquals("TRIAL_LIMIT", r.reason());
        assertEquals(200, transfer(old, codeOf(to), "NDEM", 10, "m5-0011").status(), "72 h pile : permis");
    }

    @Test
    void m5_aRecipientAcceptsAtMostThreeDistinctTrialDonorsPer24Hours() throws Exception {
        Tv to = productionTv();
        List<Tv> donors = new ArrayList<>();
        for (int i = 0; i < 4; i++) donors.add(agedTrial(100));
        for (int i = 0; i < 3; i++) assertEquals(200, transfer(donors.get(i), codeOf(to), "NDEM", 10, "m5-d" + i).status());
        assertEquals(200, transfer(donors.get(0), codeOf(to), "NDEM", 10, "m5-d0b").status(), "un donateur déjà compté peut redonner");
        Reply fourth = transfer(donors.get(3), codeOf(to), "NDEM", 10, "m5-d3");
        assertEquals(429, fourth.status(), fourth.json().toString());
        assertEquals("DAILY_CAP", fourth.reason());
        clock.freezeAt(T0.plus(Duration.ofHours(25)));
        assertEquals(200, transfer(donors.get(3), codeOf(to), "NDEM", 10, "m5-d3b").status(), "24 h plus tard");
    }

    @Test
    void m5_theSenderRecipientPairCapAppliesToTrialSenders() throws Exception {
        setPolicy("transfer.trial.dailyCap.NDEM", 1_000);
        setPolicy("transfer.trial.pairCap.NDEM", 300);
        Tv to = productionTv(), other = productionTv(), trial = agedTrial(100);
        adminGrant(trial.code(), "NDEM", 2_000);
        assertEquals(200, transfer(trial, codeOf(to), "NDEM", 300, "m5-p1").status());
        Reply r = transfer(trial, codeOf(to), "NDEM", 1, "m5-p2");
        assertEquals(429, r.status());
        assertEquals("DAILY_CAP", r.reason());
        assertEquals(200, transfer(trial, codeOf(other), "NDEM", 300, "m5-p3").status(), "un autre destinataire n'est pas concerné");
    }

    @Test
    void m5_aProductionSenderKeepsTheOrdinaryCap() throws Exception {
        Tv to = productionTv(), from = productionTv();
        adminGrant(from.code(), "NDEM", 20_000);
        assertEquals(200, transfer(from, codeOf(to), "NDEM", 6_000, "m5-q1").status());
        assertEquals(200, transfer(from, codeOf(to), "NDEM", 4_000, "m5-q2").status());
        assertEquals("DAILY_CAP", transfer(from, codeOf(to), "NDEM", 1, "m5-q3").reason());
    }

    // ---- F6, F8 ----

    @Test
    void f6_expiredCodesAreForgottenAfterSevenDays() throws Exception {
        Tv tv = productionTv();
        jdbc.update("INSERT INTO wallet_recv_code (code, holder, exp_at, used_at) VALUES ('ROLD0000X0', ?, ?, NULL)", tv.code(), Timestamp.from(T0.minus(Duration.ofDays(8))));
        jdbc.update("INSERT INTO wallet_recv_code (code, holder, exp_at, used_at) VALUES ('RNEW0000Y0', ?, ?, NULL)", tv.code(), Timestamp.from(T0.minus(Duration.ofDays(2))));
        assertEquals(200, receiveCode(tv).status());
        assertEquals(0, count("SELECT COUNT(*) FROM wallet_recv_code WHERE code = 'ROLD0000X0'"));
        assertEquals(1, count("SELECT COUNT(*) FROM wallet_recv_code WHERE code = 'RNEW0000Y0'"));
        assertEquals(1, count("SELECT COUNT(*) FROM information_schema.indexes WHERE table_name = 'wallet_recv_code' AND index_name = 'ix_wallet_recv_code_holder'"));
    }

    @Test
    void f8_aSuccessfulTransferReplaysEvenAfterTheSenderIsFrozen() throws Exception {
        Tv to = productionTv(), from = productionTv();
        String code = codeOf(to);
        assertEquals(200, transfer(from, code, "NDEM", 10, "f8-0001").status());
        jdbc.update("UPDATE wallet_identity SET frozen = TRUE WHERE holder = ?", from.code());
        Reply replay = transfer(from, code, "NDEM", 10, "f8-0001");
        assertEquals(200, replay.status(), replay.json().toString());
        assertTrue(replay.json().get("replayed").asBoolean());
        assertEquals("FROZEN", transfer(from, codeOf(to), "NDEM", 10, "f8-0002").reason(), "une nouvelle opération reste refusée");
        assertFalse(count("SELECT COUNT(*) FROM wallet_txn WHERE idem_key = ?", "xfer:" + from.code() + ":f8-0002") > 0);
    }
}
