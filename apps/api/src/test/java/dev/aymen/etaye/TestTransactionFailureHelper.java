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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test-only failure injection and pause mechanism for verifying transaction rollback
 * across all five domain tables (C2) and deterministic concurrency overlap (CO-1).
 *
 * Registers a TransactionSynchronization during beforeCommit of a practice-scoped transaction
 * that calls entityManager.flush() (ensuring all 5 writes are pushed to PostgreSQL)
 * and then throws an exception or pauses until released by the test.
 */
@Component
public class TestTransactionFailureHelper {

    private final AtomicBoolean failInBeforeCommit = new AtomicBoolean(false);
    private final AtomicInteger reachedCount = new AtomicInteger(0);

    private final AtomicBoolean pauseInBeforeCommit = new AtomicBoolean(false);
    private final AtomicBoolean firstPaused = new AtomicBoolean(false);
    private volatile CountDownLatch reachedLatch;
    private volatile CountDownLatch releaseLatch;

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

    public void enablePauseInBeforeCommit(CountDownLatch reachedLatch, CountDownLatch releaseLatch) {
        this.reachedLatch = reachedLatch;
        this.releaseLatch = releaseLatch;
        this.firstPaused.set(false);
        this.pauseInBeforeCommit.set(true);
    }

    public void disablePauseInBeforeCommit() {
        this.pauseInBeforeCommit.set(false);
        this.firstPaused.set(false);
        this.reachedLatch = null;
        this.releaseLatch = null;
    }

    public void registerSynchronizationIfEnabled() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        if (practiceTransaction.getActivePracticeId() == null) {
            return;
        }
        if (!failInBeforeCommit.get() && !pauseInBeforeCommit.get()) {
            return;
        }
        if (TransactionSynchronizationManager.hasResource(this)) {
            return;
        }
        TransactionSynchronizationManager.bindResource(this, Boolean.TRUE);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void beforeCommit(boolean readOnly) {
                if (failInBeforeCommit.get() && practiceTransaction.getActivePracticeId() != null) {
                    reachedCount.incrementAndGet();
                    if (entityManager != null) {
                        entityManager.flush();
                    }
                    throw new RuntimeException("Simulated commit failure in beforeCommit after flushing domain writes");
                }
                if (pauseInBeforeCommit.get() && practiceTransaction.getActivePracticeId() != null) {
                    if (firstPaused.compareAndSet(false, true)) {
                        if (entityManager != null) {
                            entityManager.flush();
                        }
                        if (reachedLatch != null) {
                            reachedLatch.countDown();
                        }
                        if (releaseLatch != null) {
                            try {
                                boolean released = releaseLatch.await(15, TimeUnit.SECONDS);
                                if (!released) {
                                    throw new IllegalStateException("TestTransactionFailureHelper: release latch timed out after 15 seconds");
                                }
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                throw new RuntimeException("Interrupted while paused in beforeCommit", e);
                            }
                        }
                    }
                }
            }

            @Override
            public void afterCompletion(int status) {
                if (TransactionSynchronizationManager.hasResource(TestTransactionFailureHelper.this)) {
                    TransactionSynchronizationManager.unbindResource(TestTransactionFailureHelper.this);
                }
            }
        });
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
