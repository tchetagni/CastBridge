package castbridge.server.licenses;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Second audit Opus w23-05 : MEDIUM-C (ii), l'émetteur du serveur montre l'empreinte de la clé d'installation qu'il signe pour que le propriétaire la compare avec l'écran de la TV ; LOW-E, la clé
 * d'installation n'est JAMAIS signée pour un téléphone (parité avec Kotlin et Python : seule une TV porte {@code ik}).
 */
class SecondAuditIssuerTest extends LicenseTestBase {
    private static final String SIG = "0cc4def54afef01f9b6821374ccf66548d8f49f512c6a3aa79aa9d60b1f6cd88";

    private String newLicense() {
        return licenses.create(OWNER, new LicenseService.NewLicense(null, client().id(), "PAID", 3, null, Instant.now().plus(Duration.ofDays(30)), null, null, List.of())).licenseId();
    }

    private ActivationService.Activation issue(String lic, String subject, String extra) {
        return activations.issue(OWNER, new ActivationService.IssueRequest(lic, subject, dev().text() + extra, null, null, null), "server-api");
    }

    @Test
    void theIssuedActivationAnswersWithTheFingerprintOfTheInstallKeyItSigned() {
        var a = issue(newLicense(), "tv", "\ninstall_sig=ed25519|" + SIG);
        String expected = InstallKeyFingerprint.ofRaw(java.util.HexFormat.of().parseHex(SIG));
        assertThat(a.installKeyFingerprint()).as("empreinte de la clé liée, à comparer avec l'écran de la TV").isEqualTo(expected);
        assertThat(expected).matches("[0-9a-f]{4}(-[0-9a-f]{4}){7}");
        assertThat(issue(newLicense(), "tv", "").installKeyFingerprint()).as("aucune clé liée : aucune empreinte").isNull();
    }

    @Test
    void aPhoneActivationNeverCarriesTheInstallKeyEvenWhenTheRequestHasInstallSig() {
        var a = issue(newLicense(), "phone", "\ninstall_sig=ed25519|" + SIG);
        assertThat(WireActivation.decode(a.text()).fields().rights()).noneMatch(WireActivation::isInstallKey);
        assertThat(a.installKeyFingerprint()).isNull();
    }

    @Test
    void theFingerprintIsStableAndCanonicalisable() {
        String fp = InstallKeyFingerprint.ofRaw(java.util.HexFormat.of().parseHex(SIG));
        assertThat(InstallKeyFingerprint.normalize(fp.toUpperCase())).hasSize(32).isEqualTo(fp.replace("-", ""));
        assertThat(InstallKeyFingerprint.normalize(fp.replace("-", " "))).isEqualTo(fp.replace("-", ""));
        assertThat(InstallKeyFingerprint.normalize("1234")).isNull();
        assertThat(InstallKeyFingerprint.ofRaw(new byte[31])).isNull();
    }
}
