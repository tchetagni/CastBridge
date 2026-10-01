package castbridge.server.licenses;

import castbridge.server.web.ApiException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Admin pages of the licence module (/admin/licenses/**). Same rules as the rest of /admin: session + CSRF + strict CSP (no
 * inline script nor style), French, dark theme, usable on a phone. EVERY permission is checked here on the server from the
 * role stored in the database; a button hidden by the page is cosmetic. Destructive actions go through a confirmation page
 * with a mandatory reason, and the service refuses an empty reason anyway.
 */
@Controller
public class LicenseWebController {
    private final LicenseService licenses;
    private final ActivationService activations;
    private final ClientService clients;
    private final ProductService products;
    private final AuditLog audit;
    private final LedgerService ledger;
    private final AbuseService abuse;
    private final LicenseAccounts accounts;
    private final LicenseProperties props;
    private final LicenseKeyring keyring;
    private final TotpVault vault;

    public LicenseWebController(LicenseService licenses, ActivationService activations, ClientService clients, ProductService products, AuditLog audit,
                                LedgerService ledger, AbuseService abuse, LicenseAccounts accounts, LicenseProperties props, LicenseKeyring keyring, TotpVault vault) {
        this.licenses = licenses;
        this.activations = activations;
        this.clients = clients;
        this.products = products;
        this.audit = audit;
        this.ledger = ledger;
        this.abuse = abuse;
        this.accounts = accounts;
        this.props = props;
        this.keyring = keyring;
        this.vault = vault;
    }

    private Actor actor(Authentication auth) { return accounts.actorOf(auth); }

    private Actor read(Authentication auth, Role.Permission p, Model m) {
        Actor a = actor(auth);
        a.require(p, props.requireTotp());
        m.addAttribute("active", "licenses");
        m.addAttribute("me", a);
        m.addAttribute("canWrite", a.role() == Role.OWNER && (a.strong() || !props.requireTotp()));
        m.addAttribute("canReissue", a.role() != null && a.role().can(Role.Permission.REISSUE));
        m.addAttribute("canAudit", a.role() != null && a.role().can(Role.Permission.AUDIT_READ));
        return a;
    }

    private String back(RedirectAttributes ra, String target, String ok, Runnable action) {
        try {
            action.run();
            ra.addFlashAttribute("ok", ok);
        } catch (ApiException e) {
            denied(e);
            ra.addFlashAttribute("error", e.getMessage());
            if (!e.details().isEmpty()) ra.addFlashAttribute("details", e.details());
        }
        return "redirect:" + target;
    }

    /** A refused permission is a real 403, not a flash message. */
    private static void denied(ApiException e) {
        if (e.status() == org.springframework.http.HttpStatus.FORBIDDEN) throw e;
    }

    private static String confirmed(String confirm) {
        if (!"on".equals(confirm) && !"yes".equals(confirm)) throw ApiException.badRequest("Cochez la case de confirmation pour continuer");
        return confirm;
    }

    // ------------------------------------------------------------------ dashboard

    @GetMapping("/admin/licenses")
    public String dashboard(Authentication auth, Model m) {
        read(auth, Role.Permission.DASHBOARD, m);
        m.addAttribute("d", abuse.dashboard());
        m.addAttribute("sub", "dashboard");
        return "admin/lic-dashboard";
    }

    // ------------------------------------------------------------------ licences

    @GetMapping("/admin/licenses/list")
    public String list(Authentication auth, @RequestParam(required = false) String q, @RequestParam(required = false) String state, @RequestParam(required = false) Long client,
                       @RequestParam(required = false) String product, @RequestParam(required = false) Integer expiringDays, @RequestParam(defaultValue = "created") String sort,
                       @RequestParam(defaultValue = "desc") String dir, @RequestParam(required = false) Integer page, Model m) {
        read(auth, Role.Permission.LICENSE_READ, m);
        var f = new LicenseService.Filter(q, state, client, product, expiringDays, sort, !"asc".equals(dir));
        m.addAttribute("page", licenses.list(f, Validate.page(page), 50));
        m.addAttribute("f", f);
        m.addAttribute("dir", dir);
        m.addAttribute("products", products.list());
        m.addAttribute("clientList", clients.list(null, 0, 100).items());
        m.addAttribute("sub", "list");
        return "admin/lic-list";
    }

    @GetMapping("/admin/licenses/export.csv")
    public ResponseEntity<String> csv(Authentication auth, @RequestParam(required = false) String q, @RequestParam(required = false) String state,
                                      @RequestParam(required = false) Long client, @RequestParam(required = false) String product, @RequestParam(required = false) Integer expiringDays) {
        actor(auth).require(Role.Permission.LICENSE_READ, props.requireTotp());
        String body = LicenseCsv.of(licenses.exportRows(new LicenseService.Filter(q, state, client, product, expiringDays, "created", true)));
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"licences.csv\"").body(body);
    }

    @GetMapping("/admin/licenses/new")
    public String newForm(Authentication auth, Model m) {
        read(auth, Role.Permission.LICENSE_READ, m);
        m.addAttribute("clientList", clients.list(null, 0, 100).items());
        m.addAttribute("products", products.list());
        m.addAttribute("defaults", props);
        m.addAttribute("sub", "list");
        return "admin/lic-new";
    }

    @PostMapping("/admin/licenses")
    public String create(Authentication auth, @RequestParam Long clientId, @RequestParam(defaultValue = "PAID") String kind, @RequestParam(required = false) Integer seats,
                         @RequestParam(required = false) String startAt, @RequestParam(required = false) String endAt, @RequestParam(required = false) Integer graceDays,
                         @RequestParam(required = false) Integer transferCap, @RequestParam(required = false) List<String> productIds,
                         @RequestParam(required = false) String licenseId, RedirectAttributes ra) {
        try {
            var l = licenses.create(actor(auth), new LicenseService.NewLicense(licenseId, clientId, kind, seats, Validate.instant(startAt, "Début", false),
                    Validate.instant(endAt, "Fin", true), graceDays, transferCap, productIds));
            ra.addFlashAttribute("ok", "Licence " + l.licenseId() + " créée");
            return "redirect:/admin/licenses/" + l.licenseId();
        } catch (ApiException e) {
            denied(e);
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/licenses/new";
        }
    }

    @GetMapping("/admin/licenses/{licenseId:[a-z0-9][a-z0-9-]{2,63}}")
    public String detail(Authentication auth, @PathVariable String licenseId, Model m) {
        read(auth, Role.Permission.LICENSE_READ, m);
        m.addAttribute("x", licenses.detail(licenseId));
        m.addAttribute("products", products.list());
        m.addAttribute("sub", "list");
        return "admin/lic-detail";
    }

    @PostMapping("/admin/licenses/{licenseId}/extend")
    public String extend(Authentication auth, @PathVariable String licenseId, @RequestParam(required = false) String endAt, @RequestParam(required = false) String reason, RedirectAttributes ra) {
        return back(ra, "/admin/licenses/" + licenseId, "Date de fin enregistrée", () -> licenses.extend(actor(auth), licenseId, Validate.instant(endAt, "Nouvelle fin", true), reason));
    }

    @PostMapping("/admin/licenses/{licenseId}/seats")
    public String seats(Authentication auth, @PathVariable String licenseId, @RequestParam Integer seats, @RequestParam(required = false) String reason, RedirectAttributes ra) {
        return back(ra, "/admin/licenses/" + licenseId, "Nombre de postes enregistré", () -> licenses.setSeats(actor(auth), licenseId, seats, reason));
    }

    @PostMapping("/admin/licenses/{licenseId}/settings")
    public String settings(Authentication auth, @PathVariable String licenseId, @RequestParam(required = false) Integer graceDays, @RequestParam(required = false) Integer transferCap,
                           RedirectAttributes ra) {
        return back(ra, "/admin/licenses/" + licenseId, "Réglages enregistrés", () -> licenses.update(actor(auth), licenseId, graceDays, transferCap, null));
    }

    @PostMapping("/admin/licenses/{licenseId}/products")
    public String addProduct(Authentication auth, @PathVariable String licenseId, @RequestParam String productId, @RequestParam(required = false) String endsAt, RedirectAttributes ra) {
        return back(ra, "/admin/licenses/" + licenseId, "Bouquet ajouté", () -> licenses.addProduct(actor(auth), licenseId, productId, Validate.instant(endsAt, "Fin du bouquet", true)));
    }

    /** Confirmation page of a destructive action: what will happen, a mandatory reason, an explicit tick. */
    @GetMapping("/admin/licenses/{licenseId}/confirm/{action}")
    public String confirm(Authentication auth, @PathVariable String licenseId, @PathVariable String action, @RequestParam(required = false) String seat, Model m) {
        read(auth, Role.Permission.LICENSE_READ, m);
        String text = switch (action) {
            case "suspend" -> "Suspendre la licence : plus aucune activation ne sera émise ; les appareils déjà activés gardent leurs droits jusqu'à la prochaine vérification en ligne.";
            case "resume" -> "Réactiver la licence suspendue.";
            case "revoke" -> "RÉVOQUER la licence : action définitive. Tous ses postes sont libérés et la licence entre dans la liste de révocation envoyée aux appareils.";
            case "release" -> "Libérer le poste " + (seat == null ? "" : seat) + " : l'appareil perdra son droit à sa prochaine vérification en ligne (liste de révocation signée).";
            default -> throw ApiException.notFound("Action inconnue");
        };
        m.addAttribute("license", licenses.get(licenseId));
        m.addAttribute("action", action);
        m.addAttribute("seat", seat);
        m.addAttribute("text", text);
        m.addAttribute("sub", "list");
        return "admin/lic-confirm";
    }

    @PostMapping("/admin/licenses/{licenseId}/do/{action}")
    public String doAction(Authentication auth, @PathVariable String licenseId, @PathVariable String action, @RequestParam(required = false) String reason,
                           @RequestParam(required = false) String seat, @RequestParam(required = false) String confirm, RedirectAttributes ra) {
        String target = "/admin/licenses/" + licenseId;
        try {
            confirmed(confirm);
        } catch (ApiException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:" + target + "/confirm/" + action + (seat == null ? "" : "?seat=" + java.net.URLEncoder.encode(seat, StandardCharsets.UTF_8));
        }
        Actor a = actor(auth);
        return switch (action) {
            case "suspend" -> back(ra, target, "Licence suspendue", () -> licenses.suspend(a, licenseId, reason));
            case "resume" -> back(ra, target, "Licence réactivée", () -> licenses.resume(a, licenseId, reason));
            case "revoke" -> back(ra, target, "Licence révoquée", () -> licenses.revoke(a, licenseId, reason));
            case "release" -> back(ra, target, "Poste libéré", () -> licenses.releaseSeat(a, licenseId, seat, reason));
            default -> throw ApiException.notFound("Action inconnue");
        };
    }

    // ------------------------------------------------------------------ issuing

    @GetMapping("/admin/licenses/issue")
    public String issueForm(Authentication auth, @RequestParam(required = false) String licenseId, Model m) {
        read(auth, Role.Permission.REISSUE, m);
        m.addAttribute("licenseId", licenseId);
        m.addAttribute("format", activations.format());
        m.addAttribute("keyLoaded", keyring.present());
        m.addAttribute("defaultWindow", props.windowDays());
        m.addAttribute("sub", "issue");
        return "admin/lic-issue";
    }

    @PostMapping("/admin/licenses/issue")
    public String issue(Authentication auth, @RequestParam String licenseId, @RequestParam(defaultValue = "tv") String subject, @RequestParam String deviceRequest,
                        @RequestParam(required = false) Integer windowDays, @RequestParam(required = false) String reissue, Model m) {
        Actor a = read(auth, Role.Permission.REISSUE, m);
        m.addAttribute("licenseId", licenseId);
        m.addAttribute("subject", subject);
        m.addAttribute("deviceRequest", deviceRequest);
        m.addAttribute("format", activations.format());
        m.addAttribute("keyLoaded", keyring.present());
        m.addAttribute("defaultWindow", props.windowDays());
        m.addAttribute("sub", "issue");
        try {
            var act = "on".equals(reissue) ? activations.reissue(a, Validate.licenseId(licenseId), subject, deviceRequest, "server-web")
                    : activations.issue(a, new ActivationService.IssueRequest(licenseId, subject, deviceRequest, null, null, windowDays), "server-web");
            m.addAttribute("act", act);
            m.addAttribute("qr", QrSvg.dataUri(act.text()));
        } catch (ApiException e) {
            denied(e);
            m.addAttribute("error", e.getMessage());
        }
        return "admin/lic-issue";
    }

    /** The activation as a file named "activation" (to drop in Download/CastBridge/ of the USB key): re-derived from the seat, never stored. */
    @GetMapping("/admin/licenses/{licenseId}/seats/{seat}/activation")
    public ResponseEntity<byte[]> activationFile(Authentication auth, @PathVariable String licenseId, @PathVariable String seat) {
        var act = activations.reissueSeat(actor(auth), licenseId, seat, "server-web");
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"activation\"")
                .body((act.text() + "\n").getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/admin/licenses/device")
    public String device(Authentication auth, @RequestParam(required = false) String code, Model m) {
        read(auth, Role.Permission.LICENSE_READ, m);
        m.addAttribute("code", code);
        if (code != null && !code.isBlank()) {
            try {
                m.addAttribute("found", licenses.byDeviceCode(code));
            } catch (ApiException e) {
                m.addAttribute("error", e.getMessage());
            }
        }
        m.addAttribute("sub", "device");
        return "admin/lic-device";
    }

    // ------------------------------------------------------------------ clients

    @GetMapping("/admin/licenses/clients")
    public String clientList(Authentication auth, @RequestParam(required = false) String q, @RequestParam(required = false) Integer page, Model m) {
        read(auth, Role.Permission.LICENSE_READ, m);
        m.addAttribute("page", clients.list(q, Validate.page(page), 50));
        m.addAttribute("q", q);
        m.addAttribute("sub", "clients");
        return "admin/lic-clients";
    }

    @PostMapping("/admin/licenses/clients")
    public String clientCreate(Authentication auth, @RequestParam String name, @RequestParam(required = false) String contact, @RequestParam(required = false) String notes,
                               RedirectAttributes ra) {
        return back(ra, "/admin/licenses/clients", "Client créé", () -> clients.create(actor(auth), name, contact, notes));
    }

    @GetMapping("/admin/licenses/clients/{id}")
    public String clientDetail(Authentication auth, @PathVariable long id, Model m) {
        read(auth, Role.Permission.LICENSE_READ, m);
        m.addAttribute("c", clients.get(id));
        m.addAttribute("lics", licenses.list(new LicenseService.Filter(null, null, id, null, null, "created", true), 0, 100).items());
        m.addAttribute("sub", "clients");
        return "admin/lic-client";
    }

    @PostMapping("/admin/licenses/clients/{id}")
    public String clientUpdate(Authentication auth, @PathVariable long id, @RequestParam String name, @RequestParam(required = false) String contact,
                               @RequestParam(required = false) String notes, RedirectAttributes ra) {
        return back(ra, "/admin/licenses/clients/" + id, "Fiche client enregistrée", () -> clients.update(actor(auth), id, name, contact, notes));
    }

    @GetMapping("/admin/licenses/clients/{id}/export.json")
    public ResponseEntity<Map<String, Object>> clientExport(Authentication auth, @PathVariable long id) {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"client-" + id + ".json\"").body(clients.export(actor(auth), id));
    }

    @GetMapping("/admin/licenses/clients/{id}/erase")
    public String eraseConfirm(Authentication auth, @PathVariable long id, Model m) {
        read(auth, Role.Permission.LICENSE_READ, m);
        m.addAttribute("c", clients.get(id));
        m.addAttribute("sub", "clients");
        return "admin/lic-erase";
    }

    @PostMapping("/admin/licenses/clients/{id}/erase")
    public String erase(Authentication auth, @PathVariable long id, @RequestParam(required = false) String reason, @RequestParam(required = false) String confirm, RedirectAttributes ra) {
        try {
            confirmed(confirm);
        } catch (ApiException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/licenses/clients/" + id + "/erase";
        }
        return back(ra, "/admin/licenses/clients/" + id, "Données personnelles effacées ; les postes sont anonymisés (le décompte reste exact)", () -> clients.erase(actor(auth), id, reason));
    }

    // ------------------------------------------------------------------ catalogue

    @GetMapping("/admin/licenses/products")
    public String productList(Authentication auth, Model m) {
        read(auth, Role.Permission.LICENSE_READ, m);
        m.addAttribute("items", products.list());
        m.addAttribute("sub", "products");
        return "admin/lic-products";
    }

    @PostMapping("/admin/licenses/products")
    public String productCreate(Authentication auth, @RequestParam String productId, @RequestParam String title, @RequestParam String kind, @RequestParam(required = false) Integer durationDays,
                                @RequestParam(required = false) String lots, @RequestParam(required = false) String bundles, RedirectAttributes ra) {
        List<String> lotList = lots == null ? List.of() : java.util.Arrays.stream(lots.split("[\\s,;]+")).filter(s -> !s.isBlank()).toList();
        List<String> bundleList = bundles == null ? List.of() : java.util.Arrays.stream(bundles.split("[\\s,;]+")).filter(s -> !s.isBlank()).toList();
        return back(ra, "/admin/licenses/products", "Produit créé", () -> products.create(actor(auth), new ProductService.NewProduct(productId, title, kind, durationDays, lotList, null, null, bundleList)));
    }

    @PostMapping("/admin/licenses/products/{productId}/active")
    public String productActive(Authentication auth, @PathVariable String productId, @RequestParam boolean active, @RequestParam(required = false) String reason, RedirectAttributes ra) {
        return back(ra, "/admin/licenses/products", active ? "Bouquet activé" : "Bouquet désactivé", () -> products.update(actor(auth), productId, null, null, active, reason));
    }

    // ------------------------------------------------------------------ audit

    @GetMapping("/admin/licenses/audit")
    public String auditPage(Authentication auth, @RequestParam(required = false) String actor, @RequestParam(required = false) String action, @RequestParam(required = false) String targetType,
                            @RequestParam(required = false) String targetId, @RequestParam(required = false) String from, @RequestParam(required = false) String to,
                            @RequestParam(required = false) Integer page, Model m) {
        read(auth, Role.Permission.AUDIT_READ, m);
        var f = new AuditLog.Filter(actor, action, targetType, targetId, Validate.instant(from, "Du", false), Validate.instant(to, "Au", true));
        m.addAttribute("page", audit.search(f, Validate.page(page), 50));
        m.addAttribute("fa", Map.of("actor", actor == null ? "" : actor, "action", action == null ? "" : action, "targetType", targetType == null ? "" : targetType,
                "targetId", targetId == null ? "" : targetId, "from", from == null ? "" : from, "to", to == null ? "" : to));
        m.addAttribute("head", audit.headHash());
        m.addAttribute("targetTypes", List.of("LICENSE", "CLIENT", "PRODUCT", "ACCOUNT", "LEDGER"));
        m.addAttribute("sub", "audit");
        return "admin/lic-audit";
    }

    @PostMapping("/admin/licenses/audit/verify")
    public String auditVerify(Authentication auth, RedirectAttributes ra) {
        actor(auth).require(Role.Permission.AUDIT_READ, props.requireTotp());
        AuditLog.Verification v = audit.verify();
        if (v.ok()) ra.addFlashAttribute("ok", "Journal intact : " + v.rows() + " ligne(s) vérifiée(s). Empreinte de tête : " + v.headHash());
        else ra.addFlashAttribute("error", "JOURNAL ALTÉRÉ : " + v.problem());
        return "redirect:/admin/licenses/audit";
    }

    // ------------------------------------------------------------------ registry

    @GetMapping("/admin/licenses/registry")
    public String registry(Authentication auth, Model m) {
        read(auth, Role.Permission.LICENSE_READ, m);
        m.addAttribute("conflicts", ledger.conflicts("OPEN", 0, 50).items());
        m.addAttribute("imports", ledger.imports(20));
        m.addAttribute("keyLoaded", keyring.present());
        m.addAttribute("sub", "registry");
        return "admin/lic-registry";
    }

    @GetMapping("/admin/licenses/registry/export")
    public ResponseEntity<byte[]> registryExport(Authentication auth) {
        byte[] b = ledger.export(actor(auth));
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"registre-" + Instant.now().toString().substring(0, 10) + ".json\"").body(b);
    }

    @PostMapping("/admin/licenses/registry/import")
    public String registryImport(Authentication auth, @RequestParam("file") MultipartFile file, @RequestParam(required = false) String dryRun,
                                 @RequestParam(required = false) String auto, Model m) {
        Actor a = read(auth, Role.Permission.LICENSE_READ, m);
        m.addAttribute("sub", "registry");
        m.addAttribute("keyLoaded", keyring.present());
        try {
            if (file.isEmpty()) throw ApiException.badRequest("Choisissez un fichier de registre");
            if (file.getSize() > props.maxImportBytes()) throw ApiException.badRequest("Fichier trop volumineux");
            var report = ledger.importLedger(a, file.getBytes(), "on".equals(dryRun), "on".equals(auto));
            m.addAttribute("report", report);
        } catch (ApiException e) {
            denied(e);
            m.addAttribute("error", e.getMessage());
            m.addAttribute("details", e.details());
        } catch (IOException e) {
            m.addAttribute("error", "Lecture du fichier impossible");
        }
        m.addAttribute("conflicts", ledger.conflicts("OPEN", 0, 50).items());
        m.addAttribute("imports", ledger.imports(20));
        return "admin/lic-registry";
    }

    @PostMapping("/admin/licenses/registry/conflicts/{id}")
    public String decide(Authentication auth, @PathVariable long id, @RequestParam String decision, @RequestParam(required = false) String reason, RedirectAttributes ra) {
        boolean accept = "accept".equals(decision);
        return back(ra, "/admin/licenses/registry", accept ? "Conflit accepté : l'entrée a été appliquée" : "Conflit rejeté", () -> ledger.decide(actor(auth), id, accept, reason));
    }

    // ------------------------------------------------------------------ security

    @GetMapping("/admin/licenses/security")
    public String security(Authentication auth, Model m) {
        Actor a = read(auth, Role.Permission.LICENSE_READ, m);
        m.addAttribute("accountList", a.role() == Role.OWNER ? accounts.list() : List.of());
        m.addAttribute("totpOn", accounts.totpEnabled(a.name()));
        m.addAttribute("vault", vault.available());
        m.addAttribute("roles", Role.values());
        m.addAttribute("requireTotp", props.requireTotp());
        m.addAttribute("sub", "security");
        return "admin/lic-security";
    }

    /** Starts the TOTP enrolment: the secret and its QR are shown once, in the response (never in a URL or a flash). */
    @PostMapping("/admin/licenses/security/totp/start")
    public String totpStart(Authentication auth, Model m) {
        Actor a = read(auth, Role.Permission.LICENSE_READ, m);
        m.addAttribute("sub", "security");
        m.addAttribute("accountList", a.role() == Role.OWNER ? accounts.list() : List.of());
        m.addAttribute("totpOn", false);
        m.addAttribute("vault", vault.available());
        m.addAttribute("roles", Role.values());
        m.addAttribute("requireTotp", props.requireTotp());
        try {
            var e = accounts.startEnrollment(a);
            m.addAttribute("enroll", e);
            m.addAttribute("qr", QrSvg.dataUri(e.uri()));
        } catch (ApiException e) {
            if (e.status() != org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE) denied(e);
            m.addAttribute("error", e.getMessage());
        }
        return "admin/lic-security";
    }

    @PostMapping("/admin/licenses/security/totp/confirm")
    public String totpConfirm(Authentication auth, @RequestParam String code, RedirectAttributes ra) {
        return back(ra, "/admin/licenses/security", "Double authentification activée : elle sera demandée à chaque connexion", () -> accounts.confirmEnrollment(actor(auth), code));
    }

    @PostMapping("/admin/licenses/security/accounts")
    public String accountCreate(Authentication auth, @RequestParam String username, @RequestParam String password, @RequestParam String role, RedirectAttributes ra) {
        return back(ra, "/admin/licenses/security", "Compte créé", () -> accounts.create(actor(auth), username, password, role));
    }

    @PostMapping("/admin/licenses/security/accounts/{username}/role")
    public String accountRole(Authentication auth, @PathVariable String username, @RequestParam String role, @RequestParam(required = false) String reason, RedirectAttributes ra) {
        return back(ra, "/admin/licenses/security", "Rôle modifié", () -> accounts.setRole(actor(auth), username, role, reason));
    }

    @PostMapping("/admin/licenses/security/accounts/{username}/totp-off")
    public String accountTotpOff(Authentication auth, @PathVariable String username, @RequestParam(required = false) String reason, RedirectAttributes ra) {
        return back(ra, "/admin/licenses/security", "Double authentification retirée du compte", () -> accounts.disableTotp(actor(auth), username, reason));
    }
}
