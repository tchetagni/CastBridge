package castbridge.server.licenses;

import castbridge.server.web.ApiException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Identity of a device as the apps compute it (docs/ACTIVATION-FORMAT.md § 1): a SET of hashed factors (never raw values), the device
 * code {@code XXXX-XXXX-XXXX-XXXX} derived from it, and the k-of-n matching. The server never sees raw hardware values: the owner pastes
 * the "demande d'appareil" shown or sent by the device (code, k and one factor line per factor).
 */
public final class DeviceIdentity {
    private static final Pattern FP = Pattern.compile("[0-9a-f]{32}");

    /** The five factors (bit rank, soldered?). Canonical order = rank order. */
    public enum Factor {
        FLASH(0, true), ETHERNET(1, true), WIFI(2, false), SYSTEM_SERIAL(3, false), BLUETOOTH(4, false);

        public final int rank;
        public final boolean soldered;

        Factor(int rank, boolean soldered) {
            this.rank = rank;
            this.soldered = soldered;
        }
    }

    /** The factor fingerprints of a device, in canonical order, with its device code and k. */
    public record Request(Map<Factor, String> factors, String code, int k) {
        public Request {
            factors = java.util.Collections.unmodifiableMap(new EnumMap<>(factors));
        }

        public String factorsText() {
            List<String> l = new ArrayList<>();
            factors.forEach((f, h) -> l.add(f.name() + "|" + h));
            return String.join(",", l);
        }

        public String setHashHex() { return java.util.HexFormat.of().formatHex(setHash(factors)); }
    }

    private DeviceIdentity() {}

    public static byte[] setHash(Map<Factor, String> fp) {
        List<String> lines = new ArrayList<>();
        Map<Factor, String> sorted = new EnumMap<>(Factor.class);
        sorted.putAll(fp);
        sorted.forEach((f, h) -> lines.add(f.name() + "=" + h));
        return Hashing.sha256(String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
    }

    /** XXXX-XXXX-XXXX-XXXX : mask char + 14 chars of base32(set hash) + check char. */
    public static String code(Map<Factor, String> fp) {
        int mask = 0;
        for (Factor f : fp.keySet()) mask |= 1 << f.rank;
        String body = Crockford.ALPHABET.charAt(mask & 31) + Crockford.encode(setHash(fp)).substring(0, 14);
        String s = body + Crockford.check(body, 0);
        return s.substring(0, 4) + "-" + s.substring(4, 8) + "-" + s.substring(8, 12) + "-" + s.substring(12, 16);
    }

    /** Typed code → canonical code, or null: lower case, spaces and dashes accepted, O→0, I and L→1, exact check character. */
    public static String parseCode(String text) {
        if (text == null) return null;
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (c == '-' || Character.isWhitespace(c)) continue;
            int v = Crockford.value(c);
            if (v < 0) return null;
            sb.append(Crockford.ALPHABET.charAt(v));
        }
        String s = sb.toString();
        if (s.length() != 16 || Crockford.check(s.substring(0, 15), 0) != s.charAt(15)) return null;
        return s.substring(0, 4) + "-" + s.substring(4, 8) + "-" + s.substring(8, 12) + "-" + s.substring(12, 16);
    }

    /** Like {@link #parseCode} but a 400 with a clear message. */
    public static String normalize(String raw) {
        String c = parseCode(raw);
        if (c == null) throw ApiException.badRequest("Code d'appareil invalide : 16 caractères au format XXXX-XXXX-XXXX-XXXX, avec son caractère de contrôle");
        return c;
    }

    /** ABCD-****: shortened form for the audit log and the pages that do not need the whole code. */
    public static String masked(String code) { return code == null || code.length() < 4 ? "?" : code.substring(0, 4) + "-****"; }

    public static int kFor(int n) { return n >= 3 ? n - 1 : Math.max(n, 1); }

