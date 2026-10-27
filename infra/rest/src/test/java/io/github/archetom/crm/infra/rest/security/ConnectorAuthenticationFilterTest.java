package io.github.archetom.crm.infra.rest.security;

import io.github.archetom.crm.api.context.AuthenticatedConnector;
import io.github.archetom.crm.application.security.ConnectorCredential;
import io.github.archetom.crm.application.service.ConnectorAuthenticationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectorAuthenticationFilterTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validBearerCreatesOnlyAConnectorPrincipal() throws Exception {
        ConnectorCredential.Generated credential = ConnectorCredential.create();
        ConnectorAuthenticationService service = service(value -> Optional.of(
                new ConnectorAuthenticationService.VerifiedConnector(new AuthenticatedConnector(
                        credential.connectorRef().getValue(), new Object()))));
        ConnectorAuthenticationFilter filter = filter(service, 20);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean continued = new AtomicBoolean();

        filter.doFilter(request(credential.value()), response, (request, servletResponse) -> {
            continued.set(true);
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            assertInstanceOf(ConnectorPrincipal.class, authentication.getPrincipal());
            assertEquals(0, authentication.getAuthorities().size());
        });

        assertTrue(continued.get());
        assertEquals(200, response.getStatus());
    }

    @Test
    void missingOrRejectedCredentialsShareTheSafeUnauthorizedResponse() throws Exception {
        ConnectorAuthenticationService service = service(value -> Optional.empty());
        ConnectorAuthenticationFilter filter = filter(service, 20);
        MockHttpServletRequest missing = new MockHttpServletRequest(
                "POST", "/api/v1/connector-ingress/probe");
        missing.setRemoteAddr("203.0.113.1");
        MockHttpServletResponse missingResponse = new MockHttpServletResponse();
        filter.doFilter(missing, missingResponse, (request, response) -> {
            throw new AssertionError("missing credential must not proceed");
        });
        assertEquals(401, missingResponse.getStatus());

        ConnectorCredential.Generated credential = ConnectorCredential.create();
        MockHttpServletResponse rejectedResponse = new MockHttpServletResponse();
        filter.doFilter(request(credential.value()), rejectedResponse, (request, response) -> {
            throw new AssertionError("rejected credential must not proceed");
        });
        assertEquals(401, rejectedResponse.getStatus());
    }

    @Test
    void lookupFailureIsRetryableAndDoesNotConsumeTheInvalidSecretQuota() throws Exception {
        ConnectorCredential.Generated credential = ConnectorCredential.create();
        ConnectorAuthenticationService service = service(value -> {
            throw new IllegalStateException("storage unavailable");
        });
        ConnectorAuthenticationFilter filter = filter(service, 1);

        MockHttpServletResponse first = new MockHttpServletResponse();
        filter.doFilter(request(credential.value()), first, (request, response) -> { });
        MockHttpServletResponse second = new MockHttpServletResponse();
        filter.doFilter(request(credential.value()), second, (request, response) -> { });

        assertEquals(503, first.getStatus());
        assertEquals(503, second.getStatus());
    }

    @Test
    void invalidCredentialExhaustsTheSourceFailureQuotaBeforeAnotherLookup() throws Exception {
        ConnectorCredential.Generated credential = ConnectorCredential.create();
        ConnectorAuthenticationService service = service(value -> Optional.empty());
        ConnectorAuthenticationFilter filter = filter(service, 1);

        MockHttpServletResponse rejected = new MockHttpServletResponse();
        filter.doFilter(request(credential.value()), rejected, (request, response) -> { });
        MockHttpServletResponse limited = new MockHttpServletResponse();
        filter.doFilter(request(credential.value()), limited, (request, response) -> { });

        assertEquals(401, rejected.getStatus());
        assertEquals(429, limited.getStatus());
    }

    private ConnectorAuthenticationFilter filter(
            ConnectorAuthenticationService service, int sourceLimit) {
        return new ConnectorAuthenticationFilter(service,
                new ConnectorAuthenticationRateLimiter(60, sourceLimit, 20, 20, 20,
                        Duration.ofMinutes(2), Clock.systemUTC()),
                new LeadIntakeClientAddressResolver(""));
    }

    private ConnectorAuthenticationService service(
            Function<String, Optional<ConnectorAuthenticationService.VerifiedConnector>> verifier) {
        return new ConnectorAuthenticationService(null) {
            @Override
            public Optional<VerifiedConnector> authenticate(String credential) {
                return verifier.apply(credential);
            }
        };
    }

    private MockHttpServletRequest request(String credential) {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/api/v1/connector-ingress/probe");
        request.setRemoteAddr("203.0.113.1");
        request.addHeader("Authorization", "Bearer " + credential);
        return request;
    }
}
