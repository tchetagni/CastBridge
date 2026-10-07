package castbridge.server.updates;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;

import castbridge.server.ApiTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * The per-IP limiter (burst 3) also covers the new public routes, whatever the spelling of the path: one bucket per address,
 * shared with the other public routes, and no way to reach the page or the stable link without passing through it.
 */
@TestPropertySource(properties = {"castbridge.rate-limit.per-minute=6", "castbridge.rate-limit.burst=3"})
class DownloadLinksRateLimitTest extends ApiTestBase {
    private static RequestPostProcessor from(String ip) {
        return r -> { r.setRemoteAddr(ip); return r; };
    }

    private int status(RequestBuilder request) throws Exception {
        return mvc.perform(request).andReturn().getResponse().getStatus();
    }

    @Test
    void theStableAddressIsLimitedPerIp() throws Exception {
        String ip = "203.0.113.21";
        for (int i = 0; i < 3; i++) assertEquals(404, status(get("/dl/tv/latest.apk").with(from(ip))), "nothing published: answered, not limited");
        MockHttpServletResponse r = mvc.perform(get("/dl/tv/latest.apk").with(from(ip))).andReturn().getResponse();
        assertEquals(429, r.getStatus());
        assertNotNull(r.getHeader("Retry-After"));
        JsonNode e = json.readTree(r.getContentAsByteArray());
        assertTrue(e.get("message").asText().startsWith("Trop de requêtes"), e.toString());
        assertEquals(429, status(head("/dl/phone/latest.apk").with(from(ip))), "HEAD and the other app share the same bucket");
        assertEquals(429, status(get("/dl/tv/latest.apk").param("abi", "universal").with(from(ip))));
        // another address is not affected; the admin token is never limited
        assertEquals(404, status(get("/dl/tv/latest.apk").with(from("203.0.113.22"))));
        assertEquals(404, status(get("/dl/tv/latest.apk").header("Authorization", ADMIN).with(from(ip))));
    }

    @Test
    void thePageIsLimitedPerIpWhateverTheMethodOrTheTrailingSlash() throws Exception {
        String ip = "203.0.113.23";
        assertEquals(200, status(get("/telecharger").with(from(ip))));
        assertEquals(200, status(get("/telecharger/").with(from(ip))));
        assertEquals(200, status(head("/telecharger").with(from(ip))));
        assertEquals(429, status(get("/telecharger/").with(from(ip))));
        assertEquals(429, status(get("/telecharger").with(from(ip))));
        assertEquals(200, status(get("/telecharger").with(from("203.0.113.24"))));
    }

    @Test
    void oneBucketPerAddressCoversTheStableLinkThePageAndTheGuide() throws Exception {
        String ip = "203.0.113.25";
        status(get("/dl/tv/latest.apk").with(from(ip)));
        status(get("/guide/").with(from(ip)));
        status(get("/api/v1/updates/public-key").with(from(ip)));
        assertEquals(429, status(get("/telecharger").with(from(ip))), "the bucket is shared: a new route is no new budget");
    }

    @Test
    void forwardedAddressIsTheClientAddress() throws Exception {
        for (int i = 0; i < 3; i++) status(get("/telecharger").header("X-Forwarded-For", "198.51.100.9"));
        assertEquals(429, status(get("/telecharger").header("X-Forwarded-For", "198.51.100.9")));
        assertEquals(200, status(get("/telecharger").header("X-Forwarded-For", "198.51.100.10")));
    }

    /**
     * How many of 10 requests with this (already percent-encoded) spelling of a path really reach the controller, from one address.
     * With a burst of 3 the answer must never exceed 3: a spelling that the controllers decode to a limited route but that the
     * limiter does not recognise (it only looked at the raw URI) would be served ten times out of ten.
     */
    private int reachedOutOf10(String spelling, String ip, Predicate<MockHttpServletResponse> reachedController) throws Exception {
        int reached = 0;
        for (int i = 0; i < 10; i++) {
            try {
                // get(URI) keeps the path as written: a string template would encode "%" again as "%25"
                MockHttpServletResponse r = mvc.perform(get(URI.create(spelling)).with(from(ip))).andReturn().getResponse();
                if (reachedController.test(r)) reached++;
            } catch (Exception refusedByTheFirewall) {
                // a path the firewall refuses reaches no controller
            }
        }
        return reached;
    }

    private static boolean page(MockHttpServletResponse r) {
        return r.getStatus() == 200 && r.getContentType() != null && r.getContentType().startsWith("text/html");
    }

    private static String contentOf(MockHttpServletResponse r) {
        try {
            return r.getContentAsString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    @Test
    void noSpellingOfThePathReachesAPublicRouteOutsideTheLimiter() throws Exception {
        // %74 = "t", %64 = "d", %6C = "l", %67 = "g", %61 = "a": the container hands the RAW uri to the filters and the DECODED path to Spring MVC
        int n = 100;
        for (String spelling : new String[] {"/%74elecharger", "/%74elecharger/", "/telecharger;a=b", "/telecharger/.", "/./telecharger", "/%74%65lecharger"}) {
            assertTrue(reachedOutOf10(spelling, "198.51.100." + n++, DownloadLinksRateLimitTest::page) <= 3, "page " + spelling);
        }
        for (String spelling : new String[] {"/%64l/tv/latest.apk", "/dl/tv/%6Catest.apk", "/dl/tv/latest.apk;a=b", "/%64l/%74v/%6Catest.apk"}) {
            assertTrue(reachedOutOf10(spelling, "198.51.101." + n++,
                    r -> r.getStatus() == 404 && contentOf(r).contains("Aucune version publiée")) <= 3, "stable link " + spelling);
        }
        // the routes that were already public: same rule
        assertTrue(reachedOutOf10("/%67uide/", "198.51.102." + n++, r -> r.getStatus() == 404 && "Guide non publié".equals(contentOf(r))) <= 3, "guide");
        assertTrue(reachedOutOf10("/%61pi/v1/updates/public-key", "198.51.102." + n++, r -> r.getStatus() == 200) <= 3, "api");
        assertTrue(reachedOutOf10("/%64l/tv/castbridge-tv-x-1-armeabi-v7a-00000000.apk", "198.51.102." + n++,
                r -> r.getStatus() == 404 && contentOf(r).contains("Fichier introuvable")) <= 3, "dl file");
    }

    @Test
    void theCanonicalAndTheEncodedSpellingsShareOneBucket() throws Exception {
        // also checks the helper itself: the canonical spelling is served exactly burst (3) times out of 10 ...
        assertEquals(3, reachedOutOf10("/telecharger", "198.51.103.7", DownloadLinksRateLimitTest::page));
        // ... and the same address is then limited whatever the spelling
        assertEquals(0, reachedOutOf10("/%74elecharger", "198.51.103.7", DownloadLinksRateLimitTest::page));
    }

    @Test
    void theBodyOfALimitedAnswerIsFrenchJson() throws Exception {
        String ip = "203.0.113.26";
        for (int i = 0; i < 3; i++) status(get("/telecharger").with(from(ip)));
        MockHttpServletResponse r = mvc.perform(get("/telecharger").with(from(ip))).andReturn().getResponse();
        assertEquals(429, r.getStatus());
        assertEquals("no-store", r.getHeader("Cache-Control"));
        assertTrue(r.getContentAsString(StandardCharsets.UTF_8).contains("Trop de requêtes"));
    }
}
