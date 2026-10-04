package castbridge.server.activations;

import castbridge.server.common.Times;
import castbridge.server.licenses.Actor;
import castbridge.server.licenses.DeviceIdentity;
import castbridge.server.licenses.LicenseProperties;
import castbridge.server.licenses.TrustedKeys;
import castbridge.server.web.ApiException;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * The read side of the module, one class for the JSON API and for the pages of w23-02 (the same services, not only the controller). Every method checks its permission
 * FIRST ({@link ActPermissions}, design 6.2). Lists are paged by CURSOR on {@code (sort key, id)}: no OFFSET, no COUNT over the history. The history of a TV or of an
 * activation is read through its own index. The device code is shown in full only on the fiche of a TV (and masked, or by its end, everywhere else).
 */
@Service
public class ActivationsAdminService {
    public static final int DEFAULT_LIMIT = 50, MAX_LIMIT = 500;
    private static final Pattern HEX = Pattern.compile("[0-9a-f]{8,64}");
    private static final Set<String> FLAGS = Set.of("declared_journal", "declared_registry", "server_issued", "seen_on_tv", "delivered_bt", "undeclared", "clone", "out_of_window");
    private static final Set<String> STATES = Set.of("EMISE", "ACTIVATED", "EXPIRED_UNUSED", "REPLACED", "ENDED", "REVOKED");

    static final String P = ActivationsModuleConfig.ADMIN_API;
    static final String R_ACTIVATIONS = P + "/activations", R_ACTIVATION = P + "/activations/{fp}", R_TVS = P + "/tvs", R_TV = P + "/tvs/{deviceCode}", R_DASHBOARD = P + "/dashboard",
            R_ALERTS = P + "/alerts", R_TOOLS = P + "/tools", R_INTEGRITY = P + "/integrity", R_CHECKPOINTS = P + "/checkpoints", R_READ_AUDIT = P + "/read-audit", R_CHANGES = P + "/changes";

    /**
     * Audit H2: a read audits ITSELF, here, BEFORE its data is returned, so that the web pages (w23-02) that call this service directly are covered like the JSON API and a read
     * whose audit line cannot be written fails closed (503, no data): see {@link ReadAudit#recordRead}. A refusal leaves a line too.
     */
    private void requireRead(Actor actor, ActPermissions.Perm perm, String route, Map<String, String> params) {
        try {
            access.require(actor, perm);
        } catch (ApiException e) {
            if (e.status() == org.springframework.http.HttpStatus.FORBIDDEN) readAudit.recordDenied(actor, route, params);
            throw e;
        }
    }

    private <T> T audited(Actor actor, String route, Map<String, String> params, String target, int rows, boolean export, T data) {
        readAudit.recordRead(actor, route, params, target, rows, export);
        return data;
    }

    private static Map<String, String> params(Map<String, String> f, Integer limit) {
        Map<String, String> m = new TreeMap<>(f == null ? Map.of() : f);
        if (limit != null) m.put("limit", limit.toString());
        return m;
    }

    /** A page: the rows, the cursor of the next page (null at the end), the limit applied. */
    public record CursorPage(List<?> items, String nextCursor, int limit) {}

    private final JdbcTemplate jdbc;
    private final ActClock clock;
    private final ActAccess access;
    private final TvRef tvRef;
    private final ToolDirectory tools;
    private final ActivationsPolicy policy;
    private final AlertService alerts;
    private final EventLog eventLog;
    private final ReadAudit readAudit;
    private final Checkpoints checkpoints;
    private final TrustedKeys trusted;
    private final LicenseProperties licenseProps;

