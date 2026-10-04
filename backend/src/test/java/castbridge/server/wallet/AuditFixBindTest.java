package castbridge.server.wallet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

/**
 * M5 de l'audit : une {@code cbx1} COPIÉE ne doit pas lier une identité au premier appareil venu. La TV prouve la possession d'une clé d'installation (Ed25519) en signant
 * {@code castbridge-wallet-bind-v1 \n code \n identifiant public de l'appareil API \n heure}, à ±5 minutes du serveur ; la liaison retient la clé ; l'administrateur (TOTP) peut réaffecter.
 */
@org.springframework.test.context.TestPropertySource(properties = "castbridge.wallet.require-bind-proof=true")
class AuditFixBindTest extends WalletTestBase {
    static final Instant T0 = Instant.parse("2026-10-04T09:00:00Z");
    static final long NOW = T0.toEpochMilli();

    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void freeze() { clock.freezeAt(T0); }

    private static String bindJson(KeyPair key, String code, String publicId, long at) throws Exception {
        Signature s = Signature.getInstance("Ed25519");
        s.initSign(key.getPrivate());
        s.update(("castbridge-wallet-bind-v1\n" + code + "\n" + publicId + "\n" + at).getBytes(StandardCharsets.UTF_8));
        return "{\"key\":\"" + rawPublic(key) + "\",\"at\":" + at + ",\"sig\":\"" + Base64.getEncoder().encodeToString(s.sign()) + "\"}";
    }

    private MvcResult sync(Registered dev, Acts.Tv tv, String bind, String... activations) throws Exception {
        ObjectNode b = json.createObjectNode().put("deviceCode", tv.code());
        ArrayNode a = b.putArray("activations");
        for (String s : activations) a.add(s);
        if (bind != null) b.set("bind", json.readTree(bind));
        return mvc.perform(post("/api/v1/wallet/sync").header("Authorization", dev.auth()).contentType(MediaType.APPLICATION_JSON).content(b.toString())).andReturn();
    }

    @Test
    void p9_aCopiedActivationWithoutProofOfPossessionBindsNothing() throws Exception {
        Registered attacker = registerApp("tv");
        Acts.Tv tv = Acts.Tv.random();
        String copied = Acts.trialDays(ISSUER, tv, NOW, 30);
        MvcResult r = sync(attacker, tv, null, copied);
        assertEquals(409, r.getResponse().getStatus(), r.getResponse().getContentAsString());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM wallet_identity WHERE holder = ?", Long.class, tv.code()), "aucune liaison sans preuve");
    }

    @Test
    void p9_theProofBindsTheDeviceAndItsKeyAndTheCopyIsRefusedAfterwards() throws Exception {
        Registered tvDev = registerApp("tv"), attacker = registerApp("tv");
        Acts.Tv tv = Acts.Tv.random();
        String act = Acts.trialDays(ISSUER, tv, NOW, 30);
        KeyPair install = pair();
        assertEquals(200, sync(tvDev, tv, bindJson(install, tv.code(), tvDev.publicId(), NOW), act).getResponse().getStatus());
        assertNotNull(jdbc.queryForObject("SELECT install_pub FROM wallet_identity WHERE holder = ?", String.class, tv.code()));
        // même appareil, contact suivant sans preuve : refusé (aucune tranche, aucune écriture)
        assertEquals(409, sync(tvDev, tv, null, act).getResponse().getStatus());
        // le copieur, avec sa propre clé : l'identité reste liée à la vraie TV
        MvcResult r = sync(attacker, tv, bindJson(pair(), tv.code(), attacker.publicId(), NOW), act);
        long bound = jdbc.queryForObject("SELECT api_device_id FROM wallet_identity WHERE holder = ?", Long.class, tv.code());
        assertEquals(jdbc.queryForObject("SELECT id FROM device WHERE public_id = ?", Long.class, tvDev.publicId()), bound, "liée à la vraie TV : " + r.getResponse().getContentAsString());
    }

    @Test
    void p9_aProofIsBoundToTheDeviceTheCodeAndTheTime() throws Exception {
        Registered tvDev = registerApp("tv"), other = registerApp("tv");
        Acts.Tv tv = Acts.Tv.random();
        String act = Acts.trialDays(ISSUER, tv, NOW, 30);
        KeyPair install = pair();
        // preuve rejouée depuis un autre appareil API : refusée
        assertEquals(409, sync(other, tv, bindJson(install, tv.code(), tvDev.publicId(), NOW), act).getResponse().getStatus());
        // preuve pour un autre code : refusée
        assertEquals(409, sync(tvDev, tv, bindJson(install, Acts.Tv.random().code(), tvDev.publicId(), NOW), act).getResponse().getStatus());
        // preuve périmée (plus de 5 minutes) : refusée
        assertEquals(409, sync(tvDev, tv, bindJson(install, tv.code(), tvDev.publicId(), NOW - 6 * 60_000L), act).getResponse().getStatus());
        // signature altérée : refusée
        String good = bindJson(install, tv.code(), tvDev.publicId(), NOW);
        assertEquals(409, sync(tvDev, tv, good.replace("\"at\":" + NOW, "\"at\":" + (NOW + 1)), act).getResponse().getStatus());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM wallet_identity WHERE holder = ?", Long.class, tv.code()));
        assertEquals(200, sync(tvDev, tv, good, act).getResponse().getStatus());
    }

    @Test
    void p9_theAdministratorWithTotpCanReassignTheBinding() throws Exception {
        Registered first = registerApp("tv"), second = registerApp("tv");
        Acts.Tv tv = Acts.Tv.random();
        String act = Acts.trialDays(ISSUER, tv, NOW, 30);
        assertEquals(200, sync(first, tv, bindJson(pair(), tv.code(), first.publicId(), NOW), act).getResponse().getStatus());
        KeyPair secondKey = pair();   // la clé de la TV d'origine : le propriétaire en a lu l'empreinte sur son écran (second audit w23-05, HIGH-A)
        String body = "{\"identity\":\"" + tv.code() + "\",\"reason\":\"TV d'origine reconnue par le support\",\"installKeyFingerprint\":\"" + castbridge.server.licenses.InstallKeyFingerprint.ofBase64(rawPublic(secondKey)) + "\"}";
        assertEquals(403, mvc.perform(post("/api/v1/admin/wallet/rebind").header("Authorization", ADMIN).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getStatus(),
                "le jeton seul ne réaffecte pas");
        Admin a = newAdmin();
        assertEquals(200, mvc.perform(a.sign(post("/api/v1/admin/wallet/rebind").header("Authorization", ADMIN).contentType(MediaType.APPLICATION_JSON).content(body))).andReturn().getResponse().getStatus());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM lic_audit WHERE action = 'WALLET_REBIND' AND actor = ? AND target_id = ?", Long.class, a.name, tv.code()));
        assertNull(jdbc.queryForObject("SELECT install_pub FROM wallet_identity WHERE holder = ?", String.class, tv.code()));
        assertEquals(200, sync(second, tv, bindJson(secondKey, tv.code(), second.publicId(), NOW), act).getResponse().getStatus());
        assertEquals(jdbc.queryForObject("SELECT id FROM device WHERE public_id = ?", Long.class, second.publicId()),
                jdbc.queryForObject("SELECT api_device_id FROM wallet_identity WHERE holder = ?", Long.class, tv.code()));
    }
}
