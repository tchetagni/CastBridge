package castbridge.server.library;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.List;

/**
 * Asks an LLM (through an {@link LlmProvider}) to read cleaned file names. The prompt is a versioned file ({@link PromptRegistry}); the user message
 * contains only the names ("prison break s01e04"), their extension, a kind hint and a rounded duration: nothing that identifies a person or a device.
 * The answer is validated strictly by {@link SuggestionParser}.
 */
public class LlmNameSuggester implements NameSuggester {
    private final LlmProvider provider;
    private final String model;
    private final PromptRegistry.Prompt prompt;
    private final int maxOutputTokens;
    private final boolean jsonOnly;
    private final ObjectMapper json;

    public LlmNameSuggester(LlmProvider provider, String model, PromptRegistry.Prompt prompt, int maxOutputTokens, boolean jsonOnly, ObjectMapper json) {
        this.provider = provider; this.model = model; this.prompt = prompt; this.maxOutputTokens = maxOutputTokens; this.jsonOnly = jsonOnly; this.json = json;
    }

    @Override public String model() { return model; }

    @Override public boolean available() { return true; }

    @Override public String promptText() { return prompt.text(); }

    @Override
    public List<Suggestion> suggest(String lang, List<Item> items) throws SuggesterException { return suggestWithUsage(lang, items).suggestions(); }

    @Override
    public Outcome suggestWithUsage(String lang, List<Item> items) throws SuggesterException {
        if (items.isEmpty()) return new Outcome(List.of(), Usage.NONE, prompt.version());
        String system = prompt.text() + ("en".equals(lang) ? "\nLes titres restent dans leur langue d'origine." : "");
        ArrayNode list = json.createArrayNode();
        for (Item it : items) {
            ObjectNode o = list.addObject();
            o.put("i", it.index());
            o.put("nom", it.text());
            o.put("extension", it.ext());
            if (it.kindHint() != null) o.put("indice", it.kindHint());
            if (it.durationMin() != null) o.put("duree_min", it.durationMin());
        }
        String user = list.toString();
        LlmProvider.Reply reply;
        try {
            reply = provider.complete(new LlmProvider.Request(model, system, user, maxOutputTokens, jsonOnly));
        } catch (IOException e) {
            throw new SuggesterException(e.getMessage() != null && e.getMessage().startsWith("Le modèle a répondu") ? e.getMessage() : "Le modèle est injoignable", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SuggesterException("Interrompu", e);
        }
        boolean estimated = reply.inputTokens() < 0 || reply.outputTokens() < 0;
        long in = estimated ? CostMeter.estimateTokens(system + user) : reply.inputTokens();
        long out = estimated ? CostMeter.estimateTokens(reply.text()) : reply.outputTokens();
        SuggestionParser.Parsed p = SuggestionParser.parse(json, reply.text(), items.size());
        return new Outcome(p.suggestions(), new Usage(in, out, p.rejected(), estimated), prompt.version());
    }
}
