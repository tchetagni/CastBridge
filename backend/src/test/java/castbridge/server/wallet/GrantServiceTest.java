package castbridge.server.wallet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import castbridge.server.wallet.core.AccountRef;
import castbridge.server.wallet.core.Currency;
import castbridge.server.wallet.core.Edition;
import castbridge.server.wallet.core.StakeRules;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Attributions tirées de la LICENCE lue côté serveur (tables V51 existantes, lecture seule) pour la production, de {@code cbx1} pour l'essai ; tranches paresseuses
 * et rétroactives, une seule par période et par monnaie, ouverture illimitée une seule fois. Les licences sont de VRAIES lignes de {@code lic_license} / {@code lic_seat}.
 */
class GrantServiceTest extends WalletTestBase {
    static final Instant T0 = Instant.parse("2026-10-04T09:00:00Z");
    static final long DAY = Acts.DAY;

    @Autowired JdbcTemplate jdbc;
    @Autowired WalletRepository repo;
    @Autowired JdbcLedger ledger;
    @Autowired WalletPolicyService policies;

    private GrantService service() { return new GrantService(repo, ledger, policies, new JdbcLicenseFacts(jdbc, true)); }

    private final EditionReader reader = EditionReaderTest.reader();

    private EditionReader.Reading prod(Acts.Tv tv) {
        return reader.read(tv.code(), List.of(Acts.production(ISSUER, tv, T0.toEpochMilli(), List.of())), T0);
    }

    private EditionReader.Reading trial(Acts.Tv tv, int days) {
        return reader.read(tv.code(), List.of(Acts.trialDays(ISSUER, tv, T0.toEpochMilli(), days)), T0);
    }

    private long clientId() {
        jdbc.update("INSERT INTO lic_client (name, created_at, updated_at) VALUES ('Client de test', ?, ?)", Timestamp.from(T0), Timestamp.from(T0));
        return jdbc.queryForObject("SELECT MAX(id) FROM lic_client", Long.class);
    }

    /** Une licence PAID avec un poste ACTIVE sur ce code d'appareil ; {@code end} null = sans fin. */
    private long license(Acts.Tv tv, String licenseId, String state, Instant start, Instant end, int grace, Instant updated) {
        jdbc.update("INSERT INTO lic_license (license_id, client_id, kind, state, seats_allowed, start_at, end_at, grace_days, transfer_cap, created_by, created_at, updated_at) "
                        + "VALUES (?, ?, 'PAID', ?, 1, ?, ?, ?, 2, 'test', ?, ?)", licenseId, clientId(), state, Timestamp.from(start), end == null ? null : Timestamp.from(end), grace, Timestamp.from(start),
                Timestamp.from(updated));
        long pk = jdbc.queryForObject("SELECT id FROM lic_license WHERE license_id = ?", Long.class, licenseId);
        jdbc.update("INSERT INTO lic_seat (license_pk, seat_id, subject, device_code, factors, k, slot_no, state, first_seen, last_seen) VALUES (?, ?, 'tv', ?, '', 1, 1, 'ACTIVE', ?, ?)",
                pk, "00000000" + String.format("%08x", pk), tv.code(), Timestamp.from(start), Timestamp.from(start));
        return pk;
    }

    private long bal(Acts.Tv tv, Currency c) { return ledger.balance(AccountRef.dispo(tv.code(), c)); }

    @Test
    void activeLicenseOf90DaysGivesThreeTranches() {
        Acts.Tv tv = Acts.Tv.random();
        license(tv, "lic-" + tv.code().toLowerCase(), "ACTIVE", T0, T0.plusSeconds(90 * 86_400), 14, T0);
        GrantService.Outcome o = service().sync(tv.code(), 1, prod(tv), T0.plusSeconds(65 * 86_400));
        assertEquals(3_000, bal(tv, Currency.NDEM));
        assertEquals(30, bal(tv, Currency.MBOKO));
        assertEquals(Edition.PRODUCTION, o.standing().edition());
        assertEquals("PROD", o.standing().ed());
        assertEquals(T0, o.standing().anchor());
        assertEquals(6, o.granted());
        // deux syncs de plus, bien plus tard : rien de plus (une seule tranche par période, aucune après la fin)
        assertEquals(0, service().sync(tv.code(), 1, prod(tv), T0.plusSeconds(65 * 86_400)).granted());
        service().sync(tv.code(), 1, prod(tv), T0.plusSeconds(300 * 86_400));
        assertEquals(3_000, bal(tv, Currency.NDEM));
        assertEquals(30, bal(tv, Currency.MBOKO));
    }

