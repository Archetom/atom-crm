package io.github.archetom.crm.application.service.impl;

import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.StageDefinitionRequest;
import io.github.archetom.crm.api.dto.request.StageWorkflowQueryRequest;
import io.github.archetom.crm.api.dto.request.StageWorkflowRequest;
import io.github.archetom.crm.api.dto.request.StageWorkflowStatusRequest;
import io.github.archetom.crm.application.assembler.StageWorkflowAssembler;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.port.out.CustomizationObjectLock;
import io.github.archetom.crm.application.security.BusinessRecordAccessGuard;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.application.vo.StageFieldCandidateVO;
import io.github.archetom.crm.application.vo.StageWorkflowApplicableItemVO;
import io.github.archetom.crm.application.vo.StageWorkflowApplicableVO;
import io.github.archetom.crm.application.vo.StageWorkflowApprovalDefinitionOptionVO;
import io.github.archetom.crm.application.vo.StageWorkflowConfigurationOptionsVO;
import io.github.archetom.crm.application.vo.StageWorkflowFieldOptionVO;
import io.github.archetom.crm.application.vo.StageWorkflowObjectTypeOptionVO;
import io.github.archetom.crm.application.vo.StageWorkflowVO;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.OrganizationMembership;
import io.github.archetom.crm.domain.entity.StageWorkflowDefinition;
import io.github.archetom.crm.domain.entity.StageWorkflowRecord;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.CustomFieldType;
import io.github.archetom.crm.domain.model.FieldAccessLevel;
import io.github.archetom.crm.domain.model.StageDefinition;
import io.github.archetom.crm.domain.model.StageTaskDefinition;
import io.github.archetom.crm.domain.model.StageWorkflowQuery;
import io.github.archetom.crm.domain.model.StageWorkflowStatus;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.repository.ApprovalDefinitionRepository;
import io.github.archetom.crm.domain.repository.StageWorkflowDefinitionRepository;
import io.github.archetom.crm.domain.repository.StageWorkflowRecordRepository;
import io.github.archetom.crm.domain.valueobject.ApprovalDefinitionId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.StageWorkflowId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The stage-workflow definition half: querying, reading and configuring workflows themselves.
 *
 * <p>Defining a workflow and running a record through one are different jobs with different
 * authorities — configuring demands {@code workflows:manage}, reading demands
 * {@code workflows:read} — and the REST layer already exposes them as two controllers. They lived
 * in a single 1,888-line class with eighteen entry points, where the only thing separating the two
 * concerns was which method you happened to be reading.</p>
 *
 * <p>Nothing here touches a workflow record's lifecycle; that is the record half's job. What both
 * need sits in {@link StageWorkflowSharedSupport}.</p>
 */
@Component
@RequiredArgsConstructor
public class StageWorkflowDefinitionOperations {

    private final StageWorkflowDefinitionRepository workflowRepository;
    private final StageWorkflowRecordRepository recordRepository;
    private final ApprovalDefinitionRepository approvalDefinitionRepository;
    private final ApprovalApplicationSupport support;
    private final WorkflowFieldAccessSupport fieldAccessSupport;
    private final OrganizationAccessGuard accessGuard;
    private final BusinessRecordAccessGuard recordAccessGuard;
    private final CustomizationObjectLock objectLock;
    private final CommandServiceTemplate commandTemplate;
    private final QueryServiceTemplate queryTemplate;
    private final StageWorkflowSharedSupport shared;

    public Result<Pager<StageWorkflowVO>> query(
            AuthenticatedCaller caller, StageWorkflowQueryRequest request) {
        return queryTemplate.execute(UseCaseOperation.STAGE_WORKFLOW_QUERY,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private StageWorkflowQuery query;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.WORKFLOWS_READ);
                        shared.require(request, "Stage workflow query");
                        query = new StageWorkflowQuery(optionalType(request.getObjectType()),
                                support.parseOptional(StageWorkflowStatus.class,
                                        request.getStatus(), "Stage workflow status"),
                                request.getKeyword(), shared.page(request.getPage()), shared.size(request.getSize()));
                    }

