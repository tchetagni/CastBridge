package castbridge.server.content;

import castbridge.server.CastbridgeApplication;
import castbridge.server.devices.Device;
import castbridge.server.web.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tester reports, the registry of items, review decisions, validation progress and the data of the review queue
 * (docs/CONTENT-VALIDATION.md). Reports and numbers only carry ids; the registry holds an excerpt for the reviewers.
 */
@Service
public class ContentService {
    /** One call carries at most this many reports; a device may send at most {@link #MAX_PER_HOUR} per hour and {@link #MAX_PER_DAY} per day. */
    public static final int MAX_BATCH = 50, MAX_PER_HOUR = 60, MAX_PER_DAY = 200;
    public static final int MAX_CANDIDATES = 5000;

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public ContentService(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public record ReportResult(int accepted, int duplicates, int rejected, List<String> errors) {}

    public record Row(String kind, String id, String lot, String classKey, String subject, String excerpt, String state, String reviewer,
                      String note, LocalDate decidedOn, String hash, Integer difficulty, long shown, long correct, long sumMs, long reports,
                      ContentRules.Suspicion suspicion) {
        public Double successRate() { return shown == 0 ? null : (double) correct / shown; }
        public Long averageMs() { return shown == 0 ? null : sumMs / shown; }
    }

    // ================================================================ reports (from the devices)

    @Transactional
    public ReportResult ingestReports(Device device, JsonNode body) {
        if (body == null || !body.isObject() || !body.path("reports").isArray()) throw ApiException.badRequest("Objet JSON attendu : {\"reports\":[…]}");
        JsonNode list = body.get("reports");
        if (list.size() > MAX_BATCH) throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "Au plus " + MAX_BATCH + " signalements par envoi");
        Instant now = Instant.now();
        Integer hour = jdbc.queryForObject("select count(*) from content_report where device_id = ? and received_at > ?", Integer.class,
                device.id, Timestamp.from(now.minusSeconds(3600)));
        Integer day = jdbc.queryForObject("select count(*) from content_report where device_id = ? and received_at > ?", Integer.class,
                device.id, Timestamp.from(now.minusSeconds(86400)));
        if (hour != null && hour >= MAX_PER_HOUR || day != null && day >= MAX_PER_DAY)
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Trop de signalements : réessayez plus tard");
        int accepted = 0, dup = 0, rejected = 0;
        List<String> errors = new ArrayList<>();
        for (JsonNode r : list) {
            String why;
            try {
                why = store(device, r, now);
            } catch (org.springframework.dao.DuplicateKeyException e) {
                dup++;
                continue;
            }
            if (why == null) accepted++;
            else if ("duplicate".equals(why)) dup++;
            else { rejected++; if (errors.size() < 10) errors.add(why); }
        }
        return new ReportResult(accepted, dup, rejected, errors);
    }

