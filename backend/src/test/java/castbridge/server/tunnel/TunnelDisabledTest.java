package castbridge.server.tunnel;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import castbridge.server.ApiTestBase;
import org.junit.jupiter.api.Test;

/** Off by default: nothing is exposed until the owner turns it on. */
class TunnelDisabledTest extends ApiTestBase {
    @Test void everythingIs404() throws Exception {
        mvc.perform(post("/api/v1/tunnel/enroll").contentType("application/json").content("{\"activation\":\"a\",\"sshPublicKey\":\"b\",\"deviceCode\":\"c\"}")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/tunnel/experts")).andExpect(status().isNotFound());
        mvc.perform(get("/admin/tunnels").with(user("esaie").roles("WEBADMIN"))).andExpect(status().isNotFound());
    }
}
