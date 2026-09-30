package castbridge.server.telemetry;

import castbridge.server.CastbridgeApplication;
import castbridge.server.telemetry.Kpi.Chart;
import castbridge.server.telemetry.Kpi.Dataset;
import castbridge.server.telemetry.Kpi.Filter;
import castbridge.server.telemetry.Kpi.Section;
import castbridge.server.telemetry.Kpi.Table;
import castbridge.server.telemetry.Kpi.Tile;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * KPI computations for /admin. Parc and features come from the pre-computed tables (kpi_device_day,
 * kpi_feature_day, kpi_question: updated at ingestion); the detailed sections query the raw events of the period
 * (indexed by name and day, 13 months at most). Every query accepts the same filters (period, app, version,
 * platform, country, group, model); device attributes are the current ones of the device.
 */
@Service
public class KpiService {
    private final JdbcTemplate jdbc;

    public KpiService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    // ================================================================ SQL helpers

    /** where-clause + arguments for a fact table aliased x joined to device d. */
    private static final class Q {
        final StringBuilder sql = new StringBuilder();
        final List<Object> args = new ArrayList<>();

        Q(Filter f, String dayColumn, String appColumn, String versionColumn) {
            sql.append(" where ").append(dayColumn).append(" between ? and ?");
            args.add(Date.valueOf(f.from()));
            args.add(Date.valueOf(f.to()));
            and(f, appColumn, versionColumn);
        }

        Q(Filter f, String appColumn, String versionColumn) { // no period
            sql.append(" where 1 = 1");
            and(f, appColumn, versionColumn);
        }

        private void and(Filter f, String appColumn, String versionColumn) {
            if (f.app() != null) { sql.append(" and ").append(appColumn).append(" = ?"); args.add(f.app()); }
            if (f.version() != null) { sql.append(" and ").append(versionColumn).append(" = ?"); args.add(f.version()); }
            if (f.platform() != null) { sql.append(" and d.platform = ?"); args.add(f.platform()); }
            if (f.country() != null) { sql.append(" and d.country = ?"); args.add(f.country()); }
            if (f.group() != null) { sql.append(" and d.group_name = ?"); args.add(f.group()); }
            if (f.model() != null) { sql.append(" and d.model = ?"); args.add(f.model()); }
        }

        private Q() {}

        Q copy() {
            Q c = new Q();
            c.sql.append(sql);
            c.args.addAll(args);
            return c;
        }

        Q add(String clause, Object... values) {
            sql.append(" and ").append(clause);
            args.addAll(List.of(values));
            return this;
        }
    }

    private List<Map<String, Object>> rows(String select, Q q, String tail) {
        return jdbc.queryForList(select + q.sql + (tail == null ? "" : " " + tail), q.args.toArray());
    }

    private long count(String select, Q q) {
        Number n = jdbc.queryForObject(select + q.sql, Number.class, q.args.toArray());
        return n == null ? 0 : n.longValue();
    }

    private static long l(Object o) { return o == null ? 0 : ((Number) o).longValue(); }

    private static double dbl(Object o) { return o == null ? 0 : ((Number) o).doubleValue(); }

    private static String s(Object o) { return o == null ? "?" : o.toString(); }

    static String pct(double part, double total) { return total <= 0 ? "—" : String.format(Locale.FRENCH, "%.0f %%", 100 * part / total); }

    static String dur(long ms) {
        if (ms <= 0) return "0";
        long min = ms / 60_000;
        if (min < 1) return (ms / 1000) + " s";
        if (min < 60) return min + " min";
        long h = min / 60;
        return h < 48 ? h + " h " + (min % 60) + " min" : String.format(Locale.FRENCH, "%.0f h", ms / 3_600_000d);
    }

    static String bytes(long b) {
        if (b >= 1L << 30) return String.format(Locale.FRENCH, "%.1f Go", b / (double) (1L << 30));
        if (b >= 1L << 20) return String.format(Locale.FRENCH, "%.0f Mo", b / (double) (1L << 20));
        return String.format(Locale.FRENCH, "%.0f Ko", b / 1024d);
    }

    static String trend(long now, long before) {
        if (before == 0) return now == 0 ? "=" : "nouveau";
        double p = 100.0 * (now - before) / before;
        if (Math.abs(p) < 0.5) return "= 0 %";
        return (p > 0 ? "▲ +" : "▼ ") + String.format(Locale.FRENCH, "%.0f %%", p);
    }

    private long activeDevices(Filter f) {
        return count("select count(distinct x.device_id) from kpi_device_day x join device d on d.id = x.device_id",
                new Q(f, "x.stat_day", "x.app", "x.version_code"));
    }

    // ================================================================ features (home page)

    public record FeatureRow(String app, String feature, String label, long uses, long devices, String share, long timeMs, String time,
                             String avgTime, long previousUses, String trend) {}

