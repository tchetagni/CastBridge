package castbridge.server.licenses;

import castbridge.server.licenses.ActivationSigner.IssueKind;
import castbridge.server.web.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

/**
 * The licence registry: a SIGNED JSON file by which the offline tools (desktop, owner phone) and the server synchronize who
 * issued what, which seats are used and which transfers were signed. The server NEVER signs a transfer: it only records the
 * ones that arrive here, counting them against the yearly cap of the licence.
 *
 * <p>File = {"v":1,"kid":"…","tool":"desktop|phone|server","payload":"base64(JSON)","sig":"base64(Ed25519 of the payload bytes)"}; payload =
 * {"format":"castbridge-ledger/1","tool":"…","exportedAt":"…","entries":[…]} with entries of type license, issuance, transfer.
 * Importing is idempotent (same payload = one import; same nonce = one issuance) and reports conflicts instead of guessing:
 * UNKNOWN_LICENSE, OVER_QUOTA (more devices than seats), TWO_TOOLS (same seat and kind issued differently by two tools),
 * TRANSFER_CAP. Conflicts wait for a manual decision of the owner.
 */
@Service
public class LedgerService {
    public static final String FORMAT = "castbridge-ledger/1";

    private final JdbcTemplate jdbc;
    private final LicenseService licenses;
    private final LicenseKeyring keyring;
    private final AuditLog audit;
    private final LicenseProperties props;
    private final ObjectMapper json;
    private final Map<String, TrustedKey> trusted = new HashMap<>();

    record TrustedKey(byte[] publicKey, String tool) {}

    public LedgerService(JdbcTemplate jdbc, LicenseService licenses, LicenseKeyring keyring, AuditLog audit, LicenseProperties props, ObjectMapper json) {
        this.jdbc = jdbc;
        this.licenses = licenses;
        this.keyring = keyring;
        this.audit = audit;
        this.props = props;
        this.json = json;
        for (String k : props.ledgerKeys()) {
            String[] p = k.split(":");
            if (p.length != 3 || !(p[1].equals("desktop") || p[1].equals("phone"))) continue;
            byte[] pub = Base64.getDecoder().decode(p[2].trim());
            if (pub.length == 32) trusted.put(LicenseKeyring.kidOf(pub), new TrustedKey(pub, p[1]));
        }
        if (keyring.present()) trusted.put(keyring.kid(), new TrustedKey(Base64.getDecoder().decode(keyring.publicKeyBase64()), "server"));
    }

    // ------------------------------------------------------------------ report types

    public record ImportReport(Long importId, boolean dryRun, String tool, String kid, int entries, int applied, int duplicates, int ignored,
                               List<ConflictRow> conflicts) {}

    public record ConflictRow(long id, long importId, String type, String licenseId, String deviceCode, String detail, String status, String decidedBy,
                              Instant decidedAt, String reason) {}

    private enum Kind { APPLIED, DUPLICATE, CONFLICT }

    private record Result(Kind kind, String type, String detail) {
        static Result applied() { return new Result(Kind.APPLIED, null, null); }

        static Result duplicate() { return new Result(Kind.DUPLICATE, null, null); }

        static Result conflict(String type, String detail) { return new Result(Kind.CONFLICT, type, detail); }
    }

    // ------------------------------------------------------------------ export

