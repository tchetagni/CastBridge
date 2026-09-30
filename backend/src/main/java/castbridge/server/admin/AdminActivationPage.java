package castbridge.server.admin;

import castbridge.server.activation.ActivationService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Admin web interface: offline activation token generator (the seller enters the TV's machine code, gets the token). */
@Controller
public class AdminActivationPage {
    private final ActivationService activation;

    public AdminActivationPage(ActivationService activation) {
        this.activation = activation;
    }

    @GetMapping("/admin/activation")
    public String page(Model model) {
        model.addAttribute("configured", activation.configured());
        model.addAttribute("active", "activation");
        return "admin/activation";
    }

    @PostMapping("/admin/activation")
    public String generate(@RequestParam String code, Model model) {
        model.addAttribute("configured", activation.configured());
        model.addAttribute("code", code.trim());
        model.addAttribute("active", "activation");
        if (!activation.configured()) {
            model.addAttribute("error", "Le secret d'activation (CASTBRIDGE_ACTIVATION_SECRET) n'est pas configuré sur le serveur.");
        } else {
            model.addAttribute("token", activation.token(code));
        }
        return "admin/activation";
    }
}
