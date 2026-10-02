package castbridge.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.stream.StreamSupport;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

/** Lots Langues (feature « langues », docs/LANGUES.md § 14): upload with its own validator, filtered signed catalog, download; learn and quiz unchanged. */
class LotsLanguesApiTest extends ApiTestBase {
    static byte[] lot(String id, int version, String... extraEntries) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (ZipOutputStream z = new ZipOutputStream(out)) {
                String[] p = id.split("-");
                z.putNextEntry(new ZipEntry("langue.json"));
                z.write(("{\"format\":1,\"type\":\"langue\",\"id\":\"" + id + "\",\"version\":" + version + ",\"target\":\"" + p[0] + "\",\"level\":\"" + p[1]
                        + "\",\"source\":\"" + p[3] + "\",\"title\":\"t\",\"units\":[{\"id\":\"u1\"}]}").getBytes(StandardCharsets.UTF_8));
                z.closeEntry();
                z.putNextEntry(new ZipEntry("media.json")); z.write("{\"media\":[]}".getBytes()); z.closeEntry();
                for (String e : extraEntries) { z.putNextEntry(new ZipEntry(e)); z.write(1); z.closeEntry(); }
            }
            return out.toByteArray();
        } catch (IOException e) { throw new IllegalStateException(e); }
    }

    private MockMultipartHttpServletRequestBuilder upload(String feature, String scope, int version, byte[] data, String... extra) {
        var b = (MockMultipartHttpServletRequestBuilder) multipart("/api/v1/admin/lots").file(new MockMultipartFile("file", "lot.lot", "application/octet-stream", data))
                .param("feature", feature).param("scope", scope).param("version", "" + version).param("title", "Langues « " + scope + " »")
                .header("Authorization", ADMIN);
        for (int i = 0; i + 1 < extra.length; i += 2) b.param(extra[i], extra[i + 1]);
        return b;
    }

    private JsonNode catalog(String... params) throws Exception {
        var r = get("/api/v1/lots/catalog");
        for (int i = 0; i + 1 < params.length; i += 2) r.param(params[i], params[i + 1]);
        return body(mvc.perform(r).andExpect(status().isOk()).andReturn());
    }

    @Test
    void aLanguesLotIsUploadedPublishedListedFilteredAndServed() throws Exception {
        byte[] de = lot("de-a0-famille-en", 1);
        JsonNode l = body(mvc.perform(upload("langues", "de-a0-famille-en", 1, de, "publish", "true")).andExpect(status().isCreated()).andReturn());
        assertEquals("langues", l.get("feature").asText());
        JsonNode cat = catalog("feature", "langues");
        assertEquals("langues", cat.get("feature").asText());
        assertTrue(cat.hasNonNull("signature"));
        JsonNode e = StreamSupport.stream(cat.get("lots").spliterator(), false).filter(x -> x.get("scope").asText().equals("de-a0-famille-en")).findFirst().orElseThrow();
        assertEquals(de.length, e.get("bytes").asLong());
        // the learn catalog never lists it, the unfiltered one does
        assertEquals(0, StreamSupport.stream(catalog("feature", "learn").get("lots").spliterator(), false).filter(x -> x.get("feature").asText().equals("langues")).count());
        assertTrue(StreamSupport.stream(catalog().get("lots").spliterator(), false).anyMatch(x -> x.get("feature").asText().equals("langues")));
        byte[] got = mvc.perform(get("/api/v1/lots/langues/de-a0-famille-en/1")).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertEquals(de.length, got.length);
    }

    @Test
    void badLanguesLotsAreRefusedWithFrenchReasons() throws Exception {
        mvc.perform(upload("langues", "fr-a0-salut-en", 1, lot("fr-a0-salut-en", 1, "audio/x.mp3"))).andExpect(status().isBadRequest());       // stray entry
        mvc.perform(upload("langues", "fr-a0-salut-en", 1, lot("fr-a0-autre-en", 1))).andExpect(status().isBadRequest());                       // id differs from the scope
        mvc.perform(upload("langues", "fr-a0-salut-en", 2, lot("fr-a0-salut-en", 1))).andExpect(status().isBadRequest());                       // version differs
        mvc.perform(upload("langues", "fr-a0-salut-fr", 1, lot("fr-a0-salut-fr", 1))).andExpect(status().isBadRequest());                       // same start and target
        mvc.perform(upload("langues", "cm2", 1, lot("fr-a0-salut-en", 1))).andExpect(status().isBadRequest());                                  // not a Langues scope
        mvc.perform(upload("langues", "fr-a0-salut-en", 1, "pas un zip".getBytes())).andExpect(status().isBadRequest());
        mvc.perform(upload("langues-media", "fr-a0-salut", 1, lot("fr-a0-salut-en", 1))).andExpect(status().isBadRequest());                   // media twin: not handled
        String msg = mvc.perform(upload("physique", "x1", 1, lot("fr-a0-salut-en", 1))).andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        assertTrue(msg.contains("« langues »"), msg);
        String cmsg = mvc.perform(get("/api/v1/lots/catalog").param("feature", "physique")).andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        assertTrue(cmsg.contains("« learn », « quiz » ou « langues »"), cmsg);
    }

    @Test
    void aLanguesLotOverThreeMegabytesIsRefusedButLearnKeepsTheTenMegabyteRule() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(out)) {
            z.setLevel(0);
            z.putNextEntry(new ZipEntry("langue.json")); z.write(("{\"type\":\"langue\",\"id\":\"it-a0-grand-fr\",\"version\":1,\"units\":[{}],\"pad\":\"").getBytes());
            byte[] b = new byte[(3 << 20) + 100]; java.util.Arrays.fill(b, (byte) 'x'); z.write(b); z.write("\"}".getBytes()); z.closeEntry();
        }
        mvc.perform(upload("langues", "it-a0-grand-fr", 1, out.toByteArray())).andExpect(status().isBadRequest());
        mvc.perform(upload("learn", "big4", 1, zipStored(4 << 20))).andExpect(status().isCreated());
    }

    private static byte[] zipStored(int n) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(out)) {
            z.setLevel(0); z.putNextEntry(new ZipEntry("a.bin")); byte[] b = new byte[n]; new java.util.Random(5).nextBytes(b); z.write(b); z.closeEntry();
        }
        return out.toByteArray();
    }
}