    @Test
    void licenseRevokedInMonthTwoStopsFutureTranches() {
        Acts.Tv tv = Acts.Tv.random();
        long pk = license(tv, "lic-r-" + tv.code().toLowerCase(), "ACTIVE", T0, T0.plusSeconds(180 * 86_400), 14, T0);
        service().sync(tv.code(), 1, prod(tv), T0.plusSeconds(35 * 86_400));          // p0, p1
        assertEquals(2_000, bal(tv, Currency.NDEM));
        jdbc.update("UPDATE lic_license SET state = 'REVOKED', updated_at = ? WHERE id = ?", Timestamp.from(T0.plusSeconds(40 * 86_400)), pk);
        GrantService.Outcome o = service().sync(tv.code(), 1, prod(tv), T0.plusSeconds(100 * 86_400));
        assertEquals(2_000, bal(tv, Currency.NDEM), "les tranches passées restent acquises, aucune nouvelle");
        assertEquals(20, bal(tv, Currency.MBOKO));
        assertEquals(Edition.NONE, o.standing().edition());
        assertEquals("NONE", o.standing().ed());
        assertEquals("REVOKED", o.standing().licenseState());
    }

    @Test
    void licenseRevokedBeforeTheFirstSyncOnlyCreditsWhatPrecededTheRevocation() {
        Acts.Tv tv = Acts.Tv.random();
        license(tv, "lic-rr-" + tv.code().toLowerCase(), "REVOKED", T0, T0.plusSeconds(180 * 86_400), 14, T0.plusSeconds(40 * 86_400));
        service().sync(tv.code(), 1, prod(tv), T0.plusSeconds(100 * 86_400));
        assertEquals(2_000, bal(tv, Currency.NDEM), "p0 (jour 0) et p1 (jour 30) précèdent la révocation du jour 40 ; p2 (jour 60) non");
    }

    @Test
    void suspendedLicenseGivesNothingMoreAndResumesRetroactivelyWithoutLoss() {
        Acts.Tv tv = Acts.Tv.random();
        long pk = license(tv, "lic-s-" + tv.code().toLowerCase(), "ACTIVE", T0, null, 14, T0);
        service().sync(tv.code(), 1, prod(tv), T0.plusSeconds(5 * 86_400));            // ouverture + p0
        jdbc.update("UPDATE lic_license SET state = 'SUSPENDED', updated_at = ? WHERE id = ?", Timestamp.from(T0.plusSeconds(10 * 86_400)), pk);
        service().sync(tv.code(), 1, prod(tv), T0.plusSeconds(100 * 86_400));
        assertEquals(6_000, bal(tv, Currency.NDEM), "ouverture 5 000 + p0 : la suspension du jour 10 arrête les suivantes");
        jdbc.update("UPDATE lic_license SET state = 'ACTIVE', updated_at = ? WHERE id = ?", Timestamp.from(T0.plusSeconds(101 * 86_400)), pk);
        service().sync(tv.code(), 1, prod(tv), T0.plusSeconds(100 * 86_400));
        assertEquals(5_000 + 4 * 1_000, bal(tv, Currency.NDEM), "reprise : les périodes manquées sont inscrites (p1, p2, p3), jamais deux fois");
    }

    @Test
    void productionActivationWithoutLicenseIsPendingThenCatchesUp() {
        Acts.Tv tv = Acts.Tv.random();
        GrantService.Outcome pending = service().sync(tv.code(), 1, prod(tv), T0.plusSeconds(65 * 86_400));
        assertEquals(0, bal(tv, Currency.NDEM));
        assertEquals(0, bal(tv, Currency.MBOKO));
        assertTrue(pending.standing().licensePending());
        assertEquals("NONE", pending.standing().ed());
        assertEquals(0, pending.granted());
        assertNull(repo.identity(tv.code()).orElseThrow().anchorAt(), "sans licence ni essai : pas d'ancre");
        // la licence est enregistrée : tranches rétroactives depuis start_at
        license(tv, "lic-p-" + tv.code().toLowerCase(), "ACTIVE", T0, T0.plusSeconds(90 * 86_400), 14, T0.plusSeconds(60 * 86_400));
        GrantService.Outcome caught = service().sync(tv.code(), 1, prod(tv), T0.plusSeconds(65 * 86_400));
        assertFalse(caught.standing().licensePending());
        assertEquals(3_000, bal(tv, Currency.NDEM));
        assertEquals(30, bal(tv, Currency.MBOKO));
    }

