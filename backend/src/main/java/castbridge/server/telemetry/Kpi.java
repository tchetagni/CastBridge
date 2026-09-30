package castbridge.server.telemetry;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Presentation model of the KPI pages: generic tiles, charts and tables (also exported as CSV). */
public final class Kpi {
    private Kpi() {}

    /** Filters of every KPI query. Dates are Cameroon days, both inclusive. */
    public record Filter(LocalDate from, LocalDate to, String app, Integer version, String platform, String country, String group,
                         String model) {
        public long days() { return to.toEpochDay() - from.toEpochDay() + 1; }

        public Filter previous() { return new Filter(from.minusDays(days()), from.minusDays(1), app, version, platform, country, group, model); }

        public Filter withApp(String a) { return new Filter(from, to, a, version, platform, country, group, model); }

        public Filter period(LocalDate f, LocalDate t) { return new Filter(f, t, app, version, platform, country, group, model); }
    }

    public record Tile(String label, String value, String hint) {}

    public record Dataset(String label, List<? extends Number> data) {}

    /** type = bar | line; horizontal bars for sorted rankings. */
    public record Chart(String id, String type, String title, List<String> labels, List<Dataset> datasets, boolean horizontal) {
        private static final com.fasterxml.jackson.databind.ObjectMapper JSON = new com.fasterxml.jackson.databind.ObjectMapper();

        /** Data for the canvas attribute (HTML-escaped by Thymeleaf, drawn by admin.js). */
        @com.fasterxml.jackson.annotation.JsonIgnore
        public String getJson() {
            try {
                return JSON.writeValueAsString(java.util.Map.of("type", type, "labels", labels, "datasets", datasets, "horizontal", horizontal));
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                return "{}";
            }
        }
    }

    public record Table(String title, List<String> columns, List<List<Object>> rows, String note) {
        public Table(String title, List<String> columns) { this(title, columns, new ArrayList<>(), null); }

        public Table withNote(String n) { return new Table(title, columns, rows, n); }
    }

    public record Section(String key, String title, List<Tile> tiles, List<Chart> charts, List<Table> tables) {
        public Section(String key, String title) { this(key, title, new ArrayList<>(), new ArrayList<>(), new ArrayList<>()); }
    }

    public static final List<List<String>> SECTIONS = List.of(
            List.of("parc", "Parc"), List.of("usage", "Usage"), List.of("cast", "Envois (cast)"), List.of("lecture", "Lecture"),
            List.of("quiz", "Quiz"), List.of("echecs", "Échecs"), List.of("telechargements", "Téléchargements"),
            List.of("mises-a-jour", "Mises à jour"), List.of("qualite", "Qualité"), List.of("connectivite", "Connectivité"));
}
