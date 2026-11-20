package dev.aymen.etaye;

import jakarta.servlet.http.Cookie;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.CookieSerializer.CookieValue;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Test-only session helper that provisions real sessions into Spring Session JDBC
 * and formats the exact __Host-etaye-session cookie using the production CookieSerializer.
 */
@Component
public class TestSessionHelper {

    @SuppressWarnings("rawtypes")
    private final FindByIndexNameSessionRepository sessionRepository;
    private final CookieSerializer cookieSerializer;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectProvider<Clock> clockProvider;

    @SuppressWarnings("rawtypes")
    public TestSessionHelper(FindByIndexNameSessionRepository sessionRepository,
                             CookieSerializer cookieSerializer,
                             JdbcTemplate jdbcTemplate,
                             ObjectProvider<Clock> clockProvider) {
        this.sessionRepository = sessionRepository;
        this.cookieSerializer = cookieSerializer;
        this.jdbcTemplate = jdbcTemplate;
        this.clockProvider = clockProvider;
    }

    @SuppressWarnings("unchecked")
    public String createSessionCookie(UUID accountId) {
        return createSessionCookie(accountId, java.time.Duration.ofSeconds(20));
    }

    @SuppressWarnings("unchecked")
    public String createSessionCookie(UUID accountId, java.time.Duration maxInactiveInterval) {
        Session session = sessionRepository.createSession();
        session.setMaxInactiveInterval(maxInactiveInterval);
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        Authentication auth = new UsernamePasswordAuthenticationToken(
                accountId.toString(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        securityContext.setAuthentication(auth);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);
        sessionRepository.save(session);

        Clock clock = clockProvider.getIfAvailable();
        if (clock != null) {
            long now = clock.millis();
            jdbcTemplate.update("UPDATE spring_session SET creation_time = ?, last_access_time = ? WHERE session_id = ?",
                    now, now, session.getId());
        }

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        cookieSerializer.writeCookieValue(new CookieValue(request, response, session.getId()));
        Cookie cookie = response.getCookie("__Host-etaye-session");
        if (cookie != null) {
            return cookie.getName() + "=" + cookie.getValue();
        }
        return "__Host-etaye-session=" + session.getId();
    }
}
