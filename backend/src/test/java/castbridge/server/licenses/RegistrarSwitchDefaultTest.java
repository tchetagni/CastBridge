package castbridge.server.licenses;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Second audit Opus w23-05, MEDIUM-D : l'interrupteur {@code castbridge.licenses.registrar.enabled} est ALLUMÉ par défaut (comportement documenté du déploiement : le registrar tourne dès le démarrage),
 * lisible par la variable d'environnement {@code CASTBRIDGE_LICENSES_REGISTRAR_ENABLED}, et le contexte complet l'a bien allumé.
 */
class RegistrarSwitchDefaultTest extends RegistrarTestBase {
    @Test
    void theSwitchIsDeclaredInApplicationYamlWithDefaultTrueAndAnEnvironmentVariable() throws Exception {
        String yml = Files.readString(Path.of("src", "main", "resources", "application.yml"), StandardCharsets.UTF_8);
        assertTrue(yml.contains("registrar:"), "bloc registrar sous licenses");
        assertTrue(yml.contains("${CASTBRIDGE_LICENSES_REGISTRAR_ENABLED:true}"), "variable d'environnement, allumé par défaut");
    }

    @Test
    void theRunningContextHasTheRegistrarOnByDefaultAndItRegisters() {
        assertTrue(registrar.enabled(), "le registrar est allumé quand rien n'est configuré");
        castbridge.server.wallet.Acts.Tv tv = tv();
        var r = registrar.register(new ReportedActivationRegistrar.Presented(production(ISSUER, tv, licenseId(), null, NOW - HOUR, null), tv.code(), rawPublic(installOf(tv)), true),
                ReportedActivationRegistrar.Via.WALLET, T0);
        assertEquals(ReportedActivationRegistrar.Status.REGISTERED, r.status(), r.reason());
    }
}
