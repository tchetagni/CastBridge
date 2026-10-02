package castbridge.server.licenses;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import castbridge.server.licenses.ActivationSigner.IssueKind;
import castbridge.server.web.ApiException;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Key duration chosen at issuance (usage ceiling, docs/ACTIVATION-FORMAT.md; docs/TRIAL-EDITION.md § 15). */
class LicenseKeyDurationTest extends LicenseTestBase {
    private static final long DAY = WireActivation.DAY_MS;
    @org.springframework.beans.factory.annotation.Autowired private LicenseProperties props;

    private WireActivation.Fields decode(ActivationService.Activation a) {
        var d = WireActivation.decode(a.text());
        assertThat(d).isNotNull();
        return d.fields();
    }

    private ActivationService.Activation issueDays(String lic, String days) {
        return activations.issue(OWNER, new ActivationService.IssueRequest(lic, "tv", dev().text(), null, null, null, days), "server-api");
    }

    private LicenseService.LicenseRow trialLicense() {
        return licenses.create(OWNER, new LicenseService.NewLicense(null, client().id(), "TRIAL", null, null, null, null, null, null));
    }

    private static List<String> usage(WireActivation.Fields f) { return f.rights().stream().filter(WireActivation::isUsage).toList(); }

    @Test
    void trialDefaultsTo30DaysAndKeepsOnlyTheUsageLine() {
        var a = issueDays(trialLicense().licenseId(), null);
        var f = decode(a);
        assertThat(usage(f)).hasSize(1);
        String[] u = usage(f).get(0).split("\\|");
        assertThat(Long.parseLong(u[2])).isEqualTo(f.issuedAt());
        assertThat(Long.parseLong(u[3]) - Long.parseLong(u[2])).isEqualTo(30 * DAY);
        assertThat(f.rights()).allMatch(WireActivation::isTrialRight).noneMatch(WireActivation::isRental);
        assertThat(a.usageDays()).isEqualTo(30);
        assertThat(a.usageEnd().toEpochMilli()).isEqualTo(f.issuedAt() + 30 * DAY);
        assertThat(a.properties()).contains("essai").contains("30 jours");
    }

    @Test
    void trialBoundsAndNeverUnlimited() {
        var t = trialLicense();
        assertThatThrownBy(() -> issueDays(t.licenseId(), "400")).isInstanceOf(ApiException.class).hasMessageContaining("1 à 365");
        assertThatThrownBy(() -> issueDays(t.licenseId(), "0")).hasMessageContaining("1 à 365");
        assertThatThrownBy(() -> issueDays(t.licenseId(), "-3")).hasMessageContaining("1 à 365");
        assertThatThrownBy(() -> issueDays(t.licenseId(), "illimitée")).hasMessageContaining("jamais illimitée");
        assertThatThrownBy(() -> issueDays(t.licenseId(), "abc")).hasMessageContaining("nombre de jours");
        // refusals consume nothing: the single seat is still free, and the bounds 1 and 365 are accepted
        assertThat(licenses.get(t.licenseId()).seatsUsed()).isZero();
        var one = licenses.create(OWNER, new LicenseService.NewLicense(null, client().id(), "TRIAL", null, null, null, null, null, null));
        var max = licenses.create(OWNER, new LicenseService.NewLicense(null, client().id(), "TRIAL", null, null, null, null, null, null));
        assertThat(usage(decode(issueDays(one.licenseId(), "1"))).get(0)).matches("usage\\|duree\\|\\d+\\|\\d+");
        var f = decode(issueDays(max.licenseId(), "365"));
        String[] u = usage(f).get(0).split("\\|");
        assertThat(Long.parseLong(u[3]) - Long.parseLong(u[2])).isEqualTo(365 * DAY);
    }

    @Test
    void productionIsUnlimitedByDefaultAndCarriesUsageOnlyWhenAsked() {
        var l = license(10);
        var unlimited = decode(issueDays(l.licenseId(), null));
        assertThat(usage(unlimited)).isEmpty();
        assertThat(unlimited.rights()).isNotEmpty();
        var explicit = issueDays(l.licenseId(), "illimitee");
        assertThat(usage(decode(explicit))).isEmpty();
        assertThat(explicit.usageDays()).isNull();
        assertThat(explicit.usageEnd()).isNull();
        assertThat(explicit.properties()).contains("production").contains("illimitée");
        var sixtyTwo = issueDays(l.licenseId(), "62");
        var f = decode(sixtyTwo);
        assertThat(usage(f)).hasSize(1);
        String[] u = usage(f).get(0).split("\\|");
        assertThat(Long.parseLong(u[3]) - Long.parseLong(u[2])).isEqualTo(62 * DAY);
        assertThat(f.rights()).anyMatch(r -> r.startsWith("purchase|"));
        assertThat(sixtyTwo.properties()).contains("62 jours");
    }

