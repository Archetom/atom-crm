package io.github.archetom.crm.infra.external.companieshouse.runtime;

import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataAttribution;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataReadPolicy;
import io.github.archetom.crm.domain.model.EnterpriseDataAttributionContext;
import io.github.archetom.crm.domain.model.EnterpriseDataSourceKind;
import io.github.archetom.crm.domain.model.EnterpriseDataUsageMode;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.regex.Pattern;

/** Versioned Companies House rights, attribution, TTL, region and purpose policy. */
final class CompaniesHouseSourcePolicyCatalog {
    static final String VERSION = "COMPANIES-HOUSE-B23-V1";
    static final String DIGEST = digest("version=" + VERSION
            + "|source=OFFICIAL_REGISTRY|purpose=CUSTOMER_RESEARCH"
            + "|retention=EPHEMERAL_ONLY|max_ttl=PT5M|region=GB|attribution=COMPANIES_HOUSE");
    private static final URI API = URI.create("https://api.company-information.service.gov.uk/");
    private static final Pattern APPROVAL_REF = Pattern.compile("[A-Za-z0-9._:-]{8,80}");

    private CompaniesHouseSourcePolicyCatalog() { }

    static EnterpriseDataReadPolicy resolve(CompaniesHouseEnterpriseDataProperties properties, TenantId tenantId) {
        if (properties == null || tenantId == null || !properties.isEnabled()
                || !properties.getTenantIds().contains(tenantId.getValue())
                || !matches(properties.getPolicyVersion(), VERSION)
                || !matches(properties.getPolicyDigest(), DIGEST)
                || blank(properties.getApiKey()) || !API.equals(properties.getApiBaseUri())
                || invalid(properties.getRequestTimeout()) || invalidTtl(properties.getMaximumObservationTtl())
                || properties.getMaxCandidates() == null || properties.getMaxCandidates() < 1
                || properties.getMaxCandidates() > 20
                || !approval(properties.getDataRightsApprovalRef())
                || !approval(properties.getPrivacyReviewRef())
                || !approval(properties.getCopyrightReviewRef())) {
            return EnterpriseDataReadPolicy.unavailable();
        }
        return new EnterpriseDataReadPolicy(true, EnterpriseDataSourceKind.OFFICIAL_REGISTRY,
                EnterpriseDataUsageMode.EPHEMERAL_ONLY, properties.getMaximumObservationTtl(),
                attribution(EnterpriseDataAttributionContext.SEARCH_RESULT),
                attribution(EnterpriseDataAttributionContext.DETAIL));
    }

    private static EnterpriseDataAttribution attribution(EnterpriseDataAttributionContext context) {
        return new EnterpriseDataAttribution(context, "Companies House",
                "https://www.gov.uk/government/organisations/companies-house",
                "UK company information; verify before use.");
    }
    private static boolean approval(String value) { return value != null && APPROVAL_REF.matcher(value).matches(); }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static boolean invalid(Duration value) { return value == null || value.isZero() || value.isNegative(); }
    private static boolean invalidTtl(Duration value) {
        return invalid(value) || value.compareTo(EnterpriseDataReadPolicy.CORE_MAX_OBSERVATION_TTL) > 0;
    }
    private static boolean matches(String actual, String expected) {
        return actual != null && MessageDigest.isEqual(actual.getBytes(StandardCharsets.US_ASCII),
                expected.getBytes(StandardCharsets.US_ASCII));
    }
    private static String digest(String value) {
        try { return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception exception) { throw new IllegalStateException("Companies House policy digest is unavailable", exception); }
    }
}
