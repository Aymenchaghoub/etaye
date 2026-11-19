package dev.aymen.etaye.infrastructure.error;

import org.hibernate.exception.JDBCConnectionException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.transaction.CannotCreateTransactionException;

import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.util.HashSet;
import java.util.Set;

/**
 * Classifier for database connection failures. Walks the full cause chain
 * and suppressed exceptions, returning true for any exception indicating
 * the database is unreachable or connection broken.
 */
public final class DatabaseUnavailable {

    private DatabaseUnavailable() {
        // utility class
    }

    public static boolean isConnectionFailure(Throwable t) {
        if (t == null) {
            return false;
        }
        return checkThrowable(t, new HashSet<>());
    }

    private static boolean checkThrowable(Throwable t, Set<Throwable> visited) {
        if (t == null || !visited.add(t)) {
            return false;
        }
        if (isDirectConnectionFailure(t)) {
            return true;
        }
        if (checkThrowable(t.getCause(), visited)) {
            return true;
        }
        for (Throwable suppressed : t.getSuppressed()) {
            if (checkThrowable(suppressed, visited)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isDirectConnectionFailure(Throwable t) {
        if (t instanceof SQLTransientConnectionException) {
            return true;
        }
        if (t instanceof SQLException sqlEx) {
            String sqlState = sqlEx.getSQLState();
            if (sqlState != null && sqlState.startsWith("08")) {
                return true;
            }
            String msg = sqlEx.getMessage();
            if (msg != null && (msg.contains("Connection is closed")
                    || msg.contains("closed connection")
                    || msg.contains("Connection refused")
                    || msg.contains("The connection attempt failed")
                    || msg.contains("erreur d'entrée/sortie")
                    || msg.contains("An I/O error occurred")
                    || msg.contains("terminat")
                    || msg.contains("broken pipe"))) {
                return true;
            }
        }
        if (t instanceof CannotGetJdbcConnectionException) {
            return true;
        }
        if (t instanceof DataAccessResourceFailureException) {
            return true;
        }
        if (t instanceof TransientDataAccessResourceException) {
            return true;
        }
        if (t instanceof CannotCreateTransactionException) {
            return true;
        }
        if (t instanceof JDBCConnectionException) {
            return true;
        }
        if (t instanceof JpaSystemException) {
            String msg = t.getMessage();
            if (msg != null && (msg.contains("Connection") || msg.contains("rollback") || msg.contains("JDBC"))) {
                return true;
            }
        }
        if (t instanceof java.net.ConnectException || t instanceof java.net.SocketTimeoutException || t instanceof java.net.SocketException) {
            return true;
        }
        if (t instanceof java.sql.SQLTimeoutException) {
            return true;
        }
        String className = t.getClass().getName();
        if (className.contains("Hikari") && t.getMessage() != null &&
                (t.getMessage().contains("Connection") || t.getMessage().contains("timed out"))) {
            return true;
        }
        String msg = t.getMessage();
        if (msg != null && (
                msg.contains("Unable to rollback against JDBC Connection")
                || msg.contains("Connection is closed")
                || (msg.contains("Connection to") && msg.contains("refused"))
                || msg.contains("Connection marked as broken")
        )) {
            return true;
        }
        return false;
    }
}
