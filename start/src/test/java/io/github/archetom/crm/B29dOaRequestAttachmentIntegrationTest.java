package io.github.archetom.crm;

import io.github.archetom.crm.api.dto.request.ApprovalActionItemRequest;
import io.github.archetom.crm.api.dto.request.ApprovalActionRequest;
import io.github.archetom.crm.api.dto.request.ApprovalBatchActionRequest;
import io.github.archetom.crm.api.dto.request.ApprovalConditionRequest;
import io.github.archetom.crm.api.dto.request.ApprovalDefinitionRequest;
import io.github.archetom.crm.api.dto.request.ApprovalDefinitionStatusRequest;
import io.github.archetom.crm.api.dto.request.ApprovalStepRequest;
import io.github.archetom.crm.api.dto.request.AttachmentUploadIntentCreateRequest;
import io.github.archetom.crm.api.dto.request.OaFormCreateRequest;
import io.github.archetom.crm.api.dto.request.OaFormFieldRequest;
import io.github.archetom.crm.api.dto.request.OaFormStatusUpdateRequest;
import io.github.archetom.crm.api.dto.request.OaRequestSubmitRequest;
import io.github.archetom.crm.api.dto.request.OrganizationBootstrapRequest;
import io.github.archetom.crm.api.dto.response.ApprovalDefinitionResponse;
import io.github.archetom.crm.api.dto.response.ApprovalInstanceResponse;
import io.github.archetom.crm.api.dto.response.ApprovalActionResult;
import io.github.archetom.crm.api.dto.response.AttachmentUploadIntentResponse;
import io.github.archetom.crm.api.dto.response.OaFormResponse;
import io.github.archetom.crm.api.dto.response.OaRequestResponse;
import io.github.archetom.crm.application.service.attachment.OaRequestAttachmentTerminalCoordinator;
import io.github.archetom.crm.application.service.attachment.worker.AttachmentInspectionWorker;
import io.github.archetom.crm.application.service.attachment.worker.AttachmentPublicationWorker;
import io.github.archetom.crm.domain.entity.ApprovalInstance;
import io.github.archetom.crm.domain.model.ApprovalInstanceStatus;
import io.github.archetom.crm.domain.repository.ApprovalInstanceRepository;
import io.github.archetom.crm.infra.external.attachment.SecureLocalAttachmentConformanceContract;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B29d MySQL acceptance: V84 typed ownership, current-collaboration matrix and the shared
 * approval-instance -> request -> attachment terminal order.
 */
@RequiresDatabase
@Import(B24aWorkTaskAttachmentIntegrationTest.PositiveAttachmentProfile.class)
class B29dOaRequestAttachmentIntegrationTest extends BaseIntegrationTest {
    private static final long TENANT = 9_290_841L;
    private static final long APPLICANT = TENANT;
    private static final long FIRST_ASSIGNEE = TENANT + 1;
    private static final long SECOND_ASSIGNEE = TENANT + 2;
    private static final long INSTANCE_MANAGER = TENANT + 3;
    private static final long WATCHER = TENANT + 4;
    private static final long OA_MANAGER = TENANT + 5;
    private static final byte[] PDF = "%PDF-1.7\nB29d".getBytes(StandardCharsets.US_ASCII);

    @Autowired private AttachmentInspectionWorker inspectionWorker;
    @Autowired private AttachmentPublicationWorker publicationWorker;
    @MockitoSpyBean private OaRequestAttachmentTerminalCoordinator terminalCoordinator;
    @MockitoSpyBean private ApprovalInstanceRepository approvalInstances;

