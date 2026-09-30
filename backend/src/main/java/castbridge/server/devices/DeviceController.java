package castbridge.server.devices;

import jakarta.servlet.http.HttpServletRequest;
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

    public DeviceController(DeviceService service) { this.service = service; }

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
}
