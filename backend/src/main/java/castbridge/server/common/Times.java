package castbridge.server.common;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;

/**
 * ONE tolerant reading of a date-time column. Untyped JDBC reads ({@code queryForMap}, {@code queryForList}, native queries) give a {@code java.sql.Timestamp}
 * on H2 but a {@code java.time.LocalDateTime} on MySQL with Connector/J 9.x (DATETIME, {@code treatMysqlDatetimeAsTimestamp=false}): a raw {@code (Timestamp)}
 * cast works in the tests and fails in production. Every DATETIME the server writes is a UTC instant ({@code connectionTimeZone=UTC}), so a zone-less value is UTC.
 */
public final class Times {
    private Times() {}

    /** The instant of a date-time column value; null stays null. */
    public static Instant instant(Object v) {
        if (v == null) return null;
        if (v instanceof Timestamp t) return t.toInstant();
        if (v instanceof LocalDateTime l) return l.toInstant(ZoneOffset.UTC);
        if (v instanceof Instant i) return i;
        if (v instanceof OffsetDateTime o) return o.toInstant();
        if (v instanceof java.sql.Date d) return d.toLocalDate().atStartOfDay().toInstant(ZoneOffset.UTC);
        if (v instanceof LocalDate d) return d.atStartOfDay().toInstant(ZoneOffset.UTC);
        if (v instanceof Date d) return Instant.ofEpochMilli(d.getTime());
        throw new IllegalArgumentException("not a date-time value: " + v.getClass().getName());
    }

    /** Milliseconds since the epoch of a date-time column value; null stays null. */
    public static Long ms(Object v) {
        Instant i = instant(v);
        return i == null ? null : i.toEpochMilli();
    }

    /** The same value as a {@code Timestamp}, to bind it back as a parameter; null stays null. */
    public static Timestamp ts(Object v) {
        Instant i = instant(v);
        return i == null ? null : Timestamp.from(i);
    }

    /** A copy of a row where every date-time is an {@code Instant} and every DATE a {@code LocalDate}: the same JSON on H2 and on MySQL. */
    public static java.util.Map<String, Object> normalized(java.util.Map<String, Object> row) {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        row.forEach((k, v) -> m.put(k, v instanceof java.sql.Date d ? d.toLocalDate() : v instanceof Timestamp || v instanceof LocalDateTime || v instanceof OffsetDateTime ? instant(v) : v));
        return m;
    }
}
