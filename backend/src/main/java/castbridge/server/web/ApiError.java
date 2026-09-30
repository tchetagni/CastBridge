package castbridge.server.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import castbridge.server.CastbridgeApplication;

/** Body of every error: {"status":400,"erreur":"Requête invalide","message":"…","details":[…],"chemin":"/api/…","date":"…"}. */
public record ApiError(int status, String erreur, String message, List<String> details, String chemin, OffsetDateTime date) {

    public static ApiError of(int status, String message, List<String> details, String path) {
        return new ApiError(status, label(status), message, details == null ? List.of() : details, path,
                OffsetDateTime.now(CastbridgeApplication.ZONE));
    }

    /** Writes the error directly (servlet filters, security entry points). */
    public static void write(HttpServletResponse res, ObjectMapper json, int status, String message, String path) throws IOException {
        res.setStatus(status);
        res.setContentType("application/json;charset=UTF-8");
        res.setHeader("Cache-Control", "no-store");
        json.writeValue(res.getOutputStream(), of(status, message, List.of(), path));
    }

    static String label(int status) {
        return switch (status) {
            case 400 -> "Requête invalide";
            case 401 -> "Authentification requise";
            case 403 -> "Accès refusé";
            case 404 -> "Introuvable";
            case 405 -> "Méthode non autorisée";
            case 406 -> "Format non disponible";
            case 409 -> "Conflit";
            case 410 -> "Retiré";
            case 412 -> "Version modifiée entre-temps";
            case 413 -> "Fichier trop volumineux";
            case 415 -> "Type de contenu non pris en charge";
            case 416 -> "Plage demandée invalide";
            case 429 -> "Trop de requêtes";
            case 503 -> "Service indisponible";
            default -> status >= 500 ? "Erreur interne" : "Erreur";
        };
    }
}
