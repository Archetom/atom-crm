package io.github.archetom.crm.infra.rest.security;

import io.github.archetom.crm.application.security.MarketingFormPublicId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketingFormSubmissionRateLimiterTest {

    @Test
    void enforcesBothFormAndResolvedSourceQuotas() {
        MarketingFormSubmissionRateLimiter formLimited = limiter(2, 10, new MutableClock());
        assertTrue(formLimited.tryAcquire("form-a", "198.51.100.1"));
        assertTrue(formLimited.tryAcquire("form-a", "198.51.100.2"));
        assertFalse(formLimited.tryAcquire("form-a", "198.51.100.3"));
        assertTrue(formLimited.tryAcquire("form-b", "198.51.100.3"));

        MarketingFormSubmissionRateLimiter sourceLimited = limiter(10, 2, new MutableClock());
        assertTrue(sourceLimited.tryAcquire("form-a", "198.51.100.1"));
        assertTrue(sourceLimited.tryAcquire("form-b", "198.51.100.1"));
        assertFalse(sourceLimited.tryAcquire("form-c", "198.51.100.1"));
    }

    @Test
    void fullBucketCachesFailClosedUntilEntriesExpire() {
        MutableClock clock = new MutableClock();
        MarketingFormSubmissionRateLimiter limiter = new MarketingFormSubmissionRateLimiter(
                10, 10, 1, 1, Duration.ofMinutes(2), clock);
        assertTrue(limiter.tryAcquire("form-a", "198.51.100.1"));
        assertFalse(limiter.tryAcquire("form-b", "198.51.100.2"));

        clock.advance(Duration.ofMinutes(2));
        assertTrue(limiter.tryAcquire("form-b", "198.51.100.2"));
    }

    @Test
    void directAliasAndPercentEncodedVariantsShareOneAdmissionBucket() throws Exception {
        MarketingFormSubmissionRateLimitFilter filter =
                new MarketingFormSubmissionRateLimitFilter(
                        limiter(2, 10, new MutableClock()),
                        new LeadIntakeClientAddressResolver(""));
        String publicId = publicId();
        String encodedPublicId = "%41" + publicId.substring(1)
                .replace(".", "%2e");
        AtomicInteger continuations = new AtomicInteger();

        MockHttpServletResponse firstResponse = new MockHttpServletResponse();
        filter.doFilter(request("/api/v1/open/marketing-forms/" + publicId), firstResponse,
                (ignoredRequest, ignoredResponse) -> continuations.incrementAndGet());
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        filter.doFilter(request("/api/v1/open/marketing-forms/" + publicId + "/submissions"),
                secondResponse,
                (ignoredRequest, ignoredResponse) -> continuations.incrementAndGet());
        MockHttpServletResponse encodedResponse = new MockHttpServletResponse();
        filter.doFilter(request("/api/v1/open/%6Darketing-forms/" + encodedPublicId
                        + "/%73ubmissions"),
                encodedResponse,
                (ignoredRequest, ignoredResponse) -> continuations.incrementAndGet());

        assertEquals(2, continuations.get());
        assertEquals(200, firstResponse.getStatus());
        assertEquals(200, secondResponse.getStatus());
        assertEquals(429, encodedResponse.getStatus());
    }

    @Test
    void rejectsMalformedOrNonCanonicalPublicPathsBeforeRequestHandling() throws Exception {
        MarketingFormSubmissionRateLimitFilter filter =
                new MarketingFormSubmissionRateLimitFilter(
                        limiter(10, 10, new MutableClock()),
                        new LeadIntakeClientAddressResolver(""));
        AtomicInteger continuations = new AtomicInteger();

        MockHttpServletResponse malformedResponse = new MockHttpServletResponse();
        filter.doFilter(request("/api/v1/open/marketing-forms/%ZZ"), malformedResponse,
                (ignoredRequest, ignoredResponse) -> continuations.incrementAndGet());
        MockHttpServletResponse encodedSlashResponse = new MockHttpServletResponse();
        filter.doFilter(request("/api/v1/open/marketing-forms/" + publicId() + "%2Fextra"),
                encodedSlashResponse,
                (ignoredRequest, ignoredResponse) -> continuations.incrementAndGet());

        assertEquals(0, continuations.get());
        assertEquals(404, malformedResponse.getStatus());
        assertEquals(404, encodedSlashResponse.getStatus());
    }

    @Test
    void supportsAnApplicationContextPath() throws Exception {
        MarketingFormSubmissionRateLimitFilter filter =
                new MarketingFormSubmissionRateLimitFilter(
                        limiter(1, 10, new MutableClock()),
                        new LeadIntakeClientAddressResolver(""));
        MockHttpServletRequest request = request(
                "/crm/api/v1/open/marketing-forms/" + publicId() + "/submissions");
        request.setContextPath("/crm");
        AtomicInteger continuations = new AtomicInteger();

        filter.doFilter(request, new MockHttpServletResponse(),
                (ignoredRequest, ignoredResponse) -> continuations.incrementAndGet());

        assertEquals(1, continuations.get());
    }

    @Test
    void publicFileRoutesUseTheirOwnSharedAdmissionBudget() throws Exception {
        MutableClock clock = new MutableClock();
        MarketingFormSubmissionRateLimitFilter filter = new MarketingFormSubmissionRateLimitFilter(
                new MarketingFormSubmissionRateLimiter(1, 10, 2, 10, 10, 10,
                        Duration.ofMinutes(2), clock), new LeadIntakeClientAddressResolver(""));
        String base = "/api/v1/open/marketing-forms/" + publicId() + "/file-drafts";
        AtomicInteger continuations = new AtomicInteger();

        MockHttpServletResponse submission = new MockHttpServletResponse();
        filter.doFilter(request("/api/v1/open/marketing-forms/" + publicId()), submission,
                (ignoredRequest, ignoredResponse) -> continuations.incrementAndGet());
        MockHttpServletResponse draft = new MockHttpServletResponse();
        filter.doFilter(request(base), draft,
                (ignoredRequest, ignoredResponse) -> continuations.incrementAndGet());
        MockHttpServletResponse uploadIntent = new MockHttpServletResponse();
        filter.doFilter(request(base + "/7/fields/resume/upload-intents"), uploadIntent,
                (ignoredRequest, ignoredResponse) -> continuations.incrementAndGet());
        MockHttpServletResponse content = new MockHttpServletResponse();
        filter.doFilter(request("PUT", base + "/7/fields/resume/files/9/content"), content,
                (ignoredRequest, ignoredResponse) -> continuations.incrementAndGet());

        assertEquals(3, continuations.get());
        assertEquals(200, submission.getStatus());
        assertEquals(200, draft.getStatus());
        assertEquals(200, uploadIntent.getStatus());
        assertEquals(429, content.getStatus());
    }

    @Test
    void invalidPublicFileLimitsCloseOnlyNewAdmissionAndKeepExistingDraftRoutesRateLimited() {
        MarketingFormSubmissionRateLimiter limiter = new MarketingFormSubmissionRateLimiter(
                2, 2, 0, 0, 10, 10, Duration.ofMinutes(2), new MutableClock());

        assertTrue(limiter.tryAcquire("form-a", "198.51.100.1"));
        assertFalse(limiter.tryAcquire("form-a", "198.51.100.1",
                MarketingFormSubmissionRateLimiter.RouteKind.PUBLIC_FILE_ADMISSION));
        assertTrue(limiter.tryAcquire("form-a", "198.51.100.1",
                MarketingFormSubmissionRateLimiter.RouteKind.PUBLIC_FILE));
    }

    @Test
    void publicFileLimitsAboveThePublishedPropertyBoundsFailClosedToo() {
        MarketingFormSubmissionRateLimiter limiter = new MarketingFormSubmissionRateLimiter(
                2, 2, 601, 121, 10, 10, Duration.ofMinutes(2), new MutableClock());

        assertFalse(limiter.tryAcquire("form-a", "198.51.100.1",
                MarketingFormSubmissionRateLimiter.RouteKind.PUBLIC_FILE_ADMISSION));
        assertTrue(limiter.tryAcquire("form-a", "198.51.100.1",
                MarketingFormSubmissionRateLimiter.RouteKind.PUBLIC_FILE));
    }

    @Test
    void invalidPublicFileLimitsKeepCredentialedExistingDraftRoutesReachable() throws Exception {
        MarketingFormSubmissionRateLimitFilter filter = new MarketingFormSubmissionRateLimitFilter(
                new MarketingFormSubmissionRateLimiter(2, 2, 0, 0, 10, 10,
                        Duration.ofMinutes(2), new MutableClock()), new LeadIntakeClientAddressResolver(""));
        String base = "/api/v1/open/marketing-forms/" + publicId() + "/file-drafts/7";
        AtomicInteger continuations = new AtomicInteger();

        MockHttpServletResponse admission = new MockHttpServletResponse();
        filter.doFilter(request("POST", base.substring(0, base.lastIndexOf('/'))), admission,
                (ignoredRequest, ignoredResponse) -> continuations.incrementAndGet());
        MockHttpServletResponse existingUpload = new MockHttpServletResponse();
        filter.doFilter(request("PUT", base + "/fields/resume/files/9/content"), existingUpload,
                (ignoredRequest, ignoredResponse) -> continuations.incrementAndGet());

        assertEquals(429, admission.getStatus());
        assertEquals(200, existingUpload.getStatus());
        assertEquals(1, continuations.get());
    }

    @Test
    void publicFileBucketsCannotExhaustScalarSubmissionBuckets() {
        MarketingFormSubmissionRateLimiter limiter = new MarketingFormSubmissionRateLimiter(
                10, 10, 10, 10, 1, 1, Duration.ofMinutes(2), new MutableClock());

        assertTrue(limiter.tryAcquire("public-file-form", "198.51.100.1",
                MarketingFormSubmissionRateLimiter.RouteKind.PUBLIC_FILE_ADMISSION));
        assertTrue(limiter.tryAcquire("scalar-form", "198.51.100.2",
                MarketingFormSubmissionRateLimiter.RouteKind.SUBMISSION));
    }

    private MarketingFormSubmissionRateLimiter limiter(
            int formLimit, int sourceLimit, Clock clock) {
        return new MarketingFormSubmissionRateLimiter(
                formLimit, sourceLimit, 10, 10, Duration.ofMinutes(2), clock);
    }

    private MockHttpServletRequest request(String uri) {
        return request("POST", uri);
    }

    private MockHttpServletRequest request(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRemoteAddr("198.51.100.1");
        return request;
    }

    private String publicId() {
        return MarketingFormPublicId.value(
                new TenantId(1L), "abcdefghijklmnop");
    }

    private static final class MutableClock extends Clock {
        private Instant instant = Instant.parse("2026-07-18T00:00:00Z");

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
