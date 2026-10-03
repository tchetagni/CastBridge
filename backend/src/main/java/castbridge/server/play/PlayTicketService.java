package castbridge.server.play;

import castbridge.server.devices.Device;
import castbridge.server.licenses.DeviceIdentity;
import castbridge.server.web.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Issues {@code cbp1} tickets: {@code cbp1.<b64url payload>.<b64url Ed25519 signature>}, signature over {@code castbridge-play-ticket-v1\ncbp1.<payload>}. The payload is ONLY the
 * attestation of a registered, non-blocked device ({@code aud, deviceId, blocked, country, deviceCode, iat, exp, jti}, times in ms): NO edition and NO right (the main API does not
 * know the licence core; the {@code castbridge-play} service evaluates the {@code cbx1} activation the TV joins to its {@code create}). Life 600 s; {@code jti} = 128 random bits,
 * used ONCE by the service. At most {@code per-device-per-hour} tickets (20) per device, in memory, bounded.
 */
@Service
public class PlayTicketService {
    static final String PREFIX = "cbp1";
    static final String DOMAIN = "castbridge-play-ticket-v1\n";
    static final String AUDIENCE = "castbridge-play";
    static final long LIFE_MS = 600_000L;
    private static final long WINDOW_MS = 3_600_000L;
    private static final int MAX_DEVICES = 50_000;

    private final PlayTicketKey key;
    private final int perDevicePerHour;
    private final SecureRandom random;
    private final Map<String, ArrayDeque<Long>> issued = new HashMap<>();

    @Autowired
    public PlayTicketService(PlayTicketKey key, @Value("${castbridge.play.per-device-per-hour:20}") int perDevicePerHour) {
        this(key, perDevicePerHour, new SecureRandom());
    }

    /** Test constructor: a seeded random makes the golden ticket reproducible. */
    PlayTicketService(PlayTicketKey key, int perDevicePerHour, SecureRandom random) {
        this.key = key;
        this.perDevicePerHour = Math.max(1, perDevicePerHour);
        this.random = random;
    }

    public boolean enabled() { return key.enabled(); }

    public record Issued(String ticket, long expiresAt, int ttlSeconds) {}

    /** [now] in ms. Throws {@link ApiException}: 503 no key, 403 blocked device, 400 bad device code, 429 over the hourly limit. */
    public Issued issue(Device device, String deviceCode, long now) {
        if (!key.enabled()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Ticket désactivé : le jeu en ligne n'est pas disponible sur ce serveur");
        if (device.blocked) throw new ApiException(HttpStatus.FORBIDDEN, "Cet appareil est bloqué par l'administrateur");
        String code = DeviceIdentity.parseCode(deviceCode);
        if (code == null) throw ApiException.badRequest("Code d'appareil invalide : 16 caractères au format XXXX-XXXX-XXXX-XXXX, avec son caractère de contrôle");
        reserve(device.publicId, now);

        byte[] jti = new byte[16];
        random.nextBytes(jti);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("aud", AUDIENCE);
        payload.put("deviceId", device.publicId);
        payload.put("blocked", false);
        payload.put("country", device.country == null ? "" : device.country);
        payload.put("deviceCode", code);
        payload.put("iat", now);
        payload.put("exp", now + LIFE_MS);
        payload.put("jti", HexFormat.of().formatHex(jti));
        String b64 = Base64.getUrlEncoder().withoutPadding().encodeToString(json(payload).getBytes(StandardCharsets.UTF_8));
        byte[] sig = key.sign((DOMAIN + PREFIX + "." + b64).getBytes(StandardCharsets.US_ASCII));
        return new Issued(PREFIX + "." + b64 + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(sig), now + LIFE_MS, (int) (LIFE_MS / 1000));
    }

    /** Counts one ticket for the device; 429 when it already had [perDevicePerHour] in the last hour. Memory bounded: at most [MAX_DEVICES] devices (swept), full = refuse. */
    private synchronized void reserve(String deviceId, long now) {
        if (issued.size() >= MAX_DEVICES && !issued.containsKey(deviceId)) {
            issued.values().forEach(q -> { while (!q.isEmpty() && now - q.peekFirst() >= WINDOW_MS) q.pollFirst(); });
            issued.values().removeIf(ArrayDeque::isEmpty);
            if (issued.size() >= MAX_DEVICES) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Trop de demandes de ticket : réessayez dans quelques minutes");
        }
        ArrayDeque<Long> q = issued.computeIfAbsent(deviceId, k -> new ArrayDeque<>());
        while (!q.isEmpty() && now - q.peekFirst() >= WINDOW_MS) q.pollFirst();
        if (q.size() >= perDevicePerHour) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Trop de parties ouvertes en une heure : réessayez dans un moment");
        q.addLast(now);
    }

    /** Hand-written JSON of simple values (no library: field order is the signed order, and no user text is ever put in it). */
    private static String json(Map<String, Object> m) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : m.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(e.getKey()).append("\":");
            Object v = e.getValue();
            if (v instanceof String s) sb.append('"').append(s.replace("\\", "\\\\").replace("\"", "\\\"").replaceAll("[\\p{Cntrl}]", "")).append('"');
            else sb.append(v);
        }
        return sb.append('}').toString();
    }
}
