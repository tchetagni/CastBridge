package castbridge.server.licenses;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

import castbridge.server.web.ApiException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/** TOTP at login (owner), role changes, lock-out, privacy rights and abuse alerts. */
class LicenseSecurityTest extends LicenseTestBase {

    private ResultActions login(String user, String pass, String totp) throws Exception {
        var b = post("/admin/login").with(csrf()).param("username", user).param("password", pass);
        if (totp != null) b.param("totp", totp);
        return mvc.perform(b);
    }

    private static byte[] secretOf(LicenseAccounts.Enrollment e) { return Hashing.unbase32(e.secret()); }

    @Test
    void totpEnrolmentThenLoginNeedsTheCodeAndEachCodeWorksOnce() throws Exception {
        accounts.create(OWNER, "alice", "un-mot-de-passe-solide-1", "OWNER");
        Actor alice = new Actor("alice", Role.OWNER, "web", false);
        // before enrolment: the password is enough, and the owner cannot change anything yet (strong = false)
        login("alice", "un-mot-de-passe-solide-1", null).andExpect(redirectedUrl("/admin"));
        assertThatThrownBy(() -> licenses.create(alice, new LicenseService.NewLicense(null, client().id(), "PAID", 1, null, null, null, null, null))).hasMessageContaining("double authentification");

        var enrol = accounts.startEnrollment(alice);
        assertThat(enrol.uri()).startsWith("otpauth://totp/CastBridge:alice?secret=").contains("digits=6").contains("period=30");
        // the secret is stored encrypted, never in clear
        String stored = jdbc.queryForObject("select totp_secret_enc from admin_user where username = 'alice'", String.class);
        assertThat(stored).isNotBlank().doesNotContain(enrol.secret());
        long step = Totp.stepAt(Instant.now().getEpochSecond());
        assertThatThrownBy(() -> accounts.confirmEnrollment(alice, "000000")).isInstanceOf(ApiException.class);
        accounts.confirmEnrollment(alice, Totp.code(secretOf(enrol), step));
        assertThat(accounts.totpEnabled("alice")).isTrue();
        Actor strong = accounts.actorOf(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("alice", "x", java.util.List.of()));
        assertThat(strong.strong()).isTrue();
        assertThat(licenses.create(strong, new LicenseService.NewLicense(null, client().id(), "PAID", 1, null, null, null, null, null)).licenseId()).isNotBlank();

        // login: password alone is refused, a wrong code is refused, the right code (a later step than the one used to enrol) passes once
        login("alice", "un-mot-de-passe-solide-1", null).andExpect(redirectedUrl("/admin/login?erreur"));
        login("alice", "un-mot-de-passe-solide-1", "123456").andExpect(redirectedUrl("/admin/login?erreur"));
        login("alice", "mauvais-mot-de-passe", Totp.code(secretOf(enrol), step + 1)).andExpect(redirectedUrl("/admin/login?erreur"));
        String good = Totp.code(secretOf(enrol), step + 1);
        login("alice", "un-mot-de-passe-solide-1", good.substring(0, 3) + " " + good.substring(3)).andExpect(redirectedUrl("/admin"));
        login("alice", "un-mot-de-passe-solide-1", good).andExpect(redirectedUrl("/admin/login?erreur")); // replay refused
        login("alice", "un-mot-de-passe-solide-1", Totp.code(secretOf(enrol), step)).andExpect(redirectedUrl("/admin/login?erreur")); // older step refused
        // a code from the far past or future is refused (window of one step)
        login("alice", "un-mot-de-passe-solide-1", Totp.code(secretOf(enrol), step + 40)).andExpect(redirectedUrl("/admin/login?erreur"));
    }

    @Test
    void wrongSecondFactorCountsTowardTheLockout() throws Exception {
        accounts.create(OWNER, "bob", "un-mot-de-passe-solide-1", "OWNER");
        Actor bob = new Actor("bob", Role.OWNER, "web", false);
        var enrol = accounts.startEnrollment(bob);
        accounts.confirmEnrollment(bob, Totp.code(secretOf(enrol), Totp.stepAt(Instant.now().getEpochSecond())));
        for (int i = 0; i < 5; i++) login("bob", "un-mot-de-passe-solide-1", "000000").andExpect(redirectedUrl("/admin/login?erreur"));
        // locked: even the right password and a fresh right code are refused
        login("bob", "un-mot-de-passe-solide-1", Totp.code(secretOf(enrol), Totp.stepAt(Instant.now().getEpochSecond()) + 1)).andExpect(redirectedUrl("/admin/login?erreur"));
        assertThat(jdbc.queryForObject("select locked_until from admin_user where username = 'bob'", java.sql.Timestamp.class)).isNotNull();
    }

