package dev.aymen.etaye.infrastructure.error;

import dev.aymen.etaye.infrastructure.security.AccessDenialAudit;
import dev.aymen.etaye.infrastructure.security.AuthorizationDenial;
import dev.aymen.etaye.infrastructure.security.PracticeSecurityService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.csrf.InvalidCsrfTokenException;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        var beans = new StaticListableBeanFactory();
        AccessDenialAudit failingAudit = new AccessDenialAudit(null, null, new ObjectMapper(), beans.getBeanProvider(java.time.Clock.class)) {
            @Override
            public void record(AuthorizationDenial denial, HttpServletRequest req) {
                throw new IllegalStateException("audit failure should not mask problem response");
            }
        };
        handler = new GlobalExceptionHandler(failingAudit);
        request = new MockHttpServletRequest("GET", "/api/v1/meta");
    }

    @Test
    void convertsStandardClientAndServerErrorsToApiProblems() throws Exception {
        var inputMsg = new HttpInputMessage() {
            @Override public InputStream getBody() { return new ByteArrayInputStream(new byte[0]); }
            @Override public HttpHeaders getHeaders() { return new HttpHeaders(); }
        };
        assertThat(handler.handleMessageNotReadable(new HttpMessageNotReadableException("bad", inputMsg), request).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(handler.handleTypeMismatch(new MethodArgumentTypeMismatchException("x", UUID.class, "id", null, new IllegalArgumentException()), request).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(handler.handleMissingHeader(new MissingRequestHeaderException("If-Match", null), request).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(handler.handleInvalidIdempotencyKey(new InvalidIdempotencyKeyException("bad"), request).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(handler.handleIdempotencyConflict(new IdempotencyConflictException("dup"), request).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(handler.handleInvalidParameter(new InvalidParameterException("bad"), request).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(handler.handlePreconditionRequired(new PreconditionRequiredException("missing"), request).getStatusCode())
                .isEqualTo(HttpStatus.PRECONDITION_REQUIRED);
        assertThat(handler.handleStaleVersion(new StaleVersionException("stale"), request).getStatusCode())
                .isEqualTo(HttpStatus.PRECONDITION_FAILED);
        assertThat(handler.handleMediaTypeNotSupported(new HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN, List.of(MediaType.APPLICATION_JSON)), request).getStatusCode())
                .isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(handler.handleMediaTypeNotAcceptable(new HttpMediaTypeNotAcceptableException("nope"), request).getStatusCode())
                .isEqualTo(HttpStatus.NOT_ACCEPTABLE);
        assertThat(handler.handleNoResourceFound(new NoResourceFoundException(HttpMethod.GET, "/missing", "/missing"), request).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(handler.handleMethodNotSupported(new HttpRequestMethodNotSupportedException("DELETE"), request).getStatusCode())
                .isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        var withAllowed = handler.handleMethodNotSupported(new HttpRequestMethodNotSupportedException("POST", List.of("GET")), request);
        assertThat(withAllowed.getHeaders().getAllow()).isEqualTo(Set.of(HttpMethod.GET));
        assertThat(handler.handleGenericException(new DataAccessResourceFailureException("db", new SQLException("down", "57P01")), request).getStatusCode())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(handler.handleGenericException(new RuntimeException("boom"), request).getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void masksDenialAuditFailuresWhilePreservingForbiddenAndNotFoundProblems() {
        var csrfRes = handler.handleAccessDenied(new InvalidCsrfTokenException(new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "a"), "b"), request);
        assertThat(csrfRes.getBody().code()).isEqualTo("CSRF_REJECTED");

        var deniedRes = handler.handleAccessDenied(new AccessDeniedException("denied"), request);
        assertThat(deniedRes.getBody().code()).isEqualTo("FORBIDDEN");

        var service = new PracticeSecurityService();
        var now = java.time.Instant.parse("2026-11-26T10:00:00Z");
        var acct = new dev.aymen.etaye.identity.dto.AuthenticatedAccount(
                new dev.aymen.etaye.identity.dto.AccountProfile(UUID.randomUUID(), "u@example.test", "U", "ACTIVE", "fr-FR", 0L, now, now, 0L),
                List.of(), List.of(), List.of(), List.of());
        request.setAttribute("authenticatedAccount", acct);
        PracticeSecurityService.PracticeNotVisibleException notVisible = null;
        try {
            service.requireCapability(request, UUID.randomUUID(), "CLINICAL_READ");
        } catch (PracticeSecurityService.PracticeNotVisibleException ex) {
            notVisible = ex;
        }
        assertThat(handler.handleNotFound(notVisible, request).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
