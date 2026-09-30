package castbridge.server;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {"castbridge.rate-limit.per-minute=6", "castbridge.rate-limit.burst=3"})
class RateLimitTest extends ApiTestBase {

    @Test
    void publicRoutesAreLimitedPerIp() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(get("/api/v1/updates/public-key").with(r -> { r.setRemoteAddr("203.0.113.7"); return r; })).andExpect(status().isOk());
        }
        mvc.perform(get("/api/v1/updates/public-key").with(r -> { r.setRemoteAddr("203.0.113.7"); return r; }))
                .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("Trop de requêtes")));
        // another address is not affected; the admin token is never limited
        mvc.perform(get("/api/v1/updates/public-key").with(r -> { r.setRemoteAddr("203.0.113.8"); return r; })).andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/releases").header("Authorization", ADMIN).with(r -> { r.setRemoteAddr("203.0.113.7"); return r; }))
                .andExpect(status().isOk());
        // X-Forwarded-For set by the local nginx is the client address
        mvc.perform(get("/api/v1/updates/public-key").header("X-Forwarded-For", "203.0.113.7")).andExpect(status().isTooManyRequests());
    }
}
