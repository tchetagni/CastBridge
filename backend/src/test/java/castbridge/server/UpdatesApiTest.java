package castbridge.server;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import castbridge.server.updates.UpdateManifest;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

class UpdatesApiTest extends ApiTestBase {

    private MockMultipartHttpServletRequestBuilder publish(String app, String abi, int versionCode, String channel, byte[] apk) {
        return (MockMultipartHttpServletRequestBuilder) multipart("/api/v1/admin/releases")
                .file(new MockMultipartFile("file", "app.apk", "application/vnd.android.package-archive", apk))
                .param("app", app).param("abi", abi).param("versionCode", String.valueOf(versionCode)).param("versionName", "0.6-test")
                .param("channel", channel).param("notes", "Nouveautés : lecteur plus rapide.")
                .header("Authorization", ADMIN);
    }

    /** Any zip that is not an APK manifest we can read: the form is trusted. */
    private static byte[] plainZip(String marker) throws Exception {
        var bos = new java.io.ByteArrayOutputStream();
        try (var z = new java.util.zip.ZipOutputStream(bos)) {
            z.putNextEntry(new java.util.zip.ZipEntry("classes.dex"));
            z.write(marker.getBytes(StandardCharsets.UTF_8));
            z.closeEntry();
        }
        return bos.toByteArray();
    }

    @Test
    void adminRoutesNeedTheToken() throws Exception {
        mvc.perform(get("/api/v1/admin/releases")).andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.message").value("Jeton d'administration manquant ou invalide"));
        mvc.perform(get("/api/v1/admin/releases").header("Authorization", "Bearer wrong-token-wrong-token-wrong-token-xx"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
        mvc.perform(get("/v3/api-docs").header("Authorization", ADMIN)).andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("CastBridge server"));
        mvc.perform(get("/api/v1/updates/public-key")).andExpect(status().isOk())
                .andExpect(jsonPath("$.publicKey").value(PUBLIC_KEY_B64))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().exists("Content-Security-Policy"));
    }

    @Test
    void publishCheckDownloadRolloutRevoke() throws Exception {
        byte[] apk = testApk();
        // the APK says castbridge.receiver 42: a phone release or another versionCode is refused
        mvc.perform(publish("phone", "universal", 42, "stable", apk)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]").value(org.hamcrest.Matchers.containsString("castbridge.receiver")));
        mvc.perform(publish("tv", "armeabi-v7a", 43, "stable", apk)).andExpect(status().isBadRequest());
        mvc.perform(publish("tv", "armeabi-v7a", 42, "stable", "not a zip".getBytes())).andExpect(status().isBadRequest());

        JsonNode rel = body(mvc.perform(publish("tv", "armeabi-v7a", 42, "stable", apk)).andExpect(status().isCreated()).andReturn());
        assertEquals("castbridge.receiver", rel.get("packageName").asText());
        assertEquals(26, rel.get("minSdk").asInt());
        String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(apk));
        assertEquals(sha, rel.get("sha256").asText());
        mvc.perform(publish("tv", "armeabi-v7a", 42, "stable", apk)).andExpect(status().isConflict());

        // a universal APK of the same version exists too: the ABI-specific one must be preferred
        mvc.perform(publish("tv", "universal", 42, "stable", plainZip("u42"))).andExpect(status().isCreated());

