package castbridge.server.library;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the name suggester: the LLM one when CASTBRIDGE_LIBRARY_LLM_API_KEY is set, else the empty one (nothing is called). */
@Configuration
@EnableConfigurationProperties(LibrarySuggestProperties.class)
public class LibrarySuggestConfig {
    @Bean
    @ConditionalOnMissingBean(NameSuggester.class)
    NameSuggester nameSuggester(LibrarySuggestProperties props, ObjectMapper json) {
        return props.llmConfigured() ? new LlmNameSuggester(props.llmUrl(), props.llmApiKey(), props.llmModel(), json) : new DisabledNameSuggester();
    }
}
