package castbridge.server.activations;

import castbridge.server.activations.AlertService.Type;
import castbridge.server.licenses.LicenseKeyring;
import castbridge.server.licenses.TrustedKeys;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * « Issued vs seen » reconciliation (design 3.5). Runs for the objects touched by each arrival (journal, registry, report) and every night at 03:50 Douala for everything.
 * It computes the conditions that hold NOW, opens (or counts) the alert of each, and closes by itself the alerts whose condition is gone. It NEVER acts on a licence, a seat
 * or a key: the alerts are soft, the administrator decides.
 */
@Service
public class Reconciler {
    private static final Logger log = LoggerFactory.getLogger(Reconciler.class);

    /** The objects an arrival touched; a null set is not restricted, {@link #all()} is everything. */
    public record Scope(Set<String> fps, Set<String> tvRefs, Set<String> kids, Set<String> licenses, boolean everything) {
        public static Scope all() { return new Scope(Set.of(), Set.of(), Set.of(), Set.of(), true); }

        public static Scope of(Collection<String> fps, Collection<String> tvRefs, Collection<String> kids, Collection<String> licenses) {
            return new Scope(clean(fps), clean(tvRefs), clean(kids), clean(licenses), false);
        }

        private static Set<String> clean(Collection<String> c) {
            Set<String> s = new TreeSet<>();
            if (c != null) for (String x : c) if (x != null && !x.isBlank()) s.add(x);
            return s;
        }
    }

    public record Summary(int conditions, int closed) {}

    record Cond(Type type, String fp, String tvRef, String kid, String license, String detail, String evidence) {
        String key() { return AlertService.openKey(type, fp, tvRef, kid, license); }
    }

    /** The dimensions each reconcilable alert is scoped by (the SAME for finding its conditions and for choosing which alerts may close). */
    private static final Map<Type, List<String>> DIMS = new EnumMap<>(Type.class);

    static {
        DIMS.put(Type.UNDECLARED, List.of("fp", "tv", "kid"));
        DIMS.put(Type.JOURNAL_GAP, List.of("kid"));
        DIMS.put(Type.ENDED_IN_USE, List.of("fp", "tv"));
        DIMS.put(Type.LICENSE_PENDING, List.of("fp", "tv", "license"));
        DIMS.put(Type.OVER_SEATS, List.of("license"));
        DIMS.put(Type.UNDECLARED_COMMAND, List.of("tv"));
        DIMS.put(Type.TOOL_STALE, List.of("kid"));
    }

    private final JdbcTemplate jdbc;
    private final ActClock clock;
    private final ActivationsPolicy policy;
    private final AlertService alerts;
    private final Inventory inventory;
    private final EventLog eventLog;
    private final TrustedKeys trusted;
    private final LicenseKeyring keyring;
    private final ActivationsProperties props;
    private final org.springframework.transaction.support.TransactionTemplate tx;

    public Reconciler(JdbcTemplate jdbc, ActClock clock, ActivationsPolicy policy, AlertService alerts, Inventory inventory, EventLog eventLog, TrustedKeys trusted, LicenseKeyring keyring,
                      ActivationsProperties props, org.springframework.transaction.support.TransactionTemplate tx) {
        this.tx = tx;
        this.jdbc = jdbc;
        this.clock = clock;
        this.policy = policy;
        this.alerts = alerts;
        this.inventory = inventory;
        this.eventLog = eventLog;
        this.trusted = trusted;
        this.keyring = keyring;
        this.props = props;
    }

    /** Every night at 03:50 Douala (after the licence jobs of 03:20 and 03:40). */
    @Scheduled(cron = "0 50 3 * * *", zone = "Africa/Douala")
    void nightly() {
        if (!props.enabled()) return;
        try {
            reconcileAll();
        } catch (RuntimeException e) {
            log.error("activation reconciliation failed: {}", e.getClass().getSimpleName());
        }
    }

