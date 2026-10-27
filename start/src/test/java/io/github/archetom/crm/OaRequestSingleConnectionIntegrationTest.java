package io.github.archetom.crm;

import io.github.archetom.crm.api.dto.request.ApprovalConditionRequest;
import io.github.archetom.crm.api.dto.request.ApprovalDefinitionRequest;
import io.github.archetom.crm.api.dto.request.ApprovalDefinitionStatusRequest;
import io.github.archetom.crm.api.dto.request.ApprovalStepRequest;
import io.github.archetom.crm.api.dto.request.OaFormCreateRequest;
import io.github.archetom.crm.api.dto.request.OaFormFieldRequest;
import io.github.archetom.crm.api.dto.request.OaFormStatusUpdateRequest;
import io.github.archetom.crm.api.dto.request.OaRequestSubmitRequest;
import io.github.archetom.crm.api.dto.request.OrganizationBootstrapRequest;
import io.github.archetom.crm.api.dto.response.ApprovalDefinitionResponse;
import io.github.archetom.crm.api.dto.response.OaFormResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Verifies that B20b request and B11 approval creation share one connection. */
@RequiresDatabase
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = {Bootstrap.class},
        properties = {
                "spring.datasource.hikari.maximum-pool-size=1",
                "spring.datasource.hikari.connection-timeout=1000"
        })
class OaRequestSingleConnectionIntegrationTest extends BaseIntegrationTest {

    private static final long ACTOR_ID = 939393L;
    private static final long TENANT_ID = 949494L;

    @Test
    void submissionCompletesWithOneDatabaseConnection() throws Exception {
        performPost("/api/v1/organization/bootstrap",
                new OrganizationBootstrapRequest().setDisplayName("Single connection OA owner"))
                .andExpect(status().isOk());
        ApprovalDefinitionRequest definitionRequest = new ApprovalDefinitionRequest()
                .setObjectType("OA_REQUEST").setName("Single connection OA approval")
                .setRepeatMode("ONCE").setManagerActorIds(List.of(ACTOR_ID))
                .setSteps(List.of(new ApprovalStepRequest()
                        .setCode("owner-review").setName("Owner review")
                        .setOrder(0).setType("MEMBER").setMode("ALL")
                        .setFallback("MANAGERS").setMemberActorIds(List.of(ACTOR_ID))
                        .setConditions(List.of(new ApprovalConditionRequest()
                                .setFieldCode("request_title").setOperator("EQ")
                                .setValues(List.of("Single connection request"))))));
        ApprovalDefinitionResponse approval = fromJson(performPost(
                        "/api/v1/approval-definitions", definitionRequest)
                        .andExpect(status().isOk()).andReturn()
                        .getResponse().getContentAsString(), ApprovalDefinitionResponse.class);
        approval = fromJson(performPut(
                        "/api/v1/approval-definitions/" + approval.id() + "/status",
                        new ApprovalDefinitionStatusRequest().setVersion(approval.version())
                                .setStatus("ACTIVE"))
                        .andExpect(status().isOk()).andReturn()
                        .getResponse().getContentAsString(), ApprovalDefinitionResponse.class);
        OaFormResponse form = fromJson(performPost("/api/v1/oa/forms",
                        new OaFormCreateRequest().setCode("single-connection")
                                .setName("Single connection form")
                                .setApprovalDefinitionId(approval.id())
                                .setFields(List.of(new OaFormFieldRequest()
                                        .setFieldCode("request_title").setRequired(true)
                                        .setSortOrder(10))))
                        .andExpect(status().isOk()).andReturn()
                        .getResponse().getContentAsString(), OaFormResponse.class);
        form = fromJson(performPut("/api/v1/oa/forms/" + form.getId() + "/status",
                        new OaFormStatusUpdateRequest().setVersion(form.getVersion())
                                .setStatus("ACTIVE"))
                        .andExpect(status().isOk()).andReturn()
                        .getResponse().getContentAsString(), OaFormResponse.class);

        performPost("/api/v1/oa/requests", new OaRequestSubmitRequest()
                .setFormId(form.getId()).setFormVersion(form.getVersion())
                .setIdempotencyKey("single-connection-request")
                .setValues(Map.of("request_title",
                        List.of("Single connection request"))))
                .andExpect(status().isOk());

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM oa_request WHERE tenant_id = ?",
                Integer.class, TENANT_ID));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM approval_instance WHERE tenant_id = ? "
                        + "AND object_type = 'OA_REQUEST'",
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
        executeSql("DELETE FROM oa_request_unique_field_value");
        executeSql("DELETE FROM oa_request_field_value");
        executeSql("DELETE FROM oa_request");
        executeSql("DELETE FROM approval_review_task");
        executeSql("DELETE FROM approval_instance_manager");
        executeSql("DELETE FROM approval_instance_watcher");
        executeSql("DELETE FROM approval_instance");
        executeSql("DELETE FROM oa_form_field");
        executeSql("DELETE FROM oa_form_definition");
        executeSql("DELETE FROM approval_definition");
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
