package castbridge.server.activations;

import castbridge.server.common.Times;
import castbridge.server.licenses.Actor;
import castbridge.server.licenses.Hashing;
import castbridge.server.web.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cold archive: the lines of the two chains older than 24 months are written as {@code <table>-YYYY-MM.jsonl.gz} (with their SHA-256 in act_archive), and, ONLY on the owner's
 * order (OWNER, second factor, reason), removed from the database; the chain then restarts from the hash of the last removed line (act_archive.last_hash), so verification
 * still holds. Never automatic. The raw TV reports are purged after 90 days (nothing of value in them: the change is already an event).
 */
@Service
public class Archiver {
    private static final Logger log = LoggerFactory.getLogger(Archiver.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    public record Result(Map<String, Long> rowsByTable, List<String> files, boolean removed) {
        public long rows(String table) { return rowsByTable.getOrDefault(table, 0L); }
    }

    private final JdbcTemplate jdbc;
    private final ActClock clock;
    private final ActivationsProperties props;
    private final ActAccess access;
    private final EventLog eventLog;

    public Archiver(JdbcTemplate jdbc, ActClock clock, ActivationsProperties props, ActAccess access, EventLog eventLog) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.props = props;
        this.access = access;
        this.eventLog = eventLog;
    }

    public String directory() { return props.archiveDir().toString(); }

    @Scheduled(cron = "0 55 3 * * *", zone = "Africa/Douala")
    void nightlyPurge() {
        if (!props.enabled()) return;
        try {
            purgeReports();
        } catch (RuntimeException e) {
            log.error("report purge failed: {}", e.getClass().getSimpleName());
        }
    }

    /** Raw reports older than 90 days. */
    @Transactional
    public int purgeReports() { return jdbc.update("DELETE FROM act_report WHERE received_at < ?", Timestamp.from(clock.now().minus(Duration.ofDays(90)))); }

    /**
     * @param remove false = write the files only (a copy); true = also remove the archived lines from the database
     */
    @Transactional
    public Result archive(Actor actor, String reason, boolean remove) {
        access.require(actor, ActPermissions.Perm.ACT_ARCHIVE);
        if (reason == null || reason.isBlank()) throw ApiException.badRequest("Le motif est obligatoire pour archiver");
        Instant cutoff = clock.now().atZone(ZoneOffset.UTC).minusMonths(24).toInstant();
        Map<String, Long> rows = new LinkedHashMap<>();
        List<String> files = new ArrayList<>();
        for (String[] t : new String[][] {{"act_event", "recorded_at"}, {"adm_read_audit", "at"}}) {
            rows.put(t[0], archiveTable(t[0], t[1], cutoff, remove, files));
        }
        eventLog.append(new EventLog.NewEvent("ARCHIVED", clock.nowMs(), null, null, null, null, "ADMIN", actor.name(), "ADMIN", null,
                "{\"events\":" + rows.get("act_event") + ",\"reads\":" + rows.get("adm_read_audit") + ",\"removed\":" + remove + "}", "AR:" + clock.nowMs() + ":" + (remove ? "r" : "c")));
        return new Result(rows, files, remove);
    }

    private long archiveTable(String table, String timeCol, Instant cutoff, boolean remove, List<String> files) {
        long done = jdbc.queryForObject("SELECT COALESCE(MAX(to_id), 0) FROM act_archive WHERE table_name = ?", Long.class, table);
        Long maxOld = jdbc.queryForObject("SELECT MAX(id) FROM " + table + " WHERE " + timeCol + " < ? AND id > ?", Long.class, Timestamp.from(cutoff), done);
        long total = 0, after = done;
        YearMonth month = null;
        ByteArrayOutputStream bytes = null;
        GZIPOutputStream gz = null;
        long fromId = 0, toId = 0, count = 0;
        Timestamp fromAt = null, toAt = null;
        String lastHash = null;
        try {
            while (maxOld != null) {
                List<Map<String, Object>> batch = jdbc.queryForList("SELECT * FROM " + table + " WHERE id > ? AND id <= ? ORDER BY id LIMIT 1000", after, maxOld);
                if (batch.isEmpty()) break;
                for (Map<String, Object> r : batch) {
                    Timestamp at = Times.ts(r.get(timeCol));
                    YearMonth m = YearMonth.from(at.toInstant().atZone(ZoneOffset.UTC));
                    if (month != null && !m.equals(month)) {
                        files.add(finish(table, month, bytes, gz, fromId, toId, fromAt, toAt, count, lastHash, remove));
                        gz = null;
                    }
                    if (gz == null) {
                        month = m;
                        bytes = new ByteArrayOutputStream();
                        gz = new GZIPOutputStream(bytes);
                        fromId = ((Number) r.get("id")).longValue();
                        fromAt = at;
                        count = 0;
                    }
                    gz.write((JSON.writeValueAsString(Jsonl.row(table, r)) + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    toId = ((Number) r.get("id")).longValue();
                    toAt = at;
                    lastHash = (String) r.get("hash");
                    count++;
                    total++;
                    after = toId;
                }
            }
            if (gz != null) files.add(finish(table, month, bytes, gz, fromId, toId, fromAt, toAt, count, lastHash, remove));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (remove) {
            // everything archived so far (this run, or an earlier copy) leaves the database: the chain restarts from the hash of the last removed line
            long upTo = Math.max(done, maxOld == null ? 0 : maxOld);
            if (upTo > 0) {
                jdbc.update("UPDATE act_archive SET removed = TRUE WHERE table_name = ? AND to_id <= ?", table, upTo);
                jdbc.update("DELETE FROM " + table + " WHERE id <= ?", upTo);
            }
        }
        return total;
    }

    private String finish(String table, YearMonth month, ByteArrayOutputStream bytes, GZIPOutputStream gz, long fromId, long toId, Timestamp fromAt, Timestamp toAt, long count, String lastHash, boolean remove)
            throws IOException {
        gz.close();
        byte[] data = bytes.toByteArray();
        Path dir = props.archiveDir();
        Files.createDirectories(dir);
        String name = table + "-" + month + ".jsonl.gz";
        for (int n = 2; Files.exists(dir.resolve(name)); n++) name = table + "-" + month + "-" + n + ".jsonl.gz";   // a second run in the same month never overwrites the first file
        Path tmp = Files.createTempFile(dir, "act-archive", ".tmp");
        Files.write(tmp, data);
        Files.move(tmp, dir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
        jdbc.update("INSERT INTO act_archive (table_name, from_id, to_id, from_at, to_at, file, sha256, row_count, last_hash, removed, created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)", table, fromId, toId, fromAt, toAt, name,
                Hashing.sha256Hex(data), count, lastHash, remove, Timestamp.from(clock.now()));
        return name;
    }
}
