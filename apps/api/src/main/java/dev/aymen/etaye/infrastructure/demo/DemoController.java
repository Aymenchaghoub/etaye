package dev.aymen.etaye.infrastructure.demo;

import dev.aymen.etaye.identity.dto.AuthenticatedAccount;
import dev.aymen.etaye.infrastructure.error.InvalidIdempotencyKeyException;
import dev.aymen.etaye.infrastructure.security.AccountAccessProblem;
import dev.aymen.etaye.infrastructure.security.CorrelationIdFilter;
import dev.aymen.etaye.infrastructure.security.IdentityResolutionFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.CodePointLength;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.regex.Pattern;

@RestController
@ConditionalOnProperty(name = "etaye.demo.enabled", havingValue = "true")
public class DemoController {

    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9_-]{16,128}");
    private static final String PRACTICE = "Cabinet des Tilleuls";
    private static final String LIMITS = "Données fictives uniquement. Les modifications disparaissent à la réinitialisation.";
    private static final List<Actor> ACTORS = List.of(
            new Actor("praticienne", "Praticienne", "Dr Nora Lefebvre", "nora.lefebvre@example.test",
                    "Ouvrir l’espace praticien du cabinet et retrouver ses accès."),
            new Actor("patiente", "Patiente", "Claire Martin", "claire.martin@example.test",
                    "Ouvrir son espace patient et retrouver le lien vers son dossier fictif."),
            new Actor("administration", "Administration", "Mila Dubois", "mila.dubois@example.test",
                    "Ouvrir l’espace administratif du cabinet et constater que les dossiers cliniques restent réservés aux personnes habilitées."));
    private static final List<String> CURSORS = List.of("", "d91_8bd5", "d91_37fa");

    private final DemoFixture fixture;

    public DemoController(DemoFixture fixture) {
        this.fixture = fixture;
    }

    @GetMapping(path = DemoFixture.CATALOGUE_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> list(@RequestParam(defaultValue = "") String cursor,
                                  @RequestParam(defaultValue = "25") int limit,
                                  HttpServletRequest request) {
        int start = CURSORS.indexOf(cursor);
        if (start < 0 || limit < 1 || limit > 100) {
            return AccountAccessProblem.response(request, HttpStatus.BAD_REQUEST,
                    "INVALID_INPUT", "Invalid scenario page");
        }
        int end = Math.min(start + limit, ACTORS.size());
        String version = fixture.version().version();
        List<Scenario> items = ACTORS.subList(start, end).stream()
                .map(actor -> new Scenario(actor.id(), actor.role(), actor.name(), actor.role(), PRACTICE,
                        actor.journey(), LIMITS, actor.email(), version, true, true))
                .toList();
        return json(HttpStatus.OK, new ScenarioPage(items, end < ACTORS.size() ? CURSORS.get(end) : null));
    }

    @PostMapping(path = DemoFixture.RESET_PATH, consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> reset(@RequestHeader("Idempotency-Key") String key, @Valid @RequestBody ResetReason body,
                                   @RequestAttribute(IdentityResolutionFilter.AUTHENTICATED_ACCOUNT_ATTR)
                                   AuthenticatedAccount account, HttpServletRequest request) throws InterruptedException {
        if (!KEY.matcher(key).matches()) {
            throw new InvalidIdempotencyKeyException("Invalid demo reset idempotency key");
        }
        var session = request.getSession(false);
        if (session == null) {
            return AccountAccessProblem.response(request, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication required");
        }
        var result = fixture.reset(account.account().id(), session.getId(), key, body.reason(),
                CorrelationIdFilter.getCorrelationId(request));
        return switch (result.outcome()) {
            case RESET -> {
                session.invalidate();
                yield json(HttpStatus.ACCEPTED, new Acknowledgement(true, result.correlationId()));
            }
            case REPLAYED -> json(HttpStatus.ACCEPTED, new Acknowledgement(true, result.correlationId()));
            case UNAVAILABLE -> AccountAccessProblem.response(request, HttpStatus.SERVICE_UNAVAILABLE,
                    "SERVICE_UNAVAILABLE", "The synthetic fixture could not be restored");
            case BUSY -> AccountAccessProblem.response(request, HttpStatus.CONFLICT,
                    "DEMO_RESET_IN_PROGRESS", "The synthetic fixture is being restored");
            case RATE_LIMITED -> AccountAccessProblem.rateLimited(request, result.retryAfter());
            case CONFLICT -> AccountAccessProblem.response(request, HttpStatus.CONFLICT,
                    "IDEMPOTENCY_CONFLICT", "The key was already used for a different request");
            case UNAUTHENTICATED -> AccountAccessProblem.response(request, HttpStatus.UNAUTHORIZED,
                    "UNAUTHENTICATED", "Authentication required");
            case FORBIDDEN -> AccountAccessProblem.response(request, HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Access denied");
        };
    }

    private static <T> ResponseEntity<T> json(HttpStatus status, T body) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(body);
    }

    private record Actor(String id, String role, String name, String email, String journey) {}

    private record Scenario(String id, String name, String actorName, String roleLabel, String practiceName,
                            String journeySummary, String limitsNotice, String signInIdentifier, String datasetVersion,
                            boolean synthetic, boolean available) {}

    private record ScenarioPage(List<Scenario> items, String nextCursor) {}

    private record Acknowledgement(boolean accepted, String correlationId) {}

    public record ResetReason(@NotBlank @CodePointLength(max = 1000) String reason) {
        @Override public String toString() { return "ResetReason"; }
    }
}
