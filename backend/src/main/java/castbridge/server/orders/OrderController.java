package castbridge.server.orders;

import castbridge.server.devices.Device;
import castbridge.server.devices.DeviceService;
import castbridge.server.web.ApiException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Routes of the PHONE (docs/ORDRES.md § Serveur), authenticated by the device token like the lots; a blocked or unknown device gets nothing; the TV itself never calls the server.
 * A phone only ever sees the orders of the TVs paired with it. Off by default (feature switch): 404 while the service is off.
 */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {
    private final OrderService orders;
    private final DeviceService devices;

    public OrderController(OrderService orders, DeviceService devices) { this.orders = orders; this.devices = devices; }

    public record PairBody(String deviceInfo) {}
    public record AcksBody(List<OrderService.Ack> acks) {}

    private Device phone(String authorization) {
        if (!orders.on()) throw ApiException.notFound("Service indisponible");
        Device d = devices.authenticate(authorization).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Appareil inconnu : jeton d'appareil manquant ou invalide"));
        if (d.blocked) throw new ApiException(HttpStatus.FORBIDDEN, "Cet appareil est bloqué par l'administrateur");
        if (!"phone".equals(d.app)) throw new ApiException(HttpStatus.FORBIDDEN, "Réservé à l'application téléphone");
        return d;
    }

    @GetMapping
    public OrderService.Fetched fetch(@RequestParam(defaultValue = "0") long since, @RequestHeader(name = "Authorization", required = false) String authorization) {
        return orders.fetch(phone(authorization).publicId, Math.max(0, since));
    }

    @PostMapping("/pair")
    public ResponseEntity<Void> pair(@RequestBody PairBody b, @RequestHeader(name = "Authorization", required = false) String authorization) {
        orders.pair(phone(authorization).publicId, b == null || b.deviceInfo() == null ? "" : b.deviceInfo());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/acks")
    public Map<String, Integer> acks(@RequestBody AcksBody b, @RequestHeader(name = "Authorization", required = false) String authorization) {
        return Map.of("accepted", orders.acks(phone(authorization).publicId, b == null ? null : b.acks()));
    }
}
