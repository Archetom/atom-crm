package io.github.archetom.crm;

import io.github.archetom.crm.api.dto.request.EnterpriseIdentifierLookupRequest;
import io.github.archetom.crm.api.dto.request.EnterpriseNameSearchRequest;
import io.github.archetom.crm.api.dto.request.OrganizationBootstrapRequest;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end B23a coverage using only the deterministic synthetic adapter. */
@RequiresDatabase
class EnterpriseDataIntegrationTest extends BaseIntegrationTest {

    private static final long OWNER_ACTOR_ID = 888888L;
    private static final long TENANT_ID = 999999L;
    private static final long SECOND_OWNER_ACTOR_ID = 888889L;
    private static final long SECOND_TENANT_ID = 999998L;

    @Test
    void enterpriseQueriesRequireTenantCapabilityAndReturnOnlyTheCanonicalAllowlist()
            throws Exception {
        mockMvc.perform(get("/api/v1/enterprise-data/capabilities"))
                .andExpect(status().isUnauthorized());
        performGet("/api/v1/enterprise-data/capabilities")
                .andExpect(status().isForbidden());
        bootstrap();

        performGet("/api/v1/enterprise-data/capabilities")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ready").value(true))
                .andExpect(jsonPath("$.maxCandidates").value(5))
                .andExpect(jsonPath("$.usageMode").value("EPHEMERAL_ONLY"))
                .andExpect(jsonPath("$.identifierTypes.length()").value(2));

        performPost("/api/v1/enterprise-data/search", new EnterpriseNameSearchRequest()
                .setRegisteredName("Synthetic Registry")
                .setLegalJurisdiction("GB"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].registeredName")
                        .value("Synthetic Registry Labs Ltd"))
                .andExpect(jsonPath("$[0].primaryIdentifier.identityScope")
                        .value("GLOBAL_LEI_SYSTEM"))
                .andExpect(jsonPath("$[0].primaryIdentifier.canonicalValue")
                        .value("0000SYNTHETICFIXTU42"))
                .andExpect(jsonPath("$[0].sourceKind").value("SYNTHETIC_FIXTURE"))
                .andExpect(jsonPath("$[0].sourceDeclaredPartial").value(false))
                .andExpect(jsonPath("$[0].formationDateKind").value("ESTABLISHED"))
                .andExpect(jsonPath("$[0].attribution.context")
                        .value("SEARCH_RESULT"))
                .andExpect(jsonPath("$[0].attribution.sourceName")
                        .value("Synthetic registry fixture"))
                .andExpect(jsonPath("$[0].usageMode").value("EPHEMERAL_ONLY"))
                .andExpect(jsonPath("$[0].organizationType").doesNotExist())
                .andExpect(jsonPath("$[0].sourceAttribution").doesNotExist())
                .andExpect(jsonPath("$[0].provider").doesNotExist())
                .andExpect(jsonPath("$[0].rawResponse").doesNotExist());

        performPost("/api/v1/enterprise-data/lookup",
                new EnterpriseIdentifierLookupRequest()
                        .setIdentityScope("global_lei_system")
                        .setRegistryScheme("lei")
                        .setValue("0000syntheticfixtu42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primaryIdentifier.canonicalValue")
                        .value("0000SYNTHETICFIXTU42"))
                .andExpect(jsonPath("$.attribution.context").value("DETAIL"))
                .andExpect(jsonPath("$.usageMode").value("EPHEMERAL_ONLY"));
    }

