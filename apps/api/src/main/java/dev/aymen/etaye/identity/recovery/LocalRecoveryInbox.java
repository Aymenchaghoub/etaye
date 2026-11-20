package dev.aymen.etaye.identity.recovery;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.SequencedMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "etaye.demo.recovery-inbox", havingValue = "true")
public class LocalRecoveryInbox {

    private static final String HOST = "127.0.0.1";
    private static final int PORT = 8025;
    private static final String INBOX_ORIGIN = "http://" + HOST + ":" + PORT;
    private static final String RESET_URL = "http://localhost:5173/recuperation/nouveau#";
    private static final int CAPACITY = 64;

    private final SequencedMap<String, CapturedLink> links = new LinkedHashMap<>();
    private final HttpServer server;
    private final ScheduledExecutorService expiry;

    public LocalRecoveryInbox() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getByName(HOST), PORT), 4);
        server.createContext("/", this::showInbox);
        server.start();
        expiry = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().factory());
        expiry.scheduleAtFixedRate(this::purgeExpired, 1, 1, TimeUnit.SECONDS);
    }

    public synchronized void capture(String email, String token, Instant expiresAt) {
        if (!email.endsWith("@example.test")) {
            return;
        }
        var now = Instant.now();
        removeExpired(now);
        if (!expiresAt.isAfter(now)) {
            return;
        }
        CapturedLink previous = links.get(email);
        if (previous != null && previous.expiresAt().isAfter(expiresAt)) {
            return;
        }
        links.remove(email);
        if (links.size() >= CAPACITY) {
            links.pollFirstEntry();
        }
        links.put(email, new CapturedLink(token, expiresAt));
    }

    public synchronized void remove(String token) {
        links.values().removeIf(link -> link.token().equals(token));
    }

    @PreDestroy
    void stop() {
        expiry.shutdownNow();
        server.stop(0);
        synchronized (this) {
            links.clear();
        }
    }

    private synchronized void purgeExpired() {
        removeExpired(Instant.now());
    }

    private void showInbox(HttpExchange exchange) throws IOException {
        try (exchange) {
            var request = exchange.getRequestHeaders();
            String origin = request.getFirst("Origin");
            String site = request.getFirst("Sec-Fetch-Site");
            if (!exchange.getRemoteAddress().getAddress().isLoopbackAddress()
                    || !(HOST + ":" + PORT).equals(request.getFirst("Host"))
                    || (origin != null && !INBOX_ORIGIN.equals(origin))
                    || (site != null && !"none".equals(site) && !"same-origin".equals(site))
                    || !"/".equals(exchange.getRequestURI().toString())
                    || !"GET".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }

            var headers = exchange.getResponseHeaders();
            headers.set("Content-Type", "text/html; charset=UTF-8");
            headers.set("Cache-Control", "no-store");
            headers.set("Referrer-Policy", "no-referrer");
            headers.set("X-Content-Type-Options", "nosniff");
            headers.set("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'; base-uri 'none'");
            byte[] page = page().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, page.length);
            exchange.getResponseBody().write(page);
        }
    }

    private synchronized String page() {
        removeExpired(Instant.now());
        var html = new StringBuilder("""
                <!doctype html>
                <html lang="fr"><meta charset="utf-8">
                <title>Boîte de démonstration Étaye</title>
                <h1>Liens de récupération de démonstration</h1><ul>
                """);
        links.forEach((email, link) -> html.append("<li><a rel=\"noreferrer\" href=\"")
                .append(RESET_URL).append(link.token()).append("\">")
                .append(HtmlUtils.htmlEscape(email)).append("</a> · ").append(link.expiresAt()).append("</li>"));
        return html.append("</ul></html>").toString();
    }

    private void removeExpired(Instant now) {
        links.values().removeIf(link -> !link.expiresAt().isAfter(now));
    }

    private record CapturedLink(String token, Instant expiresAt) {}
}
