package castbridge.server.licenses;

import castbridge.server.web.ApiException;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/** Errors of the licence pages are shown as a page, like the other admin pages (not as JSON). */
@ControllerAdvice(assignableTypes = LicenseWebController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LicenseWebErrors {
    private static final Logger log = LoggerFactory.getLogger(LicenseWebErrors.class);

    @ExceptionHandler(ApiException.class)
    public String api(ApiException e, Model model, HttpServletResponse res) {
        res.setStatus(e.status().value());
        model.addAttribute("message", e.getMessage());
        model.addAttribute("active", "licenses");
        return "admin/error";
    }

    @ExceptionHandler(Exception.class)
    public String other(Exception e, Model model, HttpServletResponse res) throws Exception {
        if (e instanceof org.springframework.web.ErrorResponse || e instanceof org.springframework.web.bind.ServletRequestBindingException
                || e instanceof org.springframework.beans.TypeMismatchException) {
            throw e; // 400/404/405 handled by the framework
        }
        log.error("licence page error", e);
        res.setStatus(500);
        model.addAttribute("message", "Erreur interne du serveur");
        model.addAttribute("active", "licenses");
        return "admin/error";
    }
}
