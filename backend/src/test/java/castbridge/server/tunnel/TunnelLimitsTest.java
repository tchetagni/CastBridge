package castbridge.server.tunnel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Port range exhaustion and the per-IP rate limit of the enrolment (tiny settings for the test). */
class TunnelLimitsTest extends TunnelTestBase {
    @DynamicPropertySource
    static void small(DynamicPropertyRegistry r) {
        r.add("castbridge.tunnel.port-range", () -> "22100-22101");
        r.add("castbridge.tunnel.enroll-per-hour", () -> "6");
        r.add("castbridge.tunnel.host-key-fingerprint", () -> "SHA256:" + "A".repeat(43));
    }

    @Test void portRangeExhaustionThenRateLimit() throws Exception {
        Tv a = enrollNewTv("trial"), b = enrollNewTv("trial");
        assertEquals(22100, Math.min(a.port(), b.port()));
        assertEquals(22101, Math.max(a.port(), b.port()));
        // a third device: no port left (503, in French); the known ones still work (idempotent, same port)
        Dev c = dev();
        JsonNode e = enrolled(enrollBody(activation(castbridge.server.licenses.LicenseTestAccess.desktop(), "trial", "tv", c, "lic-plein"), sshKey(), c.code()), "10.9.0.1", 503);
        assertTrue(e.get("message").asText().contains("Plus de port disponible"), e.toString());
        JsonNode again = enrolled(enrollBody(a.activation(), a.key(), a.code()), "10.9.0.1", 200);
        assertEquals(a.port(), again.get("port").asInt());
        assertEquals("SHA256:" + "A".repeat(43), again.get("hostKeyFingerprint").asText());

        // rate limit: 6 attempts per IP and hour (the helper's enrolments came from other addresses)
        String ip = "203.0.113.7";
        for (int i = 0; i < 6; i++) enrolled("{}", ip, 400);
        JsonNode limited = enrolled("{}", ip, 429);
        assertTrue(limited.get("message").asText().contains("Trop de demandes"), limited.toString());
        enrolled("{}", "203.0.113.8", 400); // another address is not affected
    }
}