    /** Rows per transaction in the nightly job (audit M2): the head lock of the chain, taken by the first event of a transaction, is held until it ends. */
    static final int BATCH = 200;

    /**
     * Everything: states first (what time changed: a window closed, a usage ceiling ended), then the conditions. Audit M2: NOT one long transaction (its first event would take
     * the head lock of the chain for the whole job, and every report and journal would wait with a pooled connection open): one transaction per batch of {@link #BATCH}, the
     * states found by targeted indexed queries. Idempotent: a run interrupted half way is finished by the next one.
     */
    public Summary reconcileAll() {
        Instant now = clock.now();
        refreshWhere("state = 'EMISE' AND expires_at < ?", Timestamp.from(now));          // window closed: EXPIRED_UNUSED (or seen meanwhile)
        refreshWhere("state = 'ACTIVATED' AND usage_to < ?", Timestamp.from(now));        // usage ceiling over: ENDED
        Summary s = syncInBatches();
        tx.executeWithoutResult(st -> {
            jdbc.update("UPDATE act_tv SET alerts_open = (SELECT COUNT(*) FROM act_alert a WHERE a.tv_ref = act_tv.tv_ref AND a.state <> 'CLOSED')");
            jdbc.update("UPDATE act_tv SET reco = CASE WHEN last_report_at IS NULL AND last_bt_at IS NULL THEN 'NEVER' WHEN alerts_open > 0 THEN 'GAP' ELSE 'OK' END");
            eventLog.append(new EventLog.NewEvent("RECONCILED", now.toEpochMilli(), null, null, null, null, "JOB", "reconciler", "RECONCILE", null,
                    "{\"conditions\":" + s.conditions() + ",\"closed\":" + s.closed() + "}", "R:" + now.atZone(ZoneOffset.UTC).toLocalDate()));
        });
        return s;
    }

    private void refreshWhere(String where, Object arg) {
        String after = "";
        while (true) {
            final String from = after;
            List<String> fps = jdbc.queryForList("SELECT fp FROM act_key WHERE " + where + " AND fp > ? ORDER BY fp LIMIT " + BATCH, String.class, arg, from);
            if (fps.isEmpty()) break;
            tx.executeWithoutResult(st -> fps.forEach(inventory::refreshState));
            after = fps.get(fps.size() - 1);
        }
    }

