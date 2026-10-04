package castbridge.server.activations;

import java.sql.Date;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Snapshots for the history: counts of activations by kind, tool and state every night (00:05 Douala), one row per TV on the first of the month. A rerun replaces the day. */
@Service
public class Snapshots {
    private static final Logger log = LoggerFactory.getLogger(Snapshots.class);
    private static final ZoneId DOUALA = ZoneId.of("Africa/Douala");

    private final JdbcTemplate jdbc;
    private final ToolDirectory tools;
    private final ActClock clock;
    private final ActivationsProperties props;

    public Snapshots(JdbcTemplate jdbc, ToolDirectory tools, ActClock clock, ActivationsProperties props) {
        this.jdbc = jdbc;
        this.tools = tools;
        this.clock = clock;
        this.props = props;
    }

    @Scheduled(cron = "0 5 0 * * *", zone = "Africa/Douala")
    void nightly() {
        if (!props.enabled()) return;
        try {
            LocalDate today = clock.now().atZone(DOUALA).toLocalDate();
            snapshotDay(today);
            if (today.getDayOfMonth() == 1) snapshotMonth(YearMonth.from(today).minusMonths(1));
        } catch (RuntimeException e) {
            log.error("activation snapshot failed: {}", e.getClass().getSimpleName());
        }
    }

    @Transactional
    public void snapshotDay(LocalDate day) {
        Map<String, Integer> counts = new TreeMap<>();
        jdbc.query("SELECT kind, kid, state, COUNT(*) AS n FROM act_key GROUP BY kind, kid, state", rs -> {
            String key = rs.getString("kind") + "|" + tools.typeOf(rs.getString("kid")) + "|" + rs.getString("state");
            counts.merge(key, rs.getInt("n"), Integer::sum);
        });
        jdbc.update("DELETE FROM act_daily WHERE snap_day = ?", Date.valueOf(day));
        counts.forEach((k, n) -> {
            String[] p = k.split("\\|");
            jdbc.update("INSERT INTO act_daily (snap_day, kind, tool, state, n) VALUES (?,?,?,?,?)", Date.valueOf(day), p[0], p[1], p[2], n);
        });
    }

    @Transactional
    public void snapshotMonth(YearMonth month) {
        jdbc.update("DELETE FROM act_tv_monthly WHERE snap_month = ?", month.toString());
        jdbc.update("INSERT INTO act_tv_monthly (snap_month, tv_ref, edition, current_fp8, app_code, last_report_at, alerts_open)"
                + " SELECT ?, tv_ref, edition, SUBSTRING(current_fp, 1, 8), app_code, last_report_at, alerts_open FROM act_tv", month.toString());
    }
}
