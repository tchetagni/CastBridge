package castbridge.server.licenses;

import castbridge.server.licenses.ActivationSigner.ActivationRequest;
import castbridge.server.licenses.ActivationSigner.IssueKind;
import castbridge.server.licenses.ActivationSigner.SignedActivation;
import castbridge.server.web.ApiException;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues (and re-issues) activations with the SERVER key. Rules:
 * <ul>
 *   <li>the scope of the key is enforced before anything is written: no transfer, no "open all" (403);</li>
 *   <li>a new device consumes a seat (OWNER); the same device again consumes nothing and gets the SAME activation back
 *       (idempotence: the nonce and the issue date come from the stored row, Ed25519 is deterministic);</li>
 *   <li>nothing is issued for a suspended, revoked or expired licence; a licence inside its grace period only re-issues;</li>
 *   <li>the activation text is returned once and never stored nor logged: only its SHA-256 fingerprint is kept.</li>
 * </ul>
 */
@Service
public class ActivationService {
    private final JdbcTemplate jdbc;
    private final LicenseService licenses;
    private final ScopedActivationSigner signer;
    private final ActivationEncoder encoder;
    private final AuditLog audit;
    private final LicenseProperties props;

    public ActivationService(JdbcTemplate jdbc, LicenseService licenses, ScopedActivationSigner signer, ActivationEncoder encoder, AuditLog audit,
                             LicenseProperties props) {
        this.jdbc = jdbc;
        this.licenses = licenses;
        this.signer = signer;
        this.encoder = encoder;
        this.audit = audit;
        this.props = props;
    }

    /** @param kind optional: PURCHASE, SUBSCRIPTION, TRIAL or REACTIVATION; anything else is refused by the scope of the server key */
    public record IssueRequest(String licenseId, String deviceCode, String kind, List<String> productIds, String factorsHash) {}

    public record Activation(String text, String kid, String nonce, String fingerprint, String kind, Instant issuedAt, Instant expiresAt,
                             String licenseId, String deviceCode, boolean reused, String format) {}

    public String format() { return encoder.formatName(); }

    @Transactional
    public Activation issue(Actor actor, IssueRequest req, String channel) { return issue(actor, req, channel, false); }

    /** Re-issue for a seat that already exists (support role allowed): never consumes a seat. */
    @Transactional
    public Activation reissue(Actor actor, String licenseId, String deviceCode, String channel) {
        return issue(actor, new IssueRequest(licenseId, deviceCode, null, null, null), channel, true);
    }

    private Activation issue(Actor actor, IssueRequest req, String channel, boolean reissueOnly) {
        actor.require(Role.Permission.REISSUE, props.requireTotp());
        String licenseId = Validate.licenseId(req.licenseId());
        String code = DeviceCode.normalize(req.deviceCode());
        IssueKind asked = parseKind(req.kind());
        // 1. scope first: a forbidden request writes nothing (not even a seat)
        if (asked != null || (req.productIds() != null && req.productIds().contains("*"))) {
            signer.check(new ActivationRequest(licenseId, code, asked == null ? IssueKind.PURCHASE : asked, req.productIds(), Instant.now(), null,
                    new byte[16], 1));
        }
        LicenseService.LicenseRow l = licenses.lock(licenseId);
        String eff = l.effectiveState();
        switch (eff) {
            case "ACTIVE" -> { }
            case "GRACE" -> { if (!existingSeat(l, code)) throw ApiException.conflict("Licence expirée (période de grâce) : seule la réémission d'un poste existant est possible"); }
            case "SUSPENDED" -> throw ApiException.conflict("Licence suspendue : aucune activation n'est émise");
            case "REVOKED" -> throw ApiException.conflict("Licence révoquée : aucune activation n'est émise");
            default -> throw ApiException.conflict("Licence expirée : prolongez-la avant d'émettre");
        }
        boolean hasSeat = existingSeat(l, code);
        if (reissueOnly && !hasSeat) throw ApiException.notFound("Ce code d'appareil n'a pas de poste actif dans cette licence : l'émission pour un nouvel appareil est réservée au propriétaire");
        if (!hasSeat) actor.require(Role.Permission.ISSUE_NEW, props.requireTotp());

        List<LicenseService.ProductRef> owned = licenses.productsOf(l.id());
        Instant now = Instant.now();
        List<String> products = new TreeSet<>(owned.stream().filter(p -> p.endsAt() == null || p.endsAt().isAfter(now)).map(LicenseService.ProductRef::productId).toList())
                .stream().toList();
        if (req.productIds() != null && !req.productIds().isEmpty()) {
            for (String p : req.productIds()) if (!products.contains(p)) throw ApiException.badRequest("Bouquet absent de cette licence : " + AuditLog.clip(p, 48));
            products = new TreeSet<>(req.productIds()).stream().toList();
        }
        IssueKind kind = hasSeat ? lastKind(l, code, asked) : (asked != null ? asked : defaultKind(l, owned));
        if (l.kind().equals("TRIAL") && kind != IssueKind.TRIAL && kind != IssueKind.REACTIVATION) throw ApiException.badRequest("Une licence d'essai ne délivre que des activations d'essai");
        Instant expires = l.endAt();
        String idem = Hashing.sha256Hex(String.join("|", "v1", licenseId, code, kind.name(), String.join(",", products), expires == null ? "-" : Long.toString(expires.getEpochSecond()),
                Integer.toString(l.seatsAllowed())));

        // 2. idempotence: the same request returns the same activation, without a new seat nor a new row
        List<Map<String, Object>> prior = jdbc.queryForList("SELECT * FROM lic_issuance WHERE idem_key = ?", idem);
        if (!prior.isEmpty()) {
            Map<String, Object> p = prior.get(0);
            Instant issuedAt = ((Timestamp) p.get("issued_at")).toInstant();
            ActivationRequest r = new ActivationRequest(licenseId, code, kind, products, issuedAt, expires, HexFormat.of().parseHex((String) p.get("nonce")), l.seatsAllowed());
            SignedActivation s = signer.sign(r);
            if (!s.fingerprint().equals(p.get("token_fingerprint"))) {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "La réémission ne correspond pas à l'activation d'origine (clé ou format changés) : contactez le propriétaire");
            }
            licenses.allocateSeat(l, code, req.factorsHash(), now);
            audit.record(actor, "ACTIVATION_REISSUE", "LICENSE", licenseId, null, Map.of("device", DeviceCode.masked(code), "fp", s.fingerprint().substring(0, 12), "channel", channel));
            return new Activation(s.text(), s.kid(), s.nonceHex(), s.fingerprint(), kind.name(), issuedAt, expires, licenseId, code, true, encoder.formatName());
        }

