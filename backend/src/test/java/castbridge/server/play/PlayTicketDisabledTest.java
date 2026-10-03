package castbridge.server.play;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import castbridge.server.ApiTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** Without the ticket key file the route answers 503 (« ticket désactivé »), like the quiz packs without a signature: nothing is ever issued unsigned. */
class PlayTicketDisabledTest extends ApiTestBase {
    @Test
    void noKeyMeansNoTicket() throws Exception {
        String id = UUID.randomUUID().toString();
        String report = """
                {"installId":"%s","app":"tv","versionCode":7,"versionName":"0.7","channel":"stable","abi":"armeabi-v7a","supportedAbis":["armeabi-v7a"],"sdk":34,
                 "platform":"android-tv","manufacturer":"Hisense","model":"43A4"}""".formatted(id);
        JsonNode reg = body(mvc.perform(post("/api/v1/devices/register").contentType(MediaType.APPLICATION_JSON).content(report)).andExpect(status().isCreated()).andReturn());
        mvc.perform(post("/api/v1/play/ticket").contentType(MediaType.APPLICATION_JSON).header("Authorization", "Bearer " + reg.get("deviceToken").asText())
                .content("{\"deviceCode\":\"" + PlayTicketControllerTest.code() + "\"}")).andExpect(status().isServiceUnavailable());
    }
}
