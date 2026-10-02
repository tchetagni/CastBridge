package castbridge.server.licenses;

import castbridge.server.web.ApiException;
import java.util.Set;
import org.springframework.http.HttpStatus;

/**
 * Builds and signs the {@code cbx1} activation with the keyring key. It applies the rules of the format (docs/ACTIVATION-FORMAT.md § 10: window
 * 1..366 days, a trial carries no right and the licence "trial", a production carries at least one right, "tout ouvert" at most 30 days, well-formed
 * rights) but NOT the scope of the key: wrap it in {@link ScopedActivationSigner} (the bean does).
 */
public final class Ed25519ActivationSigner implements ActivationSigner {
    private final LicenseKeyring keyring;

    public Ed25519ActivationSigner(LicenseKeyring keyring) { this.keyring = keyring; }

    @Override
    public String kid() { return keyring.kid(); }

    @Override
    public Set<SignerScope> scopes() { return Set.of(SignerScope.values()); }

    @Override
    public SignedActivation sign(ActivationRequest r) {
        if (!keyring.present()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Aucune clé de signature serveur : déposez-la dans le dossier des secrets (voir docs/LICENSE-ADMIN.md)");
        }
        if (r.kind() != IssueKind.TRIAL && r.kind() != IssueKind.PRODUCTION) throw new ApiException(HttpStatus.FORBIDDEN, "Sorte d'activation non prise en charge par le serveur : " + r.kind());
        if (r.windowHours() < 1 || r.windowHours() > WireActivation.MAX_WINDOW_HOURS) throw ApiException.badRequest("Fenêtre d'installation : de 1 à " + WireActivation.MAX_WINDOW_HOURS + " heures");
        if (!r.subject().equals("tv") && !r.subject().equals("phone")) throw ApiException.badRequest("Type d'appareil : tv ou phone");
        if (!WireActivation.HEX.matcher(r.nonce()).matches()) throw ApiException.badRequest("Nonce invalide (8 à 64 chiffres hexadécimaux)");
        if (r.notBefore() > r.issuedAt()) throw ApiException.badRequest("Le début de la fenêtre ne peut pas dépasser la date d'émission");
        if (!WireActivation.ID.matcher(r.license()).matches()) throw ApiException.badRequest("Identifiant de licence invalide pour l'activation");
        if (r.kind() == IssueKind.TRIAL) {
            if (!r.rights().stream().allMatch(WireActivation::isUsage) || !r.license().equals(WireActivation.TRIAL_LICENSE)) throw ApiException.badRequest("Une clé d'essai ne porte aucun droit et sa licence est « trial »");
        } else if (r.rights().stream().allMatch(WireActivation::isUsage)) {
            throw ApiException.badRequest("Une activation de production porte au moins un droit");
        }
        for (String line : r.rights()) {
            if (!WireActivation.rightLineOk(line)) throw ApiException.badRequest("Droit mal formé : « " + AuditLog.clip(line, 60) + " »");
            String[] f = line.split("\\|");
            if (f[0].equals("openall")) {
                long d = Long.parseLong(f[3]) - Long.parseLong(f[2]);
                if (d <= 0 || d > WireActivation.MAX_OPEN_ALL_MS) throw ApiException.badRequest("« Tout ouvert » : 30 jours au plus");
            }
            if (f[0].equals("subscription") && Long.parseLong(f[5]) > 30 * WireActivation.DAY_MS) throw ApiException.badRequest("Abonnement : tolérance de 30 jours au plus");
        }
        String seat = r.seat() == null ? WireActivation.defaultSeat(r.license(), r.device().factors()) : r.seat();
        long notAfter = r.notBefore() + r.windowHours() * WireActivation.HOUR_MS;
        long seq = r.seq() == null ? r.issuedAt() : r.seq();
        if (seq < 0) throw ApiException.badRequest("Numéro de séquence invalide");
        var fields = new WireActivation.Fields(r.kind().name().toLowerCase(java.util.Locale.ROOT), r.subject(), keyring.kid(), seq, r.nonce(), r.issuedAt(), r.notBefore(), notAfter, r.license(), seat,
                r.device().k(), r.device().factors(), r.rights());
        String payload = WireActivation.payload(fields);
        String text = WireActivation.token(fields, keyring.sign(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        return new SignedActivation(text, keyring.kid(), r.nonce(), Hashing.sha256Hex(text), seat, r.notBefore(), notAfter, seq);
    }
}
