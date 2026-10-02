package castbridge.server.devices;

import castbridge.server.CastbridgeApplication;
import castbridge.server.config.CastbridgeProperties;
import castbridge.server.updates.ReleaseService;
import castbridge.server.web.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Device registry: registration (returns a device token), heartbeats, crash reports, admin follow-up and retention.
 *
 * <p>Identity: the app sends a random {@code installId} (new at each installation) and, when it can, the SHA-256 of
 * ANDROID_ID salted by the app ({@code androidIdHash}). A device is found again by the hash of that value (so a
 * reinstallation is attached to the same record, and listed in its installation history), else by its installId.
 * Each registration returns a new random token (only its SHA-256 is stored); heartbeats and crash reports must carry
 * it as {@code Authorization: Bearer}: a third party cannot send data in the name of a device without it.
 */
@Service
public class DeviceService {
    private static final Logger log = LoggerFactory.getLogger(DeviceService.class);
    private static final Pattern INSTALL_ID = Pattern.compile("[A-Za-z0-9-]{8,36}");
    private static final Pattern HEX64 = Pattern.compile("[0-9a-fA-F]{64}");
    public static final List<String> PLATFORMS = List.of("android-tv", "google-tv", "fire-os", "android-box", "phone", "tablet", "other");
    /** Heartbeats closer than this are counted but not stored in detail (an app restarting in a loop). */
    private static final long MIN_DETAIL_SECONDS = 60;
    private final SecureRandom random = new SecureRandom();

    private final DeviceRepository devices;
    private final InstallRepository installs;
    private final VersionRepository versions;
    private final HeartbeatRepository heartbeats;
    private final DailyRepository daily;
    private final CrashRepository crashes;
    private final GeoLocator geo;
    private final CastbridgeProperties props;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    public DeviceService(DeviceRepository devices, InstallRepository installs, VersionRepository versions, HeartbeatRepository heartbeats,
                         DailyRepository daily, CrashRepository crashes, GeoLocator geo, CastbridgeProperties props,
                         org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.devices = devices;
        this.installs = installs;
        this.versions = versions;
        this.heartbeats = heartbeats;
        this.daily = daily;
        this.crashes = crashes;
        this.geo = geo;
        this.props = props;
    }

    // ================================================================ device side

    public record Registration(String deviceId, String deviceToken, int heartbeatSeconds, Directives directives) {}

    /** What the server asks of the app in return of a heartbeat. */
    public record Directives(String serverTime, boolean checkUpdate, boolean blocked, String channel, int heartbeatSeconds) {}

    public record CrashReport(String message, String detail, Integer versionCode) {}

    @Transactional
    public Registration register(DeviceReport r, HttpServletRequest req) {
        List<String> errors = new ArrayList<>();
        if (r == null) throw ApiException.badRequest("Corps JSON attendu");
        if (r.installId() == null || !INSTALL_ID.matcher(r.installId()).matches()) errors.add("installId : UUID attendu");
        if (!ReleaseService.APPS.contains(r.app())) errors.add("app : « tv » ou « phone » attendu");
        if (r.androidIdHash() != null && !r.androidIdHash().isBlank() && !HEX64.matcher(r.androidIdHash()).matches())
            errors.add("androidIdHash : SHA-256 hexadécimal attendu (64 caractères)");
        if (!errors.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "Enregistrement refusé", errors);

        String idHash = r.androidIdHash() == null || r.androidIdHash().isBlank() ? null : sha256Hex(r.androidIdHash().toLowerCase(Locale.ROOT));
        Instant now = Instant.now();
        Optional<Device> known = idHash != null ? devices.findFirstByAppAndAndroidIdHashOrderByLastSeenDesc(r.app(), idHash) : Optional.empty();
        if (known.isEmpty()) known = devices.findFirstByAppAndInstallIdOrderByLastSeenDesc(r.app(), r.installId());
        Device d = known.orElseGet(() -> {
            Device n = new Device();
            n.publicId = UUID.randomUUID().toString();
            n.app = r.app();
            n.firstSeen = now;
            return n;
        });
        if (idHash != null) d.androidIdHash = idHash;
        d.installId = r.installId();
        String token = newToken();
        d.tokenHash = sha256Hex(token); // a new registration invalidates the previous token
        apply(d, r, req, now);
        d = devices.save(d);
        recordHistory(d, r, now);
        log.info("device {} registered ({}, {})", d.publicId, d.app, known.isPresent() ? "known" : "new");
        return new Registration(d.publicId, token, props.devices().heartbeatSeconds(), directives(d, now));
    }

