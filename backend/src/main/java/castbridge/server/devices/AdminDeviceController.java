package castbridge.server.devices;

import castbridge.server.CastbridgeApplication;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin API (bearer token): the same device follow-up as the web interface, for scripts and agents. */
@RestController
@RequestMapping("/api/v1/admin/devices")
public class AdminDeviceController {
    private final DeviceService service;

    public AdminDeviceController(DeviceService service) { this.service = service; }

    /** A device as the admin sees it (never the token or ANDROID_ID hashes). */
    public record DeviceView(String id, String app, String name, String label, String note, String group, boolean online, boolean blocked,
                             String channel, String channelOverride, boolean forceUpdateCheck, Integer versionCode, String versionName,
                             String platform, String manufacturer, String model, String deviceName, String osName, String osBuild,
                             String fingerprint, Integer sdk, String abi, String supportedAbis, String screen, Integer densityDpi,
                             Integer ramTotalMb, Integer storageFreeMb, Integer storageTotalMb, Boolean usbPresent, Integer usbFreeMb,
                             Boolean btGateway, Boolean sshEnabled, Boolean wifiDirect, Integer videoCount, String lastError,
                             OffsetDateTime lastErrorAt, String country, String city, String ip, OffsetDateTime firstSeen,
                             OffsetDateTime lastSeen) {
        public static DeviceView of(Device d, int onlineMinutes) {
            return new DeviceView(d.publicId, d.app, d.displayName(), d.label, d.note, d.groupName, d.online(Instant.now(), onlineMinutes),
                    d.blocked, DeviceService.effectiveChannel(d), d.channelOverride, d.forceUpdateCheck, d.versionCode, d.versionName,
                    d.platform, d.manufacturer, d.model, d.deviceName, d.osName, d.osBuild, d.fingerprint, d.sdk, d.abi, d.supportedAbis,
                    d.screen, d.densityDpi, d.ramTotalMb, d.storageFreeMb, d.storageTotalMb, d.usbPresent, d.usbFreeMb, d.btGateway,
                    d.sshEnabled, d.wifiDirect, d.videoCount, d.lastError, local(d.lastErrorAt), d.country, d.city, d.ip,
                    local(d.firstSeen), local(d.lastSeen));
        }
    }

    static OffsetDateTime local(Instant i) { return i == null ? null : i.atZone(CastbridgeApplication.ZONE).toOffsetDateTime(); }

    @GetMapping
    public Map<String, Object> list(@RequestParam(required = false) String app, @RequestParam(required = false) Integer version,
                                    @RequestParam(required = false) String country, @RequestParam(required = false) String group,
                                    @RequestParam(required = false) String state, @RequestParam(required = false) String platform,
                                    @RequestParam(required = false) String manufacturer, @RequestParam(required = false) String abi,
                                    @RequestParam(required = false) String q, @RequestParam(required = false) String sort,
                                    @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        Page<Device> res = service.search(new DeviceService.Filter(app, version, country, group, state, platform, manufacturer, abi, q),
                sort, page, size);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("page", res.getNumber());
        out.put("totalPages", res.getTotalPages());
        out.put("total", res.getTotalElements());
        out.put("devices", res.map(d -> DeviceView.of(d, service.onlineMinutes())).getContent());
        return out;
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        Map<String, Object> m = new LinkedHashMap<>(service.dashboard());
        m.put("recentCrashes", ((List<?>) m.get("recentCrashes")).size());
        return m;
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable String id) {
        DeviceService.Detail d = service.detail(id);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("device", DeviceView.of(d.device(), service.onlineMinutes()));
        out.put("days", d.days().stream().map(x -> Map.of("day", x.day.toString(), "heartbeats", x.heartbeats,
                "versionCode", x.versionCode == null ? 0 : x.versionCode)).toList());
        out.put("versions", d.versions().stream().map(v -> Map.of("versionCode", v.versionCode, "versionName", String.valueOf(v.versionName),
                "firstSeen", local(v.firstSeen))).toList());
        out.put("installs", d.installs().stream().map(i -> Map.of("installId", i.installId, "firstSeen", local(i.firstSeen),
                "lastSeen", local(i.lastSeen))).toList());
        out.put("crashes", d.crashes().stream().map(c -> Map.of("at", local(c.crashedAt), "message", c.message,
                "versionCode", c.versionCode == null ? 0 : c.versionCode)).toList());
        return out;
    }

    public record Edit(String label, String note, String group) {}

    @PutMapping("/{id}")
    public DeviceView edit(@PathVariable String id, @RequestBody Edit e) {
        return DeviceView.of(service.edit(id, e.label(), e.note(), e.group()), service.onlineMinutes());
    }

    @PostMapping("/{id}/check-update")
    public DeviceView checkUpdate(@PathVariable String id) {
        return DeviceView.of(service.forceUpdateCheck(id), service.onlineMinutes());
    }

    @PostMapping("/{id}/block")
    public DeviceView block(@PathVariable String id, @RequestParam(defaultValue = "true") boolean blocked) {
        return DeviceView.of(service.setBlocked(id, blocked), service.onlineMinutes());
    }

    /** channel = beta | stable | (empty: the app's own choice) */
    @PostMapping("/{id}/channel")
    public DeviceView channel(@PathVariable String id, @RequestParam(required = false) String channel) {
        return DeviceView.of(service.setChannel(id, channel), service.onlineMinutes());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
