package castbridge.server.activations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Daily and monthly snapshots, signed daily checkpoints, cold archive (never automatic), the 90-day purge of raw reports, and the offline verifier. */
class HistoryTest extends ActTestBase {
    @Autowired Snapshots snapshots;
    @Autowired Checkpoints checkpoints;
    @Autowired Archiver archiver;
    @Autowired EventLog eventLog;
    @Autowired ReadAudit readAudit;
    @Autowired TvRef tvRef;
    @Autowired PlatformTransactionManager tx;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper mapper;

    Instant now;

    @BeforeEach
    void start() {
        resetModule();
        now = Instant.parse("2026-10-10T12:00:00Z");
        clock.set(now);
    }

    @AfterEach
    void stop() { clock.reset(); }

    private void key(String fp, String kind, String kid, String state) {
        jdbc.update("insert into act_key (fp, tag, form, kind, subject, kid, issued_at, state, state_at, flags, super) values (?,?,'ENVELOPE',?,'tv',?,?,?,?,'',false)", fp, fp.substring(0, 8), kind, kid,
                Timestamp.from(now), state, Timestamp.from(now));
    }

    private void event(String idem) {
        new TransactionTemplate(tx).executeWithoutResult(s -> eventLog.append(new EventLog.NewEvent("ISSUED", clock.nowMs(), sha256(idem), null, null, null, "TOOL", "t", "JOURNAL", null, null, idem)));
    }

    @Test
    void theDailySnapshotCountsActivationsByKindToolAndStateAndCanBeRerun() {
        for (int i = 0; i < 5; i++) key(sha256("d" + i), "TRIAL", kid(DESK), "ACTIVATED");
        for (int i = 0; i < 3; i++) key(sha256("p" + i), "PRODUCTION", kid(PHONE), "EMISE");
        key(sha256("u"), "PRODUCTION", kid(STRANGER), "REVOQUEE");
        LocalDate day = LocalDate.of(2026, 10, 10);
        snapshots.snapshotDay(day);
        snapshots.snapshotDay(day);
        assertEquals(5, jdbc.queryForObject("select n from act_daily where snap_day = ? and kind = 'TRIAL' and tool = 'DESK' and state = 'ACTIVATED'", Integer.class, java.sql.Date.valueOf(day)));
        assertEquals(3, jdbc.queryForObject("select n from act_daily where snap_day = ? and kind = 'PRODUCTION' and tool = 'PHONE' and state = 'EMISE'", Integer.class, java.sql.Date.valueOf(day)));
        assertEquals(1, jdbc.queryForObject("select n from act_daily where snap_day = ? and tool = 'UNKNOWN'", Integer.class, java.sql.Date.valueOf(day)));
        assertEquals(3, jdbc.queryForObject("select count(*) from act_daily", Integer.class), "a rerun replaces the day, it does not double it");
    }

    @Test
    void theMonthlySnapshotKeepsOneRowPerTv() {
        Dev d = dev();
        String ref = tvRef.of(d.code());
        jdbc.update("insert into act_tv (tv_ref, device_code, edition, trial_resets, app_code, last_report_at, reco, alerts_open, api_devices, android_ids) values (?,?,?,0,1412,?,'OK',2,0,0)", ref, d.code(), "PRODUCTION",
                Timestamp.from(now));
        snapshots.snapshotMonth(YearMonth.of(2026, 10));
        snapshots.snapshotMonth(YearMonth.of(2026, 10));
        Map<String, Object> m = jdbc.queryForMap("select * from act_tv_monthly where snap_month = '2026-10'");
        assertEquals(ref, m.get("tv_ref"));
        assertEquals("PRODUCTION", m.get("edition"));
        assertEquals(1412, ((Number) m.get("app_code")).intValue());
        assertEquals(2, ((Number) m.get("alerts_open")).intValue());
        assertEquals(1, jdbc.queryForObject("select count(*) from act_tv_monthly", Integer.class));
    }

