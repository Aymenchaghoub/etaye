package dev.aymen.etaye.infrastructure.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

@Configuration
class BrowserCorsConfig {

    @Bean
    UrlBasedCorsConfigurationSource corsConfigurationSource(
            @Value("${etaye.web.allowed-origins:}") String configuredOrigins) {
        var origins = Arrays.stream(configuredOrigins.split(","))
                .map(String::strip)
                .filter(origin -> !origin.isEmpty())
                .map(BrowserCorsConfig::exactOrigin)
                .toList();

        var policy = new CorsConfiguration();
        policy.setAllowedOrigins(origins);
        policy.setAllowedMethods(List.of("GET", "HEAD", "POST", "PATCH", "DELETE"));
        policy.setAllowedHeaders(List.of("Accept", "Content-Type", "X-CSRF-TOKEN", "X-Client-Version",
                "X-Correlation-Id", "Idempotency-Key", "If-Match", "If-None-Match"));
        policy.setAllowCredentials(false);

        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/v1/**", policy);
        return source;
    }

    private static String exactOrigin(String origin) {
        URI uri;
        try {
            uri = URI.create(origin);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Configure only exact HTTP or HTTPS browser origins");
        }
        if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                || uri.getHost() == null || uri.getRawUserInfo() != null
                || !uri.getRawPath().isEmpty() || uri.getRawQuery() != null || uri.getRawFragment() != null
                || uri.getPort() == 0 || uri.getPort() > 65535) {
            throw new IllegalArgumentException("Configure only exact HTTP or HTTPS browser origins");
        }
        return origin;
    }
}
