package castbridge.server.wallet;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/**
 * Réglages du module portefeuille ({@code CASTBRIDGE_WALLET_*}). Aucun secret ici : la clé « portefeuille » est un FICHIER ({@code key-file}), jamais une valeur.
 *
 * @param enabled                    interrupteur du module (éteint par défaut dans le code)
 * @param keyFile                    clé Ed25519 « portefeuille » (PEM PKCS#8 ou base64 de la graine de 32 octets) ; absente = routes de l'appareil en 503
 * @param previousKeyFile            ancienne clé, encore acceptée en vérification pendant une rotation (deux {@code kid} acceptés)
 * @param trustedKeys                clés publiques des émetteurs d'activations, au format des licences : {@code nom:base64 de la clé brute:PORTEE+PORTEE,…}
 * @param revocationsFile            facultatif : fichier de révocations signé (un jeton {@code cbx1} de type révocation par ligne)
 * @param writesPerMinutePerIdentity écritures par minute et par identité (défaut 30)
 * @param writesPerMinuteGlobal      écritures par minute, toutes identités confondues (défaut 600)
 * @param requireBindProof           la première liaison d'une identité à un appareil exige la preuve de possession d'une clé d'installation de la TV (défaut VRAI, audit M5) ; faux = ancien
 *                                   comportement (liaison au premier venu), à réserver aux TV qui ne signent pas encore (démonstration)
 */
@ConfigurationProperties(prefix = "castbridge.wallet")
public record WalletProperties(boolean enabled, String keyFile, String previousKeyFile, List<String> trustedKeys, String revocationsFile, Integer writesPerMinutePerIdentity,
                               Integer writesPerMinuteGlobal, Boolean requireBindProof) {
    public WalletProperties(boolean enabled, String keyFile, String previousKeyFile, List<String> trustedKeys, String revocationsFile, Integer writesPerMinutePerIdentity,
                            Integer writesPerMinuteGlobal) {
        this(enabled, keyFile, previousKeyFile, trustedKeys, revocationsFile, writesPerMinutePerIdentity, writesPerMinuteGlobal, null);
    }

    @ConstructorBinding
    public WalletProperties {
        if (requireBindProof == null) requireBindProof = true;
        if (keyFile == null) keyFile = "";
        if (previousKeyFile == null) previousKeyFile = "";
        trustedKeys = trustedKeys == null ? List.of() : trustedKeys.stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
        if (revocationsFile == null) revocationsFile = "";
        if (writesPerMinutePerIdentity == null || writesPerMinutePerIdentity < 1) writesPerMinutePerIdentity = 30;
        if (writesPerMinuteGlobal == null || writesPerMinuteGlobal < 1) writesPerMinuteGlobal = 600;
    }
}
