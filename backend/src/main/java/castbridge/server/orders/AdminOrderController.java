package castbridge.server.orders;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin API (bearer token of the administrator, like the lots): create, release, follow and cancel orders. The server signs only what [PolicyCatalog] allows. */
@RestController
@RequestMapping("/api/v1/admin/orders")
public class AdminOrderController {
    private static final String ACTOR = "admin-api";
    private final OrderService orders;

    public AdminOrderController(OrderService orders) { this.orders = orders; }

    public record Create(String targetKind, String targetValue, String action, Map<String, String> params, Integer priority, Integer expiresInHours, Boolean hold) {}
    public record Member(String kind, String ref, String tv, Boolean member) {}

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody Create c) {
        long id = orders.create(new OrderService.NewOrder(c.targetKind(), c.targetValue(), c.action(), c.params(), c.priority() == null ? 0 : c.priority(), c.expiresInHours(), Boolean.TRUE.equals(c.hold())), ACTOR);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id));
    }

    @PostMapping("/release")
    public Map<String, Integer> release() { return Map.of("released", orders.release(ACTOR)); }

    @GetMapping
    public List<OrderService.OrderView> list(@RequestParam(defaultValue = "50") int limit, @RequestParam(defaultValue = "0") int offset) { return orders.list(limit, offset); }

    @GetMapping("/{id}")
    public OrderService.OrderView get(@PathVariable long id) { return orders.get(id); }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable long id) { orders.cancel(id, ACTOR); return ResponseEntity.noContent().build(); }

    @PostMapping("/membership")
    public ResponseEntity<Void> membership(@RequestBody Member m) { orders.setMembership(m.kind(), m.ref(), m.tv(), !Boolean.FALSE.equals(m.member()), ACTOR); return ResponseEntity.noContent().build(); }

    @GetMapping("/audit/verify")
    public Map<String, Object> verify() { long bad = orders.verifyAudit(); return Map.of("intact", bad < 0, "firstBrokenId", bad); }
}