    @Transactional
    public byte[] export(Actor actor) {
        actor.require(Role.Permission.LEDGER_EXPORT, props.requireTotp());
        if (!keyring.present()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Aucune clé de signature serveur : export impossible");
        ObjectNode payload = json.createObjectNode();
        payload.put("format", FORMAT);
        payload.put("tool", "server");
        payload.put("exportedAt", Instant.now().toString());
        ArrayNode entries = payload.putArray("entries");
        jdbc.query("SELECT license_id, kind, seats_allowed, end_at FROM lic_license ORDER BY id LIMIT 50000", rs -> {
            ObjectNode e = entries.addObject();
            e.put("t", "license");
            e.put("licenseId", rs.getString("license_id"));
            e.put("kind", rs.getString("kind"));
            e.put("seats", rs.getInt("seats_allowed"));
            Timestamp end = rs.getTimestamp("end_at");
            if (end == null) e.putNull("endAt"); else e.put("endAt", end.toInstant().toString());
        });
        jdbc.query("SELECT l.license_id, i.device_code, i.kind, i.kid, i.nonce, i.issued_at, i.expires_at, i.token_fingerprint, i.issuer FROM lic_issuance i"
                + " JOIN lic_license l ON l.id = i.license_pk ORDER BY i.id LIMIT 50000", rs -> {
            ObjectNode e = entries.addObject();
            e.put("t", "issuance");
            e.put("licenseId", rs.getString("license_id"));
            e.put("deviceCode", rs.getString("device_code"));
            e.put("kind", rs.getString("kind"));
            e.put("kid", rs.getString("kid"));
            e.put("nonce", rs.getString("nonce"));
            e.put("issuedAt", rs.getTimestamp("issued_at").toInstant().toString());
            Timestamp ex = rs.getTimestamp("expires_at");
            if (ex == null) e.putNull("expiresAt"); else e.put("expiresAt", ex.toInstant().toString());
            e.put("fingerprint", rs.getString("token_fingerprint"));
            e.put("issuer", rs.getString("issuer"));
        });
        jdbc.query("SELECT l.license_id, t.from_device_code, t.to_device_code, t.signed_by, t.at FROM lic_transfer t JOIN lic_license l ON l.id = t.license_pk"
                + " WHERE t.accepted = TRUE ORDER BY t.id LIMIT 50000", rs -> {
            ObjectNode e = entries.addObject();
            e.put("t", "transfer");
            e.put("licenseId", rs.getString("license_id"));
            e.put("from", rs.getString("from_device_code"));
            e.put("to", rs.getString("to_device_code"));
            e.put("signedBy", rs.getString("signed_by"));
            e.put("at", rs.getTimestamp("at").toInstant().toString());
        });
        byte[] bytes = write(payload);
        ObjectNode env = json.createObjectNode();
        env.put("v", 1);
        env.put("kid", keyring.kid());
        env.put("tool", "server");
        env.put("payload", Base64.getEncoder().encodeToString(bytes));
        env.put("sig", Base64.getEncoder().encodeToString(keyring.sign(bytes)));
        audit.record(actor, "LEDGER_EXPORT", "LEDGER", "server", null, Map.of("entries", entries.size()));
        return write(env);
    }

    private byte[] write(JsonNode n) {
        try {
            return json.writeValueAsBytes(n);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    // ------------------------------------------------------------------ import

    @Transactional
    public ImportReport importLedger(Actor actor, byte[] file, boolean dryRun) {
        actor.require(Role.Permission.LEDGER_IMPORT, props.requireTotp());
        if (file == null || file.length == 0) throw ApiException.badRequest("Fichier de registre vide");
        if (file.length > props.maxImportBytes()) throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "Registre trop volumineux (" + props.maxImportBytes() / 1_000_000 + " Mo au maximum)");
        JsonNode env = parse(file, "Le fichier n'est pas un registre JSON valide");
        String kid = env.path("kid").asText("");
        TrustedKey key = trusted.get(kid);
        if (key == null) throw ApiException.badRequest("Clé de registre inconnue (kid « " + AuditLog.clip(kid, 16) + " ») : ajoutez sa clé publique à CASTBRIDGE_LICENSES_LEDGER_KEYS");
        byte[] payloadBytes, sig;
        try {
            payloadBytes = Base64.getDecoder().decode(env.path("payload").asText(""));
            sig = Base64.getDecoder().decode(env.path("sig").asText(""));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Registre illisible (base64 invalide)");
        }
        if (!LicenseKeyring.verify(key.publicKey(), payloadBytes, sig)) throw ApiException.badRequest("Signature du registre invalide : fichier altéré ou mauvaise clé");
        JsonNode payload = parse(payloadBytes, "Contenu du registre illisible");
        if (!FORMAT.equals(payload.path("format").asText())) throw ApiException.badRequest("Format de registre inconnu (attendu : " + FORMAT + ")");
        String tool = payload.path("tool").asText("");
        if (!tool.equals(key.tool())) throw ApiException.badRequest("L'outil déclaré (« " + AuditLog.clip(tool, 12) + " ») ne correspond pas à la clé de signature");
        JsonNode list = payload.path("entries");
        if (!list.isArray()) throw ApiException.badRequest("Registre sans liste d'entrées");
        if (list.size() > props.maxImportEntries()) throw ApiException.badRequest("Trop d'entrées (" + props.maxImportEntries() + " au maximum par fichier)");

        List<Entry> entries = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            try {
                entries.add(Entry.parse(list.get(i), tool));
            } catch (ApiException e) {
                if (errors.size() < 10) errors.add("entrée " + (i + 1) + " : " + e.getMessage());
            }
        }
        if (!errors.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "Registre refusé : entrées invalides", errors);
        entries.sort(Comparator.comparing(Entry::at).thenComparing(e -> e.type().ordinal()));

        String sha = Hashing.sha256Hex(payloadBytes);
        Integer already = jdbc.queryForObject("SELECT COUNT(*) FROM lic_ledger_import WHERE sha256 = ?", Integer.class, sha);
        if (already != null && already > 0) throw ApiException.conflict("Ce registre a déjà été importé : rien à faire (l'import est idempotent)");
        Instant exportedAt = null;
        try {
            if (payload.hasNonNull("exportedAt")) exportedAt = Instant.parse(payload.get("exportedAt").asText());
        } catch (DateTimeParseException ignored) {
            // informative only
        }
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        Instant exp = exportedAt;
        jdbc.update(con -> {
            var ps = con.prepareStatement("INSERT INTO lic_ledger_import (sha256, tool, kid, exported_at, imported_at, imported_by, entries, applied, duplicates, conflicts)"
                    + " VALUES (?,?,?,?,?,?,?,0,0,0)", Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, sha);
            ps.setString(2, tool);
            ps.setString(3, kid);
            ps.setTimestamp(4, LicenseService.ts(exp));
            ps.setTimestamp(5, LicenseService.ts(Instant.now()));
            ps.setString(6, actor.name());
            ps.setInt(7, entries.size());
            return ps;
        }, keys);
        long importId = keys.getKey().longValue();

        int applied = 0, dup = 0, ignored = 0;
        List<ConflictRow> conflicts = new ArrayList<>();
        for (Entry e : entries) {
            if (e.type() == Entry.Type.IGNORED) { ignored++; continue; }
            Result r = apply(e, importId, false);
            switch (r.kind()) {
                case APPLIED -> applied++;
                case DUPLICATE -> dup++;
                case CONFLICT -> conflicts.add(newConflict(importId, e, r));
            }
        }
        jdbc.update("UPDATE lic_ledger_import SET applied = ?, duplicates = ?, conflicts = ? WHERE id = ?", applied, dup, conflicts.size(), importId);
        audit.record(actor, dryRun ? "LEDGER_DRYRUN" : "LEDGER_IMPORT", "LEDGER", sha.substring(0, 16), null,
                Map.of("tool", tool, "entries", entries.size(), "applied", applied, "duplicates", dup, "conflicts", conflicts.size()));
        if (dryRun) TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        return new ImportReport(dryRun ? null : importId, dryRun, tool, kid, entries.size(), applied, dup, ignored, conflicts);
    }

    private JsonNode parse(byte[] b, String error) {
        try {
            return json.readTree(b);
        } catch (java.io.IOException e) {
            throw ApiException.badRequest(error);
        }
    }

    private ConflictRow newConflict(long importId, Entry e, Result r) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        String entryJson = e.raw().toString();
        if (entryJson.length() > 4000) entryJson = entryJson.substring(0, 4000);
        String dev = e.deviceCode();
        String es = entryJson;
        jdbc.update(con -> {
            var ps = con.prepareStatement("INSERT INTO lic_conflict (import_id, type, license_id, device_code, detail, entry_json, status) VALUES (?,?,?,?,?,?,'OPEN')",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, importId);
            ps.setString(2, r.type());
            ps.setString(3, e.licenseId());
            ps.setString(4, dev);
            ps.setString(5, AuditLog.clip(r.detail(), 500));
            ps.setString(6, es);
            return ps;
        }, keys);
        return conflict(keys.getKey().longValue());
    }

