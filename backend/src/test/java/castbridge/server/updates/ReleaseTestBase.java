package castbridge.server.updates;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import castbridge.server.ApiTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

/** Publishes releases through the real admin API (so the files exist in the test storage) and retires or throttles them the same way. */
abstract class ReleaseTestBase extends ApiTestBase {
    @Autowired protected ReleaseRepository releases;

    /** Any zip that is not a readable APK manifest: the form is trusted (as in UpdatesApiTest). */
    protected static byte[] plainZip(String marker) throws Exception {
        var bos = new ByteArrayOutputStream();
        try (var z = new ZipOutputStream(bos)) {
            z.putNextEntry(new ZipEntry("classes.dex"));
            z.write(marker.getBytes(StandardCharsets.UTF_8));
            z.closeEntry();
        }
        return bos.toByteArray();
    }

    /** POST /api/v1/admin/releases: a stable or beta release; {@code rollout} null = the default (100 %). */
    protected JsonNode publish(String app, String abi, int versionCode, String versionName, String channel, Integer rollout) throws Exception {
        return publish(app, abi, versionCode, versionName, channel, rollout, "app.apk");
    }

    protected JsonNode publish(String app, String abi, int versionCode, String versionName, String channel, Integer rollout,
                               String uploadedName) throws Exception {
        byte[] apk = plainZip(app + "/" + abi + "/" + channel + "/" + versionCode + "/" + versionName);
        MockMultipartHttpServletRequestBuilder b = multipart("/api/v1/admin/releases")
                .file(new MockMultipartFile("file", uploadedName, "application/vnd.android.package-archive", apk));
        b.param("app", app).param("abi", abi).param("versionCode", String.valueOf(versionCode)).param("versionName", versionName)
                .param("channel", channel).param("notes", "Notes de la version " + versionName).header("Authorization", ADMIN);
        if (rollout != null) b.param("rollout", String.valueOf(rollout));
        return body(mvc.perform(b).andExpect(status().isCreated()).andReturn());
    }

    protected void revoke(JsonNode release) throws Exception {
        mvc.perform(post("/api/v1/admin/releases/" + release.get("id").asLong() + "/revoke").header("Authorization", ADMIN))
                .andExpect(status().isOk());
    }

    protected void rollout(JsonNode release, int percent) throws Exception {
        mvc.perform(post("/api/v1/admin/releases/" + release.get("id").asLong() + "/rollout").param("percent", String.valueOf(percent))
                .header("Authorization", ADMIN)).andExpect(status().isOk());
    }
}
