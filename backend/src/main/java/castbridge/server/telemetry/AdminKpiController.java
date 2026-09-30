package castbridge.server.telemetry;

import java.time.LocalDate;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin API (bearer token): the KPIs of /admin in JSON, for scripts and agents. */
@RestController
@RequestMapping("/api/v1/admin/kpi")
public class AdminKpiController {
    private final KpiService kpi;

    public AdminKpiController(KpiService kpi) { this.kpi = kpi; }

    private static Kpi.Filter filter(String from, String to, String app, Integer version, String platform, String country, String group,
                                     String model) {
        return castbridge.server.admin.AdminKpiPages.filter(from, to, app, version, platform, country, group, model);
    }

    @GetMapping("/features")
    public KpiService.Features features(@RequestParam(required = false) String from, @RequestParam(required = false) String to,
                                        @RequestParam(required = false) String app, @RequestParam(required = false) Integer version,
                                        @RequestParam(required = false) String platform, @RequestParam(required = false) String country,
                                        @RequestParam(required = false) String group, @RequestParam(required = false) String model) {
        return kpi.features(filter(from, to, app, version, platform, country, group, model));
    }

    /** section = parc | usage | cast | lecture | quiz | echecs | telechargements | mises-a-jour | qualite | connectivite */
    @GetMapping("/{section}")
    public Kpi.Section section(@PathVariable String section, @RequestParam(required = false) String from,
                               @RequestParam(required = false) String to, @RequestParam(required = false) String app,
                               @RequestParam(required = false) Integer version, @RequestParam(required = false) String platform,
                               @RequestParam(required = false) String country, @RequestParam(required = false) String group,
                               @RequestParam(required = false) String model) {
        return kpi.section(section, filter(from, to, app, version, platform, country, group, model));
    }

    @GetMapping
    public Map<String, Object> catalog() {
        return Map.of("sections", Kpi.SECTIONS, "events", EventCatalog.names(), "tvFeatures", EventCatalog.TV_FEATURES,
                "phoneFeatures", EventCatalog.PHONE_FEATURES, "today", LocalDate.now(castbridge.server.CastbridgeApplication.ZONE).toString());
    }
}
