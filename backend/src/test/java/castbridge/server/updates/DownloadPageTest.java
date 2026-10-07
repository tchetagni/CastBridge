package castbridge.server.updates;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The HTML builder on its own: escaping, French formatting, structure, no external reference. */
class DownloadPageTest {
    private static final String BASE = "https://bridge.sti-cm.com";

    private static Release release(String app, String abi, String versionName, long size, String sha, String publishedAt) {
        Release r = new Release();
        r.setApp(app);
        r.setAbi(abi);
        r.setChannel("stable");
        r.setVersionCode(7);
        r.setVersionName(versionName);
        r.setFileName("castbridge-" + app + "-x-7-" + abi + "-" + sha.substring(0, 8) + ".apk");
        r.setSha256(sha);
        r.setSizeBytes(size);
        r.setRolloutPercent(100);
        r.setPublishedAt(Instant.parse(publishedAt));
        return r;
    }

    private static int count(String text, String part) {
        int n = 0;
        for (int i = text.indexOf(part); i >= 0; i = text.indexOf(part, i + part.length())) n++;
        return n;
    }

    @Test
    void escapesTheFiveHtmlSpecialCharacters() {
        assertEquals("&lt;a href=&quot;x&quot; onclick=&#39;y&#39;&gt;&amp;&lt;/a&gt;", DownloadPage.esc("<a href=\"x\" onclick='y'>&</a>"));
        assertEquals("", DownloadPage.esc(null));
        assertEquals("Télécharger › é à ç", DownloadPage.esc("Télécharger › é à ç"));
        assertEquals("&amp;lt;", DownloadPage.esc("&lt;"));
    }

    @Test
    void sizesAreRoundedInFrenchUnits() {
        assertEquals("0 octet", DownloadPage.size(0));
        assertEquals("1 octet", DownloadPage.size(1));
        assertEquals("2 octets", DownloadPage.size(2));
        assertEquals("999 octets", DownloadPage.size(999));
        assertEquals("1 Ko", DownloadPage.size(1_000));
        assertEquals("2 Ko", DownloadPage.size(1_536));
        assertEquals("999 Ko", DownloadPage.size(999_499));
        assertEquals("1,0 Mo", DownloadPage.size(999_500));
        assertEquals("29,5 Mo", DownloadPage.size(29_532_160L));
        assertEquals("999,9 Mo", DownloadPage.size(999_949_999L));
        assertEquals("1,00 Go", DownloadPage.size(999_950_000L));
        assertEquals("2,50 Go", DownloadPage.size(2_500_000_000L));
    }

    @Test
    void datesAreFrenchAndInTheTimeZoneOfTheCountry() {
        assertEquals("7 octobre 2026", DownloadPage.date(Instant.parse("2026-10-07T10:00:00Z")));
        assertEquals("8 octobre 2026", DownloadPage.date(Instant.parse("2026-10-07T23:30:00Z"))); // Douala is UTC+1
        assertEquals("1er novembre 2026", DownloadPage.date(Instant.parse("2026-10-31T23:30:00Z")));
        assertEquals("1er février 2026", DownloadPage.date(Instant.parse("2026-02-01T12:00:00Z")));
        assertEquals("15 août 2026", DownloadPage.date(Instant.parse("2026-08-15T12:00:00Z")));
        assertEquals("25 décembre 2026", DownloadPage.date(Instant.parse("2026-12-25T12:00:00Z")));
    }

    @Test
    void eachOfferedAppHasItsFactsItsButtonAndItsQrCode() {
        String shaTv = "1a".repeat(32), shaPhone = "2b".repeat(32);
        String html = DownloadPage.render(List.of(
                new DownloadPage.Offer("tv", release("tv", "armeabi-v7a", "0.14.44-beta-verrouillee", 29_532_160L, shaTv, "2026-10-07T10:00:00Z")),
                new DownloadPage.Offer("phone", release("phone", "universal", "1.2.52-beta", 41_000_000L, shaPhone, "2026-10-06T10:00:00Z"))),
                BASE, "");
        assertTrue(html.startsWith("<!DOCTYPE html>"));
        assertTrue(html.contains("<html lang=\"fr\">"));
        assertTrue(html.contains("<meta charset=\"utf-8\">"));
        assertTrue(html.contains("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"));
        assertTrue(html.contains("<title>Télécharger CastBridge</title>"));
        assertTrue(html.contains("<h1>Télécharger CastBridge</h1>"));
        assertTrue(html.indexOf("CastBridge-TV") < html.indexOf(">CastBridge<"), "the TV application comes first");
        assertTrue(html.contains("<dd>0.14.44-beta-verrouillee</dd>") && html.contains("<dd>1.2.52-beta</dd>"));
        assertTrue(html.contains("<dd>7 octobre 2026</dd>") && html.contains("<dd>6 octobre 2026</dd>"));
        assertTrue(html.contains("<dd>29,5 Mo</dd>") && html.contains("<dd>41,0 Mo</dd>"));
        assertTrue(html.contains("<dd>armeabi-v7a</dd>") && html.contains("<dd>universal</dd>"));
        assertTrue(html.contains("<dd class=\"sha\">" + shaTv + "</dd>") && html.contains("<dd class=\"sha\">" + shaPhone + "</dd>"));
        assertTrue(html.contains("<a class=\"btn\" href=\"/dl/tv/latest.apk\""));
        assertTrue(html.contains("<a class=\"btn\" href=\"/dl/phone/latest.apk\""));
        assertEquals(2, count(html, "<svg "));
        assertTrue(html.contains(BASE + "/dl/tv/latest.apk") && html.contains(BASE + "/dl/phone/latest.apk"));
        assertTrue(html.contains("<a href=\"/guide/\">"));
        assertFalse(html.contains(DownloadPage.NONE));
    }

