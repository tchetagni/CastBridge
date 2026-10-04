package castbridge.server.activations;

import castbridge.server.licenses.Actor;
import castbridge.server.web.ApiException;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The delays and cadences of the module (table act_policy: changeable within their bounds without a delivery, each change is a POLICY_CHANGED event) and the rate limits
 * of design 6.5, counted with the clock of the module. In memory: one server instance.
 */
@Component
public class ActivationsPolicy {
    public static final String UNDECLARED_GRACE_HOURS = "undeclared_grace_hours", JOURNAL_GAP_DAYS = "journal_gap_days", TOOL_STALE_DAYS = "tool_stale_days",
            SILENT_PRODUCTION_DAYS = "silent_production_days", SILENT_TRIAL_DAYS = "silent_trial_days", REPORT_NEXT_HOURS = "report_next_hours", ENDED_GRACE_DAYS = "ended_grace_days",
            LICENSE_PENDING_DAYS = "license_pending_days";

    private final JdbcTemplate jdbc;
    private final ActClock clock;
    private final EventLog log;
    private final Map<String, ArrayDeque<Long>> hits = new HashMap<>();

    public ActivationsPolicy(JdbcTemplate jdbc, ActClock clock, EventLog log) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.log = log;
    }

    public int get(String name) {
        Integer v = jdbc.queryForObject("SELECT val FROM act_policy WHERE name = ?", Integer.class, name);
        if (v == null) throw new IllegalStateException("policy " + name);
        return v;
    }

    public List<Map<String, Object>> all() { return jdbc.queryForList("SELECT name, val, min_val, max_val, updated_at, updated_by FROM act_policy ORDER BY name"); }

    @Transactional
    public void set(Actor actor, String name, int value) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT val, min_val, max_val FROM act_policy WHERE name = ?", name);
        if (rows.isEmpty()) throw ApiException.notFound("Réglage inconnu");
        int min = ((Number) rows.get(0).get("min_val")).intValue(), max = ((Number) rows.get(0).get("max_val")).intValue(), old = ((Number) rows.get(0).get("val")).intValue();
        if (value < min || value > max) throw ApiException.badRequest("Valeur hors bornes (" + min + " à " + max + ")");
        jdbc.update("UPDATE act_policy SET val = ?, updated_at = ?, updated_by = ? WHERE name = ?", value, Timestamp.from(clock.now()), Chains.clip(actor.name(), 64), name);
        log.append(new EventLog.NewEvent("POLICY_CHANGED", clock.nowMs(), null, null, null, null, "ADMIN", actor.name(), "ADMIN", "{\"" + name + "\":" + old + "}", "{\"" + name + "\":" + value + "}",
                "P:" + name + ":" + clock.nowMs() + ":" + value));
    }

    // ---- rate limits (sliding window, counted with the clock of the module)

    /** True and counted if fewer than {@code max} events happened in the last {@code window}; false (nothing counted) otherwise. */
    public synchronized boolean tryAcquire(String bucket, int max, Duration window) {
        long now = clock.nowMs();
        ArrayDeque<Long> q = hits.computeIfAbsent(bucket, k -> new ArrayDeque<>());
        q.removeIf(t -> t <= now - window.toMillis());
        long counted = q.stream().filter(t -> t <= now).count();
        if (counted >= max) return false;
        q.add(now);
        return true;
    }

    /** 429 when the bucket is full. */
    public void limit(String bucket, int max, Duration window, String what) {
        if (!tryAcquire(bucket, max, window)) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Trop de requêtes : " + what + " (" + max + " par " + describe(window) + ")");
    }

    private static String describe(Duration d) { return d.toHours() >= 1 ? d.toHours() + " h" : d.toMinutes() + " min"; }

    /** Test seam: forgets every counter. */
    public synchronized void resetLimits() { hits.clear(); }
}
