package dev.aymen.etaye.infrastructure.error;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatabaseUnavailableFilterTest {

    private DatabaseUnavailableFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        filter = new DatabaseUnavailableFilter(new ProblemResponseWriter(new ObjectMapper()));
        request = new MockHttpServletRequest("GET", "/api/v1/practices/p/records/r");
        response = new MockHttpServletResponse();
    }

    @Test
    @DisplayName("A PostgreSQL shutdown signal (57P01) escaping the chain answers 503 problem+json")
    void shutdownSignalAnswers503() throws Exception {
        FilterChain chain = (req, res) -> {
            throw new DataAccessResourceFailureException("PreparedStatementCallback",
                    new SQLException("terminating connection due to administrator command", "57P01"));
        };

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getHeader("Content-Type")).startsWith("application/problem+json");
        assertThat(response.getContentAsString()).contains("\"code\":\"SERVICE_UNAVAILABLE\"");
    }

    @Test
    @DisplayName("Any other exception escaping the chain answers 500 problem+json, never a 401, and leaks nothing")
    void unexpectedExceptionAnswers500WithoutLeaking() throws Exception {
        FilterChain chain = (req, res) -> {
            throw new IllegalStateException("SECRET-DETAIL insert into patient");
        };

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getHeader("Content-Type")).startsWith("application/problem+json");
        assertThat(response.getContentAsString()).contains("\"code\":\"INTERNAL_ERROR\"");
        assertThat(response.getContentAsString()).doesNotContain("SECRET-DETAIL").doesNotContain("insert into");
    }

    @Test
    @DisplayName("An IOException (client gone) is rethrown untouched")
    void ioExceptionIsRethrown() throws Exception {
        FilterChain chain = (req, res) -> {
            throw new IOException("Broken pipe");
        };

        assertThatThrownBy(() -> filter.doFilter(request, response, chain))
                .isInstanceOf(IOException.class)
                .hasMessage("Broken pipe");
        assertThat(response.getContentAsString()).isEmpty();
    }

    @Test
    @DisplayName("A committed response is never rewritten: the original exception is rethrown")
    void committedResponseRethrowsTheOriginalException() throws Exception {
        response.getWriter().write("partial");
        response.setCommitted(true);
        FilterChain chain = (req, res) -> {
            throw new IllegalStateException("late failure");
        };

        assertThatThrownBy(() -> filter.doFilter(request, response, chain))
                .isInstanceOf(ServletException.class)
                .hasRootCauseInstanceOf(IllegalStateException.class);
        assertThat(response.getContentAsString()).isEqualTo("partial");
        assertThat(response.getStatus()).isEqualTo(200);
    }
}
