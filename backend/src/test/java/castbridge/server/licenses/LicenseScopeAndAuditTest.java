package castbridge.server.licenses;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import castbridge.server.licenses.ActivationSigner.ActivationRequest;
import castbridge.server.licenses.ActivationSigner.IssueKind;
import castbridge.server.licenses.ActivationSigner.SignerScope;
import castbridge.server.web.ApiException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class LicenseScopeAndAuditTest extends LicenseTestBase {

    private int count(String table) { return jdbc.queryForObject("select count(*) from " + table, Integer.class); }

    @Test
    void serverKeyScopeIsFixedInCode() {
        assertThat(ScopedActivationSigner.SERVER_SCOPES).containsExactlyInAnyOrder(SignerScope.ISSUE_TRIAL, SignerScope.ISSUE_PURCHASE, SignerScope.ISSUE_SUBSCRIPTION, SignerScope.REACTIVATE)
                .doesNotContain(SignerScope.TRANSFER, SignerScope.OPEN_ALL);
        assertThatThrownBy(() -> ScopedActivationSigner.SERVER_SCOPES.add(SignerScope.TRANSFER)).isInstanceOf(UnsupportedOperationException.class);
        assertThat(activations.serverKinds()).containsExactlyInAnyOrder("TRIAL", "PURCHASE", "SUBSCRIPTION", "REACTIVATION");
    }

    @Test
    void scopedSignerRefusesTransferAndOpenAllEvenIfTheDelegateCouldSign() {
        // a delegate that WOULD sign anything: the wrapper must stop the request before it gets there
        ActivationSigner everything = new ActivationSigner() {
            public String kid() { return "k"; }
            public java.util.Set<SignerScope> scopes() { return java.util.Set.of(SignerScope.values()); }
            public SignedActivation sign(ActivationRequest r) { return new SignedActivation("SIGNED", "k", "00", "ff"); }
        };
        var server = ScopedActivationSigner.server(everything);
        byte[] nonce = new byte[16];
        for (IssueKind ok : new IssueKind[] {IssueKind.TRIAL, IssueKind.PURCHASE, IssueKind.SUBSCRIPTION, IssueKind.REACTIVATION}) {
            assertThat(server.sign(new ActivationRequest("LIC-AAAAA-BBBBB", "ABCD-1234-ABCD-1234", ok, List.of("a"), Instant.now(), null, nonce, 1)).text()).isEqualTo("SIGNED");
        }
        assertThatThrownBy(() -> server.sign(new ActivationRequest("LIC-AAAAA-BBBBB", "ABCD-1234-ABCD-1234", IssueKind.TRANSFER, List.of(), Instant.now(), null, nonce, 1)))
                .isInstanceOf(ApiException.class).hasMessageContaining("transfert");
        assertThatThrownBy(() -> server.sign(new ActivationRequest("LIC-AAAAA-BBBBB", "ABCD-1234-ABCD-1234", IssueKind.OPEN_ALL, List.of(), Instant.now(), null, nonce, 1)))
                .isInstanceOf(ApiException.class).hasMessageContaining("tout ouvrir");
        assertThatThrownBy(() -> server.sign(new ActivationRequest("LIC-AAAAA-BBBBB", "ABCD-1234-ABCD-1234", IssueKind.PURCHASE, List.of("*"), Instant.now(), null, nonce, 1)))
                .isInstanceOf(ApiException.class).hasMessageContaining("tout ouvrir");
        // the offline tools' scope (desktop) is a different wrapper and may transfer
        var desktop = new ScopedActivationSigner(everything, java.util.EnumSet.allOf(SignerScope.class));
        assertThat(desktop.sign(new ActivationRequest("LIC-AAAAA-BBBBB", "ABCD-1234-ABCD-1234", IssueKind.TRANSFER, List.of(), Instant.now(), null, nonce, 1)).text()).isEqualTo("SIGNED");
    }

    @Test
    void forbiddenIssuanceWritesNothingAndIsRefusedOnEveryEntryPoint() throws Exception {
        var l = license(3);
        int seats = count("lic_seat"), issuances = count("lic_issuance");
        for (String kind : List.of("TRANSFER", "OPEN_ALL")) {
            assertThatThrownBy(() -> activations.issue(OWNER, new ActivationService.IssueRequest(l.licenseId(), code(), kind, null, null), "server-api"))
                    .isInstanceOf(ApiException.class).satisfies(e -> assertThat(((ApiException) e).status().value()).isEqualTo(403));
        }
        assertThatThrownBy(() -> activations.issue(OWNER, new ActivationService.IssueRequest(l.licenseId(), code(), null, List.of("*"), null), "server-api"))
                .isInstanceOf(ApiException.class).hasMessageContaining("tout ouvrir");
        // same through the HTTP API with the admin token
        mvc.perform(post("/api/v1/admin/licenses/" + l.licenseId() + "/activations").header("Authorization", ADMIN).contentType(MediaType.APPLICATION_JSON)
                .content("{\"deviceCode\":\"" + code() + "\",\"kind\":\"OPEN_ALL\"}")).andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("tout ouvrir")));
        mvc.perform(post("/api/v1/admin/licenses/" + l.licenseId() + "/activations").header("Authorization", ADMIN).contentType(MediaType.APPLICATION_JSON)
                .content("{\"deviceCode\":\"" + code() + "\",\"kind\":\"TRANSFER\"}")).andExpect(status().isForbidden());
        assertThat(count("lic_seat")).isEqualTo(seats);
        assertThat(count("lic_issuance")).isEqualTo(issuances);
        // a normal issuance through the API works and returns the activation once
        mvc.perform(post("/api/v1/admin/licenses/" + l.licenseId() + "/activations").header("Authorization", ADMIN).contentType(MediaType.APPLICATION_JSON)
                .content("{\"deviceCode\":\"" + code() + "\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.kind").value("PURCHASE")).andExpect(jsonPath("$.text").isNotEmpty());
    }

    @Test
    void activationTextNeverReachesTheDatabaseNorTheAuditLog() {
        var l = license(1);
        var a = issue(l.licenseId(), code());
        String all = jdbc.queryForList("select details || coalesce(reason, '') from lic_audit", String.class).toString();
        assertThat(all).doesNotContain(a.text()).doesNotContain(a.text().substring(5, 25));
        assertThat(jdbc.queryForList("select token_fingerprint from lic_issuance", String.class)).contains(a.fingerprint());
        assertThat(all).doesNotContain("ABCD").doesNotContain(l.clientName());
    }

    @Test
    void auditChainDetectsEveryKindOfTampering() {
        var l = license(2);
        issue(l.licenseId(), code());
        licenses.suspend(OWNER, l.licenseId(), "contrôle");
        licenses.resume(OWNER, l.licenseId(), "contrôle terminé");
        AuditLog.Verification v = audit.verify();
        assertThat(v.ok()).isTrue();
        assertThat(v.rows()).isGreaterThanOrEqualTo(4);

        // 1. a modified row
        Map<String, Object> row = jdbc.queryForMap("select id, reason from lic_audit where action = 'LICENSE_SUSPEND' order by id desc limit 1");
        jdbc.update("update lic_audit set reason = 'autre' where id = ?", row.get("id"));
        AuditLog.Verification bad = audit.verify();
        assertThat(bad.ok()).isFalse();
        assertThat(bad.brokenAtId()).isEqualTo(((Number) row.get("id")).longValue());
        jdbc.update("update lic_audit set reason = ? where id = ?", row.get("reason"), row.get("id"));
        assertThat(audit.verify().ok()).isTrue();

        // 2. a deleted row in the middle (the chain breaks at the next one)
        Map<String, Object> mid = jdbc.queryForMap("select * from lic_audit where action = 'LICENSE_SUSPEND' order by id desc limit 1");
        jdbc.update("delete from lic_audit where id = ?", mid.get("id"));
        assertThat(audit.verify().ok()).isFalse();
        jdbc.update("insert into lic_audit (id, at, actor, role, channel, action, target_type, target_id, reason, details, prev_hash, hash) values (?,?,?,?,?,?,?,?,?,?,?,?)",
                mid.get("id"), mid.get("at"), mid.get("actor"), mid.get("role"), mid.get("channel"), mid.get("action"), mid.get("target_type"), mid.get("target_id"),
                mid.get("reason"), mid.get("details"), mid.get("prev_hash"), mid.get("hash"));
        assertThat(audit.verify().ok()).isTrue();

        // 3. a removed tail (the head row still points at it)
        Map<String, Object> last = jdbc.queryForMap("select * from lic_audit order by id desc limit 1");
        jdbc.update("delete from lic_audit where id = ?", last.get("id"));
        AuditLog.Verification tail = audit.verify();
        assertThat(tail.ok()).isFalse();
        assertThat(tail.problem()).contains("tête");
        jdbc.update("insert into lic_audit (id, at, actor, role, channel, action, target_type, target_id, reason, details, prev_hash, hash) values (?,?,?,?,?,?,?,?,?,?,?,?)",
                last.get("id"), last.get("at"), last.get("actor"), last.get("role"), last.get("channel"), last.get("action"), last.get("target_type"), last.get("target_id"),
                last.get("reason"), last.get("details"), last.get("prev_hash"), last.get("hash"));
        assertThat(audit.verify().ok()).isTrue();
    }

    @Test
    void auditSearchFiltersAndPaginates() {
        var l = license(1);
        licenses.suspend(OWNER, l.licenseId(), "test filtre");
        var page = audit.search(new AuditLog.Filter(null, "LICENSE_SUSPEND", "LICENSE", l.licenseId(), null, null), 0, 10);
        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items().get(0).reason()).isEqualTo("test filtre");
        assertThat(audit.search(new AuditLog.Filter("personne", null, null, null, null, null), 0, 10).total()).isZero();
        assertThat(audit.forTarget("LICENSE", l.licenseId(), 50)).extracting(AuditLog.Entry::action).contains("LICENSE_CREATE", "LICENSE_SUSPEND");
    }

    @Test
    void auditLogIsTheOnlyWriterOfTheChainUnderConcurrency() throws Exception {
        var pool = java.util.concurrent.Executors.newFixedThreadPool(8);
        var l = license(50);
        var futures = new java.util.ArrayList<java.util.concurrent.Future<?>>();
        for (int i = 0; i < 24; i++) futures.add(pool.submit(() -> issue(l.licenseId(), code())));
        for (var f : futures) f.get();
        pool.shutdown();
        AuditLog.Verification v = audit.verify();
        assertThat(v.ok()).as(String.valueOf(v.problem())).isTrue();
    }
}
