package castbridge.server.wallet;

import castbridge.server.wallet.core.LedgerException;
import castbridge.server.wallet.core.WalletReason;
import castbridge.server.web.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Refus du grand livre en réponse HTTP : toujours un MOTIF FERMÉ ({@link WalletReason}, dans {@code details[0]}) et son texte français ({@code message}), identiques sur la TV et le
 * téléphone (conception § 7.6). Ne s'applique qu'aux contrôleurs de ce module.
 */
@RestControllerAdvice(basePackageClasses = WalletErrors.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
@WalletModuleConfig.Enabled
public class WalletErrors {

    static HttpStatus statusOf(WalletReason r) {
        return switch (r) {
            case BAD_TXN, UNBALANCED -> HttpStatus.BAD_REQUEST;
            case BOUND_OTHER_TV -> HttpStatus.FORBIDDEN;
            case DAILY_CAP -> HttpStatus.TOO_MANY_REQUESTS;
            case OFFLINE -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.CONFLICT;
        };
    }

    @ExceptionHandler(LedgerException.class)
    public ResponseEntity<ApiError> ledger(LedgerException e, HttpServletRequest req) {
        HttpStatus s = statusOf(e.reason());
        return ResponseEntity.status(s).header("Cache-Control", "no-store").body(ApiError.of(s.value(), e.getMessage(), List.of(e.reason().name()), req.getRequestURI()));
    }
}
