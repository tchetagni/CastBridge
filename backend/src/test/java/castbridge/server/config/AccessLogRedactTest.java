package castbridge.server.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Audit w23-01 L1: the access log never holds a whole device code (the fiche of a TV carries it in the path). */
class AccessLogRedactTest {
    @Test
    void theDeviceCodeOfAFicheIsNotLogged() {
        assertEquals("/api/v1/admin/activations/tvs/{deviceCode}", AccessLogFilter.redact("/api/v1/admin/activations/tvs/ZAEX-6TB5-7KTC-0N7M"));
        assertEquals("/api/v1/admin/activations/tvs/{deviceCode}", AccessLogFilter.redact("/api/v1/admin/activations/tvs/zaex%206tb5%207ktc%200n7m"));
        assertEquals("/api/v1/admin/activations/tvs", AccessLogFilter.redact("/api/v1/admin/activations/tvs"));
        assertEquals("/api/v1/admin/activations/activations", AccessLogFilter.redact("/api/v1/admin/activations/activations"));
        assertEquals("/api/v1/wallet/receive-code/{code}", AccessLogFilter.redact("/api/v1/wallet/receive-code/ABCD"));
    }
}