    /** The conditions are computed once, then raised and the stale alerts closed batch by batch (each batch its own transaction). */
    private Summary syncInBatches() {
        Instant now = clock.now();
        List<Cond> conds = conditions(Scope.all(), now);
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < conds.size(); i += BATCH) {
            List<Cond> part = conds.subList(i, Math.min(conds.size(), i + BATCH));
            tx.executeWithoutResult(st -> part.forEach(c -> alerts.raise(c.type(), c.fp(), c.tvRef(), c.kid(), c.license(), c.detail(), c.evidence())));
        }
        conds.forEach(c -> keys.add(c.key()));
        int[] closed = {0};
        for (Type t : DIMS.keySet()) {
            List<Long> stale = new ArrayList<>();
            for (Map<String, Object> a : jdbc.queryForList("SELECT * FROM act_alert WHERE state <> 'CLOSED' AND type = ?", t.name())) {
                String k = AlertService.openKey(t, (String) a.get("fp"), (String) a.get("tv_ref"), (String) a.get("kid"), (String) a.get("license_id"));
                if (!keys.contains(k)) stale.add(((Number) a.get("id")).longValue());
            }
            for (int i = 0; i < stale.size(); i += BATCH) {
                List<Long> part = stale.subList(i, Math.min(stale.size(), i + BATCH));
                tx.executeWithoutResult(st -> part.forEach(id -> alerts.closeAuto(id, "La condition n'est plus réunie")));
                closed[0] += part.size();
            }
        }
        return new Summary(conds.size(), closed[0]);
    }

    /** The objects an arrival touched. */
    @Transactional
    public Summary reconcile(Scope scope) {
        for (String fp : scope.fps()) inventory.refreshState(fp);
        for (String tv : scope.tvRefs()) inventory.refreshTvKeys(tv);
        Summary s = sync(scope);
        for (String tv : scope.tvRefs()) alerts.refreshTv(tv);
        return s;
    }

    private Summary sync(Scope scope) {
        Instant now = clock.now();
        List<Cond> conds = conditions(scope, now);
        Set<String> keys = new HashSet<>();
        for (Cond c : conds) {
            alerts.raise(c.type(), c.fp(), c.tvRef(), c.kid(), c.license(), c.detail(), c.evidence());
            keys.add(c.key());
        }
        int closed = 0;
        for (Type t : DIMS.keySet()) {
            List<Object> args = new ArrayList<>();
            args.add(t.name());
            String where = scopeSql(scope, DIMS.get(t), Map.of("fp", "fp", "tv", "tv_ref", "kid", "kid", "license", "license_id"), args);
            for (Map<String, Object> a : jdbc.queryForList("SELECT * FROM act_alert WHERE state <> 'CLOSED' AND type = ?" + where, args.toArray())) {
                String k = AlertService.openKey(t, (String) a.get("fp"), (String) a.get("tv_ref"), (String) a.get("kid"), (String) a.get("license_id"));
                if (!keys.contains(k)) {
                    alerts.closeAuto(((Number) a.get("id")).longValue(), "La condition n'est plus réunie");
                    closed++;
                }
            }
        }
        return new Summary(conds.size(), closed);
    }

    /** " AND (col IN (...) OR ...)" for the dimensions of a condition that the scope restricts; " AND 1 = 0" when the scope has none of them; "" for everything. */
    private static String scopeSql(Scope s, List<String> dims, Map<String, String> cols, List<Object> args) {
        if (s.everything()) return "";
        List<String> parts = new ArrayList<>();
        for (String d : dims) {
            Set<String> values = switch (d) {
                case "fp" -> s.fps();
                case "tv" -> s.tvRefs();
                case "kid" -> s.kids();
                default -> s.licenses();
            };
            if (values.isEmpty()) continue;
            parts.add(cols.get(d) + " IN (" + String.join(",", java.util.Collections.nCopies(values.size(), "?")) + ")");
            args.addAll(values);
        }
        return parts.isEmpty() ? " AND 1 = 0" : " AND (" + String.join(" OR ", parts) + ")";
    }

    private static Timestamp ago(Instant now, Duration d) { return Timestamp.from(now.minus(d)); }

    private List<Cond> conditions(Scope scope, Instant now) {
        List<Cond> out = new ArrayList<>();
        Map<String, String> k = Map.of("fp", "k.fp", "tv", "k.tv_ref", "kid", "k.kid", "license", "k.license_id");

        // UNDECLARED: seen on a TV, declared by no journal, no registry, no server issuance, after the grace period
        List<Object> a = new ArrayList<>();
        a.add(ago(now, Duration.ofHours(policy.get(ActivationsPolicy.UNDECLARED_GRACE_HOURS))));
        String sc = scopeSql(scope, DIMS.get(Type.UNDECLARED), k, a);
        jdbc.query("SELECT k.fp, k.tv_ref, k.kid FROM act_key k WHERE k.flags LIKE '%,seen_on_tv,%' AND k.flags NOT LIKE '%,declared_journal,%' AND k.flags NOT LIKE '%,declared_registry,%'"
                + " AND k.flags NOT LIKE '%,server_issued,%' AND k.first_seen_tv_at <= ?" + sc, rs -> {
            out.add(new Cond(Type.UNDECLARED, rs.getString("fp"), rs.getString("tv_ref"), rs.getString("kid"), null, "Activation vue sur une TV, déclarée par aucun journal, aucun registre, aucune émission du serveur", "seen"));
        }, a.toArray());

        // JOURNAL_GAP: entries of a tool never received, after the grace period
        a = new ArrayList<>();
        a.add(ago(now, Duration.ofDays(policy.get(ActivationsPolicy.JOURNAL_GAP_DAYS))));
        sc = scopeSql(scope, DIMS.get(Type.JOURNAL_GAP), Map.of("kid", "g.kid"), a);
        Map<String, List<String>> gaps = new LinkedHashMap<>();
        jdbc.query("SELECT g.kid, g.from_n, g.to_n FROM act_journal_gap g WHERE g.opened_at <= ?" + sc + " ORDER BY g.kid, g.from_n", rs -> {
            gaps.computeIfAbsent(rs.getString("kid"), x -> new ArrayList<>()).add(rs.getLong("from_n") + "-" + rs.getLong("to_n"));
        }, a.toArray());
        gaps.forEach((kid, ranges) -> out.add(new Cond(Type.JOURNAL_GAP, null, null, kid, null, "Entrées " + String.join(", ", ranges) + " manquantes (outil " + shortKid(kid) + ")", String.join(",", ranges))));

        // ENDED_IN_USE: a TV that says it is still in a trial or production edition after the end of the usage ceiling (+ grace), or that still runs a revoked activation
        a = new ArrayList<>();
        a.add(ago(now, Duration.ofDays(policy.get(ActivationsPolicy.ENDED_GRACE_DAYS))));
        sc = scopeSql(scope, DIMS.get(Type.ENDED_IN_USE), Map.of("fp", "t.current_fp", "tv", "t.tv_ref"), a);
        jdbc.query("SELECT t.tv_ref, t.current_fp FROM act_tv t WHERE t.edition IN ('TRIAL','PRODUCTION') AND t.usage_to IS NOT NULL AND t.usage_to <= ?" + sc, rs -> {
            out.add(new Cond(Type.ENDED_IN_USE, rs.getString("current_fp"), rs.getString("tv_ref"), null, null, "La TV se dit encore activée après la fin du plafond d'usage", "ended"));
        }, a.toArray());
        a = new ArrayList<>();
        sc = scopeSql(scope, DIMS.get(Type.ENDED_IN_USE), k, a);
        long graceMs = Duration.ofDays(policy.get(ActivationsPolicy.ENDED_GRACE_DAYS)).toMillis();
        jdbc.query("SELECT k.fp, k.tv_ref, k.revoked_at, k.last_seen_tv_at FROM act_key k WHERE k.revoked_at IS NOT NULL AND k.last_seen_tv_at IS NOT NULL" + sc, rs -> {
            if (rs.getTimestamp("last_seen_tv_at").getTime() >= rs.getTimestamp("revoked_at").getTime() + graceMs) {
                out.add(new Cond(Type.ENDED_IN_USE, rs.getString("fp"), rs.getString("tv_ref"), null, null, "Activation révoquée encore portée par une TV après le délai de diffusion de la révocation", "revoked"));
            }
        }, a.toArray());

        // LICENSE_PENDING: a production activation in use whose licence or seat the server does not know (yet)
        a = new ArrayList<>();
        a.add(ago(now, Duration.ofDays(policy.get(ActivationsPolicy.LICENSE_PENDING_DAYS))));
        sc = scopeSql(scope, DIMS.get(Type.LICENSE_PENDING), k, a);
        jdbc.query("SELECT k.fp, k.tv_ref, k.license_id FROM act_key k WHERE k.kind = 'PRODUCTION' AND k.state = 'ACTIVATED' AND COALESCE(k.first_seen_tv_at, k.delivered_bt_at) <= ?"
                + " AND NOT EXISTS (SELECT 1 FROM lic_seat s JOIN lic_license l ON l.id = s.license_pk WHERE l.license_id = k.license_id AND s.seat_id = k.seat_id AND s.state = 'ACTIVE')" + sc, rs -> {
            out.add(new Cond(Type.LICENSE_PENDING, rs.getString("fp"), rs.getString("tv_ref"), null, rs.getString("license_id"), "Production sans licence ni poste connus du serveur : à enregistrer", "pending"));
        }, a.toArray());

        // OVER_SEATS: more TVs seen with a licence than seats allowed
        a = new ArrayList<>();
        sc = scopeSql(scope, DIMS.get(Type.OVER_SEATS), k, a);
        jdbc.query("SELECT l.license_id, l.seats_allowed, COUNT(DISTINCT k.tv_ref) AS n FROM act_key k JOIN lic_license l ON l.license_id = k.license_id WHERE k.kind = 'PRODUCTION' AND k.state = 'ACTIVATED'"
                + " AND k.tv_ref IS NOT NULL" + sc + " GROUP BY l.license_id, l.seats_allowed HAVING COUNT(DISTINCT k.tv_ref) > l.seats_allowed", rs -> {
            out.add(new Cond(Type.OVER_SEATS, null, null, null, rs.getString("license_id"), rs.getInt("n") + " TV pour " + rs.getInt("seats_allowed") + " postes permis", rs.getString("n")));
        }, a.toArray());

        // UNDECLARED_COMMAND: a command reported by a TV that no tool journal declares
        a = new ArrayList<>();
        a.add(ago(now, Duration.ofHours(policy.get(ActivationsPolicy.UNDECLARED_GRACE_HOURS))));
        sc = scopeSql(scope, DIMS.get(Type.UNDECLARED_COMMAND), Map.of("tv", "c.tv_ref"), a);
        Map<String, List<String>> cmds = new LinkedHashMap<>();
        jdbc.query("SELECT c.tv_ref, c.power, c.challenge FROM act_command c WHERE c.reported = TRUE AND c.declared = FALSE AND c.reported_at <= ?" + sc + " ORDER BY c.tv_ref, c.challenge", rs -> {
            cmds.computeIfAbsent(rs.getString("tv_ref"), x -> new ArrayList<>()).add(rs.getString("power") + ":" + rs.getString("challenge"));
        }, a.toArray());
        cmds.forEach((tv, l) -> out.add(new Cond(Type.UNDECLARED_COMMAND, null, tv, null, null, "Commande rapportée par la TV sans entrée de journal correspondante : " + String.join(", ", l), String.join(",", l))));

        // TOOL_STALE: a tool whose activations are seen on TVs but whose journal was not uploaded for a long time
        a = new ArrayList<>();
        sc = scopeSql(scope, DIMS.get(Type.TOOL_STALE), k, a);
        long staleMs = Duration.ofDays(policy.get(ActivationsPolicy.TOOL_STALE_DAYS)).toMillis();
        String serverKid = keyring.kid();
        for (String kid : jdbc.queryForList("SELECT DISTINCT k.kid FROM act_key k WHERE k.kid IS NOT NULL AND (k.flags LIKE '%,seen_on_tv,%' OR k.flags LIKE '%,delivered_bt,%')" + sc, String.class, a.toArray())) {
            if (trusted.find(kid) == null || kid.equals(serverKid)) continue;
            List<Timestamp> last = jdbc.queryForList("SELECT last_upload_at FROM act_tool WHERE kid = ?", Timestamp.class, kid);
            if (last.isEmpty() || last.get(0) == null || last.get(0).getTime() <= now.toEpochMilli() - staleMs) {
                out.add(new Cond(Type.TOOL_STALE, null, null, kid, null, "L'outil " + shortKid(kid) + " a émis des activations vues sur des TV mais n'a remonté aucun journal depuis "
                        + policy.get(ActivationsPolicy.TOOL_STALE_DAYS) + " jours", "stale"));
            }
        }
        return out;
    }

    private static String shortKid(String kid) { return kid.length() > 8 ? kid.substring(0, 8) + "…" : kid; }
}