    @Test
    void theCheckpointSignsTheHeadsOfBothChainsWithEd25519() throws Exception {
        for (int i = 0; i < 5; i++) event("e" + i);
        mvc.perform(adminGet("/api/v1/admin/activations/dashboard")).andReturn();
        LocalDate day = LocalDate.of(2026, 10, 10);
        Checkpoints.Checkpoint c = checkpoints.create(day);
        assertEquals(5L, c.eventLastId());
        assertEquals(eventLog.headHash(), c.eventHead());
        assertEquals(readAudit.headHash(), c.readHead());
        assertEquals(1L, c.readLastId());
        assertEquals(kid(new Ed25519PrivateKeyParameters(CHECKPOINT_SEED, 0)), c.sigKid());
        Ed25519Signer v = new Ed25519Signer();
        v.init(false, new Ed25519PublicKeyParameters(new Ed25519PrivateKeyParameters(CHECKPOINT_SEED, 0).generatePublicKey().getEncoded(), 0));
        byte[] payload = c.payload().getBytes(StandardCharsets.UTF_8);
        v.update(payload, 0, payload.length);
        assertTrue(v.verifySignature(Base64.getDecoder().decode(c.signature())), "the signature verifies with the public key only");
        assertEquals(String.join("|", "castbridge-act-checkpoint-v1", "2026-10-10", "5", c.eventHead(), "1", c.readHead(), c.countsJson()), c.payload());
        // a second run the same day replaces nothing and changes nothing
        Checkpoints.Checkpoint again = checkpoints.create(day);
        assertEquals(c.signature(), again.signature());
        assertEquals(1, jdbc.queryForObject("select count(*) from act_checkpoint", Integer.class));

        MvcResult r = mvc.perform(adminGet("/api/v1/admin/activations/checkpoints?from=2026-10-01&to=2026-10-31")).andReturn();
        assertEquals(200, r.getResponse().getStatus());
        JsonNode items = body(r).get("items");
        assertEquals(1, items.size());
        assertEquals(c.signature(), items.get(0).get("signature").asText());
    }

    @Test
    void archivingWritesGzipJsonlWithItsHashAndKeepsTheChainVerifiable() throws Exception {
        clock.set(now.atZone(ZoneOffset.UTC).minusMonths(26).toInstant());
        for (int i = 0; i < 6; i++) event("old" + i);
        clock.set(now.atZone(ZoneOffset.UTC).minusMonths(25).toInstant());
        for (int i = 0; i < 4; i++) event("old2-" + i);
        clock.set(now);
        for (int i = 0; i < 5; i++) event("new" + i);
        assertEquals(15, events());
        // nothing is ever archived by itself
        assertEquals(0, jdbc.queryForObject("select count(*) from act_archive", Integer.class));

        var res = archiver.archive(OWNER, "archive de test", true);
        assertEquals(10, res.rows("act_event"));
        assertEquals(2, jdbc.queryForObject("select count(*) from act_archive where table_name = 'act_event'", Integer.class), "one file per month");
        for (Map<String, Object> a : jdbc.queryForList("select * from act_archive where table_name = 'act_event' order by from_id")) {
            Path file = archiveDir().resolve((String) a.get("file"));
            assertTrue(Files.exists(file), file.toString());
            assertTrue(((String) a.get("file")).matches("act_event-\\d{4}-\\d{2}\\.jsonl\\.gz"));
            byte[] bytes = Files.readAllBytes(file);
            assertEquals(sha256Bytes(bytes), a.get("sha256"));
            try (var in = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
                String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                assertEquals(((Number) a.get("row_count")).intValue(), text.strip().split("\n").length);
                assertEquals(((Number) a.get("from_id")).longValue(), json.readTree(text.strip().split("\n")[0]).get("id").asLong());
            }
        }
        assertEquals(6, events(), "the 10 old rows left the database, on the owner's order: 5 recent ones and the line that records the archive remain");
        assertEquals(1, jdbc.queryForObject("select count(*) from act_event where type = 'ARCHIVED'", Integer.class));
        EventLog.Verification v = eventLog.verify();
        assertTrue(v.ok(), v.problem());
        event("after-archive");
        assertTrue(eventLog.verify().ok());
        // tampering with what remains is still caught
        jdbc.update("update act_event set after_json = 'x' where id = 12");
        assertFalse(eventLog.verify().ok());
    }

