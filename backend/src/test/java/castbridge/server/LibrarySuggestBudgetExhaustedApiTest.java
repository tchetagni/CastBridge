package castbridge.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

/** The server-wide daily budget: once the estimated spending is reached, the model is no longer called and the phone is told to use its local rules. */
@TestPropertySource(properties = {"castbridge.library.per-hour=100", "castbridge.library.daily-budget-usd=0.0016"})
@Import(LibrarySuggestBudgetExhaustedApiTest.Fake.class)
class LibrarySuggestBudgetExhaustedApiTest extends ApiTestBase {
    static final AtomicInteger CALLS = new AtomicInteger();

    @TestConfiguration
    static class Fake {
        @Bean @Primary
        NameSuggester llmOnFakeProvider(ObjectMapper json) {
            LlmProvider fake = r -> { CALLS.incrementAndGet(); return new LlmProvider.Reply("{\"v\":1,\"results\":[]}", 400, 100); };   // 0.0009 USD at 1 / 5 per million
            return new LlmNameSuggester(fake, "fake-model", PromptRegistry.load("v2"), 1000, false, json);
        }
    }

    @Test
    void whenTheDailyBudgetIsSpentTheModelIsNotCalledAgain() throws Exception {
        JsonNode r = body(mvc.perform(post("/api/v1/devices/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"installId\":\"" + UUID.randomUUID() + "\",\"app\":\"phone\",\"versionCode\":8,\"versionName\":\"1.0\",\"platform\":\"phone\","
                        + "\"manufacturer\":\"Samsung\",\"model\":\"SM-G996U\",\"consent\":\"essential\"}")).andExpect(status().isCreated()).andReturn());
        String t = r.get("deviceToken").asText();
        String body = "{\"consent\":\"library-ai-v1\",\"lang\":\"fr\",\"items\":[{\"i\":0,\"t\":\"prison break s01e04\",\"x\":\"mkv\",\"k\":null,\"d\":45}]}";
        mvc.perform(post("/api/v1/library/suggest").header("Authorization", "Bearer " + t).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.usage.estimatedCostUsd").value(0.0009));
        mvc.perform(post("/api/v1/library/suggest").header("Authorization", "Bearer " + t).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("suspendue")));
        assertEquals(1, CALLS.get());
    }
}
