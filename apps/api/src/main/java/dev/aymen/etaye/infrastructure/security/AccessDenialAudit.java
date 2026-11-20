package dev.aymen.etaye.infrastructure.security;

import dev.aymen.etaye.audit.service.AuditService;
import dev.aymen.etaye.infrastructure.transaction.PracticeTransaction;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.HandlerMapping;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class AccessDenialAudit {

    private final AuditService auditService;
    private final PracticeTransaction practiceTransaction;
    private final ObjectMapper objectMapper;
    private final RateLimiter rateLimiter;

    public AccessDenialAudit(AuditService auditService, PracticeTransaction practiceTransaction,
                             ObjectMapper objectMapper, ObjectProvider<Clock> clockProvider) {
        this.auditService = auditService;
        this.practiceTransaction = practiceTransaction;
        this.objectMapper = objectMapper;
        this.rateLimiter = new RateLimiter(clockProvider.getIfAvailable(Clock::systemUTC));
    }

    public void record(AuthorizationDenial denial, HttpServletRequest request) {
        UUID practiceId = denial.auditPracticeId();
        if (practiceId == null) {
            return;
        }

        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (!(pattern instanceof String template) || template.isBlank()) {
            return;
        }
        String operation = request.getMethod() + " " + template;
        Key key = new Key(denial.actorAccountId(), operation, denial.reason());
        if (!rateLimiter.acquire(key)) {
            return;
        }

        try {
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                throw new IllegalStateException("Denial audit requires its own practice transaction");
            }
            Map<String, String> metadata = new LinkedHashMap<>();
            metadata.put("reason", denial.reason().name());
            metadata.put("operation", operation);
            if (denial.capability() != null) {
                metadata.put("capability", denial.capability());
            }
            String metadataJson = objectMapper.writeValueAsString(metadata);
            String correlationId = CorrelationIdFilter.getCorrelationId(request);
            practiceTransaction.executeVoid(practiceId, () -> auditService.recordEvent(
                    practiceId, denial.actorAccountId(), "ACCESS_DENIED", "API_OPERATION",
                    null, null, "DENIED", correlationId, metadataJson));
        } finally {
            rateLimiter.finish(key);
        }
    }

    record Key(UUID actorAccountId, String operation, AuthorizationDenial.Reason reason) {}

    // Per application instance and reset on restart because the runtime role cannot read the audit table.
    static final class RateLimiter {
        private static final int MAX_ENTRIES = 10_000;
        private static final long WINDOW_SECONDS = 60;

        private final Clock clock;
        private final Set<Key> inFlight = new HashSet<>();
        private final LinkedHashMap<Key, Instant> cooldowns = new LinkedHashMap<>();

        RateLimiter(Clock clock) {
            this.clock = clock;
        }

        synchronized boolean acquire(Key key) {
            Instant now = clock.instant();
            while (!cooldowns.isEmpty() && !cooldowns.firstEntry().getValue().isAfter(now)) {
                cooldowns.pollFirstEntry();
            }
            if (cooldowns.containsKey(key) || inFlight.size() + cooldowns.size() >= MAX_ENTRIES) {
                return false;
            }
            return inFlight.add(key);
        }

        synchronized void finish(Key key) {
            inFlight.remove(key);
            Instant expiresAt = clock.instant().plusSeconds(WINDOW_SECONDS);
            // Preserve expiry order if the wall clock moves backwards.
            Map.Entry<Key, Instant> latest = cooldowns.lastEntry();
            if (latest != null && latest.getValue().isAfter(expiresAt)) {
                expiresAt = latest.getValue();
            }
            cooldowns.put(key, expiresAt);
        }
    }
}
