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
    public static final Pattern LICENSE_ID = Pattern.compile("[A-Z0-9][A-Z0-9-]{5,38}[A-Z0-9]");
    public static final Pattern PRODUCT_ID = Pattern.compile("[a-z0-9][a-z0-9._-]{1,46}[a-z0-9]");
    public static final Pattern LOT_ID = Pattern.compile("[a-z0-9][a-z0-9._/-]{0,62}[a-z0-9]");
    public static final Pattern USERNAME = Pattern.compile("[a-zA-Z0-9][a-zA-Z0-9._@-]{2,62}");

    private Validate() {}

    public static String licenseId(String s) {
        String t = s == null ? "" : s.trim().toUpperCase(java.util.Locale.ROOT);
        if (!LICENSE_ID.matcher(t).matches()) {
            throw ApiException.badRequest("Identifiant de licence invalide : 7 à 40 caractères, lettres majuscules, chiffres et tirets");
        }
        return t;
    }

    public static String productId(String s) {
        String t = s == null ? "" : s.trim();
        if (!PRODUCT_ID.matcher(t).matches()) {
            throw ApiException.badRequest("Identifiant de bouquet invalide : 3 à 48 caractères (minuscules, chiffres, . _ -)");
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
