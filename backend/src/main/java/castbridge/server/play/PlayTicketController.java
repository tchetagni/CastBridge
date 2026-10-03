package castbridge.server.play;

import castbridge.server.devices.Device;
import castbridge.server.devices.DeviceService;
import castbridge.server.web.ApiException;
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
 * Public (devices): {@code POST /api/v1/play/ticket} with {@code Authorization: Bearer <deviceToken>} and {@code {"deviceCode":"XXXX-XXXX-XXXX-XXXX"}} gives a {@code cbp1} ticket
 * that attests the device (docs/API-SERVER.md, « Ticket de jeu »). No right is evaluated here.
 */
@RestController
@RequestMapping("/api/v1/play")
public class PlayTicketController {
    private final DeviceService devices;
    private final PlayTicketService tickets;

    public PlayTicketController(DeviceService devices, PlayTicketService tickets) {
        this.devices = devices;
        this.tickets = tickets;
    }

    public record TicketRequest(String deviceCode) {}

    @PostMapping("/ticket")
    public ResponseEntity<PlayTicketService.Issued> ticket(@RequestHeader(name = "Authorization", required = false) String authorization,
                                                           @RequestBody(required = false) TicketRequest body, HttpServletRequest req) {
        Device d = devices.authenticate(authorization).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Jeton d'appareil inconnu"));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(tickets.issue(d, body == null ? null : body.deviceCode(), System.currentTimeMillis(), req.getRemoteAddr()));
    }
}
