package castbridge.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class QuizApiTest extends ApiTestBase {

    private static String question(String id, String text, String status) {
        return """
                {"uuid":"%s","track":"secondary","level":"Form 1","region":"CM","category":"Test","difficulty":2,
                 "question":"%s","choices":["Un","Deux","Trois","Quatre"],"answer":1,
                 "explanation":"Parce que.","source":"Test","reviewStatus":"%s"}""".formatted(id, text, status);
    }

    @Test
    void seededFromTheTvBankAndPublishedOnly() throws Exception {
        JsonNode stats = body(mvc.perform(get("/api/v1/admin/quiz/stats").header("Authorization", ADMIN)).andExpect(status().isOk()).andReturn());
        assertTrue(stats.get("total").asInt() >= 300, "the bundled bank of feat/tv-quiz is imported: " + stats);
        assertTrue(stats.get("parStatut").get("draft").asInt() >= 1, "questions marked review:true stay drafts");

        JsonNode page = body(mvc.perform(get("/api/v1/quiz/questions").param("size", "500")).andExpect(status().isOk())
                .andExpect(header().exists("ETag")).andReturn());
        assertEquals(2, page.get("version").asInt());
        for (JsonNode q : page.get("questions")) {
            assertEquals("reviewed", q.get("reviewStatus").asText());
            assertEquals("approved", q.get("status").asText());
            assertFalse(q.get("review").asBoolean());
            assertEquals(q.get("uuid").asText(), q.get("id").asText());
        }
        assertEquals(stats.get("parStatut").get("reviewed").asInt(), page.get("total").asInt());
        String etag = mvc.perform(get("/api/v1/quiz/questions").param("size", "500")).andReturn().getResponse().getHeader("ETag");
        mvc.perform(get("/api/v1/quiz/questions").param("size", "500").header("If-None-Match", etag)).andExpect(status().isNotModified());

        mvc.perform(get("/api/v1/quiz/questions").param("track", "higher").param("level", "L1").param("field", "Économie"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.questions[0].field").value("economie"));
        mvc.perform(get("/api/v1/quiz/questions").param("track", "primary").param("level", "L1")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("level : L1 n'appartient pas au parcours primary"));
    }

    @Test
    void strictValidationAllOrNothing() throws Exception {
        String bad = """
                {"version":2,"questions":[
                  %s,
                  {"uuid":"t-bad-1","region":"CM","category":"X","difficulty":1,"question":"Trois choix ?","choices":["a","b","c"],"answer":0,"explanation":"e","source":"s"},
                  {"uuid":"t-bad-2","region":"CM","category":"X","difficulty":1,"question":"Doublon de choix ?","choices":["a","A ","b","c"],"answer":0,"explanation":"e","source":"s"},
                  {"uuid":"t-bad-3","region":"CM","category":"X","difficulty":9,"question":"Index ?","choices":["a","b","c","d"],"answer":4,"explanation":"e","source":"s"},
                  %s
                ]}""".formatted(question("t-ok-1", "Question valide ?", "draft"), question("t-ok-2", "Question  VALIDE ?", "draft"));
        JsonNode err = body(mvc.perform(post("/api/v1/admin/quiz/import").contentType(MediaType.APPLICATION_JSON).content(bad)
                .header("Authorization", ADMIN)).andExpect(status().isBadRequest()).andReturn());
        String details = err.get("details").toString();
        assertTrue(details.contains("exactement 4 choix"), details);
        assertTrue(details.contains("choix en double"), details);
        assertTrue(details.contains("entre 0 et 3"), details);
        assertTrue(details.contains("entre 1 et 5"), details);
        assertTrue(details.contains("même question que t-ok-1"), details);
        mvc.perform(get("/api/v1/admin/quiz/questions/t-ok-1").header("Authorization", ADMIN)).andExpect(status().isNotFound());

        // a duplicate of a seeded question is refused too
        String dup = """
                [{"uuid":"t-dup","region":"CM","category":"Géographie","difficulty":1,"question":"Quelle est la capitale politique du Cameroun ?",
                  "choices":["Douala","Garoua","Yaoundé","Bamenda"],"answer":2,"explanation":"e","source":"s"}]""";
        mvc.perform(post("/api/v1/admin/quiz/import").contentType(MediaType.APPLICATION_JSON).content(dup).header("Authorization", ADMIN))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details[0]").value(org.hamcrest.Matchers.containsString("cm-geo-001")));
    }

    @Test
    void crudSyncAndTombstones() throws Exception {
        String t0 = body(mvc.perform(get("/api/v1/quiz/questions").param("size", "1")).andReturn()).get("syncToken").asText();
        Thread.sleep(5);
        mvc.perform(post("/api/v1/admin/quiz/questions").contentType(MediaType.APPLICATION_JSON).content(question("t-sync-1", "Combien font un plus un ?", "reviewed"))
                .header("Authorization", ADMIN)).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("approved"));
        mvc.perform(post("/api/v1/admin/quiz/questions").contentType(MediaType.APPLICATION_JSON).content(question("t-sync-2", "Question brouillon ?", "draft"))
                .header("Authorization", ADMIN)).andExpect(status().isCreated());
        mvc.perform(post("/api/v1/admin/quiz/questions").contentType(MediaType.APPLICATION_JSON).content(question("t-sync-3", "Combien font un plus un ?", "draft"))
                .header("Authorization", ADMIN)).andExpect(status().isConflict());

        JsonNode inc = body(mvc.perform(get("/api/v1/quiz/questions").param("since", t0)).andExpect(status().isOk()).andReturn());
        assertEquals(1, inc.get("total").asInt(), "only the new published question: " + inc);
        assertEquals("t-sync-1", inc.get("questions").get(0).get("uuid").asText());
        assertEquals(0, inc.get("deleted").size());
        String t1 = inc.get("syncToken").asText();

        // edit with the wrong version: 409; with the right one: ok
        JsonNode q = body(mvc.perform(get("/api/v1/admin/quiz/questions/t-sync-1").header("Authorization", ADMIN)).andReturn());
        int v = q.get("version").asInt();
        String edit = question("t-sync-1", "Combien font un plus deux ?", "reviewed").replace("\"answer\":1", "\"answer\":2,\"version\":" + (v + 5));
        mvc.perform(put("/api/v1/admin/quiz/questions/t-sync-1").contentType(MediaType.APPLICATION_JSON).content(edit).header("Authorization", ADMIN))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/v1/admin/quiz/questions/t-sync-1").contentType(MediaType.APPLICATION_JSON)
                .content(edit.replace("\"version\":" + (v + 5), "\"version\":" + v)).header("Authorization", ADMIN))
                .andExpect(status().isOk()).andExpect(jsonPath("$.answer").value(2));

        // rejected then deleted: tombstones for the devices
        Thread.sleep(5);
        mvc.perform(post("/api/v1/admin/quiz/questions/t-sync-1/status").param("value", "rejected").header("Authorization", ADMIN))
                .andExpect(status().isOk()).andExpect(jsonPath("$.review").value(true));
        JsonNode inc2 = body(mvc.perform(get("/api/v1/quiz/questions").param("since", t1)).andReturn());
        assertEquals(0, inc2.get("total").asInt());
        assertEquals("t-sync-1", inc2.get("deleted").get(0).asText());
        mvc.perform(delete("/api/v1/admin/quiz/questions/t-sync-2").header("Authorization", ADMIN)).andExpect(status().isNoContent());
        JsonNode inc3 = body(mvc.perform(get("/api/v1/quiz/questions").param("since", t1)).andReturn());
        assertEquals(Set.of("t-sync-1", "t-sync-2"), toSet(inc3.get("deleted")));
        // long gone: the device must reset its cache
        assertTrue(body(mvc.perform(get("/api/v1/quiz/questions").param("since", "2020-01-01T00:00:00+01:00")).andReturn())
                .get("resetRequired").asBoolean());
        mvc.perform(get("/api/v1/quiz/questions").param("since", "hier")).andExpect(status().isBadRequest());
    }

    @Test
    void csvRoundTripAndReimportIsANoOp() throws Exception {
        String csv = mvc.perform(get("/api/v1/admin/quiz/export").param("format", "csv").header("Authorization", ADMIN))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(csv.startsWith("uuid,lang,track,level,field,region,category"));
        JsonNode rep = body(mvc.perform(post("/api/v1/admin/quiz/import").param("format", "csv").contentType("text/csv").content(csv)
                .header("Authorization", ADMIN)).andExpect(status().isOk()).andReturn());
        assertEquals(0, rep.get("created").asInt());
        assertEquals(0, rep.get("updated").asInt());
        assertTrue(rep.get("unchanged").asInt() >= 300);

        String semicolon = "uuid;region;category;difficulty;question;choice1;choice2;choice3;choice4;answer;explanation;source;reviewStatus\r\n"
                + "t-csv-1;WORLD;Sciences;3;\"Quelle planète est surnommée « la planète rouge » ?\";Vénus;Mars;Jupiter;\"Saturne; la géante\";1;Couleur due à l'oxyde de fer.;NASA;draft\r\n";
        JsonNode dry = body(mvc.perform(post("/api/v1/admin/quiz/import").param("dryRun", "true").contentType("text/csv").content(semicolon)
                .header("Authorization", ADMIN)).andExpect(status().isOk()).andReturn());
        assertEquals(1, dry.get("created").asInt());
        mvc.perform(get("/api/v1/admin/quiz/questions/t-csv-1").header("Authorization", ADMIN)).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/admin/quiz/import").contentType("text/csv").content(semicolon).header("Authorization", ADMIN))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/quiz/questions/t-csv-1").header("Authorization", ADMIN)).andExpect(status().isOk())
                .andExpect(jsonPath("$.choices[3]").value("Saturne; la géante"));
    }

    @Test
    void drawIsProgressiveBalancedAndReproducible() throws Exception {
        for (int round = 0; round < 20; round++) {
            JsonNode d = body(mvc.perform(get("/api/v1/quiz/draw").param("track", "general").param("count", "15").param("seed", String.valueOf(1000 + round)))
                    .andExpect(status().isOk()).andReturn());
            JsonNode qs = d.get("questions");
            assertEquals(15, qs.size());
            Map<String, Integer> regions = new HashMap<>();
            Set<String> ids = new HashSet<>();
            int prev = 0;
            for (JsonNode q : qs) {
                regions.merge(q.get("region").asText(), 1, Integer::sum);
                assertTrue(ids.add(q.get("uuid").asText()), "no repeat");
                assertTrue(q.get("difficulty").asInt() >= prev, "increasing difficulty");
                prev = q.get("difficulty").asInt();
                assertEquals("reviewed", q.get("reviewStatus").asText());
                int a = q.get("answer").asInt();
                assertTrue(a >= 0 && a < 4);
            }
            // 70 % / 20 % / 10 % of 15 = 10.5 / 3 / 1.5, ±1
            int cm = regions.getOrDefault("CM", 0), af = regions.getOrDefault("AF", 0), world = regions.getOrDefault("WORLD", 0);
            assertTrue(Math.abs(cm - 10.5) <= 1 && Math.abs(af - 3) <= 1 && Math.abs(world - 1.5) <= 1, regions.toString());
        }
        String a = mvc.perform(get("/api/v1/quiz/draw").param("seed", "42")).andReturn().getResponse().getContentAsString();
        String b = mvc.perform(get("/api/v1/quiz/draw").param("seed", "42")).andReturn().getResponse().getContentAsString();
        assertEquals(a, b, "same seed, same bank = same game");
        JsonNode school = body(mvc.perform(get("/api/v1/quiz/draw").param("track", "higher").param("level", "L1").param("field", "droit"))
                .andExpect(status().isOk()).andReturn());
        assertEquals(15, school.get("count").asInt());
        school.get("questions").forEach(q -> assertEquals("droit", q.get("field").asText()));
        mvc.perform(get("/api/v1/quiz/draw").param("count", "80")).andExpect(status().isBadRequest());
    }

    private static Set<String> toSet(JsonNode arr) {
        Set<String> s = new HashSet<>();
        arr.forEach(x -> s.add(x.asText()));
        return s;
    }
}
