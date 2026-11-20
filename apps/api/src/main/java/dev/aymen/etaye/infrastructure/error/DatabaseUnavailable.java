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
 * - SQLException whose SQLState starts with "08"
 * - CannotGetJdbcConnectionException
 * - CannotCreateTransactionException whose cause chain contains one of the former
 * - Hibernate JDBCConnectionException
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

    private static boolean isDirectConnectionSignal(Throwable t) {
        if (t instanceof SQLTransientConnectionException) {
            return true;
        }
        if (t instanceof SQLException sqlEx) {
            String sqlState = sqlEx.getSQLState();
            if (sqlState != null && sqlState.startsWith("08")) {
                return true;
            }
        }
        if (t instanceof CannotGetJdbcConnectionException) {
            return true;
        }
        if (t instanceof JDBCConnectionException) {
            return true;
        }
        return false;
    }
}
