package castbridge.server.licenses;

import static org.assertj.core.api.Assertions.assertThat;

import castbridge.server.licenses.LicenseService.LicenseRow;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Constat R-1 de l'audit Opus (w23-01) : toute licence créée par le serveur avait {@code end_at = NULL}, quelle que soit la durée de la clé : le portefeuille aurait payé une clé de 90 jours
 * comme ILLIMITÉE (5 000 + 50 puis 1 000 + 10 par mois). {@code start_at} et {@code end_at} viennent maintenant du droit signé {@code usage} (docs/ACTIVATION-FORMAT.md, plafond d'usage) ;
 * NULL seulement si la clé est vraiment illimitée.
 */
class EndAtTest extends LicenseTestBase {
    private static final long DAY = WireActivation.DAY_MS;

    private ActivationService.Activation auto(String days) {
        return activations.issue(OWNER, new ActivationService.IssueRequest("auto", "tv", dev().text(), null, null, null, days), "server-api");
    }

    @Test
    void anAutoLicenceOfANinetyDayKeyEndsWithTheKey() {
        var a = auto("90");
        LicenseRow l = licenses.get(a.licenseId());
        assertThat(l.endAt()).as("end_at de la licence générée").isNotNull();
        assertThat(l.endAt()).isEqualTo(a.usageEnd());
        assertThat(l.startAt()).isEqualTo(a.issuedAt());
        assertThat(l.endAt().toEpochMilli() - l.startAt().toEpochMilli()).isEqualTo(90 * DAY);
    }

    @Test
    void anAutoLicenceOfAnUnlimitedKeyHasNoEnd() {
        for (String days : new String[] {null, "", "illimitée"}) {
            var a = auto(days);
            LicenseRow l = licenses.get(a.licenseId());
            assertThat(l.endAt()).as("clé illimitée (" + days + ") : sans fin").isNull();
            assertThat(l.startAt()).isEqualTo(a.issuedAt());
        }
    }

    @Test
    void theBoundsOfTheKeyDurationAreTheBoundsOfTheLicence() {
        assertThat(licenses.get(auto("1").licenseId()).endAt().toEpochMilli() - licenses.get(auto("1").licenseId()).startAt().toEpochMilli()).isBetween(DAY - 5_000, DAY + 5_000);
        var a = auto("3660");
        assertThat(licenses.get(a.licenseId()).endAt()).isEqualTo(a.usageEnd());
    }

    @Test
    void aLicenceCreatedByTheLedgerImportHasNoKnownEndBeforeAnyToken() throws java.io.IOException {
        // le registre ne porte aucune durée : l'événement `license` crée une licence sans fin ; la première clé vérifiée d'une licence importée la corrige (RegistrarEndAtTest)
        String lic = "lic-imp-" + Long.toHexString(System.nanoTime());
        long at = Instant.now().minusSeconds(3600).toEpochMilli();
        var report = importAuto(List.of(licenseEvent(DESKTOP, at, lic, 1, 0)));
        assertThat(report.applied()).isEqualTo(1);
        assertThat(licenses.get(lic).endAt()).as("avant tout jeton, la licence importée n'a pas de fin connue").isNull();
    }
}
