package castbridge.server.telemetry;

import castbridge.server.CastbridgeApplication;
import castbridge.server.devices.Device;
import castbridge.server.web.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ingestion of usage events: validation against the closed catalog (white-listed properties, forbidden keys refuse
 * the event), consent (without "usage statistics" only the essential events are kept), de-duplication on the event
 * UUID (a batch can be replayed safely), storage of the raw event and incremental update of the KPI tables.
 */
@Service
public class TelemetryService {
    private static final Logger log = LoggerFactory.getLogger(TelemetryService.class);
    public static final int MAX_EVENTS = 500;
    private static final Pattern UUID = Pattern.compile("[0-9a-fA-F-]{36}");
    private static final int MAX_ERRORS = 20;
    /** Raw events are kept 13 months; per-device aggregates 25 months; anonymous aggregates without limit. */
    static final int RAW_DAYS = 396, DEVICE_AGG_DAYS = 760;

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public TelemetryService(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public record Rejected(String id, String reason) {}

    public record Result(int accepted, int duplicates, int rejected, List<Rejected> errors, OffsetDateTime serverTime) {}

    /** A validated event, ready to store. */
    record Clean(String eventId, String name, String sessionId, Integer versionCode, String dim1, String dim2, Long ms, Long bytes,
                 Double value, Boolean ok, String props, Instant deviceTs, LocalDate day) {}

    @Transactional
    public Result ingest(Device device, JsonNode batch) {
        if (batch == null || !batch.isObject()) throw ApiException.badRequest("Objet JSON attendu : {\"events\":[…]}");
        JsonNode events = batch.get("events");
        if (events == null || !events.isArray()) throw ApiException.badRequest("Champ « events » (tableau) attendu");
        if (events.size() > MAX_EVENTS) throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "Au plus " + MAX_EVENTS + " événements par lot");
        String app = text(batch, "app", device.app);
        if (!device.app.equals(app)) throw ApiException.badRequest("app : ne correspond pas à l'appareil enregistré");
        Integer batchVersion = batch.hasNonNull("versionCode") ? batch.get("versionCode").asInt() : device.versionCode;

        Instant now = Instant.now();
        List<Rejected> errors = new ArrayList<>();
        int rejected = 0;
        Map<String, Clean> valid = new LinkedHashMap<>();
        for (JsonNode e : events) {
            String id = e.hasNonNull("id") ? e.get("id").asText() : null;
            String reason = null;
            Clean c = null;
            try {
                c = validate(device, app, e, batchVersion, now);
            } catch (Invalid ex) {
                reason = ex.getMessage();
            }
            if (c == null) {
                rejected++;
                if (errors.size() < MAX_ERRORS) errors.add(new Rejected(id, reason));
                continue;
            }
            valid.putIfAbsent(c.eventId(), c); // duplicate inside the batch: once
        }
        int inBatchDuplicates = events.size() - rejected - valid.size();

        Set<String> known = new HashSet<>();
        if (!valid.isEmpty()) {
            List<String> ids = new ArrayList<>(valid.keySet());
            String marks = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
            known.addAll(jdbc.queryForList("select event_id from telemetry_event where event_id in (" + marks + ")", String.class, ids.toArray()));
        }
        List<Clean> fresh = valid.values().stream().filter(c -> !known.contains(c.eventId())).toList();
        store(device, app, fresh, now);
        return new Result(fresh.size(), known.size() + inBatchDuplicates, rejected, errors,
                now.atZone(CastbridgeApplication.ZONE).toOffsetDateTime());
    }

    static final class Invalid extends Exception {
        Invalid(String m) { super(m, null, false, false); }
    }

