package castbridge.server.activations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** The read API: cursor pagination (no OFFSET), filters, fiches, dashboard. Rows are seeded in SQL (the ingestion paths have their own tests). */
class ApiListTest extends ActTestBase {
    @Autowired TvRef tvRef;
    @Autowired EventLog eventLog;
    @Autowired PlatformTransactionManager tx;

    static final String API = "/api/v1/admin/activations";
    Instant base;
    final List<Dev> devs = new ArrayList<>();

    @BeforeEach
    void seed() {
        resetModule();
        base = Instant.parse("2026-10-10T08:00:00Z");
        clock.set(base);
        devs.clear();
        for (int i = 0; i < 40; i++) devs.add(dev());
        for (int i = 0; i < 40; i++) {
            Dev d = devs.get(i);
            String edition = i % 4 == 0 ? "PRODUCTION" : i % 4 == 1 ? "TRIAL" : i % 4 == 2 ? "ENDED" : "NONE";
            Timestamp last = switch (i % 5) {
                case 0 -> Timestamp.from(base.minus(Duration.ofHours(3)));        // fresh
                case 1 -> Timestamp.from(base.minus(Duration.ofDays(3)));         // stale
                case 2 -> Timestamp.from(base.minus(Duration.ofDays(20)));        // old
                default -> null;                                                  // never
            };
            jdbc.update("insert into act_tv (tv_ref, device_code, edition, trial_resets, last_report_at, last_report_via, api_devices, android_ids, app_code, app_name, reco, alerts_open)"
                    + " values (?,?,?,0,?,?,0,0,?,?,?,0)", tvRef.of(d.code()), d.code(), edition, last, last == null ? null : "direct", i < 20 ? 1412 : 1300, i < 20 ? "0.14.12" : "0.13.0", last == null ? "NEVER" : "OK");
        }
        for (int i = 0; i < 120; i++) {
            String fp = sha256("k" + i);
            String state = switch (i % 4) { case 0 -> "ACTIVATED"; case 1 -> "EMISE"; case 2 -> "EXPIRED_UNUSED"; default -> "TERMINEE"; };
            jdbc.update("insert into act_key (fp, tag, form, kind, subject, kid, license_id, tv_ref, issued_at, expires_at, state, state_at, flags, last_seen_tv_at, super)"
                            + " values (?,?,'ENVELOPE',?,'tv',?,?,?,?,?,?,?,?,?,false)",
                    fp, fp.substring(0, 8), i % 2 == 0 ? "TRIAL" : "PRODUCTION", i % 3 == 0 ? kid(PHONE) : kid(DESK), "lic-seed" + (i / 10), tvRef.of(devs.get(i % 40).code()),
                    Timestamp.from(base.minus(Duration.ofMinutes(i / 3))), Timestamp.from(base.plus(Duration.ofHours(48)).minus(Duration.ofMinutes(i / 3))), state, Timestamp.from(base),
                    i % 10 == 0 ? ",seen_on_tv,undeclared," : i % 10 == 1 ? ",declared_journal," : "", state.equals("ACTIVATED") ? Timestamp.from(base.minus(Duration.ofHours(i))) : null);
        }
        jdbc.update("update act_key set tv_ref = null where fp = ?", sha256("k5"));
    }

    @AfterEach
    void stop() { clock.reset(); }

    private JsonNode get(String url) throws Exception {
        MvcResult r = mvc.perform(adminGet(API + url)).andReturn();
        assertEquals(200, r.getResponse().getStatus(), url + " " + r.getResponse().getContentAsString());
        return body(r);
    }

    @Test
    void pagingByCursorReturnsEveryRowOnceInOrderEvenWithEqualSortValues() throws Exception {
        Set<String> seen = new HashSet<>();
        String cursor = null;
        String lastKey = null;
        int pages = 0;
        do {
            JsonNode p = get("/activations?limit=50" + (cursor == null ? "" : "&cursor=" + cursor));
            assertEquals(50, p.get("limit").asInt());
            for (JsonNode it : p.get("items")) {
                assertTrue(seen.add(it.get("fp").asText()), "a row appeared twice");
                String key = it.get("issuedAt").asText() + "|" + it.get("fp").asText();
                if (lastKey != null) assertTrue(key.compareTo(lastKey) <= 0, "descending order: " + key + " after " + lastKey);
                lastKey = key;
            }
            cursor = p.get("nextCursor").isNull() ? null : p.get("nextCursor").asText();
            pages++;
        } while (cursor != null);
        assertEquals(120, seen.size());
        assertEquals(3, pages);
    }

    @Test
    void theLimitIsCappedAt500AndABadCursorIs400() throws Exception {
        JsonNode p = get("/activations?limit=1000");
        assertEquals(500, p.get("limit").asInt());
        assertEquals(120, p.get("items").size());
        assertTrue(p.get("nextCursor").isNull());
        mvc.perform(adminGet(API + "/activations?cursor=n-importe-quoi")).andExpect(status().isBadRequest());
    }

