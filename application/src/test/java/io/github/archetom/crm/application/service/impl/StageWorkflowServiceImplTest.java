package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.StageAdvanceRequest;
import io.github.archetom.crm.api.dto.request.StageDefinitionRequest;
import io.github.archetom.crm.api.dto.request.StageWorkflowRequest;
import io.github.archetom.crm.api.dto.request.StageWorkflowStatusRequest;
import io.github.archetom.crm.application.port.out.CustomizationObjectLock;
import io.github.archetom.crm.application.security.BusinessRecordAccessGuard;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.TestTransactionManager;
import io.github.archetom.crm.application.service.CustomerQueryService;
import io.github.archetom.crm.application.service.ContactQueryService;
import io.github.archetom.crm.application.service.LeadQueryService;
import io.github.archetom.crm.application.service.FileFieldBindingService;
import io.github.archetom.crm.application.service.FileFieldDraftService;
import io.github.archetom.crm.application.vo.StageWorkflowRecordVO;
import io.github.archetom.crm.application.vo.StageWorkflowVO;
import io.github.archetom.crm.domain.entity.ApprovalInstance;
import io.github.archetom.crm.domain.entity.ApprovalDefinition;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.FilterCondition;
import io.github.archetom.crm.domain.entity.OrganizationMembership;
import io.github.archetom.crm.domain.entity.StageWorkflowDefinition;
import io.github.archetom.crm.domain.entity.StageWorkflowRecord;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.ApprovalInstanceStatus;
import io.github.archetom.crm.domain.model.ApprovalDefinitionStatus;
import io.github.archetom.crm.domain.model.ApprovalFallback;
import io.github.archetom.crm.domain.model.ApprovalRepeatMode;
import io.github.archetom.crm.domain.model.ApprovalStepDefinition;
import io.github.archetom.crm.domain.model.ApprovalStepMode;
import io.github.archetom.crm.domain.model.ApprovalStepType;
import io.github.archetom.crm.domain.model.FilterOperator;
import io.github.archetom.crm.domain.model.StageDefinition;
import io.github.archetom.crm.domain.model.StageWorkflowStatus;
import io.github.archetom.crm.domain.repository.ApprovalDefinitionRepository;
import io.github.archetom.crm.domain.repository.ApprovalInstanceRepository;
import io.github.archetom.crm.domain.repository.FileFieldRepository;
import io.github.archetom.crm.domain.repository.MemberProfileRepository;
import io.github.archetom.crm.domain.repository.DepartmentRepository;
import io.github.archetom.crm.domain.repository.StageWorkflowCommentRepository;
import io.github.archetom.crm.domain.repository.StageWorkflowDefinitionRepository;
import io.github.archetom.crm.domain.repository.StageWorkflowRecordRepository;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.ApprovalDefinitionId;
import io.github.archetom.crm.domain.valueobject.ApprovalInstanceId;
import io.github.archetom.crm.domain.valueobject.StageWorkflowId;
import io.github.archetom.crm.domain.valueobject.StageWorkflowRecordId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.common.result.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static io.github.archetom.crm.domain.policy.AuthorityCatalog.WORKFLOWS_WRITE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StageWorkflowServiceImplTest {

    private static final TenantId TENANT = new TenantId(99L);
    private static final MemberId MEMBER = new MemberId(7L);
    private static final StageWorkflowId WORKFLOW_ID = new StageWorkflowId(20L);

    @Mock private StageWorkflowDefinitionRepository workflowRepository;
    @Mock private StageWorkflowRecordRepository recordRepository;
    @Mock private StageWorkflowCommentRepository commentRepository;
    @Mock private ApprovalDefinitionRepository approvalDefinitionRepository;
    @Mock private ApprovalInstanceRepository approvalInstanceRepository;
    @Mock private ApprovalSubmissionCoordinator submissionCoordinator;
    @Mock private ApprovalApplicationSupport support;
    @Mock private BusinessRecordFactResolver factResolver;
    @Mock private WorkflowFieldAccessSupport fieldAccessSupport;
    @Mock private OrganizationAccessGuard accessGuard;
    @Mock private BusinessRecordAccessGuard recordAccessGuard;
    @Mock private FileFieldRepository fileFieldRepository;
    @Mock private MemberProfileRepository memberProfileRepository;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private CustomerQueryService customerQueryService;
    @Mock private ContactQueryService contactQueryService;
    @Mock private LeadQueryService leadQueryService;
    @Mock private FileFieldBindingService fileFieldBindingService;
    @Mock private FileFieldDraftService fileFieldDraftService;
    @Mock private FileFieldSnapshotOutput fileFieldSnapshotOutput;
    @Mock private CustomizationObjectLock objectLock;
    @Mock private PlatformTransactionManager transactionManager;

    private StageWorkflowServiceImpl service;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any()))
                .thenReturn(new SimpleTransactionStatus());
        service = new StageWorkflowServiceImpl(definitionOperations(), recordOperations());
    }

    @Test
    void startReloadsWorkflowAfterAcquiringObjectLock() {
        StageWorkflowDefinition stale = workflow("old-stage", 2L);
        StageWorkflowDefinition current = workflow("new-stage", 3L);
        when(accessGuard.requireActiveMembership(any(), any())).thenReturn(TENANT);
        when(recordAccessGuard.requireVisible(any(), any(), any(), any(Boolean.class)))
                .thenReturn(TENANT);
        when(fieldAccessSupport.accesses(TENANT, MEMBER, BusinessObjectType.CUSTOMER))
                .thenReturn(java.util.Map.of());
        when(workflowRepository.findById(TENANT, WORKFLOW_ID))
                .thenReturn(Optional.of(stale), Optional.of(current));
        when(support.activeMembership(TENANT, MEMBER))
                .thenReturn(OrganizationMembership.activate(TENANT, MEMBER, null));
        when(recordRepository.findBySubject(
                TENANT, BusinessObjectType.CUSTOMER, 30L)).thenReturn(Optional.empty());
        when(recordRepository.save(any(), any())).thenAnswer(invocation -> {
            StageWorkflowRecord record = invocation.getArgument(1);
            LocalDateTime now = LocalDateTime.now();
            record.onPersisted(new StageWorkflowRecordId(40L), 0L, now, now);
            return record;
        });

        Result<StageWorkflowRecordVO> result = service.start(
                caller(), WORKFLOW_ID.getValue(), 30L);

        assertTrue(result.isSuccess());
        assertEquals("new-stage", result.getData().currentStageCode());
        assertEquals(3L, result.getData().workflowVersion());
        InOrder order = inOrder(workflowRepository, objectLock);
        order.verify(workflowRepository).findById(TENANT, WORKFLOW_ID);
        order.verify(objectLock).acquire(TENANT, BusinessObjectType.CUSTOMER);
        order.verify(workflowRepository).findById(TENANT, WORKFLOW_ID);
    }

    @Test
    void terminalNonApprovalOnlyRestoresActiveWithoutStartingAnotherAttempt() {
        for (ApprovalInstanceStatus status : List.of(
                ApprovalInstanceStatus.REJECTED,
                ApprovalInstanceStatus.CANCELLED,
                ApprovalInstanceStatus.WITHDRAWN)) {
            StageDefinition stage = new StageDefinition("review", "Review", 0, 50,
                    List.of(), List.of(), new ApprovalDefinitionId(21L));
            StageWorkflowDefinition workflow = workflow(stage, 3L);
            StageWorkflowRecord record = StageWorkflowRecord.start(
                    TENANT, WORKFLOW_ID, 3L, BusinessObjectType.CUSTOMER, 30L, stage);
            record.capture(stage, Map.of(), Set.of());
            record.awaitApproval(new ApprovalInstanceId(31L));
            LocalDateTime now = LocalDateTime.now();
            record.onPersisted(new StageWorkflowRecordId(40L), 5L, now, now);
            when(accessGuard.requireActiveMembership(any(), any())).thenReturn(TENANT);
            when(recordAccessGuard.requireVisible(any(), any(), any(), any(Boolean.class)))
                    .thenReturn(TENANT);
            when(recordRepository.findById(TENANT, new StageWorkflowRecordId(40L)))
                    .thenReturn(Optional.of(record));
            when(recordRepository.findByIdForUpdate(
                    TENANT, new StageWorkflowRecordId(40L))).thenReturn(Optional.of(record));
            when(workflowRepository.findById(TENANT, WORKFLOW_ID))
                    .thenReturn(Optional.of(workflow));
            when(approvalInstanceRepository.findById(
                    TENANT, new ApprovalInstanceId(31L)))
                    .thenReturn(Optional.of(terminalApproval(status)));
            when(recordRepository.save(TENANT, record)).thenReturn(record);
            when(fieldAccessSupport.accesses(
                    TENANT, MEMBER, BusinessObjectType.CUSTOMER)).thenReturn(Map.of());

            Result<StageWorkflowRecordVO> result = service.advance(caller(), 40L,
                    new StageAdvanceRequest().setVersion(5L));

            assertTrue(result.isSuccess());
            assertEquals("ACTIVE", result.getData().status());
            assertEquals(1, result.getData().progress()
                    .getFirst().approvalAttempt());
            verifyNoInteractions(submissionCoordinator);
            verify(support, never()).fields(any(), any());
            org.mockito.Mockito.reset(workflowRepository, recordRepository,
                    approvalInstanceRepository, accessGuard, recordAccessGuard,
                    fieldAccessSupport, submissionCoordinator, support);
        }
    }

    @Test
    void deleteRejectsWorkflowWithNonTerminalRecord() {
        StageWorkflowDefinition workflow = workflow("review", 3L);
        when(accessGuard.requireActiveMembership(any(), any())).thenReturn(TENANT);
        when(support.parse(StageWorkflowStatus.class, "DELETED", "Stage workflow status"))
                .thenReturn(StageWorkflowStatus.DELETED);
        when(support.error(any(), any())).thenAnswer(invocation ->
                new io.github.archetom.crm.shared.exception.NonRetryableApplicationException(
                        (io.github.archetom.crm.shared.enums.ApplicationErrorCode)
                                invocation.getArgument(0),
                        (String) invocation.getArgument(1)));
        when(workflowRepository.findById(TENANT, WORKFLOW_ID))
                .thenReturn(Optional.of(workflow));
        when(recordRepository.existsNonTerminalByWorkflow(TENANT, WORKFLOW_ID))
                .thenReturn(true);

        Result<StageWorkflowVO> result = service.changeStatus(caller(), WORKFLOW_ID.getValue(),
                new StageWorkflowStatusRequest().setVersion(3L).setStatus("DELETED"));

        assertFalse(result.isSuccess());
        verify(workflowRepository, never()).save(any(), any());
    }

    @Test
    void createRejectsStageApprovalFactUnionAboveInstanceLimit() {
        ApprovalDefinition definition = mock(ApprovalDefinition.class);
        ApprovalStepDefinition step = new ApprovalStepDefinition(
                "condition-step", "Condition step", 0, ApprovalStepType.MEMBER,
                ApprovalStepMode.ALL, ApprovalFallback.AUTO_APPROVE,
                List.of(MEMBER), List.of(), java.util.stream.IntStream.range(0, 20)
                .mapToObj(index -> new FilterCondition(
                        new FieldCode("condition_" + index), FilterOperator.EQ,
                        List.of("value"))).toList());
        List<String> required = java.util.stream.IntStream.range(0, 81)
                .mapToObj(index -> "required_" + index).toList();
        Map<FieldCode, FieldDefinition> fields = new LinkedHashMap<>();
        required.forEach(raw -> {
            FieldDefinition field = mock(FieldDefinition.class);
            when(field.isSearchable()).thenReturn(true);
            fields.put(new FieldCode(raw), field);
        });
        StageWorkflowRequest request = new StageWorkflowRequest()
                .setObjectType("CUSTOMER").setName("Workflow")
                .setSuccessName("Succeeded").setFailureName("Failed")
                .setApplicableActorIds(List.of()).setApplicableDepartmentIds(List.of())
                .setStages(List.of(new StageDefinitionRequest().setCode("review")
                        .setName("Review").setOrder(0).setProbability(50)
                        .setRequiredFieldCodes(required).setTasks(List.of())
                        .setApprovalDefinitionId(21L)));
        when(accessGuard.requireActiveMembership(any(), any())).thenReturn(TENANT);
        when(support.parse(BusinessObjectType.class, "CUSTOMER", "Business object type"))
                .thenReturn(BusinessObjectType.CUSTOMER);
        when(support.fields(TENANT, BusinessObjectType.CUSTOMER)).thenReturn(fields);
        when(approvalDefinitionRepository.findById(
                TENANT, new ApprovalDefinitionId(21L))).thenReturn(Optional.of(definition));
        when(definition.getObjectType()).thenReturn(BusinessObjectType.CUSTOMER);
        when(definition.getStatus()).thenReturn(ApprovalDefinitionStatus.ACTIVE);
        when(definition.getSteps()).thenReturn(List.of(step));
        when(support.error(any(), any())).thenAnswer(invocation ->
                new io.github.archetom.crm.shared.exception.NonRetryableApplicationException(
                        (io.github.archetom.crm.shared.enums.ApplicationErrorCode)
                                invocation.getArgument(0),
                        (String) invocation.getArgument(1)));

        Result<StageWorkflowVO> result = service.create(caller(), request);

        assertFalse(result.isSuccess());
        verify(workflowRepository, never()).save(any(), any());
    }

    private StageWorkflowDefinition workflow(String stageCode, Long version) {
        return workflow(new StageDefinition(stageCode, stageCode, 0, 50,
                List.of(), List.of(), null), version);
    }

    private StageWorkflowDefinition workflow(StageDefinition stage, Long version) {
        LocalDateTime now = LocalDateTime.now();
        return StageWorkflowDefinition.reconstitute(new StageWorkflowDefinition.Snapshot(
                WORKFLOW_ID, TENANT, BusinessObjectType.CUSTOMER, "Customer stages",
                "Succeeded", "Failed", StageWorkflowStatus.ACTIVE, Set.of(), Set.of(),
                List.of(stage), version, now, now));
    }

    private ApprovalInstance terminalApproval(ApprovalInstanceStatus status) {
        LocalDateTime now = LocalDateTime.now();
        return ApprovalInstance.reconstitute(new ApprovalInstance.Snapshot(
                new ApprovalInstanceId(31L), TENANT, new ApprovalDefinitionId(21L), 1L,
                BusinessObjectType.CUSTOMER, 30L, "terminal-" + status.name(),
                "Terminal approval", MEMBER, ApprovalRepeatMode.ONCE, status, null,
                Map.of(), Set.of(), Set.of(), List.of(), 0L, now, now, now, now));
    }

    private AuthenticatedCaller caller() {
        return new AuthenticatedCaller(MEMBER.getValue(), TENANT.getValue(),
                Set.of(WORKFLOWS_WRITE));
    }

    /** The real shared support over the same mocks, so the extracted checks still run. */
    private StageWorkflowSharedSupport sharedSupport() {
        return new StageWorkflowSharedSupport(workflowRepository, approvalDefinitionRepository,
                support, memberProfileRepository, departmentRepository);
    }

    /** The real definition half, so its entry points behave exactly as before the split. */
    private StageWorkflowDefinitionOperations definitionOperations() {
        return new StageWorkflowDefinitionOperations(workflowRepository, recordRepository,
                approvalDefinitionRepository, support, fieldAccessSupport, accessGuard,
                recordAccessGuard, objectLock,
                new CommandServiceTemplate("test-app", transactionManager),
                new QueryServiceTemplate("test-app", new TestTransactionManager()),
                sharedSupport());
    }

    /** The real record half, so its entry points behave exactly as before the split. */
    private StageWorkflowRecordOperations recordOperations() {
        return new StageWorkflowRecordOperations(recordRepository, commentRepository,
                approvalDefinitionRepository, approvalInstanceRepository, submissionCoordinator,
                support, factResolver, fieldAccessSupport, accessGuard, recordAccessGuard,
                fileFieldRepository, memberProfileRepository, departmentRepository,
                customerQueryService, contactQueryService, leadQueryService,
                fileFieldBindingService, fileFieldDraftService, fileFieldSnapshotOutput, objectLock,
                new CommandServiceTemplate("test-app", transactionManager),
                new QueryServiceTemplate("test-app", new TestTransactionManager()), sharedSupport());
    }
}
