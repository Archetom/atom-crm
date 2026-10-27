package io.github.archetom.crm.infra.rest.security;

import io.github.archetom.crm.application.security.ConnectorCredential;
import io.github.archetom.crm.application.service.ConnectorAuthenticationService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Enumeration;

/** Authenticates only the normalized connector-ingress route family. */
@Component
@RequiredArgsConstructor
public class ConnectorAuthenticationFilter extends OncePerRequestFilter {
    private static final String PATH_PREFIX = "/api/v1/connector-ingress/";
    private static final String AUTHORIZATION = "Authorization";

    private final ConnectorAuthenticationService authenticationService;
    private final ConnectorAuthenticationRateLimiter rateLimiter;
    private final LeadIntakeClientAddressResolver clientAddressResolver;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (uri == null) {
            return true;
        }
        if (contextPath != null && !contextPath.isEmpty()) {
            if (!uri.startsWith(contextPath)) {
                return true;
            }
            uri = uri.substring(contextPath.length());
        }
        return !uri.startsWith(PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        var sourceAttempt = rateLimiter.tryReserveSource(clientAddressResolver.resolve(request));
        if (sourceAttempt == null) {
            response.sendError(429);
            return;
        }
        ConnectorAuthenticationRateLimiter.CredentialAttempt credentialAttempt = null;
        try {
            String credential = bearerCredential(request);
            var parsed = ConnectorCredential.parse(credential);
            if (parsed.isEmpty()) {
                sourceAttempt.fail();
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
            var parts = parsed.get();
            credentialAttempt = rateLimiter.tryReserveCredential(
                    parts, parts.fingerprint());
            if (credentialAttempt == null) {
                sourceAttempt.cancel();
                response.sendError(429);
                return;
            }

            ConnectorAuthenticationService.VerifiedConnector verified;
            try {
                var result = authenticationService.authenticate(credential);
                if (result.isEmpty()) {
                    sourceAttempt.fail();
                    credentialAttempt.fail();
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                    return;
                }
                verified = result.get();
            } catch (RuntimeException exception) {
                // Lookup/storage failures are retryable unavailability, never invalid credentials.
                sourceAttempt.cancel();
                credentialAttempt.fail();
                response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                return;
            }

            sourceAttempt.succeed();
            if (!credentialAttempt.succeed()) {
                response.sendError(429);
                return;
            }
            ConnectorPrincipal principal = new ConnectorPrincipal(verified.connector());
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                    principal, null, java.util.List.of()));
            SecurityContextHolder.setContext(context);
            filterChain.doFilter(request, response);
        } finally {
            sourceAttempt.cancel();
            if (credentialAttempt != null) {
                credentialAttempt.fail();
            }
        }
    }

    private String bearerCredential(HttpServletRequest request) {
        Enumeration<String> headers = request.getHeaders(AUTHORIZATION);
        if (headers == null || !headers.hasMoreElements()) {
            return null;
        }
        String header = headers.nextElement();
        if (headers.hasMoreElements() || header == null || header.length() <= 7
                || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        String value = header.substring(7);
        return value.equals(value.trim()) ? value : null;
    }
}
