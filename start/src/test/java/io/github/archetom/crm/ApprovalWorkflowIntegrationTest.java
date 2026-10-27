package io.github.archetom.crm;

import io.github.archetom.crm.api.dto.request.ApprovalActionItemRequest;
import io.github.archetom.crm.api.dto.request.ApprovalActionRequest;
import io.github.archetom.crm.api.dto.request.ApprovalBatchActionRequest;
import io.github.archetom.crm.api.dto.request.ApprovalConditionRequest;
import io.github.archetom.crm.api.dto.request.ApprovalDefinitionRequest;
import io.github.archetom.crm.api.dto.request.ApprovalDefinitionStatusRequest;
import io.github.archetom.crm.api.dto.request.ApprovalQueryRequest;
import io.github.archetom.crm.api.dto.request.ApprovalStepRequest;
import io.github.archetom.crm.api.dto.request.ApprovalSubmitRequest;
import io.github.archetom.crm.api.dto.request.CustomerCreateRequest;
import io.github.archetom.crm.api.dto.request.FieldCreateRequest;
import io.github.archetom.crm.api.dto.request.FieldOptionRequest;
import io.github.archetom.crm.api.dto.request.FieldUpdateRequest;
import io.github.archetom.crm.api.dto.request.OrganizationBootstrapRequest;
import io.github.archetom.crm.api.dto.request.RoleAssignmentCreateRequest;
import io.github.archetom.crm.api.dto.request.RoleCreateRequest;
import io.github.archetom.crm.api.dto.request.RoleFieldAccessRequest;
import io.github.archetom.crm.api.dto.request.RoleFieldPolicyReplaceRequest;
import io.github.archetom.crm.api.dto.request.RoleGrantRequest;
import io.github.archetom.crm.api.dto.request.StageAdvanceRequest;
import io.github.archetom.crm.api.dto.request.StageCommentCreateRequest;
import io.github.archetom.crm.api.dto.request.StageDefinitionRequest;
import io.github.archetom.crm.api.dto.request.StageWorkflowRequest;
import io.github.archetom.crm.api.dto.request.StageWorkflowStatusRequest;
import io.github.archetom.crm.api.dto.response.ApprovalDefinitionResponse;
import io.github.archetom.crm.api.dto.response.ApprovalActionResult;
import io.github.archetom.crm.api.dto.response.ApprovalInstanceResponse;
import io.github.archetom.crm.api.dto.response.CustomerResponse;
import io.github.archetom.crm.api.dto.response.FieldDefinitionResponse;
import io.github.archetom.crm.api.dto.response.StageWorkflowCommentResponse;
import io.github.archetom.crm.api.dto.response.StageWorkflowActionResult;
import io.github.archetom.crm.api.dto.response.StageWorkflowMutationResult;
import io.github.archetom.crm.api.dto.response.StageWorkflowRecordResponse;
import io.github.archetom.crm.api.dto.response.TenantRoleResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end approval and stage workflow coverage against MySQL mappings. */
@RequiresDatabase
class ApprovalWorkflowIntegrationTest extends BaseIntegrationTest {

    private static final long ACTOR_ID = 888888L;
    private static final long TENANT_ID = 999999L;
    private static final long OTHER_ACTOR_ID = 777777L;
    private static final long OTHER_TENANT_ID = 666666L;
    private static final long POLICY_ACTOR_ID = 555555L;

