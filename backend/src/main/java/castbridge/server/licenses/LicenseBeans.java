package castbridge.server.licenses;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wiring of the signer: the server key + the fixed server scope. */
@Configuration
public class LicenseBeans {

    @Bean
    ScopedActivationSigner activationSigner(LicenseKeyring keyring) {
        return ScopedActivationSigner.server(new Ed25519ActivationSigner(keyring));
    }

    @Bean
    Clock licenseClock() { return Clock.systemUTC(); }
}
