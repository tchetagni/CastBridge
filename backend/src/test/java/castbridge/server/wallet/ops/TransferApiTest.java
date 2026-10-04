package castbridge.server.wallet.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import castbridge.server.wallet.core.Currency;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Codes de réception (10 min, usage unique, 3 actifs) et transferts libres entre TV : plafond du jour, liaison d'appareil, une seule écriture, code marqué utilisé avec le transfert. */
class TransferApiTest extends OpsTestBase {

    @AfterEach
    void restorePolicy() {
        setPolicy("switch.transfer", 1);
        setPolicy("transfer.dailyCap.NDEM", 10_000);
        setPolicy("transfer.dailyCap.MBOKO", 100);
    }

    private String canonical(String code) { return code.replace("-", ""); }

    @Test
    void receiveCodeHasTheAnnouncedShapeLifetimeAndLimit() throws Exception {
        Tv tv = trialTv();
        Reply r = receiveCode(tv);
        assertEquals(200, r.status(), r.json().toString());
        String code = r.json().get("code").asText();
        assertTrue(code.matches("R[0-9A-HJKMNP-TV-Z]{3}-[0-9A-HJKMNP-TV-Z]{4}-[0-9A-HJKMNP-TV-Z]{2}"), "R + 8 Crockford + 1 de contrôle, par groupes : " + code);
        assertEquals(NOW + Duration.ofMinutes(10).toMillis(), r.json().get("exp").asLong());
        assertEquals(10, canonical(code).length());
        assertEquals(1, count("SELECT COUNT(*) FROM wallet_recv_code WHERE holder = ?", tv.code()));
        assertEquals(200, receiveCode(tv).status());
        assertEquals(200, receiveCode(tv).status());
        Reply fourth = receiveCode(tv);
        assertEquals(429, fourth.status(), "3 actifs au plus par identité");
        assertEquals("CODE_LIMIT", fourth.reason());
        clock.freezeAt(T0.plus(Duration.ofMinutes(11)));
        assertEquals(200, receiveCode(tv).status(), "les codes expirés ne comptent plus");
        // codes tous différents
        java.util.Set<String> seen = new java.util.HashSet<>();
        Tv other = trialTv();
        clock.freezeAt(T0);
        for (int i = 0; i < 3; i++) assertTrue(seen.add(receiveCode(other).json().get("code").asText()));
    }

    @Test
    void lookupShowsAMaskedRecipientAndRefusesUnknownOrExpiredCodes() throws Exception {
        Tv to = trialTv(), from = trialTv();
        long device = count("SELECT api_device_id FROM wallet_identity WHERE holder = ?", to.code());
        jdbc.update("UPDATE device SET device_name = 'Kitchen' WHERE id = ?", device);
        String code = receiveCode(to).json().get("code").asText();
        Reply r = lookup(from, code);
        assertEquals(200, r.status(), r.json().toString());
        String last4 = to.code().substring(to.code().length() - 4);
        assertEquals("TV de K… · …" + last4, r.json().get("recipient").asText());
        assertFalse(r.json().toString().contains(to.code()), "jamais le code d'appareil entier");
        assertEquals(200, lookup(from, canonical(code).toLowerCase()).status(), "saisie sans tiret ni majuscules");
        Reply unknown = lookup(from, "R0000-0000-00");
        assertEquals(409, unknown.status());
        assertEquals("CODE_UNKNOWN", unknown.reason());
        String bad = code.substring(0, code.length() - 1) + (code.endsWith("0") ? "1" : "0");
        assertEquals("CODE_UNKNOWN", lookup(from, bad).reason(), "caractère de contrôle faux");
        // sans nom connu : seulement la fin du code
        Tv noName = trialTv();
        jdbc.update("UPDATE device SET device_name = NULL, label = NULL WHERE id = (SELECT api_device_id FROM wallet_identity WHERE holder = ?)", noName.code());
        assertTrue(lookup(from, receiveCode(noName).json().get("code").asText()).json().get("recipient").asText().startsWith("TV · …"));
        clock.freezeAt(T0.plus(Duration.ofMinutes(10)).plusSeconds(1));
        Reply expired = lookup(from, code);
        assertEquals(409, expired.status());
        assertEquals("CODE_EXPIRED", expired.reason());
    }

