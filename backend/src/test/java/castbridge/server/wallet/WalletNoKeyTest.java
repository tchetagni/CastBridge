package castbridge.server.wallet;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import castbridge.server.ApiTestBase;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Module allumé mais clé « portefeuille » absente : les routes de l'appareil répondent 503 « Portefeuille indisponible » (jamais un instantané non signé). */
class WalletNoKeyTest extends ApiTestBase {
    @DynamicPropertySource
    static void on(DynamicPropertyRegistry r) { r.add("castbridge.wallet.enabled", () -> "true"); }

    @Test
    void withoutTheWalletKeyDeviceRoutesAnswer503() throws Exception {
        String reg = body(mvc.perform(post("/api/v1/devices/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"installId\":\"" + UUID.randomUUID() + "\",\"app\":\"tv\",\"versionCode\":7,\"androidIdHash\":\"" + "e".repeat(64) + "\"}")).andReturn()).get("deviceToken").asText();
        String auth = "Bearer " + reg;
        mvc.perform(post("/api/v1/wallet/sync").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.message").value("Portefeuille indisponible"));
        mvc.perform(get("/api/v1/wallet/history").header("Authorization", auth)).andExpect(status().isServiceUnavailable());
        mvc.perform(get("/api/v1/wallet/policy").header("Authorization", auth)).andExpect(status().isServiceUnavailable());
        // l'administration (réconciliation) ne signe rien : elle reste disponible
        mvc.perform(get("/api/v1/admin/wallet/reconcile").header("Authorization", ADMIN)).andExpect(status().isOk());
    }
}