    public ActivationsAdminService(JdbcTemplate jdbc, ActClock clock, ActAccess access, TvRef tvRef, ToolDirectory tools, ActivationsPolicy policy, AlertService alerts, EventLog eventLog, ReadAudit readAudit,
                                   Checkpoints checkpoints, TrustedKeys trusted, LicenseProperties licenseProps) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.access = access;
        this.tvRef = tvRef;
        this.tools = tools;
        this.policy = policy;
        this.alerts = alerts;
        this.eventLog = eventLog;
        this.readAudit = readAudit;
        this.checkpoints = checkpoints;
        this.trusted = trusted;
        this.licenseProps = licenseProps;
    }

    // ================================================================== helpers

    static int limitOf(Integer limit) { return limit == null || limit < 1 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT); }

    static String encode(String a, String b) { return Base64.getUrlEncoder().withoutPadding().encodeToString((a + "\u001f" + b).getBytes(StandardCharsets.UTF_8)); }

    static String[] decode(String cursor) {
        try {
            String[] p = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split("\u001f", -1);
            if (p.length != 2) throw new IllegalArgumentException();
            return p;
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Curseur invalide : repartez de la première page");
        }
    }

    private static Instant inst(Object t) { return t == null ? null : Times.instant(t); }

    private static Timestamp ts(String ms) {
        try {
            return new Timestamp(Long.parseLong(ms));
        } catch (NumberFormatException e) {
            throw ApiException.badRequest("Curseur invalide : repartez de la première page");
        }
    }

    /** ISO instant, or a date (start of the day, UTC); for an upper bound a bare date is the START of that day too (exclusive). */
    static Timestamp when(String s, String what) {
        if (s == null || s.isBlank()) return null;
        try {
            return Timestamp.from(Instant.parse(s.trim()));
        } catch (RuntimeException e) {
            try {
                return Timestamp.from(LocalDate.parse(s.trim()).atStartOfDay().toInstant(ZoneOffset.UTC));
            } catch (RuntimeException e2) {
                throw ApiException.badRequest(what + " : date ISO attendue (2026-10-04 ou 2026-10-04T08:00:00Z)");
            }
        }
    }

    public String freshness(Object lastTs) {
        if (lastTs == null) return "never";
        long age = clock.nowMs() - Times.ms(lastTs);
        return age < Duration.ofHours(26).toMillis() ? "fresh" : age < Duration.ofDays(7).toMillis() ? "stale" : "old";
    }

    private static String tail(String code) { return code == null || code.length() < 4 ? null : code.substring(code.length() - 4); }

    private String type(Map<String, String> memo, String kid) { return memo.computeIfAbsent(kid == null ? "-" : kid, k -> tools.typeOf(kid)); }

    // ================================================================== activations

    /** A row of the list: the activation, its TV (by reference and the end of its code), its tool, its freshness. */
    Map<String, Object> activationRow(Map<String, Object> r, Map<String, String> memo, boolean forExport) {
        Map<String, Object> m = new LinkedHashMap<>();
        String kid = (String) r.get("kid");
        m.put("fp", r.get("fp"));
        m.put("tag", r.get("tag"));
        m.put("form", r.get("form"));
        m.put("kind", r.get("kind"));
        m.put("subject", r.get("subject"));
        m.put("license", r.get("license_id"));
        m.put("seat", r.get("seat_id"));
        m.put("tvRef", r.get("tv_ref"));
        m.put("tvCodeTail", tail((String) r.get("tv_code")));
        m.put("kid", kid);
        m.put("tool", type(memo, kid));
        m.put("state", r.get("state"));
        m.put("flags", Inventory.flagsOf((String) r.get("flags")));
        if (forExport) m.put("flagsRaw", r.get("flags"));
        m.put("issuedAt", inst(r.get("issued_at")));
        m.put("windowClosesAt", inst(r.get("expires_at")));
        m.put("usageTo", inst(r.get("usage_to")));
        m.put("unlimited", r.get("unlimited"));
        m.put("super", r.get("super"));
        m.put("rights", r.get("rights"));
        m.put("firstSeenTvAt", inst(r.get("first_seen_tv_at")));
        m.put("lastSeenTvAt", inst(r.get("last_seen_tv_at")));
        m.put("lastSeenVia", r.get("last_seen_via"));
        m.put("installedAt", inst(r.get("installed_at")));
        m.put("deliveredBtAt", inst(r.get("delivered_bt_at")));
        m.put("revokedAt", inst(r.get("revoked_at")));
        m.put("freshness", freshness(r.get("last_seen_tv_at")));
        if (r.get("last_seen_tv_at") == null && r.get("expires_at") != null) {
            m.put("note", clock.now().isAfter(inst(r.get("expires_at"))) ? "expiré non utilisé : jamais constatée sur une TV, fenêtre d'installation close le " + inst(r.get("expires_at"))
                    : "émise, à installer avant le " + inst(r.get("expires_at")) + " (fenêtre de 48 h)");
        }
        return m;
    }

    public CursorPage activations(Actor actor, Map<String, String> f, String cursor, Integer limit) {
        requireRead(actor, ActPermissions.Perm.ACT_READ, R_ACTIVATIONS, params(f, limit));
        CursorPage p = queryActivations(f, cursor, limitOf(limit), false);
        return audited(actor, R_ACTIVATIONS, params(f, limit), null, p.items().size(), false, p);
    }

    /** " WHERE ..." of the activation filters (state, kind, tool, license, device, from, to, flag, q), its parameters appended to {@code args}. */
    private StringBuilder activationWhere(Map<String, String> f, List<Object> args) {
        StringBuilder w = new StringBuilder(" WHERE 1 = 1");
        if (has(f, "state")) {
            if (!STATES.contains(f.get("state"))) throw ApiException.badRequest("État inconnu");
            w.append(" AND k.state = ?");
            args.add(f.get("state"));
        }
        if (has(f, "kind")) {
            String kind = f.get("kind").toUpperCase(java.util.Locale.ROOT);
            if (!kind.equals("TRIAL") && !kind.equals("PRODUCTION")) throw ApiException.badRequest("Édition : TRIAL ou PRODUCTION");
            w.append(" AND k.kind = ?");
            args.add(kind);
        }
        if (has(f, "tool")) toolFilter(f.get("tool"), w, args);
        if (has(f, "license")) {
            w.append(" AND k.license_id = ?");
            args.add(f.get("license").trim());
        }
        if (has(f, "device")) deviceFilter(f.get("device"), w, args, "k.tv_ref");
        Timestamp from = when(f.get("from"), "from"), to = when(f.get("to"), "to");
        if (from != null) {
            w.append(" AND k.issued_at >= ?");
            args.add(from);
        }
        if (to != null) {
            w.append(" AND k.issued_at < ?");
            args.add(to);
        }
        if (has(f, "flag")) {
            if (!FLAGS.contains(f.get("flag"))) throw ApiException.badRequest("Drapeau inconnu");
            w.append(" AND k.flags LIKE ?");
            args.add("%," + f.get("flag") + ",%");
        }
        if (has(f, "q")) {
            String q = f.get("q").trim().toLowerCase(java.util.Locale.ROOT);
            if (!HEX.matcher(q).matches() && !q.matches("[0-9a-f]{4,64}")) throw ApiException.badRequest("Recherche : étiquette de 8 chiffres hexadécimaux ou début d'empreinte");
            w.append(" AND (k.tag = ? OR k.fp LIKE ?)");
            args.add(q.length() == 8 ? q : "-");
            args.add(q + "%");
        }
        return w;
    }

    CursorPage queryActivations(Map<String, String> f, String cursor, int limit, boolean forExport) {
        List<Object> args = new ArrayList<>();
        StringBuilder w = activationWhere(f, args);
        boolean seen = "seen".equals(f.get("sort"));
        String sort = seen ? "COALESCE(k.last_seen_tv_at, k.issued_at)" : "k.issued_at";
        if (cursor != null && !cursor.isBlank()) {
            String[] c = decode(cursor);
            Timestamp t = ts(c[0]);
            w.append(" AND (").append(sort).append(" < ? OR (").append(sort).append(" = ? AND k.fp < ?))");
            args.add(t);
            args.add(t);
            args.add(c[1]);
        }
        args.add(limit + 1);
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT k.*, t.device_code AS tv_code FROM act_key k LEFT JOIN act_tv t ON t.tv_ref = k.tv_ref" + w + " ORDER BY " + sort + " DESC, k.fp DESC LIMIT ?",
                args.toArray());
        String next = null;
        if (rows.size() > limit) {
            rows = rows.subList(0, limit);
            Map<String, Object> last = rows.get(limit - 1);
            Object sv = seen && last.get("last_seen_tv_at") != null ? last.get("last_seen_tv_at") : last.get("issued_at");
            next = encode(Long.toString(sv == null ? 0 : Times.ms(sv)), (String) last.get("fp"));
        }
        Map<String, String> memo = new java.util.HashMap<>();
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> r : rows) items.add(activationRow(r, memo, forExport));
        return new CursorPage(items, next, limit);
    }

    private static boolean has(Map<String, String> f, String k) { return f.get(k) != null && !f.get(k).isBlank(); }

    private void toolFilter(String tool, StringBuilder w, List<Object> args) {
        String t = tool.trim();
        if (ToolDirectory.TYPES.contains(t.toUpperCase(java.util.Locale.ROOT))) {
            String type = t.toUpperCase(java.util.Locale.ROOT);
            if (type.equals("UNKNOWN")) {
                Set<String> known = tools.knownKids();
                if (known.isEmpty()) return;
                w.append(" AND (k.kid IS NULL OR k.kid NOT IN (").append(String.join(",", Collections.nCopies(known.size(), "?"))).append("))");
                args.addAll(known);
            } else {
                List<String> kids = tools.kidsOf(type);
                if (kids.isEmpty()) {
                    w.append(" AND 1 = 0");
                } else {
                    w.append(" AND k.kid IN (").append(String.join(",", Collections.nCopies(kids.size(), "?"))).append(")");
                    args.addAll(kids);
                }
            }
        } else if (HEX.matcher(t).matches()) {
            w.append(" AND k.kid = ?");
            args.add(t);
        } else {
            throw ApiException.badRequest("Outil : DESK, PHONE, SERVER, AGENT, UNKNOWN ou l'identifiant de clé");
        }
    }

    /** A whole code (any typing) is turned into its reference; four characters match the end of the readable code. */
    private void deviceFilter(String device, StringBuilder w, List<Object> args, String col) {
        String d = device.trim();
        String canonical = DeviceIdentity.parseCode(d);
        if (canonical != null) {
            w.append(" AND ").append(col).append(" = ?");
            args.add(tvRef.of(canonical));
        } else if (d.replace("-", "").length() == 4 && d.replace("-", "").matches("[0-9A-Za-z]{4}")) {
            w.append(" AND ").append(col).append(" IN (SELECT tv_ref FROM act_tv WHERE device_code LIKE ?)");
            args.add("%" + d.replace("-", "").toUpperCase(java.util.Locale.ROOT));
        } else {
            throw ApiException.badRequest("Code d'appareil : le code entier ou ses 4 derniers caractères");
        }
    }

    public Map<String, Object> activation(Actor actor, String fp) {
        requireRead(actor, ActPermissions.Perm.ACT_READ, R_ACTIVATION, Map.of());
        if (fp == null || !fp.matches("[0-9a-f]{64}")) throw ApiException.badRequest("Empreinte invalide (64 chiffres hexadécimaux)");
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT k.*, t.device_code AS tv_code FROM act_key k LEFT JOIN act_tv t ON t.tv_ref = k.tv_ref WHERE k.fp = ?", fp);
        if (rows.isEmpty()) {
            readAudit.recordRead(actor, R_ACTIVATION, Map.of(), null, 0, false);   // somebody probing for fingerprints must show
            throw ApiException.notFound("Activation inconnue");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("activation", activationRow(rows.get(0), new java.util.HashMap<>(), false));
        out.put("events", events("fp = ?", fp, 200));
        out.put("alerts", jdbc.queryForList("SELECT id, type, severity, state, opened_at FROM act_alert WHERE fp = ? ORDER BY id DESC LIMIT 50", fp).stream().map(this::alertRow).toList());
        return audited(actor, R_ACTIVATION, Map.of(), fp.substring(0, 8), ((List<?>) out.get("events")).size() + 1, false, out);
    }

    private List<Map<String, Object>> events(String where, Object arg, int max) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> e : jdbc.queryForList("SELECT * FROM act_event WHERE " + where + " ORDER BY id LIMIT ?", arg, max)) out.add(eventRow(e));
        return out;
    }

    static Map<String, Object> eventRow(Map<String, Object> e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.get("id"));
        m.put("at", inst(e.get("at")));
        m.put("type", e.get("type"));
        m.put("fp8", e.get("fp") == null ? null : ((String) e.get("fp")).substring(0, 8));
        m.put("tvRef", e.get("tv_ref"));
        m.put("license", e.get("license_id"));
        m.put("kid", e.get("kid"));
        m.put("actorType", e.get("actor_type"));
        m.put("actor", e.get("actor"));
        m.put("source", e.get("source"));
        m.put("before", e.get("before_json"));
        m.put("after", e.get("after_json"));
        return m;
    }

    // ================================================================== TVs

    Map<String, Object> tvRow(Map<String, Object> r, boolean full, boolean forExport) {
        Map<String, Object> m = new LinkedHashMap<>();
        String code = (String) r.get("device_code");
        m.put("tvRef", r.get("tv_ref"));
        if (full) m.put("deviceCode", code);
        m.put("codeTail", tail(code));
        if (forExport) m.put("codeMasked", code == null ? "" : DeviceIdentity.masked(code));
        m.put("edition", r.get("edition"));
        m.put("usageTo", inst(r.get("usage_to")));
        m.put("openAllUntil", inst(r.get("open_all_until")));
        m.put("unlockUntil", inst(r.get("unlock_until")));
        m.put("trialResets", r.get("trial_resets"));
        m.put("lastReportAt", inst(r.get("last_report_at")));
        m.put("lastReportVia", r.get("last_report_via"));
        m.put("lastBtAt", inst(r.get("last_bt_at")));
        m.put("apiDevices", r.get("api_devices"));
        m.put("androidIds", r.get("android_ids"));
        m.put("appCode", r.get("app_code"));
        m.put("appName", r.get("app_name"));
        m.put("reco", r.get("reco"));
        m.put("alertsOpen", r.get("alerts_open"));
        m.put("currentFp8", r.get("current_fp") == null ? null : ((String) r.get("current_fp")).substring(0, 8));
        m.put("freshness", freshness(r.get("last_report_at")));
        Object last = r.get("last_report_at");
        long silentDays = "PRODUCTION".equals(r.get("edition")) ? policy.get(ActivationsPolicy.SILENT_PRODUCTION_DAYS) : policy.get(ActivationsPolicy.SILENT_TRIAL_DAYS);
        m.put("silent", last != null && clock.nowMs() - Times.ms(last) > Duration.ofDays(silentDays).toMillis());
        return m;
    }

    public CursorPage tvs(Actor actor, Map<String, String> f, String cursor, Integer limit) {
        requireRead(actor, ActPermissions.Perm.ACT_READ, R_TVS, params(f, limit));
        CursorPage p = queryTvs(f, cursor, limitOf(limit), false);
        return audited(actor, R_TVS, params(f, limit), null, p.items().size(), false, p);
    }

    private StringBuilder tvWhere(Map<String, String> f, List<Object> args) {
        StringBuilder w = new StringBuilder(" WHERE 1 = 1");
        if (has(f, "edition")) {
            w.append(" AND t.edition = ?");
            args.add(f.get("edition").toUpperCase(java.util.Locale.ROOT));
        }
        if (has(f, "freshness")) {
            Instant now = clock.now();
            switch (f.get("freshness")) {
                case "fresh" -> {
                    w.append(" AND t.last_report_at >= ?");
                    args.add(Timestamp.from(now.minus(Duration.ofHours(26))));
                }
                case "stale" -> {
                    w.append(" AND t.last_report_at < ? AND t.last_report_at >= ?");
                    args.add(Timestamp.from(now.minus(Duration.ofHours(26))));
                    args.add(Timestamp.from(now.minus(Duration.ofDays(7))));
                }
                case "old" -> {
                    w.append(" AND t.last_report_at < ?");
                    args.add(Timestamp.from(now.minus(Duration.ofDays(7))));
                }
                case "never" -> w.append(" AND t.last_report_at IS NULL");
                default -> throw ApiException.badRequest("Fraîcheur : fresh, stale, old ou never");
            }
        }
        if (has(f, "alerts")) w.append(f.get("alerts").equals("true") ? " AND t.alerts_open > 0" : " AND t.alerts_open = 0");
        if (has(f, "version")) {
            try {
                args.add(Integer.parseInt(f.get("version").trim()));
            } catch (NumberFormatException e) {
                throw ApiException.badRequest("Version : le code de version (entier)");
            }
            w.append(" AND t.app_code = ?");
        }
        if (has(f, "license")) {
            w.append(" AND t.tv_ref IN (SELECT tv_ref FROM act_key WHERE license_id = ?)");
            args.add(f.get("license").trim());
        }
        if (has(f, "device")) deviceFilter(f.get("device"), w, args, "t.tv_ref");
        return w;
    }

    CursorPage queryTvs(Map<String, String> f, String cursor, int limit, boolean forExport) {
        List<Object> args = new ArrayList<>();
        StringBuilder w = tvWhere(f, args);
        if (cursor != null && !cursor.isBlank()) {
            String[] c = decode(cursor);
            if (c[0].equals("-")) {
                w.append(" AND t.last_report_at IS NULL AND t.tv_ref < ?");
                args.add(c[1]);
            } else {
                Timestamp t = ts(c[0]);
                w.append(" AND (t.last_report_at < ? OR t.last_report_at IS NULL OR (t.last_report_at = ? AND t.tv_ref < ?))");
                args.add(t);
                args.add(t);
                args.add(c[1]);
            }
        }
        args.add(limit + 1);
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT t.* FROM act_tv t" + w + " ORDER BY t.last_report_at DESC, t.tv_ref DESC LIMIT ?", args.toArray());
        String next = null;
        if (rows.size() > limit) {
            rows = rows.subList(0, limit);
            Map<String, Object> last = rows.get(limit - 1);
            next = encode(last.get("last_report_at") == null ? "-" : Long.toString(Times.ms(last.get("last_report_at"))), (String) last.get("tv_ref"));
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> r : rows) items.add(tvRow(r, false, forExport));
        return new CursorPage(items, next, limit);
    }

    /** The fiche of a TV: its state, its activations, its alerts, and the whole chronology (issued, delivered, activated, commands, versions, licence changes, alerts). */
    public Map<String, Object> tv(Actor actor, String code) {
        requireRead(actor, ActPermissions.Perm.ACT_READ, R_TV, Map.of());
        String canonical = DeviceIdentity.parseCode(code);
        if (canonical == null) throw ApiException.badRequest("Code d'appareil invalide : 16 caractères au format XXXX-XXXX-XXXX-XXXX, avec son caractère de contrôle");
        String ref = tvRef.of(canonical);
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM act_tv WHERE tv_ref = ? AND device_code = ?", ref, canonical);
        if (rows.isEmpty()) {
            readAudit.recordRead(actor, R_TV, Map.of(), null, 0, false);   // somebody probing for codes must show
            throw ApiException.notFound("TV inconnue du suivi");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, Object> tv = tvRow(rows.get(0), true, false);
        out.put("tv", tv);
        out.put("freshness", tv.get("freshness"));
        Map<String, String> memo = new java.util.HashMap<>();
        List<Map<String, Object>> keys = new ArrayList<>();
        for (Map<String, Object> k : jdbc.queryForList("SELECT k.*, t.device_code AS tv_code FROM act_key k LEFT JOIN act_tv t ON t.tv_ref = k.tv_ref WHERE k.tv_ref = ? ORDER BY k.issued_at DESC, k.fp DESC LIMIT 50", ref)) {
            keys.add(activationRow(k, memo, false));
        }
        out.put("activations", keys);
        out.put("timeline", events("tv_ref = ?", ref, 200));
        out.put("alerts", jdbc.queryForList("SELECT * FROM act_alert WHERE tv_ref = ? ORDER BY id DESC LIMIT 50", ref).stream().map(this::alertRow).toList());
        Object lic = keys.isEmpty() ? null : keys.get(0).get("license");
        if (lic != null) {
            List<Map<String, Object>> l = jdbc.queryForList("SELECT l.license_id, l.kind, l.state, l.start_at, l.end_at, l.grace_days, l.seats_allowed FROM lic_license l WHERE l.license_id = ?", lic);
            if (!l.isEmpty()) out.put("license", Times.normalized(l.get(0)));
        }
        return audited(actor, R_TV, Map.of(), ref, ((List<?>) out.get("timeline")).size() + 1, false, out);
    }

    // ================================================================== alerts

    /** One alert as the API shows it (after a decision). */
    public Map<String, Object> alert(long id) {
        List<Map<String, Object>> r = jdbc.queryForList("SELECT * FROM act_alert WHERE id = ?", id);
        if (r.isEmpty()) throw ApiException.notFound("Alerte introuvable");
        return alertRow(r.get(0));
    }

    Map<String, Object> alertRow(Map<String, Object> r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.get("id"));
        m.put("type", r.get("type"));
        m.put("severity", r.get("severity"));
        m.put("fp", r.get("fp"));
        m.put("tvRef", r.get("tv_ref"));
        m.put("kid", r.get("kid"));
        m.put("license", r.get("license_id"));
        m.put("detail", r.get("detail"));
        m.put("openedAt", inst(r.get("opened_at")));
        m.put("lastSeenAt", inst(r.get("last_seen_at")));
        m.put("hits", r.get("hits"));
        m.put("state", r.get("state"));
        m.put("decidedBy", r.get("decided_by"));
        m.put("decidedAt", inst(r.get("decided_at")));
        m.put("reason", r.get("reason"));
        return m;
    }

    public CursorPage alerts(Actor actor, Map<String, String> f, String cursor, Integer limit) {
        requireRead(actor, ActPermissions.Perm.ACT_READ, R_ALERTS, params(f, limit));
        CursorPage p = queryAlerts(f, cursor, limitOf(limit));
        return audited(actor, R_ALERTS, params(f, limit), null, p.items().size(), false, p);
    }

    CursorPage queryAlerts(Map<String, String> f, String cursor, int limit) {
        List<Object> args = new ArrayList<>();
        StringBuilder w = new StringBuilder(" WHERE 1 = 1");
        if (has(f, "state")) {
            if (!Set.of("OPEN", "ACK", "CLOSED").contains(f.get("state"))) throw ApiException.badRequest("État : OPEN, ACK ou CLOSED");
            w.append(" AND state = ?");
            args.add(f.get("state"));
        }
        if (has(f, "type")) {
            w.append(" AND type = ?");
            args.add(f.get("type"));
        }
        if (cursor != null && !cursor.isBlank()) {
            String[] c = decode(cursor);
            w.append(" AND id < ?");
            try {
                args.add(Long.parseLong(c[1]));
            } catch (NumberFormatException e) {
                throw ApiException.badRequest("Curseur invalide : repartez de la première page");
            }
        }
        args.add(limit + 1);
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM act_alert" + w + " ORDER BY id DESC LIMIT ?", args.toArray());
        String next = null;
        if (rows.size() > limit) {
            rows = rows.subList(0, limit);
            next = encode("a", rows.get(limit - 1).get("id").toString());
        }
        return new CursorPage(rows.stream().map(this::alertRow).toList(), next, limit);
    }

    public void ackAlert(Actor actor, long id) {
        access.require(actor, ActPermissions.Perm.ACT_ALERT_ACK);
        alerts.ack(actor, id);
    }

    public void closeAlert(Actor actor, long id, String reason) {
        access.require(actor, ActPermissions.Perm.ACT_ALERT_DECIDE);
        alerts.close(actor, id, reason);
    }

    // ================================================================== tools, dashboard, integrity, checkpoints, reads, changes

    public List<Map<String, Object>> tools(Actor actor) {
        requireRead(actor, ActPermissions.Perm.ACT_READ, R_TOOLS, Map.of());
        List<Map<String, Object>> r = toolRows();
        return audited(actor, R_TOOLS, Map.of(), null, r.size(), false, r);
    }

    List<Map<String, Object>> toolRows() {
        Map<String, Map<String, Object>> byKid = new TreeMap<>();
        for (TrustedKeys.Key k : trusted.all()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("kid", k.kid());
            m.put("name", k.name());
            m.put("tool", tools.typeOf(k.kid()));
            m.put("scopes", k.scopes().stream().map(Enum::name).sorted().toList());
            m.put("trusted", true);
            byKid.put(k.kid(), m);
        }
        for (Map<String, Object> t : jdbc.queryForList("SELECT * FROM act_tool")) {
            String kid = (String) t.get("kid");
            Map<String, Object> m = byKid.computeIfAbsent(kid, x -> {
                Map<String, Object> n = new LinkedHashMap<>();
                n.put("kid", kid);
                n.put("name", t.get("label"));
                n.put("tool", t.get("tool"));
                n.put("trusted", false);
                return n;
            });
            m.put("lastUploadAt", inst(t.get("last_upload_at")));
            m.put("lastEntry", t.get("last_entry_n"));
            m.put("lastBatchSeq", t.get("last_batch_seq"));
            m.put("chainOk", t.get("chain_ok"));
            Object up = t.get("last_upload_at");
            long age = up == null ? Long.MAX_VALUE : clock.nowMs() - Times.ms(up);
            m.put("freshness", up == null ? "none" : age < Duration.ofDays(7).toMillis() ? "fresh" : age < Duration.ofDays(14).toMillis() ? "stale" : "old");
        }
        for (Map<String, Object> m : byKid.values()) {
            m.putIfAbsent("freshness", "none");
            List<String> gaps = new ArrayList<>();
            for (Map<String, Object> g : jdbc.queryForList("SELECT from_n, to_n FROM act_journal_gap WHERE kid = ? ORDER BY from_n", m.get("kid"))) gaps.add(g.get("from_n") + "-" + g.get("to_n"));
            m.put("gaps", gaps);
        }
        return new ArrayList<>(byKid.values());
    }

    public Map<String, Object> dashboard(Actor actor, String day) {
        requireRead(actor, ActPermissions.Perm.ACT_READ, R_DASHBOARD, day == null ? Map.of() : Map.of("day", day));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("generatedAt", clock.now());
        out.put("licensesModule", licenseProps.enabled());
        Map<String, Map<String, Integer>> byKindState = new TreeMap<>(), byToolState = new TreeMap<>();
        long[] total = {0};
        Map<String, String> memo = new java.util.HashMap<>();
        jdbc.query("SELECT kind, kid, state, COUNT(*) AS n FROM act_key GROUP BY kind, kid, state", rs -> {
            int n = rs.getInt("n");
            total[0] += n;
            byKindState.computeIfAbsent(rs.getString("kind"), x -> new TreeMap<>()).merge(rs.getString("state"), n, Integer::sum);
            byToolState.computeIfAbsent(type(memo, rs.getString("kid")), x -> new TreeMap<>()).merge(rs.getString("state"), n, Integer::sum);
        });
        Map<String, Object> act = new LinkedHashMap<>();
        act.put("total", total[0]);
        act.put("byKindState", byKindState);
        act.put("byToolState", byToolState);
        out.put("activations", act);
        Map<String, Integer> open = new TreeMap<>(), sev = new TreeMap<>();
        jdbc.query("SELECT type, severity, COUNT(*) AS n FROM act_alert WHERE state <> 'CLOSED' GROUP BY type, severity", rs -> {
            open.merge(rs.getString("type"), rs.getInt("n"), Integer::sum);
            sev.merge(rs.getString("severity"), rs.getInt("n"), Integer::sum);
        });
        out.put("alerts", Map.of("open", open, "bySeverity", sev));
        Instant now = clock.now();
        Map<String, Object> tvs = new LinkedHashMap<>();
        Map<String, Object> row = jdbc.queryForMap("SELECT COUNT(*) AS total, SUM(CASE WHEN last_report_at >= ? THEN 1 ELSE 0 END) AS fresh,"
                        + " SUM(CASE WHEN last_report_at < ? AND last_report_at >= ? THEN 1 ELSE 0 END) AS stale, SUM(CASE WHEN last_report_at < ? THEN 1 ELSE 0 END) AS old,"
                        + " SUM(CASE WHEN last_report_at IS NULL THEN 1 ELSE 0 END) AS never FROM act_tv", Timestamp.from(now.minus(Duration.ofHours(26))), Timestamp.from(now.minus(Duration.ofHours(26))),
                Timestamp.from(now.minus(Duration.ofDays(7))), Timestamp.from(now.minus(Duration.ofDays(7))));
        tvs.put("total", ((Number) row.get("total")).longValue());
        Map<String, Long> fr = new LinkedHashMap<>();
        for (String k : List.of("fresh", "stale", "old", "never")) fr.put(k, row.get(k) == null ? 0L : ((Number) row.get(k)).longValue());
        tvs.put("freshness", fr);
        Map<String, Integer> versions = new TreeMap<>();
        jdbc.query("SELECT app_code, COUNT(*) AS n FROM act_tv WHERE app_code IS NOT NULL GROUP BY app_code", rs -> {
            versions.put(Integer.toString(rs.getInt("app_code")), rs.getInt("n"));
        });
        tvs.put("versions", versions);
        out.put("tvs", tvs);
        out.put("tools", toolRows());
        Timestamp since = Timestamp.from(now.minus(Duration.ofDays(30)));
        out.put("daily", jdbc.queryForList("SELECT snap_day AS snapday, kind, tool, state, n FROM act_daily WHERE snap_day >= ? ORDER BY snap_day", java.sql.Date.valueOf(since.toInstant().atZone(ZoneOffset.UTC).toLocalDate())).stream().map(Times::normalized).toList());
        return audited(actor, R_DASHBOARD, day == null ? Map.of() : Map.of("day", day), null, 1, false, out);
    }

    /** The verification of the two chains and of the cold archive. */
    public Map<String, Object> integrity(Actor actor) {
        requireRead(actor, ActPermissions.Perm.ACT_READ, R_INTEGRITY, Map.of());
        readAudit.recordRead(actor, R_INTEGRITY, Map.of(), null, 2, false);   // first: the verification itself reads the audit chain
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("events", eventLog.verify());
        out.put("reads", readAudit.verify());
        List<Map<String, Object>> archives = new ArrayList<>();
        for (Map<String, Object> r : jdbc.queryForList("SELECT table_name, file, sha256, row_count, removed, created_at FROM act_archive ORDER BY id DESC LIMIT 100")) {
            Map<String, Object> m = new LinkedHashMap<>(r);
            m.put("created_at", inst(r.get("created_at")));
            archives.add(m);
        }
        out.put("archives", archives);
        return out;
    }

    public Map<String, Object> checkpoints(Actor actor, String from, String to) {
        Map<String, String> ap = new TreeMap<>();
        if (from != null && !from.isBlank()) ap.put("from", from);
        if (to != null && !to.isBlank()) ap.put("to", to);
        requireRead(actor, ActPermissions.Perm.ACT_EXPORT, R_CHECKPOINTS, ap);
        LocalDate f = from == null || from.isBlank() ? null : date(from), t = to == null || to.isBlank() ? null : date(to);
        List<Map<String, Object>> items = new ArrayList<>();
        for (Checkpoints.Checkpoint c : checkpoints.list(f, t)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("day", c.day().toString());
            m.put("eventLastId", c.eventLastId());
            m.put("eventHead", c.eventHead());
            m.put("readLastId", c.readLastId());
            m.put("readHead", c.readHead());
            m.put("countsJson", c.countsJson());
            m.put("sigKid", c.sigKid());
            m.put("signature", c.signature());
            items.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", items);
        if (checkpoints.publicKey() != null) out.put("publicKey", checkpoints.publicKey());   // to keep OUTSIDE the server
        return audited(actor, R_CHECKPOINTS, ap, null, items.size(), true, out);
    }

    private static LocalDate date(String s) {
        try {
            return LocalDate.parse(s.trim());
        } catch (RuntimeException e) {
            throw ApiException.badRequest("Date : AAAA-MM-JJ");
        }
    }

    /** The audit of the reads, newest first (OWNER with the second factor: it tells who looked at what). */
    public CursorPage readAudit(Actor actor, String cursor, Integer limit) {
        requireRead(actor, ActPermissions.Perm.ACT_EXPORT, R_READ_AUDIT, params(null, limit));
        int lim = limitOf(limit);
        readAudit.recordRead(actor, R_READ_AUDIT, params(null, limit), null, lim, false);   // first: listing the audit reads the audit table (rows = the page size asked)
        List<Object> args = new ArrayList<>();
        String w = "";
        if (cursor != null && !cursor.isBlank()) {
            w = " WHERE id < ?";
            try {
                args.add(Long.parseLong(decode(cursor)[1]));
            } catch (NumberFormatException e) {
                throw ApiException.badRequest("Curseur invalide : repartez de la première page");
            }
        }
        args.add(lim + 1);
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id, at, actor, role, channel, route, params, target, rows_rendered, export FROM adm_read_audit" + w + " ORDER BY id DESC LIMIT ?", args.toArray());
        String next = null;
        if (rows.size() > lim) {
            rows = rows.subList(0, lim);
            next = encode("r", rows.get(lim - 1).get("id").toString());
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> m = new LinkedHashMap<>(r);
            m.put("at", inst(r.get("at")));
            m.put("rows", m.remove("rows_rendered"));
            items.add(m);
        }
        return new CursorPage(items, next, lim);
    }

    /** Events after an id (at most 200), for the long poll of the controller. */
    public Map<String, Object> changes(Actor actor, long after) {
        requireRead(actor, ActPermissions.Perm.ACT_READ, R_CHANGES, Map.of("after", Long.toString(after)));
        return changesSince(after);
    }

    Map<String, Object> changesSince(long after) {
        List<Map<String, Object>> ev = new ArrayList<>();
        long last = after;
        for (Map<String, Object> e : jdbc.queryForList("SELECT * FROM act_event WHERE id > ? ORDER BY id LIMIT 200", after)) {
            ev.add(eventRow(e));
            last = ((Number) e.get("id")).longValue();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("events", ev);
        out.put("last", last);
        return out;
    }

    boolean hasChangesSince(long after) { return eventLog.lastId() > after; }

    // ================================================================== for the export

    /** The most rows an export of this kind can hold without counting the history: a bound, exact for the chains (their ids are contiguous). */
    long exportBound(String what, Map<String, String> f) {
        return switch (what) {
            case "activations" -> {
                List<Object> args = new ArrayList<>();
                yield jdbc.queryForObject("SELECT COUNT(*) FROM act_key k" + activationWhere(f, args), Long.class, args.toArray());
            }
            case "tvs" -> {
                List<Object> args = new ArrayList<>();
                yield jdbc.queryForObject("SELECT COUNT(*) FROM act_tv t" + tvWhere(f, args), Long.class, args.toArray());
            }
            case "alerts" -> countOf("SELECT COUNT(*) FROM act_alert");
            case "events" -> Math.max(0, eventLog.lastId() - Math.max(longOf(f.get("after")), (Long) Chains.anchor(jdbc, "act_event")[0]));
            default -> Math.max(0, readAudit.lastId() - Math.max(longOf(f.get("after")), (Long) Chains.anchor(jdbc, "adm_read_audit")[0]));
        };
    }

    private long countOf(String sql) { return jdbc.queryForObject(sql, Long.class); }

    static long longOf(String s) {
        try {
            return s == null || s.isBlank() ? 0 : Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            throw ApiException.badRequest("Paramètre numérique invalide");
        }
    }

    /** Feeds the rows of an export to a sink, page after page (never the whole table in memory). */
    void streamRows(String what, Map<String, String> f, java.util.function.Consumer<Map<String, Object>> sink) {
        switch (what) {
            case "activations" -> {
                String cursor = null;
                do {
                    CursorPage p = queryActivations(f, cursor, 1000, true);
                    p.items().forEach(i -> sink.accept(castMap(i)));
                    cursor = p.nextCursor();
                } while (cursor != null);
            }
            case "tvs" -> {
                String cursor = null;
                do {
                    CursorPage p = queryTvs(f, cursor, 1000, true);
                    p.items().forEach(i -> sink.accept(castMap(i)));
                    cursor = p.nextCursor();
                } while (cursor != null);
            }
            case "alerts" -> {
                String cursor = null;
                do {
                    CursorPage p = queryAlerts(f, cursor, 1000);
                    p.items().forEach(i -> sink.accept(castMap(i)));
                    cursor = p.nextCursor();
                } while (cursor != null);
            }
            default -> {
                String table = what.equals("events") ? "act_event" : "adm_read_audit";
                long after = longOf(f.get("after"));
                while (true) {
                    List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM " + table + " WHERE id > ? ORDER BY id LIMIT 1000", after);
                    if (rows.isEmpty()) break;
                    for (Map<String, Object> r : rows) {
                        sink.accept(Jsonl.row(table, r));
                        after = ((Number) r.get("id")).longValue();
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object o) { return (Map<String, Object>) o; }
}
