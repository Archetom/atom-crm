package io.github.archetom.crm;

import io.github.archetom.crm.api.dto.request.ApprovalActionRequest;
import io.github.archetom.crm.api.dto.request.ApprovalConditionRequest;
import io.github.archetom.crm.api.dto.request.ApprovalDefinitionRequest;
import io.github.archetom.crm.api.dto.request.ApprovalDefinitionStatusRequest;
import io.github.archetom.crm.api.dto.request.ApprovalQueryRequest;
import io.github.archetom.crm.api.dto.request.ApprovalStepRequest;
import io.github.archetom.crm.api.dto.request.OaFormCreateRequest;
import io.github.archetom.crm.api.dto.request.OaFormFieldRequest;
import io.github.archetom.crm.api.dto.request.OaFormStatusUpdateRequest;
import io.github.archetom.crm.api.dto.request.OaRequestPreviewRequest;
import io.github.archetom.crm.api.dto.request.OaRequestSubmitRequest;
import io.github.archetom.crm.api.dto.request.OrganizationBootstrapRequest;
import io.github.archetom.crm.api.dto.request.RoleAssignmentCreateRequest;
import io.github.archetom.crm.api.dto.request.RoleCreateRequest;
import io.github.archetom.crm.api.dto.request.RoleFieldAccessRequest;
import io.github.archetom.crm.api.dto.request.RoleFieldPolicyReplaceRequest;
import io.github.archetom.crm.api.dto.request.RoleGrantRequest;
import io.github.archetom.crm.api.dto.response.ApprovalDefinitionResponse;
import io.github.archetom.crm.api.dto.response.ApprovalInstanceResponse;
import io.github.archetom.crm.api.dto.response.OaFormResponse;
import io.github.archetom.crm.api.dto.response.OaRequestResponse;
import io.github.archetom.crm.api.dto.response.RoleFieldPolicyResponse;
import io.github.archetom.crm.api.dto.response.TenantRoleResponse;
import io.github.archetom.crm.api.dto.response.WorklistPageResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** B20b atomic OA request and B11 runtime integration coverage. */
@RequiresDatabase
class OaRequestIntegrationTest extends BaseIntegrationTest {

    private static final long ACTOR_ID = 888888L;
    private static final long TENANT_ID = 999999L;
    private static final long OTHER_ACTOR_ID = 777777L;
    private static final long OTHER_TENANT_ID = 666666L;
    private static final long NON_PARTICIPANT_ACTOR_ID = 555555L;
    private static final long MANAGER_ACTOR_ID = 444444L;
    private static final long WATCHER_ACTOR_ID = 333333L;

