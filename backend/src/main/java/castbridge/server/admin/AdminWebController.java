package castbridge.server.admin;

import castbridge.server.devices.Device;
import castbridge.server.devices.DeviceRecords;
import castbridge.server.devices.DeviceService;
import castbridge.server.telemetry.KpiService;
import castbridge.server.web.ApiException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Admin web interface: login, dashboard, devices. */
@Controller
public class AdminWebController {
    private final DeviceService devices;
    private final KpiService kpi;
    private final AdminKpiPages kpiPages;

    public AdminWebController(DeviceService devices, KpiService kpi, AdminKpiPages kpiPages) {
        this.devices = devices;
        this.kpi = kpi;
        this.kpiPages = kpiPages;
    }

    @GetMapping("/")
    public String root() { return "redirect:/admin"; }

    @GetMapping("/admin/login")
    public String login() { return "admin/login"; }

    @GetMapping("/admin")
    public String dashboard(@RequestParam(required = false) String from, @RequestParam(required = false) String to,
                            @RequestParam(required = false) String app, @RequestParam(required = false) Integer version,
                            @RequestParam(required = false) String platform, @RequestParam(required = false) String country,
                            @RequestParam(required = false) String group, @RequestParam(required = false) String model,
                            Model m) {
        // first: the most used features (what Esaie wants to see first)
        var f = AdminKpiPages.filter(from, to, app, version, platform, country, group, model);
        m.addAttribute("features", kpi.features(f));
        kpiPages.formModel(m, f);
        dashboardModel(m);
        return "admin/dashboard";
    }

    private void dashboardModel(Model model) {
        Map<String, Object> d = devices.dashboard();
        model.addAttribute("d", d);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> versions = (List<Map<String, Object>>) d.get("versions");
        long max = versions.stream().mapToLong(v -> (Long) v.get("count")).max().orElse(1);
        model.addAttribute("versionMax", Math.max(1, max));
        @SuppressWarnings("unchecked")
        List<DeviceRecords.Crash> crashes = (List<DeviceRecords.Crash>) d.get("recentCrashes");
        model.addAttribute("crashDevices", devices.byIds(crashes.stream().map(c -> c.deviceId).distinct().toList()));
        model.addAttribute("active", "dashboard");
    }

    @GetMapping("/admin/devices")
    public String list(@RequestParam(required = false) String app, @RequestParam(required = false) Integer version,
                       @RequestParam(required = false) String country, @RequestParam(required = false) String group,
                       @RequestParam(required = false) String state, @RequestParam(required = false) String platform,
                       @RequestParam(required = false) String manufacturer, @RequestParam(required = false) String abi,
                       @RequestParam(required = false) String q, @RequestParam(defaultValue = "seen") String sort,
                       @RequestParam(defaultValue = "0") int page, Model model) {
        var filter = new DeviceService.Filter(app, version, country, group, state, platform, manufacturer, abi, q);
        Page<Device> res = devices.search(filter, sort, page, 50);
        model.addAttribute("page", res);
        model.addAttribute("f", filter);
        model.addAttribute("sort", sort);
        model.addAttribute("now", Instant.now());
        model.addAttribute("onlineMinutes", devices.onlineMinutes());
        model.addAttribute("groups", devices.groups());
        model.addAttribute("manufacturers", devices.manufacturers());
        model.addAttribute("abis", devices.abis());
        model.addAttribute("platforms", DeviceService.PLATFORMS);
        model.addAttribute("active", "devices");
        return "admin/devices";
    }

    @GetMapping("/admin/devices/{id}")
    public String device(@PathVariable String id, Model model) {
        DeviceService.Detail detail = devices.detail(id);
        model.addAttribute("x", detail);
        model.addAttribute("dev", detail.device());
        model.addAttribute("online", detail.device().online(Instant.now(), devices.onlineMinutes()));
        model.addAttribute("channel", DeviceService.effectiveChannel(detail.device()));
        // bars of the last 30 days (heartbeats per day), oldest on the left
        List<DeviceRecords.Daily> days = new ArrayList<>(detail.days());
        java.util.Collections.reverse(days);
        int max = days.stream().mapToInt(x -> x.heartbeats).max().orElse(1);
        List<Map<String, Object>> bars = new ArrayList<>();
        for (int i = 0; i < days.size(); i++) {
            Map<String, Object> b = new LinkedHashMap<>();
            int h = (int) Math.round(56.0 * days.get(i).heartbeats / Math.max(1, max));
            b.put("x", i * 10);
            b.put("y", 60 - Math.max(2, h));
            b.put("h", Math.max(2, h));
            b.put("title", days.get(i).day + " : " + days.get(i).heartbeats + " contacts");
            bars.add(b);
        }
        model.addAttribute("bars", bars);
        model.addAttribute("timeline", kpi.timeline(detail.device().id, 200));
        model.addAttribute("active", "devices");
        return "admin/device";
    }

    @PostMapping("/admin/devices/{id}/edit")
    public String edit(@PathVariable String id, @RequestParam(required = false) String label, @RequestParam(required = false) String note,
                       @RequestParam(required = false) String group, RedirectAttributes ra) {
        return act(id, ra, "Fiche enregistrée", () -> devices.edit(id, label, note, group));
    }

    @PostMapping("/admin/devices/{id}/check-update")
    public String checkUpdate(@PathVariable String id, RedirectAttributes ra) {
        return act(id, ra, "Vérification des mises à jour demandée au prochain contact", () -> devices.forceUpdateCheck(id));
    }

    @PostMapping("/admin/devices/{id}/block")
    public String block(@PathVariable String id, @RequestParam boolean blocked, RedirectAttributes ra) {
        return act(id, ra, blocked ? "Appareil bloqué : plus de mises à jour ni de quiz" : "Appareil débloqué",
                () -> devices.setBlocked(id, blocked));
    }

    @PostMapping("/admin/devices/{id}/channel")
    public String channel(@PathVariable String id, @RequestParam(required = false) String channel, RedirectAttributes ra) {
        return act(id, ra, "Canal de mise à jour enregistré", () -> devices.setChannel(id, channel));
    }

    @PostMapping("/admin/devices/{id}/delete")
    public String delete(@PathVariable String id, RedirectAttributes ra) {
        try {
            devices.delete(id);
            ra.addFlashAttribute("ok", "Appareil et données effacés (il sera recréé s'il reprend contact)");
        } catch (ApiException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/devices";
    }

    private String act(String id, RedirectAttributes ra, String ok, Runnable action) {
        try {
            action.run();
            ra.addFlashAttribute("ok", ok);
        } catch (ApiException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/devices/" + id;
    }
}
