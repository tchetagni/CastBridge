package castbridge.server.activations;

import castbridge.server.licenses.LicenseKeyring;
import castbridge.server.licenses.TrustedKeys;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Which tool a key id belongs to: DESK, PHONE, SERVER, AGENT, or UNKNOWN (a key outside the ring: an alert on its own). */
@Component
public class ToolDirectory {
    public static final Set<String> TYPES = Set.of("DESK", "PHONE", "SERVER", "AGENT", "UNKNOWN");

    private final TrustedKeys trusted;
    private final LicenseKeyring keyring;
    private final JdbcTemplate jdbc;

    public ToolDirectory(TrustedKeys trusted, LicenseKeyring keyring, JdbcTemplate jdbc) {
        this.trusted = trusted;
        this.keyring = keyring;
        this.jdbc = jdbc;
    }

    public String typeOf(String kid) {
        if (kid == null) return "UNKNOWN";
        if (keyring.present() && kid.equals(keyring.kid())) return "SERVER";
        TrustedKeys.Key k = trusted.find(kid);
        if (k != null) return JournalService.toolType(k, null);
        List<String> t = jdbc.queryForList("SELECT tool FROM act_tool WHERE kid = ?", String.class, kid);
        return t.isEmpty() ? "UNKNOWN" : t.get(0);
    }

    /** Every key id the module knows (ring, server key, tools that uploaded a journal). */
    public Set<String> knownKids() {
        Set<String> s = new TreeSet<>();
        trusted.all().forEach(k -> s.add(k.kid()));
        if (keyring.present()) s.add(keyring.kid());
        s.addAll(jdbc.queryForList("SELECT kid FROM act_tool", String.class));
        return s;
    }

    /** The key ids of one type of tool. */
    public List<String> kidsOf(String type) {
        List<String> out = new ArrayList<>();
        for (String kid : knownKids()) if (typeOf(kid).equals(type)) out.add(kid);
        return out;
    }
}
