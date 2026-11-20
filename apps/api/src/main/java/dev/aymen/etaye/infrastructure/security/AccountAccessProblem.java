package dev.aymen.etaye.infrastructure.security;

import dev.aymen.etaye.infrastructure.error.ApiProblem;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

public final class AccountAccessProblem {

    private AccountAccessProblem() {}

    public static ResponseEntity<ApiProblem> response(HttpServletRequest request, HttpStatus status,
                                                       String code, String detail) {
        return builder(status).body(problem(request, status, code, detail));
    }

    public static ResponseEntity<ApiProblem> rateLimited(HttpServletRequest request, int retryAfter) {
        return builder(HttpStatus.TOO_MANY_REQUESTS).header(HttpHeaders.RETRY_AFTER, Integer.toString(retryAfter))
                .body(problem(request, HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "Request budget exhausted"));
    }

    private static ResponseEntity.BodyBuilder builder(HttpStatus status) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON);
    }

    private static ApiProblem problem(HttpServletRequest request, HttpStatus status, String code, String detail) {
        return ApiProblem.of(status.value(), status.getReasonPhrase(), code, detail,
                CorrelationIdFilter.getCorrelationId(request));
    }
}