    @Test
    void licenseWithoutEndOpensOnceForever() {
        Acts.Tv tv = Acts.Tv.random();
        license(tv, "lic-u-" + tv.code().toLowerCase(), "ACTIVE", T0, null, 14, T0);
        GrantService.Outcome o = service().sync(tv.code(), 1, prod(tv), T0.plusSeconds(5 * 86_400));
        assertEquals(5_000 + 1_000, bal(tv, Currency.NDEM));
        assertEquals(50 + 10, bal(tv, Currency.MBOKO));
        assertEquals("UNLIMITED", o.standing().ed());
        assertTrue(repo.identity(tv.code()).orElseThrow().openedUnlimited());
        service().sync(tv.code(), 1, prod(tv), T0.plusSeconds(5 * 86_400));
        assertEquals(6_000, bal(tv, Currency.NDEM));
        // une nouvelle licence illimitée (nouvelle clé, nouvelle licence) ne redonne pas l'ouverture
        license(tv, "lic-u2-" + tv.code().toLowerCase(), "ACTIVE", T0.plusSeconds(1 * 86_400), null, 14, T0);
        service().sync(tv.code(), 1, prod(tv), T0.plusSeconds(65 * 86_400));
        assertEquals(5_000 + 3 * 1_000, bal(tv, Currency.NDEM), "ouverture une fois ; p0, p1, p2 une fois chacune malgré deux licences superposées");
        assertEquals(50 + 3 * 10, bal(tv, Currency.MBOKO));
    }

    @Test
    void trialIsAlwaysReadInTheActivationKey() {
        Acts.Tv tv = Acts.Tv.random();
        GrantService.Outcome o = service().sync(tv.code(), 1, trial(tv, 30), T0.plusSeconds(5 * 86_400));
        assertEquals(100, bal(tv, Currency.NDEM));
        assertEquals(0, bal(tv, Currency.MBOKO), "MBOKO : production exclusivement");
        assertEquals("TRIAL", o.standing().ed());
        assertFalse(o.standing().licensePending(), "un essai n'attend aucune licence");
        Acts.Tv tv90 = Acts.Tv.random();
        service().sync(tv90.code(), 1, trial(tv90, 90), T0.plusSeconds(65 * 86_400));
        assertEquals(300, bal(tv90, Currency.NDEM));
        Acts.Tv tv7 = Acts.Tv.random();
        service().sync(tv7.code(), 1, trial(tv7, 7), T0.plusSeconds(20 * 86_400));
        assertEquals(100, bal(tv7, Currency.NDEM), "7 jours : N = 1");
    }

    @Test
    void trialThenLicenseNeverGivesTwoTranchesForTheSamePeriod() {
        Acts.Tv tv = Acts.Tv.random();
        EditionReader.Reading r = reader.read(tv.code(), List.of(Acts.trialDays(ISSUER, tv, T0.toEpochMilli(), 40), Acts.production(ISSUER, tv, T0.toEpochMilli(), List.of())), T0);
        license(tv, "lic-t-" + tv.code().toLowerCase(), "ACTIVE", T0.plusSeconds(20 * 86_400), T0.plusSeconds(110 * 86_400), 14, T0);
        service().sync(tv.code(), 1, r, T0.plusSeconds(65 * 86_400));
        // anchor = début de l'essai ; p0 (jour 0) : essai 100 ; p1 (jour 30) : essai ET licence => la meilleure, 1 000 ; p2 (jour 60) : licence 1 000
        assertEquals(100 + 1_000 + 1_000, bal(tv, Currency.NDEM));
        assertEquals(10 + 10, bal(tv, Currency.MBOKO));
        assertEquals(T0, repo.identity(tv.code()).orElseThrow().anchorAt());
    }

    @Test
    void graceGivesNoTrancheButAllowsStakes() {
        Acts.Tv tv = Acts.Tv.random();
        license(tv, "lic-g-" + tv.code().toLowerCase(), "ACTIVE", T0, T0.plusSeconds(30 * 86_400), 14, T0);
        GrantService.Outcome o = service().sync(tv.code(), 1, prod(tv), T0.plusSeconds(35 * 86_400));
        assertEquals(1_000, bal(tv, Currency.NDEM), "une seule tranche (p0) : la grâce n'en attribue pas");
        assertTrue(o.standing().grace());
        assertEquals(Edition.NONE, o.standing().edition());
        assertTrue(StakeRules.mayStake(o.standing().edition(), Currency.MBOKO, o.standing().grace()));
        GrantService.Outcome after = service().sync(tv.code(), 1, prod(tv), T0.plusSeconds(50 * 86_400));
        assertFalse(after.standing().grace(), "au-delà de grace_days");
        assertFalse(StakeRules.mayStake(after.standing().edition(), Currency.MBOKO, after.standing().grace()));
    }

