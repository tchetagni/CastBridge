package castbridge.server.activations;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Audit w23-01 M4. act-ref.key and act-audit.key: a check value is stored at first use; at startup a key that differs makes the server refuse to start (instead of answering
 * 500 to every known TV or declaring every line of the history modified); a trailing newline added by an editor does not change the key.
 */
class KeyGuardTest extends ActTestBase {
    @Autowired KeyGuard guard;
    @Autowired TvRef tvRef;

    @BeforeEach
    void start() {
        resetModule();
        clock.set(Instant.parse("2026-10-10T08:00:00Z"));
    }

    @AfterEach
    void stop() { clock.reset(); }

    @Test
    void aTrailingNewlineDoesNotChangeTheKey(@org.junit.jupiter.api.io.TempDir Path dir) throws Exception {
        Path a = dir.resolve("a.key"), b = dir.resolve("b.key"), c = dir.resolve("c.key");
        Files.write(a, "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.US_ASCII));
        Files.write(b, "0123456789abcdef0123456789abcdef\n".getBytes(StandardCharsets.US_ASCII));
        Files.write(c, "0123456789abcdef0123456789abcdef\r\n".getBytes(StandardCharsets.US_ASCII));
        assertArrayEquals(Chains.readKey(a), Chains.readKey(b));
        assertArrayEquals(Chains.readKey(a), Chains.readKey(c));
        Files.write(c, "0123456789abcdef0123456789abcdeg".getBytes(StandardCharsets.US_ASCII));
        assertTrue(!java.util.Arrays.equals(Chains.readKey(a), Chains.readKey(c)), "another content is another key");
    }

    @Test
    void theCheckValuesAreStoredAtFirstUseAndAChangedKeyStopsTheServerLoudly() {
        jdbc.update("delete from act_key_check");
        guard.verify();   // first use: stored
        assertEquals(2, jdbc.queryForObject("select count(*) from act_key_check", Integer.class));
        guard.verify();   // same keys: fine
        String ref = jdbc.queryForObject("select check_value from act_key_check where name = 'act-ref'", String.class);
        assertEquals(64, ref.length());
        jdbc.update("update act_key_check set check_value = ? where name = 'act-ref'", "00".repeat(32));
        IllegalStateException e = assertThrows(IllegalStateException.class, guard::verify);
        assertTrue(e.getMessage().contains("act-ref.key"), e.getMessage());
        assertTrue(!e.getMessage().contains(ref) && !e.getMessage().contains("00".repeat(32)), "no check value in the message");
        jdbc.update("update act_key_check set check_value = ? where name = 'act-ref'", ref);
        jdbc.update("update act_key_check set check_value = ? where name = 'act-audit'", "11".repeat(32));
        assertTrue(assertThrows(IllegalStateException.class, guard::verify).getMessage().contains("act-audit.key"));
    }

    @Test
    void aDeviceCodeAlreadyKnownUnderAnotherReferenceAnswers503NotAnObscure500() throws Exception {
        Dev d = dev();
        String token = trialToken(DESK, d, clock.nowMs() - 3_600_000L);
        // the code is known under ANOTHER reference (as if act-ref.key had been replaced since)
        jdbc.update("insert into act_tv (tv_ref, device_code, trial_resets, api_devices, android_ids, reco, alerts_open) values (?,?,0,0,0,'NEVER',0)", "ffffffffffffffff", d.code());
        var i = install();
        var r = report(i, reportJson(d, clock.nowMs(), "TRIAL", List.of(token), null));
        assertEquals(503, r.getResponse().getStatus(), r.getResponse().getContentAsString());
        assertTrue(r.getResponse().getContentAsString().toLowerCase().contains("clé"), r.getResponse().getContentAsString());
        assertNotEquals(500, r.getResponse().getStatus());
    }
}
