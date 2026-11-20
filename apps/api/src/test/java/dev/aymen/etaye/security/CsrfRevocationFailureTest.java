package dev.aymen.etaye.security;

import dev.aymen.etaye.AbstractPostgresTest;
import dev.aymen.etaye.TestSessionHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(CsrfRevocationFailureTest.FailingSessionConfig.class)
public class CsrfRevocationFailureTest extends AbstractPostgresTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestSessionHelper testSessionHelper;

    @Autowired
    private FindByIndexNameSessionRepository<?> sessionRepository;

    private FailingSessionRepositoryWrapper<?> failingWrapper() {
        return (FailingSessionRepositoryWrapper<?>) sessionRepository;
    }

    private RestClient client() {
        return RestClient.builder().baseUrl("http://localhost:" + port).build();
    }

    public static class FailingSessionRepositoryWrapper<S extends Session> implements FindByIndexNameSessionRepository<S> {
        private final FindByIndexNameSessionRepository<S> delegate;
        private final AtomicBoolean throwOnDelete = new AtomicBoolean(false);

        public FailingSessionRepositoryWrapper(FindByIndexNameSessionRepository<S> delegate) {
            this.delegate = delegate;
        }

        public void setThrowOnDelete(boolean shouldThrow) {
            this.throwOnDelete.set(shouldThrow);
        }

        @Override
        public S createSession() {
            return delegate.createSession();
        }

        @Override
        public void save(S session) {
            delegate.save(session);
        }

        @Override
        public S findById(String id) {
            return delegate.findById(id);
        }

        @Override
        public void deleteById(String id) {
            if (throwOnDelete.get()) {
                throw new RuntimeException("Simulated database failure during session deletion");
            }
            delegate.deleteById(id);
        }

        @Override
        public Map<String, S> findByIndexNameAndIndexValue(String indexName, String indexValue) {
            return delegate.findByIndexNameAndIndexValue(indexName, indexValue);
        }
    }

    @TestConfiguration
    static class FailingSessionConfig {
        @Bean
        static BeanPostProcessor sessionRepositoryPostProcessor() {
            return new BeanPostProcessor() {
                @Override
                @SuppressWarnings({"unchecked", "rawtypes"})
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                    if (bean instanceof FindByIndexNameSessionRepository sr && !(bean instanceof FailingSessionRepositoryWrapper)) {
                        return new FailingSessionRepositoryWrapper(sr);
                    }
                    return bean;
                }
            };
        }
    }

    @Test
    @DisplayName("C3: /api/v1/csrf must return 503 without token on failed session deletion, and recover when working")
    void csrfFailsWith503OnFailedRevocationAndRecovers() {
        // 1. Create a suspended account with an active session
        String email = "csrf.fail." + UUID.randomUUID() + "@example.com";
        UUID accountId = createAccountAsOwner(email, "CSRF Failing User");
        UUID practiceId = createPracticeAsOwner("Cabinet CSRF Failing");
        createMembershipAsOwner(accountId, practiceId, "ACTIVE", new String[]{"CLINICAL_READ"});

        String oldSessionCookie = testSessionHelper.createSessionCookie(accountId);

        // Suspend the account
        updateAccountStateAsOwner(accountId, "SUSPENDED");

        // 2. Enable failure on deleteById
        failingWrapper().setThrowOnDelete(true);

        try {
            // 3. Request /api/v1/csrf using the suspended account's cookie
            ResponseEntity<Map> errorResponse = client().get()
                    .uri("/api/v1/csrf")
                    .header(HttpHeaders.COOKIE, oldSessionCookie)
                    .retrieve()
                    .onStatus(status -> status.is5xxServerError() || status.is4xxClientError(), (req, res) -> {})
                    .toEntity(Map.class);

            // Must answer 503 Service Unavailable through the shared problem writer
            assertThat(errorResponse.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(errorResponse.getHeaders().getContentType().toString()).contains("application/problem+json");

            Map<?, ?> body = errorResponse.getBody();
            assertThat(body).isNotNull();
            assertThat(body.get("code")).isEqualTo("SERVICE_UNAVAILABLE");
            assertThat(body.get("status")).isEqualTo(503);
            assertThat(body.get("token")).isNull();
        } finally {
            // 4. Disable failure
            failingWrapper().setThrowOnDelete(false);
        }

        // 5. The next request with a working repository gets the normal behavior (200 OK with token)
        ResponseEntity<Map> recoveredResponse = client().get()
                .uri("/api/v1/csrf")
                .header(HttpHeaders.COOKIE, oldSessionCookie)
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), (req, res) -> {})
                .toEntity(Map.class);

        assertThat(recoveredResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(recoveredResponse.getBody()).isNotNull();
        assertThat(recoveredResponse.getBody().get("token")).isNotNull();
        assertThat(recoveredResponse.getBody().get("headerName")).isEqualTo("X-CSRF-TOKEN");
    }
}
