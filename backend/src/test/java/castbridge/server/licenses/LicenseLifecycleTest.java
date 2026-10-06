package castbridge.server.licenses;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import castbridge.server.licenses.DeviceIdentity.Factor;
import castbridge.server.web.ApiException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class LicenseLifecycleTest extends LicenseTestBase {

    private static WireActivation.Fields decode(ActivationService.Activation a) {
        var d = WireActivation.decode(a.text());
        assertThat(d).as("l'activation émise doit se relire strictement").isNotNull();
        return d.fields();
    }

    @Test
    void createModifySuspendRevokeExtend() {
        products.create(OWNER, new ProductService.NewProduct("p-classe-3e", "Classe de 3e", "ABONNEMENT", 365, List.of("learn/3e", "quiz/3e"), null, null, List.of("classe-3e")));
        var c = client();
        var l = licenses.create(OWNER, new LicenseService.NewLicense(null, c.id(), "PAID", 2, null, Instant.now().plus(Duration.ofDays(30)), null, null, List.of("p-classe-3e")));
        assertThat(l.licenseId()).matches(Validate.LICENSE_ID).startsWith("lic-");
        assertThat(l.seatsAllowed()).isEqualTo(2);
        assertThat(licenses.detail(l.licenseId()).products()).extracting(LicenseService.ProductRef::productId).containsExactly("p-classe-3e");

        // a destructive action without a reason is refused
        assertThatThrownBy(() -> licenses.suspend(OWNER, l.licenseId(), " ")).isInstanceOf(ApiException.class).hasMessageContaining("motif");
        assertThat(licenses.suspend(OWNER, l.licenseId(), "impayé constaté").state()).isEqualTo("SUSPENDED");
        assertThatThrownBy(() -> issue(l.licenseId(), dev())).hasMessageContaining("suspendue");
        assertThat(licenses.resume(OWNER, l.licenseId(), "paiement reçu").state()).isEqualTo("ACTIVE");

        // extend forward: no reason needed; shorten: reason needed
        Instant later = Instant.now().plus(Duration.ofDays(400));
        assertThat(licenses.extend(OWNER, l.licenseId(), later, null).endAt()).isBetween(later.minusSeconds(1), later.plusSeconds(1));
        assertThatThrownBy(() -> licenses.extend(OWNER, l.licenseId(), Instant.now().plus(Duration.ofDays(10)), null)).hasMessageContaining("motif");

        // seats cannot go below the used ones
        issue(l.licenseId(), dev());
        issue(l.licenseId(), dev());
        assertThatThrownBy(() -> licenses.setSeats(OWNER, l.licenseId(), 1, "réduction")).hasMessageContaining("utilisés");
        assertThat(licenses.setSeats(OWNER, l.licenseId(), 5, null).seatsAllowed()).isEqualTo(5);

        // revoke: final, seats freed, every seat in the signed revocation list, nothing issued any more
        var r = licenses.revoke(OWNER, l.licenseId(), "fraude avérée");
        assertThat(r.state()).isEqualTo("REVOKED");
        assertThat(r.seatsUsed()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from lic_revocation where license_id = ? and seat_id is not null", Integer.class, l.licenseId())).isEqualTo(2);
        assertThatThrownBy(() -> issue(l.licenseId(), dev())).hasMessageContaining("révoquée");
        assertThatThrownBy(() -> licenses.extend(OWNER, l.licenseId(), later, null)).isInstanceOf(ApiException.class);
    }

    @Test
    void theActivationIsTheRealWireFormatWithTheRightsOfTheLicence() {
        ensureProducts();
        products.create(OWNER, new ProductService.NewProduct("abo-tout", "Abonnement tout", "ABONNEMENT", 365, null, null, null, List.of("tout")));
        Instant end = Instant.now().plus(Duration.ofDays(200));
        var l = licenses.create(OWNER, new LicenseService.NewLicense(null, client().id(), "PAID", 1, null, end, 10, null, List.of("p-test", "abo-tout")));
        Dev d = dev();
        var a = issue(l.licenseId(), d);
        var f = decode(a);
        assertThat(a.text()).startsWith("cbx1.");
        assertThat(f.kind()).isEqualTo("production");
        assertThat(f.subject()).isEqualTo("tv");
        assertThat(f.license()).isEqualTo(l.licenseId());
        assertThat(f.kid()).isEqualTo(keyring.kid());
        assertThat(f.factors()).isEqualTo(d.fp());
        assertThat(f.k()).isEqualTo(4);
        assertThat(f.seat()).isEqualTo(seatOf(l.licenseId(), d)).hasSize(16);
        assertThat(f.issuedAt()).isLessThanOrEqualTo(System.currentTimeMillis() + 1500);
        assertThat(f.notBefore()).isLessThanOrEqualTo(f.issuedAt());
        assertThat(f.notAfter() - f.notBefore()).isEqualTo(48 * 3_600_000L); // default installation window: 48 h from the creation
        assertThat(f.rights()).hasSize(2).anyMatch(r -> r.startsWith("purchase|p-test|classe-test|"))
                .anyMatch(r -> r.startsWith("subscription|abo-tout|tout|") && r.endsWith("|" + 10 * 86_400_000L + "|0"));
        String sub = f.rights().stream().filter(r -> r.startsWith("subscription|")).findFirst().orElseThrow();
        // the subscription ends with the licence (the database may round the date to the second)
        assertThat(Long.parseLong(sub.split("\\|")[4])).isBetween(end.toEpochMilli() - 1000, end.toEpochMilli());
        // the signature is the server key's, over the canonical text
        var dec = WireActivation.decode(a.text());
        assertThat(LicenseKeyring.verify(java.util.Base64.getDecoder().decode(keyring.publicKeyBase64()), dec.text().getBytes(java.nio.charset.StandardCharsets.UTF_8), dec.signature())).isTrue();
        // the issuance is in the registry as a signed `issue` event, and the licence has its `license` event
        assertThat(registry.all(100)).extracting(RegistryStore.Stored::text).anyMatch(t -> t.contains("type=license") && t.contains("license=" + l.licenseId()))
                .anyMatch(t -> t.contains("type=issue") && t.contains("seat=" + f.seat()) && t.contains("nonce=" + f.nonce()));
    }

    @Test
    void windowAndRightsRules() {
        var l = license(5);
        Dev d = dev();
        var a = activations.issue(OWNER, new ActivationService.IssueRequest(l.licenseId(), "tv", d.text(), null, null, 24), "server-api");
        assertThat(decode(a).notAfter() - decode(a).notBefore()).isEqualTo(24 * 3_600_000L);
        assertThatThrownBy(() -> activations.issue(OWNER, new ActivationService.IssueRequest(l.licenseId(), "tv", dev().text(), null, null, 49), "server-api")).hasMessageContaining("1 à 48");
        assertThatThrownBy(() -> activations.issue(OWNER, new ActivationService.IssueRequest(l.licenseId(), "tv", dev().text(), null, null, 0), "server-api")).hasMessageContaining("1 à 48");
        // a licence without any product issues a full-version key: no right at all (duration only; unlimited here)
        var empty = licenses.create(OWNER, new LicenseService.NewLicense(null, client().id(), "PAID", 1, null, null, null, null, null));
        assertThat(decode(issue(empty.licenseId(), dev())).rights()).isEmpty();
        // a subscription needs an end date
        var abo = products.create(OWNER, new ProductService.NewProduct("abo-sans-fin", "Abo", "ABONNEMENT", 30, null, null, null, null));
        var open = licenses.create(OWNER, new LicenseService.NewLicense(null, client().id(), "PAID", 1, null, null, null, null, List.of(abo.productId())));
        assertThatThrownBy(() -> issue(open.licenseId(), dev())).hasMessageContaining("date de fin");
        // restricting to one product of the licence, and refusing one that is not in it
        var two = licenses.create(OWNER, new LicenseService.NewLicense(null, client().id(), "PAID", 3, null, null, null, null, List.of("p-test")));
        assertThatThrownBy(() -> activations.issue(OWNER, new ActivationService.IssueRequest(two.licenseId(), "tv", dev().text(), null, List.of("autre-produit"), null), "server-api")).hasMessageContaining("absent");
        // trial licence: a trial key (license "trial", no right), counted as a trial, 1 seat
        var trial = licenses.create(OWNER, new LicenseService.NewLicense(null, client().id(), "TRIAL", null, null, null, null, null, null));
        assertThat(trial.licenseId()).startsWith("essai-");
        var t = issue(trial.licenseId(), dev());
        assertThat(decode(t).kind()).isEqualTo("trial");
        assertThat(decode(t).license()).isEqualTo("trial");
        assertThat(decode(t).rights()).hasSize(1).allMatch(r -> r.startsWith("usage|duree|"));
        assertThatThrownBy(() -> issue(trial.licenseId(), dev())).hasMessageContaining("Plus de poste");
    }

    @Test
    void sameHardwareKeepsItsSeatEvenWithAReplacedModuleAndTheSameRequestGivesTheSameActivation() {
        var l = license(1);
        Dev d = dev();
        var first = issue(l.licenseId(), d);
        assertThat(first.reused()).isFalse();
        assertThat(first.newSeat()).isTrue();
        // same request any number of times (reinstalled app, file lost, double click) while the activation can still be installed: the SAME activation
        var again = issue(l.licenseId(), d);
        var viaSeat = activations.reissueSeat(SUPPORT, l.licenseId(), first.seatId(), "server-web");
        var viaRequest = activations.reissue(SUPPORT, l.licenseId(), "tv", d.text(), "server-web");
        for (var x : List.of(again, viaSeat, viaRequest)) {
            assertThat(x.reused()).isTrue();
            assertThat(x.text()).isEqualTo(first.text());
            assertThat(x.fingerprint()).isEqualTo(first.fingerprint());
        }
        assertThat(licenses.get(l.licenseId()).seatsUsed()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from lic_issuance where license_pk = ?", Integer.class, l.id())).isEqualTo(1);
        // only the fingerprint is stored, never the activation
        assertThat(jdbc.queryForObject("select token_fingerprint from lic_issuance where license_pk = ?", String.class, l.id())).isEqualTo(first.fingerprint());

        // a module replaced (4 of 5 factors in common): the SAME seat, nothing consumed, and an activation for the new factor set
        Dev repaired = d.withModuleChanged(Factor.WIFI);
        var after = activations.issue(OWNER, new ActivationService.IssueRequest(l.licenseId(), "tv", repaired.text(), null, null, null), "server-api");
        assertThat(after.seatId()).isEqualTo(first.seatId());
        assertThat(after.newSeat()).isFalse();
        assertThat(decode(after).factors()).isEqualTo(repaired.fp());
        assertThat(licenses.get(l.licenseId()).seatsUsed()).isEqualTo(1);
        // two modules replaced: another hardware, and the licence is full
        assertThatThrownBy(() -> issue(l.licenseId(), d.withModuleChanged(Factor.WIFI).withModuleChanged(Factor.BLUETOOTH).withModuleChanged(Factor.SYSTEM_SERIAL)))
                .hasMessageContaining("Plus de poste").hasMessageContaining("transfert");
        // a phone is a different seat from a TV, even with the same fingerprints
        assertThatThrownBy(() -> activations.issue(OWNER, new ActivationService.IssueRequest(l.licenseId(), "phone", d.text(), null, null, null), "server-api")).hasMessageContaining("Plus de poste");
        // a support account may re-issue an existing seat but cannot create one
        assertThatThrownBy(() -> activations.reissue(SUPPORT, l.licenseId(), "tv", dev().text(), "server-web")).hasMessageContaining("réservée au propriétaire");
        assertThatThrownBy(() -> activations.issue(SUPPORT, new ActivationService.IssueRequest(l.licenseId(), "tv", dev().text(), null, null, null), "server-web")).hasMessageContaining("rôle");
    }

    @Test
    void releasedSeatIsRevokedAndLaterActivationsAreIssuedAfterTheRevocation() {
        var l = license(1);
        Dev d = dev();
        var a1 = issue(l.licenseId(), d);
        licenses.releaseSeat(OWNER, l.licenseId(), a1.seatId(), "poste remplacé");
        // revocation list: the seat is revoked at a date >= the first activation's issue date
        Timestamp revoked = jdbc.queryForObject("select max(revoked_at) from lic_revocation where license_id = ? and seat_id = ?", Timestamp.class, l.licenseId(), a1.seatId());
        assertThat(revoked.toInstant()).isAfterOrEqualTo(a1.issuedAt());
        // another device takes the seat; the first can come back only when there is room, and then with an activation issued AFTER the revocation
        Dev other = dev();
        issue(l.licenseId(), other);
        assertThatThrownBy(() -> issue(l.licenseId(), d)).hasMessageContaining("Plus de poste");
        licenses.releaseSeat(OWNER, l.licenseId(), seatOf(l.licenseId(), other), "retour du premier");
        var back = issue(l.licenseId(), d);
        assertThat(back.seatId()).isEqualTo(a1.seatId());
        assertThat(back.reused()).isFalse(); // a fresh activation: the previous one is revoked
        Timestamp lastRevoked = jdbc.queryForObject("select max(revoked_at) from lic_revocation where license_id = ? and seat_id = ?", Timestamp.class, l.licenseId(), a1.seatId());
        assertThat(back.issuedAt()).as("issuedAt doit dépasser la date de révocation du poste (sinon l'activation serait révoquée dès l'installation)").isAfter(lastRevoked.toInstant());
        assertThat(licenses.get(l.licenseId()).seatsUsed()).isEqualTo(1);
    }

    @Test
    void aReleasedSeatComesBackOnlyOnTheSameDevice() {
        var l = license(1);
        Dev d = dev();
        var a1 = issue(l.licenseId(), d);
        licenses.releaseSeat(OWNER, l.licenseId(), a1.seatId(), "poste libéré");
        // the released seat row is bound to another device code (as after a hand edit): reviving it for this device is refused, the seat is not re-bound
        jdbc.update("update lic_seat set device_code = 'autre-appareil' where license_pk = (select id from lic_license where license_id = ?)", l.licenseId());
        assertThatThrownBy(() -> issue(l.licenseId(), d)).hasMessageContaining("même appareil");
        assertThat(jdbc.queryForObject("select state from lic_seat where license_pk = (select id from lic_license where license_id = ?)", String.class, l.licenseId())).isEqualTo("RELEASED");
        assertThat(jdbc.queryForObject("select device_code from lic_seat where license_pk = (select id from lic_license where license_id = ?)", String.class, l.licenseId())).isEqualTo("autre-appareil");
    }

    @Test
    void newLicencesGetATransferCapOfZeroByDefault() {
        assertThat(license(1).transferCap()).isZero();
    }

    @Test
    void expiryAndGracePeriod() {
        var l = license(2);
        Dev d = dev();
        var first = issue(l.licenseId(), d);
        // ended 3 days ago, grace 14 days: GRACE = only re-issue of an existing seat
        jdbc.update("update lic_license set end_at = ? where id = ?", Timestamp.from(Instant.now().minus(Duration.ofDays(3))), l.id());
        assertThat(licenses.get(l.licenseId()).effectiveState()).isEqualTo("GRACE");
        assertThatThrownBy(() -> issue(l.licenseId(), dev())).hasMessageContaining("grâce");
        assertThat(activations.reissueSeat(OWNER, l.licenseId(), first.seatId(), "server-api").text()).isNotBlank();
        // grace over: EXPIRED, nothing at all, then the sweep stores the state
        jdbc.update("update lic_license set end_at = ? where id = ?", Timestamp.from(Instant.now().minus(Duration.ofDays(30))), l.id());
        assertThat(licenses.get(l.licenseId()).effectiveState()).isEqualTo("EXPIRED");
        assertThatThrownBy(() -> activations.reissueSeat(OWNER, l.licenseId(), first.seatId(), "server-api")).hasMessageContaining("expirée");
        assertThat(licenses.expireDue()).isGreaterThanOrEqualTo(1);
        assertThat(licenses.get(l.licenseId()).state()).isEqualTo("EXPIRED");
        // extended past now: back to ACTIVE
        assertThat(licenses.extend(OWNER, l.licenseId(), Instant.now().plus(Duration.ofDays(60)), null).state()).isEqualTo("ACTIVE");
    }

    @Test
    void seatCountHoldsUnderConcurrentRequests() throws Exception {
        var l = license(3);
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Boolean>> res = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            Dev d = dev();
            Callable<Boolean> task = () -> {
                go.await();
                try {
                    issue(l.licenseId(), d);
                    return true;
                } catch (ApiException e) {
                    assertThat(e.getMessage()).contains("Plus de poste");
                    return false;
                }
            };
            res.add(pool.submit(task));
        }
        go.countDown();
        int ok = 0;
        for (Future<Boolean> f : res) if (f.get()) ok++;
        pool.shutdown();
        assertThat(ok).isEqualTo(3);
        assertThat(licenses.activeSeats(l.id())).isEqualTo(3);
        assertThat(jdbc.queryForList("select slot_no from lic_seat where license_pk = ? and state = 'ACTIVE' order by slot_no", Integer.class, l.id())).containsExactly(1, 2, 3);
        assertThat(jdbc.queryForObject("select count(*) from lic_issuance where license_pk = ?", Integer.class, l.id())).isEqualTo(3);
    }

    @Test
    void sameDeviceRequestedConcurrentlyIsOneSeatOneIssuance() throws Exception {
        var l = license(5);
        Dev d = dev();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<String>> res = new ArrayList<>();
        for (int i = 0; i < 8; i++) res.add(pool.submit(() -> { go.await(); return issue(l.licenseId(), d).text(); }));
        go.countDown();
        java.util.Set<String> texts = new java.util.HashSet<>();
        for (Future<String> f : res) texts.add(f.get());
        pool.shutdown();
        assertThat(texts).hasSize(1);
        assertThat(licenses.activeSeats(l.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from lic_issuance where license_pk = ?", Integer.class, l.id())).isEqualTo(1);
    }

    @Test
    void databaseConstraintStopsAnOverfullLicenceEvenIfTheServiceIsBypassed() {
        var l = license(1);
        jdbc.update("insert into lic_seat (license_pk, seat_id, device_code, factors, slot_no, state, first_seen, last_seen) values (?,?,?,?,1,'ACTIVE',now(),now())", l.id(), "0000000000000001", "AAAA-AAAA-AAAA-AAAA", "FLASH|" + rnd32());
        // a second ACTIVE seat on the same slot, or an ACTIVE seat without slot, or the same seat id twice: refused by the database itself
        assertThatThrownBy(() -> jdbc.update("insert into lic_seat (license_pk, seat_id, device_code, factors, slot_no, state, first_seen, last_seen) values (?,?,?,?,1,'ACTIVE',now(),now())", l.id(), "0000000000000002", "AAAA-AAAA-AAAA-AAAA", "x"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("insert into lic_seat (license_pk, seat_id, device_code, factors, slot_no, state, first_seen, last_seen) values (?,?,?,?,NULL,'ACTIVE',now(),now())", l.id(), "0000000000000003", "AAAA-AAAA-AAAA-AAAA", "x"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("insert into lic_seat (license_pk, seat_id, device_code, factors, slot_no, state, first_seen, last_seen) values (?,?,?,?,NULL,'RELEASED',now(),now())", l.id(), "0000000000000001", "AAAA-AAAA-AAAA-AAAA", "x"))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
    }

    @Test
    void inputsAreValidatedStrictly() {
        assertThatThrownBy(() -> DeviceIdentity.normalize("nope")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> DeviceIdentity.normalize("ABCD-EFGH-IJKL-MNO!")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> Validate.licenseId("x")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> Validate.licenseId("trial")).hasMessageContaining("réservé");
        assertThatThrownBy(() -> Validate.licenseId("list")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> Validate.licenseId("lic-<script>")).isInstanceOf(ApiException.class);
        assertThat(Validate.licenseId(" LIC-ABC-123 ")).isEqualTo("lic-abc-123");
        assertThatThrownBy(() -> Validate.seatId("zz")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> licenses.create(OWNER, new LicenseService.NewLicense(null, client().id(), "PAID", 0, null, null, null, null, null))).hasMessageContaining("postes");
        assertThatThrownBy(() -> licenses.create(OWNER, new LicenseService.NewLicense(null, client().id(), "PAID", 1, Instant.now(), Instant.now().minusSeconds(5), null, null, null))).hasMessageContaining("fin");
        assertThatThrownBy(() -> licenses.create(OWNER, new LicenseService.NewLicense(null, 99999L, "PAID", 1, null, null, null, null, null))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> licenses.create(OWNER, new LicenseService.NewLicense(null, client().id(), "PAID", 1, null, null, null, null, List.of("inconnu-xx")))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> products.create(OWNER, new ProductService.NewProduct("Bad Id", "x", "A_LA_CARTE", null, null, null, null))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> products.create(OWNER, new ProductService.NewProduct("abo-sans-duree", "x", "ABONNEMENT", null, null, null, null))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> products.create(OWNER, new ProductService.NewProduct("ok-prod", "x", "A_LA_CARTE", null, null, null, null, List.of("Bad Bundle")))).isInstanceOf(ApiException.class);
    }
}
