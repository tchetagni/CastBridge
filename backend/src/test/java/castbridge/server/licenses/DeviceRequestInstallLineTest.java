package castbridge.server.licenses;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import castbridge.server.licenses.DeviceIdentity.Factor;
import castbridge.server.web.ApiException;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Fait constaté le 2026-10-04 : la « demande d'appareil » de la TV porte une ligne {@code install=<clé d'installation>} que l'analyseur du serveur refusait (« ligne inattendue »).
 * Elle est maintenant tolérée et IGNORÉE (additif) : ni le code, ni k, ni les facteurs n'en dépendent ; les autres lignes inconnues restent refusées.
 */
class DeviceRequestInstallLineTest {
    private static Map<Factor, String> fp() {
        Map<Factor, String> m = new EnumMap<>(Factor.class);
        m.put(Factor.FLASH, "0123456789abcdef0123456789abcdef");
        m.put(Factor.ETHERNET, "fedcba9876543210fedcba9876543210");
        return m;
    }

    private static String text(String extra) {
        Map<Factor, String> f = fp();
        return "code=" + DeviceIdentity.code(f) + "\nk=" + DeviceIdentity.kFor(f.size()) + "\nfactor=FLASH|" + f.get(Factor.FLASH) + "\n" + extra + "\nfactor=ETHERNET|" + f.get(Factor.ETHERNET);
    }

    @Test
    void anInstallLineIsToleratedAndIgnored() {
        DeviceIdentity.Request r = DeviceIdentity.parseRequest(text("install=" + "A".repeat(43) + "="));
        assertEquals(DeviceIdentity.code(fp()), r.code());
        assertEquals(DeviceIdentity.parseRequest(text("")).factorsText(), r.factorsText(), "la ligne install= ne change rien à l'identité");
        assertEquals(DeviceIdentity.parseRequest(text("")).setHashHex(), r.setHashHex());
    }

    @Test
    void severalOrOddInstallLinesAreStillIgnoredButOtherUnknownLinesAreStillRefused() {
        assertEquals(DeviceIdentity.code(fp()), DeviceIdentity.parseRequest(text("install=x\ninstall=y")).code());
        assertEquals(DeviceIdentity.code(fp()), DeviceIdentity.parseRequest(text("  install=  \n")).code());
        assertThrows(ApiException.class, () -> DeviceIdentity.parseRequest(text("secret=1")));
    }
}
