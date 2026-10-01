package castbridge.server.licenses;

import castbridge.server.CastbridgeApplication;
import castbridge.server.web.ApiException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

/** Strict validation of every input of the module (pages, API, files): length, alphabet, range, French messages. */
public final class Validate {
    /** Licence id as written in the activations (docs/ACTIVATION-FORMAT.md § 3.2: [a-z0-9][a-z0-9-]{0,63}); "trial" is reserved for trial keys. */
    public static final Pattern LICENSE_ID = Pattern.compile("[a-z0-9][a-z0-9-]{2,63}");
    /** Words used by the pages of the module: a licence cannot take them as an identifier. */
    private static final java.util.Set<String> RESERVED = java.util.Set.of("list", "new", "issue", "device", "clients", "products", "audit", "registry", "security", "export");
    public static final Pattern PRODUCT_ID = Pattern.compile("[a-z0-9][a-z0-9-]{1,63}");
    public static final Pattern BUNDLE_ID = Pattern.compile("[a-z0-9][a-z0-9-]{0,63}");
    public static final Pattern SEAT_ID = Pattern.compile("[0-9a-f]{16}");
    public static final Pattern LOT_ID = Pattern.compile("[a-z0-9][a-z0-9._/-]{0,62}[a-z0-9]");
    public static final Pattern USERNAME = Pattern.compile("[a-zA-Z0-9][a-zA-Z0-9._@-]{2,62}");

    private Validate() {}

    public static String licenseId(String s) {
        String t = s == null ? "" : s.trim().toLowerCase(java.util.Locale.ROOT);
        if (!LICENSE_ID.matcher(t).matches() || t.equals(WireActivation.TRIAL_LICENSE) || RESERVED.contains(t)) {
            throw ApiException.badRequest("Identifiant de licence invalide : 3 à 64 caractères (minuscules, chiffres, tirets), « trial » est réservé aux clés d'essai");
        }
        return t;
    }

    public static String seatId(String s) {
        String t = s == null ? "" : s.trim().toLowerCase(java.util.Locale.ROOT);
        if (!SEAT_ID.matcher(t).matches()) throw ApiException.badRequest("Identifiant de poste invalide (16 chiffres hexadécimaux)");
        return t;
    }

    public static String bundleId(String s) {
        String t = s == null ? "" : s.trim();
        if (!BUNDLE_ID.matcher(t).matches()) throw ApiException.badRequest("Identifiant de bouquet de contenu invalide : « " + AuditLog.clip(t, 30) + " » (minuscules, chiffres, tirets)");
        return t;
    }

    public static String productId(String s) {
        String t = s == null ? "" : s.trim();
        if (!PRODUCT_ID.matcher(t).matches()) {
            throw ApiException.badRequest("Identifiant de produit invalide : 2 à 64 caractères (minuscules, chiffres, tirets)");
        }
        return t;
    }

    public static String lotId(String s) {
        String t = s == null ? "" : s.trim();
        if (!LOT_ID.matcher(t).matches()) throw ApiException.badRequest("Identifiant de lot invalide : « " + AuditLog.clip(t, 30) + " »");
        return t;
    }

    public static String text(String s, String what, int max, boolean required) {
        String t = s == null ? "" : s.replaceAll("[\\p{Cntrl}&&[^\\n]]", " ").trim();
        if (t.isEmpty()) {
            if (required) throw ApiException.badRequest(what + " obligatoire");
            return null;
        }
        if (t.length() > max) throw ApiException.badRequest(what + " trop long (" + max + " caractères au maximum)");
        return t;
    }

    /** The reason demanded before every destructive action: 3 to 500 characters. */
    public static String reason(String s) {
        String t = text(s, "Motif", 500, false);
        if (t == null || t.length() < 3) throw ApiException.badRequest("Un motif (3 caractères au moins) est obligatoire pour cette action");
        return t;
    }

    public static int range(Integer v, String what, int min, int max) {
        if (v == null || v < min || v > max) throw ApiException.badRequest(what + " : un entier de " + min + " à " + max + " est attendu");
        return v;
    }

    /** ISO instant ("2027-01-31T23:59:59Z") or a date ("2027-01-31", end of that day in Cameroon time when endOfDay). */
    public static Instant instant(String s, String what, boolean endOfDay) {
        if (s == null || s.isBlank()) return null;
        String t = s.trim();
        try {
            if (t.length() == 10) {
                LocalDate d = LocalDate.parse(t);
                return endOfDay ? d.atTime(LocalTime.of(23, 59, 59)).atZone(CastbridgeApplication.ZONE).toInstant()
                        : d.atStartOfDay(CastbridgeApplication.ZONE).toInstant();
            }
            return Instant.parse(t);
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest(what + " : date invalide (AAAA-MM-JJ ou AAAA-MM-JJThh:mm:ssZ)");
        }
    }

    /** Escapes a value for LIKE … ESCAPE '!'. */
    public static String like(String s) { return "%" + s.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%"; }

    public static int page(Integer p) { return p == null || p < 0 ? 0 : Math.min(p, 100_000); }

    public static int size(Integer s, int def) { return s == null ? def : Math.max(1, Math.min(s, 100)); }
}
