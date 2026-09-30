package castbridge.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** A full application (H2 in MySQL mode, fresh database and APK folder per test class) driven through MockMvc. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class ApiTestBase {
    public static final String ADMIN = "Bearer test-admin-token-0123456789-abcdefghijklmnop";
    public static final String PUBLIC_KEY_B64 = "11qYAYKxCrfVS/7TyWQHOg7hcvPapiMlrwIaaPcHURo=";

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry r) {
        try {
            Path dir = Files.createTempDirectory("cb-apk");
            r.add("castbridge.storage-dir", dir::toString);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    protected JsonNode body(MvcResult r) throws IOException {
        return json.readTree(r.getResponse().getContentAsByteArray());
    }

    protected static byte[] testApk() throws IOException {
        try (var in = ApiTestBase.class.getResourceAsStream("/apk/receiver-42.apk")) {
            return in.readAllBytes();
        }
    }
}
