package dev.aymen.etaye.infrastructure.error;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.transaction.CannotCreateTransactionException;

import java.net.SocketException;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseUnavailableTest {

    static Stream<Arguments> classifierCases() {
        return Stream.of(
                // TRUE cases:
                Arguments.of(
                        "SQLState 08006 nested in JpaSystemException",
                        new JpaSystemException(new RuntimeException(new SQLException("connection lost", "08006"))),
                        true
                ),
                Arguments.of(
                        "Hikari SQLTransientConnectionException",
                        new SQLTransientConnectionException("Connection is not available, request timed out after 30000ms."),
                        true
                ),
                Arguments.of(
                        "CannotCreateTransactionException over a connection failure",
                        new CannotCreateTransactionException("Could not open JDBC Connection for transaction",
                                new CannotGetJdbcConnectionException("Failed to obtain JDBC Connection", new SQLException("timeout", "08001"))),
                        true
                ),

                Arguments.of(
                        "SQLState 57P01 admin_shutdown while PostgreSQL stops (seen on the CI runner)",
                        new org.springframework.dao.DataAccessResourceFailureException("PreparedStatementCallback", new SQLException("terminating connection due to administrator command", "57P01")),
                        true
                ),
                Arguments.of(
                        "SQLState 57P03 cannot_connect_now",
                        new SQLException("the database system is shutting down", "57P03"),
                        true
                ),

                // FALSE cases:
                Arguments.of(
                        "SQLState 57014 query_canceled",
                        new SQLException("canceling statement due to user request", "57014"),
                        false
                ),
                Arguments.of(
                        "bare SocketException",
                        new SocketException("Connection reset"),
                        false
                ),
                Arguments.of(
                        "RuntimeException whose message says 'connection refused'",
                        new RuntimeException("Connection refused (Connection refused)"),
                        false
                ),
                Arguments.of(
                        "SQLException 23505",
                        new SQLException("duplicate key value violates unique constraint", "23505"),
                        false
                ),
                Arguments.of(
                        "DataIntegrityViolationException",
                        new DataIntegrityViolationException("data integrity violation", new SQLException("constraint violation", "23505")),
                        false
                ),
                Arguments.of(
                        "JpaSystemException over SQLState 23505",
                        new JpaSystemException(new RuntimeException(new SQLException("duplicate key", "23505"))),
                        false
                )
        );
    }

    @ParameterizedTest(name = "{0} -> expected {2}")
    @MethodSource("classifierCases")
    @DisplayName("C2: Table-driven test of DatabaseUnavailable classifier")
    void testOutageClassifier(String description, Throwable throwable, boolean expectedResult) {
        boolean result = DatabaseUnavailable.isConnectionFailure(throwable);
        assertThat(result)
                .as("Classification for '%s' must be %b", description, expectedResult)
                .isEqualTo(expectedResult);
    }
}
