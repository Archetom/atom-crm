package io.github.archetom.crm;

import io.github.archetom.crm.api.dto.request.ApprovalConditionRequest;
import io.github.archetom.crm.api.dto.request.ApprovalDefinitionRequest;
import io.github.archetom.crm.api.dto.request.ApprovalDefinitionStatusRequest;
import io.github.archetom.crm.api.dto.request.ApprovalPreviewRequest;
import io.github.archetom.crm.api.dto.request.ApprovalSubmitRequest;
import io.github.archetom.crm.api.dto.request.ApprovalStepRequest;
import io.github.archetom.crm.api.dto.request.FieldCreateRequest;
import io.github.archetom.crm.api.dto.request.FieldUpdateRequest;
import io.github.archetom.crm.api.dto.request.OaFormCreateRequest;
import io.github.archetom.crm.api.dto.request.OaFormFieldRequest;
import io.github.archetom.crm.api.dto.request.OaFormStatusUpdateRequest;
import io.github.archetom.crm.api.dto.request.OaRequestPreviewRequest;
import io.github.archetom.crm.api.dto.request.OrganizationBootstrapRequest;
import io.github.archetom.crm.api.dto.request.RoleAssignmentCreateRequest;
import io.github.archetom.crm.api.dto.request.RoleCreateRequest;
import io.github.archetom.crm.api.dto.request.RoleFieldAccessRequest;
import io.github.archetom.crm.api.dto.request.RoleFieldPolicyReplaceRequest;
import io.github.archetom.crm.api.dto.request.RoleGrantRequest;
import io.github.archetom.crm.api.dto.response.ApprovalDefinitionResponse;
import io.github.archetom.crm.api.dto.response.FieldDefinitionResponse;
import io.github.archetom.crm.api.dto.response.OaFormResponse;
import io.github.archetom.crm.api.dto.response.RoleFieldPolicyResponse;
import io.github.archetom.crm.api.dto.response.TenantRoleResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** B20a OA_REQUEST catalog, form lifecycle, dependency, tenancy, and field-policy coverage. */
@RequiresDatabase
class OaFormIntegrationTest extends BaseIntegrationTest {

    private static final long ACTOR_ID = 888888L;
    private static final long TENANT_ID = 999999L;
    private static final long MEMBER_ACTOR_ID = 777777L;
    private static final long OTHER_ACTOR_ID = 666666L;
    private static final long OTHER_TENANT_ID = 555555L;

    @Test
    void provisionsCatalogAndProtectsActiveFieldAndApprovalDependencies() throws Exception {
        mockMvc.perform(get("/api/v1/oa/forms")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/oa/forms/configuration-options"))
                .andExpect(status().isUnauthorized());
        bootstrap(ACTOR_ID, TENANT_ID, "OA owner");
        performGet("/api/v1/fields/objects/OA_REQUEST")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[?(@.code == 'request_title')].fieldType")
                        .value("TEXT"))
                .andExpect(jsonPath("$[?(@.code == 'applicant_actor')].fieldType")
                        .value("USER"));

        FieldDefinitionResponse details = createField("Request details", true);
        FieldDefinitionResponse unconfigured = createField("Unconfigured fact", true);
        ApprovalDefinitionResponse approval = activateApproval(createApproval(
                "OA approval", "request_title", "Request"));
        performGet("/api/v1/oa/forms/configuration-options")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approvalDefinitions[0].id").value(approval.id()))
                .andExpect(jsonPath("$.approvalDefinitions[0].requiredFieldCodes[0]")
                        .value("request_title"))
                .andExpect(jsonPath("$.fields[?(@.fieldCode == 'request_title')].fieldType")
                        .value("TEXT"))
                .andExpect(jsonPath("$.fields[?(@.fieldCode == 'applicant_actor')]").isEmpty());
        OaFormResponse form = createForm("  Ｌｅａｖｅ  ", approval.id(),
                List.of(field("request_title", true, 10),
                        field(details.getCode(), false, 20)));
        assertEquals("INACTIVE", form.getStatus());

        OaFormResponse active = activate(form);
        assertEquals("leave", new JdbcTemplate(dataSource).queryForObject(
                "SELECT normalized_code FROM oa_form_definition WHERE tenant_id = ? AND id = ?",
                String.class, TENANT_ID, active.getId()));
        performGet("/api/v1/oa/forms/available")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].fields.length()").value(2))
                .andExpect(jsonPath("$[0].fields[1].displayName").value("Request details"));
        performPost("/api/v1/approval-definitions/" + approval.id() + "/preview",
                new ApprovalPreviewRequest().setSubjectId(1L))
                .andExpect(status().isUnprocessableEntity());
        performPost("/api/v1/approvals", new ApprovalSubmitRequest()
                .setDefinitionId(approval.id()).setSubjectId(1L)
                .setIdempotencyKey("b20a-runtime-not-yet-available")
                .setTitle("B20a request"))
                .andExpect(status().isUnprocessableEntity());

