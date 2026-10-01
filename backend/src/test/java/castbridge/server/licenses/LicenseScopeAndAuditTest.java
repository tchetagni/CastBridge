package castbridge.server.licenses;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import castbridge.server.licenses.ActivationSigner.SignerScope;
import castbridge.server.web.ApiException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class LicenseScopeAndAuditTest extends LicenseTestBase {

    private int count(String table) { return jdbc.queryForObject("select count(*) from " + table, Integer.class); }

    private String body(Dev d, String kind, boolean star) {
        ObjectNode n = json.createObjectNode();
        n.put("deviceRequest", d.text());
        if (kind != null) n.put("kind", kind);
        if (star) n.putArray("productIds").add("*");
        return n.toString();
    }

    @Test
    void serverKeyScopeIsFixedInCode() {
        assertThat(ScopedActivationSigner.SERVER_SCOPES).containsExactlyInAnyOrder(SignerScope.ISSUE_TRIAL, SignerScope.ISSUE_PRODUCTION, SignerScope.REVOKE, SignerScope.REGISTRY)
                .doesNotContain(SignerScope.TRANSFER, SignerScope.COMMAND_OPEN_ALL, SignerScope.COMMAND_UNLOCK, SignerScope.COMMAND_SUPPORT);
        assertThatThrownBy(() -> ScopedActivationSigner.SERVER_SCOPES.add(SignerScope.TRANSFER)).isInstanceOf(UnsupportedOperationException.class);
        assertThat(activations.serverKinds()).containsExactlyInAnyOrder("TRIAL", "PRODUCTION");
    }

    @Test
    void scopedSignerRefusesTransferAndOpenAllEvenIfTheDelegateCouldSign() {
        // a delegate that WOULD sign anything: the wrapper must stop the request before it gets there
        ActivationSigner everything = new ActivationSigner() {
            public String kid() { return "k"; }
            public java.util.Set<SignerScope> scopes() { return java.util.Set.of(SignerScope.values()); }
            public SignedActivation sign(ActivationRequest r) { return new SignedActivation("SIGNED", "k", "00", "ff", "s", 0, 0); }
        };
        var server = ScopedActivationSigner.server(everything);
        Dev d = dev();
        long now = System.currentTimeMillis();
        for (var ok : new ActivationSigner.IssueKind[] {ActivationSigner.IssueKind.TRIAL, ActivationSigner.IssueKind.PRODUCTION}) {
            assertThat(server.sign(new ActivationSigner.ActivationRequest(ok, "tv", "trial", null, d.request(), List.of(), now, now, 30, "00112233")).text()).isEqualTo("SIGNED");
        }
        assertThatThrownBy(() -> server.sign(new ActivationSigner.ActivationRequest(ActivationSigner.IssueKind.TRANSFER, "tv", "x", null, d.request(), List.of(), now, now, 30, "00112233")))
                .isInstanceOf(ApiException.class).hasMessageContaining("transfert");
        assertThatThrownBy(() -> server.sign(new ActivationSigner.ActivationRequest(ActivationSigner.IssueKind.OPEN_ALL, "tv", "x", null, d.request(), List.of(), now, now, 30, "00112233")))
                .isInstanceOf(ApiException.class).hasMessageContaining("tout ouvrir");
        assertThatThrownBy(() -> server.sign(new ActivationSigner.ActivationRequest(ActivationSigner.IssueKind.PRODUCTION, "tv", "x", null, d.request(), List.of("openall|tout|0|1"), now, now, 30, "00112233")))
                .isInstanceOf(ApiException.class).hasMessageContaining("tout ouvrir");
        // the offline tools' scope (desktop) is a different wrapper and may transfer
        var desktop = new ScopedActivationSigner(everything, java.util.EnumSet.allOf(SignerScope.class));
        assertThat(desktop.sign(new ActivationSigner.ActivationRequest(ActivationSigner.IssueKind.TRANSFER, "tv", "x", null, d.request(), List.of(), now, now, 30, "00112233")).text()).isEqualTo("SIGNED");
    }

    @Test
    void forbiddenIssuanceWritesNothingAndIsRefusedOnEveryEntryPoint() throws Exception {
        var l = license(3);
        int seats = count("lic_seat"), issuances = count("lic_issuance"), events = count("lic_event");
        for (String kind : List.of("TRANSFER", "OPEN_ALL", "open-all")) {
            assertThatThrownBy(() -> activations.issue(OWNER, new ActivationService.IssueRequest(l.licenseId(), "tv", dev().text(), kind, null, null), "server-api"))
                    .isInstanceOf(ApiException.class).satisfies(e -> assertThat(((ApiException) e).status().value()).isEqualTo(403));
        }
        assertThatThrownBy(() -> activations.issue(OWNER, new ActivationService.IssueRequest(l.licenseId(), "tv", dev().text(), null, List.of("*"), null), "server-api"))
                .isInstanceOf(ApiException.class).hasMessageContaining("tout ouvrir");
        // same through the HTTP API with the admin token
        mvc.perform(post("/api/v1/admin/licenses/" + l.licenseId() + "/activations").header("Authorization", ADMIN).contentType(MediaType.APPLICATION_JSON).content(body(dev(), "OPEN_ALL", false)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("tout ouvrir")));
        mvc.perform(post("/api/v1/admin/licenses/" + l.licenseId() + "/activations").header("Authorization", ADMIN).contentType(MediaType.APPLICATION_JSON).content(body(dev(), "TRANSFER", false)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/licenses/" + l.licenseId() + "/activations").header("Authorization", ADMIN).contentType(MediaType.APPLICATION_JSON).content(body(dev(), null, true)))
                .andExpect(status().isForbidden());
        assertThat(count("lic_seat")).isEqualTo(seats);
        assertThat(count("lic_issuance")).isEqualTo(issuances);
        assertThat(count("lic_event")).isEqualTo(events);
        // a normal issuance through the API works and returns the activation once
        mvc.perform(post("/api/v1/admin/licenses/" + l.licenseId() + "/activations").header("Authorization", ADMIN).contentType(MediaType.APPLICATION_JSON).content(body(dev(), null, false)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.kind").value("PRODUCTION")).andExpect(jsonPath("$.text").value(org.hamcrest.Matchers.startsWith("cbx1.")));
    }

    @Test
    void activationTextNeverReachesTheDatabaseNorTheAuditLog() {
        var l = license(1);
        Dev d = dev();
        var a = issue(l.licenseId(), d);
        String all = jdbc.queryForList("select concat(coalesce(details, ''), coalesce(reason, '')) from lic_audit", String.class).toString();
        assertThat(all).doesNotContain(a.text()).doesNotContain(a.text().substring(5, 25)).doesNotContain(d.code()).doesNotContain(l.clientName());
        assertThat(jdbc.queryForList("select token_fingerprint from lic_issuance", String.class)).contains(a.fingerprint());
        // the registry holds the signed issue event (fingerprints of the hardware, never raw values), not the activation
        assertThat(jdbc.queryForList("select text from lic_event where type = 'issue'", String.class).toString()).doesNotContain("cbx1.");
    }

    @Test
    void auditChainDetectsEveryKindOfTampering() {
        var l = license(2);
        issue(l.licenseId(), dev());
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
        reinsert(mid);
        assertThat(audit.verify().ok()).isTrue();

        // 3. a removed tail (the head row still points at it)
        Map<String, Object> last = jdbc.queryForMap("select * from lic_audit order by id desc limit 1");
        jdbc.update("delete from lic_audit where id = ?", last.get("id"));
        AuditLog.Verification tail = audit.verify();
        assertThat(tail.ok()).isFalse();
        assertThat(tail.problem()).contains("tête");
        reinsert(last);
        assertThat(audit.verify().ok()).isTrue();
    }

    private void reinsert(Map<String, Object> r) {
        jdbc.update("insert into lic_audit (id, at, actor, role, channel, action, target_type, target_id, reason, details, prev_hash, hash) values (?,?,?,?,?,?,?,?,?,?,?,?)",
                r.get("id"), r.get("at"), r.get("actor"), r.get("role"), r.get("channel"), r.get("action"), r.get("target_type"), r.get("target_id"), r.get("reason"), r.get("details"),
                r.get("prev_hash"), r.get("hash"));
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
        for (int i = 0; i < 24; i++) {
            Dev d = dev();
            futures.add(pool.submit(() -> issue(l.licenseId(), d)));
        }
        for (var f : futures) f.get();
        pool.shutdown();
        AuditLog.Verification v = audit.verify();
        assertThat(v.ok()).as(String.valueOf(v.problem())).isTrue();
    }
}
