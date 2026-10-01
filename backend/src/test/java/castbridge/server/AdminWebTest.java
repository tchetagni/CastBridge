package castbridge.server;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

class AdminWebTest extends ApiTestBase {
    @Autowired JdbcTemplate jdbc;

    private static RequestPostProcessor admin() {
        return user("esaie").roles("WEBADMIN");
    }

    @Test
    void loginFormBcryptAndLockout() throws Exception {
        mvc.perform(get("/admin")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("**/admin/login"));
        mvc.perform(get("/admin/login")).andExpect(status().isOk()).andExpect(content().string(containsString("Se connecter")))
                .andExpect(header().string("Content-Security-Policy", containsString("script-src 'self'")));
        String hash = jdbc.queryForObject("select password_hash from admin_user where username = 'esaie'", String.class);
        assertEquals("$2a$12$", hash.substring(0, 7), "BCrypt, cost 12");

        mvc.perform(formLogin("/admin/login").user("esaie").password("un-mot-de-passe-de-test")).andExpect(redirectedUrl("/admin"));
        // without CSRF token a login (or any POST) is refused
        mvc.perform(post("/admin/login").param("username", "esaie").param("password", "un-mot-de-passe-de-test")).andExpect(status().isForbidden());

        for (int i = 0; i < 5; i++) {
            mvc.perform(formLogin("/admin/login").user("esaie").password("mauvais")).andExpect(redirectedUrl("/admin/login?erreur"));
        }
        // locked: even the right password is refused for 15 minutes
        mvc.perform(formLogin("/admin/login").user("esaie").password("un-mot-de-passe-de-test")).andExpect(redirectedUrl("/admin/login?erreur"));
        jdbc.update("update admin_user set locked_until = null");
        mvc.perform(formLogin("/admin/login").user("esaie").password("un-mot-de-passe-de-test")).andExpect(redirectedUrl("/admin"));
    }

    @Test
    void pagesRenderWithData() throws Exception {
        // one device, one release, so the pages show real rows
        JsonNode reg = body(mvc.perform(post("/api/v1/devices/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"installId\":\"" + UUID.randomUUID() + "\",\"app\":\"tv\",\"versionCode\":7,\"versionName\":\"0.5\","
                        + "\"platform\":\"fire-os\",\"manufacturer\":\"Amazon\",\"model\":\"AFTSS\",\"storageFreeMb\":1000,\"storageTotalMb\":8000}"))
                .andExpect(status().isCreated()).andReturn());
        String id = reg.get("deviceId").asText();
        mvc.perform(post("/api/v1/devices/crash").header("Authorization", "Bearer " + reg.get("deviceToken").asText())
                .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"Plantage <script>alert(1)</script>\"}")).andExpect(status().isNoContent());

        mvc.perform(get("/admin").with(admin())).andExpect(status().isOk()).andExpect(content().string(containsString("Tableau de bord")))
                .andExpect(content().string(containsString("Fire OS")))
                .andExpect(content().string(containsString("Plantage &lt;script&gt;")));
        mvc.perform(get("/admin/devices").param("platform", "fire-os").with(admin())).andExpect(status().isOk())
                .andExpect(content().string(containsString("AFTSS")));
        mvc.perform(get("/admin/devices/" + id).with(admin())).andExpect(status().isOk())
                .andExpect(content().string(containsString("Contacts par jour")));
        mvc.perform(get("/admin/devices/00000000-0000-0000-0000-000000000000").with(admin())).andExpect(status().isNotFound())
                .andExpect(content().string(containsString("Appareil introuvable")));
        mvc.perform(get("/admin/releases").with(admin())).andExpect(status().isOk()).andExpect(content().string(containsString("Publier un APK")));
        mvc.perform(get("/admin/quiz").with(admin())).andExpect(status().isOk()).andExpect(content().string(containsString("Banque de questions")))
                .andExpect(content().string(containsString("Parties sans répétition"))).andExpect(content().string(containsString("insuffisante")));
        mvc.perform(get("/admin/quiz").param("status", "reviewed").param("track", "higher").with(admin())).andExpect(status().isOk());
        mvc.perform(get("/admin/quiz/export").param("format", "csv").param("status", "all").with(admin())).andExpect(status().isOk())
                .andExpect(content().string(containsString("uuid;lang;track")));

        // actions: CSRF required
        mvc.perform(post("/admin/devices/" + id + "/edit").param("label", "Salon").with(admin())).andExpect(status().isForbidden());
        mvc.perform(post("/admin/devices/" + id + "/edit").param("label", "Salon").param("group", "Famille").with(admin()).with(csrf()))
                .andExpect(redirectedUrl("/admin/devices/" + id)).andExpect(flash().attribute("ok", "Fiche enregistrée"));
        mvc.perform(post("/admin/devices/" + id + "/block").param("blocked", "true").with(admin()).with(csrf())).andExpect(status().is3xxRedirection());
        mvc.perform(post("/admin/devices/" + id + "/check-update").with(admin()).with(csrf())).andExpect(status().is3xxRedirection());
        mvc.perform(post("/admin/devices/" + id + "/channel").param("channel", "beta").with(admin()).with(csrf())).andExpect(status().is3xxRedirection());
        assertEquals("beta", jdbc.queryForObject("select channel_override from device where public_id = ?", String.class, id));

        // publish from the web form, then quiz review and import
        mvc.perform(multipart("/admin/releases").file(new MockMultipartFile("file", "tv.apk", "application/octet-stream", testApk()))
                        .param("app", "tv").param("abi", "armeabi-v7a").param("versionCode", "42").param("versionName", "0.6")
                        .param("channel", "stable").with(admin()).with(csrf()))
                .andExpect(redirectedUrl("/admin/releases")).andExpect(flash().attribute("ok", containsString("publiée")));
        mvc.perform(post("/admin/quiz/cm-geo-001/status").param("value", "draft").param("back", "?status=reviewed").with(admin()).with(csrf()))
                .andExpect(redirectedUrl("/admin/quiz?status=reviewed"));
        mvc.perform(post("/admin/quiz/cm-geo-001/status").param("value", "reviewed").param("back", "//evil.example").with(admin()).with(csrf()))
                .andExpect(redirectedUrl("/admin/quiz"));
        mvc.perform(multipart("/admin/quiz/import").file(new MockMultipartFile("file", "q.json", "application/json",
                        "[{\"uuid\":\"web-1\",\"region\":\"AF\",\"category\":\"Géo\",\"difficulty\":2,\"question\":\"Capitale du Sénégal ?\",\"choices\":[\"Dakar\",\"Thiès\",\"Saint-Louis\",\"Ziguinchor\"],\"answer\":0,\"explanation\":\"Dakar.\",\"source\":\"Atlas\"}]".getBytes()))
                        .with(admin()).with(csrf()))
                .andExpect(flash().attribute("ok", containsString("1 nouvelles")));

        mvc.perform(post("/admin/logout").with(admin()).with(csrf())).andExpect(redirectedUrl("/admin/login?deconnexion"));
        mvc.perform(get("/admin/assets/admin.css")).andExpect(status().isOk());
    }

    @Test
    void theApiTokenDoesNotOpenTheWebAndViceVersa() throws Exception {
        mvc.perform(get("/admin").header("Authorization", ADMIN)).andExpect(status().is3xxRedirection());
        mvc.perform(get("/api/v1/admin/releases").with(admin())).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/releases").with(SecurityMockMvcRequestPostProcessors.httpBasic("esaie", "un-mot-de-passe-de-test")))
                .andExpect(status().isUnauthorized());
    }
}
