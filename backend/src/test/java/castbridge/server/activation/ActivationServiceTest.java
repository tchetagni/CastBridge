package castbridge.server.activation;

import castbridge.server.config.CastbridgeProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The generator must produce exactly what the TV app verifies (shared reference values). */
class ActivationServiceTest {
    private static ActivationService withSecret(String secret) {
        return new ActivationService(new CastbridgeProperties(null, null, null,
                new CastbridgeProperties.Activation(secret), null, null, null, null, null, null, null));
    }

    @Test
    void tokenMatchesTheTvAlgorithm() {
        ActivationService svc = withSecret("s3cret");
        assertTrue(svc.configured());
        assertEquals("CAYN-SH5S-9QQD", svc.token("QHPP-J8YN"));
        assertEquals("CAYN-SH5S-9QQD", svc.token("qhpp-j8yn"), "case and separators are ignored");
    }

    @Test
    void notConfiguredWithoutSecret() {
        assertFalse(withSecret("").configured());
        assertFalse(withSecret(null).configured());
    }
}
