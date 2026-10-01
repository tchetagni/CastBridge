package castbridge.server.licenses;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** The pages, the CSP / CSRF rules and the role matrix (enforced on the server, whatever the page shows). */
class LicenseWebTest extends LicenseTestBase {
    // boss = OWNER with TOTP ; esaie = the initial account: OWNER WITHOUT TOTP ; plus a support and a read-only account
    static final String BOSS = "boss", WEAK = "esaie", SUPPORT_USER = "supporter", READER = "reader";

    @BeforeEach
    void accountsExist() {
        if (accounts.roleOf(BOSS) == null) {
            accounts.create(OWNER, BOSS, "un-mot-de-passe-solide-1", "OWNER");
            jdbc.update("update admin_user set totp_enabled = TRUE where username = ?", BOSS);
            accounts.create(OWNER, SUPPORT_USER, "un-mot-de-passe-solide-2", "SUPPORT");
            accounts.create(OWNER, READER, "un-mot-de-passe-solide-3", "READONLY");
        }
    }

    static RequestPostProcessor as(String name) { return user(name).roles("WEBADMIN"); }

    private int call(String as, HttpMethod m, String path, String... params) throws Exception {
        MockHttpServletRequestBuilder b = m == HttpMethod.GET ? get(path) : post(path).with(csrf());
        for (int i = 0; i + 1 < params.length; i += 2) b.param(params[i], params[i + 1]);
        return mvc.perform(b.with(as(as))).andReturn().getResponse().getStatus();
    }

    /** one matrix row: the expected status for boss, esaie (owner without TOTP), support, read-only */
    private void row(String label, int boss, int weak, int support, int reader, Function<String, Integer> run) {
        assertThat(run.apply(BOSS)).as(label + " / propriétaire avec TOTP").isEqualTo(boss);
        assertThat(run.apply(WEAK)).as(label + " / propriétaire sans TOTP").isEqualTo(weak);
        assertThat(run.apply(SUPPORT_USER)).as(label + " / support").isEqualTo(support);
        assertThat(run.apply(READER)).as(label + " / lecture seule").isEqualTo(reader);
    }

