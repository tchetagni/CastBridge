package castbridge.server;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.stream.StreamSupport;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

/** Lots (docs/LOTS.md): admin upload/publish with checks, signed catalog, resumable downloads, rollout, channels, revocation. */
class LotsApiTest extends ApiTestBase {
    static byte[] cm2 = zip(6000, 1), cm2v2 = zip(6500, 2), quiz = zip(3000, 3);

    static byte[] zip(int n, int seed) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (ZipOutputStream z = new ZipOutputStream(out)) {
                z.putNextEntry(new ZipEntry("lot.json")); z.write("{}".getBytes()); z.closeEntry();
                z.putNextEntry(new ZipEntry("data.bin"));
                byte[] b = new byte[n]; new java.util.Random(seed).nextBytes(b); z.write(b); z.closeEntry();
            }
            return out.toByteArray();
        } catch (IOException e) { throw new IllegalStateException(e); }
    }

    static String sha(byte[] b) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b)); } catch (Exception e) { throw new IllegalStateException(e); }
    }

    private MockMultipartHttpServletRequestBuilder upload(String feature, String scope, int version, byte[] data, String... extra) {
        var b = (MockMultipartHttpServletRequestBuilder) multipart("/api/v1/admin/lots").file(new MockMultipartFile("file", "lot.lot", "application/octet-stream", data))
                .param("feature", feature).param("scope", scope).param("version", "" + version).param("title", "Apprendre « " + scope + " »")
                .header("Authorization", ADMIN);
        for (int i = 0; i + 1 < extra.length; i += 2) b.param(extra[i], extra[i + 1]);
        return b;
    }

    private JsonNode publish(String feature, String scope, int version, byte[] data, String... extra) throws Exception {
        return body(mvc.perform(upload(feature, scope, version, data, extra)).andExpect(status().isCreated()).andReturn());
    }

    private JsonNode catalog(String... params) throws Exception {
        var r = get("/api/v1/lots/catalog");
        for (int i = 0; i + 1 < params.length; i += 2) r.param(params[i], params[i + 1]);
        return body(mvc.perform(r).andExpect(status().isOk()).andReturn());
    }

    /** Verifies as the apps do (castbridge.core.lots.LotManifest.canonicalPayload) with the public key. */
    private boolean verify(JsonNode c) throws Exception {
        StringBuilder sb = new StringBuilder("castbridge-lot-catalog-v1\nchannel=" + c.get("channel").asText() + "\nfeature="
                + (c.has("feature") ? c.get("feature").asText() : "*") + "\ngeneratedAt=" + c.get("generatedAt").asText());
        StreamSupport.stream(c.get("lots").spliterator(), false)
                .sorted(Comparator.<JsonNode, String>comparing(l -> l.get("feature").asText()).thenComparing(l -> l.get("scope").asText()).thenComparingInt(l -> l.get("version").asInt()))
                .forEach(l -> sb.append("\nlot=").append(l.get("feature").asText()).append('|').append(l.get("scope").asText()).append('|').append(l.get("version").asInt())
                        .append('|').append(l.get("bytes").asLong()).append('|').append(l.get("sha256").asText()).append('|').append(l.get("minAppVersion").asInt())
                        .append('|').append(sha(l.get("title").asText().getBytes(StandardCharsets.UTF_8))));
        byte[] raw = Base64.getDecoder().decode(PUBLIC_KEY_B64);
        byte[] spki = new byte[44];
        System.arraycopy(HexFormat.of().parseHex("302a300506032b6570032100"), 0, spki, 0, 12);
        System.arraycopy(raw, 0, spki, 12, 32);
        Signature v = Signature.getInstance("Ed25519");
        v.initVerify(KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(spki)));
        v.update(sb.toString().getBytes(StandardCharsets.UTF_8));
        return v.verify(Base64.getDecoder().decode(c.get("signature").asText()));
    }

    @Test
    void adminRoutesNeedTheBearerToken() throws Exception {
        mvc.perform(get("/api/v1/admin/lots")).andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/v1/admin/lots").file(new MockMultipartFile("file", "x", "application/octet-stream", cm2))
                .param("feature", "learn").param("scope", "cm2").param("version", "1").param("title", "t")).andExpect(status().isUnauthorized());
    }

    @Test
    void anUnpublishedLotIsNeverListedNorServedThenPublishedAndSigned() throws Exception {
        JsonNode l = publish("learn", "pub1", 1, cm2);                                       // uploaded, NOT published
        assertFalse(l.get("published").asBoolean());
        assertEquals(sha(cm2), l.get("sha256").asText()); assertEquals(cm2.length, l.get("size").asLong());
        assertEquals(0, StreamSupport.stream(catalog("feature", "learn").get("lots").spliterator(), false).filter(x -> x.get("scope").asText().equals("pub1")).count());
        mvc.perform(get("/api/v1/lots/learn/pub1/1")).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/admin/lots/" + l.get("id").asLong() + "/publish").header("Authorization", ADMIN)).andExpect(status().isOk());
        JsonNode cat = catalog("feature", "learn");
        assertTrue(verify(cat), "signature of " + cat);
        assertEquals("learn", cat.get("feature").asText()); assertEquals("stable", cat.get("channel").asText());
        JsonNode e = StreamSupport.stream(cat.get("lots").spliterator(), false).filter(x -> x.get("scope").asText().equals("pub1")).findFirst().orElseThrow();
        assertEquals(cm2.length, e.get("bytes").asLong()); assertEquals(sha(cm2), e.get("sha256").asText());
        // a forged field breaks the signature; a catalog for one feature is not a catalog for all
        ((com.fasterxml.jackson.databind.node.ObjectNode) e).put("bytes", cm2.length + 1);
        assertFalse(verify(cat));
        JsonNode all = catalog();
        assertTrue(verify(all)); assertFalse(all.has("feature"));
        ((com.fasterxml.jackson.databind.node.ObjectNode) all).put("feature", "learn");
        assertFalse(verify(all));
    }

    @Test
    void downloadsResumeWithRangeAndIfRangeAndAreImmutable() throws Exception {
        publish("learn", "dl1", 1, cm2, "publish", "true");
        String url = "/api/v1/lots/learn/dl1/1";
        var full = mvc.perform(get(url)).andExpect(status().isOk()).andExpect(header().string("ETag", "\"" + sha(cm2) + "\""))
                .andExpect(header().string("Accept-Ranges", "bytes")).andExpect(header().string("Cache-Control", "public, max-age=31536000, immutable"))
                .andExpect(header().string("Content-Encoding", "identity")).andReturn().getResponse();
        assertArrayEquals(cm2, full.getContentAsByteArray());
        var part = mvc.perform(get(url).header("Range", "bytes=1000-").header("If-Range", "\"" + sha(cm2) + "\"")).andExpect(status().isPartialContent())
                .andExpect(header().string("Content-Range", "bytes 1000-" + (cm2.length - 1) + "/" + cm2.length)).andReturn().getResponse();
        assertEquals(cm2.length - 1000, part.getContentAsByteArray().length);
        assertEquals(cm2[1000], part.getContentAsByteArray()[0]);
        mvc.perform(get(url).header("Range", "bytes=1000-").header("If-Range", "\"other\"")).andExpect(status().isOk());
        mvc.perform(get(url).header("Range", "bytes=99999999-")).andExpect(status().isRequestedRangeNotSatisfiable());
        mvc.perform(get(url).header("If-None-Match", "\"" + sha(cm2) + "\"")).andExpect(status().isNotModified());
        mvc.perform(head(url)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/lots/learn/dl1/9")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/lots/learn/..%2F..%2Fx/1")).andExpect(status().is4xxClientError());
        mvc.perform(get("/api/v1/lots/learn/Bad_Scope/1")).andExpect(status().isNotFound());
    }

    @Test
    void checksRefuseBadUploads() throws Exception {
        mvc.perform(upload("learn", "ck1", 1, cm2, "sha256", "0".repeat(64))).andExpect(status().isBadRequest());          // corrupted in transit
        mvc.perform(upload("learn", "ck1", 1, "pas un zip".getBytes())).andExpect(status().isBadRequest());               // not a ZIP
        mvc.perform(upload("physique", "ck1", 1, cm2)).andExpect(status().isBadRequest());
        mvc.perform(upload("learn", "Bad Scope!", 1, cm2)).andExpect(status().isBadRequest());
        mvc.perform(upload("learn", "ck1", 0, cm2)).andExpect(status().isBadRequest());
        mvc.perform(upload("learn", "ck1", 1, cm2, "channel", "nightly")).andExpect(status().isBadRequest());
        mvc.perform(upload("learn", "ck1", 1, cm2, "rollout", "101")).andExpect(status().isBadRequest());
        mvc.perform(upload("learn", "ck1", 1, cm2, "minAppVersion", "-1")).andExpect(status().isBadRequest());
        byte[] big = zipStored(11 << 20);                                                                                     // must fit the TV's 10 Mo
        mvc.perform(upload("learn", "ck1", 1, big)).andExpect(status().isBadRequest());
        ByteArrayOutputStream evil = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(evil)) { z.putNextEntry(new ZipEntry("../../etc/passwd")); z.write(1); z.closeEntry(); }
        mvc.perform(upload("learn", "ck1", 1, evil.toByteArray())).andExpect(status().isBadRequest());
        publish("learn", "ck1", 1, cm2, "sha256", sha(cm2).toUpperCase());
        mvc.perform(upload("learn", "ck1", 1, cm2v2)).andExpect(status().isConflict());                                    // a version never changes
    }

    private static byte[] zipStored(int n) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(out)) {
            z.setLevel(0); z.putNextEntry(new ZipEntry("a.bin")); byte[] b = new byte[n]; new java.util.Random(5).nextBytes(b); z.write(b); z.closeEntry();
        }
        return out.toByteArray();
    }

    @Test
    void theNewestPublishedVersionWinsAndRevokingFallsBackToNothingServed() throws Exception {
        JsonNode v1 = publish("quiz", "nv1", 1, quiz, "publish", "true");
        JsonNode v2 = publish("quiz", "nv1", 2, cm2v2, "publish", "true", "minAppVersion", "7");
        JsonNode e = StreamSupport.stream(catalog("feature", "quiz").get("lots").spliterator(), false).filter(x -> x.get("scope").asText().equals("nv1")).findFirst().orElseThrow();
        assertEquals(2, e.get("version").asInt()); assertEquals(7, e.get("minAppVersion").asInt());
        mvc.perform(post("/api/v1/admin/lots/" + v2.get("id").asLong() + "/revoke").header("Authorization", ADMIN)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/lots/quiz/nv1/2")).andExpect(status().isGone());
        e = StreamSupport.stream(catalog("feature", "quiz").get("lots").spliterator(), false).filter(x -> x.get("scope").asText().equals("nv1")).findFirst().orElseThrow();
        assertEquals(1, e.get("version").asInt(), "the previous version is announced again");
        mvc.perform(post("/api/v1/admin/lots/" + v2.get("id").asLong() + "/publish").header("Authorization", ADMIN)).andExpect(status().isConflict());
        mvc.perform(get("/api/v1/lots/quiz/nv1/1")).andExpect(status().isOk());
        assertTrue(v1.get("id").asLong() > 0);
    }

    @Test
    void channelsAndRollout() throws Exception {
        publish("learn", "ch1", 1, cm2, "publish", "true");
        JsonNode beta = publish("learn", "ch1", 2, cm2v2, "publish", "true", "channel", "beta");
        assertEquals(1, versionOf(catalog("channel", "stable"), "ch1"), "stable devices never see beta lots");
        assertEquals(2, versionOf(catalog("channel", "beta"), "ch1"));
        mvc.perform(get("/api/v1/lots/catalog").param("channel", "nightly")).andExpect(status().isBadRequest());
        // rollout: 0 % = nobody (also not without deviceId), 100 % = everybody; in between the device id decides, stably
        mvc.perform(post("/api/v1/admin/lots/" + beta.get("id").asLong() + "/rollout").param("percent", "0").header("Authorization", ADMIN)).andExpect(status().isOk());
        assertEquals(1, versionOf(catalog("channel", "beta", "deviceId", "device-0001"), "ch1"));
        mvc.perform(post("/api/v1/admin/lots/" + beta.get("id").asLong() + "/rollout").param("percent", "50").header("Authorization", ADMIN)).andExpect(status().isOk());
        int got = 0;
        for (int i = 0; i < 40; i++) if (versionOf(catalog("channel", "beta", "deviceId", "device-%04d".formatted(i)), "ch1") == 2) got++;
        assertTrue(got > 5 && got < 35, "about half of the devices: " + got);
        assertEquals(versionOf(catalog("channel", "beta", "deviceId", "device-0007"), "ch1"), versionOf(catalog("channel", "beta", "deviceId", "device-0007"), "ch1"));
        assertEquals(1, versionOf(catalog("channel", "beta"), "ch1"), "no deviceId: full rollouts only");
        mvc.perform(post("/api/v1/admin/lots/" + beta.get("id").asLong() + "/rollout").param("percent", "101").header("Authorization", ADMIN)).andExpect(status().isBadRequest());
    }

    private int versionOf(JsonNode cat, String scope) {
        return StreamSupport.stream(cat.get("lots").spliterator(), false).filter(x -> x.get("scope").asText().equals(scope)).mapToInt(x -> x.get("version").asInt()).max().orElse(-1);
    }

    @Test
    void deleteRemovesTheFileAndListShowsTheLots() throws Exception {
        JsonNode l = publish("learn", "del1", 1, cm2, "publish", "true");
        long id = l.get("id").asLong();
        assertTrue(body(mvc.perform(get("/api/v1/admin/lots").header("Authorization", ADMIN)).andExpect(status().isOk()).andReturn()).size() >= 1);
        mvc.perform(delete("/api/v1/admin/lots/" + id).header("Authorization", ADMIN)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/lots/learn/del1/1")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/admin/lots/" + id).header("Authorization", ADMIN)).andExpect(status().isNotFound());
    }

    @Test
    void existingQuizPackAndUpdateEndpointsStillWork() throws Exception {
        mvc.perform(get("/api/v1/quiz/packs")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/updates/public-key")).andExpect(status().isOk());
    }
}