    // ------------------------------------------------------------------ applying one entry

    private Result apply(Entry e, long importId, boolean force) {
        return switch (e.type()) {
            case LICENSE -> applyLicense(e);
            case ISSUANCE -> applyIssuance(e, force);
            case TRANSFER -> applyTransfer(e, importId, force);
            case IGNORED -> Result.duplicate();
        };
    }

    private Result applyLicense(Entry e) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM lic_license WHERE license_id = ?", Integer.class, e.licenseId());
        if (n != null && n > 0) return Result.duplicate();
        // a licence created offline: it lands on a placeholder client, to be completed by the owner
        long client = placeholderClient();
        Instant now = Instant.now();
        jdbc.update("INSERT INTO lic_license (license_id, client_id, kind, state, seats_allowed, start_at, end_at, grace_days, transfer_cap, created_by, created_at, updated_at)"
                        + " VALUES (?,?,?,'ACTIVE',?,?,?,?,?,?,?,?)", e.licenseId(), client, e.licenseKind(), e.seats(), LicenseService.ts(e.at()), LicenseService.ts(e.endAt()),
                props.defaultGraceDays(), props.defaultTransferCap(), "import:" + e.tool(), LicenseService.ts(now), LicenseService.ts(now));
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

    private Result applyIssuance(Entry e, boolean force) {
        List<Long> exists = jdbc.queryForList("SELECT id FROM lic_license WHERE license_id = ?", Long.class, e.licenseId());
        if (exists.isEmpty()) return Result.conflict("UNKNOWN_LICENSE", "Licence inconnue du serveur : " + e.licenseId());
        LicenseService.LicenseRow l = licenses.lock(e.licenseId());
        Integer dup = jdbc.queryForObject("SELECT COUNT(*) FROM lic_issuance WHERE license_pk = ? AND nonce = ?", Integer.class, l.id(), e.nonce());
        if (dup != null && dup > 0) return Result.duplicate();
        if (!force) {
            List<String> other = jdbc.queryForList("SELECT issuer FROM lic_issuance WHERE license_pk = ? AND device_code = ? AND kind = ? AND token_fingerprint <> ? AND channel <> ? LIMIT 1",
                    String.class, l.id(), e.deviceCode(), e.kind(), e.fingerprint(), "ledger-" + e.tool());
            if (!other.isEmpty()) {
                return Result.conflict("TWO_TOOLS", "Le même poste (" + DeviceCode.masked(e.deviceCode()) + ", " + e.kind() + ") a reçu deux activations différentes : « "
                        + other.get(0) + " » et « " + e.tool() + " »");
            }
        }
        Long seatId = null;
        if (!l.state().equals("REVOKED")) {
            int active = licenses.activeSeats(l.id());
            Integer has = jdbc.queryForObject("SELECT COUNT(*) FROM lic_seat WHERE license_pk = ? AND device_code = ? AND state = 'ACTIVE'", Integer.class, l.id(), e.deviceCode());
            if (has != null && has == 0 && active >= l.seatsAllowed()) {
                if (!force) {
                    return Result.conflict("OVER_QUOTA", "Dépassement de postes : " + active + "/" + l.seatsAllowed() + " utilisés, et " + DeviceCode.masked(e.deviceCode()) + " en demande un de plus");
                }
                jdbc.update("UPDATE lic_license SET seats_allowed = ?, version = version + 1 WHERE id = ?", active + 1, l.id());
                l = licenses.lock(e.licenseId());
            }
            licenses.allocateSeat(l, e.deviceCode(), null, e.at());
            seatId = jdbc.queryForObject("SELECT id FROM lic_seat WHERE license_pk = ? AND device_code = ?", Long.class, l.id(), e.deviceCode());
        }
        try {
            jdbc.update("INSERT INTO lic_issuance (license_pk, seat_pk, device_code, kind, kid, nonce, issued_at, expires_at, issuer, channel, token_fingerprint, source)"
                            + " VALUES (?,?,?,?,?,?,?,?,?,?,?,'IMPORT')", l.id(), seatId, e.deviceCode(), e.kind(), e.kid(), e.nonce(), LicenseService.ts(e.at()),
                    LicenseService.ts(e.endAt()), e.tool(), "ledger-" + e.tool(), e.fingerprint());
        } catch (DuplicateKeyException ex) {
            return Result.duplicate();
        }
        return Result.applied();
    }