    @Test
    void tenLookupsPerHourPerIdentity() throws Exception {
        Tv to = trialTv(), from = trialTv(), other = trialTv();
        String code = receiveCode(to).json().get("code").asText();
        for (int i = 0; i < 10; i++) assertEquals(200, lookup(from, code).status());
        assertEquals(429, lookup(from, code).status(), "11e consultation de l'heure");
        assertEquals(200, lookup(other, code).status(), "une autre identité n'est pas gênée");
        clock.freezeAt(T0.plus(Duration.ofMinutes(61)));
        String fresh = receiveCode(to).json().get("code").asText();
        assertEquals(200, lookup(from, fresh).status(), "l'heure suivante, ça repart");
    }

    @Test
    void aValidTransferMovesTokensOnceAndConsumesTheCodeWithIt() throws Exception {
        Tv to = trialTv(), from = agedTrialTv();
        adminGrant(from.code(), "NDEM", 400);   // 500
        String code = receiveCode(to).json().get("code").asText();
        Reply r = transfer(from, code, "NDEM", 200, "xfer-0001");
        assertEquals(200, r.status(), r.json().toString());
        assertFalse(r.json().get("replayed").asBoolean());
        assertEquals(300, bal(from, Currency.NDEM));
        assertEquals(300, bal(to, Currency.NDEM));
        assertTrue(r.json().get("snapshot").asText().startsWith("cbw1."));
        assertEquals(1, newTxns("TRANSFER"));
        assertEquals(1, count("SELECT COUNT(*) FROM wallet_recv_code WHERE code = ? AND used_at IS NOT NULL", canonical(code)));
        // rejeu : une seule écriture
        for (int i = 0; i < 3; i++) {
            Reply again = transfer(from, code, "NDEM", 200, "xfer-0001");
            assertEquals(200, again.status(), again.json().toString());
            assertTrue(again.json().get("replayed").asBoolean());
        }
        assertEquals(300, bal(from, Currency.NDEM));
        assertEquals(300, bal(to, Currency.NDEM));
        assertEquals(1, newTxns("TRANSFER"));
        // autre clé avec le même code : le code est à usage unique
        Reply reuse = transfer(from, code, "NDEM", 10, "xfer-0002");
        assertEquals(409, reuse.status());
        assertEquals("CODE_UNKNOWN", reuse.reason());
        // même clé, autre montant : conflit
        Reply conflict = transfer(from, code, "NDEM", 50, "xfer-0001");
        assertEquals(409, conflict.status());
        assertEquals("IDEM_CONFLICT", conflict.reason());
        assertEquals(300, bal(to, Currency.NDEM));
        assertReconciled();
    }

    @Test
    void refusals_expiredOwnCodeCapOtherDeviceSwitchAndInsufficient() throws Exception {
        Tv to = trialTv(), from = agedTrialTv();
        adminGrant(from.code(), "NDEM", 20_000);
        // code expiré
        String old = receiveCode(to).json().get("code").asText();
        clock.freezeAt(T0.plus(Duration.ofMinutes(10)).plusSeconds(1));
        Reply expired = transfer(from, old, "NDEM", 10, "xfer-0010");
        assertEquals(409, expired.status());
        assertEquals("CODE_EXPIRED", expired.reason());
        clock.freezeAt(T0);
        // son propre code
        String own = receiveCode(from).json().get("code").asText();
        assertEquals(400, transfer(from, own, "NDEM", 10, "xfer-0011").status());
        // autre appareil (le jeton d'une autre TV)
        String code = receiveCode(to).json().get("code").asText();
        Reply stolen = transferAs(from, registerTv(), code, "NDEM", 10, "xfer-0012");
        assertEquals(403, stolen.status());
        assertEquals("BOUND_OTHER_TV", stolen.reason());
        assertEquals(401, transferAs(from, null, code, "NDEM", 10, "xfer-0013").status());
        // interrupteur
        setPolicy("switch.transfer", 0);
        Reply off = transfer(from, code, "NDEM", 10, "xfer-0014");
        assertEquals(409, off.status());
        assertEquals("TRANSFER_SUSPENDED", off.reason());
        setPolicy("switch.transfer", 1);
        // montant : bornes et solde
        assertEquals(400, transfer(from, code, "NDEM", 0, "xfer-0015").status());
        assertEquals(400, transfer(from, code, "EURO", 5, "xfer-0016").status());
        Reply big = transfer(from, code, "NDEM", 9_999_999, "xfer-0017");
        assertEquals("DAILY_CAP", big.reason());
        // solde insuffisant : le code n'est PAS consommé
        Tv poor = agedTrialTv();   // 100 NDEM
        Reply short_ = transfer(poor, code, "NDEM", 500, "xfer-0018");
        assertEquals(409, short_.status());
        assertEquals("INSUFFICIENT", short_.reason());
        assertEquals(0, count("SELECT COUNT(*) FROM wallet_recv_code WHERE code = ? AND used_at IS NOT NULL", canonical(code)), "refus du grand livre : le code reste utilisable");
        assertEquals(200, transfer(poor, code, "NDEM", 50, "xfer-0019").status());
        assertEquals(150, bal(to, Currency.NDEM));
        assertEquals(1, newTxns("TRANSFER"));
        assertReconciled();
    }

