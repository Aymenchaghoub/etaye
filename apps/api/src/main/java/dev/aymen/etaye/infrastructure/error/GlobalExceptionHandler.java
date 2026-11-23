package dev.aymen.etaye.infrastructure.error;

import dev.aymen.etaye.infrastructure.security.AccessDenialAudit;
import dev.aymen.etaye.infrastructure.security.AuthorizationDenial;
import dev.aymen.etaye.infrastructure.security.CorrelationIdFilter;
import dev.aymen.etaye.infrastructure.security.PracticeSecurityService.CapabilityDeniedException;
import dev.aymen.etaye.infrastructure.security.PracticeSecurityService.PracticeNotVisibleException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
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
import java.util.Set;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final AccessDenialAudit accessDenialAudit;

    public GlobalExceptionHandler(AccessDenialAudit accessDenialAudit) {
        this.accessDenialAudit = accessDenialAudit;
    }

    private void recordDenial(AuthorizationDenial denial, HttpServletRequest request) {
        try {
            accessDenialAudit.record(denial, request);
        } catch (Exception ex) {
            log.error("Authorization denial audit failed: class={}, correlationId={}",
                    ex.getClass().getName(), CorrelationIdFilter.getCorrelationId(request));
        }
    }

    private ResponseEntity<ApiProblem> buildProblem(HttpStatus status, String code, String publicDetail, Exception ex, HttpServletRequest request) {
        String correlationId = CorrelationIdFilter.getCorrelationId(request);
        Throwable rootCause = NestedExceptionUtils.getMostSpecificCause(ex);
        String rootCauseClass = rootCause != null ? rootCause.getClass().getName() : ex.getClass().getName();
        if (status.is5xxServerError()) {
            if (status == HttpStatus.INTERNAL_SERVER_ERROR) {
                log.error("Unhandled server exception: class={}, rootCauseClass={}, code={}, correlationId={}",
                        ex.getClass().getName(), rootCauseClass, code, correlationId);
            } else {
                log.error("Database connection failure: class={}, rootCauseClass={}, code={}, correlationId={}",
                        ex.getClass().getName(), rootCauseClass, code, correlationId);
            }
        } else {
            log.warn("Request rejected: class={}, rootCauseClass={}, code={}, correlationId={}",
                    ex.getClass().getName(), rootCauseClass, code, correlationId);
        }
        ApiProblem problem = ApiProblem.of(
                status.value(),
                status.getReasonPhrase(),
                code,
                publicDetail,
                correlationId
        );
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiProblem> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        if (ex instanceof CapabilityDeniedException denied) {
            recordDenial(denied.denial(), request);
        }
        boolean isCsrf = ex instanceof CsrfException;
        String code = isCsrf ? "CSRF_REJECTED" : "FORBIDDEN";
        String detail = isCsrf ? "CSRF token is missing or invalid" : "Access denied";
        return buildProblem(HttpStatus.FORBIDDEN, code, detail, ex, request);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiProblem> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        if (ex instanceof PracticeNotVisibleException denied) {
            recordDenial(denied.denial(), request);
        }
        return buildProblem(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found", ex, request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiProblem> handleNoResourceFound(NoResourceFoundException ex, HttpServletRequest request) {
        return buildProblem(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found", ex, request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiProblem> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        ResponseEntity<ApiProblem> entity = buildProblem(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "Method not allowed", ex, request);
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON);
        Set<HttpMethod> supported = ex.getSupportedHttpMethods();
        if (supported != null && !supported.isEmpty()) {
            builder.allow(supported.toArray(new HttpMethod[0]));
        }
        return builder.body(entity.getBody());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiProblem> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        return buildProblem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "Unsupported media type", ex, request);
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ApiProblem> handleMediaTypeNotAcceptable(HttpMediaTypeNotAcceptableException ex, HttpServletRequest request) {
        String correlationId = CorrelationIdFilter.getCorrelationId(request);
        Throwable rootCause = NestedExceptionUtils.getMostSpecificCause(ex);
        String rootCauseClass = rootCause != null ? rootCause.getClass().getName() : ex.getClass().getName();
        log.warn("Request rejected: class={}, rootCauseClass={}, code={}, correlationId={}",
                ex.getClass().getName(), rootCauseClass, "NOT_ACCEPTABLE", correlationId);
        ApiProblem problem = ApiProblem.of(
                HttpStatus.NOT_ACCEPTABLE.value(),
                "Not Acceptable",
                "NOT_ACCEPTABLE",
                "The requested media type is not supported by this endpoint",
                correlationId
        );
        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiProblem> handleMessageNotReadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return buildProblem(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Malformed request payload", ex, request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiProblem> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return buildProblem(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", "Invalid request parameter", ex, request);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiProblem> handleMissingHeader(MissingRequestHeaderException ex, HttpServletRequest request) {
        return buildProblem(HttpStatus.BAD_REQUEST, "MISSING_HEADER", "Missing required request header", ex, request);
    }

    @ExceptionHandler(InvalidIdempotencyKeyException.class)
    public ResponseEntity<ApiProblem> handleInvalidIdempotencyKey(InvalidIdempotencyKeyException ex, HttpServletRequest request) {
        return buildProblem(HttpStatus.BAD_REQUEST, "INVALID_IDEMPOTENCY_KEY", "Invalid idempotency key", ex, request);
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ApiProblem> handleIdempotencyConflict(IdempotencyConflictException ex, HttpServletRequest request) {
        return buildProblem(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", "Idempotency conflict", ex, request);
    }

    @ExceptionHandler(InvalidParameterException.class)
    public ResponseEntity<ApiProblem> handleInvalidParameter(InvalidParameterException ex, HttpServletRequest request) {
        return buildProblem(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", "Invalid request parameter", ex, request);
    }

    @ExceptionHandler(StaleVersionException.class)
    public ResponseEntity<ApiProblem> handleStaleVersion(StaleVersionException ex, HttpServletRequest request) {
        return buildProblem(HttpStatus.PRECONDITION_FAILED, "STALE_VERSION", "The resource version is not current", ex, request);
    }

    @ExceptionHandler(PreconditionRequiredException.class)
    public ResponseEntity<ApiProblem> handlePreconditionRequired(PreconditionRequiredException ex, HttpServletRequest request) {
        return buildProblem(HttpStatus.PRECONDITION_REQUIRED, "PRECONDITION_REQUIRED", "A precondition header is required", ex, request);
    }

    @ExceptionHandler(ConflictWithCandidatesException.class)
    public ResponseEntity<ApiProblem> handleConflictWithCandidates(ConflictWithCandidatesException ex, HttpServletRequest request) {
        String correlationId = CorrelationIdFilter.getCorrelationId(request);
        log.warn("Request rejected: class={}, code={}, candidates={}, correlationId={}",
                ex.getClass().getName(), ex.code(), ex.candidates().size(), correlationId);
        ApiProblem problem = ApiProblem.withCandidates(
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                ex.code(),
                ex.getMessage(),
                correlationId,
                ex.candidates()
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

        Throwable rootCause = NestedExceptionUtils.getMostSpecificCause(ex);
        String rootCauseClass = rootCause != null ? rootCause.getClass().getName() : ex.getClass().getName();

        if (futureDateOfBirth) {
            log.warn("Future birth date validation failure: class={}, rootCauseClass={}, code={}, correlationId={}",
                    ex.getClass().getName(), rootCauseClass, "VALIDATION_FAILED", correlationId);
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

        log.warn("Schema validation failure: class={}, rootCauseClass={}, code={}, correlationId={}",
                ex.getClass().getName(), rootCauseClass, "VALIDATION_FAILED", correlationId);
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

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiProblem> handleGenericException(Exception ex, HttpServletRequest request) {
        if (DatabaseUnavailable.isConnectionFailure(ex)) {
            return buildProblem(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", "Service temporarily unavailable", ex, request);
        }
        return buildProblem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected server error occurred", ex, request);
    }
}
