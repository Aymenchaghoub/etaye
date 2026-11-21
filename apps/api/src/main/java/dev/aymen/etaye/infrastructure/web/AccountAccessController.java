package dev.aymen.etaye.infrastructure.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.aymen.etaye.identity.dto.AccountProfile;
import dev.aymen.etaye.identity.dto.AuthenticatedAccount;
import dev.aymen.etaye.identity.service.AccountAccessService;
import dev.aymen.etaye.infrastructure.error.InvalidIdempotencyKeyException;
import dev.aymen.etaye.infrastructure.security.AccountAccessProblem;
import dev.aymen.etaye.infrastructure.security.AccountRequestBudget;
import dev.aymen.etaye.infrastructure.security.BrowserSessionLifetime;
import dev.aymen.etaye.infrastructure.security.CorrelationIdFilter;
import dev.aymen.etaye.infrastructure.security.IdentityResolutionFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.CodePointLength;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/v1")
public class AccountAccessController {

    private static final String ACCOUNT_PATH = "/compte";
    private static final Pattern RECOVERY_KEY = Pattern.compile("[A-Za-z0-9_-]{16,128}");

    private final AccountAccessService access;
    private final AccountRequestBudget budget;
    private final BrowserSessionLifetime lifetime;

    public AccountAccessController(AccountAccessService access, AccountRequestBudget budget,
                                   BrowserSessionLifetime lifetime) {
        this.access = access;
        this.budget = budget;
        this.lifetime = lifetime;
    }

    @PostMapping(path = "/session", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> signIn(@Valid @RequestBody Credentials credentials,
                                    HttpServletRequest request, HttpServletResponse response) {
        String email = normalizeEmail(credentials.email());
        int retryAfter = budget.retryAfter(AccountRequestBudget.Operation.SIGN_IN, request.getRemoteAddr(), email);
        if (retryAfter > 0) {
            return AccountAccessProblem.rateLimited(request, retryAfter);
        }

        var verified = access.signIn(email, credentials.password(), CorrelationIdFilter.getCorrelationId(request));
        if (verified.isEmpty()) {
            return AccountAccessProblem.response(request, HttpStatus.UNAUTHORIZED,
                    "INVALID_CREDENTIALS", "Unable to sign in with these credentials");
        }

        var account = verified.get();
        HttpSession session = lifetime.establish(request, response, account.account());
        return json(HttpStatus.OK, sessionView(account, session, safeReturn(credentials.returnTo(), account)));
    }

    @GetMapping(path = "/session", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SessionView> getSession(
            @RequestAttribute(IdentityResolutionFilter.AUTHENTICATED_ACCOUNT_ATTR) AuthenticatedAccount account,
            HttpServletRequest request) {
        return json(HttpStatus.OK, sessionView(account, request.getSession(false), null));
    }

    @DeleteMapping("/session")
    public ResponseEntity<Void> signOut(HttpServletRequest request) {
        request.getSession(false).invalidate();
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @PostMapping(path = "/access-recovery", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> requestRecovery(@Valid @RequestBody RecoveryRequest payload,
            @RequestHeader("Idempotency-Key") String key, HttpServletRequest request) {
        validateRecoveryKey(key);
        String email = normalizeEmail(payload.email());
        int retryAfter = budget.retryAfter(AccountRequestBudget.Operation.REQUEST_RECOVERY, request.getRemoteAddr(), email);
        if (retryAfter > 0) {
            return AccountAccessProblem.rateLimited(request, retryAfter);
        }
        access.requestRecovery(email, key);
        return json(HttpStatus.ACCEPTED, acknowledgement(request));
    }

    @PostMapping(path = "/access-recovery/complete", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> resetPassword(@Valid @RequestBody RecoveryComplete payload,
            @RequestHeader("Idempotency-Key") String key, HttpServletRequest request) {
        validateRecoveryKey(key);
        int retryAfter = budget.retryAfter(AccountRequestBudget.Operation.RESET, request.getRemoteAddr(), null);
        if (retryAfter > 0) {
            return AccountAccessProblem.rateLimited(request, retryAfter);
        }
        if (payload.newPassword().codePointCount(0, payload.newPassword().length()) < 12) {
            return AccountAccessProblem.response(request, HttpStatus.UNPROCESSABLE_ENTITY,
                    "PASSWORD_TOO_SHORT", "The new password requires at least 12 characters");
        }
        if (!access.resetPassword(payload.token(), payload.newPassword(), CorrelationIdFilter.getCorrelationId(request))) {
            return AccountAccessProblem.response(request, HttpStatus.UNPROCESSABLE_ENTITY,
                    "INVALID_RECOVERY_LINK", "The recovery link cannot be used");
        }
        return json(HttpStatus.OK, acknowledgement(request));
    }

    private SessionView sessionView(AuthenticatedAccount account, HttpSession session, String returnTo) {
        return new SessionView(AccountView.from(account.account()), account.activeMembershipIds(),
                account.activePortalLinkIds(), lifetime.expiresAt(session), returnTo, null);
    }

    private static String safeReturn(String candidate, AuthenticatedAccount account) {
        if (candidate != null && (account.activeMembershipIds().stream()
                .anyMatch(id -> (ACCOUNT_PATH + "/pratiques/" + id).equals(candidate))
                || account.activePortalLinkIds().stream()
                .anyMatch(id -> (ACCOUNT_PATH + "/patient/" + id).equals(candidate)))) {
            return candidate;
        }
        return ACCOUNT_PATH;
    }

    private static <T> ResponseEntity<T> json(HttpStatus status, T body) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(body);
    }

    private static Acknowledgement acknowledgement(HttpServletRequest request) {
        return new Acknowledgement(true, CorrelationIdFilter.getCorrelationId(request));
    }

    private static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    private static void validateRecoveryKey(String key) {
        if (!RECOVERY_KEY.matcher(key).matches()) {
            throw new InvalidIdempotencyKeyException("Invalid recovery idempotency key");
        }
    }

    public record Credentials(@NotBlank @Email @Size(max = 254) String email,
                              @NotNull @CodePointLength(min = 1, max = 1024) String password,
                              @Size(max = 128) String returnTo) {
        @Override public String toString() { return "Credentials"; }
    }

    public record RecoveryRequest(@NotBlank @Email @Size(max = 254) String email) {
        @Override public String toString() { return "RecoveryRequest"; }
    }

    public record RecoveryComplete(@NotNull @Size(min = 32, max = 512) String token,
                                   @NotNull @CodePointLength(max = 1024) String newPassword) {
        @Override public String toString() { return "RecoveryComplete"; }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SessionView(AccountView account, List<UUID> membershipIds, List<UUID> portalLinkIds,
                              Instant expiresAt, String returnTo, UUID activatedPortalLinkId) {}

    public record AccountView(UUID id, long version, Instant createdAt, Instant updatedAt,
                              String displayName, String email, String state, String locale) {
        static AccountView from(AccountProfile account) {
            String locale = account.locale().toLowerCase(Locale.ROOT).startsWith("en") ? "en-GB" : "fr-FR";
            return new AccountView(account.id(), account.version(), account.createdAt(), account.updatedAt(),
                    account.displayName(), account.normalizedEmail(), account.state(), locale);
        }
    }

    public record Acknowledgement(boolean accepted, String correlationId) {}
}
