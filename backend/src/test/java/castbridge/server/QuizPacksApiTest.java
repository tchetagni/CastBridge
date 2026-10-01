package castbridge.server;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import castbridge.server.quiz.QuizPackService;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HexFormat;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Signed catalog and resumable downloads of the question packs, and the « parties sans répétition » coverage. */
class QuizPacksApiTest extends ApiTestBase {
    static byte[] cm2 = zip(4000), general = zip(2500);
    static Path packs;

    @DynamicPropertySource
    static void packsDir(DynamicPropertyRegistry r) throws IOException {
        packs = Files.createTempDirectory("cb-quiz-packs");
        Files.write(packs.resolve("quiz-cm2-p1-v1.quiz.zip"), cm2);
        Files.write(packs.resolve("quiz-general-p1-v1.quiz.zip"), general);
        Files.write(packs.resolve("quiz-tle-p1-v1.quiz.zip"), "tampered after the build".getBytes());     // size differs from the catalog
        Files.writeString(packs.resolve("catalog.json"), """
                {"format":1,"version":1,"packs":[
                 {"id":"cm2-p1","track":"primary","level":"CM2","field":null,"part":1,"parts":5,"version":1,"file":"quiz-cm2-p1-v1.quiz.zip","size":%d,"sha256":"%s","questions":1500,"byRegion":{"WORLD":1500}},
                 {"id":"general-p1","track":"general","level":null,"field":null,"part":1,"parts":3,"version":1,"file":"quiz-general-p1-v1.quiz.zip","size":%d,"sha256":"%s","questions":1000,"byRegion":{"CM":700,"AF":200,"WORLD":100}},
                 {"id":"tle-p1","track":"secondary","level":"Tle","field":null,"part":1,"parts":3,"version":1,"file":"quiz-tle-p1-v1.quiz.zip","size":%d,"sha256":"%s","questions":1500,"byRegion":{"WORLD":1500}},
                 {"id":"gone-p1","track":"secondary","level":"3e","field":null,"part":1,"parts":3,"version":1,"file":"quiz-3e-p1-v1.quiz.zip","size":999,"sha256":"%s","questions":1500,"byRegion":{"WORLD":1500}},
                 {"id":"evil","track":"primary","level":"CM2","field":null,"part":9,"parts":9,"version":1,"file":"../../etc/passwd","size":10,"sha256":"%s","questions":1,"byRegion":{}}
                ]}""".formatted(cm2.length, sha(cm2), general.length, sha(general), 5000, sha(cm2), sha(cm2), sha(cm2)));
        r.add("castbridge.quiz.packs-dir", packs::toString);
    }

