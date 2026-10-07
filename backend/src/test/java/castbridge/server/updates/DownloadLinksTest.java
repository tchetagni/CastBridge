package castbridge.server.updates;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;

import castbridge.server.guide.GuideController;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Public download addresses: GET|HEAD /dl/{app}/latest.apk (302 to the newest stable, not revoked, fully rolled-out APK) and the page
 * GET /telecharger. The public base URL of the test profile is https://cb.example/castbridge.
 */
class DownloadLinksTest extends ReleaseTestBase {
    private static final String BASE = "https://cb.example/castbridge";

    @BeforeEach
    void emptyCatalogue() { releases.deleteAll(); }

    private MockHttpServletResponse latest(String app) throws Exception {
        return mvc.perform(get("/dl/" + app + "/latest.apk")).andReturn().getResponse();
    }

    private MockHttpServletResponse latest(String app, String abi) throws Exception {
        return mvc.perform(get("/dl/" + app + "/latest.apk").param("abi", abi)).andReturn().getResponse();
    }

    private static String target(JsonNode release) { return "/dl/" + release.get("app").asText() + "/" + release.get("fileName").asText(); }

    private String page() throws Exception {
        MockHttpServletResponse r = mvc.perform(get("/telecharger")).andReturn().getResponse();
        assertEquals(200, r.getStatus());
        return r.getContentAsString(StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------------------------------------ /dl/{app}/latest.apk

    @Test
    void redirectsToTheHighestStableVersionWithTheDocumentedHeaders() throws Exception {
        publish("tv", "armeabi-v7a", 10, "1.0.10", "stable", null);
        JsonNode v11 = publish("tv", "armeabi-v7a", 11, "1.0.11", "stable", null);

        MockHttpServletResponse r = latest("tv");
        assertEquals(302, r.getStatus());
        assertEquals(target(v11), r.getHeader("Location"));
        assertEquals("no-store", r.getHeader("Cache-Control"));
        assertEquals(v11.get("sha256").asText(), r.getHeader("X-Content-SHA256"));
        assertEquals("1.0.11", r.getHeader("X-CastBridge-Version"));
        assertEquals(0, r.getContentAsByteArray().length);
        assertFalse(r.containsHeader("Set-Cookie"));

        // the target is the unchanged immutable download route
        MockHttpServletResponse file = mvc.perform(get(r.getHeader("Location"))).andReturn().getResponse();
        assertEquals(200, file.getStatus());
        assertEquals("\"" + v11.get("sha256").asText() + "\"", file.getHeader("ETag"));
        assertEquals("public, max-age=31536000, immutable", file.getHeader("Cache-Control"));
        assertEquals(v11.get("size").asLong(), file.getContentAsByteArray().length);
    }

    @Test
    void headAnswersLikeGetAndTheOtherAppIsIndependent() throws Exception {
        JsonNode tv = publish("tv", "armeabi-v7a", 10, "1.0.10", "stable", null);
        JsonNode phone = publish("phone", "universal", 5, "2.0.5", "stable", null);

        MockHttpServletResponse h = mvc.perform(head("/dl/tv/latest.apk")).andReturn().getResponse();
        assertEquals(302, h.getStatus());
        assertEquals(target(tv), h.getHeader("Location"));
        assertEquals(tv.get("sha256").asText(), h.getHeader("X-Content-SHA256"));
        assertEquals("1.0.10", h.getHeader("X-CastBridge-Version"));
        assertEquals("no-store", h.getHeader("Cache-Control"));
        assertEquals(0, h.getContentAsByteArray().length);

        assertEquals(target(phone), latest("phone").getHeader("Location"));
    }

    @Test
    void aRevokedVersionIsNeverTheTargetAndTheNextOneTakesOver() throws Exception {
        JsonNode v10 = publish("tv", "armeabi-v7a", 10, "1.0.10", "stable", null);
        JsonNode v11 = publish("tv", "armeabi-v7a", 11, "1.0.11", "stable", null);
        assertEquals(target(v11), latest("tv").getHeader("Location"));
        revoke(v11);
        assertEquals(target(v10), latest("tv").getHeader("Location"));
        revoke(v10);
        assertEquals(404, latest("tv").getStatus());
    }

    @Test
    void aPartialRolloutIsNotServedUntilItReaches100Percent() throws Exception {
        JsonNode v10 = publish("tv", "armeabi-v7a", 10, "1.0.10", "stable", null);
        JsonNode half = publish("tv", "armeabi-v7a", 11, "1.0.11", "stable", 50);
        JsonNode zero = publish("tv", "armeabi-v7a", 12, "1.0.12", "stable", 0);
        assertEquals(target(v10), latest("tv").getHeader("Location"));
        rollout(half, 99);
        assertEquals(target(v10), latest("tv").getHeader("Location"));
        rollout(half, 100);
        assertEquals(target(half), latest("tv").getHeader("Location"));
        rollout(zero, 100);
        assertEquals(target(zero), latest("tv").getHeader("Location"));
    }

    @Test
    void theBetaChannelIsNeverServed() throws Exception {
        JsonNode stable = publish("tv", "armeabi-v7a", 10, "1.0.10", "stable", null);
        publish("tv", "armeabi-v7a", 11, "1.1.0-beta", "beta", null);
        assertEquals(target(stable), latest("tv").getHeader("Location"));
        assertEquals("1.0.10", latest("tv").getHeader("X-CastBridge-Version"));
        revoke(stable);
        MockHttpServletResponse none = latest("tv"); // only a beta is left: nothing stable to offer
        assertEquals(404, none.getStatus());
    }

    @Test
    void withoutAbiATvPrefers32BitThenUniversalThenArm64() throws Exception {
        JsonNode arm64 = publish("tv", "arm64-v8a", 20, "2.0.0", "stable", null);
        JsonNode universal = publish("tv", "universal", 20, "2.0.0", "stable", null);
        JsonNode v7a = publish("tv", "armeabi-v7a", 20, "2.0.0", "stable", null);
        publish("tv", "x86", 20, "2.0.0", "stable", null);
        publish("tv", "x86_64", 20, "2.0.0", "stable", null);
        assertEquals(target(v7a), latest("tv").getHeader("Location"));
        revoke(v7a);
        assertEquals(target(universal), latest("tv").getHeader("Location"));
        revoke(universal);
        assertEquals(target(arm64), latest("tv").getHeader("Location"));
        revoke(arm64);
        assertEquals(404, latest("tv").getStatus()); // x86 and x86_64 are never chosen when no ABI is asked
    }

    @Test
    void theHighestVersionCodeWinsBeforeTheAbiPreference() throws Exception {
        publish("tv", "armeabi-v7a", 10, "1.0.10", "stable", null);
        JsonNode newerUniversal = publish("tv", "universal", 11, "1.0.11", "stable", null);
        assertEquals(target(newerUniversal), latest("tv").getHeader("Location"));
    }

    @Test
    void aPhonePrefersTheUniversalApk() throws Exception {
        JsonNode v7a = publish("phone", "armeabi-v7a", 7, "3.0.7", "stable", null);
        JsonNode arm64 = publish("phone", "arm64-v8a", 7, "3.0.7", "stable", null);
        JsonNode universal = publish("phone", "universal", 7, "3.0.7", "stable", null);
        assertEquals(target(universal), latest("phone").getHeader("Location"));
        revoke(universal);
        assertEquals(target(arm64), latest("phone").getHeader("Location"));
        revoke(arm64);
        assertEquals(target(v7a), latest("phone").getHeader("Location"));
    }

    @Test
    void theAbiParameterPicksThatAbiThenFallsBackToUniversalOnly() throws Exception {
        JsonNode v7a = publish("tv", "armeabi-v7a", 30, "3.0.0", "stable", null);
        JsonNode arm64 = publish("tv", "arm64-v8a", 30, "3.0.0", "stable", null);
        assertEquals(target(arm64), latest("tv", "arm64-v8a").getHeader("Location"));
        assertEquals(target(v7a), latest("tv", "armeabi-v7a").getHeader("Location"));
        // nothing for x86, and no 32/64-bit guess for another architecture
        MockHttpServletResponse none = latest("tv", "x86");
        assertEquals(404, none.getStatus());
        assertTrue(none.getContentAsString(StandardCharsets.UTF_8).contains("x86"));
        // the universal APK is the fallback of every architecture
        JsonNode universal = publish("tv", "universal", 29, "2.9.9", "stable", null);
        assertEquals(target(arm64), latest("tv", "arm64-v8a").getHeader("Location")); // higher versionCode
        revoke(arm64);
        assertEquals(target(universal), latest("tv", "arm64-v8a").getHeader("Location"));
        assertEquals(target(universal), latest("tv", "x86").getHeader("Location"));
        assertEquals(target(universal), latest("tv", "universal").getHeader("Location"));
        // a blank value is the same as no value
        assertEquals(target(v7a), latest("tv", " ").getHeader("Location"));
    }

    @Test
    void anUnknownArchitectureIsRefusedInFrench() throws Exception {
        publish("tv", "armeabi-v7a", 30, "3.0.0", "stable", null);
        MockHttpServletResponse r = latest("tv", "mips");
        assertEquals(400, r.getStatus());
        JsonNode e = json.readTree(r.getContentAsByteArray());
        assertTrue(e.get("message").asText().startsWith("abi : "), e.toString());
        assertTrue(e.get("message").asText().endsWith(" attendu"), e.toString());
    }

    @Test
    void withoutAnyPublishedVersionTheAnswerIsAFrench404() throws Exception {
        MockHttpServletResponse r = latest("tv");
        assertEquals(404, r.getStatus());
        assertEquals("no-store", r.getHeader("Cache-Control"));
        assertFalse(r.containsHeader("Location"));
        JsonNode e = json.readTree(r.getContentAsByteArray());
        assertEquals(404, e.get("status").asInt());
        assertEquals("Introuvable", e.get("erreur").asText());
        assertTrue(e.get("message").asText().startsWith("Aucune version publiée pour l'instant"), e.toString());
        assertTrue(e.get("message").asText().contains("CastBridge-TV"), e.toString());
        assertTrue(json.readTree(latest("phone").getContentAsByteArray()).get("message").asText().contains("CastBridge"));
        assertEquals(404, mvc.perform(head("/dl/tv/latest.apk")).andReturn().getResponse().getStatus());
    }

    @Test
    void anUnknownAppIsNotFound() throws Exception {
        publish("tv", "armeabi-v7a", 10, "1.0.10", "stable", null);
        assertEquals(404, latest("tv2").getStatus());
        assertEquals(404, latest("TV").getStatus());
        assertEquals(404, mvc.perform(get("/dl/tv/latest.apks")).andReturn().getResponse().getStatus());
        assertEquals(404, mvc.perform(get("/dl/tv/Latest.apk")).andReturn().getResponse().getStatus());
    }

    @Test
    void latestApkCanNeverBeAPublishedFileName() throws Exception {
        // even a file uploaded under that name, with a version name that ends like it, is stored under castbridge-<app>-…
        JsonNode r = publish("tv", "armeabi-v7a", 40, "latest.apk", "stable", null, "latest.apk");
        assertTrue(r.get("fileName").asText().startsWith("castbridge-tv-"), r.toString());
        assertTrue(releases.findByAppAndFileName("tv", "latest.apk").isEmpty());
        // so the reserved name always goes to the redirect, never to the file download
        MockHttpServletResponse res = latest("tv");
        assertEquals(302, res.getStatus());
        assertEquals(target(r), res.getHeader("Location"));
        assertEquals("latest.apk", res.getHeader("X-CastBridge-Version"));
        // and a regular file name is still served by the unchanged route
        assertEquals(200, mvc.perform(get(target(r))).andReturn().getResponse().getStatus());
    }

    @Test
    void theStableAddressNeedsNoCredentialAndSetsNoCookie() throws Exception {
        publish("tv", "armeabi-v7a", 10, "1.0.10", "stable", null);
        MockHttpServletResponse r = mvc.perform(get("/dl/tv/latest.apk").header("Authorization", "Bearer wrong-token-wrong-token-wrong-token-xx"))
                .andReturn().getResponse();
        assertEquals(302, r.getStatus());
        assertFalse(r.containsHeader("Set-Cookie"));
        assertNull(r.getCookie("JSESSIONID"));
    }

    // ------------------------------------------------------------------------------------------------ /telecharger

    @Test
    void thePageShowsTheLatestStableVersionOfEachAppAndNothingElse() throws Exception {
        JsonNode tv = publish("tv", "armeabi-v7a", 115, "0.14.44-beta-verrouillee", "stable", null);
        JsonNode phone = publish("phone", "universal", 82, "1.2.52-beta", "stable", null);
        // what must stay invisible: an older stable, a revoked one, a beta, a partial rollout
        List<JsonNode> hidden = new ArrayList<>();
        hidden.add(publish("tv", "armeabi-v7a", 114, "0.14.43-beta", "stable", null));
        JsonNode revoked = publish("tv", "armeabi-v7a", 120, "0.14.99-retiree", "stable", null);
        revoke(revoked);
        hidden.add(revoked);
        hidden.add(publish("tv", "armeabi-v7a", 130, "0.15.0-canal-beta", "beta", null));
        hidden.add(publish("tv", "armeabi-v7a", 140, "0.16.0-partielle", "stable", 50));
        JsonNode phoneRevoked = publish("phone", "universal", 90, "1.9.0-retiree", "stable", null);
        revoke(phoneRevoked);
        hidden.add(phoneRevoked);

        String html = page();
        assertTrue(html.contains("<title>Télécharger CastBridge</title>"));
        assertTrue(html.contains("<h1>Télécharger CastBridge</h1>"));
        for (JsonNode shown : List.of(tv, phone)) {
            String app = shown.get("app").asText();
            assertTrue(html.contains(">" + shown.get("versionName").asText() + "<"), "version of " + app);
            assertTrue(html.contains(shown.get("sha256").asText()), "sha256 of " + app);
            assertTrue(html.contains(DownloadPage.size(shown.get("size").asLong())), "size of " + app);
            assertTrue(html.contains(DownloadPage.date(OffsetDateTime.parse(shown.get("publishedAt").asText()).toInstant())), "date of " + app);
            assertTrue(html.contains("href=\"/dl/" + app + "/latest.apk\""), "button of " + app);
            assertTrue(html.contains(BASE + "/dl/" + app + "/latest.apk"), "visible stable link of " + app);
        }
        assertTrue(html.contains(">Télécharger<"));
        assertTrue(html.contains("CastBridge-TV"));
        for (JsonNode h : hidden) {
            assertFalse(html.contains(h.get("versionName").asText()), "hidden version " + h.get("versionName").asText());
            assertFalse(html.contains(h.get("sha256").asText()), "hidden hash of " + h.get("versionName").asText());
        }
        // the page only knows the stable address: no file name of any release
        assertFalse(html.contains("castbridge-tv-"));
        assertFalse(html.contains("castbridge-phone-"));
    }

    @Test
    void thePageCarriesTheThreeInstallLinesTheGuideLinkAndNoInventedSupportNumber() throws Exception {
        publish("tv", "armeabi-v7a", 115, "0.14.44-beta-verrouillee", "stable", null);
        String html = page();
        assertTrue(html.contains("Installer sur la TV :"));
        assertTrue(html.contains("copier l'APK sur une clé USB › ouvrir avec l'explorateur de fichiers de la TV"));
        assertTrue(html.contains("ou depuis CastBridge sur le téléphone : CastBridge TV › Mettre à jour la TV"));
        assertTrue(html.contains("href=\"/guide/\""));
        assertFalse(html.contains("+237"));
    }

    @Test
    void theQrCodesEncodeTheStableAddresses() throws Exception {
        publish("tv", "armeabi-v7a", 115, "0.14.44-beta-verrouillee", "stable", null);
        publish("phone", "universal", 82, "1.2.52-beta", "stable", null);
        String html = page();
        List<String> svgs = new ArrayList<>();
        Matcher m = Pattern.compile("(?s)<svg .*?</svg>").matcher(html);
        while (m.find()) svgs.add(m.group());
        assertEquals(2, svgs.size());
        assertEquals(BASE + "/dl/tv/latest.apk", QrDecode.decode(svgs.get(0)));
        assertEquals(BASE + "/dl/phone/latest.apk", QrDecode.decode(svgs.get(1)));
        assertTrue(svgs.get(0).contains("role=\"img\"") && svgs.get(0).contains("aria-label=\""));
    }

    @Test
    void thePageIsSelfContainedAndSafe() throws Exception {
        publish("tv", "armeabi-v7a", 115, "0.14.44-beta-verrouillee", "stable", null);
        publish("phone", "universal", 82, "1.2.52-beta", "stable", null);
        String html = page().toLowerCase();
        assertFalse(html.contains("<script"));
        assertFalse(html.contains("<link"));
        assertFalse(html.contains("<iframe"));
        assertFalse(html.contains("<img"));
        assertFalse(html.contains("javascript:"));
        assertFalse(html.contains("@import"));
        assertFalse(html.matches("(?s).*(src|href|action)=\"(https?:)?//.*"), "no reference to another site");
        assertFalse(html.matches("(?s).*\\son[a-z]+=.*"), "no inline event handler");
    }

    @Test
    void thePageHasTheSameHeadersAsTheGuideAndIsCachedFiveMinutes() throws Exception {
        publish("tv", "armeabi-v7a", 115, "0.14.44-beta-verrouillee", "stable", null);
        for (String path : new String[] {"/telecharger", "/telecharger/"}) {
            MockHttpServletResponse r = mvc.perform(get(path)).andReturn().getResponse(); // no Authorization header
            assertEquals(200, r.getStatus(), path);
            assertEquals("text/html;charset=UTF-8", r.getContentType(), path);
            assertEquals("public, max-age=300", r.getHeader("Cache-Control"), path);
            assertEquals("nosniff", r.getHeader("X-Content-Type-Options"), path);
            assertEquals(GuideController.CSP, r.getHeader("Content-Security-Policy"), path);
            assertEquals("no-referrer", r.getHeader("Referrer-Policy"), path);
            assertEquals("DENY", r.getHeader("X-Frame-Options"), path);
            assertNotNull(r.getHeader("ETag"), path);
            assertFalse(r.containsHeader("Set-Cookie"), path);
            assertNull(r.getCookie("JSESSIONID"), path);
        }
    }

    @Test
    void aConditionalRequestGives304AndAHeadAnswersLikeAGet() throws Exception {
        publish("tv", "armeabi-v7a", 115, "0.14.44-beta-verrouillee", "stable", null);
        MockHttpServletResponse first = mvc.perform(get("/telecharger")).andReturn().getResponse();
        MockHttpServletResponse again = mvc.perform(get("/telecharger/").header("If-None-Match", first.getHeader("ETag"))).andReturn().getResponse();
        assertEquals(304, again.getStatus());
        assertEquals(0, again.getContentAsByteArray().length);
        // a new publication changes the page, so the old validator no longer matches
        publish("tv", "armeabi-v7a", 116, "0.14.45-beta-verrouillee", "stable", null);
        MockHttpServletResponse changed = mvc.perform(get("/telecharger").header("If-None-Match", first.getHeader("ETag"))).andReturn().getResponse();
        assertEquals(200, changed.getStatus());
        assertTrue(changed.getContentAsString(StandardCharsets.UTF_8).contains("0.14.45-beta-verrouillee"));

        // HEAD: the same headers as GET (the servlet container, not MockMvc, drops the body)
        MockHttpServletResponse plain = mvc.perform(get("/telecharger")).andReturn().getResponse();
        MockHttpServletResponse h = mvc.perform(head("/telecharger")).andReturn().getResponse();
        assertEquals(200, h.getStatus());
        assertEquals("text/html;charset=UTF-8", h.getContentType());
        assertEquals(plain.getHeader("ETag"), h.getHeader("ETag"));
        assertEquals("public, max-age=300", h.getHeader("Cache-Control"));
    }

    @Test
    void theNewPublicationAppearsAtOnceAndARevocationDisappearsAtOnce() throws Exception {
        JsonNode v1 = publish("phone", "universal", 1, "1.0.1", "stable", null);
        assertTrue(page().contains(">1.0.1<"));
        JsonNode v2 = publish("phone", "universal", 2, "1.0.2", "stable", null);
        String html = page();
        assertTrue(html.contains(">1.0.2<") && !html.contains(">1.0.1<"));
        revoke(v2);
        html = page();
        assertTrue(html.contains(">1.0.1<") && !html.contains("1.0.2"));
        revoke(v1);
        assertFalse(page().contains("1.0.1"));
    }

    @Test
    void withNothingPublishedThePageSaysSoAndOffersNoButton() throws Exception {
        String html = page();
        assertEquals(1, count(html, "Aucune version publiée pour l'instant"));
        assertFalse(html.contains("latest.apk"));
        assertFalse(html.contains("<svg"));
        assertTrue(html.contains("Installer sur la TV :"));
        assertTrue(html.contains("href=\"/guide/\""));
    }

    @Test
    void anAppWithoutVersionKeepsItsOwnMessageWhileTheOtherOneIsOffered() throws Exception {
        publish("phone", "universal", 82, "1.2.52-beta", "stable", null);
        String html = page();
        assertEquals(1, count(html, "Aucune version publiée pour l'instant"));
        assertTrue(html.contains("href=\"/dl/phone/latest.apk\""));
        assertFalse(html.contains("href=\"/dl/tv/latest.apk\""));
        assertEquals(1, count(html, "<svg"));
    }

    @Test
    void everyValueComingFromTheDatabaseIsEscaped() throws Exception {
        // a row written behind the API's back (the publication form would refuse these characters)
        String evilHash = "\"><script>alert(2)</script>";
        Release r = new Release();
        r.setApp("tv");
        r.setAbi("armeabi-v7a");
        r.setChannel("stable");
        r.setVersionCode(900);
        r.setVersionName("<img src=x onerror=alert(1)>&\"'");
        r.setFileName("castbridge-tv-hostile-900-armeabi-v7a-deadbeef.apk");
        r.setSha256(evilHash + "0".repeat(64 - evilHash.length()));
        r.setSizeBytes(1234);
        r.setRolloutPercent(100);
        r.setPublishedAt(Instant.parse("2026-10-07T10:00:00Z"));
        releases.save(r);

        String html = page();
        assertTrue(html.contains("&lt;img src=x onerror=alert(1)&gt;&amp;&quot;&#39;"), html);
        assertFalse(html.contains("<img"));
        assertFalse(html.contains("<script"));
        assertTrue(html.contains("&quot;&gt;&lt;script&gt;alert(2)&lt;/script&gt;"));
    }

    @Test
    void theSizeAndTheDateAreShownInFrenchAndInTheLocalTimeZone() throws Exception {
        Release r = new Release();
        r.setApp("phone");
        r.setAbi("universal");
        r.setChannel("stable");
        r.setVersionCode(901);
        r.setVersionName("1.2.3");
        r.setFileName("castbridge-phone-1.2.3-901-universal-cafebabe.apk");
        r.setSha256("a".repeat(64));
        r.setSizeBytes(29_532_160L);
        r.setRolloutPercent(100);
        r.setPublishedAt(Instant.parse("2026-10-07T23:30:00Z")); // 8 October, 00:30 in Douala (UTC+1)
        releases.save(r);
        String html = page();
        assertTrue(html.contains("29,5 Mo"), html);
        assertTrue(html.contains("8 octobre 2026"), html);
    }

    private static int count(String text, String part) {
        int n = 0;
        for (int i = text.indexOf(part); i >= 0; i = text.indexOf(part, i + part.length())) n++;
        return n;
    }
}
