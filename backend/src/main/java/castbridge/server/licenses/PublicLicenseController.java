package castbridge.server.licenses;

import castbridge.server.devices.Device;
import castbridge.server.devices.DeviceService;
import castbridge.server.web.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The only two public routes of the module, OFF by default (castbridge.licenses.public-routes). There is none for the offline
 * phase. The signed revocation list is not secret (it is signed); the entitlement needs the existing device token.
 */
@RestController
public class PublicLicenseController {
    private final RevocationService service;
    private final DeviceService devices;

    public PublicLicenseController(RevocationService service, DeviceService devices) {
        this.service = service;
        this.devices = devices;
    }

    /** The signed list {@code cbr1.…} as one line of plain text (docs/ACTIVATION-FORMAT.md § 7). */
    @GetMapping(value = "/api/v1/revocations", produces = org.springframework.http.MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> revocations() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.signedList());
    }

    @GetMapping("/api/v1/entitlements/me")
    public ResponseEntity<Map<String, Object>> me(@RequestHeader(name = "Authorization", required = false) String authorization,
                                                  @RequestParam String deviceCode, HttpServletRequest req) {
        Device d = devices.authenticate(authorization).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Appareil inconnu : enregistrez-le d'abord"));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.entitlement(deviceCode, d.publicId, req.getRemoteAddr()));
    }
}
