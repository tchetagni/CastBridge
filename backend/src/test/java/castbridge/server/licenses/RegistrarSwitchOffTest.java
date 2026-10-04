package castbridge.server.licenses;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import castbridge.server.licenses.ReportedActivationRegistrar.Presented;
import castbridge.server.licenses.ReportedActivationRegistrar.Registration;
import castbridge.server.licenses.ReportedActivationRegistrar.Status;
import castbridge.server.licenses.ReportedActivationRegistrar.Via;
import castbridge.server.wallet.Acts;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

/**
 * Second audit Opus w23-05, MEDIUM-D : un INTERRUPTEUR propre au registrar, {@code castbridge.licenses.registrar.enabled} (variable {@code CASTBRIDGE_LICENSES_REGISTRAR_ENABLED}), pour l'éteindre sans
 * redéployer ni éteindre tout le module des licences. Éteint : aucune licence ni poste n'est créé par une activation présentée, rien n'est écrit, la TV voit « licence en attente ».
 */
@org.springframework.test.context.TestPropertySource(properties = "castbridge.licenses.registrar.enabled=false")
class RegistrarSwitchOffTest extends RegistrarTestBase {
    @Test
    void withTheRegistrarOffAnActivationOpensNothingAndWritesNothing() throws Exception {
        Acts.Tv tv = tv();
        String lic = licenseId();
        String token = production(ISSUER, tv, lic, null, NOW - HOUR, null);
        Registration r = registrar.register(new Presented(token, tv.code(), rawPublic(installOf(tv)), true), Via.WALLET, T0);
        assertEquals(Status.IGNORED, r.status());
        assertEquals("REGISTRAR_OFF", r.reason());
        Registered dev = registerApp("tv");
        JsonNode s = ok(sync(dev, tv, token));
        assertEquals(0, count("SELECT COUNT(*) FROM lic_license WHERE license_id = ?", lic));
        assertEquals(0, count("SELECT COUNT(*) FROM lic_registration WHERE license_id = ?", lic));
        assertEquals(0, balance(tv.code(), "NDEM"), "aucun versement : la licence n'existe pas");
        assertTrue(noticeReasons(s).contains("LICENSE_PENDING"), "la TV voit « licence en attente d'enregistrement » : " + s.path("notices"));
    }
}
