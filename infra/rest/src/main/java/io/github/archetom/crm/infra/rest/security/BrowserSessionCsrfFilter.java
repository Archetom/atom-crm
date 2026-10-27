package io.github.archetom.crm.infra.rest.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Requires a same-origin double-submit token for unsafe requests authenticated by a browser cookie. */
@Component
public class BrowserSessionCsrfFilter extends OncePerRequestFilter {

    private final AuthenticationCookieService cookieService;

    public BrowserSessionCsrfFilter(AuthenticationCookieService cookieService) {
        this.cookieService = cookieService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        boolean browserSession = Boolean.TRUE.equals(
                request.getAttribute(AuthenticationCookieService.SESSION_REQUEST_ATTRIBUTE));
        if (!browserSession || safeMethod(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String expected = cookieService.csrfToken(request).orElse(null);
        String supplied = request.getHeader(AuthenticationCookieService.CSRF_HEADER);
        if (expected == null || supplied == null || !MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII), supplied.getBytes(StandardCharsets.US_ASCII))) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static boolean safeMethod(String method) {
        return "GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method);
    }
}
