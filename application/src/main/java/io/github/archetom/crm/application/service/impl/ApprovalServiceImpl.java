package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.ApprovalActionItemRequest;
import io.github.archetom.crm.api.dto.request.ApprovalActionRequest;
import io.github.archetom.crm.api.dto.request.ApprovalBatchActionRequest;
import io.github.archetom.crm.api.dto.request.ApprovalDefinitionQueryRequest;
import io.github.archetom.crm.api.dto.request.ApprovalDefinitionRequest;
import io.github.archetom.crm.api.dto.request.ApprovalDefinitionStatusRequest;
import io.github.archetom.crm.api.dto.request.ApprovalPreviewRequest;
import io.github.archetom.crm.api.dto.request.ApprovalQueryRequest;
import io.github.archetom.crm.api.dto.request.ApprovalSubmitRequest;
import io.github.archetom.crm.application.assembler.ApprovalAssembler;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.port.out.CustomizationObjectLock;
import io.github.archetom.crm.application.security.BusinessRecordAccessGuard;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.service.ApprovalService;
import io.github.archetom.crm.application.service.attachment.OaRequestAttachmentTerminalCoordinator;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.application.vo.ApprovalDefinitionVO;
import io.github.archetom.crm.application.vo.ApprovalFieldVO;
import io.github.archetom.crm.application.vo.ApprovalInstanceVO;
import io.github.archetom.crm.application.vo.ApprovalInstanceSummaryVO;
import io.github.archetom.crm.application.vo.ApprovalPreviewVO;
import io.github.archetom.crm.application.vo.ApprovalPreviewStepVO;
import io.github.archetom.crm.application.vo.ApprovalBatchActionItemVO;
import io.github.archetom.crm.application.vo.ApprovalBatchActionResultVO;
import io.github.archetom.crm.application.vo.ApprovalStartOptionVO;
import io.github.archetom.crm.application.vo.ApprovalStartOptionsVO;
import io.github.archetom.crm.application.vo.ApprovalSelectionCandidateVO;
import io.github.archetom.crm.application.vo.ApprovalStartSelectionVO;
import io.github.archetom.crm.application.vo.ApprovalTransferCandidateVO;
import io.github.archetom.crm.domain.entity.ApprovalDefinition;
import io.github.archetom.crm.domain.entity.ApprovalInstance;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.StageWorkflowDefinition;
import io.github.archetom.crm.domain.entity.MemberProfile;
import io.github.archetom.crm.domain.model.ApprovalStepDefinition;
import io.github.archetom.crm.domain.model.ApprovalStepType;
import io.github.archetom.crm.domain.model.ApprovalActionType;
import io.github.archetom.crm.domain.model.ApprovalDefinitionQuery;
import io.github.archetom.crm.domain.model.ApprovalDefinitionStatus;
import io.github.archetom.crm.domain.model.ApprovalInstanceQuery;
import io.github.archetom.crm.domain.model.ApprovalInstanceReference;
import io.github.archetom.crm.domain.model.ApprovalInstanceStatus;
import io.github.archetom.crm.domain.model.ApprovalQueryBucket;
import io.github.archetom.crm.domain.model.ApprovalRepeatMode;
import io.github.archetom.crm.domain.model.AttachmentTarget;
import io.github.archetom.crm.domain.model.AttachmentTargetType;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.FileFieldValue;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.policy.ApprovalConditionEvaluator;
import io.github.archetom.crm.domain.policy.StageApprovalFactPolicy;
import io.github.archetom.crm.domain.policy.OaRequestFieldCatalog;
import io.github.archetom.crm.domain.repository.ApprovalDefinitionRepository;
import io.github.archetom.crm.domain.repository.ApprovalInstanceRepository;
import io.github.archetom.crm.domain.repository.FileFieldRepository;
import io.github.archetom.crm.domain.repository.MemberProfileRepository;
import io.github.archetom.crm.domain.repository.PageResult;
import io.github.archetom.crm.domain.repository.OaFormRepository;
import io.github.archetom.crm.domain.repository.StageWorkflowDefinitionRepository;
import io.github.archetom.crm.domain.valueobject.ApprovalDefinitionId;
import io.github.archetom.crm.domain.valueobject.ApprovalInstanceId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.ApplicationException;
import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ApprovalServiceImpl implements ApprovalService {

    private static final int VISIBILITY_SCAN_SIZE = 200;

    private final ApprovalDefinitionRepository definitionRepository;
    private final ApprovalInstanceRepository instanceRepository;
    private final StageWorkflowDefinitionRepository stageWorkflowRepository;
    private final OaFormRepository oaFormRepository;
    private final ApprovalApplicationSupport support;
    private final ApprovalSubmissionCoordinator submissionCoordinator;
    private final BusinessRecordFactResolver factResolver;
    private final WorkflowFieldAccessSupport fieldAccessSupport;
    private final OrganizationAccessGuard accessGuard;
    private final BusinessRecordAccessGuard recordAccessGuard;
    private final ApprovalFactVisibilitySupport factVisibilitySupport;
    private final FileFieldRepository fileFieldRepository;
    private final MemberProfileRepository memberProfileRepository;
    private final FileFieldSnapshotOutput fileFieldSnapshotOutput;
    private final OaRequestAttachmentTerminalCoordinator oaRequestAttachmentTerminalCoordinator;
    private final CustomizationObjectLock objectLock;
    private final CommandServiceTemplate commandTemplate;
    private final QueryServiceTemplate queryTemplate;

    @Override
    public Result<List<ApprovalFieldVO>> fields(
            AuthenticatedCaller caller, String objectType) {
        return queryTemplate.execute(UseCaseOperation.APPROVAL_FIELD_LIST,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private BusinessObjectType type;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.APPROVALS_MANAGE);
                        type = definitionType(objectType);
                    }

                    @Override
                    public List<ApprovalFieldVO> execute() {
                        return support.fields(tenantId, type).values().stream()
                                .filter(value -> value.isSearchable())
                                .map(value -> new ApprovalFieldVO(value.getCode().getValue(),
                                        value.getDisplayName(), value.getFieldType().name(),
                                        value.isRequired())).toList();
                    }
                });
    }

    @Override
    public Result<Pager<ApprovalDefinitionVO>> queryDefinitions(
            AuthenticatedCaller caller, ApprovalDefinitionQueryRequest request) {
        return queryTemplate.execute(UseCaseOperation.APPROVAL_DEFINITION_QUERY,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private ApprovalDefinitionQuery query;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.APPROVALS_MANAGE);
                        require(request, "Approval definition query");
                        query = new ApprovalDefinitionQuery(
                                optionalDefinitionType(request.getObjectType()),
                                support.parseOptional(ApprovalDefinitionStatus.class,
                                        request.getStatus(), "Approval definition status"),
                                request.getKeyword(), page(request.getPage()), size(request.getSize()));
                    }

                    @Override
                    public Pager<ApprovalDefinitionVO> execute() {
                        return pager(definitionRepository.findPage(tenantId, query),
                                ApprovalAssembler::toVO);
                    }
                });
    }

    @Override
    public Result<ApprovalDefinitionVO> getDefinition(
            AuthenticatedCaller caller, Long definitionId) {
        return queryTemplate.execute(UseCaseOperation.APPROVAL_DEFINITION_GET,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private ApprovalDefinitionId id;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.APPROVALS_MANAGE);
                        id = definitionId(definitionId);
                    }

                    @Override
                    public ApprovalDefinitionVO execute() {
                        return ApprovalAssembler.toVO(requireDefinition(tenantId, id));
                    }
                });
    }

    @Override
    public Result<ApprovalDefinitionVO> createDefinition(
            AuthenticatedCaller caller, ApprovalDefinitionRequest request) {
        return commandTemplate.execute(UseCaseOperation.APPROVAL_DEFINITION_CREATE,
                definitionMutation(caller, null, request));
    }

    @Override
    public Result<ApprovalDefinitionVO> updateDefinition(
            AuthenticatedCaller caller, Long definitionId, ApprovalDefinitionRequest request) {
        return commandTemplate.execute(UseCaseOperation.APPROVAL_DEFINITION_UPDATE,
                definitionMutation(caller, definitionId(definitionId), request));
    }

    @Override
    public Result<ApprovalDefinitionVO> changeDefinitionStatus(
            AuthenticatedCaller caller, Long definitionId,
            ApprovalDefinitionStatusRequest request) {
        return commandTemplate.execute(UseCaseOperation.APPROVAL_DEFINITION_STATUS_UPDATE,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private ApprovalDefinitionId id;
                    private ApprovalDefinitionStatus status;
                    private BusinessObjectType objectType;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.APPROVALS_MANAGE);
                        id = definitionId(definitionId);
                        require(request, "Approval definition status request");
                        status = support.parse(ApprovalDefinitionStatus.class,
                                request.getStatus(), "Approval definition status");
                    }

                    @Override
                    public void prepare() {
                        objectType = requireDefinition(tenantId, id).getObjectType();
                    }

                    @Override
                    public ApprovalDefinitionVO execute() {
                        objectLock.acquire(tenantId, objectType);
                        ApprovalDefinition definition = requireDefinitionForUpdate(tenantId, id);
                        if (definition.getObjectType() != objectType) {
                            throw support.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                                    "Approval definition object type cannot change");
                        }
                        support.requireVersion(definition, request.getVersion());
                        if (status == ApprovalDefinitionStatus.ACTIVE) {
                            validateStageWorkflowFactLimits(tenantId, definition);
                        }
                        if (status != ApprovalDefinitionStatus.ACTIVE
                                && stageWorkflowRepository.existsReferencingApprovalDefinition(
                                tenantId, id)) {
                            throw support.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                                    "Approval definition is referenced by a stage workflow");
                        }
                        if (status != ApprovalDefinitionStatus.ACTIVE
                                && definition.getObjectType() == BusinessObjectType.OA_REQUEST
                                && oaFormRepository.existsActiveReferencingApprovalDefinition(
                                tenantId, id)) {
                            throw support.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                                    "Approval definition is referenced by an active OA form");
                        }
                        definition.changeStatus(status);
                        return ApprovalAssembler.toVO(
                                definitionRepository.save(tenantId, definition));
                    }
                });
    }

    @Override
    public Result<ApprovalPreviewVO> preview(
            AuthenticatedCaller caller, Long definitionId, ApprovalPreviewRequest request) {
        return queryTemplate.execute(UseCaseOperation.APPROVAL_PREVIEW,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private MemberId memberId;
                    private ApprovalDefinition definition;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.APPROVALS_SUBMIT);
                        memberId = new MemberId(caller.actorId());
                        require(request, "Approval preview request");
                        definition = requireDefinition(tenantId, definitionId(definitionId));
                        ensureRuntimeType(definition.getObjectType());
                        requireSameTenant(tenantId, recordAccessGuard.requireVisible(caller,
                                definition.getObjectType(), request.getSubjectId(), false));
                    }

                    @Override
                    public ApprovalPreviewVO execute() {
                        requireSameTenant(tenantId, recordAccessGuard.requireVisible(caller,
                                definition.getObjectType(), request.getSubjectId(), false));
                        Map<FieldCode, io.github.archetom.crm.domain.entity.FieldDefinition> fields =
                                support.fields(tenantId, definition.getObjectType());
                        Map<FieldCode, List<String>> facts = factResolver.resolve(
                                tenantId, definition.getObjectType(), request.getSubjectId(),
                                factCodes(definition, Set.of()), fields);
                        var resolution = submissionCoordinator.resolve(tenantId, definition,
                                memberId, facts, request.getSelectedApprovers());
                        return new ApprovalPreviewVO(definition.getId().getValue(),
                                definition.getVersion(), request.getSubjectId(), true, null,
                                resolution.route().stream().map(step -> new ApprovalPreviewStepVO(
                                        step.code(), step.name(), definition.getSteps().stream()
                                                .anyMatch(candidate -> candidate.code().equals(step.code())
                                                        && candidate.type()
                                                        == io.github.archetom.crm.domain.model.ApprovalStepType.APPLICANT_SELECT)))
                                        .toList());
                    }
                });
    }

    @Override
    public Result<ApprovalInstanceVO> submit(
            AuthenticatedCaller caller, ApprovalSubmitRequest request) {
        return commandTemplate.execute(UseCaseOperation.APPROVAL_SUBMIT,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private MemberId memberId;
                    private ApprovalDefinition definition;
                    private String idempotencyKey;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.APPROVALS_SUBMIT);
                        memberId = new MemberId(caller.actorId());
                        require(request, "Approval submit request");
                        idempotencyKey = submissionCoordinator.normalizePublicIdempotencyKey(
                                request.getIdempotencyKey());
                        definition = requireDefinition(tenantId,
                                definitionId(request.getDefinitionId()));
                        ensureRuntimeType(definition.getObjectType());
                        requireSameTenant(tenantId, recordAccessGuard.requireVisible(caller,
                                definition.getObjectType(), request.getSubjectId(), false));
                    }

                    @Override
                    public ApprovalInstanceVO execute() {
                        objectLock.acquire(tenantId, definition.getObjectType());
                        ApprovalDefinition current = requireDefinition(
                                tenantId, definition.getId());
                        requireSameTenant(tenantId, recordAccessGuard.requireVisible(caller,
                                current.getObjectType(), request.getSubjectId(), false));
                        ApprovalInstance saved = submissionCoordinator.submit(tenantId,
                                current, request.getSubjectId(), idempotencyKey,
                                current.getName(), memberId,
                                () -> authoritativeFacts(caller, tenantId, memberId,
                                        current, request.getSubjectId()),
                                request.getSelectedApprovers(), List.of());
                        snapshotFileFields(tenantId, saved, current.getObjectType(), request.getSubjectId());
                        return toVO(caller, tenantId, memberId, saved);
                    }
                });
    }

    @Override
    public Result<ApprovalInstanceVO> getInstance(
            AuthenticatedCaller caller, Long instanceId) {
        return queryTemplate.execute(UseCaseOperation.APPROVAL_GET,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private MemberId memberId;
                    private ApprovalInstanceId id;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.APPROVALS_READ);
                        memberId = new MemberId(caller.actorId());
                        id = instanceId(instanceId);
                    }

                    @Override
                    public ApprovalInstanceVO execute() {
                        ApprovalInstance instance = requireInstance(tenantId, id, false);
                        requireInstanceAccess(caller, tenantId, memberId, instance);
                        requireVisibleRecord(caller, tenantId, instance);
                        return toVO(caller, tenantId, memberId, instance);
                    }
                });
    }

    @Override
    public Result<Pager<ApprovalInstanceSummaryVO>> queryInstances(
            AuthenticatedCaller caller, ApprovalQueryRequest request) {
        return queryTemplate.execute(UseCaseOperation.APPROVAL_QUERY,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private ApprovalInstanceQuery query;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.APPROVALS_READ);
                        require(request, "Approval query");
                        ApprovalQueryBucket bucket = support.parse(ApprovalQueryBucket.class,
                                request.getBucket(), "Approval query bucket");
                        if (bucket == ApprovalQueryBucket.ALL
                                && !caller.hasAuthority(AuthorityCatalog.APPROVALS_MANAGE)) {
                            throw support.error(ApplicationErrorCode.ACCESS_DENIED,
                                    "All approvals require approval management authority");
                        }
                        query = new ApprovalInstanceQuery(new MemberId(caller.actorId()), bucket,
                                support.parseOptional(ApprovalInstanceStatus.class,
                                        request.getStatus(), "Approval status"),
                                optionalRuntimeType(request.getObjectType()), request.getSubjectId(),
                                request.getKeyword(), page(request.getPage()), size(request.getSize()));
                    }

                    @Override
                    public Pager<ApprovalInstanceSummaryVO> execute() {
                        ApprovalVisibilityScan scan = scanVisible(
                                caller, tenantId, query, true);
                        WorkflowFieldAccessCache fieldAccessCache =
                                new WorkflowFieldAccessCache(
                                        fieldAccessSupport, tenantId, query.memberId());
                        List<ApprovalInstance> instances =
                                instanceRepository.findByIds(
                                        tenantId, scan.instanceIds());
                        Map<Long, Set<FieldCode>> hiddenRelationshipFacts =
                                factVisibilitySupport.hiddenForInstances(
                                        caller, tenantId, instances);
                        List<ApprovalInstanceSummaryVO> items = instances.stream()
                                .map(instance -> toSummary(instance,
                                        fieldAccessCache.accesses(instance.getObjectType()),
                                        hiddenRelationshipFacts.getOrDefault(
                                                instance.getId().getValue(), Set.of()),
                                        allowedActions(caller, query.memberId(), instance)))
                                .toList();
                        Pager<ApprovalInstanceSummaryVO> result = new Pager<>();
                        result.setPageNum(query.page());
                        result.setPageSize(query.size());
                        result.setTotalNum(scan.total());
                        result.setObjectList(items);
                        return result;
                    }
                });
    }

    @Override
    public Result<Long> pendingCount(AuthenticatedCaller caller) {
        return queryTemplate.execute(UseCaseOperation.APPROVAL_PENDING_COUNT,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private MemberId memberId;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.APPROVALS_READ);
                        memberId = new MemberId(caller.actorId());
                    }

                    @Override
                    public Long execute() {
                        ApprovalInstanceQuery query = new ApprovalInstanceQuery(memberId,
                                ApprovalQueryBucket.PENDING, null, null, null, null, 1, 1);
                        return scanVisible(caller, tenantId, query, false).total();
                    }
                });
    }

    @Override
    public Result<ApprovalStartOptionsVO> startOptions(
            AuthenticatedCaller caller, String objectType, Long subjectId) {
        return queryTemplate.execute(UseCaseOperation.APPROVAL_SUBMIT,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private MemberId memberId;
                    private BusinessObjectType type;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.APPROVALS_SUBMIT);
                        memberId = new MemberId(caller.actorId());
                        type = optionalRuntimeType(objectType);
                        if (type == null || subjectId == null || subjectId <= 0) {
                            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                                    "Approval subject is invalid");
                        }
                        requireSameTenant(tenantId, recordAccessGuard.requireVisible(
                                caller, type, subjectId, false));
                    }

                    @Override
                    public ApprovalStartOptionsVO execute() {
                        requireSameTenant(tenantId, recordAccessGuard.requireVisible(
                                caller, type, subjectId, false));
                        var membership = support.activeMembership(tenantId, memberId);
                        List<ApprovalStartOptionVO> options = definitionRepository
                                .findActiveByObjectType(tenantId, type).stream()
                                .filter(definition -> definition.isApplicantAllowed(
                                        memberId, membership.getDepartmentId()))
                                .map(definition -> new ApprovalStartOptionVO(
                                        definition.getId().getValue(), definition.getName(),
                                        definition.getSteps().stream()
                                                .filter(step -> step.type()
                                                        == io.github.archetom.crm.domain.model.ApprovalStepType.APPLICANT_SELECT)
                                                .map(step -> new ApprovalStartSelectionVO(
                                                        step.code(), step.name())).toList()))
                                .toList();
                        return new ApprovalStartOptionsVO(type.name(), subjectId, options);
                    }
                });
    }

    @Override
    public Result<Pager<ApprovalSelectionCandidateVO>> startSelectionCandidates(
            AuthenticatedCaller caller, String objectType, Long subjectId, Long definitionId,
            String selectionCode, Integer page, Integer size) {
        return queryTemplate.execute(UseCaseOperation.APPROVAL_START_SELECTION_CANDIDATE_QUERY,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private MemberId memberId;
                    private BusinessObjectType type;
                    private ApprovalDefinitionId id;
                    private String code;
                    private int pageNumber;
                    private int pageSize;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.APPROVALS_SUBMIT);
                        memberId = new MemberId(caller.actorId());
                        type = optionalRuntimeType(objectType);
                        id = ApprovalServiceImpl.this.definitionId(definitionId);
                        code = selectionCode == null ? null : selectionCode.trim();
                        pageNumber = ApprovalServiceImpl.this.page(page);
                        pageSize = ApprovalServiceImpl.this.size(size);
                        if (type == null || subjectId == null || subjectId <= 0
                                || code == null || code.isEmpty() || pageNumber < 1
                                || pageSize < 1 || pageSize > 200) {
                            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                                    "Approval selection candidate query is invalid");
                        }
                        requireSameTenant(tenantId, recordAccessGuard.requireVisible(
                                caller, type, subjectId, false));
                    }

                    @Override
                    public Pager<ApprovalSelectionCandidateVO> execute() {
                        requireSameTenant(tenantId, recordAccessGuard.requireVisible(
                                caller, type, subjectId, false));
                        ApprovalDefinition definition = requireDefinition(tenantId, id);
                        if (definition.getObjectType() != type || !definition.isActive()) {
                            throw support.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                                    "Approval definition is unavailable for this subject");
                        }
                        var membership = support.activeMembership(tenantId, memberId);
                        if (!definition.isApplicantAllowed(memberId, membership.getDepartmentId())) {
                            throw support.error(ApplicationErrorCode.ACCESS_DENIED,
                                    "Caller is outside the approval initiator scope");
                        }
                        ApprovalStepDefinition selection = definition.getSteps().stream()
                                .filter(step -> step.type() == ApprovalStepType.APPLICANT_SELECT)
                                .filter(step -> step.code().equals(code))
                                .findFirst().orElseThrow(() -> support.error(
                                        ApplicationErrorCode.PARAMETER_INVALID,
                                        "Approval selection is unavailable"));
                        Map<FieldCode, FieldDefinition> fields = support.fields(tenantId, type);
                        Map<FieldCode, List<String>> facts = factResolver.resolve(tenantId, type,
                                subjectId, selection.conditions().stream()
                                        .map(condition -> condition.fieldCode()).toList(), fields);
                        if (!ApprovalConditionEvaluator.matches(selection.conditions(), facts,
                                support.searchableTypes(fields))) {
                            throw support.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                                    "Approval selection does not apply to this subject");
                        }
                        PageResult<MemberProfile> profiles = memberProfileRepository.findActiveByTenant(
                                tenantId, null, pageNumber, pageSize);
                        Pager<ApprovalSelectionCandidateVO> result = new Pager<>();
                        result.setPageNum(profiles.page());
                        result.setPageSize(profiles.size());
                        result.setTotalNum(profiles.total());
                        result.setObjectList(profiles.items().stream().filter(MemberProfile::isActive)
                                .map(profile -> new ApprovalSelectionCandidateVO(
                                        profile.getId().getValue(), profile.getDisplayName())).toList());
                        return result;
                    }
                });
    }

    @Override
    public Result<Pager<ApprovalTransferCandidateVO>> transferCandidates(
            AuthenticatedCaller caller, Long instanceId, Integer page, Integer size) {
        return queryTemplate.execute(UseCaseOperation.APPROVAL_GET,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private MemberId memberId;
                    private ApprovalInstanceId id;
                    private int pageNumber;
                    private int pageSize;
                    private ApprovalInstance instance;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.APPROVALS_REVIEW);
                        memberId = new MemberId(caller.actorId());
                        id = ApprovalServiceImpl.this.instanceId(instanceId);
                        pageNumber = ApprovalServiceImpl.this.page(page);
                        pageSize = ApprovalServiceImpl.this.size(size);
                        if (pageNumber < 1 || pageSize < 1 || pageSize > 200) {
                            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                                    "Approval transfer candidate page is invalid");
                        }
                    }

                    @Override
                    public Pager<ApprovalTransferCandidateVO> execute() {
                        instance = requireInstance(tenantId, id, false);
                        requireInstanceAccess(caller, tenantId, memberId, instance);
                        requireVisibleRecord(caller, tenantId, instance);
                        if (!allowedActions(caller, memberId, instance).contains("TRANSFER")) {
                            throw support.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                                    "Approval transfer is not allowed");
                        }
                        var profiles = memberProfileRepository.findActiveByTenant(
                                tenantId, null, pageNumber, pageSize);
                        Pager<ApprovalTransferCandidateVO> result = new Pager<>();
                        result.setPageNum(profiles.page());
                        result.setPageSize(profiles.size());
                        result.setTotalNum(profiles.total());
                        result.setObjectList(profiles.items().stream()
                                .filter(MemberProfile::isActive)
                                .filter(profile -> instance.canTransferPendingTaskTo(memberId,
                                        profile.getId()))
                                .map(profile -> new ApprovalTransferCandidateVO(
                                        profile.getId().getValue(), profile.getDisplayName()))
                                .toList());
                        return result;
                    }
                });
    }

    @Override
    public Result<ApprovalInstanceVO> act(
            AuthenticatedCaller caller, Long instanceId, ApprovalActionRequest request) {
        return commandTemplate.execute(UseCaseOperation.APPROVAL_ACTION,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private MemberId memberId;
                    private ApprovalInstanceId id;
                    private ApprovalActionType action;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.APPROVALS_REVIEW);
                        memberId = new MemberId(caller.actorId());
                        id = instanceId(instanceId);
                        require(request, "Approval action request");
                        action = actionType(request.getAction());
                    }

                    @Override
                    public ApprovalInstanceVO execute() {
                        ApprovalInstance instance = requireInstance(tenantId, id, true);
                        requireVisibleRecord(caller, tenantId, instance);
                        support.requireVersion(instance, request.getVersion());
                        if (!allowedActions(caller, memberId, instance).contains(action.name())) {
                            throw support.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                                    "Approval action is not allowed");
                        }
                        MemberId transfer = transferTarget(tenantId, action,
                                request.getTransferActorId());
                        instance.act(memberId, action, transfer, request.getRemark());
                        oaRequestAttachmentTerminalCoordinator.cutoffIfTerminal(tenantId, instance);
                        return toVO(caller, tenantId, memberId,
                                instanceRepository.save(tenantId, instance));
                    }
                });
    }

    @Override
    public Result<ApprovalBatchActionResultVO> batchAct(
            AuthenticatedCaller caller, ApprovalBatchActionRequest request) {
        return commandTemplate.execute(UseCaseOperation.APPROVAL_BATCH_ACTION,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private MemberId memberId;
                    private List<ApprovalActionItemRequest> actions;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.APPROVALS_REVIEW);
                        memberId = new MemberId(caller.actorId());
                        require(request, "Approval batch action request");
                        List<ApprovalActionItemRequest> requested = request.getActions() == null
                                ? List.of() : request.getActions();
                        if (requested.stream().anyMatch(item -> item == null
                                || item.getInstanceId() == null || item.getInstanceId() <= 0)) {
                            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                                    "Approval batch actions are invalid");
                        }
                        actions = requested.stream().sorted(Comparator.comparing(
                                ApprovalActionItemRequest::getInstanceId)).toList();
                        if (actions.isEmpty() || actions.size() > 50
                                || new LinkedHashSet<>(actions.stream().map(
                                ApprovalActionItemRequest::getInstanceId).toList()).size()
                                != actions.size()) {
                            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                                    "Approval batch actions are invalid");
                        }
                    }

                    @Override
                    public ApprovalBatchActionResultVO execute() {
                        List<ApprovalInstance> locked = new ArrayList<>();
                        List<ApprovalActionType> actionTypes = new ArrayList<>();
                        List<MemberId> transferTargets = new ArrayList<>();
                        for (ApprovalActionItemRequest item : actions) {
                            ApprovalInstance instance = requireInstance(tenantId,
                                    instanceId(item.getInstanceId()), true);
                            requireVisibleRecord(caller, tenantId, instance);
                            if (!java.util.Objects.equals(instance.getVersion(), item.getVersion())) {
                                return rejectedBatch(actions, item.getInstanceId(), "VERSION_CONFLICT");
                            }
                            ApprovalActionType action = actionType(item.getAction());
                            if (!allowedActions(caller, memberId, instance).contains(action.name())) {
                                return rejectedBatch(actions, item.getInstanceId(),
                                        "APPROVAL_ACTION_NOT_ALLOWED");
                            }
                            MemberId target;
                            try {
                                target = transferTarget(tenantId, action, item.getTransferActorId());
                            } catch (ApplicationException exception) {
                                return rejectedBatch(actions, item.getInstanceId(), "TRANSFER_TARGET_INVALID");
                            }
                            if (action == ApprovalActionType.TRANSFER
                                    && !instance.canTransferPendingTaskTo(memberId, target)) {
                                return rejectedBatch(actions, item.getInstanceId(), "TRANSFER_TARGET_INVALID");
                            }
                            locked.add(instance);
                            actionTypes.add(action);
                            transferTargets.add(target);
                        }
                        List<ApprovalInstance> saved = new ArrayList<>();
                        for (int index = 0; index < actions.size(); index++) {
                            ApprovalActionItemRequest item = actions.get(index);
                            ApprovalInstance instance = locked.get(index);
                            ApprovalActionType action = actionTypes.get(index);
                            instance.act(memberId, action,
                                    transferTargets.get(index),
                                    item.getRemark());
                            oaRequestAttachmentTerminalCoordinator.cutoffIfTerminal(tenantId, instance);
                            saved.add(instanceRepository.save(tenantId, instance));
                        }
                        return new ApprovalBatchActionResultVO(true, true, saved.stream()
                                .map(instance -> new ApprovalBatchActionItemVO(instance.getId().getValue(),
                                        "APPLIED", instance.getVersion(), "NONE")).toList());
                    }
                });
    }

    @Override
    public Result<ApprovalInstanceVO> withdraw(
            AuthenticatedCaller caller, Long instanceId, Long version) {
        return commandTemplate.execute(UseCaseOperation.APPROVAL_WITHDRAW,
                terminalMutation(caller, instanceId, version, true));
    }

    @Override
    public Result<ApprovalInstanceVO> cancel(
            AuthenticatedCaller caller, Long instanceId, Long version) {
        return commandTemplate.execute(UseCaseOperation.APPROVAL_CANCEL,
                terminalMutation(caller, instanceId, version, false));
    }

    private ServiceOperation<ApprovalDefinitionVO> definitionMutation(
            AuthenticatedCaller caller, ApprovalDefinitionId id,
            ApprovalDefinitionRequest request) {
        return new ServiceOperation<>() {
            private TenantId tenantId;
            private BusinessObjectType objectType;

            @Override
            public void validate() {
                tenantId = accessGuard.requireActiveMembership(
                        caller, AuthorityCatalog.APPROVALS_MANAGE);
                require(request, "Approval definition request");
                objectType = definitionType(request.getObjectType());
            }

            @Override
            public ApprovalDefinitionVO execute() {
                objectLock.acquire(tenantId, objectType);
                var fields = support.fields(tenantId, objectType);
                var steps = support.steps(request.getSteps(), fields);
                ApprovalDefinition definition;
                if (id == null) {
                    if (request.getVersion() != null) {
                        throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                                "New approval definition cannot have a version");
                    }
                    definition = ApprovalDefinition.create(tenantId, objectType,
                            request.getName(), request.getDescription(),
                            support.parse(ApprovalRepeatMode.class, request.getRepeatMode(),
                                    "Approval repeat mode"),
                            support.memberIds(request.getInitiatorActorIds()),
                            support.departmentIds(request.getInitiatorDepartmentIds()),
                            support.memberIds(request.getManagerActorIds()), steps);
                } else {
                    definition = requireDefinitionForUpdate(tenantId, id);
                    support.requireVersion(definition, request.getVersion());
                    if (definition.getObjectType() != objectType) {
                        throw support.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                                "Approval definition object type cannot change");
                    }
                    definition.replace(request.getName(), request.getDescription(),
                            support.parse(ApprovalRepeatMode.class, request.getRepeatMode(),
                                    "Approval repeat mode"),
                            support.memberIds(request.getInitiatorActorIds()),
                            support.departmentIds(request.getInitiatorDepartmentIds()),
                            support.memberIds(request.getManagerActorIds()), steps);
                }
                support.validateDefinitionReferences(tenantId, definition);
                validateOaFormFactFields(tenantId, definition);
                if (definition.getId() != null) {
                    validateStageWorkflowFactLimits(tenantId, definition);
                }
                return ApprovalAssembler.toVO(definitionRepository.save(tenantId, definition));
            }
        };
    }

    private ServiceOperation<ApprovalInstanceVO> terminalMutation(
            AuthenticatedCaller caller, Long rawId, Long version, boolean withdraw) {
        return new ServiceOperation<>() {
            private TenantId tenantId;
            private MemberId memberId;
            private ApprovalInstanceId id;

            @Override
            public void validate() {
                tenantId = accessGuard.requireActiveMembership(caller, withdraw
                        ? AuthorityCatalog.APPROVALS_SUBMIT : AuthorityCatalog.APPROVALS_REVIEW);
                memberId = new MemberId(caller.actorId());
                id = instanceId(rawId);
            }

            @Override
            public ApprovalInstanceVO execute() {
                ApprovalInstance instance = requireInstance(tenantId, id, true);
                requireVisibleRecord(caller, tenantId, instance);
                support.requireVersion(instance, version);
                    if (withdraw) {
                        if (!allowedActions(caller, memberId, instance).contains("WITHDRAW")) {
                            throw support.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                                    "Approval withdrawal is not allowed");
                        }
                        instance.withdraw(memberId);
                    } else {
                        if (!allowedActions(caller, memberId, instance).contains("CANCEL")) {
                            throw support.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                                    "Approval cancellation is not allowed");
                        }
                    instance.cancel();
                }
                oaRequestAttachmentTerminalCoordinator.cutoffIfTerminal(tenantId, instance);
                return toVO(caller, tenantId, memberId,
                        instanceRepository.save(tenantId, instance));
            }
        };
    }

    private ApprovalDefinition requireDefinition(
            TenantId tenantId, ApprovalDefinitionId id) {
        return definitionRepository.findById(tenantId, id).orElseThrow(() ->
                support.error(ApplicationErrorCode.RESOURCE_NOT_FOUND,
                        "Approval definition was not found"));
    }

    private ApprovalDefinition requireDefinitionForUpdate(
            TenantId tenantId, ApprovalDefinitionId id) {
        return definitionRepository.findByIdForUpdate(tenantId, id).orElseThrow(() ->
                support.error(ApplicationErrorCode.RESOURCE_NOT_FOUND,
                        "Approval definition was not found"));
    }

    private ApprovalInstance requireInstance(
            TenantId tenantId, ApprovalInstanceId id, boolean lock) {
        return (lock ? instanceRepository.findByIdForUpdate(tenantId, id)
                : instanceRepository.findById(tenantId, id)).orElseThrow(() ->
                support.error(ApplicationErrorCode.RESOURCE_NOT_FOUND,
                        "Approval instance was not found"));
    }

    private void requireInstanceAccess(AuthenticatedCaller caller, TenantId tenantId,
                                       MemberId memberId, ApprovalInstance instance) {
        if (instance.isParticipant(memberId)
                || caller.hasAuthority(AuthorityCatalog.APPROVALS_MANAGE)) {
            return;
        }
        if (!instance.isManager(memberId)) {
            throw support.error(ApplicationErrorCode.RESOURCE_NOT_FOUND,
                    "Approval instance was not found");
        }
    }

    private void requireVisibleRecord(
            AuthenticatedCaller caller, TenantId tenantId, ApprovalInstance instance) {
        requireSameTenant(tenantId, recordAccessGuard.requireVisible(caller,
                instance.getObjectType(), instance.getSubjectId(), false));
    }

    private ApprovalVisibilityScan scanVisible(
            AuthenticatedCaller caller, TenantId tenantId, ApprovalInstanceQuery query,
            boolean collectRequestedPage) {
        long requestedStart = (long) (query.page() - 1) * query.size();
        long requestedEnd = requestedStart + query.size();
        long visibleTotal = 0;
        List<ApprovalInstanceId> requestedIds = collectRequestedPage
                ? new ArrayList<>(query.size()) : List.of();
        int rawPageNumber = 1;
        while (true) {
            ApprovalInstanceQuery rawQuery = new ApprovalInstanceQuery(query.memberId(),
                    query.bucket(), query.status(), query.objectType(), query.subjectId(),
                    query.keyword(), rawPageNumber, VISIBILITY_SCAN_SIZE);
            PageResult<ApprovalInstanceReference> rawPage = instanceRepository.scanPage(
                    tenantId, rawQuery);
            if (rawPage.items().isEmpty()) {
                break;
            }
            Set<RecordReference> visibleRecords = visibleRecords(
                    caller, tenantId, rawPage.items());
            for (ApprovalInstanceReference instance : rawPage.items()) {
                if (!visibleRecords.contains(new RecordReference(
                        instance.objectType(), instance.subjectId()))) {
                    continue;
                }
                if (collectRequestedPage && visibleTotal >= requestedStart
                        && visibleTotal < requestedEnd) {
                    requestedIds.add(instance.instanceId());
                }
                visibleTotal++;
            }
            long consumed = (long) rawPageNumber * VISIBILITY_SCAN_SIZE;
            if (consumed >= rawPage.total()
                    || rawPage.items().size() < VISIBILITY_SCAN_SIZE) {
                break;
            }
            rawPageNumber++;
        }
        return new ApprovalVisibilityScan(visibleTotal, List.copyOf(requestedIds));
    }

    private Set<RecordReference> visibleRecords(
            AuthenticatedCaller caller, TenantId tenantId,
            List<ApprovalInstanceReference> instances) {
        EnumMap<BusinessObjectType, LinkedHashSet<Long>> grouped =
                new EnumMap<>(BusinessObjectType.class);
        instances.forEach(instance -> grouped.computeIfAbsent(instance.objectType(),
                        ignored -> new LinkedHashSet<>()).add(instance.subjectId()));
        LinkedHashSet<RecordReference> visible = new LinkedHashSet<>();
        grouped.forEach((type, ids) -> {
            try {
                recordAccessGuard.visibleSubjectIds(caller, tenantId, type, ids).forEach(
                        id -> visible.add(new RecordReference(type, id)));
            } catch (ApplicationException exception) {
                if (exception.getErrorCode() != ApplicationErrorCode.RESOURCE_NOT_FOUND
                        && exception.getErrorCode() != ApplicationErrorCode.ACCESS_DENIED) {
                    throw exception;
                }
            }
        });
        return Set.copyOf(visible);
    }

    private record ApprovalVisibilityScan(
            long total, List<ApprovalInstanceId> instanceIds) {
    }

    private record RecordReference(BusinessObjectType objectType, Long subjectId) {
    }

    private Set<FieldCode> factCodes(
            ApprovalDefinition definition, java.util.Collection<FieldCode> requested) {
        LinkedHashSet<FieldCode> codes = new LinkedHashSet<>(requested);
        definition.getSteps().forEach(step -> step.conditions().forEach(
                condition -> codes.add(condition.fieldCode())));
        if (codes.size() > ApprovalInstance.MAX_ROUTING_FACTS) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Approval fact fields exceed the supported limit");
        }
        return java.util.Collections.unmodifiableSet(codes);
    }

    private Map<FieldCode, List<String>> authoritativeFacts(
            AuthenticatedCaller caller, TenantId tenantId, MemberId memberId,
            ApprovalDefinition definition, Long subjectId) {
        Map<FieldCode, io.github.archetom.crm.domain.entity.FieldDefinition> fields =
                support.fields(tenantId, definition.getObjectType());
        requireSameTenant(tenantId, recordAccessGuard.requireVisible(caller,
                definition.getObjectType(), subjectId, false));
        return factResolver.resolve(tenantId, definition.getObjectType(), subjectId,
                factCodes(definition, Set.of()), fields);
    }

    private ApprovalInstanceVO toVO(
            AuthenticatedCaller caller, TenantId tenantId,
            MemberId memberId, ApprovalInstance instance) {
        Map<FieldCode, io.github.archetom.crm.domain.model.EffectiveFieldAccess> accesses =
                fieldAccessSupport.accesses(tenantId, memberId, instance.getObjectType());
        return ApprovalAssembler.toVO(instance, accesses,
                factVisibilitySupport.hiddenForInstance(caller, tenantId, instance),
                fileFieldSnapshotOutput.visible(fileFieldRepository.findApprovalInstanceValues(
                        tenantId, instance.getId()), accesses,
                        caller != null && caller.hasAuthority(AuthorityCatalog.FILES_READ)),
                allowedActions(caller, memberId, instance));
    }

    private ApprovalInstanceSummaryVO toSummary(
            ApprovalInstance instance,
            Map<FieldCode, io.github.archetom.crm.domain.model.EffectiveFieldAccess> accesses,
            Set<FieldCode> hiddenRelationshipFacts, Set<String> allowedActions) {
        return ApprovalAssembler.toSummary(
                instance, accesses, hiddenRelationshipFacts, allowedActions);
    }

    private ApprovalBatchActionResultVO rejectedBatch(
            List<ApprovalActionItemRequest> actions, Long rejectedInstanceId, String errCode) {
        return new ApprovalBatchActionResultVO(true, false, actions.stream().map(item ->
                item.getInstanceId().equals(rejectedInstanceId)
                        ? new ApprovalBatchActionItemVO(item.getInstanceId(), "REJECTED", null, errCode)
                        : new ApprovalBatchActionItemVO(item.getInstanceId(), "NOT_APPLIED", null,
                                "BATCH_ABORTED")).toList());
    }

    private ApprovalActionType actionType(
            io.github.archetom.crm.api.dto.request.ApprovalAction action) {
        if (action == null) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Approval action is required");
        }
        return ApprovalActionType.valueOf(action.name());
    }

    private Set<String> allowedActions(
            AuthenticatedCaller caller, MemberId memberId, ApprovalInstance instance) {
        if (caller == null || memberId == null || instance == null
                || instance.getStatus() != ApprovalInstanceStatus.PENDING) {
            return Set.of();
        }
        LinkedHashSet<String> actions = new LinkedHashSet<>();
        if (caller.hasAuthority(AuthorityCatalog.APPROVALS_REVIEW)
                && instance.hasPendingTaskFor(memberId)) {
            actions.add(ApprovalActionType.APPROVE.name());
            actions.add(ApprovalActionType.REJECT.name());
            actions.add(ApprovalActionType.TRANSFER.name());
        }
        if (caller.hasAuthority(AuthorityCatalog.APPROVALS_SUBMIT)
                && instance.canBeWithdrawnBy(memberId)) {
            actions.add("WITHDRAW");
        }
        if (caller.hasAuthority(AuthorityCatalog.APPROVALS_REVIEW)
                && (instance.isManager(memberId)
                || caller.hasAuthority(AuthorityCatalog.APPROVALS_MANAGE))) {
            actions.add("CANCEL");
        }
        return Set.copyOf(actions);
    }

    private void snapshotFileFields(TenantId tenantId, ApprovalInstance instance,
                                    BusinessObjectType objectType, Long subjectId) {
        AttachmentTarget target;
        try {
            target = new AttachmentTarget(AttachmentTargetType.valueOf(objectType.name()), subjectId);
        } catch (RuntimeException exception) {
            return;
        }
        Map<FieldCode, List<FileFieldValue>> values = new java.util.LinkedHashMap<>();
        fileFieldRepository.findCrmValues(tenantId, target).forEach((field, bound) -> values.put(field,
                bound.stream().map(value -> new FileFieldValue(field, value.file().getId(), value.ordinal(),
                        value.contentVersion(), value.evidenceDigest())).toList()));
        fileFieldRepository.snapshotApprovalInstance(tenantId, instance.getId(), objectType, values);
    }

    private void validateStageWorkflowFactLimits(
            TenantId tenantId, ApprovalDefinition definition) {
        List<StageWorkflowDefinition> workflows = stageWorkflowRepository
                .findReferencingApprovalDefinition(tenantId, definition.getId());
        for (StageWorkflowDefinition workflow : workflows) {
            for (var stage : workflow.getStages()) {
                if (definition.getId().equals(stage.approvalDefinitionId())
                        && !StageApprovalFactPolicy.isWithinLimit(stage, definition)) {
                    throw support.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                            "Approval definition exceeds a stage workflow fact limit");
                }
            }
        }
    }

    private void validateOaFormFactFields(
            TenantId tenantId, ApprovalDefinition definition) {
        if (definition.getId() == null
                || definition.getObjectType() != BusinessObjectType.OA_REQUEST) {
            return;
        }
        Set<FieldCode> conditionFields = definition.getSteps().stream()
                .flatMap(step -> step.conditions().stream())
                .map(condition -> condition.fieldCode())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        for (var form : oaFormRepository.findActiveReferencingApprovalDefinition(
                tenantId, definition.getId())) {
            Set<FieldCode> allowed = form.getFields().stream()
                    .map(field -> field.fieldCode())
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            allowed.addAll(OaRequestFieldCatalog.serverDerivedCodes());
            if (!allowed.containsAll(conditionFields)) {
                throw support.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                        "Approval conditions reference fields outside an active OA form");
            }
        }
    }

    private MemberId transferTarget(TenantId tenantId, ApprovalActionType action, Long actorId) {
        if (action != ApprovalActionType.TRANSFER) {
            if (actorId != null) {
                throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                        "Approval transfer target is only valid for transfer actions");
            }
            return null;
        }
        if (actorId == null) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Approval transfer target is required");
        }
        MemberId target = new MemberId(actorId);
        support.validateReferences(tenantId, List.of(target), List.of(), List.of());
        return target;
    }

    private BusinessObjectType definitionType(String value) {
        BusinessObjectType type = support.parse(BusinessObjectType.class,
                value, "Business object type");
        return type == BusinessObjectType.OA_REQUEST ? type : ensureRuntimeType(type);
    }

    private BusinessObjectType optionalDefinitionType(String value) {
        BusinessObjectType type = support.parseOptional(BusinessObjectType.class,
                value, "Business object type");
        return type == null ? null
                : type == BusinessObjectType.OA_REQUEST ? type : ensureRuntimeType(type);
    }

    private BusinessObjectType optionalRuntimeType(String value) {
        BusinessObjectType type = support.parseOptional(BusinessObjectType.class,
                value, "Business object type");
        return type == null ? null
                : type == BusinessObjectType.OA_REQUEST ? type : ensureRuntimeType(type);
    }

    private BusinessObjectType ensureRuntimeType(BusinessObjectType type) {
        return switch (type) {
            case LEAD, CUSTOMER, CONTACT, OPPORTUNITY, CONTRACT, INVOICE, RECEIVABLE -> type;
            default -> throw support.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                    "Business object approval runtime is not available");
        };
    }

    private ApprovalDefinitionId definitionId(Long value) {
        try {
            return new ApprovalDefinitionId(value);
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Approval definition ID is invalid");
        }
    }

    private ApprovalInstanceId instanceId(Long value) {
        try {
            return new ApprovalInstanceId(value);
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Approval instance ID is invalid");
        }
    }

    private void requireSameTenant(TenantId expected, TenantId actual) {
        if (!expected.sameValueAs(actual)) {
            throw support.error(ApplicationErrorCode.ACCESS_DENIED,
                    "Business record tenant does not match");
        }
    }

    private void require(Object value, String description) {
        if (value == null) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    description + " is required");
        }
    }

    private int page(Integer value) {
        return value == null ? 1 : value;
    }

    private int size(Integer value) {
        return value == null ? 20 : value;
    }

    private <S, T> Pager<T> pager(PageResult<S> page,
                                  java.util.function.Function<S, T> converter) {
        Pager<T> result = new Pager<>();
        result.setPageNum(page.page());
        result.setPageSize(page.size());
        result.setTotalNum(page.total());
        result.setObjectList(page.items().stream().map(converter).toList());
        return result;
    }
}
