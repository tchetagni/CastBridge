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
 * <p>Audit H2: the services write the line of a read THEMSELVES, before returning data, and fail closed (see {@link ReadAudit#recordRead}). This interceptor is only the safety
 * net: a read that ended without any line from its service (a new route that forgot to audit) gets one here, with an ERROR in the log that says so; a refusal (403) or a 404 of
 * a handler that did not audit leaves a line too. Written after the response, so a failure here is only logged (no data in the message).
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
        if (!applies(req)) return;
        if (ReadAudit.audited(req)) return;
        int status = res.getStatus();
        if (!((status >= 200 && status < 300) || status == 404 || status == 403) || (ex != null && status < 400)) return;
        Object pattern = req.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String route = pattern == null ? req.getRequestURI() : pattern.toString();
        if (route.equals(CHANGES)) return;
        try {
            Actor actor = access.actorOf(SecurityContextHolder.getContext().getAuthentication());
            if (status >= 200 && status < 300) log.error("read route {} did not audit itself: line written by the safety net", route);
            AuditNote note = AuditNote.of(req);
            audit.record(actor.name(), actor.role() == null ? "-" : actor.role().name(), actor.channel(), route,
                    ReadAudit.normalize(req.getParameterMap()) + (status == 403 ? "&denied=403" : ""), note.target(), status >= 400 ? 0 : note.rows(), note.export());
        } catch (RuntimeException e) {
            log.error("read audit line not written ({})", e.getClass().getSimpleName());
        }
    }
}
