package castbridge.server.licenses;

import castbridge.server.web.ApiException;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * /api/v1/admin/licenses/** : the same functions as the pages, in JSON, for scripts (admin bearer token = OWNER).
 * Pages of {@code {"items":[…],"page":0,"size":50,"total":n}} (size ≤ 100); errors as {@link castbridge.server.web.ApiError}.
 */
@RestController
@RequestMapping("/api/v1/admin/licenses")
public class LicenseApiController {
    private final LicenseService licenses;
    private final ActivationService activations;
    private final ClientService clients;
    private final ProductService products;
    private final AuditLog audit;
    private final LedgerService ledger;
    private final AbuseService abuse;
    private final LicenseProperties props;
    private final LicenseKeyring keyring;

    public LicenseApiController(LicenseService licenses, ActivationService activations, ClientService clients, ProductService products, AuditLog audit,
                                LedgerService ledger, AbuseService abuse, LicenseProperties props, LicenseKeyring keyring) {
        this.keyring = keyring;
        this.licenses = licenses;
        this.activations = activations;
        this.clients = clients;
        this.products = products;
        this.audit = audit;
        this.ledger = ledger;
        this.abuse = abuse;
        this.props = props;
    }

    private Actor actor() { return Actor.token(); }

    private void read(Role.Permission p) { actor().require(p, props.requireTotp()); }

    // ---- licences

