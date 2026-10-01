package castbridge.server.licenses;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import castbridge.server.ApiTestBase;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Default configuration (module OFF): every licence route answers 404, existing admin pages are unchanged, the schema is present but empty. */
class LicenseDisabledTest extends ApiTestBase {
    @Autowired JdbcTemplate jdbc;

    @Test
    void everythingIsOffByDefault() throws Exception {
        for (String p : List.of("/admin/licenses", "/admin/licenses/list", "/admin/licenses/security", "/admin/licenses/registry/export")) {
            mvc.perform(get(p).with(user("esaie").roles("WEBADMIN"))).andExpect(status().isNotFound());
        }
        for (String p : List.of("/api/v1/admin/licenses", "/api/v1/admin/licenses/dashboard", "/api/v1/admin/licenses/ledger/export")) {
            mvc.perform(get(p).header("Authorization", ADMIN)).andExpect(status().isNotFound());
        }
        mvc.perform(get("/api/v1/revocations")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/entitlements/me").param("deviceCode", "ABCD-ABCD-ABCD-ABCD")).andExpect(status().isNotFound());
        // the rest of the admin interface is untouched, and the navigation shows no "Licences" entry
        String html = mvc.perform(get("/admin").with(user("esaie").roles("WEBADMIN"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).doesNotContain("/admin/licenses");
        // migrations are applied anyway (additive), existing accounts became OWNER, no TOTP
        assertThat(jdbc.queryForObject("select role from admin_user where username = 'esaie'", String.class)).isEqualTo("OWNER");
        assertThat(jdbc.queryForObject("select count(*) from lic_license", Integer.class)).isZero();
        // and the licence beans do not need any key to exist
        assertThat(jdbc.queryForObject("select last_id from lic_audit_head where id = 1", Long.class)).isZero();
    }
}
