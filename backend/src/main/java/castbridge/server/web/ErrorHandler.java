package castbridge.server.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Turns every failure into an {@link ApiError} in French; never leaks stack traces or internal messages. */
@RestControllerAdvice
public class ErrorHandler {
    private static final Logger log = LoggerFactory.getLogger(ErrorHandler.class);

    private static ResponseEntity<ApiError> reply(HttpStatus status, String message, List<String> details, HttpServletRequest req) {
        return ResponseEntity.status(status)
                .header("Cache-Control", "no-store")
                .body(ApiError.of(status.value(), message, details, req.getRequestURI()));
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> api(ApiException e, HttpServletRequest req) {
        return reply(e.status(), e.getMessage(), e.details(), req);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> invalidBody(MethodArgumentNotValidException e, HttpServletRequest req) {
        List<String> details = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + " : " + f.getDefaultMessage()).toList();
        return reply(HttpStatus.BAD_REQUEST, "Données invalides", details, req);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ApiError> invalidParams(HandlerMethodValidationException e, HttpServletRequest req) {
        List<String> details = e.getAllErrors().stream().map(err -> String.valueOf(err.getDefaultMessage())).toList();
        return reply(HttpStatus.BAD_REQUEST, "Paramètres invalides", details, req);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiError> constraint(ConstraintViolationException e, HttpServletRequest req) {
        List<String> details = e.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + " : " + v.getMessage()).toList();
        return reply(HttpStatus.BAD_REQUEST, "Paramètres invalides", details, req);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ApiError> missingParam(MissingServletRequestParameterException e, HttpServletRequest req) {
        return reply(HttpStatus.BAD_REQUEST, "Paramètre manquant : " + e.getParameterName(), List.of(), req);
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    ResponseEntity<ApiError> missingPart(MissingServletRequestPartException e, HttpServletRequest req) {
        return reply(HttpStatus.BAD_REQUEST, "Champ manquant dans le formulaire : " + e.getRequestPartName(), List.of(), req);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> badType(MethodArgumentTypeMismatchException e, HttpServletRequest req) {
        return reply(HttpStatus.BAD_REQUEST, "Valeur invalide pour « " + e.getName() + " »", List.of(), req);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> unreadable(HttpMessageNotReadableException e, HttpServletRequest req) {
        return reply(HttpStatus.BAD_REQUEST, "Corps de requête illisible (JSON attendu)", List.of(), req);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiError> tooBig(MaxUploadSizeExceededException e, HttpServletRequest req) {
        return reply(HttpStatus.PAYLOAD_TOO_LARGE, "Fichier trop volumineux (200 Mo au maximum)", List.of(), req);
    }

    @ExceptionHandler(MultipartException.class)
    ResponseEntity<ApiError> multipart(MultipartException e, HttpServletRequest req) {
        return reply(HttpStatus.BAD_REQUEST, "Formulaire multipart attendu", List.of(), req);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> noRoute(NoResourceFoundException e, HttpServletRequest req) {
        return reply(HttpStatus.NOT_FOUND, "Cette adresse n'existe pas", List.of(), req);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> method(HttpRequestMethodNotSupportedException e, HttpServletRequest req) {
        return reply(HttpStatus.METHOD_NOT_ALLOWED, "Méthode " + e.getMethod() + " non autorisée ici", List.of(), req);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiError> mediaType(HttpMediaTypeNotSupportedException e, HttpServletRequest req) {
        return reply(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Type de contenu non pris en charge", List.of(), req);
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    ResponseEntity<ApiError> notAcceptable(HttpMediaTypeNotAcceptableException e, HttpServletRequest req) {
        return reply(HttpStatus.NOT_ACCEPTABLE, "Format de réponse non disponible", List.of(), req);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ApiError> lock(ObjectOptimisticLockingFailureException e, HttpServletRequest req) {
        return reply(HttpStatus.CONFLICT, "L'élément a été modifié entre-temps : rechargez-le puis recommencez", List.of(), req);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> integrity(DataIntegrityViolationException e, HttpServletRequest req) {
        log.warn("integrity violation on {}: {}", req.getRequestURI(), e.getMostSpecificCause().getClass().getSimpleName());
        return reply(HttpStatus.CONFLICT, "Cet élément existe déjà (doublon)", List.of(), req);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> other(Exception e, HttpServletRequest req) {
        log.error("unexpected error on {} {}", req.getMethod(), req.getRequestURI(), e);
        return reply(HttpStatus.INTERNAL_SERVER_ERROR, "Erreur interne du serveur, réessayez plus tard", List.of(), req);
    }
}
