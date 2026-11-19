package fr.etaye.infrastructure.transaction;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Session;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.PreparedStatement;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Single transactional entry point for establishing practice-scoped transactions.
 * Enforces:
 * 1. Opening the transaction boundary.
 * 2. Setting transaction-local 'etaye.practice_id' via bound parameter before domain access.
 * 3. Prohibiting context switching within an active transaction.
 * 4. Automatic transaction-local cleanup upon commit or rollback.
 */
@Component
public class PracticeTransaction {

    private final PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private final ThreadLocal<UUID> activePracticeId = new ThreadLocal<>();

    public PracticeTransaction(PlatformTransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    public <T> T execute(UUID practiceId, Supplier<T> action) {
        Objects.requireNonNull(practiceId, "practiceId must not be null");

        UUID current = activePracticeId.get();
        if (current != null && !current.equals(practiceId)) {
            throw new IllegalStateException(
                    "Cannot change practice context inside an active transaction. Current: " + current + ", requested: " + practiceId);
        }

        boolean isOuter = (current == null);
        TransactionTemplate template = new TransactionTemplate(transactionManager);

        try {
            return template.execute(status -> {
                if (isOuter) {
                    activePracticeId.set(practiceId);
                    setTransactionPracticeContext(practiceId);
                }
                return action.get();
            });
        } finally {
            if (isOuter) {
                activePracticeId.remove();
            }
        }
    }

    public void executeVoid(UUID practiceId, Runnable action) {
        execute(practiceId, () -> {
            action.run();
            return null;
        });
    }

    public <T> T executeWithoutContext(Supplier<T> action) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        return template.execute(status -> action.get());
    }

    public void executeVoidWithoutContext(Runnable action) {
        executeWithoutContext(() -> {
            action.run();
            return null;
        });
    }

    private void setTransactionPracticeContext(UUID practiceId) {
        Session session = entityManager.unwrap(Session.class);
        session.doWork(connection -> {
            // Parameterized query: never use string concatenation
            try (PreparedStatement ps = connection.prepareStatement("SELECT set_config('etaye.practice_id', ?, true)")) {
                ps.setString(1, practiceId.toString());
                ps.execute();
            }
        });
    }
}
