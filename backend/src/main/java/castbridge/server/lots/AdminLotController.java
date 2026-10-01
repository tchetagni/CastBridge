package castbridge.server.lots;

import castbridge.server.CastbridgeApplication;
import castbridge.server.web.ApiException;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Admin (bearer token, like the releases): upload, publish, roll out and revoke lots. A lot is never served before « publish ». */
@RestController
@RequestMapping("/api/v1/admin/lots")
public class AdminLotController {
    private final LotService service;

    public AdminLotController(LotService service) { this.service = service; }

    public record LotView(long id, String feature, String scope, int version, String channel, String title, long size, String sha256,
                          int minAppVersion, int rolloutPercent, boolean published, boolean revoked, OffsetDateTime uploadedAt,
                          OffsetDateTime publishedAt, OffsetDateTime revokedAt, String downloadPath) {
        static LotView of(Lot l) {
            return new LotView(l.getId(), l.getFeature(), l.getScope(), l.getVersion(), l.getChannel(), l.getTitle(), l.getSizeBytes(), l.getSha256(),
                    l.getMinAppVersion(), l.getRolloutPercent(), l.isPublished(), l.isRevoked(), local(l.getUploadedAt()), local(l.getPublishedAt()),
                    local(l.getRevokedAt()), "/api/v1/lots/" + l.getFeature() + "/" + l.getScope() + "/" + l.getVersion());
        }
    }

    static OffsetDateTime local(Instant i) { return i == null ? null : i.atZone(CastbridgeApplication.ZONE).toOffsetDateTime(); }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<LotView> upload(
            @RequestParam String feature,
            @RequestParam String scope,
            @RequestParam int version,
            @RequestParam String title,
            @RequestParam(name = "channel", required = false, defaultValue = "stable") String channel,
            @RequestParam(required = false) Integer minAppVersion,
            @RequestParam(name = "rollout", required = false) Integer rollout,
            @RequestParam(required = false, defaultValue = "false") boolean publish,
            @RequestParam(required = false) String sha256,
            @RequestPart("file") MultipartFile file) {
        if (file.isEmpty()) throw ApiException.badRequest("Fichier vide ou absent (champ « file »)");
        var form = new LotService.NewLot(feature, scope, version, title, channel, minAppVersion, rollout, publish, sha256);
        try (InputStream in = file.getInputStream()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(LotView.of(service.upload(form, in)));
        } catch (IOException e) {
            throw ApiException.badRequest("Lecture du fichier envoyé impossible");
        }
    }

    @GetMapping
    public List<LotView> list() { return service.list().stream().map(LotView::of).toList(); }

    @GetMapping("/{id}")
    public LotView get(@PathVariable long id) { return LotView.of(service.get(id)); }

    @PostMapping("/{id}/publish")
    public LotView publish(@PathVariable long id) { return LotView.of(service.publish(id)); }

    @PostMapping("/{id}/revoke")
    public LotView revoke(@PathVariable long id) { return LotView.of(service.revoke(id)); }

    @PostMapping("/{id}/rollout")
    public LotView rollout(@PathVariable long id, @RequestParam int percent) { return LotView.of(service.rollout(id, percent)); }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
