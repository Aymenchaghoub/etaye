package dev.aymen.etaye.records;

import dev.aymen.etaye.AbstractPostgresTest;
import dev.aymen.etaye.TestSessionHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Story 3.1 on the real HTTP stack and PostgreSQL: practice-local search, the likely-duplicate rule
 * with its named confirmation, identity correction under If-Match, and the scheduling registration.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(OutputCaptureExtension.class)
public class PatientSearchAndRegistrationHttpTest extends AbstractPostgresTest {

    private static final String PATIENTS = "/api/v1/practices/%s/patients";
    private static final String SCHEDULING = "/api/v1/practices/%s/scheduling/patients";

    @LocalServerPort
    private int port;

    @Autowired
    private TestSessionHelper sessions;

    @Autowired
    private JdbcTemplate ownerless;

    private UUID practiceA;
    private UUID practiceB;
    private UUID practiceEmpty;
    private String clinicianA;
    private String clinicianA2;
    private String clinicianB;
    private String clinicianEmpty;
    private String readOnlyA;
    private String adminOnlyA;
    private String schedulerA;
    private String schedulerOnlyA;
    private UUID claireRecord;
    private UUID marieRecord;
    private UUID hugoRecord;

    private RestClient client() {
        return RestClient.builder().baseUrl("http://localhost:" + port).build();
    }

    private UUID lastAccount;
    private UUID clinicianAAccount;

    private String session(UUID practice, String... capabilities) {
        UUID account = createAccountAsOwner("p31." + UUID.randomUUID() + "@example.test", "Synthetic " + UUID.randomUUID());
        lastAccount = account;
        createMembershipAsOwner(account, practice, "ACTIVE", capabilities);
        return sessions.createSessionCookie(account);
    }

    private UUID seed(UUID practice, String first, String last, String birth) {
        UUID patient = UUID.randomUUID();
        UUID record = UUID.randomUUID();
        executeAsOwner("""
                BEGIN;
                SELECT set_config('etaye.practice_id', '%s', true);
                INSERT INTO patient (id, practice_id, first_name, last_name, date_of_birth) VALUES ('%s', '%s', '%s', '%s', '%s');
                INSERT INTO patient_record (id, practice_id, patient_id) VALUES ('%s', '%s', '%s');
                COMMIT;
                """.formatted(practice, patient, practice, first.replace("'", "''"), last.replace("'", "''"), birth, record, practice, patient));
        return record;
    }

