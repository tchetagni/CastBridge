package castbridge.server.library;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the library assistant's name suggestions (docs/LIBRARY-AGENT.md). Everything comes from environment variables;
 * no key is ever stored in the repository. Without {@code llmApiKey} the endpoint answers {@code available:false} and calls nothing.
 *
 * @param llmApiKey        key of the LLM service (CASTBRIDGE_LIBRARY_LLM_API_KEY); empty = no model (the default)
 * @param llmUrl           endpoint of the LLM service (CASTBRIDGE_LIBRARY_LLM_URL); empty = the usual endpoint of the provider
 * @param llmModel         model identifier (CASTBRIDGE_LIBRARY_LLM_MODEL)
 * @param perHour          requests allowed per device and hour (CASTBRIDGE_LIBRARY_SUGGEST_PER_HOUR)
 * @param maxItems         names allowed per request
 * @param provider         "anthropic" (Messages API, default) or "openai" (any OpenAI-compatible chat completions service) (CASTBRIDGE_LIBRARY_LLM_PROVIDER)
 * @param promptVersion    version of the prompt file {@code library/prompts/suggest-<version>.txt} (CASTBRIDGE_LIBRARY_PROMPT_VERSION)
 * @param priceInPerMtok   USD per million input tokens, to estimate the cost of an analysis (CASTBRIDGE_LIBRARY_PRICE_IN_PER_MTOK)
 * @param priceOutPerMtok  USD per million output tokens (CASTBRIDGE_LIBRARY_PRICE_OUT_PER_MTOK)
 * @param dailyBudgetUsd   estimated spending per UTC day for the whole server; beyond it the model is no longer called (CASTBRIDGE_LIBRARY_DAILY_BUDGET_USD)
 * @param perDeviceDailyItems names a device may have analysed per UTC day (CASTBRIDGE_LIBRARY_DEVICE_DAILY_ITEMS)
 * @param maxOutputTokens  cap of the model's answer (CASTBRIDGE_LIBRARY_MAX_OUTPUT_TOKENS)
 */
@ConfigurationProperties(prefix = "castbridge.library")
public record LibrarySuggestProperties(String llmApiKey, String llmUrl, String llmModel, Integer perHour, Integer maxItems,
                                       String provider, String promptVersion, Double priceInPerMtok, Double priceOutPerMtok,
                                       Double dailyBudgetUsd, Integer perDeviceDailyItems, Integer maxOutputTokens) {
    public static final String ANTHROPIC = "anthropic";
    public static final String OPENAI = "openai";

    public LibrarySuggestProperties {
        if (llmApiKey == null) llmApiKey = "";
        provider = provider == null || provider.isBlank() ? ANTHROPIC : provider.trim().toLowerCase();
        if (!provider.equals(ANTHROPIC) && !provider.equals(OPENAI)) throw new IllegalArgumentException("castbridge.library.provider : anthropic ou openai");
        if (llmUrl == null || llmUrl.isBlank()) llmUrl = provider.equals(OPENAI) ? "https://api.openai.com/v1/chat/completions" : "https://api.anthropic.com/v1/messages";
        if (llmModel == null || llmModel.isBlank()) llmModel = provider.equals(OPENAI) ? "gpt-4o-mini" : "claude-haiku-4-5";
        if (perHour == null || perHour < 1) perHour = 30;
        if (maxItems == null || maxItems < 1 || maxItems > 100) maxItems = 40;
        if (promptVersion == null || promptVersion.isBlank()) promptVersion = "v1";
        // Claude Haiku 4.5: 1 USD in / 5 USD out per million tokens. Set the prices of the model you really use.
        if (priceInPerMtok == null || priceInPerMtok < 0) priceInPerMtok = 1.0;
        if (priceOutPerMtok == null || priceOutPerMtok < 0) priceOutPerMtok = 5.0;
        if (dailyBudgetUsd == null || dailyBudgetUsd < 0) dailyBudgetUsd = 2.0;
        if (perDeviceDailyItems == null || perDeviceDailyItems < 1) perDeviceDailyItems = 300;
        if (maxOutputTokens == null || maxOutputTokens < 256 || maxOutputTokens > 8192) maxOutputTokens = 3000;
    }

    public boolean llmConfigured() { return !llmApiKey.isBlank(); }
}