    @Test
    void superKeyGivesNoAutomaticGrant() {
        Acts.Tv tv = Acts.Tv.random();
        EditionReader.Reading r = reader.read(tv.code(), List.of(Acts.production(ISSUER, tv, T0.toEpochMilli(), List.of("super|tout|" + T0.toEpochMilli()))), T0);
        license(tv, "lic-super-" + tv.code().toLowerCase(), "ACTIVE", T0, T0.plusSeconds(90 * 86_400), 14, T0);
        GrantService.Outcome o = service().sync(tv.code(), 1, r, T0.plusSeconds(65 * 86_400));
        assertEquals(0, bal(tv, Currency.NDEM));
        assertEquals(Edition.SUPER, o.standing().edition());
    }

    @Test
    void anotherApiDeviceReadsButDoesNotRebind() {
        Acts.Tv tv = Acts.Tv.random();
        service().sync(tv.code(), 11, trial(tv, 30), T0.plusSeconds(1_000));
        GrantService.Outcome o = service().sync(tv.code(), 22, trial(tv, 30), T0.plusSeconds(2_000));
        assertTrue(o.boundOther());
        assertEquals(11, repo.identity(tv.code()).orElseThrow().apiDeviceId());
        assertEquals(100, bal(tv, Currency.NDEM));
    }

    @Test
    void parallelSyncsCreditEachTrancheOnce() throws Exception {
        Acts.Tv tv = Acts.Tv.random();
        license(tv, "lic-par-" + tv.code().toLowerCase(), "ACTIVE", T0, null, 14, T0);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<?>> fs = new ArrayList<>();
        for (int i = 0; i < 8; i++) fs.add(pool.submit(() -> {
            go.await();
            service().sync(tv.code(), 1, prod(tv), T0.plusSeconds(65 * 86_400));
            return null;
        }));
        go.countDown();
        for (Future<?> f : fs) f.get(120, TimeUnit.SECONDS);
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        assertEquals(5_000 + 3 * 1_000, bal(tv, Currency.NDEM));
        assertEquals(50 + 3 * 10, bal(tv, Currency.MBOKO));
    }

    @Test
    void licenseFactsReadOnlyActiveSeatsOfPaidLicensesAndOnlyWhenTheModuleIsOn() {
        Acts.Tv tv = Acts.Tv.random();
        license(tv, "lic-ok-" + tv.code().toLowerCase(), "ACTIVE", T0, T0.plusSeconds(30 * 86_400), 9, T0);
        long released = license(tv, "lic-rel-" + tv.code().toLowerCase(), "ACTIVE", T0, null, 14, T0);
        jdbc.update("UPDATE lic_seat SET state = 'RELEASED', slot_no = NULL WHERE license_pk = ?", released);
        long trialKind = license(tv, "lic-tri-" + tv.code().toLowerCase(), "ACTIVE", T0, null, 14, T0);
        jdbc.update("UPDATE lic_license SET kind = 'TRIAL' WHERE id = ?", trialKind);
        List<LicenseFacts.LicenseView> v = new JdbcLicenseFacts(jdbc, true).forDevice(tv.code());
        assertEquals(1, v.size());
        assertEquals(LicenseFacts.State.ACTIVE, v.get(0).state());
        assertEquals(T0, v.get(0).startAt());
        assertEquals(T0.plusSeconds(30 * 86_400), v.get(0).endAt());
        assertEquals(9, v.get(0).graceDays());
        assertTrue(new JdbcLicenseFacts(jdbc, false).forDevice(tv.code()).isEmpty(), "module des licences éteint : aucune licence connue");
        assertTrue(new JdbcLicenseFacts(jdbc, true).forDevice(Acts.Tv.random().code()).isEmpty());
    }

    @Test
    void amountsComeFromThePolicyTable() {
        Acts.Tv tv = Acts.Tv.random();
        policies.set("grant.production.ndem", 2_000, "test");
        try {
            license(tv, "lic-pol-" + tv.code().toLowerCase(), "ACTIVE", T0, T0.plusSeconds(30 * 86_400), 14, T0);
            service().sync(tv.code(), 1, prod(tv), T0.plusSeconds(1_000));
            assertEquals(2_000, bal(tv, Currency.NDEM));
        } finally {
            policies.set("grant.production.ndem", 1_000, "test");
        }
        assertNotNull(policies.get());
    }
}