    @Test
    void previewsSubmitsRetriesAndDrivesTheSameB11Approval() throws Exception {
        bootstrap(ACTOR_ID, TENANT_ID, "OA owner");
        seedActiveMember(OTHER_ACTOR_ID, "OA reviewer");
        seedActiveMember(MANAGER_ACTOR_ID, "OA manager");
        seedActiveMember(WATCHER_ACTOR_ID, "OA watcher");
        TenantRoleResponse reviewer = fromJson(performPost(
                        "/api/v1/authorization/roles",
                        new RoleCreateRequest().setName("OA request reviewer")
                                .setGrants(List.of(
                                        new RoleGrantRequest().setAuthorityCode(
                                                "oa-requests:read"),
                                        new RoleGrantRequest().setAuthorityCode(
                                                "approvals:read"),
                                        new RoleGrantRequest().setAuthorityCode(
                                                "approvals:review"),
                                        new RoleGrantRequest().setAuthorityCode(
                                                "worklist:read"))))
                        .andExpect(status().isOk()).andReturn()
                        .getResponse().getContentAsString(), TenantRoleResponse.class);
        performPost("/api/v1/authorization/roles/" + reviewer.getId() + "/assignments",
                new RoleAssignmentCreateRequest().setActorIds(List.of(
                        OTHER_ACTOR_ID, MANAGER_ACTOR_ID, WATCHER_ACTOR_ID)))
                .andExpect(status().isOk());
        RoleFieldPolicyResponse reviewerPolicy = fromJson(performPut(
                        "/api/v1/fields/roles/" + reviewer.getId()
                                + "/objects/OA_REQUEST/access",
                new RoleFieldPolicyReplaceRequest().setAccesses(List.of(
                        new RoleFieldAccessRequest().setFieldCode("request_title")
                                .setAccessLevel("READ").setMaskType("NONE"))))
                        .andExpect(status().isOk()).andReturn()
                        .getResponse().getContentAsString(), RoleFieldPolicyResponse.class);

        ApprovalDefinitionResponse approval = activateApproval(
                createApproval(OTHER_ACTOR_ID, MANAGER_ACTOR_ID));
        OaFormResponse form = activateForm(createForm(approval.id()));

        OaRequestPreviewRequest preview = new OaRequestPreviewRequest()
                .setFormId(form.getId()).setFormVersion(form.getVersion())
                .setValues(Map.of("request_title", List.of("Leave request")));
        performPost("/api/v1/oa/requests/preview", preview)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.formId").value(form.getId()))
                .andExpect(jsonPath("$.definitionId").value(approval.id()))
                .andExpect(jsonPath("$.steps[0].assigneeActorIds[0]")
                        .value(OTHER_ACTOR_ID));

        OaRequestSubmitRequest submit = new OaRequestSubmitRequest()
                .setFormId(form.getId()).setFormVersion(form.getVersion())
                .setIdempotencyKey("oa-client-retry-1")
                .setValues(Map.of("request_title", List.of("Leave request")))
                .setWatcherActorIds(List.of(WATCHER_ACTOR_ID));
        OaRequestResponse created = fromJson(performPost("/api/v1/oa/requests", submit)
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.approvalStatus").value("PENDING"))
                        .andExpect(jsonPath("$.values.form_code[0]").value("leave"))
                        .andExpect(jsonPath("$.values.applicant_actor[0]")
                                .value(Long.toString(ACTOR_ID)))
                        .andExpect(jsonPath("$.formCode").doesNotExist())
                        .andExpect(jsonPath("$.applicantActorId").doesNotExist())
                        .andExpect(jsonPath("$.applicantDepartmentId").doesNotExist())
                        .andExpect(jsonPath("$.submittedTime").doesNotExist())
                        .andReturn().getResponse().getContentAsString(),
                OaRequestResponse.class);

        OaRequestResponse retried = fromJson(performPost("/api/v1/oa/requests", submit)
                        .andExpect(status().isOk()).andReturn()
                        .getResponse().getContentAsString(), OaRequestResponse.class);
        assertEquals(created.id(), retried.id());
        assertEquals(1L, new JdbcTemplate(dataSource).queryForObject(
                "SELECT COUNT(*) FROM oa_request WHERE tenant_id = ?",
                Long.class, TENANT_ID));
        assertEquals(1L, new JdbcTemplate(dataSource).queryForObject(
                "SELECT COUNT(*) FROM approval_instance WHERE tenant_id = ? "
                        + "AND object_type = 'OA_REQUEST'",
                Long.class, TENANT_ID));

        WorklistPageResponse worklist = fromJson(performAs(
                        OTHER_ACTOR_ID, TENANT_ID, get("/api/v1/worklist")
                                .param("date", created.values().get("submitted_time")
                                        .getFirst().substring(0, 10))
                                .param("size", "100"))
                        .andExpect(status().isOk()).andReturn()
                        .getResponse().getContentAsString(), WorklistPageResponse.class);
        assertTrue(worklist.getItems().stream().anyMatch(item ->
                "APPROVAL_PENDING".equals(item.getKind())
                        && created.approvalInstanceId().equals(item.getSourceId())));

        performPut("/api/v1/fields/roles/" + reviewer.getId()
                        + "/objects/OA_REQUEST/access",
                new RoleFieldPolicyReplaceRequest().setVersion(reviewerPolicy.getVersion())
                        .setAccesses(List.of(new RoleFieldAccessRequest()
                                .setFieldCode("request_title").setAccessLevel("READ")
                                .setMaskType("PARTIAL"))))
                .andExpect(status().isOk());
        WorklistPageResponse maskedWorklist = fromJson(performAs(
                        OTHER_ACTOR_ID, TENANT_ID, get("/api/v1/worklist")
                                .param("date", created.values().get("submitted_time")
                                        .getFirst().substring(0, 10))
                                .param("size", "100"))
                        .andExpect(status().isOk()).andReturn()
                        .getResponse().getContentAsString(), WorklistPageResponse.class);
        assertTrue(maskedWorklist.getItems().stream().noneMatch(item ->
                "APPROVAL_PENDING".equals(item.getKind())
                        && created.approvalInstanceId().equals(item.getSourceId())));

        performAs(OTHER_ACTOR_ID, TENANT_ID,
                get("/api/v1/oa/requests/{requestId}", created.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.values.request_title[0]")
                        .value("L********t"))
                .andExpect(jsonPath("$.values.form_code").doesNotExist());
        performAs(MANAGER_ACTOR_ID, TENANT_ID,
                get("/api/v1/oa/requests/{requestId}", created.id()))
                .andExpect(status().isOk());
        performAs(WATCHER_ACTOR_ID, TENANT_ID,
                get("/api/v1/oa/requests/{requestId}", created.id()))
                .andExpect(status().isOk());

        seedActiveMember(NON_PARTICIPANT_ACTOR_ID, "Non participant");
        TenantRoleResponse reader = fromJson(performPost(
                        "/api/v1/authorization/roles",
                        new RoleCreateRequest().setName("OA request reader")
                                .setGrants(List.of(new RoleGrantRequest()
                                        .setAuthorityCode("oa-requests:read"))))
                        .andExpect(status().isOk()).andReturn()
                        .getResponse().getContentAsString(), TenantRoleResponse.class);
        performPost("/api/v1/authorization/roles/" + reader.getId() + "/assignments",
                new RoleAssignmentCreateRequest().setActorIds(
                        List.of(NON_PARTICIPANT_ACTOR_ID)))
                .andExpect(status().isOk());
        performAs(NON_PARTICIPANT_ACTOR_ID, TENANT_ID,
                get("/api/v1/oa/requests/{requestId}", created.id()))
                .andExpect(status().isNotFound());
        performAs(NON_PARTICIPANT_ACTOR_ID, TENANT_ID, get("/api/v1/oa/requests"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNum").value(0));
        TenantRoleResponse manager = fromJson(performPost(
                        "/api/v1/authorization/roles",
                        new RoleCreateRequest().setName("OA request tenant manager")
                                .setGrants(List.of(new RoleGrantRequest()
                                        .setAuthorityCode("oa-requests:manage"))))
                        .andExpect(status().isOk()).andReturn()
                        .getResponse().getContentAsString(), TenantRoleResponse.class);
        performPost("/api/v1/authorization/roles/" + manager.getId() + "/assignments",
                new RoleAssignmentCreateRequest().setActorIds(
                        List.of(NON_PARTICIPANT_ACTOR_ID)))
                .andExpect(status().isOk());
        performAs(NON_PARTICIPANT_ACTOR_ID, TENANT_ID,
                get("/api/v1/oa/requests/{requestId}", created.id()))
                .andExpect(status().isOk());
        performAs(NON_PARTICIPANT_ACTOR_ID, TENANT_ID, get("/api/v1/oa/requests"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNum").value(1));

        performPost("/api/v1/oa/requests", new OaRequestSubmitRequest()
                .setFormId(form.getId()).setFormVersion(form.getVersion())
                .setIdempotencyKey("oa-client-retry-1")
                .setValues(Map.of("request_title", List.of("Changed request"))))
                .andExpect(status().isConflict());

        ApprovalInstanceResponse instance = fromJson(performAs(
                        OTHER_ACTOR_ID, TENANT_ID,
                        get("/api/v1/approvals/" + created.approvalInstanceId()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.title").value("L********t"))
                        .andExpect(jsonPath("$.applicantActorId").doesNotExist())
                        .andExpect(jsonPath("$.facts").doesNotExist())
                        .andExpect(jsonPath("$.submittedTime").doesNotExist())
                        .andReturn().getResponse().getContentAsString(),
                ApprovalInstanceResponse.class);
        performAs(OTHER_ACTOR_ID, TENANT_ID, post("/api/v1/approvals/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new ApprovalQueryRequest().setBucket("PENDING")
                                .setObjectType("OA_REQUEST"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNum").value(1))
                .andExpect(jsonPath("$.objectList[0].title")
                        .value("L********t"))
                .andExpect(jsonPath("$.objectList[0].facts").doesNotExist())
                .andExpect(jsonPath("$.objectList[0].applicantActorId")
                        .doesNotExist());
        performAs(OTHER_ACTOR_ID, TENANT_ID, post("/api/v1/approvals/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new ApprovalQueryRequest().setBucket("PENDING")
                                .setObjectType("OA_REQUEST").setKeyword("Leave"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNum").value(0));
        performAs(OTHER_ACTOR_ID, TENANT_ID,
                post("/api/v1/approvals/" + instance.id() + "/actions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new ApprovalActionRequest()
                                .setVersion(instance.version()).setAction("APPROVE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instance.status").value("APPROVED"));

        performGet("/api/v1/oa/requests/" + created.id())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approvalStatus").value("APPROVED"));
        performGetWithParams("/api/v1/oa/requests", Map.of("status", "APPROVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNum").value(1))
                .andExpect(jsonPath("$.objectList[0].id").value(created.id()));

        bootstrap(OTHER_ACTOR_ID, OTHER_TENANT_ID, "Other tenant owner");
        performAs(OTHER_ACTOR_ID, OTHER_TENANT_ID,
                get("/api/v1/oa/requests/{requestId}", created.id()))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsCallerOwnedFactsAndRollsBackWhenRouteResolutionFails() throws Exception {
        bootstrap(ACTOR_ID, TENANT_ID, "OA rollback owner");
        ApprovalDefinitionResponse approval = activateApproval(createApproval());
        OaFormResponse form = activateForm(createForm(approval.id()));

        mockMvc.perform(withTestActor(post("/api/v1/oa/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"formId":%d,"formVersion":%d,
                                 "idempotencyKey":"forged-facts",
                                 "values":{"request_title":["Leave request"]},
                                 "tenantId":123}
                                """.formatted(form.getId(), form.getVersion()))))
                .andExpect(status().isBadRequest());

        OaRequestSubmitRequest invalidRoute = new OaRequestSubmitRequest()
                .setFormId(form.getId()).setFormVersion(form.getVersion())
                .setIdempotencyKey("invalid-selected-step")
                .setValues(Map.of("request_title", List.of("Leave request")))
                .setSelectedApprovers(Map.of("unknown-step", List.of(ACTOR_ID)));
        performPost("/api/v1/oa/requests", invalidRoute)
                .andExpect(status().isBadRequest());
        assertEquals(0L, new JdbcTemplate(dataSource).queryForObject(
                "SELECT COUNT(*) FROM oa_request WHERE tenant_id = ?",
                Long.class, TENANT_ID));
        assertEquals(0L, new JdbcTemplate(dataSource).queryForObject(
                "SELECT COUNT(*) FROM approval_instance WHERE tenant_id = ?",
                Long.class, TENANT_ID));
    }

    @Test
    void concurrentEquivalentRetriesCreateExactlyOneRequestAndApproval() throws Exception {
        bootstrap(ACTOR_ID, TENANT_ID, "OA concurrent owner");
        ApprovalDefinitionResponse approval = activateApproval(createApproval());
        OaFormResponse form = activateForm(createForm(approval.id()));
        OaRequestSubmitRequest submit = new OaRequestSubmitRequest()
                .setFormId(form.getId()).setFormVersion(form.getVersion())
                .setIdempotencyKey("oa-concurrent-retry")
                .setValues(Map.of("request_title", List.of("Leave request")));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<OaRequestResponse> first = executor.submit(() ->
                    concurrentSubmit(submit, ready, start));
            Future<OaRequestResponse> second = executor.submit(() ->
                    concurrentSubmit(submit, ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            OaRequestResponse firstResult = first.get(15, TimeUnit.SECONDS);
            OaRequestResponse secondResult = second.get(15, TimeUnit.SECONDS);
            assertEquals(firstResult.id(), secondResult.id());
            assertEquals(firstResult.approvalInstanceId(), secondResult.approvalInstanceId());
        } finally {
            start.countDown();
            executor.shutdownNow();
        }

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertEquals(1L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM oa_request WHERE tenant_id = ?",
                Long.class, TENANT_ID));
        assertEquals(1L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM approval_instance WHERE tenant_id = ? "
                        + "AND object_type = 'OA_REQUEST'",
                Long.class, TENANT_ID));
    }

    private OaRequestResponse concurrentSubmit(
            OaRequestSubmitRequest request, CountDownLatch ready, CountDownLatch start)
            throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("OA concurrent submission start timed out");
        }
        var result = performPost("/api/v1/oa/requests", request)
                .andExpect(status().isOk()).andReturn();
        return fromJson(result.getResponse().getContentAsString(), OaRequestResponse.class);
    }

    private ApprovalDefinitionResponse createApproval() throws Exception {
        return createApproval(ACTOR_ID, ACTOR_ID);
    }

    private ApprovalDefinitionResponse createApproval(
            long reviewerActorId, long managerActorId) throws Exception {
        ApprovalDefinitionRequest request = new ApprovalDefinitionRequest()
                .setObjectType("OA_REQUEST").setName("Leave approval")
                .setRepeatMode("ONCE").setManagerActorIds(List.of(managerActorId))
                .setSteps(List.of(new ApprovalStepRequest().setCode("owner-review")
                        .setName("Owner review").setOrder(0).setType("MEMBER")
                        .setMode("ALL").setFallback("MANAGERS")
                        .setMemberActorIds(List.of(reviewerActorId))
                        .setConditions(List.of(new ApprovalConditionRequest()
                                .setFieldCode("request_title").setOperator("EQ")
                                .setValues(List.of("Leave request"))))));
        return fromJson(performPost("/api/v1/approval-definitions", request)
                        .andExpect(status().isOk()).andReturn()
                        .getResponse().getContentAsString(),
                ApprovalDefinitionResponse.class);
    }

    private ApprovalDefinitionResponse activateApproval(
            ApprovalDefinitionResponse approval) throws Exception {
        return fromJson(performPut("/api/v1/approval-definitions/"
                        + approval.id() + "/status",
                new ApprovalDefinitionStatusRequest().setVersion(approval.version())
                        .setStatus("ACTIVE"))
                        .andExpect(status().isOk()).andReturn()
                        .getResponse().getContentAsString(),
                ApprovalDefinitionResponse.class);
    }

    private OaFormResponse createForm(Long approvalId) throws Exception {
        return fromJson(performPost("/api/v1/oa/forms", new OaFormCreateRequest()
                .setCode("leave").setName("Leave form")
                .setApprovalDefinitionId(approvalId)
                .setFields(List.of(new OaFormFieldRequest()
                        .setFieldCode("request_title").setRequired(true)
                        .setSortOrder(10))))
                .andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString(), OaFormResponse.class);
    }

    private OaFormResponse activateForm(OaFormResponse form) throws Exception {
        return fromJson(performPut("/api/v1/oa/forms/" + form.getId() + "/status",
                new OaFormStatusUpdateRequest().setVersion(form.getVersion())
                        .setStatus("ACTIVE"))
                .andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString(), OaFormResponse.class);
    }

    private void bootstrap(long actorId, long tenantId, String displayName) throws Exception {
        performAs(actorId, tenantId, post("/api/v1/organization/bootstrap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new OrganizationBootstrapRequest()
                                .setDisplayName(displayName))))
                .andExpect(status().isOk());
    }

    private void seedActiveMember(long actorId, String displayName) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO organization_member_profile (actor_id, display_name) VALUES (?, ?)",
                actorId, displayName);
        jdbc.update("INSERT INTO organization_membership (tenant_id, actor_id, status) "
                        + "VALUES (?, ?, 'ACTIVE')", TENANT_ID, actorId);
        jdbc.update("INSERT INTO authorization_role_assignment (tenant_id, actor_id, role_id) "
                        + "SELECT tenant_id, ?, id FROM authorization_role "
                        + "WHERE tenant_id = ? AND system_key = 'TENANT_MEMBER'",
                actorId, TENANT_ID);
    }

    private org.springframework.test.web.servlet.ResultActions performAs(
            long actorId, long tenantId,
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        return mockMvc.perform(request.header("X-Dev-User-Id", actorId)
                .header("X-Dev-Tenant-Id", tenantId));
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