    @Test
    void activationFilters() throws Exception {
        assertEquals(30, get("/activations?state=ACTIVATED&limit=500").get("items").size());
        assertEquals(60, get("/activations?kind=TRIAL&limit=500").get("items").size());
        assertEquals(40, get("/activations?tool=" + kid(PHONE)).get("items").size());
        assertEquals(40, get("/activations?tool=PHONE&limit=500").get("items").size());
        assertEquals(80, get("/activations?tool=DESK&limit=500").get("items").size());
        assertEquals(10, get("/activations?license=lic-seed3&limit=500").get("items").size());
        String code = devs.get(7).code();
        assertEquals(3, get("/activations?device=" + code).get("items").size(), "keys of one TV: 7, 47, 87 → i%40 == 7");
        assertEquals(3, get("/activations?device=" + code.substring(code.length() - 4)).get("items").size(), "the 4 last characters are enough");
        assertEquals(12, get("/activations?flag=undeclared&limit=500").get("items").size());
        assertEquals(1, get("/activations?q=" + sha256("k17").substring(0, 8)).get("items").size());
        assertEquals(30, get("/activations?from=" + base.minus(Duration.ofMinutes(9)) + "&to=" + base.plus(Duration.ofMinutes(1))).get("items").size());
        assertEquals(120, get("/activations?sort=seen&limit=500").get("items").size());
        assertEquals(0, get("/activations?state=ACTIVATED&kind=PRODUCTION&tool=PHONE&license=lic-seed0&limit=500").get("items").size());
    }

    @Test
    void tvFilters() throws Exception {
        assertEquals(8, get("/tvs?freshness=fresh").get("items").size());
        assertEquals(8, get("/tvs?freshness=stale").get("items").size());
        assertEquals(8, get("/tvs?freshness=old").get("items").size());
        assertEquals(16, get("/tvs?freshness=never").get("items").size());
        assertEquals(10, get("/tvs?edition=PRODUCTION").get("items").size());
        assertEquals(20, get("/tvs?version=1412").get("items").size());
        assertEquals(40, get("/tvs?limit=500").get("items").size());
        JsonNode first = get("/tvs?limit=10");
        assertNotNull(first.get("nextCursor").asText());
        JsonNode item = first.get("items").get(0);
        assertNotNull(item.get("tvRef"));
        assertFalse(item.has("deviceCode"), "the list shows only the end of the code");
        assertEquals(4, item.get("codeTail").asText().length());
    }

    @Test
    void tvFicheShowsTheTimelineAndAnUnknownOrBadCodeIsRefused() throws Exception {
        Dev d = devs.get(0);
        String ref = tvRef.of(d.code());
        new TransactionTemplate(tx).executeWithoutResult(s -> {
            eventLog.append(new EventLog.NewEvent("ISSUED", base.minusSeconds(7200).toEpochMilli(), sha256("k0"), ref, "lic-seed0", kid(DESK), "TOOL", kid(DESK), "JOURNAL", null, null, "t1"));
            eventLog.append(new EventLog.NewEvent("DELIVERED", base.minusSeconds(3600).toEpochMilli(), sha256("k0"), ref, "lic-seed0", kid(DESK), "TOOL", kid(DESK), "JOURNAL", null, null, "t2"));
            eventLog.append(new EventLog.NewEvent("ACTIVATED", base.minusSeconds(1800).toEpochMilli(), sha256("k0"), ref, "lic-seed0", null, "TV", "tv", "REPORT", null, null, "t3"));
        });
        JsonNode f = get("/tvs/" + d.code().toLowerCase().replace("-", " "));
        assertEquals(d.code(), f.get("tv").get("deviceCode").asText());
        assertEquals(ref, f.get("tv").get("tvRef").asText());
        List<String> types = new ArrayList<>();
        f.get("timeline").forEach(e -> types.add(e.get("type").asText()));
        assertEquals(List.of("ISSUED", "DELIVERED", "ACTIVATED"), types, "chronological order");
        assertNotNull(f.get("activations"));
        assertNotNull(f.get("freshness"));
        mvc.perform(adminGet(API + "/tvs/" + dev().code())).andExpect(status().isNotFound());
        mvc.perform(adminGet(API + "/tvs/0000-0000-0000-000U")).andExpect(status().isBadRequest());
    }

    @Test
    void activationFicheCarriesItsEvents() throws Exception {
        String fp = sha256("k0");
        new TransactionTemplate(tx).executeWithoutResult(s -> eventLog.append(new EventLog.NewEvent("ISSUED", base.toEpochMilli(), fp, null, null, null, "TOOL", "x", "JOURNAL", null, null, "f1")));
        JsonNode f = get("/activations/" + fp);
        assertEquals(fp, f.get("activation").get("fp").asText());
        assertEquals(1, f.get("events").size());
        mvc.perform(adminGet(API + "/activations/" + "e".repeat(64))).andExpect(status().isNotFound());
        mvc.perform(adminGet(API + "/activations/not-a-fingerprint")).andExpect(status().isBadRequest());
    }

    @Test
    void theDashboardAddsUpWhatWasSeeded() throws Exception {
        jdbc.update("insert into act_alert (type, severity, fp, tv_ref, kid, license_id, opened_at, last_seen_at, hits, state, open_key, detail) values ('CLONE','high',null,null,null,null,?,?,1,'OPEN',?,'x')",
                Timestamp.from(base), Timestamp.from(base), "k".repeat(64));
        JsonNode d = get("/dashboard");
        assertEquals(120, d.get("activations").get("total").asInt());
        assertEquals(30, d.get("activations").get("byKindState").get("TRIAL").get("ACTIVATED").asInt() + d.get("activations").get("byKindState").get("PRODUCTION").path("ACTIVATED").asInt());
        assertEquals(1, d.get("alerts").get("open").get("CLONE").asInt());
        assertEquals(40, d.get("tvs").get("total").asInt());
        assertEquals(8, d.get("tvs").get("freshness").get("fresh").asInt());
        assertEquals(16, d.get("tvs").get("freshness").get("never").asInt());
        assertEquals(20, d.get("tvs").get("versions").get("1412").asInt());
        assertTrue(d.get("licensesModule").asBoolean());
        assertNull(d.get("secret"));
    }
}
