package castbridge.server.wallet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import castbridge.server.wallet.core.AccountRef;
import castbridge.server.wallet.core.Currency;
import castbridge.server.web.ApiException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Preuves de l'audit Opus de w22-02 (C1, H1, M1, M2) rendues exécutables : chaque test affirme le comportement voulu par la conception (§ 1.2) et ÉCHOUAIT avant le correctif.
 * Règle du propriétaire (2026-10-04) : une licence est liée à SA TV et ne se transfère jamais ; une tranche = (licence, période), versée une seule fois, jamais à une autre TV.
 */
class AuditFixGrantTest extends WalletTestBase {
    static final Instant T0 = Instant.parse("2026-10-04T09:00:00Z");

    @Autowired JdbcTemplate jdbc;
    @Autowired WalletRepository repo;
    @Autowired JdbcLedger ledger;
    @Autowired WalletPolicyService policies;

    private GrantService service() { return new GrantService(repo, ledger, policies, new JdbcLicenseFacts(jdbc, true)); }

    private final EditionReader reader = EditionReaderTest.reader();

    private EditionReader.Reading prod(Acts.Tv tv) { return reader.read(tv.code(), List.of(Acts.production(ISSUER, tv, T0.toEpochMilli(), List.of())), T0); }

    private static Instant day(long n) { return T0.plusSeconds(n * 86_400); }

    private long clientId() {
        jdbc.update("INSERT INTO lic_client (name, created_at, updated_at) VALUES ('Client de test', ?, ?)", Timestamp.from(T0), Timestamp.from(T0));
        return jdbc.queryForObject("SELECT MAX(id) FROM lic_client", Long.class);
    }

    private long license(String licenseId, String state, Instant start, Instant end, int seats, Instant updated) {
        jdbc.update("INSERT INTO lic_license (license_id, client_id, kind, state, seats_allowed, start_at, end_at, grace_days, transfer_cap, created_by, created_at, updated_at) "
                + "VALUES (?, ?, 'PAID', ?, ?, ?, ?, 14, 2, 'test', ?, ?)", licenseId, clientId(), state, seats, Timestamp.from(start), end == null ? null : Timestamp.from(end), Timestamp.from(start),
                Timestamp.from(updated));
        return jdbc.queryForObject("SELECT id FROM lic_license WHERE license_id = ?", Long.class, licenseId);
    }

    private long seat(long pk, int slot, Acts.Tv tv, Instant firstSeen) {
        jdbc.update("INSERT INTO lic_seat (license_pk, seat_id, subject, device_code, factors, k, slot_no, state, first_seen, last_seen) VALUES (?, ?, 'tv', ?, '', 1, ?, 'ACTIVE', ?, ?)", pk,
                String.format("%08x%08x", pk, slot), tv.code(), slot, Timestamp.from(firstSeen), Timestamp.from(firstSeen));
        return jdbc.queryForObject("SELECT MAX(id) FROM lic_seat WHERE license_pk = ?", Long.class, pk);
    }

