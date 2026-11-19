package dev.aymen.etaye.infrastructure.security;

import dev.aymen.etaye.identity.repository.IdentityLookupRepository;
import dev.aymen.etaye.infrastructure.error.ProblemResponseWriter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.session.SessionRepository;
import org.springframework.session.jdbc.config.annotation.web.http.EnableJdbcHttpSession;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

@Configuration
@EnableWebSecurity
@EnableJdbcHttpSession
public class SecurityConfig {

    private final ProblemAuthenticationEntryPoint authenticationEntryPoint;
    private final ProblemAccessDeniedHandler accessDeniedHandler;
    private final ProblemResponseWriter problemResponseWriter;
    private final IdentityLookupRepository identityLookupRepository;
    private final ObjectProvider<SessionRepository<?>> sessionRepositoryProvider;

    public SecurityConfig(ProblemAuthenticationEntryPoint authenticationEntryPoint,
                          ProblemAccessDeniedHandler accessDeniedHandler,
                          ProblemResponseWriter problemResponseWriter,
                          IdentityLookupRepository identityLookupRepository,
                          ObjectProvider<SessionRepository<?>> sessionRepositoryProvider) {
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
        this.problemResponseWriter = problemResponseWriter;
        this.identityLookupRepository = identityLookupRepository;
        this.sessionRepositoryProvider = sessionRepositoryProvider;
    }

    @Bean
    public CookieSerializer cookieSerializer() {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName("__Host-etaye-session");
        serializer.setUseHttpOnlyCookie(true);
        serializer.setUseSecureCookie(true);
        serializer.setSameSite("Lax");
        serializer.setCookiePath("/");
        serializer.setDomainName(null);
        return serializer;
    }

    @Bean
    public CsrfTokenRepository csrfTokenRepository() {
        HttpSessionCsrfTokenRepository repository = new HttpSessionCsrfTokenRepository();
        repository.setHeaderName("X-CSRF-TOKEN");
        return repository;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        HeaderOnlyCsrfTokenRequestAttributeHandler csrfRequestHandler = new HeaderOnlyCsrfTokenRequestAttributeHandler();

        http
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler)
            )
            .csrf(csrf -> csrf
                .csrfTokenRepository(csrfTokenRepository())
                .csrfTokenRequestHandler(csrfRequestHandler)
                .ignoringRequestMatchers("/api/v1/csrf", "/api/v1/probes/**")
            )
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/csrf", "/api/v1/probes/**").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterAfter(
                new IdentityResolutionFilter(
                        identityLookupRepository,
                        authenticationEntryPoint,
                        problemResponseWriter,
                        sessionRepositoryProvider.getIfAvailable()
                ),
                SecurityContextHolderFilter.class
            );

        return http.build();
    }
}
