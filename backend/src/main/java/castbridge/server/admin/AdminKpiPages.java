package castbridge.server.admin;

import castbridge.server.CastbridgeApplication;
import castbridge.server.devices.DeviceService;
import castbridge.server.telemetry.Kpi;
import castbridge.server.telemetry.KpiService;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** KPI pages of the admin interface (the features ranking is on the dashboard, see AdminWebController). */
@Controller
public class AdminKpiPages {
    private final KpiService kpi;
    private final DeviceService devices;

    public AdminKpiPages(KpiService kpi, DeviceService devices) {
        this.kpi = kpi;
        this.devices = devices;
    }

    /** Filters from the query string: period (default the last 30 days, at most 400), app, version, platform, country, group, model. */
    public static Kpi.Filter filter(String from, String to, String app, Integer version, String platform, String country, String group, String model) {
        LocalDate today = LocalDate.now(CastbridgeApplication.ZONE);
        LocalDate t = date(to, today);
        if (t.isAfter(today)) t = today;
        LocalDate f = date(from, t.minusDays(29));
        if (f.isAfter(t)) f = t;
        if (t.toEpochDay() - f.toEpochDay() > 400) f = t.minusDays(400);
        return new Kpi.Filter(f, t, "tv".equals(app) || "phone".equals(app) ? app : null, version, blank(platform),
                blank(country) == null ? null : country.trim().toUpperCase(Locale.ROOT), blank(group), blank(model));
    }

    private static LocalDate date(String s, LocalDate def) {
        try {
            return s == null || s.isBlank() ? def : LocalDate.parse(s.trim());
        } catch (DateTimeParseException e) {
            return def;
        }
    }

    private static String blank(String s) { return s == null || s.isBlank() ? null : s.trim(); }

    /** Attributes of the filter form (lists of values for the selects). */
    void formModel(Model model, Kpi.Filter f) {
        model.addAttribute("f", f);
        model.addAttribute("platforms", DeviceService.PLATFORMS);
        model.addAttribute("groups", devices.groups());
        model.addAttribute("today", LocalDate.now(CastbridgeApplication.ZONE));
    }

    @GetMapping("/admin/kpi")
    public String page(@RequestParam(defaultValue = "parc") String section, @RequestParam(required = false) String from,
                       @RequestParam(required = false) String to, @RequestParam(required = false) String app,
                       @RequestParam(required = false) Integer version, @RequestParam(required = false) String platform,
                       @RequestParam(required = false) String country, @RequestParam(required = false) String group,
                       @RequestParam(required = false) String model, Model m) {
        Kpi.Filter f = filter(from, to, app, version, platform, country, group, model);
        String key = Kpi.SECTIONS.stream().map(x -> x.get(0)).filter(section::equals).findFirst().orElse("parc");
        m.addAttribute("section", kpi.section(key, f));
        m.addAttribute("sections", Kpi.SECTIONS);
        m.addAttribute("key", key);
        formModel(m, f);
        m.addAttribute("active", "kpi");
        return "admin/kpi";
    }

    @GetMapping("/admin/kpi/export")
    public ResponseEntity<String> export(@RequestParam(defaultValue = "parc") String section, @RequestParam(required = false) String from,
                                         @RequestParam(required = false) String to, @RequestParam(required = false) String app,
                                         @RequestParam(required = false) Integer version, @RequestParam(required = false) String platform,
                                         @RequestParam(required = false) String country, @RequestParam(required = false) String group,
                                         @RequestParam(required = false) String model) {
        Kpi.Filter f = filter(from, to, app, version, platform, country, group, model);
        List<Kpi.Table> tables;
        String name;
        if ("fonctionnalites".equals(section)) {
            KpiService.Features feats = kpi.features(f);
            Kpi.Table t = new Kpi.Table("Fonctionnalités les plus utilisées du " + f.from() + " au " + f.to(),
                    List.of("Fonctionnalité", "App", "Utilisations", "Appareils", "Part du parc actif", "Temps total (ms)", "Temps moyen par appareil",
                            "Utilisations période précédente", "Tendance"));
            feats.rows().forEach(r -> t.rows().add(List.of(r.label(), r.app(), r.uses(), r.devices(), r.share(), r.timeMs(), r.avgTime(),
                    r.previousUses(), r.trend())));
            tables = List.of(t);
            name = "fonctionnalites";
        } else {
            Kpi.Section s = kpi.section(section, f);
            tables = new ArrayList<>();
            Kpi.Table tiles = new Kpi.Table(s.title() + " du " + f.from() + " au " + f.to(), List.of("Indicateur", "Valeur", "Détail"));
            s.tiles().forEach(t -> tiles.rows().add(List.of(t.label(), t.value(), t.hint() == null ? "" : t.hint())));
            tables.add(tiles);
            tables.addAll(s.tables());
            name = s.key();
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"kpi-" + name + "-" + f.from() + "-" + f.to() + ".csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body("﻿" + KpiService.csv(tables)); // BOM: Excel opens the UTF-8 accents correctly
    }
}
