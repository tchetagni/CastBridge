package castbridge.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import castbridge.server.library.CostMeter;
import castbridge.server.library.HttpLlmProvider;
import castbridge.server.library.LibrarySuggestProperties;
import castbridge.server.library.LlmNameSuggester;
import castbridge.server.library.LlmProvider;
import castbridge.server.library.NameSuggester;
import castbridge.server.library.PromptRegistry;
import castbridge.server.library.SuggestionParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * The optional AI layer, without Spring and without any real service: a fake provider scripts the answers, a local HTTP server stands in for the
 * two families of services. No test ever reaches the Internet.
 */
class LibraryAiLayerTest {
    static final ObjectMapper JSON = new ObjectMapper();

    /** Scripted provider: returns what the test says, remembers what it was asked. */
    static class FakeProvider implements LlmProvider {
        final List<Request> asked = new ArrayList<>();
        String text = "{\"v\":1,\"results\":[]}";
        long in = 120, out = 40;
        IOException fail;

        @Override public Reply complete(Request r) throws IOException {
            asked.add(r);
            if (fail != null) throw fail;
            return new Reply(text, in, out);
        }
    }

    private static LlmNameSuggester suggester(FakeProvider p, String version) {
        return new LlmNameSuggester(p, "fake-model", PromptRegistry.load(version), 1000, false, JSON);
    }

    private static List<NameSuggester.Item> items(String... t) {
        List<NameSuggester.Item> l = new ArrayList<>();
        for (int i = 0; i < t.length; i++) l.add(new NameSuggester.Item(i, t[i], "mkv", null, 45));
        return l;
    }

    // ------------------------------------------------------------------ strict output

    private static SuggestionParser.Parsed parse(String text, int n) throws Exception { return SuggestionParser.parse(JSON, text, n); }

    @Test
    void aValidAnswerIsAccepted() throws Exception {
        var p = parse("{\"v\":1,\"results\":[{\"i\":0,\"kind\":\"series\",\"title\":\"Prison Break\",\"year\":null,\"season\":1,\"episode\":4,\"episodeTitle\":\"Cut Off\",\"artist\":null,\"confidence\":0.8}]}", 2);
        assertEquals(1, p.suggestions().size());
        assertEquals(0, p.rejected());
        assertEquals("Prison Break", p.suggestions().get(0).title());
        assertEquals(4, p.suggestions().get(0).episode());
    }

    @Test
    void aSingleCodeFenceIsTolerated() throws Exception {
        assertEquals(1, parse("```json\n{\"v\":1,\"results\":[{\"i\":0,\"kind\":\"movie\",\"title\":\"Inception\",\"year\":2010}]}\n```", 1).suggestions().size());
    }

    @Test
    void anythingAroundOrBesideTheJsonRejectsTheWholeAnswer() {
        String ok = "{\"v\":1,\"results\":[]}";
        for (String bad : List.of("Voici : " + ok, ok + " Voilà !", ok + ok, "[{\"i\":0}]", "{\"results\":[]}", "{\"v\":2,\"results\":[]}", "{\"v\":\"1\",\"results\":[]}",
                "{\"v\":1,\"results\":{}}", "{\"v\":1,\"results\":[],\"note\":\"x\"}", "", "   ", "null", "```json\n" + ok, "ok", "{\"v\":1,\"results\":[1,2,3]} trailing"))
            assertThrows(NameSuggester.SuggesterException.class, () -> parse(bad, 3), "must be refused: " + bad);
    }

    @Test
    void moreResultsThanNamesIsRefused() {
        assertThrows(NameSuggester.SuggesterException.class, () -> parse("{\"v\":1,\"results\":[{\"i\":0,\"kind\":\"movie\",\"title\":\"AA\"},{\"i\":0,\"kind\":\"movie\",\"title\":\"BB\"}]}", 1));
    }

    @Test
    void badResultsAreDroppedOneByOneAndCounted() throws Exception {
        String text = "{\"v\":1,\"results\":["
                + "{\"i\":0,\"kind\":\"series\",\"title\":\"Bon\",\"season\":2,\"episode\":3,\"confidence\":0.8},"          // ok
                + "{\"i\":1,\"kind\":\"series\",\"title\":\"Saison texte\",\"season\":\"2\"},"                              // number written as text
                + "{\"i\":2,\"kind\":\"weapon\",\"title\":\"Mauvais type\"},"                                               // unknown kind
                + "{\"i\":50,\"kind\":\"movie\",\"title\":\"Hors liste\"},"                                                  // index out of range
                + "{\"i\":3,\"kind\":\"movie\",\"title\":\"X\"},"                                                           // title too short
                + "{\"i\":4,\"kind\":\"movie\",\"title\":\"Clé inconnue\",\"rating\":5},"                                   // unknown key
                + "{\"i\":5,\"kind\":\"movie\",\"title\":\"Année absurde\",\"year\":2999},"                                 // impossible year
                + "{\"i\":0,\"kind\":\"movie\",\"title\":\"Doublon\"},"                                                     // duplicate index
                + "{\"i\":6,\"kind\":\"movie\",\"title\":\"Confiance texte\",\"confidence\":\"high\"},"                     // wrong type
                + "\"pas un objet\"]}";
        var p = parse(text, 10);
        assertEquals(1, p.suggestions().size());
        assertEquals("Bon", p.suggestions().get(0).title());
        assertEquals(9, p.rejected());
    }

