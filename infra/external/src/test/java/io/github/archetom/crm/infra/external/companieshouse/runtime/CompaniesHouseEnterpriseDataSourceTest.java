package io.github.archetom.crm.infra.external.companieshouse.runtime;

import io.github.archetom.crm.domain.model.IdentityScope;
import io.github.archetom.crm.domain.model.RegistryScheme;
import io.github.archetom.crm.domain.valueobject.RegisteredIdentifierKey;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.Duration;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompaniesHouseEnterpriseDataSourceTest {
    private static final TenantId TENANT = new TenantId(23L);

    @Test
    void mapsOnlyTheAllowlistedCompanyFieldsFromAnExactRuntimeResponse() {
        CompaniesHouseEnterpriseDataProperties properties = validProperties();
        CompaniesHouseHttpTransport transport = (uri, apiKey, timeout) -> {
            assertEquals("https://api.company-information.service.gov.uk/company/SC000001", uri.toString());
            assertEquals("synthetic-only-api-key", apiKey);
            assertEquals(Duration.ofSeconds(3), timeout);
            return new CompaniesHouseHttpTransport.Response(200, """
                    {"company_number":"SC000001","company_name":"Synthetic Registry Labs Ltd",
                     "company_status":"active","date_of_creation":"2020-01-02",
                     "registered_office_address":{"premises":"1","address_line_1":"Contract Test Way",
                     "locality":"Fixture City","postal_code":"ZZ1 1ZZ"}}
                    """.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        };
        CompaniesHouseEnterpriseDataSource source = new CompaniesHouseEnterpriseDataSource(properties, transport,
                new ObjectMapper());

        var capability = source.capabilities(TENANT);
        var observation = source.findByRegisteredIdentifier(TENANT, new RegisteredIdentifierKey(
                IdentityScope.UK_COMPANIES_HOUSE, RegistryScheme.GB_CH_NUMBER, "SC000001")).orElseThrow();

        assertTrue(capability.ready());
        assertEquals("SC000001", observation.primaryIdentifier().getCanonicalValue());
        assertEquals("CURRENT", observation.primaryIdentifier().getIdentifierStanding().name());
        assertEquals("OFFICIAL_REGISTRY", observation.sourceKind().name());
        assertEquals("1, Contract Test Way", observation.registeredAddress().addressLine());
        assertEquals(1, observation.identifiers().size());
    }

    @Test
    void missingKeyOrAnyRequiredReviewLeavesTheAdapterUnavailable() {
        CompaniesHouseEnterpriseDataProperties properties = validProperties();
        properties.setApiKey(null);
        CompaniesHouseEnterpriseDataSource source = new CompaniesHouseEnterpriseDataSource(properties,
                (uri, key, timeout) -> { throw new AssertionError("unready adapter made an HTTP call"); },
                new ObjectMapper());

        assertFalse(source.capabilities(TENANT).ready());
    }

    private CompaniesHouseEnterpriseDataProperties validProperties() {
        CompaniesHouseEnterpriseDataProperties properties = new CompaniesHouseEnterpriseDataProperties();
        properties.setEnabled(true);
        properties.setApiBaseUri(URI.create("https://api.company-information.service.gov.uk/"));
        properties.setApiKey("synthetic-only-api-key");
        properties.setRequestTimeout(Duration.ofSeconds(3));
        properties.setMaxCandidates(5);
        properties.setMaximumObservationTtl(Duration.ofMinutes(5));
        properties.setPolicyVersion(CompaniesHouseSourcePolicyCatalog.VERSION);
        properties.setPolicyDigest(CompaniesHouseSourcePolicyCatalog.DIGEST);
        properties.setDataRightsApprovalRef("rights-v1");
        properties.setPrivacyReviewRef("privacy-v1");
        properties.setCopyrightReviewRef("copyright-v1");
        properties.setTenantIds(Set.of(TENANT.getValue()));
        return properties;
    }
}
