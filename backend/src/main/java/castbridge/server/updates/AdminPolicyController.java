package castbridge.server.updates;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin: oldest supported version per app and channel (devices below it must update). */
@RestController
@RequestMapping("/api/v1/admin/update-policies")
public class AdminPolicyController {
    private final ReleaseService service;

    public AdminPolicyController(ReleaseService service) { this.service = service; }

    public record PolicyView(String app, String channel, int minSupportedVersionCode, OffsetDateTime updatedAt) {
        static PolicyView of(UpdatePolicy p) {
            return new PolicyView(p.getApp(), p.getChannel(), p.getMinSupportedVersionCode(), AdminReleaseController.local(p.getUpdatedAt()));
        }
    }

    @GetMapping
    public List<PolicyView> list() {
        return service.policies().stream().map(PolicyView::of).toList();
    }

    @PutMapping("/{app}/{channel}")
    public PolicyView set(@PathVariable String app, @PathVariable String channel, @RequestParam int minSupportedVersionCode) {
        return PolicyView.of(service.setMinSupported(app, channel, minSupportedVersionCode));
    }
}
