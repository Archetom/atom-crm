package io.github.archetom.crm.infra.rest.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Applies anonymous admission control before authentication and installation reach the application. */
@Component
@RequiredArgsConstructor
public class AuthenticationRateLimitFilter extends OncePerRequestFilter {

    private static final String LOGIN_OPTIONS = "/api/v1/authentication/password/login-options";
    private static final String SESSIONS = "/api/v1/authentication/password/sessions";
    private static final String RESET_REQUESTS = "/api/v1/authentication/password/reset-requests";
    private static final String RESET_CONFIRMATIONS = "/api/v1/authentication/password/reset-confirmations";
    private static final String INSTALLATION_STATUS = "/api/v1/installation";
    private static final String INSTALLATION_TENANT_ADMIN = "/api/v1/installation/tenant-admin";

    private final AuthenticationRateLimiter rateLimiter;
    private final LeadIntakeClientAddressResolver clientAddressResolver;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return lane(request) == null;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!rateLimiter.tryAcquire(lane(request), clientAddressResolver.resolve(request))) {
            response.sendError(429);
            return;
        }
        filterChain.doFilter(request, response);
    }

    /**
     * Only the anonymous routes are metered. Cookie-authenticated session reads and sign-out are
     * cheap and already bound to an established session.
     */
    private AuthenticationRateLimiter.Lane lane(HttpServletRequest request) {
        String path = applicationPath(request);
        if (path == null) {
            return null;
        }
        boolean post = HttpMethod.POST.matches(request.getMethod());
        if (post && (LOGIN_OPTIONS.equals(path) || SESSIONS.equals(path)
                || RESET_CONFIRMATIONS.equals(path) || INSTALLATION_TENANT_ADMIN.equals(path))) {
            return AuthenticationRateLimiter.Lane.CREDENTIAL;
        }
        if (post && RESET_REQUESTS.equals(path)) {
            return AuthenticationRateLimiter.Lane.RESET_REQUEST;
        }
        if (HttpMethod.GET.matches(request.getMethod()) && INSTALLATION_STATUS.equals(path)) {
            return AuthenticationRateLimiter.Lane.STATUS;
        }
        return null;
    }

    private String applicationPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null) {
            return null;
        }
        String context = request.getContextPath();
        if (context != null && !context.isEmpty() && uri.startsWith(context)) {
            uri = uri.substring(context.length());
        }
        return uri;
    }
}
