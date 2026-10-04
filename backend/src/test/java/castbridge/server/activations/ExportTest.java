package castbridge.server.activations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** CSV (RFC 4180, spreadsheet-injection safe) and JSONL exports: filters, row limit, rate limit, no whole device code, audited as exports. */
class ExportTest extends ActTestBase {
    @Autowired TvRef tvRef;
    @Autowired EventLog eventLog;
    @Autowired PlatformTransactionManager tx;

    static final String API = "/api/v1/admin/activations";
    Instant base;

    @DynamicPropertySource
    static void small(DynamicPropertyRegistry r) { r.add("castbridge.activations.export-max-rows", () -> "50"); }

    @BeforeEach
    void seed() {
        resetModule();
        base = Instant.parse("2026-10-10T08:00:00Z");
        clock.set(base);
    }

    @AfterEach
    void stop() { clock.reset(); }

    private void keys(int n, String state) {
        for (int i = 0; i < n; i++) {
            String fp = sha256(state + i);
            jdbc.update("insert into act_key (fp, tag, form, kind, subject, kid, license_id, issued_at, state, state_at, flags, super) values (?,?,'ENVELOPE','TRIAL','tv',?,?,?,?,?,?,false)",
                    fp, fp.substring(0, 8), kid(DESK), "lic-exp" + i, Timestamp.from(base), state, Timestamp.from(base), i == 0 ? ",declared_journal,seen_on_tv," : "");
        }
    }

    /** Minimal RFC 4180 reader: CRLF between rows, double quotes doubled inside quoted cells. */
    static List<List<String>> csv(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') { cell.append('"'); i++; }
                else if (c == '"') quoted = false;
                else cell.append(c);
            } else if (c == '"') quoted = true;
            else if (c == ',') { row.add(cell.toString()); cell.setLength(0); }
            else if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') { row.add(cell.toString()); cell.setLength(0); rows.add(row); row = new ArrayList<>(); i++; }
            else cell.append(c);
        }
        if (cell.length() > 0 || !row.isEmpty()) { row.add(cell.toString()); rows.add(row); }
        return rows;
    }

    private MvcResult export(String query, int status) throws Exception {
        MvcResult r = mvc.perform(adminGet(API + "/export?" + query)).andReturn();
        assertEquals(status, r.getResponse().getStatus(), r.getResponse().getContentAsString());
        return r;
    }

    @Test
    void csvIsRfc4180AndFiltered() throws Exception {
        keys(10, "ACTIVATED");
        keys(5, "EMISE");
        MvcResult r = export("what=activations&format=csv&state=ACTIVATED", 200);
        assertTrue(r.getResponse().getContentType().startsWith("text/csv"));
        assertTrue(r.getResponse().getContentType().toLowerCase().contains("utf-8"));
        assertTrue(r.getResponse().getHeader("Content-Disposition").startsWith("attachment"));
        String text = r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(text.endsWith("\r\n"));
        assertFalse(text.replace("\r\n", "").contains("\n"), "CRLF only between rows");
        List<List<String>> rows = csv(text);
        assertEquals(11, rows.size(), "header + 10 activations");
        assertEquals(List.of("fp", "tag", "form", "kind"), rows.get(0).subList(0, 4));
        int flags = rows.get(0).indexOf("flags");
        assertTrue(rows.stream().skip(1).anyMatch(x -> x.get(flags).equals(",declared_journal,seen_on_tv,")), "the commas of a cell are quoted");
        for (List<String> row : rows) assertEquals(rows.get(0).size(), row.size());
    }

    @Test
    void aCellThatLooksLikeAFormulaIsDefused() throws Exception {
        Dev d = dev();
        jdbc.update("insert into act_tv (tv_ref, device_code, edition, trial_resets, app_name, reco, alerts_open, api_devices, android_ids) values (?,?,?,0,?,?,0,0,0)", tvRef.of(d.code()), d.code(),
                "TRIAL", "=HYPERLINK(\"http://evil\",\"x\"),\"y\"", "NEVER");
        String text = export("what=tvs&format=csv", 200).getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        List<List<String>> rows = csv(text);
        int col = rows.get(0).indexOf("app_name");
        assertEquals("'=HYPERLINK(\"http://evil\",\"x\"),\"y\"", rows.get(1).get(col));
        assertFalse(text.contains(d.code()), "an export never carries a whole device code");
        assertEquals(tvRef.of(d.code()), rows.get(1).get(rows.get(0).indexOf("tv_ref")));
        assertTrue(rows.get(1).get(rows.get(0).indexOf("code_masked")).endsWith("-****"));
    }

    @Test
    void jsonlHasOneObjectPerLine() throws Exception {
        keys(4, "ACTIVATED");
        new TransactionTemplate(tx).executeWithoutResult(s -> {
            for (int i = 0; i < 3; i++) eventLog.append(new EventLog.NewEvent("ISSUED", base.toEpochMilli() + i, sha256("x" + i), null, null, null, "TOOL", "t", "JOURNAL", null, "{\"a\":" + i + "}", "x" + i));
        });
        String text = export("what=events&format=jsonl", 200).getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        String[] lines = text.split("\n");
        assertEquals(3, lines.length);
        JsonNode first = json.readTree(lines[0]);
        for (String f : List.of("id", "atMs", "recordedMs", "type", "fp", "tvRef", "licenseId", "kid", "actorType", "actor", "source", "before", "after", "idemKey", "prevHash", "hash")) assertTrue(first.has(f), f);
        assertEquals(1, first.get("id").asInt());
        assertEquals("0".repeat(64), first.get("prevHash").asText());
        assertEquals(json.readTree(lines[1]).get("prevHash").asText(), first.get("hash").asText());
    }

    @Test
    void moreRowsThanTheLimitIsRefusedNotTruncated() throws Exception {
        keys(60, "EMISE");
        MvcResult r = export("what=activations&format=csv", 400);
        assertTrue(r.getResponse().getContentAsString().contains("50"), r.getResponse().getContentAsString());
        export("what=activations&format=csv&state=ACTIVATED", 200);
    }

    @Test
    void badParametersAre400() throws Exception {
        export("what=secrets&format=csv", 400);
        export("what=activations&format=xml", 400);
        export("format=csv", 400);
    }

    @Test
    void tenExportsAnHourAndTheExportIsAuditedAsOne() throws Exception {
        keys(2, "ACTIVATED");
        for (int i = 0; i < 10; i++) export("what=activations&format=csv", 200);
        export("what=activations&format=csv", 429);
        Object row = jdbc.queryForMap("select * from adm_read_audit where export = true order by id desc limit 1");
        assertTrue(row.toString().contains("rows_rendered=2"), row.toString());
        clock.set(base.plus(Duration.ofMinutes(61)));
        export("what=activations&format=csv", 200);
    }
}
