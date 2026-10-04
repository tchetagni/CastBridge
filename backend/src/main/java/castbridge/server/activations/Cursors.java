package castbridge.server.activations;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

/** Reading cursors on the licence tables (act_cursor): the id of the last row already copied, per table. */
final class Cursors {
    private Cursors() {}

    /** True when the row is too recent to be final: the tap stops there and leaves it, and every row after it, to the next turn (audit M7). */
    static boolean recent(ActClock clock, Object ts) {
        if (clock.tapLag().isZero()) return false;
        java.time.Instant t = castbridge.server.common.Times.instant(ts);
        return t != null && t.isAfter(clock.now().minus(clock.tapLag()));
    }

    static long get(JdbcTemplate jdbc, String name) {
        List<Long> r = jdbc.queryForList("SELECT val FROM act_cursor WHERE name = ?", Long.class, name);
        return r.isEmpty() ? 0L : r.get(0);
    }

    static void set(JdbcTemplate jdbc, String name, long value) {
        if (jdbc.update("UPDATE act_cursor SET val = ? WHERE name = ?", value, name) == 0) jdbc.update("INSERT INTO act_cursor (name, val) VALUES (?,?)", name, value);
    }
}
