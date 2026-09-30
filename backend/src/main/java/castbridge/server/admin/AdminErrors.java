package castbridge.server.admin;

import castbridge.server.web.ApiException;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/** Errors of the admin pages are shown as a page (not JSON). */
@ControllerAdvice(basePackageClasses = AdminWebController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AdminErrors {
    private static final Logger log = LoggerFactory.getLogger(AdminErrors.class);

    @ExceptionHandler(ApiException.class)
    public String api(ApiException e, Model model, HttpServletResponse res) {
        res.setStatus(e.status().value());
        model.addAttribute("message", e.getMessage());
        return "admin/error";
    }

    @ExceptionHandler(Exception.class)
    public String other(Exception e, Model model, HttpServletResponse res) {
        log.error("admin page error", e);
        res.setStatus(500);
        model.addAttribute("message", "Erreur interne du serveur");
        return "admin/error";
    }
}
