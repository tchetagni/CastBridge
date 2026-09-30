package castbridge.server.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.ShallowEtagHeaderFilter;

@Configuration
public class WebConfig {

    /**
     * ETag / If-None-Match (304) on the device-facing JSON reads: a TV that re-syncs an unchanged bank downloads
     * nothing. The ETag is the hash of the body, so it is always right.
     */
    @Bean
    FilterRegistrationBean<ShallowEtagHeaderFilter> etagFilter() {
        var reg = new FilterRegistrationBean<>(new ShallowEtagHeaderFilter());
        reg.addUrlPatterns("/api/v1/quiz/*", "/api/v1/updates/public-key");
        reg.setName("etagFilter");
        return reg;
    }

    @Bean
    OpenAPI castbridgeOpenApi() {
        return new OpenAPI()
                .info(new Info().title("CastBridge server").version("v1")
                        .description("Mises à jour des apps, banque de questions du quiz, statistiques d'appareils"))
                .components(new Components().addSecuritySchemes("admin",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer")))
                .addSecurityItem(new SecurityRequirement().addList("admin"));
    }
}
