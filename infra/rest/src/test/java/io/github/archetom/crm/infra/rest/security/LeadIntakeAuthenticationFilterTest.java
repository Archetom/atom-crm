package io.github.archetom.crm.infra.rest.security;

import io.github.archetom.crm.application.security.LeadIntakeApiKey;
import io.github.archetom.crm.application.service.LeadIntakeAuthenticationService;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LeadIntakeAuthenticationFilterTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void limitsInvalidRequestsBeforeCredentialVerification() throws Exception {
        AtomicInteger authenticationCalls = new AtomicInteger();
        LeadIntakeAuthenticationService authenticationService = new LeadIntakeAuthenticationService(null, null) {
            @Override
            public Optional<VerifiedLeadIntake> authenticate(String apiKey) {
                authenticationCalls.incrementAndGet();
                return Optional.empty();
            }
        };
        LeadIntakeAuthenticationFilter filter = new LeadIntakeAuthenticationFilter(
                authenticationService,
                new LeadIntakeRateLimiter(60, 1, 10,
                        java.time.Duration.ofMinutes(2), java.time.Clock.systemUTC()),
                new LeadIntakeClientAddressResolver(""));

        MockHttpServletRequest firstRequest = request();
        MockHttpServletResponse firstResponse = new MockHttpServletResponse();
        filter.doFilter(firstRequest, firstResponse, (ignoredRequest, ignoredResponse) -> {
            throw new AssertionError("Invalid credentials must not proceed");
        });
        assertEquals(401, firstResponse.getStatus());

        MockHttpServletRequest secondRequest = request();
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        filter.doFilter(secondRequest, secondResponse, (ignoredRequest, ignoredResponse) -> {
            throw new AssertionError("Rate-limited requests must not proceed");
        });
        assertEquals(429, secondResponse.getStatus());
        assertEquals(1, authenticationCalls.get());
    }

    @Test
    void authenticatesTheOpenLeadPathWhenTheApplicationHasAContextPath() throws Exception {
        AtomicInteger authenticationCalls = new AtomicInteger();
        LeadIntakeAuthenticationService authenticationService = new LeadIntakeAuthenticationService(null, null) {
            @Override
            public Optional<VerifiedLeadIntake> authenticate(String apiKey) {
                authenticationCalls.incrementAndGet();
                return Optional.empty();
            }
        };
        LeadIntakeAuthenticationFilter filter = new LeadIntakeAuthenticationFilter(
                authenticationService,
                new LeadIntakeRateLimiter(60, 1, 10,
                        java.time.Duration.ofMinutes(2), java.time.Clock.systemUTC()),
                new LeadIntakeClientAddressResolver(""));
        MockHttpServletRequest request = request();
        request.setContextPath("/crm");
        request.setRequestURI("/crm/api/v1/open/leads");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
            throw new AssertionError("Invalid credentials must not proceed");
        });

        assertEquals(401, response.getStatus());
        assertEquals(1, authenticationCalls.get());
    }

    @Test
    void reservesOnlyOneConcurrentInvalidAuthenticationAttemptBeforeCredentialLookup() throws Exception {
        AtomicInteger authenticationCalls = new AtomicInteger();
        CountDownLatch lookupStarted = new CountDownLatch(1);
        CountDownLatch releaseLookup = new CountDownLatch(1);
        LeadIntakeAuthenticationService authenticationService = new LeadIntakeAuthenticationService(null, null) {
            @Override
            public Optional<VerifiedLeadIntake> authenticate(String apiKey) {
                authenticationCalls.incrementAndGet();
                lookupStarted.countDown();
                try {
                    assertTrue(releaseLookup.await(5, TimeUnit.SECONDS));
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
                return Optional.empty();
            }
        };
        LeadIntakeAuthenticationFilter filter = filter(authenticationService,
                new LeadIntakeRateLimiter(60, 1, 10,
                        java.time.Duration.ofMinutes(2), java.time.Clock.systemUTC()));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            MockHttpServletResponse firstResponse = new MockHttpServletResponse();
            var first = executor.submit(() -> runFilter(filter, request(), firstResponse,
                    "Invalid credentials must not proceed"));
            assertTrue(lookupStarted.await(5, TimeUnit.SECONDS));

            MockHttpServletResponse secondResponse = new MockHttpServletResponse();
            var second = executor.submit(() -> runFilter(filter, request(), secondResponse,
                    "Rate-limited requests must not proceed"));
            second.get(5, TimeUnit.SECONDS);
            assertEquals(429, secondResponse.getStatus());
            assertEquals(1, authenticationCalls.get());

            releaseLookup.countDown();
            first.get(5, TimeUnit.SECONDS);
            assertEquals(401, firstResponse.getStatus());
        } finally {
            releaseLookup.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void rejectsAnAlreadyExhaustedVerifiedKeyBeforeAnotherCredentialLookup() throws Exception {
        LeadIntakeApiKey.Generated key = LeadIntakeApiKey.generate(new TenantId(9L));
        AtomicInteger authenticationCalls = new AtomicInteger();
        LeadIntakeAuthenticationService authenticationService = new LeadIntakeAuthenticationService(null, null) {
            @Override
            public Optional<VerifiedLeadIntake> authenticate(String apiKey) {
                authenticationCalls.incrementAndGet();
                return Optional.of(new VerifiedLeadIntake(key.keyId(), 9L, 7L));
            }
        };
        LeadIntakeAuthenticationFilter filter = filter(authenticationService,
                new LeadIntakeRateLimiter(1, 20, 10,
                        java.time.Duration.ofMinutes(2), java.time.Clock.systemUTC()));
        MockHttpServletRequest firstRequest = request(key.value());
        MockHttpServletResponse firstResponse = new MockHttpServletResponse();
        filter.doFilter(firstRequest, firstResponse, (ignoredRequest, ignoredResponse) -> { });
        assertEquals(200, firstResponse.getStatus());
        assertEquals(java.util.Set.of(AuthorityCatalog.LEADS_WRITE),
                SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                        .map(org.springframework.security.core.GrantedAuthority::getAuthority)
                        .collect(java.util.stream.Collectors.toSet()));

        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        filter.doFilter(request(key.value()), secondResponse, (ignoredRequest, ignoredResponse) -> {
            throw new AssertionError("Exhausted credentials must not proceed");
        });
        assertEquals(429, secondResponse.getStatus());
        assertEquals(1, authenticationCalls.get());
    }

    @Test
    void releasesSourceReservationWhenCredentialLookupThrows() {
        AtomicInteger authenticationCalls = new AtomicInteger();
        LeadIntakeAuthenticationService authenticationService = new LeadIntakeAuthenticationService(null, null) {
            @Override
            public Optional<VerifiedLeadIntake> authenticate(String apiKey) {
                authenticationCalls.incrementAndGet();
                throw new IllegalStateException("credential storage is unavailable");
            }
        };
        LeadIntakeAuthenticationFilter filter = filter(authenticationService,
                new LeadIntakeRateLimiter(60, 1, 10,
                        java.time.Duration.ofMinutes(2), java.time.Clock.systemUTC()));

        assertThrows(IllegalStateException.class, () -> filter.doFilter(
                request(), new MockHttpServletResponse(), (ignoredRequest, ignoredResponse) -> { }));
        assertThrows(IllegalStateException.class, () -> filter.doFilter(
                request(), new MockHttpServletResponse(), (ignoredRequest, ignoredResponse) -> { }));
        assertEquals(2, authenticationCalls.get());
    }

    private LeadIntakeAuthenticationFilter filter(
            LeadIntakeAuthenticationService authenticationService, LeadIntakeRateLimiter rateLimiter) {
        return new LeadIntakeAuthenticationFilter(authenticationService, rateLimiter,
                new LeadIntakeClientAddressResolver(""));
    }

    private void runFilter(
            LeadIntakeAuthenticationFilter filter, MockHttpServletRequest request,
            MockHttpServletResponse response, String unexpectedContinuationMessage) {
        try {
            filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
                throw new AssertionError(unexpectedContinuationMessage);
            });
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private MockHttpServletRequest request() {
        return request(LeadIntakeApiKey.generate(new TenantId(9L)).value());
    }

    private MockHttpServletRequest request(String apiKey) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/open/leads");
        request.setRemoteAddr("203.0.113.10");
        request.addHeader(LeadIntakeAuthenticationFilter.API_KEY_HEADER, apiKey);
        return request;
    }
}