    @Test
    void malformedOrUnsupportedSelectorsFailBeforeSourceUse() throws Exception {
        bootstrap();

        performPost("/api/v1/enterprise-data/search", new EnterpriseNameSearchRequest()
                .setRegisteredName(" ")
                .setLegalJurisdiction("GB"))
                .andExpect(status().isBadRequest());
        performPost("/api/v1/enterprise-data/lookup",
                new EnterpriseIdentifierLookupRequest()
                        .setIdentityScope("GLOBAL_LEI_SYSTEM")
                        .setRegistryScheme("LEI")
                        .setValue("0000SYNTHETICFIXTU41"))
                .andExpect(status().isBadRequest());
        performPost("/api/v1/enterprise-data/lookup",
                new EnterpriseIdentifierLookupRequest()
                        .setIdentityScope("UNKNOWN_SCOPE")
                        .setRegistryScheme("LEI")
                        .setValue("0000SYNTHETICFIXTU42"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(withActor(post("/api/v1/enterprise-data/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"registeredName":"Synthetic Registry","legalJurisdiction":"GB",
                                 "provider":"caller-selected"}
                                """)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tenantBoundQueriesCannotCrossMembershipAndNeverPersistDatabaseRows()
            throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Enterprise Data Owner A");
        bootstrap(SECOND_OWNER_ACTOR_ID, SECOND_TENANT_ID,
                "Enterprise Data Owner B");
        Map<String, Long> before = databaseRowCounts();

        mockMvc.perform(withActor(get("/api/v1/enterprise-data/capabilities"),
                        OWNER_ACTOR_ID, TENANT_ID))
                .andExpect(status().isOk());
        mockMvc.perform(withActor(post("/api/v1/enterprise-data/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new EnterpriseNameSearchRequest()
                                .setRegisteredName("Synthetic Registry")
                                .setLegalJurisdiction("GB"))),
                        OWNER_ACTOR_ID, TENANT_ID))
                .andExpect(status().isOk());
        mockMvc.perform(withActor(post("/api/v1/enterprise-data/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new EnterpriseIdentifierLookupRequest()
                                .setIdentityScope("CN_UNIFIED_SOCIAL_CREDIT_CODE")
                                .setRegistryScheme("CN_USCC")
                                .setValue("91110000F1XTURE1AN"))),
                        SECOND_OWNER_ACTOR_ID, SECOND_TENANT_ID))
                .andExpect(status().isOk());

        mockMvc.perform(withActor(get("/api/v1/enterprise-data/capabilities"),
                        OWNER_ACTOR_ID, SECOND_TENANT_ID))
                .andExpect(status().isForbidden());
        mockMvc.perform(withActor(post("/api/v1/enterprise-data/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new EnterpriseNameSearchRequest()
                                .setRegisteredName("Synthetic Registry")
                                .setLegalJurisdiction("GB"))),
                        SECOND_OWNER_ACTOR_ID, TENANT_ID))
                .andExpect(status().isForbidden());

        assertEquals(before, databaseRowCounts());
        // B23 has durable provider-quota and Customer-adoption controls, but ordinary
        // EPHEMERAL_ONLY discovery must leave every one of them untouched.
        assertEquals(List.of("crm_customer_registered_identity",
                        "crm_enterprise_data_adoption",
                        "crm_enterprise_data_adoption_retention_work",
                        "crm_enterprise_data_adoption_selection",
                        "crm_enterprise_data_source_quota"),
                enterpriseDataPersistenceTables());
    }

    @Test
    void publicErrorsUseStableCodesStatusesAndSafeMessages() throws Exception {
        bootstrap();

        performPost("/api/v1/enterprise-data/lookup",
                new EnterpriseIdentifierLookupRequest()
                        .setIdentityScope("GLOBAL_LEI_SYSTEM")
                        .setRegistryScheme("LEI")
                        .setValue("0000ACCEPTANCEFIXT55"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errCode").value(ApplicationErrorCode
                        .ENTERPRISE_DATA_NO_RESULT.getCompleteCode(
                                UseCaseOperation.ENTERPRISE_DATA_IDENTIFIER_LOOKUP.getCode())))
                .andExpect(jsonPath("$.errMsg").value(
                        ApplicationErrorCode.ENTERPRISE_DATA_NO_RESULT.getDescription()));

        performPost("/api/v1/enterprise-data/search", new EnterpriseNameSearchRequest()
                .setRegisteredName("Synthetic Registry")
                .setLegalJurisdiction("US"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errCode").value(ApplicationErrorCode
                        .ENTERPRISE_DATA_UNSUPPORTED.getCompleteCode(
                                UseCaseOperation.ENTERPRISE_DATA_NAME_SEARCH.getCode())))
                .andExpect(jsonPath("$.errMsg").value(
                        ApplicationErrorCode.ENTERPRISE_DATA_UNSUPPORTED.getDescription()));
    }

    private void bootstrap() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Enterprise Data Owner");
    }

    private void bootstrap(
            long actorId, long tenantId, String displayName) throws Exception {
        mockMvc.perform(withActor(post("/api/v1/organization/bootstrap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new OrganizationBootstrapRequest()
                                .setDisplayName(displayName))), actorId, tenantId))
                .andExpect(status().isOk());
    }

    private MockHttpServletRequestBuilder withActor(MockHttpServletRequestBuilder request) {
        return withActor(request, OWNER_ACTOR_ID, TENANT_ID);
    }

    private MockHttpServletRequestBuilder withActor(
            MockHttpServletRequestBuilder request, long actorId, long tenantId) {
        return request.header("X-Dev-User-Id", actorId)
                .header("X-Dev-Tenant-Id", tenantId);
    }

    private Map<String, Long> databaseRowCounts() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        List<String> tables = jdbc.queryForList("""
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'
                ORDER BY table_name
                """, String.class);
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String table : tables) {
            counts.put(table, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM `" + table + "`", Long.class));
        }
        return counts;
    }

    private List<String> enterpriseDataPersistenceTables() {
        return new JdbcTemplate(dataSource).queryForList("""
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND (LOWER(table_name) LIKE '%enterprise_data%'
                    OR LOWER(table_name) LIKE '%registered_identity%'
                    OR LOWER(table_name) LIKE '%adoption_provenance%')
                ORDER BY table_name
                """, String.class);
    }

    @Override
    protected void initTestData() {
        clearAuthorizationData();
    }

    @Override
    protected void clearTestData() {
        clearAuthorizationData();
    }

    private void clearAuthorizationData() {
        executeSql("DELETE FROM navigation_preference");
        executeSql("DELETE FROM authorization_role_assignment");
        executeSql("DELETE FROM authorization_role_grant");
        executeSql("DELETE FROM authorization_role");
        executeSql("DELETE FROM organization_membership_request");
        executeSql("DELETE FROM organization_membership");
        executeSql("DELETE FROM organization_department");
        executeSql("DELETE FROM organization_member_profile");
        executeSql("DELETE FROM organization_tenant");
    }
}
