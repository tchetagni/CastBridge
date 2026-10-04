package castbridge.server.licenses;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Audit Opus w23-05, HIGH-1, côté serveur : une demande d'appareil qui porte la clé de signature de la TV ({@code install_sig=ed25519|<64 hex>}) donne une activation de PRODUCTION qui la porte
 * SIGNÉE (droit {@code ik|<hex>}) ; sans cette ligne (TV ancienne), aucune clé n'est liée ; l'essai ne la porte jamais ; une ligne mal formée est refusée, jamais ignorée.
 */
class ServerIssuedInstallKeyTest extends LicenseTestBase {
    private static final String SIG = "0cc4def54afef01f9b6821374ccf66548d8f49f512c6a3aa79aa9d60b1f6cd88";

    private ActivationService.Activation issueWith(String licenseId, Dev d, String extraLines) {
        return activations.issue(OWNER, new ActivationService.IssueRequest(licenseId, "tv", d.text() + extraLines, null, null, null), "server-api");
    }

    private String newLicense() {
        return licenses.create(OWNER, new LicenseService.NewLicense(null, client().id(), "PAID", 3, null, Instant.now().plus(Duration.ofDays(30)), null, null, List.of())).licenseId();
    }

    @Test
    void aRequestWithTheSigningKeyGivesAProductionActivationThatCarriesItSigned() {
        var a = issueWith(newLicense(), dev(), "\ninstall=" + "ab".repeat(32) + "\ninstall_sig=ed25519|" + SIG);
        var f = WireActivation.decode(a.text()).fields();
        assertThat(f.rights()).contains("ik|" + SIG);
        assertThat(WireActivation.installKeyOf(f.rights())).isEqualTo(java.util.Base64.getEncoder().encodeToString(java.util.HexFormat.of().parseHex(SIG)));
    }

    @Test
    void aRequestWithoutItBindsNoKeyAndTheSameRequestIsIdempotent() {
        String lic = newLicense();
        Dev d = dev();
        var a = issueWith(lic, d, "");
        assertThat(WireActivation.decode(a.text()).fields().rights()).noneMatch(WireActivation::isInstallKey);
        // the same device with its key later: a NEW activation (the claim is part of what makes two requests identical), the same request again gives the same token
        var b = issueWith(lic, d, "\ninstall_sig=ed25519|" + SIG);
        assertThat(WireActivation.decode(b.text()).fields().rights()).contains("ik|" + SIG);
        assertThat(b.text()).isNotEqualTo(a.text());
        assertThat(issueWith(lic, d, "\ninstall_sig=ed25519|" + SIG).text()).isEqualTo(b.text());
    }

    @Test
    void aMalformedOrRepeatedSigningKeyLineIsRefusedNotIgnored() {
        String lic = newLicense();
        assertThatThrownBy(() -> issueWith(lic, dev(), "\ninstall_sig=ed25519|zz")).hasMessageContaining("install_sig");
        assertThatThrownBy(() -> issueWith(lic, dev(), "\ninstall_sig=rsa|" + SIG)).hasMessageContaining("install_sig");
        assertThatThrownBy(() -> issueWith(lic, dev(), "\ninstall_sig=ed25519|" + SIG + "\ninstall_sig=ed25519|" + SIG)).hasMessageContaining("double");
    }

    @Test
    void theClaimGrantsNothingAndIsRefusedOnATrialKey() {
        assertThat(WireActivation.rightLineOk("ik|" + SIG)).isTrue();
        assertThat(WireActivation.rightLineOk("ik|" + SIG.toUpperCase())).isFalse();
        assertThat(WireActivation.rightLineOk("ik|abc")).isFalse();
        assertThat(WireActivation.isTrialRight("ik|" + SIG)).isFalse();
        assertThat(WireActivation.installKeyOf(List.of("usage|duree|1|2", "ik|" + SIG))).isNotNull();
        assertThatThrownBy(() -> WireActivation.installKeyOf(List.of("ik|" + SIG, "ik|" + SIG))).isInstanceOf(IllegalArgumentException.class);
        assertThat(WireActivation.installKeyOf(List.of())).isNull();
    }
}
