package dev.aymen.etaye.infrastructure.security;

import dev.aymen.etaye.identity.dto.AccountProfile;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
public class BrowserSessionLifetime {

    public static final Duration ANONYMOUS_LIFETIME = Duration.ofMinutes(5);

    private static final String SIGNED_IN_AT = BrowserSessionLifetime.class.getName() + ".signedInAt";
    private static final String LAST_ACTIVITY = BrowserSessionLifetime.class.getName() + ".lastActivity";
    private static final String CREDENTIAL_REVISION = BrowserSessionLifetime.class.getName() + ".credentialRevision";

    private final Duration idle;
    private final Duration absolute;
    private final Clock clock;
    private final CsrfTokenRepository csrf;
    private final HttpSessionSecurityContextRepository contexts = new HttpSessionSecurityContextRepository();

    public BrowserSessionLifetime(@Value("${etaye.web.session-idle:PT30M}") Duration idle,
                                  @Value("${etaye.web.session-absolute:PT8H}") Duration absolute,
                                  ObjectProvider<Clock> clock, CsrfTokenRepository csrf) {
        if (idle.toSeconds() < 1 || idle.compareTo(Duration.ofMinutes(30)) > 0
                || absolute.compareTo(idle) < 0 || absolute.compareTo(Duration.ofHours(8)) > 0) {
            throw new IllegalArgumentException("Invalid browser session lifetime");
        }
        this.idle = idle;
        this.absolute = absolute;
        this.clock = clock.getIfAvailable(Clock::systemUTC);
        this.csrf = csrf;
    }

    public HttpSession establish(HttpServletRequest request, HttpServletResponse response, AccountProfile account) {
        HttpSession session = request.getSession(false);
        request.changeSessionId();
        Instant now = clock.instant();
        session.setAttribute(SIGNED_IN_AT, now);
        session.setAttribute(LAST_ACTIVITY, now);
        session.setAttribute(CREDENTIAL_REVISION, account.credentialRevision());
        session.setMaxInactiveInterval((int) idle.toSeconds());

        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                account.id().toString(), null, List.of()));
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        csrf.saveToken(csrf.generateToken(request), request, response);
        return session;
    }

    public boolean isExpiredAnonymous(HttpSession session) {
        return session != null
                && session.getAttribute(SIGNED_IN_AT) == null
                && !clock.instant().isBefore(
                        Instant.ofEpochMilli(session.getCreationTime()).plus(ANONYMOUS_LIFETIME));
    }

    public void retireExpiredAnonymousSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (isExpiredAnonymous(session)) {
            session.invalidate();
        }
    }

    public boolean isCurrent(HttpSession session, long credentialRevision) {
        return session != null && revision(session) == credentialRevision && clock.instant().isBefore(expiresAt(session));
    }

    public void recordActivity(HttpServletRequest request, HttpSession session) {
        // Session inspection can poll for revoked grants without extending inactivity.
        boolean sessionStatus = (request.getMethod().equals("GET") || request.getMethod().equals("HEAD"))
                && request.getRequestURI().equals(request.getContextPath() + "/api/v1/session");
        if (!sessionStatus) {
            session.setAttribute(LAST_ACTIVITY, clock.instant());
        }
    }

    public Instant expiresAt(HttpSession session) {
        Instant absoluteExpiry = absoluteExpiry(session);
        Instant lastActivity = (Instant) session.getAttribute(LAST_ACTIVITY);
        Instant idleStart = lastActivity != null ? lastActivity : Instant.ofEpochMilli(session.getCreationTime());
        Instant idleExpiry = idleStart.plusSeconds(session.getMaxInactiveInterval());
        return absoluteExpiry.isBefore(idleExpiry) ? absoluteExpiry : idleExpiry;
    }

    private Instant absoluteExpiry(HttpSession session) {
        // Sessions persisted before V2 have no signed-in timestamp or credential revision.
        Instant signedInAt = (Instant) session.getAttribute(SIGNED_IN_AT);
        return (signedInAt != null ? signedInAt : Instant.ofEpochMilli(session.getCreationTime())).plus(absolute);
    }

    private long revision(HttpSession session) {
        Long revision = (Long) session.getAttribute(CREDENTIAL_REVISION);
        return revision != null ? revision : 0;
    }
}