    @Test
    void productionBounds() {
        var l = license(10);
        assertThatThrownBy(() -> issueDays(l.licenseId(), "3661")).hasMessageContaining("1 à 3660");
        assertThatThrownBy(() -> issueDays(l.licenseId(), "0")).hasMessageContaining("1 à 3660");
        assertThat(usage(decode(issueDays(l.licenseId(), "1")))).hasSize(1);
        var f = decode(issueDays(l.licenseId(), "3660"));
        String[] u = usage(f).get(0).split("\\|");
        assertThat(Long.parseLong(u[3]) - Long.parseLong(u[2])).isEqualTo(3660 * DAY);
    }

    @Test
    void sameRequestSameDurationIsIdempotentAndAnotherDurationIsAnotherActivation() {
        var l = license(3);
        var d = dev();
        var first = activations.issue(OWNER, new ActivationService.IssueRequest(l.licenseId(), "tv", d.text(), null, null, null, "62"), "server-api");
        var again = activations.issue(OWNER, new ActivationService.IssueRequest(l.licenseId(), "tv", d.text(), null, null, null, "62"), "server-api");
        assertThat(again.reused()).isTrue();
        assertThat(again.text()).isEqualTo(first.text());
        var other = activations.issue(OWNER, new ActivationService.IssueRequest(l.licenseId(), "tv", d.text(), null, null, null, "90"), "server-api");
        assertThat(other.text()).isNotEqualTo(first.text());
        assertThat(licenses.get(l.licenseId()).seatsUsed()).isEqualTo(1);
    }

    @Test
    void theServerNeverCombinesSuperAndTheSignerBoundsTheUsageLine() {
        var d = dev();
        long now = System.currentTimeMillis();
        var scoped = ScopedActivationSigner.server(new Ed25519ActivationSigner(keyring));
        String purchase = "purchase|p-test|classe-test|" + now;
        String usage = ActivationService.usageLine(now, 62);
        var withSuper = new ActivationSigner.ActivationRequest(IssueKind.PRODUCTION, "tv", "lic-0001", null, d.request(), List.of(purchase, usage, "super|tout|" + now), now, now, 30, "00112233");
        assertThatThrownBy(() -> scoped.sign(withSuper)).isInstanceOf(ApiException.class).hasMessageContaining("SUPER_UNLIMITED");
        // production with only a usage line, or with no right at all: accepted (full version, duration only)
        var onlyUsage = new ActivationSigner.ActivationRequest(IssueKind.PRODUCTION, "tv", "lic-0001", null, d.request(), List.of(usage), now, now, 30, "00112233");
        assertThat(scoped.sign(onlyUsage).text()).startsWith("cbx1");
        var noRights = new ActivationSigner.ActivationRequest(IssueKind.PRODUCTION, "tv", "lic-0001", null, d.request(), List.of(), now, now, 30, "00112234");
        assertThat(scoped.sign(noRights).text()).startsWith("cbx1");
        var trialLic = new ActivationSigner.ActivationRequest(IssueKind.PRODUCTION, "tv", "trial", null, d.request(), List.of(), now, now, 30, "00112235");
        assertThatThrownBy(() -> scoped.sign(trialLic)).isInstanceOf(ApiException.class);
        // out of bounds or duplicated usage lines
        var tooLong = new ActivationSigner.ActivationRequest(IssueKind.PRODUCTION, "tv", "lic-0001", null, d.request(), List.of(purchase, ActivationService.usageLine(now, 3661)), now, now, 30, "00112233");
        assertThatThrownBy(() -> scoped.sign(tooLong)).hasMessageContaining("1 à 3660");
        var trialLong = new ActivationSigner.ActivationRequest(IssueKind.TRIAL, "tv", "trial", null, d.request(), List.of(ActivationService.usageLine(now, 400)), now, now, 30, "00112233");
        assertThatThrownBy(() -> scoped.sign(trialLong)).hasMessageContaining("1 à 365");
        var two = new ActivationSigner.ActivationRequest(IssueKind.PRODUCTION, "tv", "lic-0001", null, d.request(), List.of(purchase, usage, ActivationService.usageLine(now, 10)), now, now, 30, "00112233");
        assertThatThrownBy(() -> scoped.sign(two)).hasMessageContaining("au plus un plafond");
    }

    @Test
    void auditLineShowsTheKeyPropertiesAndNoSecret() {
        var l = license(1);
        var a = issueDays(l.licenseId(), "62");
        String details = jdbc.queryForList("select coalesce(details, '') from lic_audit where action = 'ACTIVATION_ISSUE' and target_id = ?", String.class, l.licenseId()).toString();
        assertThat(details).contains("production").contains("62 jours").contains("edition").doesNotContain(a.text()).doesNotContain("cbx1.");
    }