    @DynamicPropertySource
    static void attachmentProfile(DynamicPropertyRegistry registry) {
        registry.add("atom.files.enabled", () -> "true");
        registry.add("atom.files.workers.enabled", () -> "false");
        registry.add("atom.files.storage-profile", SecureLocalAttachmentConformanceContract::profile);
        registry.add("atom.files.secure-local-root", () -> B24aWorkTaskAttachmentIntegrationTest.ROOT.toString());
        registry.add("atom.files.binding-base64", () -> Base64.getUrlEncoder().withoutPadding()
                .encodeToString(B24aWorkTaskAttachmentIntegrationTest.BINDING));
        registry.add("atom.files.inspection.detector-profile", () -> "BOUNDED_MAGIC_V1");
        registry.add("atom.files.inspection.scanner-profile", () -> "STREAMING_ENGINE_V1");
        registry.add("atom.files.inspection.detector-catalog-revision", () -> "magic-r1");
        registry.add("atom.files.inspection.allowlist-revision", () -> "public-r1");
        registry.add("atom.files.inspection.definition-max-age", () -> "PT30S");
        registry.add("atom.files.policy.max-file-bytes", () -> "1048576");
        registry.add("atom.files.policy.max-ready-bytes", () -> "2097152");
        registry.add("atom.files.policy.max-quarantine-bytes", () -> "2097152");
        registry.add("atom.files.policy.max-pending-intents", () -> "128");
        registry.add("atom.files.policy.max-active-uploads", () -> "8");
        registry.add("atom.files.policy.max-active-inspections", () -> "2");
        registry.add("atom.files.policy.max-active-downloads", () -> "2");
        registry.add("atom.files.policy.global-reserve-bytes", () -> "4194304");
        registry.add("atom.files.policy.deletion-control-reserve", () -> "1");
        registry.add("atom.files.policy.local-free-floor-bytes", () -> "4096");
        registry.add("atom.files.policy.intent-ttl", () -> "PT30S");
        registry.add("atom.files.policy.upload-open-timeout", () -> "PT1S");
        registry.add("atom.files.policy.upload-idle-timeout", () -> "PT1S");
        registry.add("atom.files.policy.upload-total-timeout", () -> "PT10S");
        registry.add("atom.files.policy.inspection-total-timeout", () -> "PT10S");
        registry.add("atom.files.policy.download-open-timeout", () -> "PT1S");
        registry.add("atom.files.policy.download-idle-timeout", () -> "PT1S");
        registry.add("atom.files.policy.download-total-timeout", () -> "PT10S");
        registry.add("atom.files.policy.job-lease", () -> "PT4S");
        registry.add("atom.files.policy.job-heartbeat", () -> "PT1S");
        registry.add("atom.files.policy.max-attempts", () -> "3");
        registry.add("atom.files.policy.quarantine-retention", () -> "PT2S");
        registry.add("atom.files.policy.tombstone-retention", () -> "PT8S");
        registry.add("atom.files.policy.claim-size", () -> "1");
    }

