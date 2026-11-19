package dev.aymen.etaye.infrastructure.error;

import dev.aymen.etaye.infrastructure.security.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.ArrayList;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiProblem> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        String correlationId = CorrelationIdFilter.getCorrelationId(request);
        String detail = ex.getMessage() != null && !ex.getMessage().isBlank() ? ex.getMessage() : "Access denied";
        ApiProblem problem = ApiProblem.of(
                HttpStatus.FORBIDDEN.value(),
                "Forbidden",
                "FORBIDDEN",
                detail,
                correlationId
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiProblem> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        String correlationId = CorrelationIdFilter.getCorrelationId(request);
        ApiProblem problem = ApiProblem.of(
                HttpStatus.NOT_FOUND.value(),
                "Not Found",
                "NOT_FOUND",
                ex.getMessage(),
                correlationId
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ApiProblem> handleIdempotencyConflict(IdempotencyConflictException ex, HttpServletRequest request) {
        String correlationId = CorrelationIdFilter.getCorrelationId(request);
        ApiProblem problem = ApiProblem.of(
                HttpStatus.CONFLICT.value(),
                "Conflict",
                "IDEMPOTENCY_CONFLICT",
                ex.getMessage(),
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
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.add(new FieldErrorItem(fe.getField(), fe.getCode(), fe.getDefaultMessage()));
        }
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
        String correlationId = CorrelationIdFilter.getCorrelationId(request);
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
