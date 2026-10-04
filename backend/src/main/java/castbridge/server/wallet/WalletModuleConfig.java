package castbridge.server.wallet;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.Clock;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Module « portefeuille » (W22) : éteint par défaut dans le code ({@code castbridge.wallet.enabled=false}, allumé à la livraison par {@code CASTBRIDGE_WALLET_ENABLED=1}).
 * Éteint : aucun bean du module n'existe (chacun porte {@link Enabled}), donc aucune route (404). Les tables restent (additives) : le retour arrière est d'éteindre le module.
 */
@Configuration
@WalletModuleConfig.Enabled
@EnableConfigurationProperties(WalletProperties.class)
public class WalletModuleConfig {

    /** Interrupteur du module, à poser sur chaque bean du module (contrôleurs et services). */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @ConditionalOnProperty(prefix = "castbridge.wallet", name = "enabled", havingValue = "true")
    public @interface Enabled {}

    /** Horloge du module (UTC). Seul point où le temps entre : les tests la figent, aucune route ne le peut. */
    public static class WalletClock {
        private volatile Instant frozen;

        public Instant now() {
            Instant f = frozen;
            return f != null ? f : Clock.systemUTC().instant();
        }

        /** Pour les tests : fige l'heure. */
        public void freezeAt(Instant t) { frozen = t; }

        public void unfreeze() { frozen = null; }
    }

    @Bean
    WalletClock walletClock() { return new WalletClock(); }

    /** Clé « portefeuille » (fichier secret) : absente ou invalide = le signataire est éteint, les routes de l'appareil répondent 503. L'ancienne clé reste acceptée en vérification (rotation). */
    @Bean
    SnapshotSigner snapshotSigner(WalletProperties props) { return new SnapshotSigner(WalletKey.fromFile(props.keyFile()), WalletKey.fromFile(props.previousKeyFile())); }

    @Bean
    EditionReader editionReader(WalletProperties props) { return EditionReader.of(props); }
}
