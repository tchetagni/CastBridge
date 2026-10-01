package castbridge.server.library;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the library assistant's name suggestions (docs/LIBRARY-AGENT.md). Everything comes from environment variables;
 * no key is ever stored in the repository. Without {@code llmApiKey} the endpoint answers {@code available:false} and calls nothing.
 *
 * @param llmApiKey  key of the LLM service (CASTBRIDGE_LIBRARY_LLM_API_KEY); empty = no model (the default)
 * @param llmUrl     Messages API endpoint of the LLM service (CASTBRIDGE_LIBRARY_LLM_URL)
 * @param llmModel   model identifier (CASTBRIDGE_LIBRARY_LLM_MODEL)
 * @param perHour    requests allowed per device and hour (CASTBRIDGE_LIBRARY_SUGGEST_PER_HOUR)
 * @param maxItems   names allowed per request
 */
@ConfigurationProperties(prefix = "castbridge.library")
public record LibrarySuggestProperties(String llmApiKey, String llmUrl, String llmModel, Integer perHour, Integer maxItems) {
    public LibrarySuggestProperties {
        if (llmApiKey == null) llmApiKey = "";
        if (llmUrl == null || llmUrl.isBlank()) llmUrl = "https://api.anthropic.com/v1/messages";
        if (llmModel == null || llmModel.isBlank()) llmModel = "claude-haiku-4-5";
        if (perHour == null || perHour < 1) perHour = 30;
        if (maxItems == null || maxItems < 1 || maxItems > 100) maxItems = 40;
    }

    public boolean llmConfigured() { return !llmApiKey.isBlank(); }
}
