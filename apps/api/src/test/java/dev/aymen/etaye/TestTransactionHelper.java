package dev.aymen.etaye;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/**
 * Test-only transaction helper for executing database operations outside
 * a practice context (e.g. to assert fail-closed / denial behavior).
 */
@Component
public class TestTransactionHelper {

    private final PlatformTransactionManager transactionManager;

    public TestTransactionHelper(PlatformTransactionManager transactionManager) {
        this.transactionManager = transactionManager;
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
}
