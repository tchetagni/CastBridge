package castbridge.server.tunnel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The exact option strings (OpenSSH syntax), the atomic write and the strict key parser. No server, no database. */
class AuthorizedKeysTest {
    static final String KEY = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIGYxcpJQWlZ3q3m5m0m6o5l2aW9PqkEcG1Y0o3m1Z9q8";

    @Test void exactLines() {
        assertEquals("restrict,port-forwarding,permitlisten=\"127.0.0.1:22100\",command=\"/bin/false\" " + KEY + " tv-ABCD-EFGH-JKMN-PQRS",
                AuthorizedKeys.tvLine(22100, KEY, "ABCD-EFGH-JKMN-PQRS"));
        assertEquals("restrict,port-forwarding,permitopen=\"127.0.0.1:22100\",permitopen=\"127.0.0.1:22103\" " + KEY + " expert-alice",
                AuthorizedKeys.expertLine("alice", KEY, List.of(22100, 22103)));
        // never an empty permitopen list (that would mean « everywhere »)
        assertEquals("restrict,port-forwarding,permitopen=\"127.0.0.1:1\" " + KEY + " expert-alice", AuthorizedKeys.expertLine("alice", KEY, List.of()));
        assertEquals("# h\nl1\nl2\n", AuthorizedKeys.file("h", List.of("l1", "l2")));
    }

    @Test void atomicWriteReplacesWholeFileAndLeavesNoTemporaryFile(@org.junit.jupiter.api.io.TempDir Path dir) throws Exception {
        Path f = dir.resolve("sub").resolve("authorized_keys");
        assertTrue(AuthorizedKeys.writeAtomic(f, "one\n"));
        assertEquals("one\n", Files.readString(f));
        assertFalse(AuthorizedKeys.writeAtomic(f, "one\n"), "identical content: untouched");
        assertTrue(AuthorizedKeys.writeAtomic(f, "two\n"));
        assertEquals("two\n", Files.readString(f));
        try (var st = Files.list(f.getParent())) {
            assertEquals(List.of("authorized_keys"), st.map(p -> p.getFileName().toString()).toList());
        }
        if (Files.getFileStore(f).supportsFileAttributeView("posix")) assertEquals("rw-r--r--", java.nio.file.attribute.PosixFilePermissions.toString(Files.getPosixFilePermissions(f)));
        // readers running during rewrites only ever see a complete version
        Thread w = new Thread(() -> {
            try {
                for (int i = 0; i < 200; i++) AuthorizedKeys.writeAtomic(f, ("v" + i + "\n").repeat(500));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        w.start();
        while (w.isAlive()) {
            String s = Files.readString(f);
            assertTrue(s.equals("two\n") || s.lines().distinct().count() == 1, "never a mix of two versions");
        }
    }

    @Test void sshKeyParserIsStrict() {
        assertEquals(KEY, SshKeys.normalize(KEY + " mon@pc"));
        assertEquals(KEY, SshKeys.normalize("  " + KEY + "\n"));
        assertNull(SshKeys.normalize(null));
        assertNull(SshKeys.normalize("ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQ"));
        assertNull(SshKeys.normalize(KEY + "\nrestrict " + KEY));
        assertNull(SshKeys.normalize("command=\"x\" " + KEY));
        assertNull(SshKeys.normalize("ssh-ed25519 AAAAB3NzaC1yc2EAAAADAQABAAABAQ"));
        // a blob that is long enough but not an ed25519 key
        assertNull(SshKeys.normalize("ssh-ed25519 " + "A".repeat(68)));
    }
}
