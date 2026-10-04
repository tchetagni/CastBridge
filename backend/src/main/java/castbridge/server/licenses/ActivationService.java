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
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues (and re-issues) activations with the SERVER key, in the wire format of docs/ACTIVATION-FORMAT.md ({@code cbx1}). Rules:
 * <ul>
 *   <li>the scope of the key is enforced before anything is written: no transfer, no "tout ouvert" (403);</li>
 *   <li>the device is identified by its REQUEST (code + k + factor fingerprints, never raw values); the same hardware (k of n factors) keeps its
 *       seat and consumes nothing, a new hardware takes a seat under the licence lock; none free = refusal (transfer needed);</li>
 *   <li>a re-issue while the previous activation can still be installed (the 48 h window of its creation) gives back THE SAME token (idempotence: the nonce and the dates come from the
 *       stored row, Ed25519 is deterministic); otherwise a fresh one with a new window;</li>
 *   <li>nothing is issued for a suspended, revoked or expired licence; a licence inside its grace period only re-issues;</li>
 *   <li>the activation text is returned once and never stored nor logged: only its SHA-256 fingerprint is kept; each issuance is also written to the
 *       registry as a signed `issue` event, so the offline tools see it.</li>
 * </ul>
 */
@Service
public class ActivationService {
    private static final long DAY = WireActivation.DAY_MS;

    private final JdbcTemplate jdbc;
    private final LicenseService licenses;
    private final ScopedActivationSigner signer;
    private final AuditLog audit;
    private final LicenseProperties props;
    private final RegistryStore registry;

    public ActivationService(JdbcTemplate jdbc, LicenseService licenses, ScopedActivationSigner signer, AuditLog audit, LicenseProperties props, RegistryStore registry) {
        this.jdbc = jdbc;
        this.licenses = licenses;
        this.signer = signer;
        this.audit = audit;
        this.props = props;
        this.registry = registry;
    }

    /**
     * @param deviceRequest the "demande d'appareil" text of the device (code=…, k=…, factor=…)
     * @param subject       tv (default) or phone
     * @param kind          optional; anything but production/trial (transfer, open_all…) is refused by the scope of the server key
     * @param productIds    optional; "*" = "tout ouvert", refused
     * @param windowHours   installation window in hours (1 to 48), default castbridge.licenses.window-hours (48)
     */
    public record IssueRequest(String licenseId, String subject, String deviceRequest, String kind, List<String> productIds, Integer windowHours, String usageDays) {
        public IssueRequest(String licenseId, String subject, String deviceRequest, String kind, List<String> productIds, Integer windowHours) {
            this(licenseId, subject, deviceRequest, kind, productIds, windowHours, null);
        }
    }

    /** Key duration bounds (docs/ACTIVATION-FORMAT.md § usage ceiling, docs/TRIAL-EDITION.md § 15): trial 1..365 days (default 30, never unlimited); production illimitée or 1..3660. */
    public static final int TRIAL_DEFAULT_DAYS = 30, TRIAL_MAX_DAYS = 365, PRODUCTION_MAX_DAYS = 3660;

    /** The parsed key duration: {@code days} null = illimitée (production only, no `usage` right). */
    public record KeyDuration(Integer days) {
        public boolean unlimited() { return days == null; }
    }

    /**
     * @param raw blank/null = default (trial 30 days, production illimitée); "illimitee"/"illimitée"/"unlimited" = illimitée (production only); else an integer number of days.
     */
    public static KeyDuration parseDuration(boolean trial, String raw) {
        String t = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (t.isEmpty()) return new KeyDuration(trial ? TRIAL_DEFAULT_DAYS : null);
        if (t.equals("illimitee") || t.equals("illimitée") || t.equals("unlimited")) {
            if (trial) throw ApiException.badRequest("Une clé d'essai n'est jamais illimitée : durée de 1 à " + TRIAL_MAX_DAYS + " jours");
            return new KeyDuration(null);
        }
        int d;
        try {
            d = Integer.parseInt(t);
        } catch (NumberFormatException e) {
            throw ApiException.badRequest("Durée de la clé : un nombre de jours" + (trial ? "" : " ou « illimitée »") + " est attendu");
        }
        int max = trial ? TRIAL_MAX_DAYS : PRODUCTION_MAX_DAYS;
        if (d < 1 || d > max) throw ApiException.badRequest((trial ? "Durée de la clé d'essai" : "Durée de la clé de production") + " : de 1 à " + max + " jours" + (trial ? " (jamais illimitée)" : " ou illimitée"));
        return new KeyDuration(d);
    }

