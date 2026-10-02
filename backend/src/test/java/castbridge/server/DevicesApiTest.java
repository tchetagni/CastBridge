package castbridge.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import castbridge.server.devices.DeviceService;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

class DevicesApiTest extends ApiTestBase {
    @Autowired DeviceService service;
    @Autowired JdbcTemplate jdbc;

    private static final String ANDROID_ID_HASH = "a".repeat(64);

    private static String report(String installId, String androidIdHash, int versionCode) {
        return """
                {"installId":"%s",%s"app":"tv","versionCode":%d,"versionName":"0.%d","channel":"stable","abi":"armeabi-v7a",
                 "supportedAbis":["armeabi-v7a","armeabi"],"sdk":34,"platform":"android-tv","manufacturer":"Hisense","model":"43A4",
                 "deviceName":"TV salon","osName":"Android TV","osBuild":"GaiaOS 3.2 build 1234","fingerprint":"hisense/43a4/…:14/user",
                 "screen":"1920x1080","densityDpi":320,"ramTotalMb":1536,"storageFreeMb":2048,"storageTotalMb":8192,
                 "usbPresent":true,"usbFreeMb":30000,"btGateway":true,"sshEnabled":false,"wifiDirect":false,"videoCount":12,
                 "unknownField":"ignored"}"""
                .formatted(installId, androidIdHash == null ? "" : "\"androidIdHash\":\"" + androidIdHash + "\",", versionCode, versionCode);
    }

    private JsonNode register(String body) throws Exception {
        return body(mvc.perform(post("/api/v1/devices/register").contentType(MediaType.APPLICATION_JSON).content(body)
                .header("X-Test-Country", "cm")).andExpect(status().isCreated()).andReturn());
    }