    private int countLicenses() { return jdbc.queryForObject("select count(*) from lic_license", Integer.class); }

    @Test
    void productionWithoutLicenceGeneratesOneAndAcceptsNoRights() {
        for (String blank : new String[] {null, "", "  ", "auto", "AUTO"}) {
            int before = countLicenses();
            var a = issueDays(blank, "30");
            assertThat(countLicenses()).isEqualTo(before + 1);
            assertThat(a.licenseId()).matches("lic-[0-9a-f]{10}");
            assertThat(a.kind()).isEqualTo("PRODUCTION");
            var f = decode(a);
            assertThat(f.license()).isEqualTo(a.licenseId());
            assertThat(f.rights()).hasSize(1).allMatch(WireActivation::isUsage);
            var row = licenses.get(a.licenseId());
            assertThat(row.seatsAllowed()).isEqualTo(1);
            assertThat(row.seatsUsed()).isEqualTo(1);
            assertThat(row.kind()).isEqualTo("PAID");
            assertThat(row.transferCap()).isEqualTo(props.defaultTransferCap());
        }
        // illimitée: no right at all
        var unl = issueDays("auto", "illimitee");
        assertThat(decode(unl).rights()).isEmpty();
        assertThat(unl.licenseId()).matches("lic-[0-9a-f]{10}");
        // unique ids
        var ids = new java.util.HashSet<String>();
        for (int i = 0; i < 5; i++) ids.add(issueDays(null, null).licenseId());
        assertThat(ids).hasSize(5);
    }

    @Test
    void generatedLicenceIsAuditedWithoutSecrets() {
        var a = issueDays(null, "10");
        String created = jdbc.queryForList("select coalesce(details, '') from lic_audit where action = 'LICENSE_CREATE' and target_id = ?", String.class, a.licenseId()).toString();
        assertThat(created).isNotEqualTo("[]");
        String issued = jdbc.queryForList("select coalesce(details, '') from lic_audit where action = 'ACTIVATION_ISSUE' and target_id = ?", String.class, a.licenseId()).toString();
        assertThat(issued).contains("licenseAuto").contains("10 jours").doesNotContain(a.text()).doesNotContain("cbx1.");
        assertThat(created).doesNotContain(a.text()).doesNotContain("cbx1.");
    }

    @Test
    void aGivenLicenceIsReusedAndABadRequestCreatesNoLicence() {
        var l = license(2);
        int before = countLicenses();
        var a = issueDays(l.licenseId(), "5");
        var b = issueDays(l.licenseId(), "5");
        assertThat(a.licenseId()).isEqualTo(l.licenseId());
        assertThat(b.licenseId()).isEqualTo(l.licenseId());
        assertThat(countLicenses()).isEqualTo(before);
        // refused request (bad duration / bad device) rolls the generated licence back or never creates it
        assertThatThrownBy(() -> issueDays(null, "4000")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> activations.issue(OWNER, new ActivationService.IssueRequest(null, "tv", "code=12345", null, null, null, "5"), "server-api")).isInstanceOf(ApiException.class);
        assertThat(countLicenses()).isEqualTo(before);
        // support may not create a licence
        assertThatThrownBy(() -> activations.issue(SUPPORT, new ActivationService.IssueRequest(null, "tv", dev().text(), null, null, null, "5"), "server-web")).isInstanceOf(ApiException.class);
        assertThat(countLicenses()).isEqualTo(before);
    }

    @Test
    void aBundleLessPaidLicenceIssuesAFullVersionKey() {
        var l = licenses.create(OWNER, new LicenseService.NewLicense(null, client().id(), "PAID", 1, null, null, null, null, null));
        var a = issueDays(l.licenseId(), "20");
        assertThat(decode(a).rights()).hasSize(1).allMatch(WireActivation::isUsage);
    }

    @Test
    void generatedLicenceHasOneSeatSoReactivationWorksButASecondDeviceNeedsATransfer() {
        var d = dev();
        var a = activations.issue(OWNER, new ActivationService.IssueRequest(null, "tv", d.text(), null, null, null, "30"), "server-api");
        var again = activations.issue(OWNER, new ActivationService.IssueRequest(a.licenseId(), "tv", d.text(), null, null, null, "30"), "server-api");
        assertThat(again.reused()).isTrue();
        assertThat(again.fingerprint()).isEqualTo(a.fingerprint());
        assertThatThrownBy(() -> activations.issue(OWNER, new ActivationService.IssueRequest(a.licenseId(), "tv", dev().text(), null, null, null, "30"), "server-api")).isInstanceOf(ApiException.class);
    }
}