    @Test
    void v84CurrentCollaboratorsFreezeTerminalCandidatesAndDefeatRacingUploadWorker() throws Exception {
        Fixture fixture = fixture();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        OaRequestResponse request = submit(fixture.form(), "b29d-terminal", List.of(WATCHER));
        assertV84(jdbc, request);
        String digest = jdbc.queryForObject("SELECT submission_digest FROM oa_request"
                + " WHERE tenant_id = ? AND id = ?", String.class, TENANT, request.id());
        String facts = jdbc.queryForObject("SELECT facts_json FROM approval_instance"
                + " WHERE tenant_id = ? AND id = ?", String.class, TENANT, request.approvalInstanceId());
        String route = jdbc.queryForObject("SELECT GROUP_CONCAT(assigned_actor_id ORDER BY step_order, task_key)"
                + " FROM approval_review_task WHERE tenant_id = ? AND instance_id = ?", String.class,
                TENANT, request.approvalInstanceId());

        AttachmentUploadIntentResponse available = intent(APPLICANT, request, "available.pdf");
        upload(APPLICANT, request, available, PDF, 202);
        inspectionWorker.runOnce();
        publicationWorker.runOnce();
        assertEquals("AVAILABLE", filePublication(jdbc, available.getFileId()));

        AttachmentUploadIntentResponse privateCandidate = intent(APPLICANT, request, "private.pdf");
        upload(APPLICANT, request, privateCandidate, PDF, 202);
        inspectionWorker.runOnce();
        assertEquals("PRIVATE", filePublication(jdbc, privateCandidate.getFileId()));

        // Applicant, the current assignee and the immutable instance manager can collaborate.
        AttachmentUploadIntentResponse managerCandidate = intent(INSTANCE_MANAGER, request, "manager.pdf");
        AttachmentUploadIntentResponse firstCandidate = intent(FIRST_ASSIGNEE, request, "first.pdf");
        as(WATCHER, TENANT, post(root(request) + "/upload-intents")
                .contentType(MediaType.APPLICATION_JSON).content(toJson(intentRequest("watcher.pdf"))))
                .andExpect(status().isNotFound());
        as(OA_MANAGER, TENANT, post(root(request) + "/upload-intents")
                .contentType(MediaType.APPLICATION_JSON).content(toJson(intentRequest("oa-manager.pdf"))))
                .andExpect(status().isNotFound());
        as(WATCHER, TENANT, get("/api/v1/oa/requests/{id}", request.id()))
                .andExpect(status().isOk());
        as(OA_MANAGER, TENANT, get("/api/v1/oa/requests")
                .param("page", "1").param("size", "20"))
                .andExpect(status().isOk());

        ApprovalInstanceResponse afterFirst = action(FIRST_ASSIGNEE, request.approvalInstanceId(),
                instance(APPLICANT, request.approvalInstanceId()).version());
        as(FIRST_ASSIGNEE, TENANT, post(root(request) + "/upload-intents")
                .contentType(MediaType.APPLICATION_JSON).content(toJson(intentRequest("historical.pdf"))))
                .andExpect(status().isNotFound());
        AttachmentUploadIntentResponse racing = intent(SECOND_ASSIGNEE, request, "racing.pdf");

        CountDownLatch terminalCutoffEntered = new CountDownLatch(1);
        CountDownLatch releaseTerminal = new CountDownLatch(1);
        doAnswer(invocation -> {
            ApprovalInstance instance = invocation.getArgument(1);
            if (instance.getStatus() != ApprovalInstanceStatus.PENDING) {
                terminalCutoffEntered.countDown();
                if (!releaseTerminal.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("timed out while holding B29d terminal lock");
                }
            }
            return invocation.callRealMethod();
        }).when(terminalCoordinator).cutoffIfTerminal(any(), any());
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> terminal = executor.submit(() -> as(SECOND_ASSIGNEE, TENANT,
                    post("/api/v1/approvals/{id}/actions", request.approvalInstanceId())
                            .contentType(MediaType.APPLICATION_JSON).content(toJson(
                                    new ApprovalActionRequest().setVersion(afterFirst.version())
                                            .setAction("APPROVE"))))
                    .andReturn().getResponse().getStatus());
            assertTrue(terminalCutoffEntered.await(5, TimeUnit.SECONDS));
            Future<Integer> upload = executor.submit(() -> as(SECOND_ASSIGNEE, TENANT,
                    put(root(request) + "/{fileId}/content", racing.getFileId())
                            .param("version", racing.getVersion().toString())
                            .contentType(MediaType.APPLICATION_OCTET_STREAM).content(PDF))
                    .andReturn().getResponse().getStatus());
            assertThrows(java.util.concurrent.TimeoutException.class,
                    () -> upload.get(200, TimeUnit.MILLISECONDS));
            releaseTerminal.countDown();
            assertEquals(200, terminal.get(5, TimeUnit.SECONDS));
            assertEquals(404, upload.get(5, TimeUnit.SECONDS));
        } finally {
            releaseTerminal.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
            reset(terminalCoordinator);
        }

