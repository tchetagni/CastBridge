package castbridge.server;

import castbridge.server.config.CastbridgeProperties;
import java.time.ZoneId;
import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/** CastBridge server: app updates, quiz question bank, light device statistics. */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class) // no user store: bearer token only
@EnableScheduling
@EnableConfigurationProperties(CastbridgeProperties.class)
public class CastbridgeApplication {

    /** Every date shown to people is in Cameroon time; the database stores UTC. */
    public static final ZoneId ZONE = ZoneId.of("Africa/Douala");

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone(ZONE));
        SpringApplication.run(CastbridgeApplication.class, args);
    }
}