    @Test
    void titlesAreCleanedAndConfidenceIsBounded() throws Exception {
        var p = parse("{\"v\":1,\"results\":[{\"i\":0,\"kind\":\"movie\",\"title\":\"../../etc/passwd: *x*\",\"confidence\":42}]}", 1);
        String t = p.suggestions().get(0).title();
        assertFalse(t.contains("/") || t.contains(":") || t.contains("*") || t.contains("\\"), t);
        assertEquals(1.0, p.suggestions().get(0).confidence());
    }

    // ------------------------------------------------------------------ the suggester with a fake provider

    @Test
    void theVersionedPromptAndOnlyTheNamesAreSent() throws Exception {
        FakeProvider fake = new FakeProvider();
        fake.text = "{\"v\":1,\"results\":[{\"i\":0,\"kind\":\"series\",\"title\":\"Kaduna Nights\",\"season\":2,\"episode\":3}]}";
        var out = suggester(fake, "v2").suggestWithUsage("fr", List.of(new NameSuggester.Item(0, "kaduna nights s02e03", "mp4", "series", 45), new NameSuggester.Item(1, "autre", "mkv", null, null)));
        LlmProvider.Request r = fake.asked.get(0);
        assertEquals(PromptRegistry.load("v2").text(), r.system(), "the system prompt is the versioned file, verbatim");
        assertEquals("v2", out.promptVersion());
        JsonNode user = JSON.readTree(r.user());
        assertEquals(2, user.size());
        assertEquals(List.of("i", "nom", "extension", "indice", "duree_min"), fieldNames(user.get(0)));
        assertEquals(List.of("i", "nom", "extension"), fieldNames(user.get(1)));
        assertFalse(r.user().contains("Bearer") || r.user().toLowerCase().contains("device") || r.user().contains("/"), r.user());
        assertEquals(1, out.suggestions().size());
        assertEquals(new NameSuggester.Usage(120, 40, 0, false), out.usage());
    }

    private static List<String> fieldNames(JsonNode n) { List<String> l = new ArrayList<>(); n.fieldNames().forEachRemaining(l::add); return l; }

    @Test
    void whenTheServiceReportsNoTokensTheUsageIsEstimatedFromTheText() throws Exception {
        FakeProvider fake = new FakeProvider();
        fake.in = -1; fake.out = -1;
        var out = suggester(fake, "v1").suggestWithUsage("fr", items("prison break s01e04"));
        assertTrue(out.usage().estimated());
        assertTrue(out.usage().inputTokens() > 100, "the prompt alone is more than 100 tokens");
    }

    @Test
    void anUnusableAnswerAndAServiceFailureBecomeSuggesterExceptions() {
        FakeProvider fake = new FakeProvider();
        fake.text = "Je ne peux pas répondre à ça.";
        assertThrows(NameSuggester.SuggesterException.class, () -> suggester(fake, "v1").suggest("fr", items("a b")));
        FakeProvider down = new FakeProvider();
        down.fail = new IOException("Le modèle a répondu 529");
        var e = assertThrows(NameSuggester.SuggesterException.class, () -> suggester(down, "v1").suggest("fr", items("a b")));
        assertTrue(e.getMessage().contains("529"));
        down.fail = new IOException("Connection refused: secret-host.internal");
        var e2 = assertThrows(NameSuggester.SuggesterException.class, () -> suggester(down, "v1").suggest("fr", items("a b")));
        assertFalse(e2.getMessage().contains("secret-host"), "internal details of a failure are not repeated");
    }

    @Test
    void noItemMeansNoCall() throws Exception {
        FakeProvider fake = new FakeProvider();
        assertTrue(suggester(fake, "v1").suggest("fr", List.of()).isEmpty());
        assertTrue(fake.asked.isEmpty());
    }

    // ------------------------------------------------------------------ prompts

    @Test
    void everyPromptVersionLoadsAndTeachesTheSameStrictFormat() {
        var v1 = PromptRegistry.load("v1");
        var v2 = PromptRegistry.load("v2");
        assertNotEquals(v1.sha256(), v2.sha256());
        for (var p : List.of(v1, v2)) {
            assertTrue(p.text().contains("{\"v\":1,\"results\":["), p.version());
            assertTrue(p.text().contains("entiers"), p.version());
            assertFalse(p.text().matches("(?s).*sk-[A-Za-z0-9_-]{20,}.*"));
        }
        assertThrows(IllegalArgumentException.class, () -> PromptRegistry.load("v9"));
        assertThrows(IllegalArgumentException.class, () -> PromptRegistry.load("../application"));
    }