        // 3. new activation: seat first (under the lock), then signature; a failure rolls everything back
        LicenseService.SeatOutcome seat = licenses.allocateSeat(l, code, req.factorsHash(), now);
        byte[] nonce = java.util.Arrays.copyOf(Hashing.sha256(("nonce|" + idem).getBytes(StandardCharsets.UTF_8)), 16);
        Instant issuedAt = now.truncatedTo(ChronoUnit.MICROS);
        SignedActivation s = signer.sign(new ActivationRequest(licenseId, code, kind, products, issuedAt, expires, nonce, l.seatsAllowed()));
        Long seatId = jdbc.queryForObject("SELECT id FROM lic_seat WHERE license_pk = ? AND device_code = ?", Long.class, l.id(), code);
        try {
            jdbc.update("INSERT INTO lic_issuance (license_pk, seat_pk, device_code, kind, kid, nonce, issued_at, expires_at, issuer, channel, token_fingerprint, source, idem_key)"
                            + " VALUES (?,?,?,?,?,?,?,?,?,?,?,'SERVER',?)",
                    l.id(), seatId, code, kind.name(), s.kid(), s.nonceHex(), Timestamp.from(issuedAt), LicenseService.ts(expires), actor.name(), channel, s.fingerprint(), idem);
        } catch (DuplicateKeyException e) {
            throw ApiException.conflict("Une émission identique vient d'être enregistrée : recommencez pour la récupérer");
        }
        audit.record(actor, "ACTIVATION_ISSUE", "LICENSE", licenseId, null,
                Map.of("device", DeviceCode.masked(code), "kind", kind.name(), "seat", seat.name(), "kid", s.kid(), "fp", s.fingerprint().substring(0, 12), "channel", channel));
        return new Activation(s.text(), s.kid(), s.nonceHex(), s.fingerprint(), kind.name(), issuedAt, expires, licenseId, code, false, encoder.formatName());
    }

    private boolean existingSeat(LicenseService.LicenseRow l, String code) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM lic_seat WHERE license_pk = ? AND device_code = ? AND state = 'ACTIVE'", Integer.class, l.id(), code);
        return n != null && n > 0;
    }

    /** The kind of the activation already given to this seat (a re-issue gives back the same one). */
    private IssueKind lastKind(LicenseService.LicenseRow l, String code, IssueKind asked) {
        List<String> k = jdbc.queryForList("SELECT kind FROM lic_issuance WHERE license_pk = ? AND device_code = ? AND source = 'SERVER' ORDER BY id DESC LIMIT 1", String.class, l.id(), code);
        if (asked != null) return asked;
        return k.isEmpty() ? IssueKind.REACTIVATION : IssueKind.valueOf(k.get(0));
    }

    private static IssueKind defaultKind(LicenseService.LicenseRow l, List<LicenseService.ProductRef> owned) {
        if (l.kind().equals("TRIAL")) return IssueKind.TRIAL;
        return owned.stream().anyMatch(p -> p.kind().equals("ABONNEMENT")) ? IssueKind.SUBSCRIPTION : IssueKind.PURCHASE;
    }

    static IssueKind parseKind(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return IssueKind.valueOf(s.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Sorte d'activation inconnue : " + AuditLog.clip(s, 20));
        }
    }

    public List<String> serverKinds() {
        List<String> out = new ArrayList<>();
        for (IssueKind k : IssueKind.values()) if (ScopedActivationSigner.SERVER_SCOPES.contains(k.required())) out.add(k.name());
        return out;
    }
}
