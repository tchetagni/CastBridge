package castbridge.server.activations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The whole inventory on a synthetic scenario: the office tool issues 3 trials and 1 compact key, the owner phone issues 2 productions and 1 open_all and
 * delivers one trial by Bluetooth, the server issues 1 production, five TV reports (one of an undeclared activation, one cloned). Every activation must be in
 * the right state, exactly the expected alerts must exist, and replaying every source in another order, twice, must give the same inventory.
 */
class InventoryScenarioTest extends ActTestBase {
    @Autowired Reconciler reconciler;
    @Autowired IssuanceTap issuanceTap;
    @Autowired LicenseAuditTap auditTap;
    @Autowired TvRef tvRef;
    @Autowired EventLog eventLog;

    long t0;
    long issuanceBase;
    Dev d1, d2, d3, d4, d5, d6, d7, d8, d9;
    Install i1, i5, i7, i8, i9;
    String t1, t2, t3, p5, p6, s7, u8;
    String c4;
    String journalDesk, journalPhone;
    List<Runnable> sources;

    @BeforeEach
    void setUp() throws Exception {
        resetModule();
        t0 = System.currentTimeMillis();
        clock.set(Instant.ofEpochMilli(t0));
        d1 = dev(); d2 = dev(); d3 = dev(); d4 = dev(); d5 = dev(); d6 = dev(); d7 = dev(); d8 = dev(); d9 = dev();
        long issued = t0 - 3_600_000L;
        t1 = trialToken(DESK, d1, issued);
        t2 = trialToken(DESK, d2, issued);
        t3 = trialToken(DESK, d3, issued);
        p5 = productionToken(PHONE, "lic-scen000001", d5, issued);
        p6 = productionToken(PHONE, "lic-scen000001", d6, issued);
        u8 = trialToken(DESK, d8, issued);
        c4 = sha256("compact-key-of-" + d4.code());
        issuanceBase = jdbc.queryForObject("select coalesce(max(id), 0) from lic_issuance", Long.class);   // issuances of the other tests of this class are not part of this scenario
        s7 = serverIssuedProduction(d7);      // the server's own issuance, written to lic_issuance (not to the tracker yet)
        rewindIssuanceTap();
        long startHour = (issued - 1_767_225_600_000L) / 3_600_000L;
        journalDesk = new JournalBuilder(DESK, 1, "desk", issued, 1)
                .entry(issued, "issue", JournalBuilder.issueFields(t1, null))
                .entry(issued + 1, "issue", JournalBuilder.issueFields(t2, null))
                .entry(issued + 2, "issue", JournalBuilder.issueFields(t3, null))
                .entry(issued + 3, "compact", "fp=" + c4, "form=compact", "kind=trial", "device=" + d4.code(), "windowStartHour=" + startHour, "set=0").build();
        journalPhone = new JournalBuilder(PHONE, 1, "phone", issued, 1)
                .entry(issued, "issue", JournalBuilder.issueFields(p5, null))
                .entry(issued + 1, "issue", JournalBuilder.issueFields(p6, null))
                .entry(issued + 2, "command", "power=open_all", "action=-", "bundles=2", "lots=-", "days=30", "clamped=0", "device=" + d5.code(), "challenge=0a1b2c3d", "result=ok")
                .entry(issued + 3, "deliver", "fp=" + sha256(t2), "way=bt", "tv=ok").build();
        i1 = install(); i5 = install(); i7 = install(); i8 = install(); i9 = install();
        u8 = u8; // (kept for readability: u8 is declared by no tool)
        sources = new ArrayList<>(List.of(
                () -> uploadAndExpect(journalDesk),
                () -> uploadAndExpect(journalPhone),
                () -> issuanceTap.runOnce(),
                () -> reports()));
    }

    @AfterEach
    void tearDown() { clock.reset(); }

    /** The tap starts from the issuance of this scenario. */
    private void rewindIssuanceTap() { jdbc.update("delete from act_cursor"); jdbc.update("insert into act_cursor (name, val) values ('lic_issuance', ?)", issuanceBase); }