    /**
     * The k-of-n rule: an activation carrying set {@code f} and {@code k} matches a device whose set is {@code d} when at least k factors
     * are equal and, if f has a soldered factor, at least one soldered factor is among the matches.
     */
    public static boolean matches(Map<Factor, String> f, int k, Map<Factor, String> d) {
        if (f.isEmpty() || k < 1) return false;
        int hit = 0;
        boolean solderedHit = false, solderedInF = false;
        for (Map.Entry<Factor, String> e : f.entrySet()) {
            if (e.getKey().soldered) solderedInF = true;
            if (e.getValue().equals(d.get(e.getKey()))) {
                hit++;
                if (e.getKey().soldered) solderedHit = true;
            }
        }
        return hit >= k && (!solderedInF || solderedHit);
    }

    /** Parses factors stored as "TYPE|hex,TYPE|hex". */
    public static Map<Factor, String> parseStored(String stored) {
        Map<Factor, String> m = new EnumMap<>(Factor.class);
        if (stored == null || stored.isBlank()) return m;
        for (String p : stored.split(",")) {
            String[] t = p.split("\\|");
            m.put(Factor.valueOf(t[0]), t[1]);
        }
        return m;
    }

    /**
     * Parses the device request: lines {@code code=…}, {@code k=…} and {@code factor=TYPE|32 hex} (the DEVICE_INFO text of the Bluetooth
     * channel). Everything is validated: factor names, fingerprint shape, that the code really derives from the factor set (check character
     * included) and that k is the one the device computes for n factors.
     */
    public static Request parseRequest(String text) {
        if (text == null || text.isBlank()) throw ApiException.badRequest("Demande d'appareil manquante : collez le texte « code=… k=… factor=… » de l'appareil");
        if (text.length() > 2000) throw ApiException.badRequest("Demande d'appareil trop longue");
        Map<Factor, String> fp = new EnumMap<>(Factor.class);
        String code = null;
        Integer k = null;
        for (String raw : text.split("\\R")) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("code=")) {
                if (code != null) throw ApiException.badRequest("Demande d'appareil : « code » en double");
                code = line.substring(5).trim();
            } else if (line.startsWith("k=")) {
                try {
                    k = Integer.parseInt(line.substring(2).trim());
                } catch (NumberFormatException e) {
                    throw ApiException.badRequest("Demande d'appareil : k invalide");
                }
            } else if (line.startsWith("factor=")) {
                String[] p = line.substring(7).split("\\|");
                if (p.length != 2) throw ApiException.badRequest("Demande d'appareil : ligne « factor » invalide");
                Factor f;
                try {
                    f = Factor.valueOf(p[0].trim());
                } catch (IllegalArgumentException e) {
                    throw ApiException.badRequest("Demande d'appareil : facteur inconnu « " + AuditLog.clip(p[0], 20) + " »");
                }
                String h = p[1].trim().toLowerCase(java.util.Locale.ROOT);
                if (!FP.matcher(h).matches()) throw ApiException.badRequest("Demande d'appareil : empreinte invalide pour " + f.name() + " (32 chiffres hexadécimaux)");
                if (fp.put(f, h) != null) throw ApiException.badRequest("Demande d'appareil : facteur " + f.name() + " en double");
            } else if (line.startsWith("install=")) {
                // la TV joint sa clé d'installation (preuve de possession) : tolérée et IGNORÉE, l'identité ne dépend ni de son contenu ni de sa présence (constat du 2026-10-04, additif)
                continue;
            } else {
                throw ApiException.badRequest("Demande d'appareil : ligne inattendue « " + AuditLog.clip(line, 30) + " »");
            }
        }
        if (fp.isEmpty()) throw ApiException.badRequest("Demande d'appareil sans facteur : au moins une ligne « factor=TYPE|empreinte » est nécessaire");
        String derived = code(fp);
        if (code == null) throw ApiException.badRequest("Demande d'appareil : ligne « code=… » manquante");
        String parsed = parseCode(code);
        if (parsed == null) throw ApiException.badRequest("Code d'appareil invalide (caractère de contrôle ou longueur)");
        if (!parsed.equals(derived)) throw ApiException.badRequest("Le code d'appareil ne correspond pas aux facteurs fournis : demande altérée ou incomplète");
        int expectedK = kFor(fp.size());
        if (k != null && k != expectedK) throw ApiException.badRequest("Demande d'appareil : k=" + k + " ne correspond pas aux " + fp.size() + " facteurs (k attendu : " + expectedK + ")");
        return new Request(fp, derived, expectedK);
    }
}
