package castbridge.server.licenses;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;

/**
 * PROVISIONAL encoder, standing in for docs/ACTIVATION-FORMAT.md which is not published yet (branch claude/trial-edition).
 * It signs a canonical text and renders it as "CBP0.&lt;base32 payload&gt;.&lt;base32 signature&gt;" in groups of 5. No TV or phone
 * accepts it: it lets the whole server flow (seats, idempotence, audit, scope) be tested on staging. To replace by the
 * encoder built on {@code ActivationIssuer} of core: nothing else in the module changes.
 */
public final class ProvisionalActivationEncoder implements ActivationEncoder {
    public static final String NAME = "provisoire (CBP0) : aucun appareil ne l'accepte, en attente de docs/ACTIVATION-FORMAT.md";

    @Override
    public byte[] payload(ActivationSigner.ActivationRequest r, String kid) {
        List<String> products = new ArrayList<>(r.productIds() == null ? List.of() : r.productIds());
        Collections.sort(products);
        String s = String.join("|", "CBP0", kid, r.licenseId(), r.deviceCode(), r.kind().name(), String.join(",", products),
                Long.toString(r.issuedAt().getEpochSecond()), r.expiresAt() == null ? "-" : Long.toString(r.expiresAt().getEpochSecond()),
                HexFormat.of().formatHex(r.nonce()), Integer.toString(r.seatsAllowed()));
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String render(ActivationSigner.ActivationRequest r, String kid, byte[] payload, byte[] signature) {
        return "CBP0." + group(Hashing.base32(payload)) + "." + group(Hashing.base32(signature));
    }

    @Override
    public String formatName() { return NAME; }

    private static String group(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i += 5) {
            if (i > 0) sb.append('-');
            sb.append(s, i, Math.min(s.length(), i + 5));
        }
        return sb.toString();
    }
}
