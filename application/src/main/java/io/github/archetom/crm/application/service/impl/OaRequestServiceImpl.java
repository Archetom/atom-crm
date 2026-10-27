package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.OaRequestPreviewRequest;
import io.github.archetom.crm.api.dto.request.OaRequestQueryRequest;
import io.github.archetom.crm.api.dto.request.OaRequestSubmitRequest;
import io.github.archetom.crm.application.assembler.ApprovalAssembler;
import io.github.archetom.crm.application.assembler.OaRequestAssembler;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.port.out.CustomizationObjectLock;
import io.github.archetom.crm.application.security.EffectiveFieldAccessResolver;
import io.github.archetom.crm.application.security.OaRequestAccessResolver;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.service.OaRequestService;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.application.vo.OaRequestPreviewVO;
import io.github.archetom.crm.application.vo.OaRequestVO;
import io.github.archetom.crm.domain.entity.ApprovalDefinition;
import io.github.archetom.crm.domain.entity.ApprovalInstance;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.OaFormDefinition;
import io.github.archetom.crm.domain.entity.OaRequest;
import io.github.archetom.crm.domain.entity.OrganizationMembership;
import io.github.archetom.crm.domain.exception.ApprovalDomainException;
import io.github.archetom.crm.domain.model.ApprovalInstanceStatus;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.FileFieldValue;
import io.github.archetom.crm.domain.model.OaRequestQuery;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.repository.ApprovalDefinitionRepository;
import io.github.archetom.crm.domain.repository.ApprovalInstanceRepository;
import io.github.archetom.crm.domain.repository.OaFormRepository;
import io.github.archetom.crm.domain.repository.OaRequestRepository;
import io.github.archetom.crm.domain.repository.FileFieldRepository;
import io.github.archetom.crm.domain.repository.OrganizationMembershipRepository;
import io.github.archetom.crm.domain.repository.OrganizationTenantRepository;
import io.github.archetom.crm.domain.repository.PageResult;
import io.github.archetom.crm.domain.valueobject.ApprovalDefinitionId;
import io.github.archetom.crm.domain.valueobject.ApprovalInstanceId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.OaFormId;
import io.github.archetom.crm.domain.valueobject.OaRequestId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OaRequestServiceImpl implements OaRequestService {

    private final OaRequestRepository requestRepository;
    private final OaFormRepository formRepository;
    private final ApprovalDefinitionRepository definitionRepository;
    private final ApprovalInstanceRepository instanceRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final OrganizationTenantRepository tenantRepository;
    private final EffectiveFieldAccessResolver fieldAccessResolver;
    private final OrganizationAccessGuard accessGuard;
    private final OaRequestAccessResolver requestAccessResolver;
    private final CustomizationObjectLock objectLock;
    private final OaRequestApplicationSupport support;
    private final ApprovalApplicationSupport approvalSupport;
    private final ApprovalSubmissionCoordinator submissionCoordinator;
    private final BusinessRecordFactResolver factResolver;
    private final FileFieldRepository fileFieldRepository;
    private final io.github.archetom.crm.application.service.FileFieldBindingService fileFieldBindingService;
    private final CommandServiceTemplate commandTemplate;
    private final QueryServiceTemplate queryTemplate;

    @Override
    public Result<OaRequestPreviewVO> preview(
            AuthenticatedCaller caller, OaRequestPreviewRequest request) {
        return queryTemplate.execute(UseCaseOperation.OA_REQUEST_PREVIEW,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private MemberId memberId;
                    private OaFormId formId;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.OA_REQUESTS_SUBMIT);
                        memberId = new MemberId(caller.actorId());
                        require(request, "OA request preview");
                        formId = formId(request.getFormId());
                    }

                    @Override
                    public OaRequestPreviewVO execute() {
                        OaFormDefinition form = requireActiveForm(tenantId, formId);
                        requireFormVersion(form, request.getFormVersion());
                        OrganizationMembership membership = activeMembership(tenantId, memberId);
                        Map<FieldCode, FieldDefinition> fields = support.fields(tenantId, false);
                        Map<FieldCode, io.github.archetom.crm.domain.model.EffectiveFieldAccess>
                                accesses = fieldAccessResolver.resolve(
                                tenantId, memberId, BusinessObjectType.OA_REQUEST);
                        ApprovalDefinition definition = requireDefinition(
                                tenantId, form.getApprovalDefinitionId());
                        requireAvailableForm(form, memberId, membership, definition, fields, accesses);
                        LocalDateTime submittedTime = now();
                        Map<FieldCode, List<String>> facts = support.facts(
                                tenantId, form, memberId, membership.getDepartmentId(),
                                submittedTime, request.getValues(), fields, accesses);
                        Map<FieldCode, FieldDefinition> fileDefinitions = support.fileFields(form, fields);
                        support.requireConfiguredFileValues(form, request.getFileFields(), fileDefinitions);
                        fileFieldBindingService.prepareOaPreview(tenantId, memberId, form.getId(),
                                form.getVersion(), request.getFileFieldDraftId(), request.getFileFields(),
                                fileDefinitions, accesses);
                        var resolution = submissionCoordinator.resolve(
                                tenantId, definition, memberId, facts,
                                request.getSelectedApprovers());
                        return new OaRequestPreviewVO(form.getId().getValue(),
                                form.getVersion(), definition.getId().getValue(),
                                definition.getVersion(), resolution.route().isEmpty(),
                                resolution.route().stream()
                                        .map(ApprovalAssembler::toVO).toList());
                    }
                });
    }

    @Override
    public Result<OaRequestVO> submit(
            AuthenticatedCaller caller, OaRequestSubmitRequest request) {
        return commandTemplate.execute(UseCaseOperation.OA_REQUEST_SUBMIT,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private MemberId memberId;
                    private OaFormId formId;
                    private String idempotencyKey;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.OA_REQUESTS_SUBMIT);
                        memberId = new MemberId(caller.actorId());
                        require(request, "OA request submission");
                        formId = formId(request.getFormId());
                        idempotencyKey = normalizeIdempotencyKey(
                                request.getIdempotencyKey());
                    }

                    @Override
                    public OaRequestVO execute() {
                        if (!tenantRepository.lockForTenantChanges(tenantId)) {
                            throw notFound("OA request tenant was not found");
                        }
                        OrganizationMembership membership =
                                activeMembershipForShare(tenantId, memberId);
                        OaRequest existing = requestRepository.findByIdempotencyKey(
                                tenantId, memberId, idempotencyKey).orElse(null);
                        if (existing != null) {
                            Map<FieldCode, List<FileFieldValue>> fileValues = storedFileValues(
                                    tenantId, existing.getId());
                            requireSameFileIds(request.getFileFields(), fileValues);
                            String digest = support.retryDigest(tenantId, existing,
                                    request, support.fields(tenantId, true), fileValues);
                            if (!existing.getSubmissionDigest().equals(digest)) {
                                throw support.conflict();
                            }
                            return output(tenantId, memberId, existing,
                                    requireApprovalInstance(tenantId, existing));
                        }

                        objectLock.acquire(tenantId, BusinessObjectType.OA_REQUEST);
                        OaFormDefinition form = formRepository.findByIdForUpdate(
                                        tenantId, formId)
                                .filter(value -> value.isActive())
                                .orElseThrow(() -> notFound("OA form was not found"));
                        requireFormVersion(form, request.getFormVersion());
                        ApprovalDefinition definition = requireDefinition(
                                tenantId, form.getApprovalDefinitionId());
                        Map<FieldCode, FieldDefinition> fields = support.fields(tenantId, false);
                        Map<FieldCode, io.github.archetom.crm.domain.model.EffectiveFieldAccess>
                                accesses = fieldAccessResolver.resolve(
                                tenantId, memberId, BusinessObjectType.OA_REQUEST);
                        requireAvailableForm(form, memberId, membership, definition, fields, accesses);
                        LocalDateTime submittedTime = now();
                        Map<FieldCode, List<String>> facts = support.facts(
                                tenantId, form, memberId, membership.getDepartmentId(),
                                submittedTime, request.getValues(), fields, accesses);
                        Map<FieldCode, FieldDefinition> fileDefinitions = support.fileFields(form, fields);
                        support.requireConfiguredFileValues(form, request.getFileFields(), fileDefinitions);
                        Map<FieldCode, List<FileFieldValue>> fileValues = fileFieldBindingService.prepareOaSubmission(
                                tenantId, memberId, form.getId(), form.getVersion(), request.getFileFieldDraftId(),
                                request.getFileFields(), fileDefinitions, accesses);
                        String digest = support.digestWithFileValues(form.getId().getValue(),
                                form.getVersion(), facts, request.getSelectedApprovers(),
                                request.getWatcherActorIds(), fileValues);
                        OaRequest value = OaRequest.submit(tenantId, form.getId(),
                                form.getVersion(), form.getNormalizedCode(), memberId,
                                membership.getDepartmentId(), idempotencyKey,
                                digest, facts, submittedTime);
                        requestRepository.save(tenantId, value, fields.values());
                        fileFieldBindingService.consumeOaDraft(tenantId, memberId, form.getId(),
                                form.getVersion(), request.getFileFieldDraftId(), value.getId(),
                                request.getFileFields(), fileDefinitions, accesses);
                        ApprovalInstance approval = submissionCoordinator.submit(
                                tenantId, definition, value.getId().getValue(),
                                submissionCoordinator.oaIdempotencyKey(value.getId()),
                                value.getRequestTitle(), memberId,
                                () -> factResolver.resolve(tenantId,
                                        BusinessObjectType.OA_REQUEST,
                                        value.getId().getValue(), facts.keySet(), fields),
                                request.getSelectedApprovers(),
                                approvalSupport.memberIds(request.getWatcherActorIds()));
                        fileFieldRepository.snapshotApprovalInstance(tenantId, approval.getId(),
                                BusinessObjectType.OA_REQUEST, fileValues);
                        value.linkApproval(approval.getId());
                        requestRepository.save(tenantId, value, fields.values());
                        return OaRequestAssembler.toVO(value, approval.getStatus(), accesses);
                    }
                });
    }

    private Map<FieldCode, List<FileFieldValue>> storedFileValues(TenantId tenantId, OaRequestId requestId) {
        LinkedHashMap<FieldCode, List<FileFieldValue>> result = new LinkedHashMap<>();
        fileFieldRepository.findOaValues(tenantId, requestId).forEach((field, values) -> result.put(field,
                values.stream().map(value -> new FileFieldValue(field, value.file().getId(), value.ordinal(),
                        value.contentVersion(), value.evidenceDigest())).toList()));
        return result;
    }

    private void requireSameFileIds(Map<String, List<Long>> requested,
                                    Map<FieldCode, List<FileFieldValue>> stored) {
        Map<String, List<Long>> expected = new LinkedHashMap<>();
        stored.forEach((field, values) -> expected.put(field.getValue(), values.stream()
                .sorted(java.util.Comparator.comparingInt(FileFieldValue::ordinal))
                .map(value -> value.fileId().getValue()).toList()));
        Map<String, List<Long>> actual = requested == null ? Map.of() : requested;
        LinkedHashMap<String, List<Long>> normalized = new LinkedHashMap<>();
        actual.forEach((field, values) -> normalized.put(field, values == null ? List.of() : List.copyOf(values)));
        if (!expected.equals(normalized)) throw support.conflict();
    }

    @Override
    public Result<Pager<OaRequestVO>> query(
            AuthenticatedCaller caller, OaRequestQueryRequest request) {
        return queryTemplate.execute(UseCaseOperation.OA_REQUEST_QUERY,
                new ServiceOperation<>() {
                    private OaRequestAccessResolver.OaRequestAccess access;
                    private OaRequestQuery query;

                    @Override
                    public void validate() {
                        access = requestAccessResolver.access(caller);
                        require(request, "OA request query");
                        query = new OaRequestQuery(access.memberId(),
                                access.tenantManager(), optionalFormId(request.getFormId()),
                                optionalStatus(request.getStatus()),
                                request.getPage() == null ? 1 : request.getPage(),
                                request.getSize() == null ? 20 : request.getSize());
                    }

                    @Override
                    public Pager<OaRequestVO> execute() {
                        PageResult<OaRequest> page = requestRepository.findVisiblePage(
                                access.tenantId(), query);
                        Map<Long, ApprovalInstanceStatus> statuses = statuses(
                                access.tenantId(), page.items());
                        var fieldAccess = fieldAccessResolver.resolve(access.tenantId(),
                                access.memberId(), BusinessObjectType.OA_REQUEST);
                        Pager<OaRequestVO> result = new Pager<>();
                        result.setPageNum(page.page());
                        result.setPageSize(page.size());
                        result.setTotalNum(page.total());
                        result.setObjectList(page.items().stream().map(value ->
                                OaRequestAssembler.toVO(value,
                                        status(statuses, value), fieldAccess)).toList());
                        return result;
                    }
                });
    }

    @Override
    public Result<OaRequestVO> get(AuthenticatedCaller caller, Long requestId) {
        return queryTemplate.execute(UseCaseOperation.OA_REQUEST_GET,
                new ServiceOperation<>() {
                    private OaRequestAccessResolver.OaRequestAccess access;
                    private OaRequestId id;

                    @Override
                    public void validate() {
                        access = requestAccessResolver.access(caller);
                        id = requestId(requestId);
                    }

                    @Override
                    public OaRequestVO execute() {
                        OaRequest value = requestRepository.findVisibleById(
                                        access.tenantId(), id, access.memberId(),
                                        access.tenantManager())
                                .orElseThrow(() -> notFound("OA request was not found"));
                        ApprovalInstance instance = requireApprovalInstance(
                                access.tenantId(), value);
                        return OaRequestAssembler.toVO(value, instance.getStatus(),
                                fieldAccessResolver.resolve(access.tenantId(),
                                        access.memberId(), BusinessObjectType.OA_REQUEST));
                    }
                });
    }

    private OaRequestVO output(
            TenantId tenantId, MemberId memberId,
            OaRequest value, ApprovalInstance instance) {
        return OaRequestAssembler.toVO(value, instance.getStatus(),
                fieldAccessResolver.resolve(
                        tenantId, memberId, BusinessObjectType.OA_REQUEST));
    }

    private Map<Long, ApprovalInstanceStatus> statuses(
            TenantId tenantId, Collection<OaRequest> requests) {
        List<ApprovalInstanceId> ids = requests.stream()
                .map(OaRequest::getApprovalInstanceId).toList();
        return instanceRepository.findSummariesByIds(tenantId, ids).stream()
                .collect(Collectors.toMap(value -> value.id().getValue(),
                        value -> value.status(), (left, right) -> left,
                        LinkedHashMap::new));
    }

    private ApprovalInstanceStatus status(
            Map<Long, ApprovalInstanceStatus> statuses, OaRequest request) {
        ApprovalInstanceStatus status = statuses.get(
                request.getApprovalInstanceId().getValue());
        if (status == null) {
            throw new IllegalStateException("OA request approval instance is missing");
        }
        return status;
    }

    private ApprovalInstance requireApprovalInstance(
            TenantId tenantId, OaRequest request) {
        if (request.getApprovalInstanceId() == null) {
            throw new IllegalStateException("OA request approval association is missing");
        }
        return instanceRepository.findById(
                        tenantId, request.getApprovalInstanceId())
                .orElseThrow(() -> new IllegalStateException(
                        "OA request approval instance is missing"));
    }

    private ApprovalDefinition requireDefinition(
            TenantId tenantId, ApprovalDefinitionId definitionId) {
        ApprovalDefinition definition = definitionRepository.findById(
                        tenantId, definitionId)
                .filter(value -> value.getObjectType() == BusinessObjectType.OA_REQUEST)
                .orElseThrow(() -> notFound("OA approval definition was not found"));
        if (!definition.isActive()) {
            throw new NonRetryableApplicationException(
                    ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                    "OA approval definition is not active");
        }
        return definition;
    }

    private OaFormDefinition requireActiveForm(
            TenantId tenantId, OaFormId formId) {
        return formRepository.findById(tenantId, formId)
                .filter(OaFormDefinition::isActive)
                .orElseThrow(() -> notFound("OA form was not found"));
    }

    private void requireAvailableForm(
            OaFormDefinition form, MemberId memberId, OrganizationMembership membership,
            ApprovalDefinition definition, Map<FieldCode, FieldDefinition> fields,
            Map<FieldCode, io.github.archetom.crm.domain.model.EffectiveFieldAccess> accesses) {
        if (!OaFormAvailability.isAvailable(form, memberId, membership.getDepartmentId(),
                definition, fields, accesses)) {
            throw notFound("OA form was not found");
        }
    }

    /**
     * Re-reads the membership under a share lock, for use after the tenant root has been
     * serialized.
     *
     * <p>The command transaction runs at REPEATABLE_READ, so a plain read here can return the
     * snapshot taken before the tenant lock was acquired — admitting a member who was deactivated
     * in between. The locking read sees the current row instead.</p>
     */
    private OrganizationMembership activeMembershipForShare(
            TenantId tenantId, MemberId memberId) {
        return membershipRepository.findByMemberForShare(tenantId, memberId)
                .filter(OrganizationMembership::isActive)
                .orElseThrow(() -> new NonRetryableApplicationException(
                        ApplicationErrorCode.ACCESS_DENIED,
                        "Caller is not an active tenant member"));
    }

    /** Non-locking read, for read-only paths that hold no tenant lock. */
    private OrganizationMembership activeMembership(
            TenantId tenantId, MemberId memberId) {
        return membershipRepository.findByMember(tenantId, memberId)
                .filter(OrganizationMembership::isActive)
                .orElseThrow(() -> new NonRetryableApplicationException(
                        ApplicationErrorCode.ACCESS_DENIED,
                        "Caller is not an active tenant member"));
    }

    private void requireFormVersion(OaFormDefinition form, Long version) {
        if (version == null || !version.equals(form.getVersion())) {
            throw new NonRetryableApplicationException(
                    ApplicationErrorCode.VERSION_CONFLICT,
                    "OA form version is stale");
        }
    }

    private String normalizeIdempotencyKey(String value) {
        try {
            return ApprovalInstance.normalizeIdempotencyKey(value);
        } catch (ApprovalDomainException exception) {
            throw support.invalid("OA request idempotency key is invalid");
        }
    }

    private OaFormId formId(Long value) {
        try {
            return new OaFormId(value);
        } catch (RuntimeException exception) {
            throw support.invalid("OA form ID is invalid");
        }
    }

    private OaFormId optionalFormId(Long value) {
        return value == null ? null : formId(value);
    }

    private OaRequestId requestId(Long value) {
        try {
            return new OaRequestId(value);
        } catch (RuntimeException exception) {
            throw support.invalid("OA request ID is invalid");
        }
    }

    private ApprovalInstanceStatus optionalStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return ApprovalInstanceStatus.valueOf(
                    value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw support.invalid("OA request approval status is invalid");
        }
    }

    private LocalDateTime now() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        return now.withNano(now.getNano() / 1_000 * 1_000);
    }

    private void require(Object value, String description) {
        if (value == null) {
            throw support.invalid(description + " is required");
        }
    }

    private NonRetryableApplicationException notFound(String message) {
        return new NonRetryableApplicationException(
                ApplicationErrorCode.RESOURCE_NOT_FOUND, message);
    }
}
