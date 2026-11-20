package dev.aymen.etaye.infrastructure.security;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Component
public class AccountRequestBudget {

    public enum Operation {
        CSRF(120, 12, 0), SIGN_IN(120, 20, 8), REQUEST_RECOVERY(120, 12, 5), RESET(120, 12, 0);

        private final int global;
        private final int byAddress;
        private final int byEmail;

        Operation(int global, int byAddress, int byEmail) {
            this.global = global;
            this.byAddress = byAddress;
            this.byEmail = byEmail;
        }
    }

    private static final long WINDOW = Duration.ofMinutes(1).toNanos();
    private final Map<Operation, Window> operations = new java.util.EnumMap<>(Operation.class);

    public synchronized int retryAfter(Operation operation, String directAddress, String email) {
        long now = System.nanoTime();
        Window window = operations.compute(operation, (key, previous) ->
                previous == null || now - previous.startedAt >= WINDOW ? new Window(now) : previous);
        if (window.requests >= operation.global
                || window.addresses.getOrDefault(directAddress, 0) >= operation.byAddress
                || (operation.byEmail > 0 && window.emails.getOrDefault(email, 0) >= operation.byEmail)) {
            return (int) Math.ceil((WINDOW - (now - window.startedAt)) / 1_000_000_000.0);
        }
        window.requests++;
        window.addresses.merge(directAddress, 1, Integer::sum);
        if (operation.byEmail > 0) {
            window.emails.merge(email, 1, Integer::sum);
        }
        return 0;
    }

    private static final class Window {
        private final long startedAt;
        private int requests;
        private final Map<String, Integer> addresses = new HashMap<>();
        private final Map<String, Integer> emails = new HashMap<>();

        private Window(long startedAt) {
            this.startedAt = startedAt;
        }
    }
}