                    @Override
                    public Pager<StageWorkflowVO> execute() {
                        return shared.pager(workflowRepository.findPage(tenantId, query),
                                StageWorkflowAssembler::toVO);
                    }
                });
    }

    public Result<StageWorkflowApplicableVO> applicable(
            AuthenticatedCaller caller, String objectType, Long subjectId) {
        return queryTemplate.execute(UseCaseOperation.STAGE_WORKFLOW_APPLICABLE,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private BusinessObjectType type;
                    private MemberId memberId;
                    private OrganizationMembership membership;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.WORKFLOWS_READ);
                        type = runtimeType(objectType);
                        memberId = new MemberId(caller.actorId());
                        membership = support.activeMembership(tenantId, memberId);
                        shared.requireSameTenant(tenantId, recordAccessGuard.requireVisible(
                                caller, type, subjectId, false));
                    }

                    @Override
                    public StageWorkflowApplicableVO execute() {
                        shared.requireSameTenant(tenantId, recordAccessGuard.requireVisible(
                                caller, type, subjectId, false));
                        StageWorkflowRecord existing = recordRepository.findBySubject(
                                tenantId, type, subjectId).orElse(null);
                        List<StageWorkflowApplicableItemVO> workflows = workflowRepository
                                .findActiveByObjectType(tenantId, type).stream().map(workflow -> {
                                    if (!workflow.isApplicable(memberId, membership.getDepartmentId())) {
                                        return new StageWorkflowApplicableItemVO(
                                                workflow.getId().getValue(), workflow.getName(),
                                                false, "CALLER_OUTSIDE_WORKFLOW_SCOPE");
                                    }
                                    if (existing != null && !existing.getWorkflowId().equals(
                                            workflow.getId())) {
                                        return new StageWorkflowApplicableItemVO(
                                                workflow.getId().getValue(), workflow.getName(),
                                                false, "RECORD_ALREADY_HAS_WORKFLOW");
                                    }
                                    return new StageWorkflowApplicableItemVO(
                                            workflow.getId().getValue(), workflow.getName(),
                                            true, null);
                                }).toList();
                        return new StageWorkflowApplicableVO(type.name(), subjectId, workflows);
                    }
                });
    }

    public Result<StageWorkflowVO> get(
            AuthenticatedCaller caller, Long workflowId) {
        return queryTemplate.execute(UseCaseOperation.STAGE_WORKFLOW_GET,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private StageWorkflowId id;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.WORKFLOWS_READ);
                        id = shared.workflowId(workflowId);
                    }

                    @Override
                    public StageWorkflowVO execute() {
                        return withStageEditability(tenantId,
                                StageWorkflowAssembler.toVO(shared.requireWorkflow(tenantId, id)));
                    }
                });
    }

    public Result<StageWorkflowConfigurationOptionsVO> configurationOptions(
            AuthenticatedCaller caller, String objectType) {
        return queryTemplate.execute(UseCaseOperation.STAGE_WORKFLOW_CONFIGURATION_OPTIONS,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private MemberId memberId;
                    private BusinessObjectType type;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(caller,
                                AuthorityCatalog.WORKFLOWS_MANAGE);
                        memberId = new MemberId(caller.actorId());
                        type = objectType == null || objectType.isBlank() ? null : runtimeType(objectType);
                    }

                    @Override
                    public StageWorkflowConfigurationOptionsVO execute() {
                        List<StageWorkflowObjectTypeOptionVO> types = List.of(
                                new StageWorkflowObjectTypeOptionVO("CUSTOMER", "Customer"),
                                new StageWorkflowObjectTypeOptionVO("CONTACT", "Contact"),
                                new StageWorkflowObjectTypeOptionVO("LEAD", "Lead"),
                                new StageWorkflowObjectTypeOptionVO("OPPORTUNITY", "Opportunity"));
                        if (type == null) {
                            return new StageWorkflowConfigurationOptionsVO(types, List.of(), List.of());
                        }
                        Map<FieldCode, io.github.archetom.crm.domain.model.EffectiveFieldAccess> accesses =
                                fieldAccessSupport.accesses(tenantId, memberId, type);
                        List<StageWorkflowFieldOptionVO> fields = support.fields(tenantId, type).values()
                                .stream().map(field -> configurationField(type, field,
                                        accesses.get(field.getCode()))).toList();
                        List<StageWorkflowApprovalDefinitionOptionVO> approvals =
                                approvalDefinitionRepository.findActiveByObjectType(tenantId, type).stream()
                                        .map(value -> new StageWorkflowApprovalDefinitionOptionVO(
                                                value.getId().getValue(), value.getName(),
                                                value.getVersion())).toList();
                        return new StageWorkflowConfigurationOptionsVO(types, fields, approvals);
                    }
                });
    }

    public Result<Pager<StageFieldCandidateVO>> configurationCandidates(
            AuthenticatedCaller caller, String candidateType, String keyword, Integer page, Integer size) {
        return queryTemplate.execute(UseCaseOperation.STAGE_WORKFLOW_CONFIGURATION_CANDIDATE_QUERY,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private String type;
                    private int pageNumber;
                    private int pageSize;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(caller,
                                AuthorityCatalog.WORKFLOWS_MANAGE);
                        type = candidateType == null ? null : candidateType.trim().toUpperCase(Locale.ROOT);
                        pageNumber = shared.page(page);
                        pageSize = shared.size(size);
                        if (!("MEMBER".equals(type) || "DEPARTMENT".equals(type))
                                || pageNumber < 1 || pageSize < 1 || pageSize > 200) {
                            throw shared.error(ApplicationErrorCode.PARAMETER_INVALID,
                                    "Stage workflow configuration candidate query is invalid");
                        }
                    }

                    @Override
                    public Pager<StageFieldCandidateVO> execute() {
                        return "MEMBER".equals(type)
                                ? shared.actorCandidates(tenantId, keyword, pageNumber, pageSize)
                                : shared.departmentCandidates(tenantId, keyword, pageNumber, pageSize);
                    }
                });
    }

    public Result<StageWorkflowVO> create(
            AuthenticatedCaller caller, StageWorkflowRequest request) {
        return commandTemplate.execute(UseCaseOperation.STAGE_WORKFLOW_CREATE,
                workflowMutation(caller, null, request));
    }

    public Result<StageWorkflowVO> update(
            AuthenticatedCaller caller, Long workflowId, StageWorkflowRequest request) {
        return commandTemplate.execute(UseCaseOperation.STAGE_WORKFLOW_UPDATE,
                workflowMutation(caller, shared.workflowId(workflowId), request));
    }

    public Result<StageWorkflowVO> changeStatus(
            AuthenticatedCaller caller, Long workflowId,
            StageWorkflowStatusRequest request) {
        return commandTemplate.execute(UseCaseOperation.STAGE_WORKFLOW_STATUS_UPDATE,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private StageWorkflowId id;
                    private StageWorkflowStatus status;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.WORKFLOWS_MANAGE);
                        id = shared.workflowId(workflowId);
                        shared.require(request, "Stage workflow status request");
                        status = support.parse(StageWorkflowStatus.class,
                                request.getStatus(), "Stage workflow status");
                    }

                    @Override
                    public StageWorkflowVO execute() {
                        StageWorkflowDefinition candidate = shared.requireWorkflow(tenantId, id);
                        objectLock.acquire(tenantId, candidate.getObjectType());
                        StageWorkflowDefinition workflow = shared.requireWorkflow(tenantId, id);
                        shared.requireVersion(workflow.getVersion(), request.getVersion(),
                                "Stage workflow version is stale");
                        if (status == StageWorkflowStatus.ACTIVE) {
                            shared.validateApprovalReferences(tenantId, workflow.getObjectType(),
                                    workflow.getStages(), true);
                        }
                        if (status == StageWorkflowStatus.DELETED
                                && recordRepository.existsNonTerminalByWorkflow(
                                tenantId, workflow.getId())) {
                            throw shared.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                                    "A running stage workflow cannot be deleted");
                        }
                        workflow.changeStatus(status);
                        return StageWorkflowAssembler.toVO(
                                workflowRepository.save(tenantId, workflow));
                    }
                });
    }

    private ServiceOperation<StageWorkflowVO> workflowMutation(
            AuthenticatedCaller caller, StageWorkflowId id,
            StageWorkflowRequest request) {
        return new ServiceOperation<>() {
            private TenantId tenantId;
            private BusinessObjectType objectType;
            private MemberId memberId;

            @Override
            public void validate() {
                tenantId = accessGuard.requireActiveMembership(
                        caller, AuthorityCatalog.WORKFLOWS_MANAGE);
                memberId = new MemberId(caller.actorId());
                shared.require(request, "Stage workflow request");
                objectType = runtimeType(request.getObjectType());
            }

            @Override
            public StageWorkflowVO execute() {
                objectLock.acquire(tenantId, objectType);
                List<StageDefinition> stages = stages(tenantId, objectType,
                        request.getStages());
                StageWorkflowDefinition workflow;
                if (id == null) {
                    if (request.getVersion() != null) {
                        throw shared.error(ApplicationErrorCode.PARAMETER_INVALID,
                                "New stage workflow cannot have a version");
                    }
                    workflow = StageWorkflowDefinition.create(tenantId, objectType,
                            request.getName(), request.getSuccessName(), request.getFailureName(),
                            support.memberIds(request.getApplicableActorIds()),
                            support.departmentIds(request.getApplicableDepartmentIds()), stages);
                } else {
                    workflow = shared.requireWorkflow(tenantId, id);
                    shared.requireVersion(workflow.getVersion(), request.getVersion(),
                            "Stage workflow version is stale");
                    if (workflow.getObjectType() != objectType) {
                        throw shared.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                                "Stage workflow object type cannot change");
                    }
                    if (recordRepository.existsByWorkflow(tenantId, id)) {
                        throw shared.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                                "Referenced stage workflow configuration cannot change");
                    }
                    if (workflow.isActive()) {
                        shared.validateApprovalReferences(tenantId, objectType, stages, true);
                    }
                    workflow.replace(request.getName(), request.getSuccessName(),
                            request.getFailureName(),
                            support.memberIds(request.getApplicableActorIds()),
                            support.departmentIds(request.getApplicableDepartmentIds()), stages);
                }
                support.validateReferences(tenantId,
                        workflow.getApplicableMemberIds(),
                        workflow.getApplicableDepartmentIds(), List.of());
                return StageWorkflowAssembler.toVO(
                        workflowRepository.save(tenantId, workflow));
            }
        };
    }

    private List<StageDefinition> stages(
            TenantId tenantId, BusinessObjectType objectType,
            List<StageDefinitionRequest> requests) {
        if (requests == null) {
            return List.of();
        }
        Map<FieldCode, FieldDefinition> fields = support.fields(tenantId, objectType);
        List<StageDefinition> stages = requests.stream().map(request -> {
            if (request == null) {
                throw shared.error(ApplicationErrorCode.PARAMETER_INVALID,
                        "Stage definition is invalid");
            }
            List<FieldCode> required = (request.getRequiredFieldCodes() == null
                    ? List.<String>of() : request.getRequiredFieldCodes()).stream()
                    .map(FieldCode::new).toList();
            if (required.stream().anyMatch(code -> {
                FieldDefinition field = fields.get(code);
                return stageFieldBlocker(objectType, code, field) != null;
            })) {
                throw shared.error(ApplicationErrorCode.PARAMETER_INVALID,
                        "Stage field reference is invalid");
            }
            List<StageTaskDefinition> tasks = (request.getTasks() == null
                    ? List.<io.github.archetom.crm.api.dto.request.StageTaskRequest>of()
                    : request.getTasks()).stream().map(task -> {
                        if (task == null) {
                            throw shared.error(ApplicationErrorCode.PARAMETER_INVALID,
                                    "Stage task is invalid");
                        }
                        return new StageTaskDefinition(task.getCode(), task.getName(),
                                Boolean.TRUE.equals(task.getRequired()));
                    })
                    .toList();
            return new StageDefinition(request.getCode(), request.getName(),
                    request.getOrder() == null ? -1 : request.getOrder(),
                    request.getProbability() == null ? -1 : request.getProbability(),
                    required, tasks, request.getApprovalDefinitionId() == null ? null
                    : approvalDefinitionId(request.getApprovalDefinitionId()));
        }).toList();
        shared.validateApprovalReferences(tenantId, objectType, stages, false);
        return stages;
    }

    private BusinessObjectType runtimeType(String value) {
        BusinessObjectType type = support.parse(BusinessObjectType.class,
                value, "Business object type");
        return ensureRuntimeType(type);
    }

    private BusinessObjectType optionalType(String value) {
        BusinessObjectType type = support.parseOptional(BusinessObjectType.class,
                value, "Business object type");
        return type == null ? null : ensureRuntimeType(type);
    }

    private BusinessObjectType ensureRuntimeType(BusinessObjectType type) {
        return switch (type) {
            case LEAD, CUSTOMER, CONTACT, OPPORTUNITY -> type;
            default -> throw shared.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                    "Business object stage workflow runtime is not available");
        };
    }

    private ApprovalDefinitionId approvalDefinitionId(Long value) {
        try {
            return new ApprovalDefinitionId(value);
        } catch (RuntimeException exception) {
            throw shared.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Approval definition ID is invalid");
        }
    }



    private StageWorkflowFieldOptionVO configurationField(
            BusinessObjectType objectType, FieldDefinition field,
            io.github.archetom.crm.domain.model.EffectiveFieldAccess access) {
        boolean writable = access != null && access.accessLevel() == FieldAccessLevel.WRITE;
        String blocker = !writable ? "FIELD_NOT_WRITABLE" : stageFieldBlocker(objectType,
                field.getCode(), field);
        return new StageWorkflowFieldOptionVO(field.getCode().getValue(), field.getDisplayName(),
                field.getFieldType().name(), writable, blocker == null, blocker);
    }

    private StageWorkflowVO withStageEditability(TenantId tenantId, StageWorkflowVO workflow) {
        if (workflow == null) {
            return null;
        }
        BusinessObjectType objectType = runtimeType(workflow.objectType());
        Map<FieldCode, FieldDefinition> fields = support.fields(tenantId, objectType);
        return new StageWorkflowVO(workflow.id(), workflow.objectType(), workflow.name(),
                workflow.successName(), workflow.failureName(), workflow.status(),
                workflow.applicableActorIds(), workflow.applicableDepartmentIds(), workflow.stages()
                .stream().map(stage -> {
                    String blocker = stage.requiredFieldCodes().stream().map(shared::stageFieldCode)
                            .map(code -> stageFieldBlocker(objectType, code, fields.get(code)))
                            .filter(java.util.Objects::nonNull).findFirst().orElse(null);
                    return new io.github.archetom.crm.application.vo.StageDefinitionVO(stage.code(),
                            stage.name(), stage.order(), stage.probability(),
                            stage.requiredFieldCodes(), stage.tasks(), stage.approvalDefinitionId(),
                            blocker == null, blocker);
                }).toList(), workflow.version(), workflow.createdTime(), workflow.updatedTime());
    }

    private String stageFieldBlocker(
            BusinessObjectType objectType, FieldCode code, FieldDefinition field) {
        if (field == null) {
            return "FIELD_UNAVAILABLE";
        }
        if (!field.isSearchable() && field.getFieldType() != CustomFieldType.FILE) {
            return "FIELD_NOT_SEARCHABLE";
        }
        if (field.getFieldType() == CustomFieldType.RELATION
                && shared.relationTarget(objectType, code) == null) {
            return "RELATION_TARGET_UNDECLARED";
        }
        return null;
    }
}
