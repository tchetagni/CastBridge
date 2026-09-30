package castbridge.server.devices;

import castbridge.server.telemetry.TelemetryService;
import castbridge.server.web.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public, for the apps: register once (get a device token), then heartbeat every 15 min, at start-up and after an
 * update, and report crashes, with "Authorization: Bearer &lt;deviceToken&gt;".
 */
@RestController
@RequestMapping("/api/v1/devices")
public class DeviceController {
    private final DeviceService service;
    private final TelemetryService telemetry;

    public DeviceController(DeviceService service, TelemetryService telemetry) {
        this.service = service;
        this.telemetry = telemetry;
    }

    @PostMapping("/register")
    public ResponseEntity<DeviceService.Registration> register(@RequestBody DeviceReport report, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(service.register(report, req));
    }

    @PostMapping("/heartbeat")
    public ResponseEntity<DeviceService.Directives> heartbeat(@RequestHeader(name = "Authorization", required = false) String authorization,
                                                              @RequestBody(required = false) DeviceReport report, HttpServletRequest req) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.heartbeat(authorization, report, req));
    }

    @PostMapping("/crash")
    public ResponseEntity<Void> crash(@RequestHeader(name = "Authorization", required = false) String authorization,
                                      @RequestBody DeviceService.CrashReport crash) {
        service.crash(authorization, crash);
        return ResponseEntity.noContent().build();
    }

    /** Right of access: everything the server holds about this device (its record and its usage events). */
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(@RequestHeader(name = "Authorization", required = false) String authorization) {
        Device d = service.authenticate(authorization).orElseThrow(DeviceController::unknown);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("device", AdminDeviceController.DeviceView.of(d, service.onlineMinutes()));
        out.put("usageConsent", d.usageConsent);
        out.put("events", telemetry.events(d.id, 5000));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(out);
    }

    /** Right to erasure, asked from the app: the device record, its history and all its usage events are deleted. */
    @DeleteMapping("/me")
    public ResponseEntity<Void> erase(@RequestHeader(name = "Authorization", required = false) String authorization) {
        Device d = service.authenticate(authorization).orElseThrow(DeviceController::unknown);
        service.delete(d.publicId);
        return ResponseEntity.noContent().build();
    }

    private static ApiException unknown() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "Jeton d'appareil inconnu");
    }
}
