package dev.aymen.etaye.infrastructure.web;

import dev.aymen.etaye.infrastructure.security.AccountAccessProblem;
import dev.aymen.etaye.infrastructure.security.AccountRequestBudget;
import dev.aymen.etaye.infrastructure.security.BrowserSessionLifetime;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class CsrfController {

    private final AccountRequestBudget budget;

    public CsrfController(AccountRequestBudget budget) {
        this.budget = budget;
    }

    @GetMapping(value = "/csrf", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getCsrfToken(CsrfToken csrfToken, HttpServletRequest request) {
        boolean newSession = request.getSession(false) == null;
        if (newSession) {
            int retryAfter = budget.retryAfter(AccountRequestBudget.Operation.CSRF, request.getRemoteAddr(), null);
            if (retryAfter > 0) {
                return AccountAccessProblem.rateLimited(request, retryAfter);
            }
        }

        String token = csrfToken.getToken();
        if (newSession) {
            request.getSession(false).setMaxInactiveInterval((int) BrowserSessionLifetime.ANONYMOUS_LIFETIME.toSeconds());
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of(
                "headerName", csrfToken.getHeaderName(),
                "token", token
        ));
    }
}
