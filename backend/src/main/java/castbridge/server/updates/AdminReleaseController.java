package castbridge.server.updates;

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

/** Admin (bearer token): publish and manage APK releases. */
@RestController
@RequestMapping("/api/v1/admin/releases")
public class AdminReleaseController {
    private final ReleaseService service;

    public AdminReleaseController(ReleaseService service) { this.service = service; }

    public record ReleaseView(long id, String app, String abi, String channel, int versionCode, String versionName, String notes,
                              String packageName, Integer minSdk, boolean mandatory, String fileName, String sha256, long size,
                              int rolloutPercent, boolean revoked, OffsetDateTime revokedAt, OffsetDateTime publishedAt,
                              String downloadPath, String inspection) {
        static ReleaseView of(Release r, String inspection) {
            return new ReleaseView(r.getId(), r.getApp(), r.getAbi(), r.getChannel(), r.getVersionCode(), r.getVersionName(), r.getNotes(),
                    r.getPackageName(), r.getMinSdk(), r.isMandatory(), r.getFileName(), r.getSha256(), r.getSizeBytes(),
                    r.getRolloutPercent(), r.isRevoked(), local(r.getRevokedAt()), local(r.getPublishedAt()),
                    "/dl/" + r.getApp() + "/" + r.getFileName(), inspection);
        }
    }

    static OffsetDateTime local(Instant i) {
        return i == null ? null : i.atZone(CastbridgeApplication.ZONE).toOffsetDateTime();
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ReleaseView> publish(
            @RequestParam String app,
            @RequestParam String abi,
            @RequestParam int versionCode,
            @RequestParam String versionName,
            @RequestParam(required = false) String notes,
            @RequestParam(name = "channel", required = false, defaultValue = "stable") String channel,
            @RequestParam(required = false) Boolean mandatory,
            @RequestParam(required = false) Integer minSdk,
            @RequestParam(name = "rollout", required = false) Integer rollout,
            @RequestPart("file") MultipartFile file) {
        if (file.isEmpty()) throw ApiException.badRequest("Fichier APK vide ou absent (champ « file »)");
        var form = new ReleaseService.NewRelease(app, abi, versionCode, versionName, notes, channel, mandatory, minSdk, rollout);
        try (InputStream in = file.getInputStream()) {
            ReleaseService.Published p = service.publish(form, file.getOriginalFilename(), in);
            return ResponseEntity.status(HttpStatus.CREATED).body(ReleaseView.of(p.release(), p.inspection()));
        } catch (IOException e) {
            throw ApiException.badRequest("Lecture du fichier envoyé impossible");
        }
    }

    @GetMapping
    public List<ReleaseView> list(@RequestParam(required = false) String app) {
        return service.list(app).stream().map(r -> ReleaseView.of(r, null)).toList();
    }

    @GetMapping("/{id}")
    public ReleaseView get(@PathVariable long id) {
        return ReleaseView.of(service.get(id), null);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/rollout")
    public ReleaseView rollout(@PathVariable long id, @RequestParam int percent) {
        return ReleaseView.of(service.rollout(id, percent), null);
    }

    @PostMapping("/{id}/revoke")
    public ReleaseView revoke(@PathVariable long id) {
        return ReleaseView.of(service.revoke(id), null);
    }
}
