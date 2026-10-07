package castbridge.server.updates;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import castbridge.server.licenses.QrSvg;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/** The QR generator shared by the licence pages (data URI) and the download page (inline element). */
class QrSvgTest {
    private static final String LINK = "https://bridge.sti-cm.com/dl/tv/latest.apk";

    @Test
    void theInlineSvgIsAStandaloneElementThatDecodesBackToTheText() throws Exception {
        String svg = QrSvg.svg(LINK);
        assertNotNull(svg);
        assertTrue(svg.startsWith("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 "));
        assertTrue(svg.endsWith("</svg>"));
        assertFalse(svg.contains("<script") || svg.contains("<style") || svg.contains("href"));
        // exactly the markup the licence pages have always received (white square, one path of 1x1 black modules)
        assertTrue(svg.matches("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 \\d+ \\d+\" shape-rendering=\"crispEdges\">"
                + "<rect width=\"100%\" height=\"100%\" fill=\"#fff\"/><path fill=\"#000\" d=\"(M\\d+ \\d+h1v1h-1z)+\"/></svg>"), svg);
        assertEquals(LINK, QrDecode.decode(svg));
        assertEquals("https://cb.example/castbridge/dl/phone/latest.apk", QrDecode.decode(QrSvg.svg("https://cb.example/castbridge/dl/phone/latest.apk")));
    }

    @Test
    void theDataUriIsTheSameImageEncodedInBase64() {
        String uri = QrSvg.dataUri(LINK);
        assertNotNull(uri);
        assertTrue(uri.startsWith("data:image/svg+xml;base64,"));
        String decoded = new String(Base64.getDecoder().decode(uri.substring("data:image/svg+xml;base64,".length())), StandardCharsets.UTF_8);
        assertEquals(QrSvg.svg(LINK), decoded);
    }

    @Test
    void rootAttributesGoOnTheRootElementOnly() throws Exception {
        String svg = QrSvg.svg(LINK, "role=\"img\" aria-label=\"Code QR\"");
        assertTrue(svg.startsWith("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 "));
        assertEquals(1, svg.split("role=\"img\"", -1).length - 1);
        assertTrue(svg.indexOf("aria-label=\"Code QR\"") < svg.indexOf('>'));
        assertEquals(LINK, QrDecode.decode(svg));
        // no attribute: exactly the markup of the data URI (no stray space)
        assertEquals(QrSvg.svg(LINK), QrSvg.svg(LINK, ""));
    }

    @Test
    void aTextTooLongForAQrCodeGivesNull() {
        assertNull(QrSvg.svg("x".repeat(5000)));
        assertNull(QrSvg.dataUri("x".repeat(5000)));
    }
}
