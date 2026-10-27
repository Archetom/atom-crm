package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.ApprovalDefinitionRequest;
import io.github.archetom.crm.api.dto.request.ApprovalStepRequest;
import io.github.archetom.crm.application.port.out.CustomizationObjectLock;
import io.github.archetom.crm.application.security.BusinessRecordAccessGuard;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.service.attachment.OaRequestAttachmentTerminalCoordinator;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.TestTransactionManager;
import io.github.archetom.crm.domain.entity.ApprovalDefinition;
import io.github.archetom.crm.domain.entity.FilterCondition;
import io.github.archetom.crm.domain.entity.StageWorkflowDefinition;
import io.github.archetom.crm.domain.model.ApprovalDefinitionStatus;
import io.github.archetom.crm.domain.model.ApprovalFallback;
import io.github.archetom.crm.domain.model.ApprovalRepeatMode;
import io.github.archetom.crm.domain.model.ApprovalStepDefinition;
import io.github.archetom.crm.domain.model.ApprovalStepMode;
import io.github.archetom.crm.domain.model.ApprovalStepType;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.FilterOperator;
import io.github.archetom.crm.domain.model.StageDefinition;
import io.github.archetom.crm.domain.model.StageWorkflowStatus;
import io.github.archetom.crm.domain.repository.ApprovalDefinitionRepository;
import io.github.archetom.crm.domain.repository.ApprovalInstanceRepository;
import io.github.archetom.crm.domain.repository.FileFieldRepository;
import io.github.archetom.crm.domain.repository.MemberProfileRepository;
import io.github.archetom.crm.domain.repository.StageWorkflowDefinitionRepository;
import io.github.archetom.crm.domain.repository.OaFormRepository;
import io.github.archetom.crm.domain.valueobject.ApprovalDefinitionId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.StageWorkflowId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static io.github.archetom.crm.domain.policy.AuthorityCatalog.APPROVALS_MANAGE;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApprovalServiceImplTest {

    private static final TenantId TENANT = new TenantId(99L);
    private static final ApprovalDefinitionId DEFINITION_ID =
            new ApprovalDefinitionId(21L);

    @Mock private ApprovalDefinitionRepository definitionRepository;
    @Mock private ApprovalInstanceRepository instanceRepository;
    @Mock private StageWorkflowDefinitionRepository stageWorkflowRepository;
    @Mock private OaFormRepository oaFormRepository;
    @Mock private ApprovalApplicationSupport support;
    @Mock private ApprovalSubmissionCoordinator submissionCoordinator;
    @Mock private BusinessRecordFactResolver factResolver;
    @Mock private WorkflowFieldAccessSupport fieldAccessSupport;
    @Mock private OrganizationAccessGuard accessGuard;
    @Mock private BusinessRecordAccessGuard recordAccessGuard;
    @Mock private ApprovalFactVisibilitySupport factVisibilitySupport;
    @Mock private FileFieldRepository fileFieldRepository;
    @Mock private MemberProfileRepository memberProfileRepository;
    @Mock private FileFieldSnapshotOutput fileFieldSnapshotOutput;
    @Mock private OaRequestAttachmentTerminalCoordinator oaRequestAttachmentTerminalCoordinator;
    @Mock private CustomizationObjectLock objectLock;
    @Mock private PlatformTransactionManager transactionManager;

    private ApprovalServiceImpl service;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any()))
                .thenReturn(new SimpleTransactionStatus());
        service = new ApprovalServiceImpl(definitionRepository, instanceRepository,
                stageWorkflowRepository, oaFormRepository, support, submissionCoordinator, factResolver,
                fieldAccessSupport, accessGuard, recordAccessGuard,
                factVisibilitySupport, fileFieldRepository, memberProfileRepository, fileFieldSnapshotOutput,
                oaRequestAttachmentTerminalCoordinator, objectLock,
                new CommandServiceTemplate("test-app", transactionManager),
                new QueryServiceTemplate("test-app", new TestTransactionManager()));
    }

    @Test
    void updateRejectsDefinitionThatWouldBreakReferencedStageFactLimit() {
        ApprovalDefinition current = definitionWithConditionFields(1, 4L);
        List<ApprovalStepDefinition> replacement = stepsWithConditionFields(50);
        StageWorkflowDefinition workflow = workflowWithRequiredFields(51);
        ApprovalDefinitionRequest request = new ApprovalDefinitionRequest()
                .setVersion(4L).setObjectType("CUSTOMER").setName("Definition")
                .setRepeatMode("ONCE").setInitiatorActorIds(List.of())
                .setInitiatorDepartmentIds(List.of()).setManagerActorIds(List.of())
                .setSteps(List.of(new ApprovalStepRequest()));
        when(accessGuard.requireActiveMembership(any(), any())).thenReturn(TENANT);
        when(support.parse(BusinessObjectType.class, "CUSTOMER", "Business object type"))
                .thenReturn(BusinessObjectType.CUSTOMER);
        when(support.parse(ApprovalRepeatMode.class, "ONCE", "Approval repeat mode"))
                .thenReturn(ApprovalRepeatMode.ONCE);
        when(support.fields(TENANT, BusinessObjectType.CUSTOMER)).thenReturn(Map.of());
        when(support.steps(request.getSteps(), Map.of())).thenReturn(replacement);
        when(support.memberIds(List.of())).thenReturn(Set.of());
        when(support.departmentIds(List.of())).thenReturn(Set.of());
        when(definitionRepository.findByIdForUpdate(TENANT, DEFINITION_ID))
                .thenReturn(Optional.of(current));
        when(stageWorkflowRepository.findReferencingApprovalDefinition(
                TENANT, DEFINITION_ID)).thenReturn(List.of(workflow));
        when(support.error(any(), any())).thenAnswer(invocation ->
                new NonRetryableApplicationException(
                        (ApplicationErrorCode) invocation.getArgument(0),
                        (String) invocation.getArgument(1)));

        var result = service.updateDefinition(caller(), DEFINITION_ID.getValue(), request);

        assertFalse(result.isSuccess());
        verify(definitionRepository, never()).save(any(), any());
    }

    private ApprovalDefinition definitionWithConditionFields(int count, Long version) {
        LocalDateTime now = LocalDateTime.now();
        return ApprovalDefinition.reconstitute(new ApprovalDefinition.Snapshot(
                DEFINITION_ID, TENANT, BusinessObjectType.CUSTOMER, "Definition", null,
                ApprovalDefinitionStatus.ACTIVE, ApprovalRepeatMode.ONCE,
                Set.of(), Set.of(), Set.of(), stepsWithConditionFields(count),
                version, now, now));
    }

    private List<ApprovalStepDefinition> stepsWithConditionFields(int count) {
        List<ApprovalStepDefinition> steps = new ArrayList<>();
        int field = 0;
        int order = 0;
        while (field < count) {
            List<FilterCondition> conditions = new ArrayList<>();
            while (field < count
                    && conditions.size() < ApprovalStepDefinition.MAX_CONDITIONS) {
                conditions.add(new FilterCondition(new FieldCode("condition_" + field),
                        FilterOperator.EQ, List.of("value")));
                field++;
            }
            steps.add(new ApprovalStepDefinition("step_" + order, "Step " + order,
                    order, ApprovalStepType.MEMBER, ApprovalStepMode.ALL,
                    ApprovalFallback.AUTO_APPROVE, List.of(new MemberId(7L)), List.of(),
                    conditions));
            order++;
        }
        return List.copyOf(steps);
    }

    private StageWorkflowDefinition workflowWithRequiredFields(int count) {
        List<FieldCode> fields = java.util.stream.IntStream.range(0, count)
                .mapToObj(index -> new FieldCode("required_" + index)).toList();
        StageDefinition stage = new StageDefinition("review", "Review", 0, 50,
                fields, List.of(), DEFINITION_ID);
        LocalDateTime now = LocalDateTime.now();
        return StageWorkflowDefinition.reconstitute(new StageWorkflowDefinition.Snapshot(
                new StageWorkflowId(31L), TENANT, BusinessObjectType.CUSTOMER,
                "Workflow", "Succeeded", "Failed", StageWorkflowStatus.ACTIVE,
                Set.of(), Set.of(), List.of(stage), 2L, now, now));
    }

    private AuthenticatedCaller caller() {
        return new AuthenticatedCaller(7L, TENANT.getValue(), Set.of(APPROVALS_MANAGE));
    }
}