        performAs(ACTOR_ID, TENANT_ID,
                delete("/api/v1/fields/objects/OA_REQUEST/{fieldId}", details.getId())
                        .param("version", details.getVersion().toString()))
                .andExpect(status().isUnprocessableEntity());
        performPut("/api/v1/fields/objects/OA_REQUEST/" + details.getId(),
                new FieldUpdateRequest().setVersion(details.getVersion())
                        .setDisplayName("Request details").setSearchable(false))
                .andExpect(status().isUnprocessableEntity());
        performPut("/api/v1/approval-definitions/" + approval.id() + "/status",
                new ApprovalDefinitionStatusRequest().setVersion(approval.version())
                        .setStatus("INACTIVE"))
                .andExpect(status().isUnprocessableEntity());
        performPut("/api/v1/approval-definitions/" + approval.id(),
                approvalRequest("OA approval", unconfigured.getCode(), "blocked")
                        .setVersion(approval.version()))
                .andExpect(status().isUnprocessableEntity());

        OaFormResponse inactive = fromJson(performPut(
                "/api/v1/oa/forms/" + active.getId() + "/status",
                new OaFormStatusUpdateRequest().setVersion(active.getVersion())
                        .setStatus("INACTIVE"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                OaFormResponse.class);
        FieldDefinitionResponse notSearchable = fromJson(performPut(
                "/api/v1/fields/objects/OA_REQUEST/" + details.getId(),
                new FieldUpdateRequest().setVersion(details.getVersion())
                        .setDisplayName("Request details").setSearchable(false))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                FieldDefinitionResponse.class);
        performPut("/api/v1/approval-definitions/" + approval.id() + "/status",
                new ApprovalDefinitionStatusRequest().setVersion(approval.version())
                        .setStatus("DELETED"))
                .andExpect(status().isOk());
        performAs(ACTOR_ID, TENANT_ID,
                delete("/api/v1/fields/objects/OA_REQUEST/{fieldId}", details.getId())
                        .param("version", notSearchable.getVersion().toString()))
                .andExpect(status().isOk());
        performAs(ACTOR_ID, TENANT_ID,
                delete("/api/v1/oa/forms/{formId}", inactive.getId())
                        .param("version", inactive.getVersion().toString()))
                .andExpect(status().isOk());
        performGet("/api/v1/oa/forms")
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalNum").value(0));
    }

    @Test
    void rejectsInvalidAndCrossTenantConfigurationWithSafeNotFoundSemantics() throws Exception {
        bootstrap(ACTOR_ID, TENANT_ID, "Tenant A owner");
        ApprovalDefinitionResponse inactiveApproval = createApproval(
                "Inactive OA approval", "request_title", "Request");
        OaFormResponse inactive = createForm("travel", inactiveApproval.id(),
                List.of(field("request_title", true, 10)));
        performPut("/api/v1/oa/forms/" + inactive.getId() + "/status",
                new OaFormStatusUpdateRequest().setVersion(inactive.getVersion())
                        .setStatus("ACTIVE"))
                .andExpect(status().isUnprocessableEntity());

        performPost("/api/v1/oa/forms", new OaFormCreateRequest()
                .setCode("derived").setName("Derived")
                .setApprovalDefinitionId(inactiveApproval.id())
                .setFields(List.of(field("request_title", true, 10),
                        field("applicant_actor", true, 20))))
                .andExpect(status().isBadRequest());
        performPost("/api/v1/oa/forms", new OaFormCreateRequest()
                .setCode("duplicate-field").setName("Duplicate field")
                .setApprovalDefinitionId(inactiveApproval.id())
                .setFields(List.of(field("request_title", true, 10),
                        field("request_title", true, 20))))
                .andExpect(status().isUnprocessableEntity());
        performPost("/api/v1/oa/forms", new OaFormCreateRequest()
                .setCode("ＴＲＡＶＥＬ").setName("Normalized duplicate")
                .setApprovalDefinitionId(inactiveApproval.id())
                .setFields(List.of(field("request_title", true, 10))))
                .andExpect(status().isUnprocessableEntity());

        ApprovalDefinitionResponse customerApproval = fromJson(performPost(
                "/api/v1/approval-definitions",
                approvalRequest("Customer approval", "customer_name", "Customer")
                        .setObjectType("CUSTOMER"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ApprovalDefinitionResponse.class);
        performPost("/api/v1/oa/forms", new OaFormCreateRequest()
                .setCode("wrong-object").setName("Wrong object")
                .setApprovalDefinitionId(customerApproval.id())
                .setFields(List.of(field("request_title", true, 10))))
                .andExpect(status().isNotFound());

        bootstrap(OTHER_ACTOR_ID, OTHER_TENANT_ID, "Tenant B owner");
        performAs(OTHER_ACTOR_ID, OTHER_TENANT_ID,
                get("/api/v1/oa/forms/{formId}", inactive.getId()))
                .andExpect(status().isNotFound());
        performAs(OTHER_ACTOR_ID, OTHER_TENANT_ID,
                post("/api/v1/oa/forms").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new OaFormCreateRequest()
                                .setCode("cross-tenant").setName("Cross tenant")
                                .setApprovalDefinitionId(inactiveApproval.id())
                                .setFields(List.of(field("request_title", true, 10))))))
                .andExpect(status().isNotFound());
    }

    @Test
    void availableCatalogRequiresSubmitAuthorityAndCurrentWriteFieldPolicy() throws Exception {
        bootstrap(ACTOR_ID, TENANT_ID, "OA owner");
        FieldDefinitionResponse details = createField("Member details", true);
        ApprovalDefinitionResponse approval = activateApproval(createApproval(
                "Member OA approval", "request_title", "Request"));
        activate(createForm("member-request", approval.id(), List.of(
                field("request_title", true, 10), field(details.getCode(), false, 20))));

        seedActiveMember(MEMBER_ACTOR_ID, "OA member");
        TenantRoleResponse role = fromJson(performPost("/api/v1/authorization/roles",
                new RoleCreateRequest().setName("OA submitter")
                        .setGrants(List.of(grant("oa-forms:read"),
                                grant("oa-requests:submit"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                TenantRoleResponse.class);
        performPost("/api/v1/authorization/roles/" + role.getId() + "/assignments",
                new RoleAssignmentCreateRequest().setActorIds(List.of(MEMBER_ACTOR_ID)))
                .andExpect(status().isOk());
        RoleFieldPolicyResponse policy = fromJson(performPut(
                "/api/v1/fields/roles/" + role.getId() + "/objects/OA_REQUEST/access",
                new RoleFieldPolicyReplaceRequest().setAccesses(List.of(
                        access("request_title"), access(details.getCode()))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                RoleFieldPolicyResponse.class);

        performAs(MEMBER_ACTOR_ID, TENANT_ID, get("/api/v1/oa/forms/available"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].fields.length()").value(2));

        performPut("/api/v1/fields/roles/" + role.getId()
                        + "/objects/OA_REQUEST/access",
                new RoleFieldPolicyReplaceRequest().setVersion(policy.getVersion())
                        .setAccesses(List.of(access("request_title"))))
                .andExpect(status().isOk());
        performAs(MEMBER_ACTOR_ID, TENANT_ID, get("/api/v1/oa/forms/available"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        performAs(MEMBER_ACTOR_ID, TENANT_ID, get("/api/v1/oa/forms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectList[0].fields.length()").value(1))
                .andExpect(jsonPath("$.objectList[0].fields[0].fieldCode")
                        .value("request_title"));

        TenantRoleResponse readOnly = fromJson(performPost("/api/v1/authorization/roles",
                new RoleCreateRequest().setName("OA reader")
                        .setGrants(List.of(grant("oa-forms:read"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                TenantRoleResponse.class);
        long readOnlyActor = MEMBER_ACTOR_ID - 1;
        seedActiveMember(readOnlyActor, "OA reader");
        performPost("/api/v1/authorization/roles/" + readOnly.getId() + "/assignments",
                new RoleAssignmentCreateRequest().setActorIds(List.of(readOnlyActor)))
                .andExpect(status().isOk());
        performAs(readOnlyActor, TENANT_ID, get("/api/v1/oa/forms/available"))
                .andExpect(status().isForbidden());
        performAs(readOnlyActor, TENANT_ID,
                post("/api/v1/oa/forms").contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void constrainsCandidateCatalogsAndDirectPreviewToTheSameAvailableFormScope()
            throws Exception {
        bootstrap(ACTOR_ID, TENANT_ID, "Scoped OA owner");
        FieldDefinitionResponse delegate = fromJson(performPost(
                "/api/v1/fields/objects/OA_REQUEST",
                new FieldCreateRequest().setDisplayName("Delegate")
                        .setFieldType("USER").setSearchable(true))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                FieldDefinitionResponse.class);
        seedActiveMember(MEMBER_ACTOR_ID, "Scoped requester");
        ApprovalDefinitionResponse approval = activateApproval(fromJson(performPost(
                "/api/v1/approval-definitions", new ApprovalDefinitionRequest()
                        .setObjectType("OA_REQUEST").setName("Scoped OA approval")
                        .setRepeatMode("ONCE").setInitiatorActorIds(List.of(MEMBER_ACTOR_ID))
                        .setManagerActorIds(List.of(ACTOR_ID)).setSteps(List.of(
                                new ApprovalStepRequest().setCode("selected-reviewer")
                                        .setName("Selected reviewer").setOrder(0)
                                        .setType("APPLICANT_SELECT").setMode("ALL")
                                        .setFallback("AUTO_APPROVE"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ApprovalDefinitionResponse.class));
        OaFormResponse form = activate(createForm("scoped-request", approval.id(), List.of(
                field("request_title", true, 10), field(delegate.getCode(), false, 20))));
        assertEquals("selected-reviewer", form.getApprovalSelections().getFirst().code());

        TenantRoleResponse role = fromJson(performPost("/api/v1/authorization/roles",
                new RoleCreateRequest().setName("Scoped OA submitter")
                        .setGrants(List.of(grant("oa-forms:read"),
                                grant("oa-requests:submit"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                TenantRoleResponse.class);
        performPost("/api/v1/authorization/roles/" + role.getId() + "/assignments",
                new RoleAssignmentCreateRequest().setActorIds(List.of(MEMBER_ACTOR_ID)))
                .andExpect(status().isOk());
        performPut("/api/v1/fields/roles/" + role.getId() + "/objects/OA_REQUEST/access",
                new RoleFieldPolicyReplaceRequest().setAccesses(List.of(
                        access("request_title"), access(delegate.getCode()))))
                .andExpect(status().isOk());

        performAs(MEMBER_ACTOR_ID, TENANT_ID,
                get("/api/v1/oa/forms/available/{formId}/fields/{fieldCode}/candidates",
                        form.getId(), delegate.getCode()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectList[0].valueType").value("ACTOR_ID"))
                .andExpect(jsonPath("$.objectList[?(@.id == " + MEMBER_ACTOR_ID + ")]").exists());
        performAs(MEMBER_ACTOR_ID, TENANT_ID,
                get("/api/v1/oa/forms/available/{formId}/approval-selections/{selectionCode}/candidates",
                        form.getId(), "selected-reviewer").param("keyword", "Scoped OA owner"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNum").value(1))
                .andExpect(jsonPath("$.objectList[?(@.actorId == " + ACTOR_ID + ")]").exists());

        performPost("/api/v1/oa/requests/preview", new OaRequestPreviewRequest()
                .setFormId(form.getId()).setFormVersion(form.getVersion())
                .setValues(java.util.Map.of("request_title", List.of("Scoped request"))))
                .andExpect(status().isNotFound());
        performGet("/api/v1/oa/forms/available/{formId}/fields/{fieldCode}/candidates",
                form.getId(), delegate.getCode()).andExpect(status().isNotFound());
    }

    @Test
    void customizationGuardSerializesActivationAndDefinitionDeactivationAndTenantLimit()
            throws Exception {
        bootstrap(ACTOR_ID, TENANT_ID, "Concurrent OA owner");
        ApprovalDefinitionResponse approval = activateApproval(createApproval(
                "Concurrent OA approval", "request_title", "Request"));
        OaFormResponse form = createForm("concurrent-form", approval.id(),
                List.of(field("request_title", true, 10)));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try (Connection blocker = dataSource.getConnection()) {
            blocker.setAutoCommit(false);
            lockOaCustomizationGuard(blocker);
            Callable<Integer> activate = () -> performAs(ACTOR_ID, TENANT_ID,
                    put("/api/v1/oa/forms/{formId}/status", form.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(new OaFormStatusUpdateRequest()
                                    .setVersion(form.getVersion()).setStatus("ACTIVE"))))
                    .andReturn().getResponse().getStatus();
            Callable<Integer> deactivateDefinition = () -> performAs(ACTOR_ID, TENANT_ID,
                    put("/api/v1/approval-definitions/{definitionId}/status", approval.id())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(new ApprovalDefinitionStatusRequest()
                                    .setVersion(approval.version()).setStatus("INACTIVE"))))
                    .andReturn().getResponse().getStatus();
            Future<Integer> activation = executor.submit(activate);
            Future<Integer> deactivation = executor.submit(deactivateDefinition);
            Thread.sleep(200);
            assertTrue(!activation.isDone() || !deactivation.isDone());
            blocker.commit();
            List<Integer> statuses = List.of(activation.get(10, TimeUnit.SECONDS),
                    deactivation.get(10, TimeUnit.SECONDS)).stream().sorted().toList();
            assertEquals(List.of(200, 422), statuses);
        } finally {
            executor.shutdownNow();
        }

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        String formStatus = jdbc.queryForObject(
                "SELECT status FROM oa_form_definition WHERE tenant_id = ? AND id = ?",
                String.class, TENANT_ID, form.getId());
        String approvalStatus = jdbc.queryForObject(
                "SELECT status FROM approval_definition WHERE tenant_id = ? AND id = ?",
                String.class, TENANT_ID, approval.id());
        Long currentFormVersion = jdbc.queryForObject(
                "SELECT version FROM oa_form_definition WHERE tenant_id = ? AND id = ?",
                Long.class, TENANT_ID, form.getId());
        Long currentApprovalVersion = jdbc.queryForObject(
                "SELECT version FROM approval_definition WHERE tenant_id = ? AND id = ?",
                Long.class, TENANT_ID, approval.id());
        assertTrue(formStatus.equals("ACTIVE") && approvalStatus.equals("ACTIVE")
                || formStatus.equals("INACTIVE") && approvalStatus.equals("INACTIVE"));

        if (formStatus.equals("ACTIVE")) {
            OaFormResponse inactive = fromJson(performPut(
                    "/api/v1/oa/forms/" + form.getId() + "/status",
                    new OaFormStatusUpdateRequest().setVersion(currentFormVersion)
                            .setStatus("INACTIVE"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                    OaFormResponse.class);
            assertEquals("INACTIVE", inactive.getStatus());
        } else {
            performPut("/api/v1/approval-definitions/" + approval.id() + "/status",
                    new ApprovalDefinitionStatusRequest().setVersion(currentApprovalVersion)
                            .setStatus("ACTIVE"))
                    .andExpect(status().isOk());
        }

        for (int index = 1; index < 199; index++) {
            jdbc.update("INSERT INTO oa_form_definition "
                            + "(tenant_id, created_by_actor_id, code, normalized_code, name, "
                            + "approval_definition_id, status, version) "
                            + "VALUES (?, ?, ?, ?, ?, ?, 'INACTIVE', 0)",
                    TENANT_ID, ACTOR_ID, "seed-" + index, "seed-" + index,
                    "Seed form " + index, approval.id());
        }
        jdbc.update("INSERT INTO oa_form_field "
                + "(tenant_id, oa_form_id, object_type, field_code, is_required, sort_order) "
                + "SELECT tenant_id, id, 'OA_REQUEST', 'request_title', 1, 10 "
                + "FROM oa_form_definition WHERE tenant_id = ? "
                + "AND id <> ? AND NOT EXISTS (SELECT 1 FROM oa_form_field field_row "
                + "WHERE field_row.tenant_id = oa_form_definition.tenant_id "
                + "AND field_row.oa_form_id = oa_form_definition.id)", TENANT_ID, form.getId());

        ExecutorService limitExecutor = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> createA = () -> createStatus("limit-a", approval.id());
            Callable<Integer> createB = () -> createStatus("limit-b", approval.id());
            Future<Integer> first = limitExecutor.submit(createA);
            Future<Integer> second = limitExecutor.submit(createB);
            List<Integer> statuses = List.of(first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS)).stream().sorted().toList();
            assertEquals(List.of(200, 422), statuses);
            assertEquals(200, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM oa_form_definition "
                            + "WHERE tenant_id = ? AND status <> 'DELETED'",
                    Integer.class, TENANT_ID));
        } finally {
            limitExecutor.shutdownNow();
        }
    }

    @Test
    void approvalDefinitionUpdateAndStatusChangeLockGuardBeforeDefinitionRow()
            throws Exception {
        bootstrap(ACTOR_ID, TENANT_ID, "Approval lock-order owner");
        ApprovalDefinitionResponse approval = activateApproval(createApproval(
                "Lock-order approval", "request_title", "Request"));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try (Connection blocker = dataSource.getConnection()) {
            blocker.setAutoCommit(false);
            lockOaCustomizationGuard(blocker);
            Callable<Integer> deactivate = () -> performAs(ACTOR_ID, TENANT_ID,
                    put("/api/v1/approval-definitions/{definitionId}/status", approval.id())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(new ApprovalDefinitionStatusRequest()
                                    .setVersion(approval.version()).setStatus("INACTIVE"))))
                    .andReturn().getResponse().getStatus();
            Future<Integer> statusChange = executor.submit(deactivate);
            Thread.sleep(300);
            assertTrue(!statusChange.isDone());
            assertDefinitionRowCanBeLocked(approval.id());

            Callable<Integer> update = () -> performAs(ACTOR_ID, TENANT_ID,
                    put("/api/v1/approval-definitions/{definitionId}", approval.id())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(approvalRequest("Updated lock-order approval",
                                            "request_title", "Request")
                                    .setVersion(approval.version()))))
                    .andReturn().getResponse().getStatus();
            Future<Integer> definitionUpdate = executor.submit(update);
            Thread.sleep(200);
            assertTrue(!definitionUpdate.isDone());

            blocker.commit();
            List<Integer> statuses = List.of(statusChange.get(10, TimeUnit.SECONDS),
                    definitionUpdate.get(10, TimeUnit.SECONDS)).stream().sorted().toList();
            assertEquals(List.of(200, 409), statuses);
        } finally {
            executor.shutdownNow();
        }

        assertEquals(approval.version() + 1, new JdbcTemplate(dataSource).queryForObject(
                "SELECT version FROM approval_definition WHERE tenant_id = ? AND id = ?",
                Long.class, TENANT_ID, approval.id()));
    }

    private FieldDefinitionResponse createField(String name, boolean searchable) throws Exception {
        return fromJson(performPost("/api/v1/fields/objects/OA_REQUEST",
                new FieldCreateRequest().setDisplayName(name).setFieldType("TEXT")
                        .setSearchable(searchable))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                FieldDefinitionResponse.class);
    }

    private ApprovalDefinitionResponse createApproval(
            String name, String conditionField, String conditionValue) throws Exception {
        return fromJson(performPost("/api/v1/approval-definitions",
                approvalRequest(name, conditionField, conditionValue))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ApprovalDefinitionResponse.class);
    }

    private ApprovalDefinitionResponse activateApproval(
            ApprovalDefinitionResponse approval) throws Exception {
        return fromJson(performPut("/api/v1/approval-definitions/" + approval.id() + "/status",
                new ApprovalDefinitionStatusRequest().setVersion(approval.version())
                        .setStatus("ACTIVE"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ApprovalDefinitionResponse.class);
    }

    private ApprovalDefinitionRequest approvalRequest(
            String name, String conditionField, String conditionValue) {
        return new ApprovalDefinitionRequest().setObjectType("OA_REQUEST")
                .setName(name).setRepeatMode("ONCE")
                .setManagerActorIds(List.of(ACTOR_ID))
                .setSteps(List.of(new ApprovalStepRequest().setCode("owner-review")
                        .setName("Owner review").setOrder(0).setType("MEMBER")
                        .setMode("ALL").setFallback("MANAGERS")
                        .setMemberActorIds(List.of(ACTOR_ID))
                        .setConditions(List.of(new ApprovalConditionRequest()
                                .setFieldCode(conditionField).setOperator("EQ")
                                .setValues(List.of(conditionValue))))));
    }

    private OaFormResponse createForm(
            String code, Long approvalId, List<OaFormFieldRequest> fields) throws Exception {
        return fromJson(performPost("/api/v1/oa/forms", new OaFormCreateRequest()
                .setCode(code).setName(code.trim() + " form")
                .setApprovalDefinitionId(approvalId).setFields(fields))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                OaFormResponse.class);
    }

    private OaFormResponse activate(OaFormResponse form) throws Exception {
        return fromJson(performPut("/api/v1/oa/forms/" + form.getId() + "/status",
                new OaFormStatusUpdateRequest().setVersion(form.getVersion())
                        .setStatus("ACTIVE"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                OaFormResponse.class);
    }

    private OaFormFieldRequest field(String code, boolean required, int order) {
        return new OaFormFieldRequest().setFieldCode(code)
                .setRequired(required).setSortOrder(order);
    }

    private RoleGrantRequest grant(String authority) {
        return new RoleGrantRequest().setAuthorityCode(authority);
    }

    private RoleFieldAccessRequest access(String fieldCode) {
        return new RoleFieldAccessRequest().setFieldCode(fieldCode)
                .setAccessLevel("WRITE").setMaskType("NONE");
    }

    private int createStatus(String code, Long approvalId) throws Exception {
        return performAs(ACTOR_ID, TENANT_ID, post("/api/v1/oa/forms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new OaFormCreateRequest().setCode(code)
                                .setName(code).setApprovalDefinitionId(approvalId)
                                .setFields(List.of(field("request_title", true, 10))))))
                .andReturn().getResponse().getStatus();
    }

    private void lockOaCustomizationGuard(Connection connection) throws Exception {
        try (PreparedStatement lock = connection.prepareStatement(
                "SELECT tenant_id FROM customization_object_guard "
                        + "WHERE tenant_id = ? AND object_type = 'OA_REQUEST' FOR UPDATE")) {
            lock.setLong(1, TENANT_ID);
            try (var rows = lock.executeQuery()) {
                assertTrue(rows.next());
            }
        }
    }

    private void assertDefinitionRowCanBeLocked(Long definitionId) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (var timeout = connection.createStatement()) {
                timeout.execute("SET SESSION innodb_lock_wait_timeout = 1");
            }
            try (PreparedStatement lock = connection.prepareStatement(
                    "SELECT tenant_id FROM approval_definition "
                            + "WHERE tenant_id = ? AND id = ? FOR UPDATE")) {
                lock.setLong(1, TENANT_ID);
                lock.setLong(2, definitionId);
                try (var rows = lock.executeQuery()) {
                    assertTrue(rows.next());
                }
            } finally {
                connection.rollback();
            }
        }
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
