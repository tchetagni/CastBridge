package castbridge.server.tunnel;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** The admin page « Tunnels TV »: rendering, session + CSRF, probe, revocation, audit (no key, no activation shown or logged). */
class TunnelAdminPageTest extends TunnelTestBase {
    private static RequestPostProcessor admin() { return user("esaie").roles("WEBADMIN"); }

    @Test void pageShowsTunnelsCommandsCountersAndEscapes() throws Exception {
        Tv a = enrollNewTv("trial"), b = enrollNewTv("production");
        String html = mvc.perform(get("/admin/tunnels").with(admin())).andExpect(status().isOk())
                .andExpect(content().string(containsString("Tunnels TV")))
                .andExpect(content().string(containsString(a.code())))
                .andExpect(content().string(containsString("ssh -J cbexpert@bridge.sti-cm.com:2200 -p " + b.port() + " tv@127.0.0.1")))
                .andExpect(content().string(containsString("Sonder maintenant")))
                .andExpect(content().string(containsString("Révoquer le tunnel")))
                .andExpect(content().string(containsString("port(s) libre(s)")))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(not(containsString(a.key()))))
                .andExpect(content().string(not(containsString("cbx1."))))
                .andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("trial") && html.contains("production"));
        // the nav entry appears on the other pages too
        mvc.perform(get("/admin").with(admin())).andExpect(content().string(containsString("Tunnels TV")));
    }

    @Test void postsNeedSessionAndCsrfThenRevokeAndProbe() throws Exception {
        Tv a = enrollNewTv("production");
        mvc.perform(post("/admin/tunnels/" + a.code() + "/revoke").with(admin())).andExpect(status().isForbidden()); // no CSRF token
        mvc.perform(post("/admin/tunnels/" + a.code() + "/revoke")).andExpect(status().isForbidden()); // no session and no token: CSRF refuses first
        assertTrue(tvFile().contains(a.key()));

        mvc.perform(post("/admin/tunnels/probe").with(admin()).with(csrf())).andExpect(redirectedUrl("/admin/tunnels")).andExpect(flash().attributeExists("ok"));
        mvc.perform(post("/admin/tunnels/" + a.code() + "/probe").with(admin()).with(csrf())).andExpect(redirectedUrl("/admin/tunnels"));
        mvc.perform(post("/admin/tunnels/" + a.code() + "/revoke").with(admin()).with(csrf())).andExpect(redirectedUrl("/admin/tunnels")).andExpect(flash().attributeExists("ok"));
        assertFalse(tvFile().contains(a.key()));
        mvc.perform(get("/admin/tunnels").with(admin())).andExpect(content().string(containsString("révoqué"))).andExpect(content().string(containsString("Rétablir")));
        mvc.perform(post("/admin/tunnels/" + a.code() + "/revoke").with(admin()).with(csrf())).andExpect(flash().attributeExists("error")); // already revoked
        mvc.perform(post("/admin/tunnels/" + a.code() + "/restore").with(admin()).with(csrf())).andExpect(flash().attributeExists("ok"));
        assertTrue(tvFile().contains(a.key()));
        mvc.perform(post("/admin/tunnels/experts/refresh").with(admin()).with(csrf())).andExpect(redirectedUrl("/admin/tunnels"));
        // audit rows hold identifiers only
        var details = jdbc.queryForList("select detail from tunnel_audit", String.class);
        assertTrue(details.stream().noneMatch(d -> d.contains("ssh-ed25519") || d.contains("cbx1")));
    }
}