    private Function<String, Integer> c(HttpMethod m, String path, String... params) {
        return who -> {
            try {
                return call(who, m, path, params);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        };
    }

    @Test
    void roleMatrixIsEnforcedOnTheServer() throws Exception {
        var l = license(5);
        Dev dv = dev();
        var act = issue(l.licenseId(), dv);
        String dev = dv.code(), seat = act.seatId();
        String base = "/admin/licenses";
        // reads
        row("tableau de bord", 200, 200, 200, 200, c(HttpMethod.GET, base));
        row("liste", 200, 200, 200, 200, c(HttpMethod.GET, base + "/list"));
        row("fiche", 200, 200, 200, 200, c(HttpMethod.GET, base + "/" + l.licenseId()));
        row("recherche par appareil", 200, 200, 200, 200, c(HttpMethod.GET, base + "/device", "code", dev));
        row("CSV", 200, 200, 200, 200, c(HttpMethod.GET, base + "/export.csv"));
        row("clients", 200, 200, 200, 200, c(HttpMethod.GET, base + "/clients"));
        row("bouquets", 200, 200, 200, 200, c(HttpMethod.GET, base + "/products"));
        row("registre", 200, 200, 200, 200, c(HttpMethod.GET, base + "/registry"));
        row("sécurité", 200, 200, 200, 200, c(HttpMethod.GET, base + "/security"));
        row("audit", 200, 200, 200, 403, c(HttpMethod.GET, base + "/audit"));
        row("vérifier l'audit", 302, 302, 302, 403, c(HttpMethod.POST, base + "/audit/verify"));
        // issuing
        row("formulaire d'émission", 200, 200, 200, 403, c(HttpMethod.GET, base + "/issue"));
        row("réémission (poste existant)", 200, 200, 200, 403, c(HttpMethod.POST, base + "/issue", "licenseId", l.licenseId(), "deviceRequest", dv.text(), "reissue", "on"));
        row("activation en fichier", 200, 200, 200, 403, c(HttpMethod.GET, base + "/" + l.licenseId() + "/seats/" + seat + "/activation"));
        row("émission pour un NOUVEL appareil", 200, 403, 403, 403, who -> {
            try {
                return call(who, HttpMethod.POST, base + "/issue", "licenseId", license(2).licenseId(), "deviceRequest", dev().text());
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        // writes: owner with TOTP only
        row("nouveau client", 302, 403, 403, 403, c(HttpMethod.POST, base + "/clients", "name", "Client matrice"));
        row("nouveau bouquet", 302, 403, 403, 403, who -> {
            try {
                return call(who, HttpMethod.POST, base + "/products", "productId", "mx-" + who + "-" + Long.toHexString(RND.nextLong() & 0xffffffL), "title", "T", "kind", "A_LA_CARTE");
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        row("prolonger", 302, 403, 403, 403, c(HttpMethod.POST, base + "/" + l.licenseId() + "/extend", "endAt", "2099-01-01"));
        row("changer les postes", 302, 403, 403, 403, c(HttpMethod.POST, base + "/" + l.licenseId() + "/seats", "seats", "6"));
        row("suspendre", 302, 403, 403, 403, who -> {
            try {
                return call(who, HttpMethod.POST, base + "/" + license(1).licenseId() + "/do/suspend", "reason", "motif de test", "confirm", "on");
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        row("révoquer", 302, 403, 403, 403, who -> {
            try {
                return call(who, HttpMethod.POST, base + "/" + license(1).licenseId() + "/do/revoke", "reason", "motif de test", "confirm", "on");
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        row("libérer un poste", 302, 403, 403, 403, who -> {
            try {
                var x = license(1);
                var a = issue(x.licenseId(), dev());
                return call(who, HttpMethod.POST, base + "/" + x.licenseId() + "/do/release", "reason", "motif de test", "confirm", "on", "seat", a.seatId());
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        row("export du registre", 200, 403, 403, 403, c(HttpMethod.GET, base + "/registry/export"));
        row("export RGPD", 200, 403, 403, 403, c(HttpMethod.GET, base + "/clients/" + l.clientId() + "/export.json"));
        row("effacement RGPD", 302, 403, 403, 403, who -> {
            try {
                return call(who, HttpMethod.POST, base + "/clients/" + client().id() + "/erase", "reason", "demande du client", "confirm", "on");
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        row("créer un compte", 302, 403, 403, 403, who -> {
            try {
                return call(who, HttpMethod.POST, base + "/security/accounts", "username", "nouveau-" + who, "password", "un-mot-de-passe-solide-9", "role", "READONLY");
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        // the import itself (multipart): only the owner with TOTP gets as far as parsing
        for (String who : List.of(WEAK, SUPPORT_USER, READER)) {
            assertThat(mvc.perform(multipart(base + "/registry/import").file(new MockMultipartFile("file", "r.json", "application/json", "{}".getBytes())).with(csrf()).with(as(who)))
                    .andReturn().getResponse().getStatus()).as("import " + who).isEqualTo(403);
        }
        // a refused role never changed anything
        assertThat(licenses.get(l.licenseId()).seatsAllowed()).isEqualTo(6); // only the boss's change went through
    }

    @Test
    void anonymousAndCsrfAreRefused() throws Exception {
        mvc.perform(get("/admin/licenses")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("http://localhost/admin/login"));
        mvc.perform(post("/admin/licenses/clients").param("name", "x").with(as(BOSS))).andExpect(status().isForbidden()); // no CSRF token
        // the admin bearer token is for /api only: it opens nothing on the web pages
        mvc.perform(get("/admin/licenses").header("Authorization", ADMIN)).andExpect(status().is3xxRedirection());
        // an account that does not exist in the database has no role at all
        assertThat(call("fantome", HttpMethod.GET, "/admin/licenses")).isEqualTo(403);
    }

    @Test
    void pagesKeepTheStrictContentSecurityPolicyAndHaveNoInlineScriptOrStyle() throws Exception {
        var l = license(2);
        issue(l.licenseId(), dev());
        licenses.suspend(OWNER, l.licenseId(), "pour voir la page");
        String base = "/admin/licenses";
        List<String> pages = List.of(base, base + "/list", base + "/new", base + "/" + l.licenseId(), base + "/" + l.licenseId() + "/confirm/revoke", base + "/issue", base + "/device?code=" + dev().code(),
                base + "/clients", base + "/clients/" + l.clientId(), base + "/clients/" + l.clientId() + "/erase", base + "/products", base + "/audit", base + "/registry", base + "/security");
        for (String p : pages) {
            MvcResult r = mvc.perform(get(p).with(as(BOSS))).andExpect(status().isOk()).andExpect(header().string("Content-Security-Policy", org.hamcrest.Matchers.containsString("script-src 'self'")))
                    .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store"))).andReturn();
            String html = r.getResponse().getContentAsString();
            assertThat(html).as(p).doesNotContain(" style=\"").doesNotContain("onclick=").doesNotContain("onsubmit=").doesNotContain("javascript:");
            assertThat(html.replaceAll("<script[^>]* src=[^>]*></script>", "")).as(p + " : script en ligne").doesNotContain("<script");
            assertThat(html).as(p + " : meta viewport (téléphone)").contains("name=\"viewport\"").contains("lang=\"fr\"");
        }
    }

    @Test
    void userInputIsEscapedInPages() throws Exception {
        var c = clients.create(OWNER, "<img src=x onerror=alert(1)>", "<script>alert(2)</script>", "\"><svg onload=alert(3)>");
        var l = licenses.create(OWNER, new LicenseService.NewLicense(null, c.id(), "PAID", 1, null, null, null, null, null));
        for (String p : List.of("/admin/licenses/list", "/admin/licenses/" + l.licenseId(), "/admin/licenses/clients", "/admin/licenses/clients/" + c.id())) {
            String html = mvc.perform(get(p).with(as(BOSS))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(html).as(p).doesNotContain("<img src=x").doesNotContain("<script>alert").doesNotContain("<svg onload");
        }
    }

    @Test
    void destructiveActionNeedsConfirmationAndReason() throws Exception {
        var l = license(1);
        String base = "/admin/licenses/" + l.licenseId();
        // without the tick or without a reason, nothing happens
        mvc.perform(post(base + "/do/revoke").with(csrf()).with(as(BOSS)).param("reason", "motif valable")).andExpect(status().is3xxRedirection());
        assertThat(licenses.get(l.licenseId()).state()).isEqualTo("ACTIVE");
        mvc.perform(post(base + "/do/revoke").with(csrf()).with(as(BOSS)).param("confirm", "on").param("reason", " ")).andExpect(status().is3xxRedirection());
        assertThat(licenses.get(l.licenseId()).state()).isEqualTo("ACTIVE");
        // the confirmation page tells what will happen
        String page = mvc.perform(get(base + "/confirm/revoke").with(as(BOSS))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(page).contains("RÉVOQUER").contains("name=\"reason\"").contains("required");
        mvc.perform(post(base + "/do/revoke").with(csrf()).with(as(BOSS)).param("confirm", "on").param("reason", "demande du propriétaire")).andExpect(status().is3xxRedirection());
        assertThat(licenses.get(l.licenseId()).state()).isEqualTo("REVOKED");
    }

    @Test
    void issueFormShowsTheActivationQrAndTheProvisionalFormatWarning() throws Exception {
        var l = license(1);
        Dev d = dev();
        String html = mvc.perform(post("/admin/licenses/issue").with(csrf()).with(as(BOSS)).param("licenseId", l.licenseId()).param("deviceRequest", d.text()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("cba1.").contains("data:image/svg+xml;base64,").contains("ACTIVATION-FORMAT").contains("Télécharger").contains("(nouveau)");
        // invalid or altered device request: a clear message, nothing consumed
        String bad = mvc.perform(post("/admin/licenses/issue").with(csrf()).with(as(BOSS)).param("licenseId", l.licenseId()).param("deviceRequest", "code=12345"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(bad).contains("Demande d&#39;appareil").doesNotContain("cba1.");
        String codeOnly = mvc.perform(post("/admin/licenses/issue").with(csrf()).with(as(BOSS)).param("licenseId", l.licenseId()).param("deviceRequest", "code=" + dev().code()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(codeOnly).contains("sans facteur").doesNotContain("cba1.");
        assertThat(licenses.get(l.licenseId()).seatsUsed()).isEqualTo(1);
        String seat = licenses.detail(l.licenseId()).seats().get(0).seatId();
        String file = mvc.perform(get("/admin/licenses/" + l.licenseId() + "/seats/" + seat + "/activation").with(as(SUPPORT_USER))).andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("activation"))).andReturn().getResponse().getContentAsString();
        assertThat(file).startsWith("cba1.");
    }

    @Test
    void csvExportNeutralizesSpreadsheetFormulas() throws Exception {
        var c = clients.create(OWNER, "=HYPERLINK(\"http://evil\")", null, null);
        licenses.create(OWNER, new LicenseService.NewLicense(null, c.id(), "PAID", 1, null, null, null, null, null));
        String csv = mvc.perform(get("/admin/licenses/export.csv").with(as(READER))).andExpect(status().isOk()).andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("text/csv")))
                .andReturn().getResponse().getContentAsString();
        assertThat(csv).startsWith("licence;client;type;etat;").contains("'=HYPERLINK").doesNotContain(";=HYPERLINK");
    }
}
