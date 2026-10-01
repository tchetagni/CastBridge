package castbridge.server.licenses;

import java.nio.file.Path;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the licence module (CASTBRIDGE_LICENSES_*). Everything is OFF by default: the module only exists once the
 * owner turns it on after a backup and a staging run (docs/LICENSE-ADMIN.md, "Déploiement sûr").
 *
 * @param enabled          feature switch of the /admin/licenses pages and of /api/v1/admin/licenses/** (off = 404)
 * @param publicRoutes     separate switch of GET /api/v1/revocations and GET /api/v1/entitlements/me (off = 404)
 * @param trialIssuance    "manual" (default: the owner issues every trial) or "automatic" (reserved: no automatic route is shipped)
 * @param secretsDir       folder of the Docker secrets (never inside the image): signing key, audit key, TOTP key
 * @param signingKeyFile   file name, inside secretsDir, of the SERVER Ed25519 key (scope-limited: see ServerSignerScope)
 * @param totpKey          optional base64 AES-256 key protecting the TOTP secrets at rest (else file license-totp.key in secretsDir)
 * @param requireTotp      true = an OWNER account without TOTP can read but not change anything
 * @param defaultGraceDays grace period after the end date of a new licence
 * @param defaultTransferCap transfers allowed per licence and year, for a new licence
 * @param ledgerKeys       public keys trusted to sign a ledger file: "kid:tool:base64(raw 32-byte public key)", tool = desktop|phone
 * @param maxImportBytes   size limit of an imported ledger file
 * @param maxImportEntries entries limit of an imported ledger file
 * @param burstPer10Min    issuances per licence in 10 minutes above which an alert is raised
 */
@ConfigurationProperties(prefix = "castbridge.licenses")
public record LicenseProperties(
        boolean enabled,
        boolean publicRoutes,
        String trialIssuance,
        Path secretsDir,
        String signingKeyFile,
        String totpKey,
        Boolean requireTotp,
        Integer defaultGraceDays,
        Integer defaultTransferCap,
        List<String> ledgerKeys,
        Integer maxImportBytes,
        Integer maxImportEntries,
        Integer burstPer10Min) {

    public LicenseProperties {
        if (trialIssuance == null || !trialIssuance.equals("automatic")) trialIssuance = "manual";
        if (secretsDir == null) secretsDir = Path.of("/run/secrets");
        if (signingKeyFile == null || signingKeyFile.isBlank()) signingKeyFile = "license-signing.key";
        if (requireTotp == null) requireTotp = true;
        if (defaultGraceDays == null) defaultGraceDays = 14;
        if (defaultTransferCap == null) defaultTransferCap = 2;
        if (ledgerKeys == null) ledgerKeys = List.of();
        if (maxImportBytes == null) maxImportBytes = 5_000_000;
        if (maxImportEntries == null) maxImportEntries = 5000;
        if (burstPer10Min == null) burstPer10Min = 10;
    }
}
