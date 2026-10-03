package castbridge.server.play;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import castbridge.server.devices.Device;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.junit.jupiter.api.Test;

/**
 * The golden ticket: the Java issuer, with a TEST key (a fixed seed, worth nothing), a fixed clock and a seeded random, produces byte for byte the ticket stored in
 * {@code server-play/src/test/resources/play/ticket-golden.txt}, which the Kotlin verifier of {@code castbridge-play} verifies in its own test ({@code TicketGoldenTest}).
 * So the two languages cannot drift apart on the format (domain prefix, base64url, field names). Regenerate with {@code -Dplay.golden.write=true}.
 */
class PlayTicketGoldenTest {
    private static final Path GOLDEN = Path.of("..", "server-play", "src", "test", "resources", "play", "ticket-golden.txt");
    private static final long NOW = 1_800_000_000_000L;

    private static String render() throws Exception {
        byte[] seed = new byte[32];
        java.util.Arrays.fill(seed, (byte) 7);
        PlayTicketKey key = new PlayTicketKey(PlayTicketKey.parse(Base64.getEncoder().encode(seed)));
        SecureRandom random = SecureRandom.getInstance("SHA1PRNG");
        random.setSeed(20261003L);
        PlayTicketService service = new PlayTicketService(key, 20, random);
        Device d = new Device();
        d.publicId = "00000000-0000-4000-8000-000000000001";
        d.app = "tv";
        d.country = "CM";
        d.blocked = false;
        String code = castbridge.server.licenses.DeviceIdentity.code(java.util.Map.of(castbridge.server.licenses.DeviceIdentity.Factor.FLASH, "a".repeat(32)));
        String ticket = service.issue(d, code, NOW).ticket();
        String pub = Base64.getEncoder().encodeToString(new Ed25519PrivateKeyParameters(seed, 0).generatePublicKey().getEncoded());
        return "# Test vector of the play ticket (w20-04). TEST key only. Written by PlayTicketGoldenTest, verified by TicketGoldenTest (castbridge-play).\n"
                + "pub=" + pub + "\nnow=" + NOW + "\ndeviceId=" + d.publicId + "\ndeviceCode=" + code + "\nticket=" + ticket + "\n";
    }

    @Test
    void theIssuerReproducesTheCommittedGoldenTicket() throws Exception {
        String rendered = render();
        if (Boolean.getBoolean("play.golden.write")) Files.writeString(GOLDEN, rendered, StandardCharsets.UTF_8);
        assertTrue(Files.isRegularFile(GOLDEN), "run once with -Dplay.golden.write=true");
        assertEquals(rendered, Files.readString(GOLDEN, StandardCharsets.UTF_8), "the golden ticket drifted: the Kotlin verifier would no longer agree");
    }
}
