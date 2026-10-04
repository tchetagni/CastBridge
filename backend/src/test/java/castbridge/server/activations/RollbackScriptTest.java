package castbridge.server.activations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Audit w23-01 M1: tools/activations/rollback-V63.sql drops exactly the tables of the migration, each with IF EXISTS (it can be replayed), and clears the Flyway line of V63. */
class RollbackScriptTest {
    private static Set<String> names(String text, String regex) {
        Set<String> out = new LinkedHashSet<>();
        Matcher m = Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.MULTILINE).matcher(text);
        while (m.find()) out.add(m.group(1));
        return out;
    }

    @Test
    void theRollbackDropsExactlyTheTablesOfTheMigrationAndCanBeReplayed() throws IOException {
        String migration = Files.readString(Path.of("src", "main", "resources", "db", "migration", "V63__activation_tracking.sql"));
        String rollback = Files.readString(Path.of("..", "tools", "activations", "rollback-V63.sql"));
        Set<String> created = names(migration, "^CREATE TABLE (\\w+)");
        Set<String> dropped = names(rollback, "^DROP TABLE IF EXISTS (\\w+);");
        assertEquals(created, dropped, "every table the migration creates is dropped, and no other");
        assertTrue(created.size() >= 23, "19 tables of the design + the 4 of the audit fixes: " + created.size());
        assertEquals(0, names(rollback, "^DROP TABLE (?!IF EXISTS)(\\w+)").size(), "no DROP without IF EXISTS");
        assertTrue(rollback.contains("DELETE FROM flyway_schema_history WHERE version = '63'"));
        // reverse order of the creation (the migration has no foreign key today; if one is added, the order stays right)
        java.util.List<String> c = new java.util.ArrayList<>(created), d = new java.util.ArrayList<>(dropped);
        java.util.Collections.reverse(c);
        assertEquals(c.stream().sorted().toList(), d.stream().sorted().toList());
        assertTrue(rollback.indexOf("act_event_head") > rollback.indexOf("act_alert"), "the order is documented and stable");
    }
}
