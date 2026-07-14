package com.fenixcore.optibienestar360.core.exception;

import jakarta.validation.ConstraintViolationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private final MessageSource messageSource;
    private final String problemsBase;

    public GlobalExceptionHandler(MessageSource messageSource,
                                  @Value("${problems.base-url}") String problemsBase) {
        this.messageSource = messageSource;
        this.problemsBase = problemsBase;
    }

    // 400 — bean validation (@Valid on request body)
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {

        Locale locale = LocaleContextHolder.getLocale();
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> Map.of("field", e.getField(), "message", messageOf(e)))
                .toList();

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, resolve("error.detail.validation", locale));
        problem.setType(URI.create(problemsBase + "/validation-error"));
        problem.setTitle(resolve("error.title.validation_failed", locale));
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problem);
    }

    // 400 — path/query param constraint violations (@Validated on controller)
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex) {
        Locale locale = LocaleContextHolder.getLocale();
        List<Map<String, String>> errors = ex.getConstraintViolations().stream()
                .map(v -> Map.of("field", v.getPropertyPath().toString(), "message", v.getMessage()))
                .toList();

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, resolve("error.detail.validation", locale));
        problem.setType(URI.create(problemsBase + "/validation-error"));
        problem.setTitle(resolve("error.title.validation_failed", locale));
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problem);
    }

    // 401 — bad credentials / invalid token
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleUnauthorized(AuthenticationException ex) {
        Locale locale = LocaleContextHolder.getLocale();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(UNAUTHORIZED, resolveLocalized(ex, locale));
        problem.setType(URI.create(problemsBase + "/unauthorized"));
        problem.setTitle(resolve("error.title.unauthorized", locale));
        return ResponseEntity.status(UNAUTHORIZED).body(problem);
    }

    // 423 — account temporarily locked (brute-force protection)
    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<ProblemDetail> handleLocked(AccountLockedException ex) {
        Locale locale = LocaleContextHolder.getLocale();
        HttpStatusCode status = HttpStatusCode.valueOf(423);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, resolveLocalized(ex, locale));
        problem.setType(URI.create(problemsBase + "/account-locked"));
        problem.setTitle(resolve("error.title.account_locked", locale));
        problem.setProperty("lockedUntil", ex.getLockedUntil());
        return ResponseEntity.status(status).body(problem);
    }

    // 403 — thrown from @PreAuthorize or service layer (filter-level 403 handled in SecurityConfig)
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleForbidden(AccessDeniedException ex) {
        Locale locale = LocaleContextHolder.getLocale();
        // When the service throws AccessDeniedException("role.system.not_editable") the code
        // arrives as ex.getMessage() and resolveCodeOrLiteral renders the localized text. When
        // Spring Security throws from @PreAuthorize (no specific message), we fall back to
        // the generic "access denied" entry.
        String detail = (ex.getMessage() != null && !ex.getMessage().isBlank())
                ? resolveCodeOrLiteral(ex.getMessage(), locale)
                : resolve("error.detail.access_denied", locale);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, detail);
        problem.setType(URI.create(problemsBase + "/forbidden"));
        problem.setTitle(resolve("error.title.forbidden", locale));
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
    }

    // 404
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(NoSuchElementException ex) {
        Locale locale = LocaleContextHolder.getLocale();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, resolveCodeOrLiteral(ex.getMessage(), locale));
        problem.setType(URI.create(problemsBase + "/not-found"));
        problem.setTitle(resolve("error.title.not_found", locale));
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    // 409 — unique constraint or FK violation from DB
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleConflict(DataIntegrityViolationException ex) {
        Locale locale = LocaleContextHolder.getLocale();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, resolve("error.detail.conflict", locale));
        problem.setType(URI.create(problemsBase + "/conflict"));
        problem.setTitle(resolve("error.title.conflict", locale));
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    // 422 — domain rule violations (illegal state / bad argument from business logic)
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleUnprocessable(IllegalArgumentException ex) {
        Locale locale = LocaleContextHolder.getLocale();
        HttpStatusCode status = HttpStatusCode.valueOf(422);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                status, resolveCodeOrLiteral(ex.getMessage(), locale));
        problem.setType(URI.create(problemsBase + "/unprocessable"));
        problem.setTitle(resolve("error.title.unprocessable", locale));
        return ResponseEntity.status(status).body(problem);
    }

    // 500 — catch-all
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGeneric(Exception ex) {
        logger.error("Unexpected error", ex);
        Locale locale = LocaleContextHolder.getLocale();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, resolve("error.detail.unexpected", locale));
        problem.setType(URI.create(problemsBase + "/internal-error"));
        problem.setTitle(resolve("error.title.internal", locale));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }

    // ─── Resolvers ────────────────────────────────────────────────────────────

    private String resolve(String code, Locale locale) {
        return messageSource.getMessage(code, null, code, locale);
    }

    private String resolveLocalized(LocalizedBusinessException ex, Locale locale) {
        return messageSource.getMessage(ex.getMessageCode(), ex.getArgs(), ex.getMessageCode(), locale);
    }

    /**
     * Best-effort resolver for JDK/Spring exceptions whose message we hope to
     * be a code (e.g. {@code role.system.not_editable}). If the code isn't in
     * any bundle the literal message is returned, so legacy or third-party
     * throws are not silently swallowed.
     */
    private String resolveCodeOrLiteral(String codeOrLiteral, Locale locale) {
        if (codeOrLiteral == null || codeOrLiteral.isBlank()) {
            return null;
        }
        return messageSource.getMessage(codeOrLiteral, null, codeOrLiteral, locale);
    }

    private static String messageOf(FieldError error) {
        return error.getDefaultMessage() != null ? error.getDefaultMessage() : "Invalid value";
    }
}
