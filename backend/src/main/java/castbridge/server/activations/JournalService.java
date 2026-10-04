package castbridge.server.activations;

import castbridge.server.activations.JournalVerifier.Entry;
import castbridge.server.activations.JournalVerifier.Status;
import castbridge.server.licenses.Actor;
import castbridge.server.licenses.Envelope;
import castbridge.server.licenses.TrustedKeys;
import castbridge.server.web.ApiException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The signed journal of an offline tool (desk, owner phone, agent): verified by {@link JournalVerifier} against what is already known, kept as received, then each NEW
 * entry becomes one event of the history (idem {@code J:<kid>:<n>}, carrying the entry hash so that a later rewrite is seen) and updates the inventory. A batch that does
 * not verify goes to quarantine (kept as proof, never applied) and raises a JOURNAL_BROKEN alert. A hole in the numbering is remembered until the missing batch arrives.
 */
@Service
public class JournalService {
    static final long EPOCH_2026_MS = 1_767_225_600_000L;
    private static final Pattern H = Pattern.compile("\"h\":\"([0-9a-f]{64})\""), P = Pattern.compile("\"p\":\"([0-9a-f]{64})\"");
    private static final Pattern HEX64 = Pattern.compile("[0-9a-f]{64}"), HEX8 = Pattern.compile("[0-9a-f]{8}"), SEAT = Pattern.compile("[0-9a-f]{16}"), NONCE = Pattern.compile("[0-9a-f]{8,64}");
    private static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9-]{0,63}");
    private static final long DAY_MS = 86_400_000L;

    public record Result(Status status, JournalVerifier.Reason reason, int accepted, int duplicate, int rejected, JournalVerifier.Gap gap, String batchSha256, String kid) {}

    private final JdbcTemplate jdbc;
    private final ActClock clock;
    private final EventLog log;
    private final Inventory inventory;
    private final TvRef tvRef;
    private final TrustedKeys trusted;
    private final AlertService alerts;
    private final Reconciler reconciler;
    private final ActivationsPolicy policy;
    private final ActAccess access;

    public JournalService(JdbcTemplate jdbc, ActClock clock, EventLog log, Inventory inventory, TvRef tvRef, TrustedKeys trusted, AlertService alerts, Reconciler reconciler, ActivationsPolicy policy,
                          ActAccess access) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.log = log;
        this.inventory = inventory;
        this.tvRef = tvRef;
        this.trusted = trusted;
        this.alerts = alerts;
        this.reconciler = reconciler;
        this.policy = policy;
        this.access = access;
    }

    /** What the database already knows about the journals. */
    private final class DbState implements JournalVerifier.State {
        @Override
        public long lastBatchSeq(String kid) { return first("SELECT last_batch_seq FROM act_tool WHERE kid = ?", kid); }

        @Override
        public long maxEntry(String kid) { return first("SELECT last_entry_n FROM act_tool WHERE kid = ?", kid); }

        @Override
        public String entryHash(String kid, long n) { return find(H, kid, n); }

        @Override
        public String entryPrev(String kid, long n) { return find(P, kid, n); }

        private long first(String sql, String kid) {
            List<Long> r = jdbc.queryForList(sql, Long.class, kid);
            return r.isEmpty() || r.get(0) == null ? 0L : r.get(0);
        }

        private String find(Pattern p, String kid, long n) {
            List<String> r = jdbc.queryForList("SELECT after_json FROM act_event WHERE idem_key = ?", String.class, "J:" + kid + ":" + n);
            if (r.isEmpty() || r.get(0) == null) return null;
            Matcher m = p.matcher(r.get(0));
            return m.find() ? m.group(1) : null;
        }
    }

    private boolean revoked(String kid) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM lic_revocation WHERE kid = ?", Integer.class, kid);
        return n != null && n > 0;
    }

    private byte[] keyOf(String kid) {
        TrustedKeys.Key k = trusted.find(kid);
        return k == null ? null : k.publicKey();
    }

    /** Verifies, keeps and applies a signed journal (the body of POST /api/v1/admin/activations/journal). */
    @Transactional
    public Result upload(Actor actor, String token, String via) {
        access.require(actor, ActPermissions.Perm.ACT_JOURNAL_UPLOAD);
        String channel = Set.of("web", "api", "phone").contains(via) ? via : "api";
        Envelope env = token == null ? null : Envelope.decode(token);
        policy.limit("journal:" + (env == null ? "?" : env.kid()), 30, Duration.ofHours(1), "journaux téléversés par outil");
        JournalVerifier verifier = new JournalVerifier(this::keyOf, this::revoked);
        JournalVerifier.Verdict v = verifier.verify(token, new DbState());
        JournalVerifier.Batch b = v.batch();
        Instant now = clock.now();
        switch (v.status()) {
            case REJECT -> {
                return new Result(Status.REJECT, v.reason(), 0, 0, 0, null, b == null ? null : b.sha256(), v.kid());
            }
            case QUARANTINE -> {
                boolean stored = storeBatch(b, token, channel, "QUARANTINE", v.reason().name(), 0, 0, 0, now);
                if (stored) {
                    TrustedKeys.Key key = trusted.find(v.kid());
                    if (key != null) touchTool(v.kid(), key, b, false, null, null, now);
                    alerts.raise(AlertService.Type.JOURNAL_BROKEN, null, null, v.kid(), null, "Lot de journal refusé (" + v.reason() + ") : gardé en quarantaine, jamais appliqué", v.reason() + ":" + b.sha256().substring(0, 8));
                    log.append(new EventLog.NewEvent("JOURNAL_BATCH", now.toEpochMilli(), null, null, null, v.kid(), "TOOL", v.kid(), "JOURNAL", null,
                            "{\"seq\":" + b.seq() + ",\"status\":\"QUARANTINE\",\"reason\":\"" + v.reason() + "\",\"by\":\"" + clean(actor.name()) + "\",\"via\":\"" + channel + "\"}", "B:" + b.sha256()));
                }
                return new Result(Status.QUARANTINE, v.reason(), 0, 0, 0, null, b.sha256(), v.kid());
            }
            case DUPLICATE -> {
                storeBatch(b, token, channel, "OK", null, 0, v.duplicate(), 0, now);
                return new Result(Status.DUPLICATE, null, 0, v.duplicate(), 0, null, b.sha256(), v.kid());
            }
            default -> { /* OK, below */ }
        }
        Touched t = new Touched();
        t.kids.add(v.kid());
        int rejected = 0;
        for (Entry e : v.fresh()) if (!project(b, e, t, now)) rejected++;
        TrustedKeys.Key key = trusted.find(v.kid());
        Entry last = b.entries().get(b.entries().size() - 1);
        long max = jdbc.queryForObject("SELECT COALESCE((SELECT last_entry_n FROM act_tool WHERE kid = ?), 0)", Long.class, v.kid());
        touchTool(v.kid(), key, b, true, last.n() > max ? last.n() : null, last.n() > max ? last.hash() : null, now);
        closeGaps(v.kid(), b.from(), b.to());
        if (v.gap() != null) {
            jdbc.update("INSERT INTO act_journal_gap (kid, from_n, to_n, opened_at) VALUES (?,?,?,?)", v.kid(), v.gap().from(), v.gap().to(), Timestamp.from(now));
        }
        storeBatch(b, token, channel, "OK", null, v.accepted() - rejected, v.duplicate(), rejected, now);
        log.append(new EventLog.NewEvent("JOURNAL_BATCH", now.toEpochMilli(), null, null, null, v.kid(), "TOOL", v.kid(), "JOURNAL", null,
                "{\"seq\":" + b.seq() + ",\"from\":" + b.from() + ",\"to\":" + b.to() + ",\"accepted\":" + v.accepted() + ",\"status\":\"OK\",\"by\":\"" + clean(actor.name()) + "\",\"via\":\"" + channel + "\"}", "B:" + b.sha256()));
        reconciler.reconcile(Reconciler.Scope.of(t.fps, t.tvRefs, t.kids, t.licenses));
        return new Result(Status.OK, null, v.accepted() - rejected, v.duplicate(), rejected, v.gap(), b.sha256(), v.kid());
    }

    // ------------------------------------------------------------------ storage of the batch, the tool, the holes

    private boolean storeBatch(JournalVerifier.Batch b, String token, String via, String status, String reason, int accepted, int duplicate, int rejected, Instant now) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM act_journal_batch WHERE sha256 = ?", Integer.class, b.sha256());
        if (n != null && n > 0) return false;
        jdbc.update("INSERT INTO act_journal_batch (kid, seq, sha256, text, from_n, to_n, received_at, via, accepted, duplicate, rejected, status, reason) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                b.kid(), b.seq(), b.sha256(), token, b.from(), b.to(), Timestamp.from(now), via, accepted, duplicate, rejected, status, reason);
        return true;
    }

    private void touchTool(String kid, TrustedKeys.Key key, JournalVerifier.Batch b, boolean ok, Long lastN, String lastHash, Instant now) {
        String type = toolType(key, b.tool());
        List<Map<String, Object>> row = jdbc.queryForList("SELECT last_entry_n, last_batch_seq FROM act_tool WHERE kid = ?", kid);
        Timestamp ts = Timestamp.from(now);
        if (row.isEmpty()) {
            jdbc.update("INSERT INTO act_tool (kid, tool, label, scopes, last_entry_n, last_entry_hash, last_batch_seq, last_upload_at, chain_ok) VALUES (?,?,?,?,?,?,?,?,?)", kid, type,
                    key == null ? null : Chains.clip(key.name(), 64), key == null ? null : String.join("+", new TreeSet<>(key.scopes().stream().map(Enum::name).toList())), lastN == null ? 0 : lastN, lastHash,
                    ok ? b.seq() : 0, ts, ok);
            return;
        }
        if (ok) {
            jdbc.update("UPDATE act_tool SET last_entry_n = COALESCE(?, last_entry_n), last_entry_hash = COALESCE(?, last_entry_hash),"
                    + " last_batch_seq = CASE WHEN last_batch_seq < ? THEN ? ELSE last_batch_seq END, last_upload_at = ?, chain_ok = TRUE WHERE kid = ?", lastN, lastHash, b.seq(), b.seq(), ts, kid);
        } else {
            jdbc.update("UPDATE act_tool SET chain_ok = FALSE WHERE kid = ?", kid);
        }
    }

    /** DESK, PHONE, SERVER, AGENT from the name of the trusted key, else from the tool declared in the signed body, else UNKNOWN. */
    static String toolType(TrustedKeys.Key key, String declared) {
        String name = key == null ? "" : key.name().toLowerCase(Locale.ROOT);
        if (name.contains("server")) return "SERVER";
        if (name.contains("desk")) return "DESK";
        if (name.contains("phone")) return "PHONE";
        if (name.contains("agent")) return "AGENT";
        return switch (declared == null ? "" : declared) {
            case "desk" -> "DESK";
            case "phone" -> "PHONE";
            case "agent" -> "AGENT";
            default -> "UNKNOWN";
        };
    }

    /** Removes [from, to] from the known holes of the tool. */
    private void closeGaps(String kid, long from, long to) {
        for (Map<String, Object> g : jdbc.queryForList("SELECT from_n, to_n, opened_at FROM act_journal_gap WHERE kid = ? AND from_n <= ? AND to_n >= ?", kid, to, from)) {
            long gf = ((Number) g.get("from_n")).longValue(), gt = ((Number) g.get("to_n")).longValue();
            Timestamp opened = (Timestamp) g.get("opened_at");
            jdbc.update("DELETE FROM act_journal_gap WHERE kid = ? AND from_n = ?", kid, gf);
            if (gf < from) jdbc.update("INSERT INTO act_journal_gap (kid, from_n, to_n, opened_at) VALUES (?,?,?,?)", kid, gf, from - 1, opened);
            if (gt > to) jdbc.update("INSERT INTO act_journal_gap (kid, from_n, to_n, opened_at) VALUES (?,?,?,?)", kid, to + 1, gt, opened);
        }
    }

    // ------------------------------------------------------------------ projection of one entry

    private static final class Touched {
        final Set<String> fps = new TreeSet<>(), tvRefs = new TreeSet<>(), kids = new TreeSet<>(), licenses = new TreeSet<>();
    }

    /** An entry whose fields are not what the format says: recorded as REFUSED (the batch itself is signed and good). */
    private static final class Bad extends RuntimeException {
        Bad(String m) { super(m); }
    }

    private static String need(Map<String, String> f, String name) {
        String v = f.get(name);
        if (v == null || v.isEmpty()) throw new Bad("missing " + name);
        return v;
    }

    private static String match(Pattern p, String v, String name) {
        if (!p.matcher(v).matches()) throw new Bad("bad " + name);
        return v;
    }

    private static long num(String v, String name) {
        try {
            long n = Long.parseLong(v);
            if (n < 0 || n > 4_102_444_800_000L) throw new Bad("bad " + name);
            return n;
        } catch (NumberFormatException e) {
            throw new Bad("bad " + name);
        }
    }

    private String json(Entry e, String extra) { return "{\"h\":\"" + e.hash() + "\",\"p\":\"" + e.prev() + "\",\"t\":\"" + e.type() + "\"" + (extra == null ? "" : "," + extra) + "}"; }

    private boolean ev(JournalVerifier.Batch b, Entry e, String type, String fp, String tv, String license, String extra) {
        return log.append(new EventLog.NewEvent(type, e.atMs(), fp, tv, license, b.kid(), "TOOL", b.kid(), "JOURNAL", null, json(e, extra), "J:" + b.kid() + ":" + e.n()));
    }

    /** @return false when the entry was recorded as refused because its fields were wrong */
    private boolean project(JournalVerifier.Batch b, Entry e, Touched t, Instant now) {
        try {
            switch (e.type()) {
                case "issue" -> issue(b, e, t);
                case "compact" -> compact(b, e, t);
                case "deliver" -> deliver(b, e, t);
                case "command" -> command(b, e, t);
                case "license" -> ev(b, e, "LICENSE_CHANGED", null, null, null, "\"registry\":\"" + clean(e.fields().get("registry")) + "\"");
                case "transfer" -> ev(b, e, "TRANSFERRED", null, null, null, "\"registry\":\"" + clean(e.fields().get("registry")) + "\"");
                case "revoke" -> ev(b, e, "key".equals(e.fields().get("target")) ? "REVOKED_KEY" : "REVOKED_SEAT", null, null, null, "\"registry\":\"" + clean(e.fields().get("registry")) + "\"");
                case "refused" -> {
                    String device = tvRef.canonical(e.fields().getOrDefault("device", ""));
                    ev(b, e, "REFUSED", null, device == null ? null : tvRef.of(device), null, "\"reason\":\"" + clean(e.fields().get("reason")) + "\"");
                }
                default -> throw new Bad("type");
            }
            return true;
        } catch (Bad bad) {
            ev(b, e, "REFUSED", null, null, null, "\"problem\":\"" + clean(bad.getMessage()) + "\"");
            return false;
        }
    }

    private static String clean(String s) { return s == null ? "" : Chains.clip(s.replaceAll("[\"\\\\]", "'"), 60); }

    private void issue(JournalVerifier.Batch b, Entry e, Touched t) {
        Map<String, String> f = e.fields();
        String fp = match(HEX64, need(f, "fp"), "fp");
        String kind = need(f, "kind").toLowerCase(Locale.ROOT);
        if (!kind.equals("trial") && !kind.equals("production")) throw new Bad("kind");
        String subject = f.getOrDefault("subject", "tv");
        if (!subject.equals("tv") && !subject.equals("phone")) throw new Bad("subject");
        String license = match(ID, need(f, "license"), "license");
        String seat = match(SEAT, need(f, "seat"), "seat");
        String device = tvRef.canonical(need(f, "device"));
        if (device == null) throw new Bad("device");
        String ref = tvRef.of(device);
        int k = (int) num(need(f, "k"), "k");
        String nonce = match(NONCE, need(f, "nonce"), "nonce");
        long aseq = num(need(f, "aseq"), "aseq"), issued = num(need(f, "issuedAt"), "issuedAt"), expires = num(need(f, "expiresAt"), "expiresAt");
        Instant usageTo = f.containsKey("usageTo") ? Instant.ofEpochMilli(num(f.get("usageTo"), "usageTo")) : null;
        boolean unlimited = "1".equals(f.get("unlimited"));
        String rights = f.get("rights");
        inventory.ensureTv(ref, device);
        inventory.upsertKey(new Inventory.KeyFacts(fp, "ENVELOPE", b.kid(), kind.toUpperCase(Locale.ROOT), subject, license, seat, ref, k, aseq, nonce, Instant.ofEpochMilli(issued), Instant.ofEpochMilli(issued),
                Instant.ofEpochMilli(expires), usageTo, usageTo == null ? (unlimited ? Boolean.TRUE : null) : Boolean.FALSE, "1".equals(f.get("super")), rights == null || rights.equals("-") ? null : Chains.clip(rights, 200)),
                Set.of(Inventory.DECLARED_JOURNAL));
        ev(b, e, "ISSUED", fp, ref, license, "\"kind\":\"" + kind + "\",\"seat\":\"" + seat + "\",\"expiresAt\":" + expires);
        t.fps.add(fp);
        t.tvRefs.add(ref);
        t.licenses.add(license);
    }

    private void compact(JournalVerifier.Batch b, Entry e, Touched t) {
        Map<String, String> f = e.fields();
        String fp = match(HEX64, need(f, "fp"), "fp");
        String kind = need(f, "kind").toLowerCase(Locale.ROOT);
        if (!kind.equals("trial") && !kind.equals("production")) throw new Bad("kind");
        String device = tvRef.canonical(need(f, "device"));
        if (device == null) throw new Bad("device");
        String ref = tvRef.of(device);
        long hour = num(need(f, "windowStartHour"), "windowStartHour");
        Instant issued = Instant.ofEpochMilli(EPOCH_2026_MS + hour * 3_600_000L);
        String set = f.getOrDefault("set", "-");
        inventory.ensureTv(ref, device);
        inventory.upsertKey(new Inventory.KeyFacts(fp, "COMPACT", b.kid(), kind.toUpperCase(Locale.ROOT), "tv", null, null, ref, null, null, null, issued, issued, issued.plusSeconds(48 * 3600L), null, null, false,
                "set:" + Chains.clip(set, 20)), Set.of(Inventory.DECLARED_JOURNAL));
        ev(b, e, "ISSUED_COMPACT", fp, ref, null, "\"kind\":\"" + kind + "\"");
        t.fps.add(fp);
        t.tvRefs.add(ref);
    }

    private void deliver(JournalVerifier.Batch b, Entry e, Touched t) {
        Map<String, String> f = e.fields();
        String fp = match(HEX64, need(f, "fp"), "fp");
        String way = need(f, "way");
        if (!Set.of("bt", "usb", "qr", "text").contains(way)) throw new Bad("way");
        String tv = f.getOrDefault("tv", "-");
        inventory.upsertKey(Inventory.KeyFacts.of(fp, "ENVELOPE"), Set.of());
        String ref = jdbc.queryForList("SELECT tv_ref FROM act_key WHERE fp = ?", String.class, fp).get(0);
        ev(b, e, "DELIVERED", fp, ref, null, "\"way\":\"" + way + "\",\"tv\":\"" + clean(tv) + "\"");
        if (tv.equals("ok")) {
            inventory.addFlags(fp, Set.of(Inventory.DELIVERED_BT));
            jdbc.update("UPDATE act_key SET delivered_bt_at = COALESCE(delivered_bt_at, ?) WHERE fp = ?", new Timestamp(e.atMs()), fp);
            inventory.syncContact(fp);
        }
        t.fps.add(fp);
        if (ref != null) t.tvRefs.add(ref);
    }

    private void command(JournalVerifier.Batch b, Entry e, Touched t) {
        Map<String, String> f = e.fields();
        String power = need(f, "power");
        if (!Set.of("support", "unlock", "open_all").contains(power)) throw new Bad("power");
        String action = f.getOrDefault("action", "-");
        String device = tvRef.canonical(need(f, "device"));
        if (device == null) throw new Bad("device");
        String challenge = match(HEX8, need(f, "challenge"), "challenge");
        String result = need(f, "result");
        int days = f.containsKey("days") ? (int) Math.min(num(f.get("days"), "days"), 3660) : 0;
        String ref = tvRef.of(device);
        inventory.ensureTv(ref, device);
        upsertCommand(ref, challenge, power, e.atMs(), days, result);
        t.tvRefs.add(ref);
        if (!result.equals("ok")) {
            ev(b, e, "REFUSED", null, ref, null, "\"command\":\"" + power + "\",\"result\":\"" + clean(result) + "\"");
            return;
        }
        switch (power) {
            case "open_all" -> {
                Timestamp until = new Timestamp(e.atMs() + days * DAY_MS);
                jdbc.update("UPDATE act_tv SET open_all_until = CASE WHEN open_all_until IS NULL OR open_all_until < ? THEN ? ELSE open_all_until END WHERE tv_ref = ?", until, until, ref);
                ev(b, e, "COMMAND_OPEN_ALL", null, ref, null, "\"days\":" + days);
            }
            case "unlock" -> {
                Timestamp until = new Timestamp(e.atMs() + days * DAY_MS);
                jdbc.update("UPDATE act_tv SET unlock_until = CASE WHEN unlock_until IS NULL OR unlock_until < ? THEN ? ELSE unlock_until END WHERE tv_ref = ?", until, until, ref);
                ev(b, e, "COMMAND_UNLOCK", null, ref, null, "\"days\":" + days);
            }
            default -> {
                if (action.equals("reset-trial")) {
                    // the TV's own report is the observed count; the journal only counts while the TV has never reported
                    jdbc.update("UPDATE act_tv SET trial_resets = trial_resets + 1 WHERE tv_ref = ? AND last_report_at IS NULL", ref);
                    ev(b, e, "TRIAL_RESET", null, ref, null, null);
                } else {
                    ev(b, e, "COMMAND_SUPPORT", null, ref, null, "\"action\":\"" + clean(action) + "\"");
                }
            }
        }
    }

    private void upsertCommand(String ref, String challenge, String power, long atMs, int days, String result) {
        int n = jdbc.update("UPDATE act_command SET declared = TRUE, power = ?, at_ms = COALESCE(at_ms, ?), days = COALESCE(days, ?), result = ? WHERE tv_ref = ? AND challenge = ?", power, atMs, days, Chains.clip(result, 40), ref, challenge);
        if (n == 0) {
            jdbc.update("INSERT INTO act_command (tv_ref, challenge, power, at_ms, days, declared, reported, result) VALUES (?,?,?,?,?,TRUE,FALSE,?)", ref, challenge, power, atMs, days, Chains.clip(result, 40));
        }
    }

    /** Helper for the controller: how the outcome maps to HTTP. */
    public static int httpStatus(Result r) {
        return switch (r.status()) {
            case OK, DUPLICATE -> 200;
            case QUARANTINE -> 422;
            case REJECT -> r.reason() == JournalVerifier.Reason.SEQ_NOT_INCREASING ? 409 : 400;
        };
    }

    static ApiException unavailable() { return new ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "Suivi des activations indisponible"); }
}
