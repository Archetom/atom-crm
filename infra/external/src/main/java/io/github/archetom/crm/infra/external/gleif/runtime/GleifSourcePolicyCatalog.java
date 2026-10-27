package io.github.archetom.crm.infra.external.gleif.runtime;

import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataAttribution;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataReadPolicy;
import io.github.archetom.crm.domain.model.EnterpriseDataAttributionContext;
import io.github.archetom.crm.domain.model.EnterpriseDataSourceKind;
import io.github.archetom.crm.domain.model.EnterpriseDataUsageMode;
import io.github.archetom.crm.domain.valueobject.LegalJurisdiction;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

/** Versioned data-rights, attribution, TTL, jurisdiction, retention and purpose policy. */
final class GleifSourcePolicyCatalog {
    static final String VERSION = "GLEIF-LEVEL1-B23-V1";
    static final String DIGEST = digest("version=" + VERSION
            + "|source=OPEN_REFERENCE_INDEX|purpose=CUSTOMER_RESEARCH"
            + "|retention=EPHEMERAL_ONLY|max_ttl=PT5M|attribution=GLEIF");
    private static final URI API = URI.create("https://api.gleif.org/api/v1/");
    private static final Pattern APPROVAL_REF = Pattern.compile("[A-Za-z0-9._:-]{8,80}");

    private GleifSourcePolicyCatalog() { }

    static Resolved resolve(GleifEnterpriseDataProperties properties, GleifSharedQuota quota,
                            TenantId tenantId) {
        if (properties == null || tenantId == null || quota == null || !quota.ready()
                || !properties.isEnabled() || !properties.getTenantIds().contains(tenantId.getValue())
                || !matches(properties.getPolicyVersion(), VERSION)
                || !matches(properties.getPolicyDigest(), DIGEST)
                || !matches(properties.getRaCatalogVersion(), GleifRegistrationAuthorityCatalog.VERSION)
                || !matches(properties.getRaCatalogDigest(), GleifRegistrationAuthorityCatalog.DIGEST)
                || properties.getDataRightsApprovalRef() == null
                || !APPROVAL_REF.matcher(properties.getDataRightsApprovalRef()).matches()
                || !API.equals(properties.getApiBaseUri())
                || invalid(properties.getRequestTimeout())
                || invalidTtl(properties.getMaximumObservationTtl())
                || properties.getMaxCandidates() == null || properties.getMaxCandidates() < 1
                || properties.getMaxCandidates() > 20
                || properties.getSharedQuota() == null
                || properties.getSharedQuota().getMaximumRequests() == null
                || properties.getSharedQuota().getMaximumRequests() < 1
                || invalid(properties.getSharedQuota().getWindow())) {
            return Resolved.unavailable();
        }
        Set<LegalJurisdiction> jurisdictions = jurisdictions(properties.getLegalJurisdictions());
        if (jurisdictions.isEmpty()) return Resolved.unavailable();
        EnterpriseDataReadPolicy policy = new EnterpriseDataReadPolicy(true,
                EnterpriseDataSourceKind.OPEN_REFERENCE_INDEX,
                // The published B23 policy permits display only. Adoption remains unavailable
                // until a separately approved source-rights policy version is introduced.
                EnterpriseDataUsageMode.EPHEMERAL_ONLY,
                properties.getMaximumObservationTtl(),
                attribution(EnterpriseDataAttributionContext.SEARCH_RESULT),
                attribution(EnterpriseDataAttributionContext.DETAIL));
        return new Resolved(policy, jurisdictions);
    }

    private static EnterpriseDataAttribution attribution(EnterpriseDataAttributionContext context) {
        return new EnterpriseDataAttribution(context, "Global Legal Entity Identifier Foundation",
                "https://www.gleif.org/en/lei-data/gleif-lei-data", "LEI reference data; verify before use.");
    }
    private static Set<LegalJurisdiction> jurisdictions(Set<String> supplied) {
        if (supplied == null || supplied.isEmpty() || supplied.size() > 100) return Set.of();
        try {
            Set<LegalJurisdiction> values = new LinkedHashSet<>();
            for (String value : supplied) values.add(new LegalJurisdiction(value));
            return Set.copyOf(values);
        } catch (RuntimeException exception) {
            return Set.of();
        }
    }
    private static boolean invalid(Duration value) { return value == null || value.isZero() || value.isNegative(); }
    private static boolean invalidTtl(Duration value) {
        return invalid(value) || value.compareTo(EnterpriseDataReadPolicy.CORE_MAX_OBSERVATION_TTL) > 0;
    }
    private static boolean matches(String actual, String expected) {
        return actual != null && MessageDigest.isEqual(actual.getBytes(StandardCharsets.US_ASCII), expected.getBytes(StandardCharsets.US_ASCII));
    }
    private static String digest(String value) {
        try { return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception exception) { throw new IllegalStateException("GLEIF policy digest is unavailable", exception); }
    }

    record Resolved(EnterpriseDataReadPolicy policy, Set<LegalJurisdiction> jurisdictions) {
        static Resolved unavailable() { return new Resolved(EnterpriseDataReadPolicy.unavailable(), Set.of()); }
        boolean ready() { return policy.ready() && !jurisdictions.isEmpty(); }
    }
}
