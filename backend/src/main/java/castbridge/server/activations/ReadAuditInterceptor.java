package castbridge.server.activations;

import castbridge.server.licenses.Actor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/**
 * One audit line per READ of the administration API (GET /api/v1/admin/activations/**): list, fiche, dashboard, alerts, tools, export, checkpoints, integrity. Registered ONCE
 * for the whole prefix by {@link ActivationsModuleConfig}, so that a new read route is audited without anyone thinking of it (the test that lists the routes by reflection
 * fails otherwise). The long poll {@code /changes} is audited once per tracking session by its controller, not by turn. Also the limit of 120 reads a minute per actor.
 *
 * <p>A read that ended in 404 is a line too (rows 0): somebody probing for codes must show. Written after the response; a failure to write is logged (no data in the message).
 */
@Component
public class ReadAuditInterceptor implements HandlerInterceptor {
    private static final Logger log = LoggerFactory.getLogger(ReadAuditInterceptor.class);
    static final String CHANGES = "/api/v1/admin/activations/changes";

    private final ReadAudit audit;
    private final ActAccess access;
    private final ActivationsPolicy policy;

    public ReadAuditInterceptor(ReadAudit audit, ActAccess access, ActivationsPolicy policy) {
        this.audit = audit;
        this.access = access;
        this.policy = policy;
    }

    private static boolean applies(HttpServletRequest req) { return "GET".equals(req.getMethod()); }

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) {
        if (applies(req)) {
            Actor actor = access.actorOf(SecurityContextHolder.getContext().getAuthentication());
            policy.limit("read:" + actor.name(), 120, Duration.ofMinutes(1), "lectures");
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest req, HttpServletResponse res, Object handler, Exception ex) {
        if (!applies(req) || ex != null) return;
        int status = res.getStatus();
        if (!((status >= 200 && status < 300) || status == 404)) return;
        Object pattern = req.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String route = pattern == null ? req.getRequestURI() : pattern.toString();
        if (route.equals(CHANGES)) return;
        try {
            Actor actor = access.actorOf(SecurityContextHolder.getContext().getAuthentication());
            AuditNote note = AuditNote.of(req);
            audit.record(actor.name(), actor.role() == null ? "-" : actor.role().name(), actor.channel(), route, ReadAudit.normalize(req.getParameterMap()), note.target(), status == 404 ? 0 : note.rows(), note.export());
        } catch (RuntimeException e) {
            log.error("read audit line not written ({})", e.getClass().getSimpleName());
        }
    }
}
