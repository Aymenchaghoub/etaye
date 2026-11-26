package dev.aymen.etaye.infrastructure.security;

import dev.aymen.etaye.identity.dto.AccountProfile;
import dev.aymen.etaye.identity.dto.AuthenticatedAccount;
import dev.aymen.etaye.identity.service.IdentityLookupService;
import dev.aymen.etaye.infrastructure.error.ProblemResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.session.MapSession;
import org.springframework.session.SessionRepository;
import tools.jackson.databind.ObjectMapper;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdentityResolutionFilterTest {

    private static final UUID ACCOUNT_ID = UUID.fromString("00000000-0000-4000-8000-000000000011");
    private BrowserSessionLifetime lifetime;
    private ProblemResponseWriter problemWriter;

    @BeforeEach
    void setUp() {
        var beans = new StaticListableBeanFactory();
        beans.addBean("clock", Clock.fixed(Instant.parse("2026-11-26T12:00:00Z"), java.time.ZoneOffset.UTC));
        lifetime = new BrowserSessionLifetime(Duration.ofMinutes(30), Duration.ofHours(8),
                beans.getBeanProvider(Clock.class), new HttpSessionCsrfTokenRepository());
        problemWriter = new ProblemResponseWriter(new ObjectMapper());
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private IdentityResolutionFilter createFilter(
            Function<UUID, Optional<AuthenticatedAccount>> resolver,
            SessionRepository<?> sessionRepository) {
        IdentityLookupService lookup = new IdentityLookupService(null) {
            @Override
            public Optional<AuthenticatedAccount> resolveAccountAndMemberships(UUID accountId) {
                return resolver.apply(accountId);
            }
        };
        return new IdentityResolutionFilter(
                lookup,
                new ProblemAuthenticationEntryPoint(problemWriter),
                problemWriter,
                sessionRepository,
                lifetime);
    }

    private static void authenticateAs(Object principal) {
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
        SecurityContextHolder.setContext(context);
    }

    private static AccountProfile suspendedAccount() {
        Instant now = Instant.parse("2026-11-26T10:00:00Z");
        return new AccountProfile(ACCOUNT_ID, "a@example.test", "A", "SUSPENDED", "fr-FR", 0L, now, now, 1L);
    }

    private static AuthenticatedAccount authenticated(AccountProfile profile) {
        return new AuthenticatedAccount(profile, List.of(), List.of(), List.of(), List.of());
    }

    @Test
    void skipsProbePathsAndPassesNonUuidPrincipals() throws Exception {
        IdentityResolutionFilter filter = createFilter(_ -> Optional.empty(), null);
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/api/v1/probes/readiness"))).isTrue();
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/api/v1/session"))).isFalse();

        authenticateAs("not-a-uuid");
        AtomicBoolean called = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> called.set(true);
        filter.doFilter(new MockHttpServletRequest("GET", "/api/v1/session"), new MockHttpServletResponse(), chain);
        assertThat(called).isTrue();
    }

    @Test
    void handlesSessionInvalidationFailuresOnCsrfEndpointDistinguishingOutageFromBug() throws Exception {
        authenticateAs(ACCOUNT_ID.toString());
        IdentityResolutionFilter filter = createFilter(_ -> Optional.of(authenticated(suspendedAccount())), null);

        MockHttpServletRequest outageReq = new MockHttpServletRequest("GET", "/api/v1/csrf");
        outageReq.setSession(new MockHttpSession() {
            @Override
            public void invalidate() {
                throw new DataAccessResourceFailureException("db gone", new SQLException("shutdown", "57P01"));
            }
        });
        MockHttpServletResponse outageRes = new MockHttpServletResponse();
        filter.doFilter(outageReq, outageRes, (req, res) -> {});
        assertThat(outageRes.getStatus()).isEqualTo(503);
        assertThat(outageRes.getContentAsString()).contains("\"code\":\"SERVICE_UNAVAILABLE\"");

        authenticateAs(ACCOUNT_ID.toString());
        MockHttpServletRequest bugReq = new MockHttpServletRequest("GET", "/api/v1/csrf");
        bugReq.setSession(new MockHttpSession() {
            @Override
            public void invalidate() {
                throw new IllegalStateException("unexpected");
            }
        });
        assertThatThrownBy(() -> filter.doFilter(bugReq, new MockHttpServletResponse(), (req, res) -> {}))
                .isInstanceOf(ServletException.class);
    }

    @Test
    void toleratesSessionCleanupExceptionsOnProtectedRoutesAndAnswers401() throws Exception {
        authenticateAs(ACCOUNT_ID.toString());
        SessionRepository<MapSession> failingRepo = new SessionRepository<>() {
            @Override public MapSession createSession() { return new MapSession(); }
            @Override public void save(MapSession session) {}
            @Override public MapSession findById(String id) { return null; }
            @Override public void deleteById(String id) { throw new IllegalStateException("cleanup failed"); }
        };
        IdentityResolutionFilter filter = createFilter(_ -> Optional.empty(), failingRepo);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/session");
        req.setSession(new MockHttpSession() {
            @Override
            public void invalidate() {
                throw new IllegalStateException("already gone");
            }
        });
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, (r, s) -> {});
        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(res.getContentAsString()).contains("\"code\":\"UNAUTHORIZED\"");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void answers503WhenIdentityLookupHitsDatabaseConnectionFailure() throws Exception {
        authenticateAs(ACCOUNT_ID.toString());
        IdentityResolutionFilter filter = createFilter(_ -> {
            throw new DataAccessResourceFailureException("db down", new SQLException("down", "08006"));
        }, null);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/session");
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, (r, s) -> {});
        assertThat(res.getStatus()).isEqualTo(503);
        assertThat(res.getContentAsString()).contains("\"code\":\"SERVICE_UNAVAILABLE\"");

        authenticateAs(ACCOUNT_ID.toString());
        IdentityResolutionFilter unexpectedFilter = createFilter(_ -> {
            throw new IllegalArgumentException("bad state");
        }, null);
        assertThatThrownBy(() -> unexpectedFilter.doFilter(new MockHttpServletRequest("GET", "/api/v1/session"),
                new MockHttpServletResponse(), (r, s) -> {})).isInstanceOf(ServletException.class);
    }
}
