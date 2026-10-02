package castbridge.server.tunnel;

import castbridge.server.web.ApiException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Admin page « Tunnels TV » (session + CSRF + strict CSP like the other pages): state of every tunnel, ssh command, probe, kill switch, experts list. */
@Controller
public class AdminTunnelPage {
    private final TunnelService tunnel;
    private final ExpertsService experts;

    public AdminTunnelPage(TunnelService tunnel, ExpertsService experts) {
        this.tunnel = tunnel;
        this.experts = experts;
    }

    @GetMapping("/admin/tunnels")
    public String page(Model m) {
        tunnel.requireEnabled();
        m.addAttribute("active", "tunnels");
        m.addAttribute("tunnels", tunnel.views());
        m.addAttribute("c", tunnel.counters());
        m.addAttribute("experts", experts.experts());
        m.addAttribute("expertsStatus", experts.status());
        m.addAttribute("audit", tunnel.recentAudit(30));
        m.addAttribute("host", tunnel.props().host());
        m.addAttribute("sshPort", tunnel.props().sshPort());
        return "admin/tunnels";
    }

    private String back(RedirectAttributes ra, String ok, Runnable action) {
        try {
            action.run();
            ra.addFlashAttribute("ok", ok);
        } catch (ApiException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/tunnels";
    }

    @PostMapping("/admin/tunnels/probe")
    public String probeAll(RedirectAttributes ra) {
        tunnel.requireEnabled();
        ra.addFlashAttribute("ok", "Sonde terminée : " + tunnel.probeAll() + " tunnel(s) connecté(s)");
        return "redirect:/admin/tunnels";
    }

    @PostMapping("/admin/tunnels/{code}/probe")
    public String probeOne(@PathVariable String code, RedirectAttributes ra) {
        tunnel.requireEnabled();
        return back(ra, "Sonde terminée", () -> tunnel.probe(castbridge.server.licenses.DeviceIdentity.normalize(code)));
    }

    @PostMapping("/admin/tunnels/{code}/revoke")
    public String revoke(@PathVariable String code, Authentication auth, RedirectAttributes ra) {
        return back(ra, "Tunnel révoqué : la clé de la TV est retirée (une connexion déjà ouverte tombe à la prochaine coupure ou par ops/tunnel/kick.sh)", () -> tunnel.revoke(code, auth.getName()));
    }

    @PostMapping("/admin/tunnels/{code}/restore")
    public String restore(@PathVariable String code, Authentication auth, RedirectAttributes ra) {
        return back(ra, "Tunnel rétabli", () -> tunnel.restore(code, auth.getName()));
    }

    @PostMapping("/admin/tunnels/experts/refresh")
    public String refreshExperts(RedirectAttributes ra) {
        tunnel.requireEnabled();
        ExpertsService.Result r = experts.refresh();
        if (r.outcome() == ExpertsService.Outcome.REJECTED) ra.addFlashAttribute("error", "Liste des experts refusée : " + r.message());
        else ra.addFlashAttribute("ok", "Liste des experts : " + r.message());
        return "redirect:/admin/tunnels";
    }
}
