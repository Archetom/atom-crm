package io.github.archetom.crm.infra.rest.security;

import io.github.archetom.crm.application.security.TenantAuthorityResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Development and test adapter that converts trusted headers into an
 * authenticated Spring Security principal.
 *
 * <p>The filter is installed only by {@code SecurityConfig} when a dev or test
 * profile is active and {@code atom.security.trusted-header.enabled=true}.
 * Authorities are supplied by server configuration; request headers can never
 * grant roles or administrator privileges.</p>
 */
@Slf4j
public final class TrustedHeaderAuthenticationFilter extends OncePerRequestFilter {

    public static final String USER_ID_HEADER = "X-Dev-User-Id";
    public static final String TENANT_ID_HEADER = "X-Dev-Tenant-Id";

    private final List<String> platformAuthorities;
    private final TenantAuthorityResolver tenantAuthorityResolver;

    public TrustedHeaderAuthenticationFilter(Collection<String> authorities,
                                             TenantAuthorityResolver tenantAuthorityResolver) {
        this.platformAuthorities = authorities == null
                ? List.of()
                : authorities.stream()
                .filter(authority -> authority != null && !authority.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        if (tenantAuthorityResolver == null) {
            throw new IllegalArgumentException("tenantAuthorityResolver must not be null");
        }
        this.tenantAuthorityResolver = tenantAuthorityResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        if (current != null && current.isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        String userIdHeader = request.getHeader(USER_ID_HEADER);
        String tenantIdHeader = request.getHeader(TENANT_ID_HEADER);

        if (userIdHeader == null && tenantIdHeader == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            long userId = parsePositiveId(userIdHeader);
            long tenantId = parsePositiveId(tenantIdHeader);
            ActorPrincipal principal = new ActorPrincipal(userId, tenantId);
            Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                    principal, null, resolveAuthorities(userId, tenantId));

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
        } catch (IllegalArgumentException exception) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid trusted-header credentials");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private List<GrantedAuthority> resolveAuthorities(long userId, long tenantId) {
        Collection<String> tenantAuthorities;
        try {
            tenantAuthorities = tenantAuthorityResolver.resolve(userId, tenantId);
        } catch (RuntimeException exception) {
            // Identity remains verified, but tenant business access fails closed.
            log.warn("Tenant authority resolution failed closed: exceptionType={}",
                    exception.getClass().getName());
            tenantAuthorities = List.of();
        }
        LinkedHashSet<String> combined = new LinkedHashSet<>(platformAuthorities);
        if (tenantAuthorities != null) {
            combined.addAll(tenantAuthorities);
        }
        return combined.stream()
                .map(SimpleGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList();
    }

    private long parsePositiveId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Required trusted header is missing");
        }

        long id = Long.parseLong(value);
        if (id <= 0) {
            throw new IllegalArgumentException("Trusted-header IDs must be positive");
        }
        return id;
    }
}
