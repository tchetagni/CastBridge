package castbridge.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import castbridge.server.library.LibrarySuggestProperties;
import castbridge.server.library.NameSuggester;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/** POST /api/v1/library/suggest: authenticated like the other device routes, rate limited, cleaned names only, mock until a key is set. */
@TestPropertySource(properties = {"castbridge.library.per-hour=4", "castbridge.library.max-items=5"})
@Import(LibrarySuggestApiTest.Stub.class)
class LibrarySuggestApiTest extends ApiTestBase {
    /** What the endpoint handed to the model, and what the model answers. */
    static final List<List<NameSuggester.Item>> SEEN = new ArrayList<>();
    static final AtomicReference<String> LANG = new AtomicReference<>();

    @TestConfiguration
    static class Stub {
        @Bean @Primary
        NameSuggester stubSuggester() {
            return new NameSuggester() {
                @Override public String model() { return "stub"; }
                @Override public boolean available() { return true; }
                @Override public List<Suggestion> suggest(String lang, List<Item> items) {
                    SEEN.add(items); LANG.set(lang);
                    return List.of(new Suggestion(0, "series", "Prison Break", null, 1, 4, "Cut Off", null, 0.9),
                            new Suggestion(1, "movie", "Evil: ../x", 2010, null, null, null, null, 0.5));
                }
            };
        }
    }

    @Autowired JdbcTemplate jdbc;

    private String token() throws Exception {
        JsonNode r = body(mvc.perform(post("/api/v1/devices/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"installId\":\"" + UUID.randomUUID() + "\",\"app\":\"phone\",\"versionCode\":8,\"versionName\":\"1.0\",\"platform\":\"phone\","
                        + "\"manufacturer\":\"Samsung\",\"model\":\"SM-G996U\",\"consent\":\"essential\"}")).andExpect(status().isCreated()).andReturn());
        return r.get("deviceToken").asText();
    }

    private static String req(String... texts) {
        StringBuilder sb = new StringBuilder("{\"consent\":\"library-ai-v1\",\"lang\":\"fr\",\"items\":[");
        for (int i = 0; i < texts.length; i++) sb.append(i > 0 ? "," : "").append("{\"i\":").append(i).append(",\"t\":\"").append(texts[i]).append("\",\"x\":\"mkv\",\"k\":null,\"d\":45}");
        return sb.append("]}").toString();
    }

    private org.springframework.test.web.servlet.ResultActions call(String token, String body) throws Exception {
        var r = post("/api/v1/library/suggest").contentType(MediaType.APPLICATION_JSON).content(body);
        if (token != null) r = r.header("Authorization", "Bearer " + token);
        return mvc.perform(r);
    }

    @Test
    void authenticatedLikeTheOtherDeviceRoutes() throws Exception {
        call(null, req("prison break s01e04")).andExpect(status().isUnauthorized());
        call("not-a-device-token", req("prison break s01e04")).andExpect(status().isUnauthorized());
        // the admin token is not a device token
        mvc.perform(post("/api/v1/library/suggest").header("Authorization", ADMIN).contentType(MediaType.APPLICATION_JSON).content(req("prison break s01e04")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void answersWithSanitisedSuggestionsAndNeverCachesAndKeepsOnlyCleanedText() throws Exception {
        String t = token();
        SEEN.clear();
        call(t, req("prison break s01e04", "appelle 0612345678 https://exemple.org/x a@b.cd film")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(jsonPath("$.model").value("stub")).andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.suggestions[0].title").value("Prison Break")).andExpect(jsonPath("$.suggestions[0].season").value(1));
        assertEquals("fr", LANG.get());
        List<NameSuggester.Item> seen = SEEN.get(0);
        assertEquals("prison break s01e04", seen.get(0).text());
        String second = seen.get(1).text();
        assertFalse(second.contains("0612345678") || second.contains("http") || second.contains("@"), "digits runs, links and e-mail addresses are dropped: " + second);
        assertTrue(second.contains("film"));
    }

    @Test
    void theSeparateConsentIsMandatory() throws Exception {
        String t = token();
        call(t, req("x y").replace("library-ai-v1", "yes")).andExpect(status().isBadRequest());
        call(t, "{\"lang\":\"fr\",\"items\":[]}").andExpect(status().isBadRequest());
    }

    @Test
    void inputIsValidated() throws Exception {
        String t = token();
        call(t, req("a", "b", "c", "d", "e", "f")).andExpect(status().isPayloadTooLarge());                     // max 5 in this test
        call(t, req("x".repeat(121))).andExpect(status().isBadRequest());
        call(t, req("ok name").replace("\"fr\"", "\"de\"")).andExpect(status().isBadRequest());
        call(t, req("ok name").replace("\"mkv\"", "\"../../x\"")).andExpect(status().isBadRequest());
        call(t, req("ok name").replace("\"k\":null", "\"k\":\"weapon\"")).andExpect(status().isBadRequest());
        call(t, "{\"consent\":\"library-ai-v1\",\"items\":\"nope\"}").andExpect(status().isBadRequest());
    }

    @Test
    void rateLimitedPerDevice() throws Exception {
        String t = token();
        for (int i = 0; i < 4; i++) call(t, req("prison break s01e04")).andExpect(status().isOk());
        call(t, req("prison break s01e04")).andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("Trop de demandes")));
        call(token(), req("prison break s01e04")).andExpect(status().isOk());               // another device is not affected
    }

    @Test
    void blockedDevicesAreRefused() throws Exception {
        String t = token();
        jdbc.update("update device set blocked = true");
        call(t, req("prison break s01e04")).andExpect(status().isForbidden());
    }

    @Test
    void noSecretIsEverStoredInTheRepositoryConfiguration() throws Exception {
        Path main = Path.of("src/main/resources/application.yml");
        String yml = Files.readString(main);
        assertTrue(yml.contains("${CASTBRIDGE_LIBRARY_LLM_API_KEY:}"), "the key comes from the environment only, empty by default");
        for (String f : List.of("src/main/resources/application.yml", "src/test/resources/application-test.yml", ".env.example"))
            assertFalse(Files.readString(Path.of(f)).matches("(?s).*sk-[A-Za-z0-9_-]{20,}.*"), f + " must not contain an API key");
        LibrarySuggestProperties p = new LibrarySuggestProperties(null, null, null, null, null, null, null, null, null, null, null, null);
        assertFalse(p.llmConfigured());
        assertEquals(30, p.perHour());
        assertEquals(40, p.maxItems());
    }

}
