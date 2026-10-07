package castbridge.server.licenses;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    private static final String SIG = "0cc4def54afef01f9b6821374ccf66548d8f49f512c6a3aa79aa9d60b1f6cd88";

    /** Exactly what CastBridge-TV gives, in its order (code, k, factors, install, install_sig), joined with the line end a mail client may leave. */
    private static String tvComplete(String lineEnd, Map<Factor, String> f) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        lines.add("code=" + DeviceIdentity.code(f));
        lines.add("k=" + DeviceIdentity.kFor(f.size()));
        f.forEach((k, h) -> lines.add("factor=" + k.name() + "|" + h));
        lines.add("install=x25519|" + "0a".repeat(32));
        lines.add("install_sig=ed25519|" + SIG);
        return String.join(lineEnd, lines);
    }

    /**
     * ACT-F4 amendée le 2026-10-07 : la demande COMPLÈTE de la TV, que le téléphone rend à l'agent et que celui-ci peut envoyer telle quelle, porte {@code install=x25519|<64 hexadécimaux>} (clé PUBLIQUE
     * d'installation). Le serveur l'accepte et l'ignore : même identité, même clé de signature liée, que la forme « pour le serveur » sans cette ligne.
     */
    @Test
    void theCompleteRequestOfTheTvIsAcceptedAsIsAndMeansTheSameAsTheServerForm() {
        Map<Factor, String> f = fp();
        DeviceIdentity.Request server = DeviceIdentity.parseRequest(tvComplete("\n", f).replace("install=x25519|" + "0a".repeat(32) + "\n", ""));
        for (String lineEnd : new String[] {"\n", "\r\n", "\r\n\r\n"}) {
            DeviceIdentity.Request full = DeviceIdentity.parseRequest(tvComplete(lineEnd, f));
            assertEquals(DeviceIdentity.code(f), full.code());
            assertEquals(server.factorsText(), full.factorsText());
            assertEquals(server.setHashHex(), full.setHashHex());
            assertEquals(server.k(), full.k());
            assertEquals(SIG, full.installSig(), "la clé de signature est lue : elle est signée dans l'activation de production");
            assertEquals(server.installSig(), full.installSig());
        }
    }

    @Test
    void theCompleteRequestOfAFiveFactorTvFitsTheServersBound() {
        Map<Factor, String> all = new EnumMap<>(Factor.class);
        for (Factor f : Factor.values()) all.put(f, Integer.toHexString(f.rank + 10).repeat(32).substring(0, 32));
        String t = tvComplete("\n", all);
        assertTrue(t.length() < 2000, "demande complète de 5 facteurs : " + t.length() + " caractères (borne du serveur : 2000)");
        assertEquals(DeviceIdentity.code(all), DeviceIdentity.parseRequest(t).code());
    }

    @Test
    void aMalformedSigningKeyIsStillRefusedWhenTheInstallLineIsThere() {
        Map<Factor, String> f = fp();
        assertThrows(ApiException.class, () -> DeviceIdentity.parseRequest(tvComplete("\n", f).replace(SIG, "zz")));
    }
}
