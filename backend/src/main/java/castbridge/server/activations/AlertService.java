package castbridge.server.activations;

import castbridge.server.licenses.Actor;
import castbridge.server.licenses.Hashing;
import castbridge.server.web.ApiException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * SOFT alerts: they inform, the administrator decides (acknowledge, or close with a reason). Never a revocation, a suspension or a seat release by themselves. One alert is
 * open per object ({@code open_key} unique while not closed): a repetition counts a hit only when it brings new evidence; closing frees the key, so that the same condition
 * later opens a NEW alert.
 */
@Service
public class AlertService {
    /** The codes of design 3.5 (+ BAD_TOKEN: a token that is malformed or whose signature is false, ignored). {@code reconcilable}: closes by itself when the condition is gone. */
    public enum Type {
        UNDECLARED("high", true), JOURNAL_GAP("high", true), JOURNAL_BROKEN("critical", false), CLONE("high", false), OUT_OF_WINDOW("high", false), ENDED_IN_USE("medium", true),
        UNKNOWN_KEY("high", false), BAD_TOKEN("high", false), LICENSE_PENDING("low", true), OVER_SEATS("medium", true), UNDECLARED_COMMAND("high", true), TOOL_STALE("medium", true);

        private final String severity;
        private final boolean reconcilable;

        Type(String severity, boolean reconcilable) {
            this.severity = severity;
            this.reconcilable = reconcilable;
        }

        public String severity() { return severity; }

        public boolean reconcilable() { return reconcilable; }
    }

    private final JdbcTemplate jdbc;
    private final ActClock clock;
    private final EventLog log;