    @BeforeEach
    void fixtures() {
        practiceA = createPracticeAsOwner("Cabinet A " + UUID.randomUUID());
        practiceB = createPracticeAsOwner("Cabinet B " + UUID.randomUUID());
        practiceEmpty = createPracticeAsOwner("Cabinet vide " + UUID.randomUUID());
        clinicianA = session(practiceA, "CLINICAL_READ", "CLINICAL_WRITE");
        clinicianAAccount = lastAccount;
        clinicianA2 = session(practiceA, "CLINICAL_READ", "CLINICAL_WRITE");
        clinicianB = session(practiceB, "CLINICAL_READ", "CLINICAL_WRITE");
        clinicianEmpty = session(practiceEmpty, "CLINICAL_READ", "CLINICAL_WRITE");
        readOnlyA = session(practiceA, "CLINICAL_READ");
        adminOnlyA = session(practiceA, "TEAM_MANAGE", "PRACTICE_SETTINGS", "ADMIN_AUDIT");
        schedulerA = session(practiceA, "SCHEDULE_MANAGE", "PATIENT_REGISTER");
        schedulerOnlyA = session(practiceA, "SCHEDULE_MANAGE");
        claireRecord = seed(practiceA, "Claire", "Martin", "1978-04-18");
        seed(practiceA, "Louis", "Bernard", "1991-07-09");
        marieRecord = seed(practiceA, "Marie", "Collet", "1990-03-12");
        seed(practiceA, "Éloïse", "Léger-Müller", "1985-11-02");
        hugoRecord = seed(practiceB, "Hugo", "Petit", "1986-02-25");
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<Map<String, Object>> get(String cookie, String uri) {
        // An absolute URI keeps a pre-encoded query such as %20 from being encoded a second time.
        return (ResponseEntity<Map<String, Object>>) (ResponseEntity<?>) client().get().uri(java.net.URI.create("http://localhost:" + port + uri))
                .header(HttpHeaders.COOKIE, cookie).retrieve().onStatus(s -> true, (rq, rs) -> {}).toEntity(Map.class);
    }

    private String csrf(String cookie) {
        return (String) client().get().uri("/api/v1/csrf").header(HttpHeaders.COOKIE, cookie).retrieve().toEntity(Map.class).getBody().get("token");
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<Map<String, Object>> send(HttpMethod method, String cookie, String uri, Map<String, ?> body, Map<String, String> headers) {
        RestClient.RequestBodySpec spec = client().method(method).uri(uri)
                .header(HttpHeaders.COOKIE, cookie).header("X-CSRF-TOKEN", csrf(cookie)).contentType(MediaType.APPLICATION_JSON);
        headers.forEach(spec::header);
        return (ResponseEntity<Map<String, Object>>) (ResponseEntity<?>) spec.body(body).retrieve().onStatus(s -> true, (rq, rs) -> {}).toEntity(Map.class);
    }

    private ResponseEntity<Map<String, Object>> create(String cookie, UUID practice, Map<String, ?> body) {
        return send(HttpMethod.POST, cookie, PATIENTS.formatted(practice), body, Map.of("Idempotency-Key", "key-" + UUID.randomUUID()));
    }

    private static Map<String, Object> identity(String first, String last, String birth) {
        Map<String, Object> body = new HashMap<>();
        body.put("firstName", first);
        body.put("lastName", last);
        body.put("dateOfBirth", birth);
        return body;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> items(ResponseEntity<Map<String, Object>> response) {
        return (List<Map<String, Object>>) response.getBody().get("items");
    }

    private static List<String> lastNames(ResponseEntity<Map<String, Object>> response) {
        return items(response).stream().map(i -> (String) i.get("lastName")).toList();
    }

    /** Counts as the database superuser: forced row-level security would hide every row from the owner. */
    private long count(String sql) {
        try (Connection c = DriverManager.getConnection(POSTGRES_CONTAINER.getJdbcUrl(), POSTGRES_CONTAINER.getUsername(), POSTGRES_CONTAINER.getPassword());
             Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private long patientCount(UUID practice) {
        return count("SELECT count(*) FROM patient WHERE practice_id = '" + practice + "'");
    }

    @Test
    @DisplayName("C1 search stays inside the practice and orders by normalized name")
    void searchIsPracticeLocalAndOrdered() {
        assertThat(lastNames(get(clinicianA, PATIENTS.formatted(practiceA) + "?q=Petit"))).isEmpty();
        assertThat(lastNames(get(clinicianB, PATIENTS.formatted(practiceB) + "?q=Petit"))).containsExactly("Petit");
        assertThat(lastNames(get(clinicianA, PATIENTS.formatted(practiceA)))).containsExactly("Bernard", "Collet", "Léger-Müller", "Martin");
        // Another practice is never a result and never discloses itself: 404, not an empty page.
        ResponseEntity<Map<String, Object>> foreign = get(clinicianA, PATIENTS.formatted(practiceB));
        assertThat(foreign.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(foreign.getBody().toString()).doesNotContain("Petit");
    }

    @Test
    @DisplayName("search matches word starts, ignores case, accents and punctuation, and reads birth dates")
    void searchMatchingRules() {
        assertThat(lastNames(get(clinicianA, PATIENTS.formatted(practiceA) + "?q=mar"))).containsExactly("Collet", "Martin");
        assertThat(lastNames(get(clinicianA, PATIENTS.formatted(practiceA) + "?q=ELOISE"))).containsExactly("Léger-Müller");
        assertThat(lastNames(get(clinicianA, PATIENTS.formatted(practiceA) + "?q=muller%20leger"))).containsExactly("Léger-Müller");
        assertThat(lastNames(get(clinicianA, PATIENTS.formatted(practiceA) + "?q=18/04/1978"))).containsExactly("Martin");
        assertThat(lastNames(get(clinicianA, PATIENTS.formatted(practiceA) + "?q=martin%201978-04-18"))).containsExactly("Martin");
        assertThat(lastNames(get(clinicianA, PATIENTS.formatted(practiceA) + "?q=martin%2009/07/1991"))).isEmpty();
        assertThat(lastNames(get(clinicianA, PATIENTS.formatted(practiceA) + "?q=18/04/1978%2009/07/1991"))).isEmpty();
        assertThat(lastNames(get(clinicianA, PATIENTS.formatted(practiceA) + "?q=tin"))).as("not a word start").isEmpty();
        assertThat(lastNames(get(clinicianA, PATIENTS.formatted(practiceA) + "?q=!!!"))).as("no searchable content").isEmpty();
    }

    @Test
    @DisplayName("C2 an empty practice and a query without match return the same empty page")
    void emptyListAndNoResultAreTheSamePage() {
        ResponseEntity<Map<String, Object>> empty = get(clinicianEmpty, PATIENTS.formatted(practiceEmpty));
        ResponseEntity<Map<String, Object>> none = get(clinicianEmpty, PATIENTS.formatted(practiceEmpty) + "?q=Nobody");
        assertThat(empty.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(empty.getBody()).isEqualTo(none.getBody()).containsEntry("nextCursor", null);
        assertThat(items(empty)).isEmpty();
    }

    @Test
    @DisplayName("C3 the cursor is opaque, ordered and bound to practice, actor and query")
    void paginationCursor() {
        String uri = PATIENTS.formatted(practiceA) + "?limit=3";
        ResponseEntity<Map<String, Object>> first = get(clinicianA, uri);
        assertThat(lastNames(first)).containsExactly("Bernard", "Collet", "Léger-Müller");
        String cursor = (String) first.getBody().get("nextCursor");
        assertThat(cursor).isNotBlank().doesNotContain("Bernard");
        ResponseEntity<Map<String, Object>> second = get(clinicianA, uri + "&cursor=" + cursor);
        assertThat(lastNames(second)).containsExactly("Martin");
        assertThat(second.getBody().get("nextCursor")).isNull();

        assertThat(get(clinicianA, uri + "&q=mar&cursor=" + cursor).getStatusCode()).as("other query").isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(get(clinicianA2, uri + "&cursor=" + cursor).getStatusCode()).as("other actor").isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(get(clinicianA, uri + "&cursor=not-a-cursor").getBody()).containsEntry("code", "INVALID_PARAMETER");
        assertThat(get(clinicianA, PATIENTS.formatted(practiceA) + "?limit=0").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(get(clinicianA, PATIENTS.formatted(practiceA) + "?limit=101").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(get(clinicianA, PATIENTS.formatted(practiceA) + "?q=" + "a".repeat(101)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("C4 the query never reaches logs, audit or outbox")
    void queryTextIsNeverRecorded(CapturedOutput output) {
        long auditBefore = count("SELECT count(*) FROM audit_event");
        get(clinicianA, PATIENTS.formatted(practiceA) + "?q=zzcanaryzz");
        get(clinicianA, PATIENTS.formatted(practiceA) + "?q=zzcanaryzz&cursor=broken");
        assertThat(output.getAll()).doesNotContain("zzcanaryzz");
        assertThat(count("SELECT count(*) FROM audit_event")).as("reads are not audited").isEqualTo(auditBefore);
        assertThat(count("SELECT count(*) FROM audit_event WHERE safe_metadata::text LIKE '%zzcanaryzz%'")).isZero();
    }

    @Test
    @DisplayName("C7 search authorization is current and server-side")
    void searchAuthorization() {
        assertThat(get(adminOnlyA, PATIENTS.formatted(practiceA)).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get(schedulerA, PATIENTS.formatted(practiceA)).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get(clinicianA, SCHEDULING.formatted(practiceA) + "?q=mar").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get(adminOnlyA, SCHEDULING.formatted(practiceA) + "?q=mar").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get(clinicianB, PATIENTS.formatted(practiceA)).getStatusCode()).as("foreign practice is hidden").isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("C12 the scheduling lookup returns only the minimal projection and needs two characters")
    void schedulingLookup() {
        ResponseEntity<Map<String, Object>> page = get(schedulerOnlyA, SCHEDULING.formatted(practiceA) + "?q=mar");
        assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(lastNames(page)).containsExactly("Collet", "Martin");
        assertThat(items(page).get(0).keySet()).containsExactlyInAnyOrder(
                "id", "practiceId", "recordId", "firstName", "lastName", "dateOfBirth", "contactEmail", "phone");
        assertThat(get(schedulerOnlyA, SCHEDULING.formatted(practiceA)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(get(schedulerOnlyA, SCHEDULING.formatted(practiceA) + "?q=m").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(lastNames(get(schedulerOnlyA, SCHEDULING.formatted(practiceA) + "?q=Petit"))).isEmpty();
    }

    @Test
    @DisplayName("C5 a valid identity creates a patient and an empty record atomically")
    void createValidIdentity() {
        long auditBefore = count("SELECT count(*) FROM audit_event WHERE event_type = 'PATIENT_CREATED' AND practice_id = '" + practiceA + "'");
        ResponseEntity<Map<String, Object>> created = create(clinicianA, practiceA, identity("Jeanne", "Dupont", "1985-06-02"));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getHeaders().getETag()).isEqualTo("\"0\"");
        assertThat(created.getBody()).containsKeys("id", "recordId").containsEntry("version", 0);
        assertThat(count("SELECT count(*) FROM patient_record WHERE practice_id = '" + practiceA + "' AND patient_id = '" + created.getBody().get("id") + "'")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM audit_event WHERE event_type = 'PATIENT_CREATED' AND practice_id = '" + practiceA + "'")).isEqualTo(auditBefore + 1);
        assertThat(lastNames(get(clinicianA, PATIENTS.formatted(practiceA) + "?q=dupont"))).containsExactly("Dupont");
    }

    @Test
    @DisplayName("C6 likely duplicates are refused with typed same-practice candidates and create nothing")
    void duplicateRule() {
        long before = patientCount(practiceA);
        long auditBefore = count("SELECT count(*) FROM audit_event WHERE event_type = 'PATIENT_CREATED'");
        ResponseEntity<Map<String, Object>> exact = create(clinicianA, practiceA, identity("Claire", "Martin", "1978-04-18"));
        assertThat(exact.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(exact.getBody()).containsEntry("code", "DUPLICATE_PATIENT");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> candidates = (List<Map<String, Object>>) exact.getBody().get("candidates");
        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0)).containsEntry("recordId", claireRecord.toString()).containsEntry("lastName", "Martin");
        assertThat(candidates.get(0).keySet()).doesNotContain("version", "createdAt");

        assertThat(create(clinicianA, practiceA, identity("Martin", "Claire", "1990-01-01")).getStatusCode()).as("names swapped").isEqualTo(HttpStatus.CONFLICT);
        assertThat(create(clinicianA, practiceA, identity("CLAIRE", "martin", "1960-05-05")).getStatusCode()).as("homonym, other birth date").isEqualTo(HttpStatus.CONFLICT);
        ResponseEntity<Map<String, Object>> sameBirthAndLast = create(clinicianA, practiceA, identity("Louis", "Collet", "1990-03-12"));
        assertThat(sameBirthAndLast.getStatusCode()).as("same birth date and last name").isEqualTo(HttpStatus.CONFLICT);
        assertThat(sameBirthAndLast.getBody().toString()).contains(marieRecord.toString());
        assertThat(patientCount(practiceA)).as("a refusal creates nothing").isEqualTo(before);
        assertThat(count("SELECT count(*) FROM audit_event WHERE event_type = 'PATIENT_CREATED'")).as("and audits no success").isEqualTo(auditBefore);

        assertThat(create(clinicianA, practiceA, identity("Clair", "Martin", "1978-04-19")).getStatusCode()).as("typos are not detected").isEqualTo(HttpStatus.CREATED);
        ResponseEntity<Map<String, Object>> foreignName = create(clinicianA, practiceA, identity("Hugo", "Petit", "1986-02-25"));
        assertThat(foreignName.getStatusCode()).as("another practice is never matched").isEqualTo(HttpStatus.CREATED);
        assertThat(foreignName.getBody().toString()).doesNotContain(hugoRecord.toString());
    }

    @Test
    @DisplayName("C6 a confirmed distinct person is created only when every current candidate is named")
    void namedConfirmation() {
        Map<String, Object> confirmed = identity("Claire", "Martin", "1978-04-18");
        confirmed.put("confirmedDistinctRecordIds", List.of(UUID.randomUUID().toString()));
        assertThat(create(clinicianA, practiceA, confirmed).getStatusCode()).as("wrong record named").isEqualTo(HttpStatus.CONFLICT);

        confirmed.put("confirmedDistinctRecordIds", List.of(claireRecord.toString()));
        ResponseEntity<Map<String, Object>> created = create(clinicianA, practiceA, confirmed);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().get("recordId")).isNotEqualTo(claireRecord.toString());

        // A candidate that appeared since the last check is not covered by the earlier confirmation.
        ResponseEntity<Map<String, Object>> again = create(clinicianA, practiceA, confirmed);
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> candidates = (List<Map<String, Object>>) again.getBody().get("candidates");
        assertThat(candidates).hasSize(2);
    }

    @Test
    @DisplayName("C8 the same key replays one patient and a changed body under the key conflicts")
    void idempotency() {
        String key = "key-" + UUID.randomUUID();
        Map<String, Object> body = identity("Jeanne", "Dupont", "1985-06-02");
        ResponseEntity<Map<String, Object>> first = send(HttpMethod.POST, clinicianA, PATIENTS.formatted(practiceA), body, Map.of("Idempotency-Key", key));
        ResponseEntity<Map<String, Object>> replay = send(HttpMethod.POST, clinicianA, PATIENTS.formatted(practiceA), body, Map.of("Idempotency-Key", key));
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(replay.getBody()).containsEntry("id", first.getBody().get("id"));
        assertThat(count("SELECT count(*) FROM patient WHERE practice_id = '" + practiceA + "' AND last_name = 'Dupont'")).isEqualTo(1);

        Map<String, Object> changed = new HashMap<>(body);
        changed.put("confirmedDistinctRecordIds", List.of(claireRecord.toString()));
        ResponseEntity<Map<String, Object>> conflict = send(HttpMethod.POST, clinicianA, PATIENTS.formatted(practiceA), changed, Map.of("Idempotency-Key", key));
        assertThat(conflict.getBody()).containsEntry("code", "IDEMPOTENCY_CONFLICT");
    }

    @Test
    @DisplayName("input validation rejects names without letters, future birth dates and unknown fields")
    void validation() {
        assertThat(create(clinicianA, practiceA, identity("---", "Dupont", "1985-06-02")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(create(clinicianA, practiceA, identity("Jeanne", "Dupont", "2999-01-01")).getStatusCode()).isEqualTo(HttpStatus.valueOf(422));
        Map<String, Object> unknown = identity("Jeanne", "Dupont", "1985-06-02");
        unknown.put("usageName", "Dupuis");
        assertThat(create(clinicianA, practiceA, unknown).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(patientCount(practiceA)).isEqualTo(4);
    }

    @Test
    @DisplayName("control characters are refused as input instead of failing in the database")
    void controlCharactersAreRefused() {
        assertThat(get(clinicianA, PATIENTS.formatted(practiceA) + "?q=a%00b").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(get(clinicianA, PATIENTS.formatted(practiceA) + "?cursor=" + "a".repeat(1025)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(create(clinicianA, practiceA, identity("Jean\u0000ne", "Dupont", "1985-06-02")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        Map<String, Object> phone = identity("Jeanne", "Dupont", "1985-06-02");
        phone.put("phone", "06\u0007 00");
        assertThat(create(clinicianA, practiceA, phone).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(patientCount(practiceA)).isEqualTo(4);
    }

    @Test
    @DisplayName("C9 two concurrent corrections on one version yield one success and one stale refusal")
    void concurrentCorrections() throws Exception {
        Map<String, Object> created = create(clinicianA, practiceA, identity("Jeanne", "Dupont", "1985-06-02")).getBody();
        String uri = PATIENTS.formatted(practiceA) + "/" + created.get("id");
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.CountDownLatch go = new java.util.concurrent.CountDownLatch(1);
            List<java.util.concurrent.Future<HttpStatus>> results = new ArrayList<>();
            for (String first : List.of("Jeanne-Marie", "Jeanne-Anne")) {
                results.add(pool.submit(() -> {
                    go.await();
                    return HttpStatus.valueOf(send(HttpMethod.PATCH, clinicianA, uri, identity(first, "Dupont", "1985-06-02"),
                            Map.of("If-Match", "\"0\"")).getStatusCode().value());
                }));
            }
            go.countDown();
            List<HttpStatus> statuses = new ArrayList<>();
            for (var result : results) {
                statuses.add(result.get());
            }
            assertThat(statuses).containsExactlyInAnyOrder(HttpStatus.OK, HttpStatus.PRECONDITION_FAILED);
            assertThat(count("SELECT version FROM patient WHERE id = '" + created.get("id") + "'")).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("C7 create authorization")
    void createAuthorization() {
        assertThat(create(readOnlyA, practiceA, identity("Jeanne", "Dupont", "1985-06-02")).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(create(adminOnlyA, practiceA, identity("Jeanne", "Dupont", "1985-06-02")).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(create(clinicianB, practiceA, identity("Jeanne", "Dupont", "1985-06-02")).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(patientCount(practiceA)).isEqualTo(4);
    }

    @Test
    @DisplayName("C9 a correction needs the current version, keeps the identifiers and audits atomically")
    void identityCorrection() {
        Map<String, Object> created = create(clinicianA, practiceA, identity("Jeanne", "Dupont", "1985-06-02")).getBody();
        String uri = PATIENTS.formatted(practiceA) + "/" + created.get("id");
        Map<String, Object> corrected = identity("Jeanne-Marie", "Dupont", "1985-06-02");

        assertThat(send(HttpMethod.PATCH, clinicianA, uri, corrected, Map.of()).getStatusCode()).as("no If-Match").isEqualTo(HttpStatus.PRECONDITION_REQUIRED);
        assertThat(send(HttpMethod.PATCH, clinicianA, uri, corrected, Map.of("If-Match", "\"abc\"")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(send(HttpMethod.PATCH, clinicianA, uri, corrected, Map.of("If-Match", "\"9\"")).getBody()).containsEntry("code", "STALE_VERSION");
        assertThat(count("SELECT count(*) FROM patient WHERE id = '" + created.get("id") + "' AND first_name = 'Jeanne'")).as("a refusal changes nothing").isEqualTo(1);

        ResponseEntity<Map<String, Object>> ok = send(HttpMethod.PATCH, clinicianA, uri, corrected, Map.of("If-Match", "\"0\""));
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ok.getHeaders().getETag()).isEqualTo("\"1\"");
        assertThat(ok.getBody()).containsEntry("id", created.get("id")).containsEntry("recordId", created.get("recordId"))
                .containsEntry("version", 1).containsEntry("firstName", "Jeanne-Marie");
        assertThat(count("SELECT count(*) FROM audit_event WHERE event_type = 'PATIENT_UPDATED' AND resource_id = '" + created.get("id") + "' AND resource_version = 1")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM outbox_event WHERE event_type = 'PATIENT_UPDATED' AND resource_id = '" + created.get("id") + "'")).isEqualTo(1);
        assertThat(send(HttpMethod.PATCH, clinicianA, uri, corrected, Map.of("If-Match", "\"0\"")).getStatusCode()).as("the old version is stale").isEqualTo(HttpStatus.PRECONDITION_FAILED);
        assertThat(lastNames(get(clinicianA, PATIENTS.formatted(practiceA) + "?q=jeanne-marie"))).containsExactly("Dupont");
    }

    @Test
    @DisplayName("C7 a correction is refused without the grant and never reaches another practice")
    void correctionAuthorization() {
        Map<String, Object> created = create(clinicianA, practiceA, identity("Jeanne", "Dupont", "1985-06-02")).getBody();
        String uri = PATIENTS.formatted(practiceA) + "/" + created.get("id");
        Map<String, Object> corrected = identity("Jeanne-Marie", "Dupont", "1985-06-02");
        assertThat(send(HttpMethod.PATCH, readOnlyA, uri, corrected, Map.of("If-Match", "\"0\"")).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(send(HttpMethod.PATCH, clinicianB, uri, corrected, Map.of("If-Match", "\"0\"")).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        String foreignPath = PATIENTS.formatted(practiceB) + "/" + created.get("id");
        assertThat(send(HttpMethod.PATCH, clinicianB, foreignPath, corrected, Map.of("If-Match", "\"0\"")).getStatusCode()).as("id of another practice under own practice").isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(send(HttpMethod.PATCH, clinicianA, PATIENTS.formatted(practiceA) + "/" + UUID.randomUUID(), corrected, Map.of("If-Match", "\"0\"")).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(count("SELECT count(*) FROM patient WHERE id = '" + created.get("id") + "' AND first_name = 'Jeanne'")).isEqualTo(1);
    }

    @Test
    @DisplayName("C12 scheduling registration needs both grants and returns the minimal projection")
    void schedulingRegistration() {
        Map<String, Object> body = identity("Jeanne", "Dupont", "1985-06-02");
        assertThat(send(HttpMethod.POST, schedulerOnlyA, SCHEDULING.formatted(practiceA), body, Map.of("Idempotency-Key", "key-" + UUID.randomUUID())).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(send(HttpMethod.POST, adminOnlyA, SCHEDULING.formatted(practiceA), body, Map.of("Idempotency-Key", "key-" + UUID.randomUUID())).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(send(HttpMethod.POST, clinicianA, SCHEDULING.formatted(practiceA), body, Map.of("Idempotency-Key", "key-" + UUID.randomUUID())).getStatusCode()).as("clinical grants alone do not register for scheduling").isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(patientCount(practiceA)).isEqualTo(4);

        ResponseEntity<Map<String, Object>> created = send(HttpMethod.POST, schedulerA, SCHEDULING.formatted(practiceA), body, Map.of("Idempotency-Key", "key-" + UUID.randomUUID()));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().keySet()).containsExactlyInAnyOrder(
                "id", "practiceId", "recordId", "firstName", "lastName", "dateOfBirth", "contactEmail", "phone");
        assertThat(count("SELECT count(*) FROM patient_record WHERE practice_id = '" + practiceA + "' AND id = '" + created.getBody().get("recordId") + "'")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM audit_event WHERE event_type = 'PATIENT_CREATED' AND safe_metadata->>'channel' = 'SCHEDULING' AND resource_id = '" + created.getBody().get("id") + "'")).isEqualTo(1);
        // The same grants still give no clinical route.
        assertThat(get(schedulerA, "/api/v1/practices/" + practiceA + "/records/" + created.getBody().get("recordId")).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("C12 scheduling duplicates return minimal candidates and accept a named confirmation")
    void schedulingDuplicates() {
        Map<String, Object> body = identity("Claire", "Martin", "1978-04-18");
        ResponseEntity<Map<String, Object>> refused = send(HttpMethod.POST, schedulerA, SCHEDULING.formatted(practiceA), body, Map.of("Idempotency-Key", "key-" + UUID.randomUUID()));
        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(refused.getBody()).containsEntry("code", "DUPLICATE_PATIENT");
        assertThat(refused.getBody().toString()).contains(claireRecord.toString());
        body.put("confirmedDistinctRecordIds", List.of(claireRecord.toString()));
        assertThat(send(HttpMethod.POST, schedulerA, SCHEDULING.formatted(practiceA), body, Map.of("Idempotency-Key", "key-" + UUID.randomUUID())).getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("C13 failed creations leave no audit, outbox or receipt row")
    void refusedRegistrationsLeaveNoTrace() {
        long audit = count("SELECT count(*) FROM audit_event WHERE event_type = 'PATIENT_CREATED'");
        long outbox = count("SELECT count(*) FROM outbox_event WHERE event_type = 'PATIENT_CREATED'");
        long receipts = count("SELECT count(*) FROM idempotency_receipt");
        create(clinicianA, practiceA, identity("Claire", "Martin", "1978-04-18"));
        create(readOnlyA, practiceA, identity("Jeanne", "Dupont", "1985-06-02"));
        create(clinicianA, practiceA, identity("---", "Dupont", "1985-06-02"));
        assertThat(count("SELECT count(*) FROM audit_event WHERE event_type = 'PATIENT_CREATED'")).isEqualTo(audit);
        assertThat(count("SELECT count(*) FROM outbox_event WHERE event_type = 'PATIENT_CREATED'")).isEqualTo(outbox);
        assertThat(count("SELECT count(*) FROM idempotency_receipt")).isEqualTo(receipts);
    }

    @Test
    @DisplayName("C7 a scheduler of one practice is refused in another, on both routes, with no write")
    void schedulingRoutesRefuseAForeignPractice() {
        long before = patientCount(practiceB);
        UUID unknown = UUID.randomUUID();
        for (UUID target : List.of(practiceB, unknown)) {
            assertThat(get(schedulerA, SCHEDULING.formatted(target) + "?q=hugo").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            ResponseEntity<Map<String, Object>> registration = send(HttpMethod.POST, schedulerA, SCHEDULING.formatted(target),
                    identity("Jeanne", "Dupont", "1985-06-02"), Map.of("Idempotency-Key", "key-" + UUID.randomUUID()));
            assertThat(registration.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(registration.getBody().toString()).doesNotContain("Hugo").doesNotContain("Petit");
        }
        assertThat(patientCount(practiceB)).isEqualTo(before);
    }

    @Test
    @DisplayName("C6 more than ten likely duplicates can never be named, so the registration stays refused")
    void elevenMatchesStayRefused() {
        List<String> named = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            named.add(seed(practiceA, "Paul", "Durand", (1950 + i) + "-01-01").toString());
        }
        Map<String, Object> body = identity("Paul", "Durand", "1999-09-09");
        ResponseEntity<Map<String, Object>> refused = create(clinicianA, practiceA, body);
        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        @SuppressWarnings("unchecked")
        List<Object> candidates = (List<Object>) refused.getBody().get("candidates");
        assertThat(candidates).hasSize(10);
        body.put("confirmedDistinctRecordIds", named.subList(0, 10));
        assertThat(create(clinicianA, practiceA, body).getStatusCode()).as("naming ten of eleven is not enough").isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("search and duplicates treat a decomposed accent like the precomposed letter, and keep non-Latin names comparable")
    void canonicallyEquivalentSpellings() {
        assertThat(lastNames(get(clinicianA, PATIENTS.formatted(practiceA) + "?q=Le%CC%81ger"))).containsExactly("Léger-Müller");
        ResponseEntity<Map<String, Object>> decomposed = create(clinicianA, practiceA, identity("E\u0301loi\u0308se", "Le\u0301ger-Mu\u0308ller", "1985-11-02"));
        assertThat(decomposed.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        assertThat(create(clinicianA, practiceA, identity("Иван", "Петров", "1980-05-05")).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(create(clinicianA, practiceA, identity("иван", "ПЕТРОВ", "1970-01-01")).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(lastNames(get(clinicianA, PATIENTS.formatted(practiceA) + "?q=%D0%BF%D0%B5%D1%82"))).containsExactly("Петров");
    }

    @Test
    @DisplayName("a conditional record read after a correction returns the corrected patient")
    void recordEtagFollowsTheIdentity() {
        Map<String, Object> created = create(clinicianA, practiceA, identity("Jeanne", "Dupont", "1985-06-02")).getBody();
        String record = "/api/v1/practices/" + practiceA + "/records/" + created.get("recordId");
        ResponseEntity<Map<String, Object>> first = get(clinicianA, record);
        String etag = first.getHeaders().getETag();
        assertThat(etag).isNotBlank();
        send(HttpMethod.PATCH, clinicianA, PATIENTS.formatted(practiceA) + "/" + created.get("id"),
                identity("Jeanne-Marie", "Dupont", "1985-06-02"), Map.of("If-Match", "\"0\""));
        @SuppressWarnings("unchecked")
        ResponseEntity<Map<String, Object>> conditional = (ResponseEntity<Map<String, Object>>) (ResponseEntity<?>) client().get()
                .uri(java.net.URI.create("http://localhost:" + port + record)).header(HttpHeaders.COOKIE, clinicianA)
                .header(HttpHeaders.IF_NONE_MATCH, etag).retrieve().onStatus(s -> true, (rq, rs) -> {}).toEntity(Map.class);
        assertThat(conditional.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(conditional.getHeaders().getETag()).isNotEqualTo(etag);
        @SuppressWarnings("unchecked")
        Map<String, Object> patient = (Map<String, Object>) conditional.getBody().get("patient");
        assertThat(patient).containsEntry("firstName", "Jeanne-Marie");
    }

    @Test
    @DisplayName("the cursor carries no name, cannot be redirected to another practice and is checked even for an unmatched query")
    void cursorIntegrity() {
        String uri = PATIENTS.formatted(practiceA) + "?limit=2";
        String cursor = (String) get(clinicianA, uri).getBody().get("nextCursor");
        String decoded = new String(java.util.Base64.getUrlDecoder().decode(cursor), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(decoded).doesNotContain("bernard").doesNotContain("collet");
        UUID foreignPatient = UUID.fromString(String.valueOf(jdbcValue("SELECT patient_id FROM patient_record WHERE id = '" + hugoRecord + "'")));
        String forged = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                (decoded.substring(0, decoded.indexOf('\n') + 1) + foreignPatient).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(get(clinicianA, uri + "&cursor=" + forged).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(get(clinicianA, PATIENTS.formatted(practiceA) + "?q=!!!&cursor=broken").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // The reviewer's case: the right binding for a query that cannot match (two different dates) with a
        // foreign patient id must be refused, not answered with an empty page.
        String binding;
        try {
            binding = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(
                    ("v1|" + practiceA + "|" + clinicianAAccount + "||null").getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        String foreignCursor = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                (binding + "\n" + foreignPatient).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String unmatched = PATIENTS.formatted(practiceA) + "?q=01/01/2000%2002/01/2000";
        assertThat(get(clinicianA, unmatched).getBody()).as("without a cursor the empty page is kept").containsEntry("nextCursor", null);
        assertThat(get(clinicianA, unmatched + "&cursor=" + foreignCursor).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private Object jdbcValue(String sql) {
        try (Connection c = DriverManager.getConnection(POSTGRES_CONTAINER.getJdbcUrl(), POSTGRES_CONTAINER.getUsername(), POSTGRES_CONTAINER.getPassword());
             Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getObject(1);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("malformed preconditions and confirmation lists are 400, never 500")
    void malformedInputIsRefused() {
        Map<String, Object> created = create(clinicianA, practiceA, identity("Jeanne", "Dupont", "1985-06-02")).getBody();
        String uri = PATIENTS.formatted(practiceA) + "/" + created.get("id");
        Map<String, Object> corrected = identity("Jeanne-Marie", "Dupont", "1985-06-02");
        assertThat(send(HttpMethod.PATCH, clinicianA, uri, corrected, Map.of("If-Match", "W/\"0\"")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(send(HttpMethod.PATCH, clinicianA, uri, corrected, Map.of("If-Match", "0")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        Map<String, Object> withNull = identity("Claire", "Martin", "1978-04-18");
        withNull.put("confirmedDistinctRecordIds", java.util.Arrays.asList(claireRecord.toString(), null));
        assertThat(create(clinicianA, practiceA, withNull).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        Map<String, Object> repeated = identity("Claire", "Martin", "1978-04-18");
        repeated.put("confirmedDistinctRecordIds", List.of(claireRecord.toString(), claireRecord.toString()));
        assertThat(create(clinicianA, practiceA, repeated).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