    @Test
    void theThreeInstallLinesAreThereWithOrWithoutAVersion() {
        for (List<DownloadPage.Offer> offers : List.of(List.<DownloadPage.Offer>of(), List.of(new DownloadPage.Offer("tv", null)))) {
            String html = DownloadPage.render(offers, BASE, "");
            assertTrue(html.contains(">Installer sur la TV :<"));
            assertTrue(html.contains(">copier l'APK sur une clé USB › ouvrir avec l'explorateur de fichiers de la TV<"));
            assertTrue(html.contains(">ou depuis CastBridge sur le téléphone : CastBridge TV › Mettre à jour la TV<"));
            assertTrue(html.contains("href=\"/guide/\""));
        }
    }

    @Test
    void nothingPublishedShowsTheMessageOnceAndNoButton() {
        for (List<DownloadPage.Offer> offers : List.of(List.<DownloadPage.Offer>of(),
                List.of(new DownloadPage.Offer("tv", null), new DownloadPage.Offer("phone", null)))) {
            String html = DownloadPage.render(offers, BASE, "");
            assertEquals(1, count(html, "Aucune version publiée pour l'instant"), offers.toString());
            assertFalse(html.contains("latest.apk"));
            assertFalse(html.contains("<svg"));
            assertFalse(html.contains("<dl>"));
        }
    }

    @Test
    void anAppWithoutVersionKeepsItsOwnMessage() {
        String html = DownloadPage.render(List.of(new DownloadPage.Offer("tv", null),
                new DownloadPage.Offer("phone", release("phone", "universal", "1.0.0", 1_000_000, "c".repeat(64), "2026-10-07T10:00:00Z"))), BASE, "");
        assertEquals(1, count(html, "Aucune version publiée pour l'instant"));
        assertTrue(html.indexOf("CastBridge-TV") < html.indexOf("Aucune version publiée pour l'instant"));
        assertTrue(html.contains("href=\"/dl/phone/latest.apk\""));
        assertFalse(html.contains("href=\"/dl/tv/latest.apk\""));
    }

    @Test
    void hostileValuesNeverBreakOutOfTheirElement() {
        Release r = release("tv", "arm<b>", "<img src=x onerror=alert(1)>\"'&", 5, "d".repeat(64), "2026-10-07T10:00:00Z");
        r.setSha256("\"><script>alert(2)</script>" + "0".repeat(37));
        String html = DownloadPage.render(List.of(new DownloadPage.Offer("tv", r)), "https://x.example/\"><script>alert(3)</script>", "/pre\"fix");
        assertFalse(html.contains("<img"));
        assertFalse(html.contains("<script"));
        assertFalse(html.contains("<b>"));
        assertTrue(html.contains("&lt;img src=x onerror=alert(1)&gt;&quot;&#39;&amp;"));
        assertTrue(html.contains("arm&lt;b&gt;"));
        assertTrue(html.contains("https://x.example/&quot;&gt;&lt;script&gt;alert(3)&lt;/script&gt;/dl/tv/latest.apk"));
        assertTrue(html.contains("href=\"/pre&quot;fix/dl/tv/latest.apk\""));
    }

    @Test
    void theContextPathPrefixesTheLocalLinksButTheAbsoluteLinkComesFromTheBaseUrl() {
        Release r = release("phone", "universal", "1.0.0", 1_000_000, "e".repeat(64), "2026-10-07T10:00:00Z");
        String html = DownloadPage.render(List.of(new DownloadPage.Offer("phone", r)), "https://example.org/castbridge", "/castbridge");
        assertTrue(html.contains("href=\"/castbridge/dl/phone/latest.apk\""));
        assertTrue(html.contains("href=\"/castbridge/guide/\""));
        assertTrue(html.contains("https://example.org/castbridge/dl/phone/latest.apk"));
    }

    @Test
    void aLinkTooLongForAQrCodeKeepsTheButtonAndTheTextLinkWithoutImage() {
        Release r = release("tv", "universal", "1.0.0", 1_000_000, "f".repeat(64), "2026-10-07T10:00:00Z");
        String html = DownloadPage.render(List.of(new DownloadPage.Offer("tv", r)), "https://example.org/" + "a".repeat(4000), "");
        assertFalse(html.contains("<svg"));
        assertTrue(html.contains("href=\"/dl/tv/latest.apk\""));
        assertTrue(html.contains("/dl/tv/latest.apk</span>"));
    }

    @Test
    void thePageNeedsNoScriptAndLoadsNothingFromElsewhere() {
        Release r = release("tv", "armeabi-v7a", "1.0.0", 1_000_000, "9".repeat(64), "2026-10-07T10:00:00Z");
        String html = DownloadPage.render(List.of(new DownloadPage.Offer("tv", r)), BASE, "").toLowerCase();
        assertFalse(html.contains("<script"));
        assertFalse(html.contains("<link"));
        assertFalse(html.contains("<img"));
        assertFalse(html.contains("<iframe"));
        assertFalse(html.contains("@import"));
        assertFalse(html.contains("url("));
        assertFalse(html.matches("(?s).*(src|href|action)=\"(https?:)?//.*"));
        assertTrue(html.contains("<style>") && html.contains("prefers-color-scheme"));
    }
}
