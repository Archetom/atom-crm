package io.github.archetom.crm.infra.rest.security;

import io.github.archetom.crm.application.security.TenantAuthorityResolver;
import io.github.archetom.crm.infra.rest.config.SecurityConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrustedHeaderAuthenticationFilterTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void combinesServerPlatformAuthoritiesWithResolvedTenantAuthorities() throws Exception {
        TrustedHeaderAuthenticationFilter filter =
                new TrustedHeaderAuthenticationFilter(Set.of("organization:request"),
                        (actorId, tenantId) -> Set.of("users:read"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users");
        request.addHeader(TrustedHeaderAuthenticationFilter.USER_ID_HEADER, "7");
        request.addHeader(TrustedHeaderAuthenticationFilter.TENANT_ID_HEADER, "11");
        request.addHeader("X-Admin", "true");
        request.addHeader("X-Authorities", "users:delete");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean continued = new AtomicBoolean();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
            continued.set(true);
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            assertInstanceOf(ActorPrincipal.class, authentication.getPrincipal());
            ActorPrincipal principal = (ActorPrincipal) authentication.getPrincipal();
            assertEquals(7L, principal.userId());
            assertEquals(11L, principal.tenantId());
            assertEquals(Set.of("organization:request", "users:read"), authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.toSet()));
        });

        assertTrue(continued.get());
        assertEquals(200, response.getStatus());
    }

    @Test
    void rejectsIncompleteOrNonPositiveCredentials() throws Exception {
        TrustedHeaderAuthenticationFilter filter =
                new TrustedHeaderAuthenticationFilter(Set.of("organization:request"),
                        (actorId, tenantId) -> Set.of());

        assertRejected(filter, "7", null);
        assertRejected(filter, null, "11");
        assertRejected(filter, "0", "11");
        assertRejected(filter, "not-a-number", "11");
    }

    @Test
    void configurationRequiresDevOrTestAndExplicitEnablement() throws Exception {
        Method factory = SecurityConfig.class.getMethod(
                "trustedHeaderAuthenticationFilter", Environment.class, TenantAuthorityResolver.class);
        Profile profile = factory.getAnnotation(Profile.class);
        Profiles expression = Profiles.of(profile.value());

        assertTrue(environment("dev").acceptsProfiles(expression));
        assertTrue(environment("test").acceptsProfiles(expression));
        assertFalse(environment("prod").acceptsProfiles(expression));
        assertFalse(environment("dev", "prod").acceptsProfiles(expression));
        assertFalse(environment().acceptsProfiles(expression));

        ConditionalOnProperty property = factory.getAnnotation(ConditionalOnProperty.class);
        assertEquals("atom.security.trusted-header", property.prefix());
        assertArrayEquals(new String[]{"enabled"}, property.name());
        assertEquals("true", property.havingValue());
        assertFalse(property.matchIfMissing());
    }

    @Test
    void resolverFailureKeepsOnlyPlatformAuthorities() throws Exception {
        TrustedHeaderAuthenticationFilter filter = new TrustedHeaderAuthenticationFilter(
                Set.of("organization:request"), (actorId, tenantId) -> {
                    throw new IllegalStateException("database unavailable");
                });
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users");
        request.addHeader(TrustedHeaderAuthenticationFilter.USER_ID_HEADER, "7");
        request.addHeader(TrustedHeaderAuthenticationFilter.TENANT_ID_HEADER, "11");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean continued = new AtomicBoolean();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
            continued.set(true);
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            assertEquals(Set.of("organization:request"), authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.toSet()));
        });

        assertTrue(continued.get());
        assertEquals(200, response.getStatus());
    }

    private void assertRejected(
            TrustedHeaderAuthenticationFilter filter,
            String userId,
            String tenantId
    ) throws Exception {
        SecurityContextHolder.clearContext();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users");
        if (userId != null) {
            request.addHeader(TrustedHeaderAuthenticationFilter.USER_ID_HEADER, userId);
        }
        if (tenantId != null) {
            request.addHeader(TrustedHeaderAuthenticationFilter.TENANT_ID_HEADER, tenantId);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean continued = new AtomicBoolean();

        filter.doFilter(request, response,
                (ignoredRequest, ignoredResponse) -> continued.set(true));

        assertEquals(401, response.getStatus());
        assertFalse(continued.get());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    private MockEnvironment environment(String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        return environment;
    }
}