    @Test
    void approvalAndStageGatePersistSnapshotsTasksVersionsAndComments() throws Exception {
        mockMvc.perform(post("/api/v1/approvals/query")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        bootstrap();
        FieldDefinitionResponse field = fromJson(performPost(
                "/api/v1/fields/objects/CUSTOMER",
                new FieldCreateRequest().setDisplayName("Approval marker")
                        .setFieldType("TEXT").setSearchable(true))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                FieldDefinitionResponse.class);
        CustomerResponse customer = fromJson(performPost("/api/v1/customers",
                new CustomerCreateRequest().setName("Workflow account")
                        .setCustomFields(Map.of(field.getCode(), List.of("ready"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                CustomerResponse.class);
        FieldDefinitionResponse stageField = fromJson(performPost(
                "/api/v1/fields/objects/CUSTOMER",
                new FieldCreateRequest().setDisplayName("Stage marker")
                        .setFieldType("TEXT").setSearchable(true))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                FieldDefinitionResponse.class);
        FieldDefinitionResponse hiddenField = fromJson(performPost(
                "/api/v1/fields/objects/CUSTOMER",
                new FieldCreateRequest().setDisplayName("Hidden workflow marker")
                        .setFieldType("TEXT").setSearchable(true))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                FieldDefinitionResponse.class);
        seedActiveMember(POLICY_ACTOR_ID, "Workflow policy reader");
        TenantRoleResponse policyRole = fromJson(performPost(
                "/api/v1/authorization/roles",
                new RoleCreateRequest().setName("Workflow policy reader")
                        .setGrants(List.of(
                                grant("customers:read", "TENANT"),
                                grant("customers:write", "TENANT"),
                                grant("approvals:read", null),
                                grant("approvals:review", null),
                                grant("workflows:read", null),
                                grant("workflows:write", null))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                TenantRoleResponse.class);
        performPost("/api/v1/authorization/roles/" + policyRole.getId() + "/assignments",
                new RoleAssignmentCreateRequest().setActorIds(List.of(POLICY_ACTOR_ID)))
                .andExpect(status().isOk());
        performPut("/api/v1/fields/roles/" + policyRole.getId()
                        + "/objects/CUSTOMER/access",
                new RoleFieldPolicyReplaceRequest().setAccesses(List.of(
                        new RoleFieldAccessRequest().setFieldCode(field.getCode())
                                .setAccessLevel("READ").setMaskType("FULL"),
                        new RoleFieldAccessRequest().setFieldCode(stageField.getCode())
                                .setAccessLevel("READ").setMaskType("PARTIAL"))))
                .andExpect(status().isOk());

        ApprovalDefinitionResponse definition = fromJson(performPost(
                "/api/v1/approval-definitions", definitionRequest(field.getCode()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ApprovalDefinitionResponse.class);
        definition = fromJson(performPut("/api/v1/approval-definitions/"
                        + definition.id() + "/status",
                new ApprovalDefinitionStatusRequest().setVersion(definition.version())
                        .setStatus("ACTIVE"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ApprovalDefinitionResponse.class);

        ApprovalInstanceResponse approval = fromJson(performPost("/api/v1/approvals",
                new ApprovalSubmitRequest().setDefinitionId(definition.id())
                        .setSubjectId(customer.getId()).setIdempotencyKey("public-submit")
                        .setTitle("Public approval")
                        .setFacts(Map.of(field.getCode(), List.of("forged"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ApprovalInstanceResponse.class);
        assertEquals("PENDING", approval.status());
        assertEquals(1, approval.tasks().size());
        assertEquals(List.of("APPROVE", "CANCEL", "REJECT", "TRANSFER", "WITHDRAW"),
                approval.allowedActions());
        ApprovalInstanceResponse same = fromJson(performPost("/api/v1/approvals",
                new ApprovalSubmitRequest().setDefinitionId(definition.id())
                        .setSubjectId(customer.getId()).setIdempotencyKey("public-submit")
                        .setTitle("Public approval")
                        .setFacts(Map.of(field.getCode(), List.of("ready"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ApprovalInstanceResponse.class);
        assertEquals(approval.id(), same.id());
        ApprovalInstanceResponse trimmedRetry = fromJson(performPost("/api/v1/approvals",
                new ApprovalSubmitRequest().setDefinitionId(definition.id())
                        .setSubjectId(customer.getId()).setIdempotencyKey(" public-submit ")
                        .setTitle("Public approval")
                        .setFacts(Map.of(field.getCode(), List.of("ready"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ApprovalInstanceResponse.class);
        assertEquals(approval.id(), trimmedRetry.id());
        ApprovalDefinitionRequest invalidCondition = definitionRequest(field.getCode())
                .setName("Invalid condition definition");
        invalidCondition.getSteps().getFirst().getConditions().getFirst()
                .setValues(Collections.singletonList(null));
        performPost("/api/v1/approval-definitions", invalidCondition)
                .andExpect(status().isBadRequest());
        performPost("/api/v1/approvals/actions/batch",
                new ApprovalBatchActionRequest().setActions(Collections.singletonList(null)))
                .andExpect(status().isBadRequest());
        performPost("/api/v1/approvals/query",
                new ApprovalQueryRequest().setBucket("SUBMITTED").setKeyword("%"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalNum").value(0));

        ApprovalInstanceResponse approved = actionResult(performPost(
                "/api/v1/approvals/" + approval.id() + "/actions",
                new ApprovalActionRequest().setVersion(approval.version())
                        .setAction("APPROVE").setRemark("Looks good"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals("APPROVED", approved.status());
        performPost("/api/v1/approvals/" + approval.id() + "/actions",
                new ApprovalActionRequest().setVersion(approval.version())
                        .setAction("APPROVE"))
                .andExpect(status().isConflict());

        definition = fromJson(performPut("/api/v1/approval-definitions/" + definition.id(),
                definitionRequest(field.getCode()).setVersion(definition.version())
                        .setManagerActorIds(List.of(POLICY_ACTOR_ID)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ApprovalDefinitionResponse.class);
        mockMvc.perform(get("/api/v1/approvals/{instanceId}", approval.id())
                        .header("X-Dev-User-Id", POLICY_ACTOR_ID)
                        .header("X-Dev-Tenant-Id", TENANT_ID))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/approvals/{instanceId}/cancel", approval.id())
                .param("version", approved.version().toString())
                .header("X-Dev-User-Id", POLICY_ACTOR_ID)
                .header("X-Dev-Tenant-Id", TENANT_ID))
                .andExpect(status().isUnprocessableEntity());

        CustomerResponse hiddenCustomer = fromJson(performPost("/api/v1/customers",
                new CustomerCreateRequest().setName("Hidden workflow account")
                        .setCustomFields(Map.of(field.getCode(), List.of("ready"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                CustomerResponse.class);
        ApprovalInstanceResponse hiddenApproval = fromJson(performPost("/api/v1/approvals",
                new ApprovalSubmitRequest().setDefinitionId(definition.id())
                        .setSubjectId(hiddenCustomer.getId()).setIdempotencyKey("hidden-submit")
                        .setTitle("Hidden approval")
                        .setFacts(Map.of(field.getCode(), List.of("ready"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ApprovalInstanceResponse.class);
        mockMvc.perform(withTestActor(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/customers/{customerId}", hiddenCustomer.getId())
                        .param("version", hiddenCustomer.getVersion().toString())))
                .andExpect(status().isOk());

        performPost("/api/v1/approvals/query", new ApprovalQueryRequest()
                .setBucket("SUBMITTED").setPage(1).setSize(1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNum").value(1))
                .andExpect(jsonPath("$.objectList.length()").value(1))
                .andExpect(jsonPath("$.objectList[0].id").value(approval.id()))
                .andExpect(jsonPath("$.objectList[0].tasks").doesNotExist())
                .andExpect(jsonPath("$.objectList[0].watcherActorIds").doesNotExist());
        performPost("/api/v1/approvals/query", new ApprovalQueryRequest()
                .setBucket("PENDING").setPage(1).setSize(1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNum").value(0))
                .andExpect(jsonPath("$.objectList.length()").value(0));
        performGet("/api/v1/approvals/pending-count")
                .andExpect(status().isOk()).andExpect(jsonPath("$.count").value(0));
        performPost("/api/v1/approvals/" + hiddenApproval.id() + "/actions",
                new ApprovalActionRequest().setVersion(hiddenApproval.version())
                        .setAction("APPROVE"))
                .andExpect(status().isNotFound());
        performPost("/api/v1/approvals/actions/batch",
                new ApprovalBatchActionRequest().setActions(List.of(
                        new ApprovalActionItemRequest().setInstanceId(hiddenApproval.id())
                                .setVersion(hiddenApproval.version()).setAction("APPROVE"))))
                .andExpect(status().isNotFound());
        mockMvc.perform(withTestActor(post("/api/v1/approvals/{instanceId}/withdraw",
                        hiddenApproval.id()).param("version", hiddenApproval.version().toString())))
                .andExpect(status().isNotFound());
        mockMvc.perform(withTestActor(post("/api/v1/approvals/{instanceId}/cancel",
                        hiddenApproval.id()).param("version", hiddenApproval.version().toString())))
                .andExpect(status().isNotFound());

        ApprovalInstanceResponse batchApproval = fromJson(performPost("/api/v1/approvals",
                new ApprovalSubmitRequest().setDefinitionId(definition.id())
                        .setSubjectId(customer.getId()).setIdempotencyKey("batch-submit")
                        .setTitle("Batch approval")
                        .setFacts(Map.of(field.getCode(), List.of("ready"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ApprovalInstanceResponse.class);
        performPost("/api/v1/approvals/actions/batch",
                new ApprovalBatchActionRequest().setActions(List.of(
                        new ApprovalActionItemRequest().setInstanceId(batchApproval.id())
                                .setVersion(batchApproval.version()).setAction("APPROVE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.atomic").value(true))
                .andExpect(jsonPath("$.applied").value(true))
                .andExpect(jsonPath("$.items[0].instanceId").value(batchApproval.id()))
                .andExpect(jsonPath("$.items[0].status").value("APPLIED"))
                .andExpect(jsonPath("$.items[0].errCode").value("NONE"));

        ApprovalDefinitionResponse inactiveDefinition = fromJson(performPost(
                "/api/v1/approval-definitions",
                definitionRequest(field.getCode()).setName("Inactive stage approval"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ApprovalDefinitionResponse.class);
        StageWorkflowRequest invalidTasks = workflowRequest(definition.id(), stageField.getCode())
                .setName("Invalid task workflow");
        invalidTasks.getStages().getFirst().setTasks(Collections.singletonList(null));
        performPost("/api/v1/stage-workflows", invalidTasks)
                .andExpect(status().isBadRequest());

        StageWorkflowMutationResult workflow = fromJson(performPost("/api/v1/stage-workflows",
                workflowRequest(definition.id(), stageField.getCode()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                StageWorkflowMutationResult.class);
        performPut("/api/v1/fields/objects/CUSTOMER/" + stageField.getId(),
                new FieldUpdateRequest().setVersion(stageField.getVersion())
                        .setDisplayName(stageField.getDisplayName()).setSearchable(false))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(withTestActor(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/fields/objects/CUSTOMER/{fieldId}", field.getId())
                        .param("version", field.getVersion().toString())))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(withTestActor(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/fields/objects/CUSTOMER/{fieldId}", stageField.getId())
                        .param("version", stageField.getVersion().toString())))
                .andExpect(status().isUnprocessableEntity());
        workflow = fromJson(performPut("/api/v1/stage-workflows/" + workflow.id() + "/status",
                new StageWorkflowStatusRequest().setVersion(workflow.version())
                        .setStatus("ACTIVE"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                StageWorkflowMutationResult.class);
        performPut("/api/v1/stage-workflows/" + workflow.id(),
                workflowRequest(inactiveDefinition.id(), stageField.getCode())
                        .setVersion(workflow.version()))
                .andExpect(status().isBadRequest());
        StageWorkflowRecordResponse record = fromJson(performPost(
                "/api/v1/stage-workflows/" + workflow.id() + "/records/" + customer.getId(),
                Map.of()).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                StageWorkflowRecordResponse.class);
        performPut("/api/v1/stage-workflows/" + workflow.id() + "/status",
                new StageWorkflowStatusRequest().setVersion(workflow.version())
                        .setStatus("DELETED"))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(withTestActor(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/fields/objects/CUSTOMER/{fieldId}", stageField.getId())
                        .param("version", stageField.getVersion().toString())))
                .andExpect(status().isUnprocessableEntity());
        performPut("/api/v1/approval-definitions/" + definition.id() + "/status",
                new ApprovalDefinitionStatusRequest().setVersion(definition.version())
                        .setStatus("INACTIVE"))
                .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                        "/api/v1/stage-workflows/records/{recordId}/advance", record.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new StageAdvanceRequest().setVersion(record.version())
                                .setFieldValues(Map.of(stageField.getCode(), List.of("ready")))))
                        .header("X-Dev-User-Id", POLICY_ACTOR_ID)
                        .header("X-Dev-Tenant-Id", TENANT_ID))
                .andExpect(status().isForbidden());

        performPost("/api/v1/approvals", new ApprovalSubmitRequest()
                .setDefinitionId(definition.id()).setSubjectId(customer.getId())
                .setIdempotencyKey("stage:" + record.id() + ":review:1")
                .setTitle("Reserved stage approval")
                .setFacts(Map.of(field.getCode(), List.of("ready"))))
                .andExpect(status().isBadRequest());
        performPut("/api/v1/stage-workflows/records/" + record.id() + "/advance",
                new StageAdvanceRequest().setVersion(record.version())
                        .setFieldValues(Map.of(stageField.getCode(),
                                Collections.singletonList(null))))
                .andExpect(status().isBadRequest());
        performPut("/api/v1/stage-workflows/records/" + record.id() + "/advance",
                new StageAdvanceRequest().setVersion(record.version())
                        .setFieldValues(Map.of(field.getCode(), List.of("ready"),
                                stageField.getCode(), List.of())))
                .andExpect(status().isBadRequest());

        record = actionRecord(performPut("/api/v1/stage-workflows/records/" + record.id() + "/advance",
                new StageAdvanceRequest().setVersion(record.version())
                        .setFieldValues(Map.of(stageField.getCode(), List.of("ready"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals("AWAITING_APPROVAL", record.status());
        assertNotNull(record.pendingApprovalInstanceId());
        ApprovalInstanceResponse gate = fromJson(performGet(
                "/api/v1/approvals/{instanceId}", record.pendingApprovalInstanceId())
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ApprovalInstanceResponse.class);
        ApprovalInstanceResponse maskedGate = fromJson(mockMvc.perform(get(
                        "/api/v1/approvals/{instanceId}", gate.id())
                        .header("X-Dev-User-Id", POLICY_ACTOR_ID)
                        .header("X-Dev-Tenant-Id", TENANT_ID))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ApprovalInstanceResponse.class);
        assertEquals(List.of("CANCEL"), maskedGate.allowedActions());
        mockMvc.perform(post("/api/v1/approvals/{instanceId}/actions", gate.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new ApprovalActionRequest().setVersion(gate.version())
                                .setAction("REJECT")))
                        .header("X-Dev-User-Id", POLICY_ACTOR_ID)
                        .header("X-Dev-Tenant-Id", TENANT_ID))
                .andExpect(status().isUnprocessableEntity());
        StageWorkflowRecordResponse maskedRecord = fromJson(mockMvc.perform(get(
                        "/api/v1/stage-workflow-records/{workflowRecordId}", record.id())
                        .header("X-Dev-User-Id", POLICY_ACTOR_ID)
                        .header("X-Dev-Tenant-Id", TENANT_ID))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                StageWorkflowRecordResponse.class);
        assertEquals(List.of("r***y"), maskedRecord.progress().getFirst()
                .fieldValues().get(stageField.getCode()));
        assertEquals(false, maskedRecord.progress().getFirst()
                .fieldValues().containsKey(hiddenField.getCode()));
        performPost("/api/v1/approvals/" + gate.id() + "/actions",
                new ApprovalActionRequest().setVersion(gate.version()).setAction("REJECT"))
                .andExpect(status().isOk());
        record = actionRecord(performPut("/api/v1/stage-workflows/records/" + record.id() + "/advance",
                new StageAdvanceRequest().setVersion(record.version()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals("ACTIVE", record.status());
        assertNull(record.pendingApprovalInstanceId());
        assertEquals(1, record.progress().getFirst().approvalAttempt());

        record = retryStageApproval(record, field, stageField, hiddenField);
        assertEquals(2, record.progress().getFirst().approvalAttempt());
        ApprovalInstanceResponse cancelledGate = approval(record.pendingApprovalInstanceId());
        mockMvc.perform(withTestActor(post("/api/v1/approvals/{instanceId}/cancel",
                        cancelledGate.id()).param("version", cancelledGate.version().toString())))
                .andExpect(status().isOk());
        record = consumeStageApproval(record);
        assertEquals("ACTIVE", record.status());
        assertNull(record.pendingApprovalInstanceId());
        assertEquals(2, record.progress().getFirst().approvalAttempt());

        record = retryStageApproval(record, field, stageField, hiddenField);
        assertEquals(3, record.progress().getFirst().approvalAttempt());
        ApprovalInstanceResponse withdrawnGate = approval(record.pendingApprovalInstanceId());
        mockMvc.perform(withTestActor(post("/api/v1/approvals/{instanceId}/withdraw",
                        withdrawnGate.id()).param("version", withdrawnGate.version().toString())))
                .andExpect(status().isOk());
        record = consumeStageApproval(record);
        assertEquals("ACTIVE", record.status());
        assertNull(record.pendingApprovalInstanceId());
        assertEquals(3, record.progress().getFirst().approvalAttempt());

        record = retryStageApproval(record, field, stageField, hiddenField);
        assertEquals(4, record.progress().getFirst().approvalAttempt());
        ApprovalInstanceResponse approvedGate = approval(record.pendingApprovalInstanceId());
        performPost("/api/v1/approvals/" + approvedGate.id() + "/actions",
                new ApprovalActionRequest().setVersion(approvedGate.version())
                        .setAction("APPROVE"))
                .andExpect(status().isOk());
        record = consumeStageApproval(record);
        assertEquals("SUCCEEDED", record.status());

        StageWorkflowCommentResponse comment = fromJson(performPost(
                "/api/v1/stage-workflows/records/" + record.id() + "/comments",
                new StageCommentCreateRequest().setContent("Completed"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                StageWorkflowCommentResponse.class);
        performGet("/api/v1/stage-workflows/records/{recordId}/comments", record.id())
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalNum").value(1));
        mockMvc.perform(withTestActor(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/stage-workflows/records/{recordId}/comments/{commentId}",
                                record.id(), comment.id())
                        .param("version", comment.version().toString())))
                .andExpect(status().isOk());

        bootstrap(OTHER_ACTOR_ID, OTHER_TENANT_ID, "Other workflow owner");
        mockMvc.perform(get("/api/v1/approvals/{instanceId}", approval.id())
                        .header("X-Dev-User-Id", OTHER_ACTOR_ID)
                        .header("X-Dev-Tenant-Id", OTHER_TENANT_ID))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/stage-workflows/records/{recordId}", record.id())
                        .header("X-Dev-User-Id", OTHER_ACTOR_ID)
                        .header("X-Dev-Tenant-Id", OTHER_TENANT_ID))
                .andExpect(status().isNotFound());

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertEquals(7, jdbc.queryForObject(
                "SELECT COUNT(*) FROM approval_instance WHERE tenant_id = ?",
                Integer.class, TENANT_ID));
        assertEquals(7, jdbc.queryForObject(
                "SELECT COUNT(*) FROM approval_instance_manager WHERE tenant_id = ?",
                Integer.class, TENANT_ID));
        assertEquals(3, jdbc.queryForObject(
                "SELECT COUNT(*) FROM approval_review_task WHERE tenant_id = ? AND status = 'APPROVED'",
                Integer.class, TENANT_ID));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM approval_review_task WHERE tenant_id = ? AND status = 'PENDING'",
                Integer.class, TENANT_ID));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM stage_workflow_record WHERE tenant_id = ? AND status = 'SUCCEEDED'",
                Integer.class, TENANT_ID));
    }

    @Test
    void startAndStageOptionsExposeServerScopedCandidatesAndRenderableFields() throws Exception {
        bootstrap();
        seedActiveMember(POLICY_ACTOR_ID, "Selectable reviewer");
        FieldDefinitionResponse stageField = fromJson(performPost(
                "/api/v1/fields/objects/CUSTOMER", new FieldCreateRequest()
                        .setDisplayName("Stage decision").setFieldType("SELECT").setSearchable(true)
                        .setOptions(List.of(new FieldOptionRequest().setValue("READY")
                                .setLabel("Ready"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                FieldDefinitionResponse.class);
        CustomerResponse customer = fromJson(performPost("/api/v1/customers",
                new CustomerCreateRequest().setName("Candidate contract")
                        .setCustomFields(Map.of(stageField.getCode(), List.of("READY"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                CustomerResponse.class);
        ApprovalDefinitionRequest selectionDefinition = new ApprovalDefinitionRequest()
                .setObjectType("CUSTOMER").setName("Applicant selection")
                .setRepeatMode("ONCE").setInitiatorActorIds(List.of(ACTOR_ID))
                .setManagerActorIds(List.of(ACTOR_ID)).setSteps(List.of(
                        new ApprovalStepRequest().setCode("selected-reviewer")
                                .setName("Selected reviewer").setOrder(0)
                                .setType("APPLICANT_SELECT").setMode("ALL")
                                .setFallback("AUTO_APPROVE")));
        ApprovalDefinitionResponse definition = fromJson(performPost(
                "/api/v1/approval-definitions", selectionDefinition)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ApprovalDefinitionResponse.class);
        definition = fromJson(performPut("/api/v1/approval-definitions/" + definition.id()
                        + "/status", new ApprovalDefinitionStatusRequest()
                        .setVersion(definition.version()).setStatus("ACTIVE"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ApprovalDefinitionResponse.class);

        String candidatePath = "/api/v1/approvals/subjects/CUSTOMER/" + customer.getId()
                + "/start-options/" + definition.id()
                + "/selections/selected-reviewer/candidates";
        performGet(candidatePath).andExpect(status().isOk())
                .andExpect(jsonPath("$.objectList[?(@.actorId == " + POLICY_ACTOR_ID + ")]")
                        .isNotEmpty());
        performPost("/api/v1/approvals", new ApprovalSubmitRequest()
                .setDefinitionId(definition.id()).setSubjectId(customer.getId())
                .setIdempotencyKey("invalid-selection")
                .setSelectedApprovers(Map.of("selected-reviewer", List.of(123456789L))))
                .andExpect(status().isUnprocessableEntity());

        StageWorkflowMutationResult workflow = fromJson(performPost("/api/v1/stage-workflows",
                new StageWorkflowRequest().setObjectType("CUSTOMER").setName("Form workflow")
                        .setSuccessName("Done").setFailureName("Stopped")
                        .setApplicableActorIds(List.of(ACTOR_ID)).setStages(List.of(
                                new StageDefinitionRequest().setCode("form")
                                        .setName("Form").setOrder(0).setProbability(50)
                                        .setRequiredFieldCodes(List.of(stageField.getCode()))
                                        .setApprovalDefinitionId(definition.id()))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                StageWorkflowMutationResult.class);
        workflow = fromJson(performPut("/api/v1/stage-workflows/" + workflow.id() + "/status",
                new StageWorkflowStatusRequest().setVersion(workflow.version()).setStatus("ACTIVE"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                StageWorkflowMutationResult.class);
        performGet("/api/v1/stage-workflows/applicable?objectType=CUSTOMER&recordId="
                        + customer.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.objectType").value("CUSTOMER"))
                .andExpect(jsonPath("$.recordId").value(customer.getId()))
                .andExpect(jsonPath("$.workflows[0].workflowId").value(workflow.id()))
                .andExpect(jsonPath("$.workflows[0].canStart").value(true))
                .andExpect(jsonPath("$.workflows[0].blockerCode").doesNotExist());
        StageWorkflowRecordResponse record = fromJson(performPost(
                "/api/v1/stage-workflows/" + workflow.id() + "/records/" + customer.getId(), Map.of())
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                StageWorkflowRecordResponse.class);
        performGet("/api/v1/stage-workflow-records/" + record.id()).andExpect(status().isOk())
                .andExpect(jsonPath("$.writableFields[0].fieldCode").value(stageField.getCode()))
                .andExpect(jsonPath("$.writableFields[0].displayName").value("Stage decision"))
                .andExpect(jsonPath("$.writableFields[0].fieldType").value("SELECT"))
                .andExpect(jsonPath("$.writableFields[0].required").value(true))
                .andExpect(jsonPath("$.writableFields[0].allowedValues[0].value").value("READY"))
                .andExpect(jsonPath("$.writableFields[0].currentValues[0]").value("READY"))
                .andExpect(jsonPath("$.writableFields[0].input.inputType").value("OPTION_VALUES"))
                .andExpect(jsonPath("$.writableFields[0].masked").value(false))
                .andExpect(jsonPath("$.allowedOutcomes[0].code").value("FAILED"))
                .andExpect(jsonPath("$.allowedOutcomes[0].remarkMaxLength").value(1000))
                .andExpect(jsonPath("$.approvalGate.approverSelections[0].code")
                        .value("selected-reviewer"));
        performGet("/api/v1/stage-workflow-records/" + record.id()
                + "/advance-options/selected-reviewer/candidates").andExpect(status().isOk())
                .andExpect(jsonPath("$.objectList[?(@.actorId == " + POLICY_ACTOR_ID + ")]")
                        .isNotEmpty());
        performPut("/api/v1/stage-workflow-records/" + record.id() + "/advance",
                new StageAdvanceRequest().setVersion(record.version())
                        .setFieldValues(Map.of(stageField.getCode(), List.of("READY")))
                        .setSelectedApprovers(Map.of("selected-reviewer", List.of(POLICY_ACTOR_ID))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.record.status")
                        .value("AWAITING_APPROVAL"));
    }

    private ApprovalInstanceResponse approval(Long instanceId) throws Exception {
        return fromJson(performGet("/api/v1/approvals/{instanceId}", instanceId)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ApprovalInstanceResponse.class);
    }

    private ApprovalInstanceResponse actionResult(String response) {
        return fromJson(response, ApprovalActionResult.class).instance();
    }

    private StageWorkflowRecordResponse actionRecord(String response) {
        return fromJson(response, StageWorkflowActionResult.class).record();
    }

    private StageWorkflowRecordResponse consumeStageApproval(
            StageWorkflowRecordResponse record) throws Exception {
        return actionRecord(performPut("/api/v1/stage-workflows/records/" + record.id()
                        + "/advance", new StageAdvanceRequest().setVersion(record.version()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private StageWorkflowRecordResponse retryStageApproval(
            StageWorkflowRecordResponse record,
            FieldDefinitionResponse field,
            FieldDefinitionResponse stageField,
            FieldDefinitionResponse hiddenField) throws Exception {
        return actionRecord(performPut("/api/v1/stage-workflows/records/" + record.id()
                        + "/advance", new StageAdvanceRequest().setVersion(record.version())
                        .setFieldValues(Map.of(stageField.getCode(), List.of("ready"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private ApprovalDefinitionRequest definitionRequest(String fieldCode) {
        return new ApprovalDefinitionRequest().setObjectType("CUSTOMER")
                .setName("Customer approval").setRepeatMode("ONCE")
                .setInitiatorActorIds(List.of(ACTOR_ID)).setManagerActorIds(List.of(ACTOR_ID))
                .setSteps(List.of(new ApprovalStepRequest().setCode("owner-review")
                        .setName("Owner review").setOrder(0).setType("MEMBER")
                        .setMode("ALL").setFallback("MANAGERS")
                        .setMemberActorIds(List.of(ACTOR_ID))
                        .setConditions(List.of(new ApprovalConditionRequest()
                                .setFieldCode(fieldCode).setOperator("EQ")
                                .setValues(List.of("ready"))))));
    }

    private StageWorkflowRequest workflowRequest(
            Long approvalDefinitionId, String fieldCode) {
        return new StageWorkflowRequest().setObjectType("CUSTOMER").setName("Customer stages")
                .setSuccessName("Completed").setFailureName("Stopped")
                .setApplicableActorIds(List.of(ACTOR_ID))
                .setStages(List.of(new StageDefinitionRequest().setCode("review")
                        .setName("Review").setOrder(0).setProbability(50)
                        .setRequiredFieldCodes(List.of(fieldCode))
                        .setApprovalDefinitionId(approvalDefinitionId)));
    }

    private void bootstrap() throws Exception {
        bootstrap(ACTOR_ID, TENANT_ID, "Workflow owner");
    }

    private void bootstrap(long actorId, long tenantId, String displayName) throws Exception {
        mockMvc.perform(post("/api/v1/organization/bootstrap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new OrganizationBootstrapRequest()
                                .setDisplayName(displayName)))
                        .header("X-Dev-User-Id", actorId)
                        .header("X-Dev-Tenant-Id", tenantId))
                .andExpect(status().isOk());
    }

    private RoleGrantRequest grant(String authority, String scope) {
        return new RoleGrantRequest().setAuthorityCode(authority).setDataScope(scope);
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

    @Override
    protected void initTestData() {
        clearWorkflowData();
    }

    @Override
    protected void clearTestData() {
        clearWorkflowData();
    }

    private void clearWorkflowData() {
        executeSql("DELETE FROM stage_workflow_comment");
        executeSql("DELETE FROM stage_workflow_record");
        executeSql("DELETE FROM stage_workflow_definition");
        executeSql("DELETE FROM approval_review_task");
        executeSql("DELETE FROM approval_instance_manager");
        executeSql("DELETE FROM approval_instance_watcher");
        executeSql("DELETE FROM approval_instance");
        executeSql("DELETE FROM approval_definition");
        executeSql("DELETE FROM crm_customer_star");
        executeSql("DELETE FROM crm_customer_unique_field_value");
        executeSql("DELETE FROM crm_customer_field_value");
        executeSql("DELETE FROM crm_customer_collaborator");
        executeSql("DELETE FROM crm_customer_tag");
        executeSql("DELETE FROM crm_customer_address");
        executeSql("UPDATE crm_customer SET parent_customer_id = NULL");
        executeSql("DELETE FROM crm_customer");
        executeSql("DELETE FROM crm_customer_guard");
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
