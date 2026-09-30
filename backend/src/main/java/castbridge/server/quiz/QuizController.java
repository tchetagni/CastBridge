package castbridge.server.quiz;

import castbridge.server.devices.DeviceService;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public (devices): published questions for the offline cache, and server-side draws. ETag / 304 by WebConfig.
 * A device blocked by the admin (device token or deviceId) gets 403.
 */
@RestController
@RequestMapping("/api/v1/quiz")
public class QuizController {
    private final QuizService service;
    private final DeviceService devices;

    public QuizController(QuizService service, DeviceService devices) {
        this.service = service;
        this.devices = devices;
    }

    @GetMapping("/questions")
    public ResponseEntity<QuizService.SyncPage> questions(@RequestParam(required = false) String track,
                                                          @RequestParam(required = false) String level,
                                                          @RequestParam(required = false) String field,
                                                          @RequestParam(required = false) String lang,
                                                          @RequestParam(required = false) String since,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "200") int size,
                                                          @RequestParam(required = false) String deviceId,
                                                          @RequestHeader(name = "Authorization", required = false) String authorization) {
        devices.refuseIfBlocked(authorization, deviceId);
        return ResponseEntity.ok().cacheControl(CacheControl.noCache())
                .body(service.published(track, level, field, lang, since, page, size));
    }

    @GetMapping("/draw")
    public ResponseEntity<QuizService.Draw> draw(@RequestParam(required = false) String track,
                                                 @RequestParam(required = false) String level,
                                                 @RequestParam(required = false) String field,
                                                 @RequestParam(required = false) String lang,
                                                 @RequestParam(defaultValue = "15") int count,
                                                 @RequestParam(required = false) Long seed,
                                                 @RequestParam(required = false) String exclude,
                                                 @RequestParam(defaultValue = "true") boolean shuffle,
                                                 @RequestParam(required = false) String deviceId,
                                                 @RequestHeader(name = "Authorization", required = false) String authorization) {
        devices.refuseIfBlocked(authorization, deviceId);
        Set<String> ex = exclude == null ? Set.of()
                : Arrays.stream(exclude.split(",")).map(String::trim).filter(s -> !s.isEmpty()).limit(500).collect(Collectors.toSet());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.draw(track, level, field, lang, count, seed, ex, shuffle));
    }
}