    /** Ligne du journal d'audit des licences, comme celle que {@code LicenseService} écrit à chaque suspension, reprise ou révocation. */
    private void audit(String action, String licenseId, Instant at) {
        String hash = (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", "").substring(0, 64);
        jdbc.update("INSERT INTO lic_audit (at, actor, role, channel, action, target_type, target_id, reason, details, prev_hash, hash) VALUES (?, 'test', 'OWNER', 'api', ?, 'LICENSE', ?, NULL, NULL, ?, ?)",
                Timestamp.from(at), action, licenseId, "0".repeat(64), hash);
    }

    private long bal(Acts.Tv tv, Currency c) { return ledger.balance(AccountRef.dispo(tv.code(), c)); }

    // ---- C1 ----

    @Test
    void p1_changingTheDeviceCodeOfASeatCreditsNothingOnTheNewTv() {
        Acts.Tv a = Acts.Tv.random(), b = Acts.Tv.random();
        long pk = license("lic-c1-" + a.code().toLowerCase(), "ACTIVE", T0, null, 1, T0);
        seat(pk, 1, a, T0);
        service().sync(a.code(), 1, prod(a), day(95));
        assertEquals(5_000 + 3 * 1_000, bal(a, Currency.NDEM), "ouverture + p1..p3 pour la TV de la licence (5000 et 50 d'abord : pas de p0)");
        jdbc.update("UPDATE lic_seat SET device_code = ? WHERE license_pk = ?", b.code(), pk);
        service().sync(b.code(), 2, prod(b), day(96));
        assertEquals(0, bal(b, Currency.NDEM), "la nouvelle TV ne reçoit rien au titre des périodes déjà versées");
        assertEquals(0, bal(b, Currency.MBOKO));
        service().sync(b.code(), 2, prod(b), day(130));
        assertEquals(0, bal(b, Currency.NDEM), "ni au titre des périodes futures : la licence ne se transfère jamais");
        assertEquals(5_000 + 3 * 1_000, bal(a, Currency.NDEM), "l'ancienne TV garde ce qu'elle a reçu");
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM wallet_txn WHERE kind = 'GRANT' AND holder = ?", Long.class, b.code()));
    }

    @Test
    void p1_releasingASeatThenGivingTheLicenceToAnotherTvCreditsNothing() {
        Acts.Tv a = Acts.Tv.random(), b = Acts.Tv.random();
        long pk = license("lic-c1r-" + a.code().toLowerCase(), "ACTIVE", T0, null, 1, T0);
        long first = seat(pk, 1, a, T0);
        service().sync(a.code(), 1, prod(a), day(35));
        long before = bal(a, Currency.NDEM);
        jdbc.update("UPDATE lic_seat SET state = 'RELEASED', slot_no = NULL, released_at = ? WHERE id = ?", Timestamp.from(day(40)), first);
        seat(pk, 2, b, day(41));
        service().sync(b.code(), 2, prod(b), day(100));
        assertEquals(0, bal(b, Currency.NDEM));
        assertEquals(0, bal(b, Currency.MBOKO));
        assertEquals(before, bal(a, Currency.NDEM));
    }

    @Test
    void p1_twoSeatsOfOneLicenceCreditTheLicenceOnlyOnce() {
        Acts.Tv a = Acts.Tv.random(), b = Acts.Tv.random();
        long pk = license("lic-c1s-" + a.code().toLowerCase(), "ACTIVE", T0, null, 2, T0);
        seat(pk, 1, a, T0);
        seat(pk, 2, b, T0);
        service().sync(a.code(), 1, prod(a), day(65));
        service().sync(b.code(), 2, prod(b), day(65));
        assertEquals(5_000 + 2 * 1_000, bal(a, Currency.NDEM));
        assertEquals(0, bal(b, Currency.NDEM), "(licence, période) est versée une seule fois, à une seule TV");
    }

    @Test
    void licenceTranchesAreKeyedByLicenceAndPeriodNotByIdentity() {
        Acts.Tv a = Acts.Tv.random();
        String lic = "lic-key-" + a.code().toLowerCase();
        long pk = license(lic, "ACTIVE", T0, null, 1, T0);
        seat(pk, 1, a, T0);
        service().sync(a.code(), 1, prod(a), day(35));
        List<String> keys = jdbc.queryForList("SELECT idem_key FROM wallet_txn WHERE kind = 'GRANT' AND holder = ?", String.class, a.code());
        assertFalse(keys.isEmpty());
        for (String k : keys) {
            assertTrue(k.startsWith("grant:lic:" + lic + ":"), "clé de licence attendue, reçu " + k);
            assertFalse(k.contains(a.code()), "aucune clé de tranche de licence ne porte l'identité : " + k);
        }
    }

    // ---- H1 ----

    @Test
    void p2_suspendedLicenceEditedLaterMustNotPayTheSuspendedPeriods() {
        Acts.Tv tv = Acts.Tv.random();
        String lic = "lic-h1-" + tv.code().toLowerCase();
        long pk = license(lic, "ACTIVE", T0, null, 1, T0);
        seat(pk, 1, tv, T0);
        service().sync(tv.code(), 1, prod(tv), day(5));
        assertEquals(5_000, bal(tv, Currency.NDEM), "5 000 NDEM d'abord, pas de tranche mensuelle le premier jour");
        // suspension au jour 10 (journal d'audit, comme LicenseService), puis changement du délai de grâce au jour 95 : updated_at glisse, la licence reste suspendue
        jdbc.update("UPDATE lic_license SET state = 'SUSPENDED', updated_at = ? WHERE id = ?", Timestamp.from(day(10)), pk);
        audit("LICENSE_SUSPEND", lic, day(10));
        jdbc.update("UPDATE lic_license SET grace_days = 30, updated_at = ? WHERE id = ?", Timestamp.from(day(95)), pk);
        service().sync(tv.code(), 1, prod(tv), day(100));
        assertEquals(5_000, bal(tv, Currency.NDEM), "aucune tranche pour les périodes de suspension (p1, p2, p3)");
        assertEquals(50, bal(tv, Currency.MBOKO));
    }

    @Test
    void p2_theEndOfTheActiveStateIsFrozenTheFirstTimeTheWalletSeesIt() {
        Acts.Tv tv = Acts.Tv.random();
        String lic = "lic-h1f-" + tv.code().toLowerCase();
        long pk = license(lic, "ACTIVE", T0, null, 1, T0);
        seat(pk, 1, tv, T0);
        service().sync(tv.code(), 1, prod(tv), day(5));
        jdbc.update("UPDATE lic_license SET state = 'SUSPENDED', updated_at = ? WHERE id = ?", Timestamp.from(day(10)), pk);
        service().sync(tv.code(), 1, prod(tv), day(20));     // le portefeuille voit la suspension : la fin de l'état ACTIVE est figée au jour 10
        jdbc.update("UPDATE lic_license SET grace_days = 30, updated_at = ? WHERE id = ?", Timestamp.from(day(95)), pk);
        service().sync(tv.code(), 1, prod(tv), day(100));
        assertEquals(5_000, bal(tv, Currency.NDEM), "la date figée ne glisse jamais avec updated_at");
    }

    @Test
    void p2_resumeOpensANewIntervalWithoutCatchUpOfTheSuspendedPeriods() {
        // réécriture du test de w22-02 qui figeait le rattrapage (suspendedLicenseGivesNothingMoreAndResumesRetroactivelyWithoutLoss), contre la conception § 1.2 :
        // « une tranche est due si, au début de sa période, une licence ACTIVE couvrait cet instant »
        Acts.Tv tv = Acts.Tv.random();
        String lic = "lic-h1r-" + tv.code().toLowerCase();
        long pk = license(lic, "ACTIVE", T0, null, 1, T0);
        seat(pk, 1, tv, T0);
        service().sync(tv.code(), 1, prod(tv), day(5));
        jdbc.update("UPDATE lic_license SET state = 'SUSPENDED', updated_at = ? WHERE id = ?", Timestamp.from(day(10)), pk);
        audit("LICENSE_SUSPEND", lic, day(10));
        service().sync(tv.code(), 1, prod(tv), day(100));
        assertEquals(5_000, bal(tv, Currency.NDEM));
        jdbc.update("UPDATE lic_license SET state = 'ACTIVE', updated_at = ? WHERE id = ?", Timestamp.from(day(101)), pk);
        audit("LICENSE_RESUME", lic, day(101));
        service().sync(tv.code(), 1, prod(tv), day(110));
        assertEquals(5_000, bal(tv, Currency.NDEM), "la reprise ne rattrape pas p1, p2, p3 (jours 30, 60, 90)");
        service().sync(tv.code(), 1, prod(tv), day(125));
        assertEquals(6_000, bal(tv, Currency.NDEM), "p4 (jour 120) commence dans le nouvel intervalle ACTIF : une tranche");
        assertEquals(60, bal(tv, Currency.MBOKO));
    }

    // ---- M1 ----

    @Test
    void p4_thePeriodLengthCannotBeChanged() {
        ApiException e = assertThrows(ApiException.class, () -> policies.set("grant.periodDays", 15, "test"));
        assertEquals(400, e.status().value());
        assertEquals(30, policies.get().periodDays());
        assertThrows(ApiException.class, () -> policies.set("grant.periodDays", 31, "test"));
    }

    // ---- M2 ----

    @Test
    void p3_aRealRevocationStillSeesTheLicenceAndPaysThePastUnpaidTranche() {
        Acts.Tv tv = Acts.Tv.random();
        String lic = "lic-m2-" + tv.code().toLowerCase();
        long pk = license(lic, "ACTIVE", T0, T0.plusSeconds(180 * 86_400), 1, T0);
        long seatPk = seat(pk, 1, tv, T0);
        service().sync(tv.code(), 1, prod(tv), day(5));
        assertEquals(1_000, bal(tv, Currency.NDEM));
        // LicenseService.revoke() : état REVOKED ET libération de tous les postes
        jdbc.update("UPDATE lic_license SET state = 'REVOKED', updated_at = ? WHERE id = ?", Timestamp.from(day(40)), pk);
        jdbc.update("UPDATE lic_seat SET state = 'RELEASED', slot_no = NULL, released_at = ?, released_reason = 'révocation' WHERE id = ?", Timestamp.from(day(40)), seatPk);
        audit("LICENSE_REVOKE", lic, day(40));
        GrantService.Outcome o = service().sync(tv.code(), 1, prod(tv), day(100));
        assertEquals(2_000, bal(tv, Currency.NDEM), "p1 (jour 30) précède la révocation du jour 40 : acquise ; p2 non");
        assertEquals("REVOKED", o.standing().licenseState(), "jamais « en attente d'enregistrement » pour une licence révoquée");
        assertFalse(o.standing().licensePending());
    }

    // ---- « 5000 et 50 d'abord » (décision du propriétaire, 2026-10-04) ----

    @Test
    void unlimitedLicencePaysOnly5000And50OnTheFirstDayThenOneTrancheFromTheFollowingMonth() {
        Acts.Tv tv = Acts.Tv.random();
        long pk = license("lic-open-" + tv.code().toLowerCase(), "ACTIVE", T0, null, 1, T0);
        seat(pk, 1, tv, T0);
        service().sync(tv.code(), 1, prod(tv), T0);
        assertEquals(5_000, bal(tv, Currency.NDEM), "premier jour : 5 000 NDEM exactement");
        assertEquals(50, bal(tv, Currency.MBOKO), "premier jour : 50 MBOKO exactement");
        service().sync(tv.code(), 1, prod(tv), day(29));
        assertEquals(5_000, bal(tv, Currency.NDEM), "avant ancre + 1 période : rien de plus");
        service().sync(tv.code(), 1, prod(tv), day(30));
        assertEquals(6_000, bal(tv, Currency.NDEM), "ancre + 1 période : +1 000");
        assertEquals(60, bal(tv, Currency.MBOKO), "ancre + 1 période : +10");
        service().sync(tv.code(), 1, prod(tv), day(60));
        assertEquals(7_000, bal(tv, Currency.NDEM), "ancre + 2 périodes : encore +1 000");
        assertEquals(70, bal(tv, Currency.MBOKO));
        // relire ne reverse rien
        assertEquals(0, service().sync(tv.code(), 1, prod(tv), day(60)).granted());
        assertEquals(7_000, bal(tv, Currency.NDEM));
        assertEquals(70, bal(tv, Currency.MBOKO));
        // la clé d'une tranche reste (licence, période) : jamais de p0 pour une illimitée
        List<String> keys = jdbc.queryForList("SELECT idem_key FROM wallet_txn WHERE kind = 'GRANT' AND holder = ?", String.class, tv.code());
        assertFalse(keys.stream().anyMatch(k -> k.endsWith(":p0")), keys.toString());
        assertTrue(keys.stream().anyMatch(k -> k.endsWith(":NDEM:p1")) && keys.stream().anyMatch(k -> k.endsWith(":MBOKO:p2")), keys.toString());
    }

    @Test
    void aSeatTransferDoesNotGiveTheOpeningAgain() {
        Acts.Tv a = Acts.Tv.random(), b = Acts.Tv.random();
        long pk = license("lic-open2-" + a.code().toLowerCase(), "ACTIVE", T0, null, 1, T0);
        seat(pk, 1, a, T0);
        service().sync(a.code(), 1, prod(a), T0);
        jdbc.update("UPDATE lic_seat SET device_code = ? WHERE license_pk = ?", b.code(), pk);
        service().sync(b.code(), 2, prod(b), day(1));
        assertEquals(0, bal(b, Currency.NDEM), "pas d'ouverture pour la nouvelle TV");
        assertEquals(0, bal(b, Currency.MBOKO));
        assertEquals(5_000, bal(a, Currency.NDEM));
    }
}
