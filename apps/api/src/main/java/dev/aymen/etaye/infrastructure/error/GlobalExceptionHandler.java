package dev.aymen.etaye.infrastructure.error;

import dev.aymen.etaye.infrastructure.security.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.ArrayList;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiProblem> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        String correlationId = CorrelationIdFilter.getCorrelationId(request);
        log.warn("Access denied: class={}, code={}, correlationId={}", ex.getClass().getName(), "FORBIDDEN", correlationId);
        ApiProblem problem = ApiProblem.of(
                HttpStatus.FORBIDDEN.value(),
                "Forbidden",
                "FORBIDDEN",
                "Access denied",
                correlationId
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiProblem> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        String correlationId = CorrelationIdFilter.getCorrelationId(request);
        log.warn("Resource not found: class={}, code={}, correlationId={}", ex.getClass().getName(), "NOT_FOUND", correlationId);
        ApiProblem problem = ApiProblem.of(
                HttpStatus.NOT_FOUND.value(),
                "Not Found",
                "NOT_FOUND",
                "Resource not found",
                correlationId
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiProblem> handleNoResourceFound(NoResourceFoundException ex, HttpServletRequest request) {
        String correlationId = CorrelationIdFilter.getCorrelationId(request);
        log.warn("Route not found: class={}, code={}, correlationId={}", ex.getClass().getName(), "NOT_FOUND", correlationId);
        ApiProblem problem = ApiProblem.of(
                HttpStatus.NOT_FOUND.value(),
                "Not Found",
                "NOT_FOUND",
                "Resource not found",
                correlationId
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiProblem> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        String correlationId = CorrelationIdFilter.getCorrelationId(request);
        log.warn("Method not allowed: class={}, code={}, correlationId={}", ex.getClass().getName(), "METHOD_NOT_ALLOWED", correlationId);
        ApiProblem problem = ApiProblem.of(
                HttpStatus.METHOD_NOT_ALLOWED.value(),
                "Method Not Allowed",
                "METHOD_NOT_ALLOWED",
                "Method not allowed",
                correlationId
        );
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiProblem> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        String correlationId = CorrelationIdFilter.getCorrelationId(request);
        log.warn("Unsupported media type: class={}, code={}, correlationId={}", ex.getClass().getName(), "UNSUPPORTED_MEDIA_TYPE", correlationId);
        ApiProblem problem = ApiProblem.of(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(),
                "Unsupported Media Type",
                "UNSUPPORTED_MEDIA_TYPE",
                "Unsupported media type",
                correlationId
        );
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiProblem> handleMessageNotReadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        String correlationId = CorrelationIdFilter.getCorrelationId(request);
        log.warn("Malformed JSON or unknown properties: class={}, code={}, correlationId={}", ex.getClass().getName(), "MALFORMED_REQUEST", correlationId);
        ApiProblem problem = ApiProblem.of(
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                "MALFORMED_REQUEST",
                "Malformed request payload",
                correlationId
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiProblem> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String correlationId = CorrelationIdFilter.getCorrelationId(request);
        log.warn("Parameter type mismatch: class={}, code={}, correlationId={}", ex.getClass().getName(), "INVALID_PARAMETER", correlationId);
        ApiProblem problem = ApiProblem.of(
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                "INVALID_PARAMETER",
                "Invalid request parameter",
                correlationId
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiProblem> handleMissingHeader(MissingRequestHeaderException ex, HttpServletRequest request) {
        String correlationId = CorrelationIdFilter.getCorrelationId(request);
        log.warn("Missing required header: class={}, code={}, correlationId={}", ex.getClass().getName(), "MISSING_HEADER", correlationId);
        ApiProblem problem = ApiProblem.of(
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                "MISSING_HEADER",
                "Missing required request header",
                correlationId
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(InvalidIdempotencyKeyException.class)
    public ResponseEntity<ApiProblem> handleInvalidIdempotencyKey(InvalidIdempotencyKeyException ex, HttpServletRequest request) {
        String correlationId = CorrelationIdFilter.getCorrelationId(request);
        log.warn("Invalid idempotency key: class={}, code={}, correlationId={}", ex.getClass().getName(), "INVALID_IDEMPOTENCY_KEY", correlationId);
        ApiProblem problem = ApiProblem.of(
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                "INVALID_IDEMPOTENCY_KEY",
                "Invalid idempotency key",
                correlationId
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ApiProblem> handleIdempotencyConflict(IdempotencyConflictException ex, HttpServletRequest request) {
        String correlationId = CorrelationIdFilter.getCorrelationId(request);
        log.warn("Idempotency conflict: class={}, code={}, correlationId={}", ex.getClass().getName(), "IDEMPOTENCY_CONFLICT", correlationId);
        ApiProblem problem = ApiProblem.of(
                HttpStatus.CONFLICT.value(),
                "Conflict",
                "IDEMPOTENCY_CONFLICT",
                "Idempotency conflict",
                correlationId
        );
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiProblem> handleValidationErrors(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String correlationId = CorrelationIdFilter.getCorrelationId(request);
        List<FieldErrorItem> fieldErrors = new ArrayList<>();
        boolean futureDateOfBirth = false;

        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            String code = fe.getCode();
            if ("dateOfBirth".equals(fe.getField()) && ("PastOrPresent".equals(code) || "Past".equals(code))) {
                futureDateOfBirth = true;
            }
            fieldErrors.add(new FieldErrorItem(fe.getField(), fe.getCode(), fe.getDefaultMessage()));
        }

        if (futureDateOfBirth) {
            log.warn("Future birth date validation failure: class={}, code={}, correlationId={}",
                    ex.getClass().getName(), "VALIDATION_FAILED", correlationId);
            ApiProblem problem = ApiProblem.withFieldErrors(
                    HttpStatus.UNPROCESSABLE_ENTITY.value(),
                    "Unprocessable Content",
                    "VALIDATION_FAILED",
                    "Input validation failed",
                    correlationId,
                    fieldErrors
            );
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .body(problem);
        }

        log.warn("Schema validation failure: class={}, code={}, correlationId={}",
                ex.getClass().getName(), "VALIDATION_FAILED", correlationId);
        ApiProblem problem = ApiProblem.withFieldErrors(
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                "VALIDATION_FAILED",
                "Input validation failed",
                correlationId,
                fieldErrors
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(DataAccessResourceFailureException.class)
    public ResponseEntity<ApiProblem> handleJdbcConnectionFailure(DataAccessResourceFailureException ex, HttpServletRequest request) {
        String correlationId = CorrelationIdFilter.getCorrelationId(request);
        log.error("Database connection failure: class={}, code={}, correlationId={}", ex.getClass().getName(), "SERVICE_UNAVAILABLE", correlationId);
        ApiProblem problem = ApiProblem.of(
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                "Service Unavailable",
                "SERVICE_UNAVAILABLE",
                "Service temporarily unavailable",
                correlationId
        );
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiProblem> handleGenericException(Exception ex, HttpServletRequest request) {
        String correlationId = CorrelationIdFilter.getCorrelationId(request);
        log.error("Unhandled server exception: class={}, code={}, correlationId={}", ex.getClass().getName(), "INTERNAL_ERROR", correlationId, ex);
        ApiProblem problem = ApiProblem.of(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Internal Server Error",
                "INTERNAL_ERROR",
                "An unexpected server error occurred",
                correlationId
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