    // ------------------------------------------------------------------ cost and limits

    @Test
    void costIsComputedFromThePricesPerMillionTokens() {
        CostMeter m = new CostMeter(1.0, 5.0, 10, 100, System::currentTimeMillis);
        assertEquals(6.0, m.cost(1_000_000, 1_000_000), 1e-9);
        assertEquals(0.0009, m.cost(400, 100), 1e-9);
        double est = m.estimateBeforehand("x".repeat(3000), 40, 40 * 25, 3000);
        assertTrue(est > 0 && est < 0.02, "an analysis of 40 names costs a fraction of a cent: " + est);
    }

    @Test
    void theDailyBudgetAndTheDeviceQuotaStopTheCalls() {
        AtomicLong now = new AtomicLong(1_000_000_000_000L);
        CostMeter m = new CostMeter(1.0, 5.0, 0.01, 50, now::get);
        assertEquals(CostMeter.Refusal.NONE, m.reserve(1, 30, 0.004));
        assertEquals(CostMeter.Refusal.DEVICE_QUOTA, m.reserve(1, 30, 0.001), "50 names per device and day");
        assertEquals(CostMeter.Refusal.NONE, m.reserve(2, 30, 0.004));
        assertEquals(CostMeter.Refusal.BUDGET, m.reserve(3, 5, 0.004), "0.012 > 0.01 for the whole server");
        m.release(2, 30, 0.004);
        assertEquals(CostMeter.Refusal.NONE, m.reserve(3, 5, 0.004));
        now.addAndGet(86_400_000L);
        assertEquals(CostMeter.Refusal.NONE, m.reserve(1, 30, 0.004), "a new UTC day starts at zero");
        assertEquals(0.004, m.spentToday(), 1e-9);
    }

    @Test
    void settlingReplacesTheEstimateByTheRealCost() {
        CostMeter m = new CostMeter(1.0, 5.0, 1, 50, System::currentTimeMillis);
        m.reserve(1, 10, 0.010);
        m.settle(0.010, 0.002);
        assertEquals(0.002, m.spentToday(), 1e-9);
    }

    // ------------------------------------------------------------------ configuration

    @Test
    void defaultsAreSafeAndTheKeyIsNeverRequired() {
        var p = new LibrarySuggestProperties(null, null, null, null, null, null, null, null, null, null, null, null);
        assertFalse(p.llmConfigured());
        assertEquals("anthropic", p.provider());
        assertEquals("https://api.anthropic.com/v1/messages", p.llmUrl());
        assertEquals("claude-haiku-4-5", p.llmModel());
        assertEquals("v1", p.promptVersion());
        assertEquals(1.0, p.priceInPerMtok());
        assertEquals(5.0, p.priceOutPerMtok());
        assertEquals(2.0, p.dailyBudgetUsd());
        assertEquals(300, p.perDeviceDailyItems());
        var o = new LibrarySuggestProperties("k", "", "", null, null, "OpenAI", null, null, null, null, null, null);
        assertTrue(o.llmConfigured());
        assertEquals("openai", o.provider());
        assertEquals("https://api.openai.com/v1/chat/completions", o.llmUrl());
        assertThrows(IllegalArgumentException.class, () -> new LibrarySuggestProperties("k", null, null, null, null, "gemini", null, null, null, null, null, null));
    }

    // ------------------------------------------------------------------ the HTTP transports, against a local stand-in

    private static final class Seen { final AtomicReference<String> body = new AtomicReference<>(); final AtomicReference<Map<String, String>> headers = new AtomicReference<>(); }

    private static HttpServer stand(String path, String answer, int status, Seen seen) throws IOException {
        HttpServer srv = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        srv.createContext(path, ex -> {
            seen.body.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            seen.headers.set(Map.of("x-api-key", String.valueOf(ex.getRequestHeaders().getFirst("x-api-key")), "anthropic-version", String.valueOf(ex.getRequestHeaders().getFirst("anthropic-version")),
                    "authorization", String.valueOf(ex.getRequestHeaders().getFirst("Authorization"))));
            byte[] out = answer.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(status, out.length == 0 ? -1 : out.length);
            if (out.length > 0) ex.getResponseBody().write(out);
            ex.close();
        });
        srv.start();
        return srv;
    }

