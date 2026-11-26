package dev.aymen.etaye.identity.recovery;

import dev.aymen.etaye.MutableTestClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class LocalRecoveryInboxTest {

    private final MutableTestClock clock = new MutableTestClock(Instant.parse("2026-11-26T10:00:00Z"));
    private LocalRecoveryInbox inbox;
    private URI origin;

    @BeforeEach
    void startInbox() throws IOException {
        inbox = new LocalRecoveryInbox(0, clock);
        origin = URI.create("http://127.0.0.1:" + inbox.address().getPort());
    }

    @AfterEach
    void stopInbox() {
        if (inbox != null) {
            inbox.stop();
        }
    }

    @Test
    void servesOnlyFictionalUnexpiredLinksAndKeepsTheLatestLink() throws Exception {
        assertThat(inbox.address().getAddress().isLoopbackAddress()).isTrue();
        Instant now = clock.instant();
        inbox.capture("outside@example.com", "outside-link", now.plusSeconds(120));
        inbox.capture("expired@example.test", "already-expired", now);
        inbox.capture("alice&bob@example.test", "initial-link", now.plusSeconds(15));
        inbox.capture("alice&bob@example.test", "current-link", now.plusSeconds(60));
        inbox.capture("alice&bob@example.test", "late-old-link", now.plusSeconds(30));
        inbox.capture("short-lived@example.test", "expires-now", now.plusSeconds(20));
        inbox.capture("redeemed@example.test", "redeemed-link", now.plusSeconds(60));
        inbox.remove("redeemed-link");
        clock.advanceSeconds(20);
        inbox.purgeExpired();

        var page = send(request("/").header("Origin", origin.toString()).header("Sec-Fetch-Site", "same-origin"));

        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(page.headers().firstValue("Content-Type")).hasValue("text/html; charset=UTF-8");
        assertThat(page.headers().firstValue("Cache-Control")).hasValue("no-store");
        assertThat(page.headers().firstValue("Referrer-Policy")).hasValue("no-referrer");
        assertThat(page.headers().firstValue("Content-Security-Policy")).hasValueSatisfying(policy ->
                assertThat(policy).contains("default-src 'none'", "frame-ancestors 'none'", "base-uri 'none'"));
        assertThat(page.body()).contains("http://localhost:5173/recuperation/nouveau#current-link", "alice&amp;bob@example.test")
                .doesNotContain("outside-link", "already-expired", "initial-link", "late-old-link", "expires-now", "redeemed-link");
    }

    @Test
    void publicPreviewRefusesOtherOriginsAndNonPreviewRequests() throws Exception {
        inbox.capture("fixture@example.test", "preview-only", clock.instant().plusSeconds(60));
        assertThat(send(request("/")).body()).contains("preview-only");

        HttpRequest.Builder[] deniedRequests = {
            request("/").header("Origin", "https://other.example.test"),
            request("/").header("Sec-Fetch-Site", "cross-site"),
            request("/?view=other"),
            request("/").POST(HttpRequest.BodyPublishers.noBody())
        };
        for (var denied : deniedRequests) {
            var response = send(denied);
            assertThat(response.statusCode()).isEqualTo(404);
            assertThat(response.body()).isEmpty();
        }
    }

    @Test
    void evictsOldestEntryWhenThePreviewReachesCapacity() throws Exception {
        Instant future = clock.instant().plusSeconds(60);
        for (int i = 0; i < 65; i++) {
            inbox.capture("fixture-" + i + "@example.test", "recovery-" + i, future);
        }

        var page = send(request("/").header("Sec-Fetch-Site", "none"));

        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(page.body()).doesNotContain("fixture-0@example.test")
                .contains("fixture-1@example.test", "fixture-64@example.test");
    }

    @Test
    void rejectsUnexpectedHostHeaderAndBindsDefaultPort() throws Exception {
        try (var socket = new java.net.Socket("127.0.0.1", inbox.address().getPort())) {
            socket.setSoTimeout(3000);
            var out = socket.getOutputStream();
            out.write("GET / HTTP/1.1\r\nHost: wrong.example:8025\r\nConnection: close\r\n\r\n"
                    .getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            out.flush();
            String raw = new String(socket.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.US_ASCII);
            assertThat(raw).startsWith("HTTP/1.1 404");
        }

        LocalRecoveryInbox defaultInbox = null;
        try {
            defaultInbox = new LocalRecoveryInbox();
            assertThat(defaultInbox.address().getPort()).isEqualTo(8025);
        } finally {
            if (defaultInbox != null) {
                defaultInbox.stop();
            }
        }
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(origin.resolve(path)).timeout(Duration.ofSeconds(3));
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws IOException, InterruptedException {
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()) {
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}
