package dev.aymen.etaye.infrastructure.transaction;

import dev.aymen.etaye.AbstractPostgresTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
public class PracticeTransactionSuspensionTest extends AbstractPostgresTest {

    @Autowired
    private PracticeTransaction practiceTransaction;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private UUID practiceAId;
    private UUID practiceBId;

    @BeforeEach
    void setUp() {
        practiceAId = createPracticeAsOwner("Suspension Practice A");
        practiceBId = createPracticeAsOwner("Suspension Practice B");
    }

    @Test
    @DisplayName("B3: PracticeTransaction suspension and resume test with REQUIRES_NEW inner transaction")
    void checkSuspensionAndResumeWithRequiresNew() {
        practiceTransaction.executeVoid(practiceAId, () -> {
            // In outer Practice A transaction
            assertThat(practiceTransaction.getActivePracticeId()).isEqualTo(practiceAId);

            // Inner transaction with REQUIRES_NEW (suspends outer transaction)
            TransactionTemplate innerRequiresNew = new TransactionTemplate(transactionManager);
            innerRequiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            innerRequiresNew.setName("suspended-tx-inner");

            innerRequiresNew.execute(innerStatus -> {
                // While suspended:
                // 1. Any attempt to call practiceTransaction for practice A is refused
                assertThatThrownBy(() -> practiceTransaction.execute(practiceAId, () -> "forbidden-while-suspended"))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("without practice context");

                // 2. Any attempt to call practiceTransaction for practice B is refused
                assertThatThrownBy(() -> practiceTransaction.execute(practiceBId, () -> "forbidden-while-suspended"))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("without practice context");

                return null;
            });

            // Resumed outer Practice A transaction
            assertThat(practiceTransaction.getActivePracticeId()).isEqualTo(practiceAId);

            // Subsequent call for Practice A succeeds upon resume
            String resumed = practiceTransaction.execute(practiceAId, () -> "resumed-successfully");
            assertThat(resumed).isEqualTo("resumed-successfully");
        });
    }
}
