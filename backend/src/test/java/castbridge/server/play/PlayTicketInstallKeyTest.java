package castbridge.server.play;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import castbridge.server.devices.Device;
import castbridge.server.licenses.DeviceIdentity;
import castbridge.server.web.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Audit Opus H-3 : le ticket épingle la clé d'installation de la TV (jamais remise aux téléphones) ; une autre clé pour le même code est refusée, un ticket sans clé n'est plus servi pour un code épinglé. */
class PlayTicketInstallKeyTest {
    private static final long NOW = 1_800_000_000_000L;

    private static PlayTicketService service() throws Exception {
        PlayTicketKey key = new PlayTicketKey(java.security.KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPrivate());
        return new PlayTicketService(key, 20, 1_000, new SecureRandom(), 1_000);
    }

    private static Device tv(String id) { Device d = new Device(); d.publicId = id; d.app = "tv"; d.country = "CM"; return d; }
    private static String code(int i) { return DeviceIdentity.code(Map.of(DeviceIdentity.Factor.FLASH, "%032x".formatted(i))); }
    private static byte[] raw(int seed) { byte[] b = new byte[32]; java.util.Arrays.fill(b, (byte) seed); return b; }
    private static String b64(byte[] k) { return Base64.getEncoder().encodeToString(k); }
    private static String sha(byte[] k) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(k)); }

    private static String payloadOf(PlayTicketService.Issued t) {
        return new String(Base64.getUrlDecoder().decode(t.ticket().split("\\.")[1]), StandardCharsets.UTF_8);
    }

    @Test
    void theTicketCarriesTheFingerprintOfTheInstallKey() throws Exception {
        PlayTicketService s = service();
        PlayTicketService.Issued t = s.issue(tv("d1"), code(1), NOW, "10.0.0.1", b64(raw(7)));
        assertTrue(payloadOf(t).contains("\"ik\":\"" + sha(raw(7)) + "\""), payloadOf(t));
    }

    @Test
    void aTicketRequestedWithoutAKeyStillWorksForALegacyTvButCarriesNoFingerprint() throws Exception {
        PlayTicketService s = service();
        assertFalse(payloadOf(s.issue(tv("d1"), code(1), NOW, "10.0.0.1", null)).contains("\"ik\""));
    }

    @Test
    void anotherKeyForTheSameCodeIsRefusedAndKeylessRequestsAreRefusedOnceTheCodeIsPinned() throws Exception {
        PlayTicketService s = service();
        s.issue(tv("tv"), code(2), NOW, "10.0.0.2", b64(raw(1)));                                   // la vraie TV épingle sa clé
        ApiException other = assertThrows(ApiException.class, () -> s.issue(tv("fake"), code(2), NOW + 1, "10.0.0.3", b64(raw(2))));
        assertEquals(403, other.status().value(), "un faux appareil qui demande un ticket pour le code de la TV avec SA clé");
        ApiException keyless = assertThrows(ApiException.class, () -> s.issue(tv("fake"), code(2), NOW + 2, "10.0.0.3", null));
        assertEquals(403, keyless.status().value(), "sans clé : refusé, sinon un ticket sans empreinte contournerait l'épinglage");
        assertDoesNotThrow(() -> s.issue(tv("tv"), code(2), NOW + 3, "10.0.0.2", b64(raw(1))), "la vraie TV est toujours servie");
        assertDoesNotThrow(() -> s.issue(tv("tv2"), code(2), NOW + 4, "10.0.0.2", b64(raw(1))), "même TV réinstallée côté API (autre appareil API), même clé");
    }

    @Test
    void aMalformedInstallKeyIsABadRequest() throws Exception {
        PlayTicketService s = service();
        for (String bad : new String[] {"pas du base64 !", b64(new byte[31]), b64(new byte[33]), ""}) {
            assertEquals(400, assertThrows(ApiException.class, () -> s.issue(tv("d9"), code(9), NOW, "10.0.0.9", bad)).status().value(), "clé refusée : " + bad);
        }
    }

    @Test
    void thePinOutlivesTheDayAndTheTableIsBounded() throws Exception {
        PlayTicketKey key = new PlayTicketKey(java.security.KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPrivate());
        PlayTicketService s = new PlayTicketService(key, 20, 1_000, new SecureRandom(), 2);   // 2 entrées au plus par table
        s.issue(tv("tv"), code(3), NOW, "10.0.0.2", b64(raw(1)));
        long nextWeek = NOW + 7 * 86_400_000L;
        assertEquals(403, assertThrows(ApiException.class, () -> s.issue(tv("fake"), code(3), nextWeek, "10.0.0.3", b64(raw(2)))).status().value(), "l'épinglage ne s'oublie pas au bout de 24 h");
        for (int i = 10; i < 14; i++) s.issue(tv("x" + i), code(i), nextWeek, "10.0.1." + i, b64(raw(i)));   // la table reste bornée : les plus anciens sortent, personne n'est refusé pour autant
    }
}