    public record Features(Filter filter, long activeDevices, List<FeatureRow> rows, Chart ranking, Chart weekly) {}

    /** « Fonctionnalités les plus utilisées »: uses, distinct devices (% of the active fleet), time, trend vs the previous period. */
    public Features features(Filter f) {
        long active = activeDevices(f);
        String select = "select x.app as app, x.feature as feature, sum(x.uses) as uses, sum(x.views) as views, sum(x.time_ms) as ms, "
                + "count(distinct x.device_id) as devices from kpi_feature_day x join device d on d.id = x.device_id";
        Map<String, Map<String, Object>> now = new HashMap<>();
        for (Map<String, Object> r : rows(select, new Q(f, "x.stat_day", "x.app", "x.version_code"), "group by x.app, x.feature"))
            now.put(r.get("app") + "/" + r.get("feature"), r);
        Map<String, Long> before = new HashMap<>();
        for (Map<String, Object> r : rows(select, new Q(f.previous(), "x.stat_day", "x.app", "x.version_code"), "group by x.app, x.feature"))
            before.put(r.get("app") + "/" + r.get("feature"), l(r.get("uses")));

        List<FeatureRow> out = new ArrayList<>();
        for (String app : f.app() == null ? List.of("tv", "phone") : List.of(f.app())) {
            for (Map.Entry<String, String> feat : EventCatalog.features(app).entrySet()) {
                Map<String, Object> r = now.getOrDefault(app + "/" + feat.getKey(), Map.of());
                long uses = l(r.get("uses")), devices = l(r.get("devices")), ms = l(r.get("ms"));
                long prev = before.getOrDefault(app + "/" + feat.getKey(), 0L);
                out.add(new FeatureRow(app, feat.getKey(), feat.getValue(), uses, devices, pct(devices, active), ms, dur(ms),
                        devices == 0 ? "—" : dur(ms / devices), prev, trend(uses, prev)));
            }
        }
        out.sort(Comparator.comparingLong(FeatureRow::uses).thenComparingLong(FeatureRow::timeMs).reversed());

        List<FeatureRow> used = out.stream().filter(r -> r.uses() > 0).toList();
        Chart ranking = new Chart("features-ranking", "bar", "Utilisations sur la période",
                used.stream().map(r -> label(r, f)).toList(), List.of(new Dataset("Utilisations", used.stream().map(FeatureRow::uses).toList())), true);

        // weekly evolution of the 6 most used features
        List<FeatureRow> top = used.stream().limit(6).toList();
        Map<String, Map<LocalDate, Long>> series = new LinkedHashMap<>();
        top.forEach(r -> series.put(r.app() + "/" + r.feature(), new TreeMap<>()));
        List<LocalDate> weeks = weeks(f);
        if (!top.isEmpty()) {
            for (Map<String, Object> r : rows("select x.stat_day as dt, x.app as app, x.feature as feature, sum(x.uses) as uses "
                    + "from kpi_feature_day x join device d on d.id = x.device_id", new Q(f, "x.stat_day", "x.app", "x.version_code"),
                    "group by x.stat_day, x.app, x.feature")) {
                Map<LocalDate, Long> m = series.get(r.get("app") + "/" + r.get("feature"));
                if (m != null) m.merge(weekOf(((Date) r.get("dt")).toLocalDate()), l(r.get("uses")), Long::sum);
            }
        }
        List<Dataset> ds = new ArrayList<>();
        for (FeatureRow r : top) {
            Map<LocalDate, Long> m = series.get(r.app() + "/" + r.feature());
            ds.add(new Dataset(label(r, f), weeks.stream().map(w -> m.getOrDefault(w, 0L)).toList()));
        }
        Chart weekly = new Chart("features-weekly", "line", "Évolution par semaine (6 premières)",
                weeks.stream().map(w -> "sem. du " + w.getDayOfMonth() + "/" + w.getMonthValue()).toList(), ds, false);
        return new Features(f, active, out, ranking, weekly);
    }

    private static String label(FeatureRow r, Filter f) {
        return f.app() == null ? r.label() + (r.app().equals("tv") ? " (TV)" : " (tél.)") : r.label();
    }

