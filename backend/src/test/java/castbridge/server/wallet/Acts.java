package castbridge.server.wallet;

import castbridge.server.licenses.DeviceIdentity;
import castbridge.server.licenses.DeviceIdentity.Factor;
import castbridge.server.licenses.Envelope;
import castbridge.server.licenses.LicenseKeyring;
import castbridge.server.licenses.WireActivation;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.Signature;
import java.util.Base64;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Fabrique d'activations {@code cbx1} de test, signées par une clé de test (jamais une clé réelle). */
public final class Acts {
    private Acts() {}

    public static final long DAY = 86_400_000L;

    /** Un matériel de test : deux empreintes aléatoires, donc un code d'appareil propre. */
    public record Tv(Map<Factor, String> factors, String code) {
        public static Tv random() {
            Map<Factor, String> f = new EnumMap<>(Factor.class);
            f.put(Factor.FLASH, UUID.randomUUID().toString().replace("-", ""));
            f.put(Factor.ETHERNET, UUID.randomUUID().toString().replace("-", ""));
            return new Tv(f, DeviceIdentity.code(f));
        }
    }

    /** Essai avec plafond d'usage explicite {@code [from, to)} (ms). */
    public static String trial(KeyPair issuer, Tv tv, long issuedAt, long usageFrom, long usageTo) {
        return make(issuer, "trial", tv, issuedAt, List.of("usage|duree|" + usageFrom + "|" + usageTo), 1);
    }

    public static String trialDays(KeyPair issuer, Tv tv, long issuedAt, int days) { return trial(issuer, tv, issuedAt, issuedAt, issuedAt + days * DAY); }

    /** Essai sans droit usage : 30 jours implicites depuis l'émission. */
    public static String trialImplicit(KeyPair issuer, Tv tv, long issuedAt) { return make(issuer, "trial", tv, issuedAt, List.of(), 1); }

    public static String production(KeyPair issuer, Tv tv, long issuedAt, List<String> rights) { return make(issuer, "production", tv, issuedAt, rights, 1); }

    public static String make(KeyPair issuer, String kind, Tv tv, long issuedAt, List<String> rights, long seq) {
        String kid = LicenseKeyring.kidOf(WalletTestBase.rawPublicBytes(issuer));
        boolean trial = kind.equals("trial");
        String license = trial ? WireActivation.TRIAL_LICENSE : "lic-test";
        WireActivation.Fields f = new WireActivation.Fields(kind, "tv", kid, seq, UUID.randomUUID().toString().replace("-", "").substring(0, 16), issuedAt, issuedAt,
                issuedAt + 48 * 3_600_000L, license, WireActivation.defaultSeat(license, tv.factors()), DeviceIdentity.kFor(tv.factors().size()), tv.factors(), rights);
        return WireActivation.token(f, sign(issuer, WireActivation.payload(f)));
    }

    public static byte[] sign(KeyPair p, String payload) {
        try {
            Signature s = Signature.getInstance("Ed25519");
            s.initSign(p.getPrivate());
            s.update(payload.getBytes(StandardCharsets.UTF_8));
            return s.sign();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Jeton de révocation signé (lignes {@code key=<kid>} ou {@code seat=<licence>|<poste>|<ms>}), lu par {@code revocations-file}. */
    public static String revocation(KeyPair issuer, long at, List<String> lines) {
        String kid = LicenseKeyring.kidOf(WalletTestBase.rawPublicBytes(issuer));
        Envelope e = new Envelope("revocation", kid, 1, "00112233445566778899", at, at, at + 365 * DAY, Envelope.Target.ANY, lines, "");
        return e.withSignature(Base64.getEncoder().encodeToString(sign(issuer, e.payload()))).token();
    }
}