    @Test
    void registerHeartbeatReinstallCrash() throws Exception {
        String install1 = UUID.randomUUID().toString();
        JsonNode reg = register(report(install1, ANDROID_ID_HASH, 7));
        String id = reg.get("deviceId").asText();
        String token = reg.get("deviceToken").asText();
        assertEquals(900, reg.get("heartbeatSeconds").asInt());
        assertTrue(token.length() >= 40);

        // stored: never the token nor the androidIdHash as sent
        String stored = jdbc.queryForObject("select token_hash from device where public_id = ?", String.class, id);
        assertNotEquals(token, stored);
        assertNotEquals(ANDROID_ID_HASH, jdbc.queryForObject("select android_id_hash from device where public_id = ?", String.class, id));
        assertEquals("CM", jdbc.queryForObject("select country from device where public_id = ?", String.class, id));
        // the free-text device name sent by an old app is ignored, not stored
        assertNull(jdbc.queryForObject("select device_name from device where public_id = ?", String.class, id));

        // heartbeat with the token
        mvc.perform(post("/api/v1/devices/heartbeat").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content(report(install1, ANDROID_ID_HASH, 8))).andExpect(status().isOk())
                .andExpect(jsonPath("$.checkUpdate").value(false)).andExpect(jsonPath("$.blocked").value(false))
                .andExpect(jsonPath("$.channel").value("stable"));
        // no token / wrong token: 401
        mvc.perform(post("/api/v1/devices/heartbeat").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/devices/heartbeat").header("Authorization", "Bearer forged").contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isUnauthorized());

        // reinstall: new installId, same ANDROID_ID → same device, new token, old token dead, 2 installations
        String install2 = UUID.randomUUID().toString();
        JsonNode reg2 = register(report(install2, ANDROID_ID_HASH, 8));
        assertEquals(id, reg2.get("deviceId").asText());
        assertNotEquals(token, reg2.get("deviceToken").asText());
        mvc.perform(post("/api/v1/devices/heartbeat").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isUnauthorized());
        String token2 = reg2.get("deviceToken").asText();

        // another device (no ANDROID_ID): its own record
        JsonNode other = register(report(UUID.randomUUID().toString(), null, 7));
        assertNotEquals(id, other.get("deviceId").asText());

        // crash report
        mvc.perform(post("/api/v1/devices/crash").header("Authorization", "Bearer " + token2).contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"NullPointerException dans le lecteur\",\"detail\":\"at castbridge.receiver.PlayerActivity\",\"versionCode\":8}"))
                .andExpect(status().isNoContent());

        DeviceService.Detail d = service.detail(id);
        assertEquals(2, d.installs().size());
        assertEquals(2, d.versions().size(), "versions 7 then 8");
        assertEquals(1, d.crashes().size());
        assertEquals("NullPointerException dans le lecteur", d.device().lastError);
        assertEquals("android-tv", d.device().platform);
        assertEquals("armeabi-v7a,armeabi", d.device().supportedAbis);
        assertFalse(d.days().isEmpty());
        assertTrue(d.heartbeats().size() >= 1);

        // admin: force an update check (delivered once), beta channel, block
        mvc.perform(post("/api/v1/admin/devices/" + id + "/check-update").header("Authorization", ADMIN)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/devices/" + id + "/channel").param("channel", "beta").header("Authorization", ADMIN)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/devices/heartbeat").header("Authorization", "Bearer " + token2).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(jsonPath("$.checkUpdate").value(true)).andExpect(jsonPath("$.channel").value("beta"));
        mvc.perform(post("/api/v1/devices/heartbeat").header("Authorization", "Bearer " + token2).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(jsonPath("$.checkUpdate").value(false));

        mvc.perform(get("/api/v1/quiz/draw").header("Authorization", "Bearer " + token2)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/devices/" + id + "/block").header("Authorization", ADMIN)).andExpect(status().isOk())
                .andExpect(jsonPath("$.blocked").value(true));
        mvc.perform(get("/api/v1/quiz/draw").header("Authorization", "Bearer " + token2)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Cet appareil est bloqué par l'administrateur"));
        mvc.perform(get("/api/v1/quiz/questions").param("deviceId", id)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/updates/tv/latest").header("Authorization", "Bearer " + token2)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/devices/heartbeat").header("Authorization", "Bearer " + token2).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(jsonPath("$.blocked").value(true));

        // admin edit + list + stats
        mvc.perform(put("/api/v1/admin/devices/" + id).header("Authorization", ADMIN).contentType(MediaType.APPLICATION_JSON)
                .content("{\"label\":\"Salon Esaie\",\"group\":\"Famille\",\"note\":\"TV du salon\"}"))
                .andExpect(jsonPath("$.name").value("Salon Esaie"));
        mvc.perform(get("/api/v1/admin/devices").param("platform", "android-tv").param("q", "esaie").header("Authorization", ADMIN))
                .andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.devices[0].online").value(true))
                .andExpect(jsonPath("$.devices[0].group").value("Famille"));
        mvc.perform(get("/api/v1/admin/devices/stats").header("Authorization", ADMIN)).andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.online").value(2)).andExpect(jsonPath("$.platforms['android-tv']").value(2));
        mvc.perform(get("/api/v1/admin/devices/" + id).header("Authorization", ADMIN)).andExpect(jsonPath("$.installs.length()").value(2))
                .andExpect(jsonPath("$.device.tokenHash").doesNotExist());
    }

    @Test
    void validationAndRetention() throws Exception {
        mvc.perform(post("/api/v1/devices/register").contentType(MediaType.APPLICATION_JSON).content("{\"installId\":\"x\",\"app\":\"fridge\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.length()").value(2));
        mvc.perform(post("/api/v1/devices/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"installId\":\"" + UUID.randomUUID() + "\",\"app\":\"phone\",\"androidIdHash\":\"zz\"}"))
                .andExpect(status().isBadRequest());

        JsonNode reg = register(report(UUID.randomUUID().toString(), "b".repeat(64), 7));
        String id = reg.get("deviceId").asText();
        // make everything old: detailed heartbeats and the IP must go, daily aggregates stay
        jdbc.update("update device_heartbeat set seen_at = ?", java.sql.Timestamp.from(java.time.Instant.now().minus(java.time.Duration.ofDays(40))));
        jdbc.update("update device set ip_seen_at = ?", java.sql.Timestamp.from(java.time.Instant.now().minus(java.time.Duration.ofDays(40))));
        service.purge();
        assertEquals(0, jdbc.queryForObject("select count(*) from device_heartbeat", Integer.class));
        assertNull(jdbc.queryForObject("select ip from device where public_id = ?", String.class, id));
        assertTrue(jdbc.queryForObject("select count(*) from device_daily", Integer.class) >= 1);
        assertEquals(1, jdbc.queryForObject("select count(*) from device where public_id = ?", Integer.class, id));
    }
}
