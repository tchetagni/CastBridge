package castbridge.server.updates;

import castbridge.server.CastbridgeApplication;
import castbridge.server.licenses.QrSvg;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * The public download page (GET /telecharger) as ONE self-contained HTML document built with plain strings: no template engine, no script,
 * no external resource (inline CSS, inline SVG QR codes). Every value that comes from the database, the configuration or the request is
 * HTML-escaped; the fixed texts are written once here.
 */
final class DownloadPage {
    static final String NONE = "Aucune version publiée pour l'instant";
    static final String INSTALL_TITLE = "Installer sur la TV :";
    static final String INSTALL_USB = "copier l'APK sur une clé USB › ouvrir avec l'explorateur de fichiers de la TV";
    static final String INSTALL_PHONE = "ou depuis CastBridge sur le téléphone : CastBridge TV › Mettre à jour la TV";

    private static final String LEAD = "Les dernières versions stables des applications.";
    private static final String LEAD_LINKS = " Les liens de cette page ne changent jamais : ils mènent toujours à la version la plus récente.";

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM uuuu", Locale.FRENCH).withZone(CastbridgeApplication.ZONE);

    /** Self-contained style sheet (no url(), no @import, no external font): light and dark themes, 48 px touch targets. */
    private static final String CSS = """
            :root{color-scheme:light dark}
            *{box-sizing:border-box}
            body{margin:0;background:#f4f6f8;color:#14202b;font:16px/1.5 system-ui,-apple-system,"Segoe UI",Roboto,"Helvetica Neue",Arial,sans-serif}
            main{max-width:42rem;margin:0 auto;padding:1.25rem 1rem 2.5rem}
            h1{margin:.5rem 0 .25rem;font-size:1.6rem;line-height:1.2}
            h2{margin:0;font-size:1.2rem;line-height:1.3}
            .lead,.sub{margin:.25rem 0 1rem;color:#4a5b6a}
            .sub{margin-top:.1rem}
            .card,.help{margin:0 0 1rem;padding:1rem 1.1rem;background:#fff;border:1px solid #d5dde5;border-radius:12px}
            dl{margin:0 0 1rem}
            dt{margin-top:.6rem;font-size:.85rem;font-weight:600;color:#4a5b6a}
            dd{margin:0}
            .sha,.url{font:.85rem/1.45 ui-monospace,SFMono-Regular,Menlo,Consolas,monospace;word-wrap:break-word;overflow-wrap:anywhere}
            a{color:#0b6bcb}
            a:focus{outline:3px solid #f5b400;outline-offset:2px}
            .btn{display:inline-block;min-height:48px;padding:.7rem 1.6rem;border-radius:10px;background:#0b6bcb;color:#fff;font-weight:700;text-decoration:none}
            .btn:hover,.btn:focus{background:#09539f}
            .qr{display:flex;flex-wrap:wrap;align-items:center;margin-top:1.1rem}
            .qr svg{width:9.5rem;height:9.5rem;margin:0 1rem .5rem 0;padding:.5rem;background:#fff;border:1px solid #d5dde5;border-radius:8px}
            .qr p{flex:1;min-width:12rem;margin:0 0 .5rem}
            .none{font-weight:600}
            .help p{margin:.3rem 0 0}
            .guide{margin:1.25rem 0 0}
            @media (prefers-color-scheme:dark){body{background:#10171e;color:#e6edf3}.lead,.sub,dt{color:#9fb0bf}.card,.help{background:#18222c;border-color:#2c3a47}a{color:#79bbff}.btn{background:#4aa3ff;color:#06121f}.btn:hover,.btn:focus{background:#79bbff}.qr svg{border-color:#2c3a47}}
            """;

    private DownloadPage() {}

    /** One application of the page; {@code release} is null when nothing stable is offered for it. */
    record Offer(String app, Release release) {}

