package dev.aymen.etaye.infrastructure.transaction;

import dev.aymen.etaye.AbstractPostgresTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
public class PracticeTransactionTest extends AbstractPostgresTest {

    @Autowired
    private PracticeTransaction practiceTransaction;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("A7: A foreign transaction named exactly etaye-practice-{uuid} is refused by PracticeTransaction")
    void foreignTransactionWithForgedPracticeNameIsRefused() {
        UUID practiceId = UUID.randomUUID();
        TransactionTemplate foreignTemplate = new TransactionTemplate(transactionManager);
        foreignTemplate.setName(PracticeTransaction.PRACTICE_TX_PREFIX + practiceId);
        foreignTemplate.execute(status -> {
            assertThatThrownBy(() -> practiceTransaction.execute(practiceId, () -> "forbidden"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Outermost PracticeTransaction cannot join an already active transaction without practice context");
            return null;
        });
    }

    @Test
    @DisplayName("A7: REQUIRES_NEW inner stays refused and outer transaction works again after resume")
    void requiresNewInnerStaysRefusedAndOuterWorksAfterResume() {
        UUID practiceId = UUID.randomUUID();
        practiceTransaction.executeVoid(practiceId, () -> {
            // Inside active PracticeTransaction: marker must be present
            assertThat(practiceTransaction.getActivePracticeId()).isEqualTo(practiceId);

            // Inner REQUIRES_NEW transaction suspends the outer transaction and its synchronizations
            TransactionTemplate requiresNew = new TransactionTemplate(transactionManager);
            requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            requiresNew.execute(status -> {
                // In suspended state: practiceTransaction must refuse to join because marker is suspended
                assertThat(practiceTransaction.getActivePracticeId()).isNull();
                assertThatThrownBy(() -> practiceTransaction.execute(practiceId, () -> "forbidden-inner"))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("Outermost PracticeTransaction cannot join an already active transaction without practice context");
                return null;
            });

            // After inner transaction completes and outer transaction resumes:
            assertThat(practiceTransaction.getActivePracticeId()).isEqualTo(practiceId);
            String resumed = practiceTransaction.execute(practiceId, () -> "outer-resumed-success");
            assertThat(resumed).isEqualTo("outer-resumed-success");
        });
    }

    @Test
    @DisplayName("A7: Same practice nested joins existing transaction")
    void samePracticeNestedJoins() {
        UUID practiceId = UUID.randomUUID();
        String result = practiceTransaction.execute(practiceId, () -> {
            assertThat(practiceTransaction.getActivePracticeId()).isEqualTo(practiceId);
            return practiceTransaction.execute(practiceId, () -> "nested-success");
        });
        assertThat(result).isEqualTo("nested-success");
    }

    @Test
    @DisplayName("A7: Different practice nested in active transaction is rejected")
    void differentPracticeNestedIsRejected() {
        UUID practiceA = UUID.randomUUID();
        UUID practiceB = UUID.randomUUID();
        practiceTransaction.executeVoid(practiceA, () -> {
            assertThatThrownBy(() -> practiceTransaction.execute(practiceB, () -> "forbidden-switch"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot change practice context inside an active transaction");
        });
    }

    @Test
    @DisplayName("C5: Practice marker is deactivated in afterCommit and afterCompletion callbacks on commit")
    void markerDeactivatedInAfterCommitAndAfterCompletionOnCommit() {
        UUID practiceId = UUID.randomUUID();
        AtomicBoolean afterCommitSawNull = new AtomicBoolean(false);
        AtomicBoolean afterCompletionSawNull = new AtomicBoolean(false);

        practiceTransaction.executeVoid(practiceId, () -> {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    afterCommitSawNull.set(practiceTransaction.getActivePracticeId() == null);
                }

                @Override
                public void afterCompletion(int status) {
                    afterCompletionSawNull.set(practiceTransaction.getActivePracticeId() == null);
                }
            });
        });

        assertThat(afterCommitSawNull.get()).isTrue();
        assertThat(afterCompletionSawNull.get()).isTrue();
    }

    @Test
    @DisplayName("C5: Practice marker is deactivated in afterCompletion callback on rollback")
    void markerDeactivatedInAfterCompletionOnRollback() {
        UUID practiceId = UUID.randomUUID();
        AtomicBoolean afterCompletionSawNull = new AtomicBoolean(false);

        assertThatThrownBy(() -> {
            practiceTransaction.executeVoid(practiceId, () -> {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        afterCompletionSawNull.set(practiceTransaction.getActivePracticeId() == null);
                    }
                });
                throw new RuntimeException("Simulated rollback");
            });
        }).isInstanceOf(RuntimeException.class).hasMessage("Simulated rollback");

        assertThat(afterCompletionSawNull.get()).isTrue();
    }
}
