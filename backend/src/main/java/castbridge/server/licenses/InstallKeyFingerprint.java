package castbridge.server.licenses;

import java.util.Base64;
import java.util.HexFormat;

/**
 * L'empreinte lisible d'une clé publique d'installation (la clé Ed25519 qui signe la preuve {@code bind} de la TV) : SHA-256 de la clé brute (32 octets), 16 premiers octets, 8 groupes de 4
 * hexadécimaux séparés par « - ». Même forme sur l'écran de la TV, dans le texte « Demande d'appareil », dans les émetteurs (bureau, console, ligne de commande, serveur) et dans la
 * réaffectation de liaison du propriétaire : on compare à l'œil avant de signer ou de lier (second audit w23-05, MEDIUM-C et HIGH-A).
 */
public final class InstallKeyFingerprint {
    private InstallKeyFingerprint() {}

    /** Empreinte d'une clé publique brute de 32 octets (base64) ; {@code null} si ce n'est pas une telle clé. */
    public static String ofBase64(String publicKeyBase64) {
        if (publicKeyBase64 == null) return null;
        try {
            return ofRaw(Base64.getDecoder().decode(publicKeyBase64.trim()));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static String ofRaw(byte[] raw) {
        if (raw == null || raw.length != 32) return null;
        String hex = HexFormat.of().formatHex(Hashing.sha256(raw), 0, 16);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < hex.length(); i += 4) b.append(i == 0 ? "" : "-").append(hex, i, i + 4);
        return b.toString();
    }

    /** La forme canonique (32 hexadécimaux minuscules, sans séparateur) d'une empreinte saisie avec ou sans « - », espaces et majuscules ; {@code null} si ce n'en est pas une. */
    public static String normalize(String typed) {
        if (typed == null) return null;
        String s = typed.replaceAll("[\\s-]", "").toLowerCase(java.util.Locale.ROOT);
        return s.matches("[0-9a-f]{32}") ? s : null;
    }

    /** La forme canonique de l'empreinte d'une clé (base64 brute), ou {@code null}. */
    public static String canonicalOf(String publicKeyBase64) { return normalize(ofBase64(publicKeyBase64)); }
}
