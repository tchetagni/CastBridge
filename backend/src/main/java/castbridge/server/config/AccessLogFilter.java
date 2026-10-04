package castbridge.server.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * One log line per request: method, path (never the query string, which carries device ids), status, duration.
 * No headers (the admin token), no IP address. Paths that carry a secret-ish value are redacted ({@link #redact}): the wallet receive code (audit w22-05, F4) and the device code of a TV fiche (audit w23-01, L1).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AccessLogFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger("castbridge.access");

    private static final String RECEIVE_CODE = "/api/v1/wallet/receive-code/";
    private static final String TV_FICHE = "/api/v1/admin/activations/tvs/";

    /** Le code de réception est dans le chemin de la consultation : il ne doit figurer dans aucun journal. */
    static String redact(String uri) {
        // audit w23-01 L1: the fiche of a TV carries the whole device code in its path
        if (uri != null && uri.startsWith(TV_FICHE) && uri.length() > TV_FICHE.length()) return TV_FICHE + "{deviceCode}";
        return uri != null && uri.startsWith(RECEIVE_CODE) && uri.length() > RECEIVE_CODE.length() ? RECEIVE_CODE + "{code}" : uri;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        return req.getRequestURI().startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        try {
            chain.doFilter(req, res);
        } finally {
            log.info("{} {} {} {}ms", req.getMethod(), redact(req.getRequestURI()), res.getStatus(), (System.nanoTime() - start) / 1_000_000);
        }
    }
}
