package castbridge.server.wallet;

import castbridge.server.licenses.LicenseKeyring;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/**
 * Preuve de possession de la TV (audit M5) : une {@code cbx1} copiée ne suffit pas pour lier une identité à un appareil. La TV signe, avec sa clé d'installation (Ed25519, créée une fois, jamais
 * quittée), le message {@code castbridge-wallet-bind-v1 \n code \n identifiant public de l'appareil API \n heure(ms)} : la preuve est liée à CE code, à CET appareil API (rejouée depuis un autre
 * appareil, elle ne vaut rien) et à l'heure du serveur (± 5 minutes). Corps de la synchronisation : {@code "bind": {"key": <clé publique brute, base64>, "at": <ms>, "sig": <base64>}}.
 * <p>Limite connue : la clé n'est pas encore liée à l'activation (w22-14) ; la liaison au premier appareil qui PROUVE reste donc un « premier venu », mais un premier venu qui détient une clé,
 * réaffectable par l'administrateur (TOTP). Ce n'est plus « n'importe qui avec la copie d'un fichier ».
 */
public final class BindProof {
    public static final String DOMAIN = "castbridge-wallet-bind-v1";
    public static final long WINDOW_MS = 5 * 60_000L;

    /** @param key clé publique brute (32 octets) en base64 ; c'est aussi la forme retenue en base. */
    public record Proof(String key, long at, String sig) {}

    private BindProof() {}

    /** La preuve du corps de la requête, ou {@code null} si absente ou mal formée. */
    public static Proof parse(JsonNode node) {
        if (node == null || !node.isObject()) return null;
        JsonNode key = node.path("key"), at = node.path("at"), sig = node.path("sig");
        if (!key.isTextual() || !sig.isTextual() || !at.canConvertToLong() || !at.isIntegralNumber()) return null;
        try {
            return new Proof(Base64.getEncoder().encodeToString(Base64.getDecoder().decode(key.asText())), at.asLong(), sig.asText());   // forme canonique : la même clé s'écrit toujours pareil
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Vrai si la signature est celle de la clé annoncée sur ce code et cet appareil, à l'heure du serveur ± 5 minutes. */
    public static boolean valid(Proof p, String code, String devicePublicId, Instant now) {
        if (p == null || devicePublicId == null || Math.abs(now.toEpochMilli() - p.at()) > WINDOW_MS) return false;
        try {
            byte[] pub = Base64.getDecoder().decode(p.key());
            byte[] sig = Base64.getDecoder().decode(p.sig());
            if (pub.length != 32 || sig.length != 64) return false;
            String msg = DOMAIN + "\n" + code + "\n" + devicePublicId + "\n" + p.at();
            return LicenseKeyring.verify(pub, msg.getBytes(StandardCharsets.UTF_8), sig);
        } catch (RuntimeException e) {
            return false;
        }
    }
}
