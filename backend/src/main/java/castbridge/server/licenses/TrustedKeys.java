package castbridge.server.licenses;

import castbridge.server.licenses.ActivationSigner.SignerScope;
import java.util.Base64;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The public keys the server trusts to sign registry events, each with the scopes it may use (docs/ACTIVATION-FORMAT.md § 2): the offline
 * tools declared in {@code castbridge.licenses.trusted-keys} ("name:base64 raw public key:SCOPE+SCOPE") and the server's own key with its fixed
 * server scope. A key without an explicit valid scope list is ignored (never "all scopes by default").
 */
@Component
public class TrustedKeys {
    private static final Logger log = LoggerFactory.getLogger(TrustedKeys.class);

    public record Key(String kid, String name, byte[] publicKey, Set<SignerScope> scopes) {
        public boolean allows(SignerScope s) { return scopes.contains(s); }
    }

    private final Map<String, Key> byKid = new HashMap<>();

    public TrustedKeys(LicenseProperties props, LicenseKeyring keyring) {
        for (String entry : props.trustedKeys()) {
            try {
                String[] p = entry.trim().split(":");
                if (p.length != 3) throw new IllegalArgumentException("format");
                byte[] pub = Base64.getDecoder().decode(p[1].trim());
                if (pub.length != 32) throw new IllegalArgumentException("key size");
                Set<SignerScope> scopes = EnumSet.noneOf(SignerScope.class);
                for (String s : p[2].split("\\+")) scopes.add(SignerScope.valueOf(s.trim()));
                if (scopes.isEmpty()) throw new IllegalArgumentException("scopes");
                byKid.put(LicenseKeyring.kidOf(pub), new Key(LicenseKeyring.kidOf(pub), p[0].trim(), pub, scopes));
            } catch (RuntimeException e) {
                log.warn("licence module: a trusted key entry is ignored (expected name:base64 public key:SCOPE+SCOPE)");
            }
        }
        if (keyring.present()) {
            byKid.put(keyring.kid(), new Key(keyring.kid(), "server", Base64.getDecoder().decode(keyring.publicKeyBase64()), ScopedActivationSigner.SERVER_SCOPES));
        }
    }

    public Key find(String kid) { return byKid.get(kid); }

    public java.util.Collection<Key> all() { return java.util.List.copyOf(byKid.values()); }

    /** Name of the tool that owns this key (for the pages), or the kid. */
    public String nameOf(String kid) {
        Key k = byKid.get(kid);
        return k == null ? kid : k.name();
    }
}
