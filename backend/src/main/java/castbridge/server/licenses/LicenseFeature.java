package castbridge.server.licenses;

import castbridge.server.web.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** The feature switches (templates ask {@code @licenseFeature.enabled()}), and the interceptor that answers 404 when a switch is off. */
@Component("licenseFeature")
public class LicenseFeature {
    private final LicenseProperties props;

    public LicenseFeature(LicenseProperties props) { this.props = props; }

    public boolean enabled() { return props.enabled(); }

    public boolean publicRoutes() { return props.enabled() && props.publicRoutes(); }

    /** Registers the 404-when-off interceptor and the no-store header on every licence response. */
    @Configuration
    static class Wiring implements WebMvcConfigurer {
        private final LicenseFeature feature;

        Wiring(LicenseFeature feature) { this.feature = feature; }

        @Override
        public void addInterceptors(InterceptorRegistry r) {
            r.addInterceptor(new HandlerInterceptor() {
                @Override
                public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) {
                    res.setHeader("Cache-Control", "no-store");
                    if (!feature.enabled()) throw ApiException.notFound("Module licences désactivé");
                    return true;
                }
            }).addPathPatterns("/admin/licenses", "/admin/licenses/**", "/api/v1/admin/licenses", "/api/v1/admin/licenses/**");
            r.addInterceptor(new HandlerInterceptor() {
                @Override
                public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) {
                    res.setHeader("Cache-Control", "no-store");
                    if (!feature.publicRoutes()) throw ApiException.notFound("Cette adresse n'existe pas");
                    return true;
                }
            }).addPathPatterns("/api/v1/revocations", "/api/v1/entitlements/me");
        }
    }
}