    @GetMapping
    public ResponseEntity<?> list(@RequestParam(required = false) String q, @RequestParam(required = false) String state, @RequestParam(required = false) Long client,
                                  @RequestParam(required = false) String product, @RequestParam(required = false) Integer expiringDays,
                                  @RequestParam(required = false) String sort, @RequestParam(defaultValue = "desc") String dir,
                                  @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
                                  @RequestParam(required = false) String format) {
        read(Role.Permission.LICENSE_READ);
        var f = new LicenseService.Filter(q, state, client, product, expiringDays, sort, !"asc".equals(dir));
        if ("csv".equals(format)) {
            return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"licences.csv\"").body(LicenseCsv.of(licenses.exportRows(f)));
        }
        return ResponseEntity.ok(licenses.list(f, Validate.page(page), Validate.size(size, 50)));
    }

    public record CreateBody(String licenseId, Long clientId, String kind, Integer seats, String startAt, String endAt, Integer graceDays, Integer transferCap,
                             List<String> productIds) {}

    @PostMapping
    public ResponseEntity<LicenseService.LicenseRow> create(@RequestBody CreateBody b) {
        var l = licenses.create(actor(), new LicenseService.NewLicense(b.licenseId(), b.clientId(), b.kind(), b.seats(), Validate.instant(b.startAt(), "Début", false),
                Validate.instant(b.endAt(), "Fin", true), b.graceDays(), b.transferCap(), b.productIds()));
        return ResponseEntity.status(201).body(l);
    }

    @GetMapping("/{licenseId}")
    public LicenseService.Detail get(@PathVariable String licenseId) {
        read(Role.Permission.LICENSE_READ);
        return licenses.detail(Validate.licenseId(licenseId));
    }

    public record ReasonBody(String reason) {}

    @PostMapping("/{licenseId}/suspend")
    public LicenseService.LicenseRow suspend(@PathVariable String licenseId, @RequestBody ReasonBody b) { return licenses.suspend(actor(), Validate.licenseId(licenseId), b.reason()); }

    @PostMapping("/{licenseId}/resume")
    public LicenseService.LicenseRow resume(@PathVariable String licenseId, @RequestBody ReasonBody b) { return licenses.resume(actor(), Validate.licenseId(licenseId), b.reason()); }

    @PostMapping("/{licenseId}/revoke")
    public LicenseService.LicenseRow revoke(@PathVariable String licenseId, @RequestBody ReasonBody b) { return licenses.revoke(actor(), Validate.licenseId(licenseId), b.reason()); }

    public record ExtendBody(String endAt, String reason) {}

    @PostMapping("/{licenseId}/extend")
    public LicenseService.LicenseRow extend(@PathVariable String licenseId, @RequestBody ExtendBody b) {
        return licenses.extend(actor(), Validate.licenseId(licenseId), Validate.instant(b.endAt(), "Nouvelle fin", true), b.reason());
    }

    public record SeatsBody(Integer seats, String reason) {}

    @PostMapping("/{licenseId}/seats")
    public LicenseService.LicenseRow seats(@PathVariable String licenseId, @RequestBody SeatsBody b) { return licenses.setSeats(actor(), Validate.licenseId(licenseId), b.seats(), b.reason()); }

    public record SettingsBody(Integer graceDays, Integer transferCap, String reason) {}

    @PostMapping("/{licenseId}/settings")
    public LicenseService.LicenseRow settings(@PathVariable String licenseId, @RequestBody SettingsBody b) {
        return licenses.update(actor(), Validate.licenseId(licenseId), b.graceDays(), b.transferCap(), b.reason());
    }

    public record AddProductBody(String productId, String endsAt) {}

    @PostMapping("/{licenseId}/products")
    public LicenseService.LicenseRow addProduct(@PathVariable String licenseId, @RequestBody AddProductBody b) {
        return licenses.addProduct(actor(), Validate.licenseId(licenseId), b.productId(), Validate.instant(b.endsAt(), "Fin du bouquet", true));
    }

    public record ReleaseBody(String seatId, String reason) {}

    @PostMapping("/{licenseId}/seats/release")
    public LicenseService.LicenseRow release(@PathVariable String licenseId, @RequestBody ReleaseBody b) {
        return licenses.releaseSeat(actor(), Validate.licenseId(licenseId), b.seatId(), b.reason());
    }

    /**
     * @param deviceRequest the device's "demande d'appareil" (code=…, k=…, factor=TYPE|hash…): the code alone does not allow to build an activation
     * @param subject       tv (default) or phone
     */
    public record IssueBody(String subject, String deviceRequest, String kind, List<String> productIds, Integer windowDays) {}

    @PostMapping("/{licenseId}/activations")
    public ActivationService.Activation issue(@PathVariable String licenseId, @RequestBody IssueBody b) {
        return activations.issue(actor(), new ActivationService.IssueRequest(licenseId, b.subject(), b.deviceRequest(), b.kind(), b.productIds(), b.windowDays()), "server-api");
    }

    /** Re-issue: by seat id (the hardware is already on the seat) or from a pasted device request. */
    public record ReissueBody(String seatId, String subject, String deviceRequest) {}

    @PostMapping("/{licenseId}/reissue")
    public ActivationService.Activation reissue(@PathVariable String licenseId, @RequestBody ReissueBody b) {
        if (b.seatId() != null && !b.seatId().isBlank()) return activations.reissueSeat(actor(), licenseId, b.seatId(), "server-api");
        return activations.reissue(actor(), Validate.licenseId(licenseId), b.subject(), b.deviceRequest(), "server-api");
    }

    public record KeyRevokeBody(String kid, String reason) {}

    /** Revokes a signing key (kid): it goes into the signed revocation list and the registry. */
    @PostMapping("/keys/revoke")
    public Map<String, Object> revokeKey(@RequestBody KeyRevokeBody b) {
        licenses.revokeKey(actor(), b.kid(), b.reason());
        return Map.of("revoked", b.kid());
    }

    @GetMapping("/devices/{code}")
    public List<Map<String, Object>> device(@PathVariable String code) {
        read(Role.Permission.LICENSE_READ);
        return licenses.byDeviceCode(code);
    }

    // ---- clients

    @GetMapping("/clients")
    public Page<ClientService.ClientRow> clients(@RequestParam(required = false) String q, @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        read(Role.Permission.LICENSE_READ);
        return clients.list(q, Validate.page(page), Validate.size(size, 50));
    }

    public record ClientBody(String name, String contact, String notes) {}

    @PostMapping("/clients")
    public ResponseEntity<ClientService.ClientRow> createClient(@RequestBody ClientBody b) { return ResponseEntity.status(201).body(clients.create(actor(), b.name(), b.contact(), b.notes())); }

    @GetMapping("/clients/{id}")
    public ClientService.ClientRow client(@PathVariable long id) {
        read(Role.Permission.LICENSE_READ);
        return clients.get(id);
    }

    @PutMapping("/clients/{id}")
    public ClientService.ClientRow updateClient(@PathVariable long id, @RequestBody ClientBody b) { return clients.update(actor(), id, b.name(), b.contact(), b.notes()); }

    @GetMapping("/clients/{id}/export")
    public Map<String, Object> exportClient(@PathVariable long id) { return clients.export(actor(), id); }

    @PostMapping("/clients/{id}/erase")
    public ClientService.ClientRow eraseClient(@PathVariable long id, @RequestBody ReasonBody b) { return clients.erase(actor(), id, b.reason()); }

    // ---- bouquets

    @GetMapping("/products")
    public List<ProductService.ProductRow> products() {
        read(Role.Permission.LICENSE_READ);
        return products.list();
    }

    @PostMapping("/products")
    public ResponseEntity<ProductService.ProductRow> createProduct(@RequestBody ProductService.NewProduct b) { return ResponseEntity.status(201).body(products.create(actor(), b)); }

    public record ProductPatch(String title, List<String> lots, Boolean active, String reason) {}

    @PutMapping("/products/{productId}")
    public ProductService.ProductRow updateProduct(@PathVariable String productId, @RequestBody ProductPatch b) {
        return products.update(actor(), Validate.productId(productId), b.title(), b.lots(), b.active(), b.reason());
    }

    // ---- dashboard, alerts, audit

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() {
        read(Role.Permission.DASHBOARD);
        return abuse.dashboard();
    }

    @GetMapping("/alerts")
    public List<AbuseService.Alert> alerts() {
        read(Role.Permission.DASHBOARD);
        return abuse.alerts();
    }

    @GetMapping("/audit")
    public Page<AuditLog.Entry> audit(@RequestParam(required = false) String actor, @RequestParam(required = false) String action, @RequestParam(required = false) String targetType,
                                      @RequestParam(required = false) String targetId, @RequestParam(required = false) String from, @RequestParam(required = false) String to,
                                      @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        read(Role.Permission.AUDIT_READ);
        return audit.search(new AuditLog.Filter(actor, action, targetType, targetId, Validate.instant(from, "Du", false), Validate.instant(to, "Au", true)),
                Validate.page(page), Validate.size(size, 50));
    }

    @GetMapping("/audit/verify")
    public AuditLog.Verification verify() {
        read(Role.Permission.AUDIT_READ);
        return audit.verify();
    }

    // ---- registry

    @GetMapping("/ledger/export")
    public ResponseEntity<byte[]> ledgerExport() {
        byte[] b = ledger.export(actor());
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"registre-" + Instant.now().toString().substring(0, 10) + ".json\"").body(b);
    }

    /** @param policy review (default: conflicts wait for a decision) or auto (the format's own resolution, for scripts) */
    @PostMapping(value = "/ledger/import", consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_OCTET_STREAM_VALUE, "text/plain"})
    public LedgerService.ImportReport ledgerImport(@RequestBody byte[] body, @RequestParam(defaultValue = "false") boolean dryRun, @RequestParam(defaultValue = "review") String policy) throws IOException {
        if (!policy.equals("review") && !policy.equals("auto")) throw ApiException.badRequest("policy : review ou auto");
        return ledger.importLedger(actor(), body, dryRun, policy.equals("auto"));
    }

    @GetMapping("/ledger/conflicts")
    public Page<LedgerService.ConflictRow> conflicts(@RequestParam(required = false) String status, @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        read(Role.Permission.LICENSE_READ);
        return ledger.conflicts(status, Validate.page(page), Validate.size(size, 50));
    }

    public record DecisionBody(Boolean accept, String reason) {}

    @PostMapping("/ledger/conflicts/{id}/decision")
    public LedgerService.ConflictRow decide(@PathVariable long id, @RequestBody DecisionBody b) {
        if (b.accept() == null) throw ApiException.badRequest("accept : true ou false attendu");
        return ledger.decide(actor(), id, b.accept(), b.reason());
    }

    @GetMapping("/ledger/imports")
    public List<Map<String, Object>> imports() {
        read(Role.Permission.LICENSE_READ);
        return ledger.imports(50);
    }

    /** What the server key may and may not issue (documentation of the scope, machine-readable). */
    @GetMapping("/signing")
    public Map<String, Object> signing() {
        read(Role.Permission.LICENSE_READ);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("keyLoaded", keyring.present());
        m.put("kid", keyring.kid());
        m.put("publicKey", keyring.publicKeyBase64());
        m.put("scopes", ScopedActivationSigner.SERVER_SCOPES);
        m.put("issuableKinds", activations.serverKinds());
        m.put("format", activations.format());
        m.put("trialIssuance", props.trialIssuance());
        return m;
    }
}
