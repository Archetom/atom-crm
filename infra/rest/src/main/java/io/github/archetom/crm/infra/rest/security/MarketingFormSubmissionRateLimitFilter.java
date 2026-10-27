package io.github.archetom.crm.infra.rest.security;

import io.github.archetom.crm.application.security.MarketingFormPublicId;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/** Applies anonymous admission control before marketing-form submissions reach the application. */
@Component
@RequiredArgsConstructor
public class MarketingFormSubmissionRateLimitFilter extends OncePerRequestFilter {

    private static final String PATH_PREFIX = "/api/v1/open/marketing-forms/";
    private static final String SUBMISSION_SUFFIX = "/submissions";
    private static final String FILE_DRAFT_SEGMENT = "/file-drafts";

    private final MarketingFormSubmissionRateLimiter rateLimiter;
    private final LeadIntakeClientAddressResolver clientAddressResolver;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return routeKind(request) == null;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Optional<String> publicId = canonicalPublicId(request);
        if (publicId.isEmpty()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String sourceAddress = clientAddressResolver.resolve(request);
        if (!rateLimiter.tryAcquire(publicId.get(), sourceAddress, routeKind(request))) {
            response.sendError(429);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private Optional<String> canonicalPublicId(HttpServletRequest request) {
        Optional<String> path = decodedApplicationPath(request);
        if (path.isEmpty() || !path.get().startsWith(PATH_PREFIX)) {
            return Optional.empty();
        }
        String suffix = path.get().substring(PATH_PREFIX.length());
        int separator = suffix.indexOf('/');
        String publicId = separator < 0 ? suffix : suffix.substring(0, separator);
        if (suffix.endsWith(SUBMISSION_SUFFIX)) {
            publicId = suffix.substring(0, suffix.length() - SUBMISSION_SUFFIX.length());
        }
        if (publicId.isBlank() || separator >= 0 && !suffix.endsWith(SUBMISSION_SUFFIX)
                && !suffix.substring(separator).startsWith(FILE_DRAFT_SEGMENT)) {
            return Optional.empty();
        }
        return MarketingFormPublicId.parse(publicId)
                .map(parts -> MarketingFormPublicId.value(
                        parts.tenantId(), parts.publicKey()))
                .filter(publicId::equals);
    }

    private MarketingFormSubmissionRateLimiter.RouteKind routeKind(HttpServletRequest request) {
        Optional<String> path = decodedApplicationPath(request);
        if (path.isEmpty()) {
            return HttpMethod.POST.matches(request.getMethod())
                    && applicationPath(request).filter(value -> value.startsWith(PATH_PREFIX)).isPresent()
                    ? MarketingFormSubmissionRateLimiter.RouteKind.SUBMISSION : null;
        }
        if (!path.get().startsWith(PATH_PREFIX)) return null;
        String suffix = path.get().substring(PATH_PREFIX.length());
        if (suffix.contains(FILE_DRAFT_SEGMENT)) {
            // POST creates a draft or a candidate. Content upload, status, and withdraw must
            // retain a bounded fallback even if the deployer later makes FILE admission invalid.
            return HttpMethod.POST.matches(request.getMethod())
                    ? MarketingFormSubmissionRateLimiter.RouteKind.PUBLIC_FILE_ADMISSION
                    : MarketingFormSubmissionRateLimiter.RouteKind.PUBLIC_FILE;
        }
        if (HttpMethod.POST.matches(request.getMethod())) {
            return MarketingFormSubmissionRateLimiter.RouteKind.SUBMISSION;
        }
        return null;
    }

    private Optional<String> decodedApplicationPath(HttpServletRequest request) {
        return decode(applicationPath(request));
    }

    private Optional<String> decode(Optional<String> value) {
        if (value.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UriUtils.decode(value.get(), StandardCharsets.UTF_8));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private Optional<String> applicationPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (uri == null) {
            return Optional.empty();
        }
        if (contextPath != null && !contextPath.isEmpty()) {
            if (!uri.startsWith(contextPath)) {
                return Optional.empty();
            }
            uri = uri.substring(contextPath.length());
        }
        return Optional.of(uri);
    }
}
