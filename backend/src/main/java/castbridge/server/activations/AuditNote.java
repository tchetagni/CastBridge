package castbridge.server.activations;

import jakarta.servlet.http.HttpServletRequest;

/** What a read handler tells the audit about its answer: the target (a tv_ref or the 8 first hex of a fingerprint), the number of rows rendered, whether it was an export. */
public record AuditNote(String target, int rows, boolean export) {
    static final String ATTR = "castbridge.activations.audit";

    public static void set(HttpServletRequest req, String target, int rows, boolean export) { req.setAttribute(ATTR, new AuditNote(target, rows, export)); }

    static AuditNote of(HttpServletRequest req) {
        Object o = req.getAttribute(ATTR);
        return o instanceof AuditNote n ? n : new AuditNote(null, 0, false);
    }
}
