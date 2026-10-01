package castbridge.server.licenses;

import castbridge.server.licenses.ActivationSigner.SignerScope;
import castbridge.server.licenses.DeviceIdentity.Factor;
import castbridge.server.web.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

/**
 * The licence registry (docs/ACTIVATION-FORMAT.md § 8-9): the set of SIGNED events by which the offline tools (desktop, owner phone) and the
 * server synchronize who issued what, which seats are used and which transfers were signed. The server NEVER signs a transfer: it only records
 * the ones that arrive here (scope TRANSFER, desktop or owner phone), counting them against the yearly cap of the licence.
 *
 * <p>File = {"format":"castbridge-licence-registry-v1","events":[{"id","kid","text","signature"}…]}. Importing is a UNION by event id (idempotent: a
 * re-imported file changes nothing). Every event is verified (known key, not revoked, signature, scope) and applied with the rules of the format;
 * what the format applies silently, the server REVIEW policy turns into a conflict that waits for the owner's decision: UNKNOWN_LICENSE, OVER_QUOTA
 * (more devices than seats), TWO_TOOLS (the same hardware under two seat ids), TRANSFER_CAP. Policy AUTO applies the format as is (duplicate hardware
 * merged into the older seat, quota raised with a warning, over-cap transfer and unknown licence rejected).
 */
@Service
public class LedgerService {
    public static final String FORMAT = "castbridge-licence-registry-v1";
    private static final long YEAR_MS = 365L * 24 * 3600 * 1000;

    private final JdbcTemplate jdbc;
    private final LicenseService licenses;
    private final RegistryStore registry;
    private final TrustedKeys trusted;
    private final AuditLog audit;
    private final LicenseProperties props;
    private final ObjectMapper json;

    public LedgerService(JdbcTemplate jdbc, LicenseService licenses, RegistryStore registry, TrustedKeys trusted, AuditLog audit, LicenseProperties props, ObjectMapper json) {
        this.jdbc = jdbc;
        this.licenses = licenses;
        this.registry = registry;
        this.trusted = trusted;
        this.audit = audit;
        this.props = props;
        this.json = json;
    }

    // ------------------------------------------------------------------ report types

    /** @param rejections events refused by the format (unknown or revoked key, bad signature, scope missing, malformed): never stored, never applied */
    public record ImportReport(Long importId, boolean dryRun, String policy, int entries, int applied, int duplicates, int ignored, List<Rejection> rejections,
                               List<ConflictRow> conflicts, List<String> warnings) {}

    public record Rejection(String eventId, String reason) {}

    public record ConflictRow(long id, long importId, String type, String licenseId, String deviceCode, String detail, String status, String decidedBy, Instant decidedAt, String reason, String eventId) {}

    private enum Kind { APPLIED, NOOP, CONFLICT, REJECTED }

    private record Result(Kind kind, String type, String detail, String deviceCode) {
        static Result applied() { return new Result(Kind.APPLIED, null, null, null); }

        static Result noop() { return new Result(Kind.NOOP, null, null, null); }

        static Result conflict(String type, String detail, String device) { return new Result(Kind.CONFLICT, type, detail, device); }

        static Result rejected(String reason) { return new Result(Kind.REJECTED, reason, null, null); }
    }

    // ------------------------------------------------------------------ export