    private Result applyTransfer(Entry e, long importId, boolean force) {
        List<Long> exists = jdbc.queryForList("SELECT id FROM lic_license WHERE license_id = ?", Long.class, e.licenseId());
        if (exists.isEmpty()) return Result.conflict("UNKNOWN_LICENSE", "Licence inconnue du serveur : " + e.licenseId());
        LicenseService.LicenseRow l = licenses.lock(e.licenseId());
        Integer dup = jdbc.queryForObject("SELECT COUNT(*) FROM lic_transfer WHERE license_pk = ? AND from_device_code = ? AND to_device_code = ? AND at = ?", Integer.class,
                l.id(), e.fromDevice(), e.deviceCode(), Timestamp.from(e.at()));
        if (dup != null && dup > 0) return Result.duplicate();
        if (!force) {
            int count = licenses.transfersSince(l.id(), e.at().minus(Duration.ofDays(365)));
            if (count >= l.transferCap()) {
                return Result.conflict("TRANSFER_CAP", "Plafond de transferts dépassé : " + count + " sur les 12 derniers mois pour un plafond de " + l.transferCap());
            }
        }
        int active = licenses.activeSeats(l.id());
        Integer fromActive = jdbc.queryForObject("SELECT COUNT(*) FROM lic_seat WHERE license_pk = ? AND device_code = ? AND state = 'ACTIVE'", Integer.class, l.id(), e.fromDevice());
        Integer toActive = jdbc.queryForObject("SELECT COUNT(*) FROM lic_seat WHERE license_pk = ? AND device_code = ? AND state = 'ACTIVE'", Integer.class, l.id(), e.deviceCode());
        int after = active - (fromActive == null ? 0 : fromActive) + (toActive != null && toActive > 0 ? 0 : 1);
        if (after > l.seatsAllowed()) {
            if (!force) return Result.conflict("OVER_QUOTA", "Le transfert dépasserait le nombre de postes (" + active + "/" + l.seatsAllowed() + ")");
            jdbc.update("UPDATE lic_license SET seats_allowed = ?, version = version + 1 WHERE id = ?", after, l.id());
            l = licenses.lock(e.licenseId());
        }
        jdbc.update("UPDATE lic_seat SET state = 'TRANSFERRED', slot_no = NULL, released_at = ?, released_reason = 'transfert' WHERE license_pk = ? AND device_code = ? AND state = 'ACTIVE'",
                Timestamp.from(e.at()), l.id(), e.fromDevice());
        licenses.allocateSeat(l, e.deviceCode(), null, e.at());
        jdbc.update("INSERT INTO lic_transfer (license_pk, from_device_code, to_device_code, signed_by, at, accepted, ledger_import_id) VALUES (?,?,?,?,?,TRUE,?)",
                l.id(), e.fromDevice(), e.deviceCode(), e.tool(), Timestamp.from(e.at()), importId);
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
        return jdbc.queryForList("SELECT id, tool, kid, exported_at, imported_at, imported_by, entries, applied, duplicates, conflicts FROM lic_ledger_import ORDER BY id DESC LIMIT ?", limit);
    }

