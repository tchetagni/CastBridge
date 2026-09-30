package castbridge.server.admin;

import castbridge.server.CastbridgeApplication;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Formatting helpers for the admin pages, called as ${@fmt.date(x)} (French, Cameroon time). */
@Component("fmt")
public class Fmt {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.FRENCH);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE dd/MM", Locale.FRENCH);
    private static final Map<String, String> PLATFORMS = Map.of("android-tv", "Android TV", "google-tv", "Google TV", "fire-os", "Fire OS",
            "android-box", "Box Android", "phone", "Téléphone", "tablet", "Tablette", "other", "Autre");

    public String date(Instant i) {
        return i == null ? "—" : DATE_TIME.format(i.atZone(CastbridgeApplication.ZONE));
    }

    public String date(OffsetDateTime t) {
        return t == null ? "—" : DATE_TIME.format(t.atZoneSameInstant(CastbridgeApplication.ZONE));
    }

    public String day(LocalDate d) {
        return d == null ? "—" : DAY.format(d);
    }

    /** "il y a 3 min", "il y a 2 h", "il y a 5 j". */
    public String ago(Instant i) {
        if (i == null) return "jamais";
        long s = Math.max(0, Duration.between(i, Instant.now()).getSeconds());
        if (s < 60) return "à l'instant";
        if (s < 3600) return "il y a " + s / 60 + " min";
        if (s < 86400) return "il y a " + s / 3600 + " h";
        return "il y a " + s / 86400 + " j";
    }

    public String mb(Integer mb) {
        if (mb == null) return "—";
        return mb >= 1024 ? String.format(Locale.FRENCH, "%.1f Go", mb / 1024.0) : mb + " Mo";
    }

    public String bytes(long b) {
        if (b >= 1 << 20) return String.format(Locale.FRENCH, "%.1f Mo", b / 1048576.0);
        if (b >= 1024) return String.format(Locale.FRENCH, "%.0f Ko", b / 1024.0);
        return b + " o";
    }

    public String yesNo(Boolean b) {
        return b == null ? "—" : b ? "oui" : "non";
    }

    public String platform(String p) {
        return p == null ? "—" : PLATFORMS.getOrDefault(p, p);
    }

    public String orDash(Object o) {
        return o == null || o.toString().isBlank() ? "—" : o.toString();
    }

    public String duration(Number ms) {
        if (ms == null) return "";
        long s = ms.longValue() / 1000;
        if (s < 60) return s + " s";
        if (s < 3600) return s / 60 + " min " + s % 60 + " s";
        return s / 3600 + " h " + (s % 3600) / 60 + " min";
    }

    /** CSS class of a trend text ("▲ +12 %", "▼ -5 %"). */
    public String trendClass(String t) {
        return t == null ? "" : t.startsWith("▲") ? "up" : t.startsWith("▼") ? "down" : "";
    }

    /** Used-space percentage for a meter (0..100), null if unknown. */
    public Integer usedPct(Integer free, Integer total) {
        if (free == null || total == null || total <= 0) return null;
        return Math.max(0, Math.min(100, (int) Math.round(100.0 * (total - free) / total)));
    }
}
