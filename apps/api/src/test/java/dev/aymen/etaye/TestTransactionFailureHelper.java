package dev.aymen.etaye;

import dev.aymen.etaye.infrastructure.transaction.PracticeTransaction;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.ResourceTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test-only failure injection mechanism for verifying transaction rollback
 * across all five domain tables (C2).
 *
 * Registers a TransactionSynchronization during beforeCommit of a practice-scoped transaction
 * that calls entityManager.flush() (ensuring all 5 writes are pushed to PostgreSQL)
 * and then throws an exception, with a counter proving the synchronization was reached.
 */
@Component
public class TestTransactionFailureHelper {

    private final AtomicBoolean failInBeforeCommit = new AtomicBoolean(false);
    private final AtomicInteger reachedCount = new AtomicInteger(0);
    private final PracticeTransaction practiceTransaction;

    @PersistenceContext
    private EntityManager entityManager;

    public TestTransactionFailureHelper(PracticeTransaction practiceTransaction) {
        this.practiceTransaction = practiceTransaction;
    }

    public void enableFailureInBeforeCommit() {
        reachedCount.set(0);
        failInBeforeCommit.set(true);
    }

    public void disableFailureInBeforeCommit() {
        failInBeforeCommit.set(false);
    }

    public int getReachedCount() {
        return reachedCount.get();
    }

    public void registerSynchronizationIfEnabled() {
        if (failInBeforeCommit.get()
                && practiceTransaction.getActivePracticeId() != null
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void beforeCommit(boolean readOnly) {
                    reachedCount.incrementAndGet();
                    if (entityManager != null) {
                        entityManager.flush();
                    }
                    throw new RuntimeException("Simulated commit failure in beforeCommit after flushing domain writes");
                }
            });
        }
    }

    @Configuration
    static class TestTransactionFailureConfig {

        @Bean
        public static BeanPostProcessor transactionManagerFailurePostProcessor(ObjectProvider<TestTransactionFailureHelper> helperProvider) {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                    if (bean instanceof PlatformTransactionManager tm && "transactionManager".equals(beanName)) {
                        return new DelegatingTestTransactionManager(tm, helperProvider);
                    }
                    return bean;
                }
            };
        }
    }

    private static class DelegatingTestTransactionManager implements ResourceTransactionManager {
        private final PlatformTransactionManager delegate;
        private final ObjectProvider<TestTransactionFailureHelper> helperProvider;

        public DelegatingTestTransactionManager(PlatformTransactionManager delegate, ObjectProvider<TestTransactionFailureHelper> helperProvider) {
            this.delegate = delegate;
            this.helperProvider = helperProvider;
        }

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) throws TransactionException {
            TransactionStatus status = delegate.getTransaction(definition);
            TestTransactionFailureHelper helper = helperProvider.getIfAvailable();
            if (helper != null) {
                helper.registerSynchronizationIfEnabled();
            }
            return status;
        }

        @Override
        public void commit(TransactionStatus status) throws TransactionException {
            delegate.commit(status);
        }

        @Override
        public void rollback(TransactionStatus status) throws TransactionException {
            delegate.rollback(status);
        }

        @Override
        public Object getResourceFactory() {
            if (delegate instanceof ResourceTransactionManager rtm) {
                return rtm.getResourceFactory();
            }
            return null;
        }
    }
}
