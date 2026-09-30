package castbridge.server.admin;

import castbridge.server.updates.ReleaseService;
import castbridge.server.web.ApiException;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Admin web interface: publish APKs, progressive rollout, revoke, oldest supported version. */
@Controller
public class AdminReleasesPage {
    private final ReleaseService releases;

    public AdminReleasesPage(ReleaseService releases) { this.releases = releases; }

    @GetMapping("/admin/releases")
    public String page(@RequestParam(required = false) String app, Model model) {
        model.addAttribute("releases", releases.list(app));
        model.addAttribute("policies", releases.policies());
        model.addAttribute("app", app);
        model.addAttribute("abis", ReleaseService.ABIS.stream().sorted().toList());
        model.addAttribute("active", "releases");
        return "admin/releases";
    }

    @PostMapping("/admin/releases")
    public String publish(@RequestParam String app, @RequestParam String abi, @RequestParam int versionCode,
                          @RequestParam String versionName, @RequestParam(required = false) String notes,
                          @RequestParam(defaultValue = "stable") String channel, @RequestParam(defaultValue = "false") boolean mandatory,
                          @RequestParam(defaultValue = "100") int rollout, @RequestParam("file") MultipartFile file, RedirectAttributes ra) {
        try (InputStream in = file.getInputStream()) {
            if (file.isEmpty()) throw ApiException.badRequest("Choisissez un fichier APK");
            var p = releases.publish(new ReleaseService.NewRelease(app, abi, versionCode, versionName, notes, channel, mandatory, null, rollout),
                    file.getOriginalFilename(), in);
            ra.addFlashAttribute("ok", "Version " + p.release().getVersionName() + " (" + p.release().getVersionCode() + ") publiée — APK " + p.inspection());
        } catch (ApiException e) {
            ra.addFlashAttribute("error", e.getMessage() + (e.details().isEmpty() ? "" : " : " + String.join(" ; ", e.details())));
        } catch (IOException e) {
            ra.addFlashAttribute("error", "Lecture du fichier impossible");
        }
        return "redirect:/admin/releases";
    }

    @PostMapping("/admin/releases/{id}/rollout")
    public String rollout(@PathVariable long id, @RequestParam int percent, RedirectAttributes ra) {
        return act(ra, "Déploiement progressif réglé à " + percent + " %", () -> releases.rollout(id, percent));
    }

    @PostMapping("/admin/releases/{id}/revoke")
    public String revoke(@PathVariable long id, RedirectAttributes ra) {
        return act(ra, "Version retirée : plus proposée ni téléchargeable", () -> releases.revoke(id));
    }

    @PostMapping("/admin/releases/{id}/delete")
    public String delete(@PathVariable long id, RedirectAttributes ra) {
        return act(ra, "Version supprimée", () -> releases.delete(id));
    }

    @PostMapping("/admin/releases/policy")
    public String policy(@RequestParam String app, @RequestParam String channel, @RequestParam int minSupportedVersionCode,
                         RedirectAttributes ra) {
        return act(ra, "Version minimale supportée de " + app + "/" + channel + " : " + minSupportedVersionCode,
                () -> releases.setMinSupported(app, channel, minSupportedVersionCode));
    }

    private String act(RedirectAttributes ra, String ok, Runnable action) {
        try {
            action.run();
            ra.addFlashAttribute("ok", ok);
        } catch (ApiException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/releases";
    }
}