    @Test
    void anthropicTransportUsesTheMessagesApiShape() throws Exception {
        Seen seen = new Seen();
        String answer = JSON.writeValueAsString(Map.of("content", List.of(Map.of("type", "text", "text", "{\"v\":1,\"results\":[]}")), "usage", Map.of("input_tokens", 321, "output_tokens", 9)));
        HttpServer srv = stand("/v1/messages", answer, 200, seen);
        try {
            var p = new HttpLlmProvider("anthropic", "http://127.0.0.1:" + srv.getAddress().getPort() + "/v1/messages", "test-key-not-real", JSON);
            var r = p.complete(new LlmProvider.Request("test-model", "SYS", "USER", 777, true));
            assertEquals("{\"v\":1,\"results\":[]}", r.text());
            assertEquals(321, r.inputTokens());
            assertEquals(9, r.outputTokens());
            assertEquals("test-key-not-real", seen.headers.get().get("x-api-key"));
            assertEquals("2023-06-01", seen.headers.get().get("anthropic-version"));
            assertEquals("null", seen.headers.get().get("authorization"), "a Bearer header is for the other family only");
            JsonNode b = JSON.readTree(seen.body.get());
            assertEquals("test-model", b.get("model").asText());
            assertEquals(777, b.get("max_tokens").asInt());
            assertEquals("SYS", b.get("system").asText());
            assertEquals("USER", b.get("messages").get(0).get("content").asText());
            assertEquals("user", b.get("messages").get(0).get("role").asText());
            assertNull(b.get("temperature"), "sampling parameters are not sent");
            assertNull(b.get("response_format"));
        } finally { srv.stop(0); }
    }

    @Test
    void openAiCompatibleTransportUsesChatCompletions() throws Exception {
        Seen seen = new Seen();
        String answer = JSON.writeValueAsString(Map.of("choices", List.of(Map.of("message", Map.of("role", "assistant", "content", "{\"v\":1,\"results\":[]}"))), "usage", Map.of("prompt_tokens", 200, "completion_tokens", 7)));
        HttpServer srv = stand("/v1/chat/completions", answer, 200, seen);
        try {
            var p = new HttpLlmProvider("openai", "http://127.0.0.1:" + srv.getAddress().getPort() + "/v1/chat/completions", "test-key-not-real", JSON);
            var r = p.complete(new LlmProvider.Request("m", "SYS", "USER", 500, true));
            assertEquals("{\"v\":1,\"results\":[]}", r.text());
            assertEquals(200, r.inputTokens());
            assertEquals(7, r.outputTokens());
            assertEquals("Bearer test-key-not-real", seen.headers.get().get("authorization"));
            assertEquals("null", seen.headers.get().get("x-api-key"));
            JsonNode b = JSON.readTree(seen.body.get());
            assertEquals("system", b.get("messages").get(0).get("role").asText());
            assertEquals("json_object", b.get("response_format").get("type").asText());
        } finally { srv.stop(0); }
    }

    @Test
    void aServiceErrorIsAnIoExceptionWithoutTheBody() throws Exception {
        Seen seen = new Seen();
        HttpServer srv = stand("/", "secret details of the failure", 500, seen);
        try {
            var p = new HttpLlmProvider("anthropic", "http://127.0.0.1:" + srv.getAddress().getPort() + "/", "k", JSON);
            var e = assertThrows(IOException.class, () -> p.complete(new LlmProvider.Request("m", "s", "u", 100, false)));
            assertTrue(e.getMessage().contains("500"));
            assertFalse(e.getMessage().contains("secret"));
        } finally { srv.stop(0); }
    }

    @Test
    void theWholeChainWorksAgainstAStandInService() throws Exception {
        Seen seen = new Seen();
        String text = "{\"v\":1,\"results\":[{\"i\":0,\"kind\":\"series\",\"title\":\"Kaduna Nights\",\"season\":2,\"episode\":3,\"confidence\":0.8},{\"i\":1,\"kind\":\"weapon\",\"title\":\"Mauvais\"}]}";
        String answer = JSON.writeValueAsString(Map.of("content", List.of(Map.of("type", "text", "text", text)), "usage", Map.of("input_tokens", 1000, "output_tokens", 100)));
        HttpServer srv = stand("/v1/messages", answer, 200, seen);
        try {
            var provider = new HttpLlmProvider("anthropic", "http://127.0.0.1:" + srv.getAddress().getPort() + "/v1/messages", "test-key-not-real", JSON);
            var s = new LlmNameSuggester(provider, "test-model", PromptRegistry.load("v1"), 1000, false, JSON);
            var out = s.suggestWithUsage("fr", items("kaduna nights s02e03", "autre"));
            assertEquals(1, out.suggestions().size());
            assertEquals(1, out.usage().rejected());
            assertEquals(new CostMeter(1.0, 5.0, 1, 1, System::currentTimeMillis).cost(1000, 100), 0.0015, 1e-9);
            assertTrue(seen.body.get().contains("kaduna nights s02e03"));
        } finally { srv.stop(0); }
    }
}
