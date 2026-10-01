package castbridge.server.orders;

import castbridge.server.web.ApiException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Admin web interface « Ordres » (CSRF, strict CSP, no inline script: same conventions as the other pages): create an order, follow its states, cancel, check the audit chain. */
@Controller
public class AdminOrdersPage {
    private final OrderService orders;

    public AdminOrdersPage(OrderService orders) { this.orders = orders; }

    @GetMapping("/admin/orders")
    public String page(Model model) {
        model.addAttribute("on", orders.on());
        model.addAttribute("actions", PolicyCatalog.ACTIONS.stream().sorted().toList());
        model.addAttribute("orders", orders.list(50, 0));
        model.addAttribute("active", "orders");
        return "admin/orders";
    }

    @PostMapping("/admin/orders")
    public String create(@RequestParam String targetKind, @RequestParam(required = false) String targetValue, @RequestParam String action,
                         @RequestParam(required = false) String params, @RequestParam(defaultValue = "0") int priority,
                         @RequestParam(required = false) Integer hours, @RequestParam(defaultValue = "false") boolean hold, Authentication auth, RedirectAttributes ra) {
        try {
            Map<String, String> p = new LinkedHashMap<>();
            if (params != null) for (String line : params.split("\\R")) {
                if (line.isBlank()) continue;
                int i = line.indexOf('=');
                if (i < 1) throw ApiException.badRequest("Paramètre attendu sous la forme nom=valeur : " + line);
                p.put(line.substring(0, i).trim(), line.substring(i + 1).trim());
            }
            long id = orders.create(new OrderService.NewOrder(targetKind, targetValue == null || targetValue.isBlank() ? null : targetValue.trim(), action, p, priority, hours, hold), auth.getName());
            ra.addFlashAttribute("ok", "Ordre n° " + id + (hold ? " créé (retenu : à publier)" : " créé et signé"));
        } catch (ApiException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/orders";
    }

    @PostMapping("/admin/orders/release")
    public String release(Authentication auth, RedirectAttributes ra) {
        try { ra.addFlashAttribute("ok", orders.release(auth.getName()) + " ordre(s) signé(s) et publié(s)"); } catch (ApiException e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/orders";
    }

    @PostMapping("/admin/orders/{id}/cancel")
    public String cancel(@PathVariable long id, Authentication auth, RedirectAttributes ra) {
        try { orders.cancel(id, auth.getName()); ra.addFlashAttribute("ok", "Ordre annulé : il ne sera plus remis"); } catch (ApiException e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/orders";
    }

    @PostMapping("/admin/orders/verify")
    public String verify(RedirectAttributes ra) {
        long bad = orders.verifyAudit();
        if (bad < 0) ra.addFlashAttribute("ok", "Journal d'audit des ordres : chaîne intacte"); else ra.addFlashAttribute("error", "Journal d'audit altéré à partir de la ligne " + bad);
        return "redirect:/admin/orders";
    }
}