    @Transactional
    public Directives heartbeat(String authorization, DeviceReport r, HttpServletRequest req) {
        Device d = authenticate(authorization).orElseThrow(DeviceService::unknownToken);
        Instant now = Instant.now();
        if (r != null) {
            if (r.app() != null && !r.app().equals(d.app)) throw ApiException.badRequest("app : ne correspond pas à l'appareil enregistré");
            if (r.installId() != null && INSTALL_ID.matcher(r.installId()).matches()) d.installId = r.installId();
            apply(d, r == null ? DeviceReport.empty() : r, req, now);
        } else {
            apply(d, DeviceReport.empty(), req, now);
        }
        Directives out = directives(d, now);
        d.forceUpdateCheck = false; // delivered once
        recordHistory(d, r == null ? DeviceReport.empty() : r, now);
        return out;
    }

    @Transactional
    public void crash(String authorization, CrashReport c) {
        Device d = authenticate(authorization).orElseThrow(DeviceService::unknownToken);
        if (c == null || c.message() == null || c.message().isBlank()) throw ApiException.badRequest("message : texte court attendu");
        DeviceRecords.Crash x = new DeviceRecords.Crash();
        x.deviceId = d.id;
        x.crashedAt = Instant.now();
        x.versionCode = c.versionCode() != null ? c.versionCode() : d.versionCode;
        x.message = cut(c.message(), 500);
        x.detail = cut(c.detail(), 4000);
        crashes.save(x);
        d.lastError = x.message;
        d.lastErrorAt = x.crashedAt;
    }

