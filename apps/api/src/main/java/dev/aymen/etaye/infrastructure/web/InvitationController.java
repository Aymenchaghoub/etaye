package dev.aymen.etaye.infrastructure.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.aymen.etaye.identity.dto.AuthenticatedAccount;
import dev.aymen.etaye.identity.service.InvitationAccessService;
import dev.aymen.etaye.infrastructure.error.IdempotencyConflictException;
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
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.CodePointLength;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

/**
 * Anonymous invitation endpoints. The token is only read from the body, never
 * from a URL; refusal wording stays generic so responses never enumerate
 * accounts, records or which verification input differed.
 */
@RestController
public class InvitationController {

    private static final Pattern BIRTH_DATE = Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}");

    private final InvitationAccessService invitations;
    private final AccountRequestBudget budget;
    private final BrowserSessionLifetime lifetime;
    private final boolean demoEnabled;

    public InvitationController(InvitationAccessService invitations, AccountRequestBudget budget,
                                BrowserSessionLifetime lifetime,
                                @Value("${etaye.demo.enabled:false}") boolean demoEnabled) {
        this.invitations = invitations;
        this.budget = budget;
        this.lifetime = lifetime;
        this.demoEnabled = demoEnabled;
    }

    @PostMapping(path = "/api/v1/invitations/inspect", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> inspect(@Valid @RequestBody InvitationInspect payload, HttpServletRequest request) {
        int retryAfter = budget.retryAfter(AccountRequestBudget.Operation.INVITE_INSPECT,
                request.getRemoteAddr(), null);
        if (retryAfter > 0) {
            return AccountAccessProblem.rateLimited(request, retryAfter);
        }
        return invitations.inspect(payload.token())
                .<ResponseEntity<?>>map(preview -> ResponseEntity.ok()
                        .cacheControl(CacheControl.noStore())
                        .body(new PreviewView(preview.purpose(), preview.practiceName(),
                                preview.recipientHint(), preview.expiresAt(), preview.eligibility(), preview.acceptedAt())))
                .orElseGet(() -> AccountAccessProblem.response(request, HttpStatus.NOT_FOUND,
                        "NOT_FOUND", "Resource not found"));
    }

    @PostMapping(path = "/api/v1/invitations/accept", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> accept(@Valid @RequestBody Activation payload,
                                    @RequestHeader("Idempotency-Key") String key,
                                    @RequestAttribute(name = IdentityResolutionFilter.AUTHENTICATED_ACCOUNT_ATTR,
                                            required = false) AuthenticatedAccount signedIn,
                                    HttpServletRequest request, HttpServletResponse response) {
        if (key.length() < 16 || key.length() > 128) {
            throw new InvalidIdempotencyKeyException("Invalid invitation idempotency key");
        }
        int retryAfter = budget.retryAfter(AccountRequestBudget.Operation.INVITE_ACCEPT,
                request.getRemoteAddr(), null);
        if (retryAfter > 0) {
            return AccountAccessProblem.rateLimited(request, retryAfter);
        }
        if (payload.password() != null
                && payload.password().codePointCount(0, payload.password().length()) < 12) {
            return AccountAccessProblem.response(request, HttpStatus.UNPROCESSABLE_ENTITY,
                    "PASSWORD_TOO_SHORT", "The password requires at least 12 characters");
        }
        // The pending team fixture accepts a user-entered name. The portal path derives its name from the record.
        if (demoEnabled && "etaye-fictional-invitation-INV-TEAM-01".equals(payload.token())
                && payload.displayName() != null && !"Alex Rivière".equals(payload.displayName().strip())) {
            return AccountAccessProblem.response(request, HttpStatus.UNPROCESSABLE_ENTITY,
                    "RECIPIENT_MISMATCH", "Use the published fictional identity");
        }
        LocalDate birthDate = parseBirthDate(payload.recipientVerification());

        var acceptance = invitations.accept(payload.token(), key, canonicalBody(payload),
                payload.displayName(), payload.password(), birthDate, signedIn,
                CorrelationIdFilter.getCorrelationId(request));
        if ("IDEMPOTENCY_CONFLICT".equals(acceptance.outcome())) {
            throw new IdempotencyConflictException("Idempotency key replayed with different request body");
        }

        var account = acceptance.account();
        if (account != null) {
            HttpSession session = lifetime.establish(request, response, account.account());
            return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                    .body(new AccountAccessController.SessionView(
                            AccountAccessController.AccountView.from(account.account()),
                            account.activeMembershipIds(), account.activePortalLinkIds(),
                            lifetime.expiresAt(session), null, acceptance.portalLinkId(), null));
        }
        return refusal(acceptance.outcome(), request);
    }

    private static ResponseEntity<?> refusal(String outcome, HttpServletRequest request) {
        return switch (outcome) {
            case "EXPIRED" -> AccountAccessProblem.response(request, HttpStatus.UNPROCESSABLE_ENTITY,
                    "INVITATION_EXPIRED", "This invitation has expired");
            case "UNAVAILABLE" -> AccountAccessProblem.response(request, HttpStatus.UNPROCESSABLE_ENTITY,
                    "INVITATION_UNAVAILABLE", "This practice cannot currently grant access");
            case "REVOKED" -> AccountAccessProblem.response(request, HttpStatus.UNPROCESSABLE_ENTITY,
                    "INVITATION_REVOKED", "This invitation is no longer valid");
            case "ALREADY_USED" -> AccountAccessProblem.response(request, HttpStatus.UNPROCESSABLE_ENTITY,
                    "INVITATION_ALREADY_USED", "This invitation has already been used");
            case "MISMATCH" -> AccountAccessProblem.response(request, HttpStatus.UNPROCESSABLE_ENTITY,
                    "RECIPIENT_MISMATCH", "This invitation cannot be activated with this information");
            case "SIGN_IN_REQUIRED" -> AccountAccessProblem.response(request, HttpStatus.UNPROCESSABLE_ENTITY,
                    "SIGN_IN_REQUIRED", "Sign in before accepting this invitation");
            case "DISPLAY_NAME_REQUIRED" -> AccountAccessProblem.response(request, HttpStatus.UNPROCESSABLE_ENTITY,
                    "DISPLAY_NAME_REQUIRED", "A display name is required to activate this invitation");
            case "PASSWORD_REQUIRED" -> AccountAccessProblem.response(request, HttpStatus.UNPROCESSABLE_ENTITY,
                    "PASSWORD_TOO_SHORT", "The password requires at least 12 characters");
            case "LINK_EXISTS" -> AccountAccessProblem.response(request, HttpStatus.CONFLICT,
                    "PORTAL_LINK_EXISTS", "This record already has an active patient link");
            case "MEMBERSHIP_EXISTS" -> AccountAccessProblem.response(request, HttpStatus.CONFLICT,
                    "MEMBERSHIP_EXISTS", "An active membership already exists with different permissions");
            case "UNKNOWN_TOKEN" -> AccountAccessProblem.response(request, HttpStatus.NOT_FOUND,
                    "NOT_FOUND", "Resource not found");
            default -> throw new IllegalStateException("Unexpected invitation acceptance outcome");
        };
    }

    private static LocalDate parseBirthDate(String verification) {
        if (verification == null || !BIRTH_DATE.matcher(verification).matches() || verification.startsWith("0000")) {
            return null;
        }
        try {
            return LocalDate.parse(verification);
        } catch (DateTimeParseException ex) {
            // A non-date verification value fails the generic mismatch check,
            // exactly like a wrong date; the response never says which input differed.
            return null;
        }
    }

    private static String canonicalBody(Activation payload) {
        return framed(payload.token()) + framed(payload.recipientVerification())
                + framed(payload.displayName()) + framed(payload.password());
    }

    private static String framed(String value) {
        return value == null ? "-1:" : value.length() + ":" + value;
    }

    public record InvitationInspect(@NotNull @Size(min = 32, max = 512) String token) {
        @Override public String toString() { return "InvitationInspect"; }
    }

    public record Activation(@NotNull @Size(min = 32, max = 512) String token,
                             @Size(min = 1, max = 512) String recipientVerification,
                             @Size(min = 1, max = 200) String displayName,
                             @CodePointLength(max = 1024) String password) {
        @Override public String toString() { return "Activation"; }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PreviewView(String purpose, String practiceName, String recipientHint,
                              Instant expiresAt, String eligibility, Instant acceptedAt) {}
}