    /** @return null if stored, "duplicate", or the reason it is refused */
    private String store(Device device, JsonNode r, Instant now) {
        if (!r.isObject()) return "objet attendu";
        String kind = text(r, "kind"), item = text(r, "item"), reason = text(r, "reason"), id = text(r, "id");
        if (!ContentRules.KINDS.contains(kind)) return "kind inconnu";
        if (item == null || !ContentRules.ID.matcher(item).matches()) return "item invalide";
        if (!ContentRules.REASONS.containsKey(reason)) return "motif inconnu";
        if (id == null || !id.matches("[A-Za-z0-9-]{8,40}")) return "id de signalement invalide";
        String hash = text(r, "hash");
        if (hash != null && !ContentRules.HASH.matcher(hash).matches()) return "empreinte invalide";
        String lot = text(r, "lot");
        if (lot != null && (!ContentRules.LOT.matcher(lot).matches() || lot.contains(".."))) lot = null;
        String channel = "beta".equals(text(r, "channel")) ? "beta" : "stable";
        Integer lotVersion = r.path("lotVersion").isInt() && r.get("lotVersion").asInt() >= 0 && r.get("lotVersion").asInt() <= 1_000_000 ? r.get("lotVersion").asInt() : null;
        Instant at = r.path("at").isNumber() ? Instant.ofEpochMilli(r.get("at").asLong()) : now;
        if (at.isAfter(now.plusSeconds(86400)) || at.isBefore(now.minusSeconds(86400L * 400))) at = now;
        String note = ContentRules.cleanNote(text(r, "note"), ContentRules.MAX_NOTE);
        Integer same = jdbc.queryForObject("select count(*) from content_report where report_id = ? or (device_id = ? and kind = ? and item_id = ? and reason = ? and "
                + "coalesce(content_hash, '') = ?)", Integer.class, id, device.id, kind, item, reason, hash == null ? "" : hash);
        if (same != null && same > 0) return "duplicate";
        jdbc.update("insert into content_report (report_id, device_id, kind, item_id, reason, note, content_hash, lot, lot_version, channel, reported_at, received_at) "
                        + "values (?,?,?,?,?,?,?,?,?,?,?,?)", id, device.id, kind, item, reason, note, hash, lot, lotVersion, channel, Timestamp.from(at), Timestamp.from(now));
        // an item nobody registered yet still gets a row, so that its reports show in the queue
        jdbc.update("insert into content_item (kind, item_id, lot, updated_at) select ?, ?, ?, ? where not exists (select 1 from content_item where kind = ? and item_id = ?)",
                kind, item, lot == null ? "inconnu" : lot, Timestamp.from(now), kind, item);
        return null;
    }

    private static String text(JsonNode n, String k) { return n.hasNonNull(k) && n.get(k).isTextual() ? n.get(k).asText() : null; }

    // ================================================================ aggregated numbers (called by the telemetry ingestion)

    /** Adds totals of "content_stat" events: key "kind|item" -> {shown, correct, ms}. */
    public static void addStats(JdbcTemplate jdbc, Map<String, long[]> totals, Instant now) {
        totals.forEach((k, v) -> {
            String[] p = k.split("\\|", 2);
            jdbc.update("""
                    insert into content_stat (kind, item_id, shown, correct, sum_ms, updated_at) values (?,?,?,?,?,?)
                    on duplicate key update shown = shown + values(shown), correct = correct + values(correct),
                      sum_ms = sum_ms + values(sum_ms), updated_at = values(updated_at)""", p[0], p[1], v[0], v[1], v[2], Timestamp.from(now));
        });
    }

    // ================================================================ registry (index built by tools/content-validation)

    public record ImportResult(int created, int updated, int unchanged, int reopened, List<String> errors) {}

    /** One JSON object per line: {kind, id, lot, cls, subject, hash, text, state, difficulty}. A decision made on another hash is void. */
    @Transactional
    public ImportResult importIndex(Iterable<String> lines) {
        int created = 0, updated = 0, unchanged = 0, reopened = 0;
        List<String> errors = new ArrayList<>();
        Timestamp now = Timestamp.from(Instant.now());
        int n = 0;
        for (String line : lines) {
            n++;
            if (line.isBlank()) continue;
            JsonNode o;
            try {
                o = json.readTree(line);
            } catch (Exception e) {
                if (errors.size() < 10) errors.add("ligne " + n + " : JSON illisible");
                continue;
            }
            String kind = text(o, "kind"), id = text(o, "id"), lot = text(o, "lot"), hash = text(o, "hash");
            if (!ContentRules.KINDS.contains(kind) || id == null || !ContentRules.ID.matcher(id).matches() || lot == null || lot.isBlank() || lot.length() > 80
                    || (hash != null && !ContentRules.HASH.matcher(hash).matches())) {
                if (errors.size() < 10) errors.add("ligne " + n + " : élément invalide");
                continue;
            }
            String cls = clip(text(o, "cls"), 40), subject = clip(text(o, "subject"), 80), excerpt = clip(ContentRules.cleanNote(text(o, "text"), 200), 200);
            Integer diff = o.path("difficulty").isInt() ? o.get("difficulty").asInt() : null;
            List<Map<String, Object>> cur = jdbc.queryForList("select content_hash, state, decided_hash, lot, class_key, subject, excerpt, difficulty from content_item where kind = ? and item_id = ?", kind, id);
            if (cur.isEmpty()) {
                jdbc.update("insert into content_item (kind, item_id, lot, class_key, subject, content_hash, difficulty, excerpt, state, updated_at) values (?,?,?,?,?,?,?,?,?,?)",
                        kind, id, lot, cls, subject, hash, diff, excerpt, "review", now);
                created++;
                continue;
            }
            Map<String, Object> c = cur.get(0);
            boolean same = java.util.Objects.equals(c.get("content_hash"), hash) && lot.equals(c.get("lot")) && cls.equals(c.get("class_key"))
                    && subject.equals(c.get("subject")) && excerpt.equals(c.get("excerpt")) && java.util.Objects.equals(c.get("difficulty"), diff);
            if (same) { unchanged++; continue; }
            String state = (String) c.get("state");
            boolean changedContent = hash != null && c.get("content_hash") != null && !hash.equals(c.get("content_hash"));
            if (changedContent && !"review".equals(state)) {
                // the text changed since the decision: back to review (the old decision stays in the history)
                state = "review";
                reopened++;
            }
            jdbc.update("update content_item set lot = ?, class_key = ?, subject = ?, content_hash = ?, difficulty = ?, excerpt = ?, state = ?, updated_at = ? where kind = ? and item_id = ?",
                    lot, cls, subject, hash, diff, excerpt, state, now, kind, id);
            updated++;
        }
        return new ImportResult(created, updated, unchanged, reopened, errors);
    }

