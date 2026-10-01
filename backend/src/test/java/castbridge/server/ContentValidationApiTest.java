package castbridge.server;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import castbridge.server.content.ContentRules;
import castbridge.server.content.ContentService;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

class ContentValidationApiTest extends ApiTestBase {
    @Autowired JdbcTemplate jdbc;
    @Autowired ContentService content;

    private record Dev(String publicId, String token) {}

    private static RequestPostProcessor admin() { return user("prof").roles("WEBADMIN"); }

    private Dev register(String consent) throws Exception {
        JsonNode r = body(mvc.perform(post("/api/v1/devices/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"installId\":\"" + UUID.randomUUID() + "\",\"app\":\"tv\",\"versionCode\":8,\"versionName\":\"0.6\","
                        + "\"platform\":\"android-tv\",\"manufacturer\":\"Hisense\",\"model\":\"H55\",\"consent\":\"" + consent + "\"}"))
                .andExpect(status().isCreated()).andReturn());
        return new Dev(r.get("deviceId").asText(), r.get("deviceToken").asText());
    }

    private static String report(String item, String reason, String note) {
        return "{\"id\":\"" + UUID.randomUUID() + "\",\"kind\":\"question\",\"item\":\"" + item + "\",\"reason\":\"" + reason + "\",\"note\":" + quote(note)
                + ",\"hash\":\"4aba34d897c8b24d\",\"lot\":\"quiz/3e\",\"lotVersion\":1,\"at\":" + Instant.now().toEpochMilli() + ",\"channel\":\"beta\"}";
    }

