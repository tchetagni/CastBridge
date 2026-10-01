package castbridge.server.licenses;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wiring of the signer: provisional encoder + server key + the fixed server scope. */
@Configuration
public class LicenseBeans {

    @Bean
    ActivationEncoder activationEncoder() { return new ProvisionalActivationEncoder(); }

    @Bean
    ScopedActivationSigner activationSigner(LicenseKeyring keyring, ActivationEncoder encoder) {
        return ScopedActivationSigner.server(new Ed25519ActivationSigner(keyring, encoder));
    }

    @Bean
    Clock licenseClock() { return Clock.systemUTC(); }
}
