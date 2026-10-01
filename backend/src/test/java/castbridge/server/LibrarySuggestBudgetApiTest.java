package castbridge.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import castbridge.server.library.LlmNameSuggester;
import castbridge.server.library.LlmProvider;
import castbridge.server.library.NameSuggester;
import castbridge.server.library.PromptRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

/**
 * POST /api/v1/library/suggest with the real LLM suggester on top of a FAKE provider: usage and cost in the answer, daily device quota, daily budget,
 * strict output turned into a 502, and the model never called once a limit is reached.
 */
@TestPropertySource(properties = {"castbridge.library.per-hour=100", "castbridge.library.max-items=10", "castbridge.library.per-device-daily-items=6",
        "castbridge.library.daily-budget-usd=10", "castbridge.library.price-in-per-mtok=1.0", "castbridge.library.price-out-per-mtok=5.0"})
@Import(LibrarySuggestBudgetApiTest.Fake.class)
class LibrarySuggestBudgetApiTest extends ApiTestBase {
    static final AtomicInteger CALLS = new AtomicInteger();
    static volatile String answer = "{\"v\":1,\"results\":[{\"i\":0,\"kind\":\"series\",\"title\":\"Prison Break\",\"season\":1,\"episode\":4,\"confidence\":0.9}]}";
    static volatile long in = 400, out = 100;

    @TestConfiguration
    static class Fake {
        @Bean @Primary
        NameSuggester llmOnFakeProvider(ObjectMapper json) {
            LlmProvider fake = r -> { CALLS.incrementAndGet(); return new LlmProvider.Reply(answer, in, out); };
            return new LlmNameSuggester(fake, "fake-model", PromptRegistry.load("v2"), 1000, false, json);
        }
    }

    private String token() throws Exception {
        JsonNode r = body(mvc.perform(post("/api/v1/devices/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"installId\":\"" + UUID.randomUUID() + "\",\"app\":\"phone\",\"versionCode\":8,\"versionName\":\"1.0\",\"platform\":\"phone\","
                        + "\"manufacturer\":\"Samsung\",\"model\":\"SM-G996U\",\"consent\":\"essential\"}")).andExpect(status().isCreated()).andReturn());
        return r.get("deviceToken").asText();
    }

    private static String req(int n) {
        StringBuilder sb = new StringBuilder("{\"consent\":\"library-ai-v1\",\"lang\":\"fr\",\"items\":[");
        for (int i = 0; i < n; i++) sb.append(i > 0 ? "," : "").append("{\"i\":").append(i).append(",\"t\":\"prison break s01e04\",\"x\":\"mkv\",\"k\":null,\"d\":45}");
        return sb.append("]}").toString();
    }

    private org.springframework.test.web.servlet.ResultActions call(String token, String body) throws Exception {
        return mvc.perform(post("/api/v1/library/suggest").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void theAnswerCarriesTheModelPromptVersionUsageAndEstimatedCost() throws Exception {
        in = 400; out = 100; answer = "{\"v\":1,\"results\":[{\"i\":0,\"kind\":\"series\",\"title\":\"Prison Break\",\"season\":1,\"episode\":4,\"confidence\":0.9},{\"i\":1,\"kind\":\"nope\",\"title\":\"Zz\"}]}";
        call(token(), req(2)).andExpect(status().isOk())
                .andExpect(jsonPath("$.model").value("fake-model")).andExpect(jsonPath("$.promptVersion").value("v2"))
                .andExpect(jsonPath("$.usage.inputTokens").value(400)).andExpect(jsonPath("$.usage.outputTokens").value(100))
                .andExpect(jsonPath("$.usage.estimatedCostUsd").value(0.0009)).andExpect(jsonPath("$.usage.estimated").value(false))
                .andExpect(jsonPath("$.rejected").value(1)).andExpect(jsonPath("$.suggestions.length()").value(1));
    }

    @Test
    void aDeviceHasADailyQuotaOfNamesAndTheModelIsNotCalledBeyondIt() throws Exception {
        in = 10; out = 5; answer = "{\"v\":1,\"results\":[]}";
        String t = token();
        call(t, req(5)).andExpect(status().isOk());
        int before = CALLS.get();
        call(t, req(2)).andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Limite quotidienne")));
        assertEquals(before, CALLS.get(), "the model is not called once the quota is reached");
    }

    @Test
    void aDifferentDeviceIsNotAffectedByAnotherOnesQuota() throws Exception {
        in = 10; out = 5; answer = "{\"v\":1,\"results\":[]}";
        String a = token(), b = token();
        call(a, req(6)).andExpect(status().isOk());
        call(a, req(1)).andExpect(status().isTooManyRequests());
        call(b, req(1)).andExpect(status().isOk());
    }

    @Test
    void aNonConformingAnswerIsAFriendly502() throws Exception {
        in = 10; out = 5; answer = "Voici mes suggestions : {\"v\":1,\"results\":[]}";
        var r = call(token(), req(1)).andExpect(status().isBadGateway()).andReturn();
        assertTrue(!r.getResponse().getContentAsString().contains("Voici"), "the model's text is never echoed to the phone");
    }
}