    /** The registry file: every event of the server (own and imported), canonical order, erased ones left out. The events are signed by their authors. */
    @Transactional
    public byte[] export(Actor actor) {
        actor.require(Role.Permission.LEDGER_EXPORT, props.requireTotp());
        ObjectNode root = json.createObjectNode();
        root.put("format", FORMAT);
        ArrayNode events = root.putArray("events");
        for (RegistryStore.Stored e : registry.all(50_000)) {
            ObjectNode n = events.addObject();
            n.put("id", e.id());
            n.put("kid", e.kid());
            n.put("text", e.text());
            n.put("signature", e.signature());
        }
        audit.record(actor, "LEDGER_EXPORT", "LEDGER", "server", null, Map.of("events", events.size()));
        try {
            return json.writeValueAsBytes(root);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    // ------------------------------------------------------------------ import

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ImportReport importLedger(Actor actor, byte[] file, boolean dryRun, boolean auto) {
        actor.require(Role.Permission.LEDGER_IMPORT, props.requireTotp());
        if (file == null || file.length == 0) throw ApiException.badRequest("Fichier de registre vide");
        if (file.length > props.maxImportBytes()) throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "Registre trop volumineux (" + props.maxImportBytes() / 1_000_000 + " Mo au maximum)");
        JsonNode root;
        try {
            root = json.readTree(file);
        } catch (java.io.IOException e) {
            throw ApiException.badRequest("Le fichier n'est pas un registre JSON valide");
        }
        if (root == null || !FORMAT.equals(root.path("format").asText())) throw ApiException.badRequest("Format de registre inconnu (attendu : " + FORMAT + ")");
        JsonNode list = root.path("events");
        if (!list.isArray()) throw ApiException.badRequest("Registre sans liste d'événements");
        if (list.size() > props.maxImportEntries()) throw ApiException.badRequest("Trop d'événements (" + props.maxImportEntries() + " au maximum par fichier)");

        // entries whose id does not match their text are skipped, never trusted (signatures are checked below)
        List<RegistryEvent> events = new ArrayList<>();
        int ignored = 0;
        Set<String> inFile = new HashSet<>();
        int duplicates = 0;
        for (JsonNode n : list) {
            RegistryEvent e = RegistryEvent.fromJson(n);
            if (e == null) ignored++;
            else if (!inFile.add(e.id())) duplicates++;
            else events.add(e);
        }
        events.sort(Comparator.comparingLong(RegistryEvent::at).thenComparing(RegistryEvent::id));

        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        String sha = Hashing.sha256Hex(file);
        int total = list.size();
        jdbc.update(con -> {
            var ps = con.prepareStatement("INSERT INTO lic_ledger_import (sha256, imported_at, imported_by, entries, applied, duplicates, rejected, conflicts) VALUES (?,?,?,?,0,0,0,0)", Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, sha);
            ps.setTimestamp(2, LicenseService.ts(Instant.now()));
            ps.setString(3, AuditLog.clip(actor.name(), 64));
            ps.setInt(4, total);
            return ps;
        }, keys);
        long importId = keys.getKey().longValue();

        int applied = 0;
        List<Rejection> rejections = new ArrayList<>();
        List<ConflictRow> conflicts = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (RegistryEvent e : events) {
            if (registry.has(e.id())) { duplicates++; continue; }
            String why = verify(e);
            if (why != null) { rejections.add(new Rejection(e.id(), why)); continue; }
            String malformed = validateFields(e);
            if (malformed != null) { rejections.add(new Rejection(e.id(), "MALFORMED")); continue; }
            registry.put(e, "IMPORT", importId, true);
            Result r = apply(e, importId, false, auto, warnings);
            switch (r.kind()) {
                case APPLIED -> applied++;
                case NOOP -> duplicates++;
                case REJECTED -> {
                    jdbc.update("UPDATE lic_event SET applied = FALSE WHERE id = ?", e.id());
                    rejections.add(new Rejection(e.id(), r.type()));
                }
                case CONFLICT -> {
                    jdbc.update("UPDATE lic_event SET applied = FALSE WHERE id = ?", e.id());
                    conflicts.add(newConflict(importId, e, r));
                }
            }
        }
        jdbc.update("UPDATE lic_ledger_import SET applied = ?, duplicates = ?, rejected = ?, conflicts = ? WHERE id = ?", applied, duplicates, rejections.size(), conflicts.size(), importId);
        audit.record(actor, dryRun ? "LEDGER_DRYRUN" : "LEDGER_IMPORT", "LEDGER", sha.substring(0, 16), null,
                Map.of("policy", auto ? "auto" : "review", "entries", total, "applied", applied, "duplicates", duplicates, "rejected", rejections.size(), "conflicts", conflicts.size()));
        if (dryRun) TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        return new ImportReport(dryRun ? null : importId, dryRun, auto ? "auto" : "review", total, applied, duplicates, ignored, rejections, conflicts, warnings);
    }

    /** Checks key, revocation, signature and scope (the order of docs/ACTIVATION-FORMAT.md § 8.2); returns the reason of a refusal, or null. */
    private String verify(RegistryEvent e) {
        TrustedKeys.Key key = trusted.find(e.kid());
        if (key == null) return "UNKNOWN_KEY";
        Integer revoked = jdbc.queryForObject("SELECT COUNT(*) FROM lic_revocation WHERE kid = ?", Integer.class, e.kid());
        if (revoked != null && revoked > 0) return "REVOKED_KEY";
        try {
            if (!LicenseKeyring.verify(key.publicKey(), e.text().getBytes(java.nio.charset.StandardCharsets.UTF_8), e.signatureBytes())) return "BAD_SIGNATURE";
        } catch (IllegalArgumentException ex) {
            return "BAD_SIGNATURE";
        }
        Map<String, String> f = e.fields();
        SignerScope need = switch (f.getOrDefault("type", "")) {
            case "license", "issue" -> "trial".equals(f.get("kind")) ? SignerScope.ISSUE_TRIAL : SignerScope.ISSUE_PRODUCTION;
            case "transfer" -> SignerScope.TRANSFER;
            case "revoke" -> SignerScope.REVOKE;
            default -> null;
        };
        if (need == null) return "MALFORMED";
        return key.allows(need) ? null : "KEY_NOT_ALLOWED";
    }

    /** Strict validation of the fields an event needs, so that nothing malformed reaches the database. null = fine. */
    private String validateFields(RegistryEvent e) {
        try {
            Map<String, String> f = e.fields();
            if (!e.kid().equals(f.get("kid"))) return "kid";
            switch (e.type()) {
                case "license" -> {
                    if (!Validate.licenseId(f.get("license")).equals(f.get("license"))) return "license";
                    Validate.range(Integer.parseInt(f.get("seats")), "seats", 1, 1000);
                    Validate.range(Integer.parseInt(f.getOrDefault("maxTransfersPerYear", "2")), "cap", 0, 100);
                }
                case "issue" -> {
                    if (!f.get("license").equals(WireActivation.TRIAL_LICENSE) && !Validate.licenseId(f.get("license")).equals(f.get("license"))) return "ids";
                    if (!Validate.SEAT_ID.matcher(f.get("seat")).matches()) return "ids";
                    if (!f.get("subject").equals("tv") && !f.get("subject").equals("phone")) return "subject";
                    if (!f.get("kind").equals("trial") && !f.get("kind").equals("production")) return "kind";
                    if (!WireActivation.HEX.matcher(f.get("nonce")).matches()) return "nonce";
                    Long.parseLong(f.get("notAfter"));
                    Validate.range(Integer.parseInt(f.get("k")), "k", 1, 5);
                    if (e.factors().isEmpty() || !e.factors().values().stream().allMatch(h -> h.matches("[0-9a-f]{32}"))) return "factors";
                }
                case "transfer" -> {
                    if (!Validate.licenseId(f.get("license")).equals(f.get("license")) || !Validate.SEAT_ID.matcher(f.get("seat")).matches()) return "ids";
                    Validate.range(Integer.parseInt(f.get("k")), "k", 1, 5);
                    if (e.factors().isEmpty() || !e.factors().values().stream().allMatch(h -> h.matches("[0-9a-f]{32}"))) return "factors";
                }
                case "revoke" -> {
                    String t = f.get("target"), v = f.get("value");
                    if (t.equals("key") ? !v.matches("[0-9a-f]{16}") : !(t.equals("seat") && v.matches("[a-z0-9][a-z0-9-]{0,63}\\|[0-9a-f]{16}"))) return "revoke";
                }
                default -> {
                    return "type";
                }
            }
            return e.at() > 0 ? null : "at";
        } catch (RuntimeException ex) {
            return "fields";
        }
    }

    private ConflictRow newConflict(long importId, RegistryEvent e, Result r) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        String license = e.fields().get("license");
        jdbc.update(con -> {
            var ps = con.prepareStatement("INSERT INTO lic_conflict (import_id, type, license_id, device_code, detail, event_id, status) VALUES (?,?,?,?,?,?,'OPEN')", Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, importId);
            ps.setString(2, r.type());
            ps.setString(3, license);
            ps.setString(4, r.deviceCode());
            ps.setString(5, AuditLog.clip(r.detail(), 500));
            ps.setString(6, e.id());
            return ps;
        }, keys);
        return conflict(keys.getKey().longValue());
    }

