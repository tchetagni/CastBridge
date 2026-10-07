package castbridge.server.config;

import castbridge.server.web.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

/**
 * Simple per-IP token bucket on /api, /dl, the public pages (/guide, /telecharger) and the admin login form (in memory: one server instance). A request
 * carrying the valid admin token is not limited. The client IP is the X-Forwarded-For set by the local nginx
 * (server.forward-headers-strategy=framework; the port is only published on 127.0.0.1).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {
    private static final int MAX_TRACKED_IPS = 100_000;

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final double perNano;
    private final double capacity;
    private final AdminToken adminToken;
    private final ObjectMapper json;

    public RateLimitFilter(CastbridgeProperties props, AdminToken adminToken, ObjectMapper json) {
        this.perNano = Math.max(1, props.rateLimit().perMinute()) / 60e9;
        this.capacity = Math.max(1, props.rateLimit().burst());
        this.adminToken = adminToken;
        this.json = json;
    }

    /**
     * The path as the controllers see it: percent-decoded, without ";params" and doubled slashes, context path removed. The raw request
     * URI alone let "/%64l/…", "/%61pi/…" or "/%74elecharger" (a percent-encoded letter) past the limiter while Spring MVC still served them.
     */
    static String publicPath(HttpServletRequest req) {
        return UrlPathHelper.defaultInstance.getPathWithinApplication(req);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        String p = publicPath(req);
        return !(p.startsWith("/api/") || p.startsWith("/dl/") || p.startsWith("/v3/") || p.equals("/guide") || p.startsWith("/guide/")
                || p.equals("/telecharger") || p.startsWith("/telecharger/")
                || ("POST".equals(req.getMethod()) && (p.equals("/admin/login") || p.startsWith("/admin/licenses"))));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        if (!adminToken.matchesHeader(req.getHeader("Authorization"))) {
            if (buckets.size() > MAX_TRACKED_IPS) evictIdle(0);
            Bucket b = buckets.computeIfAbsent(req.getRemoteAddr(), k -> new Bucket(capacity));
            long waitSeconds = b.take(capacity, perNano);
            if (waitSeconds > 0) {
                res.setHeader("Retry-After", Long.toString(waitSeconds));
                ApiError.write(res, json, 429, "Trop de requêtes depuis cette adresse : réessayez dans " + waitSeconds + " s",
                        req.getRequestURI());
                return;
            }
        }
        chain.doFilter(req, res);
    }

    /** Forgets the buckets that have been full again for a while (idle clients). */
    @Scheduled(fixedDelay = 300_000)
    void evictIdle() { evictIdle(600_000_000_000L); }

    private void evictIdle(long idleNanos) {
        long now = System.nanoTime();
        buckets.entrySet().removeIf(e -> e.getValue().idleSince(now) > idleNanos);
    }

    int trackedIps() { return buckets.size(); }

    static final class Bucket {
        private double tokens;
        private long last = System.nanoTime();

        Bucket(double capacity) { tokens = capacity; }

        /** Takes one token; returns 0 if allowed, else the number of seconds to wait. */
        synchronized long take(double capacity, double perNano) {
            long now = System.nanoTime();
            tokens = Math.min(capacity, tokens + (now - last) * perNano);
            last = now;
            if (tokens >= 1) {
                tokens -= 1;
                return 0;
            }
            return Math.max(1, (long) Math.ceil((1 - tokens) / perNano / 1e9));
        }

        synchronized long idleSince(long now) { return now - last; }
    }
}