    static LocalDate weekOf(LocalDate d) { return d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)); }

    static List<LocalDate> weeks(Filter f) {
        List<LocalDate> w = new ArrayList<>();
        for (LocalDate d = weekOf(f.from()); !d.isAfter(f.to()); d = d.plusWeeks(1)) w.add(d);
        return w;
    }

    private static List<LocalDate> days(Filter f) {
        List<LocalDate> out = new ArrayList<>();
        for (LocalDate d = f.from(); !d.isAfter(f.to()); d = d.plusDays(1)) out.add(d);
        return out;
    }

    private static String dayLabel(LocalDate d) { return d.getDayOfMonth() + "/" + d.getMonthValue(); }

    // ================================================================ sections

    public Section section(String key, Filter f) {
        return switch (key) {
            case "usage" -> usage(f);
            case "cast" -> cast(f);
            case "lecture" -> playback(f);
            case "quiz" -> quiz(f);
            case "echecs" -> chess(f);
            case "telechargements" -> downloads(f);
            case "mises-a-jour" -> updates(f);
            case "qualite" -> quality(f);
            case "connectivite" -> connectivity(f);
            default -> fleet(f);
        };
    }

    /** DAU/WAU/MAU, new and lost devices, retention by weekly cohort, distributions. */
    Section fleet(Filter f) {
        Section s = new Section("parc", "Parc d'appareils");
        LocalDate end = f.to();
        long dau = activeDevices(f.period(end, end)), wau = activeDevices(f.period(end.minusDays(6), end)),
                mau = activeDevices(f.period(end.minusDays(29), end)), active = activeDevices(f);
        long fresh = count("select count(*) from device d", new Q(f, "d.app", "d.version_code").add("d.first_seen >= ? and d.first_seen < ?",
                Timestamp.valueOf(f.from().atStartOfDay()), Timestamp.valueOf(end.plusDays(1).atStartOfDay())));
        // lost: active in the 30 days before the period, not during it
        Set<Long> before = ids(f.period(f.from().minusDays(30), f.from().minusDays(1)));
        Set<Long> during = ids(f);
        long lost = before.stream().filter(id -> !during.contains(id)).count();
        s.tiles().add(new Tile("Actifs le " + dayLabel(end) + " (DAU)", String.valueOf(dau), null));
        s.tiles().add(new Tile("Actifs sur 7 jours (WAU)", String.valueOf(wau), null));
        s.tiles().add(new Tile("Actifs sur 30 jours (MAU)", String.valueOf(mau), mau == 0 ? null : "DAU/MAU " + pct(dau, mau)));
        s.tiles().add(new Tile("Actifs sur la période", String.valueOf(active), null));
        s.tiles().add(new Tile("Nouveaux", String.valueOf(fresh), "premier contact dans la période"));
        s.tiles().add(new Tile("Perdus", String.valueOf(lost), "actifs les 30 j avant, plus depuis"));

        Map<LocalDate, Long> daily = new HashMap<>();
        for (Map<String, Object> r : rows("select x.stat_day as dt, count(distinct x.device_id) as n from kpi_device_day x join device d on d.id = x.device_id",
                new Q(f, "x.stat_day", "x.app", "x.version_code"), "group by x.stat_day"))
            daily.put(((Date) r.get("dt")).toLocalDate(), l(r.get("n")));
        List<LocalDate> ds = days(f);
        s.charts().add(new Chart("fleet-daily", "line", "Appareils actifs par jour", ds.stream().map(KpiService::dayLabel).toList(),
                List.of(new Dataset("Actifs", ds.stream().map(d -> daily.getOrDefault(d, 0L)).toList())), false));

        s.tables().add(retention(f));
        String base = "select %s as v, count(distinct x.device_id) as n from kpi_device_day x join device d on d.id = x.device_id";
        for (String[] dim : new String[][] {{"x.version_code", "Versions (versionCode)"}, {"d.platform", "Plateformes"},
                {"d.manufacturer", "Fabricants"}, {"d.abi", "ABI"}, {"d.screen", "Résolutions"}, {"d.country", "Pays"}}) {
            Table t = new Table(dim[1], List.of("Valeur", "Appareils", "Part"));
            for (Map<String, Object> r : rows(base.formatted(dim[0]), new Q(f, "x.stat_day", "x.app", "x.version_code"),
                    "group by " + dim[0] + " order by n desc"))
                t.rows().add(List.of(s(r.get("v")), l(r.get("n")), pct(l(r.get("n")), active)));
            s.tables().add(t);
        }
        return s;
    }

    private Set<Long> ids(Filter f) {
        return new HashSet<>(jdbc.queryForList("select distinct x.device_id from kpi_device_day x join device d on d.id = x.device_id"
                + new Q(f, "x.stat_day", "x.app", "x.version_code").sql, Long.class, new Q(f, "x.stat_day", "x.app", "x.version_code").args.toArray()));
    }

    /** Weekly cohorts of the devices whose first activity is in the period: share active at D+1, D+7..13, D+30..36. */
    Table retention(Filter f) {
        Table t = new Table("Rétention par cohorte (semaine du premier jour d'activité)", List.of("Cohorte", "Appareils", "J1", "J7", "J30"));
        Q q = new Q(f, "x.app", "x.version_code");
        Map<Long, List<LocalDate>> activity = new HashMap<>();
        for (Map<String, Object> r : rows("select x.device_id as id, x.stat_day as dt from kpi_device_day x join device d on d.id = x.device_id",
                q.add("x.stat_day <= ?", Date.valueOf(f.to().plusDays(40))), null))
            activity.computeIfAbsent(l(r.get("id")), k -> new ArrayList<>()).add(((Date) r.get("dt")).toLocalDate());
        Map<LocalDate, int[]> cohorts = new TreeMap<>(); // devices, d1, d7, d30, d1-possible, d7-possible, d30-possible
        LocalDate today = LocalDate.now(CastbridgeApplication.ZONE);
        for (List<LocalDate> days : activity.values()) {
            LocalDate first = days.stream().min(LocalDate::compareTo).orElseThrow();
            if (first.isBefore(f.from()) || first.isAfter(f.to())) continue;
            Set<LocalDate> set = new HashSet<>(days);
            int[] c = cohorts.computeIfAbsent(weekOf(first), k -> new int[7]);
            c[0]++;
            if (!first.plusDays(1).isAfter(today)) { c[4]++; if (set.contains(first.plusDays(1))) c[1]++; }
            if (!first.plusDays(13).isAfter(today)) { c[5]++; if (anyIn(set, first.plusDays(7), 7)) c[2]++; }
            if (!first.plusDays(36).isAfter(today)) { c[6]++; if (anyIn(set, first.plusDays(30), 7)) c[3]++; }
        }
        cohorts.forEach((w, c) -> t.rows().add(List.of("sem. du " + dayLabel(w), c[0],
                c[4] == 0 ? "—" : pct(c[1], c[4]), c[5] == 0 ? "—" : pct(c[2], c[5]), c[6] == 0 ? "—" : pct(c[3], c[6]))));
        return t.withNote("J1 = actif le lendemain ; J7 = actif entre J+7 et J+13 ; J30 = entre J+30 et J+36 ; « — » = trop récent.");
    }

    private static boolean anyIn(Set<LocalDate> days, LocalDate start, int len) {
        for (int i = 0; i < len; i++) if (days.contains(start.plusDays(i))) return true;
        return false;
    }

    /** Sessions per day, average duration, most viewed screens, funnel first contact → first send → first playback. */
    Section usage(Filter f) {
        Section s = new Section("usage", "Usage");
        Map<LocalDate, long[]> daily = new HashMap<>();
        long sessions = 0, sessionMs = 0;
        for (Map<String, Object> r : rows("select x.stat_day as dt, sum(x.sessions) as n, sum(x.session_ms) as ms from kpi_device_day x join device d on d.id = x.device_id",
                new Q(f, "x.stat_day", "x.app", "x.version_code"), "group by x.stat_day")) {
            daily.put(((Date) r.get("dt")).toLocalDate(), new long[] {l(r.get("n")), l(r.get("ms"))});
            sessions += l(r.get("n"));
            sessionMs += l(r.get("ms"));
        }
        s.tiles().add(new Tile("Sessions", String.valueOf(sessions), String.format(Locale.FRENCH, "%.1f par jour", sessions / (double) f.days())));
        s.tiles().add(new Tile("Durée moyenne d'une session", sessions == 0 ? "—" : dur(sessionMs / sessions), null));
        List<LocalDate> ds = days(f);
        s.charts().add(new Chart("usage-sessions", "bar", "Sessions par jour", ds.stream().map(KpiService::dayLabel).toList(),
                List.of(new Dataset("Sessions", ds.stream().map(d -> daily.getOrDefault(d, new long[2])[0]).toList())), false));

        Table screens = new Table("Écrans les plus vus", List.of("Écran", "App", "Affichages", "Temps total", "Appareils"));
        for (Map<String, Object> r : rows("select x.app as app, x.feature as screen, sum(x.views) as views, sum(x.time_ms) as ms, count(distinct x.device_id) as n "
                + "from kpi_feature_day x join device d on d.id = x.device_id", new Q(f, "x.stat_day", "x.app", "x.version_code").add("(x.views > 0 or x.time_ms > 0)"),
                "group by x.app, x.feature order by views desc"))
            screens.rows().add(List.of(EventCatalog.label(s(r.get("app")), s(r.get("screen"))), s(r.get("app")), l(r.get("views")), dur(l(r.get("ms"))), l(r.get("n"))));
        s.tables().add(screens);

        // funnel on the devices first seen in the period
        Q cohort = new Q(f, "d.app", "d.version_code").add("d.first_seen >= ? and d.first_seen < ?",
                Timestamp.valueOf(f.from().atStartOfDay()), Timestamp.valueOf(f.to().plusDays(1).atStartOfDay()));
        long step1 = count("select count(*) from device d", cohort);
        long step2 = count("select count(distinct e.device_id) from telemetry_event e join device d on d.id = e.device_id",
                cohort.copy().add("e.name = 'cast_end' and e.ok = true"));
        long step3 = count("select count(distinct e.device_id) from telemetry_event e join device d on d.id = e.device_id",
                cohort.copy().add("e.name = 'playback_start'"));
        Table funnel = new Table("Entonnoir des nouveaux appareils de la période", List.of("Étape", "Appareils", "Part"));
        funnel.rows().add(List.of("Première connexion", step1, pct(step1, step1)));
        funnel.rows().add(List.of("Premier envoi réussi", step2, pct(step2, step1)));
        funnel.rows().add(List.of("Première lecture", step3, pct(step3, step1)));
        s.tables().add(funnel.withNote("Envoi et lecture comptés seulement pour les appareils ayant accepté les statistiques d'usage."));
        return s;
    }

    private Q events(Filter f, String name) {
        return new Q(f, "e.stat_day", "e.app", "e.version_code").add("e.name = ?", name);
    }

    private static final String EV = " from telemetry_event e join device d on d.id = e.device_id";

    Section cast(Filter f) {
        Section s = new Section("cast", "Envois vers la TV");
        Table ch = new Table("Par canal", List.of("Canal", "Envois", "Volume", "Débit moyen", "Échecs", "Taux d'échec"));
        long total = 0, vol = 0, ko = 0;
        List<String> labels = new ArrayList<>();
        List<Long> counts = new ArrayList<>();
        for (Map<String, Object> r : rows("select e.dim1 as ch, count(*) as n, sum(e.num_bytes) as b, sum(e.num_ms) as ms, "
                + "sum(case when e.ok = false then 1 else 0 end) as ko" + EV, events(f, "cast_end"), "group by e.dim1 order by n desc")) {
            long n = l(r.get("n")), b = l(r.get("b")), ms = l(r.get("ms")), k = l(r.get("ko"));
            total += n; vol += b; ko += k;
            ch.rows().add(List.of(s(r.get("ch")), n, bytes(b), ms == 0 ? "—" : String.format(Locale.FRENCH, "%.0f ko/s", b / 1024.0 / (ms / 1000.0)), k, pct(k, n)));
            labels.add(s(r.get("ch")));
            counts.add(n);
        }
        s.tiles().add(new Tile("Envois", String.valueOf(total), null));
        s.tiles().add(new Tile("Volume transféré", bytes(vol), null));
        s.tiles().add(new Tile("Taux d'échec", pct(ko, total), null));
        s.charts().add(new Chart("cast-channels", "bar", "Envois par canal", labels, List.of(new Dataset("Envois", counts)), false));
        s.tables().add(ch);
        Table mode = new Table("Par mode", List.of("Mode", "Envois", "Volume"));
        for (Map<String, Object> r : rows("select e.dim2 as m, count(*) as n, sum(e.num_bytes) as b" + EV, events(f, "cast_end"), "group by e.dim2 order by n desc"))
            mode.rows().add(List.of(s(r.get("m")), l(r.get("n")), bytes(l(r.get("b")))));
        s.tables().add(mode);
        s.tables().add(byModel(f, "cast_end", "Échecs d'envoi par modèle"));
        return s;
    }

    private Table byModel(Filter f, String event, String title) {
        Table t = new Table(title, List.of("Modèle", "Événements", "Échecs", "Taux d'échec"));
        for (Map<String, Object> r : rows("select d.manufacturer as mf, d.model as model, count(*) as n, sum(case when e.ok = false then 1 else 0 end) as ko" + EV,
                events(f, event), "group by d.manufacturer, d.model order by ko desc, n desc"))
            t.rows().add(List.of((r.get("mf") == null ? "" : r.get("mf") + " ") + s(r.get("model")), l(r.get("n")), l(r.get("ko")), pct(l(r.get("ko")), l(r.get("n")))));
        return t;
    }

    Section playback(Filter f) {
        Section s = new Section("lecture", "Lecture");
        Map<String, Object> tot = jdbc.queryForMap("select count(*) as n, sum(e.num_ms) as ms, avg(e.num_value) as pct, "
                + "sum(case when e.ok = false then 1 else 0 end) as ko" + EV + events(f, "playback_end").sql, events(f, "playback_end").args.toArray());
        s.tiles().add(new Tile("Lectures", String.valueOf(l(tot.get("n"))), null));
        s.tiles().add(new Tile("Heures regardées", String.format(Locale.FRENCH, "%.1f", l(tot.get("ms")) / 3_600_000d), null));
        s.tiles().add(new Tile("Part moyenne vue", tot.get("pct") == null ? "—" : String.format(Locale.FRENCH, "%.0f %%", dbl(tot.get("pct"))), null));
        s.tiles().add(new Tile("Erreurs de lecture", String.valueOf(l(tot.get("ko"))), pct(l(tot.get("ko")), l(tot.get("n")))));
        for (String[] dim : new String[][] {{"e.dim1", "Codecs"}, {"e.dim2", "Résolutions"}}) {
            Table t = new Table(dim[1], List.of("Valeur", "Lectures", "Heures"));
            for (Map<String, Object> r : rows("select " + dim[0] + " as v, count(*) as n, sum(e.num_ms) as ms" + EV, events(f, "playback_end"),
                    "group by " + dim[0] + " order by n desc"))
                t.rows().add(List.of(s(r.get("v")), l(r.get("n")), String.format(Locale.FRENCH, "%.1f", l(r.get("ms")) / 3_600_000d)));
            s.tables().add(t);
        }
        s.tables().add(byModel(f, "playback_end", "Erreurs de lecture par modèle"));
        return s;
    }

    Section quiz(Filter f) {
        Section s = new Section("quiz", "Quiz");
        Map<LocalDate, Long> perDay = new HashMap<>();
        long games = 0;
        double players = 0;
        for (Map<String, Object> r : rows("select e.stat_day as dt, count(*) as n, sum(e.num_value) as p" + EV, events(f, "quiz_game"), "group by e.stat_day")) {
            perDay.put(((Date) r.get("dt")).toLocalDate(), l(r.get("n")));
            games += l(r.get("n"));
            players += dbl(r.get("p"));
        }
        s.tiles().add(new Tile("Parties", String.valueOf(games), String.format(Locale.FRENCH, "%.1f par jour", games / (double) f.days())));
        s.tiles().add(new Tile("Joueurs par partie", games == 0 ? "—" : String.format(Locale.FRENCH, "%.1f", players / games), null));
        List<LocalDate> ds = days(f);
        s.charts().add(new Chart("quiz-daily", "bar", "Parties par jour", ds.stream().map(KpiService::dayLabel).toList(),
                List.of(new Dataset("Parties", ds.stream().map(d -> perDay.getOrDefault(d, 0L)).toList())), false));
        Table modes = new Table("Modes et parcours", List.of("Mode", "Parcours", "Parties"));
        for (Map<String, Object> r : rows("select e.dim1 as m, e.dim2 as t, count(*) as n" + EV, events(f, "quiz_game"), "group by e.dim1, e.dim2 order by n desc"))
            modes.rows().add(List.of(s(r.get("m")), s(r.get("t")), l(r.get("n"))));
        s.tables().add(modes);

        // answers per question (anonymous totals since the beginning, all devices)
        List<Map<String, Object>> qs = jdbc.queryForList("select k.question_uuid as id, k.answers as n, k.correct as ok, k.sum_ms as ms, q.question_text as text "
                + "from kpi_question k left join question q on q.uuid = k.question_uuid where k.answers >= ? order by k.answers desc", 10);
        long answers = qs.stream().mapToLong(r -> l(r.get("n"))).sum(), correct = qs.stream().mapToLong(r -> l(r.get("ok"))).sum();
        long ms = qs.stream().mapToLong(r -> l(r.get("ms"))).sum();
        s.tiles().add(new Tile("Bonnes réponses", pct(correct, answers), answers + " réponses (questions à ≥ 10 réponses)"));
        s.tiles().add(new Tile("Temps de réponse moyen", answers == 0 ? "—" : String.format(Locale.FRENCH, "%.1f s", ms / 1000.0 / answers), null));
        Table easy = new Table("Trop faciles (≥ 90 % de bonnes réponses)", List.of("Question", "Id", "Réponses", "Bonnes", "Temps moyen"));
        Table hard = new Table("Trop difficiles ou à revoir (≤ 25 %)", List.of("Question", "Id", "Réponses", "Bonnes", "Temps moyen"));
        for (Map<String, Object> r : qs) {
            double rate = l(r.get("ok")) / (double) l(r.get("n"));
            List<Object> row = List.of(r.get("text") == null ? "(supprimée)" : r.get("text"), s(r.get("id")), l(r.get("n")), pct(l(r.get("ok")), l(r.get("n"))),
                    String.format(Locale.FRENCH, "%.1f s", l(r.get("ms")) / 1000.0 / l(r.get("n"))));
            if (rate >= 0.9) easy.rows().add(row);
            else if (rate <= 0.25) hard.rows().add(row);
        }
        s.tables().add(easy.withNote("Tous appareils confondus, depuis le début (compteurs anonymes)."));
        s.tables().add(hard);
        return s;
    }

    Section chess(Filter f) {
        Section s = new Section("echecs", "Échecs");
        Table t = new Table("Parties", List.of("Mode", "Résultat", "Parties", "Coups moyens", "Durée moyenne"));
        long games = 0;
        for (Map<String, Object> r : rows("select e.dim1 as m, e.dim2 as res, count(*) as n, avg(e.num_value) as moves, avg(e.num_ms) as ms" + EV,
                events(f, "chess_game"), "group by e.dim1, e.dim2 order by n desc")) {
            games += l(r.get("n"));
            t.rows().add(List.of(s(r.get("m")), s(r.get("res")), l(r.get("n")), String.format(Locale.FRENCH, "%.0f", dbl(r.get("moves"))), dur((long) dbl(r.get("ms")))));
        }
        s.tiles().add(new Tile("Parties", String.valueOf(games), null));
        Table lv = new Table("Niveaux de l'IA", List.of("Niveau", "Parties"));
        for (Map<String, Object> r : rows("select e.props as p" + EV, events(f, "chess_game"), null).stream()
                .collect(Collectors.groupingBy(r -> level(s(r.get("p"))), TreeMap::new, Collectors.counting())).entrySet().stream()
                .map(en -> Map.<String, Object>of("l", en.getKey(), "n", en.getValue())).toList())
            lv.rows().add(List.of(s(r.get("l")), l(r.get("n"))));
        s.tables().add(t);
        s.tables().add(lv);
        return s;
    }

    private static String level(String props) {
        int i = props.indexOf("\"ai_level\":");
        if (i < 0) return "—";
        String rest = props.substring(i + 11);
        return rest.replaceAll("^(\\d+).*$", "$1");
    }

    Section downloads(Filter f) {
        Section s = new Section("telechargements", "Téléchargements");
        Table t = new Table("Par type", List.of("Type", "Téléchargements", "Réussis", "Taux de réussite", "Volume", "Durée moyenne"));
        long n0 = 0, ok0 = 0;
        for (Map<String, Object> r : rows("select e.dim1 as t, count(*) as n, sum(case when e.ok = true then 1 else 0 end) as ok, sum(e.num_bytes) as b, avg(e.num_ms) as ms" + EV,
                events(f, "download"), "group by e.dim1 order by n desc")) {
            n0 += l(r.get("n")); ok0 += l(r.get("ok"));
            t.rows().add(List.of(s(r.get("t")), l(r.get("n")), l(r.get("ok")), pct(l(r.get("ok")), l(r.get("n"))), bytes(l(r.get("b"))), dur((long) dbl(r.get("ms")))));
        }
        s.tiles().add(new Tile("Téléchargements", String.valueOf(n0), "réussite " + pct(ok0, n0)));
        s.tables().add(t);
        return s;
    }

    Section updates(Filter f) {
        Section s = new Section("mises-a-jour", "Mises à jour");
        long active = activeDevices(f);
        Table adoption = new Table("Adoption des versions (appareils actifs, version actuelle)", List.of("App", "Version", "Appareils", "Part"));
        List<String> labels = new ArrayList<>();
        List<Long> counts = new ArrayList<>();
        for (Map<String, Object> r : rows("select d.app as app, d.version_code as v, d.version_name as vn, count(distinct x.device_id) as n "
                + "from kpi_device_day x join device d on d.id = x.device_id", new Q(f, "x.stat_day", "x.app", "x.version_code"),
                "group by d.app, d.version_code, d.version_name order by d.app, d.version_code desc")) {
            String v = s(r.get("vn")) + " (" + s(r.get("v")) + ")";
            adoption.rows().add(List.of(s(r.get("app")), v, l(r.get("n")), pct(l(r.get("n")), active)));
            labels.add(s(r.get("app")) + " " + v);
            counts.add(l(r.get("n")));
        }
        s.charts().add(new Chart("updates-adoption", "bar", "Appareils par version", labels, List.of(new Dataset("Appareils", counts)), true));
        s.tables().add(adoption);
        Table inst = new Table("Installations de mises à jour", List.of("Vers la version", "Tentatives", "Échecs", "Taux d'échec"));
        long fails = 0;
        for (Map<String, Object> r : rows("select e.dim1 as v, count(*) as n, sum(case when e.ok = false then 1 else 0 end) as ko" + EV,
                events(f, "update_install"), "group by e.dim1 order by n desc")) {
            fails += l(r.get("ko"));
            inst.rows().add(List.of(s(r.get("v")), l(r.get("n")), l(r.get("ko")), pct(l(r.get("ko")), l(r.get("n")))));
        }
        s.tiles().add(new Tile("Échecs d'installation", String.valueOf(fails), null));
        s.tables().add(inst);
        return s;
    }

    Section quality(Filter f) {
        Section s = new Section("qualite", "Qualité");
        long active = activeDevices(f);
        Timestamp from = Timestamp.valueOf(f.from().atStartOfDay()), to = Timestamp.valueOf(f.to().plusDays(1).atStartOfDay());
        Q crashes = new Q(f, "d.app", "c.version_code").add("c.crashed_at >= ? and c.crashed_at < ?", from, to);
        Set<Long> withErrors = new HashSet<>(jdbc.queryForList("select distinct c.device_id from device_crash c join device d on d.id = c.device_id"
                + crashes.sql, Long.class, crashes.args.toArray()));
        Q errs = new Q(f, "e.stat_day", "e.app", "e.version_code").add("e.name in ('error', 'crash')");
        withErrors.addAll(jdbc.queryForList("select distinct e.device_id" + EV + errs.sql, Long.class, errs.args.toArray()));
        Set<Long> activeIds = ids(f);
        long clean = activeIds.stream().filter(id -> !withErrors.contains(id)).count();
        s.tiles().add(new Tile("Appareils sans erreur", pct(clean, active), clean + " sur " + active + " actifs"));
        Table byVersion = new Table("Plantages par version", List.of("Version", "Plantages", "Appareils touchés"));
        for (Map<String, Object> r : rows("select c.version_code as v, count(*) as n, count(distinct c.device_id) as dev from device_crash c join device d on d.id = c.device_id",
                crashes, "group by c.version_code order by n desc"))
            byVersion.rows().add(List.of(s(r.get("v")), l(r.get("n")), l(r.get("dev"))));
        Table byModel = new Table("Plantages par modèle", List.of("Modèle", "Plantages", "Appareils touchés"));
        for (Map<String, Object> r : rows("select d.manufacturer as mf, d.model as m, count(*) as n, count(distinct c.device_id) as dev from device_crash c join device d on d.id = c.device_id",
                crashes, "group by d.manufacturer, d.model order by n desc"))
            byModel.rows().add(List.of((r.get("mf") == null ? "" : r.get("mf") + " ") + s(r.get("m")), l(r.get("n")), l(r.get("dev"))));
        Table types = new Table("Erreurs signalées (événements)", List.of("Écran", "Type", "Nombre"));
        for (Map<String, Object> r : rows("select e.dim1 as sc, e.dim2 as t, count(*) as n" + EV, errs, "group by e.dim1, e.dim2 order by n desc"))
            types.rows().add(List.of(s(r.get("sc")), s(r.get("t")), l(r.get("n"))));
        s.tables().add(byVersion);
        s.tables().add(byModel);
        s.tables().add(types);
        return s;
    }

    Section connectivity(Filter f) {
        Section s = new Section("connectivite", "Connectivité");
        long active = activeDevices(f);
        Table paths = new Table("Tests de connexion par chemin", List.of("Chemin", "Tests", "Réussis", "Latence moyenne"));
        // devices whose every check of the period failed = no Internet
        List<Map<String, Object>> perDevice = rows("select e.device_id as id, max(case when e.ok = true then 1 else 0 end) as anyok" + EV,
                events(f, "connectivity_check"), "group by e.device_id");
        for (Map<String, Object> r : rows("select e.dim1 as p, count(*) as n, sum(case when e.ok = true then 1 else 0 end) as ok, avg(e.num_value) as lat" + EV,
                events(f, "connectivity_check"), "group by e.dim1 order by n desc"))
            paths.rows().add(List.of(s(r.get("p")), l(r.get("n")), l(r.get("ok")), r.get("lat") == null ? "—" : String.format(Locale.FRENCH, "%.0f ms", dbl(r.get("lat")))));
        long offline = perDevice.stream().filter(r -> l(r.get("anyok")) == 0).count();
        s.tiles().add(new Tile("Appareils sans Internet", pct(offline, perDevice.size()), offline + " sur " + perDevice.size() + " testés"));
        Map<String, Object> gw = jdbc.queryForMap("select count(distinct e.device_id) as n, sum(e.num_bytes) as b, sum(e.num_ms) as ms" + EV
                + events(f, "gateway_session").sql, events(f, "gateway_session").args.toArray());
        s.tiles().add(new Tile("Passerelle Bluetooth utilisée", pct(l(gw.get("n")), active), l(gw.get("n")) + " appareils, " + bytes(l(gw.get("b")))
                + ", " + dur(l(gw.get("ms")))));
        s.tables().add(paths);
        return s;
    }

    /** CSV of a section's tables (and of the features ranking). */
    public static String csv(List<Table> tables) {
        StringBuilder sb = new StringBuilder();
        for (Table t : tables) {
            sb.append(cell(t.title())).append("\r\n");
            sb.append(t.columns().stream().map(KpiService::cell).collect(Collectors.joining(";"))).append("\r\n");
            for (List<Object> r : t.rows()) sb.append(r.stream().map(o -> cell(String.valueOf(o))).collect(Collectors.joining(";"))).append("\r\n");
            sb.append("\r\n");
        }
        return sb.toString();
    }

    private static String cell(String s) {
        return s.contains(";") || s.contains("\"") || s.contains("\n") ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }

    /** Timeline of a device's events for its page. */
    public List<Map<String, Object>> timeline(long deviceId, int limit) {
        return jdbc.queryForList("select name, dim1, dim2, num_ms, ok, props, version_code, device_ts, server_ts from telemetry_event "
                + "where device_id = ? order by device_ts desc limit ?", deviceId, limit);
    }
}
