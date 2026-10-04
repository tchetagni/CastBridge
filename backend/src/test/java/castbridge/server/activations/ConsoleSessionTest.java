package castbridge.server.activations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import castbridge.server.licenses.Actor;
import castbridge.server.licenses.Role;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;

/** Option A of D-W23-3 (off by default): a 32-hex challenge, single use, 120 s, signed by the owner phone key, gives a 15-minute read-only bearer. */
class ConsoleSessionTest extends ActTestBase {
    @Autowired ConsoleSession sessions;

    @DynamicPropertySource
    static void on(DynamicPropertyRegistry r) { r.add("castbridge.activations.console-sessions", () -> "true"); }

    Instant t0;

    @BeforeEach
    void start() {
        t0 = Instant.parse("2026-10-10T08:00:00Z");
        clock.set(t0);
    }

    @AfterEach
    void stop() { clock.reset(); }

    private MvcResult challenge(String kid) throws Exception { return mvc.perform(get("/api/v1/activations/console/challenge?kid=" + kid)).andReturn(); }

    private MvcResult session(String kid, String challenge, byte[] signature) throws Exception {
        String body = "{\"kid\":\"" + kid + "\",\"challenge\":\"" + challenge + "\",\"signature\":\"" + Base64.getEncoder().encodeToString(signature) + "\"}";
        return mvc.perform(post("/api/v1/activations/console/session").contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
    }

    private static byte[] signChallenge(org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters k, String challenge) { return ActTestBase.sign(k, "castbridge-console-session-v1\n" + challenge); }

    @Test
    void thePhoneKeyOpensAFifteenMinuteReadSession() throws Exception {
        MvcResult c = challenge(kid(PHONE));
        assertEquals(200, c.getResponse().getStatus());
        String ch = body(c).get("challenge").asText();
        assertTrue(ch.matches("[0-9a-f]{32}"));
        assertEquals(120, body(c).get("expiresInSeconds").asInt());

        MvcResult s = session(kid(PHONE), ch, signChallenge(PHONE, ch));
        assertEquals(200, s.getResponse().getStatus(), s.getResponse().getContentAsString());
        JsonNode b = body(s);
        assertEquals(900, b.get("expiresInSeconds").asInt());
        assertEquals("[\"ACT_READ\",\"ACT_JOURNAL_UPLOAD\"]", b.get("scopes").toString());
        String token = b.get("token").asText();
        assertFalse(jdbc.queryForList("select * from act_event").toString().contains(token));

        Optional<Actor> actor = sessions.authenticate(token);
        assertTrue(actor.isPresent());
        assertEquals(Role.SUPPORT, actor.get().role(), "reads and journal upload only: never an export or a decision");
        assertEquals("phone:" + kid(PHONE), actor.get().name());
        assertEquals("phone", actor.get().channel());
        assertTrue(ActPermissions.allows(actor.get(), ActPermissions.Perm.ACT_READ));
        assertTrue(ActPermissions.allows(actor.get(), ActPermissions.Perm.ACT_JOURNAL_UPLOAD));
        assertFalse(ActPermissions.allows(actor.get(), ActPermissions.Perm.ACT_ALERT_ACK), "the session scope is narrower than the SUPPORT role");
        assertFalse(ActPermissions.allows(actor.get(), ActPermissions.Perm.ACT_EXPORT));
        clock.set(t0.plus(Duration.ofMinutes(16)));
        assertTrue(sessions.authenticate(token).isEmpty(), "the token dies after 15 minutes");
        assertTrue(sessions.authenticate("act1.fake").isEmpty());
    }

    @Test
    void aChallengeIsSingleUseAndShortLived() throws Exception {
        String ch = body(challenge(kid(PHONE))).get("challenge").asText();
        assertEquals(200, session(kid(PHONE), ch, signChallenge(PHONE, ch)).getResponse().getStatus());
        assertEquals(400, session(kid(PHONE), ch, signChallenge(PHONE, ch)).getResponse().getStatus(), "second use");
        String late = body(challenge(kid(PHONE))).get("challenge").asText();
        clock.set(t0.plus(Duration.ofSeconds(121)));
        assertEquals(400, session(kid(PHONE), late, signChallenge(PHONE, late)).getResponse().getStatus(), "after 120 s");
    }

    @Test
    void onlyAPhoneKeyWithAGoodSignatureIsAccepted() throws Exception {
        assertEquals(403, challenge(kid(DESK)).getResponse().getStatus(), "the office key is not a console key");
        assertEquals(403, challenge(kid(STRANGER)).getResponse().getStatus());
        assertEquals(400, challenge("zz").getResponse().getStatus());
        String ch = body(challenge(kid(PHONE))).get("challenge").asText();
        assertEquals(403, session(kid(PHONE), ch, signChallenge(DESK, ch)).getResponse().getStatus(), "signed by the wrong key");
        String ch2 = body(challenge(kid(PHONE))).get("challenge").asText();
        assertEquals(403, session(kid(PHONE), ch2, signChallenge(PHONE, ch)).getResponse().getStatus(), "signed another challenge");
        assertEquals(400, session(kid(PHONE), "0".repeat(32), signChallenge(PHONE, "0".repeat(32))).getResponse().getStatus(), "never issued");
        assertNotNull(ch2);
    }

    @Test
    void tenChallengesAnHourPerKey() throws Exception {
        t0 = t0.plus(Duration.ofDays(2));
        clock.set(t0);
        for (int i = 0; i < 10; i++) assertEquals(200, challenge(kid(PHONE)).getResponse().getStatus());
        assertEquals(429, challenge(kid(PHONE)).getResponse().getStatus());
        clock.set(t0.plus(Duration.ofMinutes(61)));
        assertEquals(200, challenge(kid(PHONE)).getResponse().getStatus());
    }
}