        JsonNode m = body(mvc.perform(get("/api/v1/updates/tv/latest").param("abis", "armeabi-v7a,armeabi").param("versionCode", "7")
                .param("deviceId", "device-000001")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")).andReturn());
        assertEquals(42, m.get("versionCode").asInt());
        assertEquals("armeabi-v7a", m.get("abi").asText());
        assertEquals(sha, m.get("sha256").asText());
        assertEquals(apk.length, m.get("size").asLong());
        assertFalse(m.get("mandatory").asBoolean());
        assertTrue(m.get("url").asText().startsWith("https://cb.example/castbridge/dl/tv/castbridge-tv-0.6-test-42-armeabi-v7a-"));
        assertTrue(verify(m), "manifest signature verifies with the published public key");

        // an x86_64 device gets the universal APK
        assertEquals("universal", body(mvc.perform(get("/api/v1/updates/tv/latest").param("abis", "x86_64,x86").param("versionCode", "7"))
                .andExpect(status().isOk()).andReturn()).get("abi").asText());
        // up to date: 204
        mvc.perform(get("/api/v1/updates/tv/latest").param("abi", "armeabi-v7a").param("versionCode", "42")).andExpect(status().isNoContent());
        // unknown app / abi
        mvc.perform(get("/api/v1/updates/tv2/latest")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/updates/tv/latest").param("abi", "mips")).andExpect(status().isBadRequest());

        // download: full, HEAD, range (resume), If-None-Match, unsatisfiable range
        String path = new java.net.URI(m.get("url").asText()).getPath().substring("/castbridge".length());
        byte[] all = mvc.perform(get(path)).andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"" + sha + "\"")).andExpect(header().string("Accept-Ranges", "bytes"))
                .andExpect(header().string("Content-Length", String.valueOf(apk.length)))
                .andReturn().getResponse().getContentAsByteArray();
        assertArrayEquals(apk, all);
        mvc.perform(head(path)).andExpect(status().isOk()).andExpect(header().string("Content-Length", String.valueOf(apk.length)));
        byte[] tail = mvc.perform(get(path).header("Range", "bytes=100-").header("If-Range", "\"" + sha + "\""))
                .andExpect(status().isPartialContent())
                .andExpect(header().string("Content-Range", "bytes 100-" + (apk.length - 1) + "/" + apk.length))
                .andReturn().getResponse().getContentAsByteArray();
        assertArrayEquals(Arrays.copyOfRange(apk, 100, apk.length), tail);
        mvc.perform(get(path).header("Range", "bytes=10-19")).andExpect(status().isPartialContent())
                .andExpect(header().string("Content-Length", "10"));
        mvc.perform(get(path).header("Range", "bytes=100-").header("If-Range", "\"other\"")).andExpect(status().isOk());
        mvc.perform(get(path).header("If-None-Match", "\"" + sha + "\"")).andExpect(status().isNotModified());
        mvc.perform(get(path).header("Range", "bytes=999999-")).andExpect(status().isRequestedRangeNotSatisfiable())
                .andExpect(header().string("Content-Range", "bytes */" + apk.length));
        mvc.perform(get("/dl/tv/../../etc/passwd")).andExpect(status().is4xxClientError());

        // rollout 0 %: nobody gets it (apart from the universal one still at 100 %)
        long id = rel.get("id").asLong();
        mvc.perform(post("/api/v1/admin/releases/" + id + "/rollout").param("percent", "0").header("Authorization", ADMIN))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rolloutPercent").value(0));
        assertEquals("universal", body(mvc.perform(get("/api/v1/updates/tv/latest").param("abis", "armeabi-v7a").param("versionCode", "7")
                .param("deviceId", "device-000001")).andReturn()).get("abi").asText());

        // oldest supported version above the device: mandatory, even outside the rollout
        mvc.perform(put("/api/v1/admin/update-policies/tv/stable").param("minSupportedVersionCode", "40").header("Authorization", ADMIN))
                .andExpect(status().isOk());
        JsonNode forced = body(mvc.perform(get("/api/v1/updates/tv/latest").param("abis", "armeabi-v7a").param("versionCode", "7")
                .param("deviceId", "device-000001")).andExpect(status().isOk()).andReturn());
        assertEquals("armeabi-v7a", forced.get("abi").asText());
        assertTrue(forced.get("mandatory").asBoolean());
        assertEquals(40, forced.get("minSupportedVersionCode").asInt());
        assertTrue(verify(forced));

        // revoke: no longer offered, download gone
        mvc.perform(post("/api/v1/admin/releases/" + id + "/revoke").header("Authorization", ADMIN)).andExpect(status().isOk());
        mvc.perform(get(path)).andExpect(status().isGone());
        JsonNode after = body(mvc.perform(get("/api/v1/updates/tv/latest").param("abis", "armeabi-v7a").param("versionCode", "7")).andReturn());
        assertEquals("universal", after.get("abi").asText());

        mvc.perform(get("/api/v1/admin/releases").param("app", "tv").header("Authorization", ADMIN)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/admin/releases/" + id)
                .header("Authorization", ADMIN)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/admin/releases/" + id).header("Authorization", ADMIN)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.erreur").value("Introuvable"));
    }

    @Test
    void betaChannelAndPartialRollout() throws Exception {
        mvc.perform(publish("phone", "universal", 5, "stable", plainZip("p5"))).andExpect(status().isCreated());
        JsonNode beta = body(mvc.perform(publish("phone", "universal", 6, "beta", plainZip("p6")).param("rollout", "50"))
                .andExpect(status().isCreated()).andReturn());
        assertEquals("illisible : informations du formulaire utilisées", beta.get("inspection").asText());
        // stable devices never see beta releases
        assertEquals(5, body(mvc.perform(get("/api/v1/updates/phone/latest").param("versionCode", "4")).andReturn()).get("versionCode").asInt());
        // beta devices: about half get 6 (stable hash of the deviceId), the others 5
        int got6 = 0;
        for (int i = 0; i < 60; i++) {
            JsonNode m = body(mvc.perform(get("/api/v1/updates/phone/latest").param("channel", "beta").param("versionCode", "4")
                    .param("deviceId", "beta-device-" + i)).andReturn());
            if (m.get("versionCode").asInt() == 6) got6++;
        }
        assertTrue(got6 > 15 && got6 < 45, "half of the beta devices: " + got6);
        // without deviceId: only full rollouts
        assertEquals(5, body(mvc.perform(get("/api/v1/updates/phone/latest").param("channel", "beta").param("versionCode", "4"))
                .andReturn()).get("versionCode").asInt());
    }

    /** Verifies a manifest exactly as the apps do: canonical text rebuilt from the fields, Ed25519 public key. */
    private boolean verify(JsonNode m) throws Exception {
        UpdateManifest um = json.treeToValue(m, UpdateManifest.class);
        byte[] raw = Base64.getDecoder().decode(PUBLIC_KEY_B64);
        byte[] spki = new byte[44];
        System.arraycopy(HexFormat.of().parseHex("302a300506032b6570032100"), 0, spki, 0, 12);
        System.arraycopy(raw, 0, spki, 12, 32);
        Signature v = Signature.getInstance("Ed25519");
        v.initVerify(KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(spki)));
        v.update(um.canonicalPayload().getBytes(StandardCharsets.UTF_8));
        return v.verify(Base64.getDecoder().decode(m.get("signature").asText()));
    }
}
