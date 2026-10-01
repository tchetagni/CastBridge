package castbridge.server.licenses;

import castbridge.server.web.ApiException;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The device code of a TV or phone: XXXX-XXXX-XXXX-XXXX (4 groups of 4 letters or digits). The factor field and the exact
 * alphabet belong to docs/ACTIVATION-FORMAT.md (branch claude/trial-edition, not published yet): this class is the only
 * place to adapt once it is. Typed codes are normalized (upper case, spaces and missing dashes tolerated, O→0 and I→1 as
 * the planned input format does).
 */
public final class DeviceCode {
    private static final Pattern CODE = Pattern.compile("[0-9A-Z]{4}(-[0-9A-Z]{4}){3}");

    private DeviceCode() {}

    /** @return the normalized code; throws a 400 if it is not a device code */
    public static String normalize(String raw) {
        if (raw == null) throw ApiException.badRequest("Code d'appareil manquant");
        String s = raw.trim().toUpperCase(Locale.ROOT).replace(" ", "").replace("-", "").replace('O', '0').replace('I', '1');
        if (s.length() != 16 || !s.matches("[0-9A-Z]{16}")) {
            throw ApiException.badRequest("Code d'appareil invalide : 16 caractères attendus, au format XXXX-XXXX-XXXX-XXXX");
        }
        String out = s.substring(0, 4) + "-" + s.substring(4, 8) + "-" + s.substring(8, 12) + "-" + s.substring(12);
        if (!CODE.matcher(out).matches()) throw ApiException.badRequest("Code d'appareil invalide");
        return out;
    }

    /** Shortened form for the audit log and the pages that do not need the full code: ABCD-…-…-WXYZ → ABCD-****. */
    public static String masked(String code) { return code == null || code.length() < 4 ? "?" : code.substring(0, 4) + "-****"; }
}