    // ------------------------------------------------------------------ applying one event

    private Result apply(RegistryEvent e, long importId, boolean force, boolean auto, List<String> warnings) {
        return switch (e.type()) {
            case "license" -> applyLicense(e);
            case "issue" -> applyIssue(e, force, auto, warnings);
            case "transfer" -> applyTransfer(e, importId, force, auto);
            case "revoke" -> applyRevoke(e);
            default -> Result.noop();
        };
    }

    private Result applyLicense(RegistryEvent e) {
        Map<String, String> f = e.fields();
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM lic_license WHERE license_id = ?", Integer.class, f.get("license"));
        if (n != null && n > 0) return Result.noop(); // the first event of a licence wins
        long client = placeholderClient();
        Instant now = Instant.now();
        jdbc.update("INSERT INTO lic_license (license_id, client_id, kind, state, seats_allowed, start_at, end_at, grace_days, transfer_cap, created_by, created_at, updated_at)"
                        + " VALUES (?,?,'PAID','ACTIVE',?,?,NULL,?,?,?,?,?)", f.get("license"), client, Integer.parseInt(f.get("seats")), LicenseService.ts(Instant.ofEpochMilli(e.at())), props.defaultGraceDays(),
                Integer.parseInt(f.getOrDefault("maxTransfersPerYear", Integer.toString(props.defaultTransferCap()))), "import:" + trusted.nameOf(e.kid()), LicenseService.ts(now), LicenseService.ts(now));
        return Result.applied();
    }