    private static String clip(String s, int max) { return s == null ? "" : s.length() > max ? s.substring(0, max) : s; }

    // ================================================================ review queue

    public record Filter(String lot, String classKey, String subject, String state, String kind, String q, String sort) {}

    public record Page(List<Row> rows, int total, int page, int pages) {}

    private static final String SELECT = """
            select i.kind, i.item_id, i.lot, i.class_key, i.subject, i.excerpt, i.state, i.reviewer, i.note, i.decided_on, i.content_hash, i.difficulty,
              coalesce(s.shown, 0) as shown, coalesce(s.correct, 0) as correct, coalesce(s.sum_ms, 0) as sum_ms,
              (select count(distinct r.device_id) from content_report r where r.kind = i.kind and r.item_id = i.item_id) as reports
            from content_item i left join content_stat s on s.kind = i.kind and s.item_id = i.item_id""";

    private static Row row(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        long shown = rs.getLong("shown"), correct = rs.getLong("correct"), reports = rs.getLong("reports");
        int d = rs.getInt("difficulty");
        Integer diff = rs.wasNull() ? null : d;
        java.sql.Date on = rs.getDate("decided_on");
        return new Row(rs.getString("kind"), rs.getString("item_id"), rs.getString("lot"), rs.getString("class_key"), rs.getString("subject"),
                rs.getString("excerpt"), rs.getString("state"), rs.getString("reviewer"), rs.getString("note"), on == null ? null : on.toLocalDate(),
                rs.getString("content_hash"), diff, shown, correct, rs.getLong("sum_ms"), reports,
                "question".equals(rs.getString("kind")) || "exercise".equals(rs.getString("kind"))
                        ? ContentRules.suspicion(diff, shown, correct, reports) : ContentRules.suspicion(diff, 0, 0, reports));
    }

