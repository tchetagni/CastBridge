package castbridge.server.tunnel;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The authorized_keys files read by the host sshd (through ops/tunnel/cb-authkeys.sh, so that the owner of the file does not matter). Lines are built here only from
 * validated pieces (a normalized key, a canonical device code, a port, an expert id matching {@code [a-z0-9][a-z0-9_-]{0,31}}).
 *
 * <p>OpenSSH syntax: {@code restrict} switches every restriction on, {@code port-forwarding} gives back the forwarding only; {@code permitlisten} limits the remote
 * forward (ssh -R) of a TV to its own loopback port; {@code permitopen} limits the direct-tcpip channels of an expert (ssh -J / -L), ONE host:port per option because a
 * port range is not supported; {@code command="/bin/false"} removes any shell or command.
 */
public final class AuthorizedKeys {
    /** sshd (before OpenSSH 8.8) ignores a line longer than this: a warning is logged when an experts line gets close. */
    public static final int OLD_SSHD_LINE_LIMIT = 16_384;
    /** Used when no TV is enrolled: a permitopen list must never be empty (an absent permitopen means « everything »). */
    public static final String NOWHERE = "127.0.0.1:1";

    private AuthorizedKeys() {}

    public static String tvLine(int port, String key, String deviceCode) {
        return "restrict,port-forwarding,permitlisten=\"127.0.0.1:" + port + "\",command=\"/bin/false\" " + key + " tv-" + deviceCode;
    }

    public static String expertLine(String id, String key, List<Integer> ports) {
        String open = ports.isEmpty() ? "permitopen=\"" + NOWHERE + "\""
                : ports.stream().map(p -> "permitopen=\"127.0.0.1:" + p + "\"").collect(Collectors.joining(","));
        return "restrict,port-forwarding," + open + " " + key + " expert-" + id;
    }

    public static String file(String header, List<String> lines) {
        StringBuilder sb = new StringBuilder("# ").append(header).append('\n');
        for (String l : lines) sb.append(l).append('\n');
        return sb.toString();
    }

    /**
     * Writes the content next to the target and renames it over it (atomic: sshd never reads half a file). Returns false when the file already holds exactly this content
     * (nothing is touched). The mode is 0644 (public keys only; sshd runs the reader as another user).
     */
    public static boolean writeAtomic(Path target, String content) throws IOException {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        if (Files.isRegularFile(target) && java.util.Arrays.equals(Files.readAllBytes(target), bytes)) return false;
        Path dir = target.toAbsolutePath().getParent();
        Files.createDirectories(dir);
        Path tmp = Files.createTempFile(dir, "." + target.getFileName() + ".", ".tmp");
        try {
            try (FileChannel ch = FileChannel.open(tmp, StandardOpenOption.WRITE)) {
                ch.write(java.nio.ByteBuffer.wrap(bytes));
                ch.force(true);
            }
            try {
                Files.setPosixFilePermissions(tmp, PosixFilePermissions.fromString("rw-r--r--"));
            } catch (UnsupportedOperationException e) {
                // not a POSIX file system
            }
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
        return true;
    }
}
