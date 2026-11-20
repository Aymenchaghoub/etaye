package dev.aymen.etaye.infrastructure.error;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class ProblemResponseWriterTest {

    private ProblemResponseWriter writer;

    @BeforeEach
    void setUp() {
        writer = new ProblemResponseWriter(new ObjectMapper());
    }

    @Test
    @DisplayName("C1: Committed response is left untouched and writer reports it")
    void committedResponse_isLeftUntouchedAndWriterReportsFalse() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);
        response.getWriter().write("initial response body");
        response.setCommitted(true);

        ApiProblem problem = ApiProblem.of(503, "Service Unavailable", "SERVICE_UNAVAILABLE",
                "Service temporarily unavailable", "corr-committed");

        boolean written = writer.writeProblem(response, problem);

        assertThat(written).isFalse();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).isEqualTo("initial response body");
    }

    @Test
    @DisplayName("Stale headers of a half-built success are dropped and the problem is written after getOutputStream() was used")
    void staleHeadersAreDroppedAndOutputStreamWasAlreadyChosen() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);
        response.setHeader("Content-Length", "15");
        response.setHeader("ETag", "\"0\"");
        response.getOutputStream().write("{\"status\":\"ok\"}".getBytes());

        ApiProblem problem = ApiProblem.of(503, "Service Unavailable", "SERVICE_UNAVAILABLE",
                "Service temporarily unavailable", "corr-stale");

        boolean written = writer.writeProblem(response, problem);

        assertThat(written).isTrue();
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getHeader("ETag")).isNull();
        assertThat(response.getHeader("Content-Length")).isNull();
        assertThat(response.getContentAsString()).doesNotContain("\"status\":\"ok\"").contains("\"code\":\"SERVICE_UNAVAILABLE\"");
    }

    @Test
    @DisplayName("Security headers already set by the chain survive the reset, stale success headers do not")
    void securityHeadersSurviveTheReset() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setHeader("Strict-Transport-Security", "max-age=31536000");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("ETag", "\"0\"");
        response.setHeader("Set-Cookie", "__Host-etaye-session=stale");

        boolean written = writer.writeProblem(response, ApiProblem.of(503, "Service Unavailable",
                "SERVICE_UNAVAILABLE", "Service temporarily unavailable", "corr-headers"));

        assertThat(written).isTrue();
        assertThat(response.getHeader("Strict-Transport-Security")).isEqualTo("max-age=31536000");
        assertThat(response.getHeader("X-Frame-Options")).isEqualTo("DENY");
        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(response.getHeader("ETag")).isNull();
        assertThat(response.getHeader("Set-Cookie")).isNull();
    }

    @Test
    @DisplayName("C1: Not-yet-committed response with partial body bytes ends with ONLY problem JSON")
    void notCommittedResponseWithPartialBytes_endsOnlyWithProblemJson() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);
        response.getWriter().write("{\"status\":\"ok\"}");

        ApiProblem problem = ApiProblem.of(503, "Service Unavailable", "SERVICE_UNAVAILABLE",
                "Service temporarily unavailable", "corr-uncommitted");

        boolean written = writer.writeProblem(response, problem);

        assertThat(written).isTrue();
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getHeader("Content-Type")).startsWith("application/problem+json");
        assertThat(response.getHeader("X-Correlation-Id")).isEqualTo("corr-uncommitted");
        assertThat(response.getContentAsString()).doesNotContain("{\"status\":\"ok\"}");
        assertThat(response.getContentAsString()).contains("\"code\":\"SERVICE_UNAVAILABLE\"");
        assertThat(response.getContentAsString()).contains("\"status\":503");
    }
}
