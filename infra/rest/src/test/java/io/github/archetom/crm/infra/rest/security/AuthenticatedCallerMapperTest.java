package io.github.archetom.crm.infra.rest.security;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthenticatedCallerMapperTest {

    private final AuthenticatedCallerMapper mapper = new AuthenticatedCallerMapper();

    @Test
    void mapsOnlyTheVerifiedPrincipalAndItsAuthorities() {
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                new ActorPrincipal(7L, 11L),
                null,
                List.of(new SimpleGrantedAuthority("users:read")));

        AuthenticatedCaller caller = mapper.from(authentication);

        assertEquals(7L, caller.actorId());
        assertEquals(11L, caller.tenantId());
        assertEquals(Set.of("users:read"), caller.authorities());
    }

    @Test
    void mapsAuthorityValuesRatherThanTheirDisplayText() {
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                new ActorPrincipal(7L, 11L),
                null,
                List.of(new DisplayOnlyAuthority()));

        AuthenticatedCaller caller = mapper.from(authentication);

        assertEquals(Set.of("users:read"), caller.authorities());
    }

    @Test
    void rejectsMissingUnauthenticatedOrUnexpectedPrincipals() {
        assertThrows(AccessDeniedException.class, () -> mapper.from(null));
        assertThrows(AccessDeniedException.class, () -> mapper.from(
                new UsernamePasswordAuthenticationToken(new ActorPrincipal(7L, 11L), null)));
        assertThrows(AccessDeniedException.class, () -> mapper.from(
                UsernamePasswordAuthenticationToken.authenticated("actor", null, List.of())));
    }

    private static final class DisplayOnlyAuthority implements GrantedAuthority {

        @Override
        public String getAuthority() {
            return "users:read";
        }

        @Override
        public String toString() {
            return "display-only authority";
        }
    }
}