    private ConflictRow mapConflict(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ConflictRow(rs.getLong("id"), rs.getLong("import_id"), rs.getString("type"), rs.getString("license_id"), rs.getString("device_code"),
                rs.getString("detail"), rs.getString("status"), rs.getString("decided_by"), LicenseService.inst(rs, "decided_at"), rs.getString("reason"));
    }

    /** Manual decision: accept = apply the entry anyway (raising the quota if needed), reject = keep it out. The reason is mandatory. */
    @Transactional
    public ConflictRow decide(Actor actor, long id, boolean accept, String reason) {
        actor.require(Role.Permission.LEDGER_IMPORT, props.requireTotp());
        String why = Validate.reason(reason);
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM lic_conflict WHERE id = ? FOR UPDATE", id);
        if (rows.isEmpty()) throw ApiException.notFound("Conflit introuvable");
        Map<String, Object> c = rows.get(0);
        if (!"OPEN".equals(c.get("status"))) throw ApiException.conflict("Ce conflit a déjà été décidé");
        Entry e;
        try {
            JsonNode raw = json.readTree((String) (c.get("entry_json")));
            e = Entry.parse(raw, raw.path("tool").asText(null) == null ? "desktop" : raw.path("tool").asText());
        } catch (java.io.IOException ex) {
            throw new IllegalStateException(ex);
        }
        long importId = ((Number) (c.get("import_id"))).longValue();
        if (accept) {
            Result r = apply(e, importId, true);
            if (r.kind() == Kind.CONFLICT) throw ApiException.conflict(r.detail() + " : créez d'abord la licence, puis acceptez de nouveau");
        } else if (e.type() == Entry.Type.TRANSFER) {
            LicenseService.LicenseRow l = licenses.get(e.licenseId());
            jdbc.update("INSERT INTO lic_transfer (license_pk, from_device_code, to_device_code, signed_by, at, accepted, ledger_import_id) VALUES (?,?,?,?,?,FALSE,?)",
                    l.id(), e.fromDevice(), e.deviceCode(), e.tool(), Timestamp.from(e.at()), importId);
        }
        jdbc.update("UPDATE lic_conflict SET status = ?, decided_by = ?, decided_at = ?, reason = ? WHERE id = ?", accept ? "ACCEPTED" : "REJECTED", actor.name(),
                LicenseService.ts(Instant.now()), why, id);
        audit.record(actor, accept ? "CONFLICT_ACCEPT" : "CONFLICT_REJECT", "LEDGER", Long.toString(id), why, Map.of("type", String.valueOf(c.get("type"))));
        return conflict(id);
    }