    /**
     * @param offers      the applications in display order, each with the release its stable address leads to (or null)
     * @param baseUrl     public base URL without trailing slash ("https://bridge.sti-cm.com"): the QR codes and the text links are absolute
     * @param contextPath prefix of the local links ("" at the root of the site, "/castbridge" behind a path prefix)
     */
    static String render(List<Offer> offers, String baseUrl, String contextPath) {
        String ctx = esc(contextPath == null ? "" : contextPath);
        boolean any = offers.stream().anyMatch(o -> o.release() != null);
        StringBuilder h = new StringBuilder(any ? 24_000 : 6_000);
        h.append("<!DOCTYPE html>\n<html lang=\"fr\">\n<head>\n<meta charset=\"utf-8\">\n")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
                .append("<meta name=\"color-scheme\" content=\"light dark\">\n")
                .append("<title>Télécharger CastBridge</title>\n<style>\n").append(CSS).append("</style>\n</head>\n<body>\n<main>\n")
                .append("<h1>Télécharger CastBridge</h1>\n<p class=\"lead\">").append(LEAD).append(any ? LEAD_LINKS : "").append("</p>\n");
        if (any) {
            for (Offer o : offers) card(h, o, baseUrl, ctx);
        } else {
            h.append("<p class=\"none\">").append(NONE).append("</p>\n");
        }
        h.append("<section class=\"help\" aria-labelledby=\"h-help\">\n<h2 id=\"h-help\">").append(INSTALL_TITLE).append("</h2>\n")
                .append("<p>").append(INSTALL_USB).append("</p>\n<p>").append(INSTALL_PHONE).append("</p>\n</section>\n")
                .append("<p class=\"guide\">Besoin d'aide ? Ouvrez le <a href=\"").append(ctx).append("/guide/\">guide utilisateur</a>.</p>\n")
                .append("</main>\n</body>\n</html>\n");
        return h.toString();
    }

    /** {@code ctx} is already escaped. */
    private static void card(StringBuilder h, Offer o, String baseUrl, String ctx) {
        String app = esc(o.app());
        String label = esc(ReleaseService.appLabel(o.app()));
        h.append("<section class=\"card\" id=\"").append(app).append("\" aria-labelledby=\"h-").append(app).append("\">\n")
                .append("<h2 id=\"h-").append(app).append("\">").append(label).append("</h2>\n<p class=\"sub\">")
                .append("tv".equals(o.app()) ? "Application de la télévision" : "Application du téléphone Android").append("</p>\n");
        Release r = o.release();
        if (r == null) {
            h.append("<p class=\"none\">").append(NONE).append("</p>\n</section>\n");
            return;
        }
        String link = baseUrl + "/dl/" + o.app() + "/latest.apk";
        h.append("<dl>\n<dt>Version</dt><dd>").append(esc(r.getVersionName())).append("</dd>\n")
                .append("<dt>Publiée le</dt><dd>").append(esc(date(r.getPublishedAt()))).append("</dd>\n")
                .append("<dt>Taille</dt><dd>").append(esc(size(r.getSizeBytes()))).append("</dd>\n")
                .append("<dt>Architecture</dt><dd>").append(esc(r.getAbi())).append("</dd>\n")
                .append("<dt>SHA-256</dt><dd class=\"sha\">").append(esc(r.getSha256())).append("</dd>\n</dl>\n")
                .append("<p><a class=\"btn\" href=\"").append(ctx).append("/dl/").append(app).append("/latest.apk\" aria-label=\"Télécharger ")
                .append(label).append("\">Télécharger</a></p>\n<div class=\"qr\">\n");
        String qr = QrSvg.svg(link, "role=\"img\" aria-label=\"" + esc("Code QR du lien de téléchargement de " + ReleaseService.appLabel(o.app())) + "\"");
        if (qr != null) h.append(qr).append('\n');
        h.append("<p>").append(qr != null ? "Scannez ce code avec le téléphone, ou copiez le lien :" : "Lien à copier :")
                .append("<br><span class=\"url\">").append(esc(link)).append("</span></p>\n</div>\n</section>\n");
    }

    /** The five characters that matter in HTML text and in a double- or single-quoted attribute. */
    static String esc(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&' -> b.append("&amp;");
                case '<' -> b.append("&lt;");
                case '>' -> b.append("&gt;");
                case '"' -> b.append("&quot;");
                case '\'' -> b.append("&#39;");
                default -> b.append(c);
            }
        }
        return b.toString();
    }

    /** File size in French decimal units ("29,5 Mo"), the units a phone shows. */
    static String size(long bytes) {
        if (bytes < 1_000) return bytes + (bytes < 2 ? " octet" : " octets");
        if (bytes < 999_500) return String.format(Locale.FRANCE, "%.0f Ko", bytes / 1e3);
        if (bytes < 999_950_000L) return String.format(Locale.FRANCE, "%.1f Mo", bytes / 1e6);
        return String.format(Locale.FRANCE, "%.2f Go", bytes / 1e9);
    }

    /** Long French date in the country's time zone ("7 octobre 2026", "1er novembre 2026"). */
    static String date(Instant at) {
        if (at == null) return "";
        boolean firstOfMonth = ZonedDateTime.ofInstant(at, CastbridgeApplication.ZONE).getDayOfMonth() == 1;
        String text = DATE.format(at);
        return firstOfMonth ? "1er" + text.substring(1) : text;
    }
}
