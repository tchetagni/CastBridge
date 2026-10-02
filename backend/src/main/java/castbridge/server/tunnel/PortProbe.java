package castbridge.server.tunnel;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Is something listening on a tunnel port? A TCP connect to the probe host, or, from a container, the list of listening ports written by the host (ops/tunnel/listening.sh). */
@Component
public class PortProbe {
    private final TunnelProperties props;

    public PortProbe(TunnelProperties props) { this.props = props; }

    /** One snapshot per probe round, so that the file is read once. */
    public java.util.function.IntPredicate snapshot() {
        Path f = props.listeningPortsFile();
        if (f != null && !f.toString().isBlank()) {
            Set<Integer> ports;
            try {
                ports = Files.readAllLines(f).stream().map(String::trim).filter(s -> s.matches("[0-9]{1,5}")).map(Integer::parseInt).collect(Collectors.toSet());
            } catch (IOException e) {
                ports = Set.of();
            }
            Set<Integer> p = ports;
            return p::contains;
        }
        return this::connect;
    }

    private boolean connect(int port) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(props.probeHost(), port), 700);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