    /** The `usage|duree|from|to` right line. */
    public static String usageLine(long fromMs, int days) { return "usage|duree|" + fromMs + "|" + (fromMs + days * DAY); }

    public record Activation(String text, String kid, String nonce, String fingerprint, String kind, String subject, Instant issuedAt, Instant notAfter, String licenseId,
                             String seatId, String deviceCode, boolean reused, boolean newSeat, String format, Integer usageDays, Instant usageEnd, String installKeyFingerprint) {
        public Activation(String text, String kid, String nonce, String fingerprint, String kind, String subject, Instant issuedAt, Instant notAfter, String licenseId,
                          String seatId, String deviceCode, boolean reused, boolean newSeat, String format, Integer usageDays, Instant usageEnd) {
            this(text, kid, nonce, fingerprint, kind, subject, issuedAt, notAfter, licenseId, seatId, deviceCode, reused, newSeat, format, usageDays, usageEnd, null);
        }

        /** Human summary of the key (edition, duration, end date): no secret. */
        public String properties() {
            return "édition " + (kind.equals("TRIAL") ? "essai" : "production") + " · durée " + (usageDays == null ? "illimitée" : usageDays + " jours") + (usageEnd == null ? "" : " · fin le " + usageEnd.toString().substring(0, 10));
        }
    }

    public String format() { return "cbx1 (docs/ACTIVATION-FORMAT.md)"; }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Activation issue(Actor actor, IssueRequest req, String channel) { return issue(actor, req, channel, false); }

    /** Re-issue for the seat of a device that already holds one (support role allowed): never consumes a seat. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Activation reissue(Actor actor, String licenseId, String subject, String deviceRequest, String channel) { return reissue(actor, licenseId, subject, deviceRequest, channel, null); }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Activation reissue(Actor actor, String licenseId, String subject, String deviceRequest, String channel, String usageDays) {
        return issue(actor, new IssueRequest(licenseId, subject, deviceRequest, null, null, null, usageDays), channel, true);
    }

    /** Re-issue from the hardware stored on the seat itself (nothing to paste): the page button and the activation file download. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Activation reissueSeat(Actor actor, String licenseId, String seatId, String channel) {
        actor.require(Role.Permission.REISSUE, props.requireTotp());
        String lic = Validate.licenseId(licenseId);
        String seat = Validate.seatId(seatId);
        List<LicenseService.SeatRow> rows = jdbc.query("SELECT s.* FROM lic_seat s JOIN lic_license l ON l.id = s.license_pk WHERE l.license_id = ? AND s.seat_id = ?", (rs, i) -> LicenseService.seat(rs), lic, seat);
        if (rows.isEmpty() || !rows.get(0).state().equals("ACTIVE")) throw ApiException.notFound("Aucun poste actif avec cet identifiant dans cette licence");
        LicenseService.SeatRow s = rows.get(0);
        if (s.anonymized() || s.factorsText() == null || s.factorsText().isBlank()) throw ApiException.conflict("Ce poste a été anonymisé : l'appareil doit refaire une demande");
        var device = new DeviceIdentity.Request(DeviceIdentity.parseStored(s.factorsText()), s.deviceCode(), s.k());
        return doIssue(actor, lic, s.subject(), device, null, null, null, null, channel, true, false);
    }

    private Activation issue(Actor actor, IssueRequest req, String channel, boolean reissueOnly) {
        actor.require(Role.Permission.REISSUE, props.requireTotp());
        boolean auto = !reissueOnly && isAuto(req.licenseId());
        String licenseId = auto ? null : Validate.licenseId(req.licenseId());
        String subject = req.subject() == null || req.subject().isBlank() ? "tv" : req.subject().trim().toLowerCase(Locale.ROOT);
        if (!subject.equals("tv") && !subject.equals("phone")) throw ApiException.badRequest("Type d'appareil : tv ou phone");
        var device = DeviceIdentity.parseRequest(req.deviceRequest());
        if (auto) {
            // a production key without a licence id: the licence is created first, in the same transaction (a refusal below rolls it back)
            actor.require(Role.Permission.ISSUE_NEW, props.requireTotp());
            KeyDuration autoDuration = parseDuration(false, req.usageDays());
            if (req.kind() != null && !req.kind().isBlank() && parseKind(req.kind()) != IssueKind.PRODUCTION) throw ApiException.badRequest("Une licence générée ne délivre que des activations de production");
            // start and end of the licence = usage right of the key (audit R-1); the exact issue date is pinned in doIssue
            Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
            licenseId = licenses.createAuto(actor, now, usageEnd(autoDuration, now)).licenseId();
        }
        return doIssue(actor, licenseId, subject, device, req.kind(), req.productIds(), req.windowHours(), req.usageDays(), channel, reissueOnly, auto);
    }

    /** Blank or « auto »: the server generates the licence of a production key. */
    public static boolean isAuto(String licenseId) {
        return licenseId == null || licenseId.isBlank() || licenseId.trim().equalsIgnoreCase("auto");
    }

