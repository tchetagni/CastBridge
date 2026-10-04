package castbridge.server.activations;

import castbridge.server.licenses.Actor;
import castbridge.server.licenses.LicenseProperties;
import castbridge.server.web.ApiException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The audit of the READS of the administration (who looked at what, since a read can be as sensitive as a write): one line per read of a list, a fiche, the dashboard, an
 * alert, an export, a checkpoint, chained like {@link EventLog} (head number 2). The admin bearer token is audited too, as {@code api-token}. A line holds the route, the
 * normalized filters (never a token, a key, a whole device code), the target (a {@code tv_ref} or the 8 first hex of a fingerprint), the number of rows rendered and whether
 * it was an export.
 */
@Service
public class ReadAudit {
    static final int HEAD = 2;
    private static final int BATCH = 500;
    private static final Pattern TOKEN = Pattern.compile("cbx1\\.[A-Za-z0-9_.=+/-]*");
    /** A long run of base64 / base32 characters (a compact key of 112 characters, a token part) is never recopied; a lowercase hex hash (a fingerprint) may be. */
    private static final Pattern LONG_RUN = Pattern.compile("[A-Za-z0-9+/_=.%-]{20,}");
    private static final Pattern HEX_HASH = Pattern.compile("[0-9a-f]{20,64}");
    /** Request attribute: how many audit lines this request already wrote itself (the interceptor writes none of its own then). */
    static final String AUDITED = "castbridge.activations.audited";
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ReadAudit.class);
    private static final Pattern SECRET_PARAM = Pattern.compile("(?i)(token|authorization|password|secret|key|signature|challenge|totp|bearer)");

    public record Line(long id, long atMs, String actor, String role, String channel, String route, String params, String target, int rows, boolean export, String prevHash, String hash) {}

    private final JdbcTemplate jdbc;
    private final ActClock clock;
    private final byte[] key;
    private final Checkpoints checkpoints;

    public ReadAudit(JdbcTemplate jdbc, LicenseProperties props, ActClock clock, @org.springframework.context.annotation.Lazy Checkpoints checkpoints) {
        this.checkpoints = checkpoints;
        this.jdbc = jdbc;
        this.clock = clock;
        this.key = Chains.readKey(props.secretsDir().resolve("act-audit.key"));
    }

    /** Filters as "name=value" pairs sorted by name, the secrets and the cursor left out, tokens and whole device codes masked, cut to 300 characters. */
    public static String normalize(Map<String, String[]> params) {
        Map<String, String> sorted = new TreeMap<>();
        params.forEach((k, v) -> {
            if (k.equals("cursor") || SECRET_PARAM.matcher(k).find() || v == null || v.length == 0) return;
            sorted.put(k, String.join(",", v));
        });
        List<String> parts = new ArrayList<>();
        sorted.forEach((k, v) -> parts.add(k + "=" + mask(v)));
        String s = String.join("&", parts);
        return Chains.clip(s, 300);
    }

    static String mask(String v) {
        String t = TOKEN.matcher(v).replaceAll("[jeton]");
        t = Chains.DEVICE_CODE.matcher(t).replaceAll(m -> m.group().substring(0, 4) + "-****");
        return LONG_RUN.matcher(t).replaceAll(m -> HEX_HASH.matcher(m.group()).matches() || m.group().contains("****") ? m.group() : "[long]");
    }

    /** The same normalisation for filters already parsed by a service (no HTTP request around). */
    public static String normalizeFilters(Map<String, String> filters) {
        Map<String, String[]> m = new TreeMap<>();
        if (filters != null) filters.forEach((k, v) -> m.put(k, new String[] {v}));
        return normalize(m);
    }

    /**
     * Audit H2: the line of a read, written BEFORE its data is returned, in its own transaction. If it cannot be written the read FAILS CLOSED: 503 and no data (the exception
     * carries no data). Called by the service methods themselves, so that the web pages are covered like the JSON API.
     */
    public void recordRead(Actor actor, String route, Map<String, String> filters, String target, int rows, boolean export) {
        try {
            record(actor.name(), actor.role() == null ? "-" : actor.role().name(), actor.channel(), route, normalizeFilters(filters), target, rows, export);
        } catch (RuntimeException e) {
            log.error("read audit line not written, the read is refused ({})", e.getClass().getSimpleName());
            throw new ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "Audit des lectures indisponible : lecture refusée");
        }
        flag();
    }

    /** A refused read leaves a line (rows 0, denied=403): somebody trying what they may not do must show. Best effort: nothing was returned. */
    public void recordDenied(Actor actor, String route, Map<String, String> filters) {
        try {
            Map<String, String> f = new TreeMap<>(filters == null ? Map.of() : filters);
            f.put("denied", "403");
            record(actor == null ? "-" : actor.name(), actor == null || actor.role() == null ? "-" : actor.role().name(), actor == null ? "-" : actor.channel(), route, normalizeFilters(f), null, 0, false);
        } catch (RuntimeException e) {
            log.error("read audit line of a refusal not written ({})", e.getClass().getSimpleName());
        }
        flag();
    }

    private static void flag() {
        var attrs = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        if (attrs != null) attrs.setAttribute(AUDITED, Boolean.TRUE, org.springframework.web.context.request.RequestAttributes.SCOPE_REQUEST);
    }

    static boolean audited(jakarta.servlet.http.HttpServletRequest req) { return req.getAttribute(AUDITED) != null; }

    /** One line, in its own transaction (it is written after the response of a read, which has none). */
    @Transactional(propagation = Propagation.REQUIRED)
    public void record(String actor, String role, String channel, String route, String params, String target, int rows, boolean export) {
        Object[] head = Chains.lockHead(jdbc, HEAD);
        long id = (Long) head[0] + 1;
        String prev = (String) head[1];
        long atMs = clock.nowMs();
        String a = Chains.clip(actor, 64), r = Chains.clip(role, 16), c = Chains.clip(channel, 8), rt = Chains.clip(route, 80), p = Chains.clip(params, 300), t = Chains.clip(target, 64);
        String hash = hash(prev, atMs, a, r, c, rt, p, t, rows, export);
        jdbc.update("INSERT INTO adm_read_audit (id, at, actor, role, channel, route, params, target, rows_rendered, export, prev_hash, hash) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                id, new Timestamp(atMs), a, r, c, rt, p, t, rows, export, prev, hash);
        Chains.setHead(jdbc, HEAD, id, hash);
    }

    private String hash(String prev, long atMs, String actor, String role, String channel, String route, String params, String target, int rows, boolean export) {
        return Chains.hash(key, prev, Long.toString(atMs), Chains.nz(actor), role, channel, route, Chains.nz(params), Chains.nz(target), Integer.toString(rows), export ? "1" : "0");
    }

    @Transactional(readOnly = true)
    public EventLog.Verification verify() {
        Object[] anchor = Chains.anchor(jdbc, "adm_read_audit");
        long after = (Long) anchor[0];
        String prev = (String) anchor[1];
        long count = 0, lastId = after;
        boolean first = true;
        while (true) {
            List<Line> rows = jdbc.query("SELECT * FROM adm_read_audit WHERE id > ? ORDER BY id LIMIT ?", (rs, i) -> map(rs), after, BATCH);
            if (rows.isEmpty()) break;
            for (Line l : rows) {
                if (!l.prevHash().equals(prev) || (first && after > 0 && l.id() != after + 1)) {
                    return new EventLog.Verification(false, count, l.id(), "Chaîne des lectures rompue avant la ligne " + l.id(), null);
                }
                first = false;
                if (!hash(prev, l.atMs(), l.actor(), l.role(), l.channel(), l.route(), l.params(), l.target(), l.rows(), l.export()).equals(l.hash())) {
                    return new EventLog.Verification(false, count, l.id(), "La ligne de lecture " + l.id() + " a été modifiée", null);
                }
                prev = l.hash();
                lastId = l.id();
                count++;
                after = l.id();
            }
        }
        var head = jdbc.queryForMap("SELECT last_id, last_hash FROM act_event_head WHERE id = ?", HEAD);
        if (((Number) head.get("last_id")).longValue() != lastId || !head.get("last_hash").equals(prev)) {
            return new EventLog.Verification(false, count, lastId + 1, "La fin du journal des lectures ne correspond pas à la tête de chaîne", null);
        }
        String cross = checkpoints.crossCheck("adm_read_audit", lastId);   // audit H3
        if (cross != null) return new EventLog.Verification(false, count, null, cross, null);
        return new EventLog.Verification(true, count, null, null, prev);
    }

    public String headHash() { return jdbc.queryForObject("SELECT last_hash FROM act_event_head WHERE id = ?", String.class, HEAD); }

    public long lastId() { return jdbc.queryForObject("SELECT last_id FROM act_event_head WHERE id = ?", Long.class, HEAD); }

    static Line map(ResultSet rs) throws SQLException {
        return new Line(rs.getLong("id"), rs.getTimestamp("at").getTime(), rs.getString("actor"), rs.getString("role"), rs.getString("channel"), rs.getString("route"), rs.getString("params"),
                rs.getString("target"), rs.getInt("rows_rendered"), rs.getBoolean("export"), rs.getString("prev_hash"), rs.getString("hash"));
    }
}
