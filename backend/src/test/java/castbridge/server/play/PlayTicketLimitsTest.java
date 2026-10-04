package castbridge.server.play;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import castbridge.server.devices.Device;
import castbridge.server.licenses.DeviceIdentity;
import castbridge.server.web.ApiException;
import java.security.SecureRandom;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Audit Opus de w20-04 (I2) : au plafond de mémoire on évince le plus ancien au lieu de refuser tout le monde ; plafond par adresse cliente. */
class PlayTicketLimitsTest {
    private static final long NOW = 1_800_000_000_000L;

    private static PlayTicketService service(int perDevice, int perAddress, int maxDevices) throws Exception {
        PlayTicketKey key = new PlayTicketKey(java.security.KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPrivate());
        return new PlayTicketService(key, perDevice, perAddress, new SecureRandom(), maxDevices);
    }

    private static Device tv(String id) { Device d = new Device(); d.publicId = id; d.app = "tv"; d.country = "CM"; return d; }

    private static String code(int i) { return DeviceIdentity.code(Map.of(DeviceIdentity.Factor.FLASH, "%032x".formatted(i))); }

    @Test
    void whenTheDeviceTableIsFullTheLeastRecentlyUsedIsEvictedAndNewDevicesAreNotRefused() throws Exception {
        PlayTicketService s = service(1, 1_000, 3);
        for (int i = 0; i < 3; i++) s.issue(tv("d" + i), code(i), NOW, "10.0.0." + i);
        assertDoesNotThrow(() -> s.issue(tv("d3"), code(3), NOW + 1, "10.0.0.3"), "table pleine : d0 est évincé, d3 est servi");
        assertEquals(429, assertThrows(ApiException.class, () -> s.issue(tv("d3"), code(3), NOW + 2, "10.0.0.3")).status().value(), "d3 est bien compté");
    }

    @Test
    void anIpv6SubscriberHasOneAddressCapForItsWholeSlash64() throws Exception {
        PlayTicketService s = service(20, 3, 1_000);
        for (int i = 0; i < 3; i++) s.issue(tv("v" + i), code(30 + i), NOW, "2001:db8:abcd:1::" + (i + 1));   // trois adresses d'un même /64
        assertEquals(429, assertThrows(ApiException.class, () -> s.issue(tv("v9"), code(39), NOW + 1, "2001:db8:abcd:1:ffff::9")).status().value(),
                "un abonné IPv6 ne contourne pas le plafond en changeant d'adresse dans son /64");
        assertDoesNotThrow(() -> s.issue(tv("w0"), code(40), NOW + 1, "2001:db8:abcd:2::1"), "un autre /64 n'est pas touché");
        assertEquals("203.0.113.9", PlayTicketService.addressKey("203.0.113.9"));
        assertEquals("203.0.113.9", PlayTicketService.addressKey("::ffff:203.0.113.9"), "IPv4 inscrite en IPv6");
    }

    @Test
    void aClientAddressHasItsOwnHourlyCapAcrossDevices() throws Exception {
        PlayTicketService s = service(20, 3, 1_000);
        for (int i = 0; i < 3; i++) s.issue(tv("a" + i), code(10 + i), NOW, "203.0.113.9");
        assertEquals(429, assertThrows(ApiException.class, () -> s.issue(tv("a9"), code(19), NOW + 1, "203.0.113.9")).status().value());
        assertDoesNotThrow(() -> s.issue(tv("b0"), code(20), NOW + 1, "203.0.113.10"));
        assertDoesNotThrow(() -> s.issue(tv("a0"), code(10), NOW + 3_600_001L, "203.0.113.9"), "la fenêtre glisse");
    }
}
