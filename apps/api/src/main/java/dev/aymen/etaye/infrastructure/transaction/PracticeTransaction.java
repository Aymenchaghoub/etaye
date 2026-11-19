package dev.aymen.etaye.infrastructure.transaction;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Session;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Single transactional entry point for establishing practice-scoped transactions.
 * Enforces:
 * 1. Opening the transaction boundary marked with transaction name "etaye-practice-{practiceId}".
 * 2. Outermost call REFUSES to join an already active transaction without practice context.
 * 3. Nested call for the same practice joins; nested call for a different practice is rejected.
 * 4. Any other active transaction (including a REQUIRES_NEW inner transaction that suspended ours) is refused.
 * 5. Setting transaction-local 'etaye.practice_id' via bound parameter before domain access.
 * 6. Automatic transaction-local cleanup upon commit or rollback.
 * Uses TransactionSynchronizationManager.getCurrentTransactionName() with NO ThreadLocal.
 */
@Component
public class PracticeTransaction {

    public static final String PRACTICE_TX_PREFIX = "etaye-practice-";

    @FunctionalInterface
    public interface ConnectionLifecycleListener {
        void onPracticeContextSetting(Connection connection, UUID practiceId, String contextBeforeSetting, long backendPid);
    }

    private final PlatformTransactionManager transactionManager;
    private final ObjectProvider<ConnectionLifecycleListener> connectionLifecycleListenerProvider;

    @PersistenceContext
    private EntityManager entityManager;

    public PracticeTransaction(PlatformTransactionManager transactionManager,
                               ObjectProvider<ConnectionLifecycleListener> connectionLifecycleListenerProvider) {
        this.transactionManager = transactionManager;
        this.connectionLifecycleListenerProvider = connectionLifecycleListenerProvider;
    }

    public <T> T execute(UUID practiceId, Supplier<T> action) {
        Objects.requireNonNull(practiceId, "practiceId must not be null");

        String currentTxName = TransactionSynchronizationManager.getCurrentTransactionName();
        boolean txActive = TransactionSynchronizationManager.isActualTransactionActive();

        if (txActive) {
            if (currentTxName == null || !currentTxName.startsWith(PRACTICE_TX_PREFIX)) {
                throw new IllegalStateException(
                        "Outermost PracticeTransaction cannot join an already active transaction without practice context (or where transaction was suspended). Current tx: " + currentTxName);
            }
            UUID currentPracticeId;
            try {
                currentPracticeId = UUID.fromString(currentTxName.substring(PRACTICE_TX_PREFIX.length()));
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("Malformed practice transaction name: " + currentTxName, e);
            }

            if (!currentPracticeId.equals(practiceId)) {
                throw new IllegalStateException(
                        "Cannot change practice context inside an active transaction. Current: " + currentPracticeId + ", requested: " + practiceId);
            }

            // Same practice joins existing transaction
            TransactionTemplate joinTemplate = new TransactionTemplate(transactionManager);
            joinTemplate.setName(PRACTICE_TX_PREFIX + practiceId);
            return joinTemplate.execute(status -> action.get());
        }

        // Outermost transaction
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setName(PRACTICE_TX_PREFIX + practiceId);

        return template.execute(status -> {
            setTransactionPracticeContext(practiceId);
            return action.get();
        });
    }

    public void executeVoid(UUID practiceId, Runnable action) {
        execute(practiceId, () -> {
            action.run();
            return null;
        });
    }

    public UUID getActivePracticeId() {
        String name = TransactionSynchronizationManager.getCurrentTransactionName();
        if (name != null && name.startsWith(PRACTICE_TX_PREFIX)) {
            try {
                return UUID.fromString(name.substring(PRACTICE_TX_PREFIX.length()));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return null;
    }

    private void setTransactionPracticeContext(UUID practiceId) {
        Session session = entityManager.unwrap(Session.class);
        session.doWork(connection -> {
            String priorContext = null;
            long backendPid = 0L;
            try (PreparedStatement checkPs = connection.prepareStatement(
                    "SELECT NULLIF(current_setting('etaye.practice_id', true), ''), pg_backend_pid()");
                 ResultSet rs = checkPs.executeQuery()) {
                if (rs.next()) {
                    priorContext = rs.getString(1);
                    backendPid = rs.getLong(2);
                    if (priorContext != null) {
                        throw new IllegalStateException("Leaked practice context detected before setting practiceId: " + priorContext);
                    }
                }
            }

            ConnectionLifecycleListener listener = connectionLifecycleListenerProvider.getIfAvailable();
            if (listener != null) {
                listener.onPracticeContextSetting(connection, practiceId, priorContext, backendPid);
            }

            // Parameterized query: never use string concatenation
            try (PreparedStatement ps = connection.prepareStatement("SELECT set_config('etaye.practice_id', ?, true)")) {
                ps.setString(1, practiceId.toString());
                ps.execute();
            }
        });
    }
}
