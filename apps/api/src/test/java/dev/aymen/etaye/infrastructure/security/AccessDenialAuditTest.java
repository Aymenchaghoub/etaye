package dev.aymen.etaye.infrastructure.security;

import dev.aymen.etaye.MutableTestClock;
import dev.aymen.etaye.audit.service.AuditService;
import dev.aymen.etaye.infrastructure.transaction.PracticeTransaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.HandlerMapping;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class AccessDenialAuditTest {

    private final MutableTestClock clock = new MutableTestClock(Instant.parse("2026-11-26T12:00:00Z"));

    @AfterEach
    void cleanTransactionSync() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void skipsNullPracticeOrMissingPatternAndRejectsAmbientTransaction() {
        List<String> recorded = new ArrayList<>();
        AuditService auditService = new AuditService(null) {
            @Override
            public void recordEvent(UUID practiceId, UUID actorAccountId, String eventType, String resourceType,
                                    UUID resourceId, Long resourceVersion, String outcome, String correlationId, String safeMetadataJson) {
                recorded.add(eventType + ":" + safeMetadataJson);
            }
        };
        PracticeTransaction tx = new PracticeTransaction(null, null) {
            @Override
            public void executeVoid(UUID practiceId, Runnable action) {
                action.run();
            }
        };
        var beans = new StaticListableBeanFactory();
        beans.addBean("clock", clock);
        var audit = new AccessDenialAudit(auditService, tx, new ObjectMapper(), beans.getBeanProvider(Clock.class));

        UUID actor = UUID.randomUUID();
        UUID practice = UUID.randomUUID();
        var denialNoPractice = new AuthorizationDenial(actor, null, AuthorizationDenial.Reason.NOT_VISIBLE, null);
        audit.record(denialNoPractice, new MockHttpServletRequest("GET", "/api/v1/practices/x"));

        var denial = new AuthorizationDenial(actor, practice, AuthorizationDenial.Reason.CAPABILITY_MISSING, "CLINICAL_WRITE");
        var missingPatternReq = new MockHttpServletRequest("POST", "/api/v1/practices/x/patients");
        audit.record(denial, missingPatternReq);

        missingPatternReq.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "   ");
        audit.record(denial, missingPatternReq);
        assertThat(recorded).isEmpty();

        var validReq = new MockHttpServletRequest("POST", "/api/v1/practices/" + practice + "/patients");
        validReq.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v1/practices/{practiceId}/patients");
        TransactionSynchronizationManager.setActualTransactionActive(true);
        assertThatIllegalStateException().isThrownBy(() -> audit.record(denial, validReq));

        // Cooldown started in finally despite active-transaction rejection; advance past cooldown window to record cleanly.
        TransactionSynchronizationManager.setActualTransactionActive(false);
        clock.advanceSeconds(61);
        audit.record(denial, validReq);
        assertThat(recorded).hasSize(1);
        assertThat(recorded.getFirst()).contains("CLINICAL_WRITE").contains("CAPABILITY_MISSING");
    }
}
