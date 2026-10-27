package io.github.archetom.crm;

import io.github.archetom.crm.api.dto.request.MarketingFormCreateRequest;
import io.github.archetom.crm.api.dto.request.MarketingFormFieldRequest;
import io.github.archetom.crm.api.dto.request.MarketingFormStatusUpdateRequest;
import io.github.archetom.crm.api.dto.request.MarketingFormSubmissionRequest;
import io.github.archetom.crm.api.dto.request.OrganizationBootstrapRequest;
import io.github.archetom.crm.api.dto.response.MarketingFormResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Verifies that the composite B19a/B09 command does not require a nested connection. */
@RequiresDatabase
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = {Bootstrap.class},
        properties = {
                "spring.datasource.hikari.maximum-pool-size=1",
                "spring.datasource.hikari.connection-timeout=1000"
        })
class MarketingFormSingleConnectionIntegrationTest extends BaseIntegrationTest {

    private static final long ACTOR_ID = 919191L;
    private static final long TENANT_ID = 929292L;

    @Test
    void submissionCompletesWithOneDatabaseConnection() throws Exception {
        performPost("/api/v1/organization/bootstrap",
                new OrganizationBootstrapRequest().setDisplayName("Single connection owner"))
                .andExpect(status().isOk());
        MarketingFormResponse created = fromJson(performPost("/api/v1/marketing-forms",
                new MarketingFormCreateRequest()
                        .setName("Single connection form")
                        .setTitle("Single connection form")
                        .setFields(List.of(new MarketingFormFieldRequest()
                                .setFieldCode("lead_name").setLabel("Name")
                                .setRequired(true).setSortOrder(10))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                MarketingFormResponse.class);
        MarketingFormResponse active = fromJson(performPut(
                "/api/v1/marketing-forms/" + created.getId() + "/status",
                new MarketingFormStatusUpdateRequest().setVersion(created.getVersion())
                        .setStatus("ACTIVE"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                MarketingFormResponse.class);

        mockMvc.perform(post("/api/v1/open/marketing-forms/{publicId}", active.getPublicId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new MarketingFormSubmissionRequest()
                                .setIdempotencyKey("single-connection-lead")
                                .setValues(Map.of("lead_name", List.of("Single connection lead"))))))
                .andExpect(status().isOk());

        assertEquals(1, new JdbcTemplate(dataSource).queryForObject(
                "SELECT COUNT(*) FROM crm_lead WHERE tenant_id = ? AND name = ?",
                Integer.class, TENANT_ID, "Single connection lead"));
        assertEquals(1, new JdbcTemplate(dataSource).queryForObject(
                "SELECT COUNT(*) FROM crm_marketing_form_submission WHERE tenant_id = ?",
                Integer.class, TENANT_ID));
    }

    @Override
    protected org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
            withTestActor(
                    org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
                            request) {
        return request.header("X-Dev-User-Id", ACTOR_ID)
                .header("X-Dev-Tenant-Id", TENANT_ID);
    }

    @Override
    protected void initTestData() {
        clearData();
    }

    @Override
    protected void clearTestData() {
        clearData();
    }

    private void clearData() {
        executeSql("DELETE FROM crm_marketing_form_submission");
        executeSql("DELETE FROM crm_marketing_form_field");
        executeSql("DELETE FROM crm_marketing_form");
        executeSql("DELETE FROM crm_lead_unique_field_value");
        executeSql("DELETE FROM crm_lead_field_value");
        executeSql("DELETE FROM crm_lead_collaborator");
        executeSql("DELETE FROM crm_lead_tag");
        executeSql("DELETE FROM crm_lead_address");
        executeSql("UPDATE crm_lead SET parent_lead_id = NULL");
        executeSql("DELETE FROM crm_lead");
        executeSql("DELETE FROM crm_lead_guard");
        executeSql("DELETE FROM saved_view");
        executeSql("DELETE FROM role_field_access");
        executeSql("DELETE FROM role_field_policy");
        executeSql("DELETE FROM field_layout_preference");
        executeSql("DELETE FROM custom_field_definition");
        executeSql("DELETE FROM customization_object_guard");
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