    private Activation doIssue(Actor actor, String licenseId, String subject, DeviceIdentity.Request device, String askedKind, List<String> productIds, Integer windowHours, String usageDays,
                               String channel, boolean reissueOnly, boolean autoLicense) {
        IssueKind asked = parseKind(askedKind);
        // 1. scope first: a forbidden request writes nothing (not even a seat)
        if (asked != null && asked != IssueKind.TRIAL && asked != IssueKind.PRODUCTION || (productIds != null && productIds.contains("*"))) {
            signer.check(new ActivationRequest(asked == null ? IssueKind.OPEN_ALL : asked, subject, "x-check", null, device, productIds != null && productIds.contains("*") ? List.of("openall|tout|0|1") : List.of(),
                    0, 0, 1, "00000000"));
        }
        LicenseService.LicenseRow l = licenses.lock(licenseId);
        String eff = l.effectiveState();
        Instant nowI = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        LicenseService.SeatRow existing = licenses.findMatchingSeat(l, subject, device);
        switch (eff) {
            case "ACTIVE" -> { }
            case "GRACE" -> { if (existing == null) throw ApiException.conflict("Licence expirée (période de grâce) : seule la réémission d'un poste existant est possible"); }
            case "SUSPENDED" -> throw ApiException.conflict("Licence suspendue : aucune activation n'est émise");
            case "REVOKED" -> throw ApiException.conflict("Licence révoquée : aucune activation n'est émise");
            default -> throw ApiException.conflict("Licence expirée : prolongez-la avant d'émettre");
        }
        if (reissueOnly && existing == null) throw ApiException.notFound("Cet appareil n'a pas de poste actif dans cette licence : l'émission pour un nouvel appareil est réservée au propriétaire");
        if (existing == null) actor.require(Role.Permission.ISSUE_NEW, props.requireTotp());

        boolean trial = l.kind().equals("TRIAL");
        IssueKind kind = trial ? IssueKind.TRIAL : IssueKind.PRODUCTION;
        if (asked != null && asked != kind) throw ApiException.badRequest(trial ? "Une licence d'essai ne délivre que des clés d'essai" : "Une licence payante ne délivre que des activations de production");
        KeyDuration duration = parseDuration(trial, usageDays);
        List<String> baseRights = trial ? List.of() : rightsOf(l, nowI, productIds);
        // W23-05 audit HIGH-1 : une demande qui porte la clé de signature de la TV (`install_sig=`) la fait SIGNER dans l'activation (droit « ik ») ; sans elle (TV ancienne) le jeton ne lie aucune clé
        // la clé d'installation n'est signée QUE pour une TV (parité Kotlin et Python : un téléphone n'a pas de clé d'installation à lier ; second audit w23-05, LOW-E)
        String boundFingerprint = null;
        if (!trial && device.installSig() != null && subject.equals("tv")) {
            boundFingerprint = InstallKeyFingerprint.ofRaw(java.util.HexFormat.of().parseHex(device.installSig()));
            baseRights = new ArrayList<>(baseRights);
            baseRights.add(WireActivation.installKeyLine(java.util.HexFormat.of().parseHex(device.installSig())));
        }
        int window = windowHours == null ? props.windowHours() : Validate.range(windowHours, "Fenêtre d'installation (heures)", 1, WireActivation.MAX_WINDOW_HOURS);

        // the seat the activation is for (the id of a new seat is deterministic)
        String seatId = existing != null ? existing.seatId() : WireActivation.defaultSeat(l.wireId(), device.factors());
        String idem = Hashing.sha256Hex(String.join("|", "v2", l.wireId(), seatId, kind.name(), subject, String.join(";", baseRights), "d=" + (duration.days() == null ? "inf" : duration.days()), device.setHashHex(), Integer.toString(device.k()), Integer.toString(window)));

        // 2. idempotence: the same request, while the previous activation can still be installed, returns the same activation
        record Prior(Instant issuedAt, Instant notBefore, Instant notAfter, String nonce, String fingerprint) {}
        List<Prior> prior = jdbc.query("SELECT issued_at, not_before, not_after, nonce, token_fingerprint FROM lic_issuance WHERE license_pk = ? AND idem_key = ? ORDER BY id DESC LIMIT 1",
                (rs, i) -> new Prior(rs.getTimestamp("issued_at").toInstant(), rs.getTimestamp("not_before").toInstant(), rs.getTimestamp("not_after").toInstant(), rs.getString("nonce"),
                        rs.getString("token_fingerprint")), l.id(), idem);
        if (existing != null && !prior.isEmpty() && prior.get(0).notAfter().isAfter(nowI.plusMillis(WireActivation.HOUR_MS))) {
            Prior p = prior.get(0);
            int w = (int) ((p.notAfter().toEpochMilli() - p.notBefore().toEpochMilli()) / WireActivation.HOUR_MS);
            List<String> rights = withUsage(baseRights, duration, p.issuedAt().toEpochMilli());
            SignedActivation s = signer.sign(new ActivationRequest(kind, subject, l.wireId(), seatId, device, rights, p.issuedAt().toEpochMilli(), p.notBefore().toEpochMilli(), w, p.nonce()));
            if (!s.fingerprint().equals(p.fingerprint())) {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "La réémission ne correspond pas à l'activation d'origine (clé changée) : contactez le propriétaire");
            }
            licenses.allocateSeat(l, subject, device, nowI);
            audit.record(actor, "ACTIVATION_REISSUE", "LICENSE", licenseId, null, Map.of("seat", seatId, "fp", s.fingerprint().substring(0, 12), "channel", channel, "edition", editionOf(kind), "duration", durationText(duration), "end", endText(duration, p.issuedAt())));
            return new Activation(s.text(), s.kid(), s.nonce(), s.fingerprint(), kind.name(), subject, p.issuedAt(), p.notAfter(), licenseId, seatId, device.code(), true, false, format(), duration.days(), usageEnd(duration, p.issuedAt()), boundFingerprint);
        }

