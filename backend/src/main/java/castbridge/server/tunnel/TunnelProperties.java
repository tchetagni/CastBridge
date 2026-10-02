package castbridge.server.tunnel;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

/**
 * Remote administration by SSH reverse tunnels (CASTBRIDGE_TUNNEL_*, docs/REMOTE-TUNNEL.md). OFF by default: nothing is exposed (404) until the owner turns it on
 * once the sshd side (ops/tunnel) is in place.
 *
 * @param enabled                   feature switch of /api/v1/tunnel/** and of the admin page « Tunnels TV »
 * @param host                      public host name given to the TVs and shown in the ssh commands
 * @param sshPort                   port of the dedicated sshd listener the TVs and the experts connect to
 * @param portRange                 "low-high": one port of this range per TV, on 127.0.0.1 of the server
 * @param hostKeyFingerprint        "SHA256:…" of the sshd host key (or the path of a file holding it); empty = not sent to the TVs
 * @param authorizedKeysFile        authorized_keys of the account {@code cbtunnel} (one line per TV); default {storage-dir}/tunnel/authorized_keys
 * @param expertsFile               experts.json signed offline by the owner; default {storage-dir}/tunnel/experts.json
 * @param expertsAuthorizedKeysFile authorized_keys of the experts account; default {storage-dir}/tunnel/experts_authorized_keys
 * @param probeSeconds              period of the probe of the tunnel ports (5 minimum, default 60)
 * @param probeHost                 where the forwarded ports are probed (default 127.0.0.1)
 * @param listeningPortsFile        optional: file with the listening TCP ports of the host (one per line, written by ops/tunnel/listening.sh), used instead of a TCP
 *                                  connect when the backend runs in a container that cannot reach the host loopback
 * @param enrollPerHour             enrolment attempts per IP and hour
 * @param expertUser                unix account of the experts (only used in the ssh commands shown)
 */
@ConfigurationProperties(prefix = "castbridge.tunnel")
public record TunnelProperties(
        boolean enabled,
        String host,
        Integer sshPort,
        String portRange,
        String hostKeyFingerprint,
        Path authorizedKeysFile,
        Path expertsFile,
        Path expertsAuthorizedKeysFile,
        Integer probeSeconds,
        String probeHost,
        Path listeningPortsFile,
        Integer enrollPerHour,
        String expertUser) {

    public static final int DEFAULT_LOW = 22100, DEFAULT_HIGH = 22999;

    public TunnelProperties {
        if (host == null || host.isBlank()) host = "bridge.sti-cm.com";
        if (sshPort == null || sshPort < 1 || sshPort > 65535) sshPort = 2200;
        if (portRange == null || portRange.isBlank()) portRange = DEFAULT_LOW + "-" + DEFAULT_HIGH;
        if (hostKeyFingerprint == null) hostKeyFingerprint = "";
        if (probeSeconds == null || probeSeconds < 5) probeSeconds = 60;
        if (probeHost == null || probeHost.isBlank()) probeHost = "127.0.0.1";
        if (enrollPerHour == null || enrollPerHour < 1) enrollPerHour = 30;
        if (expertUser == null || !expertUser.matches("[a-z_][a-z0-9_-]{0,31}")) expertUser = "cbexpert";
    }

    /** {low, high} of the port range; an invalid setting falls back to the default range. */
    public int[] range() {
        try {
            String[] p = portRange.trim().split("-");
            int lo = Integer.parseInt(p[0].trim()), hi = p.length == 1 ? lo : Integer.parseInt(p[1].trim());
            if (p.length <= 2 && lo >= 1024 && hi <= 65535 && lo <= hi) return new int[] {lo, hi};
        } catch (RuntimeException e) {
            // fall through
        }
        return new int[] {DEFAULT_LOW, DEFAULT_HIGH};
    }

    @Configuration
    @EnableConfigurationProperties(TunnelProperties.class)
    static class Wiring {}

    /** Template switch: {@code ${@tunnelFeature.enabled()}}. */
    @Component("tunnelFeature")
    public static class Feature {
        private final TunnelProperties props;

        public Feature(TunnelProperties props) { this.props = props; }

        public boolean enabled() { return props.enabled(); }
    }
}