    private static String quote(String s) {
        if (s == null) return "null";
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            if (c == '"' || c == '\\') b.append('\\').append(c);
            else if (c < ' ') b.append(String.format("\\u%04x", (int) c));
            else b.append(c);
        }
        return b.append('"').toString();
    }

    private ResultActionsHelper send(Dev d, String... reports) throws Exception {
        return new ResultActionsHelper(body(mvc.perform(post("/api/v1/content/reports").header("Authorization", "Bearer " + d.token())
                .contentType(MediaType.APPLICATION_JSON).content("{\"reports\":[" + String.join(",", reports) + "]}")).andExpect(status().isOk()).andReturn()));
    }

    private record ResultActionsHelper(JsonNode n) {
        int accepted() { return n.get("accepted").asInt(); }
        int duplicates() { return n.get("duplicates").asInt(); }
        int rejected() { return n.get("rejected").asInt(); }
    }

    private static String indexLine(String kind, String id, String lot, String cls, String subject, String hash, int difficulty) {
        return "{\"kind\":\"" + kind + "\",\"id\":\"" + id + "\",\"lot\":\"" + lot + "\",\"cls\":\"" + cls + "\",\"subject\":\"" + subject + "\",\"hash\":\""
                + hash + "\",\"text\":\"Texte de " + id + "\",\"state\":\"review\",\"difficulty\":" + difficulty + "}";
    }

    // ------------------------------------------------------------------ pure rules (same vectors as QualitySignals in core)

    @Test
    void rulesLifecycleAndSuspicion() {
        assertTrue(ContentRules.canMove("review", "validated")); assertTrue(ContentRules.canMove("needs-fix", "review"));
        assertFalse(ContentRules.canMove("rejected", "validated")); assertFalse(ContentRules.canMove("needs-fix", "validated"));
        assertEquals("validated", ContentRules.state("approved")); assertEquals("needs-fix", ContentRules.state("À corriger")); assertNull(ContentRules.state("x"));
        assertEquals("ab c", ContentRules.cleanNote("a\u0000b\u0007\n\n c", 200));
        assertEquals(200, ContentRules.cleanNote("x".repeat(500), 200).length());
        assertEquals(0.0, ContentRules.suspicion(3, 10, 0, 0).score(), 1e-9);
        var wrong = ContentRules.suspicion(2, 100, 10, 0);
        assertTrue(wrong.flagged()); assertTrue(wrong.reasons().stream().anyMatch(r -> r.contains("hasard")));
        int[] diff = {1, 2, 3, 4, 5}; double[] p = {0.90, 0.78, 0.65, 0.50, 0.35};
        for (int i = 0; i < 5; i++) assertFalse(ContentRules.suspicion(diff[i], 200, (long) (200 * p[i]), 0).flagged(), "difficulty " + diff[i]);
        assertTrue(ContentRules.suspicion(1, 60, 30, 0).flagged());
        double easy = ContentRules.suspicion(5, 100, 95, 0).score(), hard = ContentRules.suspicion(1, 100, 60, 0).score();
        assertTrue(easy > 0 && easy < hard);
        assertTrue(ContentRules.suspicion(3, 5, 5, 3).flagged()); assertFalse(ContentRules.suspicion(3, 5, 5, 1).flagged());
        double a = ContentRules.suspicion(3, 100, 20, 0).score(), b = ContentRules.suspicion(3, 100, 20, 9).score();
        assertTrue(a >= 0 && b <= 1 && b > a);
        double[] w = ContentRules.wilson(50, 100);
        assertTrue(w[0] < 0.5 && w[1] > 0.5 && w[0] > 0.39 && w[1] < 0.61);
    }

    // ------------------------------------------------------------------ reports from the devices

    @Test
    void reportsAreValidatedDedupedBoundedAndLimited() throws Exception {
        mvc.perform(post("/api/v1/content/reports").contentType(MediaType.APPLICATION_JSON).content("{\"reports\":[]}")).andExpect(status().isUnauthorized());
        Dev d = register("essential");
        String longNote = "x".repeat(500);
        var r = send(d, report("q-100", "wrong_answer", "  la réponse\u0000 est\n\nfausse "), report("q-100", "ambiguous", longNote), report("../etc", "other", "x"),
                report("q-101", "inconnu", "x"));
        assertEquals(2, r.accepted()); assertEquals(2, r.rejected());
        assertEquals("la réponse est fausse", jdbc.queryForObject("select note from content_report where item_id = 'q-100' and reason = 'wrong_answer'", String.class));
        assertEquals(200, jdbc.queryForObject("select length(note) from content_report where item_id = 'q-100' and reason = 'ambiguous'", Integer.class));
        // the same device, item, reason and content version again: counted once (also with another report id)
        assertEquals(1, send(d, report("q-100", "wrong_answer", "encore")).duplicates());
        assertEquals(2, jdbc.queryForObject("select count(*) from content_report where item_id = 'q-100'", Integer.class));
        // an item nobody registered still appears in the queue
        assertEquals(1, jdbc.queryForObject("select count(*) from content_item where item_id = 'q-100'", Integer.class));
        // the report carries no identity but the device row it hangs on
        assertEquals(0, jdbc.queryForObject("select count(*) from content_report where note like '%@%'", Integer.class));

        // a batch is at most 50 reports
        String[] many = new String[51];
        for (int i = 0; i < many.length; i++) many[i] = report("q-" + i, "other", null);
        mvc.perform(post("/api/v1/content/reports").header("Authorization", "Bearer " + d.token()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reports\":[" + String.join(",", many) + "]}")).andExpect(status().isPayloadTooLarge());
        mvc.perform(post("/api/v1/content/reports").header("Authorization", "Bearer " + d.token()).contentType(MediaType.APPLICATION_JSON).content("{\"x\":1}")).andExpect(status().isBadRequest());

        // rate limit per device: 60 an hour
        long id = jdbc.queryForObject("select id from device where public_id = ?", Long.class, d.publicId());
        for (int i = 0; i < ContentService.MAX_PER_HOUR; i++) {
            jdbc.update("insert into content_report (report_id, device_id, kind, item_id, reason, note, channel, reported_at, received_at) values (?,?,?,?,?,?,?,?,?)",
                    UUID.randomUUID().toString(), id, "question", "fill-" + i, "other", "", "beta", java.sql.Timestamp.from(Instant.now()), java.sql.Timestamp.from(Instant.now()));
        }
        mvc.perform(post("/api/v1/content/reports").header("Authorization", "Bearer " + d.token()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reports\":[" + report("q-new", "other", null) + "]}")).andExpect(status().isTooManyRequests());
    }

    // ------------------------------------------------------------------ aggregated numbers

    private String statEvent(String kind, String item, int shown, int correct, long ms) {
        return "{\"id\":\"" + UUID.randomUUID() + "\",\"ts\":" + Instant.now().toEpochMilli() + ",\"name\":\"content_stat\",\"props\":{\"kind\":\"" + kind
                + "\",\"item\":\"" + item + "\",\"shown\":" + shown + ",\"correct\":" + correct + ",\"ms\":" + ms + ",\"reports\":0}}";
    }

    @Test
    void contentStatsAreSummedOnlyWithConsentAndNeverCarryText() throws Exception {
        Dev yes = register("usage"), no = register("essential");
        events(yes, statEvent("question", "stat-1", 10, 4, 40_000), statEvent("question", "stat-1", 5, 5, 10_000));
        events(no, statEvent("question", "stat-1", 99, 99, 1));
        var row = jdbc.queryForMap("select shown, correct, sum_ms from content_stat where kind = 'question' and item_id = 'stat-1'");
        assertEquals(15L, ((Number) row.get("shown")).longValue()); assertEquals(9L, ((Number) row.get("correct")).longValue()); assertEquals(50_000L, ((Number) row.get("sum_ms")).longValue());
        // a text or a forbidden key refuses the event
        JsonNode res = body(mvc.perform(post("/api/v1/events/batch").header("Authorization", "Bearer " + yes.token()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"events\":[{\"id\":\"" + UUID.randomUUID() + "\",\"ts\":" + Instant.now().toEpochMilli() + ",\"name\":\"content_stat\",\"props\":{\"kind\":\"question\","
                        + "\"item\":\"Quelle est la capitale ?\",\"shown\":1}}]}")).andExpect(status().isOk()).andReturn());
        assertEquals(1, res.get("rejected").asInt());
    }

    private void events(Dev d, String... events) throws Exception {
        mvc.perform(post("/api/v1/events/batch").header("Authorization", "Bearer " + d.token()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"events\":[" + String.join(",", events) + "]}")).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ registry, queue, decisions, bulk, CSV

    private void index(String... lines) throws Exception {
        mvc.perform(post("/api/v1/admin/content/index").header("Authorization", ADMIN).contentType(MediaType.TEXT_PLAIN).content(String.join("\n", lines)))
                .andExpect(status().isOk());
    }

    @Test
    void indexQueueDecisionsBulkCsvAndProgress() throws Exception {
        mvc.perform(post("/api/v1/admin/content/index").contentType(MediaType.TEXT_PLAIN).content("{}")).andExpect(status().isUnauthorized());
        String h1 = "1111111111111111", h2 = "2222222222222222", h3 = "3333333333333333", h4 = "4444444444444444";
        index(indexLine("question", "ix-1", "quiz/3e", "3e", "Algèbre", h1, 2), indexLine("question", "ix-2", "quiz/3e", "3e", "Géométrie", h2, 3),
                indexLine("question", "ix-3", "quiz/3e", "3e", "Algèbre", h3, 4), indexLine("exercise", "ix-ex", "learn/cep-maths", "CM2-ix", "maths", h4, 1), "pas du json");

        // reports + numbers make ix-2 the most suspicious
        Dev d1 = register("usage");
        send(d1, report("ix-2", "wrong_answer", "faux"), report("ix-2", "ambiguous", "deux réponses"));
        Dev d2 = register("essential");
        send(d2, report("ix-2", "wrong_answer", "faux aussi"), report("ix-1", "language", "faute"));
        events(d1, statEvent("question", "ix-2", 100, 10, 500_000), statEvent("question", "ix-3", 100, 35, 500_000));
        var page = content.queue(new ContentService.Filter("quiz/3e", null, null, null, null, null, "score"), 0, 10);
        assertEquals("ix-2", page.rows().get(0).id()); assertTrue(page.rows().get(0).suspicion().flagged());
        assertEquals(2, page.rows().get(0).reports());        // distinct devices, not distinct reports
        assertEquals("ix-1", content.queue(new ContentService.Filter(null, null, null, null, null, null, "reports"), 0, 10).rows().get(1).id());
        assertEquals(1, content.queue(new ContentService.Filter(null, "CM2-ix", null, null, null, null, "lot"), 0, 10).total());
        assertEquals(1, content.queue(new ContentService.Filter("quiz/3e", null, "Géométrie", null, null, null, "lot"), 0, 10).total());

        // decisions: legal moves, a note to reject, the hash seen is recorded
        var bad = org.junit.jupiter.api.Assertions.assertThrows(castbridge.server.web.ApiException.class, () -> content.decide("question", "ix-2", "rejected", "prof", "", null));
        assertTrue(bad.getMessage().contains("note"));
        content.decide("question", "ix-2", "needs-fix", "prof", "deux réponses possibles", null);
        org.junit.jupiter.api.Assertions.assertThrows(castbridge.server.web.ApiException.class, () -> content.decide("question", "ix-2", "validated", "prof", "", null));
        content.decide("question", "ix-2", "review", "prof", "", null);
        content.decide("question", "ix-2", "validated", "prof", "corrigé", null);
        assertEquals("validated", content.item("question", "ix-2").state());

        // bulk: the reported / suspicious items stay, the others are validated; a second run does nothing
        var bulk = content.bulkValidate("quiz/3e", null, "prof", "", false);
        assertEquals(1, bulk.validated());                  // ix-3: 35 % for difficulty 4 is within the usual range
        assertEquals(1, bulk.skippedReported());            // ix-1 has a report: it stays in the queue
        assertEquals("review", content.item("question", "ix-1").state());
        assertEquals(0, content.bulkValidate("quiz/3e", null, "prof", "", false).validated());

        // progress per lot and class
        var lots = content.progress(false).stream().filter(p -> p.lot().equals("quiz/3e")).findFirst().orElseThrow();
        assertEquals(3, lots.total()); assertEquals(2, lots.validated()); assertEquals(2, lots.reported());
        assertEquals(1, content.progress(true).stream().filter(p -> p.lot().equals("quiz/3e")).count());

        // the decisions as records for the repository
        String records = mvc.perform(get("/api/v1/admin/content/records").header("Authorization", ADMIN)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(records.contains("\"id\":\"ix-2\"") && records.contains("\"state\":\"needs-fix\"") && records.contains("\"hash\":\"" + h2 + "\""));
        mvc.perform(get("/api/v1/admin/content/records")).andExpect(status().isUnauthorized());

        // changing the content (new hash) reopens a validated item; the old reports stay but are marked as about an older version
        index(indexLine("question", "ix-2", "quiz/3e", "3e", "Géométrie", "9999999999999999", 3));
        assertEquals("review", content.item("question", "ix-2").state());
        assertFalse(content.reports("question", "ix-2", "9999999999999999").get(0).current());
    }

    @Test
    void csvRoundTripForOfflineReviewers() throws Exception {
        index(indexLine("question", "csv-1", "quiz/cm2", "CM2", "Calcul", "aaaaaaaaaaaaaaaa", 2), indexLine("question", "csv-2", "quiz/cm2", "CM2", "Calcul", "bbbbbbbbbbbbbbbb", 2),
                indexLine("question", "csv-3", "quiz/cm2", "CM2", "=cmd|' /C calc'!A0", "cccccccccccccccc", 2));
        String csv = mvc.perform(get("/admin/content/export.csv").param("lot", "quiz/cm2").with(admin())).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv")).andReturn().getResponse().getContentAsString();
        assertTrue(csv.startsWith("﻿id;kind;lot;classe;matiere;etat;relecteur;date;note;empreinte"));
        assertTrue(csv.contains("\"'=cmd"), "a formula in a cell is neutralised");
        assertFalse(csv.contains(";\"=cmd"));
        String edited = "id;etat;relecteur;date;note;empreinte\r\n"
                + "csv-1;validé;Mme Ngo;2026-10-05;ok;aaaaaaaaaaaaaaaa\r\n"
                + "csv-2;rejeté;M. Fouda;2026-10-05;\"énoncé faux, ou « ambigu »\";bbbbbbbbbbbbbbbb\r\n"
                + "csv-3;validé;Mme Ngo;2026-10-05;;ffffffffffffffff\r\n"      // content changed since the export: refused
                + "inconnu;validé;Mme Ngo;2026-10-05;;\r\n";
        var res = content.importCsv(edited, "prof");
        assertEquals(2, res.applied()); assertEquals(2, res.skipped());
        assertEquals("validated", content.item("question", "csv-1").state()); assertEquals("Mme Ngo", content.item("question", "csv-1").reviewer());
        assertEquals("rejected", content.item("question", "csv-2").state()); assertEquals("énoncé faux, ou « ambigu »", content.item("question", "csv-2").note());
        assertEquals("review", content.item("question", "csv-3").state());
        // the page form works and needs CSRF + the admin role
        MockMultipartFile file = new MockMultipartFile("file", "d.csv", "text/csv", "id;etat;note\r\ncsv-3;rejeté;doublon\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        mvc.perform(multipart("/admin/content/import-csv").file(file).with(admin()).with(csrf())).andExpect(status().is3xxRedirection());
        assertEquals("rejected", content.item("question", "csv-3").state());
        mvc.perform(multipart("/admin/content/import-csv").file(file).with(admin())).andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ admin pages

    @Test
    void adminPagesRenderAndAreProtected() throws Exception {
        index(indexLine("question", "pg-1", "quiz/6e", "6e", "Histoire", "dddddddddddddddd", 2));
        Dev d = register("essential");
        send(d, report("pg-1", "language", "<script>alert(1)</script>"));
        mvc.perform(get("/admin/content")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/admin/content").with(admin())).andExpect(status().isOk()).andExpect(content().string(containsString("Relecture des contenus")))
                .andExpect(content().string(containsString("pg-1")));
        mvc.perform(get("/admin/content").param("lot", "quiz/6e").param("sort", "reports").with(admin())).andExpect(status().isOk()).andExpect(content().string(containsString("Texte de pg-1")));
        mvc.perform(get("/admin/content/item").param("kind", "question").param("id", "pg-1").with(admin())).andExpect(status().isOk())
                .andExpect(content().string(containsString("Faute de langue"))).andExpect(content().string(containsString("&lt;script&gt;")))
                .andExpect(content().string(not(containsString("<script>alert"))));
        mvc.perform(post("/admin/content/decide").param("kind", "question").param("id", "pg-1").param("state", "validated").param("note", "ok").with(admin()).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertEquals("validated", content.item("question", "pg-1").state()); assertEquals("prof", content.item("question", "pg-1").reviewer());
        mvc.perform(post("/admin/content/bulk").param("lot", "quiz/6e").with(admin()).with(csrf())).andExpect(status().is3xxRedirection());
        mvc.perform(get("/admin/content/progress").with(admin())).andExpect(status().isOk()).andExpect(content().string(containsString("Budget de contenu")))
                .andExpect(content().string(containsString("quiz/6e")));
        mvc.perform(get("/admin/quiz").with(admin())).andExpect(status().isOk()).andExpect(content().string(containsString("Contenus")));
        mvc.perform(get("/api/v1/admin/content/progress")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/content/progress").header("Authorization", ADMIN)).andExpect(status().isOk()).andExpect(jsonPath("$.lots").isArray());
        mvc.perform(get("/api/v1/admin/content/budget").header("Authorization", ADMIN)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ok"));
    }
}