    private static String where(Filter f, List<Object> args) {
        StringBuilder w = new StringBuilder(" where 1=1");
        if (notBlank(f.lot())) { w.append(" and i.lot = ?"); args.add(f.lot()); }
        if (notBlank(f.classKey())) { w.append(" and i.class_key = ?"); args.add(f.classKey()); }
        if (notBlank(f.subject())) { w.append(" and i.subject = ?"); args.add(f.subject()); }
        if (notBlank(f.state()) && ContentRules.STATES.contains(f.state())) { w.append(" and i.state = ?"); args.add(f.state()); }
        if (notBlank(f.kind()) && ContentRules.KINDS.contains(f.kind())) { w.append(" and i.kind = ?"); args.add(f.kind()); }
        if (notBlank(f.q())) { w.append(" and (lower(i.item_id) like ? or lower(i.excerpt) like ?)"); String like = "%" + f.q().toLowerCase().replace("%", "").replace("_", "") + "%"; args.add(like); args.add(like); }
        return w.toString();
    }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }

    /** sort = "reports" | "score" | "lot" (default). The score is computed here (function of difficulty, numbers and reports). */
    public Page queue(Filter f, int page, int size) {
        List<Object> args = new ArrayList<>();
        String w = where(f, args);
        String sort = f.sort() == null ? "lot" : f.sort();
        Integer total = jdbc.queryForObject("select count(*) from content_item i" + w, Integer.class, args.toArray());
        int t = total == null ? 0 : total;
        int pages = Math.max(1, (t + size - 1) / size);
        int p = Math.max(0, Math.min(page, pages - 1));
        if ("score".equals(sort)) {
            // only items with numbers or reports can score: bounded candidate set, sorted in memory
            List<Row> rows = new ArrayList<>(jdbc.query(SELECT + w + " and (s.shown is not null or exists (select 1 from content_report r where r.kind = i.kind and r.item_id = i.item_id))"
                    + " limit " + MAX_CANDIDATES, ContentService::row, args.toArray()));
            rows.sort(Comparator.comparingDouble((Row r) -> r.suspicion().score()).reversed().thenComparing(Row::id));
            int from = Math.min(rows.size(), p * size);
            return new Page(rows.subList(from, Math.min(rows.size(), from + size)), rows.size(), p, Math.max(1, (rows.size() + size - 1) / size));
        }
        String order = "reports".equals(sort) ? " order by reports desc, i.lot, i.item_id" : " order by i.lot, i.class_key, i.subject, i.item_id";
        List<Row> rows = jdbc.query(SELECT + w + order + " limit " + size + " offset " + (p * size), ContentService::row, args.toArray());
        return new Page(rows, t, p, pages);
    }

    public Row item(String kind, String id) {
        List<Row> r = jdbc.query(SELECT + " where i.kind = ? and i.item_id = ?", ContentService::row, kind, id);
        if (r.isEmpty()) throw ApiException.notFound("Élément inconnu : " + id);
        return r.get(0);
    }

    public record Report(String reason, String note, String hash, String lot, Integer lotVersion, String channel, Instant at, boolean current) {}

    /** The reports of an item, newest first (no device identity), flagged when made on an older content version. */
    public List<Report> reports(String kind, String id, String currentHash) {
        return jdbc.query("select reason, note, content_hash, lot, lot_version, channel, reported_at from content_report where kind = ? and item_id = ? order by reported_at desc limit 200",
                (rs, n) -> new Report(rs.getString("reason"), rs.getString("note"), rs.getString("content_hash"), rs.getString("lot"),
                        (Integer) rs.getObject("lot_version"), rs.getString("channel"), rs.getTimestamp("reported_at").toInstant(),
                        currentHash == null || rs.getString("content_hash") == null || currentHash.equals(rs.getString("content_hash"))), kind, id);
    }

    public Map<String, Long> reasonCounts(String kind, String id) {
        Map<String, Long> m = new LinkedHashMap<>();
        jdbc.query("select reason, count(distinct device_id) c from content_report where kind = ? and item_id = ? group by reason order by c desc",
                rs -> { m.put(rs.getString(1), rs.getLong(2)); }, kind, id);
        return m;
    }

    public List<String> lots() { return jdbc.queryForList("select distinct lot from content_item order by lot", String.class); }

    public List<String> classes(String lot) {
        return notBlank(lot) ? jdbc.queryForList("select distinct class_key from content_item where lot = ? and class_key <> '' order by class_key", String.class, lot)
                : jdbc.queryForList("select distinct class_key from content_item where class_key <> '' order by class_key", String.class);
    }

    public List<String> subjects(String lot) {
        return notBlank(lot) ? jdbc.queryForList("select distinct subject from content_item where lot = ? and subject <> '' order by subject", String.class, lot)
                : jdbc.queryForList("select distinct subject from content_item where subject <> '' order by subject", String.class);
    }

    // ================================================================ decisions

    /** Sets the state of an item. Legal moves only; a reason is required to reject or ask for a fix; validating records the hash seen. */
    @Transactional
    public void decide(String kind, String id, String state, String reviewer, String note, LocalDate date) {
        String to = ContentRules.state(state);
        if (to == null) throw ApiException.badRequest("État inconnu : " + state);
        Row cur = item(kind, id);
        String why = check(cur, to, reviewer, note);
        if (why != null) throw ApiException.badRequest(id + " : " + why);
        write(cur, to, reviewer.trim(), ContentRules.cleanNote(note, 500), date == null ? LocalDate.now(CastbridgeApplication.ZONE) : date);
    }

    private static String check(Row cur, String to, String reviewer, String note) {
        if (reviewer == null || reviewer.isBlank() || reviewer.length() > 60) return "relecteur manquant";
        if (!ContentRules.canMove(cur.state(), to)) return cur.state() + " → " + to + " n'est pas une transition permise";
        if (("rejected".equals(to) || "needs-fix".equals(to)) && ContentRules.cleanNote(note, 500).isEmpty()) return "une note est obligatoire pour « " + to + " »";
        if ("validated".equals(to) && cur.hash() == null) return "empreinte inconnue : importez d'abord l'index des contenus";
        return null;
    }

    private void write(Row cur, String to, String reviewer, String note, LocalDate date) {
        Timestamp now = Timestamp.from(Instant.now());
        boolean review = "review".equals(to);
        jdbc.update("update content_item set state = ?, reviewer = ?, note = ?, decided_on = ?, decided_hash = ?, updated_at = ? where kind = ? and item_id = ?",
                to, review ? null : reviewer, review ? null : note, review ? null : java.sql.Date.valueOf(date), review ? null : cur.hash(), now, cur.kind(), cur.id());
        jdbc.update("insert into content_decision (kind, item_id, state, reviewer, decided_on, note, content_hash, lot, created_at) values (?,?,?,?,?,?,?,?,?)",
                cur.kind(), cur.id(), to, reviewer, java.sql.Date.valueOf(date), note.isEmpty() ? null : note, cur.hash(), cur.lot(), now);
    }

    public record BulkResult(int validated, int skippedReported, int skippedOther) {}

    /**
     * Validates every item of a lot that is still under review. Items with reports (and, unless {@code includeSuspicious}, the flagged ones)
     * are skipped and stay in the queue; returns what was done.
     */
    @Transactional
    public BulkResult bulkValidate(String lot, String classKey, String reviewer, String note, boolean includeReported) {
        if (!notBlank(lot)) throw ApiException.badRequest("Choisissez un lot");
        if (reviewer == null || reviewer.isBlank()) throw ApiException.badRequest("Relecteur manquant");
        Filter f = new Filter(lot, classKey, null, "review", null, null, "lot");
        List<Object> args = new ArrayList<>();
        List<Row> rows = jdbc.query(SELECT + where(f, args) + " order by i.item_id limit 100000", ContentService::row, args.toArray());
        int ok = 0, reported = 0, other = 0;
        String n = notBlank(note) ? note : "Validation du lot " + lot;
        for (Row r : rows) {
            if (r.hash() == null) { other++; continue; }
            if (!includeReported && (r.reports() > 0 || r.suspicion().flagged())) { reported++; continue; }
            write(r, "validated", reviewer.trim(), ContentRules.cleanNote(n, 500), LocalDate.now(CastbridgeApplication.ZONE));
            ok++;
        }
        return new BulkResult(ok, reported, other);
    }

    // ================================================================ CSV for reviewers who work offline

    public static final List<String> CSV_HEADER = List.of("id", "kind", "lot", "classe", "matiere", "etat", "relecteur", "date", "note", "empreinte",
            "signalements", "vus", "reussite", "score", "extrait");

    public String exportCsv(Filter f) {
        List<Object> args = new ArrayList<>();
        List<Row> rows = jdbc.query(SELECT + where(f, args) + " order by i.lot, i.class_key, i.subject, i.item_id limit 200000", ContentService::row, args.toArray());
        StringBuilder b = new StringBuilder("﻿").append(String.join(";", CSV_HEADER)).append("\r\n");
        for (Row r : rows) {
            b.append(String.join(";", cell(r.id()), cell(r.kind()), cell(r.lot()), cell(r.classKey()), cell(r.subject()), cell(r.state()), cell(r.reviewer()),
                    cell(r.decidedOn() == null ? "" : r.decidedOn().toString()), cell(r.note()), cell(r.hash()), String.valueOf(r.reports()), String.valueOf(r.shown()),
                    r.successRate() == null ? "" : String.valueOf(Math.round(100 * r.successRate())), String.format(java.util.Locale.ROOT, "%.2f", r.suspicion().score()),
                    cell(r.excerpt()))).append("\r\n");
        }
        return b.toString();
    }

    /** Spreadsheet-safe cell: quoted, and a leading = + - @ is neutralised (formula injection). */
    static String cell(String s) {
        if (s == null) return "";
        String v = s.replace("\r", " ").replace("\n", " ");
        if (!v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0) v = "'" + v;
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }

    public record CsvResult(int applied, int skipped, List<String> errors) {}

    /** Re-imports the decisions of a reviewer (columns id, etat/state, relecteur/reviewer, date, note, empreinte/hash; kind optional). */
    @Transactional
    public CsvResult importCsv(String text, String defaultReviewer) {
        List<List<String>> rows = parseCsv(text);
        if (rows.isEmpty()) throw ApiException.badRequest("Fichier vide");
        List<String> head = rows.get(0).stream().map(h -> h.trim().toLowerCase().replace("﻿", "")).toList();
        int iId = col(head, "id"), iKind = col(head, "kind"), iState = Math.max(col(head, "etat"), col(head, "state")), iRev = Math.max(col(head, "relecteur"), col(head, "reviewer")),
                iDate = col(head, "date"), iNote = col(head, "note"), iHash = Math.max(col(head, "empreinte"), col(head, "hash"));
        if (iId < 0 || iState < 0) throw ApiException.badRequest("Colonnes obligatoires : id et etat");
        int applied = 0, skipped = 0;
        List<String> errors = new ArrayList<>();
        for (int n = 1; n < rows.size(); n++) {
            List<String> r = rows.get(n);
            String id = get(r, iId);
            String to = ContentRules.state(get(r, iState));
            if (id.isEmpty() || to == null) { skipped++; continue; }
            String kind = get(r, iKind);
            try {
                Row cur = kind.isEmpty() ? findById(id) : item(kind, id);
                if (cur.state().equals(to) && ("review".equals(to) || get(r, iNote).equals(cur.note() == null ? "" : cur.note()))) { skipped++; continue; }
                String hash = get(r, iHash);
                if (!hash.isEmpty() && cur.hash() != null && !hash.equals(cur.hash())) throw ApiException.badRequest("le contenu a changé depuis l'export");
                String reviewer = get(r, iRev).isEmpty() ? defaultReviewer : get(r, iRev);
                LocalDate date = null;
                try { if (!get(r, iDate).isEmpty()) date = LocalDate.parse(get(r, iDate)); } catch (java.time.format.DateTimeParseException e) { throw ApiException.badRequest("date invalide (AAAA-MM-JJ)"); }
                String why = check(cur, to, reviewer, get(r, iNote));
                if (why != null) throw ApiException.badRequest(why);
                write(cur, to, reviewer.trim(), ContentRules.cleanNote(get(r, iNote), 500), date == null ? LocalDate.now(CastbridgeApplication.ZONE) : date);
                applied++;
            } catch (ApiException e) {
                skipped++;
                if (errors.size() < 20) errors.add("ligne " + (n + 1) + " (" + id + ") : " + e.getMessage());
            }
        }
        return new CsvResult(applied, skipped, errors);
    }

    private Row findById(String id) {
        List<Row> r = jdbc.query(SELECT + " where i.item_id = ?", ContentService::row, id);
        if (r.isEmpty()) throw ApiException.notFound("élément inconnu");
        if (r.size() > 1) throw ApiException.badRequest("id ambigu : ajoutez la colonne kind");
        return r.get(0);
    }

    private static int col(List<String> head, String name) { return head.indexOf(name); }
    private static String get(List<String> r, int i) { return i < 0 || i >= r.size() ? "" : r.get(i).trim(); }

    /** Minimal CSV reader: ';' or ',' (taken from the header), quotes, doubled quotes, line breaks inside quotes. */
    static List<List<String>> parseCsv(String text) {
        String t = text.startsWith("﻿") ? text.substring(1) : text;
        int eol = t.indexOf('\n');
        String first = eol < 0 ? t : t.substring(0, eol);
        char sep = count(first, ';') >= count(first, ',') && first.indexOf(';') >= 0 ? ';' : first.indexOf('\t') >= 0 && first.indexOf(',') < 0 ? '\t' : ',';
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < t.length() && t.charAt(i + 1) == '"') { cur.append('"'); i++; } else quoted = false;
                } else cur.append(c);
            } else if (c == '"') quoted = true;
            else if (c == sep) { row.add(cur.toString()); cur.setLength(0); }
            else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < t.length() && t.charAt(i + 1) == '\n') i++;
                row.add(cur.toString()); cur.setLength(0);
                if (!(row.size() == 1 && row.get(0).isEmpty())) rows.add(row);
                row = new ArrayList<>();
            } else cur.append(c);
        }
        if (cur.length() > 0 || !row.isEmpty()) { row.add(cur.toString()); rows.add(row); }
        return rows;
    }

    private static int count(String s, char c) { return (int) s.chars().filter(x -> x == c).count(); }

    // ================================================================ progress dashboard

    public record Progress(String lot, String classKey, long total, long validated, long review, long needsFix, long rejected, long reported) {
        public double percentValidated() { return total == 0 ? 0 : 100.0 * validated / total; }
    }

    /** Per lot (classKey = null) or per lot and class. */
    public List<Progress> progress(boolean perClass) {
        String cls = perClass ? "i.class_key" : "''";
        return jdbc.query("select i.lot, " + cls + " as cls, count(*) total, sum(case when i.state = 'validated' then 1 else 0 end) validated, "
                + "sum(case when i.state = 'review' then 1 else 0 end) review, sum(case when i.state = 'needs-fix' then 1 else 0 end) nf, "
                + "sum(case when i.state = 'rejected' then 1 else 0 end) rej, "
                + "sum(case when exists (select 1 from content_report r where r.kind = i.kind and r.item_id = i.item_id) then 1 else 0 end) reported "
                + "from content_item i group by i.lot, " + cls + " order by i.lot, " + cls,
                (rs, n) -> new Progress(rs.getString("lot"), rs.getString("cls"), rs.getLong("total"), rs.getLong("validated"), rs.getLong("review"),
                        rs.getLong("nf"), rs.getLong("rej"), rs.getLong("reported")));
    }

    // ================================================================ export of the decisions to the repository

    /** The history of the decisions in the record format of tools/content-validation (one JSON per line, oldest first). */
    public String exportRecords() {
        StringBuilder b = new StringBuilder();
        jdbc.query("select kind, item_id, state, reviewer, decided_on, note, content_hash, lot from content_decision order by id", rs -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rs.getString("item_id")); m.put("kind", rs.getString("kind")); m.put("state", rs.getString("state"));
            m.put("reviewer", rs.getString("reviewer")); m.put("date", rs.getDate("decided_on").toLocalDate().toString());
            if (rs.getString("note") != null) m.put("note", rs.getString("note"));
            if (rs.getString("content_hash") != null) m.put("hash", rs.getString("content_hash"));
            if (rs.getString("lot") != null) m.put("lot", rs.getString("lot"));
            try { b.append(json.writeValueAsString(m)).append('\n'); } catch (Exception e) { throw new IllegalStateException(e); }
        });
        return b.toString();
    }
}