    public AlertService(JdbcTemplate jdbc, ActClock clock, EventLog log) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.log = log;
    }

    static String openKey(Type t, String fp, String tvRef, String kid, String licenseId) {
        return Hashing.sha256Hex(String.join("|", t.name(), nz(fp), nz(tvRef), nz(kid), nz(licenseId)));
    }

    private static String nz(String s) { return s == null ? "" : s; }

    /** Opens the alert of this object, or counts a hit on the one already open (only if {@code evidence} differs from the last one). Returns its id. */
    @Transactional
    public long raise(Type type, String fp, String tvRef, String kid, String licenseId, String detail, String evidence) {
        String key = openKey(type, fp, tvRef, kid, licenseId);
        Timestamp now = Timestamp.from(clock.now());
        String det = Chains.clip(detail, 300), ev = Chains.clip(evidence, 64);
        List<Map<String, Object>> open = jdbc.queryForList("SELECT id, last_evidence FROM act_alert WHERE open_key = ?", key);
        if (!open.isEmpty()) {
            long id = ((Number) open.get(0).get("id")).longValue();
            if (ev != null && !ev.equals(open.get(0).get("last_evidence"))) {
                jdbc.update("UPDATE act_alert SET hits = hits + 1, last_seen_at = ?, last_evidence = ?, detail = ? WHERE id = ?", now, ev, det, id);
            }
            return id;
        }
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        try {
            jdbc.update(con -> {
                var ps = con.prepareStatement("INSERT INTO act_alert (type, severity, fp, tv_ref, kid, license_id, detail, opened_at, last_seen_at, hits, state, open_key, last_evidence) VALUES (?,?,?,?,?,?,?,?,?,1,'OPEN',?,?)",
                        Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, type.name());
                ps.setString(2, type.severity());
                ps.setString(3, fp);
                ps.setString(4, tvRef);
                ps.setString(5, kid);
                ps.setString(6, licenseId);
                ps.setString(7, det);
                ps.setTimestamp(8, now);
                ps.setTimestamp(9, now);
                ps.setString(10, key);
                ps.setString(11, ev);
                return ps;
            }, keys);
        } catch (DuplicateKeyException e) {
            return jdbc.queryForObject("SELECT id FROM act_alert WHERE open_key = ?", Long.class, key);
        }
        long id = keys.getKey().longValue();
        log.append(new EventLog.NewEvent("ALERT_OPENED", clock.nowMs(), fp, tvRef, licenseId, kid, "JOB", "reconciler", "RECONCILE", null,
                "{\"alert\":" + id + ",\"type\":\"" + type.name() + "\",\"severity\":\"" + type.severity() + "\"}", "AL:" + id));
        refreshTv(tvRef);
        return id;
    }

    /** Acknowledge (no reason needed). Idempotent on an acknowledged alert; 409 on a closed one. */
    @Transactional
    public void ack(Actor actor, long id) {
        Map<String, Object> a = load(id);
        String state = (String) a.get("state");
        if (state.equals("CLOSED")) throw ApiException.conflict("Cette alerte est déjà classée");
        if (state.equals("ACK")) return;
        jdbc.update("UPDATE act_alert SET state = 'ACK', decided_by = ?, decided_at = ? WHERE id = ?", Chains.clip(actor.name(), 64), Timestamp.from(clock.now()), id);
        decided(a, "ack", actor.name(), "ADMIN", null);
    }

    /** Closes with a reason (mandatory), frees the key. */
    @Transactional
    public void close(Actor actor, long id, String reason) {
        if (reason == null || reason.isBlank()) throw ApiException.badRequest("Le motif est obligatoire pour classer une alerte");
        Map<String, Object> a = load(id);
        if (a.get("state").equals("CLOSED")) throw ApiException.conflict("Cette alerte est déjà classée");
        closeNow(a, Chains.clip(reason, 500), actor.name(), "ADMIN");
    }

    /** The reconciler closes an alert whose condition is gone. */
    @Transactional
    public void closeAuto(long id, String reason) {
        Map<String, Object> a = load(id);
        if (!a.get("state").equals("CLOSED")) closeNow(a, reason, "reconciler", "JOB");
    }

    private void closeNow(Map<String, Object> a, String reason, String by, String actorType) {
        long id = ((Number) a.get("id")).longValue();
        jdbc.update("UPDATE act_alert SET state = 'CLOSED', decided_by = ?, decided_at = ?, reason = ?, open_key = NULL WHERE id = ?", Chains.clip(by, 64), Timestamp.from(clock.now()), reason, id);
        decided(a, "close", by, actorType, reason);
        refreshTv((String) a.get("tv_ref"));
    }

    private void decided(Map<String, Object> a, String what, String by, String actorType, String reason) {
        long id = ((Number) a.get("id")).longValue();
        log.append(new EventLog.NewEvent("ALERT_DECIDED", clock.nowMs(), (String) a.get("fp"), (String) a.get("tv_ref"), (String) a.get("license_id"), (String) a.get("kid"), actorType, by, actorType.equals("JOB") ? "RECONCILE" : "ADMIN",
                "{\"state\":\"" + a.get("state") + "\"}", "{\"alert\":" + id + ",\"decision\":\"" + what + "\"" + (reason == null ? "" : ",\"reason\":\"" + json(reason) + "\"") + "}", "AD:" + id + ":" + what));
    }

    private static String json(String s) { return s.replace("\\", "\\\\").replace("\"", "'"); }

    private Map<String, Object> load(long id) {
        List<Map<String, Object>> r = jdbc.queryForList("SELECT * FROM act_alert WHERE id = ?", id);
        if (r.isEmpty()) throw ApiException.notFound("Alerte introuvable");
        return r.get(0);
    }

    /** Keeps the counters of a TV (open alerts) and its reconciliation state (OK, GAP, NEVER) in line. */
    @Transactional
    public void refreshTv(String tvRef) {
        if (tvRef == null) return;
        jdbc.update("UPDATE act_tv SET alerts_open = (SELECT COUNT(*) FROM act_alert a WHERE a.tv_ref = act_tv.tv_ref AND a.state <> 'CLOSED') WHERE tv_ref = ?", tvRef);
        jdbc.update("UPDATE act_tv SET reco = CASE WHEN last_report_at IS NULL AND last_bt_at IS NULL THEN 'NEVER' WHEN alerts_open > 0 THEN 'GAP' ELSE 'OK' END WHERE tv_ref = ?", tvRef);
    }
}