        // 3. new activation: seat first (under the lock), then signature; a failure rolls everything back
        LicenseService.SeatResult seat = licenses.allocateSeat(l, subject, device, nowI);
        seatId = seat.seat().seatId();
        // an activation is revoked when issuedAt <= the revocation date of its seat: a seat released earlier must get a LATER issue date
        Timestamp lastRev = jdbc.queryForObject("SELECT MAX(revoked_at) FROM lic_revocation WHERE license_id = ? AND seat_id = ?", Timestamp.class, l.wireId(), seatId);
        Instant issuedAt = lastRev != null && !lastRev.toInstant().isBefore(nowI) ? lastRev.toInstant().plusSeconds(1) : nowI;
        // the sequence number of the key is the issue date (docs/ACTIVATION-FORMAT.md § 3.2) and a device refuses an activation OLDER than the last one it saw for this key:
        // an issue date is never earlier than the previous one of this key (only when a release bumped the previous one into the future)
        Timestamp lastIssued = jdbc.queryForObject("SELECT MAX(issued_at) FROM lic_issuance WHERE kid = ?", Timestamp.class, signer.kid());
        if (lastIssued != null && lastIssued.toInstant().isAfter(issuedAt)) issuedAt = lastIssued.toInstant();
        String nonce = HexOf(Hashing.sha256(("nonce|" + idem + "|" + issuedAt.toEpochMilli()).getBytes(StandardCharsets.UTF_8)), 16);
        List<String> rights = withUsage(baseRights, duration, issuedAt.toEpochMilli());
        // a licence generated for this very key follows the usage right actually signed (start = issue date, end = start + duration, none if the key is unlimited)
        if (autoLicense) licenses.alignToKey(l.id(), issuedAt, usageEnd(duration, issuedAt));
        SignedActivation s = signer.sign(new ActivationRequest(kind, subject, l.wireId(), seatId, device, rights, issuedAt.toEpochMilli(), issuedAt.toEpochMilli(), window, nonce));
        jdbc.update("INSERT INTO lic_issuance (license_pk, seat_pk, seat_id, device_code, kind, subject, kid, nonce, issued_at, not_before, not_after, issuer, channel, token_fingerprint, source, idem_key)"
                        + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,'SERVER',?)", l.id(), seat.seat().id(), seatId, device.code(), kind.name(), subject, s.kid(), s.nonce(), Timestamp.from(issuedAt),
                Timestamp.from(Instant.ofEpochMilli(s.notBefore())), Timestamp.from(Instant.ofEpochMilli(s.notAfter())), actor.name(), channel, s.fingerprint(), idem);
        if (!trial) registry.ensureLicense(l.licenseId(), l.seatsAllowed(), l.transferCap(), l.createdAt());
        registry.emitIssue(s, subject, kind.name().toLowerCase(Locale.ROOT), l.wireId(), device.k(), device.factors(), issuedAt.toEpochMilli());
        audit.record(actor, "ACTIVATION_ISSUE", "LICENSE", licenseId, null,
                Map.of("seat", seatId, "kind", kind.name(), "outcome", seat.outcome().name(), "kid", s.kid(), "fp", s.fingerprint().substring(0, 12), "channel", channel,
                        "edition", editionOf(kind), "duration", durationText(duration), "end", endText(duration, issuedAt), "licenseAuto", autoLicense));
        return new Activation(s.text(), s.kid(), s.nonce(), s.fingerprint(), kind.name(), subject, issuedAt, Instant.ofEpochMilli(s.notAfter()), licenseId, seatId, device.code(), false,
                seat.outcome() != LicenseService.SeatOutcome.REUSED, format(), duration.days(), usageEnd(duration, issuedAt), boundFingerprint);
    }

    /** The rights (none for a licence without bundle: the full version) plus the usage ceiling line (none for « illimitée »). The server never adds `super` nor the trial rental window (owner tools only). */
    private static List<String> withUsage(List<String> base, KeyDuration d, long issuedAtMs) {
        if (d.unlimited()) return base;
        List<String> out = new ArrayList<>(base);
        out.add(usageLine(issuedAtMs, d.days()));
        return out;
    }

    private static String editionOf(IssueKind k) { return k == IssueKind.TRIAL ? "essai" : "production"; }
    private static String durationText(KeyDuration d) { return d.unlimited() ? "illimitée" : d.days() + " jours"; }
    private static Instant usageEnd(KeyDuration d, Instant issuedAt) { return d.unlimited() ? null : Instant.ofEpochMilli(issuedAt.toEpochMilli() + d.days() * DAY); }
    private static String endText(KeyDuration d, Instant issuedAt) { return d.unlimited() ? "aucune" : usageEnd(d, issuedAt).toString().substring(0, 10); }

    private static String HexOf(byte[] b, int n) { return java.util.HexFormat.of().formatHex(b, 0, n); }

    /**
     * The right lines of the licence's bouquets (§ 3.3): à la carte = {@code purchase|produit|bouquets|grantedAt} (definitive), abonnement =
     * {@code subscription|produit|bouquets|début|fin|tolérance|0}. A subscription ends at the end of its bouquet, else at the end of the licence;
     * an already ended one is left out. "Tout ouvert" is never built here (that is the owner's own tool).
     */
    private List<String> rightsOf(LicenseService.LicenseRow l, Instant now, List<String> only) {
        List<String> out = new ArrayList<>();
        long grace = Math.min(l.graceDays(), 30) * DAY;
        for (LicenseService.ProductRef p : licenses.productsOf(l.id())) {
            if (only != null && !only.isEmpty() && !only.contains(p.productId())) continue;
            List<String> bundles = new ArrayList<>(licenses.bundlesOf(p.id(), p.productId()));
            Collections.sort(bundles);
            String b = String.join(",", bundles);
            if (p.kind().equals("A_LA_CARTE")) {
                out.add("purchase|" + p.productId() + "|" + b + "|" + p.addedAt().toEpochMilli());
            } else {
                Instant end = p.endsAt() != null ? p.endsAt() : l.endAt();
                if (end == null) throw ApiException.conflict("L'abonnement « " + p.productId() + " » n'a pas de date de fin : fixez la fin de la licence ou du bouquet avant d'émettre");
                if (!end.isAfter(now)) continue;
                Instant start = p.addedAt().isAfter(end) ? end.minusSeconds(1) : p.addedAt();
                out.add("subscription|" + p.productId() + "|" + b + "|" + start.toEpochMilli() + "|" + end.toEpochMilli() + "|" + grace + "|0");
            }
        }
        if (only != null) for (String o : only) if (licenses.productsOf(l.id()).stream().noneMatch(p -> p.productId().equals(o))) throw ApiException.badRequest("Bouquet absent de cette licence : " + AuditLog.clip(o, 48));
        return out;
    }

    static IssueKind parseKind(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return IssueKind.valueOf(s.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
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
