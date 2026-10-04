package castbridge.server.activations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Rules of the wave that a test can read straight from the source of the module (the design forbids them, so a regression shows here). */
class SourceRulesTest {
    static final Path DIR = Path.of("src", "main", "java", "castbridge", "server", "activations");

    private List<Path> sources() throws IOException {
        try (Stream<Path> s = Files.list(DIR)) {
            return s.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    private List<String> hits(Pattern p) throws IOException {
        List<String> bad = new ArrayList<>();
        for (Path f : sources()) {
            int n = 0;
            for (String line : Files.readAllLines(f)) {
                n++;
                String t = line.trim();
                if (t.startsWith("*") || t.startsWith("/*") || t.startsWith("//")) continue;   // comments may name what the code must not do
                String code = line.replaceAll("//.*$", "");
                if (p.matcher(code).find()) bad.add(f.getFileName() + ":" + n + " " + line.trim());
            }
        }
        return bad;
    }

    @Test
    void theModuleExists() throws IOException {
        assertTrue(sources().size() >= 20);
    }

    @Test
    void neverAWriteToTheLicenceTables() throws IOException {
        assertEquals(List.of(), hits(Pattern.compile("(?i)(insert\\s+into|update|delete\\s+from|merge\\s+into|alter\\s+table|drop\\s+table|truncate)\\s+lic_")));
    }

    @Test
    void noOffsetPaginationAndNoCountOfTheEventTable() throws IOException {
        assertEquals(List.of(), hits(Pattern.compile("(?i)\\boffset\\b")));
        assertEquals(List.of(), hits(Pattern.compile("(?i)count\\s*\\(\\s*\\*\\s*\\)\\s+from\\s+act_event")));
    }

    @Test
    void nothingIsLoggedWithATokenOrAKeyInIt() throws IOException {
        // the module logs counts and identifiers only: no log call may mention a token, a key, a challenge or a code
        assertEquals(List.of(), hits(Pattern.compile("(?i)log\\.(info|warn|error|debug|trace)\\s*\\([^;]*\\b(token|bearer|challenge|secret|password|deviceCode|device_code|text)\\b")));
    }

    @Test
    void theMigrationIsV65AndTouchesNoExistingTable() throws IOException {
        Path v65 = Files.list(Path.of("src", "main", "resources", "db", "migration")).filter(p -> p.getFileName().toString().startsWith("V65__")).findFirst().orElseThrow();
        String sql = Files.readString(v65);
        assertFalse(Pattern.compile("(?i)alter\\s+table\\s+(?!act_|adm_)").matcher(sql).find(), "V65 only creates its own tables");
        for (var m = Pattern.compile("(?i)create\\s+table\\s+(\\w+)").matcher(sql); m.find(); ) {
            assertTrue(m.group(1).startsWith("act_") || m.group(1).equals("adm_read_audit"), m.group(1));
        }
        assertFalse(Pattern.compile("(?i)(insert\\s+into|update|delete\\s+from)\\s+(?!act_)").matcher(sql).find());
    }
}