    private static byte[] zip(int n) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (ZipOutputStream z = new ZipOutputStream(out)) {
                z.putNextEntry(new ZipEntry("manifest.json")); z.write("{}".getBytes()); z.closeEntry();
                z.putNextEntry(new ZipEntry("questions.json"));
                byte[] b = new byte[n]; new java.util.Random(n).nextBytes(b); z.write(b); z.closeEntry();
            }
            return out.toByteArray();
        } catch (IOException e) { throw new IllegalStateException(e); }
    }

    private static String sha(byte[] b) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b)); } catch (Exception e) { throw new IllegalStateException(e); }
    }

    /** Verifies an entry as the apps do (QuizPackInfo.canonicalPayload in castbridge.core.quiz) with the public key. */
    private boolean verify(JsonNode p) throws Exception {
        String course = p.get("track").asText() + (p.get("level").isNull() ? "" : "/" + p.get("level").asText()) + (p.get("field").isNull() ? "" : "/" + p.get("field").asText());
        String payload = String.join("\n", "castbridge-quiz-pack-v1", "id=" + p.get("id").asText(), "course=" + course, "part=" + p.get("part").asInt(),
                "parts=" + p.get("parts").asInt(), "version=" + p.get("version").asInt(), "file=" + p.get("file").asText(), "size=" + p.get("size").asLong(),
                "sha256=" + p.get("sha256").asText(), "questions=" + p.get("questions").asInt());
        byte[] raw = Base64.getDecoder().decode(PUBLIC_KEY_B64);
        byte[] spki = new byte[44];
        System.arraycopy(HexFormat.of().parseHex("302a300506032b6570032100"), 0, spki, 0, 12);
        System.arraycopy(raw, 0, spki, 12, 32);
        Signature v = Signature.getInstance("Ed25519");
        v.initVerify(KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(spki)));
        v.update(payload.getBytes(StandardCharsets.UTF_8));
        return v.verify(Base64.getDecoder().decode(p.get("signature").asText()));
    }

    @Test
    void theCatalogIsSignedAndOnlyAnnouncesIntactPacks() throws Exception {
        JsonNode cat = body(mvc.perform(get("/api/v1/quiz/packs")).andExpect(status().isOk()).andReturn());
        assertEquals(1, cat.get("format").asInt());
        assertEquals(11_000_000L, cat.get("tvCapBytes").asLong());
        assertEquals(2, cat.get("packs").size(), "the tampered, the missing and the unsafe entries are not announced: " + cat);
        for (JsonNode p : cat.get("packs")) assertTrue(verify(p), "signature of " + p.get("file").asText());
        assertEquals(cm2.length + general.length, cat.get("totalBytes").asLong());
        // a forged field breaks the signature
        JsonNode first = cat.get("packs").get(0);
        ((com.fasterxml.jackson.databind.node.ObjectNode) first).put("size", first.get("size").asLong() + 1);
        assertFalse(verify(first));
        JsonNode one = body(mvc.perform(get("/api/v1/quiz/packs").param("course", "primary/CM2")).andReturn());
        assertEquals(1, one.get("packs").size());
        assertEquals("primary/CM2".equals("primary/CM2") ? "cm2-p1" : "", one.get("packs").get(0).get("id").asText());
    }

    @Test
    void downloadsResumeWithRangeAndIfRange() throws Exception {
        String url = "/api/v1/quiz/packs/quiz-cm2-p1-v1.quiz.zip";
        var full = mvc.perform(get(url)).andExpect(status().isOk()).andExpect(header().string("ETag", "\"" + sha(cm2) + "\""))
                .andExpect(header().string("Accept-Ranges", "bytes")).andReturn().getResponse();
        assertArrayEquals(cm2, full.getContentAsByteArray());
        var part = mvc.perform(get(url).header("Range", "bytes=1000-").header("If-Range", "\"" + sha(cm2) + "\"")).andExpect(status().isPartialContent())
                .andExpect(header().string("Content-Range", "bytes 1000-" + (cm2.length - 1) + "/" + cm2.length)).andReturn().getResponse();
        assertEquals(cm2.length - 1000, part.getContentAsByteArray().length);
        assertEquals(cm2[1000], part.getContentAsByteArray()[0]);
        // another version of the file under the same name (If-Range differs): the whole file again
        mvc.perform(get(url).header("Range", "bytes=1000-").header("If-Range", "\"other\"")).andExpect(status().isOk());
        mvc.perform(get(url).header("Range", "bytes=99999999-")).andExpect(status().isRequestedRangeNotSatisfiable());
        mvc.perform(get(url).header("If-None-Match", "\"" + sha(cm2) + "\"")).andExpect(status().isNotModified());
        mvc.perform(head(url)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/quiz/packs/quiz-nothing-p1-v1.quiz.zip")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/quiz/packs/quiz-tle-p1-v1.quiz.zip")).andExpect(status().isNotFound());       // announced size does not match the file
        mvc.perform(get("/api/v1/quiz/packs/..%2F..%2Fapplication.yml")).andExpect(status().is4xxClientError());
    }

    @Test
    void coverageSaysHowManyGamesWithoutRepeatEachCourseGuarantees() throws Exception {
        mvc.perform(get("/api/v1/admin/quiz/coverage")).andExpect(status().isUnauthorized());
        JsonNode c = body(mvc.perform(get("/api/v1/admin/quiz/coverage").header("Authorization", ADMIN)).andExpect(status().isOk()).andReturn());
        assertEquals(300, c.get("targetGames").asInt());
        JsonNode cm2Row = null, generalRow = null;
        for (JsonNode r : c.get("courses")) {
            if (r.get("course").asText().equals("primary/CM2")) cm2Row = r;
            if (r.get("course").asText().equals("general")) generalRow = r;
        }
        assertEquals(1500, cm2Row.get("packs").asLong());
        assertEquals(cm2Row.get("total").asLong() / 15, cm2Row.get("games").asInt(), "games = questions / 15 (the 1 500 of the pack + the few published ones)");
        assertTrue(cm2Row.get("games").asInt() >= 100);
        assertFalse(cm2Row.get("enough").asBoolean());
        assertEquals(4500 - cm2Row.get("total").asLong(), cm2Row.get("missing").asLong());
        assertTrue(generalRow.get("server").asLong() >= 150 && generalRow.get("packs").asLong() == 1000);
        assertTrue(generalRow.get("games").asInt() < 300 && !generalRow.get("enough").asBoolean());
        // the rule itself
        assertEquals(300, QuizPackService.games("secondary", java.util.Map.of(), 4500));
        assertEquals(300, QuizPackService.games("general", java.util.Map.of("CM", 3150, "AF", 900, "WORLD", 450), 4500));
        assertEquals(0, QuizPackService.games("general", java.util.Map.of("CM", 3150, "AF", 900), 4050), "no World question = no game that keeps the 70/20/10");
    }
}
