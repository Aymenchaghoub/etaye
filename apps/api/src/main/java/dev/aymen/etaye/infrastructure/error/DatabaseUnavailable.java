package dev.aymen.etaye.infrastructure.error;

import org.hibernate.exception.JDBCConnectionException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;

import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.util.HashSet;
import java.util.Set;

/**
 * Classifier for database connection failures. Walks the cause chain
 * and returns true ONLY for reliable JDBC connection signals:
 * - SQLTransientConnectionException
 * - SQLException whose SQLState starts with "08" (connection exception) or is 57P01, 57P02 or 57P03
 *   (admin shutdown, crash shutdown, cannot connect now): PostgreSQL answers these while it stops
 * - CannotGetJdbcConnectionException
 * - CannotCreateTransactionException whose cause chain contains one of the former
 * - Hibernate JDBCConnectionException
 * - a failed rollback or commit (Hibernate or Spring TransactionException) caused by Hikari's closed-connection
 *   SQLException (no SQLState, message "Connection is closed"): the pool already evicted a connection that broke
 *   on a socket timeout and the rollback attempt masks the original error
 */
public final class DatabaseUnavailable {

    private DatabaseUnavailable() {
        // utility class
    }

    public static boolean isConnectionFailure(Throwable t) {
        if (t == null) {
            return false;
        }
        return checkCauseChain(t, new HashSet<>());
    }

    private static boolean checkCauseChain(Throwable t, Set<Throwable> visited) {
        if (t == null || !visited.add(t)) {
            return false;
        }
        if (isDirectConnectionSignal(t)) {
            return true;
        }
        return checkCauseChain(t.getCause(), visited);
    }

    private static boolean isShutdownState(String sqlState) {
        return "57P01".equals(sqlState) || "57P02".equals(sqlState) || "57P03".equals(sqlState);
    }

    private static boolean isDirectConnectionSignal(Throwable t) {
        if (t instanceof SQLTransientConnectionException) {
            return true;
        }
        if (t instanceof SQLException sqlEx) {
            String sqlState = sqlEx.getSQLState();
            if (sqlState != null && (sqlState.startsWith("08") || isShutdownState(sqlState))) {
                return true;
            }
        }
        if (t instanceof CannotGetJdbcConnectionException) {
            return true;
        }
        if ((t instanceof org.hibernate.TransactionException || t instanceof org.springframework.transaction.TransactionSystemException)
                && t.getCause() instanceof SQLException closed
                && closed.getSQLState() == null
                && "Connection is closed".equals(closed.getMessage())) {
            return true;
        }
        if (t instanceof JDBCConnectionException) {
            return true;
        }
        return false;
    }
}
