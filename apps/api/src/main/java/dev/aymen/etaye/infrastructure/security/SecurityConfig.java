package dev.aymen.etaye.infrastructure.security;

import dev.aymen.etaye.identity.dto.AuthenticatedAccount;
import dev.aymen.etaye.identity.service.IdentityLookupService;
import dev.aymen.etaye.infrastructure.error.ProblemResponseWriter;
import dev.aymen.etaye.infrastructure.demo.DemoFixture;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.session.SessionRepository;
import org.springframework.session.jdbc.config.annotation.web.http.EnableJdbcHttpSession;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.web.filter.CorsFilter;

@Configuration
@EnableWebSecurity
@EnableJdbcHttpSession(maxInactiveIntervalInSeconds = 1800)
public class SecurityConfig {

    private final ProblemAuthenticationEntryPoint authenticationEntryPoint;
    private final ProblemAccessDeniedHandler accessDeniedHandler;
    private final ProblemResponseWriter problemResponseWriter;
    private final IdentityLookupService identityLookupService;
    private final ObjectProvider<SessionRepository<?>> sessionRepositoryProvider;
    private final boolean demoEnabled;
    private final boolean demoPatientInputEnabled;

    public SecurityConfig(ProblemAuthenticationEntryPoint authenticationEntryPoint,
                          ProblemAccessDeniedHandler accessDeniedHandler,
                          ProblemResponseWriter problemResponseWriter,
                          IdentityLookupService identityLookupService,
                          ObjectProvider<SessionRepository<?>> sessionRepositoryProvider,
                          @Value("${etaye.demo.enabled:false}") boolean demoEnabled,
                          @Value("${etaye.demo.patient-input-enabled:false}") boolean demoPatientInputEnabled) {
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
        this.problemResponseWriter = problemResponseWriter;
        this.identityLookupService = identityLookupService;
        this.sessionRepositoryProvider = sessionRepositoryProvider;
        this.demoEnabled = demoEnabled;
        this.demoPatientInputEnabled = demoPatientInputEnabled;
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
    @Order(1)
    public SecurityFilterChain publicMetadataSecurityFilterChain(HttpSecurity http) throws Exception {
        String[] paths = demoEnabled
                ? new String[] {"/api/v1/probes/**", "/api/v1/meta", DemoFixture.CATALOGUE_PATH}
                : new String[] {"/api/v1/probes/**", "/api/v1/meta"};
        http
            .securityMatcher(paths)
            .cors(Customizer.withDefaults())
            .addFilterAfter(new RejectBearerFilter(accessDeniedHandler), CorsFilter.class)
            .addFilterBefore(new StatelessRequestFilter(), org.springframework.security.web.session.DisableEncodeUrlFilter.class)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(AbstractHttpConfigurer::disable)
            .requestCache(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    private static final class StatelessRequestFilter extends org.springframework.web.filter.OncePerRequestFilter {
        @Override
        protected void doFilterInternal(jakarta.servlet.http.HttpServletRequest request,
                                        jakarta.servlet.http.HttpServletResponse response,
                                        jakarta.servlet.FilterChain filterChain)
                throws jakarta.servlet.ServletException, java.io.IOException {
            jakarta.servlet.http.HttpServletRequest wrapped = new jakarta.servlet.http.HttpServletRequestWrapper(request) {
                @Override
                public jakarta.servlet.http.HttpSession getSession(boolean create) {
                    return null;
                }

                @Override
                public jakarta.servlet.http.HttpSession getSession() {
                    return null;
                }

                @Override
                public String getRequestedSessionId() {
                    return null;
                }

                @Override
                public boolean isRequestedSessionIdValid() {
                    return false;
                }
            };
            filterChain.doFilter(wrapped, response);
        }
    }

    @Bean
    @Order(2)
    public SecurityFilterChain securityFilterChain(HttpSecurity http, BrowserSessionLifetime lifetime) throws Exception {
        HeaderOnlyCsrfTokenRequestAttributeHandler csrfRequestHandler = new HeaderOnlyCsrfTokenRequestAttributeHandler();

        http
            .cors(Customizer.withDefaults())
            .addFilterAfter(new RejectBearerFilter(accessDeniedHandler), CorsFilter.class)
            .requestCache(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler)
            )
            .csrf(csrf -> csrf
                .csrfTokenRepository(csrfTokenRepository())
                .csrfTokenRequestHandler(csrfRequestHandler)
                .ignoringRequestMatchers("/api/v1/csrf")
            )
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
            )
            .authorizeHttpRequests(auth -> {
                auth.requestMatchers("/api/v1/csrf").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/session", "/api/v1/access-recovery",
                            "/api/v1/access-recovery/complete", "/api/v1/invitations/inspect",
                            "/api/v1/invitations/accept").permitAll();
                if (demoEnabled) {
                    auth.requestMatchers(HttpMethod.POST, DemoFixture.RESET_PATH)
                        .access((authentication, context) -> {
                            var account = context.getRequest().getAttribute(IdentityResolutionFilter.AUTHENTICATED_ACCOUNT_ATTR);
                            return new AuthorizationDecision(account instanceof AuthenticatedAccount resolved
                                    && DemoFixture.isOperator(resolved.account().id()));
                        });
                    // The public demo identities have no arbitrary patient-input surface unless the local
                    // package opts in (story 3.1 registers and corrects fictional patients on the loopback demo).
                    if (!demoPatientInputEnabled) {
                        auth.requestMatchers(HttpMethod.POST, "/api/v1/practices/{practiceId}/patients").denyAll();
                        auth.requestMatchers(HttpMethod.PATCH, "/api/v1/practices/{practiceId}/patients/{patientId}").denyAll();
                        auth.requestMatchers(HttpMethod.POST, "/api/v1/practices/{practiceId}/scheduling/patients").denyAll();
                    }
                }
                auth.anyRequest().authenticated();
            })
            .addFilterBefore(new ExistingSessionCsrfFilter(accessDeniedHandler, lifetime,
                    demoEnabled ? authenticationEntryPoint : null), CsrfFilter.class)
            .addFilterBefore(
                new IdentityResolutionFilter(
                        identityLookupService,
                        authenticationEntryPoint,
                        problemResponseWriter,
                        sessionRepositoryProvider.getIfAvailable(), lifetime
                ),
                CsrfFilter.class
            );

        return http.build();
    }
}
