package castbridge.server.activations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 100 000 activations and 1 000 000 synthetic events on H2: the list page and the TV fiche answer in under 200 ms (local indicative measure, the median of five
 * calls after a warm-up; the real figure is printed). The display path never counts the rows of act_event (no COUNT(*), no OFFSET).
 */
class VolumeTest extends ActTestBase {
    @Autowired TvRef tvRef;

    static final int KEYS = 100_000, EVENTS = 1_000_000, TVS = 10_000;

    @AfterEach
    void stop() { clock.reset(); }

    private void batch(String sql, int n, java.util.function.IntFunction<Object[]> row) {
        for (int from = 0; from < n; from += 10_000) {
            List<Object[]> args = new ArrayList<>();
            for (int i = from; i < Math.min(n, from + 10_000); i++) args.add(row.apply(i));
            jdbc.batchUpdate(sql, args);
        }
    }

    private long median(String url) throws Exception {
        long[] t = new long[5];
        mvc.perform(adminGet(url)).andReturn();   // warm-up
        for (int i = 0; i < 5; i++) {
            long s = System.nanoTime();
            assertEquals(200, mvc.perform(adminGet(url)).andReturn().getResponse().getStatus());
            t[i] = (System.nanoTime() - s) / 1_000_000;
        }
        Arrays.sort(t);
        return t[2];
    }

    @Test
    void theListPageAndTheTvFicheStayUnder200MsAtVolume() throws Exception {
        resetModule();
        Instant base = Instant.parse("2026-10-10T08:00:00Z");
        clock.set(base);
        List<String> refs = new ArrayList<>();
        Dev sample = dev();
        for (int i = 0; i < TVS; i++) refs.add(i == 0 ? tvRef.of(sample.code()) : "%016x".formatted(i));
        batch("insert into act_tv (tv_ref, device_code, edition, trial_resets, last_report_at, api_devices, android_ids, app_code, reco, alerts_open) values (?,?,?,0,?,0,0,1412,'OK',0)", TVS,
                i -> new Object[] {refs.get(i), i == 0 ? sample.code() : null, "PRODUCTION", Timestamp.from(base.minusSeconds(i))});
        batch("insert into act_key (fp, tag, form, kind, subject, kid, license_id, tv_ref, issued_at, expires_at, state, state_at, flags, super) values (?,?,'ENVELOPE',?,'tv',?,?,?,?,?,?,?,'',false)", KEYS,
                i -> {
                    String fp = "%064x".formatted(i);
                    return new Object[] {fp, fp.substring(56), i % 2 == 0 ? "TRIAL" : "PRODUCTION", "0011223344556677", "lic-vol" + (i % 5000), refs.get(i % TVS), Timestamp.from(base.minusSeconds(i)),
                            Timestamp.from(base.minusSeconds(i).plusSeconds(172800)), i % 3 == 0 ? "ACTIVATED" : "EMISE", Timestamp.from(base)};
                });
        batch("insert into act_event (id, at, recorded_at, type, fp, tv_ref, license_id, kid, actor_type, actor, source, before_json, after_json, idem_key, prev_hash, hash) values (?,?,?,'SEEN',?,?,null,null,'TV','tv','REPORT',null,null,?,?,?)",
                EVENTS, i -> new Object[] {(long) i + 1, Timestamp.from(base), Timestamp.from(base), "%064x".formatted(i % KEYS), refs.get(i % TVS), "v" + i, "%064x".formatted(i), "%064x".formatted(i + 1L)});
        jdbc.update("update act_event_head set last_id = ?, last_hash = ? where id = 1", (long) EVENTS, "%064x".formatted(EVENTS));

        long list = median("/api/v1/admin/activations/activations?limit=50&state=ACTIVATED");
        JsonNode page = body(mvc.perform(adminGet("/api/v1/admin/activations/activations?limit=500")).andReturn());
        assertEquals(500, page.get("items").size());
        JsonNode deepPage = body(mvc.perform(adminGet("/api/v1/admin/activations/activations?limit=50&cursor=" + page.get("nextCursor").asText())).andReturn());
        assertEquals(50, deepPage.get("items").size());
        JsonNode fiche = body(mvc.perform(adminGet("/api/v1/admin/activations/tvs/" + sample.code())).andReturn());
        assertEquals(EVENTS / TVS, fiche.get("timeline").size(), "the chronology of a TV among a million events: its own 100 events, read through its own index");
        long deep = median("/api/v1/admin/activations/activations?limit=50&cursor=" + page.get("nextCursor").asText());
        long tv = median("/api/v1/admin/activations/tvs/" + sample.code());
        long tvs = median("/api/v1/admin/activations/tvs?limit=50&freshness=fresh");
        long dash = median("/api/v1/admin/activations/dashboard");
        System.out.printf("VOLUME %d keys, %d events: list %d ms, deep page %d ms, tv fiche %d ms, tv list %d ms, dashboard %d ms%n", KEYS, EVENTS, list, deep, tv, tvs, dash);
        assertTrue(list < 200, "list " + list);
        assertTrue(deep < 200, "deep page " + deep);
        assertTrue(tv < 200, "fiche " + tv);
        assertTrue(tvs < 200, "tv list " + tvs);
        assertTrue(dash < 1000, "dashboard " + dash);
    }
}