    Clean validate(Device device, String app, JsonNode e, Integer batchVersion, Instant now) throws Invalid {
        if (!e.isObject()) throw new Invalid("objet attendu");
        String id = e.hasNonNull("id") ? e.get("id").asText() : "";
        if (!UUID.matcher(id).matches()) throw new Invalid("id : UUID attendu");
        String name = e.hasNonNull("name") ? e.get("name").asText() : "";
        EventCatalog.Def def = EventCatalog.EVENTS.get(name);
        if (def == null) throw new Invalid("événement inconnu : " + name);
        if (!def.essential() && !device.usageConsent) throw new Invalid("statistiques d'usage non consenties");
        String session = e.hasNonNull("sessionId") ? e.get("sessionId").asText() : null;
        if (session != null && !UUID.matcher(session).matches()) throw new Invalid("sessionId : UUID attendu");
        Integer version = e.hasNonNull("versionCode") ? Integer.valueOf(e.get("versionCode").asInt()) : batchVersion;

        Instant ts = timestamp(e.get("ts"));
        if (ts == null) throw new Invalid("ts : date attendue (millisecondes ou ISO 8601)");
        // a device clock far off: the server time is used for the day
        Instant forDay = ts.isAfter(now.plus(1, ChronoUnit.DAYS)) || ts.isBefore(now.minus(30, ChronoUnit.DAYS)) ? now : ts;
        LocalDate day = LocalDate.ofInstant(forDay, CastbridgeApplication.ZONE);

        ObjectNode kept = json.createObjectNode();
        JsonNode props = e.get("props");
        if (props != null && !props.isNull()) {
            if (!props.isObject()) throw new Invalid("props : objet attendu");
            for (Iterator<Map.Entry<String, JsonNode>> it = props.fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> p = it.next();
                String key = p.getKey();
                if (EventCatalog.FORBIDDEN.contains(key.toLowerCase())) throw new Invalid("propriété interdite : " + key);
                EventCatalog.Prop spec = def.props().get(key);
                if (spec == null) continue; // not in the white list: dropped
                JsonNode v = p.getValue();
                if (v == null || v.isNull()) continue;
                if (v.isContainerNode()) throw new Invalid(key + " : valeur simple attendue");
                switch (spec.kind()) {
                    case BOOL -> {
                        if (!v.isBoolean()) throw new Invalid(key + " : true/false attendu");
                        kept.put(key, v.asBoolean());
                    }
                    case INT, NUM -> {
                        if (!v.isNumber()) throw new Invalid(key + " : nombre attendu");
                        double d = v.asDouble();
                        if (d < 0 || d > spec.max() || Double.isNaN(d)) throw new Invalid(key + " : hors limites");
                        if (spec.kind() == EventCatalog.Kind.INT) kept.put(key, v.asLong());
                        else kept.put(key, d);
                    }
                    case ENUM -> {
                        if (!spec.values().contains(v.asText())) throw new Invalid(key + " : valeur inconnue");
                        kept.put(key, v.asText());
                    }
                    case TEXT -> {
                        String s = v.asText();
                        if (s.length() > spec.maxLen() || !EventCatalog.CODE.matcher(s).matches()) throw new Invalid(key + " : code court attendu");
                        kept.put(key, s);
                    }
                    case MESSAGE -> {
                        String s = EventCatalog.scrub(v.asText().replaceAll("\\p{Cntrl}", " ").strip());
                        kept.put(key, s.length() > spec.maxLen() ? s.substring(0, spec.maxLen()) : s);
                    }
                }
            }
        }
        // features and screens: closed list per app
        for (String k : List.of("feature", "screen")) {
            if (kept.has(k) && !EventCatalog.knownScreen(app, kept.get(k).asText()))
                throw new Invalid(k + " : identifiant inconnu « " + kept.get(k).asText() + " »");
        }
        if ("feature_used".equals(name) && !kept.has("feature")) throw new Invalid("feature obligatoire");
        if (("screen_view".equals(name) || "screen_time".equals(name)) && !kept.has("screen")) throw new Invalid("screen obligatoire");

        String dim1 = str(kept, def.dim1()), dim2 = str(kept, def.dim2());
        Long ms = lng(kept, def.ms()), bytes = lng(kept, def.bytes());
        Double value = def.value() == null || !kept.has(def.value()) ? null : kept.get(def.value()).asDouble();
        Boolean ok = def.ok() == null || !kept.has(def.ok()) ? null : kept.get(def.ok()).asBoolean();
        if (ok == null && kept.has("error") && def.ok() != null) ok = false;
        String propsText = kept.isEmpty() ? null : kept.toString();
        if (propsText != null && propsText.length() > 2000) throw new Invalid("props trop volumineuses");
        return new Clean(id.toLowerCase(), name, session, version, dim1, dim2, ms, bytes, value, ok, propsText, ts, day);
    }