    @Test
    void rolesAndAccountRules() {
        accounts.create(OWNER, "carol", "un-mot-de-passe-solide-1", "SUPPORT");
        assertThat(accounts.roleOf("carol")).isEqualTo(Role.SUPPORT);
        assertThatThrownBy(() -> accounts.create(OWNER, "carol", "un-mot-de-passe-solide-1", "SUPPORT")).hasMessageContaining("existe");
        assertThatThrownBy(() -> accounts.create(OWNER, "dave", "court", "SUPPORT")).hasMessageContaining("12");
        assertThatThrownBy(() -> accounts.create(OWNER, "dave", "un-mot-de-passe-solide-1", "ROOT")).hasMessageContaining("Rôle");
        assertThatThrownBy(() -> accounts.create(OWNER, "d'; drop", "un-mot-de-passe-solide-1", "SUPPORT")).hasMessageContaining("Identifiant");
        assertThatThrownBy(() -> accounts.create(SUPPORT, "erin", "un-mot-de-passe-solide-1", "SUPPORT")).hasMessageContaining("rôle");
        // the last owner cannot be demoted
        jdbc.update("update admin_user set role = 'READONLY' where role = 'OWNER' and username <> 'esaie'");
        assertThatThrownBy(() -> accounts.setRole(OWNER, "esaie", "SUPPORT", null)).hasMessageContaining("au moins un propriétaire");
        accounts.setRole(OWNER, "carol", "READONLY", "changement d'équipe");
        assertThat(accounts.roleOf("carol")).isEqualTo(Role.READONLY);
        // an owner can switch the second factor off for a lost phone, with a reason
        assertThatThrownBy(() -> accounts.disableTotp(OWNER, "carol", "")).hasMessageContaining("motif");
    }

