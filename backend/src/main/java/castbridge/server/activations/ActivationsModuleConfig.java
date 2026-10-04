package castbridge.server.activations;

import castbridge.server.web.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Wiring of the module: its settings, the gate (404 while the module is off, 503 without the secret act-ref.key, no cache on any answer) on every route of the module, and
 * the audit of the reads on the whole administration prefix. The module is OFF by default ({@code castbridge.activations.enabled}, CASTBRIDGE_ACTIVATIONS_ENABLED=1).
 */
@Configuration
@EnableConfigurationProperties(ActivationsProperties.class)
public class ActivationsModuleConfig implements WebMvcConfigurer {
    static final String ADMIN_API = "/api/v1/admin/activations";

    private final ActivationsProperties props;
    private final TvRef tvRef;
    private final ReadAuditInterceptor readAudit;

    public ActivationsModuleConfig(ActivationsProperties props, TvRef tvRef, ReadAuditInterceptor readAudit) {
        this.props = props;
        this.tvRef = tvRef;
        this.readAudit = readAudit;
    }

    @Override
    public void addInterceptors(InterceptorRegistry r) {
        r.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) {
                res.setHeader("Cache-Control", "no-store");
                if (!props.enabled()) throw ApiException.notFound("Cette adresse n'existe pas");
                if (!tvRef.available()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Suivi des activations indisponible");
                return true;
            }
        }).addPathPatterns(ADMIN_API, ADMIN_API + "/**", "/api/v1/activations/**", "/admin/activations", "/admin/activations/**").order(0);
        r.addInterceptor(readAudit).addPathPatterns(ADMIN_API, ADMIN_API + "/**").order(1);
    }
}
