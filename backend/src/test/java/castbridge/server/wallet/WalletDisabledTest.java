package castbridge.server.wallet;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import castbridge.server.ApiTestBase;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** Éteint par défaut dans le code : aucune route portefeuille n'existe (404), même avec un jeton d'appareil ou d'administration valide. */
class WalletDisabledTest extends ApiTestBase {
    @Test
    void everyWalletRouteIsAbsentWhenTheModuleIsOff() throws Exception {
        String reg = body(mvc.perform(post("/api/v1/devices/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"installId\":\"" + UUID.randomUUID() + "\",\"app\":\"tv\",\"versionCode\":7,\"androidIdHash\":\"" + "d".repeat(64) + "\"}")).andReturn()).get("deviceToken").asText();
        String auth = "Bearer " + reg;
        mvc.perform(post("/api/v1/wallet/sync").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/wallet/history").header("Authorization", auth)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/wallet/policy").header("Authorization", auth)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/admin/wallet/reconcile").header("Authorization", ADMIN)).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/admin/wallet/grant").header("Authorization", ADMIN).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isNotFound());
    }
}
