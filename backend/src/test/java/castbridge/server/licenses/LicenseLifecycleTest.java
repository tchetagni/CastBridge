package castbridge.server.licenses;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import castbridge.server.web.ApiException;
import java.time.Duration;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class LicenseLifecycleTest extends LicenseTestBase {

    @Test
    void createModifySuspendRevokeExtend() {
        products.create(OWNER, new ProductService.NewProduct("classe-3e", "Classe de 3e", "ABONNEMENT", 365, List.of("learn/3e", "quiz/3e"), null, null));
        var c = client();
        var l = licenses.create(OWNER, new LicenseService.NewLicense(null, c.id(), "PAID", 2, null, Instant.now().plus(Duration.ofDays(30)), null, null, List.of("classe-3e")));
        assertThat(l.licenseId()).matches(Validate.LICENSE_ID).startsWith("LIC-");
        assertThat(l.seatsAllowed()).isEqualTo(2);
        assertThat(licenses.detail(l.licenseId()).products()).extracting(LicenseService.ProductRef::productId).containsExactly("classe-3e");

        // a destructive action without a reason is refused
        assertThatThrownBy(() -> licenses.suspend(OWNER, l.licenseId(), " ")).isInstanceOf(ApiException.class).hasMessageContaining("motif");
        assertThat(licenses.suspend(OWNER, l.licenseId(), "impayé constaté").state()).isEqualTo("SUSPENDED");
        assertThatThrownBy(() -> issue(l.licenseId(), code())).hasMessageContaining("suspendue");
        assertThat(licenses.resume(OWNER, l.licenseId(), "paiement reçu").state()).isEqualTo("ACTIVE");

        // extend forward: no reason needed; shorten: reason needed
        Instant later = Instant.now().plus(Duration.ofDays(400));
        assertThat(licenses.extend(OWNER, l.licenseId(), later, null).endAt()).isBetween(later.minusSeconds(1), later.plusSeconds(1));
        assertThatThrownBy(() -> licenses.extend(OWNER, l.licenseId(), Instant.now().plus(Duration.ofDays(10)), null)).hasMessageContaining("motif");

        // seats cannot go below the used ones
        String d1 = code(), d2 = code();
        issue(l.licenseId(), d1);
        issue(l.licenseId(), d2);
        assertThatThrownBy(() -> licenses.setSeats(OWNER, l.licenseId(), 1, "réduction")).hasMessageContaining("utilisés");
        assertThat(licenses.setSeats(OWNER, l.licenseId(), 5, null).seatsAllowed()).isEqualTo(5);

        // revoke: final, seats freed, in the revocation list, nothing issued any more
        var r = licenses.revoke(OWNER, l.licenseId(), "fraude avérée");
        assertThat(r.state()).isEqualTo("REVOKED");
        assertThat(r.seatsUsed()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from lic_revocation where license_id = ?", Integer.class, l.licenseId())).isEqualTo(1);
        assertThatThrownBy(() -> issue(l.licenseId(), code())).hasMessageContaining("révoquée");
        assertThatThrownBy(() -> licenses.extend(OWNER, l.licenseId(), later, null)).isInstanceOf(ApiException.class);
    }

    @Test
    void reactivationOfSameHardwareConsumesNoSeatAndGivesSameActivation() {
        var l = license(1);
        String dev = code();
        var first = issue(l.licenseId(), dev);
        assertThat(first.reused()).isFalse();
        // same hardware, app reinstalled: the same request any number of times
        var again = issue(l.licenseId(), dev);
        var viaReissue = activations.reissue(SUPPORT, l.licenseId(), dev, "server-web");
        assertThat(again.reused()).isTrue();
        assertThat(again.text()).isEqualTo(first.text());
        assertThat(viaReissue.text()).isEqualTo(first.text());
        assertThat(again.fingerprint()).isEqualTo(first.fingerprint());
        assertThat(licenses.get(l.licenseId()).seatsUsed()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from lic_issuance where license_pk = ?", Integer.class, l.id())).isEqualTo(1);
        // only the fingerprint is stored, never the activation
        assertThat(jdbc.queryForObject("select token_fingerprint from lic_issuance where license_pk = ?", String.class, l.id())).isEqualTo(first.fingerprint());
        // the licence is full for ANOTHER device
        assertThatThrownBy(() -> issue(l.licenseId(), code())).hasMessageContaining("Quota");
        // freeing the seat lets another device in; the first can come back only if there is room
        licenses.releaseSeat(OWNER, l.licenseId(), dev, "poste remplacé");
        String other = code();
        issue(l.licenseId(), other);
        assertThatThrownBy(() -> issue(l.licenseId(), dev)).hasMessageContaining("Quota");
    }

    @Test
    void expiryAndGracePeriod() {
        var l = license(2);
        String dev = code();
        issue(l.licenseId(), dev);
        // ended 3 days ago, grace 14 days: GRACE = only re-issue of an existing seat
        jdbc.update("update lic_license set end_at = ? where id = ?", Timestamp.from(Instant.now().minus(Duration.ofDays(3))), l.id());
        assertThat(licenses.get(l.licenseId()).effectiveState()).isEqualTo("GRACE");
        assertThatThrownBy(() -> issue(l.licenseId(), code())).hasMessageContaining("grâce");
        assertThat(activations.reissue(OWNER, l.licenseId(), dev, "server-api").text()).isNotBlank();
        // grace over: EXPIRED, nothing at all, then the sweep stores the state
        jdbc.update("update lic_license set end_at = ? where id = ?", Timestamp.from(Instant.now().minus(Duration.ofDays(30))), l.id());
        assertThat(licenses.get(l.licenseId()).effectiveState()).isEqualTo("EXPIRED");
        assertThatThrownBy(() -> activations.reissue(OWNER, l.licenseId(), dev, "server-api")).hasMessageContaining("expirée");
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
            String dev = code();
            Callable<Boolean> task = () -> {
                go.await();
                try {
                    issue(l.licenseId(), dev);
                    return true;
                } catch (ApiException e) {
                    assertThat(e.getMessage()).contains("Quota");
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
        String dev = code();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<String>> res = new ArrayList<>();
        for (int i = 0; i < 8; i++) res.add(pool.submit(() -> { go.await(); return issue(l.licenseId(), dev).text(); }));
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
        jdbc.update("insert into lic_seat (license_pk, device_code, slot_no, state, first_seen, last_seen) values (?,?,1,'ACTIVE',now(),now())", l.id(), code());
        // a second ACTIVE seat on the same slot, or an ACTIVE seat without slot, is refused by the database itself
        assertThatThrownBy(() -> jdbc.update("insert into lic_seat (license_pk, device_code, slot_no, state, first_seen, last_seen) values (?,?,1,'ACTIVE',now(),now())", l.id(), code()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("insert into lic_seat (license_pk, device_code, slot_no, state, first_seen, last_seen) values (?,?,NULL,'ACTIVE',now(),now())", l.id(), code()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void inputsAreValidatedStrictly() {
        assertThatThrownBy(() -> DeviceCode.normalize("nope")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> DeviceCode.normalize("ABCD-EFGH-IJKL-MNO!")).isInstanceOf(ApiException.class);
        assertThat(DeviceCode.normalize(" abcd efgh 2345 6789 ")).isEqualTo("ABCD-EFGH-2345-6789");
        assertThat(DeviceCode.normalize("oooo-iiii-0000-1111")).isEqualTo("0000-1111-0000-1111");
        assertThatThrownBy(() -> Validate.licenseId("x")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> Validate.licenseId("LIC-<script>")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> licenses.create(OWNER, new LicenseService.NewLicense(null, client().id(), "PAID", 0, null, null, null, null, null))).hasMessageContaining("postes");
        assertThatThrownBy(() -> licenses.create(OWNER, new LicenseService.NewLicense(null, client().id(), "PAID", 1, Instant.now(), Instant.now().minusSeconds(5), null, null, null))).hasMessageContaining("fin");
        assertThatThrownBy(() -> licenses.create(OWNER, new LicenseService.NewLicense(null, 99999L, "PAID", 1, null, null, null, null, null))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> licenses.create(OWNER, new LicenseService.NewLicense(null, client().id(), "PAID", 1, null, null, null, null, List.of("inconnu-xx")))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> products.create(OWNER, new ProductService.NewProduct("Bad Id", "x", "A_LA_CARTE", null, null, null, null))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> products.create(OWNER, new ProductService.NewProduct("abo-sans-duree", "x", "ABONNEMENT", null, null, null, null))).isInstanceOf(ApiException.class);
    }
}