    @Test
    void erasureAnonymizesSeatsWithoutBreakingTheCount() {
        ensureProducts();
        var c = clients.create(OWNER, "Madame Exemple", "exemple@example.invalid", "note libre");
        var l = licenses.create(OWNER, new LicenseService.NewLicense(null, c.id(), "PAID", 3, null, null, null, null, List.of("p-test")));
        Dev d1 = dev(), d2 = dev();
        var a1 = issue(l.licenseId(), d1);
        var a2 = issue(l.licenseId(), d2);
        abuse.sighting(d1.code(), "ip", "203.0.113.7");
        assertThatThrownBy(() -> clients.erase(SUPPORT, c.id(), "demande")).hasMessageContaining("rôle");

        var export = clients.export(OWNER, c.id());
        assertThat(export.toString()).contains("Madame Exemple").contains(d1.code()).contains(l.licenseId());
        assertThatThrownBy(() -> clients.erase(OWNER, c.id(), "")).hasMessageContaining("motif");
        assertThat(registry.all(1000)).extracting(RegistryStore.Stored::text).anyMatch(t -> t.contains("seat=" + a1.seatId()));

        var erased = clients.erase(OWNER, c.id(), "demande écrite du client");
        assertThat(erased.erasedAt()).isNotNull();
        assertThat(erased.name()).startsWith("Client effacé").doesNotContain("Exemple");
        assertThat(erased.contact()).isNull();
        assertThat(erased.notes()).isNull();
        // the count is intact: 2 seats still used out of 3, rows kept, only anonymized
        var after = licenses.get(l.licenseId());
        assertThat(after.seatsUsed()).isEqualTo(2);
        assertThat(after.seatsAllowed()).isEqualTo(3);
        var seats = licenses.detail(l.licenseId()).seats();
        assertThat(seats).hasSize(2).allSatisfy(s -> {
            assertThat(s.anonymized()).isTrue();
            assertThat(s.deviceCode()).startsWith("ANON-").doesNotContain(d1.code()).doesNotContain(d2.code());
            assertThat(s.factors()).isZero();
            assertThat(s.state()).isEqualTo("ACTIVE");
        });
        assertThat(jdbc.queryForObject("select count(*) from lic_sighting where device_code = ?", Integer.class, d1.code())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from lic_issuance where device_code in (?, ?)", Integer.class, d1.code(), d2.code())).isZero();
        // the registry copies of the seat's events (they carry the hardware fingerprints) are no longer kept nor exported
        assertThat(registry.all(1000)).extracting(RegistryStore.Stored::text).noneMatch(t -> t.contains("seat=" + a1.seatId()) || t.contains("seat=" + a2.seatId()));
        assertThat(jdbc.queryForObject("select count(*) from lic_event where seat_id = ? and erased = TRUE and text is null", Integer.class, a1.seatId())).isPositive();
        assertThat(clients.export(OWNER, c.id()).toString()).doesNotContain("Exemple").doesNotContain(d1.code());
        // an anonymized seat cannot be re-issued from its (erased) hardware; a new device takes the third seat; the quota still holds
        assertThatThrownBy(() -> activations.reissueSeat(OWNER, l.licenseId(), a1.seatId(), "server-api")).hasMessageContaining("anonymisé");
        issue(l.licenseId(), dev());
        assertThatThrownBy(() -> issue(l.licenseId(), dev())).hasMessageContaining("Plus de poste");
        assertThatThrownBy(() -> clients.erase(OWNER, c.id(), "encore")).hasMessageContaining("déjà effacé");
        assertThat(audit.verify().ok()).isTrue();
        String audited = jdbc.queryForList("select concat(coalesce(details,''), coalesce(reason,''), target_id) from lic_audit", String.class).toString();
        assertThat(audited).doesNotContain("Madame").doesNotContain("exemple@example.invalid").doesNotContain(d1.code());
        // importing the same events again does not bring the erased data back
        // (the tombstone row keeps their ids)
        assertThat(jdbc.queryForObject("select count(*) from lic_event where seat_id = ? and text is not null", Integer.class, a1.seatId())).isZero();
    }

    @Test
    void abuseAlertsWarnButNeverBlock() throws Exception {
        long now = System.currentTimeMillis();
        // 1. the same hardware on two licences
        var a = license(2);
        var b = license(2);
        Dev shared = dev();
        issue(a.licenseId(), shared);
        issue(b.licenseId(), shared);
        // 2. one device code from three IPs and two phones
        Dev roaming = dev();
        for (String ip : new String[] {"198.51.100.1", "198.51.100.2", "198.51.100.3"}) abuse.sighting(roaming.code(), "ip", ip);
        abuse.sighting(roaming.code(), "phone", "phone-A");
        abuse.sighting(roaming.code(), "phone", "phone-B");
        abuse.sighting(roaming.code(), "phone", "phone-B"); // twice from the same phone: still two
        // 3. a burst of issuances
        var burst = license(30);
        for (int i = 0; i < 10; i++) issue(burst.licenseId(), dev());
        // 4. repeated transfers (via the registry)
        String tr = "lic-" + Long.toString(RND.nextLong() & 0xffffffL, 36);
        Dev t1 = dev(), t2 = dev(), t3 = dev();
        String seat = seatOf(tr, t1);
        importReview(List.of(licenseEvent(DESKTOP, now - 6 * 86_400_000L, tr, 2, 2), issueEvent(DESKTOP, now - 5 * 86_400_000L, tr, seat, "tv", "production", t1, nonce()),
                transferEvent(DESKTOP, now - 4 * 86_400_000L, tr, seat, t1, nonce()), transferEvent(PHONE, now - 3 * 86_400_000L, tr, seat, t1, nonce())));
        // 5. a quota overflow waiting for a decision
        String over = "lic-" + Long.toString(RND.nextLong() & 0xffffffL, 36);
        importReview(List.of(licenseEvent(DESKTOP, now - 3 * 86_400_000L, over, 1, 2), issueEvent(DESKTOP, now - 2 * 86_400_000L, over, "1111111111111111", "tv", "production", dev(), nonce()),
                issueEvent(PHONE, now - 86_400_000L, over, "2222222222222222", "tv", "production", dev(), nonce())));

        var alerts = abuse.alerts();
        assertThat(alerts).extracting(AbuseService.Alert::type).contains("DUPLICATE_DEVICE", "MULTI_SOURCE", "ISSUANCE_BURST", "TRANSFER_CAP", "TRANSFER_REPEAT", "OVER_QUOTA_PENDING");
        assertThat(alerts).filteredOn(x -> x.type().equals("MULTI_SOURCE")).extracting(AbuseService.Alert::subject).contains(DeviceIdentity.masked(roaming.code()));
        assertThat(alerts).filteredOn(x -> x.type().equals("ISSUANCE_BURST")).extracting(AbuseService.Alert::subject).contains(burst.licenseId());
        // nothing was blocked automatically: every licence involved is still ACTIVE and can still issue
        for (var l : new LicenseService.LicenseRow[] {a, b, burst}) assertThat(licenses.get(l.licenseId()).state()).isEqualTo("ACTIVE");
        issue(burst.licenseId(), dev());

        // dashboard numbers
        var trial = licenses.create(OWNER, new LicenseService.NewLicense(null, client().id(), "TRIAL", null, null, Instant.now().plusSeconds(86400L * 20), null, null, null));
        long trialsBefore = ((Number) abuse.dashboard().get("trialsIssued")).longValue();
        assertThat(issue(trial.licenseId(), dev()).kind()).isEqualTo("TRIAL");
        var d = abuse.dashboard();
        assertThat(((Number) d.get("trialsIssued")).longValue()).isEqualTo(trialsBefore + 1);
        assertThat(((Number) d.get("expiring30")).longValue()).isGreaterThanOrEqualTo(1);
        assertThat(((Number) d.get("seatsUsed")).longValue()).isGreaterThan(10);
        assertThat(((Number) d.get("suspectDuplicates")).longValue()).isGreaterThanOrEqualTo(2);
        // a trial licence cannot hand out a paid activation
        assertThatThrownBy(() -> activations.issue(OWNER, new ActivationService.IssueRequest(trial.licenseId(), "tv", dev().text(), "PRODUCTION", null, null), "server-api")).isInstanceOf(ApiException.class);
    }
}