    private void store(Device device, String app, List<Clean> events, Instant now) {
        if (events.isEmpty()) return;
        Timestamp serverTs = Timestamp.from(now);
        jdbc.batchUpdate("""
                insert into telemetry_event (event_id, device_id, app, version_code, session_id, name, dim1, dim2, num_ms, num_bytes,
                  num_value, ok, props, device_ts, server_ts, stat_day) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
                events, 200, (ps, c) -> {
                    ps.setString(1, c.eventId());
                    ps.setLong(2, device.id);
                    ps.setString(3, app);
                    ps.setObject(4, c.versionCode());
                    ps.setString(5, c.sessionId());
                    ps.setString(6, c.name());
                    ps.setString(7, c.dim1());
                    ps.setString(8, c.dim2());
                    ps.setObject(9, c.ms());
                    ps.setObject(10, c.bytes());
                    ps.setObject(11, c.value());
                    ps.setObject(12, c.ok());
                    ps.setString(13, c.props());
                    ps.setTimestamp(14, Timestamp.from(c.deviceTs()));
                    ps.setTimestamp(15, serverTs);
                    ps.setObject(16, c.day());
                });

        // --- incremental aggregates (summed in memory first: one upsert per key) ---
        Map<LocalDate, long[]> perDay = new HashMap<>();          // events, sessions, session_ms
        Map<LocalDate, Integer> versionOfDay = new HashMap<>();
        Map<List<Object>, long[]> perFeature = new HashMap<>();    // (day, feature) -> uses, views, time_ms
        Map<List<Object>, long[]> perEvent = new HashMap<>();      // (day, name, dim1, dim2) -> events, ok, ko, ms, bytes
        Map<String, long[]> perQuestion = new HashMap<>();         // uuid -> answers, correct, ms
        Map<String, long[]> perContent = new HashMap<>();          // "kind|item" -> shown, correct, ms (docs/CONTENT-VALIDATION.md)
        for (Clean c : events) {
            long[] d = perDay.computeIfAbsent(c.day(), k -> new long[3]);
            d[0]++;
            if ("session_start".equals(c.name())) d[1]++;
            if ("session_end".equals(c.name()) && c.ms() != null) d[2] += c.ms();
            if (c.versionCode() != null) versionOfDay.put(c.day(), c.versionCode());
            switch (c.name()) {
                case "feature_used" -> perFeature.computeIfAbsent(List.of(c.day(), c.dim1()), k -> new long[3])[0]++;
                case "screen_view" -> perFeature.computeIfAbsent(List.of(c.day(), c.dim1()), k -> new long[3])[1]++;
                case "screen_time" -> perFeature.computeIfAbsent(List.of(c.day(), c.dim1()), k -> new long[3])[2] += c.ms() == null ? 0 : c.ms();
                case "quiz_answer" -> {
                    if (c.dim1() != null) {
                        long[] q = perQuestion.computeIfAbsent(c.dim1(), k -> new long[3]);
                        q[0]++;
                        if (Boolean.TRUE.equals(c.ok())) q[1]++;
                        if (c.ms() != null) q[2] += c.ms();
                    }
                }
                case "content_stat" -> {
                    try {
                        com.fasterxml.jackson.databind.JsonNode p = json.readTree(c.props());
                        if (c.dim1() != null && c.dim2() != null) {
                            long[] v = perContent.computeIfAbsent(c.dim1() + "|" + c.dim2(), k -> new long[3]);
                            v[0] += p.path("shown").asLong(); v[1] += Math.min(p.path("correct").asLong(), p.path("shown").asLong()); v[2] += p.path("ms").asLong();
                        }
                    } catch (java.io.IOException ignored) { /* props were written by validate(): cannot happen */ }
                }
                default -> { }
            }
            if (EventCatalog.EVENTS.get(c.name()).dayCounter()) {
                long[] v = perEvent.computeIfAbsent(List.of(c.day(), c.name(), nz(c.dim1()), nz(c.dim2())), k -> new long[5]);
                v[0]++;
                if (Boolean.TRUE.equals(c.ok())) v[1]++;
                if (Boolean.FALSE.equals(c.ok())) v[2]++;
                if (c.ms() != null) v[3] += c.ms();
                if (c.bytes() != null) v[4] += c.bytes();
            }
        }
        perDay.forEach((day, v) -> jdbc.update("""
                insert into kpi_device_day (stat_day, device_id, app, version_code, events, sessions, session_ms) values (?,?,?,?,?,?,?)
                on duplicate key update events = events + values(events), sessions = sessions + values(sessions),
                  session_ms = session_ms + values(session_ms), version_code = coalesce(values(version_code), version_code)""",
                day, device.id, app, versionOfDay.get(day), v[0], v[1], v[2]));
        perFeature.forEach((k, v) -> jdbc.update("""
                insert into kpi_feature_day (stat_day, device_id, app, feature, version_code, uses, views, time_ms) values (?,?,?,?,?,?,?,?)
                on duplicate key update uses = uses + values(uses), views = views + values(views), time_ms = time_ms + values(time_ms),
                  version_code = coalesce(values(version_code), version_code)""",
                k.get(0), device.id, app, k.get(1), versionOfDay.get((LocalDate) k.get(0)), v[0], v[1], v[2]));
        perEvent.forEach((k, v) -> jdbc.update("""
                insert into kpi_event_day (stat_day, app, name, dim1, dim2, events, ok, ko, sum_ms, sum_bytes) values (?,?,?,?,?,?,?,?,?,?)
                on duplicate key update events = events + values(events), ok = ok + values(ok), ko = ko + values(ko),
                  sum_ms = sum_ms + values(sum_ms), sum_bytes = sum_bytes + values(sum_bytes)""",
                k.get(0), app, k.get(1), k.get(2), k.get(3), v[0], v[1], v[2], v[3], v[4]));
        Timestamp t = Timestamp.from(now);
        perQuestion.forEach((q, v) -> jdbc.update("""
                insert into kpi_question (question_uuid, answers, correct, sum_ms, updated_at) values (?,?,?,?,?)
                on duplicate key update answers = answers + values(answers), correct = correct + values(correct),
                  sum_ms = sum_ms + values(sum_ms), updated_at = values(updated_at)""", q, v[0], v[1], v[2], t));
        castbridge.server.content.ContentService.addStats(jdbc, perContent, now);
    }

    // ================================================================ device side: access and erasure

    /** Right of access: what the server holds about this device's usage (most recent first, at most 5000 events). */
    public List<Map<String, Object>> events(long deviceId, int limit) {
        return jdbc.queryForList("""
                select event_id, name, app, version_code, session_id, props, device_ts, server_ts from telemetry_event
                where device_id = ? order by device_ts desc limit ?""", deviceId, Math.max(1, Math.min(5000, limit)));
    }

    // ================================================================ retention and nightly consolidation

    /**
     * Every night: raw events older than 13 months and per-device aggregates older than 25 months are deleted; the
     * anonymous daily counters of yesterday and today are rebuilt from the raw events (repairs any drift, e.g. after an
     * erasure or a replayed batch counted during a failure).
     */
    @Scheduled(cron = "0 45 3 * * *", zone = "Africa/Douala")
    @Transactional
    public void nightly() {
        LocalDate today = LocalDate.now(CastbridgeApplication.ZONE);
        int raw = jdbc.update("delete from telemetry_event where stat_day < ?", today.minusDays(RAW_DAYS));
        int dev = jdbc.update("delete from kpi_device_day where stat_day < ?", today.minusDays(DEVICE_AGG_DAYS));
        int feat = jdbc.update("delete from kpi_feature_day where stat_day < ?", today.minusDays(DEVICE_AGG_DAYS));
        for (LocalDate d : List.of(today.minusDays(1), today)) rebuildEventDay(d);
        log.info("telemetry retention: {} raw events, {} device-days, {} feature-days purged", raw, dev, feat);
    }

    void rebuildEventDay(LocalDate day) {
        jdbc.update("delete from kpi_event_day where stat_day = ?", day);
        List<String> counted = EventCatalog.EVENTS.values().stream().filter(EventCatalog.Def::dayCounter).map(EventCatalog.Def::name).toList();
        String marks = String.join(",", java.util.Collections.nCopies(counted.size(), "?"));
        List<Object> args = new ArrayList<>();
        args.add(day);
        args.addAll(counted);
        jdbc.update("""
                insert into kpi_event_day (stat_day, app, name, dim1, dim2, events, ok, ko, sum_ms, sum_bytes)
                select stat_day, app, name, coalesce(dim1, ''), coalesce(dim2, ''), count(*),
                  sum(case when ok = true then 1 else 0 end), sum(case when ok = false then 1 else 0 end),
                  coalesce(sum(num_ms), 0), coalesce(sum(num_bytes), 0)
                from telemetry_event where stat_day = ? and name in (""" + marks + ") group by stat_day, app, name, coalesce(dim1, ''), coalesce(dim2, '')",
                args.toArray());
    }

    // ================================================================ helpers

    private static Instant timestamp(JsonNode ts) {
        if (ts == null || ts.isNull()) return null;
        try {
            if (ts.isNumber()) return Instant.ofEpochMilli(ts.asLong());
            return OffsetDateTime.parse(ts.asText()).toInstant();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String text(JsonNode n, String k, String def) { return n.hasNonNull(k) ? n.get(k).asText() : def; }

    private static String str(ObjectNode n, String k) { return k == null || !n.has(k) ? null : n.get(k).asText(); }

    private static Long lng(ObjectNode n, String k) { return k == null || !n.has(k) ? null : n.get(k).asLong(); }

    private static String nz(String s) { return s == null ? "" : s; }
}
