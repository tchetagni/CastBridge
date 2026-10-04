package castbridge.server.activations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import castbridge.server.ApiTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;

/** Module ON but without the secret act-ref.key: the whole module answers 503 « Suivi des activations indisponible » (no tv_ref can be computed). */
class ActivationsNoRefKeyTest extends ApiTestBase {
    @DynamicPropertySource
    static void on(DynamicPropertyRegistry r) throws Exception {
        Path empty = Files.createTempDirectory("cb-act-empty-secrets");
        r.add("castbridge.activations.enabled", () -> "true");
        r.add("castbridge.licenses.secrets-dir", empty::toString);
    }

    @Test
    void everyRouteAnswers503WithAClearMessage() throws Exception {
        MvcResult r = mvc.perform(get("/api/v1/admin/activations/dashboard").header("Authorization", ADMIN)).andReturn();
        assertEquals(503, r.getResponse().getStatus());
        JsonNode b = body(r);
        assertTrue(b.get("message").asText().contains("Suivi des activations indisponible"), b.toString());
        assertEquals(503, mvc.perform(post("/api/v1/admin/activations/journal").header("Authorization", ADMIN).contentType(MediaType.TEXT_PLAIN).content("x")).andReturn().getResponse().getStatus());
        assertEquals(503, mvc.perform(post("/api/v1/activations/report").header("Authorization", "Bearer x").contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn().getResponse().getStatus());
    }
}
