package io.github.archetom.crm.infra.rest.security;

import io.github.archetom.crm.application.security.TenantAuthorityResolver;
import io.github.archetom.crm.application.service.AuthenticationService;
import io.github.archetom.crm.application.vo.BrowserSessionVO;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/** Restores only a valid, current signed browser session into Spring Security. */
@Component
@RequiredArgsConstructor
public class BrowserSessionAuthenticationFilter extends OncePerRequestFilter {

    private final AuthenticationCookieService cookieService;
    private final AuthenticationService authenticationService;
    private final TenantAuthorityResolver tenantAuthorityResolver;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        if (current != null && current.isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        BrowserSessionVO session = cookieService.sessionToken(request)
                .flatMap(authenticationService::resumeBrowserSession)
                .orElse(null);
        if (session != null) {
            List<GrantedAuthority> authorities = tenantAuthorityResolver
                    .resolve(session.actorId(), session.tenantId()).stream()
                    .map(SimpleGrantedAuthority::new)
                    .map(GrantedAuthority.class::cast)
                    .toList();
            Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                    new ActorPrincipal(session.actorId(), session.tenantId()), null, authorities);
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            request.setAttribute(AuthenticationCookieService.SESSION_REQUEST_ATTRIBUTE, Boolean.TRUE);
        }
        filterChain.doFilter(request, response);
    }
}
