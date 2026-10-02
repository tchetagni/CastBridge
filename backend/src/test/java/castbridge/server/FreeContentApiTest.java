package castbridge.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import castbridge.server.config.CastbridgeProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** GET /api/v1/free-content: the free content archive (CC BY-SA, public) is streamed (404 when absent, Range/ETag support). */
class FreeContentApiTest extends ApiTestBase {
    @Autowired CastbridgeProperties props;

    @Test void absentThen404() throws Exception {
        Path f = props.freeContent().file();
        Files.deleteIfExists(f);
        String msg = mvc.perform(get("/api/v1/free-content")).andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(msg.contains("Archive des contenus libres"), msg);
    }

    @Test void presentReturns200WithExactBytes() throws Exception {
        Path f = props.freeContent().file();
        Files.createDirectories(f.getParent());
        byte[] content = "PK\u0003\u0004".getBytes(StandardCharsets.UTF_8); // ZIP header signature
        Files.write(f, content);

        var result = mvc.perform(get("/api/v1/free-content")).andExpect(status().isOk()).andReturn();
        byte[] body = result.getResponse().getContentAsByteArray();
        assertEquals(content.length, body.length);
        assertTrue(java.util.Arrays.equals(content, body));
        Files.deleteIfExists(f);
    }

    @Test void etagAnd304NotModified() throws Exception {
        Path f = props.freeContent().file();
        Files.createDirectories(f.getParent());
        Files.write(f, "test content".getBytes(StandardCharsets.UTF_8));

        var first = mvc.perform(get("/api/v1/free-content")).andExpect(status().isOk()).andReturn();
        String etag = first.getResponse().getHeader("ETag");
        assertTrue(etag != null && etag.matches("\"[0-9a-f]{16}\""), etag);

        mvc.perform(get("/api/v1/free-content").header("If-None-Match", etag)).andExpect(status().isNotModified());
        mvc.perform(get("/api/v1/free-content").header("If-None-Match", "\"autre\"")).andExpect(status().isOk());
        Files.deleteIfExists(f);
    }

    @Test void headRequest() throws Exception {
        Path f = props.freeContent().file();
        Files.createDirectories(f.getParent());
        byte[] content = "test content".getBytes(StandardCharsets.UTF_8);
        Files.write(f, content);

        var result = mvc.perform(head("/api/v1/free-content")).andExpect(status().isOk()).andReturn();
        // HEAD request: Content-Length header is set but no body is sent
        assertEquals(String.valueOf(content.length), result.getResponse().getHeader("Content-Length"));
        byte[] body = result.getResponse().getContentAsByteArray();
        assertEquals(0, body.length); // HEAD response has no body
        Files.deleteIfExists(f);
    }

    @Test void rangeRequest206() throws Exception {
        Path f = props.freeContent().file();
        Files.createDirectories(f.getParent());
        byte[] content = "0123456789".getBytes(StandardCharsets.UTF_8);
        Files.write(f, content);

        var result = mvc.perform(get("/api/v1/free-content").header("Range", "bytes=2-5")).andExpect(status().isPartialContent()).andReturn();
        byte[] body = result.getResponse().getContentAsByteArray();
        assertEquals(4, body.length); // 5-2+1 = 4 bytes
        assertEquals("bytes 2-5/10", result.getResponse().getHeader("Content-Range"));
        assertEquals("2345", new String(body, StandardCharsets.UTF_8));
        Files.deleteIfExists(f);
    }

    @Test void rangeRequestOutOfBounds() throws Exception {
        Path f = props.freeContent().file();
        Files.createDirectories(f.getParent());
        byte[] content = "0123456789".getBytes(StandardCharsets.UTF_8);
        Files.write(f, content);

        mvc.perform(get("/api/v1/free-content").header("Range", "bytes=20-30")).andExpect(status().isRequestedRangeNotSatisfiable());
        Files.deleteIfExists(f);
    }

    @Test void fileTooLarge() throws Exception {
        Path f = props.freeContent().file();
        Files.createDirectories(f.getParent());
        // Create a file exceeding the cap (200 MiB)
        byte[] largeContent = new byte[(int) (201L * 1024 * 1024)];
        Files.write(f, largeContent);

        mvc.perform(get("/api/v1/free-content")).andExpect(status().isPayloadTooLarge());
        Files.deleteIfExists(f);
    }

    @Test void infoAbsentReturns404() throws Exception {
        Path f = props.freeContent().file();
        Files.deleteIfExists(f);
        mvc.perform(get("/api/v1/free-content/info")).andExpect(status().isNotFound());
    }

    @Test void infoPresentReturnsMetadata() throws Exception {
        Path f = props.freeContent().file();
        Files.createDirectories(f.getParent());
        byte[] content = "test zip content".getBytes(StandardCharsets.UTF_8);
        Files.write(f, content);

        var result = mvc.perform(get("/api/v1/free-content/info")).andExpect(status().isOk()).andReturn();
        JsonNode info = body(result);

        assertTrue(info.get("available").asBoolean());
        assertEquals(content.length, info.get("sizeBytes").asLong());
        assertTrue(info.get("sha256").isTextual());
        assertEquals(64, info.get("sha256").asText().length()); // Full SHA-256 hex
        assertTrue(info.get("generatedAt").isTextual());
        assertEquals("CC BY-SA", info.get("licence").asText());
        assertEquals("castbridge-contenus-libres.zip", info.get("fileName").asText());
        Files.deleteIfExists(f);
    }

    @Test void infoSha256Correct() throws Exception {
        Path f = props.freeContent().file();
        Files.createDirectories(f.getParent());
        byte[] content = "test content".getBytes(StandardCharsets.UTF_8);
        Files.write(f, content);

        var result = mvc.perform(get("/api/v1/free-content/info")).andExpect(status().isOk()).andReturn();
        JsonNode info = body(result);
        String sha256Returned = info.get("sha256").asText();

        // Compute SHA-256 ourselves to verify
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(content);
        String sha256Expected = HexFormat.of().formatHex(digest);

        assertEquals(sha256Expected, sha256Returned);
        Files.deleteIfExists(f);
    }

    @Test void contentDispositionAttachment() throws Exception {
        Path f = props.freeContent().file();
        Files.createDirectories(f.getParent());
        Files.write(f, "test".getBytes(StandardCharsets.UTF_8));

        var result = mvc.perform(get("/api/v1/free-content")).andExpect(status().isOk()).andReturn();
        String disposition = result.getResponse().getHeader("Content-Disposition");
        assertTrue(disposition != null && disposition.contains("attachment") && disposition.contains("castbridge-contenus-libres.zip"), disposition);
        Files.deleteIfExists(f);
    }

    @Test void acceptRanges() throws Exception {
        Path f = props.freeContent().file();
        Files.createDirectories(f.getParent());
        Files.write(f, "test".getBytes(StandardCharsets.UTF_8));

        var result = mvc.perform(get("/api/v1/free-content")).andExpect(status().isOk()).andReturn();
        assertEquals("bytes", result.getResponse().getHeader("Accept-Ranges"));
        Files.deleteIfExists(f);
    }
}
