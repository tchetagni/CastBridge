package castbridge.server.guide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import castbridge.server.ApiTestBase;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

class GuideControllerTest extends ApiTestBase {
    static final Path DIR = tmp();

    static Path tmp() {
        try { return Files.createTempDirectory("cb-guide"); } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
    }

    @DynamicPropertySource
    static void guide(DynamicPropertyRegistry r) { r.add("castbridge.guide.dir", DIR::toString); }

    @BeforeAll
    static void files() throws Exception {
        Files.writeString(DIR.resolve("index.html"), "<html><body>Guide d'usage : é à ç</body></html>", StandardCharsets.UTF_8);
        Files.write(DIR.resolve("pic.png"), new byte[] {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3});
        Files.writeString(DIR.resolve("notes.exe"), "x");
        Files.writeString(DIR.resolve(".hidden.txt"), "secret");
        Files.writeString(DIR.getParent().resolve("outside-guide.txt"), "hors dossier");
    }

    @Test
    void indexIsServedPubliclyWithCharsetAndHeaders() throws Exception {
        MockHttpServletResponse r = mvc.perform(get("/guide/")).andReturn().getResponse(); // no Authorization header
        assertEquals(200, r.getStatus());
        assertEquals("text/html;charset=UTF-8", r.getContentType());
        assertTrue(r.getContentAsString(StandardCharsets.UTF_8).contains("é à ç"));
        assertEquals("public, max-age=300", r.getHeader("Cache-Control"));
        assertEquals("nosniff", r.getHeader("X-Content-Type-Options"));
        assertEquals(GuideController.CSP, r.getHeader("Content-Security-Policy"));
        assertTrue(r.getHeader("ETag") != null && r.getHeader("Last-Modified") != null);
        assertNull(r.getCookie("JSESSIONID"));
        assertFalse(r.containsHeader("Set-Cookie"));
    }

    @Test
    void conditionalRequestGives304() throws Exception {
        MockHttpServletResponse first = mvc.perform(get("/guide/")).andReturn().getResponse();
        MockHttpServletResponse r = mvc.perform(get("/guide/").header("If-None-Match", first.getHeader("ETag"))).andReturn().getResponse();
        assertEquals(304, r.getStatus());
        assertEquals(0, r.getContentAsByteArray().length);
    }

    @Test
    void imageIsServedWithLongCache() throws Exception {
        MockHttpServletResponse r = mvc.perform(get("/guide/pic.png")).andReturn().getResponse();
        assertEquals(200, r.getStatus());
        assertEquals("image/png", r.getContentType());
        assertEquals("public, max-age=86400", r.getHeader("Cache-Control"));
        assertEquals(7, r.getContentAsByteArray().length);
    }

    @Test
    void traversalAndHiddenNamesAreRefusedWith404() throws Exception {
        for (String name : new String[] {"..", "a..b.txt", "..outside-guide.txt", ".hidden.txt"}) {
            int s = mvc.perform(get("/guide/" + name)).andReturn().getResponse().getStatus();
            assertTrue(s == 404 || s == 400, name + " -> " + s);
        }
        assertEquals(404, mvc.perform(get("/guide/.hidden.txt")).andReturn().getResponse().getStatus());
        assertEquals(404, mvc.perform(get("/guide/a..b.txt")).andReturn().getResponse().getStatus());
        assertFalse(GuideController.allowedName("../x.txt"));
        assertFalse(GuideController.allowedName("a/b.txt"));
        assertFalse(GuideController.allowedName("a\\b.txt"));
        assertTrue(GuideController.allowedName("fig-1.WEBP"));
    }

    @Test
    void forbiddenExtensionAndMissingFileAreNotFound() throws Exception {
        assertEquals(404, mvc.perform(get("/guide/notes.exe")).andReturn().getResponse().getStatus());
        MockHttpServletResponse r = mvc.perform(get("/guide/absent.png")).andReturn().getResponse();
        assertEquals(404, r.getStatus());
        assertEquals("Fichier introuvable", r.getContentAsString(StandardCharsets.UTF_8));
    }

    @Test
    void missingIndexGivesFrenchText404() throws Exception {
        Path index = DIR.resolve("index.html");
        byte[] keep = Files.readAllBytes(index);
        Files.delete(index);
        try {
            MockHttpServletResponse r = mvc.perform(get("/guide/")).andReturn().getResponse();
            assertEquals(404, r.getStatus());
            assertEquals("Guide non publié", r.getContentAsString(StandardCharsets.UTF_8));
        } finally {
            Files.write(index, keep);
        }
    }

    @Test
    void guideWithoutSlashRedirects301() throws Exception {
        MockHttpServletResponse r = mvc.perform(get("/guide")).andReturn().getResponse();
        assertEquals(301, r.getStatus());
        assertEquals("/guide/", r.getHeader("Location"));
    }
}
