package castbridge.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import castbridge.server.devices.DeviceService;
import castbridge.server.quiz.QuizService;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The same flows against a real MySQL 8.4 (the production image and settings), when Docker is available; skipped
 * otherwise (the H2 tests still run). Checks the Flyway migrations, the JPQL queries and the date handling on MySQL.
 */
@Testcontainers(disabledWithoutDocker = true)
class MySqlContainerTest extends ApiTestBase {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withCommand("--innodb-buffer-pool-size=128M", "--performance-schema=OFF", "--character-set-server=utf8mb4");

    @Autowired DeviceService devices;
    @Autowired QuizService quiz;

    @Test
    void fullFlowOnMySql() throws Exception {
        // quiz: seeded, synced, drawn
        JsonNode page = body(mvc.perform(get("/api/v1/quiz/questions").param("size", "500")).andExpect(status().isOk()).andReturn());
        assertTrue(page.get("total").asInt() >= 300);
        String token0 = page.get("syncToken").asText();
        assertEquals(0, body(mvc.perform(get("/api/v1/quiz/questions").param("since", token0)).andReturn()).get("total").asInt());
        mvc.perform(get("/api/v1/quiz/draw").param("seed", "7")).andExpect(jsonPath("$.count").value(15));
        mvc.perform(post("/api/v1/admin/quiz/questions/cm-geo-001/status").param("value", "rejected").header("Authorization", ADMIN))
                .andExpect(status().isOk());
        assertEquals("cm-geo-001", body(mvc.perform(get("/api/v1/quiz/questions").param("since", token0)).andReturn()).get("deleted").get(0).asText());

        // updates: publish, signed manifest, download
        mvc.perform(multipart("/api/v1/admin/releases").file(new MockMultipartFile("file", "tv.apk", "application/octet-stream", testApk()))
                .param("app", "tv").param("abi", "armeabi-v7a").param("versionCode", "42").param("versionName", "0.6")
                .param("notes", "Accents : é à ç « » — ok").header("Authorization", ADMIN)).andExpect(status().isCreated());
        JsonNode m = body(mvc.perform(get("/api/v1/updates/tv/latest").param("abis", "armeabi-v7a").param("versionCode", "7"))
                .andExpect(status().isOk()).andReturn());
        assertEquals("Accents : é à ç « » — ok", m.get("notes").asText());

        // devices: register, heartbeat, crash, stats, retention
        JsonNode reg = body(mvc.perform(post("/api/v1/devices/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"installId\":\"" + UUID.randomUUID() + "\",\"app\":\"tv\",\"versionCode\":7,\"androidIdHash\":\"" + "c".repeat(64) + "\"}"))
                .andExpect(status().isCreated()).andReturn());
        String auth = "Bearer " + reg.get("deviceToken").asText();
        mvc.perform(post("/api/v1/devices/heartbeat").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"versionCode\":42,\"storageFreeMb\":500}")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/devices/crash").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"boom\"}")).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/admin/devices/stats").header("Authorization", ADMIN)).andExpect(jsonPath("$.online").value(1));
        assertEquals(2, devices.detail(reg.get("deviceId").asText()).versions().size());
        devices.purge();
        quiz.purgeTombstones();
    }
}
