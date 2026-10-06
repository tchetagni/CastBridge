package castbridge.server.config;

import castbridge.server.web.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Two security chains:
 * <ul>
 *   <li>API ({@code /api/**}, {@code /dl/**}, {@code /v3/**}): stateless, no cookie. Admin routes and the OpenAPI
 *       description need the bearer token; the rest is public for the devices (rate limited). CORS closed.</li>
 *   <li>Admin web interface ({@code /admin/**}): login form (BCrypt accounts), session cookie, CSRF protection,
 *       strict Content-Security-Policy (no inline script or style).</li>
 * </ul>
 */
@Configuration
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /** The single AuthenticationProvider: password + optional TOTP (see AdminAuthenticationProvider). */
    @Bean
    org.springframework.security.authentication.AuthenticationProvider adminAuthenticationProvider(
            castbridge.server.admin.AdminAccounts accounts, PasswordEncoder encoder, castbridge.server.licenses.LicenseAccounts licenseAccounts) {
        return new castbridge.server.admin.AdminAuthenticationProvider(accounts, encoder, licenseAccounts);
    }

    @Bean
    @Order(1)
    SecurityFilterChain apiChain(HttpSecurity http, AdminToken token, ObjectMapper json) throws Exception {
        http
                .securityMatcher("/api/**", "/dl/**", "/v3/**")
                .csrf(AbstractHttpConfigurer::disable) // no cookie, no session: nothing for CSRF to abuse
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(c -> c.configurationSource(closedCors()))
                .addFilterBefore(new BearerFilter(token), AnonymousAuthenticationFilter.class)
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/api/v1/admin/**", "/v3/api-docs", "/v3/api-docs/**").hasRole("ADMIN")
                        .anyRequest().permitAll())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> {
                            res.setHeader("WWW-Authenticate", "Bearer");
                            ApiError.write(res, json, 401, token.enabled()
                                    ? "Jeton d'administration manquant ou invalide"
                                    : "Administration désactivée : CASTBRIDGE_ADMIN_TOKEN n'est pas configuré", req.getRequestURI());
                        })
                        .accessDeniedHandler((req, res, ex) ->
                                ApiError.write(res, json, 403, "Accès refusé", req.getRequestURI())))
                .headers(h -> h
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .frameOptions(f -> f.deny()));
        return http.build();
    }

    /** Public user guide ({@code /guide/**}): read-only, no authentication, stateless, no cookie; the controller sets the cache headers. */
    @Bean
    @Order(0)
    SecurityFilterChain guideChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/guide", "/guide/**")
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(c -> c.configurationSource(closedCors()))
                .authorizeHttpRequests(a -> a.anyRequest().permitAll())
                .headers(h -> h
                        .cacheControl(c -> c.disable())
                        .contentSecurityPolicy(csp -> csp.policyDirectives(castbridge.server.guide.GuideController.CSP))
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .frameOptions(f -> f.deny()));
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain webChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/admin/login", "/admin/login/", "/admin/assets/**").permitAll()
                        .requestMatchers("/admin", "/admin/**").hasRole("WEBADMIN")
                        .anyRequest().permitAll())
                .formLogin(f -> f
                        .loginPage("/admin/login")
                        .loginProcessingUrl("/admin/login")
                        .authenticationDetailsSource(castbridge.server.admin.AdminAuthenticationProvider.TotpDetails::new)
                        .defaultSuccessUrl("/admin", true)
                        .failureUrl("/admin/login?erreur"))
                .logout(l -> l.logoutUrl("/admin/logout").logoutSuccessUrl("/admin/login?deconnexion").deleteCookies("CBSESSION"))
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionFixation(f -> f.changeSessionId()))
                .headers(h -> h
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'; img-src 'self' data:; style-src 'self'; "
                                + "script-src 'self'; form-action 'self'; frame-ancestors 'none'; base-uri 'none'; object-src 'none'"))
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN))
                        .frameOptions(f -> f.deny()));
        return http.build();
    }

    /** CORS closed: no origin is allowed, preflight requests are refused. */
    private static CorsConfigurationSource closedCors() {
        CorsConfiguration none = new CorsConfiguration();
        none.setAllowedOrigins(List.of());
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", none);
        return source;
    }

    /** Authenticates a request carrying the right "Authorization: Bearer …" admin header as ROLE_ADMIN. */
    static final class BearerFilter extends OncePerRequestFilter {
        private final AdminToken token;

        BearerFilter(AdminToken token) { this.token = token; }

        @Override
        protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                throws ServletException, IOException {
            if (token.matchesHeader(req.getHeader("Authorization"))) {
                var auth = new UsernamePasswordAuthenticationToken("admin", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
            chain.doFilter(req, res);
        }
    }
}
