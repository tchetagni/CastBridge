package castbridge.server.library;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * HTTP transport for the two families of services: the Anthropic Messages API ({@code x-api-key}) and OpenAI-compatible chat completions
 * ({@code Authorization: Bearer}). Only the prompt and the cleaned names are sent; the key stays in this object and goes to the configured URL only.
 */
public class HttpLlmProvider implements LlmProvider {
    private final String kind, url, apiKey;
    private final HttpClient http;
    private final ObjectMapper json;

    public HttpLlmProvider(String kind, String url, String apiKey, ObjectMapper json) {
        this(kind, url, apiKey, json, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build());
    }

    HttpLlmProvider(String kind, String url, String apiKey, ObjectMapper json, HttpClient http) {
        this.kind = kind; this.url = url; this.apiKey = apiKey; this.json = json; this.http = http;
    }

    @Override
    public Reply complete(Request r) throws IOException, InterruptedException {
        ObjectNode body = json.createObjectNode();
        body.put("model", r.model());
        body.put("max_tokens", r.maxOutputTokens());
        boolean openai = LibrarySuggestProperties.OPENAI.equals(kind);
        if (openai) {
            var msgs = body.putArray("messages");
            msgs.addObject().put("role", "system").put("content", r.system());
            msgs.addObject().put("role", "user").put("content", r.user());
            if (r.jsonOnly()) body.putObject("response_format").put("type", "json_object");
        } else {
            body.put("system", r.system());
            body.putArray("messages").addObject().put("role", "user").put("content", r.user());
        }
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).header("content-type", "application/json");
        if (openai) b.header("Authorization", "Bearer " + apiKey); else b.header("x-api-key", apiKey).header("anthropic-version", "2023-06-01");
        HttpResponse<String> res = http.send(b.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() / 100 != 2) throw new IOException("Le modèle a répondu " + res.statusCode());
        JsonNode root = json.readTree(res.body());
        StringBuilder text = new StringBuilder();
        long in, out;
        if (openai) {
            text.append(root.path("choices").path(0).path("message").path("content").asText(""));
            in = root.path("usage").path("prompt_tokens").asLong(-1); out = root.path("usage").path("completion_tokens").asLong(-1);
        } else {
            for (JsonNode c : root.path("content")) if ("text".equals(c.path("type").asText())) text.append(c.path("text").asText());
            in = root.path("usage").path("input_tokens").asLong(-1); out = root.path("usage").path("output_tokens").asLong(-1);
        }
        return new Reply(text.toString(), in, out);
    }
}