        assertEquals("APPROVED", jdbc.queryForObject("SELECT status FROM approval_instance"
                + " WHERE tenant_id = ? AND id = ?", String.class, TENANT, request.approvalInstanceId()));
        assertEquals("ACTIVE", relationState(jdbc, available.getFileId()));
        assertEquals("AVAILABLE", filePublication(jdbc, available.getFileId()));
        for (long fileId : List.of(privateCandidate.getFileId(), managerCandidate.getFileId(),
                firstCandidate.getFileId(), racing.getFileId())) {
            assertEquals("WITHDRAWN", relationState(jdbc, fileId));
            assertEquals("WITHDRAWN", filePublication(jdbc, fileId));
        }
        publicationWorker.runOnce();
        assertEquals("WITHDRAWN", filePublication(jdbc, privateCandidate.getFileId()));
        assertEquals(PDF.length, completeAsync(as(APPLICANT, TENANT,
                get(root(request) + "/{fileId}/content", available.getFileId()))
                .andExpect(status().isOk())).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsByteArray().length);
        as(APPLICANT, TENANT, post(root(request) + "/upload-intents")
                .contentType(MediaType.APPLICATION_JSON).content(toJson(intentRequest("late.pdf"))))
                .andExpect(status().isNotFound());
        assertEquals(digest, jdbc.queryForObject("SELECT submission_digest FROM oa_request"
                + " WHERE tenant_id = ? AND id = ?", String.class, TENANT, request.id()));
        assertEquals(facts, jdbc.queryForObject("SELECT facts_json FROM approval_instance"
                + " WHERE tenant_id = ? AND id = ?", String.class, TENANT, request.approvalInstanceId()));
        assertEquals(route, jdbc.queryForObject("SELECT GROUP_CONCAT(assigned_actor_id ORDER BY step_order, task_key)"
                + " FROM approval_review_task WHERE tenant_id = ? AND instance_id = ?", String.class,
                TENANT, request.approvalInstanceId()));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM crm_oa_request_file_value"
                + " WHERE tenant_id = ? AND oa_request_id = ?", Integer.class, TENANT, request.id()));
    }

    @Test
    void firstAssigneeCannotUseThePreAdvanceTaskSnapshotWhileSecondAssigneeIsPending() throws Exception {
        Fixture fixture = fixture();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        OaRequestResponse request = submit(fixture.form(), "b29d-current-assignee", List.of());
        long initialVersion = instance(APPLICANT, request.approvalInstanceId()).version();
        CountDownLatch firstActionBeforePersist = new CountDownLatch(1);
        CountDownLatch releaseFirstAction = new CountDownLatch(1);
        CountDownLatch attachmentRootLockAttempt = new CountDownLatch(1);
        CountDownLatch releaseAttachmentRootLock = new CountDownLatch(1);
        AtomicInteger approvalRootLocks = new AtomicInteger();

        doAnswer(invocation -> {
            firstActionBeforePersist.countDown();
            if (!releaseFirstAction.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("timed out before persisting first approval action");
            }
            return invocation.callRealMethod();
        }).when(terminalCoordinator).cutoffIfTerminal(any(), any());
        doAnswer(invocation -> {
            if (approvalRootLocks.incrementAndGet() == 2) {
                attachmentRootLockAttempt.countDown();
                if (!releaseAttachmentRootLock.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("timed out before locking attachment approval root");
                }
            }
            return invocation.callRealMethod();
        }).when(approvalInstances).findByIdForUpdate(any(), any());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> firstAction = executor.submit(() -> as(FIRST_ASSIGNEE, TENANT,
                    post("/api/v1/approvals/{id}/actions", request.approvalInstanceId())
                            .contentType(MediaType.APPLICATION_JSON).content(toJson(
                                    new ApprovalActionRequest().setVersion(initialVersion).setAction("APPROVE"))))
                    .andReturn().getResponse().getStatus());
            assertTrue(firstActionBeforePersist.await(5, TimeUnit.SECONDS));

            Future<Integer> staleFirstAssigneeIntent = executor.submit(() -> as(FIRST_ASSIGNEE, TENANT,
                    post(root(request) + "/upload-intents")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(intentRequest("stale-first.pdf"))))
                    .andReturn().getResponse().getStatus());
            assertTrue(attachmentRootLockAttempt.await(5, TimeUnit.SECONDS));

            // The attachment's ordinary visibility read is complete because its next operation
            // is the intercepted root lock. Advance A -> B, then allow the attachment to use
            // the production locking/current-read path.
            releaseFirstAction.countDown();
            assertEquals(200, firstAction.get(5, TimeUnit.SECONDS));
            releaseAttachmentRootLock.countDown();
            assertEquals(404, staleFirstAssigneeIntent.get(5, TimeUnit.SECONDS));
        } finally {
            releaseAttachmentRootLock.countDown();
            releaseFirstAction.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
            reset(terminalCoordinator, approvalInstances);
        }

        assertEquals("PENDING", jdbc.queryForObject("SELECT status FROM approval_instance"
                + " WHERE tenant_id = ? AND id = ?", String.class, TENANT, request.approvalInstanceId()));
        assertEquals(SECOND_ASSIGNEE, jdbc.queryForObject("SELECT assigned_actor_id FROM approval_review_task"
                + " WHERE tenant_id = ? AND instance_id = ? AND status = 'PENDING'", Long.class,
                TENANT, request.approvalInstanceId()));
        assertEquals(201, as(SECOND_ASSIGNEE, TENANT, post(root(request) + "/upload-intents")
                .contentType(MediaType.APPLICATION_JSON).content(toJson(intentRequest("current-second.pdf"))))
                .andReturn().getResponse().getStatus());
    }

    @Test
    void batchWithdrawAndCancelApplyTheSameTerminalCutoff() throws Exception {
        Fixture fixture = fixture();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        OaRequestResponse batch = submit(fixture.form(), "b29d-batch", List.of());
        long batchCandidate = pendingCandidate(jdbc, batch.id(), "batch.pdf");
        ApprovalInstanceResponse batchFirst = action(FIRST_ASSIGNEE, batch.approvalInstanceId(),
                instance(APPLICANT, batch.approvalInstanceId()).version());
        as(SECOND_ASSIGNEE, TENANT, post("/api/v1/approvals/actions/batch")
                .contentType(MediaType.APPLICATION_JSON).content(toJson(new ApprovalBatchActionRequest()
                        .setActions(List.of(new ApprovalActionItemRequest()
                                .setInstanceId(batch.approvalInstanceId()).setVersion(batchFirst.version())
                                .setAction("APPROVE"))))))
                .andExpect(status().isOk());
        assertEquals("WITHDRAWN", relationState(jdbc, batchCandidate));

        OaRequestResponse withdrawn = submit(fixture.form(), "b29d-withdraw", List.of());
        long withdrawnCandidate = pendingCandidate(jdbc, withdrawn.id(), "withdraw.pdf");
        ApprovalInstanceResponse withdrawInstance = instance(APPLICANT, withdrawn.approvalInstanceId());
        as(APPLICANT, TENANT, post("/api/v1/approvals/{id}/withdraw", withdrawn.approvalInstanceId())
                .param("version", withdrawInstance.version().toString()))
                .andExpect(status().isOk());
        assertEquals("WITHDRAWN", relationState(jdbc, withdrawnCandidate));

        OaRequestResponse cancelled = submit(fixture.form(), "b29d-cancel", List.of());
        long cancelledCandidate = pendingCandidate(jdbc, cancelled.id(), "cancel.pdf");
        ApprovalInstanceResponse cancelInstance = instance(INSTANCE_MANAGER, cancelled.approvalInstanceId());
        as(INSTANCE_MANAGER, TENANT, post("/api/v1/approvals/{id}/cancel", cancelled.approvalInstanceId())
                .param("version", cancelInstance.version().toString()))
                .andExpect(status().isOk());
        assertEquals("WITHDRAWN", relationState(jdbc, cancelledCandidate));
    }

    private Fixture fixture() throws Exception {
        bootstrap(APPLICANT, TENANT, "B29d applicant");
        seedMember(FIRST_ASSIGNEE, "B29d first", collaboratorAuthorities(true));
        seedMember(SECOND_ASSIGNEE, "B29d second", collaboratorAuthorities(true));
        seedMember(INSTANCE_MANAGER, "B29d manager", collaboratorAuthorities(true));
        seedMember(WATCHER, "B29d watcher", collaboratorAuthorities(false));
        seedMember(OA_MANAGER, "B29d OA manager", List.of("files:read", "files:write",
                "oa-requests:read", "oa-requests:manage", "oa-requests:collaborate"));
        ApprovalDefinitionResponse definition = activateApproval(createApproval());
        return new Fixture(activateForm(createForm(definition.id())));
    }

    private List<String> collaboratorAuthorities(boolean review) {
        java.util.ArrayList<String> result = new java.util.ArrayList<>(List.of("files:read", "files:write",
                "oa-requests:read", "oa-requests:collaborate"));
        if (review) result.addAll(List.of("approvals:read", "approvals:review"));
        return result;
    }

    private OaRequestResponse submit(OaFormResponse form, String key, List<Long> watchers) throws Exception {
        return fromJson(as(APPLICANT, TENANT, post("/api/v1/oa/requests")
                .contentType(MediaType.APPLICATION_JSON).content(toJson(new OaRequestSubmitRequest()
                        .setFormId(form.getId()).setFormVersion(form.getVersion()).setIdempotencyKey(key)
                        .setValues(java.util.Map.of("request_title", List.of("Leave request")))
                        .setWatcherActorIds(watchers)))).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(), OaRequestResponse.class);
    }

    private ApprovalDefinitionResponse createApproval() throws Exception {
        ApprovalConditionRequest condition = new ApprovalConditionRequest().setFieldCode("request_title")
                .setOperator("EQ").setValues(List.of("Leave request"));
        ApprovalDefinitionRequest request = new ApprovalDefinitionRequest().setObjectType("OA_REQUEST")
                .setName("B29d approval").setRepeatMode("ONCE")
                .setManagerActorIds(List.of(INSTANCE_MANAGER)).setSteps(List.of(
                        step("first", "First", 0, FIRST_ASSIGNEE, condition),
                        step("second", "Second", 1, SECOND_ASSIGNEE, condition)));
        return fromJson(as(APPLICANT, TENANT, post("/api/v1/approval-definitions")
                .contentType(MediaType.APPLICATION_JSON).content(toJson(request))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), ApprovalDefinitionResponse.class);
    }

    private ApprovalStepRequest step(String code, String name, int order, long assignee,
                                     ApprovalConditionRequest condition) {
        return new ApprovalStepRequest().setCode(code).setName(name).setOrder(order).setType("MEMBER")
                .setMode("ALL").setFallback("MANAGERS").setMemberActorIds(List.of(assignee))
                .setConditions(List.of(condition));
    }

    private ApprovalDefinitionResponse activateApproval(ApprovalDefinitionResponse definition) throws Exception {
        return fromJson(as(APPLICANT, TENANT, put("/api/v1/approval-definitions/{id}/status", definition.id())
                .contentType(MediaType.APPLICATION_JSON).content(toJson(new ApprovalDefinitionStatusRequest()
                        .setVersion(definition.version()).setStatus("ACTIVE")))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), ApprovalDefinitionResponse.class);
    }

    private OaFormResponse createForm(long definitionId) throws Exception {
        return fromJson(as(APPLICANT, TENANT, post("/api/v1/oa/forms")
                .contentType(MediaType.APPLICATION_JSON).content(toJson(new OaFormCreateRequest().setCode("b29d")
                        .setName("B29d form").setApprovalDefinitionId(definitionId).setFields(List.of(
                                new OaFormFieldRequest().setFieldCode("request_title").setRequired(true)
                                        .setSortOrder(10)))))).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(), OaFormResponse.class);
    }

    private OaFormResponse activateForm(OaFormResponse form) throws Exception {
        return fromJson(as(APPLICANT, TENANT, put("/api/v1/oa/forms/{id}/status", form.getId())
                .contentType(MediaType.APPLICATION_JSON).content(toJson(new OaFormStatusUpdateRequest()
                        .setVersion(form.getVersion()).setStatus("ACTIVE")))).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString(), OaFormResponse.class);
    }

    private ApprovalInstanceResponse action(long actor, long instanceId, long version) throws Exception {
        ApprovalActionResult result = fromJson(as(actor, TENANT, post("/api/v1/approvals/{id}/actions", instanceId)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(new ApprovalActionRequest()
                        .setVersion(version).setAction("APPROVE")))).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString(), ApprovalActionResult.class);
        return result.instance();
    }

    private ApprovalInstanceResponse instance(long actor, long instanceId) throws Exception {
        return fromJson(as(actor, TENANT, get("/api/v1/approvals/{id}", instanceId))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ApprovalInstanceResponse.class);
    }

    private AttachmentUploadIntentResponse intent(long actor, OaRequestResponse request, String name)
            throws Exception {
        return fromJson(as(actor, TENANT, post(root(request) + "/upload-intents")
                .contentType(MediaType.APPLICATION_JSON).content(toJson(intentRequest(name))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(),
                AttachmentUploadIntentResponse.class);
    }

    private AttachmentUploadIntentCreateRequest intentRequest(String name) {
        return new AttachmentUploadIntentCreateRequest().setDisplayName(name)
                .setDeclaredMediaType("application/pdf").setExpectedBytes((long) PDF.length);
    }

    private void upload(long actor, OaRequestResponse request, AttachmentUploadIntentResponse intent,
                        byte[] bytes, int expectedStatus) throws Exception {
        assertEquals(expectedStatus, as(actor, TENANT, put(root(request) + "/{fileId}/content", intent.getFileId())
                .param("version", intent.getVersion().toString()).contentType(MediaType.APPLICATION_OCTET_STREAM)
                .content(bytes)).andReturn().getResponse().getStatus());
    }

    private void assertV84(JdbcTemplate jdbc, OaRequestResponse request) {
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history"
                + " WHERE version = '84' AND success = 1", Integer.class));
        String clause = jdbc.queryForObject("SELECT check_clause FROM information_schema.check_constraints"
                + " WHERE constraint_schema = DATABASE() AND constraint_name = 'chk_crm_attachment_relation_target'",
                String.class);
        assertTrue(clause.contains("OA_REQUEST"));
        long fileId = pendingFile(jdbc, "invalid-parent.pdf");
        assertThrows(RuntimeException.class, () -> jdbc.update(
                "INSERT INTO crm_attachment_relation (tenant_id, file_id, target_type, oa_request_id)"
                        + " VALUES (?, ?, 'OA_REQUEST', ?)", TENANT, fileId, request.id() + 9_999_999L));
        assertThrows(RuntimeException.class, () -> jdbc.update(
                "INSERT INTO crm_attachment_relation (tenant_id, file_id, target_type, oa_request_id, work_task_id)"
                        + " VALUES (?, ?, 'OA_REQUEST', ?, 1)", TENANT, fileId, request.id()));
    }

    private long pendingCandidate(JdbcTemplate jdbc, long requestId, String name) {
        long fileId = pendingFile(jdbc, name);
        jdbc.update("INSERT INTO crm_attachment_relation (tenant_id, file_id, target_type, oa_request_id)"
                + " VALUES (?, ?, 'OA_REQUEST', ?)", TENANT, fileId, requestId);
        return fileId;
    }

    private long pendingFile(JdbcTemplate jdbc, String name) {
        jdbc.update("INSERT INTO crm_attachment_file (tenant_id, uploader_actor_id, display_name, declared_media_type,"
                        + " expected_bytes, byte_state, inspection_state, publication_state)"
                        + " VALUES (?, ?, ?, 'application/pdf', 4, 'UPLOADING', 'NOT_RUN', 'PRIVATE')",
                TENANT, APPLICANT, name);
        return jdbc.queryForObject("SELECT MAX(id) FROM crm_attachment_file WHERE tenant_id = ?", Long.class, TENANT);
    }

    private String relationState(JdbcTemplate jdbc, long fileId) {
        return jdbc.queryForObject("SELECT relation_state FROM crm_attachment_relation"
                + " WHERE tenant_id = ? AND file_id = ?", String.class, TENANT, fileId);
    }

    private String filePublication(JdbcTemplate jdbc, long fileId) {
        return jdbc.queryForObject("SELECT publication_state FROM crm_attachment_file"
                + " WHERE tenant_id = ? AND id = ?", String.class, TENANT, fileId);
    }

    private void bootstrap(long actor, long tenant, String name) throws Exception {
        as(actor, tenant, post("/api/v1/organization/bootstrap").contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new OrganizationBootstrapRequest().setDisplayName(name))))
                .andExpect(status().isOk());
    }

    private void seedMember(long actor, String name, List<String> authorities) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO organization_member_profile (actor_id, display_name) VALUES (?, ?)", actor, name);
        jdbc.update("INSERT INTO organization_membership (tenant_id, actor_id, status) VALUES (?, ?, 'ACTIVE')",
                TENANT, actor);
        jdbc.update("INSERT INTO authorization_role (tenant_id, name, status) VALUES (?, ?, 'ACTIVE')", TENANT,
                name + " role");
        long roleId = jdbc.queryForObject("SELECT id FROM authorization_role WHERE tenant_id = ? AND name = ?",
                Long.class, TENANT, name + " role");
        jdbc.update("INSERT INTO authorization_role_assignment (tenant_id, actor_id, role_id) VALUES (?, ?, ?)",
                TENANT, actor, roleId);
        for (String authority : authorities) {
            jdbc.update("INSERT INTO authorization_role_grant (tenant_id, role_id, authority_code) VALUES (?, ?, ?)",
                    TENANT, roleId, authority);
        }
    }

    private String root(OaRequestResponse request) {
        return "/api/v1/attachment-targets/oa-request/" + request.id() + "/attachments";
    }

    private org.springframework.test.web.servlet.ResultActions as(
            long actor, long tenant, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header("X-Dev-User-Id", actor).header("X-Dev-Tenant-Id", tenant));
    }

    @Override
    protected void clearTestData() {
        executeSql("DELETE FROM crm_attachment_job WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM crm_attachment_read_lease WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM crm_attachment_content WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM crm_attachment_relation WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM crm_attachment_file WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM crm_attachment_tenant_usage WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM oa_request_unique_field_value WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM oa_request_field_value WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM oa_request WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM approval_review_task WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM approval_instance_manager WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM approval_instance_watcher WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM approval_instance WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM oa_form_field WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM oa_form_definition WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM approval_definition WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM role_field_access WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM role_field_policy WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM custom_field_definition WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM customization_object_guard WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM authorization_role_assignment WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM authorization_role_grant WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM authorization_role WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM organization_membership_request WHERE target_tenant_id = " + TENANT);
        executeSql("DELETE FROM organization_membership WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM organization_tenant WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM organization_member_profile WHERE actor_id BETWEEN " + APPLICANT + " AND "
                + OA_MANAGER);
    }

    private record Fixture(OaFormResponse form) {
    }
}