    // ------------------------------------------------------------------ entries

    /** One validated entry of a ledger file. */
    record Entry(Type type, String tool, String licenseId, String deviceCode, String fromDevice, String kind, String kid, String nonce, Instant at, Instant endAt,
                 String fingerprint, int seats, String licenseKind, JsonNode raw) {
        enum Type { LICENSE, ISSUANCE, TRANSFER, IGNORED }

        static Entry parse(JsonNode n, String tool) {
            if (n == null || !n.isObject()) throw ApiException.badRequest("entrée non valide");
            String t = n.path("t").asText("");
            ObjectNode raw = ((ObjectNode) n.deepCopy());
            raw.put("tool", tool);
            switch (t) {
                case "license" -> {
                    return new Entry(Type.LICENSE, tool, Validate.licenseId(n.path("licenseId").asText(null)), null, null, null, null, null,
                            time(n, "startAt", false) == null ? Instant.EPOCH.plusSeconds(1) : time(n, "startAt", false), time(n, "endAt", true), null,
                            Validate.range(n.hasNonNull("seats") ? n.get("seats").asInt() : null, "postes", 1, 1000),
                            n.path("kind").asText("PAID").equals("TRIAL") ? "TRIAL" : "PAID", raw);
                }
                case "issuance" -> {
                    String kind = n.path("kind").asText("");
                    try {
                        IssueKind.valueOf(kind);
                    } catch (IllegalArgumentException ex) {
                        throw ApiException.badRequest("sorte d'activation inconnue");
                    }
                    String kid = n.path("kid").asText("");
                    String nonce = n.path("nonce").asText("");
                    String fp = n.path("fingerprint").asText("");
                    if (!kid.matches("[0-9a-f]{8,16}")) throw ApiException.badRequest("kid invalide");
                    if (!nonce.matches("[0-9a-f]{32}")) throw ApiException.badRequest("nonce invalide (32 chiffres hexadécimaux)");
                    if (!fp.matches("[0-9a-f]{64}")) throw ApiException.badRequest("empreinte du jeton invalide (SHA-256 hexadécimal)");
                    Instant at = time(n, "issuedAt", false);
                    if (at == null) throw ApiException.badRequest("issuedAt manquant");
                    return new Entry(Type.ISSUANCE, tool, Validate.licenseId(n.path("licenseId").asText(null)), DeviceCode.normalize(n.path("deviceCode").asText(null)), null,
                            kind, kid, nonce, at, time(n, "expiresAt", true), fp, 0, null, raw);
                }
                case "transfer" -> {
                    String by = n.path("signedBy").asText(tool);
                    if (by.equals("server") || tool.equals("server")) {
                        throw ApiException.badRequest("un transfert ne peut jamais être signé par le serveur");
                    }
                    Instant at = time(n, "at", false);
                    if (at == null) throw ApiException.badRequest("date du transfert manquante");
                    return new Entry(Type.TRANSFER, tool, Validate.licenseId(n.path("licenseId").asText(null)), DeviceCode.normalize(n.path("to").asText(null)),
                            DeviceCode.normalize(n.path("from").asText(null)), null, null, null, at, null, null, 0, null, raw);
                }
                case "revocation" -> {
                    return new Entry(Type.IGNORED, tool, null, null, null, null, null, null, Instant.EPOCH, null, null, 0, null, raw);
                }
                default -> throw ApiException.badRequest("type d'entrée inconnu « " + AuditLog.clip(t, 16) + " »");
            }
        }

        private static Instant time(JsonNode n, String field, boolean nullable) {
            if (!n.hasNonNull(field)) return null;
            try {
                return Instant.parse(n.get(field).asText());
            } catch (DateTimeParseException e) {
                throw ApiException.badRequest(field + " : date invalide");
            }
        }
    }
}