    private long placeholderClient() {
        List<Long> ids = jdbc.queryForList("SELECT id FROM lic_client WHERE name = 'Client importé (à renseigner)' AND erased_at IS NULL ORDER BY id LIMIT 1", Long.class);
        if (!ids.isEmpty()) return ids.get(0);
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            var ps = con.prepareStatement("INSERT INTO lic_client (name, created_at, updated_at) VALUES ('Client importé (à renseigner)', ?, ?)", Statement.RETURN_GENERATED_KEYS);
            ps.setTimestamp(1, LicenseService.ts(Instant.now()));
            ps.setTimestamp(2, LicenseService.ts(Instant.now()));
            return ps;
        }, keys);
        return keys.getKey().longValue();
    }

    /** Seat row of this licence for a seat id, following the alias of a merged duplicate. */
    private LicenseService.SeatRow seatOf(long licensePk, String seatId) {
        List<LicenseService.SeatRow> r = jdbc.query("SELECT s.* FROM lic_seat s WHERE s.license_pk = ? AND s.seat_id = ? UNION ALL"
                + " SELECT s.* FROM lic_seat s JOIN lic_seat_alias a ON a.seat_pk = s.id WHERE a.license_pk = ? AND a.alias_seat_id = ?", (rs, i) -> LicenseService.seat(rs), licensePk, seatId, licensePk, seatId);
        return r.isEmpty() ? null : r.get(0);
    }

    private Result applyIssue(RegistryEvent e, boolean force, boolean auto, List<String> warnings) {
        Map<String, String> f = e.fields();
        String lic = f.get("license"), seatId = f.get("seat"), subject = f.get("subject");
        if (lic.equals(WireActivation.TRIAL_LICENSE)) return Result.applied(); // trial keys are logged by the tools, never counted as seats
        LicenseService.LicenseRow l;
        try {
            l = licenses.lock(lic);
        } catch (ApiException ex) {
            return auto ? Result.rejected("UNKNOWN_LICENSE") : Result.conflict("UNKNOWN_LICENSE", "Licence inconnue du serveur : " + lic, null);
        }
        Instant at = Instant.ofEpochMilli(e.at());
        int k = Integer.parseInt(f.get("k"));
        var factors = e.factors();
        String deviceCode = DeviceIdentity.code(factors);
        LicenseService.SeatRow seat = seatOf(l.id(), seatId);
        if (seat != null && seat.state().equals("ACTIVE")) {
            jdbc.update("UPDATE lic_seat SET last_seen = ? WHERE id = ? AND last_seen < ?", LicenseService.ts(at), seat.id(), LicenseService.ts(at));
            recordIssuance(l, seat.id(), seatId, deviceCode, e, f, at);
            return Result.applied();
        }
        if (seat == null && !force) {
            // same hardware under another seat id (two tools issued it): the format keeps the older seat and counts it once
            var device = new DeviceIdentity.Request(factors, deviceCode, k);
            LicenseService.SeatRow dup = licenses.findMatchingSeat(l, subject, device);
            if (dup != null) {
                if (!auto) return Result.conflict("TWO_TOOLS", "Le même matériel a reçu deux identifiants de poste (« " + dup.seatId() + " » et « " + seatId + " ») de deux outils : à fusionner (le plus ancien est gardé)", deviceCode);
                alias(l.id(), seatId, dup.id());
                recordIssuance(l, dup.id(), dup.seatId(), deviceCode, e, f, at);
                return Result.applied();
            }
        } else if (seat == null) {
            var device = new DeviceIdentity.Request(factors, deviceCode, k);
            LicenseService.SeatRow dup = licenses.findMatchingSeat(l, subject, device);
            if (dup != null) {
                alias(l.id(), seatId, dup.id());
                recordIssuance(l, dup.id(), dup.seatId(), deviceCode, e, f, at);
                return Result.applied();
            }
        }
        // a new seat (or a released one coming back): needs a free slot
        int active = licenses.activeSeats(l.id());
        if (!l.state().equals("REVOKED") && active >= l.seatsAllowed()) {
            if (!(force || auto)) return Result.conflict("OVER_QUOTA", "Dépassement de postes : " + active + "/" + l.seatsAllowed() + " utilisés, et le poste « " + seatId + " » en demande un de plus", deviceCode);
            jdbc.update("UPDATE lic_license SET seats_allowed = ?, version = version + 1 WHERE id = ?", active + 1, l.id());
            warnings.add("licence " + lic + " : plus de postes que prévu (" + l.seatsAllowed() + ") : quota relevé à " + (active + 1) + " pour le poste " + seatId);
            l = licenses.lock(lic);
        }
        Long seatPk = null;
        if (!l.state().equals("REVOKED")) seatPk = insertOrReviveSeat(l, seat, seatId, subject, deviceCode, factors, k, at);
        recordIssuance(l, seatPk, seatId, deviceCode, e, f, at);
        return Result.applied();
    }

    private Long insertOrReviveSeat(LicenseService.LicenseRow l, LicenseService.SeatRow existing, String seatId, String subject, String code, Map<Factor, String> factors, int k, Instant at) {
        var dev = new DeviceIdentity.Request(factors, code, k);
        if (existing != null) {
            // released seat coming back
            LicenseService.SeatResult r = licenses.allocateSeat(l, subject, dev, at);
            return r.seat().id();
        }
        // a seat with the id given by the issuing tool (not the default one: the tool may have chosen it)
        int slot = firstFreeSlot(l);
        jdbc.update("INSERT INTO lic_seat (license_pk, seat_id, subject, device_code, factors, k, slot_no, state, first_seen, last_seen) VALUES (?,?,?,?,?,?,?,'ACTIVE',?,?)",
                l.id(), seatId, subject, code, dev.factorsText(), k, slot, LicenseService.ts(at), LicenseService.ts(at));
        return jdbc.queryForObject("SELECT id FROM lic_seat WHERE license_pk = ? AND seat_id = ?", Long.class, l.id(), seatId);
    }

    private int firstFreeSlot(LicenseService.LicenseRow l) {
        java.util.BitSet used = new java.util.BitSet();
        jdbc.query("SELECT slot_no FROM lic_seat WHERE license_pk = ? AND state = 'ACTIVE'", rs -> { used.set(rs.getInt(1)); }, l.id());
        return used.nextClearBit(1);
    }

    private void alias(long licensePk, String aliasSeatId, long seatPk) {
        try {
            jdbc.update("INSERT INTO lic_seat_alias (license_pk, alias_seat_id, seat_pk) VALUES (?,?,?)", licensePk, aliasSeatId, seatPk);
        } catch (DuplicateKeyException ex) {
            // already merged
        }
    }

    private void recordIssuance(LicenseService.LicenseRow l, Long seatPk, String seatId, String deviceCode, RegistryEvent e, Map<String, String> f, Instant at) {
        try {
            jdbc.update("INSERT INTO lic_issuance (license_pk, seat_pk, seat_id, device_code, kind, subject, kid, nonce, issued_at, not_before, not_after, issuer, channel, token_fingerprint, source)"
                            + " VALUES (?,?,?,?,?,?,?,?,?,NULL,?,?,?,?,'IMPORT')", l.id(), seatPk, seatId, deviceCode, f.get("kind").toUpperCase(java.util.Locale.ROOT), f.get("subject"), e.kid(), f.get("nonce"),
                    LicenseService.ts(at), LicenseService.ts(Instant.ofEpochMilli(Long.parseLong(f.get("notAfter")))), AuditLog.clip(trusted.nameOf(e.kid()), 64),
                    "ledger-" + AuditLog.clip(trusted.nameOf(e.kid()), 12), Hashing.sha256Hex(e.text()));
        } catch (DuplicateKeyException ex) {
            // same nonce already recorded
        }
    }

    private Result applyTransfer(RegistryEvent e, long importId, boolean force, boolean auto) {
        Map<String, String> f = e.fields();
        String lic = f.get("license"), seatId = f.get("seat");
        LicenseService.LicenseRow l;
        try {
            l = licenses.lock(lic);
        } catch (ApiException ex) {
            return auto ? Result.rejected("UNKNOWN_LICENSE") : Result.conflict("UNKNOWN_LICENSE", "Licence inconnue du serveur : " + lic, null);
        }
        LicenseService.SeatRow seat = seatOf(l.id(), seatId);
        if (seat == null) return auto ? Result.rejected("UNKNOWN_LICENSE") : Result.conflict("UNKNOWN_LICENSE", "Poste inconnu dans la licence " + lic + " : « " + seatId + " »", null);
        long at = e.at();
        if (!force) {
            int count = jdbc.queryForObject("SELECT COUNT(*) FROM lic_transfer WHERE license_pk = ? AND accepted = TRUE AND at > ? AND at <= ?", Integer.class, l.id(),
                    LicenseService.ts(Instant.ofEpochMilli(at - YEAR_MS)), LicenseService.ts(Instant.ofEpochMilli(at)));
            if (count >= l.transferCap()) {
                return auto ? Result.rejected("TRANSFER_LIMIT")
                        : Result.conflict("TRANSFER_CAP", "Plafond de transferts dépassé : " + count + " sur les 12 derniers mois pour un plafond de " + l.transferCap(), seat.deviceCode());
            }
        }
        var factors = e.factors();
        int k = Integer.parseInt(f.get("k"));
        String newCode = DeviceIdentity.code(factors);
        String oldCode = seat.deviceCode();
        jdbc.update("UPDATE lic_seat SET factors = ?, k = ?, device_code = ?, anonymized = FALSE WHERE id = ?", new DeviceIdentity.Request(factors, newCode, k).factorsText(), k, newCode, seat.id());
        jdbc.update("INSERT INTO lic_transfer (license_pk, seat_id, from_device_code, to_device_code, signed_by, at, accepted, ledger_import_id) VALUES (?,?,?,?,?,?,TRUE,?)",
                l.id(), seat.seatId(), oldCode, newCode, e.kid(), LicenseService.ts(Instant.ofEpochMilli(at)), importId);
        // the old activation of this seat (issued before) is revoked; the new hardware gets a new activation issued after this date
        jdbc.update("INSERT INTO lic_revocation (license_id, seat_id, reason, revoked_by, revoked_at) VALUES (?,?,?,?,?)", l.wireId(), seat.seatId(), "transfert", AuditLog.clip(trusted.nameOf(e.kid()), 64),
                LicenseService.ts(Instant.ofEpochMilli(at)));
        return Result.applied();
    }

    private Result applyRevoke(RegistryEvent e) {
        Map<String, String> f = e.fields();
        Instant at = Instant.ofEpochMilli(e.at());
        String by = AuditLog.clip(trusted.nameOf(e.kid()), 64);
        if (f.get("target").equals("key")) {
            Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM lic_revocation WHERE kid = ?", Integer.class, f.get("value"));
            if (n != null && n > 0) return Result.noop();
            jdbc.update("INSERT INTO lic_revocation (kid, reason, revoked_by, revoked_at) VALUES (?,?,?,?)", f.get("value"), "registre", by, LicenseService.ts(at));
        } else {
            String[] v = f.get("value").split("\\|");
            jdbc.update("INSERT INTO lic_revocation (license_id, seat_id, reason, revoked_by, revoked_at) VALUES (?,?,?,?,?)", v[0], v[1], "registre", by, LicenseService.ts(at));
        }
        return Result.applied();
    }

    // ------------------------------------------------------------------ conflicts

    public Page<ConflictRow> conflicts(String status, int page, int size) {
        String where = status == null || status.isBlank() ? "" : " WHERE status = ?";
        List<Object> args = new ArrayList<>();
        if (!where.isEmpty()) args.add(status.trim().toUpperCase(java.util.Locale.ROOT));
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM lic_conflict" + where, Long.class, args.toArray());
        args.add(size);
        args.add((long) page * size);
        return new Page<>(jdbc.query("SELECT * FROM lic_conflict" + where + " ORDER BY id DESC LIMIT ? OFFSET ?", (rs, i) -> mapConflict(rs), args.toArray()), page, size, total);
    }

    public ConflictRow conflict(long id) {
        List<ConflictRow> r = jdbc.query("SELECT * FROM lic_conflict WHERE id = ?", (rs, i) -> mapConflict(rs), id);
        if (r.isEmpty()) throw ApiException.notFound("Conflit introuvable");
        return r.get(0);
    }

    public List<Map<String, Object>> imports(int limit) {
        return jdbc.queryForList("SELECT id, imported_at, imported_by, entries, applied, duplicates, rejected, conflicts FROM lic_ledger_import ORDER BY id DESC LIMIT ?", limit);
    }

    private ConflictRow mapConflict(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ConflictRow(rs.getLong("id"), rs.getLong("import_id"), rs.getString("type"), rs.getString("license_id"), rs.getString("device_code"), rs.getString("detail"),
                rs.getString("status"), rs.getString("decided_by"), LicenseService.inst(rs, "decided_at"), rs.getString("reason"), rs.getString("event_id"));
    }

    /** Manual decision: accept = apply the event anyway (raising the quota, merging the duplicate hardware, lifting the cap), reject = keep it out. Reason mandatory. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ConflictRow decide(Actor actor, long id, boolean accept, String reason) {
        actor.require(Role.Permission.LEDGER_IMPORT, props.requireTotp());
        String why = Validate.reason(reason);
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM lic_conflict WHERE id = ? FOR UPDATE", id);
        if (rows.isEmpty()) throw ApiException.notFound("Conflit introuvable");
        Map<String, Object> c = rows.get(0);
        if (!"OPEN".equals(c.get("status"))) throw ApiException.conflict("Ce conflit a déjà été décidé");
        RegistryStore.Stored stored = registry.get((String) c.get("event_id"));
        if (stored == null || stored.text() == null) throw ApiException.conflict("L'événement de ce conflit n'est plus disponible");
        RegistryEvent e = new RegistryEvent(stored.kid(), stored.text(), stored.signature());
        long importId = ((Number) c.get("import_id")).longValue();
        if (accept) {
            Result r = apply(e, importId, true, false, new ArrayList<>());
            if (r.kind() == Kind.CONFLICT) throw ApiException.conflict(r.detail() + " : créez d'abord la licence, puis acceptez de nouveau");
            if (r.kind() == Kind.REJECTED) throw ApiException.conflict("Événement refusé : " + r.type());
            registry.markApplied(e.id());
        } else if (e.type().equals("transfer")) {
            LicenseService.LicenseRow l = licenses.get(e.fields().get("license"));
            jdbc.update("INSERT INTO lic_transfer (license_pk, seat_id, from_device_code, to_device_code, signed_by, at, accepted, ledger_import_id) VALUES (?,?,?,?,?,?,FALSE,?)",
                    l.id(), e.fields().get("seat"), "?", DeviceIdentity.code(e.factors()), e.kid(), LicenseService.ts(Instant.ofEpochMilli(e.at())), importId);
        }
        jdbc.update("UPDATE lic_conflict SET status = ?, decided_by = ?, decided_at = ?, reason = ? WHERE id = ?", accept ? "ACCEPTED" : "REJECTED", AuditLog.clip(actor.name(), 64),
                LicenseService.ts(Instant.now()), why, id);
        audit.record(actor, accept ? "CONFLICT_ACCEPT" : "CONFLICT_REJECT", "LEDGER", Long.toString(id), why, Map.of("type", String.valueOf(c.get("type"))));
        return conflict(id);
    }
}