    /** The device owning this "Bearer <device token>" header, if any. */
    public Optional<Device> authenticate(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) return Optional.empty();
        String token = authorization.substring(7).trim();
        if (token.isEmpty() || token.length() > 100) return Optional.empty();
        return devices.findByTokenHash(sha256Hex(token));
    }

    /** 403 if the calling device (device token, or public id given as deviceId) is blocked by the admin. */
    public void refuseIfBlocked(String authorization, String deviceId) {
        Optional<Device> d = authenticate(authorization);
        if (d.isEmpty()) d = byPublicId(deviceId);
        if (d.isPresent() && d.get().blocked) throw new ApiException(HttpStatus.FORBIDDEN, "Cet appareil est bloqué par l'administrateur");
    }

    /** Blocked devices get no updates and no quiz; the effective channel may be forced by the admin (beta). */
    public Optional<Device> byPublicId(String publicId) {
        return publicId == null || publicId.length() != 36 ? Optional.empty() : devices.findByPublicId(publicId);
    }

    private Directives directives(Device d, Instant now) {
        return new Directives(now.atZone(CastbridgeApplication.ZONE).toOffsetDateTime().toString(), d.forceUpdateCheck, d.blocked,
                effectiveChannel(d), props.devices().heartbeatSeconds());
    }

    public static String effectiveChannel(Device d) {
        if (d.channelOverride != null) return d.channelOverride;
        return d.channel != null && ReleaseService.CHANNELS.contains(d.channel) ? d.channel : "stable";
    }

    private void apply(Device d, DeviceReport r, HttpServletRequest req, Instant now) {
        if (r.versionCode() != null && r.versionCode() > 0) d.versionCode = r.versionCode();
        if (r.versionName() != null) d.versionName = cut(r.versionName(), 64);
        if (r.channel() != null && ReleaseService.CHANNELS.contains(r.channel())) d.channel = r.channel();
        if (r.abi() != null) d.abi = cut(r.abi(), 16);
        if (r.supportedAbis() != null && !r.supportedAbis().isEmpty())
            d.supportedAbis = cut(String.join(",", r.supportedAbis().stream().limit(8).map(a -> cut(a, 16)).filter(a -> a != null).toList()), 100);
        if (r.sdk() != null && r.sdk() > 0 && r.sdk() < 1000) d.sdk = r.sdk();
        if (r.platform() != null) d.platform = PLATFORMS.contains(r.platform()) ? r.platform() : "other";
        if (r.manufacturer() != null) d.manufacturer = cut(r.manufacturer(), 64);
        if (r.model() != null) d.model = cut(r.model(), 64);
        // r.deviceName() is deliberately ignored: free text typed by the user (often a first name), never stored (docs/TELEMETRY.md § 1)
        if (r.osName() != null) d.osName = cut(r.osName(), 64);
        if (r.osBuild() != null) d.osBuild = cut(r.osBuild(), 160);
        if (r.fingerprint() != null) d.fingerprint = cut(r.fingerprint(), 200);
        if (r.screen() != null) d.screen = cut(r.screen(), 32);
        if (r.densityDpi() != null && r.densityDpi() > 0 && r.densityDpi() < 2000) d.densityDpi = r.densityDpi();
        if (r.ramTotalMb() != null) d.ramTotalMb = positive(r.ramTotalMb());
        if (r.storageFreeMb() != null) d.storageFreeMb = positive(r.storageFreeMb());
        if (r.storageTotalMb() != null) d.storageTotalMb = positive(r.storageTotalMb());
        if (r.usbPresent() != null) {
            d.usbPresent = r.usbPresent();
            d.usbFreeMb = r.usbPresent() ? positive(r.usbFreeMb()) : null;
        }
        if (r.btGateway() != null) d.btGateway = r.btGateway();
        if (r.sshEnabled() != null) d.sshEnabled = r.sshEnabled();
        if (r.wifiDirect() != null) d.wifiDirect = r.wifiDirect();
        if (r.videoCount() != null) d.videoCount = positive(r.videoCount());
        if (r.lastError() != null && !r.lastError().isBlank() && !r.lastError().equals(d.lastError)) {
            d.lastError = cut(r.lastError(), 500);
            d.lastErrorAt = now;
        }
        if (r.consent() != null && (r.consent().equals("usage") || r.consent().equals("essential"))) {
            boolean usage = r.consent().equals("usage");
            if (usage != d.usageConsent || d.consentAt == null) d.consentAt = now;
            d.usageConsent = usage;
            d.consentVersion = cut(r.consentVersion(), 16);
        }
        GeoLocator.Place place = geo.locate(req);
        if (place.country() != null) {
            d.country = place.country();
            d.city = cut(place.city(), 80);
        }
        d.ip = cut(req.getRemoteAddr(), 45);
        d.ipSeenAt = now;
        d.lastSeen = now;
    }

    private void recordHistory(Device d, DeviceReport r, Instant now) {
        DeviceRecords.Install inst = installs.findByDeviceIdAndInstallId(d.id, d.installId).orElseGet(() -> {
            DeviceRecords.Install i = new DeviceRecords.Install();
            i.deviceId = d.id;
            i.installId = d.installId;
            i.versionCode = d.versionCode;
            i.firstSeen = now;
            return i;
        });
        inst.lastSeen = now;
        installs.save(inst);

        if (d.versionCode != null && !versions.existsByDeviceIdAndVersionCode(d.id, d.versionCode)) {
            DeviceRecords.Version v = new DeviceRecords.Version();
            v.deviceId = d.id;
            v.versionCode = d.versionCode;
            v.versionName = d.versionName;
            v.firstSeen = now;
            versions.save(v);
        }

        boolean detail = heartbeats.findFirstByDeviceIdOrderBySeenAtDesc(d.id)
                .map(h -> h.seenAt.isBefore(now.minusSeconds(MIN_DETAIL_SECONDS))).orElse(true);
        if (detail) {
            DeviceRecords.Heartbeat h = new DeviceRecords.Heartbeat();
            h.deviceId = d.id;
            h.seenAt = now;
            h.versionCode = d.versionCode;
            h.storageFreeMb = d.storageFreeMb;
            h.usbFreeMb = d.usbFreeMb;
            h.videoCount = d.videoCount;
            h.btGateway = d.btGateway;
            h.sshEnabled = d.sshEnabled;
            h.wifiDirect = d.wifiDirect;
            heartbeats.save(h);
        }

        LocalDate day = LocalDate.ofInstant(now, CastbridgeApplication.ZONE);
        DeviceRecords.Daily agg = daily.findById(new DeviceRecords.DailyKey(d.id, day)).orElseGet(() -> {
            DeviceRecords.Daily a = new DeviceRecords.Daily();
            a.deviceId = d.id;
            a.day = day;
            return a;
        });
        agg.heartbeats++;
        agg.versionCode = d.versionCode;
        if (d.storageFreeMb != null) agg.minStorageFreeMb = agg.minStorageFreeMb == null ? d.storageFreeMb : Math.min(agg.minStorageFreeMb, d.storageFreeMb);
        if (d.videoCount != null) agg.maxVideoCount = agg.maxVideoCount == null ? d.videoCount : Math.max(agg.maxVideoCount, d.videoCount);
        daily.save(agg);
        // activity of the day for the fleet KPIs (DAU/MAU, retention): every device counts, even without usage statistics
        devices.flush();
        jdbc.update("""
                insert into kpi_device_day (stat_day, device_id, app, version_code) values (?,?,?,?)
                on duplicate key update version_code = coalesce(values(version_code), version_code)""", day, d.id, d.app, d.versionCode);
    }

    // ================================================================ admin

    public record Filter(String app, Integer version, String country, String group, String state, String platform,
                         String manufacturer, String abi, String text) {}

    public Page<Device> search(Filter f, String sort, int page, int size) {
        Instant now = Instant.now();
        Instant limit = now.minus(props.devices().onlineMinutes(), ChronoUnit.MINUTES);
        String text = f.text() == null || f.text().isBlank() ? null : "%" + f.text().trim().toLowerCase(Locale.ROOT) + "%";
        Boolean blocked = "blocked".equals(f.state()) ? Boolean.TRUE : null;
        Sort s = switch (sort == null ? "" : sort) {
            case "name" -> Sort.by("label", "deviceName", "model");
            case "model" -> Sort.by("model");
            case "version" -> Sort.by(Sort.Direction.DESC, "versionCode");
            case "country" -> Sort.by("country");
            case "first" -> Sort.by(Sort.Direction.DESC, "firstSeen");
            default -> Sort.by(Sort.Direction.DESC, "lastSeen");
        };
        return devices.search(blank(f.app()), f.version(), blank(f.country()), blank(f.group()),
                "online".equals(f.state()) ? limit : null, "offline".equals(f.state()) ? limit : null, blocked,
                blank(f.platform()), blank(f.manufacturer()), blank(f.abi()), text,
                PageRequest.of(Math.max(0, page), Math.max(1, Math.min(200, size)), s.and(Sort.by("id"))));
    }

    public Device get(String publicId) {
        return byPublicId(publicId).orElseThrow(() -> ApiException.notFound("Appareil introuvable"));
    }

    public record Detail(Device device, List<DeviceRecords.Heartbeat> heartbeats, List<DeviceRecords.Daily> days,
                         List<DeviceRecords.Version> versions, List<DeviceRecords.Install> installs, List<DeviceRecords.Crash> crashes) {}

    public Detail detail(String publicId) {
        Device d = get(publicId);
        LocalDate from = LocalDate.now(CastbridgeApplication.ZONE).minusDays(30);
        return new Detail(d, heartbeats.findTop100ByDeviceIdOrderBySeenAtDesc(d.id), daily.findByDeviceIdAndDayGreaterThanEqualOrderByDayDesc(d.id, from),
                versions.findByDeviceIdOrderByFirstSeenDesc(d.id), installs.findByDeviceIdOrderByFirstSeenDesc(d.id),
                crashes.findTop50ByDeviceIdOrderByCrashedAtDesc(d.id));
    }

    @Transactional
    public Device edit(String publicId, String label, String note, String group) {
        Device d = get(publicId);
        d.label = cut(blank(label), 80);
        d.note = cut(blank(note), 1000);
        d.groupName = cut(blank(group), 80);
        return devices.save(d);
    }

    @Transactional
    public Device forceUpdateCheck(String publicId) {
        Device d = get(publicId);
        d.forceUpdateCheck = true;
        return devices.save(d);
    }

    @Transactional
    public Device setBlocked(String publicId, boolean blocked) {
        Device d = get(publicId);
        d.blocked = blocked;
        log.info("device {} {}", publicId, blocked ? "blocked" : "unblocked");
        return devices.save(d);
    }

    /** @param channel "beta", "stable", or null/"" = the one the app reports */
    @Transactional
    public Device setChannel(String publicId, String channel) {
        Device d = get(publicId);
        String c = blank(channel);
        if (c != null && !ReleaseService.CHANNELS.contains(c)) throw ApiException.badRequest("canal : stable ou beta");
        d.channelOverride = c;
        return devices.save(d);
    }

    @Transactional
    public void delete(String publicId) {
        devices.delete(get(publicId));
    }

    public List<String> groups() { return devices.groups(); }

    public List<String> manufacturers() { return devices.manufacturers(); }

    public List<String> abis() { return devices.abis(); }

    public Map<String, Object> dashboard() {
        Instant now = Instant.now();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", devices.count());
        m.put("online", devices.countByLastSeenAfter(now.minus(props.devices().onlineMinutes(), ChronoUnit.MINUTES)));
        m.put("seen24h", devices.countByLastSeenAfter(now.minus(1, ChronoUnit.DAYS)));
        m.put("seen30d", devices.countByLastSeenAfter(now.minus(30, ChronoUnit.DAYS)));
        m.put("blocked", devices.countByBlockedTrue());
        Map<String, Long> perApp = new LinkedHashMap<>();
        for (Object[] r : devices.perApp()) perApp.put((String) r[0], (Long) r[1]);
        m.put("perApp", perApp);
        List<Map<String, Object>> vers = new ArrayList<>();
        for (Object[] r : devices.versions(now.minus(30, ChronoUnit.DAYS))) {
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("app", r[0]);
            v.put("versionCode", r[1]);
            v.put("versionName", r[2]);
            v.put("count", r[3]);
            vers.add(v);
        }
        m.put("versions", vers);
        List<Map<String, Object>> countries = new ArrayList<>();
        for (Object[] r : devices.countries(now.minus(30, ChronoUnit.DAYS))) {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("country", r[0] == null ? "?" : r[0]);
            c.put("count", r[1]);
            countries.add(c);
        }
        m.put("countries", countries);
        Map<String, Long> platforms = new LinkedHashMap<>();
        for (Object[] r : devices.platforms(now.minus(30, ChronoUnit.DAYS))) platforms.put(r[0] == null ? "?" : (String) r[0], (Long) r[1]);
        m.put("platforms", platforms);
        m.put("recentCrashes", crashes.findTop20ByOrderByCrashedAtDesc());
        return m;
    }

    public Map<Long, Device> byIds(List<Long> ids) {
        Map<Long, Device> out = new LinkedHashMap<>();
        devices.findAllById(ids).forEach(d -> out.put(d.id, d));
        return out;
    }

    public int onlineMinutes() { return props.devices().onlineMinutes(); }

    // ================================================================ retention

    /** Every night: detailed heartbeats > 30 days (daily aggregates stay), raw IPs > 30 days, old crashes, forgotten devices. */
    @Scheduled(cron = "0 15 3 * * *", zone = "Africa/Douala")
    @Transactional
    public void purge() {
        Instant now = Instant.now();
        var p = props.devices();
        int hb = heartbeats.purge(now.minus(p.heartbeatDays(), ChronoUnit.DAYS));
        int ips = devices.forgetIps(now.minus(p.ipDays(), ChronoUnit.DAYS));
        int cr = crashes.purge(now.minus(p.crashDays(), ChronoUnit.DAYS));
        int dev = devices.forgetDevices(now.minus(p.retentionDays(), ChronoUnit.DAYS));
        log.info("device retention: {} heartbeats, {} IP addresses, {} crashes, {} devices purged", hb, ips, cr, dev);
    }

    // ================================================================ helpers

    private static ApiException unknownToken() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "Jeton d'appareil inconnu : réenregistrez l'appareil (POST /api/v1/devices/register)");
    }

    private String newToken() {
        byte[] b = new byte[32];
        random.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    static String sha256Hex(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Trims, removes control characters, cuts to {@code max} characters. */
    static String cut(String s, int max) {
        if (s == null) return null;
        String t = s.replaceAll("\\p{Cntrl}", " ").strip();
        if (t.isEmpty()) return null;
        return t.length() > max ? t.substring(0, max) : t;
    }

    private static Integer positive(Integer v) { return v == null || v < 0 ? null : v; }

    private static String blank(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}
