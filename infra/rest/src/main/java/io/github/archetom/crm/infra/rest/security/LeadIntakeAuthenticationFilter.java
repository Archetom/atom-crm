package io.github.archetom.crm.infra.rest.security;

import io.github.archetom.crm.application.security.LeadIntakeApiKey;
import io.github.archetom.crm.application.service.LeadIntakeAuthenticationService;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Converts only a verified lead-intake API key into its configured actor and tenant. */
@Component
@RequiredArgsConstructor
public class LeadIntakeAuthenticationFilter extends OncePerRequestFilter {

    public static final String API_KEY_HEADER = "X-API-Key";
    private static final String PATH = "/api/v1/open/leads";

    private final LeadIntakeAuthenticationService authenticationService;
    private final LeadIntakeRateLimiter rateLimiter;
    private final LeadIntakeClientAddressResolver clientAddressResolver;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !HttpMethod.POST.matches(request.getMethod()) || !matchesOpenLeadPath(request);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String sourceAddress = clientAddressResolver.resolve(request);
        LeadIntakeRateLimiter.SourceAttempt sourceAttempt = rateLimiter
                .tryReserveSourceAttempt(sourceAddress);
        if (sourceAttempt == null) {
            response.sendError(429);
            return;
        }
        try {
            String apiKey = request.getHeader(API_KEY_HEADER);
            var credential = LeadIntakeApiKey.parse(apiKey);
            if (credential.isEmpty()) {
                sourceAttempt.fail();
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
            var parts = credential.get();
            LeadIntakeRateLimiter.VerifiedKeyAttempt keyAttempt = rateLimiter.tryReserveVerifiedKey(
                    parts.tenantId().getValue(), LeadIntakeApiKey.sha256(apiKey), parts.keyId());
            if (keyAttempt == null) {
                sourceAttempt.succeed();
                response.sendError(429);
                return;
            }
            try {
                var verified = authenticationService.authenticate(apiKey);
                if (verified.isEmpty()) {
                    sourceAttempt.fail();
                    keyAttempt.fail();
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                    return;
                }
                sourceAttempt.succeed();
                var intake = verified.get();
                if (!keyAttempt.succeed()) {
                    response.sendError(429);
                    return;
                }
                var principal = new ActorPrincipal(intake.ownerActorId(), intake.tenantId());
                var authorities = java.util.List.of(
                        new SimpleGrantedAuthority(AuthorityCatalog.LEADS_WRITE));
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                        principal, null, authorities));
                SecurityContextHolder.setContext(context);
                filterChain.doFilter(request, response);
            } finally {
                keyAttempt.fail();
            }
        } finally {
            sourceAttempt.cancel();
        }
    }

    private boolean matchesOpenLeadPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (uri == null) {
            return false;
        }
        if (contextPath != null && !contextPath.isEmpty()) {
            if (!uri.startsWith(contextPath)) {
                return false;
            }
            uri = uri.substring(contextPath.length());
        }
        return PATH.equals(uri);
    }
}
