package castbridge.server.library;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the name suggester: the LLM one when CASTBRIDGE_LIBRARY_LLM_API_KEY is set, else the empty one (nothing is called). */
@Configuration
@EnableConfigurationProperties(LibrarySuggestProperties.class)
public class LibrarySuggestConfig {
    @Bean
    @ConditionalOnMissingBean(NameSuggester.class)
    NameSuggester nameSuggester(LibrarySuggestProperties props, ObjectMapper json) {
        if (!props.llmConfigured()) return new DisabledNameSuggester();
        LlmProvider provider = new HttpLlmProvider(props.provider(), props.llmUrl(), props.llmApiKey(), json);
        return new LlmNameSuggester(provider, props.llmModel(), PromptRegistry.load(props.promptVersion()), props.maxOutputTokens(),
                LibrarySuggestProperties.OPENAI.equals(props.provider()), json);
    }

    @Bean
    CostMeter libraryCostMeter(LibrarySuggestProperties props) {
        return new CostMeter(props.priceInPerMtok(), props.priceOutPerMtok(), props.dailyBudgetUsd(), props.perDeviceDailyItems(), System::currentTimeMillis);
    }
}
