package com.fenixcore.optisaludplus.core.exception;

import jakarta.validation.ConstraintViolationException;
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
import java.util.Map;
import java.util.NoSuchElementException;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @org.springframework.beans.factory.annotation.Value("${problems.base-url}")
    private String problemsBase;

    // 400 — bean validation (@Valid on request body)
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {

        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> Map.of("field", e.getField(), "message", messageOf(e)))
                .toList();

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "One or more fields have invalid values");
        problem.setType(URI.create(problemsBase + "/validation-error"));
        problem.setTitle("Validation Failed");
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problem);
    }

    // 400 — path/query param constraint violations (@Validated on controller)
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex) {
        List<Map<String, String>> errors = ex.getConstraintViolations().stream()
                .map(v -> Map.of("field", v.getPropertyPath().toString(), "message", v.getMessage()))
                .toList();

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Constraint violation");
        problem.setType(URI.create(problemsBase + "/validation-error"));
        problem.setTitle("Validation Failed");
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problem);
    }

    // 401 — bad credentials (not to be confused with 403 Forbidden)
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleUnauthorized(AuthenticationException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(UNAUTHORIZED, ex.getMessage());
        problem.setType(URI.create(problemsBase + "/unauthorized"));
        problem.setTitle("Unauthorized");
        return ResponseEntity.status(UNAUTHORIZED).body(problem);
    }

    // 423 — account temporarily locked (brute-force protection)
    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<ProblemDetail> handleLocked(AccountLockedException ex) {
        HttpStatusCode status = HttpStatusCode.valueOf(423);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problem.setType(URI.create(problemsBase + "/account-locked"));
        problem.setTitle("Account Locked");
        problem.setProperty("lockedUntil", ex.getLockedUntil());
        return ResponseEntity.status(status).body(problem);
    }

    // 403 — thrown from @PreAuthorize or service layer (filter-level 403 handled in SecurityConfig)
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleForbidden(AccessDeniedException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Access denied");
        problem.setType(URI.create(problemsBase + "/forbidden"));
        problem.setTitle("Forbidden");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
    }

    // 404
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(NoSuchElementException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setType(URI.create(problemsBase + "/not-found"));
        problem.setTitle("Not Found");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    // 409 — unique constraint or FK violation from DB
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleConflict(DataIntegrityViolationException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "Resource already exists or a uniqueness constraint was violated");
        problem.setType(URI.create(problemsBase + "/conflict"));
        problem.setTitle("Conflict");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    // 422 — domain rule violations (illegal state / bad argument from business logic)
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleUnprocessable(IllegalArgumentException ex) {
        HttpStatusCode status = HttpStatusCode.valueOf(422);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problem.setType(URI.create(problemsBase + "/unprocessable"));
        problem.setTitle("Unprocessable Entity");
        return ResponseEntity.status(status).body(problem);
    }

    // 500 — catch-all
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGeneric(Exception ex) {
        logger.error("Unexpected error", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        problem.setType(URI.create(problemsBase + "/internal-error"));
        problem.setTitle("Internal Server Error");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }

    private static String messageOf(FieldError error) {
        return error.getDefaultMessage() != null ? error.getDefaultMessage() : "Invalid value";
    }
}
