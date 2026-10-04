package castbridge.server.play;

import castbridge.server.devices.Device;
import castbridge.server.licenses.DeviceIdentity;
import castbridge.server.web.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
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
    private final int perAddressPerHour;
    private final int maxDevices;
    /** Per device: its tickets of the last hour. Access-ordered: when full, the LEAST recently used device is evicted (never a refusal for everybody, audit I2). */
    private final Map<String, ArrayDeque<Long>> issued = new LinkedHashMap<>(16, 0.75f, true);
    private final Map<String, ArrayDeque<Long>> perAddress = new LinkedHashMap<>(16, 0.75f, true);
    /** Sticky link (audit B1): the first device code seen for a device, and the devices a code was seen with in the last 24 h. */
    private final Map<String, String> firstCode = new LinkedHashMap<>(16, 0.75f, true);
    private final Map<String, Map<String, Long>> codeDevices = new LinkedHashMap<>(16, 0.75f, true);
    static final int MAX_DEVICES_PER_CODE = 2;
    private static final long DAY_MS = 86_400_000L;

    @Autowired
    public PlayTicketService(PlayTicketKey key, @Value("${castbridge.play.per-device-per-hour:20}") int perDevicePerHour,
                             @Value("${castbridge.play.per-address-per-hour:120}") int perAddressPerHour) {
        this(key, perDevicePerHour, perAddressPerHour, new SecureRandom());
    }

    /** Test constructor: a seeded random makes the golden ticket reproducible. */
    PlayTicketService(PlayTicketKey key, int perDevicePerHour, SecureRandom random) { this(key, perDevicePerHour, 1_000_000, random); }

    PlayTicketService(PlayTicketKey key, int perDevicePerHour, int perAddressPerHour, SecureRandom random) { this(key, perDevicePerHour, perAddressPerHour, random, MAX_DEVICES); }

    PlayTicketService(PlayTicketKey key, int perDevicePerHour, int perAddressPerHour, SecureRandom random, int maxDevices) {
        this.maxDevices = maxDevices;
        this.key = key;
        this.perAddressPerHour = Math.max(1, perAddressPerHour);
        this.perDevicePerHour = Math.max(1, perDevicePerHour);
        this.random = random;
    }

    public boolean enabled() { return key.enabled(); }

    public record Issued(String ticket, long expiresAt, int ttlSeconds) {}

    /** [now] in ms. Throws {@link ApiException}: 503 no key, 403 blocked device, 400 bad device code, 429 over the hourly limit. */
    public Issued issue(Device device, String deviceCode, long now) { return issue(device, deviceCode, now, null); }

    /** [address] = client address (null: no per-address limit). Also 403 for a non-TV device, 403 for a code already linked to more than 2 devices in 24 h or not the device's first code. */
    public Issued issue(Device device, String deviceCode, long now, String address) { return issue(device, deviceCode, now, address, null); }

    /**
     * [installKey] = the TV install key (raw Ed25519 public key, 32 bytes, base64; audit Opus H-3). The first request for a code PINS code to key (in memory, like the device link); another key, or no key once pinned,
     * is a 403; the ticket then carries {@code ik} = SHA-256 (hex) of the key, and the play service requires a proof of possession bound to the ticket. A malformed key is a 400. Null: legacy TV (no pin, no {@code ik}).
     */
    public Issued issue(Device device, String deviceCode, long now, String address, String installKey) {
        if (!key.enabled()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Ticket désactivé : le jeu en ligne n'est pas disponible sur ce serveur");
        if (device.blocked) throw new ApiException(HttpStatus.FORBIDDEN, "Cet appareil est bloqué par l'administrateur");
        String code = DeviceIdentity.parseCode(deviceCode);
        if (code == null) throw ApiException.badRequest("Code d'appareil invalide : 16 caractères au format XXXX-XXXX-XXXX-XXXX, avec son caractère de contrôle");
        if (!"tv".equals(device.app)) throw new ApiException(HttpStatus.FORBIDDEN, "Seule une TV CastBridge-TV peut ouvrir une salle en ligne");
        String ik = installKey == null ? null : installHash(installKey);
        checkPin(code, ik);                 // a refused key never uses up one of the code's two device slots
        link(device.publicId, code, now);
        commitPin(code, ik);                // pinned only once the request is otherwise accepted
        if (address != null) count(perAddress, addressKey(address), perAddressPerHour, now, "Trop de demandes de ticket depuis cette adresse : réessayez dans une heure");
        reserve(device.publicId, now);

        byte[] jti = new byte[16];
        random.nextBytes(jti);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("aud", AUDIENCE);
        payload.put("deviceId", device.publicId);
        payload.put("blocked", false);
        payload.put("country", device.country == null ? "" : device.country);
        payload.put("deviceCode", code);
        if (ik != null) payload.put("ik", ik);
        payload.put("iat", now);
        payload.put("exp", now + LIFE_MS);
        payload.put("jti", HexFormat.of().formatHex(jti));
        String b64 = Base64.getUrlEncoder().withoutPadding().encodeToString(json(payload).getBytes(StandardCharsets.UTF_8));
        byte[] sig = key.sign((DOMAIN + PREFIX + "." + b64).getBytes(StandardCharsets.US_ASCII));
        return new Issued(PREFIX + "." + b64 + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(sig), now + LIFE_MS, (int) (LIFE_MS / 1000));
    }

    /** Limit key of a client address: IPv4 as is, IPv6 reduced to its /64 (a subscriber gets at least a /64: otherwise one subscriber has 2^64 caps). A literal only: no DNS lookup. */
    static String addressKey(String address) {
        if (address == null || address.indexOf(':') < 0) return address;
        try {
            byte[] b = java.net.InetAddress.getByName(address).getAddress();
            return b.length == 4 ? java.net.InetAddress.getByAddress(b).getHostAddress() : "v6:" + HexFormat.of().formatHex(b, 0, 8);
        } catch (java.net.UnknownHostException | RuntimeException e) { return address; }
    }

    /** Counts one ticket for the device; 429 when it already had [perDevicePerHour] in the last hour. Bounded: at most [MAX_DEVICES] devices, the least recently used is evicted. */
    private synchronized void reserve(String deviceId, long now) { count(issued, deviceId, perDevicePerHour, now, "Trop de parties ouvertes en une heure : réessayez dans un moment"); }

    private void count(Map<String, ArrayDeque<Long>> table, String k, int max, long now, String message) {
        synchronized (this) {
            if (table.size() >= maxDevices && !table.containsKey(k)) {
                table.values().forEach(q -> { while (!q.isEmpty() && now - q.peekFirst() >= WINDOW_MS) q.pollFirst(); });
                table.values().removeIf(ArrayDeque::isEmpty);
                if (table.size() >= maxDevices) table.remove(table.keySet().iterator().next());   // LRU: the oldest-used key leaves, nobody is refused for it
            }
            ArrayDeque<Long> q = table.computeIfAbsent(k, x -> new ArrayDeque<>());
            while (!q.isEmpty() && now - q.peekFirst() >= WINDOW_MS) q.pollFirst();
            if (q.size() >= max) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, message);
            q.addLast(now);
        }
    }

    /** Sticky link: a device keeps the first code it showed; a code is linked to at most [MAX_DEVICES_PER_CODE] devices per 24 h (a copied activation does not spread). */
    private synchronized void link(String deviceId, String code, long now) {
        String first = firstCode.get(deviceId);
        if (first != null && !first.equals(code)) throw new ApiException(HttpStatus.FORBIDDEN, "Cet appareil a déjà annoncé un autre code d'appareil");
        Map<String, Long> seen = codeDevices.computeIfAbsent(code, x -> new LinkedHashMap<>());
        seen.values().removeIf(t -> now - t >= DAY_MS);
        if (!seen.containsKey(deviceId) && seen.size() >= MAX_DEVICES_PER_CODE) throw new ApiException(HttpStatus.FORBIDDEN, "Ce code d'appareil est déjà utilisé par d'autres appareils : contactez l'assistance");
        seen.put(deviceId, now);
        if (first == null) firstCode.put(deviceId, code);
        for (Map<String, ?> m : List.of(firstCode, codeDevices)) if (m.size() > maxDevices) m.remove(m.keySet().iterator().next());
    }

    /** Pinned install-key fingerprint per device code (audit Opus H-3). Bounded, least recently used leaves first; lost at restart (documented). */
    private final Map<String, String> pinned = new LinkedHashMap<>(16, 0.75f, true);

    /** SHA-256 (hex) of a raw 32-byte Ed25519 key given in base64; 400 for anything else. */
    static String installHash(String installKey) {
        byte[] raw;
        try { raw = Base64.getDecoder().decode(installKey.trim()); } catch (IllegalArgumentException e) { raw = null; }
        if (raw == null || raw.length != 32) throw ApiException.badRequest("Clé d'installation invalide : 32 octets en base64 attendus");
        try { return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(raw)); } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    private synchronized void checkPin(String code, String ik) {
        String have = pinned.get(code);
        if (have != null && ik == null) throw new ApiException(HttpStatus.FORBIDDEN, "Cette TV a déjà une clé d'installation : mettez CastBridge-TV à jour");
        if (have != null && !have.equals(ik)) throw new ApiException(HttpStatus.FORBIDDEN, "Ce code d'appareil est lié à une autre clé d'installation");
    }

    private synchronized void commitPin(String code, String ik) {
        if (ik != null && !pinned.containsKey(code)) { pinned.put(code, ik); if (pinned.size() > maxDevices) pinned.remove(pinned.keySet().iterator().next()); }
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