    @Test
    void theDailyCapIsPerSenderPerCurrencyAndRolls() throws Exception {
        Tv to = trialTv(), from = productionTv();
        adminGrant(from.code(), "NDEM", 20_000);
        assertEquals(200, transfer(from, receiveCode(to).json().get("code").asText(), "NDEM", 6_000, "cap-0001").status());
        Reply over = transfer(from, receiveCode(to).json().get("code").asText(), "NDEM", 4_001, "cap-0002");
        assertEquals(429, over.status());
        assertEquals("DAILY_CAP", over.reason());
        assertEquals("Plafond du jour atteint : réessayez demain", over.message());
        assertEquals(200, transfer(from, receiveCode(to).json().get("code").asText(), "NDEM", 4_000, "cap-0003").status(), "pile au plafond : 10 000");
        assertEquals(429, transfer(from, receiveCode(to).json().get("code").asText(), "NDEM", 1, "cap-0004").status());
        // le plafond est lu dans wallet_policy à chaque appel
        setPolicy("transfer.dailyCap.NDEM", 10_500);
        assertEquals(200, transfer(from, receiveCode(to).json().get("code").asText(), "NDEM", 500, "cap-0005").status());
        // MBOKO : plafond propre à la monnaie (100 / jour)
        adminGrant(from.code(), "MBOKO", 500);
        assertEquals(200, transfer(from, receiveCode(to).json().get("code").asText(), "MBOKO", 100, "cap-0006").status());
        assertEquals("DAILY_CAP", transfer(from, receiveCode(to).json().get("code").asText(), "MBOKO", 1, "cap-0007").reason());
        // le lendemain (jour UTC suivant)
        clock.freezeAt(T0.plus(Duration.ofDays(1)));
        assertEquals(200, transfer(from, receiveCode(to).json().get("code").asText(), "NDEM", 1_000, "cap-0008").status());
        // un autre émetteur n'est pas concerné
        Tv other = productionTv();
        adminGrant(other.code(), "NDEM", 5_000);
        assertEquals(200, transfer(other, receiveCode(to).json().get("code").asText(), "NDEM", 5_000, "cap-0009").status());
        assertReconciled();
    }

    @Test
    void aTrialTvReceivesMboko_andTwoSendersRacingOnOneCodeCreditItOnce() throws Exception {
        Tv to = trialTv(), a = productionTv(), b = productionTv();
        String code = receiveCode(to).json().get("code").asText();
        CyclicBarrier go = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<Reply>> fs = new ArrayList<>();
        fs.add(pool.submit(() -> { go.await(); return transfer(a, code, "MBOKO", 3, "race-xfer-a"); }));
        fs.add(pool.submit(() -> { go.await(); return transfer(b, code, "MBOKO", 4, "race-xfer-b"); }));
        int ok = 0;
        for (Future<Reply> f : fs) {
            Reply r = f.get();
            if (r.status() == 200) ok++;
            else assertEquals("CODE_UNKNOWN", r.reason());
        }
        pool.shutdown();
        assertEquals(1, ok, "un code à usage unique ne sert qu'une fois, même en course");
        assertEquals(1, newTxns("TRANSFER"));
        assertTrue(bal(to, Currency.MBOKO) == 3 || bal(to, Currency.MBOKO) == 4);
        assertEquals(20 - bal(to, Currency.MBOKO), bal(a, Currency.MBOKO) + bal(b, Currency.MBOKO));
        assertReconciled();
    }
}