    private void uploadAndExpect(String token) {
        try {
            int s = uploadJournal(token).getResponse().getStatus();
            assertEquals(200, s);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void reports() {
        try {
            long at = clock.nowMs();
            post(i1, reportJson(d1, at, "TRIAL", List.of(t1), null));
            post(i5, "{\"v\":1,\"deviceCode\":\"" + d5.code() + "\",\"app\":{\"code\":1412,\"name\":\"0.14.12-beta\"},\"activations\":[\"" + p5 + "\"],\"state\":{\"edition\":\"PRODUCTION\","
                    + "\"usageTo\":null,\"super\":false,\"openAllUntil\":" + (t0 - 3_600_000L + 2 + 30 * 86_400_000L) + ",\"unlockUntil\":0,\"trialResets\":0,\"installedAt\":{},"
                    + "\"commands\":[[\"open_all\",\"0a1b2c3d\"," + (t0 - 3_600_000L + 2) + ",30]]},\"at\":" + at + "}");
            post(i7, reportJson(d7, at, "PRODUCTION", List.of(s7), null));
            post(i8, reportJson(d8, at, "TRIAL", List.of(u8), null));
            post(i9, reportJson(d9, at, "TRIAL", List.of(t1), null));   // d1's trial token reported by another TV
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void post(Install i, String json) throws Exception {
        int s = report(i, json).getResponse().getStatus();
        assertTrue(s == 200 || s == 429, "report status " + s);
    }

    private Map<String, String> inventory() {
        Map<String, String> m = new TreeMap<>();
        jdbc.queryForList("select fp, tag, form, kind, subject, license_id, seat_id, tv_ref, kid, k, aseq, issued_at, expires_at, usage_to, unlimited, state, flags from act_key order by fp")
                .forEach(r -> m.put("key|" + r.get("fp"), r.toString()));
        jdbc.queryForList("select tv_ref, device_code, edition, current_fp, open_all_until, trial_resets, reco, alerts_open, app_code from act_tv order by tv_ref")
                .forEach(r -> m.put("tv|" + r.get("tv_ref"), r.toString()));
        jdbc.queryForList("select type, severity, fp, tv_ref, kid, license_id, state, hits from act_alert order by type, fp, tv_ref")
                .forEach(r -> m.put("alert|" + r.get("type") + "|" + r.get("fp") + "|" + r.get("tv_ref"), r.toString()));
        return m;
    }

    private static String diff(Map<String, String> a, Map<String, String> b) {
        StringBuilder sb = new StringBuilder();
        for (String k : new TreeSet<>(java.util.stream.Stream.concat(a.keySet().stream(), b.keySet().stream()).toList())) {
            if (!java.util.Objects.equals(a.get(k), b.get(k))) sb.append('\n').append(k).append("\n  first : ").append(a.get(k)).append("\n  replay: ").append(b.get(k));
        }
        return sb.toString();
    }

    private Map<String, Integer> eventTypes() {
        Map<String, Integer> m = new TreeMap<>();
        jdbc.queryForList("select type, count(*) n from act_event group by type").forEach(r -> m.put((String) r.get("type"), ((Number) r.get("n")).intValue()));
        return m;
    }

    private String licDigest() {
        StringBuilder sb = new StringBuilder();
        for (String t : jdbc.queryForList("select table_name from information_schema.tables where table_name like 'lic\\_%' order by table_name", String.class)) {
            sb.append(t).append('=').append(jdbc.queryForList("select * from " + t + " order by 1").toString().hashCode()).append(';');
        }
        return sb.toString();
    }

    private String fpOf(String token) { return sha256(token); }

    private void afterSources() {
        clock.set(Instant.ofEpochMilli(t0).plus(Duration.ofHours(80)));   // windows are closed, the 72 h grace of an undeclared activation is over
        reconciler.reconcileAll();
    }

    @Test
    void everyActivationIsInTheRightStateAndOnlyTheExpectedAlertsExist() {
        String lic = licDigest();
        sources.forEach(Runnable::run);
        afterSources();

        Map<String, String> state = new TreeMap<>();
        jdbc.queryForList("select fp, state from act_key").forEach(r -> state.put((String) r.get("fp"), (String) r.get("state")));
        assertEquals(8, state.size(), "3 trials + 1 compact + 2 productions (phone) + 1 server production + 1 undeclared");
        assertEquals("ACTIVATED", state.get(fpOf(t1)), "seen on its TV");
        assertEquals("ACTIVATED", state.get(fpOf(t2)), "delivered by Bluetooth, acknowledged by the TV");
        assertEquals("EXPIRED_UNUSED", state.get(fpOf(t3)), "window closed, never seen");
        assertEquals("EXPIRED_UNUSED", state.get(c4));
        assertEquals("ACTIVATED", state.get(fpOf(p5)));
        assertEquals("EXPIRED_UNUSED", state.get(fpOf(p6)));
        assertEquals("ACTIVATED", state.get(fpOf(s7)));
        assertEquals("ACTIVATED", state.get(fpOf(u8)));

        assertEquals(",clone,declared_journal,seen_on_tv,", flags(t1), "the clone alert marks the original activation too");
        assertEquals(",declared_journal,delivered_bt,", flags(t2));
        assertEquals(",seen_on_tv,server_issued,", flags(s7));
        assertEquals(",seen_on_tv,undeclared,", flags(u8));
        assertEquals("COMPACT", jdbc.queryForObject("select form from act_key where fp = ?", String.class, c4));
        assertEquals(tvRef.of(d4.code()), jdbc.queryForObject("select tv_ref from act_key where fp = ?", String.class, c4));
        assertEquals("PRODUCTION", jdbc.queryForObject("select kind from act_key where fp = ?", String.class, fpOf(s7)));
        assertEquals(true, jdbc.queryForObject("select unlimited from act_key where fp = ?", Boolean.class, fpOf(p5)));

        Set<String> alerts = new TreeSet<>();
        jdbc.queryForList("select type, fp, tv_ref from act_alert").forEach(r -> alerts.add(r.get("type") + "|" + r.get("fp") + "|" + r.get("tv_ref")));
        Set<String> expected = new TreeSet<>(List.of("UNDECLARED|" + fpOf(u8) + "|" + tvRef.of(d8.code()), "CLONE|" + fpOf(t1) + "|" + tvRef.of(d9.code())));
        assertEquals(expected, alerts, "exactly the expected alerts and no other");

        assertEquals(9, jdbc.queryForObject("select count(*) from act_tv", Integer.class));
        assertEquals("NEVER", jdbc.queryForObject("select reco from act_tv where tv_ref = ?", String.class, tvRef.of(d3.code())));
        assertEquals("OK", jdbc.queryForObject("select reco from act_tv where tv_ref = ?", String.class, tvRef.of(d1.code())));
        assertEquals("GAP", jdbc.queryForObject("select reco from act_tv where tv_ref = ?", String.class, tvRef.of(d8.code())));
        assertEquals(1, jdbc.queryForObject("select alerts_open from act_tv where tv_ref = ?", Integer.class, tvRef.of(d9.code())));
        assertTrue(jdbc.queryForObject("select open_all_until from act_tv where tv_ref = ?", java.sql.Timestamp.class, tvRef.of(d5.code())) != null);

        assertTrue(eventLog.verify().ok());
        assertEquals(lic, licDigest(), "the tracker never writes to the lic_* tables");
    }

    private String flags(String token) { return jdbc.queryForObject("select flags from act_key where fp = ?", String.class, fpOf(token)); }

    @Test
    void replayingEverySourceInAnotherOrderTwiceGivesTheSameInventory() {
        sources.forEach(Runnable::run);
        afterSources();
        Map<String, String> first = inventory();
        Map<String, Integer> firstTypes = eventTypes();

        resetModule();
        rewindIssuanceTap();
        clock.set(Instant.ofEpochMilli(t0));
        List<Runnable> reversed = new ArrayList<>(sources);
        Collections.reverse(reversed);
        for (int pass = 0; pass < 2; pass++) {
            clock.set(Instant.ofEpochMilli(t0).plus(Duration.ofMinutes(pass * 11L)));   // the second pass is past the 10-minute rule of the reports
            reversed.forEach(Runnable::run);
        }
        afterSources();
        assertEquals("", diff(first, inventory()), "same act_key, act_tv and act_alert");
        assertEquals(firstTypes, eventTypes(), "same events, none in double");
        assertEquals(0, jdbc.queryForObject("select count(*) from (select idem_key from act_event group by idem_key having count(*) > 1) x", Integer.class));
        // the taps lose their cursors (a restore, a deployment): they read the licence tables from the start again, and only the idem_key stands between that and double events
        int events = events();
        jdbc.update("delete from act_cursor");
        issuanceTap.runOnce();
        auditTap.runOnce();
        assertEquals(events, events(), "re-reading the licence tables writes nothing twice");
        assertEquals("", diff(first, inventory()));
        assertTrue(eventLog.verify().ok());
    }
}
