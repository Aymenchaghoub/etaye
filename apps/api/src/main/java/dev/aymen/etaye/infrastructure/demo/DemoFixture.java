package dev.aymen.etaye.infrastructure.demo;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Component
@ConditionalOnProperty(name = "etaye.demo.enabled", havingValue = "true")
public class DemoFixture {

    public static final String CATALOGUE_PATH = "/api/v1/demo/scenarios";
    public static final String RESET_PATH = "/api/v1/demo/reset";
    private static final UUID OPERATOR = UUID.fromString("2f18200a-ecb4-9aaa-9c5b-90342bb450e8");

    private final JdbcTemplate jdbc;
    // The local package runs one API replica. Hold reads through the Spring Session save.
    private final ReentrantReadWriteLock barrier = new ReentrantReadWriteLock(true);
    private final AtomicBoolean resetPending = new AtomicBoolean();

    public DemoFixture(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    void verifyDatabase() {
        Boolean dedicated = jdbc.queryForObject("""
                SELECT current_database() = 'etaye_demo' AND EXISTS (
                    SELECT FROM pg_namespace WHERE nspname = 'etaye_demo_bootstrap'
                        AND nspowner = 'postgres'::regrole)
                """, Boolean.class);
        if (!Boolean.TRUE.equals(dedicated) || version() == null) {
            throw new IllegalStateException("The demo requires a provisioned synthetic database");
        }
    }

    public Version version() {
        return jdbc.queryForObject("SELECT version, generation FROM etaye_demo.fixture",
                (row, index) -> new Version(row.getString(1), row.getLong(2)));
    }

    public static boolean isOperator(UUID accountId) {
        return OPERATOR.equals(accountId);
    }

    boolean tryEnter() {
        return !resetPending.get() && barrier.readLock().tryLock();
    }

    void leave() {
        barrier.readLock().unlock();
    }

    Reset reset(UUID accountId, String sessionId, String key, String reason, String correlation)
            throws InterruptedException {
        if (!isOperator(accountId)) return new Reset(Outcome.FORBIDDEN, correlation, 0);
        if (!resetPending.compareAndSet(false, true)) return new Reset(Outcome.BUSY, correlation, 0);
        try {
            var resetLock = barrier.writeLock();
            if (!resetLock.tryLock(2, TimeUnit.SECONDS)) {
                return new Reset(Outcome.BUSY, correlation, 0);
            }
            try {
                return jdbc.query(connection -> {
                    var statement = connection.prepareStatement("SELECT * FROM etaye_demo.reset_fixture(?, ?, ?, ?, ?)");
                    statement.setQueryTimeout(2);
                    statement.setObject(1, accountId);
                    statement.setString(2, sessionId);
                    statement.setString(3, digest(key));
                    statement.setString(4, digest(reason));
                    statement.setString(5, correlation);
                    return statement;
                }, result -> {
                    if (!result.next()) throw new IllegalStateException("The fixture reset returned no outcome");
                    return new Reset(Outcome.valueOf(result.getString(1)), result.getString(2), result.getInt(3));
                });
            } catch (QueryTimeoutException ex) {
                return new Reset(Outcome.UNAVAILABLE, correlation, 0);
            } finally {
                resetLock.unlock();
            }
        } finally {
            resetPending.set(false);
        }
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    public record Version(String version, long generation) {}

    enum Outcome { RESET, REPLAYED, BUSY, RATE_LIMITED, CONFLICT, UNAUTHENTICATED, FORBIDDEN, UNAVAILABLE }

    record Reset(Outcome outcome, String correlationId, int retryAfter) {}
}
