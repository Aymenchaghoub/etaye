package dev.aymen.etaye.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.util.Assert;

/**
 * CSRF request attribute handler that resolves the token ONLY from the configured HTTP header
 * (e.g. X-CSRF-TOKEN) and strictly ignores any token supplied as a request parameter (such as _csrf).
 */
public class HeaderOnlyCsrfTokenRequestAttributeHandler extends CsrfTokenRequestAttributeHandler {

    @Override
    public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
        Assert.notNull(request, "request cannot be null");
        Assert.notNull(csrfToken, "csrfToken cannot be null");
        return request.getHeader(csrfToken.getHeaderName());
    }
}
