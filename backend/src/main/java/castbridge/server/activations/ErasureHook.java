package castbridge.server.activations;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The right to erasure of the licence module anonymizes the seats of a client ({@code lic_seat.anonymized}). The licence module publishes no event for it, so this job
 * compares (read only) the anonymized seats with the TVs the tracker holds under them: for each, the readable device code is wiped from {@code act_tv}, wiped from the
 * signed journals that carried it, and an ERASED event is written. The history designates TVs by {@code tv_ref} only, so the chain stays intact and verifiable. Every 5 min.
 *
 * <p>Limit: a TV seen only by a trial (no seat in any licence) cannot be linked to a client, so it is not erased by this job.
 */
@Component
public class ErasureHook {
    private static final Logger log = LoggerFactory.getLogger(ErasureHook.class);

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final ActClock clock;
    private final EventLog eventLog;
    private final TvRef tvRef;
    private final ActivationsProperties props;

    public ErasureHook(JdbcTemplate jdbc, TransactionTemplate tx, ActClock clock, EventLog eventLog, TvRef tvRef, ActivationsProperties props) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.clock = clock;
        this.eventLog = eventLog;
        this.tvRef = tvRef;
        this.props = props;
    }

    @Scheduled(fixedDelay = 300_000, initialDelay = 50_000)
    void scheduled() {
        if (!props.enabled() || !tvRef.available()) return;
        try {
            runOnce();
        } catch (RuntimeException e) {
            log.error("erasure hook failed: {}", e.getClass().getSimpleName());
        }
    }

    /** @return how many TVs lost their readable code */
    public int runOnce() {
        Integer n = tx.execute(s -> {
            List<Map<String, Object>> tvs = jdbc.queryForList("SELECT DISTINCT t.tv_ref, t.device_code FROM act_tv t JOIN act_key k ON k.tv_ref = t.tv_ref JOIN lic_license l ON l.license_id = k.license_id"
                    + " JOIN lic_seat s ON s.license_pk = l.id AND s.seat_id = k.seat_id WHERE s.anonymized = TRUE AND t.device_code IS NOT NULL");
            for (Map<String, Object> t : tvs) {
                String ref = (String) t.get("tv_ref"), code = (String) t.get("device_code");
                scrubJournals(code);
                jdbc.update("UPDATE act_tv SET device_code = NULL WHERE tv_ref = ?", ref);
                eventLog.append(new EventLog.NewEvent("ERASED", clock.nowMs(), null, ref, null, null, "JOB", "erasure-hook", "LICENSE", null, "{\"reason\":\"right-to-erasure\"}", "E:" + ref));
            }
            return tvs.size();
        });
        return n == null ? 0 : n;
    }

    /** The signed batches keep the code in their body (base64url inside the token): the body is decoded, the code replaced, and the batch marked erased (its signature is gone). */
    private void scrubJournals(String code) {
        long after = 0;
        while (true) {
            List<Map<String, Object>> batches = jdbc.queryForList("SELECT id, text FROM act_journal_batch WHERE id > ? AND text_erased = FALSE ORDER BY id LIMIT 100", after);
            if (batches.isEmpty()) return;
            for (Map<String, Object> b : batches) {
                long id = ((Number) b.get("id")).longValue();
                after = id;
                String text = (String) b.get("text");
                String[] parts = text.split("\\.", -1);
                if (parts.length != 3) continue;
                String payload;
                try {
                    payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
                } catch (IllegalArgumentException e) {
                    continue;
                }
                if (!payload.contains(code)) continue;
                jdbc.update("UPDATE act_journal_batch SET text = ?, text_erased = TRUE WHERE id = ?", "erased-batch-v1\n" + payload.replace(code, "ERASED"), id);
            }
        }
    }
}
