package dev.aymen.etaye.records;

import dev.aymen.etaye.AbstractPostgresTest;
import dev.aymen.etaye.TestSessionHelper;
import dev.aymen.etaye.records.domain.PatientDirectoryRepository;
import dev.aymen.etaye.records.domain.PatientView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two registrations of the same person under different idempotency keys must not both pass the duplicate
 * check. The duplicate lookup is slowed so that, without the identity locks, both requests would read an
 * empty candidate set before either inserts; with the locks the second waits for the first and is warned.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PatientRegistrationRaceHttpTest.SlowLookupConfig.class)
public class PatientRegistrationRaceHttpTest extends AbstractPostgresTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class SlowLookupConfig {
        @Bean
        @Primary
        PatientDirectoryRepository slowDirectory(JdbcTemplate jdbcTemplate) {
            return new PatientDirectoryRepository(jdbcTemplate) {
                @Override
                public List<PatientView> findDuplicateCandidates(UUID practiceId, String firstKey, String lastKey,
                                                                 LocalDate birthDate, int limit) {
                    List<PatientView> found = super.findDuplicateCandidates(practiceId, firstKey, lastKey, birthDate, limit);
                    try {
                        Thread.sleep(600);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return found;
                }
            };
        }
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestSessionHelper sessions;

    private String session(UUID practice) {
        UUID account = createAccountAsOwner("race." + UUID.randomUUID() + "@example.test", "Dr Course");
        createMembershipAsOwner(account, practice, "ACTIVE", new String[]{"CLINICAL_READ", "CLINICAL_WRITE"});
        return sessions.createSessionCookie(account);
    }

    private int register(String cookie, UUID practice, String first, String last) {
        RestClient client = RestClient.builder().baseUrl("http://localhost:" + port).build();
        String token = (String) client.get().uri("/api/v1/csrf").header(HttpHeaders.COOKIE, cookie).retrieve().toEntity(Map.class).getBody().get("token");
        return client.post().uri("/api/v1/practices/" + practice + "/patients").header(HttpHeaders.COOKIE, cookie)
                .header("X-CSRF-TOKEN", token).header("Idempotency-Key", "key-" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("firstName", first, "lastName", last, "dateOfBirth", "1985-06-02"))
                .retrieve().onStatus(s -> true, (rq, rs) -> {}).toEntity(Map.class).getStatusCode().value();
    }

    private long count(UUID practice) {
        try (Connection c = DriverManager.getConnection(POSTGRES_CONTAINER.getJdbcUrl(), POSTGRES_CONTAINER.getUsername(), POSTGRES_CONTAINER.getPassword());
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT count(*) FROM patient WHERE practice_id = '" + practice + "'")) {
            rs.next();
            return rs.getLong(1);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void sameIdentityUnderTwoKeysIsCreatedOnceAndWarnedOnce() throws Exception {
        UUID practice = createPracticeAsOwner("Cabinet course " + UUID.randomUUID());
        String first = session(practice);
        String second = session(practice);
        var pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch go = new CountDownLatch(1);
            List<Future<Integer>> results = new ArrayList<>();
            // The same person, once in each name order: both orders must contend on the same locks.
            results.add(pool.submit(() -> { go.await(); return register(first, practice, "Jeanne", "Dupont"); }));
            results.add(pool.submit(() -> { go.await(); return register(second, practice, "Dupont", "Jeanne"); }));
            go.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get());
            }
            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
            assertThat(count(practice)).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }
}