    @Test
    void archivingWithoutRemovalKeepsTheRows() throws Exception {
        clock.set(now.atZone(ZoneOffset.UTC).minusMonths(26).toInstant());
        for (int i = 0; i < 3; i++) event("old" + i);
        clock.set(now);
        var res = archiver.archive(OWNER, "copie seulement", false);
        assertEquals(3, res.rows("act_event"));
        assertEquals(4, events(), "3 copied rows stay, plus the line that records the archive");
        assertTrue(eventLog.verify().ok());
    }

    private Path archiveDir() { return Path.of(archiver.directory()); }

    private static String sha256Bytes(byte[] b) { return java.util.HexFormat.of().formatHex(castbridge.server.licenses.Hashing.sha256(b)); }

    @Test
    void rawReportsAreKeptNinetyDaysThenPurged() {
        Dev d = dev();
        String ref = tvRef.of(d.code());
        jdbc.update("insert into act_report (device_id, tv_ref, received_at, via, sha, app_code, n_activations) values (1,?,?,'direct',?,1412,1)", ref, Timestamp.from(now.minus(Duration.ofDays(91))), "a".repeat(64));
        jdbc.update("insert into act_report (device_id, tv_ref, received_at, via, sha, app_code, n_activations) values (1,?,?,'direct',?,1412,1)", ref, Timestamp.from(now.minus(Duration.ofDays(89))), "b".repeat(64));
        assertEquals(1, archiver.purgeReports());
        assertEquals(1, jdbc.queryForObject("select count(*) from act_report", Integer.class));
        assertNotNull(jdbc.queryForObject("select sha from act_report", String.class));
    }

    @Test
    void theOfflineVerifierAcceptsTheExportAndCatchesATamperedLine() throws Exception {
        assumeTrue(pythonWithCryptography(), "python3 with the cryptography package is needed for the signature check");
        for (int i = 0; i < 25; i++) event("v" + i);
        checkpoints.create(LocalDate.of(2026, 10, 10));
        for (int i = 0; i < 5; i++) event("w" + i);
        String events = mvc.perform(adminGet("/api/v1/admin/activations/export?what=events&format=jsonl")).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String cps = mvc.perform(adminGet("/api/v1/admin/activations/checkpoints")).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        Path dir = Files.createTempDirectory("verify-export");
        Files.writeString(dir.resolve("events.jsonl"), events);
        Files.writeString(dir.resolve("checkpoints.json"), cps);
        String pub = Base64.getEncoder().encodeToString(new Ed25519PrivateKeyParameters(CHECKPOINT_SEED, 0).generatePublicKey().getEncoded());
        Path script = Path.of("..", "tools", "activations", "verify_export.py");
        String[] args = {"--events", dir.resolve("events.jsonl").toString(), "--checkpoints", dir.resolve("checkpoints.json").toString(), "--audit-key-file", SECRETS.resolve("act-audit.key").toString(),
                "--public-key", pub};
        var ok = run(script, args);
        assertEquals(0, ok.exit, ok.out);
        assertTrue(ok.out.contains("chaîne intacte jusqu'au 30"), ok.out);
        // one line altered
        Files.writeString(dir.resolve("events.jsonl"), events.replaceFirst("\"after\":null", "\"after\":\"truqué\""));
        var bad = run(script, args);
        assertEquals(1, bad.exit, bad.out);
        assertTrue(bad.out.contains("ligne"), bad.out);
        // a checkpoint with a forged head
        Files.writeString(dir.resolve("events.jsonl"), events);
        Files.writeString(dir.resolve("checkpoints.json"), cps.replaceFirst("\"eventHead\":\"[0-9a-f]{4}", "\"eventHead\":\"ffff"));
        assertEquals(1, run(script, args).exit);
    }

    record Run(int exit, String out) {}

    private static boolean pythonWithCryptography() {
        try {
            Process p = new ProcessBuilder("python3", "-c", "import cryptography").redirectErrorStream(true).start();
            return p.waitFor(20, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    private static Run run(Path script, String... args) throws Exception {
        List<String> cmd = new java.util.ArrayList<>(List.of("python3", script.toString()));
        cmd.addAll(List.of(args));
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        p.waitFor(60, TimeUnit.SECONDS);
        return new Run(p.exitValue(), out);
    }
}
